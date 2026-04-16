package com.videoeditor.ui

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Surface
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.videoeditor.R
import com.videoeditor.databinding.ActivityEditorBinding
import com.videoeditor.lib.engine.VideoPlayer
import com.videoeditor.lib.engine.VideoExporter
import com.videoeditor.ui.fragments.FilterFragment
import com.videoeditor.ui.fragments.OverlayFragment
import com.videoeditor.ui.fragments.GifFragment
import java.io.File
import java.io.FileInputStream

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
    }

    lateinit var binding: ActivityEditorBinding
    val viewModel: EditorViewModel by viewModels()
    private var player: VideoPlayer? = null
    private var playerSurface: Surface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriString = intent.getStringExtra(EXTRA_VIDEO_URI) ?: run { finish(); return }
        val uri = Uri.parse(uriString)
        
        viewModel.videoUri.value = uri

        val vertSrc = resources.openRawResource(R.raw.vertex_shader).bufferedReader().readText().trim('\uFEFF', '\u200B')
        val fragSrc = resources.openRawResource(R.raw.fragment_shader).bufferedReader().readText().trim('\uFEFF', '\u200B')
        binding.glSurfaceView.setShaders(vertSrc, fragSrc)

        player = VideoPlayer(this)

        binding.glSurfaceView.onSurfaceReady = { surface ->
            playerSurface = surface
            player?.prepare(uri, surface)
        }

        viewModel.filterParams.observe(this) { params ->
            val libParams = com.videoeditor.lib.filters.FilterParams(
                brightness = params.brightness,
                contrast = params.contrast,
                saturation = params.saturation,
                filterType = params.filterType
            )
            binding.glSurfaceView.filterParams = libParams
            binding.glSurfaceView.requestRender()
        }

        setupPlaybackControls()
        setupBottomNav()
        setupExport()

        var exportDialog: android.app.AlertDialog? = null

        viewModel.exportProgress.observe(this) { pct ->
            binding.exportProgressBar.progress = pct
            binding.exportProgressBar.visibility = android.view.View.VISIBLE
            if (exportDialog == null) {
                exportDialog = android.app.AlertDialog.Builder(this)
                    .setTitle("Exporting Video")
                    .setMessage("Please wait... $pct%")
                    .setCancelable(false)
                    .show()
            } else {
                exportDialog?.setMessage("Please wait... $pct%")
            }
        }
        viewModel.exportResult.observe(this) { result ->
            binding.exportProgressBar.visibility = android.view.View.GONE
            exportDialog?.dismiss()
            exportDialog = null
            when (result) {
                is VideoExporter.ExportResult.Success -> {
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        scanFileToGallery(result.file)
                    }
                    Toast.makeText(this, "Video saved to Gallery", Toast.LENGTH_LONG).show()
                }
                is VideoExporter.ExportResult.Error ->
                    Toast.makeText(this, "Export failed: ${result.message}", Toast.LENGTH_LONG).show()
                else -> {}
            }
        }
    }

    private fun scanFileToGallery(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VidEdit")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = contentResolver.insert(collection, values)

            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(it, values, null, null)
            }
        } else {
            // Older versions
            android.media.MediaScannerConnection.scanFile(
                this, arrayOf(file.absolutePath), arrayOf("video/mp4"), null
            )
        }
    }

    private fun setupPlaybackControls() {
        player?.onPrepared = {
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
            player?.play()
        }
        player?.onCompletion = {
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
        }
        player?.onProgressUpdate = { cur, dur ->
            runOnUiThread {
                binding.seekBar.max  = dur.toInt()
                binding.seekBar.progress = cur.toInt()
                binding.tvTimecode.text = "${formatMs(cur)} / ${formatMs(dur)}"
            }
        }

        binding.btnPlayPause.setOnClickListener {
            if (player?.isPlaying == true) {
                player?.pause()
                binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            } else {
                player?.play()
                binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(p.toLong())
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
        })
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_filter  -> FilterFragment()
                R.id.nav_text    -> OverlayFragment()
                R.id.nav_gif     -> GifFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }
        binding.bottomNav.selectedItemId = R.id.nav_filter
    }

    private fun setupExport() {
        binding.btnExport.setOnClickListener {
            val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: filesDir
            val w = if (binding.overlayCanvasView.width > 0) binding.overlayCanvasView.width else 1080
            val h = if (binding.overlayCanvasView.height > 0) binding.overlayCanvasView.height else 1920
            viewModel.startExport(dir, w, h)
        }
    }

    private fun formatMs(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
