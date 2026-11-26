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
    val unlockLevel: Int,
    val station: StationType?,
    val cookTime: Float,
    val height: Float,
    val label: String,
) {
    BURGER("burger", 5, 1, StationType.GRILL, 2.1f, 0.20f, "Classic Burger"),
    FRIES("fries", 3, 4, StationType.FRYER, 1.5f, 0.22f, "Fries"),
    SODA("soda", 2, 6, StationType.SODA, 1.1f, 0.25f, "Soda"),
    CHEESE("cheese", 8, 12, StationType.GRILL, 2.5f, 0.21f, "Cheese Burger"),
    DOUBLE("double", 14, 25, StationType.GRILL, 3.1f, 0.27f, "Double Stack"),
    SHAKE("shake", 6, 40, StationType.SHAKE, 2.2f, 0.29f, "Milkshake"),
    VEGGIE("veggie", 9, 60, StationType.GRILL, 2.7f, 0.21f, "Veggie Burger"),
    TRAY("tray", 0, 1, null, 0f, 0.08f, "Dirty Tray");

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

enum class BossType(val label: String, val blurb: String) {
    CRITIC("Critic's Day", "A famous food critic visits. Serve every critic fast!"),
    FESTIVAL("Festival Rush", "The whole town is hungry. Survive the rush waves!"),
    CELEBRITY("Celebrity Party", "VIP guests with huge orders and huge tips."),
    CONTEST("Eating Contest", "Contestants want burgers. Lots of burgers."),
    RALLY("Food-Truck Rally", "Drive-thru madness. Keep the cars moving!"),
    REOPENING("Grand Reopening", "Rebuild the kitchen from scratch as fast as you can."),
}

enum class Mutator(val label: String, val blurb: String) {
    HUNGRY_HOUR("Hungry Hour", "+30% more customers"),
    PICKY_EATERS("Picky Eaters", "Bigger orders"),
    TIP_FRENZY("Tip Frenzy", "Bigger tips, less patience"),
    SLIPPERY("Slippery Floors", "More spills"),
    INSPECTION("Critic Inspection", "A critic drops by"),
    NIGHT_SHIFT("Night Shift", "Lights low, neon on"),
    HEAT_WAVE("Heat Wave", "Everyone wants drinks"),
    BIG_PARTY("Big Party", "Customers arrive in groups"),
    DOUBLE_DRIVE("Double Drive-Thru", "Cars arrive twice as often"),
}

/** Outcome stars for a finished shift. */
data class ShiftResult(
    val levelId: Int,
    val won: Boolean,
    val stars: Int,
    val revenue: Int,
    val target: Int,
    val served: Int,
    val walkouts: Int,
    val bestCombo: Float,
    val timeLeft: Float,
    val itemsServed: IntArray,
)

/** Persistent per-player multipliers coming from Kitchen upgrades, skins, pets and the store perk. */
data class PlayerStats(
    val moveSpeed: Float = 4.3f,
    val capacity: Int = 4,
    val cookSpeedMul: Float = 1f,
    val patienceMul: Float = 1f,
    val tipBonus: Float = 0f,
    val startCashBonus: Int = 0,
    val staffSpeedMul: Float = 1f,
    val magnetRadius: Float = 1.15f,
    val driveThruPayMul: Float = 1f,
    val vipChanceAdd: Float = 0f,
    val comboGainMul: Float = 1f,
)
