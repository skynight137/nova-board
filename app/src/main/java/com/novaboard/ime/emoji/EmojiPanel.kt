package com.novaboard.ime.emoji

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.novaboard.ime.R
import com.novaboard.ime.settings.KeyboardPreferences

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
    val symbols = listOf("✅", "❌", "❗", "❓", "💡", "❤️", "🔥", "⭐", "🎉", "💯", "✨", "💦")
    val all = smileys + gestures + objects + symbols

    private val keywords =
        mapOf(
            "😀" to "grinning smile happy",
            "😃" to "smiley smile happy",
            "😄" to "smile happy",
            "😁" to "grin happy",
            "😆" to "laugh happy",
            "😂" to "joy laugh tears",
            "🤣" to "rofl laugh",
            "😊" to "blush smile happy",
            "😍" to "heart eyes love",
            "😘" to "kiss love",
            "👍" to "thumbs up approve",
            "👏" to "clap applause",
            "🙏" to "pray thanks please",
            "❤️" to "heart love red",
            "🔥" to "fire hot lit",
            "⭐" to "star",
            "🎉" to "party celebrate",
            "💯" to "hundred perfect",
            "✨" to "sparkles magic",
            "💡" to "idea light bulb",
            "☕" to "coffee drink",
        )

    fun search(query: String): List<String> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return all
        return all.filter { emoji ->
            emoji.contains(normalized) || keywords[emoji].orEmpty().contains(normalized)
        }
    }
}

class EmojiPanel(private val context: Context, private val onPick: (String) -> Unit) {
    private var container: ViewGroup? = null
    private var panel: View? = null
    private var grid: GridLayout? = null
    fun show(target: ViewGroup) {
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(25, 26, 32))
            }
        root.addView(createHeader())

        val scroll = ScrollView(context)
        grid =
            GridLayout(context).apply {
                columnCount = 7
                setPadding(6, 8, 6, 12)
                clipChildren = false
            }
        scroll.addView(grid)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                )
                .apply { weight = 1f },
        )
        target.removeAllViews()
        target.addView(
            root,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        target.visibility = View.VISIBLE
        container = target
        panel = root
        renderEmojis()
    }

    private fun createHeader(): View =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 12, 0)
            addView(
                TextView(context).apply {
                    text = "ABC"
                    contentDescription = context.getString(R.string.emoji_panel_close)
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setOnClickListener { dismiss() }
                },
                LinearLayout.LayoutParams(48, 52),
            )
            addView(
                TextView(context).apply {
                    text = "☺  Emoji"
                    textSize = 18f
                    setTextColor(Color.WHITE)
                },
                LinearLayout.LayoutParams(0, 52, 1f),
            )
        }

    private fun renderEmojis() {
        val target = grid ?: return
        target.removeAllViews()
        val emojiTypeface =
            if (KeyboardPreferences.getEmojiFont(context) == "google") {
                Typeface.create("sans-serif-emoji", Typeface.NORMAL)
            } else {
                Typeface.DEFAULT
            }
        EmojiData.all.forEach { emoji ->
            target.addView(
                TextView(context).apply {
                    text = emoji
                    contentDescription = context.getString(R.string.emoji_item, emoji)
                    typeface = emojiTypeface
                    textSize = 32f
                    gravity = Gravity.CENTER
                     includeFontPadding = false
                    setPadding(4, 4, 4, 4)
                    layoutParams =
                        GridLayout.LayoutParams().apply {
                            width = 0
                            height = 64
                            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        }
                    setOnClickListener { onPick(emoji) }
                }
            )
        }
    }

    fun dismiss() {
        panel?.let { container?.removeView(it) }
        container?.visibility = View.GONE
        panel = null
        container = null
    }
}
