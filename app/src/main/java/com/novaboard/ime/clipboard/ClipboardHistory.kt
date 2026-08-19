package com.novaboard.ime.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager as SystemClipboardManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ClipType {
    TEXT,
    IMAGE,
}

data class ClipboardItem(
    val id: Long,
    val type: ClipType,
    val text: String? = null,
    val imageUri: String? = null,
    val pinned: Boolean = false,
)

/**
 * Listens to the system clipboard and keeps a capped, persisted history. Text and images are both
 * supported (an image clip is stored by its content URI, which is what the platform clipboard hands
 * back for images). Pinned items survive the cap trim; everything else is subject to a max size so
 * storage doesn't grow unbounded.
 */
class ClipboardHistoryManager(private val context: Context) {

    companion object {
        private const val PREFS = "novaboard_clipboard"
        private const val KEY_ITEMS = "items"
        private const val MAX_UNPINNED = 40
    }

    private val systemClipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as SystemClipboardManager

    private val items = mutableListOf<ClipboardItem>()
    private var nextId = 1L
    private var listener: (() -> Unit)? = null

    private val clipListener = SystemClipboardManager.OnPrimaryClipChangedListener {
        systemClipboard.primaryClip?.let { addFromClipData(it) }
    }

    fun start() {
        load()
        systemClipboard.addPrimaryClipChangedListener(clipListener)
    }

    fun stop() {
        systemClipboard.removePrimaryClipChangedListener(clipListener)
        save()
    }

    fun setOnChangedListener(l: () -> Unit) {
        listener = l
    }

    fun getItems(): List<ClipboardItem> =
        items.sortedWith(
            compareByDescending<ClipboardItem> { it.pinned }.thenByDescending { it.id }
        )

    private fun addFromClipData(clip: ClipData) {
        if (clip.itemCount == 0) return
        val item = clip.getItemAt(0)
        val desc = clip.description

        val newEntry =
            when {
                desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                    !item.text.isNullOrBlank() ->
                    ClipboardItem(nextId++, ClipType.TEXT, text = item.text.toString())
                item.uri != null && (desc.hasMimeType("image/*")) ->
                    ClipboardItem(nextId++, ClipType.IMAGE, imageUri = item.uri.toString())
                else -> null
            } ?: return

        // avoid duplicate consecutive entries
        if (
            items.firstOrNull()?.let {
                it.text == newEntry.text && it.imageUri == newEntry.imageUri
            } == true
        )
            return

        items.add(0, newEntry)
        trim()
        save()
        listener?.invoke()
    }

    fun togglePin(id: Long) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx == -1) return
        items[idx] = items[idx].copy(pinned = !items[idx].pinned)
        save()
        listener?.invoke()
    }

    fun delete(id: Long) {
        items.removeAll { it.id == id }
        save()
        listener?.invoke()
    }

    private fun trim() {
        val unpinned = items.filter { !it.pinned }
        if (unpinned.size > MAX_UNPINNED) {
            val toDrop = unpinned.drop(MAX_UNPINNED).map { it.id }.toSet()
            items.removeAll { it.id in toDrop }
        }
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type.name)
                    put("text", item.text ?: JSONObject.NULL)
                    put("imageUri", item.imageUri ?: JSONObject.NULL)
                    put("pinned", item.pinned)
                }
            )
        }
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, arr.toString())
            .apply()
    }

    private fun load() {
        val raw =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null)
                ?: return
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return
        items.clear()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getLong("id")
            items.add(
                ClipboardItem(
                    id = id,
                    type = ClipType.valueOf(o.getString("type")),
                    text = o.optString("text").takeIf { o.has("text") && !o.isNull("text") },
                    imageUri =
                        o.optString("imageUri").takeIf {
                            o.has("imageUri") && !o.isNull("imageUri")
                        },
                    pinned = o.optBoolean("pinned", false),
                )
            )
            if (id >= nextId) nextId = id + 1
        }
    }
}
