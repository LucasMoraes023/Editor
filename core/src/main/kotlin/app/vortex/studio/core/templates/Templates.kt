package app.vortex.studio.core.templates

import app.vortex.studio.core.edit.Edits
import app.vortex.studio.core.model.*

enum class TemplateCategory { BLANK, SOCIAL, MOTIVATIONAL, CINEMATIC, COMMERCIAL }

data class TemplateText(
    val text: String, val x: Float, val y: Float, val startUs: Long, val durationUs: Long, val style: TextStyleSpec,
    val animIn: ItemAnimation = ItemAnimation.FADE, val animOut: ItemAnimation = ItemAnimation.FADE,
)

/** Modelo editável: gera um projeto real (formato, fps, textos posicionados e efeitos padrão dos clipes). */
data class TemplateSpec(
    val id: String,
    val category: TemplateCategory,
    val aspect: AspectPreset,
    val fps: Int = 30,
    val defaultEffects: List<EffectType> = emptyList(),
    val texts: List<TemplateText> = emptyList(),
)

object Templates {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GOLD = 0xFFFFD54F.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val BOX = 0xB3000000.toInt()

    val all: List<TemplateSpec> = listOf(
        TemplateSpec("blank_reels", TemplateCategory.BLANK, AspectPreset.PORTRAIT_9_16),
        TemplateSpec("blank_tiktok", TemplateCategory.BLANK, AspectPreset.PORTRAIT_9_16),
        TemplateSpec("blank_shorts", TemplateCategory.BLANK, AspectPreset.PORTRAIT_9_16),
        TemplateSpec("blank_youtube", TemplateCategory.BLANK, AspectPreset.LANDSCAPE_16_9),
        TemplateSpec("blank_square", TemplateCategory.BLANK, AspectPreset.SQUARE_1_1),
        TemplateSpec(
            "social_reels_title", TemplateCategory.SOCIAL, AspectPreset.PORTRAIT_9_16,
            texts = listOf(
                TemplateText("SEU TÍTULO AQUI", 0f, 0.62f, 0L, 3_000_000L, TextStyleSpec(sizeFrac = 0.07f, strokeWidthFrac = 0.006f), ItemAnimation.SLIDE_UP, ItemAnimation.FADE),
                TemplateText("legenda do vídeo", 0f, -0.7f, 0L, 4_000_000L, TextStyleSpec(sizeFrac = 0.04f, bold = false, shadow = true), ItemAnimation.FADE, ItemAnimation.FADE),
            ),
        ),
        TemplateSpec(
            "social_tiktok_caption", TemplateCategory.SOCIAL, AspectPreset.PORTRAIT_9_16,
            texts = listOf(TemplateText("Escreva sua legenda", 0f, -0.45f, 0L, 4_000_000L, TextStyleSpec(sizeFrac = 0.06f, strokeWidthFrac = 0.008f, colorArgb = WHITE), ItemAnimation.POP, ItemAnimation.FADE)),
        ),
        TemplateSpec(
            "social_shorts_hook", TemplateCategory.SOCIAL, AspectPreset.PORTRAIT_9_16,
            texts = listOf(TemplateText("VOCÊ NÃO VAI ACREDITAR", 0f, 0.7f, 0L, 3_000_000L, TextStyleSpec(sizeFrac = 0.055f, backgroundArgb = BOX), ItemAnimation.ZOOM, ItemAnimation.FADE)),
        ),
        TemplateSpec(
            "youtube_intro", TemplateCategory.COMMERCIAL, AspectPreset.LANDSCAPE_16_9,
            texts = listOf(
                TemplateText("TÍTULO DO VÍDEO", 0f, 0.1f, 0L, 4_000_000L, TextStyleSpec(sizeFrac = 0.11f, strokeWidthFrac = 0.004f), ItemAnimation.FADE, ItemAnimation.FADE),
                TemplateText("subtítulo ou canal", 0f, -0.15f, 500_000L, 3_500_000L, TextStyleSpec(sizeFrac = 0.05f, bold = false, colorArgb = GOLD), ItemAnimation.SLIDE_UP, ItemAnimation.FADE),
            ),
        ),
        TemplateSpec(
            "commercial_offer", TemplateCategory.COMMERCIAL, AspectPreset.SQUARE_1_1,
            defaultEffects = listOf(EffectType.SATURATION),
            texts = listOf(
                TemplateText("OFERTA ESPECIAL", 0f, 0.7f, 0L, 4_000_000L, TextStyleSpec(sizeFrac = 0.08f, backgroundArgb = BOX, colorArgb = GOLD), ItemAnimation.SLIDE_DOWN, ItemAnimation.FADE),
                TemplateText("R$ 99,90", 0f, -0.65f, 800_000L, 3_200_000L, TextStyleSpec(sizeFrac = 0.12f, strokeWidthFrac = 0.006f), ItemAnimation.POP, ItemAnimation.FADE),
            ),
        ),
        TemplateSpec(
            "motivational_quote", TemplateCategory.MOTIVATIONAL, AspectPreset.PORTRAIT_9_16,
            defaultEffects = listOf(EffectType.VIGNETTE),
            texts = listOf(
                TemplateText("Não pare até se orgulhar.", 0f, 0.05f, 0L, 5_000_000L, TextStyleSpec(fontKey = "serif", sizeFrac = 0.06f, italic = true, shadow = true), ItemAnimation.ZOOM, ItemAnimation.FADE),
                TemplateText("— seu nome", 0f, -0.15f, 1_000_000L, 4_000_000L, TextStyleSpec(fontKey = "sans", sizeFrac = 0.032f, bold = false, colorArgb = GOLD), ItemAnimation.FADE, ItemAnimation.FADE),
            ),
        ),
        TemplateSpec(
            "cinematic_title", TemplateCategory.CINEMATIC, AspectPreset.CINEMA_21_9, fps = 24,
            defaultEffects = listOf(EffectType.CINEMA, EffectType.VIGNETTE, EffectType.FILM_GRAIN),
            texts = listOf(TemplateText("T Í T U L O", 0f, 0f, 500_000L, 4_000_000L, TextStyleSpec(fontKey = "serif", sizeFrac = 0.1f, bold = false, shadow = true), ItemAnimation.FADE, ItemAnimation.FADE)),
        ),
    )

    fun byId(id: String?): TemplateSpec? = all.firstOrNull { it.id == id }

    fun defaultEffectsFor(templateId: String?): List<EffectType> = byId(templateId)?.defaultEffects ?: emptyList()

    fun instantiate(spec: TemplateSpec, ids: IdGenerator, name: String, nowMs: Long): Project {
        var p = Project(
            id = ids.next(), name = name, createdAtMs = nowMs, modifiedAtMs = nowMs,
            canvas = CanvasSpec(spec.aspect, spec.fps, FitMode.FIT), templateId = spec.id,
        )
        for (t in spec.texts) {
            p = Edits.addText(p, ids.next(), t.text, t.startUs, t.durationUs, t.style, t.x, t.y, t.animIn, t.animOut)
        }
        return p.normalized()
    }
}
