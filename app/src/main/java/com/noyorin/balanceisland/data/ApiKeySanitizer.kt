package com.noyorin.balanceisland.data

/** Removes common paste wrappers without ever logging or transforming the key itself. */
object ApiKeySanitizer {
    private val bearerPrefix = Regex("^Bearer\\s+", RegexOption.IGNORE_CASE)
    private val knownKey = Regex(
        "(?:sk-[A-Za-z0-9_-]{8,}|tp-[A-Za-z0-9_-]{8,}|AIza[A-Za-z0-9_-]{8,}|xai-[A-Za-z0-9_-]{8,})"
    )

    fun clean(raw: String): String {
        val trimmed = raw.trim().trim('"', '\'', '`')
        val withoutBearer = trimmed.replaceFirst(bearerPrefix, "").trim()
        return knownKey.find(withoutBearer)?.value ?: withoutBearer
    }
}
