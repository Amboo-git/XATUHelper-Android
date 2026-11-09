package com.xatune.app.data

import com.xatune.app.model.*
import org.jsoup.Jsoup

object EamsParser {
    fun isLogin(html: String): Boolean = Jsoup.parse(html).select("#pwdEncryptSalt, input[name=password], form[action*=loginExt]").isNotEmpty()
    fun shell(html: String, project: String = ""): Shell {
        if (isLogin(html)) throw SessionExpired()
        val ids = Regex("""["']ids["']\s*,\s*["'](\d+)["']""").findAll(html).map { it.groupValues[1] }.toList()
        val semester = Regex("""semesterCalendar\s*\(\s*\{[^}]*?value\s*:\s*["'](\d+)["']""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: throw ParseFailure()
        val tag = Jsoup.parse(html).select("[id]").firstOrNull { it.id().matches(Regex("semesterBar\\d+Semester")) }?.id() ?: "semesterCalendar_target"
        return Shell(ids.firstOrNull() ?: throw ParseFailure(), ids.getOrNull(1), semester, tag, project)
    }
    fun semesters(text: String): List<Semester> {
        val records = Regex("""\{[^{}]*\}""").findAll(text).mapNotNull { match ->
            fun field(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
                Regex("""["']?${Regex.escape(key)}["']?\s*:\s*(?:["']([^"']*)["']|(\d+))""").find(match.value)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            }
            val id = field("id") ?: return@mapNotNull null
            val year = field("schoolYear", "school_year", "academicYear", "year").orEmpty()
            val name = field("name", "termName", "term", "title").orEmpty()
            if (year.isEmpty() && name.isEmpty()) return@mapNotNull null
            val term = if (name.contains("学期")) name else "${name.ifEmpty { "1" }}学期"
            Semester(id, if (year.isEmpty()) term else "${year}学年$term", field("startDate", "start_date", "beginDate", "start"))
        }.toList()
        val fallback = Jsoup.parse(text).select("[val], option[value]").mapNotNull {
            val id = it.attr("val").ifEmpty { it.attr("value") }
            if (id.toLongOrNull() != null && it.text().isNotBlank()) Semester(id, it.text()) else null
        }
        return (records.ifEmpty { fallback }).distinctBy { it.id }.sortedByDescending { it.id.toLongOrNull() ?: 0 }
    }
    fun officialWeek(html: String): Int? {
        val patterns = listOf(
            """<span>\s*(\d{1,2})\s*</span>\s*<i>\s*/\s*\d+\s*</i>[\s\S]{0,80}?当前教学周""",
            """当前教学周[\s\S]{0,80}?<span>\s*(\d{1,2})\s*</span>""",
            """第\s*(\d{1,2})\s*周""", """currentWeek\s*[:=]\s*["']?(\d{1,2})"""
        )
        return patterns.firstNotNullOfOrNull { Regex(it).find(html)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { n -> n in 1..30 } }
    }
}
