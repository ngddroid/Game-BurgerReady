package com.ngstudio.burgerready.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.BuildConfig
import com.ngstudio.burgerready.game.LevelGenerator
import com.ngstudio.burgerready.game.Stores
import com.ngstudio.burgerready.ui.kit.BadgeDot
import com.ngstudio.burgerready.ui.kit.GIcon
import com.ngstudio.burgerready.ui.kit.GameButton
import com.ngstudio.burgerready.ui.kit.GameIcon
import com.ngstudio.burgerready.ui.kit.OutlinedText
import com.ngstudio.burgerready.ui.kit.StarIcon
import com.ngstudio.burgerready.ui.kit.bob
import com.ngstudio.burgerready.ui.kit.drawCorgiFace
import com.ngstudio.burgerready.ui.kit.pulse
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

private val chapterColors = listOf(
    Color(0xFFBFEA9A), Color(0xFFBDEBFF), Color(0xFFFFD3E2), Color(0xFFE3EEFF), Color(0xFFC9D3F2), Color(0xFFFFE2B5),
    Color(0xFFE6D6FF), Color(0xFFCFF2C6), Color(0xFFFFCDB8), Color(0xFFBFC8EE), Color(0xFFB8E9F7), Color(0xFFFFE3F4),
)

/** Saga-style road map for 10,000 levels (bottom = level 1). Lazily composed. */
@Composable
fun PlayTab(onPlay: (Int) -> Unit, openDialog: (HomeDialog) -> Unit, onRecipeBook: () -> Unit, onAchievements: () -> Unit) {
    val d = App.repo.state.value
    val current = App.repo.currentLevel()
    val total = LevelGenerator.MAX_LEVEL
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf("") }
    var shakeLevel by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(toast) { if (toast.isNotEmpty()) { delay(1800); toast = "" } }
    val screenW = LocalConfiguration.current.screenWidthDp

    Box(Modifier.fillMaxSize().background(chapterColors[0])) {
        LazyColumn(
            state = list, reverseLayout = true, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 110.dp, bottom = 200.dp),
        ) {
            items(total, key = { it }) { idx ->
                val level = idx + 1
                val chapter = (level - 1) / 10
                val bg = chapterColors[(chapter / 3) % chapterColors.size]
                val next = chapterColors[((chapter + 1) / 3) % chapterColors.size]
                val frac = ((level - 1) % 30) / 30f
                Box(Modifier.fillMaxWidth().height(if (level % 10 == 0) 150.dp else 104.dp).background(lerp(bg, next, frac))) {
                    // decorative dots / path to the next node
                    val x = nodeX(level); val xn = nodeX(level + 1)
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        drawLine(Color.White.copy(alpha = 0.85f), Offset(w * x, size.height * 0.62f), Offset(w * xn, -size.height * 0.1f), 10.dp.toPx(),
                            cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 26f)))
                        // scenery blobs
                        val s = (level * 37) % 100 / 100f
                        drawCircle(Color.White.copy(alpha = 0.25f), 28.dp.toPx() + s * 20, Offset(w * (if (x < 0.5f) 0.85f else 0.15f), size.height * (0.3f + s * 0.4f)))
                    }
                    if (level % 10 == 1) ChapterBanner(level, Modifier.align(Alignment.BottomCenter))
                    val unlocked = App.repo.isUnlocked(level)
                    val stars = d.starsOf(level)
                    val boss = LevelGenerator.bossFor(level)
                    val isCurrent = level == current
                    Box(Modifier.align(Alignment.TopStart).offset(x = (screenW * x).dp - 40.dp, y = if (level % 10 == 0) 30.dp else 12.dp)
                        .graphicsLayer { if (shakeLevel == level) translationX = shake.value }) {
                        LevelNode(level, unlocked, stars, boss != null, isCurrent) {
                            if (unlocked) openDialog(HomeDialog.LevelInfo(level))
                            else if (BuildConfig.DEBUG_AD_UNLOCK && App.ads.rewardedAvailable) openDialog(HomeDialog.Unlock(level))
                            else {
                                App.sfx("ui_error"); App.haptic(1)
                                toast = "Finish Level ${d.highestUnlocked} to unlock"
                                shakeLevel = level
                                scope.launch { shake.snapTo(0f); for (k in 0 until 6) { shake.animateTo(if (k % 2 == 0) 14f else -14f, spring(stiffness = 4000f)) }; shake.animateTo(0f) }
                            }
                        }
                    }
                }
            }
        }
        // Side buttons
        Column(Modifier.align(Alignment.TopEnd).padding(top = 92.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SideButton(GIcon.SPIN, "Spin", App.repo.canSpin(), BV.Lilac) { openDialog(HomeDialog.Spin) }
            SideButton(GIcon.MISSION, "Tasks", d.missions.any { it.done && !it.claimed }, BV.Mint) { openDialog(HomeDialog.Missions) }
            SideButton(GIcon.CALENDAR, "Daily", App.repo.canClaimLogin(), BV.Tomato) { openDialog(HomeDialog.Login) }
        }
        Column(Modifier.align(Alignment.TopStart).padding(top = 92.dp, start = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SideButton(GIcon.BOOK, "Recipes", false, BV.Teal, onRecipeBook)
            SideButton(GIcon.TROPHY, "Trophies", App.repo.unclaimedAchievements() > 0, BV.Mustard, onAchievements)
            if (d.stores.size > 1 || App.repo.totalPendingIncome() > 0) SideButton(GIcon.STORE, "Income", App.repo.totalPendingIncome() >= 50, BV.Pink) { openDialog(HomeDialog.Income) }
        }
        // Play current level
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(BV.Cocoa.copy(alpha = 0.8f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(LevelGenerator.titleFor(current) + " · " + Stores.byId(d.selectedStore).name, style = Type.Small.copy(color = BV.Cream))
            }
            Spacer(Modifier.height(6.dp))
            GameButton("PLAY LEVEL $current", Modifier.width(260.dp).pulse(0.97f, 1.03f, 1100), color = BV.Tomato, dark = BV.TomatoDark, height = 62.dp, textSize = 24.sp) {
                openDialog(HomeDialog.LevelInfo(current))
            }
        }
        if (toast.isNotEmpty()) {
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(BV.Cocoa).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(toast, style = Type.Body.copy(color = BV.Cream))
            }
        }
        // Jump-to-current button when far away
        if (kotlin.math.abs(list.firstVisibleItemIndex - (current - 3)) > 12) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 100.dp)) {
                com.ngstudio.burgerready.ui.kit.RoundButton(color = BV.Mustard, size = 50.dp, onClick = { scope.launch { list.animateScrollToItem((current - 3).coerceAtLeast(0)) } }) {
                    OutlinedText("⌂", size = 22.sp)
                }
            }
        }
    }
}

private fun nodeX(level: Int): Float = 0.5f + 0.28f * sin(level * 0.9f)

@Composable
private fun ChapterBanner(level: Int, modifier: Modifier) {
    val chapter = (level - 1) / 10 + 1
    Box(modifier.padding(bottom = 2.dp).clip(RoundedCornerShape(10.dp)).background(BV.Cocoa.copy(alpha = 0.75f)).padding(horizontal = 12.dp, vertical = 3.dp)) {
        Text("CHAPTER $chapter", style = Type.Small.copy(color = BV.Gold, fontSize = 11.sp))
    }
}

@Composable
private fun LevelNode(level: Int, unlocked: Boolean, stars: Int, boss: Boolean, current: Boolean, onClick: () -> Unit) {
    val size = if (boss) 92.dp else 80.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size)) {
        if (current) {
            Box(Modifier.size(44.dp).bob(5f).clip(CircleShape).background(BV.Cocoa).padding(3.dp).clip(CircleShape).background(BV.Mustard)) {
                Canvas(Modifier.fillMaxSize()) { drawCorgiFace() }
            }
        }
        Box(
            Modifier.size(size * 0.82f).then(if (current) Modifier.pulse(0.96f, 1.06f) else Modifier).shadow(6.dp, CircleShape).clip(CircleShape).background(BV.Cocoa).padding(4.dp).clip(CircleShape)
                .background(
                    when {
                        !unlocked -> Brush.verticalGradient(listOf(Color(0xFFD8D0C8), Color(0xFFADA49C)))
                        boss -> Brush.verticalGradient(listOf(Color(0xFFFF8A5C), BV.Tomato))
                        stars > 0 -> Brush.verticalGradient(listOf(Color(0xFF7BF0BD), BV.MintDark))
                        else -> Brush.verticalGradient(listOf(Color(0xFFFFDB7A), BV.MustardDark))
                    },
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { App.sfx(if (unlocked) "ui_tap" else "ui_error"); onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(size * 0.45f, 8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.35f)))
            if (unlocked) OutlinedText("$level", size = if (level >= 1000) 17.sp else 22.sp, stroke = 1.8.dp)
            else GameIcon(GIcon.LOCK, 30.dp)
            if (boss) Box(Modifier.align(Alignment.TopCenter).offset(y = (-2).dp)) { Text("👑", fontSize = 16.sp) }
        }
        if (unlocked && !current) {
            Row(Modifier.offset(y = (-8).dp)) { for (k in 1..3) StarIcon(18.dp, filled = k <= stars) }
        }
    }
}

@Composable
private fun SideButton(icon: GIcon, label: String, badge: Boolean, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            com.ngstudio.burgerready.ui.kit.RoundButton(color = color, size = 54.dp, onClick = onClick, modifier = if (badge) Modifier.pulse(0.95f, 1.05f) else Modifier) { GameIcon(icon, 32.dp) }
            if (badge) BadgeDot(modifier = Modifier.align(Alignment.TopEnd))
        }
        OutlinedText(label, size = 12.sp, stroke = 1.2.dp)
    }
}
