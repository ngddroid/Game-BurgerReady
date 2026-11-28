package com.ngstudio.burgerready.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.game.Cosmetics
import com.ngstudio.burgerready.game.Restaurants
import com.ngstudio.burgerready.game.Stores
import com.ngstudio.burgerready.game.Tour
import com.ngstudio.burgerready.game.Upgrade
import com.ngstudio.burgerready.ui.kit.ArtImage
import com.ngstudio.burgerready.ui.kit.BucksIcon
import com.ngstudio.burgerready.ui.kit.ConfettiBurst
import com.ngstudio.burgerready.ui.kit.GIcon
import com.ngstudio.burgerready.ui.kit.GameButton
import com.ngstudio.burgerready.ui.kit.GameIcon
import com.ngstudio.burgerready.ui.kit.GemIcon
import com.ngstudio.burgerready.ui.kit.ItemCard
import com.ngstudio.burgerready.ui.kit.OutlinedText
import com.ngstudio.burgerready.ui.kit.PriceTag
import com.ngstudio.burgerready.ui.kit.ProgressBar3D
import com.ngstudio.burgerready.ui.kit.Ribbon
import com.ngstudio.burgerready.ui.kit.StarIcon
import com.ngstudio.burgerready.ui.kit.VectorBurger
import com.ngstudio.burgerready.ui.kit.bob
import com.ngstudio.burgerready.ui.kit.drawCorgiFace
import com.ngstudio.burgerready.ui.kit.fadeTop
import com.ngstudio.burgerready.ui.kit.formatNum
import com.ngstudio.burgerready.ui.kit.pulse
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Type

@Composable
private fun TabBackground(top: Color, bottom: Color, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        Canvas(Modifier.fillMaxSize()) {
            // soft polka dots
            val step = 64.dp.toPx()
            var y = 0f; var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else step / 2
                while (x < size.width) { drawCircle(Color.White.copy(alpha = 0.12f), 9.dp.toPx(), Offset(x, y)); x += step }
                y += step; row++
            }
        }
        // Scrolling content fades out under the floating top bar instead of sliding beneath the currency pills.
        val top = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(Modifier.fillMaxSize().fadeTop(clear = top + 62.dp, fade = 22.dp)) { content() }
    }
}

// =============================================================================================== SHOP
@Composable
fun ShopTab(openDialog: (HomeDialog) -> Unit) {
    val d = App.repo.state.value
    TabBackground(Color(0xFFFFB4A2), Color(0xFFFFE2B8)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 82.dp, start = 14.dp, end = 14.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Ribbon("SHOP", Modifier.fillMaxWidth()) }
            item {
                ShopBanner("Free Daily Chest", "A gift from the kitchen, every day!", BV.Mint) {
                    val can = App.repo.canClaimFreeChest()
                    GameButton(if (can) "OPEN" else "TOMORROW", Modifier.width(130.dp), enabled = can, color = BV.Mint, dark = BV.MintDark, height = 48.dp, textSize = 18.sp, sound = "ui_chest_open") {
                        App.repo.claimFreeChest()?.let { openDialog(HomeDialog.Reward("CHEST OPENED!", it.first, it.second)) }
                    }
                }
            }
            if (App.ads.rewardedAvailable) item {
                ShopBanner("Bonus Bucks (Ad)", "Debug build: mock rewarded ad", BV.Mustard) {
                    var watching by remember { mutableStateOf(false) }
                    GameButton("WATCH", Modifier.width(130.dp), color = BV.Mustard, dark = BV.MustardDark, height = 48.dp, textSize = 18.sp) { watching = true }
                    if (watching) MockAdOverlay(onReward = { watching = false; val amt = com.ngstudio.burgerready.game.Restaurants.rewardUnit(d.frontier()) * 2; App.repo.doubleReward(amt); openDialog(HomeDialog.Reward("BONUS!", amt, 0)) }, onCancel = { watching = false })
                }
            }
            item { Text("Chests", style = Type.Heading.copy(color = BV.Cocoa)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Triple("Snack Chest", 40, 1f), Triple("Feast Chest", 120, 3.5f), Triple("Royal Chest", 300, 10f)).forEach { (name, gems, mult) ->
                        ItemCard(Modifier.weight(1f), onClick = {}) {
                            GameIcon(GIcon.CHEST, 56.dp, tint = if (gems > 200) BV.Lilac else if (gems > 100) BV.Teal else null)
                            Text(name, style = Type.Body.copy(fontSize = 13.sp, textAlign = TextAlign.Center))
                            Spacer(Modifier.height(4.dp))
                            GameButton("$gems", Modifier.fillMaxWidth(), enabled = d.gems >= gems, height = 40.dp, textSize = 16.sp, color = BV.Teal, dark = BV.TealDark, icon = { GemIcon(18.dp) }, sound = "ui_chest_open") {
                                if (App.repo.state.value.gems >= gems) {
                                    val rw = App.repo.chestReward()
                                    val bucks = (rw.first * mult).toLong()
                                    App.repo.update { it.copy(gems = it.gems - gems + rw.second, bucks = it.bucks + bucks) }
                                    openDialog(HomeDialog.Reward(name.uppercase() + "!", bucks, rw.second))
                                }
                            }
                        }
                    }
                }
            }
            item { Text("Bucks for Gems", style = Type.Heading.copy(color = BV.Cocoa)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(20 to 1L, 60 to 4L, 150 to 12L).forEach { (gems, k) ->
                        val amount = com.ngstudio.burgerready.game.Restaurants.rewardUnit(d.frontier()) * 5 * k
                        ItemCard(Modifier.weight(1f), onClick = {}) {
                            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { BucksIcon(52.dp) }
                            Text(formatNum(amount), style = Type.Heading.copy(fontSize = 17.sp))
                            GameButton("$gems", Modifier.fillMaxWidth(), enabled = d.gems >= gems, height = 40.dp, textSize = 16.sp, color = BV.Teal, dark = BV.TealDark, icon = { GemIcon(18.dp) }, sound = "ui_purchase") {
                                if (App.repo.state.value.gems >= gems) App.repo.update { it.copy(gems = it.gems - gems, bucks = it.bucks + amount) }
                            }
                        }
                    }
                }
            }
            item {
                ShopBanner("Gem Packs & No-Ads", "Coming soon — in-app purchases aren't enabled yet.", BV.Lilac) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { GemIcon(48.dp) }
                }
            }
        }
    }
}

@Composable
private fun ShopBanner(title: String, sub: String, color: Color, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(BV.Cocoa).padding(3.dp).clip(RoundedCornerShape(18.dp))
        .background(Brush.horizontalGradient(listOf(color, Color.White))).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            OutlinedText(title, size = 20.sp, align = TextAlign.Start, stroke = 1.6.dp)
            Text(sub, style = Type.Small.copy(color = BV.Cocoa))
        }
        action()
    }
}

// =============================================================================================== KITCHEN (upgrades)
@Composable
fun KitchenTab() {
    val d = App.repo.state.value
    var confetti by remember { mutableIntStateOf(0) }
    val stats = App.repo.stats()
    TabBackground(Color(0xFFBDEBFF), Color(0xFFFFF3DC)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 82.dp, start = 14.dp, end = 14.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Ribbon("KITCHEN UPGRADES", Modifier.fillMaxWidth(), color = BV.Teal, dark = BV.TealDark) }
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.85f)).border(2.dp, BV.Cocoa, RoundedCornerShape(16.dp)).padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatCell("Speed", String.format("%.1f", stats.moveSpeed))
                    StatCell("Carry", "${stats.capacity}")
                    StatCell("Cook", "x" + String.format("%.2f", stats.cookSpeedMul))
                    StatCell("Tips", "+${(stats.tipBonus * 100).toInt()}%")
                }
            }
            val hot = Upgrade.entries.filter { d.upgrade(it) < it.maxLevel }.minByOrNull { it.cost(d.upgrade(it)) }
            items(Upgrade.entries.toList()) { u ->
                val lvl = d.upgrade(u)
                val maxed = lvl >= u.maxLevel
                val cost = u.cost(lvl)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BV.Cocoa).padding(3.dp).clip(RoundedCornerShape(16.dp)).background(BV.Cream).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(BV.Mustard.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            ArtImage("upgrade_${u.id}", Modifier.fillMaxSize()) { Text(u.icon, fontSize = 30.sp) }
                        }
                        // on the icon, so the name keeps its full width
                        if (u == hot) Box(Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-5).dp).graphicsLayer { rotationZ = -10f }
                            .clip(RoundedCornerShape(6.dp)).background(BV.Cocoa).padding(1.5.dp).clip(RoundedCornerShape(5.dp)).background(BV.Tomato).padding(horizontal = 5.dp)) {
                            Text("HOT", style = Type.Number.copy(fontSize = 11.sp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        // one line: "Customer Patience" used to wrap and make its card taller than the rest
                        Text(u.label, style = Type.Heading.copy(fontSize = if (u.label.length > 15) 15.sp else 17.sp), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(u.desc, style = Type.Small)
                        ProgressBar3D(lvl.toFloat() / u.maxLevel, Modifier.fillMaxWidth().padding(top = 4.dp), height = 12.dp, color = BV.Teal)
                        Text("Lv $lvl / ${u.maxLevel}", style = Type.Small)
                    }
                    Spacer(Modifier.width(8.dp))
                    GameButton(if (maxed) "MAX" else formatNum(cost), Modifier.width(104.dp), enabled = !maxed && d.bucks >= cost, height = 46.dp, textSize = 16.sp,
                        icon = if (maxed) null else ({ BucksIcon(18.dp) }), sound = "upgrade") {
                        if (App.repo.buyUpgrade(u)) { confetti++; App.haptic(1) }
                    }
                }
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Type.Small)
        Text(value, style = Type.Heading.copy(fontSize = 18.sp))
    }
}

// =============================================================================================== WORLD (restaurants)
@Composable
fun StoresTab(openDialog: (HomeDialog) -> Unit) {
    val d = App.repo.state.value
    TabBackground(Color(0xFFC9F0C0), Color(0xFFE6F6FF)) {
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 82.dp, start = 12.dp, end = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(span = { GridItemSpan(2) }) { Ribbon("MY RESTAURANTS", Modifier.fillMaxWidth(), color = BV.Mint, dark = BV.MintDark) }
            item(span = { GridItemSpan(2) }) {
                val pending = App.repo.totalPendingIncome()
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.9f)).border(2.dp, BV.Cocoa, RoundedCornerShape(16.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Idle income", style = Type.Heading.copy(fontSize = 17.sp))
                        Text("${formatNum(d.stores.keys.sumOf { App.repo.incomeRate(it) }.toLong())} Bucks/hour · up to ${Stores.INCOME_CAP_HOURS.toInt()}\u00A0h", style = Type.Small)
                    }
                    GameButton("COLLECT ${formatNum(pending)}", Modifier.width(160.dp), enabled = pending > 0, height = 46.dp, textSize = 15.sp, sound = "coin_cascade") {
                        val amt = App.repo.collectIncome(); if (amt > 0) openDialog(HomeDialog.Reward("INCOME!", amt, 0))
                    }
                }
            }
            items(Stores.ALL) { s ->
                val owned = s.id in d.stores
                val selected = d.selectedStore == s.id
                val cfg = Restaurants.config(s.id)
                val prog = d.progressOf(s.id)
                ItemCard(selected = selected, locked = !owned, onClick = { openDialog(HomeDialog.StoreDetail(s.id)) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(12.dp)).background(
                        if (owned) Brush.verticalGradient(listOf(Color(s.sky.toLong() or 0xFF000000), Color(s.accent.toLong() or 0xFF000000)))
                        else Brush.verticalGradient(listOf(Color(0xFFE4DDD4), Color(0xFFC9BFB4)))),
                        contentAlignment = Alignment.Center) {
                        ArtImage("store_${s.id}", Modifier.fillMaxSize(), colorFilter = if (owned) null else com.ngstudio.burgerready.ui.kit.LockedFilter) { GameIcon(GIcon.STORE, 64.dp, tint = Color(s.wall.toLong() or 0xFF000000)) }
                        if (!owned) Box(Modifier.fillMaxSize().background(Color(0x26FFFFFF)), contentAlignment = Alignment.Center) { GameIcon(GIcon.LOCK, 40.dp) }
                        if (selected) Box(Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(BV.Mint).padding(horizontal = 6.dp)) { Text("PLAYING", style = Type.Number.copy(fontSize = 11.sp)) }
                    }
                    Text(s.name, style = Type.Heading.copy(fontSize = 15.sp), maxLines = 1)
                    Text(s.perk, style = Type.Small.copy(fontSize = 11.sp), maxLines = 1)
                    if (owned) {
                        ProgressBar3D(prog.fraction(cfg), Modifier.fillMaxWidth().padding(vertical = 2.dp), height = 10.dp, color = if (prog.expanded(cfg)) BV.Mint else BV.Mustard)
                        Row { for (k in 1..Stores.MAX_RENOVATION) StarIcon(14.dp, filled = k <= (d.stores[s.id] ?: 0)) }
                    } else Text("Restaurant ${cfg.index + 1}", style = Type.Small.copy(color = BV.CocoaSoft))
                }
            }
            // ---- World Tour: one row per region with open restaurants (500 cards would not scroll well)
            if (App.repo.worldTourOpen()) {
                item(span = { GridItemSpan(2) }) { Ribbon("WORLD TOUR", Modifier.fillMaxWidth().padding(top = 6.dp), color = BV.Teal, dark = BV.TealDark, size = 20.sp) }
                items(Tour.REGIONS.size, span = { GridItemSpan(2) }) { r -> RegionIncomeRow(d, r + 1) { id -> openDialog(HomeDialog.StoreDetail(id)) } }
            }
        }
    }
}

@Composable
fun StoreDetailCard(id: String, onClose: () -> Unit, onPlay: () -> Unit) {
    val s = Stores.byId(id)
    val d = App.repo.state.value
    val owned = id in d.stores
    val stars = d.stores[id] ?: 0
    val cfg = Restaurants.config(id)
    val prog = d.progressOf(id)
    var confetti by remember { mutableIntStateOf(0) }
    Box {
        DialogCard(s.name.uppercase(), onClose, color = BV.Mint, dark = BV.MintDark) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(Color(s.sky.toLong() or 0xFF000000), Color(s.accent.toLong() or 0xFF000000)))),
                    contentAlignment = Alignment.Center) {
                    if (s.isTour) ArtImage(s.art, Modifier.fillMaxSize().bob(4f)) { RegionBadge(s, 130.dp) }
                    else ArtImage("store_${s.id}", Modifier.fillMaxSize().bob(4f)) { GameIcon(GIcon.STORE, 90.dp, tint = Color(s.wall.toLong() or 0xFF000000)) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Perk: ${s.perk}", style = Type.Body)
                Text("Menu: " + cfg.menu.joinToString { it.label }, style = Type.Small.copy(textAlign = TextAlign.Center))
                Spacer(Modifier.height(10.dp))
                if (!owned) {
                    Text("Restaurant ${cfg.index + 1}. Buy the NEW RESTAURANT pad in ${Restaurants.storeAt(d.frontier()).name} to open it.",
                        style = Type.Body.copy(textAlign = TextAlign.Center))
                } else {
                    Box(Modifier.fillMaxWidth()) {
                        ProgressBar3D(prog.fraction(cfg), Modifier.fillMaxWidth(), height = 22.dp, color = if (prog.expanded(cfg)) BV.Mint else BV.Mustard)
                        Text("Built ${prog.builtCount(cfg)} / ${cfg.buildPads}", style = Type.Number.copy(fontSize = 13.sp, color = BV.Cocoa), modifier = Modifier.align(Alignment.Center))
                    }
                    Text("Idle income: ${formatNum(App.repo.incomeRate(id).toLong())} / hour", style = Type.Small)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Renovation  ", style = Type.Small)
                        for (k in 1..Stores.MAX_RENOVATION) StarIcon(22.dp, filled = k <= stars)
                    }
                    if (prog.expanded(cfg) && !s.isTour) {
                        // the Golden Tour: three one-visit challenges per finished restaurant (game/GoldStars.kt)
                        val mask = d.goldOf(id)
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF4CC)).border(2.dp, BV.Gold, RoundedCornerShape(12.dp)).padding(8.dp)) {
                            Text("GOLD STARS  ${com.ngstudio.burgerready.game.GoldStars.count(mask)} / 3", style = Type.Heading.copy(fontSize = 15.sp))
                            com.ngstudio.burgerready.game.GoldStars.goals(cfg.index).forEach { g ->
                                val got = ((mask shr g.star) and 1) == 1
                                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StarIcon(16.dp, filled = got, color = if (got) BV.Gold else Color(0xFFD9CFC4))
                                    Text("  " + goldGoalText(g), style = Type.Body.copy(fontSize = 13.sp, color = if (got) BV.MintDark else BV.Cocoa))
                                }
                            }
                            Text("Each star: +${com.ngstudio.burgerready.game.GoldStars.GEMS} gems and +5% idle income here.", style = Type.Small.copy(fontSize = 11.sp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GameButton(if (d.selectedStore == id) "PLAY" else "PLAY HERE", Modifier.fillMaxWidth(), color = BV.Tomato, dark = BV.TomatoDark) { App.repo.selectStore(id); onPlay() }
                    Spacer(Modifier.height(8.dp))
                    if (stars < Stores.MAX_RENOVATION) {
                        val cost = Stores.renovationCost(s, stars)
                        GameButton("RENOVATE · ${formatNum(cost)}", Modifier.fillMaxWidth(), enabled = d.bucks >= cost, color = BV.Mustard, dark = BV.MustardDark, height = 50.dp, textSize = 18.sp, sound = "upgrade") {
                            if (App.repo.renovate(id)) confetti++
                        }
                        Text("+10% idle income per star", style = Type.Small)
                    }
                }
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxWidth().height(420.dp))
    }
}

// =============================================================================================== STYLE
@Composable
fun StyleTab() {
    var sub by remember { mutableIntStateOf(0) }
    val d = App.repo.state.value
    var confetti by remember { mutableIntStateOf(0) }
    TabBackground(Color(0xFFE6D6FF), Color(0xFFFFE6F2)) {
        Column(Modifier.fillMaxSize().padding(top = 82.dp)) {
            Ribbon("STYLE", Modifier.fillMaxWidth().padding(horizontal = 14.dp), color = BV.Lilac, dark = Color(0xFF8E73E0))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Chefs", "Pets", "Themes").forEachIndexed { i, label ->
                    GameButton(label, Modifier.weight(1f), color = if (sub == i) BV.Tomato else BV.Cream, dark = if (sub == i) BV.TomatoDark else BV.CreamDark, height = 42.dp, textSize = 16.sp, sound = "ui_tab") { sub = i }
                }
            }
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (sub) {
                    0 -> items(Cosmetics.SKINS) { s ->
                        val owned = s.id in d.skins
                        StyleItem("skin_${s.id}", s.name, owned, d.selectedSkin == s.id, s.costBucks, s.costGems, { Canvas(Modifier.fillMaxSize(0.8f)) { drawCorgiFace() } }) {
                            if (owned) App.repo.selectSkin(s.id) else if (App.repo.buySkin(s.id)) { confetti++; App.sfx("ui_purchase") } else App.sfx("ui_error")
                        }
                    }
                    1 -> items(Cosmetics.PETS) { p ->
                        val owned = p.id in d.pets
                        StyleItem("pet_${p.id}", p.name, owned, d.selectedPet == p.id, p.costBucks, p.costGems, { GameIcon(GIcon.PAW, 48.dp) }, p.ability) {
                            if (owned) App.repo.selectPet(p.id) else if (App.repo.buyPet(p.id)) { confetti++; App.sfx("ui_purchase") } else App.sfx("ui_error")
                        }
                    }
                    else -> items(Cosmetics.THEMES) { t ->
                        val owned = t.id in d.themes
                        StyleItem("theme_${t.id}", t.name, owned, d.selectedTheme == t.id, t.costBucks, t.costGems, {
                            Canvas(Modifier.fillMaxSize(0.8f)) {
                                val w = size.width
                                drawRect(Color(t.floorTint.toLong() or 0xFF000000), Offset(0f, size.height * 0.5f), Size(w, size.height * 0.5f))
                                drawRect(Color(t.wallTint.toLong() or 0xFF000000), Offset.Zero, Size(w, size.height * 0.5f))
                                drawRect(Color(t.trim.toLong() or 0xFF000000), Offset(0f, size.height * 0.42f), Size(w, size.height * 0.08f))
                                drawCircle(Color(t.seat.toLong() or 0xFF000000), w * 0.16f, Offset(w * 0.5f, size.height * 0.72f))
                            }
                        }) {
                            if (owned) App.repo.selectTheme(t.id) else if (App.repo.buyTheme(t.id)) { confetti++; App.sfx("ui_purchase") } else App.sfx("ui_error")
                        }
                    }
                }
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}

@Composable
private fun StyleItem(art: String, name: String, owned: Boolean, selected: Boolean, bucks: Long, gems: Int, fallback: @Composable () -> Unit, sub: String? = null, onClick: () -> Unit) {
    val d = App.repo.state.value
    ItemCard(selected = selected, locked = !owned, onClick = onClick) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Color(0xFFFFF7E8), Color(0xFFFFE0B8)))), contentAlignment = Alignment.Center) {
            ArtImage(art, Modifier.fillMaxSize()) { fallback() }
            if (selected) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape).background(BV.Mint), contentAlignment = Alignment.Center) { Text("✓", style = Type.Number.copy(fontSize = 12.sp)) }
        }
        Text(name, style = Type.Body.copy(fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 2, modifier = Modifier.height(32.dp))
        if (sub != null && sub != "—") Text(sub, style = Type.Small.copy(fontSize = 9.sp, textAlign = TextAlign.Center), maxLines = 2)
        if (owned) Text(if (selected) "EQUIPPED" else "EQUIP", style = Type.Small.copy(color = BV.MintDark))
        else PriceTag(bucks, gems, d.bucks >= bucks && d.gems >= gems)
    }
}
