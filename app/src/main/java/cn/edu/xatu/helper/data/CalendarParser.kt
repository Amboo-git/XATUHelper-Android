package cn.edu.xatu.helper.data

import cn.edu.xatu.helper.model.*
import org.jsoup.Jsoup
import java.time.LocalDate

object CalendarParser {
    const val index = "https://jwcw.xatu.edu.cn/index/xagydxxl.htm"
    fun latest(html: String, base: String = index): String = Jsoup.parse(html, base).select("a[href]").firstOrNull { "校历" in it.text() && it.absUrl("href") != base && ("/info/" in it.absUrl("href") || "/news/content.jsp" in it.absUrl("href")) }?.absUrl("href") ?: throw ParseFailure("校历列表未找到正文，请在校园网下重试")
    fun image(html: String, base: String): String {
        val images = Jsoup.parse(html, base).select("img[src]")
        val image = images.firstOrNull { "__local" in it.attr("src") } ?: images.firstOrNull { it.hasAttr("orisrc") } ?: images.firstOrNull { node -> Regex("(?i)\\.(jpg|jpeg|png)(?:\\?|$)").containsMatchIn(node.attr("src")) && listOf("logo", "nav-", "bottom-icon").none { it in node.attr("src") } }
        return image?.let { it.absUrl(if (it.hasAttr("orisrc")) "orisrc" else "src") }?.takeIf { it.isNotBlank() } ?: throw ParseFailure("校历正文中未找到原图")
    }
    // Coordinates preserve the calendar's reading order; OCR text order can put footnotes last.
    data class Cell(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val x get() = (left + right) / 2
        val y get() = (top + bottom) / 2
        val height get() = bottom - top
    }
    fun coverageEnd(cells: List<Cell>, semesters: List<SemesterSpan>): String? {
        val lastSemester = semesters.mapNotNull { runCatching { LocalDate.parse(it.end) }.getOrNull() }.maxOrNull() ?: return null
        val monthPattern = Regex("""(?:(\d{4})年)?(\d{1,2})月""")
        val months = cells.mapNotNull { cell -> monthPattern.matchEntire(cell.text.replace(Regex("\\s+"), ""))?.let { cell to it } }
        val bottom = months.maxOfOrNull { it.first.y } ?: return null
        val (header, match) = months.filter { bottom - it.first.y <= it.first.height.coerceAtLeast(10) }.maxByOrNull { it.first.x } ?: return null
        val month = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: lastSemester.year + if (month < lastSemester.monthValue) 1 else 0
        val weekRow = cells.filter { it.text.replace(" ", "") == "周次" && it.y > header.y }.minOfOrNull { it.top } ?: return null
        val days = cells.filter { it.top > header.bottom && it.bottom < weekRow && it.x > header.x && it.text.matches(Regex("[0-9]{1,2}")) && it.text.toInt() in 1..31 }
        val lastRow = days.maxOfOrNull { it.y } ?: return null
        val last = days.filter { lastRow - it.y <= it.height.coerceAtLeast(8) / 2 }.maxByOrNull { it.x } ?: return null
        return runCatching { LocalDate.of(year, month, last.text.toInt()) }.getOrNull()
            ?.takeIf { it > lastSemester && java.time.temporal.ChronoUnit.DAYS.between(lastSemester, it) <= 120 }?.toString()
    }
    fun semesters(text: String): List<SemesterSpan> {
        val compact = text.replace(Regex("\\s+"), "")
        val regex = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日[-－—–~～至到]{1,2}(?:(\d{4})年)?(\d{1,2})月(\d{1,2})日""")
        return regex.findAll(compact).mapNotNull { match ->
            val g = match.groupValues
            val start = runCatching { LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt()) }.getOrNull() ?: return@mapNotNull null
            val year = g[4].toIntOrNull() ?: (start.year + if (g[5].toInt() < start.monthValue) 1 else 0)
            val end = runCatching { LocalDate.of(year, g[5].toInt(), g[6].toInt()) }.getOrNull() ?: return@mapNotNull null
            if (java.time.temporal.ChronoUnit.DAYS.between(start, end) !in 30..260) return@mapNotNull null
            SemesterSpan("", start.toString(), end.toString())
        }.distinctBy { it.start to it.end }.take(4).mapIndexed { i, span -> span.copy(label = "第${i + 1}学期") }.toList()
    }
}
