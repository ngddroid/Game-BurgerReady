package com.ngstudio.burgerready.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.math.hypot

/**
 * Validates the continuous restaurant progression (replaces the old 10,000-level validator):
 *  - every restaurant's build plan is completable by the expert bot, within the pacing window;
 *  - every pad is reachable and pads that can be visible together never overlap;
 *  - saved progress restores exactly (bought things never "lock again");
 *  - the simulation is deterministic.
 *
 * Re-run after changing any gameplay number: World, Bot, Layouts, Restaurants, Types prices/cook times, Economy stats.
 */
class RestaurantFlowTest {

    /** Pacing window per restaurant, in simulated minutes for the expert bot. */
    private fun window(index: Int): ClosedFloatingPointRange<Float> = if (index == 0) 8f..18f else 10f..45f

    @Test
    fun everyRestaurantIsCompletableWithinPacing() {
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))
        val jobs = (0 until Restaurants.CLASSIC).map { r ->
            pool.submit<String?> {
                val cfg = Restaurants.config(r)
                val stats = StatsCalc.stats(Restaurants.expectedUpgrades(r), cfg.storeId)
                val w = Simulator.run(cfg, stats, 1f, maxSeconds = 90f * 60f, startCash = if (r == 0) 30L else 0L)
                val minutes = w.time / 60f
                val done = w.pads.count { it.done }
                val line = String.format("R%-2d %-20s layout=%d pads=%2d done=%2d time=%5.1f min served=%4d walkouts=%3d revenue=%,d",
                    r, cfg.name, cfg.layoutId, cfg.pads.size, done, minutes, w.served, w.walkouts, w.revenue)
                println(line)
                when {
                    done < cfg.pads.size -> "R$r not completed: $line"
                    minutes !in window(r) -> "R$r outside pacing ${window(r)}: $line"
                    else -> null
                }
            }
        }
        val failures = jobs.mapNotNull { it.get() }
        pool.shutdown()
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun firstRestaurantWorksForANovice() {
        val cfg = Restaurants.config(0)
        val w = Simulator.run(cfg, PlayerStats(), skill = 0.55f, maxSeconds = 40f * 60f, startCash = 30L)
        println(String.format("novice R0: %.1f min, %d/%d pads", w.time / 60f, w.pads.count { it.done }, cfg.pads.size))
        assertTrue("a novice must finish the first restaurant in 40 min", w.expanded)
    }

    @Test
    fun padsAreReachableAndDoNotOverlap() {
        for (r in 0 until Restaurants.CLASSIC) {
            val cfg = Restaurants.config(r)
            val w = World(cfg, PlayerStats())
            // Build everything so reachability is checked against the final, most cluttered floor.
            w.debugAction(1)
            for (p in w.pads) {
                assertNotNull("R$r pad ${p.spec.id} ${p.spec.kind} unreachable at (${p.x}, ${p.z})", w.nav.path(w.layout.doorX, w.layout.d + 1f, p.x, p.z))
            }
            // Pads shown together are consecutive in plan order: they must not overlap.
            for (i in 0 until w.pads.size - 1) {
                val a = w.pads[i]; val b = w.pads[i + 1]
                assertTrue("R$r pads ${a.spec.id}/${b.spec.id} overlap", hypot(a.x - b.x, a.z - b.z) >= 1.3f)
            }
            // Ids are the plan positions; EXPAND (if any) is last.
            cfg.pads.forEachIndexed { k, p -> assertEquals(k, p.id) }
            cfg.pads.dropLast(1).forEach { assertTrue(it.kind != PadKind.EXPAND) }
            assertEquals(r < Restaurants.CLASSIC - 1, cfg.expandPad != null)
        }
    }

    @Test
    fun savedProgressRestoresExactly() {
        val cfg = Restaurants.config(2)
        val w = Simulator.run(cfg, PlayerStats(), maxSeconds = 12f * 60f)
        val saved = w.progress()
        assertTrue("bot should have bought something", saved.done.isNotEmpty())
        val restored = World(cfg, PlayerStats(), saved = saved)
        assertEquals(saved, restored.progress())
        for (i in w.stations.indices) {
            assertEquals(w.stations[i].built, restored.stations[i].built)
            assertEquals(w.stations[i].level, restored.stations[i].level)
        }
        for (i in w.tables.indices) assertEquals(w.tables[i].built, restored.tables[i].built)
        assertEquals(w.driveBuilt, restored.driveBuilt)
        assertEquals(w.staff.map { it.role to it.post }, restored.staff.map { it.role to it.post })
        // the visible pads continue exactly where the player left off
        assertEquals(w.visiblePads().map { it.spec.id }, restored.visiblePads().map { it.spec.id })
    }

    @Test
    fun simulationIsDeterministic() {
        val cfg = Restaurants.config(1)
        val a = Simulator.run(cfg, PlayerStats(), maxSeconds = 300f)
        val b = Simulator.run(cfg, PlayerStats(), maxSeconds = 300f)
        assertEquals(a.revenue, b.revenue)
        assertEquals(a.progress(), b.progress())
        assertEquals(a.served, b.served)
    }
}
