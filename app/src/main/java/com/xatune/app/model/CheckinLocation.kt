package com.xatune.app.model

import com.google.gson.JsonParser
import kotlin.math.*

enum class CoordinateSystem(val title: String) { GCJ02("地图 / 高德（GCJ02）"), WGS84("GPS（WGS84）") }

data class CheckinCoordinate(val latitude: Double, val longitude: Double, val system: CoordinateSystem = CoordinateSystem.GCJ02) {
    val isValid get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    fun gps(): CheckinCoordinate = if (system == CoordinateSystem.WGS84) this else LocationCoordinates.toWgs84(this)
    companion object {
        fun decode(text: String?): CheckinCoordinate? = runCatching {
            val value = JsonParser.parseString(text).asJsonObject
            CheckinCoordinate(value.get("latitude").asDouble, value.get("longitude").asDouble,
                value.get("system")?.asString?.let { CoordinateSystem.valueOf(it.uppercase()) } ?: CoordinateSystem.GCJ02).takeIf { it.isValid }
        }.getOrNull()
    }
}

/** Same conversion constants and iterative inverse as the iOS location bridge. */
object LocationCoordinates {
    fun toGcj02(point: CheckinCoordinate): CheckinCoordinate {
        val lat = point.latitude; val lng = point.longitude
        if (lng !in 72.004..137.8347 || lat !in .8293..55.8271) return point.copy(system = CoordinateSystem.GCJ02)
        val x = lng - 105; val y = lat - 35
        var dLat = -100 + 2*x + 3*y + .2*y*y + .1*x*y + .2*sqrt(abs(x))
        dLat += (20*sin(6*x*PI) + 20*sin(2*x*PI))*2/3
        dLat += (20*sin(y*PI) + 40*sin(y/3*PI))*2/3
        dLat += (160*sin(y/12*PI) + 320*sin(y*PI/30))*2/3
        var dLng = 300 + x + 2*y + .1*x*x + .1*x*y + .1*sqrt(abs(x))
        dLng += (20*sin(6*x*PI) + 20*sin(2*x*PI))*2/3
        dLng += (20*sin(x*PI) + 40*sin(x/3*PI))*2/3
        dLng += (150*sin(x/12*PI) + 300*sin(x/30*PI))*2/3
        val rad = lat/180*PI
        val magic = 1 - .00669342162296594323 * sin(rad).pow(2)
        val root = sqrt(magic)
        dLat = dLat*180/((6378245*(1-.00669342162296594323))/(magic*root)*PI)
        dLng = dLng*180/(6378245/root*cos(rad)*PI)
        return CheckinCoordinate(lat+dLat, lng+dLng, CoordinateSystem.GCJ02)
    }
    fun toWgs84(point: CheckinCoordinate): CheckinCoordinate {
        var result = point.copy(system = CoordinateSystem.WGS84)
        repeat(8) {
            val converted = toGcj02(result)
            val dLat = converted.latitude-point.latitude; val dLng = converted.longitude-point.longitude
            result = result.copy(latitude=result.latitude-dLat,longitude=result.longitude-dLng)
            if (max(abs(dLat), abs(dLng))<1e-9) return result
        }
        return result
    }
}
