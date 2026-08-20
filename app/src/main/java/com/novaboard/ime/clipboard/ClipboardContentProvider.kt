package com.novaboard.ime.clipboard

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

class ClipboardContentProvider : ContentProvider() {
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = fileFor(uri) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = if (uri.lastPathSegment?.endsWith(".png") == true) {
        "image/png"
    } else {
        "image/*"
    }

    override fun onCreate() = true
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun fileFor(uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return null
        val root = File(requireNotNull(context).filesDir, "clipboard-images")
        val file = File(root, name)
        return file.takeIf { it.canonicalPath.startsWith(root.canonicalPath + File.separator) && it.isFile }
    }
}