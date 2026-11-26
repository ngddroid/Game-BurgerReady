package com.ngstudio.burgerready.game

import org.junit.Assume
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.max
import kotlin.math.min

/**
 * Offline level tuner. For every level 11..10,000 it simulates a full shift with the expert bot at the
 * expected upgrade strength for that point in the game, then sets:
 *   target = botRevenue × efficiency(D)     (0.40 early → 0.80 at the end)
 *   star2  = target × 1.2  (capped at 92% of the bot)
 *   star3  = target × 1.45 (capped at 98% of the bot)
 * Tutorial levels 1..10 keep their handcrafted targets.
 *
 * Run explicitly:  ./gradlew :app:testDebugUnitTest --tests "*LevelTunerTest*" -Dtune=true
 * Output: app/src/main/assets/levels/targets.bin (int32 LE quadruples: id, target, star2, star3)
 */
class LevelTunerTest {
    data class Row(val id: Int, val target: Int, val star2: Int, val star3: Int, val bot: Int, val d: Float)

    @Test
    fun tuneAllLevels() {
        Assume.assumeTrue("set -Dtune=true to run the tuner", System.getProperty("tune") == "true" || System.getenv("TUNE") == "true")
        val pool = Executors.newFixedThreadPool(max(2, Runtime.getRuntime().availableProcessors() - 1))
        val t0 = System.nanoTime()
        EconomyModel.expectedStats(1) // warm the table on this thread
        val futures = ArrayList<Future<Row>>()
        for (id in 1..LevelGenerator.MAX_LEVEL) {
            futures.add(pool.submit<Row> { tune(id) })
        }
        val rows = futures.map { it.get() }
        pool.shutdown()
        val bb = ByteBuffer.allocate(rows.size * 16).order(ByteOrder.LITTLE_ENDIAN)
        for (r in rows) { bb.putInt(r.id); bb.putInt(r.target); bb.putInt(r.star2); bb.putInt(r.star3) }
        val out = File(findProjectDir(), "app/src/main/assets/levels/targets.bin")
        out.parentFile.mkdirs()
        out.writeBytes(bb.array())
        val csv = File(findProjectDir(), "docs/level_targets_sample.csv")
        csv.writeText("id,difficulty,bot_revenue,target,star2,star3\n" + rows.filter { it.id <= 60 || it.id % 250 == 0 }.joinToString("\n") { "${it.id},${"%.3f".format(it.d)},${it.bot},${it.target},${it.star2},${it.star3}" })
        println("Tuned ${rows.size} levels in ${(System.nanoTime() - t0) / 1e9}s → ${out.absolutePath}")
    }

    companion object {
        fun efficiency(d: Float) = 0.40f + (0.80f - 0.40f) * d

        fun tune(id: Int): Row {
            val cfg = LevelGenerator.generate(id)
            val stats = EconomyModel.expectedStats(id)
            val w = Simulator.run(cfg, stats, 1f)
            val bot = w.revenue
            if (id <= 10) return Row(id, cfg.target, cfg.star2, cfg.star3, bot, cfg.difficulty)
            val target = LevelGenerator.roundTarget(bot * efficiency(cfg.difficulty))
            val s2 = LevelGenerator.roundTarget(min(target * 1.2f, bot * 0.92f)).coerceAtLeast(target + 5)
            val s3 = LevelGenerator.roundTarget(min(target * 1.45f, bot * 0.98f)).coerceAtLeast(s2 + 5)
            return Row(id, target, s2, s3, bot, cfg.difficulty)
        }

        fun findProjectDir(): File {
            var f: File? = File("").absoluteFile
            while (f != null && !File(f, "settings.gradle.kts").exists()) f = f.parentFile
            return f ?: File("").absoluteFile
        }
    }
}
