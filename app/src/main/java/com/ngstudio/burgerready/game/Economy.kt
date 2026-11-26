package com.ngstudio.burgerready.game

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Upgrade(val id: String, val label: String, val desc: String, val base: Int, val maxLevel: Int, val icon: String) {
    SPEED("speed", "Chef Speed", "+2% walking speed", 120, 30, "⚡"),
    CAPACITY("capacity", "Carry Capacity", "+1 stack slot every 2 levels", 150, 40, "🍔"),
    COOK("cook", "Cook Speed", "+2.5% station speed", 140, 40, "🔥"),
    PATIENCE("patience", "Customer Patience", "+1.5% patience", 100, 40, "⏳"),
    TIPS("tips", "Tip Bonus", "+3% tips", 130, 40, "💰"),
    START_CASH("start", "Starting Cash", "+$5 at shift start", 90, 40, "💵"),
    STAFF("staff", "Staff Speed", "+2% helper speed", 160, 30, "🧑‍🍳"),
    INCOME("income", "Store Income", "+4% store income", 200, 40, "🏪");

    fun cost(level: Int): Long = (base * 1.20.pow(level)).roundToLong5()
}

private fun Double.roundToLong5(): Long { val v = this.toLong(); return if (v < 1000) (v / 5) * 5 else (v / 50) * 50 }

data class StoreDef(
    val id: String, val name: String, val cost: Long, val expectedLevel: Int, val perk: String,
    val floor: String, val wall: Int, val trim: Int, val ground: String, val accent: Int,
    val props: List<String>, val sky: Int, val music: Int,
)

object Stores {
    // colours are 0xRRGGBB
    val ALL = listOf(
        StoreDef("wagon", "Bun Wagon", 0, 1, "Your first stand", "floor_wood", 0xFFF3DC, 0xF2542D, "ground_grass", 0xFFC23C, listOf("store_wagon_truck", "store_wagon_balloon_stand", "store_wagon_picnic_table"), 0xFFE2B8, 0),
        StoreDef("seaside", "Seaside Shack", 1_200, 15, "+10% tips", "floor_wood", 0xE9FBFF, 0x1FA6B8, "ground_sand", 0x3DDC97, listOf("store_seaside_surfboard", "store_seaside_lifeguard_tower", "store_seaside_beach_chair"), 0xCFF3FF, 1),
        StoreDef("diner", "Retro Diner", 5_000, 40, "+10% patience", "floor_checker", 0xFFE9F1, 0xE94B3C, "ground_sidewalk", 0x3DDC97, listOf("store_diner_jukebox", "store_diner_stool", "store_diner_neon_star"), 0xFFD9E6, 2),
        StoreDef("lodge", "Mountain Lodge", 12_000, 80, "+8% cook speed", "floor_wood", 0xF4E2CF, 0x8B5A3C, "ground_snow", 0xE94B3C, listOf("store_lodge_fireplace", "store_lodge_log_pile", "store_lodge_snowman"), 0xE6F0FF, 0),
        StoreDef("rooftop", "City Rooftop", 30_000, 150, "More VIP guests", "floor_tile", 0xE7EAF6, 0x2C3E66, "ground_sidewalk", 0xFFC23C, listOf("store_rooftop_ac_unit", "store_rooftop_water_tower", "store_rooftop_string_lights"), 0x9FB5E8, 3),
        StoreDef("desert", "Desert Drive-In", 70_000, 250, "Drive-thru pays +20%", "floor_terrazzo", 0xFFF0D6, 0xFF9A3C, "ground_sand", 0x1FA6B8, listOf("store_desert_cactus", "store_desert_cow_skull", "store_desert_retro_sign"), 0xFFE0A8, 1),
        StoreDef("tokyo", "Tokyo Neon Alley", 160_000, 400, "Combo builds faster", "floor_tile", 0xF6E8FF, 0xB79CFF, "ground_sidewalk", 0xFF8FB1, listOf("store_tokyo_lantern", "store_tokyo_vending_machine", "store_tokyo_bonsai"), 0xD9C8FF, 3),
        StoreDef("jungle", "Jungle Treehouse", 380_000, 650, "Pet ability +50%", "floor_wood", 0xEAF8DE, 0x4CC38A, "ground_grass", 0xFFC23C, listOf("store_jungle_vine_arch", "store_jungle_tiki_torch", "store_jungle_big_leaf"), 0xCFF5C8, 1),
        StoreDef("volcano", "Volcano Grill", 800_000, 1000, "+12% cook speed", "floor_terrazzo", 0xF7E1D7, 0xF2542D, "ground_rock", 0xFFC23C, listOf("store_volcano_lava_rock", "store_volcano_obsidian_pillar", "store_volcano_steam_vent"), 0xFFC8A8, 2),
        StoreDef("space", "Space Station Diner", 1_800_000, 1600, "Carry +2 (low gravity)", "floor_tile", 0xE8F4FF, 0x1FA6B8, "ground_metal", 0xFF8FB1, listOf("store_space_console", "store_space_astro_plant", "store_space_rocket_model"), 0x2A2F5A, 3),
        StoreDef("underwater", "Underwater Dome", 4_000_000, 2500, "+15% patience", "floor_tile", 0xE0FBFF, 0x1FA6B8, "ground_sand", 0xFF9A3C, listOf("store_underwater_coral", "store_underwater_clam", "store_underwater_bubble_column"), 0x7FD3F0, 1),
        StoreDef("candy", "Candy Kingdom", 9_000_000, 4000, "+20% tips", "floor_checker", 0xFFF0FA, 0xFF8FB1, "ground_grass", 0xB79CFF, listOf("store_candy_lollipop", "store_candy_candy_cane", "store_candy_gumdrop_tree"), 0xFFE0F4, 0),
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: ALL[0]

    /** Coins per hour while owned (before Store Income upgrade and renovation). */
    /** 1.2 × the (non-boss) level reward expected when the store becomes affordable. */
    fun incomePerHour(s: StoreDef): Long = if (s.id == "wagon") 60 else (1.2 * (50.0 + 12.0 * Math.pow(s.expectedLevel.toDouble(), 0.8))).roundToInt().toLong()

    fun renovationCost(s: StoreDef, stars: Int): Long = maxOf(500L, (s.cost * 0.35 * (stars + 1)).toLong()) / 50 * 50
    const val MAX_RENOVATION = 5
    const val INCOME_CAP_HOURS = 8.0
}

/** Cosmetics: chef skins (species + hat), pets and interior themes. */
data class Skin(val id: String, val name: String, val species: Species, val hat: String?, val apron: Int, val costBucks: Long, val costGems: Int)
data class Pet(val id: String, val name: String, val model: String, val ability: String, val costBucks: Long, val costGems: Int)
data class Theme(val id: String, val name: String, val floorTint: Int, val wallTint: Int, val trim: Int, val seat: Int, val costBucks: Long, val costGems: Int)

object Cosmetics {
    val SKINS = listOf(
        Skin("patty", "Chef Patty", Species.CORGI, "toque", 0xFFFFFF, 0, 0),
        Skin("miso", "Miso the Cat", Species.CAT, "toque", 0xFF8FB1, 2_000, 0),
        Skin("hopper", "Hopper Bunny", Species.BUNNY, "beret", 0x3DDC97, 6_000, 0),
        Skin("bruno", "Bruno Bear", Species.BEAR, "cap", 0xF2542D, 15_000, 0),
        Skin("bamboo", "Bamboo Panda", Species.PANDA, "bandana", 0x1FA6B8, 0, 120),
        Skin("ribbit", "Ribbit Frog", Species.FROG, "cowboy", 0xFFC23C, 40_000, 0),
        Skin("pip", "Pip Penguin", Species.PENGUIN, "beanie", 0x3D8BFF, 0, 250),
        Skin("ember", "Ember Fox", Species.FOX, "party", 0xB79CFF, 120_000, 0),
        Skin("quackers", "Quackers", Species.DUCK, "flower", 0xFF9A3C, 300_000, 0),
        Skin("bandit", "Bandit Raccoon", Species.RACCOON, "crown", 0x2C3E66, 0, 500),
        Skin("astro", "Astro Corgi", Species.CORGI, "helmet", 0xE8F4FF, 900_000, 0),
    )
    val PETS = listOf(
        Pet("none", "No Pet", "", "—", 0, 0),
        Pet("pug", "Biscuit the Pug", "pet_pug", "Coin magnet radius +60%", 8_000, 0),
        Pet("parrot", "Captain Parrot", "pet_parrot", "Troubles appear on your radar sooner", 25_000, 0),
        Pet("hamster", "Nibbles", "pet_hamster", "+1 carry capacity", 0, 180),
        Pet("kitten", "Mochi Kitten", "pet_kitten", "Helpers +5% speed", 70_000, 0),
        Pet("robot", "Robo-Bun", "pet_robot", "Combo builds 15% faster", 0, 400),
    )
    val THEMES = listOf(
        Theme("classic", "Classic", 0xFFFFFF, 0xFFF3DC, 0xF2542D, 0xF2542D, 0, 0),
        Theme("pastel", "Pastel Pop", 0xFFE6F2, 0xF2F0FF, 0xB79CFF, 0x8FD8FF, 3_000, 0),
        Theme("neon", "Neon Night", 0xD8D2FF, 0x2C3E66, 0xFF8FB1, 0x3DDC97, 10_000, 0),
        Theme("cabin", "Cozy Cabin", 0xF3D7B8, 0xF4E2CF, 0x8B5A3C, 0xC98A55, 25_000, 0),
        Theme("royal", "Royal Gold", 0xFFF2CC, 0xFFF7E6, 0xE0A21B, 0x9B2C5C, 0, 150),
        Theme("spooky", "Spooky", 0xE0D4F0, 0x5B4A70, 0xFF9A3C, 0x6B4C9A, 60_000, 0),
        Theme("winter", "Winter Festive", 0xEAF6FF, 0xFFFFFF, 0xE94B3C, 0x4CC38A, 0, 220),
        Theme("tropical", "Tropical", 0xE6FFF4, 0xFFFBEA, 0x1FA6B8, 0xFFC23C, 150_000, 0),
    )
}

/** Converts upgrade levels (+ skin/pet/store perks) into gameplay stats. */
object StatsCalc {
    fun stats(levels: Map<Upgrade, Int>, storeId: String = "wagon", petId: String = "none"): PlayerStats {
        fun l(u: Upgrade) = levels[u] ?: 0
        var cap = 4 + l(Upgrade.CAPACITY) / 2
        var cook = 1f + 0.025f * l(Upgrade.COOK)
        var patience = 1f + 0.015f * l(Upgrade.PATIENCE)
        var tips = 0.03f * l(Upgrade.TIPS)
        var staff = 1f + 0.02f * l(Upgrade.STAFF)
        var magnet = 1.15f
        var drive = 1f
        var vip = 0f
        var combo = 1f
        when (storeId) {
            "seaside" -> tips += 0.10f
            "diner" -> patience += 0.10f
            "lodge" -> cook += 0.08f
            "rooftop" -> vip += 0.04f
            "desert" -> drive += 0.20f
            "tokyo" -> combo += 0.25f
            "volcano" -> cook += 0.12f
            "space" -> cap += 2
            "underwater" -> patience += 0.15f
            "candy" -> tips += 0.20f
        }
        val petBoost = if (storeId == "jungle") 1.5f else 1f
        when (petId) {
            "pug" -> magnet *= 1f + 0.6f * petBoost
            "hamster" -> cap += if (petBoost > 1f) 2 else 1
            "kitten" -> staff += 0.05f * petBoost
            "robot" -> combo += 0.15f * petBoost
        }
        return PlayerStats(
            moveSpeed = 4.3f * (1f + 0.02f * l(Upgrade.SPEED)),
            capacity = cap,
            cookSpeedMul = cook,
            patienceMul = patience,
            tipBonus = tips,
            startCashBonus = 5 * l(Upgrade.START_CASH),
            staffSpeedMul = staff,
            magnetRadius = magnet,
            driveThruPayMul = drive,
            vipChanceAdd = vip,
            comboGainMul = combo,
        )
    }
}

/**
 * Typical-player economy model: average 2★, spends ~55% of Bucks on the cheapest useful upgrade.
 * Used to pick level targets that match the strength an average player has at that point.
 */
object EconomyModel {
    private var expected: Array<IntArray>? = null

    fun expectedLevels(level: Int): Map<Upgrade, Int> {
        val table = expected ?: build().also { expected = it }
        val row = table[min(level, LevelGenerator.MAX_LEVEL)]
        return Upgrade.entries.associateWith { row[it.ordinal] }
    }

    fun expectedStats(level: Int): PlayerStats = StatsCalc.stats(expectedLevels(level))

    private fun build(): Array<IntArray> {
        val n = LevelGenerator.MAX_LEVEL
        val out = Array(n + 1) { IntArray(Upgrade.entries.size) }
        val lv = IntArray(Upgrade.entries.size)
        var wallet = 0.0
        val tracks = Upgrade.entries.filter { it != Upgrade.INCOME && it != Upgrade.START_CASH }
        for (k in 1..n) {
            wallet += LevelGenerator.reward(k) * 1.25 * 0.55
            while (true) {
                val cheapest = tracks.filter { lv[it.ordinal] < it.maxLevel }.minByOrNull { it.cost(lv[it.ordinal]) } ?: break
                val c = cheapest.cost(lv[cheapest.ordinal])
                if (c > wallet) break
                wallet -= c
                lv[cheapest.ordinal]++
            }
            out[k] = lv.copyOf()
        }
        return out
    }
}
