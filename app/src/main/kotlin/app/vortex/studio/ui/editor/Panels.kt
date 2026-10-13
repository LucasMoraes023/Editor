@file:OptIn(ExperimentalMaterial3Api::class)

package app.vortex.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vortex.studio.R
import app.vortex.studio.core.export.EngineCapabilities
import app.vortex.studio.core.model.*
import app.vortex.studio.engine.Fonts
import app.vortex.studio.ui.ActionChip
import app.vortex.studio.ui.ChipRow
import app.vortex.studio.ui.LabeledSlider
import app.vortex.studio.ui.fontLabel
import app.vortex.studio.ui.label
import app.vortex.studio.ui.theme.VCyan
import app.vortex.studio.ui.theme.VMuted
import app.vortex.studio.ui.theme.VOutline
import app.vortex.studio.ui.theme.VSurface2

@Composable
fun ToolPanel(tool: Tool, ui: EditorUi, vm: EditorViewModel, onAddAudio: (TrackKind) -> Unit, onAddOverlay: () -> Unit) {
    when (tool) {
        Tool.CUT -> CutPanel(ui, vm)
        Tool.SPEED -> SpeedPanel(ui, vm)
        Tool.AUDIO -> AudioPanel(ui, vm, onAddAudio)
        Tool.EFFECTS -> EffectsPanel(ui, vm)
        Tool.TEXT -> TextPanel(ui, vm)
        Tool.IMAGE -> ImagePanel(ui, vm, onAddOverlay)
        Tool.TRANSFORM -> TransformPanel(ui, vm)
        Tool.TRANSITION -> TransitionPanel(ui, vm)
    }
}

@Composable
private fun Hint(@androidx.annotation.StringRes res: Int) = Text(stringResource(res), color = VMuted, fontSize = 13.sp)

@Composable
private fun CutPanel(ui: EditorUi, vm: EditorViewModel) {
    val hasTarget = ui.selectedId != null || ui.project?.mainClipAt(ui.playheadUs) != null
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ActionChip(Icons.Default.ContentCut, stringResource(R.string.cut_split), vm::split, enabled = hasTarget) }
        item { ActionChip(Icons.Default.FirstPage, stringResource(R.string.cut_trim_start), vm::trimStartHere, enabled = hasTarget) }
        item { ActionChip(Icons.Default.LastPage, stringResource(R.string.cut_trim_end), vm::trimEndHere, enabled = hasTarget) }
        item { ActionChip(Icons.Default.ContentCopy, stringResource(R.string.cut_duplicate), vm::duplicateSelected, enabled = ui.selectedId != null) }
        item { ActionChip(Icons.Default.Delete, stringResource(R.string.cut_delete), vm::deleteSelected, enabled = ui.selectedId != null, danger = true) }
        item { ActionChip(Icons.Default.GraphicEq, stringResource(R.string.cut_suggest), vm::suggestCuts, enabled = ui.selected is MediaClip) }
    }
    Spacer(Modifier.height(8.dp))
    if (ui.suggestions.isNotEmpty()) {
        val total = ui.suggestions.sumOf { it.durationUs } / US_PER_SEC.toDouble()
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VSurface2).padding(12.dp)) {
            Text(stringResource(R.string.cut_pauses_found, ui.suggestions.size, total), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(vm::applySuggestions) { Text(stringResource(R.string.cut_remove_pauses)) }
                TextButton(vm::dismissSuggestions) { Text(stringResource(R.string.cut_ignore)) }
            }
        }
    } else Hint(R.string.panel_cut_hint)
}

@Composable
private fun SpeedPanel(ui: EditorUi, vm: EditorViewModel) {
    when (val sel = ui.selected) {
        is MediaClip -> if (sel.isStill) {
            LabeledSlider(stringResource(R.string.speed_duration), "%.1f s".format(sel.durationUs / 1e6f), sel.durationUs / 1e6f, { vm.setStillDuration((it * US_PER_SEC).toLong()) }, 0.5f..15f)
        } else SpeedControls(sel.speed, vm)
        is AudioClip -> SpeedControls(sel.speed, vm)
        else -> Hint(R.string.panel_select_clip_hint)
    }
}

@Composable
private fun SpeedControls(speed: Float, vm: EditorViewModel) {
    LabeledSlider(stringResource(R.string.speed_label), "%.2fx".format(speed), speed.coerceAtMost(4f), vm::setSpeed, MIN_SPEED..4f)
    ChipRow(listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f, 8f), listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f, 8f).firstOrNull { kotlin.math.abs(it - speed) < 0.01f }, { "${it}x" }, vm::setSpeed)
    Spacer(Modifier.height(4.dp))
    Hint(R.string.speed_hint)
}

@Composable
private fun AudioPanel(ui: EditorUi, vm: EditorViewModel, onAddAudio: (TrackKind) -> Unit) {
    val sel = ui.selected
    val (volume, muted, fin, fout) = when (sel) {
        is MediaClip -> listOf(sel.volume, sel.muted, sel.fadeInUs, sel.fadeOutUs)
        is AudioClip -> listOf(sel.volume, sel.muted, sel.fadeInUs, sel.fadeOutUs)
        else -> listOf(null, null, null, null)
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ActionChip(Icons.Default.MusicNote, stringResource(R.string.audio_add_music), { onAddAudio(TrackKind.MUSIC) }) }
        item { ActionChip(Icons.Default.GraphicEq, stringResource(R.string.audio_add_sfx), { onAddAudio(TrackKind.SFX) }) }
        item { ActionChip(Icons.Default.Mic, stringResource(R.string.audio_add_voice), { onAddAudio(TrackKind.VOICE) }) }
        if (muted != null) item {
            val isMuted = muted as Boolean
            ActionChip(if (isMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff, stringResource(if (isMuted) R.string.audio_unmute else R.string.audio_mute), vm::toggleMute)
        }
    }
    if (volume == null) { Spacer(Modifier.height(8.dp)); Hint(R.string.panel_select_clip_hint); return }
    val v = volume as Float
    val finUs = fin as Long
    val foutUs = fout as Long
    val fi = finUs / 1e6f
    val fo = foutUs / 1e6f
    LabeledSlider(stringResource(R.string.audio_volume), "${(v * 100).toInt()}%", v, vm::setVolume, 0f..2f)
    LabeledSlider(stringResource(R.string.audio_fade_in), "%.1f s".format(fi), fi, { vm.setFades((it * US_PER_SEC).toLong(), foutUs) }, 0f..5f)
    LabeledSlider(stringResource(R.string.audio_fade_out), "%.1f s".format(fo), fo, { vm.setFades(finUs, (it * US_PER_SEC).toLong()) }, 0f..5f)
    Hint(R.string.audio_hint)
}

@Composable
private fun EffectsPanel(ui: EditorUi, vm: EditorViewModel) {
    val clip = ui.selected as? MediaClip
    if (clip == null) { Hint(R.string.panel_select_clip_hint); return }
    var category by remember { mutableStateOf(EffectCategory.COLOR) }
    ChipRow(EffectCategory.values().toList(), category, { stringResource(it.label()) }, { category = it })
    Spacer(Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(EffectType.values().filter { it.category == category }) { t ->
            FilterChip(
                selected = clip.effects.any { it.type == t },
                onClick = { if (clip.effects.none { it.type == t }) vm.addEffect(t) },
                label = { Text(stringResource(t.label())) },
            )
        }
    }
    clip.effects.forEach { e ->
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(e.type.label()), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Switch(e.enabled, { vm.toggleEffect(e.id, it) })
            IconButton({ vm.removeEffect(e.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.cut_delete), tint = MaterialTheme.colorScheme.error) }
        }
        if (e.type.hasIntensity) {
            val shown = if (e.type.bipolar) "%+d%%".format(((e.intensity - 0.5f) * 200).toInt()) else "${(e.intensity * 100).toInt()}%"
            LabeledSlider(stringResource(R.string.effect_intensity), shown, e.intensity, { vm.setEffectIntensity(e.id, it) }, enabled = e.enabled)
        }
    }
    if (clip.effects.isEmpty()) Hint(R.string.effects_hint)
}

private val SWATCHES = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFD54F, 0xFF18E0F0, 0xFF7C4DFF, 0xFFFF5C7A, 0xFF66BB6A, 0xFFFF8A3D).map { it.toInt() }

@Composable
private fun TextPanel(ui: EditorUi, vm: EditorViewModel) {
    val defaultText = stringResource(R.string.text_default)
    ActionChip(Icons.Default.TextFields, stringResource(R.string.text_add), { vm.addText(defaultText) })
    val t = ui.selected as? TextItem
    if (t == null) { Spacer(Modifier.height(8.dp)); Hint(R.string.text_hint); return }
    var content by remember(t.id) { mutableStateOf(t.text) }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(content, { content = it; vm.updateText { x -> x.copy(text = it) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.text_content)) })
    LabeledSlider(stringResource(R.string.text_size), "${(t.style.sizeFrac * 100).toInt()}", t.style.sizeFrac, { v -> vm.updateText { it.copy(style = it.style.copy(sizeFrac = v)) } }, 0.02f..0.2f)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        items(SWATCHES) { c ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                    .border(if (t.style.colorArgb == c) 3.dp else 1.dp, if (t.style.colorArgb == c) VCyan else VOutline, CircleShape)
                    .clickable { vm.updateText { it.copy(style = it.style.copy(colorArgb = c)) } },
            )
        }
    }
    ChipRow(Fonts.keys, t.style.fontKey, { stringResource(fontLabel(it)) }, { k -> vm.updateText { it.copy(style = it.style.copy(fontKey = k)) } })
    Spacer(Modifier.height(4.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(t.style.bold, { vm.updateText { it.copy(style = it.style.copy(bold = !it.style.bold)) } }, { Text(stringResource(R.string.text_bold)) }) }
        item { FilterChip(t.style.italic, { vm.updateText { it.copy(style = it.style.copy(italic = !it.style.italic)) } }, { Text(stringResource(R.string.text_italic)) }) }
        item { FilterChip(t.style.shadow, { vm.updateText { it.copy(style = it.style.copy(shadow = !it.style.shadow)) } }, { Text(stringResource(R.string.text_shadow)) }) }
        item {
            FilterChip(t.style.backgroundArgb != 0, { vm.updateText { it.copy(style = it.style.copy(backgroundArgb = if (it.style.backgroundArgb == 0) 0xB3000000.toInt() else 0)) } }, { Text(stringResource(R.string.text_background)) })
        }
        items(TextAlignment.values().toList()) { a ->
            FilterChip(t.style.alignment == a, { vm.updateText { it.copy(style = it.style.copy(alignment = a)) } }, {
                Text(stringResource(when (a) { TextAlignment.START -> R.string.text_align_start; TextAlignment.CENTER -> R.string.text_align_center; TextAlignment.END -> R.string.text_align_end }))
            })
        }
    }
    LabeledSlider(stringResource(R.string.text_outline), "${(t.style.strokeWidthFrac * 1000).toInt()}", t.style.strokeWidthFrac, { v -> vm.updateText { it.copy(style = it.style.copy(strokeWidthFrac = v)) } }, 0f..0.02f)
    Text(stringResource(R.string.text_anim_in), fontSize = 12.sp, color = VMuted)
    ChipRow(ItemAnimation.values().toList(), t.animIn, { stringResource(it.label()) }, { a -> vm.updateText { it.copy(animIn = a) } })
    Text(stringResource(R.string.text_anim_out), fontSize = 12.sp, color = VMuted, modifier = Modifier.padding(top = 4.dp))
    ChipRow(ItemAnimation.values().toList(), t.animOut, { stringResource(it.label()) }, { a -> vm.updateText { it.copy(animOut = a) } })
    Hint(R.string.text_drag_hint)
}

@Composable
private fun ImagePanel(ui: EditorUi, vm: EditorViewModel, onAddOverlay: () -> Unit) {
    ActionChip(Icons.Default.Image, stringResource(R.string.image_add), onAddOverlay)
    val o = ui.selected as? OverlayItem
    if (o == null) { Spacer(Modifier.height(8.dp)); Hint(R.string.image_hint); return }
    LabeledSlider(stringResource(R.string.image_size), "${(o.scale * 100).toInt()}%", o.scale, { v -> vm.updateOverlay { it.copy(scale = v) } }, 0.05f..1f)
    LabeledSlider(stringResource(R.string.image_opacity), "${(o.opacity * 100).toInt()}%", o.opacity, { v -> vm.updateOverlay { it.copy(opacity = v) } })
    LabeledSlider(stringResource(R.string.image_rotation), "${o.rotationDeg.toInt()}°", o.rotationDeg, { v -> vm.updateOverlay { it.copy(rotationDeg = v) } }, -180f..180f)
    ChipRow(ItemAnimation.values().toList(), o.animIn, { stringResource(it.label()) }, { a -> vm.updateOverlay { it.copy(animIn = a) } })
    Hint(R.string.text_drag_hint)
}

@Composable
private fun TransformPanel(ui: EditorUi, vm: EditorViewModel) {
    val project = ui.project ?: return
    Text(stringResource(R.string.transform_canvas), fontSize = 12.sp, color = VMuted)
    ChipRow(AspectPreset.values().toList(), project.canvas.aspect, { stringResource(it.label()) }, { a -> vm.setCanvas { it.copy(aspect = a) } })
    Spacer(Modifier.height(4.dp))
    ChipRow(FitMode.values().toList(), project.canvas.fit, { stringResource(if (it == FitMode.FIT) R.string.transform_fit else R.string.transform_fill) }, { f -> vm.setCanvas { it.copy(fit = f) } })
    val clip = ui.selected as? MediaClip
    if (clip == null) { Spacer(Modifier.height(8.dp)); Hint(R.string.panel_select_clip_hint); return }
    val tf = clip.transform
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ActionChip(Icons.Default.RotateRight, stringResource(R.string.transform_rotate), vm::rotate) }
        item { FilterChip(tf.mirrorH, { vm.updateTransform { it.copy(mirrorH = !it.mirrorH) } }, { Text(stringResource(R.string.effect_mirror_h)) }) }
        item { FilterChip(tf.mirrorV, { vm.updateTransform { it.copy(mirrorV = !it.mirrorV) } }, { Text(stringResource(R.string.effect_mirror_v)) }) }
        item { ActionChip(Icons.Default.ZoomIn, stringResource(R.string.transform_kenburns), vm::kenBurns) }
        if (clip.keyframes.isNotEmpty()) item { ActionChip(Icons.Default.Close, stringResource(R.string.transform_clear_keyframes), vm::clearKeyframes) }
    }
    LabeledSlider(stringResource(R.string.transform_zoom), "%.2fx".format(tf.zoom), tf.zoom, { v -> vm.updateTransform { it.copy(zoom = v) } }, 1f..4f)
    LabeledSlider(stringResource(R.string.transform_offset_x), "%+.2f".format(tf.offsetX), tf.offsetX, { v -> vm.updateTransform { it.copy(offsetX = v) } }, -1f..1f)
    LabeledSlider(stringResource(R.string.transform_offset_y), "%+.2f".format(tf.offsetY), tf.offsetY, { v -> vm.updateTransform { it.copy(offsetY = v) } }, -1f..1f)
}

@Composable
private fun TransitionPanel(ui: EditorUi, vm: EditorViewModel) {
    val project = ui.project ?: return
    val clip = ui.selected as? MediaClip
    val main = project.mainTrack.items
    if (clip == null || main.none { it.id == clip.id }) { Hint(R.string.transition_select_hint); return }
    if (main.first().id == clip.id) { Hint(R.string.transition_first_hint); return }
    val options: List<TransitionType?> = listOf(null) + TransitionType.values().toList()
    ChipRow(
        options, clip.transitionIn?.type,
        { if (it == null) stringResource(R.string.transition_none) else stringResource(it.label()) + if (EngineCapabilities.isTransitionSupported(it)) "" else " ·" },
        { t -> vm.setTransition(t?.let { TransitionSpec(it, clip.transitionIn?.durationUs ?: 800_000L) }) },
        enabled = { it == null || EngineCapabilities.isTransitionSupported(it) },
    )
    val d = (clip.transitionIn?.durationUs ?: 800_000L) / 1e6f
    LabeledSlider(stringResource(R.string.transition_duration), "%.1f s".format(d), d, { v -> clip.transitionIn?.let { vm.setTransition(it.copy(durationUs = (v * US_PER_SEC).toLong())) } }, 0.2f..2.5f, enabled = clip.transitionIn != null)
    Hint(R.string.transition_hint)
}
