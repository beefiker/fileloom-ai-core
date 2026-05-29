package dev.jaeyoung.fileloom.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiDocumentTest {
    @Test
    fun supportsLayerVisibilityOverridesOnlyForDisplayListDocumentsWithLayers() {
        val layer = AiLayer(
            id = "ai-layer-0001-logo",
            name = "Logo",
            visible = true,
            enabled = true,
            printable = true,
        )
        val document = AiDocument(
            sourceKind = AiSourceKind.PDF_COMPATIBLE,
            layers = listOf(layer),
            renderModel = AiRenderModel.DisplayList(
                width = 10f,
                height = 10f,
                objects = listOf(
                    AiDisplayObject(
                        id = "ai-object-0001",
                        layerId = layer.id,
                        pathData = "M 0 0 L 10 0",
                        fillColor = null,
                        strokeColor = 0xff000000.toInt(),
                        strokeWidth = 1f,
                    )
                )
            )
        )

        assertTrue(document.supportsLayerVisibilityOverrides)
    }

    @Test
    fun previewOnlyDocumentsDoNotSupportLayerVisibilityOverrides() {
        val layer = AiLayer(
            id = "ai-layer-0001-logo",
            name = "Logo",
            visible = true,
            enabled = true,
            printable = true,
        )
        val document = AiDocument(
            sourceKind = AiSourceKind.PDF_COMPATIBLE,
            layers = listOf(layer),
            renderModel = AiRenderModel.PdfPreviewOnly,
        )

        assertFalse(document.supportsLayerVisibilityOverrides)
    }

    @Test
    fun displayListWithoutLayerMetadataDoesNotSupportLayerVisibilityOverrides() {
        val document = AiDocument(
            sourceKind = AiSourceKind.LEGACY_TEXT,
            layers = emptyList(),
            renderModel = AiRenderModel.DisplayList(width = 1f, height = 1f, objects = emptyList())
        )

        assertFalse(document.supportsLayerVisibilityOverrides)
    }
}
