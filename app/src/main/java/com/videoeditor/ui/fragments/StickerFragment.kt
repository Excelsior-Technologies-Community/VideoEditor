package com.videoeditor.ui.fragments

import android.graphics.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.videoeditor.databinding.FragmentStickerBinding
import com.videoeditor.lib.overlay.OverlayItem
import com.videoeditor.ui.EditorActivity
import com.videoeditor.ui.EditorViewModel
import com.videoeditor.ui.adapters.StickerAdapter

class StickerFragment : Fragment() {

    private var _binding: FragmentStickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EditorViewModel by activityViewModels()

    private val emojiList = listOf(
        "❤️", "😂", "🔥", "⭐", "🎉", "👍", "💯", "✨",
        "🌈", "🎵", "💫", "🏆", "🎯", "💥", "🌟", "😎"
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentStickerBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = StickerAdapter(emojiList) { emoji ->
            val bitmap = emojiBitmap(emoji, 160)
            val item = OverlayItem.StickerOverlay(bitmap = bitmap)
            viewModel.addOverlay(item)
            (requireActivity() as EditorActivity)
                .binding.overlayCanvasView.addOverlay(item)
        }

        binding.rvStickers.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            this.adapter = adapter
        }

        binding.btnDeleteSticker.setOnClickListener {
            val canvas = (requireActivity() as EditorActivity).binding.overlayCanvasView
            val selected = canvas.selectedOverlay() ?: return@setOnClickListener
            if (selected is OverlayItem.StickerOverlay) {
                canvas.removeOverlay(selected.id)
                viewModel.removeOverlay(selected.id)
            }
        }
    }

    private fun emojiBitmap(emoji: String, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx * 0.75f
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        paint.getTextBounds(emoji, 0, emoji.length, bounds)
        canvas.drawText(emoji, sizePx / 2f, sizePx / 2f - bounds.exactCenterY(), paint)
        return bmp
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
