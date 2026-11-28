package com.ngstudio.burgerready.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.BuildConfig
import com.ngstudio.burgerready.data.ChefRank
import com.ngstudio.burgerready.data.LoginRewards
import com.ngstudio.burgerready.data.SpinWheel
import com.ngstudio.burgerready.game.Stores
import com.ngstudio.burgerready.ui.kit.BucksIcon
import com.ngstudio.burgerready.ui.kit.CloseButton
import com.ngstudio.burgerready.ui.kit.ConfettiBurst
import com.ngstudio.burgerready.ui.kit.GIcon
import com.ngstudio.burgerready.ui.kit.GameButton
import com.ngstudio.burgerready.ui.kit.GameDialog
import com.ngstudio.burgerready.ui.kit.GameIcon
import com.ngstudio.burgerready.ui.kit.GemIcon
import com.ngstudio.burgerready.ui.kit.OutlinedText
import com.ngstudio.burgerready.ui.kit.Panel
import com.ngstudio.burgerready.ui.kit.ProgressBar3D
import com.ngstudio.burgerready.ui.kit.Ribbon
import com.ngstudio.burgerready.ui.kit.StarIcon
import com.ngstudio.burgerready.ui.kit.Sunburst
import com.ngstudio.burgerready.ui.kit.formatNum
import com.ngstudio.burgerready.ui.kit.pulse
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DialogCard(title: String, onClose: (() -> Unit)?, color: Color = BV.Tomato, dark: Color = BV.TomatoDark, content: @Composable () -> Unit) {
    Box {
        Panel(Modifier.widthIn(max = 380.dp).fillMaxWidth().padding(top = 26.dp), padding = 18.dp) {
            Spacer(Modifier.height(26.dp))
            content()
        }
        Ribbon(title, Modifier.align(Alignment.TopCenter).fillMaxWidth(0.9f), color = color, dark = dark)
        if (onClose != null) CloseButton(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = 16.dp), onClose)
    }
}

@Composable
fun HomeDialogs(dialog: HomeDialog, onPlay: () -> Unit, onClose: () -> Unit) {
    when (val d = dialog) {
        HomeDialog.UnlockNext -> UnlockNextDialog(onClose, onPlay)
        HomeDialog.Login -> GameDialog(onClose) { LoginCard(onClose) }
        HomeDialog.Spin -> GameDialog(null) { SpinCard(onClose) }
        HomeDialog.Missions -> GameDialog(onClose) { MissionsCard(onClose) }
        HomeDialog.Income -> GameDialog(onClose) { IncomeCard(onClose) }
        HomeDialog.WelcomeBack -> GameDialog(onClose) { WelcomeBackCard(onClose) }
        HomeDialog.Rank -> GameDialog(onClose) { RankCard(onClose) }
        is HomeDialog.Reward -> RewardPopup(d.title, d.bucks, d.gems, onClose)
        is HomeDialog.StoreDetail -> GameDialog(onClose) { StoreDetailCard(d.id, onClose, onPlay) }
    }
}

@Composable
private fun InfoCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Type.Small)
        Text(value, style = Type.Heading.copy(fontSize = 20.sp))
    }
}

// --------------------------------------------------------------------------------------------- unlock (debug ads)
/** Debug builds only: offer a mock rewarded ad that opens the next restaurant early. */
@Composable
fun UnlockNextDialog(onClose: () -> Unit, onPlay: () -> Unit) {
    var watching by remember { mutableStateOf(false) }
    if (!BuildConfig.DEBUG_AD_UNLOCK) { onClose(); return }
    val next = com.ngstudio.burgerready.game.Restaurants.storeAt(App.repo.data.frontier() + 1)
    if (watching) {
        MockAdOverlay(onReward = { App.repo.adUnlockNext(); App.sfx("ui_level_unlock"); onPlay() }, onCancel = { watching = false })
        return
    }
    GameDialog(onClose) {
        DialogCard("LOCKED", onClose, color = BV.Lilac, dark = Color(0xFF8E73E0)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                GameIcon(GIcon.LOCK, 72.dp)
                Spacer(Modifier.height(8.dp))
                Text(next.name, style = Type.Title.copy(fontSize = 22.sp))
                Text("Buy the NEW RESTAURANT pad to open it, or watch a short ad to open it now.", style = Type.Body.copy(textAlign = TextAlign.Center))
                Spacer(Modifier.height(4.dp))
                Text("DEBUG BUILD · mock ad", style = Type.Small.copy(color = BV.TomatoDark))
                Spacer(Modifier.height(16.dp))
                GameButton("WATCH AD", Modifier.fillMaxWidth(), color = BV.Mustard, dark = BV.MustardDark, icon = { GameIcon(GIcon.AD, 28.dp) }) { watching = true }
                Spacer(Modifier.height(8.dp))
                GameButton("NOT NOW", Modifier.fillMaxWidth(), color = Color(0xFFCFC5BA), dark = Color(0xFF9E958C), height = 46.dp, textSize = 18.sp, sound = "ui_back") { onClose() }
            }
        }
    }
}


/** Placeholder "rewarded ad" shown in debug builds (replace with a real ad SDK later). */
@Composable
fun MockAdOverlay(seconds: Int = 3, onReward: () -> Unit, onCancel: () -> Unit) {
    var left by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        App.audio.pauseMusic()
        while (left > 0) { delay(1000); left-- }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
        Sunburst(Modifier.fillMaxSize(), a = Color(0xFF242424), b = Color(0xFF2E2E2E))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedText("AD PLACEHOLDER", size = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text("Rewarded video will appear here.", style = Type.Body.copy(color = Color.White))
            Spacer(Modifier.height(24.dp))
            if (left > 0) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(BV.Mustard), contentAlignment = Alignment.Center) { OutlinedText("$left", size = 36.sp) }
            } else {
                GameButton("CLAIM REWARD", Modifier.width(240.dp), color = BV.Mint, dark = BV.MintDark) { App.audio.resumeMusic(); onReward() }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(24.dp)) { CloseButton { App.audio.resumeMusic(); onCancel() } }
    }
}

// --------------------------------------------------------------------------------------------- daily login
@Composable
fun LoginCard(onClose: () -> Unit) {
    val d = App.repo.state.value
    val can = App.repo.canClaimLogin()
    var confetti by remember { mutableIntStateOf(0) }
    Box {
        DialogCard("DAILY REWARDS", onClose, color = BV.Tomato) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val idx = d.loginStreak % 7
                for (row in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        val range = if (row == 0) 0..3 else 4..6
                        for (k in range) {
                            val r = LoginRewards.DAYS[k]
                            val today = k == idx && can
                            val done = k < idx
                            Column(
                                Modifier.weight(if (k == 6) 2f else 1f).clip(RoundedCornerShape(12.dp)).background(if (today) BV.Mustard else if (done) BV.Mint.copy(alpha = 0.5f) else Color.White)
                                    .border(2.dp, if (today) BV.Cocoa else BV.CreamDark, RoundedCornerShape(12.dp)).padding(vertical = 6.dp).then(if (today) Modifier.pulse(0.96f, 1.04f) else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("Day ${k + 1}", style = Type.Small.copy(color = BV.Cocoa))
                                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                    if (r.chest) GameIcon(GIcon.CHEST, 34.dp) else if (r.bucks > 0) BucksIcon(30.dp) else GemIcon(28.dp)
                                }
                                Text(if (r.bucks > 0) formatNum(App.repo.loginBucks(k)) else "${r.gems}", style = Type.Heading.copy(fontSize = 15.sp), maxLines = 1)
                                if (r.bucks > 0 && r.gems > 0) Text("+${r.gems} gems", style = Type.Small.copy(fontSize = 10.sp, color = BV.TealDark))
                                if (done) Text("✓", style = Type.Heading.copy(color = BV.MintDark, fontSize = 14.sp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GameButton(if (can) "CLAIM" else "COME BACK TOMORROW", Modifier.fillMaxWidth(), enabled = can, color = BV.Mint, dark = BV.MintDark, sound = "ui_reward") {
                    App.repo.claimLogin(); confetti++; App.haptic(2)
                }
            }
        }
        ConfettiBurst(confetti, Modifier.matchParentSizeCompat())
    }
}

@Composable
private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().height(420.dp)

// --------------------------------------------------------------------------------------------- spin wheel
@Composable
fun SpinCard(onClose: () -> Unit) {
    val rot = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var spinning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    var watching by remember { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }
    val segs = SpinWheel.SEGMENTS
    androidx.activity.compose.BackHandler(enabled = spinning) { } // finish the spin before the dialog can close
    if (watching) { MockAdOverlay(onReward = { App.repo.grantExtraSpin(); watching = false }, onCancel = { watching = false }); return }
    Box {
        DialogCard("LUCKY SPIN", if (spinning) null else onClose, color = BV.Lilac, dark = Color(0xFF8E73E0)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(0.9f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().rotate(rot.value)) {
                        val r = size.minDimension / 2
                        drawCircle(BV.Cocoa, r)
                        val sweep = 360f / segs.size
                        for ((i, s) in segs.withIndex()) {
                            drawArc(Color(s.color), -90f + i * sweep - sweep / 2, sweep, true, Offset(size.width / 2 - r * 0.93f, size.height / 2 - r * 0.93f), androidx.compose.ui.geometry.Size(r * 1.86f, r * 1.86f))
                        }
                        for (i in segs.indices) {
                            val a = (-90f + i * sweep - sweep / 2) * PI.toFloat() / 180f
                            drawLine(BV.Cocoa, center, Offset(center.x + cos(a) * r * 0.93f, center.y + sin(a) * r * 0.93f), 3.dp.toPx())
                        }
                        for (k in 0 until 24) {
                            val a = k * 15f * PI.toFloat() / 180f
                            drawCircle(if (k % 2 == 0) Color.White else BV.Gold, 4.dp.toPx(), Offset(center.x + cos(a) * r * 0.965f, center.y + sin(a) * r * 0.965f))
                        }
                    }
                    // labels: centred at 72 dp, inside the band the pointer covers (they used to sit under it at 95 dp)
                    for ((i, s) in segs.withIndex()) {
                        val ang = (i * 360f / segs.size + rot.value) * PI.toFloat() / 180f
                        Box(Modifier.offset(x = (sin(ang) * 72).dp, y = (-cos(ang) * 72).dp).rotate(i * 360f / segs.size + rot.value), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (s.label == "CHEST") GameIcon(GIcon.CHEST, 26.dp) else if (s.gems > 0) GemIcon(22.dp) else BucksIcon(22.dp)
                                OutlinedText(if (s.label == "CHEST") "" else spinLabel(s), size = if (spinLabel(s).length > 4) 12.sp else 14.sp, stroke = 1.2.dp, maxLines = 1)
                            }
                        }
                    }
                    Box(Modifier.size(56.dp).clip(CircleShape).background(BV.Cocoa).padding(4.dp).clip(CircleShape).background(BV.Tomato), contentAlignment = Alignment.Center) { OutlinedText("GO", size = 18.sp) }
                    // pointer
                    Canvas(Modifier.align(Alignment.TopCenter).size(34.dp, 40.dp).offset(y = (-10).dp)) {
                        val p = Path().apply { moveTo(size.width / 2, size.height); lineTo(0f, 0f); lineTo(size.width, 0f); close() }
                        drawPath(p, BV.Tomato); drawPath(p, BV.Cocoa, style = Stroke(3.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(10.dp))
                result?.let { i -> Text("You won ${if (segs[i].label == "CHEST") "a chest!" else spinLabel(segs[i]) + if (segs[i].gems > 0) " gems!" else " Bucks!"}", style = Type.Heading) }
                Spacer(Modifier.height(8.dp))
                val can = App.repo.canSpin()
                if (can) {
                    GameButton(if (App.repo.state.value.extraSpins > 0 && App.repo.state.value.lastSpinDay == App.repo.today()) "EXTRA SPIN" else "FREE SPIN", Modifier.fillMaxWidth(), enabled = !spinning, color = BV.Mint, dark = BV.MintDark) {
                        val idx = App.repo.spin() ?: return@GameButton
                        spinning = true; result = null
                        scope.launch {
                            val target = rot.value - rot.value % 360f + 360f * 6 - idx * (360f / segs.size)
                            var lastTick = 0
                            launch {
                                while (spinning) { val t = (rot.value / (360f / segs.size)).toInt(); if (t != lastTick) { lastTick = t; App.sfx("ui_spin_tick", 0.8f) }; delay(16) }
                            }
                            rot.animateTo(target, tween(4200, easing = CubicBezierEasing(0.15f, 0.85f, 0.25f, 1f)))
                            spinning = false; result = idx; confetti++; App.sfx("ui_spin_win"); App.haptic(2)
                        }
                    }
                } else if (App.ads.rewardedAvailable) {
                    GameButton("SPIN AGAIN (AD)", Modifier.fillMaxWidth(), enabled = !spinning, color = BV.Mustard, dark = BV.MustardDark, icon = { GameIcon(GIcon.AD, 26.dp) }) { watching = true }
                } else {
                    Text("Next free spin tomorrow!", style = Type.Body)
                }
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxWidth().height(460.dp))
    }
}

/** What a wheel segment shows: gems as they are, Bucks scaled to the newest restaurant (what [GameRepo.spin] pays). */
private fun spinLabel(s: SpinWheel.Segment): String = if (s.gems > 0) "${s.gems}" else formatNum(App.repo.scaledBucks(s.bucks))

// --------------------------------------------------------------------------------------------- welcome back
/**
 * A returning player with idle income and today's calendar reward gets one card with one button, instead of the
 * income popup followed by the daily calendar (round 3 stacked prologue → income → daily). Closing it loses
 * nothing: the income keeps waiting on the INCOME pill and the day on the Daily button.
 */
@Composable
fun WelcomeBackCard(onClose: () -> Unit) {
    val d = App.repo.state.value
    val canLogin = App.repo.canClaimLogin()
    val pending = App.repo.totalPendingIncome()
    var got by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var confetti by remember { mutableIntStateOf(0) }
    LaunchedEffect(got) { if (got != null) { delay(1600); onClose() } }
    Box {
        DialogCard("WELCOME BACK!", onClose, color = BV.Teal, dark = BV.TealDark) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                val open = d.stores.size
                WelcomeRow(GIcon.STORE, "While you were away", if (open == 1) "Your restaurant kept cooking" else "Your $open restaurants kept cooking", BV.Pink.copy(alpha = 0.22f)) {
                    if (got != null) Text("✓ Collected", style = Type.Heading.copy(fontSize = 16.sp, color = BV.MintDark), maxLines = 1)
                    else { BucksIcon(24.dp); Text(" " + formatNum(pending), style = Type.Heading.copy(fontSize = 19.sp), maxLines = 1) }
                }
                if (canLogin || got != null) {
                    Spacer(Modifier.height(8.dp))
                    val idx = (if (got != null) d.loginStreak + 6 else d.loginStreak) % 7
                    val r = LoginRewards.DAYS[idx]
                    WelcomeRow(if (r.chest) GIcon.CHEST else GIcon.CALENDAR, "Daily reward · Day ${idx + 1}", null, BV.Mustard.copy(alpha = 0.25f), streak = idx) {
                        if (got != null) Text("✓ Claimed", style = Type.Heading.copy(fontSize = 16.sp, color = BV.MintDark), maxLines = 1)
                        else {
                            if (r.bucks > 0) { BucksIcon(22.dp); Text(" " + formatNum(App.repo.loginBucks(idx)), style = Type.Heading.copy(fontSize = 17.sp), maxLines = 1) }
                            if (r.gems > 0) { Spacer(Modifier.width(4.dp)); GemIcon(20.dp); Text(" ${r.gems}", style = Type.Heading.copy(fontSize = 17.sp), maxLines = 1) }
                            if (r.chest) { Spacer(Modifier.width(4.dp)); GameIcon(GIcon.CHEST, 22.dp) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                val g = got
                if (g != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (g.first > 0) { BucksIcon(30.dp); OutlinedText(" +${formatNum(g.first)}  ", size = 24.sp, color = BV.Mint) }
                        if (g.second > 0) { GemIcon(28.dp); OutlinedText(" +${g.second}", size = 24.sp, color = BV.Gem) }
                    }
                } else {
                    GameButton("COLLECT ALL", Modifier.fillMaxWidth(), color = BV.Mint, dark = BV.MintDark, sound = "coin_cascade", icon = { BucksIcon(26.dp) }) {
                        val b0 = App.repo.data.bucks; val g0 = App.repo.data.gems
                        App.repo.collectIncome()
                        if (App.repo.canClaimLogin()) { App.repo.claimLogin(); App.sfx("ui_reward") }
                        got = (App.repo.data.bucks - b0) to (App.repo.data.gems - g0)
                        confetti++; App.haptic(2)
                    }
                }
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxWidth().height(380.dp))
    }
}

/** One reward line of the welcome card: icon, title across the full width, then the amount (and the week's dots). */
@Composable
private fun WelcomeRow(icon: GIcon, title: String, sub: String?, tint: Color, streak: Int = -1, amount: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(2.dp, BV.CreamDark, RoundedCornerShape(16.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(tint), contentAlignment = Alignment.Center) { GameIcon(icon, 38.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Heading.copy(fontSize = 15.sp), maxLines = 1)
            if (sub != null) Text(sub, style = Type.Small.copy(fontSize = 12.sp), maxLines = 1)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                amount()
                if (streak >= 0) {
                    Spacer(Modifier.weight(1f))
                    for (k in 0 until 7) Box(Modifier.padding(start = 3.dp).size(if (k == streak) 9.dp else 7.dp).clip(CircleShape)
                        .background(if (k < streak) BV.Mint else if (k == streak) BV.Mustard else BV.CreamDark))
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------- missions
@Composable
fun MissionsCard(onClose: () -> Unit) {
    val d = App.repo.state.value
    DialogCard("DAILY TASKS", onClose, color = BV.Mint, dark = BV.MintDark) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.missions.forEach { m ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(2.dp, BV.CreamDark, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(missionText(m.kind, m.item, m.goal), style = Type.Body)
                        ProgressBar3D(m.progress.toFloat() / m.goal, Modifier.fillMaxWidth().padding(top = 4.dp), height = 14.dp)
                        Text("${m.progress}/${m.goal}", style = Type.Small)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (m.claimed) Text("✓", style = Type.Title.copy(color = BV.MintDark))
                    else GameButton(if (m.rewardGems > 0) "${m.rewardGems}" else formatNum(m.rewardBucks), Modifier.width(96.dp), enabled = m.done, height = 42.dp, textSize = 15.sp,
                        color = BV.Mustard, dark = BV.MustardDark, sound = "ui_reward",
                        icon = { if (m.rewardGems > 0) GemIcon(18.dp) else BucksIcon(18.dp) }) { App.repo.claimMission(m.id); App.haptic(1) }
                }
            }
            Spacer(Modifier.height(4.dp))
            val allClaimed = d.missions.isNotEmpty() && d.missions.all { it.claimed }
            GameButton(if (d.missionChestClaimed) "CHEST OPENED" else "OPEN BONUS CHEST", Modifier.fillMaxWidth(), enabled = allClaimed && !d.missionChestClaimed,
                color = BV.Tomato, dark = BV.TomatoDark, icon = { GameIcon(GIcon.CHEST, 28.dp) }, sound = "ui_chest_open") { App.repo.claimMissionChest() }
        }
    }
}

fun missionText(kind: String, item: String, goal: Long): String = when (kind) {
    "serve" -> "Serve $goal customers"
    "revenue" -> "Earn $$goal from guests"
    "build" -> "Build or upgrade $goal things"
    "rush" -> "Survive $goal rush hours"
    "combo" -> "Reach a x${goal / 10f} combo"
    "item" -> "Serve $goal ${item.replaceFirstChar { it.uppercase() }}"
    "trays" -> "Clean $goal trays"
    "troubles" -> "Fix $goal troubles"
    "vips" -> "Serve $goal VIP guests"
    else -> kind
}

// --------------------------------------------------------------------------------------------- store income
@Composable
fun IncomeCard(onClose: () -> Unit) {
    val d = App.repo.state.value
    var collected by remember { mutableStateOf(-1L) }
    var confetti by remember { mutableIntStateOf(0) }
    // close by itself shortly after collecting (it used to sit there showing "COLLECT 0")
    LaunchedEffect(collected) { if (collected >= 0) { delay(1400); onClose() } }
    Box {
        DialogCard("IDLE INCOME", onClose, color = BV.Pink, dark = Color(0xFFE0668E)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Stores.ALL.forEach { s ->
                    if (s.id !in d.stores) return@forEach
                    val id = s.id
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = Type.Body, modifier = Modifier.weight(1f))
                        BucksIcon(18.dp); Text(" " + formatNum(App.repo.pendingIncome(id)), style = Type.Body)
                    }
                }
                // World Tour restaurants: one row per region (up to 20 restaurants each)
                com.ngstudio.burgerready.game.Tour.REGIONS.forEachIndexed { r, reg ->
                    val ids = com.ngstudio.burgerready.game.Tour.storeIds(r).filter { it in d.stores }
                    if (ids.isEmpty()) return@forEachIndexed
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${reg.name} (${ids.size})", style = Type.Body, modifier = Modifier.weight(1f), maxLines = 1)
                        BucksIcon(18.dp); Text(" " + formatNum(ids.sumOf { App.repo.pendingIncome(it) }), style = Type.Body)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Your restaurants keep earning for up to ${Stores.INCOME_CAP_HOURS.toInt()} hours while you're away.", style = Type.Small.copy(textAlign = TextAlign.Center))
                Spacer(Modifier.height(10.dp))
                if (collected >= 0) Text("+${formatNum(collected)} Bucks!", style = Type.Title.copy(color = BV.MintDark))
                val pending = App.repo.totalPendingIncome()
                GameButton("COLLECT ${formatNum(pending)}", Modifier.fillMaxWidth(), enabled = pending > 0, color = BV.Mint, dark = BV.MintDark, sound = "coin_cascade",
                    icon = { BucksIcon(26.dp) }) { collected = App.repo.collectIncome(); confetti++; App.haptic(2) }
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxWidth().height(360.dp))
    }
}

// --------------------------------------------------------------------------------------------- chef rank
@Composable
fun RankCard(onClose: () -> Unit) {
    val d = App.repo.state.value
    val rank = ChefRank.rankOf(d.chefXp)
    val next = ChefRank.xpFor(rank + 1); val cur = ChefRank.xpFor(rank)
    DialogCard("CHEF RANK", onClose, color = BV.Teal, dark = BV.TealDark) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Rank $rank", style = Type.Title)
            ProgressBar3D((d.chefXp - cur).toFloat() / (next - cur).coerceAtLeast(1), Modifier.fillMaxWidth(), color = BV.Teal)
            Text("${d.chefXp - cur} / ${next - cur} XP · earn XP by serving guests", style = Type.Small)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                InfoCell("Served", formatNum(d.counter("served")))
                InfoCell("Built", formatNum(d.builtTotal().toLong()))
                InfoCell("Restaurants", "${d.stores.size}")
            }
            Spacer(Modifier.height(12.dp))
            val pendingGems = (rank - d.chefRankClaimed).coerceAtLeast(0) * 5
            GameButton(if (pendingGems > 0) "CLAIM $pendingGems GEMS" else "KEEP COOKING!", Modifier.fillMaxWidth(), enabled = pendingGems > 0, color = BV.Mint, dark = BV.MintDark, sound = "ui_reward") { App.repo.claimRankRewards() }
        }
    }
}

// --------------------------------------------------------------------------------------------- reward popup
@Composable
fun RewardPopup(title: String, bucks: Long, gems: Int, onClose: () -> Unit) {
    var confetti by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { confetti++; App.sfx("ui_reward"); App.haptic(2) }
    GameDialog(onClose) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(360.dp).clip(CircleShape)) { Sunburst(Modifier.fillMaxSize()) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText(title, size = 30.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (bucks > 0) { BucksIcon(44.dp); OutlinedText(" +${formatNum(bucks)}  ", size = 28.sp) }
                    if (gems > 0) { GemIcon(40.dp); OutlinedText(" +$gems", size = 28.sp) }
                }
                Spacer(Modifier.height(16.dp))
                GameButton("AWESOME!", Modifier.width(200.dp), color = BV.Mint, dark = BV.MintDark) { onClose() }
            }
            ConfettiBurst(confetti, Modifier.size(380.dp))
        }
    }
}
