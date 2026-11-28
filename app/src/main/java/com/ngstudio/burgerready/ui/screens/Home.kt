package com.ngstudio.burgerready.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.data.ChefRank
import com.ngstudio.burgerready.ui.kit.BadgeDot
import com.ngstudio.burgerready.ui.kit.BucksIcon
import com.ngstudio.burgerready.ui.kit.CurrencyChip
import com.ngstudio.burgerready.ui.kit.GIcon
import com.ngstudio.burgerready.ui.kit.GameIcon
import com.ngstudio.burgerready.ui.kit.GemIcon
import com.ngstudio.burgerready.ui.kit.OutlinedText
import com.ngstudio.burgerready.ui.kit.RoundButton
import com.ngstudio.burgerready.ui.kit.VectorBurger
import com.ngstudio.burgerready.ui.kit.drawCorgiFace
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Type

@Composable
fun HomeScreen(
    initialTab: Int,
    onTab: (Int) -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onRecipeBook: () -> Unit,
    onAchievements: () -> Unit,
    onVoyage: () -> Unit,
    onMap: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(initialTab) }
    var prevTab by remember { mutableIntStateOf(initialTab) }
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val d = App.repo.state.value
    LaunchedEffect(Unit) {
        App.audio.playMusic("menu_theme")
        // Welcome back: one card at most (income and/or today's calendar day). It waits behind the prologue.
        dialog = AutoPopups.next()
    }
    // Saves from before the story existed never saw the Bun Wagon arrival: tell them the premise once.
    var prologue by remember {
        mutableStateOf(com.ngstudio.burgerready.game.Story.beat("prologue")?.takeIf {
            d.onboardingDone && it.id !in d.story && "s0_arrive" !in d.story && (d.frontier() > 0 || d.isComplete("wagon"))
        })
    }
    // Back closes the open dialog instead of stacking "Leaving?" on top of it
    // (SpinCard registers its own handler that swallows Back while the wheel turns).
    BackHandler(enabled = dialog != null) {
        val cur = dialog
        App.sfx("ui_back"); dialog = null
    }
    Box(Modifier.fillMaxSize().background(BV.Cream)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(tab, transitionSpec = {
                    val dir = if (targetState > prevTab) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 4 * dir } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(200)) { -it / 4 * dir } + fadeOut(tween(160)))
                }, label = "tabs") { t ->
                    when (t) {
                        0 -> ShopTab { dialog = it }
                        1 -> KitchenTab()
                        2 -> PlayTab(onPlay = onPlay, openDialog = { dialog = it }, onRecipeBook = onRecipeBook, onAchievements = onAchievements, onVoyage = onVoyage, onMap = onMap)
                        3 -> StoresTab { dialog = it }
                        else -> StyleTab()
                    }
                }
                TopBar(onSettings) { dialog = HomeDialog.Rank }
            }
            BottomBar(tab) { prevTab = tab; tab = it; onTab(it) }
        }
        val pro = prologue
        if (pro != null) {
            BackHandler { }  // a few taps; Back waits for it
            StoryScene(pro, "BEFORE PAGE ONE") { App.repo.markStorySeen(pro.id); prologue = null }
        } else dialog?.let { cur ->
            HomeDialogs(cur, onPlay = { dialog = null; onPlay() }) { dialog = null }
        }
    }
}

/**
 * Which welcome dialog Home opens by itself: never more than one. Home is recreated every time the player comes
 * back from Settings, the Recipe Book or a restaurant, so without this the popups reappeared on every return.
 * Income is offered at most every 10 minutes and only when it is worth it; the daily calendar once per launch.
 * When both are due they share one WELCOME BACK card (round 3 showed them back to back, after the prologue).
 */
private object AutoPopups {
    private var incomeShownAt = 0L
    private var loginShown = false

    fun next(): HomeDialog? {
        val repo = App.repo
        val d = repo.data
        if (d.builtTotal() < 3) return null
        val now = System.currentTimeMillis()
        val pending = repo.totalPendingIncome(now)
        val worthIt = pending >= maxOf(50L, (repo.totalIncomeRate() / 12).toLong()) // ≥ 5 minutes of income
        val income = worthIt && now - incomeShownAt > 10 * 60_000L
        val login = !loginShown && repo.canClaimLogin()
        if (income) incomeShownAt = now
        if (login) loginShown = true
        return when {
            income && login -> HomeDialog.WelcomeBack
            income -> HomeDialog.Income
            login -> HomeDialog.Login
            else -> null
        }
    }
}

@Composable
fun TopBar(onSettings: () -> Unit, onRank: () -> Unit) {
    val d = App.repo.state.value
    val rank = ChefRank.rankOf(d.chefXp)
    val next = ChefRank.xpFor(rank + 1); val cur = ChefRank.xpFor(rank)
    val prog = if (next > cur) (d.chefXp - cur).toFloat() / (next - cur) else 1f
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC3B2A3A), Color.Transparent))).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { App.sfx("ui_tap"); onRank() }) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(BV.Cocoa).padding(3.dp).clip(CircleShape).background(BV.Mustard)) {
                Canvas(Modifier.fillMaxSize().padding(2.dp)) { drawCorgiFace() }
            }
            Canvas(Modifier.size(50.dp)) {
                drawArc(BV.Mint, -90f, 360f * prog, false, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
            }
            Box(Modifier.align(Alignment.BottomEnd).offset(6.dp, 4.dp).clip(RoundedCornerShape(8.dp)).background(BV.Cocoa).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Text("$rank", style = Type.Number.copy(fontSize = 12.sp, color = BV.Gold))
            }
            if (rank > d.chefRankClaimed) BadgeDot(modifier = Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.width(10.dp))
        CurrencyChip({ BucksIcon(24.dp) }, d.bucks, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        CurrencyChip({ GemIcon(22.dp) }, d.gems.toLong(), Modifier.weight(0.75f))
        Spacer(Modifier.width(8.dp))
        RoundButton(color = BV.Cream, size = 44.dp, onClick = onSettings) { GameIcon(GIcon.GEAR, 28.dp, BV.Cream) }
    }
}

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Shop", "Kitchen", "Play", "World", "Style")
    val icons = listOf(GIcon.SHOP, GIcon.KITCHEN, GIcon.PLAY, GIcon.STORE, GIcon.STYLE)
    val d = App.repo.state.value
    val badges = listOf(
        App.repo.canClaimFreeChest(),
        com.ngstudio.burgerready.game.Upgrade.entries.any { d.bucks >= it.cost(d.upgrade(it)) && d.upgrade(it) < it.maxLevel },
        false,
        App.repo.totalPendingIncome() >= 50,
        false,
    )
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().shadow(12.dp).background(BV.Cocoa).padding(top = 3.dp).background(Brush.verticalGradient(listOf(Color(0xFFFFE6B8), BV.CreamDark)))
                .navigationBarsPadding().height(70.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until 5) {
                val sel = i == selected
                val lift by animateDpAsState(if (sel) (-8).dp else 0.dp, spring(dampingRatio = 0.45f), label = "lift")
                val scale by animateFloatAsState(if (sel) 1.15f else 1f, spring(dampingRatio = 0.45f), label = "sc")
                Box(
                    Modifier.weight(1f).fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (!sel) { App.sfx("ui_tab"); App.haptic(0); onSelect(i) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == 2) {
                        // big raised play button
                        Box(Modifier.offset(y = (-18).dp + lift / 2).size(74.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(BV.Cocoa).padding(4.dp).clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFFFF8A5C), BV.Tomato))), contentAlignment = Alignment.Center) {
                            VectorBurger(Modifier.size(50.dp).graphicsLayer { scaleX = scale; scaleY = scale })
                        }
                        OutlinedText("PLAY", Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), size = 13.sp, stroke = 1.2.dp)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = lift)) {
                            Box {
                                if (sel) Box(Modifier.size(44.dp).clip(CircleShape).background(BV.Mustard.copy(alpha = 0.6f)).align(Alignment.Center))
                                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { GameIcon(icons[i], 32.dp) } }
                                if (badges[i]) BadgeDot(modifier = Modifier.align(Alignment.TopEnd))
                            }
                            if (sel) OutlinedText(labels[i], size = 13.sp, stroke = 1.2.dp) else Text(labels[i], style = Type.Small.copy(color = BV.CocoaSoft, fontSize = 11.sp))
                        }
                    }
                }
            }
        }
    }
}

sealed class HomeDialog {
    data object Login : HomeDialog()
    data object Spin : HomeDialog()
    data object Missions : HomeDialog()
    data object Income : HomeDialog()
    /** Income and the daily calendar together (both due on a return). */
    data object WelcomeBack : HomeDialog()
    data object Rank : HomeDialog()
    /** Debug builds only: mock rewarded ad that opens the next restaurant early. */
    data object UnlockNext : HomeDialog()
    data class Reward(val title: String, val bucks: Long, val gems: Int) : HomeDialog()
    data class StoreDetail(val id: String) : HomeDialog()
}
