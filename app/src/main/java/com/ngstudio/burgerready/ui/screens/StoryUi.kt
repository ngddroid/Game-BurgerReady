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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.data.SaveData
import com.ngstudio.burgerready.game.Beat
import com.ngstudio.burgerready.game.GoldStars
import com.ngstudio.burgerready.game.Restaurants
import com.ngstudio.burgerready.game.Speaker
import com.ngstudio.burgerready.game.Stores
import com.ngstudio.burgerready.game.Story
import com.ngstudio.burgerready.game.Trigger
import com.ngstudio.burgerready.ui.kit.ArtImage
import com.ngstudio.burgerready.ui.kit.GIcon
import com.ngstudio.burgerready.ui.kit.GameIcon
import com.ngstudio.burgerready.ui.kit.LockedFilter
import com.ngstudio.burgerready.ui.kit.OutlinedText
import com.ngstudio.burgerready.ui.kit.Panel
import com.ngstudio.burgerready.ui.kit.ProgressBar3D
import com.ngstudio.burgerready.ui.kit.Ribbon
import com.ngstudio.burgerready.ui.kit.StarIcon
import com.ngstudio.burgerready.ui.kit.drawCorgiFace
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Type
import kotlinx.coroutines.delay

private fun rgb(c: Int) = Color(c.toLong() or 0xFF000000)

/** A cast member's face: their rendered bust (assets/ui/cast_*), else the guest head of their species. */
@Composable
fun Portrait(sp: Speaker, size: Dp, dim: Boolean = false) {
    val c = rgb(sp.color)
    Box(Modifier.size(size).shadow(4.dp, CircleShape).clip(CircleShape).background(BV.Cocoa).padding(size * 0.045f).clip(CircleShape)
        .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.35f).compositeOverWhite(), c))), contentAlignment = Alignment.Center) {
        val filter = if (dim) LockedFilter else null
        ArtImage(sp.portrait, Modifier.fillMaxSize().padding(top = size * 0.06f), colorFilter = filter) {
            ArtImage(sp.fallback, Modifier.fillMaxSize().padding(size * 0.1f), colorFilter = filter) {
                Canvas(Modifier.fillMaxSize().padding(size * 0.12f)) { drawCorgiFace() }
            }
        }
        if (dim) Box(Modifier.fillMaxSize().background(Color(0x66FFF3DC)), contentAlignment = Alignment.Center) { OutlinedText("?", size = (size.value * 0.4f).sp) }
    }
}

private fun Color.compositeOverWhite(): Color = Color(red * alpha + (1 - alpha), green * alpha + (1 - alpha), blue * alpha + (1 - alpha), 1f)

/**
 * One story beat as speech bubbles: portrait, name tag, typewriter text. Tap to finish the line / go on; every line
 * also advances by itself, so a beat never holds up play. Patty speaks from the right, everyone else from the left.
 * [compact] is the in-game size (it sits under the HUD's top row, away from the joystick).
 * Only a deliberate tap counts: a press that drags or is held (someone steering who brushed the card) does nothing.
 */
@Composable
fun DialogueCard(beat: Beat, modifier: Modifier = Modifier, autoAdvance: Boolean = true, compact: Boolean = false, onDone: () -> Unit) {
    var idx by remember(beat.id) { mutableIntStateOf(0) }
    var chars by remember(beat.id) { mutableIntStateOf(0) }
    val appear = remember(beat.id) { Animatable(0f) }
    LaunchedEffect(beat.id) { App.sfx("ui_popup", 0.7f); appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 420f)) }
    val line = beat.lines[idx.coerceIn(0, beat.lines.size - 1)]
    fun advance() {
        if (idx >= beat.lines.size - 1) onDone() else { idx++; chars = 0 }
    }
    // typewriter, then a reading pause, then the next bubble by itself (in-game cards only: cut-scenes wait for a
    // tap, so a scene nobody is watching, e.g. on Home, is never marked as seen)
    LaunchedEffect(beat.id, idx) {
        val n = line.text.length
        while (chars < n) { delay(22); chars++ }
        if (!autoAdvance) return@LaunchedEffect
        delay((1500L + n * 42L).coerceAtLeast(2300L))
        advance()
    }
    val right = line.who == Speaker.PATTY
    val portrait = if (compact) 58.dp else 74.dp
    val tap by androidx.compose.runtime.rememberUpdatedState {
        if (chars < line.text.length) chars = line.text.length else { App.sfx("ui_tap", 0.5f); App.haptic(0); advance() }
    }
    Box(modifier.fillMaxWidth().graphicsLayer { translationY = (1f - appear.value) * (if (compact) -90f else 160f); alpha = appear.value.coerceIn(0f, 1f) }
        .pointerInput(beat.id) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val up = withTimeoutOrNull(450L) { waitForUpOrCancellation() }
                if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) { up.consume(); tap() }
            }
        }.padding(horizontal = if (compact) 2.dp else 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (!right) { Portrait(line.who, portrait); Spacer(Modifier.width(6.dp)) }
            Box(Modifier.weight(1f).padding(top = 14.dp)) {
                Column(Modifier.fillMaxWidth().heightIn(min = if (compact) 66.dp else 82.dp).shadow(6.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                    .background(Color.White).border(3.dp, BV.Cocoa, RoundedCornerShape(18.dp))
                    .padding(start = 14.dp, end = 14.dp, top = if (compact) 14.dp else 16.dp, bottom = if (compact) 6.dp else 8.dp)) {
                    val shown = chars.coerceAtMost(line.text.length)
                    // unrevealed letters are laid out but transparent, so the bubble never reflows while typing
                    Text(buildAnnotatedString {
                        append(line.text.substring(0, shown))
                        withStyle(SpanStyle(color = Color.Transparent)) { append(line.text.substring(shown)) }
                    }, style = Type.Body.copy(fontSize = if (compact) 15.sp else 16.sp, lineHeight = if (compact) 19.sp else 21.sp))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (i in beat.lines.indices) Box(Modifier.padding(end = 3.dp).size(if (i == idx) 8.dp else 6.dp).clip(CircleShape).background(if (i <= idx) BV.Mustard else BV.CreamDark))
                        Spacer(Modifier.weight(1f))
                        Text(if (idx >= beat.lines.size - 1) "TAP TO CLOSE" else "TAP ▸", style = Type.Small.copy(fontSize = 10.sp, color = BV.CocoaSoft))
                    }
                }
                // name tag on the bubble's top edge
                Box(Modifier.align(if (right) Alignment.TopEnd else Alignment.TopStart).offset(x = if (right) (-12).dp else 12.dp, y = (-14).dp)
                    .clip(RoundedCornerShape(10.dp)).background(BV.Cocoa).padding(2.dp).clip(RoundedCornerShape(8.dp)).background(rgb(line.who.color)).padding(horizontal = 10.dp, vertical = 2.dp)) {
                    OutlinedText(line.who.displayName, size = 15.sp, stroke = 1.2.dp, maxLines = 1)
                }
            }
            if (right) { Spacer(Modifier.width(6.dp)); Portrait(line.who, portrait) }
        }
    }
}

/**
 * A beat played as a short cut-scene: dimmed screen, a title ribbon, the speech card. Used for completions, the finale
 * and the Home prologue. The game is paused underneath, so it waits for taps instead of advancing by itself.
 */
@Composable
fun StoryScene(beat: Beat, header: String, onDone: () -> Unit) {
    val enter = remember(beat.id) { Animatable(0f) }
    LaunchedEffect(beat.id) { enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 300f)) }
    Box(Modifier.fillMaxSize().background(Color(0xD91E1420)).clickable(remember { MutableInteractionSource() }, null) { }) {
        // the title sits on its own plate: over a busy screen (the Home hub card) bare outlined text was hard to read
        Column(Modifier.align(Alignment.Center).padding(bottom = 170.dp, start = 24.dp, end = 24.dp)
            .graphicsLayer { scaleX = 0.85f + 0.15f * enter.value; scaleY = 0.85f + 0.15f * enter.value; alpha = enter.value.coerceIn(0f, 1f) }
            .shadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(BV.Cocoa)
            .border(3.dp, BV.Gold.copy(alpha = 0.85f), RoundedCornerShape(22.dp)).padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(header, style = Type.Small.copy(color = BV.Gold, letterSpacing = 2.sp, fontSize = 13.sp))
            OutlinedText(beat.title, size = 30.sp, color = BV.Cream, stroke = 2.dp, align = TextAlign.Center)
        }
        DialogueCard(beat, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 26.dp), autoAdvance = false, onDone = onDone)
    }
}

/** A gold-star challenge as text, with money targets formatted. */
fun goldGoalText(g: com.ngstudio.burgerready.game.GoldGoal): String =
    if (g.kind == com.ngstudio.burgerready.game.GoalKind.REVENUE) "Earn ${com.ngstudio.burgerready.ui.kit.formatNum(g.target)} in one visit" else g.text()

/** Three gold stars (earned ones filled). */
@Composable
fun GoldStarsRow(mask: Int, size: Dp = 18.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) { for (i in 0 until GoldStars.PER_RESTAURANT) StarIcon(size, filled = ((mask shr i) and 1) == 1, color = if (((mask shr i) and 1) == 1) BV.Gold else Color(0xFFD9CFC4)) }
}

/** Beats the Voyage Book may show: everything seen, plus every page of a restaurant that is already finished. */
private fun readable(b: Beat, d: SaveData): Boolean {
    if (b.id in d.story) return true
    if (b.store < 0 || b.trigger == Trigger.POSTGAME) return false
    val id = Restaurants.storeAt(b.store).id
    return d.isComplete(id) && b.trigger != Trigger.FINALE
}

/** The Voyage Book: Hazel's twelve pages, their stamps and gold stars, the cast, and every story beat to re-read. */
@Composable
fun VoyageBookScreen(onBack: () -> Unit) {
    val d = App.repo.state.value
    var open by remember { mutableStateOf(Restaurants.indexOf(d.selectedStore)) }
    LaunchedEffect(Unit) { App.repo.markVoyageSeen() }
    val metSpeakers = remember(d.story) {
        Story.BEATS.filter { readable(it, d) }.flatMap { b -> b.lines.map { it.who } }.toSet() + Speaker.PATTY
    }
    ScreenFrame("VOYAGE BOOK", onBack, color = BV.Mustard, dark = BV.MustardDark) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val stamped = Stores.ALL.count { d.isComplete(it.id) }
                Panel(Modifier.fillMaxWidth()) {
                    Text("Hazel's twelve pages", style = Type.Heading)
                    Text("Fill every page, and send a postcard from each one.", style = Type.Small)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pages stamped", style = Type.Body, modifier = Modifier.weight(1f)); Text("$stamped / ${Restaurants.CLASSIC}", style = Type.Body)
                    }
                    ProgressBar3D(stamped.toFloat() / Restaurants.CLASSIC, Modifier.fillMaxWidth().padding(vertical = 4.dp), color = BV.Tomato, height = 14.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StarIcon(18.dp); Text(" Gold stars", style = Type.Body, modifier = Modifier.weight(1f)); Text("${d.goldTotal()} / ${GoldStars.TOTAL}", style = Type.Body)
                    }
                    ProgressBar3D(d.goldTotal().toFloat() / GoldStars.TOTAL, Modifier.fillMaxWidth().padding(top = 4.dp), color = BV.Gold, height = 14.dp)
                }
            }
            item {
                Panel(Modifier.fillMaxWidth(), padding = 10.dp) {
                    Text("The crew", style = Type.Heading.copy(fontSize = 18.sp))
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(Speaker.entries.toList()) { sp ->
                            val met = sp in metSpeakers
                            Column(Modifier.width(66.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Portrait(sp, 56.dp, dim = !met)
                                Text(if (met) sp.displayName else "???", style = Type.Body.copy(fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 1)
                                Text(if (met) sp.role else "Not met yet", style = Type.Small.copy(fontSize = 9.5.sp, textAlign = TextAlign.Center, lineHeight = 11.sp), maxLines = 2)
                            }
                        }
                    }
                }
            }
            Story.beat("prologue")?.takeIf { it.id in d.story }?.let { pro ->
                item { PageCard(-1, "Prologue", "Hazel's old book", null, 0, listOf(pro), open == -1) { open = if (open == -1) -99 else -1 } }
            }
            items(Stores.ALL.size) { i ->
                val s = Stores.ALL[i]
                val opened = s.id in d.stores
                val cfg = Restaurants.config(i)
                val built = d.progressOf(s.id).builtCount(cfg)
                val status = when {
                    !opened -> null
                    d.isComplete(s.id) -> "STAMPED"
                    else -> "Building $built / ${cfg.buildPads}"
                }
                val beats = if (opened) Story.beatsFor(i).filter { readable(it, d) } else emptyList()
                PageCard(i, if (opened) s.name else "???", if (opened) s.perk else "A page still unwritten", status, if (d.isComplete(s.id)) d.goldOf(s.id) or 8 else 0, beats, open == i) { open = if (open == i) -99 else i }
            }
            val golden = listOf("s11_finale", "golden_tour", "golden_book").mapNotNull { Story.beat(it) }.filter { it.id in d.story }
            if (golden.isNotEmpty()) item { PageCard(12, "The Golden Book", "Every page blank, until now", "${d.goldTotal()} / ${GoldStars.TOTAL} gold", 0, golden, open == 12) { open = if (open == 12) -99 else 12 } }
            // ---- part two: the World Tour, one page per region (docs/WORLD_TOUR_DESIGN.md §6)
            if (App.repo.worldTourOpen()) {
                item { Ribbon("THE WORLD TOUR", Modifier.fillMaxWidth().padding(top = 6.dp), color = BV.Teal, dark = BV.TealDark, size = 20.sp) }
                Story.beat("tour_open")?.takeIf { it.id in d.story }?.let { b -> item { RegionPage(-1, d, listOf(b), open == 100) { open = if (open == 100) -99 else 100 } } }
                items(com.ngstudio.burgerready.game.Tour.REGIONS.size) { r ->
                    val beats = listOf("arrive", "mid", "done").mapNotNull { Story.beat("t_${com.ngstudio.burgerready.game.Tour.REGIONS[r].id}_$it") }.filter { it.id in d.story }
                    RegionPage(r, d, beats, open == 101 + r) { open = if (open == 101 + r) -99 else 101 + r }
                }
                Story.beat("tour_finale")?.takeIf { it.id in d.story }?.let { b -> item { RegionPage(-2, d, listOf(b), open == 200) { open = if (open == 200) -99 else 200 } } }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

/** One page of the Voyage Book. [gold] bit 3 = show the gold-star row (finished restaurants). */
@Composable
private fun PageCard(index: Int, name: String, sub: String, status: String?, gold: Int, beats: List<Beat>, expanded: Boolean, onToggle: () -> Unit) {
    val locked = index in 0 until Restaurants.CLASSIC && status == null
    Column(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(BV.Cocoa).padding(3.dp)
        .clip(RoundedCornerShape(16.dp)).background(if (locked) Color(0xFFEDE4DA) else Color(0xFFFFFBF2))
        .clickable(enabled = beats.isNotEmpty(), interactionSource = remember { MutableInteractionSource() }, indication = null) { App.sfx("ui_tap"); onToggle() }.padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(if (locked) Color(0xFFD8D0C8) else BV.Cream), contentAlignment = Alignment.Center) {
                when {
                    locked -> GameIcon(GIcon.LOCK, 26.dp)
                    index in 0 until Restaurants.CLASSIC -> ArtImage("store_${Stores.ALL[index].id}", Modifier.fillMaxSize()) { GameIcon(GIcon.STORE, 34.dp) }
                    index == 12 -> StarIcon(40.dp)
                    else -> GameIcon(GIcon.COMPASS, 38.dp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (index in 0 until Restaurants.CLASSIC) "PAGE ${index + 1}" else if (index == 12) "EPILOGUE" else "BEFORE PAGE 1", style = Type.Small.copy(fontSize = 10.sp, letterSpacing = 1.5.sp, color = BV.TealDark))
                Text(name, style = Type.Heading.copy(fontSize = 18.sp), maxLines = 1)
                Text(sub, style = Type.Small, maxLines = 1)
                if ((gold and 8) != 0) Row(Modifier.padding(top = 2.dp)) { GoldStarsRow(gold and 7, 16.dp) }
            }
            if (status != null) {
                val stamped = status == "STAMPED"
                Box(Modifier.graphicsLayer { rotationZ = if (stamped) -8f else 0f }.clip(RoundedCornerShape(8.dp))
                    .border(2.dp, if (stamped) BV.Tomato else BV.CocoaSoft, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(status, style = Type.Small.copy(fontSize = 10.sp, color = if (stamped) BV.Tomato else BV.CocoaSoft))
                }
            }
        }
        if (beats.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            if (!expanded) Text("${beats.size} ${if (beats.size == 1) "story" else "stories"} · tap to read", style = Type.Small.copy(color = BV.TealDark))
            else {
                // the page's illustration: a postcard from the restaurant, postmarked once the page is stamped
                if (index in 0 until Restaurants.CLASSIC) Postcard(index, stamped = status == "STAMPED", Modifier.padding(top = 4.dp, bottom = 2.dp))
                beats.forEach { b -> Transcript(b) }
            }
        }
    }
}

/** A World Tour page: region art, its host, how many of its 20 restaurants are finished, its scenes. r = -1/-2: opening/finale. */
@Composable
private fun RegionPage(r: Int, d: SaveData, beats: List<Beat>, expanded: Boolean, onToggle: () -> Unit) {
    val reg = if (r >= 0) com.ngstudio.burgerready.game.Tour.REGIONS[r] else null
    val chapter = r + 1
    val unlocked = reg == null || com.ngstudio.burgerready.game.Tour.id(r, 0) in d.stores
    val done = if (reg != null) chapterDone(d, chapter) else 0
    Column(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(BV.Cocoa).padding(3.dp)
        .clip(RoundedCornerShape(16.dp)).background(if (unlocked) Color(0xFFFFFBF2) else Color(0xFFEDE4DA))
        .clickable(enabled = beats.isNotEmpty(), interactionSource = remember { MutableInteractionSource() }, indication = null) { App.sfx("ui_tap"); onToggle() }.padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                when {
                    reg != null && unlocked -> RegionArt(reg, 58.dp)
                    reg != null -> GameIcon(GIcon.LOCK, 26.dp)
                    else -> GameIcon(GIcon.COMPASS, 40.dp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(when (r) { -1 -> "THE GOLDEN BOOK"; -2 -> "THE LAST POSTCARD"; else -> "REGION ${r + 1}" }, style = Type.Small.copy(fontSize = 10.sp, letterSpacing = 1.5.sp, color = BV.TealDark))
                Text(when { r == -1 -> "The World Tour"; r == -2 -> "Every region visited"; unlocked -> reg!!.name; else -> "???" }, style = Type.Heading.copy(fontSize = 18.sp), maxLines = 1)
                if (reg != null) Text(if (unlocked) reg.blurb else "A region still ahead", style = Type.Small, maxLines = 1)
            }
            if (reg != null && unlocked) {
                val stamped = done == com.ngstudio.burgerready.game.Tour.PER_REGION
                Box(Modifier.graphicsLayer { rotationZ = if (stamped) -8f else 0f }.clip(RoundedCornerShape(8.dp))
                    .border(2.dp, if (stamped) BV.Tomato else BV.CocoaSoft, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(if (stamped) "STAMPED" else "$done / ${com.ngstudio.burgerready.game.Tour.PER_REGION}", style = Type.Small.copy(fontSize = 10.sp, color = if (stamped) BV.Tomato else BV.CocoaSoft))
                }
            }
        }
        if (beats.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            if (!expanded) Text("${beats.size} ${if (beats.size == 1) "story" else "stories"} · tap to read", style = Type.Small.copy(color = BV.TealDark))
            else beats.forEach { b -> Transcript(b) }
        }
    }
}

@Composable
private fun Transcript(b: Beat) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(b.title, style = Type.Heading.copy(fontSize = 15.sp, color = BV.Tomato))
        b.lines.forEach { l ->
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                Portrait(l.who, 30.dp)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(l.who.displayName, style = Type.Small.copy(fontSize = 10.sp, color = rgb(l.who.color)))
                    Text(l.text, style = Type.Body.copy(fontSize = 13.sp, lineHeight = 16.sp))
                }
            }
        }
    }
}

/**
 * A Voyage Book page illustration: a postcard with the restaurant's art on its sky colours, a perforated stamp with the
 * page number, and a red postmark once the page is stamped. Drawn in Compose from the existing store renders.
 */
@Composable
private fun Postcard(index: Int, stamped: Boolean, modifier: Modifier = Modifier) {
    val st = Stores.ALL[index]
    val sky = rgb(st.sky); val accent = rgb(st.accent)
    Box(modifier.fillMaxWidth().padding(horizontal = 6.dp).graphicsLayer { rotationZ = if (index % 2 == 0) -1.2f else 1.2f }
        .shadow(5.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFFDF7)).padding(7.dp)) {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(sky.copy(alpha = 0.55f).compositeOverWhite(), sky, accent.copy(alpha = 0.55f).compositeOverWhite())))) {
            ArtImage("store_${st.id}", Modifier.fillMaxSize().padding(top = 6.dp, bottom = 2.dp)) { GameIcon(GIcon.STORE, 80.dp) }
            // caption tag, bottom-left
            Box(Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(BV.Cocoa.copy(alpha = 0.82f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(if (stamped) "Greetings from ${st.name}!" else "Wish you were here…", style = Type.Small.copy(color = BV.Cream, fontSize = 11.sp), maxLines = 1)
            }
            // stamp, top-right
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(46.dp, 54.dp).graphicsLayer { rotationZ = 4f }) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }) {
                    drawRect(Color.White)
                    val r = 2.6.dp.toPx(); val step = 7.dp.toPx()
                    var x = step / 2
                    while (x < size.width) { drawCircle(Color.Black, r, androidx.compose.ui.geometry.Offset(x, 0f), blendMode = androidx.compose.ui.graphics.BlendMode.Clear); drawCircle(Color.Black, r, androidx.compose.ui.geometry.Offset(x, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.Clear); x += step }
                    var y = step / 2
                    while (y < size.height) { drawCircle(Color.Black, r, androidx.compose.ui.geometry.Offset(0f, y), blendMode = androidx.compose.ui.graphics.BlendMode.Clear); drawCircle(Color.Black, r, androidx.compose.ui.geometry.Offset(size.width, y), blendMode = androidx.compose.ui.graphics.BlendMode.Clear); y += step }
                    val inset = 5.dp.toPx()
                    drawRect(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.7f).compositeOverWhite())), androidx.compose.ui.geometry.Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2))
                }
                OutlinedText("${index + 1}", Modifier.align(Alignment.Center), size = 22.sp, stroke = 1.6.dp)
            }
            if (stamped) {
                // postmark: a ring with the trip's name and two wavy cancel lines, over the stamp's corner
                Box(Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 34.dp).size(56.dp).graphicsLayer { rotationZ = -14f }, contentAlignment = Alignment.Center) {
                    // no layer alpha here: it would clip the cancel lines that run out to the left of the ring
                    Canvas(Modifier.fillMaxSize()) {
                        val red = BV.Tomato.copy(alpha = 0.85f)
                        drawCircle(red, size.minDimension / 2 - 2.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
                        drawCircle(red, size.minDimension / 2 - 7.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        for (k in 0..1) {
                            val path = androidx.compose.ui.graphics.Path()
                            val y0 = size.height * (0.38f + k * 0.24f)
                            path.moveTo(-size.width * 0.9f, y0)
                            var xx = -size.width * 0.9f
                            while (xx < -2.dp.toPx()) { path.relativeQuadraticTo(5.dp.toPx(), -4.dp.toPx(), 10.dp.toPx(), 0f); path.relativeQuadraticTo(5.dp.toPx(), 4.dp.toPx(), 10.dp.toPx(), 0f); xx += 20.dp.toPx() }
                            drawPath(path, red, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BUN", style = Type.Small.copy(color = BV.Tomato.copy(alpha = 0.85f), fontSize = 8.sp, letterSpacing = 1.sp, lineHeight = 9.sp))
                        Text("VOYAGE", style = Type.Small.copy(color = BV.Tomato.copy(alpha = 0.85f), fontSize = 8.sp, letterSpacing = 0.5.sp, lineHeight = 9.sp))
                    }
                }
            }
        }
    }
}
