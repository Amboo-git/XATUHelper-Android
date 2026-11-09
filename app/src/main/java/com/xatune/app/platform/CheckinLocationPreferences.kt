package com.xatune.app.platform

import android.content.Context
import com.xatune.app.model.CheckinCoordinate
import com.google.gson.Gson

data class CheckinLocationSettings(val enabled: Boolean = false, val coordinate: CheckinCoordinate? = null, val revealed: Boolean = false)

class CheckinLocationPreferences(context: Context) {
    internal val preferences = context.getSharedPreferences("checkin_location", Context.MODE_PRIVATE)
    fun read() = CheckinLocationSettings(preferences.getBoolean("enabled", false), CheckinCoordinate.decode(preferences.getString("coordinate", null)), preferences.getBoolean("revealed", false))
    fun reveal() { preferences.edit().putBoolean("revealed", true).apply() }
    fun enable(value: Boolean) { if (!value || read().coordinate != null) preferences.edit().putBoolean("enabled", value).apply() }
    fun save(coordinate: CheckinCoordinate) { require(coordinate.isValid); preferences.edit().putString("coordinate", Gson().toJson(coordinate)).apply() }
}
