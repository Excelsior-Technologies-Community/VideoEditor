package com.videoeditor.ui.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.videoeditor.databinding.FragmentGifBinding
import com.videoeditor.lib.overlay.OverlayItem
import com.videoeditor.ui.EditorActivity
import com.videoeditor.ui.EditorViewModel

class GifFragment : Fragment() {

    private var _binding: FragmentGifBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EditorViewModel by activityViewModels()

    private val pickGifLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> 
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                addGifOverlay(uri) 
            }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentGifBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectGif.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/gif"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pickGifLauncher.launch(intent)
        }

        binding.btnDeleteGif.setOnClickListener {
            val canvas = (requireActivity() as EditorActivity).binding.overlayCanvasView
            val selected = canvas.selectedOverlay() ?: return@setOnClickListener
            if (selected is OverlayItem.GifOverlay) {
                canvas.removeOverlay(selected.id)
                viewModel.removeOverlay(selected.id)
            }
        }
    }

    private fun addGifOverlay(uri: Uri) {
        Glide.with(this)
            .asGif()
            .load(uri)
            .into(object : com.bumptech.glide.request.target.CustomTarget<GifDrawable>() {
                override fun onResourceReady(resource: GifDrawable, transition: com.bumptech.glide.request.transition.Transition<in GifDrawable>?) {
                    binding.ivGifPreview.setImageDrawable(resource)
                    resource.start()

                    val bytes = try {
                        requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } catch (e: Exception) { null }

                    if (bytes != null) {
                        val item = OverlayItem.GifOverlay(
                            uri = uri,
                            gifBytes = bytes,
                            width = resource.intrinsicWidth,
                            height = resource.intrinsicHeight
                        )
                        viewModel.addOverlay(item)
                        (requireActivity() as EditorActivity).binding.overlayCanvasView.addOverlay(item)
                    } else {
                        Toast.makeText(context, "Could not read GIF data", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    Toast.makeText(context, "Failed to load GIF", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
