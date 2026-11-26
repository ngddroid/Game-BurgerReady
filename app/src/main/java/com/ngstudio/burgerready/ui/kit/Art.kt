package com.ngstudio.burgerready.ui.kit

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.ui.theme.BV
import kotlin.math.PI
import kotlin.math.sin

/** Loads pre-rendered HD art from assets/ui (Blender renders) with an in-memory cache. */
object Art {
    private val cache = HashMap<String, ImageBitmap?>()
    fun get(name: String): ImageBitmap? = synchronized(cache) {
        cache.getOrPut(name) {
            try {
                App.instance.assets.open("ui/$name.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            } catch (e: Exception) {
                try { App.instance.assets.open("ui/$name.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } } catch (e2: Exception) { null }
            }
        }
    }
}

/** Shows `assets/ui/<name>` if present, otherwise [fallback]. */
@Composable
fun ArtImage(name: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Fit, fallback: @Composable () -> Unit = {}) {
    val bmp = remember(name) { Art.get(name) }
    if (bmp != null) Image(bmp, contentDescription = null, modifier = modifier, contentScale = scale)
    else Box(modifier, contentAlignment = Alignment.Center) { fallback() }
}

/** Vector burger drawn layer by layer (used on the splash and as a fallback illustration). */
@Composable
fun VectorBurger(modifier: Modifier, layers: Float = 5f) {
    Canvas(modifier) { drawBurger(layers) }
}

fun DrawScope.drawBurger(layers: Float) {
    val w = size.width; val h = size.height
    val cx = w / 2
    val outline = Stroke(w * 0.022f)
    fun layer(i: Int): Float = (layers - i).coerceIn(0f, 1f)
    fun drop(i: Int) = (1f - layer(i)) * -h * 0.6f
    // bottom bun
    if (layer(0) > 0f) {
        val y = h * 0.72f + drop(0)
        drawRoundRect(BV.Cocoa, Offset(cx - w * 0.42f, y), Size(w * 0.84f, h * 0.16f), CornerRadius(w * 0.09f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF6C27A), Color(0xFFD9822B)), y, y + h * 0.16f), Offset(cx - w * 0.4f, y + h * 0.012f), Size(w * 0.8f, h * 0.135f), CornerRadius(w * 0.08f))
    }
    // patty
    if (layer(1) > 0f) {
        val y = h * 0.6f + drop(1)
        drawRoundRect(BV.Cocoa, Offset(cx - w * 0.45f, y), Size(w * 0.9f, h * 0.14f), CornerRadius(w * 0.07f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF8A4A30), Color(0xFF4A2618)), y, y + h * 0.14f), Offset(cx - w * 0.43f, y + h * 0.012f), Size(w * 0.86f, h * 0.115f), CornerRadius(w * 0.06f))
        for (k in 0 until 7) drawCircle(Color(0xFF3A1E12), w * 0.012f, Offset(cx - w * 0.33f + k * w * 0.11f, y + h * 0.06f + (k % 2) * h * 0.02f))
    }
    // cheese
    if (layer(2) > 0f) {
        val y = h * 0.56f + drop(2)
        val p = Path().apply {
            moveTo(cx - w * 0.44f, y); lineTo(cx + w * 0.44f, y); lineTo(cx + w * 0.4f, y + h * 0.05f)
            lineTo(cx + w * 0.22f, y + h * 0.05f); quadraticTo(cx + w * 0.16f, y + h * 0.16f, cx + w * 0.1f, y + h * 0.05f)
            lineTo(cx - w * 0.12f, y + h * 0.05f); quadraticTo(cx - w * 0.2f, y + h * 0.13f, cx - w * 0.26f, y + h * 0.05f)
            lineTo(cx - w * 0.4f, y + h * 0.05f); close()
        }
        drawPath(p, BV.Cocoa, style = outline)
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFE070), Color(0xFFFFB020)), y, y + h * 0.15f))
    }
    // lettuce
    if (layer(3) > 0f) {
        val y = h * 0.5f + drop(3)
        val p = Path().apply {
            moveTo(cx - w * 0.47f, y + h * 0.05f)
            var x = -0.47f
            while (x < 0.47f) {
                quadraticTo(cx + (x + 0.035f) * w, y + h * 0.1f, cx + (x + 0.07f) * w, y + h * 0.05f)
                x += 0.07f
            }
            lineTo(cx + w * 0.45f, y); lineTo(cx - w * 0.45f, y); close()
        }
        drawPath(p, Color(0xFF6CC24A)); drawPath(p, BV.Cocoa, style = outline)
    }
    // top bun
    if (layer(4) > 0f) {
        val y = h * 0.14f + drop(4)
        val p = Path().apply {
            moveTo(cx - w * 0.44f, y + h * 0.38f)
            cubicTo(cx - w * 0.46f, y - h * 0.02f, cx + w * 0.46f, y - h * 0.02f, cx + w * 0.44f, y + h * 0.38f)
            close()
        }
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFFF2A65A), Color(0xFFD9822B)), y, y + h * 0.38f))
        drawPath(p, BV.Cocoa, style = outline)
        drawOval(Color.White.copy(alpha = 0.35f), Offset(cx - w * 0.28f, y + h * 0.06f), Size(w * 0.22f, h * 0.07f))
        val seeds = listOf(-0.22f to 0.14f, -0.08f to 0.09f, 0.08f to 0.1f, 0.22f to 0.15f, -0.15f to 0.22f, 0.02f to 0.19f, 0.16f to 0.24f)
        for ((sx, sy) in seeds) drawOval(Color(0xFFFFF1CF), Offset(cx + sx * w - w * 0.02f, y + sy * h), Size(w * 0.045f, h * 0.024f))
    }
}

/** Little animal chef face (fallback avatar). */
fun DrawScope.drawCorgiFace(wobble: Float = 0f) {
    val w = size.width; val h = size.height; val cx = w / 2; val cy = h * 0.56f
    val ear = Path().apply { moveTo(cx - w * 0.36f, cy - h * 0.12f); lineTo(cx - w * 0.3f, cy - h * 0.5f); lineTo(cx - w * 0.08f, cy - h * 0.26f); close() }
    val ear2 = Path().apply { moveTo(cx + w * 0.36f, cy - h * 0.12f); lineTo(cx + w * 0.3f, cy - h * 0.5f); lineTo(cx + w * 0.08f, cy - h * 0.26f); close() }
    for (e in listOf(ear, ear2)) { drawPath(e, Color(0xFFF29A3A)); drawPath(e, BV.Cocoa, style = Stroke(w * 0.025f)) }
    drawCircle(BV.Cocoa, w * 0.38f, Offset(cx, cy))
    drawCircle(Color(0xFFF29A3A), w * 0.355f, Offset(cx, cy))
    drawOval(Color(0xFFFFF6EA), Offset(cx - w * 0.2f, cy - h * 0.02f), Size(w * 0.4f, h * 0.34f))
    val blink = if (sin(wobble * 2.1f) > 0.97f) 0.15f else 1f
    drawOval(BV.Cocoa, Offset(cx - w * 0.17f, cy - h * 0.08f), Size(w * 0.08f, h * 0.1f * blink))
    drawOval(BV.Cocoa, Offset(cx + w * 0.09f, cy - h * 0.08f), Size(w * 0.08f, h * 0.1f * blink))
    drawCircle(Color.White, w * 0.015f, Offset(cx - w * 0.14f, cy - h * 0.06f))
    drawCircle(Color.White, w * 0.015f, Offset(cx + w * 0.12f, cy - h * 0.06f))
    drawOval(BV.Cocoa, Offset(cx - w * 0.05f, cy + h * 0.05f), Size(w * 0.1f, h * 0.065f))
    drawArc(BV.Cocoa, 20f, 140f, false, Offset(cx - w * 0.08f, cy + h * 0.06f), Size(w * 0.16f, h * 0.1f), style = Stroke(w * 0.022f))
    drawCircle(Color(0x55FF6F8C), w * 0.045f, Offset(cx - w * 0.22f, cy + h * 0.06f))
    drawCircle(Color(0x55FF6F8C), w * 0.045f, Offset(cx + w * 0.22f, cy + h * 0.06f))
    // toque
    val tq = h * 0.28f
    drawRoundRect(BV.Cocoa, Offset(cx - w * 0.24f, cy - h * 0.5f), Size(w * 0.48f, tq * 0.55f), CornerRadius(w * 0.05f))
    drawRoundRect(Color.White, Offset(cx - w * 0.22f, cy - h * 0.49f), Size(w * 0.44f, tq * 0.48f), CornerRadius(w * 0.04f))
    for (k in -1..1) {
        drawCircle(BV.Cocoa, w * 0.15f, Offset(cx + k * w * 0.14f, cy - h * 0.6f))
    }
    for (k in -1..1) drawCircle(Color.White, w * 0.13f, Offset(cx + k * w * 0.14f, cy - h * 0.6f))
    @Suppress("UNUSED_VARIABLE") val unused = PI
}
