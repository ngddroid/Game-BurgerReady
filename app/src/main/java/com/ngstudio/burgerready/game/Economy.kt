package com.ngstudio.burgerready.game

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Upgrade(val id: String, val label: String, val desc: String, val base: Int, val maxLevel: Int, val icon: String) {
    SPEED("speed", "Chef Speed", "+2% walking speed", 120, 30, "⚡"),
    CAPACITY("capacity", "Carry Capacity", "+1 stack slot every 2 upgrades", 150, 40, "🍔"),
    COOK("cook", "Cook Speed", "+2.5% station speed", 140, 40, "🔥"),
    PATIENCE("patience", "Customer Patience", "+1.5% patience", 100, 40, "⏳"),
    TIPS("tips", "Tip Bonus", "+3% tips", 130, 40, "💰"),
    PRICES("start", "Menu Prices", "+2% food prices", 110, 40, "💵"),
    STAFF("staff", "Staff Speed", "+2% helper speed", 160, 30, "🧑‍🍳"),
    INCOME("income", "Offline Income", "+4% restaurant idle income", 200, 40, "🏪");

    fun cost(level: Int): Long = (base * 1.20.pow(level)).roundToLong5()
}

private fun Double.roundToLong5(): Long { val v = this.toLong(); return if (v < 1000) (v / 5) * 5 else (v / 50) * 50 }

/** Ambient particles drawn around the camera (render/GameRenderer.weather). */
enum class Weather { NONE, SNOW, BUBBLES, SPARKLE_GOLD, SPARKLE_PINK, EMBERS, HEARTS, MIST, PETALS, LEAVES, RAIN, FIREFLIES, DUST, CONFETTI, STARS, STEAM, CLOUDS, BUTTERFLIES, SPARKLE }

/** A World Tour restaurant's perk (classic restaurants keep their hand-written perks in [StatsCalc]). */
enum class PerkKind { NONE, TIPS, PATIENCE, COOK, VIP, DRIVE, COMBO, CARRY, PET, STAFF, ALL }

/**
 * One restaurant's identity. The look fields (night, weather, tree, floor tint, ground colour) used to be switches on
 * the classic ids inside the renderer; they are data now so World Tour restaurants can set them. The classic entries
 * carry exactly the values those switches had.
 */
data class StoreDef(
    val id: String, val name: String, val perk: String,
    val floor: String, val wall: Int, val trim: Int, val ground: String, val accent: Int,
    val props: List<String>, val sky: Int, val music: Int,
    /** 0 = day … 0.9 = deep night (warm lamp light inside the walls). */
    val night: Float = 0f,
    val weather: Weather = Weather.NONE,
    /** Model used for the outdoor trees. */
    val tree: String = "tree_round",
    /** Dining-floor tint in the default theme. */
    val floorTint: Int = 0xFFFFFF,
    /** Ground colour override (-1 = from [ground]). */
    val groundColor: Int = -1,
    val perkKind: PerkKind = PerkKind.NONE,
    val perkAmount: Float = 0f,
    /** World Tour region id and step (0..19); null for the classic restaurants. */
    val region: String? = null,
    val step: Int = -1,
) {
    val isTour: Boolean get() = region != null
    /** Hub / card art: classic stores have their own diorama, tour restaurants share their region's. */
    val art: String get() = if (region != null) "region_$region" else "store_$id"
}

/** The 12 restaurant locations, in the order the player opens them (see [Restaurants]). */
object Stores {
    // colours are 0xRRGGBB
    val ALL = listOf(
        StoreDef("wagon", "Bun Wagon", "Your first stand", "floor_wood", 0xFFE7C2, 0xF2542D, "ground_grass", 0xFFC23C, listOf("store_wagon_truck", "store_wagon_balloon_stand", "store_wagon_picnic_table"), 0xFFD9A0, 0),
        StoreDef("seaside", "Seaside Shack", "+10% tips", "floor_wood", 0xCFF4FA, 0x1FA6B8, "ground_sand", 0x3DDC97, listOf("store_seaside_surfboard", "store_seaside_lifeguard_tower", "store_seaside_beach_chair"), 0xA8E6FF, 1, weather = Weather.HEARTS, tree = "palm"),
        StoreDef("diner", "Retro Diner", "+10% patience", "floor_checker", 0xFFD2E2, 0xE94B3C, "ground_sidewalk", 0x3DDC97, listOf("store_diner_jukebox", "store_diner_stool", "store_diner_neon_star"), 0xFFC4D9, 2),
        StoreDef("lodge", "Mountain Lodge", "+8% cook speed", "floor_wood", 0xEBCFAF, 0x8B5A3C, "ground_snow", 0xE94B3C, listOf("store_lodge_fireplace", "store_lodge_log_pile", "store_lodge_snowman"), 0xCFE0FF, 0, weather = Weather.SNOW, tree = "tree_pine_snow"),
        StoreDef("rooftop", "City Rooftop", "More VIP guests", "floor_tile", 0xD3D9F0, 0x2C3E66, "ground_sidewalk", 0xFFC23C, listOf("store_rooftop_ac_unit", "store_rooftop_water_tower", "store_rooftop_string_lights"), 0x8FA6E0, 3, night = 0.72f, floorTint = 0xB3BEDD),
        StoreDef("desert", "Desert Drive-In", "Drive-thru pays +20%", "floor_terrazzo", 0xFFDDB0, 0xFF8A2C, "ground_sand", 0x1FA6B8, listOf("store_desert_cactus", "store_desert_cow_skull", "store_desert_retro_sign"), 0xFFD08A, 1, tree = "store_desert_cactus"),
        StoreDef("tokyo", "Tokyo Neon Alley", "Combo builds faster", "floor_tile", 0xE6D2FF, 0xA47CFF, "ground_sidewalk", 0xFF8FB1, listOf("store_tokyo_lantern", "store_tokyo_vending_machine", "store_tokyo_bonsai"), 0xC9B2FF, 3, night = 0.72f, tree = "store_tokyo_bonsai", floorTint = 0xC9B3EF),
        StoreDef("jungle", "Jungle Treehouse", "Pet ability +50%", "floor_wood", 0xD6F2C4, 0x38B27A, "ground_grass", 0xFFC23C, listOf("store_jungle_vine_arch", "store_jungle_tiki_torch", "store_jungle_big_leaf"), 0xB5EDA8, 1, weather = Weather.HEARTS, tree = "palm"),
        StoreDef("volcano", "Volcano Grill", "+12% cook speed", "floor_terrazzo", 0xF2CDBE, 0xE5431F, "ground_rock", 0xFFC23C, listOf("store_volcano_lava_rock", "store_volcano_obsidian_pillar", "store_volcano_steam_vent"), 0xFFB08A, 2, weather = Weather.EMBERS, tree = "store_volcano_lava_rock"),
        StoreDef("space", "Space Station Diner", "Carry +2 (low gravity)", "floor_tile", 0xD4E8FF, 0x1FA6B8, "ground_metal", 0xFF8FB1, listOf("store_space_console", "store_space_astro_plant", "store_space_rocket_model"), 0x2A2F5A, 3, night = 0.9f, weather = Weather.SPARKLE_GOLD, tree = "store_space_astro_plant", floorTint = 0xB7CCEE),
        StoreDef("underwater", "Underwater Dome", "+15% patience", "floor_tile", 0xC8F3FF, 0x1A8FB0, "ground_sand", 0xFF9A3C, listOf("store_underwater_coral", "store_underwater_clam", "store_underwater_bubble_column"), 0x5FC4EA, 1, weather = Weather.BUBBLES, tree = "store_underwater_coral", floorTint = 0xA9E0EA),
        StoreDef("candy", "Candy Kingdom", "+20% tips", "floor_checker", 0xFFD9F0, 0xFF6FA0, "ground_grass", 0xB79CFF, listOf("store_candy_lollipop", "store_candy_candy_cane", "store_candy_gumdrop_tree"), 0xFFCDEB, 0, weather = Weather.SPARKLE_PINK, tree = "store_candy_gumdrop_tree"),
    )

    private val classicIds = ALL.map { it.id }.toSet()
    fun isClassic(id: String) = id in classicIds
    /** Classic or World Tour restaurant by id (unknown ids fall back to the Bun Wagon, as before). */
    fun byId(id: String): StoreDef = ALL.firstOrNull { it.id == id } ?: Tour.storeById(id) ?: ALL[0]

    /** Renovation stars raise a restaurant's idle income by 10% each. */
    fun renovationCost(s: StoreDef, stars: Int): Long =
        Restaurants.roundCost(1500f * Restaurants.priceMul(Restaurants.indexOf(s.id)) * (stars + 1)).toLong()
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
        Pet("parrot", "Captain Parrot", "pet_parrot", "Fixes troubles twice as fast", 25_000, 0),
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
        var fix = 1f
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
        var petBoost = if (storeId == "jungle") 1.5f else 1f
        // World Tour restaurants: the region's perk family, stronger towards the flagship (game/Tour.kt)
        if (!Stores.isClassic(storeId)) {
            val def = Stores.byId(storeId)
            val a = def.perkAmount
            when (def.perkKind) {
                PerkKind.TIPS -> tips += a
                PerkKind.PATIENCE -> patience += a
                PerkKind.COOK -> cook += a
                PerkKind.VIP -> vip += a
                PerkKind.DRIVE -> drive += a
                PerkKind.COMBO -> combo += a
                PerkKind.CARRY -> cap += a.toInt()
                PerkKind.PET -> petBoost = 1f + a
                PerkKind.STAFF -> staff += a
                PerkKind.ALL -> { tips += a; patience += a; cook += a }
                PerkKind.NONE -> {}
            }
        }
        when (petId) {
            "pug" -> magnet *= 1f + 0.6f * petBoost
            "hamster" -> cap += if (petBoost > 1f) 2 else 1
            "kitten" -> staff += 0.05f * petBoost
            "robot" -> combo += 0.15f * petBoost
            "parrot" -> fix += 1f * petBoost
        }
        return PlayerStats(
            moveSpeed = 4.3f * (1f + 0.02f * l(Upgrade.SPEED)),
            capacity = cap,
            cookSpeedMul = cook,
            patienceMul = patience,
            tipBonus = tips,
            priceBonus = 0.02f * l(Upgrade.PRICES),
            staffSpeedMul = staff,
            magnetRadius = magnet,
            driveThruPayMul = drive,
            vipChanceAdd = vip,
            comboGainMul = combo,
            fixSpeedMul = fix,
        )
    }
}

