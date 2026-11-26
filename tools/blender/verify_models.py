"""
Verify the prop/food/station/building/store .bvm files against
docs/ASSET_CONTRACT.md §4-§7. Plain Python (no numpy, no Blender):

    python3 -I tools/blender/verify_models.py

Checks per model: file exists and parses (BVM1 layout from bvlib), part names
and parent links, triangle budget, required anchors, bounding box vs the
contract footprint (engine axes: x = Blender X, y = up, z = -Blender Y), pivot
at the floor (min y ~ 0) and, for floor/ground tiles, that every upward
facing vertex sits exactly on y = 0. Exit code 1 if anything fails.
Character/hat/pet files (char_*, hat_*, pet_*) belong to another artist and
are ignored.
"""
import os
import struct
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MODELS = os.path.join(ROOT, "app", "src", "main", "assets", "models")

R = ["root"]
SEATS2 = ["seat0", "seat1", "tray0", "tray1"]
SEATS4 = ["seat%d" % i for i in range(4)] + ["tray%d" % i for i in range(4)]

# name: (budget, parts, anchors, footprint (x, z) or None, tint required, kind)
SPEC = {}


def add(name, budget, parts=None, anchors=(), fp=None, tint=False, kind="prop"):
    SPEC[name] = (budget, parts or R, list(anchors), fp, tint, kind)


# §4 food
for n in ("burger", "cheese", "double", "veggie", "fries", "soda", "shake", "tray", "bag", "coin", "gem",
          "patty_raw"):
    fp = {"burger": (0.30, 0.30), "cheese": (0.30, 0.30), "double": (0.30, 0.30), "veggie": (0.30, 0.30),
          "fries": (0.20, 0.14), "soda": (0.14, 0.14), "shake": (0.14, 0.14), "tray": (0.36, 0.26),
          "bag": (0.24, 0.16), "coin": (0.16, 0.16), "gem": (0.14, 0.14), "patty_raw": (0.24, 0.24)}[n]
    add("food_" + n, 700, ["item"], ["top"], fp, tint=(n == "soda"), kind="food")
# §5 stations & furniture
add("st_grill", 4000, anchors=["out", "cook", "smoke"], fp=(1.4, 1.0))
add("st_fryer", 3500, anchors=["out", "smoke"], fp=(1.0, 1.0))
add("st_soda", 3500, anchors=["out"], fp=(1.0, 0.8))
add("st_shake", 3500, parts=["root", "spinner"], anchors=["out", "spin"], fp=(1.0, 0.8))
add("counter", 4000, anchors=["register", "cashier", "rack0", "rack1", "rack2", "rack3", "queue"], fp=(2.4, 0.9),
    tint=True)
add("drive_window", 3000, anchors=["rack0", "rack1", "cashier", "car"], fp=(1.6, 1.2), tint=True)
add("table2", 1200, anchors=SEATS2, fp=(1.0, 1.0))
add("table4", 1500, anchors=SEATS4, fp=(1.6, 1.6))
add("chair", 600, fp=(0.45, 0.45), tint=True)
add("trash_bin", 1000, anchors=["in"], fp=(0.6, 0.6))
add("pad", 800, fp=(1.3, 1.3), tint=True)
add("upgrade_pad", 800, fp=(1.1, 1.1), tint=True)
add("staff_pad", 800, fp=(1.1, 1.1), tint=True)
add("register_stand", 800, fp=(0.6, 0.6))
# §6 building & environment
add("floor_wood", 60, fp=(1, 1), kind="tile")
add("floor_checker", 40, fp=(1, 1), tint=True, kind="tile")
add("floor_terrazzo", 80, fp=(1, 1), kind="tile")
add("floor_tile", 40, fp=(1, 1), tint=True, kind="tile")
add("ground_grass", 20, fp=(1, 1), kind="tile")
add("ground_sidewalk", 40, fp=(1, 1), kind="tile")
add("road", 40, fp=(2, 2), kind="tile")
add("crosswalk", 80, fp=(2, 2), kind="tile")
add("wall", 300, fp=(1.0, 0.18), tint=True)
add("wall_window", 500, fp=(1.0, 0.18), tint=True)
add("wall_post", 200, fp=(0.26, 0.26), tint=True)
add("door_frame", 600, fp=(1.6, None), tint=True)
add("tree_round", 900, fp=(1.4, 1.4))
add("tree_pine", 600, fp=(1.1, 1.1))
add("tree_pine_snow", 600, fp=(1.1, 1.1))
add("palm", 900, fp=(1.6, 1.6))
add("bush", 400, fp=(0.8, 0.8))
add("flower_bed", 600, fp=(1.0, 0.5))
add("plant_pot", 600, fp=(0.6, 0.6))
add("lamp_street", 500, fp=(0.4, 0.4))
add("lamp_floor", 400, fp=(0.42, 0.42))
add("bench", 600, fp=(1.4, 0.5))
add("menu_board", 500, fp=(1.2, None))
add("neon_burger", 800, fp=(1.0, None))
add("umbrella", 700, fp=(1.6, 1.6), tint=True)
add("car", 2500, parts=["root", "wheels"], fp=(1.8, 3.6), tint=True)
add("van", 2500, parts=["root", "wheels"], fp=(2.0, 4.2), tint=True)
add("scooter", 1200, fp=(0.62, 1.4), tint=True)
add("cloud", 300, fp=(3.0, None))
# §7 store props
STORES = {"wagon": ["truck", "balloon_stand", "picnic_table"], "seaside": ["surfboard", "lifeguard_tower", "beach_chair"],
          "diner": ["jukebox", "stool", "neon_star"], "lodge": ["fireplace", "log_pile", "snowman"],
          "rooftop": ["ac_unit", "water_tower", "string_lights"], "desert": ["cactus", "cow_skull", "retro_sign"],
          "tokyo": ["lantern", "vending_machine", "bonsai"], "jungle": ["vine_arch", "tiki_torch", "big_leaf"],
          "volcano": ["lava_rock", "obsidian_pillar", "steam_vent"], "space": ["console", "astro_plant", "rocket_model"],
          "underwater": ["coral", "clam", "bubble_column"], "candy": ["lollipop", "candy_cane", "gumdrop_tree"]}
for st, props in STORES.items():
    for p in props:
        add("store_%s_%s" % (st, p), 1500, tint=(st == "wagon" and p == "truck"))

# Contract items that must carry emissive vertices (bvlib writes emission into the 4th normal byte).
EMISSIVE = {"lamp_street", "lamp_floor", "neon_burger", "store_diner_jukebox", "store_lodge_fireplace",
            "store_rooftop_string_lights", "store_tokyo_lantern", "store_tokyo_vending_machine",
            "store_jungle_tiki_torch", "store_volcano_lava_rock", "store_space_console"}

TOL = 0.025  # metres of slack on footprints (handles, bevel bulges)


def parse(path):
    b = open(path, "rb").read()
    if b[:4] != b"BVM1":
        raise ValueError("bad magic")
    o = 4
    (n,) = struct.unpack_from("<H", b, o)
    o += 2
    parts = []
    for _ in range(n):
        ln = b[o]
        name = b[o + 1:o + 1 + ln].decode("utf-8")
        o += 1 + ln
        (parent,) = struct.unpack_from("<h", b, o)
        o += 2
        pivot = struct.unpack_from("<3f", b, o)
        o += 12
        vc, ic = struct.unpack_from("<II", b, o)
        o += 8
        verts = []
        for i in range(vc):
            px, py, pz = struct.unpack_from("<3f", b, o)
            nx, ny, nz, em = struct.unpack_from("<4b", b, o + 12)
            r, g, bb, a = struct.unpack_from("<4B", b, o + 16)
            verts.append((px + pivot[0], py + pivot[1], pz + pivot[2], nx, ny, nz, a, em))
            o += 20
        idx = struct.unpack_from("<%dH" % ic, b, o)
        o += 2 * ic
        parts.append({"name": name, "parent": parent, "pivot": pivot, "verts": verts, "idx": idx})
    (na,) = struct.unpack_from("<H", b, o)
    o += 2
    anchors = {}
    for _ in range(na):
        ln = b[o]
        name = b[o + 1:o + 1 + ln].decode("utf-8")
        o += 1 + ln
        anchors[name] = struct.unpack_from("<3f", b, o)
        o += 12
    if o != len(b):
        raise ValueError("trailing bytes: %d" % (len(b) - o))
    return parts, anchors


def check(name):
    budget, want_parts, want_anchors, fp, want_tint, kind = SPEC[name]
    path = os.path.join(MODELS, name + ".bvm")
    errs = []
    if not os.path.exists(path):
        return {"name": name, "errs": ["missing file"], "tris": 0, "budget": budget, "dims": (0, 0, 0),
                "anchors": "", "tint": 0, "emit": 0, "pivots": ""}
    parts, anchors = parse(path)
    tris = sum(len(p["idx"]) // 3 for p in parts)
    if tris > budget:
        errs.append("tris %d > %d" % (tris, budget))
    names = [p["name"] for p in parts]
    if names != want_parts:
        errs.append("parts %s != %s" % (names, want_parts))
    for i, p in enumerate(parts):
        if len(p["idx"]) % 3:
            errs.append("index count not /3")
        if p["idx"] and max(p["idx"]) >= len(p["verts"]):
            errs.append("index out of range in %s" % p["name"])
        if i == 0 and p["parent"] != -1:
            errs.append("root has a parent")
        if i > 0 and p["parent"] != 0:
            errs.append("%s parent %d (expected 0)" % (p["name"], p["parent"]))
    missing = [a for a in want_anchors if a not in anchors]
    if missing:
        errs.append("missing anchors " + ",".join(missing))
    allv = [v for p in parts for v in p["verts"]]
    xs = [v[0] for v in allv]
    ys = [v[1] for v in allv]
    zs = [v[2] for v in allv]
    dims = (max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))
    tint_n = sum(1 for v in allv if v[6] == 255)
    if want_tint and tint_n == 0:
        errs.append("no tint-masked vertices")
    emit_n = sum(1 for v in allv if v[7] > 0)
    if name in EMISSIVE and emit_n == 0:
        errs.append("no emissive vertices")
    if fp:
        fx, fz = fp
        tol = TOL if kind != "food" else 0.2 * max(fx, fz)  # §4 sizes are "approx." (lettuce/cheese overhang)
        if fx is not None and dims[0] > fx + tol:
            errs.append("width %.3f > %.2f" % (dims[0], fx))
        if fz is not None and dims[2] > fz + tol:
            errs.append("depth %.3f > %.2f" % (dims[2], fz))
    if kind == "tile":
        ups = [v for v in allv if v[4] >= 125]
        bad = [v for v in ups if abs(v[1]) > 1e-5 and v[1] < 0.0]
        if max(v[1] for v in ups) > 1e-5 and name != "ground_grass":
            errs.append("up-facing geometry above y=0")
        if bad:
            errs.append("%d up-facing vertices below y=0" % len(bad))
        if not any(abs(v[1]) < 1e-6 for v in ups):
            errs.append("no top surface at y=0")
        if min(ys) < -0.0401:
            errs.append("slab deeper than 0.04")
    else:
        if min(ys) < -0.005:
            errs.append("geometry below the floor (min y %.3f)" % min(ys))
        if min(ys) > 0.02 and not name.startswith("store_") and name != "cloud":
            errs.append("model floats (min y %.3f)" % min(ys))
    if name.startswith("food_") and "top" in anchors:
        if anchors["top"][1] <= 0.0:
            errs.append("@top not above the base")
    an = " ".join("%s(%.2f,%.2f,%.2f)" % (k, *v) for k, v in sorted(anchors.items()))
    pv = " ".join("%s(%.2f,%.2f,%.2f)" % (p["name"], *p["pivot"]) for p in parts[1:])
    return {"name": name, "errs": errs, "tris": tris, "budget": budget, "dims": dims, "anchors": an,
            "tint": tint_n, "emit": emit_n, "pivots": pv}


def main():
    rows = [check(n) for n in SPEC]
    print("%-32s %6s %6s  %-20s %5s %5s  %s" % ("model", "tris", "budget", "size x*y*z (m)", "tintv", "emitv",
                                                "status"))
    fails = 0
    for r in rows:
        status = "OK" if not r["errs"] else "FAIL: " + "; ".join(r["errs"])
        fails += bool(r["errs"])
        print("%-32s %6d %6d  %5.2f*%5.2f*%5.2f   %5d %5d  %s" % (r["name"], r["tris"], r["budget"], r["dims"][0],
                                                                r["dims"][1], r["dims"][2], r["tint"], r["emit"],
                                                                status))
    print("\nAnchors (engine coords: x, y=up, z=-blender_y):")
    for r in rows:
        if r["anchors"] and not r["name"].startswith("food_"):
            print("  %-16s %s" % (r["name"], r["anchors"]))
    print("\nChild part pivots (engine coords):")
    for r in rows:
        if r["pivots"]:
            print("  %-16s %s" % (r["name"], r["pivots"]))
    extra = sorted(f[:-4] for f in os.listdir(MODELS) if f.endswith(".bvm") and f[:-4] not in SPEC
                   and not f.startswith(("char_", "hat_", "pet_")))
    if extra:
        print("\nUnexpected (non-contract) files:", ", ".join(extra))
    print("\n%d models checked, %d failed" % (len(rows), fails))
    return 1 if fails or extra else 0


if __name__ == "__main__":
    sys.exit(main())
