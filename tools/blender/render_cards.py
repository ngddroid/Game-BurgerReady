"""
HD UI card art rendered from the game's own models (Cycles, transparent PNG).

    Blender -b --factory-startup --python tools/blender/render_cards.py [-- <filter> ...]

Filters match output names by prefix, e.g. `store_`, `theme_neon`, `upgrade_`,
`card_food_burger`. Outputs go to app/src/main/assets/ui/:

    store_<id>.png      640x512  diorama per Store (floor, walls, counter, station, 3 signature props, env)
    theme_<id>.png      512x512  room corner per Interior Theme (tinted floor/walls/seats)
    upgrade_<id>.png    256x256  hero icon objects
    card_food_<id>.png  256x256  3/4 hero shot of a food item

Look: same warm studio light as the model previews, AO baked into the
materials (Cycles bake), a shadow catcher for a soft contact shadow on the
transparent background. Files are kept under ~400 KB.
"""
import os
import sys
import math
import random
import subprocess

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from kit import M, T, E, B, lathe, prism, tube, rbox, rcyl, ball, join, move  # noqa: E402
import food  # noqa: E402
import stations  # noqa: E402
import building  # noqa: E402
import env  # noqa: E402
import stores  # noqa: E402
from mathutils import Vector, Matrix, Euler  # noqa: E402

UI_DIR = os.path.join(B.ROOT, "app", "src", "main", "assets", "ui")
MAX_BYTES = 400 * 1024
TAU = 2 * math.pi


# ---------------------------------------------------------------------------
# Scene helpers
# ---------------------------------------------------------------------------
def place(fn, loc=(0, 0, 0), rotz=0.0, scale=1.0, rot=None):
    """Build a model into the current scene, group it and move/rotate/scale the group."""
    before = set(bpy.data.objects)
    fn()
    new = [o for o in bpy.data.objects if o not in before]
    bpy.context.view_layer.update()
    grp = bpy.data.objects.new("grp", None)
    bpy.context.collection.objects.link(grp)
    for o in new:
        if o.parent is None:
            B.parent_to(o, grp)
    grp.location = loc
    grp.rotation_euler = [math.radians(a) for a in rot] if rot else (0, 0, math.radians(rotz))
    grp.scale = (scale, scale, scale)
    bpy.context.view_layer.update()
    return new


def meshes(objs):
    return [o for o in objs if o.type == 'MESH']


def anchors_of(objs):
    out = {}
    for o in objs:
        if o.name.startswith("@"):
            out[o.name[1:].split(".")[0]] = o.matrix_world.translation.copy()
    return out


def dims_of(fn):
    """Bounding box size of a model (built in a throwaway pass)."""
    objs = place(fn)
    lo, hi = kit.bbox(meshes(objs))
    for o in objs:
        bpy.data.objects.remove(o, do_unlink=True)
    return hi - lo


def fit_scale(fn, fit=None, hcap=None):
    if fit is None and hcap is None:
        return 1.0
    d = dims_of(fn)
    s = 1.0
    if fit:
        s = min(s, fit / max(d.x, d.y))
    if hcap:
        s = min(s, hcap / d.z)
    return s


def hexcol(m):
    return "%02X%02X%02X" % tuple(int(round(v * 255)) for v in m.diffuse_color[:3])


def hexkey(c):
    return "%02X%02X%02X" % tuple(int(round(v * 255)) for v in B.color(c))


def remap(objs, tint=None, colors=None):
    """Recolour placed objects: tint-masked faces -> `tint`; fixed colours by palette map."""
    cmap = {hexkey(k): v for k, v in (colors or {}).items()}
    for o in meshes(objs):
        for i, m in enumerate(o.data.materials):
            if m is None:
                continue
            emit = float(m.get("emit", 0.0))
            if int(m.get("tint", 0)):
                if tint:
                    o.data.materials[i] = B.mat(tint, tint=True)
                continue
            h = hexcol(m)
            if h in cmap:
                o.data.materials[i] = B.mat(cmap[h], emit=emit)


def multiply(objs, color):
    """Multiply every fixed material colour by `color` (in-game floor tint: white = natural)."""
    tr, tg, tb = B.color(color)
    for o in meshes(objs):
        for i, m in enumerate(o.data.materials):
            if m is None:
                continue
            r, g, b = m.diffuse_color[:3]
            hx = "#%02X%02X%02X" % (int(round(r * tr * 255)), int(round(g * tg * 255)), int(round(b * tb * 255)))
            o.data.materials[i] = B.mat(hx, tint=bool(int(m.get("tint", 0))), emit=float(m.get("emit", 0.0)))


def glass_mat(col="#CFF3FF"):
    m = bpy.data.materials.new("GlassCard")
    if m.node_tree is None:
        m.use_nodes = True
    bs = m.node_tree.nodes.get("Principled BSDF")
    lin = [B.srgb_to_linear(v) for v in B.color(col)]
    bs.inputs["Base Color"].default_value = (*lin, 1.0)
    bs.inputs["Roughness"].default_value = 0.08
    bs.inputs["IOR"].default_value = 1.35
    bs.inputs["Transmission Weight"].default_value = 1.0
    m.diffuse_color = (*B.color(col), 1.0)
    m["tint"] = 0
    m["emit"] = 0.0
    return m


def slab(w, d, top, side, cx=0.0, cy=0.0, ztop=-0.042, h=0.30, r=0.45):
    """Rounded diorama base: a thin ground-coloured top layer over a darker body."""
    body = B.rounded_rect_prism(w, d, h - 0.07, r, loc=(cx, cy, ztop - h), m=M(side), seg=6, bevel=0.05)
    kit.finish(body, angle=40)
    lay = B.rounded_rect_prism(w + 0.04, d + 0.04, 0.08, r + 0.02, loc=(cx, cy, ztop - 0.08), m=M(top), seg=6,
                               bevel=0.035)
    kit.finish(lay, angle=40)
    return [body, lay]


def finalize_and_render(path, res, yaw=38, pitch=33, margin=1.07, ao_dist=0.35, ao_strength=0.75, samples=128,
                        world=0.42, sun=2.8, offset=(0.0, 0.0), post=None):
    objs = meshes(list(bpy.data.objects))
    # AO bake (vertex colours) -> multiplied into the materials, like the model previews
    B.bake_ao(objs, samples=32, distance=ao_dist)
    kit.ao_preview_materials(objs, strength=ao_strength, tint_color=None)
    if post:  # objects that must not occlude the AO bake (e.g. clear glass)
        post()
        objs = meshes(list(bpy.data.objects))
    lo, hi = kit.bbox(objs)
    B.setup_render(res=res, transparent=True, samples=samples)
    scene = bpy.context.scene
    scene.render.image_settings.compression = 100
    kit.light_rig((lo + hi) / 2, (hi - lo).length, sun=sun, world=world)
    # soft contact shadow on the transparent background
    bpy.ops.mesh.primitive_plane_add(size=200, location=(0, 0, lo.z + 0.0005))
    catcher = bpy.context.active_object
    catcher.is_shadow_catcher = True
    fit_camera(objs, res, yaw, pitch, margin, offset)
    os.makedirs(UI_DIR, exist_ok=True)
    B.render(path)
    shrink(path)
    print("CARD %s %d bytes" % (os.path.relpath(path, B.ROOT), os.path.getsize(path)))


def fit_camera(objs, res, yaw, pitch, margin, offset=(0.0, 0.0)):
    yr, pr = math.radians(yaw), math.radians(pitch)
    cdir = Vector((math.sin(yr) * math.cos(pr), -math.cos(yr) * math.cos(pr), math.sin(pr)))
    f = -cdir
    right = f.cross(Vector((0, 0, 1))).normalized()
    up = right.cross(f).normalized()
    xs, ys, pts = [], [], []
    for o in objs:
        mw = o.matrix_world
        for v in o.data.vertices:
            p = mw @ v.co
            pts.append(p)
            xs.append(p.dot(right))
            ys.append(p.dot(up))
    cx = (max(xs) + min(xs)) / 2 + offset[0]
    cy = (max(ys) + min(ys)) / 2 + offset[1]
    center = sum(pts, Vector()) / len(pts)
    depth = center.dot(f)
    tgt = right * cx + up * cy + f * depth
    aspect = res[0] / res[1]
    span = max(max(xs) - min(xs), (max(ys) - min(ys)) * aspect) * margin
    dist = 60.0
    cam = B.camera(tuple(tgt + cdir * dist), tuple(tgt), ortho=span)
    cam.data.clip_end = 400
    return cam


def shrink(path):
    """Keep files under MAX_BYTES: quantise (RGBA, 256 colours) with system Python/PIL if needed."""
    if os.path.getsize(path) <= MAX_BYTES:
        return
    code = ("import sys\nfrom PIL import Image\nim=Image.open(sys.argv[1]).convert('RGBA')\n"
            "q=im.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.FLOYDSTEINBERG)\n"
            "q.save(sys.argv[1], optimize=True)\n")
    try:
        subprocess.run(["python3", "-c", code, path], check=True, timeout=120)
    except Exception as e:
        print("shrink failed for", path, e)


# ---------------------------------------------------------------------------
# 1. Store dioramas
# ---------------------------------------------------------------------------
def _st(name):
    return getattr(stations, "build_" + name)


def _env(name):
    return env.MODELS[name][0]


def _sig(store, prop):
    return getattr(stores, "build_%s_%s" % (store, prop))


FLOOR = {"floor_wood": building.build_floor_wood, "floor_checker": building.build_floor_checker,
         "floor_tile": building.build_floor_tile, "floor_terrazzo": building.build_floor_terrazzo}

GREEN_TO_CANDY = {"grass": "#FFB3D1", "lettuce": "#FF8FB1", "#A2E070": "#FFD0E4", "#4FAE3A": "#C8B6FF",
                  "lettuce_dark": "#B79CFF", "#2E8B57": "#B79CFF"}
GREEN_TO_SEA = {"grass": "#3DDC97", "lettuce": "#1FA6B8", "#A2E070": "#7FE3C7", "lettuce_dark": "#13808F",
                "brown": "#FF9A3C"}

# id: (wall, trim, accent), floor (model, tint), slab (top, side), station, props, env
# props/env entries: (builder, x, y, rotz, fit, hcap[, recolour map][, z])
STORE_SPECS = {
    "wagon": dict(colors=("#FFF3DC", "#F2542D", "#FFC23C"), floor=("floor_wood", None),
                  slab=("#8BD45C", "#7A5236"), station="st_grill",
                  props=[("truck", 2.35, 0.45, 90, 2.4, None), ("balloon_stand", 1.55, -1.75, 0, None, 1.9),
                         ("picnic_table", -1.05, -1.85, 0, 1.25, None)],
                  env=[("tree_round", -1.95, 2.0, 0, None, 2.2), ("bush", 2.6, -1.55, 0, 0.7, None)]),
    "seaside": dict(colors=("#E9FBFF", "#1FA6B8", "#3DDC97"), floor=("floor_wood", None),
                    slab=("#F3D9A4", "#D6B57A"), station="st_soda",
                    props=[("lifeguard_tower", 2.25, 0.75, 25, None, 2.4), ("beach_chair", 1.75, -1.65, 20, 0.9, None),
                           ("surfboard", -1.15, -1.85, 0, None, 1.5)],
                    env=[("palm", -1.95, 1.95, 0, None, 2.5), ("umbrella", 2.75, -0.95, 0, None, 1.8)]),
    "diner": dict(colors=("#FFE9F1", "#E94B3C", "#3DDC97"), floor=("floor_checker", "#E94B3C"),
                  slab=("#4A4E5A", "#33363F"), station="st_shake",
                  props=[("jukebox", 2.15, 0.95, 30, None, 1.6), ("stool", 1.55, -1.6, 0, None, 0.85),
                         ("neon_star", -1.15, -1.95, 0, None, 1.6)],
                  env=[("neon_burger", 2.45, -0.55, 40, None, 1.0), ("lamp_street", -1.95, 2.0, 0, None, 2.6)]),
    "lodge": dict(colors=("#F4E2CF", "#8B5A3C", "#E94B3C"), floor=("floor_wood", None),
                  slab=("#F4F8FF", "#C9D6E6"), station="st_fryer",
                  props=[("fireplace", 2.2, 0.85, 35, None, 1.9), ("log_pile", 1.65, -1.65, 10, 1.0, None),
                         ("snowman", -1.1, -1.85, 15, None, 1.45)],
                  env=[("tree_pine_snow", -1.95, 2.0, 0, None, 2.6), ("tree_pine_snow", 2.65, -0.65, 40, None, 1.8)]),
    "rooftop": dict(colors=("#E7EAF6", "#2C3E66", "#FFC23C"), floor=("floor_tile", "#C9CFE6"),
                    slab=("#6A7380", "#474E59"), station="st_grill",
                    props=[("water_tower", 2.25, 0.8, 0, None, 2.5), ("ac_unit", 1.7, -1.6, 25, 1.0, None),
                           ("string_lights", -0.45, -1.95, 0, 2.2, None)],
                    env=[("lamp_street", -1.95, 2.0, 0, None, 2.4), ("plant_pot", 2.75, -0.55, 0, None, 0.9)]),
    "desert": dict(colors=("#FFF0D6", "#FF9A3C", "#1FA6B8"), floor=("floor_terrazzo", None),
                   slab=("#F3C98B", "#D69A5A"), station="st_grill",
                   props=[("retro_sign", 2.3, 0.8, 30, None, 2.4), ("cactus", 1.7, -1.6, 0, None, 1.6),
                          ("cow_skull", -1.1, -1.9, 15, 0.9, None)],
                   env=[("palm", -1.95, 2.0, 0, None, 2.4), ("lamp_street", 2.75, -0.75, 0, None, 2.0)]),
    "tokyo": dict(colors=("#F6E8FF", "#B79CFF", "#FF8FB1"), floor=("floor_tile", "#FFD3E2"),
                  slab=("#6E6A80", "#4E4B5E"), station="st_fryer",
                  props=[("vending_machine", 2.2, 0.95, 30, None, 1.7), ("bonsai", 1.65, -1.6, 0, None, 1.0),
                         ("lantern", -1.1, -1.85, 0, None, 1.9)],
                  env=[("neon_burger", 2.55, -0.55, 40, None, 1.0), ("lamp_street", -1.95, 2.0, 0, None, 2.5)]),
    "jungle": dict(colors=("#EAF8DE", "#4CC38A", "#FFC23C"), floor=("floor_wood", None),
                   slab=("#5DB04F", "#7A5236"), station="st_soda",
                   props=[("vine_arch", 2.35, 0.7, 90, 2.0, None), ("tiki_torch", 1.65, -1.6, 0, None, 1.6),
                          ("big_leaf", -1.1, -1.85, 0, 1.2, None)],
                   env=[("palm", -1.95, 2.0, 0, None, 2.6), ("bush", 2.75, -0.9, 0, 0.75, None)]),
    "volcano": dict(colors=("#F7E1D7", "#F2542D", "#FFC23C"), floor=("floor_terrazzo", None),
                    slab=("#4A3346", "#2E2030"), station="st_grill",
                    props=[("obsidian_pillar", 2.2, 0.9, 0, None, 2.0), ("lava_rock", 1.75, -1.6, 0, 1.1, None),
                           ("steam_vent", -1.1, -1.9, 0, 0.95, None)],
                    env=[("palm", -1.95, 2.0, 0, None, 2.4)]),
    "space": dict(colors=("#E8F4FF", "#1FA6B8", "#FF8FB1"), floor=("floor_tile", "#CFE8FF"),
                  slab=("#2C3E66", "#1C2846"), station="st_shake",
                  props=[("rocket_model", 2.2, 0.95, 0, None, 1.7), ("console", 1.75, -1.6, 25, 1.1, None),
                         ("astro_plant", -1.1, -1.85, 0, None, 1.2)],
                  env=[("cloud", -1.75, 2.15, 0, 1.6, None, {"white": "#E3D9FF"}, 1.15), ("lamp_floor", 2.75, -0.6, 0, None, 1.5)]),
    "underwater": dict(colors=("#E0FBFF", "#1FA6B8", "#FF9A3C"), floor=("floor_tile", "#BDEFF4"),
                       slab=("#F1E2B8", "#C9B488"), station="st_soda",
                       props=[("bubble_column", 2.25, 0.9, 0, None, 2.1), ("clam", 1.7, -1.55, 25, 1.0, None),
                              ("coral", -1.1, -1.85, 0, None, 1.3)],
                       env=[("tree_round", -1.95, 2.0, 0, None, 2.1, GREEN_TO_SEA),
                            ("bush", 2.75, -0.8, 0, 0.7, None, GREEN_TO_SEA)]),
    "candy": dict(colors=("#FFF0FA", "#FF8FB1", "#B79CFF"), floor=("floor_checker", "#FF8FB1"),
                  slab=("#A8F0D0", "#FFB3D1"), station="st_shake",
                  props=[("gumdrop_tree", 2.25, 0.85, 0, None, 2.3), ("lollipop", 1.65, -1.6, 0, None, 1.7),
                         ("candy_cane", -1.1, -1.85, 0, None, 1.5)],
                  env=[("tree_round", -1.95, 2.0, 0, None, 2.3, GREEN_TO_CANDY),
                       ("bush", 2.75, -0.8, 0, 0.7, None, GREEN_TO_CANDY)]),
}


def room_shell(floor_fn, floor_tint, wall, trim, n=3, mult=False, full=False):
    """n x n floor patch (cells centred on -1..1) + L of two wall pieces and a corner post.
    mult=True multiplies the floor's own colours by floor_tint (theme floors on wood)."""
    objs = []
    half = n // 2
    for ix in range(-half, half + 1):
        for iy in range(-half, half + 1):
            fo = place(floor_fn, (ix, iy, 0))
            if mult:
                multiply(fo, floor_tint)
            else:
                remap(fo, tint=floor_tint)
            objs += fo
    walls = []
    if full:  # back and left walls along the whole patch (themes: a real room corner)
        for k, ix in enumerate(range(-half, half + 1)):
            fn = building.build_wall_window if ix == 0 else building.build_wall
            walls += place(fn, (ix, half + 0.5, 0))
        for iy in range(-half, half + 1):
            walls += place(building.build_wall, (-half - 0.5, iy, 0), rotz=90)
    else:
        walls += place(building.build_wall, (-half, half + 0.5, 0))
        walls += place(building.build_wall, (-half - 0.5, half, 0), rotz=90)
    walls += place(building.build_wall_post, (-half - 0.5, half + 0.5, 0))
    remap(walls, tint=trim, colors={"cream": wall})
    return objs + walls


def build_store(sid):
    spec = STORE_SPECS[sid]
    wall, trim, accent = spec["colors"]
    B.reset_scene()
    fmodel, ftint = spec["floor"]
    room_shell(FLOOR[fmodel], ftint or trim, wall, trim)
    slab(5.6, 4.9, spec["slab"][0], spec["slab"][1], cx=0.45, cy=-0.15)
    st = place(_st(spec["station"]), (0.15, 0.98, 0))
    remap(st, tint=trim)
    ct = place(stations.build_counter, (-0.05, -0.45, 0))
    remap(ct, tint=trim, colors={"cream": wall, "offwhite": "#FFFFFF"})
    # food on the counter racks
    a = anchors_of(ct)
    z = a["rack0"].z
    for fn in (food.build_burger, food.build_cheese):
        it = place(fn, (a["rack0"].x, a["rack0"].y, z))
        z = anchors_of(it)["top"].z
    place(food.build_fries, tuple(a["rack1"]))
    sd = place(food.build_soda, tuple(a["rack2"]))
    remap(sd, tint=accent)
    for i, entry in enumerate(spec["props"] + spec["env"]):
        name, x, y, rz, fit, hcap = entry[:6]
        cmap = entry[6] if len(entry) > 6 else None
        z = entry[7] if len(entry) > 7 else 0.0
        fn = _sig(sid, name) if i < len(spec["props"]) else _env(name)
        s = fit_scale(fn, fit, hcap)
        o = place(fn, (x, y, z), rotz=rz, scale=s)
        remap(o, tint=trim, colors=cmap)
        if name == "cloud":  # backdrop puff: no shadow on the transparent background
            for ob in meshes(o):
                ob.visible_shadow = False
    finalize_and_render(os.path.join(UI_DIR, "store_%s.png" % sid), (640, 512), yaw=38, pitch=34, margin=1.05)


# ---------------------------------------------------------------------------
# 2. Interior themes
# ---------------------------------------------------------------------------
THEMES = {
    "classic": ("#FFFFFF", "#FFF3DC", "#F2542D", "#F2542D", "plant"),
    "pastel": ("#FFE6F2", "#F2F0FF", "#B79CFF", "#8FD8FF", "plant"),
    "neon": ("#D8D2FF", "#2C3E66", "#FF8FB1", "#3DDC97", "lamp"),
    "cabin": ("#F3D7B8", "#F4E2CF", "#8B5A3C", "#C98A55", "lamp"),
    "royal": ("#FFF2CC", "#FFF7E6", "#E0A21B", "#9B2C5C", "plant"),
    "spooky": ("#E0D4F0", "#5B4A70", "#FF9A3C", "#6B4C9A", "lamp"),
    "winter": ("#EAF6FF", "#FFFFFF", "#E94B3C", "#4CC38A", "plant"),
    "tropical": ("#E6FFF4", "#FFFBEA", "#1FA6B8", "#FFC23C", "plant"),
}


def shade(hexc, k):
    r, g, b = B.color(hexc)
    return "#%02X%02X%02X" % (int(r * k * 255), int(g * k * 255), int(b * k * 255))


def build_theme(tid):
    floor_t, wall_t, trim, seat, extra = THEMES[tid]
    B.reset_scene()
    room_shell(building.build_floor_wood, floor_t, wall_t, trim, mult=True, full=True)
    wood = shade("#C98A55", 1.0)
    tr, tg, tb = B.color(floor_t)
    wr, wg, wb = B.color(wood)
    base = "#%02X%02X%02X" % (int(wr * tr * 255), int(wg * tg * 255), int(wb * tb * 255))
    slab(3.36, 3.36, shade(base, 0.92), shade(base, 0.62), cx=0.0, cy=0.0, r=0.3)
    t = place(stations.build_table2, (0.25, -0.15, 0), rotz=0)
    a = anchors_of(t)
    for nm, rz in (("seat0", -90), ("seat1", 90)):
        p = a[nm]
        ch = place(stations.build_chair, (p.x, p.y, 0), rotz=rz, scale=0.95)
        remap(ch, tint=seat)
    place(food.build_burger, (a["tray0"].x + 0.04, a["tray0"].y - 0.04, a["tray0"].z))
    sd = place(food.build_soda, (a["tray1"].x - 0.04, a["tray1"].y + 0.08, a["tray1"].z))
    remap(sd, tint=trim)
    if extra == "lamp":
        lp = place(env.build_lamp_floor, (-0.95, 0.95, 0))
        glow = {"neon": "#FF8FB1", "spooky": "#FF9A3C"}.get(tid, "#FFD89A")
        remap(lp, colors={"#FFE2A8": glow, "#FFF4D0": "#FFF4D0"})
        for o in meshes(lp):
            for i, m in enumerate(o.data.materials):
                if m is not None and float(m.get("emit", 0)) > 0:
                    o.data.materials[i] = B.mat("#" + hexcol(m), emit=1.4)
        # warm/coloured light pool from the lamp
        ld = bpy.data.lights.new("LampGlow", 'POINT')
        ld.energy = 90 if tid in ("neon", "spooky") else 45
        ld.color = B.color(glow)
        ld.shadow_soft_size = 0.15
        lo_ = bpy.data.objects.new("LampGlow", ld)
        lo_.location = (-0.95, 0.95, 1.33)
        bpy.context.collection.objects.link(lo_)
        place(env.build_plant_pot, (1.0, 1.0, 0), scale=0.85)
    else:
        place(env.build_plant_pot, (-0.95, 0.95, 0))
        lp = place(env.build_lamp_floor, (1.05, 1.0, 0), scale=0.9)
    finalize_and_render(os.path.join(UI_DIR, "theme_%s.png" % tid), (512, 512), yaw=40, pitch=36, margin=1.04,
                        world=0.5 if tid not in ("neon", "spooky") else 0.38)


# ---------------------------------------------------------------------------
# 3. Upgrade icons
# ---------------------------------------------------------------------------
def icon_speed():
    bolt = kit.ccw([(0.11, 0.54), (-0.25, -0.03), (-0.03, -0.03), (-0.15, -0.54), (0.27, 0.11), (0.05, 0.11)])
    o = [prism(bolt, 0.17, M("mustard"), side_m=M("gold_dark"), bevel=0.04, segs=3, rot=(90, 0, 0),
               loc=(0, 0.085, 0.56), wn=True)]
    hl = kit.ccw([(0.07, 0.40), (-0.13, 0.04), (-0.07, 0.04), (0.06, 0.30)])
    o.append(prism(hl, 0.02, M("#FFF1A8"), loc=(0, -0.09, 0.56), rot=(90, 0, 0), wn=False))
    for (x, z, L) in ((-0.42, 0.78, 0.34), (-0.50, 0.58, 0.44), (-0.40, 0.38, 0.28)):
        o.append(rbox((L, 0.07, 0.07), loc=(x, 0.05, z), m=M("sky"), r=0.034, segs=2))
    g = join(o, "icon")
    move([g], (0, 0, 0), rot=(0, -12, 18))
    kit.plate_on(g, 0.0)
    return [g]


def icon_capacity():
    seq = [food.build_burger, food.build_cheese, food.build_double, food.build_veggie, food.build_burger]
    base = Vector((0, 0, 0))
    tilt = 0.0
    for i, fn in enumerate(seq):
        o = place(fn, tuple(base), rot=(0, tilt, i * 23))
        base = anchors_of(o)["top"] + Vector((0.012, 0, -0.004))
        tilt += 4.5


def icon_cook():
    o = []
    o.append(lathe([(0.10, 0.42), (0.26, 0.47), (0.36, 0.56), (0.40, 0.67), (0.41, 0.73), (0.38, 0.745)], 20,
                   [M("tomato")], cap_bot=True, name="bowl"))
    o.append(lathe([(0.405, 0.72), (0.425, 0.735), (0.41, 0.755), (0.385, 0.75)], 20, [M("steel")], name="rim"))
    for k in range(3):
        a = TAU * k / 3 + 0.5
        o.append(tube([(0.22 * math.cos(a), 0.22 * math.sin(a), 0.48), (0.40 * math.cos(a), 0.40 * math.sin(a), 0.0)],
                      0.025, M("cocoa"), seg=6))
    for k in range(-3, 4):
        o.append(rbox((0.012, 0.74 - abs(k) * 0.08, 0.012), loc=(k * 0.1, 0, 0.745), m=M("steel_dark"), r=0, segs=1))
    o.append(rbox((0.78, 0.016, 0.016), loc=(0, 0, 0.75), m=M("steel_dark"), r=0, segs=1))
    # flames licking up through the grate
    for (x, y, h, r, c) in ((-0.16, 0.05, 0.40, 0.09, "orange"), (0.02, 0.12, 0.48, 0.10, "orange"),
                            (0.20, 0.02, 0.36, 0.08, "orange"), (-0.05, -0.12, 0.30, 0.07, "mustard"),
                            (0.14, -0.14, 0.26, 0.06, "mustard"), (-0.22, -0.08, 0.24, 0.06, "#FFF1A8")):
        o.append(lathe([(0.0, 0.0), (r * 0.8, h * 0.12), (r, h * 0.28), (r * 0.7, h * 0.55), (r * 0.25, h * 0.82),
                        (0.0, h)], 8, [E(c, 1.1)], loc=(x, y, 0.70), name="flame"))
    # two patties on the grate
    for (x, y) in ((-0.13, -0.02), (0.15, 0.06)):
        o.append(lathe([(0.10, 0.0), (0.12, 0.012), (0.12, 0.032), (0.10, 0.045), (0.0, 0.048)], 14, [M("patty")],
                       cap_bot=True, loc=(x, y, 0.757), name="patty"))
    g = join(o, "icon")
    move([g], (0, 0, 0), rotz=-15)
    return [g]


def icon_patience():
    o = []
    wood, dark = M("wood"), M("wood_dark")
    for z in (0.0, 0.96):
        o.append(lathe([(0.30, z), (0.32, z + 0.02), (0.32, z + 0.07), (0.30, z + 0.09), (0.0, z + 0.09)], 16,
                       [wood], cap_bot=True, name="cap"))
        o.append(lathe([(0.325, z + 0.035), (0.335, z + 0.045), (0.325, z + 0.055)], 16, [dark], name="ring"))
    for k in range(3):
        a = TAU * k / 3 + 0.4
        o.append(rcyl(0.03, 0.88, loc=(0.25 * math.cos(a), 0.25 * math.sin(a), 0), z0=0.09, m=dark, verts=8,
                      bevel=0.01))
    # sand (inside the glass)
    o.append(lathe([(0.20, 0.09), (0.19, 0.16), (0.12, 0.25), (0.04, 0.31), (0.0, 0.32)], 16, [M("mustard")],
                   cap_bot=True, name="sand"))
    o.append(lathe([(0.12, 0.70), (0.05, 0.60), (0.0, 0.57)], 16, [M("mustard")], name="sandtop"))
    o.append(rcyl(0.008, 0.26, loc=(0, 0, 0), z0=0.31, m=M("mustard"), verts=6, bevel=0))
    g = join(o, "icon")
    move([g], (0, 0, 0), rot=(0, 14, 10))
    lo = kit.bbox([g])[0].z
    move([g], (0, 0, -lo))

    def add_glass():
        glass = lathe([(0.21, 0.09), (0.22, 0.20), (0.15, 0.36), (0.035, 0.52), (0.035, 0.53), (0.15, 0.69),
                       (0.22, 0.85), (0.21, 0.96)], 20, [M("glass")], name="glass", angle=80)
        glass.data.materials[0] = glass_mat()
        move([glass], (0, 0, 0), rot=(0, 14, 10))
        move([glass], (0, 0, -lo))
    return add_glass


def coin_pile():
    for (x, y, n) in ((0.0, 0.0, 6), (0.20, 0.10, 4), (-0.18, 0.12, 3), (0.08, -0.20, 2)):
        z = 0.0
        for k in range(n):
            it = place(food.build_coin, (x + 0.006 * (k % 2), y, z), rotz=k * 37)
            z = anchors_of(it)["top"].z
    for (x, y, r) in ((-0.24, -0.16, (60, 0, 25)), (0.30, -0.12, (-55, 10, -30))):
        place(food.build_coin, (x, y, 0.06), rot=r)


def icon_tips():
    coin_pile()
    place(food.build_gem, (-0.05, -0.27, 0.0), rot=(8, 0, 20), scale=1.5)


def icon_start():
    o = []
    kraft = M("#4CC38A")

    def frill(i, t, p):
        if i >= 7:
            k = 1 + 0.12 * math.sin(7 * t)
            return (p[0] * k, p[1] * k, p[2] + 0.02 * math.sin(7 * t + 1))
        return p
    o.append(lathe([(0.20, 0.0), (0.33, 0.05), (0.40, 0.18), (0.39, 0.34), (0.30, 0.48), (0.17, 0.56), (0.12, 0.60),
                    (0.17, 0.66), (0.23, 0.72), (0.15, 0.72), (0.0, 0.69)], 16, [kraft], cap_bot=True, deform=frill,
                   name="sack"))
    o.append(kit.finish(B.torus(0.13, 0.035, loc=(0, 0, 0.60), m=M("tomato"), maj=16, minr=6), angle=70))
    o.append(rbox((0.06, 0.04, 0.16), loc=(0.05, -0.15, 0.53), m=M("tomato"), r=0.02, segs=1, rot=(20, 0, 25)))
    g = join(o, "icon")
    move([g], (0, 0, 0), rotz=-10)
    # coin emblem on the front of the sack
    place(food.build_coin, (0.0, -0.39, 0.30), rot=(90 - 18, 0, -10), scale=1.5)
    for (x, y, r) in ((0.36, -0.30, (0, 0, 20)), (-0.38, -0.22, (0, 0, -15)), (0.44, -0.08, (0, 0, 5))):
        place(food.build_coin, (x, y, 0.0), rot=r)
    place(food.build_coin, (0.36, -0.30, 0.035), rotz=50)
    place(food.build_coin, (-0.22, -0.42, 0.06), rot=(55, 0, 30))
    return [g]


def icon_staff():
    o = []

    def pleat(i, t, p):
        k = 1 + 0.03 * math.cos(12 * t)
        return (p[0] * k, p[1] * k, p[2])
    o.append(lathe([(0.24, 0.0), (0.255, 0.02), (0.27, 0.28), (0.25, 0.30), (0.0, 0.30)], 24, [M("white")],
                   deform=pleat, cap_bot=True, name="band"))
    for k in range(6):
        a = TAU * k / 6
        o.append(ball(0.17, loc=(0.17 * math.cos(a), 0.17 * math.sin(a), 0.40), m=M("white"), seg=12, rings=8))
    o.append(ball(0.21, loc=(0, 0, 0.50), m=M("white"), seg=14, rings=9))
    o.append(lathe([(0.262, 0.035), (0.272, 0.045), (0.278, 0.125), (0.268, 0.135)], 24, [M("tomato")], name="band"))
    o.append(prism(kit.star_pts(5, 0.10, 0.045), 0.04, M("mustard"), side_m=M("gold_dark"), bevel=0.012, segs=2,
                   loc=(0, -0.258, 0.205), rot=(90, 0, 0)))
    g = join(o, "icon")
    move([g], (0, 0, 0), rot=(0, 10, -12))
    kit.plate_on(g, 0.0)
    return [g]


def icon_income():
    o = []
    o.append(rbox((0.92, 0.72, 0.62), loc=(0, 0, 0), z0=0.0, m=M("cream"), r=0.06, segs=2))
    o.append(rbox((1.02, 0.82, 0.12), loc=(0, 0, 0), z0=0.60, m=M("tomato"), r=0.05, segs=2))
    o.append(rbox((0.22, 0.04, 0.38), loc=(-0.22, -0.36, 0.19), m=M("cocoa"), r=0.03, segs=1))
    o.append(rbox((0.30, 0.04, 0.22), loc=(0.18, -0.36, 0.30), m=M("glass"), r=0.03, segs=1))
    o.append(ball(0.02, loc=(-0.15, -0.39, 0.20), m=M("mustard"), seg=6, rings=4))
    g = join(o + stations.striped_awning(-0.48, 0.48, 4, -0.36, -0.62, 0.60, 0.46, drop=0.07), "icon")
    remap([g], tint="#F2542D")
    place(lambda: [join(stations.burger_badge(0, -0.06, 0.92, r=0.2), "badge")], (0, 0, 0))
    for (x, z, r) in ((-0.50, 1.30, (70, 0, 25)), (-0.20, 1.58, (80, 0, -10)), (0.16, 1.66, (75, 0, 15)),
                      (0.50, 1.38, (65, 0, -30))):
        place(food.build_coin, (x, -0.05, z), rot=r, scale=1.2)
    return [g]


ICONS = {"speed": icon_speed, "capacity": icon_capacity, "cook": icon_cook, "patience": icon_patience,
         "tips": icon_tips, "start": icon_start, "staff": icon_staff, "income": icon_income}


def build_icon(iid):
    B.reset_scene()
    r = ICONS[iid]()
    finalize_and_render(os.path.join(UI_DIR, "upgrade_%s.png" % iid), (256, 256), yaw=28, pitch=24, margin=1.18,
                        ao_dist=0.15, ao_strength=0.6, samples=96, world=0.5, post=r if callable(r) else None)


# ---------------------------------------------------------------------------
# 4. Food cards
# ---------------------------------------------------------------------------
FOODS = ["burger", "fries", "soda", "cheese", "double", "shake", "veggie"]


def build_food(fid):
    B.reset_scene()
    o = place(getattr(food, "build_" + fid), (0, 0, 0), rotz=(-20 if fid in ("fries", "soda", "shake") else 0))
    remap(o, tint="#F2542D")
    finalize_and_render(os.path.join(UI_DIR, "card_food_%s.png" % fid), (256, 256), yaw=28, pitch=26, margin=1.15,
                        ao_dist=0.08, ao_strength=0.6, samples=96, world=0.5)


# ---------------------------------------------------------------------------
JOBS = {}
for _s in STORE_SPECS:
    JOBS["store_" + _s] = (build_store, _s)
for _t in THEMES:
    JOBS["theme_" + _t] = (build_theme, _t)
for _i in ICONS:
    JOBS["upgrade_" + _i] = (build_icon, _i)
for _f in FOODS:
    JOBS["card_food_" + _f] = (build_food, _f)

if __name__ == "__main__":
    want = [a for a in B.script_args() if not a.startswith("-")]
    failed = []
    for name, (fn, arg) in JOBS.items():
        if want and not any(name.startswith(w) for w in want):
            continue
        try:
            fn(arg)
        except Exception:
            import traceback
            traceback.print_exc()
            failed.append(name)
    if failed:
        print("FAILED:", ", ".join(failed))
