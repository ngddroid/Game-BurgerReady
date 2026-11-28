package com.ngstudio.burgerready.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.ui.kit.ArtImage
import com.ngstudio.burgerready.ui.kit.ConfettiBurst
import com.ngstudio.burgerready.ui.kit.GameButton
import com.ngstudio.burgerready.ui.kit.OutlinedText
import com.ngstudio.burgerready.ui.kit.ProgressBar3D
import com.ngstudio.burgerready.ui.kit.Sunburst
import com.ngstudio.burgerready.ui.kit.VectorBurger
import com.ngstudio.burgerready.ui.kit.bob
import com.ngstudio.burgerready.ui.kit.drawCorgiFace
import com.ngstudio.burgerready.ui.kit.pulse
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Logo: "Bun Voyage!" with a tilted, outlined two-tone treatment. */
@Composable
fun Logo(modifier: Modifier = Modifier, scale: Float = 1f) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedText("BUN", Modifier.graphicsLayer { rotationZ = -4f }, size = (64 * scale).sp, color = BV.Mustard, stroke = (3.5 * scale).dp)
        OutlinedText("VOYAGE!", Modifier.graphicsLayer { rotationZ = -4f; translationY = -18f * scale }, size = (52 * scale).sp, color = BV.Tomato, stroke = (3.5 * scale).dp)
        Box(Modifier.graphicsLayer { translationY = -26f * scale }.clip(RoundedCornerShape(12.dp)).background(BV.Cocoa).padding(horizontal = 12.dp, vertical = 3.dp)) {
            Text("BURGER TYCOON", style = Type.Button.copy(fontSize = (14 * scale).sp, color = BV.Cream))
        }
    }
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val layers = remember { Animatable(0f) }
    val logo = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }
    var confetti by remember { mutableIntStateOf(0) }
    val tips = listOf("Serve fast to build combos!", "VIP guests pay triple!", "Hire helpers to run the kitchen.", "Finished restaurants earn while you're away.", "Clean tables so guests can sit.")
    val tip = remember { tips.random() }
    LaunchedEffect(Unit) {
        App.audio.playMusic("menu_theme")
        launch { progress.animateTo(1f, tween(2700, easing = LinearEasing)) }
        launch { logo.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
        delay(450)
        for (i in 1..5) {
            launch { layers.animateTo(i.toFloat(), tween(200)) }
            App.sfx("splash_pop", 1f, 0.9f + i * 0.08f)
            delay(230)
        }
        confetti++
        App.sfx("ui_reward")
        delay(1000)
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        Sunburst(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.6f))
            Logo(Modifier.graphicsLayer { scaleX = logo.value; scaleY = logo.value; translationY = (1f - logo.value) * -300f; alpha = logo.value.coerceIn(0f, 1f) })
            Spacer(Modifier.height(10.dp))
            VectorBurger(Modifier.fillMaxWidth(0.62f).aspectRatio(1.1f).bob(5f), layers.value)
            Spacer(Modifier.weight(1f))
            Text(tip, style = Type.Body.copy(color = BV.Cocoa, textAlign = TextAlign.Center))
            Spacer(Modifier.height(10.dp))
            ProgressBar3D(progress.value, Modifier.fillMaxWidth(0.8f), color = BV.Tomato, height = 22.dp, animate = false)
            Spacer(Modifier.height(6.dp))
            Text("Heating up the grill… ${(progress.value * 100).toInt()}%", style = Type.Small.copy(color = BV.Cocoa))
            Spacer(Modifier.height(24.dp))
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}

private data class OnboardPage(val title: String, val body: String, val art: String, val top: Color, val bottom: Color)

/** Four illustrated pages with a big Next button. There is intentionally NO skip button. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, onLeave: (() -> Unit)? = null) {
    val pages = listOf(
        OnboardPage("Welcome to\nBun Voyage!", "Meet Chef Patty, a corgi with an old food wagon and a big promise to keep.", "onboard_1", Color(0xFFFFD86B), Color(0xFFFF9A3C)),
        OnboardPage("Grill, Stack\n& Serve", "Grab burgers from the grill, stack them high and serve hungry guests before they lose patience!", "onboard_2", Color(0xFF8FE3D0), Color(0xFF1FA6B8)),
        OnboardPage("Build Your\nDream Diner", "Spend your cash on new stations, tables and helpers. Everything you build stays yours!", "onboard_3", Color(0xFFFFB4C8), Color(0xFFF2542D)),
        OnboardPage("Fill the\nVoyage Book", "Twelve restaurants, from the beach to outer space. Finish one to open the next. They all earn while you're away!", "onboard_4", Color(0xFFC9B8FF), Color(0xFF3D8BFF)),
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    // Back steps to the previous page (it used to close the app from any page); still no skip
    androidx.activity.compose.BackHandler(enabled = pager.currentPage > 0) {
        App.sfx("ui_back"); scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }
    // a replay from Settings → How to Play goes back Home from the first page instead of closing the app
    androidx.activity.compose.BackHandler(enabled = pager.currentPage == 0 && onLeave != null) { App.sfx("ui_back"); onLeave?.invoke() }
    LaunchedEffect(pager.currentPage) { App.sfx("whoosh", 0.6f) }
    Box(Modifier.fillMaxSize()) {
        val page = pages[pager.currentPage]
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(page.top, page.bottom))))
        Sunburst(Modifier.fillMaxSize().graphicsLayer { alpha = 0.25f }, a = Color.Transparent, b = Color.White.copy(alpha = 0.5f), speed = 40000)
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(36.dp))
                Box(Modifier.fillMaxWidth().aspectRatio(0.95f).bob(8f, 1800), contentAlignment = Alignment.Center) {
                    ArtImage(p.art, Modifier.fillMaxSize()) { OnboardFallback(i) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedText(p.title, size = 38.sp, stroke = 3.dp, maxLines = 2)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.88f)).border(3.dp, BV.Cocoa, RoundedCornerShape(20.dp)).padding(16.dp)) {
                    Text(p.body, style = Type.Body.copy(fontSize = 16.sp, textAlign = TextAlign.Center))
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { k ->
                    val active = k == pager.currentPage
                    Box(Modifier.height(12.dp).width(if (active) 32.dp else 12.dp).clip(CircleShape).background(BV.Cocoa).padding(2.dp).clip(CircleShape).background(if (active) BV.Mustard else Color.White))
                }
            }
            Spacer(Modifier.height(18.dp))
            val last = pager.currentPage == pages.size - 1
            GameButton(
                if (last) "LET'S COOK!" else "NEXT",
                Modifier.fillMaxWidth(0.8f).then(if (last) Modifier.pulse() else Modifier),
                color = if (last) BV.Tomato else BV.Mint, dark = if (last) BV.TomatoDark else BV.MintDark, height = 62.dp, textSize = 26.sp,
            ) {
                if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
        }
    }
}

@Composable
private fun OnboardFallback(i: Int) {
    val t = rememberInfiniteTransition(label = "of")
    val w by t.animateFloat(0f, 100f, infiniteRepeatable(tween(100000, easing = LinearEasing)), label = "ofw")
    when (i) {
        0 -> Canvas(Modifier.fillMaxSize(0.8f)) { drawCorgiFace(w) }
        1 -> Box(contentAlignment = Alignment.Center) {
            VectorBurger(Modifier.size(200.dp).graphicsLayer { translationY = -120f })
            VectorBurger(Modifier.size(200.dp))
            VectorBurger(Modifier.size(200.dp).graphicsLayer { translationY = 120f })
        }
        else -> VectorBurger(Modifier.fillMaxSize(0.75f))
    }
}
