package com.ngstudio.burgerready.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * The World Tour: 25 regions × 20 restaurants appended after the 12 classic ones (docs/WORLD_TOUR_DESIGN.md).
 *
 * Every tour restaurant is generated from its region ([TourRegions]) and its step in the region's 20-step pattern
 * (size class, layout, palette, time of day, props, perk), so the same id always gives the same restaurant.
 * Global indices: classic 0..11, tour 12..511. Ids: "<region id>_<step 01..20>" (saved: append regions only).
 */
class Palette(val wall: Int, val trim: Int, val accent: Int)

class RegionDef(
    val id: String, val name: String, val blurb: String,
    val ground: Int, val sky: Int, val floor: String, val floorTint: Int,
    val palettes: List<Palette>,
    /** Night amount of the region's usual time, and of the "other" time (one restaurant in five). */
    val night: Float, val altNight: Float,
    val weather: Weather, val tree: String, val props: List<String>,
    val host: Speaker, val perk: PerkKind, val menuLead: List<Item>, val names: List<String>,
)

/**
 * Size classes of the 20-step pattern. [lastPadSeconds] sets how long the build takes: the last pad costs that many
 * seconds of the restaurant's estimated full-build earnings (so pacing doesn't depend on the menu or the region).
 */
enum class SizeClass(
    val label: String, val layouts: IntArray, val lastPadSeconds: Double,
    val patience: Float, val orderMean: Float, val orderMax: Int, val vip: Float, val trouble: Float, val maxQueue: Int, val groups: Boolean,
    /** Shortest gap between arrivals (s). */
    val arrivalFloor: Float,
    /** Expert-bot pacing window in simulated minutes (TourFlowTest). */
    val minMinutes: Float, val maxMinutes: Float,
) {
    S("Pop-up", intArrayOf(9, 10, 1, 2), 180.0, 64f, 1.6f, 3, 0.05f, 0.6f, 8, false, 1.5f, 5f, 15f),
    M("Diner", intArrayOf(3, 4, 12, 13), 175.0, 58f, 1.9f, 4, 0.07f, 0.8f, 10, true, 1.25f, 7f, 20f),
    L("Hall", intArrayOf(5, 6, 11, 14), 155.0, 54f, 2.2f, 5, 0.08f, 1.0f, 12, true, 1.1f, 8f, 26f),
    XL("Grand", intArrayOf(7, 15), 155.0, 50f, 2.4f, 6, 0.09f, 1.2f, 12, true, 1.0f, 12f, 30f),
    F("Flagship", intArrayOf(16, 8), 160.0, 50f, 2.5f, 6, 0.10f, 1.3f, 12, true, 1.0f, 10f, 32f),
}

object Tour {
    const val PER_REGION = 20
    /**
     * Idle income of a fully built tour restaurant, as a share of what it earns while you play it (§5.3). Small enough
     * that offline income stays a welcome-back boost, never a replacement for play (TourTest.economyBounds).
     */
    const val IDLE_SHARE = 0.0003
    private const val EXPAND_MUL = 2.4
    private const val BASE_PRICE = 3400.0
    /** The 20-step rhythm of every region, building up to its flagship. */
    private const val PATTERN = "SMSMLSMLMLSMLMXMLMLF"

    val REGIONS: List<RegionDef> get() = TourRegions.ALL
    val COUNT: Int get() = REGIONS.size * PER_REGION

    fun size(step: Int): SizeClass = when (PATTERN[step.coerceIn(0, PER_REGION - 1)]) {
        'S' -> SizeClass.S; 'M' -> SizeClass.M; 'L' -> SizeClass.L; 'X' -> SizeClass.XL; else -> SizeClass.F
    }

    fun regionOf(global: Int): Int = ((global - Restaurants.CLASSIC) / PER_REGION).coerceIn(0, REGIONS.size - 1)
    fun stepOf(global: Int): Int = ((global - Restaurants.CLASSIC) % PER_REGION).coerceIn(0, PER_REGION - 1)
    fun globalIndex(region: Int, step: Int): Int = Restaurants.CLASSIC + region * PER_REGION + step
    fun id(region: Int, step: Int): String = REGIONS[region].id + "_" + (step + 1).toString().padStart(2, '0')
    fun regionIndex(regionId: String): Int = REGIONS.indexOfFirst { it.id == regionId }

    private val idIndex: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        for (r in REGIONS.indices) for (k in 0 until PER_REGION) m[id(r, k)] = globalIndex(r, k)
        m
    }

    fun indexOf(id: String): Int? = idIndex[id]
    fun storeById(id: String): StoreDef? = indexOf(id)?.let { store(it) }
    fun storeIds(region: Int): List<String> = (0 until PER_REGION).map { id(region, it) }

    /**
     * Region r is a tier (×1.55 per region); inside it, a gentle ×1.022 per step. Region 1 starts at ×3,400, about
     * 8× Candy Kingdom: tour players have every upgrade, and the 12 finished classic restaurants' idle income must
     * stay a small help there, not pay for whole restaurants.
     */
    fun priceMul(global: Int): Float {
        val r = regionOf(global); val k = stepOf(global)
        return (BASE_PRICE * 1.55.pow(r) * 1.022.pow(k)).toFloat()
    }

    // ------------------------------------------------------------------------------------------ store definitions
    private val storeCache = HashMap<Int, StoreDef>()

    fun store(global: Int): StoreDef = synchronized(storeCache) { storeCache.getOrPut(global) { makeStore(global) } }

    private fun makeStore(global: Int): StoreDef {
        val r = regionOf(global); val k = stepOf(global)
        val reg = REGIONS[r]
        val pal = reg.palettes[(k + r) % reg.palettes.size]
        val alt = k % 5 == 3
        val night = if (alt) reg.altNight else reg.night
        val pool = reg.props
        val start = (k * 2 + r) % pool.size
        val props = listOf(pool[start], pool[(start + 1) % pool.size], pool[(start + 2) % pool.size])
        val weather = if (night >= 0.4f && reg.weather in setOf(Weather.BUTTERFLIES, Weather.PETALS, Weather.HEARTS, Weather.CLOUDS)) Weather.FIREFLIES
            else if (night < 0.4f && reg.weather == Weather.FIREFLIES) Weather.BUTTERFLIES else reg.weather
        val sky = if (night > reg.night + 0.2f) mix(reg.sky, 0x232A55, 0.7f) else if (night < reg.night - 0.2f) mix(reg.sky, 0xFFD6A8, 0.55f) else reg.sky
        val amount = perkAmount(reg.perk, k)
        return StoreDef(
            id = id(r, k), name = reg.names[k], perk = perkText(reg.perk, amount),
            floor = reg.floor, wall = pal.wall, trim = pal.trim, ground = "ground_grass", accent = pal.accent,
            props = props, sky = sky, music = 0,
            night = night, weather = weather, tree = reg.tree, floorTint = reg.floorTint, groundColor = reg.ground,
            perkKind = reg.perk, perkAmount = amount, region = reg.id, step = k,
        )
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(c: Int, s: Int) = (c shr s) and 255
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
        return (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    /** Perk strength steps up towards the region's flagship. */
    fun perkAmount(kind: PerkKind, step: Int): Float {
        val t = step / (PER_REGION - 1f)
        fun lerp(a: Float, b: Float) = a + (b - a) * t
        return when (kind) {
            PerkKind.TIPS, PerkKind.PATIENCE -> lerp(0.08f, 0.20f)
            PerkKind.COOK, PerkKind.STAFF -> lerp(0.06f, 0.15f)
            PerkKind.VIP -> lerp(0.02f, 0.05f)
            PerkKind.DRIVE -> lerp(0.10f, 0.25f)
            PerkKind.COMBO -> lerp(0.15f, 0.30f)
            PerkKind.CARRY -> if (step >= 10) 2f else 1f
            PerkKind.PET -> lerp(0.25f, 0.60f)
            PerkKind.ALL -> lerp(0.05f, 0.10f)
            PerkKind.NONE -> 0f
        }
    }

    fun perkText(kind: PerkKind, a: Float): String {
        val pct = (a * 100f + 0.5f).toInt()
        return when (kind) {
            PerkKind.TIPS -> "+$pct% tips"
            PerkKind.PATIENCE -> "+$pct% patience"
            PerkKind.COOK -> "+$pct% cook speed"
            PerkKind.VIP -> "+$pct% VIP guests"
            PerkKind.DRIVE -> "Drive-thru pays +$pct%"
            PerkKind.COMBO -> "Combo builds $pct% faster"
            PerkKind.CARRY -> "Carry +${a.toInt()}"
            PerkKind.PET -> "Pet ability +$pct%"
            PerkKind.STAFF -> "Helpers +$pct% speed"
            PerkKind.ALL -> "+$pct% tips, patience and cook speed"
            PerkKind.NONE -> "World Tour"
        }
    }

    // ------------------------------------------------------------------------------------------ restaurant configs
    /** All seven items, the region's lead first (stations are built in this order). */
    fun menuFor(reg: RegionDef): List<Item> {
        val all = listOf(Item.BURGER, Item.FRIES, Item.SODA, Item.CHEESE, Item.SHAKE, Item.DOUBLE, Item.VEGGIE)
        return reg.menuLead + all.filter { it !in reg.menuLead }
    }

    fun build(global: Int): RestaurantConfig {
        val r = regionOf(global); val k = stepOf(global)
        val reg = REGIONS[r]
        val size = size(k)
        val store = store(global)
        val layout = Layouts.get(size.layouts[(r + k) % size.layouts.size])
        val menu = menuFor(reg)
        val stations = Restaurants.stationSpecs(layout, menu)
        val weights = FloatArray(menu.size) { i ->
            val base = when (menu[i].station) { StationType.GRILL -> 1.0f; StationType.FRYER -> 0.85f; StationType.SODA -> 0.7f; else -> 0.6f }
            if (menu[i] in reg.menuLead) base * 1.3f else base
        }
        val tableOrder = layout.tables.indices.sortedBy { val t = layout.tables[it].p; abs(t.x - layout.registerX) + abs(t.z - layout.counter.z) * 0.8f }
        val tables = layout.tables.size
        val drive = layout.drive != null
        val steps = Restaurants.planSteps(stations.size, tables, tableOrder, drive)

        // costs: the same geometric curve as the classic restaurants, but the last pad costs a fixed number of seconds
        // of this restaurant's own earning power (menu, layout, crowd, drive-thru, the player's expected upgrades),
        // so a cheap-menu pop-up and a double-stack hall pace alike. Long money with Double maths (values reach ~1e12).
        val p = priceMul(global).toDouble()
        val n = steps.size
        val first = 10.0
        val earn = earningPower(global, store, size, stations, menu, weights, drive)
        val last = max(first * 4, size.lastPadSeconds * earn)
        val g = (last / first).pow(1.0 / max(1, n - 1))
        val pads = ArrayList<PadSpec>()
        steps.forEachIndexed { idx, s ->
            val raw = first * g.pow(idx) * (if (idx == 0) 1.0 else s.weight.toDouble())
            pads.add(PadSpec(idx, s.kind, s.ref, if (idx == 0) 0L else Restaurants.roundCostD(raw * p)))
        }
        if (global < Restaurants.TOTAL - 1) pads.add(PadSpec(n, PadKind.EXPAND, global + 1, Restaurants.roundCostD(last * EXPAND_MUL * p)))

        return RestaurantConfig(
            index = global, storeId = store.id, name = store.name, layoutId = layout.id,
            menu = menu, menuWeights = weights, stations = stations,
            tableCount = tables, drive = drive, pads = pads, priceMul = p.toFloat(),
            patience = size.patience, orderMean = size.orderMean, orderMax = size.orderMax,
            vipChance = size.vip, dineIn = 0.55f, troubleRate = size.trouble,
            troubles = TroubleKind.entries.toList(), critics = true, groupArrivals = size.groups,
            maxQueue = size.maxQueue, seed = Hash.mix(global * 7349L + 11), arrivalFloor = size.arrivalFloor, earnPerSec = earn,
        )
    }

    /**
     * Estimated earnings per second (base units, before the money scale) of the fully built restaurant: guests arrive
     * as fast as the stations cook (level-3 machines, the player's expected Cook Speed) but no faster than the size
     * class allows; each spends the weighted average menu price, plus price and tip upgrades; cars add their share.
     */
    fun earningPower(global: Int, store: StoreDef, size: SizeClass, stations: List<StationSpec>, menu: List<Item>, weights: FloatArray, drive: Boolean): Double {
        val stats = StatsCalc.stats(Restaurants.expectedUpgrades(global), store.id)
        val items = stations.map { it.item }.toSet()
        var wSum = 0.0; var vSum = 0.0
        for (i in menu.indices) if (menu[i] in items) { wSum += weights[i]; vSum += weights[i] * menu[i].price }
        val avgPrice = if (wSum > 0) vSum / wSum else 5.0
        var rate = 0.0
        for (s in stations) rate += 1.8 * stats.cookSpeedMul / s.item.cookTime
        val interval = max(size.arrivalFloor.toDouble(), size.orderMean / (rate * 0.72))
        val spend = avgPrice * size.orderMean * (1.0 + stats.priceBonus) * (1.0 + stats.tipBonus + 0.5)
        val cars = if (drive) avgPrice * size.orderMean * 1.3 * 1.4 * stats.driveThruPayMul * (1.0 + stats.priceBonus) * (1.0 + stats.tipBonus + 0.5) / 6.5 else 0.0
        return spend / interval + cars
    }

    /** True when [global] is a region's flagship (its NEXT REGION pad opens the next region). */
    fun isFlagship(global: Int): Boolean = global >= Restaurants.CLASSIC && stepOf(global) == PER_REGION - 1
}
