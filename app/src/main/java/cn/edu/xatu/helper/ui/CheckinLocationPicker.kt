package cn.edu.xatu.helper.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.edu.xatu.helper.model.*
import cn.edu.xatu.helper.platform.*
import java.util.Locale

@Composable internal fun rememberCheckinLocationSettings(): State<CheckinLocationSettings> {
    val context = LocalContext.current
    val store = remember(context) { CheckinLocationPreferences(context) }
    val state = remember(store) { mutableStateOf(store.read()) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.value = store.read() }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CheckinLocationPicker(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { CheckinLocationPreferences(context) }
    val original = remember { preferences.read().coordinate ?: CheckinCoordinate(34.383, 108.981) }
    var latitude by rememberSaveable { mutableStateOf("%.6f".format(Locale.ROOT, original.latitude)) }
    var longitude by rememberSaveable { mutableStateOf("%.6f".format(Locale.ROOT, original.longitude)) }
    var system by rememberSaveable { mutableStateOf(original.system) }
    var systemMenu by remember { mutableStateOf(false) }
    val selected = latitude.trim().toDoubleOrNull()?.let { lat -> longitude.trim().toDoubleOrNull()?.let { lng -> CheckinCoordinate(lat, lng, system).takeIf { it.isValid } } }
    var mapCenter by remember { mutableStateOf(original.gps()) }
    LaunchedEffect(selected) {
        kotlinx.coroutines.delay(350)
        selected?.gps()?.let { mapCenter = it }
    }
    val back = Modifier.predictiveBack(onBack = onBack)
    Scaffold(modifier = back, topBar = { TopAppBar(title = { Text("选择签到位置") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回设置") } }, actions = {
        TextButton(onClick = { selected?.let { preferences.save(it); onBack() } }, enabled = selected != null, modifier = Modifier.testTag("saveCheckinLocation")) { Icon(Icons.Default.Check, null); Text("保存") }
    }) }) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CoordinateMap(mapCenter) { point ->
                mapCenter = point
                latitude = "%.6f".format(Locale.ROOT, point.latitude)
                longitude = "%.6f".format(Locale.ROOT, point.longitude)
                system = CoordinateSystem.WGS84
            }
            Text("移动地图，将十字中心对准要传入的位置；也可以直接输入经纬度。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionHeading("坐标")
            Box {
                FilledTonalButton(onClick = { systemMenu = true }, modifier = Modifier.fillMaxWidth().testTag("coordinateSystem")) { Text(system.title) }
                DropdownMenu(systemMenu, { systemMenu = false }) {
                    CoordinateSystem.entries.forEach { entry -> DropdownMenuItem(text = { Text(entry.title) }, onClick = { system = entry; systemMenu = false }) }
                }
            }
            OutlinedTextField(latitude, { latitude = it }, label = { Text("纬度（-90～90）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), isError = selected == null, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().testTag("checkinLatitude"))
            OutlinedTextField(longitude, { longitude = it }, label = { Text("经度（-180～180）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), isError = selected == null, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().testTag("checkinLongitude"))
            if (selected == null) Text("请输入有效的经纬度。", color = MaterialTheme.colorScheme.error)
            InfoPanel("地图选点使用 GPS（WGS84）；手动输入地图或高德坐标时请选择 GCJ02。传给签到网页前会统一为 GPS 坐标。保存位置后，还需在设置中开启「传入定位」。")
        }
    }
}
