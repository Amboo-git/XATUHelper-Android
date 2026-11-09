package com.xatune.app.ui

import com.xatune.app.BuildConfig
import com.xatune.app.model.*

/** Deliberately constructed from counters: never copy raw HTML, errors, identity or session values. */
fun diagnosticSummary(state: UiState): String = buildString {
    appendLine("XATune ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT}) · ${BuildConfig.BUILD_TYPE}")
    appendLine("课表缓存：${if (state.table == null) "无" else "有"}")
    appendLine("课表类型：${state.kind.title}；周次：${state.week}")
    appendLine("学期数：${state.semesters.size}；课程节次：${state.table?.courses?.size ?: 0}")
    appendLine("课表更新毫秒：${state.table?.fetchedAt ?: 0}")
    AcademicKind.entries.forEach { kind ->
        val entries = state.academic.filterKeys { it.startsWith("${kind.name}:") }.values
        appendLine("${kind.title}缓存：${entries.count { it.data != null }}；加载中：${entries.count { it.loading }}；失败：${entries.count { it.error != null }}")
    }
    append("需要重新登录：${state.needsLogin}")
}
