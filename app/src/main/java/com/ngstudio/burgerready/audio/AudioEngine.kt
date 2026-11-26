package com.ngstudio.burgerready.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/** Sound effects (SoundPool) + adaptive music (custom AudioTrack mixer) + haptics. */
class AudioEngine(private val context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(14)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = HashMap<String, Int>()
    private val lastPlay = HashMap<String, Long>()
    private var loopStream = 0

    @Volatile var sfxOn = true
    @Volatile var sfxVolume = 1f
    @Volatile var musicOn = true
    @Volatile var musicVolume = 0.7f
    @Volatile var hapticsOn = true

    private val music = MusicMixer()
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    } catch (e: Exception) { null }

    fun load() {
        try {
            context.assets.list("audio/sfx")?.forEach { f ->
                if (f.endsWith(".wav")) {
                    try {
                        context.assets.openFd("audio/sfx/$f").use { fd -> ids[f.removeSuffix(".wav")] = pool.load(fd, 1) }
                    } catch (e: Exception) { Log.w("Audio", "sfx $f: ${e.message}") }
                }
            }
        } catch (e: Exception) { Log.w("Audio", "no sfx folder") }
    }

    fun play(name: String, vol: Float = 1f, rate: Float = 1f, minGapMs: Long = 35) {
        if (!sfxOn) return
        val id = ids[name] ?: return
        val now = SystemClock.uptimeMillis()
        val last = lastPlay[name] ?: 0L
        if (now - last < minGapMs) return
        lastPlay[name] = now
        val v = (vol * sfxVolume).coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun startLoop(name: String, vol: Float) {
        stopLoop()
        if (!sfxOn) return
        val id = ids[name] ?: return
        loopStream = pool.play(id, vol * sfxVolume, vol * sfxVolume, 0, -1, 1f)
    }

    fun stopLoop() { if (loopStream != 0) { pool.stop(loopStream); loopStream = 0 } }

    // ---------------------------------------------------------------------------------- music
    fun playMusic(main: String, rush: String? = null) {
        if (!musicOn) { music.stop(); return }
        music.play(context, main, rush, musicVolume)
    }
    fun setRush(on: Boolean) { music.rushTarget = if (on) 1f else 0f }
    fun stopMusic() = music.stop()
    fun pauseMusic() = music.pause()
    fun resumeMusic() { if (musicOn) music.resume() }
    fun applyVolumes() { music.volume = musicVolume; if (!musicOn) music.stop() }
    fun jingle(name: String) = play(name, 1f, 1f, 0)

    // ---------------------------------------------------------------------------------- haptics
    fun haptic(kind: Int) {
        if (!hapticsOn) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val effect = when (kind) {
                    0 -> VibrationEffect.EFFECT_TICK
                    1 -> VibrationEffect.EFFECT_CLICK
                    2 -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    else -> VibrationEffect.EFFECT_HEAVY_CLICK
                }
                v.vibrate(VibrationEffect.createPredefined(effect))
            } else {
                v.vibrate(VibrationEffect.createOneShot(longArrayOf(8, 15, 30, 45)[kind.coerceIn(0, 3)], VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) { }
    }

    fun release() { music.stop(); pool.release() }
}

/** Streams one or two sample-synchronous stereo WAV stems and crossfades between them. */
class MusicMixer {
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile var volume = 0.7f
    @Volatile var rushTarget = 0f
    private var current = ""

    private class Pcm(val data: ShortArray, val channels: Int, val rate: Int)

    private fun loadWav(context: Context, name: String): Pcm? {
        return try {
            val bytes = context.assets.open("audio/music/$name.wav").use { it.readBytes() }
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var pos = 12
            var channels = 2; var rate = 22050; var dataOff = -1; var dataLen = 0
            while (pos + 8 <= bytes.size) {
                val id = String(bytes, pos, 4); val len = bb.getInt(pos + 4)
                if (id == "fmt ") { channels = bb.getShort(pos + 10).toInt(); rate = bb.getInt(pos + 12) }
                if (id == "data") { dataOff = pos + 8; dataLen = min(len, bytes.size - dataOff); break }
                pos += 8 + len + (len and 1)
            }
            if (dataOff < 0) return null
            val n = dataLen / 2
            val out = ShortArray(n)
            bb.position(dataOff)
            bb.asShortBuffer().get(out, 0, n)
            Pcm(out, channels, rate)
        } catch (e: Exception) { Log.w("Music", "missing $name"); null }
    }

    fun play(context: Context, main: String, rush: String?, vol: Float) {
        val key = main + "|" + rush
        volume = vol
        if (running && key == current) { paused = false; return }
        stop()
        current = key
        val a = loadWav(context, main) ?: return
        val b = rush?.let { loadWav(context, it) }?.takeIf { it.data.size == a.data.size && it.channels == a.channels }
        running = true; paused = false
        thread = Thread({ loop(a, b) }, "music").apply { priority = Thread.NORM_PRIORITY + 1; start() }
    }

    private fun loop(a: Pcm, b: Pcm?) {
        val chMask = if (a.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuf = AudioTrack.getMinBufferSize(a.rate, chMask, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(a.rate).setChannelMask(chMask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minBuf * 2, 8192))
            .build()
        track.play()
        val chunk = 2048
        val buf = ShortArray(chunk)
        var pos = 0
        var rush = 0f
        var fadeIn = 0f
        val total = a.data.size
        try {
            while (running) {
                if (paused) { Thread.sleep(40); continue }
                for (i in 0 until chunk) {
                    rush += (rushTarget - rush) * 0.00004f
                    fadeIn = min(1f, fadeIn + 0.00002f)
                    val sa = a.data[pos].toFloat()
                    val s = if (b != null) sa * (1f - rush) + b.data[pos] * rush else sa
                    buf[i] = (s * volume * fadeIn).toInt().coerceIn(-32768, 32767).toShort()
                    pos++; if (pos >= total) pos = 0
                }
                track.write(buf, 0, chunk)
            }
        } catch (e: Exception) { } finally {
            try { track.stop(); track.release() } catch (e: Exception) { }
        }
    }

    fun pause() { paused = true }
    fun resume() { paused = false }
    fun stop() {
        running = false
        thread?.join(300)
        thread = null
        current = ""
    }
}
