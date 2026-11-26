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
    GOAL_REACHED, TIMER_WARN, FOCUS, ITEM_FLY, COIN_FLY, STEP, CRITIC_HAPPY, STOLEN, SHOO
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
    var built = spec.prebuilt
    var level = spec.level
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

class Pad(val index: Int, val spec: PadSpec, val x: Float, val z: Float) {
    var paid = 0
    var done = false
    var visible = false
    var pop = 0f
    var flash = 0f
    val remaining get() = spec.cost - paid
}

class CoinPile(val x: Float, val z: Float) {
    var amount = 0
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

class Arrival(val time: Float, val species: Species, val shirt: Int, val order: IntArray, val vip: Boolean, val critic: Boolean)

// ===================================================================================== world
class World(val cfg: LevelConfig, val stats: PlayerStats, val playerSpecies: Species = Species.CORGI) {
    val layout: Layout = Layouts.get(cfg.layoutId, cfg.mirror)
    val nav = NavGrid(layout)
    private val rng = Rng(cfg.seed)
    private val troubleRng = Rng(cfg.seed xor 0x5DEECE66DL)

    // ---------------------------------------------------------------- state
    var time = 0f
    var finished = false
    var won = false
    var inOvertime = false
    var goalReached = false
    var cash = cfg.startCash + stats.startCashBonus
    var revenue = 0
    var served = 0
    var walkouts = 0
    var combo = 1f
    var bestCombo = 1f
    private var comboIdle = 0f
    var criticsServed = 0
    var criticsFailed = 0
    val itemsServed = IntArray(Item.entries.size)
    val speciesServed = HashSet<Species>()
    var vipsServed = 0
    val powerUpsUsed = HashSet<PowerUpKind>()
    var carsServed = 0
    val troublesFixed = HashSet<TroubleKind>()
    var stars = 0

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
    private var rushIdx = 0

    // input (world-space direction, magnitude 0..1)
    var inputX = 0f; var inputZ = 0f
    var finishRequested = false

    private val arrivals = ArrayList<Arrival>()
    private var arrivalIdx = 0
    private var nextCarT = 6f
    private var nextTroubleT = 25f
    private var nextPowerT = 30f
    private var customerIds = 0
    private var carIds = 0
    private var stepT = 0f
    private var warned = false
    var cashierPresent = false; private set
    var driveCashierPresent = false; private set

    val timeLeft get() = max(0f, cfg.shiftSeconds - time)
    val capacity get() = stats.capacity

    init {
        nav.buildWalls()
        cfg.stations.forEachIndexed { i, s ->
            val slot = layout.stationSlots[s.slot.coerceIn(0, layout.stationSlots.size - 1)]
            val st = Station(i, s, slot.x, slot.z)
            stations.add(st)
            if (st.built) blockStation(st)
        }
        for (i in 0 until min(cfg.tableCount, layout.tables.size)) {
            val t = Table(i, layout.tables[i])
            t.built = i < cfg.tablesPrebuilt
            tables.add(t)
            if (t.built) blockTable(t)
        }
        blockCounter()
        val tr = layout.trash
        nav.blockRect(tr.x - 0.3f, tr.z - 0.3f, tr.x + 0.3f, tr.z + 0.3f)
        for (dcr in layout.decorInside) nav.blockRect(dcr.x - 0.25f, dcr.z - 0.25f, dcr.x + 0.25f, dcr.z + 0.25f)
        if (cfg.drive && layout.drive != null) {
            driveBuilt = cfg.drivePrebuilt
            if (driveBuilt) blockDrive()
        }
        counterPile = CoinPile(layout.cashierSpot.x - 0.75f, layout.cashierSpot.z - 0.55f)
        drivePile = layout.driveCashier?.let { CoinPile(it.x - 0.4f, it.z - 0.85f) }
        cfg.pads.forEachIndexed { i, ps -> pads.add(Pad(i, ps, padX(ps), padZ(ps))) }
        refreshPads(initial = true)
        player.x = layout.doorX; player.z = layout.d - 1.4f; player.yaw = Math.PI.toFloat()
        buildArrivals()
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

    private fun padX(p: PadSpec): Float = when (p.kind) {
        PadKind.BUILD_STATION, PadKind.UPGRADE_STATION -> layout.stationSlots[cfg.stations[p.ref].slot.coerceIn(0, layout.stationSlots.size - 1)].x
        PadKind.BUILD_TABLE -> layout.tables[p.ref.coerceIn(0, layout.tables.size - 1)].p.x
        PadKind.BUILD_DRIVE -> layout.driveCashier!!.x - 0.2f
        PadKind.HIRE -> layout.counter.x - 2.25f - (p.ref % 10) * 0.0f
    }

    private fun padZ(p: PadSpec): Float = when (p.kind) {
        PadKind.BUILD_STATION -> layout.stationSlots[cfg.stations[p.ref].slot.coerceIn(0, layout.stationSlots.size - 1)].z + 0.1f
        PadKind.UPGRADE_STATION -> 2.85f
        PadKind.BUILD_TABLE -> layout.tables[p.ref.coerceIn(0, layout.tables.size - 1)].p.z
        PadKind.BUILD_DRIVE -> layout.driveCashier!!.z + 1.4f
        PadKind.HIRE -> layout.counter.z + 1.35f
    }

    private fun buildArrivals() {
        val r = Rng(cfg.seed * 31 + 7)
        var t = if (cfg.tutorial == 1) 1.0f else 2.0f
        val horizon = cfg.shiftSeconds + 600f
        val speciesPool = Species.entries.filter { it != Species.RACCOON || cfg.id > 20 }
        var criticsLeft = cfg.critics
        val criticTimes = (0 until cfg.critics).map { cfg.shiftSeconds * (0.25f + 0.55f * it / max(1, cfg.critics - 1).toFloat()) }
        while (t < horizon) {
            val rm = rushMultAt(t)
            val gap = cfg.spawnInterval / rm * r.range(0.65f, 1.35f)
            t += gap
            val group = if (cfg.groupArrivals && r.chance(0.25f)) 3 else 1
            for (g in 0 until group) {
                val critic = criticsLeft > 0 && t >= criticTimes[cfg.critics - criticsLeft]
                if (critic) criticsLeft--
                val vip = !critic && r.chance(cfg.vipChance + stats.vipChanceAdd)
                arrivals.add(Arrival(t + g * 0.6f, r.pick(speciesPool), r.int(12), makeOrder(r, if (critic) 1.3f else if (vip) 1.4f else 1f), vip, critic))
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

    private fun rushMultAt(t: Float): Float {
        for (w in cfg.rush) if (t >= w.start && t < w.start + w.duration) return w.mult
        return 1f
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

    // ---------------------------------------------------------------- main update
    fun update(dt: Float) {
        if (finished) return
        events.clear()
        time += dt
        updateRush()
        updateBoosts(dt)
        updatePlayer(dt)
        updateStations(dt)
        spawnCustomers()
        updateCustomers(dt)
        if (cfg.drive && driveBuilt) updateCars(dt)
        updateStaff(dt)
        updatePads(dt)
        updatePiles(dt)
        updateTroubles(dt)
        updatePowerUps(dt)
        updateCombo(dt)
        checkEnd()
    }

    private fun emit(e: GameEvent) { if (events.size < 400) events.add(e) }

    private fun updateRush() {
        if (rushIdx < cfg.rush.size) {
            val w = cfg.rush[rushIdx]
            if (time >= w.start && rushT <= 0f) {
                rushT = w.duration; rushMult = w.mult; rushIdx++
                emit(GameEvent(Ev.RUSH_START))
            }
        }
    }

    private fun updateBoosts(dt: Float) {
        if (rushT > 0f) { rushT -= dt; if (rushT <= 0f) { rushMult = 1f; emit(GameEvent(Ev.RUSH_END)) } }
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
        if (pile.amount <= 0) return
        val p = player
        if (near(p.x, p.z, pile.x, pile.z, radius)) {
            val amt = pile.amount
            cash += amt
            emit(GameEvent(Ev.COIN_COLLECT, pile.x, 1.0f, pile.z, a = amt.toFloat(), b = pile.visual.toFloat()))
            pile.amount = 0; pile.visual = 0
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
    private fun spawnCustomers() {
        while (arrivalIdx < arrivals.size && arrivals[arrivalIdx].time <= time) {
            if (queue.size >= cfg.maxQueue || queue.size >= layout.queueSlots.size) return // wait for space; arrival is delayed
            val a = arrivals[arrivalIdx++]
            val c = Customer(customerIds++, a.species, a.shirt, availableOrder(a.order), a.vip, a.critic,
                cfg.patience * stats.patienceMul * (if (a.vip) 0.75f else 1f) * (if (a.critic) 0.85f else 1f))
            val fromLeft = rng.chance(0.5f)
            c.x = if (fromLeft) -6f else layout.w + 6f
            c.z = layout.sidewalkZ + rng.range(-0.3f, 0.3f)
            c.pop = 0.0f
            c.queueIdx = queue.size
            queue.add(c)
            customers.add(c)
            routeToQueue(c)
            if (a.vip) emit(GameEvent(Ev.VIP, c.x, 1.2f, c.z))
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
        val it = customers.iterator()
        while (it.hasNext()) {
            val c = it.next()
            if (c.emoteT > 0f) { c.emoteT -= dt; if (c.emoteT <= 0f) c.emote = 0 }
            if (c.pop > 0f) c.pop -= dt
            when (c.state) {
                CState.WALK_IN -> {
                    tickPatience(c, dt * 0.5f)
                    if (c.followPath(dt, walk)) {
                        c.state = if (c.queueIdx == 0) CState.ORDERING else CState.QUEUE
                        if (c.state == CState.ORDERING) emit(GameEvent(Ev.ORDER, c.x, 1.6f, c.z))
                    }
                }
                CState.QUEUE -> {
                    tickPatience(c, dt)
                    val q = layout.queueSlots[c.queueIdx.coerceIn(0, layout.queueSlots.size - 1)]
                    if (hypot(q.x - c.x, q.z - c.z) > 0.05f) {
                        c.path = floatArrayOf(q.x, q.z); c.pathIdx = 0
                        c.followPath(dt, walk)
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
            if (c.state == CState.GONE) it.remove()
        }
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
        if (cfg.tutorial in 1..3) return
        c.patience -= dt
        if (c.patience <= 0f) {
            // walk out angry
            walkouts++
            c.angry = true; c.emote = 2; c.emoteT = 3f
            if (c.critic) criticsFailed++
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
        val tip = stats.tipBonus + (combo - 1f) * 0.25f + (if (Mutator.TIP_FRENZY in cfg.mutators) 0.3f else 0f)
        var pay = (base * (1f + tip)).toInt()
        if (c.vip) { pay *= 3; vipsServed++ }
        if (c.critic) { pay *= 2; criticsServed++; emit(GameEvent(Ev.CRITIC_HAPPY, c.x, 1.8f, c.z)) }
        pay = max(1, pay)
        revenue += pay
        counterPile.amount += pay
        counterPile.visual = min(40, counterPile.visual + max(1, pay / 6))
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
                    c.willSleep = TroubleKind.SLEEPY in cfg.troubles && troubleRng.chance(cfg.troubleRate * 0.06f)
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
            nextCarT = cfg.carInterval * rng.range(0.7f, 1.3f)
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
                            var pay = (car.orderValue() * 1.4f * stats.driveThruPayMul * (1f + stats.tipBonus + (combo - 1f) * 0.25f)).toInt()
                            pay = max(1, pay)
                            revenue += pay; served++; carsServed++
                            for (i in car.order.indices) itemsServed[i] += car.order[i]
                            drivePile!!.amount += pay
                            drivePile.visual = min(40, drivePile.visual + max(1, pay / 6)); drivePile.bounce = 0.3f
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
        if (cfg.tutorial in 1..9) return
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
    fun hire(role: StaffRole, post: Int) {
        val s = Staff(role, post, Species.entries[(staff.size * 3 + role.ordinal + 1) % 9])
        s.x = layout.doorX; s.z = layout.d + 0.6f; s.pop = 0.5f
        staff.add(s)
        s.state = SState.ENTER
        val dest = staffPost(s)
        s.path = nav.path(s.x, s.z, dest[0], dest[1]); s.pathIdx = 0
    }

    private fun staffPost(s: Staff): FloatArray = when (s.role) {
        StaffRole.CASHIER -> if (s.post == 1 && layout.driveCashier != null) floatArrayOf(layout.driveCashier!!.x, layout.driveCashier!!.z) else floatArrayOf(layout.cashierSpot.x, layout.cashierSpot.z)
        else -> floatArrayOf(layout.dropSpot.x - 1.2f, layout.dropSpot.z)
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
    private fun refreshPads(initial: Boolean = false) {
        var shown = 0
        for (p in pads) {
            if (p.done) continue
            if (!prerequisiteMet(p)) continue
            if (shown < 2) {
                if (!p.visible) { p.visible = true; p.pop = 0.5f; if (!initial) emit(GameEvent(Ev.FOCUS, p.x, 0f, p.z, a = 0.9f)) }
                shown++
            } else p.visible = false
        }
    }

    private fun prerequisiteMet(p: Pad): Boolean = when (p.spec.kind) {
        PadKind.UPGRADE_STATION -> stations[p.spec.ref].built
        else -> true
    }

    private fun updatePads(dt: Float) {
        val pl = player
        var onAny = false
        for (pad in pads) {
            if (pad.pop > 0f) pad.pop -= dt
            if (pad.flash > 0f) pad.flash -= dt
            if (!pad.visible || pad.done) continue
            if (near(pl.x, pl.z, pad.x, pad.z, 0.85f)) {
                onAny = true
                pl.padDwell += dt
                if (pl.padDwell < 0.22f) continue
                if (cash <= 0) continue
                val rate = max(pad.spec.cost / 1.3f, 12f)
                var amount = (rate * dt + padCarry).toInt()
                padCarry = rate * dt + padCarry - amount
                amount = min(amount, min(cash, pad.remaining))
                if (amount <= 0) continue
                cash -= amount; pad.paid += amount; pad.flash = 0.1f
                emit(GameEvent(Ev.PAY_TICK, pl.x, 1.2f, pl.z, x2 = pad.x, y2 = 0.1f, z2 = pad.z, a = pad.paid.toFloat() / pad.spec.cost))
                if (pad.remaining <= 0) completePad(pad)
            }
        }
        if (!onAny) { pl.padDwell = 0f; padCarry = 0f }
    }

    private var padCarry = 0f

    private fun completePad(pad: Pad) {
        pad.done = true; pad.visible = false
        val s = pad.spec
        when (s.kind) {
            PadKind.BUILD_STATION -> {
                val st = stations[s.ref]; st.built = true; st.pop = 0.6f; blockStation(st)
                ejectPlayer(st.px, st.pz + 1.2f)
                emit(GameEvent(Ev.BUILT, st.px, 0f, st.pz, text = st.spec.type.label))
            }
            PadKind.UPGRADE_STATION -> {
                val st = stations[s.ref]; st.level = min(3, st.level + 1); st.pop = 0.5f
                emit(GameEvent(Ev.UPGRADED, st.px, 0f, st.pz, a = st.level.toFloat(), text = st.spec.type.label))
            }
            PadKind.BUILD_TABLE -> {
                val t = tables[s.ref]; t.built = true; t.pop = 0.6f; blockTable(t)
                ejectPlayer(t.x, t.z + 1.3f)
                emit(GameEvent(Ev.BUILT, t.x, 0f, t.z, text = "Table"))
            }
            PadKind.BUILD_DRIVE -> {
                driveBuilt = true; drivePop = 0.6f; blockDrive()
                nextCarT = 2f
                val dv = layout.drive!!
                emit(GameEvent(Ev.BUILT, dv.x, 0f, dv.z, text = "Drive-Thru"))
            }
            PadKind.HIRE -> {
                val role = StaffRole.entries[s.ref / 10]
                hire(role, s.ref % 10)
                emit(GameEvent(Ev.HIRED, pad.x, 0f, pad.z, text = role.label))
            }
        }
        refreshPads()
    }

    private fun ejectPlayer(x: Float, z: Float) {
        if (blockedCircle(player.x, player.z, 0.24f)) { player.x = x; player.z = z; player.vx = 0f; player.vz = 0f }
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
                    if (near(p.x, p.z, t.x, t.z, 0.65f)) { t.progress += dt; if (t.progress >= 0.9f) fixTrouble(t) }
                    if (t.age > 45f) t.done = true
                }
                TroubleKind.FIRE, TroubleKind.JAM -> {
                    val st = t.station!!
                    if (near(p.x, p.z, st.pickX, st.pickZ, 1.3f)) {
                        t.progress += dt
                        if (t.progress >= (if (t.kind == TroubleKind.FIRE) 1.0f else 1.4f)) {
                            st.fire = false; st.jammed = false
                            fixTrouble(t)
                        }
                    }
                }
                TroubleKind.SLEEPY -> {
                    val c = t.customer!!
                    if (near(p.x, p.z, c.x, c.z, 1.15f)) {
                        t.progress += dt
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
            troublesFixed.add(TroubleKind.RACCOON)
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
        if (cfg.powerUps) {
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

    // ---------------------------------------------------------------- combo & end
    private fun updateCombo(dt: Float) {
        comboIdle += dt
        if (comboIdle > 12f && combo > 1f) { combo = max(1f, combo - 0.25f); comboIdle = 8f }
    }

    private fun checkEnd() {
        if (!goalReached && revenue >= cfg.target) {
            goalReached = true
            emit(GameEvent(Ev.GOAL_REACHED))
            if (inOvertime) { finish(true); return }
        }
        if (!warned && timeLeft <= 15f && !inOvertime) { warned = true; emit(GameEvent(Ev.TIMER_WARN)) }
        if (finishRequested && goalReached) { finish(true); return }
        if (time >= cfg.shiftSeconds && !inOvertime) {
            if (goalReached) finish(true)
            else if (cfg.overtime) inOvertime = true
            else finish(false)
        }
    }

    private fun finish(win: Boolean) {
        finished = true
        won = win
        stars = if (!win) 0 else {
            var s = 1
            if (revenue >= cfg.star2) s = 2
            val criticsOk = cfg.critics == 0 || criticsFailed == 0
            if (revenue >= cfg.star3 && walkouts <= cfg.maxWalkouts3 && criticsOk) s = 3
            if (cfg.tutorial in 1..3) s = maxOf(s, 3)  // first tutorials always shine
            s
        }
    }

    /** Debug tooling: 1 = instant 3-star win, 2 = +500 cash. */
    fun debugAction(kind: Int) {
        when (kind) {
            1 -> { revenue = maxOf(revenue, cfg.star3); served = maxOf(served, 12); goalReached = true; finish(true) }
            2 -> cash += 500
        }
    }

    fun result() = ShiftResult(cfg.id, won, stars, revenue, cfg.target, served, walkouts, bestCombo, timeLeft, itemsServed.copyOf())

    // ---------------------------------------------------------------- queries for UI/bot
    fun visiblePads() = pads.filter { it.visible && !it.done }

    fun guidanceTarget(): FloatArray? {
        // Where should a beginner go next? Used for tutorial arrows.
        val p = player
        val pad = visiblePads().firstOrNull { cash >= min(it.remaining, 5) }
        if (pad != null && (cfg.tutorial > 0)) return floatArrayOf(pad.x, pad.z)
        if (hasTrays(p.stack)) return floatArrayOf(layout.trash.x, layout.trash.z)
        if (hasFood(p.stack)) {
            return if (driveBuilt && carQueue.isNotEmpty() && queue.isEmpty()) floatArrayOf(layout.driveDrop!!.x, layout.driveDrop!!.z)
            else floatArrayOf(layout.dropSpot.x, layout.dropSpot.z - 0.2f)
        }
        if (counterPile.amount >= 15 && cfg.tutorial > 0) return floatArrayOf(counterPile.x, counterPile.z)
        val front = queue.firstOrNull()
        if (front != null && front.state == CState.ORDERING) {
            var lacking = false
            for (i in front.order.indices) if (front.order[i] - front.got[i] > counterRacks[i]) lacking = true
            if (!lacking && !cashierPresent && staff.none { it.role == StaffRole.CASHIER && it.post == 0 }) return floatArrayOf(layout.cashierSpot.x, layout.cashierSpot.z)
        }
        val st = stations.filter { it.built && it.tray > 0 }.maxByOrNull { it.tray }
        if (st != null) return floatArrayOf(st.pickX, st.pickZ)
        if (tables.any { it.trays > 0 }) { val t = tables.first { it.trays > 0 }; return floatArrayOf(t.x, t.z + 0.9f) }
        return null
    }
}
