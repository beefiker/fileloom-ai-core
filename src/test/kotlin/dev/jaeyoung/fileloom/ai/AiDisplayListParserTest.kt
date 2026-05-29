package dev.jaeyoung.fileloom.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiDisplayListParserTest {
    @Test
    fun parsesSimpleFilledPathInsideLayer() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Logo) Ln
            1 0 0 rg
            0 0 m
            10 0 l
            10 10 l
            h
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(10f, result.model.width)
        assertEquals(10f, result.model.height)
        assertEquals(
            AiDisplayObject(
                id = "ai-object-0001",
                layerId = "ai-layer-0001-logo",
                pathData = "M 0 0 L 10 0 L 10 10 Z",
                fillColor = 0xffff0000.toInt(),
                strokeColor = null,
                strokeWidth = 0f,
            ),
            result.model.objects.single()
        )
    }

    @Test
    fun parsesMultiplePathOperatorsOnOneLine() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Logo) Ln
            1 0 0 rg 0 0 m 10 0 l 10 10 l h f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(
            AiDisplayObject(
                id = "ai-object-0001",
                layerId = "ai-layer-0001-logo",
                pathData = "M 0 0 L 10 0 L 10 10 Z",
                fillColor = 0xffff0000.toInt(),
                strokeColor = null,
                strokeWidth = 0f,
            ),
            result.model.objects.single()
        )
    }

    @Test
    fun parsesOperandsSplitAcrossLines() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Logo) Ln
            1 0 0
            rg
            0 0
            m
            10 0
            l
            10 10
            l
            h
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(
            AiDisplayObject(
                id = "ai-object-0001",
                layerId = "ai-layer-0001-logo",
                pathData = "M 0 0 L 10 0 L 10 10 Z",
                fillColor = 0xffff0000.toInt(),
                strokeColor = null,
                strokeWidth = 0f,
            ),
            result.model.objects.single()
        )
    }

    @Test
    fun parsesSimpleStrokedPathInsideLayer() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Line) Ln
            0 0 1 RG
            2 w
            0 0 m
            4 4 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(
            AiDisplayObject(
                id = "ai-object-0001",
                layerId = "ai-layer-0001-line",
                pathData = "M 0 0 L 4 4",
                fillColor = null,
                strokeColor = 0xff0000ff.toInt(),
                strokeWidth = 2f,
            ),
            result.model.objects.single()
        )
    }

    @Test
    fun parsesStrokeLineCapAndJoinOperators() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Styled Stroke) Ln
            1 J
            2 j
            2 w
            0 0 m
            4 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(AiStrokeLineCap.ROUND, result.model.objects.single().strokeLineCap)
        assertEquals(AiStrokeLineJoin.BEVEL, result.model.objects.single().strokeLineJoin)
    }

    @Test
    fun parsesStrokeDashPatternOperator() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Dashed Stroke) Ln
            [3 2] 1 d
            0 0 m
            10 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(listOf(3f, 2f), result.model.objects.single().strokeDashArray)
        assertEquals(1f, result.model.objects.single().strokeDashOffset)
    }

    @Test
    fun parsesStrokeDashOperandsSplitAcrossLines() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Dashed Stroke) Ln
            [3 2]
            1
            d
            0 0 m
            10 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(listOf(3f, 2f), result.model.objects.single().strokeDashArray)
        assertEquals(1f, result.model.objects.single().strokeDashOffset)
    }

    @Test
    fun reportsNonFiniteStrokeDashArrayOperandInsteadOfRenderingInvalidSvg() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Invalid Dash) Ln
            [NaN 2] 0 d
            0 0 m
            10 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("dash") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsInvalidStrokeDashArrayOperandInsteadOfSilentlyDroppingIt() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Invalid Dash) Ln
            [foo 2] 0 d
            0 0 m
            10 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("dash") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun parsesStrokeMiterLimitOperator() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Miter Stroke) Ln
            12 M
            0 0 m
            4 0 l
            4 4 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(12f, result.model.objects.single().strokeMiterLimit)
    }

    @Test
    fun parsesGrayscaleAndCmykColorOperators() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Print) Ln
            0.5 g
            0 0 m
            5 0 l
            5 5 l
            h
            f
            0 1 1 0 k
            6 0 m
            10 0 l
            10 4 l
            h
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(0xff808080.toInt(), result.model.objects[0].fillColor)
        assertEquals(0xffff0000.toInt(), result.model.objects[1].fillColor)
    }

    @Test
    fun reportsIncompleteColorOperatorInsteadOfRenderingWithDefaultColor() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Bad Color) Ln
            1 0 rg
            0 0 m
            10 0 l
            10 10 l
            h
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("rg") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsExtraColorOperandsInsteadOfSilentlyDroppingThem() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Bad Color) Ln
            1 0 0 0 rg
            0 0 m
            10 0 l
            10 10 l
            h
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("rg") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsIncompleteTransformOperatorInsteadOfIgnoringTransform() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Bad Transform) Ln
            1 0 0 cm
            0 0 m
            10 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("cm") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsExtraPaintOperandsInsteadOfSilentlyDroppingThem() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Bad Paint) Ln
            0 0 m
            10 0 l
            1 S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("S") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun parsesCurveShorthandOperators() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Curves) Ln
            0 0 m
            5 0 10 10 v
            15 10 20 20 y
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(
            "M 0 0 C 0 0 5 0 10 10 C 15 10 20 20 20 20",
            result.model.objects.single().pathData
        )
    }

    @Test
    fun appliesCurrentTransformMatrixToPathCoordinates() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Translated) Ln
            1 0 0 1 10 20 cm
            0 0 m
            5 0 l
            5 5 l
            h
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(5f, result.model.width)
        assertEquals(5f, result.model.height)
        assertEquals(10f, result.model.viewBoxMinX)
        assertEquals(20f, result.model.viewBoxMinY)
        assertEquals(5f, result.model.viewBoxWidth)
        assertEquals(5f, result.model.viewBoxHeight)
        assertEquals("M 10 20 L 15 20 L 15 25 Z", result.model.objects.single().pathData)
    }

    @Test
    fun tracksViewBoxBoundsForNegativeCoordinates() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Bounds) Ln
            -5 -5 m
            5 5 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(-5.5f, result.model.viewBoxMinX)
        assertEquals(-5.5f, result.model.viewBoxMinY)
        assertEquals(11f, result.model.viewBoxWidth)
        assertEquals(11f, result.model.viewBoxHeight)
    }

    @Test
    fun expandsViewBoxBoundsForStrokeWidth() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Stroke Bounds) Ln
            4 w
            0 0 m
            10 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(-2f, result.model.viewBoxMinX)
        assertEquals(-2f, result.model.viewBoxMinY)
        assertEquals(14f, result.model.viewBoxWidth)
        assertEquals(4f, result.model.viewBoxHeight)
    }

    @Test
    fun expandsViewBoxBoundsForMiterJoinLimit() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Miter Bounds) Ln
            3 M
            4 w
            0 0 m
            10 0 l
            10 10 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(-6f, result.model.viewBoxMinX)
        assertEquals(-6f, result.model.viewBoxMinY)
        assertEquals(22f, result.model.viewBoxWidth)
        assertEquals(22f, result.model.viewBoxHeight)
    }

    @Test
    fun expandsRectangleOperatorIntoTransformedPath() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Rect) Ln
            1 0 0 1 10 20 cm
            0 0 5 4 re
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals("M 10 20 L 15 20 L 15 24 L 10 24 Z", result.model.objects.single().pathData)
    }

    @Test
    fun pathEndOperatorClearsUnpaintedPath() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Discard) Ln
            0 0 m
            10 0 l
            n
            1 1 m
            3 1 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals("M 1 1 L 3 1", result.model.objects.single().pathData)
    }

    @Test
    fun lowercasePaintOperatorsClosePathBeforePainting() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Close Paint) Ln
            0 0 m
            5 0 l
            5 5 l
            s
            10 0 m
            15 0 l
            15 5 l
            b
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals("M 0 0 L 5 0 L 5 5 Z", result.model.objects[0].pathData)
        assertEquals(null, result.model.objects[0].fillColor)
        assertEquals(0xff000000.toInt(), result.model.objects[0].strokeColor)
        assertEquals("M 10 0 L 15 0 L 15 5 Z", result.model.objects[1].pathData)
        assertEquals(0xff000000.toInt(), result.model.objects[1].fillColor)
        assertEquals(0xff000000.toInt(), result.model.objects[1].strokeColor)
    }

    @Test
    fun parsesEvenOddFillOperators() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Even Odd) Ln
            0 0 m
            10 0 l
            10 10 l
            h
            f*
            20 0 m
            30 0 l
            30 10 l
            h
            B*
            40 0 m
            50 0 l
            50 10 l
            b*
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(AiFillRule.EVEN_ODD, result.model.objects[0].fillRule)
        assertEquals(null, result.model.objects[0].strokeColor)
        assertEquals(AiFillRule.EVEN_ODD, result.model.objects[1].fillRule)
        assertEquals(0xff000000.toInt(), result.model.objects[1].strokeColor)
        assertEquals("M 40 0 L 50 0 L 50 10 Z", result.model.objects[2].pathData)
        assertEquals(AiFillRule.EVEN_ODD, result.model.objects[2].fillRule)
    }

    @Test
    fun restoresTransformAfterGraphicsStatePop() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (State) Ln
            q
            1 0 0 1 10 0 cm
            0 0 m
            5 0 l
            S
            Q
            0 0 m
            5 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals("M 10 0 L 15 0", result.model.objects[0].pathData)
        assertEquals("M 0 0 L 5 0", result.model.objects[1].pathData)
    }

    @Test
    fun reportsUnmatchedGraphicsStateRestoreInsteadOfIgnoringIt() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Unmatched Restore) Ln
            1 0 0 1 10 0 cm
            Q
            0 0 m
            5 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("graphics state restore") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsUnclosedGraphicsStateSaveInsteadOfClaimingFullFidelity() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Unclosed Save) Ln
            q
            0 0 m
            5 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("graphics state save") })
    }

    @Test
    fun reportsUnsupportedTransformChangeWhilePathIsActive() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Transform Path) Ln
            0 0 m
            5 0 l
            2 0 0 2 0 0 cm
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("active path") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsLineToWithoutCurrentPointInsteadOfRenderingInvalidPath() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Invalid Path) Ln
            5 5 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("current point") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsNonFinitePathOperandInsteadOfRenderingInvalidSvg() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Invalid Number) Ln
            NaN 0 m
            10 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("non-finite") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsInfiniteColorOperandInsteadOfClaimingFullFidelity() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Invalid Color) Ln
            Infinity 0 0 rg
            0 0 m
            10 0 l
            10 10 l
            h
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("non-finite") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun unsupportedEmptyPathIsClearedAtPaintBoundary() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Recover Path) Ln
            5 5 l
            S
            0 0 m
            4 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertEquals("M 0 0 L 4 0", result.model.objects.single().pathData)
    }

    @Test
    fun reportsCurveToWithoutCurrentPointInsteadOfRenderingInvalidPath() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Invalid Curve) Ln
            1 1 2 2 3 3 c
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("current point") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsShorthandCurveWithoutCurrentPointInsteadOfRenderingInvalidPath() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Invalid Curve) Ln
            1 1 2 2 y
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("current point") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun closePathWithoutCurrentPointDoesNotEmitPhantomObject() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Empty Close) Ln
            h
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun scalesStrokeWidthWhenFlatteningUniformTransform() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Scaled Stroke) Ln
            2 0 0 2 0 0 cm
            1 w
            0 0 m
            5 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals("M 0 0 L 10 0", result.model.objects.single().pathData)
        assertEquals(2f, result.model.objects.single().strokeWidth)
    }

    @Test
    fun scalesDashPatternWhenFlatteningUniformTransform() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Scaled Dash) Ln
            2 0 0 2 0 0 cm
            [3 2] 1 d
            0 0 m
            5 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        val objectPath = result.model.objects.single()
        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals(listOf(6f, 4f), objectPath.strokeDashArray)
        assertEquals(2f, objectPath.strokeDashOffset)
    }

    @Test
    fun reportsUnsupportedNonUniformStrokeTransformInsteadOfApproximatingWidth() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Scaled Stroke) Ln
            2 0 0 3 0 0 cm
            1 w
            0 0 m
            5 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("non-uniform stroke transform") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsUnsupportedClippingInsteadOfRenderingInaccuratePath() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Print) Ln
            0 0 m
            10 0 l
            10 10 l
            h
            W
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.single().contains("W"))
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsUnsupportedTextOperatorsInsteadOfClaimingFullFidelity() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Labels) Ln
            BT
            /F1 12 Tf
            0 0 Td
            (Hello) Tj
            ET
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("BT") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsUnsupportedPatternColorOperatorsInsteadOfRenderingBlackFallback() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Pattern Fill) Ln
            /Pattern cs
            /P1 scn
            0 0 10 10 re
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("cs") })
        assertTrue(result.report.unsupportedObjects.any { it.contains("scn") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsUnsupportedExternalGraphicsStateInsteadOfIgnoringOpacityOrBlendMode() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Transparent Shape) Ln
            /GS1 gs
            0 0 10 10 re
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("gs") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsUnsupportedInlineImagesInsteadOfDroppingRasterContent() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Image Layer) Ln
            BI
            /W 1 /H 1 /CS /RGB /BPC 8
            ID
            abc
            EI
            0 0 10 10 re
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("BI") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsUnknownOperatorsInsteadOfClaimingFullFidelity() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Special Effect) Ln
            0 0 m
            10 0 l
            XM
            f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("XM") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsUnknownOperatorBeforeKnownOperatorOnSameLine() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Special Effect) Ln
            XM 0 0 m 10 0 l f
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("XM") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun reportsUnknownOperatorBeforeLayerBoundary() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Special Effect) Ln
            XM
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertTrue(result.report.hasUnsupportedObjects)
        assertTrue(result.report.unsupportedObjects.any { it.contains("XM") })
        assertEquals(emptyList(), result.model.objects)
    }

    @Test
    fun ignoresTrailingCommentsAfterSupportedOperators() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Logo) Ln
            0 0 m % move starts the shape
            10 0 l
            S
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertFalse(result.report.hasUnsupportedObjects)
        assertEquals("M 0 0 L 10 0", result.model.objects.single().pathData)
    }

    @Test
    fun mapsObjectsToNestedLayerIdsInPreorder() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Artwork) Ln
            %AI5_BeginLayer
            1 1 1 Lb
            (Shadow) Ln
            0 0 m
            4 0 l
            4 4 l
            h
            f
            %AI5_EndLayer--
            %AI5_EndLayer--
        """.trimIndent()
        val layers = AiLayerParser.parseLayers(source)

        val result = AiDisplayListParser.parse(source, layers)

        assertEquals("ai-layer-0002-shadow", result.model.objects.single().layerId)
    }
}
