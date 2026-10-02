package com.anchor.adhd.ai

data class ModelCatalogEntry(
    val id: String,
    val label: String,
    val subtitle: String,
    val filename: String,
    val url: String,
    val sizeBytes: Long,
    val quant: String,
    val recommendedForRamGb: Int = 8
) {
    fun sizeLabel(): String = when {
        sizeBytes >= 1_000_000_000 -> "%.1f GB".format(sizeBytes / 1_000_000_000.0)
        else -> "%.0f MB".format(sizeBytes / 1_000_000.0)
    }
}

object ModelCatalog {
    val entries = listOf(
        ModelCatalogEntry(
            id = "q8",
            label = "MiniCPM5-1B Claude-Opus-Fable5 Thinking Q8_0",
            subtitle = "Desktop main model · bundled · CPU profile · reasoning off",
            filename = ModelDownloadManager.MODEL_FILENAME,
            url = ModelDownloadManager.DEFAULT_MODEL_URL,
            sizeBytes = DesktopInferenceProfile.MODEL_BYTES,
            quant = "Q8_0",
            recommendedForRamGb = 8
        )
    )

    fun find(id: String): ModelCatalogEntry? = entries.find { it.id == id }
    fun findByFilename(filename: String): ModelCatalogEntry? = entries.find { it.filename == filename }
}
