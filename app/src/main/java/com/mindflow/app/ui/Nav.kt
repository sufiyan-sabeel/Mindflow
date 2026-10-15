package com.mindflow.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mindflow.app.ui.screens.DailyChallengesScreen
import com.mindflow.app.ui.screens.HomeScreen
import com.mindflow.app.ui.screens.LeaderboardScreen
import com.mindflow.app.ui.screens.OnboardingScreen
import com.mindflow.app.ui.screens.PlayScreen
import com.mindflow.app.ui.screens.ProfileScreen
import com.mindflow.app.ui.screens.SettingsScreen
import com.mindflow.app.ui.screens.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PLAY = "play/{mode}/{levelId}/{dailyId}"
    const val LEADERBOARD = "leaderboard"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val DAILY = "daily"

    fun play(mode: String, levelId: Int, dailyId: String = ""): String = "play/$mode/$levelId/$dailyId"
}

@Composable
fun MindflowRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val state by vm.state.collectAsState()

    NavHost(navController = nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    val target = if (vm.state.value.onboarded) Routes.HOME else Routes.ONBOARDING
                    nav.navigate(target) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    vm.completeOnboarding()
                    nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onOpenPlay = { mode, levelId, dailyId -> nav.navigate(Routes.play(mode.name, levelId, dailyId ?: "")) },
                onOpenLeaderboard = { nav.navigate(Routes.LEADERBOARD) },
                onOpenProfile = { nav.navigate(Routes.PROFILE) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenDaily = { nav.navigate(Routes.DAILY) }
            )
        }
        composable(
            route = Routes.PLAY,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("levelId") { type = NavType.IntType },
                navArgument("dailyId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val mode = entry.arguments?.getString("mode") ?: "MATH_SPRINT"
            val levelId = entry.arguments?.getInt("levelId") ?: -1
            val dailyId = entry.arguments?.getString("dailyId").orEmpty()
            PlayScreen(
                vm = vm,
                modeId = mode,
                levelId = levelId,
                dailyId = dailyId.ifBlank { null },
                onExit = { nav.popBackStack() }
            )
        }
        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(state = state, onBack = { nav.popBackStack() })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(vm = vm, state = state, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(vm = vm, state = state, onBack = { nav.popBackStack() })
        }
        composable(Routes.DAILY) {
            DailyChallengesScreen(
                state = state,
                onPlay = { mode, dailyId -> nav.navigate(Routes.play(mode.name, -1, dailyId)) },
                onBack = { nav.popBackStack() }
            )
        }
    }
}
