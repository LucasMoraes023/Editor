package app.vortex.studio.ui

import androidx.annotation.StringRes
import app.vortex.studio.R
import app.vortex.studio.core.export.ExportIssueCode
import app.vortex.studio.core.export.ExportQuality
import app.vortex.studio.core.model.AspectPreset
import app.vortex.studio.core.model.EffectCategory
import app.vortex.studio.core.model.EffectType
import app.vortex.studio.core.model.ItemAnimation
import app.vortex.studio.core.model.TransitionType
import app.vortex.studio.core.templates.TemplateCategory
import app.vortex.studio.data.PreviewQuality
import app.vortex.studio.engine.FailureCode

@StringRes fun EffectType.label(): Int = when (this) {
    EffectType.BLACK_WHITE -> R.string.effect_black_white
    EffectType.SEPIA -> R.string.effect_sepia
    EffectType.VINTAGE -> R.string.effect_vintage
    EffectType.CINEMA -> R.string.effect_cinema
    EffectType.CONTRAST -> R.string.effect_contrast
    EffectType.SATURATION -> R.string.effect_saturation
    EffectType.TEMPERATURE -> R.string.effect_temperature
    EffectType.BRIGHTNESS -> R.string.effect_brightness
    EffectType.SHARPEN -> R.string.effect_sharpen
    EffectType.BLUR -> R.string.effect_blur
    EffectType.VIGNETTE -> R.string.effect_vignette
    EffectType.FILM_GRAIN -> R.string.effect_grain
    EffectType.ZOOM -> R.string.effect_zoom
    EffectType.ROTATION -> R.string.effect_rotation
    EffectType.MIRROR_H -> R.string.effect_mirror_h
    EffectType.MIRROR_V -> R.string.effect_mirror_v
    EffectType.RGB_SPLIT -> R.string.effect_rgb_split
    EffectType.GLITCH -> R.string.effect_glitch
    EffectType.CHROMATIC_ABERRATION -> R.string.effect_chromatic
    EffectType.GLOW -> R.string.effect_glow
}

@StringRes fun EffectCategory.label(): Int = when (this) {
    EffectCategory.COLOR -> R.string.cat_color
    EffectCategory.STYLE -> R.string.cat_style
    EffectCategory.FRAMING -> R.string.cat_framing
    EffectCategory.GLITCH -> R.string.cat_glitch
    EffectCategory.LIGHT -> R.string.cat_light
}

@StringRes fun TransitionType.label(): Int = when (this) {
    TransitionType.FADE_BLACK -> R.string.transition_fade_black
    TransitionType.FADE_WHITE -> R.string.transition_fade_white
    TransitionType.DISSOLVE -> R.string.transition_dissolve
    TransitionType.SLIDE_LEFT -> R.string.transition_slide_left
    TransitionType.SLIDE_RIGHT -> R.string.transition_slide_right
    TransitionType.SLIDE_UP -> R.string.transition_slide_up
    TransitionType.SLIDE_DOWN -> R.string.transition_slide_down
    TransitionType.ZOOM_IN -> R.string.transition_zoom_in
    TransitionType.ZOOM_OUT -> R.string.transition_zoom_out
}

@StringRes fun AspectPreset.label(): Int = when (this) {
    AspectPreset.PORTRAIT_9_16 -> R.string.aspect_9_16
    AspectPreset.LANDSCAPE_16_9 -> R.string.aspect_16_9
    AspectPreset.SQUARE_1_1 -> R.string.aspect_1_1
    AspectPreset.PORTRAIT_4_5 -> R.string.aspect_4_5
    AspectPreset.CINEMA_21_9 -> R.string.aspect_21_9
    AspectPreset.CLASSIC_4_3 -> R.string.aspect_4_3
}

@StringRes fun TemplateCategory.label(): Int = when (this) {
    TemplateCategory.BLANK -> R.string.tplcat_blank
    TemplateCategory.SOCIAL -> R.string.tplcat_social
    TemplateCategory.MOTIVATIONAL -> R.string.tplcat_motivational
    TemplateCategory.CINEMATIC -> R.string.tplcat_cinematic
    TemplateCategory.COMMERCIAL -> R.string.tplcat_commercial
}

@StringRes fun templateLabel(id: String): Int = when (id) {
    "blank_reels" -> R.string.tpl_blank_reels
    "blank_tiktok" -> R.string.tpl_blank_tiktok
    "blank_shorts" -> R.string.tpl_blank_shorts
    "blank_youtube" -> R.string.tpl_blank_youtube
    "blank_square" -> R.string.tpl_blank_square
    "social_reels_title" -> R.string.tpl_social_reels_title
    "social_tiktok_caption" -> R.string.tpl_social_tiktok_caption
    "social_shorts_hook" -> R.string.tpl_social_shorts_hook
    "youtube_intro" -> R.string.tpl_youtube_intro
    "commercial_offer" -> R.string.tpl_commercial_offer
    "motivational_quote" -> R.string.tpl_motivational_quote
    "cinematic_title" -> R.string.tpl_cinematic_title
    else -> R.string.tpl_generic
}

@StringRes fun ItemAnimation.label(): Int = when (this) {
    ItemAnimation.NONE -> R.string.anim_none
    ItemAnimation.FADE -> R.string.anim_fade
    ItemAnimation.SLIDE_UP -> R.string.anim_slide_up
    ItemAnimation.SLIDE_DOWN -> R.string.anim_slide_down
    ItemAnimation.SLIDE_LEFT -> R.string.anim_slide_left
    ItemAnimation.SLIDE_RIGHT -> R.string.anim_slide_right
    ItemAnimation.ZOOM -> R.string.anim_zoom
    ItemAnimation.POP -> R.string.anim_pop
}

@StringRes fun FailureCode.label(): Int = when (this) {
    FailureCode.EMPTY_PROJECT -> R.string.err_empty_project
    FailureCode.MISSING_MEDIA -> R.string.err_missing_media
    FailureCode.INSUFFICIENT_STORAGE -> R.string.err_storage
    FailureCode.UNSUPPORTED_FORMAT -> R.string.err_format
    FailureCode.ENCODER_FAILED -> R.string.err_encoder
    FailureCode.IO_ERROR -> R.string.err_io
    FailureCode.OUT_OF_MEMORY -> R.string.err_oom
    FailureCode.UNKNOWN -> R.string.err_unknown
}

@StringRes fun ExportQuality.label(): Int = when (this) {
    ExportQuality.LOW -> R.string.quality_low
    ExportQuality.MEDIUM -> R.string.quality_medium
    ExportQuality.HIGH -> R.string.quality_high
}

@StringRes fun PreviewQuality.label(): Int = when (this) {
    PreviewQuality.LOW -> R.string.prev_low
    PreviewQuality.MEDIUM -> R.string.prev_medium
    PreviewQuality.HIGH -> R.string.prev_high
}

@StringRes fun ExportIssueCode.label(): Int = when (this) {
    ExportIssueCode.EMPTY_PROJECT -> R.string.err_empty_project
    ExportIssueCode.MISSING_ASSET -> R.string.err_missing_media
    ExportIssueCode.EXTRA_VIDEO_TRACK_IGNORED -> R.string.warn_extra_video_track
    ExportIssueCode.TRANSITION_NOT_SUPPORTED -> R.string.warn_transition_unsupported
    ExportIssueCode.AUDIO_TRIMMED_TO_PROJECT_END -> R.string.warn_audio_trimmed
    ExportIssueCode.ITEM_OUTSIDE_PROJECT -> R.string.warn_item_outside
}

@StringRes fun fontLabel(key: String): Int = when (key) {
    "serif" -> R.string.font_serif
    "mono" -> R.string.font_mono
    "sans-condensed" -> R.string.font_sans_condensed
    "cursive" -> R.string.font_cursive
    "casual" -> R.string.font_casual
    else -> R.string.font_sans
}
