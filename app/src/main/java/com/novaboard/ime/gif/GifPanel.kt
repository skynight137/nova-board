package com.novaboard.ime.gif

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import com.novaboard.ime.R
import com.novaboard.ime.model.Key
import com.novaboard.ime.model.KeyType
import com.novaboard.ime.model.KeyboardLayouts
import com.novaboard.ime.settings.KeyboardPreferences
import com.novaboard.ime.view.KeyboardView

class GifPanel(
    private val context: android.content.Context,
    private val client: GifClient = GifClient(),
    private val onPick: (GifItem) -> Unit,
    private val onClose: () -> Unit,
) {
    private var executor: ExecutorService? = null
    private val requestId = AtomicInteger()
    private var target: ViewGroup? = null
    private var root: View? = null
    private lateinit var grid: GridLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var searchKeyboard: KeyboardView

    fun show(target: ViewGroup) {
        dismiss()
        executor = Executors.newFixedThreadPool(3)
        this.target = target
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(context.getColor(R.color.kb_background))
        }
        panel.addView(header())
        status = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(context.getColor(R.color.kb_key_text))
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        panel.addView(status, LinearLayout.LayoutParams(-1, dp(52)))
        val scroll = android.widget.ScrollView(context)
        grid = GridLayout(context).apply {
            columnCount = 3
            useDefaultMargins = false
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        scroll.addView(grid)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        searchKeyboard =
            KeyboardView(context).apply {
                listener =
                    object : KeyboardView.OnKeyListener {
                        override fun onKey(key: Key, outputChar: String) {
                            when (key.type) {
                                KeyType.CHAR,
                                KeyType.COMMA,
                                KeyType.PERIOD,
                                KeyType.SPACE -> search.append(outputChar)
                                KeyType.BACKSPACE -> removePreviousCharacter()
                                else -> Unit
                            }
                        }

                        override fun onBackspace() = removePreviousCharacter()

                        override fun onEnter() = load(search.text.toString())

                        override fun onShiftToggled(shiftOn: Boolean, capsLock: Boolean) {
                            searchKeyboard.setShiftState(shiftOn, capsLock)
                        }

                        override fun onSwitchToSymbols() {
                            searchKeyboard.setPage(
                                KeyboardLayouts.symbols(
                                    KeyboardPreferences.getBoolean(
                                        context,
                                        KeyboardPreferences.SHOW_NUMBER_ROW,
                                    ),
                                ),
                            )
                        }

                        override fun onSwitchToLetters() {
                            searchKeyboard.setPage(
                                if (
                                    KeyboardPreferences.getBoolean(
                                        context,
                                        KeyboardPreferences.SHOW_NUMBER_ROW,
                                    )
                                ) {
                                    KeyboardLayouts.letters
                                } else {
                                    KeyboardLayouts.lettersWithoutNumberRow
                                },
                            )
                        }

                        override fun onEmoji() = Unit

                        override fun onCursorMove(direction: Int) = Unit

                        override fun onQuickDelete() {
                            search.setText("")
                        }

                        override fun onGestureWord(path: String) {
                            search.append(path)
                        }
                    }
                applyPreferences()
                setPage(
                    if (
                        KeyboardPreferences.getBoolean(
                            context,
                            KeyboardPreferences.SHOW_NUMBER_ROW,
                        )
                    ) {
                        KeyboardLayouts.letters
                    } else {
                        KeyboardLayouts.lettersWithoutNumberRow
                    },
                )
            }
        panel.addView(searchKeyboard, LinearLayout.LayoutParams(-1, -2))
        target.removeAllViews()
        target.addView(panel, -1, -1)
        target.visibility = View.VISIBLE
        root = panel
        load("")
    }

    private fun removePreviousCharacter() {
        val end = search.selectionEnd.coerceAtLeast(0)
        if (end > 0) search.text.delete(end - 1, end)
    }

    private fun header(): View = FrameLayout(context).apply {
        setBackgroundColor(context.getColor(R.color.kb_background))
        val close = ImageButton(context).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(context.getColor(R.color.kb_toolbar_icon))
            background = ColorDrawable(Color.TRANSPARENT)
            contentDescription = context.getString(R.string.gif_close)
            setOnClickListener { onClose() }
        }
        addView(close, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.START))
        val title = TextView(context).apply {
            text = context.getString(R.string.gif_title)
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(context.getColor(R.color.kb_key_text))
        }
        addView(title, FrameLayout.LayoutParams(-1, dp(56)))
        search = EditText(context).apply {
            hint = context.getString(R.string.gif_search_hint)
            setSingleLine(true)
            showSoftInputOnFocus = false
            setPadding(dp(52), 0, dp(52), 0)
            contentDescription = context.getString(R.string.gif_search_hint)
            setOnEditorActionListener { _, _, _ ->
                load(text.toString())
                true
            }
        }
        addView(search, FrameLayout.LayoutParams(-1, dp(56)))
        title.visibility = View.GONE
        close.bringToFront()
    }

    private fun load(query: String) {
        val id = requestId.incrementAndGet()
        status.text = context.getString(R.string.gif_loading)
        status.visibility = View.VISIBLE
        grid.removeAllViews()
        submit {
            try {
                val results = client.load(query)
                postIfCurrent(id) {
                    status.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                    if (results.isEmpty()) status.text = context.getString(R.string.gif_empty)
                    results.forEach(::addGif)
                }
            } catch (error: GifClientException.NotConfigured) {
                postIfCurrent(id) {
                    status.text = context.getString(R.string.gif_not_configured)
                }
            } catch (_: Exception) {
                postIfCurrent(id) {
                    status.text = context.getString(R.string.gif_error)
                }
            }
        }
    }

    private fun addGif(item: GifItem) {
        val image = ImageView(context).apply {
            contentDescription = item.title
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(context.getColor(R.color.kb_surface))
            setOnClickListener { onPick(item) }
        }
        grid.addView(image, GridLayout.LayoutParams().apply {
            width = 0
            height = dp(116)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(2), dp(2), dp(2), dp(2))
        })
        submit {
            try {
                val bitmap = URL(item.previewUrl).openStream().use(BitmapFactory::decodeStream)
                if (bitmap != null) image.post { if (image.parent != null) image.setImageBitmap(bitmap) }
            } catch (_: Exception) {
                // Keep the neutral tile when a single remote preview is unavailable.
            }
        }
    }

    private fun postIfCurrent(id: Int, action: () -> Unit) {
        root?.post { if (requestId.get() == id && root?.parent != null) action() }
    }

    private fun submit(task: () -> Unit) {
        runCatching { executor?.execute(task) }
    }

    fun dismiss() {
        requestId.incrementAndGet()
        executor?.shutdownNow()
        executor = null
        root?.let { (it.parent as? ViewGroup)?.removeView(it) }
        target?.let { if (it.childCount == 0) it.visibility = View.GONE }
        root = null
        target = null
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
