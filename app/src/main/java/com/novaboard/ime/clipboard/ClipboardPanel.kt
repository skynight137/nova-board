package com.novaboard.ime.clipboard

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
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

    fun show(target: ViewGroup) {
        dismiss()
        val root = FrameLayout(context)
        val backButton =
            TextView(context).apply {
                text = "‹  Keyboard"
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(context.getColor(R.color.kb_key_text))
                setPadding(16, 0, 16, 0)
                contentDescription = context.getString(R.string.clipboard_back_to_keyboard)
                setOnClickListener { onClose() }
            }
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
        root.addView(
            backButton,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                48,
            ),
        )
        (recycler.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = 48
        }.also { recycler.layoutParams = it }
        root.addView(
            emptyLabel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        (emptyLabel.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = 48
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
        val list = history.getItems()
        adapter.submit(list)
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
}
