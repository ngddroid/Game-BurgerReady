package com.ngstudio.burgerready.game

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class PadKind { BUILD_STATION, BUILD_TABLE, BUILD_DRIVE, UPGRADE_STATION, HIRE }

data class StationSpec(val type: StationType, val item: Item, val slot: Int, val prebuilt: Boolean, val level: Int = 1)

/** ref: station index / table index / 0; for HIRE, ref = StaffRole ordinal * 10 + post (0 counter, 1 drive). */
data class PadSpec(val kind: PadKind, val ref: Int, val cost: Int)

data class RushWave(val start: Float, val duration: Float, val mult: Float)

data class LevelConfig(
    val id: Int,
    val chapter: Int,
    val slot: Int,
    val difficulty: Float,
    val boss: BossType?,
    val mutators: List<Mutator>,
    val layoutId: Int,
    val mirror: Boolean,
    val shiftSeconds: Float,
    val overtime: Boolean,
    val menu: List<Item>,
    val menuWeights: FloatArray,
    val stations: List<StationSpec>,
    val tablesPrebuilt: Int,
    val tableCount: Int,
    val drive: Boolean,
    val drivePrebuilt: Boolean,
    val pads: List<PadSpec>,
    val startCash: Int,
    val spawnInterval: Float,
    val rush: List<RushWave>,
    val maxQueue: Int,
    val patience: Float,
    val orderMean: Float,
    val orderMax: Int,
    val vipChance: Float,
    val carInterval: Float,
    val dineIn: Float,
    val troubleRate: Float,
    val troubles: List<TroubleKind>,
    val powerUps: Boolean,
    val critics: Int,
    val groupArrivals: Boolean,
    var target: Int,
    var star2: Int,
    var star3: Int,
    val maxWalkouts3: Int,
    val tutorial: Int,       // 0 = none, else tutorial script id (1..10)
    val seed: Long,
    val title: String,
) {
    val isBoss get() = boss != null
}

/**
 * Difficulty script (docs/MASTER_GAME_PLAN.md §5.3). D ∈ [0,1]; deterministic per level id.
 */
object Difficulty {
    private val SAW = floatArrayOf(-0.05f, -0.03f, -0.01f, 0.00f, 0.01f, 0.02f, 0.03f, 0.04f, 0.06f)

    fun base(n: Int): Float {
        if (n <= 10) return 0.02f * n
        val t = (n - 10).toFloat()
        return (0.15f + 0.35f * (1f - exp(-t / 60f)) + 0.40f * (1f - exp(-t / 900f)) + 0.10f * (t / 9990f)).coerceIn(0f, 1f)
    }

    fun slotOf(n: Int) = ((n - 1) % 10) + 1
    fun chapterOf(n: Int) = (n - 1) / 10 + 1

    fun of(n: Int): Float {
        if (n <= 10) return base(n)
        val slot = slotOf(n)
        var d = base(n)
        d += if (slot == 10) 0.05f else SAW[slot - 1]
        if (n % 50 == 0 && slot != 10) d -= 0.08f
        if (n % 50 == 0) d -= 0.03f // boss on a breather level is a little gentler
        d += (hash(n.toLong() * 7919L) % 2001 - 1000) / 100000f // ±0.01 jitter
        return d.coerceIn(0f, 1f)
    }

    fun hash(x: Long): Long {
        var z = x + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        return z and Long.MAX_VALUE
    }
}

/** Small deterministic PRNG (SplitMix64) so level generation never depends on platform Random. */
class Rng(seed: Long) {
    private var s = seed
    fun nextLong(): Long { s += -0x61c8864680b583ebL; return Difficulty.hash(s) }
    fun float(): Float = (nextLong() ushr 40).toFloat() / (1L shl 24).toFloat()
    fun range(a: Float, b: Float) = a + (b - a) * float()
    fun int(n: Int): Int = if (n <= 1) 0 else ((nextLong() ushr 1) % n).toInt()
    fun chance(p: Float) = float() < p
    fun <T> pick(list: List<T>): T = list[int(list.size)]
}

object LevelGenerator {
    const val MAX_LEVEL = 10_000

    private val tuned = HashMap<Int, IntArray>()

    /** Install offline-tuned targets (assets/levels/targets.bin): per level target, star2, star3. */
    fun installTargets(data: IntArray) {
        tuned.clear()
        var i = 0
        while (i + 3 < data.size) {
            tuned[data[i]] = intArrayOf(data[i + 1], data[i + 2], data[i + 3]); i += 4
        }
    }

    fun hasTuned(id: Int) = tuned.containsKey(id)

    fun generate(id: Int): LevelConfig {
        val cfg = if (id <= 10) Tutorial.level(id) else procedural(id)
        tuned[id]?.let { cfg.target = it[0]; cfg.star2 = it[1]; cfg.star3 = it[2] }
        return cfg
    }

    fun titleFor(id: Int): String {
        if (id <= 10) return Tutorial.TITLES[id - 1]
        val boss = bossFor(id)
        if (boss != null) return boss.label
        val r = Rng(id * 31L + 5)
        val a = listOf("Sunny", "Busy", "Lazy", "Crispy", "Sizzling", "Golden", "Cozy", "Breezy", "Snappy", "Hungry", "Happy", "Juicy", "Toasty", "Zesty", "Rowdy", "Mellow")
        val b = listOf("Morning", "Lunch", "Brunch", "Afternoon", "Evening", "Supper", "Weekend", "Shift", "Rush", "Service", "Diner Day", "Grill Day")
        return "${r.pick(a)} ${r.pick(b)}"
    }

    fun bossFor(id: Int): BossType? {
        if (id == 10) return BossType.CRITIC   // the tutorial finale is the first (gentle) boss
        if (id < 10 || id % 10 != 0) return null
        val k = (id / 10 - 2) % BossType.entries.size
        return BossType.entries[(k + BossType.entries.size) % BossType.entries.size]
    }

    // ----------------------------------------------------------------------------------- procedural
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun procedural(id: Int): LevelConfig {
        val d = Difficulty.of(id)
        val rng = Rng(Difficulty.hash(id.toLong() * 104729L))
        val slot = Difficulty.slotOf(id)
        val chapter = Difficulty.chapterOf(id)
        val boss = bossFor(id)

        // ---- mutators
        val mutators = ArrayList<Mutator>()
        if (id >= 50 && boss == null) {
            val count = when { d < 0.35f -> if (rng.chance(0.35f)) 1 else 0; d < 0.7f -> 1; else -> if (rng.chance(0.6f)) 2 else 1 }
            val pool = Mutator.entries.toMutableList()
            if (id < 100) pool.remove(Mutator.DOUBLE_DRIVE)
            repeat(count) { if (pool.isNotEmpty()) { val m = pool[rng.int(pool.size)]; pool.remove(m); mutators.add(m) } }
        }

        // ---- layout
        val unlocked = Layouts.unlockedFor(id)
        var layoutId = if (boss == BossType.RALLY) unlocked.filter { Layouts.hasDrive(it) }.let { if (it.isEmpty()) unlocked.last() else rng.pick(it.takeLast(3)) }
        else {
            // Prefer the newest layouts, sometimes revisit older ones (cheaper travel = relief).
            val recent = unlocked.takeLast(3)
            if (rng.chance(0.75f)) rng.pick(recent) else rng.pick(unlocked)
        }
        if (boss == BossType.REOPENING) layoutId = unlocked.last()
        val mirror = rng.chance(0.5f)
        val layout = Layouts.get(layoutId, mirror)

        // ---- menu
        val unlockedFoods = Item.FOODS.filter { it.unlockLevel <= id }
        val menuSize = when (boss) {
            BossType.CONTEST -> 1
            else -> min(unlockedFoods.size, min(layout.stationSlots.size, 2 + floor(d * 4.2f).toInt()))
        }
        val menu = ArrayList<Item>()
        val grills = unlockedFoods.filter { it.station == StationType.GRILL }
        if (boss == BossType.CONTEST) menu.add(Item.BURGER) else {
            // Feature the most recently unlocked item for a while after it appears.
            val newest = unlockedFoods.maxByOrNull { it.unlockLevel }!!
            if (id - newest.unlockLevel < 25) menu.add(newest)
            if (menu.none { it.station == StationType.GRILL }) menu.add(rng.pick(grills.takeLast(3)))
            val rest = unlockedFoods.filter { it !in menu }.toMutableList()
            while (menu.size < menuSize && rest.isNotEmpty()) { val it = rest[rng.int(rest.size)]; rest.remove(it); menu.add(it) }
        }
        val weights = FloatArray(menu.size) { i ->
            val it = menu[i]
            var w = when (it.station) { StationType.GRILL -> 1.0f; StationType.FRYER -> 0.8f; StationType.SODA -> 0.7f; else -> 0.6f }
            if (Mutator.HEAT_WAVE in mutators && (it == Item.SODA || it == Item.SHAKE)) w *= 2.2f
            w
        }

        // ---- stations: one per menu item, extra grill when space and difficulty allow
        val stations = ArrayList<StationSpec>()
        var slotIdx = 0
        val order = menu.sortedBy { if (it.station == StationType.GRILL) 0 else 1 }
        for (it in order) { if (slotIdx < layout.stationSlots.size) stations.add(StationSpec(it.station!!, it, slotIdx++, true)) }
        if (slotIdx < layout.stationSlots.size && d > 0.3f && rng.chance(0.4f + d * 0.4f)) {
            stations.add(StationSpec(order.first().station!!, order.first(), slotIdx++, true))
        }

        // ---- economy unit for pad costs: average order value
        val avgPrice = menu.indices.sumOf { (menu[it].price * weights[it]).toDouble() }.toFloat() / weights.sum()
        val orderMean = lerp(1.1f, 3.4f, d) * (if (Mutator.PICKY_EATERS in mutators) 1.35f else 1f) * (if (boss == BossType.CELEBRITY) 1.6f else 1f)
        val unit = max(4f, avgPrice * orderMean)
        fun cost(mult: Float) = roundCost(unit * mult)

        // Which stations start unbuilt?
        val reopening = boss == BossType.REOPENING
        val unbuiltCount = if (reopening) stations.size - 1 else min(stations.size - 1, (rng.float() * (1 + d * 2.2f)).toInt())
        val unbuilt = stations.indices.drop(1).shuffledWith(rng).take(unbuiltCount).toSet()
        for (i in unbuilt) stations[i] = stations[i].copy(prebuilt = false)

        // ---- tables & drive
        val tableCount = layout.tables.size
        val tablesPrebuilt = if (reopening) 1 else max(1, (tableCount * lerp(0.7f, 0.35f, d)).roundToInt())
        val drive = layout.drive != null && (boss == BossType.RALLY || Mutator.DOUBLE_DRIVE in mutators || rng.chance(0.45f + d * 0.4f))
        val drivePrebuilt = drive && !reopening && rng.chance(0.5f)

        // ---- pads, in the order they will appear
        val pads = ArrayList<PadSpec>()
        stations.forEachIndexed { i, s -> if (!s.prebuilt) pads.add(PadSpec(PadKind.BUILD_STATION, i, cost(5.5f + i * 1.5f))) }
        for (t in tablesPrebuilt until tableCount) {
            if (pads.size >= 9) break
            pads.add(PadSpec(PadKind.BUILD_TABLE, t, cost(2.4f + (t - tablesPrebuilt) * 0.5f)))
            if (t - tablesPrebuilt >= 2) break
        }
        if (drive && !drivePrebuilt) pads.add(PadSpec(PadKind.BUILD_DRIVE, 0, cost(9f)))
        if (id >= 7) pads.add(PadSpec(PadKind.HIRE, StaffRole.CASHIER.ordinal * 10, cost(7f)))
        val upgrades = stations.indices.toList().shuffledWith(rng).take(1 + (d * 2.5f).toInt())
        for (i in upgrades) pads.add(PadSpec(PadKind.UPGRADE_STATION, i, cost(8f + stations.size)))
        if (id >= 18) pads.add(PadSpec(PadKind.HIRE, StaffRole.RUNNER.ordinal * 10, cost(11f)))
        if (id >= 30) pads.add(PadSpec(PadKind.HIRE, StaffRole.CLEANER.ordinal * 10, cost(9f)))
        if (drive && id >= 40) pads.add(PadSpec(PadKind.HIRE, StaffRole.CASHIER.ordinal * 10 + 1, cost(9f)))
        if (pads.size > 2) {
            // light seeded shuffle that keeps station builds first
            val head = pads.filter { it.kind == PadKind.BUILD_STATION }
            val tail = pads.filter { it.kind != PadKind.BUILD_STATION }.shuffledWith(rng)
            pads.clear(); pads.addAll(head); pads.addAll(tail)
        }

        // ---- demand
        val capacity = stations.filter { it.prebuilt }.sumOf { (1.0 / it.item.cookTime) }.toFloat().coerceAtLeast(0.3f)
        val ratio = lerp(0.55f, 1.1f, d)
        var interval = (orderMean / (ratio * capacity * 0.85f)).coerceIn(1.3f, 6f)
        if (Mutator.HUNGRY_HOUR in mutators) interval /= 1.3f
        if (boss == BossType.FESTIVAL) interval /= 1.25f
        val rushCount = when (boss) { BossType.FESTIVAL -> 3 + (d * 2).toInt(); else -> if (d < 0.18f) (if (rng.chance(0.5f)) 1 else 0) else 1 + (d * 2.6f).toInt() }
        var shift = lerp(150f, 200f, (layoutId - 1) / 7f) + (if (boss != null) 30f else 0f)
        shift = (shift / 5f).roundToInt() * 5f
        val rush = ArrayList<RushWave>()
        for (k in 0 until rushCount) {
            val start = shift * (0.2f + 0.7f * (k + rng.float() * 0.6f) / max(1, rushCount))
            rush.add(RushWave(start.coerceAtMost(shift - 20f), rng.range(12f, 18f + d * 6f), rng.range(1.7f, 2.2f + d * 0.5f)))
        }
        var patience = lerp(62f, 22f, d)
        if (Mutator.TIP_FRENZY in mutators) patience *= 0.8f
        val troublesEnabled = TroubleKind.entries.filter { troubleUnlock(it) <= id }
        val troubleRate = if (troublesEnabled.isEmpty()) 0f else lerp(0.35f, 2.4f, d) * (if (Mutator.SLIPPERY in mutators) 1.5f else 1f)
        val tKinds = if (Mutator.SLIPPERY in mutators && TroubleKind.SPILL in troublesEnabled) troublesEnabled + TroubleKind.SPILL + TroubleKind.SPILL else troublesEnabled
        val critics = when { boss == BossType.CRITIC -> 3; Mutator.INSPECTION in mutators -> 1; else -> 0 }
        val vip = if (id < 8) 0f else (0.03f + d * 0.06f) * (if (boss == BossType.CELEBRITY) 4f else 1f)
        val carInterval = lerp(15f, 6.5f, d) / (if (Mutator.DOUBLE_DRIVE in mutators || boss == BossType.RALLY) 2f else 1f)

        // Placeholder targets (replaced by tuned table / LevelTuner); a sane analytic estimate.
        val est = estimateRevenue(shift, interval, orderMean, avgPrice, capacity, rush)
        val eff = lerp(0.42f, 0.86f, d)
        val target = roundTarget(est * eff)
        return LevelConfig(
            id = id, chapter = chapter, slot = slot, difficulty = d, boss = boss, mutators = mutators,
            layoutId = layoutId, mirror = mirror, shiftSeconds = shift, overtime = false,
            menu = menu, menuWeights = weights, stations = stations,
            tablesPrebuilt = tablesPrebuilt, tableCount = tableCount, drive = drive, drivePrebuilt = drivePrebuilt,
            pads = pads, startCash = cost(2.2f) + (if (pads.firstOrNull()?.kind == PadKind.BUILD_STATION) cost(3f) else 0),
            spawnInterval = interval, rush = rush, maxQueue = 12,
            patience = patience, orderMean = orderMean, orderMax = max(2, (orderMean * 2.2f).roundToInt()),
            vipChance = vip, carInterval = carInterval, dineIn = 0.5f,
            troubleRate = troubleRate, troubles = tKinds, powerUps = id >= 12,
            critics = critics, groupArrivals = Mutator.BIG_PARTY in mutators,
            target = target, star2 = roundTarget(target * 1.25f), star3 = roundTarget(target * 1.5f),
            maxWalkouts3 = if (d < 0.5f) 1 else 2,
            tutorial = 0, seed = Difficulty.hash(id * 7349L + 11), title = titleFor(id),
        )
    }

    fun troubleUnlock(k: TroubleKind) = when (k) {
        TroubleKind.SPILL -> 15; TroubleKind.FIRE -> 25; TroubleKind.RACCOON -> 35; TroubleKind.JAM -> 75; TroubleKind.SLEEPY -> 45
    }

    fun estimateRevenue(shift: Float, interval: Float, orderMean: Float, avgPrice: Float, capacity: Float, rush: List<RushWave>): Float {
        var customers = shift / interval
        for (r in rush) customers += r.duration / interval * (r.mult - 1f)
        val demand = customers * orderMean
        val supply = capacity * shift * 1.1f
        return min(demand, supply) * avgPrice * 0.8f
    }

    fun roundCost(v: Float): Int {
        val x = max(5f, v)
        val step = when { x < 50 -> 5f; x < 200 -> 10f; x < 1000 -> 25f; x < 5000 -> 50f; else -> 100f }
        return (floor(x / step) * step).toInt()
    }

    fun roundTarget(v: Float): Int {
        val x = max(20f, v)
        val step = when { x < 200 -> 5f; x < 1000 -> 10f; x < 5000 -> 25f; else -> 50f }
        return ((x / step).roundToInt() * step).toInt()
    }

    private fun <T> List<T>.shuffledWith(rng: Rng): List<T> {
        val a = toMutableList()
        for (i in a.size - 1 downTo 1) { val j = rng.int(i + 1); val t = a[i]; a[i] = a[j]; a[j] = t }
        return a
    }

    /** Bucks reward for a level (before star multiplier). */
    fun reward(id: Int): Int {
        val base = 50.0 + 12.0 * id.toDouble().pow(0.8)
        val boss = if (bossFor(id) != null) 2.0 else 1.0
        return (base * boss).roundToInt()
    }

    fun starMultiplier(stars: Int) = when (stars) { 3 -> 1.6f; 2 -> 1.25f; 1 -> 1f; else -> 0.25f }
}

/** Handcrafted tutorial levels 1–10 (cannot be failed: overtime instead of a fail state). */
object Tutorial {
    val TITLES = listOf("Grand Opening", "Stack 'Em High", "Take a Seat", "Fry Time", "First Rush!",
        "Fizz Fountain", "Hire a Helper", "VIP Visit", "Drive-Thru Debut", "Critic's Day")

    fun level(id: Int): LevelConfig {
        val st = ArrayList<StationSpec>()
        val pads = ArrayList<PadSpec>()
        var layout = 1
        var menu = listOf(Item.BURGER)
        var target = 60
        var tablesPre = 0
        var tableCount = 2
        var drive = false
        var rush = emptyList<RushWave>()
        var vip = 0f
        var critics = 0
        var interval = 4.2f
        var startCash = 15
        var order = 1.0f
        var dineIn = 0.0f
        when (id) {
            1 -> {
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 1, false))
                pads.add(PadSpec(PadKind.BUILD_STATION, 0, 10))
                target = 60; interval = 4.0f; tableCount = 0
            }
            2 -> {
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 1, true))
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 0, false))
                pads.add(PadSpec(PadKind.BUILD_STATION, 1, 40))
                target = 100; interval = 3.4f; tableCount = 0; order = 1.3f; startCash = 10
            }
            3 -> {
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 1, true))
                pads.add(PadSpec(PadKind.BUILD_TABLE, 0, 15)); pads.add(PadSpec(PadKind.BUILD_TABLE, 1, 25))
                pads.add(PadSpec(PadKind.BUILD_STATION, 1, 45))
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 0, false))
                target = 150; interval = 3.6f; dineIn = 0.75f; tableCount = 2; startCash = 15; order = 1.3f
            }
            4 -> {
                layout = 2; menu = listOf(Item.BURGER, Item.FRIES)
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 1, true))
                st.add(StationSpec(StationType.FRYER, Item.FRIES, 2, false))
                pads.add(PadSpec(PadKind.BUILD_STATION, 1, 30)); pads.add(PadSpec(PadKind.BUILD_TABLE, 2, 25))
                target = 220; interval = 3.6f; tablesPre = 2; tableCount = 3; dineIn = 0.5f; order = 1.6f; startCash = 30
            }
            5 -> {
                layout = 2; menu = listOf(Item.BURGER, Item.FRIES)
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 1, true))
                st.add(StationSpec(StationType.FRYER, Item.FRIES, 2, true))
                pads.add(PadSpec(PadKind.UPGRADE_STATION, 0, 60)); pads.add(PadSpec(PadKind.BUILD_TABLE, 3, 30))
                rush = listOf(RushWave(55f, 16f, 1.8f))
                target = 300; interval = 3.3f; tablesPre = 3; tableCount = 4; dineIn = 0.5f; order = 1.7f
            }
            6 -> {
                layout = 2; menu = listOf(Item.BURGER, Item.FRIES, Item.SODA)
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 1, true))
                st.add(StationSpec(StationType.FRYER, Item.FRIES, 2, true))
                st.add(StationSpec(StationType.SODA, Item.SODA, 3, false))
                pads.add(PadSpec(PadKind.BUILD_STATION, 2, 35)); pads.add(PadSpec(PadKind.UPGRADE_STATION, 0, 70))
                rush = listOf(RushWave(70f, 16f, 1.8f))
                target = 360; interval = 3.2f; tablesPre = 3; tableCount = 4; dineIn = 0.5f; order = 1.9f; startCash = 35
            }
            7 -> {
                layout = 2; menu = listOf(Item.BURGER, Item.FRIES, Item.SODA)
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 1, true))
                st.add(StationSpec(StationType.FRYER, Item.FRIES, 2, true))
                st.add(StationSpec(StationType.SODA, Item.SODA, 3, true))
                pads.add(PadSpec(PadKind.HIRE, StaffRole.CASHIER.ordinal * 10, 50)); pads.add(PadSpec(PadKind.UPGRADE_STATION, 0, 80))
                target = 450; interval = 3.0f; tablesPre = 4; tableCount = 4; dineIn = 0.5f; order = 2.0f; startCash = 20
                rush = listOf(RushWave(80f, 15f, 1.8f))
            }
            8 -> {
                layout = 2; menu = listOf(Item.BURGER, Item.FRIES, Item.SODA)
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 1, true))
                st.add(StationSpec(StationType.FRYER, Item.FRIES, 2, true))
                st.add(StationSpec(StationType.SODA, Item.SODA, 3, true))
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 0, false))
                pads.add(PadSpec(PadKind.HIRE, StaffRole.CASHIER.ordinal * 10, 50)); pads.add(PadSpec(PadKind.BUILD_STATION, 3, 70))
                vip = 0.22f; target = 520; interval = 3.0f; tablesPre = 4; tableCount = 4; dineIn = 0.5f; order = 2.0f; startCash = 20
                rush = listOf(RushWave(70f, 15f, 1.8f))
            }
            9 -> {
                layout = 3; menu = listOf(Item.BURGER, Item.FRIES, Item.SODA); drive = true
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 1, true))
                st.add(StationSpec(StationType.FRYER, Item.FRIES, 2, true))
                st.add(StationSpec(StationType.SODA, Item.SODA, 3, true))
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 0, true))
                pads.add(PadSpec(PadKind.BUILD_DRIVE, 0, 40)); pads.add(PadSpec(PadKind.HIRE, StaffRole.CASHIER.ordinal * 10, 60))
                pads.add(PadSpec(PadKind.UPGRADE_STATION, 0, 90))
                vip = 0.08f; target = 600; interval = 3.2f; tablesPre = 4; tableCount = 5; dineIn = 0.45f; order = 2.0f; startCash = 25
            }
            else -> {
                layout = 3; menu = listOf(Item.BURGER, Item.FRIES, Item.SODA); drive = true
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 1, true))
                st.add(StationSpec(StationType.FRYER, Item.FRIES, 2, true))
                st.add(StationSpec(StationType.SODA, Item.SODA, 3, true))
                st.add(StationSpec(StationType.GRILL, Item.BURGER, 0, true))
                pads.add(PadSpec(PadKind.HIRE, StaffRole.CASHIER.ordinal * 10, 50)); pads.add(PadSpec(PadKind.UPGRADE_STATION, 0, 80))
                pads.add(PadSpec(PadKind.BUILD_DRIVE, 0, 60))
                critics = 2; vip = 0.06f; target = 700; interval = 3.0f; tablesPre = 4; tableCount = 5; dineIn = 0.45f; order = 2.1f; startCash = 25
                rush = listOf(RushWave(90f, 16f, 1.8f))
            }
        }
        val weights = FloatArray(menu.size) { i -> if (menu[i] == Item.BURGER) 1f else 0.8f }
        return LevelConfig(
            id = id, chapter = 1, slot = id, difficulty = Difficulty.of(id), boss = if (id == 10) BossType.CRITIC else null,
            mutators = emptyList(), layoutId = layout, mirror = false, shiftSeconds = 150f, overtime = true,
            menu = menu, menuWeights = weights, stations = st, tablesPrebuilt = tablesPre, tableCount = tableCount,
            drive = drive, drivePrebuilt = false, pads = pads, startCash = startCash,
            spawnInterval = interval, rush = rush, maxQueue = 10, patience = 120f, orderMean = order, orderMax = 3,
            vipChance = vip, carInterval = 11f, dineIn = dineIn, troubleRate = 0f, troubles = emptyList(),
            powerUps = id >= 6, critics = critics, groupArrivals = false,
            target = target, star2 = (target * 1.25f).toInt() / 5 * 5, star3 = (target * 1.5f).toInt() / 5 * 5,
            maxWalkouts3 = 2, tutorial = id, seed = Difficulty.hash(id * 7349L + 11), title = TITLES[id - 1],
        )
    }
}
