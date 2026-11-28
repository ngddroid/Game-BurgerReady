package com.ngstudio.burgerready.game

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Continuous "arcade idle" progression (docs/ARCHITECTURE.md §4).
 *
 * The game is a chain of persistent restaurants, one per [Stores] entry. Each restaurant has a fixed,
 * ordered build plan of pads (stations, tables, upgrades, hires, drive-thru, and finally EXPAND, which
 * opens the next restaurant). Everything bought stays bought: the save stores the ids of completed pads
 * and any partial payment, and [World] restores them on load. There is no timer and no fail state.
 */
enum class PadKind { BUILD_STATION, BUILD_TABLE, BUILD_DRIVE, UPGRADE_STATION, HIRE, EXPAND }

/** A kitchen station: which machine, what it makes, and which back-wall slot of the layout it uses. */
data class StationSpec(val type: StationType, val item: Item, val slot: Int)

/**
 * One purchase in a restaurant's build plan. [id] is its position in the plan and is what the save keeps.
 * ref: station index / table index / 0; for HIRE, ref = StaffRole ordinal * 10 + post
 * (cashier: 0 counter, 1 drive-thru; runner/cleaner: instance number).
 */
data class PadSpec(val id: Int, val kind: PadKind, val ref: Int, val cost: Long)

class RestaurantConfig(
    val index: Int,
    val storeId: String,
    val name: String,
    val layoutId: Int,
    val menu: List<Item>,
    val menuWeights: FloatArray,
    val stations: List<StationSpec>,
    val tableCount: Int,
    val drive: Boolean,
    val pads: List<PadSpec>,
    /** Money scale of this restaurant: item prices and pad costs are multiplied by it. */
    val priceMul: Float,
    val patience: Float,
    val orderMean: Float,
    val orderMax: Int,
    val vipChance: Float,
    val dineIn: Float,
    val troubleRate: Float,
    val troubles: List<TroubleKind>,
    val critics: Boolean,
    val groupArrivals: Boolean,
    val maxQueue: Int,
    val seed: Long,
    /** Shortest gap between arrivals in seconds (bigger restaurants draw bigger crowds). Classic: from the index. */
    val arrivalFloor: Float = max(0.9f, 1.45f - 0.05f * index),
    /** World Tour: estimated full-build earnings per second (base units); drives costs and idle income. */
    val earnPerSec: Double = 0.0,
) {
    val isFirst get() = index == 0
    /** Candy Kingdom: the classic finale restaurant (no NEW RESTAURANT pad; the World Tour opens from its finale). */
    val isLast get() = index == Restaurants.CLASSIC - 1
    val isTour get() = index >= Restaurants.CLASSIC
    /** The last World Tour flagship: nothing after it. */
    val isTourEnd get() = index == Restaurants.TOTAL - 1
    val expandPad: PadSpec? get() = pads.lastOrNull()?.takeIf { it.kind == PadKind.EXPAND }
    /** Pads that count towards "fully built" (everything except EXPAND). */
    val buildPads: Int get() = pads.count { it.kind != PadKind.EXPAND }
}

/** Saved state of one restaurant: completed pad ids and partial payments (pad id → amount paid). */
data class RestaurantProgress(val done: Set<Int> = emptySet(), val paid: Map<Int, Long> = emptyMap()) {
    fun builtCount(cfg: RestaurantConfig) = cfg.pads.count { it.kind != PadKind.EXPAND && it.id in done }
    fun fraction(cfg: RestaurantConfig) = builtCount(cfg).toFloat() / max(1, cfg.buildPads)
    fun expanded(cfg: RestaurantConfig) = cfg.expandPad?.let { it.id in done } ?: (builtCount(cfg) >= cfg.buildPads)
}

/** Deterministic hashing helpers (SplitMix64 finaliser). */
object Hash {
    fun mix(x: Long): Long {
        var z = x + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        return z and Long.MAX_VALUE
    }
}

/** Small deterministic PRNG (SplitMix64) so the simulation never depends on platform Random. */
class Rng(seed: Long) {
    private var s = seed
    fun nextLong(): Long { s += -0x61c8864680b583ebL; return Hash.mix(s) }
    fun float(): Float = (nextLong() ushr 40).toFloat() / (1L shl 24).toFloat()
    fun range(a: Float, b: Float) = a + (b - a) * float()
    fun int(n: Int): Int = if (n <= 1) 0 else ((nextLong() ushr 1) % n).toInt()
    fun chance(p: Float) = float() < p
    fun <T> pick(list: List<T>): T = list[int(list.size)]
}

object Restaurants {
    /** The 12 classic restaurants (the Voyage). They never change (ClassicGoldenTest). */
    const val CLASSIC = 12
    /** Classic + World Tour (game/Tour.kt). */
    val TOTAL: Int get() = CLASSIC + Tour.COUNT

    /** Money multiplier per restaurant: each new location is a clear step up in earnings and prices. */
    private val PRICE = floatArrayOf(1f, 2.4f, 5f, 9f, 16f, 28f, 46f, 75f, 120f, 185f, 280f, 420f)

    fun priceMul(index: Int): Float = if (index < CLASSIC) PRICE[index.coerceIn(0, PRICE.size - 1)] else Tour.priceMul(index)
    fun layoutFor(index: Int) = min(Layouts.CLASSIC, index + 1)
    /** Global index (0..TOTAL-1) of a classic or World Tour restaurant; unknown ids map to 0 (as before). */
    fun indexOf(storeId: String): Int = Stores.ALL.indexOfFirst { it.id == storeId }.let { if (it >= 0) it else (Tour.indexOf(storeId) ?: 0) }
    fun storeAt(index: Int): StoreDef = if (index < CLASSIC) Stores.ALL[index.coerceIn(0, CLASSIC - 1)] else Tour.store(index.coerceAtMost(TOTAL - 1))
    fun nextStoreId(storeId: String): String? { val i = indexOf(storeId) + 1; return if (i < TOTAL) storeAt(i).id else null }

    /** Menu items introduced per restaurant, in the order their stations are built. */
    fun menuFor(index: Int): List<Item> = when (index) {
        0 -> listOf(Item.BURGER, Item.FRIES)
        1 -> listOf(Item.BURGER, Item.FRIES, Item.SODA)
        2 -> listOf(Item.BURGER, Item.FRIES, Item.SODA, Item.CHEESE)
        3 -> listOf(Item.BURGER, Item.FRIES, Item.CHEESE, Item.SODA, Item.DOUBLE)
        4 -> listOf(Item.BURGER, Item.FRIES, Item.SODA, Item.CHEESE, Item.SHAKE, Item.DOUBLE)
        else -> listOf(Item.BURGER, Item.FRIES, Item.SODA, Item.CHEESE, Item.SHAKE, Item.DOUBLE, Item.VEGGIE)
    }

    fun troublesFor(index: Int): List<TroubleKind> {
        val out = ArrayList<TroubleKind>()
        if (index >= 1) out.add(TroubleKind.SPILL)
        if (index >= 2) out.add(TroubleKind.FIRE)
        if (index >= 3) out.add(TroubleKind.RACCOON)
        if (index >= 4) out.add(TroubleKind.SLEEPY)
        if (index >= 5) out.add(TroubleKind.JAM)
        return out
    }

    private val cache = HashMap<Int, RestaurantConfig>()

    fun config(storeId: String) = config(indexOf(storeId))

    fun config(index: Int): RestaurantConfig {
        val i = index.coerceIn(0, TOTAL - 1)
        return synchronized(cache) { cache.getOrPut(i) { if (i < CLASSIC) build(i) else Tour.build(i) } }
    }


    /** Stations fill the back-wall slots nearest the counter first, so the first machines are a short walk away. */
    internal fun stationSpecs(layout: Layout, menu: List<Item>): List<StationSpec> {
        val slotOrder = layout.stationSlots.indices.sortedBy { abs(layout.stationSlots[it].x - layout.dropSpot.x) }
        val stations = ArrayList<StationSpec>()
        for (k in slotOrder.indices) {
            val item = if (k < menu.size) menu[k] else (if ((k - menu.size) % 2 == 0) Item.BURGER else menu[min(menu.size - 1, 3)])
            stations.add(StationSpec(item.station!!, item, slotOrder[k]))
        }
        return stations
    }

    /** One step of a build plan: what the pad builds and its cost weight. */
    class PlanStep(val kind: PadKind, val ref: Int, val weight: Float)

    /**
     * The build plan's order (two pads are visible at a time, in this order): first station, its table, a second
     * station and table, an upgrade and a cashier; then stations, tables and level-2 upgrades interleaved with staff
     * and the drive-thru at fixed fractions; then level-3 machines.
     */
    internal fun planSteps(stationCount: Int, tables: Int, tableOrder: List<Int>, drive: Boolean): List<PlanStep> {
        val steps = ArrayList<PlanStep>()
        fun st(i: Int) = PlanStep(PadKind.BUILD_STATION, i, 1f)
        fun tb(i: Int) = PlanStep(PadKind.BUILD_TABLE, tableOrder[i], 0.75f)
        fun up(i: Int) = PlanStep(PadKind.UPGRADE_STATION, i, 1.2f)
        fun hire(role: StaffRole, post: Int) = PlanStep(PadKind.HIRE, role.ordinal * 10 + post, 1.35f)
        val S = stationCount
        steps.add(st(0))
        if (tables > 0) steps.add(tb(0))
        steps.add(st(1))
        if (tables > 1) steps.add(tb(1))
        steps.add(up(0))
        steps.add(hire(StaffRole.CASHIER, 0))
        // interleave the rest: new stations, new tables, level-2 upgrades
        val a = (2 until S).map { st(it) }
        val b = (2 until tables).map { tb(it) }
        val c = (1 until S).map { up(it) }
        val mid = ArrayList<PlanStep>()
        var i = 0
        while (i < max(a.size, max(b.size, c.size))) {
            if (i < a.size) mid.add(a[i])
            if (i < b.size) mid.add(b[i])
            if (i < c.size) mid.add(c[i])
            i++
        }
        // staff and the drive-thru slot into the middle section at fixed fractions
        val inserts = ArrayList<Pair<Float, PlanStep>>()
        inserts.add(0.15f to hire(StaffRole.CLEANER, 0))
        inserts.add(0.45f to hire(StaffRole.RUNNER, 0))
        if (drive) { inserts.add(0.30f to PlanStep(PadKind.BUILD_DRIVE, 0, 1.4f)); inserts.add(0.55f to hire(StaffRole.CASHIER, 1)) }
        if (S >= 6) inserts.add(0.80f to hire(StaffRole.RUNNER, 1))
        if (tables >= 7) inserts.add(0.90f to hire(StaffRole.CLEANER, 1))
        for ((f, s) in inserts.sortedByDescending { it.first }) mid.add((mid.size * f).roundToInt().coerceIn(0, mid.size), s)
        steps.addAll(mid)
        // final polish: level-3 machines, busiest first
        for (k in 0 until S) steps.add(up(k))

        return steps
    }

    private fun build(index: Int): RestaurantConfig {
        val store = storeAt(index)
        val layout = Layouts.get(layoutFor(index))
        val p = priceMul(index)
        val menu = menuFor(index)

        val stations = stationSpecs(layout, menu)
        val weights = FloatArray(menu.size) { i ->
            when (menu[i].station) { StationType.GRILL -> 1.0f; StationType.FRYER -> 0.85f; StationType.SODA -> 0.7f; else -> 0.6f }
        }
        val tableOrder = layout.tables.indices.sortedBy { val t = layout.tables[it].p; abs(t.x - layout.registerX) + abs(t.z - layout.counter.z) * 0.8f }
        val tables = layout.tables.size
        val drive = layout.drive != null && index >= 2

        val steps = planSteps(stations.size, tables, tableOrder, drive)

        // ---- costs: smooth geometric curve, scaled per kind and by the restaurant's money multiplier
        // The first restaurant teaches with a $10 grill; every later one opens with its first grill already
        // running (cost 0 = built on arrival), so the player can earn from the first second.
        val n = steps.size
        val first = 10f
        val last = LAST_PAD * (1f + LAST_GROWTH * index)
        val g = (last / first).toDouble().pow(1.0 / max(1, n - 1)).toFloat()
        val pads = ArrayList<PadSpec>()
        steps.forEachIndexed { k, s ->
            val raw = first * g.toDouble().pow(k).toFloat() * (if (k == 0) 1f else s.weight)
            val cost = if (k == 0 && index > 0) 0L else roundCost(raw * p)
            pads.add(PadSpec(k, s.kind, s.ref, cost))
        }
        if (index < CLASSIC - 1) pads.add(PadSpec(n, PadKind.EXPAND, index + 1, roundCost(last * EXPAND_MUL * p)))

        return RestaurantConfig(
            index = index, storeId = store.id, name = store.name, layoutId = layout.id,
            menu = menu, menuWeights = weights, stations = stations,
            tableCount = tables, drive = drive, pads = pads, priceMul = p,
            patience = max(38f, 72f - index * 3f),
            orderMean = min(2.8f, 1.15f + index * 0.16f),
            orderMax = 2 + min(4, index / 2 + 1),
            vipChance = 0.03f + 0.008f * index,
            dineIn = 0.55f,
            troubleRate = if (index == 0) 0f else min(1.6f, 0.35f + 0.12f * index),
            troubles = troublesFor(index),
            critics = index >= 1,
            groupArrivals = index >= 3,
            maxQueue = min(12, 7 + index),
            seed = Hash.mix(index * 7349L + 11),
        )
    }

    /** Pacing knobs (see RestaurantFlowTest): price of the last build pad in restaurant 0, its growth per restaurant, EXPAND multiple. */
    private const val LAST_PAD = 1300f
    private const val LAST_GROWTH = 0.45f
    private const val EXPAND_MUL = 2.4f

    /** Rounds a price to a friendly step. Float maths kept exactly as before (classic costs must not move). */
    fun roundCost(v: Float): Long {
        val x = max(5f, v)
        val step = when { x < 50 -> 5f; x < 200 -> 10f; x < 1000 -> 25f; x < 5000 -> 50f; x < 50_000 -> 250f; x < 500_000 -> 1000f; else -> 5000f }
        return (floor(x / step) * step).toLong()
    }

    /** [roundCost] for the World Tour's large values: Double maths, then about three significant digits. */
    fun roundCostD(v: Double): Long {
        val x = kotlin.math.max(5.0, v)
        if (x < 500_000.0) return roundCost(x.toFloat())
        var step = 5000.0
        while (x / step >= 1000.0) step *= 10.0
        return (kotlin.math.floor(x / step) * step).toLong()
    }

    // ------------------------------------------------------------------------------------------ meta economy
    /** Passive income per hour of a restaurant (before renovation / Store Income upgrade). */
    fun incomePerHour(index: Int, builtFraction: Float): Long {
        val full = if (index < CLASSIC) 900.0 * priceMul(index) else config(index).earnPerSec * 3600.0 * Tour.IDLE_SHARE * priceMul(index)
        return (full * (0.15 + 0.85 * builtFraction.coerceIn(0f, 1f))).roundToLong()
    }

    /** A "typical" spend for chest / ad / daily rewards at this stage of the game. */
    fun rewardUnit(index: Int): Long = (120.0 * priceMul(index)).toLong() / 10 * 10

    /** Typical Kitchen upgrade levels a player has when starting restaurant [index] (used by tests). */
    fun expectedUpgrades(index: Int): Map<Upgrade, Int> = Upgrade.entries.associateWith { u ->
        when (u) { Upgrade.INCOME, Upgrade.PRICES -> min(u.maxLevel, index); else -> min(u.maxLevel, index * 2) }
    }
}
