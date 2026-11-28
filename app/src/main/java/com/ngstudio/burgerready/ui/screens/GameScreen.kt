package com.ngstudio.burgerready.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.BuildConfig
import com.ngstudio.burgerready.data.ChefRank
import com.ngstudio.burgerready.data.SessionDelta
import com.ngstudio.burgerready.game.Beat
import com.ngstudio.burgerready.game.Cosmetics
import com.ngstudio.burgerready.game.GoldStars
import com.ngstudio.burgerready.game.Speaker
import com.ngstudio.burgerready.game.Story
import com.ngstudio.burgerready.game.Tour
import com.ngstudio.burgerready.game.Trigger
import com.ngstudio.burgerready.game.Ev
import com.ngstudio.burgerready.game.Item
import com.ngstudio.burgerready.game.Restaurants
import com.ngstudio.burgerready.game.StatsCalc
import com.ngstudio.burgerready.game.Stores
import com.ngstudio.burgerready.game.Upgrade
import com.ngstudio.burgerready.render.GameSession
import com.ngstudio.burgerready.render.GameSurfaceView
import com.ngstudio.burgerready.render.HudState
import com.ngstudio.burgerready.render.SessionSnapshot
import com.ngstudio.burgerready.render.VisualStyle
import com.ngstudio.burgerready.render.snapshot
import com.ngstudio.burgerready.ui.kit.ArtImage
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
import com.ngstudio.burgerready.ui.kit.bob
import com.ngstudio.burgerready.ui.kit.formatNum
import com.ngstudio.burgerready.ui.kit.pulse
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Type
import kotlinx.coroutines.delay

/** Saves the running restaurant: progress, wallet and the counters that changed since [prev]. */
private class Saver(val store: String) {
    var prev: SessionSnapshot? = null
    var lastVersion = -1
    /** Wall-clock time from which play time counts (−1 while the app is in the background). */
    var activeSince = System.currentTimeMillis()
    fun pauseClock() { activeSince = -1L }
    fun resumeClock() { if (activeSince < 0L) activeSince = System.currentTimeMillis() }
    fun commit(snap: SessionSnapshot, onCards: (String) -> Unit = {}) {
        val now = System.currentTimeMillis()
        val played = if (activeSince >= 0L) (now - activeSince).coerceAtLeast(0L) else 0L
        if (activeSince >= 0L) activeSince = now
        val p = prev ?: run { prev = snap; return }
        val delta = SessionDelta(
            served = snap.served - p.served, revenue = snap.revenue - p.revenue,
            items = IntArray(snap.items.size) { snap.items[it] - p.items[it] },
            vips = snap.vips - p.vips, troubles = snap.troubles - p.troubles, trays = snap.trays - p.trays,
            rushes = snap.rushes - p.rushes, built = snap.built - p.built, critics = snap.critics - p.critics,
            bestCombo = if (snap.bestCombo > p.bestCombo) snap.bestCombo else 1f, cards = snap.cards,
        )
        val cards = App.repo.commitRestaurant(store, snap.progress, snap.cash, delta, played)
        prev = snap
        if (cards.isNotEmpty()) onCards(cards.joinToString { it.name })
    }
}

/**
 * One visit to a restaurant. Nothing here ends the game: the player builds, serves and leaves whenever they like,
 * and everything is saved every few seconds (and on pause/exit). Buying the NEW RESTAURANT pad shows a
 * celebration and opens the next location.
 */
@Composable
fun GameScreen(store: String, onExit: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val repo = App.repo
    val d = repo.state.value
    val cfg = remember(store) { Restaurants.config(store) }
    var hud by remember { mutableStateOf(HudState()) }
    var paused by remember { mutableStateOf(false) }
    var upgrades by remember { mutableStateOf(false) }
    var expandedTo by remember { mutableStateOf<String?>(null) }
    var cardToast by remember { mutableStateOf("") }
    // ---- story (game/Story.kt) and gold stars (game/GoldStars.kt)
    val completeOnEntry = remember(store) { repo.data.isComplete(store) }
    var beat by remember { mutableStateOf<Beat?>(null) }
    var beatScene by remember { mutableStateOf(false) }
    var nextBeatAt by remember { mutableLongStateOf(System.currentTimeMillis() + 2600L) }
    var voyageDone by remember { mutableStateOf(false) }
    var tourDone by remember { mutableStateOf(false) }
    var tracker by remember { mutableStateOf(Tracker()) }
    val skin = Cosmetics.SKINS.firstOrNull { it.id == d.selectedSkin } ?: Cosmetics.SKINS[0]
    val pet = Cosmetics.PETS.firstOrNull { it.id == d.selectedPet }
    val theme = Cosmetics.THEMES.firstOrNull { it.id == d.selectedTheme } ?: Cosmetics.THEMES[0]
    val style = remember(store) { VisualStyle(Stores.byId(store), theme, skin.species, skin.hat, skin.apron, pet?.model?.takeIf { it.isNotEmpty() }) }

    val view = remember(store) { GameSurfaceView(context, App.quality(context)) }
    val saver = remember(store) { Saver(store) }
    val session = remember(store) {
        GameSession(cfg, repo.stats(), style, repo.data.bucks, repo.data.progressOf(store),
            onEvent = { e, item, a -> onGameEvent(e, item, a) },
        ).also { saver.prev = it.world.snapshot() }
    }
    fun saveNow() { session.snapshot?.let { saver.commit(it) { c -> cardToast = "New recipe card: $c" } } }
    /** Gold stars count what happens after the restaurant is finished: from the start of a visit, or from the moment it was finished. */
    // gold stars belong to the 12 classic restaurants (the 36-star Golden Tour); World Tour restaurants have none
    val goldBase = remember(store) { mutableStateOf(if (completeOnEntry && !cfg.isTour) saver.prev else null) }
    fun showBeat(b: Beat, scene: Boolean) {
        beat = b; beatScene = scene
        if (scene) session.paused = true
    }
    fun finishBeat(b: Beat) {
        repo.markStorySeen(b.id)
        beat = null
        nextBeatAt = System.currentTimeMillis() + 9000L
        when (b.trigger) {
            Trigger.FINALE -> {
                if (b.id == "tour_finale") { tourDone = true; return }
                val pg = Story.postgame; if (pg.id !in repo.data.story) { showBeat(pg, true); return } else voyageDone = true
            }
            Trigger.POSTGAME -> { voyageDone = true; return }
            else -> {}
        }
        if (beatScene && expandedTo == null && !paused && !voyageDone) session.paused = false
        beatScene = false
    }

    LaunchedEffect(session) {
        view.game.session = session
        App.audio.playMusic("level_calm", "level_rush")
        App.audio.startLoop("sizzle_loop", 0.18f)
    }
    // pause + save when the app goes to the background
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, session) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_PAUSE) { saveNow(); saver.pauseClock(); if (expandedTo == null) { paused = true; session.paused = true } }
            if (ev == Lifecycle.Event.ON_RESUME) saver.resumeClock()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    DisposableEffect(store) {
        view.onResume()
        onDispose {
            view.game.session = null
            view.onPause()
            // The GL thread is paused now: read the final state directly (including unapplied UI purchases).
            val w = session.world
            w.cash = (w.cash + session.cashAdjust.getAndSet(0L)).coerceAtLeast(0L)
            saver.commit(w.snapshot())
            App.audio.stopLoop()
            App.audio.setRush(false)
        }
    }
    // HUD polling + autosave
    LaunchedEffect(session) {
        var saveT = 0L
        var goldT = 0L
        while (true) {
            withFrameNanos { }
            val h = session.hud
            // HudState is a data class: skip recomposition when the 15 Hz publish carries nothing new
            if (h !== hud && h != hud) {
                if (h.rush != hud.rush) App.audio.setRush(h.rush)
                hud = h
            }
            val now = System.currentTimeMillis()
            if (now - saveT > 3000) { saveT = now; saveNow() }
            if (now - goldT > 1000) {
                goldT = now
                // the last restaurant has no NEW RESTAURANT pad: it is finished when fully built, mid-visit
                if (goldBase.value == null && cfg.isLast && repo.data.isComplete(store)) goldBase.value = session.snapshot
                val snap = session.snapshot
                val base = goldBase.value
                val visit = if (snap != null && base != null) snap.visitSince(base, fromStart = completeOnEntry, combo = hud.combo) else null
                if (visit != null) {
                    val fresh = repo.awardGold(store, GoldStars.met(cfg.index, visit))
                    if (fresh > 0) {
                        cardToast = if (fresh == 1) "GOLD STAR! +${GoldStars.GEMS} gems" else "$fresh GOLD STARS! +${fresh * GoldStars.GEMS} gems"
                        App.sfx("ui_star3"); App.haptic(2)
                        if (repo.data.goldTotal() >= GoldStars.TOTAL) Story.beat("golden_book")?.takeIf { it.id !in repo.data.story }?.let { showBeat(it, true) }
                    }
                }
                tracker = Tracker.of(repo.data, cfg.index, store, visit)
            }
            delay(50)
        }
    }
    // the NEW RESTAURANT pad was bought
    LaunchedEffect(hud.expanded) {
        if (!hud.expanded || expandedTo != null) return@LaunchedEffect
        delay(900)
        saveNow()
        val next = repo.openNextRestaurant(store)
        if (next != null) {
            expandedTo = next; session.paused = true; App.audio.stopMusic(); App.sfx("level_win"); App.haptic(2)
            Story.completion(cfg.index)?.takeIf { it.id !in repo.data.story }?.let { showBeat(it, true) }
        }
    }
    LaunchedEffect(cardToast) { if (cardToast.isNotEmpty()) { delay(2600); cardToast = "" } }
    // Story director: at most one beat at a time, a pause between milestone beats, never over menus.
    LaunchedEffect(hud.cues, hud.built, beat, paused, upgrades, expandedTo, voyageDone, tourDone) {
        if (beat != null || paused || upgrades || expandedTo != null || voyageDone || tourDone) return@LaunchedEffect
        val wait = nextBeatAt - System.currentTimeMillis()
        // the grand finale doesn't wait for the pause between beats
        if (wait > 0 && Story.next(cfg.index, hud.cues, repo.data.story, completeOnEntry)?.trigger != Trigger.FINALE) delay(wait)
        val seen = repo.data.story
        val n = Story.next(cfg.index, hud.cues, seen, completeOnEntry)
        val b = when {
            n?.trigger == Trigger.FINALE -> n
            completeOnEntry && !cfg.isTour && "gold_intro" !in seen -> Story.beat("gold_intro")
            else -> n
        } ?: return@LaunchedEffect
        showBeat(b, b.trigger == Trigger.FINALE || b.scene)
    }
    BackHandler(enabled = expandedTo == null && !(beat != null && beatScene) && !voyageDone && !tourDone) {
        if (upgrades) { upgrades = false; return@BackHandler }
        paused = !paused; session.paused = paused
        if (paused) saveNow()
    }
    // Cut-scenes and the two celebration cards used to leave Back unhandled, so it closed the app mid-scene (the
    // completion beat and the RESTAURANT COMPLETE card were then lost for good). A scene waits for taps; the
    // cards take Back as their secondary button.
    BackHandler(enabled = (beat != null && beatScene) || expandedTo != null || voyageDone || tourDone) {
        when {
            beat != null && beatScene -> {}
            voyageDone -> { App.sfx("ui_back"); voyageDone = false; session.paused = false; App.audio.resumeMusic() }
            tourDone -> { App.sfx("ui_back"); tourDone = false; session.paused = false; App.audio.resumeMusic() }
            expandedTo != null -> { App.sfx("ui_back"); onExit() }
        }
    }

    // Entry curtain: the GL view needs a few hundred ms for its first frame (new context, model upload). A cream
    // cover that fades away on that frame replaces the old pop-in and blank flash.
    val curtain = remember(store) { Animatable(1f) }
    LaunchedEffect(view.firstFrame.value) { if (view.firstFrame.value) curtain.animateTo(0f, tween(280)) }

    Box(Modifier.fillMaxSize().background(Color(0xFFFFE9C8))) {
        AndroidView({ view }, Modifier.fillMaxSize())
        if (curtain.value > 0.001f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = curtain.value }.background(Color(0xFFFFF2DB)))
        JoystickOverlay(view)
        // In-game story cards sit at the top, under the trackers: the bottom half is where players steer from.
        val cardBeat = beat?.takeIf { !beatScene && !paused && !upgrades }
        if (expandedTo == null && !voyageDone && !tourDone) Hud(hud, cardToast, tracker, showTip = beat == null,
            story = cardBeat?.let { b -> { DialogueCard(b, compact = true) { finishBeat(b) } } },
            onPause = { paused = true; session.paused = true; saveNow() },
            onUpgrades = { upgrades = true })
        CoinFly(hud)
        WelcomeBanner(cfg.name, when {
            completeOnEntry && !cfg.isTour -> "Gold stars here: ${GoldStars.count(repo.data.goldOf(store))} / 3"
            cfg.isTour -> "${Tour.REGIONS[Tour.regionOf(cfg.index)].name} · stop ${Tour.stepOf(cfg.index) + 1} of ${Tour.PER_REGION}"
            hud.built == 0 -> "Stand on the glowing pad to build!"
            else -> "Welcome back, chef!"
        })
        if (upgrades) UpgradesSheet(hud.cash, session) { upgrades = false }
        if (paused && expandedTo == null) PauseMenu(
            onResume = { paused = false; session.paused = false; App.audio.resumeMusic() },
            onHome = { onExit() },
            onDebugBuild = { paused = false; session.paused = false; session.debugAction = 1 },
            onDebugCash = { paused = false; session.paused = false; session.debugAction = 2 },
            onDebugFinish = { paused = false; session.paused = false; session.debugAction = 3 },
            onDebugGold = { paused = false; session.paused = false; if (BuildConfig.DEBUG_TOOLS && repo.awardGold(store, 7) > 0) { cardToast = "GOLD STARS (debug)"; if (repo.data.goldTotal() >= GoldStars.TOTAL) Story.beat("golden_book")?.takeIf { it.id !in repo.data.story }?.let { showBeat(it, true) } } },
        )
        if (beat == null) expandedTo?.let { next -> ExpandOverlay(cfg.name, store, next, onGo = { onOpen(next) }, onHome = onExit) }
        beat?.let { b -> if (beatScene) StoryScene(b, sceneHeader(b, cfg.index)) { finishBeat(b) } }
        if (voyageDone && beat == null) VoyageCompleteOverlay(
            onTour = { voyageDone = false; onOpen(repo.openWorldTour()) },
            onKeep = { voyageDone = false; session.paused = false; App.audio.resumeMusic() }, onHome = onExit)
        if (tourDone && beat == null) TourCompleteOverlay(onKeep = { tourDone = false; session.paused = false; App.audio.resumeMusic() }, onHome = onExit)
    }
}

private fun onGameEvent(e: Ev, item: Item?, a: Float) {
    when (e) {
        Ev.PICKUP -> { App.sfx("pickup", 0.9f, 0.85f + kotlin.math.min(a, 14f) * 0.05f); App.haptic(0) }
        Ev.DROP -> App.sfx("drop", 0.8f, 0.9f + kotlin.math.min(a, 10f) * 0.03f)
        Ev.TRASH -> App.sfx("trash_dump", 0.7f)
        Ev.COIN_COLLECT -> { App.sfx("coin_cascade"); App.haptic(1) }
        Ev.PAY_TICK -> App.sfx("pad_fill", 0.6f, 0.9f + a * 0.5f)
        Ev.BUILT -> { App.sfx("build_pop"); App.haptic(3) }
        Ev.UPGRADED -> { App.sfx("upgrade"); App.haptic(2) }
        Ev.HIRED -> { App.sfx("hire"); App.haptic(2) }
        Ev.SERVED -> { App.sfx("cash_register", 0.8f); App.sfx("customer_happy", 0.5f) }
        Ev.ORDER -> App.sfx("customer_order", 0.6f)
        Ev.ANGRY -> App.sfx("customer_angry", 0.8f)
        Ev.EAT -> App.sfx("eat_chomp", 0.5f)
        Ev.COMBO_UP -> App.sfx(if (a >= 2.5f) "combo_up3" else if (a >= 1.75f) "combo_up2" else "combo_up1")
        Ev.COMBO_BREAK -> App.sfx("combo_break", 0.7f)
        Ev.RUSH_START -> App.sfx("rush_siren", 0.8f)
        Ev.RUSH_END -> App.sfx("ui_reward", 0.7f)
        Ev.TROUBLE -> App.sfx("fire_whoosh", 0.7f)
        Ev.TROUBLE_FIXED -> App.sfx("machine_fixed")
        Ev.POWERUP -> App.sfx("powerup")
        Ev.VIP -> App.sfx("vip_arrive", 0.7f)
        Ev.CRITIC -> App.sfx("critic_hmm", 0.8f)
        Ev.CAR_HORN -> App.sfx("car_horn", 0.5f)
        Ev.EXPANDED -> { App.sfx("ui_level_unlock"); App.haptic(3) }
        Ev.STEP -> App.sfx(if ((System.nanoTime() / 1000) % 2 == 0L) "footstep1" else "footstep2", 0.5f)
        Ev.CRITIC_HAPPY -> App.sfx("critic_hmm")
        Ev.STOLEN -> App.sfx("raccoon_chitter")
        Ev.SHOO -> App.sfx("whoosh")
        else -> {}
    }
}

@Composable
private fun Hud(h: HudState, cardToast: String, tracker: Tracker, showTip: Boolean, story: (@Composable () -> Unit)?, onPause: () -> Unit, onUpgrades: () -> Unit) {
    val d = App.repo.state.value
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(10.dp)) {
      // Everything at the top stacks in one column (top row, trackers, story card, rush/combo chips, card notices),
      // so nothing overlaps whatever subset is showing.
      Column(Modifier.align(Alignment.TopStart).fillMaxWidth()) {
        // ---- top row: chef level (star + XP ring) · restaurant name + build progress · wallet · pause.
        // One Row with a weighted middle, so the progress bar can never slide under the wallet on narrow screens.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val rank = ChefRank.rankOf(d.chefXp)
            val cur = ChefRank.xpFor(rank); val next = ChefRank.xpFor(rank + 1)
            val xpProg = ((d.chefXp - cur).toFloat() / (next - cur).coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(46.dp)) {
                    drawCircle(BV.Cocoa)
                    drawArc(BV.Mint, -90f, 360f * xpProg, false, style = Stroke(4.dp.toPx()))
                }
                StarIcon(34.dp)
                OutlinedText("$rank", size = if (rank >= 10) 13.sp else 15.sp, stroke = 1.4.dp)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                OutlinedText(h.storeName, size = when { h.storeName.length > 16 -> 12.sp; h.storeName.length > 12 -> 13.5.sp; else -> 15.sp }, stroke = 1.4.dp, align = TextAlign.Start, maxLines = 1)
                Box {
                    ProgressBar3D(h.built.toFloat() / h.total.coerceAtLeast(1), Modifier.fillMaxWidth(), color = if (h.built >= h.total) BV.Mint else BV.Mustard, height = 18.dp)
                    OutlinedText("${h.built}/${h.total} built", Modifier.align(Alignment.Center), size = 11.sp, stroke = 1.dp, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.height(40.dp).widthIn(min = 92.dp).shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(BV.Cocoa.copy(alpha = 0.88f)).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BucksIcon(30.dp); Spacer(Modifier.width(4.dp))
                Text(formatNum(h.cash), style = Type.Number.copy(fontSize = 19.sp), maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            RoundButton(color = BV.Tomato, size = 42.dp, onClick = onPause) { GameIcon(GIcon.PAUSE, 24.dp) }
        }
        // ---- trackers under the top row: the next gold star here (finished restaurants) and today's first open
        // task (daily tasks used to be invisible while playing)
        Column(Modifier.padding(top = 4.dp, start = 2.dp)) {
            tracker.gold?.let { g ->
                Row(Modifier.padding(top = 3.dp).clip(RoundedCornerShape(12.dp)).background(BV.Cocoa.copy(alpha = 0.78f)).padding(start = 6.dp, end = 9.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    GoldStarsRow(tracker.goldMask, 19.dp); Spacer(Modifier.width(6.dp))
                    Text(g, style = Type.Number.copy(fontSize = 13.sp, color = BV.Gold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // not during Hazel's tutorial: a new player has enough to read
            tracker.task?.takeIf { h.tip.isEmpty() }?.let { t ->
                Row(Modifier.padding(top = 3.dp).clip(RoundedCornerShape(12.dp)).background(BV.Cocoa.copy(alpha = 0.78f)).padding(start = 6.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(GIcon.MISSION, 16.dp); Spacer(Modifier.width(6.dp))
                    Text(t, style = Type.Number.copy(fontSize = 13.sp, color = if (tracker.taskDone) BV.Mint else Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // ---- story card (milestone beats; cut-scenes are full screen)
        if (story != null) Box(Modifier.fillMaxWidth().padding(top = 8.dp)) { story() }
        // ---- rush / combo / boosts, centred
        Column(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (h.rush) Box(Modifier.padding(top = 4.dp).pulse(0.9f, 1.1f, 350).clip(RoundedCornerShape(10.dp)).background(BV.Tomato).padding(horizontal = 12.dp, vertical = 3.dp)) { OutlinedText("RUSH HOUR!", size = 16.sp, stroke = 1.2.dp) }
            if (h.combo > 1.01f) {
                Box(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(BV.Mustard, BV.Tomato))).padding(horizontal = 10.dp, vertical = 2.dp)) {
                    OutlinedText("🔥 COMBO x${String.format("%.2f", h.combo).trimEnd('0').trimEnd('.')}", size = 14.sp, stroke = 1.2.dp)
                }
            }
            h.boosts.forEach { (name, t) ->
                Box(Modifier.padding(top = 3.dp).clip(RoundedCornerShape(8.dp)).background(BV.Cocoa.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 1.dp)) {
                    Text("$name ${t.toInt()}s", style = Type.Small.copy(color = BV.Gold, fontSize = 11.sp))
                }
            }
            AnimatedVisibility(cardToast.isNotEmpty(), Modifier.padding(top = 6.dp), enter = slideInVertically() + fadeIn(), exit = fadeOut()) {
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(BV.Teal).border(2.dp, BV.Cocoa, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(if (cardToast.contains("GOLD")) GIcon.STAR else GIcon.BOOK, 22.dp); Spacer(Modifier.width(6.dp))
                    Text(cardToast, style = Type.Number.copy(fontSize = 13.sp))
                }
            }
        }
      }
        // ---- bottom-centre: tutorial tip. It sits above the UPGRADES button (it used to overlap it) and below the
        // chef: the camera keeps the kitchen in the top half of the screen, so the top is the worst place for it.
        if (h.tip.isNotEmpty() && showTip) {
            // Hazel's tutorial lines, with her portrait (docs/STORY_SCRIPT.md §6)
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp).widthIn(max = 330.dp), verticalAlignment = Alignment.CenterVertically) {
                Portrait(Speaker.HAZEL, 44.dp)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.shadow(4.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.96f))
                    .border(2.dp, BV.Cocoa, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(h.tip, style = Type.Body.copy(fontSize = 14.sp))
                }
            }
        }
        // ---- trouble alerts (left)
        Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // static chips: an endless pulse here kept the whole Compose layer redrawing at 60 Hz in busy restaurants
            h.troubles.distinct().take(3).forEach { t ->
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(BV.Tomato).border(2.dp, BV.Cocoa, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("⚠ $t", style = Type.Number.copy(fontSize = 13.sp))
                }
            }
        }
        // ---- toasts (game events) and new-card notices
        var shownId by remember { mutableIntStateOf(0) }
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(h.toastId) { if (h.toastId != shownId && h.toast.isNotEmpty()) { shownId = h.toastId; visible = true; delay(1500); visible = false } }
        AnimatedVisibility(visible, Modifier.align(Alignment.Center).offset(y = (-90).dp), enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(), exit = fadeOut()) {
            OutlinedText(h.toast, size = 30.sp, color = BV.Mustard, stroke = 2.5.dp)
        }
        // ---- bottom-right: upgrades (with a badge when something is affordable)
        val canBuy = Upgrade.entries.any { d.upgrade(it) < it.maxLevel && h.cash >= it.cost(d.upgrade(it)) }
        // A short nudge when something becomes affordable, then the badge alone: an endless pulse kept the
        // Compose layer redrawing every frame on top of the game.
        val nudge = remember { Animatable(1f) }
        LaunchedEffect(canBuy) {
            if (canBuy) repeat(3) { nudge.animateTo(1.1f, tween(180)); nudge.animateTo(1f, tween(220)) }
        }
        Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                RoundButton(color = BV.Teal, size = 58.dp, onClick = onUpgrades, modifier = Modifier.graphicsLayer { scaleX = nudge.value; scaleY = nudge.value }) { GameIcon(GIcon.KITCHEN, 34.dp) }
                if (canBuy) com.ngstudio.burgerready.ui.kit.BadgeDot(modifier = Modifier.align(Alignment.TopEnd))
            }
            OutlinedText("UPGRADES", size = 11.sp, stroke = 1.2.dp)
        }
        // ---- stack capacity
        if (h.stack > 0) {
            Box(Modifier.align(Alignment.BottomStart).navigationBarsPadding().clip(RoundedCornerShape(12.dp)).background(BV.Cocoa.copy(alpha = 0.75f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("Stack ${h.stack}/${h.capacity}", style = Type.Number.copy(fontSize = 14.sp, color = if (h.stack >= h.capacity) BV.Mustard else Color.White))
            }
        }
        if (BuildConfig.DEBUG_TOOLS) Text("${h.fps} fps · ${h.drawCalls} dc", style = Type.Small.copy(color = Color(0xAA000000), fontSize = 10.sp), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** A short, non-blocking "you are here" banner when entering a restaurant. */
@Composable
private fun WelcomeBanner(name: String, sub: String) {
    var show by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { App.sfx("whoosh"); delay(2200); show = false }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(show, Modifier.align(Alignment.Center).offset(y = (-40).dp), enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(), exit = fadeOut(tween(300))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText(name, size = 36.sp, stroke = 3.dp)
                Box(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(14.dp)).background(BV.Cocoa.copy(alpha = 0.85f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(sub, style = Type.Button.copy(fontSize = 16.sp))
                }
            }
        }
    }
}

/** Kitchen upgrades without leaving the restaurant (paid from the cash in hand). */
@Composable
private fun UpgradesSheet(cash: Long, session: GameSession, onClose: () -> Unit) {
    val d = App.repo.state.value
    var confetti by remember { mutableIntStateOf(0) }
    var spent by remember { mutableStateOf(0L) }
    LaunchedEffect(cash) { spent = 0L }  // the HUD caught up with our purchases
    val wallet = cash - spent
    Box(Modifier.fillMaxSize().background(Color(0x881E1420)).clickable(remember { MutableInteractionSource() }, null) { onClose() }) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(BV.Cocoa).padding(top = 3.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(BV.Cream)
            .clickable(remember { MutableInteractionSource() }, null) { }.navigationBarsPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedText("UPGRADES", size = 24.sp, color = BV.Mustard, stroke = 2.dp, modifier = Modifier.weight(1f), align = TextAlign.Start)
                BucksIcon(26.dp); Text(" " + formatNum(wallet), style = Type.Heading.copy(fontSize = 18.sp)); Spacer(Modifier.width(10.dp))
                com.ngstudio.burgerready.ui.kit.CloseButton(onClick = onClose)
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Upgrade.entries.toList()) { u ->
                    val lvl = d.upgrade(u)
                    val maxed = lvl >= u.maxLevel
                    val cost = u.cost(lvl)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(2.dp, BV.CreamDark, RoundedCornerShape(16.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(BV.Mustard.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            ArtImage("upgrade_${u.id}", Modifier.fillMaxSize()) { Text(u.icon, fontSize = 24.sp) }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.label, style = Type.Heading.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Lv $lvl · ${u.desc}", style = Type.Small.copy(fontSize = 11.sp), maxLines = 2)
                        }
                        Spacer(Modifier.width(6.dp))
                        GameButton(if (maxed) "MAX" else formatNum(cost), Modifier.width(96.dp), enabled = !maxed && wallet >= cost, height = 42.dp, textSize = 15.sp,
                            icon = if (maxed) null else ({ BucksIcon(16.dp) }), sound = "upgrade") {
                            if (wallet >= cost && App.repo.data.upgrade(u) == lvl) {
                                session.cashAdjust.addAndGet(-cost)
                                spent += cost
                                App.repo.update { it.copy(upgrades = it.upgrades + (u.id to lvl + 1), counters = it.counters + ("upgrades" to it.counter("upgrades") + 1)) }
                                session.newStats = App.repo.stats()
                                confetti++; App.haptic(1)
                            }
                        }
                    }
                }
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}

@Composable
private fun PauseMenu(onResume: () -> Unit, onHome: () -> Unit, onDebugBuild: () -> Unit, onDebugCash: () -> Unit, onDebugFinish: () -> Unit, onDebugGold: () -> Unit) {
    val d = App.repo.state.value
    LaunchedEffect(Unit) { App.audio.pauseMusic() }
    Box(Modifier.fillMaxSize().background(Color(0xAA1E1420)), contentAlignment = Alignment.Center) {
        Box {
            Panel(Modifier.width(300.dp).padding(top = 26.dp)) {
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LabeledToggle(GIcon.MUSIC, "Music", d.music) { App.repo.update { it.copy(music = !it.music) }; App.applySettings() }
                    LabeledToggle(GIcon.SOUND, "Sound", d.sfx) { App.repo.update { it.copy(sfx = !it.sfx) }; App.applySettings() }
                    LabeledToggle(GIcon.VIBRATE, "Vibration", d.haptics) { App.repo.update { it.copy(haptics = !it.haptics) }; App.applySettings() }
                }
                Spacer(Modifier.height(12.dp))
                Text("Your restaurant is saved. Everything you build stays forever.", style = Type.Small.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                GameButton("RESUME", Modifier.fillMaxWidth(), color = BV.Mint, dark = BV.MintDark) { onResume() }
                Spacer(Modifier.height(8.dp))
                GameButton("HOME", Modifier.fillMaxWidth(), color = BV.Mustard, dark = BV.MustardDark, height = 50.dp, textSize = 19.sp, sound = "ui_back") { onHome() }
                if (BuildConfig.DEBUG_TOOLS) {
                    Spacer(Modifier.height(10.dp))
                    Text("DEBUG TOOLS", style = Type.Small.copy(color = BV.TomatoDark))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GameButton("BUILD ALL", Modifier.weight(1f), color = BV.Lilac, dark = Color(0xFF8E73E0), height = 40.dp, textSize = 14.sp) { App.audio.resumeMusic(); onDebugBuild() }
                        GameButton("+CASH", Modifier.weight(1f), color = BV.Lilac, dark = Color(0xFF8E73E0), height = 40.dp, textSize = 14.sp) { App.audio.resumeMusic(); onDebugCash() }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GameButton("FINISH", Modifier.weight(1f), color = BV.Lilac, dark = Color(0xFF8E73E0), height = 40.dp, textSize = 14.sp) { App.audio.resumeMusic(); onDebugFinish() }
                        GameButton("+GOLD", Modifier.weight(1f), color = BV.Lilac, dark = Color(0xFF8E73E0), height = 40.dp, textSize = 14.sp) { App.audio.resumeMusic(); onDebugGold() }
                    }
                }
            }
            Ribbon("PAUSED", Modifier.align(Alignment.TopCenter).width(260.dp), color = BV.Teal, dark = BV.TealDark)
        }
    }
}

/** Celebration after buying NEW RESTAURANT: the old place keeps earning, the next one is open. */
@Composable
private fun ExpandOverlay(oldName: String, oldId: String, next: String, onGo: () -> Unit, onHome: () -> Unit) {
    val s = Stores.byId(next)
    val fromIndex = Restaurants.indexOf(oldId)
    val regionDone = Tour.isFlagship(fromIndex)
    val nextRegion = if (s.isTour) Tour.REGIONS[Tour.regionOf(Restaurants.indexOf(next))] else null
    var confetti by remember { mutableIntStateOf(0) }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { confetti++; a.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f)) }
    Box(Modifier.fillMaxSize().background(Color(0xCC1E1420)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(480.dp).graphicsLayer { alpha = 0.5f }.clip(CircleShape)) { Sunburst(Modifier.fillMaxSize()) }
        Box(Modifier.graphicsLayer { scaleX = a.value; scaleY = a.value }) {
            Panel(Modifier.width(330.dp).padding(top = 28.dp)) {
              Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(30.dp))
                Text("$oldName is fully built and keeps earning ${formatNum(App.repo.incomeRate(oldId).toLong())} Bucks/hour while you're away.",
                    style = Type.Body.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(s.sky.toLong() or 0xFF000000), Color(s.accent.toLong() or 0xFF000000)))), contentAlignment = Alignment.Center) {
                    ArtImage(s.art, Modifier.fillMaxSize().bob(4f)) { RegionBadge(s, 110.dp) }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (regionDone) "NEXT REGION" else "NEXT STOP", style = Type.Small.copy(color = BV.TealDark, letterSpacing = 2.sp))
                OutlinedText(s.name, size = 26.sp, color = BV.Mustard)
                Text(if (nextRegion != null) "${nextRegion.name} · stop ${Tour.stepOf(Restaurants.indexOf(next)) + 1} of ${Tour.PER_REGION} · ${s.perk}" else "New menu, new look · ${s.perk}",
                    style = Type.Small.copy(textAlign = TextAlign.Center))
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) { GemIcon(26.dp); OutlinedText(if (regionDone) " +40" else " +15", size = 22.sp, color = BV.Gem) }
                Spacer(Modifier.height(12.dp))
                GameButton("LET'S GO!", Modifier.fillMaxWidth().pulse(0.97f, 1.03f), color = BV.Mint, dark = BV.MintDark, height = 58.dp, textSize = 24.sp) { onGo() }
                Spacer(Modifier.height(8.dp))
                GameButton("HOME", Modifier.fillMaxWidth(), color = BV.Cream, dark = BV.CreamDark, height = 44.dp, textSize = 16.sp, sound = "ui_back") { onHome() }
              }
            }
            Ribbon(if (regionDone) "REGION COMPLETE!" else "RESTAURANT COMPLETE!", Modifier.align(Alignment.TopCenter).width(320.dp), color = if (regionDone) BV.Mustard else BV.Mint, dark = if (regionDone) BV.MustardDark else BV.MintDark, size = 21.sp)
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}

@Composable
private fun JoystickOverlay(view: GameSurfaceView) {
    val j = view.joystick
    // Reads the touch version in the draw phase only: no per-frame recomposition and no redraw while idle
    // (a frame loop here kept the whole Compose layer redrawing at 60 Hz, competing with the GL thread).
    Canvas(Modifier.fillMaxSize()) {
        if (j.version.intValue < 0 || !j.active) return@Canvas
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
            Box(Modifier.graphicsLayer { translationX = x - 14f; translationY = y - 14f; scaleX = 1.2f - t * 0.4f; scaleY = 1.2f - t * 0.4f }) { BucksIcon(26.dp) }
        }
    }
}


/** A pause-menu toggle with its name and state under it (the bare icons were easy to misread). */
@Composable
private fun LabeledToggle(icon: GIcon, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ToggleIcon(icon, on, onClick)
        Text("$label ${if (on) "on" else "off"}", style = Type.Small.copy(fontSize = 11.sp, color = if (on) BV.Cocoa else BV.CocoaSoft), modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun ToggleIcon(icon: GIcon, on: Boolean, onClick: () -> Unit) {
    Box {
        RoundButton(color = if (on) BV.Mint else Color(0xFFBDB3AA), size = 56.dp, onClick = onClick) { GameIcon(icon, 30.dp) }
        if (!on) Canvas(Modifier.size(56.dp)) { drawLine(BV.Tomato, Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), 5.dp.toPx()) }
    }
}


/** HUD tracker lines: the next gold star in a finished restaurant, and today's first unfinished task. */
data class Tracker(val gold: String? = null, val goldMask: Int = 0, val task: String? = null, val taskDone: Boolean = false) {
    companion object {
        fun of(d: com.ngstudio.burgerready.data.SaveData, index: Int, store: String, visit: com.ngstudio.burgerready.game.VisitStats?): Tracker {
            var gold: String? = null
            val mask = d.goldOf(store)
            if (visit != null) {
                val g = GoldStars.goals(index).firstOrNull { ((mask shr it.star) and 1) == 0 }
                gold = if (g == null) "All gold stars earned!" else {
                    val v = GoldStars.value(g.kind, visit).coerceAtMost(g.target)
                    if (g.kind == com.ngstudio.burgerready.game.GoalKind.REVENUE) "Earn ${formatNum(g.target)}: ${formatNum(v)}"
                    else if (g.kind == com.ngstudio.burgerready.game.GoalKind.COMBO) g.text()
                    else "${g.text()}: $v/${g.target}"
                }
            }
            val m = d.missions.firstOrNull { !it.claimed && !it.done } ?: d.missions.firstOrNull { it.done && !it.claimed }
            // short: the pill sits over the kitchen in big restaurants (the clipboard icon already says "task")
            val task = m?.let { if (it.done) "Task done · claim at Home" else if (it.kind == "combo") missionText(it.kind, it.item, it.goal) else "${missionText(it.kind, it.item, it.goal)}: ${it.progress}/${it.goal}" }
            return Tracker(gold, mask, task, m?.done == true)
        }
    }
}

private fun sceneHeader(b: Beat, index: Int): String = when {
    b.id == "tour_open" -> "THE GOLDEN BOOK"
    index >= Restaurants.CLASSIC && b.trigger == Trigger.ARRIVE -> "WORLD TOUR · REGION ${Tour.regionOf(index) + 1} OF ${Tour.REGIONS.size}"
    index >= Restaurants.CLASSIC && b.trigger == Trigger.COMPLETE -> "REGION ${Tour.regionOf(index) + 1} STAMPED"
    b.id == "tour_finale" -> "THE WORLD TOUR"
    else -> sceneHeaderClassic(b, index)
}

private fun sceneHeaderClassic(b: Beat, index: Int): String = when (b.trigger) {
    Trigger.COMPLETE -> "PAGE ${index + 1} STAMPED"
    Trigger.FINALE -> "THE LAST PAGE"
    Trigger.POSTGAME -> "WHAT'S NEXT?"
    Trigger.EPILOGUE -> "THE GOLDEN BOOK"
    else -> "STORY"
}

/** After the finale: the voyage is complete, and the Golden Tour begins. */
@Composable
private fun VoyageCompleteOverlay(onTour: () -> Unit, onKeep: () -> Unit, onHome: () -> Unit) {
    var confetti by remember { mutableIntStateOf(0) }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { confetti++; App.sfx("level_win"); a.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f)) }
    Box(Modifier.fillMaxSize().background(Color(0xCC1E1420)).clickable(remember { MutableInteractionSource() }, null) { }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(480.dp).graphicsLayer { alpha = 0.5f }.clip(CircleShape)) { Sunburst(Modifier.fillMaxSize()) }
        Box(Modifier.graphicsLayer { scaleX = a.value; scaleY = a.value }) {
            Panel(Modifier.width(330.dp).padding(top = 28.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(30.dp))
                    Text("All twelve pages of Hazel's book are stamped.", style = Type.Body.copy(textAlign = TextAlign.Center))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { StarIcon(34.dp); OutlinedText(" THE GOLDEN TOUR", size = 22.sp, color = BV.Gold) }
                    Text("Every finished restaurant has 3 gold stars to earn. Each pays gems and boosts its idle income for good.",
                        style = Type.Small.copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 4.dp))
                    Text("Gold stars: ${App.repo.data.goldTotal()} / ${GoldStars.TOTAL}", style = Type.Heading.copy(fontSize = 18.sp), modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(12.dp))
                    // the golden book's blank pages: 25 regions, 500 more restaurants (docs/WORLD_TOUR_DESIGN.md)
                    GameButton("START THE WORLD TOUR", Modifier.fillMaxWidth().pulse(0.97f, 1.03f), color = BV.Tomato, dark = BV.TomatoDark, height = 56.dp, textSize = 20.sp) { onTour() }
                    Text("25 regions · 500 new restaurants", style = Type.Small.copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(8.dp))
                    GameButton("KEEP COOKING", Modifier.fillMaxWidth(), color = BV.Mint, dark = BV.MintDark, height = 46.dp, textSize = 18.sp) { onKeep() }
                    Spacer(Modifier.height(8.dp))
                    GameButton("HOME", Modifier.fillMaxWidth(), color = BV.Cream, dark = BV.CreamDark, height = 44.dp, textSize = 16.sp, sound = "ui_back") { onHome() }
                }
            }
            Ribbon("VOYAGE COMPLETE!", Modifier.align(Alignment.TopCenter).width(320.dp), color = BV.Mustard, dark = BV.MustardDark, size = 22.sp)
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}


/** After the World Tour's last restaurant: every region visited. */
@Composable
private fun TourCompleteOverlay(onKeep: () -> Unit, onHome: () -> Unit) {
    var confetti by remember { mutableIntStateOf(0) }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { confetti++; App.sfx("level_win"); a.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f)) }
    Box(Modifier.fillMaxSize().background(Color(0xCC1E1420)).clickable(remember { MutableInteractionSource() }, null) { }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(480.dp).graphicsLayer { alpha = 0.5f }.clip(CircleShape)) { Sunburst(Modifier.fillMaxSize()) }
        Box(Modifier.graphicsLayer { scaleX = a.value; scaleY = a.value }) {
            Panel(Modifier.width(330.dp).padding(top = 28.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(30.dp))
                    Text("${Restaurants.TOTAL} restaurants in ${Tour.REGIONS.size + 1} chapters. The golden book is full.", style = Type.Body.copy(textAlign = TextAlign.Center))
                    Text("Every restaurant keeps earning, and every one is still yours to play.", style = Type.Small.copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    GameButton("KEEP COOKING", Modifier.fillMaxWidth(), color = BV.Mint, dark = BV.MintDark, height = 56.dp, textSize = 22.sp) { onKeep() }
                    Spacer(Modifier.height(8.dp))
                    GameButton("HOME", Modifier.fillMaxWidth(), color = BV.Cream, dark = BV.CreamDark, height = 44.dp, textSize = 16.sp, sound = "ui_back") { onHome() }
                }
            }
            Ribbon("WORLD TOUR COMPLETE!", Modifier.align(Alignment.TopCenter).width(320.dp), color = BV.Mustard, dark = BV.MustardDark, size = 21.sp)
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}
