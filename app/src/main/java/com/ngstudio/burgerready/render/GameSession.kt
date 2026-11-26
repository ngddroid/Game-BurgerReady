package com.ngstudio.burgerready.render

import com.ngstudio.burgerready.game.Ev
import com.ngstudio.burgerready.game.Item
import com.ngstudio.burgerready.game.LevelConfig
import com.ngstudio.burgerready.game.PlayerStats
import com.ngstudio.burgerready.game.ShiftResult
import com.ngstudio.burgerready.game.World

/** Immutable HUD snapshot published from the GL thread to Compose (~15 Hz). */
data class HudState(
    val levelId: Int = 0,
    val title: String = "",
    val cash: Int = 0,
    val revenue: Int = 0,
    val target: Int = 1,
    val star2: Int = 2,
    val star3: Int = 3,
    val timeLeft: Float = 0f,
    val overtime: Boolean = false,
    val combo: Float = 1f,
    val rush: Boolean = false,
    val goalReached: Boolean = false,
    val walkouts: Int = 0,
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
    val tutorial: Int = 0,
    val coinFlyId: Int = 0,
    val coinFlyAmount: Int = 0,
    val coinFlyX: Float = 0f,
    val coinFlyY: Float = 0f,
)

/** Everything the GL thread needs to run one shift. Created on the UI thread, owned by the GL thread. */
class GameSession(
    val cfg: LevelConfig,
    val stats: PlayerStats,
    val style: VisualStyle,
    val onFinished: (ShiftResult) -> Unit,
    val onEvent: (Ev, Item?, Float) -> Unit,
) {
    val world = World(cfg, stats, style.playerSpecies)
    @Volatile var paused = false
    @Volatile var joyX = 0f
    @Volatile var joyY = 0f
    @Volatile var finishRequested = false
    @Volatile var hud = HudState()
    @Volatile var finishedNotified = false
    /** Debug builds only: 1 = instant 3-star win, 2 = +500 cash. */
    @Volatile var debugAction = 0
    var cameraSnapped = false
    var endDelay = 0f
}
