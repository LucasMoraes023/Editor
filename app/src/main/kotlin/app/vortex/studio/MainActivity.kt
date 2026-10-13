package app.vortex.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.vortex.studio.ui.AppNav
import app.vortex.studio.ui.LocalContainer
import app.vortex.studio.ui.theme.VortexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as VortexApp).container
        setContent {
            VortexTheme {
                CompositionLocalProvider(LocalContainer provides container) { AppNav() }
            }
        }
    }
}
