"""instruments.py - synthesised instruments shared by the SFX and music renderers.

Every function returns a mono float array (or (n, 2) for stereo instruments) at sample rate
`sr`, starting at the note onset and including its natural release tail. `vel` is 0..1.
"""
import math

import numpy as np

import dsp as D


# =========================================================================== pitched
def marimba(f, vel, sr, dur=None):
    """FM-flavoured marimba: warm fundamental (1:1 FM bloom), tuned ~3.93x overtone, mallet tick."""
    t60 = float(np.clip(1.5 * (196.0 / f) ** 0.6, 0.22, 1.6))
    n = D.nsamp(t60 * 1.05, sr)
    t = D.tvec(n, sr)
    idx = 0.25 + 0.9 * vel * np.exp(-t / 0.02)
    y = D.fm(f, 1.0, idx, n, sr) * 10.0 ** (-3 * t / t60)
    y += 0.20 * (0.5 + vel) * D.sine(3.93 * f, n, sr) * 10.0 ** (-3 * t / (t60 * 0.22)) * (3.93 * f < 0.45 * sr)
    y += 0.05 * vel * D.sine(9.2 * f, n, sr) * 10.0 ** (-3 * t / 0.025) * (9.2 * f < 0.45 * sr)
    y *= np.clip(t / 0.0012, 0, 1)
    return y * vel


def kalimba(f, vel, sr, dur=None):
    """Kalimba / thumb-piano: FM 1:7 'tink' that collapses quickly into a pure, long fundamental."""
    t60 = float(np.clip(2.2 * (262.0 / f) ** 0.5, 0.6, 2.6))
    n = D.nsamp(t60 * 1.02, sr)
    t = D.tvec(n, sr)
    idx = 1.8 * vel * np.exp(-t / 0.010) + 0.15 * np.exp(-t / 0.2)
    y = D.fm(f, 7.0, idx, n, sr) * 10.0 ** (-3 * t / t60)
    y += 0.10 * D.sine(5.95 * f, n, sr) * 10.0 ** (-3 * t / (0.09)) * (5.95 * f < 0.45 * sr)
    y *= np.clip(t / 0.0015, 0, 1)
    return y * vel


def epiano(f, vel, sr, dur=0.5):
    """FM electric piano (1:1 body + high-ratio tine bark), exponential decay, damper release."""
    rel = 0.25
    t60 = float(np.clip(3.6 * (262.0 / f) ** 0.4, 1.4, 5.0))
    n = D.nsamp(min(dur + rel, t60), sr)
    t = D.tvec(n, sr)
    idx = (0.35 + 1.3 * vel) * np.exp(-t / 0.4) + 0.2
    body = D.fm(f, 1.0, idx, n, sr)
    r = 14.0 if 15 * f < 0.45 * sr else 5.0
    tine = D.fm(f, r, 0.9 * vel * np.exp(-t / 0.02), n, sr) - np.sin(D.TWO_PI * D.phase(f, n, sr))
    y = body + 0.6 * tine
    env = 10.0 ** (-3 * t / t60) * np.clip(t / 0.002, 0, 1)
    env *= np.where(t < dur, 1.0, np.exp(-(t - dur) / (rel / 4.6)))
    return y * env * vel


def pluck(f, vel, sr, dur=None, rng=None, t60=1.6, damp=0.45, bright=0.6, mute=False):
    """Karplus-Strong guitar string with a gentle body lowpass and DC block."""
    rng = rng if rng is not None else np.random.default_rng(int(f * 100))
    if mute:
        t60 = 0.18
    length = min(t60 * 1.05, 3.0)
    y = D.ks_pluck(f, length, sr, rng, t60=t60, damp=damp, bright=bright * (0.6 + 0.4 * vel))
    y = D.dc_block(y, sr, 20.0)
    if mute:
        y = D.svf(y, 1400.0, sr, 0.7, "lp")
    n = len(y)
    y *= np.clip(np.arange(n) / (0.001 * sr), 0, 1)
    return 0.8 * y * vel


def pad(freqs, dur, sr, rng, cutoff=1300.0, attack=0.45, release=1.0, detune=8.0, vel=1.0,
        lfo_rate=0.17):
    """Warm stereo pad: 3 detuned PolyBLEP saws per chord tone (spread L/C/R), slow LP sweep."""
    n = D.nsamp(dur + release, sr)
    t = D.tvec(n, sr)
    L = np.zeros(n)
    R = np.zeros(n)
    for f in freqs:
        drift = 1.0 + 0.0012 * np.sin(D.TWO_PI * rng.uniform(0.15, 0.4) * t + rng.uniform(0, 6.28))
        for j, dc in enumerate((-detune, 0.0, detune)):
            s = D.saw(D.cents(f, dc + rng.uniform(-1.5, 1.5)) * drift, n, sr, phase0=rng.random())
            if j == 0:
                L += s
                R += 0.25 * s
            elif j == 2:
                R += s
                L += 0.25 * s
            else:
                L += 0.6 * s
                R += 0.6 * s
    fc = cutoff * (1.0 + 0.25 * np.sin(D.TWO_PI * lfo_rate * t + rng.uniform(0, 6.28)))
    L = D.svf(L, fc, sr, 0.75, "lp")
    R = D.svf(R, fc * 1.03, sr, 0.75, "lp")
    env = D.adsr(n, sr, a=attack, d=0.6, s=0.85, r=release, gate=dur)
    norm = 0.3 / max(1, len(freqs))
    return np.stack([L * env, R * env], axis=1) * norm * vel


def bass_soft(f, vel, sr, dur=0.5, rng=None):
    """Round, warm bass: 1:1 FM pluck transient over a sine body, gentle tanh, lowpassed."""
    rel = 0.07
    n = D.nsamp(dur + rel, sr)
    t = D.tvec(n, sr)
    idx = 0.45 + 1.1 * vel * np.exp(-t / 0.045)
    y = D.fm(f, 1.0, idx, n, sr) + 0.7 * D.sine(f, n, sr)
    env = D.adsr(n, sr, a=0.004, d=0.35, s=0.7, r=rel, gate=dur)
    y = D.soft_clip(0.9 * y * env, 1.8)  # a little 2nd/3rd harmonic so it reads on phone speakers
    y = D.svf(y, 750.0, sr, 0.7, "lp")
    return y * vel


def bass_drive(f, vel, sr, dur=0.3, rng=None, cutoff=900.0):
    """Punchier bass for rush/boss: saw through an enveloped LP, plus a solid sine sub."""
    rel = 0.05
    n = D.nsamp(dur + rel, sr)
    t = D.tvec(n, sr)
    s = D.saw(f, n, sr)
    fc = 180.0 + cutoff * (0.35 + vel) * np.exp(-t / 0.08)
    s = D.svf(s, fc, sr, 1.1, "lp")
    sub = D.sine(f, n, sr)
    env = D.adsr(n, sr, a=0.003, d=0.25, s=0.75, r=rel, gate=dur)
    y = D.soft_clip((0.55 * s + 0.95 * sub) * env, 1.6)
    return D.svf(y, 1100.0, sr, 0.7, "lp") * vel


def bass_upright(f, vel, sr, dur=0.5, rng=None):
    """Upright-ish plucked bass: mellow Karplus-Strong string + sine fundamental thump."""
    rng = rng if rng is not None else np.random.default_rng(int(f * 10))
    rel = 0.08
    ks = D.ks_pluck(f, dur + rel, sr, rng, t60=1.6, damp=0.5, bright=0.35, pick=0.25)
    n = len(ks)
    t = D.tvec(n, sr)
    body = D.sine(f, n, sr) * 10.0 ** (-3 * t / 1.6)
    y = 0.7 * D.dc_block(ks, sr, 25.0) + 0.8 * body
    env = np.where(t < dur, 1.0, np.exp(-(t - dur) / (rel / 4.6))) * np.clip(t / 0.003, 0, 1)
    y = D.svf(y * env, 900.0, sr, 0.7, "lp")
    return D.soft_clip(y, 1.2) * vel


def lead_soft(f, vel, sr, dur=0.4, rng=None):
    """Ocarina/whistle-like lead: near-sine with a breathy band of noise, scoop + delayed vibrato."""
    rng = rng if rng is not None else np.random.default_rng(int(f))
    rel = 0.12
    n = D.nsamp(dur + rel, sr)
    t = D.tvec(n, sr)
    scoop = 2.0 ** (-28.0 * np.exp(-t / 0.025) / 1200.0)
    ff = f * scoop * D.vibrato(n, sr, 5.3, 14.0, delay=0.16, ramp=0.25)
    ph = D.phase(ff, n, sr)
    y = np.sin(D.TWO_PI * ph) + 0.16 * np.sin(2 * D.TWO_PI * ph) + 0.05 * np.sin(3 * D.TWO_PI * ph)
    breath = D.svf(D.white(n, rng), ff * 1.0, sr, 3.0, "bp")
    y = y + 0.22 * breath * (0.5 + 0.5 * np.exp(-t / 0.05))
    env = D.adsr(n, sr, a=0.025, d=0.2, s=0.85, r=rel, gate=dur)
    return y * env * vel


def lead_bright(f, vel, sr, dur=0.4, rng=None, cutoff=4200.0):
    """Brighter lead: saw + narrow pulse, enveloped resonant LP, vibrato, light drive. Stereo."""
    rng = rng if rng is not None else np.random.default_rng(int(f))
    rel = 0.1
    n = D.nsamp(dur + rel, sr)
    t = D.tvec(n, sr)
    scoop = 2.0 ** (-20.0 * np.exp(-t / 0.02) / 1200.0)
    ff = f * scoop * D.vibrato(n, sr, 5.6, 12.0, delay=0.14, ramp=0.2)
    out = []
    for dc in (-6.0, 6.0):
        s = 0.6 * D.saw(D.cents(ff, dc), n, sr, rng.random()) + 0.4 * D.square(D.cents(ff, -dc), n, sr, 0.32, rng.random())
        fc = np.minimum(f * 2.0 + cutoff * (0.55 + 0.45 * np.exp(-t / 0.18)), 0.45 * sr)
        s = D.svf(s, fc, sr, 1.0, "lp")
        out.append(s)
    env = D.adsr(n, sr, a=0.012, d=0.25, s=0.8, r=rel, gate=dur)
    st = np.stack(out, axis=1) * env[:, None]
    return D.soft_clip(0.8 * st, 1.4) * vel


def brass(f, vel, sr, dur=0.4, rng=None, bright=1.0, cutoff=3200.0):
    """FM brass: 1:1 FM whose index follows the amplitude envelope (brassy 'blat'), 2 voices."""
    rng = rng if rng is not None else np.random.default_rng(int(f))
    rel = 0.14
    n = D.nsamp(dur + rel, sr)
    t = D.tvec(n, sr)
    env = D.adsr(n, sr, a=0.035, d=0.25, s=0.8, r=rel, gate=dur)
    scoop = 2.0 ** (-35.0 * np.exp(-t / 0.03) / 1200.0) * D.vibrato(n, sr, 5.0, 8.0, 0.2, 0.25)
    y = np.zeros(n)
    for dc in (-5.0, 5.0):
        idx = env * (1.0 + 2.2 * vel * bright)
        y += D.fm(D.cents(f, dc) * scoop, 1.0, idx, n, sr, phase0=rng.random())
    y = D.svf(y * env, cutoff, sr, 0.7, "lp")
    return 0.5 * y * vel


def glock(f, vel, sr, dur=None, t60=1.4):
    """Glockenspiel: free-bar modal ratios 1 : 2.756 : 5.404."""
    n = D.nsamp(t60 * 1.02, sr)
    return D.modal(n, sr, [f, 2.756 * f, 5.404 * f], [1.0, 0.28, 0.08], [t60, t60 * 0.35, t60 * 0.15]) * vel


def bell(f, vel, sr, t60=1.2, ratio=3.5, index=1.4, idx_tau=0.12):
    """Inharmonic FM bell / chime."""
    n = D.nsamp(t60 * 1.02, sr)
    t = D.tvec(n, sr)
    idx = index * vel * np.exp(-t / idx_tau) + 0.1
    y = D.fm(f, ratio, idx, n, sr) * 10.0 ** (-3 * t / t60)
    return y * np.clip(t / 0.0008, 0, 1) * vel


def chime(f, vel, sr, t60=0.6):
    """Bright 'ding': bell + glock modes layered."""
    a = bell(f, 1.0, sr, t60=t60, ratio=3.0, index=0.9, idx_tau=0.05)
    b = glock(f, 1.0, sr, t60=t60)
    n = max(len(a), len(b))
    return (D.fit(a, n) * 0.6 + D.fit(b, n) * 0.6) * vel


# =========================================================================== drums
def kick(sr, rng, soft=False, tune=1.0):
    n = D.nsamp(0.45, sr)
    t = D.tvec(n, sr)
    if soft:
        f = 46.0 * tune + 95.0 * tune * np.exp(-t / 0.03)
    else:
        f = 50.0 * tune + 135.0 * tune * np.exp(-t / 0.032)
    body = D.sine(f, n, sr) * 10.0 ** (-3 * t / (0.38 if soft else 0.42)) * np.clip(t / 0.0015, 0, 1)
    click = D.svf(D.white(n, rng), 3000.0, sr, 0.7, "lp") * np.exp(-t / 0.0025)
    y = body + (0.12 if soft else 0.3) * click
    return D.soft_clip(1.2 * y, 1.4)


def snare(sr, rng, tone=1.0, snappy=1.0):
    n = D.nsamp(0.3, sr)
    t = D.tvec(n, sr)
    f1 = 188.0 * tone * (1 + 0.25 * np.exp(-t / 0.012))
    body = 0.55 * D.sine(f1, n, sr) * 10.0 ** (-3 * t / 0.11) + 0.25 * D.sine(335.0 * tone, n, sr) * 10.0 ** (-3 * t / 0.07)
    nz = D.white(n, rng)
    nz = D.svf(D.svf(nz, 1300.0, sr, 0.7, "hp"), min(8000.0, 0.45 * sr), sr, 0.7, "lp")
    y = body + 0.75 * snappy * nz * 10.0 ** (-3 * t / 0.17)
    return y * np.clip(t / 0.0008, 0, 1)


def rim(sr, rng):
    n = D.nsamp(0.08, sr)
    t = D.tvec(n, sr)
    y = D.modal(n, sr, [1650.0, 520.0, 3350.0], [1.0, 0.5, 0.35], [0.04, 0.05, 0.02], rng)
    y += 0.4 * D.svf(D.white(n, rng), 2500.0, sr, 0.8, "bp") * np.exp(-t / 0.002)
    return y


def clap(sr, rng):
    n = D.nsamp(0.3, sr)
    t = D.tvec(n, sr)
    env = np.zeros(n)
    for i, d in enumerate((0.0, 0.010, 0.019, 0.027)):
        env += (0.8 if i < 3 else 1.0) * (t >= d) * np.exp(-np.clip(t - d, 0, None) / (0.004 if i < 3 else 0.065))
    nz = D.svf(D.white(n, rng), 1250.0, sr, 0.9, "bp") + 0.3 * D.svf(D.white(n, rng), 3000.0, sr, 0.7, "hp")
    return nz * env


def hat(sr, rng, open_=False, bright=1.0):
    n = D.nsamp(0.45 if open_ else 0.09, sr)
    t = D.tvec(n, sr)
    metal = np.zeros(n)
    for fr in (205.3, 304.4, 369.6, 522.7, 540.0, 800.0):
        metal += D.square(fr * 1.6 * bright, n, sr, 0.5, rng.random())
    nz = D.white(n, rng)
    x = 0.5 * metal / 6 + 0.6 * nz
    x = D.svf(x, min(6800.0, 0.38 * sr), sr, 0.8, "hp")
    x = x + 0.4 * D.svf(nz, min(9000.0, 0.42 * sr), sr, 1.5, "bp")
    env = 10.0 ** (-3 * t / (0.32 if open_ else 0.045)) * np.clip(t / 0.0006, 0, 1)
    return x * env


def shaker(sr, rng, length=0.09):
    n = D.nsamp(length + 0.03, sr)
    t = D.tvec(n, sr)
    nz = D.svf(D.white(n, rng), min(5200.0, 0.4 * sr), sr, 0.7, "hp")
    env = np.sin(0.5 * math.pi * np.clip(t / 0.012, 0, 1)) ** 2 * 10.0 ** (-3 * np.clip(t - 0.012, 0, None) / length)
    return nz * env


def tambourine(sr, rng):
    n = D.nsamp(0.25, sr)
    t = D.tvec(n, sr)
    y = np.zeros(n)
    for _ in range(9):
        d = rng.uniform(0, 0.008)
        fr = rng.uniform(4300, min(9800, 0.44 * sr))
        y += rng.uniform(0.4, 1.0) * (t >= d) * np.sin(D.TWO_PI * fr * (t - d)) * 10.0 ** (-3 * np.clip(t - d, 0, None) / rng.uniform(0.08, 0.18))
    nz = D.svf(D.white(n, rng), min(6000.0, 0.4 * sr), sr, 0.7, "hp") * 10.0 ** (-3 * t / 0.1)
    return 0.25 * y + 0.6 * nz


def conga(sr, rng, f=230.0, slap=0.5):
    n = D.nsamp(0.3, sr)
    t = D.tvec(n, sr)
    ff = f * (1.0 + 0.12 * np.exp(-t / 0.01))
    y = D.sine(ff, n, sr) * 10.0 ** (-3 * t / 0.24) + 0.25 * D.sine(ff * 1.52, n, sr) * 10.0 ** (-3 * t / 0.08)
    y += slap * D.svf(D.white(n, rng), 1800.0, sr, 0.9, "bp") * np.exp(-t / 0.006)
    return y * np.clip(t / 0.0008, 0, 1)


def tom(sr, rng, f=110.0):
    n = D.nsamp(0.5, sr)
    t = D.tvec(n, sr)
    ff = f * (1.0 + 0.45 * np.exp(-t / 0.05))
    y = D.sine(ff, n, sr) * 10.0 ** (-3 * t / 0.42)
    y += 0.25 * D.svf(D.white(n, rng), 900.0, sr, 0.8, "lp") * np.exp(-t / 0.02)
    return D.soft_clip(1.2 * y * np.clip(t / 0.001, 0, 1), 1.3)


def crash(sr, rng, length=1.8):
    n = D.nsamp(length, sr)
    t = D.tvec(n, sr)
    nz = D.white(n, rng)
    metal = np.zeros(n)
    for _ in range(14):
        fr = rng.uniform(3000, 0.44 * sr)
        metal += np.sin(D.TWO_PI * (fr * t + rng.random()))
    x = 0.7 * nz + 0.06 * metal
    x = D.svf(x, 3800.0, sr, 0.7, "hp")
    env = 10.0 ** (-3 * t / length) * np.clip(t / 0.002, 0, 1) * (0.7 + 0.3 * np.exp(-t / 0.08))
    return x * env


def brush(sr, rng):
    n = D.nsamp(0.22, sr)
    t = D.tvec(n, sr)
    nz = D.svf(D.white(n, rng), 3200.0, sr, 0.6, "bp")
    env = np.sin(0.5 * math.pi * np.clip(t / 0.02, 0, 1)) ** 2 * 10.0 ** (-3 * np.clip(t - 0.02, 0, None) / 0.15)
    return nz * env


def woodblock(sr, rng, f=1100.0):
    n = D.nsamp(0.08, sr)
    return D.modal(n, sr, [f, f * 1.63, f * 2.9], [1.0, 0.35, 0.15], [0.05, 0.03, 0.015], rng)


class DrumKit:
    """Caches a few random variants of each drum voice so repeated hits are cheap but not identical."""

    def __init__(self, sr, seed=7, variants=4):
        self.sr = sr
        self.rng = np.random.default_rng(seed)
        self.variants = variants
        self.cache = {}

    def get(self, name, **kw):
        key = (name, tuple(sorted(kw.items())))
        if key not in self.cache:
            fn = globals()[name]
            vs = []
            for _ in range(self.variants):
                y = fn(self.sr, self.rng, **kw)
                vs.append(y / (np.max(np.abs(y)) + 1e-12))
            self.cache[key] = vs
        vs = self.cache[key]
        return vs[int(self.rng.integers(len(vs)))]
