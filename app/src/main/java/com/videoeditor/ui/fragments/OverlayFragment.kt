package com.videoeditor.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.videoeditor.databinding.FragmentOverlayBinding
import com.videoeditor.lib.overlay.OverlayItem
import com.videoeditor.ui.EditorActivity
import com.videoeditor.ui.EditorViewModel

class OverlayFragment : Fragment() {

    private var _binding: FragmentOverlayBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EditorViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentOverlayBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddText.setOnClickListener {
            val text = binding.etTextInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val colorInt = when (binding.rgTextColor.checkedRadioButtonId) {
                binding.rbWhite.id  -> Color.WHITE
                binding.rbBlack.id  -> Color.BLACK
                binding.rbYellow.id -> Color.YELLOW
                binding.rbRed.id    -> Color.RED
                else -> Color.WHITE
            }

            val item = OverlayItem.TextOverlay(
                text      = text,
                textColor = colorInt,
                fontSize  = binding.seekFontSize.progress.toFloat().coerceAtLeast(24f),
                bold      = binding.cbBold.isChecked,
                hasShadow = binding.cbShadow.isChecked
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
