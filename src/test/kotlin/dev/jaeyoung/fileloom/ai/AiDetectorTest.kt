package dev.jaeyoung.fileloom.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class AiDetectorTest {
    @Test
    fun detectsPdfCompatibleAi() {
        val bytes = "%PDF-1.7\n".encodeToByteArray()

        assertEquals(AiSourceKind.PDF_COMPATIBLE, AiDetector.detect(bytes))
    }

    @Test
    fun detectsPdfCompatibleAiWhenHeaderIsOffsetInPrefix() {
        val bytes = "\u0000\u0000generated-by-exporter\n%PDF-1.7\n".encodeToByteArray()

        assertEquals(AiSourceKind.PDF_COMPATIBLE, AiDetector.detect(bytes))
    }

    @Test
    fun detectsLegacyTextAi() {
        val bytes = "%!PS-Adobe-3.0\n%%Creator: Adobe Illustrator(R) 8.0\n".encodeToByteArray()

        assertEquals(AiSourceKind.LEGACY_TEXT, AiDetector.detect(bytes))
    }

    @Test
    fun unknownBytesAreUnknown() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)

        assertEquals(AiSourceKind.UNKNOWN, AiDetector.detect(bytes))
    }
}
