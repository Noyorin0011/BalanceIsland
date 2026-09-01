package com.noyorin.balanceisland.experimental

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.noyorin.balanceisland.R
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale
import kotlin.math.roundToInt

class ExperimentalPlanActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var proxyStatus: TextView
    private lateinit var progress: ProgressBar
    private lateinit var proxyEnabled: CheckBox
    private lateinit var proxyHost: EditText
    private lateinit var proxyPort: EditText
    private lateinit var autoRefreshEnabled: CheckBox
    private lateinit var resetNotificationsEnabled: CheckBox
    private lateinit var preferences: ExperimentalPlanPreferences
    private lateinit var proxyPreferences: ExperimentalProxyPreferences

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mainFrameLoading = false
    private var mainFrameFailed = false
    private var webViewAvailable = true
    private var activityVisible = false
    private var pageReady = false
    private var usageReadInFlight = false
    private var usageReadGeneration = 0
    private var autoRefreshPaused = false
    private var suppressAutoRefreshListener = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && ::resetNotificationsEnabled.isInitialized) {
            preferences.setResetNotificationsEnabled(false)
            resetNotificationsEnabled.isChecked = false
        }
    }
    private val loadTimeout = Runnable {
        if (mainFrameLoading && webViewAvailable) {
            mainFrameLoading = false
            mainFrameFailed = true
            webView.stopLoading()
            status.text = getString(R.string.experimental_web_timeout)
            progress.visibility = View.GONE
        }
    }
    private val autoRefresh = Runnable {
        if (canAutoRefresh()) readUsage(manual = false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preferences = ExperimentalPlanPreferences(this)
        proxyPreferences = ExperimentalProxyPreferences(this)
        val savedProxy = proxyPreferences.settings()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        val warning = TextView(this).apply {
            text = getString(R.string.experimental_browser_warning)
            setTextColor(0xffb3261e.toInt())
            textSize = 14f
            setPadding(0, 0, 0, dp(8))
        }
        status = TextView(this).apply {
            text = getString(R.string.experimental_browser_sign_in)
            setPadding(0, 0, 0, dp(8))
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val readButton = Button(this).apply {
            text = getString(R.string.experimental_read_usage)
            setOnClickListener { readUsage() }
        }
        val clearButton = Button(this).apply {
            text = getString(R.string.experimental_disconnect)
            setOnClickListener { disconnect() }
        }
        actions.addView(readButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(clearButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        autoRefreshEnabled = CheckBox(this).apply {
            text = getString(R.string.experimental_auto_refresh_enable)
            isChecked = preferences.autoRefreshWhileOpenEnabled()
        }
        val autoRefreshHelp = TextView(this).apply {
            text = getString(R.string.experimental_auto_refresh_help)
            textSize = 12f
            setTextColor(0xffb3261e.toInt())
            setPadding(0, 0, 0, dp(4))
        }
        autoRefreshEnabled.setOnCheckedChangeListener { _, checked ->
            if (suppressAutoRefreshListener) return@setOnCheckedChangeListener
            if (checked) {
                setAutoRefreshChecked(false)
                android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.experimental_auto_refresh_confirm_title)
                    .setMessage(R.string.experimental_auto_refresh_confirm_message)
                    .setPositiveButton(R.string.experimental_auto_refresh_confirm_enable) { _, _ ->
                        preferences.setAutoRefreshWhileOpenEnabled(true)
                        autoRefreshPaused = false
                        setAutoRefreshChecked(true)
                        scheduleImmediateAutoRefresh()
                    }
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show()
            } else {
                preferences.setAutoRefreshWhileOpenEnabled(false)
                cancelAutoRefresh()
            }
        }

        resetNotificationsEnabled = CheckBox(this).apply {
            text = getString(R.string.experimental_reset_notifications_enable)
            isChecked = preferences.resetNotificationsEnabled()
            setOnCheckedChangeListener { _, checked ->
                preferences.setResetNotificationsEnabled(checked)
                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@ExperimentalPlanActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        val resetNotificationsHelp = TextView(this).apply {
            text = getString(R.string.experimental_reset_notifications_help)
            textSize = 12f
            setTextColor(0xffb3261e.toInt())
            setPadding(0, 0, 0, dp(4))
        }

        proxyEnabled = CheckBox(this).apply {
            text = getString(R.string.experimental_proxy_enable)
            isChecked = savedProxy.enabled
        }
        val proxyHelp = TextView(this).apply {
            text = getString(R.string.experimental_proxy_help)
            textSize = 12f
            setTextColor(0xffb3261e.toInt())
            setPadding(0, 0, 0, dp(4))
        }
        val proxyFields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        proxyHost = EditText(this).apply {
            hint = getString(R.string.experimental_proxy_host)
            setText(savedProxy.host)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        proxyPort = EditText(this).apply {
            hint = getString(R.string.experimental_proxy_port)
            setText(String.format(Locale.ROOT, "%d", savedProxy.port))
            setSingleLine()
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        proxyFields.addView(proxyHost, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        proxyFields.addView(proxyPort, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val proxyActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val applyProxyButton = Button(this).apply {
            text = getString(R.string.experimental_proxy_apply)
            setOnClickListener { applyProxyAndLoad(persist = true) }
        }
        val retryButton = Button(this).apply {
            text = getString(R.string.experimental_retry)
            setOnClickListener {
                if (webViewAvailable) applyProxyAndLoad(persist = false) else recreate()
            }
        }
        proxyActions.addView(applyProxyButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        proxyActions.addView(retryButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        proxyStatus = TextView(this).apply {
            textSize = 12f
            setPadding(0, 0, 0, dp(6))
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.safeBrowsingEnabled = true
            webViewClient = ExperimentalWebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@ExperimentalPlanActivity.progress.progress = newProgress
                    this@ExperimentalPlanActivity.progress.visibility =
                        if (newProgress in 0..99) View.VISIBLE else View.GONE
                }
            }
            setBackgroundColor(Color.WHITE)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        root.addView(warning)
        root.addView(status)
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        root.addView(actions)
        root.addView(autoRefreshEnabled)
        root.addView(autoRefreshHelp)
        root.addView(resetNotificationsEnabled)
        root.addView(resetNotificationsHelp)
        root.addView(proxyEnabled)
        root.addView(proxyHelp)
        root.addView(proxyFields)
        root.addView(proxyActions)
        root.addView(proxyStatus)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        applyProxyAndLoad(persist = false)
    }

    @SuppressLint("RequiresFeature")
    private fun applyProxyAndLoad(persist: Boolean) {
        val enabled = proxyEnabled.isChecked
        val validation = ExperimentalProxyValidator.validate(proxyHost.text.toString(), proxyPort.text.toString())
        val endpoint = when (validation) {
            is ExperimentalProxyValidation.Valid -> validation.endpoint
            is ExperimentalProxyValidation.Invalid -> {
                if (enabled) {
                    proxyStatus.text = getString(validation.error.stringResource())
                    return
                }
                val saved = proxyPreferences.settings()
                proxyHost.setText(saved.host)
                proxyPort.setText(String.format(Locale.ROOT, "%d", saved.port))
                ExperimentalProxyEndpoint(saved.host, saved.port)
            }
        }
        if (persist) proxyPreferences.save(enabled, endpoint)

        val webViewVersion = WebView.getCurrentWebViewPackage()?.versionName ?: getString(R.string.experimental_unknown)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (enabled) {
                proxyStatus.text = getString(R.string.experimental_proxy_unsupported, webViewVersion)
                return
            }
            proxyStatus.text = getString(R.string.experimental_proxy_direct, webViewVersion)
            loadChatGpt()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        runCatching {
            if (enabled) {
                val config = ProxyConfig.Builder()
                    .addProxyRule(endpoint.proxyUrl)
                    .build()
                ProxyController.getInstance().setProxyOverride(config, executor) {
                    if (!isFinishing && !isDestroyed) {
                        proxyStatus.text = getString(
                            R.string.experimental_proxy_active,
                            endpoint.host,
                            endpoint.port,
                            webViewVersion
                        )
                        loadChatGpt()
                    }
                }
            } else {
                ProxyController.getInstance().clearProxyOverride(executor) {
                    if (!isFinishing && !isDestroyed) {
                        proxyStatus.text = getString(R.string.experimental_proxy_direct, webViewVersion)
                        loadChatGpt()
                    }
                }
            }
        }.onFailure {
            proxyStatus.text = getString(R.string.experimental_proxy_apply_failed)
        }
    }

    private fun ExperimentalProxyValidationError.stringResource(): Int = when (this) {
        ExperimentalProxyValidationError.HOST_EMPTY -> R.string.experimental_proxy_host_empty
        ExperimentalProxyValidationError.HOST_INVALID -> R.string.experimental_proxy_host_invalid
        ExperimentalProxyValidationError.PORT_INVALID -> R.string.experimental_proxy_port_invalid
    }

    private fun loadChatGpt() {
        if (!webViewAvailable) return
        beginMainFrameLoad()
        webView.loadUrl(CHATGPT_URL)
    }

    private fun beginMainFrameLoad() {
        pageReady = false
        autoRefreshPaused = false
        cancelAutoRefresh()
        invalidateUsageRead()
        mainFrameLoading = true
        mainFrameFailed = false
        status.text = getString(R.string.experimental_web_loading)
        mainHandler.removeCallbacks(loadTimeout)
        mainHandler.postDelayed(loadTimeout, PAGE_LOAD_TIMEOUT_MILLIS)
    }

    private inner class ExperimentalWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (!mainFrameLoading) beginMainFrameLoad()
            status.text = getString(R.string.experimental_web_loading)
        }

        override fun onPageCommitVisible(view: WebView?, url: String?) {
            finishMainFrameLoad()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            finishMainFrameLoad()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return true
            if (uri.scheme.equals("https", ignoreCase = true)) return false
            if (request.isForMainFrame) {
                status.text = getString(R.string.experimental_non_https_blocked)
            }
            return true
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true) {
                pageReady = false
                cancelAutoRefresh()
                mainFrameFailed = true
                mainFrameLoading = false
                mainHandler.removeCallbacks(loadTimeout)
                progress.visibility = View.GONE
                status.text = getString(R.string.experimental_web_error, error?.errorCode ?: 0)
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            if (request?.isForMainFrame == true) {
                pageReady = false
                cancelAutoRefresh()
                mainFrameFailed = true
                mainFrameLoading = false
                mainHandler.removeCallbacks(loadTimeout)
                progress.visibility = View.GONE
                status.text = getString(R.string.experimental_web_http_error, errorResponse?.statusCode ?: 0)
            }
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            handler?.cancel()
            pageReady = false
            cancelAutoRefresh()
            mainFrameFailed = true
            mainFrameLoading = false
            mainHandler.removeCallbacks(loadTimeout)
            progress.visibility = View.GONE
            status.text = getString(R.string.experimental_web_ssl_error)
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            mainFrameFailed = true
            mainFrameLoading = false
            webViewAvailable = false
            pageReady = false
            cancelAutoRefresh()
            invalidateUsageRead()
            mainHandler.removeCallbacks(loadTimeout)
            progress.visibility = View.GONE
            status.text = getString(R.string.experimental_web_renderer_gone)
            view?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                it.destroy()
            }
            return true
        }

        private fun finishMainFrameLoad() {
            mainFrameLoading = false
            mainHandler.removeCallbacks(loadTimeout)
            if (!mainFrameFailed) {
                pageReady = true
                status.text = getString(R.string.experimental_browser_sign_in)
                scheduleAutoRefresh(immediateIfStale = true)
            }
        }
    }

    private fun readUsage(manual: Boolean = true) {
        if (!webViewAvailable) {
            status.text = getString(R.string.experimental_web_renderer_gone)
            return
        }
        if (usageReadInFlight) {
            if (manual) status.text = getString(R.string.experimental_read_in_progress)
            return
        }
        val currentUri = webView.url.orEmpty().toUri()
        if (currentUri.scheme != "https" || !currentUri.host.equals("chatgpt.com", ignoreCase = true)) {
            status.text = getString(R.string.experimental_return_to_chatgpt)
            return
        }
        if (manual) autoRefreshPaused = false
        usageReadInFlight = true
        val readGeneration = usageReadGeneration
        preferences.markReadAttempt()
        status.text = getString(R.string.experimental_reading)
        webView.evaluateJavascript(USAGE_SCRIPT, usageCallback@{ encodedResult ->
            if (readGeneration != usageReadGeneration || !webViewAvailable) {
                return@usageCallback
            }
            runCatching {
                val decoded = JSONTokener(encodedResult).nextValue() as String
                val envelope = JSONObject(decoded)
                val httpStatus = envelope.optInt("status")
                if (httpStatus !in 200..299) throw UsageHttpException(httpStatus)
                parseUsage(JSONObject(envelope.getString("body")))
            }.onSuccess { usage ->
                val resetEvents = preferences.saveUsage(usage)
                if (preferences.resetNotificationsEnabled()) {
                    ExperimentalPlanResetNotifier(this).notify(resetEvents)
                }
                status.text = getString(R.string.experimental_read_success)
                autoRefreshPaused = false
                setResult(RESULT_OK)
            }.onFailure { error ->
                val category = error.readErrorCategory()
                preferences.markReadFailure(category)
                autoRefreshPaused = ExperimentalPlanAutoRefreshPolicy.shouldPauseAfter(category)
                status.text = getString(R.string.experimental_read_failed, error.message ?: "unknown")
            }
            usageReadInFlight = false
            if (canAutoRefresh()) scheduleAutoRefresh(immediateIfStale = false)
        })
    }

    private fun parseUsage(json: JSONObject): ExperimentalPlanUsage {
        val rateLimit = json.optJSONObject("rate_limit") ?: json
        val primary = rateLimit.optJSONObject("primary_window")
        val secondary = rateLimit.optJSONObject("secondary_window")
        return ExperimentalPlanUsage(
            planType = json.optString("plan_type"),
            primaryRemaining = primary?.remainingPercent(),
            primaryResetAtSeconds = primary?.resetAt(),
            primaryWindowSeconds = primary?.windowSeconds(),
            secondaryRemaining = secondary?.remainingPercent(),
            secondaryResetAtSeconds = secondary?.resetAt(),
            secondaryWindowSeconds = secondary?.windowSeconds(),
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    private fun JSONObject.remainingPercent(): Int? {
        if (!has("used_percent")) return null
        return (100f - optDouble("used_percent").toFloat()).roundToInt().coerceIn(0, 100)
    }

    private fun JSONObject.resetAt(): Long? = when {
        has("reset_at") -> optLong("reset_at").takeIf { it > 0 }
        has("reset_after_seconds") -> (System.currentTimeMillis() / 1000L) + optLong("reset_after_seconds")
        else -> null
    }

    private fun JSONObject.windowSeconds(): Long? =
        optLong("limit_window_seconds").takeIf { it > 0 }

    private fun Throwable.readErrorCategory(): ExperimentalPlanReadError = when (this) {
        is UsageHttpException -> ExperimentalPlanReadErrorClassifier.fromHttpStatus(statusCode)
        else -> ExperimentalPlanReadError.PARSE
    }

    private fun canAutoRefresh(): Boolean =
        ::autoRefreshEnabled.isInitialized &&
            autoRefreshEnabled.isChecked &&
            activityVisible &&
            pageReady &&
            webViewAvailable &&
            !usageReadInFlight &&
            !autoRefreshPaused &&
            !isFinishing &&
            !isDestroyed

    private fun scheduleAutoRefresh(immediateIfStale: Boolean) {
        cancelAutoRefresh()
        if (!canAutoRefresh()) return
        val lastUpdated = preferences.usage()?.updatedAtMillis ?: 0L
        val delay = ExperimentalPlanAutoRefreshPolicy.nextDelayMillis(
            lastUpdatedAtMillis = lastUpdated,
            nowMillis = System.currentTimeMillis(),
            immediateIfStale = immediateIfStale
        )
        mainHandler.postDelayed(autoRefresh, delay)
    }

    private fun scheduleImmediateAutoRefresh() {
        cancelAutoRefresh()
        if (canAutoRefresh()) {
            mainHandler.postDelayed(autoRefresh, ExperimentalPlanAutoRefreshPolicy.INITIAL_DELAY_MILLIS)
        }
    }

    private fun cancelAutoRefresh() {
        mainHandler.removeCallbacks(autoRefresh)
    }

    private fun setAutoRefreshChecked(checked: Boolean) {
        suppressAutoRefreshListener = true
        autoRefreshEnabled.isChecked = checked
        suppressAutoRefreshListener = false
    }

    private fun invalidateUsageRead() {
        usageReadGeneration++
        usageReadInFlight = false
    }

    private fun disconnect() {
        cancelAutoRefresh()
        invalidateUsageRead()
        autoRefreshPaused = true
        setAutoRefreshChecked(false)
        preferences.clearAll()
        setResult(RESULT_OK)
        WebStorage.getInstance().deleteAllData()
        if (webViewAvailable) {
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            webView.clearSslPreferences()
        }
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            if (!isFinishing && !isDestroyed && webViewAvailable) {
                status.text = getString(R.string.experimental_disconnected)
                applyProxyAndLoad(persist = false)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activityVisible = true
        scheduleAutoRefresh(immediateIfStale = true)
    }

    override fun onStop() {
        activityVisible = false
        cancelAutoRefresh()
        CookieManager.getInstance().flush()
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(loadTimeout)
        cancelAutoRefresh()
        invalidateUsageRead()
        if (webViewAvailable) {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
            webViewAvailable = false
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val CHATGPT_URL = "https://chatgpt.com/"
        private const val PAGE_LOAD_TIMEOUT_MILLIS = 25_000L
        private const val USAGE_SCRIPT = """
            (function() {
              try {
                var sessionRequest = new XMLHttpRequest();
                sessionRequest.open('GET', '/api/auth/session', false);
                sessionRequest.withCredentials = true;
                sessionRequest.setRequestHeader('Accept', 'application/json');
                sessionRequest.send(null);

                if (sessionRequest.status < 200 || sessionRequest.status >= 300) {
                  return JSON.stringify({status: sessionRequest.status, body: ''});
                }
                var session = JSON.parse(sessionRequest.responseText || '{}');
                var accessToken = typeof session.accessToken === 'string' ? session.accessToken : '';
                if (!accessToken) return JSON.stringify({status: 401, body: ''});

                var request = new XMLHttpRequest();
                request.open('GET', '/backend-api/wham/usage', false);
                request.withCredentials = true;
                request.setRequestHeader('Accept', 'application/json');
                request.setRequestHeader('Authorization', 'Bearer ' + accessToken);
                request.send(null);

                var body = '';
                if (request.status >= 200 && request.status < 300) {
                  var rawUsage = JSON.parse(request.responseText || '{}');
                  var rawRateLimit = rawUsage.rate_limit || rawUsage;
                  var sanitizeWindow = function(windowValue) {
                    if (!windowValue || typeof windowValue !== 'object') return null;
                    return {
                      used_percent: windowValue.used_percent,
                      reset_at: windowValue.reset_at,
                      reset_after_seconds: windowValue.reset_after_seconds,
                      limit_window_seconds: windowValue.limit_window_seconds
                    };
                  };
                  body = JSON.stringify({
                    plan_type: typeof rawUsage.plan_type === 'string' ? rawUsage.plan_type : '',
                    rate_limit: {
                      primary_window: sanitizeWindow(rawRateLimit.primary_window),
                      secondary_window: sanitizeWindow(rawRateLimit.secondary_window)
                    }
                  });
                }
                return JSON.stringify({status: request.status, body: body});
              } catch (error) {
                return JSON.stringify({status: 0, body: ''});
              }
            })();
        """
    }

    private class UsageHttpException(val statusCode: Int) : IllegalStateException("HTTP $statusCode")
}
