@file:OptIn(ExperimentalMaterial3Api::class)

package app.vortex.studio.ui.home

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.vortex.studio.R
import app.vortex.studio.core.model.EffectCategory
import app.vortex.studio.core.model.EffectType
import app.vortex.studio.core.model.formatTimecode
import app.vortex.studio.core.templates.TemplateCategory
import app.vortex.studio.core.templates.TemplateSpec
import app.vortex.studio.core.templates.Templates
import app.vortex.studio.data.ProjectSummary
import app.vortex.studio.ui.GradientButton
import app.vortex.studio.ui.LocalContainer
import app.vortex.studio.ui.label
import app.vortex.studio.ui.templateLabel
import app.vortex.studio.ui.theme.VCyan
import app.vortex.studio.ui.theme.VMuted
import app.vortex.studio.ui.theme.VSurface
import app.vortex.studio.ui.theme.VSurface2
import app.vortex.studio.ui.theme.VortexGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(onOpenEditor: (String) -> Unit, onSettings: () -> Unit) {
    val container = LocalContainer.current
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container) } })
    val projects by vm.projects.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    var showNew by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }
    val defaultName = stringResource(R.string.default_project_name, projects.size + 1)
    val copySuffix = stringResource(R.string.project_copy_suffix)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) vm.createFromMedia(uris, defaultName, onOpenEditor)
    }
    fun openGallery() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.home_tagline), color = VMuted, fontSize = 13.sp)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings_title)) }
                }
            }
            if (vm.recoveredProjectId != null) item {
                Banner(stringResource(R.string.home_recovered), stringResource(R.string.home_open)) { onOpenEditor(vm.recoveredProjectId) }
            }
            if (vm.exportWasInterrupted) item { Banner(stringResource(R.string.home_export_interrupted), null) {} }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientButton(stringResource(R.string.home_new_project), { showNew = true }, Modifier.fillMaxWidth(), Icons.Default.Add)
                    OutlinedButton(
                        onClick = { projects.firstOrNull()?.let { onOpenEditor(it.id) } }, enabled = projects.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.home_continue)) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(Icons.Default.PhotoLibrary, stringResource(R.string.home_gallery), Modifier.weight(1f)) { openGallery() }
                    Tile(Icons.Default.VideoLibrary, stringResource(R.string.home_templates), Modifier.weight(1f)) { showNew = true }
                    Tile(Icons.Default.AutoAwesome, stringResource(R.string.home_effects), Modifier.weight(1f)) { showLibrary = true }
                }
            }
            item { Text(stringResource(R.string.home_templates), fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Templates.all.filter { it.category != TemplateCategory.BLANK }) { spec ->
                        TemplateCard(spec) { vm.createFromTemplate(spec, defaultName, onOpenEditor) }
                    }
                }
            }
            item { Text(stringResource(R.string.home_recent), fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
            if (projects.isEmpty()) item { EmptyState { showNew = true } }
            items(projects, key = { it.id }) { p ->
                ProjectRow(
                    p, onOpen = { onOpenEditor(p.id) }, onRename = { renaming = p },
                    onDuplicate = { vm.duplicate(p.id, p.name + copySuffix) }, onDelete = { deleting = p },
                )
            }
        }
        if (busy) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xAA000000)), Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = VCyan)
        }
    }

    if (showNew) NewProjectSheet(
        onDismiss = { showNew = false },
        onPick = { spec -> showNew = false; vm.createFromTemplate(spec, defaultName, onOpenEditor) },
        onGallery = { showNew = false; openGallery() },
    )
    if (showLibrary) EffectLibrarySheet { showLibrary = false }
    renaming?.let { p ->
        var text by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.project_rename)) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(stringResource(R.string.project_name_label)) }) },
            confirmButton = { TextButton({ vm.rename(p.id, text); renaming = null }) { Text(stringResource(R.string.dialog_ok)) } },
            dismissButton = { TextButton({ renaming = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.project_delete)) },
            text = { Text(stringResource(R.string.project_delete_confirm, p.name)) },
            confirmButton = { TextButton({ vm.delete(p.id); deleting = null }) { Text(stringResource(R.string.project_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

@Composable
private fun Banner(text: String, action: String?, onAction: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = VSurface2, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Warning, null, tint = VCyan)
            Text(text, Modifier.weight(1f), fontSize = 13.sp)
            if (action != null) TextButton(onAction) { Text(action) }
        }
    }
}

@Composable
private fun Tile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = VSurface, modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = VCyan)
            Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TemplateCard(spec: TemplateSpec, onClick: () -> Unit) {
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(16.dp)).background(VSurface).clickable(onClick = onClick).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val ratio = spec.aspect.ratioW.toFloat() / spec.aspect.ratioH
        Box(
            Modifier.height(96.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(VortexGradient),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.height(64.dp).width((64 * ratio).coerceIn(28f, 110f).dp).clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color(0x66000000)))
        }
        Text(stringResource(templateLabel(spec.id)), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(spec.category.label()), color = VMuted, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyState(onNew: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Movie, null, tint = VMuted, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.home_empty_title), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.home_empty_text), color = VMuted, fontSize = 13.sp)
        TextButton(onNew) { Text(stringResource(R.string.home_new_project)) }
    }
}

@Composable
private fun ProjectRow(p: ProjectSummary, onOpen: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(VSurface).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, p.thumbnail?.path, p.modifiedAtMs) {
            value = p.thumbnail?.let { f -> withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.path)?.asImageBitmap() } }
        }
        Box(Modifier.size(width = 96.dp, height = 60.dp).clip(RoundedCornerShape(10.dp)).background(VSurface2), Alignment.Center) {
            val t = thumb
            if (t != null) Image(t, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.PlayArrow, null, tint = VMuted)
        }
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val date = DateUtils.formatDateTime(androidx.compose.ui.platform.LocalContext.current, p.modifiedAtMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
            Text("$date  •  ${formatTimecode(p.durationUs, false)}  •  ${stringResource(R.string.home_clips_count, p.clipCount)}", color = VMuted, fontSize = 12.sp)
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.project_rename)) }, { menu = false; onRename() })
                DropdownMenuItem({ Text(stringResource(R.string.project_duplicate)) }, { menu = false; onDuplicate() })
                DropdownMenuItem({ Text(stringResource(R.string.project_delete), color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun NewProjectSheet(onDismiss: () -> Unit, onPick: (TemplateSpec) -> Unit, onGallery: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VSurface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.new_project_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(stringResource(R.string.new_project_formats), color = VMuted, fontSize = 13.sp)
            Templates.all.filter { it.category == TemplateCategory.BLANK }.forEach { spec ->
                SheetRow(stringResource(templateLabel(spec.id)), "${stringResource(spec.aspect.label())} • ${spec.fps} fps") { onPick(spec) }
            }
            Text(stringResource(R.string.home_templates), color = VMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Templates.all.filter { it.category != TemplateCategory.BLANK }.forEach { spec ->
                SheetRow(stringResource(templateLabel(spec.id)), "${stringResource(spec.category.label())} • ${stringResource(spec.aspect.label())}") { onPick(spec) }
            }
            OutlinedButton(onGallery, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.new_project_from_gallery)) }
        }
    }
}

@Composable
private fun SheetRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VSurface2).clickable(onClick = onClick).padding(12.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = VMuted, fontSize = 12.sp)
    }
}

@Composable
private fun EffectLibrarySheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VSurface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.library_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(stringResource(R.string.library_hint), color = VMuted, fontSize = 13.sp)
            EffectCategory.values().forEach { cat ->
                Text(stringResource(cat.label()), color = VCyan, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                val labels = mutableListOf<String>()
                EffectType.values().filter { it.category == cat }.forEach { effect -> labels.add(stringResource(effect.label())) }
                Text(labels.joinToString("  •  "), fontSize = 14.sp)
            }
        }
    }
}
