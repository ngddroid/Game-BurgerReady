package com.ngstudio.burgerready.engine

import kotlin.math.cos
import kotlin.math.sin

/** CPU particle system drawn as instanced sprites. Fixed pool, zero allocation per frame. */
class Particles(private val atlas: SpriteAtlas, max: Int = 900) {
    private val px = FloatArray(max); private val py = FloatArray(max); private val pz = FloatArray(max)
    private val vx = FloatArray(max); private val vy = FloatArray(max); private val vz = FloatArray(max)
    private val life = FloatArray(max); private val maxLife = FloatArray(max)
    private val size0 = FloatArray(max); private val size1 = FloatArray(max)
    private val rot = FloatArray(max); private val spin = FloatArray(max)
    private val grav = FloatArray(max); private val drag = FloatArray(max)
    private val cr = FloatArray(max); private val cg = FloatArray(max); private val cb = FloatArray(max)
    private val a0 = FloatArray(max)
    private val mode = IntArray(max)
    private val uv = arrayOfNulls<FloatArray>(max)
    private var count = 0
    private val cap = max
    private var seed = 12345

    fun rnd(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 65535f }
    fun rr(a: Float, b: Float) = a + (b - a) * rnd()

    fun emit(
        x: Float, y: Float, z: Float, vx0: Float, vy0: Float, vz0: Float, lifeS: Float, sizeA: Float, sizeB: Float,
        sprite: String, r: Float, g: Float, b: Float, alpha: Float = 1f, gravity: Float = 0f, dragK: Float = 0f,
        spinRate: Float = 0f, ground: Boolean = false,
    ) {
        if (count >= cap) return
        val i = count++
        px[i] = x; py[i] = y; pz[i] = z; vx[i] = vx0; vy[i] = vy0; vz[i] = vz0
        life[i] = lifeS; maxLife[i] = lifeS; size0[i] = sizeA; size1[i] = sizeB
        rot[i] = rnd() * 6.28f; spin[i] = spinRate; grav[i] = gravity; drag[i] = dragK
        cr[i] = r; cg[i] = g; cb[i] = b; a0[i] = alpha
        mode[i] = if (ground) 1 else 0
        uv[i] = atlas.uv(sprite)
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = --count
                if (i != last) {
                    px[i] = px[last]; py[i] = py[last]; pz[i] = pz[last]; vx[i] = vx[last]; vy[i] = vy[last]; vz[i] = vz[last]
                    life[i] = life[last]; maxLife[i] = maxLife[last]; size0[i] = size0[last]; size1[i] = size1[last]
                    rot[i] = rot[last]; spin[i] = spin[last]; grav[i] = grav[last]; drag[i] = drag[last]
                    cr[i] = cr[last]; cg[i] = cg[last]; cb[i] = cb[last]; a0[i] = a0[last]; mode[i] = mode[last]; uv[i] = uv[last]
                }
                continue
            }
            vy[i] -= grav[i] * dt
            val dk = 1f - drag[i] * dt
            vx[i] *= dk; vy[i] *= dk; vz[i] *= dk
            px[i] += vx[i] * dt; py[i] += vy[i] * dt; pz[i] += vz[i] * dt
            if (py[i] < 0.02f && grav[i] > 0f) { py[i] = 0.02f; vy[i] = -vy[i] * 0.35f; vx[i] *= 0.6f; vz[i] *= 0.6f }
            rot[i] += spin[i] * dt
            i++
        }
    }

    fun draw(r: Renderer) {
        for (i in 0 until count) {
            val t = 1f - life[i] / maxLife[i]
            val s = size0[i] + (size1[i] - size0[i]) * t
            val fade = if (t > 0.7f) (1f - t) / 0.3f else if (t < 0.08f) t / 0.08f else 1f
            r.sprite(px[i], py[i], pz[i], s, s, uv[i]!!, cr[i], cg[i], cb[i], a0[i] * fade, rot[i], mode[i])
        }
    }

    fun clear() { count = 0 }

    // --------------------------------------------------------------------------------- presets
    fun confetti(x: Float, y: Float, z: Float, n: Int = 36) {
        val cols = arrayOf(floatArrayOf(1f, 0.76f, 0.24f), floatArrayOf(0.95f, 0.33f, 0.18f), floatArrayOf(0.24f, 0.86f, 0.59f),
            floatArrayOf(0.12f, 0.65f, 0.72f), floatArrayOf(1f, 0.56f, 0.69f), floatArrayOf(0.72f, 0.61f, 1f))
        for (k in 0 until n) {
            val c = cols[k % cols.size]
            val a = rnd() * 6.283f; val sp = rr(1.5f, 4.2f)
            emit(x, y, z, cos(a) * sp, rr(3.5f, 6.5f), sin(a) * sp, rr(1.1f, 1.8f), 0.16f, 0.14f, "confetti", c[0], c[1], c[2], 1f, 7.5f, 1.2f, rr(-9f, 9f))
        }
    }

    fun sparkles(x: Float, y: Float, z: Float, n: Int = 10, r: Float = 1f, g: Float = 0.95f, b: Float = 0.6f, spread: Float = 0.6f) {
        for (k in 0 until n) {
            emit(x + rr(-spread, spread), y + rr(-0.2f, 0.4f), z + rr(-spread, spread), 0f, rr(0.4f, 1.2f), 0f,
                rr(0.4f, 0.8f), rr(0.12f, 0.22f), 0f, "sparkle", r, g, b, 1f, 0f, 0f, rr(-4f, 4f))
        }
    }

    fun puff(x: Float, y: Float, z: Float, n: Int = 12, radius: Float = 0.8f, r: Float = 1f, g: Float = 1f, b: Float = 1f) {
        for (k in 0 until n) {
            val a = k / n.toFloat() * 6.283f
            emit(x, y, z, cos(a) * radius * 2.2f, rr(0.2f, 0.8f), sin(a) * radius * 2.2f, rr(0.45f, 0.7f), 0.35f, 0.75f, "smoke", r, g, b, 0.9f, 0f, 4f)
        }
    }

    fun steam(x: Float, y: Float, z: Float) {
        emit(x + rr(-0.25f, 0.25f), y, z + rr(-0.15f, 0.15f), rr(-0.1f, 0.1f), rr(0.5f, 0.9f), 0f, rr(1.0f, 1.6f), 0.18f, 0.55f, "smoke", 1f, 1f, 1f, 0.45f, -0.1f, 0.6f)
    }

    fun flame(x: Float, y: Float, z: Float) {
        emit(x + rr(-0.3f, 0.3f), y, z + rr(-0.2f, 0.2f), 0f, rr(1.0f, 1.8f), 0f, rr(0.35f, 0.6f), 0.4f, 0.1f, "fire", 1f, 1f, 1f, 1f, 0f, 0f, rr(-2f, 2f))
        if (rnd() < 0.3f) emit(x, y + 0.6f, z, 0f, rr(0.8f, 1.2f), 0f, 1.0f, 0.3f, 0.8f, "smoke", 0.35f, 0.3f, 0.32f, 0.5f, -0.2f, 0.5f)
    }

    fun ring(x: Float, z: Float, r: Float, g: Float, b: Float, size: Float = 2.2f) {
        emit(x, 0.06f, z, 0f, 0f, 0f, 0.5f, 0.4f, size, "ring_thick", r, g, b, 0.9f, ground = true)
    }

    fun dust(x: Float, z: Float) {
        emit(x + rr(-0.1f, 0.1f), 0.08f, z + rr(-0.1f, 0.1f), rr(-0.3f, 0.3f), rr(0.2f, 0.5f), rr(-0.3f, 0.3f), 0.45f, 0.12f, 0.32f, "smoke", 1f, 0.95f, 0.9f, 0.55f, 0f, 2f)
    }
}

/** Short-lived floating text in the world (e.g. "+$25", "MAX", "x2"). */
class FloatTexts(private val atlas: SpriteAtlas) {
    private class FT(var text: String, var x: Float, var y: Float, var z: Float, var t: Float, var life: Float, var size: Float, var r: Float, var g: Float, var b: Float)
    private val items = ArrayList<FT>()
    private val pool = ArrayList<FT>()

    fun add(text: String, x: Float, y: Float, z: Float, size: Float = 0.42f, r: Float = 1f, g: Float = 1f, b: Float = 1f, life: Float = 1.1f) {
        val ft = if (pool.isNotEmpty()) pool.removeAt(pool.size - 1) else FT("", 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        ft.text = text; ft.x = x; ft.y = y; ft.z = z; ft.t = 0f; ft.life = life; ft.size = size; ft.r = r; ft.g = g; ft.b = b
        items.add(ft)
    }

    fun update(dt: Float) {
        var i = 0
        while (i < items.size) {
            val f = items[i]
            f.t += dt; f.y += dt * 1.1f
            if (f.t >= f.life) { pool.add(items.removeAt(i)); continue }
            i++
        }
    }

    fun draw(r: Renderer, cam: Camera) {
        for (f in items) {
            val k = f.t / f.life
            val pop = if (k < 0.15f) 0.6f + k / 0.15f * 0.5f else if (k < 0.25f) 1.1f - (k - 0.15f) else 1f
            val a = if (k > 0.75f) (1f - k) / 0.25f else 1f
            TextDraw.draw(r, atlas, cam, f.text, f.x, f.y, f.z, f.size * pop, f.r, f.g, f.b, a)
        }
    }
}

/** Draws a centred string of atlas glyphs as camera-facing sprites. */
object TextDraw {
    fun width(atlas: SpriteAtlas, text: String, size: Float): Float {
        var w = 0f
        for (c in text) w += atlas.advance(c) * size
        return w
    }

    fun draw(r: Renderer, atlas: SpriteAtlas, cam: Camera, text: String, x: Float, y: Float, z: Float, size: Float,
             cr: Float = 1f, cg: Float = 1f, cb: Float = 1f, a: Float = 1f) {
        val total = width(atlas, text, size)
        var off = -total / 2f
        for (c in text) {
            val uv = atlas.glyph(c) ?: continue
            val adv = atlas.advance(c) * size
            val gw = atlas.glyphAspect(c) * size
            val cx = off + adv / 2f
            r.sprite(x + cam.rightX * cx, y + cam.rightY * cx, z + cam.rightZ * cx, gw, size, uv, cr, cg, cb, a)
            off += adv
        }
    }
}
