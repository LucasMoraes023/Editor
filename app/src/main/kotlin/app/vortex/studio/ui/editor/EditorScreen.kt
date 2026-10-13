@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package app.vortex.studio.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.vortex.studio.R
import app.vortex.studio.core.export.OverlayPlan
import app.vortex.studio.core.model.*
import app.vortex.studio.engine.TextBitmapRenderer
import app.vortex.studio.ui.GradientButton
import app.vortex.studio.ui.LocalContainer
import app.vortex.studio.ui.ToolButton
import app.vortex.studio.ui.theme.VCyan
import app.vortex.studio.ui.theme.VMuted
import app.vortex.studio.ui.theme.VSurface
import app.vortex.studio.ui.theme.VSurface2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(projectId: String, onBack: () -> Unit, onExport: () -> Unit) {
    val container = LocalContainer.current
    val vm: EditorViewModel = viewModel(key = projectId, factory = viewModelFactory { initializer { EditorViewModel(container, projectId) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val waveforms by vm.waveforms.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingAudioKind by remember { mutableStateOf(TrackKind.MUSIC) }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.pauseAndSave() }
    LaunchedEffect(ui.message?.token) {
        ui.message?.let { m -> snackbar.showSnackbar(context.getString(m.res, m.arg ?: "")); vm.messageShown() }
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { vm.addMedia(it) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addAudio(it, pendingAudioKind) }
    val overlayPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::addOverlay) }
    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> ui.missingAssets.firstOrNull()?.let { vm.relink(it, u) } }
    }
    fun addMedia() = mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    fun addAudio(kind: TrackKind) { pendingAudioKind = kind; audioPicker.launch(arrayOf("audio/*")) }
    fun addOverlay() = overlayPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val project = ui.project
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        if (project == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (ui.loadFailed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.editor_load_failed))
                    TextButton(onBack) { Text(stringResource(R.string.action_back)) }
                } else CircularProgressIndicator(color = VCyan)
            }
            return@Box
        }
        Column(Modifier.fillMaxSize()) {
            // ----- barra superior -----
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                Text(project.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                IconButton(vm::undo, enabled = ui.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.action_undo)) }
                IconButton(vm::redo, enabled = ui.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.action_redo)) }
                GradientButton(stringResource(R.string.action_export), { vm.saveThen(onExport) }, Modifier.padding(start = 4.dp, end = 8.dp), enabled = project.mainTrack.items.isNotEmpty())
            }
            // ----- prévia -----
            Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = 140.dp)) {
                PreviewArea(ui, vm, onAddMedia = ::addMedia, onRelink = { relinkPicker.launch(arrayOf("video/*", "image/*", "audio/*")) })
                if (ui.busy) Box(Modifier.fillMaxSize().background(Color(0x66000000)), Alignment.Center) { CircularProgressIndicator(color = VCyan) }
            }
            // ----- transporte -----
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${formatTimecode(ui.playheadUs)} / ${formatTimecode(project.durationUs)}", fontSize = 13.sp, color = VMuted, modifier = Modifier.weight(1f))
                IconButton(vm::togglePlay) {
                    Icon(if (ui.playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(R.string.action_play), tint = VCyan, modifier = Modifier.size(32.dp))
                }
                IconButton({ vm.setZoom(ui.pxPerSec / 1.4f) }) { Icon(Icons.Default.ZoomOut, stringResource(R.string.action_zoom_out)) }
                IconButton({ vm.setZoom(ui.pxPerSec * 1.4f) }) { Icon(Icons.Default.ZoomIn, stringResource(R.string.action_zoom_in)) }
                IconButton(::addMedia) { Icon(Icons.Default.Add, stringResource(R.string.action_add_media), tint = VCyan) }
            }
            // ----- linha do tempo -----
            TimelineView(
                project = project, selectedId = ui.selectedId, playheadUs = ui.playheadUs, pxPerSec = ui.pxPerSec,
                missing = ui.missingAssets, waveforms = waveforms, thumbnails = vm.thumbnails,
                onSeek = vm::seek, onSelect = vm::select, onZoom = vm::setZoom, onMoveMain = vm::moveMain, onMoveFree = vm::moveFree,
                onTrimStart = vm::trimStartBy, onTrimEnd = vm::trimEndBy,
                modifier = Modifier.fillMaxWidth().height(176.dp),
            )
            // ----- painel da ferramenta -----
            ui.tool?.let { tool ->
                Column(Modifier.fillMaxWidth().heightIn(max = 230.dp).background(VSurface).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    ToolPanel(tool, ui, vm, onAddAudio = ::addAudio, onAddOverlay = ::addOverlay)
                }
            }
            // ----- ferramentas -----
            LazyRow(Modifier.fillMaxWidth().background(VSurface2), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                items(Tool.values().toList()) { t ->
                    ToolButton(toolIcon(t), stringResource(toolLabel(t)), ui.tool == t, { vm.selectTool(t) })
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
}

private fun toolIcon(t: Tool) = when (t) {
    Tool.CUT -> Icons.Default.ContentCut
    Tool.SPEED -> Icons.Default.Speed
    Tool.AUDIO -> Icons.Default.VolumeUp
    Tool.EFFECTS -> Icons.Default.AutoAwesome
    Tool.TEXT -> Icons.Default.TextFields
    Tool.IMAGE -> Icons.Default.Image
    Tool.TRANSFORM -> Icons.Default.Crop
    Tool.TRANSITION -> Icons.Default.Animation
}

private fun toolLabel(t: Tool) = when (t) {
    Tool.CUT -> R.string.tool_cut
    Tool.SPEED -> R.string.tool_speed
    Tool.AUDIO -> R.string.tool_audio
    Tool.EFFECTS -> R.string.tool_effects
    Tool.TEXT -> R.string.tool_text
    Tool.IMAGE -> R.string.tool_image
    Tool.TRANSFORM -> R.string.tool_transform
    Tool.TRANSITION -> R.string.tool_transition
}

@Composable
private fun PreviewArea(ui: EditorUi, vm: EditorViewModel, onAddMedia: () -> Unit, onRelink: () -> Unit) {
    val project = ui.project ?: return
    val density = LocalDensity.current
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = vm.preview.player
                }
            },
            update = { it.player = vm.preview.player },
            modifier = Modifier.fillMaxSize(),
        )

        val aspect = project.canvas.aspect.ratioW.toFloat() / project.canvas.aspect.ratioH
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val frameW = if (boxW / boxH > aspect) boxH * aspect else boxW
        val frameH = frameW / aspect
        val plan = ui.plan

        // Imagens sobrepostas (logotipos) são decodificadas uma vez, na largura do quadro da prévia.
        val logos = remember { mutableStateMapOf<String, ImageBitmap>() }
        val imageOverlays = plan?.overlays?.filterIsInstance<OverlayPlan.Image>().orEmpty()
        LaunchedEffect(imageOverlays.map { it.item.id to it.item.scale }, frameW) {
            imageOverlays.forEach { o ->
                val key = "${o.item.id}|${o.uri}|${o.item.scale}|${frameW.toInt()}"
                if (!logos.containsKey(key)) {
                    val bmp = withContext(Dispatchers.IO) { TextBitmapRenderer.decodeScaled(context, Uri.parse(o.uri), (frameW * o.item.scale).toInt().coerceAtLeast(2)) }
                    if (bmp != null) logos[key] = bmp.asImageBitmap()
                }
            }
        }
        val textCache = remember { HashMap<String, ImageBitmap>() }
        val selected = ui.selected
        Canvas(
            Modifier.align(Alignment.Center).size(with(density) { frameW.toDp() }, with(density) { frameH.toDp() })
                .pointerInput(ui.selectedId, selected is TextItem || selected is OverlayItem) {
                    if (selected is TextItem || selected is OverlayItem) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            vm.nudge(drag.x / (size.width / 2f), -drag.y / (size.height / 2f))
                        }
                    }
                },
        ) {
            val t = ui.playheadUs
            plan?.overlays?.forEach { o ->
                if (t < o.startUs || t > o.startUs + o.durationUs) return@forEach
                when (o) {
                    is OverlayPlan.Text -> {
                        if (textCache.size > 24) textCache.clear()
                        val key = "${o.item.id}|${o.item.text}|${o.item.style}|${size.width.toInt()}"
                        val img = textCache.getOrPut(key) { TextBitmapRenderer.render(o.item, size.width.toInt(), size.height.toInt()).asImageBitmap() }
                        val st = OverlayAnimation.evaluate(t - o.startUs, o.durationUs, o.item.animIn, o.item.animOut, o.item.animDurationUs)
                        drawOverlay(img, o.item.x, o.item.y, o.item.rotationDeg, 1f, st)
                    }
                    is OverlayPlan.Image -> {
                        val key = "${o.item.id}|${o.uri}|${o.item.scale}|${frameW.toInt()}"
                        val img = logos[key] ?: return@forEach
                        val st = OverlayAnimation.evaluate(t - o.startUs, o.durationUs, o.item.animIn, o.item.animOut, o.item.animDurationUs)
                        drawOverlay(img, o.item.x, o.item.y, o.item.rotationDeg, o.item.opacity, st)
                    }
                }
            }
        }

        if (project.mainTrack.items.isEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.VideoLibrary, null, tint = VMuted, modifier = Modifier.size(40.dp))
                Text(stringResource(R.string.editor_empty_hint), color = VMuted, fontSize = 13.sp)
                GradientButton(stringResource(R.string.action_add_media), onAddMedia, icon = Icons.Default.Add)
            }
        }
        if (ui.missingAssets.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xCC8A1F36)).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.msg_missing_media), Modifier.weight(1f), fontSize = 12.sp, color = Color.White)
                TextButton(onRelink) { Text(stringResource(R.string.action_relink), color = Color.White) }
            }
        }
        if (ui.playerError) {
            Text(stringResource(R.string.msg_player_error), Modifier.align(Alignment.BottomCenter).padding(8.dp).background(Color(0xCC000000)).padding(6.dp), fontSize = 12.sp)
        }
    }
}

/** Desenha um bitmap centrado na posição normalizada (-1..1) com a animação de entrada/saída aplicada. */
private fun DrawScope.drawOverlay(img: ImageBitmap, x: Float, y: Float, rotationDeg: Float, baseAlpha: Float, st: OverlayAnimState) {
    val cx = size.width / 2f * (1f + x + st.dx)
    val cy = size.height / 2f * (1f - (y + st.dy))
    withTransform({
        translate(cx, cy)
        rotate(-rotationDeg, Offset.Zero)
        scale(st.scale, st.scale, Offset.Zero)
    }) {
        drawImage(img, topLeft = Offset(-img.width / 2f, -img.height / 2f), alpha = (baseAlpha * st.alpha).coerceIn(0f, 1f))
    }
}
