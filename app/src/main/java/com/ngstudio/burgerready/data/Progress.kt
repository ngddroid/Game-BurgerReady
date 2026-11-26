package com.ngstudio.burgerready.data

import com.ngstudio.burgerready.game.BossType
import com.ngstudio.burgerready.game.Item
import com.ngstudio.burgerready.game.Mutator
import com.ngstudio.burgerready.game.Species
import com.ngstudio.burgerready.game.Stores
import com.ngstudio.burgerready.game.TroubleKind
import com.ngstudio.burgerready.game.Upgrade

/** Immutable snapshot of everything we persist. */
data class SaveData(
    val version: Int = 1,
    val onboardingDone: Boolean = false,
    val highestUnlocked: Int = 1,
    val stars: String = "",                       // index = level-1, chars '0'..'3'
    val adUnlocked: Set<Int> = emptySet(),        // debug-only rewarded unlocks
    val bucks: Long = 250,
    val gems: Int = 10,
    val upgrades: Map<String, Int> = emptyMap(),
    val stores: Map<String, Int> = mapOf("wagon" to 0),   // owned store -> renovation stars
    val storeCollect: Map<String, Long> = emptyMap(),     // store -> last collect epoch ms
    val selectedStore: String = "wagon",
    val skins: Set<String> = setOf("patty"),
    val selectedSkin: String = "patty",
    val pets: Set<String> = setOf("none"),
    val selectedPet: String = "none",
    val themes: Set<String> = setOf("classic"),
    val selectedTheme: String = "classic",
    // daily
    val lastLoginDay: Long = -1,
    val loginStreak: Int = 0,          // 0..6 index of the next reward to claim
    val loginClaimedDay: Long = -1,
    val lastSpinDay: Long = -1,
    val extraSpins: Int = 0,
    val missionDay: Long = -1,
    val missions: List<Mission> = emptyList(),
    val missionChestClaimed: Boolean = false,
    val freeChestDay: Long = -1,
    // collection & achievements
    val cards: Set<String> = emptySet(),
    val setsClaimed: Set<String> = emptySet(),
    val counters: Map<String, Long> = emptyMap(),
    val achievementsClaimed: Set<String> = emptySet(),
    val chefXp: Long = 0,
    val chefRankClaimed: Int = 1,
    // settings
    val music: Boolean = true,
    val sfx: Boolean = true,
    val haptics: Boolean = true,
    val musicVolume: Float = 0.7f,
    val sfxVolume: Float = 1f,
    val quality: String = "auto",
    val highFps: Boolean = false,
) {
    fun starsOf(level: Int): Int = if (level - 1 < stars.length) stars[level - 1] - '0' else 0
    fun totalStars(): Int = stars.sumOf { (it - '0').coerceAtLeast(0) }
    fun upgrade(u: Upgrade): Int = upgrades[u.id] ?: 0
    fun counter(k: String): Long = counters[k] ?: 0L
    fun upgradeLevels(): Map<Upgrade, Int> = Upgrade.entries.associateWith { upgrade(it) }
}

data class Mission(val id: String, val kind: String, val item: String, val goal: Int, val progress: Int, val rewardBucks: Long, val rewardGems: Int, val claimed: Boolean) {
    val done get() = progress >= goal
}

/** Recipe Book card catalogue (60 cards in 7 sets). */
object Cards {
    data class Card(val id: String, val name: String, val set: String, val icon: String, val hint: String)

    val ALL: List<Card> = buildList {
        Item.FOODS.forEach { add(Card("food_${it.id}", it.label, "Menu", "icon_${it.id}", "Serve a ${it.label}")) }
        Species.entries.forEach { add(Card("guest_${it.id}", it.id.replaceFirstChar { c -> c.uppercase() } + " Guest", "Guests", it.id, "Serve a ${it.id} customer")) }
        Stores.ALL.forEach { add(Card("store_${it.id}", it.name, "Stores", it.id, "Open ${it.name}")) }
        BossType.entries.forEach { add(Card("boss_${it.name.lowercase()}", it.label, "Boss Days", "crown", "Win ${it.label}")) }
        Mutator.entries.forEach { add(Card("mut_${it.name.lowercase()}", it.label, "Wild Shifts", "bolt", "Win a ${it.label} shift")) }
        TroubleKind.entries.forEach { add(Card("trouble_${it.name.lowercase()}", it.label, "Troubles", "exclaim", "Fix a ${it.label}")) }
        listOf(
            "vip" to "VIP Guest", "critic" to "Food Critic", "combo3" to "Combo Master", "rush" to "Rush Survivor",
            "espresso" to "Espresso Shot", "magnet" to "Coin Magnet", "spatula" to "Golden Spatula", "drive" to "Drive-Thru Hero",
            "cashier" to "First Hire", "perfect" to "Flawless Shift",
        ).forEach { add(Card("special_${it.first}", it.second, "Specials", "star", it.second)) }
    }
    val SETS: List<String> = ALL.map { it.set }.distinct()
    fun setReward(set: String): Int = when (set) { "Menu" -> 40; "Guests" -> 50; "Stores" -> 150; "Boss Days" -> 80; "Wild Shifts" -> 80; "Troubles" -> 50; else -> 100 }
}

/** 50 achievements = 10 tracks × 5 tiers. */
object Achievements {
    data class Track(val key: String, val name: String, val tiers: List<Long>, val icon: String)

    val TRACKS = listOf(
        Track("served", "Happy Customers", listOf(50, 250, 1_000, 5_000, 25_000), "emo_happy"),
        Track("wins", "Shift Winner", listOf(5, 25, 100, 500, 2_000), "star"),
        Track("stars", "Star Collector", listOf(15, 75, 300, 1_500, 6_000), "star"),
        Track("threestar", "Perfectionist", listOf(5, 25, 100, 400, 1_500), "crown"),
        Track("revenue", "Big Earner", listOf(1_000, 10_000, 100_000, 1_000_000, 10_000_000), "coin"),
        Track("combo3", "Combo King", listOf(1, 10, 50, 200, 800), "bolt"),
        Track("storesOpened", "Franchise Builder", listOf(2, 4, 6, 9, 12), "store"),
        Track("upgrades", "Kitchen Pro", listOf(5, 20, 60, 150, 280), "wrench"),
        Track("troubles", "Trouble Shooter", listOf(5, 25, 100, 400, 1_500), "exclaim"),
        Track("vips", "VIP Treatment", listOf(3, 15, 60, 250, 1_000), "crown"),
    )
    fun gems(tier: Int) = listOf(5, 10, 20, 40, 80)[tier]
    fun id(track: String, tier: Int) = "$track#$tier"
}

/** Chef Rank from XP (customers served). */
object ChefRank {
    fun xpFor(rank: Int): Long = if (rank <= 1) 0 else (40.0 * Math.pow(rank.toDouble() - 1, 1.6)).toLong()
    fun rankOf(xp: Long): Int { var r = 1; while (r < 100 && xp >= xpFor(r + 1)) r++; return r }
}

/** Daily login calendar rewards (7-day loop). */
object LoginRewards {
    data class Reward(val bucks: Long, val gems: Int, val chest: Boolean)
    val DAYS = listOf(Reward(200, 0, false), Reward(300, 2, false), Reward(450, 0, false), Reward(0, 8, false),
        Reward(700, 0, false), Reward(900, 5, false), Reward(1200, 20, true))
}

/** Daily spin wheel segments. */
object SpinWheel {
    data class Segment(val label: String, val bucks: Long, val gems: Int, val color: Long)
    val SEGMENTS = listOf(
        Segment("150", 150, 0, 0xFFFFC23C), Segment("5", 0, 5, 0xFF1FA6B8), Segment("400", 400, 0, 0xFFF2542D),
        Segment("CHEST", 0, 0, 0xFFB79CFF), Segment("250", 250, 0, 0xFF3DDC97), Segment("15", 0, 15, 0xFF3D8BFF),
        Segment("800", 800, 0, 0xFFFF8FB1), Segment("2", 0, 2, 0xFFFF9A3C),
    )
    val WEIGHTS = floatArrayOf(22f, 12f, 16f, 6f, 20f, 5f, 8f, 11f)
}
