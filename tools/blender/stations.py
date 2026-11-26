"""
Stations & furniture (ASSET_CONTRACT §5): models/<name>.bvm
One part `root` (st_shake adds a `spinner` child), pivot at the floor centre,
front = Blender -Y. Anchors exactly as named in the contract.

    Blender -b --factory-startup --python tools/blender/stations.py [-- counter st_grill ...]
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from kit import M, T, E, B, lathe, prism, tube, rbox, rcyl, ball, join, move  # noqa: E402
from mathutils import Vector  # noqa: E402

TAU = 2 * math.pi


# ---------------------------------------------------------------------------
# Shared station bits
# ---------------------------------------------------------------------------
def feet(w, d, h=0.07, r=0.04, col="darkgray"):
    out = []
    for sx in (-1, 1):
        for sy in (-1, 1):
            out.append(rcyl(r, h, loc=(sx * w / 2, sy * d / 2, 0), z0=0.0, m=M(col), verts=8,
                            bevel=0.01, r2=r * 0.8))
    return out


def cabinet(w, d, h, col, z0=0.07, x=0.0, y=0.0, r=0.045):
    """Rounded appliance body standing on a recessed dark plinth."""
    return [rbox((w - 0.06, d - 0.06, z0 + 0.04), loc=(x, y, 0), z0=0.0, m=M("cocoa"), r=0.015, segs=1),
            rbox((w, d, h), loc=(x, y, 0), z0=z0, m=M(col), r=r, segs=2)]


def knob(x, y, z, r=0.032, col="cocoa", tip="mustard"):
    k = rcyl(r, 0.035, loc=(x, y - 0.0175, z), m=M(col), verts=10, bevel=0.008, rot=(90, 0, 0))
    p = rbox((0.008, 0.012, r * 0.9), loc=(x, y - 0.037, z + r * 0.35), m=M(tip), r=0.003, segs=1)
    return [k, p]


def handle_bar(x, y, z, length, col="steel_dark", vertical=False, out_=0.04):
    """Bar handle on standoffs, sticking out toward -Y from a face at y."""
    out = []
    if vertical:
        out.append(rcyl(0.014, length, loc=(x, y - out_, z), m=M(col), verts=8, bevel=0.005))
        for s in (-1, 1):
            out.append(rcyl(0.009, out_, loc=(x, y - out_ / 2, z + s * (length / 2 - 0.02)), m=M(col),
                            verts=6, bevel=0, rot=(90, 0, 0)))
    else:
        out.append(rcyl(0.014, length, loc=(x, y - out_, z), m=M(col), verts=8, bevel=0.005, rot=(0, 90, 0)))
        for s in (-1, 1):
            out.append(rcyl(0.009, out_, loc=(x + s * (length / 2 - 0.02), y - out_ / 2, z), m=M(col),
                            verts=6, bevel=0, rot=(90, 0, 0)))
    return out


def shallow_tray(w, d, x, y, z, col="steel", inner="offwhite", h=0.03, r=0.04):
    def sec(ww, dd, zz):
        return [(px + x, py + y, zz) for px, py in kit.rrect_pts(ww, dd, r, 2)]
    t = kit.loft([sec(w - 0.01, d - 0.01, z), sec(w, d, z + h * 0.8), sec(w - 0.012, d - 0.012, z + h),
                  sec(w - 0.03, d - 0.03, z + 0.008)], [M(col), M(inner)], band_mat=[0, 0, 0],
                 cap_bot=True, cap_top=True, name="tray", wn=False)
    t.data.polygons[-1].material_index = 1
    return t


def plate(x, y, z, r=0.16, col="white", rim="offwhite"):
    prof = [(r * 0.75, z), (r, z + 0.012), (r * 0.97, z + 0.024), (r * 0.88, z + 0.02), (r * 0.80, z + 0.012)]
    return lathe(prof, 18, [M(col), M(rim)], band_mat=[0, 0, 0, 0], cap_bot=True, cap_top=True, name="plate",
                 loc=(x, y, 0))


def squeeze_bottle(x, y, z, col):
    body = lathe([(0.03, z), (0.036, z + 0.01), (0.036, z + 0.13), (0.026, z + 0.15)], 10, [M(col)],
                 cap_bot=True, name="bottle")
    cap = lathe([(0.016, z + 0.148), (0.012, z + 0.17), (0.003, z + 0.205), (0.0, z + 0.206)], 8,
                [M("white")], name="nozzle")
    for o in (body, cap):
        move([o], (x, y, 0))
    return [body, cap]


def root(objs):
    return [join(objs, "root", (0, 0, 0))]


# ---------------------------------------------------------------------------
# st_grill 1.4 x 1.0
# ---------------------------------------------------------------------------
def build_st_grill():
    o = []
    o += cabinet(1.36, 0.84, 0.66, "steel", z0=0.07)
    # red trim belt + worktop
    o.append(rbox((1.38, 0.86, 0.11), z0=0.68, m=M("tomato"), r=0.04, segs=2))
    o.append(rbox((1.40, 0.90, 0.05), z0=0.79, m=M("steel"), r=0.022, segs=2))
    # doors + handles (main unit), drawer on the side unit
    for x in (-0.43, -0.03):
        o.append(rbox((0.36, 0.03, 0.44), loc=(x, -0.425, 0.41), m=M("offwhite"), r=0.02, segs=2))
        o += handle_bar(x, -0.44, 0.585, 0.20)
    o.append(rbox((0.32, 0.03, 0.44), loc=(0.48, -0.425, 0.41), m=M("tomato"), r=0.02, segs=2))
    o += handle_bar(0.48, -0.44, 0.585, 0.18)
    # knobs on the red belt
    for i, x in enumerate((-0.58, -0.40, -0.22, -0.04, 0.14)):
        o += knob(x, -0.43, 0.735, tip=("mustard" if i % 2 == 0 else "mint"))
    # griddle plate (dark) with a steel lip and splash guards
    o.append(rbox((0.92, 0.64, 0.035), loc=(-0.19, -0.04, 0), z0=0.84, m=M("#45434D"), r=0.012, segs=1))
    o.append(rbox((0.94, 0.05, 0.05), loc=(-0.19, -0.37, 0), z0=0.83, m=M("steel_dark"), r=0.015, segs=1))
    o.append(rbox((0.98, 0.05, 0.17), loc=(-0.19, 0.31, 0), z0=0.84, m=M("steel"), r=0.02, segs=2))
    o.append(rbox((0.05, 0.70, 0.12), loc=(-0.675, -0.03, 0), z0=0.84, m=M("steel"), r=0.02, segs=2))
    o.append(rbox((0.04, 0.70, 0.07), loc=(0.29, -0.03, 0), z0=0.84, m=M("steel"), r=0.015, segs=2))
    # hood on a back panel + chimney
    o.append(rbox((1.00, 0.08, 0.62), loc=(-0.19, 0.39, 0), z0=0.84, m=M("steel"), r=0.03, segs=2))
    o.append(rbox((1.02, 0.44, 0.22), loc=(-0.17, 0.22, 0), z0=1.40, m=M("tomato"), r=0.07, segs=3))
    o.append(rbox((0.98, 0.40, 0.04), loc=(-0.17, 0.22, 0), z0=1.38, m=M("steel_dark"), r=0.015, segs=1))
    o.append(rbox((1.04, 0.06, 0.05), loc=(-0.17, 0.0, 0), z0=1.50, m=M("mustard"), r=0.02, segs=2))
    cx, cy = 0.06, 0.26
    o.append(rcyl(0.10, 0.42, loc=(cx, cy, 0), z0=1.58, m=M("steel"), verts=14, bevel=0.012))
    o.append(rcyl(0.115, 0.05, loc=(cx, cy, 0), z0=1.70, m=M("steel_dark"), verts=14, bevel=0.012))
    o.append(rcyl(0.16, 0.06, loc=(cx, cy, 0), z0=2.06, m=M("tomato"), verts=14, bevel=0.02, r2=0.07))
    for s in (-1, 1):
        o.append(rcyl(0.012, 0.08, loc=(cx + s * 0.09, cy, 0), z0=2.0, m=M("steel_dark"), verts=6, bevel=0))
    # side table: output tray, bottles, spatula
    o.append(shallow_tray(0.34, 0.40, 0.50, -0.12, 0.84))
    o += squeeze_bottle(0.42, 0.28, 0.84, "tomato")
    o += squeeze_bottle(0.56, 0.28, 0.84, "mustard")
    o.append(rbox((0.13, 0.10, 0.008), loc=(-0.47, -0.20, 0.885), m=M("steel"), r=0.004, segs=1, rot=(0, 0, 20)))
    o.append(rbox((0.035, 0.16, 0.024), loc=(-0.535, -0.33, 0.89), m=M("tomato"), r=0.01, segs=1, rot=(0, 0, 20)))
    o += feet(1.2, 0.7, h=0.075)
    B.anchor("out", (0.50, -0.12, 0.85))
    B.anchor("cook", (-0.19, -0.04, 0.875))
    B.anchor("smoke", (cx, cy, 2.12))
    return root(o)


# ---------------------------------------------------------------------------
# st_fryer 1.0 x 1.0
# ---------------------------------------------------------------------------
def frame_ring(w, d, x, y, z0, z1, wall=0.05, r=0.05, col="steel"):
    def sec(ww, dd, zz):
        return [(px + x, py + y, zz) for px, py in kit.rrect_pts(ww, dd, r, 2)]
    return kit.loft([sec(w, d, z0), sec(w, d, z1 - 0.01), sec(w - 0.015, d - 0.015, z1),
                     sec(w - 2 * wall + 0.015, d - 2 * wall + 0.015, z1),
                     sec(w - 2 * wall, d - 2 * wall, z1 - 0.01), sec(w - 2 * wall, d - 2 * wall, z0)],
                    [M(col)], name="frame", wn=False)


def basket(x, y, z, w=0.25, d=0.30):
    """Fry basket sitting in the oil, full of fries, handle toward the front."""
    o = [frame_ring(w, d, x, y, z, z + 0.075, wall=0.022, r=0.03, col="steel_dark")]
    # fries heap inside
    o.append(rbox((w - 0.05, d - 0.05, 0.05), loc=(x, y, 0), z0=z + 0.03, m=M("fries"), r=0.02, segs=1))
    for i, (dx, dy, a) in enumerate([(-0.05, -0.06, 20), (0.04, 0.02, -35), (-0.02, 0.08, 60), (0.06, -0.08, 5)]):
        o.append(rbox((0.13, 0.025, 0.025), loc=(x + dx, y + dy, z + 0.085), m=M("#FFD866"), r=0.008,
                      segs=1, rot=(0, 8 * (1 if i % 2 else -1), a)))
    # wire handle + red grip
    o.append(tube([(x - 0.04, y - d / 2, z + 0.07), (x - 0.04, y - d / 2 - 0.12, z + 0.13)], 0.009,
                  M("steel_dark"), seg=6))
    o.append(tube([(x + 0.04, y - d / 2, z + 0.07), (x + 0.04, y - d / 2 - 0.12, z + 0.13)], 0.009,
                  M("steel_dark"), seg=6))
    o.append(rbox((0.13, 0.13, 0.045), loc=(x, y - d / 2 - 0.175, z + 0.155), m=M("tomato"), r=0.02, segs=2,
                  rot=(-26, 0, 0)))
    return o


def build_st_fryer():
    o = []
    o += cabinet(0.94, 0.86, 0.70, "mustard", z0=0.07)
    o.append(rbox((0.98, 0.90, 0.05), z0=0.79, m=M("steel"), r=0.022, segs=2))
    # front door + vents
    o.append(rbox((0.56, 0.03, 0.48), loc=(-0.12, -0.435, 0.42), m=M("cream"), r=0.025, segs=2))
    o += handle_bar(-0.12, -0.45, 0.61, 0.26)
    for i in range(3):
        o.append(rbox((0.20, 0.02, 0.025), loc=(0.30, -0.44, 0.30 + i * 0.07), m=M("gold_dark"), r=0.008, segs=1))
    # oil well with twin baskets (left/back), holding tray on the front-right
    o.append(frame_ring(0.66, 0.52, -0.13, 0.02, 0.83, 0.92, wall=0.05))
    o.append(rbox((0.58, 0.44, 0.03), loc=(-0.13, 0.02, 0), z0=0.84, m=M("#F2A93B"), r=0.01, segs=1))
    o += basket(-0.27, 0.06, 0.835)
    o += basket(0.01, 0.06, 0.835)
    o.append(shallow_tray(0.24, 0.36, 0.34, -0.20, 0.84, col="steel", inner="offwhite"))
    # back control panel with a display, knobs and a status light
    o.append(rbox((0.96, 0.12, 0.34), loc=(0, 0.39, 0), z0=0.84, m=M("mustard"), r=0.04, segs=2))
    o.append(rbox((0.24, 0.02, 0.10), loc=(-0.22, 0.325, 1.06), m=E("mint", 1.5), r=0.012, segs=1))
    o.append(rbox((0.28, 0.02, 0.14), loc=(-0.22, 0.333, 1.06), m=M("cocoa"), r=0.02, segs=1))
    o += knob(0.10, 0.33, 1.06, r=0.035)
    o += knob(0.24, 0.33, 1.06, r=0.035, tip="mint")
    o.append(ball(0.025, loc=(0.37, 0.325, 1.08), m=E("tomato", 2.0), seg=8, rings=5))
    o += feet(0.8, 0.72, h=0.075)
    B.anchor("out", (0.34, -0.20, 0.85))
    B.anchor("smoke", (-0.13, 0.04, 1.08))
    return root(o)


# ---------------------------------------------------------------------------
# st_soda 1.0 x 0.8
# ---------------------------------------------------------------------------
def cup_stack(x, y, z, n=5):
    prof = []
    h = 0.055
    for i in range(n):
        zz = z + i * h
        prof += [(0.044, zz), (0.052, zz + h - 0.012), (0.056, zz + h - 0.006)]
    prof += [(0.04, z + n * h), (0.0, z + n * h + 0.004)]
    return lathe(prof, 12, [M("white")], cap_bot=True, name="cups",
                 band_mat=None, face_mat=lambda i, j: None, loc=(x, y, 0))


def build_st_soda():
    o = []
    o += cabinet(0.96, 0.70, 0.70, "teal", z0=0.07)
    o.append(rbox((1.0, 0.78, 0.05), z0=0.79, m=M("steel"), r=0.022, segs=2))
    for x in (-0.23, 0.23):
        o.append(rbox((0.40, 0.03, 0.48), loc=(x, -0.355, 0.42), m=M("#3BC2D2"), r=0.025, segs=2))
        o += handle_bar(x + (0.13 if x < 0 else -0.13), -0.37, 0.42, 0.22, vertical=True, out_=0.02)
    # dispenser tower
    tx, ty = -0.02, 0.17
    o.append(rbox((0.56, 0.36, 0.66), loc=(tx, ty, 0), z0=0.84, m=M("teal"), r=0.08, segs=3))
    o.append(rbox((0.60, 0.40, 0.06), loc=(tx, ty, 0), z0=1.47, m=M("mint"), r=0.03, segs=2))
    # translucent-looking panel: glass with three syrup windows and a shine strip
    o.append(rbox((0.48, 0.03, 0.28), loc=(tx, ty - 0.18, 1.27), m=M("glass"), r=0.03, segs=2))
    for i, c in enumerate(("#7A3B22", "orange", "lettuce")):
        o.append(rbox((0.11, 0.02, 0.16), loc=(tx - 0.14 + i * 0.14, ty - 0.195, 1.245), m=M(c), r=0.03, segs=2))
    o.append(rbox((0.035, 0.015, 0.22), loc=(tx + 0.20, ty - 0.198, 1.28), m=M("white"), r=0.007, segs=1,
                  rot=(0, 12, 0)))
    # nozzle block, 3 nozzles + coloured levers
    o.append(rbox((0.50, 0.14, 0.08), loc=(tx, ty - 0.22, 1.08), m=M("steel"), r=0.03, segs=2))
    for i, c in enumerate(("tomato", "mustard", "mint")):
        nx = tx - 0.15 + i * 0.15
        o.append(rcyl(0.022, 0.05, loc=(nx, ty - 0.24, 1.02), m=M("steel_dark"), verts=8, bevel=0.005,
                      r2=0.015))
        o.append(rbox((0.06, 0.025, 0.12), loc=(nx, ty - 0.30, 1.02), m=M(c), r=0.012, segs=2, rot=(12, 0, 0)))
    # drip tray with grate
    o.append(rbox((0.50, 0.22, 0.04), loc=(tx, -0.17, 0), z0=0.84, m=M("steel_dark"), r=0.015, segs=1))
    for i in range(4):
        o.append(rbox((0.46, 0.012, 0.012), loc=(tx, -0.24 + i * 0.045, 0.882), m=M("steel"), r=0, segs=1))
    # cup rack on the left
    o.append(rbox((0.16, 0.30, 0.03), loc=(-0.40, 0.05, 0), z0=0.84, m=M("steel_dark"), r=0.01, segs=1))
    o.append(cup_stack(-0.40, 0.12, 0.87, 5))
    o.append(cup_stack(-0.40, -0.04, 0.87, 4))
    o.append(rbox((0.16, 0.02, 0.36), loc=(-0.40, 0.21, 0), z0=0.84, m=M("steel"), r=0.01, segs=1))
    o += feet(0.82, 0.6, h=0.075)
    B.anchor("out", (0.38, -0.20, 0.85))
    return root(o)


# ---------------------------------------------------------------------------
# st_shake 1.0 x 0.8 (root + spinner)
# ---------------------------------------------------------------------------
def build_st_shake():
    o = []
    o += cabinet(0.96, 0.72, 0.70, "cream", z0=0.07)
    o.append(rbox((0.98, 0.76, 0.10), z0=0.66, m=M("pink"), r=0.04, segs=2))
    o.append(rbox((1.0, 0.78, 0.05), z0=0.79, m=M("steel"), r=0.022, segs=2))
    for x in (-0.23, 0.23):
        o.append(rbox((0.40, 0.03, 0.40), loc=(x, -0.365, 0.38), m=M("offwhite"), r=0.025, segs=2))
        o.append(prism(kit.star_pts(5, 0.05, 0.022), 0.012, M("pink"), loc=(x, -0.378, 0.40), rot=(90, 0, 0),
                       bevel=0.003))
    # mixer machine: base, column, head (pink accent)
    mx, my = -0.18, 0.02
    o.append(rbox((0.34, 0.30, 0.08), loc=(mx, 0.12, 0), z0=0.84, m=M("pink"), r=0.035, segs=2))
    o.append(rcyl(0.10, 0.02, loc=(mx, my - 0.06, 0), z0=0.92, m=M("steel_dark"), verts=14, bevel=0.006))
    o.append(rbox((0.16, 0.14, 0.62), loc=(mx, 0.20, 0), z0=0.90, m=M("pink"), r=0.06, segs=3))
    o.append(rbox((0.24, 0.40, 0.20), loc=(mx, 0.05, 0), z0=1.46, m=M("pink"), r=0.08, segs=3))
    o.append(rbox((0.26, 0.05, 0.06), loc=(mx, 0.05, 0), z0=1.50, m=M("white"), r=0.02, segs=2))
    o.append(ball(0.03, loc=(mx, -0.15, 1.60), m=E("mint", 1.8), seg=8, rings=5))
    o += knob(mx + 0.1, -0.13, 1.53, r=0.025, col="white", tip="pink")
    # shake glasses + whipped cream on the right side
    for (gx, gy) in ((0.27, 0.18), (0.40, 0.10)):
        o.append(lathe([(0.04, 0.84), (0.045, 0.85), (0.055, 1.0), (0.058, 1.03)], 12,
                       [M("glass")], cap_bot=True, cap_top=True, loc=(gx, gy, 0), name="glass"))
        o[-1].data.polygons[-1].material_index = 0
        o.append(lathe([(0.052, 0.98), (0.058, 1.035), (0.035, 1.07), (0.0, 1.085)], 12,
                       [M("pink")], loc=(gx, gy, 0), name="shake"))
    o += feet(0.82, 0.6, h=0.075)
    rootp = root(o)[0]
    # spinner: spindle + tall steel cup, pivot on the spindle where it leaves the head
    sx, sy, sz = mx, my - 0.06, 1.46
    shaft = rcyl(0.012, 0.30, loc=(sx, sy, 0), z0=1.16, m=M("steel_dark"), verts=8, bevel=0)
    cup = lathe([(0.055, 0.94), (0.062, 0.95), (0.075, 1.18), (0.079, 1.195), (0.073, 1.20), (0.066, 1.17)],
                14, [M("steel"), M("steel_dark")], band_mat=[0, 0, 0, 0, 1], cap_bot=True, loc=(sx, sy, 0),
                name="cup", wn=False)
    band = lathe([(0.066, 1.02), (0.068, 1.06)], 14, [M("pink")], loc=(sx, sy, 0), name="band")
    foam = lathe([(0.066, 1.165), (0.04, 1.185), (0.0, 1.19)], 14, [M("pink")], loc=(sx, sy, 0), name="foam")
    spin = join([shaft, cup, band, foam], "spinner", (sx, sy, sz))
    B.parent_to(spin, rootp)
    B.anchor("out", (0.36, -0.20, 0.85))
    B.anchor("spin", (sx, sy, 1.07))
    return [rootp, spin]


# ---------------------------------------------------------------------------
# counter 2.4 x 0.9, h 0.95
# ---------------------------------------------------------------------------
def build_counter():
    o = []
    o.append(rbox((2.28, 0.74, 0.10), z0=0.0, m=M("cocoa"), r=0.02, segs=1))
    o.append(rbox((2.34, 0.80, 0.80), z0=0.06, m=M("cream"), r=0.05, segs=2))
    # tint trim band (wraps the front and sides) + a fixed kick band
    o.append(rbox((2.37, 0.83, 0.10), z0=0.66, m=T(), r=0.035, segs=2))
    o.append(rbox((2.36, 0.82, 0.05), z0=0.10, m=M("wood_dark"), r=0.02, segs=1))
    # raised front panels
    for i in range(6):
        x = -0.975 + i * 0.39
        o.append(rbox((0.32, 0.03, 0.42), loc=(x, -0.405, 0.405), m=M("offwhite"), r=0.03, segs=2))
    # wood top
    o.append(rbox((2.40, 0.90, 0.08), z0=0.87, m=M("wood"), r=0.03, segs=2))
    # display rack: low steel rail + 4 plates (left -> right)
    rx = [-0.98, -0.62, -0.26, 0.10]
    for x in rx:
        o.append(plate(x, -0.06, 0.95))
    o.append(rbox((1.50, 0.035, 0.035), loc=(-0.44, 0.17, 1.06), m=M("steel"), r=0.012, segs=1))
    for x in (-1.16, -0.80, -0.44, -0.08, 0.28):
        o.append(rcyl(0.014, 0.11, loc=(x, 0.17, 0), z0=0.95, m=M("steel_dark"), verts=6, bevel=0))
    # cash register at the +X end
    gx = 0.88
    o.append(rbox((0.38, 0.32, 0.12), loc=(gx, 0.08, 0), z0=0.95, m=M("tomato"), r=0.04, segs=2))
    o.append(rbox((0.34, 0.16, 0.06), loc=(gx, 0.16, 0), z0=1.05, m=M("cream"), r=0.025, segs=2, rot=(-14, 0, 0)))
    for i in range(3):
        for j in range(2):
            o.append(rbox((0.06, 0.045, 0.025), loc=(gx - 0.09 + i * 0.09, 0.13 + j * 0.06, 1.10 + j * 0.014),
                          m=M(("mustard", "mint", "pink")[i]), r=0.01, segs=1, rot=(-14, 0, 0)))
    o.append(rbox((0.30, 0.04, 0.06), loc=(gx, 0.255, 0.985), m=M("cream"), r=0.015, segs=1))
    o.append(rcyl(0.02, 0.16, loc=(gx, 0.0, 0), z0=1.05, m=M("steel_dark"), verts=8, bevel=0))
    o.append(rbox((0.32, 0.10, 0.20), loc=(gx, -0.01, 1.30), m=M("cocoa"), r=0.04, segs=2, rot=(-12, 0, 0)))
    o.append(rbox((0.26, 0.02, 0.145), loc=(gx, -0.062, 1.30), m=E("#7FF0E0", 1.4), r=0.02, segs=2,
                  rot=(-12, 0, 0)))
    B.anchor("register", (gx, -0.07, 1.30))
    B.anchor("cashier", (gx, 0.85, 0.0))
    B.anchor("queue", (gx, -0.90, 0.0))
    for i, x in enumerate(rx):
        B.anchor("rack%d" % i, (x, -0.06, 0.97))
    return root(o)


# ---------------------------------------------------------------------------
# drive_window 1.6 x 1.2
# ---------------------------------------------------------------------------
def striped_awning(x0, x1, stripes, y0, y1, z0, z1, thick=0.035, drop=0.10, name="awning"):
    """Curved awning sheet along X with alternating tint/white stripes and a
    scalloped valance. Back edge at (y0, z0), front edge at (y1, z1)."""
    prof = []
    n = 5
    for k in range(n + 1):
        t = k / n
        y = y0 + (y1 - y0) * t
        z = z0 + (z1 - z0) * t + 0.06 * math.sin(math.pi * t)
        prof.append((y, z))
    top = prof + [(y1, z1 - drop)]
    bot = [(y1 - thick, z1 - drop)] + [(y - 0.0, z - thick) for y, z in reversed(prof)]
    ring2d = top + bot
    rings = []
    xs = [x0 + (x1 - x0) * i / stripes for i in range(stripes + 1)]
    for x in xs:
        rings.append([(x, y, z) for y, z in ring2d])
    aw = kit.loft(rings, [T(), M("white")], closed=True, cap_bot=True, cap_top=True, name=name,
                  face_mat=lambda i, j: i % 2, recalc=True, angle=40)
    out = [aw]
    w = (x1 - x0) / stripes
    for i in range(stripes):
        cx = x0 + w * (i + 0.5)
        tab = prism(kit.arc_pts(0, 0, w / 2, w * 0.45, 180, 360, 6), thick, (T() if i % 2 == 0 else M("white")),
                    loc=(cx, y1 + 0.0, z1 - drop + 0.001), rot=(90, 0, 0), wn=False)
        out.append(tab)
    return out


def burger_badge(x, y, z, r=0.20, ring="mustard", face="cream"):
    """Round sign with an abstract burger silhouette, facing -Y."""
    o = [rcyl(r, 0.05, loc=(x, y, z), m=M(ring), verts=20, bevel=0.015, rot=(90, 0, 0)),
         rcyl(r * 0.82, 0.02, loc=(x, y - 0.03, z), m=M(face), verts=20, bevel=0.006, rot=(90, 0, 0))]
    s = r / 0.2
    o.append(prism(kit.arc_pts(0, 0, 0.11 * s, 0.075 * s, 0, 180, 8), 0.02, M("bun_top"),
                   loc=(x, y - 0.04, z + 0.005 * s), rot=(90, 0, 0), bevel=0.005))
    o.append(prism(kit.rrect_pts(0.24 * s, 0.035 * s, 0.017 * s, 2), 0.02, M("patty"),
                   loc=(x, y - 0.04, z - 0.03 * s), rot=(90, 0, 0), bevel=0.005))
    o.append(prism(kit.rrect_pts(0.24 * s, 0.022 * s, 0.011 * s, 2), 0.02, M("lettuce"),
                   loc=(x, y - 0.045, z - 0.003 * s), rot=(90, 0, 0), bevel=0.004))
    o.append(prism(kit.rrect_pts(0.21 * s, 0.04 * s, 0.02 * s, 2), 0.02, M("bun"),
                   loc=(x, y - 0.04, z - 0.075 * s), rot=(90, 0, 0), bevel=0.005))
    return o


def build_drive_window():
    o = []
    # counter behind the window
    o.append(rbox((1.50, 0.56, 0.10), loc=(0, 0.24, 0), z0=0.0, m=M("cocoa"), r=0.02, segs=1))
    o.append(rbox((1.56, 0.62, 0.80), loc=(0, 0.24, 0), z0=0.06, m=M("cream"), r=0.05, segs=2))
    o.append(rbox((1.58, 0.64, 0.08), loc=(0, 0.24, 0), z0=0.87, m=M("wood"), r=0.03, segs=2))
    # front wall: lower wall, two posts, header, tint base band
    wy = -0.15
    o.append(rbox((1.12, 0.16, 1.0), loc=(0, wy, 0), z0=0.0, m=M("cream"), r=0.03, segs=2))
    for s in (-1, 1):
        o.append(rbox((0.24, 0.18, 2.10), loc=(s * 0.68, wy, 0), z0=0.0, m=M("cream"), r=0.05, segs=2))
    o.append(rbox((1.60, 0.20, 0.30), loc=(0, wy, 0), z0=1.80, m=M("cream"), r=0.06, segs=2))
    o.append(rbox((1.60, 0.22, 0.16), loc=(0, wy, 0), z0=0.0, m=T(), r=0.03, segs=2))
    # window frame + sill shelf
    o.append(rbox((1.16, 0.30, 0.06), loc=(0, wy - 0.06, 0), z0=0.98, m=M("white"), r=0.025, segs=2))
    for s in (-1, 1):
        o.append(rbox((0.05, 0.20, 0.78), loc=(s * 0.56, wy, 0), z0=1.02, m=M("white"), r=0.015, segs=1))
    o.append(rbox((1.16, 0.20, 0.05), loc=(0, wy, 0), z0=1.77, m=M("white"), r=0.015, segs=1))
    # sliding pane (half open) with a shine strip
    o.append(rbox((0.56, 0.05, 0.74), loc=(-0.26, wy - 0.02, 1.41), m=M("offwhite"), r=0.02, segs=1))
    o.append(rbox((0.48, 0.055, 0.66), loc=(-0.26, wy - 0.02, 1.41), m=M("glass"), r=0.015, segs=1))
    o.append(rbox((0.05, 0.06, 0.40), loc=(-0.34, wy - 0.02, 1.45), m=M("white"), r=0.01, segs=1, rot=(0, 25, 0)))
    o.append(rbox((0.03, 0.06, 0.25), loc=(-0.24, wy - 0.02, 1.40), m=M("white"), r=0.01, segs=1, rot=(0, 25, 0)))
    o.append(rbox((1.10, 0.03, 0.02), loc=(0, wy - 0.06, 1.035), m=M("steel_dark"), r=0, segs=1))
    # awning with tint stripes
    o += striped_awning(-0.80, 0.80, 8, wy - 0.06, -0.56, 2.10, 1.88)
    # roof badge
    o.append(rbox((0.10, 0.06, 0.20), loc=(0, wy, 0), z0=2.05, m=M("cocoa"), r=0.02, segs=1))
    o += burger_badge(0, wy - 0.02, 2.42, r=0.24)
    # racks on the inner counter
    for i, x in enumerate((-0.34, 0.20)):
        o.append(plate(x, 0.24, 0.95))
        B.anchor("rack%d" % i, (x, 0.24, 0.97))
    B.anchor("cashier", (0.0, 0.95, 0.0))
    B.anchor("car", (0.0, -1.80, 0.0))
    return root(o)


# ---------------------------------------------------------------------------
# Tables & chair
# ---------------------------------------------------------------------------
def build_table2():
    o = []
    o.append(lathe([(0.25, 0.0), (0.26, 0.012), (0.24, 0.035), (0.10, 0.06), (0.05, 0.075), (0.0, 0.078)], 20,
                   [M("cocoa")], cap_bot=True, name="base"))
    o.append(rcyl(0.04, 0.52, loc=(0, 0, 0), z0=0.06, m=M("steel_dark"), verts=10, bevel=0))
    o.append(rcyl(0.10, 0.04, loc=(0, 0, 0), z0=0.535, m=M("cocoa"), verts=12, bevel=0.012, r2=0.07))
    o.append(lathe([(0.355, 0.570), (0.380, 0.585), (0.380, 0.607), (0.368, 0.620), (0.0, 0.620)], 24,
                   [M("wood_dark"), M("wood")], band_mat=[0, 0, 0, 1], cap_bot=True, name="top", wn=True))
    for i, s in enumerate((-1, 1)):
        B.anchor("seat%d" % i, (s * 0.62, 0.0, 0.0))
        B.anchor("tray%d" % i, (s * 0.19, 0.0, 0.62))
    return root(o)


def build_table4():
    o = []
    # cross base + pedestal
    for a in (45, 135):
        o.append(rbox((0.78, 0.09, 0.05), z0=0.0, m=M("cocoa"), r=0.022, segs=2, rot=(0, 0, a)))
    o.append(rcyl(0.065, 0.50, loc=(0, 0, 0), z0=0.04, m=M("steel"), verts=12, bevel=0))
    o.append(rcyl(0.12, 0.05, loc=(0, 0, 0), z0=0.52, m=M("cocoa"), verts=14, bevel=0.012, r2=0.08))
    # laminate top with a chrome edge band
    pts = kit.rrect_pts(1.0, 1.0, 0.14, 3)
    o.append(prism(pts, 0.05, M("steel"), z0=0.555, bevel=0.015, segs=2))
    o.append(prism(kit.rrect_pts(0.94, 0.94, 0.11, 3), 0.012, M("tomato"), z0=0.608, bevel=0.004, segs=1))
    seats = [(0.0, -0.75), (0.75, 0.0), (0.0, 0.75), (-0.75, 0.0)]
    for i, (x, y) in enumerate(seats):
        B.anchor("seat%d" % i, (x, y, 0.0))
        B.anchor("tray%d" % i, (x * 0.35, y * 0.35, 0.62))
    return root(o)


def build_chair():
    o = []
    for sx in (-1, 1):
        for sy in (-1, 1):
            o.append(rcyl(0.024, 0.34, loc=(sx * 0.16, sy * 0.15, 0), z0=0.0, m=M("wood_dark"), verts=8, bevel=0,
                          r2=0.018))
    o.append(rbox((0.42, 0.40, 0.05), loc=(0, 0, 0), z0=0.31, m=M("wood"), r=0.02, segs=2))
    o.append(rbox((0.40, 0.38, 0.06), loc=(0, -0.005, 0), z0=0.34, m=T(), r=0.03, segs=2))
    for sx in (-1, 1):
        o.append(rcyl(0.022, 0.46, loc=(sx * 0.16, 0.17, 0), z0=0.34, m=M("wood_dark"), verts=8, bevel=0))
    o.append(rbox((0.42, 0.06, 0.24), loc=(0, 0.175, 0), z0=0.56, m=T(), r=0.03, segs=2))
    return root(o)


# ---------------------------------------------------------------------------
# trash bin, pads, register stand
# ---------------------------------------------------------------------------
def build_trash_bin():
    o = []
    o.append(lathe([(0.20, 0.0), (0.225, 0.015), (0.245, 0.62), (0.25, 0.64)], 16, [M("mint")],
                   cap_bot=True, name="body"))
    o.append(lathe([(0.226, 0.06), (0.234, 0.07), (0.234, 0.11), (0.226, 0.12)], 16, [M("teal")], name="band"))
    o.append(lathe([(0.25, 0.62), (0.268, 0.63), (0.268, 0.68), (0.25, 0.69)], 16, [M("teal")], name="rim"))
    # dome lid with a front flap
    o.append(lathe([(0.255, 0.68), (0.25, 0.74), (0.21, 0.82), (0.12, 0.88), (0.0, 0.90)], 16,
                   [M("offwhite")], name="lid"))
    o.append(rbox((0.22, 0.05, 0.13), loc=(0, -0.205, 0.775), m=M("cocoa"), r=0.03, segs=2, rot=(-38, 0, 0)))
    o.append(rbox((0.20, 0.04, 0.115), loc=(0, -0.225, 0.785), m=M("teal"), r=0.03, segs=2, rot=(-38, 0, 0)))
    o.append(rcyl(0.035, 0.03, loc=(0, 0, 0), z0=0.895, m=M("teal"), verts=10, bevel=0.01))
    # burger icon on the front
    s = 0.8
    y = -0.246
    o.append(prism(kit.arc_pts(0, 0, 0.11 * s, 0.075 * s, 0, 180, 8), 0.02, M("bun_top"),
                   loc=(0, y, 0.40), rot=(90, 0, 0), bevel=0.005, bottom=False))
    o.append(prism(kit.rrect_pts(0.24 * s, 0.035 * s, 0.014, 2), 0.02, M("patty"), loc=(0, y, 0.375),
                   rot=(90, 0, 0), bevel=0.005))
    o.append(prism(kit.rrect_pts(0.21 * s, 0.04 * s, 0.016, 2), 0.02, M("bun"), loc=(0, y + 0.002, 0.338),
                   rot=(90, 0, 0), bevel=0.005))
    B.anchor("in", (0, -0.16, 0.92))
    return root(o)


def pad_base(R, seg=28):
    k = R / 0.65
    prof = [(0.60 * k, 0.0), (0.65 * k, 0.012), (0.65 * k, 0.030), (0.632 * k, 0.050), (0.596 * k, 0.054),
            (0.565 * k, 0.040), (0.545 * k, 0.026), (0.44 * k, 0.026), (0.425 * k, 0.031), (0.41 * k, 0.038),
            (0.39 * k, 0.040)]
    p = lathe(prof, seg, [M("white"), M("offwhite"), T()], band_mat=[1, 0, 0, 0, 0, 0, 1, 1, 2, 2],
              cap_top=True, name="pad")
    return p


def build_pad():
    return root([pad_base(0.65, 30)])


def build_upgrade_pad():
    o = [pad_base(0.55, 24)]
    arrow = kit.ccw([(0, 0.21), (0.17, 0.03), (0.065, 0.03), (0.065, -0.18), (-0.065, -0.18), (-0.065, 0.03),
                     (-0.17, 0.03)])
    o.append(prism(arrow, 0.022, M("white"), z0=0.03, bevel=0.008, segs=2, loc=(0, -0.01, 0)))
    return root(o)


def build_staff_pad():
    o = [pad_base(0.55, 24)]
    o.append(prism(kit.rrect_pts(0.40, 0.27, 0.05, 2), 0.018, M("white"), z0=0.03, bevel=0.006, segs=1,
                   loc=(0, -0.02, 0), bottom=False))
    o.append(prism(kit.rrect_pts(0.10, 0.05, 0.02, 2), 0.03, M("steel_dark"), z0=0.03, bottom=False,
                   loc=(0, 0.125, 0)))
    o.append(rcyl(0.055, 0.012, loc=(-0.10, -0.02, 0), z0=0.046, m=M("mustard"), verts=12, bevel=0))
    o.append(prism(kit.rrect_pts(0.16, 0.035, 0.017, 2), 0.01, M("cocoa"), z0=0.046, loc=(0.06, 0.0, 0), bottom=False))
    o.append(prism(kit.rrect_pts(0.11, 0.03, 0.015, 2), 0.01, M("gray"), z0=0.046, loc=(0.035, -0.06, 0), bottom=False))
    return root(o)


def paw(x, y, z, s, col):
    o = [prism(kit.circle_pts(0.055 * s, 10, 1.15, 0.9), 0.008, M(col), z0=z, loc=(x, y - 0.01 * s, 0),
               bottom=False)]
    for ang, dist in ((-50, 0.085), (-17, 0.10), (17, 0.10), (50, 0.085)):
        a = math.radians(90 + ang) + math.pi
        tx = x + math.cos(a) * dist * s * 0.95
        ty = y - 0.01 * s + math.sin(a) * dist * s
        o.append(prism(kit.circle_pts(0.024 * s, 7, 0.85, 1.1), 0.008, M(col), z0=z, loc=(tx, ty, 0),
                       bottom=False))
    return o


def build_register_stand():
    o = [prism(kit.rrect_pts(0.58, 0.58, 0.12, 3), 0.018, M("mustard"), z0=0.0, bevel=0.007, segs=2,
               bottom=False)]
    o.append(prism(kit.rrect_pts(0.48, 0.48, 0.08, 2), 0.004, M("cream"), z0=0.016, bottom=False))
    o.append(prism(kit.rrect_pts(0.44, 0.44, 0.065, 2), 0.004, M("mustard"), z0=0.018, bottom=False))
    o += paw(-0.095, 0.0, 0.022, 1.0, "cocoa")
    o += paw(0.095, 0.03, 0.022, 1.0, "cocoa")
    return root(o)


ST = {"ao": 0.35, "ao_strength": 0.8}
MODELS = {
    "st_grill": (build_st_grill, 4000, ST),
    "st_fryer": (build_st_fryer, 3500, ST),
    "st_soda": (build_st_soda, 3500, ST),
    "st_shake": (build_st_shake, 3500, ST),
    "counter": (build_counter, 4000, ST),
    "drive_window": (build_drive_window, 3000, ST),
    "table2": (build_table2, 1200, ST),
    "table4": (build_table4, 1500, ST),
    "chair": (build_chair, 600, ST),
    "trash_bin": (build_trash_bin, 1000, ST),
    "pad": (build_pad, 800, {"ao": 0.15, "ao_strength": 0.6}),
    "upgrade_pad": (build_upgrade_pad, 800, {"ao": 0.15, "ao_strength": 0.6}),
    "staff_pad": (build_staff_pad, 800, {"ao": 0.15, "ao_strength": 0.6}),
    "register_stand": (build_register_stand, 800, {"ao": 0.15, "ao_strength": 0.6}),
}

if __name__ == "__main__":
    kit.run(MODELS)
