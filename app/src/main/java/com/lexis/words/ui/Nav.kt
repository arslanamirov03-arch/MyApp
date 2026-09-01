package com.lexis.words.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lexis.words.AppViewModel
import com.lexis.words.StudyMode
import com.lexis.words.StudyViewModel
import com.lexis.words.ui.screens.AboutScreen
import com.lexis.words.ui.screens.BackupScreen
import com.lexis.words.ui.screens.BlockScreen
import com.lexis.words.ui.screens.HomeScreen
import com.lexis.words.ui.screens.ListScreen
import com.lexis.words.ui.screens.SettingsScreen
import com.lexis.words.ui.screens.StudyScreen
import com.lexis.words.ui.theme.ScreenBg

object Routes {
    const val HOME = "home"
    const val BLOCK = "block/{blockId}"
    const val LIST = "list/{listId}"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val ABOUT = "about"
    const val STUDY = "study/{blockId}/{mode}"

    fun block(id: Long) = "block/$id"
    fun list(id: Long) = "list/$id"
    fun study(blockId: Long, mode: StudyMode) = "study/$blockId/${mode.name}"
}

@Composable
fun LexisNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val appViewModel: AppViewModel = viewModel()

    // The host paints the screen colour itself: during a transition there is no
    // darker canvas showing through, which is what read as a "jump" between screens.
    Box(modifier.background(ScreenBg)) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // Short, subtle slide + fade — the same feel as the prototype's screen-in
            // animation, instead of Navigation's long default cross-fade.
            enterTransition = {
                slideInHorizontally(tween(230, easing = FastOutSlowInEasing)) { it / 12 } +
                    fadeIn(tween(190, easing = FastOutSlowInEasing))
            },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(190)) },
            popExitTransition = {
                slideOutHorizontally(tween(230, easing = FastOutSlowInEasing)) { it / 12 } +
                    fadeOut(tween(170))
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(nav = navController, vm = appViewModel)
            }
            composable(
                Routes.BLOCK,
                arguments = listOf(navArgument("blockId") { type = NavType.LongType })
            ) { entry ->
                val blockId = entry.arguments!!.getLong("blockId")
                BlockScreen(blockId = blockId, nav = navController, vm = appViewModel)
            }
            composable(
                Routes.LIST,
                arguments = listOf(navArgument("listId") { type = NavType.LongType })
            ) { entry ->
                val listId = entry.arguments!!.getLong("listId")
                ListScreen(listId = listId, nav = navController, vm = appViewModel)
            }
            composable(Routes.SETTINGS) { SettingsScreen(nav = navController, vm = appViewModel) }
            composable(Routes.BACKUP) { BackupScreen(nav = navController, vm = appViewModel) }
            composable(Routes.ABOUT) { AboutScreen(nav = navController) }
            composable(
                Routes.STUDY,
                arguments = listOf(
                    navArgument("blockId") { type = NavType.LongType },
                    navArgument("mode") { type = NavType.StringType },
                )
            ) { entry ->
                val blockId = entry.arguments!!.getLong("blockId")
                val mode = StudyMode.valueOf(entry.arguments!!.getString("mode")!!)
                val studyVm: StudyViewModel = viewModel(viewModelStoreOwner = entry)
                val settings by appViewModel.settings.collectAsState()
                StudyScreen(
                    blockId = blockId, mode = mode, nav = navController, vm = studyVm,
                    settings = settings, appVm = appViewModel,
                )
            }
        }
    }
}
