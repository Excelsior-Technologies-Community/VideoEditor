package com.videoeditor.lib.overlay

import android.graphics.Color
import android.graphics.Typeface

data class TextStyle(
    val name: String,
    val color: Int = Color.WHITE,
    val typeface: Typeface = Typeface.DEFAULT,
    val isBold: Boolean = false,
    val hasShadow: Boolean = false,
    val fontSize: Float = 48f
)
