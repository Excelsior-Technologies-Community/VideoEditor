package com.videoeditor.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.videoeditor.databinding.ItemTextStyleBinding
import com.videoeditor.lib.overlay.TextStyle

class TextStyleAdapter(
    private val styles: List<TextStyle>,
    private val onStyleSelected: (TextStyle) -> Unit
) : RecyclerView.Adapter<TextStyleAdapter.ViewHolder>() {

    private var selectedPosition = 0

    inner class ViewHolder(val binding: ItemTextStyleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTextStyleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val style = styles[position]
        holder.binding.tvStyleName.text = style.name
        holder.binding.tvStyleName.setTextColor(style.color)
        holder.binding.tvStyleName.typeface = style.typeface
        
        holder.itemView.isSelected = selectedPosition == position
        
        holder.itemView.setOnClickListener {
            val oldPos = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)
            onStyleSelected(style)
        }
    }

    override fun getItemCount() = styles.size

    fun getSelectedStyle() = styles[selectedPosition]
}
