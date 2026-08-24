package com.noyorin.balanceisland.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.noyorin.balanceisland.localization.AppLanguagePreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.RoundedCorner
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.noyorin.balanceisland.R
import com.noyorin.balanceisland.data.BalanceRepository
import com.noyorin.balanceisland.data.BalanceSnapshot
import com.noyorin.balanceisland.data.Provider
import com.noyorin.balanceisland.data.SnapshotStatus
import com.noyorin.balanceisland.display.BalanceContentMode
import com.noyorin.balanceisland.display.BalanceTextFormatter
import com.noyorin.balanceisland.display.OverlayDisplayPreferences
import com.noyorin.balanceisland.display.StatusBarContrast
import com.noyorin.balanceisland.display.StatusBarVisualStyle
import com.noyorin.balanceisland.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** A compact status-bar text overlay. It intentionally avoids the center cutout. */
class IslandOverlayService : Service() {
    private val strings get() = AppLanguagePreferences.wrap(this)
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguagePreferences.wrap(newBase))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var repository: BalanceRepository
    private lateinit var displayPreferences: OverlayDisplayPreferences
    private lateinit var runtimePreferences: ServiceRuntimePreferences
    private var visibleAccountIndex = 0
    private var showDailyDetail = false
    private var refreshInProgress = false
    private var explicitStop = false

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            applyPosition()
            render()
            if (intent?.action == OverlayDisplayPreferences.ACTION_DISPLAY_SETTINGS_CHANGED ||
                intent?.action == AppLanguagePreferences.ACTION_LANGUAGE_CHANGED
            ) {
                startForeground(NOTIFICATION_ID, buildNotification())
                scheduleNextRefresh()
            }
        }
    }

    private val refreshRunnable = Runnable { refreshNow() }

    private val rotateRunnable = object : Runnable {
        override fun run() {
            val count = visibleSnapshots().size
            if (displayPreferences.contentMode() == BalanceContentMode.AUTO_ROTATE && count > 0) {
                showDailyDetail = !showDailyDetail
                if (!showDailyDetail && count > 1) {
                    visibleAccountIndex = (visibleAccountIndex + 1) % count
                }
            } else {
                showDailyDetail = false
                visibleAccountIndex = if (count > 1) (visibleAccountIndex + 1) % count else 0
            }
            render()
            handler.postDelayed(this, ROTATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = BalanceRepository(this)
        displayPreferences = OverlayDisplayPreferences(this)
        runtimePreferences = ServiceRuntimePreferences(this)
        runtimePreferences.setServiceRunning(true)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!Settings.canDrawOverlays(this)) {
            runtimePreferences.setDesiredRunning(false)
            runtimePreferences.setServiceRunning(false)
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        root = FrameLayout(this).apply {
            setOnClickListener { showNextAccount() }
            setOnLongClickListener {
                openSettings()
                true
            }
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayHeightPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        applyPosition()
        windowManager.addView(root, params)

        val filter = IntentFilter().apply {
            addAction(BalanceRepository.ACTION_BALANCE_UPDATED)
            addAction(OverlayDisplayPreferences.ACTION_DISPLAY_SETTINGS_CHANGED)
            addAction(AppLanguagePreferences.ACTION_LANGUAGE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(updateReceiver, filter)
        }
        handler.postDelayed(rotateRunnable, ROTATE_INTERVAL_MS)
        render()
        refreshNow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                explicitStop = true
                runtimePreferences.clearRunningState()
                cancelScheduledRestart(this)
                stopSelf()
            }
            ACTION_REFRESH -> refreshNow()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(updateReceiver) }
        if (::root.isInitialized && root.isAttachedToWindow) windowManager.removeView(root)
        scope.cancel()
        if (::runtimePreferences.isInitialized) {
            runtimePreferences.setServiceRunning(false)
            if (!explicitStop && runtimePreferences.autoRestartEnabled() &&
                runtimePreferences.desiredRunning()
            ) {
                scheduleRestart(this)
            }
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (::runtimePreferences.isInitialized && runtimePreferences.autoRestartEnabled() &&
            runtimePreferences.desiredRunning()
        ) {
            scheduleRestart(this)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun applyPosition() {
        if (!::params.isInitialized) return
        val preset = displayPreferences.position()
        params.gravity = Gravity.TOP or if (preset.alignStart) Gravity.START else Gravity.END
        val baseHorizontalInset = if (preset.roundedCornerSafeArea) {
            safeHorizontalInsetPx(preset.alignStart)
        } else {
            dp(MIN_EDGE_INSET_DP)
        }
        params.x = baseHorizontalInset + dp(displayPreferences.horizontalOffsetDp())
        params.y = dp(displayPreferences.verticalOffsetDp())
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = overlayHeightPx()
        if (::root.isInitialized && root.isAttachedToWindow) {
            windowManager.updateViewLayout(root, params)
        }
    }

    private fun render() {
        if (!::root.isInitialized) return
        val snapshots = visibleSnapshots()
        if (snapshots.isNotEmpty()) visibleAccountIndex %= snapshots.size else visibleAccountIndex = 0
        val configuredTextColor = displayPreferences.textColor().argb
        val visualStyle = displayPreferences.visualStyle()
        val snapshot = snapshots.getOrNull(visibleAccountIndex)
        val displayTextColor = when (snapshot?.status) {
            SnapshotStatus.CRITICAL -> Color.rgb(255, 82, 96)
            SnapshotStatus.WARNING -> Color.rgb(255, 166, 61)
            SnapshotStatus.ERROR -> Color.rgb(255, 100, 112)
            else -> configuredTextColor
        }
        val hasBackground = visualStyle == StatusBarVisualStyle.TRANSLUCENT_PILL ||
            visualStyle == StatusBarVisualStyle.ADAPTIVE_PILL
        root.background = when (visualStyle) {
            StatusBarVisualStyle.TRANSLUCENT_PILL ->
                roundedBackground(Color.argb(158, 0, 0, 0), 14f)
            StatusBarVisualStyle.ADAPTIVE_PILL ->
                roundedBackground(StatusBarContrast.backgroundColorFor(displayTextColor), 14f)
            StatusBarVisualStyle.TEXT_ONLY,
            StatusBarVisualStyle.OUTLINED_TEXT -> null
        }
        root.elevation = if (hasBackground) dp(5).toFloat() else 0f
        root.setPadding(dp(if (hasBackground) 6 else 1), 0, dp(if (hasBackground) 6 else 1), 0)
        root.removeAllViews()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (snapshots.isEmpty()) {
            row.addView(providerIcon(null), linearParams(dp(17), dp(17)))
            row.addView(
                statusText(
                    strings.getString(R.string.configure_api),
                    configuredTextColor,
                    outlined = visualStyle == StatusBarVisualStyle.OUTLINED_TEXT
                )
            )
        } else {
            checkNotNull(snapshot)
            val outlined = visualStyle == StatusBarVisualStyle.OUTLINED_TEXT
            row.addView(providerIcon(snapshot.provider), linearParams(dp(17), dp(17)))
            val sameProviderCount = snapshots.count { it.provider == snapshot.provider }
            if (sameProviderCount > 1 || snapshot.accountLabel.isNotBlank()) {
                row.addView(
                    statusText(
                        "［${snapshot.accountDisplayLabel}］",
                        displayTextColor,
                        10.5f,
                        outlined
                    )
                )
            }
            val showToday = displayPreferences.contentMode() == BalanceContentMode.TODAY_AND_BALANCE ||
                (displayPreferences.contentMode() == BalanceContentMode.AUTO_ROTATE && showDailyDetail)
            val compactText = BalanceTextFormatter.compact(this, snapshot, showToday)
            row.addView(statusText(" $compactText", displayTextColor, 11.5f, outlined).apply {
                maxWidth = dp(STATUS_BAR_MAX_WIDTH_DP)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        root.addView(
            row,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        applyPosition()
    }

    private fun providerIcon(provider: Provider?) = if (provider == null) {
        TextView(this).apply {
            text = "+"
            setTextColor(Color.BLACK)
            textSize = 10f
            gravity = Gravity.CENTER
            background = roundedBackground(Color.rgb(255, 190, 70), 9f)
        }
    } else {
        ImageView(this).apply {
            setImageResource(
                when (provider) {
                    Provider.DEEPSEEK -> R.drawable.ic_provider_deepseek
                    Provider.OPENAI -> R.drawable.ic_provider_openai
                    Provider.OPENROUTER -> R.drawable.ic_provider_openrouter
                    Provider.SILICONFLOW -> R.drawable.ic_provider_siliconflow
                    Provider.MOONSHOT -> R.drawable.ic_provider_kimi
                    Provider.ANTHROPIC -> R.drawable.ic_provider_anthropic
                    Provider.GEMINI -> R.drawable.ic_provider_gemini
                    Provider.XAI -> R.drawable.ic_provider_xai
                }
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            if (provider == Provider.OPENAI || provider == Provider.XAI) {
                setPadding(dp(2), dp(2), dp(2), dp(2))
                background = roundedBackground(Color.rgb(20, 22, 27), 9f)
            } else if (provider == Provider.MOONSHOT) {
                setPadding(dp(2), dp(2), dp(2), dp(2))
                background = roundedBackground(Color.WHITE, 9f)
            }
            contentDescription = provider.displayName
        }
    }

    private fun statusText(
        value: String,
        color: Int,
        sizeSp: Float = 12f,
        outlined: Boolean = false
    ) = TextView(this).apply {
        text = value
        setTextColor(color)
        textSize = sizeSp
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        if (outlined) {
            setShadowLayer(
                1.35f * resources.displayMetrics.density,
                0f,
                0f,
                StatusBarContrast.outlineColorFor(color)
            )
        }
    }

    private fun showNextAccount() {
        val count = visibleSnapshots().size
        if (count > 1) visibleAccountIndex = (visibleAccountIndex + 1) % count
        showDailyDetail = false
        render()
    }

    private fun visibleSnapshots(): List<BalanceSnapshot> =
        displayPreferences.select(repository.cached())

    private fun refreshNow() {
        if (refreshInProgress) return
        refreshInProgress = true
        scope.launch {
            try {
                repository.refreshAll()
                render()
            } finally {
                refreshInProgress = false
                scheduleNextRefresh()
            }
        }
    }

    private fun scheduleNextRefresh() {
        handler.removeCallbacks(refreshRunnable)
        val delay = displayPreferences.refreshIntervalMinutes() * 60_000L
        handler.postDelayed(refreshRunnable, delay)
    }

    private fun openSettings() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(strings.getString(R.string.notification_title))
        .setContentText(
            strings.getString(
                R.string.notification_refresh_interval,
                displayPreferences.refreshIntervalMinutes()
            )
        )
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            0,
            strings.getString(R.string.notification_stop),
            PendingIntent.getService(
                this,
                1,
                Intent(this, IslandOverlayService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                strings.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun roundedBackground(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun linearParams(width: Int, height: Int) = LinearLayout.LayoutParams(width, height)

    private fun overlayHeightPx(): Int = dp(STATUS_BAR_TEXT_HEIGHT_DP)
        .coerceAtMost(statusBarHeightPx().coerceAtLeast(dp(STATUS_BAR_TEXT_HEIGHT_DP)))

    private fun statusBarHeightPx(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val top = windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            if (top > 0) return top
        }
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId)
        else dp(FALLBACK_STATUS_BAR_HEIGHT_DP)
    }

    private fun safeHorizontalInsetPx(alignStart: Boolean): Int {
        var inset = dp(MIN_SAFE_INSET_DP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowInsets = windowManager.currentWindowMetrics.windowInsets
            val cutout = windowInsets.displayCutout
            inset = maxOf(
                inset,
                if (alignStart) cutout?.safeInsetLeft ?: 0 else cutout?.safeInsetRight ?: 0
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val position = if (alignStart) {
                    RoundedCorner.POSITION_TOP_LEFT
                } else {
                    RoundedCorner.POSITION_TOP_RIGHT
                }
                inset = maxOf(inset, windowInsets.getRoundedCorner(position)?.radius ?: 0)
            }
        }
        return inset
    }

    companion object {
        private const val CHANNEL_ID = "balance_island_running"
        private const val NOTIFICATION_ID = 1001
        private const val ROTATE_INTERVAL_MS = 5_000L
        private const val STATUS_BAR_TEXT_HEIGHT_DP = 26
        private const val STATUS_BAR_MAX_WIDTH_DP = 220
        private const val FALLBACK_STATUS_BAR_HEIGHT_DP = 28
        private const val MIN_SAFE_INSET_DP = 16
        private const val MIN_EDGE_INSET_DP = 4
        const val PREFS_NAME = "overlay_settings"
        const val KEY_Y_OFFSET = "y_offset_dp"
        const val ACTION_STOP = "com.noyorin.balanceisland.STOP_OVERLAY"
        const val ACTION_REFRESH = "com.noyorin.balanceisland.REFRESH_OVERLAY"
        private const val ACTION_RESTART = "com.noyorin.balanceisland.RESTART_OVERLAY"
        private const val RESTART_REQUEST_CODE = 2002

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
            ServiceRuntimePreferences(context).setDesiredRunning(true)
            cancelScheduledRestart(context)
            ContextCompat.startForegroundService(
                context,
                Intent(context, IslandOverlayService::class.java)
            )
        }

        fun stop(context: Context) {
            ServiceRuntimePreferences(context).clearRunningState()
            cancelScheduledRestart(context)
            context.stopService(Intent(context, IslandOverlayService::class.java))
        }

        fun restart(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, IslandOverlayService::class.java).setAction(ACTION_RESTART)
            )
        }

        private fun scheduleRestart(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 5_000L,
                restartPendingIntent(context)
            )
        }

        private fun cancelScheduledRestart(context: Context) {
            context.getSystemService(AlarmManager::class.java)
                .cancel(restartPendingIntent(context))
        }

        private fun restartPendingIntent(context: Context): PendingIntent =
            PendingIntent.getForegroundService(
                context,
                RESTART_REQUEST_CODE,
                Intent(context, IslandOverlayService::class.java).setAction(ACTION_RESTART),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
