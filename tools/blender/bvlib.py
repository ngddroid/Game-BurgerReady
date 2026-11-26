"""
bvlib — shared Blender helpers for Bun Voyage! asset generation.

Every 3D asset in the game is produced by scripts that import this module and
run headless:

    ~/Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup \
        --python tools/blender/<script>.py -- <args>

Conventions
-----------
* Units are metres. Blender is Z-up; the exporter converts to engine Y-up:
      engine (x, y, z) = blender (x, z, -y)
  Characters face Blender -Y, which becomes engine +Z.
* A model is a set of *parts*. Each part is a single mesh object whose
  **origin is its pivot** (e.g. a shoulder). A Blender parent relation
  between part objects becomes the engine part hierarchy.
* Colour comes from flat "palette" materials (see `mat`). The exporter bakes
  base colour x ambient occlusion into vertex colours. Vertex alpha is the
  **tint mask**: 255 = the engine may recolour it (shirts, customer variants),
  0 = fixed colour.
* Empties whose name starts with "@" are exported as anchors (attach points),
  e.g. "@hat", "@hand", "@tray". Anchors are in model space.

.bvm binary format (little-endian)
----------------------------------
    char[4]  'BVM1'
    u16      partCount
    per part:
        u8 nameLen, utf8 name
        i16 parent index (-1 = root)
        f32 pivot x, y, z            (model space, engine axes)
        u32 vertexCount, u32 indexCount
        vertex x vertexCount:        20 bytes
            f32 px, py, pz           (relative to the part pivot)
            i8  nx, ny, nz, emissive (normal * 127; 4th byte = emission 0..127, emit*40)
            u8  r, g, b, a           (a = tint mask)
        u16 index x indexCount
    u16      anchorCount
    per anchor: u8 nameLen, utf8 name, f32 x, y, z
"""

import bpy
import bmesh
import math
import os
import struct
import sys
from mathutils import Vector, Matrix

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSET_MODELS = os.path.join(ROOT, "app", "src", "main", "assets", "models")
PREVIEW_DIR = os.path.join(ROOT, "tools", "previews")


def script_args():
    """Arguments after the '--' separator on the Blender command line."""
    if "--" in sys.argv:
        return sys.argv[sys.argv.index("--") + 1:]
    return []


# ---------------------------------------------------------------------------
# Palette (art direction, see docs/MASTER_GAME_PLAN.md §3.3)
# ---------------------------------------------------------------------------
def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


PAL = {
    "mustard": "#FFC23C", "tomato": "#F2542D", "mint": "#3DDC97",
    "teal": "#1FA6B8", "cream": "#FFF3DC", "cocoa": "#3B2A3A",
    "white": "#FFFFFF", "offwhite": "#F4EEE6", "black": "#1E1A22",
    "bun": "#E9A04B", "bun_top": "#D9822B", "bun_light": "#F6C27A",
    "sesame": "#FFF1CF", "patty": "#6B3A26", "patty_dark": "#4A2618",
    "cheese": "#FFC72C", "lettuce": "#6CC24A", "lettuce_dark": "#3E9B34",
    "tomato_slice": "#E8452C", "onion": "#F3E3F2", "fries": "#FFCF4A",
    "fries_box": "#E8402A", "steel": "#C9D2DA", "steel_dark": "#8A96A3",
    "wood": "#C98A55", "wood_dark": "#9A6238", "grass": "#8BD45C",
    "asphalt": "#4A4E5A", "pink": "#FF8FB1", "lilac": "#B79CFF",
    "sky": "#8FD8FF", "gold": "#FFCF3F", "gold_dark": "#E0A21B",
    "red": "#E94B3C", "green": "#4CC38A", "blue": "#3D8BFF",
    "navy": "#2C3E66", "orange": "#FF9A3C", "brown": "#8B5A3C",
    "gray": "#9AA3AE", "darkgray": "#5B6470", "glass": "#BDEBFF",
}


def color(c):
    """Accept a palette key, '#RRGGBB' or an (r, g, b) tuple in 0..1."""
    if isinstance(c, str):
        return hex_rgb(PAL.get(c, c))
    return tuple(c[:3])


# ---------------------------------------------------------------------------
# Scene
# ---------------------------------------------------------------------------
_MATS = {}

def reset_scene():
    _MATS.clear()
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.unit_settings.system = 'METRIC'
    if scene.world is None:
        scene.world = bpy.data.worlds.new("World")
    return scene


def mat(c, tint=False, rough=0.55, metal=0.0, emit=0.0, name=None):
    """Flat palette material. `tint=True` marks the faces as recolourable."""
    rgb = color(c)
    key = (rgb, tint, rough, metal, emit)
    if key in _MATS and _MATS[key].name in bpy.data.materials:
        return _MATS[key]
    m = bpy.data.materials.new(name or "M_%02X%02X%02X%s" % (
        int(rgb[0] * 255), int(rgb[1] * 255), int(rgb[2] * 255), "_T" if tint else ""))
    m.diffuse_color = (rgb[0], rgb[1], rgb[2], 1.0)
    m["tint"] = 1 if tint else 0
    m["emit"] = float(emit)
    try:
        if m.node_tree is None:
            m.use_nodes = True
        bsdf = m.node_tree.nodes.get("Principled BSDF")
        if bsdf is None:
            bsdf = m.node_tree.nodes.new("ShaderNodeBsdfPrincipled")
            out = m.node_tree.nodes.get("Material Output") or m.node_tree.nodes.new("ShaderNodeOutputMaterial")
            m.node_tree.links.new(bsdf.outputs[0], out.inputs[0])
        lin = tuple(srgb_to_linear(v) for v in rgb)
        bsdf.inputs["Base Color"].default_value = (lin[0], lin[1], lin[2], 1.0)
        bsdf.inputs["Roughness"].default_value = rough
        bsdf.inputs["Metallic"].default_value = metal
        if emit > 0:
            bsdf.inputs["Emission Color"].default_value = (lin[0], lin[1], lin[2], 1.0)
            bsdf.inputs["Emission Strength"].default_value = emit
    except Exception as e:  # pragma: no cover - node API drift between versions
        print("mat(): node setup skipped:", e)
    _MATS[key] = m
    return m


def srgb_to_linear(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def assign(obj, material):
    obj.data.materials.clear()
    obj.data.materials.append(material)
    return obj


def _active(obj):
    for o in bpy.context.selected_objects:
        o.select_set(False)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)


def _finish(obj, name, material, loc, rot, scale):
    obj.name = name or obj.name
    if material is not None:
        assign(obj, material)
    if loc is not None:
        obj.location = loc
    if rot is not None:
        obj.rotation_euler = [math.radians(a) for a in rot]
    if scale is not None:
        obj.scale = scale if hasattr(scale, "__len__") else (scale, scale, scale)
    return obj


# ---------------------------------------------------------------------------
# Primitives (all return the new object; dimensions in metres)
# ---------------------------------------------------------------------------
def box(size=(1, 1, 1), loc=(0, 0, 0), m=None, bevel=0.03, segs=3, name="box", rot=None):
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 0))
    o = bpy.context.active_object
    o.scale = size
    bpy.ops.object.transform_apply(scale=True)
    if bevel > 0:
        add_bevel(o, bevel, segs)
    return _finish(o, name, m, loc, rot, None)


def cyl(r=0.5, h=1.0, loc=(0, 0, 0), m=None, verts=32, bevel=0.02, segs=2, name="cyl", rot=None, r2=None):
    if r2 is None:
        bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=h, location=(0, 0, 0))
    else:
        bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r, radius2=r2, depth=h, location=(0, 0, 0))
    o = bpy.context.active_object
    if bevel > 0:
        add_bevel(o, bevel, segs)
    return _finish(o, name, m, loc, rot, None)


def sphere(r=0.5, loc=(0, 0, 0), m=None, seg=24, rings=16, scale=None, name="sph", rot=None):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=rings, radius=r, location=(0, 0, 0))
    o = bpy.context.active_object
    o = _finish(o, name, m, loc, rot, scale)
    smooth(o)
    return o


def torus(R=0.5, r=0.1, loc=(0, 0, 0), m=None, maj=32, minr=12, name="tor", rot=None, scale=None):
    bpy.ops.mesh.primitive_torus_add(major_radius=R, minor_radius=r, major_segments=maj,
                                     minor_segments=minr, location=(0, 0, 0))
    o = bpy.context.active_object
    o = _finish(o, name, m, loc, rot, scale)
    smooth(o)
    return o


def capsule(r=0.2, h=0.6, loc=(0, 0, 0), m=None, seg=20, name="cap", rot=None):
    """Vertical capsule; total height h (>= 2r), centred on loc."""
    bm = bmesh.new()
    rings = 8
    body = max(h - 2 * r, 0.0)
    verts_rings = []
    for i in range(rings * 2 + 2):
        if i <= rings:
            a = -math.pi / 2 + (math.pi / 2) * (i / rings)
            z = -body / 2 + r * math.sin(a)
        else:
            a = (math.pi / 2) * ((i - rings - 1) / rings)
            z = body / 2 + r * math.sin(a)
        rr = r * math.cos(a)
        ring = []
        for j in range(seg):
            t = 2 * math.pi * j / seg
            ring.append(bm.verts.new((rr * math.cos(t), rr * math.sin(t), z)))
        verts_rings.append(ring)
    for i in range(len(verts_rings) - 1):
        a, b = verts_rings[i], verts_rings[i + 1]
        for j in range(seg):
            k = (j + 1) % seg
            try:
                bm.faces.new((a[j], a[k], b[k], b[j]))
            except ValueError:
                pass
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(o)
    _active(o)
    o = _finish(o, name, m, loc, rot, None)
    smooth(o)
    return o


def rounded_rect_prism(w, d, h, radius, loc=(0, 0, 0), m=None, seg=6, bevel=0.02, name="rrp"):
    """Extruded rounded rectangle (great for counters, pads, signs)."""
    bm = bmesh.new()
    pts = []
    rx = min(radius, w / 2 - 1e-4)
    ry = min(radius, d / 2 - 1e-4)
    corners = [(w / 2 - rx, d / 2 - ry, 0), (-w / 2 + rx, d / 2 - ry, 90),
               (-w / 2 + rx, -d / 2 + ry, 180), (w / 2 - rx, -d / 2 + ry, 270)]
    for cx, cy, start in corners:
        for i in range(seg + 1):
            a = math.radians(start + 90 * i / seg)
            pts.append((cx + rx * math.cos(a), cy + ry * math.sin(a)))
    bottom = [bm.verts.new((x, y, 0)) for x, y in pts]
    top = [bm.verts.new((x, y, h)) for x, y in pts]
    bm.faces.new(list(reversed(bottom)))
    bm.faces.new(top)
    n = len(pts)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((bottom[i], bottom[j], top[j], top[i]))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(o)
    _active(o)
    if bevel > 0:
        add_bevel(o, bevel, 2)
    o = _finish(o, name, m, loc, None, None)
    return o


# ---------------------------------------------------------------------------
# Modifiers / shading
# ---------------------------------------------------------------------------
def add_bevel(o, width=0.03, segs=3, angle=40):
    mod = o.modifiers.new("Bevel", 'BEVEL')
    mod.width = width
    mod.segments = segs
    mod.limit_method = 'ANGLE'
    mod.angle_limit = math.radians(angle)
    mod.harden_normals = False
    return mod


def subsurf(o, levels=1):
    mod = o.modifiers.new("Subsurf", 'SUBSURF')
    mod.levels = levels
    mod.render_levels = levels
    return mod


def smooth(o, angle=50):
    """Smooth shading with hard edges above `angle` degrees."""
    _active(o)
    try:
        bpy.ops.object.shade_smooth_by_angle(angle=math.radians(angle))
    except Exception:
        bpy.ops.object.shade_smooth()
    return o


def apply_mods(o):
    _active(o)
    for mod in list(o.modifiers):
        try:
            bpy.ops.object.modifier_apply(modifier=mod.name)
        except Exception as e:
            print("apply_mods: could not apply", mod.name, e)
    return o


def join(objs, name, pivot=(0, 0, 0)):
    """Apply modifiers, join objects into one part and set its pivot (world)."""
    objs = [o for o in objs if o is not None]
    for o in objs:
        apply_mods(o)
    for o in bpy.context.selected_objects:
        o.select_set(False)
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    if len(objs) > 1:
        bpy.ops.object.join()
    o = bpy.context.active_object
    o.name = name
    o.data.name = name
    set_pivot(o, pivot)
    return o


def set_pivot(o, pivot):
    """Move the object origin to `pivot` (world) without moving geometry."""
    _active(o)
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    mw = o.matrix_world.copy()
    p = Vector(pivot)
    local = mw.inverted() @ p
    o.data.transform(Matrix.Translation(-local))
    o.matrix_world = Matrix.Translation(mw @ local) @ mw.to_3x3().to_4x4()
    return o


def anchor(name, loc, parent=None):
    """Named attach point, exported as an anchor ('@' is added)."""
    e = bpy.data.objects.new("@" + name.lstrip("@"), None)
    e.location = loc
    bpy.context.collection.objects.link(e)
    if parent is not None:
        mw = e.matrix_world.copy()
        e.parent = parent
        e.matrix_world = mw
    return e


def parent_to(child, parent):
    mw = child.matrix_world.copy()
    child.parent = parent
    child.matrix_world = mw
    return child


def mirror_x(o, name=None):
    """Duplicate an object mirrored across X (useful for left/right limbs)."""
    _active(o)
    bpy.ops.object.duplicate()
    d = bpy.context.active_object
    d.location.x = -d.location.x
    d.scale.x = -d.scale.x
    d.rotation_euler.y = -d.rotation_euler.y
    d.rotation_euler.z = -d.rotation_euler.z
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    # Negative scale flips winding; recalc normals outward.
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.mesh.normals_make_consistent(inside=False)
    bpy.ops.object.mode_set(mode='OBJECT')
    if name:
        d.name = name
    return d


# ---------------------------------------------------------------------------
# Ambient occlusion bake (into a corner colour attribute named "AO")
# ---------------------------------------------------------------------------
def bake_ao(objs, samples=48, distance=0.6):
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    try:
        scene.cycles.device = 'CPU'
        scene.cycles.samples = samples
        scene.cycles.bake_type = 'AO'
    except Exception:
        pass
    scene.world.light_settings.distance = distance
    for o in objs:
        me = o.data
        if "AO" in me.color_attributes:
            me.color_attributes.remove(me.color_attributes["AO"])
        attr = me.color_attributes.new("AO", 'BYTE_COLOR', 'CORNER')
        me.color_attributes.active_color = attr
        try:
            me.attributes.active_color = attr
        except Exception:
            pass
    for o in bpy.context.selected_objects:
        o.select_set(False)
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    try:
        bpy.ops.object.bake(type='AO', target='VERTEX_COLORS', use_clear=True)
        return True
    except Exception as e:
        print("bake_ao failed (continuing without AO):", e)
        return False


# ---------------------------------------------------------------------------
# Export
# ---------------------------------------------------------------------------
def _to_engine(v):
    return (v[0], v[2], -v[1])


def export_bvm(path, parts, ao=True, ao_strength=0.85, ao_distance=0.6, ao_samples=48):
    """
    parts: list of part objects (mesh objects with origin = pivot). Order
    matters: parents must come before children.
    Anchors: every object in the scene named '@...'.
    """
    for o in parts:
        apply_mods(o)
        _active(o)
        bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    if ao:
        bake_ao(parts, samples=ao_samples, distance=ao_distance)

    name_to_index = {o.name: i for i, o in enumerate(parts)}
    out = bytearray()
    out += b"BVM1"
    out += struct.pack("<H", len(parts))
    total_tris = 0
    for o in parts:
        me = o.data
        me.calc_loop_triangles()
        try:
            corner_normals = [Vector(cn.vector) for cn in me.corner_normals]
        except Exception:
            me.calc_normals_split()
            corner_normals = [Vector(l.normal) for l in me.loops]
        ao_attr = me.color_attributes.get("AO") if ao else None
        mw = o.matrix_world
        nmat = mw.to_3x3().inverted().transposed()
        pivot_world = mw.translation.copy()
        mats = me.materials
        verts = []
        index_of = {}
        indices = []
        for tri in me.loop_triangles:
            mi = tri.material_index
            mtl = mats[mi] if mi < len(mats) and mats[mi] is not None else None
            base = (1.0, 0.0, 1.0)
            tint = 0
            emit = 0.0
            if mtl is not None:
                base = tuple(mtl.diffuse_color[:3])
                tint = int(mtl.get("tint", 0))
                emit = float(mtl.get("emit", 0.0))
            for li in tri.loops:
                vi = me.loops[li].vertex_index
                pw = mw @ me.vertices[vi].co
                p = _to_engine(pw - pivot_world)
                n = (nmat @ corner_normals[li]).normalized()
                n = _to_engine(n)
                occ = 1.0
                if ao_attr is not None and emit <= 0:
                    c = ao_attr.data[li].color
                    occ = 1.0 - ao_strength * (1.0 - c[0])
                r, g, b = (min(1.0, base[0] * occ), min(1.0, base[1] * occ), min(1.0, base[2] * occ))
                key = (round(p[0], 5), round(p[1], 5), round(p[2], 5),
                       int(round(n[0] * 127)), int(round(n[1] * 127)), int(round(n[2] * 127)),
                       int(r * 255), int(g * 255), int(b * 255), tint,
                       int(min(127, max(0.0, emit) * 40)))
                idx = index_of.get(key)
                if idx is None:
                    idx = len(verts)
                    index_of[key] = idx
                    verts.append(key)
                indices.append(idx)
        if len(verts) >= 65536:
            raise RuntimeError("part %s has too many vertices (%d)" % (o.name, len(verts)))
        total_tris += len(indices) // 3
        nm = o.name.encode("utf-8")
        parent = -1
        if o.parent is not None and o.parent.name in name_to_index:
            parent = name_to_index[o.parent.name]
        out += struct.pack("<B", len(nm)) + nm
        out += struct.pack("<h", parent)
        out += struct.pack("<3f", *_to_engine(pivot_world))
        out += struct.pack("<II", len(verts), len(indices))
        for k in verts:
            out += struct.pack("<3f", k[0], k[1], k[2])
            out += struct.pack("<4b", max(-127, min(127, k[3])), max(-127, min(127, k[4])),
                               max(-127, min(127, k[5])), k[10])
            out += struct.pack("<4B", k[6], k[7], k[8], 255 if k[9] else 0)
        out += struct.pack("<%dH" % len(indices), *indices)
    anchors = [o for o in bpy.data.objects if o.name.startswith("@")]
    out += struct.pack("<H", len(anchors))
    for a in anchors:
        nm = a.name[1:].encode("utf-8")
        out += struct.pack("<B", len(nm)) + nm
        out += struct.pack("<3f", *_to_engine(a.matrix_world.translation))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(out)
    print("EXPORTED %s parts=%d tris=%d bytes=%d" % (os.path.relpath(path, ROOT), len(parts), total_tris, len(out)))
    return total_tris


# ---------------------------------------------------------------------------
# Beauty renders (icons, thumbnails, onboarding art)
# ---------------------------------------------------------------------------
def setup_render(res=(1024, 1024), transparent=True, samples=96, engine='CYCLES'):
    scene = bpy.context.scene
    scene.render.engine = engine
    scene.render.resolution_x, scene.render.resolution_y = res
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = transparent
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    try:
        scene.view_settings.view_transform = 'Standard'
        scene.view_settings.look = 'None'
    except Exception:
        pass
    if engine == 'CYCLES':
        try:
            scene.cycles.samples = samples
            scene.cycles.use_denoising = True
            prefs = bpy.context.preferences.addons['cycles'].preferences
            prefs.compute_device_type = 'METAL'
            prefs.get_devices()
            for d in prefs.devices:
                d.use = True
            scene.cycles.device = 'GPU'
        except Exception as e:
            print("setup_render: GPU not available, using CPU:", e)
    world = scene.world
    try:
        if world.node_tree is None:
            world.use_nodes = True
        bg = world.node_tree.nodes.get("Background")
        if bg:
            bg.inputs[0].default_value = (1.0, 0.96, 0.9, 1.0)
            bg.inputs[1].default_value = 0.45
    except Exception:
        pass
    return scene


def studio_lights(target=(0, 0, 0.5), strength=1.0):
    def area(name, loc, energy, size, col=(1, 1, 1)):
        d = bpy.data.lights.new(name, 'AREA')
        d.energy = energy * strength
        d.size = size
        d.color = col
        o = bpy.data.objects.new(name, d)
        o.location = loc
        bpy.context.collection.objects.link(o)
        look_at(o, target)
        return o
    area("Key", (2.5, -3.0, 4.0), 380, 3.0, (1.0, 0.95, 0.88))
    area("Fill", (-3.5, -2.0, 2.0), 140, 4.0, (0.85, 0.92, 1.0))
    area("Rim", (0.5, 3.5, 3.0), 260, 2.0, (1.0, 0.9, 0.8))


def look_at(obj, target):
    d = Vector(target) - obj.location
    obj.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()


def camera(loc, target, lens=50, ortho=None):
    c = bpy.data.cameras.new("Cam")
    c.lens = lens
    if ortho:
        c.type = 'ORTHO'
        c.ortho_scale = ortho
    o = bpy.data.objects.new("Cam", c)
    o.location = loc
    bpy.context.collection.objects.link(o)
    look_at(o, target)
    bpy.context.scene.camera = o
    return o


def render(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print("RENDERED", os.path.relpath(path, ROOT))


def preview(path, target=(0, 0, 0.5), dist=4.0, res=(512, 512)):
    """Quick 3/4 view render used to eyeball a model while authoring."""
    setup_render(res=res, transparent=False, samples=32)
    studio_lights(target)
    camera((dist * 0.75, -dist, dist * 0.7 + target[2]), target, lens=50)
    render(path)
