package com.ngstudio.burgerready.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngstudio.burgerready.ui.theme.BV

/** Hand-drawn vector icons in the game style (cocoa outline + flat fills). */
enum class GIcon { SHOP, KITCHEN, STORE, STYLE, GEAR, LOCK, SPIN, MISSION, CALENDAR, BOOK, TROPHY, CHEST, PLAY, CLOSE, PAUSE, MUSIC, SOUND, VIBRATE, DOC, SHIELD, STAR, HOME, RETRY, AD, BOLT, PAW, HAT, CLOCK, GIFT, COMPASS }

@Composable
fun GameIcon(icon: GIcon, size: Dp = 28.dp, tint: Color? = null) {
    Canvas(Modifier.size(size)) { drawGameIcon(icon, tint) }
}

fun DrawScope.drawGameIcon(icon: GIcon, tint: Color? = null) {
    val w = size.width; val h = size.height
    val o = Stroke(w * 0.08f, cap = StrokeCap.Round)
    val c = BV.Cocoa
    fun fill(p: Path, col: Color) { drawPath(p, tint ?: col); drawPath(p, c, style = o) }
    when (icon) {
        GIcon.SHOP -> {
            val bag = Path().apply { moveTo(w * 0.15f, h * 0.35f); lineTo(w * 0.85f, h * 0.35f); lineTo(w * 0.8f, h * 0.9f); lineTo(w * 0.2f, h * 0.9f); close() }
            fill(bag, BV.Tomato)
            drawArc(c, 180f, 180f, false, Offset(w * 0.32f, h * 0.12f), Size(w * 0.36f, h * 0.42f), style = o)
            drawCircle(Color.White.copy(alpha = 0.7f), w * 0.06f, Offset(w * 0.5f, h * 0.6f))
        }
        GIcon.KITCHEN, GIcon.HAT -> {
            val hat = Path().apply {
                moveTo(w * 0.25f, h * 0.88f); lineTo(w * 0.25f, h * 0.55f)
                cubicTo(w * 0.0f, h * 0.5f, w * 0.1f, h * 0.1f, w * 0.38f, h * 0.22f)
                cubicTo(w * 0.42f, h * 0.0f, w * 0.62f, h * 0.02f, w * 0.64f, h * 0.22f)
                cubicTo(w * 0.92f, h * 0.12f, w * 1.0f, h * 0.5f, w * 0.75f, h * 0.55f)
                lineTo(w * 0.75f, h * 0.88f); close()
            }
            fill(hat, Color.White)
            drawLine(c, Offset(w * 0.25f, h * 0.7f), Offset(w * 0.75f, h * 0.7f), w * 0.06f)
        }
        GIcon.STORE, GIcon.HOME -> {
            drawRoundRect(tint ?: BV.Cream, Offset(w * 0.15f, h * 0.42f), Size(w * 0.7f, h * 0.48f), CornerRadius(w * 0.05f))
            drawRoundRect(c, Offset(w * 0.15f, h * 0.42f), Size(w * 0.7f, h * 0.48f), CornerRadius(w * 0.05f), style = o)
            val awn = Path().apply { moveTo(w * 0.08f, h * 0.45f); lineTo(w * 0.2f, h * 0.15f); lineTo(w * 0.8f, h * 0.15f); lineTo(w * 0.92f, h * 0.45f); close() }
            fill(awn, BV.Tomato)
            for (k in 0..2) drawLine(Color.White, Offset(w * (0.32f + k * 0.18f), h * 0.18f), Offset(w * (0.28f + k * 0.2f), h * 0.42f), w * 0.06f)
            drawRoundRect(BV.Teal, Offset(w * 0.42f, h * 0.6f), Size(w * 0.16f, h * 0.3f), CornerRadius(w * 0.03f))
        }
        GIcon.STYLE -> {
            val pal = Path().apply {
                moveTo(w * 0.5f, h * 0.08f)
                cubicTo(w * 1.05f, h * 0.08f, w * 1.0f, h * 0.75f, w * 0.68f, h * 0.7f)
                cubicTo(w * 0.55f, h * 0.68f, w * 0.6f, h * 0.92f, w * 0.42f, h * 0.92f)
                cubicTo(w * -0.05f, h * 0.88f, w * -0.02f, h * 0.08f, w * 0.5f, h * 0.08f); close()
            }
            fill(pal, BV.Cream)
            drawCircle(BV.Tomato, w * 0.08f, Offset(w * 0.32f, h * 0.32f)); drawCircle(BV.Mint, w * 0.08f, Offset(w * 0.56f, h * 0.26f))
            drawCircle(BV.Teal, w * 0.08f, Offset(w * 0.74f, h * 0.44f)); drawCircle(BV.Mustard, w * 0.08f, Offset(w * 0.28f, h * 0.6f))
        }
        GIcon.GEAR -> {
            for (k in 0 until 8) {
                val a = k * Math.PI / 4
                val x = w / 2 + (kotlin.math.cos(a) * w * 0.36f).toFloat(); val y = h / 2 + (kotlin.math.sin(a) * h * 0.36f).toFloat()
                drawCircle(c, w * 0.12f, Offset(x, y))
            }
            drawCircle(c, w * 0.36f, center); drawCircle(tint ?: Color.White, w * 0.29f, center)
            drawCircle(c, w * 0.13f, center); drawCircle(tint?.copy(alpha = 0.5f) ?: BV.CreamDark, w * 0.08f, center)
        }
        GIcon.LOCK -> {
            drawArc(c, 180f, 180f, false, Offset(w * 0.28f, h * 0.12f), Size(w * 0.44f, h * 0.5f), style = Stroke(w * 0.12f))
            drawRoundRect(c, Offset(w * 0.16f, h * 0.4f), Size(w * 0.68f, h * 0.52f), CornerRadius(w * 0.1f))
            drawRoundRect(tint ?: BV.Mustard, Offset(w * 0.22f, h * 0.46f), Size(w * 0.56f, h * 0.4f), CornerRadius(w * 0.07f))
            drawCircle(c, w * 0.07f, Offset(w * 0.5f, h * 0.62f))
        }
        GIcon.SPIN -> {
            drawCircle(c, w * 0.46f, center)
            val cols = listOf(BV.Tomato, BV.Mustard, BV.Mint, BV.Teal, BV.Pink, BV.Lilac)
            for (k in 0 until 6) drawArc(cols[k], k * 60f, 60f, true, Offset(w * 0.08f, h * 0.08f), Size(w * 0.84f, h * 0.84f))
            drawCircle(c, w * 0.12f, center); drawCircle(Color.White, w * 0.07f, center)
        }
        GIcon.MISSION -> {
            drawRoundRect(c, Offset(w * 0.15f, h * 0.08f), Size(w * 0.7f, h * 0.86f), CornerRadius(w * 0.08f))
            drawRoundRect(tint ?: Color.White, Offset(w * 0.21f, h * 0.14f), Size(w * 0.58f, h * 0.74f), CornerRadius(w * 0.05f))
            for (k in 0..2) {
                drawLine(BV.Mint, Offset(w * 0.28f, h * (0.3f + k * 0.2f)), Offset(w * 0.36f, h * (0.36f + k * 0.2f)), w * 0.06f, StrokeCap.Round)
                drawLine(BV.Mint, Offset(w * 0.36f, h * (0.36f + k * 0.2f)), Offset(w * 0.44f, h * (0.26f + k * 0.2f)), w * 0.06f, StrokeCap.Round)
                drawLine(c, Offset(w * 0.5f, h * (0.32f + k * 0.2f)), Offset(w * 0.72f, h * (0.32f + k * 0.2f)), w * 0.05f, StrokeCap.Round)
            }
        }
        GIcon.CALENDAR, GIcon.GIFT -> {
            drawRoundRect(c, Offset(w * 0.1f, h * 0.36f), Size(w * 0.8f, h * 0.56f), CornerRadius(w * 0.06f))
            drawRoundRect(tint ?: BV.Tomato, Offset(w * 0.16f, h * 0.42f), Size(w * 0.68f, h * 0.44f), CornerRadius(w * 0.04f))
            drawRoundRect(c, Offset(w * 0.06f, h * 0.24f), Size(w * 0.88f, h * 0.18f), CornerRadius(w * 0.05f))
            drawRoundRect(BV.Mustard, Offset(w * 0.1f, h * 0.27f), Size(w * 0.8f, h * 0.12f), CornerRadius(w * 0.04f))
            drawRect(BV.Mustard, Offset(w * 0.44f, h * 0.27f), Size(w * 0.12f, h * 0.6f))
            drawCircle(c, w * 0.1f, Offset(w * 0.38f, h * 0.18f), style = o); drawCircle(c, w * 0.1f, Offset(w * 0.62f, h * 0.18f), style = o)
        }
        GIcon.BOOK -> {
            drawRoundRect(c, Offset(w * 0.12f, h * 0.1f), Size(w * 0.76f, h * 0.82f), CornerRadius(w * 0.06f))
            drawRoundRect(tint ?: BV.Teal, Offset(w * 0.18f, h * 0.14f), Size(w * 0.64f, h * 0.68f), CornerRadius(w * 0.04f))
            drawRect(Color.White, Offset(w * 0.18f, h * 0.8f), Size(w * 0.64f, h * 0.06f))
            drawCircle(BV.Mustard, w * 0.13f, Offset(w * 0.5f, h * 0.45f)); drawCircle(c, w * 0.13f, Offset(w * 0.5f, h * 0.45f), style = Stroke(w * 0.05f))
        }
        GIcon.TROPHY -> {
            val cup = Path().apply { moveTo(w * 0.22f, h * 0.12f); lineTo(w * 0.78f, h * 0.12f); cubicTo(w * 0.78f, h * 0.55f, w * 0.6f, h * 0.62f, w * 0.5f, h * 0.62f); cubicTo(w * 0.4f, h * 0.62f, w * 0.22f, h * 0.55f, w * 0.22f, h * 0.12f); close() }
            drawArc(c, 90f, 180f, false, Offset(w * 0.06f, h * 0.18f), Size(w * 0.26f, h * 0.26f), style = o)
            drawArc(c, -90f, 180f, false, Offset(w * 0.68f, h * 0.18f), Size(w * 0.26f, h * 0.26f), style = o)
            fill(cup, BV.Gold)
            drawRect(c, Offset(w * 0.44f, h * 0.6f), Size(w * 0.12f, h * 0.16f))
            drawRoundRect(c, Offset(w * 0.28f, h * 0.76f), Size(w * 0.44f, h * 0.14f), CornerRadius(w * 0.04f))
        }
        GIcon.CHEST -> {
            drawRoundRect(c, Offset(w * 0.08f, h * 0.3f), Size(w * 0.84f, h * 0.6f), CornerRadius(w * 0.08f))
            drawRoundRect(tint ?: Color(0xFFC98A55), Offset(w * 0.14f, h * 0.36f), Size(w * 0.72f, h * 0.48f), CornerRadius(w * 0.05f))
            drawRect(BV.Gold, Offset(w * 0.14f, h * 0.5f), Size(w * 0.72f, h * 0.08f))
            drawRoundRect(BV.Gold, Offset(w * 0.42f, h * 0.44f), Size(w * 0.16f, h * 0.2f), CornerRadius(w * 0.03f))
            drawRoundRect(c, Offset(w * 0.42f, h * 0.44f), Size(w * 0.16f, h * 0.2f), CornerRadius(w * 0.03f), style = Stroke(w * 0.04f))
        }
        GIcon.PLAY -> { val p = Path().apply { moveTo(w * 0.3f, h * 0.18f); lineTo(w * 0.82f, h * 0.5f); lineTo(w * 0.3f, h * 0.82f); close() }; fill(p, tint ?: Color.White) }
        GIcon.CLOSE -> { drawLine(c, Offset(w * 0.25f, h * 0.25f), Offset(w * 0.75f, h * 0.75f), w * 0.14f, StrokeCap.Round); drawLine(c, Offset(w * 0.75f, h * 0.25f), Offset(w * 0.25f, h * 0.75f), w * 0.14f, StrokeCap.Round) }
        GIcon.PAUSE -> { drawRoundRect(tint ?: Color.White, Offset(w * 0.26f, h * 0.2f), Size(w * 0.17f, h * 0.6f), CornerRadius(w * 0.05f)); drawRoundRect(tint ?: Color.White, Offset(w * 0.57f, h * 0.2f), Size(w * 0.17f, h * 0.6f), CornerRadius(w * 0.05f)) }
        GIcon.MUSIC -> {
            drawLine(c, Offset(w * 0.4f, h * 0.72f), Offset(w * 0.4f, h * 0.18f), w * 0.09f); drawLine(c, Offset(w * 0.4f, h * 0.18f), Offset(w * 0.78f, h * 0.1f), w * 0.12f)
            drawLine(c, Offset(w * 0.78f, h * 0.1f), Offset(w * 0.78f, h * 0.62f), w * 0.09f)
            drawCircle(tint ?: BV.Pink, w * 0.14f, Offset(w * 0.28f, h * 0.74f)); drawCircle(c, w * 0.14f, Offset(w * 0.28f, h * 0.74f), style = Stroke(w * 0.06f))
            drawCircle(tint ?: BV.Pink, w * 0.14f, Offset(w * 0.66f, h * 0.66f)); drawCircle(c, w * 0.14f, Offset(w * 0.66f, h * 0.66f), style = Stroke(w * 0.06f))
        }
        GIcon.SOUND -> {
            val sp = Path().apply { moveTo(w * 0.1f, h * 0.38f); lineTo(w * 0.3f, h * 0.38f); lineTo(w * 0.55f, h * 0.15f); lineTo(w * 0.55f, h * 0.85f); lineTo(w * 0.3f, h * 0.62f); lineTo(w * 0.1f, h * 0.62f); close() }
            fill(sp, tint ?: BV.Mustard)
            drawArc(c, -45f, 90f, false, Offset(w * 0.45f, h * 0.3f), Size(w * 0.3f, h * 0.4f), style = o)
            drawArc(c, -45f, 90f, false, Offset(w * 0.45f, h * 0.15f), Size(w * 0.48f, h * 0.7f), style = o)
        }
        GIcon.VIBRATE -> {
            drawRoundRect(c, Offset(w * 0.3f, h * 0.1f), Size(w * 0.4f, h * 0.8f), CornerRadius(w * 0.08f))
            drawRoundRect(tint ?: BV.Teal, Offset(w * 0.35f, h * 0.16f), Size(w * 0.3f, h * 0.62f), CornerRadius(w * 0.04f))
            for (s in listOf(-1, 1)) { val x = w * 0.5f + s * w * 0.34f; drawLine(c, Offset(x, h * 0.3f), Offset(x, h * 0.7f), w * 0.07f, StrokeCap.Round) }
        }
        GIcon.DOC, GIcon.SHIELD -> {
            if (icon == GIcon.SHIELD) {
                val p = Path().apply { moveTo(w * 0.5f, h * 0.08f); lineTo(w * 0.85f, h * 0.22f); cubicTo(w * 0.85f, h * 0.6f, w * 0.7f, h * 0.82f, w * 0.5f, h * 0.94f); cubicTo(w * 0.3f, h * 0.82f, w * 0.15f, h * 0.6f, w * 0.15f, h * 0.22f); close() }
                fill(p, BV.Mint)
                drawLine(c, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.46f, h * 0.62f), w * 0.08f, StrokeCap.Round); drawLine(c, Offset(w * 0.46f, h * 0.62f), Offset(w * 0.68f, h * 0.38f), w * 0.08f, StrokeCap.Round)
            } else {
                drawRoundRect(c, Offset(w * 0.18f, h * 0.08f), Size(w * 0.64f, h * 0.84f), CornerRadius(w * 0.08f))
                drawRoundRect(tint ?: Color.White, Offset(w * 0.24f, h * 0.14f), Size(w * 0.52f, h * 0.72f), CornerRadius(w * 0.05f))
                for (k in 0..3) drawLine(c, Offset(w * 0.32f, h * (0.3f + k * 0.13f)), Offset(w * 0.68f, h * (0.3f + k * 0.13f)), w * 0.05f, StrokeCap.Round)
            }
        }
        GIcon.STAR -> {
            val p = Path()
            for (k in 0 until 10) {
                val a = -Math.PI / 2 + k * Math.PI / 5
                val rr = if (k % 2 == 0) w * 0.46f else w * 0.2f
                val x = w / 2 + (kotlin.math.cos(a) * rr).toFloat(); val y = h / 2 + (kotlin.math.sin(a) * rr).toFloat()
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close(); fill(p, BV.Gold)
        }
        GIcon.RETRY -> {
            drawArc(c, 40f, 280f, false, Offset(w * 0.18f, h * 0.18f), Size(w * 0.64f, h * 0.64f), style = Stroke(w * 0.13f, cap = StrokeCap.Round))
            val p = Path().apply { moveTo(w * 0.62f, h * 0.06f); lineTo(w * 0.92f, h * 0.28f); lineTo(w * 0.58f, h * 0.38f); close() }
            drawPath(p, c)
        }
        GIcon.AD -> {
            drawRoundRect(c, Offset(w * 0.06f, h * 0.18f), Size(w * 0.88f, h * 0.64f), CornerRadius(w * 0.1f))
            drawRoundRect(tint ?: BV.Mustard, Offset(w * 0.12f, h * 0.24f), Size(w * 0.76f, h * 0.52f), CornerRadius(w * 0.07f))
            val p = Path().apply { moveTo(w * 0.42f, h * 0.34f); lineTo(w * 0.64f, h * 0.5f); lineTo(w * 0.42f, h * 0.66f); close() }
            drawPath(p, c)
        }
        GIcon.BOLT -> { val p = Path().apply { moveTo(w * 0.6f, h * 0.05f); lineTo(w * 0.2f, h * 0.55f); lineTo(w * 0.48f, h * 0.55f); lineTo(w * 0.38f, h * 0.95f); lineTo(w * 0.82f, h * 0.4f); lineTo(w * 0.54f, h * 0.4f); close() }; fill(p, BV.Mustard) }
        GIcon.PAW -> {
            drawCircle(c, w * 0.22f, Offset(w * 0.5f, h * 0.64f)); drawCircle(tint ?: BV.Pink, w * 0.17f, Offset(w * 0.5f, h * 0.64f))
            for ((x, y) in listOf(0.22f to 0.38f, 0.4f to 0.22f, 0.6f to 0.22f, 0.78f to 0.38f)) { drawCircle(c, w * 0.1f, Offset(w * x, h * y)); drawCircle(tint ?: BV.Pink, w * 0.065f, Offset(w * x, h * y)) }
        }
        GIcon.COMPASS -> {
            // the Voyage Book: a compass rose
            drawCircle(c, w * 0.46f, center); drawCircle(tint ?: BV.Cream, w * 0.38f, center)
            for (k in 0 until 8) {
                val a = k * Math.PI / 4
                val r0 = w * 0.3f; val r1 = w * 0.36f
                drawLine(c, Offset(w / 2 + (kotlin.math.cos(a) * r0).toFloat(), h / 2 + (kotlin.math.sin(a) * r0).toFloat()),
                    Offset(w / 2 + (kotlin.math.cos(a) * r1).toFloat(), h / 2 + (kotlin.math.sin(a) * r1).toFloat()), w * 0.04f, StrokeCap.Round)
            }
            val north = Path().apply { moveTo(w * 0.5f, h * 0.16f); lineTo(w * 0.62f, h * 0.5f); lineTo(w * 0.38f, h * 0.5f); close() }
            val south = Path().apply { moveTo(w * 0.5f, h * 0.84f); lineTo(w * 0.62f, h * 0.5f); lineTo(w * 0.38f, h * 0.5f); close() }
            drawPath(north, BV.Tomato); drawPath(south, BV.Teal)
            drawPath(north, c, style = Stroke(w * 0.04f)); drawPath(south, c, style = Stroke(w * 0.04f))
            drawCircle(c, w * 0.06f, center)
        }
        GIcon.CLOCK -> {
            drawCircle(c, w * 0.44f, center); drawCircle(tint ?: Color.White, w * 0.36f, center)
            drawLine(c, center, Offset(w * 0.5f, h * 0.26f), w * 0.07f, StrokeCap.Round); drawLine(c, center, Offset(w * 0.68f, h * 0.56f), w * 0.07f, StrokeCap.Round)
        }
    }
}
