package com.videoeditor.lib.overlay

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
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

    data class StickerOverlay(
        override val id: String = UUID.randomUUID().toString(),
        var bitmap: Bitmap,
        override var normX: Float    = 0.5f,
        override var normY: Float    = 0.5f,
        override var scale: Float    = 1f,
        override var rotation: Float = 0f
    ) : OverlayItem()
}
