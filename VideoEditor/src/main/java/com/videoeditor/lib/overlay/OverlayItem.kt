package com.videoeditor.lib.overlay

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import java.util.UUID

sealed class OverlayItem {
    abstract val id: String
    abstract var normX: Float
    abstract var normY: Float
    abstract var scale: Float
    abstract var rotation: Float

    data class TextOverlay(
        override val id: String = UUID.randomUUID().toString(),
        var text: String        = "Text",
        var textColor: Int      = Color.WHITE,
        var fontSize: Float     = 48f,
        var typeface: Typeface  = Typeface.DEFAULT,
        var bold: Boolean       = false,
        var hasShadow: Boolean  = true,
        var bgColor: Int        = Color.TRANSPARENT,
        override var normX: Float    = 0.5f,
        override var normY: Float    = 0.5f,
        override var scale: Float    = 1f,
        override var rotation: Float = 0f
    ) : OverlayItem()

    data class GifOverlay(
        override val id: String = UUID.randomUUID().toString(),
        val uri: Uri,
        val gifBytes: ByteArray,
        val width: Int,
        val height: Int,
        override var normX: Float    = 0.5f,
        override var normY: Float    = 0.5f,
        override var scale: Float    = 1f,
        override var rotation: Float = 0f
    ) : OverlayItem() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is GifOverlay) return false
            return id == other.id
        }
        override fun hashCode(): Int = id.hashCode()
    }
}
