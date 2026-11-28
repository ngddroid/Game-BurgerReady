package com.ngstudio.burgerready.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.ui.screens.AchievementsScreen
import com.ngstudio.burgerready.ui.screens.GameScreen
import com.ngstudio.burgerready.ui.screens.HomeScreen
import com.ngstudio.burgerready.ui.screens.LegalScreen
import com.ngstudio.burgerready.ui.screens.OnboardingScreen
import com.ngstudio.burgerready.ui.screens.RecipeBookScreen
import com.ngstudio.burgerready.ui.screens.SettingsScreen
import com.ngstudio.burgerready.ui.screens.SplashScreen
import com.ngstudio.burgerready.ui.theme.BV

sealed class Route {
    data object Splash : Route()
    data object Onboarding : Route()
    data class Home(val tab: Int = 2) : Route()
    data class Game(val store: String) : Route()
    data object Settings : Route()
    data class Legal(val doc: String) : Route()
    data object RecipeBook : Route()
    data object Achievements : Route()
    data object VoyageBook : Route()
    data object WorldMap : Route()
}

class Navigator {
    val stack = mutableStateListOf<Route>(Route.Splash)
    val current: Route get() = stack.last()
    fun push(r: Route) { stack.add(r) }
    fun replace(r: Route) { stack.removeAt(stack.size - 1); stack.add(r) }
    fun resetTo(r: Route) { stack.clear(); stack.add(r) }
    fun back(): Boolean { if (stack.size > 1) { stack.removeAt(stack.size - 1); return true }; return false }
}

@Composable
fun AppRoot() {
    val nav = remember { Navigator() }
    var homeTab by remember { mutableStateOf(2) }
    val route = nav.current
    var confirmExit by remember { mutableStateOf(false) }
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    BackHandler(enabled = route !is Route.Home && route !is Route.Splash && route !is Route.Onboarding && route !is Route.Game) {
        App.sfx("ui_back"); nav.back()
    }
    BackHandler(enabled = route is Route.Home && !confirmExit) { App.sfx("ui_popup"); confirmExit = true }
    Box(Modifier.fillMaxSize().background(BV.Cream)) {
        AnimatedContent(
            targetState = route,
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith fadeOut(tween(180)) },
            label = "nav",
        ) { r ->
            when (r) {
                Route.Splash -> SplashScreen {
                    if (App.repo.data.onboardingDone) nav.resetTo(Route.Home(2)) else nav.resetTo(Route.Onboarding)
                }
                Route.Onboarding -> OnboardingScreen(
                    onFinish = {
                        App.repo.setOnboardingDone()
                        nav.resetTo(Route.Home(2)); nav.push(Route.Game(App.repo.data.selectedStore))
                    },
                    onLeave = if (App.repo.data.onboardingDone) ({ nav.resetTo(Route.Home(2)) }) else null,
                )
                is Route.Home -> HomeScreen(
                    initialTab = homeTab,
                    onTab = { homeTab = it },
                    onPlay = { nav.push(Route.Game(App.repo.data.selectedStore)) },
                    onSettings = { nav.push(Route.Settings) },
                    onRecipeBook = { nav.push(Route.RecipeBook) },
                    onAchievements = { nav.push(Route.Achievements) },
                    onVoyage = { nav.push(Route.VoyageBook) },
                    onMap = { nav.push(Route.WorldMap) },
                )
                is Route.Game -> GameScreen(
                    store = r.store,
                    onExit = { nav.resetTo(Route.Home(2)) },
                    onOpen = { next -> nav.replace(Route.Game(next)) },
                )
                Route.Settings -> SettingsScreen(
                    onBack = { nav.back() },
                    onLegal = { nav.push(Route.Legal(it)) },
                    onReplayTutorial = { nav.resetTo(Route.Onboarding) },
                )
                is Route.Legal -> LegalScreen(r.doc) { nav.back() }
                Route.RecipeBook -> RecipeBookScreen { nav.back() }
                Route.Achievements -> AchievementsScreen { nav.back() }
                Route.VoyageBook -> com.ngstudio.burgerready.ui.screens.VoyageBookScreen { nav.back() }
                Route.WorldMap -> com.ngstudio.burgerready.ui.screens.WorldMapScreen(onBack = { nav.back() }, onPlay = { nav.resetTo(Route.Home(2)); nav.push(Route.Game(App.repo.data.selectedStore)) })
            }
        }
        if (confirmExit) {
            BackHandler { confirmExit = false }
            com.ngstudio.burgerready.ui.kit.GameDialog({ confirmExit = false }) {
                com.ngstudio.burgerready.ui.screens.DialogCard("LEAVING?", { confirmExit = false }) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        androidx.compose.material3.Text("Leave the kitchen? Your restaurants keep earning while you're away!",
                            style = com.ngstudio.burgerready.ui.theme.Type.Body.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                        androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                        com.ngstudio.burgerready.ui.kit.GameButton("KEEP COOKING", Modifier.fillMaxWidth(), color = BV.Mint, dark = BV.MintDark) { confirmExit = false }
                        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                        com.ngstudio.burgerready.ui.kit.GameButton("QUIT", Modifier.fillMaxWidth(), color = BV.Tomato, dark = BV.TomatoDark, height = 46.dp, textSize = 18.sp, sound = "ui_back") { activity?.finish() }
                    }
                }
            }
        }
    }
}
