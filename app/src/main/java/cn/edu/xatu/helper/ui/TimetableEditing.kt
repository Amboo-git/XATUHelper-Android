@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.edu.xatu.helper.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.edu.xatu.helper.model.*
import java.time.LocalDate

data class CourseEditRequest(val kind: EditKind, val block: CourseBlock? = null)

@Composable private fun NumberChoice(label: String, value: Int, values: IntRange, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag(label)) { Text("$label：$value") }
        DropdownMenu(open, { open = false }) { values.forEach { number -> DropdownMenuItem(text = { Text(number.toString()) }, onClick = { open = false; onChange(number) }) } }
    }
}

@Composable fun TimetableEditDialog(state: UiState, request: CourseEditRequest, onDismiss: () -> Unit, onSave: (Timetable, List<TimetableEdit>) -> Unit) {
    val scope = remember { requireNotNull(state.baseTable) }
    val table = remember { requireNotNull(state.table) }
    val edits = remember { state.edits }
    val block = request.block
    val kind = request.kind
    val title = when(kind) { EditKind.ADD -> "添加课程"; EditKind.MOVE -> "修改课程"; EditKind.REMOVE -> "移除课程" }
    var name by remember { mutableStateOf(block?.name.orEmpty()) }
    var teacher by remember { mutableStateOf(block?.teacher.orEmpty()) }
    var room by remember { mutableStateOf(block?.room.orEmpty()) }
    var days by remember { mutableStateOf(setOf(block?.day ?: 1)) }
    var start by remember { mutableIntStateOf(block?.start ?: 1) }
    var end by remember { mutableIntStateOf(block?.end ?: 2.coerceAtMost(table.unitCount)) }
    var mode by remember { mutableIntStateOf(0) }
    var first by remember { mutableIntStateOf(state.week) }
    var last by remember { mutableIntStateOf(state.week) }
    val monday = LocalDate.parse(table.monday)
    var firstDate by remember { mutableStateOf(monday.plusWeeks((state.week - 1).toLong()).toString()) }
    var lastDate by remember { mutableStateOf(firstDate) }
    val available = remember { if (block == null) emptyList() else (1..30).filter { runCatching { sourceFor(table, block, listOf(it)) }.isSuccess } }
    var selected by remember { mutableStateOf(setOf(state.week).intersect(available.toSet()).ifEmpty { available.take(1).toSet() }) }
    var error by remember { mutableStateOf<String?>(null) }
    var proposed by remember { mutableStateOf<TimetableEdit?>(null) }
    var conflicts by remember { mutableStateOf(emptyList<TimetableConflict>()) }
    val id = remember { java.util.UUID.randomUUID().toString() }
    fun save() {
        try {
            require(state.account == scope.account && state.selectedSemester == scope.semester.id && state.kind == scope.kind && state.edits == edits && state.baseTable == scope) { "课表已变化，请关闭后重新编辑。" }
            val edit = if (kind == EditKind.ADD) {
                require(name.isNotBlank()) { "请填写课程名。" }
                require(days.isNotEmpty()) { "请选择上课日。" }
                if (mode == 2) {
                    val from = LocalDate.parse(firstDate); val to = LocalDate.parse(lastDate)
                    require(from <= to && from >= monday && to <= monday.plusDays(209)) { "日期应在本学期 30 周内，且结束日期不早于开始日期。" }
                }
                val added = ManualCourse(name.trim(), teacher.trim(), room.trim(), days.sorted(), start, end, first, if (mode == 0) first else last,
                    if (mode == 2) firstDate else null, if (mode == 2) lastDate else null)
                require(days.any { added.weeks(it, monday).isNotEmpty() }) { "所选范围内没有上课日期。" }
                TimetableEdit(id, kind, added = added)
            } else {
                require(selected.isNotEmpty()) { "请选择影响周次。" }
                TimetableEdit(id, kind, source = sourceFor(table, requireNotNull(block), selected.sorted()),
                    destination = if (kind == EditKind.MOVE) EditDestination(days.single(), start, room.trim()) else null)
            }
            val after = editTimetable(scope, edits + edit)
            require(after.pending[edit.id].isNullOrEmpty()) { "课程安排已变化，请重新选择课程。" }
            val found = timetableConflicts(after.table, edit)
            if (found.isNotEmpty()) { proposed = edit; conflicts = found } else { onSave(scope, edits + edit); onDismiss() }
        } catch (e: Exception) { error = if (e is java.time.format.DateTimeParseException) "日期格式应为 yyyy-MM-dd。" else e.message }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "取消编辑") } }, actions = {
            TextButton(onClick = ::save, enabled = !state.busy, modifier = Modifier.testTag("saveCourseEdit")) { Text(if (kind == EditKind.REMOVE) "确认移除" else "保存") }
        }) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("courseEditor"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (kind == EditKind.ADD) {
                    OutlinedTextField(name, { name = it }, label = { Text("课程名") }, modifier = Modifier.fillMaxWidth().testTag("courseName"), singleLine = true)
                    OutlinedTextField(teacher, { teacher = it }, label = { Text("教师（选填）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                } else Text(requireNotNull(block).name, style = MaterialTheme.typography.titleLarge)
                if (kind != EditKind.REMOVE) {
                    OutlinedTextField(room, { room = it }, label = { Text("教室（选填）") }, modifier = Modifier.fillMaxWidth().testTag("courseRoom"), singleLine = true)
                    val maximum = if (block == null) table.unitCount else table.unitCount - (block.end - block.start)
                    NumberChoice("起始节次", start, 1..maximum) { start = it; end = maxOf(end, it) }
                    if (block == null) NumberChoice("结束节次", end, start..table.unitCount) { end = it }
                    else Text("结束节次：${start + block.end - block.start}")
                    Text("上课日", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..7).forEach { day ->
                        FilterChip(day in days, { days = if (kind != EditKind.ADD || mode == 0) setOf(day) else if (day in days) days - day else days + day }, label = { Text("周${"一二三四五六日"[day - 1]}") })
                    } }
                }
                if (kind == EditKind.ADD) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("只上一次", "按教学周", "按日期").forEachIndexed { index, label ->
                        FilterChip(mode == index, { mode = index; if (index == 0) days = setOf(days.firstOrNull() ?: 1) }, label = { Text(label) })
                    } }
                    if (mode == 2) {
                        OutlinedTextField(firstDate, { firstDate = it }, label = { Text("开始日期 yyyy-MM-dd") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(lastDate, { lastDate = it }, label = { Text("结束日期 yyyy-MM-dd") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    } else {
                        NumberChoice(if (mode == 0) "上课周次" else "开始周", first, 1..30) { first = it; last = maxOf(last, it) }
                        if (mode == 1) NumberChoice("结束周", last, first..30) { last = it }
                    }
                } else {
                    Text("影响周次", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { selected = if (selected.size == available.size) emptySet() else available.toSet() }) { Text(if (selected.size == available.size) "取消全选" else "选择全部周次") }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { available.forEach { week ->
                        FilterChip(week in selected, { selected = if (week in selected) selected - week else selected + week }, label = { Text("第 $week 周") })
                    } }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    proposed?.let { edit -> AlertDialog(onDismissRequest = { proposed = null }, title = { Text("时间冲突") }, text = {
        Text(conflicts.joinToString("\n") { "${it.name} · 第 ${it.weeks.joinToString("、")} 周" })
    }, confirmButton = { TextButton(onClick = { onSave(scope, edits + edit); onDismiss() }) { Text("仍然保存") } }, dismissButton = { TextButton(onClick = { proposed = null }) { Text("返回调整") } }) }
}

@Composable fun TimetableHistoryDialog(state: UiState, onDismiss: () -> Unit, onSave: (Timetable, List<TimetableEdit>) -> Unit) {
    val base = state.baseTable ?: return
    var undo by remember { mutableStateOf<TimetableEdit?>(null) }
    var repair by remember { mutableStateOf<Pair<TimetableEdit, List<TimetableConflict>>?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = { TopAppBar(title = { Text("我的课表修改") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭修改记录") } }) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.edits.isEmpty()) Text("暂无课表修改")
                state.edits.lastOrNull()?.let { last -> OutlinedButton(onClick = { undo = last }, enabled = !state.busy) { Text("撤销上一次修改") } }
                state.edits.asReversed().forEach { edit ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(edit.title, style = MaterialTheme.typography.titleMedium)
                            edit.added?.let { added ->
                                Text("周${added.days.sorted().joinToString("、") { "一二三四五六日"[it - 1].toString() }} · 第 ${added.start}–${added.end} 节")
                                Text(if (added.firstDate != null) "${added.firstDate} – ${added.lastDate}" else "第 ${added.firstWeek}–${added.lastWeek} 周")
                                if (added.room.isNotBlank()) Text(added.room)
                            }
                            edit.source?.let { source -> Text("影响周次：${source.anchors.map { it.resolve(LocalDate.parse(base.monday)) }.joinToString("、")}") }
                            state.pendingEdits[edit.id]?.let { weeks ->
                                Text("待确认 · 第 ${weeks.joinToString("、")} 周", color = MaterialTheme.colorScheme.error)
                                if (edit.source == null) Text("课程节次已超出当前课表范围，可撤销后重新添加。")
                                val source = edit.source
                                if (source != null) {
                                    val earlier = state.edits.takeWhile { it.id != edit.id }
                                    val previous = editTimetable(base, earlier).table
                                    weeks.forEach { week ->
                                        val candidates = blocksFor(previous.courses, week).filter { it.key == source.key && it.end - it.start + 1 == source.ids.size }
                                        if (candidates.isEmpty()) Text("第 $week 周没有可重新应用的安排。")
                                        candidates.forEach { candidate -> TextButton(onClick = {
                                            val index = source.anchors.indexOfFirst { it.resolve(LocalDate.parse(base.monday)) == week }
                                            val ids = sourceFor(previous, candidate, listOf(week)).ids
                                            val updated = edit.copy(source = source.copy(replacements = source.replacements + (index to ids)))
                                            val after = editTimetable(base, earlier + updated).table
                                            repair = updated to timetableConflicts(after, updated)
                                        }, enabled = !state.busy) { Text("重新应用：第 $week 周 · 周${"一二三四五六日"[candidate.day - 1]} · ${candidate.periodText} · ${candidate.room}") } }
                                    }
                                }
                            }
                            TextButton(onClick = { undo = edit }, enabled = !state.busy) { Text(if (edit.source?.key?.startsWith("local-") == false) "恢复教务安排" else "撤销这项修改") }
                        }
                    }
                }
            }
        }
    }
    undo?.let { edit -> AlertDialog(onDismissRequest = { undo = null }, title = { Text("撤销这项修改？") }, text = { Text("后续针对同一次课程的修改可能需要重新确认。") },
        confirmButton = { TextButton(onClick = { onSave(base, state.edits.filter { it.id != edit.id }); undo = null }) { Text("撤销修改") } }, dismissButton = { TextButton(onClick = { undo = null }) { Text("取消") } }) }
    repair?.let { (edit, conflicts) -> AlertDialog(onDismissRequest = { repair = null }, title = { Text(if (conflicts.isEmpty()) "重新应用修改？" else "重新应用后有课程冲突") },
        text = { Text(if (conflicts.isEmpty()) edit.title else conflicts.joinToString("\n") { "${it.name} · 第 ${it.weeks.joinToString("、")} 周" }) },
        confirmButton = { TextButton(onClick = { onSave(base, state.edits.map { if (it.id == edit.id) edit else it }); repair = null }) { Text(if (conflicts.isEmpty()) "重新应用" else "仍然保存") } }, dismissButton = { TextButton(onClick = { repair = null }) { Text("取消") } }) }
}
