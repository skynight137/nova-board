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
        val search =
            EditText(context).apply {
                hint = context.getString(R.string.clipboard_search_hint)
                setSingleLine(true)
                setText(searchQuery)
                setPadding(dp(12), 0, dp(12), 0)
                contentDescription = context.getString(R.string.clipboard_search_hint)
            }
        header.addView(
            backButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)),
        )
        header.addView(
            search,
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
        search.addTextChangedListener(
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
