package com.ngstudio.burgerready.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import androidx.core.content.res.ResourcesCompat
import com.ngstudio.burgerready.R

/**
 * A single 1024x1024 texture holding every 2D element drawn in the 3D world:
 * glyphs (Lilita One with a cocoa outline), particle shapes, bubbles, rings, arrows, emotes,
 * and a block of product icons that [IconBaker] renders from the 3D models.
 */
class SpriteAtlas(private val context: Context) {
    companion object {
        const val SIZE = 1024
        const val GLYPHS = "0123456789\$+-x.,kMBT!?%/:ABCDEFGHIJKLMNOPQRSTUVWXYZ "
        const val ICON_CELL = 112
        const val ICON_ORIGIN_Y = 640

        /** The drawn atlas, kept for the process: every restaurant visit has a new GL context but the same pixels. */
        private class Drawn(
            val bmp: Bitmap, val regions: Map<String, FloatArray>, val advance: Map<Char, Float>,
            val uv: Map<Char, FloatArray>, val aspect: Map<Char, Float>, val icons: Map<String, IntArray>,
        )
        @Volatile private var drawn: Drawn? = null
    }

    var texture = 0; private set
    private val regions = HashMap<String, FloatArray>()
    private val glyphAdvance = HashMap<Char, Float>()
    private val glyphUv = HashMap<Char, FloatArray>()
    private val glyphAspect = HashMap<Char, Float>()
    val iconSlots = HashMap<String, IntArray>() // name -> pixel x,y,w,h

    fun uv(name: String): FloatArray = regions[name] ?: regions["dot"]!!
    fun has(name: String) = regions.containsKey(name)
    fun glyph(c: Char): FloatArray? = glyphUv[c]
    fun glyphAspect(c: Char): Float = glyphAspect[c] ?: 0.6f

    private fun reg(name: String, x: Int, y: Int, w: Int, h: Int) {
        // small inset to avoid bleeding
        regions[name] = floatArrayOf((x + 0.5f) / SIZE, (y + h - 0.5f) / SIZE, (x + w - 0.5f) / SIZE, (y + 0.5f) / SIZE)
    }

    fun build() {
        val cached = drawn
        val bmp: Bitmap
        if (cached != null && !cached.bmp.isRecycled) {
            bmp = cached.bmp
            regions.putAll(cached.regions); glyphAdvance.putAll(cached.advance); glyphUv.putAll(cached.uv)
            glyphAspect.putAll(cached.aspect); iconSlots.putAll(cached.icons)
        } else {
            bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            drawGlyphs(c)
            drawShapes(c)
            drawn = Drawn(bmp, HashMap(regions), HashMap(glyphAdvance), HashMap(glyphUv), HashMap(glyphAspect), HashMap(iconSlots))
        }
        texture = GlUtil.genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        // Premultiply happens in GLUtils for ARGB_8888 bitmaps.
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    }

    fun regenerateMipmaps() {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    }

    // ----------------------------------------------------------------------------------- glyphs
    private fun drawGlyphs(c: Canvas) {
        val tf = ResourcesCompat.getFont(context, R.font.lilita_one) ?: Typeface.DEFAULT_BOLD
        val size = 58f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; color = Color.WHITE }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = size; color = Color.rgb(59, 42, 58)
            style = Paint.Style.STROKE; strokeWidth = 10f; strokeJoin = Paint.Join.ROUND
        }
        val cellH = 80
        var x = 4; var y = 4
        for (ch in GLYPHS) {
            val s = ch.toString()
            val w = (fill.measureText(s) + 14).toInt().coerceAtLeast(22)
            if (x + w > SIZE - 4) { x = 4; y += cellH + 4 }
            val baseY = y + cellH * 0.74f
            c.drawText(s, x + 7f, baseY, stroke)
            c.drawText(s, x + 7f, baseY, fill)
            glyphUv[ch] = floatArrayOf((x + 0.5f) / SIZE, (y + cellH - 0.5f) / SIZE, (x + w - 0.5f) / SIZE, (y + 0.5f) / SIZE)
            glyphAspect[ch] = w.toFloat() / cellH
            glyphAdvance[ch] = (fill.measureText(s) + 3f) / cellH
            x += w + 4
        }
    }

    fun advance(ch: Char): Float = glyphAdvance[ch] ?: 0.5f

    // ----------------------------------------------------------------------------------- shapes
    private fun drawShapes(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        var ox = 0; var oy = 340 // shapes area starts below glyph rows
        fun cell(name: String, w: Int = 96, h: Int = 96, draw: (RectF) -> Unit) {
            if (ox + w > SIZE) { ox = 0; oy += 100 }
            val r = RectF(ox + 2f, oy + 2f, ox + w - 2f, oy + h - 2f)
            draw(r)
            reg(name, ox, oy, w, h)
            ox += w
        }
        // solid dot & soft dot
        cell("dot") { r -> p.reset(); p.isAntiAlias = true; p.color = Color.WHITE; c.drawOval(r, p) }
        cell("soft") { r ->
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(r.centerX(), r.centerY(), r.width() / 2, intArrayOf(Color.WHITE, Color.argb(160, 255, 255, 255), Color.TRANSPARENT), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawOval(r, p); p.shader = null
        }
        cell("shadow") { r ->
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(r.centerX(), r.centerY(), r.width() / 2, intArrayOf(Color.argb(200, 0, 0, 0), Color.argb(120, 0, 0, 0), Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawOval(r, p); p.shader = null
        }
        cell("ring") { r ->
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = 10f; p.color = Color.WHITE
            r.inset(6f, 6f); c.drawOval(r, p)
        }
        cell("ring_thick") { r ->
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = 18f; p.color = Color.WHITE
            r.inset(10f, 10f); c.drawOval(r, p)
        }
        cell("ring_dash") { r ->
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = 9f; p.color = Color.WHITE
            p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
            p.strokeCap = Paint.Cap.ROUND
            r.inset(6f, 6f); c.drawOval(r, p); p.pathEffect = null
        }
        cell("star") { r -> p.reset(); p.isAntiAlias = true; p.color = Color.WHITE; c.drawPath(starPath(r, 5, 0.46f), p) }
        cell("sparkle") { r -> p.reset(); p.isAntiAlias = true; p.color = Color.WHITE; c.drawPath(starPath(r, 4, 0.22f), p) }
        cell("confetti", 48, 96) { r -> p.reset(); p.isAntiAlias = true; p.color = Color.WHITE; c.drawRoundRect(RectF(r.left + 8, r.top + 14, r.right - 8, r.bottom - 14), 6f, 6f, p) }
        cell("smoke") { r ->
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(r.centerX(), r.centerY(), r.width() / 2, intArrayOf(Color.WHITE, Color.argb(200, 255, 255, 255), Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(r.centerX() - 10, r.centerY() + 4, r.width() * 0.32f, p)
            c.drawCircle(r.centerX() + 14, r.centerY() + 8, r.width() * 0.28f, p)
            c.drawCircle(r.centerX() + 2, r.centerY() - 12, r.width() * 0.3f, p)
            p.shader = null
        }
        cell("heart") { r -> p.reset(); p.isAntiAlias = true; p.color = Color.WHITE; c.drawPath(heartPath(r), p) }
        cell("arrow") { r ->
            // chevron pointing up (−Z on the ground)
            p.reset(); p.isAntiAlias = true; p.color = Color.WHITE
            val path = Path()
            path.moveTo(r.centerX(), r.top + 8); path.lineTo(r.right - 8, r.centerY() + 6); path.lineTo(r.right - 26, r.centerY() + 22)
            path.lineTo(r.centerX(), r.top + 40); path.lineTo(r.left + 26, r.centerY() + 22); path.lineTo(r.left + 8, r.centerY() + 6); path.close()
            c.drawPath(path, p)
            path.reset()
            path.moveTo(r.centerX(), r.centerY() - 2); path.lineTo(r.right - 8, r.bottom - 22); path.lineTo(r.right - 26, r.bottom - 6)
            path.lineTo(r.centerX(), r.centerY() + 30); path.lineTo(r.left + 26, r.bottom - 6); path.lineTo(r.left + 8, r.bottom - 22); path.close()
            c.drawPath(path, p)
        }
        cell("pointer") { r ->
            // bouncing down-pointing marker (teardrop)
            p.reset(); p.isAntiAlias = true
            val path = Path()
            path.moveTo(r.centerX(), r.bottom - 4)
            path.cubicTo(r.left - 6, r.centerY() - 8, r.left + 10, r.top + 4, r.centerX(), r.top + 4)
            path.cubicTo(r.right - 10, r.top + 4, r.right + 6, r.centerY() - 8, r.centerX(), r.bottom - 4)
            p.color = Color.rgb(59, 42, 58); c.drawPath(path, p)
            val inner = RectF(r); inner.inset(9f, 9f)
            val path2 = Path()
            path2.moveTo(inner.centerX(), inner.bottom - 6)
            path2.cubicTo(inner.left - 4, inner.centerY() - 8, inner.left + 8, inner.top + 2, inner.centerX(), inner.top + 2)
            path2.cubicTo(inner.right - 8, inner.top + 2, inner.right + 4, inner.centerY() - 8, inner.centerX(), inner.bottom - 6)
            p.color = Color.WHITE; c.drawPath(path2, p)
        }
        cell("bubble", 128, 112) { r ->
            // speech bubble: rounded rect + tail, cocoa outline, white fill
            val body = RectF(r.left + 4, r.top + 4, r.right - 4, r.bottom - 26)
            val path = Path()
            path.addRoundRect(body, 30f, 30f, Path.Direction.CW)
            val tail = Path()
            tail.moveTo(r.centerX() - 16, body.bottom - 2); tail.lineTo(r.centerX(), r.bottom - 6); tail.lineTo(r.centerX() + 16, body.bottom - 2); tail.close()
            path.op(tail, Path.Op.UNION)
            p.reset(); p.isAntiAlias = true; p.color = Color.rgb(59, 42, 58); p.style = Paint.Style.STROKE; p.strokeWidth = 7f
            c.drawPath(path, p)
            p.style = Paint.Style.FILL; p.color = Color.WHITE; c.drawPath(path, p)
        }
        cell("pill", 160, 72) { r ->
            p.reset(); p.isAntiAlias = true; p.color = Color.rgb(59, 42, 58)
            c.drawRoundRect(r, 34f, 34f, p)
            val i = RectF(r); i.inset(6f, 6f)
            p.color = Color.WHITE; c.drawRoundRect(i, 28f, 28f, p)
        }
        cell("bar_bg", 160, 40) { r -> p.reset(); p.isAntiAlias = true; p.color = Color.rgb(59, 42, 58); c.drawRoundRect(r, 18f, 18f, p) }
        cell("bar_fill", 160, 40) { r -> p.reset(); p.isAntiAlias = true; p.color = Color.WHITE; r.inset(5f, 5f); c.drawRoundRect(r, 14f, 14f, p) }
        cell("badge") { r ->
            p.reset(); p.isAntiAlias = true; p.color = Color.rgb(59, 42, 58); c.drawOval(r, p)
            val i = RectF(r); i.inset(7f, 7f); p.color = Color.WHITE; c.drawOval(i, p)
        }
        cell("square") { r -> p.reset(); p.color = Color.WHITE; c.drawRect(r, p) }
        cell("light_pool") { r ->
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(r.centerX(), r.centerY(), r.width() / 2, intArrayOf(Color.argb(255, 255, 255, 255), Color.argb(90, 255, 255, 255), Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawOval(r, p); p.shader = null
        }
        cell("drop") { r ->
            p.reset(); p.isAntiAlias = true; p.color = Color.WHITE
            val path = Path(); path.moveTo(r.centerX(), r.top + 6)
            path.cubicTo(r.right - 6, r.centerY() + 4, r.right - 14, r.bottom - 6, r.centerX(), r.bottom - 6)
            path.cubicTo(r.left + 14, r.bottom - 6, r.left + 6, r.centerY() + 4, r.centerX(), r.top + 6)
            c.drawPath(path, p)
        }
        cell("splat") { r ->
            p.reset(); p.isAntiAlias = true; p.color = Color.WHITE
            c.drawCircle(r.centerX(), r.centerY(), r.width() * 0.3f, p)
            val rnd = java.util.Random(7)
            for (i in 0 until 9) {
                val a = i / 9f * 6.283f + rnd.nextFloat() * 0.4f
                val d = r.width() * (0.3f + rnd.nextFloat() * 0.15f)
                c.drawCircle(r.centerX() + kotlin.math.cos(a) * d, r.centerY() + kotlin.math.sin(a) * d, r.width() * (0.06f + rnd.nextFloat() * 0.06f), p)
            }
        }
        // Emotes (white face disc with cocoa features) ------------------------------------
        cell("emo_happy") { r -> face(c, r, 0) }
        cell("emo_angry") { r -> face(c, r, 1) }
        cell("emo_love") { r -> face(c, r, 2) }
        cell("emo_sleep") { r -> face(c, r, 3) }
        cell("emo_wow") { r -> face(c, r, 4) }
        cell("fire") { r ->
            p.reset(); p.isAntiAlias = true
            val path = Path()
            path.moveTo(r.centerX(), r.top + 4)
            path.cubicTo(r.right + 4, r.centerY(), r.right - 6, r.bottom - 4, r.centerX(), r.bottom - 4)
            path.cubicTo(r.left + 6, r.bottom - 4, r.left - 4, r.centerY(), r.centerX(), r.top + 4)
            p.shader = LinearGradient(0f, r.top, 0f, r.bottom, Color.rgb(255, 230, 90), Color.rgb(255, 90, 40), Shader.TileMode.CLAMP)
            c.drawPath(path, p); p.shader = null
        }
        cell("bolt") { r ->
            p.reset(); p.isAntiAlias = true; p.color = Color.WHITE
            val path = Path()
            path.moveTo(r.left + r.width() * 0.58f, r.top + 6); path.lineTo(r.left + r.width() * 0.22f, r.centerY() + 6)
            path.lineTo(r.left + r.width() * 0.48f, r.centerY() + 6); path.lineTo(r.left + r.width() * 0.4f, r.bottom - 6)
            path.lineTo(r.left + r.width() * 0.8f, r.centerY() - 8); path.lineTo(r.left + r.width() * 0.54f, r.centerY() - 8); path.close()
            c.drawPath(path, p)
        }
        cell("crown") { r ->
            p.reset(); p.isAntiAlias = true; p.color = Color.WHITE
            val path = Path()
            path.moveTo(r.left + 8, r.bottom - 20); path.lineTo(r.left + 12, r.top + 26); path.lineTo(r.left + r.width() * 0.32f, r.centerY())
            path.lineTo(r.centerX(), r.top + 12); path.lineTo(r.right - r.width() * 0.32f, r.centerY()); path.lineTo(r.right - 12, r.top + 26)
            path.lineTo(r.right - 8, r.bottom - 20); path.close()
            c.drawPath(path, p)
        }
        cell("exclaim") { r ->
            p.reset(); p.isAntiAlias = true; p.color = Color.rgb(242, 84, 45); c.drawOval(r, p)
            p.color = Color.WHITE
            c.drawRoundRect(RectF(r.centerX() - 7, r.top + 16, r.centerX() + 7, r.bottom - 34), 7f, 7f, p)
            c.drawCircle(r.centerX(), r.bottom - 22, 8f, p)
        }
        cell("wrench") { r ->
            p.reset(); p.isAntiAlias = true; p.color = Color.WHITE; p.strokeWidth = 14f; p.strokeCap = Paint.Cap.ROUND
            c.drawLine(r.left + 24, r.bottom - 24, r.right - 30, r.top + 30, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 12f
            c.drawCircle(r.right - 26, r.top + 26, 14f, p)
        }
        cell("zz") { r ->
            p.reset(); p.isAntiAlias = true; p.color = Color.WHITE
            p.typeface = Typeface.DEFAULT_BOLD; p.textSize = 54f
            c.drawText("z", r.left + 14, r.bottom - 20, p); p.textSize = 38f; c.drawText("z", r.left + 50, r.top + 44, p)
        }
        // Reserve the icon block (filled by IconBaker via render-to-texture).
        val names = listOf("burger", "cheese", "double", "veggie", "fries", "soda", "shake", "bag", "tray", "coin", "gem", "patty_raw")
        var ix = 0; var iy = ICON_ORIGIN_Y
        for (n in names) {
            if (ix + ICON_CELL > SIZE) { ix = 0; iy += ICON_CELL }
            iconSlots[n] = intArrayOf(ix, iy, ICON_CELL, ICON_CELL)
            // Icons are rendered with GL (row 0 at the bottom of the image), so their V is flipped.
            regions["icon_$n"] = floatArrayOf((ix + 0.5f) / SIZE, (iy + 0.5f) / SIZE, (ix + ICON_CELL - 0.5f) / SIZE, (iy + ICON_CELL - 0.5f) / SIZE)
            ix += ICON_CELL
        }
    }

    private fun face(c: Canvas, r: RectF, kind: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cocoa = Color.rgb(59, 42, 58)
        p.color = cocoa; c.drawOval(r, p)
        val i = RectF(r); i.inset(6f, 6f)
        p.color = when (kind) { 1 -> Color.rgb(255, 120, 90); 2 -> Color.rgb(255, 150, 190); 3 -> Color.rgb(170, 200, 255); else -> Color.rgb(255, 214, 80) }
        c.drawOval(i, p)
        p.color = cocoa
        val cx = r.centerX(); val cy = r.centerY()
        when (kind) {
            0 -> { // happy
                c.drawCircle(cx - 14, cy - 8, 6f, p); c.drawCircle(cx + 14, cy - 8, 6f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.strokeCap = Paint.Cap.ROUND
                c.drawArc(RectF(cx - 18, cy - 6, cx + 18, cy + 20), 20f, 140f, false, p)
            }
            1 -> { // angry
                p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.strokeCap = Paint.Cap.ROUND
                c.drawLine(cx - 22, cy - 18, cx - 8, cy - 10, p); c.drawLine(cx + 22, cy - 18, cx + 8, cy - 10, p)
                c.drawArc(RectF(cx - 16, cy + 6, cx + 16, cy + 26), 200f, 140f, false, p)
                p.style = Paint.Style.FILL; c.drawCircle(cx - 13, cy - 3, 5f, p); c.drawCircle(cx + 13, cy - 3, 5f, p)
            }
            2 -> { // love
                p.color = Color.rgb(233, 40, 80)
                c.drawPath(heartPath(RectF(cx - 26, cy - 22, cx - 4, cy)), p)
                c.drawPath(heartPath(RectF(cx + 4, cy - 22, cx + 26, cy)), p)
                p.color = cocoa; p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.strokeCap = Paint.Cap.ROUND
                c.drawArc(RectF(cx - 16, cy - 2, cx + 16, cy + 22), 20f, 140f, false, p)
            }
            3 -> { // sleepy
                p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.strokeCap = Paint.Cap.ROUND
                c.drawLine(cx - 22, cy - 6, cx - 8, cy - 6, p); c.drawLine(cx + 8, cy - 6, cx + 22, cy - 6, p)
                c.drawCircle(cx, cy + 14, 6f, p)
            }
            else -> { // wow
                c.drawCircle(cx - 14, cy - 8, 7f, p); c.drawCircle(cx + 14, cy - 8, 7f, p)
                c.drawOval(RectF(cx - 9, cy + 4, cx + 9, cy + 24), p)
            }
        }
    }

    private fun starPath(r: RectF, points: Int, inner: Float): Path {
        val path = Path()
        val cx = r.centerX(); val cy = r.centerY(); val R = r.width() / 2
        for (i in 0 until points * 2) {
            val a = -Math.PI / 2 + i * Math.PI / points
            val rad = if (i % 2 == 0) R else R * inner
            val x = (cx + kotlin.math.cos(a) * rad).toFloat(); val y = (cy + kotlin.math.sin(a) * rad).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); return path
    }

    private fun heartPath(r: RectF): Path {
        val path = Path()
        val w = r.width(); val h = r.height()
        path.moveTo(r.centerX(), r.bottom - h * 0.08f)
        path.cubicTo(r.left - w * 0.1f, r.top + h * 0.55f, r.left + w * 0.1f, r.top - h * 0.05f, r.centerX(), r.top + h * 0.28f)
        path.cubicTo(r.right - w * 0.1f, r.top - h * 0.05f, r.right + w * 0.1f, r.top + h * 0.55f, r.centerX(), r.bottom - h * 0.08f)
        path.close(); return path
    }
}
