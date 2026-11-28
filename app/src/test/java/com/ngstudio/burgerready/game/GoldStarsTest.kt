package com.ngstudio.burgerready.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

/** The late-game gold stars (game/GoldStars.kt). */
class GoldStarsTest {

    @Test
    fun threeGoalsPerRestaurant() {
        for (r in 0 until Restaurants.CLASSIC) {
            val g = GoldStars.goals(r)
            assertEquals(GoldStars.PER_RESTAURANT, g.size)
            assertEquals(listOf(0, 1, 2), g.map { it.star })
            assertTrue(g.all { it.target > 0 && it.text().isNotEmpty() })
            if (Restaurants.config(r).drive.not()) assertTrue("R$r has no drive-thru", g.none { it.kind == GoalKind.CARS })
        }
        assertEquals(36, GoldStars.TOTAL)
    }

    @Test
    fun metIsABitmask() {
        val none = VisitStats(0, 0, 1f, 0, 0, 0, 0)
        val lots = VisitStats(100_000, Long.MAX_VALUE / 2, 3f, 99, 9_999, 9_999, 9_999)
        for (r in 0 until Restaurants.CLASSIC) {
            assertEquals(0, GoldStars.met(r, none))
            assertEquals(7, GoldStars.met(r, lots))
        }
        assertEquals(2, GoldStars.count(0b101))
    }

    /**
     * New mechanic, so the bot must cope: in every fully built restaurant the expert bot earns all three stars
     * within 8 simulated minutes (humans take ~1.5–2× as long), using the upgrades a player has by then.
     */
    @Test
    fun goldStarsAreEarnable() {
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))
        val jobs = (0 until Restaurants.CLASSIC).map { r ->
            pool.submit<String?> {
                val cfg = Restaurants.config(r)
                val stats = StatsCalc.stats(Restaurants.expectedUpgrades(r + 1), cfg.storeId)
                val all = RestaurantProgress(cfg.pads.map { it.id }.toSet())
                var at = -1f
                val w = Simulator.play(cfg, stats, all, 8f * 60f) { w ->
                    if (GoldStars.met(r, VisitStats.of(w)) == 7) { at = w.time; true } else false
                }
                println(String.format("gold R%-2d %-20s all 3 stars at %s", r, cfg.name, if (at > 0) String.format("%.1f min", at / 60f) else "NEVER"))
                if (at < 0) "R$r: only ${GoldStars.met(r, VisitStats.of(w))} in 8 min (served ${w.served}, revenue ${w.revenue})" else null
            }
        }
        val fails = jobs.mapNotNull { it.get() }
        pool.shutdown()
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }
}
