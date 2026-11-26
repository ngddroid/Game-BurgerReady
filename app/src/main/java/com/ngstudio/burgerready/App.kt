package com.ngstudio.burgerready

import android.app.Application
import android.content.Context
import android.os.Build
import com.ngstudio.burgerready.ads.AdsProvider
import com.ngstudio.burgerready.ads.DebugMockAds
import com.ngstudio.burgerready.ads.NoAds
import com.ngstudio.burgerready.audio.AudioEngine
import com.ngstudio.burgerready.data.GameRepo
import com.ngstudio.burgerready.engine.Quality
import com.ngstudio.burgerready.game.LevelGenerator
import java.nio.ByteBuffer
import java.nio.ByteOrder

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = GameRepo(this)
        repo.load()
        audio = AudioEngine(this)
        applySettings()
        Thread({ audio.load() }, "sfx-load").start()
        ads = if (BuildConfig.DEBUG_AD_UNLOCK) DebugMockAds else NoAds
        loadLevelTargets(this)
    }

    companion object {
        lateinit var instance: App; private set
        lateinit var repo: GameRepo; private set
        lateinit var audio: AudioEngine; private set
        lateinit var ads: AdsProvider; private set

        /** Per-sound volume trims (dB → linear) from tools/audio/README.md, so loud tones don't dominate. */
        private val TRIM = mapOf(
            "ui_popup" to -6, "ui_error" to -5, "ui_purchase" to -2, "ui_reward" to -1, "ui_star1" to -2, "ui_star2" to -2, "ui_star3" to -3,
            "upgrade" to -1, "hire" to -7, "blender_whirr" to -6, "customer_order" to -7, "customer_happy" to -6, "customer_angry" to -3,
            "combo_break" to -6, "rush_siren" to -7, "fire_whoosh" to -2, "mop_squeak" to -6, "raccoon_chitter" to -3, "machine_jam" to -3,
            "car_horn" to -7, "car_engine" to -1, "powerup" to -2, "timer_warning" to -7, "level_win" to -1, "level_fail" to -4,
            "vip_arrive" to -4, "critic_hmm" to -5, "footstep1" to -10, "footstep2" to -10, "pickup" to -3, "drop" to -4,
        ).mapValues { Math.pow(10.0, it.value / 20.0).toFloat() }

        fun sfx(name: String, vol: Float = 1f, rate: Float = 1f) {
            if (!::audio.isInitialized) return
            audio.play(name, vol * (TRIM[name] ?: 1f), rate)
        }

        fun haptic(kind: Int) { if (::audio.isInitialized) audio.haptic(kind) }

        fun applySettings() {
            val d = repo.data
            audio.musicOn = d.music; audio.sfxOn = d.sfx; audio.hapticsOn = d.haptics
            audio.musicVolume = d.musicVolume; audio.sfxVolume = d.sfxVolume
            audio.applyVolumes()
        }

        /** Auto quality from RAM / GPU class; Settings can override. */
        fun quality(context: Context): Quality {
            return when (repo.data.quality) {
                "low" -> Quality.LOW
                "medium" -> Quality.MEDIUM
                "high" -> Quality.HIGH
                else -> {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
                    val gb = mi.totalMem / (1024.0 * 1024 * 1024)
                    when {
                        gb < 2.5 || am.isLowRamDevice -> Quality.LOW
                        gb >= 7.5 && Build.VERSION.SDK_INT >= 31 -> Quality.HIGH
                        else -> Quality.MEDIUM
                    }
                }
            }
        }

        fun loadLevelTargets(context: Context) {
            try {
                val bytes = context.assets.open("levels/targets.bin").use { it.readBytes() }
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                LevelGenerator.installTargets(IntArray(bytes.size / 4) { bb.int })
            } catch (e: Exception) { /* not tuned yet: analytic targets are used */ }
        }
    }
}
