package com.noyorin.balanceisland.experimental

import android.content.Context
import androidx.core.content.edit

data class ExperimentalProxySettings(
    val enabled: Boolean,
    val host: String,
    val port: Int
)

data class ExperimentalProxyEndpoint(
    val host: String,
    val port: Int
) {
    val proxyUrl: String
        get() {
            val authority = if (host.contains(':')) "[$host]" else host
            return "http://$authority:$port"
        }
}

enum class ExperimentalProxyValidationError {
    HOST_EMPTY,
    HOST_INVALID,
    PORT_INVALID
}

sealed interface ExperimentalProxyValidation {
    data class Valid(val endpoint: ExperimentalProxyEndpoint) : ExperimentalProxyValidation
    data class Invalid(val error: ExperimentalProxyValidationError) : ExperimentalProxyValidation
}

object ExperimentalProxyValidator {
    fun validate(rawHost: String, rawPort: String): ExperimentalProxyValidation {
        val host = rawHost.trim().removeSurrounding("[", "]")
        if (host.isEmpty()) {
            return ExperimentalProxyValidation.Invalid(ExperimentalProxyValidationError.HOST_EMPTY)
        }
        if (!isValidHost(host)) {
            return ExperimentalProxyValidation.Invalid(ExperimentalProxyValidationError.HOST_INVALID)
        }

        val port = rawPort.trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            return ExperimentalProxyValidation.Invalid(ExperimentalProxyValidationError.PORT_INVALID)
        }
        return ExperimentalProxyValidation.Valid(ExperimentalProxyEndpoint(host, port))
    }

    private fun isValidHost(host: String): Boolean {
        if (host.length > 253 || host.any { it.isWhitespace() || it.isISOControl() }) return false
        if (host.any { it in "/\\@?#%" } || "://" in host) return false
        return if (':' in host) isValidIpv6Literal(host) else isValidHostnameOrIpv4(host)
    }

    private fun isValidHostnameOrIpv4(host: String): Boolean {
        val labels = host.split('.')
        if (labels.any { label ->
                label.isEmpty() ||
                    label.length > 63 ||
                    label.first() == '-' ||
                    label.last() == '-' ||
                    label.any { !it.isLetterOrDigit() && it != '-' }
            }
        ) {
            return false
        }
        if (host.all { it.isDigit() || it == '.' }) {
            return labels.size == 4 && labels.all { label ->
                label.toIntOrNull()?.let { it in 0..255 } == true
            }
        }
        return true
    }

    private fun isValidIpv6Literal(host: String): Boolean {
        if (!host.matches(Regex("[0-9A-Fa-f:]+"))) return false
        if (host.contains(":::")) return false
        if (host.indexOf("::") != host.lastIndexOf("::")) return false
        val segments = host.split(':').filter { it.isNotEmpty() }
        if (segments.any { it.length !in 1..4 }) return false
        return if (host.contains("::")) segments.size < 8 else segments.size == 8
    }
}

class ExperimentalProxyPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun settings(): ExperimentalProxySettings = ExperimentalProxySettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        host = prefs.getString(KEY_HOST, DEFAULT_HOST)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST,
        port = prefs.getInt(KEY_PORT, DEFAULT_PORT).takeIf { it in 1..65535 } ?: DEFAULT_PORT
    )

    fun save(enabled: Boolean, endpoint: ExperimentalProxyEndpoint) {
        prefs.edit {
            putBoolean(KEY_ENABLED, enabled)
            putString(KEY_HOST, endpoint.host)
            putInt(KEY_PORT, endpoint.port)
        }
    }

    companion object {
        const val DEFAULT_HOST = "10.0.2.2"
        const val DEFAULT_PORT = 7890

        private const val PREFS_NAME = "experimental_webview_proxy"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
    }
}
