"""
World Tour region props (docs/WORLD_TOUR_DESIGN.md §3, ASSET_CONTRACT §7b): models/region_<region>_<prop>.bvm
Same conventions as stores.py: one part `root`, pivot at the bottom centre, front = -Y, <= 1,500 tris each,
footprint about 1.6 m, height <= 3 m so they fit the three signature-prop spots beside the entrance.

    Blender -b --factory-startup --python tools/blender/regions.py [-- region_harbor_* ...]
"""
import os
import sys
import math
import random

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from kit import M, T, E, lathe, prism, tube, rbox, rcyl, ball, leaf, join  # noqa: E402
from mathutils import Vector  # noqa: E402
from stations import striped_awning  # noqa: E402
from env import wheel, burger_shapes  # noqa: E402

TAU = 2 * math.pi


def root(objs):
    r = join(objs, "root", (0, 0, 0))
    kit.clamp_floor([r])
    return [r]


def crate(size, loc, col="wood", band="wood_dark"):
    w, d, h = size
    o = [rbox(size, loc=loc, m=M(col), r=0.02, segs=1)]
    x, y, z = loc
    for dz in (-h * 0.32, h * 0.32):
        o.append(rbox((w + 0.012, d + 0.012, 0.035), loc=(x, y, z + dz), m=M(band), r=0.0, segs=1))
    return o


def tree_blob(trunk_h, canopy, col, trunk_col="wood_dark", seed=1, r=0.55):
    """Round-canopy tree: a slightly bent trunk and a few lumpy balls."""
    o = [tube([(0, 0, 0), (0.03, 0.02, trunk_h * 0.5), (-0.02, 0.0, trunk_h)], 0.09, M(trunk_col), seg=6,
              radii=[0.12, 0.09, 0.07])]
    rnd = random.Random(seed)
    for k in range(canopy):
        a = TAU * k / canopy
        rr = r * (0.75 if k else 1.0)
        loc = (math.cos(a) * r * 0.55 * (k > 0), math.sin(a) * r * 0.45 * (k > 0), trunk_h + r * 0.35 + rnd.uniform(-0.08, 0.12))
        o.append(ball(rr, loc=loc, m=M(col), seg=9, rings=6, jitter=0.1, seed=seed + k))
    return o


# ======================================================================== 1 harbor
def build_harbor_lighthouse():
    prof = [(0.48, 0.0), (0.46, 0.25), (0.40, 1.30), (0.34, 2.25), (0.34, 2.32)]
    o = [lathe(prof, 14, [M("white"), M("red")], band_mat=[0, 1, 0, 1], cap_bot=True, name="tower")]
    o.append(rcyl(0.44, 0.08, loc=(0, 0, 0), z0=2.30, m=M("navy"), verts=14, bevel=0.01))
    o.append(rcyl(0.24, 0.32, loc=(0, 0, 0), z0=2.38, m=E("#FFF1A8", 2.4), verts=10, bevel=0.0))
    o.append(lathe([(0.30, 2.70), (0.32, 2.74), (0.12, 2.92), (0.0, 2.98)], 10, [M("red")], cap_bot=True, name="cap"))
    o.append(rbox((0.26, 0.06, 0.40), loc=(0, -0.42, 0.20), m=M("navy"), r=0.02, segs=1))
    return root(o)


def build_harbor_crate_stack():
    o = crate((0.62, 0.52, 0.46), (0.0, 0.0, 0.23)) + crate((0.52, 0.46, 0.40), (0.42, 0.05, 0.20), "#D9A066")
    o += crate((0.50, 0.44, 0.40), (0.15, 0.02, 0.66), "#C98A55")
    o.append(tube([(0.45, -0.30, 0.02), (0.62, -0.42, 0.04), (0.78, -0.30, 0.03)], 0.035, M("#E6D4A8"), seg=5))
    o.append(rbox((0.30, 0.22, 0.20), loc=(-0.32, -0.30, 0.10), m=M("teal"), r=0.04, segs=1))  # bait box
    return root(o)


def build_harbor_anchor():
    o = [rbox((0.5, 0.5, 0.12), loc=(0, 0, 0.06), m=M("gray"), r=0.03, segs=1)]
    o.append(rcyl(0.06, 1.25, loc=(0, 0, 0), z0=0.12, m=M("navy"), verts=8, bevel=0.0))
    o.append(rcyl(0.11, 0.05, loc=(0, 0, 0), z0=1.37, m=M("navy"), verts=10, bevel=0.0, rot=(90, 0, 0)))
    o.append(rbox((0.62, 0.10, 0.08), loc=(0, 0, 1.05), m=M("navy"), r=0.02, segs=1))
    arc = kit.arc_pts(0, 0.62, 0.42, 0.40, 200, 340, 8)
    o.append(tube([(x, 0, z) for x, z in arc], 0.055, M("navy"), seg=6))
    return root(o)


# ======================================================================== 2 blossom
def build_blossom_tree():
    o = tree_blob(1.25, 5, "#FFB3CC", seed=3)
    for k, (x, y) in enumerate([(-0.4, -0.3), (0.35, -0.25), (0.1, 0.4)]):
        o.append(ball(0.05, loc=(x, y, 0.02), m=M("#FFC9DA"), seg=5, rings=3, scale=(1, 1, 0.3)))
    return root(o)


def build_blossom_kite():
    o = [rcyl(0.035, 2.2, loc=(0, 0, 0), z0=0.0, m=M("wood"), verts=6, bevel=0.0)]
    diamond = [(0.0, 0.38), (0.26, 0.0), (0.0, -0.45), (-0.26, 0.0)]
    o.append(prism(diamond, 0.03, M("pink"), loc=(0.35, 0.0, 2.25), rot=(90, 0, 15), side_m=M("cocoa")))
    o.append(tube([(0.25, 0.0, 1.85), (0.05, -0.05, 1.55), (0.20, -0.05, 1.25), (0.05, 0.0, 0.95)], 0.015, M("mustard"), seg=4))
    for k, c in enumerate(("mint", "lilac", "mustard")):
        o.append(rbox((0.07, 0.012, 0.05), loc=(0.15 - k * 0.04, -0.05, 1.7 - k * 0.3), m=M(c), r=0.0, segs=1))
    return root(o)


# ======================================================================== 3 savanna
def build_savanna_acacia():
    o = [tube([(0, 0, 0), (0.05, 0, 0.7), (0.25, 0, 1.4)], 0.1, M("brown"), seg=6, radii=[0.13, 0.1, 0.07])]
    o.append(tube([(0.04, 0, 0.75), (-0.3, 0.05, 1.35)], 0.06, M("brown"), seg=5))
    for x, y, s in ((0.25, 0.0, 1.0), (-0.3, 0.05, 0.8), (0.0, 0.1, 0.9)):
        o.append(ball(0.6 * s, loc=(x, y, 1.48), m=M("#7BA23F"), seg=10, rings=4, scale=(1.3, 1.0, 0.32), jitter=0.06, seed=7))
    return root(o)


def build_savanna_jeep():
    o = [rbox((1.5, 0.82, 0.42), loc=(0, 0, 0.0), z0=0.28, m=M("#C9A24D"), r=0.06, segs=1)]
    o.append(rbox((0.6, 0.78, 0.05), loc=(-0.2, 0, 0.0), z0=0.70, m=M("#8B7A3A"), r=0.0, segs=1))
    for x in (-0.45, 0.1):
        for s in (-1, 1):
            o.append(rcyl(0.025, 0.42, loc=(x, s * 0.38, 0), z0=0.70, m=M("darkgray"), verts=5, bevel=0.0))
    o.append(rbox((0.7, 0.86, 0.04), loc=(-0.17, 0, 0), z0=1.12, m=M("#E8D9A8"), r=0.0, segs=1))  # canvas roof
    o.append(rbox((0.05, 0.6, 0.26), loc=(0.42, 0, 0), z0=0.72, m=M("glass"), r=0.0, segs=1))
    for x in (-0.48, 0.5):
        for s in (-1, 1):
            o += wheel(x, s * 0.44, 0.24, 0.24, 0.16)
    return root(o)


def build_savanna_drum():
    prof = [(0.26, 0.0), (0.30, 0.06), (0.32, 0.32), (0.28, 0.58), (0.28, 0.62)]
    o = [lathe(prof, 12, [M("brown"), M("tomato")], band_mat=[0, 1, 0, 0], cap_bot=True, name="drum")]
    o.append(rcyl(0.29, 0.03, loc=(0, 0, 0), z0=0.61, m=M("#F2E2C0"), verts=12, bevel=0.0))
    for k in range(8):
        a = TAU * k / 8
        o.append(tube([(math.cos(a) * 0.3, math.sin(a) * 0.3, 0.1), (math.cos(a + 0.4) * 0.3, math.sin(a + 0.4) * 0.3, 0.58)], 0.012, M("cream"), seg=3))
    return root(o)


# ======================================================================== 4 polar
def build_polar_igloo():
    o = [ball(0.8, loc=(0, 0.1, 0), m=M("#F2F8FF"), seg=14, rings=8, scale=(1, 1, 0.82), flat_bottom=0.0)]
    o.append(rbox((0.5, 0.5, 0.5), loc=(0, -0.62, 0.25), m=M("#E8F2FF"), r=0.2, segs=2))
    o.append(rbox((0.30, 0.06, 0.34), loc=(0, -0.88, 0.18), m=M("navy"), r=0.1, segs=2))
    for k in range(3):
        z = 0.22 + k * 0.2
        rr = math.sqrt(max(0.0, 0.8 ** 2 - (z / 0.82) ** 2))
        o.append(tube([(math.cos(a) * rr, 0.1 + math.sin(a) * rr, z) for a in [i * TAU / 16 for i in range(17)]], 0.012, M("#C8DDF2"), seg=3))
    return root(o)


def build_polar_ice_bench():
    ice = M("#BFE6FF")
    o = [rbox((1.2, 0.42, 0.10), loc=(0, 0, 0.42), m=ice, r=0.03, segs=1)]
    for x in (-0.45, 0.45):
        o.append(rbox((0.24, 0.38, 0.38), loc=(x, 0, 0.19), m=M("#D6F0FF"), r=0.03, segs=1))
    o.append(rbox((0.6, 0.36, 0.06), loc=(0, 0.02, 0.50), m=M("#E94B3C"), r=0.02, segs=1))  # cushion
    return root(o)


# ======================================================================== 5 delta
def build_delta_riverboat():
    hull = [(-0.75, 0.0), (0.75, 0.0), (0.9, 0.18), (-0.9, 0.18)]
    o = [prism(hull, 0.7, M("#2E7D4F"), loc=(0, 0.35, 0), rot=(90, 0, 0), side_m=M("wood_dark"))]
    o.append(rbox((1.65, 0.72, 0.05), loc=(0, 0, 0.2), m=M("wood"), r=0.0, segs=1))
    o.append(rbox((0.8, 0.6, 0.5), loc=(-0.15, 0, 0.47), m=M("cream"), r=0.04, segs=1))
    o.append(rbox((0.92, 0.72, 0.06), loc=(-0.15, 0, 0.75), m=M("red"), r=0.02, segs=1))
    o.append(rcyl(0.06, 0.5, loc=(-0.4, 0, 0), z0=0.78, m=M("black"), verts=8, bevel=0.0))
    wheel_c = (0.68, 0, 0.36)
    o.append(rcyl(0.3, 0.08, loc=wheel_c, m=M("red"), verts=12, bevel=0.0, rot=(90, 0, 0)))
    for k in range(6):
        a = TAU * k / 6
        o.append(rbox((0.05, 0.36, 0.05), loc=(0.68 + math.cos(a) * 0.2, 0, 0.36 + math.sin(a) * 0.2), m=M("wood"), r=0.0, segs=1))
    return root(o)


def build_delta_lily_planter():
    o = [lathe([(0.34, 0.0), (0.40, 0.04), (0.42, 0.30), (0.38, 0.32)], 12, [M("#B86F45")], cap_bot=True, name="pot")]
    o.append(rcyl(0.37, 0.02, loc=(0, 0, 0), z0=0.28, m=M("#3BA0B8"), verts=12, bevel=0.0))
    for k, (x, y) in enumerate([(-0.15, -0.1), (0.14, 0.08), (0.05, -0.2)]):
        o.append(rcyl(0.11, 0.015, loc=(x, y, 0), z0=0.30, m=M("#4CB86A"), verts=9, bevel=0.0))
    o.append(ball(0.05, loc=(0.14, 0.08, 0.34), m=M("pink"), seg=6, rings=4))
    for k in range(4):
        a = TAU * k / 4 + 0.4
        o.append(leaf([(0, 0, 0.3), (math.cos(a) * 0.18, math.sin(a) * 0.18, 0.75), (math.cos(a) * 0.3, math.sin(a) * 0.3, 1.0)],
                      [0.0, 0.07, 0.0], M("#3E9B34")))
    return root(o)


# ======================================================================== 6 castle
def build_castle_stone_tower():
    o = [lathe([(0.55, 0.0), (0.52, 0.15), (0.48, 1.8), (0.52, 1.85), (0.52, 2.0)], 12, [M("#B9B1A6")], cap_bot=True, name="tower")]
    for k in range(8):
        a = TAU * k / 8
        o.append(rbox((0.16, 0.16, 0.18), loc=(math.cos(a) * 0.47, math.sin(a) * 0.47, 2.08), m=M("#A79F95"), r=0.0, segs=1))
    o.append(rbox((0.24, 0.06, 0.42), loc=(0, -0.50, 0.21), m=M("wood_dark"), r=0.04, segs=1))
    o.append(rbox((0.12, 0.05, 0.22), loc=(0.1, -0.49, 1.2), m=M("#3B2A3A"), r=0.02, segs=1))
    o.append(rcyl(0.025, 0.6, loc=(0, 0, 0), z0=2.0, m=M("wood_dark"), verts=5, bevel=0.0))
    o.append(prism([(0, 0), (0.35, 0.1), (0, 0.22)], 0.02, M("lilac"), loc=(0.02, 0, 2.35), rot=(90, 0, 0)))
    return root(o)


def build_castle_market_stall():
    o = [rbox((1.3, 0.6, 0.85), loc=(0, 0, 0.425), m=M("wood"), r=0.03, segs=1)]
    o.append(rbox((1.36, 0.66, 0.05), loc=(0, 0, 0.87), m=M("wood_dark"), r=0.01, segs=1))
    for x in (-0.6, 0.6):
        o.append(rcyl(0.035, 1.2, loc=(x, 0.25, 0), z0=0.85, m=M("wood_dark"), verts=6, bevel=0.0))
    o += striped_awning(-0.72, 0.72, 5, 0.28, -0.45, 2.05, 1.75, drop=0.10)
    for k, c in enumerate(("red", "mustard", "green", "orange")):
        o.append(ball(0.08, loc=(-0.45 + k * 0.3, -0.1, 0.97), m=M(c), seg=6, rings=4))
    return root(o)


def build_castle_barrels():
    prof = [(0.22, 0.0), (0.27, 0.15), (0.28, 0.3), (0.27, 0.45), (0.22, 0.6)]
    o = []
    for x, y, z in ((-0.28, 0.0, 0.0), (0.28, 0.0, 0.0), (0.0, 0.05, 0.6)):
        o.append(lathe([(r, zz + z) for r, zz in prof], 10, [M("wood"), M("darkgray")], band_mat=[1, 0, 0, 1], cap_bot=True, cap_top=True, loc=(x, y, 0), name="barrel"))
    return root(o)


# ======================================================================== 7 gulch
def build_gulch_saloon_sign():
    o = []
    for x in (-0.7, 0.7):
        o.append(rbox((0.1, 0.1, 2.2), loc=(x, 0, 1.1), m=M("wood_dark"), r=0.01, segs=1))
    o.append(rbox((1.6, 0.08, 0.55), loc=(0, 0, 1.85), m=M("wood"), r=0.03, segs=1))
    o.append(rbox((1.45, 0.04, 0.4), loc=(0, -0.05, 1.85), m=M("#F2D8A8"), r=0.01, segs=1))
    o += burger_shapes(0.0, -0.08, 1.80, 2.2)
    for k in range(2):
        o.append(rcyl(0.05, 0.02, loc=(-0.5 + k * 1.0, -0.08, 1.85), m=E("#FFC23C", 1.2), verts=8, bevel=0.0, rot=(90, 0, 0)))
    return root(o)


def build_gulch_hay_bale():
    o = [rbox((0.9, 0.55, 0.5), loc=(0, 0, 0.25), m=M("#E8C35A"), r=0.06, segs=1)]
    for x in (-0.22, 0.22):
        o.append(rbox((0.03, 0.57, 0.52), loc=(x, 0, 0.25), m=M("#B8862B"), r=0.0, segs=1))
    o.append(rbox((0.7, 0.45, 0.42), loc=(0.1, 0.05, 0.71), m=M("#EDCB6A"), r=0.06, segs=1))
    o.append(rbox((0.03, 0.47, 0.44), loc=(0.1, 0.05, 0.71), m=M("#B8862B"), r=0.0, segs=1))
    return root(o)


# ======================================================================== 8 cove
def build_cove_hammock():
    o = []
    for x in (-0.8, 0.8):
        o.append(tube([(x, 0, 0), (x * 1.02, 0.02, 0.8), (x * 0.98, 0, 1.4)], 0.07, M("wood"), seg=6))
    pts = [(-0.75, 0, 1.05), (-0.4, 0, 0.62), (0, 0, 0.52), (0.4, 0, 0.62), (0.75, 0, 1.05)]
    for dy, c in ((-0.18, "orange"), (0.0, "mustard"), (0.18, "teal")):
        o.append(tube([(x, y + dy, z) for x, y, z in pts], 0.05, M(c), seg=5))
    o.append(ball(0.18, loc=(1.05, 0.2, 0.15), m=M("#8B5A3C"), seg=8, rings=5))  # coconut
    return root(o)


def build_cove_treasure_chest():
    o = [rbox((0.7, 0.45, 0.36), loc=(0, 0, 0.18), m=M("wood_dark"), r=0.03, segs=1)]
    lid = rcyl(0.225, 0.7, loc=(0, 0, 0.36), m=M("wood"), verts=10, bevel=0.0, rot=(0, 90, 0))
    o.append(lid)
    for x in (-0.26, 0.26):
        o.append(rbox((0.05, 0.47, 0.6), loc=(x, 0, 0.32), m=M("gold"), r=0.01, segs=1))
    o.append(rbox((0.1, 0.04, 0.12), loc=(0, -0.24, 0.34), m=M("gold"), r=0.01, segs=1))
    for k in range(5):
        o.append(rcyl(0.06, 0.02, loc=(-0.5 + k * 0.22, -0.4 + (k % 2) * 0.1, 0), z0=0.0, m=M("gold"), verts=8, bevel=0.0))
    return root(o)


# ======================================================================== 9 alpine
def build_alpine_milk_churn():
    prof = [(0.2, 0.0), (0.22, 0.05), (0.22, 0.48), (0.12, 0.62), (0.11, 0.75), (0.13, 0.78)]
    o = [lathe(prof, 12, [M("steel"), M("red")], band_mat=[0, 1, 0, 0, 0], cap_bot=True, name="churn")]
    o.append(rcyl(0.14, 0.05, loc=(0, 0, 0), z0=0.78, m=M("steel_dark"), verts=10, bevel=0.01))
    o.append(lathe([(0.17, 0.0), (0.19, 0.04), (0.19, 0.36), (0.1, 0.46), (0.11, 0.52)], 10, [M("steel")], cap_bot=True, loc=(0.38, 0.1, 0), name="churn2"))
    return root(o)


def build_alpine_fence():
    o = []
    for x in (-0.75, -0.25, 0.25, 0.75):
        o.append(rbox((0.08, 0.08, 0.8), loc=(x, 0, 0.4), m=M("wood"), r=0.01, segs=1))
        o.append(prism([(-0.04, 0), (0.04, 0), (0, 0.06)], 0.08, M("wood"), loc=(x, 0.04, 0.8), rot=(90, 0, 0)))
    for z in (0.3, 0.6):
        o.append(rbox((1.7, 0.05, 0.08), loc=(0, -0.05, z), m=M("wood_dark"), r=0.01, segs=1))
    o.append(rcyl(0.07, 0.1, loc=(0.25, -0.12, 0.45), m=M("gold"), verts=8, bevel=0.0, r2=0.04))  # cowbell
    return root(o)


# ======================================================================== 10 arcade
def build_arcade_cabinet():
    o = [rbox((0.62, 0.55, 1.5), loc=(0, 0, 0.75), m=T(), r=0.03, segs=1)]
    o.append(rbox((0.5, 0.05, 0.4), loc=(0, -0.28, 1.15), m=E("#7FE8FF", 1.6), r=0.01, segs=1))
    o.append(rbox((0.56, 0.2, 0.08), loc=(0, -0.32, 0.86), m=M("black"), r=0.02, segs=1))
    o.append(rcyl(0.02, 0.1, loc=(-0.12, -0.33, 0.9), m=M("black"), verts=5, bevel=0.0))
    o.append(ball(0.04, loc=(-0.12, -0.33, 1.0), m=M("red"), seg=6, rings=4))
    for k, c in enumerate(("mustard", "mint", "pink")):
        o.append(rcyl(0.03, 0.02, loc=(0.06 + k * 0.08, -0.36, 0.9), m=E(c, 1.0), verts=8, bevel=0.0))
    o.append(rbox((0.6, 0.12, 0.18), loc=(0, -0.22, 1.45), m=E("#FF4F9A", 1.4), r=0.02, segs=1))
    return root(o)


def build_arcade_prize_claw():
    o = [rbox((0.7, 0.7, 0.75), loc=(0, 0, 0.375), m=M("lilac"), r=0.03, segs=1)]
    o.append(rbox((0.66, 0.66, 0.8), loc=(0, 0, 1.15), m=M("glass"), r=0.0, segs=1))
    o.append(rbox((0.72, 0.72, 0.1), loc=(0, 0, 1.6), m=E("#FFC23C", 1.2), r=0.02, segs=1))
    for k, c in enumerate(("pink", "mint", "sky", "mustard", "tomato")):
        o.append(ball(0.11, loc=(-0.18 + (k % 3) * 0.18, -0.1 + (k // 3) * 0.2, 0.86), m=M(c), seg=7, rings=5))
    o.append(rcyl(0.015, 0.25, loc=(0.1, 0, 0), z0=1.3, m=M("steel"), verts=4, bevel=0.0))
    return root(o)


# ======================================================================== 11 hollow
def pumpkin(r, loc, col="orange", seed=1):
    def rib(i, a, p):
        k = 1 + 0.08 * math.cos(a * 8)
        return (p[0] * k, p[1] * k, p[2])
    prof = [(0.0, 0.0), (r * 0.7, 0.02), (r, r * 0.45), (r * 0.9, r * 0.85), (r * 0.3, r * 1.0), (0.0, r * 0.95)]
    x, y, z = loc
    p = lathe([(a, b + z) for a, b in prof], 16, [M(col)], deform=rib, loc=(x, y, 0), name="pumpkin")
    stem = rcyl(r * 0.1, r * 0.3, loc=(x, y, 0), z0=z + r * 0.9, m=M("lettuce_dark"), verts=5, bevel=0.0)
    return [p, stem]


def build_hollow_pumpkin_stack():
    o = pumpkin(0.38, (0, 0, 0)) + pumpkin(0.26, (0.45, 0.1, 0), "#FF8A2C") + pumpkin(0.22, (0.05, 0.02, 0.36), "#FFB13C")
    o.append(rbox((0.12, 0.02, 0.06), loc=(0.05, -0.2, 0.58), m=E("#FFE07A", 1.8), r=0.0, segs=1))  # carved smile glow
    return root(o)


def build_hollow_crooked_lamp():
    o = [tube([(0, 0, 0), (0.05, 0, 0.8), (-0.06, 0, 1.5), (0.1, 0, 2.0), (0.35, 0, 2.05)], 0.05, M("#3B2A3A"), seg=6)]
    o.append(rbox((0.22, 0.22, 0.28), loc=(0.35, 0, 1.85), m=M("#3B2A3A"), r=0.02, segs=1))
    o.append(rbox((0.16, 0.16, 0.22), loc=(0.35, 0, 1.85), m=E("#9BFF8A", 2.0), r=0.02, segs=1))
    o.append(rbox((0.4, 0.4, 0.06), loc=(0, 0, 0.03), m=M("#3B2A3A"), r=0.02, segs=1))
    return root(o)


# ======================================================================== 12 funfair
def build_funfair_ferris_wheel():
    o = []
    c = (0, 0, 1.45)
    ring = [(math.cos(a) * 1.0, 0, 1.45 + math.sin(a) * 1.0) for a in [i * TAU / 24 for i in range(25)]]
    o.append(tube(ring, 0.04, E("#FF8FB1", 1.0), seg=4, up=(0, -1, 0)))
    for k in range(8):
        a = TAU * k / 8
        o.append(tube([c, (math.cos(a) * 1.0, 0, 1.45 + math.sin(a) * 1.0)], 0.02, M("white"), seg=3))
        cols = ["red", "mustard", "mint", "sky"]
        o.append(rbox((0.2, 0.2, 0.18), loc=(math.cos(a) * 1.0, -0.12, 1.45 + math.sin(a) * 1.0 - 0.12), m=M(cols[k % 4]), r=0.04, segs=1))
    for s in (-1, 1):
        o.append(tube([(s * 0.6, 0.12, 0.0), (0, 0.12, 1.45)], 0.05, M("navy"), seg=5))
    o.append(rcyl(0.1, 0.3, loc=c, m=M("gold"), verts=10, bevel=0.0, rot=(90, 0, 0)))
    return root(o)


def build_funfair_ticket_booth():
    o = [rbox((0.9, 0.8, 1.2), loc=(0, 0, 0.6), m=T(), r=0.04, segs=1)]
    o.append(rbox((0.6, 0.05, 0.45), loc=(0, -0.41, 0.95), m=M("glass"), r=0.0, segs=1))
    o.append(rbox((0.7, 0.2, 0.06), loc=(0, -0.48, 0.7), m=M("wood"), r=0.01, segs=1))
    roof = lathe([(0.7, 1.2), (0.72, 1.25), (0.0, 1.7)], 8, [M("red"), M("white")], band_mat=[0, 1], cap_bot=True, a0=22.5, name="roof")
    o.append(roof)
    o.append(ball(0.08, loc=(0, 0, 1.74), m=M("gold"), seg=6, rings=4))
    return root(o)


# ======================================================================== 13 ruins
def build_ruins_column():
    o = [rbox((0.7, 0.7, 0.2), loc=(0, 0, 0.1), m=M("#E3C98F"), r=0.03, segs=1)]
    def flute(i, a, p):
        k = 1 + 0.05 * math.cos(a * 10)
        return (p[0] * k, p[1] * k, p[2])
    o.append(lathe([(0.26, 0.2), (0.24, 1.2), (0.22, 1.75)], 20, [M("#EBD6A6")], deform=flute, name="shaft"))
    broken = [(0.24, 1.75), (0.2, 1.82), (0.1, 1.9), (0.0, 1.86)]
    o.append(lathe(broken, 10, [M("#DCC28A")], name="top"))
    o.append(rbox((0.36, 0.3, 0.2), loc=(0.45, -0.2, 0.1), m=M("#D8BC84"), r=0.05, segs=1, rot=(0, 0, 25)))
    return root(o)


def build_ruins_clay_urn():
    prof = [(0.12, 0.0), (0.2, 0.08), (0.3, 0.35), (0.26, 0.6), (0.13, 0.75), (0.15, 0.85), (0.18, 0.88)]
    o = [lathe(prof, 14, [M("#C1662B"), M("#3B2A3A")], band_mat=[0, 0, 1, 0, 0, 0], cap_bot=True, name="urn")]
    o.append(lathe([(0.09, 0.0), (0.15, 0.06), (0.2, 0.25), (0.15, 0.42), (0.1, 0.48)], 10, [M("#D9822B")], cap_bot=True, loc=(0.42, 0.1, 0), name="urn2"))
    return root(o)


# ======================================================================== 14 sky
def build_sky_windmill():
    o = [lathe([(0.42, 0.0), (0.36, 1.6), (0.3, 1.7)], 8, [M("white")], cap_bot=True, a0=22.5, name="tower")]
    o.append(lathe([(0.36, 1.7), (0.0, 2.15)], 8, [M("blue")], a0=22.5, name="roof"))
    hub = (0, -0.4, 1.75)
    o.append(rcyl(0.07, 0.1, loc=hub, m=M("wood_dark"), verts=8, bevel=0.0, rot=(90, 0, 0)))
    for k in range(4):
        a = TAU * k / 4 + 0.3
        tip = (math.cos(a) * 0.95, -0.42, 1.75 + math.sin(a) * 0.95)
        o.append(leaf([hub, ((hub[0] + tip[0]) / 2, -0.42, (hub[2] + tip[2]) / 2), tip], [0.04, 0.13, 0.1], M("cream"), thick=0.02, up=(0, -1, 0)))
    o.append(rbox((0.2, 0.06, 0.32), loc=(0, -0.4, 0.16), m=M("wood_dark"), r=0.02, segs=1))
    return root(o)


def build_sky_hot_air_balloon():
    o = [ball(0.62, loc=(0, 0, 1.95), m=T(), seg=12, rings=8, scale=(1, 1, 1.15))]
    o.append(lathe([(0.3, 1.25), (0.62, 1.7)], 12, [M("white")], name="skirt"))
    o.append(rbox((0.4, 0.4, 0.3), loc=(0, 0, 0.75), m=M("wood"), r=0.04, segs=1))
    for x, y in ((-0.17, -0.17), (0.17, -0.17), (-0.17, 0.17), (0.17, 0.17)):
        o.append(tube([(x, y, 0.9), (x * 1.8, y * 1.8, 1.4)], 0.012, M("wood_dark"), seg=3))
    o.append(rcyl(0.05, 0.6, loc=(0, 0, 0), z0=0.0, m=M("wood_dark"), verts=5, bevel=0.0))  # mooring post
    return root(o)


# ======================================================================== 15 caverns
def build_caverns_crystal_cluster():
    o = [ball(0.4, loc=(0, 0, 0), m=M("#5E5A6E"), seg=8, rings=5, scale=(1.2, 1.0, 0.45), jitter=0.15, seed=4, flat_bottom=0.0)]
    rnd = random.Random(9)
    for k in range(6):
        a = TAU * k / 6 + rnd.uniform(-0.3, 0.3)
        h = rnd.uniform(0.5, 1.1)
        base = (math.cos(a) * 0.2, math.sin(a) * 0.15, 0.1)
        tip = (base[0] + math.cos(a) * 0.25, base[1] + math.sin(a) * 0.2, h)
        c = rcyl(0.09, h, loc=(0, 0, 0), m=E("#B79CFF" if k % 2 else "#7FE8FF", 1.4), verts=6, bevel=0.0, r2=0.0)
        from stores import orient
        orient(c, base, (tip[0] - base[0], tip[1] - base[1], tip[2] - base[2]))
        o.append(c)
    return root(o)


def build_caverns_mine_cart():
    o = [rbox((0.9, 0.6, 0.42), loc=(0, 0, 0.4), m=M("#8A6E5A"), r=0.03, segs=1)]
    for x in (-0.3, 0.3):
        for s in (-1, 1):
            o += wheel(x, s * 0.32, 0.17, 0.14, 0.08)
    for k in range(5):
        o.append(ball(0.12, loc=(-0.25 + k * 0.12, (k % 2) * 0.1 - 0.05, 0.64), m=E("#FFCF3F", 0.6) if k % 2 else M("#9AA3AE"), seg=6, rings=4, jitter=0.15, seed=k))
    o.append(rbox((1.4, 0.1, 0.04), loc=(0, -0.25, 0.02), m=M("darkgray"), r=0.0, segs=1))
    o.append(rbox((1.4, 0.1, 0.04), loc=(0, 0.25, 0.02), m=M("darkgray"), r=0.0, segs=1))
    return root(o)


def build_caverns_glow_mushroom():
    o = []
    for x, y, h, r in ((0, 0, 0.7, 0.32), (0.4, 0.1, 0.45, 0.22), (-0.3, 0.15, 0.35, 0.18)):
        o.append(rcyl(r * 0.25, h, loc=(x, y, 0), z0=0.0, m=M("#E8E0F2"), verts=8, bevel=0.0))
        o.append(lathe([(r, h - 0.02), (r * 0.95, h + r * 0.25), (r * 0.5, h + r * 0.45), (0.0, h + r * 0.5)], 10, [E("#7FE8FF", 1.6)], cap_bot=True, loc=(x, y, 0), name="cap"))
    return root(o)


# ======================================================================== 16 farm
def build_farm_barn_front():
    o = [rbox((1.5, 0.35, 1.15), loc=(0, 0, 0.575), m=M("#C0392B"), r=0.02, segs=1)]
    o.append(prism([(-0.82, 0), (0.82, 0), (0, 0.6)], 0.4, M("#A93226"), loc=(0, 0.2, 1.15), rot=(90, 0, 0), side_m=M("white")))
    o.append(rbox((0.7, 0.04, 0.8), loc=(0, -0.19, 0.42), m=M("white"), r=0.0, segs=1))
    o.append(rbox((0.6, 0.05, 0.7), loc=(0, -0.2, 0.42), m=M("#A93226"), r=0.0, segs=1))
    for s in (-1, 1):
        o.append(tube([(-0.3, -0.23, 0.07 if s > 0 else 0.77), (0.3, -0.23, 0.77 if s > 0 else 0.07)], 0.03, M("white"), seg=4))
    o.append(rbox((0.3, 0.04, 0.24), loc=(0, -0.19, 1.3), m=M("white"), r=0.0, segs=1))
    return root(o)


def build_farm_haystack():
    o = [ball(0.62, loc=(0, 0, 0), m=M("#EDCB6A"), seg=12, rings=6, scale=(1, 1, 0.9), jitter=0.06, seed=2, flat_bottom=0.0)]
    o.append(rcyl(0.02, 0.6, loc=(0.2, -0.1, 0), z0=0.4, m=M("wood"), verts=4, bevel=0.0, rot=(25, 0, 0)))  # pitchfork handle
    return root(o)


def build_farm_apple_crate():
    o = crate((0.6, 0.42, 0.32), (0, 0, 0.16), "#D9A066")
    for k in range(9):
        o.append(ball(0.07, loc=(-0.2 + (k % 3) * 0.2, -0.13 + (k // 3) * 0.13, 0.34), m=M("red" if k % 4 else "lettuce"), seg=6, rings=4))
    o += crate((0.5, 0.38, 0.28), (0.5, 0.05, 0.14), "#C98A55")
    return root(o)


# ======================================================================== 17 lunar
def build_lunar_habitat_dome():
    o = [rcyl(0.85, 0.18, loc=(0, 0, 0), z0=0.0, m=M("steel"), verts=16, bevel=0.03)]
    o.append(ball(0.8, loc=(0, 0, 0.18), m=M("#BDEBFF"), seg=14, rings=8, flat_bottom=0.0))
    o.append(rbox((0.42, 0.42, 0.5), loc=(0, -0.8, 0.25), m=M("steel"), r=0.08, segs=1))
    o.append(rbox((0.24, 0.05, 0.36), loc=(0, -1.02, 0.2), m=E("#7FE8FF", 1.2), r=0.04, segs=1))
    o.append(ball(0.25, loc=(0.1, 0.05, 0.5), m=M("#4CB86A"), seg=8, rings=5))  # garden inside
    return root(o)


def build_lunar_moon_rover():
    o = [rbox((1.0, 0.6, 0.25), loc=(0, 0, 0.42), m=M("white"), r=0.04, segs=1)]
    for x in (-0.38, 0.0, 0.38):
        for s in (-1, 1):
            o += wheel(x, s * 0.36, 0.2, 0.16, 0.1)
    o.append(rcyl(0.02, 0.35, loc=(-0.3, 0.15, 0), z0=0.55, m=M("steel_dark"), verts=4, bevel=0.0))
    o.append(lathe([(0.0, 0.9), (0.2, 0.96), (0.22, 1.0)], 10, [M("steel")], loc=(-0.3, 0.15, 0), name="dish"))
    o.append(rbox((0.3, 0.3, 0.12), loc=(0.25, 0, 0.6), m=M("mustard"), r=0.02, segs=1))
    o.append(rbox((0.55, 0.4, 0.02), loc=(0.1, 0.0, 0.67), m=E("#3D8BFF", 0.5), r=0.0, segs=1))  # solar panel
    return root(o)


# ======================================================================== 18 lagoon
def build_lagoon_floating_lantern():
    o = [rcyl(0.6, 0.04, loc=(0, 0, 0), z0=0.0, m=M("#2E5E8A"), verts=14, bevel=0.0)]
    for k, (x, y, s) in enumerate(((0, 0, 1.0), (0.32, 0.2, 0.7), (-0.3, 0.18, 0.6))):
        prof = [(0.12 * s, 0.04), (0.2 * s, 0.1 * s + 0.04), (0.2 * s, 0.32 * s + 0.04), (0.12 * s, 0.4 * s + 0.04)]
        o.append(lathe(prof, 8, [E("#FFB347", 1.8)], cap_bot=True, cap_top=True, loc=(x, y, 0), name="lantern"))
    o.append(rcyl(0.03, 1.4, loc=(0.5, -0.3, 0), z0=0.0, m=M("wood_dark"), verts=5, bevel=0.0))
    o.append(lathe([(0.1, 1.2), (0.14, 1.28), (0.12, 1.42)], 8, [E("#FF6F5A", 1.6)], cap_bot=True, cap_top=True, loc=(0.5, -0.3, 0), name="hang"))
    return root(o)


def build_lagoon_canoe():
    hull = [(-0.95, 0.12), (-0.6, 0.0), (0.6, 0.0), (0.95, 0.12), (0.95, 0.22), (-0.95, 0.22)]
    o = [prism(hull, 0.36, M("wood"), loc=(0, 0.18, 0), rot=(90, 0, 0), side_m=M("wood_dark"))]
    o.append(rbox((1.6, 0.26, 0.03), loc=(0, 0, 0.12), m=M("wood_dark"), r=0.0, segs=1))
    for x in (-0.4, 0.4):
        o.append(rbox((0.08, 0.34, 0.04), loc=(x, 0, 0.2), m=M("cream"), r=0.0, segs=1))
    o.append(tube([(-0.2, -0.3, 0.02), (0.5, 0.25, 0.4)], 0.025, M("wood_dark"), seg=4))  # paddle
    o.append(rbox((0.12, 0.03, 0.26), loc=(-0.24, -0.32, 0.05), m=M("wood"), r=0.02, segs=1))
    return root(o)


# ======================================================================== 19 amber
def build_amber_maple_tree():
    o = tree_blob(1.2, 5, "#E8742A", seed=5, r=0.6)
    o.append(ball(0.3, loc=(0.35, 0.2, 1.6), m=M("#C0392B"), seg=8, rings=5, jitter=0.1, seed=11))
    return root(o)


def build_amber_leaf_pile():
    o = []
    for k, (x, y, r, c) in enumerate(((0, 0, 0.45, "#E8742A"), (0.35, 0.1, 0.3, "#C0392B"), (-0.32, 0.12, 0.28, "#FFB13C"))):
        o.append(ball(r, loc=(x, y, 0), m=M(c), seg=9, rings=5, scale=(1, 1, 0.5), jitter=0.12, seed=k + 3, flat_bottom=0.0))
    o.append(tube([(0.5, -0.3, 0), (0.55, -0.32, 0.9)], 0.02, M("wood_dark"), seg=4))  # rake handle
    o.append(rbox((0.3, 0.04, 0.05), loc=(0.5, -0.3, 0.03), m=M("darkgray"), r=0.0, segs=1))
    return root(o)


# ======================================================================== 20 gearworks
def gear(r, teeth, depth, loc, rot, col):
    pts = []
    for i in range(teeth * 2):
        rr = r if i % 2 == 0 else r * 0.82
        a0 = TAU * i / (teeth * 2)
        pts.append((rr * math.cos(a0), rr * math.sin(a0)))
        pts.append((rr * math.cos(a0 + TAU / (teeth * 4)), rr * math.sin(a0 + TAU / (teeth * 4))))
    return prism(pts, depth, M(col), loc=loc, rot=rot, bevel=0.0)


def build_gearworks_giant_gear():
    o = [rbox((0.6, 0.4, 0.2), loc=(0, 0, 0.1), m=M("darkgray"), r=0.03, segs=1)]
    o.append(gear(0.85, 12, 0.14, (0, 0.07, 1.0), (90, 0, 0), "#B8862B"))
    o.append(gear(0.45, 8, 0.12, (0.75, -0.08, 0.55), (90, 0, 0), "#8B5A2B"))
    o.append(rcyl(0.14, 0.24, loc=(0, 0, 1.0), m=M("steel_dark"), verts=10, bevel=0.0, rot=(90, 0, 0)))
    for x in (-0.2, 0.2):
        o.append(rbox((0.08, 0.1, 0.85), loc=(x, 0.12, 0.6), m=M("darkgray"), r=0.0, segs=1))
    return root(o)


def build_gearworks_chimney_stack():
    o = [rbox((0.7, 0.6, 0.5), loc=(0, 0, 0.25), m=M("#8E4A3A"), r=0.02, segs=1)]
    for x, h in ((-0.15, 1.9), (0.2, 1.5)):
        o.append(lathe([(0.17, 0.5), (0.15, h), (0.19, h + 0.02), (0.19, h + 0.12)], 10, [M("#9A5040"), M("darkgray")], band_mat=[0, 0, 1], loc=(x, 0, 0), name="chimney"))
        o.append(ball(0.16, loc=(x, 0, h + 0.3), m=M("#E6E6EA"), seg=7, rings=5, jitter=0.15, seed=int(h * 10)))
    o.append(rcyl(0.12, 0.04, loc=(0, -0.31, 0.3), m=M("gold"), verts=10, bevel=0.0, rot=(90, 0, 0)))  # pressure gauge
    return root(o)


# ======================================================================== 21 glade
def build_glade_giant_mushroom():
    o = [lathe([(0.16, 0.0), (0.13, 0.6), (0.14, 1.0)], 10, [M("#F4EEE6")], cap_bot=True, name="stem")]
    o.append(lathe([(0.75, 0.95), (0.7, 1.15), (0.45, 1.4), (0.0, 1.5)], 14, [M("#E94B3C")], cap_bot=True, name="cap"))
    for k in range(7):
        a = TAU * k / 7
        o.append(ball(0.07, loc=(math.cos(a) * 0.45, math.sin(a) * 0.45, 1.27), m=E("#FFF6D0", 1.2), seg=6, rings=3, scale=(1, 1, 0.4)))
    o.append(rbox((0.12, 0.03, 0.2), loc=(0, -0.15, 0.25), m=M("wood_dark"), r=0.04, segs=1))  # tiny door
    return root(o)


def build_glade_toadstool_stool():
    o = []
    for x, y in ((-0.3, 0), (0.3, 0.05)):
        o.append(rcyl(0.09, 0.4, loc=(x, y, 0), z0=0.0, m=M("#F4EEE6"), verts=8, bevel=0.0))
        o.append(lathe([(0.26, 0.38), (0.24, 0.46), (0.0, 0.52)], 10, [M("lilac")], cap_bot=True, loc=(x, y, 0), name="seat"))
    o.append(ball(0.08, loc=(0, -0.25, 0.04), m=E("#B6FF8A", 1.6), seg=6, rings=4))  # firefly jar glow
    return root(o)


# ======================================================================== 22 springs
def build_springs_bamboo_grove():
    o = []
    for k, (x, y, h) in enumerate(((0, 0, 2.2), (0.25, 0.15, 1.8), (-0.22, 0.12, 1.95), (0.1, -0.2, 1.5))):
        o.append(rcyl(0.06, h, loc=(x, y, 0), z0=0.0, m=M("#6CC24A"), verts=6, bevel=0.0))
        for z in [0.45 * i for i in range(1, int(h / 0.45) + 1)]:
            o.append(rcyl(0.07, 0.03, loc=(x, y, 0), z0=z, m=M("#3E9B34"), verts=6, bevel=0.0))
        a = 0.7 * k
        o.append(leaf([(x, y, h - 0.2), (x + math.cos(a) * 0.25, y + math.sin(a) * 0.25, h)], [0.0, 0.06], M("#4CB86A")))
    return root(o)


def build_springs_stone_lantern():
    stone = M("#A7A39A")
    o = [rbox((0.5, 0.5, 0.12), loc=(0, 0, 0.06), m=stone, r=0.03, segs=1)]
    o.append(rcyl(0.1, 0.6, loc=(0, 0, 0), z0=0.12, m=stone, verts=6, bevel=0.0))
    o.append(rbox((0.42, 0.42, 0.1), loc=(0, 0, 0.77), m=stone, r=0.02, segs=1))
    o.append(rbox((0.3, 0.3, 0.26), loc=(0, 0, 0.95), m=stone, r=0.02, segs=1))
    o.append(rbox((0.18, 0.32, 0.16), loc=(0, 0, 0.95), m=E("#FFC76A", 1.8), r=0.0, segs=1))
    o.append(lathe([(0.34, 1.08), (0.36, 1.12), (0.0, 1.36)], 6, [stone], cap_bot=True, a0=45, name="roof"))
    return root(o)


# ======================================================================== 23 frostfest
def build_frostfest_festive_pine():
    o = [rcyl(0.08, 0.3, loc=(0, 0, 0), z0=0.0, m=M("wood_dark"), verts=6, bevel=0.0)]
    for k, (r, z) in enumerate(((0.7, 0.25), (0.55, 0.75), (0.38, 1.2))):
        o.append(lathe([(r, z), (0.0, z + 0.65)], 10, [M("#2E7D4F")], cap_bot=True, name="tier"))
    cols = ["red", "mustard", "sky", "pink"]
    for k in range(10):
        a = TAU * k / 10 * 1.618
        z = 0.4 + (k % 5) * 0.28
        rr = 0.62 - (z - 0.25) * 0.4
        o.append(ball(0.05, loc=(math.cos(a) * rr, math.sin(a) * rr, z), m=E(cols[k % 4], 1.4), seg=6, rings=4))
    o.append(prism(kit.star_pts(5, 0.14, 0.06), 0.04, E("#FFD84A", 2.0), loc=(0, 0.02, 1.95), rot=(90, 0, 0)))
    return root(o)


def build_frostfest_gift_stack():
    o = []
    for (w, d, h, x, y, z, c, rib) in ((0.5, 0.5, 0.4, 0, 0, 0, "red", "gold"), (0.4, 0.4, 0.32, 0.05, 0.02, 0.4, "green", "white"),
                                       (0.3, 0.3, 0.26, 0.0, 0.0, 0.72, "blue", "pink"), (0.34, 0.3, 0.3, 0.45, -0.1, 0.0, "lilac", "mustard")):
        o.append(rbox((w, d, h), loc=(x, y, z + h / 2), m=M(c), r=0.02, segs=1))
        o.append(rbox((w + 0.01, 0.06, h + 0.01), loc=(x, y, z + h / 2), m=M(rib), r=0.0, segs=1))
        o.append(rbox((0.06, d + 0.01, h + 0.01), loc=(x, y, z + h / 2), m=M(rib), r=0.0, segs=1))
    o.append(ball(0.07, loc=(0, 0, 1.0), m=M("gold"), seg=6, rings=4, scale=(1.4, 1, 0.8)))
    return root(o)


# ======================================================================== 24 fossil
def build_fossil_fern_clump():
    o = []
    for k in range(7):
        a = TAU * k / 7
        tip = (math.cos(a) * 0.75, math.sin(a) * 0.75, 0.55 + (k % 2) * 0.2)
        mid = (math.cos(a) * 0.35, math.sin(a) * 0.35, 0.75)
        o.append(leaf([(0, 0, 0.05), mid, tip], [0.02, 0.16, 0.0], M("#4E9A3A" if k % 2 else "#6CC24A")))
    o.append(ball(0.15, loc=(0, 0, 0), m=M("#6B5A3A"), seg=6, rings=4, flat_bottom=0.0))
    return root(o)


def build_fossil_dino_statue():
    stone = M("#9FB46A")
    o = [rbox((1.3, 0.6, 0.18), loc=(0, 0, 0.09), m=M("#8E9A55"), r=0.04, segs=1)]
    o.append(ball(0.42, loc=(0, 0, 0.62), m=stone, seg=10, rings=6, scale=(1.4, 0.9, 0.8)))
    for x in (-0.35, 0.35):
        for s in (-1, 1):
            o.append(rcyl(0.1, 0.45, loc=(x, s * 0.18, 0), z0=0.18, m=stone, verts=8, bevel=0.0))
    o.append(tube([(0.45, 0, 0.75), (0.65, 0, 1.15), (0.75, 0, 1.55)], 0.11, stone, seg=7, radii=[0.16, 0.11, 0.09]))
    o.append(ball(0.16, loc=(0.82, 0, 1.6), m=stone, seg=8, rings=5, scale=(1.3, 1, 0.9)))
    for s in (-1, 1):
        o.append(ball(0.03, loc=(0.92, s * 0.08, 1.66), m=M("black"), seg=5, rings=3))
    o.append(tube([(-0.5, 0, 0.6), (-0.85, 0, 0.42), (-1.05, 0, 0.3)], 0.09, stone, seg=6, radii=[0.14, 0.08, 0.03]))
    return root(o)


# ======================================================================== 25 capital
def build_capital_fountain():
    o = [lathe([(0.8, 0.0), (0.82, 0.05), (0.82, 0.32), (0.72, 0.34), (0.72, 0.2), (0.0, 0.2)], 18, [M("#F4EEE6")], cap_bot=True, name="basin")]
    o.append(rcyl(0.73, 0.02, loc=(0, 0, 0), z0=0.28, m=M("#8FD8FF"), verts=18, bevel=0.0))
    o.append(rcyl(0.1, 0.7, loc=(0, 0, 0), z0=0.2, m=M("gold"), verts=10, bevel=0.0))
    o.append(lathe([(0.35, 0.9), (0.38, 0.95), (0.3, 1.0), (0.0, 0.98)], 14, [M("gold")], cap_bot=True, name="bowl"))
    o.append(lathe([(0.04, 1.0), (0.12, 1.25), (0.0, 1.35)], 8, [E("#BDEBFF", 1.0)], name="jet"))
    return root(o)


def build_capital_golden_burger():
    o = [rbox((0.7, 0.7, 0.6), loc=(0, 0, 0.3), m=M("#F4EEE6"), r=0.04, segs=1)]
    o.append(rbox((0.78, 0.78, 0.08), loc=(0, 0, 0.64), m=M("gold_dark"), r=0.02, segs=1))
    gold, dark = M("gold"), M("gold_dark")
    z = 0.68
    o.append(lathe([(0.28, z), (0.32, z + 0.06), (0.30, z + 0.13)], 12, [gold], cap_bot=True, name="b1"))
    o.append(lathe([(0.33, z + 0.12), (0.35, z + 0.17), (0.33, z + 0.24)], 12, [dark], name="b2"))
    o.append(lathe([(0.32, z + 0.23), (0.34, z + 0.29), (0.28, z + 0.42), (0.15, z + 0.5), (0.0, z + 0.52)], 12, [gold], name="b3"))
    o.append(rbox((0.4, 0.04, 0.12), loc=(0, -0.36, 0.35), m=M("gold"), r=0.02, segs=1))  # plaque
    return root(o)


PROP = {"ao": 0.35, "ao_strength": 0.8}
MODELS = {f"region_{n.split('build_', 1)[1]}": (fn, 1500, PROP) for n, fn in sorted(globals().items())
          if n.startswith("build_") and callable(fn)}

if __name__ == "__main__":
    kit.run(MODELS)
