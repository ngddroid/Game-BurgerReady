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
            renderer.init()
            models = ModelCache(context.assets)
            atlas = SpriteAtlas(context)
            atlas.build()
            renderer.spriteTexture = atlas.texture
            view = WorldView(models, atlas, Particles(atlas), FloatTexts(atlas))
            // Preload every model so the first seconds of play never hitch.
            context.assets.list("models")?.forEach { f -> if (f.endsWith(".bvm")) models.getOrNull(f.removeSuffix(".bvm")) }
            IconBaker.bake(renderer, atlas, models)
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
        camera.distance = if (camera.aspect < 0.5f) 22.5f else 20f
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
        if (!s.cameraSnapped) { camera.snapTo(w.player.x, w.player.z - 0.6f); s.cameraSnapped = true; view.particles.clear() }
        val simDt = if (s.paused) 0f else dt
        if (!s.paused && !w.finished) {
            // joystick → world input
            val out = FloatArray(2)
            camera.screenDirToWorld(s.joyX, s.joyY, out)
            w.inputX = out[0]; w.inputZ = out[1]
            if (s.finishRequested) w.finishRequested = true
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
        }
        if (w.finished && !s.finishedNotified) {
            w.inputX = 0f; w.inputZ = 0f
            s.endDelay += dt
            if (s.endDelay > 1.2f) { s.finishedNotified = true; s.onFinished(w.result()) }
        }
        camera.follow(w.player.x, w.player.z - 0.6f, w.player.vx, w.player.vz)
        camera.update(dt)
        applyLighting(w, s.style)
        view.night = renderer.lighting.night
        if (!s.paused) weather(s.style.store.id, w)
        view.particles.update(simDt)
        view.texts.update(simDt)
        renderer.begin(camera)
        view.draw(w, renderer, camera, s.style, simDt, w.cfg.tutorial > 0 || w.time < 20f)
        view.carBubbles(renderer, w, camera)
        view.particles.draw(renderer)
        view.texts.draw(renderer, camera)
        renderer.end()
        hudT -= dt
        if (hudT <= 0f) { hudT = 1f / 15f; publishHud(s) }
    }

    private fun applyLighting(w: World, style: VisualStyle) {
        val L = renderer.lighting
        val f = (w.time / w.cfg.shiftSeconds).coerceIn(0f, 1.25f)
        val nightShift = w.cfg.mutators.any { it.name == "NIGHT_SHIFT" } || style.store.id == "space" || style.store.id == "rooftop"
        // day → golden hour → dusk
        val sunset = smooth(0.55f, 0.85f, f)
        var dusk = smooth(0.85f, 1.1f, f)
        if (nightShift) dusk = max(dusk, 0.85f)
        val sky = rgbF(style.store.sky)
        L.sunDir[0] = -0.45f + sunset * -0.25f; L.sunDir[1] = 0.80f - sunset * 0.25f - dusk * 0.1f; L.sunDir[2] = 0.40f
        L.normalizeSun()
        mix3(L.sunColor, floatArrayOf(0.80f, 0.74f, 0.64f), floatArrayOf(0.86f, 0.58f, 0.40f), sunset)
        mix3(L.sunColor, L.sunColor, floatArrayOf(0.36f, 0.38f, 0.58f), dusk)
        mix3(L.skyColor, floatArrayOf(0.40f + sky[0] * 0.08f, 0.43f + sky[1] * 0.08f, 0.52f + sky[2] * 0.08f), floatArrayOf(0.46f, 0.38f, 0.46f), sunset)
        mix3(L.skyColor, L.skyColor, floatArrayOf(0.30f, 0.31f, 0.52f), dusk)
        mix3(L.groundColor, floatArrayOf(0.30f, 0.26f, 0.25f), floatArrayOf(0.22f, 0.19f, 0.30f), dusk)
        mix3(L.fogColor, floatArrayOf(sky[0], sky[1], sky[2]), floatArrayOf(1f, 0.74f, 0.58f), sunset * 0.7f)
        mix3(L.fogColor, L.fogColor, floatArrayOf(0.25f, 0.24f, 0.42f), dusk)
        System.arraycopy(L.fogColor, 0, L.clearColor, 0, 3)
        L.fogStart = 30f; L.fogEnd = 64f
        L.night = dusk
    }

    /** Light ambient weather per store, spawned around the camera target. */
    private fun weather(store: String, w: World) {
        val fx = view.particles
        val cx = camera.targetX; val cz = camera.targetZ
        when (store) {
            "lodge" -> if (fx.rnd() < 0.55f) fx.emit(cx + fx.rr(-9f, 9f), 7f, cz + fx.rr(-9f, 7f), fx.rr(-0.3f, 0.3f), -1.1f, fx.rr(-0.2f, 0.2f), 6f, 0.12f, 0.1f, "soft", 1f, 1f, 1f, 0.9f)
            "underwater" -> if (fx.rnd() < 0.25f) fx.emit(cx + fx.rr(-9f, 9f), 0.2f, cz + fx.rr(-9f, 7f), 0f, 1.2f, 0f, 4.5f, 0.12f, 0.28f, "ring", 0.85f, 0.95f, 1f, 0.7f)
            "space", "candy" -> if (fx.rnd() < 0.15f) fx.emit(cx + fx.rr(-9f, 9f), fx.rr(1.5f, 4f), cz + fx.rr(-9f, 7f), 0f, 0.15f, 0f, 1.6f, 0.22f, 0f, "sparkle", 1f, if (store == "candy") 0.7f else 0.95f, if (store == "candy") 0.9f else 0.6f, 0.9f, spinRate = 3f)
            "volcano" -> if (fx.rnd() < 0.2f) fx.emit(cx + fx.rr(-9f, 9f), 0.3f, cz + fx.rr(-9f, 7f), fx.rr(-0.2f, 0.2f), fx.rr(0.8f, 1.6f), 0f, 2.2f, 0.1f, 0.02f, "dot", 1f, 0.55f, 0.2f, 1f)
            "seaside", "jungle" -> if (fx.rnd() < 0.04f) fx.emit(cx + fx.rr(-9f, 9f), fx.rr(2f, 4f), cz + fx.rr(-9f, 7f), fx.rr(0.6f, 1.2f), 0f, 0f, 3f, 0.25f, 0.25f, "heart", 1f, 1f, 1f, 0.0f)
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
                view.texts.add("+$" + e.a.toInt(), e.x, e.y + 0.6f, e.z, 0.46f, 0.55f, 1f, 0.6f)
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
                view.texts.add("$" + e.a.toInt(), e.x2, 2.1f, e.z2, 0.4f, 1f, 0.85f, 0.3f)
                fx.sparkles(e.x2, 1.2f, e.z2, 4)
            }
            Ev.ORDER -> s.onEvent(Ev.ORDER, null, 0f)
            Ev.ANGRY -> { s.onEvent(Ev.ANGRY, null, 0f); fx.puff(e.x, e.y, e.z, 6, 0.25f, 0.4f, 0.35f, 0.38f) }
            Ev.EAT -> s.onEvent(Ev.EAT, null, 0f)
            Ev.COMBO_UP -> { s.onEvent(Ev.COMBO_UP, null, e.a); view.texts.add("COMBO x" + String.format("%.1f", e.a).removeSuffix(".0"), e.x, e.y + 0.4f, e.z, 0.42f, 1f, 0.6f, 0.2f, 1.3f) }
            Ev.COMBO_BREAK -> s.onEvent(Ev.COMBO_BREAK, null, 0f)
            Ev.RUSH_START -> { s.onEvent(Ev.RUSH_START, null, 0f); showToast("RUSH HOUR!"); camera.shake(0.6f) }
            Ev.RUSH_END -> s.onEvent(Ev.RUSH_END, null, 0f)
            Ev.TROUBLE -> { s.onEvent(Ev.TROUBLE, null, 0f); showToast(e.text + "!"); fx.ring(e.x, e.z, 1f, 0.3f, 0.2f, 2.4f) }
            Ev.TROUBLE_FIXED -> { s.onEvent(Ev.TROUBLE_FIXED, null, 0f); fx.sparkles(e.x, e.y, e.z, 10, 0.6f, 1f, 0.7f); fx.puff(e.x, 0.4f, e.z, 8, 0.4f) }
            Ev.POWERUP -> { s.onEvent(Ev.POWERUP, null, 0f); showToast(e.text + "!"); fx.sparkles(e.x, 0.8f, e.z, 16); fx.ring(e.x, e.z, 1f, 0.9f, 0.4f, 3f) }
            Ev.VIP -> s.onEvent(Ev.VIP, null, 0f)
            Ev.CAR_HORN -> s.onEvent(Ev.CAR_HORN, null, 0f)
            Ev.GOAL_REACHED -> { s.onEvent(Ev.GOAL_REACHED, null, 0f); showToast("GOAL REACHED!"); fx.confetti(s.world.player.x, 2f, s.world.player.z, 50) }
            Ev.TIMER_WARN -> s.onEvent(Ev.TIMER_WARN, null, 0f)
            Ev.FOCUS -> camera.focus(e.x, e.z + 0.8f, e.a)
            Ev.ITEM_FLY -> { s.onEvent(Ev.ITEM_FLY, e.item, 0f); fx.emit(e.x, e.y + 0.2f, e.z, (e.x2 - e.x) * 2.6f, 2.5f, (e.z2 - e.z) * 2.6f, 0.38f, 0.42f, 0.3f, "icon_" + (e.item?.id ?: "burger"), 1f, 1f, 1f, 1f, 9f) }
            Ev.STEP -> s.onEvent(Ev.STEP, null, 0f)
            Ev.CRITIC_HAPPY -> { s.onEvent(Ev.CRITIC_HAPPY, null, 0f); fx.confetti(e.x, 2f, e.z, 20); showToast("The critic loved it!") }
            Ev.STOLEN -> { s.onEvent(Ev.STOLEN, null, 0f); showToast("Raccoon thief! Chase it!") }
            Ev.SHOO -> { s.onEvent(Ev.SHOO, null, 0f); fx.puff(e.x, 0.4f, e.z, 10, 0.5f) }
            Ev.COIN_FLY -> {}
        }
    }

    private fun showToast(t: String) { toast = t; toastId++ }

    private fun publishHud(s: GameSession) {
        val w = s.world
        val troubles = w.troubles.filter { !it.done }.map { it.kind.label }
        val boosts = ArrayList<Pair<String, Float>>()
        if (w.espressoT > 0f) boosts.add(PowerUpKind.ESPRESSO.label to w.espressoT)
        if (w.magnetT > 0f) boosts.add(PowerUpKind.MAGNET.label to w.magnetT)
        if (w.spatulaT > 0f) boosts.add(PowerUpKind.SPATULA.label to w.spatulaT)
        s.hud = HudState(
            levelId = w.cfg.id, title = w.cfg.title, cash = w.cash, revenue = w.revenue, target = w.cfg.target,
            star2 = w.cfg.star2, star3 = w.cfg.star3, timeLeft = w.timeLeft, overtime = w.inOvertime, combo = w.combo,
            rush = w.rushT > 0f, goalReached = w.goalReached, walkouts = w.walkouts, served = w.served,
            stack = w.player.stack.size, capacity = w.capacity, troubles = troubles, boosts = boosts,
            tip = if (w.cfg.tutorial > 0) TutorialTips.tip(w) else "", toast = toast, toastId = toastId,
            fps = fps, drawCalls = renderer.drawCalls, tutorial = w.cfg.tutorial,
            coinFlyId = coinFlyId, coinFlyAmount = coinFlyAmount, coinFlyX = coinFlyX, coinFlyY = coinFlyY,
        )
    }
}

/** Context-sensitive hints for the tutorial levels. */
object TutorialTips {
    fun tip(w: World): String {
        val p = w.player
        val pad = w.visiblePads().firstOrNull()
        if (w.stations.none { it.built }) return if (w.cash >= (pad?.remaining ?: 0)) "Stand on the glowing pad to build your Grill!" else "Collect cash to build!"
        if (p.stack.isNotEmpty() && p.stack[0] == Item.TRAY) return "Drop dirty trays in the trash bin"
        if (p.stack.isNotEmpty()) return "Bring the food to the counter"
        val front = w.queue.firstOrNull()
        if (front != null && front.state == CState.ORDERING && !w.cashierPresent && w.staff.none { it.role.name == "CASHIER" }) {
            var ok = true
            for (i in front.order.indices) if (front.order[i] - front.got[i] > w.counterRacks[i]) ok = false
            if (ok) return "Stand at the register to serve!"
        }
        if (w.counterPile.amount >= 10) return "Grab your cash from the counter"
        if (pad != null && w.cash >= pad.remaining) return "You can afford an upgrade — step on the pad!"
        if (w.tables.any { it.trays > 0 }) return "Clean the dirty tables"
        if (w.stations.any { it.built && it.tray > 0 }) return "Pick up food from the stations"
        return when (w.cfg.tutorial) {
            5 -> "Serve fast to build a COMBO for bigger tips!"
            8 -> "VIPs wear crowns and pay triple!"
            9 -> "Cars order at the drive-thru window"
            10 -> "Keep the critics happy!"
            else -> "Food is cooking…"
        }
    }
}
