@file:OptIn(ExperimentalMaterial3Api::class)

package app.vortex.studio.ui.export

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.vortex.studio.R
import app.vortex.studio.core.export.*
import app.vortex.studio.engine.ExportState
import app.vortex.studio.ui.GradientButton
import app.vortex.studio.ui.LocalContainer
import app.vortex.studio.ui.label
import app.vortex.studio.ui.theme.VCyan
import app.vortex.studio.ui.theme.VMuted
import app.vortex.studio.ui.theme.VSurface
import app.vortex.studio.ui.theme.VSurface2

@Composable
fun ExportScreen(projectId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val vm: ExportViewModel = viewModel(key = "export-$projectId", factory = viewModelFactory { initializer { ExportViewModel(container, projectId) } })
    val project by vm.project.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val busy = state is ExportState.Preparing || state is ExportState.Running

    // Mantém a tela acesa durante a exportação (evita interrupções por bloqueio de tela).
    DisposableEffect(busy) {
        val window = (view.context as? android.app.Activity)?.window
        if (busy) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.start() }
    fun startExport() {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.start()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
            Text(stringResource(R.string.export_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        val p = project
        if (p == null) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = VCyan) }; return@Column }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val plan = remember(p, settings) { ExportPlanner.plan(p, settings) { true } }
            val (w, h) = ExportMath.outputSize(p.canvas.aspect, settings.resolution)
            val bitrate = ExportMath.videoBitrate(w, h, settings.fps, settings.quality)
            val estimate = ExportMath.estimateBytes(p.durationUs, bitrate)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold)
                    Text("${w}×${h} • ${settings.fps} fps • H.264 + AAC (MP4)", color = VMuted, fontSize = 13.sp)
                    Text(stringResource(R.string.export_estimate, Formatter.formatShortFileSize(context, estimate), Formatter.formatShortFileSize(context, caps.freeBytes)), color = VMuted, fontSize = 13.sp)
                }
            }

            Text(stringResource(R.string.export_resolution), fontWeight = FontWeight.SemiBold)
            val resOptions = remember(p.canvas, settings.fps, caps) { ExportMath.resolutionOptions(p.canvas.aspect, settings.fps, caps) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                resOptions.forEach { o ->
                    FilterChip(settings.resolution == o.value, { vm.update { s -> s.copy(resolution = o.value) } }, { Text(resLabel(o.value)) }, enabled = o.enabled && !busy)
                }
            }
            Text(stringResource(R.string.export_fps), fontWeight = FontWeight.SemiBold)
            val fpsOptions = remember(p.canvas, settings.resolution, caps) { ExportMath.fpsOptions(p.canvas.aspect, settings.resolution, caps) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fpsOptions.forEach { o ->
                    FilterChip(settings.fps == o.value, { vm.update { s -> s.copy(fps = o.value) } }, { Text("${o.value} fps") }, enabled = o.enabled && !busy)
                }
            }
            if (resOptions.any { !it.enabled } || fpsOptions.any { !it.enabled }) Text(stringResource(R.string.export_unsupported_hint), color = VMuted, fontSize = 12.sp)
            Text(stringResource(R.string.export_quality), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportQuality.values().forEach { q ->
                    FilterChip(settings.quality == q, { vm.update { s -> s.copy(quality = q) } }, { Text(stringResource(q.label())) }, enabled = !busy)
                }
            }
            if (settings.fps == 60) Text(stringResource(R.string.export_60fps_hint), color = VMuted, fontSize = 12.sp)

            // Avisos e erros reais do planejador.
            val issues = when (plan) { is PlanResult.Ok -> plan.warnings; is PlanResult.Failed -> plan.issues }
            issues.forEach { i ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, null, tint = VCyan, modifier = Modifier.size(18.dp))
                    Text(stringResource(i.code.label()), fontSize = 13.sp)
                }
            }
            val storageOk = ExportMath.hasEnoughStorage(estimate * 2, caps.freeBytes)
            if (!storageOk) Text(stringResource(R.string.err_storage), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)

            // Estado real da exportação.
            when (val s = state) {
                is ExportState.Idle -> Unit
                is ExportState.Preparing -> StatusCard { Text(stringResource(R.string.export_preparing)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = VCyan) }
                is ExportState.Running -> StatusCard {
                    Text(stringResource(R.string.export_running, s.progress))
                    LinearProgressIndicator(progress = { s.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = VCyan)
                    OutlinedButton(vm::cancel) { Text(stringResource(R.string.export_cancel)) }
                }
                is ExportState.Success -> StatusCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Check, null, tint = VCyan); Text(stringResource(R.string.export_done), fontWeight = FontWeight.SemiBold)
                    }
                    Text("${s.displayName} • ${Formatter.formatShortFileSize(context, s.sizeBytes)} • ${s.tookMs / 1000} s", color = VMuted, fontSize = 12.sp)
                    s.uri?.let { uri ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({
                                try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                                catch (e: ActivityNotFoundException) { Toast.makeText(context, R.string.export_no_player, Toast.LENGTH_SHORT).show() }
                            }) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.export_open)) }
                            OutlinedButton({
                                val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.export_share)))
                            }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.export_share)) }
                        }
                    }
                }
                is ExportState.Failed -> StatusCard {
                    Text(stringResource(R.string.export_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(s.code.label()), fontSize = 13.sp)
                    s.detail?.let { Text(it, color = VMuted, fontSize = 11.sp) }
                }
                is ExportState.Cancelled -> StatusCard { Text(stringResource(R.string.export_cancelled)) }
            }
        }

        val canStart = !busy && project?.mainTrack?.items?.isNotEmpty() == true
        GradientButton(
            stringResource(if (state is ExportState.Success || state is ExportState.Failed || state is ExportState.Cancelled) R.string.export_again else R.string.export_start),
            { startExport() }, Modifier.fillMaxWidth().padding(16.dp), enabled = canStart,
        )
    }
}

@Composable
private fun StatusCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(VSurface2, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

private fun resLabel(r: ExportResolution) = when (r) { ExportResolution.P720 -> "720p"; ExportResolution.P1080 -> "1080p"; ExportResolution.P2160 -> "4K" }
