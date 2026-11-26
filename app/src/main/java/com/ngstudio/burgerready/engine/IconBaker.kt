package com.ngstudio.burgerready.engine

import android.opengl.GLES30

/**
 * Renders the food/item models into their reserved cells of the sprite atlas once at start-up,
 * so order bubbles and HUD icons always match the in-game 3D models exactly.
 */
object IconBaker {
    fun bake(renderer: Renderer, atlas: SpriteAtlas, models: ModelCache) {
        val fb = IntArray(1); GLES30.glGenFramebuffers(1, fb, 0)
        val rb = IntArray(1); GLES30.glGenRenderbuffers(1, rb, 0)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rb[0])
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, SpriteAtlas.SIZE, SpriteAtlas.SIZE)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fb[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, atlas.texture, 0)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, rb[0])
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        android.util.Log.i("IconBaker", "fbo status=0x" + Integer.toHexString(status) + " icons=" + atlas.iconSlots.size)
        if (status == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            val cam = Camera().apply { aspect = 1f; fovY = 24f; pitchDeg = 32f; yawDeg = 28f; near = 0.05f; far = 20f }
            val m = FloatArray(16)
            val saveSun = renderer.lighting.sunDir.copyOf()
            renderer.lighting.sunDir[0] = -0.35f; renderer.lighting.sunDir[1] = 0.85f; renderer.lighting.sunDir[2] = 0.55f
            renderer.lighting.normalizeSun()
            val saveFog = renderer.lighting.fogEnd
            val L = renderer.lighting
            val saveSunC = L.sunColor.copyOf(); val saveSky = L.skyColor.copyOf(); val saveGround = L.groundColor.copyOf()
            L.sunColor[0] = 0.85f; L.sunColor[1] = 0.8f; L.sunColor[2] = 0.72f
            L.skyColor[0] = 0.55f; L.skyColor[1] = 0.55f; L.skyColor[2] = 0.6f
            L.groundColor[0] = 0.35f; L.groundColor[1] = 0.3f; L.groundColor[2] = 0.3f
            renderer.lighting.fogStart = 500f; renderer.lighting.fogEnd = 600f
            for ((name, slot) in atlas.iconSlots) {
                val model = models.getOrNull("food_$name") ?: continue
                val h = maxOf(model.height, 0.1f)
                val r = maxOf(model.radius, h * 0.5f)
                cam.targetX = 0f; cam.targetZ = 0f; cam.targetY = h * 0.45f
                cam.distance = maxOf(r, h * 0.62f) * 4.7f
                cam.rebuild()
                renderer.begin(cam)
                M4.trs(m, 0, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
                for (p in model.parts) {
                    val mesh = p.mesh ?: continue
                    M4.trs(m, 0, p.pivotX, p.pivotY, p.pivotZ, 0f, 1f, 1f, 1f)
                    renderer.draw(mesh, m, 0, outline = true, shadow = false)
                }
                renderer.end(fb[0], slot[0], slot[1], slot[2], slot[3], transparentClear = true, useShadows = false)
            }
            System.arraycopy(saveSun, 0, renderer.lighting.sunDir, 0, 3)
            System.arraycopy(saveSunC, 0, L.sunColor, 0, 3); System.arraycopy(saveSky, 0, L.skyColor, 0, 3); System.arraycopy(saveGround, 0, L.groundColor, 0, 3)
            renderer.lighting.fogEnd = saveFog; renderer.lighting.fogStart = saveFog * 0.45f
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteFramebuffers(1, fb, 0)
        GLES30.glDeleteRenderbuffers(1, rb, 0)
        atlas.regenerateMipmaps()
    }
}
