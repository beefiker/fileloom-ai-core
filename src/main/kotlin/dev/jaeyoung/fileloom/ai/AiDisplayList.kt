package dev.jaeyoung.fileloom.ai

data class AiRenderReport(
    val unsupportedObjects: List<String> = emptyList(),
) {
    val hasUnsupportedObjects: Boolean
        get() = unsupportedObjects.isNotEmpty()
}

data class AiDisplayListParseResult(
    val model: AiRenderModel.DisplayList,
    val report: AiRenderReport,
)
