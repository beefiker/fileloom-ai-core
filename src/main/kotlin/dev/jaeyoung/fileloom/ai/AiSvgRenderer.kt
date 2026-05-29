package dev.jaeyoung.fileloom.ai

import java.util.Locale

object AiSvgRenderer {
    fun renderSvg(
        layers: List<AiLayer>,
        model: AiRenderModel.DisplayList,
        visibilityOverrides: Map<String, Boolean> = emptyMap(),
    ): String {
        return buildString {
            append(
                """<svg xmlns="http://www.w3.org/2000/svg" width="${model.width}" height="${model.height}" """ +
                    """viewBox="${model.viewBoxMinX.formatNumber()} ${model.viewBoxMinY.formatNumber()} """ +
                    """${model.viewBoxWidth.formatNumber()} ${model.viewBoxHeight.formatNumber()}">"""
            )
            layers.forEach { layer ->
                appendLayer(
                    layer = layer,
                    objects = model.objects,
                    visibilityOverrides = visibilityOverrides
                )
            }
            append("</svg>")
        }
    }

    private fun StringBuilder.appendLayer(
        layer: AiLayer,
        objects: List<AiDisplayObject>,
        visibilityOverrides: Map<String, Boolean>,
    ) {
        val visible = visibilityOverrides[layer.id] ?: layer.visible
        append("""<g id="${escapeXml(layer.id)}"""")
        if (!visible) append(""" display="none"""")
        append(">")
        objects
            .filter { it.layerId == layer.id }
            .forEach { appendPath(it) }
        layer.children.forEach { child ->
            appendLayer(
                layer = child,
                objects = objects,
                visibilityOverrides = visibilityOverrides
            )
        }
        append("</g>")
    }

    private fun StringBuilder.appendPath(path: AiDisplayObject) {
        append("""<path id="${escapeXml(path.id)}" d="${escapeXml(path.pathData)}"""")
        append(path.fillColor?.let { """ fill="${formatColor(it)}"""" } ?: """ fill="none"""")
        if (path.fillRule == AiFillRule.EVEN_ODD) append(""" fill-rule="evenodd"""")
        append(path.strokeColor?.let { """ stroke="${formatColor(it)}"""" } ?: """ stroke="none"""")
        if (path.strokeColor != null) {
            append(""" stroke-width="${path.strokeWidth.formatNumber()}"""")
        }
        if (path.strokeWidth > 0f) {
            when (path.strokeLineCap) {
                AiStrokeLineCap.BUTT -> Unit
                AiStrokeLineCap.ROUND -> append(""" stroke-linecap="round"""")
                AiStrokeLineCap.SQUARE -> append(""" stroke-linecap="square"""")
            }
            when (path.strokeLineJoin) {
                AiStrokeLineJoin.MITER -> Unit
                AiStrokeLineJoin.ROUND -> append(""" stroke-linejoin="round"""")
                AiStrokeLineJoin.BEVEL -> append(""" stroke-linejoin="bevel"""")
            }
            append(""" stroke-miterlimit="${path.strokeMiterLimit.formatNumber()}"""")
            if (path.strokeDashArray.isNotEmpty()) {
                append(""" stroke-dasharray="${path.strokeDashArray.joinToString(" ") { it.formatNumber() }}"""")
                if (path.strokeDashOffset > 0f) append(""" stroke-dashoffset="${path.strokeDashOffset.formatNumber()}"""")
            }
        }
        append("/>")
    }

    private fun formatColor(argb: Int): String {
        val rgb = argb and 0x00ffffff
        return "#${rgb.toString(16).padStart(6, '0')}"
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun Float.formatNumber(): String {
        if (this % 1f == 0f) return toInt().toString()
        return String.format(Locale.US, "%.4f", this)
            .trimEnd('0')
            .trimEnd('.')
    }
}
