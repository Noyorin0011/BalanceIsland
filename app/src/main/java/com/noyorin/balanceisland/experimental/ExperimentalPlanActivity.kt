package com.noyorin.balanceisland.experimental

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.noyorin.balanceisland.R
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.roundToInt

class ExperimentalPlanActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var preferences: ExperimentalPlanPreferences

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preferences = ExperimentalPlanPreferences(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        val warning = TextView(this).apply {
            text = getString(R.string.experimental_browser_warning)
            setTextColor(0xffb3261e.toInt())
            textSize = 14f
            setPadding(0, 0, 0, dp(10))
        }
        status = TextView(this).apply {
            text = getString(R.string.experimental_browser_sign_in)
            setPadding(0, 0, 0, dp(10))
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

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.safeBrowsingEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        root.addView(warning)
        root.addView(status)
        root.addView(actions)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        webView.loadUrl(CHATGPT_URL)
    }

    private fun readUsage() {
        val currentUri = Uri.parse(webView.url.orEmpty())
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
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            webView.clearSslPreferences()
            preferences.clearAll()
            status.text = getString(R.string.experimental_disconnected)
            setResult(RESULT_OK)
            webView.loadUrl(CHATGPT_URL)
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val CHATGPT_URL = "https://chatgpt.com/"
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
