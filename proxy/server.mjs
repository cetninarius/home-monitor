#!/usr/bin/env node
/**
 * Home Monitor — read-only WF1 LAN proxy.
 *
 * Polls the localhost WF1 monitor (GET /api/snapshot) and exposes a sanitized
 * GET-only API for the Android TV app. Never forwards control routes, tokens,
 * cookies, state paths, issue bodies, or transcripts.
 *
 * Env:
 *   HM_HOST          bind address (default 0.0.0.0)
 *   HM_PORT          listen port (default 8792)
 *   HM_TOKEN         required bearer token
 *   WF1_MONITOR_URL  upstream base (default http://127.0.0.1:8791)
 *   HM_POLL_MS       upstream poll interval (default 5000)
 *   HM_STALE_MS      mark connection stale after this age (default 20000)
 */

import http from 'node:http'
import { URL } from 'node:url'

const HOST = process.env.HM_HOST || '0.0.0.0'
const PORT = Number(process.env.HM_PORT || 8792)
const TOKEN = process.env.HM_TOKEN || ''
const UPSTREAM = (process.env.WF1_MONITOR_URL || 'http://127.0.0.1:8791').replace(/\/$/, '')
const POLL_MS = Number(process.env.HM_POLL_MS || 5000)
const STALE_MS = Number(process.env.HM_STALE_MS || 20_000)

if (!TOKEN || TOKEN === 'change-me-home-monitor-token') {
  console.warn(
    '[home-monitor-proxy] WARNING: using default/empty HM_TOKEN. Set a strong HM_TOKEN before LAN use.',
  )
}

/** @type {{ at: number, raw: object|null, error: string|null, payload: object|null }} */
let cache = {
  at: 0,
  raw: null,
  error: null,
  payload: null,
}

function sanitizeString(value, max = 200) {
  if (value == null) return null
  const s = String(value)
  return s.length <= max ? s : `${s.slice(0, max)}…`
}

function numOrNull(v) {
  if (v == null || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

function mapQueuedItem(row) {
  return {
    issueNumber: numOrNull(row?.number ?? row?.issueNumber),
    title: sanitizeString(row?.title),
    status: 'queued',
    priority: sanitizeString(row?.priority, 32),
    queuePosition: numOrNull(row?.queuePosition),
    detail: null,
    errorSummary: null,
  }
}

function mapBlockedItem(row) {
  return {
    issueNumber: numOrNull(row?.number ?? row?.issueNumber),
    title: sanitizeString(row?.title),
    status: 'blocked',
    priority: sanitizeString(row?.priority, 32),
    queuePosition: null,
    detail: sanitizeString(row?.reason || row?.waitingReason, 120),
    errorSummary: sanitizeString(row?.summary || row?.error || row?.note, 180),
  }
}

function mapReviewItem(row) {
  return {
    issueNumber: numOrNull(row?.number ?? row?.issueNumber),
    title: sanitizeString(row?.title),
    status: 'ready_for_review',
    priority: sanitizeString(row?.priority, 32),
    queuePosition: null,
    detail: null,
    errorSummary: null,
  }
}

function mapDoneItem(row) {
  return {
    issueNumber: numOrNull(row?.number ?? row?.issueNumber),
    title: sanitizeString(row?.title),
    status: 'completed',
    priority: sanitizeString(row?.priority, 32),
    queuePosition: null,
    detail: null,
    errorSummary: null,
  }
}

function projectSnapshot(snap) {
  const op = snap?.operational || {}
  const activeTask = op.activeTask || {}
  const countsSrc = op.counts || {}
  const queue = op.currentQueue || {}
  const freshness = op.freshness || snap?.freshness || {}

  const githubStale = Boolean(freshness.githubStale)
  const queueFreshness = queue.freshness || snap?.queue?.freshness
  let connection = 'ok'
  if (githubStale || queueFreshness === 'stale') connection = 'stale'

  const runningCount = countsSrc.active?.available ? Number(countsSrc.active.value) || 0 : 0
  const queuedCount = countsSrc.eligibleQueued?.available
    ? Number(countsSrc.eligibleQueued.value) || 0
    : Array.isArray(queue.eligibleOrdered)
      ? queue.eligibleOrdered.length
      : 0
  const blockedCount = countsSrc.blocked?.available ? Number(countsSrc.blocked.value) || 0 : 0
  const reviewCount = countsSrc.awaitingOwnerReview?.available
    ? Number(countsSrc.awaitingOwnerReview.value) || 0
    : 0

  let active = null
  if (!activeTask.idle && activeTask.status !== 'idle') {
    const stage =
      activeTask.confirmedCurrentStage?.name ||
      activeTask.expectedNextStage?.name ||
      null
    active = {
      idle: false,
      issueNumber: numOrNull(activeTask.issueNumber),
      title: sanitizeString(activeTask.issueTitle),
      status: String(activeTask.status || 'unconfirmed'),
      showSpinner: Boolean(activeTask.showSpinner) && activeTask.status === 'running',
      statusConfirmed: Boolean(activeTask.statusConfirmed ?? true),
      stage: sanitizeString(stage, 64),
      elapsedMs: numOrNull(activeTask.runDurationMs),
      errorSummary: sanitizeString(
        activeTask.statusNote &&
          ['blocked', 'interrupted', 'unconfirmed'].includes(String(activeTask.status))
          ? activeTask.statusNote
          : null,
        180,
      ),
    }
  }

  const blockedItems = Array.isArray(op.blockers?.items)
    ? op.blockers.items.map(mapBlockedItem)
    : []

  // Owner-review list — operational.ownerReview (live field name).
  let reviewItems = []
  const ownerReview = op.ownerReview || {}
  if (Array.isArray(ownerReview.items)) {
    reviewItems = ownerReview.items.map(mapReviewItem)
  } else if (Array.isArray(queue.ineligible)) {
    reviewItems = queue.ineligible
      .filter(
        (r) =>
          r?.waitingReason === 'awaiting_owner_review' ||
          String(r?.status || '').includes('ready-for-owner-review'),
      )
      .map(mapReviewItem)
  }

  const recentlyAccepted = op.recentlyAccepted || {}
  const recentDone = Array.isArray(recentlyAccepted.items)
    ? recentlyAccepted.items.slice(0, 3).map(mapDoneItem)
    : []

  const queued = Array.isArray(queue.eligibleOrdered)
    ? queue.eligibleOrdered.map(mapQueuedItem)
    : []

  return {
    refreshedAt: snap?.refreshedAt || new Date().toISOString(),
    connection,
    counts: {
      running: runningCount,
      queued: queuedCount,
      blocked: blockedCount,
      readyForReview: reviewCount,
    },
    active,
    queued,
    blocked: blockedItems,
    readyForReview: reviewItems,
    recentDone,
    // Minimal non-sensitive identity for operators debugging the TV link.
    meta: {
      queueAuthoritative: Boolean(queue.authoritative),
      queueSource: sanitizeString(queue.source, 64),
      upstream: 'wf1-monitor-snapshot',
    },
  }
}

async function pollUpstream() {
  const url = `${UPSTREAM}/api/snapshot`
  try {
    const res = await fetch(url, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(8000),
    })
    if (!res.ok) {
      throw new Error(`upstream HTTP ${res.status}`)
    }
    const raw = await res.json()
    const payload = projectSnapshot(raw)
    cache = { at: Date.now(), raw: null, error: null, payload }
  } catch (err) {
    cache = {
      ...cache,
      at: cache.at || Date.now(),
      error: String(err?.message || err),
      // Keep last payload; mark down/stale on serve.
    }
    if (!cache.payload) {
      cache.payload = {
        refreshedAt: null,
        connection: 'down',
        counts: { running: 0, queued: 0, blocked: 0, readyForReview: 0 },
        active: null,
        queued: [],
        blocked: [],
        readyForReview: [],
        recentDone: [],
        meta: { queueAuthoritative: false, queueSource: null, upstream: 'wf1-monitor-snapshot' },
      }
    }
  }
}

function servePayload() {
  const age = Date.now() - (cache.at || 0)
  const payload = cache.payload
    ? { ...cache.payload }
    : {
        refreshedAt: null,
        connection: 'down',
        counts: { running: 0, queued: 0, blocked: 0, readyForReview: 0 },
        active: null,
        queued: [],
        blocked: [],
        readyForReview: [],
        recentDone: [],
      }

  if (cache.error && !cache.payload?.refreshedAt) {
    payload.connection = 'down'
  } else if (cache.error || age > STALE_MS) {
    payload.connection = 'stale'
    if (payload.active) {
      payload.active = { ...payload.active, showSpinner: false }
    }
  }

  return payload
}

function unauthorized(res) {
  res.writeHead(401, {
    'content-type': 'application/json',
    'cache-control': 'no-store',
  })
  res.end(JSON.stringify({ ok: false, error: 'unauthorized' }))
}

function notFound(res) {
  res.writeHead(404, {
    'content-type': 'application/json',
    'cache-control': 'no-store',
  })
  res.end(JSON.stringify({ ok: false, error: 'not_found' }))
}

function methodNotAllowed(res) {
  res.writeHead(405, {
    'content-type': 'application/json',
    'cache-control': 'no-store',
    allow: 'GET',
  })
  res.end(JSON.stringify({ ok: false, error: 'method_not_allowed' }))
}

function checkAuth(req) {
  if (!TOKEN) return false
  const header = req.headers.authorization || ''
  if (header === `Bearer ${TOKEN}`) return true
  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)
  // Query token intentionally unsupported — header only.
  void url
  return false
}

const server = http.createServer(async (req, res) => {
  const method = (req.method || 'GET').toUpperCase()
  if (method !== 'GET' && method !== 'HEAD') {
    return methodNotAllowed(res)
  }

  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)
  const path = url.pathname.replace(/\/$/, '') || '/'

  if (path === '/v1/health') {
    const body = JSON.stringify({
      ok: true,
      service: 'home-monitor-wf1-proxy',
      upstream: UPSTREAM,
      lastPollAt: cache.at ? new Date(cache.at).toISOString() : null,
      lastError: cache.error,
      readOnly: true,
    })
    res.writeHead(200, {
      'content-type': 'application/json',
      'cache-control': 'no-store',
    })
    if (method === 'HEAD') return res.end()
    return res.end(body)
  }

  if (path === '/v1/wf1/queue') {
    if (!checkAuth(req)) return unauthorized(res)
    const payload = servePayload()
    const body = JSON.stringify(payload)
    res.writeHead(200, {
      'content-type': 'application/json',
      'cache-control': 'no-store',
    })
    if (method === 'HEAD') return res.end()
    return res.end(body)
  }

  return notFound(res)
})

await pollUpstream()
setInterval(() => {
  pollUpstream().catch(() => {})
}, POLL_MS)

server.listen(PORT, HOST, () => {
  console.log(
    `[home-monitor-proxy] listening on http://${HOST}:${PORT} (upstream ${UPSTREAM}, poll ${POLL_MS}ms)`,
  )
  console.log('[home-monitor-proxy] endpoints: GET /v1/health, GET /v1/wf1/queue (Bearer auth)')
})
