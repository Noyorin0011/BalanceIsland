package com.noyorin.balanceisland.experimental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalProxyValidatorTest {
    @Test
    fun `accepts emulator host and builds explicit http proxy URL`() {
        val result = ExperimentalProxyValidator.validate(" 10.0.2.2 ", "7890")

        assertTrue(result is ExperimentalProxyValidation.Valid)
        val endpoint = (result as ExperimentalProxyValidation.Valid).endpoint
        assertEquals("10.0.2.2", endpoint.host)
        assertEquals(7890, endpoint.port)
        assertEquals("http://10.0.2.2:7890", endpoint.proxyUrl)
    }

    @Test
    fun `accepts bracketed ipv6 and restores brackets in proxy URL`() {
        val result = ExperimentalProxyValidator.validate("[::1]", "8080")

        assertTrue(result is ExperimentalProxyValidation.Valid)
        assertEquals("http://[::1]:8080", (result as ExperimentalProxyValidation.Valid).endpoint.proxyUrl)
    }

    @Test
    fun `rejects scheme path and credentials in host field`() {
        listOf("http://127.0.0.1", "proxy.local/path", "user@proxy.local").forEach { host ->
            val result = ExperimentalProxyValidator.validate(host, "7890")
            assertEquals(
                ExperimentalProxyValidation.Invalid(ExperimentalProxyValidationError.HOST_INVALID),
                result
            )
        }
    }

    @Test
    fun `rejects empty host and invalid ports`() {
        assertEquals(
            ExperimentalProxyValidation.Invalid(ExperimentalProxyValidationError.HOST_EMPTY),
            ExperimentalProxyValidator.validate(" ", "7890")
        )
        listOf("", "0", "65536", "abc").forEach { port ->
            assertEquals(
                ExperimentalProxyValidation.Invalid(ExperimentalProxyValidationError.PORT_INVALID),
                ExperimentalProxyValidator.validate("localhost", port)
            )
        }
    }

    @Test
    fun `rejects malformed numeric ipv4 address`() {
        listOf("999.0.0.1", "127.1", "1.2.3").forEach { host ->
            assertEquals(
                ExperimentalProxyValidation.Invalid(ExperimentalProxyValidationError.HOST_INVALID),
                ExperimentalProxyValidator.validate(host, "7890")
            )
        }
    }

    @Test
    fun `rejects incomplete ipv6 address without compression`() {
        assertEquals(
            ExperimentalProxyValidation.Invalid(ExperimentalProxyValidationError.HOST_INVALID),
            ExperimentalProxyValidator.validate("1:2:3", "7890")
        )
    }
}
