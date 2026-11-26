package com.ngstudio.burgerready.engine

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

object GlUtil {
    private const val TAG = "GrillGL"

    fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(s)
            GLES30.glDeleteShader(s)
            throw IllegalStateException("Shader compile failed: $log\n$src")
        }
        return s
    }

    fun program(vs: String, fs: String): Int {
        val v = compile(GLES30.GL_VERTEX_SHADER, vs)
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v)
        GLES30.glAttachShader(p, f)
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(p)
            throw IllegalStateException("Program link failed: $log")
        }
        GLES30.glDeleteShader(v)
        GLES30.glDeleteShader(f)
        return p
    }

    fun floatBuffer(n: Int): FloatBuffer =
        ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun shortBuffer(n: Int): ShortBuffer =
        ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder()).asShortBuffer()

    fun byteBuffer(n: Int): ByteBuffer = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder())

    fun genBuffer(): Int {
        val a = IntArray(1); GLES30.glGenBuffers(1, a, 0); return a[0]
    }

    fun genVao(): Int {
        val a = IntArray(1); GLES30.glGenVertexArrays(1, a, 0); return a[0]
    }

    fun genTexture(): Int {
        val a = IntArray(1); GLES30.glGenTextures(1, a, 0); return a[0]
    }

    fun checkError(where: String) {
        val e = GLES30.glGetError()
        if (e != GLES30.GL_NO_ERROR) Log.w(TAG, "GL error 0x${Integer.toHexString(e)} at $where")
    }
}

/** Small column-major 4x4 matrix helpers working on FloatArray(16) without allocation. */
object M4 {
    fun identity(m: FloatArray, o: Int = 0) {
        for (i in 0 until 16) m[o + i] = 0f
        m[o] = 1f; m[o + 5] = 1f; m[o + 10] = 1f; m[o + 15] = 1f
    }

    /** out = a * b (column-major). out must not alias a or b. */
    fun mul(out: FloatArray, oo: Int, a: FloatArray, ao: Int, b: FloatArray, bo: Int) {
        for (c in 0 until 4) {
            val b0 = b[bo + c * 4]; val b1 = b[bo + c * 4 + 1]; val b2 = b[bo + c * 4 + 2]; val b3 = b[bo + c * 4 + 3]
            for (r in 0 until 4) {
                out[oo + c * 4 + r] = a[ao + r] * b0 + a[ao + 4 + r] * b1 + a[ao + 8 + r] * b2 + a[ao + 12 + r] * b3
            }
        }
    }

    /** Builds T * Ry(yaw) * S(sx,sy,sz). */
    fun trs(m: FloatArray, o: Int, x: Float, y: Float, z: Float, yaw: Float, sx: Float, sy: Float, sz: Float) {
        val c = kotlin.math.cos(yaw); val s = kotlin.math.sin(yaw)
        m[o] = c * sx; m[o + 1] = 0f; m[o + 2] = -s * sx; m[o + 3] = 0f
        m[o + 4] = 0f; m[o + 5] = sy; m[o + 6] = 0f; m[o + 7] = 0f
        m[o + 8] = s * sz; m[o + 9] = 0f; m[o + 10] = c * sz; m[o + 11] = 0f
        m[o + 12] = x; m[o + 13] = y; m[o + 14] = z; m[o + 15] = 1f
    }

    /** Builds T(x,y,z) * Rz(rz) * Rx(rx) * Ry(ry) * S(s) — enough for procedural part animation. */
    fun trsEuler(m: FloatArray, o: Int, x: Float, y: Float, z: Float, rx: Float, ry: Float, rz: Float, sx: Float, sy: Float, sz: Float) {
        val cx = kotlin.math.cos(rx); val sxn = kotlin.math.sin(rx)
        val cy = kotlin.math.cos(ry); val syn = kotlin.math.sin(ry)
        val cz = kotlin.math.cos(rz); val szn = kotlin.math.sin(rz)
        // R = Rz * Rx * Ry
        val r00 = cz * cy - szn * sxn * syn
        val r01 = -szn * cx
        val r02 = cz * syn + szn * sxn * cy
        val r10 = szn * cy + cz * sxn * syn
        val r11 = cz * cx
        val r12 = szn * syn - cz * sxn * cy
        val r20 = -cx * syn
        val r21 = sxn
        val r22 = cx * cy
        m[o] = r00 * sx; m[o + 1] = r10 * sx; m[o + 2] = r20 * sx; m[o + 3] = 0f
        m[o + 4] = r01 * sy; m[o + 5] = r11 * sy; m[o + 6] = r21 * sy; m[o + 7] = 0f
        m[o + 8] = r02 * sz; m[o + 9] = r12 * sz; m[o + 10] = r22 * sz; m[o + 11] = 0f
        m[o + 12] = x; m[o + 13] = y; m[o + 14] = z; m[o + 15] = 1f
    }

    fun transformPoint(m: FloatArray, o: Int, x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = m[o] * x + m[o + 4] * y + m[o + 8] * z + m[o + 12]
        out[1] = m[o + 1] * x + m[o + 5] * y + m[o + 9] * z + m[o + 13]
        out[2] = m[o + 2] * x + m[o + 6] * y + m[o + 10] * z + m[o + 14]
    }

    fun ortho(m: FloatArray, l: Float, r: Float, b: Float, t: Float, n: Float, f: Float) {
        android.opengl.Matrix.orthoM(m, 0, l, r, b, t, n, f)
    }

    fun perspective(m: FloatArray, fovYDeg: Float, aspect: Float, n: Float, f: Float) {
        android.opengl.Matrix.perspectiveM(m, 0, fovYDeg, aspect, n, f)
    }

    fun lookAt(m: FloatArray, ex: Float, ey: Float, ez: Float, cx: Float, cy: Float, cz: Float) {
        android.opengl.Matrix.setLookAtM(m, 0, ex, ey, ez, cx, cy, cz, 0f, 1f, 0f)
    }
}
