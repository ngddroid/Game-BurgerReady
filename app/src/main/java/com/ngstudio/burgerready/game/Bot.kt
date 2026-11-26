package com.ngstudio.burgerready.game

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A scripted player used to tune and validate levels. It only uses the same joystick input a human has
 * ([World.inputX]/[World.inputZ]) and plans with the same nav grid. `skill` scales its reaction speed
 * and efficiency (1.0 = expert, 0.5 = novice).
 */
class Bot(private val w: World, private val skill: Float = 1f) {
    private var goalX = 0f; private var goalZ = 0f
    private var path: FloatArray? = null
    private var pathIdx = 0
    private var replanT = 0f
    private var dwell = 0f
    private var thinkT = 0f
    private var mode = 0
    private val rng = Rng(w.cfg.seed + 99)

    fun update(dt: Float) {
        thinkT -= dt
        replanT -= dt
        if (thinkT <= 0f) {
            thinkT = 0.25f / max(0.3f, skill)
            decide()
        }
        steer(dt)
    }

    private fun setGoal(x: Float, z: Float, m: Int, hold: Float = 0f) {
        if (hypot(x - goalX, z - goalZ) > 0.3f || path == null || replanT <= 0f) {
            goalX = x; goalZ = z
            path = w.nav.path(w.player.x, w.player.z, x, z)
            pathIdx = 0
            replanT = 1.2f
        }
        mode = m
        dwell = hold
    }

    private fun decide() {
        val p = w.player
        val lay = w.layout
        val cap = w.capacity
        // 0) Troubles that block production / steal.
        w.troubles.firstOrNull { !it.done && (it.kind == TroubleKind.FIRE || it.kind == TroubleKind.JAM || it.kind == TroubleKind.RACCOON) }?.let { t ->
            if (t.kind == TroubleKind.RACCOON) setGoal(t.x, t.z, 9) else setGoal(t.station!!.pickX, t.station!!.pickZ, 9)
            return
        }
        // 1) Pads we can afford outright (or are standing on).
        val pad = w.visiblePads().filter { w.cash >= it.remaining || (w.cash > 0 && near(p.x, p.z, it.x, it.z, 0.7f)) }.minByOrNull { it.index }
        if (pad != null && p.stack.isEmpty()) { setGoal(pad.x, pad.z, 1); return }
        // 2) Carrying trays → trash.
        if (p.stack.isNotEmpty() && p.stack[0] == Item.TRAY) { setGoal(lay.trashApproach.x, lay.trashApproach.z, 2); return }
        // 3) Carrying food → wherever it is needed (counter first), otherwise stock the counter.
        if (p.stack.isNotEmpty()) {
            var counterUse = 0; var driveUse = 0
            for (it in p.stack) { if (need(it, false) > 0) counterUse++; if (need(it, true) > 0) driveUse++ }
            val toDrive = w.driveBuilt && lay.driveCashier != null && driveUse > counterUse
            if (toDrive) setGoal(lay.driveCashier!!.x, lay.driveCashier!!.z, 3)
            else setGoal(lay.cashierSpot.x - 0.05f, lay.cashierSpot.z, 3)
            if (p.stack.size < cap && skill > 0.7f) {
                // top up from a nearby station whose product is needed
                val st = w.stations.filter { it.built && it.tray > 0 && (need(it.item, false) + need(it.item, true)) > 0 }
                    .minByOrNull { hypot(it.pickX - p.x, it.pickZ - p.z) }
                if (st != null && hypot(st.pickX - p.x, st.pickZ - p.z) < 1.6f) setGoal(st.pickX, st.pickZ, 4)
            }
            return
        }
        // 4) Front customer waiting with everything available and no cashier → serve.
        val hasCashier = w.staff.any { it.role == StaffRole.CASHIER && it.post == 0 }
        val front = w.queue.firstOrNull()
        if (!hasCashier && front != null && front.state == CState.ORDERING && covered(front.order, front.got, w.counterRacks)) {
            setGoal(lay.cashierSpot.x, lay.cashierSpot.z, 5); return
        }
        val car = w.carQueue.firstOrNull()
        if (car != null && car.state == CarState.ORDERING && w.staff.none { it.role == StaffRole.CASHIER && it.post == 1 } &&
            covered(car.order, car.got, w.driveRacks) && (front == null || !covered(front.order, front.got, w.counterRacks) || hasCashier)) {
            setGoal(lay.driveCashier!!.x, lay.driveCashier!!.z, 5); return
        }
        // 5) Collect coins when we need cash for the next pad, or the pile is big.
        val nextPad = w.visiblePads().minByOrNull { it.index }
        val pileTotal = w.counterPile.amount + (w.drivePile?.amount ?: 0)
        if (pileTotal > 0 && (nextPad != null && w.cash < nextPad.remaining && w.cash + pileTotal >= min(nextPad.remaining, 1)) || pileTotal > 120) {
            val pile = if (w.counterPile.amount >= (w.drivePile?.amount ?: 0)) w.counterPile else w.drivePile!!
            setGoal(pile.x, pile.z, 6); return
        }
        // 6) Fetch the most needed product.
        val want = mostNeeded()
        if (want != null) { setGoal(want.pickX, want.pickZ, 7); return }
        // 7) Clean tables if diners are coming.
        val dirty = w.tables.filter { it.built && it.trays > 0 }.minByOrNull { hypot(it.x - p.x, it.z - p.z) }
        if (dirty != null && w.staff.none { it.role == StaffRole.CLEANER }) { setGoal(dirty.x, dirty.z + 0.95f, 8); return }
        // 8) Spills on the floor.
        w.troubles.firstOrNull { !it.done && it.kind == TroubleKind.SPILL }?.let { setGoal(it.x, it.z, 9); return }
        w.troubles.firstOrNull { !it.done && it.kind == TroubleKind.SLEEPY }?.let { t -> t.customer?.let { setGoal(it.x, it.z + 0.8f, 9) }; return }
        // 9) Power-ups.
        w.powerUps.firstOrNull()?.let { setGoal(it.x, it.z, 10); return }
        // 10) Pre-stock: grab from the fullest station.
        val st = w.stations.filter { it.built && it.tray > 0 }.maxByOrNull { it.tray }
        if (st != null) { setGoal(st.pickX, st.pickZ, 7); return }
        if (pileTotal > 0) { setGoal(w.counterPile.x, w.counterPile.z, 6); return }
        setGoal(lay.cashierSpot.x, lay.cashierSpot.z, 5)
    }

    private fun covered(order: IntArray, got: IntArray, racks: IntArray): Boolean {
        for (i in order.indices) if (order[i] - got[i] > racks[i]) return false
        return true
    }

    private fun demand(drive: Boolean): Int {
        var d = 0
        if (drive) for (c in w.carQueue) d += c.remaining() else for (c in w.queue.take(5)) d += c.remaining()
        return d
    }

    /** Units of [item] still missing for the visible customers (counter) or cars (drive). */
    private fun need(item: Item, drive: Boolean): Int {
        var n = 0
        if (drive) { for (c in w.carQueue) n += max(0, c.order[item.ordinal] - c.got[item.ordinal]); n -= w.driveRacks[item.ordinal] }
        else { for (c in w.queue.take(6)) n += max(0, c.order[item.ordinal] - c.got[item.ordinal]); n -= w.counterRacks[item.ordinal] }
        return n
    }

    private fun mostNeeded(): Station? {
        var best: Station? = null; var bestScore = -1e9f
        for (s in w.stations) {
            if (!s.built || s.tray <= 0) continue
            val nd = max(0, need(s.item, false)) + (if (w.driveBuilt) max(0, need(s.item, true)) else 0)
            val dist = hypot(s.pickX - w.player.x, s.pickZ - w.player.z)
            val score = nd * 4f + s.tray * 0.6f - dist * 0.35f
            if (nd > 0 && score > bestScore) { bestScore = score; best = s }
        }
        return best
    }

    private fun near(x: Float, z: Float, tx: Float, tz: Float, r: Float) = hypot(x - tx, z - tz) < r

    private var lastX = 0f; private var lastZ = 0f; private var stuckT = 0f

    private fun steer(dt: Float) {
        val p = w.player
        // Unstick: if we push but barely move for a while, replan.
        if (hypot(w.inputX, w.inputZ) > 0.3f && hypot(p.x - lastX, p.z - lastZ) < 0.01f) stuckT += dt else stuckT = 0f
        lastX = p.x; lastZ = p.z
        if (stuckT > 0.6f) { stuckT = 0f; path = w.nav.path(p.x, p.z, goalX, goalZ); pathIdx = 0 }
        val pth = path
        if (pth == null || pathIdx * 2 >= pth.size) { w.inputX = 0f; w.inputZ = 0f; return }
        var tx = pth[pathIdx * 2]; var tz = pth[pathIdx * 2 + 1]
        var d = hypot(tx - p.x, tz - p.z)
        if (d < 0.18f) {
            pathIdx++
            if (pathIdx * 2 >= pth.size) { w.inputX = 0f; w.inputZ = 0f; return }
            tx = pth[pathIdx * 2]; tz = pth[pathIdx * 2 + 1]; d = hypot(tx - p.x, tz - p.z)
        }
        val slow = if (pathIdx * 2 + 2 >= pth.size) min(1f, d / 0.6f).coerceAtLeast(0.25f) else 1f
        val k = skill.coerceIn(0.3f, 1f) * slow
        w.inputX = (tx - p.x) / d * k
        w.inputZ = (tz - p.z) / d * k
        // Novice bots occasionally hesitate.
        if (skill < 0.8f && rng.chance(0.004f)) thinkT = 0.8f
    }
}

/** Runs a whole shift headlessly. */
object Simulator {
    fun run(cfg: LevelConfig, stats: PlayerStats, skill: Float = 1f, dt: Float = 1f / 20f, maxExtra: Float = 300f): World {
        val w = World(cfg, stats)
        val bot = Bot(w, skill)
        val limit = cfg.shiftSeconds + (if (cfg.overtime) maxExtra else 0f)
        while (!w.finished && w.time < limit) {
            bot.update(dt)
            w.update(dt)
        }
        return w
    }
}
