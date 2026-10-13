package app.vortex.studio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.vortex.studio.AppContainer
import app.vortex.studio.ui.editor.EditorScreen
import app.vortex.studio.ui.export.ExportScreen
import app.vortex.studio.ui.home.HomeScreen
import app.vortex.studio.ui.settings.SettingsScreen

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer não fornecido") }

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(onOpenEditor = { nav.navigate("editor/$it") }, onSettings = { nav.navigate("settings") })
        }
        composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            EditorScreen(projectId = id, onBack = { nav.popBackStack() }, onExport = { nav.navigate("export/$id") })
        }
        composable("export/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            ExportScreen(projectId = id, onBack = { nav.popBackStack() })
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
