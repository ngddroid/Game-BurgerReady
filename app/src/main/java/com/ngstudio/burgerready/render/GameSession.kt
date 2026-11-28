package com.ngstudio.burgerready.render

import com.ngstudio.burgerready.game.Ev
import com.ngstudio.burgerready.game.Item
import com.ngstudio.burgerready.game.PadKind
import com.ngstudio.burgerready.game.PlayerStats
import com.ngstudio.burgerready.game.PowerUpKind
import com.ngstudio.burgerready.game.RestaurantConfig
import com.ngstudio.burgerready.game.RestaurantProgress
import com.ngstudio.burgerready.game.StaffRole
import com.ngstudio.burgerready.game.StationType
import com.ngstudio.burgerready.game.World
import java.util.concurrent.atomic.AtomicLong

/** Immutable HUD snapshot published from the GL thread to Compose (~15 Hz). */
data class HudState(
    val storeName: String = "",
    val built: Int = 0,
    val total: Int = 1,
    val cash: Long = 0,
    val combo: Float = 1f,
    val rush: Boolean = false,
    val served: Int = 0,
    val stack: Int = 0,
    val capacity: Int = 4,
    val troubles: List<String> = emptyList(),
    val boosts: List<Pair<String, Float>> = emptyList(),
    val tip: String = "",
    val toast: String = "",
    val toastId: Int = 0,
    val fps: Int = 0,
    val drawCalls: Int = 0,
    val coinFlyId: Int = 0,
    val coinFlyAmount: Int = 0,
    val coinFlyX: Float = 0f,
    val coinFlyY: Float = 0f,
    /** Cost still to pay on the next pad in the plan (0 = none visible). */
    val nextPadLeft: Long = 0,
    val expanded: Boolean = false,
    /** Story milestone mask (game/Story.cues). */
    val cues: Int = 0,
)

/**
 * Cumulative counters for this visit to a restaurant (plus its build progress and the wallet).
 * The game screen saves the difference between two snapshots.
 */
class SessionSnapshot(
    val cash: Long,
    val progress: RestaurantProgress,
    val served: Int,
    val revenue: Long,
    val items: IntArray,
    val vips: Int,
    val troubles: Int,
    val trays: Int,
    val rushes: Int,
    val built: Int,
    val critics: Int,
    val bestCombo: Float,
    val cards: Set<String>,
    val flawless: Int = 0,
    val cars: Int = 0,
) {
    /** This visit so far, for the gold-star challenges. */
    fun visit() = com.ngstudio.burgerready.game.VisitStats(served, revenue, bestCombo, flawless, vips, cars, troubles)

    /**
     * What happened since [base]. [fromStart] = base is the start of the visit (the best combo then counts);
     * otherwise only a combo still running now ([combo]) counts, since the best combo can't be split.
     */
    fun visitSince(base: SessionSnapshot, fromStart: Boolean, combo: Float) = com.ngstudio.burgerready.game.VisitStats(
        served - base.served, revenue - base.revenue, if (fromStart) bestCombo else combo,
        flawless - base.flawless, vips - base.vips, cars - base.cars, troubles - base.troubles,
    )
}

/** Takes a snapshot of [World] for saving. Called ~1/s on the GL thread, or on the UI thread once the GL thread is paused. */
fun World.snapshot(): SessionSnapshot {
    val cards = HashSet<String>()
    for (i in itemsServed.indices) if (itemsServed[i] > 0) cards.add("food_" + Item.entries[i].id)
    for (s in speciesServed) cards.add("guest_" + s.id)
    for (t in troublesFixed) cards.add("trouble_" + t.name.lowercase())
    for (p in powerUpsUsed) cards.add("special_" + when (p) { PowerUpKind.ESPRESSO -> "espresso"; PowerUpKind.MAGNET -> "magnet"; PowerUpKind.SPATULA -> "spatula" })
    if (vipsServed > 0) cards.add("special_vip")
    if (carsServed > 0) cards.add("special_drive")
    if (criticsServed > 0) cards.add("special_critic")
    if (rushesDone > 0) cards.add("special_rush")
    if (flawlessRushes > 0) cards.add("special_perfect")
    if (bestCombo >= 2.99f) cards.add("special_combo3")
    if (staff.any { it.role == StaffRole.CASHIER }) cards.add("special_cashier")
    // milestones
    if (tables.any { it.built }) cards.add("mile_first_table")
    if (stations.count { it.built && it.spec.type == StationType.GRILL } >= 2) cards.add("mile_second_grill")
    if (tables.isNotEmpty() && tables.all { it.built }) cards.add("mile_full_house")
    if (driveBuilt) cards.add("mile_drive_open")
    if (pads.none { it.spec.kind == PadKind.HIRE && !it.done }) cards.add("mile_full_staff")
    if (stations.any { it.level >= 3 }) cards.add("mile_max_machine")
    if (pads.none { it.spec.kind != PadKind.EXPAND && !it.done }) cards.add("mile_fully_built")
    if (rushesDone >= 3) cards.add("mile_rush_pro")
    return SessionSnapshot(
        cash = cash, progress = progress(), served = served, revenue = revenue, items = itemsServed.copyOf(),
        vips = vipsServed, troubles = troublesFixedCount, trays = traysCleared, rushes = rushesDone, built = padsBought,
        critics = criticsServed, bestCombo = bestCombo, cards = cards, flawless = flawlessRushes, cars = carsServed,
    )
}

/** Everything the GL thread needs to run one restaurant. Created on the UI thread, the World is owned by the GL thread. */
class GameSession(
    val cfg: RestaurantConfig,
    stats: PlayerStats,
    val style: VisualStyle,
    startCash: Long,
    saved: RestaurantProgress,
    val onEvent: (Ev, Item?, Float) -> Unit,
) {
    val world = World(cfg, stats, style.playerSpecies, startCash, saved)
    @Volatile var paused = false
    @Volatile var joyX = 0f
    @Volatile var joyY = 0f
    @Volatile var hud = HudState()
    /** Latest save snapshot (~1 s old at most). */
    @Volatile var snapshot: SessionSnapshot? = null
    /** Money added/removed by the UI (in-game upgrade purchases, rewards); applied on the GL thread. */
    val cashAdjust = AtomicLong(0)
    /** New player stats after an in-game upgrade purchase; applied on the GL thread. */
    @Volatile var newStats: PlayerStats? = null
    /** Debug builds only: 1 = build everything except EXPAND, 2 = +cash, 3 = finish the restaurant (incl. EXPAND). */
    @Volatile var debugAction = 0
    var cameraSnapped = false
}
