package cn.edu.xatu.helper.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.xatu.helper.BuildConfig
import cn.edu.xatu.helper.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun number(value: Double): String = String.format(Locale.CHINA, "%.2f", value).trimEnd('0').trimEnd('.')
private fun updated(at: Long): String = java.time.Instant.ofEpochMilli(at).atZone(SchoolZone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AcademicScreen(kind: AcademicKind, state: UiState, model: HelperViewModel, onBack: () -> Unit, onRelogin: () -> Unit, onWeb: () -> Unit) {
    var selectedSemester by rememberSaveable(kind) { mutableStateOf("") }
    val semester = selectedSemester.ifEmpty { state.currentSemester.ifEmpty { state.selectedSemester } }
    val allGrades = state.allGrades
    var semesterMenu by remember { mutableStateOf(false) }
    val scope = if (kind == AcademicKind.PLAN || kind == AcademicKind.GRADES && allGrades) "all" else semester
    val reading = state.academic[academicKey(kind, scope)] ?: AcademicUi(loading = true)
    LaunchedEffect(kind, scope) { model.loadAcademic(kind, scope) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(kind.title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回校园") } }, actions = {
            IconButton(onClick = { model.loadAcademic(kind, scope, true) }, enabled = !reading.loading) { Icon(Icons.Default.Refresh, "刷新${kind.title}") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (reading.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.demo) Text("示例数据 · 未连接学校账号", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelMedium)
            if (kind != AcademicKind.PLAN) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { semesterMenu = true }, enabled = !allGrades || kind == AcademicKind.EXAMS, modifier = Modifier.fillMaxWidth()) {
                            Text(state.semesters.firstOrNull { it.id == semester }?.name ?: "当前学期", maxLines = 2)
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(semesterMenu, { semesterMenu = false }) {
                            state.semesters.forEach { term -> DropdownMenuItem(text = { Text(term.name) }, onClick = { semesterMenu = false; selectedSemester = term.id }) }
                        }
                    }
                    if (kind == AcademicKind.GRADES) FilterChip(selected = allGrades, onClick = { model.gradeScope(!allGrades) }, label = { Text("所有学期") })
                }
            }
            reading.error?.let { message ->
                Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = onWeb) { Text("在教务网页中查看") }
                        if (reading.data != null) Text("正在显示上次保存的数据。", style = MaterialTheme.typography.bodySmall)
                        if (reading.needsLogin) TextButton(onClick = onRelogin) { Text("重新登录") }
                        else TextButton(onClick = { model.loadAcademic(kind, scope, true) }, enabled = !reading.loading) { Text("重试") }
                    }
                }
            }
            val data = reading.data
            if (data == null) {
                if (!reading.loading && reading.error == null) Text("尚无本地数据，请刷新。", Modifier.padding(24.dp))
            } else {
                Text("更新于 ${updated(data.fetchedAt)} · 可离线查看", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (kind) {
                    AcademicKind.EXAMS -> ExamList(data.exams, state.semesters.firstOrNull { it.id == semester })
                    AcademicKind.GRADES -> data.grades?.let { GradeList(it, scope) }
                    AcademicKind.PLAN -> data.plan?.let { PlanList(it, data.catalog, data.catalogWarning) }
                }
            }
        }
    }
}

@Composable private fun ExamList(exams: List<Exam>, semester: Semester?) {
    val anchor = runCatching { LocalDate.parse(semester?.startDate?.take(10)) }.getOrNull() ?: schoolToday()
    var today by remember { mutableStateOf(schoolToday()) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { today = schoolToday() }
    LaunchedEffect(Unit) { while (true) { today = schoolToday(); kotlinx.coroutines.delay(30_000) } }
    val sorted = remember(exams, anchor, today) { orderedExams(exams, anchor, today) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("examsList")) {
        if (sorted.isEmpty()) item { EmptyMessage("当前学期暂无考试安排", "学校公布安排后，可在这里刷新查看。") }
        items(sorted, key = { it.id }) { exam ->
            val index = sorted.indexOf(exam)
            val date = exam.date(anchor)
            val bottom = date == null || date < today
            val previous = sorted.getOrNull(index - 1)?.date(anchor)
            if (bottom && (index == 0 || previous != null && previous >= today)) Text("已结束 / 待定", style = MaterialTheme.typography.titleMedium)
            else if (!bottom && (index == 0 || previous != date)) Text("$date · ${sorted.count { it.date(anchor) == date }} 场考试", style = MaterialTheme.typography.titleMedium)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val date = exam.date(anchor)
                    if (date != null) Text(date.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(exam.course.ifBlank { exam.name.ifBlank { "考试" } }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        val remaining = exam.remainingText(anchor, today)
                        if (remaining.isNotEmpty()) Text(remaining, Modifier.testTag("examCountdown:${exam.id}"),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(exam.time.ifEmpty { "考试时间待公布" })
                    Text("考场：${exam.room.ifEmpty { "待公布" }}${if (exam.seat.isNotBlank()) " · 座位 ${exam.seat}" else ""}")
                    if (exam.name.isNotBlank()) Text(exam.name, style = MaterialTheme.typography.bodySmall)
                    if (exam.note.isNotBlank()) Text(exam.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun GradeList(page: GradePage, scope: String) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(page.grades, search) { page.grades.filter { search.isBlank() || matchesCourse(it.course, it.code, search) } }
    val weighted = page.grades.mapNotNull { g -> val credit = g.credit.toDoubleOrNull(); val point = g.gpa.toDoubleOrNull(); if (credit != null && credit > 0 && point != null) credit to point else null }
    val gpa = page.summary.gpa ?: weighted.takeIf { it.isNotEmpty() }?.let { entries -> entries.sumOf { it.first * it.second } / entries.sumOf { it.first } }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("gradesList")) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${page.summary.count ?: page.grades.size} 门课程 · ${number(page.summary.credits ?: page.grades.sumOf { it.credit.toDoubleOrNull() ?: 0.0 })} 学分", style = MaterialTheme.typography.titleMedium)
                    Text("平均绩点 ${gpa?.let(::number) ?: "—"}", style = MaterialTheme.typography.headlineSmall)
                    Text(if (page.summary.gpa != null) "绩点汇总来自教务系统" else "绩点按有学分和绩点的课程加权计算", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { OutlinedTextField(search, { search = it }, singleLine = true, label = { Text("搜索课程或课程号") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().testTag("gradeSearch")) }
        if (filtered.isEmpty()) item { EmptyMessage(if (page.grades.isEmpty()) "当前范围暂无成绩" else "没有匹配的课程", "可切换学期或调整搜索内容。") }
        items(filtered, key = { it.id }) { grade ->
            var expanded by rememberSaveable(scope, grade.id) { mutableStateOf(false) }
            OutlinedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(grade.course, style = MaterialTheme.typography.titleMedium)
                    Text("${grade.score.ifEmpty { "未公布" }} / ${grade.gpa.ifEmpty { "—" }}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(listOf(grade.term, grade.code, grade.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" }.orEmpty()).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (grade.parts.isNotEmpty()) {
                        Text(if (expanded) "收起分项成绩" else "查看分项成绩", style = MaterialTheme.typography.labelLarge)
                        if (expanded) grade.parts.forEach { part -> Text("${part.name}：${part.score}") }
                    }
                }
            }
        }
    }
}

@Composable private fun PlanList(plan: StudyPlan, catalog: List<CourseInfo>, warning: String?) {
    var query by rememberSaveable { mutableStateOf("") }
    var term by rememberSaveable { mutableStateOf("") }
    var termMenu by remember { mutableStateOf(false) }
    val terms = remember(catalog) { catalog.map { it.term }.filter(String::isNotBlank).distinct().sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it })) }
    val byCode = remember(catalog) { catalog.filter { it.code.isNotBlank() }.associateBy { it.code } }
    val byName = remember(catalog) { catalog.associateBy { it.name } }
    fun info(course: PlanCourse) = byCode[course.code] ?: byName[course.name]
    fun matches(course: PlanCourse): Boolean = (query.isBlank() || matchesCourse(course.name, course.code, query)) && (term.isBlank() || info(course)?.term == term)
    val rows = remember(plan, query, term, catalog) { plan.sections.flatMap { it.allCourses() }.filter(::matches) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("planList")) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("毕业学分", style = MaterialTheme.typography.titleMedium)
                    Text("${number(plan.earned)} / ${number(plan.required)}", style = MaterialTheme.typography.headlineLarge)
                    LinearProgressIndicator(progress = { if (plan.required > 0) (plan.earned / plan.required).toFloat().coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                    Text("还差 ${number((plan.required - plan.earned).coerceAtLeast(0.0))} 学分${if (plan.gpa.isNotBlank()) " · GPA ${plan.gpa}" else ""}")
                    if (plan.major.isNotBlank()) Text(plan.major, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (warning != null) item { Text(warning, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        item { OutlinedTextField(query, { query = it }, label = { Text("搜索计划课程") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("planSearch")) }
        item {
            Box {
                OutlinedButton(onClick = { termMenu = true }) { Text(if (term.isBlank()) "全部开课学期" else "开课学期 $term"); Icon(Icons.Default.ArrowDropDown, null) }
                DropdownMenu(termMenu, { termMenu = false }) {
                    DropdownMenuItem(text = { Text("全部开课学期") }, onClick = { term = ""; termMenu = false })
                    terms.forEach { value -> DropdownMenuItem(text = { Text("开课学期 $value") }, onClick = { term = value; termMenu = false }) }
                }
            }
        }
        if (query.isNotBlank() || term.isNotBlank()) {
            item { Text("找到 ${rows.size} 门课程", style = MaterialTheme.typography.labelLarge) }
            items(rows, key = { it.id }) { course -> PlanCourseCard(course, info(course)) }
        } else {
            items(plan.sections, key = { it.id }) { section -> PlanModule(section, byCode, byName) }
        }
    }
}

@Composable private fun PlanModule(section: PlanSection, byCode: Map<String, CourseInfo>, byName: Map<String, CourseInfo>) {
    var expanded by rememberSaveable(section.id) { mutableStateOf(false) }
    OutlinedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            Text("${number(section.earned)} / ${number(section.required)} 学分 · ${section.allCourses().size} 门课程")
            Text(if (expanded) "收起模块" else "展开模块", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
    if (expanded) Column(Modifier.padding(start = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        section.courses.forEach { course -> PlanCourseCard(course, byCode[course.code] ?: byName[course.name]) }
        section.children.forEach { child -> PlanModule(child, byCode, byName) }
    }
}
@Composable private fun PlanCourseCard(course: PlanCourse, info: CourseInfo?) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleMedium)
            Text("${number(course.earned)} / ${number(course.required)} 学分 · ${if (course.passed) "已通过" else "未完成"}", color = if (course.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (course.score.isNotBlank()) Text("成绩 ${course.score}")
            if (info != null) Text(listOf(info.nature, info.hours.takeIf(String::isNotBlank)?.let { "$it 学时" }.orEmpty(), info.term.takeIf(String::isNotBlank)?.let { "开课学期 $it" }.orEmpty()).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            Text(course.code, style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable private fun EmptyMessage(title: String, detail: String) {
    Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(state: UiState, onBack: () -> Unit, onCalibrate: () -> Unit, onRelogin: () -> Unit, onLogout: () -> Unit, onReminders: () -> Unit = {}, onWidgets: () -> Unit = {}, onWeekend: (Boolean) -> Unit = {}, onDiagnostics: (Boolean) -> Unit = {}, campus: CampusState = CampusState(), onAutoElectric: (Boolean) -> Unit = {}) {
    var confirmLogout by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("设置") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回校园") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("settings"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("账号", style = MaterialTheme.typography.titleMedium) }
            item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (state.demo) "示例账号" else "学号 ${state.account}")
                Text("登录凭据加密保存在本机，课表和教务数据可离线查看。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!state.demo) OutlinedButton(onClick = onRelogin, enabled = !state.busy) { Text("重新登录") }
            } } }
            item { Text("课程表", style = MaterialTheme.typography.titleMedium) }
            item { OutlinedButton(onClick = onCalibrate, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.EditCalendar, null); Spacer(Modifier.width(8.dp)); Text("校准第一周日期") } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("显示周末", Modifier.weight(1f)); Switch(state.showWeekend, onWeekend, modifier = Modifier.testTag("weekendSetting"))
            } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("每日自动刷新电费余额", Modifier.weight(1f)); Switch(campus.autoElectric, onAutoElectric, enabled = !campus.busy)
            } }
            item { Text("提醒与桌面", style = MaterialTheme.typography.titleMedium) }
            item { OutlinedButton(onClick = onReminders, modifier = Modifier.fillMaxWidth()) { Text("提醒设置") } }
            item { OutlinedButton(onClick = onWidgets, modifier = Modifier.fillMaxWidth()) { Text("桌面小组件") } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("显示诊断数据", Modifier.weight(1f)); Switch(state.showDiagnostics, onDiagnostics, modifier = Modifier.testTag("diagnosticsSetting"))
            } }
            if (state.showDiagnostics) item {
                Text(diagnosticSummary(state), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("diagnostics"))
                TextButton(onClick = {
                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("XATU Helper 诊断", diagnosticSummary(state)))
                    copied = true
                }) { Text(if (copied) "已复制诊断数据" else "复制诊断数据") }
            }
            item { Text("关于", style = MaterialTheme.typography.titleMedium) }
            item { Text("XATU Helper ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Medium) }
            item { Text("西安工业大学校园助手 · 原生 Android", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Spacer(Modifier.height(16.dp)); OutlinedButton(onClick = { confirmLogout = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("logout"), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("退出登录") } }
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("退出登录？") }, text = { Text("将清除此设备上的账号、登录状态、课表和教务缓存。") }, confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("确认退出") } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } })
}
