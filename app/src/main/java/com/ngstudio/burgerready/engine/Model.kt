package com.ngstudio.burgerready.engine

import android.content.res.AssetManager
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** GPU mesh: interleaved 20-byte vertices (pos f32x3, normal+emit i8x4, colour u8x4) + u16 indices. */
class Mesh(
    val name: String,
    val vbo: Int,
    val ibo: Int,
    val indexCount: Int,
    val minY: Float,
    val maxY: Float,
    val radius: Float,
) {
    /** One VAO per mesh with the per-instance attributes bound to the shared instance buffer. */
    var vao = 0
}

class Part(
    val name: String,
    val parent: Int,
    val pivotX: Float, val pivotY: Float, val pivotZ: Float,
    val mesh: Mesh?,
) {
    /** Pivot relative to the parent pivot (or to the model origin for roots). */
    var localX = 0f; var localY = 0f; var localZ = 0f
}

class Model(val name: String, val parts: Array<Part>, val anchors: Map<String, FloatArray>) {
    val height: Float
    val radius: Float

    init {
        for (p in parts) {
            if (p.parent >= 0) {
                val pp = parts[p.parent]
                p.localX = p.pivotX - pp.pivotX; p.localY = p.pivotY - pp.pivotY; p.localZ = p.pivotZ - pp.pivotZ
            } else {
                p.localX = p.pivotX; p.localY = p.pivotY; p.localZ = p.pivotZ
            }
        }
        var h = 0f; var r = 0f
        for (p in parts) {
            val m = p.mesh ?: continue
            h = maxOf(h, p.pivotY + m.maxY)
            r = maxOf(r, m.radius + kotlin.math.sqrt(p.pivotX * p.pivotX + p.pivotZ * p.pivotZ))
        }
        height = h; radius = r
    }

    fun part(name: String): Int = parts.indexOfFirst { it.name == name }
    fun anchor(name: String): FloatArray? = anchors[name]
}

/** Loads `.bvm` files from assets (format documented in tools/blender/bvlib.py). Must run on the GL thread. */
class ModelCache(private val assets: AssetManager) {
    private val cache = HashMap<String, Model>()

    fun get(name: String): Model = cache.getOrPut(name) { load(name) }
    fun getOrNull(name: String): Model? = try { get(name) } catch (e: Exception) { null }

    fun clear() = cache.clear()

    private fun load(name: String): Model {
        val bytes = assets.open("models/$name.bvm").use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4); bb.get(magic)
        require(String(magic) == "BVM1") { "Bad model magic in $name" }
        val partCount = bb.short.toInt() and 0xFFFF
        val parts = ArrayList<Part>(partCount)
        repeat(partCount) {
            val nl = bb.get().toInt() and 0xFF
            val nb = ByteArray(nl); bb.get(nb)
            val pname = String(nb)
            val parent = bb.short.toInt()
            val px = bb.float; val py = bb.float; val pz = bb.float
            val vc = bb.int; val ic = bb.int
            var mesh: Mesh? = null
            if (vc > 0 && ic > 0) {
                val vbytes = GlUtil.byteBuffer(vc * 20)
                var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var rad = 0f
                val start = bb.position()
                for (i in 0 until vc) {
                    val base = start + i * 20
                    val x = bb.getFloat(base); val y = bb.getFloat(base + 4); val z = bb.getFloat(base + 8)
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    val rr = kotlin.math.sqrt(x * x + z * z)
                    if (rr > rad) rad = rr
                }
                vbytes.put(bytes, start, vc * 20).position(0)
                bb.position(start + vc * 20)
                val ibuf = GlUtil.byteBuffer(ic * 2)
                ibuf.put(bytes, bb.position(), ic * 2).position(0)
                bb.position(bb.position() + ic * 2)
                val vbo = GlUtil.genBuffer()
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vc * 20, vbytes, GLES30.GL_STATIC_DRAW)
                val ibo = GlUtil.genBuffer()
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, ic * 2, ibuf, GLES30.GL_STATIC_DRAW)
                mesh = Mesh("$name/$pname", vbo, ibo, ic, minY, maxY, rad)
            } else {
                bb.position(bb.position() + vc * 20 + ic * 2)
            }
            parts.add(Part(pname, parent, px, py, pz, mesh))
        }
        val anchors = HashMap<String, FloatArray>()
        if (bb.remaining() >= 2) {
            val ac = bb.short.toInt() and 0xFFFF
            repeat(ac) {
                val nl = bb.get().toInt() and 0xFF
                val nb = ByteArray(nl); bb.get(nb)
                anchors[String(nb)] = floatArrayOf(bb.float, bb.float, bb.float)
            }
        }
        return Model(name, parts.toTypedArray(), anchors)
    }
}
