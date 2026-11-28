package com.ngstudio.burgerready.game

import org.junit.Test
import java.util.concurrent.Executors

/** Manual: prints what the expert bot achieves in a fully built restaurant (run with CALIBRATE=true). */
class GoldCalibrationTest {
    @Test
    fun printFullyBuiltStats() {
        if (System.getenv("CALIBRATE") != "true") return
        val pool = Executors.newFixedThreadPool(8)
        val jobs = (0 until Restaurants.CLASSIC).map { r ->
            pool.submit<String> {
                val cfg = Restaurants.config(r)
                val stats = StatsCalc.stats(Restaurants.expectedUpgrades(r + 1), cfg.storeId)
                val all = RestaurantProgress(cfg.pads.map { it.id }.toSet())
                val sb = StringBuilder()
                Simulator.play(cfg, stats, all, 600f) { w ->
                    val t = w.time.toInt()
                    if (t == 180 || t == 360 || t == 599) sb.append(String.format("R%-2d t=%3ds served=%4d rev=%,10d revPerMul=%7.0f combo=%.2f flawless=%d rushes=%d vips=%3d cars=%3d troubles=%3d walkouts=%d\n",
                        r, t, w.served, w.revenue, w.revenue / cfg.priceMul, w.bestCombo, w.flawlessRushes, w.rushesDone, w.vipsServed, w.carsServed, w.troublesFixedCount, w.walkouts))
                    false
                }
                sb.toString()
            }
        }
        jobs.forEach { print(it.get()) }
        pool.shutdown()
    }
}
