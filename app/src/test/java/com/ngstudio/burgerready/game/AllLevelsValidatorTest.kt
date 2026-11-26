package com.ngstudio.burgerready.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Validates every level (docs/MASTER_GAME_PLAN.md §5.7):
 *  - all 10,000 levels have tuned targets and the expert bot beats 1★ with ≥10% margin,
 *  - star thresholds are ordered and 3★ is reachable by the bot,
 *  - tutorial levels 1–10 are won even by a novice bot,
 *  - the difficulty curve has the planned shape.
 */
class AllLevelsValidatorTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadTargets() {
            val f = File(LevelTunerTest.findProjectDir(), "app/src/main/assets/levels/targets.bin")
            if (f.exists()) {
                val bb = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
                val arr = IntArray(bb.remaining() / 4) { bb.int }
                LevelGenerator.installTargets(arr)
            }
        }
    }

    @Test
    fun difficultyCurveShape() {
        // chapter averages (excluding bosses) never decrease by more than the breather dip
        var prev = 0f
        for (ch in 2..1000) {
            val ids = ((ch - 1) * 10 + 1..(ch - 1) * 10 + 9)
            val avg = ids.map { Difficulty.of(it) }.average().toFloat()
            assertTrue("chapter $ch average dropped too much ($prev → $avg)", avg >= prev - 0.03f)
            prev = max(prev, avg)
        }
        for (id in 11..10_000 step 10) {
            val boss = id + 9
            if (boss <= 10_000) assertTrue("boss slot $boss", LevelGenerator.bossFor(boss) != null)
        }
        assertTrue(Difficulty.of(11) < 0.2f)
        assertTrue(Difficulty.of(10_000) > 0.95f)
    }

    @Test
    fun tutorialLevelsAreWonByNovice() {
        for (id in 1..10) {
            val cfg = LevelGenerator.generate(id)
            val w = Simulator.run(cfg, EconomyModel.expectedStats(id), skill = 0.5f)
            assertTrue("tutorial $id must be won by a novice (rev ${w.revenue} / ${cfg.target})", w.won)
        }
    }

    @Test
    fun allLevelsWinnable() {
        val pool = Executors.newFixedThreadPool(max(2, Runtime.getRuntime().availableProcessors() - 1))
        val failures = java.util.concurrent.ConcurrentLinkedQueue<String>()
        var missing = 0
        val tasks = (1..LevelGenerator.MAX_LEVEL).map { id ->
            pool.submit {
                if (id > 10 && !LevelGenerator.hasTuned(id)) { synchronized(this) { missing++ }; return@submit }
                val cfg = LevelGenerator.generate(id)
                assertTrue("L$id star order", cfg.target < cfg.star2 && cfg.star2 < cfg.star3)
                val w = Simulator.run(cfg, EconomyModel.expectedStats(id), 1f)
                if (w.revenue < cfg.target * 1.10f) failures.add("L$id bot ${w.revenue} < target ${cfg.target} × 1.10")
                if (id > 10 && w.revenue < cfg.star3) failures.add("L$id 3★ ${cfg.star3} unreachable (bot ${w.revenue})")
                if (cfg.layoutId !in Layouts.unlockedFor(id)) failures.add("L$id uses a locked layout")
                for (s in cfg.stations) if (s.item.unlockLevel > id) failures.add("L$id uses locked item ${s.item}")
            }
        }
        tasks.forEach { it.get() }
        pool.shutdown()
        assertEquals("levels without tuned targets", 0, missing)
        assertTrue("failures: ${failures.take(20)} (total ${failures.size})", failures.isEmpty())
    }
}
