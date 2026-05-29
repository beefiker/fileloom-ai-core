package dev.jaeyoung.fileloom.ai

import kotlin.test.Test
import kotlin.test.assertTrue

class AiSvgRendererTest {
    @Test
    fun hiddenLayerIsRenderedAsDisplayNoneGroup() {
        val layer = AiLayer(
            id = "ai-layer-0001-logo",
            name = "Logo",
            visible = true,
            enabled = true,
            printable = true,
        )
        val model = AiRenderModel.DisplayList(
            width = 10f,
            height = 10f,
            objects = listOf(
                AiDisplayObject(
                    id = "path-1",
                    layerId = "ai-layer-0001-logo",
                    pathData = "M 0 0 L 10 0 L 10 10 Z",
                    fillColor = 0xffff0000.toInt(),
                    strokeColor = null,
                    strokeWidth = 0f,
                )
            )
        )

        val svg = AiSvgRenderer.renderSvg(
            layers = listOf(layer),
            model = model,
            visibilityOverrides = mapOf("ai-layer-0001-logo" to false),
        )

        assertTrue(svg.contains("""id="ai-layer-0001-logo""""))
        assertTrue(svg.contains("""display="none""""))
    }

    @Test
    fun rendersChildLayerGroupsRecursively() {
        val child = AiLayer(
            id = "ai-layer-0002-shadow",
            name = "Shadow",
            visible = true,
            enabled = true,
            printable = true,
        )
        val parent = AiLayer(
            id = "ai-layer-0001-logo",
            name = "Logo",
            visible = true,
            enabled = true,
            printable = true,
            children = listOf(child),
        )
        val model = AiRenderModel.DisplayList(
            width = 10f,
            height = 10f,
            objects = listOf(
                AiDisplayObject(
                    id = "path-1",
                    layerId = child.id,
                    pathData = "M 0 0 L 10 0 L 10 10 Z",
                    fillColor = 0xff000000.toInt(),
                    strokeColor = null,
                    strokeWidth = 0f,
                )
            )
        )

        val svg = AiSvgRenderer.renderSvg(
            layers = listOf(parent),
            model = model,
            visibilityOverrides = mapOf(child.id to false),
        )

        assertTrue(svg.contains("""id="ai-layer-0001-logo""""))
        assertTrue(svg.contains("""id="ai-layer-0002-shadow" display="none""""))
        assertTrue(svg.contains("""id="path-1""""))
    }

    @Test
    fun rendersEvenOddFillRule() {
        val layer = AiLayer(
            id = "ai-layer-0001-compound",
            name = "Compound",
            visible = true,
            enabled = true,
            printable = true,
        )
        val model = AiRenderModel.DisplayList(
            width = 10f,
            height = 10f,
            objects = listOf(
                AiDisplayObject(
                    id = "path-1",
                    layerId = layer.id,
                    pathData = "M 0 0 L 10 0 L 10 10 Z",
                    fillColor = 0xff000000.toInt(),
                    strokeColor = null,
                    strokeWidth = 0f,
                    fillRule = AiFillRule.EVEN_ODD,
                )
            )
        )

        val svg = AiSvgRenderer.renderSvg(
            layers = listOf(layer),
            model = model,
        )

        assertTrue(svg.contains("""fill-rule="evenodd""""))
    }

    @Test
    fun rendersStrokeLineCapAndJoin() {
        val layer = AiLayer(
            id = "ai-layer-0001-stroke",
            name = "Stroke",
            visible = true,
            enabled = true,
            printable = true,
        )
        val model = AiRenderModel.DisplayList(
            width = 10f,
            height = 10f,
            objects = listOf(
                AiDisplayObject(
                    id = "path-1",
                    layerId = layer.id,
                    pathData = "M 0 0 L 10 0",
                    fillColor = null,
                    strokeColor = 0xff000000.toInt(),
                    strokeWidth = 2f,
                    strokeLineCap = AiStrokeLineCap.ROUND,
                    strokeLineJoin = AiStrokeLineJoin.BEVEL,
                )
            )
        )

        val svg = AiSvgRenderer.renderSvg(
            layers = listOf(layer),
            model = model,
        )

        assertTrue(svg.contains("""stroke-linecap="round""""))
        assertTrue(svg.contains("""stroke-linejoin="bevel""""))
    }

    @Test
    fun rendersCompactStrokeWidthNumber() {
        val layer = AiLayer(
            id = "ai-layer-0001-stroke",
            name = "Stroke",
            visible = true,
            enabled = true,
            printable = true,
        )
        val model = AiRenderModel.DisplayList(
            width = 10f,
            height = 10f,
            objects = listOf(
                AiDisplayObject(
                    id = "path-1",
                    layerId = layer.id,
                    pathData = "M 0 0 L 10 0",
                    fillColor = null,
                    strokeColor = 0xff000000.toInt(),
                    strokeWidth = 2f,
                )
            )
        )

        val svg = AiSvgRenderer.renderSvg(
            layers = listOf(layer),
            model = model,
        )

        assertTrue(svg.contains("""stroke-width="2""""))
    }

    @Test
    fun rendersZeroStrokeWidthExplicitly() {
        val layer = AiLayer(
            id = "ai-layer-0001-hairline",
            name = "Hairline",
            visible = true,
            enabled = true,
            printable = true,
        )
        val model = AiRenderModel.DisplayList(
            width = 10f,
            height = 10f,
            objects = listOf(
                AiDisplayObject(
                    id = "path-1",
                    layerId = layer.id,
                    pathData = "M 0 0 L 10 0",
                    fillColor = null,
                    strokeColor = 0xff000000.toInt(),
                    strokeWidth = 0f,
                )
            )
        )

        val svg = AiSvgRenderer.renderSvg(
            layers = listOf(layer),
            model = model,
        )

        assertTrue(svg.contains("""stroke="#000000""""))
        assertTrue(svg.contains("""stroke-width="0""""))
    }

    @Test
    fun rendersStrokeDashPattern() {
        val layer = AiLayer(
            id = "ai-layer-0001-dash",
            name = "Dash",
            visible = true,
            enabled = true,
            printable = true,
        )
        val model = AiRenderModel.DisplayList(
            width = 10f,
            height = 10f,
            objects = listOf(
                AiDisplayObject(
                    id = "path-1",
                    layerId = layer.id,
                    pathData = "M 0 0 L 10 0",
                    fillColor = null,
                    strokeColor = 0xff000000.toInt(),
                    strokeWidth = 1f,
                    strokeDashArray = listOf(3f, 2f),
                    strokeDashOffset = 1f,
                )
            )
        )

        val svg = AiSvgRenderer.renderSvg(
            layers = listOf(layer),
            model = model,
        )

        assertTrue(svg.contains("""stroke-dasharray="3 2""""))
        assertTrue(svg.contains("""stroke-dashoffset="1""""))
    }

    @Test
    fun rendersStrokeMiterLimit() {
        val layer = AiLayer(
            id = "ai-layer-0001-miter",
            name = "Miter",
            visible = true,
            enabled = true,
            printable = true,
        )
        val model = AiRenderModel.DisplayList(
            width = 10f,
            height = 10f,
            objects = listOf(
                AiDisplayObject(
                    id = "path-1",
                    layerId = layer.id,
                    pathData = "M 0 0 L 10 0 L 10 10",
                    fillColor = null,
                    strokeColor = 0xff000000.toInt(),
                    strokeWidth = 1f,
                    strokeMiterLimit = 12f,
                )
            )
        )

        val svg = AiSvgRenderer.renderSvg(
            layers = listOf(layer),
            model = model,
        )

        assertTrue(svg.contains("""stroke-miterlimit="12""""))
    }

    @Test
    fun rendersViewBoxBoundsFromDisplayList() {
        val layer = AiLayer(
            id = "ai-layer-0001-bounds",
            name = "Bounds",
            visible = true,
            enabled = true,
            printable = true,
        )
        val model = AiRenderModel.DisplayList(
            width = 10f,
            height = 10f,
            objects = listOf(
                AiDisplayObject(
                    id = "path-1",
                    layerId = layer.id,
                    pathData = "M -5 -5 L 5 5",
                    fillColor = null,
                    strokeColor = 0xff000000.toInt(),
                    strokeWidth = 1f,
                )
            ),
            viewBoxMinX = -5f,
            viewBoxMinY = -5f,
            viewBoxWidth = 10f,
            viewBoxHeight = 10f,
        )

        val svg = AiSvgRenderer.renderSvg(
            layers = listOf(layer),
            model = model,
        )

        assertTrue(svg.contains("""viewBox="-5 -5 10 10""""))
    }
}
