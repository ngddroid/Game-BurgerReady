package com.ngstudio.burgerready.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Lilita
import com.ngstudio.burgerready.ui.theme.Type
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Text with a chunky cocoa outline (drawn by layering offset copies). */
@Composable
fun OutlinedText(
    text: String, modifier: Modifier = Modifier, size: TextUnit = 22.sp, color: Color = BV.White,
    outline: Color = BV.Cocoa, stroke: Dp = 2.dp, align: TextAlign = TextAlign.Center, maxLines: Int = 2,
) {
    // A round-joined stroke behind the fill (plus the same stroke dropped as a shadow): 3 text layers instead of 10
    // offset copies, and a smoother outline.
    val strokePx = with(LocalDensity.current) { stroke.toPx() }
    val style = TextStyle(fontFamily = Lilita, fontSize = size, textAlign = align)
    val outlineStyle = remember(size, align, outline, strokePx) {
        style.copy(color = outline, drawStyle = Stroke(width = strokePx * 2f, join = StrokeJoin.Round))
    }
    Box(modifier) {
        Text(text, style = outlineStyle, maxLines = maxLines, modifier = Modifier.offset(0.dp, stroke * 1.2f))
        Text(text, style = outlineStyle, maxLines = maxLines)
        Text(text, style = style.copy(color = color), maxLines = maxLines)
    }
}

/** Chunky game button with a 3D bottom edge, press squash, click sound and haptic. */
@Composable
fun GameButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BV.Mint,
    dark: Color = BV.MintDark,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    textSize: TextUnit = 22.sp,
    icon: (@Composable () -> Unit)? = null,
    sound: String = "ui_tap",
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val depth = if (pressed) 1.dp else 5.dp
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "btn")
    val c = if (enabled) color else Color(0xFFBDB3AA)
    val d = if (enabled) dark else Color(0xFF8E857E)
    Box(
        modifier
            .scale(scale)
            .height(height + 5.dp)
            .clickable(interactionSource = src, indication = null, enabled = true) {
                if (enabled) { App.sfx(sound); App.haptic(0); onClick() } else App.sfx("ui_error")
            },
    ) {
        Box(Modifier.fillMaxWidth().height(height).offset(y = 5.dp).clip(RoundedCornerShape(18.dp)).background(BV.Cocoa))
        Box(Modifier.fillMaxWidth().height(height).offset(y = 5.dp - depth + 0.dp).clip(RoundedCornerShape(18.dp)).background(d).border(3.dp, BV.Cocoa, RoundedCornerShape(18.dp)))
        Box(
            Modifier.fillMaxWidth().height(height - 5.dp).offset(y = 5.dp - depth).clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(lerp(c, Color.White, 0.25f), c)))
                .border(3.dp, BV.Cocoa, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // glossy highlight
            Box(Modifier.align(Alignment.TopCenter).padding(horizontal = 10.dp, vertical = 5.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.35f)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                if (icon != null) { icon(); Spacer(Modifier.width(6.dp)) }
                OutlinedText(text, size = textSize, stroke = 1.6.dp, maxLines = 1)
            }
        }
    }
}

/** Small round icon button (e.g. settings, close). */
@Composable
fun RoundButton(modifier: Modifier = Modifier, color: Color = BV.Teal, size: Dp = 46.dp, sound: String = "ui_tap", onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.4f, stiffness = 800f), label = "rb")
    Box(
        modifier.scale(scale).size(size).shadow(4.dp, CircleShape).clip(CircleShape).background(BV.Cocoa).padding(3.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.25f), color)))
            .clickable(interactionSource = src, indication = null) { App.sfx(sound); App.haptic(0); onClick() },
        contentAlignment = Alignment.Center, content = content,
    )
}

/** Cream "menu paper" panel with a cocoa outline. */
@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = BV.Cream, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.shadow(8.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(BV.Cocoa).padding(3.dp)
            .clip(RoundedCornerShape(22.dp)).background(color).padding(padding),
        content = content,
    )
}

/** Ribbon title banner. */
@Composable
fun Ribbon(text: String, modifier: Modifier = Modifier, color: Color = BV.Tomato, dark: Color = BV.TomatoDark, size: TextUnit = 24.sp) {
    Box(modifier.height(54.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.toPx(); val h = this.size.height
            val tail = 22.dp.toPx()
            fun tailPath(left: Boolean): Path = Path().apply {
                val x0 = if (left) 0f else this@Canvas.size.width
                val dir = if (left) 1f else -1f
                moveTo(x0, h * 0.25f); lineTo(x0 + dir * tail * 1.6f, h * 0.25f); lineTo(x0 + dir * tail * 1.6f, h * 0.95f); lineTo(x0, h * 0.95f); lineTo(x0 + dir * tail * 0.55f, h * 0.6f); close()
            }
            drawPath(tailPath(true), dark); drawPath(tailPath(false), dark)
            drawPath(tailPath(true), BV.Cocoa, style = Stroke(3.dp.toPx())); drawPath(tailPath(false), BV.Cocoa, style = Stroke(3.dp.toPx()))
            val bodyL = tail * 0.9f; val bodyR = this.size.width - tail * 0.9f
            drawRoundRect(color, Offset(bodyL, 0f), Size(bodyR - bodyL, h * 0.82f), CornerRadius(10.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(bodyL + 8, 6f), Size(bodyR - bodyL - 16, h * 0.18f), CornerRadius(6.dp.toPx()))
            drawRoundRect(BV.Cocoa, Offset(bodyL, 0f), Size(bodyR - bodyL, h * 0.82f), CornerRadius(10.dp.toPx()), style = Stroke(3.dp.toPx()))
        }
        OutlinedText(text, Modifier.offset(y = (-4).dp).padding(horizontal = 40.dp), size = size, maxLines = 1)
    }
}

/** Currency pill (icon + animated number). */
@Composable
fun CurrencyChip(icon: @Composable () -> Unit, value: Long, modifier: Modifier = Modifier, color: Color = BV.Cocoa.copy(alpha = 0.85f), onPlus: (() -> Unit)? = null) {
    val anim = remember { Animatable(value.toFloat()) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(600)) }
    Row(modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(color).border(2.dp, BV.Cocoa, RoundedCornerShape(18.dp)).padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { icon() }
        val txt = formatNum(anim.value.toLong())
        // never wrap ("5,00" over "0" in the narrow gem chip): long values step down a size
        Text(txt, style = Type.Number.copy(fontSize = if (txt.length >= 5) 15.sp else 17.sp), modifier = Modifier.widthIn(min = 30.dp), maxLines = 1, softWrap = false)
        if (onPlus != null) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(20.dp).clip(CircleShape).background(BV.Mint).clickable { App.sfx("ui_tap"); onPlus() }, contentAlignment = Alignment.Center) {
                Text("+", style = Type.Number.copy(fontSize = 15.sp))
            }
        }
    }
}

/** Wallet-style numbers: "9,999", then three significant digits ("12.5K", "900K", "1.25M", "84.2B"). */
fun formatNum(v: Long): String = when {
    v >= 1_000_000_000_000 -> sig3(v / 1e12, "T")
    v >= 1_000_000_000 -> sig3(v / 1e9, "B")
    v >= 1_000_000 -> sig3(v / 1e6, "M")
    v >= 10_000 -> sig3(v / 1e3, "K")
    else -> "%,d".format(v)
}

private fun sig3(x: Double, unit: String): String {
    // floor, so a wallet never shows more than it holds; "84.0K" → "84K", "1.50M" → "1.5M"
    val t = when {
        x >= 100 -> String.format(java.util.Locale.US, "%.0f", kotlin.math.floor(x))
        x >= 10 -> String.format(java.util.Locale.US, "%.1f", kotlin.math.floor(x * 10) / 10)
        else -> String.format(java.util.Locale.US, "%.2f", kotlin.math.floor(x * 100) / 100)
    }
    return (if ('.' in t) t.trimEnd('0').trimEnd('.') else t) + unit
}

/** Coin icon drawn in code (gold coin with a bun emblem). */
@Composable
fun CoinIcon(size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(BV.Cocoa, r)
        drawCircle(Color(0xFFE0A21B), r * 0.86f)
        drawCircle(BV.Gold, r * 0.7f)
        drawArc(Color(0xFFE0A21B), 200f, 140f, true, Offset(center.x - r * 0.4f, center.y - r * 0.35f), Size(r * 0.8f, r * 0.55f))
        drawRoundRect(Color(0xFFE0A21B), Offset(center.x - r * 0.4f, center.y + r * 0.05f), Size(r * 0.8f, r * 0.2f), CornerRadius(r * 0.1f))
        drawCircle(Color.White.copy(alpha = 0.6f), r * 0.14f, Offset(center.x - r * 0.35f, center.y - r * 0.4f))
    }
}

/** Bucks icon: green note with a bun stamp. */
@Composable
fun BucksIcon(size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        rotate(-12f) {
            drawRoundRect(BV.Cocoa, Offset(w * 0.04f, h * 0.2f), Size(w * 0.92f, h * 0.6f), CornerRadius(w * 0.1f))
            drawRoundRect(Color(0xFF4CC38A), Offset(w * 0.1f, h * 0.26f), Size(w * 0.8f, h * 0.48f), CornerRadius(w * 0.07f))
            drawCircle(Color(0xFFB6F2CF), w * 0.14f, Offset(w / 2, h / 2))
            drawCircle(Color(0xFF1FAA6E), w * 0.07f, Offset(w / 2, h / 2))
        }
    }
}

/** Gem icon: faceted teal diamond. */
@Composable
fun GemIcon(size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply { moveTo(w * 0.5f, h * 0.92f); lineTo(w * 0.05f, h * 0.4f); lineTo(w * 0.25f, h * 0.12f); lineTo(w * 0.75f, h * 0.12f); lineTo(w * 0.95f, h * 0.4f); close() }
        drawPath(p, BV.Cocoa)
        val i = Path().apply { moveTo(w * 0.5f, h * 0.8f); lineTo(w * 0.15f, h * 0.4f); lineTo(w * 0.3f, h * 0.2f); lineTo(w * 0.7f, h * 0.2f); lineTo(w * 0.85f, h * 0.4f); close() }
        drawPath(i, BV.Gem)
        val t = Path().apply { moveTo(w * 0.3f, h * 0.2f); lineTo(w * 0.7f, h * 0.2f); lineTo(w * 0.62f, h * 0.4f); lineTo(w * 0.38f, h * 0.4f); close() }
        drawPath(t, Color(0xFFB8FFF8))
    }
}

@Composable
fun StarIcon(size: Dp = 24.dp, filled: Boolean = true, color: Color = BV.Gold) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        fun star(rr: Float): Path = Path().apply {
            for (k in 0 until 10) {
                val a = -PI / 2 + k * PI / 5
                val rad = if (k % 2 == 0) rr else rr * 0.48f
                val x = center.x + (cos(a) * rad).toFloat(); val y = center.y + (sin(a) * rad).toFloat()
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(star(r), BV.Cocoa)
        drawPath(star(r * 0.78f), if (filled) color else Color(0xFFD9CFC4))
        if (filled) drawCircle(Color.White.copy(alpha = 0.5f), r * 0.12f, Offset(center.x - r * 0.2f, center.y - r * 0.25f))
    }
}

/** 3D-ish progress bar. */
@Composable
fun ProgressBar3D(progress: Float, modifier: Modifier = Modifier, color: Color = BV.Mint, height: Dp = 18.dp, markers: List<Float> = emptyList(), animate: Boolean = true) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "pb")
    val p = if (animate) animated else progress.coerceIn(0f, 1f)
    Box(modifier.height(height).clip(RoundedCornerShape(height / 2)).background(BV.Cocoa).padding(3.dp)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(height / 2)).background(Color(0x55FFFFFF)))
        Box(Modifier.fillMaxWidth(p).fillMaxSize().clip(RoundedCornerShape(height / 2))
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.35f), color, lerp(color, Color.Black, 0.12f)))))
        Canvas(Modifier.fillMaxSize()) {
            for (m in markers) drawLine(BV.Cocoa.copy(alpha = 0.6f), Offset(size.width * m, 0f), Offset(size.width * m, size.height), 2.dp.toPx())
        }
    }
}

/** Rotating sunburst backdrop used on splash, results and reward popups. */
@Composable
fun Sunburst(modifier: Modifier = Modifier, a: Color = BV.Mustard, b: Color = Color(0xFFFFD86B), rays: Int = 18, speed: Int = 24000) {
    val t = rememberInfiniteTransition(label = "sb")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(speed, easing = LinearEasing)), label = "sbr")
    Canvas(modifier) {
        drawRect(a)
        val r = size.maxDimension * 1.2f
        rotate(rot, center) {
            for (k in 0 until rays) {
                val a0 = (k * 2 * PI / rays).toFloat(); val a1 = a0 + (PI / rays).toFloat()
                val p = Path().apply { moveTo(center.x, center.y); lineTo(center.x + cos(a0) * r, center.y + sin(a0) * r); lineTo(center.x + cos(a1) * r, center.y + sin(a1) * r); close() }
                drawPath(p, b)
            }
        }
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center, size.minDimension * 0.45f), size.minDimension * 0.45f, center)
    }
}

/** Gentle bobbing modifier for idle UI elements. */
@Composable
fun Modifier.bob(amount: Float = 6f, period: Int = 1600): Modifier {
    val t = rememberInfiniteTransition(label = "bob")
    val y by t.animateFloat(-amount, amount, infiniteRepeatable(tween(period), RepeatMode.Reverse), label = "boby")
    return this.graphicsLayer { translationY = y }
}

@Composable
fun Modifier.pulse(min: Float = 0.94f, max: Float = 1.06f, period: Int = 900): Modifier {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(min, max, infiniteRepeatable(tween(period), RepeatMode.Reverse), label = "ps")
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

/** Hides content above [clear] and fades it in over [fade] (for lists that scroll under a floating bar). */
fun Modifier.fadeTop(clear: Dp, fade: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val a = clear.toPx()
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = a, endY = a + fade.toPx()), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
    }

/** Red notification dot. */
@Composable
fun BadgeDot(count: Int = 0, modifier: Modifier = Modifier) {
    Box(modifier.size(if (count > 0) 20.dp else 14.dp).clip(CircleShape).background(BV.Cocoa).padding(2.dp).clip(CircleShape).background(BV.Tomato), contentAlignment = Alignment.Center) {
        if (count > 0) Text(if (count > 9) "9+" else count.toString(), style = Type.Number.copy(fontSize = 11.sp))
    }
}

/** Darkened full-screen scrim with a centred popping card. */
@Composable
fun GameDialog(onDismiss: (() -> Unit)?, content: @Composable BoxScope.() -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { App.sfx("ui_popup"); appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f)) }
    Box(
        Modifier.fillMaxSize().background(Color(0xAA1E1420)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.padding(22.dp).graphicsLayer { scaleX = 0.6f + 0.4f * appear.value; scaleY = 0.6f + 0.4f * appear.value; alpha = appear.value.coerceIn(0f, 1f) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, content = content)
    }
}

@Composable
fun CloseButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    RoundButton(modifier, color = BV.Tomato, size = 42.dp, sound = "ui_back", onClick = onClick) { OutlinedText("✕", size = 18.sp, stroke = 1.2.dp) }
}

/** Simple confetti burst overlay (Compose-side, for menus). */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(1f) }
    LaunchedEffect(trigger) { if (trigger > 0) { anim.snapTo(0f); anim.animateTo(1f, tween(1600, easing = LinearEasing)) } }
    val cols = listOf(BV.Mustard, BV.Tomato, BV.Mint, BV.Teal, BV.Pink, BV.Lilac)
    Canvas(modifier) {
        val t = anim.value
        if (t >= 1f) return@Canvas
        val rnd = java.util.Random(trigger.toLong())
        for (i in 0 until 70) {
            val ang = rnd.nextFloat() * 2 * PI.toFloat()
            val sp = 0.35f + rnd.nextFloat() * 0.65f
            val x = size.width / 2 + cos(ang) * sp * size.width * 0.6f * t
            val y = size.height * 0.4f + sin(ang) * sp * size.height * 0.35f * t + size.height * 0.6f * t * t
            rotate(t * 720f * (if (i % 2 == 0) 1 else -1), Offset(x, y)) {
                drawRect(cols[i % cols.size].copy(alpha = 1f - t * 0.8f), Offset(x, y), Size(10.dp.toPx(), 5.dp.toPx()))
            }
        }
    }
}

/** Card tile used in shops/grids. */
@Composable
fun ItemCard(
    modifier: Modifier = Modifier, selected: Boolean = false, locked: Boolean = false, color: Color = BV.Cream,
    onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "ic")
    Column(
        modifier.scale(scale).shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(if (selected) BV.Mint else BV.Cocoa).padding(3.dp).clip(RoundedCornerShape(16.dp))
            .background(if (locked) Color(0xFFEDE3D6) else color)
            .clickable(interactionSource = src, indication = null) { App.sfx("ui_tap"); onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, content = content,
    )
}

@Composable
fun PriceTag(bucks: Long, gems: Int, affordable: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (gems > 0) GemIcon(18.dp) else BucksIcon(18.dp)
        Spacer(Modifier.width(3.dp))
        Text(if (gems > 0) gems.toString() else formatNum(bucks), style = Type.Body.copy(fontFamily = Lilita, fontWeight = FontWeight.Normal, fontSize = 15.sp, color = if (affordable) BV.Cocoa else BV.Tomato))
    }
}

@Composable
fun RowScope.Weighted(weight: Float = 1f, content: @Composable () -> Unit) { Box(Modifier.weight(weight)) { content() } }
