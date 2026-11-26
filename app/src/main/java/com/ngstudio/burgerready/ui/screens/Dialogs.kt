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
import com.ngstudio.burgerready.game.LevelGenerator
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
fun HomeDialogs(dialog: HomeDialog, onPlay: (Int) -> Unit, onClose: () -> Unit) {
    var current by remember(dialog) { mutableStateOf(dialog) }
    when (val d = current) {
        is HomeDialog.LevelInfo -> GameDialog(onClose) { LevelInfoCard(d.level, onPlay, onClose) }
        is HomeDialog.Unlock -> UnlockLevelDialog(d.level, onClose) { current = HomeDialog.LevelInfo(d.level) }
        HomeDialog.Login -> GameDialog(onClose) { LoginCard(onClose) }
        HomeDialog.Spin -> GameDialog(null) { SpinCard(onClose) }
        HomeDialog.Missions -> GameDialog(onClose) { MissionsCard(onClose) }
        HomeDialog.Income -> GameDialog(onClose) { IncomeCard(onClose) }
        HomeDialog.Rank -> GameDialog(onClose) { RankCard(onClose) }
        is HomeDialog.Reward -> RewardPopup(d.title, d.bucks, d.gems, onClose)
        is HomeDialog.StoreDetail -> GameDialog(onClose) { StoreDetailCard(d.id, onClose) }
    }
}

// --------------------------------------------------------------------------------------------- level info
@Composable
fun LevelInfoCard(level: Int, onPlay: (Int) -> Unit, onClose: () -> Unit) {
    val cfg = remember(level) { LevelGenerator.generate(level) }
    val stars = App.repo.data.starsOf(level)
    DialogCard("LEVEL $level", onClose, color = if (cfg.isBoss) BV.Tomato else BV.Teal, dark = if (cfg.isBoss) BV.TomatoDark else BV.TealDark) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(cfg.title, style = Type.Title)
            Row(Modifier.padding(vertical = 6.dp)) { for (k in 1..3) StarIcon(34.dp, filled = k <= stars) }
            cfg.boss?.let { b ->
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(BV.Tomato.copy(alpha = 0.15f)).padding(10.dp)) {
                    Text("👑 BOSS: ${b.blurb}", style = Type.Body.copy(textAlign = TextAlign.Center))
                }
                Spacer(Modifier.height(6.dp))
            }
            if (cfg.mutators.isNotEmpty()) {
                Text("Wild Shift", style = Type.Heading.copy(fontSize = 16.sp, color = BV.TomatoDark))
                cfg.mutators.forEach { Text("• ${it.label}: ${it.blurb}", style = Type.Small) }
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(2.dp, BV.CreamDark, RoundedCornerShape(14.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                InfoCell("Goal", "$" + formatNum(cfg.target.toLong()))
                InfoCell("Time", "${cfg.shiftSeconds.toInt() / 60}:${"%02d".format(cfg.shiftSeconds.toInt() % 60)}")
                InfoCell("Menu", cfg.menu.size.toString())
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reward up to ", style = Type.Body); BucksIcon(20.dp)
                Text(" " + formatNum((LevelGenerator.reward(level) * 1.6f).toLong()), style = Type.Body)
            }
            if (cfg.tutorial > 0) Text("Training shift — you can't lose this one!", style = Type.Small.copy(color = BV.MintDark))
            Spacer(Modifier.height(14.dp))
            GameButton("PLAY!", Modifier.fillMaxWidth().pulse(0.97f, 1.03f), color = BV.Mint, dark = BV.MintDark, height = 60.dp, textSize = 26.sp) { onPlay(level) }
        }
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
/** Debug builds only: offer a mock rewarded ad that unlocks the tapped level. */
@Composable
fun UnlockLevelDialog(level: Int, onClose: () -> Unit, onUnlocked: () -> Unit) {
    var watching by remember { mutableStateOf(false) }
    if (!BuildConfig.DEBUG_AD_UNLOCK) { onClose(); return }
    if (watching) {
        MockAdOverlay(onReward = { App.repo.adUnlock(level); App.sfx("ui_level_unlock"); onUnlocked() }, onCancel = { watching = false })
        return
    }
    GameDialog(onClose) {
        DialogCard("LOCKED", onClose, color = BV.Lilac, dark = Color(0xFF8E73E0)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                GameIcon(GIcon.LOCK, 72.dp)
                Spacer(Modifier.height(8.dp))
                Text("Level $level is locked", style = Type.Title.copy(fontSize = 22.sp))
                Text("Watch a short ad to unlock it now!", style = Type.Body.copy(textAlign = TextAlign.Center))
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
                                Text(if (r.bucks > 0) formatNum(r.bucks) else "${r.gems}", style = Type.Heading.copy(fontSize = 15.sp))
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
                    // labels
                    for ((i, s) in segs.withIndex()) {
                        val ang = (i * 360f / segs.size + rot.value) * PI.toFloat() / 180f
                        Box(Modifier.offset(x = (sin(ang) * 95).dp, y = (-cos(ang) * 95).dp).rotate(i * 360f / segs.size + rot.value), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (s.label == "CHEST") GameIcon(GIcon.CHEST, 26.dp) else if (s.gems > 0) GemIcon(22.dp) else BucksIcon(22.dp)
                                OutlinedText(if (s.label == "CHEST") "" else s.label, size = 14.sp, stroke = 1.2.dp)
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
                result?.let { i -> Text("You won ${if (segs[i].label == "CHEST") "a Chest!" else segs[i].label + if (segs[i].gems > 0) " gems!" else " Bucks!"}", style = Type.Heading) }
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
                    else GameButton(if (m.rewardGems > 0) "${m.rewardGems}💎" else formatNum(m.rewardBucks), Modifier.width(92.dp), enabled = m.done, height = 42.dp, textSize = 15.sp,
                        color = BV.Mustard, dark = BV.MustardDark, sound = "ui_reward") { App.repo.claimMission(m.id); App.haptic(1) }
                }
            }
            Spacer(Modifier.height(4.dp))
            val allClaimed = d.missions.isNotEmpty() && d.missions.all { it.claimed }
            GameButton(if (d.missionChestClaimed) "CHEST OPENED" else "OPEN BONUS CHEST", Modifier.fillMaxWidth(), enabled = allClaimed && !d.missionChestClaimed,
                color = BV.Tomato, dark = BV.TomatoDark, icon = { GameIcon(GIcon.CHEST, 28.dp) }, sound = "ui_chest_open") { App.repo.claimMissionChest() }
        }
    }
}

fun missionText(kind: String, item: String, goal: Int): String = when (kind) {
    "serve" -> "Serve $goal customers"
    "revenue" -> "Earn $$goal in shifts"
    "win" -> "Win $goal shifts"
    "stars3" -> "Get 3 stars on $goal shifts"
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
    Box {
        DialogCard("STORE INCOME", onClose, color = BV.Pink, dark = Color(0xFFE0668E)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                d.stores.keys.forEach { id ->
                    val s = Stores.byId(id)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = Type.Body, modifier = Modifier.weight(1f))
                        BucksIcon(18.dp); Text(" " + formatNum(App.repo.pendingIncome(id)), style = Type.Body)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Stores fill up for ${Stores.INCOME_CAP_HOURS.toInt()} hours while you're away.", style = Type.Small.copy(textAlign = TextAlign.Center))
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
                InfoCell("Stars", formatNum(d.totalStars().toLong()))
                InfoCell("Wins", formatNum(d.counter("wins")))
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
