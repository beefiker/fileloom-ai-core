package dev.jaeyoung.fileloom.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AiDocumentRendererTest {
    @Test
    fun rendersSupportedDisplayListDocumentWithLayerOverrides() {
        val layer = AiLayer(
            id = "ai-layer-0001-logo",
            name = "Logo",
            visible = true,
            enabled = true,
            printable = true,
        )
        val document = AiDocument(
            sourceKind = AiSourceKind.LEGACY_TEXT,
            layers = listOf(layer),
            renderModel = AiRenderModel.DisplayList(
                width = 12f,
                height = 12f,
                objects = listOf(
                    AiDisplayObject(
                        id = "ai-object-0001",
                        layerId = layer.id,
                        pathData = "M 0 0 L 12 0 L 12 12 Z",
                        fillColor = 0xffff0000.toInt(),
                        strokeColor = null,
                        strokeWidth = 0f,
                    )
                )
            )
        )

        val result = AiDocumentRenderer.renderSvg(
            document = document,
            visibilityOverrides = mapOf(layer.id to false)
        )

        val svg = assertIs<AiDocumentRenderResult.Svg>(result)
        assertTrue(svg.svg.contains("""id="ai-layer-0001-logo""""))
        assertTrue(svg.svg.contains("""display="none""""))
        assertEquals(true, layer.visible)
    }

    @Test
    fun returnsUnsupportedForPreviewOnlyDocument() {
        val document = AiDocument(
            sourceKind = AiSourceKind.PDF_COMPATIBLE,
            layers = emptyList(),
            renderModel = AiRenderModel.PdfPreviewOnly,
        )

        val result = AiDocumentRenderer.renderSvg(document)

        val unsupported = assertIs<AiDocumentRenderResult.Unsupported>(result)
        assertTrue(unsupported.reason.contains("PDF preview"))
    }
}
