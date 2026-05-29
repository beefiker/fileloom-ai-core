package dev.jaeyoung.fileloom.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AiDocumentReaderTest {
    @Test
    fun legacyTextAiReturnsParsedLayersWithUnsupportedRenderModel() {
        val bytes = """
            %!PS-Adobe-3.0
            %%Creator: Adobe Illustrator(R) 8.0
            %AI5_BeginLayer
            0 1 1 Lb
            (Logo) Ln
            %AI5_EndLayer--
        """.trimIndent().encodeToByteArray()

        val document = AiDocumentReader.read(bytes)

        assertEquals(AiSourceKind.LEGACY_TEXT, document.sourceKind)
        assertEquals("Logo", document.layers.single().name)
        assertEquals(false, document.layers.single().visible)
        assertIs<AiRenderModel.Unsupported>(document.renderModel)
    }

    @Test
    fun legacyTextAiWithSupportedPathReturnsDisplayList() {
        val bytes = """
            %!PS-Adobe-3.0
            %%Creator: Adobe Illustrator(R) 8.0
            %AI5_BeginLayer
            1 1 1 Lb
            (Logo) Ln
            0 1 0 rg
            0 0 m
            8 0 l
            8 8 l
            h
            f
            %AI5_EndLayer--
        """.trimIndent().encodeToByteArray()

        val document = AiDocumentReader.read(bytes)

        val displayList = assertIs<AiRenderModel.DisplayList>(document.renderModel)
        assertEquals("ai-layer-0001-logo", displayList.objects.single().layerId)
        assertEquals(0xff00ff00.toInt(), displayList.objects.single().fillColor)
    }

    @Test
    fun pdfCompatibleAiReturnsPdfPreviewWithPrivateDataLayers() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Artwork) Ln
            %AI5_EndLayer--
        """.trimIndent()
        val pdf = pdfWithPrivateData(privateData)

        val document = AiDocumentReader.read(pdf)

        assertEquals(AiSourceKind.PDF_COMPATIBLE, document.sourceKind)
        assertEquals("Artwork", document.layers.single().name)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun pdfCompatibleAiWithSupportedPrivateDataPathReturnsDisplayList() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Artwork) Ln
            0 0 1 rg
            0 0 m
            4 0 l
            4 4 l
            h
            f
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        val displayList = assertIs<AiRenderModel.DisplayList>(document.renderModel)
        assertEquals("ai-layer-0001-artwork", displayList.objects.single().layerId)
        assertEquals(0xff0000ff.toInt(), displayList.objects.single().fillColor)
    }

    @Test
    fun pdfCompatibleAiWithOffsetHeaderReadsPrivateData() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Offset Artwork) Ln
            0 0 1 rg
            0 0 m
            4 0 l
            4 4 l
            h
            f
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(
            "\u0000\u0000generated-by-exporter\n".encodeToByteArray() + pdfWithPrivateData(privateData)
        )

        assertEquals(AiSourceKind.PDF_COMPATIBLE, document.sourceKind)
        assertEquals("Offset Artwork", document.layers.single().name)
        val displayList = assertIs<AiRenderModel.DisplayList>(document.renderModel)
        assertEquals("ai-layer-0001-offset-artwork", displayList.objects.single().layerId)
    }

    @Test
    fun pdfCompatiblePrivateDataBlocksCanBeReadAfterExternalPdfExtraction() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Artwork) Ln
            0 0 1 rg
            0 0 m
            4 0 l
            4 4 l
            h
            f
            %AI5_EndLayer--
        """.trimIndent().encodeToByteArray()

        val document = AiDocumentReader.readPdfCompatiblePrivateData(listOf(privateData))

        assertEquals(AiSourceKind.PDF_COMPATIBLE, document.sourceKind)
        assertEquals("Artwork", document.layers.single().name)
        val displayList = assertIs<AiRenderModel.DisplayList>(document.renderModel)
        assertEquals("ai-layer-0001-artwork", displayList.objects.single().layerId)
    }

    @Test
    fun unsupportedExtractedPrivateDataFallsBackToPdfPreview() {
        val blocks = listOf("%AI24_ZStandard_Data...".encodeToByteArray())

        val document = AiDocumentReader.readPdfCompatiblePrivateData(blocks)

        assertEquals(AiSourceKind.PDF_COMPATIBLE, document.sourceKind)
        assertEquals(emptyList(), document.layers)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun pdfCompatibleAiWithCmykPrivateDataPathReturnsDisplayList() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Artwork) Ln
            0 1 1 0 k
            0 0 m
            4 0 l
            4 4 l
            h
            f
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        val displayList = assertIs<AiRenderModel.DisplayList>(document.renderModel)
        assertEquals(0xffff0000.toInt(), displayList.objects.single().fillColor)
    }

    @Test
    fun pdfCompatibleAiWithUnsupportedClippingFallsBackToPdfPreview() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Artwork) Ln
            0 0 m
            4 0 l
            4 4 l
            h
            W
            f
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        assertEquals("Artwork", document.layers.single().name)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun pdfCompatibleAiWithUnsupportedTextFallsBackToPdfPreview() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Labels) Ln
            BT
            /F1 12 Tf
            0 0 Td
            (Hello) Tj
            ET
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        assertEquals("Labels", document.layers.single().name)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun pdfCompatibleAiWithNonFinitePathOperandFallsBackToPdfPreview() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Invalid Number) Ln
            NaN 0 m
            10 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        assertEquals("Invalid Number", document.layers.single().name)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun pdfCompatibleAiWithIncompleteTransformFallsBackToPdfPreview() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Bad Transform) Ln
            1 0 0 cm
            0 0 m
            10 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        assertEquals("Bad Transform", document.layers.single().name)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun pdfCompatibleAiWithExtraPaintOperandsFallsBackToPdfPreview() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Bad Paint) Ln
            0 0 m
            10 0 l
            1 S
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        assertEquals("Bad Paint", document.layers.single().name)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun pdfCompatibleAiWithUnsupportedPatternColorFallsBackToPdfPreview() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Pattern Fill) Ln
            /Pattern cs
            /P1 scn
            0 0 10 10 re
            f
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        assertEquals("Pattern Fill", document.layers.single().name)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun pdfCompatibleAiWithUnsupportedExternalGraphicsStateFallsBackToPdfPreview() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Transparent Shape) Ln
            /GS1 gs
            0 0 10 10 re
            f
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        assertEquals("Transparent Shape", document.layers.single().name)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun pdfCompatibleAiWithUnmatchedGraphicsStateRestoreFallsBackToPdfPreview() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Unmatched Restore) Ln
            Q
            0 0 m
            5 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        assertEquals("Unmatched Restore", document.layers.single().name)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun pdfCompatibleAiWithInlineImageFallsBackToPdfPreview() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Image Layer) Ln
            BI
            /W 1 /H 1 /CS /RGB /BPC 8
            ID
            abc
            EI
            0 0 10 10 re
            f
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        assertEquals("Image Layer", document.layers.single().name)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun pdfCompatibleAiWithUnknownOperatorFallsBackToPdfPreview() {
        val privateData = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Special Effect) Ln
            0 0 m
            10 0 l
            XM
            f
            %AI5_EndLayer--
        """.trimIndent()

        val document = AiDocumentReader.read(pdfWithPrivateData(privateData))

        assertEquals("Special Effect", document.layers.single().name)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    @Test
    fun zstandardPrivateDataFallsBackToPdfPreviewUntilApproved() {
        val pdf = pdfWithPrivateData("%AI24_ZStandard_Data...")

        val document = AiDocumentReader.read(pdf)

        assertEquals(AiSourceKind.PDF_COMPATIBLE, document.sourceKind)
        assertEquals(emptyList(), document.layers)
        assertIs<AiRenderModel.PdfPreviewOnly>(document.renderModel)
    }

    private fun pdfWithPrivateData(privateData: String): ByteArray {
        val privateDataHex = privateData
            .toByteArray(Charsets.ISO_8859_1)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return """
            %PDF-1.7
            1 0 obj
            <<
              /Type /Page
              /PieceInfo <<
                /Illustrator <<
                  /Private <<
                    /NumBlock 1
                    /AIPrivateData1 <$privateDataHex>
                  >>
                >>
              >>
            >>
            endobj
        """.trimIndent().encodeToByteArray()
    }
}
