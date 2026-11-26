"""Smoke test for bvlib: builds a burger, exports .bvm with AO and renders a preview."""
import os
import sys
import math
import random

sys.path.insert(0, os.path.dirname(__file__))
import bvlib as B

B.reset_scene()
random.seed(3)

bun_bottom = B.cyl(0.22, 0.07, loc=(0, 0, 0.035), m=B.mat("bun"), verts=40, bevel=0.025, segs=3, name="bb")
patty = B.cyl(0.235, 0.06, loc=(0, 0, 0.1), m=B.mat("patty"), verts=40, bevel=0.025, segs=3, name="pt")
cheese = B.box((0.40, 0.40, 0.018), loc=(0, 0, 0.138), m=B.mat("cheese"), bevel=0.006, segs=2, name="ch", rot=(0, 0, 45))
lettuce = B.cyl(0.25, 0.025, loc=(0, 0, 0.16), m=B.mat("lettuce"), verts=24, bevel=0.01, segs=1, name="le")
# Wavy lettuce edge
for v in lettuce.data.vertices:
    a = math.atan2(v.co.y, v.co.x)
    if (v.co.x ** 2 + v.co.y ** 2) > 0.04:
        v.co.z += 0.012 * math.sin(a * 9)
top = B.sphere(0.23, loc=(0, 0, 0.17), m=B.mat("bun_top"), seg=40, rings=20, scale=(1, 1, 0.62), name="bt")
# cut lower half of the dome
import bmesh
bm = bmesh.new()
bm.from_mesh(top.data)
geom = [v for v in bm.verts if v.co.z < 0]
for v in geom:
    v.co.z = 0
bm.to_mesh(top.data)
bm.free()
seeds = []
for i in range(14):
    a = random.uniform(0, 2 * math.pi)
    r = random.uniform(0.02, 0.15)
    x, y = r * math.cos(a), r * math.sin(a)
    z = 0.17 + 0.23 * 0.62 * math.sqrt(max(0.0, 1 - (r / 0.23) ** 2)) + 0.003
    s = B.sphere(0.012, loc=(x, y, z), m=B.mat("sesame"), seg=8, rings=6, scale=(1.0, 0.55, 0.45), name="s%d" % i,
                 rot=(0, 0, math.degrees(a)))
    seeds.append(s)

part = B.join([bun_bottom, patty, cheese, lettuce, top] + seeds, "burger", pivot=(0, 0, 0))
B.anchor("top", (0, 0, 0.32))
tris = B.export_bvm(os.path.join(B.ASSET_MODELS, "test_burger.bvm"), [part])
B.preview(os.path.join(B.PREVIEW_DIR, "test_burger.png"), target=(0, 0, 0.15), dist=1.1)
