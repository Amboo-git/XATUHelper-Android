package cn.edu.xatu.helper.platform

import java.net.URI

internal object WebDiagnosticText {
    fun url(value: String): String = runCatching {
        val uri = URI(value)
        if (uri.scheme !in listOf("https", "http")) return@runCatching "[${uri.scheme ?: "relative"}]"
        "${uri.scheme}://${uri.host ?: "unknown"}${if (uri.port >= 0) ":${uri.port}" else ""}${uri.rawPath.orEmpty()}"
            .replace(Regex("[0-9]{6,}|[A-Za-z0-9_-]{32,}"), "[redacted]").take(400)
    }.getOrDefault("[invalid-url]")

    fun clean(value: String): String = value
        .replace(Regex("https?://[^\\s\"<>]+")) { url(it.value) }
        .replace(Regex("(?i)(password|passwd|cookie|authorization|token|ticket|secret|学号|密码)\\s*[=:：]\\s*[^\\s,;]+"), "$1=[redacted]")
        .replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "[email]")
        .replace(Regex("[0-9]{6,}|[A-Za-z0-9_-]{32,}"), "[redacted]")
        .replace('\n', ' ').replace('\r', ' ').take(6000)
}
