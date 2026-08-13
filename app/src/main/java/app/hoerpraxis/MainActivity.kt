package app.hoerpraxis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.hoerpraxis.ui.library.LibraryScreen
import app.hoerpraxis.ui.player.PlayerScreen
import app.hoerpraxis.ui.settings.SettingsScreen
import app.hoerpraxis.ui.theme.HoerpraxisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HoerpraxisTheme {
                AppNav(rememberNavController())
            }
        }
    }
}

@Composable
private fun AppNav(nav: NavHostController) {
    val spec = tween<androidx.compose.ui.unit.IntOffset>(350)
    val fade = tween<Float>(350)
    NavHost(
        navController = nav,
        startDestination = "library",
        enterTransition = { slideInHorizontally(spec) { it / 4 } + fadeIn(fade) },
        exitTransition = { slideOutHorizontally(spec) { -it / 4 } + fadeOut(fade) },
        popEnterTransition = { slideInHorizontally(spec) { -it / 4 } + fadeIn(fade) },
        popExitTransition = { slideOutHorizontally(spec) { it / 4 } + fadeOut(fade) },
    ) {
        composable("library") {
            LibraryScreen(
                onOpenItem = { id -> nav.navigate("player/$id") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("player/{id}") { backStack ->
            val id = backStack.arguments?.getString("id") ?: return@composable
            PlayerScreen(itemId = id, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
