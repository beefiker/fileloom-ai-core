package dev.jaeyoung.fileloom.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiLayerParserTest {
    @Test
    fun parsesLayerNameAndVisibility() {
        val source = """
            %AI5_BeginLayer
            0 1 1 1 0 0 0 7 255 0 0 0 0 0 Lb
            (Logo) Ln
            %AI5_EndLayer--
        """.trimIndent()

        val layers = AiLayerParser.parseLayers(source)

        assertEquals("ai-layer-0001-logo", layers.single().id)
        assertEquals("Logo", layers.single().name)
        assertFalse(layers.single().visible)
        assertTrue(layers.single().enabled)
        assertTrue(layers.single().printable)
    }

    @Test
    fun parsesLayerFlagsAndNameWhenOperatorsShareOneLine() {
        val source = """
            %AI5_BeginLayer
            0 1 1 Lb (Logo) Ln
            %AI5_EndLayer--
        """.trimIndent()

        val layers = AiLayerParser.parseLayers(source)

        assertEquals("Logo", layers.single().name)
        assertEquals("ai-layer-0001-logo", layers.single().id)
        assertFalse(layers.single().visible)
        assertTrue(layers.single().enabled)
        assertTrue(layers.single().printable)
    }

    @Test
    fun parsesDecimalLayerFlags() {
        val source = """
            %AI5_BeginLayer
            0.0 1.0 1.0 Lb
            (Logo) Ln
            %AI5_EndLayer--
        """.trimIndent()

        val layers = AiLayerParser.parseLayers(source)

        assertFalse(layers.single().visible)
        assertTrue(layers.single().enabled)
        assertTrue(layers.single().printable)
    }

    @Test
    fun preservesLayerOrder() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Background) Ln
            %AI5_EndLayer--
            %AI5_BeginLayer
            1 1 1 Lb
            (Ink) Ln
            %AI5_EndLayer--
        """.trimIndent()

        val layers = AiLayerParser.parseLayers(source)

        assertEquals(listOf("Background", "Ink"), layers.map { it.name })
        assertEquals(listOf("ai-layer-0001-background", "ai-layer-0002-ink"), layers.map { it.id })
    }

    @Test
    fun decodesEscapedLayerNames() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Logo \050Final\051\053Backslash\\) Ln
            %AI5_EndLayer--
        """.trimIndent()

        val layers = AiLayerParser.parseLayers(source)

        assertEquals("Logo (Final)+Backslash\\", layers.single().name)
        assertEquals("ai-layer-0001-logo-final-backslash", layers.single().id)
    }

    @Test
    fun preservesNestedLayerHierarchy() {
        val source = """
            %AI5_BeginLayer
            1 1 1 Lb
            (Artwork) Ln
            %AI5_BeginLayer
            0 1 1 Lb
            (Shadow) Ln
            %AI5_EndLayer--
            %AI5_EndLayer--
        """.trimIndent()

        val layers = AiLayerParser.parseLayers(source)

        val parent = layers.single()
        val child = parent.children.single()
        assertEquals("Artwork", parent.name)
        assertEquals("ai-layer-0001-artwork", parent.id)
        assertEquals("Shadow", child.name)
        assertEquals("ai-layer-0002-shadow", child.id)
        assertFalse(child.visible)
    }
}
