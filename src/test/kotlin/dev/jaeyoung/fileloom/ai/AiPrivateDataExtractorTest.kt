package dev.jaeyoung.fileloom.ai

import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals

class AiPrivateDataExtractorTest {
    @Test
    fun extractsIllustratorPrivateDataBlocksInNumericOrder() {
        val pdf = """
            %PDF-1.7
            1 0 obj
            <<
              /Type /Page
              /PieceInfo <<
                /Illustrator <<
                  /Private <<
                    /NumBlock 2
                    /AIPrivateData2 (block-two)
                    /AIPrivateData1 (block-one)
                  >>
                >>
              >>
            >>
            endobj
        """.trimIndent().encodeToByteArray()

        val blocks = AiPrivateDataExtractor.extractFromPdfBytes(pdf)

        assertEquals(
            listOf("block-one", "block-two"),
            blocks.map { it.toString(Charsets.ISO_8859_1) }
        )
    }

    @Test
    fun extractsBlocksByNumericNameWhenNumBlockIsMissing() {
        val pdf = """
            %PDF-1.7
            1 0 obj
            <<
              /Type /Page
              /PieceInfo <<
                /Illustrator <<
                  /Private <<
                    /AIPrivateData10 (block-ten)
                    /AIPrivateData2 (block-two)
                    /AIPrivateData1 (block-one)
                  >>
                >>
              >>
            >>
            endobj
        """.trimIndent().encodeToByteArray()

        val blocks = AiPrivateDataExtractor.extractFromPdfBytes(pdf)

        assertEquals(
            listOf("block-one", "block-two", "block-ten"),
            blocks.map { it.toString(Charsets.ISO_8859_1) }
        )
    }

    @Test
    fun fallsBackToNumericBlockNamesWhenNumBlockIsInconsistent() {
        val pdf = """
            %PDF-1.7
            1 0 obj
            <<
              /Type /Page
              /PieceInfo <<
                /Illustrator <<
                  /Private <<
                    /NumBlock 0
                    /AIPrivateData2 (block-two)
                    /AIPrivateData1 (block-one)
                  >>
                >>
              >>
            >>
            endobj
        """.trimIndent().encodeToByteArray()

        val blocks = AiPrivateDataExtractor.extractFromPdfBytes(pdf)

        assertEquals(
            listOf("block-one", "block-two"),
            blocks.map { it.toString(Charsets.ISO_8859_1) }
        )
    }

    @Test
    fun fallsBackToNumericBlockNamesWhenNumBlockCountIsTooSmall() {
        val pdf = """
            %PDF-1.7
            1 0 obj
            <<
              /Type /Page
              /PieceInfo <<
                /Illustrator <<
                  /Private <<
                    /NumBlock 1
                    /AIPrivateData2 (block-two)
                    /AIPrivateData1 (block-one)
                  >>
                >>
              >>
            >>
            endobj
        """.trimIndent().encodeToByteArray()

        val blocks = AiPrivateDataExtractor.extractFromPdfBytes(pdf)

        assertEquals(
            listOf("block-one", "block-two"),
            blocks.map { it.toString(Charsets.ISO_8859_1) }
        )
    }

    @Test
    fun skipsPieceInfoDictionariesWithoutIllustratorPrivateData() {
        val pdf = """
            %PDF-1.7
            1 0 obj
            <<
              /Type /Metadata
              /PieceInfo <<
                /OtherProducer <<
                  /Private <<
                    /AIPrivateData1 (wrong-private)
                  >>
                >>
              >>
            >>
            endobj
            2 0 obj
            <<
              /Type /Page
              /PieceInfo <<
                /Illustrator <<
                  /Private <<
                    /AIPrivateData1 (right-private)
                  >>
                >>
              >>
            >>
            endobj
        """.trimIndent().encodeToByteArray()

        val blocks = AiPrivateDataExtractor.extractFromPdfBytes(pdf)

        assertEquals("right-private", blocks.single().toString(Charsets.ISO_8859_1))
    }

    @Test
    fun resolvesIndirectPieceInfoIllustratorAndPrivateDictionaries() {
        val pdf = """
            %PDF-1.7
            1 0 obj
            <<
              /Type /Page
              /PieceInfo 4 0 R
            >>
            endobj
            2 0 obj
            <<
              /Illustrator <<
                /Private <<
                  /AIPrivateData1 (wrong-earlier-dictionary)
                >>
              >>
            >>
            endobj
            4 0 obj
            <<
              /Illustrator 5 0 R
            >>
            endobj
            5 0 obj
            <<
              /Private 6 0 R
            >>
            endobj
            6 0 obj
            <<
              /AIPrivateData1 (right-private)
            >>
            endobj
        """.trimIndent().encodeToByteArray()

        val blocks = AiPrivateDataExtractor.extractFromPdfBytes(pdf)

        assertEquals(
            listOf("right-private"),
            blocks.map { it.toString(Charsets.ISO_8859_1) }
        )
    }

    @Test
    fun decodesPdfLiteralStringEscapes() {
        val pdf = """
            %PDF-1.7
            1 0 obj
            <<
              /Type /Page
              /PieceInfo <<
                /Illustrator <<
                  /Private <<
                    /AIPrivateData1 (block \050one\051\053two\\end)
                  >>
                >>
              >>
            >>
            endobj
        """.trimIndent().encodeToByteArray()

        val blocks = AiPrivateDataExtractor.extractFromPdfBytes(pdf)

        assertEquals("block (one)+two\\end", blocks.single().toString(Charsets.ISO_8859_1))
    }

    @Test
    fun extractsFlateDecodedIndirectStreamBlock() {
        val compressed = zlib("stream-block".encodeToByteArray()).toString(Charsets.ISO_8859_1)
        val pdf = """
            %PDF-1.7
            1 0 obj
            <<
              /Type /Page
              /PieceInfo <<
                /Illustrator <<
                  /Private <<
                    /AIPrivateData1 2 0 R
                  >>
                >>
              >>
            >>
            endobj
            2 0 obj
            << /Length ${compressed.length} /Filter /FlateDecode >>
            stream
            $compressed
            endstream
            endobj
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        val blocks = AiPrivateDataExtractor.extractFromPdfBytes(pdf)

        assertEquals("stream-block", blocks.single().toString(Charsets.UTF_8))
    }

    @Test
    fun extractsIndirectBlockWithNonZeroGenerationNumber() {
        val pdf = """
            %PDF-1.7
            1 0 obj
            <<
              /Type /Page
              /PieceInfo <<
                /Illustrator <<
                  /Private <<
                    /AIPrivateData1 2 1 R
                  >>
                >>
              >>
            >>
            endobj
            2 1 obj
            (generation-one-block)
            endobj
        """.trimIndent().encodeToByteArray()

        val blocks = AiPrivateDataExtractor.extractFromPdfBytes(pdf)

        assertEquals("generation-one-block", blocks.single().toString(Charsets.ISO_8859_1))
    }

    @Test
    fun ignoresSimilarlyPrefixedPdfDictionaryNames() {
        val pdf = """
            %PDF-1.7
            1 0 obj
            <<
              /Type /Page
              /PieceInformation <<
                /Illustrator <<
                  /Private <<
                    /AIPrivateData1 (wrong-piece-info)
                  >>
                >>
              >>
              /PieceInfo <<
                /Illustrator <<
                  /PrivateData <<
                    /AIPrivateData1 (wrong-private-data)
                  >>
                  /Private <<
                    /AIPrivateData1 (right-private)
                  >>
                >>
              >>
            >>
            endobj
        """.trimIndent().encodeToByteArray()

        val blocks = AiPrivateDataExtractor.extractFromPdfBytes(pdf)

        assertEquals("right-private", blocks.single().toString(Charsets.ISO_8859_1))
    }

    private fun zlib(bytes: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(bytes)
        deflater.finish()
        val output = ByteArray(256)
        val length = deflater.deflate(output)
        return output.copyOf(length)
    }
}
