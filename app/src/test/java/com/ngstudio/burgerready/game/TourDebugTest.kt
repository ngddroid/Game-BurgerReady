package com.ngstudio.burgerready.game

import org.junit.Test

/** Manual (DEBUG_TOUR=<store id>): traces the expert bot through one tour restaurant every 30 simulated seconds. */
class TourDebugTest {
    @Test
    fun trace() {
        val id = System.getenv("DEBUG_TOUR") ?: return
        val cfg = Restaurants.config(id)
        val stats = StatsCalc.stats(Restaurants.expectedUpgrades(cfg.index), cfg.storeId)
        println("layout ${cfg.layoutId} menu ${cfg.menu} stations ${cfg.stations.map { it.item }} drive ${cfg.drive}")
        Simulator.play(cfg, stats, null, 900f) { w ->
            if (w.time.toInt() % 30 == 0) {
                val vis = w.visiblePads().joinToString { "${it.spec.id}:${it.spec.kind}:${it.remaining}" }
                println(String.format("t=%4.0f cash=%d built=%d served=%d walk=%d p=(%.1f,%.1f) q=%d cars=%d stack=%d staff=%d troubles=%s pads=[%s]",
                    w.time, w.cash, w.builtCount, w.served, w.walkouts, w.player.x, w.player.z, w.queue.size, w.carQueue.size, w.player.stack.size, w.staff.size,
                    w.troubles.filter { !it.done }.map { it.kind }, vis))
                val cs = w.layout.cashierSpot
                val toCash = w.nav.path(w.player.x, w.player.z, cs.x - 0.05f, cs.z)
                println("   input=(${w.inputX},${w.inputZ}) v=(${w.player.vx},${w.player.vz}) stack=${w.player.stack.groupingBy { it }.eachCount()} trash=(${w.layout.trashApproach.x},${w.layout.trashApproach.z}) tables=${w.tables.filter { it.built }.map { "(${it.x},${it.z},${it.trays})" }}")
                val px = w.player.x; val pz = w.player.z; val r = 0.24f; val k = r * 0.7071f
                val pts = listOf(0f to 0f, r to 0f, -r to 0f, 0f to r, 0f to -r, k to k, -k to k, k to -k, -k to -k)
                println("   circle blocked: " + pts.filter { w.nav.isBlocked(px + it.first, pz + it.second) }.map { "(${px + it.first},${pz + it.second})" })
                for ((dx, dz) in listOf(-0.1f to 0f, 0f to 0.03f, 0f to -0.03f, 0.05f to 0f)) {
                    val bad = pts.filter { w.nav.isBlocked(px + dx + it.first, pz + dz + it.second) }.map { "(${px + dx + it.first},${pz + dz + it.second})" }
                    println("   move ($dx,$dz) blocked at: $bad")
                }
                println("   blocked=${w.nav.isBlocked(w.player.x, w.player.z)} pathToCashier=${toCash?.toList()} cashier=(${cs.x},${cs.z}) sleepy=${w.troubles.filter { it.kind == TroubleKind.SLEEPY && !it.done }.map { "(${it.customer?.x},${it.customer?.z})" }}")
            }
            w.expanded
        }
    }
}
