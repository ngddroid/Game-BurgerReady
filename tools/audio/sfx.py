"""sfx.py - procedural recipes for every sound in sfx_list.txt (44.1 kHz mono).

Each recipe is `fn(n, sr, rng) -> float array` producing exactly the target length. Target
durations come from sfx_list.txt; the renderer normalises (UI -4 dBFS, others -1 dBFS),
applies the 5 ms fades (loops excepted) and writes 16-bit WAVs.
"""
import math
import os
import re

import numpy as np

import dsp as D
import instruments as I

SR = 44100
RECIPES = {}
LOOPS = {"sizzle_loop", "fryer_loop"}
# Short transient ticks keep a 1.5 ms fade-in (a 5 ms ramp would smear their attack);
# every file still gets the 5 ms fade-out.
SHORT_FADE_IN = {"ui_spin_tick", "ui_coin_tick", "ui_toggle", "pad_fill", "timer_tick"}


def recipe(name):
    def deco(fn):
        RECIPES[name] = fn
        return fn
    return deco


def parse_list(path):
    """Returns [(name, seconds, description)] from sfx_list.txt."""
    out = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            name, desc = [s.strip() for s in line.split("|", 1)]
            m = re.search(r"([\d.]+)\s*(ms|s)\b\s*$", desc)
            if not m:
                raise ValueError("no duration in: " + line)
            v = float(m.group(1))
            out.append((name, v / 1000.0 if m.group(2) == "ms" else v, desc))
    return out


# =========================================================================== building blocks
def T(n, sr):
    return D.tvec(n, sr)


def blip(n, sr, f0, f1, t_sweep, t60, attack=0.0015, harm=(1.0, 0.15), curve="exp", at=0.0):
    """Pitched blip with a pitch sweep and exponential decay, placed `at` seconds in."""
    m = n - D.nsamp(at, sr)
    f = D.sweep(f0, f1, m, sr, t_sweep, curve)
    y = D.harmonics(f, m, sr, harm) * D.env_ad(m, sr, attack, t60)
    return D.place(np.zeros(n), y, D.nsamp(at, sr))


def thump(n, sr, f0=160.0, f1=55.0, t_sweep=0.05, t60=0.2, at=0.0):
    return blip(n, sr, f0, f1, t_sweep, t60, attack=0.001, harm=(1.0,), at=at)


def click(n, sr, rng, fc=4000.0, q=0.8, tau=0.0015, at=0.0, mode="bp"):
    m = n - D.nsamp(at, sr)
    t = T(m, sr)
    y = D.svf(D.white(m, rng) * np.exp(-t / tau), fc, sr, q, mode)
    return D.place(np.zeros(n), y, D.nsamp(at, sr))


def noise_burst(n, sr, rng, attack, t60, lo=None, hi=None, at=0.0):
    m = n - D.nsamp(at, sr)
    nz = D.white(m, rng)
    if lo:
        nz = D.svf(nz, lo, sr, 0.7, "hp")
    if hi:
        nz = D.svf(nz, hi, sr, 0.7, "lp")
    y = nz * D.env_ad(m, sr, attack, t60)
    return D.place(np.zeros(n), y, D.nsamp(at, sr))


def add(dst, src, at, sr, gain=1.0):
    return D.place_s(dst, src, at, sr, gain)


PENTA_HIGH = [D.midi_hz(m) for m in (84, 86, 88, 91, 93, 96, 98, 100, 103, 105, 108, 110, 112, 115, 117, 120)]


def sparkles(n, sr, rng, t0, t1, count, fmin=2500.0, fmax=9500.0, t60=(0.05, 0.18), decay=0.6, gain=1.0):
    """Random tiny pentatonic glints between t0 and t1 seconds (front-loaded)."""
    out = np.zeros(n)
    pool = [f for f in PENTA_HIGH if fmin <= f <= fmax] or [fmin]
    for _ in range(count):
        u = rng.random() ** 1.4
        t = t0 + (t1 - t0) * u
        f = pool[int(rng.integers(len(pool)))] * D.cents(1.0, rng.uniform(-8, 8))
        d = rng.uniform(*t60)
        m = D.nsamp(d * 1.05, sr)
        g = rng.uniform(0.35, 1.0) * (1.0 - decay * u)
        grain = D.modal(m, sr, [f, 2.756 * f], [1.0, 0.2], [d, d * 0.4], rng)
        add(out, grain, t, sr, g * gain)
    return out


def bell_note(n, sr, f, at, t60=0.5, vel=1.0, ratio=3.0, index=1.0):
    y = I.bell(f, vel, sr, t60=t60, ratio=ratio, index=index, idx_tau=0.06)
    return D.place_s(np.zeros(n), y, at, sr)


def chime_note(n, sr, f, at, t60=0.5, vel=1.0):
    return D.place_s(np.zeros(n), I.chime(f, vel, sr, t60), at, sr)


def bp_sweep_noise(n, sr, rng, fc_curve, q=1.2):
    return D.svf(D.white(n, rng), fc_curve, sr, q, "bp")


def voice(n, sr, rng, f0, formants, breath=0.04, tilt=3000.0):
    """Formant voice: band-limited saw glottal source -> parallel time-varying bandpasses.
    formants: list of (fc_array_or_scalar, q, gain)."""
    src = D.saw(f0, n, sr)
    src = D.onepole_lp(src, tilt, sr) + breath * D.white(n, rng)
    y = np.zeros(n)
    for fc, q, g in formants:
        y += g * D.svf(src, fc, sr, q, "bp")
    return y


def interp_curve(n, sr, pts):
    return D.env_points(pts, n, sr, "smooth")


def ks_note(f, dur, sr, rng, t60=0.5, bright=0.8, damp=0.35):
    y = D.ks_pluck(f, dur, sr, rng, t60=t60, damp=damp, bright=bright)
    return D.dc_block(y, sr, 30.0)


def small_room(x, sr, rt60=0.5, mix=0.18):
    return D.schroeder_reverb(x, sr, rt60=rt60, mix=mix, damp_hz=6000.0)[:len(x)]


def metal_hit(n, sr, rng, f, at=0.0, t60=0.08, ratios=(1.0, 1.58, 2.36, 3.1, 4.2), amps=(1.0, 0.6, 0.45, 0.3, 0.2)):
    m = n - D.nsamp(at, sr)
    y = D.modal(m, sr, [f * r for r in ratios], amps, [t60 * (1.0 - 0.12 * i) for i in range(len(ratios))], rng)
    y += 0.3 * D.svf(D.white(m, rng), min(f * 2, 0.45 * sr), sr, 0.8, "bp") * np.exp(-T(m, sr) / 0.0015)
    return D.place(np.zeros(n), y, D.nsamp(at, sr))


# =========================================================================== UI
@recipe("ui_tap")
def ui_tap(n, sr, rng):
    y = blip(n, sr, 930.0, 560.0, 0.022, 0.05, attack=0.0015, harm=(1.0, 0.14, 0.03))
    y += 0.3 * thump(n, sr, 260.0, 180.0, 0.02, 0.03)
    return y


@recipe("ui_back")
def ui_back(n, sr, rng):
    y = blip(n, sr, 600.0, 350.0, 0.028, 0.055, attack=0.002, harm=(1.0, 0.08))
    y += 0.25 * thump(n, sr, 190.0, 130.0, 0.025, 0.035)
    return D.svf(y, 2500.0, sr, 0.7, "lp")


@recipe("ui_tab")
def ui_tab(n, sr, rng):
    fc = D.sweep(1800.0, 7000.0, n, sr, 0.085)
    sw = bp_sweep_noise(n, sr, rng, fc, 1.3) * D.env_points([(0, 0), (0.03, 1), (0.07, 0.35), (0.1, 0), (1, 0)], n, sr, "smooth")
    pl = ks_note(D.midi_hz(88), 0.08, sr, rng, t60=0.09, bright=0.9, damp=0.3)  # E6
    y = 0.55 * sw
    add(y, pl * D.env_ad(len(pl), sr, 0.0005, 0.09), 0.042, sr, 0.8)
    return y


@recipe("ui_toggle")
def ui_toggle(n, sr, rng):
    y = click(n, sr, rng, 5200.0, 1.2, 0.0012) * 0.6
    y += blip(n, sr, 1500.0, 820.0, 0.012, 0.03, attack=0.0008, harm=(1.0, 0.1), at=0.004)
    return y


@recipe("ui_popup")
def ui_popup(n, sr, rng):
    t = T(n, sr)
    u = np.clip(t / 0.14, 0, 1) ** 1.5
    f = 260.0 * (980.0 / 260.0) ** u * (1.0 + 0.025 * np.sin(D.TWO_PI * 26.0 * t))
    env = D.env_points([(0, 0), (0.01, 1), (0.12, 0.9), (0.19, 0), (1, 0)], n, sr, "smooth")
    y = D.harmonics(f, n, sr, (1.0, 0.12)) * env
    y += 0.4 * blip(n, sr, 620.0, 1450.0, 0.06, 0.06, attack=0.004, at=0.1)
    return y


@recipe("ui_purchase")
def ui_purchase(n, sr, rng):
    y = chime_note(n, sr, D.midi_hz(91), 0.0, 0.3)            # G6
    y += 1.1 * chime_note(n, sr, D.midi_hz(96), 0.075, 0.33)  # C7
    y += 0.3 * sparkles(n, sr, rng, 0.06, 0.33, 12, 4000.0, 10000.0, (0.03, 0.09))
    y += 0.08 * noise_burst(n, sr, rng, 0.02, 0.2, lo=7000.0, at=0.07)
    return y


@recipe("ui_error")
def ui_error(n, sr, rng):
    y = np.zeros(n)
    for f, at, d in ((466.16, 0.0, 0.1), (369.99, 0.12, 0.115)):
        m = D.nsamp(d + 0.03, sr)
        ff = f * D.vibrato(m, sr, 9.0, 10.0, 0.0, 0.01)
        s = 0.6 * D.square(ff, m, sr, 0.5) + 0.4 * D.saw(ff * 1.003, m, sr)
        s = D.svf(s, 1300.0, sr, 0.8, "lp")
        s *= D.adsr(m, sr, 0.006, 0.08, 0.75, 0.025, gate=d)
        add(y, s, at, sr)
    return D.soft_clip(y * 0.9, 1.2)


@recipe("ui_reward")
def ui_reward(n, sr, rng):
    y = np.zeros(n)
    for i, m in enumerate((84, 88, 91, 96, 100)):  # C6 E6 G6 C7 E7
        y += (0.8 + 0.05 * i) * chime_note(n, sr, D.midi_hz(m), 0.06 * i, 0.32)
    y += 0.35 * sparkles(n, sr, rng, 0.2, 0.56, 16, 4500.0, 11000.0)
    return small_room(y, sr, 0.6, 0.15)


def _star(n, sr, rng, midi, tail=False):
    y = thump(n, sr, 170.0, 52.0, 0.06, 0.17)
    y += 0.35 * click(n, sr, rng, 1800.0, 0.7, 0.004, mode="lp")
    y += 0.25 * noise_burst(n, sr, rng, 0.001, 0.05, lo=200.0, hi=700.0)
    y += 0.9 * chime_note(n, sr, D.midi_hz(midi), 0.035, 0.29 if not tail else 0.42)
    if tail:
        y += 0.45 * chime_note(n, sr, D.midi_hz(midi + 12), 0.06, 0.3)
        y += 0.32 * sparkles(n, sr, rng, 0.12, 0.45, 16, 4000.0, 11000.0)
    return y


@recipe("ui_star1")
def ui_star1(n, sr, rng):
    return _star(n, sr, rng, 84)  # C6


@recipe("ui_star2")
def ui_star2(n, sr, rng):
    return _star(n, sr, rng, 88)  # E6


@recipe("ui_star3")
def ui_star3(n, sr, rng):
    return _star(n, sr, rng, 91, tail=True)  # G6


@recipe("ui_chest_open")
def ui_chest_open(n, sr, rng):
    t = T(n, sr)
    # stick-slip creak: impulse train with a wandering rate, exciting wooden body resonances
    rate = 70.0 + 35.0 * D.smooth_random(n, sr, 9.0, rng)
    ph = D.phase(rate, n, sr)
    imp = np.zeros(n)
    edges = np.nonzero(np.diff(np.floor(ph)) > 0)[0] + 1
    imp[edges] = rng.uniform(0.5, 1.0, len(edges))
    creak = D.resonator_bank(imp, sr, [480.0, 1120.0, 2350.0, 3900.0], [9.0, 10.0, 8.0, 6.0], [1.0, 0.8, 0.5, 0.25])
    creak *= D.env_points([(0, 0), (0.04, 1), (0.3, 0.9), (0.43, 0.0), (1, 0)], n, sr, "smooth")
    creak = D.svf(creak, 250.0, sr, 0.7, "hp")
    creak = np.tanh(4.0 * creak / (np.max(np.abs(creak)) + 1e-12))  # tame the stick-slip spikes
    y = 0.5 * creak
    y += 0.7 * thump(n, sr, 130.0, 70.0, 0.05, 0.12, at=0.40)
    y += 0.25 * noise_burst(n, sr, rng, 0.001, 0.06, hi=1200.0, at=0.40)
    # magical shimmer burst
    y += 0.22 * noise_burst(n, sr, rng, 0.04, 0.45, lo=4500.0, at=0.44)
    for i, m in enumerate((84, 86, 88, 91, 93, 96)):
        y += 0.32 * chime_note(n, sr, D.midi_hz(m), 0.45 + 0.03 * i, 0.4)
    y += 0.45 * sparkles(n, sr, rng, 0.46, 0.86, 26, 3500.0, 11000.0)
    return small_room(y, sr, 0.7, 0.14)


@recipe("ui_spin_tick")
def ui_spin_tick(n, sr, rng):
    y = D.modal(n, sr, [1850.0, 3150.0, 4720.0], [1.0, 0.6, 0.3], [0.016, 0.011, 0.007], rng)
    y += 0.5 * click(n, sr, rng, 3500.0, 0.7, 0.0008, mode="hp")
    return y


@recipe("ui_spin_win")
def ui_spin_win(n, sr, rng):
    y = np.zeros(n)
    notes = [(79, 0.0, 0.8), (84, 0.10, 0.85), (88, 0.20, 0.9), (91, 0.30, 1.0), (88, 0.45, 0.8), (91, 0.55, 0.9)]
    for m, at, v in notes:
        add(y, I.marimba(D.midi_hz(m), v, sr), at, sr, 0.7)
        add(y, I.glock(D.midi_hz(m), v * 0.5, sr, t60=0.5), at, sr, 0.35)
    # final chord C major, brass swell + marimba + bell
    for m in (72, 76, 79):
        add(y, I.brass(D.midi_hz(m), 0.8, sr, dur=0.42), 0.66, sr, 0.33)
    for m in (84, 88, 91, 96):
        add(y, I.marimba(D.midi_hz(m), 0.9, sr), 0.66, sr, 0.45)
    add(y, I.chime(D.midi_hz(96), 1.0, sr, 0.5), 0.66, sr, 0.5)
    y += 0.3 * sparkles(n, sr, rng, 0.68, 1.12, 14, 4000.0, 10000.0)
    y *= D.env_points([(0, 1), (1.0, 1), (1.2, 0)], n, sr, "smooth")
    return small_room(y, sr, 0.8, 0.14)


@recipe("ui_level_unlock")
def ui_level_unlock(n, sr, rng):
    y = 0.8 * thump(n, sr, 150.0, 80.0, 0.04, 0.12)
    y += 0.6 * metal_hit(n, sr, rng, 1150.0, 0.0, 0.07, (1.0, 2.3, 3.4, 4.9), (1.0, 0.6, 0.4, 0.2))
    y += 0.45 * metal_hit(n, sr, rng, 2300.0, 0.075, 0.04)
    y += 0.15 * noise_burst(n, sr, rng, 0.04, 0.35, lo=5000.0, at=0.14)
    for i, m in enumerate((88, 91, 95, 100)):  # E6 G6 B6 E7
        y += 0.4 * chime_note(n, sr, D.midi_hz(m), 0.15 + 0.045 * i, 0.32)
    y += 0.3 * sparkles(n, sr, rng, 0.2, 0.56, 14, 4000.0, 11000.0)
    return small_room(y, sr, 0.5, 0.12)


@recipe("ui_coin_tick")
def ui_coin_tick(n, sr, rng):
    return D.modal(n, sr, [3950.0, 6230.0, 8710.0, 10850.0], [1.0, 0.5, 0.35, 0.2], [0.022, 0.014, 0.009, 0.006], rng) \
        + 0.25 * click(n, sr, rng, 7000.0, 1.0, 0.0006)


# =========================================================================== core gameplay
@recipe("pickup")
def pickup(n, sr, rng):
    y = blip(n, sr, 430.0, 880.0, 0.022, 0.07, attack=0.0015, harm=(1.0, 0.15, 0.04))
    y += 0.15 * click(n, sr, rng, 2200.0, 1.0, 0.0015)
    return y


@recipe("drop")
def drop(n, sr, rng):
    y = thump(n, sr, 220.0, 85.0, 0.04, 0.075)
    y += 0.45 * noise_burst(n, sr, rng, 0.001, 0.035, hi=650.0)
    t = T(n, sr)
    y += 0.25 * D.svf(D.white(n, rng) * np.exp(-t / 0.012), D.sweep(900.0, 300.0, n, sr, 0.04), sr, 3.0, "bp")
    return y


@recipe("coin_collect")
def coin_collect(n, sr, rng):
    ratios, amps = (1.0, 2.41, 3.86), (1.0, 0.3, 0.12)
    y = 0.8 * metal_hit(n, sr, rng, 1760.0, 0.0, 0.05, ratios, amps)
    y += metal_hit(n, sr, rng, 2637.0, 0.035, 0.16, ratios, amps)
    return y


@recipe("coin_cascade")
def coin_cascade(n, sr, rng):
    y = np.zeros(n)
    for _ in range(26):
        u = rng.random() ** 1.5
        at = 0.6 * u
        f = rng.uniform(2200.0, 4600.0) if rng.random() > 0.25 else rng.uniform(1100.0, 1600.0)
        g = rng.uniform(0.4, 1.0) * (1.0 - 0.55 * u)
        y += g * metal_hit(n, sr, rng, f, at, rng.uniform(0.05, 0.14), (1.0, 1.58, 2.36, 3.1), (1.0, 0.5, 0.35, 0.2))
    y += 0.06 * noise_burst(n, sr, rng, 0.03, 0.4, lo=6000.0)
    return small_room(y, sr, 0.4, 0.1)


@recipe("cash_register")
def cash_register(n, sr, rng):
    y = 1.4 * metal_hit(n, sr, rng, 650.0, 0.0, 0.05, (1.0, 2.2, 4.4), (1.0, 0.6, 0.3))
    y += 0.5 * thump(n, sr, 120.0, 70.0, 0.03, 0.08)
    y += 0.9 * noise_burst(n, sr, rng, 0.001, 0.03, lo=900.0, hi=4000.0)
    y += 1.0 * metal_hit(n, sr, rng, 480.0, 0.045, 0.06, (1.0, 2.7, 3.9), (1.0, 0.5, 0.3))
    t = T(n, sr)
    m = n - D.nsamp(0.09, sr)
    tm = T(m, sr)
    ching = D.modal(m, sr, [2093.0 * r for r in (1.0, 2.02, 2.76, 3.9, 5.4)], [1.0, 0.5, 0.4, 0.2, 0.1],
                    [0.42, 0.33, 0.28, 0.18, 0.1], rng)
    ching += 0.5 * D.modal(m, sr, [3136.0, 3136.0 * 2.76], [1.0, 0.3], [0.3, 0.15], rng)
    ching *= 1.0 + 0.18 * np.sin(D.TWO_PI * 17.0 * tm)
    add(y, ching, 0.09, sr, 1.0)
    y += 0.2 * sparkles(n, sr, rng, 0.1, 0.4, 8, 5000.0, 11000.0)
    return y


@recipe("pad_fill")
def pad_fill(n, sr, rng):
    y = D.modal(n, sr, [2600.0, 4100.0, 6300.0], [1.0, 0.6, 0.3], [0.01, 0.007, 0.005], rng)
    y += 0.3 * click(n, sr, rng, 3000.0, 0.8, 0.0008)
    m = n - D.nsamp(0.016, sr)
    y2 = D.modal(m, sr, [2750.0, 4300.0, 6600.0], [1.0, 0.6, 0.3], [0.009, 0.006, 0.004], rng)
    add(y, y2, 0.016, sr, 0.6)
    return y


@recipe("build_pop")
def build_pop(n, sr, rng):
    t = T(n, sr)
    poof = D.svf(D.white(n, rng), D.sweep(5000.0, 500.0, n, sr, 0.22), sr, 0.7, "lp") * D.env_ad(n, sr, 0.015, 0.4)
    y = 0.9 * poof
    y += 0.7 * thump(n, sr, 120.0, 58.0, 0.06, 0.16)
    y += 0.5 * blip(n, sr, 320.0, 760.0, 0.03, 0.06)
    for m, at, v in ((79, 0.17, 0.8), (84, 0.29, 1.0)):  # ta-da G5 -> C6
        add(y, I.marimba(D.midi_hz(m), v, sr), at, sr, 0.55)
        y += 0.5 * v * chime_note(n, sr, D.midi_hz(m + 12), at, 0.38)
    y += 0.4 * sparkles(n, sr, rng, 0.2, 0.66, 22, 3500.0, 11000.0)
    return small_room(y, sr, 0.6, 0.14)


@recipe("upgrade")
def upgrade(n, sr, rng):
    t = T(n, sr)
    f = D.sweep(220.0, 880.0, n, sr, 0.45)
    s = np.zeros(n)
    for dc in (-9.0, 0.0, 9.0):
        s += D.saw(D.cents(1.0, dc) * f, n, sr, rng.random())
    s = D.svf(s / 3, D.sweep(500.0, 6000.0, n, sr, 0.45), sr, 1.6, "lp")
    s *= D.env_points([(0, 0), (0.03, 0.8), (0.42, 1.0), (0.5, 0.25), (0.69, 0), (1, 0)], n, sr, "smooth")
    y = 0.55 * s
    for i, m in enumerate((84, 88, 91)):
        y += 0.5 * chime_note(n, sr, D.midi_hz(m), 0.42 + 0.02 * i, 0.38)
    y += 0.2 * sparkles(n, sr, rng, 0.44, 0.66, 8, 5000.0, 11000.0)
    return small_room(y, sr, 0.5, 0.12)


@recipe("hire")
def hire(n, sr, rng):
    y = np.zeros(n)
    for (f0, f1, at, d) in ((1050.0, 1650.0, 0.0, 0.15), (1250.0, 2050.0, 0.18, 0.17)):
        m = D.nsamp(d + 0.03, sr)
        f = D.sweep(f0, f1, m, sr, d * 0.8, "smooth") * D.vibrato(m, sr, 7.0, 25.0, 0.03, 0.05)
        tone = D.harmonics(f, m, sr, (1.0, 0.05))
        breath = D.svf(D.white(m, rng), f, sr, 6.0, "bp")
        add(y, (tone + 0.25 * breath) * D.adsr(m, sr, 0.015, 0.1, 0.85, 0.03, gate=d), at, sr)
    y += 0.7 * blip(n, sr, 480.0, 1000.0, 0.015, 0.045, at=0.39)
    y += 0.2 * click(n, sr, rng, 2500.0, 1.0, 0.001, at=0.39)
    return y


@recipe("sizzle_loop")
def sizzle_loop(n, sr, rng):
    xf = D.nsamp(0.3, sr)
    m = n + xf
    t = T(m, sr)
    bed = D.fft_filter(rng.standard_normal(m), sr, lo=2500.0, hi=11000.0, order=2)
    bed /= np.max(np.abs(bed))
    am = 0.75 + 0.25 * D.smooth_random(m, sr, 7.0, rng)
    y = 0.45 * bed * am
    # crackles: tiny band-limited ticks at random frequencies
    for _ in range(int(55 * m / sr)):
        at = rng.uniform(0, m / sr)
        k = D.nsamp(0.008, sr)
        fc = rng.uniform(2500.0, 8000.0)
        g = rng.uniform(0.2, 1.0) ** 2
        tick = D.svf(D.white(k, rng) * np.exp(-T(k, sr) / rng.uniform(0.0006, 0.002)), fc, sr, 1.5, "bp")
        add(y, tick, at, sr, 1.4 * g)
    # fat spits: short bursts of brighter hiss
    for _ in range(int(5 * m / sr)):
        at = rng.uniform(0, m / sr)
        k = D.nsamp(0.09, sr)
        burst = D.svf(D.white(k, rng), rng.uniform(3000.0, 6000.0), sr, 0.9, "bp") * D.env_ad(k, sr, 0.01, 0.07)
        add(y, burst, at, sr, 0.5)
    return D.loop_crossfade(y, n)


@recipe("fryer_loop")
def fryer_loop(n, sr, rng):
    xf = D.nsamp(0.3, sr)
    m = n + xf
    rumble = D.fft_filter(rng.standard_normal(m), sr, lo=60.0, hi=220.0, order=2)
    rumble /= np.max(np.abs(rumble))
    fizz = D.fft_filter(rng.standard_normal(m), sr, lo=3500.0, hi=10000.0, order=2)
    fizz /= np.max(np.abs(fizz))
    y = 0.25 * rumble + 0.12 * fizz * (0.8 + 0.2 * D.smooth_random(m, sr, 5.0, rng))
    # Minnaert-style bubbles: short sine chirps rising in pitch
    for _ in range(int(80 * m / sr)):
        at = rng.uniform(0, m / sr)
        f0 = math.exp(rng.uniform(math.log(260.0), math.log(1200.0)))
        d = rng.uniform(0.012, 0.035)
        k = D.nsamp(d * 1.5, sr)
        fb = D.sweep(f0, f0 * rng.uniform(1.25, 1.8), k, sr, d)
        b = np.sin(D.TWO_PI * D.phase(fb, k, sr)) * D.env_ad(k, sr, 0.001, d)
        add(y, b, at, sr, rng.uniform(0.15, 0.6) * (600.0 / f0) ** 0.3)
    return D.loop_crossfade(y, n)


@recipe("soda_pour")
def soda_pour(n, sr, rng):
    t = T(n, sr)
    env = D.env_points([(0, 0), (0.05, 1), (0.48, 1), (0.6, 0)], n, sr, "smooth")
    gurgle = 0.7 + 0.3 * D.smooth_random(n, sr, 28.0, rng)
    pour = D.svf(D.white(n, rng), D.sweep(500.0, 1400.0, n, sr, 0.55, "lin") * gurgle, sr, 3.5, "bp")
    fizz = D.fft_filter(rng.standard_normal(n), sr, lo=4000.0, hi=12000.0)
    fizz /= np.max(np.abs(fizz))
    y = (0.9 * pour + 0.2 * fizz * np.clip(t / 0.4, 0, 1)) * env
    for _ in range(40):
        at = rng.uniform(0.08, 0.58)
        k = D.nsamp(0.004, sr)
        tick = D.svf(D.white(k, rng) * np.exp(-T(k, sr) / 0.0007), rng.uniform(4000, 9000), sr, 1.5, "bp")
        add(y, tick, at, sr, rng.uniform(0.1, 0.45))
    return y


@recipe("blender_whirr")
def blender_whirr(n, sr, rng):
    t = T(n, sr)
    f = D.env_points([(0, 60.0), (0.15, 165.0), (0.62, 172.0), (0.8, 90.0)], n, sr, "smooth")
    f = f * (1.0 + 0.02 * D.smooth_random(n, sr, 12.0, rng))
    motor = 0.6 * D.saw(f, n, sr) + 0.4 * D.square(2.0 * f, n, sr, 0.3)
    motor = D.svf(motor, 2200.0, sr, 0.9, "lp")
    whirr = D.svf(D.white(n, rng), 1800.0, sr, 1.2, "bp") * (0.6 + 0.4 * np.sin(D.TWO_PI * D.phase(f * 0.5, n, sr)))
    y = (motor + 0.5 * whirr) * D.env_points([(0, 0), (0.04, 0.8), (0.15, 1.0), (0.62, 1.0), (0.8, 0)], n, sr, "smooth")
    return D.soft_clip(1.3 * y, 1.5)


# =========================================================================== characters
@recipe("customer_order")
def customer_order(n, sr, rng):
    y = np.zeros(n)
    for f0, f1, at in ((1250.0, 1420.0, 0.0), (1550.0, 1780.0, 0.07)):
        m = D.nsamp(0.06, sr)
        f = D.sweep(f0, f1, m, sr, 0.03)
        b = D.harmonics(f, m, sr, (1.0, 0.0, 0.12)) * D.env_points([(0, 0), (0.004, 1), (0.035, 0.7), (0.055, 0), (1, 0)], m, sr, "smooth")
        add(y, b, at, sr)
    return y


@recipe("customer_happy")
def customer_happy(n, sr, rng):
    f0 = D.env_points([(0, 520.0), (0.11, 780.0), (0.2, 740.0), (0.3, 690.0)], n, sr, "smooth") * D.vibrato(n, sr, 8.0, 30.0, 0.1, 0.05)
    F1 = interp_curve(n, sr, [(0, 320.0), (0.06, 850.0), (0.16, 800.0), (0.27, 520.0), (0.3, 500.0)])
    F2 = interp_curve(n, sr, [(0, 2300.0), (0.06, 1450.0), (0.16, 1500.0), (0.27, 2100.0), (0.3, 2150.0)])
    F3 = interp_curve(n, sr, [(0, 3000.0), (0.3, 2800.0)])
    y = voice(n, sr, rng, f0, [(F1, 5.0, 1.0), (F2, 8.0, 0.6), (F3, 10.0, 0.25)], breath=0.05)
    y *= D.env_points([(0, 0), (0.02, 1), (0.2, 0.9), (0.29, 0)], n, sr, "smooth")
    return y


@recipe("customer_angry")
def customer_angry(n, sr, rng):
    t = T(n, sr)
    h = noise_burst(n, sr, rng, 0.01, 0.05, lo=600.0, hi=2000.0) * 0.25
    f0 = D.env_points([(0, 175.0), (0.12, 160.0), (0.3, 118.0), (0.35, 110.0)], n, sr, "smooth")
    f0 = f0 * (1.0 + 0.03 * D.smooth_random(n, sr, 40.0, rng))
    hum = voice(n, sr, rng, f0, [(270.0, 4.0, 1.0), (1100.0, 6.0, 0.18), (2500.0, 8.0, 0.06)], breath=0.02, tilt=1500.0)
    growl = 1.0 - 0.45 * (0.5 + 0.5 * np.sin(D.TWO_PI * 32.0 * t))
    hum *= growl * D.env_points([(0, 0), (0.04, 0), (0.08, 1), (0.25, 0.9), (0.3, 0), (1, 0)], n, sr, "smooth")
    ph = noise_burst(n, sr, rng, 0.002, 0.04, hi=900.0, at=0.285) * 0.5
    return D.svf(h + 1.4 * hum + ph, 2500.0, sr, 0.7, "lp")


@recipe("eat_chomp")
def eat_chomp(n, sr, rng):
    t = T(n, sr)
    y = np.zeros(n)
    for at, g, d in ((0.0, 1.0, 0.06), (0.075, 0.55, 0.045)):
        m = D.nsamp(d, sr)
        grains = (rng.random(m) < 0.25) * rng.uniform(-1, 1, m)
        crunch = D.svf(grains, 2600.0, sr, 1.0, "bp") * D.env_ad(m, sr, 0.002, d)
        add(y, crunch, at, sr, 1.5 * g)
        add(y, D.modal(D.nsamp(0.03, sr), sr, [900.0, 1750.0], [1.0, 0.4], [0.02, 0.012], rng), at, sr, 0.3 * g)
    y += 0.8 * thump(n, sr, 270.0, 120.0, 0.04, 0.08, at=0.01)
    return y


@recipe("trash_dump")
def trash_dump(n, sr, rng):
    y = np.zeros(n)
    hits = [(0.0, 1.0), (0.035, 0.7), (0.07, 0.85), (0.12, 0.5), (0.17, 0.45), (0.23, 0.3), (0.29, 0.2)]
    for at, g in hits:
        f = rng.uniform(600.0, 1800.0)
        y += g * metal_hit(n, sr, rng, f, at, rng.uniform(0.05, 0.1), (1.0, 1.7, 2.9, 4.1), (1.0, 0.6, 0.35, 0.2))
    y += 0.8 * thump(n, sr, 100.0, 68.0, 0.05, 0.2, at=0.05)
    y += 0.12 * D.svf(D.white(n, rng), 3000.0, sr, 0.8, "bp") * D.env_points([(0, 0), (0.02, 1), (0.3, 0.3), (0.39, 0)], n, sr)
    return small_room(y, sr, 0.35, 0.1)


def _footstep(n, sr, rng, lp, f0, scuff_at):
    y = noise_burst(n, sr, rng, 0.004, 0.035, hi=lp)
    y += 0.7 * thump(n, sr, f0 * 1.5, f0, 0.02, 0.04)
    y += 0.25 * noise_burst(n, sr, rng, 0.003, 0.02, lo=900.0, hi=2200.0, at=scuff_at)
    return y


@recipe("footstep1")
def footstep1(n, sr, rng):
    return _footstep(n, sr, rng, 900.0, 85.0, 0.018)


@recipe("footstep2")
def footstep2(n, sr, rng):
    return _footstep(n, sr, rng, 1100.0, 100.0, 0.022)


# =========================================================================== combo / events
def _combo(n, sr, rng, top, notes, shimmer=False):
    fc = D.sweep(800.0, top, n, sr, 0.14)
    w = bp_sweep_noise(n, sr, rng, fc, 1.5) * D.env_points([(0, 0), (0.08, 1), (0.14, 0.3), (0.2, 0), (1, 0)], n, sr, "smooth")
    y = 0.45 * w
    for i, (m, at) in enumerate(notes):
        pl = ks_note(D.midi_hz(m), 0.3, sr, rng, t60=0.28, bright=0.85 + 0.05 * i, damp=0.3)
        add(y, pl, at, sr, 0.9)
        y += 0.25 * bell_note(n, sr, D.midi_hz(m + 12), at, 0.2)
    if shimmer:
        y += 0.35 * sparkles(n, sr, rng, 0.14, 0.38, 14, 4000.0, 11000.0)
        y += 0.35 * chime_note(n, sr, D.midi_hz(96), 0.13, 0.25)
    return y


@recipe("combo_up1")
def combo_up1(n, sr, rng):
    return _combo(n, sr, rng, 4000.0, [(72, 0.08)])


@recipe("combo_up2")
def combo_up2(n, sr, rng):
    return _combo(n, sr, rng, 5000.0, [(76, 0.07), (79, 0.1)])


@recipe("combo_up3")
def combo_up3(n, sr, rng):
    return _combo(n, sr, rng, 6500.0, [(79, 0.06), (84, 0.09)], shimmer=True)


@recipe("combo_break")
def combo_break(n, sr, rng):
    t = T(n, sr)
    f = D.sweep(620.0, 140.0, n, sr, 0.34)
    wob = 1.0 + (0.02 + 0.05 * t / 0.4) * np.sin(D.TWO_PI * D.phase(D.sweep(9.0, 15.0, n, sr), n, sr))
    tone = D.triangle(f * wob, n, sr) + 0.3 * D.sine(f * wob * 2, n, sr)
    air = D.svf(D.white(n, rng), D.sweep(2000.0, 500.0, n, sr), sr, 2.0, "bp")
    y = (tone + 0.35 * air) * D.env_points([(0, 0), (0.01, 1), (0.3, 0.8), (0.39, 0), (1, 0)], n, sr, "smooth")
    return D.svf(y, 3000.0, sr, 0.7, "lp")


@recipe("rush_siren")
def rush_siren(n, sr, rng):
    t = T(n, sr)
    seg = 0.25
    k = np.floor(t / seg).astype(int)
    target = np.where(k % 2 == 0, 740.0, 988.0)
    f = D.onepole_lp(target, 1.0 / (D.TWO_PI * 0.03), sr)  # smooth glides
    f[:D.nsamp(0.03, sr)] = 740.0
    f = f * D.vibrato(n, sr, 6.0, 10.0, 0.0, 0.01)
    y = D.triangle(f, n, sr) + 0.3 * D.sine(2 * f, n, sr) + 0.1 * D.fm(f, 2.0, 0.6, n, sr)
    y *= D.env_points([(0, 0), (0.6, 1.0), (1.1, 1.0), (1.49, 0), (1.5, 0)], n, sr, "smooth")
    return D.svf(y, 4000.0, sr, 0.7, "lp")


@recipe("fire_whoosh")
def fire_whoosh(n, sr, rng):
    fc = D.env_points([(0, 500.0), (0.12, 1800.0), (0.6, 700.0)], n, sr, "smooth")
    body = D.svf(D.white(n, rng), fc, sr, 0.7, "bp")
    roar = D.svf(D.white(n, rng), 260.0, sr, 0.7, "lp")
    env = D.env_ad(n, sr, 0.025, 0.65)
    y = (body + 0.9 * roar) * env
    for _ in range(18):
        at = rng.uniform(0.02, 0.5)
        y += rng.uniform(0.1, 0.4) * click(n, sr, rng, rng.uniform(2500, 6000), 1.2, 0.001, at=at)
    return D.soft_clip(1.4 * y, 1.3)


@recipe("extinguish")
def extinguish(n, sr, rng):
    hiss = D.fft_filter(rng.standard_normal(n), sr, lo=2000.0, hi=9000.0)
    hiss /= np.max(np.abs(hiss))
    am = 0.75 + 0.25 * D.smooth_random(n, sr, 30.0, rng)
    y = 0.7 * hiss * am * D.env_points([(0, 0), (0.015, 1), (0.45, 0.85), (0.69, 0), (1, 0)], n, sr, "smooth")
    y += 0.8 * noise_burst(n, sr, rng, 0.003, 0.12, hi=420.0)
    for _ in range(40):
        at = rng.uniform(0.1, 0.6)
        f0 = rng.uniform(1500.0, 4000.0)
        k = D.nsamp(0.02, sr)
        b = np.sin(D.TWO_PI * D.phase(D.sweep(f0, f0 * 1.5, k, sr), k, sr)) * D.env_ad(k, sr, 0.001, 0.015)
        add(y, b, at, sr, rng.uniform(0.04, 0.15))
    return y


@recipe("spill_splat")
def spill_splat(n, sr, rng):
    t = T(n, sr)
    splat = D.svf(D.white(n, rng), D.sweep(2200.0, 350.0, n, sr, 0.08), sr, 1.2, "bp") * D.env_ad(n, sr, 0.002, 0.12)
    y = 2.6 * splat + 0.35 * thump(n, sr, 160.0, 70.0, 0.04, 0.08)
    for i in range(6):
        at = 0.06 + 0.19 * rng.random()
        f0 = rng.uniform(700.0, 1800.0)
        k = D.nsamp(0.03, sr)
        b = np.sin(D.TWO_PI * D.phase(D.sweep(f0, f0 * 1.6, k, sr, 0.015), k, sr)) * D.env_ad(k, sr, 0.001, 0.03)
        add(y, b, at, sr, rng.uniform(0.15, 0.35))
    return y


@recipe("mop_squeak")
def mop_squeak(n, sr, rng):
    y = np.zeros(n)
    for at, d, base in ((0.01, 0.12, 1400.0), (0.16, 0.13, 1650.0)):
        m = D.nsamp(d, sr)
        arc = D.env_points([(0, 0.85), (d * 0.4, 1.12), (d, 0.95)], m, sr, "smooth")
        jitter = 1.0 + 0.06 * D.smooth_random(m, sr, 45.0, rng)
        f = base * arc * jitter
        s = 0.7 * D.saw(f, m, sr) + 0.3 * D.square(f, m, sr, 0.25)
        s = D.svf(s, 2000.0, sr, 2.0, "bp") + 0.3 * D.svf(s, 1200.0, sr, 0.7, "lp")
        s *= D.env_points([(0, 0), (0.01, 1), (d - 0.02, 0.9), (d, 0)], m, sr, "smooth")
        add(y, s, at, sr)
    y += 0.12 * D.svf(D.white(n, rng), 1000.0, sr, 0.8, "bp") * D.env_points([(0, 0), (0.03, 1), (0.27, 1), (0.3, 0)], n, sr, "smooth")
    return y


@recipe("raccoon_chitter")
def raccoon_chitter(n, sr, rng):
    y = np.zeros(n)
    at = 0.0
    i = 0
    while at < 0.4:
        d = rng.uniform(0.022, 0.035)
        m = D.nsamp(d, sr)
        base = (2100.0 - 1100.0 * at) * rng.uniform(0.9, 1.1)
        f = D.sweep(base * 1.15, base * 0.85, m, sr, d)
        src = D.saw(f, m, sr)
        s = D.svf(src, 2600.0, sr, 4.0, "bp") + 0.5 * D.svf(src, 4200.0, sr, 5.0, "bp") + 0.3 * np.sin(D.TWO_PI * D.phase(f, m, sr))
        s *= D.env_points([(0, 0), (0.003, 1), (d * 0.6, 0.7), (d, 0)], m, sr, "smooth")
        add(y, s, at, sr, rng.uniform(0.7, 1.0))
        at += d + rng.uniform(0.012, 0.03)
        i += 1
    # cheeky rising squeak at the end
    m = D.nsamp(0.07, sr)
    f = D.sweep(1500.0, 2600.0, m, sr, 0.06)
    s = D.harmonics(f, m, sr, (1.0, 0.3, 0.1)) * D.env_points([(0, 0), (0.01, 1), (0.05, 0.8), (0.07, 0)], m, sr, "smooth")
    add(y, s, 0.42, sr, 0.3)
    return y


@recipe("machine_jam")
def machine_jam(n, sr, rng):
    y = D.modal(n, sr, [95.0, 180.0, 410.0], [1.0, 0.6, 0.3], [0.16, 0.12, 0.08], rng) * 0.9
    y += 0.7 * metal_hit(n, sr, rng, 820.0, 0.0, 0.12, (1.0, 1.99, 3.01, 4.76), (1.0, 0.6, 0.45, 0.25))
    y += 0.4 * noise_burst(n, sr, rng, 0.001, 0.04, hi=3000.0)
    at = 0.06
    rate = 28.0
    while at < 0.45:
        g = 0.5 * (1.0 - (at - 0.06) / 0.42) + 0.1
        y += g * metal_hit(n, sr, rng, rng.uniform(1200.0, 3000.0), at, rng.uniform(0.02, 0.04), (1.0, 1.7, 2.6), (1.0, 0.5, 0.3))
        at += 1.0 / rate * rng.uniform(0.8, 1.2)
        rate = max(8.0, rate * 0.9)
    return D.soft_clip(1.2 * y, 1.3)


@recipe("machine_fixed")
def machine_fixed(n, sr, rng):
    y = np.zeros(n)
    for i in range(7):
        g = 1.8 if i % 2 == 0 else 1.2
        y += g * metal_hit(n, sr, rng, rng.uniform(2700.0, 2950.0), 0.03 * i, 0.018, (1.0, 1.66, 2.5), (1.0, 0.5, 0.3))
    y += 0.9 * chime_note(n, sr, D.midi_hz(84), 0.25, 0.3)
    y += 1.0 * chime_note(n, sr, D.midi_hz(91), 0.31, 0.32)
    return small_room(y, sr, 0.4, 0.1)


@recipe("car_horn")
def car_horn(n, sr, rng):
    y = np.zeros(n)
    for at, d in ((0.0, 0.15), (0.2, 0.17)):
        m = D.nsamp(d + 0.03, sr)
        rise = 2.0 ** (-90.0 * np.exp(-T(m, sr) / 0.015) / 1200.0)
        s = np.zeros(m)
        for f in (700.0, 880.0):
            s += D.square(f * rise, m, sr, 0.42, rng.random())
        s = D.svf(s, 1600.0, sr, 1.1, "lp") + 0.3 * D.svf(s, 2400.0, sr, 3.0, "bp")
        s = D.soft_clip(0.6 * s, 1.5) * D.adsr(m, sr, 0.008, 0.06, 0.85, 0.025, gate=d)
        add(y, s, at, sr)
    return y


@recipe("car_engine")
def car_engine(n, sr, rng):
    t = T(n, sr)
    rate = D.env_points([(0, 11.0), (0.5, 17.0), (1.0, 14.0)], n, sr, "smooth")
    ph = D.phase(rate, n, sr)
    edges = np.nonzero(np.diff(np.floor(ph)) > 0)[0] + 1
    y = np.zeros(n)
    k = D.nsamp(0.08, sr)
    for e in edges:
        g = rng.uniform(0.7, 1.0)
        pop = D.sine(D.sweep(110.0, 65.0, k, sr, 0.03), k, sr) * D.env_ad(k, sr, 0.002, 0.06)
        pop += 0.5 * D.svf(D.white(k, rng), 550.0, sr, 0.8, "lp") * D.env_ad(k, sr, 0.001, 0.03)
        D.place(y, pop, e, g)
    rumble = D.svf(D.white(n, rng), 180.0, sr, 0.7, "lp")
    y += 0.4 * rumble
    y *= D.env_points([(0, 0), (0.02, 1), (0.85, 1), (1.0, 0)], n, sr, "smooth")
    return D.soft_clip(1.2 * y, 1.2)


@recipe("powerup")
def powerup(n, sr, rng):
    fc = D.sweep(400.0, 6000.0, n, sr, 0.45)
    w = bp_sweep_noise(n, sr, rng, fc, 2.0) * D.env_points([(0, 0), (0.35, 1), (0.59, 0), (1, 0)], n, sr, "smooth")
    f = D.sweep(300.0, 1500.0, n, sr, 0.45) * D.vibrato(n, sr, 12.0, 40.0, 0.0, 0.1)
    tone = D.triangle(f, n, sr) * D.env_points([(0, 0), (0.05, 0.6), (0.45, 1.0), (0.59, 0), (1, 0)], n, sr, "smooth")
    y = 0.5 * w + 0.4 * tone
    for i, m in enumerate((84, 88, 91, 96, 100, 103)):
        y += 0.28 * bell_note(n, sr, D.midi_hz(m), 0.05 + 0.045 * i, 0.25)
    y += 0.3 * sparkles(n, sr, rng, 0.3, 0.58, 12, 4000.0, 11000.0)
    return y


@recipe("timer_tick")
def timer_tick(n, sr, rng):
    y = D.modal(n, sr, [2300.0, 3600.0, 5200.0, 7100.0], [1.0, 0.7, 0.4, 0.25], [0.022, 0.015, 0.01, 0.008], rng)
    y += 0.25 * D.modal(n, sr, [620.0], [1.0], [0.015], rng)
    y += 0.3 * click(n, sr, rng, 4000.0, 0.8, 0.0006)
    return y


@recipe("timer_warning")
def timer_warning(n, sr, rng):
    m = n
    y = D.harmonics(1046.5, m, sr, (1.0, 0.22, 0.07)) * D.env_points([(0, 0), (0.008, 1), (0.14, 0.85), (0.19, 0), (1, 0)], m, sr, "smooth")
    y *= 1.0 + 0.08 * np.sin(D.TWO_PI * 30.0 * T(m, sr))
    return y


@recipe("level_win")
def level_win(n, sr, rng):
    beat = 60.0 / 180.0
    y = np.zeros(n)
    kit = I.DrumKit(sr, seed=11, variants=2)
    mel = [(67, 0.0, 0.5), (72, 0.5, 0.5), (76, 1.0, 0.5), (79, 1.5, 1.0), (76, 2.5, 0.5), (79, 3.0, 1.0), (84, 4.0, 3.2)]
    for m, b, d in mel:
        add(y, I.brass(D.midi_hz(m), 0.95, sr, dur=d * beat * 0.95, bright=1.2), b * beat, sr, 0.5)
        add(y, I.marimba(D.midi_hz(m + 12), 0.8, sr), b * beat, sr, 0.25)
    chords = [((60, 64, 67), 0.0, 2.5), ((57, 60, 64), 2.5, 0.5), ((55, 59, 62, 67), 3.0, 1.0), ((60, 64, 67, 72), 4.0, 3.0)]
    for notes, b, d in chords:
        for m in notes:
            add(y, I.brass(D.midi_hz(m), 0.6, sr, dur=d * beat * 0.9, bright=0.7), b * beat, sr, 0.18)
    for m, b in ((48, 0.0), (45, 2.5), (43, 3.0), (36, 4.0)):
        add(y, I.bass_soft(D.midi_hz(m), 0.9, sr, dur=1.2 * beat), b * beat, sr, 0.22)
    for b in (0.0, 2.5, 3.0, 4.0):
        add(y, kit.get("kick"), b * beat, sr, 0.25)
    for i in range(8):  # snare roll into the last chord
        b = 3.0 + i / 8.0
        add(y, kit.get("snare"), b * beat, sr, 0.12 + 0.03 * i)
    add(y, kit.get("crash"), 4.0 * beat, sr, 0.3)
    for i, m in enumerate((84, 88, 91, 96)):
        y += 0.25 * chime_note(n, sr, D.midi_hz(m + 12), 4.0 * beat + 0.04 * i, 0.6)
    y += 0.25 * sparkles(n, sr, rng, 4.0 * beat, 2.3, 18, 4000.0, 11000.0)
    y *= D.env_points([(0, 1), (2.15, 1), (2.5, 0)], n, sr, "smooth")
    return small_room(y, sr, 1.0, 0.18)


@recipe("level_fail")
def level_fail(n, sr, rng):
    y = np.zeros(n)
    notes = [(67, 0.0, 0.28), (66, 0.32, 0.28), (65, 0.64, 0.28), (64, 0.96, 0.6)]
    for i, (m, at, d) in enumerate(notes):
        k = D.nsamp(d + 0.15, sr)
        f = D.midi_hz(m)
        if i == 3:
            f = f * 2.0 ** (-40.0 * np.clip((T(k, sr) - 0.2) / 0.4, 0, 1) / 1200.0) * D.vibrato(k, sr, 6.0, 45.0, 0.12, 0.25)
        # muted 'wah' trombone: FM brass core through a closing/opening low-pass
        env = D.adsr(k, sr, 0.03, 0.2, 0.8, 0.1, gate=d)
        tone = D.fm(f, 1.0, env * 1.8, k, sr)
        wah = D.env_points([(0, 500.0), (0.08, 1500.0), (d, 900.0), (d + 0.15, 600.0)], k, sr, "smooth")
        if i == 3:
            wah = wah * (1.0 + 0.35 * np.sin(D.TWO_PI * 3.0 * T(k, sr)))
        tone = D.svf(tone, wah, sr, 2.0, "lp") * env
        add(y, tone, at, sr, 0.9)
    # cute little plop at the end
    y += 0.5 * blip(n, sr, 520.0, 260.0, 0.06, 0.08, at=1.62)
    return small_room(y, sr, 0.6, 0.12)


@recipe("vip_arrive")
def vip_arrive(n, sr, rng):
    y = np.zeros(n)
    scale = (72, 74, 76, 79, 81, 84, 86, 88, 91, 93, 96)
    for i, m in enumerate(scale):
        pl = ks_note(D.midi_hz(m), 0.6, sr, rng, t60=0.55, bright=0.8, damp=0.35)
        add(y, pl, 0.03 * i, sr, 0.55 + 0.03 * i)
    y += 0.6 * chime_note(n, sr, D.midi_hz(96), 0.33, 0.45)
    y += 0.35 * sparkles(n, sr, rng, 0.25, 0.76, 22, 4000.0, 11000.0)
    return small_room(y, sr, 0.9, 0.2)


@recipe("critic_hmm")
def critic_hmm(n, sr, rng):
    f0 = D.env_points([(0, 160.0), (0.22, 148.0), (0.45, 178.0), (0.5, 180.0)], n, sr, "smooth") * D.vibrato(n, sr, 5.0, 15.0, 0.15, 0.1)
    y = voice(n, sr, rng, f0, [(250.0, 3.0, 1.0), (2100.0, 7.0, 0.1), (2900.0, 9.0, 0.05)], breath=0.015, tilt=1200.0)
    y = D.svf(y, 850.0, sr, 2.0, "notch")
    y *= D.env_points([(0, 0), (0.05, 1), (0.4, 0.9), (0.49, 0), (1, 0)], n, sr, "smooth")
    return D.svf(y, 2000.0, sr, 0.7, "lp")


@recipe("splash_pop")
def splash_pop(n, sr, rng):
    y = blip(n, sr, 380.0, 950.0, 0.02, 0.06, harm=(1.0, 0.15))
    y += 0.6 * thump(n, sr, 140.0, 70.0, 0.03, 0.05)
    y += 0.25 * click(n, sr, rng, 3000.0, 1.0, 0.0012)
    y += 0.15 * D.modal(n, sr, [2800.0], [1.0], [0.02], rng)
    return y


@recipe("whoosh")
def whoosh(n, sr, rng):
    fc = D.env_points([(0, 350.0), (0.22, 2200.0), (0.4, 900.0)], n, sr, "smooth")
    body = D.svf(D.white(n, rng), fc, sr, 1.0, "bp")
    low = D.svf(D.white(n, rng), fc * 0.4, sr, 0.7, "lp")
    env = D.env_points([(0, 0), (0.2, 1), (0.39, 0), (1, 0)], n, sr, "smooth") ** 1.5
    return (body + 0.4 * low) * env


# =========================================================================== render
def render_all(list_path, out_dir, only=None, log=print):
    entries = parse_list(list_path)
    names = [e[0] for e in entries]
    missing = [nm for nm in names if nm not in RECIPES]
    if missing:
        raise RuntimeError("no recipe for: %s" % ", ".join(missing))
    os.makedirs(out_dir, exist_ok=True)
    results = []
    for name, dur, desc in entries:
        if only and name not in only:
            continue
        n = D.nsamp(dur, SR)
        rng = D.rng_for(name)
        y = np.asarray(RECIPES[name](n, SR, rng), dtype=float)
        y = D.fit(y, n)
        if not np.all(np.isfinite(y)):
            raise RuntimeError("NaN/Inf in " + name)
        if name not in LOOPS:
            y = D.dc_block(y, SR, 12.0)  # remove any DC before fades
            y = D.fade(y, SR, 0.0015 if name in SHORT_FADE_IN else 0.005, 0.005)
            # A short transient (e.g. a decaying sine thump) has a non-zero mean over its own
            # length; remove it with a scaled Hann bump (sub-audio, zero at both ends -> no click).
            w = np.hanning(n)
            y = y - np.mean(y) / np.mean(w) * w
        else:
            y = y - np.mean(y)
        peak = -4.0 if name.startswith("ui_") else -1.0
        y = D.normalize(y, peak)
        path = os.path.join(out_dir, name + ".wav")
        D.write_wav(path, y, SR, dither_seed=len(name))
        results.append((name, dur, path))
        log("  sfx %-16s %5.3f s" % (name, dur))
    return results
