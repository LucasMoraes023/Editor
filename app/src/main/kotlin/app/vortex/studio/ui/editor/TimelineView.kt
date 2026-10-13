package app.vortex.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vortex.studio.core.model.*
import app.vortex.studio.core.timeline.TimelineMath
import app.vortex.studio.media.Thumbnails
import app.vortex.studio.media.displayAspect
import app.vortex.studio.ui.theme.VCyan
import app.vortex.studio.ui.theme.VDanger
import app.vortex.studio.ui.theme.VMuted
import app.vortex.studio.ui.theme.VOutline
import app.vortex.studio.ui.theme.VSurface
import app.vortex.studio.ui.theme.VSurface2
import app.vortex.studio.ui.theme.VViolet
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private val MAIN_H = 56.dp
private val LANE_H = 34.dp
private val RULER_H = 22.dp

/**
 * Linha do tempo multifaixa. O cabeçote fica fixo no centro: rolar a linha = navegar no tempo
 * (e a reprodução rola sozinha). Toque seleciona; segurar e arrastar move/reordena; as alças das
 * bordas (item selecionado) cortam entrada e saída; pinça ou botões ampliam e reduzem.
 */
@Composable
fun TimelineView(
    project: Project,
    selectedId: String?,
    playheadUs: Long,
    pxPerSec: Float,
    missing: Set<String>,
    waveforms: Map<String, FloatArray>,
    thumbnails: Thumbnails,
    onSeek: (Long) -> Unit,
    onSelect: (String?) -> Unit,
    onZoom: (Float) -> Unit,
    onMoveMain: (String, Int) -> Unit,
    onMoveFree: (String, Long) -> Unit,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.background(VSurface)) {
        val density = LocalDensity.current
        val viewportPx = with(density) { maxWidth.toPx() }
        val half = viewportPx / 2f
        val scroll = rememberScrollState()
        val latestZoom by rememberUpdatedState(pxPerSec)
        val latestOnSeek by rememberUpdatedState(onSeek)
        val durationUs = (project.tracks.maxOfOrNull { t -> t.items.maxOfOrNull { it.endUs } ?: 0L } ?: 0L).coerceAtLeast(project.durationUs)
        val contentPx = TimelineMath.usToPx(durationUs, pxPerSec) + viewportPx
        var programmatic by remember { mutableFloatStateOf(0f) }

        // Reprodução/seek externo -> rola a linha até a posição.
        LaunchedEffect(playheadUs, pxPerSec) {
            val target = TimelineMath.usToPx(playheadUs, pxPerSec).roundToInt()
            if (!scroll.isScrollInProgress && abs(scroll.value - target) > 1) {
                programmatic = 1f
                scroll.scrollTo(target)
                programmatic = 0f
            }
        }
        // Usuário rolando -> navega no tempo.
        LaunchedEffect(scroll, pxPerSec) {
            snapshotFlow { scroll.value }.collect { v ->
                if (scroll.isScrollInProgress && programmatic == 0f) latestOnSeek(TimelineMath.pxToUs(v.toFloat(), pxPerSec))
            }
        }

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size >= 2) {
                            val z = event.calculateZoom()
                            if (z != 1f) { onZoom(latestZoom * z); event.changes.forEach { it.consume() } }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        ) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.horizontalScroll(scroll).width(with(density) { contentPx.toDp() })) {
                    Ruler(durationUs, pxPerSec, half)
                    project.tracks.forEach { track ->
                        val h = if (track.isMain) MAIN_H else LANE_H
                        Box(Modifier.fillMaxWidth().height(h).padding(vertical = 2.dp)) {
                            val mainCenters = if (track.isMain) track.items.map { it.id to TimelineMath.usToPx(it.startUs + it.durationUs / 2, pxPerSec) } else emptyList()
                            track.items.forEach { item ->
                                TimelineItemView(
                                    item = item, track = track, project = project, selected = item.id == selectedId,
                                    missing = (item as? MediaClip)?.assetId in missing || (item as? AudioClip)?.assetId in missing,
                                    half = half, pxPerSec = pxPerSec, height = h - 4.dp, waveforms = waveforms, thumbnails = thumbnails,
                                    mainCenters = mainCenters, playheadUs = playheadUs, siblings = track.items,
                                    onSelect = onSelect, onMoveMain = onMoveMain, onMoveFree = onMoveFree,
                                    onTrimStart = onTrimStart, onTrimEnd = onTrimEnd,
                                )
                            }
                        }
                    }
                }
            }
            // Cabeçote fixo no centro.
            Box(Modifier.align(Alignment.TopCenter).fillMaxHeight().width(2.dp).background(VCyan))
            Box(Modifier.align(Alignment.TopCenter).size(10.dp).background(VCyan, RoundedCornerShape(5.dp)))
        }
    }
}

@Composable
private fun Ruler(durationUs: Long, pxPerSec: Float, halfPx: Float) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(RULER_H)) {
        val ticks = TimelineMath.rulerTicks(0L, durationUs + 2 * US_PER_SEC, pxPerSec)
        for (t in ticks) {
            val x = halfPx + TimelineMath.usToPx(t, pxPerSec)
            drawLine(VMuted, Offset(x, size.height * 0.62f), Offset(x, size.height), strokeWidth = 1f)
            drawText(measurer, formatTimecode(t, false), Offset(x + 3f, 0f), style = TextStyle(color = VMuted, fontSize = 9.sp))
        }
    }
}

@Composable
private fun TimelineItemView(
    item: TimelineItem, track: Track, project: Project, selected: Boolean, missing: Boolean,
    half: Float, pxPerSec: Float, height: Dp, waveforms: Map<String, FloatArray>, thumbnails: Thumbnails,
    mainCenters: List<Pair<String, Float>>, playheadUs: Long, siblings: List<TimelineItem>,
    onSelect: (String?) -> Unit, onMoveMain: (String, Int) -> Unit, onMoveFree: (String, Long) -> Unit,
    onTrimStart: (String, Long) -> Unit, onTrimEnd: (String, Long) -> Unit,
) {
    val density = LocalDensity.current
    var dragPx by remember(item.id, item.startUs) { mutableFloatStateOf(0f) }
    val widthPx = TimelineMath.usToPx(item.durationUs, pxPerSec).coerceAtLeast(6f)
    val xPx = half + TimelineMath.usToPx(item.startUs, pxPerSec)
    val shape = RoundedCornerShape(8.dp)
    val tint = when (track.kind) {
        TrackKind.VIDEO -> VSurface2
        TrackKind.TEXT -> Color(0xFF0E5560)
        TrackKind.IMAGE -> Color(0xFF3B2A6B)
        else -> Color(0xFF4A2F9E)
    }

    Box(
        Modifier
            .offset { IntOffset((xPx + dragPx).roundToInt(), 0) }
            .width(with(density) { widthPx.toDp() })
            .height(height)
            .clip(shape)
            .background(tint, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) VCyan else VOutline, shape)
            .pointerInput(item.id) { detectTapGestures(onTap = { onSelect(item.id) }) }
            .pointerInput(item.id, pxPerSec, mainCenters) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onSelect(item.id) },
                    onDrag = { change, amount -> change.consume(); dragPx += amount.x },
                    onDragEnd = {
                        if (track.isMain) {
                            val center = TimelineMath.usToPx(item.startUs + item.durationUs / 2, pxPerSec) + dragPx
                            val idx = mainCenters.filter { it.first != item.id }.count { it.second < center }
                            onMoveMain(item.id, idx)
                        } else {
                            val raw = item.startUs + TimelineMath.pxToUs(dragPx, pxPerSec)
                            val candidates = listOf(0L, playheadUs) + siblings.filter { it.id != item.id }.flatMap { listOf(it.startUs, it.endUs) }
                            val snapped = TimelineMath.snap(raw, candidates, TimelineMath.pxToUs(10f, pxPerSec))
                            val snappedEnd = TimelineMath.snap(raw + item.durationUs, candidates, TimelineMath.pxToUs(10f, pxPerSec)) - item.durationUs
                            onMoveFree(item.id, if (snapped != raw) snapped else snappedEnd.coerceAtLeast(0L))
                        }
                        dragPx = 0f
                    },
                    onDragCancel = { dragPx = 0f },
                )
            },
    ) {
        when (item) {
            is MediaClip -> ClipContent(item, project.assets[item.assetId], widthPx, height, thumbnails)
            is AudioClip -> AudioContent(item, project.assets[item.assetId], waveforms[item.assetId])
            is TextItem -> Text(item.text, color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp))
            is OverlayItem -> Text("IMG", color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp))
        }
        if (missing) Box(Modifier.fillMaxSize().background(VDanger.copy(alpha = 0.55f)), Alignment.Center) { Icon(Icons.Default.Warning, null, tint = Color.White) }
        if (selected) {
            TrimHandle(Modifier.align(Alignment.CenterStart), item.id, pxPerSec) { id, d -> onTrimStart(id, d) }
            TrimHandle(Modifier.align(Alignment.CenterEnd), item.id, pxPerSec) { id, d -> onTrimEnd(id, d) }
        }
    }
}

@Composable
private fun TrimHandle(modifier: Modifier, id: String, pxPerSec: Float, onDelta: (String, Long) -> Unit) {
    val latest by rememberUpdatedState(pxPerSec)
    Box(
        modifier.width(18.dp).fillMaxHeight().background(VCyan.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
            .pointerInput(id) {
                detectDragGestures { change, amount ->
                    change.consume()
                    onDelta(id, TimelineMath.pxToUs(amount.x, latest))
                }
            },
    )
}

@Composable
private fun ClipContent(clip: MediaClip, asset: MediaAsset?, widthPx: Float, height: Dp, thumbnails: Thumbnails) {
    val density = LocalDensity.current
    if (asset == null) return
    val heightPx = with(density) { height.toPx() }
    val tileW = (heightPx * asset.displayAspect()).coerceAtLeast(24f)
    val count = ceil(widthPx / tileW).toInt().coerceIn(1, 24)
    Row(Modifier.fillMaxSize()) {
        for (i in 0 until count) {
            val timeUs = clip.sourceInUs + ((i + 0.5) * (clip.sourceOutUs - clip.sourceInUs) / count).toLong()
            val bmp by produceState<ImageBitmap?>(null, asset.uri, timeUs / 200_000L, clip.isStill) {
                value = (if (clip.isStill) thumbnails.image(asset.uri, heightPx.toInt().coerceAtLeast(32))
                else thumbnails.video(asset.uri, timeUs, tileW.toInt(), heightPx.toInt()))?.asImageBitmap()
            }
            Box(Modifier.width(with(density) { tileW.toDp() }).fillMaxHeight()) {
                bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }
    }
    Box(Modifier.fillMaxSize().padding(3.dp), contentAlignment = Alignment.BottomStart) {
        val label = buildString {
            append(formatTimecode(clip.durationUs, false))
            if (!clip.isStill && clip.speed != 1f) append("  ${"%.2g".format(clip.speed)}x")
            if (clip.effects.isNotEmpty()) append("  ✦")
        }
        Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
    }
}

@Composable
private fun AudioContent(clip: AudioClip, asset: MediaAsset?, peaks: FloatArray?) {
    Canvas(Modifier.fillMaxSize().padding(vertical = 3.dp)) {
        val p = peaks
        if (p != null && asset != null && asset.durationUs > 0L) {
            val startFrac = clip.sourceInUs.toDouble() / asset.durationUs
            val lenFrac = (clip.sourceOutUs - clip.sourceInUs).toDouble() / asset.durationUs
            val bars = (size.width / 3f).toInt().coerceAtLeast(1)
            for (b in 0 until bars) {
                val idx = ((startFrac + lenFrac * b / bars) * p.size).toInt().coerceIn(0, p.size - 1)
                val h = (p[idx] * 3f).coerceIn(0.04f, 1f) * size.height
                drawRect(Color(0xFFBFA8FF), Offset(b * 3f, (size.height - h) / 2f), Size(2f, h))
            }
        } else {
            drawLine(Color(0xFFBFA8FF), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2f)
        }
    }
}
