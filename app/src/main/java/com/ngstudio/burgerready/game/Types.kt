package com.ngstudio.burgerready.game

/** Kinds of station that produce items. */
enum class StationType(val model: String, val label: String) {
    GRILL("st_grill", "Grill"),
    FRYER("st_fryer", "Fryer"),
    SODA("st_soda", "Soda Fountain"),
    SHAKE("st_shake", "Shake Blender"),
}

/** Everything that can be carried in a stack. */
enum class Item(
    val id: String,
    val price: Int,
    val station: StationType?,
    val cookTime: Float,
    val height: Float,
    val label: String,
) {
    BURGER("burger", 5, StationType.GRILL, 2.1f, 0.20f, "Classic Burger"),
    FRIES("fries", 3, StationType.FRYER, 1.5f, 0.22f, "Fries"),
    SODA("soda", 2, StationType.SODA, 1.1f, 0.25f, "Soda"),
    CHEESE("cheese", 8, StationType.GRILL, 2.5f, 0.21f, "Cheese Burger"),
    DOUBLE("double", 14, StationType.GRILL, 3.1f, 0.27f, "Double Stack"),
    SHAKE("shake", 6, StationType.SHAKE, 2.2f, 0.29f, "Milkshake"),
    VEGGIE("veggie", 9, StationType.GRILL, 2.7f, 0.21f, "Veggie Burger"),
    TRAY("tray", 0, null, 0f, 0.08f, "Dirty Tray");

    val isFood: Boolean get() = this != TRAY

    companion object {
        val FOODS = entries.filter { it.isFood }
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

enum class Species(val id: String) {
    CORGI("corgi"), CAT("cat"), BUNNY("bunny"), BEAR("bear"), PANDA("panda"),
    FROG("frog"), PENGUIN("penguin"), FOX("fox"), DUCK("duck"), RACCOON("raccoon");
}

enum class StaffRole(val label: String) { CASHIER("Cashier"), RUNNER("Runner"), CLEANER("Cleaner") }

enum class TroubleKind(val label: String) { SPILL("Spill"), FIRE("Grease Fire"), RACCOON("Raccoon Thief"), JAM("Jammed Machine"), SLEEPY("Sleepy Guest") }

enum class PowerUpKind(val label: String) { ESPRESSO("Espresso"), MAGNET("Magnet"), SPATULA("Golden Spatula") }

/** Persistent per-player multipliers coming from Kitchen upgrades, skins, pets and the restaurant perk. */
data class PlayerStats(
    val moveSpeed: Float = 4.3f,
    val capacity: Int = 4,
    val cookSpeedMul: Float = 1f,
    val patienceMul: Float = 1f,
    val tipBonus: Float = 0f,
    val priceBonus: Float = 0f,
    val staffSpeedMul: Float = 1f,
    val magnetRadius: Float = 1.15f,
    val driveThruPayMul: Float = 1f,
    val vipChanceAdd: Float = 0f,
    val comboGainMul: Float = 1f,
    /** How fast standing at a trouble fixes it (Captain Parrot). */
    val fixSpeedMul: Float = 1f,
)
