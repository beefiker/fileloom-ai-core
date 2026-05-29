package dev.jaeyoung.fileloom.ai

import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object AiDisplayListParser {
    private val unsupportedTextOperators = setOf(
        "BT",
        "ET",
        "Tf",
        "Tj",
        "TJ",
        "'",
        "\"",
        "Td",
        "TD",
        "Tm",
        "T*",
        "Tc",
        "Tw",
        "Tz",
        "TL",
        "Tr",
        "Ts",
    )
    private val unsupportedColorSpaceOperators = setOf(
        "cs",
        "CS",
        "sc",
        "SC",
        "scn",
        "SCN",
    )
    private val unsupportedInlineImageOperators = setOf(
        "BI",
        "ID",
        "EI",
    )
    private val displayListOperators = setOf(
        "q",
        "Q",
        "rg",
        "RG",
        "k",
        "K",
        "g",
        "G",
        "w",
        "J",
        "j",
        "M",
        "d",
        "m",
        "l",
        "re",
        "c",
        "v",
        "y",
        "h",
        "n",
        "f",
        "F",
        "f*",
        "F*",
        "S",
        "s",
        "B",
        "B*",
        "b",
        "b*",
        "W",
        "W*",
        "cm",
        "sh",
        "Do",
        "gs",
        "Lb",
        "Ln",
    ) + unsupportedColorSpaceOperators + unsupportedInlineImageOperators + unsupportedTextOperators

    fun parse(
        source: String,
        layers: List<AiLayer>,
    ): AiDisplayListParseResult {
        val orderedLayers = layers.flattenPreorder()
        val objects = mutableListOf<AiDisplayObject>()
        val unsupportedObjects = mutableListOf<String>()
        val pathCommands = mutableListOf<String>()
        val layerStack = mutableListOf<String?>()
        val graphicsStateStack = mutableListOf<GraphicsState>()
        var layerIndex = -1
        var currentLayerId: String? = null
        var fillColor = 0xff000000.toInt()
        var strokeColor = 0xff000000.toInt()
        var strokeWidth = 1f
        var strokeLineCap = AiStrokeLineCap.BUTT
        var strokeLineJoin = AiStrokeLineJoin.MITER
        var strokeDashArray = emptyList<Float>()
        var strokeDashOffset = 0f
        var strokeMiterLimit = 10f
        var transform = Matrix.identity()
        var currentPathUnsupported = false
        var currentPoint: Point? = null
        var subpathStart: Point? = null
        var hasTrackedBounds = false
        var minX = 0f
        var minY = 0f
        var maxX = 0f
        var maxY = 0f
        var currentPathHasTrackedPoint = false
        var currentPathMinX = 0f
        var currentPathMinY = 0f
        var currentPathMaxX = 0f
        var currentPathMaxY = 0f
        var currentPathSegmentCount = 0
        val operands = mutableListOf<String>()

        fun includeBounds(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
        ) {
            if (!hasTrackedBounds) {
                minX = left
                minY = top
                maxX = right
                maxY = bottom
                hasTrackedBounds = true
            } else {
                minX = min(minX, left)
                minY = min(minY, top)
                maxX = max(maxX, right)
                maxY = max(maxY, bottom)
            }
        }

        fun includeCurrentPathBounds(padding: Float) {
            if (!currentPathHasTrackedPoint) return
            includeBounds(
                left = currentPathMinX - padding,
                top = currentPathMinY - padding,
                right = currentPathMaxX + padding,
                bottom = currentPathMaxY + padding,
            )
        }

        fun trackPoint(x: Float, y: Float) {
            if (!currentPathHasTrackedPoint) {
                currentPathMinX = x
                currentPathMinY = y
                currentPathMaxX = x
                currentPathMaxY = y
                currentPathHasTrackedPoint = true
            } else {
                currentPathMinX = min(currentPathMinX, x)
                currentPathMinY = min(currentPathMinY, y)
                currentPathMaxX = max(currentPathMaxX, x)
                currentPathMaxY = max(currentPathMaxY, y)
            }
        }

        fun resetCurrentPathBounds() {
            currentPathHasTrackedPoint = false
            currentPathMinX = 0f
            currentPathMinY = 0f
            currentPathMaxX = 0f
            currentPathMaxY = 0f
            currentPathSegmentCount = 0
        }

        fun trackSegment(count: Int = 1) {
            currentPathSegmentCount += count
        }

        fun transformedPoint(x: Float, y: Float): Point {
            return transform.map(Point(x, y))
        }

        fun clearPath() {
            pathCommands.clear()
            currentPathUnsupported = false
            currentPoint = null
            subpathStart = null
            resetCurrentPathBounds()
        }

        fun markUnsupportedObject(reason: String) {
            unsupportedObjects += reason
        }

        fun emitPath(
            fill: Boolean,
            stroke: Boolean,
            fillRule: AiFillRule = AiFillRule.NON_ZERO,
        ) {
            val layerId = currentLayerId ?: return
            if (pathCommands.isEmpty()) {
                if (currentPathUnsupported) clearPath()
                return
            }
            if (currentPathUnsupported) {
                clearPath()
                return
            }
            val strokeScale = if (stroke) {
                transform.uniformStrokeScaleOrNull() ?: run {
                    markUnsupportedObject("Unsupported non-uniform stroke transform")
                    clearPath()
                    return
                }
            } else {
                1f
            }
            val scaledStrokeWidth = if (stroke) strokeWidth * strokeScale else 0f
            val strokePadding = if (
                stroke &&
                strokeLineJoin == AiStrokeLineJoin.MITER &&
                currentPathSegmentCount > 1
            ) {
                (scaledStrokeWidth / 2f) * strokeMiterLimit
            } else {
                scaledStrokeWidth / 2f
            }
            includeCurrentPathBounds(padding = strokePadding)
            objects += AiDisplayObject(
                id = "ai-object-${(objects.size + 1).toString().padStart(4, '0')}",
                layerId = layerId,
                pathData = pathCommands.joinToString(separator = " "),
                fillColor = if (fill) fillColor else null,
                strokeColor = if (stroke) strokeColor else null,
                strokeWidth = scaledStrokeWidth,
                fillRule = fillRule,
                strokeLineCap = if (stroke) strokeLineCap else AiStrokeLineCap.BUTT,
                strokeLineJoin = if (stroke) strokeLineJoin else AiStrokeLineJoin.MITER,
                strokeDashArray = if (stroke) strokeDashArray.map { it * strokeScale } else emptyList(),
                strokeDashOffset = if (stroke) strokeDashOffset * strokeScale else 0f,
                strokeMiterLimit = if (stroke) strokeMiterLimit else 10f,
            )
            clearPath()
        }

        fun markPathUnsupported(reason: String) {
            markUnsupportedObject(reason)
            currentPathUnsupported = true
        }

        fun markTransformChangeUnsupportedIfPathActive(operator: String) {
            if (pathCommands.isNotEmpty()) {
                markPathUnsupported("Unsupported transform change with active path: $operator")
            }
        }

        fun closePath() {
            if (currentPoint == null || subpathStart == null || pathCommands.isEmpty()) return
            pathCommands += "Z"
            if (currentPathSegmentCount > 0) trackSegment()
            currentPoint = subpathStart ?: currentPoint
        }

        fun markMissingCurrentPointUnsupported(operator: String) {
            markPathUnsupported("Unsupported path operator without current point: $operator")
        }

        fun flushUnknownOperands() {
            operands
                .filter { it.isUnknownOperatorCandidate() }
                .forEach { token ->
                    markPathUnsupported("Unsupported Illustrator operator: $token")
                }
            operands.clear()
        }

        fun handleOperator(operator: String, operands: List<String>) {
            val nonFiniteOperands = operands.filter { token ->
                token.toFloatOrNull()?.isFinite() == false
            }
            if (nonFiniteOperands.isNotEmpty()) {
                markPathUnsupported("Unsupported non-finite numeric operand for operator: $operator")
                return
            }
            val numbers = operands.mapNotNull { it.toFiniteFloatOrNull() }
            val exactOperandCount = when (operator) {
                "q", "Q",
                "h", "n",
                "f", "F", "f*", "F*",
                "S", "s", "B", "B*", "b", "b*" -> 0
                "rg", "RG" -> 3
                "k", "K" -> 4
                "g", "G", "w", "J", "j", "M" -> 1
                "m", "l" -> 2
                "re", "v", "y" -> 4
                "c", "cm" -> 6
                else -> null
            }
            if (exactOperandCount != null && numbers.size != exactOperandCount) {
                markPathUnsupported("Unsupported operand count for operator: $operator")
                return
            }
            when (operator) {
                "q" -> {
                    graphicsStateStack += GraphicsState(
                        fillColor = fillColor,
                        strokeColor = strokeColor,
                        strokeWidth = strokeWidth,
                        strokeLineCap = strokeLineCap,
                        strokeLineJoin = strokeLineJoin,
                        strokeDashArray = strokeDashArray,
                        strokeDashOffset = strokeDashOffset,
                        strokeMiterLimit = strokeMiterLimit,
                        transform = transform,
                    )
                }
                "Q" -> {
                    val state = graphicsStateStack.removeLastOrNull()
                    if (state == null) {
                        markPathUnsupported("Unsupported unmatched graphics state restore: Q")
                    } else {
                        markTransformChangeUnsupportedIfPathActive(operator)
                        fillColor = state.fillColor
                        strokeColor = state.strokeColor
                        strokeWidth = state.strokeWidth
                        strokeLineCap = state.strokeLineCap
                        strokeLineJoin = state.strokeLineJoin
                        strokeDashArray = state.strokeDashArray
                        strokeDashOffset = state.strokeDashOffset
                        strokeMiterLimit = state.strokeMiterLimit
                        transform = state.transform
                    }
                }
                "rg" -> if (numbers.size >= 3) fillColor = rgb(numbers[0], numbers[1], numbers[2])
                "RG" -> if (numbers.size >= 3) strokeColor = rgb(numbers[0], numbers[1], numbers[2])
                "k" -> if (numbers.size >= 4) fillColor = cmyk(numbers[0], numbers[1], numbers[2], numbers[3])
                "K" -> if (numbers.size >= 4) strokeColor = cmyk(numbers[0], numbers[1], numbers[2], numbers[3])
                "g" -> if (numbers.isNotEmpty()) fillColor = gray(numbers[0])
                "G" -> if (numbers.isNotEmpty()) strokeColor = gray(numbers[0])
                "w" -> if (numbers.isNotEmpty()) strokeWidth = numbers[0].coerceAtLeast(0f)
                "J" -> if (numbers.isNotEmpty()) strokeLineCap = strokeLineCap(numbers[0])
                "j" -> if (numbers.isNotEmpty()) strokeLineJoin = strokeLineJoin(numbers[0])
                "M" -> if (numbers.isNotEmpty()) strokeMiterLimit = numbers[0].coerceAtLeast(1f)
                "d" -> {
                    val dash = parseStrokeDash((operands + operator).joinToString(" "))
                    if (dash == null) {
                        markPathUnsupported("Unsupported stroke dash operator")
                    } else {
                        strokeDashArray = dash.array
                        strokeDashOffset = dash.offset
                    }
                }
                "m" -> if (numbers.size >= 2) {
                    val point = transformedPoint(numbers[0], numbers[1])
                    pathCommands += "M ${point.x.formatNumber()} ${point.y.formatNumber()}"
                    trackPoint(point.x, point.y)
                    currentPoint = point
                    subpathStart = currentPoint
                }
                "l" -> if (numbers.size >= 2) {
                    if (currentPoint == null) {
                        markMissingCurrentPointUnsupported(operator)
                    } else {
                        val point = transformedPoint(numbers[0], numbers[1])
                        pathCommands += "L ${point.x.formatNumber()} ${point.y.formatNumber()}"
                        trackPoint(point.x, point.y)
                        trackSegment()
                        currentPoint = point
                    }
                }
                "re" -> if (numbers.size >= 4) {
                    val x = numbers[0]
                    val y = numbers[1]
                    val width = numbers[2]
                    val height = numbers[3]
                    val topLeft = transformedPoint(x, y)
                    val topRight = transformedPoint(x + width, y)
                    val bottomRight = transformedPoint(x + width, y + height)
                    val bottomLeft = transformedPoint(x, y + height)
                    pathCommands += "M ${topLeft.x.formatNumber()} ${topLeft.y.formatNumber()}"
                    pathCommands += "L ${topRight.x.formatNumber()} ${topRight.y.formatNumber()}"
                    pathCommands += "L ${bottomRight.x.formatNumber()} ${bottomRight.y.formatNumber()}"
                    pathCommands += "L ${bottomLeft.x.formatNumber()} ${bottomLeft.y.formatNumber()}"
                    pathCommands += "Z"
                    listOf(topLeft, topRight, bottomRight, bottomLeft).forEach { point ->
                        trackPoint(point.x, point.y)
                    }
                    trackSegment(count = 4)
                    currentPoint = topLeft
                    subpathStart = topLeft
                }
                "c" -> if (numbers.size >= 6) {
                    if (currentPoint == null) {
                        markMissingCurrentPointUnsupported(operator)
                    } else {
                        val control1 = transformedPoint(numbers[0], numbers[1])
                        val control2 = transformedPoint(numbers[2], numbers[3])
                        val end = transformedPoint(numbers[4], numbers[5])
                        pathCommands += "C ${control1.x.formatNumber()} ${control1.y.formatNumber()} " +
                            "${control2.x.formatNumber()} ${control2.y.formatNumber()} " +
                            "${end.x.formatNumber()} ${end.y.formatNumber()}"
                        trackPoint(control1.x, control1.y)
                        trackPoint(control2.x, control2.y)
                        trackPoint(end.x, end.y)
                        trackSegment()
                        currentPoint = end
                    }
                }
                "v" -> if (numbers.size >= 4) {
                    val current = currentPoint
                    if (current == null) {
                        markPathUnsupported("Unsupported curve shorthand without current point: $operator")
                    } else {
                        val control2 = transformedPoint(numbers[0], numbers[1])
                        val end = transformedPoint(numbers[2], numbers[3])
                        pathCommands += "C ${current.x.formatNumber()} ${current.y.formatNumber()} " +
                            "${control2.x.formatNumber()} ${control2.y.formatNumber()} " +
                            "${end.x.formatNumber()} ${end.y.formatNumber()}"
                        trackPoint(control2.x, control2.y)
                        trackPoint(end.x, end.y)
                        trackSegment()
                        currentPoint = end
                    }
                }
                "y" -> if (numbers.size >= 4) {
                    if (currentPoint == null) {
                        markMissingCurrentPointUnsupported(operator)
                    } else {
                        val control1 = transformedPoint(numbers[0], numbers[1])
                        val end = transformedPoint(numbers[2], numbers[3])
                        pathCommands += "C ${control1.x.formatNumber()} ${control1.y.formatNumber()} " +
                            "${end.x.formatNumber()} ${end.y.formatNumber()} " +
                            "${end.x.formatNumber()} ${end.y.formatNumber()}"
                        trackPoint(control1.x, control1.y)
                        trackPoint(end.x, end.y)
                        trackSegment()
                        currentPoint = end
                    }
                }
                "h" -> {
                    closePath()
                }
                "n" -> clearPath()
                "f", "F" -> emitPath(fill = true, stroke = false)
                "f*", "F*" -> emitPath(fill = true, stroke = false, fillRule = AiFillRule.EVEN_ODD)
                "S" -> emitPath(fill = false, stroke = true)
                "s" -> {
                    closePath()
                    emitPath(fill = false, stroke = true)
                }
                "B" -> emitPath(fill = true, stroke = true)
                "B*" -> emitPath(fill = true, stroke = true, fillRule = AiFillRule.EVEN_ODD)
                "b" -> {
                    closePath()
                    emitPath(fill = true, stroke = true)
                }
                "b*" -> {
                    closePath()
                    emitPath(fill = true, stroke = true, fillRule = AiFillRule.EVEN_ODD)
                }
                "W", "W*" -> {
                    markPathUnsupported("Unsupported clipping operator: $operator")
                }
                "cm" -> if (numbers.size >= 6) {
                    markTransformChangeUnsupportedIfPathActive(operator)
                    transform = transform.concat(
                        Matrix(
                            a = numbers[0],
                            b = numbers[1],
                            c = numbers[2],
                            d = numbers[3],
                            e = numbers[4],
                            f = numbers[5],
                        )
                    )
                }
                "sh", "Do" -> {
                    markPathUnsupported("Unsupported paint operator: $operator")
                }
                "gs" -> {
                    markPathUnsupported("Unsupported external graphics state operator: $operator")
                }
                in unsupportedColorSpaceOperators -> {
                    markPathUnsupported("Unsupported color-space operator: $operator")
                }
                in unsupportedInlineImageOperators -> {
                    markPathUnsupported("Unsupported inline image operator: $operator")
                }
                in unsupportedTextOperators -> {
                    markUnsupportedObject("Unsupported text operator: $operator")
                }
            }
        }

        source.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach
            when (line) {
                "%AI5_BeginLayer" -> {
                    flushUnknownOperands()
                    layerIndex += 1
                    currentLayerId = orderedLayers.getOrNull(layerIndex)?.id
                    layerStack += currentLayerId
                    clearPath()
                }
                "%AI5_EndLayer--" -> {
                    flushUnknownOperands()
                    clearPath()
                    layerStack.removeLastOrNull()
                    currentLayerId = layerStack.lastOrNull()
                }
                else -> {
                    if (line.startsWith("%")) return@forEach
                    tokenizeLine(line).forEach { token ->
                        if (token in displayListOperators) {
                            operands
                                .filter { it.isUnknownOperatorCandidate() }
                                .forEach { unknown ->
                                    markPathUnsupported("Unsupported Illustrator operator: $unknown")
                                }
                            handleOperator(token, operands)
                            operands.clear()
                        } else {
                            operands += token
                        }
                    }
                }
            }
        }
        flushUnknownOperands()
        if (graphicsStateStack.isNotEmpty()) {
            markUnsupportedObject("Unsupported unclosed graphics state save")
        }

        val viewBoxMinX = minX.takeIf { hasTrackedBounds } ?: 0f
        val viewBoxMinY = minY.takeIf { hasTrackedBounds } ?: 0f
        val viewBoxWidth = if (hasTrackedBounds) (maxX - minX).takeIf { it > 0f } ?: 1f else 1f
        val viewBoxHeight = if (hasTrackedBounds) (maxY - minY).takeIf { it > 0f } ?: 1f else 1f
        return AiDisplayListParseResult(
            model = AiRenderModel.DisplayList(
                width = viewBoxWidth,
                height = viewBoxHeight,
                objects = objects,
                viewBoxMinX = viewBoxMinX,
                viewBoxMinY = viewBoxMinY,
                viewBoxWidth = viewBoxWidth,
                viewBoxHeight = viewBoxHeight,
            ),
            report = AiRenderReport(unsupportedObjects = unsupportedObjects)
        )
    }

    private fun List<AiLayer>.flattenPreorder(): List<AiLayer> {
        val ordered = mutableListOf<AiLayer>()

        fun append(layer: AiLayer) {
            ordered += layer
            layer.children.forEach(::append)
        }

        forEach(::append)
        return ordered
    }

    private fun rgb(red: Float, green: Float, blue: Float): Int {
        val r = (red.coerceIn(0f, 1f) * 255f).roundToInt()
        val g = (green.coerceIn(0f, 1f) * 255f).roundToInt()
        val b = (blue.coerceIn(0f, 1f) * 255f).roundToInt()
        return (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun gray(value: Float): Int {
        val channel = value.coerceIn(0f, 1f)
        return rgb(channel, channel, channel)
    }

    private fun cmyk(cyan: Float, magenta: Float, yellow: Float, black: Float): Int {
        val c = cyan.coerceIn(0f, 1f)
        val m = magenta.coerceIn(0f, 1f)
        val y = yellow.coerceIn(0f, 1f)
        val k = black.coerceIn(0f, 1f)
        return rgb(
            red = (1f - c) * (1f - k),
            green = (1f - m) * (1f - k),
            blue = (1f - y) * (1f - k)
        )
    }

    private fun strokeLineCap(value: Float): AiStrokeLineCap {
        return when (value.toInt()) {
            1 -> AiStrokeLineCap.ROUND
            2 -> AiStrokeLineCap.SQUARE
            else -> AiStrokeLineCap.BUTT
        }
    }

    private fun strokeLineJoin(value: Float): AiStrokeLineJoin {
        return when (value.toInt()) {
            1 -> AiStrokeLineJoin.ROUND
            2 -> AiStrokeLineJoin.BEVEL
            else -> AiStrokeLineJoin.MITER
        }
    }

    private fun parseStrokeDash(line: String): StrokeDash? {
        val match = Regex("""^\[(.*)]\s+([-+]?\d*\.?\d+(?:[eE][-+]?\d+)?)\s+d$""").matchEntire(line) ?: return null
        val arrayTokens = match.groupValues[1]
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.split(Regex("\\s+"))
            ?: emptyList()
        val array = arrayTokens.map { token ->
            token.toFiniteFloatOrNull()?.coerceAtLeast(0f) ?: return null
        }
        val offset = match.groupValues[2].toFiniteFloatOrNull()?.coerceAtLeast(0f) ?: return null
        return StrokeDash(array = array, offset = offset)
    }

    private fun tokenizeLine(line: String): List<String> {
        val content = stripInlineComment(line)
        val tokens = mutableListOf<String>()
        var index = 0
        while (index < content.length) {
            while (index < content.length && content[index].isWhitespace()) index += 1
            if (index >= content.length) break
            val end = when (content[index]) {
                '(' -> literalStringEnd(content, index)
                '[' -> arrayEnd(content, index)
                else -> {
                    var cursor = index
                    while (cursor < content.length && !content[cursor].isWhitespace()) cursor += 1
                    cursor
                }
            }
            tokens += content.substring(index, end)
            index = end
        }
        return tokens
    }

    private fun stripInlineComment(line: String): String {
        var index = 0
        while (index < line.length) {
            index = when (line[index]) {
                '(' -> literalStringEnd(line, index)
                '%' -> return line.substring(0, index)
                else -> index + 1
            }
        }
        return line
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

    private fun arrayEnd(line: String, start: Int): Int {
        val end = line.indexOf(']', startIndex = start + 1)
        return if (end < 0) line.length else end + 1
    }

    private fun String.isUnknownOperatorCandidate(): Boolean {
        if (isBlank()) return false
        if (toFloatOrNull() != null) return false
        if (startsWith("(") || startsWith("[") || startsWith("/") || startsWith("%")) return false
        return any { it.isLetter() }
    }

    private fun String.toFiniteFloatOrNull(): Float? {
        return toFloatOrNull()?.takeIf { it.isFinite() }
    }

    private fun Float.formatNumber(): String {
        if (this % 1f == 0f) return toInt().toString()
        return String.format(Locale.US, "%.4f", this)
            .trimEnd('0')
            .trimEnd('.')
    }

    private data class Point(
        val x: Float,
        val y: Float,
    )

    private data class GraphicsState(
        val fillColor: Int,
        val strokeColor: Int,
        val strokeWidth: Float,
        val strokeLineCap: AiStrokeLineCap,
        val strokeLineJoin: AiStrokeLineJoin,
        val strokeDashArray: List<Float>,
        val strokeDashOffset: Float,
        val strokeMiterLimit: Float,
        val transform: Matrix,
    )

    private data class StrokeDash(
        val array: List<Float>,
        val offset: Float,
    )

    private data class Matrix(
        val a: Float,
        val b: Float,
        val c: Float,
        val d: Float,
        val e: Float,
        val f: Float,
    ) {
        fun concat(other: Matrix): Matrix {
            return Matrix(
                a = a * other.a + c * other.b,
                b = b * other.a + d * other.b,
                c = a * other.c + c * other.d,
                d = b * other.c + d * other.d,
                e = a * other.e + c * other.f + e,
                f = b * other.e + d * other.f + f,
            )
        }

        fun map(point: Point): Point {
            return Point(
                x = a * point.x + c * point.y + e,
                y = b * point.x + d * point.y + f,
            )
        }

        fun uniformStrokeScaleOrNull(): Float? {
            val xScale = sqrt(a * a + b * b)
            val yScale = sqrt(c * c + d * d)
            val dot = a * c + b * d
            if (xScale <= 0f || yScale <= 0f) return null
            if (abs(xScale - yScale) > STROKE_SCALE_EPSILON) return null
            if (abs(dot) > STROKE_SCALE_EPSILON) return null
            return xScale
        }

        companion object {
            private const val STROKE_SCALE_EPSILON = 0.0001f

            fun identity(): Matrix = Matrix(
                a = 1f,
                b = 0f,
                c = 0f,
                d = 1f,
                e = 0f,
                f = 0f,
            )
        }
    }
}
