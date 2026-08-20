package com.novaboard.ime.clipboard

import org.json.JSONArray
import org.json.JSONObject

data class ClipboardLoadResult(
    val items: List<ClipboardItem>,
    val nextId: Long,
)

internal object ClipboardPersistence {
    fun decode(raw: String): ClipboardLoadResult {
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return ClipboardLoadResult(emptyList(), 1L)
        val items = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)?.let(::decodeItem) ?: continue
                add(item)
            }
        }
        return ClipboardLoadResult(items, items.maxOfOrNull { it.id }?.plus(1) ?: 1L)
    }

    fun encode(items: List<ClipboardItem>): String =
        JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("type", item.type.name)
                        put("text", item.text ?: JSONObject.NULL)
                        put("imageUri", item.imageUri ?: JSONObject.NULL)
                        put("pinned", item.pinned)
                    },
                )
            }
        }.toString()

    private fun decodeItem(entry: JSONObject): ClipboardItem? =
        runCatching {
            val id = entry.optLong("id", Long.MIN_VALUE).takeIf { it > 0 } ?: return null
            val type =
                (entry.opt("type") as? String)?.let { value ->
                    runCatching { ClipType.valueOf(value) }.getOrNull()
                } ?: return null
            ClipboardItem(
                id,
                type,
                entry.optionalString("text"),
                entry.optionalString("imageUri"),
                entry.optBoolean("pinned", false),
            )
        }.getOrNull()

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return opt(key) as? String ?: error("$key must be a string or null")
    }
}