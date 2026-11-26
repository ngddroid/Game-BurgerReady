package com.ngstudio.burgerready.engine

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * High 3/4 follow camera. The target follows the player with critically-damped smoothing and a small
 * look-ahead; `focus()` performs cinematic pans (e.g. to a newly unlocked station).
 */
class Camera {
    var pitchDeg = 54f
    var yawDeg = 0f
    var distance = 15.5f
    var fovY = 30f
    var near = 1f
    var far = 90f

    var targetX = 0f; var targetZ = 0f
    var targetY = 0.4f
    private var goalX = 0f; private var goalZ = 0f
    private var lookAheadX = 0f; private var lookAheadZ = 0f

    var eyeX = 0f; var eyeY = 0f; var eyeZ = 0f
    var rightX = 1f; var rightY = 0f; var rightZ = 0f
    var upX = 0f; var upY = 1f; var upZ = 0f
    val view = FloatArray(16)
    val proj = FloatArray(16)
    val viewProj = FloatArray(16)
    private val invViewProj = FloatArray(16)
    var aspect = 0.45f

    // Shake / zoom punch
    private var shake = 0f
    private var shakeT = 0f
    private var zoomPunch = 0f

    // Focus pan
    private var focusX = 0f; private var focusZ = 0f
    private var focusTime = 0f
    private var focusHold = 0f

    /** Half-size (m) of the light frustum used for shadows; covers what the camera sees. */
    val shadowExtent: Float get() = distance * 0.95f

    fun snapTo(x: Float, z: Float) {
        targetX = x; targetZ = z; goalX = x; goalZ = z
        lookAheadX = 0f; lookAheadZ = 0f
    }

    fun follow(x: Float, z: Float, vx: Float, vz: Float) {
        goalX = x; goalZ = z
        lookAheadX += (vx * 0.35f - lookAheadX) * 0.05f
        lookAheadZ += (vz * 0.35f - lookAheadZ) * 0.05f
    }

    fun focus(x: Float, z: Float, hold: Float = 1.2f) {
        focusX = x; focusZ = z; focusHold = hold; focusTime = hold + 0.6f
    }

    val isFocusing: Boolean get() = focusTime > 0f

    fun shake(amount: Float) { shake = maxOf(shake, amount) }
    fun punch(amount: Float) { zoomPunch = maxOf(zoomPunch, amount) }

    fun update(dt: Float) {
        var gx = goalX + lookAheadX
        var gz = goalZ + lookAheadZ
        if (focusTime > 0f) {
            focusTime -= dt
            val k = if (focusTime > focusHold) 1f - (focusTime - focusHold) / 0.6f else if (focusTime < 0.6f) focusTime / 0.6f else 1f
            val e = k * k * (3 - 2 * k)
            gx += (focusX - gx) * e
            gz += (focusZ - gz) * e
        }
        val a = 1f - exp(-dt * 6.5f)
        targetX += (gx - targetX) * a
        targetZ += (gz - targetZ) * a
        shakeT += dt
        shake *= exp(-dt * 7f)
        zoomPunch *= exp(-dt * 6f)
        rebuild()
    }

    fun rebuild() {
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val d = distance * (1f - zoomPunch * 0.08f)
        val sx = if (shake > 0.001f) sin(shakeT * 47f) * shake * 0.15f else 0f
        val sz = if (shake > 0.001f) cos(shakeT * 39f) * shake * 0.15f else 0f
        val tx = targetX + sx; val tz = targetZ + sz
        val ty = targetY
        eyeX = tx + sin(yaw) * cos(pitch) * d
        eyeY = ty + sin(pitch) * d
        eyeZ = tz + cos(yaw) * cos(pitch) * d
        M4.lookAt(view, eyeX, eyeY, eyeZ, tx, ty, tz)
        M4.perspective(proj, fovY, aspect, near, far)
        android.opengl.Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)
        // Camera basis from the view matrix rows.
        rightX = view[0]; rightY = view[4]; rightZ = view[8]
        upX = view[1]; upY = view[5]; upZ = view[9]
        android.opengl.Matrix.invertM(invViewProj, 0, viewProj, 0)
    }

    /** Projects a world point to screen pixels. Returns false if behind the camera. */
    fun project(x: Float, y: Float, z: Float, w: Int, h: Int, out: FloatArray): Boolean {
        val m = viewProj
        val cx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val cy = m[1] * x + m[5] * y + m[9] * z + m[13]
        val cw = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (cw <= 0.0001f) return false
        out[0] = (cx / cw * 0.5f + 0.5f) * w
        out[1] = (1f - (cy / cw * 0.5f + 0.5f)) * h
        return true
    }

    /**
     * Converts a joystick direction in screen space (dx right, dy DOWN) into a world XZ direction.
     * Screen right = world (cos yaw, -sin yaw); screen down (towards the camera) = world (sin yaw, cos yaw).
     */
    fun screenDirToWorld(dx: Float, dy: Float, out: FloatArray) {
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val c = cos(yaw); val s = sin(yaw)
        out[0] = dx * c + dy * s
        out[1] = -dx * s + dy * c
    }
}
