"""
Environment props (ASSET_CONTRACT §6, second half): trees, plants, lamps,
bench, signs, umbrella, vehicles, cloud. One part `root` (car/van add a
`wheels` child holding all 4 wheels), pivot at the bottom centre, front = -Y.

    Blender -b --factory-startup --python tools/blender/env.py [-- car tree_round ...]
"""
import os
import sys
import math
import random

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from kit import M, T, E, B, lathe, prism, tube, rbox, rcyl, ball, leaf, join, move  # noqa: E402
from mathutils import Vector  # noqa: E402

TAU = 2 * math.pi


def root(objs):
    return [join(objs, "root", (0, 0, 0))]


# ---------------------------------------------------------------------------
# Trees & plants
# ---------------------------------------------------------------------------
def trunk(h=1.3, r0=0.13, r1=0.065, col="brown", seg=8):
    return lathe([(r0, 0.0), (r0 * 0.78, 0.07), (r1 * 1.15, 0.35), (r1, h)], seg, [M(col)], name="trunk")


def build_tree_round():
    o = [trunk(1.25, r0=0.14, r1=0.075)]
    o.append(tube([(0.0, 0.0, 0.85), (0.20, -0.07, 1.12)], 0.045, M("brown"), seg=6))
    o.append(ball(0.56, loc=(0.02, 0.02, 1.70), m=M("grass"), seg=12, rings=8, jitter=0.035, seed=2))
    o.append(ball(0.38, loc=(0.36, -0.16, 1.38), m=M("lettuce"), seg=11, rings=7, jitter=0.04, seed=5))
    o.append(ball(0.34, loc=(-0.34, 0.14, 1.40), m=M("lettuce"), seg=10, rings=7, jitter=0.04, seed=9))
    o.append(ball(0.30, loc=(-0.08, -0.08, 2.10), m=M("#A2E070"), seg=10, rings=6, jitter=0.04, seed=4))
    return root(o)


def pine_tier(z, R, h, snow=False, phase=0.0):
    def wave(i, t, p):
        if i <= 1:
            k = 1 + 0.07 * math.sin(6 * t + phase)
            return (p[0] * k, p[1] * k, p[2] - (0.03 * (1 + math.sin(6 * t + phase)) if i == 1 else 0))
        if i == 2 and snow:
            return (p[0], p[1], p[2] - 0.045 * max(0.0, math.sin(5 * t + phase)))
        return p
    if snow:
        prof = [(R * 0.86, z), (R, z + 0.06), (R * 0.80, z + h * 0.16), (R * 0.45, z + h * 0.56), (0.0, z + h)]
        band = [0, 0, 1, 1]
    else:
        prof = [(R * 0.86, z), (R, z + 0.06), (R * 0.52, z + h * 0.52), (0.0, z + h)]
        band = [0, 0, 0]
    mats = [M("#2E8B57"), M("white")]
    return lathe(prof, 10, mats, band_mat=band, cap_bot=True, deform=wave, name="tier")


def build_tree_pine(snow=False):
    o = [lathe([(0.11, 0.0), (0.085, 0.06), (0.075, 0.55)], 8, [M("brown")], name="trunk")]
    for i, (z, R, h) in enumerate(((0.40, 0.50, 0.95), (0.98, 0.41, 0.85), (1.52, 0.31, 1.05))):
        o.append(pine_tier(z, R, h, snow=snow, phase=i * 1.3))
    return root(o)


def build_palm():
    o = []
    # curved, ringed trunk
    n = 13
    pts, radii = [], []
    for i in range(n):
        t = i / (n - 1)
        pts.append((0.16 * t ** 2, 0.04 * math.sin(t * 2.5), 0.015 + 2.45 * t))
        radii.append((0.13 - 0.05 * t) * (1.0 if i % 2 == 0 else 0.86))
    o.append(tube(pts, 0.1, M("wood"), seg=8, radii=radii, mats=[M("wood"), M("#B97A47")],
                  seg_mat=lambda i, j: i % 2, angle=60))
    top = Vector(pts[-1])
    # fronds
    rnd = random.Random(3)
    for k in range(7):
        a = TAU * k / 7 + 0.3
        d = Vector((math.cos(a), math.sin(a), 0))
        L = rnd.uniform(0.62, 0.70)
        lift = rnd.uniform(0.15, 0.3)
        spine = []
        for s in range(6):
            u = s / 5
            p = top + d * (L * u) + Vector((0, 0, lift * math.sin(u * math.pi * 0.9) - 0.42 * u ** 2.2))
            spine.append(tuple(p))
        widths = [0.03, 0.14, 0.16, 0.13, 0.07, 0.0]
        o.append(leaf(spine, widths, M("lettuce") if k % 2 else M("#4FAE3A"), thick=0.03))
    for k in range(3):
        a = TAU * k / 3 + 0.8
        o.append(ball(0.075, loc=(top.x + 0.10 * math.cos(a), top.y + 0.10 * math.sin(a), top.z - 0.10),
                      m=M("brown"), seg=8, rings=5))
    o.append(ball(0.11, loc=tuple(top + Vector((0, 0, 0.02))), m=M("#4FAE3A"), seg=8, rings=5))
    return root(o)


def build_bush():
    o = [ball(0.27, loc=(0.0, 0.03, 0.25), m=M("lettuce"), seg=9, rings=6, jitter=0.05, seed=1, flat_bottom=-0.23),
         ball(0.19, loc=(0.18, -0.07, 0.18), m=M("grass"), seg=8, rings=6, jitter=0.05, seed=2, flat_bottom=-0.16),
         ball(0.18, loc=(-0.19, -0.05, 0.17), m=M("grass"), seg=8, rings=6, jitter=0.05, seed=3, flat_bottom=-0.15),
         ball(0.15, loc=(0.04, -0.20, 0.14), m=M("#A2E070"), seg=8, rings=5, jitter=0.05, seed=4, flat_bottom=-0.12)]
    return root(o)


def build_flower_bed():
    o = [rbox((1.0, 0.5, 0.30), z0=0.0, m=M("wood"), r=0.04, segs=2)]
    o.append(rbox((1.02, 0.52, 0.05), z0=0.12, m=M("wood_dark"), r=0.02, segs=1))
    o.append(rbox((0.92, 0.42, 0.02), z0=0.29, m=M("patty_dark"), r=0.0, segs=1))
    for (x, y, r, sd) in ((-0.28, 0.04, 0.17, 1), (0.02, 0.06, 0.18, 2), (0.30, 0.02, 0.16, 3)):
        o.append(ball(r, loc=(x, y, 0.33), m=M("lettuce"), seg=8, rings=5, jitter=0.06, seed=sd,
                      scale=(1.0, 0.9, 0.75)))
    cols = ["pink", "mustard", "tomato", "lilac", "white", "pink", "mustard", "lilac"]
    spots = [(-0.40, -0.08, 0.43), (-0.20, -0.12, 0.47), (-0.30, 0.12, 0.50), (-0.05, -0.10, 0.49),
             (0.10, 0.12, 0.52), (0.18, -0.12, 0.46), (0.38, -0.06, 0.45), (0.32, 0.12, 0.48)]
    for c, (x, y, z) in zip(cols, spots):
        o.append(ball(0.05, loc=(x, y, z), m=M(c), seg=6, rings=3, scale=(1, 1, 0.75)))
    return root(o)


def build_plant_pot():
    o = [lathe([(0.13, 0.0), (0.15, 0.015), (0.18, 0.30), (0.20, 0.31), (0.20, 0.36), (0.185, 0.37),
                (0.17, 0.345)], 12, [M("teal"), M("cream")], band_mat=[0, 0, 1, 1, 1, 1], cap_bot=True,
               name="pot")]
    o.append(rcyl(0.17, 0.02, loc=(0, 0, 0), z0=0.33, m=M("patty_dark"), verts=12, bevel=0))
    rnd = random.Random(7)
    for k in range(6):
        a = TAU * k / 6 + rnd.uniform(-0.2, 0.2)
        tilt = rnd.uniform(14, 26)
        L = rnd.uniform(0.42, 0.52)
        d = Vector((math.cos(a), math.sin(a), 0))
        base = Vector((0, 0, 0.34))
        tip = base + d * (L * math.sin(math.radians(tilt))) + Vector((0, 0, L * math.cos(math.radians(tilt))))
        o.append(tube([tuple(base), tuple(base + (tip - base) * 0.5)], 0.012, M("lettuce_dark"), seg=4))
        mid = base + (tip - base) * 0.68
        lf = ball(1.0, loc=(0, 0, 0), m=M("lettuce") if k % 2 else M("#4FAE3A"), seg=8, rings=4,
                  scale=(0.085, 0.04, 0.21))
        q = (tip - base).normalized().to_track_quat('Z', 'X')
        from mathutils import Matrix
        lf.data.transform(Matrix.Translation(mid) @ q.to_matrix().to_4x4() @ Matrix.Rotation(a, 4, 'Z'))
        lf.data.update()
        o.append(lf)
    return root(o)


# ---------------------------------------------------------------------------
# Lamps, bench, signs, umbrella
# ---------------------------------------------------------------------------
def build_lamp_street():
    o = [lathe([(0.17, 0.0), (0.17, 0.07), (0.13, 0.11), (0.085, 0.28), (0.055, 0.34)], 8, [M("navy")],
               cap_bot=True, name="base")]
    o.append(rcyl(0.045, 2.24, loc=(0, 0, 0), z0=0.32, m=M("navy"), verts=8, bevel=0))
    o.append(rcyl(0.07, 0.06, loc=(0, 0, 0), z0=1.40, m=M("mustard"), verts=10, bevel=0.015))
    o.append(rcyl(0.08, 0.08, loc=(0, 0, 0), z0=2.50, m=M("navy"), verts=10, bevel=0.02))
    o.append(lathe([(0.06, 2.56), (0.13, 2.60), (0.12, 2.62)], 8, [M("navy")], cap_bot=True, name="cup"))
    o.append(ball(0.15, loc=(0, 0, 2.74), m=E("#FFE7A6", 2.0), seg=8, rings=6))
    o.append(lathe([(0.10, 2.84), (0.20, 2.85), (0.19, 2.89), (0.09, 2.94), (0.0, 2.96)], 8, [M("navy")],
                   cap_bot=True, name="hat"))
    o.append(ball(0.03, loc=(0, 0, 2.98), m=M("mustard"), seg=6, rings=4))
    return root(o)


def build_lamp_floor():
    o = [lathe([(0.16, 0.0), (0.165, 0.015), (0.14, 0.04), (0.04, 0.06), (0.0, 0.062)], 12, [M("cocoa")],
               cap_bot=True, name="base")]
    o.append(rcyl(0.018, 1.20, loc=(0, 0, 0), z0=0.05, m=M("gold_dark"), verts=6, bevel=0))
    o.append(lathe([(0.20, 1.18), (0.14, 1.52), (0.13, 1.52), (0.19, 1.19)], 12, [E("#FFE2A8", 2.0)],
                   name="shade", recalc=True))
    o.append(lathe([(0.205, 1.17), (0.205, 1.20), (0.19, 1.20)], 12, [M("gold_dark")], name="rim"))
    o.append(ball(0.05, loc=(0, 0, 1.30), m=E("#FFF4D0", 2.5), seg=8, rings=5))
    return root(o)


def build_bench():
    o = []
    for s in (-1, 1):
        x = s * 0.60
        o.append(rbox((0.07, 0.07, 0.42), loc=(x, -0.16, 0), z0=0.0, m=M("navy"), r=0.02, segs=1))
        o.append(rbox((0.07, 0.07, 0.80), loc=(x, 0.17, 0), z0=0.0, m=M("navy"), r=0.02, segs=1, rot=(-8, 0, 0)))
        o.append(rbox((0.09, 0.30, 0.05), loc=(x, -0.03, 0), z0=0.60, m=M("navy"), r=0.02, segs=1))
    for i, y in enumerate((-0.15, -0.01, 0.13)):
        o.append(rbox((1.36, 0.12, 0.045), loc=(0, y, 0), z0=0.42, m=M("wood" if i % 2 == 0 else "#D69A62"),
                      r=0.018, segs=1))
    for i, z in enumerate((0.58, 0.72)):
        o.append(rbox((1.36, 0.04, 0.11), loc=(0, 0.19 + (z - 0.58) * 0.14, z), m=M("#D69A62" if i else "wood"),
                      r=0.018, segs=1, rot=(-8, 0, 0)))
    return root(o)


def burger_shapes(x, y, z, s, depth=0.02, bevel=0.005):
    """Abstract burger silhouette in the XZ plane facing -Y (bun, lettuce, patty, bun)."""
    return [prism(kit.arc_pts(0, 0, 0.11 * s, 0.075 * s, 0, 180, 8), depth, M("bun_top"),
                  loc=(x, y, z + 0.008 * s), rot=(90, 0, 0), bevel=bevel, bottom=False),
            prism(kit.rrect_pts(0.24 * s, 0.022 * s, 0.011 * s, 2), depth, M("lettuce"),
                  loc=(x, y - 0.004, z - 0.002 * s), rot=(90, 0, 0), bevel=bevel * 0.8, bottom=False),
            prism(kit.rrect_pts(0.235 * s, 0.035 * s, 0.017 * s, 2), depth, M("patty"),
                  loc=(x, y, z - 0.03 * s), rot=(90, 0, 0), bevel=bevel, bottom=False),
            prism(kit.rrect_pts(0.21 * s, 0.04 * s, 0.02 * s, 2), depth, M("bun"),
                  loc=(x, y, z - 0.075 * s), rot=(90, 0, 0), bevel=bevel, bottom=False)]


def build_menu_board():
    o = []
    for s in (-1, 1):
        o.append(rcyl(0.035, 1.86, loc=(s * 0.52, 0.03, 0), z0=0.0, m=M("cocoa"), verts=8, bevel=0))
        o.append(rbox((0.09, 0.24, 0.05), loc=(s * 0.52, 0.03, 0), z0=0.0, m=M("cocoa"), r=0, segs=1))
    o.append(rbox((1.16, 0.08, 0.78), loc=(0, 0, 0), z0=1.06, m=M("wood"), r=0.04, segs=1))
    o.append(rbox((1.06, 0.02, 0.62), loc=(0, -0.04, 1.47), m=M("cocoa"), r=0.02, segs=1))
    o.append(rbox((1.20, 0.10, 0.10), loc=(0, 0, 0), z0=1.80, m=M("tomato"), r=0.04, segs=1))
    o += burger_shapes(-0.30, -0.05, 1.52, 1.5, bevel=0)
    rows = [("cream", "mustard"), ("cream", "tomato"), ("cream", "mint")]
    for i, (bar, tag) in enumerate(rows):
        z = 1.68 - i * 0.16
        o.append(prism(kit.rrect_pts(0.34, 0.05, 0.025, 1), 0.015, M(bar), loc=(0.11, -0.05, z), rot=(90, 0, 0),
                       bottom=False, wn=False))
        o.append(prism(kit.rrect_pts(0.12, 0.08, 0.03, 1), 0.02, M(tag), loc=(0.39, -0.05, z), rot=(90, 0, 0),
                       bottom=False, wn=False))
    o.append(prism(kit.star_pts(5, 0.05, 0.022), 0.015, M("mustard"), loc=(-0.47, -0.05, 1.72), rot=(90, 0, 0),
                   bottom=False, wn=False))
    return root(o)


def neon_loop(pts2d, y, col, r=0.022, seg=5, closed=True):
    pts = [(x, y, z) for x, z in pts2d]
    return tube(pts, r, E(col, 3.0), seg=seg, closed=closed, caps=not closed, up=(0, -1, 0))


def build_neon_burger():
    o = []
    back = kit.rrect_pts(1.0, 0.86, 0.22, 3)
    o.append(prism(back, 0.05, M("navy"), loc=(0, 0.03, 0.50), rot=(90, 0, 0), bevel=0.015, segs=1))
    o.append(rbox((0.36, 0.16, 0.08), loc=(0, 0.0, 0), z0=0.0, m=M("cocoa"), r=0.03, segs=1))
    o.append(rbox((0.10, 0.06, 0.06), loc=(0, 0.0, 0), z0=0.06, m=M("cocoa"), r=0.02, segs=1))
    y = -0.035
    top = kit.arc_pts(0, 0.56, 0.36, 0.27, 0, 180, 9)
    o.append(neon_loop(top, y, "mustard"))
    wave = [(-0.38 + 0.76 * i / 10, 0.49 + 0.035 * (1 if i % 2 else -1)) for i in range(11)]
    o.append(neon_loop(wave, y, "mint", closed=False, r=0.018))
    o.append(neon_loop(kit.rrect_pts(0.80, 0.11, 0.055, 2), y, "tomato"))
    move([o[-1]], (0, 0, 0.385))
    o.append(neon_loop(kit.rrect_pts(0.70, 0.13, 0.06, 2), y, "mustard"))
    move([o[-1]], (0, 0, 0.225))
    for (x, z) in ((-0.14, 0.70), (0.02, 0.76), (0.16, 0.68)):
        o.append(ball(0.022, loc=(x, y, z), m=E("white", 3.0), seg=6, rings=4, scale=(1.5, 1, 1)))
    return root(o)


def build_umbrella():
    o = [lathe([(0.22, 0.0), (0.22, 0.05), (0.15, 0.09), (0.05, 0.12), (0.0, 0.125)], 12, [M("cocoa")],
               cap_bot=True, name="base")]
    o.append(rcyl(0.025, 2.20, loc=(0, 0, 0), z0=0.10, m=M("white"), verts=6, bevel=0))
    seg = 16

    def scallop(i, t, p):
        j = round(t / (TAU / seg))
        if i in (1, 2) and j % 2 == 1:
            return (p[0] * 0.97, p[1] * 0.97, p[2] - 0.05)
        return p
    prof = [(0.0, 2.17), (0.76, 1.92), (0.80, 1.93), (0.80, 1.96), (0.52, 2.15), (0.26, 2.27), (0.0, 2.31)]
    o.append(lathe(prof, seg, [T(), M("white")], deform=scallop, recalc=True, angle=45, name="canopy",
                   face_mat=lambda i, j: (j // 2) % 2))
    o.append(ball(0.05, loc=(0, 0, 2.34), m=M("white"), seg=8, rings=5))
    return root(o)


# ---------------------------------------------------------------------------
# Vehicles (front = -Y). Parts: root + wheels (child, all four wheels)
# ---------------------------------------------------------------------------
def wheel(x, y, z, r, w, hub="offwhite", both=False):
    tyre = rcyl(r, w, loc=(x, y, z), m=M("black"), verts=12, bevel=r * 0.25, segs=1, rot=(0, 90, 0), wn=False,
                angle=60)
    out = [tyre]
    sides = (-1, 1) if both else ((1,) if x > 0 else (-1,))
    for side in sides:
        out.append(rcyl(r * 0.52, 0.03, loc=(x + side * w / 2, y, z), m=M(hub), verts=10, bevel=0,
                        rot=(0, 90, 0)))
        out.append(rcyl(r * 0.18, 0.03, loc=(x + side * (w / 2 + 0.02), y, z), m=M("steel_dark"), verts=6,
                        bevel=0, rot=(0, 90, 0)))
    return out


def wheels_part(xs, ys, z, r, w, rootp):
    objs = []
    for x in xs:
        for y in ys:
            objs += wheel(x, y, z, r, w)
    wp = join(objs, "wheels", (0, 0, z))
    B.parent_to(wp, rootp)
    return wp


def lights(y, xs, z, col, emit, r=0.09, front=True):
    out = []
    for x in xs:
        out.append(rcyl(r, 0.05, loc=(x, y, z), m=E(col, emit), verts=12, bevel=0.015, rot=(90, 0, 0)))
    return out


def cabin(x0, y_c, z_base, z_belt, z_win, z_roof, w0, d0, w1, d1, w2, d2, lean=0.10):
    """Greenhouse cabin as a loft of rounded-rect rings: window band = glass on
    the four straight sides, rounded corner faces stay body colour (pillars)."""
    def ring(w, d, z, dy):
        return [(x0 + px, y_c + dy + py, z) for px, py in kit.rrect_pts(w, d, min(w, d) * 0.22, 2)]
    rings = [ring(w0 + 0.04, d0 + 0.04, z_base, 0.0), ring(w0, d0, z_belt, 0.0), ring(w1, d1, z_win, lean),
             ring(w2, d2, z_roof, lean * 1.2)]
    glass_j = {2, 5, 8, 11}
    cab = kit.loft(rings, [T(), M("glass")], cap_top=True, name="cabin", angle=40,
                   face_mat=lambda i, j: 1 if (i == 1 and j in glass_j) else 0)
    cab.data.polygons[-1].material_index = 0
    return cab


def build_car():
    o = []
    # lower body (tint) + greenhouse cabin (tint body, glass windows)
    o.append(rbox((1.64, 3.30, 0.56), loc=(0, 0, 0), z0=0.26, m=T(), r=0.22, segs=3))
    o.append(cabin(0, 0.18, 0.74, 0.84, 1.18, 1.30, 1.48, 1.92, 1.30, 1.40, 1.16, 1.20, lean=0.10))
    for s in (-1, 1):
        o.append(rbox((0.04, 2.5, 0.07), loc=(s * 0.815, 0.0, 0.53), m=M("white"), r=0.02, segs=1))
    # bumpers, grille, lights
    o.append(rbox((1.68, 0.18, 0.16), loc=(0, -1.62, 0.34), m=M("offwhite"), r=0.07, segs=2))
    o.append(rbox((1.68, 0.18, 0.16), loc=(0, 1.62, 0.34), m=M("offwhite"), r=0.07, segs=2))
    o.append(rbox((0.70, 0.06, 0.12), loc=(0, -1.645, 0.53), m=M("cocoa"), r=0.04, segs=2))
    o += lights(-1.655, (-0.52, 0.52), 0.55, "#FFF1C2", 2.0, r=0.09)
    o += lights(1.655, (-0.55, 0.55), 0.55, "red", 1.5, r=0.075)
    rootp = root(o)[0]
    wp = wheels_part((-0.70, 0.70), (-1.06, 1.06), 0.30, 0.30, 0.26, rootp)
    return [rootp, wp]


def build_van():
    o = []
    o.append(rbox((1.84, 3.15, 1.56), loc=(0, 0.45, 0), z0=0.30, m=T(), r=0.26, segs=2))
    o.append(rbox((1.80, 1.20, 0.86), loc=(0, -1.38, 0), z0=0.30, m=T(), r=0.26, segs=2))
    # windscreen + cab side windows
    o.append(rbox((1.26, 0.06, 0.46), loc=(0, -1.13, 1.30), m=M("glass"), r=0.06, segs=1))
    for s in (-1, 1):
        o.append(rbox((0.06, 0.56, 0.42), loc=(s * 0.92, -0.55, 1.30), m=M("glass"), r=0.06, segs=1))
        # side badge: white disc with a burger silhouette
        o.append(rcyl(0.40, 0.04, loc=(s * 0.915, 0.65, 1.08), m=M("white"), verts=16, bevel=0,
                      rot=(0, 90, 0)))
        bs = burger_shapes(0, 0, 0, 2.3, depth=0.02, bevel=0)
        move(bs, (0, 0, 0), rot=(0, 0, 90 * s))
        move(bs, (s * 0.93, 0.65, 1.10))
        o += bs
        o.append(rbox((0.04, 2.9, 0.08), loc=(s * 0.91, 0.30, 0.52), m=M("mustard"), r=0.025, segs=1))
    # rear doors seam + handles
    o.append(rbox((0.03, 0.03, 1.10), loc=(0, 2.03, 1.0), m=M("steel_dark"), r=0.01, segs=1))
    # bumpers / lights
    o.append(rbox((1.88, 0.18, 0.18), loc=(0, -1.98, 0.38), m=M("offwhite"), r=0.08, segs=2))
    o.append(rbox((1.88, 0.18, 0.18), loc=(0, 2.04, 0.38), m=M("offwhite"), r=0.08, segs=2))
    o.append(rbox((0.80, 0.06, 0.16), loc=(0, -1.99, 0.66), m=M("cocoa"), r=0.05, segs=2))
    o += lights(-1.99, (-0.62, 0.62), 0.74, "#FFF1C2", 2.0, r=0.11)
    o += lights(2.04, (-0.70, 0.70), 0.70, "red", 1.5, r=0.08)
    # roof burger sign (abstract 3D burger on a little stand)
    o.append(rbox((0.30, 0.30, 0.10), loc=(0, 0.6, 0), z0=1.84, m=M("cocoa"), r=0.04, segs=1))
    zb = 1.94
    o.append(lathe([(0.30, zb), (0.34, zb + 0.04), (0.33, zb + 0.10), (0.30, zb + 0.12)], 12, [M("bun")],
                   cap_bot=True, loc=(0, 0.6, 0), name="b1"))
    o.append(lathe([(0.34, zb + 0.11), (0.37, zb + 0.14), (0.36, zb + 0.20), (0.33, zb + 0.22)], 12,
                   [M("patty")], loc=(0, 0.6, 0), name="b2"))
    o.append(lathe([(0.34, zb + 0.21), (0.36, zb + 0.25), (0.31, zb + 0.36), (0.18, zb + 0.44), (0.0, zb + 0.46)],
                   12, [M("bun_top")], loc=(0, 0.6, 0), name="b3"))
    rootp = root(o)[0]
    wp = wheels_part((-0.80, 0.80), (-1.30, 1.40), 0.34, 0.34, 0.28, rootp)
    return [rootp, wp]


def build_scooter():
    o = []
    for y in (-0.50, 0.46):
        o += wheel(0.0, y, 0.20, 0.20, 0.12, hub="steel", both=True)
    o.append(rcyl(0.022, 0.30, loc=(0, 0.46, 0.20), m=M("steel_dark"), verts=6, bevel=0, rot=(0, 90, 0)))
    o.append(rbox((0.30, 0.62, 0.10), loc=(0, -0.02, 0), z0=0.24, m=M("cocoa"), r=0.04, segs=1))
    o.append(rbox((0.40, 0.56, 0.34), loc=(0, 0.36, 0), z0=0.28, m=M("mint"), r=0.15, segs=2))
    o.append(rbox((0.30, 0.46, 0.10), loc=(0, 0.30, 0), z0=0.60, m=M("cocoa"), r=0.05, segs=1))
    # front fender + leg shield
    o.append(rbox((0.36, 0.12, 0.62), loc=(0, -0.40, 0.58), m=M("mint"), r=0.06, segs=2, rot=(-14, 0, 0)))
    o.append(rbox((0.18, 0.36, 0.08), loc=(0, -0.50, 0.42), m=M("mint"), r=0.04, segs=1))
    # steering column + handlebar + headlight
    o.append(tube([(0, -0.47, 0.40), (0, -0.52, 0.98)], 0.03, M("steel_dark"), seg=6))
    o.append(rcyl(0.022, 0.56, loc=(0, -0.52, 1.0), m=M("steel"), verts=6, bevel=0, rot=(0, 90, 0)))
    for s in (-1, 1):
        o.append(rcyl(0.032, 0.11, loc=(s * 0.25, -0.52, 1.0), m=M("cocoa"), verts=6, bevel=0, rot=(0, 90, 0)))
    o.append(rbox((0.20, 0.12, 0.13), loc=(0, -0.55, 0.98), m=M("mint"), r=0.05, segs=1))
    o.append(rcyl(0.05, 0.03, loc=(0, -0.62, 0.98), m=E("#FFF1C2", 2.0), verts=10, bevel=0, rot=(90, 0, 0)))
    # rear rack + delivery box (tint) with a fixed white lid band
    o.append(rbox((0.36, 0.36, 0.03), loc=(0, 0.47, 0), z0=0.70, m=M("steel_dark"), r=0.01, segs=1))
    o.append(rbox((0.46, 0.44, 0.34), loc=(0, 0.47, 0), z0=0.73, m=T(), r=0.06, segs=2))
    o.append(rbox((0.48, 0.46, 0.06), loc=(0, 0.47, 0), z0=1.03, m=M("white"), r=0.03, segs=1))
    o.append(rcyl(0.025, 0.02, loc=(0, 0.47 - 0.23, 0.93), m=M("mustard"), verts=8, bevel=0, rot=(90, 0, 0)))
    o.append(rcyl(0.04, 0.03, loc=(0, 0.66, 0.42), m=E("red", 1.5), verts=8, bevel=0, rot=(90, 0, 0)))
    return root(o)


def build_cloud():
    o = []
    for (x, y, z, r, sd) in ((0.0, 0.0, 0.45, 0.62, 1), (-0.85, 0.05, 0.32, 0.46, 2), (0.85, -0.05, 0.34, 0.50, 3),
                             (-0.35, -0.25, 0.30, 0.40, 4), (0.40, 0.25, 0.42, 0.42, 5)):
        o.append(ball(r, loc=(x, y, z), m=M("white"), seg=8 if r > 0.45 else 7, rings=5 if r > 0.45 else 4,
                      jitter=0.03, seed=sd, flat_bottom=-z, scale=(1.0, 0.85, 0.85)))
    return root(o)


ENV = {"ao": 0.35, "ao_strength": 0.8}
MODELS = {
    "tree_round": (build_tree_round, 900, ENV),
    "tree_pine": (lambda: build_tree_pine(False), 600, ENV),
    "tree_pine_snow": (lambda: build_tree_pine(True), 600, ENV),
    "palm": (build_palm, 900, ENV),
    "bush": (build_bush, 400, ENV),
    "flower_bed": (build_flower_bed, 600, ENV),
    "plant_pot": (build_plant_pot, 600, ENV),
    "lamp_street": (build_lamp_street, 500, ENV),
    "lamp_floor": (build_lamp_floor, 400, ENV),
    "bench": (build_bench, 600, ENV),
    "menu_board": (build_menu_board, 500, ENV),
    "neon_burger": (build_neon_burger, 800, ENV),
    "umbrella": (build_umbrella, 700, ENV),
    "car": (build_car, 2500, {"ao": 0.5, "ao_strength": 0.8, "prev_kw": {"tint_color": "#3D8BFF"}}),
    "van": (build_van, 2500, {"ao": 0.5, "ao_strength": 0.8, "prev_kw": {"tint_color": "#F2542D"}}),
    "scooter": (build_scooter, 1200, ENV),
    "cloud": (build_cloud, 300, {"ao": 0.6, "ao_strength": 0.5}),
}

if __name__ == "__main__":
    kit.run(MODELS)
