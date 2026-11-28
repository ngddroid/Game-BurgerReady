package com.ngstudio.burgerready.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ===================================================================================== events
enum class Ev {
    PICKUP, DROP, COIN_COLLECT, PAY_TICK, BUILT, UPGRADED, HIRED, SERVED, ORDER, ANGRY, EAT, TRASH,
    COMBO_UP, COMBO_BREAK, RUSH_START, RUSH_END, TROUBLE, TROUBLE_FIXED, POWERUP, VIP, CAR_HORN,
    EXPANDED, FOCUS, ITEM_FLY, COIN_FLY, STEP, CRITIC_HAPPY, STOLEN, SHOO, CRITIC
}

/** A gameplay event for audio/VFX/UI. [a],[b] are optional extra values (pitch, amount, ids...). */
class GameEvent(val type: Ev, val x: Float = 0f, val y: Float = 0f, val z: Float = 0f, val a: Float = 0f, val b: Float = 0f,
                val x2: Float = 0f, val y2: Float = 0f, val z2: Float = 0f, val text: String = "", val item: Item? = null)

// ===================================================================================== entities
open class Actor {
    var x = 0f; var z = 0f; var yaw = 0f
    var vx = 0f; var vz = 0f
    var path: FloatArray? = null
    var pathIdx = 0
    var walkPhase = 0f
    var anim = 0f          // generic animation timer
    var pop = 0f           // spawn squash/pop timer (counts down)
    val speed get() = hypot(vx, vz)

    /** Follow [path]; returns true when the destination has been reached. */
    fun followPath(dt: Float, spd: Float): Boolean {
        val p = path ?: return true
        if (pathIdx * 2 >= p.size) { vx = 0f; vz = 0f; return true }
        val tx = p[pathIdx * 2]; val tz = p[pathIdx * 2 + 1]
        val dx = tx - x; val dz = tz - z
        val d = hypot(dx, dz)
        val step = spd * dt
        if (d <= step || d < 0.02f) {
            x = tx; z = tz; pathIdx++
            if (pathIdx * 2 >= p.size) { vx = 0f; vz = 0f; path = null; return true }
            return false
        }
        vx = dx / d * spd; vz = dz / d * spd
        x += vx * dt; z += vz * dt
        turnTo(atan2(vx, vz), dt)
        walkPhase += dt * spd * 3.2f
        return false
    }

    fun turnTo(target: Float, dt: Float, rate: Float = 12f) {
        var d = target - yaw
        while (d > Math.PI) d -= (2 * Math.PI).toFloat()
        while (d < -Math.PI) d += (2 * Math.PI).toFloat()
        yaw += d * min(1f, dt * rate)
    }
}

class Player : Actor() {
    val stack = ArrayList<Item>()
    var sway = 0f; var swayV = 0f
    var moving = false
    var pickT = 0f; var dropT = 0f
    var padDwell = 0f
}

class Station(val index: Int, val spec: StationSpec, val px: Float, val pz: Float) {
    var built = false
    var level = 1
    var tray = 0
    var progress = 0f
    var fire = false
    var jammed = false
    var fixProgress = 0f
    var pop = 0f
    var cookAnim = 0f
    val item get() = spec.item
    val capacity get() = when (level) { 1 -> 6; 2 -> 10; else -> 14 }
    val speedMul get() = when (level) { 1 -> 1f; 2 -> 1.4f; else -> 1.8f }
    val pickX get() = px
    val pickZ get() = pz + 0.95f
    val working get() = built && !fire && !jammed
}

class Table(val index: Int, val slot: TableSlot) {
    var built = false
    var pop = 0f
    var trays = 0
    val seats = arrayOfNulls<Customer>(slot.seats)
    val x get() = slot.p.x
    val z get() = slot.p.z
    fun seatPos(i: Int): FloatArray {
        val off = 0.78f
        return when {
            slot.seats == 2 -> if (i == 0) floatArrayOf(x - off, z) else floatArrayOf(x + off, z)
            else -> when (i) { 0 -> floatArrayOf(x - off - 0.1f, z); 1 -> floatArrayOf(x + off + 0.1f, z); 2 -> floatArrayOf(x, z - off - 0.1f); else -> floatArrayOf(x, z + off + 0.1f) }
        }
    }
    fun seatYaw(i: Int): Float { val p = seatPos(i); return atan2(x - p[0], z - p[1]) }
    val dirty get() = trays > 0
    fun freeSeat(): Int { if (!built || dirty) return -1; for (i in seats.indices) if (seats[i] == null) return i; return -1 }
}

enum class CState { WALK_IN, QUEUE, ORDERING, TO_SEAT, EATING, SLEEPING, LEAVING, GONE }

class Customer(val id: Int, val species: Species, val shirt: Int, val order: IntArray, val vip: Boolean, val critic: Boolean, val maxPatience: Float) : Actor() {
    var state = CState.WALK_IN
    var queueIdx = -1
    var patience = maxPatience
    val got = IntArray(Item.entries.size)
    val holding = ArrayList<Item>()
    var takeT = 0f
    var table: Table? = null
    var seat = -1
    var eatT = 0f
    var angry = false
    var emote = 0       // 0 none, 1 happy, 2 angry, 3 love, 4 sleep, 5 wow
    var emoteT = 0f
    var willSleep = false
    var paid = false
    /** Reused one-point path to the customer's queue slot (no per-frame allocation). */
    val slotPath = FloatArray(2)
    fun remaining(): Int { var r = 0; for (i in order.indices) r += max(0, order[i] - got[i]); return r }
    fun orderTotal(): Int { var r = 0; for (i in order.indices) r += order[i]; return r }
    fun orderValue(): Int { var v = 0; for (i in order.indices) v += order[i] * Item.entries[i].price; return v }
}

enum class CarState { ARRIVING, QUEUE, ORDERING, LEAVING, GONE }

class Car(val id: Int, val color: Int, val order: IntArray, val van: Boolean) : Actor() {
    var state = CarState.ARRIVING
    var queueIdx = -1
    val got = IntArray(Item.entries.size)
    var takeT = 0f
    var patience = 0f
    var maxPatience = 0f
    fun remaining(): Int { var r = 0; for (i in order.indices) r += max(0, order[i] - got[i]); return r }
    fun orderValue(): Int { var v = 0; for (i in order.indices) v += order[i] * Item.entries[i].price; return v }
}

enum class SState { ENTER, IDLE, TO_PICK, TO_DROP, TO_TABLE, TO_TRASH, AT_POST }

class Staff(val role: StaffRole, val post: Int, val species: Species) : Actor() {
    var state = SState.ENTER
    val stack = ArrayList<Item>()
    var target: Station? = null
    var targetTable: Table? = null
    var actT = 0f
    var idleT = 0f
}

class Pad(val spec: PadSpec, val x: Float, val z: Float) {
    val index get() = spec.id
    var paid = 0L
    var done = false
    var visible = false
    var pop = 0f
    var flash = 0f
    val remaining: Long get() = spec.cost - paid
}

class CoinPile(val x: Float, val z: Float) {
    var amount = 0L
    var visual = 0     // number of coin meshes shown
    var bounce = 0f
}

class Trouble(val kind: TroubleKind, var x: Float, var z: Float) {
    var station: Station? = null
    var customer: Customer? = null
    var progress = 0f
    var age = 0f
    var done = false
    // raccoon
    var vx = 0f; var vz = 0f; var yaw = 0f
    var stolen = ArrayList<Item>()
    var phase = 0        // 0 approach, 1 stealing, 2 fleeing
    var walkPhase = 0f
    var path: FloatArray? = null
    var pathIdx = 0
}

class PowerUp(val kind: PowerUpKind, val x: Float, val z: Float) {
    var ttl = 12f
    var t = 0f
}

// ===================================================================================== world
/**
 * One restaurant, simulated continuously. [saved] restores everything bought earlier (built stations,
 * tables, upgrades, staff, partial pad payments); [startCash] is the player's wallet (Bucks).
 * There is no timer and no fail state; the only "end" is buying the EXPAND pad ([expanded]).
 */
class World(
    val cfg: RestaurantConfig,
    stats: PlayerStats,
    val playerSpecies: Species = Species.CORGI,
    startCash: Long = 0L,
    saved: RestaurantProgress? = null,
) {
    /** Kitchen upgrades can be bought mid-session; the GL thread swaps in the new stats. */
    var stats: PlayerStats = stats
    val layout: Layout = Layouts.get(cfg.layoutId)
    val nav = NavGrid(layout)
    private val rng = Rng(cfg.seed)
    private val troubleRng = Rng(cfg.seed xor 0x5DEECE66DL)

    // ---------------------------------------------------------------- state
    var time = 0f
    var cash: Long = startCash
    /** True once the EXPAND pad is bought (the next restaurant opens). */
    var expanded = false; private set
    var combo = 1f
    var bestCombo = 1f
    private var comboIdle = 0f

    // session counters (cumulative since the World was created; the UI commits deltas)
    var revenue = 0L
    var served = 0
    var walkouts = 0
    var criticsServed = 0
    val itemsServed = IntArray(Item.entries.size)
    val speciesServed = HashSet<Species>()
    var vipsServed = 0
    val powerUpsUsed = HashSet<PowerUpKind>()
    var carsServed = 0
    val troublesFixed = HashSet<TroubleKind>()
    var troublesFixedCount = 0
    var traysCleared = 0
    var rushesDone = 0
    /** Rush hours that ended without a single walkout. */
    var flawlessRushes = 0
    private var walkoutsAtRush = 0
    var padsBought = 0
    /** Bumped whenever pad progress changes, so the UI knows when to save. */
    var progressVersion = 0; private set

    val player = Player()
    val stations = ArrayList<Station>()
    val tables = ArrayList<Table>()
    val counterRacks = IntArray(Item.entries.size)
    val driveRacks = IntArray(Item.entries.size)
    var driveBuilt = false
    var drivePop = 0f
    val customers = ArrayList<Customer>()
    val queue = ArrayList<Customer>()
    val cars = ArrayList<Car>()
    val carQueue = ArrayList<Car>()
    val staff = ArrayList<Staff>()
    val pads = ArrayList<Pad>()
    val counterPile: CoinPile
    val drivePile: CoinPile?
    val troubles = ArrayList<Trouble>()
    val powerUps = ArrayList<PowerUp>()
    val events = ArrayList<GameEvent>()

    var espressoT = 0f; var magnetT = 0f; var spatulaT = 0f
    var rushT = 0f; var rushMult = 1f

    // input (world-space direction, magnitude 0..1)
    var inputX = 0f; var inputZ = 0f

    private var nextArrivalT = 1.2f
    private var nextCriticT = 200f
    private var nextRushT = 150f
    private var nextCarT = 6f
    private var nextTroubleT = 60f
    private var nextPowerT = 40f
    private var customerIds = 0
    private var carIds = 0
    private var stepT = 0f
    private val speciesPool = Species.entries.filter { it != Species.RACCOON || cfg.index >= 3 }
    var cashierPresent = false; private set
    var driveCashierPresent = false; private set

    val capacity get() = stats.capacity
    val builtCount get() = pads.count { it.done && it.spec.kind != PadKind.EXPAND }
    /** Early in the very first restaurant nobody walks out, so new players can learn in peace. */
    val learning get() = cfg.isFirst && builtCount < 5

    init {
        nav.buildWalls()
        cfg.stations.forEachIndexed { i, s ->
            val slot = layout.stationSlots[s.slot.coerceIn(0, layout.stationSlots.size - 1)]
            stations.add(Station(i, s, slot.x, slot.z))
        }
        for (i in 0 until min(cfg.tableCount, layout.tables.size)) tables.add(Table(i, layout.tables[i]))
        blockCounter()
        val tr = layout.trash
        nav.blockRect(tr.x - 0.3f, tr.z - 0.3f, tr.x + 0.3f, tr.z + 0.3f)
        for (dcr in layout.decorInside) nav.blockRect(dcr.x - 0.25f, dcr.z - 0.25f, dcr.x + 0.25f, dcr.z + 0.25f)
        // Within the magnet radius of both the drop spot and the register, so it is collected during normal work.
        // (Round 4 tried moving it for visibility; every other spot cost extra trips and broke pacing, so only the
        // drawing moved: see WorldView.drawPile.)
        counterPile = CoinPile(layout.cashierSpot.x - 0.75f, layout.cashierSpot.z - 0.55f)
        drivePile = layout.driveCashier?.let { CoinPile(it.x - 0.4f, it.z - 0.85f) }
        for (ps in cfg.pads) { val xz = padPos(ps); pads.add(Pad(ps, xz[0], xz[1])) }
        // restore saved progress silently (in plan order, so upgrades land on built stations);
        // free pads (cost 0) are part of the restaurant from the start
        for (pad in pads) {
            if (pad.spec.cost == 0L || (saved != null && pad.spec.id in saved.done)) { pad.done = true; pad.paid = pad.spec.cost; apply(pad, silent = true) }
            else if (saved != null) pad.paid = (saved.paid[pad.spec.id] ?: 0L).coerceIn(0L, max(0L, pad.spec.cost - 1))
        }
        refreshPads(initial = true)
        player.x = layout.doorX - 0.6f; player.z = layout.d - 2.2f; player.yaw = Math.PI.toFloat()
        nextRushT = if (cfg.isFirst) 160f else 110f
        nextCriticT = 150f + rng.range(0f, 90f)
    }

    // ---------------------------------------------------------------- setup helpers
    private fun blockStation(s: Station) { nav.blockRect(s.px - 0.62f, s.pz - 0.55f, s.px + 0.62f, s.pz + 0.45f) }
    private fun blockTable(t: Table) {
        val r = if (t.slot.seats == 4) 0.55f else 0.42f
        nav.blockRect(t.x - r, t.z - r, t.x + r, t.z + r)
    }
    private fun blockCounter() {
        val c = layout.counter
        nav.blockRect(c.x - 1.2f, c.z - 0.4f, c.x + 1.2f, c.z + 0.4f)
    }
    private fun blockDrive() {
        val dv = layout.drive ?: return
        nav.blockRect(dv.x - 0.5f, dv.z - 0.75f, dv.x + 0.4f, dv.z + 0.75f)
    }

    /** Where each pad sits. Staff and EXPAND pads line the sidewalk by the door ("now hiring" spots). */
    fun padPos(p: PadSpec): FloatArray {
        val l = layout
        val sidewalk = l.d + 1.0f
        return when (p.kind) {
            PadKind.BUILD_STATION -> { val sl = l.stationSlots[cfg.stations[p.ref].slot.coerceIn(0, l.stationSlots.size - 1)]; floatArrayOf(sl.x, sl.z + 0.1f) }
            PadKind.UPGRADE_STATION -> { val sl = l.stationSlots[cfg.stations[p.ref].slot.coerceIn(0, l.stationSlots.size - 1)]; floatArrayOf(sl.x, 2.85f) }
            PadKind.BUILD_TABLE -> { val t = l.tables[p.ref.coerceIn(0, l.tables.size - 1)].p; floatArrayOf(t.x, t.z) }
            PadKind.BUILD_DRIVE -> floatArrayOf(l.driveCashier!!.x - 0.2f, l.driveCashier!!.z + 1.4f)
            PadKind.EXPAND -> floatArrayOf(l.doorX + 1.9f, sidewalk)
            PadKind.HIRE -> {
                val role = StaffRole.entries[p.ref / 10]; val post = p.ref % 10
                val x = when (role) {
                    StaffRole.CASHIER -> l.doorX + 1.9f + post * 1.5f
                    StaffRole.RUNNER -> l.doorX - 1.9f - post * 1.5f
                    StaffRole.CLEANER -> l.doorX - 4.9f - post * 1.5f
                }
                floatArrayOf(x, sidewalk)
            }
        }
    }

    /** Re-maps ordered items whose station is not built yet onto items that can be produced right now. */
    fun availableOrder(order: IntArray): IntArray {
        val avail = cfg.menu.filter { m -> stations.any { it.built && it.item == m } }
        if (avail.isEmpty()) return order
        val out = order.copyOf()
        var k = 0
        for (i in out.indices) {
            val item = Item.entries[i]
            if (out[i] > 0 && item !in avail) {
                val n = out[i]; out[i] = 0
                repeat(n) { out[avail[(k++ + i) % avail.size].ordinal]++ }
            }
        }
        return out
    }

    fun makeOrder(r: Rng, mul: Float): IntArray {
        val order = IntArray(Item.entries.size)
        // Poisson-ish count around orderMean.
        var n = 1
        val extra = cfg.orderMean * mul - 1f
        var e = extra
        while (e > 0f) { if (r.float() < min(1f, e)) n++; e -= 1f }
        if (r.chance(0.15f)) n += 1
        n = n.coerceIn(1, cfg.orderMax + if (mul > 1f) 2 else 0)
        val wsum = cfg.menuWeights.sum()
        repeat(n) {
            var pick = r.float() * wsum
            var idx = 0
            for (i in cfg.menu.indices) { pick -= cfg.menuWeights[i]; if (pick <= 0f) { idx = i; break } }
            order[cfg.menu[idx].ordinal]++
        }
        return order
    }

    /** Price paid for an order of [base] menu value, including this restaurant's scale and the Menu Prices upgrade. */
    private fun priced(base: Int): Float = base * cfg.priceMul * (1f + stats.priceBonus)

    /** Seconds between arrivals: customers come as fast as the built kitchen can feed them. */
    private fun arrivalInterval(): Float {
        var rate = 0f
        for (s in stations) if (s.built) rate += s.speedMul * stats.cookSpeedMul / s.item.cookTime
        if (rate <= 0f) return 999f
        // bigger restaurants draw bigger crowds
        val floor = cfg.arrivalFloor
        return (cfg.orderMean / (rate * 0.72f)).coerceIn(floor, 6.5f)
    }

    // ---------------------------------------------------------------- main update
    fun update(dt: Float) {
        events.clear()
        time += dt
        updateEvents(dt)
        updateBoosts(dt)
        updatePlayer(dt)
        updateStations(dt)
        spawnCustomers(dt)
        updateCustomers(dt)
        if (driveBuilt) updateCars(dt)
        updateStaff(dt)
        updatePads(dt)
        updatePiles(dt)
        updateTroubles(dt)
        updatePowerUps(dt)
        updateCombo(dt)
    }

    private fun emit(e: GameEvent) { if (events.size < 400) events.add(e) }

    /** Rush hours and critic visits arrive on their own schedule once the restaurant is up and running. */
    private fun updateEvents(dt: Float) {
        val running = builtCount >= (if (cfg.isFirst) 6 else 3)
        if (!running) return
        if (rushT <= 0f) {
            nextRushT -= dt
            if (nextRushT <= 0f) {
                nextRushT = rng.range(150f, 220f)
                rushT = rng.range(16f, 20f); rushMult = rng.range(1.8f, 2.1f)
                walkoutsAtRush = walkouts
                emit(GameEvent(Ev.RUSH_START))
            }
        }
        if (cfg.critics) {
            nextCriticT -= dt
            if (nextCriticT <= 0f) { nextCriticT = rng.range(210f, 300f); criticDue = true }
        }
    }
    private var criticDue = false

    private fun updateBoosts(dt: Float) {
        if (rushT > 0f) {
            rushT -= dt
            if (rushT <= 0f) { rushMult = 1f; rushesDone++; if (walkouts == walkoutsAtRush) flawlessRushes++; emit(GameEvent(Ev.RUSH_END)) }
        }
        if (espressoT > 0f) espressoT -= dt
        if (magnetT > 0f) magnetT -= dt
        if (spatulaT > 0f) spatulaT -= dt
    }

    // ---------------------------------------------------------------- player
    private fun inSpill(x: Float, z: Float): Boolean {
        for (t in troubles) if (t.kind == TroubleKind.SPILL && !t.done && hypot(t.x - x, t.z - z) < 0.6f) return true
        return false
    }

    private fun updatePlayer(dt: Float) {
        val p = player
        var spd = stats.moveSpeed * (if (espressoT > 0f) 1.45f else 1f)
        if (inSpill(p.x, p.z)) spd *= 0.55f
        val mag = min(1f, hypot(inputX, inputZ))
        val tvx = inputX * spd; val tvz = inputZ * spd
        val acc = 1f - exp(-dt * 14f)
        p.vx += (tvx - p.vx) * acc; p.vz += (tvz - p.vz) * acc
        if (mag < 0.05f) { p.vx *= max(0f, 1f - dt * 16f); p.vz *= max(0f, 1f - dt * 16f) }
        moveWithCollision(p, p.vx * dt, p.vz * dt, 0.24f)
        val s = p.speed
        p.moving = s > 0.25f
        if (p.moving) {
            p.turnTo(atan2(p.vx, p.vz), dt, 14f)
            p.walkPhase += dt * s * 3.0f
            stepT -= dt
            if (stepT <= 0f) { stepT = 0.28f; emit(GameEvent(Ev.STEP, p.x, 0f, p.z)) }
        }
        // stack sway spring: driven by acceleration
        val target = -(s / max(1f, stats.moveSpeed)) * 0.22f
        p.swayV += ((target - p.sway) * 60f - p.swayV * 7f) * dt
        p.sway += p.swayV * dt
        p.pickT -= dt; p.dropT -= dt
        interact(dt)
    }

    fun moveWithCollision(a: Actor, dx: Float, dz: Float, r: Float) {
        val nx = a.x + dx
        if (!blockedCircle(nx, a.z, r)) a.x = nx else a.vx = 0f
        val nz = a.z + dz
        if (!blockedCircle(a.x, nz, r)) a.z = nz else a.vz = 0f
    }

    private fun blockedCircle(x: Float, z: Float, r: Float): Boolean {
        if (nav.isBlocked(x, z)) return true
        val k = r * 0.7071f
        return nav.isBlocked(x + r, z) || nav.isBlocked(x - r, z) || nav.isBlocked(x, z + r) || nav.isBlocked(x, z - r) ||
            nav.isBlocked(x + k, z + k) || nav.isBlocked(x - k, z + k) || nav.isBlocked(x + k, z - k) || nav.isBlocked(x - k, z - k)
    }

    private fun hasTrays(stack: List<Item>) = stack.isNotEmpty() && stack[0] == Item.TRAY
    private fun hasFood(stack: List<Item>) = stack.isNotEmpty() && stack[0] != Item.TRAY

    private fun near(x: Float, z: Float, tx: Float, tz: Float, r: Float) = abs(x - tx) < r && abs(z - tz) < r && hypot(x - tx, z - tz) < r

    private fun interact(dt: Float) {
        val p = player
        val cap = capacity
        // --- station pickup
        if (p.pickT <= 0f && p.stack.size < cap && !hasTrays(p.stack)) {
            for (s in stations) {
                if (!s.built || s.tray <= 0) continue
                if (near(p.x, p.z, s.pickX, s.pickZ, 0.75f)) {
                    s.tray--
                    p.stack.add(s.item)
                    p.pickT = 0.11f
                    emit(GameEvent(Ev.PICKUP, s.px, 1.05f, s.pz + 0.35f, a = p.stack.size.toFloat(), item = s.item))
                    break
                }
            }
        }
        // --- counter drop
        cashierPresent = near(p.x, p.z, layout.cashierSpot.x, layout.cashierSpot.z, 0.75f)
        val atCounterBack = p.z < layout.counter.z && p.z > layout.counter.z - 1.5f && abs(p.x - layout.counter.x) < 1.6f
        if (p.dropT <= 0f && hasFood(p.stack) && atCounterBack) {
            val it = p.stack.removeAt(p.stack.size - 1)
            counterRacks[it.ordinal]++
            p.dropT = 0.07f
            emit(GameEvent(Ev.DROP, p.x, 1.0f, p.z, a = p.stack.size.toFloat(), x2 = layout.counter.x, y2 = 1.0f, z2 = layout.counter.z, item = it))
        }
        // --- drive drop
        driveCashierPresent = false
        val dd = layout.driveDrop
        if (driveBuilt && dd != null) {
            val dc = layout.driveCashier!!
            driveCashierPresent = near(p.x, p.z, dc.x, dc.z, 0.75f)
            if (p.dropT <= 0f && hasFood(p.stack) && (near(p.x, p.z, dd.x, dd.z, 0.9f) || driveCashierPresent)) {
                val it = p.stack.removeAt(p.stack.size - 1)
                driveRacks[it.ordinal]++
                p.dropT = 0.07f
                emit(GameEvent(Ev.DROP, p.x, 1.0f, p.z, a = p.stack.size.toFloat(), x2 = layout.drive!!.x, y2 = 1.0f, z2 = layout.drive.z, item = it))
            }
        }
        // --- tables: pick trays
        if (p.pickT <= 0f && !hasFood(p.stack) && p.stack.size < cap) {
            for (t in tables) {
                if (!t.built || t.trays <= 0) continue
                val r = if (t.slot.seats == 4) 1.35f else 1.15f
                if (near(p.x, p.z, t.x, t.z, r)) {
                    t.trays--
                    p.stack.add(Item.TRAY)
                    p.pickT = 0.12f
                    traysCleared++
                    emit(GameEvent(Ev.PICKUP, t.x, 0.7f, t.z, a = p.stack.size.toFloat(), item = Item.TRAY))
                    break
                }
            }
        }
        // --- trash
        val tr = layout.trash
        if (p.dropT <= 0f && hasTrays(p.stack) && near(p.x, p.z, tr.x, tr.z, 1.05f)) {
            p.stack.removeAt(p.stack.size - 1)
            p.dropT = 0.07f
            emit(GameEvent(Ev.TRASH, tr.x, 0.8f, tr.z, a = p.stack.size.toFloat()))
        }
        // --- coin piles
        val mr = if (magnetT > 0f) 4.5f else stats.magnetRadius
        collectPile(counterPile, mr)
        drivePile?.let { collectPile(it, mr) }
        // --- power-ups
        val pit = powerUps.iterator()
        while (pit.hasNext()) {
            val pu = pit.next()
            if (near(p.x, p.z, pu.x, pu.z, 0.8f)) {
                when (pu.kind) {
                    PowerUpKind.ESPRESSO -> espressoT = 10f
                    PowerUpKind.MAGNET -> magnetT = 12f
                    PowerUpKind.SPATULA -> spatulaT = 8f
                }
                powerUpsUsed.add(pu.kind)
                emit(GameEvent(Ev.POWERUP, pu.x, 0.5f, pu.z, text = pu.kind.label))
                pit.remove()
            }
        }
    }

    private fun collectPile(pile: CoinPile, radius: Float) {
        if (pile.amount <= 0L) return
        val p = player
        if (near(p.x, p.z, pile.x, pile.z, radius)) {
            val amt = pile.amount
            cash += amt
            emit(GameEvent(Ev.COIN_COLLECT, pile.x, 1.0f, pile.z, a = amt.toFloat(), b = pile.visual.toFloat()))
            pile.amount = 0L; pile.visual = 0
        }
    }

    // ---------------------------------------------------------------- stations
    private fun updateStations(dt: Float) {
        val boost = (if (spatulaT > 0f) 3f else 1f) * stats.cookSpeedMul
        for (s in stations) {
            if (s.pop > 0f) s.pop -= dt
            if (!s.working) { s.cookAnim = max(0f, s.cookAnim - dt); continue }
            if (s.tray >= s.capacity) { s.progress = 0f; continue }
            s.cookAnim += dt
            s.progress += dt * s.speedMul * boost / s.item.cookTime
            if (s.progress >= 1f) { s.progress -= 1f; s.tray++ }
        }
    }

    // ---------------------------------------------------------------- customers
    private fun spawnCustomers(dt: Float) {
        nextArrivalT -= dt * rushMult
        if (nextArrivalT > 0f) return
        val interval = arrivalInterval()
        if (interval > 100f) { nextArrivalT = 0.5f; return } // nothing to sell yet
        if (queue.size >= cfg.maxQueue || queue.size >= layout.queueSlots.size) { nextArrivalT = 0.6f; return }
        nextArrivalT = interval * rng.range(0.7f, 1.3f)
        val group = if (cfg.groupArrivals && rng.chance(0.2f)) 2 + rng.int(2) else 1
        for (g in 0 until group) {
            if (queue.size >= cfg.maxQueue || queue.size >= layout.queueSlots.size) break
            val critic = criticDue && g == 0
            if (critic) criticDue = false
            val vip = !critic && rng.chance(cfg.vipChance + stats.vipChanceAdd)
            val order = availableOrder(makeOrder(rng, if (critic) 1.3f else if (vip) 1.4f else 1f))
            val c = Customer(customerIds++, rng.pick(speciesPool), rng.int(12), order, vip, critic,
                cfg.patience * stats.patienceMul * (if (vip) 0.75f else 1f) * (if (critic) 0.85f else 1f))
            val fromLeft = rng.chance(0.5f)
            c.x = (if (fromLeft) -6f else layout.w + 6f) + g * (if (fromLeft) -0.7f else 0.7f)
            c.z = layout.sidewalkZ + rng.range(-0.3f, 0.3f)
            c.queueIdx = queue.size
            queue.add(c)
            customers.add(c)
            routeToQueue(c)
            if (vip) emit(GameEvent(Ev.VIP, c.x, 1.2f, c.z))
            if (critic) emit(GameEvent(Ev.CRITIC, c.x, 1.2f, c.z))
        }
    }

    private fun routeToQueue(c: Customer) {
        val q = layout.queueSlots[c.queueIdx.coerceIn(0, layout.queueSlots.size - 1)]
        c.path = nav.path(c.x, c.z, q.x, q.z) ?: floatArrayOf(q.x, q.z)
        c.pathIdx = 0
        c.state = CState.WALK_IN
    }

    private fun updateCustomers(dt: Float) {
        val walk = 2.3f
        var ci = 0
        while (ci < customers.size) {
            val c = customers[ci]
            if (c.emoteT > 0f) { c.emoteT -= dt; if (c.emoteT <= 0f) c.emote = 0 }
            if (c.pop > 0f) c.pop -= dt
            when (c.state) {
                CState.WALK_IN -> {
                    tickPatience(c, dt * 0.5f)
                    if (queueBlocked(c)) { c.vx = 0f; c.vz = 0f }
                    else if (c.followPath(dt, walk)) {
                        c.state = if (c.queueIdx == 0) CState.ORDERING else CState.QUEUE
                        if (c.state == CState.ORDERING) emit(GameEvent(Ev.ORDER, c.x, 1.6f, c.z))
                    }
                }
                CState.QUEUE -> {
                    tickPatience(c, dt)
                    val q = layout.queueSlots[c.queueIdx.coerceIn(0, layout.queueSlots.size - 1)]
                    if (hypot(q.x - c.x, q.z - c.z) > 0.05f) {
                        c.slotPath[0] = q.x; c.slotPath[1] = q.z
                        c.path = c.slotPath; c.pathIdx = 0
                        if (queueBlocked(c)) { c.vx = 0f; c.vz = 0f } else c.followPath(dt, walk)
                    } else {
                        c.vx = 0f; c.vz = 0f
                        c.turnTo(atan2(layout.counter.x - c.x, layout.counter.z - c.z) * 0f + Math.PI.toFloat(), dt, 6f)
                        if (c.queueIdx == 0) { c.state = CState.ORDERING; emit(GameEvent(Ev.ORDER, c.x, 1.6f, c.z)) }
                    }
                }
                CState.ORDERING -> {
                    tickPatience(c, dt)
                    c.turnTo(Math.PI.toFloat(), dt, 8f)
                    val cashier = cashierPresent || staff.any { it.role == StaffRole.CASHIER && it.post == 0 && it.state == SState.AT_POST }
                    if (cashier && c.state == CState.ORDERING) {
                        c.takeT -= dt
                        if (c.takeT <= 0f) {
                            for (i in c.order.indices) {
                                if (c.got[i] < c.order[i] && counterRacks[i] > 0) {
                                    counterRacks[i]--; c.got[i]++
                                    c.holding.add(Item.entries[i])
                                    c.takeT = 0.14f
                                    emit(GameEvent(Ev.ITEM_FLY, layout.counter.x, 1.0f, layout.counter.z, x2 = c.x, y2 = 0.9f, z2 = c.z, item = Item.entries[i]))
                                    break
                                }
                            }
                        }
                        if (c.remaining() == 0) completeOrder(c)
                    }
                }
                CState.TO_SEAT -> {
                    if (c.followPath(dt, walk)) {
                        val t = c.table!!
                        val sp = t.seatPos(c.seat)
                        c.x = sp[0]; c.z = sp[1]; c.yaw = t.seatYaw(c.seat)
                        c.state = CState.EATING
                        c.eatT = rng.range(5f, 7.5f)
                        emit(GameEvent(Ev.EAT, c.x, 0.9f, c.z))
                    }
                }
                CState.EATING -> {
                    c.eatT -= dt
                    c.anim += dt
                    if (c.eatT <= 0f) {
                        val t = c.table!!
                        t.trays += 1
                        c.holding.clear()
                        if (c.willSleep && !troubles.any { it.kind == TroubleKind.SLEEPY && !it.done }) {
                            c.state = CState.SLEEPING
                            c.emote = 4; c.emoteT = 999f
                            val tb = Trouble(TroubleKind.SLEEPY, c.x, c.z); tb.customer = c
                            troubles.add(tb)
                            emit(GameEvent(Ev.TROUBLE, c.x, 1.4f, c.z, text = TroubleKind.SLEEPY.label))
                        } else leaveTable(c)
                    }
                }
                CState.SLEEPING -> { c.anim += dt }
                CState.LEAVING -> {
                    if (c.followPath(dt, if (c.angry) 2.8f else walk)) { c.state = CState.GONE }
                }
                CState.GONE -> {}
            }
            when (c.state) {
                CState.WALK_IN, CState.QUEUE, CState.TO_SEAT, CState.LEAVING -> yieldToPlayer(c, dt)
                else -> {}
            }
            if (c.state == CState.GONE) customers.removeAt(ci) else ci++
        }
    }

    /**
     * True when someone further ahead in the line stands right in front of [c] (within 0.62 m along its way, 0.36 m
     * to the side): [c] waits instead of walking into them. Groups and back-to-back arrivals used to overlap in long
     * queues. Only people ahead can hold someone up, so the line can never deadlock.
     */
    private fun queueBlocked(c: Customer): Boolean {
        val n = min(c.queueIdx, queue.size)
        if (n <= 0) return false
        val p = c.path ?: return false
        val ti = c.pathIdx * 2
        if (ti + 1 >= p.size) return false
        val dx = p[ti] - c.x; val dz = p[ti + 1] - c.z
        val d = hypot(dx, dz)
        if (d < 1e-3f) return false
        val ux = dx / d; val uz = dz / d
        for (i in 0 until n) {
            val o = queue[i]
            if (o === c) continue
            val ox = o.x - c.x; val oz = o.z - c.z
            val along = ox * ux + oz * uz
            if (along < -0.05f || along > 0.62f) continue
            if (abs(ox * uz - oz * ux) < 0.36f) return true
        }
        return false
    }

    /**
     * Guests step aside for the chef instead of walking through them (most visible at the door and along the queue).
     * The push is radial plus a sidestep across the guest's way, so a guest walking straight at a chef who stands
     * still slides around them rather than getting stuck. The chef is never slowed down.
     */
    private fun yieldToPlayer(c: Customer, dt: Float) {
        val p = player
        val dx = c.x - p.x; val dz = c.z - p.z
        val r = 0.5f
        if (abs(dx) >= r || abs(dz) >= r) return
        val d = hypot(dx, dz)
        if (d >= r) return
        var nx = 1f; var nz = 0f
        if (d > 1e-3f) { nx = dx / d; nz = dz / d }
        // sidestep: perpendicular to the guest's own motion, on the side away from the chef
        val sp = hypot(c.vx, c.vz)
        if (sp > 0.1f) {
            var tx = -c.vz / sp; var tz = c.vx / sp
            if (tx * dx + tz * dz < 0f) { tx = -tx; tz = -tz }
            nx = nx * 0.55f + tx * 0.85f; nz = nz * 0.55f + tz * 0.85f
            val nn = hypot(nx, nz); nx /= nn; nz /= nn
        }
        val push = min(r - d + 0.02f, 3.2f * dt)
        val vx = c.vx; val vz = c.vz
        moveWithCollision(c, nx * push, nz * push, 0.2f)
        c.vx = vx; c.vz = vz
    }

    fun leaveTable(c: Customer) {
        val t = c.table
        if (t != null && c.seat >= 0) t.seats[c.seat] = null
        c.table = null; c.seat = -1
        c.emote = 0
        sendHome(c)
    }

    private fun sendHome(c: Customer) {
        val ex = if (c.x < layout.w / 2f) -7f else layout.w + 7f
        val path = nav.path(c.x, c.z, layout.doorX, layout.d + 0.9f)
        val tail = floatArrayOf(ex, layout.sidewalkZ)
        c.path = if (path != null) path + tail else tail
        c.pathIdx = 0
        c.state = CState.LEAVING
    }

    private fun tickPatience(c: Customer, dt: Float) {
        if (learning) return
        c.patience -= dt
        if (c.patience <= 0f) {
            // walk out angry
            walkouts++
            c.angry = true; c.emote = 2; c.emoteT = 3f
            // return any taken items to the racks
            for (i in c.got.indices) counterRacks[i] += c.got[i]
            c.holding.clear()
            removeFromQueue(c)
            sendHome(c)
            if (combo > 1f) emit(GameEvent(Ev.COMBO_BREAK))
            combo = 1f
            emit(GameEvent(Ev.ANGRY, c.x, 1.5f, c.z))
        }
    }

    private fun removeFromQueue(c: Customer) {
        val idx = queue.indexOf(c)
        if (idx >= 0) {
            queue.removeAt(idx)
            for (i in idx until queue.size) queue[i].queueIdx = i
        }
        c.queueIdx = -1
    }

    private fun completeOrder(c: Customer) {
        val base = c.orderValue()
        val fast = c.patience / c.maxPatience >= 0.45f
        if (fast) {
            val before = combo
            combo = min(3f, combo + 0.25f * stats.comboGainMul)
            if (combo.toInt() > before.toInt() || (combo >= 1.5f && before < 1.5f) || (combo >= 2.5f && before < 2.5f)) emit(GameEvent(Ev.COMBO_UP, c.x, 2f, c.z, a = combo))
            bestCombo = max(bestCombo, combo)
        }
        comboIdle = 0f
        val tip = stats.tipBonus + (combo - 1f) * 0.25f
        var pay = (priced(base) * (1f + tip)).toLong()
        if (c.vip) { pay *= 3; vipsServed++ }
        if (c.critic) { pay *= 2; criticsServed++; emit(GameEvent(Ev.CRITIC_HAPPY, c.x, 1.8f, c.z)) }
        pay = max(1L, pay)
        revenue += pay
        counterPile.amount += pay
        counterPile.visual = min(40, counterPile.visual + max(1, (pay / (6f * cfg.priceMul)).toInt()))
        counterPile.bounce = 0.3f
        served++
        speciesServed.add(c.species)
        for (i in c.order.indices) itemsServed[i] += c.order[i]
        c.paid = true
        c.emote = if (c.vip || c.critic) 3 else 1; c.emoteT = 2f
        emit(GameEvent(Ev.SERVED, counterPile.x, 1.1f, counterPile.z, a = pay.toFloat(), x2 = c.x, z2 = c.z))
        removeFromQueue(c)
        // dine in?
        if (rng.float() < cfg.dineIn) {
            for (t in tables) {
                val s = t.freeSeat()
                if (s >= 0) {
                    t.seats[s] = c; c.table = t; c.seat = s
                    val sp = t.seatPos(s)
                    c.path = nav.path(c.x, c.z, sp[0], sp[1]) ?: floatArrayOf(sp[0], sp[1])
                    c.pathIdx = 0
                    c.state = CState.TO_SEAT
                    c.willSleep = TroubleKind.SLEEPY in cfg.troubles && troubleRng.chance(cfg.troubleRate * 0.05f)
                    return
                }
            }
        }
        sendHome(c)
    }

    // ---------------------------------------------------------------- drive-thru cars
    private fun updateCars(dt: Float) {
        val stop = layout.carStop ?: return
        nextCarT -= dt * (if (rushT > 0f) rushMult else 1f)
        if (nextCarT <= 0f && carQueue.size < 4) {
            nextCarT = max(6.5f, 13f - cfg.index * 0.4f) * rng.range(0.7f, 1.3f)
            val car = Car(carIds++, rng.int(8), availableOrder(makeOrder(rng, 1.3f)), rng.chance(0.25f))
            car.x = stop.x; car.z = layout.roadZ + 3f; car.yaw = Math.PI.toFloat()
            car.queueIdx = carQueue.size
            car.maxPatience = cfg.patience * 1.3f * stats.patienceMul; car.patience = car.maxPatience
            carQueue.add(car); cars.add(car)
            emit(GameEvent(Ev.CAR_HORN, car.x, 0.5f, car.z))
        }
        val it = cars.iterator()
        while (it.hasNext()) {
            val car = it.next()
            when (car.state) {
                CarState.ARRIVING, CarState.QUEUE -> {
                    val tz = stop.z + car.queueIdx * 4.3f
                    val dz = tz - car.z
                    if (abs(dz) > 0.05f) {
                        val v = min(abs(dz) * 2f, 5f)
                        car.vz = if (dz > 0) v else -v
                        car.z += car.vz * dt
                    } else { car.vz = 0f; car.z = tz }
                    car.yaw = Math.PI.toFloat()
                    if (car.queueIdx == 0 && abs(car.z - stop.z) < 0.1f) car.state = CarState.ORDERING
                    if (car.queueIdx > 0) carPatience(car, dt)
                }
                CarState.ORDERING -> {
                    carPatience(car, dt)
                    if (car.state != CarState.ORDERING) continue
                    val cashier = driveCashierPresent || staff.any { it.role == StaffRole.CASHIER && it.post == 1 && it.state == SState.AT_POST }
                    if (cashier) {
                        car.takeT -= dt
                        if (car.takeT <= 0f) {
                            for (i in car.order.indices) {
                                if (car.got[i] < car.order[i] && driveRacks[i] > 0) {
                                    driveRacks[i]--; car.got[i]++; car.takeT = 0.14f
                                    emit(GameEvent(Ev.ITEM_FLY, layout.drive!!.x, 1.1f, layout.drive.z, x2 = car.x, y2 = 0.9f, z2 = car.z, item = Item.entries[i]))
                                    break
                                }
                            }
                        }
                        if (car.remaining() == 0) {
                            var pay = (priced(car.orderValue()) * 1.4f * stats.driveThruPayMul * (1f + stats.tipBonus + (combo - 1f) * 0.25f)).toLong()
                            pay = max(1L, pay)
                            revenue += pay; served++; carsServed++
                            for (i in car.order.indices) itemsServed[i] += car.order[i]
                            drivePile!!.amount += pay
                            drivePile.visual = min(40, drivePile.visual + max(1, (pay / (6f * cfg.priceMul)).toInt())); drivePile.bounce = 0.3f
                            if (car.patience / car.maxPatience >= 0.45f) { combo = min(3f, combo + 0.25f * stats.comboGainMul); bestCombo = max(bestCombo, combo) }
                            emit(GameEvent(Ev.SERVED, drivePile.x, 1.1f, drivePile.z, a = pay.toFloat(), x2 = car.x, z2 = car.z))
                            emit(GameEvent(Ev.CAR_HORN, car.x, 0.5f, car.z))
                            leaveCar(car)
                        }
                    }
                }
                CarState.LEAVING -> {
                    car.vz = max(-7f, car.vz - dt * 9f)
                    car.z += car.vz * dt
                    if (car.z < -8f) car.state = CarState.GONE
                }
                CarState.GONE -> {}
            }
            if (car.state == CarState.GONE) it.remove()
        }
    }

    private fun carPatience(car: Car, dt: Float) {
        car.patience -= dt
        if (car.patience <= 0f) {
            walkouts++
            for (i in car.got.indices) driveRacks[i] += car.got[i]
            if (combo > 1f) emit(GameEvent(Ev.COMBO_BREAK))
            combo = 1f
            emit(GameEvent(Ev.ANGRY, car.x, 1.5f, car.z))
            leaveCar(car)
        }
    }

    private fun leaveCar(car: Car) {
        val idx = carQueue.indexOf(car)
        if (idx >= 0) { carQueue.removeAt(idx); for (i in idx until carQueue.size) carQueue[i].queueIdx = i }
        car.state = CarState.LEAVING
        car.vz = -1f
    }

    // ---------------------------------------------------------------- staff
    fun hire(role: StaffRole, post: Int, atPost: Boolean = false) {
        val s = Staff(role, post, Species.entries[(staff.size * 3 + role.ordinal + 1) % 9])
        staff.add(s)
        val dest = staffPost(s)
        if (atPost) {
            s.x = dest[0]; s.z = dest[1]
            s.state = if (role == StaffRole.CASHIER) SState.AT_POST else SState.IDLE
            return
        }
        s.x = layout.doorX; s.z = layout.d + 0.6f; s.pop = 0.5f
        s.state = SState.ENTER
        s.path = nav.path(s.x, s.z, dest[0], dest[1]); s.pathIdx = 0
    }

    private fun staffPost(s: Staff): FloatArray = when (s.role) {
        StaffRole.CASHIER -> if (s.post == 1 && layout.driveCashier != null) floatArrayOf(layout.driveCashier!!.x, layout.driveCashier!!.z) else floatArrayOf(layout.cashierSpot.x, layout.cashierSpot.z)
        else -> floatArrayOf(layout.dropSpot.x - 1.2f - s.post * 0.6f, layout.dropSpot.z)
    }

    private fun updateStaff(dt: Float) {
        val spd = 2.7f * stats.staffSpeedMul
        for (s in staff) {
            if (s.pop > 0f) s.pop -= dt
            when (s.role) {
                StaffRole.CASHIER -> {
                    if (s.state == SState.ENTER) { if (s.followPath(dt, spd)) s.state = SState.AT_POST }
                    else { s.vx = 0f; s.vz = 0f; s.turnTo(if (s.post == 1) (Math.PI / 2).toFloat() else 0f, dt, 6f) }
                }
                StaffRole.RUNNER -> runner(s, dt, spd)
                StaffRole.CLEANER -> cleaner(s, dt, spd)
            }
        }
    }

    private fun neededAt(rack: IntArray, item: Item, forDrive: Boolean): Int {
        var demand = 0
        if (forDrive) { for (c in carQueue) demand += max(0, c.order[item.ordinal] - c.got[item.ordinal]) }
        else { for (c in queue) demand += max(0, c.order[item.ordinal] - c.got[item.ordinal]) }
        return demand - rack[item.ordinal]
    }

    private fun runner(s: Staff, dt: Float, spd: Float) {
        when (s.state) {
            SState.ENTER, SState.IDLE, SState.AT_POST -> {
                if (s.state == SState.ENTER && !s.followPath(dt, spd)) return
                s.state = SState.IDLE
                s.idleT -= dt
                if (s.idleT > 0f) return
                s.idleT = 0.4f
                // choose the station whose product is most needed and has stock
                var best: Station? = null; var bestScore = -999
                for (st in stations) {
                    if (!st.built || st.tray <= 0) continue
                    val score = neededAt(counterRacks, st.item, false) * 3 + st.tray - counterRacks[st.item.ordinal]
                    if (score > bestScore) { bestScore = score; best = st }
                }
                if (best != null && (bestScore > -4 || best.tray >= best.capacity)) {
                    s.target = best
                    s.path = nav.path(s.x, s.z, best.pickX, best.pickZ + 0.05f); s.pathIdx = 0
                    s.state = SState.TO_PICK
                }
            }
            SState.TO_PICK -> {
                if (s.followPath(dt, spd)) {
                    val st = s.target!!
                    s.actT -= dt
                    if (s.actT <= 0f && st.tray > 0 && s.stack.size < 4) {
                        st.tray--; s.stack.add(st.item); s.actT = 0.16f
                        emit(GameEvent(Ev.PICKUP, st.px, 1.05f, st.pz + 0.35f, a = -1f, item = st.item))
                    }
                    if (s.stack.size >= 4 || st.tray == 0) {
                        if (s.stack.isEmpty()) { s.state = SState.IDLE; return }
                        val dp = layout.dropSpot
                        s.path = nav.path(s.x, s.z, dp.x, dp.z); s.pathIdx = 0
                        s.state = SState.TO_DROP
                    }
                }
            }
            SState.TO_DROP -> {
                if (s.followPath(dt, spd)) {
                    s.actT -= dt
                    if (s.actT <= 0f && s.stack.isNotEmpty()) {
                        val it = s.stack.removeAt(s.stack.size - 1)
                        counterRacks[it.ordinal]++; s.actT = 0.1f
                        emit(GameEvent(Ev.DROP, s.x, 1.0f, s.z, a = -1f, x2 = layout.counter.x, y2 = 1.0f, z2 = layout.counter.z, item = it))
                    }
                    if (s.stack.isEmpty()) s.state = SState.IDLE
                }
            }
            else -> s.state = SState.IDLE
        }
    }

    private fun cleaner(s: Staff, dt: Float, spd: Float) {
        when (s.state) {
            SState.ENTER, SState.IDLE, SState.AT_POST -> {
                if (s.state == SState.ENTER && !s.followPath(dt, spd)) return
                s.state = SState.IDLE
                s.idleT -= dt
                if (s.idleT > 0f) return
                s.idleT = 0.5f
                val t = tables.filter { it.built && it.trays > 0 }.minByOrNull { hypot(it.x - s.x, it.z - s.z) }
                if (t != null) {
                    s.targetTable = t
                    s.path = nav.path(s.x, s.z, t.x + 0.0f, t.z + 0.95f); s.pathIdx = 0
                    s.state = SState.TO_TABLE
                }
            }
            SState.TO_TABLE -> {
                if (s.followPath(dt, spd)) {
                    val t = s.targetTable!!
                    s.actT -= dt
                    if (s.actT <= 0f && t.trays > 0 && s.stack.size < 4) { t.trays--; s.stack.add(Item.TRAY); s.actT = 0.18f }
                    if (t.trays == 0 || s.stack.size >= 4) {
                        val tr = layout.trashApproach
                        s.path = nav.path(s.x, s.z, tr.x, tr.z); s.pathIdx = 0
                        s.state = SState.TO_TRASH
                    }
                }
            }
            SState.TO_TRASH -> {
                if (s.followPath(dt, spd)) {
                    s.actT -= dt
                    if (s.actT <= 0f && s.stack.isNotEmpty()) {
                        s.stack.removeAt(s.stack.size - 1); s.actT = 0.12f
                        emit(GameEvent(Ev.TRASH, layout.trash.x, 0.8f, layout.trash.z, a = -1f))
                    }
                    if (s.stack.isEmpty()) s.state = SState.IDLE
                }
            }
            else -> s.state = SState.IDLE
        }
    }

    // ---------------------------------------------------------------- pads
    /** Shows the next two pads of the plan (in order) whose prerequisites are met. */
    private fun refreshPads(initial: Boolean = false) {
        var shown = 0
        for (p in pads) {
            if (p.done) continue
            if (!prerequisiteMet(p)) { p.visible = false; continue }
            if (shown < 2) {
                if (!p.visible) { p.visible = true; p.pop = 0.5f; if (!initial) emit(GameEvent(Ev.FOCUS, p.x, 0f, p.z, a = 0.9f)) }
                shown++
            } else p.visible = false
        }
    }

    private fun prerequisiteMet(p: Pad): Boolean = when (p.spec.kind) {
        PadKind.UPGRADE_STATION -> stations[p.spec.ref].built
        PadKind.HIRE -> p.spec.ref != StaffRole.CASHIER.ordinal * 10 + 1 || driveBuilt
        PadKind.EXPAND -> pads.all { it.done || it.spec.kind == PadKind.EXPAND }
        else -> true
    }

    private fun updatePads(dt: Float) {
        val pl = player
        var onAny = false
        for (pad in pads) {
            if (pad.pop > 0f) pad.pop -= dt
            if (pad.flash > 0f) pad.flash -= dt
            if (!pad.visible || pad.done) continue
            if (near(pl.x, pl.z, pad.x, pad.z, if (pad.spec.kind == PadKind.EXPAND) 1.2f else 0.85f)) {
                onAny = true
                pl.padDwell += dt
                if (pl.padDwell < 0.22f) continue
                if (cash <= 0L) continue
                // pay a pad off in ~1.3 s however expensive it is
                val rate = max(pad.spec.cost / 1.3f, 12f)
                var amount = (rate * dt + padCarry).toLong()
                padCarry = rate * dt + padCarry - amount
                amount = min(amount, min(cash, pad.remaining))
                if (amount <= 0L) continue
                cash -= amount; pad.paid += amount; pad.flash = 0.1f
                progressVersion++
                emit(GameEvent(Ev.PAY_TICK, pl.x, 1.2f, pl.z, x2 = pad.x, y2 = 0.1f, z2 = pad.z, a = pad.paid.toFloat() / pad.spec.cost))
                if (pad.remaining <= 0L) completePad(pad)
            }
        }
        if (!onAny) { pl.padDwell = 0f; padCarry = 0f }
    }

    private var padCarry = 0f
    /** Eject candidates relative to the requested spot (x, z pairs): as asked first, then around the object. */
    private val EJECT = floatArrayOf(0f, 0f, 0f, -2.6f, 1.3f, -1.3f, -1.3f, -1.3f, 1.3f, 0f, -1.3f, 0f, 0f, -3.4f, 2.2f, -1.3f, -2.2f, -1.3f)

    private fun completePad(pad: Pad) {
        pad.done = true; pad.visible = false
        padsBought++
        progressVersion++
        apply(pad, silent = false)
        refreshPads()
    }

    /** Applies a bought pad to the restaurant. [silent] = restoring a save (no FX, staff appear at their posts). */
    private fun apply(pad: Pad, silent: Boolean) {
        val s = pad.spec
        when (s.kind) {
            PadKind.BUILD_STATION -> {
                val st = stations[s.ref]; st.built = true; blockStation(st)
                if (!silent) {
                    st.pop = 0.6f
                    ejectPlayer(st.px, st.pz + 1.2f)
                    emit(GameEvent(Ev.BUILT, st.px, 0f, st.pz, text = st.spec.type.label))
                }
            }
            PadKind.UPGRADE_STATION -> {
                val st = stations[s.ref]; st.level = min(3, st.level + 1)
                if (!silent) { st.pop = 0.5f; emit(GameEvent(Ev.UPGRADED, st.px, 0f, st.pz, a = st.level.toFloat(), text = st.spec.type.label)) }
            }
            PadKind.BUILD_TABLE -> {
                val t = tables[s.ref]; t.built = true; blockTable(t)
                if (!silent) {
                    t.pop = 0.6f
                    ejectPlayer(t.x, t.z + 1.3f)
                    emit(GameEvent(Ev.BUILT, t.x, 0f, t.z, text = "Table"))
                }
            }
            PadKind.BUILD_DRIVE -> {
                driveBuilt = true; blockDrive()
                nextCarT = if (silent) 4f else 2f
                if (!silent) {
                    drivePop = 0.6f
                    val dv = layout.drive!!
                    emit(GameEvent(Ev.BUILT, dv.x, 0f, dv.z, text = "Drive-Thru"))
                }
            }
            PadKind.HIRE -> {
                val role = StaffRole.entries[s.ref / 10]
                hire(role, s.ref % 10, atPost = silent)
                if (!silent) emit(GameEvent(Ev.HIRED, pad.x, 0f, pad.z, text = role.label))
            }
            PadKind.EXPAND -> {
                if (!silent) { expanded = true; emit(GameEvent(Ev.EXPANDED, pad.x, 0f, pad.z)) }
            }
        }
    }

    /**
     * Moves the chef off something just built on top of them, to (x, z) or, if that spot is blocked or a dead end
     * (e.g. the gap between a new table and the front wall), to the first nearby spot that still reaches the door.
     */
    private fun ejectPlayer(x: Float, z: Float) {
        if (!blockedCircle(player.x, player.z, 0.24f)) return
        var tx = x; var tz = z
        for (k in EJECT.indices step 2) {
            val ex = x + EJECT[k]; val ez = z + EJECT[k + 1]
            if (!blockedCircle(ex, ez, 0.24f) && nav.path(ex, ez, layout.doorX, layout.d + 0.6f) != null) { tx = ex; tz = ez; break }
        }
        player.x = tx; player.z = tz; player.vx = 0f; player.vz = 0f
    }

    // ---------------------------------------------------------------- coins
    private fun updatePiles(dt: Float) {
        if (counterPile.bounce > 0f) counterPile.bounce -= dt
        drivePile?.let { if (it.bounce > 0f) it.bounce -= dt }
    }

    // ---------------------------------------------------------------- troubles
    private fun updateTroubles(dt: Float) {
        if (cfg.troubleRate > 0f && cfg.troubles.isNotEmpty()) {
            nextTroubleT -= dt
            if (nextTroubleT <= 0f) {
                nextTroubleT = 60f / cfg.troubleRate * troubleRng.range(0.6f, 1.4f)
                spawnTrouble(troubleRng.pick(cfg.troubles))
            }
        }
        val p = player
        val it = troubles.iterator()
        while (it.hasNext()) {
            val t = it.next()
            t.age += dt
            when (t.kind) {
                TroubleKind.SPILL -> {
                    if (near(p.x, p.z, t.x, t.z, 0.65f)) { t.progress += dt * stats.fixSpeedMul; if (t.progress >= 0.9f) fixTrouble(t) }
                    if (t.age > 45f) t.done = true
                }
                TroubleKind.FIRE, TroubleKind.JAM -> {
                    val st = t.station!!
                    if (near(p.x, p.z, st.pickX, st.pickZ, 1.3f)) {
                        t.progress += dt * stats.fixSpeedMul
                        if (t.progress >= (if (t.kind == TroubleKind.FIRE) 1.0f else 1.4f)) {
                            st.fire = false; st.jammed = false
                            fixTrouble(t)
                        }
                    }
                }
                TroubleKind.SLEEPY -> {
                    val c = t.customer!!
                    if (near(p.x, p.z, c.x, c.z, 1.15f)) {
                        t.progress += dt * stats.fixSpeedMul
                        if (t.progress >= 0.6f) { fixTrouble(t); c.emote = 5; c.emoteT = 1f; leaveTable(c) }
                    }
                }
                TroubleKind.RACCOON -> updateRaccoon(t, dt)
            }
            if (t.done) it.remove()
        }
    }

    private fun fixTrouble(t: Trouble) {
        t.done = true
        troublesFixed.add(t.kind)
        troublesFixedCount++
        emit(GameEvent(Ev.TROUBLE_FIXED, t.x, 0.8f, t.z, text = t.kind.label))
    }

    private fun spawnTrouble(kind: TroubleKind) {
        when (kind) {
            TroubleKind.SPILL -> {
                repeat(10) {
                    val x = troubleRng.range(1f, layout.w - 1f)
                    val z = troubleRng.range(1.8f, layout.d - 1.2f)
                    if (!nav.isBlocked(x, z) && abs(z - layout.counter.z) > 0.8f) {
                        troubles.add(Trouble(TroubleKind.SPILL, x, z))
                        emit(GameEvent(Ev.TROUBLE, x, 0.3f, z, text = kind.label))
                        return
                    }
                }
            }
            TroubleKind.FIRE, TroubleKind.JAM -> {
                val cands = stations.filter { it.built && !it.fire && !it.jammed && (kind == TroubleKind.JAM || it.spec.type == StationType.GRILL || it.spec.type == StationType.FRYER) }
                if (cands.isEmpty()) return
                val st = troubleRng.pick(cands)
                if (kind == TroubleKind.FIRE) st.fire = true else st.jammed = true
                val t = Trouble(kind, st.px, st.pz); t.station = st
                troubles.add(t)
                emit(GameEvent(Ev.TROUBLE, st.px, 1.6f, st.pz, text = kind.label))
            }
            TroubleKind.RACCOON -> {
                if (troubles.any { it.kind == TroubleKind.RACCOON }) return
                val t = Trouble(TroubleKind.RACCOON, layout.doorX + 0.5f, layout.d + 1.5f)
                val q = layout.queueSpot
                t.path = nav.path(t.x, t.z, q.x - 1.0f, q.z + 0.1f); t.pathIdx = 0
                troubles.add(t)
                emit(GameEvent(Ev.TROUBLE, t.x, 0.6f, t.z, text = kind.label))
            }
            TroubleKind.SLEEPY -> {
                // handled when a customer finishes eating (willSleep flag); bump the chance on the next diner
                customers.firstOrNull { it.state == CState.EATING && !it.willSleep }?.willSleep = true
            }
        }
    }

    private fun updateRaccoon(t: Trouble, dt: Float) {
        val spd = if (t.phase == 2) 4.2f else 2.6f
        val p = player
        // caught?
        if (t.phase < 2 && near(p.x, p.z, t.x, t.z, 0.95f)) {
            for (i in t.stolen) counterRacks[i.ordinal]++
            t.stolen.clear()
            t.phase = 2
            t.path = floatArrayOf(layout.doorX, layout.d + 0.8f, -8f, layout.sidewalkZ); t.pathIdx = 0
            emit(GameEvent(Ev.SHOO, t.x, 0.8f, t.z))
            troublesFixed.add(TroubleKind.RACCOON); troublesFixedCount++
            emit(GameEvent(Ev.TROUBLE_FIXED, t.x, 0.8f, t.z, text = TroubleKind.RACCOON.label))
        }
        val path = t.path
        if (path != null && t.pathIdx * 2 < path.size) {
            val tx = path[t.pathIdx * 2]; val tz = path[t.pathIdx * 2 + 1]
            val dx = tx - t.x; val dz = tz - t.z
            val d = hypot(dx, dz)
            if (d < spd * dt) { t.x = tx; t.z = tz; t.pathIdx++ } else {
                t.vx = dx / d * spd; t.vz = dz / d * spd
                t.x += t.vx * dt; t.z += t.vz * dt
                t.yaw = atan2(t.vx, t.vz)
                t.walkPhase += dt * spd * 4f
            }
        } else {
            when (t.phase) {
                0 -> { t.phase = 1; t.progress = 0f }
                1 -> {
                    t.progress += dt
                    t.yaw = Math.PI.toFloat()
                    if (t.progress > 0.7f) {
                        t.progress = 0f
                        val idx = counterRacks.indices.filter { counterRacks[it] > 0 }
                        if (idx.isNotEmpty() && t.stolen.size < 3) {
                            val i = idx[troubleRng.int(idx.size)]
                            counterRacks[i]--; t.stolen.add(Item.entries[i])
                            emit(GameEvent(Ev.STOLEN, t.x, 1f, t.z, item = Item.entries[i]))
                        }
                        if (t.stolen.size >= 3 || idx.isEmpty() || t.age > 14f) {
                            t.phase = 2
                            t.path = floatArrayOf(layout.doorX, layout.d + 0.8f, -8f, layout.sidewalkZ); t.pathIdx = 0
                        }
                    }
                }
                else -> t.done = true
            }
        }
    }

    // ---------------------------------------------------------------- power-ups
    private fun updatePowerUps(dt: Float) {
        if (!learning && builtCount >= 4) {
            nextPowerT -= dt
            if (nextPowerT <= 0f) {
                nextPowerT = troubleRng.range(32f, 48f)
                repeat(10) {
                    val x = troubleRng.range(1f, layout.w - 1f)
                    val z = troubleRng.range(layout.counter.z + 1.2f, layout.d - 1f)
                    if (!nav.isBlocked(x, z)) { powerUps.add(PowerUp(troubleRng.pick(PowerUpKind.entries), x, z)); return@repeat }
                }
            }
        }
        val it = powerUps.iterator()
        while (it.hasNext()) { val p = it.next(); p.t += dt; p.ttl -= dt; if (p.ttl <= 0f) it.remove() }
    }

    // ---------------------------------------------------------------- combo
    private fun updateCombo(dt: Float) {
        comboIdle += dt
        if (comboIdle > 12f && combo > 1f) { combo = max(1f, combo - 0.25f); comboIdle = 8f }
    }

    /** Debug tooling: 1 = build everything except EXPAND, 2 = +cash, 3 = finish (everything, then EXPAND as if bought). */
    fun debugAction(kind: Int) {
        when (kind) {
            3 -> {
                debugAction(1)
                pads.firstOrNull { !it.done && it.spec.kind == PadKind.EXPAND }?.let { pad -> pad.paid = pad.spec.cost; completePad(pad) }
            }
            1 -> {
                for (pad in pads) if (!pad.done && pad.spec.kind != PadKind.EXPAND) { pad.done = true; pad.paid = pad.spec.cost; apply(pad, silent = true) }
                progressVersion++
                refreshPads()
            }
            2 -> cash += (500 * cfg.priceMul).toLong()
        }
    }

    // ---------------------------------------------------------------- queries for UI/bot/save
    fun visiblePads() = pads.filter { it.visible && !it.done }

    /** Snapshot of pad progress for the save file. */
    fun progress(): RestaurantProgress {
        val done = HashSet<Int>(); val paid = HashMap<Int, Long>()
        for (p in pads) { if (p.done) done.add(p.spec.id) else if (p.paid > 0L) paid[p.spec.id] = p.paid }
        return RestaurantProgress(done, paid)
    }

    /** Where should the player go next? Drives the floor arrows. Null = nothing worth pointing at. */
    /**
     * Where the guide arrows lead, written into [out] as (x, z). Returns false when there is nowhere to point.
     * Called every frame by the renderer, so it allocates nothing.
     */
    fun guidanceTarget(out: FloatArray): Boolean {
        val p = player
        // An affordable pad always gets the arrow (the core loop of the genre).
        var pad: Pad? = null
        var firstVisible: Pad? = null
        for (i in pads.indices) {
            val q = pads[i]
            if (!q.visible || q.done) continue
            if (firstVisible == null) firstVisible = q
            if (pad == null && cash >= q.remaining) pad = q
        }
        if (pad != null && p.stack.isEmpty()) return put(out, pad.x, pad.z)
        if (!cfg.isFirst || builtCount >= 9) return false
        // First restaurant: walk new players through the serving loop.
        if (pad == null && !anyStationBuilt()) return if (firstVisible != null) put(out, firstVisible.x, firstVisible.z) else false
        if (hasTrays(p.stack)) return put(out, layout.trash.x, layout.trash.z)
        if (hasFood(p.stack)) {
            return if (driveBuilt && carQueue.isNotEmpty() && queue.isEmpty()) put(out, layout.driveDrop!!.x, layout.driveDrop!!.z)
            else put(out, layout.dropSpot.x, layout.dropSpot.z - 0.2f)
        }
        if (pad != null) return put(out, pad.x, pad.z)
        if (counterPile.amount >= 15) return put(out, counterPile.x, counterPile.z)
        val front = if (queue.isEmpty()) null else queue[0]
        if (front != null && front.state == CState.ORDERING) {
            var lacking = false
            for (i in front.order.indices) if (front.order[i] - front.got[i] > counterRacks[i]) lacking = true
            var counterCashier = false
            for (i in staff.indices) if (staff[i].role == StaffRole.CASHIER && staff[i].post == 0) counterCashier = true
            if (!lacking && !cashierPresent && !counterCashier) return put(out, layout.cashierSpot.x, layout.cashierSpot.z)
        }
        var st: Station? = null
        for (i in stations.indices) { val s = stations[i]; if (s.built && s.tray > 0 && (st == null || s.tray > st.tray)) st = s }
        if (st != null) return put(out, st.pickX, st.pickZ)
        for (i in tables.indices) { val t = tables[i]; if (t.trays > 0) return put(out, t.x, t.z + 0.9f) }
        return false
    }

    private fun put(out: FloatArray, x: Float, z: Float): Boolean { out[0] = x; out[1] = z; return true }
    private fun anyStationBuilt(): Boolean { for (i in stations.indices) if (stations[i].built) return true; return false }
}
