package com.ngstudio.burgerready.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The story catalogue (game/Story.kt): structure, line style, and when beats fire. */
class StoryTest {

    @Test
    fun everyRestaurantHasAnArcAndIdsAreUnique() {
        val ids = Story.BEATS.map { it.id }
        assertEquals("duplicate beat ids", ids.size, ids.toSet().size)
        for (r in 0 until Restaurants.CLASSIC) {
            val beats = Story.beatsFor(r)
            assertEquals("R$r arrival", 1, beats.count { it.trigger == Trigger.ARRIVE })
            val mids = beats.filter { it.trigger == Trigger.CUE }
            assertTrue("R$r needs 1–2 mid-build beats, has ${mids.size}", mids.size in 1..2)
            assertEquals("R$r mid beats need distinct cues", mids.size, mids.map { it.cue }.toSet().size)
            if (r < Restaurants.CLASSIC - 1) assertNotNull("R$r completion beat", Story.completion(r))
            else assertEquals(Trigger.FINALE, Story.finale.trigger)
        }
        assertEquals(Trigger.POSTGAME, Story.postgame.trigger)
        assertNotNull(Story.beat("prologue")); assertNotNull(Story.beat("gold_intro")); assertNotNull(Story.beat("golden_book"))
    }

    /** Mobile-game bubbles: one or two short sentences, never a wall of text. */
    @Test
    fun linesAreShortAndPunchy() {
        for (b in Story.BEATS) {
            assertTrue("${b.id}: 1–8 lines", b.lines.size in 1..8)
            for (l in b.lines) {
                assertTrue("${b.id}: too long (${l.text.length}): ${l.text}", l.text.length <= 72)
                val sentences = l.text.replace("...", "…").trim().split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                assertTrue("${b.id}: more than two sentences: ${l.text}", sentences.size <= 2)
            }
        }
    }

    @Test
    fun arrivalComesFirstThenCuesThenFinale() {
        val all = Cue.entries.fold(0) { m, c -> m or c.bit }
        // a fresh restaurant: arrival first, whatever is going on
        assertEquals("s3_arrive", Story.next(3, all, emptySet(), completeOnEntry = false)?.id)
        // after arrival, a cue fires only when present
        val seen = setOf("s3_arrive")
        assertNull(Story.next(3, 0, seen, false))
        assertEquals("s3_raccoon", Story.next(3, Cue.RACCOON.bit, seen, false)?.id)
        assertEquals("s3_half", Story.next(3, Cue.HALF.bit, seen, false)?.id)
        // completion beats are not milestone beats (the EXPAND flow shows them)
        assertNull(Story.next(3, all, seen + "s3_raccoon" + "s3_half", false))
        // a restaurant that was already finished on entry stays quiet
        assertNull(Story.next(0, all, emptySet(), completeOnEntry = true))
        // …except the grand finale, which fires once the last restaurant is fully built
        val last = Restaurants.CLASSIC - 1
        assertEquals("s11_finale", Story.next(last, Cue.FULL.bit, emptySet(), completeOnEntry = true)?.id)
        assertNull(Story.next(last, Cue.FULL.bit, setOf("s11_finale"), completeOnEntry = true))
        // fully built with milestone beats still unseen: the finale comes first (after the arrival)
        assertEquals("s11_arrive", Story.next(last, all, emptySet(), completeOnEntry = false)?.id)
        assertEquals("s11_finale", Story.next(last, all, setOf("s11_arrive"), completeOnEntry = false)?.id)
    }

    @Test
    fun cuesReflectTheRunningRestaurant() {
        val cfg = Restaurants.config(2)
        val w = World(cfg, PlayerStats())
        assertEquals(0, Story.cues(w) and (Cue.TABLE.bit or Cue.HIRE.bit or Cue.DRIVE.bit or Cue.FULL.bit))
        w.debugAction(1)
        val m = Story.cues(w)
        for (c in listOf(Cue.TABLE, Cue.HIRE, Cue.HALF, Cue.FULL)) assertTrue("$c after BUILD ALL", (m and c.bit) != 0)
        assertEquals(cfg.drive, (m and Cue.DRIVE.bit) != 0)
    }

    /** Every milestone cue used by a beat can actually happen in that restaurant (the expert bot sees it). */
    @Test
    fun everyMidBeatCanFire() {
        for (r in 0 until Restaurants.CLASSIC) {
            val cfg = Restaurants.config(r)
            val wanted = Story.beatsFor(r).filter { it.trigger == Trigger.CUE }.mapNotNull { it.cue }.toMutableSet()
            val stats = StatsCalc.stats(Restaurants.expectedUpgrades(r), cfg.storeId)
            var seenAt = 0f
            Simulator.play(cfg, stats, null, 45f * 60f, startCash = if (r == 0) 30L else 0L) { w ->
                val m = Story.cues(w)
                wanted.removeAll { (m and it.bit) != 0 }
                seenAt = w.time
                wanted.isEmpty() || w.expanded
            }
            println("story R$r: all mid-beat cues seen at ${"%.1f".format(seenAt / 60f)} min")
            assertTrue("R$r cues never seen: $wanted", wanted.isEmpty())
        }
    }

    @Test
    fun tutorialTipsAreInHazelsVoice() {
        val w = World(Restaurants.config(0), PlayerStats(), startCash = 30L)
        val tip = Story.tutorialTip(w)
        assertTrue(tip, tip.isNotEmpty() && tip.length <= 72)
    }
}
