package dev.jaeyoung.fileloom.ai

internal object AiLiteralStringDecoder {
    fun decodeToString(value: String): String {
        return buildString {
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (char != '\\' || index + 1 >= value.length) {
                    append(char)
                    index += 1
                    continue
                }

                val escaped = value[index + 1]
                when {
                    escaped in '0'..'7' -> {
                        val end = octalEnd(value, index + 1)
                        append(value.substring(index + 1, end).toInt(8).toChar())
                        index = end
                    }
                    escaped == '\r' -> {
                        index += if (index + 2 < value.length && value[index + 2] == '\n') 3 else 2
                    }
                    escaped == '\n' -> {
                        index += 2
                    }
                    else -> {
                        append(decodeSimpleEscape(escaped))
                        index += 2
                    }
                }
            }
        }
    }

    fun decodeToBytes(value: String): ByteArray {
        return decodeToString(value).toByteArray(Charsets.ISO_8859_1)
    }

    private fun octalEnd(value: String, start: Int): Int {
        var end = start
        while (end < value.length && end - start < 3 && value[end] in '0'..'7') {
            end += 1
        }
        return end
    }

    private fun decodeSimpleEscape(char: Char): Char {
        return when (char) {
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'b' -> '\b'
            'f' -> '\u000c'
            else -> char
        }
    }
}
