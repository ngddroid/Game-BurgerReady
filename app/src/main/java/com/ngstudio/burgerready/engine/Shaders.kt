package com.ngstudio.burgerready.engine

/** GLSL ES 3.00 sources for the Grill Engine toon pipeline. */
object Shaders {

    private const val INSTANCE_ATTRS = """
layout(location=0) in vec3 aPos;
layout(location=1) in vec4 aNormal;   // xyz normal, w = emission
layout(location=2) in vec4 aColor;    // rgb colour (AO baked), a = tint mask
layout(location=3) in vec4 iM0;
layout(location=4) in vec4 iM1;
layout(location=5) in vec4 iM2;
layout(location=6) in vec4 iM3;
layout(location=7) in vec4 iTint;     // rgb tint, a = alpha
layout(location=8) in vec4 iFx;       // x flash-to-white, y extra emission, z darken, w unused
"""

    val TOON_VS = """#version 300 es
$INSTANCE_ATTRS
uniform mat4 uViewProj;
uniform mat4 uLightVP;
out vec3 vNormal;
out vec3 vColor;
out vec4 vLightPos;
out vec3 vWorld;
out vec4 vFx;   // x flash, y emission, z alpha
void main() {
    mat4 M = mat4(iM0, iM1, iM2, iM3);
    vec4 w = M * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = mat3(M) * aNormal.xyz;
    vec3 base = aColor.rgb;
    vec3 tinted = mix(base, base * iTint.rgb, aColor.a);
    vColor = tinted * (1.0 - iFx.z);
    vFx = vec4(iFx.x, clamp(aNormal.w * 3.2 + iFx.y, 0.0, 1.0), iTint.a, 0.0);
    vLightPos = uLightVP * w;
    gl_Position = uViewProj * w;
}
"""

    // Colour maths runs at mediump (twice the ALU rate on Mali Valhall); positions and the shadow lookup stay highp.
    val TOON_FS = """#version 300 es
precision mediump float;
precision mediump sampler2DShadow;
in vec3 vNormal;
in vec3 vColor;
in highp vec4 vLightPos;
in highp vec3 vWorld;
in vec4 vFx;
uniform vec3 uSunDir;
uniform vec3 uSunColor;     // lit-side light (multiplier, ~1)
uniform vec3 uSkyColor;     // shadow-side light: a coloured multiplier, never grey
uniform vec3 uGroundColor;  // rim light colour
uniform highp vec3 uCamPos;
uniform vec3 uFogColor;
uniform highp vec2 uFog;
uniform float uShadowOn;
uniform highp float uTexel;
uniform float uNight;
uniform float uSat;
uniform highp vec4 uInterior; // x0, z0, x1, z1: the restaurant's floor rectangle
uniform vec3 uInLit;        // lamp light inside it (evening stores)
uniform vec3 uInShade;
uniform float uInAmt;       // 0 = daytime everywhere, 1 = fully lamp-lit interior
uniform sampler2DShadow uShadowMap;
out vec4 frag;

// One hardware-filtered (2x2 bilinear PCF) tap: crisp toon shadows at a quarter of the old 4-tap cost.
float shadowAt() {
    highp vec3 p = vLightPos.xyz / vLightPos.w * 0.5 + 0.5;
    if (p.x <= 0.001 || p.x >= 0.999 || p.y <= 0.001 || p.y >= 0.999 || p.z >= 1.0) return 1.0;
    return texture(uShadowMap, vec3(p.xy, p.z - 0.0018));
}

// Identity below 0.84, then eases into 1.0: bright paint never clips to flat white.
vec3 shoulder(vec3 c) {
    vec3 k = max(c - 0.84, 0.0);
    return min(c, 0.84 + 0.16 * (1.0 - exp(-k / 0.16)));
}

void main() {
    vec3 N = normalize(vNormal);
    float ndl = dot(N, uSunDir);
    float sh = uShadowOn > 0.5 ? shadowAt() : 1.0;
    // Crisp cel terminator; cast shadows use the same coloured shade.
    float lit = smoothstep(-0.02, 0.20, ndl) * sh;
    float band = smoothstep(0.62, 0.80, ndl) * sh;
    float form = 0.93 + 0.07 * N.y;                    // up-facing reads a touch brighter
    // Inside the restaurant walls, evening stores use warm lamp light; outside stays night-blue.
    highp vec2 dIn = min(vWorld.xz - uInterior.xy, uInterior.zw - vWorld.xz);
    float inside = smoothstep(-0.35, 0.45, min(dIn.x, dIn.y)) * uInAmt;
    vec3 sunC = mix(uSunColor, uInLit, inside);
    vec3 skyC = mix(uSkyColor, uInShade, inside);
    vec3 light = mix(skyC, sunC, lit) * form + sunC * band * 0.06;
    vec3 col = vColor * light;
    // Saturation: a little everywhere, more in shade, so shadows stay colourful instead of muddy.
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = max(mix(vec3(l), col, uSat + (1.0 - lit) * 0.12), 0.0);
    vec3 V = normalize(uCamPos - vWorld);
    float fres = 1.0 - max(dot(N, V), 0.0);
    col += fres * fres * fres * (0.05 + 0.08 * lit) * uGroundColor;
    // Emission (lamps, neon, screens) glows more at night.
    float e = vFx.y;
    col = mix(col, vColor * (1.2 + uNight * 0.6) + vec3(0.04), e);
    col = shoulder(col);
    col = mix(col, vec3(1.0, 0.98, 0.92), vFx.x);
    highp float d = length(vWorld - uCamPos);
    float fogT = smoothstep(uFog.x, uFog.y, d);
    col = mix(col, uFogColor, fogT);
    frag = vec4(col * vFx.z, vFx.z);
}
"""

    val DEPTH_VS = """#version 300 es
$INSTANCE_ATTRS
uniform mat4 uLightVP;
void main() {
    mat4 M = mat4(iM0, iM1, iM2, iM3);
    gl_Position = uLightVP * (M * vec4(aPos, 1.0));
}
"""

    const val DEPTH_FS = """#version 300 es
precision mediump float;
void main() { }
"""

    val OUTLINE_VS = """#version 300 es
$INSTANCE_ATTRS
uniform mat4 uViewProj;
uniform float uWidth;
out float vAlpha;
void main() {
    mat4 M = mat4(iM0, iM1, iM2, iM3);
    vec3 n = normalize(mat3(M) * aNormal.xyz);
    vec4 w = M * vec4(aPos, 1.0);
    float s = length(M[0].xyz);
    w.xyz += n * uWidth * s;
    vAlpha = iTint.a;
    gl_Position = uViewProj * w;
}
"""

    const val OUTLINE_FS = """#version 300 es
precision mediump float;
uniform vec3 uColor;
in float vAlpha;
out vec4 frag;
void main() { frag = vec4(uColor * vAlpha, vAlpha); }
"""

    // ---------------------------------------------------------------- sprites
    // Instance: center xyz, size wh, rotation, mode, fill, uv rect, colour.
    const val SPRITE_VS = """#version 300 es
layout(location=0) in vec2 aCorner;      // -0.5..0.5
layout(location=1) in vec3 iPos;
layout(location=2) in vec4 iSizeRotMode; // w, h, rotation, mode(0 billboard,1 ground,2 screen-pixels billboard)
layout(location=3) in vec4 iUv;          // u0 v0 u1 v1
layout(location=4) in vec4 iColor;       // premultiplied rgba
layout(location=5) in float iFill;       // radial fill 0..1 (>=1 = full)
uniform mat4 uViewProj;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
out vec2 vUv;
out vec2 vLocal;
out vec4 vColor;
out float vFill;
void main() {
    float c = cos(iSizeRotMode.z), s = sin(iSizeRotMode.z);
    vec2 p = vec2(aCorner.x * c - aCorner.y * s, aCorner.x * s + aCorner.y * c) * iSizeRotMode.xy;
    vec3 world;
    if (iSizeRotMode.w > 0.5) {
        world = iPos + vec3(p.x, 0.0, -p.y);
    } else {
        world = iPos + uCamRight * p.x + uCamUp * p.y;
    }
    vUv = mix(iUv.xy, iUv.zw, aCorner + 0.5);
    vLocal = aCorner;
    vColor = iColor;
    vFill = iFill;
    gl_Position = uViewProj * vec4(world, 1.0);
}
"""

    const val SPRITE_FS = """#version 300 es
precision mediump float;
in vec2 vUv;
in vec2 vLocal;
in vec4 vColor;
in float vFill;
uniform sampler2D uTex;
out vec4 frag;
void main() {
    if (vFill < 0.999) {
        float a = atan(vLocal.x, vLocal.y);        // 0 at top, clockwise positive
        float t = (a < 0.0 ? a + 6.2831853 : a) / 6.2831853;
        if (t > vFill) discard;
    }
    vec4 t = texture(uTex, vUv);
    frag = t * vColor;
}
"""
}
