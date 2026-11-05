package cn.edu.xatu.helper

import cn.edu.xatu.helper.model.*
import cn.edu.xatu.helper.platform.CheckinLocationBridge
import org.junit.Assert.*
import org.junit.Test

class CheckinLocationTest {
    @Test fun validatesBoundsFiniteNumbersAndStoredCoordinateSystems() {
        for (point in listOf(CheckinCoordinate(0.0, 0.0), CheckinCoordinate(-90.0, -180.0), CheckinCoordinate(90.0, 180.0))) assertTrue(point.isValid)
        for (point in listOf(CheckinCoordinate(90.01, 0.0), CheckinCoordinate(0.0, -180.01), CheckinCoordinate(Double.NaN, 0.0), CheckinCoordinate(0.0, Double.POSITIVE_INFINITY))) assertFalse(point.isValid)
        for (text in listOf("{}", "invalid", "{\"latitude\":91,\"longitude\":0}")) assertNull(CheckinCoordinate.decode(text))
        assertEquals(CoordinateSystem.GCJ02, CheckinCoordinate.decode("{\"latitude\":34.383,\"longitude\":108.981}")?.system)
        assertEquals(CoordinateSystem.WGS84, CheckinCoordinate.decode("{\"latitude\":34.383,\"longitude\":108.981,\"system\":\"WGS84\"}")?.system)
    }
    @Test fun conversionsMatchIosReferenceAndRoundTripWithinCentimeters() {
        val gps = CheckinCoordinate(39.916527, 116.397128, CoordinateSystem.WGS84)
        val map = LocationCoordinates.toGcj02(gps)
        assertEquals(39.917930749, map.latitude, 1e-8)
        assertEquals(116.403372494, map.longitude, 1e-8)
        val restored = map.gps()
        assertEquals(gps.latitude, restored.latitude, 1e-8)
        assertEquals(gps.longitude, restored.longitude, 1e-8)
        assertEquals(gps, gps.gps())
        val london = CheckinCoordinate(51.5074, -.1278)
        assertEquals(london.latitude, london.gps().latitude, 0.0)
        assertEquals(london.longitude, london.gps().longitude, 0.0)
    }
    @Test fun customLocationOnlyAppliesToExactHttpsCheckinPage() {
        assertTrue(CheckinLocationBridge.mayOverride("https://ehall.xatu.edu.cn/xsfw/sys/swmqdzsapp/index.html#/jrqd"))
        for (url in listOf("http://ehall.xatu.edu.cn/xsfw/sys/swmqdzsapp/", "https://ehall.xatu.edu.cn:8443/xsfw/sys/swmqdzsapp/", "https://ehall.xatu.edu.cn.evil.test/xsfw/sys/swmqdzsapp/", "https://ehall.xatu.edu.cn/xsfw/sys/swmxsqjapp/", "https://ehall.xatu.edu.cn/xsfw/sys/swmqdzsapp/../other/")) assertFalse(url, CheckinLocationBridge.mayOverride(url))
    }
    @Test fun mapProjectionRoundTripsAndHandlesPolesAndDateLine() {
        val point = CheckinCoordinate(34.383, 108.981, CoordinateSystem.WGS84)
        val result = MapCoordinates.coordinate(MapCoordinates.x(point.longitude), MapCoordinates.y(point.latitude))
        assertEquals(point.latitude, result.latitude, 1e-8)
        assertEquals(point.longitude, result.longitude, 1e-8)
        assertTrue(MapCoordinates.y(90.0).isFinite())
        assertTrue(MapCoordinates.y(-90.0).isFinite())
        assertEquals(-180.0, MapCoordinates.coordinate(1.0, .5).longitude, 0.0)
        assertEquals(0.0, MapCoordinates.coordinate(0.5, .5).latitude, 1e-8)
    }
}
