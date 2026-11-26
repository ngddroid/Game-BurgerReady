"""
hats.py - Bun Voyage! hats (docs/ASSET_CONTRACT.md section 2).

Each hat is one part named `hat` whose pivot (origin) is the bottom centre of the
hat. The engine places that pivot on a character's `@hat` anchor, i.e. on the
0.36 m seating ring of the shared chibi skull (see characters.HEAD / HAT_Z), so
every hat is modelled around the same skull dome and fits all ten species.

Outputs
    app/src/main/assets/models/hat_<name>.bvm
    tools/previews/characters/hat_<name>.png
    tools/previews/characters/hats_on_heads.png   (fit check: one hat per species)

Run
    ~/Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup \
        --python tools/blender/hats.py -- [name ...] [--no-preview]
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpy
from mathutils import Vector as V

import bvlib as B
import characters as C

HATS = ["toque", "cap", "bandana", "crown", "party", "helmet", "beanie", "cowboy", "flower", "beret"]
BUDGET = 600
TAU = 2 * math.pi

# Skull dome measured from the hat pivot (z = 0 is the seating ring plane).
SKULL = C.Ell((0.0, 0.0, C.HEAD.c.z - C.HAT_Z), tuple(C.HEAD.r))


def skull_r(h):
    """Largest skull radius (x) at height h above the seating plane."""
    q = 1.0 - ((h - SKULL.c.z) / SKULL.r.z) ** 2
    return SKULL.r.x * math.sqrt(max(q, 0.0))


YS = SKULL.r.y / SKULL.r.x      # skull is slightly narrower front-to-back


def lathe(P, prof, seg=20, ys=1.0, rmod=None, closed=False, tilt=None):
    """Surface of revolution around +Z. prof = [(r, z, material), ...]; the material
    colours the band to the next ring; r == 0 makes a pole. Traverse the profile
    bottom -> outside -> top so normals face out (inner walls go downwards)."""
    rings = []
    for i, (r, z, _) in enumerate(prof):
        if r <= 1e-7:
            rings.append([V((0, 0, z))])
            continue
        ring = []
        for j in range(seg):
            th = TAU * j / seg
            k = rmod(th, i) if rmod else 1.0
            ring.append(V((r * k * math.cos(th), r * k * math.sin(th) * ys, z)))
        rings.append(ring)
    mats = [m for _, _, m in prof]
    if closed:
        rings.append([V(p) for p in rings[0]])
    else:
        mats = mats[:-1]
    if tilt is not None:
        rings = [[tilt @ p for p in ring] for ring in rings]
    return P.rings(rings, mats)


def loop_tube(P, path_fn, n, seg, radius_fn, m_fn, hint=(0, 0, 1)):
    """Closed tube along a closed curve path_fn(u), u in [0, 1)."""
    bm = P.bm
    pts = [V(path_fn(i / n)) for i in range(n)]
    rings = []
    for i in range(n):
        t = (pts[(i + 1) % n] - pts[i - 1]).normalized()
        a = V(hint) - t * V(hint).dot(t)
        a.normalize()
        b = t.cross(a)
        r = radius_fn(i / n)
        rings.append([bm.verts.new(pts[i] + a * (r * math.cos(TAU * j / seg)) + b * (r * math.sin(TAU * j / seg)))
                      for j in range(seg)])
    faces = []
    for i in range(n):
        R0, R1 = rings[i], rings[(i + 1) % n]
        for j in range(seg):
            k = (j + 1) % seg
            P._face((R0[j], R0[k], R1[k], R1[j]), m_fn(i / n), faces)
    return faces


def tint_mat():
    return B.mat("white", tint=True, rough=0.7)


# ---------------------------------------------------------------------------
# Hats
# ---------------------------------------------------------------------------
def h_toque(P):
    band = B.mat("#F4EEE6", rough=0.7)
    puff = B.mat("white", rough=0.65)
    prof = [(0.000, 0.000, band), (0.186, 0.000, band), (0.197, 0.014, band), (0.199, 0.070, band),
            (0.192, 0.090, puff), (0.204, 0.104, puff), (0.236, 0.140, puff), (0.248, 0.200, puff),
            (0.236, 0.262, puff), (0.196, 0.306, puff), (0.115, 0.334, puff), (0.000, 0.344, puff)]

    def pleats(th, i):
        if i < 5:
            return 1.0
        w = {5: 0.4, 6: 0.85, 7: 1.0, 8: 1.0, 9: 0.8, 10: 0.45}.get(i, 0.0)
        return 1.0 + 0.055 * w * math.cos(6 * th)
    lathe(P, prof, seg=24, rmod=pleats)


def h_cap(P):
    crown = tint_mat()
    visor = B.mat("#F4EEE6", rough=0.6)
    button = B.mat("#F4EEE6", rough=0.6)
    prof = [(0.000, -0.004, crown), (0.190, -0.004, crown), (0.196, 0.012, crown), (0.192, 0.060, crown),
            (0.170, 0.105, crown), (0.118, 0.138, crown), (0.000, 0.150, crown)]
    lathe(P, prof, seg=20, ys=0.95)
    # visor: flattened ellipsoid tucked under the crown front, tipped slightly down
    P.ellipsoid((0.0, -0.200, 0.010), (0.150, 0.125, 0.013), visor, uv=(16, 6), rot=(-8, 0, 0))
    P.ellipsoid((0.0, 0.0, 0.150), (0.024, 0.024, 0.012), button, uv=(8, 4))


def h_bandana(P):
    cloth = tint_mat()
    dot = B.mat("white", rough=0.7)
    g = 0.013
    hs = [-0.016, 0.0, 0.022, 0.042, 0.058, 0.070]
    prof = [(0.0, hs[0] - 0.002, cloth)]
    for h in hs:
        prof.append((skull_r(h) + g, h, cloth))
    prof.append((0.0, SKULL.c.z + SKULL.r.z + g, cloth))
    lathe(P, prof, seg=20, ys=YS)
    shell = C.Ell(SKULL.c, SKULL.r + V((g, g, g)))
    for az, el in ((0, 62), (60, 40), (-60, 40), (130, 45), (-130, 45), (180, 70), (0, 82)):
        d = V((math.sin(math.radians(az)) * math.cos(math.radians(el)),
               -math.cos(math.radians(az)) * math.cos(math.radians(el)), math.sin(math.radians(el))))
        sp = shell.surf(d)
        if sp.z < 0.004:
            continue
        C.decal(P, shell, 0, 0, 0.020, 0.020, dot, h=0.002, seg=8, rho=(0.88, 1.0), center=sp)
    # knot + two tails at the back
    knot = V((0.0, skull_r(0.0) * YS + 0.012, 0.004))
    P.ellipsoid(knot, (0.034, 0.026, 0.030), cloth, n=2)
    for s in (1, -1):
        ctrl = [knot + V((0.0, -0.01, 0.0)), knot + V((0.03 * s, 0.025, -0.03)), knot + V((0.055 * s, 0.04, -0.085))]
        P.tube(ctrl, [(0.0, 0.0, cloth), (0.12, 0.022, cloth), (0.6, 0.026, cloth), (0.92, 0.020, cloth),
                      (1.0, 0.0, cloth)], seg=6, hint=(0, 1, 0), scale_keys=[(0.0, 0.45, 1.0), (1.0, 0.4, 1.0)])


def h_crown(P):
    gold = B.mat("gold", rough=0.28, metal=0.9)
    dark = B.mat("gold_dark", rough=0.3, metal=0.9)
    gem = B.mat("tomato", rough=0.15)
    gem2 = B.mat("teal", rough=0.15)
    r0, r1, top = 0.172, 0.192, 0.070
    # thick band (closed rounded-rectangle profile), darker rim lines
    prof = [(r0, 0.000, dark), (r1, -0.002, gold), (r1 + 0.006, 0.014, gold), (r1 + 0.006, top - 0.012, dark),
            (r1, top, gold), (r0, top, gold)]
    lathe(P, prof, seg=18, closed=True)
    n = 5
    for i in range(n):
        a = -math.pi / 2 + TAU * i / n          # first point straight above the front gem
        rad = (r0 + r1) / 2 + 0.002
        base = V((rad * math.cos(a), rad * math.sin(a), top - 0.012))
        out = V((math.cos(a), math.sin(a), 0))
        pts = [base, base + V((0, 0, 0.03)), base + V((0, 0, 0.062)) + out * 0.004, base + V((0, 0, 0.082)) + out * 0.006]
        P.sweep(pts, [0.026, 0.020, 0.008, 0.0], gold, seg=6, scales=[(0.45, 1.0)] * 4, hint=out)
        P.ellipsoid(pts[-1] + V((0, 0, 0.012)), (0.014, 0.014, 0.014), gold, uv=(6, 3))
    P.ellipsoid((0.0, -(r1 + 0.010), top * 0.5), (0.022, 0.012, 0.024), gem, n=2)
    for s in (1, -1):
        a = -math.pi / 2 + s * 0.95
        P.ellipsoid(((r1 + 0.008) * math.cos(a), (r1 + 0.008) * math.sin(a), top * 0.5), (0.013, 0.013, 0.013),
                    gem2, uv=(6, 3))


def h_party(P):
    cone = tint_mat()
    frill = B.mat("white", rough=0.7)
    pom = B.mat("#FFF3DC", rough=0.8)
    prof = [(0.000, 0.000, frill), (0.150, 0.000, frill), (0.176, 0.016, frill), (0.168, 0.034, cone),
            (0.142, 0.040, cone), (0.098, 0.140, cone), (0.054, 0.240, cone), (0.018, 0.315, cone),
            (0.000, 0.330, cone)]

    def ruffle(th, i):
        return 1.0 + (0.09 * math.cos(10 * th) if i in (2, 3) else 0.0)
    lathe(P, prof, seg=20, rmod=ruffle)
    P.ellipsoid((0.0, 0.0, 0.345), (0.050, 0.050, 0.046), pom, n=3)


def h_helmet(P):
    rim = B.mat("white", rough=0.5)
    glass = B.mat("glass", rough=0.05)
    shine = B.mat("white", rough=0.2, emit=0.4)
    ant = B.mat("#F4EEE6", rough=0.5)
    bulb = B.mat("tomato", rough=0.3, emit=0.6)
    prof = [(0.000, -0.004, rim), (0.192, -0.004, rim), (0.214, 0.008, rim), (0.218, 0.030, rim),
            (0.206, 0.044, glass), (0.214, 0.070, glass), (0.214, 0.120, glass), (0.188, 0.190, glass),
            (0.128, 0.240, glass), (0.000, 0.262, glass)]
    lathe(P, prof, seg=20)
    dome = C.Ell((0, 0, 0.07), (0.214, 0.214, 0.192))
    for (az, el, a, b) in ((-35, 38, 0.020, 0.060), (-58, 30, 0.010, 0.026)):
        d = V((math.sin(math.radians(az)) * math.cos(math.radians(el)),
               -math.cos(math.radians(az)) * math.cos(math.radians(el)), math.sin(math.radians(el))))
        C.decal(P, dome, 0, 0, a, b, shine, h=0.003, seg=8, rho=(0.88, 1.0), center=dome.surf(d), roll=-25)
    # little antenna on the rim
    base = V((0.150, 0.110, 0.030))
    P.sweep([base, base + V((0.02, 0.012, 0.09)), base + V((0.03, 0.018, 0.13))], [0.007, 0.006, 0.0], ant, seg=6)
    P.ellipsoid(base + V((0.031, 0.018, 0.137)), (0.017, 0.017, 0.017), bulb, uv=(8, 5))


def h_beanie(P):
    knit = tint_mat()
    pom = B.mat("#FFF3DC", rough=0.85)
    g = 0.016
    prof = [(0.000, -0.012, knit), (0.188, -0.012, knit), (0.206, -0.004, knit), (0.210, 0.028, knit),
            (0.206, 0.058, knit), (0.190, 0.064, knit), (0.196, 0.092, knit),
            (0.150, 0.128, knit), (0.112, 0.160, knit), (0.060, 0.180, knit), (0.000, 0.186, knit)]

    def ribs(th, i):
        return 1.0 + (0.018 * math.cos(12 * th) if 1 <= i <= 4 else 0.0)
    lathe(P, prof, seg=24, ys=0.97, rmod=ribs)
    P.ellipsoid((0.0, 0.0, 0.205), (0.056, 0.056, 0.052), pom, n=3)


def h_cowboy(P):
    felt = B.mat("brown", rough=0.75)
    band = B.mat("#3B2A3A", rough=0.6)
    # crown with a dented top
    prof = [(0.000, 0.000, felt), (0.176, 0.000, felt), (0.180, 0.010, band), (0.176, 0.042, felt),
            (0.166, 0.120, felt), (0.150, 0.158, felt), (0.110, 0.166, felt), (0.050, 0.148, felt),
            (0.000, 0.140, felt)]
    # the crown top is pinched front-to-back
    lathe(P, prof, seg=20, rmod=lambda th, i: 1.0 - (0.08 * math.sin(th) ** 2 if i >= 4 else 0.0))
    # brim: closed loop of rings; sides curl up, front/back dip a little
    seg = 24

    def brim_ring(r, dz):
        ring = []
        for j in range(seg):
            th = TAU * j / seg
            cx, sy = math.cos(th), math.sin(th)
            u = max(0.0, (r - 0.20) / 0.13)
            z = 0.012 + dz + (0.075 * cx * cx - 0.012 * sy * sy) * u * u
            ring.append(V((r * cx, r * sy * 0.92, z)))
        return ring
    # underside outwards, round the rim, top side back in (keeps normals facing out)
    rings = [brim_ring(0.170, -0.010), brim_ring(0.255, -0.010), brim_ring(0.315, -0.010), brim_ring(0.334, -0.002),
             brim_ring(0.315, 0.006), brim_ring(0.255, 0.008), brim_ring(0.170, 0.008)]
    P.rings(rings, felt)


def h_flower(P):
    vine = B.mat("lettuce_dark", rough=0.7)
    leaf = B.mat("lettuce", rough=0.6)
    petals = [B.mat("pink", rough=0.6), B.mat("white", rough=0.6), B.mat("lilac", rough=0.6),
              B.mat("#FF9A3C", rough=0.6), B.mat("pink", rough=0.6), B.mat("white", rough=0.6)]
    centre = B.mat("mustard", rough=0.6)
    R = 0.192

    def path(u):
        a = TAU * u
        return (R * math.cos(a), R * math.sin(a) * 0.95, 0.008 + 0.005 * math.sin(3 * a))
    loop_tube(P, path, 14, 5, lambda u: 0.018, lambda u: vine)
    n = 6
    for i in range(n):
        a = -math.pi / 2 + (i - (n - 1) / 2) * 0.62     # flowers across the front half
        p = V(path(a / TAU % 1.0))
        out = V((math.cos(a), math.sin(a) * 0.95, 0.0)).normalized()
        nrm = (out * 0.75 + V((0, 0, 0.66))).normalized()
        Rm = C.frame(nrm)
        X, Y, N = Rm.col[0], Rm.col[1], Rm.col[2]
        rr = 0.050 if i % 2 == 0 else 0.042
        c = p + out * 0.010 + V((0, 0, 0.012))
        k = 15
        rings = [[c - N * 0.010]]
        for (rf, dz) in ((1.0, 0.0), (0.38, 0.012)):
            ring = []
            for j in range(k):
                th = TAU * j / k
                lobe = 0.72 + 0.28 * abs(math.cos(2.5 * th)) if rf == 1.0 else 1.0
                ring.append(c + X * (rr * rf * lobe * math.cos(th)) + Y * (rr * rf * lobe * math.sin(th)) + N * dz)
            rings.append(ring)
        rings.append([c + N * 0.020])
        P.rings(rings, [petals[i], petals[i], centre])
    for a in (math.pi / 2 - 0.5, math.pi / 2 + 0.5, math.pi / 2 + 1.5, math.pi / 2 - 1.5):
        p = V(path(a / TAU % 1.0))
        out = V((math.cos(a), math.sin(a), 0.0))
        P.ellipsoid(p + out * 0.012 + V((0, 0, 0.006)), (0.040, 0.020, 0.007), leaf, uv=(6, 3),
                    rot=C.frame((out * 0.6 + V((0, 0, 0.8))).normalized()) @ C.rotz(90))


def h_beret(P):
    felt = tint_mat()
    prof = [(0.000, 0.000, felt), (0.186, 0.000, felt), (0.193, 0.012, felt), (0.190, 0.030, felt),
            (0.226, 0.054, felt), (0.246, 0.076, felt), (0.234, 0.098, felt), (0.180, 0.112, felt),
            (0.090, 0.118, felt), (0.000, 0.120, felt)]
    tilt = C.roty(-9) @ C.rotx(4)
    lathe(P, prof, seg=22, ys=0.97, tilt=tilt)
    top = tilt @ V((0, 0, 0.118))
    P.sweep([top - V((0, 0, 0.01)), top + V((0, 0, 0.004)), top + V((0.004, 0, 0.026)), top + V((0.005, 0, 0.032))],
            [0.0, 0.010, 0.008, 0.0], felt, seg=6)


BUILDERS = {"toque": h_toque, "cap": h_cap, "bandana": h_bandana, "crown": h_crown, "party": h_party,
            "helmet": h_helmet, "beanie": h_beanie, "cowboy": h_cowboy, "flower": h_flower, "beret": h_beret}
PREVIEW_TINT = {"cap": "tomato", "bandana": "red", "party": "teal", "beanie": "mint", "beret": "navy"}


def build_hat(name):
    P = C.Part("hat")
    BUILDERS[name](P)
    return P.finish((0.0, 0.0, 0.0))


def dummy_head():
    """Preview-only grey skull under the hat (same placement as on a character)."""
    P = C.Part("dummy_head")
    P.ellipsoid(SKULL.c, SKULL.r, B.mat("#B9B4AE", rough=0.8), n=6)
    return P.finish((0, 0, 0))


def export_hat(name, preview=True):
    B.reset_scene()
    hat = build_hat(name)
    path = os.path.join(B.ASSET_MODELS, "hat_%s.bvm" % name)
    with C.smoothed_ao():
        tris = B.export_bvm(path, [hat], ao=True, ao_strength=0.6, ao_distance=0.15, ao_samples=48)
    if preview:
        C.vc_preview([hat], tint=PREVIEW_TINT.get(name), ao_strength=0.6)
        dummy_head()
        C.render_view(os.path.join(C.PREVIEW_CHAR_DIR, "hat_%s.png" % name), target=(0, 0, 0.06), dist=1.75,
                      lens=85, res=(512, 512), samples=48, cam_dir=(0.62, -1.0, 0.55), ground_rgb=(0.62, 0.58, 0.55))
    return tris


def hats_on_heads(path=None):
    """Fit check: each species wears one hat (hat pivot on its @hat anchor)."""
    B.reset_scene()
    pairs = list(zip(C.SPECIES_ORDER, HATS))
    allp = []
    n = len(pairs)
    tints = []
    for i, (sp, hn) in enumerate(pairs):
        parts, _ = C.build_character(sp)
        hat = build_hat(hn)
        x = (i - (n - 1) / 2) * 0.74
        parts[0].location.x += x
        hat.location = (x, 0.0, C.HAT_Z)
        allp += parts + [hat]
        tints.append((sp, parts, hn, hat))
    bpy.context.view_layer.update()
    C.bake_ao_smooth(allp, samples=32, distance=C.AO_DIST)
    for sp, parts, hn, hat in tints:
        C.vc_preview(parts, tint=C.PREVIEW_TINT[sp], ao_strength=C.AO_STRENGTH)
        C.vc_preview([hat], tint=PREVIEW_TINT.get(hn), ao_strength=C.AO_STRENGTH)
    C.render_view(path or os.path.join(C.PREVIEW_CHAR_DIR, "hats_on_heads.png"), target=(0, 0, 0.72),
                  dist=11.6, lens=50, res=(2400, 760), samples=64, cam_dir=(0.0, -1.0, 0.30), rig="sun")


def main():
    args = B.script_args()
    flags = {a for a in args if a.startswith("--")}
    names = [a for a in args if not a.startswith("--")] or HATS
    report = []
    for n in names:
        report.append((n, export_hat(n, preview="--no-preview" not in flags)))
    if not [a for a in args if not a.startswith("--")] and "--no-preview" not in flags:
        hats_on_heads()
    for n, t in report:
        print("HAT %-8s tris=%d %s" % (n, t, "OK" if t <= BUDGET else "OVER BUDGET"))


if __name__ == "__main__":
    main()
