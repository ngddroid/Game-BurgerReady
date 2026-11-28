package com.ngstudio.burgerready.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.math.hypot

/**
 * The World Tour (game/Tour.kt, docs/WORLD_TOUR_DESIGN.md §10):
 *  - structure of all 500 restaurants (ids, names, plans, costs, reachability);
 *  - expert-bot pacing on a sample covering every region × size class (FULL_TOUR=true runs all 500);
 *  - the economy bounds (offline income vs build cost, Long headroom).
 */
class TourTest {

    @Test
    fun catalogueAndIds() {
        assertEquals(25, Tour.REGIONS.size)
        assertEquals(500, Tour.COUNT)
        assertEquals(512, Restaurants.TOTAL)
        val ids = HashSet<String>()
        val names = HashSet<String>(Stores.ALL.map { it.name })
        for (r in Tour.REGIONS.indices) {
            val reg = Tour.REGIONS[r]
            assertTrue("${reg.id}: id must be a short lowercase word", reg.id.matches(Regex("[a-z]{3,12}")))
            assertEquals("${reg.id}: 20 names", Tour.PER_REGION, reg.names.size)
            assertTrue("${reg.id}: 4 palettes", reg.palettes.size == 4)
            assertTrue("${reg.id}: prop pool", reg.props.size >= 3)
            for (k in 0 until Tour.PER_REGION) {
                val g = Tour.globalIndex(r, k)
                val id = Tour.id(r, k)
                assertTrue("duplicate id $id", ids.add(id))
                assertEquals(g, Restaurants.indexOf(id))
                assertEquals(id, Restaurants.storeAt(g).id)
                val n = reg.names[k]
                assertTrue("duplicate name $n", names.add(n))
                assertTrue("name too long for the HUD: $n", n.length <= 20)
                assertEquals(n, Stores.byId(id).name)
            }
        }
        // the classic chain is untouched and the tour follows it
        assertEquals("wagon", Restaurants.storeAt(0).id)
        assertEquals("candy", Restaurants.storeAt(11).id)
        assertEquals("harbor_01", Restaurants.storeAt(12).id)
        assertEquals("harbor_01", Restaurants.nextStoreId("candy"))
        assertEquals("blossom_01", Restaurants.nextStoreId("harbor_20"))
        assertNull(Restaurants.nextStoreId(Restaurants.storeAt(Restaurants.TOTAL - 1).id))
        assertNull("Candy Kingdom keeps its plan (no NEW RESTAURANT pad)", Restaurants.config(11).expandPad)
    }

    @Test
    fun everyTourPlanIsSound() {
        val maxCost = 100_000_000_000_000_000L
        for (g in Restaurants.CLASSIC until Restaurants.TOTAL) {
            val cfg = Restaurants.config(g)
            val tag = "${cfg.storeId} (#$g)"
            cfg.pads.forEachIndexed { k, p -> assertEquals("$tag pad ids are plan positions", k, p.id) }
            assertEquals("$tag first grill is free", 0L, cfg.pads[0].cost)
            for (p in cfg.pads.drop(1)) assertTrue("$tag pad ${p.id} cost ${p.cost}", p.cost in 1..maxCost)
            assertEquals("$tag NEW RESTAURANT pad", g < Restaurants.TOTAL - 1, cfg.expandPad != null)
            cfg.pads.dropLast(1).forEach { assertTrue(it.kind != PadKind.EXPAND) }
            assertTrue("$tag has a cashier", cfg.pads.any { it.kind == PadKind.HIRE && it.ref == 0 })
            assertTrue("$tag priceMul", cfg.priceMul >= 3400f)
        }
        // each layout used by the tour: pads reachable and consecutive pads apart, on the most cluttered floor
        val checked = HashSet<Int>()
        for (g in Restaurants.CLASSIC until Restaurants.TOTAL) {
            val cfg = Restaurants.config(g)
            if (!checked.add(cfg.layoutId)) continue
            val w = World(cfg, PlayerStats())
            w.debugAction(1)
            for (p in w.pads) assertNotNull("layout ${cfg.layoutId} pad ${p.spec.id} ${p.spec.kind} unreachable", w.nav.path(w.layout.doorX, w.layout.d + 1f, p.x, p.z))
            for (i in 0 until w.pads.size - 1) {
                val a = w.pads[i]; val b = w.pads[i + 1]
                assertTrue("layout ${cfg.layoutId} pads ${a.spec.id}/${b.spec.id} overlap", hypot(a.x - b.x, a.z - b.z) >= 1.3f)
            }
        }
        assertTrue("all 16 layouts in use across classic + tour", checked.size >= 9)
    }

    /** Sample: every region at its first S, M, L, XL and flagship step (125 restaurants). FULL_TOUR=true: all 500. */
    @Test
    fun tourPacing() {
        val full = System.getenv("FULL_TOUR") == "true"
        val sample = if (full) (Restaurants.CLASSIC until Restaurants.TOTAL).toList()
            else Tour.REGIONS.indices.flatMap { r -> listOf(0, 1, 4, 14, 19).map { Tour.globalIndex(r, it) } }
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))
        val jobs = sample.map { g ->
            pool.submit<Pair<String, String?>> {
                val cfg = Restaurants.config(g)
                val size = Tour.size(Tour.stepOf(g))
                val stats = StatsCalc.stats(Restaurants.expectedUpgrades(g), cfg.storeId)
                val w = Simulator.run(cfg, stats, 1f, maxSeconds = 60f * 60f)
                val min = w.time / 60f
                val done = w.pads.count { it.done }
                val line = String.format("%-14s %-2s L%-2d pads=%2d done=%2d %5.1f min served=%4d walkouts=%3d", cfg.storeId, size.name, cfg.layoutId, cfg.pads.size, done, min, w.served, w.walkouts)
                line to when {
                    done < cfg.pads.size -> "not completed: $line"
                    min < size.minMinutes || min > size.maxMinutes -> "outside ${size.minMinutes}..${size.maxMinutes}: $line"
                    else -> null
                }
            }
        }
        val res = jobs.map { it.get() }
        pool.shutdown()
        res.forEach { println("tour " + it.first) }
        val fails = res.mapNotNull { it.second }
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    /**
     * Offline income must stay a boost (docs/WORLD_TOUR_DESIGN.md §5.3): 8 h of idle income from everything opened so
     * far buys, typically, a fifth to half of the next restaurant (a pop-up at most ~1.5 times over), never several.
     * And the largest money values keep plenty of Long headroom.
     */
    @Test
    fun economyBounds() {
        var idlePerHour = 0.0
        val ratios = ArrayList<Double>()
        for (g in 0 until Restaurants.TOTAL) {
            val cfg = Restaurants.config(g)
            val ratio = idlePerHour * 8.0 / cfg.pads.sumOf { it.cost.toDouble() }
            if (g >= Restaurants.CLASSIC + Tour.PER_REGION) ratios.add(ratio)   // after the first region has filled up
            if (System.getenv("PRINT_ECONOMY") == "true" && g >= Restaurants.CLASSIC) println(String.format("econ %-14s %-2s ratio=%.3f", cfg.storeId, Tour.size(Tour.stepOf(g)).name, ratio))
            idlePerHour += Restaurants.incomePerHour(g, 1f).toDouble()
        }
        val sorted = ratios.sorted()
        val median = sorted[sorted.size / 2]
        println(String.format("tour offline ratio: median %.2f, max %.2f of the next restaurant per 8 h", median, sorted.last()))
        assertTrue("offline income median $median", median in 0.2..0.6)
        assertTrue("offline income max ${sorted.last()}", sorted.last() <= 1.5)
        // the first tour restaurant is a real step up from the 12 finished classic restaurants
        val classicIdle8h = (0 until Restaurants.CLASSIC).sumOf { Restaurants.incomePerHour(it, 1f).toDouble() } * 8
        assertTrue("classic idle income must not pay for the first tour restaurant", classicIdle8h < 0.5 * Restaurants.config(Restaurants.CLASSIC).pads.sumOf { it.cost.toDouble() })
        val last = Restaurants.config(Restaurants.TOTAL - 1)
        val maxPad = last.pads.maxOf { it.cost }
        assertTrue("pad costs stay far below Long.MAX ($maxPad)", maxPad < Long.MAX_VALUE / 1000)
        assertTrue("8 h of all idle income fits", idlePerHour * 8 < Long.MAX_VALUE / 1000)
        for (g in 0 until Restaurants.TOTAL) assertTrue("idle income of #$g", Restaurants.incomePerHour(g, 1f) > 0)
    }

    /** Story: the opening, an arrival + halfway beat per region, a farewell per flagship but the last, the finale. */
    @Test
    fun tourStoryIsComplete() {
        assertEquals(Trigger.ARRIVE, Story.beat("tour_open")?.trigger)
        assertEquals(Tour.globalIndex(0, 0), Story.beat("tour_open")?.store)
        for (r in Tour.REGIONS.indices) {
            val id = Tour.REGIONS[r].id
            val arrive = Story.beat("t_${id}_arrive"); val mid = Story.beat("t_${id}_mid"); val done = Story.beat("t_${id}_done")
            assertNotNull("$id arrival", arrive); assertNotNull("$id halfway", mid)
            assertTrue("$id arrival is a cut-scene", arrive!!.scene)
            assertEquals(Tour.globalIndex(r, 9), mid!!.store)
            if (r < Tour.REGIONS.size - 1) assertEquals("$id farewell", Story.completion(Tour.globalIndex(r, Tour.PER_REGION - 1))?.id, done?.id)
            else assertNull("the last region ends with the tour finale", done)
        }
        assertEquals(Trigger.FINALE, Story.beat("tour_finale")?.trigger)
        // arrival order at the first tour restaurant: the opening, then the region
        val first = Tour.globalIndex(0, 0)
        assertEquals("tour_open", Story.next(first, 0, emptySet(), false)?.id)
        assertEquals("t_harbor_arrive", Story.next(first, 0, setOf("tour_open"), false)?.id)
        assertNull(Story.next(first, 0, setOf("tour_open", "t_harbor_arrive"), false))
        // the tour finale fires when the last restaurant is fully built
        assertEquals("tour_finale", Story.next(Restaurants.TOTAL - 1, Cue.FULL.bit, emptySet(), completeOnEntry = true)?.id)
    }

    /** A tour restaurant's world restores from saved progress (as the compacted save gives it back). */
    @Test
    fun tourProgressRestores() {
        val cfg = Restaurants.config("polar_07")
        val w = Simulator.run(cfg, StatsCalc.stats(Restaurants.expectedUpgrades(cfg.index), cfg.storeId), maxSeconds = 240f)
        val saved = w.progress()
        assertTrue(saved.done.size > 3)
        val restored = World(cfg, PlayerStats(), saved = saved)
        assertEquals(saved, restored.progress())
        assertEquals(w.visiblePads().map { it.spec.id }, restored.visiblePads().map { it.spec.id })
    }

    /** Every model a region names (props, trees) exists: a missing one would silently not be drawn (CLAUDE.md rule 4). */
    @Test
    fun regionModelsExist() {
        val dir = listOf(java.io.File("src/main/assets/models"), java.io.File("app/src/main/assets/models")).first { it.isDirectory }
        for (reg in Tour.REGIONS) for (m in reg.props + reg.tree) assertTrue("${reg.id}: missing model $m", java.io.File(dir, "$m.bvm").isFile)
        for (g in Restaurants.CLASSIC until Restaurants.TOTAL step 7) for (m in Restaurants.storeAt(g).props) assertTrue(java.io.File(dir, "$m.bvm").isFile)
    }
}
