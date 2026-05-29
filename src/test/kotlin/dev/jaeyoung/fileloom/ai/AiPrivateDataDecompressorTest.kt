package dev.jaeyoung.fileloom.ai

import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiPrivateDataDecompressorTest {
    @Test
    fun decompressesAi9StyleIndividualZlibBlocks() {
        val payload = "(Layer 1) Ln\n".encodeToByteArray()
        val compressed = byteArrayOf('H'.code.toByte(), 0x89.toByte()) + zlib(payload)

        val text = AiPrivateDataDecompressor.decompress(listOf(compressed))

        assertEquals("(Layer 1) Ln\n", text)
    }

    @Test
    fun decompressesAi12StyleJoinedZlibStream() {
        val payload = "%AI5_BeginLayer\n(Layer 1) Ln\n%AI5_EndLayer--\n".encodeToByteArray()
        val block = "%AI12_CompressedData".encodeToByteArray() + zlib(payload)

        val text = AiPrivateDataDecompressor.decompress(listOf(block))

        assertEquals(payload.toString(Charsets.UTF_8), text)
    }

    @Test
    fun decompressesAi12StyleJoinedZlibStreamAfterMarkerLineEnding() {
        val payload = "%AI5_BeginLayer\n(Layer 1) Ln\n%AI5_EndLayer--\n".encodeToByteArray()
        val block = "%AI12_CompressedData\r\n".encodeToByteArray() + zlib(payload)

        val text = AiPrivateDataDecompressor.decompress(listOf(block))

        assertEquals(payload.toString(Charsets.UTF_8), text)
    }

    @Test
    fun decompressesAi12StyleJoinedZlibStreamAfterLeadingWhitespace() {
        val payload = "%AI5_BeginLayer\n(Layer 1) Ln\n%AI5_EndLayer--\n".encodeToByteArray()
        val block = "\r\n %AI12_CompressedData\n".encodeToByteArray() + zlib(payload)

        val text = AiPrivateDataDecompressor.decompress(listOf(block))

        assertEquals(payload.toString(Charsets.UTF_8), text)
    }

    @Test
    fun zstandardRequiresExplicitApproval() {
        val block = "%AI24_ZStandard_Data".encodeToByteArray() + byteArrayOf(1, 2, 3)

        assertFailsWith<UnsupportedOperationException> {
            AiPrivateDataDecompressor.decompress(listOf(block))
        }
    }

    private fun zlib(bytes: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(bytes)
        deflater.finish()
        val output = ByteArray(256)
        val length = deflater.deflate(output)
        deflater.end()
        return output.copyOf(length)
    }
}
