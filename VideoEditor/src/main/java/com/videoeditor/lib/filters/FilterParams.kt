package com.videoeditor.lib.filters

data class FilterParams(
    val brightness: Float  = 0f,
    val contrast:   Float  = 1f,
    val saturation: Float  = 1f,
    val filterType: Int    = FilterPreset.NONE.id
)

enum class FilterPreset(val id: Int, val label: String) {
    NONE      (0, "Original"),
    GRAYSCALE (1, "B&W"),
    SEPIA     (2, "Sepia"),
    VIVID     (3, "Vivid"),
    COOL      (4, "Cool"),
    WARM      (5, "Warm"),
    FADE      (6, "Fade"),
    NOIR      (7, "Noir");

    companion object {
        fun fromId(id: Int) = values().firstOrNull { it.id == id } ?: NONE
    }
}
