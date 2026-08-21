package com.novaboard.ime.clipboard

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.novaboard.ime.R
import com.novaboard.ime.model.Key
import com.novaboard.ime.model.KeyType
import com.novaboard.ime.model.KeyboardLayouts
import com.novaboard.ime.settings.KeyboardPreferences
import com.novaboard.ime.view.KeyboardView

/**
 * Shows clipboard history as an overlay above the keyboard (rather than a separate Activity, since
 * IME windows can't easily launch activities on top of themselves). Tapping an item pastes it via
 * [onPick]; the pin button and swipe-to-delete match the reference screenshot.
 */
class ClipboardPanel(
    private val context: Context,
    private val history: ClipboardHistoryManager,
    private val onPick: (ClipboardItem) -> Unit,
    private val onClose: () -> Unit,
) {
    private var panel: View? = null
    private var target: ViewGroup? = null
    private lateinit var adapter: ClipboardAdapter
    private lateinit var emptyLabel: TextView
    private lateinit var searchField: EditText
    private lateinit var searchKeyboard: KeyboardView
    private var searchQuery = ""

    fun show(target: ViewGroup) {
        dismiss()
        val root = FrameLayout(context)
        val header =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val backButton =
            TextView(context).apply {
                text = "‹  Keyboard"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.kb_key_text))
                minHeight = dp(56)
                setPadding(dp(16), 0, dp(16), 0)
                contentDescription = context.getString(R.string.clipboard_back_to_keyboard)
                setOnClickListener { onClose() }
            }
        searchField =
            EditText(context).apply {
                hint = context.getString(R.string.clipboard_search_hint)
                setSingleLine(true)
                setText(searchQuery)
                setPadding(dp(12), 0, dp(12), 0)
                contentDescription = context.getString(R.string.clipboard_search_hint)
                isFocusable = true
                isFocusableInTouchMode = true
                showSoftInputOnFocus = false
            }
        searchKeyboard =
            KeyboardView(context).apply {
                listener =
                    object : KeyboardView.OnKeyListener {
                        override fun onKey(key: Key, outputChar: String) {
                            handleKey(key, outputChar)
                        }

                        override fun onBackspace() {
                            handleBackspace()
                        }

                        override fun onEnter() = Unit

                        override fun onShiftToggled(shiftOn: Boolean, capsLock: Boolean) {
                            setShiftState(shiftOn, capsLock)
                        }

                        override fun onSwitchToSymbols() {
                            setPage(
                                KeyboardLayouts.symbols(
                                    KeyboardPreferences.getBoolean(
                                        context,
                                        KeyboardPreferences.SHOW_NUMBER_ROW,
                                    ),
                                ),
                            )
                        }

                        override fun onSwitchToLetters() {
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

                        override fun onEmoji() = Unit

                        override fun onCursorMove(direction: Int) = Unit

                        override fun onQuickDelete() {
                            searchField.setText("")
                        }

                        override fun onGestureWord(path: String) {
                            handleKey(Key(KeyType.CHAR, path), path)
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
        header.addView(
            backButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)),
        )
        header.addView(
            searchField,
            LinearLayout.LayoutParams(0, dp(56), 1f),
        )
        val recycler =
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
            }
        emptyLabel =
            TextView(context).apply {
                 text = context.getString(R.string.clipboard_empty)
                gravity = Gravity.CENTER
                setPadding(24, 48, 24, 48)
            }
        root.addView(
            recycler,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(header, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        (recycler.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = dp(56)
        }.also { recycler.layoutParams = it }
        root.addView(
            emptyLabel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        (emptyLabel.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = dp(56)
        }.also { emptyLabel.layoutParams = it }
        root.addView(
            searchKeyboard,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        val keyboardRows =
            if (
                KeyboardPreferences.getBoolean(
                    context,
                    KeyboardPreferences.SHOW_NUMBER_ROW,
                )
            ) {
                5
            } else {
                4
            }
        val keyboardHeight = dp(keyboardRows * 58)
        (recycler.layoutParams as FrameLayout.LayoutParams).apply {
            bottomMargin = keyboardHeight
        }.also { recycler.layoutParams = it }
        (emptyLabel.layoutParams as FrameLayout.LayoutParams).apply {
            bottomMargin = keyboardHeight
        }.also { emptyLabel.layoutParams = it }

        adapter =
            ClipboardAdapter(
                history.getItems(),
                onClick = {
                    onPick(it)
                },
                onTogglePin = {
                    history.togglePin(it.id)
                    refresh()
                },
            )
        recycler.adapter = adapter
        searchField.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString().orEmpty()
                    refresh()
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )

        ItemTouchHelper(
                object :
                    ItemTouchHelper.SimpleCallback(
                        0,
                        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                    ) {
                    override fun onMove(
                        rv: RecyclerView,
                        vh: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder,
                    ) = false

                    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                        val item = adapter.itemAt(vh.bindingAdapterPosition) ?: return
                        history.delete(item.id)
                        refresh()
                    }
                }
            )
            .attachToRecyclerView(recycler)

        refresh()
        history.setOnChangedListener { refresh() }

        target.removeAllViews()
        target.addView(
            root,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        target.visibility = View.VISIBLE
        panel = root
        this.target = target
        focusSearchField()
    }

    fun handleKey(key: Key, outputChar: String): Boolean {
        if (!searchField.hasFocus()) {
            focusSearchField()
        }
        when (key.type) {
            KeyType.CHAR, KeyType.COMMA, KeyType.PERIOD, KeyType.SPACE -> searchField.append(outputChar)
            KeyType.BACKSPACE -> {
                val start = searchField.selectionStart
                val end = searchField.selectionEnd
                if (start != end) {
                    searchField.text.delete(minOf(start, end), maxOf(start, end))
                } else if (start > 0) {
                    searchField.text.delete(start - 1, start)
                }
            }
            else -> return false
        }
        return true
    }

    fun handleBackspace(): Boolean = handleKey(Key(KeyType.BACKSPACE, ""), "")

    private fun focusSearchField() {
        searchField.requestFocus()
        searchField.setSelection(searchField.length())
    }

    private fun refresh() {
        val list = filterClipboardItems(history.getItems(), searchQuery)
        adapter.submit(list)
        emptyLabel.text =
            if (searchQuery.trim().isEmpty()) {
                context.getString(R.string.clipboard_empty)
            } else {
                context.getString(R.string.clipboard_no_matches)
            }
        emptyLabel.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    fun dismiss() {
        history.removeOnChangedListener()
        panel?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        panel = null
        if (target?.childCount == 0) {
            target?.visibility = View.GONE
        }
        target = null
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
