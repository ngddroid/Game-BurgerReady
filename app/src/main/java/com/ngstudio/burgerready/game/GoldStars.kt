package com.ngstudio.burgerready.game

import kotlin.math.max

/**
 * Gold stars: the late-game "Golden Tour" (docs/STORY_SCRIPT.md, docs/GAP_ANALYSIS.md).
 *
 * Once a restaurant is finished it offers three challenges, each met within one visit. A star is earned once and
 * kept forever (`SaveData.gold`, store id → star bitmask); each pays [GEMS] gems and adds [INCOME_BONUS] to that
 * restaurant's idle income. 12 restaurants × 3 = 36 stars. Targets come from the expert bot's numbers in a fully built
 * restaurant (GoldCalibrationTest) and are validated by RestaurantFlowTest.goldStarsAreEarnable.
 */
enum class GoalKind { SERVE, REVENUE, COMBO, FLAWLESS, VIPS, CARS, TROUBLES }

class GoldGoal(val star: Int, val kind: GoalKind, val target: Long) {
    fun text(): String = when (kind) {
        GoalKind.SERVE -> "Serve $target guests"
        GoalKind.REVENUE -> "Earn $target in one visit"
        GoalKind.COMBO -> "Reach a x3 combo"
        GoalKind.FLAWLESS -> if (target == 1L) "Survive a rush with no walkouts" else "Survive $target rushes with no walkouts"
        GoalKind.VIPS -> "Serve $target VIP guests"
        GoalKind.CARS -> "Serve $target drive-thru cars"
        GoalKind.TROUBLES -> "Fix $target troubles"
    }
}

/** What happened during one visit to a restaurant (cumulative since the visit started). */
class VisitStats(val served: Int, val revenue: Long, val bestCombo: Float, val flawless: Int, val vips: Int, val cars: Int, val troubles: Int) {
    companion object {
        fun of(w: World) = VisitStats(w.served, w.revenue, w.bestCombo, w.flawlessRushes, w.vipsServed, w.carsServed, w.troublesFixedCount)
    }
}

object GoldStars {
    const val PER_RESTAURANT = 3
    const val GEMS = 10
    const val INCOME_BONUS = 0.05
    /** Gems for the very last star (all 36). */
    const val BOOK_GEMS = 300
    val TOTAL: Int get() = Restaurants.CLASSIC * PER_RESTAURANT

    /** The second challenge of each restaurant, matched to what that place teaches (drive-thru, troubles, VIPs…). */
    private val SECOND = arrayOf(
        GoalKind.COMBO to 3L, GoalKind.VIPS to 8L, GoalKind.CARS to 20L, GoalKind.TROUBLES to 4L,
        GoalKind.VIPS to 20L, GoalKind.CARS to 15L, GoalKind.FLAWLESS to 2L, GoalKind.TROUBLES to 5L,
        GoalKind.TROUBLES to 6L, GoalKind.VIPS to 40L, GoalKind.TROUBLES to 5L, GoalKind.FLAWLESS to 2L,
    )
    /** One-visit revenue targets in units of the restaurant's money scale (≈ 3.5 min for the expert bot). */
    private val REVENUE = intArrayOf(2700, 3400, 4000, 6500, 6200, 7500, 8000, 10500, 12000, 14000, 16000, 17000)

    fun goals(index: Int): List<GoldGoal> {
        val i = index.coerceIn(0, Restaurants.CLASSIC - 1)
        val (kind, n) = SECOND[i]
        return listOf(
            GoldGoal(0, GoalKind.SERVE, 100L + 10L * i),
            GoldGoal(1, kind, n),
            GoldGoal(2, GoalKind.REVENUE, Restaurants.roundCost(REVENUE[i] * Restaurants.priceMul(i)).toLong()),
        )
    }

    fun value(kind: GoalKind, v: VisitStats): Long = when (kind) {
        GoalKind.SERVE -> v.served.toLong()
        GoalKind.REVENUE -> v.revenue
        GoalKind.COMBO -> if (v.bestCombo >= 2.99f) 3L else max(1L, v.bestCombo.toLong())
        GoalKind.FLAWLESS -> v.flawless.toLong()
        GoalKind.VIPS -> v.vips.toLong()
        GoalKind.CARS -> v.cars.toLong()
        GoalKind.TROUBLES -> v.troubles.toLong()
    }

    /** Bitmask of the stars [v] meets in restaurant [index]. */
    fun met(index: Int, v: VisitStats): Int {
        var m = 0
        for (g in goals(index)) if (value(g.kind, v) >= g.target) m = m or (1 shl g.star)
        return m
    }

    fun count(mask: Int): Int = Integer.bitCount(mask and 7)
}
