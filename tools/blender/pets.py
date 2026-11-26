"""
pets.py - Bun Voyage! pets (docs/ASSET_CONTRACT.md section 3).

Every pet has two parts: `body` (pivot at the ground centre) and `head` (child of
body, pivot at the neck), plus an `@bubble` anchor. Pets face Blender -Y like the
characters and share their look: glossy dome eyes with highlight dots, soft
raised face markings and smooth rounded shapes.

Outputs
    app/src/main/assets/models/pet_<name>.bvm
    tools/previews/characters/pet_<name>.png
    tools/previews/characters/pets_lineup.png

Run
    ~/Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup \
        --python tools/blender/pets.py -- [name ...] [--no-preview]
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpy
from mathutils import Vector as V

import bvlib as B
import characters as C
import hats as H

PETS = ["pug", "parrot", "hamster", "kitten", "robot"]
BUDGET = 1500
AO_DIST = 0.15
AO_STRENGTH = 0.65


def base_mats(**cols):
    M = C.Mats()
    M["eye"] = B.mat("#17131C", rough=0.08)
    M["hl"] = B.mat("white", rough=0.2, emit=0.6)
    M["mouth"] = B.mat("#3B2A3A", rough=0.5)
    M["blush"] = B.mat("#FF9DB6", rough=0.6)
    for k, v in cols.items():
        M[k] = B.mat(v, rough=0.6)
    return M


def eyes_on(P, M, host, x, z, er, proud=0.009, hl=0.62):
    spots = []
    for s in (1, -1):
        p = host.front(x * s, z)
        n = host.normal(p)
        spots.append((p - n * (er[2] - proud), n))
    C.eye_pair(P, M, spots, er=er, hl_scale=hl, seg=12, hl2=False)


def blush(P, M, host, x, z, a=0.022, b=0.016):
    for s in (1, -1):
        C.decal(P, host, x * s, z, a, b, M.blush, h=0.002, seg=10, rho=(0.88, 1.0))


def surf_decal(P, host, d, a, b, m, h=0.003, roll=0.0, seg=12, rho=(0.62, 0.95, 1.0)):
    """Decal centred where direction d (from the host centre) meets the surface."""
    return C.decal(P, host, 0, 0, a, b, m, h=h, roll=roll, seg=seg, rho=rho, center=host.surf(V(d)))


def stub_leg(P, top, bottom, r, m, foot_r, foot_m, foot_fwd=0.012, seg=6):
    top, bottom = V(top), V(bottom)
    P.sweep([top + V((0, 0, 0.01)), top, bottom + V((0, 0, 0.012)), bottom], [0.0, r, r, 0.0], m, seg=seg)
    # sole sunk below z=0 and flattened onto the ground
    P.ellipsoid(V((bottom.x, bottom.y - foot_fwd, foot_r[2] * 0.55)), foot_r, foot_m, uv=(8, 4), zmin=0.0)


def squircle(th, i=0):
    return (abs(math.cos(th)) ** 4 + abs(math.sin(th)) ** 4) ** -0.25 / 1.07


# ---------------------------------------------------------------------------
# Pug: fawn with a black mask, curly tail, ~0.37 m tall
# ---------------------------------------------------------------------------
def pug():
    M = base_mats(fur="#E6C28E", mask="#2E2731", nose="#17131C", tongue="#FF8FA8", paw="#D9AF78")
    body = C.Part("body")
    torso = C.Ell((0, 0.03, 0.15), (0.105, 0.150, 0.098))
    body.ellipsoid(torso.c, torso.r, M.fur, n=4)
    for x, y in ((0.060, -0.065), (0.066, 0.120)):
        for s in (1, -1):
            stub_leg(body, (x * s, y, 0.14), (x * s, y - 0.004, 0.022), 0.036, M.fur, (0.034, 0.042, 0.022), M.paw)
    tail = [(0, 0.150, 0.205), (0, 0.178, 0.250), (0.020, 0.160, 0.288), (0.040, 0.124, 0.272),
            (0.030, 0.118, 0.238), (0.010, 0.135, 0.230)]
    body.tube(tail, [(0.0, 0.0, M.fur), (0.05, 0.022, M.fur), (0.5, 0.020, M.fur), (0.85, 0.016, M.fur),
                     (1.0, 0.0, M.fur)], seg=8, hint=(1, 0, 0))

    head = C.Part("head")
    hd = C.Ell((0, -0.115, 0.270), (0.118, 0.104, 0.100))
    C.decal(head, hd, 0, 0.250, 0.088, 0.064, M.mask, h=0.004, seg=14)
    muz = C.Ell((0, -0.200, 0.246), (0.066, 0.040, 0.048))
    head.ellipsoid(muz.c, muz.r, M.mask, n=3, cull=hd.inside_fn(0.003))
    C.cover_ell(head, muz.c, muz.r)
    C.nose(head, muz, M.nose, z=0.266, r=(0.026, 0.016, 0.016), proud=0.005)
    head.ellipsoid((0, -0.218, 0.206), (0.020, 0.012, 0.018), M.tongue, uv=(6, 3))
    C.mouth_w(head, muz, M.fur, z0=0.236, w=0.026, r=0.004, stem=0.0)
    eyes_on(head, M, hd, 0.058, 0.292, (0.029, 0.033, 0.020))
    for s in (1, -1):
        root = hd.surf(V((0.70 * s, -0.10, 0.70)))
        nrm = V((0.55 * s, -0.75, 0.35)).normalized()
        head.ellipsoid(root + V((0.012 * s, -0.012, -0.008)), (0.046, 0.040, 0.012), M.mask, n=2,
                       rot=C.frame(nrm) @ C.rotz(-25 * s), cull=hd.inside_fn(0.003))
    blush(head, M, hd, 0.090, 0.252)
    head.ellipsoid(hd.c, hd.r, M.fur, n=5, cull=head.covered)
    return body, head, (0, -0.075, 0.215), (0, -0.11, 0.53)


# ---------------------------------------------------------------------------
# Parrot: red / blue / yellow, standing upright (it hops)
# ---------------------------------------------------------------------------
def parrot():
    M = base_mats(red="#E94B3C", blue="#3D8BFF", yellow="#FFC72C", ivory="#F6EEDF", beak2="#3B2A3A",
                  face="#FFFFFF", feet="#8A96A3")
    body = C.Part("body")
    trunk = C.Ell((0, 0.012, 0.175), (0.084, 0.082, 0.112))
    body.ellipsoid(trunk.c, trunk.r, M.red, n=5, rot=C.rotx(-10))

    def wing_mat(u):
        if u.z > 0.40:
            return M.red
        return M.yellow if u.z > -0.05 else M.blue
    for s in (1, -1):
        body.ellipsoid((0.074 * s, 0.035, 0.165), (0.030, 0.075, 0.104), None, n=3,
                       rot=C.rotx(-22) @ C.roty(-8 * s), mat_fn=wing_mat, cull=trunk.inside_fn(0.004))
    for dx, ln, m in ((0.0, 1.0, M.blue), (0.022, 0.78, M.yellow), (-0.022, 0.78, M.yellow)):
        ctrl = [(dx * 0.5, 0.06, 0.10), (dx, 0.13, 0.05), (dx * 1.3, 0.13 + 0.09 * ln, 0.012 + 0.01 * (1 - ln))]
        body.tube(ctrl, [(0.0, 0.0, M.red), (0.1, 0.028, M.red), (0.32, 0.030, m), (0.8, 0.026, m),
                         (1.0, 0.0, m)], seg=8, hint=(0, 0, 1), scale_keys=[(0.0, 0.35, 1.0), (1.0, 0.3, 1.0)])
    for s in (1, -1):
        stub_leg(body, (0.032 * s, -0.005, 0.085), (0.034 * s, -0.012, 0.012), 0.011, M.feet,
                 (0.020, 0.036, 0.011), M.feet, foot_fwd=0.008, seg=6)

    head = C.Part("head")
    hd = C.Ell((0, -0.026, 0.328), (0.078, 0.074, 0.074))
    for s in (1, -1):
        C.decal(head, hd, 0.040 * s, 0.336, 0.032, 0.030, M.face, h=0.003, seg=10)
    # short hooked beak: ivory upper mandible curling down over a dark lower one
    beak = [(0, -0.062, 0.334), (0, -0.098, 0.330), (0, -0.117, 0.314), (0, -0.112, 0.292)]
    head.tube(beak, [(0.0, 0.0, M.ivory), (0.08, 0.026, M.ivory), (0.4, 0.024, M.ivory), (0.75, 0.014, M.ivory),
                     (0.92, 0.007, M.ivory), (1.0, 0.0, M.ivory)], seg=8, hint=(1, 0, 0),
              scale_keys=[(0.0, 0.85, 1.0), (1.0, 0.75, 1.0)], cull=hd.inside_fn(0.003))
    head.ellipsoid((0, -0.088, 0.302), (0.020, 0.020, 0.014), M.beak2, uv=(8, 4), cull=hd.inside_fn(0.003))
    eyes_on(head, M, hd, 0.040, 0.338, (0.018, 0.021, 0.013), proud=0.007, hl=0.45)
    for dx, ln in ((0.0, 1.0), (0.016, 0.75), (-0.016, 0.75)):
        r0 = hd.surf(V((dx * 3, 0.15, 1.0)))
        ctrl = [r0 - V((0, 0, 0.015)), r0 + V((dx, 0.01, 0.03 * ln)), r0 + V((dx * 1.5, 0.04, 0.05 * ln))]
        head.tube(ctrl, [(0.0, 0.0, M.red), (0.1, 0.014, M.red), (0.6, 0.011, M.red), (1.0, 0.0, M.red)], seg=6,
                  cull=hd.inside_fn(0.003))
    blush(head, M, hd, 0.050, 0.306, a=0.016, b=0.011)
    head.ellipsoid(hd.c, hd.r, M.red, n=4, cull=head.covered)
    return body, head, (0, -0.012, 0.268), (0, -0.02, 0.55)


# ---------------------------------------------------------------------------
# Hamster: orange/white ball
# ---------------------------------------------------------------------------
def hamster():
    M = base_mats(fur="#F4A24C", white="#FFF6EA", pink="#FF9DB6", nose="#FF7F9E")
    body = C.Part("body")
    ball = C.Ell((0, 0.02, 0.118), (0.122, 0.132, 0.116))
    C.decal(body, ball, 0, 0.085, 0.092, 0.075, M.white, h=0.004, seg=14)
    for x, y in ((0.052, -0.088), (0.074, 0.075)):
        for s in (1, -1):
            body.ellipsoid((x * s, y, 0.014), (0.024, 0.030, 0.020), M.pink, uv=(8, 3), zmin=0.0)
    body.ellipsoid((0, 0.150, 0.085), (0.020, 0.020, 0.018), M.fur, uv=(8, 4))
    body.ellipsoid(ball.c, ball.r, M.fur, n=5, cull=body.covered)

    head = C.Part("head")
    hd = C.Ell((0, -0.080, 0.205), (0.100, 0.090, 0.086))
    C.decal(head, hd, 0, 0.178, 0.078, 0.048, M.white, h=0.004, seg=16)
    for s in (1, -1):
        c = (0.054 * s, -0.142, 0.176)
        head.ellipsoid(c, (0.040, 0.034, 0.033), M.white, n=2, cull=hd.inside_fn(0.003))
        C.cover_ell(head, c, (0.040, 0.034, 0.033))
    C.nose(head, C.Ell((0, -0.150, 0.200), (0.04, 0.03, 0.03)), M.nose, z=0.204, r=(0.014, 0.010, 0.010),
           proud=0.004)
    head.ellipsoid((0, -0.170, 0.167), (0.012, 0.006, 0.012), M.white, uv=(6, 3))
    eyes_on(head, M, hd, 0.046, 0.222, (0.022, 0.026, 0.016), proud=0.008, hl=0.5)
    for s in (1, -1):
        C.round_ear(head, M, s, (0.62, -0.05, 0.78), 0.032, M.fur, M.pink, thick=0.016, host=hd, n=(2, 2))
    blush(head, M, hd, 0.075, 0.200, a=0.018, b=0.013)
    head.ellipsoid(hd.c, hd.r, M.fur, n=5, cull=head.covered)
    return body, head, (0, -0.060, 0.170), (0, -0.07, 0.44)


# ---------------------------------------------------------------------------
# Kitten: sitting calico (white with orange and black patches)
# ---------------------------------------------------------------------------
def kitten():
    M = base_mats(white="#FFF8F2", orange="#F29A45", black="#2E2833", pink="#FFA9BF", nose="#FF8FA8")
    body = C.Part("body")
    trunk = C.Ell((0, 0.03, 0.125), (0.085, 0.095, 0.112))
    surf_decal(body, trunk, (0.55, 0.55, 0.60), 0.055, 0.050, M.orange, roll=20, seg=10, rho=(0.9, 1.0))
    surf_decal(body, trunk, (-0.75, 0.45, 0.20), 0.050, 0.060, M.black, roll=-10, seg=10, rho=(0.9, 1.0))
    surf_decal(body, trunk, (0.10, 0.95, -0.05), 0.045, 0.040, M.orange, seg=10, rho=(0.9, 1.0))
    for s in (1, -1):
        stub_leg(body, (0.034 * s, -0.050, 0.12), (0.038 * s, -0.072, 0.012), 0.024, M.white,
                 (0.026, 0.034, 0.018), M.white, foot_fwd=0.01)
        body.ellipsoid((0.066 * s, 0.060, 0.050), (0.040, 0.066, 0.050), M.white if s > 0 else M.orange, n=2,
                       zmin=0.0, cull=trunk.inside_fn(0.004))
    tail = [(0.0, 0.110, 0.040), (0.070, 0.115, 0.022), (0.110, 0.030, 0.020), (0.090, -0.060, 0.024),
            (0.055, -0.095, 0.028)]
    body.tube(tail, [(0.0, 0.0, M.orange), (0.04, 0.020, M.orange), (0.4, 0.019, M.orange), (0.75, 0.018, M.black),
                     (0.95, 0.015, M.black), (1.0, 0.0, M.black)], seg=6, hint=(0, 0, 1))
    body.ellipsoid(trunk.c, trunk.r, M.white, n=4, cull=body.covered)

    head = C.Part("head")
    hd = C.Ell((0, -0.020, 0.300), (0.100, 0.088, 0.085))
    C.decal(head, hd, 0.050, 0.330, 0.052, 0.050, M.orange, h=0.003, seg=12, roll=15, rho=(0.9, 1.0))
    C.decal(head, hd, 0, 0, 0.060, 0.045, M.black, h=0.003, seg=12, roll=20, rho=(0.9, 1.0),
            center=hd.surf(V((-0.55, 0.0, 0.75))))
    for s in (1, -1):
        c = (0.017 * s, -0.098, 0.279)
        head.ellipsoid(c, (0.024, 0.019, 0.020), M.white, uv=(8, 3), cull=hd.inside_fn(0.003))
        C.cover_ell(head, c, (0.024, 0.019, 0.020))
    C.nose(head, C.Ell((0, -0.096, 0.294), (0.03, 0.02, 0.02)), M.nose, z=0.297, r=(0.012, 0.008, 0.009),
           proud=0.004)
    eyes_on(head, M, hd, 0.045, 0.313, (0.023, 0.028, 0.016), proud=0.008, hl=0.5)
    for s, m in ((1, M.orange), (-1, M.black)):
        C.ear(head, M, s, (0.58, 0.02, 0.81), (0.50, 0.06, 1.0), 0.072, 0.044, 0.40, C.EAR_POINTED, m, M.pink,
              turn=0.25, embed=0.03, host=hd, seg=6)
    blush(head, M, hd, 0.070, 0.285, a=0.018, b=0.013)
    head.ellipsoid(hd.c, hd.r, M.white, n=5, cull=head.covered)
    return body, head, (0, -0.005, 0.225), (0, -0.02, 0.55)


# ---------------------------------------------------------------------------
# Robot: rounded white/teal bot, one antenna, screen face with glowing eyes
# ---------------------------------------------------------------------------
def robot():
    M = base_mats(shell="#F4F6F8", teal="teal", screen="#1E2A3A", knob="mustard")
    eye = B.mat("#3DF2FF", rough=0.2, emit=1.5)
    bulb = B.mat("tomato", rough=0.3, emit=0.8)
    body = C.Part("body")
    prof = [(0.000, 0.060, M.shell), (0.080, 0.062, M.shell), (0.104, 0.080, M.shell), (0.110, 0.118, M.teal),
            (0.111, 0.132, M.shell), (0.106, 0.190, M.shell), (0.086, 0.214, M.shell), (0.000, 0.220, M.shell)]
    H.lathe(body, prof, seg=20, rmod=squircle)
    body.ellipsoid((0, -0.108, 0.160), (0.048, 0.014, 0.030), M.teal, uv=(12, 5))
    body.ellipsoid((0, -0.119, 0.162), (0.011, 0.006, 0.011), M.knob, uv=(8, 4))
    for s in (1, -1):
        stub_leg(body, (0.050 * s, 0.0, 0.075), (0.052 * s, -0.004, 0.024), 0.022, M.teal,
                 (0.040, 0.054, 0.030), M.shell, foot_fwd=0.01)
        arm = [(0.100 * s, 0.0, 0.175), (0.128 * s, -0.004, 0.150), (0.140 * s, -0.012, 0.112)]
        body.tube(arm, [(0.0, 0.0, M.teal), (0.15, 0.020, M.teal), (0.75, 0.018, M.teal), (0.85, 0.024, M.shell),
                        (1.0, 0.026, M.shell), (1.12, 0.0, M.shell)], seg=8)

    head = C.Part("head")
    head.sweep([(0, 0, 0.205), (0, 0, 0.212), (0, 0, 0.245), (0, 0, 0.25)], [0.0, 0.034, 0.030, 0.0], M.teal, seg=12)
    hprof = [(0.000, 0.240, M.shell), (0.090, 0.242, M.shell), (0.116, 0.262, M.shell), (0.122, 0.305, M.shell),
             (0.117, 0.362, M.shell), (0.096, 0.388, M.shell), (0.000, 0.394, M.shell)]
    H.lathe(head, hprof, seg=20, ys=0.92, rmod=squircle)
    head.ellipsoid((0, -0.100, 0.314), (0.090, 0.024, 0.056), M.screen, n=3)
    for s in (1, -1):
        head.ellipsoid((0.034 * s, -0.124, 0.322), (0.013, 0.006, 0.020), eye, uv=(10, 4))
        head.ellipsoid((0.124 * s, 0.0, 0.312), (0.012, 0.030, 0.030), M.teal, uv=(10, 4))
    head.line([(-0.020, -0.123, 0.292), (-0.008, -0.1245, 0.284), (0.008, -0.1245, 0.284), (0.020, -0.123, 0.292)],
              0.0035, eye, seg=5)
    head.sweep([(0.03, 0.02, 0.385), (0.03, 0.02, 0.392), (0.034, 0.024, 0.44), (0.036, 0.026, 0.462)],
               [0.0, 0.010, 0.007, 0.0], M.teal, seg=6)
    head.ellipsoid((0.036, 0.026, 0.472), (0.020, 0.020, 0.020), bulb, uv=(10, 6))
    return body, head, (0, 0, 0.225), (0, 0, 0.60)


BUILDERS = {"pug": pug, "parrot": parrot, "hamster": hamster, "kitten": kitten, "robot": robot}


def build_pet(name):
    body, head, neck, bubble = BUILDERS[name]()
    bo = body.finish((0.0, 0.0, 0.0))
    ho = head.finish(neck)
    C.kid(ho, bo)
    bpy.context.view_layer.update()
    return [bo, ho], bubble


def export_pet(name, preview=True):
    B.reset_scene()
    parts, bubble = build_pet(name)
    B.anchor("bubble", bubble)
    bpy.context.view_layer.update()
    path = os.path.join(B.ASSET_MODELS, "pet_%s.bvm" % name)
    with C.smoothed_ao():
        tris = B.export_bvm(path, parts, ao=True, ao_strength=AO_STRENGTH, ao_distance=AO_DIST, ao_samples=48)
    if preview:
        C.vc_preview(parts, ao_strength=AO_STRENGTH)
        C.render_view(os.path.join(C.PREVIEW_CHAR_DIR, "pet_%s.png" % name), target=(0, 0, 0.22), dist=1.75,
                      lens=85, res=(560, 560), samples=48, cam_dir=(0.62, -1.0, 0.50))
    return tris


def pets_lineup(path=None):
    B.reset_scene()
    allp = []
    n = len(PETS)
    for i, name in enumerate(PETS):
        parts, _ = build_pet(name)
        parts[0].location.x += (i - (n - 1) / 2) * 0.42
        allp += parts
    bpy.context.view_layer.update()
    C.bake_ao_smooth(allp, samples=32, distance=AO_DIST)
    C.vc_preview(allp, ao_strength=AO_STRENGTH)
    C.render_view(path or os.path.join(C.PREVIEW_CHAR_DIR, "pets_lineup.png"), target=(0, 0, 0.24), dist=4.3,
                  lens=60, res=(1600, 600), samples=64, cam_dir=(0.18, -1.0, 0.40), rig="sun")


def main():
    args = B.script_args()
    flags = {a for a in args if a.startswith("--")}
    names = [a for a in args if not a.startswith("--")] or PETS
    report = []
    for n in names:
        report.append((n, export_pet(n, preview="--no-preview" not in flags)))
    if not [a for a in args if not a.startswith("--")] and "--no-preview" not in flags:
        pets_lineup()
    for n, t in report:
        print("PET %-8s tris=%d %s" % (n, t, "OK" if t <= BUDGET else "OVER BUDGET"))


if __name__ == "__main__":
    main()
