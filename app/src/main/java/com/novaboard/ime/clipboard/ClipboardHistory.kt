package com.novaboard.ime.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager as SystemClipboardManager
import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
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
 * supported. Image content is copied into app-private storage and exposed through
 * [ClipboardContentProvider], so provider grants do not need to survive a restart.
 */
class ClipboardHistoryManager(private val context: Context) {

    companion object {
        private const val PREFS = "novaboard_clipboard"
        private const val KEY_ITEMS = "items"
        private const val MAX_UNPINNED = 40
        private const val IMAGE_DIR = "clipboard-images"
        private const val OWN_AUTHORITY = "com.novaboard.ime.clipboard"
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
        systemClipboard.primaryClip?.let { addFromClipData(it) }
    }

    fun stop() {
        systemClipboard.removePrimaryClipChangedListener(clipListener)
        save()
    }

    fun setOnChangedListener(l: () -> Unit) {
        listener = l
    }

    fun removeOnChangedListener() {
        listener = null
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
                !item.text.isNullOrBlank() ->
                    ClipboardItem(nextId++, ClipType.TEXT, text = item.text.toString())
                item.uri != null && desc.hasMimeType("image/*") ->
                    copyImage(item.uri, desc.getMimeType(0) ?: "image/*")?.let { uri ->
                        ClipboardItem(nextId++, ClipType.IMAGE, imageUri = uri.toString())
                    }
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
        items.filter { it.id == id }.forEach(::deleteImageFile)
        items.removeAll { it.id == id }
        save()
        listener?.invoke()
    }

    private fun trim() {
        val unpinned = items.filter { !it.pinned }
        if (unpinned.size > MAX_UNPINNED) {
            val toDrop = unpinned.drop(MAX_UNPINNED).map { it.id }.toSet()
            items.filter { it.id in toDrop }.forEach(::deleteImageFile)
            items.removeAll { it.id in toDrop }
        }
    }

    private fun copyImage(source: Uri, mimeType: String): Uri? {
        val extension = if (mimeType.endsWith("png")) ".png" else ".bin"
        val directory = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}$extension")
        return runCatching {
                context.contentResolver.openInputStream(source).use { input ->
                    checkNotNull(input) { "Image source is unreadable" }
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                Uri.parse(
                    "content://$OWN_AUTHORITY/${file.name}",
                )
            }
            .getOrElse {
                file.delete()
                null
            }
    }

    private fun deleteImageFile(item: ClipboardItem) {
        val uri = item.imageUri?.let(Uri::parse) ?: return
        if (uri.authority == OWN_AUTHORITY) {
            File(File(context.filesDir, IMAGE_DIR), uri.lastPathSegment.orEmpty()).delete()
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
            runCatching {
                val o = arr.getJSONObject(i)
                val id = o.getLong("id")
                val type = ClipType.valueOf(o.getString("type"))
                ClipboardItem(
                    id = id,
                    type = type,
                    text = o.optString("text").takeIf { o.has("text") && !o.isNull("text") },
                    imageUri = o.optString("imageUri").takeIf {
                        o.has("imageUri") && !o.isNull("imageUri")
                    },
                    pinned = o.optBoolean("pinned", false),
                )
            }.onSuccess { item ->
                val migrated =
                    if (item.type == ClipType.IMAGE) migrateImage(item) else item
                if (migrated != null) {
                    items.add(migrated)
                    if (migrated.id >= nextId) nextId = migrated.id + 1
                }
            }
        }
        trim()
        cleanupOrphanImages()
        save()
    }

    private fun migrateImage(item: ClipboardItem): ClipboardItem? {
        val rawUri = item.imageUri ?: return null
        val uri = Uri.parse(rawUri)
        return if (uri.authority == OWN_AUTHORITY) {
            item.takeIf {
                File(File(context.filesDir, IMAGE_DIR), uri.lastPathSegment.orEmpty()).isFile
            }
        } else {
            copyImage(uri, "image/*")?.let { item.copy(imageUri = it.toString()) }
        }
    }

    private fun cleanupOrphanImages() {
        val retained = items.mapNotNull { it.imageUri?.let(Uri::parse)?.lastPathSegment }.toSet()
        File(context.filesDir, IMAGE_DIR).listFiles()?.forEach { file ->
            if (file.name !in retained) file.delete()
        }
    }
}
