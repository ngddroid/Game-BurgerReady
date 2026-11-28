"""
Food & items (ASSET_CONTRACT §4): models/food_<name>.bvm
One part `item`, pivot at the bottom centre, anchor `@top` where the next
stacked item sits. Budget <= 700 tris each (they are stacked up to 30 high).

    Blender -b --factory-startup --python tools/blender/food.py [-- food_burger ...]
"""
import os
import sys
import math
import random

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from kit import M, T, E, B, lathe, section_loft, prism, tube, rbox, rcyl, ball, seed_shape, join, move  # noqa: E402

SEG = 16
TAU = 2 * math.pi

# ---------------------------------------------------------------------------
# Burger components (all take a base height z and return (objs, new_z))
# ---------------------------------------------------------------------------
def bun_bottom(z=0.0, col="bun", cut="bun_light"):
    prof = [(0.120, z), (0.142, z + 0.014), (0.146, z + 0.040), (0.131, z + 0.056)]
    o = lathe(prof, SEG, [M(col), M(cut)], band_mat=[0, 0, 0], cap_bot=True, cap_top=True,
              name="bunb")
    # top cap gets the lighter "cut" colour
    o.data.polygons[-1].material_index = 1
    return [o], z + 0.052


def patty(z, col="patty", r=0.153, h=0.044, phase=0.0):
    prof = [(r - 0.011, z), (r, z + 0.010), (r + 0.001, z + h - 0.010), (r - 0.010, z + h)]

    def lump(i, t, p):
        k = 1 + 0.022 * math.sin(5 * t + phase) + 0.012 * math.sin(9 * t + 1.3 + phase)
        return (p[0] * k, p[1] * k, p[2] + (0.002 * math.sin(7 * t + phase) if i in (1, 2) else 0))
    o = lathe(prof, SEG, [M(col)], deform=lump, name="patty")
    return [o], z + h


def lettuce(z, col="lettuce"):
    prof = [(0.128, z - 0.001), (0.162, z + 0.000), (0.160, z + 0.011), (0.128, z + 0.015)]

    def ruffle(i, t, p):
        if i in (1, 2):
            w = math.sin(7 * t)
            k = 1 + 0.035 * math.sin(7 * t + 0.6)
            return (p[0] * k, p[1] * k, p[2] + 0.009 * w)
        return p
    o = lathe(prof, SEG, [M(col)], deform=ruffle, name="lettuce")
    return [o], z + 0.010


def tomato_slice(z):
    prof = [(0.150, z), (0.161, z + 0.006), (0.157, z + 0.016)]
    o = lathe(prof, SEG, [M("tomato_slice"), M("#F47A5A")], band_mat=[0, 0], cap_top=True,
              name="tomato")
    o.data.polygons[-1].material_index = 1
    return [o], z + 0.014


def cheese_slice(z, rotz=18.0, drips=2, size=0.29):
    """Thin square slice whose corners melt down over the patty."""
    import bmesh
    bm = bmesh.new()
    n = 3
    h = 0.008
    half = size / 2
    grid = []
    for iy in range(n + 1):
        row = []
        for ix in range(n + 1):
            x = -half + size * ix / n
            y = -half + size * iy / n
            d = math.hypot(x, y)
            droop = min(0.046, 1.3 * max(0.0, d - 0.142))
            if ix in (0, n) and iy in (0, n):  # soften the pointy corners
                x, y = x * 0.92, y * 0.92
                d = math.hypot(x, y)
                droop = min(0.046, 1.3 * max(0.0, d - 0.142))
            row.append((x, y, z + h - droop))
        grid.append(row)
    top = [[bm.verts.new(p) for p in row] for row in grid]
    for iy in range(n):
        for ix in range(n):
            bm.faces.new((top[iy][ix], top[iy][ix + 1], top[iy + 1][ix + 1], top[iy + 1][ix]))
    # side skirt around the border
    border = [top[0][i] for i in range(n + 1)] + [top[i][n] for i in range(1, n + 1)] + \
             [top[n][i] for i in range(n - 1, -1, -1)] + [top[i][0] for i in range(n - 1, 0, -1)]
    low = [bm.verts.new((v.co.x, v.co.y, v.co.z - h)) for v in border]
    m = len(border)
    for i in range(m):
        j = (i + 1) % m
        bm.faces.new((low[i], low[j], border[j], border[i]))
    o = kit._mk(bm, "cheese", [M("cheese")])
    kit.finish(o, angle=60, rot=(0, 0, rotz))
    objs = [o]
    # drips hanging from two front corners
    corners = [(half, -half), (-half, -half), (half, half)][:drips]
    for cx, cy in corners:
        a = math.radians(rotz)
        x = cx * math.cos(a) - cy * math.sin(a)
        y = cx * math.sin(a) + cy * math.cos(a)
        d = math.hypot(x, y)
        x, y = x * 0.92 * 0.965, y * 0.92 * 0.965
        d = math.hypot(x, y) / 0.965
        droop = min(0.046, 1.3 * max(0.0, d - 0.142))
        objs.append(ball(0.0105, loc=(x, y, z + h - droop - 0.014), m=M("cheese"), seg=6, rings=4,
                         scale=(1, 1, 1.9), name="drip"))
    return objs, z + h


BUN_TOP = [(0.147, 0.000), (0.153, 0.018), (0.140, 0.050), (0.108, 0.081), (0.058, 0.099), (0.0, 0.105)]


def bun_top(z, col="bun_top", rim="bun_light", seeds=7, seed_cols=("sesame",), rnd_seed=4):
    prof = [(r, z + dz) for r, dz in BUN_TOP]
    o = lathe(prof, SEG, [M(rim), M(col)], band_mat=[0, 1, 1, 1, 1], name="buntop")
    objs = [o]
    rnd = random.Random(rnd_seed)
    # seeds sit on face centres (never over a dome vertex -> no AO blotches)
    spots = [(0.083, 1), (0.124, 3), (0.083, 6), (0.124, 8), (0.083, 11), (0.124, 13), (0.036, 4),
             (0.036, 12)][:seeds]
    for k, (r, j) in enumerate(spots):
        t = (j + 0.5) * TAU / SEG
        zz, nrm = dome_at(prof, r)
        x, y = r * math.cos(t), r * math.sin(t)
        nrm = (nrm[0] * math.cos(t), nrm[0] * math.sin(t), nrm[1])
        objs.append(seed_shape((x, y, zz + 0.0015), nrm, 0.0135, M(seed_cols[k % len(seed_cols)]),
                               spin=rnd.uniform(0, math.pi), name="seed"))
    return objs, z + BUN_TOP[-1][1]


def dome_at(prof, r):
    """Height and (radial, z) normal of a lathe profile at radius r (dome part)."""
    pts = sorted(prof, key=lambda p: p[0])
    for (r0, z0), (r1, z1) in zip(pts, pts[1:]):
        if r0 <= r <= r1:
            t = (r - r0) / (r1 - r0) if r1 > r0 else 0
            z = z0 + (z1 - z0) * t
            slope = (z1 - z0) / (r1 - r0) if r1 > r0 else 0
            n = (-slope, 1.0)
            ln = math.hypot(*n)
            return z, (n[0] / ln, n[1] / ln)
    return pts[-1][1], (0.0, 1.0)


def item(objs, top):
    o = join(objs, "item", (0, 0, 0))
    B.anchor("top", (0, 0, top))
    return [o]


# ---------------------------------------------------------------------------
# Builders
# ---------------------------------------------------------------------------
def build_burger():
    objs, z = bun_bottom()
    o, z = patty(z - 0.002)
    objs += o
    o, z = lettuce(z)
    objs += o
    o, z = bun_top(z - 0.004)
    objs += o
    return item(objs, z - 0.006)


def build_cheese():
    objs, z = bun_bottom()
    o, z = lettuce(z - 0.004)
    objs += o
    o, z = patty(z - 0.002)
    objs += o
    o, z = cheese_slice(z - 0.002)
    objs += o
    o, z = bun_top(z - 0.004, rnd_seed=7)
    objs += o
    return item(objs, z - 0.006)


def build_double():
    objs, z = bun_bottom()
    o, z = patty(z - 0.002)
    objs += o
    o, z = cheese_slice(z - 0.002, rotz=12, drips=2)
    objs += o
    o, z = patty(z - 0.002, phase=1.7)
    objs += o
    o, z = cheese_slice(z - 0.002, rotz=-22, drips=0)
    objs += o
    o, z = bun_top(z - 0.002, seeds=6, rnd_seed=9)
    objs += o
    return item(objs, z - 0.006)


def build_veggie():
    objs, z = bun_bottom(col="#C97C3E", cut="#E3A868")
    o, z = patty(z - 0.002, col="#6A8A2C", h=0.040, phase=0.8)
    objs += o
    o, z = lettuce(z)
    objs += o
    o, z = tomato_slice(z - 0.004)
    objs += o
    o, z = bun_top(z - 0.004, col="#B9672C", rim="#D99552", seeds=8,
                   seed_cols=("sesame", "wood_dark", "bun_light", "patty_dark"), rnd_seed=2)
    objs += o
    return item(objs, z - 0.006)


def build_fries():
    # Tapered paper carton with a curved "smile" top edge and a mustard band.
    def sec(w, d, z, curve=0.0, n=3):
        pts = kit.rrect_pts(w, d, 0.03, n)
        return [(x, y, z + curve * (x / (w / 2)) ** 2) for x, y in pts]
    rings = [sec(0.150, 0.100, 0.0), sec(0.166, 0.112, 0.070), sec(0.170, 0.115, 0.086),
             sec(0.200, 0.136, 0.140, 0.030), sec(0.190, 0.126, 0.143, 0.030),
             sec(0.182, 0.118, 0.105, 0.015)]
    box = kit.loft(rings, [M("fries_box"), M("mustard"), M("#C23222"), M("fries")],
                   band_mat=[0, 1, 0, 0, 2], cap_bot=True, cap_top=True, name="carton", angle=50)
    box.data.polygons[-1].material_index = 3
    objs = [box]
    # Fry sticks fanned out of the carton
    fry_pts = kit.rrect_pts(0.028, 0.028, 0.008, 1)
    small = [(x * 0.62, y * 0.62) for x, y in fry_pts]
    layout = [(-0.062, 0.026, 0.232, -15, 7), (-0.032, -0.030, 0.214, -7, -10), (0.000, 0.032, 0.250, 2, 9),
              (0.034, -0.026, 0.226, 8, -8), (0.064, 0.024, 0.224, 16, 6), (-0.012, -0.004, 0.244, -3, -2),
              (0.040, 0.030, 0.240, 9, 11), (-0.050, -0.006, 0.222, -12, -3), (0.020, 0.004, 0.236, 4, 1),
              (0.068, -0.026, 0.208, 17, -9), (-0.068, -0.034, 0.202, -17, -11)]
    cols = ["fries", "fries", "#FFD866"]
    for k, (x, y, top, ty, tx) in enumerate(layout):
        L = top - 0.075
        rings = [[(px, py, 0.0) for px, py in fry_pts], [(px, py, L - 0.007) for px, py in fry_pts],
                 [(px, py, L) for px, py in small]]
        f = kit.loft(rings, [M(cols[k % 3])], cap_top=True, name="fry", angle=50)
        move([f], (x, y, 0.075), rot=(tx, ty, k * 17))
        objs.append(f)
    return item(objs, 0.20)


def build_soda():
    prof = [(0.050, 0.000), (0.055, 0.006), (0.066, 0.170), (0.071, 0.173), (0.073, 0.186),
            (0.066, 0.192), (0.040, 0.199), (0.0, 0.201)]
    cup = lathe(prof, SEG, [T(), M("white")], band_mat=[0, 0, 1, 1, 1, 1, 1], cap_bot=True, name="cup")
    # straw: candy striped, bent toward the viewer's right
    pts = [(0.012, 0.010, 0.170), (0.014, 0.010, 0.215), (0.016, 0.010, 0.238), (0.026, 0.010, 0.255),
           (0.042, 0.010, 0.262)]
    straw = tube(pts, 0.009, M("red"), seg=6, mats=[M("red"), M("white")],
                 seg_mat=lambda i, j: i % 2, name="straw")
    return item([cup, straw], 0.200)


def build_shake():
    glass = [(0.046, 0.000), (0.048, 0.007), (0.036, 0.014), (0.014, 0.024), (0.012, 0.048),
             (0.028, 0.062), (0.052, 0.088), (0.064, 0.130), (0.068, 0.166), (0.070, 0.186)]
    # band 7 (top of bowl) is the empty glass above the shake; streak column for a glassy shine
    cup = lathe(glass, SEG, [M("glass"), M("#FF9FBF"), M("#FFC6D8"), M("#E4F7FF")],
                band_mat=[0, 0, 0, 0, 1, 1, 1, 1, 0], cap_bot=True, name="glass",
                face_mat=lambda i, j: ((2 if i < 8 else 3) if (j == 13 and i in (5, 6, 7, 8)) else None))
    cream_prof = [(0.067, 0.183), (0.072, 0.201), (0.059, 0.216), (0.058, 0.229), (0.040, 0.246),
                  (0.020, 0.261), (0.0, 0.265)]

    def swirl(i, t, p):
        k = 1 + 0.06 * math.sin(3 * t + i * 1.4)
        return (p[0] * k, p[1] * k, p[2])
    cream = lathe(cream_prof, SEG, [M("cream")], deform=swirl, name="cream")
    cherry = ball(0.021, loc=(0.004, -0.004, 0.282), m=M("red"), seg=8, rings=6, name="cherry")
    stem = tube([(0.004, -0.004, 0.296), (0.008, -0.002, 0.312), (0.018, 0.004, 0.322)], 0.0025,
                M("lettuce_dark"), seg=4, name="stem")
    straw = tube([(-0.020, 0.018, 0.230), (-0.030, 0.026, 0.285), (-0.038, 0.032, 0.312)], 0.008,
                 M("pink"), seg=6, mats=[M("pink"), M("white")], seg_mat=lambda i, j: i % 2, name="straw")
    return item([cup, cream, cherry, stem, straw], 0.265)


def build_tray():
    def sec(w, d, z):
        return [(x, y, z) for x, y in kit.rrect_pts(w, d, 0.045, 3)]
    tr = kit.loft([sec(0.33, 0.23, 0.0), sec(0.36, 0.26, 0.018), sec(0.348, 0.248, 0.025),
                   sec(0.328, 0.228, 0.012)], [M("teal"), M("#2BB7C8")], band_mat=[0, 0, 0],
                  cap_bot=True, cap_top=True, name="tray", wn=False)
    tr.data.polygons[-1].material_index = 1
    objs = [tr]
    # paper liner
    liner = rbox((0.20, 0.15, 0.003), loc=(-0.035, 0.005, 0.0135), m=M("cream"), r=0, rot=(0, 0, 8))
    objs.append(liner)
    # ketchup smear
    objs.append(rcyl(0.022, 0.003, loc=(-0.07, -0.035, 0.0155), m=M("tomato"), verts=8, bevel=0,
                     rot=None))
    # crumpled wrapper balls
    objs.append(ball(0.03, loc=(-0.09, 0.045, 0.040), m=M("offwhite"), seg=6, rings=4, jitter=0.18,
                     seed=3, name="wrap"))
    objs.append(ball(0.024, loc=(0.0, -0.06, 0.034), m=M("mustard"), seg=6, rings=4, jitter=0.2,
                     seed=8, name="wrap2"))
    # empty cup lying on its side
    cup = lathe([(0.026, 0.0), (0.030, 0.004), (0.034, 0.090), (0.031, 0.091), (0.029, 0.082)], 10,
                [M("tomato"), M("white"), M("#8E2A1C")], band_mat=[0, 0, 1, 2], cap_bot=True,
                name="cup")
    move([cup], (0.03, 0.05, 0.047), rot=(0, 90, 0))
    move([cup], (0, 0, 0), rotz=-24)
    objs.append(cup)
    # stray fries + crumbs
    fry = rbox((0.10, 0.018, 0.018), loc=(-0.02, -0.025, 0.024), m=M("fries"), r=0.006, segs=1,
               rot=(0, 0, 32))
    objs.append(fry)
    rnd = random.Random(5)
    for k in range(5):
        objs.append(seed_shape((rnd.uniform(-0.13, 0.12), rnd.uniform(-0.09, 0.09), 0.0155),
                               (0, 0, 1), 0.011, M("bun"), spin=rnd.uniform(0, 3), name="crumb"))
    return item(objs, 0.075)


def build_bag():
    def sec(w, d, z, n=2):
        return [(x, y, z) for x, y in kit.rrect_pts(w, d, 0.02, n)]
    kraft = M("#D6A061")
    dark = M("wood")
    body = kit.loft([sec(0.215, 0.135, 0.0), sec(0.228, 0.148, 0.014), sec(0.236, 0.156, 0.180),
                     sec(0.236, 0.070, 0.214), sec(0.236, 0.028, 0.226)],
                    [kraft], cap_bot=True, cap_top=True, name="bag", angle=40)
    # top folded over once toward the front: crease roll + flap lying on the pinch slope
    from mathutils import Vector
    roll = rcyl(0.013, 0.238, loc=(0, -0.006, 0.226), m=kraft, verts=10, bevel=0.004, rot=(0, 90, 0))
    th = math.radians(-54)
    nrm = Vector((0, -math.cos(th), -math.sin(th)))
    c = Vector((0, -0.0455, 0.2045)) + nrm * 0.007
    flap = rbox((0.238, 0.012, 0.074), loc=tuple(c), m=dark, r=0.005, segs=2, rot=(-54, 0, 0))
    sc = c + nrm * 0.008
    seal = rcyl(0.024, 0.006, loc=tuple(sc), m=M("tomato"), verts=12, bevel=0.002, rot=(36, 0, 0))
    star = prism(kit.star_pts(5, 0.0135, 0.006), 0.003, M("mustard"), z0=0.0,
                 loc=tuple(sc + nrm * 0.0025), rot=(36, 0, 0), wn=False)
    return item([body, roll, flap, seal, star], 0.25)


def build_coin():
    prof = [(0.072, 0.000), (0.079, 0.006), (0.080, 0.028), (0.074, 0.035), (0.066, 0.0355),
            (0.060, 0.029), (0.057, 0.026)]
    gold = M("gold", metal=0.35, rough=0.32)
    dark = M("gold_dark", metal=0.35, rough=0.4)
    coin = lathe(prof, 20, [gold, dark], band_mat=[0, 0, 0, 0, 0, 1], cap_bot=True, cap_top=True,
                 name="coin", wn=False)
    coin.data.polygons[-1].material_index = 1
    # embossed bun shape: dome + patty bar + bottom bun (raised, bright gold)
    z0 = 0.0255
    dome = kit.arc_pts(0, 0.004, 0.041, 0.030, 0, 180, 8)
    parts = [coin,
             prism(dome, 0.0075, gold, z0=z0, bevel=0.002, segs=1, name="emb1"),
             prism(kit.rrect_pts(0.088, 0.014, 0.007, 1), 0.0075, gold, z0=z0, bevel=0.002,
                   loc=(0, -0.0095, 0), name="emb2"),
             prism(kit.rrect_pts(0.076, 0.016, 0.008, 1), 0.0075, gold, z0=z0, bevel=0.002,
                   loc=(0, -0.0305, 0), name="emb3")]
    return item(parts, 0.035)


def build_cash():
    """A banded brick of green bills: what guests leave at the register (stacked by WorldView)."""
    bill = M("#4CB86A")
    face = M("#8EDB9C")
    deep = M("#2F8A4E")
    band = M("mustard")
    objs = [rbox((0.220, 0.110, 0.040), m=bill, r=0.006, segs=1, z0=0.0, name="stack"),
            rbox((0.206, 0.098, 0.004), m=face, r=0.002, segs=1, z0=0.0395, name="top_bill")]
    # printed ovals on the top bill, either side of the band
    for x in (-0.068, 0.068):
        objs.append(rcyl(0.021, 0.003, loc=(x, 0.0, 0.0), m=deep, verts=12, bevel=0.0, z0=0.043, name="oval"))
    # paper band around the middle, with a little bun stamp
    objs.append(rbox((0.046, 0.114, 0.046), m=band, r=0.004, segs=1, z0=-0.002, name="band"))
    objs.append(rcyl(0.012, 0.003, loc=(0.0, 0.0, 0.0), m=M("tomato"), verts=10, bevel=0.0, z0=0.044, name="stamp"))
    return item(objs, 0.047)


def build_gem():
    n = 8
    girdle_lo = [(0.070 * math.cos(TAU * j / n), 0.070 * math.sin(TAU * j / n), 0.072) for j in range(n)]
    girdle_hi = [(0.070 * math.cos(TAU * j / n), 0.070 * math.sin(TAU * j / n), 0.082) for j in range(n)]
    crown = [(0.060 * math.cos(TAU * (j + 0.5) / n), 0.060 * math.sin(TAU * (j + 0.5) / n), 0.102)
             for j in range(n)]
    table = [(0.040 * math.cos(TAU * j / n), 0.042 * math.sin(TAU * j / n), 0.118) for j in range(n)]
    rings = [[(0, 0, 0.0)], girdle_lo, girdle_hi, crown, table]
    g = kit.loft(rings, [M("teal"), M("#4FD6E0"), M("#B8F4FA")], cap_top=True, name="gem", angle=None,
                 face_mat=lambda i, j: (1 if (i + j) % 2 else 0) if i != 3 else (2 if j % 4 == 0 else 1))
    g.data.polygons[-1].material_index = 2
    return item([g], 0.118)


def build_patty_raw():
    prof = [(0.104, 0.000), (0.117, 0.010), (0.121, 0.031), (0.111, 0.046), (0.072, 0.051), (0.032, 0.053),
            (0.0, 0.053)]

    def lump(i, t, p):
        k = 1 + 0.03 * math.sin(5 * t + 0.4) + 0.015 * math.sin(11 * t)
        dz = 0.003 * math.sin(6 * t + i) if i >= 3 else 0.0
        return (p[0] * k, p[1] * k, p[2] + dz)
    pt = lathe(prof, 18, [M("#EF7C8A")], deform=lump, cap_bot=True, name="raw")
    objs = [pt]
    rnd = random.Random(11)
    for k, (r, j) in enumerate([(0.092, 1), (0.092, 5), (0.092, 9), (0.092, 14), (0.052, 3), (0.052, 8),
                                (0.052, 12), (0.052, 16)]):
        a = (j + 0.5) * TAU / 18
        objs.append(seed_shape((r * math.cos(a), r * math.sin(a), 0.0515), (0, 0, 1),
                               rnd.uniform(0.010, 0.016), M("#FFD2DA"), spin=rnd.uniform(0, 3),
                               name="fat"))
    return item(objs, 0.050)


FOOD_AO = {"ao": 0.08, "ao_strength": 0.6}
MODELS = {
    "food_burger": (build_burger, 700, FOOD_AO),
    "food_cheese": (build_cheese, 700, FOOD_AO),
    "food_double": (build_double, 700, FOOD_AO),
    "food_veggie": (build_veggie, 700, FOOD_AO),
    "food_fries": (build_fries, 700, FOOD_AO),
    "food_soda": (build_soda, 700, FOOD_AO),
    "food_shake": (build_shake, 700, FOOD_AO),
    "food_tray": (build_tray, 700, FOOD_AO),
    "food_bag": (build_bag, 700, FOOD_AO),
    "food_coin": (build_coin, 700, {"ao": 0.05}),
    "food_gem": (build_gem, 700, {"ao": 0.05}),
    "food_cash": (build_cash, 400, {"ao": 0.05}),
    "food_patty_raw": (build_patty_raw, 700, FOOD_AO),
}

if __name__ == "__main__":
    kit.run(MODELS)
