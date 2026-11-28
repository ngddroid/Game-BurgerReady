package com.ngstudio.burgerready.render

import com.ngstudio.burgerready.engine.Camera
import com.ngstudio.burgerready.engine.FloatTexts
import com.ngstudio.burgerready.engine.M4
import com.ngstudio.burgerready.engine.Mesh
import com.ngstudio.burgerready.engine.MeshBuilder
import com.ngstudio.burgerready.engine.Model
import com.ngstudio.burgerready.engine.ModelCache
import com.ngstudio.burgerready.engine.Particles
import com.ngstudio.burgerready.engine.Renderer
import com.ngstudio.burgerready.engine.SpriteAtlas
import com.ngstudio.burgerready.engine.TextDraw
import com.ngstudio.burgerready.game.CState
import com.ngstudio.burgerready.game.CarState
import com.ngstudio.burgerready.game.Item
import com.ngstudio.burgerready.game.PadKind
import com.ngstudio.burgerready.game.PowerUpKind
import com.ngstudio.burgerready.game.SState
import com.ngstudio.burgerready.game.Species
import com.ngstudio.burgerready.game.StaffRole
import com.ngstudio.burgerready.game.StoreDef
import com.ngstudio.burgerready.game.Theme
import com.ngstudio.burgerready.game.TroubleKind
import com.ngstudio.burgerready.game.World
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Visual choices that don't affect gameplay. */
data class VisualStyle(
    val store: StoreDef,
    val theme: Theme,
    val playerSpecies: Species,
    val playerHat: String?,
    val apron: Int,
    val petModel: String?,
)

fun rgbF(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)

/** Draws a [World] with the 3D models. Holds no gameplay state except purely visual smoothing. */
class WorldView(
    private val models: ModelCache,
    private val atlas: SpriteAtlas,
    val particles: Particles,
    val texts: FloatTexts,
) {
    private val m = FloatArray(16)
    private val root = FloatArray(16)
    private val tmp = FloatArray(16)
    private val partG = FloatArray(16 * 16)
    private val local = FloatArray(16)
    private var ground: Mesh? = null
    private var groundLayout: Any? = null
    private var groundStore: Any? = null
    var time = 0f
    /** 0 = day, 1 = night (set by the renderer each frame); drives lamp glow pools. */
    var night = 0f
    private val shirtColors = intArrayOf(0xF2542D, 0x1FA6B8, 0x3DDC97, 0xFFC23C, 0xB79CFF, 0xFF8FB1, 0x3D8BFF, 0xFF9A3C, 0x4CC38A, 0xE94B3C, 0x2C3E66, 0x9B2C5C)
    private val carColors = intArrayOf(0xF2542D, 0x1FA6B8, 0xFFC23C, 0x3DDC97, 0xFF8FB1, 0xB79CFF, 0x3D8BFF, 0xFFFFFF)
    private val petPos = floatArrayOf(0f, 0f, 0f, 0f) // x, z, yaw, phase
    private var doorFade = 1f
    // Kitchen checker: a clear mint-teal instead of the old near-white (read as washed out next to the wood floors).
    private val KITCHEN_A = rgbF(0xC4E4DE)
    private val KITCHEN_B = rgbF(0xA9D4CD)
    private val DOORMAT = rgbF(0xC9B9A6)

    // ---- per-frame caches: names, colours and labels are built once, never inside draw()
    private val foodModel = Array(Item.entries.size) { "food_" + Item.entries[it].id }
    private val iconName = Array(Item.entries.size) { "icon_" + Item.entries[it].id }
    private val charModel = Array(Species.entries.size) { "char_" + Species.entries[it].id }
    private val shirtRgb = Array(shirtColors.size) { rgbF(shirtColors[it]) }
    private val carRgb = Array(carColors.size) { rgbF(carColors[it]) }
    private val staffRgb = arrayOf(rgbF(0x1FA6B8), rgbF(0xFFC23C), rgbF(0x3DDC97)) // by StaffRole ordinal
    private val hireTag = Array(StaffRole.entries.size) { "HIRE " + StaffRole.entries[it].label.uppercase() }
    private val countStr = Array(10) { it.toString() }
    private val HAT_RED = rgbF(0xF2542D)
    private val CRITIC_HAT = rgbF(0x2C3E66)
    private val RACCOON_TINT = rgbF(0x2C2C34)
    private val PATIENCE_MID = rgbF(0xFFC23C)
    private val PATIENCE_LOW = rgbF(0xF2542D)
    private val puIcon = arrayOf("bolt", "heart", "star") // by PowerUpKind ordinal
    private val puRgb = arrayOf(rgbF(0x8B5A3C), rgbF(0xE94B3C), rgbF(0xFFC23C))
    private var styleKey: VisualStyle? = null
    private val trimC = FloatArray(3)
    private val floorC = FloatArray(3)
    private val floorAltC = FloatArray(3)
    private val seatC = FloatArray(3)
    private val apronC = FloatArray(3)
    private var playerHat: String? = null
    private var padLabelVal = LongArray(0)
    private var padLabel = arrayOfNulls<String>(0)
    private val racks = ArrayList<Item>(4)
    private var racksFor: Any? = null
    private val guideTarget = FloatArray(2)
    private val RACK = arrayOf("rack0", "rack1", "rack2", "rack3")

    private fun setRgb(out: FloatArray, c: Int) { out[0] = ((c shr 16) and 255) / 255f; out[1] = ((c shr 8) and 255) / 255f; out[2] = (c and 255) / 255f }

    /**
     * Dining-floor tint for tile floors in the default theme. The tile model is white, so restaurants with tile
     * floors (Rooftop, Tokyo, Space, Underwater) used to get a near-white floor that went pale lavender at night.
     */
    private fun storeFloorTint(st: StoreDef): Int = st.floorTint

    private fun updateStyle(style: VisualStyle) {
        if (styleKey === style) return
        styleKey = style
        val st = style.store; val th = style.theme
        val classic = th.id == "classic"
        setRgb(trimC, if (classic) st.trim else th.trim)
        setRgb(floorC, if (classic) storeFloorTint(st) else th.floorTint)
        floorAltC[0] = floorC[0] * 0.93f; floorAltC[1] = floorC[1] * 0.92f; floorAltC[2] = floorC[2] * 0.92f
        setRgb(seatC, if (classic) st.trim else th.seat)
        setRgb(apronC, style.apron)
        playerHat = style.playerHat?.let { "hat_$it" }
    }

    private fun padLabelFor(pad: com.ngstudio.burgerready.game.Pad): String {
        val i = pad.index
        if (i >= padLabelVal.size) { padLabelVal = LongArray(i + 16) { -1L }; padLabel = arrayOfNulls(i + 16) }
        val v = pad.remaining
        if (padLabelVal[i] != v || padLabel[i] == null) { padLabelVal[i] = v; padLabel[i] = "$" + formatShort(v) }
        return padLabel[i]!!
    }

    private fun padNear(w: World, x: Float, z: Float, r: Float): Boolean {
        for (p in w.pads) if (p.visible && !p.done && abs(p.x - x) < r && abs(p.z - z) < r) return true
        return false
    }

    /** Soft contact shadow under characters when the shadow map is off (LOW quality). */
    private fun blob(r: Renderer, x: Float, z: Float, size: Float) {
        if (r.quality.shadows) return
        r.sprite(x, 0.035f, z, size * 1.1f, size * 1.1f, atlas.uv("soft"), 0.16f, 0.10f, 0.14f, 0.5f, mode = 1)
    }

    fun mdl(name: String): Model? = models.getOrNull(name)

    // ------------------------------------------------------------------------------------------- generic drawing
    fun drawModel(
        r: Renderer, model: Model?, x: Float, y: Float, z: Float, yaw: Float = 0f, s: Float = 1f,
        tint: FloatArray? = null, alpha: Float = 1f, flash: Float = 0f, outline: Boolean = false, shadow: Boolean = true,
        sy: Float = s, emit: Float = 0f,
    ) {
        if (model == null) return
        M4.trs(root, 0, x, y, z, yaw, s, sy, s)
        for (p in model.parts) {
            val mesh = p.mesh ?: continue
            M4.identity(local, 0)
            local[12] = p.pivotX; local[13] = p.pivotY; local[14] = p.pivotZ
            M4.mul(m, 0, root, 0, local, 0)
            r.draw(mesh, m, 0, tint?.get(0) ?: 1f, tint?.get(1) ?: 1f, tint?.get(2) ?: 1f, alpha, flash, emit, 0f, shadow && alpha >= 0.99f, outline)
        }
    }

    // ------------------------------------------------------------------------------------------- characters
    class Pose {
        var bob = 0f; var lean = 0f
        var legL = 0f; var legR = 0f
        var armL = 0f; var armR = 0f
        var armLz = 0f; var armRz = 0f
        var headTilt = 0f; var headYaw = 0f
        var blink = 1f; var tail = 0f
        var squashY = 1f
        var sit = false
        fun reset() { bob = 0f; lean = 0f; legL = 0f; legR = 0f; armL = 0f; armR = 0f; armLz = 0f; armRz = 0f; headTilt = 0f; headYaw = 0f; blink = 1f; tail = 0f; squashY = 1f; sit = false }
    }

    private val pose = Pose()

    private fun hatLiftFor(sp: Species): Float = if (sp == Species.FROG) 0.11f else 0f

    fun walkPose(p: Pose, phase: Float, speed: Float, carrying: Boolean, seed: Int) {
        p.reset()
        val moving = speed > 0.2f
        val amp = min(1f, speed / 3.5f)
        if (moving) {
            val s = sin(phase)
            p.legL = s * 0.75f * amp + 0.0f
            p.legR = -s * 0.75f * amp
            p.bob = abs(cos(phase)) * 0.045f * amp
            p.lean = 0.12f * amp
            if (!carrying) { p.armL = -s * 0.6f * amp; p.armR = s * 0.6f * amp }
            p.headTilt = sin(phase * 2f) * 0.03f
        } else {
            p.bob = sin(time * 2.2f + seed) * 0.012f
            if (!carrying) { p.armLz = 0.08f + sin(time * 2.2f + seed) * 0.03f; p.armRz = -p.armLz }
        }
        if (carrying) { p.armL = -1.35f; p.armR = -1.35f; p.armLz = -0.12f; p.armRz = 0.12f }
        p.tail = sin(time * (if (moving) 14f else 5f) + seed) * 0.35f
        val bp = (time * 0.37f + seed * 0.123f) % 3.1f
        p.blink = if (bp < 0.12f) 0.12f else 1f
    }

    /**
     * Draws a rigid-part character. Returns the root matrix in [root] so stacks/hats can be attached.
     */
    fun drawCharacter(
        r: Renderer, model: Model?, x: Float, z: Float, yaw: Float, p: Pose, tint: FloatArray?, scale: Float = 1f,
        y: Float = 0f, flash: Float = 0f, hat: Model? = null, hatTint: FloatArray? = null, outline: Boolean = true,
        hatLift: Float = 0f,
    ) {
        if (model == null) return
        M4.trs(root, 0, x, y + p.bob, z, yaw, scale, scale * p.squashY, scale)
        val parts = model.parts
        for (i in parts.indices) {
            val part = parts[i]
            var rx = 0f; var ry = 0f; var rz = 0f; var sxx = 1f; var syy = 1f
            when (part.name) {
                "body" -> { rx = -p.lean * 0.5f }
                "head" -> { rx = p.headTilt; ry = p.headYaw }
                "eyes" -> { syy = p.blink }
                "legL" -> rx = -p.legL
                "legR" -> rx = -p.legR
                "armL" -> { rx = p.armL; rz = p.armLz }
                "armR" -> { rx = p.armR; rz = p.armRz }
                "tail" -> ry = p.tail
            }
            M4.trsEuler(local, 0, part.localX, part.localY, part.localZ, rx, ry, rz, sxx, syy, 1f)
            if (part.parent >= 0 && part.parent < i) M4.mul(partG, i * 16, partG, part.parent * 16, local, 0)
            else M4.mul(partG, i * 16, root, 0, local, 0)
            val mesh = part.mesh ?: continue
            val caster = when (part.name) { "body", "head", "legL", "legR" -> true; else -> false }
            r.draw(mesh, partG, i * 16, tint?.get(0) ?: 1f, tint?.get(1) ?: 1f, tint?.get(2) ?: 1f, 1f, flash, 0f, 0f, caster, outline)
        }
        if (hat != null) {
            val head = model.part("head")
            val a = model.anchor("hat")
            if (head >= 0 && a != null) {
                val hp = parts[head]
                // hatLift raises/tilts hats on species whose eyes sit high on the head (frog)
                M4.trsEuler(local, 0, a[0] - hp.pivotX, a[1] - hp.pivotY + hatLift, a[2] - hp.pivotZ + hatLift * 0.5f, -hatLift * 1.5f, 0f, 0f, 1f, 1f, 1f)
                M4.mul(tmp, 0, partG, head * 16, local, 0)
                for (hpart in hat.parts) {
                    val mesh = hpart.mesh ?: continue
                    M4.identity(local, 0); local[12] = hpart.pivotX; local[13] = hpart.pivotY; local[14] = hpart.pivotZ
                    M4.mul(m, 0, tmp, 0, local, 0)
                    r.draw(mesh, m, 0, hatTint?.get(0) ?: 1f, hatTint?.get(1) ?: 1f, hatTint?.get(2) ?: 1f, 1f, flash, 0f, 0f, false, outline)
                }
            }
        }
    }

    /** Draws a stack of items held in front of a character drawn last with [drawCharacter]. */
    private val DEFAULT_HAND = floatArrayOf(0f, 0.55f, 0.26f)

    fun drawHeldStack(r: Renderer, model: Model?, items: List<Item>, sway: Float, scale: Float = 1f, max: Int = Int.MAX_VALUE) {
        if (items.isEmpty()) return
        val hand = model?.anchor("hand") ?: DEFAULT_HAND
        var y = hand[1]
        val n = min(items.size, max)
        for (i in 0 until n) {
            val it = items[i]
            val im = mdl(foodModel[it.ordinal]) ?: continue
            val k = i.toFloat() / max(1, 12)
            val off = sway * (i * 0.06f + k * k * 0.5f)
            // position in character root space
            M4.trsEuler(local, 0, hand[0], y, hand[2] - off, -sway * 0.08f * k, 0f, 0f, 0.95f, 0.95f, 0.95f)
            M4.mul(tmp, 0, root, 0, local, 0)
            for (pp in im.parts) {
                val mesh = pp.mesh ?: continue
                M4.identity(local, 0); local[12] = pp.pivotX; local[13] = pp.pivotY; local[14] = pp.pivotZ
                M4.mul(m, 0, tmp, 0, local, 0)
                r.draw(mesh, m, 0, shadow = false, outline = false)
            }
            y += it.height * 0.95f
        }
    }

    // ------------------------------------------------------------------------------------------- the world
    fun draw(w: World, r: Renderer, cam: Camera, style: VisualStyle, dt: Float, showGuide: Boolean) {
        time += dt
        val lay = w.layout
        val st = style.store
        updateStyle(style)
        val trim = trimC
        val floorT = floorC
        val floorAlt = floorAltC
        val seat = seatC

        drawGround(r, w, style)
        // ---- interior floor (two zones: kitchen + dining)
        val floorModel = mdl(st.floor) ?: mdl("floor_wood")
        val kitchenFloor = mdl("floor_tile") ?: floorModel
        for (zi in 0 until lay.d) for (xi in 0 until lay.w) {
            val inKitchen = zi + 0.5f < lay.counter.z
            val fm = if (inKitchen) kitchenFloor else floorModel
            val alt = (xi + zi) % 2 == 0
            val t = if (inKitchen) (if (alt) KITCHEN_A else KITCHEN_B) else (if (alt) floorT else floorAlt)
            drawModel(r, fm, xi + 0.5f, 0f, zi + 0.5f, 0f, 1f, t, shadow = false)
        }
        // door mat area outside
        for (xi in -1..1) drawModel(r, mdl("floor_tile"), lay.doorX + xi, 0f, lay.d + 0.5f, 0f, 1f, DOORMAT, shadow = false)
        // ---- walls
        val wallM = mdl("wall"); val winM = mdl("wall_window"); val post = mdl("wall_post")
        val half = (PI / 2).toFloat()
        for (xi in 0 until lay.w) drawModel(r, if (xi in lay.windowsBack) winM ?: wallM else wallM, xi + 0.5f, 0f, 0f, 0f, 1f, trim)
        for (zi in 0 until lay.d) {
            val isWin = zi in lay.windowsSide
            val driveGap = lay.drive != null && abs(zi + 0.5f - lay.drive.z) < 1.0f
            drawModel(r, if (isWin) winM ?: wallM else wallM, 0f, 0f, zi + 0.5f, half, 1f, trim)
            if (!driveGap) drawModel(r, if (isWin) winM ?: wallM else wallM, lay.w.toFloat(), 0f, zi + 0.5f, -half, 1f, trim)
        }
        // front wall: low and with a door gap; drawn semi-transparent so the dining room stays visible
        for (xi in 0 until lay.w) {
            val cx = xi + 0.5f
            if (abs(cx - lay.doorX) < 1.0f) continue
            drawModel(r, wallM, cx, 0f, lay.d.toFloat(), PI.toFloat(), 1f, trim, sy = 0.45f)
        }
        val pl = w.player
        val behindDoor = abs(pl.x - lay.doorX) < 1.8f && pl.z < lay.d && pl.z > lay.d - 2.6f
        doorFade += ((if (behindDoor) 0.35f else 1f) - doorFade) * min(1f, dt * 8f)
        drawModel(r, mdl("door_frame"), lay.doorX, 0f, lay.d.toFloat(), 0f, 1f, trim, alpha = if (doorFade > 0.98f) 1f else doorFade)
        drawModel(r, post, 0f, 0f, 0f, 0f, 1f, trim)
        drawModel(r, post, lay.w.toFloat(), 0f, 0f, 0f, 1f, trim)
        drawModel(r, post, 0f, 0f, lay.d.toFloat(), 0f, 1f, trim)
        drawModel(r, post, lay.w.toFloat(), 0f, lay.d.toFloat(), 0f, 1f, trim)

        // ---- decor (outdoor trees swap per store so every shift shows where you are)
        val treeModel = st.tree
        for (dcr in lay.decorInside) {
            if (dcr.model == "plant_pot" && dcr.x > lay.w / 2f && dcr.z > lay.d - 1f) continue // replaced by a signature prop
            drawModel(r, mdl(dcr.model), dcr.x, 0f, dcr.z, dcr.yaw, dcr.scale, trim)
        }
        for (dcr in lay.decorOutside) {
            // the bench and flower bed share the sidewalk with the staff pads: step aside while a pad shows there
            if (dcr.z > lay.d && (dcr.model == "bench" || dcr.model == "flower_bed") && padNear(w, dcr.x, dcr.z, 1.25f)) continue
            val name = if (dcr.model == "tree_round") treeModel else dcr.model
            drawModel(r, mdl(name) ?: mdl(dcr.model), dcr.x, 0f, dcr.z, dcr.yaw, dcr.scale, trim)
        }
        // store signature props: two by the entrance, one in the dining corner (scaled to fit)
        for (i in st.props.indices) {
            val pm = mdl(st.props[i]) ?: continue
            val px = when (i) { 0 -> lay.doorX - 2.45f; 1 -> lay.doorX + 2.5f; else -> lay.w - 0.75f }
            val pz = if (i < 2) lay.d + 1.95f else lay.d - 0.75f
            val maxR = if (i < 2) 0.6f else 0.5f
            // the sidewalk hosts staff / NEW RESTAURANT pads: step aside while one is showing there
            if (i < 2 && padNear(w, px, pz, 1.6f)) continue
            if (pm.radius > 1.6f && i < 2) {
                // big props (food truck, towers) get their own lawn spot at full size
                drawModel(r, pm, -3.4f, 0f, lay.d - 4.5f + i * 3.2f, half, 1f, trim)
            } else {
                val sc = minOf(1f, maxR / maxOf(0.1f, pm.radius))
                drawModel(r, pm, px, 0f, pz, if (i == 2) -half * 0.5f else 0f, sc, trim, outline = i == 2)
            }
        }

        if (night > 0.15f) {
            val a = ((night - 0.15f) / 0.6f).coerceIn(0f, 1f) * 0.55f
            for (dcr in lay.decorInside) if (dcr.model.startsWith("lamp")) r.sprite(dcr.x, 0.05f, dcr.z, 4.2f, 4.2f, atlas.uv("light_pool"), 1f, 0.82f, 0.48f, a, mode = 1)
            for (dcr in lay.decorOutside) if (dcr.model.startsWith("lamp")) r.sprite(dcr.x, 0.05f, dcr.z, 4.2f, 4.2f, atlas.uv("light_pool"), 1f, 0.82f, 0.48f, a, mode = 1)
            // warm spill under the counter and door
            r.sprite(lay.counter.x, 0.05f, lay.counter.z + 0.8f, 5f, 3.2f, atlas.uv("light_pool"), 1f, 0.85f, 0.55f, a * 0.6f, mode = 1)
            r.sprite(lay.doorX, 0.05f, lay.d + 0.6f, 3.6f, 2.4f, atlas.uv("light_pool"), 1f, 0.85f, 0.55f, a * 0.7f, mode = 1)
            // Lit windows: lamp light falls out of every window onto the lawn and out of the shop front onto the
            // sidewalk, so the restaurant glows against the night instead of sitting in one flat blue.
            val pool = atlas.uv("light_pool")
            val wa = min(1f, a * 1.6f)
            for (xi in 0 until lay.w) if (xi in lay.windowsBack) r.sprite(xi + 0.5f, 0.04f, -0.8f, 2.3f, 1.8f, pool, 1f, 0.76f, 0.38f, wa, mode = 1)
            for (zi in 0 until lay.d) if (zi in lay.windowsSide) {
                r.sprite(-0.85f, 0.04f, zi + 0.5f, 1.8f, 2.4f, pool, 1f, 0.76f, 0.38f, wa, mode = 1)
                if (lay.drive == null || abs(zi + 0.5f - lay.drive.z) >= 1.0f) r.sprite(lay.w + 0.85f, 0.04f, zi + 0.5f, 1.8f, 2.4f, pool, 1f, 0.76f, 0.38f, wa, mode = 1)
            }
            var fx = 1f
            while (fx < lay.w) {
                if (abs(fx - lay.doorX) > 1.7f) r.sprite(fx, 0.04f, lay.d + 0.65f, 2.5f, 1.7f, pool, 1f, 0.78f, 0.42f, wa * 0.9f, mode = 1)
                fx += 2f
            }
            // street-lamp halos around the bulbs (the ground pools above only lit the pavement)
            val soft = atlas.uv("soft")
            for (i in lay.decorOutside.indices) {
                val dcr = lay.decorOutside[i]
                if (dcr.model == "lamp_street") {
                    r.sprite(dcr.x, 2.74f, dcr.z, 1.5f, 1.5f, soft, 1f, 0.86f, 0.55f, a * 0.85f)
                    r.sprite(dcr.x, 2.74f, dcr.z, 0.55f, 0.55f, soft, 1f, 0.97f, 0.85f, a)
                }
            }
            for (i in lay.decorInside.indices) {
                val dcr = lay.decorInside[i]
                if (dcr.model == "lamp_floor") r.sprite(dcr.x, 1.3f, dcr.z, 0.9f, 0.9f, soft, 1f, 0.9f, 0.62f, a * 0.8f)
            }
        }
        drawStations(r, w, cam, trim)
        drawCounter(r, w, trim)
        drawTables(r, w, seat)
        drawModel(r, mdl("trash_bin"), lay.trash.x, 0f, lay.trash.z, 0f, 1f, trim, outline = true)
        if (w.cfg.drive) drawDrive(r, w, trim)
        drawPiles(r, w)
        drawPads(r, w, cam, trim)
        drawTroubles(r, w, cam)
        drawPowerUps(r, w)
        drawCustomers(r, w, cam)
        drawCars(r, w)
        drawStaff(r, w)
        drawPlayer(r, w, style)
        if (showGuide) drawGuide(r, w)
    }

    private fun drawGround(r: Renderer, w: World, style: VisualStyle) {
        val lay = w.layout
        if (ground == null || groundLayout !== lay || groundStore !== style.store) {
            val b = MeshBuilder()
            val grass = if (style.store.groundColor >= 0) style.store.groundColor else when (style.store.ground) {
                "ground_sand" -> 0xEFC98A; "ground_snow" -> 0xE2ECF7; "ground_rock" -> 0x6B5550; "ground_metal" -> 0xA4AFBF
                "ground_sidewalk" -> 0xB7BDC8; else -> 0x7FC94E
            }
            val grassDark = darken(grass, 0.88f)
            val x0 = -24f; val x1 = lay.w + 24f; val z0 = -20f; val z1 = lay.d + 24f
            b.quadXZ4(x0, z0, x1, z1, -0.03f, grassDark, grass, grass, grassDark)
            // sidewalk and road along the front
            b.quadXZ(x0, lay.d.toFloat(), x1, lay.d + 2.3f, -0.01f, 0xC4C9D1)
            b.quadXZ(x0, lay.d + 2.3f, x1, lay.d + 2.45f, -0.005f, 0x9AA2AE)
            b.quadXZ(x0, lay.d + 2.45f, x1, lay.d + 7.2f, -0.02f, 0x4B5060)
            var xx = x0
            while (xx < x1) { b.quadXZ(xx, lay.d + 4.75f, xx + 1.2f, lay.d + 4.9f, -0.012f, 0xFFD45C); xx += 2.6f }
            // crosswalk at the door
            for (k in 0 until 6) b.quadXZ(lay.doorX - 1.3f + k * 0.5f, lay.d + 2.6f, lay.doorX - 1.05f + k * 0.5f, lay.d + 7.0f, -0.011f, 0xF2F2F2)
            // drive-thru lane
            lay.drive?.let {
                b.quadXZ(lay.w + 0.6f, -6f, lay.w + 3.2f, lay.d + 2.45f, -0.015f, 0x5E6370)
                var zz = -5.5f
                while (zz < lay.d + 2f) { b.quadXZ(lay.w + 1.85f, zz, lay.w + 1.95f, zz + 0.8f, -0.012f, 0xF2F2F2); zz += 1.8f }
            }
            // back path
            b.quadXZ(-1.2f, -1.6f, lay.w + 0.2f, -0.2f, -0.02f, 0xD9C7A8)
            ground = b.build("ground")
            groundLayout = lay; groundStore = style.store
        }
        M4.identity(m, 0)
        r.draw(ground!!, m, 0, shadow = false)
    }

    private fun darken(c: Int, k: Float): Int {
        val rr = (((c shr 16) and 255) * k).toInt(); val gg = (((c shr 8) and 255) * k).toInt(); val bb = ((c and 255) * k).toInt()
        return (rr shl 16) or (gg shl 8) or bb
    }

    private fun drawStations(r: Renderer, w: World, cam: Camera, trim: FloatArray) {
        for (s in w.stations) {
            if (!s.built) continue
            val model = mdl(s.spec.type.model)
            val pop = if (s.pop > 0f) 1f + sin((0.6f - s.pop) / 0.6f * PI.toFloat() * 3f) * s.pop * 0.35f else 1f
            val jiggle = if (s.working && s.tray < s.capacity) sin(time * 18f + s.index) * 0.008f else 0f
            drawModel(r, model, s.px, 0f, s.pz, 0f, pop, trim, flash = if (s.pop > 0.45f) (s.pop - 0.45f) * 4f else 0f, outline = true, sy = pop * (1f + jiggle))
            // level stars on top (sprites)
            if (s.level > 1) {
                for (k in 0 until s.level - 1) r.sprite(s.px - 0.18f + k * 0.36f, 1.75f, s.pz - 0.2f, 0.28f, 0.28f, atlas.uv("star"), 1f, 0.8f, 0.2f)
            }
            // output tray stack
            val out = model?.anchor("out") ?: floatArrayOf(0.45f, 0.98f, 0.25f)
            val im = mdl(foodModel[s.item.ordinal])
            var y = out[1]
            for (k in 0 until min(s.tray, 8)) {
                drawModel(r, im, s.px + out[0], y, s.pz + out[2], (k % 3) * 0.2f, 0.95f, shadow = k == 0)
                y += s.item.height * 0.92f
            }
            if (s.tray >= s.capacity) TextDraw.draw(r, atlas, cam, "MAX", s.px + out[0], y + 0.35f + sin(time * 5f) * 0.04f, s.pz + out[2], 0.34f, 1f, 0.35f, 0.25f)
            // cooking FX
            if (s.working && s.tray < s.capacity) {
                val sm = model?.anchor("smoke") ?: floatArrayOf(0f, 1.5f, -0.2f)
                if (particles.rnd() < 0.08f) particles.steam(s.px + sm[0], sm[1], s.pz + sm[2])
                // progress ring above the station
                r.sprite(s.px, 1.95f, s.pz + 0.1f, 0.42f, 0.42f, atlas.uv("ring_thick"), 0.23f, 0.16f, 0.23f, 0.5f)
                r.sprite(s.px, 1.95f, s.pz + 0.1f, 0.42f, 0.42f, atlas.uv("ring_thick"), 0.24f, 0.86f, 0.59f, 1f, fill = s.progress)
            }
            if (s.fire && particles.rnd() < 0.7f) particles.flame(s.px, 1.0f, s.pz)
        }
    }

    /** Item types shown on the counter racks, in menu order. */
    fun rackItems(w: World): List<Item> {
        if (racksFor !== w.cfg) { racksFor = w.cfg; racks.clear(); for (i in 0 until min(4, w.cfg.menu.size)) racks.add(w.cfg.menu[i]) }
        return racks
    }

    private fun drawCounter(r: Renderer, w: World, trim: FloatArray) {
        val c = w.layout.counter
        val model = mdl("counter")
        drawModel(r, model, c.x, 0f, c.z, 0f, 1f, trim, outline = true)
        drawModel(r, mdl("register_stand"), w.layout.cashierSpot.x, 0f, w.layout.cashierSpot.z, 0f, 1f, trim, shadow = false)
        val items = rackItems(w)
        for (k in items.indices) {
            val it = items[k]
            val a = model?.anchor(RACK[k]) ?: floatArrayOf(-0.9f + k * 0.55f, 0.97f, 0f)
            val im = mdl(foodModel[it.ordinal])
            var y = a[1]
            val n = min(w.counterRacks[it.ordinal], 10)
            for (j in 0 until n) { drawModel(r, im, c.x + a[0], y, c.z + a[2], j * 0.4f, 0.9f, shadow = j == 0); y += it.height * 0.88f }
        }
    }

    private fun drawDrive(r: Renderer, w: World, trim: FloatArray) {
        val dv = w.layout.drive ?: return
        if (!w.driveBuilt) return
        val model = mdl("drive_window")
        val pop = if (w.drivePop > 0f) 1.15f else 1f
        drawModel(r, model, dv.x, 0f, dv.z, dv.yaw, pop, trim, outline = true)
        val items = rackItems(w)
        for (k in 0 until min(2, items.size)) {
            val it = items[k]
            val a = model?.anchor(RACK[k]) ?: floatArrayOf(-0.3f + k * 0.6f, 1.0f, 0f)
            // rotate anchor by yaw (+90°: x' = z, z' = -x)
            val ax = a[2]; val az = -a[0]
            val im = mdl(foodModel[it.ordinal])
            var y = a[1]
            var extra = 0
            if (k == 1) for (j in 2 until items.size) extra += w.driveRacks[items[j].ordinal]
            val count = min(w.driveRacks[it.ordinal] + extra, 10)
            for (j in 0 until count) { drawModel(r, im, dv.x + ax, y, dv.z + az, j * 0.4f, 0.9f, shadow = false); y += it.height * 0.88f }
        }
    }

    private fun drawTables(r: Renderer, w: World, seat: FloatArray) {
        for (t in w.tables) {
            if (!t.built) continue
            val tm = mdl(if (t.slot.seats == 4) "table4" else "table2")
            val pop = if (t.pop > 0f) 1f + sin((0.6f - t.pop) / 0.6f * PI.toFloat() * 3f) * t.pop * 0.35f else 1f
            drawModel(r, tm, t.x, 0f, t.z, 0f, pop, seat, outline = true)
            for (i in 0 until t.slot.seats) {
                val sp = t.seatPos(i)
                drawModel(r, mdl("chair"), sp[0], 0f, sp[1], t.seatYaw(i) + PI.toFloat(), pop, seat)
            }
            // trays on the table
            for (k in 0 until t.trays) {
                val a = tm?.anchor("tray${k % t.slot.seats}") ?: floatArrayOf(0f, 0.64f, 0f)
                drawModel(r, mdl("food_tray"), t.x + a[0], a[1] + (k / t.slot.seats) * 0.08f, t.z + a[2], k * 0.7f)
            }
            if (t.trays > 0) r.sprite(t.x, 1.3f + sin(time * 4f) * 0.05f, t.z, 0.36f, 0.36f, atlas.uv("emo_wow"), 1f, 1f, 1f, 0.95f)
        }
    }

    private fun drawPiles(r: Renderer, w: World) {
        // The register pile is drawn 0.35 m further from the counter than where it is collected (the magnet radius
        // covers that easily): at its true spot the food racks on the counter and the cashier hid it.
        drawPile(r, w.counterPile, dz = -0.35f)
        w.drivePile?.let { drawPile(r, it) }
    }

    /**
     * Money waiting at the register: banded cash bricks (`food_cash`) in a 2×2 footprint, stacked up to 8 high
     * (the genre's "tall stack" reward; round 3 replaced the coin towers).
     */
    private fun drawPile(r: Renderer, pile: com.ngstudio.burgerready.game.CoinPile, dz: Float = 0f) {
        run {
            if (pile.visual <= 0) return
            val cash = mdl("food_cash")
            val bounce = if (pile.bounce > 0f) sin(pile.bounce * 20f) * pile.bounce * 0.4f else 0f
            val n = min(pile.visual, 32)
            for (k in 0 until n) {
                val tw = k % 4; val lvl = k / 4
                val ox = (tw % 2) * 0.235f - 0.1175f; val oz = (tw / 2) * 0.125f - 0.0625f
                // a little untidy, like a real stack of bricks
                val yaw = ((k * 37) % 7 - 3) * 0.025f
                drawModel(r, cash, pile.x + ox, 0.01f + lvl * 0.046f * (1f + bounce), pile.z + dz + oz, yaw, 1f, shadow = lvl == 0, emit = 0.04f)
            }
        }
    }

    private fun drawPads(r: Renderer, w: World, cam: Camera, trim: FloatArray) {
        for (pad in w.pads) {
            if (!pad.visible || pad.done) continue
            val kind = pad.spec.kind
            val col = when (kind) {
                PadKind.UPGRADE_STATION -> PAD_UPGRADE
                PadKind.HIRE -> PAD_HIRE
                PadKind.EXPAND -> PAD_EXPAND
                else -> PAD_BUILD
            }
            val big = if (kind == PadKind.EXPAND) 1.5f else 1f
            val model = mdl(when (kind) { PadKind.UPGRADE_STATION, PadKind.EXPAND -> "upgrade_pad"; PadKind.HIRE -> "staff_pad"; else -> "pad" }) ?: mdl("pad")
            val pop = if (pad.pop > 0f) 1f + sin((0.5f - pad.pop) / 0.5f * PI.toFloat() * 3f) * pad.pop * 0.5f else 1f
            val breathe = 1f + sin(time * 3f) * 0.025f
            val affordable = w.cash >= pad.remaining
            drawModel(r, model, pad.x, 0f, pad.z, 0f, pop * breathe * big, col, flash = if (pad.flash > 0f) 0.25f else 0f, shadow = false, emit = if (affordable) 0.12f + sin(time * 5f) * 0.06f else 0f)
            val fill = pad.paid.toFloat() / pad.spec.cost
            r.sprite(pad.x, 0.09f, pad.z, 1.35f * big, 1.35f * big, atlas.uv("ring_dash"), 1f, 1f, 1f, 0.9f, rot = time * 0.6f, mode = 1)
            if (fill > 0f) r.sprite(pad.x, 0.1f, pad.z, 1.3f * big, 1.3f * big, atlas.uv("ring_thick"), col[0], col[1], col[2], 1f, mode = 1, fill = fill)
            if (affordable) r.sprite(pad.x, 0.06f, pad.z, 2.2f * big, 2.2f * big, atlas.uv("light_pool"), col[0], col[1], col[2], 0.35f + sin(time * 4f) * 0.1f, mode = 1)
            // ghost preview of what will be built
            when (kind) {
                PadKind.BUILD_STATION -> { val s = w.stations[pad.spec.ref]; drawModel(r, mdl(s.spec.type.model), s.px, 0f, s.pz, 0f, 1f, trim, alpha = 0.28f + sin(time * 3f) * 0.06f) }
                PadKind.BUILD_TABLE -> { val t = w.tables[pad.spec.ref]; drawModel(r, mdl(if (t.slot.seats == 4) "table4" else "table2"), t.x, 0f, t.z, 0f, 1f, null, alpha = 0.3f) }
                PadKind.BUILD_DRIVE -> w.layout.drive?.let { drawModel(r, mdl("drive_window"), it.x, 0f, it.z, it.yaw, 1f, trim, alpha = 0.3f) }
                else -> {}
            }
            // price label, icon above it and the tag below it, spaced so they never touch
            val label = padLabelFor(pad)
            val big2 = if (kind == PadKind.EXPAND) 1.2f else 1f
            val hover = padHover(kind) + sin(time * 3f + pad.index) * 0.06f
            TextDraw.draw(r, atlas, cam, label, pad.x, hover, pad.z, 0.42f * big2,
                1f, if (affordable) 1f else 0.82f, if (affordable) 1f else 0.78f)
            val icon = when (kind) {
                PadKind.BUILD_STATION, PadKind.UPGRADE_STATION -> iconName[w.stations[pad.spec.ref].item.ordinal]
                PadKind.BUILD_TABLE -> "emo_happy"
                PadKind.BUILD_DRIVE -> "icon_bag"
                PadKind.HIRE -> "emo_love"
                PadKind.EXPAND -> "star"
            }
            r.sprite(pad.x, hover + 0.4f * big2 + 0.26f, pad.z, 0.5f, 0.5f, atlas.uv(icon), 1f, if (kind == PadKind.EXPAND) 0.8f else 1f, if (kind == PadKind.EXPAND) 0.2f else 1f)
            val tag = when (kind) {
                PadKind.UPGRADE_STATION -> "UPGRADE"
                PadKind.HIRE -> hireTag[pad.spec.ref / 10]
                PadKind.BUILD_DRIVE -> "DRIVE-THRU"
                PadKind.EXPAND -> "NEW RESTAURANT"
                else -> ""
            }
            if (tag.isNotEmpty()) TextDraw.draw(r, atlas, cam, tag, pad.x, hover - 0.31f * big2, pad.z, 0.24f * big2, col[0] * 0.6f + 0.4f, col[1] * 0.6f + 0.4f, col[2] * 0.6f + 0.4f)
        }
    }

    /** Height of a pad's price label; the guide pointer floats above the icon that sits on top of it. */
    private fun padHover(kind: PadKind) = if (kind == PadKind.EXPAND) 1.5625f else 1.25f

    private val PAD_UPGRADE = rgbF(0xFFB81C)
    private val PAD_HIRE = rgbF(0x1BA3D6)
    private val PAD_EXPAND = rgbF(0xFF5FA2)
    private val PAD_BUILD = rgbF(0x2ED58A)

    private fun drawTroubles(r: Renderer, w: World, cam: Camera) {
        for (t in w.troubles) {
            if (t.done) continue
            when (t.kind) {
                TroubleKind.SPILL -> {
                    r.sprite(t.x, 0.06f, t.z, 1.2f, 1.2f, atlas.uv("splat"), 0.86f, 0.18f, 0.16f, 0.95f, rot = t.x, mode = 1)
                    if (t.progress > 0f) r.sprite(t.x, 0.08f, t.z, 1.4f, 1.4f, atlas.uv("ring_thick"), 1f, 1f, 1f, 1f, mode = 1, fill = t.progress / 0.9f)
                    alert(r, t.x, 1.0f, t.z, "drop", 0.86f, 0.18f, 0.16f)
                }
                TroubleKind.FIRE -> {
                    val st = t.station!!
                    alert(r, st.px, 2.2f, st.pz, "fire", 1f, 1f, 1f)
                    if (t.progress > 0f) r.sprite(st.px, 2.2f, st.pz, 0.9f, 0.9f, atlas.uv("ring_thick"), 0.3f, 0.7f, 1f, 1f, fill = t.progress)
                }
                TroubleKind.JAM -> {
                    val st = t.station!!
                    alert(r, st.px, 2.2f, st.pz, "wrench", 0.4f, 0.45f, 0.5f)
                    if (particles.rnd() < 0.05f) particles.puff(st.px, 1.2f, st.pz, 3, 0.2f, 0.5f, 0.5f, 0.55f)
                    if (t.progress > 0f) r.sprite(st.px, 2.2f, st.pz, 0.9f, 0.9f, atlas.uv("ring_thick"), 1f, 0.8f, 0.2f, 1f, fill = t.progress / 1.4f)
                }
                TroubleKind.SLEEPY -> { val c = t.customer ?: continue; alert(r, c.x, 1.9f, c.z, "zz", 0.6f, 0.7f, 1f) }
                TroubleKind.RACCOON -> {
                    walkPose(pose, t.walkPhase, hypot(t.vx, t.vz), t.stolen.isNotEmpty(), 7)
                    drawCharacter(r, mdl("char_raccoon"), t.x, t.z, t.yaw, pose, RACCOON_TINT, 0.85f)
                    drawHeldStack(r, mdl("char_raccoon"), t.stolen, 0f)
                    if (t.phase < 2) alert(r, t.x, 1.6f, t.z, "exclaim", 1f, 1f, 1f)
                }
            }
        }
    }

    private fun alert(r: Renderer, x: Float, y: Float, z: Float, icon: String, cr: Float, cg: Float, cb: Float) {
        val b = 1f + sin(time * 8f) * 0.08f
        r.sprite(x, y, z, 0.62f * b, 0.62f * b, atlas.uv("badge"), 1f, 1f, 1f)
        r.sprite(x, y, z, 0.44f * b, 0.44f * b, atlas.uv(icon), cr, cg, cb)
    }

    private fun drawPowerUps(r: Renderer, w: World) {
        for (p in w.powerUps) {
            val bob = 0.55f + sin(p.t * 4f) * 0.12f
            val fade = if (p.ttl < 2f) (if ((p.ttl * 6f).toInt() % 2 == 0) 0.4f else 1f) else 1f
            val icon = puIcon[p.kind.ordinal]
            val c = puRgb[p.kind.ordinal]
            r.sprite(p.x, 0.07f, p.z, 1.1f, 1.1f, atlas.uv("light_pool"), c[0], c[1], c[2], 0.6f * fade, mode = 1)
            r.sprite(p.x, bob, p.z, 0.7f, 0.7f, atlas.uv("badge"), 1f, 1f, 1f, fade)
            r.sprite(p.x, bob, p.z, 0.5f, 0.5f, atlas.uv(icon), c[0], c[1], c[2], fade, rot = sin(p.t * 3f) * 0.3f)
        }
    }

    private fun drawCustomers(r: Renderer, w: World, cam: Camera) {
        for (c in w.customers) {
            val model = mdl(charModel[c.species.ordinal])
            val shirt = shirtRgb[c.shirt % shirtRgb.size]
            val carrying = c.holding.isNotEmpty() && c.state != CState.EATING
            walkPose(pose, c.walkPhase, c.speed, carrying, c.id)
            var y = 0f
            when (c.state) {
                CState.EATING, CState.SLEEPING -> {
                    pose.reset(); pose.legL = 1.45f; pose.legR = 1.45f
                    if (c.state == CState.EATING) { val e = sin(c.anim * 9f); pose.armL = -1.2f + e * 0.3f; pose.armR = -1.2f - e * 0.3f; pose.headTilt = e * 0.08f }
                    else { pose.headTilt = 0.35f; pose.blink = 0.1f; pose.armL = -0.3f; pose.armR = -0.3f }
                    y = 0.06f
                }
                CState.ORDERING, CState.QUEUE -> if (c.speed < 0.2f) { pose.bob = sin(time * 3f + c.id) * 0.01f; if (c.angry) pose.squashY = 1f }
                else -> {}
            }
            val hat = if (c.vip) mdl("hat_crown") else if (c.critic) mdl("hat_beret") else null
            val hatTint = if (c.critic) CRITIC_HAT else null
            blob(r, c.x, c.z, 0.75f)
            drawCharacter(r, model, c.x, c.z, c.yaw, pose, shirt, 0.92f, y, hat = hat, hatTint = hatTint, hatLift = hatLiftFor(c.species))
            if (carrying) drawHeldStack(r, model, c.holding, 0f, 0.92f, max = 2)
            // emotes
            if (c.emote != 0) {
                val icon = when (c.emote) { 1 -> "emo_happy"; 2 -> "emo_angry"; 3 -> "emo_love"; 4 -> "emo_sleep"; else -> "emo_wow" }
                r.sprite(c.x, 1.75f + sin(time * 6f) * 0.04f, c.z, 0.5f, 0.5f, atlas.uv(icon))
            }
            // Order bubbles: the guest being served, plus a smaller one for the next in line (a third bubble stacked
            // on top of the other two and hid the queue).
            if ((c.state == CState.ORDERING || (c.state == CState.QUEUE && c.queueIdx <= 1)) && c.speed < 0.3f) orderBubble(r, cam, c.x, c.z, c.order, c.got, c.patience / c.maxPatience, c.queueIdx == 0, c.vip || c.critic)
        }
    }

    private val bubbleItems = ArrayList<Int>()

    fun orderBubble(r: Renderer, cam: Camera, x: Float, z: Float, order: IntArray, got: IntArray, patience: Float, front: Boolean, special: Boolean) {
        bubbleItems.clear()
        for (i in order.indices) if (order[i] - got[i] > 0) bubbleItems.add(i)
        if (bubbleItems.isEmpty()) return
        val n = min(3, bubbleItems.size)
        val s = if (front) 1f else 0.68f
        val y = (if (front) 1.95f else 1.78f) + sin(time * 3f + x) * 0.03f
        val w = (0.62f + n * 0.5f) * s
        r.sprite(x, y, z, w, 0.82f * s, atlas.uv("bubble"), if (special) 1f else 1f, if (special) 0.93f else 1f, if (special) 0.7f else 1f, 1f)
        for (k in 0 until n) {
            val i = bubbleItems[k]
            val off = (k - (n - 1) / 2f) * 0.5f * s
            val px = x + cam.rightX * off; val pz = z + cam.rightZ * off; val py = y + 0.07f * s + cam.rightY * off
            r.sprite(px - cam.rightX * 0.06f, py, pz - cam.rightZ * 0.06f, 0.44f * s, 0.44f * s, atlas.uv(iconName[i]))
            val left = order[i] - got[i]
            if (left > 1) TextDraw.draw(r, atlas, cam, if (left < countStr.size) countStr[left] else "9", px + cam.rightX * 0.16f * s, py - 0.13f * s, pz + cam.rightZ * 0.16f * s, 0.3f * s)
        }
        if (patience < 0.65f) {
            val bw = 0.9f * s
            val by = y - 0.5f * s
            r.sprite(x, by, z, bw, 0.14f * s, atlas.uv("bar_bg"))
            val col = if (patience > 0.35f) PATIENCE_MID else PATIENCE_LOW
            val fw = (bw - 0.04f) * patience.coerceIn(0f, 1f)
            val shift = (bw - 0.04f - fw) / 2f
            r.sprite(x - cam.rightX * shift, by - cam.rightY * shift, z - cam.rightZ * shift, fw, 0.1f * s, atlas.uv("bar_fill"), col[0], col[1], col[2])
        }
    }

    private fun drawCars(r: Renderer, w: World) {
        for (car in w.cars) {
            val model = mdl(if (car.van) "van" else "car") ?: mdl("car")
            val col = carRgb[car.color % carRgb.size]
            if (model == null) continue
            M4.trs(root, 0, car.x, 0f, car.z, car.yaw, 1f, 1f, 1f)
            val spin = car.z * 2.2f
            for (p in model.parts) {
                val mesh = p.mesh ?: continue
                if (p.name == "wheels") M4.trsEuler(local, 0, p.localX, p.localY, p.localZ, 0f, 0f, 0f, 1f, 1f, 1f)
                else M4.trsEuler(local, 0, p.localX, p.localY + if (car.state == CarState.ORDERING) sin(time * 9f) * 0.005f else 0f, p.localZ, 0f, 0f, 0f, 1f, 1f, 1f)
                M4.mul(m, 0, root, 0, local, 0)
                r.draw(mesh, m, 0, col[0], col[1], col[2], outline = true)
            }
            if (car.state == CarState.ORDERING || (car.state == CarState.QUEUE && car.queueIdx == 1)) {
                // reuse the order bubble above the car roof
            }
        }
    }

    fun carBubbles(r: Renderer, w: World, cam: Camera) {
        for (car in w.cars) if (car.state == CarState.ORDERING) orderBubble(r, cam, car.x, car.z, car.order, car.got, car.patience / car.maxPatience, true, false)
    }

    private fun drawStaff(r: Renderer, w: World) {
        for (s in w.staff) {
            val model = mdl(charModel[s.species.ordinal])
            walkPose(pose, s.walkPhase, s.speed, s.stack.isNotEmpty(), s.hashCode() and 31)
            val pop = if (s.pop > 0f) 1f + s.pop * 0.3f else 1f
            val shirt = staffRgb[s.role.ordinal]
            blob(r, s.x, s.z, 0.78f)
            drawCharacter(r, model, s.x, s.z, s.yaw, pose, shirt, 0.95f * pop, hat = mdl("hat_cap"), hatTint = shirt, hatLift = hatLiftFor(s.species))
            drawHeldStack(r, model, s.stack, 0f, 0.95f)
            if (s.role == StaffRole.CASHIER && s.state == SState.AT_POST && sin(time * 2f + s.x) > 0.95f) r.sprite(s.x, 1.8f, s.z, 0.35f, 0.35f, atlas.uv("emo_happy"))
        }
    }

    private fun drawPlayer(r: Renderer, w: World, style: VisualStyle) {
        val p = w.player
        val model = mdl(charModel[style.playerSpecies.ordinal])
        walkPose(pose, p.walkPhase, p.speed, p.stack.isNotEmpty(), 0)
        if (w.espressoT > 0f && p.moving && particles.rnd() < 0.5f) particles.sparkles(p.x, 0.4f, p.z, 1, 0.9f, 0.6f, 0.3f, 0.2f)
        if (p.moving && particles.rnd() < 0.12f) particles.dust(p.x, p.z)
        blob(r, p.x, p.z, 0.85f)
        drawCharacter(r, model, p.x, p.z, p.yaw, pose, apronC, 1f, hat = playerHat?.let { mdl(it) }, hatTint = HAT_RED, hatLift = hatLiftFor(style.playerSpecies))
        drawHeldStack(r, model, p.stack, p.sway, 1f)
        if (p.stack.size >= w.capacity) {
            var h = (model?.anchor("hand")?.get(1) ?: 0.55f) + 0.35f
            for (i in p.stack.indices) h += p.stack[i].height
            TextDraw.draw(r, atlas, r.camera, "MAX", p.x, h + sin(time * 6f) * 0.04f, p.z, 0.32f, 1f, 0.35f, 0.25f)
        }
        // magnet aura
        if (w.magnetT > 0f) r.sprite(p.x, 0.07f, p.z, 9f, 9f, atlas.uv("ring_dash"), 0.95f, 0.3f, 0.25f, 0.5f, rot = time, mode = 1)
        // pet follows
        style.petModel?.let { pm ->
            val tx = p.x - sin(p.yaw) * 0.9f + 0.5f; val tz = p.z - cos(p.yaw) * 0.9f
            val dx = tx - petPos[0]; val dz = tz - petPos[1]
            val d = hypot(dx, dz)
            if (d > 6f) { petPos[0] = tx; petPos[1] = tz }
            else if (d > 0.3f) { petPos[0] += dx * 0.08f; petPos[1] += dz * 0.08f; petPos[2] = kotlin.math.atan2(dx, dz); petPos[3] += 0.4f }
            val hop = if (d > 0.3f) abs(sin(petPos[3])) * 0.12f else 0f
            drawModel(r, mdl(pm), petPos[0], hop, petPos[1], petPos[2], 1f, outline = true)
        }
    }

    private var guidePath: FloatArray? = null
    private var guideT = 0f
    private var guideGoalX = 0f; private var guideGoalZ = 0f

    private fun drawGuide(r: Renderer, w: World) {
        if (!w.guidanceTarget(guideTarget)) return
        val g = guideTarget
        val p = w.player
        if (hypot(g[0] - p.x, g[1] - p.z) < 1.2f) return
        guideT -= 1f / 60f
        if (guidePath == null || guideT <= 0f || hypot(g[0] - guideGoalX, g[1] - guideGoalZ) > 0.3f) {
            guidePath = w.nav.path(p.x, p.z, g[0], g[1]); guideT = 0.4f; guideGoalX = g[0]; guideGoalZ = g[1]
        }
        val path = guidePath ?: return
        // walk along the polyline from the player, dropping chevrons every 0.8 m
        var px = p.x; var pz = p.z
        val shift = (time * 1.6f) % 1f
        var carry = 0.9f + shift * 0.8f
        var placed = 0
        var i = 0
        while (i * 2 < path.size && placed < 12) {
            val tx = path[i * 2]; val tz = path[i * 2 + 1]
            val dx = tx - px; val dz = tz - pz
            val seg = hypot(dx, dz)
            if (seg < 0.001f) { i++; continue }
            val ux = dx / seg; val uz = dz / seg
            val yaw = kotlin.math.atan2(ux, -uz)
            var s = carry
            while (s < seg && placed < 12) {
                val a = (1f - placed / 12f).coerceIn(0.3f, 1f)
                r.sprite(px + ux * s, 0.08f, pz + uz * s, 0.6f, 0.6f, atlas.uv("arrow"), 0.24f, 0.86f, 0.59f, a * 0.95f, rot = -yaw, mode = 1)
                s += 0.8f; placed++
            }
            carry = s - seg
            px = tx; pz = tz; i++
        }
        // marker bouncing over the target; over a pad it floats above the pad's icon instead of covering the price
        var baseY = 1.3f
        for (i in w.pads.indices) {
            val pad = w.pads[i]
            if (pad.visible && !pad.done && abs(pad.x - g[0]) < 0.05f && abs(pad.z - g[1]) < 0.05f) {
                val k = if (pad.spec.kind == PadKind.EXPAND) 1.2f else 1f
                baseY = padHover(pad.spec.kind) + 0.4f * k + 0.26f + 0.62f; break
            }
        }
        r.sprite(g[0], baseY + abs(sin(time * 4f)) * 0.3f, g[1], 0.55f, 0.66f, atlas.uv("pointer"), 1f, 0.56f, 0.69f)
    }

    fun formatShort(v: Long): String = when {
        v >= 1_000_000_000_000_000L -> String.format("%.1fQa", v / 1e15).replace(".0Qa", "Qa")
        v >= 1_000_000_000_000L -> String.format("%.1fT", v / 1e12).replace(".0T", "T")
        v >= 1_000_000_000L -> String.format("%.1fB", v / 1e9).replace(".0B", "B")
        v >= 1_000_000L -> String.format("%.1fM", v / 1_000_000f)
        v >= 10_000L -> String.format("%.1fk", v / 1000f).replace(".0k", "k")
        else -> v.toString()
    }
}
