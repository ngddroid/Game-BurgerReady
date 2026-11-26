package com.ngstudio.burgerready.game

import org.junit.Assert.assertTrue
import org.junit.Test

class SimSmokeTest {
    @Test
    fun tutorialAndEarlyLevelsRun() {
        val t0 = System.nanoTime()
        val ids = (1..30).toList() + listOf(50, 100, 250, 500, 1000, 2500, 5000, 10000)
        for (id in ids) {
            val cfg = LevelGenerator.generate(id)
            val stats = EconomyModel.expectedStats(id)
            val s0 = System.nanoTime()
            val w = Simulator.run(cfg, stats, 1f)
            val ms = (System.nanoTime() - s0) / 1e6
            println(String.format("L%-5d D=%.2f layout=%d menu=%-28s st=%d pads=%d rev=%6d target=%6d served=%3d walk=%2d won=%b stars=%d t=%.0fs  %.0fms",
                id, cfg.difficulty, cfg.layoutId, cfg.menu.joinToString(",") { it.id }, cfg.stations.size, cfg.pads.size,
                w.revenue, cfg.target, w.served, w.walkouts, w.won, w.stars, w.time, ms))
            if (id <= 10) assertTrue("tutorial level $id must be won", w.won)
        }
        println("total ${(System.nanoTime() - t0) / 1e6} ms")
    }
}
