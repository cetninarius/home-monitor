package com.custom.vox.homemonitor

import android.app.Application
import com.custom.vox.homemonitor.admin.AdminAuth
import com.custom.vox.homemonitor.data.appearance.AppearanceStore
import com.custom.vox.homemonitor.data.appearance.BackgroundImageStore
import com.custom.vox.homemonitor.data.calendar.CalendarRepository
import com.custom.vox.homemonitor.data.local.AppDatabase
import com.custom.vox.homemonitor.data.media.NowPlayingRepository
import com.custom.vox.homemonitor.data.secure.PendingSecretGate
import com.custom.vox.homemonitor.data.secure.SecretStore
import com.custom.vox.homemonitor.data.services.HttpEndpointStore
import com.custom.vox.homemonitor.data.services.HttpMonitorRepository
import com.custom.vox.homemonitor.data.services.ServiceMonitorHub
import com.custom.vox.homemonitor.data.services.UptimeConfigStore
import com.custom.vox.homemonitor.data.services.UptimeRepository
import com.custom.vox.homemonitor.data.settings.DashboardPrefs
import com.custom.vox.homemonitor.data.settings.DiagnosticsPrefs
import com.custom.vox.homemonitor.data.todo.TodoRepository
import com.custom.vox.homemonitor.data.weather.WeatherRepository
import com.custom.vox.homemonitor.data.wf1.Wf1ConfigStore
import com.custom.vox.homemonitor.data.wf1.Wf1Repository
import com.custom.vox.homemonitor.diagnostics.CrashReporting
import com.custom.vox.homemonitor.media.DashboardReclaimer
import com.custom.vox.homemonitor.media.SmartTubeIntegrationStatus

class HomeMonitorApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var calendarRepository: CalendarRepository
        private set
    lateinit var todoRepository: TodoRepository
        private set
    lateinit var adminAuth: AdminAuth
        private set
    lateinit var weatherRepository: WeatherRepository
        private set
    lateinit var secretStore: SecretStore
        private set
    lateinit var pendingSecretGate: PendingSecretGate
        private set
    lateinit var uptimeConfig: UptimeConfigStore
        private set
    lateinit var uptimeRepository: UptimeRepository
        private set
    lateinit var httpEndpointStore: HttpEndpointStore
        private set
    lateinit var httpMonitorRepository: HttpMonitorRepository
        private set
    lateinit var serviceMonitorHub: ServiceMonitorHub
        private set
    lateinit var dashboardPrefs: DashboardPrefs
        private set
    lateinit var diagnosticsPrefs: DiagnosticsPrefs
        private set
    lateinit var wf1Config: Wf1ConfigStore
        private set
    lateinit var wf1Repository: Wf1Repository
        private set
    lateinit var nowPlayingRepository: NowPlayingRepository
        private set
    lateinit var dashboardReclaimer: DashboardReclaimer
        private set
    lateinit var appearanceStore: AppearanceStore
        private set
    lateinit var backgroundImageStore: BackgroundImageStore
        private set
    lateinit var smartTubeStatus: SmartTubeIntegrationStatus
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.get(this)
        calendarRepository = CalendarRepository(database.calendarEvents())
        todoRepository = TodoRepository(database.todoTasks())
        adminAuth = AdminAuth(this)
        weatherRepository = WeatherRepository()
        secretStore = SecretStore(this)
        pendingSecretGate = PendingSecretGate()
        uptimeConfig = UptimeConfigStore(this, secretStore)
        uptimeRepository = UptimeRepository(uptimeConfig)
        httpEndpointStore = HttpEndpointStore(this)
        httpMonitorRepository = HttpMonitorRepository(httpEndpointStore)
        serviceMonitorHub = ServiceMonitorHub(
            uptimeRepository,
            httpMonitorRepository,
            uptimeConfig,
            httpEndpointStore,
        )
        wf1Config = Wf1ConfigStore(this, secretStore)
        dashboardPrefs = DashboardPrefs(this)
        diagnosticsPrefs = DiagnosticsPrefs(this)
        dashboardPrefs.migrateBf3IfNeeded(wf1Config)
        // Safe if already configured; repairs upgrades that skipped placeholder-token seed.
        wf1Config.seedFromBuildConfigIfEmpty()
        wf1Repository = Wf1Repository(wf1Config)
        nowPlayingRepository = NowPlayingRepository(this)
        appearanceStore = AppearanceStore(this)
        backgroundImageStore = BackgroundImageStore(this)
        smartTubeStatus = SmartTubeIntegrationStatus(this)
        dashboardReclaimer = DashboardReclaimer(this)
        // Opt-in only — never start Sentry until the user enables diagnostics.
        CrashReporting.syncWithPrefs(this)
    }
}

fun android.content.Context.app(): HomeMonitorApp = applicationContext as HomeMonitorApp
