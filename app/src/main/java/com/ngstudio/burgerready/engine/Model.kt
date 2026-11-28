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

/** CPU-side `.bvm` data. Parsed once per process; every new GL context only uploads it (see [ModelData]). */
class ParsedPart(
    val name: String, val parent: Int, val px: Float, val py: Float, val pz: Float,
    val vbytes: ByteBuffer?, val ibuf: ByteBuffer?, val vertexCount: Int, val indexCount: Int,
    val minY: Float, val maxY: Float, val radius: Float,
)
class ParsedModel(val name: String, val parts: Array<ParsedPart>, val anchors: Map<String, FloatArray>)

/**
 * Process-wide cache of parsed models. Entering a restaurant creates a new GL context, which used to re-read and
 * re-parse all 116 models (~200 ms on the Realme); now only the GPU upload is repeated. [preloadAsync] parses
 * everything on a background thread at app start, so even the first visit skips the parsing.
 */
object ModelData {
    private val parsed = HashMap<String, ParsedModel>()
    @Volatile private var preloadStarted = false

    fun get(assets: AssetManager, name: String): ParsedModel {
        synchronized(parsed) { parsed[name] }?.let { return it }
        val m = parse(assets, name)
        synchronized(parsed) { parsed[name] = m }
        return m
    }

    fun preloadAsync(assets: AssetManager) {
        if (preloadStarted) return
        preloadStarted = true
        Thread({
            try {
                assets.list("models")?.forEach { f -> if (f.endsWith(".bvm")) try { get(assets, f.removeSuffix(".bvm")) } catch (_: Exception) {} }
            } catch (_: Exception) {}
        }, "ModelPreload").apply { priority = Thread.MIN_PRIORITY; isDaemon = true; start() }
    }

    private fun parse(assets: AssetManager, name: String): ParsedModel {
        val bytes = assets.open("models/$name.bvm").use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4); bb.get(magic)
        require(String(magic) == "BVM1") { "Bad model magic in $name" }
        val partCount = bb.short.toInt() and 0xFFFF
        val parts = ArrayList<ParsedPart>(partCount)
        repeat(partCount) {
            val nl = bb.get().toInt() and 0xFF
            val nb = ByteArray(nl); bb.get(nb)
            val pname = String(nb)
            val parent = bb.short.toInt()
            val px = bb.float; val py = bb.float; val pz = bb.float
            val vc = bb.int; val ic = bb.int
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
                parts.add(ParsedPart(pname, parent, px, py, pz, vbytes, ibuf, vc, ic, minY, maxY, rad))
            } else {
                bb.position(bb.position() + vc * 20 + ic * 2)
                parts.add(ParsedPart(pname, parent, px, py, pz, null, null, 0, 0, 0f, 0f, 0f))
            }
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
        return ParsedModel(name, parts.toTypedArray(), anchors)
    }
}

/** GPU models for one GL context, built from [ModelData] (format documented in tools/blender/bvlib.py). Must run on the GL thread. */
class ModelCache(private val assets: AssetManager) {
    private val cache = HashMap<String, Model>()

    fun get(name: String): Model = cache.getOrPut(name) { load(name) }
    fun getOrNull(name: String): Model? = try { get(name) } catch (e: Exception) { null }

    fun clear() = cache.clear()

    private fun load(name: String): Model {
        val pm = ModelData.get(assets, name)
        val parts = Array(pm.parts.size) { i ->
            val p = pm.parts[i]
            var mesh: Mesh? = null
            if (p.vbytes != null && p.ibuf != null) {
                val vbo = GlUtil.genBuffer()
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, p.vertexCount * 20, p.vbytes, GLES30.GL_STATIC_DRAW)
                val ibo = GlUtil.genBuffer()
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, p.indexCount * 2, p.ibuf, GLES30.GL_STATIC_DRAW)
                mesh = Mesh("$name/${p.name}", vbo, ibo, p.indexCount, p.minY, p.maxY, p.radius)
            }
            Part(p.name, p.parent, p.px, p.py, p.pz, mesh)
        }
        return Model(name, parts, pm.anchors)
    }
}
