package cn.edu.xatu.helper.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.edu.xatu.helper.BuildConfig
import cn.edu.xatu.helper.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.*

/** Tests substitute the offline grid and never prefetch community map tiles. */
internal val LocalMapNetworkEnabled = staticCompositionLocalOf { true }
private object MapTiles {
    private var client: OkHttpClient? = null
    @Synchronized fun client(context: Context): OkHttpClient = client ?: OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "map_tiles"), 20L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build().also { client = it }
}

@Composable internal fun CoordinateMap(center: CheckinCoordinate, onMove: (CheckinCoordinate) -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val density = LocalDensity.current
    val network = LocalMapNetworkEnabled.current
    val tile = with(density) { 256.dp.toPx() }
    var zoom by remember { mutableIntStateOf(16) }
    var zoomGesture by remember { mutableFloatStateOf(1f) }
    var failed by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(center)
    val moved by rememberUpdatedState(onMove)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxWidth().height(280.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
        .testTag("coordinateMap").onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            visible = bounds.width > 0 && bounds.height > 0
        }.pointerInput(zoom, tile) {
            detectTransformGestures { _, pan, scale, _ ->
                val world = tile * (1 shl zoom)
                if (pan != Offset.Zero) moved(MapCoordinates.coordinate(MapCoordinates.x(current.longitude) - pan.x / world, MapCoordinates.y(current.latitude) - pan.y / world))
                zoomGesture *= scale
                if (zoomGesture > 1.5f) { zoom = (zoom + 1).coerceAtMost(18); zoomGesture = 1f }
                if (zoomGesture < .67f) { zoom = (zoom - 1).coerceAtLeast(3); zoomGesture = 1f }
            }
        }) {
        val width = constraints.maxWidth.toFloat(); val height = constraints.maxHeight.toFloat()
        val count = 1 shl zoom
        val cx = MapCoordinates.x(center.longitude) * count; val cy = MapCoordinates.y(center.latitude) * count
        val firstX = floor(cx - width / tile / 2).toInt(); val lastX = floor(cx + width / tile / 2).toInt()
        val firstY = floor(cy - height / tile / 2).toInt().coerceAtLeast(0); val lastY = floor(cy + height / tile / 2).toInt().coerceAtMost(count - 1)
        Canvas(Modifier.matchParentSize()) {
            for (line in 0..8) {
                drawLine(grid, Offset(size.width * line / 8, 0f), Offset(size.width * line / 8, size.height))
                drawLine(grid, Offset(0f, size.height * line / 8), Offset(size.width, size.height * line / 8))
            }
        }
        if (network && visible) for (x in firstX..lastX) for (y in firstY..lastY) key(zoom, x, y) {
            val wrapped = ((x % count) + count) % count
            val bitmap by produceState<android.graphics.Bitmap?>(null, zoom, wrapped, y) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val request = Request.Builder().url("https://tile.openstreetmap.org/$zoom/$wrapped/$y.png")
                            .header("User-Agent", "XATUHelper/${BuildConfig.VERSION_NAME} (+https://github.com/Kelejun/XATUHelper-Android)").build()
                        MapTiles.client(context.applicationContext).newCall(request).execute().use { response ->
                            if (!response.isSuccessful) null else response.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                        }
                    }.getOrNull()
                }
                if (value == null) failed = true
            }
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.offset { IntOffset(((x - cx) * tile + width / 2).roundToInt(), ((y - cy) * tile + height / 2).roundToInt()) }.size(256.dp)) }
        }
        Canvas(Modifier.size(28.dp).align(Alignment.Center)) {
            val middle = size.width / 2
            drawCircle(androidx.compose.ui.graphics.Color.White, middle, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
            drawLine(accent, Offset(middle, 0f), Offset(middle, size.height), 2.dp.toPx())
            drawLine(accent, Offset(0f, middle), Offset(size.width, middle), 2.dp.toPx())
        }
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            FilledTonalIconButton(onClick = { zoom = (zoom + 1).coerceAtMost(18) }, enabled = zoom < 18) { Icon(Icons.Default.Add, "放大地图") }
            FilledTonalIconButton(onClick = { zoom = (zoom - 1).coerceAtLeast(3) }, enabled = zoom > 3) { Icon(Icons.Default.Remove, "缩小地图") }
        }
        if (failed || !network) Surface(Modifier.align(Alignment.TopStart).padding(8.dp).widthIn(max = 220.dp), shape = MaterialTheme.shapes.small) {
            Text(if (network) "地图暂不可用，可直接输入经纬度" else "离线地图预览，可直接输入经纬度", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
        }
        Surface(Modifier.align(Alignment.BottomEnd), color = MaterialTheme.colorScheme.surface.copy(alpha = .95f)) {
            Text("© OpenStreetMap contributors", Modifier.clickable { uri.openUri("https://www.openstreetmap.org/copyright") }.padding(6.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}
