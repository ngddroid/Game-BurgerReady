"""
characters.py - Bun Voyage! animal-folk cast (docs/ASSET_CONTRACT.md section 1).

One parametric chibi builder shared by all ten species: the torso, sleeves,
shorts, limbs and eyes are identical across the cast; each species only swaps
its fur colours, ears, muzzle/beak, face markings and tail.

Outputs
    app/src/main/assets/models/char_<species>.bvm
    tools/previews/characters/char_<species>.png      (3/4 front view)
    tools/previews/characters/lineup_characters.png   (all ten side by side)
    tools/previews/characters/char_corgi_toque.png    (corgi wearing hat_toque)

Run
    ~/Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup \
        --python tools/blender/characters.py -- [species ...] [--no-preview] [--no-lineup]

The geometry helpers in this file (Part, sweep, cube-sphere ellipsoids, face
decals, preview rendering) are also imported by hats.py and pets.py.
"""
import os
import sys
import math
from contextlib import contextmanager

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpy
import bmesh
from mathutils import Vector, Matrix

import bvlib as B

V = Vector
PREVIEW_CHAR_DIR = os.path.join(B.PREVIEW_DIR, "characters")

# ---------------------------------------------------------------------------
# Geometry kernel (builds straight into one bmesh per part; no bpy.ops)
# ---------------------------------------------------------------------------
CUBE_FACES = [  # (normal, a1, a2) with a1 x a2 == normal  -> outward winding
    ((1, 0, 0), (0, 1, 0), (0, 0, 1)),
    ((-1, 0, 0), (0, 0, 1), (0, 1, 0)),
    ((0, 1, 0), (0, 0, 1), (1, 0, 0)),
    ((0, -1, 0), (1, 0, 0), (0, 0, 1)),
    ((0, 0, 1), (1, 0, 0), (0, 1, 0)),
    ((0, 0, -1), (0, 1, 0), (1, 0, 0)),
]


def frame(nrm, up=(0.0, 0.0, 1.0)):
    """3x3 rotation whose Z column is `nrm`, X column horizontal (up x nrm)."""
    n = V(nrm).normalized()
    x = V(up).cross(n)
    if x.length < 1e-6:
        x = V((0.0, 1.0, 0.0)).cross(n)
    x.normalize()
    y = n.cross(x)
    return Matrix((x, y, n)).transposed()


def rotz(deg):
    return Matrix.Rotation(math.radians(deg), 3, 'Z')


def rotx(deg):
    return Matrix.Rotation(math.radians(deg), 3, 'X')


def roty(deg):
    return Matrix.Rotation(math.radians(deg), 3, 'Y')


def _rot3(rot):
    if rot is None:
        return Matrix.Identity(3)
    if isinstance(rot, Matrix):
        return rot.to_3x3()
    from mathutils import Euler
    return Euler([math.radians(a) for a in rot], 'XYZ').to_matrix()


def lerp_keys(keys, t):
    """Piecewise-linear lookup in [(t, value), ...]."""
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t <= t1:
            u = (t - t0) / max(t1 - t0, 1e-9)
            return v0 + (v1 - v0) * u
    return keys[-1][1]


def curve_at(ctrl, t):
    """Catmull-Rom through control points (t in 0..1, linear extrapolation outside)."""
    P = [V(c) for c in ctrl]
    n = len(P) - 1
    if n == 1 or t < 0 or t > 1:
        if t < 0:
            return P[0] + (P[1] - P[0]) * (t * n)
        if t > 1:
            return P[-1] + (P[-1] - P[-2]) * ((t - 1) * n)
        return P[0].lerp(P[1], t)
    f = t * n
    i = min(int(f), n - 1)
    u = f - i
    p1, p2 = P[i], P[i + 1]
    p0 = P[i - 1] if i > 0 else p1 * 2 - p2
    p3 = P[i + 2] if i + 2 <= n else p2 * 2 - p1
    u2, u3 = u * u, u * u * u
    return 0.5 * ((2 * p1) + (p2 - p0) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2
                  + (3 * p1 - p0 - 3 * p2 + p3) * u3)


class Ell:
    """Axis-aligned ellipsoid used as a host surface for placing features."""

    def __init__(self, c, r):
        self.c = V(c)
        self.r = V(r)

    def inside(self, p, margin=0.0):
        d = V(p) - self.c
        rx, ry, rz = self.r.x - margin, self.r.y - margin, self.r.z - margin
        return (d.x / rx) ** 2 + (d.y / ry) ** 2 + (d.z / rz) ** 2 < 1.0

    def inside_fn(self, margin=0.003):
        return lambda p: self.inside(p, margin)

    def surf(self, d):
        d = V(d).normalized()
        k = 1.0 / math.sqrt((d.x / self.r.x) ** 2 + (d.y / self.r.y) ** 2 + (d.z / self.r.z) ** 2)
        return self.c + d * k

    def normal(self, p):
        d = V(p) - self.c
        return V((d.x / self.r.x ** 2, d.y / self.r.y ** 2, d.z / self.r.z ** 2)).normalized()

    def front(self, x, z, side=-1.0):
        """Surface point with the given x, z on the -Y (front) side."""
        q = 1.0 - ((x - self.c.x) / self.r.x) ** 2 - ((z - self.c.z) / self.r.z) ** 2
        return V((x, self.c.y + side * self.r.y * math.sqrt(max(q, 0.0)), z))


def any_inside(*fns):
    fns = [f for f in fns if f is not None]
    return lambda p: any(f(p) for f in fns)


class Part:
    """Accumulates geometry for one exported part, then becomes one object."""

    def __init__(self, name):
        self.name = name
        self.bm = bmesh.new()
        self.mats = []
        # vertices hidden inside other geometry; their AO is filled from visible neighbours
        self.buried = self.bm.verts.layers.int.new("buried")
        # tests for points hidden under decals/muzzles (used to cull the base shell)
        self.covers = []

    def covered(self, p):
        return any(f(p) for f in self.covers)

    def mi(self, m):
        for i, mm in enumerate(self.mats):
            if mm == m:
                return i
        self.mats.append(m)
        return len(self.mats) - 1

    def _cull(self, faces, cull, tag=None):
        """Delete faces entirely inside `cull`; tag vertices inside `cull`/`tag` as buried."""
        if cull is not None:
            dead = [f for f in faces if f.is_valid and all(cull(v.co) for v in f.verts)]
            if dead:
                bmesh.ops.delete(self.bm, geom=dead, context='FACES')
        for fn in (cull, tag):
            if fn is None:
                continue
            for f in faces:
                if f.is_valid:
                    for v in f.verts:
                        if fn(v.co):
                            v[self.buried] = 1

    def _face(self, vs, m, faces):
        try:
            f = self.bm.faces.new(vs)
        except ValueError:
            return None
        f.material_index = self.mi(m)
        f.smooth = True
        faces.append(f)
        return f

    def rings(self, rings, mats, zmin=None, buried_rings=()):
        """rings: list of point lists (len 1 = pole). Each ring runs counter-clockwise
        around the travel direction; mats[i] colours the band between ring i and i+1."""
        bm = self.bm
        vr = []
        for ring in rings:
            row = []
            for p in ring:
                p = V(p)
                if zmin is not None and p.z < zmin:
                    p.z = zmin
                row.append(bm.verts.new(p))
            vr.append(row)
        for i in buried_rings:
            for v in vr[i]:
                v[self.buried] = 1
        faces = []
        for i in range(len(vr) - 1):
            R0, R1 = vr[i], vr[i + 1]
            m = mats[i] if isinstance(mats, (list, tuple)) else mats
            if len(R0) == 1 and len(R1) == 1:
                continue
            seg = max(len(R0), len(R1))
            for j in range(seg):
                k = (j + 1) % seg
                if len(R0) == 1:
                    vs = (R0[0], R1[k], R1[j])
                elif len(R1) == 1:
                    vs = (R0[j], R0[k], R1[0])
                else:
                    vs = (R0[j], R0[k], R1[k], R1[j])
                self._face(vs, m, faces)
        return faces

    def sweep(self, pts, radii, mats, seg=12, scales=None, hint=(0, 0, 1), rmod=None,
              cull=None, zmin=None, tag=None):
        """Generalised cylinder along a polyline. radii[i] == 0 makes a pole.
        scales[i] = (sa, sb): cross-section scale along A (= hint projected) and B = T x A."""
        pts = [V(p) for p in pts]
        n = len(pts)
        T = []
        for i in range(n):
            if i == 0:
                t = pts[1] - pts[0]
            elif i == n - 1:
                t = pts[-1] - pts[-2]
            else:
                t = pts[i + 1] - pts[i - 1]
            T.append(t.normalized())
        h = V(hint)
        a = h - T[0] * h.dot(T[0])
        if a.length < 1e-6:
            a = T[0].orthogonal()
        a.normalize()
        A = [a]
        for i in range(1, n):
            q = T[i - 1].rotation_difference(T[i])
            a = q @ A[-1]
            a = (a - T[i] * a.dot(T[i])).normalized()
            A.append(a)
        rings = []
        for i in range(n):
            r = radii[i]
            if r <= 1e-7:
                rings.append([pts[i]])
                continue
            a = A[i]
            b = T[i].cross(a)
            sa, sb = scales[i] if scales else (1.0, 1.0)
            ring = []
            for j in range(seg):
                th = 2 * math.pi * j / seg
                rr = r * (rmod(th, i) if rmod else 1.0)
                ring.append(pts[i] + a * (math.cos(th) * rr * sa) + b * (math.sin(th) * rr * sb))
            rings.append(ring)
        faces = self.rings(rings, mats, zmin=zmin)
        self._cull(faces, cull, tag)
        return faces

    def tube(self, ctrl, ring_keys, seg=10, hint=(0, 0, 1), scale_keys=None, cull=None, rmod=None, tag=None):
        """ring_keys: [(t, radius, material), ...] sampled along a Catmull-Rom curve
        through `ctrl`. The material of ring i colours the band up to ring i+1."""
        pts = [curve_at(ctrl, t) for t, _, _ in ring_keys]
        radii = [r for _, r, _ in ring_keys]
        mats = [m for _, _, m in ring_keys[:-1]]
        scales = None
        if scale_keys:
            scales = [(lerp_keys([(k[0], k[1]) for k in scale_keys], t),
                       lerp_keys([(k[0], k[2]) for k in scale_keys], t)) for t, _, _ in ring_keys]
        return self.sweep(pts, radii, mats, seg=seg, scales=scales, hint=hint, cull=cull, rmod=rmod, tag=tag)

    def line(self, pts, r, m, seg=6, hint=(0, 0, 1)):
        """Thin rounded tube through pts (smile lines, stitches)."""
        pts = [V(p) for p in pts]
        t0 = (pts[1] - pts[0]).normalized()
        t1 = (pts[-1] - pts[-2]).normalized()
        path = [pts[0] - t0 * r * 0.9] + pts + [pts[-1] + t1 * r * 0.9]
        radii = [0.0] + [r] * len(pts) + [0.0]
        return self.sweep(path, radii, m, seg=seg, hint=hint)

    def ellipsoid(self, c, r, m, n=4, rot=None, cull=None, mat_fn=None, zmin=None, uv=None, tag=None):
        """Ellipsoid as a cube-sphere (n x n quads per cube face: even, efficient
        tessellation, no pinched poles) or a UV sphere if uv=(segments, rings)."""
        c = V(c)
        r = V(r) if hasattr(r, "__len__") else V((r, r, r))
        R = _rot3(rot)

        def world(u):
            return c + R @ V((u.x * r.x, u.y * r.y, u.z * r.z))

        faces = []
        if uv:
            seg, nr = uv
            rings_u = [[V((0, 0, -1))]]
            for k in range(1, nr):
                ph = -math.pi / 2 + math.pi * k / nr
                rings_u.append([V((math.cos(ph) * math.cos(2 * math.pi * j / seg),
                                   math.cos(ph) * math.sin(2 * math.pi * j / seg),
                                   math.sin(ph))) for j in range(seg)])
            rings_u.append([V((0, 0, 1))])
            if mat_fn:
                mats = [mat_fn(V((0, 0, (rings_u[i][0].z + rings_u[i + 1][0].z) * 0.5)))
                        for i in range(len(rings_u) - 1)]
            else:
                mats = m
            faces = self.rings([[world(u) for u in ring] for ring in rings_u], mats, zmin=zmin)
        else:
            bm = self.bm
            cache = {}
            unit = {}

            def vert(key):
                v = cache.get(key)
                if v is None:
                    u = V([math.tan(k / n * math.pi / 4) if abs(k) != n else (1.0 if k > 0 else -1.0)
                           for k in key]).normalized()
                    w = world(u)
                    if zmin is not None and w.z < zmin:
                        w.z = zmin
                    v = bm.verts.new(w)
                    cache[key] = v
                    unit[v] = u
                return v

            for N, a1, a2 in CUBE_FACES:
                grid = []
                for i in range(n + 1):
                    row = []
                    for j in range(n + 1):
                        key = tuple(N[k] * n + a1[k] * (2 * i - n) + a2[k] * (2 * j - n) for k in range(3))
                        row.append(vert(key))
                    grid.append(row)
                for i in range(n):
                    for j in range(n):
                        vs = (grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1])
                        mm = m
                        if mat_fn:
                            uc = (unit[vs[0]] + unit[vs[1]] + unit[vs[2]] + unit[vs[3]]) / 4
                            mm = mat_fn(uc)
                        self._face(vs, mm, faces)
        self._cull(faces, cull, tag)
        return faces

    def finish(self, pivot):
        me = bpy.data.meshes.new(self.name)
        bm = self.bm
        bm.normal_update()
        bm.to_mesh(me)
        bm.free()
        for m in self.mats:
            me.materials.append(m)
        me.transform(Matrix.Translation(-V(pivot)))
        try:
            me.shade_smooth()
        except Exception:
            for p in me.polygons:
                p.use_smooth = True
        me.update()
        ob = bpy.data.objects.new(self.name, me)
        bpy.context.collection.objects.link(ob)
        ob.location = V(pivot)
        return ob


def decal(P, host, x, z, a, b, m, h=0.005, roll=0.0, seg=14, rho=(0.62, 0.95, 1.0), sink=0.0025,
          center=None, taper=0.0, notch=0.0):
    """Soft raised marking (cheeks, masks, blazes, face plates): an elliptical patch
    projected onto `host`, lifted `h` in the middle and sunk `sink` below the surface
    at the rim, so the outline is crisp, follows the curvature and never z-fights.
    taper > 0 narrows the top; notch > 0 dents the top centre (heart shapes)."""
    p0 = host.front(x, z) if center is None else V(center)
    n0 = host.normal(p0)
    R = frame(n0) @ rotz(roll)
    X, Y = R.col[0], R.col[1]

    def lift(rh):
        return h * math.sqrt(max(0.0, 1.0 - rh * rh)) if rh < 1.0 else -sink

    rings = [[p0 + n0 * h]]
    for rh in rho:
        ring = []
        for j in range(seg):
            th = -2 * math.pi * j / seg          # clockwise -> normals face out of the host
            v = math.sin(th)
            k = 1.0
            if notch:
                dth = math.atan2(math.sin(th - math.pi / 2), math.cos(th - math.pi / 2))
                k = 1.0 - notch * math.exp(-(dth / 0.34) ** 2)
            q = p0 + X * (a * rh * k * math.cos(th) * (1.0 - taper * v)) + Y * (b * rh * k * v)
            sp = host.surf(q - host.c)
            ring.append(sp + host.normal(sp) * lift(rh))
        rings.append(ring)
    P.rings(rings, m, buried_rings=(len(rings) - 1,))

    lim = 0.72 * (1.0 - notch)
    base = (p0 - host.c).dot(n0)

    def under(q):
        d = V(q) - host.c
        dn = d.dot(n0)
        if dn <= 1e-6:
            return False
        t = d * (base / dn) + host.c - p0
        u, v = t.dot(X) / a, t.dot(Y) / b
        sv = max(-1.0, min(1.0, v / max(math.hypot(u, v), 1e-6)))
        return (u / (1.0 - taper * sv)) ** 2 + v * v < lim * lim
    P.covers.append(under)
    return p0, n0


def kid(child, parent):
    """Parent with an identity parent-inverse (parts never rotate or scale)."""
    child.parent = parent
    child.matrix_parent_inverse = Matrix.Identity(4)
    child.location = child.location - parent.location
    return child


def world_pivot(o):
    p = o.location.copy()
    q = o.parent
    while q is not None:
        p += q.location
        q = q.parent
    return p


# ---------------------------------------------------------------------------
# Smoother AO. bvlib bakes AO per face corner, so neighbouring triangles get
# slightly different noisy values and low-poly meshes look faceted/blotchy.
# While our own exports run (`with smoothed_ao():`) bvlib.bake_ao is swapped for
# a wrapper that calls the original bake and then averages AO per vertex, gives
# buried vertices (inside other geometry) their visible neighbours' value instead
# of black, and applies one blur pass. bvlib.py itself is untouched and the swap
# is undone as soon as the export returns.
# ---------------------------------------------------------------------------
_BVLIB_BAKE_AO = B.bake_ao


def smooth_ao(me, blur=1):
    ao = me.color_attributes.get("AO")
    if ao is None:
        return
    nv = len(me.vertices)
    acc = [0.0] * nv
    cnt = [0] * nv
    for li, loop in enumerate(me.loops):
        acc[loop.vertex_index] += ao.data[li].color[0]
        cnt[loop.vertex_index] += 1
    val = [acc[i] / cnt[i] if cnt[i] else 1.0 for i in range(nv)]
    nb = [[] for _ in range(nv)]
    for e in me.edges:
        a, b = e.vertices
        nb[a].append(b)
        nb[b].append(a)
    battr = me.attributes.get("buried")
    buried = [bool(battr.data[i].value) for i in range(nv)] if battr is not None else [False] * nv
    known = [not b for b in buried]
    for _ in range(6):
        changed = False
        for i in range(nv):
            if known[i]:
                continue
            vs = [val[j] for j in nb[i] if known[j]]
            if vs:
                val[i] = max(vs)
                known[i] = True
                changed = True
        if not changed:
            break
    for _ in range(blur):
        val = [0.5 * val[i] + 0.5 * (sum(val[j] for j in nb[i]) / len(nb[i])) if nb[i] else val[i]
               for i in range(nv)]
    for li, loop in enumerate(me.loops):
        x = val[loop.vertex_index]
        ao.data[li].color = (x, x, x, 1.0)


def bake_ao_smooth(objs, samples=48, distance=0.6):
    ok = _BVLIB_BAKE_AO(objs, samples=max(samples, 64), distance=distance)
    if ok:
        for o in objs:
            smooth_ao(o.data)
    return ok


@contextmanager
def smoothed_ao():
    prev = B.bake_ao
    B.bake_ao = bake_ao_smooth
    try:
        yield
    finally:
        B.bake_ao = prev


# ---------------------------------------------------------------------------
# Preview helpers (shared with hats.py / pets.py)
# ---------------------------------------------------------------------------
def vc_preview(parts, tint=None, ao_strength=0.75):
    """Show exactly what the engine gets: base colour x baked AO (as export_bvm
    computes it), optionally recoloured on tint faces to simulate the engine tint."""
    seen = set()
    for o in parts:
        me = o.data
        ao = me.color_attributes.get("AO")
        col = me.color_attributes.get("COL")
        if col is None:
            col = me.color_attributes.new("COL", 'FLOAT_COLOR', 'CORNER')
        mats = me.materials
        for poly in me.polygons:
            mt = mats[poly.material_index]
            base = tuple(mt.diffuse_color[:3])
            is_tint = int(mt.get("tint", 0))
            emit = float(mt.get("emit", 0.0))
            for li in poly.loop_indices:
                occ = 1.0
                if ao is not None and emit <= 0:
                    occ = 1.0 - ao_strength * (1.0 - ao.data[li].color[0])
                c = [min(1.0, base[k] * occ) for k in range(3)]
                if is_tint and tint is not None:
                    tt = B.color(tint)
                    c = [c[k] * tt[k] for k in range(3)]
                col.data[li].color = (B.srgb_to_linear(c[0]), B.srgb_to_linear(c[1]),
                                      B.srgb_to_linear(c[2]), 1.0)
        for mt in mats:
            if mt.name in seen:
                continue
            seen.add(mt.name)
            nt = mt.node_tree
            if nt is None:
                continue
            bsdf = nt.nodes.get("Principled BSDF")
            node = nt.nodes.new("ShaderNodeVertexColor")
            node.layer_name = "COL"
            nt.links.new(node.outputs["Color"], bsdf.inputs["Base Color"])
            if float(mt.get("emit", 0.0)) > 0:
                nt.links.new(node.outputs["Color"], bsdf.inputs["Emission Color"])


def add_ground(size=30.0, rgb=(0.93, 0.87, 0.79)):
    me = bpy.data.meshes.new("ground")
    s = size / 2
    me.from_pydata([(-s, -s, 0), (s, -s, 0), (s, s, 0), (-s, s, 0)], [], [(0, 1, 2, 3)])
    ob = bpy.data.objects.new("ground", me)
    bpy.context.collection.objects.link(ob)
    gm = bpy.data.materials.new("PreviewGround")
    if gm.node_tree is None:
        gm.use_nodes = True
    bsdf = gm.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (B.srgb_to_linear(rgb[0]), B.srgb_to_linear(rgb[1]),
                                               B.srgb_to_linear(rgb[2]), 1)
    bsdf.inputs["Roughness"].default_value = 0.9
    me.materials.append(gm)
    return ob


def sun_rig(strength=3.2, ambient=0.55):
    d = bpy.data.lights.new("Sun", 'SUN')
    d.energy = strength
    d.angle = math.radians(12)
    d.color = (1.0, 0.96, 0.9)
    o = bpy.data.objects.new("Sun", d)
    bpy.context.collection.objects.link(o)
    o.rotation_euler = V((0.35, -0.75, 1.0)).normalized().to_track_quat('Z', 'Y').to_euler()
    f = bpy.data.lights.new("Fill", 'SUN')
    f.energy = strength * 0.25
    f.angle = math.radians(30)
    f.color = (0.85, 0.92, 1.0)
    fo = bpy.data.objects.new("Fill", f)
    bpy.context.collection.objects.link(fo)
    fo.rotation_euler = V((-0.8, -0.4, 0.5)).normalized().to_track_quat('Z', 'Y').to_euler()
    bg = bpy.context.scene.world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs[1].default_value = ambient


def render_view(path, target, dist, lens=85, res=(720, 720), samples=64, cam_dir=(0.62, -1.0, 0.42),
                rig="studio", ground=True, ground_rgb=(0.93, 0.87, 0.79)):
    B.setup_render(res=res, transparent=False, samples=samples)
    if ground:
        add_ground(rgb=ground_rgb)
    if rig == "studio":
        B.studio_lights(target)
    else:
        sun_rig()
    d = V(cam_dir).normalized()
    B.camera(tuple(V(target) + d * dist), target, lens=lens)
    B.render(path)


# ---------------------------------------------------------------------------
# Cast proportions (Blender coords, metres). hats.py relies on HEAD / HAT_Z.
# ---------------------------------------------------------------------------
HEAD = Ell((0.0, 0.0, 0.835), (0.265, 0.240, 0.235))
HEAD_TOP = HEAD.c.z + HEAD.r.z                         # skull apex, 1.07
HAT_RING_R = 0.18                                     # contract: hat bottom ring ~0.36 m
# The hat's bottom ring seats on the crown where the skull is 0.36 m wide.
HAT_Z = 1.0

PART_ORDER = ["body", "head", "eyes", "armL", "armR", "legL", "legR", "tail"]
PARENT = {"body": None, "head": "body", "eyes": "head", "armL": "body", "armR": "body",
          "legL": "body", "legR": "body", "tail": "body"}
PIVOT = {
    "body": (0.0, 0.0, 0.40),
    "head": (0.0, 0.0, 0.62),
    "eyes": (0.0, -0.20, 0.80),
    "armL": (0.17, 0.0, 0.58),
    "armR": (-0.17, 0.0, 0.58),
    "legL": (0.085, 0.0, 0.36),
    "legR": (-0.085, 0.0, 0.36),
    "tail": (0.0, 0.14, 0.40),
}
HAND = (0.0, -0.26, 0.55)
EYE_X, EYE_Z = 0.098, 0.80
EYE_R = (0.046, 0.058, 0.030)       # (side, up, depth) in the face frame
EYE_PROUD = 0.013                   # how far an eye stands off the face

# Torso lathe profile: (radius, z, band label). Shirt = tint faces.
TORSO = [
    (0.000, 0.205, "shorts"),
    (0.150, 0.240, "shorts"),
    (0.178, 0.290, "shorts"),
    (0.186, 0.350, "shorts"),
    (0.186, 0.370, "shirt"),     # hem underside
    (0.197, 0.386, "shirt"),     # rolled hem
    (0.190, 0.402, "shirt"),
    (0.182, 0.447, "shirt"),
    (0.148, 0.548, "shirt"),
    (0.120, 0.586, "collar"),
    (0.133, 0.604, "collar"),
    (0.126, 0.624, "collar"),
    (0.084, 0.638, "neck"),
    (0.000, 0.680, None),
]



def torso_radius(z):
    pts = [(zz, r) for r, zz, _ in TORSO]
    if z < pts[0][0] or z > pts[-1][0]:
        return 0.0
    return lerp_keys(pts, z)


def torso_inside(p, margin=0.004):
    r = torso_radius(p.z) - margin
    return r > 0 and (p.x * p.x + (p.y / 0.88) ** 2) < r * r


# ---------------------------------------------------------------------------
# Species
# ---------------------------------------------------------------------------
SPECIES_ORDER = ["corgi", "cat", "bunny", "bear", "panda", "frog", "penguin", "fox", "duck", "raccoon"]

SPECIES = {
    #            fur        light (muzzle/face)  arm       paw        leg        foot       extra
    "corgi":   dict(fur="#F2A043", light="#FFF6EA", arm="#F2A043", paw="#FFF6EA", leg="#F2A043", foot="#FFF6EA",
                    inner="#FFE0C2", nose="#2A2130"),
    "cat":     dict(fur="#B6B1C0", light="#FFF8F2", arm="#B6B1C0", paw="#FFF8F2", leg="#B6B1C0", foot="#FFF8F2",
                    inner="#FFA9BF", nose="#FF8FA8", stripe="#8A8497"),
    "bunny":   dict(fur="#FBF7F2", light="#FFFFFF", arm="#FBF7F2", paw="#FBF7F2", leg="#FBF7F2", foot="#FBF7F2",
                    inner="#FFA6BE", nose="#FF8FAE"),
    "bear":    dict(fur="#9A6440", light="#E9BD8C", arm="#9A6440", paw="#9A6440", leg="#9A6440", foot="#86553A",
                    inner="#E9BD8C", nose="#2A2130"),
    "panda":   dict(fur="#FBF8F4", light="#FFFFFF", arm="#2A2631", paw="#2A2631", leg="#2A2631", foot="#2A2631",
                    patch="#2A2631", nose="#1E1A22"),
    "frog":    dict(fur="#72C653", light="#D9F2A6", arm="#72C653", paw="#72C653", leg="#72C653", foot="#5DB043",
                    nose="#2F5A2A"),
    "penguin": dict(fur="#2C3E66", light="#FBF8F4", arm="#2C3E66", paw="#2C3E66", leg="#2C3E66", foot="#FF9A3C",
                    beak="#FF9A3C"),
    "fox":     dict(fur="#EE7432", light="#FFF6EA", arm="#EE7432", paw="#2E2630", leg="#2E2630", foot="#2E2630",
                    inner="#FFE6CF", nose="#2A2130", tip="#2E2630"),
    "duck":    dict(fur="#FFD043", light="#FFE58A", arm="#FFD043", paw="#FFD043", leg="#FF9A3C", foot="#FF9A3C",
                    beak="#FF9A3C"),
    "raccoon": dict(fur="#8F95A0", light="#F6F3EF", arm="#8F95A0", paw="#3A3742", leg="#8F95A0", foot="#3A3742",
                    inner="#F6F3EF", nose="#1E1A22", mask="#3A3742", ring="#3A3742"),
}
# Preview-only shirt colours (simulate the engine tint so the renders read in context).
PREVIEW_TINT = {
    "corgi": "teal", "cat": "tomato", "bunny": "lilac", "bear": "mint", "panda": "red",
    "frog": "mustard", "penguin": "orange", "fox": "blue", "duck": "pink", "raccoon": "green",
}


class Mats(dict):
    __getattr__ = dict.__getitem__


def make_mats(S):
    M = Mats()
    M["shirt"] = B.mat("white", tint=True, rough=0.7)
    M["collar"] = B.mat("#FFF3DC", rough=0.7)
    M["shorts"] = B.mat("#4B587E", rough=0.75)
    M["eye"] = B.mat("#17131C", rough=0.08)
    M["hl"] = B.mat("white", rough=0.2, emit=0.6)
    M["mouth"] = B.mat("#3B2A3A", rough=0.5)
    M["blush"] = B.mat("#FF9DB6", rough=0.6)
    for k, v in S.items():
        M[k] = B.mat(v, rough=0.5 if k in ("nose", "beak") else 0.62)
    M["neck"] = M["light"] if S.get("chest_light") else M["fur"]
    return M


# ---------------------------------------------------------------------------
# Shared body parts
# ---------------------------------------------------------------------------
def build_body(S, M):
    P = Part("body")
    lab = {"shorts": M.shorts, "shirt": M.shirt, "collar": M.collar, "neck": M.neck}
    pts = [(0.0, 0.0, z) for _, z, _ in TORSO]
    radii = [r for r, _, _ in TORSO]
    mats = [lab[l] for _, _, l in TORSO[:-1]]
    P.sweep(pts, radii, mats, seg=16, scales=[(1.0, 0.88)] * len(TORSO), hint=(1, 0, 0))
    return P.finish(PIVOT["body"])


ARM_PAW = [  # (t, radius, label); t in arm lengths from the shoulder pivot
    (-0.10, 0.000, "sleeve"),
    (-0.07, 0.042, "sleeve"),
    (0.02, 0.062, "sleeve"),
    (0.18, 0.066, "sleeve"),
    (0.34, 0.063, "sleeve"),     # cuff, band below is the cuff underside
    (0.37, 0.045, "arm"),
    (0.66, 0.048, "paw"),
    (0.84, 0.058, "paw"),
    (0.97, 0.047, "paw"),
    (1.04, 0.000, None),
]
ARM_FLIPPER = [
    (-0.10, 0.000, "sleeve"),
    (-0.07, 0.042, "sleeve"),
    (0.02, 0.062, "sleeve"),
    (0.18, 0.066, "sleeve"),
    (0.34, 0.063, "sleeve"),
    (0.37, 0.050, "arm"),
    (0.58, 0.058, "arm"),
    (0.84, 0.050, "arm"),
    (1.02, 0.030, "arm"),
    (1.12, 0.000, None),
]
ARM_WING = [
    (-0.10, 0.000, "sleeve"),
    (-0.07, 0.042, "sleeve"),
    (0.02, 0.062, "sleeve"),
    (0.18, 0.066, "sleeve"),
    (0.34, 0.063, "sleeve"),
    (0.37, 0.050, "arm"),
    (0.56, 0.062, "arm"),
    (0.78, 0.060, "arm"),
    (0.95, 0.034, "arm"),
    (1.02, 0.000, None),
]


def build_arm(S, M, side, style="paw"):
    s = side
    name = "armL" if s > 0 else "armR"
    P = Part(name)
    piv = V(PIVOT[name])
    L = 0.22
    d = V((0.5 * s, -0.03, -1.0)).normalized()
    table = {"paw": ARM_PAW, "flipper": ARM_FLIPPER, "wing": ARM_WING}[style]
    lab = {"sleeve": M.shirt, "arm": M.arm, "paw": M.paw, None: M.paw}
    pts, radii, mats, scales = [], [], [], []
    for t, r, l in table:
        fwd = V((0, -0.035, 0)) * max(0.0, t - 0.45) ** 2 * 3.3
        pts.append(piv + d * (t * L) + fwd)
        radii.append(r)
        mats.append(lab[l])
        flat = style != "paw" and l == "arm"
        scales.append((0.42, 1.22) if flat else (1.0, 1.0))
    # A = outward (so flippers/wings are thin sideways and wide front-to-back)
    P.sweep(pts, radii, mats[:-1], seg=10, scales=scales, hint=(s, 0, 0), tag=torso_inside)
    return P.finish(piv)


def build_leg(S, M, side, webbed=False):
    s = side
    name = "legL" if s > 0 else "legR"
    P = Part(name)
    piv = V(PIVOT[name])
    x = piv.x
    pts = [(x, 0, 0.405), (x, 0, 0.392), (x + 0.004 * s, 0, 0.36), (x + 0.008 * s, -0.008, 0.22),
           (x + 0.01 * s, -0.02, 0.11), (x + 0.01 * s, -0.022, 0.09)]
    radii = [0.0, 0.050, 0.068, 0.070, 0.068, 0.0]
    P.sweep(pts, radii, M.leg, seg=8, hint=(1, 0, 0), tag=torso_inside)
    if webbed:
        fr, fc = (0.080, 0.116, 0.052), (x + 0.012 * s, -0.066, 0.038)
    else:
        fr, fc = (0.074, 0.108, 0.064), (x + 0.010 * s, -0.056, 0.050)
    P.ellipsoid(fc, fr, M.foot, n=3, rot=rotz(9 * s), zmin=0.0,
                tag=lambda p: (p - V((x, -0.02, 0.10))).length < 0.06)
    return P.finish(piv)


# ---------------------------------------------------------------------------
# Eyes
# ---------------------------------------------------------------------------
def dome(P, c, R, r, m, seg=14, phis=(100, 62, 30)):
    """Front cap of an ellipsoid (local Z = R's third column); the rim ring is buried."""
    X, Y, N = R.col[0], R.col[1], R.col[2]
    rings = []
    for ph in phis:
        a = math.radians(ph)
        rings.append([c + X * (math.sin(a) * math.cos(2 * math.pi * j / seg) * r[0])
                      + Y * (math.sin(a) * math.sin(2 * math.pi * j / seg) * r[1])
                      + N * (math.cos(a) * r[2]) for j in range(seg)])
    rings.append([c + N * r[2]])
    return P.rings(rings, m, buried_rings=(0,))


def eye_pair(P, M, centers_normals, er=EYE_R, hl_scale=1.0, cull=None, seg=14, hl2=True):
    for c, nrm in centers_normals:
        R = frame(nrm)
        X, Y, N = R.col[0], R.col[1], R.col[2]
        dome(P, c, R, er, M.eye, seg=seg)

        def on_eye(u, v):
            w = math.sqrt(max(0.0, 1 - u * u - v * v))
            return c + X * (u * er[0]) + Y * (v * er[1]) + N * (w * er[2])
        hs = 0.0145 * hl_scale
        P.ellipsoid(on_eye(-0.40, 0.44) + N * 0.0015, (hs, hs * 1.05, hs * 0.55), M.hl, uv=(8, 3), rot=R)
        if not hl2:
            continue
        hs2 = 0.0072 * hl_scale
        P.ellipsoid(on_eye(0.36, -0.40) + N * 0.001, (hs2, hs2, hs2 * 0.6), M.hl, uv=(6, 3), rot=R)


def face_eye_spots(er=EYE_R, x=EYE_X, z=EYE_Z, proud=EYE_PROUD):
    out = []
    for s in (1, -1):
        p = HEAD.front(x * s, z)
        nrm = HEAD.normal(p)
        out.append((p - nrm * (er[2] - proud), nrm))
    return out


FROG_BUMP_R = 0.088


def frog_bumps():
    return [V((0.112 * s, -0.075, 1.0)) for s in (1, -1)]


def build_eyes(sp, S, M):
    P = Part("eyes")
    if sp == "frog":
        spots = []
        bumps = frog_bumps()
        for s, bc in zip((1, -1), bumps):
            nrm = V((0.30 * s, -1.0, 0.42)).normalized()
            p = bc + nrm * FROG_BUMP_R
            spots.append((p - nrm * (0.030 - 0.014), nrm))
        cull = any_inside(*[Ell(b, (FROG_BUMP_R,) * 3).inside_fn(0.006) for b in bumps])
        eye_pair(P, M, spots, er=(0.048, 0.054, 0.030), cull=cull)
    else:
        eye_pair(P, M, face_eye_spots(), cull=HEAD.inside_fn(0.006))
    return P.finish(PIVOT["eyes"])


# ---------------------------------------------------------------------------
# Head features
# ---------------------------------------------------------------------------
EAR_POINTED = [(0.0, 0.0), (0.03, 0.88), (0.24, 1.0), (0.55, 0.76), (0.80, 0.42), (0.95, 0.14), (1.0, 0.0)]
EAR_LONG = [(0.0, 0.0), (0.03, 0.72), (0.2, 0.9), (0.5, 1.0), (0.78, 0.9), (0.93, 0.55), (1.0, 0.0)]
EAR_ROUNDTRI = [(0.0, 0.0), (0.03, 0.86), (0.24, 1.0), (0.52, 0.88), (0.76, 0.62), (0.91, 0.34), (1.0, 0.0)]


def ear(P, M, s, root_dir, axis, length, width, depth, prof, outer, inner=None, tip=None,
        tip_t=0.64, turn=0.25, embed=0.05, seg=8, bend=0.0, host=None):
    host = host or HEAD
    rd = V((root_dir[0] * s, root_dir[1], root_dir[2]))
    root = host.surf(rd)
    ax = V((axis[0] * s, axis[1], axis[2])).normalized()
    front = V((turn * s, -1.0, 0.0)).normalized()
    start = root - ax * embed
    tot = length + embed
    bendv = V((0, -1, 0)) * bend

    def path(t, scale=1.0):
        return start + ax * (t * tot * scale) + bendv * (t * t)

    ts = [t for t, _ in prof]
    pts = [path(t) for t in ts]
    radii = [width * f for _, f in prof]
    mats = []
    for t in ts[:-1]:
        mats.append(tip if (tip is not None and t >= tip_t) else outer)
    hin = host.inside_fn(0.004)
    P.sweep(pts, radii, mats, seg=seg, scales=[(depth, 1.0)] * len(pts), hint=front, cull=hin)
    if inner is not None:
        # raised pad hugging the front face; it follows the outer profile and stops short of the tip
        fo = [f for _, f in prof]
        fi = [lerp_keys(prof, min(1.0, t * 1.13)) for t in ts]
        ipts = [p + front * (width * depth * f * 0.84) for p, f in zip(pts, fo)]
        iradii = [width * 0.60 * f for f in fi]
        P.sweep(ipts[:-1] + [ipts[-1]], iradii, inner, seg=max(6, seg - 2), scales=[(depth * 0.55, 1.0)] * len(ipts),
                hint=front, cull=hin)
    return root + ax * length


def round_ear(P, M, s, root_dir, rad, outer, inner=None, thick=0.034, face=(0.3, -1.0, 0.25), host=None,
              n=(3, 2)):
    host = host or HEAD
    rd = V((root_dir[0] * s, root_dir[1], root_dir[2]))
    root = host.surf(rd)
    nrm = host.normal(root)
    fn = V((face[0] * s, face[1], face[2])).normalized()
    R = frame(fn)
    c = root + nrm * (rad * 0.55)
    P.ellipsoid(c, (rad, rad * 0.95, thick), outer, n=n[0], rot=R, cull=host.inside_fn(0.004))
    if inner is not None:
        P.ellipsoid(c + fn * (thick * 0.78) - nrm * 0.008, (rad * 0.62, rad * 0.56, thick * 0.40), inner,
                    n=n[1], rot=R, cull=host.inside_fn(0.004))
    return c + nrm * rad


def cover_ell(P, c, r, margin=0.004):
    P.covers.append(Ell(c, r).inside_fn(margin))


def oval_muzzle(P, m, c=(0.0, -0.205, 0.735), r=(0.088, 0.062, 0.058), n=3, rot=None):
    P.ellipsoid(c, r, m, n=n, rot=rot, cull=HEAD.inside_fn(0.004))
    cover_ell(P, c, r)
    return Ell(c, r)


def nose(P, host, m, z=0.765, r=(0.031, 0.020, 0.021), x=0.0, proud=0.006):
    p = host.front(x, z)
    nrm = host.normal(p)
    R = frame(nrm)
    P.ellipsoid(p - nrm * (r[2] - proud), r, m, n=2, rot=R, cull=host.inside_fn(0.003))


def mouth_w(P, host, m, z0=0.735, w=0.032, r=0.0052, stem=0.012):
    def on(x, z):
        p = host.front(x, z)
        return p - host.normal(p) * (r * 0.35)
    xs = [(-w, z0 + 0.004), (-w * 0.55, z0 - 0.007), (0.0, z0 + 0.001),
          (w * 0.55, z0 - 0.007), (w, z0 + 0.004)]
    P.line([on(x, z) for x, z in xs], r, m, seg=5)
    if stem > 0:
        P.line([on(0, z0 + stem), on(0, z0 + 0.002)], r, m, seg=5)


def smile(P, host, m, z0, half_w, depth, r=0.006, n=9, seg=6):
    pts = []
    for i in range(n):
        u = -1 + 2 * i / (n - 1)
        x = u * half_w
        z = z0 - depth * (1 - u * u) + 0.010 * u ** 6
        p = host.front(x, z)
        pts.append(p - host.normal(p) * (r * 0.3))
    P.line(pts, r, m, seg=seg)


def cheeks(P, M, x=0.168, z=0.742, a=0.042, b=0.029):
    for s in (1, -1):
        decal(P, HEAD, x * s, z, a, b, M.blush, h=0.003, seg=10, rho=(0.88, 1.0))


def f_corgi(P, M):
    decal(P, HEAD, 0.0, 0.700, 0.165, 0.085, M.light, h=0.005, seg=18)          # white lower face
    decal(P, HEAD, 0.0, 0.868, 0.040, 0.098, M.light, h=0.0045, seg=12, taper=0.55)  # blaze
    muz = oval_muzzle(P, M.light, c=(0.0, -0.205, 0.733), r=(0.090, 0.064, 0.058))
    nose(P, muz, M.nose, z=0.764, r=(0.034, 0.021, 0.022))
    mouth_w(P, muz, M.mouth, z0=0.733)
    cheeks(P, M, x=0.175)
    tops = []
    for s in (1, -1):
        tops.append(ear(P, M, s, (0.50, 0.06, 0.86), (0.36, 0.10, 1.0), 0.215, 0.094, 0.34, EAR_POINTED,
                        M.fur, M.inner, turn=0.35))
    return max(t.z for t in tops)


def add_puffs(P, m, z=0.733, r=(0.038, 0.030, 0.031), dx=0.027):
    for s in (1, -1):
        P.ellipsoid((dx * s, -0.214, z), r, m, n=2, cull=HEAD.inside_fn(0.004))
        cover_ell(P, (dx * s, -0.214, z), r)


def f_cat(P, M):
    add_puffs(P, M.light)
    puffs = Ell((0.0, -0.205, 0.736), (0.064, 0.040, 0.034))
    nose(P, Ell((0, -0.212, 0.758), (0.05, 0.036, 0.03)), M.nose, z=0.762, r=(0.020, 0.013, 0.014))
    smile(P, puffs, M.mouth, z0=0.704, half_w=0.022, depth=0.006, r=0.0045, n=5)
    cheeks(P, M, x=0.172)
    decal(P, HEAD, 0.0, 0.985, 0.018, 0.048, M.stripe, h=0.004, seg=10)
    for s in (1, -1):
        decal(P, HEAD, 0.048 * s, 0.968, 0.015, 0.038, M.stripe, h=0.004, seg=10, roll=14 * s)
    tops = []
    for s in (1, -1):
        tops.append(ear(P, M, s, (0.58, 0.04, 0.81), (0.52, 0.07, 1.0), 0.135, 0.080, 0.40, EAR_POINTED,
                        M.fur, M.inner, turn=0.25))
    return max(t.z for t in tops)


def f_bunny(P, M):
    add_puffs(P, M.light, z=0.732, r=(0.039, 0.031, 0.032), dx=0.028)
    nose(P, Ell((0, -0.212, 0.757), (0.05, 0.036, 0.03)), M.nose, z=0.762, r=(0.021, 0.014, 0.015))
    puffs = Ell((0.0, -0.205, 0.735), (0.064, 0.040, 0.034))
    smile(P, puffs, M.mouth, z0=0.703, half_w=0.022, depth=0.006, r=0.0045, n=5)
    cheeks(P, M, x=0.170, a=0.046, b=0.031)
    tops = []
    for s in (1, -1):
        tops.append(ear(P, M, s, (0.32, 0.10, 0.94), (0.20, 0.10, 1.0), 0.30, 0.060, 0.42, EAR_LONG,
                        M.fur, M.inner, turn=0.15, bend=0.0, seg=10))
    return max(t.z for t in tops)


def f_bear(P, M):
    muz = oval_muzzle(P, M.light, c=(0.0, -0.204, 0.728), r=(0.094, 0.064, 0.064))
    nose(P, muz, M.nose, z=0.762, r=(0.036, 0.022, 0.024))
    mouth_w(P, muz, M.mouth, z0=0.727)
    cheeks(P, M, x=0.178)
    tops = []
    for s in (1, -1):
        tops.append(round_ear(P, M, s, (0.62, 0.06, 0.78), 0.072, M.fur, M.inner))
    return max(t.z for t in tops)


def f_panda(P, M):
    for s in (1, -1):
        decal(P, HEAD, 0.104 * s, 0.792, 0.058, 0.074, M.patch, h=0.005, roll=-30 * s)
    muz = oval_muzzle(P, M.light, c=(0.0, -0.205, 0.728), r=(0.078, 0.058, 0.054))
    nose(P, muz, M.nose, z=0.757, r=(0.032, 0.020, 0.021))
    mouth_w(P, muz, M.mouth, z0=0.726)
    cheeks(P, M, x=0.182, z=0.730)
    tops = []
    for s in (1, -1):
        tops.append(round_ear(P, M, s, (0.62, 0.06, 0.78), 0.072, M.patch, None))
    return max(t.z for t in tops)


def f_frog(P, M):
    for bc in frog_bumps():
        P.ellipsoid(bc, (FROG_BUMP_R,) * 3, M.fur, n=4, cull=HEAD.inside_fn(0.004))
        cover_ell(P, bc, (FROG_BUMP_R,) * 3)
    smile(P, HEAD, M.mouth, z0=0.742, half_w=0.150, depth=0.032, r=0.0065, n=9)
    for s in (1, -1):
        p = HEAD.front(0.024 * s, 0.790)
        P.ellipsoid(p, (0.009, 0.009, 0.006), M.nose, uv=(6, 3), rot=frame(HEAD.normal(p)))
    cheeks(P, M, x=0.185, z=0.735, a=0.040, b=0.028)
    return max(b.z for b in frog_bumps()) + FROG_BUMP_R


def f_penguin(P, M):
    decal(P, HEAD, 0.0, 0.764, 0.200, 0.150, M.light, h=0.006, seg=24, taper=-0.14, notch=0.28)
    beak_base = HEAD.front(0.0, 0.752)
    ax = V((0.0, -1.0, -0.12)).normalized()
    pts = [beak_base - ax * 0.03, beak_base, beak_base + ax * 0.03, beak_base + ax * 0.055,
           beak_base + ax * 0.068]
    P.sweep(pts, [0.0, 0.040, 0.030, 0.014, 0.0], M.beak, seg=10, scales=[(0.62, 1.0)] * 5,
            hint=(0, 0, 1), cull=HEAD.inside_fn(0.004))
    cheeks(P, M, x=0.165, z=0.728)
    return HEAD_TOP


def f_fox(P, M):
    decal(P, HEAD, 0.0, 0.705, 0.205, 0.090, M.light, h=0.005, seg=20, notch=0.42)
    muz = Ell((0.0, -0.212, 0.726), (0.072, 0.074, 0.050))
    P.ellipsoid(muz.c, muz.r, M.light, n=3, cull=HEAD.inside_fn(0.004))
    cover_ell(P, muz.c, muz.r)
    nose(P, muz, M.nose, z=0.748, r=(0.028, 0.019, 0.020))
    mouth_w(P, muz, M.mouth, z0=0.716, w=0.026, stem=0.010)
    for s in (1, -1):
        root = HEAD.surf(V((0.88 * s, -0.40, -0.40)))
        ax = V((0.80 * s, -0.25, -0.55)).normalized()
        pts = [root - ax * 0.03, root + ax * 0.0, root + ax * 0.030, root + ax * 0.055]
        P.sweep(pts, [0.0, 0.034, 0.018, 0.0], M.light, seg=8, scales=[(0.45, 1.0)] * 4,
                hint=(0, -1, 0), cull=HEAD.inside_fn(0.004))
    tops = []
    for s in (1, -1):
        tops.append(ear(P, M, s, (0.50, 0.06, 0.86), (0.40, 0.08, 1.0), 0.175, 0.074, 0.38, EAR_POINTED,
                        M.fur, M.inner, tip=M.tip, tip_t=0.6, turn=0.3))
    return max(t.z for t in tops)


def f_duck(P, M):
    bill = Ell((0.0, -0.228, 0.738), (0.098, 0.082, 0.034))
    P.ellipsoid(bill.c, bill.r, M.beak, n=3, cull=HEAD.inside_fn(0.004))
    cover_ell(P, bill.c, bill.r)
    cheeks(P, M, x=0.180, z=0.770, a=0.040, b=0.028)
    top = HEAD.surf(V((0, -0.15, 1)))
    for dx, lean, ln in ((0.0, 0.0, 0.11), (0.030, 0.35, 0.085), (-0.030, -0.35, 0.085)):
        root = HEAD.surf(V((dx * 4, -0.15, 1)))
        ctrl = [root - V((0, 0, 0.03)), root + V((lean * 0.03, -0.01, 0.04)),
                root + V((lean * 0.05, -0.045, ln * 0.85)), root + V((lean * 0.06, -0.075, ln))]
        P.tube(ctrl, [(0.0, 0.0, M.fur), (0.08, 0.020, M.fur), (0.45, 0.018, M.fur), (0.85, 0.010, M.fur),
                      (1.0, 0.0, M.fur)], seg=7, cull=HEAD.inside_fn(0.004))
    return top.z + 0.1


def f_raccoon(P, M):
    for s in (1, -1):
        decal(P, HEAD, 0.100 * s, 0.796, 0.084, 0.062, M.mask, h=0.005, roll=-12 * s, rho=(0.88, 1.0))
        decal(P, HEAD, 0.098 * s, 0.878, 0.062, 0.022, M.light, h=0.004, seg=8, roll=10 * s, rho=(0.88, 1.0))
    decal(P, HEAD, 0.0, 0.808, 0.045, 0.032, M.mask, h=0.004, seg=8, rho=(0.88, 1.0))
    muz = oval_muzzle(P, M.light, c=(0.0, -0.205, 0.728), r=(0.082, 0.062, 0.054))
    nose(P, muz, M.nose, z=0.758, r=(0.032, 0.020, 0.021))
    mouth_w(P, muz, M.mouth, z0=0.726)
    tops = []
    for s in (1, -1):
        tops.append(ear(P, M, s, (0.58, 0.05, 0.81), (0.55, 0.08, 1.0), 0.12, 0.078, 0.40, EAR_ROUNDTRI,
                        M.fur, M.inner, turn=0.25))
    return max(t.z for t in tops)


FEATURES = {"corgi": f_corgi, "cat": f_cat, "bunny": f_bunny, "bear": f_bear, "panda": f_panda,
            "frog": f_frog, "penguin": f_penguin, "fox": f_fox, "duck": f_duck, "raccoon": f_raccoon}


def build_head(sp, S, M):
    P = Part("head")
    top = FEATURES[sp](P, M)
    # skull last, so faces fully hidden under muzzles/markings can be dropped
    P.ellipsoid(HEAD.c, HEAD.r, M.fur, n=7, cull=P.covered)
    return P.finish(PIVOT["head"]), max(top, HEAD_TOP)


# ---------------------------------------------------------------------------
# Tails (pivot at the back of the hip)
# ---------------------------------------------------------------------------
def build_tail(sp, S, M):
    P = Part("tail")
    p0 = V(PIVOT["tail"])
    fur = M.fur
    if sp == "corgi":
        ctrl = [p0 + V((0, -0.02, -0.01)), p0 + V((0, 0.05, 0.03)), p0 + V((0, 0.10, 0.09))]
        P.tube(ctrl, [(0.0, 0.0, fur), (0.05, 0.040, fur), (0.35, 0.058, fur), (0.65, 0.054, M.light),
                      (0.88, 0.036, M.light), (1.0, 0.0, M.light)], seg=10, hint=(1, 0, 0))
    elif sp == "cat":
        ctrl = [p0 + V((0, -0.02, 0.0)), p0 + V((0, 0.09, -0.01)), p0 + V((0, 0.17, 0.07)),
                p0 + V((0, 0.19, 0.19)), p0 + V((0, 0.15, 0.30)), p0 + V((0, 0.09, 0.34))]
        keys = [(0.0, 0.0, fur), (0.03, 0.030, fur), (0.25, 0.029, fur), (0.5, 0.028, fur),
                (0.72, 0.028, fur), (0.86, 0.029, M.stripe), (0.96, 0.024, M.stripe), (1.0, 0.0, M.stripe)]
        P.tube(ctrl, keys, seg=8, hint=(1, 0, 0))
    elif sp == "bunny":
        P.ellipsoid(p0 + V((0, 0.045, 0.0)), (0.066, 0.062, 0.064), fur, n=3)
    elif sp in ("bear", "panda"):
        P.ellipsoid(p0 + V((0, 0.03, 0.0)), (0.046, 0.042, 0.044), fur, n=2)
    elif sp == "frog":
        P.ellipsoid(p0 + V((0, 0.02, -0.04)), (0.030, 0.030, 0.026), fur, n=2)
    elif sp == "penguin":
        ctrl = [p0 + V((0, -0.02, 0.0)), p0 + V((0, 0.05, -0.04)), p0 + V((0, 0.085, -0.075))]
        P.tube(ctrl, [(0.0, 0.0, fur), (0.1, 0.045, fur), (0.6, 0.030, fur), (1.0, 0.0, fur)], seg=8,
               hint=(1, 0, 0), scale_keys=[(0.0, 1.0, 0.55), (1.0, 1.0, 0.55)])
    elif sp == "duck":
        ctrl = [p0 + V((0, -0.02, -0.01)), p0 + V((0, 0.05, 0.02)), p0 + V((0, 0.10, 0.08))]
        P.tube(ctrl, [(0.0, 0.0, fur), (0.1, 0.048, fur), (0.55, 0.036, fur), (0.85, 0.018, fur),
                      (1.0, 0.0, fur)], seg=8, hint=(1, 0, 0), scale_keys=[(0.0, 1.0, 0.55), (1.0, 1.0, 0.5)])
    elif sp == "fox":
        ctrl = [p0 + V((0, -0.02, -0.01)), p0 + V((0, 0.10, -0.03)), p0 + V((0, 0.22, 0.03)),
                p0 + V((0, 0.29, 0.15)), p0 + V((0, 0.28, 0.27))]
        keys = [(0.0, 0.0, fur), (0.03, 0.030, fur), (0.16, 0.052, fur), (0.36, 0.080, fur),
                (0.56, 0.088, fur), (0.72, 0.080, M.light), (0.86, 0.058, M.light), (0.96, 0.028, M.light),
                (1.0, 0.0, M.light)]
        P.tube(ctrl, keys, seg=10, hint=(1, 0, 0))
    elif sp == "raccoon":
        ctrl = [p0 + V((0, -0.02, -0.01)), p0 + V((0, 0.10, -0.02)), p0 + V((0, 0.20, 0.04)),
                p0 + V((0, 0.26, 0.15)), p0 + V((0, 0.27, 0.26))]
        rg = M.ring
        keys = [(0.0, 0.0, fur), (0.03, 0.034, fur), (0.20, 0.054, rg), (0.30, 0.060, fur),
                (0.46, 0.063, rg), (0.56, 0.063, fur), (0.72, 0.058, rg), (0.82, 0.052, rg),
                (0.95, 0.032, rg), (1.0, 0.0, rg)]
        P.tube(ctrl, keys, seg=10, hint=(1, 0, 0))
    return P.finish(p0)


ARM_STYLE = {"penguin": "flipper", "duck": "wing"}
WEBBED = {"penguin", "duck", "frog"}


def build_character(sp):
    S = SPECIES[sp]
    M = make_mats(S)
    body = build_body(S, M)
    head, top = build_head(sp, S, M)
    eyes = build_eyes(sp, S, M)
    style = ARM_STYLE.get(sp, "paw")
    armL = build_arm(S, M, 1, style)
    armR = build_arm(S, M, -1, style)
    legL = build_leg(S, M, 1, sp in WEBBED)
    legR = build_leg(S, M, -1, sp in WEBBED)
    tail = build_tail(sp, S, M)
    parts = [body, head, eyes, armL, armR, legL, legR, tail]     # contract order
    assert [p.name.split(".")[0] for p in parts] == PART_ORDER   # (".001" suffixes in lineup scenes)
    kid(eyes, head)                     # child first: kid() makes locations parent-relative
    for p in (head, armL, armR, legL, legR, tail):
        kid(p, body)
    bpy.context.view_layer.update()
    return parts, top


def add_anchors(top):
    B.anchor("hat", (0.0, 0.0, HAT_Z))
    B.anchor("hand", HAND)
    B.anchor("bubble", (0.0, 0.0, max(HEAD_TOP + 0.25, top + 0.08)))


AO_DIST = 0.32
AO_STRENGTH = 0.72


def export_species(sp, preview=True):
    B.reset_scene()
    parts, top = build_character(sp)
    add_anchors(top)
    bpy.context.view_layer.update()
    for p in parts:
        piv = world_pivot(p)
        want = V(PIVOT[p.name])
        assert (piv - want).length < 1e-6, (p.name, piv, want)
    path = os.path.join(B.ASSET_MODELS, "char_%s.bvm" % sp)
    with smoothed_ao():
        tris = B.export_bvm(path, parts, ao=True, ao_strength=AO_STRENGTH, ao_distance=AO_DIST, ao_samples=48)
    print("  parts:", ", ".join("%s=%d" % (p.name, len(p.data.loop_triangles)) for p in parts))
    if preview:
        vc_preview(parts, tint=PREVIEW_TINT[sp], ao_strength=AO_STRENGTH)
        render_view(os.path.join(PREVIEW_CHAR_DIR, "char_%s.png" % sp), target=(0, 0, 0.60), dist=3.35,
                    lens=85)
    return tris


def lineup(species=SPECIES_ORDER, path=None, spacing=0.74):
    B.reset_scene()
    allparts = []
    n = len(species)
    for i, sp in enumerate(species):
        parts, _ = build_character(sp)
        x = (i - (n - 1) / 2) * spacing
        parts[0].location.x += x
        allparts.append((sp, parts))
    bpy.context.view_layer.update()
    flat = [p for _, ps in allparts for p in ps]
    for p in flat:
        B.apply_mods(p)
    bake_ao_smooth(flat, samples=32, distance=AO_DIST)
    for sp, ps in allparts:
        vc_preview(ps, tint=PREVIEW_TINT[sp], ao_strength=AO_STRENGTH)
    render_view(path or os.path.join(PREVIEW_CHAR_DIR, "lineup_characters.png"), target=(0, 0, 0.62),
                dist=11.4, lens=50, res=(2400, 760), samples=64, cam_dir=(0.0, -1.0, 0.30), rig="sun")


def corgi_with_toque(path=None):
    import hats
    B.reset_scene()
    parts, _ = build_character("corgi")
    hat = hats.build_hat("toque")
    hat.location = (0.0, 0.0, HAT_Z)
    bpy.context.view_layer.update()
    allp = parts + [hat]
    bake_ao_smooth(allp, samples=32, distance=AO_DIST)
    vc_preview(allp, tint=PREVIEW_TINT["corgi"], ao_strength=AO_STRENGTH)
    render_view(path or os.path.join(PREVIEW_CHAR_DIR, "char_corgi_toque.png"), target=(0, 0, 0.7),
                dist=4.6, lens=85)


def main():
    args = B.script_args()
    flags = {a for a in args if a.startswith("--")}
    names = [a for a in args if not a.startswith("--")] or SPECIES_ORDER
    report = []
    for sp in names:
        if sp not in SPECIES:
            print("unknown species", sp)
            continue
        report.append((sp, export_species(sp, preview="--no-preview" not in flags)))
    if "--no-lineup" not in flags and not [a for a in args if not a.startswith("--")]:
        lineup()
        corgi_with_toque()
    for sp, t in report:
        print("CHAR %-8s tris=%d %s" % (sp, t, "OK" if t <= 2600 else "OVER BUDGET"))


if __name__ == "__main__":
    main()
