package com.videoeditor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.videoeditor.R
import com.videoeditor.lib.engine.VideoExporter
import com.videoeditor.lib.filters.FilterParams
import com.videoeditor.lib.filters.FilterPreset
import com.videoeditor.lib.overlay.OverlayItem
import kotlinx.coroutines.launch
import java.io.File

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    val videoUri = MutableLiveData<Uri?>()
    val filterParams = MutableLiveData(FilterParams())
    val activePreset = MutableLiveData(FilterPreset.NONE)
    val overlays = MutableLiveData<MutableList<OverlayItem>>(mutableListOf())
    val exportProgress = MutableLiveData<Int>()
    val exportResult = MutableLiveData<VideoExporter.ExportResult?>()

    fun applyPreset(preset: FilterPreset) {
        activePreset.value = preset
        filterParams.value = filterParams.value?.copy(filterType = preset.id)
    }

    fun updateBrightness(v: Float) {
        filterParams.value = filterParams.value?.copy(brightness = v)
    }
    fun updateContrast(v: Float) {
        filterParams.value = filterParams.value?.copy(contrast = v)
    }
    fun updateSaturation(v: Float) {
        filterParams.value = filterParams.value?.copy(saturation = v)
    }

    fun addOverlay(item: OverlayItem) {
        val list = overlays.value ?: mutableListOf()
        list.add(item)
        overlays.value = list
    }

    fun removeOverlay(id: String) {
        val list = overlays.value ?: return
        list.removeAll { it.id == id }
        overlays.value = list
    }

    fun startExport(outputDir: File, previewWidth: Int, previewHeight: Int) {
        val uri = videoUri.value ?: return
        val params = filterParams.value ?: FilterParams()
        val items = overlays.value ?: emptyList()
        val outFile = File(outputDir, "edited_${System.currentTimeMillis()}.mp4")

        val vertSrc = getApplication<Application>().resources.openRawResource(R.raw.vertex_shader).bufferedReader().readText().trim('\uFEFF', '\u200B')
        val fragSrc = getApplication<Application>().resources.openRawResource(R.raw.fragment_shader).bufferedReader().readText().trim('\uFEFF', '\u200B')

        viewModelScope.launch {
            val exporter = VideoExporter(getApplication(), vertSrc, fragSrc)
            val result = exporter.export(
                VideoExporter.ExportConfig(uri, outFile, params, items, previewWidth = previewWidth, previewHeight = previewHeight)
            ) { pct -> exportProgress.postValue(pct) }
            exportResult.postValue(result)
        }
    }
}
