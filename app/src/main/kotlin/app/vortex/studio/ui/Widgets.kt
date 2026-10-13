@file:OptIn(ExperimentalMaterial3Api::class)

package app.vortex.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vortex.studio.ui.theme.VCyan
import app.vortex.studio.ui.theme.VMuted
import app.vortex.studio.ui.theme.VOutline
import app.vortex.studio.ui.theme.VSurface2
import app.vortex.studio.ui.theme.VortexGradient

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(shape)
            .background(brush = if (enabled) VortexGradient else androidx.compose.ui.graphics.SolidColor(VSurface2), shape = shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(text, color = if (enabled) Color.White else VMuted, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Botão de ação compacto (ícone + texto) com estados claros: normal, desabilitado. */
@Composable
fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    val shape = RoundedCornerShape(12.dp)
    val tint = when { !enabled -> VMuted.copy(alpha = 0.5f); danger -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface }
    Row(
        modifier.clip(shape).background(VSurface2, shape).border(1.dp, VOutline, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, fontSize = 13.sp)
    }
}

@Composable
fun ToolButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = if (selected) VCyan else MaterialTheme.colorScheme.onSurface
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = tint, fontSize = 11.sp)
    }
}

@Composable
fun LabeledSlider(
    label: String, valueText: String, value: Float, onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f, enabled: Boolean = true, modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = VCyan, activeTrackColor = VCyan, inactiveTrackColor = VOutline),
        )
    }
}

@Composable
fun <T> ChipRow(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit, enabled: (T) -> Boolean = { true }) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, enabled = enabled(o), label = { Text(label(o)) })
        }
    }
}
