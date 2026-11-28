package com.ngstudio.burgerready.engine

import android.opengl.GLES30
import java.nio.FloatBuffer

/** Rendering quality tier, chosen from the GPU/RAM at start-up (Settings can override). */
enum class Quality(val shadowSize: Int, val outlines: Boolean, val shadows: Boolean) {
    LOW(0, false, false),
    MEDIUM(1024, true, true),
    HIGH(2048, true, true),
}

/**
 * Per-scene toon lighting. [sunColor] multiplies the lit side, [skyColor] the shadow side (a coloured shade,
 * not grey), [groundColor] tints the rim light. [saturation] > 1 adds a little colour after lighting.
 */
class Lighting {
    val sunDir = floatArrayOf(-0.45f, 0.78f, 0.43f)
    val sunColor = floatArrayOf(1.03f, 0.99f, 0.92f)
    val skyColor = floatArrayOf(0.68f, 0.62f, 0.72f)
    val groundColor = floatArrayOf(1f, 0.92f, 0.8f)
    val fogColor = floatArrayOf(0.98f, 0.86f, 0.70f)
    var fogStart = 48f
    var fogEnd = 95f
    var night = 0f
    var saturation = 1.14f
    val clearColor = floatArrayOf(0.98f, 0.86f, 0.70f)
    /** World XZ rectangle (x0, z0, x1, z1) lit by [inLit]/[inShade] instead of the sun/sky, blended by [interiorAmt]. */
    val interior = floatArrayOf(0f, 0f, 0f, 0f)
    val inLit = floatArrayOf(1f, 1f, 1f)
    val inShade = floatArrayOf(0.6f, 0.6f, 0.6f)
    var interiorAmt = 0f

    fun normalizeSun() {
        val l = kotlin.math.sqrt(sunDir[0] * sunDir[0] + sunDir[1] * sunDir[1] + sunDir[2] * sunDir[2])
        sunDir[0] /= l; sunDir[1] /= l; sunDir[2] /= l
    }
}

/**
 * Instanced forward renderer. Usage per frame:
 *   begin(camera) → draw(...)/sprite(...) many times → end()
 * All draw calls are batched by mesh, uploaded once and drawn instanced.
 */
class Renderer(var quality: Quality) {
    private class Batch(val mesh: Mesh) {
        var data = FloatArray(INST_FLOATS * 32)
        var count = 0
        var shadow = false
        var outline = false
        var offsetBytes = 0
        fun reserve(): Int {
            if ((count + 1) * INST_FLOATS > data.size) data = data.copyOf(data.size * 2)
            return count++ * INST_FLOATS
        }
    }

    companion object {
        const val INST_FLOATS = 24
        const val SPRITE_FLOATS = 16
        val OUTLINE_COLOR = floatArrayOf(0.20f, 0.13f, 0.20f)
    }

    val lighting = Lighting()
    lateinit var camera: Camera
    var width = 1; var height = 1

    private var toon = 0; private var depth = 0; private var outline = 0; private var spriteProg = 0
    private val uToon = HashMap<String, Int>()
    private var uDepthLightVP = -1
    private var uOutViewProj = -1; private var uOutWidth = -1; private var uOutColor = -1
    private var uSprViewProj = -1; private var uSprRight = -1; private var uSprUp = -1; private var uSprTex = -1
    private var instanceVbo = 0
    private var instanceBuf: FloatBuffer = GlUtil.floatBuffer(INST_FLOATS * 2048)

    private val opaque = HashMap<Mesh, Batch>()
    private val transparent = HashMap<Mesh, Batch>()
    private val activeOpaque = ArrayList<Batch>()
    private val activeTransparent = ArrayList<Batch>()

    // Shadows
    private var shadowFbo = 0; private var shadowTex = 0; private var shadowSize = 0
    private var dummyShadowTex = 0
    private val lightView = FloatArray(16); private val lightProj = FloatArray(16); val lightVP = FloatArray(16)

    // Sprites
    private var spriteVao = 0; private var spriteQuadVbo = 0; private var spriteInstVbo = 0
    private var spriteData = FloatArray(SPRITE_FLOATS * 512)
    private var spriteCount = 0
    private var spriteBuf: FloatBuffer = GlUtil.floatBuffer(SPRITE_FLOATS * 512)
    var spriteTexture = 0

    private var shadowsThisFrame = false
    var drawCalls = 0; private set
    var instances = 0; private set

    fun init() {
        toon = GlUtil.program(Shaders.TOON_VS, Shaders.TOON_FS)
        depth = GlUtil.program(Shaders.DEPTH_VS, Shaders.DEPTH_FS)
        outline = GlUtil.program(Shaders.OUTLINE_VS, Shaders.OUTLINE_FS)
        spriteProg = GlUtil.program(Shaders.SPRITE_VS, Shaders.SPRITE_FS)
        for (n in listOf("uViewProj", "uLightVP", "uSunDir", "uSunColor", "uSkyColor", "uGroundColor", "uCamPos",
            "uFogColor", "uFog", "uShadowOn", "uTexel", "uNight", "uSat", "uShadowMap", "uInterior", "uInLit", "uInShade", "uInAmt")) {
            uToon[n] = GLES30.glGetUniformLocation(toon, n)
        }
        uDepthLightVP = GLES30.glGetUniformLocation(depth, "uLightVP")
        uOutViewProj = GLES30.glGetUniformLocation(outline, "uViewProj")
        uOutWidth = GLES30.glGetUniformLocation(outline, "uWidth")
        uOutColor = GLES30.glGetUniformLocation(outline, "uColor")
        uSprViewProj = GLES30.glGetUniformLocation(spriteProg, "uViewProj")
        uSprRight = GLES30.glGetUniformLocation(spriteProg, "uCamRight")
        uSprUp = GLES30.glGetUniformLocation(spriteProg, "uCamUp")
        uSprTex = GLES30.glGetUniformLocation(spriteProg, "uTex")
        instanceVbo = GlUtil.genBuffer()
        setupSprites()
        setupShadows()
        opaque.clear(); transparent.clear()
    }

    fun setupShadows() {
        if (shadowFbo != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(shadowFbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(shadowTex), 0)
            shadowFbo = 0; shadowTex = 0
        }
        if (dummyShadowTex == 0) {
            // 1x1 depth texture so the shadow sampler always has a valid, complete texture bound.
            dummyShadowTex = GlUtil.genTexture()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dummyShadowTex)
            val one = java.nio.ByteBuffer.allocateDirect(2).order(java.nio.ByteOrder.nativeOrder()); one.putShort(0xFFFF.toShort()).position(0)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT16, 1, 1, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_SHORT, one)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL)
        }
        shadowSize = quality.shadowSize
        if (!quality.shadows || shadowSize == 0) return
        shadowTex = GlUtil.genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT16, shadowSize, shadowSize, 0,
            GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_SHORT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL)
        val fb = IntArray(1); GLES30.glGenFramebuffers(1, fb, 0); shadowFbo = fb[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, shadowTex, 0)
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun setupSprites() {
        spriteVao = GlUtil.genVao()
        spriteQuadVbo = GlUtil.genBuffer()
        spriteInstVbo = GlUtil.genBuffer()
        GLES30.glBindVertexArray(spriteVao)
        val quad = GlUtil.floatBuffer(8)
        quad.put(floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, spriteQuadVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 32, quad, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, spriteInstVbo)
        val stride = SPRITE_FLOATS * 4
        val sizes = intArrayOf(3, 4, 4, 4, 1)
        var off = 0
        for (i in sizes.indices) {
            val loc = i + 1
            GLES30.glEnableVertexAttribArray(loc)
            GLES30.glVertexAttribPointer(loc, sizes[i], GLES30.GL_FLOAT, false, stride, off)
            GLES30.glVertexAttribDivisor(loc, 1)
            off += sizes[i] * 4
        }
        GLES30.glBindVertexArray(0)
    }

    private fun ensureVao(mesh: Mesh) {
        if (mesh.vao != 0) return
        mesh.vao = GlUtil.genVao()
        GLES30.glBindVertexArray(mesh.vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mesh.vbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 20, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_BYTE, true, 20, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_UNSIGNED_BYTE, true, 20, 16)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.ibo)
        for (loc in 3..8) {
            GLES30.glEnableVertexAttribArray(loc)
            GLES30.glVertexAttribDivisor(loc, 1)
        }
        GLES30.glBindVertexArray(0)
    }

    fun resize(w: Int, h: Int) {
        width = w; height = h
    }

    // ------------------------------------------------------------------ submission
    fun begin(cam: Camera) {
        camera = cam
        for (b in activeOpaque) b.count = 0
        for (b in activeTransparent) b.count = 0
        activeOpaque.clear(); activeTransparent.clear()
        spriteCount = 0
        drawCalls = 0; instances = 0
    }

    /**
     * Queue one instance of [mesh] with model matrix [m] (at offset [mo]).
     * tint = rgb applied to tint-masked vertices; alpha < 1 renders in the transparent pass.
     */
    fun draw(
        mesh: Mesh, m: FloatArray, mo: Int = 0,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, alpha: Float = 1f,
        flash: Float = 0f, emit: Float = 0f, darken: Float = 0f,
        shadow: Boolean = true, outline: Boolean = false,
    ) {
        val trans = alpha < 0.999f
        val map = if (trans) transparent else opaque
        var batch = map[mesh]
        if (batch == null) { batch = Batch(mesh); map[mesh] = batch }
        if (batch.count == 0) {
            batch.shadow = false; batch.outline = false
            if (trans) activeTransparent.add(batch) else activeOpaque.add(batch)
        }
        if (shadow) batch.shadow = true
        if (outline) batch.outline = true
        val o = batch.reserve()
        val d = batch.data
        System.arraycopy(m, mo, d, o, 16)
        d[o + 16] = r; d[o + 17] = g; d[o + 18] = b; d[o + 19] = alpha
        d[o + 20] = flash; d[o + 21] = emit; d[o + 22] = darken; d[o + 23] = 0f
    }

    /** Queue a sprite. mode: 0 = camera-facing billboard, 1 = flat on the ground. Colour is straight alpha. */
    fun sprite(
        x: Float, y: Float, z: Float, w: Float, h: Float, uv: FloatArray,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f,
        rot: Float = 0f, mode: Int = 0, fill: Float = 1f,
    ) {
        if (a <= 0.001f) return
        if ((spriteCount + 1) * SPRITE_FLOATS > spriteData.size) spriteData = spriteData.copyOf(spriteData.size * 2)
        val o = spriteCount++ * SPRITE_FLOATS
        val d = spriteData
        d[o] = x; d[o + 1] = y; d[o + 2] = z
        d[o + 3] = w; d[o + 4] = h; d[o + 5] = rot; d[o + 6] = mode.toFloat()
        d[o + 7] = uv[0]; d[o + 8] = uv[1]; d[o + 9] = uv[2]; d[o + 10] = uv[3]
        d[o + 11] = r * a; d[o + 12] = g * a; d[o + 13] = b * a; d[o + 14] = a
        d[o + 15] = fill
    }

    // ------------------------------------------------------------------ frame
    fun end(
        targetFbo: Int = 0, vx: Int = 0, vy: Int = 0, vw: Int = width, vh: Int = height,
        transparentClear: Boolean = false, useShadows: Boolean = true,
    ) {
        uploadInstances()
        val shadowsWanted = useShadows && quality.shadows && shadowFbo != 0
        if (shadowsWanted) shadowPass()
        shadowsThisFrame = shadowsWanted
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFbo)
        GLES30.glViewport(vx, vy, vw, vh)
        if (transparentClear) {
            GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
            GLES30.glScissor(vx, vy, vw, vh)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
        } else {
            val cc = lighting.clearColor
            GLES30.glClearColor(cc[0], cc[1], cc[2], 1f)
        }
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (transparentClear) GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glDisable(GLES30.GL_BLEND)
        mainPass(activeOpaque, frontToBack = true)
        if (quality.outlines) outlinePass()
        if (activeTransparent.isNotEmpty()) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glDepthMask(false)
            mainPass(activeTransparent)
            GLES30.glDepthMask(true)
        }
        spritePass()
        GLES30.glBindVertexArray(0)
    }

    private fun uploadInstances() {
        var total = 0
        for (b in activeOpaque) total += b.count
        for (b in activeTransparent) total += b.count
        instances = total
        val floats = total * INST_FLOATS
        if (instanceBuf.capacity() < floats) instanceBuf = GlUtil.floatBuffer(floats + INST_FLOATS * 512)
        instanceBuf.clear()
        var off = 0
        for (b in activeOpaque) { b.offsetBytes = off * 4; instanceBuf.put(b.data, 0, b.count * INST_FLOATS); off += b.count * INST_FLOATS }
        for (b in activeTransparent) { b.offsetBytes = off * 4; instanceBuf.put(b.data, 0, b.count * INST_FLOATS); off += b.count * INST_FLOATS }
        instanceBuf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, maxOf(4, floats * 4), if (floats > 0) instanceBuf else null, GLES30.GL_STREAM_DRAW)
    }

    private fun bindBatch(b: Batch) {
        ensureVao(b.mesh)
        GLES30.glBindVertexArray(b.mesh.vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        val stride = INST_FLOATS * 4
        for (i in 0 until 4) GLES30.glVertexAttribPointer(3 + i, 4, GLES30.GL_FLOAT, false, stride, b.offsetBytes + i * 16)
        GLES30.glVertexAttribPointer(7, 4, GLES30.GL_FLOAT, false, stride, b.offsetBytes + 64)
        GLES30.glVertexAttribPointer(8, 4, GLES30.GL_FLOAT, false, stride, b.offsetBytes + 80)
    }

    private fun drawBatch(b: Batch) {
        GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, b.mesh.indexCount, GLES30.GL_UNSIGNED_SHORT, 0, b.count)
        drawCalls++
    }

    private fun computeLight() {
        val cam = camera
        val s = lighting.sunDir
        val tx = cam.targetX; val ty = 0f; val tz = cam.targetZ - 2.5f
        val dist = 30f
        M4.lookAt(lightView, tx + s[0] * dist, ty + s[1] * dist, tz + s[2] * dist, tx, ty, tz)
        val half = cam.shadowExtent
        M4.ortho(lightProj, -half, half, -half, half, 1f, dist * 2f)
        android.opengl.Matrix.multiplyMM(lightVP, 0, lightProj, 0, lightView, 0)
    }

    private fun shadowPass() {
        computeLight()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glViewport(0, 0, shadowSize, shadowSize)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(1.6f, 2.5f)
        GLES30.glUseProgram(depth)
        GLES30.glUniformMatrix4fv(uDepthLightVP, 1, false, lightVP, 0)
        for (b in activeOpaque) {
            if (!b.shadow) continue
            bindBatch(b); drawBatch(b)
        }
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
    }

    private fun mainPass(list: List<Batch>, frontToBack: Boolean = false) {
        if (list.isEmpty()) return
        val L = lighting
        GLES30.glUseProgram(toon)
        GLES30.glUniformMatrix4fv(uToon["uViewProj"]!!, 1, false, camera.viewProj, 0)
        GLES30.glUniformMatrix4fv(uToon["uLightVP"]!!, 1, false, lightVP, 0)
        GLES30.glUniform3fv(uToon["uSunDir"]!!, 1, L.sunDir, 0)
        GLES30.glUniform3fv(uToon["uSunColor"]!!, 1, L.sunColor, 0)
        GLES30.glUniform3fv(uToon["uSkyColor"]!!, 1, L.skyColor, 0)
        GLES30.glUniform3fv(uToon["uGroundColor"]!!, 1, L.groundColor, 0)
        GLES30.glUniform3f(uToon["uCamPos"]!!, camera.eyeX, camera.eyeY, camera.eyeZ)
        GLES30.glUniform3fv(uToon["uFogColor"]!!, 1, L.fogColor, 0)
        GLES30.glUniform2f(uToon["uFog"]!!, L.fogStart, L.fogEnd)
        GLES30.glUniform1f(uToon["uNight"]!!, L.night)
        GLES30.glUniform1f(uToon["uSat"]!!, L.saturation)
        val shadowsOn = shadowsThisFrame
        GLES30.glUniform1f(uToon["uShadowOn"]!!, if (shadowsOn) 1f else 0f)
        GLES30.glUniform1f(uToon["uTexel"]!!, if (shadowSize > 0) 1f / shadowSize else 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (shadowsOn) shadowTex else dummyShadowTex)
        GLES30.glUniform1i(uToon["uShadowMap"]!!, 1)
        GLES30.glUniform4fv(uToon["uInterior"]!!, 1, L.interior, 0)
        GLES30.glUniform3fv(uToon["uInLit"]!!, 1, L.inLit, 0)
        GLES30.glUniform3fv(uToon["uInShade"]!!, 1, L.inShade, 0)
        GLES30.glUniform1f(uToon["uInAmt"]!!, L.interiorAmt)
        // Opaque batches arrive back-to-front (ground and floors first, characters last). Drawing them in reverse
        // lets early-Z reject most of the floor and ground fragments hidden under furniture and characters.
        if (frontToBack) for (i in list.indices.reversed()) { val b = list[i]; bindBatch(b); drawBatch(b) }
        else for (b in list) { bindBatch(b); drawBatch(b) }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    private fun outlinePass() {
        var any = false
        for (b in activeOpaque) if (b.outline) { any = true; break }
        if (!any) return
        GLES30.glUseProgram(outline)
        GLES30.glUniformMatrix4fv(uOutViewProj, 1, false, camera.viewProj, 0)
        GLES30.glUniform1f(uOutWidth, 0.018f)
        GLES30.glUniform3fv(uOutColor, 1, OUTLINE_COLOR, 0)
        GLES30.glCullFace(GLES30.GL_FRONT)
        for (b in activeOpaque) {
            if (!b.outline) continue
            bindBatch(b); drawBatch(b)
        }
        GLES30.glCullFace(GLES30.GL_BACK)
    }

    private fun spritePass() {
        if (spriteCount == 0 || spriteTexture == 0) return
        val floats = spriteCount * SPRITE_FLOATS
        if (spriteBuf.capacity() < floats) spriteBuf = GlUtil.floatBuffer(floats + SPRITE_FLOATS * 256)
        spriteBuf.clear(); spriteBuf.put(spriteData, 0, floats); spriteBuf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, spriteInstVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floats * 4, spriteBuf, GLES30.GL_STREAM_DRAW)
        GLES30.glUseProgram(spriteProg)
        GLES30.glUniformMatrix4fv(uSprViewProj, 1, false, camera.viewProj, 0)
        GLES30.glUniform3f(uSprRight, camera.rightX, camera.rightY, camera.rightZ)
        GLES30.glUniform3f(uSprUp, camera.upX, camera.upY, camera.upZ)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, spriteTexture)
        GLES30.glUniform1i(uSprTex, 0)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glBindVertexArray(spriteVao)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, spriteCount)
        drawCalls++
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }
}
