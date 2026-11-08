# WF1 integration

## Trust boundary

```
WF1 state (.wf1) → WF1 Monitor :8791 (localhost, has controls)
                 → Home Monitor Proxy :8792 (LAN, GET-only + bearer token)
                 → Home Monitor TV app (optional widget)
```

The TV never receives Cursor SDK credentials, `gh` tokens, deploy keys, issue bodies, transcripts, or WF1 control routes.

## Product behavior (BF3)

- WF1 is an **experimental** optional integration
- Fresh installs: widget **disabled**, no proxy URL/token bundled
- Existing owner upgrades: runtime config seeded from BuildConfig (when present) and widget stays enabled
- Settings → Integrations → **WF1 Engine** toggles the dashboard column
- When disabled, the right column is removed (left column uses full width)
- Configure proxy URL + token via authenticated LAN web admin; token requires TV remote approval

## Upstream

Proxy polls `GET http://127.0.0.1:8791/api/snapshot` every 5s and projects `operational.*`:

| TV field | Source |
|----------|--------|
| `active` | `operational.activeTask` (`showSpinner` only when status is `running`) |
| `queued` | `operational.currentQueue.eligibleOrdered` (preserves `queuePosition`) |
| `blocked` | `operational.blockers.items` |
| `readyForReview` | `operational.ownerReview.items` |
| `recentDone` | `operational.recentlyAccepted.items` (short tail) |
| `counts` | `operational.counts` |
| `connection` | `ok` / `stale` / `down` from upstream freshness + poll health |

Interrupted / unconfirmed active runs are never labeled as spinning RUNNING.

## TV client

- Runtime URL + token in `Wf1ConfigStore` (token in `SecretStore`)
- BuildConfig fields are empty by default; optional `local.properties` seeds upgrade builds only
- Polls `GET /v1/wf1/queue` every 5s only while the widget is enabled, configured, and the overlay is started
- On failure: retains last payload, marks stale, disables spinner
- No network discovery — disconnected UI is honest when the proxy is unreachable or unconfigured

## Running the proxy

```bash
cd proxy
HM_TOKEN='your-strong-token' node server.mjs
# optional KeepAlive:
HM_TOKEN='your-strong-token' ./launchagent/install-launchagent.sh
```

Health (no auth): `GET /v1/health`  
Queue (auth): `GET /v1/wf1/queue` with `Authorization: Bearer <token>`
