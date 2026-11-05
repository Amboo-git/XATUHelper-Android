package cn.edu.xatu.helper.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import cn.edu.xatu.helper.model.CheckinCoordinate
import cn.edu.xatu.helper.model.CoordinateSystem
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class LocatedPoint(val coordinate: CheckinCoordinate, val accuracyMeters: Float?)
internal class LocationRequestException(message: String) : Exception(message)
internal interface CurrentLocationSource {
    val hasPermission: Boolean
    suspend fun locate(): LocatedPoint
}

/** Foreground, one-shot Android location. Independent of map tiles and custom check-in settings. */
internal class AndroidCurrentLocationSource(context: Context) : CurrentLocationSource {
    private val context = context.applicationContext
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    override val hasPermission get() = granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)

    @Suppress("MissingPermission", "DEPRECATION")
    override suspend fun locate(): LocatedPoint = withContext(Dispatchers.Main.immediate) {
        if (!hasPermission) throw LocationRequestException("请先允许定位权限。")
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val precise = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            (it != LocationManager.GPS_PROVIDER || precise) && runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
        }
        if (providers.isEmpty()) {
            val gpsEnabled = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
            throw LocationRequestException(if (gpsEnabled && !precise) "请在应用权限中允许精确定位后重试。" else "系统定位未开启，请在系统设置中开启定位。")
        }
        var best: Location? = null
        val fix = withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine<Location> { continuation ->
                lateinit var listener: LocationListener
                fun remove() { runCatching { manager.removeUpdates(listener) } }
                listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val point = CheckinCoordinate(location.latitude, location.longitude, CoordinateSystem.WGS84)
                        if (!point.isValid || !continuation.isActive) return
                        val age = android.os.SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
                        if (age < 0 || age > 60_000_000_000L) return
                        if (best == null || (location.hasAccuracy() && (!best!!.hasAccuracy() || location.accuracy < best!!.accuracy))) best = location
                        if (location.provider == LocationManager.GPS_PROVIDER || (location.hasAccuracy() && location.accuracy <= 100) || LocationManager.GPS_PROVIDER !in providers) {
                            remove(); continuation.resume(location)
                        }
                    }
                    override fun onProviderDisabled(provider: String) {
                        if (providers.none { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } && continuation.isActive) {
                            remove(); continuation.resumeWithException(LocationRequestException("系统定位已关闭，请开启后重试。"))
                        }
                    }
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                continuation.invokeOnCancellation { remove() }
                try {
                    providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
                } catch (_: SecurityException) {
                    remove()
                    if (continuation.isActive) continuation.resumeWithException(LocationRequestException("定位权限已变化，请重新允许定位后重试。"))
                } catch (_: IllegalArgumentException) {
                    remove()
                    if (continuation.isActive) continuation.resumeWithException(LocationRequestException("当前定位服务不可用，请稍后重试。"))
                }
            }
        } ?: best ?: throw LocationRequestException("暂未获取到当前位置，请检查系统定位，或移至开阔处后重试。")
        LocatedPoint(CheckinCoordinate(fix.latitude, fix.longitude, CoordinateSystem.WGS84), fix.accuracy.takeIf { fix.hasAccuracy() && it.isFinite() && it >= 0 })
    }
}
