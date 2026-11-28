package com.ngstudio.burgerready.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.data.SaveData
import com.ngstudio.burgerready.game.RegionDef
import com.ngstudio.burgerready.game.Restaurants
import com.ngstudio.burgerready.game.StoreDef
import com.ngstudio.burgerready.game.Stores
import com.ngstudio.burgerready.game.Tour
import com.ngstudio.burgerready.ui.kit.ArtImage
import com.ngstudio.burgerready.ui.kit.GIcon
import com.ngstudio.burgerready.ui.kit.GameIcon
import com.ngstudio.burgerready.ui.kit.OutlinedText
import com.ngstudio.burgerready.ui.kit.Panel
import com.ngstudio.burgerready.ui.kit.ProgressBar3D
import com.ngstudio.burgerready.ui.kit.formatNum
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Type

/** World Tour UI (docs/WORLD_TOUR_DESIGN.md §7): chapters, region badges, the World Map. */
private fun rgb(c: Int) = Color(c.toLong() or 0xFF000000)

/** Chapter of a global restaurant index: 0 = the classic Voyage, 1..25 = World Tour regions. */
fun chapterOf(index: Int): Int = if (index < Restaurants.CLASSIC) 0 else Tour.regionOf(index) + 1

/** Global indices of a chapter's restaurants. */
fun chapterIndices(chapter: Int): List<Int> =
    if (chapter == 0) (0 until Restaurants.CLASSIC).toList() else (0 until Tour.PER_REGION).map { Tour.globalIndex(chapter - 1, it) }

fun chapterTitle(chapter: Int): String = if (chapter == 0) "The Classic Voyage" else "Region $chapter · ${Tour.REGIONS[chapter - 1].name}"

/** The last chapter the player may browse: everything opened, plus a peek at the next locked one. */
fun maxChapter(d: SaveData): Int = (chapterOf(d.frontier()) + 1).coerceAtMost(Tour.REGIONS.size)

/** Finished restaurants in a chapter (EXPAND bought; the very last one when fully built). */
fun chapterDone(d: SaveData, chapter: Int): Int = chapterIndices(chapter).count { d.isComplete(Restaurants.storeAt(it).id) }

/**
 * Drawn art for a World Tour restaurant until its region diorama exists (assets/ui/region_<id>): the region's sky,
 * ground and a little storefront in its palette, with the stop number.
 */
@Composable
fun RegionBadge(s: StoreDef, size: Dp, showStep: Boolean = true) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width; val h = this.size.height
            val r = minOf(w, h) / 2f
            val c = Offset(w / 2f, h / 2f)
            drawCircle(Brush.verticalGradient(listOf(rgb(s.sky), rgb(s.accent).copy(alpha = 0.85f))), r, c)
            // ground
            val ground = if (s.groundColor >= 0) rgb(s.groundColor) else BV.Mint
            drawArc(ground, 0f, 180f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
            // storefront: wall, awning in trim, door
            val bw = r * 0.95f; val bh = r * 0.62f
            val left = c.x - bw / 2; val top = c.y - bh * 0.55f
            drawRect(rgb(s.wall), Offset(left, top), Size(bw, bh))
            drawRect(BV.Cocoa, Offset(left, top), Size(bw, bh), style = Stroke(r * 0.05f))
            val aw = Path().apply { moveTo(left - r * 0.06f, top); lineTo(left + bw + r * 0.06f, top); lineTo(left + bw, top - r * 0.22f); lineTo(left, top - r * 0.22f); close() }
            drawPath(aw, rgb(s.trim)); drawPath(aw, BV.Cocoa, style = Stroke(r * 0.04f))
            for (k in 0 until 4) drawRect(Color.White.copy(alpha = 0.55f), Offset(left + bw * (0.06f + k * 0.25f), top - r * 0.2f), Size(bw * 0.1f, r * 0.18f))
            drawRect(BV.Cocoa, Offset(c.x - bw * 0.12f, top + bh * 0.35f), Size(bw * 0.24f, bh * 0.65f))
            drawRect(Color(0xFFBDEBFF), Offset(left + bw * 0.08f, top + bh * 0.2f), Size(bw * 0.22f, bh * 0.3f))
            drawRect(Color(0xFFBDEBFF), Offset(left + bw * 0.7f, top + bh * 0.2f), Size(bw * 0.22f, bh * 0.3f))
            if (s.night > 0.4f) drawCircle(Color(0xFFFFF3B0), r * 0.1f, Offset(c.x + r * 0.55f, c.y - r * 0.6f))
            else drawCircle(Color(0xFFFFE27A), r * 0.12f, Offset(c.x + r * 0.55f, c.y - r * 0.58f))
            drawCircle(BV.Cocoa, r, c, style = Stroke(r * 0.06f))
        }
        if (showStep && s.step >= 0) Box(Modifier.align(Alignment.BottomEnd).size(size * 0.36f).clip(CircleShape).background(BV.Cocoa), contentAlignment = Alignment.Center) {
            OutlinedText("${s.step + 1}", size = (size.value * 0.17f).sp, stroke = 1.dp)
        }
    }
}

/** Region art for cards: the rendered diorama if present, else the badge of its flagship. */
@Composable
fun RegionArt(reg: RegionDef, size: Dp) {
    val flagship = Tour.store(Tour.globalIndex(Tour.regionIndex(reg.id), Tour.PER_REGION - 1))
    ArtImage("region_${reg.id}", Modifier.size(size)) { RegionBadge(flagship, size, showStep = false) }
}

/** The World Map: the classic Voyage and the 25 regions of the World Tour, with their restaurants. */
@Composable
fun WorldMapScreen(onBack: () -> Unit, onPlay: () -> Unit) {
    val d = App.repo.state.value
    var open by remember { mutableIntStateOf(chapterOf(Restaurants.indexOf(d.selectedStore))) }
    ScreenFrame("WORLD MAP", onBack, color = BV.Teal, dark = BV.TealDark) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val opened = d.stores.size
                Panel(Modifier.fillMaxWidth()) {
                    Text("Bun Voyage, all over the world", style = Type.Heading)
                    Text("$opened of ${Restaurants.TOTAL} restaurants open · ${App.repo.regionsComplete()} of ${Tour.REGIONS.size} regions stamped", style = Type.Small)
                    ProgressBar3D(opened.toFloat() / Restaurants.TOTAL, Modifier.fillMaxWidth().padding(top = 6.dp), color = BV.Teal, height = 14.dp)
                    if (!App.repo.worldTourOpen()) Text("The World Tour opens after Candy Kingdom's grand opening.", style = Type.Small.copy(color = BV.TomatoDark), modifier = Modifier.padding(top = 6.dp))
                }
            }
            items(Tour.REGIONS.size + 1) { ch -> ChapterCard(d, ch, ch == open, onToggle = { open = if (open == ch) -1 else ch }, onPlay = onPlay) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ChapterCard(d: SaveData, chapter: Int, expanded: Boolean, onToggle: () -> Unit, onPlay: () -> Unit) {
    val idx = chapterIndices(chapter)
    val unlocked = Restaurants.storeAt(idx.first()).id in d.stores
    val done = chapterDone(d, chapter)
    val reg = if (chapter > 0) Tour.REGIONS[chapter - 1] else null
    Column(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(BV.Cocoa).padding(3.dp)
        .clip(RoundedCornerShape(16.dp)).background(if (unlocked) Color(0xFFFFFBF2) else Color(0xFFEDE4DA))
        .clickable(remember { MutableInteractionSource() }, null) { App.sfx("ui_tap"); onToggle() }.padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (reg != null) RegionArt(reg, 64.dp) else ArtImage("store_candy", Modifier.size(64.dp)) { GameIcon(GIcon.COMPASS, 44.dp) }
                if (!unlocked) Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0x99EDE4DA)), contentAlignment = Alignment.Center) { GameIcon(GIcon.LOCK, 26.dp) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (chapter == 0) "CHAPTER 1" else "REGION $chapter OF ${Tour.REGIONS.size}", style = Type.Small.copy(fontSize = 10.sp, letterSpacing = 1.5.sp, color = BV.TealDark))
                Text(if (reg != null) reg.name else "The Classic Voyage", style = Type.Heading.copy(fontSize = 18.sp), maxLines = 1)
                Text(reg?.blurb ?: "Hazel's twelve pages, Bun Wagon to Candy Kingdom", style = Type.Small, maxLines = 2)
                if (reg != null) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Portrait(reg.host, 22.dp); Text("  Host: ${reg.host.displayName} · ${Tour.perkText(reg.perk, Tour.perkAmount(reg.perk, 0))}+", style = Type.Small.copy(fontSize = 10.5.sp), maxLines = 1)
                }
            }
            Box(Modifier.clip(RoundedCornerShape(8.dp)).border(2.dp, if (done == idx.size) BV.Tomato else BV.CocoaSoft, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(if (done == idx.size) "STAMPED" else "$done / ${idx.size}", style = Type.Small.copy(fontSize = 10.sp, color = if (done == idx.size) BV.Tomato else BV.CocoaSoft))
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            idx.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { g -> RestaurantChip(d, g, Modifier.weight(1f), onPlay) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RestaurantChip(d: SaveData, g: Int, modifier: Modifier, onPlay: () -> Unit) {
    val s = Restaurants.storeAt(g)
    val owned = s.id in d.stores
    val sel = d.selectedStore == s.id
    val complete = owned && d.isComplete(s.id)
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) BV.Mustard.copy(alpha = 0.45f) else Color.White).border(2.dp, if (sel) BV.Mustard else BV.CreamDark, RoundedCornerShape(12.dp))
        .clickable(enabled = owned) { App.sfx("ui_tap"); App.repo.selectStore(s.id); onPlay() }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            if (s.isTour) RegionBadge(s, 46.dp) else ArtImage("store_${s.id}", Modifier.size(46.dp)) { GameIcon(GIcon.STORE, 34.dp) }
            if (!owned) Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xAAEDE4DA)), contentAlignment = Alignment.Center) { GameIcon(GIcon.LOCK, 20.dp) }
            if (complete) Box(Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(BV.Mint).border(1.5.dp, BV.Cocoa, CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", style = Type.Number.copy(fontSize = 9.sp))
            }
        }
        Text(if (owned) s.name else "???", style = Type.Small.copy(fontSize = 9.5.sp, lineHeight = 11.sp, textAlign = TextAlign.Center, color = if (owned) BV.Cocoa else BV.CocoaSoft), maxLines = 2)
    }
}

/** World tab: one row per opened World Tour region (restaurants, idle income), tap for its first open restaurant. */
@Composable
fun RegionIncomeRow(d: SaveData, chapter: Int, onOpen: (String) -> Unit) {
    val reg = Tour.REGIONS[chapter - 1]
    val ids = chapterIndices(chapter).map { Restaurants.storeAt(it).id }.filter { it in d.stores }
    if (ids.isEmpty()) return
    val perHour = ids.sumOf { App.repo.incomeRate(it) }.toLong()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.92f)).border(2.dp, BV.Cocoa, RoundedCornerShape(14.dp))
        .clickable { App.sfx("ui_tap"); onOpen(ids.last()) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RegionArt(reg, 44.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(reg.name, style = Type.Heading.copy(fontSize = 15.sp), maxLines = 1)
            Text("${ids.size} / ${Tour.PER_REGION} open · ${chapterDone(d, chapter)} finished", style = Type.Small.copy(fontSize = 11.sp))
        }
        Text("${formatNum(perHour)}/h", style = Type.Body.copy(fontSize = 13.sp, color = BV.TealDark))
    }
}

/** Store-detail art for any restaurant. */
@Composable
fun StoreArt(s: StoreDef, size: Dp) {
    if (s.isTour) ArtImage(s.art, Modifier.size(size)) { RegionBadge(s, size) } else ArtImage("store_${s.id}", Modifier.size(size)) { GameIcon(GIcon.STORE, size * 0.6f) }
}

@Suppress("unused") private val keepStores = Stores
