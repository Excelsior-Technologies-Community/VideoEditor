package com.videoeditor.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.videoeditor.databinding.ItemStickerBinding

class StickerAdapter(
    private val emojis: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<StickerAdapter.VH>() {

    inner class VH(val binding: ItemStickerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(emoji: String) {
            binding.tvEmoji.text = emoji
            binding.root.setOnClickListener { onClick(emoji) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStickerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(emojis[position])
    override fun getItemCount() = emojis.size
}
