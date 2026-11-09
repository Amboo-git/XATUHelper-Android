package com.xatune.app.platform

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.net.URI

internal object WebDiagnosticText {
    fun url(value: String): String = runCatching {
        val uri = URI(value.substringBefore('?').substringBefore('#').replace("[redacted]", "%5Bredacted%5D"))
        if (uri.scheme !in listOf("https", "http")) return@runCatching "[${uri.scheme ?: "relative"}]"
        "${uri.scheme}://${uri.host ?: "unknown"}${if (uri.port >= 0) ":${uri.port}" else ""}${uri.rawPath.orEmpty()}"
            .replace(Regex("[0-9]{6,}|[A-Za-z0-9_-]{32,}"), "[redacted]")
            .replace("%5Bredacted%5D", "[redacted]").take(400)
    }.getOrDefault("[invalid-url]")

    private fun redactNumbers(value: String): String = Regex("[0-9]{6,}").replace(value) { match ->
        val start = match.range.first
        if (start >= 2 && value[start - 1] == '.' && value[start - 2].isDigit()) match.value else "[redacted]"
    }
    private fun plain(value: String): String = redactNumbers(value
        .replace(Regex("https?://[^\\s\"<>]+")) { url(it.value) }
        .replace(Regex("(?i)(password|passwd|cookie|authorization|token|ticket|secret|学号|密码)\\s*[=:：]\\s*[^\\s,;]+"), "$1=[redacted]")
        .replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "[email]")
        .replace(Regex("[A-Za-z0-9_-]{32,}"), "[redacted]"))
        .replace('\n', ' ').replace('\r', ' ').take(6000)

    // Diagnostic JSON contains selected metadata, never raw response records. Sanitize string
    // fields separately so fractional dimensions and numeric timings remain valid JSON.
    private fun scrub(value: JsonElement): JsonElement = when {
        value.isJsonObject -> value.asJsonObject.apply { entrySet().toList().forEach { (key, child) -> add(key, scrub(child)) } }
        value.isJsonArray -> value.asJsonArray.apply { for (i in 0 until size()) set(i, scrub(get(i))) }
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> JsonPrimitive(plain(value.asString))
        else -> value
    }
    fun clean(value: String): String {
        if (value.startsWith("{")) {
            if (value.length > 24000) return "{\"diagnostic\":\"message-too-large\"}"
            return runCatching { scrub(JsonParser.parseString(value)).toString() }.getOrElse { plain(value) }
        }
        return plain(value)
    }
}
