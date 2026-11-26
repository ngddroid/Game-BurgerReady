"""synth.py - Bun Voyage! procedural audio generator (entry point).

Run through Blender's bundled Python (it ships numpy):
  ~/Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup --python-exit-code 1 \
      --python tools/audio/synth.py -- [all|sfx|music|check] [--only name1,name2]

  all    (default) render SFX + music, then run the QC report
  sfx    render only the SFX (then QC)
  music  render only the music (then QC)
  check  QC the files already on disk (no rendering)
  --only restrict rendering to the listed names (QC still covers every file on disk)
"""
import os
import sys
import time

try:
    HERE = os.path.dirname(os.path.abspath(__file__))
except NameError:  # pragma: no cover - some embedders don't set __file__
    HERE = os.path.dirname(os.path.abspath(sys.argv[sys.argv.index("--python") + 1]))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

import dsp as D  # noqa: E402
import music as M  # noqa: E402
import qc as Q  # noqa: E402
import sfx as S  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SFX_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "audio", "sfx")
MUSIC_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "audio", "music")
SFX_LIST = os.path.join(HERE, "sfx_list.txt")

TICKS = ["ui_spin_tick", "ui_coin_tick", "timer_tick", "pad_fill"]  # must be mostly > 1.5 kHz
LOW_SFX = ["footstep1", "footstep2", "drop"]  # must be mostly < 1 kHz
SIZE_BUDGET = 16_000_000


def parse_args(argv):
    args = argv[argv.index("--") + 1:] if "--" in argv else argv[1:]
    mode, only = "all", None
    i = 0
    while i < len(args):
        a = args[i]
        if a in ("all", "sfx", "music", "check"):
            mode = a
        elif a == "--only":
            only = set(s.strip() for s in args[i + 1].split(",") if s.strip())
            i += 1
        elif a.startswith("--only="):
            only = set(s.strip() for s in a.split("=", 1)[1].split(",") if s.strip())
        else:
            raise SystemExit("unknown argument: %s" % a)
        i += 1
    return mode, only


def fmt(v, f="%.2f"):
    return "-" if v is None else f % v


def main():
    mode, only = parse_args(sys.argv)
    t0 = time.time()
    music_runs = {}
    if mode in ("all", "sfx"):
        print("Rendering SFX ->", os.path.relpath(SFX_DIR, ROOT))
        S.render_all(SFX_LIST, SFX_DIR, only)
    if mode in ("all", "music"):
        print("Rendering music ->", os.path.relpath(MUSIC_DIR, ROOT))
        music_runs = M.render_all(MUSIC_DIR, only)
    t_render = time.time() - t0

    failures = []

    def check(cond, msg):
        if not cond:
            failures.append(msg)
        return cond

    # ------------------------------------------------------------------ SFX QC
    entries = S.parse_list(SFX_LIST)
    sfx_rows = []
    total_size = 0
    for name, dur, _ in entries:
        path = os.path.join(SFX_DIR, name + ".wav")
        if not check(os.path.exists(path), "missing " + path):
            continue
        st = Q.file_stats(path)
        total_size += st["size"]
        x = st["x"][:, 0]
        want_peak = -4.0 if name.startswith("ui_") else -1.0
        check(st["sr"] == S.SR and st["ch"] == 1, "%s: format %d Hz x%d" % (name, st["sr"], st["ch"]))
        check(st["n"] == D.nsamp(dur, S.SR), "%s: %d samples, want %d" % (name, st["n"], D.nsamp(dur, S.SR)))
        check(abs(st["peak_db"] - want_peak) < 0.3, "%s: peak %.2f dBFS (want %.0f)" % (name, st["peak_db"], want_peak))
        check(st["dc"] < 1e-3, "%s: DC %.5f" % (name, st["dc"]))
        check(st["clip"] == 0 and st["nan"] == 0, "%s: clip/nan" % name)
        note = ""
        if name in S.LOOPS:
            sm = Q.seam(st["x"], st["sr"])
            ok = check(Q.seam_ok(sm), "%s: loop seam %s" % (name, sm))
            note = "loop jump %.2fx, wrap %+.1f dB (ref p90 %.1f) %s" % (sm["jump_ratio"], sm["wrap_step_db"], sm["ref_p90_db"], "OK" if ok else "BAD")
        else:
            edge = max(abs(x[0]), abs(x[-1])) * 32768
            check(edge <= 4, "%s: edge samples not faded (%.0f LSB)" % (name, edge))
        if name in TICKS:
            hf = Q.band_fraction(x, st["sr"], 1500.0)
            check(hf >= 0.85, "%s: only %.0f%% energy > 1.5 kHz" % (name, 100 * hf))
            note = "HF>1.5k %.0f%% %s" % (100 * hf, "OK" if hf >= 0.85 else "BAD")
        if name in LOW_SFX:
            lf = Q.band_fraction(x, st["sr"], 0.0, 1000.0)
            check(lf >= 0.8, "%s: only %.0f%% energy < 1 kHz" % (name, 100 * lf))
            note = "LF<1k %.0f%% %s" % (100 * lf, "OK" if lf >= 0.8 else "BAD")
        cen = Q.centroid(x, st["sr"])
        sfx_rows.append((name, "%.3f" % dur, "%.3f" % st["dur"], fmt(st["peak_db"]), fmt(st["rms_db"], "%.1f"),
                         fmt(Q.max_short_rms_db(x, st["sr"]), "%.1f"), "%.0e" % st["dc"], st["clip"], st["nan"],
                         "%.0f" % cen, note))

    # ------------------------------------------------------------------ music QC
    mus_rows = []
    lengths = {}
    for name in M.TRACKS:
        path = os.path.join(MUSIC_DIR, name + ".wav")
        if not check(os.path.exists(path), "missing " + path):
            continue
        st = Q.file_stats(path)
        total_size += st["size"]
        lengths[name] = st["n"]
        check(st["sr"] == M.SR and st["ch"] == 2, "%s: format %d Hz x%d" % (name, st["sr"], st["ch"]))
        check(st["peak_db"] <= -0.9, "%s: peak %.2f dBFS" % (name, st["peak_db"]))
        check(st["dc"] < 1e-3 and st["clip"] == 0 and st["nan"] == 0, "%s: dc/clip/nan" % name)
        is_loop = name not in ("victory_jingle", "fail_jingle")
        seam_txt = "-"
        if is_loop:
            run = music_runs.get(name)
            spb = run["song"].spb if run else None
            if spb is None:  # derive the bar grid from the known tempo when only checking
                spb = int(round(M.SR * 60.0 / {"menu_theme": M.MENU_BPM, "boss_theme": M.BOSS_BPM}.get(name, M.LEVEL_BPM)))
            bars = list(range(4 * spb, st["n"] - 1, 4 * spb))
            sm = Q.seam(st["x"], st["sr"], boundaries=bars)
            ok = check(Q.seam_ok(sm), "%s: loop seam %s" % (name, sm))
            seam_txt = "jump %.2fx (%.0f LSB), wrap %+.1f dB vs bar-line p90 %.1f %s" % (
                sm["jump_ratio"], sm["jump_lsb"], sm["wrap_step_db"], sm["ref_p90_db"], "OK" if ok else "BAD")
        else:
            edge = np.max(np.abs(st["x"][[0, -1]])) * 32768
            check(edge <= 4, "%s: edges not faded" % name)
        bass_txt = "-"
        run = music_runs.get(name)
        if run and "bass" in run["song"].keep:
            fr = Q.band_fraction(run["song"].keep["bass"], M.SR, 0.0, 250.0)
            check(fr >= 0.85, "%s: bass only %.0f%% < 250 Hz" % (name, 100 * fr))
            bass_txt = "%.0f%%" % (100 * fr)
        mono = st["x"].mean(axis=1)
        low = Q.band_fraction(mono, st["sr"], 0.0, 250.0)
        bpm = {"menu_theme": M.MENU_BPM, "boss_theme": M.BOSS_BPM, "victory_jingle": 150, "fail_jingle": 100}.get(name, M.LEVEL_BPM)
        mus_rows.append((name, "%.3f" % st["dur"], st["n"], bpm, fmt(st["peak_db"]), fmt(st["rms_db"], "%.1f"),
                         "%.0e" % st["dc"], st["clip"], fmt(st["corr"]), bass_txt, "%.0f%%" % (100 * low), seam_txt))
    if "level_calm" in lengths and "level_rush" in lengths:
        check(lengths["level_calm"] == lengths["level_rush"],
              "level_calm (%d) and level_rush (%d) lengths differ" % (lengths["level_calm"], lengths["level_rush"]))

    # identical shared layers -> correlation of calm vs rush (informative)
    pair_txt = ""
    pc, pr = os.path.join(MUSIC_DIR, "level_calm.wav"), os.path.join(MUSIC_DIR, "level_rush.wav")
    if os.path.exists(pc) and os.path.exists(pr):
        a, _, _ = D.read_wav(pc)
        b, _, _ = D.read_wav(pr)
        if len(a) == len(b):
            c = float(np.corrcoef(a.mean(axis=1), b.mean(axis=1))[0, 1])
            pair_txt = "level_calm/level_rush: %d == %d samples (%.4f s), RMS %.1f vs %.1f dBFS, waveform corr %.2f" % (
                len(a), len(b), len(a) / M.SR, D.lin_to_db(D.rms(a)), D.lin_to_db(D.rms(b)), c)

    check(total_size <= SIZE_BUDGET, "total size %.2f MB over budget" % (total_size / 1e6))

    # ------------------------------------------------------------------ report
    print()
    print("SFX (44.1 kHz mono 16-bit)")
    print(Q.table(sfx_rows, ["name", "target", "dur s", "peak dB", "rms dB", "max50ms", "dc", "clip", "nan", "centroid", "check"]))
    print()
    print("MUSIC (22.05 kHz stereo 16-bit)")
    print(Q.table(mus_rows, ["name", "dur s", "samples", "bpm", "peak dB", "rms dB", "dc", "clip", "L/R corr",
                             "bass<250", "mix<250", "loop seam"]))
    if music_runs:
        print()
        print("Music stem levels (target active RMS dBFS before mastering, active fraction):")
        for name, run in music_runs.items():
            print("  %-15s %s" % (name, ", ".join("%s %.0f (%.0f%%)" % (s, l, 100 * a) for s, l, a in run["song"].stems)))
    print()
    if pair_txt:
        print(pair_txt)
    print("Total audio size: %.2f MB (%d files), render time %.1f s" % (total_size / 1e6, len(sfx_rows) + len(mus_rows), t_render))
    if failures:
        print("\nQC FAILURES (%d):" % len(failures))
        for f in failures:
            print("  - " + f)
        sys.exit(1)
    print("\nALL QC CHECKS PASSED")


if __name__ == "__main__":
    main()
