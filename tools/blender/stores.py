"""
Store signature props (ASSET_CONTRACT §7): models/store_<store>_<prop>.bvm
One part `root`, pivot at the bottom centre, front = -Y, <= 1,500 tris each.

    Blender -b --factory-startup --python tools/blender/stores.py [-- store_diner_* ...]
"""
import os
import sys
import math
import random

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from kit import M, T, E, B, lathe, prism, tube, rbox, rcyl, ball, leaf, join, move  # noqa: E402
from mathutils import Vector, Matrix  # noqa: E402
from stations import striped_awning  # noqa: E402
from env import wheel, burger_shapes, neon_loop  # noqa: E402

TAU = 2 * math.pi


def root(objs):
    r = join(objs, "root", (0, 0, 0))
    kit.clamp_floor([r])
    return [r]


def orient(o, loc, direction, spin=0.0):
    """Rotate an object built along +Z so its +Z points along `direction`, then move to loc."""
    q = Vector(direction).normalized().to_track_quat('Z', 'Y')
    o.data.transform(Matrix.Translation(Vector(loc)) @ q.to_matrix().to_4x4() @ Matrix.Rotation(spin, 4, 'Z'))
    o.data.update()
    return o


def rock(r, loc, col="gray", seed=1, seg=7, rings=5, scale=(1, 1, 0.7), jitter=0.16):
    return ball(r, loc=loc, m=M(col), seg=seg, rings=rings, jitter=jitter, seed=seed, scale=scale, angle=35)


# ===========================================================================
# wagon (Bun Wagon, sunny park)
# ===========================================================================
def build_wagon_truck():
    """Food truck: 4.5 long (X) x 2.0 deep (Y), serving hatch facing -Y."""
    o = []
    # box body + cab (cream), tint stripes
    o.append(rbox((3.30, 1.90, 1.95), loc=(0.55, 0, 0), z0=0.45, m=M("cream"), r=0.22, segs=2))
    o.append(rbox((1.15, 1.84, 1.35), loc=(-1.62, 0, 0), z0=0.45, m=M("cream"), r=0.26, segs=2))
    o.append(rbox((3.34, 1.94, 0.16), loc=(0.55, 0, 0), z0=0.62, m=T(), r=0.0, segs=1))
    o.append(rbox((3.34, 1.94, 0.12), loc=(0.55, 0, 0), z0=2.18, m=T(), r=0.0, segs=1))
    o.append(rbox((1.19, 1.88, 0.16), loc=(-1.62, 0, 0), z0=0.62, m=T(), r=0.0, segs=1))
    # cab glass
    o.append(rbox((0.06, 1.40, 0.42), loc=(-2.205, 0, 1.40), m=M("glass"), r=0.0, segs=1))
    for s in (-1, 1):
        o.append(rbox((0.62, 0.06, 0.38), loc=(-1.62, s * 0.93, 1.40), m=M("glass"), r=0.0, segs=1))
    # serving hatch (-Y side): dark opening, wood counter, striped awning (propped hatch)
    o.append(rbox((2.00, 0.06, 0.80), loc=(0.70, -0.935, 1.55), m=M("cocoa"), r=0.0, segs=1))
    o.append(rbox((2.10, 0.30, 0.07), loc=(0.70, -1.0, 1.12), m=M("wood"), r=0.03, segs=1))
    o += striped_awning(-0.35, 1.75, 5, -0.96, -1.55, 2.12, 2.00, drop=0.10)
    # little menu blocks inside the hatch
    for i, c in enumerate(("mustard", "mint", "tomato")):
        o.append(rbox((0.36, 0.03, 0.22), loc=(0.15 + i * 0.55, -0.95, 1.65), m=M(c), r=0.0, segs=1))
    # bumpers + lights
    o.append(rbox((0.16, 1.92, 0.18), loc=(-2.22, 0, 0.55), m=M("offwhite"), r=0.06, segs=1))
    o.append(rbox((0.16, 1.92, 0.18), loc=(2.24, 0, 0.55), m=M("offwhite"), r=0.06, segs=1))
    for s in (-1, 1):
        o.append(rcyl(0.11, 0.05, loc=(-2.21, s * 0.62, 0.82), m=E("#FFF1C2", 2.0), verts=8, bevel=0,
                      rot=(0, 90, 0)))
        o.append(rbox((0.05, 0.14, 0.10), loc=(2.215, s * 0.70, 0.82), m=E("red", 1.5), r=0, segs=1))
    # wheels (y-facing axles)
    for x in (-1.55, 1.40):
        for s in (-1, 1):
            t = rcyl(0.40, 0.30, loc=(x, s * 0.82, 0.40), m=M("black"), verts=10, bevel=0.08, segs=1,
                     rot=(90, 0, 0), wn=False, angle=60)
            h = rcyl(0.20, 0.03, loc=(x, s * 0.975, 0.40), m=M("offwhite"), verts=8, bevel=0, rot=(90, 0, 0))
            o += [t, h]
    # roof burger
    zb = 2.40
    bx = 0.55
    o.append(rcyl(0.06, 0.12, loc=(bx, 0, 0), z0=zb, m=M("steel_dark"), verts=4, bevel=0))
    zb += 0.10
    o.append(lathe([(0.42, zb), (0.48, zb + 0.06), (0.46, zb + 0.15), (0.42, zb + 0.17)], 10, [M("bun")],
                   cap_bot=True, loc=(bx, 0, 0), name="b1"))
    o.append(lathe([(0.48, zb + 0.16), (0.53, zb + 0.20), (0.53, zb + 0.28), (0.48, zb + 0.31)], 10,
                   [M("patty")], loc=(bx, 0, 0), name="b2"))
    o.append(lathe([(0.52, zb + 0.29), (0.56, zb + 0.32), (0.52, zb + 0.35)], 8, [M("lettuce")], loc=(bx, 0, 0),
                   name="b3"))
    o.append(lathe([(0.48, zb + 0.33), (0.50, zb + 0.38), (0.44, zb + 0.52), (0.26, zb + 0.63), (0.0, zb + 0.66)],
                   10, [M("bun_top")], loc=(bx, 0, 0), name="b4"))
    return root(o)


def build_wagon_balloon_stand():
    o = [lathe([(0.26, 0.0), (0.27, 0.04), (0.22, 0.10), (0.10, 0.14), (0.0, 0.15)], 12, [M("tomato")],
               cap_bot=True, name="base")]
    o.append(rcyl(0.03, 1.1, loc=(0, 0, 0), z0=0.12, m=M("white"), verts=8, bevel=0))
    o.append(rcyl(0.05, 0.05, loc=(0, 0, 0), z0=1.20, m=M("mustard"), verts=8, bevel=0.01))
    top = Vector((0, 0, 1.24))
    cols = ["tomato", "mustard", "mint", "sky", "pink", "lilac", "orange"]
    spots = [(-0.30, -0.10, 2.05), (0.05, -0.22, 2.30), (0.34, -0.05, 2.08), (-0.10, 0.22, 2.42),
             (0.22, 0.26, 2.20), (-0.36, 0.20, 1.92), (0.08, 0.02, 1.86)]
    for c, (x, y, z) in zip(cols, spots):
        r = 0.19 if z > 2.0 else 0.17
        p = Vector((x, y, z))
        b = ball(r, loc=(0, 0, 0), m=M(c), seg=10, rings=6, scale=(1, 1, 1.18))
        knot = rcyl(0.035, 0.05, loc=(0, 0, -r * 1.18 - 0.01), m=M(c), verts=6, bevel=0, r2=0.012)
        d = (p - top).normalized()
        for ob in (b, knot):
            orient(ob, p, d)
        o += [b, knot]
        tail = p - d * (r * 1.18 + 0.03)
        o.append(tube([tuple(top), tuple(top + (tail - top) * 0.5 + Vector((0.02, 0, 0))), tuple(tail)], 0.006,
                      M("offwhite"), seg=3, caps=False))
    return root(o)


def build_wagon_picnic_table():
    o = []
    wood, dark = M("wood"), M("wood_dark")
    # A-frame legs
    for x in (-0.68, 0.68):
        for s in (-1, 1):
            o.append(rbox((0.07, 0.09, 0.86), loc=(x, s * 0.30, 0.38), m=dark, r=0.02, segs=1, rot=(s * 33, 0, 0)))
        o.append(rbox((0.07, 1.50, 0.08), loc=(x, 0, 0.42), m=dark, r=0.02, segs=1))
    # table top planks + checker cloth
    for i in range(4):
        o.append(rbox((1.80, 0.19, 0.05), loc=(0, -0.30 + i * 0.20, 0.74), m=wood, r=0.02, segs=1))
    import bmesh
    bm = bmesh.new()
    nx, ny = 6, 4
    w, d, z = 1.10, 0.84, 0.775
    for iy in range(ny):
        for ix in range(nx):
            x0 = -w / 2 + w * ix / nx
            y0 = -d / 2 + d * iy / ny
            vs = [bm.verts.new((x0, y0, z)), bm.verts.new((x0 + w / nx, y0, z)),
                  bm.verts.new((x0 + w / nx, y0 + d / ny, z)), bm.verts.new((x0, y0 + d / ny, z))]
            f = bm.faces.new(vs)
            f.material_index = (ix + iy) % 2
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    cloth = kit._mk(bm, "cloth", [M("tomato"), M("white")])
    o.append(kit.finish(cloth, angle=None))
    # hanging cloth flaps front/back
    for s in (-1, 1):
        o.append(rbox((1.10, 0.012, 0.14), loc=(0, s * (d / 2 + 0.004), 0.71), m=M("tomato"), r=0, segs=1))
    # benches
    for s in (-1, 1):
        o.append(rbox((1.80, 0.26, 0.05), loc=(0, s * 0.62, 0.43), m=wood, r=0.02, segs=1))
    # a soda cup + basket of fries on the cloth
    o.append(lathe([(0.04, 0.78), (0.05, 0.90), (0.054, 0.92), (0.0, 0.925)], 10, [M("mint")], cap_bot=True,
                   loc=(0.30, 0.10, 0), name="cup"))
    o.append(rbox((0.16, 0.12, 0.06), loc=(-0.25, -0.08, 0.81), m=M("fries_box"), r=0.02, segs=1))
    for k in range(4):
        o.append(rbox((0.02, 0.02, 0.10), loc=(-0.29 + k * 0.026, -0.08, 0.86), m=M("fries"), r=0, segs=1,
                      rot=(0, (k - 1.5) * 9, 0)))
    return root(o)


# ===========================================================================
# seaside
# ===========================================================================
SAND = "#F3D9A4"


def build_seaside_surfboard():
    o = [ball(0.40, loc=(0, 0.05, 0.0), m=M(SAND), seg=10, rings=5, scale=(1.0, 0.8, 0.35), flat_bottom=0.0,
              jitter=0.04)]
    outline = []
    n = 12
    for k in range(n + 1):  # right edge from tail to nose
        t = k / n
        z = 1.95 * t
        wdt = 0.26 * math.sin(math.pi * (0.12 + 0.88 * t)) ** 0.7 if t < 0.97 else 0.02
        outline.append((wdt, z))
    pts = outline + [(-x, z) for x, z in reversed(outline)]
    pts = kit.ccw(pts)
    board = prism(pts, 0.07, M("mint"), loc=(0, 0.035, 0.0), rot=(90, 0, 0), bevel=0.03, segs=2, wn=False,
                  angle=60)
    stripe = prism([(x * 0.22, z) for x, z in pts], 0.08, M("tomato"), loc=(0, 0.04, 0.0), rot=(90, 0, 0),
                   wn=False)
    fin = prism([(0, 0.12), (0, 0.36), (0.14, 0.14)], 0.025, M("navy"), loc=(0.0125, 0.035, 0.0), rot=(90, 0, 90),
                wn=False)
    move([fin], (0, 0, 0), rot=(0, 0, 0))
    for ob in (board, stripe, fin):
        move([ob], (0, 0, -0.18), rot=(0, 0, 0))
        move([ob], (0, 0, 0), rot=(-9, 6, 0))
    o += [board, stripe]
    o.append(rbox((0.30, 0.20, 0.06), loc=(0.42, -0.25, 0.03), m=M("sky"), r=0.03, segs=1, rot=(0, 0, 25)))
    return root(o)


def build_seaside_lifeguard_tower():
    o = []
    for sx in (-1, 1):
        for sy in (-1, 1):
            o.append(rbox((0.10, 0.10, 1.60), loc=(sx * 0.55, sy * 0.50, 0.80), m=M("white"), r=0.03, segs=1,
                          rot=(sy * -4, sx * 4, 0)))
    for z in (0.55,):
        for s in (-1, 1):
            o.append(rbox((1.10, 0.05, 0.07), loc=(0, s * 0.50, z), m=M("white"), r=0.02, segs=1))
            o.append(rbox((0.05, 1.00, 0.07), loc=(s * 0.55, 0, z), m=M("white"), r=0.02, segs=1))
    o.append(rbox((1.50, 1.40, 0.10), loc=(0, 0, 1.62), m=M("wood"), r=0.03, segs=1))
    # hut
    o.append(rbox((1.10, 0.90, 0.90), loc=(0, 0.18, 0), z0=1.67, m=M("tomato"), r=0.06, segs=2))
    o.append(rbox((1.12, 0.92, 0.22), loc=(0, 0.18, 0), z0=1.67, m=M("white"), r=0.05, segs=1))
    o.append(rbox((0.70, 0.04, 0.40), loc=(0, -0.275, 2.20), m=M("cocoa"), r=0.04, segs=1))
    # roof
    o.append(prism([(-0.75, 0.0), (0.75, 0.0), (0.0, 0.42)], 1.20, M("tomato"), loc=(0, 0.78, 2.55),
                   rot=(90, 0, 0), bevel=0.04, segs=1, wn=False))
    o.append(prism([(-0.80, 0.0), (0.80, 0.0), (0.0, 0.45)], 0.08, M("white"), loc=(0, -0.42, 2.53),
                   rot=(90, 0, 0), bevel=0.02, segs=1, wn=False))
    # railing on the front deck
    for x in (-0.70, 0.0, 0.70):
        o.append(rcyl(0.025, 0.40, loc=(x, -0.66, 0), z0=1.66, m=M("white"), verts=6, bevel=0))
    o.append(rbox((1.44, 0.05, 0.05), loc=(0, -0.66, 2.07), m=M("white"), r=0.02, segs=1))
    # ladder
    for s in (-1, 1):
        o.append(rbox((0.05, 0.06, 1.85), loc=(s * 0.22, -1.0, 0.85), m=M("white"), r=0.02, segs=1, rot=(-18, 0, 0)))
    for k in range(5):
        z = 0.25 + k * 0.30
        y = -1.0 + (z - 0.85) * math.tan(math.radians(18))
        o.append(rbox((0.44, 0.05, 0.035), loc=(0, y, z), m=M("wood"), r=0.012, segs=1))
    # lifebuoy
    buoy = B.torus(0.16, 0.052, loc=(0, 0, 0), m=M("tomato"), maj=12, minr=6)
    buoy.data.materials.append(M("white"))
    for p in buoy.data.polygons:
        c = p.center
        if (math.atan2(c.y, c.x) % (TAU / 4)) < TAU / 8:
            p.material_index = 1
    kit.finish(buoy, angle=70, rot=(90, 0, 0), loc=(0.42, -0.30, 2.05))
    # flag
    o.append(rcyl(0.02, 0.9, loc=(0.48, 0.55, 0), z0=2.70, m=M("white"), verts=6, bevel=0))
    o.append(prism([(0, 0), (0.42, 0.12), (0, 0.26)], 0.02, M("mustard"), loc=(0.49, 0.56, 3.30), rot=(90, 0, 0),
                   wn=False))
    return root(o + [buoy])


def build_seaside_beach_chair():
    o = []
    wood = M("wood")
    for s in (-1, 1):
        x = s * 0.30
        o.append(rbox((0.05, 0.05, 1.10), loc=(x, 0.05, 0.40), m=wood, r=0.015, segs=1, rot=(-55, 0, 0)))
        o.append(rbox((0.05, 0.05, 0.80), loc=(x, -0.18, 0.33), m=wood, r=0.015, segs=1, rot=(38, 0, 0)))
        o.append(rbox((0.05, 0.62, 0.05), loc=(x, -0.06, 0.48), m=wood, r=0.015, segs=1))
    o.append(rbox((0.62, 0.04, 0.04), loc=(0, -0.43, 0.06), m=wood, r=0.012, segs=1))
    o.append(rbox((0.62, 0.04, 0.04), loc=(0, 0.48, 0.06), m=wood, r=0.012, segs=1))
    # striped sling: curved strip from the front rail up to the backrest
    path = [(-0.40, 0.42), (-0.22, 0.30), (0.02, 0.26), (0.20, 0.40), (0.34, 0.70), (0.42, 0.98)]
    rings = []
    for (y, z) in path:
        rings.append([(-0.27, y, z), (0.27, y, z), (0.27, y + 0.012, z + 0.02), (-0.27, y + 0.012, z + 0.02)])
    import bmesh
    stripes = 6
    xs = [-0.27 + 0.54 * k / stripes for k in range(stripes + 1)]
    bm = bmesh.new()
    grid = []
    for (y, z) in path:
        grid.append([bm.verts.new((x, y, z)) for x in xs])
    for i in range(len(path) - 1):
        for k in range(stripes):
            f = bm.faces.new((grid[i][k], grid[i][k + 1], grid[i + 1][k + 1], grid[i + 1][k]))
            f.material_index = k % 2
    sling = kit._mk(bm, "sling", [M("teal"), M("white")])
    sol = sling.modifiers.new("Sol", 'SOLIDIFY')
    sol.thickness = 0.02
    o.append(kit.finish(sling, angle=40))
    o.append(rbox((0.40, 0.10, 0.10), loc=(0, 0.36, 0.92), m=M("mustard"), r=0.045, segs=2, rot=(-60, 0, 0)))
    return root(o)


# ===========================================================================
# diner (retro neon 50s)
# ===========================================================================
def build_diner_jukebox():
    o = []
    W, D = 0.84, 0.50
    arch = [(-W / 2, 0.0), (W / 2, 0.0)] + kit.arc_pts(0, 1.08, W / 2, 0.42, 0, 180, 10)
    arch = kit.ccw(arch)
    o.append(prism(arch, D, M("wood_dark"), loc=(0, D / 2, 0), rot=(90, 0, 0), bevel=0.04, segs=2, wn=True))
    # chrome base + front panel
    o.append(rbox((W + 0.04, D + 0.04, 0.10), loc=(0, 0, 0), z0=0.0, m=M("steel"), r=0.035, segs=1))
    o.append(rbox((0.60, 0.04, 0.40), loc=(0, -D / 2 - 0.005, 0.40), m=M("gold_dark"), r=0.03, segs=1))
    for k in range(5):
        o.append(rbox((0.54, 0.03, 0.025), loc=(0, -D / 2 - 0.03, 0.26 + k * 0.07), m=M("gold"), r=0.008,
                      segs=1))
    # record window (glass) + record
    o.append(prism(kit.arc_pts(0, 0, 0.27, 0.27, 0, 180, 8), 0.04, M("glass"), loc=(0, -D / 2 - 0.0, 0.80),
                   rot=(90, 0, 0), wn=False))
    o.append(rcyl(0.14, 0.012, loc=(0, -D / 2 - 0.045, 0.88), m=M("black"), verts=12, bevel=0, rot=(90, 0, 0)))
    o.append(rcyl(0.05, 0.016, loc=(0, -D / 2 - 0.047, 0.88), m=M("tomato"), verts=8, bevel=0, rot=(90, 0, 0)))
    # neon arches (emissive) following the arch outline
    for k, (col, inset) in enumerate((("pink", 0.03), ("mustard", 0.10), ("teal", 0.17))):
        pts = [(-W / 2 + inset, 0.62)] + kit.arc_pts(0, 1.08, W / 2 - inset, 0.42 - inset * 0.9, 180, 0, 10) + \
              [(W / 2 - inset, 0.62)]
        o.append(neon_loop(pts, -D / 2 - 0.02, col, r=0.022, seg=5, closed=False))
    # side neon tubes
    for s in (-1, 1):
        o.append(rcyl(0.025, 0.52, loc=(s * (W / 2 - 0.02), -D / 2 - 0.01, 0.36), m=E("pink", 3.0), verts=6,
                      bevel=0))
    o.append(ball(0.05, loc=(0, -0.05, 1.53), m=E("mustard", 3.0), seg=8, rings=5))
    return root(o)


def build_diner_stool():
    o = [lathe([(0.20, 0.0), (0.21, 0.015), (0.17, 0.04), (0.05, 0.06), (0.0, 0.062)], 14, [M("steel")],
               cap_bot=True, name="base")]
    o.append(rcyl(0.035, 0.62, loc=(0, 0, 0), z0=0.05, m=M("steel"), verts=8, bevel=0))
    o.append(kit.finish(B.torus(0.16, 0.014, loc=(0, 0, 0.26), m=M("steel"), maj=14, minr=5), angle=70))
    for a in (0, 120, 240):
        d = Vector((math.cos(math.radians(a)), math.sin(math.radians(a)), 0))
        o.append(tube([(0, 0, 0.26), tuple(d * 0.16 + Vector((0, 0, 0.26)))], 0.01, M("steel"), seg=4))
    o.append(rcyl(0.07, 0.06, loc=(0, 0, 0), z0=0.62, m=M("steel_dark"), verts=10, bevel=0.01))
    o.append(lathe([(0.17, 0.66), (0.20, 0.68), (0.20, 0.72), (0.0, 0.72)], 16, [M("white")], cap_bot=True,
                   name="ring"))
    o.append(lathe([(0.19, 0.715), (0.205, 0.73), (0.19, 0.78), (0.12, 0.80), (0.0, 0.805)], 16, [M("tomato")],
                   name="cushion"))
    return root(o)


def build_diner_neon_star():
    o = [rcyl(0.05, 0.06, loc=(0, 0, 0), z0=0.0, m=M("cocoa"), verts=8, bevel=0.01, r2=0.04)]
    o.append(rbox((0.40, 0.20, 0.06), loc=(0, 0, 0), z0=0.0, m=M("cocoa"), r=0.025, segs=1))
    o.append(rcyl(0.03, 0.62, loc=(0, 0.04, 0), z0=0.05, m=M("steel_dark"), verts=6, bevel=0))
    o.append(rcyl(0.58, 0.06, loc=(0, 0.04, 1.20), m=M("navy"), verts=20, bevel=0.02, rot=(90, 0, 0)))
    star = kit.star_pts(5, 0.48, 0.21, 90)
    o.append(neon_loop([(x, z + 1.20) for x, z in star], -0.015, "mustard", r=0.024))
    star2 = kit.star_pts(5, 0.26, 0.115, 90)
    o.append(neon_loop([(x, z + 1.20) for x, z in star2], -0.015, "pink", r=0.02))
    for k in range(8):
        a = TAU * k / 8 + 0.2
        o.append(ball(0.025, loc=(0.52 * math.cos(a), -0.01, 1.20 + 0.52 * math.sin(a)), m=E("teal", 3.0),
                      seg=6, rings=4))
    return root(o)


# ===========================================================================
# lodge (mountain)
# ===========================================================================
def build_lodge_fireplace():
    o = []
    stones = [M("gray"), M("#B7BEC6"), M("darkgray"), M("#A9A29A")]
    rnd = random.Random(4)
    # hearth slab
    o.append(rbox((1.70, 0.70, 0.12), loc=(0, -0.05, 0), z0=0.0, m=M("#8F98A3"), r=0.04, segs=1))
    # stone jambs
    for s in (-1, 1):
        for k in range(4):
            w = rnd.uniform(0.32, 0.38)
            o.append(rbox((w, 0.50, 0.24), loc=(s * 0.58 + rnd.uniform(-0.02, 0.02), 0.05, 0), z0=0.12 + k * 0.245,
                          m=stones[(k + (s > 0)) % 4], r=0.07, segs=1))
    # lintel + mantel + chimney breast
    o.append(rbox((1.60, 0.52, 0.26), loc=(0, 0.05, 0), z0=1.10, m=stones[1], r=0.07, segs=1))
    o.append(rbox((1.80, 0.36, 0.08), loc=(0, -0.04, 0), z0=1.36, m=M("wood_dark"), r=0.03, segs=1))
    for k in range(3):
        o.append(rbox((1.00 - k * 0.06, 0.44, 0.26), loc=(0, 0.10, 0), z0=1.44 + k * 0.26, m=stones[(k + 2) % 4],
                      r=0.07, segs=1))
    # firebox back + logs + flames
    o.append(rbox((0.86, 0.10, 0.98), loc=(0, 0.26, 0), z0=0.12, m=M("cocoa"), r=0.02, segs=1))
    o.append(rbox((0.86, 0.46, 0.04), loc=(0, 0.05, 0), z0=0.12, m=M("black"), r=0.0, segs=1))
    for (x, rz, z) in ((-0.12, 20, 0.21), (0.14, -24, 0.21), (0.0, 90, 0.30)):
        o.append(rcyl(0.07, 0.56, loc=(x, 0.05, z), m=M("brown"), verts=8, bevel=0.015, rot=(0, 90, rz)))
    for (x, h, r, c) in ((0.0, 0.50, 0.15, "orange"), (-0.18, 0.36, 0.10, "orange"), (0.18, 0.40, 0.11, "orange"),
                         (0.0, 0.34, 0.09, "mustard"), (-0.10, 0.24, 0.07, "#FFF1A8"), (0.10, 0.26, 0.07, "mustard")):
        o.append(ball(r, loc=(x, 0.02, 0.33 + h * 0.45), m=E(c, 3.0), seg=7, rings=5, scale=(1, 0.8, h / r * 0.5)))
    # mantel decor: two candles
    for x in (-0.65, 0.62):
        o.append(rcyl(0.035, 0.14, loc=(x, -0.06, 0), z0=1.44, m=M("cream"), verts=8, bevel=0))
        o.append(ball(0.02, loc=(x, -0.06, 1.61), m=E("mustard", 3.0), seg=6, rings=4, scale=(1, 1, 1.6)))
    return root(o)


def log(length, r, loc, rot, seg=9):
    """Log along local X with lighter end grain."""
    lg = rcyl(r, length, loc=(0, 0, 0), m=M("brown"), verts=seg, bevel=0.012, segs=1, wn=False)
    lg.data.materials.append(M("bun_light"))
    for p in lg.data.polygons:
        if abs(p.normal.z) > 0.9:
            p.material_index = 1
    move([lg], loc, rot=rot)
    return lg


def build_lodge_log_pile():
    o = []
    r = 0.13
    rows = [(-0.27, 0.0, 0.27), (-0.135, 0.135), (0.0,)]
    for i, row in enumerate(rows):
        for x in row:
            z = r + i * (r * 1.75)
            o.append(log(1.10, r, (x * 1.0 + (0.03 if i == 1 else 0), 0.0, z), (90, 0, 0)))
    for k, x in enumerate((-0.48, 0.48)):
        o.append(rcyl(0.025, 0.72, loc=(x, 0, 0), z0=0.0, m=M("wood_dark"), verts=6, bevel=0))
    # stump with an axe
    o.append(lathe([(0.20, 0.0), (0.17, 0.05), (0.16, 0.30), (0.0, 0.30)], 10, [M("brown"), M("bun_light")],
                   band_mat=[0, 0, 1], loc=(0.75, -0.35, 0), name="stump"))
    o.append(rbox((0.04, 0.04, 0.48), loc=(0.80, -0.35, 0.48), m=M("wood"), r=0.012, segs=1, rot=(0, -28, 0)))
    o.append(rbox((0.16, 0.025, 0.11), loc=(0.70, -0.35, 0.32), m=M("steel"), r=0.01, segs=1, rot=(0, -28, 0)))
    return root(o)


def build_lodge_snowman():
    o = []
    snow = M("white")
    o.append(ball(0.36, loc=(0, 0, 0.32), m=snow, seg=12, rings=8, flat_bottom=-0.30))
    o.append(ball(0.27, loc=(0, 0, 0.86), m=snow, seg=12, rings=8))
    o.append(ball(0.21, loc=(0, 0, 1.28), m=snow, seg=12, rings=8))
    for (x, z) in ((-0.075, 1.33), (0.075, 1.33)):
        o.append(ball(0.03, loc=(x, -0.185, z), m=M("cocoa"), seg=6, rings=4))
    nose = rcyl(0.04, 0.20, loc=(0, 0, 0), m=M("orange"), verts=8, bevel=0, r2=0.0)
    orient(nose, (0, -0.27, 1.27), (0, -1, 0.05))
    o.append(nose)
    for k in range(3):
        dz = 0.98 - k * 0.12 - 0.86
        o.append(ball(0.03, loc=(0, -math.sqrt(0.27 ** 2 - dz ** 2) - 0.004, 0.98 - k * 0.12), m=M("cocoa"), seg=6,
                      rings=4))
    # scarf
    o.append(kit.finish(B.torus(0.20, 0.05, loc=(0, 0, 1.10), m=M("tomato"), maj=14, minr=6, scale=(1, 1, 0.8)),
                        angle=70))
    o.append(rbox((0.10, 0.04, 0.26), loc=(0.12, -0.20, 0.98), m=M("tomato"), r=0.02, segs=1, rot=(8, 0, 12)))
    # hat
    o.append(lathe([(0.20, 1.44), (0.20, 1.46), (0.13, 1.47), (0.13, 1.68), (0.0, 1.68)], 12,
                   [M("cocoa")], cap_bot=True, name="hat"))
    o.append(lathe([(0.132, 1.48), (0.132, 1.53)], 12, [M("mint")], name="band"))
    # twig arms
    for s in (-1, 1):
        a = tube([(s * 0.24, 0, 0.92), (s * 0.48, 0.02, 1.08), (s * 0.62, 0.0, 1.20)], 0.02, M("brown"), seg=5)
        f = tube([(s * 0.50, 0.02, 1.10), (s * 0.58, 0.0, 1.06)], 0.012, M("brown"), seg=4)
        o += [a, f]
    return root(o)


# ===========================================================================
# rooftop (city night)
# ===========================================================================
def build_rooftop_ac_unit():
    o = []
    o.append(rbox((1.10, 0.80, 0.10), loc=(0, 0, 0), z0=0.0, m=M("darkgray"), r=0.02, segs=1))
    o.append(rbox((1.00, 0.70, 0.78), loc=(0, 0, 0), z0=0.08, m=M("steel"), r=0.06, segs=2))
    # side louvres
    for k in range(6):
        o.append(rbox((0.03, 0.56, 0.03), loc=(0.51, 0, 0.22 + k * 0.09), m=M("steel_dark"), r=0.01, segs=1))
    # front fan grille
    o.append(rcyl(0.30, 0.03, loc=(-0.08, -0.36, 0.46), m=M("darkgray"), verts=16, bevel=0, rot=(90, 0, 0)))
    for R in (0.30, 0.20, 0.10):
        o.append(kit.finish(B.torus(R, 0.012, loc=(-0.08, -0.38, 0.46), m=M("steel_dark"), maj=16, minr=4,
                                    rot=(90, 0, 0)), angle=70))
    for a in (0, 90):
        o.append(rbox((0.60, 0.012, 0.02), loc=(-0.08, -0.385, 0.46), m=M("steel_dark"), r=0, segs=1, rot=(0, a, 0)))
    for k in range(3):
        bl = rbox((0.22, 0.01, 0.08), loc=(0.11, 0, 0), m=M("gray"), r=0, segs=1, rot=(0, 15, 0))
        move([bl], (0, 0, 0), rot=(0, 120 * k, 0))
        move([bl], (-0.08, -0.37, 0.46))
        o.append(bl)
    # control box + pipes
    o.append(rbox((0.16, 0.06, 0.22), loc=(0.36, -0.37, 0.62), m=M("offwhite"), r=0.02, segs=1))
    o.append(ball(0.02, loc=(0.36, -0.405, 0.68), m=E("mint", 2.0), seg=6, rings=4))
    for (z, r) in ((0.30, 0.03), (0.42, 0.025)):
        o.append(tube([(0.50, 0.20, z), (0.68, 0.20, z), (0.68, 0.20, 0.05)], r, M("steel_dark"), seg=6))
    return root(o)


def build_rooftop_water_tower():
    o = []
    for sx in (-1, 1):
        for sy in (-1, 1):
            o.append(rbox((0.09, 0.09, 1.60), loc=(sx * 0.55, sy * 0.55, 0.80), m=M("steel_dark"), r=0.02, segs=1,
                          rot=(sy * -3, sx * 3, 0)))
    for s in (-1, 1):
        for (a, b) in ((1, -1),):
            o.append(rbox((1.40, 0.04, 0.05), loc=(0, s * 0.54, 0.80), m=M("steel_dark"), r=0.01, segs=1,
                          rot=(0, 40, 0)))
            o.append(rbox((0.04, 1.40, 0.05), loc=(s * 0.54, 0, 0.80), m=M("steel_dark"), r=0.01, segs=1,
                          rot=(40, 0, 0)))
    o.append(rcyl(0.82, 0.08, loc=(0, 0, 0), z0=1.58, m=M("steel_dark"), verts=16, bevel=0.02))
    # wooden tank with hoops
    o.append(lathe([(0.70, 1.66), (0.74, 1.70), (0.78, 2.60), (0.80, 2.66)], 16, [M("wood")], cap_bot=True,
                   name="tank"))
    for z in (1.85, 2.15, 2.45):
        o.append(lathe([(0.770 + (z - 1.7) * 0.04, z), (0.79 + (z - 1.7) * 0.04, z + 0.025),
                        (0.770 + (z - 1.7) * 0.04, z + 0.05)], 16, [M("steel_dark")], name="hoop"))
    # stave lines
    for k in range(8):
        a = TAU * k / 8
        o.append(rbox((0.02, 0.02, 0.88), loc=(0.775 * math.cos(a), 0.775 * math.sin(a), 2.17), m=M("wood_dark"),
                      r=0, segs=1, rot=(0, 0, math.degrees(a))))
    # conical roof + finial
    o.append(lathe([(0.84, 2.62), (0.86, 2.66), (0.10, 3.20), (0.0, 3.22)], 16, [M("tomato")], cap_bot=True,
                   name="roof"))
    o.append(ball(0.06, loc=(0, 0, 3.26), m=M("mustard"), seg=8, rings=5))
    # ladder on the front
    for s in (-1, 1):
        o.append(rbox((0.035, 0.035, 2.6), loc=(s * 0.15, -0.88, 1.30), m=M("steel"), r=0.01, segs=1))
    for k in range(9):
        o.append(rbox((0.30, 0.025, 0.025), loc=(0, -0.88, 0.25 + k * 0.28), m=M("steel"), r=0, segs=1))
    return root(o)


def build_rooftop_string_lights():
    o = []
    span = 2.6
    for s in (-1, 1):
        x = s * span / 2
        o.append(rbox((0.30, 0.30, 0.10), loc=(x, 0, 0), z0=0.0, m=M("darkgray"), r=0.03, segs=1))
        o.append(rcyl(0.04, 2.30, loc=(x, 0, 0), z0=0.08, m=M("wood_dark"), verts=8, bevel=0))
        o.append(ball(0.05, loc=(x, 0, 2.40), m=M("mustard"), seg=8, rings=5))
    # sagging wire
    n = 14
    pts = []
    for i in range(n + 1):
        t = i / n
        x = -span / 2 + span * t
        z = 2.30 - 0.38 * math.sin(math.pi * t)
        pts.append((x, 0, z))
    o.append(tube(pts, 0.01, M("cocoa"), seg=4))
    cols = ["mustard", "pink", "mint", "tomato", "sky", "mustard", "pink", "mint", "orange"]
    for k in range(9):
        t = (k + 0.5) / 9
        x = -span / 2 + span * t
        z = 2.30 - 0.38 * math.sin(math.pi * t)
        o.append(rcyl(0.02, 0.04, loc=(x, 0, z - 0.03), m=M("cocoa"), verts=6, bevel=0))
        o.append(ball(0.07, loc=(x, 0, z - 0.11), m=E(cols[k], 3.0), seg=8, rings=5, scale=(1, 1, 1.25)))
    return root(o)


# ===========================================================================
# desert (drive-in)
# ===========================================================================
def ribbed_cyl(path, r, col, seg=8, cap=True):
    radii = None
    return tube(path, r, M(col), seg=seg, caps=cap, angle=50)


def build_desert_cactus():
    o = [ball(0.36, loc=(0, 0, 0.0), m=M(SAND), seg=10, rings=5, scale=(1, 1, 0.35), flat_bottom=0.0, jitter=0.05)]
    green, dark = M("#4CAF50"), M("#3E9142")

    def ribs(i, t, p):
        k = 1 + 0.07 * math.cos(8 * t)
        return (p[0] * k, p[1] * k, p[2])
    body = lathe([(0.20, 0.0), (0.22, 0.10), (0.22, 1.50), (0.20, 1.66), (0.12, 1.76), (0.0, 1.79)], 16,
                 [green, dark], deform=ribs, name="body", face_mat=lambda i, j: j % 2)
    o.append(body)
    for s, (zj, h, out) in ((1, (0.70, 0.55, 0.42)), (-1, (0.95, 0.40, 0.38))):
        arm = lathe([(0.12, 0.0), (0.13, h), (0.10, h + 0.10), (0.0, h + 0.13)], 12, [green, dark], deform=ribs,
                    name="arm", face_mat=lambda i, j: j % 2)
        move([arm], (s * out, 0, zj))
        o.append(arm)
        elbow = tube([(s * 0.18, 0, zj - 0.02), (s * (out - 0.06), 0, zj - 0.02), (s * out, 0, zj + 0.06)], 0.12,
                     green, seg=10, caps=False)
        o.append(elbow)
    for k in range(5):
        a = TAU * k / 5
        o.append(ball(0.045, loc=(0.05 * math.cos(a), 0.05 * math.sin(a), 1.80), m=M("pink"), seg=6, rings=4,
                      scale=(1, 1, 0.6)))
    o.append(ball(0.035, loc=(0, 0, 1.83), m=M("mustard"), seg=6, rings=4))
    return root(o)


def build_desert_cow_skull():
    o = [rock(0.42, (0, 0.05, 0.10), col="#C98A55", seed=3, scale=(1.2, 1.0, 0.55))]
    bone, dark = M("#F4EEE6"), M("cocoa")
    o.append(ball(0.22, loc=(0, 0.05, 0.50), m=bone, seg=10, rings=7, scale=(1.05, 0.95, 0.85)))
    snout = lathe([(0.15, 0.0), (0.13, 0.18), (0.10, 0.26), (0.0, 0.28)], 10, [bone], name="snout")
    orient(snout, (0, -0.05, 0.47), (0, -1, -0.55))
    o.append(snout)
    for s in (-1, 1):
        o.append(ball(0.06, loc=(s * 0.09, -0.14, 0.53), m=dark, seg=8, rings=5, scale=(1, 0.5, 1.2)))
        o.append(ball(0.022, loc=(s * 0.04, -0.33, 0.33), m=dark, seg=6, rings=4, scale=(1, 0.6, 1.4)))
        horn = [(s * 0.18, 0.05, 0.60), (s * 0.34, 0.03, 0.66), (s * 0.46, 0.0, 0.78), (s * 0.50, -0.03, 0.92)]
        o.append(tube(horn, 0.05, bone, seg=7, radii=[0.06, 0.05, 0.035, 0.012]))
    return root(o)


def build_desert_retro_sign():
    o = []
    o.append(rbox((0.50, 0.40, 0.12), loc=(0, 0, 0), z0=0.0, m=M("cocoa"), r=0.04, segs=1))
    o.append(rcyl(0.07, 2.2, loc=(-0.25, 0.04, 0), z0=0.10, m=M("teal"), verts=8, bevel=0))
    # arrow board pointing right/down
    arrow = kit.ccw([(-0.80, 0.18), (0.35, 0.18), (0.35, 0.40), (0.80, 0.0), (0.35, -0.40), (0.35, -0.18),
                     (-0.80, -0.18)])
    board = prism(arrow, 0.10, M("tomato"), loc=(0, 0.05, 0), rot=(90, 0, 0), bevel=0.02, segs=1)
    move([board], (0, 0, 0), rot=(0, 18, 0))
    move([board], (0.05, 0, 1.75))
    o.append(board)
    inner = kit.ccw([(-0.70, 0.08), (0.42, 0.08), (0.42, 0.24), (0.66, 0.0), (0.42, -0.24), (0.42, -0.08),
                     (-0.70, -0.08)])
    ib = prism(inner, 0.02, M("mustard"), loc=(0, -0.05, 0), rot=(90, 0, 0), wn=False)
    move([ib], (0, 0, 0), rot=(0, 18, 0))
    move([ib], (0.05, 0, 1.75))
    o.append(ib)
    # bulbs along the arrow edges
    a = math.radians(-18)
    for k in range(8):
        t = k / 7
        for zz in (0.13,):
            x = -0.72 + 1.0 * t
            for sz in (-1, 1):
                px, pz = x, sz * zz
                rx = px * math.cos(a) - pz * math.sin(a)
                rz = px * math.sin(a) + pz * math.cos(a)
                o.append(ball(0.03, loc=(rx + 0.05, -0.055, 1.75 + rz), m=E("#FFF1A8", 3.0), seg=6, rings=4))
    # star on top
    st = prism(kit.star_pts(5, 0.22, 0.10), 0.06, M("mustard"), loc=(0, 0.04, 0), rot=(90, 0, 0), bevel=0.012)
    move([st], (-0.25, 0, 2.55))
    o.append(st)
    return root(o)


# ===========================================================================
# tokyo
# ===========================================================================
def build_tokyo_lantern():
    o = []
    o.append(rbox((0.40, 0.40, 0.08), loc=(0, 0, 0), z0=0.0, m=M("cocoa"), r=0.03, segs=1))
    o.append(rbox((0.10, 0.10, 2.20), loc=(0, 0.10, 0), z0=0.06, m=M("wood_dark"), r=0.025, segs=1))
    o.append(rbox((0.08, 0.66, 0.10), loc=(0, -0.18, 2.20), m=M("wood_dark"), r=0.025, segs=1))
    o.append(rbox((0.16, 0.74, 0.05), loc=(0, -0.18, 2.29), m=M("tomato"), r=0.02, segs=1))
    cx, cy = 0, -0.38
    o.append(rcyl(0.012, 0.22, loc=(cx, cy, 0), z0=1.98, m=M("cocoa"), verts=4, bevel=0))
    prof = []
    n = 8
    for k in range(n + 1):
        t = k / n
        z = 1.28 + 0.62 * t
        r = 0.08 + 0.24 * math.sin(math.pi * t) ** 0.8
        if 0 < k < n:
            r *= (1.0 if k % 2 else 0.95)
        prof.append((r, z))
    o.append(lathe(prof, 14, [E("tomato", 2.2)], loc=(cx, cy, 0), name="paper", angle=60))
    o.append(lathe([(0.10, 1.25), (0.11, 1.28), (0.10, 1.31), (0.0, 1.31)], 12, [M("cocoa")], cap_bot=True,
                   loc=(cx, cy, 0), name="capb"))
    o.append(lathe([(0.10, 1.88), (0.11, 1.91), (0.10, 1.95), (0.0, 1.95)], 12, [M("cocoa")], cap_bot=True,
                   loc=(cx, cy, 0), name="capt"))
    o.append(rcyl(0.035, 0.18, loc=(cx, cy, 0), z0=1.08, m=M("mustard"), verts=8, bevel=0, r2=0.015))
    o.append(ball(0.03, loc=(cx, cy, 1.24), m=M("mustard"), seg=6, rings=4))
    return root(o)


def build_tokyo_vending_machine():
    o = []
    W, D, H = 0.92, 0.72, 1.86
    o.append(rbox((W, D, H), loc=(0, 0, 0), z0=0.04, m=M("white"), r=0.06, segs=2))
    o.append(rbox((W + 0.02, D + 0.02, 0.06), loc=(0, 0, 0), z0=0.0, m=M("darkgray"), r=0.02, segs=1))
    # top light-box header
    o.append(rbox((W - 0.10, 0.04, 0.20), loc=(0, -D / 2 - 0.005, 1.72), m=E("pink", 2.0), r=0.03, segs=1))
    # emissive display window with cans
    o.append(rbox((0.62, 0.04, 0.80), loc=(-0.08, -D / 2 - 0.005, 1.18), m=E("#CFF6FF", 1.5), r=0.03, segs=1))
    cols = ["tomato", "mint", "mustard", "blue", "lilac"]
    for row, z in enumerate((0.92, 1.18, 1.44)):
        for k in range(5):
            o.append(rcyl(0.04, 0.13, loc=(-0.32 + k * 0.12, -D / 2 - 0.04, 0), z0=z - 0.07,
                          m=M(cols[(k + row) % 5]), verts=8, bevel=0))
        o.append(rbox((0.62, 0.05, 0.02), loc=(-0.08, -D / 2 - 0.035, z - 0.08), m=M("steel"), r=0, segs=1))
    for k in range(5):
        o.append(rbox((0.06, 0.02, 0.03), loc=(-0.32 + k * 0.12, -D / 2 - 0.03, 0.78), m=E("mustard", 2.0), r=0,
                      segs=1))
    # side panel: coin slot + buttons
    o.append(rbox((0.16, 0.03, 0.46), loc=(0.33, -D / 2 - 0.005, 1.20), m=M("navy"), r=0.02, segs=1))
    o.append(rbox((0.06, 0.03, 0.10), loc=(0.33, -D / 2 - 0.02, 1.30), m=M("steel"), r=0.01, segs=1))
    o.append(rcyl(0.03, 0.02, loc=(0.33, -D / 2 - 0.025, 1.12), m=E("tomato", 2.0), verts=8, bevel=0,
                  rot=(90, 0, 0)))
    # dispensing slot
    o.append(rbox((0.56, 0.05, 0.20), loc=(-0.08, -D / 2 - 0.01, 0.30), m=M("darkgray"), r=0.03, segs=1))
    o.append(rbox((0.50, 0.05, 0.10), loc=(-0.08, -D / 2 - 0.02, 0.30), m=M("black"), r=0.02, segs=1))
    return root(o)


def build_tokyo_bonsai():
    o = []
    o.append(rbox((0.70, 0.46, 0.18), loc=(0, 0, 0), z0=0.0, m=M("navy"), r=0.05, segs=2))
    o.append(rbox((0.74, 0.50, 0.04), loc=(0, 0, 0), z0=0.16, m=M("teal"), r=0.02, segs=1))
    o.append(ball(0.30, loc=(0, 0, 0.18), m=M("lettuce_dark"), seg=10, rings=5, scale=(1.1, 0.7, 0.22),
                  jitter=0.05))
    trunk_pts = [(0.0, 0.0, 0.18), (0.08, 0.0, 0.40), (-0.05, 0.02, 0.62), (0.06, -0.02, 0.80), (0.20, 0.0, 0.92)]
    o.append(tube(trunk_pts, 0.06, M("brown"), seg=7, radii=[0.08, 0.065, 0.055, 0.045, 0.03]))
    o.append(tube([(-0.05, 0.02, 0.62), (-0.24, 0.0, 0.70), (-0.34, 0.02, 0.72)], 0.03, M("brown"), seg=5,
                  radii=[0.035, 0.028, 0.018]))
    for (x, y, z, r) in ((0.24, 0.0, 0.98, 0.20), (-0.36, 0.02, 0.78, 0.17), (0.02, 0.02, 1.08, 0.15),
                         (0.10, -0.05, 0.76, 0.12)):
        o.append(ball(r, loc=(x, y, z), m=M("#2E8B57"), seg=9, rings=5, scale=(1.4, 1.1, 0.6), jitter=0.06))
        o.append(ball(r * 0.7, loc=(x - 0.02, y - 0.02, z + r * 0.25), m=M("#4CAF50"), seg=8, rings=4,
                      scale=(1.4, 1.1, 0.55), jitter=0.06))
    return root(o)


# ===========================================================================
# jungle
# ===========================================================================
def build_jungle_vine_arch():
    o = []
    bark = M("wood_dark")
    for s in (-1, 1):
        o.append(lathe([(0.16, 0.0), (0.13, 0.08), (0.12, 2.30)], 9, [bark], name="post", loc=(s * 1.0, 0, 0)))
    o.append(tube([(-1.15, 0, 2.30), (-0.5, 0, 2.48), (0.5, 0, 2.48), (1.15, 0, 2.30)], 0.13, bark, seg=9))
    rnd = random.Random(6)
    greens = [M("lettuce"), M("#4FAE3A"), M("lettuce_dark")]
    for k in range(7):
        x = -0.85 + k * 0.28 + rnd.uniform(-0.05, 0.05)
        L = rnd.uniform(0.55, 1.15)
        top = 2.40
        pts = [(x, -0.10, top), (x + 0.04, -0.14, top - L * 0.5), (x - 0.02, -0.12, top - L)]
        o.append(tube(pts, 0.018, M("lettuce_dark"), seg=4))
        for j in range(2):
            t = 0.4 + 0.5 * j
            p = Vector(pts[0]).lerp(Vector(pts[2]), t)
            lf = ball(0.08, loc=(0, 0, 0), m=greens[(k + j) % 3], seg=6, rings=4, scale=(1.0, 0.35, 1.5))
            orient(lf, p + Vector((0.05 * (-1) ** j, -0.04, 0)), (0.6 * (-1) ** j, -0.5, -0.6))
            o.append(lf)
    # leafy clumps on the beam and flowers
    for (x, z, r) in ((-0.90, 2.42, 0.24), (-0.30, 2.58, 0.22), (0.35, 2.58, 0.24), (0.95, 2.44, 0.22)):
        o.append(ball(r, loc=(x, 0, z), m=greens[int(abs(x) * 10) % 3], seg=8, rings=5, jitter=0.08, seed=int(x * 10),
                      scale=(1.3, 0.9, 0.8)))
    for (x, z, c) in ((-0.62, 2.55, "pink"), (0.05, 2.63, "mustard"), (0.70, 2.55, "tomato"), (-0.98, 1.6, "pink"),
                      (1.02, 1.3, "mustard")):
        o.append(ball(0.06, loc=(x, -0.18, z), m=M(c), seg=6, rings=4))
    return root(o)


def build_jungle_tiki_torch():
    o = []
    n = 6
    zs = [0.0]
    for k in range(n):
        zs.append(zs[-1] + 0.26)
    for k in range(n):
        o.append(lathe([(0.05, zs[k]), (0.06, zs[k] + 0.01), (0.05, zs[k] + 0.03), (0.048, zs[k + 1] - 0.02),
                        (0.058, zs[k + 1])], 8, [M("bun_light") if k % 2 else M("#E8B260")], name="bamboo"))
    z0 = zs[-1]
    o.append(lathe([(0.06, z0), (0.13, z0 + 0.10), (0.15, z0 + 0.26), (0.13, z0 + 0.28), (0.10, z0 + 0.26)], 10,
                   [M("wood")], cap_bot=True, name="basket",
                   face_mat=lambda i, j: None))
    for zz in (z0 + 0.08, z0 + 0.18):
        o.append(lathe([(0.12 + (zz - z0) * 0.12, zz), (0.135 + (zz - z0) * 0.12, zz + 0.015),
                        (0.12 + (zz - z0) * 0.12, zz + 0.03)], 10, [M("wood_dark")], name="wrap"))
    def flame(h, r, col, loc, lean):
        f = lathe([(0.0, 0.0), (r * 0.8, h * 0.10), (r, h * 0.25), (r * 0.75, h * 0.50), (r * 0.30, h * 0.78),
                   (0.0, h)], 8, [E(col, 3.0)], name="flame",
                   deform=lambda i, t, p: (p[0] + lean * (p[2] / h) ** 2, p[1], p[2]))
        move([f], loc)
        return f
    o.append(flame(0.46, 0.13, "orange", (0, 0, z0 + 0.14), 0.05))
    o.append(flame(0.30, 0.085, "mustard", (0.0, -0.06, z0 + 0.16), -0.03))
    o.append(flame(0.22, 0.06, "mustard", (0.06, -0.05, z0 + 0.16), 0.04))
    return root(o)


def build_jungle_big_leaf():
    o = [ball(0.22, loc=(0, 0, 0.0), m=M("lettuce_dark"), seg=8, rings=5, scale=(1, 1, 0.5), flat_bottom=0.0)]
    rnd = random.Random(12)
    for k in range(5):
        a = TAU * k / 5 + 0.4
        d = Vector((math.cos(a), math.sin(a), 0))
        L = rnd.uniform(1.0, 1.35)
        stem_top = Vector((0, 0, 0.0)) + d * 0.25 * L + Vector((0, 0, 0.75 * L))
        o.append(tube([(0, 0, 0.05), tuple(Vector((0, 0, 0.05)).lerp(stem_top, 0.5) + Vector((0, 0, 0.05))),
                       tuple(stem_top)], 0.025, M("lettuce_dark"), seg=5))
        spine = []
        for s in range(6):
            u = s / 5
            p = stem_top + d * (0.75 * L * u) + Vector((0, 0, 0.30 * math.sin(u * math.pi * 0.7) - 0.45 * u * u))
            spine.append(tuple(p))
        widths = [0.10, 0.30, 0.36, 0.30, 0.16, 0.0]
        o.append(leaf(spine, [w * L * 0.8 for w in widths], M("lettuce") if k % 2 else M("#4FAE3A"), thick=0.04))
        rib = tube(spine[:5], 0.012, M("#A2E070"), seg=4)
        move([rib], (0, 0, 0.022))
        o.append(rib)
    return root(o)


# ===========================================================================
# volcano
# ===========================================================================
def build_volcano_lava_rock():
    import bpy
    o = []
    rk = ball(0.6, loc=(0, 0, 0.0), m=M("cocoa"), seg=12, rings=8, jitter=0.12, seed=7, scale=(1.2, 1.0, 0.75),
              flat_bottom=0.0, angle=30)
    o.append(rk)
    from mathutils.bvhtree import BVHTree
    bvh = BVHTree.FromObject(rk, bpy.context.evaluated_depsgraph_get())
    c0 = Vector((0, 0, 0.12))
    cracks = [[(-0.9, -0.5, 0.5), (-0.5, -0.8, 0.6), (-0.2, -0.85, 0.75), (0.15, -0.7, 0.9), (0.3, -0.75, 0.6),
               (0.6, -0.6, 0.55)],
              [(0.1, -0.3, 1.0), (0.3, 0.0, 1.0), (0.2, 0.3, 1.0), (0.5, 0.5, 0.8)],
              [(-0.2, -0.85, 0.75), (-0.45, -0.55, 1.0), (-0.6, -0.2, 0.9)]]
    for cr in cracks:
        pts = []
        for d in cr:
            dv = Vector(d).normalized()
            hit = bvh.ray_cast(c0 + dv * 2.0, -dv)
            if hit[0] is not None:
                pts.append(tuple(hit[0] + hit[1] * 0.004))
        if len(pts) >= 2:
            o.append(tube(pts, 0.022, E("orange", 3.0), seg=4))
    o.append(rock(0.25, (0.62, -0.30, 0.0), col="#4A3346", seed=3, scale=(1.0, 0.9, 0.7)))
    o.append(rock(0.18, (-0.62, -0.22, 0.0), col="#4A3346", seed=5, scale=(1.0, 0.9, 0.8)))
    pool = rcyl(0.26, 0.02, loc=(0.05, -0.62, 0.0), m=E("orange", 3.0), verts=10, bevel=0)
    for v in pool.data.vertices:
        v.co.x *= 1.0 + 0.12 * math.sin(v.co.y * 20)
    o.append(pool)
    o.append(ball(0.05, loc=(0.2, -0.62, 0.02), m=E("mustard", 3.0), seg=6, rings=4, scale=(1, 1, 0.5)))
    return root(o)


def build_volcano_obsidian_pillar():
    o = []

    def crystal(h, r, loc, tilt, seed):
        prof = [(r * 0.9, 0.0), (r, h * 0.12), (r * 0.88, h * 0.78), (0.0, h)]
        c = lathe(prof, 6, [M("#2D2140"), M("#5A3F78"), M("lilac")], name="crystal", angle=None,
                  face_mat=lambda i, j: (1 if j % 2 else 0) if i < 2 else (2 if j % 3 == 0 else 1))
        move([c], (0, 0, 0), rot=tilt)
        move([c], loc)
        return c
    o.append(rock(0.42, (0, 0, 0.0), col="#3B2A3A", seed=2, scale=(1.2, 1.1, 0.35)))
    o.append(crystal(2.2, 0.24, (0, 0, 0.05), (4, -3, 15), 1))
    o.append(crystal(1.1, 0.14, (0.34, -0.12, 0.02), (8, 22, 40), 2))
    o.append(crystal(0.8, 0.11, (-0.30, -0.18, 0.02), (-10, -20, 5), 3))
    o.append(crystal(0.55, 0.08, (-0.18, 0.28, 0.02), (14, -12, 70), 4))
    o.append(ball(0.05, loc=(0.0, -0.32, 0.06), m=E("orange", 3.0), seg=6, rings=4, scale=(1.6, 1, 0.4)))
    return root(o)


def build_volcano_steam_vent():
    o = []
    rnd = random.Random(9)
    for k in range(9):
        a = TAU * k / 9 + rnd.uniform(-0.15, 0.15)
        r = rnd.uniform(0.16, 0.24)
        o.append(rock(r, (0.42 * math.cos(a), 0.42 * math.sin(a), 0.0), col=("#4A3346" if k % 2 else "cocoa"),
                      seed=k + 1, scale=(1.0, 0.9, 0.9)))
    o.append(rcyl(0.34, 0.06, loc=(0, 0, 0), z0=0.0, m=M("#2A1E2A"), verts=12, bevel=0))
    o.append(rcyl(0.22, 0.02, loc=(0, 0, 0), z0=0.06, m=E("orange", 3.0), verts=12, bevel=0))
    o.append(ball(0.10, loc=(0, 0, 0.08), m=E("mustard", 3.0), seg=8, rings=4, scale=(1.2, 1.2, 0.5)))
    for (z, r, dx) in ((0.40, 0.18, 0.0), (0.66, 0.15, 0.06), (0.88, 0.12, -0.02)):
        o.append(ball(r, loc=(dx, 0, z), m=M("#EDEFF2"), seg=8, rings=5, jitter=0.08, seed=int(z * 100)))
    return root(o)


# ===========================================================================
# space
# ===========================================================================
def build_space_console():
    o = []
    o.append(rbox((1.20, 0.60, 0.80), loc=(0, 0.05, 0), z0=0.0, m=M("white"), r=0.07, segs=2))
    o.append(rbox((1.22, 0.62, 0.08), loc=(0, 0.05, 0), z0=0.0, m=M("teal"), r=0.03, segs=1))
    # sloped control deck (rises toward the back, faces the user at -Y)
    dc = Vector((0, -0.10, 0.84))
    R = Matrix.Rotation(math.radians(22), 3, 'X')
    nrm = R @ Vector((0, 0, 1))

    def on_deck(x, y, h):
        return tuple(dc + R @ Vector((x, y, 0)) + nrm * h)
    o.append(rbox((1.26, 0.56, 0.10), loc=tuple(dc), m=M("navy"), r=0.04, segs=2, rot=(22, 0, 0)))
    for (x, w, c) in ((-0.36, 0.36, "mint"), (0.20, 0.50, "sky")):
        o.append(rbox((w, 0.24, 0.03), loc=on_deck(x, 0.08, 0.055), m=E(c, 1.6), r=0.02, segs=1, rot=(22, 0, 0)))
    for k in range(6):
        c = ("tomato", "mustard", "mint", "pink", "mustard", "tomato")[k]
        o.append(rcyl(0.028, 0.03, loc=on_deck(-0.50 + k * 0.20, -0.17, 0.06), m=E(c, 2.0), verts=8, bevel=0.006,
                      rot=(22, 0, 0)))
    # back monitor + antenna
    o.append(rbox((0.90, 0.10, 0.50), loc=(0, 0.28, 1.20), m=M("white"), r=0.06, segs=2))
    o.append(rbox((0.78, 0.03, 0.38), loc=(0, 0.215, 1.20), m=E("#7FF0E0", 1.6), r=0.03, segs=1))
    for k in range(3):
        o.append(rbox((0.50 - k * 0.12, 0.01, 0.025), loc=(-0.08, 0.198, 1.30 - k * 0.08), m=E("navy", 1.0), r=0,
                      segs=1))
    o.append(rcyl(0.012, 0.30, loc=(0.40, 0.30, 0), z0=1.45, m=M("steel"), verts=6, bevel=0))
    o.append(ball(0.04, loc=(0.40, 0.30, 1.77), m=E("tomato", 2.5), seg=8, rings=5))
    return root(o)


def build_space_astro_plant():
    o = []
    o.append(lathe([(0.24, 0.0), (0.26, 0.03), (0.30, 0.36), (0.32, 0.38), (0.32, 0.42), (0.29, 0.43),
                    (0.27, 0.40)], 14, [M("white"), M("teal")], band_mat=[0, 0, 1, 1, 1, 1], cap_bot=True, name="pot"))
    o.append(rcyl(0.27, 0.02, loc=(0, 0, 0), z0=0.38, m=M("#3B2A3A"), verts=14, bevel=0))
    for k in range(3):
        a = TAU * k / 3
        o.append(rbox((0.05, 0.02, 0.18), loc=(0.29 * math.cos(a), 0.29 * math.sin(a), 0.20), m=E("mint", 1.6),
                      r=0.01, segs=1, rot=(0, 0, math.degrees(a) + 90)))
    rnd = random.Random(3)
    for k in range(5):
        a = TAU * k / 5 + 0.3
        h = rnd.uniform(0.55, 0.95)
        d = Vector((math.cos(a), math.sin(a), 0)) * rnd.uniform(0.12, 0.22)
        base = Vector((0, 0, 0.40))
        tip = base + d + Vector((0, 0, h))
        mid = base + d * 0.3 + Vector((0, 0, h * 0.55))
        o.append(tube([tuple(base), tuple(mid), tuple(tip)], 0.03, M("lilac"), seg=6,
                      radii=[0.04, 0.03, 0.02]))
        o.append(ball(0.07, loc=tuple(tip + Vector((0, 0, 0.04))), m=E(("mint", "pink")[k % 2], 2.5), seg=8, rings=5))
    for k in range(4):
        a = TAU * k / 4 + 0.8
        d = Vector((math.cos(a), math.sin(a), 0))
        spine = [tuple(Vector((0, 0, 0.40)) + d * 0.05), tuple(Vector((0, 0, 0.52)) + d * 0.22),
                 tuple(Vector((0, 0, 0.55)) + d * 0.40)]
        o.append(leaf(spine, [0.05, 0.08, 0.0], M("#7A5CD6"), thick=0.03))
    return root(o)


def build_space_rocket_model():
    o = []
    o.append(lathe([(0.30, 0.0), (0.31, 0.04), (0.26, 0.10), (0.10, 0.12), (0.0, 0.125)], 14, [M("navy")],
                   cap_bot=True, name="stand"))
    o.append(rcyl(0.03, 0.30, loc=(0, 0, 0), z0=0.10, m=M("steel"), verts=6, bevel=0))
    z0 = 0.36
    body = [(0.10, z0), (0.13, z0 + 0.04), (0.18, z0 + 0.20), (0.19, z0 + 0.50), (0.17, z0 + 0.78),
            (0.12, z0 + 0.95), (0.05, z0 + 1.07), (0.0, z0 + 1.10)]
    o.append(lathe(body, 14, [M("white"), M("tomato")], band_mat=[0, 0, 0, 0, 1, 1, 1], cap_bot=True, name="body"))
    o.append(lathe([(0.185, z0 + 0.28), (0.195, z0 + 0.30), (0.195, z0 + 0.36), (0.185, z0 + 0.38)], 14,
                   [M("tomato")], name="band"))
    o.append(lathe([(0.07, z0 - 0.10), (0.11, z0 + 0.01), (0.10, z0 + 0.02)], 10, [M("steel_dark")], name="nozzle"))
    o.append(ball(0.07, loc=(0, 0, z0 - 0.10), m=E("orange", 2.5), seg=8, rings=5, scale=(1, 1, 1.4)))
    for k in range(3):
        a = TAU * k / 3 - math.pi / 2
        fin = prism([(0.0, 0.0), (0.20, -0.08), (0.20, 0.10), (0.0, 0.34)], 0.04, M("tomato"), bevel=0.012,
                    segs=1, rot=(90, 0, 0), loc=(0, 0.02, 0))
        move([fin], (0, 0, 0), rot=(0, 0, math.degrees(a)))
        move([fin], (0.15 * math.cos(a), 0.15 * math.sin(a), z0 + 0.02))
        o.append(fin)
    win = rcyl(0.075, 0.04, loc=(0, 0, 0), m=M("glass"), verts=12, bevel=0.01)
    orient(win, (0, -0.19, z0 + 0.60), (0, -1, 0))
    rim = kit.finish(B.torus(0.075, 0.02, loc=(0, 0, 0), m=M("teal"), maj=12, minr=5), angle=70)
    orient(rim, (0, -0.19, z0 + 0.60), (0, -1, 0))
    o += [win, rim]
    return root(o)


# ===========================================================================
# underwater
# ===========================================================================
def build_underwater_coral():
    o = [rock(0.36, (0, 0, 0.0), col="#8C8FA8", seed=4, scale=(1.2, 1.0, 0.45))]
    rnd = random.Random(21)

    def branch(p0, d, L, r, depth, col):
        p1 = p0 + d * L
        o.append(tube([tuple(p0), tuple(p0.lerp(p1, 0.5) + Vector((0, 0, 0.03))), tuple(p1)], r, M(col), seg=6,
                      radii=[r, r * 0.9, r * 0.8]))
        o.append(ball(r * 1.05, loc=tuple(p1), m=M(col), seg=6, rings=4))
        if depth > 0:
            for s in (-1, 1):
                ang = s * rnd.uniform(0.35, 0.6)
                nd = (Matrix.Rotation(ang, 3, Vector((-d.y, d.x, 0)).normalized() if abs(d.z) < 0.99 else
                                      Vector((1, 0, 0))) @ d)
                nd = (nd + Vector((rnd.uniform(-0.2, 0.2), rnd.uniform(-0.2, 0.2), 0.3))).normalized()
                branch(p1, nd, L * 0.72, r * 0.75, depth - 1, col)
    branch(Vector((0.05, 0.0, 0.10)), Vector((0.1, 0, 1)).normalized(), 0.45, 0.06, 2, "#FF7A8A")
    branch(Vector((-0.22, -0.05, 0.08)), Vector((-0.5, -0.2, 1)).normalized(), 0.32, 0.05, 1, "orange")
    for k in range(6):
        a = TAU * k / 6
        o.append(ball(0.06, loc=(0.28 * math.cos(a) + 0.05, 0.22 * math.sin(a), 0.12), m=M("lilac"), seg=6,
                      rings=4, scale=(1, 1, 0.7)))
    return root(o)


def build_underwater_clam():
    o = [rock(0.30, (0, 0.05, 0.0), col="#8C8FA8", seed=8, scale=(1.5, 1.2, 0.35))]
    n = 9

    def shell(sign, tilt):
        import bmesh
        bm = bmesh.new()
        hinge = bm.verts.new((0, 0, 0))
        R = 0.48
        rings = []
        for ring_r, h in ((0.55, 0.15), (1.0, 0.07)):
            ring = []
            for k in range(n + 1):
                a = math.pi * k / n
                edge = ring_r == 1.0
                rr = R * ring_r * (1 + (0.06 if (k % 2 == 0 and edge) else 0))
                wz = (0.035 if k % 2 == 0 else -0.01) if edge else 0.0
                ring.append(bm.verts.new((rr * math.cos(a), -rr * math.sin(a) * 0.9, sign * (h + wz))))
            rings.append(ring)
        for k in range(n):
            f = bm.faces.new((hinge, rings[0][k + 1], rings[0][k]) if sign > 0 else
                             (hinge, rings[0][k], rings[0][k + 1]))
            f.material_index = k % 2
            q = (rings[0][k], rings[1][k], rings[1][k + 1], rings[0][k + 1])
            f = bm.faces.new(tuple(reversed(q)) if sign > 0 else q)
            f.material_index = k % 2
        sh = kit._mk(bm, "shell", [M("pink"), M("#FFB3C9")])
        sol = sh.modifiers.new("Sol", 'SOLIDIFY')
        sol.thickness = 0.04
        sol.offset = 0
        kit.finish(sh, angle=50)
        move([sh], (0, 0, 0), rot=(tilt, 0, 0))
        return sh
    bottom = shell(1, 0)
    top = shell(-1, 0)
    move([top], (0, 0, 0), rot=(-48, 0, 0))
    move([bottom, top], (0, 0.30, 0.12))
    o += [bottom, top]
    o.append(ball(0.10, loc=(0, 0.05, 0.27), m=M("#FFF6FA"), seg=10, rings=7))
    return root(o)


def build_underwater_bubble_column():
    o = []
    for k in range(7):
        a = TAU * k / 7
        o.append(rock(0.16, (0.28 * math.cos(a), 0.28 * math.sin(a), 0.0), col=("#8C8FA8" if k % 2 else "#6E7390"),
                      seed=k + 3, scale=(1, 1, 0.8)))
    o.append(rcyl(0.18, 0.06, loc=(0, 0, 0), z0=0.0, m=M("navy"), verts=10, bevel=0))
    rnd = random.Random(2)
    z = 0.25
    r = 0.10
    for k in range(9):
        x = 0.06 * math.sin(k * 1.3)
        y = 0.05 * math.cos(k * 0.9)
        o.append(ball(r, loc=(x, y, z), m=M("glass"), seg=8, rings=6))
        o.append(ball(r * 0.25, loc=(x - r * 0.35, y - r * 0.55, z + r * 0.35), m=M("white"), seg=5, rings=3))
        z += r * 1.6 + 0.05
        r = max(0.04, r * 0.86) if k % 2 == 0 else r * 1.02
    for (x, y, zz, rr) in ((0.20, -0.05, 0.55, 0.05), (-0.16, 0.08, 0.90, 0.045), (0.14, 0.10, 1.35, 0.04)):
        o.append(ball(rr, loc=(x, y, zz), m=M("glass"), seg=6, rings=4))
    return root(o)


# ===========================================================================
# candy
# ===========================================================================
def build_candy_lollipop():
    import bmesh
    o = [rcyl(0.22, 0.10, loc=(0, 0, 0), z0=0.0, m=M("pink"), verts=12, bevel=0.03)]
    o.append(rcyl(0.035, 1.30, loc=(0, 0, 0), z0=0.08, m=M("white"), verts=8, bevel=0))
    # spiral disc (polar grid with spiral colouring), thickness via solidify
    bm = bmesh.new()
    R, rings, segs = 0.46, 4, 16
    centre = bm.verts.new((0, 0, 0))
    grid = []
    for i in range(1, rings + 1):
        grid.append([bm.verts.new((R * i / rings * math.cos(TAU * j / segs), R * i / rings * math.sin(TAU * j / segs),
                                   0)) for j in range(segs)])
    for j in range(segs):
        k = (j + 1) % segs
        f = bm.faces.new((centre, grid[0][j], grid[0][k]))
        f.material_index = (j // 2) % 3
        for i in range(rings - 1):
            f = bm.faces.new((grid[i][j], grid[i + 1][j], grid[i + 1][k], grid[i][k]))
            f.material_index = ((j // 2) + i + 1) % 3
    disc = kit._mk(bm, "disc", [M("tomato"), M("white"), M("mustard")])
    sol = disc.modifiers.new("Sol", 'SOLIDIFY')
    sol.thickness = 0.10
    sol.offset = 0
    disc.rotation_euler = (math.radians(90), 0, 0)
    disc.location = (0, 0, 1.70)
    kit.finish(disc, angle=40)
    o.append(disc)
    o.append(kit.finish(B.torus(0.46, 0.05, loc=(0, 0, 1.70), m=M("pink"), maj=24, minr=6, rot=(90, 0, 0)), angle=70))
    # ribbon bow
    for s in (-1, 1):
        o.append(ball(0.09, loc=(s * 0.09, -0.02, 1.18), m=M("mint"), seg=6, rings=4, scale=(1.4, 0.5, 0.8)))
    o.append(ball(0.04, loc=(0, -0.03, 1.18), m=M("mint"), seg=6, rings=4))
    return root(o)


def build_candy_candy_cane():
    o = [rcyl(0.20, 0.08, loc=(0, 0, 0), z0=0.0, m=M("mint"), verts=12, bevel=0.025)]
    pts = [(0, 0, 0.05), (0, 0, 0.5), (0, 0, 1.0), (0, 0, 1.40)]
    R = 0.26
    for k in range(1, 9):
        a = math.pi * k / 8
        pts.append((R - R * math.cos(a), 0, 1.40 + R * math.sin(a)))
    pts.append((2 * R + 0.02, 0, 1.30))
    pts.append((2 * R + 0.02, 0, 1.18))
    # finer sampling so the stripes are dense
    fine = []
    for i in range(len(pts) - 1):
        a, b = Vector(pts[i]), Vector(pts[i + 1])
        steps = max(1, int((b - a).length / 0.07))
        for s in range(steps):
            fine.append(tuple(a.lerp(b, s / steps)))
    fine.append(pts[-1])
    seg = 8
    o.append(tube(fine, 0.075, M("white"), seg=seg, mats=[M("white"), M("red")],
                  seg_mat=lambda i, j: ((i + j) // 3) % 2, angle=60))
    o.append(ball(0.075, loc=pts[-1], m=M("red"), seg=8, rings=4, scale=(1, 1, 0.5)))
    move([o[-1]], (0, 0, 0))
    o.append(lathe([(0.082, 0.95), (0.09, 0.97), (0.09, 1.03), (0.082, 1.05)], 10, [M("mint")], name="band"))
    for s in (-1, 1):
        o.append(ball(0.06, loc=(s * 0.07, -0.09, 1.0), m=M("mint"), seg=6, rings=4, scale=(1.3, 0.5, 0.8)))
    return root(o)


def build_candy_gumdrop_tree():
    o = []
    o.append(lathe([(0.14, 0.0), (0.10, 0.08), (0.08, 1.10)], 10, [M("pink"), M("white")], name="trunk",
                   face_mat=lambda i, j: (j // 1 + 0) % 2))
    o.append(ball(0.55, loc=(0, 0, 1.55), m=M("#FFB3D1"), seg=12, rings=8, jitter=0.04, seed=3))
    o.append(ball(0.36, loc=(0.36, -0.10, 1.30), m=M("#C8B6FF"), seg=10, rings=7, jitter=0.04, seed=5))
    o.append(ball(0.32, loc=(-0.36, 0.08, 1.34), m=M("#A8F0D0"), seg=10, rings=7, jitter=0.04, seed=6))
    cols = ["tomato", "mustard", "mint", "lilac", "orange", "sky", "pink"]
    spots = [(0.0, -0.50, 1.72), (0.30, -0.40, 1.84), (-0.30, -0.40, 1.66), (0.58, -0.28, 1.30),
             (-0.60, -0.16, 1.42), (0.12, -0.28, 2.04), (-0.06, -0.54, 1.38), (0.40, 0.10, 1.98),
             (-0.30, 0.30, 1.90), (0.62, 0.05, 1.50)]
    cols = cols + ["mint", "tomato", "mustard"]
    for c, (x, y, z) in zip(cols, spots):
        g = lathe([(0.10, 0.0), (0.11, 0.03), (0.08, 0.11), (0.0, 0.135)], 8, [M(c)], cap_bot=True, name="gum")
        n = Vector((x, y, z - 1.55)).normalized()
        orient(g, (x, y, z), n)
        o.append(g)
    return root(o)


STORE = {"ao": 0.35, "ao_strength": 0.8}
MODELS = {
    "store_wagon_truck": (build_wagon_truck, 1500, {"ao": 0.5, "ao_strength": 0.8}),
    "store_wagon_balloon_stand": (build_wagon_balloon_stand, 1500, STORE),
    "store_wagon_picnic_table": (build_wagon_picnic_table, 1500, STORE),
    "store_seaside_surfboard": (build_seaside_surfboard, 1500, STORE),
    "store_seaside_lifeguard_tower": (build_seaside_lifeguard_tower, 1500, STORE),
    "store_seaside_beach_chair": (build_seaside_beach_chair, 1500, STORE),
    "store_diner_jukebox": (build_diner_jukebox, 1500, STORE),
    "store_diner_stool": (build_diner_stool, 1500, STORE),
    "store_diner_neon_star": (build_diner_neon_star, 1500, STORE),
    "store_lodge_fireplace": (build_lodge_fireplace, 1500, STORE),
    "store_lodge_log_pile": (build_lodge_log_pile, 1500, STORE),
    "store_lodge_snowman": (build_lodge_snowman, 1500, STORE),
    "store_rooftop_ac_unit": (build_rooftop_ac_unit, 1500, STORE),
    "store_rooftop_water_tower": (build_rooftop_water_tower, 1500, STORE),
    "store_rooftop_string_lights": (build_rooftop_string_lights, 1500, STORE),
    "store_desert_cactus": (build_desert_cactus, 1500, STORE),
    "store_desert_cow_skull": (build_desert_cow_skull, 1500, STORE),
    "store_desert_retro_sign": (build_desert_retro_sign, 1500, STORE),
    "store_tokyo_lantern": (build_tokyo_lantern, 1500, STORE),
    "store_tokyo_vending_machine": (build_tokyo_vending_machine, 1500, STORE),
    "store_tokyo_bonsai": (build_tokyo_bonsai, 1500, STORE),
    "store_jungle_vine_arch": (build_jungle_vine_arch, 1500, STORE),
    "store_jungle_tiki_torch": (build_jungle_tiki_torch, 1500, STORE),
    "store_jungle_big_leaf": (build_jungle_big_leaf, 1500, STORE),
    "store_volcano_lava_rock": (build_volcano_lava_rock, 1500, STORE),
    "store_volcano_obsidian_pillar": (build_volcano_obsidian_pillar, 1500, STORE),
    "store_volcano_steam_vent": (build_volcano_steam_vent, 1500, STORE),
    "store_space_console": (build_space_console, 1500, STORE),
    "store_space_astro_plant": (build_space_astro_plant, 1500, STORE),
    "store_space_rocket_model": (build_space_rocket_model, 1500, STORE),
    "store_underwater_coral": (build_underwater_coral, 1500, STORE),
    "store_underwater_clam": (build_underwater_clam, 1500, STORE),
    "store_underwater_bubble_column": (build_underwater_bubble_column, 1500, STORE),
    "store_candy_lollipop": (build_candy_lollipop, 1500, STORE),
    "store_candy_candy_cane": (build_candy_candy_cane, 1500, STORE),
    "store_candy_gumdrop_tree": (build_candy_gumdrop_tree, 1500, STORE),
}

if __name__ == "__main__":
    kit.run(MODELS)
