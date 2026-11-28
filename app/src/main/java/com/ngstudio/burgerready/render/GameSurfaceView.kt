package com.ngstudio.burgerready.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.ngstudio.burgerready.engine.Quality
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.hypot
import kotlin.math.min

/**
 * GL surface hosting the game. A floating joystick appears wherever the finger lands; its state is
 * mirrored into [joystick] so the Compose HUD can draw the ring.
 */
@SuppressLint("ViewConstructor")
class GameSurfaceView(context: Context, quality: Quality) : GLSurfaceView(context) {
    val game = GameRenderer(context.applicationContext, quality)

    class Joystick {
        @Volatile var active = false; @Volatile var ox = 0f; @Volatile var oy = 0f; @Volatile var kx = 0f; @Volatile var ky = 0f
        /** Bumped on every touch change (UI thread); the Compose ring redraws only when this changes. */
        val version = androidx.compose.runtime.mutableIntStateOf(0)
    }
    val joystick = Joystick()
    /** True once the first restaurant frame is on screen (UI thread state for the entry curtain). */
    val firstFrame = androidx.compose.runtime.mutableStateOf(false)
    private val radius = resources.displayMetrics.density * 56f
    private var pointerId = -1

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MsaaChooser(if (quality == Quality.LOW) 0 else 4))
        preserveEGLContextOnPause = true
        game.onFirstFrame = { post { firstFrame.value = true } }
        setRenderer(game)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = game.session
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = e.getPointerId(0)
                joystick.active = true; joystick.ox = e.x; joystick.oy = e.y; joystick.kx = e.x; joystick.ky = e.y
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointerId)
                if (i >= 0) {
                    var dx = e.getX(i) - joystick.ox; var dy = e.getY(i) - joystick.oy
                    val d = hypot(dx, dy)
                    if (d > radius * 1.6f) {
                        // drag the base along so the stick never feels "stuck"
                        val k = (d - radius * 1.6f) / d
                        joystick.ox += dx * k; joystick.oy += dy * k
                        dx = e.getX(i) - joystick.ox; dy = e.getY(i) - joystick.oy
                    }
                    joystick.kx = e.getX(i); joystick.ky = e.getY(i)
                    val dd = hypot(dx, dy)
                    val mag = if (dd < radius * 0.12f) 0f else min(1f, dd / radius)
                    if (s != null) { s.joyX = if (dd > 0f) dx / dd * mag else 0f; s.joyY = if (dd > 0f) dy / dd * mag else 0f }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                joystick.active = false
                pointerId = -1
                if (s != null) { s.joyX = 0f; s.joyY = 0f }
            }
        }
        joystick.version.intValue++
        return true
    }

    /** Prefers RGBA8888 + depth24 + MSAA, falling back gracefully. */
    private class MsaaChooser(private val samples: Int) : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val attempts = listOf(samples, 2, 0).distinct().filter { it <= samples }
            for (s in attempts) {
                val attrs = intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_RENDERABLE_TYPE, 0x40, // EGL_OPENGL_ES3_BIT_KHR
                    EGL10.EGL_SAMPLE_BUFFERS, if (s > 0) 1 else 0, EGL10.EGL_SAMPLES, s, EGL10.EGL_NONE,
                )
                val num = IntArray(1)
                val cfgs = arrayOfNulls<EGLConfig>(1)
                if (egl.eglChooseConfig(display, attrs, cfgs, 1, num) && num[0] > 0 && cfgs[0] != null) return cfgs[0]!!
            }
            val attrs = intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, 0x40, EGL10.EGL_NONE)
            val num = IntArray(1); val cfgs = arrayOfNulls<EGLConfig>(1)
            egl.eglChooseConfig(display, attrs, cfgs, 1, num)
            return cfgs[0]!!
        }
    }
}
