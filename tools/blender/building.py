"""
Building pieces (ASSET_CONTRACT §6, first half): floors, walls, posts, door,
ground tiles, road, crosswalk. One part `root`, pivot at the bottom centre.

Floor/ground tiles are hand-built tiny meshes (instanced hundreds of times):
the walkable top surface is exactly z = 0, slabs extend down to -0.04, and
there are no bottom faces.

    Blender -b --factory-startup --python tools/blender/building.py [-- wall floor_wood ...]
"""
import os
import sys
import math

import bmesh

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from kit import M, T, E, B, lathe, prism, tube, rbox, rcyl, ball, join, move  # noqa: E402

TH = 0.04  # slab thickness below z = 0
CREAM = "cream"


# ---------------------------------------------------------------------------
# Tile construction helpers
# ---------------------------------------------------------------------------
class Mesh:
    """Tiny bmesh builder with shared vertices and per-face material."""

    def __init__(self):
        self.bm = bmesh.new()
        self.vmap = {}

    def v(self, p):
        k = tuple(round(c, 6) for c in p)
        if k not in self.vmap:
            self.vmap[k] = self.bm.verts.new(k)
        return self.vmap[k]

    def f(self, pts, mi=0):
        face = self.bm.faces.new([self.v(p) for p in pts])
        face.material_index = mi
        return face

    def obj(self, name, mats, angle=None):
        o = kit._mk(self.bm, name, mats)
        return kit.finish(o, angle=angle)


def slab(m, x0, y0, x1, y1, top_mi, side_mi, c=0.0, cz=0.0, sides="nesw", segs=1, top=True):
    """Box slab from z=0 down to -TH with a chamfer of width c / depth cz along its
    top border (segs=2 gives a rounded 2-step chamfer). Only listed sides get
    side faces (n=-Y? no: 's' = -Y front, 'n' = +Y, 'w' = -X, 'e' = +X)."""
    rings = [[(x0 + c, y0 + c), (x1 - c, y0 + c), (x1 - c, y1 - c), (x0 + c, y1 - c)]]
    zs = [0.0]
    if c > 0:
        if segs == 2:
            k = 0.62
            rings.append([(x0 + c * (1 - k), y0 + c * (1 - k)), (x1 - c * (1 - k), y0 + c * (1 - k)),
                          (x1 - c * (1 - k), y1 - c * (1 - k)), (x0 + c * (1 - k), y1 - c * (1 - k))])
            zs.append(-cz * 0.35)
        rings.append([(x0, y0), (x1, y0), (x1, y1), (x0, y1)])
        zs.append(-cz)
    if top:
        m.f([(x, y, 0.0) for x, y in rings[0]], top_mi)
    for r in range(len(rings) - 1):
        a, b = rings[r], rings[r + 1]
        for i in range(4):
            j = (i + 1) % 4
            m.f([(b[i][0], b[i][1], zs[r + 1]), (b[j][0], b[j][1], zs[r + 1]),
                 (a[j][0], a[j][1], zs[r]), (a[i][0], a[i][1], zs[r])], top_mi)
    outer = rings[-1]
    zt = zs[-1]
    names = "senw"  # edges: 0->1 is -Y (s), 1->2 is +X (e), 2->3 is +Y (n), 3->0 is -X (w)
    for i in range(4):
        if names[i] not in sides:
            continue
        j = (i + 1) % 4
        m.f([(outer[i][0], outer[i][1], -TH), (outer[j][0], outer[j][1], -TH),
             (outer[j][0], outer[j][1], zt), (outer[i][0], outer[i][1], zt)], side_mi)


# ---------------------------------------------------------------------------
# Floors
# ---------------------------------------------------------------------------
def build_floor_wood():
    m = Mesh()
    cols = [M("#DDA062"), M("#CC8C51"), M("#E3AE72"), M("#B9784A")]
    w = 1.0 / 3
    for i in range(3):
        x0, x1 = -0.5 + i * w, -0.5 + (i + 1) * w
        sides = "sn" + ("w" if i == 0 else "") + ("e" if i == 2 else "")
        slab(m, x0, -0.5, x1, 0.5, i, 3, c=0.012, cz=0.008, sides=sides)
    return [m.obj("root", cols, angle=None)]


def build_floor_checker():
    m = Mesh()
    c, cz = 0.012, 0.008
    a = 0.5 - c
    # top: 2x2 squares (cream / tint)
    xs = [-a, 0.0, a]
    for iy in range(2):
        for ix in range(2):
            mi = (ix + iy) % 2
            m.f([(xs[ix], xs[iy], 0), (xs[ix + 1], xs[iy], 0), (xs[ix + 1], xs[iy + 1], 0), (xs[ix], xs[iy + 1], 0)], mi)
    # chamfer ring (8 quads, coloured like the adjacent square)
    inner = [(-a, -a), (0, -a), (a, -a), (a, 0), (a, a), (0, a), (-a, a), (-a, 0)]
    outer = [(-0.5, -0.5), (0, -0.5), (0.5, -0.5), (0.5, 0), (0.5, 0.5), (0, 0.5), (-0.5, 0.5), (-0.5, 0)]
    sq = [0, 1, 1, 0, 0, 1, 1, 0]  # material of the square each chamfer quad touches
    for i in range(8):
        j = (i + 1) % 8
        m.f([(outer[i][0], outer[i][1], -cz), (outer[j][0], outer[j][1], -cz),
             (inner[j][0], inner[j][1], 0), (inner[i][0], inner[i][1], 0)], sq[i])
    # sides (pentagons: 3 top verts, 2 bottom)
    for k in range(4):
        p0, pm, p1 = outer[2 * k], outer[2 * k + 1], outer[(2 * k + 2) % 8]
        m.f([(p0[0], p0[1], -TH), (p1[0], p1[1], -TH), (p1[0], p1[1], -cz), (pm[0], pm[1], -cz),
             (p0[0], p0[1], -cz)], 2)
    return [m.obj("root", [M(CREAM), T(), M("#E8DCC4")], angle=None)]


def build_floor_terrazzo():
    m = Mesh()
    c, cz = 0.014, 0.008
    a = 0.5 - c
    bm = m.bm
    # top surface with chip holes (all coplanar at z = 0 -> no z-fighting)
    chips = [((-0.30, -0.22), 0.060, 0.3, 1), ((0.18, -0.31), 0.050, 1.4, 2), ((0.05, 0.06), 0.065, 2.2, 3),
             ((-0.14, 0.30), 0.055, 0.9, 4), ((0.33, 0.24), 0.060, 2.8, 1), ((-0.36, 0.08), 0.045, 1.9, 2),
             ((0.32, -0.06), 0.045, 0.5, 4), ((-0.05, -0.36), 0.045, 2.5, 3), ((0.10, 0.36), 0.04, 1.1, 2),
             ((-0.16, -0.06), 0.04, 0.2, 1)]
    edges = []

    def loop(pts):
        vs = [bm.verts.new((x, y, 0.0)) for x, y in pts]
        return vs, [bm.edges.new((vs[i], vs[(i + 1) % len(vs)])) for i in range(len(vs))]
    sq_vs, e = loop([(-a, -a), (a, -a), (a, a), (-a, a)])
    edges += e
    chip_loops = []
    for (cx, cy), r, rot, mi in chips:
        pts = [(cx + r * math.cos(rot + k * 2.1 + (0.35 if k == 1 else 0)), cy + r * 0.8 * math.sin(rot + k * 2.1))
               for k in range(3)]
        pts = kit.ccw(pts)
        vs, e = loop(pts)
        edges += e
        chip_loops.append((vs, mi))
    res = bmesh.ops.triangle_fill(bm, use_beauty=True, use_dissolve=False, edges=edges)
    for f in bm.faces:
        f.material_index = 0
        if f.normal.z < 0:
            f.normal_flip()
    for vs, mi in chip_loops:
        f = bm.faces.new(vs)
        f.material_index = mi
        if f.normal.z < 0:
            f.normal_flip()
    # register square corners in the vertex map so the chamfer shares them
    for v in sq_vs:
        m.vmap[tuple(round(c_, 6) for c_ in v.co)] = v
    inner = [(-a, -a), (a, -a), (a, a), (-a, a)]
    outer = [(-0.5, -0.5), (0.5, -0.5), (0.5, 0.5), (-0.5, 0.5)]
    for i in range(4):
        j = (i + 1) % 4
        m.f([(outer[i][0], outer[i][1], -cz), (outer[j][0], outer[j][1], -cz),
             (inner[j][0], inner[j][1], 0), (inner[i][0], inner[i][1], 0)], 0)
        m.f([(outer[i][0], outer[i][1], -TH), (outer[j][0], outer[j][1], -TH),
             (outer[j][0], outer[j][1], -cz), (outer[i][0], outer[i][1], -cz)], 5)
    mats = [M(CREAM), M("tomato"), M("teal"), M("mustard"), M("cocoa"), M("#E8DCC4")]
    return [m.obj("root", mats, angle=None)]


def build_floor_tile():
    m = Mesh()
    slab(m, -0.5, -0.5, 0.5, 0.5, 0, 1, c=0.035, cz=0.016, segs=2)
    return [m.obj("root", [T(), M("#E8E2DA")], angle=None)]


# ---------------------------------------------------------------------------
# Ground, road, crosswalk
# ---------------------------------------------------------------------------
def build_ground_grass():
    m = Mesh()
    slab(m, -0.5, -0.5, 0.5, 0.5, 0, 0)
    # two tiny tufts (3 tris each)
    x, y = -0.18, 0.16
    for k, (lean, h) in enumerate(((0.0, 0.085), (2.1, 0.065), (4.2, 0.07))):
        bx, by = x + 0.012 * math.cos(lean), y + 0.012 * math.sin(lean)
        base = [(bx + 0.013 * math.cos(a), by + 0.013 * math.sin(a), 0.0) for a in (0.3, 2.4, 4.5)]
        tip = (bx + 0.035 * math.cos(lean), by + 0.035 * math.sin(lean), h)
        for i in range(3):
            m.f([base[i], base[(i + 1) % 3], tip], 1)
    return [m.obj("root", [M("grass"), M("lettuce_dark")], angle=None)]


def build_ground_sidewalk():
    m = Mesh()
    slab(m, -0.5, -0.5, 0.5, 0.5, 0, 1, c=0.03, cz=0.014, segs=2)
    return [m.obj("root", [M("#D7D2CA"), M("#B9B3AA")], angle=None)]


def build_road():
    m = Mesh()
    w = 0.06
    ys = [-1.0, -0.5, 0.5, 1.0]
    m.f([(-1, -1, 0), (-w, -1, 0), (-w, -0.5, 0), (-w, 0.5, 0), (-w, 1, 0), (-1, 1, 0)], 0)
    m.f([(w, -1, 0), (1, -1, 0), (1, 1, 0), (w, 1, 0), (w, 0.5, 0), (w, -0.5, 0)], 0)
    for i in range(3):
        m.f([(-w, ys[i], 0), (w, ys[i], 0), (w, ys[i + 1], 0), (-w, ys[i + 1], 0)], 1 if i == 1 else 0)
    m.f([(-1, -1, -TH), (1, -1, -TH), (1, -1, 0), (w, -1, 0), (-w, -1, 0), (-1, -1, 0)], 2)
    m.f([(1, 1, -TH), (-1, 1, -TH), (-1, 1, 0), (-w, 1, 0), (w, 1, 0), (1, 1, 0)], 2)
    m.f([(1, -1, -TH), (1, 1, -TH), (1, 1, 0), (1, -1, 0)], 2)
    m.f([(-1, 1, -TH), (-1, -1, -TH), (-1, -1, 0), (-1, 1, 0)], 2)
    return [m.obj("root", [M("asphalt"), M("mustard"), M("#3C404B")], angle=None)]


def build_crosswalk():
    m = Mesh()
    xs = [-1.0, -0.92]
    x = -0.92
    for k in range(5):
        xs += [x + 0.24] if k == 4 else [x + 0.24, x + 0.40]
        x += 0.40
    xs.append(1.0)
    yb, yt = -0.75, 0.75
    for i in range(len(xs) - 1):
        white = (i % 2 == 1)
        m.f([(xs[i], yb, 0), (xs[i + 1], yb, 0), (xs[i + 1], yt, 0), (xs[i], yt, 0)], 1 if white else 0)
    front = [(-1, -1, 0), (1, -1, 0)] + [(x, yb, 0) for x in reversed(xs)]
    m.f(front, 0)
    back = [(1, 1, 0), (-1, 1, 0)] + [(x, yt, 0) for x in xs]
    m.f(back, 0)
    m.f([(-1, -1, -TH), (1, -1, -TH), (1, -1, 0), (-1, -1, 0)], 2)
    m.f([(1, 1, -TH), (-1, 1, -TH), (-1, 1, 0), (1, 1, 0)], 2)
    m.f([(1, -1, -TH), (1, 1, -TH), (1, 1, 0), (1, yt, 0), (1, yb, 0), (1, -1, 0)], 2)
    m.f([(-1, 1, -TH), (-1, -1, -TH), (-1, -1, 0), (-1, yb, 0), (-1, yt, 0), (-1, 1, 0)], 2)
    return [m.obj("root", [M("asphalt"), M("offwhite"), M("#3C404B")], angle=None)]


# ---------------------------------------------------------------------------
# Walls, post, door
# ---------------------------------------------------------------------------
BAND = 0.16


def wall_profile(z_top=1.20, half=0.08, band=0.09, z_bottom=0.0, band_h=BAND, cap=True):
    """Closed YZ outline (front -> top -> back) and the material of each edge
    (0 tint band, 1 cream, 2 cap)."""
    pts, mats = [], []
    pts += [(-band, z_bottom), (-band, band_h - 0.02), (-band + 0.006, band_h - 0.006), (-half, band_h)]
    mats += [0, 0, 0, 1]
    zc = z_top - half if cap else z_top
    pts.append((-half, zc))
    mats.append(1)
    if cap:
        n = 6
        for k in range(1, n):
            a = math.pi - math.pi * k / n
            pts.append((half * math.cos(a), zc + half * math.sin(a)))
            mats.append(2)
        pts.append((half, zc))
        mats.append(1)
    else:
        pts.append((half, zc))
        mats.append(1)
    pts += [(half, band_h), (band - 0.006, band_h - 0.006), (band, band_h - 0.02), (band, z_bottom)]
    mats += [0, 0, 0, 0]
    return pts, mats


def extrude_x(profile, mats_edge, x0, x1, mats, name="ext", caps=True, cap_mi=1):
    """Extrude a YZ outline (open at the bottom edge) along X."""
    bm = bmesh.new()
    L = [bm.verts.new((x0, y, z)) for y, z in profile]
    R = [bm.verts.new((x1, y, z)) for y, z in profile]
    n = len(profile)
    for i in range(n - 1):
        f = bm.faces.new((L[i], L[i + 1], R[i + 1], R[i]))
        f.material_index = mats_edge[i]
    if caps:
        f = bm.faces.new(list(reversed(R)))
        f.material_index = cap_mi
        f = bm.faces.new(L)
        f.material_index = cap_mi
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    o = kit._mk(bm, name, mats)
    return kit.finish(o, angle=50)


def build_wall():
    prof, me = wall_profile()
    o = extrude_x(prof, me, -0.5, 0.5, [T(), M(CREAM), M(CREAM)], name="wall")
    return [join([o], "root")]


def glass_panel(w, d, z0, z1, nx=4, nz=3, col="glass"):
    """Glass slab whose front/back faces are a grid (interior vertices keep the
    baked AO bright instead of inheriting the occluded frame corners)."""
    m = Mesh()
    xs = [-w / 2 + w * i / nx for i in range(nx + 1)]
    zs = [z0 + (z1 - z0) * k / nz for k in range(nz + 1)]
    for sy in (-1, 1):
        y = sy * d / 2
        for i in range(nx):
            for k in range(nz):
                q = [(xs[i], y, zs[k]), (xs[i + 1], y, zs[k]), (xs[i + 1], y, zs[k + 1]), (xs[i], y, zs[k + 1])]
                m.f(q if sy < 0 else list(reversed(q)), 0)
    return m.obj("glass", [M(col)], angle=None)


def build_wall_window():
    o = []
    # lower wall with sill-ish rounded top
    prof, me = wall_profile(z_top=0.56, half=0.08)
    o.append(extrude_x(prof, me, -0.5, 0.5, [T(), M(CREAM), M(CREAM)], name="low"))
    # top beam with the same rounded cap as the plain wall
    beam = [(-0.08, 1.04), (-0.08, 1.12)] + [(0.08 * math.cos(math.pi - math.pi * k / 6),
                                                1.12 + 0.08 * math.sin(math.pi - math.pi * k / 6)) for k in range(1, 6)] + \
           [(0.08, 1.12), (0.08, 1.04)]
    bm_mats = [1] * (len(beam) - 1)
    b = extrude_x(beam, bm_mats, -0.5, 0.5, [T(), M(CREAM)], name="beam")
    o.append(b)
    # half posts at the ends (adjacent windows form one mullion)
    for s in (-1, 1):
        o.append(rbox((0.05, 0.16, 0.52), loc=(s * 0.475, 0, 0), z0=0.53, m=M("offwhite"), r=0.012, segs=1))
    # glass + shine strips (both faces)
    o.append(glass_panel(0.92, 0.10, 0.54, 1.045, nx=4, nz=3))
    for sy in (-1, 1):
        for (x, w, h) in ((-0.20, 0.05, 0.34), (-0.10, 0.025, 0.24)):
            o.append(rbox((w, 0.01, h), loc=(x, sy * 0.051, 0.80), m=M("white"), r=0.004, segs=1,
                          rot=(0, 32, 0)))
    o.append(rbox((0.92, 0.05, 0.03), loc=(0, 0, 0), z0=0.54, m=M("offwhite"), r=0.01, segs=1))
    return [join(o, "root")]


def build_wall_post():
    def sec(w, z, n=2):
        return [(x, y, z) for x, y in kit.rrect_pts(w, w, w * 0.25, n)]
    rings = [sec(0.26, 0.0), sec(0.26, BAND - 0.02), sec(0.24, BAND), sec(0.24, 1.17), sec(0.26, 1.18),
             sec(0.26, 1.27), sec(0.21, 1.30)]
    p = kit.loft(rings, [T(), M(CREAM)], band_mat=[0, 0, 1, 0, 0, 0], cap_top=True, name="post", angle=50)
    p.data.polygons[-1].material_index = 0
    return [join([p], "root")]


def build_door_frame():
    from stations import striped_awning
    o = []

    def post(x):
        def sec(w, d, z):
            return [(px + x, py, z) for px, py in kit.rrect_pts(w, d, 0.05, 1)]
        return kit.loft([sec(0.26, 0.26, 0.0), sec(0.26, 0.26, BAND - 0.02), sec(0.24, 0.24, BAND),
                         sec(0.24, 0.24, 2.02)], [T(), M(CREAM)], band_mat=[0, 0, 1], name="post", angle=50)
    for s in (-1, 1):
        o.append(post(s * 0.67))
    o.append(rbox((1.60, 0.26, 0.34), loc=(0, 0, 0), z0=1.98, m=M(CREAM), r=0.08, segs=1))
    # arch fillets in the inner top corners
    for s in (-1, 1):
        r = 0.24
        cx = s * (0.55 - r)
        pts = [(s * 0.55, 2.0), (s * 0.55, 2.0 - r)] + \
              [(cx + s * r * math.cos(math.radians(a)), 2.0 - r + r * math.sin(math.radians(a)))
               for a in (20, 45, 70)] + [(cx, 2.0)]
        pts = kit.ccw(pts)
        o.append(prism(pts, 0.20, M(CREAM), loc=(0, 0.10, 0), rot=(90, 0, 0), wn=False, angle=40))
    o += striped_awning(-0.78, 0.78, 6, -0.12, -0.62, 2.26, 1.96, drop=0.10)
    return [join(o, "root")]


BUILD_AO = {"ao": 0.3, "ao_strength": 0.75}
TILE = {"ao": 0.06, "ao_strength": 0.5, "prev_kw": {"tint_color": "#7FC8E8"}}
MODELS = {
    "floor_wood": (build_floor_wood, 60, TILE),
    "floor_checker": (build_floor_checker, 40, TILE),
    "floor_terrazzo": (build_floor_terrazzo, 80, TILE),
    "floor_tile": (build_floor_tile, 40, TILE),
    "ground_grass": (build_ground_grass, 20, TILE),
    "ground_sidewalk": (build_ground_sidewalk, 40, TILE),
    "road": (build_road, 40, TILE),
    "crosswalk": (build_crosswalk, 80, TILE),
    "wall": (build_wall, 300, BUILD_AO),
    "wall_window": (build_wall_window, 500, BUILD_AO),
    "wall_post": (build_wall_post, 200, BUILD_AO),
    "door_frame": (build_door_frame, 600, BUILD_AO),
}

if __name__ == "__main__":
    kit.run(MODELS)
