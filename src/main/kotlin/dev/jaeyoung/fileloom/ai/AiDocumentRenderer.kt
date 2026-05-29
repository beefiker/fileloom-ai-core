package dev.jaeyoung.fileloom.ai

object AiDocumentRenderer {
    fun renderSvg(
        document: AiDocument,
        visibilityOverrides: Map<String, Boolean> = emptyMap(),
    ): AiDocumentRenderResult {
        return when (val model = document.renderModel) {
            is AiRenderModel.DisplayList -> AiDocumentRenderResult.Svg(
                svg = AiSvgRenderer.renderSvg(
                    layers = document.layers,
                    model = model,
                    visibilityOverrides = visibilityOverrides
                )
            )
            AiRenderModel.PdfPreviewOnly -> AiDocumentRenderResult.Unsupported(
                "PDF preview-only Illustrator documents must use the platform PDF renderer"
            )
            is AiRenderModel.Unsupported -> AiDocumentRenderResult.Unsupported(model.reason)
        }
    }
}

sealed interface AiDocumentRenderResult {
    data class Svg(val svg: String) : AiDocumentRenderResult
    data class Unsupported(val reason: String) : AiDocumentRenderResult
}
