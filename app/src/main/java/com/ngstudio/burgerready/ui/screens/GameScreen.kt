package com.ngstudio.burgerready.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.BuildConfig
import com.ngstudio.burgerready.data.LevelReward
import com.ngstudio.burgerready.game.Cosmetics
import com.ngstudio.burgerready.game.Ev
import com.ngstudio.burgerready.game.Item
import com.ngstudio.burgerready.game.LevelGenerator
import com.ngstudio.burgerready.game.ShiftResult
import com.ngstudio.burgerready.game.Stores
import com.ngstudio.burgerready.render.GameSession
import com.ngstudio.burgerready.render.GameSurfaceView
import com.ngstudio.burgerready.render.HudState
import com.ngstudio.burgerready.render.VisualStyle
import com.ngstudio.burgerready.ui.kit.BucksIcon
import com.ngstudio.burgerready.ui.kit.CoinIcon
import com.ngstudio.burgerready.ui.kit.ConfettiBurst
import com.ngstudio.burgerready.ui.kit.GIcon
import com.ngstudio.burgerready.ui.kit.GameButton
import com.ngstudio.burgerready.ui.kit.GameIcon
import com.ngstudio.burgerready.ui.kit.GemIcon
import com.ngstudio.burgerready.ui.kit.OutlinedText
import com.ngstudio.burgerready.ui.kit.Panel
import com.ngstudio.burgerready.ui.kit.ProgressBar3D
import com.ngstudio.burgerready.ui.kit.Ribbon
import com.ngstudio.burgerready.ui.kit.RoundButton
import com.ngstudio.burgerready.ui.kit.StarIcon
import com.ngstudio.burgerready.ui.kit.Sunburst
import com.ngstudio.burgerready.ui.kit.formatNum
import com.ngstudio.burgerready.ui.kit.pulse
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Type
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/** Counters collected during a shift for missions, cards and achievements. */
class ShiftCounters {
    var troubles = 0L; var trays = 0L; var vips = 0L
    val cards = HashSet<String>()
}

@Composable
fun GameScreen(level: Int, onExit: () -> Unit, onNext: (Int) -> Unit) {
    val context = LocalContext.current
    val repo = App.repo
    val d = repo.state.value
    val cfg = remember(level) { LevelGenerator.generate(level) }
    val counters = remember(level) { ShiftCounters() }
    var hud by remember { mutableStateOf(HudState()) }
    var paused by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ShiftResult?>(null) }
    var reward by remember { mutableStateOf<LevelReward?>(null) }
    var intro by remember { mutableStateOf(true) }
    val skin = Cosmetics.SKINS.firstOrNull { it.id == d.selectedSkin } ?: Cosmetics.SKINS[0]
    val pet = Cosmetics.PETS.firstOrNull { it.id == d.selectedPet }
    val theme = Cosmetics.THEMES.firstOrNull { it.id == d.selectedTheme } ?: Cosmetics.THEMES[0]
    val style = VisualStyle(Stores.byId(d.selectedStore), theme, skin.species, skin.hat, skin.apron, pet?.model?.takeIf { it.isNotEmpty() })

    val view = remember(level) { GameSurfaceView(context, App.quality(context)) }
    val session = remember(level) {
        GameSession(cfg, repo.stats(), style,
            onFinished = { r -> result = r },
            onEvent = { e, item, a -> onGameEvent(e, item, a, counters) },
        )
    }
    LaunchedEffect(session) {
        view.game.session = session
        session.paused = true
        App.audio.playMusic(if (cfg.isBoss) "boss_theme" else "level_calm", if (cfg.isBoss) null else "level_rush")
        App.audio.startLoop("sizzle_loop", 0.18f)
        // counters/cards seeded from the config
        cfg.boss?.let { counters.cards.add("boss_${it.name.lowercase()}") }
    }
    DisposableEffect(level) {
        view.onResume()
        onDispose {
            view.game.session = null
            view.onPause()
            App.audio.stopLoop()
            App.audio.setRush(false)
        }
    }
    // HUD polling
    LaunchedEffect(session) {
        while (true) {
            withFrameNanos { }
            val h = session.hud
            if (h !== hud) {
                if (h.rush != hud.rush) App.audio.setRush(h.rush)
                hud = h
            }
            delay(50)
        }
    }
    // result handling
    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        App.audio.stopLoop()
        App.audio.setRush(false)
        val world = session.world
        counters.vips = world.vipsServed.toLong()
        if (world.vipsServed > 0) counters.cards.add("special_vip")
        if (world.carsServed > 0) counters.cards.add("special_drive")
        world.powerUpsUsed.forEach { counters.cards.add("special_" + when (it) { com.ngstudio.burgerready.game.PowerUpKind.ESPRESSO -> "espresso"; com.ngstudio.burgerready.game.PowerUpKind.MAGNET -> "magnet"; else -> "spatula" }) }
        if (r.won && cfg.rush.isNotEmpty()) counters.cards.add("special_rush")
        if (r.won) {
            if (r.stars == 3 && r.walkouts == 0) counters.cards.add("special_perfect")
            cfg.mutators.forEach { counters.cards.add("mut_${it.name.lowercase()}") }
            if (!cfg.isBoss) counters.cards.remove("boss_" + (cfg.boss?.name?.lowercase() ?: ""))
            App.audio.stopMusic(); App.sfx("level_win"); App.haptic(2)
        } else {
            counters.cards.removeAll { it.startsWith("boss_") }
            App.audio.stopMusic(); App.sfx("level_fail"); App.haptic(3)
        }
        for (i in r.itemsServed.indices) if (r.itemsServed[i] > 0) counters.cards.add("food_" + Item.entries[i].id)
        session.world.speciesServed.forEach { counters.cards.add("guest_" + it.id) }
        session.world.troublesFixed.forEach { counters.cards.add("trouble_" + it.name.lowercase()) }
        if (r.bestCombo >= 2.99f) counters.cards.add("special_combo3")
        reward = repo.completeLevel(r, counters.cards, mapOf("troubles" to counters.troubles, "trays" to counters.trays, "vips" to counters.vips))
    }
    BackHandler(enabled = result == null) { if (!intro) { paused = !paused; session.paused = paused } }

    Box(Modifier.fillMaxSize().background(Color(0xFFFFE9C8))) {
        AndroidView({ view }, Modifier.fillMaxSize())
        JoystickOverlay(view)
        if (result == null) Hud(hud, cfg.isBoss, onPause = { paused = true; session.paused = true }, onFinish = { session.finishRequested = true })
        CoinFly(hud)
        if (intro) LevelIntro(cfg.title, level, cfg.target, cfg.boss?.blurb, cfg.mutators.map { it.label }) { intro = false; session.paused = false }
        if (paused && result == null) PauseMenu(
            onResume = { paused = false; session.paused = false },
            onRestart = { onNext(level) },
            onHome = { onExit() },
            onDebugWin = { paused = false; session.paused = false; session.debugAction = 1 },
            onDebugCash = { paused = false; session.paused = false; session.debugAction = 2 },
        )
        result?.let { r -> ResultsOverlay(r, reward, cfg.isBoss, onNext = { onNext(min(LevelGenerator.MAX_LEVEL, level + 1)) }, onRetry = { onNext(level) }, onHome = onExit) }
    }
}

private fun onGameEvent(e: Ev, item: Item?, a: Float, c: ShiftCounters) {
    when (e) {
        Ev.PICKUP -> { App.sfx("pickup", 0.9f, 0.85f + min(a, 14f) * 0.05f); App.haptic(0); if (item == Item.TRAY) c.trays++ }
        Ev.DROP -> App.sfx("drop", 0.8f, 0.9f + min(a, 10f) * 0.03f)
        Ev.TRASH -> App.sfx("trash_dump", 0.7f)
        Ev.COIN_COLLECT -> { App.sfx("coin_cascade"); App.haptic(1) }
        Ev.PAY_TICK -> App.sfx("pad_fill", 0.6f, 0.9f + a * 0.5f)
        Ev.BUILT -> { App.sfx("build_pop"); App.haptic(3) }
        Ev.UPGRADED -> { App.sfx("upgrade"); App.haptic(2) }
        Ev.HIRED -> { App.sfx("hire"); App.haptic(2); c.cards.add("special_cashier") }
        Ev.SERVED -> { App.sfx("cash_register", 0.8f); App.sfx("customer_happy", 0.5f) }
        Ev.ORDER -> App.sfx("customer_order", 0.6f)
        Ev.ANGRY -> App.sfx("customer_angry", 0.8f)
        Ev.EAT -> App.sfx("eat_chomp", 0.5f)
        Ev.COMBO_UP -> App.sfx(if (a >= 2.5f) "combo_up3" else if (a >= 1.75f) "combo_up2" else "combo_up1")
        Ev.COMBO_BREAK -> App.sfx("combo_break", 0.7f)
        Ev.RUSH_START -> App.sfx("rush_siren", 0.8f)
        Ev.RUSH_END -> {}
        Ev.TROUBLE -> App.sfx("fire_whoosh", 0.7f)
        Ev.TROUBLE_FIXED -> { App.sfx("machine_fixed"); c.troubles++ }
        Ev.POWERUP -> App.sfx("powerup")
        Ev.VIP -> App.sfx("vip_arrive", 0.7f)
        Ev.CAR_HORN -> App.sfx("car_horn", 0.5f)
        Ev.GOAL_REACHED -> App.sfx("ui_reward")
        Ev.TIMER_WARN -> App.sfx("timer_warning")
        Ev.ITEM_FLY -> {}
        Ev.STEP -> App.sfx(if ((System.nanoTime() / 1000) % 2 == 0L) "footstep1" else "footstep2", 0.5f)
        Ev.CRITIC_HAPPY -> { App.sfx("critic_hmm"); c.cards.add("special_critic") }
        Ev.STOLEN -> App.sfx("raccoon_chitter")
        Ev.SHOO -> App.sfx("whoosh")
        else -> {}
    }
}

@Composable
private fun JoystickOverlay(view: GameSurfaceView) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { }; tick++ } }
    val j = view.joystick
    @Suppress("UNUSED_EXPRESSION") tick
    if (!j.active) return
    Canvas(Modifier.fillMaxSize()) {
        val r = 56.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.18f), r * 1.15f, Offset(j.ox, j.oy))
        drawCircle(Color.White.copy(alpha = 0.6f), r * 1.15f, Offset(j.ox, j.oy), style = Stroke(3.dp.toPx()))
        var dx = j.kx - j.ox; var dy = j.ky - j.oy
        val d = kotlin.math.hypot(dx, dy)
        if (d > r) { dx = dx / d * r; dy = dy / d * r }
        drawCircle(BV.Cocoa.copy(alpha = 0.35f), r * 0.48f, Offset(j.ox + dx, j.oy + dy + 4f))
        drawCircle(Color.White.copy(alpha = 0.92f), r * 0.45f, Offset(j.ox + dx, j.oy + dy))
    }
}

@Composable
private fun Hud(h: HudState, boss: Boolean, onPause: () -> Unit, onFinish: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(10.dp)) {
        // Goal bar (top left)
        Column(Modifier.align(Alignment.TopStart).widthIn(max = 220.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(BV.Cocoa).padding(3.dp).clip(CircleShape).background(if (boss) BV.Tomato else BV.Teal), contentAlignment = Alignment.Center) {
                    OutlinedText("${h.levelId}", size = if (h.levelId >= 1000) 12.sp else 16.sp, stroke = 1.2.dp)
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    val prog = h.revenue.toFloat() / max(1, h.star3)
                    Box {
                        ProgressBar3D(prog, Modifier.fillMaxWidth(), color = if (h.goalReached) BV.Mint else BV.Mustard, height = 22.dp,
                            markers = listOf(h.target.toFloat() / h.star3, h.star2.toFloat() / h.star3))
                        Text("$${formatNum(h.revenue.toLong())} / $${formatNum(h.target.toLong())}", style = Type.Small.copy(color = BV.Cocoa, fontSize = 11.sp),
                            modifier = Modifier.align(Alignment.Center))
                    }
                    Row {
                        val stars = when { h.revenue >= h.star3 -> 3; h.revenue >= h.star2 -> 2; h.revenue >= h.target -> 1; else -> 0 }
                        for (k in 1..3) StarIcon(18.dp, filled = k <= stars)
                    }
                }
            }
        }
        // Cash (top right) + pause
        Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(BV.Cocoa.copy(alpha = 0.85f)).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(30.dp); Spacer(Modifier.width(4.dp))
                Text(formatNum(h.cash.toLong()), style = Type.Number.copy(fontSize = 19.sp))
            }
            Spacer(Modifier.width(8.dp))
            RoundButton(color = BV.Tomato, size = 42.dp, onClick = onPause) { GameIcon(GIcon.PAUSE, 24.dp) }
        }
        // Timer pill (top centre, below)
        Column(Modifier.align(Alignment.TopCenter).padding(top = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val low = h.timeLeft < 15f && !h.overtime
            val pulseMod = if (low) Modifier.pulse(0.92f, 1.1f, 450) else Modifier
            Box(pulseMod.clip(RoundedCornerShape(16.dp)).background(if (low) BV.Tomato else BV.Cocoa.copy(alpha = 0.85f)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(GIcon.CLOCK, 20.dp); Spacer(Modifier.width(4.dp))
                    val t = h.timeLeft.toInt()
                    Text(if (h.overtime) "OVERTIME" else "${t / 60}:${"%02d".format(t % 60)}", style = Type.Number.copy(fontSize = 18.sp))
                }
            }
            if (h.rush) Box(Modifier.padding(top = 4.dp).pulse(0.9f, 1.1f, 350).clip(RoundedCornerShape(10.dp)).background(BV.Tomato).padding(horizontal = 10.dp, vertical = 2.dp)) { OutlinedText("RUSH!", size = 15.sp, stroke = 1.2.dp) }
            if (h.combo > 1.01f) {
                Box(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(BV.Mustard, BV.Tomato))).padding(horizontal = 10.dp, vertical = 2.dp)) {
                    OutlinedText("🔥 COMBO x${String.format("%.2f", h.combo).trimEnd('0').trimEnd('.')}", size = 14.sp, stroke = 1.2.dp)
                }
            }
            h.boosts.forEach { (name, t) -> Text("$name ${t.toInt()}s", style = Type.Small.copy(color = BV.Cocoa, fontSize = 11.sp)) }
        }
        // Trouble alerts (left)
        Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            h.troubles.distinct().take(3).forEach { t ->
                Box(Modifier.pulse(0.95f, 1.05f, 500).clip(RoundedCornerShape(12.dp)).background(BV.Tomato).border(2.dp, BV.Cocoa, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("⚠ $t", style = Type.Number.copy(fontSize = 13.sp))
                }
            }
        }
        // Tutorial tip
        if (h.tip.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 64.dp).widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.92f))
                .border(2.dp, BV.Cocoa, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(h.tip, style = Type.Body.copy(textAlign = TextAlign.Center))
            }
        }
        // Toasts
        var shownId by remember { mutableIntStateOf(0) }
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(h.toastId) { if (h.toastId != shownId && h.toast.isNotEmpty()) { shownId = h.toastId; visible = true; delay(1500); visible = false } }
        AnimatedVisibility(visible, Modifier.align(Alignment.Center).offset(y = (-90).dp), enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(), exit = fadeOut()) {
            OutlinedText(h.toast, size = 30.sp, color = BV.Mustard, stroke = 2.5.dp)
        }
        // Finish early when the goal is reached
        if (h.goalReached && !h.overtime) {
            Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 6.dp)) {
                GameButton("FINISH", Modifier.width(118.dp), color = BV.Mint, dark = BV.MintDark, height = 46.dp, textSize = 17.sp) { onFinish() }
            }
        }
        // stack capacity
        if (h.stack > 0) {
            Box(Modifier.align(Alignment.BottomStart).navigationBarsPadding().clip(RoundedCornerShape(12.dp)).background(BV.Cocoa.copy(alpha = 0.75f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("Stack ${h.stack}/${h.capacity}", style = Type.Number.copy(fontSize = 14.sp, color = if (h.stack >= h.capacity) BV.Mustard else Color.White))
            }
        }
        if (BuildConfig.DEBUG_TOOLS) Text("${h.fps} fps · ${h.drawCalls} dc", style = Type.Small.copy(color = Color(0xAA000000), fontSize = 10.sp), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** Coins flying from the collected pile into the HUD cash counter. */
@Composable
private fun CoinFly(h: HudState) {
    val anim = remember { Animatable(1f) }
    var fromX by remember { mutableFloatStateOf(0f) }; var fromY by remember { mutableFloatStateOf(0f) }; var n by remember { mutableIntStateOf(0) }
    LaunchedEffect(h.coinFlyId) {
        if (h.coinFlyId == 0) return@LaunchedEffect
        fromX = h.coinFlyX; fromY = h.coinFlyY; n = h.coinFlyAmount
        anim.snapTo(0f); anim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    if (anim.value >= 1f) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val hh = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        for (i in 0 until n) {
            val t = ((anim.value * 1.4f) - i * 0.03f).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) continue
            val sx = fromX * w + ((i * 37) % 60 - 30) * (1 - t); val sy = fromY * hh + ((i * 53) % 60 - 30) * (1 - t)
            val ex = w - with(density) { 110.dp.toPx() }; val ey = with(density) { 40.dp.toPx() }
            val x = sx + (ex - sx) * t; val y = sy + (ey - sy) * t - kotlin.math.sin(t * Math.PI).toFloat() * 120f
            Box(Modifier.graphicsLayer { translationX = x - 14f; translationY = y - 14f; scaleX = 1.2f - t * 0.4f; scaleY = 1.2f - t * 0.4f }) { CoinIcon(26.dp) }
        }
    }
}

@Composable
private fun LevelIntro(title: String, level: Int, target: Int, boss: String?, mutators: List<String>, onGo: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { App.sfx("whoosh"); a.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f)); delay(1300); App.sfx("ui_reward"); a.animateTo(0f, tween(250)); onGo() }
    Box(Modifier.fillMaxSize().background(Color(0x55000000).copy(alpha = 0.35f * a.value)), contentAlignment = Alignment.Center) {
        Column(Modifier.graphicsLayer { scaleX = a.value; scaleY = a.value; alpha = a.value.coerceIn(0f, 1f) }, horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedText("LEVEL $level", size = 26.sp, color = BV.Mustard)
            OutlinedText(title, size = 38.sp, stroke = 3.dp)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(BV.Cocoa.copy(alpha = 0.85f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text("Goal: earn $${formatNum(target.toLong())}", style = Type.Button.copy(fontSize = 18.sp))
            }
            boss?.let { Spacer(Modifier.height(6.dp)); Text(it, style = Type.Body.copy(color = Color.White, textAlign = TextAlign.Center), modifier = Modifier.widthIn(max = 300.dp)) }
            mutators.forEach { Text("⚡ $it", style = Type.Body.copy(color = BV.Mustard)) }
        }
    }
}

@Composable
private fun PauseMenu(onResume: () -> Unit, onRestart: () -> Unit, onHome: () -> Unit, onDebugWin: () -> Unit, onDebugCash: () -> Unit) {
    val d = App.repo.state.value
    LaunchedEffect(Unit) { App.audio.pauseMusic() }
    Box(Modifier.fillMaxSize().background(Color(0xAA1E1420)), contentAlignment = Alignment.Center) {
        Box {
            Panel(Modifier.width(300.dp).padding(top = 26.dp)) {
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToggleIcon(GIcon.MUSIC, d.music) { App.repo.update { it.copy(music = !it.music) }; App.applySettings() }
                    ToggleIcon(GIcon.SOUND, d.sfx) { App.repo.update { it.copy(sfx = !it.sfx) }; App.applySettings() }
                    ToggleIcon(GIcon.VIBRATE, d.haptics) { App.repo.update { it.copy(haptics = !it.haptics) }; App.applySettings() }
                }
                Spacer(Modifier.height(16.dp))
                GameButton("RESUME", Modifier.fillMaxWidth(), color = BV.Mint, dark = BV.MintDark) { App.audio.resumeMusic(); onResume() }
                Spacer(Modifier.height(8.dp))
                GameButton("RESTART", Modifier.fillMaxWidth(), color = BV.Mustard, dark = BV.MustardDark, height = 50.dp, textSize = 19.sp) { onRestart() }
                Spacer(Modifier.height(8.dp))
                GameButton("QUIT", Modifier.fillMaxWidth(), color = BV.Tomato, dark = BV.TomatoDark, height = 50.dp, textSize = 19.sp, sound = "ui_back") { onHome() }
                if (BuildConfig.DEBUG_TOOLS) {
                    Spacer(Modifier.height(10.dp))
                    Text("DEBUG TOOLS", style = Type.Small.copy(color = BV.TomatoDark))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GameButton("WIN ★★★", Modifier.weight(1f), color = BV.Lilac, dark = Color(0xFF8E73E0), height = 40.dp, textSize = 14.sp) { App.audio.resumeMusic(); onDebugWin() }
                        GameButton("+$500", Modifier.weight(1f), color = BV.Lilac, dark = Color(0xFF8E73E0), height = 40.dp, textSize = 14.sp) { App.audio.resumeMusic(); onDebugCash() }
                    }
                }
            }
            Ribbon("PAUSED", Modifier.align(Alignment.TopCenter).width(260.dp), color = BV.Teal, dark = BV.TealDark)
        }
    }
}

@Composable
fun ToggleIcon(icon: GIcon, on: Boolean, onClick: () -> Unit) {
    Box {
        RoundButton(color = if (on) BV.Mint else Color(0xFFBDB3AA), size = 56.dp, onClick = onClick) { GameIcon(icon, 30.dp) }
        if (!on) Canvas(Modifier.size(56.dp)) { drawLine(BV.Tomato, Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), 5.dp.toPx()) }
    }
}

@Composable
private fun ResultsOverlay(r: ShiftResult, reward: LevelReward?, boss: Boolean, onNext: () -> Unit, onRetry: () -> Unit, onHome: () -> Unit) {
    val starAnims = remember { List(3) { Animatable(0f) } }
    val count = remember { Animatable(0f) }
    var confetti by remember { mutableIntStateOf(0) }
    var doubled by remember { mutableStateOf(false) }
    var watching by remember { mutableStateOf(false) }
    LaunchedEffect(reward) {
        if (reward == null) return@LaunchedEffect
        delay(350)
        if (r.won) {
            for (k in 0 until r.stars) {
                starAnims[k].animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
                App.sfx("ui_star${k + 1}"); App.haptic(1)
                delay(120)
            }
            confetti++
        }
        count.animateTo(reward.bucks.toFloat(), tween(900, easing = LinearEasing))
    }
    if (watching) {
        MockAdOverlay(onReward = { watching = false; doubled = true; reward?.let { App.repo.doubleReward(it.bucks) } }, onCancel = { watching = false })
        return
    }
    Box(Modifier.fillMaxSize().background(Color(0xBB1E1420)), contentAlignment = Alignment.Center) {
        if (r.won) Box(Modifier.size(460.dp).graphicsLayer { alpha = 0.55f }.clip(CircleShape)) { Sunburst(Modifier.fillMaxSize()) }
        Box {
            Panel(Modifier.width(330.dp).padding(top = 28.dp)) {
                Spacer(Modifier.height(30.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    for (k in 0 until 3) {
                        val s = starAnims[k].value
                        Box(Modifier.offset(y = if (k == 1) (-10).dp else 0.dp), contentAlignment = Alignment.Center) {
                            StarIcon(if (k == 1) 74.dp else 60.dp, filled = false)
                            Box(Modifier.graphicsLayer { scaleX = 2f - s; scaleY = 2f - s; alpha = s }) { StarIcon(if (k == 1) 74.dp else 60.dp, filled = true) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (r.won) "Revenue $${formatNum(r.revenue.toLong())} / $${formatNum(r.target.toLong())}" else "So close! $${formatNum(r.revenue.toLong())} / $${formatNum(r.target.toLong())}",
                    style = Type.Heading.copy(fontSize = 17.sp, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ResultCell("Served", r.served.toString())
                    ResultCell("Walkouts", r.walkouts.toString())
                    ResultCell("Combo", "x" + String.format("%.1f", r.bestCombo))
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(2.dp, BV.CreamDark, RoundedCornerShape(14.dp)).padding(10.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    BucksIcon(34.dp)
                    OutlinedText(" +${formatNum((count.value * (if (doubled) 2 else 1)).toLong())}", size = 26.sp, color = BV.Mint)
                    if ((reward?.gems ?: 0) > 0) { Spacer(Modifier.width(12.dp)); GemIcon(28.dp); OutlinedText(" +${reward!!.gems}", size = 24.sp, color = BV.Gem) }
                }
                reward?.newCards?.takeIf { it.isNotEmpty() }?.let { cards ->
                    Text("New recipe cards: " + cards.joinToString { it.name }, style = Type.Small.copy(color = BV.TealDark, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                reward?.rankUp?.let { Text("Chef Rank up! → $it", style = Type.Heading.copy(fontSize = 16.sp, color = BV.MintDark), modifier = Modifier.padding(top = 4.dp)) }
                Spacer(Modifier.height(12.dp))
                if (App.ads.rewardedAvailable && !doubled && reward != null) {
                    GameButton("x2 REWARD (AD)", Modifier.fillMaxWidth(), color = BV.Mustard, dark = BV.MustardDark, height = 48.dp, textSize = 18.sp, icon = { GameIcon(GIcon.AD, 24.dp) }) { watching = true }
                    Spacer(Modifier.height(8.dp))
                }
                if (r.won) {
                    GameButton("NEXT LEVEL", Modifier.fillMaxWidth().pulse(0.97f, 1.03f), color = BV.Mint, dark = BV.MintDark, height = 58.dp, textSize = 24.sp) { onNext() }
                } else {
                    GameButton("TRY AGAIN", Modifier.fillMaxWidth().pulse(0.97f, 1.03f), color = BV.Tomato, dark = BV.TomatoDark, height = 58.dp, textSize = 24.sp, icon = { GameIcon(GIcon.RETRY, 26.dp) }) { onRetry() }
                    Text("Tip: upgrade your Kitchen for faster cooking and bigger stacks!", style = Type.Small.copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.won) GameButton("REPLAY", Modifier.weight(1f), color = BV.Teal, dark = BV.TealDark, height = 46.dp, textSize = 17.sp) { onRetry() }
                    GameButton("HOME", Modifier.weight(1f), color = BV.Cream, dark = BV.CreamDark, height = 46.dp, textSize = 17.sp, sound = "ui_back") { onHome() }
                }
            }
            Ribbon(if (r.won) (if (boss) "BOSS DEFEATED!" else "SHIFT COMPLETE!") else "SHIFT OVER", Modifier.align(Alignment.TopCenter).width(310.dp),
                color = if (r.won) BV.Mint else BV.Tomato, dark = if (r.won) BV.MintDark else BV.TomatoDark, size = 22.sp)
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}

@Composable
private fun ResultCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Type.Small)
        Text(value, style = Type.Heading.copy(fontSize = 20.sp))
    }
}
