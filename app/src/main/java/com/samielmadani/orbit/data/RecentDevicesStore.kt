package com.samielmadani.orbit.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class RecentDevice(
    val name: String,
    val lastEndpointId: String,
    val timestamp: Long
)

class RecentDevicesStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("orbit_recent_devices", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_RECENTS = "recents_json"
        private const val MAX_RECENTS = 5
    }

    fun getRecentDevices(): List<RecentDevice> {
        val jsonStr = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<RecentDevice>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RecentDevice(
                        name = obj.getString("name"),
                        lastEndpointId = obj.optString("endpointId", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun recordDevice(name: String, endpointId: String) {
        val current = getRecentDevices().toMutableList()
        current.removeAll { it.name.equals(name, ignoreCase = true) }
        current.add(0, RecentDevice(name = name, lastEndpointId = endpointId, timestamp = System.currentTimeMillis()))

        val trimmed = current.take(MAX_RECENTS)
        val array = JSONArray()
        trimmed.forEach {
            array.put(JSONObject().apply {
                put("name", it.name)
                put("endpointId", it.lastEndpointId)
                put("timestamp", it.timestamp)
            })
        }
        prefs.edit().putString(KEY_RECENTS, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_RECENTS).apply()
    }
}

