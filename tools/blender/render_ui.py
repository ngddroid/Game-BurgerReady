"""
render_ui.py - HD UI art for Bun Voyage! rendered from the game's own builders
(characters.py, hats.py, pets.py, food.py, stations.py + bvlib) with Cycles.

Outputs (exact names, the app loads them by name)
    app/src/main/assets/ui/skin_<id>.png          512x512 RGBA  chef-skin busts
    app/src/main/assets/ui/pet_<id>.png           512x512 RGBA  pets + pet_none
    app/src/main/assets/ui/onboard_1..4.png       1024x1024 RGBA vignettes
    app/src/main/assets/ui/card_guest_<sp>.png    256x256 RGBA  head portraits
    tools/icon/icon_full.png                      1024x1024 opaque app icon
    tools/icon/icon_fg.png / icon_bg.png          1024x1024 adaptive-icon layers
    tools/icon/feature_graphic.png                1024x500 opaque Play feature graphic

Run (all groups, or name some: skins pets cards onboard icon)
    ~/Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup \
        --python tools/blender/render_ui.py -- [group ...] [--fast]

Renders go through a temporary PNG, are re-encoded here with a small numpy PNG
writer (best-of filter per row, zlib 9), and anything still over 400 KB is
palette-quantised (256 colours, ordered dither) when that is smaller.
"""
import os
import sys
import math
import random
import shutil
import struct
import tempfile
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpy
import numpy as np
from mathutils import Vector as V, Matrix
from bpy_extras.object_utils import world_to_camera_view

import bvlib as B
import characters as C
import hats as H
import pets as PT

try:                                # other artists' builders: optional
    import food as FOOD
except Exception as e:              # pragma: no cover
    print("render_ui: food.py unavailable, using a simple burger:", e)
    FOOD = None
try:
    import stations as ST
except Exception as e:              # pragma: no cover
    print("render_ui: stations.py unavailable, using simple shapes:", e)
    ST = None
try:
    import kit as KIT
except Exception:                   # pragma: no cover
    KIT = None

UI_DIR = os.path.join(B.ROOT, "app", "src", "main", "assets", "ui")
ICON_DIR = os.path.join(B.ROOT, "tools", "icon")
FONT_TITLE = os.path.join(B.ROOT, "app", "src", "main", "assets", "fonts", "LilitaOne.ttf")
MAX_BYTES = 400 * 1024
HAT_TINT = "#F2542D"
FAST = False

SKINS = [  # id, species, hat, apron (shirt tint)
    ("patty", "corgi", "toque", "#FFFFFF"),
    ("miso", "cat", "toque", "#FF8FB1"),
    ("hopper", "bunny", "beret", "#3DDC97"),
    ("bruno", "bear", "cap", "#F2542D"),
    ("bamboo", "panda", "bandana", "#1FA6B8"),
    ("ribbit", "frog", "cowboy", "#FFC23C"),
    ("pip", "penguin", "beanie", "#3D8BFF"),
    ("ember", "fox", "party", "#B79CFF"),
    ("quackers", "duck", "flower", "#FF9A3C"),
    ("bandit", "raccoon", "crown", "#2C3E66"),
    ("astro", "corgi", "helmet", "#E8F4FF"),
]
# Render-only hat lifts where a brim would cover the eyes (frog eye bumps sit on the crown).
HAT_LIFT = {("frog", "cowboy"): (0.0, 0.02, 0.075, -10.0)}   # dx, dy, dz, tilt back (deg)


# ---------------------------------------------------------------------------
# PNG I/O (numpy + zlib; no PIL needed)
# ---------------------------------------------------------------------------
def _filter_rows(a):
    """PNG adaptive filtering: per row pick the filter with the smallest abs sum."""
    h, w, c = a.shape
    x = a.reshape(h, w * c).astype(np.int16)
    left = np.zeros_like(x)
    left[:, c:] = x[:, :-c]
    up = np.zeros_like(x)
    up[1:] = x[:-1]
    ul = np.zeros_like(x)
    ul[1:, c:] = x[:-1, :-c]
    p = left + up - ul
    pa, pb, pc = np.abs(p - left), np.abs(p - up), np.abs(p - ul)
    paeth = np.where((pa <= pb) & (pa <= pc), left, np.where(pb <= pc, up, ul))
    cands = [x, x - left, x - up, x - ((left + up) >> 1), x - paeth]
    cands = [(cc % 256).astype(np.uint8) for cc in cands]
    score = np.stack([np.abs(cc.astype(np.int8).astype(np.int16)).sum(axis=1) for cc in cands])
    best = score.argmin(axis=0)
    out = np.empty((h, w * c + 1), np.uint8)
    out[:, 0] = best
    for f in range(5):
        rows = best == f
        out[rows, 1:] = cands[f][rows]
    return out.tobytes()


def _chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def png_bytes(a, palette=None, trns=None):
    """a: (h, w, 3|4) uint8 truecolour, or (h, w) uint8 indices with palette."""
    if palette is not None:
        h, w = a.shape
        raw = np.zeros((h, w + 1), np.uint8)
        raw[:, 1:] = a
        body = _chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 3, 0, 0, 0))
        body += _chunk(b"PLTE", palette.astype(np.uint8).tobytes())
        if trns is not None:
            body += _chunk(b"tRNS", trns.astype(np.uint8).tobytes())
        body += _chunk(b"IDAT", zlib.compress(raw.tobytes(), 9))
    else:
        h, w, c = a.shape
        ctype = 6 if c == 4 else 2
        body = _chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, ctype, 0, 0, 0))
        body += _chunk(b"IDAT", zlib.compress(_filter_rows(a), 9))
    return b"\x89PNG\r\n\x1a\n" + body + _chunk(b"IEND", b"")


BAYER = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) + 0.5) / 16.0 - 0.5


def quantize(a, n=256, iters=8, seed=7):
    """k-means palette (RGBA) + 4x4 ordered dither -> (indices, palette, trns)."""
    h, w, c = a.shape
    if c == 3:
        a = np.concatenate([a, np.full((h, w, 1), 255, np.uint8)], axis=2)
    f = a.reshape(-1, 4).astype(np.float32)
    # premultiply so fully transparent pixels collapse together
    f[:, :3] *= f[:, 3:4] / 255.0
    rng = np.random.default_rng(seed)
    sample = f[rng.choice(len(f), size=min(len(f), 20000), replace=False)]
    uniq = np.unique(sample, axis=0)
    cent = uniq[rng.choice(len(uniq), size=min(n, len(uniq)), replace=False)]
    for _ in range(iters):
        d = ((sample[:, None, :] - cent[None, :, :]) ** 2).sum(-1)
        lab = d.argmin(1)
        for k in range(len(cent)):
            sel = sample[lab == k]
            if len(sel):
                cent[k] = sel.mean(0)
    ramp = np.zeros((17, 4), np.float32)
    ramp[:, 3] = np.linspace(0, 255, 17)
    cent = np.concatenate([cent[: max(1, n - 17)], ramp])
    dith = np.tile(BAYER, (h // 4 + 1, w // 4 + 1))[:h, :w].reshape(-1, 1) * 12.0
    g = f.copy()
    edge = (f[:, 3:4] > 0) & (f[:, 3:4] < 255)
    g[:, :3] += dith * (f[:, 3:4] > 0)
    g[:, 3:4] += dith * edge
    idx = np.empty(len(g), np.uint8)
    cc = (cent ** 2).sum(1)
    for s in range(0, len(g), 65536):
        blk = g[s:s + 65536]
        d = cc[None, :] - 2.0 * blk @ cent.T
        idx[s:s + 65536] = d.argmin(1)
    pal = cent.copy()
    alpha = np.clip(pal[:, 3], 0, 255)
    rgb = np.where(alpha[:, None] > 0, pal[:, :3] * 255.0 / np.maximum(alpha[:, None], 1e-3), 0)
    return idx.reshape(h, w), np.clip(np.round(rgb), 0, 255), np.clip(np.round(alpha), 0, 255)


def save_png(path, a):
    """Write uint8 (h, w, 3|4) top-down; quantise if still above MAX_BYTES."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = png_bytes(a)
    note = ""
    if len(data) > MAX_BYTES:
        idx, pal, trns = quantize(a)
        q = png_bytes(idx, palette=pal, trns=trns if a.shape[2] == 4 else None)
        if len(q) < len(data):
            note = " (quantised from %d KB)" % (len(data) // 1024)
            data = q
    with open(path, "wb") as fh:
        fh.write(data)
    print("UI %-44s %4d KB%s" % (os.path.relpath(path, B.ROOT), len(data) // 1024, note))
    return len(data)


def load_rgba(path):
    img = bpy.data.images.load(path)
    w, h = img.size
    px = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)
    bpy.data.images.remove(img)
    return np.clip(np.round(px[::-1] * 255.0), 0, 255).astype(np.uint8)   # top-down rows


# ---------------------------------------------------------------------------
# Backgrounds (numpy)
# ---------------------------------------------------------------------------
def _rgb(c):
    return np.array(B.color(c), np.float32)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def sunburst(w, h, center=(0.5, 0.5), inner="#FFC23C", outer="#F2542D", rays=16, ray_amt=0.075,
             vignette=0.30, radius=None):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    cx, cy = center[0] * w, center[1] * h
    dx, dy = x - cx, y - cy
    R = radius or 0.72 * max(w, h)
    r = np.sqrt(dx * dx + dy * dy) / R
    t = smoothstep(0.0, 1.0, r) ** 0.85
    col = _rgb(inner)[None, None] * (1 - t[..., None]) + _rgb(outer)[None, None] * t[..., None]
    glow = np.exp(-(r / 0.28) ** 2)[..., None]
    col = col * (1 - 0.35 * glow) + np.array([1.0, 0.93, 0.62], np.float32) * 0.35 * glow
    ang = np.arctan2(dy, dx)
    ray = smoothstep(0.30, 0.70, 0.5 + 0.5 * np.cos(ang * rays))
    col = col * (1.0 + ray_amt * (ray[..., None] * 2 - 1) * smoothstep(0.05, 0.35, r)[..., None])
    rc = np.sqrt((x / w - 0.5) ** 2 + (y / h - 0.5) ** 2) / math.sqrt(0.5)
    col = col * (1.0 - vignette * smoothstep(0.55, 1.05, rc))[..., None]
    return np.clip(col, 0, 1)


def over(fg, bg):
    """Straight-alpha uint8 RGBA over float RGB background -> uint8 RGB."""
    a = fg[..., 3:4].astype(np.float32) / 255.0
    out = fg[..., :3].astype(np.float32) / 255.0 * a + bg * (1 - a)
    return np.clip(np.round(out * 255.0), 0, 255).astype(np.uint8)


# ---------------------------------------------------------------------------
# Scene plumbing
# ---------------------------------------------------------------------------
_TINTED = {}
_TMP = tempfile.mkdtemp(prefix="bv_ui_")


def new_scene():
    _TINTED.clear()
    B.reset_scene()


def tinted(m, hexcol, emit=0.0, rough=None):
    key = (m.name, hexcol, emit, rough)
    if key in _TINTED:
        return _TINTED[key]
    c = m.copy()
    rgb = B.color(hexcol)
    lin = [B.srgb_to_linear(v) for v in rgb]
    c.diffuse_color = (rgb[0], rgb[1], rgb[2], 1.0)
    bsdf = c.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (lin[0], lin[1], lin[2], 1.0)
    if rough is not None:
        bsdf.inputs["Roughness"].default_value = rough
    if emit:
        bsdf.inputs["Emission Color"].default_value = (lin[0], lin[1], lin[2], 1.0)
        bsdf.inputs["Emission Strength"].default_value = emit
    _TINTED[key] = c
    return c


def retint(objs, hexcol, emit=0.0):
    for o in objs:
        if o is None or o.type != 'MESH':
            continue
        for i, m in enumerate(o.data.materials):
            if m is not None and m.get("tint"):
                o.data.materials[i] = tinted(m, hexcol, emit)


def meshes(objs):
    out = []
    for o in objs:
        if o is None:
            continue
        if o.type == 'MESH':
            out.append(o)
        out += meshes(list(o.children))
    return list(dict.fromkeys(out))


def smoothen(objs, level=2):
    """UI renders only: Subdivision Surface (render level 2) + fully smooth shading so
    the game-resolution meshes read as smooth toys at 1024 px. Exports are untouched."""
    for o in meshes(objs):
        if o.name.startswith(("cshadow", "conf")) or any(m.type == 'SUBSURF' for m in o.modifiers):
            continue
        me = o.data
        if me.users > 1:
            o.data = me = me.copy()
        try:
            if getattr(me, "has_custom_normals", False):
                B._active(o)
                bpy.ops.mesh.customdata_custom_splitnormals_clear()
        except Exception:
            pass
        try:
            me.shade_smooth()
        except Exception:
            for f in me.polygons:
                f.use_smooth = True
        mod = o.modifiers.new("UISubsurf", 'SUBSURF')
        mod.levels = 0
        mod.render_levels = level
        try:
            mod.boundary_smooth = 'PRESERVE_CORNERS'
        except Exception:
            pass
    return objs


ARM_REST = {"armL": V((0.5, -0.03, -1.0)), "armR": V((-0.5, -0.03, -1.0))}


def aim(obj, rest_dir, target_dir, twist=0.0):
    """Rotate a limb about its pivot so its rest direction points along target_dir."""
    q = V(rest_dir).normalized().rotation_difference(V(target_dir).normalized())
    if twist:
        from mathutils import Quaternion
        q = Quaternion(V(target_dir).normalized(), math.radians(twist)) @ q
    obj.rotation_mode = 'QUATERNION'
    obj.rotation_quaternion = q


class Char:
    """A posed character (optionally with a hat parented to its head).
    pose values: Euler degrees (x, y, z), or ("aim", (dx, dy, dz)) for arms."""

    def __init__(self, sp, hat=None, apron="#FFFFFF", hat_tint=HAT_TINT, loc=(0, 0, 0), rotz=0.0,
                 pose=None, head_only=False, hat_scale=1.0, hat_offset=(0, 0, 0, 0)):
        parts, self.top = C.build_character(sp)
        self.sp = sp
        self.p = {o.name.split(".")[0]: o for o in parts}
        self.parts = parts
        retint(parts, apron)
        self.hat = None
        bpy.context.view_layer.update()
        if hat:
            h = H.build_hat(hat)
            retint([h], hat_tint)
            dx, dy, dz, tilt = HAT_LIFT.get((sp, hat), hat_offset)
            h.location = (dx, dy, C.HAT_Z + dz)
            h.rotation_euler = (math.radians(-tilt), 0, 0)
            h.scale = (hat_scale, hat_scale, hat_scale)
            bpy.context.view_layer.update()
            B.parent_to(h, self.p["head"])
            self.hat = h
        for name, rot in (pose or {}).items():
            if rot and rot[0] == "aim":
                aim(self.p[name], ARM_REST.get(name, V((0, 0, -1))), rot[1])
            else:
                self.p[name].rotation_euler = [math.radians(a) for a in rot]
        body = self.p["body"]
        body.location = V(loc) + V(C.PIVOT["body"])
        body.rotation_euler.z = math.radians(rotz)
        if head_only:
            for n in ("body", "armL", "armR", "legL", "legR", "tail"):
                self.p[n].hide_render = True
        bpy.context.view_layer.update()

    def objs(self):
        out = [o for o in self.parts if not o.hide_render]
        if self.hat:
            out.append(self.hat)
        return out

    def hand(self, name="armL"):
        """World position of a paw (end of the arm sweep) for props."""
        arm = self.p[name]
        mw = arm.matrix_world
        far = max((mw @ v.co for v in arm.data.vertices), key=lambda p: (p - mw.translation).length)
        return far


def burger(loc=(0, 0, 0), scale=1.0, rot=(0, 0, 0)):
    if FOOD is not None:
        o = FOOD.build_burger()[0]
        for a in [x for x in bpy.data.objects if x.name.startswith("@")]:
            bpy.data.objects.remove(a)
    else:
        P = C.Part("burger")
        P.ellipsoid((0, 0, 0.03), (0.14, 0.14, 0.035), B.mat("bun"), n=3)
        P.ellipsoid((0, 0, 0.075), (0.155, 0.155, 0.025), B.mat("patty"), n=3)
        P.ellipsoid((0, 0, 0.10), (0.16, 0.16, 0.012), B.mat("lettuce"), n=3)
        P.ellipsoid((0, 0, 0.11), (0.145, 0.145, 0.075), B.mat("bun_top"), n=4, zmin=0.11)
        o = P.finish((0, 0, 0))
    o.location = V(loc)
    o.scale = (scale, scale, scale)
    o.rotation_euler = [math.radians(a) for a in rot]
    return o


def burger_height():
    return 0.168


def shadow_mat(opacity=0.30):
    """Camera-only soft contact shadow: near-black emission mixed with transparency
    by a smooth radial falloff (no lighting, no noise, nothing outside the disc)."""
    m = bpy.data.materials.new("ContactShadow")
    if m.node_tree is None:
        m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    tc = nt.nodes.new("ShaderNodeTexCoord")
    ln = nt.nodes.new("ShaderNodeVectorMath")
    ln.operation = 'LENGTH'
    mr = nt.nodes.new("ShaderNodeMapRange")
    mr.interpolation_type = 'SMOOTHSTEP'
    mr.inputs["From Min"].default_value = 0.0
    mr.inputs["From Max"].default_value = 1.0
    mr.inputs["To Min"].default_value = opacity
    mr.inputs["To Max"].default_value = 0.0
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    em = nt.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = (0.035, 0.022, 0.03, 1.0)
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(tc.outputs["Object"], ln.inputs[0])
    nt.links.new(ln.outputs["Value"], mr.inputs["Value"])
    nt.links.new(mr.outputs["Result"], mix.inputs["Fac"])
    nt.links.new(tr.outputs[0], mix.inputs[1])
    nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs["Surface"])
    try:
        m.blend_method = 'BLEND'
    except Exception:
        pass
    return m


def contact_shadow(objs, opacity=0.32, grow=1.9, zmax=0.10, min_r=0.24, center=None, radius=None):
    """Small round shadow disc under the feet/base of objs (fitted to the vertices
    below zmax), visible to the camera only."""
    bpy.context.view_layer.update()
    if radius is None:
        pts = []
        for o in meshes(objs):
            if o.hide_render:
                continue
            mw = o.matrix_world
            pts += [p for p in (mw @ v.co for v in o.data.vertices) if p.z < zmax]
        if not pts:
            return None
        x0, x1 = min(p.x for p in pts), max(p.x for p in pts)
        y0, y1 = min(p.y for p in pts), max(p.y for p in pts)
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        rx, ry = max((x1 - x0) / 2 * grow, min_r), max((y1 - y0) / 2 * grow, min_r)
    else:
        cx, cy = center
        rx, ry = radius
    n = 48
    me = bpy.data.meshes.new("cshadow")
    me.from_pydata([(0, 0, 0)] + [(math.cos(6.2831853 * i / n), math.sin(6.2831853 * i / n), 0) for i in range(n)],
                   [], [(0, 1 + i, 1 + (i + 1) % n) for i in range(n)])
    o = bpy.data.objects.new("cshadow", me)
    bpy.context.collection.objects.link(o)
    o.location = (cx, cy, 0.0008)
    o.scale = (rx, ry, 1.0)
    me.materials.append(shadow_mat(opacity))
    for attr in ("visible_shadow", "visible_diffuse", "visible_glossy", "visible_transmission",
                 "visible_volume_scatter"):
        try:
            setattr(o, attr, False)
        except Exception:
            pass
    return o


def lights(key=2.6, fill=0.7, rim=1.2, world=0.62, soft=22.0, key_dir=(0.45, -0.70, 1.0),
           target=(0, 0, 0.6), rim_scale=1.0):
    scene = bpy.context.scene

    def sun(name, d, e, ang, col):
        L = bpy.data.lights.new(name, 'SUN')
        L.energy = e
        L.angle = math.radians(ang)
        L.color = col
        o = bpy.data.objects.new(name, L)
        scene.collection.objects.link(o)
        o.rotation_euler = V(d).normalized().to_track_quat('Z', 'Y').to_euler()
        return o
    sun("Key", key_dir, key, soft, (1.0, 0.95, 0.88))
    sun("Fill", (-0.9, -0.5, 0.45), fill, 35, (0.86, 0.92, 1.0))
    L = bpy.data.lights.new("Rim", 'AREA')
    L.energy = 260 * rim * rim_scale ** 2
    L.size = 2.5 * rim_scale
    L.color = (1.0, 0.92, 0.82)
    o = bpy.data.objects.new("Rim", L)
    scene.collection.objects.link(o)
    o.location = V(target) + V((-0.8, 3.0, 2.6)) * rim_scale
    B.look_at(o, target)
    bg = scene.world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs[0].default_value = (1.0, 0.95, 0.88, 1.0)
        bg.inputs[1].default_value = world


def setup(res, samples=96):
    s = B.setup_render(res=res, transparent=True, samples=max(16, samples // 3) if FAST else samples)
    s.render.image_settings.compression = 15
    try:
        s.cycles.use_adaptive_sampling = True
        s.render.film_transparent = True
    except Exception:
        pass
    return s


def camera_fit(objs, cam_dir, lens=70, margins=(0.07, 0.07, 0.07, 0.07), z_cut=None, anchor_bottom=False,
               iters=5, ortho=False, include=None):
    """Aim a camera along cam_dir at the objects and fit them inside the frame.
    margins = (left, right, bottom, top) as frame fractions. With z_cut only points
    above that height are fitted (busts); anchor_bottom pins them to the bottom edge."""
    scene = bpy.context.scene
    rx, ry = scene.render.resolution_x, scene.render.resolution_y
    bpy.context.view_layer.update()
    pts = []
    for o in meshes(objs):
        mw = o.matrix_world
        pts += [mw @ v.co for v in o.data.vertices]
    if include:
        pts += [V(p) for p in include]
    if z_cut is not None:
        pts = [p for p in pts if p.z >= z_cut]
    lo = V((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = V((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    target = (lo + hi) / 2
    d = V(cam_dir).normalized()
    D = (hi - lo).length * 2.2 * 85.0 / lens
    cam = B.camera(tuple(target + d * D), tuple(target), lens=lens)
    ml, mr, mb, mt = margins

    def ndc():
        bpy.context.view_layer.update()
        cs = [world_to_camera_view(scene, cam, p) for p in pts]
        return min(c.x for c in cs), max(c.x for c in cs), min(c.y for c in cs), max(c.y for c in cs)
    for _ in range(iters):
        cam.location = target + d * D
        B.look_at(cam, target)
        cam.data.shift_x = cam.data.shift_y = 0.0
        x0, x1, y0, y1 = ndc()
        s = max((x1 - x0) / (1 - ml - mr), (y1 - y0) / (1 - mb - mt))
        D *= s
    cam.location = target + d * D
    B.look_at(cam, target)
    x0, x1, y0, y1 = ndc()
    dx = (x0 + x1) / 2 - (ml + (1 - ml - mr) / 2)
    if anchor_bottom:
        dy = y0 - mb
    else:
        dy = (y0 + y1) / 2 - (mb + (1 - mb - mt) / 2)
    big = max(rx, ry)
    cam.data.shift_x = dx * rx / big
    cam.data.shift_y = dy * ry / big
    return cam


def render_rgba(name):
    path = os.path.join(_TMP, name + ".png")
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    return load_rgba(path)


# ---------------------------------------------------------------------------
# 1. Chef skins (busts)
# ---------------------------------------------------------------------------
BUST_POSE = {"head": (-4, 5, 0), "armL": (-6, 8, 0), "armR": (-6, -8, 0)}


def render_skin(sid, sp, hat, apron):
    new_scene()
    setup((512, 512), samples=96)
    ch = Char(sp, hat=hat, apron=apron, pose=BUST_POSE)
    smoothen(ch.objs())
    lights(target=(0, 0, 0.9))
    camera_fit(ch.objs(), (0.40, -1.0, -0.16), lens=70, margins=(0.08, 0.08, 0.0, 0.04), z_cut=0.55,
               anchor_bottom=True)
    save_png(os.path.join(UI_DIR, "skin_%s.png" % sid), render_rgba("skin_" + sid))


# ---------------------------------------------------------------------------
# 2. Pets
# ---------------------------------------------------------------------------
def render_pet(name):
    new_scene()
    setup((512, 512), samples=96)
    parts, _ = PT.build_pet(name)
    parts[0].rotation_euler.z = math.radians(-8)
    smoothen(parts)
    contact_shadow(parts, grow=1.35, zmax=0.06, opacity=0.32, min_r=0.10)
    lights(target=(0, 0, 0.2), rim_scale=0.6)
    camera_fit(parts, (0.62, -1.0, 0.42), lens=70, margins=(0.09, 0.09, 0.08, 0.08))
    save_png(os.path.join(UI_DIR, "pet_%s.png" % name), render_rgba("pet_" + name))


def render_pet_none():
    new_scene()
    setup((512, 512), samples=64)
    P = C.Part("none")
    disc = B.mat("#FDEBD8", rough=0.8)
    ring = B.mat("#F6D2B8", rough=0.8)
    pawm = B.mat("#F3B9A6", rough=0.7)
    H.lathe(P, [(0.0, 0.0, disc), (0.40, 0.0, ring), (0.43, 0.012, ring), (0.43, 0.040, ring), (0.40, 0.052, disc),
                (0.36, 0.044, disc), (0.0, 0.044, disc)], seg=48)
    P.ellipsoid((0.0, -0.045, 0.044), (0.120, 0.100, 0.030), pawm, n=4, zmin=0.044)
    for x, y, r in ((-0.125, 0.065, 0.048), (-0.048, 0.135, 0.050), (0.048, 0.135, 0.050), (0.125, 0.065, 0.048)):
        P.ellipsoid((x, y, 0.044), (r, r * 1.15, 0.026), pawm, n=3, zmin=0.044,
                    rot=C.rotz(math.degrees(math.atan2(-x, y)) * 0.6))
    o = P.finish((0, 0, 0))
    smoothen([o])
    o.rotation_euler = (math.radians(62), 0, 0)
    lights(target=(0, 0, 0), world=0.9, key=1.8, rim=0.4, key_dir=(0.3, -1.0, 0.8))
    camera_fit([o], (0.0, -1.0, 0.0), lens=85, margins=(0.08, 0.08, 0.08, 0.08))
    save_png(os.path.join(UI_DIR, "pet_none.png"), render_rgba("pet_none"))


# ---------------------------------------------------------------------------
# 3. Guest cards (head only)
# ---------------------------------------------------------------------------
def render_card(sp):
    new_scene()
    setup((256, 256), samples=80)
    ch = Char(sp, pose={"head": (0, 4, 0)}, head_only=True)
    smoothen(ch.objs())
    lights(target=(0, 0, 0.85))
    camera_fit(ch.objs(), (0.34, -1.0, 0.12), lens=70, margins=(0.07, 0.07, 0.07, 0.06))
    save_png(os.path.join(UI_DIR, "card_guest_%s.png" % sp), render_rgba("card_" + sp))


# ---------------------------------------------------------------------------
# 4. Onboarding vignettes
# ---------------------------------------------------------------------------
PALETTE_CONFETTI = ["mustard", "tomato", "mint", "teal", "pink", "lilac", "sky", "white"]


def confetti(center, spread, n=40, seed=3, zmin=0.25):
    rnd = random.Random(seed)
    out = []
    for i in range(n):
        p = V(center) + V((rnd.uniform(-1, 1) * spread[0], rnd.uniform(-1, 1) * spread[1],
                           rnd.uniform(0, 1) * spread[2]))
        p.z = max(p.z, zmin)
        m = B.mat(PALETTE_CONFETTI[i % len(PALETTE_CONFETTI)], rough=0.5)
        if i % 3 == 0:
            o = B.sphere(0.016, loc=tuple(p), m=m, seg=8, rings=5, name="conf")
        else:
            o = B.box((0.055, 0.026, 0.006), loc=tuple(p), m=m, bevel=0.003, segs=1, name="conf",
                      rot=(rnd.uniform(0, 360), rnd.uniform(0, 360), rnd.uniform(0, 360)))
        out.append(o)
    return out


def rbox(size, loc, m, r=0.03, rot=None):
    if KIT is not None:
        return KIT.rbox(size, loc=loc, m=m, r=r, segs=2, rot=rot)
    return B.box(size, loc=loc, m=m, bevel=r, segs=2, rot=rot)


def stack_of_burgers(base, n=7, scale=0.82, seed=5):
    rnd = random.Random(seed)
    objs = []
    z = base.z
    hgt = burger_height() * scale
    for i in range(n):
        wob = math.sin(i * 1.1) * 0.016 + 0.0016 * i * i
        o = burger((base.x + wob, base.y + rnd.uniform(-0.012, 0.012), z), scale,
                   rot=(rnd.uniform(-5, 5), rnd.uniform(-4, 6) + i * 1.6, rnd.uniform(0, 360)))
        objs.append(o)
        z += hgt * 0.98
    return objs


def onboard_1():
    new_scene()
    setup((1024, 1024), samples=128)
    ch = Char("corgi", hat="toque", apron="#FFFFFF",
              pose={"armL": ("aim", (0.62, -0.28, 0.75)), "armR": ("aim", (-0.45, -0.12, -0.9)),
                    "head": (-8, -7, 6)}, rotz=-16)
    b = burger((-0.66, 0.12, 0.0), 2.6, rot=(0, 0, 25))
    smoothen(ch.objs() + [b])
    contact_shadow(ch.objs())
    contact_shadow([b], grow=1.12, zmax=0.05, opacity=0.28)
    lights(target=(0, 0, 0.6))
    camera_fit(ch.objs() + [b], (0.45, -1.0, 0.30), lens=55, margins=(0.08, 0.08, 0.08, 0.07))
    save_png(os.path.join(UI_DIR, "onboard_1.png"), render_rgba("onboard_1"))


def onboard_2():
    new_scene()
    setup((1024, 1024), samples=128)
    ch = Char("corgi", hat="toque", apron="#FFFFFF",
              pose={"armL": (-62, -62, 0), "armR": (-30, 25, 0), "head": (-4, 4, 6)}, rotz=-8)
    paw = ch.hand("armL")
    stack = stack_of_burgers(V((paw.x + 0.02, paw.y - 0.01, paw.z - 0.01)), n=7)
    custom = []
    for i, (sp, tint, arm) in enumerate((("cat", C.PREVIEW_TINT["cat"], {"armL": (0, -120, 0)}),
                                         ("bunny", C.PREVIEW_TINT["bunny"], {"armR": (0, 40, 0)}),
                                         ("bear", C.PREVIEW_TINT["bear"], {"armL": (0, -35, 0)}))):
        pose = dict(arm)
        pose["head"] = (-5, (-1) ** i * 5, 0)
        c = Char(sp, apron=B.PAL.get(tint, tint) if isinstance(tint, str) else tint,
                 loc=(-0.88 - 0.72 * i, 0.42 + 0.36 * i, 0), rotz=-24, pose=pose)
        custom += c.objs()
        contact_shadow(c.objs())
    contact_shadow(ch.objs())
    smoothen(ch.objs() + stack + custom)
    lights(target=(-0.8, 0.6, 0.7), rim_scale=1.6)
    camera_fit(ch.objs() + stack + custom, (0.42, -1.0, 0.34), lens=50, margins=(0.06, 0.06, 0.07, 0.05))
    save_png(os.path.join(UI_DIR, "onboard_2.png"), render_rgba("onboard_2"))


def simple_grill(loc):
    objs = []
    x, y, z = loc
    objs.append(rbox((1.0, 0.62, 0.78), (x, y, z + 0.39), B.mat("steel"), r=0.05))
    objs.append(rbox((1.04, 0.66, 0.06), (x, y, z + 0.80), B.mat("steel_dark"), r=0.02))
    objs.append(rbox((0.80, 0.48, 0.02), (x, y, z + 0.835), B.mat("#3A3742"), r=0.01))
    objs.append(rbox((1.02, 0.05, 0.16), (x, y - 0.315, z + 0.62), B.mat("tomato"), r=0.02))
    for i in range(3):
        objs.append(B.cyl(0.035, 0.04, loc=(x - 0.25 + 0.25 * i, y - 0.34, z + 0.45), m=B.mat("cocoa"),
                          verts=12, rot=(90, 0, 0)))
    return objs


def onboard_3():
    new_scene()
    setup((1024, 1024), samples=128)
    ch = Char("corgi", hat="toque", apron="#FFFFFF",
              pose={"armL": ("aim", (0.85, -0.45, -0.05)), "armR": (6, 22, 0), "head": (-6, -8, 10)},
              loc=(0.0, -0.28, 0), rotz=-18)
    props = []
    if ST is not None:
        try:
            g = ST.build_st_grill()[0]
            for a in [x for x in bpy.data.objects if x.name.startswith("@")]:
                bpy.data.objects.remove(a)
            g.location = (-1.05, 0.95, 0)
            g.rotation_euler.z = math.radians(8)
            g.scale = (0.8, 0.8, 0.8)
            props.append(g)
        except Exception as e:
            print("render_ui: grill fallback:", e)
            props += simple_grill((-1.05, 0.95, 0))
    else:
        props += simple_grill((-1.05, 0.95, 0))
    # small counter: wood top, cream front, tomato trim, a register block
    cx, cy = 0.55, 1.05
    props.append(rbox((1.10, 0.58, 0.08), (cx, cy, 0.04), B.mat("cocoa"), r=0.02))
    props.append(rbox((1.14, 0.62, 0.74), (cx, cy, 0.45), B.mat("cream"), r=0.05))
    props.append(rbox((1.16, 0.64, 0.10), (cx, cy, 0.66), B.mat("tomato"), r=0.03))
    props.append(rbox((1.22, 0.70, 0.07), (cx, cy, 0.855), B.mat("wood"), r=0.03))
    props.append(rbox((0.26, 0.22, 0.18), (cx + 0.32, cy + 0.05, 0.98), B.mat("steel_dark"), r=0.04))
    props.append(rbox((0.22, 0.03, 0.12), (cx + 0.32, cy - 0.07, 1.03), B.mat("glass", emit=0.5), r=0.015,
                      rot=(-15, 0, 0)))
    props.append(burger((cx - 0.25, cy - 0.05, 0.89), 0.9, rot=(0, 0, 30)))
    props.append(burger((cx - 0.05, cy + 0.08, 0.89), 0.9, rot=(0, 0, 70)))
    # glowing mustard build pad
    pad = None
    if ST is not None:
        try:
            pad = ST.pad_base(0.65)
        except Exception as e:
            print("render_ui: pad fallback:", e)
    if pad is None:
        P = C.Part("pad")
        H.lathe(P, [(0.0, 0.0, B.mat("white")), (0.65, 0.0, B.mat("white")), (0.65, 0.04, B.mat("offwhite")),
                    (0.55, 0.05, B.mat("white", tint=True)), (0.0, 0.045, B.mat("white", tint=True))], seg=32)
        pad = P.finish((0, 0, 0))
    retint([pad], "#FFAE00", emit=0.18)
    # thin glowing rim inside the plate edge
    R = 0.49 * 0.80          # in the groove between the rim and the inner plate
    ring = C.Part("padring")
    H.loop_tube(ring, lambda u: (R * math.cos(6.2831853 * u), R * math.sin(6.2831853 * u), 0.0), 48, 6,
                lambda u: 0.012, lambda u: B.mat("#FFE27A", emit=2.5))
    pad.location = (0.80, -0.62, 0.0)
    pad.scale = (0.80, 0.80, 0.80)
    ro = ring.finish((0, 0, 0))
    ro.location = (0.80, -0.62, 0.024)
    # dashed progress ring on the plate rim, like the in-game build pad
    for k in range(16):
        a = 6.2831853 * k / 16
        rr = 0.62 * 0.80
        props.append(B.box((0.07, 0.022, 0.012), loc=(0.80 + rr * math.cos(a), -0.62 + rr * math.sin(a), 0.047),
                           m=B.mat("#FFC23C", emit=0.8), bevel=0.006, segs=2, rot=(0, 0, math.degrees(a) + 90)))
    props.append(pad)
    gl = bpy.data.lights.new("PadGlow", 'POINT')
    gl.energy = 60
    gl.color = (1.0, 0.78, 0.25)
    gl.shadow_soft_size = 0.4
    go = bpy.data.objects.new("PadGlow", gl)
    bpy.context.scene.collection.objects.link(go)
    go.location = (0.80, -0.62, 0.30)
    conf = confetti((0.45, -0.35, 0.75), (0.85, 0.50, 0.95), n=52, seed=11)
    smoothen(ch.objs() + [o for o in props if o.name.startswith("item")])
    contact_shadow(ch.objs())
    for grp in ([o for o in props if o is not pad and (o.location - V((cx, cy, 0))).length < 1.0],
                [o for o in props if (o.location - V((-1.05, 0.95, 0))).length < 0.5]):
        if grp:
            contact_shadow(grp, grow=1.12, zmax=0.08, opacity=0.26)
    lights(target=(0, 0.3, 0.6), rim_scale=1.4)
    camera_fit(ch.objs() + props + [pad], (0.36, -1.0, 0.40), lens=50, margins=(0.05, 0.05, 0.05, 0.05),
               include=[o.location for o in conf])
    save_png(os.path.join(UI_DIR, "onboard_3.png"), render_rgba("onboard_3"))


def rocket(loc, rot, scale=1.0):
    P = C.Part("rocket")
    white, red, teal = B.mat("white", rough=0.4), B.mat("tomato", rough=0.4), B.mat("teal", rough=0.3)
    H.lathe(P, [(0.0, -0.20, red), (0.06, -0.20, red), (0.085, -0.15, white), (0.095, -0.02, white),
                (0.085, 0.10, red), (0.055, 0.19, red), (0.0, 0.25, red)], seg=16)
    for k in range(3):
        a = math.radians(90 + 120 * k)
        d = V((math.cos(a), math.sin(a), 0))
        P.sweep([d * 0.05 + V((0, 0, -0.10)), d * 0.12 + V((0, 0, -0.17)), d * 0.13 + V((0, 0, -0.22))],
                [0.035, 0.022, 0.0], red, seg=6, scales=[(0.3, 1.0)] * 3, hint=V((-d.y, d.x, 0)))
    P.ellipsoid((0, -0.088, 0.03), (0.035, 0.012, 0.035), teal, uv=(12, 5))
    P.sweep([(0, 0, -0.20), (0, 0, -0.24), (0, 0, -0.33)], [0.05, 0.04, 0.0], B.mat("orange", emit=3.0), seg=10)
    o = P.finish((0, 0, 0))
    o.location = V(loc)
    o.rotation_euler = [math.radians(a) for a in rot]
    o.scale = (scale, scale, scale)
    return o


def globe(R=0.62, seed=21):
    P = C.Part("globe")
    host = C.Ell((0, 0, -R), (R, R, R))
    rnd = random.Random(seed)
    lands = [B.mat("#8BD45C", rough=0.7), B.mat("#3DDC97", rough=0.7)]
    for i in range(7):
        az, el = rnd.uniform(0, 360), rnd.uniform(-45, 40)
        base = V((math.cos(math.radians(el)) * math.cos(math.radians(az)),
                  math.cos(math.radians(el)) * math.sin(math.radians(az)), math.sin(math.radians(el))))
        for k in range(3):
            d = (base + V((rnd.uniform(-0.18, 0.18), rnd.uniform(-0.18, 0.18), rnd.uniform(-0.12, 0.12)))).normalized()
            if d.z > 0.80:
                continue
            a = rnd.uniform(0.13, 0.26) * R
            C.decal(P, host, 0, 0, a, a * rnd.uniform(0.55, 0.9), lands[i % 2], h=0.016, seg=16,
                    roll=rnd.uniform(0, 180), center=host.surf(d), rho=(0.62, 0.95, 1.0))
    P.ellipsoid(host.c, host.r, B.mat("#6EC6F5", rough=0.35), n=12)
    return P.finish((0, 0, 0))


def stars(points, m):
    out = []
    for p, s, rz in points:
        if KIT is not None:
            o = KIT.prism(KIT.star_pts(5, 0.5, 0.24), 0.18, m, bevel=0.05, segs=2, rot=(90, 0, rz))
            o.location = V(p)
            o.scale = (s, s, s)
        else:
            o = B.sphere(s * 0.4, loc=p, m=m, seg=10, rings=6)
        out.append(o)
    return out


def onboard_4():
    new_scene()
    setup((1024, 1024), samples=128)
    g = globe()
    ch = Char("corgi", hat="helmet", apron="#E8F4FF",
              pose={"armL": ("aim", (0.18, -0.22, 1.0)), "armR": (-10, 55, 0), "head": (-12, -6, 0),
                    "legR": (16, 0, 0), "legL": (-6, 0, 0)}, rotz=-10)
    extras = [rocket((-0.62, -0.05, 1.12), (20, -38, 0), 1.15)]
    extras += [burger((0.62, -0.15, 0.92), 0.95, rot=(18, -22, 40)), burger((-0.72, 0.15, 0.25), 0.85, rot=(-15, 30, 0)),
               burger((0.50, 0.20, 1.45), 0.75, rot=(25, 12, 0))]
    extras += stars([((0.22, -0.2, 1.55), 0.11, 10), ((-0.30, -0.1, 1.62), 0.08, -15), ((0.78, 0.0, 0.35), 0.09, 20),
                     ((-0.80, 0.0, 0.72), 0.07, 5)], B.mat("gold", rough=0.3, emit=0.4))
    smoothen(ch.objs() + [g] + [o for o in extras if not o.name.startswith("prism")])
    lights(target=(0, 0, 0.5), rim_scale=1.4)
    camera_fit(ch.objs() + [g] + extras, (0.38, -1.0, 0.25), lens=50, margins=(0.06, 0.06, 0.05, 0.05))
    save_png(os.path.join(UI_DIR, "onboard_4.png"), render_rgba("onboard_4"))


# ---------------------------------------------------------------------------
# 5. App icon + feature graphic
# ---------------------------------------------------------------------------
# Patty holds the burger up in front of her chest, a little toward the camera side;
# smaller toque so the face dominates and both ears peek out beside it.
ICON_POSE = {"armL": ("aim", (0.07, -0.30, -0.08)), "armR": ("aim", (0.05, -0.30, -0.08)), "head": (-4, 4, 7)}
ICON_CAM = (0.25, -1.0, 0.02)


def icon_scene():
    ch = Char("corgi", hat="toque", apron="#FFFFFF", pose=ICON_POSE, hat_scale=0.74,
              hat_offset=(0.0, 0.01, 0.028, -5.0))
    b = burger((0.05, -0.37, 0.395), 1.35, rot=(-16, 5, 25))
    smoothen(ch.objs() + [b])
    return ch, b


def circle_fit(cam, pts, radius=0.30, iters=4):
    """Scale/centre the framing so every point lies within `radius` (frame fraction)
    of the image centre: the Android adaptive-icon safe zone is a 66 % circle."""
    scene = bpy.context.scene
    bpy.context.view_layer.update()
    fwd = cam.matrix_world.to_3x3() @ V((0, 0, -1))
    cen = sum(pts, V((0, 0, 0))) / len(pts)
    tgt = cam.location + fwd * (cen - cam.location).dot(fwd)
    for _ in range(iters):
        bpy.context.view_layer.update()
        cs = [world_to_camera_view(scene, cam, p) for p in pts]
        x0, x1 = min(c.x for c in cs), max(c.x for c in cs)
        y0, y1 = min(c.y for c in cs), max(c.y for c in cs)
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        cam.data.shift_x += cx - 0.5
        cam.data.shift_y += cy - 0.5
        bpy.context.view_layer.update()
        cs = [world_to_camera_view(scene, cam, p) for p in pts]
        r = max(math.hypot(c.x - 0.5, c.y - 0.5) for c in cs)
        k = r / radius
        d = cam.location - tgt
        cam.location = tgt + d * k
        cam.data.shift_x /= k
        cam.data.shift_y /= k
    bpy.context.view_layer.update()
    cs = [world_to_camera_view(scene, cam, p) for p in pts]
    return max(math.hypot(c.x - 0.5, c.y - 0.5) for c in cs)


def render_icon():
    os.makedirs(ICON_DIR, exist_ok=True)
    bg = sunburst(1024, 1024, center=(0.5, 0.47), rays=16)
    save_png(os.path.join(ICON_DIR, "icon_bg.png"), np.clip(np.round(bg * 255), 0, 255).astype(np.uint8))
    # full icon: big bust, whole face + whole burger, body runs off the bottom edge
    new_scene()
    setup((1024, 1024), samples=160)
    ch, b = icon_scene()
    lights(target=(0, -0.1, 0.80))
    camera_fit(ch.objs() + [b], ICON_CAM, lens=70, margins=(0.07, 0.07, 0.035, 0.05), z_cut=0.37,
               anchor_bottom=True)
    fg_full = render_rgba("icon_full_fg")
    save_png(os.path.join(ICON_DIR, "icon_full.png"), over(fg_full, bg))
    # adaptive foreground: head, ears, toque and burger inside the central 66 % circle;
    # the body continues below the visible mask so no cut edge shows.
    new_scene()
    setup((1024, 1024), samples=160)
    ch, b = icon_scene()
    lights(target=(0, -0.1, 0.80))
    cam = camera_fit(ch.objs() + [b], ICON_CAM, lens=70, margins=(0.2, 0.2, 0.2, 0.2), z_cut=0.40)
    pts = [o.matrix_world @ v.co for o in meshes(ch.objs() + [b]) for v in o.data.vertices]
    pts = [p for p in pts if p.z >= 0.40 or p.y < -0.12 and p.z >= 0.37]
    r = circle_fit(cam, pts, radius=0.315)
    print("icon_fg content radius = %.3f of the canvas (safe zone 0.33)" % r)
    save_png(os.path.join(ICON_DIR, "icon_fg.png"), render_rgba("icon_fg"))


def title_text(text, loc, size, face, edge, rot=(90, 0, 0)):
    objs = []
    font = bpy.data.fonts.load(FONT_TITLE) if os.path.exists(FONT_TITLE) else None
    # face text in front, a fatter cocoa copy pushed well behind it as the outline
    for k, (mat, extrude, offset, dy) in enumerate(((face, 0.020, 0.0, 0.0), (edge, 0.030, 0.034, 0.075))):
        cu = bpy.data.curves.new("title%d" % k, 'FONT')
        cu.body = text
        if font:
            cu.font = font
        cu.align_x = 'CENTER'
        cu.align_y = 'CENTER'
        cu.size = size
        cu.space_line = 0.86
        cu.extrude = extrude
        cu.offset = offset
        cu.bevel_depth = 0.008 + offset * 0.3
        cu.bevel_resolution = 3
        cu.resolution_u = 10
        o = bpy.data.objects.new("title%d" % k, cu)
        bpy.context.scene.collection.objects.link(o)
        o.data.materials.append(mat)
        o.location = V(loc) + V((0, dy, 0))
        o.rotation_euler = [math.radians(a) for a in rot]
        objs.append(o)
    return objs


def render_feature():
    W, Hh = 1024, 500
    new_scene()
    setup((W, Hh), samples=128)
    cast = [("corgi", "toque", "#FFFFFF", {"armL": (0, -150, 10), "head": (-6, -6, 0)}),
            ("cat", None, "#F2542D", {"armR": (-70, 20, 0), "head": (-4, 6, 0)}),
            ("bunny", None, "#B79CFF", {"armL": (0, -45, 0), "head": (-4, -5, 0)}),
            ("bear", None, "#3DDC97", {"armR": (-75, 25, 0), "head": (-4, 6, 0)})]
    objs = []
    chars = []
    for i, (sp, hat, apron, pose) in enumerate(cast):
        c = Char(sp, hat=hat, apron=apron, pose=pose, loc=(0.72 + 0.66 * i, 0.10 * i, 0), rotz=-6)
        chars.append(c)
        objs += c.objs()
    for c in (chars[1], chars[3]):
        p = c.hand("armR")
        objs.append(burger((p.x, p.y - 0.03, p.z - 0.02), 0.85, rot=(-12, 0, 20)))
    objs.append(burger((0.50, -0.45, 0.0), 1.5, rot=(0, 0, 30)))
    objs.append(burger((3.05, -0.38, 0.0), 1.25, rot=(0, 0, -20)))
    title = []
    if os.path.exists(FONT_TITLE):
        face = B.mat("#FFF3DC", rough=0.35)
        edge = B.mat("#3B2A3A", rough=0.5)
        title += title_text("BUN", (-0.80, -0.35, 1.12), 0.60, B.mat("#FFC23C", rough=0.35), edge)
        title += title_text("VOYAGE!", (-0.80, -0.35, 0.56), 0.50, face, edge)
    smoothen(objs)
    for c in chars:
        contact_shadow(c.objs())
    for b in objs[-2:]:
        contact_shadow([b], grow=1.12, zmax=0.05, opacity=0.26)
    lights(target=(1.5, 0, 0.6), rim_scale=2.0)
    bpy.context.view_layer.update()
    tb = [o.matrix_world @ V(c) for o in title for c in o.bound_box]
    camera_fit(objs, (0.10, -1.0, 0.16), lens=50, margins=(0.04, 0.03, 0.05, 0.06), include=tb)
    fg = render_rgba("feature")
    bg = sunburst(W, Hh, center=(0.66, 0.42), rays=18, radius=0.62 * W)
    save_png(os.path.join(ICON_DIR, "feature_graphic.png"), over(fg, bg))
    return bool(title)


# ---------------------------------------------------------------------------
def main():
    global FAST
    args = B.script_args()
    FAST = "--fast" in args
    groups = [a for a in args if not a.startswith("--")] or ["skins", "pets", "cards", "onboard", "icon"]
    only = [a.split("=", 1)[1] for a in args if a.startswith("--only=")]
    os.makedirs(UI_DIR, exist_ok=True)
    if "skins" in groups:
        for sid, sp, hat, apron in SKINS:
            if not only or sid in only:
                render_skin(sid, sp, hat, apron)
    if "pets" in groups:
        for n in PT.PETS:
            if not only or n in only:
                render_pet(n)
        if not only or "none" in only:
            render_pet_none()
    if "cards" in groups:
        for sp in C.SPECIES_ORDER:
            if not only or sp in only:
                render_card(sp)
    if "onboard" in groups:
        for i, fn in enumerate((onboard_1, onboard_2, onboard_3, onboard_4), 1):
            if not only or str(i) in only:
                fn()
    if "icon" in groups:
        if not only or "icon" in only:
            render_icon()
        if not only or "feature" in only:
            print("feature graphic title:", render_feature())
    shutil.rmtree(_TMP, ignore_errors=True)


if __name__ == "__main__":
    main()
