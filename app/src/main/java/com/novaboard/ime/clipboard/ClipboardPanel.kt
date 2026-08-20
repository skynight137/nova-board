package com.novaboard.ime.clipboard

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
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
) {
    private var popup: PopupWindow? = null
    private lateinit var adapter: ClipboardAdapter
    private lateinit var emptyLabel: TextView

    fun show(anchor: View) {
        dismiss()
        val root = FrameLayout(context)
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
            emptyLabel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        adapter =
            ClipboardAdapter(
                history.getItems(),
                onClick = {
                    onPick(it)
                    dismiss()
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
                        val item = adapter.itemAt(vh.bindingAdapterPosition)
                        history.delete(item.id)
                        refresh()
                    }
                }
            )
            .attachToRecyclerView(recycler)

        refresh()
        history.setOnChangedListener { refresh() }

        val pw =
            PopupWindow(
                root,
                ViewGroup.LayoutParams.MATCH_PARENT,
                (320 * context.resources.displayMetrics.density).toInt(),
                true,
            )
        pw.showAtLocation(anchor, Gravity.BOTTOM, 0, anchor.height)
        popup = pw
    }

    private fun refresh() {
        val list = history.getItems()
        adapter.submit(list)
        emptyLabel.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    fun dismiss() {
        history.removeOnChangedListener()
        popup?.dismiss()
        popup = null
    }
}
