package com.xatune.app.model

import kotlin.math.*

internal object MapCoordinates {
    fun x(longitude: Double) = (longitude + 180) / 360
    fun y(latitude: Double): Double {
        val angle = latitude.coerceIn(-85.05112878, 85.05112878) * PI / 180
        return (1 - ln(tan(angle) + 1 / cos(angle)) / PI) / 2
    }
    fun coordinate(x: Double, y: Double): CheckinCoordinate {
        val wrapped = x - floor(x)
        return CheckinCoordinate(atan(sinh(PI * (1 - 2 * y.coerceIn(0.0, 1.0)))) * 180 / PI, wrapped * 360 - 180, CoordinateSystem.WGS84)
    }
}
