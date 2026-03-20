package com.videoeditor.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.videoeditor.databinding.ItemFilterPresetBinding
import com.videoeditor.lib.filters.FilterPreset

class FilterPresetAdapter(
    private val presets: List<FilterPreset>,
    private val onClick: (FilterPreset) -> Unit
) : RecyclerView.Adapter<FilterPresetAdapter.VH>() {

    private var selectedPreset: FilterPreset = FilterPreset.NONE

    fun setSelected(preset: FilterPreset) {
        val old = presets.indexOf(selectedPreset)
        val new = presets.indexOf(preset)
        selectedPreset = preset
        if (old >= 0) notifyItemChanged(old)
        if (new >= 0) notifyItemChanged(new)
    }

    inner class VH(val binding: ItemFilterPresetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(preset: FilterPreset) {
            binding.tvFilterName.text = preset.label
            binding.root.isSelected   = (preset == selectedPreset)
            binding.root.setOnClickListener { onClick(preset) }

            val color = when (preset) {
                FilterPreset.NONE      -> 0xFFAAAAAA.toInt()
                FilterPreset.GRAYSCALE -> 0xFF555555.toInt()
                FilterPreset.SEPIA     -> 0xFF9B7255.toInt()
                FilterPreset.VIVID     -> 0xFFFF4081.toInt()
                FilterPreset.COOL      -> 0xFF4FC3F7.toInt()
                FilterPreset.WARM      -> 0xFFFF8A65.toInt()
                FilterPreset.FADE      -> 0xFFB0BEC5.toInt()
                FilterPreset.NOIR      -> 0xFF212121.toInt()
            }
            binding.viewSwatch.setBackgroundColor(color)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFilterPresetBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(presets[position])
    override fun getItemCount() = presets.size
}
