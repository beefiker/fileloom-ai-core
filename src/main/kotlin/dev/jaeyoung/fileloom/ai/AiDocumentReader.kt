package dev.jaeyoung.fileloom.ai

object AiDocumentReader {
    fun read(bytes: ByteArray): AiDocument {
        return when (val sourceKind = AiDetector.detect(bytes)) {
            AiSourceKind.PDF_COMPATIBLE -> readPdfCompatible(bytes)
            AiSourceKind.LEGACY_TEXT -> readLegacyText(bytes)
            AiSourceKind.UNKNOWN -> AiDocument(
                sourceKind = sourceKind,
                layers = emptyList(),
                renderModel = AiRenderModel.Unsupported("Unsupported Illustrator source")
            )
        }
    }

    fun readPdfCompatiblePrivateData(blocks: List<ByteArray>): AiDocument {
        if (blocks.isEmpty()) {
            return AiDocument(
                sourceKind = AiSourceKind.PDF_COMPATIBLE,
                layers = emptyList(),
                renderModel = AiRenderModel.PdfPreviewOnly
            )
        }
        return runCatching {
            val source = AiPrivateDataDecompressor.decompress(blocks)
            val layers = AiLayerParser.parseLayers(source)
            AiDocument(
                sourceKind = AiSourceKind.PDF_COMPATIBLE,
                layers = layers,
                renderModel = supportedDisplayList(source, layers) ?: AiRenderModel.PdfPreviewOnly
            )
        }.getOrElse {
            AiDocument(
                sourceKind = AiSourceKind.PDF_COMPATIBLE,
                layers = emptyList(),
                renderModel = AiRenderModel.PdfPreviewOnly
            )
        }
    }

    private fun readPdfCompatible(bytes: ByteArray): AiDocument {
        return readPdfCompatiblePrivateData(AiPrivateDataExtractor.extractFromPdfBytes(bytes))
    }

    private fun readLegacyText(bytes: ByteArray): AiDocument {
        val source = AiPrivateDataDecompressor.decompress(listOf(bytes))
        val layers = AiLayerParser.parseLayers(source)
        return AiDocument(
            sourceKind = AiSourceKind.LEGACY_TEXT,
            layers = layers,
            renderModel = supportedDisplayList(source, layers)
                ?: AiRenderModel.Unsupported("Legacy Illustrator vector rendering is not supported yet")
        )
    }

    private fun supportedDisplayList(
        source: String,
        layers: List<AiLayer>,
    ): AiRenderModel.DisplayList? {
        if (layers.isEmpty()) return null
        val result = AiDisplayListParser.parse(source, layers)
        return result.model.takeIf {
            it.objects.isNotEmpty() && !result.report.hasUnsupportedObjects
        }
    }
}
