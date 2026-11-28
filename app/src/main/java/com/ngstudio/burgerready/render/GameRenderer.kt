package com.ngstudio.burgerready.render

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import com.ngstudio.burgerready.engine.Camera
import com.ngstudio.burgerready.engine.FloatTexts
import com.ngstudio.burgerready.engine.IconBaker
import com.ngstudio.burgerready.engine.ModelCache
import com.ngstudio.burgerready.engine.Particles
import com.ngstudio.burgerready.engine.Quality
import com.ngstudio.burgerready.engine.Renderer
import com.ngstudio.burgerready.engine.SpriteAtlas
import com.ngstudio.burgerready.game.CState
import com.ngstudio.burgerready.game.Ev
import com.ngstudio.burgerready.game.Item
import com.ngstudio.burgerready.game.PowerUpKind
import com.ngstudio.burgerready.game.Story
import com.ngstudio.burgerready.game.Weather
import com.ngstudio.burgerready.game.World
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min

class GameRenderer(private val context: Context, quality: Quality) : GLSurfaceView.Renderer {
    @Volatile var session: GameSession? = null
    @Volatile var requestedQuality: Quality = quality
    /** Called once the GL resources are ready (first frame can render). */
    @Volatile var onReady: (() -> Unit)? = null
    /** Called (GL thread) after the first frame of a restaurant has been drawn: the UI lifts its entry curtain. */
    @Volatile var onFirstFrame: (() -> Unit)? = null

    private val renderer = Renderer(quality)
    private lateinit var models: ModelCache
    private lateinit var atlas: SpriteAtlas
    private lateinit var view: WorldView
    private val camera = Camera()
    private var lastNs = 0L
    private var acc = 0f
    private var fpsFrames = 0
    private var fpsT = 0f
    private var fps = 0
    private var hudT = 0f
    private var toast = ""
    private var toastId = 0
    private var coinFlyId = 0; private var coinFlyAmount = 0; private var coinFlyX = 0f; private var coinFlyY = 0f
    private val proj = FloatArray(2)
    private var width = 1; private var height = 1
    private var ready = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            val t0 = SystemClock.elapsedRealtime()
            renderer.init()
            val t1 = SystemClock.elapsedRealtime()
            models = ModelCache(context.assets)
            atlas = SpriteAtlas(context)
            atlas.build()
            renderer.spriteTexture = atlas.texture
            view = WorldView(models, atlas, Particles(atlas), FloatTexts(atlas))
            val t2 = SystemClock.elapsedRealtime()
            // Preload every model so the first seconds of play never hitch.
            context.assets.list("models")?.forEach { f -> if (f.endsWith(".bvm")) models.getOrNull(f.removeSuffix(".bvm")) }
            val t3 = SystemClock.elapsedRealtime()
            IconBaker.bake(renderer, atlas, models)
            val t4 = SystemClock.elapsedRealtime()
            Log.i("GameRenderer", "GL init ${t4 - t0} ms: shaders ${t1 - t0}, atlas ${t2 - t1}, models ${t3 - t2}, icons ${t4 - t3}")
            ready = true
            onReady?.invoke()
        } catch (e: Throwable) {
            Log.e("GameRenderer", "GL init failed", e)
        }
        lastNs = SystemClock.elapsedRealtimeNanos()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
        renderer.resize(w, h)
        camera.aspect = w.toFloat() / max(1, h)
        // Taller screens need a slightly larger distance to show the same width.
        // Wider framing on tall phones so the whole kitchen + counter reads at a glance.
        camera.fovY = 32f
        camera.pitchDeg = 52f
        baseDistance = if (camera.aspect < 0.5f) 22.5f else 20f
        camera.distance = baseDistance
    }

    private var baseDistance = 22.5f

    /**
     * Keeps the view on the restaurant: the target may not drift past the back wall (it used to show ~40 % lawn
     * when the chef stood at the grills) or far beyond the side walls, and big layouts pull back a little so more
     * of the dining room fits (up to +14 % distance for the 15 m Burger Palace).
     */
    private fun frameRestaurant(w: World) {
        val lay = w.layout
        camera.distance = baseDistance * (1f + 0.02f * (lay.w - 8)).coerceIn(1f, 1.14f)
        val half = lay.w * 0.5f
        val maxX = if (lay.drive != null) lay.w - 1.4f else lay.w - 2.6f
        camera.setBounds(min(half, 2.6f), max(half, maxX), 3.4f, lay.d - 0.5f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.elapsedRealtimeNanos()
        var dt = (now - lastNs) / 1e9f
        lastNs = now
        if (dt > 0.1f) dt = 0.1f
        fpsFrames++; fpsT += dt
        if (fpsT >= 1f) { fps = fpsFrames; fpsFrames = 0; fpsT = 0f }
        if (!ready) { GLES30.glClearColor(1f, 0.95f, 0.86f, 1f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); return }
        if (renderer.quality != requestedQuality) { renderer.quality = requestedQuality; renderer.setupShadows() }
        val s = session
        if (s == null) {
            GLES30.glClearColor(1f, 0.95f, 0.86f, 1f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); return
        }
        val w = s.world
        frameRestaurant(w)
        if (!s.cameraSnapped) { camera.snapTo(w.player.x, w.player.z - 0.6f); s.cameraSnapped = true; view.particles.clear() }
        val simDt = if (s.paused) 0f else dt
        if (!s.paused) {
            // UI → world: purchases/rewards and upgraded stats
            val adj = s.cashAdjust.getAndSet(0L)
            if (adj != 0L) w.cash = (w.cash + adj).coerceAtLeast(0L)
            s.newStats?.let { w.stats = it; s.newStats = null }
            // joystick → world input
            camera.screenDirToWorld(s.joyX, s.joyY, joyOut)
            w.inputX = joyOut[0]; w.inputZ = joyOut[1]
            if (s.debugAction != 0 && com.ngstudio.burgerready.BuildConfig.DEBUG_TOOLS) { w.debugAction(s.debugAction); s.debugAction = 0 }
            acc += dt
            val step = 1f / 60f
            var n = 0
            while (acc >= step && n < 6) {
                w.update(step)
                for (e in w.events) handle(s, e)
                acc -= step; n++
            }
            if (n == 6) acc = 0f
        } else { w.inputX = 0f; w.inputZ = 0f }
        camera.follow(w.player.x, w.player.z - 0.6f, w.player.vx, w.player.vz)
        camera.update(dt)
        applyLighting(w, s.style)
        view.night = renderer.lighting.night
        if (!s.paused) weather(s.style.store.weather, w)
        view.particles.update(simDt)
        view.texts.update(simDt)
        renderer.begin(camera)
        view.draw(w, renderer, camera, s.style, simDt, true)
        view.carBubbles(renderer, w, camera)
        view.particles.draw(renderer)
        view.texts.draw(renderer, camera)
        renderer.end()
        if (!firstFrameDone) { firstFrameDone = true; onFirstFrame?.invoke() }
        hudT -= dt
        if (hudT <= 0f) { hudT = 1f / 15f; publishHud(s) }
        snapT -= dt
        if (snapT <= 0f || w.progressVersion != lastProgress) { snapT = 1f; lastProgress = w.progressVersion; s.snapshot = w.snapshot() }
    }

    private val joyOut = FloatArray(2)
    private var firstFrameDone = false
    private var snapT = 0f
    private var lastProgress = -1

    /**
     * Toon lighting: [Lighting.sunColor] is the lit-side multiplier, [Lighting.skyColor] the (coloured) shadow side,
     * [Lighting.groundColor] the rim. A slow day cycle adds a golden hour; only night-themed stores go dark.
     */
    private fun applyLighting(w: World, style: VisualStyle) {
        val L = renderer.lighting
        val store = style.store.id
        val cycle = (w.time % 540f) / 540f
        val golden = smooth(0.55f, 0.72f, cycle) * (1f - smooth(0.86f, 0.98f, cycle)) * 0.75f
        // Evening stores: dark blue outside, warm lamp light inside the restaurant walls (see TOON_FS uInterior).
        val night = style.store.night
        val skyC = style.store.sky
        sky[0] = ((skyC shr 16) and 255) / 255f; sky[1] = ((skyC shr 8) and 255) / 255f; sky[2] = (skyC and 255) / 255f
        L.sunDir[0] = -0.42f - golden * 0.22f; L.sunDir[1] = 0.82f - golden * 0.18f; L.sunDir[2] = 0.42f
        L.normalizeSun()
        mix3(L.sunColor, DAY_LIT, GOLD_LIT, golden)
        mix3(L.sunColor, L.sunColor, NIGHT_LIT, night)
        mix3(L.skyColor, DAY_SHADE, GOLD_SHADE, golden)
        mix3(L.skyColor, L.skyColor, NIGHT_SHADE, night)
        mix3(L.groundColor, RIM_DAY, RIM_NIGHT, night)
        mix3(L.fogColor, sky, NIGHT_FOG, night * 0.8f)
        System.arraycopy(L.fogColor, 0, L.clearColor, 0, 3)
        L.fogStart = 48f; L.fogEnd = 95f
        L.night = night
        L.saturation = 1.14f
        val lay = w.layout
        L.interior[0] = 0f; L.interior[1] = 0f; L.interior[2] = lay.w.toFloat(); L.interior[3] = lay.d.toFloat()
        L.interiorAmt = night
        System.arraycopy(INSIDE_LIT, 0, L.inLit, 0, 3)
        System.arraycopy(INSIDE_SHADE, 0, L.inShade, 0, 3)
    }

    private val sky = FloatArray(3)
    private val DAY_LIT = floatArrayOf(1.03f, 0.99f, 0.92f)
    private val GOLD_LIT = floatArrayOf(1.06f, 0.90f, 0.72f)
    private val NIGHT_LIT = floatArrayOf(0.42f, 0.47f, 0.76f)
    // Shade is a cool slate, not lilac: the old (0.68, 0.62, 0.72) turned white tiles and the sidewalk purple.
    private val DAY_SHADE = floatArrayOf(0.62f, 0.635f, 0.72f)
    private val GOLD_SHADE = floatArrayOf(0.60f, 0.58f, 0.68f)
    private val NIGHT_SHADE = floatArrayOf(0.21f, 0.23f, 0.41f)
    private val INSIDE_LIT = floatArrayOf(1.04f, 0.97f, 0.86f)
    private val INSIDE_SHADE = floatArrayOf(0.64f, 0.61f, 0.66f)
    private val RIM_DAY = floatArrayOf(1f, 0.92f, 0.8f)
    private val RIM_NIGHT = floatArrayOf(0.7f, 0.6f, 1f)
    private val NIGHT_FOG = floatArrayOf(0.16f, 0.17f, 0.32f)

    /** Light ambient weather per store, spawned around the camera target. */
    private fun weather(kind: Weather, w: World) {
        val fx = view.particles
        val cx = camera.targetX; val cz = camera.targetZ
        when (kind) {
            Weather.SNOW -> if (fx.rnd() < 0.55f) fx.emit(cx + fx.rr(-9f, 9f), 7f, cz + fx.rr(-9f, 7f), fx.rr(-0.3f, 0.3f), -1.1f, fx.rr(-0.2f, 0.2f), 6f, 0.12f, 0.1f, "soft", 1f, 1f, 1f, 0.9f)
            Weather.BUBBLES -> if (fx.rnd() < 0.25f) fx.emit(cx + fx.rr(-9f, 9f), 0.2f, cz + fx.rr(-9f, 7f), 0f, 1.2f, 0f, 4.5f, 0.12f, 0.28f, "ring", 0.85f, 0.95f, 1f, 0.7f)
            Weather.SPARKLE_GOLD, Weather.SPARKLE_PINK -> if (fx.rnd() < 0.15f) fx.emit(cx + fx.rr(-9f, 9f), fx.rr(1.5f, 4f), cz + fx.rr(-9f, 7f), 0f, 0.15f, 0f, 1.6f, 0.22f, 0f, "sparkle", 1f, if (kind == Weather.SPARKLE_PINK) 0.7f else 0.95f, if (kind == Weather.SPARKLE_PINK) 0.9f else 0.6f, 0.9f, spinRate = 3f)
            Weather.EMBERS -> if (fx.rnd() < 0.2f) fx.emit(cx + fx.rr(-9f, 9f), 0.3f, cz + fx.rr(-9f, 7f), fx.rr(-0.2f, 0.2f), fx.rr(0.8f, 1.6f), 0f, 2.2f, 0.1f, 0.02f, "dot", 1f, 0.55f, 0.2f, 1f)
            Weather.HEARTS -> if (fx.rnd() < 0.04f) fx.emit(cx + fx.rr(-9f, 9f), fx.rr(2f, 4f), cz + fx.rr(-9f, 7f), fx.rr(0.6f, 1.2f), 0f, 0f, 3f, 0.25f, 0.25f, "heart", 1f, 1f, 1f, 0.0f)
            // ---- World Tour weathers (same emitter, new motion and colours)
            Weather.MIST -> if (fx.rnd() < 0.10f) fx.emit(cx + fx.rr(-10f, 10f), fx.rr(0.2f, 1.2f), cz + fx.rr(-9f, 8f), fx.rr(0.2f, 0.5f), 0.02f, 0f, 5f, 0.9f, 0f, "soft", 1f, 1f, 1f, 0.22f)
            Weather.PETALS -> if (fx.rnd() < 0.30f) fx.emit(cx + fx.rr(-9f, 9f), 5f, cz + fx.rr(-9f, 7f), fx.rr(0.3f, 0.8f), -0.6f, fx.rr(-0.2f, 0.2f), 7f, 0.09f, 0.05f, "heart", 1f, 0.72f, 0.82f, 0.9f, spinRate = 2f)
            Weather.LEAVES -> if (fx.rnd() < 0.25f) fx.emit(cx + fx.rr(-9f, 9f), 5f, cz + fx.rr(-9f, 7f), fx.rr(0.2f, 0.7f), -0.7f, fx.rr(-0.2f, 0.2f), 7f, 0.11f, 0.05f, "heart", 1f, if (fx.rnd() < 0.5f) 0.55f else 0.35f, 0.15f, 0.95f, spinRate = 2.5f)
            Weather.RAIN -> if (fx.rnd() < 0.85f) fx.emit(cx + fx.rr(-9f, 9f), 6f, cz + fx.rr(-9f, 7f), 0.4f, -9f, 0f, 0.8f, 0.05f, 0f, "dot", 0.75f, 0.85f, 1f, 0.55f)
            Weather.FIREFLIES -> if (fx.rnd() < 0.12f) fx.emit(cx + fx.rr(-9f, 9f), fx.rr(0.4f, 2.2f), cz + fx.rr(-9f, 7f), fx.rr(-0.2f, 0.2f), fx.rr(-0.05f, 0.15f), fx.rr(-0.2f, 0.2f), 3.5f, 0.12f, 0f, "dot", 0.8f, 1f, 0.45f, 1f)
            Weather.DUST -> if (fx.rnd() < 0.10f) fx.emit(cx + fx.rr(-10f, 10f), fx.rr(0.1f, 0.8f), cz + fx.rr(-9f, 8f), fx.rr(0.6f, 1.4f), 0.05f, 0f, 3f, 0.35f, 0f, "soft", 0.95f, 0.85f, 0.65f, 0.35f)
            Weather.CONFETTI -> if (fx.rnd() < 0.15f) fx.emit(cx + fx.rr(-9f, 9f), 5f, cz + fx.rr(-9f, 7f), fx.rr(-0.3f, 0.3f), -1.2f, fx.rr(-0.3f, 0.3f), 5f, 0.08f, 0.02f, "sparkle", fx.rr(0.6f, 1f), fx.rr(0.4f, 1f), fx.rr(0.4f, 1f), 1f, spinRate = 4f)
            Weather.STARS -> if (fx.rnd() < 0.12f) fx.emit(cx + fx.rr(-9f, 9f), fx.rr(2f, 5f), cz + fx.rr(-9f, 7f), 0f, 0.05f, 0f, 2f, 0.16f, 0f, "sparkle", 0.85f, 0.9f, 1f, 0.9f, spinRate = 1.5f)
            Weather.STEAM -> if (fx.rnd() < 0.18f) fx.emit(cx + fx.rr(-9f, 9f), 0.2f, cz + fx.rr(-9f, 7f), fx.rr(-0.1f, 0.1f), fx.rr(0.5f, 0.9f), 0f, 3f, 0.45f, 0.2f, "soft", 1f, 1f, 1f, 0.3f)
            Weather.CLOUDS -> if (fx.rnd() < 0.05f) fx.emit(cx - 11f, fx.rr(3f, 6f), cz + fx.rr(-9f, 7f), fx.rr(0.8f, 1.4f), 0f, 0f, 14f, 1.4f, 0f, "soft", 1f, 1f, 1f, 0.35f)
            Weather.BUTTERFLIES -> if (fx.rnd() < 0.05f) fx.emit(cx + fx.rr(-9f, 9f), fx.rr(0.6f, 2f), cz + fx.rr(-9f, 7f), fx.rr(-0.6f, 0.6f), fx.rr(0.1f, 0.3f), fx.rr(-0.4f, 0.4f), 4f, 0.14f, 0f, "heart", fx.rr(0.7f, 1f), fx.rr(0.6f, 1f), 0.3f, 1f, spinRate = 6f)
            Weather.SPARKLE -> if (fx.rnd() < 0.15f) fx.emit(cx + fx.rr(-9f, 9f), fx.rr(0.5f, 3f), cz + fx.rr(-9f, 7f), 0f, 0.1f, 0f, 1.6f, 0.2f, 0f, "sparkle", 0.75f, 0.6f, 1f, 0.9f, spinRate = 3f)
            Weather.NONE -> {}
        }
        @Suppress("UNUSED_EXPRESSION") w
    }

    private fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
    private fun mix3(out: FloatArray, a: FloatArray, b: FloatArray, t: Float) { for (i in 0..2) out[i] = a[i] + (b[i] - a[i]) * t }

    // ------------------------------------------------------------------------------------------- events → FX
    private fun handle(s: GameSession, e: com.ngstudio.burgerready.game.GameEvent) {
        val fx = view.particles
        when (e.type) {
            Ev.PICKUP -> { if (e.a >= 0f) s.onEvent(Ev.PICKUP, e.item, e.a); fx.sparkles(e.x, e.y, e.z, 2, spread = 0.15f) }
            Ev.DROP -> if (e.a >= 0f) s.onEvent(Ev.DROP, e.item, e.a)
            Ev.TRASH -> { if (e.a >= 0f) s.onEvent(Ev.TRASH, null, 0f); fx.puff(e.x, e.y, e.z, 4, 0.2f) }
            Ev.COIN_COLLECT -> {
                s.onEvent(Ev.COIN_COLLECT, null, e.a)
                view.texts.add("+$" + amountText(e.a), e.x, e.y + 0.6f, e.z, 0.46f, 0.55f, 1f, 0.6f)
                fx.sparkles(e.x, 0.4f, e.z, 8)
                if (camera.project(e.x, e.y, e.z, width, height, proj)) {
                    coinFlyId++; coinFlyAmount = min(14, max(3, e.b.toInt())); coinFlyX = proj[0] / width; coinFlyY = proj[1] / height
                }
            }
            Ev.PAY_TICK -> { s.onEvent(Ev.PAY_TICK, null, e.a); if (fx.rnd() < 0.5f) fx.emit(e.x, e.y, e.z, (e.x2 - e.x) * 2.5f, 2.2f, (e.z2 - e.z) * 2.5f, 0.42f, 0.22f, 0.18f, "dot", 1f, 0.8f, 0.2f, 1f, 9f) }
            Ev.BUILT, Ev.UPGRADED -> {
                s.onEvent(e.type, null, 0f)
                fx.confetti(e.x, 1.2f, e.z, 40); fx.puff(e.x, 0.3f, e.z, 14, 0.7f); fx.ring(e.x, e.z, 1f, 0.85f, 0.3f, 3f)
                camera.punch(1f); camera.focus(e.x, e.z + 1f, 0.9f)
                showToast(if (e.type == Ev.BUILT) "${e.text} built!" else "${e.text} Lv.${e.a.toInt()}!")
            }
            Ev.HIRED -> { s.onEvent(Ev.HIRED, null, 0f); fx.confetti(e.x, 1f, e.z, 24); showToast("${e.text} hired!") }
            Ev.SERVED -> {
                s.onEvent(Ev.SERVED, null, e.a)
                view.texts.add("$" + amountText(e.a), e.x2, 2.1f, e.z2, 0.4f, 1f, 0.85f, 0.3f)
                fx.sparkles(e.x2, 1.2f, e.z2, 4)
            }
            Ev.ORDER -> s.onEvent(Ev.ORDER, null, 0f)
            Ev.ANGRY -> { s.onEvent(Ev.ANGRY, null, 0f); fx.puff(e.x, e.y, e.z, 6, 0.25f, 0.4f, 0.35f, 0.38f) }
            Ev.EAT -> s.onEvent(Ev.EAT, null, 0f)
            Ev.COMBO_UP -> { s.onEvent(Ev.COMBO_UP, null, e.a); view.texts.add("COMBO x" + String.format("%.1f", e.a).removeSuffix(".0"), e.x, e.y + 0.4f, e.z, 0.42f, 1f, 0.6f, 0.2f, 1.3f) }
            Ev.COMBO_BREAK -> s.onEvent(Ev.COMBO_BREAK, null, 0f)
            Ev.RUSH_START -> { s.onEvent(Ev.RUSH_START, null, 0f); showToast("RUSH HOUR!"); camera.shake(0.6f) }
            Ev.RUSH_END -> { s.onEvent(Ev.RUSH_END, null, 0f); showToast(if (s.world.flawlessRushes > flawlessSeen) "FLAWLESS RUSH!" else "Rush over!"); flawlessSeen = s.world.flawlessRushes }
            Ev.TROUBLE -> { s.onEvent(Ev.TROUBLE, null, 0f); showToast(e.text + "!"); fx.ring(e.x, e.z, 1f, 0.3f, 0.2f, 2.4f) }
            Ev.TROUBLE_FIXED -> { s.onEvent(Ev.TROUBLE_FIXED, null, 0f); fx.sparkles(e.x, e.y, e.z, 10, 0.6f, 1f, 0.7f); fx.puff(e.x, 0.4f, e.z, 8, 0.4f) }
            Ev.POWERUP -> { s.onEvent(Ev.POWERUP, null, 0f); showToast(e.text + "!"); fx.sparkles(e.x, 0.8f, e.z, 16); fx.ring(e.x, e.z, 1f, 0.9f, 0.4f, 3f) }
            Ev.VIP -> s.onEvent(Ev.VIP, null, 0f)
            Ev.CAR_HORN -> s.onEvent(Ev.CAR_HORN, null, 0f)
            Ev.EXPANDED -> {
                s.onEvent(Ev.EXPANDED, null, 0f)
                fx.confetti(e.x, 1.5f, e.z, 80); fx.ring(e.x, e.z, 1f, 0.85f, 0.3f, 5f)
                camera.punch(1.4f); camera.focus(e.x, e.z, 1.2f)
            }
            Ev.CRITIC -> { s.onEvent(Ev.CRITIC, null, 0f); showToast("A food critic is here!") }
            Ev.FOCUS -> camera.focus(e.x, e.z + 0.8f, e.a)
            Ev.ITEM_FLY -> { s.onEvent(Ev.ITEM_FLY, e.item, 0f); fx.emit(e.x, e.y + 0.2f, e.z, (e.x2 - e.x) * 2.6f, 2.5f, (e.z2 - e.z) * 2.6f, 0.38f, 0.42f, 0.3f, "icon_" + (e.item?.id ?: "burger"), 1f, 1f, 1f, 1f, 9f) }
            Ev.STEP -> s.onEvent(Ev.STEP, null, 0f)
            Ev.CRITIC_HAPPY -> { s.onEvent(Ev.CRITIC_HAPPY, null, 0f); fx.confetti(e.x, 2f, e.z, 20); showToast("The critic loved it!") }
            Ev.STOLEN -> { s.onEvent(Ev.STOLEN, null, 0f); showToast("Raccoon thief! Chase it!") }
            Ev.SHOO -> { s.onEvent(Ev.SHOO, null, 0f); fx.puff(e.x, 0.4f, e.z, 10, 0.5f) }
            Ev.COIN_FLY -> {}
        }
    }

    private var flawlessSeen = 0
    /** Floating money text: exact below a billion (as before), short form for the World Tour's big numbers. */
    private fun amountText(a: Float): String { val v = a.toLong(); return if (v < 1_000_000_000L) v.toString() else view.formatShort(v) }
    private fun showToast(t: String) { toast = t; toastId++ }

    private fun publishHud(s: GameSession) {
        val w = s.world
        val troubles = w.troubles.filter { !it.done }.map { it.kind.label }
        val boosts = ArrayList<Pair<String, Float>>()
        if (w.espressoT > 0f) boosts.add(PowerUpKind.ESPRESSO.label to w.espressoT)
        if (w.magnetT > 0f) boosts.add(PowerUpKind.MAGNET.label to w.magnetT)
        if (w.spatulaT > 0f) boosts.add(PowerUpKind.SPATULA.label to w.spatulaT)
        var nextLeft = 0L
        for (p in w.pads) if (p.visible && !p.done) { nextLeft = p.remaining; break }
        s.hud = HudState(
            storeName = w.cfg.name, built = w.builtCount, total = w.cfg.buildPads, cash = w.cash, combo = w.combo,
            rush = w.rushT > 0f, served = w.served,
            stack = w.player.stack.size, capacity = w.capacity, troubles = troubles, boosts = boosts,
            tip = if (w.cfg.isFirst && w.builtCount < 10) Story.tutorialTip(w) else "", toast = toast, toastId = toastId,
            fps = fps, drawCalls = renderer.drawCalls,
            coinFlyId = coinFlyId, coinFlyAmount = coinFlyAmount, coinFlyX = coinFlyX, coinFlyY = coinFlyY,
            nextPadLeft = nextLeft, expanded = w.expanded, cues = Story.cues(w),
        )
    }
}
