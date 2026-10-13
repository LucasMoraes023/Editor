package app.vortex.studio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Abertura do app e criação de projeto (fluxo da tela inicial até o editor). */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun appOpens_showingHome() {
        rule.onNodeWithText("Vortex Video Studio").assertIsDisplayed()
        rule.onAllNodesWithText("Novo projeto")[0].assertIsDisplayed()
    }

    @Test fun newProject_fromFormatSheet_opensEditor() {
        rule.onAllNodesWithText("Novo projeto")[0].performClick()
        rule.onNodeWithText("Instagram Reels").performClick()
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithText("Adicionar mídia").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Adicionar mídia")[0].assertIsDisplayed()
    }
}
