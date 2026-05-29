package dev.jaeyoung.fileloom.ai

object AiDetector {
    fun detect(bytes: ByteArray): AiSourceKind {
        val prefix = bytes.copyOfRange(0, minOf(bytes.size, 512)).toString(Charsets.ISO_8859_1)
        return when {
            prefix.contains("%PDF-") -> AiSourceKind.PDF_COMPATIBLE
            prefix.startsWith("%!PS-Adobe") && prefix.contains("Adobe Illustrator") -> AiSourceKind.LEGACY_TEXT
            prefix.contains("%AI") -> AiSourceKind.LEGACY_TEXT
            else -> AiSourceKind.UNKNOWN
        }
    }
}
