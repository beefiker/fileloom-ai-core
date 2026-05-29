package dev.jaeyoung.fileloom.ai

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

object AiPrivateDataDecompressor {
    private val ai12Marker = "%AI12_CompressedData".encodeToByteArray()
    private val ai24Marker = "%AI24_ZStandard_Data".encodeToByteArray()
    private val zlibHeaderMarker = byteArrayOf('H'.code.toByte(), 0x89.toByte())

    fun decompress(blocks: List<ByteArray>): String {
        if (blocks.isEmpty()) return ""
        val joined = blocks.concat()
        val dataStart = joined.indexAfterAsciiWhitespace(start = 0)
        if (joined.startsWith(ai24Marker, start = dataStart)) {
            throw UnsupportedOperationException(
                "Zstandard-compressed Illustrator private data requires explicit bundle-size approval"
            )
        }
        if (joined.startsWith(ai12Marker, start = dataStart)) {
            val compressedStart = joined.indexAfterAsciiWhitespace(start = dataStart + ai12Marker.size)
            return inflate(joined.copyOfRange(compressedStart, joined.size)).toString(Charsets.UTF_8)
        }
        if (joined.startsWith("%!PS-Adobe", start = dataStart) || joined.startsWith("%AI", start = dataStart)) {
            return joined.toString(Charsets.UTF_8)
        }

        val inflatedBlocks = blocks.mapNotNull { block ->
            val markerIndex = block.indexOf(zlibHeaderMarker)
            if (markerIndex < 0) return@mapNotNull null
            inflate(block.copyOfRange(markerIndex + zlibHeaderMarker.size, block.size))
        }
        if (inflatedBlocks.isNotEmpty()) {
            return inflatedBlocks.concat().toString(Charsets.UTF_8)
        }

        return joined.toString(Charsets.UTF_8)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        return startsWith(prefix = prefix, start = 0)
    }

    private fun ByteArray.startsWith(
        prefix: ByteArray,
        start: Int,
    ): Boolean {
        if (start < 0) return false
        if (size - start < prefix.size) return false
        return prefix.indices.all { index -> this[start + index] == prefix[index] }
    }

    private fun ByteArray.startsWith(prefix: String): Boolean {
        return startsWith(prefix = prefix, start = 0)
    }

    private fun ByteArray.startsWith(
        prefix: String,
        start: Int,
    ): Boolean {
        return startsWith(prefix.encodeToByteArray(), start = start)
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || size < needle.size) return -1
        for (index in 0..size - needle.size) {
            var matches = true
            for (needleIndex in needle.indices) {
                if (this[index + needleIndex] != needle[needleIndex]) {
                    matches = false
                    break
                }
            }
            if (matches) return index
        }
        return -1
    }

    private fun ByteArray.indexAfterAsciiWhitespace(start: Int): Int {
        var index = start
        while (index < size && this[index].isAsciiWhitespace()) index += 1
        return index
    }

    private fun Byte.isAsciiWhitespace(): Boolean {
        return when (toInt() and 0xff) {
            0x09, 0x0a, 0x0c, 0x0d, 0x20 -> true
            else -> false
        }
    }

    private fun List<ByteArray>.concat(): ByteArray {
        val output = ByteArray(sumOf { it.size })
        var offset = 0
        forEach { bytes ->
            bytes.copyInto(output, destinationOffset = offset)
            offset += bytes.size
        }
        return output
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(bytes)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count > 0) {
                output.write(buffer, 0, count)
            } else if (inflater.needsInput() || inflater.needsDictionary()) {
                break
            }
        }
        inflater.end()
        return output.toByteArray()
    }
}
