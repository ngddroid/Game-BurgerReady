package com.ngstudio.burgerready.engine

import android.opengl.GLES30

/** Builds simple meshes in code (ground planes, road strips) in the same 20-byte vertex format as .bvm. */
class MeshBuilder {
    private val v = ArrayList<Float>()
    private val n = ArrayList<Int>()
    private val c = ArrayList<Int>()
    private val idx = ArrayList<Int>()

    private fun vert(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, rgb: Int, tint: Boolean): Int {
        v.add(x); v.add(y); v.add(z)
        n.add((nx * 127).toInt()); n.add((ny * 127).toInt()); n.add((nz * 127).toInt())
        c.add(rgb); c.add(if (tint) 255 else 0)
        return v.size / 3 - 1
    }

    /** Horizontal quad at height y. */
    fun quadXZ(x0: Float, z0: Float, x1: Float, z1: Float, y: Float, rgb: Int, tint: Boolean = false) {
        val a = vert(x0, y, z0, 0f, 1f, 0f, rgb, tint)
        val b = vert(x0, y, z1, 0f, 1f, 0f, rgb, tint)
        val cc = vert(x1, y, z1, 0f, 1f, 0f, rgb, tint)
        val d = vert(x1, y, z0, 0f, 1f, 0f, rgb, tint)
        idx.add(a); idx.add(b); idx.add(cc); idx.add(a); idx.add(cc); idx.add(d)
    }

    /** Horizontal quad with per-corner colours (for soft gradients). */
    fun quadXZ4(x0: Float, z0: Float, x1: Float, z1: Float, y: Float, c00: Int, c01: Int, c11: Int, c10: Int) {
        val a = vert(x0, y, z0, 0f, 1f, 0f, c00, false)
        val b = vert(x0, y, z1, 0f, 1f, 0f, c01, false)
        val cc = vert(x1, y, z1, 0f, 1f, 0f, c11, false)
        val d = vert(x1, y, z0, 0f, 1f, 0f, c10, false)
        idx.add(a); idx.add(b); idx.add(cc); idx.add(a); idx.add(cc); idx.add(d)
    }

    fun build(name: String): Mesh {
        val vc = v.size / 3
        val bb = GlUtil.byteBuffer(vc * 20)
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var rad = 0f
        for (i in 0 until vc) {
            val x = v[i * 3]; val y = v[i * 3 + 1]; val z = v[i * 3 + 2]
            bb.putFloat(x); bb.putFloat(y); bb.putFloat(z)
            bb.put(n[i * 3].toByte()); bb.put(n[i * 3 + 1].toByte()); bb.put(n[i * 3 + 2].toByte()); bb.put(0)
            val rgb = c[i * 2]
            bb.put(((rgb shr 16) and 255).toByte()); bb.put(((rgb shr 8) and 255).toByte()); bb.put((rgb and 255).toByte()); bb.put(c[i * 2 + 1].toByte())
            minY = minOf(minY, y); maxY = maxOf(maxY, y); rad = maxOf(rad, kotlin.math.sqrt(x * x + z * z))
        }
        bb.position(0)
        val ib = GlUtil.byteBuffer(idx.size * 2)
        for (i in idx) ib.putShort(i.toShort())
        ib.position(0)
        val vbo = GlUtil.genBuffer()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vc * 20, bb, GLES30.GL_STATIC_DRAW)
        val ibo = GlUtil.genBuffer()
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, ib, GLES30.GL_STATIC_DRAW)
        return Mesh(name, vbo, ibo, idx.size, minY, maxY, rad)
    }
}
