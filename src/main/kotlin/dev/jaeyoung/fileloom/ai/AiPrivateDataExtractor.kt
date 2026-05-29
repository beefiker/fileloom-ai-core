package dev.jaeyoung.fileloom.ai

import java.util.zip.Inflater

object AiPrivateDataExtractor {
    fun extractFromPdfBytes(bytes: ByteArray): List<ByteArray> {
        val source = bytes.toString(Charsets.ISO_8859_1)
        for (pieceInfo in source.dictionariesAfterName("PieceInfo", source)) {
            val illustrator = pieceInfo.dictionaryAfterName("Illustrator", source) ?: continue
            val privateData = illustrator.dictionaryAfterName("Private", source) ?: continue
            val blocks = privateData.extractPrivateBlocks(source)
            if (blocks.isNotEmpty()) return blocks
        }
        return emptyList()
    }

    private fun String.extractPrivateBlocks(source: String): List<ByteArray> {
        val blocks = privateBlockNumbers()
        val numbersFromCount = numBlock()?.let { count ->
            (1..count).filter { number -> number in blocks }
        }
        val orderedNumbers = if (numbersFromCount != null && numbersFromCount.size == blocks.size) {
            numbersFromCount
        } else {
            blocks.sorted()
        }
        return orderedNumbers.mapNotNull { number ->
            valueAfterName("AIPrivateData$number")?.let { token ->
                parsePdfBytesValue(source, token)
            }
        }
    }

    private fun String.dictionariesAfterName(name: String, source: String): List<String> {
        val dictionaries = mutableListOf<String>()
        var searchFrom = 0
        while (searchFrom < length) {
            val nameIndex = indexOfPdfName(name, startIndex = searchFrom)
            if (nameIndex < 0) break
            dictionaryAtName(nameIndex = nameIndex, name = name, source = source)?.let { dictionary ->
                dictionaries += dictionary
            }
            searchFrom = nameIndex + name.length + 1
        }
        return dictionaries
    }

    private fun String.dictionaryAfterName(name: String, source: String): String? {
        val nameIndex = indexOfPdfName(name)
        if (nameIndex < 0) return null
        return dictionaryAtName(nameIndex = nameIndex, name = name, source = source)
    }

    private fun String.dictionaryAtName(nameIndex: Int, name: String, source: String): String? {
        var valueIndex = nameIndex + name.length + 1
        while (valueIndex < length && this[valueIndex].isWhitespace()) valueIndex += 1
        if (valueIndex >= length) return null
        return when {
            valueIndex + 1 < length && this[valueIndex] == '<' && this[valueIndex + 1] == '<' -> {
                sliceBalancedDictionary(valueIndex)
            }
            else -> {
                val reference = readReferenceToken(valueIndex) ?: return null
                source.parseIndirectDictionary(reference)
            }
        }
    }

    private fun String.sliceBalancedDictionary(start: Int): String? {
        var index = start
        var depth = 0
        while (index < length - 1) {
            when {
                this[index] == '(' -> index = skipLiteralString(index)
                this[index] == '<' && this[index + 1] == '<' -> {
                    depth += 1
                    index += 2
                }
                this[index] == '>' && this[index + 1] == '>' -> {
                    depth -= 1
                    index += 2
                    if (depth == 0) return substring(start, index)
                }
                else -> index += 1
            }
        }
        return null
    }

    private fun String.skipLiteralString(start: Int): Int {
        var index = start + 1
        var escaped = false
        var depth = 1
        while (index < length) {
            val char = this[index]
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '(' -> depth += 1
                char == ')' -> {
                    depth -= 1
                    if (depth == 0) return index + 1
                }
            }
            index += 1
        }
        return length
    }

    private fun String.privateBlockNumbers(): Set<Int> {
        return Regex("""/AIPrivateData(\d+)\b""")
            .findAll(this)
            .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
            .toSet()
    }

    private fun String.numBlock(): Int? {
        return Regex("""/NumBlock\s+(\d+)\b""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun String.valueAfterName(name: String): String? {
        val nameIndex = indexOfPdfName(name)
        if (nameIndex < 0) return null
        var index = nameIndex + name.length + 1
        while (index < length && this[index].isWhitespace()) index += 1
        if (index >= length) return null
        return when {
            this[index] == '(' -> substring(index, skipLiteralString(index))
            this[index] == '<' && index + 1 < length && this[index + 1] != '<' -> {
                val end = indexOf('>', startIndex = index + 1)
                if (end < 0) null else substring(index, end + 1)
            }
            else -> {
                val end = indexOfNextNameOrDictionaryEnd(index)
                substring(index, end).trim()
            }
        }
    }

    private fun String.indexOfPdfName(name: String): Int {
        return indexOfPdfName(name = name, startIndex = 0)
    }

    private fun String.indexOfPdfName(name: String, startIndex: Int): Int {
        val needle = "/$name"
        var searchFrom = startIndex
        while (searchFrom < length) {
            val index = indexOf(needle, startIndex = searchFrom)
            if (index < 0) return -1
            val next = index + needle.length
            if (next >= length || this[next].isPdfNameDelimiter()) return index
            searchFrom = next
        }
        return -1
    }

    private fun Char.isPdfNameDelimiter(): Boolean {
        return isWhitespace() || this in charArrayOf('/', '(', ')', '<', '>', '[', ']', '{', '}', '%')
    }

    private fun String.indexOfNextNameOrDictionaryEnd(start: Int): Int {
        var index = start
        while (index < length) {
            if (this[index] == '(') {
                index = skipLiteralString(index)
            } else if (this[index] == '/' || (this[index] == '>' && index + 1 < length && this[index + 1] == '>')) {
                return index
            } else {
                index += 1
            }
        }
        return length
    }

    private fun String.readReferenceToken(start: Int): String? {
        val match = Regex("""^(\d+)\s+(\d+)\s+R\b""").find(substring(start)) ?: return null
        return match.value
    }

    private fun parsePdfBytesValue(source: String, token: String): ByteArray? {
        return when {
            token.startsWith("(") -> parseLiteralString(token)
            token.startsWith("<") -> parseHexString(token)
            Regex("""\d+\s+\d+\s+R""").matches(token) -> parseIndirectValue(source, token)
            else -> null
        }
    }

    private fun String.parseIndirectDictionary(token: String): String? {
        val parts = Regex("""^(\d+)\s+(\d+)\s+R$""").matchEntire(token)?.groupValues ?: return null
        val objectNumber = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val generationNumber = parts.getOrNull(2)?.toIntOrNull() ?: return null
        val objectPattern = Regex("""(?s)\b$objectNumber\s+$generationNumber\s+obj\s+(.*?)\s+endobj""")
        val objectBody = objectPattern.find(this)?.groupValues?.getOrNull(1)?.trim() ?: return null
        val dictionaryStart = objectBody.indexOf("<<")
        if (dictionaryStart < 0) return null
        return objectBody.sliceBalancedDictionary(dictionaryStart)
    }

    private fun parseIndirectValue(source: String, token: String): ByteArray? {
        val reference = Regex("""^(\d+)\s+(\d+)\s+R$""").matchEntire(token)?.groupValues ?: return null
        val objectNumber = reference.getOrNull(1)?.toIntOrNull() ?: return null
        val generationNumber = reference.getOrNull(2)?.toIntOrNull() ?: return null
        val objectPattern = Regex("""(?s)\b$objectNumber\s+$generationNumber\s+obj\s+(.*?)\s+endobj""")
        val objectBody = objectPattern.find(source)?.groupValues?.getOrNull(1)?.trim() ?: return null
        if ("stream" in objectBody) return parseStreamObject(objectBody)
        return parsePdfBytesValue(source, objectBody)
    }

    private fun parseStreamObject(objectBody: String): ByteArray? {
        val streamIndex = objectBody.indexOf("stream")
        val endStreamIndex = objectBody.indexOf("endstream", startIndex = streamIndex)
        if (streamIndex < 0 || endStreamIndex < 0) return null
        var payloadStart = streamIndex + "stream".length
        if (payloadStart < objectBody.length && objectBody[payloadStart] == '\r') payloadStart += 1
        if (payloadStart < objectBody.length && objectBody[payloadStart] == '\n') payloadStart += 1
        val payloadText = objectBody.substring(payloadStart, endStreamIndex).trimEnd('\r', '\n')
        val payload = payloadText.toByteArray(Charsets.ISO_8859_1)
        return if ("/FlateDecode" in objectBody) inflate(payload) else payload
    }

    private fun parseLiteralString(token: String): ByteArray {
        val body = token.removePrefix("(").removeSuffix(")")
        return AiLiteralStringDecoder.decodeToBytes(body)
    }

    private fun parseHexString(token: String): ByteArray {
        val hex = token.removePrefix("<").removeSuffix(">").filterNot(Char::isWhitespace)
        val padded = if (hex.length % 2 == 0) hex else "${hex}0"
        return ByteArray(padded.length / 2) { index ->
            padded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(bytes)
        val output = java.io.ByteArrayOutputStream()
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
