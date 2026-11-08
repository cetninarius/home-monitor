package com.custom.vox.homemonitor.admin

import android.content.Context
import android.util.Log
import com.custom.vox.homemonitor.BuildConfig
import com.custom.vox.homemonitor.data.appearance.AppearanceStore
import com.custom.vox.homemonitor.data.appearance.BackgroundImageStore
import com.custom.vox.homemonitor.data.appearance.CardStyle
import com.custom.vox.homemonitor.data.calendar.CalendarRepository
import com.custom.vox.homemonitor.data.secure.PendingSecretGate
import com.custom.vox.homemonitor.data.services.HttpEndpointStore
import com.custom.vox.homemonitor.data.services.HttpMonitorRepository
import com.custom.vox.homemonitor.data.services.UptimeConfigStore
import com.custom.vox.homemonitor.data.services.UptimeRepository
import com.custom.vox.homemonitor.data.settings.DashboardPrefs
import com.custom.vox.homemonitor.data.todo.TodoRepository
import com.custom.vox.homemonitor.data.wf1.Wf1ConfigStore
import com.custom.vox.homemonitor.media.SmartTubeIntegrationStatus
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Foreground-only LAN admin server. GET static assets + authenticated JSON API.
 * No shell, ADB, or filesystem browsing.
 * UptimeRobot / WF1 secrets are never committed from here — they enter [PendingSecretGate]
 * and must be confirmed on the TV remote.
 */
class LocalAdminServer private constructor(
    private val context: Context,
    private val auth: AdminAuth,
    private val calendarRepository: CalendarRepository,
    private val todoRepository: TodoRepository,
    private val uptimeConfig: UptimeConfigStore,
    private val uptimeRepository: UptimeRepository,
    private val pendingSecretGate: PendingSecretGate,
    private val httpEndpointStore: HttpEndpointStore,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val dashboardPrefs: DashboardPrefs,
    private val wf1Config: Wf1ConfigStore,
    private val smartTubeStatus: SmartTubeIntegrationStatus,
    private val appearanceStore: AppearanceStore,
    private val backgroundImages: BackgroundImageStore,
    private val port: Int,
    private val bindHost: String,
) : NanoHTTPD(bindHost, port) {

    @Volatile
    var lastBindAddress: String? = null
        private set

    fun publicBaseUrl(): String? {
        val ip = lastBindAddress ?: guessLanIpv4() ?: return null
        return "http://$ip:$port"
    }

    @Throws(IOException::class)
    override fun start() {
        lastBindAddress = guessLanIpv4()
        super.start(SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "Admin server listening on ${publicBaseUrl()} (bind=$bindHost)")
    }

    companion object {
        private const val TAG = "HomeMonitorAdmin"
        private const val COOKIE = "hm_session"

        fun create(
            context: Context,
            auth: AdminAuth,
            calendarRepository: CalendarRepository,
            todoRepository: TodoRepository,
            uptimeConfig: UptimeConfigStore,
            uptimeRepository: UptimeRepository,
            pendingSecretGate: PendingSecretGate,
            httpEndpointStore: HttpEndpointStore,
            httpMonitorRepository: HttpMonitorRepository,
            dashboardPrefs: DashboardPrefs,
            wf1Config: Wf1ConfigStore,
            smartTubeStatus: SmartTubeIntegrationStatus,
            appearanceStore: AppearanceStore,
            backgroundImages: BackgroundImageStore,
            port: Int = BuildConfig.ADMIN_PORT,
        ): LocalAdminServer {
            // Bind all interfaces so adb-forward (127.0.0.1) and LAN share one listener.
            // On this VOX, direct LAN HTTP may hang after TCP connect — use
            // `adb forward tcp:8765 tcp:8765` then http://127.0.0.1:8765 for Cursor/CLI.
            return LocalAdminServer(
                context,
                auth,
                calendarRepository,
                todoRepository,
                uptimeConfig,
                uptimeRepository,
                pendingSecretGate,
                httpEndpointStore,
                httpMonitorRepository,
                dashboardPrefs,
                wf1Config,
                smartTubeStatus,
                appearanceStore,
                backgroundImages,
                port,
                "0.0.0.0",
            )
        }

        fun guessLanIpv4(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces()?.toList()
                    ?.flatMap { it.inetAddresses.toList() }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.startsWith("192.168.") == true }
                    ?.hostAddress
                    ?: NetworkInterface.getNetworkInterfaces()?.toList()
                        ?.flatMap { it.inetAddresses.toList() }
                        ?.filterIsInstance<Inet4Address>()
                        ?.firstOrNull { !it.isLoopbackAddress }
                        ?.hostAddress
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val client = session.remoteIpAddress ?: "unknown"
        if (!auth.allowRequest(client)) {
            return json(Response.Status.TOO_MANY_REQUESTS, JsonUtil.error("rate_limited"))
        }

        val uri = session.uri.substringBefore('?').trimEnd('/').ifEmpty { "/" }
        val method = session.method

        return try {
            when {
                method == Method.GET && (uri == "/" || uri == "/index.html") ->
                    asset("admin/index.html", "text/html; charset=utf-8")
                method == Method.GET && uri == "/styles.css" ->
                    asset("admin/styles.css", "text/css; charset=utf-8")
                method == Method.GET && uri == "/app.js" ->
                    asset("admin/app.js", "application/javascript; charset=utf-8")
                method == Method.GET && uri == "/api/health" ->
                    json(
                        Response.Status.OK,
                        JSONObject()
                            .put("ok", true)
                            .put("service", "home-monitor-admin")
                            .put("readOnly", false)
                            .put("port", port)
                            .toString(),
                    )
                method == Method.POST && uri == "/api/login" -> handleLogin(session)
                method == Method.POST && uri == "/api/logout" -> handleLogout(session)
                uri.startsWith("/api/") -> handleApi(session, uri, method)
                else -> json(Response.Status.NOT_FOUND, JsonUtil.error("not_found"))
            }
        } catch (e: IllegalArgumentException) {
            json(Response.Status.BAD_REQUEST, JsonUtil.error(e.message ?: "bad_request"))
        } catch (e: NoSuchElementException) {
            json(Response.Status.NOT_FOUND, JsonUtil.error(e.message ?: "not_found"))
        } catch (e: Exception) {
            Log.e(TAG, "Admin request failed", e)
            json(Response.Status.INTERNAL_ERROR, JsonUtil.error("internal_error"))
        }
    }

    private fun handleApi(session: IHTTPSession, uri: String, method: Method): Response {
        if (!isAuthenticated(session)) {
            return json(Response.Status.UNAUTHORIZED, JsonUtil.error("unauthorized"))
        }
        if (method != Method.GET && usesCookieAuth(session) && !csrfOk(session)) {
            return json(Response.Status.FORBIDDEN, JsonUtil.error("csrf_rejected"))
        }

        return when {
            uri == "/api/tasks" && method == Method.GET -> runBlocking {
                val tasks = todoRepository.listAll()
                json(
                    Response.Status.OK,
                    JSONObject().put("ok", true).put("tasks", JsonUtil.tasksArray(tasks)).toString(),
                )
            }
            uri == "/api/tasks" && method == Method.POST -> {
                val body = readJson(session)
                val created = runBlocking {
                    todoRepository.create(
                        title = body.getString("title"),
                        description = body.optStringOrNull("description"),
                        priority = body.optString("priority", "normal"),
                        dueDate = body.optStringOrNull("dueDate"),
                        sortOrder = if (body.has("sortOrder")) body.getInt("sortOrder") else null,
                    )
                }
                json(Response.Status.CREATED, JSONObject().put("ok", true).put("task", JsonUtil.taskJson(created)).toString())
            }
            uri.matches(Regex("/api/tasks/[^/]+")) && method == Method.PATCH -> {
                val id = uri.substringAfterLast('/')
                val body = readJson(session)
                val updated = runBlocking {
                    todoRepository.update(
                        id = id,
                        title = body.optStringOrNull("title"),
                        description = body.optStringOrNull("description"),
                        clearDescription = body.optBoolean("clearDescription", false),
                        priority = body.optStringOrNull("priority"),
                        dueDate = body.optStringOrNull("dueDate"),
                        clearDueDate = body.optBoolean("clearDueDate", false),
                        sortOrder = if (body.has("sortOrder")) body.getInt("sortOrder") else null,
                    )
                }
                json(Response.Status.OK, JSONObject().put("ok", true).put("task", JsonUtil.taskJson(updated)).toString())
            }
            uri.matches(Regex("/api/tasks/[^/]+")) && method == Method.DELETE -> {
                val id = uri.substringAfterLast('/')
                runBlocking { todoRepository.delete(id) }
                json(Response.Status.OK, JsonUtil.ok())
            }
            uri.matches(Regex("/api/tasks/[^/]+/complete")) && method == Method.POST -> {
                val id = uri.removeSuffix("/complete").substringAfterLast('/')
                val task = runBlocking { todoRepository.complete(id) }
                json(Response.Status.OK, JSONObject().put("ok", true).put("task", JsonUtil.taskJson(task)).toString())
            }
            uri.matches(Regex("/api/tasks/[^/]+/reopen")) && method == Method.POST -> {
                val id = uri.removeSuffix("/reopen").substringAfterLast('/')
                val task = runBlocking { todoRepository.reopen(id) }
                json(Response.Status.OK, JSONObject().put("ok", true).put("task", JsonUtil.taskJson(task)).toString())
            }
            uri == "/api/tasks/reorder" && method == Method.POST -> {
                val body = readJson(session)
                val ids = body.getJSONArray("orderedIds").toStringList()
                runBlocking { todoRepository.reorder(ids) }
                json(Response.Status.OK, JsonUtil.ok())
            }
            uri == "/api/events" && method == Method.GET -> runBlocking {
                val events = calendarRepository.listAll()
                json(
                    Response.Status.OK,
                    JSONObject().put("ok", true).put("events", JsonUtil.eventsArray(events)).toString(),
                )
            }
            uri == "/api/events" && method == Method.POST -> {
                val body = readJson(session)
                val created = runBlocking {
                    calendarRepository.create(
                        title = body.getString("title"),
                        date = body.getString("date"),
                        startTime = body.optStringOrNull("startTime"),
                        endTime = body.optStringOrNull("endTime"),
                        allDay = body.optBoolean("allDay", false),
                        description = body.optStringOrNull("description"),
                    )
                }
                json(Response.Status.CREATED, JSONObject().put("ok", true).put("event", JsonUtil.eventJson(created)).toString())
            }
            uri.matches(Regex("/api/events/[^/]+")) && method == Method.PATCH -> {
                val id = uri.substringAfterLast('/')
                val body = readJson(session)
                val updated = runBlocking {
                    calendarRepository.update(
                        id = id,
                        title = body.optStringOrNull("title"),
                        date = body.optStringOrNull("date"),
                        startTime = body.optStringOrNull("startTime"),
                        endTime = body.optStringOrNull("endTime"),
                        allDay = if (body.has("allDay")) body.getBoolean("allDay") else null,
                        description = body.optStringOrNull("description"),
                        clearDescription = body.optBoolean("clearDescription", false),
                    )
                }
                json(Response.Status.OK, JSONObject().put("ok", true).put("event", JsonUtil.eventJson(updated)).toString())
            }
            uri.matches(Regex("/api/events/[^/]+")) && method == Method.DELETE -> {
                val id = uri.substringAfterLast('/')
                runBlocking { calendarRepository.delete(id) }
                json(Response.Status.OK, JsonUtil.ok())
            }
            uri == "/api/uptime/config" && method == Method.GET -> uptimeConfigResponse()
            uri == "/api/uptime/config" && method == Method.POST -> handleUptimeConfigPost(session)
            uri == "/api/uptime/config" && method == Method.DELETE -> {
                uptimeConfig.disconnect()
                pendingSecretGate.cancel()
                json(Response.Status.OK, JsonUtil.ok())
            }
            uri == "/api/uptime/key" && method == Method.POST -> {
                val body = readJson(session)
                val apiKey = body.optStringOrNull("apiKey")
                    ?: throw IllegalArgumentException("apiKey required")
                val pending = pendingSecretGate.submit(
                    PendingSecretGate.Pending.Kind.UPTIMEROBOT_API_KEY,
                    apiKey,
                )
                json(
                    Response.Status.ACCEPTED,
                    JSONObject()
                        .put("ok", true)
                        .put("pendingConfirm", true)
                        .put("id", pending.id)
                        .put("expiresInMs", PendingSecretGate.DEFAULT_TTL_MS)
                        .put("message", "Approve the key on the TV remote within 2 minutes")
                        .toString(),
                )
            }
            uri == "/api/uptime/pending" && method == Method.GET -> {
                val pub = pendingSecretGate.peekPublic()
                val payload = JSONObject().put("ok", true)
                pub.forEach { (k, v) -> payload.put(k, JsonUtil.toJsonValue(v)) }
                json(Response.Status.OK, payload.toString())
            }
            uri == "/api/uptime/pending/cancel" && method == Method.POST -> {
                val body = readJson(session)
                val id = body.optStringOrNull("id")
                pendingSecretGate.cancel(id)
                json(Response.Status.OK, JsonUtil.ok())
            }
            uri == "/api/uptime/test" && method == Method.POST -> {
                val body = readJson(session)
                val key = body.optStringOrNull("apiKey") ?: uptimeConfig.apiKey()
                    ?: throw IllegalArgumentException("api_key_required")
                val result = uptimeRepository.testConnection(key)
                result.fold(
                    onSuccess = { count ->
                        json(
                            Response.Status.OK,
                            JSONObject().put("ok", true).put("monitorCount", count).toString(),
                        )
                    },
                    onFailure = { e ->
                        json(
                            Response.Status.BAD_REQUEST,
                            JsonUtil.error(e.message ?: "test_failed"),
                        )
                    },
                )
            }
            uri == "/api/uptime/monitors" && method == Method.GET -> {
                val result = uptimeRepository.listMonitors()
                result.fold(
                    onSuccess = { list ->
                        val arr = JSONArray()
                        list.forEach { m ->
                            arr.put(
                                JSONObject()
                                    .put("id", m.id)
                                    .put("name", m.name)
                                    .put("status", m.status.name),
                            )
                        }
                        json(
                            Response.Status.OK,
                            JSONObject().put("ok", true).put("monitors", arr).toString(),
                        )
                    },
                    onFailure = { e ->
                        json(
                            Response.Status.BAD_REQUEST,
                            JsonUtil.error(e.message ?: "list_failed"),
                        )
                    },
                )
            }
            uri == "/api/uptime/selection" && method == Method.POST -> {
                val body = readJson(session)
                val ids = body.optJSONArray("selectedMonitorIds")?.toStringList()
                    ?: throw IllegalArgumentException("selectedMonitorIds required")
                val labels = LinkedHashMap<String, String>()
                body.optJSONObject("labels")?.let { obj ->
                    obj.keys().forEach { k ->
                        val v = obj.optString(k)
                        if (v.isNotBlank()) labels[k] = v
                    }
                }
                uptimeConfig.saveSelection(ids, labels)
                uptimeConfigResponse()
            }
            uri == "/api/http/endpoints" && method == Method.GET -> {
                val arr = JSONArray()
                httpEndpointStore.list().forEach { ep ->
                    arr.put(
                        JSONObject()
                            .put("id", ep.id)
                            .put("name", ep.name)
                            .put("url", ep.url)
                            .put("enabled", ep.enabled),
                    )
                }
                json(
                    Response.Status.OK,
                    JSONObject()
                        .put("ok", true)
                        .put("endpoints", arr)
                        .put("max", HttpEndpointStore.MAX_ENDPOINTS)
                        .toString(),
                )
            }
            uri == "/api/http/endpoints" && method == Method.POST -> {
                val body = readJson(session)
                val ep = httpEndpointStore.add(
                    name = body.optString("name"),
                    url = body.getString("url"),
                    enabled = body.optBoolean("enabled", true),
                )
                json(
                    Response.Status.CREATED,
                    JSONObject()
                        .put("ok", true)
                        .put("endpoint", JSONObject()
                            .put("id", ep.id)
                            .put("name", ep.name)
                            .put("url", ep.url)
                            .put("enabled", ep.enabled))
                        .toString(),
                )
            }
            uri.matches(Regex("/api/http/endpoints/[^/]+")) && method == Method.PATCH -> {
                val id = uri.substringAfterLast('/')
                val existing = httpEndpointStore.get(id)
                    ?: throw NoSuchElementException("not_found")
                val body = readJson(session)
                val updated = httpEndpointStore.upsert(
                    existing.copy(
                        name = body.optStringOrNull("name") ?: existing.name,
                        url = body.optStringOrNull("url") ?: existing.url,
                        enabled = if (body.has("enabled")) body.getBoolean("enabled") else existing.enabled,
                    ),
                )
                json(
                    Response.Status.OK,
                    JSONObject()
                        .put("ok", true)
                        .put("endpoint", JSONObject()
                            .put("id", updated.id)
                            .put("name", updated.name)
                            .put("url", updated.url)
                            .put("enabled", updated.enabled))
                        .toString(),
                )
            }
            uri.matches(Regex("/api/http/endpoints/[^/]+")) && method == Method.DELETE -> {
                val id = uri.substringAfterLast('/')
                if (!httpEndpointStore.delete(id)) throw NoSuchElementException("not_found")
                json(Response.Status.OK, JsonUtil.ok())
            }
            uri == "/api/http/test" && method == Method.POST -> {
                val body = readJson(session)
                val url = body.optStringOrNull("url")
                    ?: throw IllegalArgumentException("url required")
                val result = httpMonitorRepository.test(url)
                result.fold(
                    onSuccess = { m ->
                        json(
                            Response.Status.OK,
                            JSONObject()
                                .put("ok", true)
                                .put("status", m.status.name)
                                .put("responseTimeMs", m.responseTimeMs)
                                .put("error", m.error)
                                .toString(),
                        )
                    },
                    onFailure = { e ->
                        json(Response.Status.BAD_REQUEST, JsonUtil.error(e.message ?: "test_failed"))
                    },
                )
            }
            uri == "/api/dashboard" && method == Method.GET -> {
                val payload = JSONObject().put("ok", true)
                dashboardPrefs.publicConfig().forEach { (k, v) ->
                    payload.put(k, JsonUtil.toJsonValue(v))
                }
                json(Response.Status.OK, payload.toString())
            }
            uri == "/api/dashboard" && method == Method.POST -> {
                val body = readJson(session)
                if (body.has("servicesEnabled")) {
                    dashboardPrefs.setServicesEnabled(body.getBoolean("servicesEnabled"))
                }
                if (body.has("nowPlayingEnabled")) {
                    dashboardPrefs.setNowPlayingEnabled(body.getBoolean("nowPlayingEnabled"))
                }
                if (body.has("wf1Enabled")) {
                    dashboardPrefs.setWf1Enabled(body.getBoolean("wf1Enabled"))
                }
                val payload = JSONObject().put("ok", true)
                dashboardPrefs.publicConfig().forEach { (k, v) ->
                    payload.put(k, JsonUtil.toJsonValue(v))
                }
                json(Response.Status.OK, payload.toString())
            }
            uri == "/api/wf1/config" && method == Method.GET -> wf1ConfigResponse()
            uri == "/api/wf1/config" && method == Method.POST -> handleWf1ConfigPost(session)
            uri == "/api/wf1/config" && method == Method.DELETE -> {
                wf1Config.clear()
                dashboardPrefs.setWf1Enabled(false)
                pendingSecretGate.cancel()
                json(Response.Status.OK, JsonUtil.ok())
            }
            uri == "/api/wf1/token" && method == Method.POST -> {
                val body = readJson(session)
                val token = body.optStringOrNull("token")
                    ?: throw IllegalArgumentException("token required")
                val baseUrl = body.optStringOrNull("baseUrl")
                val meta = LinkedHashMap<String, String>()
                if (!baseUrl.isNullOrBlank()) meta["baseUrl"] = baseUrl.trim().trimEnd('/')
                val pending = pendingSecretGate.submit(
                    PendingSecretGate.Pending.Kind.WF1_PROXY_TOKEN,
                    token,
                    meta = meta,
                )
                json(
                    Response.Status.ACCEPTED,
                    JSONObject()
                        .put("ok", true)
                        .put("pendingConfirm", true)
                        .put("id", pending.id)
                        .put("expiresInMs", PendingSecretGate.DEFAULT_TTL_MS)
                        .put("message", "Approve the WF1 token on the TV remote within 2 minutes")
                        .toString(),
                )
            }
            uri == "/api/smarttube" && method == Method.GET -> {
                val payload = JSONObject().put("ok", true)
                smartTubeStatus.publicConfig().forEach { (k, v) ->
                    payload.put(k, JsonUtil.toJsonValue(v))
                }
                json(Response.Status.OK, payload.toString())
            }
            uri == "/api/appearance" && method == Method.GET -> {
                val payload = JSONObject().put("ok", true)
                appearanceStore.publicConfig(backgroundImages.listCustom()).forEach { (k, v) ->
                    payload.put(k, JsonUtil.toJsonValue(v))
                }
                json(Response.Status.OK, payload.toString())
            }
            // POST preferred — NanoHTTPD reliably parses JSON bodies on POST.
            // PUT kept as an alias for clients that already use it.
            (uri == "/api/appearance" && (method == Method.POST || method == Method.PUT)) -> {
                val body = readJson(session)
                if (body.optBoolean("restoreDefault", false)) {
                    appearanceStore.restoreDefaults()
                } else {
                    val bg = body.optStringOrNull("backgroundId")
                    if (bg != null && !backgroundImages.exists(bg)) {
                        throw IllegalArgumentException("unknown_background")
                    }
                    val dim = if (body.has("dimPercent")) body.getInt("dimPercent") else null
                    val style = body.optStringOrNull("cardStyle")?.let { CardStyle.fromId(it) }
                    appearanceStore.update(backgroundId = bg, dimPercent = dim, cardStyle = style)
                }
                val payload = JSONObject().put("ok", true)
                appearanceStore.publicConfig(backgroundImages.listCustom()).forEach { (k, v) ->
                    payload.put(k, JsonUtil.toJsonValue(v))
                }
                json(Response.Status.OK, payload.toString())
            }
            uri == "/api/appearance/background" && method == Method.POST -> {
                val id = importBackgroundUpload(session)
                appearanceStore.setBackgroundId(id)
                json(
                    Response.Status.CREATED,
                    JSONObject()
                        .put("ok", true)
                        .put("backgroundId", id)
                        .toString(),
                )
            }
            uri.matches(Regex("/api/appearance/background/.+")) && method == Method.DELETE &&
                !uri.endsWith("/thumb") -> {
                val fullId = decodeAppearanceId(uri.removePrefix("/api/appearance/background/"))
                if (!AppearanceStore.isCustomId(fullId)) {
                    throw IllegalArgumentException("only_custom_deletable")
                }
                val deleted = backgroundImages.deleteCustom(fullId)
                if (!deleted) throw NoSuchElementException("not_found")
                if (appearanceStore.backgroundId() == fullId) {
                    appearanceStore.setBackgroundId(AppearanceStore.DEFAULT_BACKGROUND_ID)
                }
                json(Response.Status.OK, JsonUtil.ok())
            }
            uri.matches(Regex("/api/appearance/background/.+/thumb")) && method == Method.GET -> {
                val fullId = decodeAppearanceId(
                    uri.removeSuffix("/thumb").removePrefix("/api/appearance/background/"),
                )
                val bytes = backgroundImages.loadThumbBytes(fullId)
                    ?: throw NoSuchElementException("not_found")
                newFixedLengthResponse(
                    Response.Status.OK,
                    "image/jpeg",
                    bytes.inputStream(),
                    bytes.size.toLong(),
                ).also { it.addHeader("Cache-Control", "private, max-age=60") }
            }
            else -> json(Response.Status.NOT_FOUND, JsonUtil.error("not_found"))
        }
    }

    private fun uptimeConfigResponse(): Response {
        val cfg = uptimeConfig.publicConfig()
        val payload = JSONObject().put("ok", true)
        cfg.forEach { (k, v) -> payload.put(k, JsonUtil.toJsonValue(v)) }
        val pending = pendingSecretGate.peekPublic()
        payload.put("pending", JsonUtil.toJsonValue(pending))
        return json(Response.Status.OK, payload.toString())
    }

    private fun wf1ConfigResponse(): Response {
        val cfg = wf1Config.publicConfig()
        val payload = JSONObject().put("ok", true)
        cfg.forEach { (k, v) -> payload.put(k, JsonUtil.toJsonValue(v)) }
        payload.put("wf1Enabled", dashboardPrefs.wf1Enabled())
        payload.put("pending", JsonUtil.toJsonValue(pendingSecretGate.peekPublic()))
        return json(Response.Status.OK, payload.toString())
    }

    /**
     * URL can be saved immediately. A token in the body is staged for TV confirmation.
     */
    private fun handleWf1ConfigPost(session: IHTTPSession): Response {
        val body = readJson(session)
        val baseUrl = body.optStringOrNull("baseUrl")
        if (baseUrl != null) {
            wf1Config.setBaseUrl(baseUrl)
        }
        if (body.has("wf1Enabled")) {
            dashboardPrefs.setWf1Enabled(body.getBoolean("wf1Enabled"))
        }
        var pendingId: String? = null
        val token = body.optStringOrNull("token")
        if (!token.isNullOrBlank()) {
            val meta = LinkedHashMap<String, String>()
            (baseUrl ?: wf1Config.baseUrl())?.let { meta["baseUrl"] = it }
            val pending = pendingSecretGate.submit(
                PendingSecretGate.Pending.Kind.WF1_PROXY_TOKEN,
                token,
                meta = meta,
            )
            pendingId = pending.id
        }
        val cfg = wf1Config.publicConfig()
        val payload = JSONObject().put("ok", true)
        cfg.forEach { (k, v) -> payload.put(k, JsonUtil.toJsonValue(v)) }
        payload.put("wf1Enabled", dashboardPrefs.wf1Enabled())
        if (pendingId != null) {
            payload.put("pendingConfirm", true)
            payload.put("pendingId", pendingId)
            payload.put("message", "Approve the WF1 token on the TV remote within 2 minutes")
        }
        payload.put("pending", JsonUtil.toJsonValue(pendingSecretGate.peekPublic()))
        return json(
            if (pendingId != null) Response.Status.ACCEPTED else Response.Status.OK,
            payload.toString(),
        )
    }

    /**
     * Selection can be saved immediately. An apiKey in the body is never written
     * directly — it is staged in [PendingSecretGate] for TV confirmation.
     */
    private fun handleUptimeConfigPost(session: IHTTPSession): Response {
        val body = readJson(session)
        val apiKey = body.optStringOrNull("apiKey")
        var pendingId: String? = null
        if (!apiKey.isNullOrBlank()) {
            val pending = pendingSecretGate.submit(
                PendingSecretGate.Pending.Kind.UPTIMEROBOT_API_KEY,
                apiKey,
            )
            pendingId = pending.id
        }

        val selected = body.optJSONArray("selectedMonitorIds")?.toStringList()
        if (selected != null) {
            val labels = LinkedHashMap<String, String>()
            body.optJSONObject("labels")?.let { obj ->
                obj.keys().forEach { k ->
                    val v = obj.optString(k)
                    if (v.isNotBlank()) labels[k] = v
                }
            }
            // Legacy A/B fields still accepted as labels fallback
            body.optStringOrNull("labelA")?.let { selected.getOrNull(0)?.let { id -> labels.putIfAbsent(id, it) } }
            body.optStringOrNull("labelB")?.let { selected.getOrNull(1)?.let { id -> labels.putIfAbsent(id, it) } }
            uptimeConfig.saveSelection(selected, labels)
        } else {
            // Legacy A/B form
            val idA = body.optStringOrNull("monitorIdA")
            val idB = body.optStringOrNull("monitorIdB")
            if (idA != null || idB != null) {
                val ids = listOfNotNull(idA, idB)
                val labels = LinkedHashMap<String, String>()
                body.optStringOrNull("labelA")?.let { idA?.let { id -> labels[id] = it } }
                body.optStringOrNull("labelB")?.let { idB?.let { id -> labels[id] = it } }
                uptimeConfig.saveSelection(ids, labels)
            }
        }

        val cfg = uptimeConfig.publicConfig()
        val payload = JSONObject().put("ok", true)
        cfg.forEach { (k, v) -> payload.put(k, JsonUtil.toJsonValue(v)) }
        if (pendingId != null) {
            payload.put("pendingConfirm", true)
            payload.put("pendingId", pendingId)
            payload.put("message", "Approve the API key on the TV remote within 2 minutes")
        }
        payload.put("pending", JsonUtil.toJsonValue(pendingSecretGate.peekPublic()))
        return json(
            if (pendingId != null) Response.Status.ACCEPTED else Response.Status.OK,
            payload.toString(),
        )
    }

    private fun importBackgroundUpload(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val tempPath = files["file"]
            ?: files.values.firstOrNull()
            ?: throw IllegalArgumentException("file_required")
        val temp = File(tempPath)
        if (!temp.exists()) throw IllegalArgumentException("file_required")
        val bytes = temp.readBytes()
        val name = session.parms["file"] ?: temp.name
        return try {
            backgroundImages.importUpload(bytes, name)
        } finally {
            temp.delete()
        }
    }

    private fun decodeAppearanceId(raw: String): String {
        val decoded = java.net.URLDecoder.decode(raw, "UTF-8")
        return when {
            decoded.startsWith("bundled:") || decoded.startsWith("custom:") -> decoded
            backgroundImages.exists("bundled:$decoded") -> "bundled:$decoded"
            backgroundImages.exists("custom:$decoded") -> "custom:$decoded"
            else -> decoded
        }
    }

    private fun handleLogin(session: IHTTPSession): Response {
        val body = readJson(session)
        val token = body.optStringOrNull("token")
        val sessionId = auth.loginWithToken(token)
            ?: return json(Response.Status.UNAUTHORIZED, JsonUtil.error("invalid_token"))
        val res = json(Response.Status.OK, JsonUtil.ok(JSONObject().put("authenticated", true)))
        res.addHeader(
            "Set-Cookie",
            "$COOKIE=$sessionId; Path=/; HttpOnly; SameSite=Strict",
        )
        return res
    }

    private fun handleLogout(session: IHTTPSession): Response {
        auth.logout(sessionCookie(session))
        val res = json(Response.Status.OK, JsonUtil.ok())
        res.addHeader("Set-Cookie", "$COOKIE=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict")
        return res
    }

    private fun isAuthenticated(session: IHTTPSession): Boolean {
        if (auth.validateBearer(session.headers["authorization"])) return true
        return auth.validateSession(sessionCookie(session))
    }

    private fun usesCookieAuth(session: IHTTPSession): Boolean {
        if (auth.validateBearer(session.headers["authorization"])) return false
        return auth.validateSession(sessionCookie(session))
    }

    private fun csrfOk(session: IHTTPSession): Boolean {
        val origin = session.headers["origin"]
        val host = session.headers["host"]?.trim()?.lowercase()
        if (host.isNullOrBlank()) return false
        val hostNoPort = host.substringBefore(':')
        fun matchesHost(raw: String): Boolean {
            val cleaned = raw.trim().lowercase()
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore('/')
            val cleanedHost = cleaned.substringBefore(':')
            return cleaned == host || cleanedHost == hostNoPort
        }
        if (origin.isNullOrBlank()) {
            // Non-browser clients using cookies without Origin are rejected.
            val referer = session.headers["referer"]
            if (referer.isNullOrBlank()) return false
            return matchesHost(referer)
        }
        return matchesHost(origin)
    }

    private fun sessionCookie(session: IHTTPSession): String? {
        val raw = session.headers["cookie"] ?: return null
        return raw.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("$COOKIE=") }
            ?.substringAfter('=')
    }

    private fun readJson(session: IHTTPSession): JSONObject {
        val map = HashMap<String, String>()
        session.parseBody(map)
        val raw = map["postData"]
            ?: session.queryParameterString?.takeIf { it.trim().startsWith('{') }
            ?: "{}"
        return JSONObject(raw.ifBlank { "{}" })
    }

    private fun asset(path: String, mime: String): Response {
        val bytes = context.assets.open(path).use { it.readBytes() }
        return newFixedLengthResponse(
            Response.Status.OK,
            mime,
            bytes.inputStream(),
            bytes.size.toLong(),
        ).also { applySecurityHeaders(it) }
    }

    private fun json(status: Response.Status, body: String): Response {
        val res = newFixedLengthResponse(status, "application/json; charset=utf-8", body)
        res.addHeader("Cache-Control", "no-store")
        applySecurityHeaders(res)
        return res
    }

    private fun applySecurityHeaders(res: Response) {
        // Same-origin browser clients only — do not add Access-Control-Allow-Origin.
        res.addHeader("X-Content-Type-Options", "nosniff")
        res.addHeader("X-Frame-Options", "DENY")
        res.addHeader("Referrer-Policy", "no-referrer")
        res.addHeader("Cross-Origin-Resource-Policy", "same-origin")
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key)
    return v.ifBlank { null }
}

private fun JSONArray.toStringList(): List<String> {
    val out = ArrayList<String>(length())
    for (i in 0 until length()) out.add(getString(i))
    return out
}
