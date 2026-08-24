package com.noyorin.balanceisland.experimental

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
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
    private lateinit var preferences: ExperimentalPlanPreferences
    private lateinit var proxyPreferences: ExperimentalProxyPreferences

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mainFrameLoading = false
    private var mainFrameFailed = false
    private var webViewAvailable = true
    private val loadTimeout = Runnable {
        if (mainFrameLoading && webViewAvailable) {
            mainFrameLoading = false
            mainFrameFailed = true
            webView.stopLoading()
            status.text = getString(R.string.experimental_web_timeout)
            progress.visibility = View.GONE
        }
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
                mainFrameFailed = true
                mainFrameLoading = false
                mainHandler.removeCallbacks(loadTimeout)
                progress.visibility = View.GONE
                status.text = getString(R.string.experimental_web_http_error, errorResponse?.statusCode ?: 0)
            }
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            handler?.cancel()
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
                status.text = getString(R.string.experimental_browser_sign_in)
            }
        }
    }

    private fun readUsage() {
        if (!webViewAvailable) {
            status.text = getString(R.string.experimental_web_renderer_gone)
            return
        }
        val currentUri = webView.url.orEmpty().toUri()
        if (currentUri.scheme != "https" || !currentUri.host.equals("chatgpt.com", ignoreCase = true)) {
            status.text = getString(R.string.experimental_return_to_chatgpt)
            return
        }
        status.text = getString(R.string.experimental_reading)
        webView.evaluateJavascript(USAGE_SCRIPT) { encodedResult ->
            runCatching {
                val decoded = JSONTokener(encodedResult).nextValue() as String
                val envelope = JSONObject(decoded)
                val httpStatus = envelope.optInt("status")
                if (httpStatus !in 200..299) error("HTTP $httpStatus")
                parseUsage(JSONObject(envelope.getString("body")))
            }.onSuccess { usage ->
                preferences.saveUsage(usage)
                status.text = getString(R.string.experimental_read_success)
                setResult(RESULT_OK)
            }.onFailure { error ->
                status.text = getString(R.string.experimental_read_failed, error.message ?: "unknown")
            }
        }
    }

    private fun parseUsage(json: JSONObject): ExperimentalPlanUsage {
        val rateLimit = json.optJSONObject("rate_limit") ?: json
        val primary = rateLimit.optJSONObject("primary_window")
        val secondary = rateLimit.optJSONObject("secondary_window")
        return ExperimentalPlanUsage(
            planType = json.optString("plan_type"),
            primaryRemaining = primary?.remainingPercent(),
            primaryResetAtSeconds = primary?.resetAt(),
            secondaryRemaining = secondary?.remainingPercent(),
            secondaryResetAtSeconds = secondary?.resetAt(),
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

    private fun disconnect() {
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

    override fun onDestroy() {
        mainHandler.removeCallbacks(loadTimeout)
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
                var request = new XMLHttpRequest();
                request.open('GET', '/backend-api/wham/usage', false);
                request.withCredentials = true;
                request.send(null);
                return JSON.stringify({status: request.status, body: request.responseText});
              } catch (error) {
                return JSON.stringify({status: 0, body: '', error: String(error)});
              }
            })();
        """
    }
}
