package com.samielmadani.orbit.model

import org.json.JSONArray
import org.json.JSONObject

data class ManifestItem(
    val name: String,
    val sizeBytes: Long,
    val relativePath: String,
    val mimeType: String,
    val isText: Boolean = false,
    val textContent: String? = null
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("name", name)
        put("sizeBytes", sizeBytes)
        put("relativePath", relativePath)
        put("mimeType", mimeType)
        put("isText", isText)
        put("textContent", textContent ?: JSONObject.NULL)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): ManifestItem = ManifestItem(
            name = json.getString("name"),
            sizeBytes = json.getLong("sizeBytes"),
            relativePath = json.optString("relativePath", json.getString("name")),
            mimeType = json.optString("mimeType", "*/*"),
            isText = json.optBoolean("isText", false),
            textContent = if (json.isNull("textContent")) null else json.getString("textContent")
        )
    }
}

data class ManifestPayload(
    val batchId: String,
    val senderDeviceName: String,
    val totalFiles: Int,
    val totalBytes: Long,
    val items: List<ManifestItem>
) {
    fun toJsonString(): String = JSONObject().apply {
        put("batchId", batchId)
        put("senderDeviceName", senderDeviceName)
        put("totalFiles", totalFiles)
        put("totalBytes", totalBytes)
        val array = JSONArray()
        items.forEach { array.put(it.toJsonObject()) }
        put("items", array)
    }.toString()

    companion object {
        fun fromJsonString(jsonStr: String): ManifestPayload {
            val json = JSONObject(jsonStr)
            val itemsArray = json.getJSONArray("items")
            val itemsList = mutableListOf<ManifestItem>()
            for (i in 0 until itemsArray.length()) {
                itemsList.add(ManifestItem.fromJsonObject(itemsArray.getJSONObject(i)))
            }
            return ManifestPayload(
                batchId = json.getString("batchId"),
                senderDeviceName = json.getString("senderDeviceName"),
                totalFiles = json.getInt("totalFiles"),
                totalBytes = json.getLong("totalBytes"),
                items = itemsList
            )
        }
    }
}

