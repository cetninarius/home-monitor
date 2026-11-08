package com.custom.vox.homemonitor.dashboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.admin.AdminAuth
import com.custom.vox.homemonitor.app
import com.custom.vox.homemonitor.widgets.DashboardWidget
import com.custom.vox.homemonitor.widgets.WidgetRegistry
import com.custom.vox.homemonitor.widgets.calendar.CalendarWidget
import com.custom.vox.homemonitor.widgets.clock.ClockWidget
import com.custom.vox.homemonitor.widgets.network.NetworkStripWidget
import com.custom.vox.homemonitor.widgets.nowplaying.NowPlayingWidget
import com.custom.vox.homemonitor.widgets.services.ServicesWidget
import com.custom.vox.homemonitor.widgets.todo.TodoWidget
import com.custom.vox.homemonitor.widgets.weather.WeatherWidget
import com.custom.vox.homemonitor.widgets.wf1.Wf1QueueWidget
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Layout policy:
 * - HOME / MEDIA (WF1 off): adaptive compositions driven by **live** playback
 * - WORKFLOW / WORKFLOW_MEDIA (WF1 on): classic 66/34 + left-column NP hero
 *
 * Idle Now Playing preference alone never opens MEDIA / WORKFLOW_MEDIA.
 */
class DashboardController(
    private val root: View,
) {
    private val widgets = mutableListOf<DashboardWidget>()
    private var nowPlayingWidget: NowPlayingWidget? = null
    private var calendarWidget: CalendarWidget? = null
    private var todoWidget: TodoWidget? = null
    private var servicesWidget: ServicesWidget? = null
    private var weatherWidget: WeatherWidget? = null
    private var wf1Widget: Wf1QueueWidget? = null

    /** Live Now Playing card is showing (session + pref). */
    private var mediaActive = false
    private var layoutMode: DashboardLayoutMode = DashboardLayoutMode.HOME
    private var layoutAnimator: Animator? = null
    private var lastChromeKey: Int = -1
    private var transitioning = false

    private var appearanceApplier: AppearanceApplier? = null
    private var settingsPanel: SettingsPanelController? = null
    private var appearanceScope: CoroutineScope? = null
    private var appearanceJob: Job? = null
    private var settingsTickJob: Job? = null

    private val ease = PathInterpolator(0.33f, 0f, 0.2f, 1f)

    fun setup() {
        val ctx = root.context
        val clock = WidgetRegistry.createClock(ctx)
        clock.bind(
            root.findViewById(R.id.clockText),
            root.findViewById(R.id.dateText),
        )
        widgets.add(clock)

        val network = WidgetRegistry.createNetworkStrip(ctx)
        network.bindViews(
            root.findViewById(R.id.networkStatus),
            root.findViewById(R.id.networkDetail),
            root.findViewById(R.id.networkRouter),
        )
        widgets.add(network)

        val nowPlaying = WidgetRegistry.createNowPlaying(ctx)
        nowPlaying.attach(root.findViewById(R.id.nowPlayingSlot))
        nowPlaying.setLayoutModeListener { active ->
            val enabled = ctx.app().dashboardPrefs.nowPlayingEnabled()
            setMediaActive(enabled && active)
        }
        nowPlayingWidget = nowPlaying
        widgets.add(nowPlaying)

        val calendar = WidgetRegistry.createCalendar(ctx)
        calendar.attach(root.findViewById(R.id.calendarSlot))
        calendarWidget = calendar
        widgets.add(calendar)

        val todo = WidgetRegistry.createTodo(ctx)
        todo.attach(root.findViewById(R.id.todoSlot))
        todoWidget = todo
        widgets.add(todo)

        val services = WidgetRegistry.createServices(ctx)
        services.attach(root.findViewById(R.id.servicesSlot))
        servicesWidget = services
        widgets.add(services)

        val weather = WidgetRegistry.createWeather(ctx)
        weather.attach(root.findViewById(R.id.weatherSlot))
        weatherWidget = weather
        widgets.add(weather)

        val wf1 = WidgetRegistry.createWf1(ctx)
        wf1.attach(root.findViewById(R.id.rightSlot))
        wf1Widget = wf1
        widgets.add(wf1)

        appearanceApplier = AppearanceApplier(root, ctx.app().backgroundImageStore)
        appearanceApplier?.apply(ctx.app().appearanceStore.snapshot())

        val panelHost = root as? ViewGroup
        if (panelHost != null) {
            val panel = SettingsPanelController(panelHost) {
                applyWidgetVisibility()
            }
            panel.attach()
            settingsPanel = panel
            root.findViewById<View>(R.id.appearanceEntry)?.setOnClickListener {
                panel.open()
            }
        }

        applyWidgetVisibility()
        setupAdminHint(ctx.app().adminAuth)
    }

    fun start() {
        widgets.forEach { it.onStart() }
        val app = root.context.app()
        appearanceScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        appearanceScope = scope
        appearanceJob = scope.launch {
            app.appearanceStore.observe().collect { state ->
                appearanceApplier?.apply(state)
            }
        }
        settingsTickJob?.cancel()
        settingsTickJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(2_000)
                settingsPanel?.tick()
            }
        }
    }

    fun stop() {
        layoutAnimator?.cancel()
        appearanceJob?.cancel()
        appearanceJob = null
        settingsTickJob?.cancel()
        settingsTickJob = null
        appearanceScope?.cancel()
        appearanceScope = null
        settingsPanel?.close()
        appearanceApplier?.release()
        widgets.forEach { it.onStop() }
    }

    fun isAppearanceOpen(): Boolean = settingsPanel?.isOpen() == true
    fun closeAppearance(): Boolean = settingsPanel?.handleBack() == true
    fun openAppearance() { settingsPanel?.open() }
    fun toggleAppearance() { settingsPanel?.toggle() }

    fun applyWidgetVisibility() {
        val prefs = root.context.app().dashboardPrefs
        if (!prefs.nowPlayingEnabled()) mediaActive = false
        resolveAndApply(animate = true)
        applyServicesVisibility(prefs.servicesEnabled())
        wf1Widget?.syncPolling()
        appearanceApplier?.apply(root.context.app().appearanceStore.snapshot())
    }

    private fun setMediaActive(active: Boolean) {
        if (active == mediaActive) return
        mediaActive = active
        resolveAndApply(animate = true)
    }

    private fun resolveAndApply(animate: Boolean) {
        val prefs = root.context.app().dashboardPrefs
        val next = DashboardLayoutMode.resolve(
            mediaShowing = prefs.nowPlayingEnabled() && mediaActive,
            wf1Enabled = prefs.wf1Enabled(),
        )
        if (next == layoutMode) {
            // Same shell — still may need NP settle if we landed mid-flight.
            if (!transitioning) pinMode(next)
            return
        }
        val from = layoutMode
        layoutMode = next
        if (!animate || !root.isLaidOut) {
            pinMode(next)
            return
        }
        transition(from, next)
    }

    private fun transition(from: DashboardLayoutMode, to: DashboardLayoutMode) {
        layoutAnimator?.cancel()
        transitioning = true
        val focused = root.findFocus()

        val anim: Animator? = when {
            from == DashboardLayoutMode.HOME && to == DashboardLayoutMode.MEDIA ->
                animateHomeToMedia()
            from == DashboardLayoutMode.MEDIA && to == DashboardLayoutMode.HOME ->
                animateMediaToHome()
            from == DashboardLayoutMode.WORKFLOW && to == DashboardLayoutMode.WORKFLOW_MEDIA ->
                animateClassicNowPlaying(enter = true)
            from == DashboardLayoutMode.WORKFLOW_MEDIA && to == DashboardLayoutMode.WORKFLOW ->
                animateClassicNowPlaying(enter = false)
            else -> {
                // Cross-family (e.g. WF1 toggle): soft fade + pin.
                pinMode(to)
                playSoftFade()
                null
            }
        }

        if (anim == null) {
            transitioning = false
            restoreFocus(focused)
            return
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                transitioning = false
                pinMode(to)
                restoreFocus(focused)
            }

            override fun onAnimationCancel(animation: Animator) {
                transitioning = false
            }
        })
        layoutAnimator = anim
        anim.start()
    }

    private fun pinMode(mode: DashboardLayoutMode) {
        val left = leftSlot() ?: return
        val rightRail = rightRail() ?: return
        val rightSlot = rightSlot() ?: return
        val rail = railColumn() ?: return
        val body = homeBody() ?: return
        val grid = homeGrid() ?: return
        val mid = midRow() ?: return
        val bottom = bottomRow() ?: return
        val weather = weatherSlot() ?: return
        val services = servicesSlot() ?: return
        val np = npSlot() ?: return
        val gap = dimen(R.dimen.hm_widget_gap)
        val columnGap = dimen(R.dimen.hm_column_gap)
        val rowGap = dimen(R.dimen.hm_row_gap)
        val bottomH = dimen(R.dimen.hm_home_widget_height)
        // MEDIA bottom strip matches WORKFLOW_MEDIA compact services/weather height.
        val mediaBottomH = dimen(R.dimen.hm_home_widget_height_strip)

        when (mode) {
            DashboardLayoutMode.HOME -> {
                ensureNpInLeft(left, np, body)
                nowPlayingWidget?.setRailPresentation(false)
                setColWeight(left, 100f)
                setColWeight(rightRail, 0f)
                setColWeight(rightSlot, 0f)
                rightRail.visibility = View.GONE
                rightSlot.visibility = View.GONE
                left.setPaddingRelative(left.paddingStart, left.paddingTop, 0, left.paddingBottom)
                rail.visibility = View.VISIBLE
                setColWeight(rail, 30f)
                setColWeight(grid, 70f)
                placeInRail(rail, weather, services, gap)
                applyHomeColumnGutters(active = true)
                bottom.visibility = View.GONE
                resetBodyFlex(body, mid)
                hideLeftHero(np)
                applyModeChrome(mode)
            }

            DashboardLayoutMode.MEDIA -> {
                nowPlayingWidget?.setRailPresentation(true)
                ensureNpInRail(rightRail, np)
                setColWeight(left, 67f)
                setColWeight(rightRail, 33f)
                setColWeight(rightSlot, 0f)
                rightRail.visibility = View.VISIBLE
                rightSlot.visibility = View.GONE
                left.setPaddingRelative(
                    left.paddingStart, left.paddingTop, columnGap, left.paddingBottom,
                )
                rail.visibility = View.GONE
                setColWeight(rail, 0f)
                setColWeight(grid, 100f)
                applyHomeColumnGutters(active = false)
                // Same strip height as WORKFLOW_MEDIA; mid (calendar/todo) fills the rest.
                placeInBottomRow(bottom, services, weather, gap, mediaBottomH, rowGap)
                bottom.visibility = View.VISIBLE
                expandMidRow(body, mid)
                np.visibility = View.VISIBLE
                np.alpha = 1f
                setVertWeight(np, 1f)
                applyModeChrome(mode)
            }

            DashboardLayoutMode.WORKFLOW -> {
                nowPlayingWidget?.setRailPresentation(false)
                ensureNpInLeft(left, np, body)
                setColWeight(left, 66f)
                setColWeight(rightRail, 0f)
                setColWeight(rightSlot, 34f)
                rightRail.visibility = View.GONE
                rightSlot.visibility = View.VISIBLE
                left.setPaddingRelative(
                    left.paddingStart, left.paddingTop, columnGap, left.paddingBottom,
                )
                rail.visibility = View.GONE
                setColWeight(rail, 0f)
                setColWeight(grid, 100f)
                applyHomeColumnGutters(active = false)
                placeInBottomRow(bottom, services, weather, gap, bottomH, rowGap)
                bottom.visibility = View.VISIBLE
                settleClassicHome(np, body, grid, mid, bottom)
                applyModeChrome(mode)
            }

            DashboardLayoutMode.WORKFLOW_MEDIA -> {
                nowPlayingWidget?.setRailPresentation(false)
                ensureNpInLeft(left, np, body)
                setColWeight(left, 66f)
                setColWeight(rightRail, 0f)
                setColWeight(rightSlot, 34f)
                rightRail.visibility = View.GONE
                rightSlot.visibility = View.VISIBLE
                left.setPaddingRelative(
                    left.paddingStart, left.paddingTop, columnGap, left.paddingBottom,
                )
                rail.visibility = View.GONE
                setColWeight(rail, 0f)
                setColWeight(grid, 100f)
                applyHomeColumnGutters(active = false)
                placeInBottomRow(bottom, services, weather, gap, bottomH, rowGap)
                bottom.visibility = View.VISIBLE
                settleClassicNowPlaying(np, body, grid, mid, bottom)
                applyModeChrome(mode)
            }
        }
        root.findViewById<View>(R.id.dashboardContent)?.requestLayout()
    }

    // region HOME ↔ MEDIA animations

    private fun animateHomeToMedia(): Animator {
        val left = leftSlot()!!
        val rightRail = rightRail()!!
        val rightSlot = rightSlot()!!
        val rail = railColumn()!!
        val body = homeBody()!!
        val grid = homeGrid()!!
        val mid = midRow()!!
        val bottom = bottomRow()!!
        val weather = weatherSlot()!!
        val services = servicesSlot()!!
        val np = npSlot()!!
        val gap = dimen(R.dimen.hm_widget_gap)
        val columnGap = dimen(R.dimen.hm_column_gap)
        val rowGap = dimen(R.dimen.hm_row_gap)
        val mediaBottomH = dimen(R.dimen.hm_home_widget_height_strip)

        nowPlayingWidget?.setRailPresentation(true)
        ensureNpInRail(rightRail, np)
        rightSlot.visibility = View.GONE
        setColWeight(rightSlot, 0f)
        rail.visibility = View.GONE
        setColWeight(rail, 0f)
        setColWeight(grid, 100f)
        placeInBottomRow(bottom, services, weather, gap, 0, rowGap)
        bottom.visibility = View.VISIBLE
        expandMidRow(body, mid)
        rightRail.visibility = View.VISIBLE
        setColWeight(rightRail, 0f)
        np.visibility = View.VISIBLE
        np.alpha = 0f
        np.translationX = 48f * root.resources.displayMetrics.density
        setVertWeight(np, 1f)
        left.setPaddingRelative(left.paddingStart, left.paddingTop, 0, left.paddingBottom)
        applyModeChrome(DashboardLayoutMode.MEDIA)

        val startLeft = 100f
        val endLeft = 67f
        val startRail = 0f
        val endRail = 33f
        val startPad = 0
        val endPad = columnGap
        val startBottom = 0
        val endBottom = mediaBottomH

        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = HOME_MEDIA_MS
            interpolator = ease
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                setColWeight(left, startLeft + (endLeft - startLeft) * t)
                setColWeight(rightRail, startRail + (endRail - startRail) * t)
                left.setPaddingRelative(
                    left.paddingStart,
                    left.paddingTop,
                    (startPad + (endPad - startPad) * t).roundToInt(),
                    left.paddingBottom,
                )
                bottom.layoutParams = (bottom.layoutParams as LinearLayout.LayoutParams).also {
                    it.height = (startBottom + (endBottom - startBottom) * t).roundToInt()
                    it.weight = 0f
                    it.topMargin = rowGap
                }
                np.alpha = t
                np.translationX = (1f - t) * 48f * root.resources.displayMetrics.density
                left.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    np.translationX = 0f
                    np.alpha = 1f
                }
            })
        }
    }

    private fun animateMediaToHome(): Animator {
        val left = leftSlot()!!
        val rightRail = rightRail()!!
        val rail = railColumn()!!
        val body = homeBody()!!
        val grid = homeGrid()!!
        val mid = midRow()!!
        val bottom = bottomRow()!!
        val weather = weatherSlot()!!
        val services = servicesSlot()!!
        val np = npSlot()!!
        val gap = dimen(R.dimen.hm_widget_gap)
        val columnGap = dimen(R.dimen.hm_column_gap)
        val rowGap = dimen(R.dimen.hm_row_gap)
        val startBottomH = bottom.height.coerceAtLeast(dimen(R.dimen.hm_home_widget_height_strip))

        val startLeft = 67f
        val endLeft = 100f
        val startRailW = 33f
        val endRailW = 0f
        val startPad = columnGap
        val endPad = 0

        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = HOME_MEDIA_MS
            interpolator = ease
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                setColWeight(left, startLeft + (endLeft - startLeft) * t)
                setColWeight(rightRail, startRailW + (endRailW - startRailW) * t)
                left.setPaddingRelative(
                    left.paddingStart,
                    left.paddingTop,
                    (startPad + (endPad - startPad) * t).roundToInt(),
                    left.paddingBottom,
                )
                bottom.layoutParams = (bottom.layoutParams as LinearLayout.LayoutParams).also {
                    it.height = (startBottomH * (1f - t)).roundToInt()
                    it.weight = 0f
                    it.topMargin = rowGap
                }
                np.alpha = 1f - t
                np.translationX = t * 36f * root.resources.displayMetrics.density
                if (t >= 0.45f) applyModeChrome(DashboardLayoutMode.HOME)
                left.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    np.translationX = 0f
                    ensureNpInLeft(left, np, body)
                    nowPlayingWidget?.setRailPresentation(false)
                    hideLeftHero(np)
                    rightRail.visibility = View.GONE
                    setColWeight(rightRail, 0f)
                    placeInRail(rail, weather, services, gap)
                    rail.visibility = View.VISIBLE
                    setColWeight(rail, 30f)
                    setColWeight(grid, 70f)
                    applyHomeColumnGutters(active = true)
                    bottom.visibility = View.GONE
                    resetBodyFlex(body, mid)
                    applyModeChrome(DashboardLayoutMode.HOME)
                }
            })
        }
    }

    // endregion

    // region Classic WF1 Now Playing animations

    private fun animateClassicNowPlaying(enter: Boolean): Animator? {
        val left = leftSlot() ?: return null
        val body = homeBody() ?: return null
        val grid = homeGrid() ?: return null
        val mid = midRow() ?: return null
        val bottom = bottomRow() ?: return null
        val np = npSlot() ?: return null
        val weather = weatherSlot() ?: return null
        val services = servicesSlot() ?: return null
        val gap = dimen(R.dimen.hm_widget_gap)
        val bottomH = dimen(R.dimen.hm_home_widget_height)
        val rowGap = dimen(R.dimen.hm_row_gap)

        nowPlayingWidget?.setRailPresentation(false)
        ensureNpInLeft(left, np, body)
        placeInBottomRow(bottom, services, weather, gap, bottomH, rowGap)
        bottom.visibility = View.VISIBLE

        if (body.height <= 0 || mid.height <= 0) {
            root.post {
                if (!transitioning) return@post
                val again = animateClassicNowPlaying(enter)
                if (again != null) {
                    layoutAnimator = again
                    again.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            transitioning = false
                            pinMode(if (enter) DashboardLayoutMode.WORKFLOW_MEDIA else DashboardLayoutMode.WORKFLOW)
                        }
                    })
                    again.start()
                }
            }
            return null
        }

        val snipH = dimen(R.dimen.hm_home_widget_height_snip)
        val stripH = dimen(R.dimen.hm_home_widget_height_strip)
        val npGap = dimen(R.dimen.hm_np_row_gap)
        val npSlotMargin = dimen(R.dimen.hm_np_slot_margin_top)
        val npBodyMargin = dimen(R.dimen.hm_np_grid_margin_top)
        val defaultBodyMargin = (20f * root.resources.displayMetrics.density).roundToInt()
        val defaultSlotMargin = (16f * root.resources.displayMetrics.density).roundToInt()
        val miniBodyH = snipH + npGap + stripH
        val slidePx = root.resources.displayMetrics.density * 24f

        val topStripH = (root.findViewById<View>(R.id.clockContainer)?.parent as? View)?.height ?: 0
        val fullBodyTarget = (left.height - topStripH - defaultBodyMargin).coerceAtLeast(miniBodyH)
        val naturalMidH = (fullBodyTarget - bottomH - rowGap).coerceAtLeast(snipH + 48)

        val slotLp = np.layoutParams as LinearLayout.LayoutParams
        val bodyLp = body.layoutParams as LinearLayout.LayoutParams
        val midLp = mid.layoutParams as LinearLayout.LayoutParams
        val bottomLp = bottom.layoutParams as LinearLayout.LayoutParams

        val startSlotW: Float
        val endSlotW: Float
        val startSlotMargin: Int
        val endSlotMargin: Int
        val startBodyH: Int
        val endBodyH: Int
        val startBodyMargin: Int
        val endBodyMargin: Int
        val startMidH: Int
        val endMidH: Int
        val startBottomH: Int
        val endBottomH: Int
        val startBottomMargin: Int
        val endBottomMargin: Int
        val startSlotAlpha: Float
        val endSlotAlpha: Float
        val startSlotTy: Float
        val endSlotTy: Float

        if (enter) {
            startSlotW = 0f
            endSlotW = 1f
            startSlotMargin = defaultSlotMargin
            endSlotMargin = npSlotMargin
            startBodyH = body.height.coerceAtLeast(fullBodyTarget)
            endBodyH = miniBodyH
            startBodyMargin = bodyLp.topMargin.takeIf { it > 0 } ?: defaultBodyMargin
            endBodyMargin = npBodyMargin
            startMidH = mid.height.coerceAtLeast(naturalMidH)
            endMidH = snipH
            startBottomH = bottom.height.coerceAtLeast(bottomH)
            endBottomH = stripH
            startBottomMargin = bottomLp.topMargin.takeIf { it > 0 } ?: rowGap
            endBottomMargin = npGap
            startSlotAlpha = 0f
            endSlotAlpha = 1f
            startSlotTy = -slidePx
            endSlotTy = 0f
            np.visibility = View.VISIBLE
            np.alpha = 0f
            np.translationY = startSlotTy
            applyModeChrome(DashboardLayoutMode.WORKFLOW_MEDIA)
            slotLp.height = 0
            slotLp.weight = 0f
            np.layoutParams = slotLp
        } else {
            startSlotW = slotLp.weight.coerceAtLeast(0.01f)
            endSlotW = 0f
            startSlotMargin = slotLp.topMargin.takeIf { it > 0 } ?: npSlotMargin
            endSlotMargin = defaultSlotMargin
            startBodyH = body.height.coerceAtLeast(miniBodyH)
            endBodyH = fullBodyTarget
            startBodyMargin = bodyLp.topMargin.takeIf { it > 0 } ?: npBodyMargin
            endBodyMargin = defaultBodyMargin
            startMidH = mid.height.coerceAtLeast(snipH)
            endMidH = naturalMidH
            startBottomH = bottom.height.coerceAtLeast(stripH)
            endBottomH = bottomH
            startBottomMargin = bottomLp.topMargin.takeIf { it > 0 } ?: npGap
            endBottomMargin = rowGap
            startSlotAlpha = np.alpha.coerceIn(0.2f, 1f)
            endSlotAlpha = 0f
            startSlotTy = 0f
            endSlotTy = -slidePx * 0.35f
            applyModeChrome(DashboardLayoutMode.WORKFLOW_MEDIA)
        }

        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (enter) NP_ENTER_MS else NP_EXIT_MS
            interpolator = ease
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                slotLp.weight = startSlotW + (endSlotW - startSlotW) * t
                slotLp.height = 0
                slotLp.width = LinearLayout.LayoutParams.MATCH_PARENT
                slotLp.topMargin =
                    (startSlotMargin + (endSlotMargin - startSlotMargin) * t).roundToInt()
                np.layoutParams = slotLp

                bodyLp.height = (startBodyH + (endBodyH - startBodyH) * t).roundToInt()
                bodyLp.weight = 0f
                bodyLp.topMargin =
                    (startBodyMargin + (endBodyMargin - startBodyMargin) * t).roundToInt()
                body.layoutParams = bodyLp

                midLp.height = (startMidH + (endMidH - startMidH) * t).roundToInt().coerceAtLeast(0)
                midLp.weight = 0f
                mid.layoutParams = midLp

                bottomLp.height =
                    (startBottomH + (endBottomH - startBottomH) * t).roundToInt().coerceAtLeast(0)
                bottomLp.weight = 0f
                bottomLp.topMargin =
                    (startBottomMargin + (endBottomMargin - startBottomMargin) * t).roundToInt()
                bottom.layoutParams = bottomLp

                np.alpha = startSlotAlpha + (endSlotAlpha - startSlotAlpha) * t
                np.translationY = startSlotTy + (endSlotTy - startSlotTy) * t
                if (!enter && t >= 0.4f) applyModeChrome(DashboardLayoutMode.WORKFLOW)
                left.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    np.translationY = 0f
                    if (enter) {
                        settleClassicNowPlaying(np, body, grid, mid, bottom)
                        applyModeChrome(DashboardLayoutMode.WORKFLOW_MEDIA)
                    } else {
                        settleClassicHome(np, body, grid, mid, bottom)
                        applyModeChrome(DashboardLayoutMode.WORKFLOW)
                    }
                }
            })
        }
    }

    // endregion

    private fun settleClassicHome(
        slot: FrameLayout,
        body: LinearLayout,
        grid: LinearLayout,
        mid: LinearLayout,
        bottom: LinearLayout,
    ) {
        val bottomH = dimen(R.dimen.hm_home_widget_height)
        val rowGap = dimen(R.dimen.hm_row_gap)
        val defaultBodyMargin = (20f * root.resources.displayMetrics.density).roundToInt()
        hideLeftHero(slot)
        body.layoutParams = (body.layoutParams as LinearLayout.LayoutParams).also {
            it.height = 0
            it.weight = 1f
            it.topMargin = defaultBodyMargin
        }
        grid.layoutParams = (grid.layoutParams as LinearLayout.LayoutParams).also {
            it.height = LinearLayout.LayoutParams.MATCH_PARENT
            it.weight = 100f
            it.topMargin = 0
        }
        mid.layoutParams = (mid.layoutParams as LinearLayout.LayoutParams).also {
            it.height = 0
            it.weight = 1f
        }
        bottom.layoutParams = (bottom.layoutParams as LinearLayout.LayoutParams).also {
            it.height = bottomH
            it.weight = 0f
            it.topMargin = rowGap
        }
        bottom.visibility = View.VISIBLE
    }

    private fun settleClassicNowPlaying(
        slot: FrameLayout,
        body: LinearLayout,
        grid: LinearLayout,
        mid: LinearLayout,
        bottom: LinearLayout,
    ) {
        val snipH = dimen(R.dimen.hm_home_widget_height_snip)
        val stripH = dimen(R.dimen.hm_home_widget_height_strip)
        val npGap = dimen(R.dimen.hm_np_row_gap)
        val npSlotMargin = dimen(R.dimen.hm_np_slot_margin_top)
        val npBodyMargin = dimen(R.dimen.hm_np_grid_margin_top)
        val miniBodyH = snipH + npGap + stripH

        slot.visibility = View.VISIBLE
        slot.alpha = 1f
        slot.translationY = 0f
        slot.layoutParams = (slot.layoutParams as LinearLayout.LayoutParams).also {
            it.width = LinearLayout.LayoutParams.MATCH_PARENT
            it.height = 0
            it.weight = 1f
            it.topMargin = npSlotMargin
        }
        mid.layoutParams = (mid.layoutParams as LinearLayout.LayoutParams).also {
            it.height = snipH
            it.weight = 0f
        }
        bottom.layoutParams = (bottom.layoutParams as LinearLayout.LayoutParams).also {
            it.height = stripH
            it.weight = 0f
            it.topMargin = npGap
        }
        bottom.visibility = View.VISIBLE
        grid.layoutParams = (grid.layoutParams as LinearLayout.LayoutParams).also {
            it.height = LinearLayout.LayoutParams.MATCH_PARENT
            it.weight = 100f
            it.topMargin = 0
        }
        body.layoutParams = (body.layoutParams as LinearLayout.LayoutParams).also {
            it.height = miniBodyH
            it.weight = 0f
            it.topMargin = npBodyMargin
        }
    }

    private fun hideLeftHero(slot: FrameLayout) {
        slot.visibility = View.GONE
        slot.alpha = 1f
        slot.translationY = 0f
        slot.translationX = 0f
        slot.layoutParams = (slot.layoutParams as LinearLayout.LayoutParams).also {
            it.width = LinearLayout.LayoutParams.MATCH_PARENT
            it.height = 0
            it.weight = 0f
        }
    }

    private fun resetBodyFlex(body: LinearLayout, mid: LinearLayout) {
        val defaultBodyMargin = (20f * root.resources.displayMetrics.density).roundToInt()
        body.layoutParams = (body.layoutParams as LinearLayout.LayoutParams).also {
            it.height = 0
            it.weight = 1f
            it.topMargin = defaultBodyMargin
        }
        mid.layoutParams = (mid.layoutParams as LinearLayout.LayoutParams).also {
            it.height = 0
            it.weight = 1f
        }
    }

    /** MEDIA: calendar/todo absorb leftover height above the services/weather strip. */
    private fun expandMidRow(body: LinearLayout, mid: LinearLayout) {
        resetBodyFlex(body, mid)
        mid.layoutParams = (mid.layoutParams as LinearLayout.LayoutParams).also {
            it.height = 0
            it.weight = 1f
        }
    }

    private fun ensureNpInLeft(left: LinearLayout, npSlot: FrameLayout, body: LinearLayout) {
        if (npSlot.parent === left) return
        (npSlot.parent as? ViewGroup)?.removeView(npSlot)
        val bodyIndex = left.indexOfChild(body).coerceAtLeast(0)
        left.addView(npSlot, bodyIndex)
    }

    private fun ensureNpInRail(rightRail: LinearLayout, npSlot: FrameLayout) {
        if (npSlot.parent === rightRail) {
            setVertWeight(npSlot, 1f)
            return
        }
        (npSlot.parent as? ViewGroup)?.removeView(npSlot)
        rightRail.addView(
            npSlot,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
    }

    private fun applyServicesVisibility(servicesOn: Boolean) {
        val services = servicesSlot() ?: return
        val weather = weatherSlot() ?: return
        val gap = dimen(R.dimen.hm_widget_gap)
        if ((services.parent as? View)?.id == R.id.railColumn) {
            services.visibility = if (servicesOn) View.VISIBLE else View.GONE
            return
        }
        val bottom = bottomRow() ?: return
        if (servicesOn) {
            services.visibility = View.VISIBLE
            (services.layoutParams as LinearLayout.LayoutParams).also {
                it.width = 0; it.weight = 1f; it.marginEnd = gap
                services.layoutParams = it
            }
            (weather.layoutParams as LinearLayout.LayoutParams).also {
                it.width = 0; it.weight = 1f; it.marginStart = gap
                weather.layoutParams = it
            }
            bottom.weightSum = 2f
        } else {
            services.visibility = View.GONE
            (weather.layoutParams as LinearLayout.LayoutParams).also {
                it.width = 0; it.weight = 1f; it.marginStart = 0
                weather.layoutParams = it
            }
            bottom.weightSum = 1f
        }
    }

    private fun placeInRail(
        rail: LinearLayout,
        weather: FrameLayout,
        services: FrameLayout,
        gap: Int,
    ) {
        reparent(rail, weather)
        reparent(rail, services)
        weather.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ).also { it.bottomMargin = gap }
        services.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ).also { it.topMargin = gap }
        weather.visibility = View.VISIBLE
    }

    /**
     * HOME: breathe between rail and calendar; nudge calendar a touch narrower
     * so event cards are not glued to weather/services.
     */
    private fun applyHomeColumnGutters(active: Boolean) {
        val rail = railColumn() ?: return
        val calendar = calendarSlot() ?: return
        val todo = todoSlot() ?: return
        val gutter = dimen(R.dimen.hm_column_gap)
        val widgetGap = dimen(R.dimen.hm_widget_gap)
        rail.layoutParams = (rail.layoutParams as LinearLayout.LayoutParams).also {
            it.marginEnd = if (active) gutter else 0
        }
        calendar.layoutParams = (calendar.layoutParams as LinearLayout.LayoutParams).also {
            it.width = 0
            it.weight = if (active) 0.88f else 1f
            it.marginEnd = widgetGap
        }
        todo.layoutParams = (todo.layoutParams as LinearLayout.LayoutParams).also {
            it.width = 0
            it.weight = if (active) 1.12f else 1f
            it.marginStart = widgetGap
        }
    }

    private fun placeInBottomRow(
        bottom: LinearLayout,
        services: FrameLayout,
        weather: FrameLayout,
        gap: Int,
        height: Int,
        topMargin: Int,
    ) {
        reparent(bottom, services)
        reparent(bottom, weather)
        bottom.layoutParams = (bottom.layoutParams as LinearLayout.LayoutParams).also {
            it.height = height
            it.weight = 0f
            it.topMargin = topMargin
        }
        services.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            .also { it.marginEnd = gap }
        weather.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            .also { it.marginStart = gap }
        weather.visibility = View.VISIBLE
    }

    private fun reparent(parent: ViewGroup, child: View) {
        val current = child.parent as? ViewGroup
        if (current === parent) return
        current?.removeView(child)
        parent.addView(child)
    }

    private fun setColWeight(view: View, weight: Float) {
        val lp = view.layoutParams as? LinearLayout.LayoutParams ?: return
        lp.width = 0
        lp.weight = weight
        if (lp.height == LinearLayout.LayoutParams.WRAP_CONTENT && weight > 0f) {
            lp.height = LinearLayout.LayoutParams.MATCH_PARENT
        }
        view.layoutParams = lp
    }

    private fun setVertWeight(view: View, weight: Float) {
        val lp = view.layoutParams as? LinearLayout.LayoutParams ?: return
        lp.width = LinearLayout.LayoutParams.MATCH_PARENT
        lp.height = 0
        lp.weight = weight
        view.layoutParams = lp
    }

    /**
     * Per-mode chrome:
     * - HOME: full cards; services side-by-side in the left rail
     * - MEDIA: strip-height services/weather (same as WF1+NP); calendar/todo stay expanded
     * - WORKFLOW: full cards
     * - WORKFLOW_MEDIA: classic compact on all four
     */
    private fun applyModeChrome(mode: DashboardLayoutMode) {
        val key = when (mode) {
            DashboardLayoutMode.HOME -> 0
            DashboardLayoutMode.MEDIA -> 1
            DashboardLayoutMode.WORKFLOW -> 2
            DashboardLayoutMode.WORKFLOW_MEDIA -> 3
        }
        if (key == lastChromeKey) return
        lastChromeKey = key
        when (mode) {
            DashboardLayoutMode.HOME -> {
                servicesWidget?.setCompact(false)
                servicesWidget?.setRailNarrow(true)
                weatherWidget?.setCompact(false)
                calendarWidget?.setCompact(false)
                todoWidget?.setCompact(false)
            }
            DashboardLayoutMode.MEDIA -> {
                servicesWidget?.setCompact(true)
                servicesWidget?.setRailNarrow(false)
                weatherWidget?.setCompact(true)
                calendarWidget?.setCompact(false)
                todoWidget?.setCompact(false)
            }
            DashboardLayoutMode.WORKFLOW -> {
                servicesWidget?.setCompact(false)
                servicesWidget?.setRailNarrow(false)
                weatherWidget?.setCompact(false)
                calendarWidget?.setCompact(false)
                todoWidget?.setCompact(false)
            }
            DashboardLayoutMode.WORKFLOW_MEDIA -> {
                servicesWidget?.setCompact(true)
                servicesWidget?.setRailNarrow(false)
                weatherWidget?.setCompact(true)
                calendarWidget?.setCompact(true)
                todoWidget?.setCompact(true)
            }
        }
    }

    private fun playSoftFade() {
        val content = root.findViewById<View>(R.id.dashboardContent) ?: return
        layoutAnimator?.cancel()
        val fade = ValueAnimator.ofFloat(0.88f, 1f).apply {
            duration = CROSS_FADE_MS
            interpolator = ease
            addUpdateListener { content.alpha = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    content.alpha = 1f
                }
            })
        }
        layoutAnimator = fade
        fade.start()
    }

    private fun restoreFocus(focused: View?) {
        if (focused != null && focused.isShown && focused.isFocusable) {
            focused.requestFocus()
        } else {
            preferredFocus()?.requestFocus()
        }
    }

    fun preferredFocus(): View? {
        val np = nowPlayingWidget?.preferredFocusView()?.takeIf { it.isShown && it.isFocusable }
        if (mediaActive && np != null) return np
        return todoWidget?.preferredFocusView()?.takeIf { it.isShown }
            ?: wf1Widget?.preferredFocusView()?.takeIf { it.isShown }
            ?: np
    }

    fun updateAdminHint(url: String?, auth: AdminAuth) {
        val banner = root.findViewById<View>(R.id.adminHintBanner) ?: return
        if (auth.isHintDismissed() || url.isNullOrBlank()) {
            banner.visibility = View.GONE
            return
        }
        banner.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.adminHintUrl)?.text = url
        val tokenView = root.findViewById<TextView>(R.id.adminHintToken)
        if (auth.shouldRevealTokenOnTv()) {
            tokenView?.visibility = View.VISIBLE
            tokenView?.text = root.context.getString(
                R.string.admin_hint_token_fmt,
                auth.getOrCreateToken(),
            )
        } else {
            tokenView?.visibility = View.GONE
        }
    }

    private fun setupAdminHint(auth: AdminAuth) {
        val banner = root.findViewById<ViewGroup>(R.id.adminHintBanner) ?: return
        val dismiss = root.findViewById<View>(R.id.adminHintDismiss)
        dismiss?.setOnClickListener {
            auth.dismissHint()
            banner.visibility = View.GONE
        }
        if (auth.isHintDismissed()) banner.visibility = View.GONE
    }

    private fun leftSlot() = root.findViewById<LinearLayout>(R.id.leftSlot)
    private fun rightRail() = root.findViewById<LinearLayout>(R.id.rightRail)
    private fun rightSlot() = root.findViewById<FrameLayout>(R.id.rightSlot)
    private fun railColumn() = root.findViewById<LinearLayout>(R.id.railColumn)
    private fun homeBody() = root.findViewById<LinearLayout>(R.id.homeBody)
    private fun homeGrid() = root.findViewById<LinearLayout>(R.id.homeGrid)
    private fun midRow() = root.findViewById<LinearLayout>(R.id.midHomeRow)
    private fun bottomRow() = root.findViewById<LinearLayout>(R.id.bottomHomeRow)
    private fun weatherSlot() = root.findViewById<FrameLayout>(R.id.weatherSlot)
    private fun servicesSlot() = root.findViewById<FrameLayout>(R.id.servicesSlot)
    private fun calendarSlot() = root.findViewById<FrameLayout>(R.id.calendarSlot)
    private fun todoSlot() = root.findViewById<FrameLayout>(R.id.todoSlot)
    private fun npSlot() = root.findViewById<FrameLayout>(R.id.nowPlayingSlot)
    private fun dimen(id: Int) = root.resources.getDimensionPixelSize(id)

    object LayoutPolicy {
        fun mode(
            nowPlayingEnabled: Boolean,
            wf1Enabled: Boolean,
            mediaActive: Boolean = false,
        ): DashboardLayoutMode =
            DashboardLayoutMode.resolve(
                mediaShowing = nowPlayingEnabled && mediaActive,
                wf1Enabled = wf1Enabled,
            )
    }

    companion object {
        private const val HOME_MEDIA_MS = 620L
        private const val NP_ENTER_MS = 900L
        private const val NP_EXIT_MS = 720L
        private const val CROSS_FADE_MS = 340L
    }
}
