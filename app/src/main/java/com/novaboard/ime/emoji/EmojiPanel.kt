package com.novaboard.ime.emoji

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

/** Standard emoji set grouped loosely by category; tapping one commits it via [onPick]. */
object EmojiData {
    val smileys =
        listOf(
            "\ud83d\ude00",
            "\ud83d\ude03",
            "\ud83d\ude04",
            "\ud83d\ude01",
            "\ud83d\ude06",
            "\ud83d\ude05",
            "\ud83d\ude02",
            "\ud83e\udd23",
            "\ud83d\ude0a",
            "\ud83d\ude07",
            "\ud83d\ude42",
            "\ud83d\ude43",
            "\ud83d\ude09",
            "\ud83d\ude0c",
            "\ud83d\ude0d",
            "\ud83e\udd70",
            "\ud83d\ude18",
            "\ud83d\ude17",
            "\ud83d\ude09",
            "\ud83d\ude1a",
            "\ud83d\ude0b",
            "\ud83d\ude1b",
            "\ud83d\ude1c",
            "\ud83e\udd2a",
        )
    val gestures =
        listOf(
            "\ud83d\udc4d",
            "\ud83d\udc4e",
            "\ud83d\udc4c",
            "\u270c\ufe0f",
            "\ud83e\udd1e",
            "\ud83e\udd1f",
            "\ud83d\udc4f",
            "\ud83d\ude4c",
            "\ud83d\udc4b",
            "\ud83d\udcaa",
            "\ud83d\ude4f",
            "\u270b",
        )
    val objects =
        listOf(
            "\u2764\ufe0f",
            "\ud83d\udd25",
            "\u2b50",
            "\ud83c\udf89",
            "\ud83d\udcaf",
            "\u2705",
            "\u274c",
            "\u2757",
            "\u2753",
            "\ud83d\udca1",
            "\ud83d\udcf1",
            "\u2615",
        )
    val all = smileys + gestures + objects
}

class EmojiPanel(private val context: Context, private val onPick: (String) -> Unit) {
    private var popup: PopupWindow? = null

    fun show(anchor: android.view.View) {
        val scroll = ScrollView(context)
        val grid =
            GridLayout(context).apply {
                columnCount = 8
                setPadding(16, 16, 16, 16)
            }
        EmojiData.all.forEach { emoji ->
            grid.addView(
                TextView(context).apply {
                    text = emoji
                    textSize = 24f
                    setPadding(16, 16, 16, 16)
                    setOnClickListener { onPick(emoji) }
                }
            )
        }
        scroll.addView(grid)
        val pw =
            PopupWindow(
                scroll,
                ViewGroup.LayoutParams.MATCH_PARENT,
                (320 * context.resources.displayMetrics.density).toInt(),
                true,
            )
        pw.showAtLocation(anchor, Gravity.BOTTOM, 0, anchor.height)
        popup = pw
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }
}
