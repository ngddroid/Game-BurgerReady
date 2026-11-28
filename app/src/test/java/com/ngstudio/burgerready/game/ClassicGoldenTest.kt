package com.ngstudio.burgerready.game

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 * The 12 classic restaurants must never change (docs/WORLD_TOUR_DESIGN.md §1, CLAUDE.md). This test fingerprints
 * everything that defines them (build plans, costs, guests, layouts 1–8, store definitions, stats) and a short
 * simulation of each, and compares with the fingerprint recorded before the World Tour was added.
 *
 * If this fails, a classic restaurant changed. Undo that change; only re-record (PRINT_GOLDEN=true) for a deliberate,
 * owner-approved change to the classic game.
 */
class ClassicGoldenTest {

    private fun digest(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun f(x: Float) = "%.5f".format(x)

    private fun configText(): String = buildString {
        for (i in 0 until 12) {
            val c = Restaurants.config(i)
            append("R$i ${c.storeId} ${c.name} L${c.layoutId} pm=${f(c.priceMul)} pat=${f(c.patience)} om=${f(c.orderMean)} ox=${c.orderMax}")
            append(" vip=${f(c.vipChance)} dine=${f(c.dineIn)} tr=${f(c.troubleRate)} ${c.troubles} cr=${c.critics} grp=${c.groupArrivals} q=${c.maxQueue} seed=${c.seed}\n")
            append(" menu=${c.menu} w=${c.menuWeights.joinToString { f(it) }} tables=${c.tableCount} drive=${c.drive}\n")
            for (s in c.stations) append(" st ${s.type} ${s.item} ${s.slot}\n")
            for (p in c.pads) append(" pad ${p.id} ${p.kind} ${p.ref} ${p.cost}\n")
        }
    }

    private fun layoutText(): String = buildString {
        for (id in 1..8) {
            val l = Layouts.get(id)
            append("L$id ${l.name} ${l.w}x${l.d} door=${f(l.doorX)} counter=${l.counter} trash=${l.trash} drive=${l.drive}\n")
            append(" slots=${l.stationSlots}\n tables=${l.tables}\n in=${l.decorInside}\n out=${l.decorOutside}\n win=${l.windowsBack} ${l.windowsSide}\n")
        }
    }

    private fun storeText(): String = buildString {
        for (i in 0 until 12) {
            val s = Restaurants.storeAt(i)
            append("${s.id} ${s.name} ${s.perk} ${s.floor} ${s.wall} ${s.trim} ${s.ground} ${s.accent} ${s.props} ${s.sky} ${s.music}\n")
            for (pet in listOf("none", "pug", "parrot", "hamster", "kitten", "robot")) {
                append(" $pet ${StatsCalc.stats(Restaurants.expectedUpgrades(i), s.id, pet)}\n")
            }
            append(" income ${Restaurants.incomePerHour(i, 0f)} ${Restaurants.incomePerHour(i, 1f)} reward ${Restaurants.rewardUnit(i)}\n")
        }
    }

    private fun simText(): String = buildString {
        for (i in 0 until 12) {
            val c = Restaurants.config(i)
            val w = Simulator.run(c, StatsCalc.stats(Restaurants.expectedUpgrades(i), c.storeId), maxSeconds = 240f, startCash = if (i == 0) 30L else 0L)
            append("R$i served=${w.served} revenue=${w.revenue} walkouts=${w.walkouts} done=${w.progress().done.sorted()} cash=${w.cash}\n")
        }
    }

    @Test
    fun classicRestaurantsAreUnchanged() {
        val parts = listOf("configs" to configText(), "layouts" to layoutText(), "stores" to storeText(), "sim" to simText())
        val got = parts.associate { (k, v) -> k to digest(v) }
        if (System.getenv("PRINT_GOLDEN") == "true") {
            parts.forEach { (k, v) -> println("=== $k ${digest(v)}\n$v") }
        }
        assertEquals("classic build plans / costs / guests changed", GOLDEN["configs"], got["configs"])
        assertEquals("classic layouts 1–8 changed", GOLDEN["layouts"], got["layouts"])
        assertEquals("classic store definitions or stats changed", GOLDEN["stores"], got["stores"])
        assertEquals("classic simulation results changed", GOLDEN["sim"], got["sim"])
    }

    companion object {
        /** Recorded 2026-11-28 on top of round 4, before any World Tour change. */
        val GOLDEN = mapOf(
            "configs" to "b50673ed6f089e71b8207e2aea2479f2d644388865ef75f9c459534fc882bf4c",
            "layouts" to "ea6501817f21f9514bec8e1910ee3be0d8fe59329e96328380f79ed141f1624f",
            "stores" to "9efb571e87ec88704447cb7c0aca59591450f99034fa3312c7c5d033ea7b9d80",
            "sim" to "3632e90759be6f329214351fdd9b12686ffa5dcfcc604677a110a2d1e539c4d3",
        )
    }
}
