package app.vortex.studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val VBg = Color(0xFF0B0B12)
val VSurface = Color(0xFF14141F)
val VSurface2 = Color(0xFF1C1C2B)
val VViolet = Color(0xFF7C4DFF)
val VCyan = Color(0xFF18E0F0)
val VText = Color(0xFFECECF4)
val VMuted = Color(0xFF8A8AA3)
val VDanger = Color(0xFFFF5C7A)
val VOutline = Color(0xFF2A2A3D)

val VortexGradient: Brush = Brush.horizontalGradient(listOf(VViolet, VCyan))

private val Scheme = darkColorScheme(
    primary = VViolet, onPrimary = Color.White,
    secondary = VCyan, onSecondary = Color.Black,
    background = VBg, onBackground = VText,
    surface = VSurface, onSurface = VText,
    surfaceVariant = VSurface2, onSurfaceVariant = VMuted,
    error = VDanger, outline = VOutline,
)

private val VortexShapes = Shapes(
    small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp),
)

/** Tema escuro único do Vortex: violeta elétrico e azul-ciano sobre fundo quase preto. */
@Composable
fun VortexTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography(), shapes = VortexShapes, content = content)
}
