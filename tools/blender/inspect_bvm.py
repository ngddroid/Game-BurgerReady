"""
Inspect .bvm models (format documented in tools/blender/bvlib.py). Plain Python, no Blender/numpy.

    python3 -I tools/blender/inspect_bvm.py char_corgi counter      # detail for named models
    python3 -I tools/blender/inspect_bvm.py --all                   # one-line summary for every model

Names may be given with or without the ".bvm" suffix, or as a path. Coordinates are in engine
axes (x, y = up, z = toward the camera / customers). Useful after re-exporting a model to check
that part order, pivots and anchors still match docs/ASSET_CONTRACT.md (the engine relies on them).
"""
import os
import struct
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MODELS = os.path.join(ROOT, "app", "src", "main", "assets", "models")


def parse(path):
    d = open(path, "rb").read()
    assert d[:4] == b"BVM1", "bad magic in %s" % path
    o = 4
    (n,) = struct.unpack_from("<H", d, o); o += 2
    parts = []
    for _ in range(n):
        ln = d[o]; o += 1
        name = d[o:o + ln].decode(); o += ln
        (parent,) = struct.unpack_from("<h", d, o); o += 2
        pivot = struct.unpack_from("<3f", d, o); o += 12
        vc, ic = struct.unpack_from("<II", d, o); o += 8
        lo = [1e9] * 3; hi = [-1e9] * 3; tint = emit = 0
        for i in range(vc):
            b = o + i * 20
            p = struct.unpack_from("<3f", d, b)
            for k in range(3):
                lo[k] = min(lo[k], p[k] + pivot[k]); hi[k] = max(hi[k], p[k] + pivot[k])
            if d[b + 15] > 0:
                emit += 1
            if d[b + 19] > 0:
                tint += 1
        o += vc * 20 + ic * 2
        parts.append(dict(name=name, parent=parent, pivot=pivot, verts=vc, tris=ic // 3, lo=lo, hi=hi, tint=tint, emit=emit))
    anchors = {}
    if o + 2 <= len(d):
        (na,) = struct.unpack_from("<H", d, o); o += 2
        for _ in range(na):
            ln = d[o]; o += 1
            nm = d[o:o + ln].decode(); o += ln
            anchors[nm] = struct.unpack_from("<3f", d, o); o += 12
    return parts, anchors


def fmt(v):
    return "(%s)" % ", ".join("%.3f" % x for x in v)


def resolve(arg):
    if os.path.exists(arg):
        return arg
    name = arg if arg.endswith(".bvm") else arg + ".bvm"
    return os.path.join(MODELS, name)


def main(args):
    if not args:
        print(__doc__)
        return 1
    if args == ["--all"]:
        print("%-34s %5s %6s %5s %5s  %s" % ("model", "parts", "tris", "tint", "emit", "size x*y*z (m)"))
        for f in sorted(os.listdir(MODELS)):
            if not f.endswith(".bvm"):
                continue
            parts, _ = parse(os.path.join(MODELS, f))
            lo = [min(p["lo"][k] for p in parts) for k in range(3)]
            hi = [max(p["hi"][k] for p in parts) for k in range(3)]
            print("%-34s %5d %6d %5d %5d  %.2f*%.2f*%.2f" % (f[:-4], len(parts), sum(p["tris"] for p in parts),
                  sum(p["tint"] for p in parts), sum(p["emit"] for p in parts), hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]))
        return 0
    for a in args:
        path = resolve(a)
        parts, anchors = parse(path)
        print("== %s  (%d parts, %d tris)" % (os.path.basename(path), len(parts), sum(p["tris"] for p in parts)))
        for i, p in enumerate(parts):
            print("  [%d] %-8s parent=%-2d pivot=%s tris=%-5d tint=%-4d emit=%-4d bbox y %.3f..%.3f"
                  % (i, p["name"], p["parent"], fmt(p["pivot"]), p["tris"], p["tint"], p["emit"], p["lo"][1], p["hi"][1]))
        for k, v in anchors.items():
            print("  @%-10s %s" % (k, fmt(v)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
