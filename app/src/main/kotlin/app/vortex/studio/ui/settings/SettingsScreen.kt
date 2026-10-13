@file:OptIn(ExperimentalMaterial3Api::class)

package app.vortex.studio.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vortex.studio.R
import app.vortex.studio.core.export.ExportQuality
import app.vortex.studio.core.export.ExportResolution
import app.vortex.studio.data.PreviewQuality
import app.vortex.studio.ui.ChipRow
import app.vortex.studio.ui.LocalContainer
import app.vortex.studio.ui.label
import app.vortex.studio.ui.theme.VCyan
import app.vortex.studio.ui.theme.VMuted
import app.vortex.studio.ui.theme.VSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val s = c.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewQuality by remember { mutableStateOf(s.previewQuality) }
    var previewEffects by remember { mutableStateOf(s.previewEffects) }
    var resolution by remember { mutableStateOf(s.defaultResolution) }
    var fps by remember { mutableStateOf(s.defaultFps) }
    var quality by remember { mutableStateOf(s.defaultQuality) }
    var cacheBytes by remember { mutableStateOf(0L) }
    suspend fun measure(): Long = withContext(Dispatchers.IO) { context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    LaunchedEffect(Unit) { cacheBytes = measure() }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
            Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section(R.string.settings_preview) {
                ChipRow(PreviewQuality.values().toList(), previewQuality, { stringResource(it.label()) }, { previewQuality = it; s.previewQuality = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_preview_effects), Modifier.weight(1f))
                    Switch(previewEffects, { previewEffects = it; s.previewEffects = it })
                }
                Text(stringResource(R.string.settings_preview_hint), color = VMuted, fontSize = 12.sp)
            }
            Section(R.string.settings_export) {
                ChipRow(ExportResolution.values().toList(), resolution, { when (it) { ExportResolution.P720 -> "720p"; ExportResolution.P1080 -> "1080p"; ExportResolution.P2160 -> "4K" } }, { resolution = it; s.defaultResolution = it })
                ChipRow(listOf(30, 60), fps, { "$it fps" }, { fps = it; s.defaultFps = it })
                ChipRow(ExportQuality.values().toList(), quality, { stringResource(it.label()) }, { quality = it; s.defaultQuality = it })
            }
            Section(R.string.settings_cache) {
                Text(stringResource(R.string.settings_cache_size, Formatter.formatShortFileSize(context, cacheBytes)), color = VMuted)
                OutlinedButton({
                    scope.launch {
                        c.thumbnails.clear()
                        if (!c.exportManager.isBusy) withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                        cacheBytes = measure()
                    }
                }) { Text(stringResource(R.string.settings_cache_clear)) }
                Text(stringResource(R.string.settings_cache_hint), color = VMuted, fontSize = 12.sp)
            }
            Section(R.string.settings_privacy) {
                Text(stringResource(R.string.privacy_text), fontSize = 13.sp)
            }
            Section(R.string.settings_about) {
                Text(stringResource(R.string.about_version, version), fontSize = 13.sp)
                Text(stringResource(R.string.about_tech), color = VMuted, fontSize = 12.sp)
                Text(stringResource(R.string.about_local_vs_external), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Section(@androidx.annotation.StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(VSurface, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), color = VCyan, fontWeight = FontWeight.SemiBold)
        content()
    }
}
