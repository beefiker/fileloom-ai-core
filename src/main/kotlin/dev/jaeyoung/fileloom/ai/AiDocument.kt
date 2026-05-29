package dev.jaeyoung.fileloom.ai

data class AiDocument(
    val sourceKind: AiSourceKind,
    val layers: List<AiLayer>,
    val renderModel: AiRenderModel,
)

val AiDocument.supportsLayerVisibilityOverrides: Boolean
    get() = layers.isNotEmpty() && renderModel is AiRenderModel.DisplayList

enum class AiSourceKind {
    LEGACY_TEXT,
    PDF_COMPATIBLE,
    UNKNOWN,
}

data class AiLayer(
    val id: String,
    val name: String,
    val visible: Boolean,
    val enabled: Boolean,
    val printable: Boolean,
    val children: List<AiLayer> = emptyList(),
)

sealed interface AiRenderModel {
    data object PdfPreviewOnly : AiRenderModel
    data class DisplayList(
        val width: Float,
        val height: Float,
        val objects: List<AiDisplayObject>,
        val viewBoxMinX: Float = 0f,
        val viewBoxMinY: Float = 0f,
        val viewBoxWidth: Float = width,
        val viewBoxHeight: Float = height,
    ) : AiRenderModel
    data class Unsupported(val reason: String) : AiRenderModel
}

data class AiDisplayObject(
    val id: String,
    val layerId: String,
    val pathData: String,
    val fillColor: Int?,
    val strokeColor: Int?,
    val strokeWidth: Float,
    val fillRule: AiFillRule = AiFillRule.NON_ZERO,
    val strokeLineCap: AiStrokeLineCap = AiStrokeLineCap.BUTT,
    val strokeLineJoin: AiStrokeLineJoin = AiStrokeLineJoin.MITER,
    val strokeDashArray: List<Float> = emptyList(),
    val strokeDashOffset: Float = 0f,
    val strokeMiterLimit: Float = 10f,
)

enum class AiFillRule {
    NON_ZERO,
    EVEN_ODD,
}

enum class AiStrokeLineCap {
    BUTT,
    ROUND,
    SQUARE,
}

enum class AiStrokeLineJoin {
    MITER,
    ROUND,
    BEVEL,
}
