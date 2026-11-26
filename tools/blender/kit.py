"""
kit — extra modelling helpers for Bun Voyage! props, food, stations and stores.

Built on top of `bvlib` (never modifies it). Everything here builds geometry in
Blender world space, bakes the object transform into the mesh and computes
clean "toy" normals (smooth by angle + optional weighted normals so big flat
faces stay flat while bevels read as rounded).

Typical module layout (see food.py / stations.py / building.py / env.py /
stores.py):

    MODELS = {"food_burger": (build_burger, 700, {"ao": 0.12}), ...}
    if __name__ == "__main__":
        kit.run(MODELS)

`kit.run` resets the scene per model, calls the builder (which returns the
part objects, parents first), exports `<name>.bvm` into the game assets and
renders a 3/4 preview into tools/previews/props/<name>.png. Pass model names
after `--` to build only some of them:

    Blender -b --factory-startup --python tools/blender/food.py -- food_burger
"""

import os
import sys
import math
import random
import traceback

import bpy
import bmesh
from mathutils import Vector, Matrix, Euler

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bvlib as B  # noqa: E402

PREVIEW_PROPS = os.path.join(B.PREVIEW_DIR, "props")
PREVIEW_SCENES = os.path.join(B.PREVIEW_DIR, "scenes")
TAU = 2 * math.pi


# ---------------------------------------------------------------------------
# Materials
# ---------------------------------------------------------------------------
def M(c, **kw):
    """Palette material (shortcut for bvlib.mat)."""
    return B.mat(c, **kw)


def T(c="white"):
    """Tint-masked material: near-white base so the engine tint shows true."""
    return B.mat(c, tint=True)


def E(c, s=2.0):
    """Emissive material."""
    return B.mat(c, emit=s)


# ---------------------------------------------------------------------------
# Object plumbing
# ---------------------------------------------------------------------------
def _mk(bm, name, mats):
    me = bpy.data.meshes.new(name)
    bm.normal_update()
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(o)
    for m in mats:
        me.materials.append(m)
    return o


def finish(o, angle=50, wn=False, loc=None, rot=None, scale=None):
    """Apply modifiers + transform, then shade: smooth by `angle` (None = flat),
    optional weighted normals (hard-surface look: flat faces, round bevels)."""
    if loc is not None:
        o.location = loc
    if rot is not None:
        o.rotation_euler = [math.radians(a) for a in rot]
    if scale is not None:
        o.scale = scale if hasattr(scale, "__len__") else (scale, scale, scale)
    B.apply_mods(o)
    B._active(o)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    if angle is None:
        bpy.ops.object.shade_flat()
    else:
        try:
            bpy.ops.object.shade_smooth_by_angle(angle=math.radians(angle), keep_sharp_edges=True)
        except Exception:
            bpy.ops.object.shade_smooth()
    if wn and angle is not None:
        mod = o.modifiers.new("WN", 'WEIGHTED_NORMAL')
        mod.mode = 'FACE_AREA'
        mod.weight = 50
        mod.keep_sharp = True
        B.apply_mods(o)
    return o


def move(objs, loc=(0, 0, 0), rotz=0.0, rot=None):
    """Rigidly move already-finished objects (bakes into mesh data)."""
    R = Euler([math.radians(a) for a in rot]).to_matrix().to_4x4() if rot else \
        Matrix.Rotation(math.radians(rotz), 4, 'Z')
    Mx = Matrix.Translation(Vector(loc)) @ R
    for o in objs:
        o.data.transform(Mx)
        o.data.update()
    return objs


def dup(o, loc=(0, 0, 0), rotz=0.0, rot=None, name=None):
    """Copy a finished object and move the copy."""
    c = o.copy()
    c.data = o.data.copy()
    bpy.context.collection.objects.link(c)
    if name:
        c.name = name
    move([c], loc, rotz, rot)
    return c


def mirror_x(o):
    """Mirrored copy across X=0 (keeps custom normals sane by recomputing)."""
    c = o.copy()
    c.data = o.data.copy()
    bpy.context.collection.objects.link(c)
    c.data.transform(Matrix.Scale(-1, 4, (1, 0, 0)))
    c.data.flip_normals()
    c.data.update()
    return c


def join(objs, name, pivot=(0, 0, 0)):
    return B.join([o for o in objs if o is not None], name, pivot)


def clamp_floor(objs, z=0.0):
    """Flatten any vertex below the floor onto it (props that 'sink' into the ground)."""
    for o in objs:
        if o.type != 'MESH':
            continue
        mw = o.matrix_world
        inv = mw.inverted()
        for v in o.data.vertices:
            p = mw @ v.co
            if p.z < z:
                p.z = z
                v.co = inv @ p
        o.data.update()
    return objs


def bbox(objs):
    lo = Vector((1e9, 1e9, 1e9))
    hi = Vector((-1e9, -1e9, -1e9))
    for o in objs:
        if o.type != 'MESH':
            continue
        mw = o.matrix_world
        for v in o.data.vertices:
            p = mw @ v.co
            lo = Vector((min(lo.x, p.x), min(lo.y, p.y), min(lo.z, p.z)))
            hi = Vector((max(hi.x, p.x), max(hi.y, p.y), max(hi.z, p.z)))
    return lo, hi


def tri_count(o):
    o.data.calc_loop_triangles()
    return len(o.data.loop_triangles)


# ---------------------------------------------------------------------------
# 2D point helpers
# ---------------------------------------------------------------------------
def circle_pts(r, n, sx=1.0, sy=1.0, a0=0.0):
    return [(r * sx * math.cos(a0 + TAU * i / n), r * sy * math.sin(a0 + TAU * i / n)) for i in range(n)]


def rrect_pts(w, d, r, n=3):
    """Rounded rectangle, CCW, `n` segments per corner (n=0 -> sharp)."""
    r = min(r, w / 2 - 1e-4, d / 2 - 1e-4)
    pts = []
    corners = [(w / 2 - r, d / 2 - r, 0), (-w / 2 + r, d / 2 - r, 90),
               (-w / 2 + r, -d / 2 + r, 180), (w / 2 - r, -d / 2 + r, 270)]
    for cx, cy, a0 in corners:
        if n <= 0 or r <= 0:
            pts.append((cx + (r if a0 in (0, 270) else -r), cy + (r if a0 in (0, 90) else -r)))
            continue
        for i in range(n + 1):
            a = math.radians(a0 + 90 * i / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def ccw(pts):
    """Return the polygon wound counter-clockwise."""
    a = sum(pts[i][0] * pts[(i + 1) % len(pts)][1] - pts[(i + 1) % len(pts)][0] * pts[i][1]
            for i in range(len(pts)))
    return list(pts) if a > 0 else list(reversed(pts))


def star_pts(n=5, ro=0.5, ri=0.22, a0=90):
    pts = []
    for i in range(2 * n):
        r = ro if i % 2 == 0 else ri
        a = math.radians(a0) + math.pi * i / n
        pts.append((r * math.cos(a), r * math.sin(a)))
    return pts


def arc_pts(cx, cy, rx, ry, a0, a1, n):
    return [(cx + rx * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cy + ry * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


# ---------------------------------------------------------------------------
# Generators
# ---------------------------------------------------------------------------
def loft(rings, mats, band_mat=None, cap_bot=False, cap_top=False, closed=True,
         name="loft", angle=50, wn=False, face_mat=None, cap_mat=None, recalc=False):
    """Skin a list of 3D rings (each a list of points, or a single pole point).
    Rings must wind CCW seen from the ring's outward 'top' so normals face out
    when the rings go bottom -> top. band_mat[i] = material index of band i.
    face_mat(i_band, j) -> material index overrides per face."""
    bm = bmesh.new()
    vr = []
    for ring in rings:
        vr.append([bm.verts.new(p) for p in ring])
    nb = len(vr) - 1
    for i in range(nb):
        a, b = vr[i], vr[i + 1]
        n = max(len(a), len(b))
        cnt = n if closed else n - 1
        for j in range(cnt):
            k = (j + 1) % n
            if len(a) == 1 and len(b) == 1:
                continue
            if len(a) == 1:
                vs = (a[0], b[k], b[j])
            elif len(b) == 1:
                vs = (a[j], a[k], b[0])
            else:
                vs = (a[j], a[k], b[k], b[j])
            try:
                f = bm.faces.new(vs)
            except ValueError:
                continue
            mi = 0
            if band_mat is not None:
                mi = band_mat[i]
            if face_mat is not None:
                r = face_mat(i, j)
                if r is not None:
                    mi = r
            f.material_index = mi
    if cap_bot and len(vr[0]) > 2:
        f = bm.faces.new(list(reversed(vr[0])))
        f.material_index = (cap_mat[0] if cap_mat else (band_mat[0] if band_mat else 0))
    if cap_top and len(vr[-1]) > 2:
        f = bm.faces.new(vr[-1])
        f.material_index = (cap_mat[1] if cap_mat else (band_mat[-1] if band_mat else 0))
    if recalc:
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    o = _mk(bm, name, mats if isinstance(mats, (list, tuple)) else [mats])
    return finish(o, angle=angle, wn=wn)


def lathe(prof, seg=16, mats=None, band_mat=None, loc=(0, 0, 0), sx=1.0, sy=1.0,
          cap_bot=False, cap_top=False, deform=None, a0=0.0, name="lathe",
          angle=50, wn=False, face_mat=None, rot=None, recalc=False):
    """Revolve a (r, z) profile (bottom -> top) around Z. r == 0 gives a pole.
    deform(i_ring, angle, (x, y, z)) -> (x, y, z) lets you add waves/lumps."""
    rings = []
    for i, (r, z) in enumerate(prof):
        if r < 1e-6:
            p = (0.0, 0.0, z)
            if deform:
                p = deform(i, 0.0, p)
            rings.append([p])
            continue
        ring = []
        for j in range(seg):
            t = a0 + TAU * j / seg
            p = (r * sx * math.cos(t), r * sy * math.sin(t), z)
            if deform:
                p = deform(i, t, p)
            ring.append(p)
        rings.append(ring)
    o = loft(rings, mats, band_mat=band_mat, cap_bot=cap_bot, cap_top=cap_top, name=name,
             angle=angle, wn=wn, face_mat=face_mat, recalc=recalc)
    if rot is not None or any(loc):
        move([o], loc, rot=rot) if rot else move([o], loc)
    return o


def section_loft(sections, mats, band_mat=None, cap_bot=False, cap_top=False, name="sloft",
                 angle=50, wn=False, face_mat=None):
    """sections: list of (pts2d, z) or (pts2d, z, (dx, dy))."""
    rings = []
    for s in sections:
        pts, z = s[0], s[1]
        dx, dy = (s[2] if len(s) > 2 else (0.0, 0.0))
        rings.append([(x + dx, y + dy, z) for x, y in pts])
    return loft(rings, mats, band_mat=band_mat, cap_bot=cap_bot, cap_top=cap_top, name=name,
                angle=angle, wn=wn, face_mat=face_mat)


def prism(pts, h, m, z0=0.0, bevel=0.0, segs=1, name="prism", loc=(0, 0, 0), rot=None,
          angle=50, wn=True, side_m=None, bevel_angle=40, top=True, bottom=True):
    """Extrude a CCW 2D polygon along +Z from z0 to z0+h, then rotate/move.
    rot=(90,0,0) stands an XY drawing up in the XZ plane, extruded toward -Y."""
    bm = bmesh.new()
    bot = [bm.verts.new((x, y, z0)) for x, y in pts]
    tp = [bm.verts.new((x, y, z0 + h)) for x, y in pts]
    if bottom:
        bm.faces.new(list(reversed(bot)))
    if top:
        bm.faces.new(tp)
    n = len(pts)
    for i in range(n):
        j = (i + 1) % n
        f = bm.faces.new((bot[i], bot[j], tp[j], tp[i]))
        if side_m is not None:
            f.material_index = 1
    mats = [m] if side_m is None else [m, side_m]
    o = _mk(bm, name, mats)
    if bevel > 0:
        B.add_bevel(o, bevel, segs, angle=bevel_angle)
        o.modifiers[-1].use_clamp_overlap = True
    if rot is not None:
        o.rotation_euler = [math.radians(a) for a in rot]
    o.location = loc
    return finish(o, angle=angle, wn=wn)


def tube(pts, r, m, seg=6, closed=False, caps=True, name="tube", radii=None, up=None,
         angle=70, seg_mat=None, mats=None, twist=0.0):
    """Sweep a circle along a 3D polyline. `up` = fixed plane normal for planar
    curves (stable frames, e.g. neon outlines). seg_mat(i_segment, j) -> mat idx."""
    P = [Vector(p) for p in pts]
    n = len(P)
    tans = []
    for i in range(n):
        if closed:
            t = (P[(i + 1) % n] - P[i - 1])
        else:
            t = (P[min(i + 1, n - 1)] - P[max(i - 1, 0)])
        tans.append(t.normalized())
    frames = []
    if up is not None:
        U = Vector(up).normalized()
        for t in tans:
            nn = U.cross(t)
            if nn.length < 1e-6:
                nn = Vector((1, 0, 0))
            nn.normalize()
            frames.append((nn, t.cross(nn).normalized()))
    else:
        t0 = tans[0]
        ref = Vector((0, 0, 1)) if abs(t0.z) < 0.9 else Vector((1, 0, 0))
        nn = ref.cross(t0).normalized()
        for t in tans:
            nn = (nn - t * nn.dot(t))
            if nn.length < 1e-6:
                nn = Vector((1, 0, 0))
            nn.normalize()
            frames.append((nn, t.cross(nn).normalized()))
    bm = bmesh.new()
    rings = []
    for i, p in enumerate(P):
        rr = radii[i] if radii else r
        nn, bb = frames[i]
        ring = []
        for j in range(seg):
            a = TAU * j / seg + twist * i
            ring.append(bm.verts.new(p + rr * (math.cos(a) * nn + math.sin(a) * bb)))
        rings.append(ring)
    cnt = n if closed else n - 1
    for i in range(cnt):
        a, b = rings[i], rings[(i + 1) % n]
        for j in range(seg):
            k = (j + 1) % seg
            f = bm.faces.new((a[j], a[k], b[k], b[j]))
            if seg_mat is not None:
                f.material_index = seg_mat(i, j)
    if caps and not closed:
        bm.faces.new(list(reversed(rings[0])))
        bm.faces.new(rings[-1])
    o = _mk(bm, name, mats if mats else [m])
    return finish(o, angle=angle)


def leaf(spine, widths, m, thick=0.02, name="leaf", angle=60, up=(0, 0, 1)):
    """Thin leaf/frond along a 3D spine: diamond cross-section (left, top,
    right, bottom), width per spine point (0 at the tip is fine)."""
    P = [Vector(p) for p in spine]
    U = Vector(up)
    rings = []
    for i, p in enumerate(P):
        t = (P[min(i + 1, len(P) - 1)] - P[max(i - 1, 0)]).normalized()
        side = t.cross(U)
        if side.length < 1e-6:
            side = Vector((1, 0, 0))
        side.normalize()
        nrm = side.cross(t).normalized()
        w = widths[i]
        th = thick * (0.3 + 0.7 * (w / max(widths)))
        if w < 1e-5:
            rings.append([tuple(p)])
            continue
        rings.append([tuple(p - side * w), tuple(p - nrm * th * 0.5), tuple(p + side * w),
                      tuple(p + nrm * th)])
    return loft(rings, [m], name=name, angle=angle, recalc=True, cap_bot=len(rings[0]) > 1)


def rbox(size, loc=(0, 0, 0), m=None, r=0.03, segs=2, rot=None, wn=True, z0=None, name="box"):
    """Bevelled box. If z0 is given, loc z is ignored and the box sits on z0."""
    if z0 is not None:
        loc = (loc[0], loc[1], z0 + size[2] / 2)
    r = min(r, min(size) / 2 - 1e-4) if r > 0 else 0
    o = B.box(size, loc=(0, 0, 0), m=m, bevel=r, segs=segs, name=name)
    if r > 0:
        o.modifiers[-1].use_clamp_overlap = True
    return finish(o, wn=wn, loc=loc, rot=rot)


def rcyl(r, h, loc=(0, 0, 0), m=None, verts=16, bevel=0.015, segs=1, rot=None, r2=None,
         wn=True, z0=None, name="cyl", angle=50):
    """Cylinder/cone centred on loc (or sitting on z0)."""
    if z0 is not None:
        loc = (loc[0], loc[1], z0 + h / 2)
    o = B.cyl(r, h, loc=(0, 0, 0), m=m, verts=verts, bevel=bevel, segs=segs, name=name, r2=r2)
    if bevel > 0:
        o.modifiers[-1].use_clamp_overlap = True
    return finish(o, wn=wn, loc=loc, rot=rot, angle=angle)


def ball(r, loc=(0, 0, 0), m=None, seg=10, rings=6, scale=None, rot=None, name="ball",
         jitter=0.0, seed=1, flat_bottom=None, angle=80):
    """Low-poly UV sphere. jitter = random radial lumps; flat_bottom = clamp z (local)."""
    o = B.sphere(r, loc=(0, 0, 0), m=m, seg=seg, rings=rings, name=name)
    if jitter > 0 or flat_bottom is not None:
        rnd = random.Random(seed)
        for v in o.data.vertices:
            if jitter > 0:
                v.co *= 1.0 + rnd.uniform(-jitter, jitter)
            if flat_bottom is not None and v.co.z < flat_bottom:
                v.co.z = flat_bottom
    return finish(o, angle=angle, loc=loc, rot=rot, scale=scale)


def seed_shape(loc, normal, size, m, spin=0.0, name="seed"):
    """Tiny flattened hexagonal bipyramid (12 tris): sesame/oat seeds, crumbs, specks."""
    hexa = [(size * math.cos(TAU * j / 6), size * 0.55 * math.sin(TAU * j / 6), 0.0) for j in range(6)]
    o = loft([[(0, 0, -size * 0.35)], hexa, [(0, 0, size * 0.35)]], [m], name=name, angle=80)
    q = Vector(normal).normalized().to_track_quat('Z', 'Y')
    Mx = Matrix.Translation(Vector(loc)) @ q.to_matrix().to_4x4() @ Matrix.Rotation(spin, 4, 'Z')
    o.data.transform(Mx)
    o.data.update()
    return o


def plate_on(o, z):
    """Translate object so its lowest vertex sits on z."""
    lo, _ = bbox([o])
    move([o], (0, 0, z - lo.z))
    return o


# ---------------------------------------------------------------------------
# Flat tiles (tiny meshes built by hand; top surface exactly at z = 0)
# ---------------------------------------------------------------------------
def tile_bm():
    return bmesh.new()


def quad(bm, vs, mi=0):
    f = bm.faces.new([bm.verts.new(v) if not isinstance(v, bmesh.types.BMVert) else v for v in vs])
    f.material_index = mi
    return f


# ---------------------------------------------------------------------------
# Preview rendering
# ---------------------------------------------------------------------------
def _sock(node, name, typ, out=False):
    for s in (node.outputs if out else node.inputs):
        if s.name == name and s.type == typ:
            return s
    raise KeyError(name)


def ao_preview_materials(parts, strength=0.85, tint_color="#F2542D"):
    """Make the preview show the baked AO exactly like the engine (base x AO).
    Tint-masked materials are shown multiplied by `tint_color` (preview only)."""
    seen = set()
    for o in parts:
        if o.type != 'MESH' or "AO" not in o.data.color_attributes:
            continue
        for m in o.data.materials:
            if m is None or m.name in seen:
                continue
            seen.add(m.name)
            if float(m.get("emit", 0.0)) > 0:
                continue
            nt = m.node_tree
            bsdf = nt.nodes.get("Principled BSDF")
            if bsdf is None:
                continue
            base = tuple(bsdf.inputs["Base Color"].default_value)
            if int(m.get("tint", 0)) and tint_color:
                tc = [B.srgb_to_linear(v) for v in B.color(tint_color)]
                base = (base[0] * tc[0], base[1] * tc[1], base[2] * tc[2], 1.0)
            vc = nt.nodes.new("ShaderNodeVertexColor")
            vc.layer_name = "AO"
            mix = nt.nodes.new("ShaderNodeMix")
            mix.data_type = 'RGBA'
            mix.blend_type = 'MIX'
            mix.inputs[0].default_value = strength
            _sock(mix, "A", 'RGBA').default_value = (1, 1, 1, 1)
            nt.links.new(vc.outputs["Color"], _sock(mix, "B", 'RGBA'))
            mul = nt.nodes.new("ShaderNodeMix")
            mul.data_type = 'RGBA'
            mul.blend_type = 'MULTIPLY'
            mul.inputs[0].default_value = 1.0
            _sock(mul, "A", 'RGBA').default_value = base
            nt.links.new(_sock(mix, "Result", 'RGBA', out=True), _sock(mul, "B", 'RGBA'))
            nt.links.new(_sock(mul, "Result", 'RGBA', out=True), bsdf.inputs["Base Color"])


def light_rig(center, size, sun=2.6, world=0.38):
    scene = bpy.context.scene
    d = bpy.data.lights.new("Sun", 'SUN')
    d.energy = sun
    d.angle = math.radians(10)
    d.color = (1.0, 0.95, 0.86)
    o = bpy.data.objects.new("Sun", d)
    o.location = Vector(center) + Vector((0.55, -0.75, 1.0)) * 10
    bpy.context.collection.objects.link(o)
    B.look_at(o, center)
    d2 = bpy.data.lights.new("Fill", 'SUN')
    d2.energy = sun * 0.25
    d2.angle = math.radians(30)
    d2.color = (0.85, 0.9, 1.0)
    o2 = bpy.data.objects.new("Fill", d2)
    o2.location = Vector(center) + Vector((-0.8, -0.3, 0.5)) * 10
    bpy.context.collection.objects.link(o2)
    B.look_at(o2, center)
    try:
        bg = scene.world.node_tree.nodes.get("Background")
        bg.inputs[0].default_value = (0.92, 0.9, 1.0, 1.0)
        bg.inputs[1].default_value = world
    except Exception:
        pass


def ground_plane(z, size, color="#B9B1A8"):
    bpy.ops.mesh.primitive_plane_add(size=size, location=(0, 0, z))
    g = bpy.context.active_object
    g.name = "PreviewGround"
    B.assign(g, B.mat(color, rough=0.9, name="PreviewGroundMat"))
    return g


def ortho_camera(lo, hi, yaw=32, pitch=42, margin=1.12):
    center = (lo + hi) / 2
    yr, pr = math.radians(yaw), math.radians(pitch)
    cdir = Vector((math.sin(yr) * math.cos(pr), -math.cos(yr) * math.cos(pr), math.sin(pr)))
    f = -cdir
    right = f.cross(Vector((0, 0, 1))).normalized()
    up = right.cross(f).normalized()
    xs, ys = [], []
    for x in (lo.x, hi.x):
        for y in (lo.y, hi.y):
            for z in (lo.z, hi.z):
                p = Vector((x, y, z)) - center
                xs.append(p.dot(right))
                ys.append(p.dot(up))
    span = max(max(xs) - min(xs), max(ys) - min(ys)) * margin
    # recentre on the projected box
    cx = (max(xs) + min(xs)) / 2
    cy = (max(ys) + min(ys)) / 2
    tgt = center + right * cx + up * cy
    dist = (hi - lo).length * 2 + 10
    cam = B.camera(tgt + cdir * dist, tgt, ortho=max(span, 0.05))
    cam.data.clip_end = dist * 4
    return cam


def preview(path, parts, yaw=32, pitch=42, res=512, ground=True, ao=True, samples=32, margin=1.12,
            tint_color="#F2542D", ao_strength=0.85):
    objs = [o for o in parts if o.type == 'MESH']
    for o in objs:
        for c in o.children_recursive:
            if c.type == 'MESH' and c not in objs:
                objs.append(c)
    lo, hi = bbox(objs)
    if ao:
        ao_preview_materials(objs, strength=ao_strength, tint_color=tint_color)
    B.setup_render(res=(res, res), transparent=False, samples=samples)
    light_rig((lo + hi) / 2, (hi - lo).length)
    if ground:
        ground_plane(lo.z - 0.002, max((hi - lo).length * 30, 10))
    ortho_camera(lo, hi, yaw, pitch, margin)
    B.render(path)


# ---------------------------------------------------------------------------
# Runner
# ---------------------------------------------------------------------------
RESULTS = []


def make(name, builder, budget=None, ao=0.35, prev=True, prev_kw=None, ao_strength=0.85):
    B.reset_scene()
    parts = builder()
    if not isinstance(parts, (list, tuple)):
        parts = [parts]
    path = os.path.join(B.ASSET_MODELS, name + ".bvm")
    tris = B.export_bvm(path, list(parts), ao_distance=ao, ao_strength=ao_strength)
    ok = budget is None or tris <= budget
    RESULTS.append((name, tris, budget, ok))
    if not ok:
        print("OVER BUDGET %s tris=%d budget=%d" % (name, tris, budget))
    if prev:
        kw = dict(prev_kw or {})
        kw.setdefault("ao_strength", ao_strength)
        preview(os.path.join(PREVIEW_PROPS, name + ".png"), parts, **kw)
    return tris


def run(models, argv=None):
    """models: {name: (builder, budget, opts)}; opts keys: ao, prev_kw."""
    argv = B.script_args() if argv is None else argv
    want = [a for a in argv if not a.startswith("-")]
    noprev = "--noprev" in argv
    failed = []
    for name, spec in models.items():
        if want and not any(name == w or name.startswith(w.rstrip("*")) and w.endswith("*") for w in want):
            continue
        fn, budget = spec[0], spec[1]
        opts = spec[2] if len(spec) > 2 else {}
        try:
            make(name, fn, budget, ao=opts.get("ao", 0.35), prev=not noprev,
                 prev_kw=opts.get("prev_kw"), ao_strength=opts.get("ao_strength", 0.85))
        except Exception:
            traceback.print_exc()
            failed.append(name)
    print("\n%-34s %7s %7s" % ("model", "tris", "budget"))
    for name, tris, budget, ok in RESULTS:
        print("%-34s %7d %7s %s" % (name, tris, budget if budget else "-", "" if ok else "OVER"))
    if failed:
        print("FAILED:", ", ".join(failed))
    return failed
