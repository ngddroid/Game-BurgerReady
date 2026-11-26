"""
Composed "scene check" renders (NOT exported): verifies cohesion and scale of
the prop kit from the game's high 3/4 camera.

    Blender -b --factory-startup --python tools/blender/scenes.py [-- diner stack exterior]

Writes tools/previews/scenes/<name>.png
"""
import os
import sys
import math

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from kit import B, M  # noqa: E402
import food  # noqa: E402
import stations  # noqa: E402
import building  # noqa: E402
import env  # noqa: E402
from mathutils import Vector  # noqa: E402


def place(fn, loc=(0, 0, 0), rotz=0.0):
    """Build a model into the current scene and move it as a group."""
    before = set(bpy.data.objects)
    fn()
    new = [o for o in bpy.data.objects if o not in before]
    bpy.context.view_layer.update()  # empties' matrix_world is stale until the depsgraph updates
    grp = bpy.data.objects.new("grp", None)
    bpy.context.collection.objects.link(grp)
    for o in new:
        if o.parent is None:
            B.parent_to(o, grp)
    grp.location = loc
    grp.rotation_euler = (0, 0, math.radians(rotz))
    bpy.context.view_layer.update()
    return new


def anchors_of(objs):
    """{anchor name (no '@', no .001 suffix): world position}."""
    out = {}
    for o in objs:
        if o.name.startswith("@"):
            out[o.name[1:].split(".")[0]] = o.matrix_world.translation.copy()
    return out


def mannequin(loc, col="lilac"):
    """1.05 m scale reference (stand-in for a character)."""
    body = kit.ball(0.22, loc=(loc[0], loc[1], 0.30), m=M(col), seg=12, rings=8, scale=(1, 0.9, 1.25))
    head = kit.ball(0.25, loc=(loc[0], loc[1], 0.80), m=M(col), seg=12, rings=8)
    return [body, head]


def tint_scene(color):
    """Preview only: show tint-masked faces in a sample store colour."""
    lin = [B.srgb_to_linear(v) for v in B.color(color)]
    for m in bpy.data.materials:
        if int(m.get("tint", 0)) and m.node_tree:
            bsdf = m.node_tree.nodes.get("Principled BSDF")
            if bsdf:
                bsdf.inputs["Base Color"].default_value = (lin[0], lin[1], lin[2], 1.0)


def scene_setup(world_col=(1.0, 0.95, 0.88), world_strength=0.55, sun=3.0, res=(1280, 960), samples=64):
    B.setup_render(res=res, transparent=False, samples=samples)
    scene = bpy.context.scene
    try:
        bg = scene.world.node_tree.nodes.get("Background")
        bg.inputs[0].default_value = (*world_col, 1.0)
        bg.inputs[1].default_value = world_strength
    except Exception:
        pass
    d = bpy.data.lights.new("Sun", 'SUN')
    d.energy = sun
    d.angle = math.radians(6)
    d.color = (1.0, 0.94, 0.84)
    o = bpy.data.objects.new("Sun", d)
    o.location = (6, -8, 12)
    bpy.context.collection.objects.link(o)
    B.look_at(o, (0, 0, 0))


def game_camera(target, dist, pitch=50, yaw=40, lens=70):
    yr, pr = math.radians(yaw), math.radians(pitch)
    d = Vector((math.sin(yr) * math.cos(pr), -math.cos(yr) * math.cos(pr), math.sin(pr)))
    cam = B.camera(tuple(Vector(target) + d * dist), target, lens=lens)
    cam.data.clip_end = 500
    return cam


# ---------------------------------------------------------------------------
def scene_diner():
    B.reset_scene()
    # floor: checker kitchen strip at the back, wood in the dining room
    for ix in range(-3, 4):
        for iy in range(-3, 3):
            fn = building.build_floor_checker if iy >= 1 else building.build_floor_wood
            place(fn, (ix, iy, 0))
    # back wall (+Y) and left wall (-X) with a corner post and a window in each
    for ix in range(-3, 4):
        place(building.build_wall_window if ix in (-1, 2) else building.build_wall, (ix, 2.5, 0))
    for iy in range(-3, 3):
        place(building.build_wall_window if iy == -1 else building.build_wall, (-3.5, iy, 0), rotz=90)
    place(building.build_wall_post, (-3.5, 2.5, 0))
    # kitchen line against the back wall
    g = place(stations.build_st_grill, (-1.8, 1.95, 0))
    place(stations.build_st_fryer, (0.0, 1.95, 0))
    place(stations.build_st_soda, (1.3, 2.05, 0))
    place(env.build_lamp_floor, (2.9, 2.0, 0))
    # service counter
    c = place(stations.build_counter, (0.2, 0.45, 0))
    a = anchors_of(c)
    # burgers on the racks, raw patties on the grill, a soda
    z = a["rack0"].z
    for k, fn in enumerate((food.build_burger, food.build_cheese, food.build_burger)):
        items = place(fn, (a["rack0"].x, a["rack0"].y, z))
        z += anchors_of(items)["top"].z - z
    place(food.build_fries, tuple(a["rack1"]))
    place(food.build_soda, tuple(a["rack2"]))
    ga = anchors_of(g)
    place(food.build_patty_raw, (ga["cook"].x - 0.2, ga["cook"].y, ga["cook"].z))
    place(food.build_patty_raw, (ga["cook"].x + 0.15, ga["cook"].y + 0.05, ga["cook"].z))
    # dining: table2 + 2 chairs, table4 + 4 chairs
    t2 = place(stations.build_table2, (-1.8, -1.9, 0))
    for nm, rz in (("seat0", -90), ("seat1", 90)):
        p = anchors_of(t2)[nm]
        place(stations.build_chair, (p.x, p.y, 0), rotz=rz)
    place(food.build_tray, tuple(anchors_of(t2)["tray0"]))
    t4 = place(stations.build_table4, (1.4, -1.9, 0))
    for nm, rz in (("seat0", 180), ("seat1", -90), ("seat2", 0), ("seat3", 90)):
        p = anchors_of(t4)[nm]
        place(stations.build_chair, (p.x, p.y, 0), rotz=rz)
    place(env.build_plant_pot, (-3.0, -0.4, 0))
    place(stations.build_trash_bin, (-3.0, -2.6, 0))
    place(stations.build_pad, (3.0, -0.6, 0))
    # scale reference at the queue spot and the cashier spot
    for k in ("queue", "cashier"):
        mannequin(a[k], col="lilac" if k == "queue" else "sky")
    kit.ground_plane(-0.05, 200, color="#9BC46A")
    tint_scene("#F2542D")
    scene_setup()
    game_camera((0.0, 0.0, 0.4), 17, pitch=50, yaw=40, lens=60)
    B.render(os.path.join(kit.PREVIEW_SCENES, "diner_corner.png"))


def scene_stack():
    B.reset_scene()
    seq = [food.build_burger, food.build_cheese, food.build_double, food.build_veggie, food.build_burger,
           food.build_cheese, food.build_burger, food.build_double]
    z = 0.0
    for fn in seq:
        items = place(fn, (0, 0, z))
        z = anchors_of(items)["top"].z
    # context stacks: coins, trays, fries, soda, bag, gem
    z = 0.0
    for k in range(8):
        items = place(food.build_coin, (0.42, 0.10, z), rotz=k * 17)
        z = anchors_of(items)["top"].z
    z = 0.0
    for k in range(3):
        items = place(food.build_tray, (-0.45, 0.15, z), rotz=k * 6)
        z = anchors_of(items)["top"].z
    place(food.build_fries, (-0.35, -0.38, 0))
    place(food.build_soda, (0.05, -0.42, 0))
    place(food.build_shake, (0.38, -0.36, 0))
    place(food.build_bag, (0.72, -0.15, 0), rotz=-20)
    place(food.build_gem, (0.75, 0.30, 0))
    place(food.build_patty_raw, (-0.82, -0.25, 0))
    kit.ground_plane(0.0, 50, color="#C9A27A")
    tint_scene("#3D8BFF")
    scene_setup(res=(1024, 1024))
    game_camera((0.0, 0.0, 0.62), 5.0, pitch=36, yaw=32, lens=70)
    B.render(os.path.join(kit.PREVIEW_SCENES, "burger_stack.png"))


def scene_exterior():
    B.reset_scene()
    # road along Y at x = 0 with a crosswalk, sidewalks, grass
    for k, y in enumerate(range(-8, 9, 2)):
        place(building.build_crosswalk if y == 0 else building.build_road, (0, y, 0))
    for y in range(-8, 9):
        for x in (-1.5, 1.5):
            place(building.build_ground_sidewalk, (x, y, 0))
        for x in (-2.5, -3.5, -4.5, 2.5):
            place(building.build_ground_grass, (x, y, 0))
    # diner front on the right (floor + walls + door); grass strip x 2..3, floor from x = 3
    for x in (3.5, 4.5, 5.5, 6.5, 7.5):
        for y in range(-3, 4):
            place(building.build_floor_terrazzo, (x, y, 0))
    for y in range(-3, 4):
        if y in (0, 1):
            continue
        place(building.build_wall_window if y in (-2, 3) else building.build_wall, (3.0, y, 0), rotz=90)
    place(building.build_door_frame, (3.0, 0.5, 0), rotz=-90)
    for y in (-3.5, -0.5, 1.5, 3.5):
        place(building.build_wall_post, (3.0, y, 0))
    place(env.build_menu_board, (3.9, 2.2, 0), rotz=-90)
    place(env.build_umbrella, (5.5, -1.8, 0))
    # street furniture
    for y in (-6, 2.5):
        place(env.build_lamp_street, (1.5, y, 0))
    place(env.build_bench, (-1.6, -2.0, 0), rotz=-90)
    place(env.build_tree_round, (-3.2, -4.5, 0))
    place(env.build_tree_round, (-3.6, 3.8, 0), rotz=60)
    place(env.build_tree_pine, (-4.2, -0.5, 0))
    place(env.build_bush, (-2.6, 1.0, 0))
    place(env.build_flower_bed, (-2.6, -7.0, 0), rotz=90)
    place(env.build_palm, (-3.4, 2.6, 0))
    # traffic on the 2 m road strip: car heading -Y, van heading +Y, scooter
    place(env.build_car, (0.0, -3.2, 0))
    place(env.build_van, (0.0, 4.4, 0), rotz=180)
    place(env.build_scooter, (0.3, -7.0, 0), rotz=180)
    mannequin((-1.4, 0.3, 0))
    mannequin((1.4, -0.8, 0), col="sky")
    kit.ground_plane(-0.05, 300, color="#8BD45C")
    tint_scene("#1FA6B8")
    scene_setup(world_col=(0.85, 0.93, 1.0), world_strength=0.6)
    game_camera((0.5, -0.5, 0.5), 26, pitch=50, yaw=40, lens=60)
    B.render(os.path.join(kit.PREVIEW_SCENES, "exterior.png"))


SCENES = {"diner": scene_diner, "stack": scene_stack, "exterior": scene_exterior}

if __name__ == "__main__":
    want = [a for a in B.script_args() if not a.startswith("-")] or list(SCENES)
    for n in want:
        SCENES[n]()
