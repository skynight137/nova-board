package com.novaboard.ime.clipboard

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.novaboard.ime.R

class ClipboardAdapter(
    private var items: List<ClipboardItem>,
    private val onClick: (ClipboardItem) -> Unit,
    private val onTogglePin: (ClipboardItem) -> Unit,
) : RecyclerView.Adapter<ClipboardAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.clipText)
        val image: ImageView = view.findViewById(R.id.clipImage)
        val pin: ImageButton = view.findViewById(R.id.btnPin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.clipboard_item, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        if (item.type == ClipType.IMAGE) {
            holder.image.visibility = View.VISIBLE
            holder.text.visibility = View.GONE
            runCatching { holder.image.setImageURI(Uri.parse(item.imageUri)) }
        } else {
            holder.image.visibility = View.GONE
            holder.text.visibility = View.VISIBLE
            holder.text.text = item.text
        }
        holder.pin.alpha = if (item.pinned) 1f else 0.45f
        holder.itemView.setOnClickListener { onClick(item) }
        holder.pin.setOnClickListener { onTogglePin(item) }
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<ClipboardItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun itemAt(position: Int) = items[position]
}
