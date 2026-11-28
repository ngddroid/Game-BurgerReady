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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.BuildConfig
import com.ngstudio.burgerready.game.Restaurants
import com.ngstudio.burgerready.game.Stores
import com.ngstudio.burgerready.game.Tour
import com.ngstudio.burgerready.ui.kit.ArtImage
import com.ngstudio.burgerready.ui.kit.BadgeDot
import com.ngstudio.burgerready.ui.kit.GIcon
import com.ngstudio.burgerready.ui.kit.GameButton
import com.ngstudio.burgerready.ui.kit.GameIcon
import com.ngstudio.burgerready.ui.kit.OutlinedText
import com.ngstudio.burgerready.ui.kit.ProgressBar3D
import com.ngstudio.burgerready.ui.kit.bob
import com.ngstudio.burgerready.ui.kit.formatNum
import com.ngstudio.burgerready.ui.kit.pulse
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun rgb(c: Int) = Color(c.toLong() or 0xFF000000)

private fun lum(c: Int): Float = (0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)) / 255f

/**
 * Title colour for a restaurant: its trim colour when that reads well as white-outlined display text, else its
 * accent, else tomato. Dark trims (City Rooftop navy, Mountain Lodge brown) used to give dark-on-dark titles.
 */
fun titleColor(s: com.ngstudio.burgerready.game.StoreDef): Color = when {
    lum(s.trim) in 0.33f..0.82f -> rgb(s.trim)
    lum(s.accent) in 0.33f..0.82f -> rgb(s.accent)
    else -> BV.Tomato
}

/**
 * The hub: the restaurant you are building (hero art, build progress, idle income), the chain of all
 * restaurants, and one big PLAY button that drops you back exactly where you left off.
 */
@Composable
fun PlayTab(onPlay: () -> Unit, openDialog: (HomeDialog) -> Unit, onRecipeBook: () -> Unit, onAchievements: () -> Unit, onVoyage: () -> Unit, onMap: () -> Unit) {
    val d = App.repo.state.value
    val store = Stores.byId(d.selectedStore)
    val cfg = Restaurants.config(store.id)
    val progress = d.progressOf(store.id)
    val built = progress.builtCount(cfg)
    val complete = progress.expanded(cfg) || built >= cfg.buildPads
    var toast by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var shakeIdx by remember { mutableStateOf(-1) }
    LaunchedEffect(toast) { if (toast.isNotEmpty()) { delay(2200); toast = "" } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(rgb(store.sky), rgb(store.accent).copy(alpha = 0.55f), BV.Cream)))) {
        // soft clouds
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            for (k in 0 until 6) {
                val y = size.height * (0.12f + k * 0.13f)
                val x = w * (if (k % 2 == 0) 0.12f else 0.86f)
                drawCircle(Color.White.copy(alpha = 0.22f), 34.dp.toPx() + k * 4f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.18f), 22.dp.toPx(), Offset(x + 30.dp.toPx(), y + 8.dp.toPx()))
            }
        }
        Column(Modifier.fillMaxSize().padding(top = 84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // ---- hero: the restaurant art between the side buttons
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                Box(Modifier.align(Alignment.Center).size(220.dp).bob(5f, 2200), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(200.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
                    if (store.isTour) ArtImage(store.art, Modifier.size(220.dp)) { RegionBadge(store, 190.dp) }
                    else ArtImage("store_${store.id}", Modifier.size(220.dp)) { GameIcon(GIcon.STORE, 120.dp, tint = rgb(store.trim)) }
                }
                Column(Modifier.align(Alignment.TopStart).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SideButton(GIcon.COMPASS, "Voyage", App.repo.voyageHasNew(), BV.Mustard, onVoyage)
                    SideButton(GIcon.BOOK, "Recipes", false, BV.Teal, onRecipeBook)
                    SideButton(GIcon.TROPHY, "Trophies", App.repo.unclaimedAchievements() > 0, BV.Mustard, onAchievements)
                }
                Column(Modifier.align(Alignment.TopEnd).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SideButton(GIcon.SPIN, "Spin", App.repo.canSpin(), BV.Lilac) { openDialog(HomeDialog.Spin) }
                    SideButton(GIcon.MISSION, "Tasks", d.missions.any { it.done && !it.claimed }, BV.Mint) { openDialog(HomeDialog.Missions) }
                    SideButton(GIcon.CALENDAR, "Daily", App.repo.canClaimLogin(), BV.Tomato) { openDialog(HomeDialog.Login) }
                }
            }
            // ---- name + progress card
            Column(
                Modifier.padding(horizontal = 22.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                    .background(BV.Cocoa).padding(3.dp).clip(RoundedCornerShape(18.dp)).background(BV.Cream).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedText(store.name, size = 26.sp, color = titleColor(store), stroke = 2.dp)
                Text(if (store.isTour) "${Tour.REGIONS[Tour.regionOf(cfg.index)].name} · stop ${Tour.stepOf(cfg.index) + 1} of ${Tour.PER_REGION} · ${store.perk}"
                    else "Restaurant ${cfg.index + 1} of ${Restaurants.CLASSIC} · ${store.perk}", style = Type.Small.copy(textAlign = TextAlign.Center), maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth()) {
                    ProgressBar3D(built.toFloat() / cfg.buildPads, Modifier.fillMaxWidth(), color = if (complete) BV.Mint else BV.Mustard, height = 24.dp)
                    Text(if (complete) "FULLY BUILT!" else "Built $built / ${cfg.buildPads}", style = Type.Number.copy(fontSize = 14.sp, color = BV.Cocoa), modifier = Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.height(4.dp))
                // idle income lives here now (the side columns hold Voyage / Recipes / Trophies and Spin / Tasks / Daily)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Earns ${formatNum(App.repo.incomeRate(store.id).toLong())}/hour while you're away", style = Type.Small.copy(color = BV.TealDark), modifier = Modifier.weight(1f), maxLines = 2)
                    val pending = App.repo.totalPendingIncome() >= 50
                    Box {
                        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(BV.Pink).border(2.dp, BV.Cocoa, RoundedCornerShape(12.dp))
                            .clickable { App.sfx("ui_tap"); openDialog(HomeDialog.Income) }.padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            GameIcon(GIcon.STORE, 20.dp); Spacer(Modifier.width(3.dp)); OutlinedText("INCOME", size = 12.sp, stroke = 1.dp)
                        }
                        if (pending) BadgeDot(modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp))
                    }
                }
                if (complete && !store.isTour) Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    GoldStarsRow(d.goldOf(store.id), 15.dp)
                    Text("  Gold stars ${com.ngstudio.burgerready.game.GoldStars.count(d.goldOf(store.id))}/3 · play here to earn them", style = Type.Small.copy(fontSize = 11.sp))
                }
            }
            Spacer(Modifier.height(12.dp))
            // ---- the restaurant chain: one chapter at a time (the classic Voyage, or one World Tour region)
            var chapter by remember(store.id) { mutableStateOf(chapterOf(cfg.index)) }
            val last = maxChapter(d)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ChapterArrow("‹", chapter > 0) { chapter-- }
                Text(chapterTitle(chapter), style = Type.Body.copy(fontSize = 13.sp, textAlign = TextAlign.Center), modifier = Modifier.weight(1f), maxLines = 1)
                ChapterArrow("›", chapter < last) { chapter++ }
                Spacer(Modifier.width(6.dp))
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(BV.Teal).border(2.dp, BV.Cocoa, RoundedCornerShape(12.dp))
                    .clickable { App.sfx("ui_tap"); onMap() }.padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(GIcon.COMPASS, 18.dp); Spacer(Modifier.width(3.dp)); OutlinedText("MAP", size = 12.sp, stroke = 1.dp)
                }
            }
            Spacer(Modifier.height(4.dp))
            val indices = chapterIndices(chapter)
            val chain = rememberLazyListState(initialFirstVisibleItemIndex = (indices.indexOf(cfg.index) - 1).coerceAtLeast(0))
            LazyRow(state = chain, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(indices.map { Restaurants.storeAt(it) }) { pos, s ->
                    val i = indices[pos]
                    val open = s.id in d.stores
                    val sel = s.id == store.id
                    val c = Restaurants.config(i)
                    val done = open && d.progressOf(s.id).expanded(c)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).graphicsLayer { if (shakeIdx == i) translationX = shake.value }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (open) { App.sfx("ui_tap"); App.repo.selectStore(s.id) }
                            else if (BuildConfig.DEBUG_AD_UNLOCK && App.ads.rewardedAvailable && i == d.frontier() + 1) { App.sfx("ui_tap"); openDialog(HomeDialog.UnlockNext) }
                            else {
                                App.sfx("ui_error"); App.haptic(1)
                                toast = if (i >= Restaurants.CLASSIC && !App.repo.worldTourOpen()) "Finish Candy Kingdom's grand opening to start the World Tour"
                                    else "Buy the NEW RESTAURANT pad in ${Restaurants.storeAt(d.frontier()).name} to open it"
                                shakeIdx = i
                                scope.launch { shake.snapTo(0f); for (k in 0 until 6) shake.animateTo(if (k % 2 == 0) 10f else -10f, spring(stiffness = 4000f)); shake.animateTo(0f) }
                            }
                        }) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(Modifier.size(60.dp).shadow(if (sel) 8.dp else 2.dp, CircleShape).clip(CircleShape).background(if (sel) BV.Mustard else BV.Cocoa).padding(3.dp).clip(CircleShape)
                                .background(if (open) Brush.verticalGradient(listOf(rgb(s.sky), rgb(s.accent))) else Brush.verticalGradient(listOf(Color(0xFFD8D0C8), Color(0xFF9E958C)))),
                                contentAlignment = Alignment.Center) {
                                if (open && s.isTour) RegionBadge(s, 54.dp)
                                else if (open) ArtImage("store_${s.id}", Modifier.fillMaxSize()) { OutlinedText("${i + 1}", size = 20.sp) }
                                else GameIcon(GIcon.LOCK, 26.dp)
                            }
                            if (done) Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-2).dp).size(20.dp).clip(CircleShape).background(BV.Mint).border(2.dp, BV.Cocoa, CircleShape), contentAlignment = Alignment.Center) {
                                Text("✓", style = Type.Number.copy(fontSize = 11.sp))
                            }
                        }
                        Text(s.name, style = Type.Small.copy(fontSize = 10.5.sp, lineHeight = 12.5.sp, textAlign = TextAlign.Center, color = if (sel || open) BV.Cocoa else BV.CocoaSoft), maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            GameButton(if (built == 0 && cfg.isFirst) "START COOKING" else "PLAY", Modifier.width(260.dp).pulse(0.97f, 1.03f, 1100), color = BV.Tomato, dark = BV.TomatoDark, height = 64.dp, textSize = 28.sp) { onPlay() }
            Spacer(Modifier.height(18.dp))
        }
        if (toast.isNotEmpty()) {
            Box(Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp)).background(BV.Cocoa).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(toast, style = Type.Body.copy(color = BV.Cream, textAlign = TextAlign.Center))
            }
        }
    }
}

@Composable
private fun SideButton(icon: GIcon, label: String, badge: Boolean, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            com.ngstudio.burgerready.ui.kit.RoundButton(color = color, size = 50.dp, onClick = onClick, modifier = if (badge) Modifier.pulse(0.95f, 1.05f) else Modifier) { GameIcon(icon, 30.dp) }
            if (badge) BadgeDot(modifier = Modifier.align(Alignment.TopEnd))
        }
        OutlinedText(label, size = 12.sp, stroke = 1.2.dp)
    }
}


@Composable
private fun ChapterArrow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(30.dp).clip(CircleShape).background(if (enabled) BV.Cocoa else BV.CocoaSoft.copy(alpha = 0.35f))
        .clickable(enabled = enabled) { App.sfx("ui_tab"); onClick() }, contentAlignment = Alignment.Center) {
        OutlinedText(label, size = 20.sp, stroke = 1.dp)
    }
}
