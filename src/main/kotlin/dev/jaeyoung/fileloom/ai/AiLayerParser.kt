package dev.jaeyoung.fileloom.ai

object AiLayerParser {
    fun parseLayers(source: String): List<AiLayer> {
        val roots = mutableListOf<LayerBuilder>()
        val stack = mutableListOf<LayerBuilder>()
        var nextIndex = 1

        fun closeCurrentLayer() {
            val closed = stack.removeLastOrNull() ?: return
            val parent = stack.lastOrNull()
            if (parent == null) {
                roots += closed
            } else {
                parent.children += closed
            }
        }

        source.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line == "%AI5_BeginLayer" -> {
                    stack += LayerBuilder(index = nextIndex)
                    nextIndex += 1
                }
                line == "%AI5_EndLayer--" -> {
                    closeCurrentLayer()
                }
                else -> {
                    val operands = mutableListOf<String>()
                    tokenizeLine(line).forEach { token ->
                        when (token) {
                            "Lb" -> {
                                stack.lastOrNull()?.applyLayerFlags(operands)
                                operands.clear()
                            }
                            "Ln" -> {
                                parseLayerName(operands.lastOrNull())?.let { layerName ->
                                    stack.lastOrNull()?.name = layerName
                                }
                                operands.clear()
                            }
                            else -> operands += token
                        }
                    }
                }
            }
        }

        while (stack.isNotEmpty()) {
            closeCurrentLayer()
        }

        return roots.map { it.toLayer() }
    }

    private fun LayerBuilder.toLayer(): AiLayer {
        return AiLayer(
            id = stableLayerId(index, name),
            name = name.ifBlank { "Layer $index" },
            visible = visible,
            enabled = enabled,
            printable = printable,
            children = children.map { it.toLayer() },
        )
    }

    private fun LayerBuilder.applyLayerFlags(tokens: List<String>) {
        visible = tokens.firstOrNull().toLayerFlag(default = true)
        enabled = tokens.getOrNull(1).toLayerFlag(default = true)
        printable = tokens.getOrNull(2).toLayerFlag(default = true)
    }

    private fun String?.toLayerFlag(default: Boolean): Boolean {
        return this?.toFloatOrNull()?.let { value -> value != 0f } ?: default
    }

    private fun parseLayerName(token: String?): String? {
        val escaped = token
            ?.takeIf { it.startsWith("(") && it.endsWith(")") }
            ?.removePrefix("(")
            ?.removeSuffix(")")
            ?: return null
        return AiLiteralStringDecoder.decodeToString(escaped)
    }

    private fun tokenizeLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var index = 0
        while (index < line.length) {
            while (index < line.length && line[index].isWhitespace()) index += 1
            if (index >= line.length) break
            val end = if (line[index] == '(') {
                literalStringEnd(line, index)
            } else {
                var cursor = index
                while (cursor < line.length && !line[cursor].isWhitespace()) cursor += 1
                cursor
            }
            tokens += line.substring(index, end)
            index = end
        }
        return tokens
    }

    private fun literalStringEnd(line: String, start: Int): Int {
        var index = start + 1
        var escaped = false
        var depth = 1
        while (index < line.length) {
            val char = line[index]
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
        return line.length
    }

    private fun stableLayerId(index: Int, name: String): String {
        val normalizedName = name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "layer" }
        return "ai-layer-${index.toString().padStart(4, '0')}-$normalizedName"
    }

    private data class LayerBuilder(
        val index: Int,
        var name: String = "",
        var visible: Boolean = true,
        var enabled: Boolean = true,
        var printable: Boolean = true,
        val children: MutableList<LayerBuilder> = mutableListOf(),
    )
}
