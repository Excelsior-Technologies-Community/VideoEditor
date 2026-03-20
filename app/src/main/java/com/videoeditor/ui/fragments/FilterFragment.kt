package com.videoeditor.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.videoeditor.databinding.FragmentFilterBinding
import com.videoeditor.lib.filters.FilterPreset
import com.videoeditor.ui.EditorViewModel
import com.videoeditor.ui.adapters.FilterPresetAdapter

class FilterFragment : Fragment() {

    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EditorViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentFilterBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = FilterPresetAdapter(FilterPreset.values().toList()) { preset ->
            viewModel.applyPreset(preset)
        }
        binding.rvPresets.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            this.adapter = adapter
        }

        viewModel.activePreset.observe(viewLifecycleOwner) { adapter.setSelected(it) }

        setupSlider(binding.seekBrightness, 100) { v ->
            viewModel.updateBrightness((v - 100) / 100f)
        }
        setupSlider(binding.seekContrast, 100) { v ->
            viewModel.updateContrast(v / 100f)
        }
        setupSlider(binding.seekSaturation, 100) { v ->
            viewModel.updateSaturation(v / 100f)
        }

        viewModel.filterParams.value?.let { p ->
            binding.seekBrightness.progress = ((p.brightness * 100) + 100).toInt()
            binding.seekContrast.progress   = (p.contrast * 100).toInt()
            binding.seekSaturation.progress = (p.saturation * 100).toInt()
        }
    }

    private fun setupSlider(seekBar: SeekBar, defaultVal: Int, onChange: (Int) -> Unit) {
        seekBar.max      = 200
        seekBar.progress = defaultVal
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
