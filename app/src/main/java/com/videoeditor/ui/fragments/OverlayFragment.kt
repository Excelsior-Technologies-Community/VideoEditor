package com.videoeditor.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.videoeditor.databinding.FragmentOverlayBinding
import com.videoeditor.lib.overlay.OverlayItem
import com.videoeditor.lib.overlay.TextStyle
import com.videoeditor.ui.EditorActivity
import com.videoeditor.ui.EditorViewModel
import com.videoeditor.ui.adapters.TextStyleAdapter

class OverlayFragment : Fragment() {

    private var _binding: FragmentOverlayBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EditorViewModel by activityViewModels()
    private var styleAdapter: TextStyleAdapter? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentOverlayBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.textStyles.observe(viewLifecycleOwner) { styles ->
            styleAdapter = TextStyleAdapter(styles) { selectedStyle ->
                // Optionally update preview if an item is selected
            }
            binding.rvTextStyles.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = styleAdapter
            }
        }

        binding.btnAddText.setOnClickListener {
            val text = binding.etTextInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val selectedStyle = styleAdapter?.getSelectedStyle() ?: TextStyle("Default")

            val item = OverlayItem.TextOverlay(
                text      = text,
                textColor = selectedStyle.color,
                fontSize  = binding.seekFontSize.progress.toFloat().coerceAtLeast(24f),
                typeface  = selectedStyle.typeface,
                bold      = selectedStyle.isBold,
                hasShadow = selectedStyle.hasShadow
            )

            viewModel.addOverlay(item)
            (requireActivity() as EditorActivity)
                .binding.overlayCanvasView.addOverlay(item)

            binding.etTextInput.text?.clear()
        }

        binding.btnDeleteSelected.setOnClickListener {
            val canvas = (requireActivity() as EditorActivity).binding.overlayCanvasView
            val selected = canvas.selectedOverlay() ?: return@setOnClickListener
            canvas.removeOverlay(selected.id)
            viewModel.removeOverlay(selected.id)
        }

        binding.seekFontSize.max = 120
        binding.seekFontSize.progress = 48
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
