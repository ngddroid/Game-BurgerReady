"""dsp.py - a tiny, dependency-free (numpy only) synthesis toolkit for Bun Voyage!

Everything here is original procedural DSP: oscillators (band-limited), FM operators,
Karplus-Strong plucks, noise, filters (one-pole, RBJ biquad, TPT state-variable),
envelopes, sweeps, reverbs (Schroeder comb/allpass and synthetic-IR convolution),
saturation, normalisation, fades, panning and 16-bit WAV I/O.

Signals are float64 numpy arrays, mono shape (n,) or stereo shape (n, 2), nominal range [-1, 1].
Sample-recursive filters run as plain Python loops over lists (fast enough on Python 3.13).
"""
import math
import wave
import zlib

import numpy as np

TWO_PI = 2.0 * math.pi


# --------------------------------------------------------------------------- helpers
def nsamp(dur, sr):
    return max(0, int(round(dur * sr)))


def tvec(n, sr):
    return np.arange(n) / sr


def rng_for(name, salt=0):
    """Deterministic RNG seeded from a string, so every render is reproducible."""
    return np.random.default_rng((zlib.crc32(name.encode("utf-8")) + salt * 7919) & 0xFFFFFFFF)


def db_to_lin(db):
    return 10.0 ** (db / 20.0)


def lin_to_db(x):
    return 20.0 * math.log10(max(float(x), 1e-12))


def midi_hz(m):
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


def cents(f, c):
    return f * 2.0 ** (c / 1200.0)


def _arr(v, n):
    a = np.asarray(v, dtype=np.float64)
    if a.ndim == 0:
        return np.full(n, float(a))
    if len(a) != n:
        raise ValueError("array length %d != %d" % (len(a), n))
    return a


def place(dst, src, at, gain=1.0):
    """Mix src into dst starting at sample index `at` (clipped to dst)."""
    at = int(at)
    if at >= len(dst) or len(src) == 0:
        return dst
    s0 = 0
    if at < 0:
        s0 = -at
        at = 0
    m = min(len(src) - s0, len(dst) - at)
    if m > 0:
        dst[at:at + m] += gain * src[s0:s0 + m]
    return dst


def place_s(dst, src, t, sr, gain=1.0):
    """place() with a time in seconds."""
    return place(dst, src, int(round(t * sr)), gain)


def fit(x, n):
    """Pad with zeros / truncate to exactly n samples."""
    if len(x) >= n:
        return x[:n].copy()
    out = np.zeros((n,) + x.shape[1:])
    out[:len(x)] = x
    return out


# --------------------------------------------------------------------------- frequency curves
def sweep(f0, f1, n, sr, dur=None, curve="exp", start=0.0, power=1.0):
    """Frequency array gliding f0 -> f1 between `start` and `start+dur` seconds (held outside)."""
    t = tvec(n, sr)
    dur = (n / sr - start) if dur is None else dur
    u = np.clip((t - start) / max(dur, 1e-9), 0.0, 1.0) ** power
    if curve == "exp":
        return f0 * (f1 / f0) ** u
    if curve == "smooth":
        u = u * u * (3 - 2 * u)
        return f0 * (f1 / f0) ** u
    return f0 + (f1 - f0) * u


def vibrato(n, sr, rate=5.5, depth_cents=15.0, delay=0.0, ramp=0.15, phase0=0.0):
    """Multiplicative pitch factor array with a delayed, ramped-in vibrato."""
    t = tvec(n, sr)
    amt = np.clip((t - delay) / max(ramp, 1e-6), 0.0, 1.0)
    return 2.0 ** (amt * depth_cents * np.sin(TWO_PI * rate * t + phase0) / 1200.0)


def smooth_random(n, sr, rate, rng, lo=-1.0, hi=1.0):
    """Smooth random control curve (cubic-ish interpolation of random points at `rate` Hz)."""
    k = max(2, int(math.ceil(n / sr * rate)) + 3)
    pts = rng.uniform(lo, hi, k)
    x = np.arange(n) / sr * rate
    i = np.floor(x).astype(int)
    f = x - i
    f = f * f * (3 - 2 * f)
    return pts[i] * (1 - f) + pts[i + 1] * f


# --------------------------------------------------------------------------- oscillators
def phase(freq, n, sr, phase0=0.0):
    """Running phase in cycles (unwrapped) for a scalar or per-sample frequency."""
    f = _arr(freq, n)
    ph = np.empty(n)
    if n:
        ph[0] = 0.0
        np.cumsum(f[:-1] / sr, out=ph[1:])
    return ph + phase0


def sine(freq, n, sr, phase0=0.0):
    return np.sin(TWO_PI * phase(freq, n, sr, phase0))


def _polyblep(t, dt):
    y = np.zeros_like(t)
    m = t < dt
    x = t[m] / dt[m]
    y[m] = x + x - x * x - 1.0
    m = t > 1.0 - dt
    x = (t[m] - 1.0) / dt[m]
    y[m] = x * x + x + x + 1.0
    return y


def saw(freq, n, sr, phase0=0.0):
    """PolyBLEP band-limited sawtooth."""
    f = _arr(freq, n)
    ph = phase(f, n, sr, phase0) % 1.0
    dt = np.clip(np.abs(f) / sr, 1e-9, 0.5)
    return 2.0 * ph - 1.0 - _polyblep(ph, dt)


def square(freq, n, sr, pw=0.5, phase0=0.0):
    """PolyBLEP band-limited pulse/square (DC removed for pw != 0.5)."""
    f = _arr(freq, n)
    pw = _arr(pw, n)
    ph = phase(f, n, sr, phase0) % 1.0
    dt = np.clip(np.abs(f) / sr, 1e-9, 0.5)
    y = np.where(ph < pw, 1.0, -1.0)
    y += _polyblep(ph, dt)
    y -= _polyblep((ph + 1.0 - pw) % 1.0, dt)
    return y - (2.0 * pw - 1.0)


def triangle(freq, n, sr, phase0=0.0, max_h=31):
    """Additive band-limited triangle (odd harmonics, smooth taper near Nyquist)."""
    f = np.abs(_arr(freq, n))
    ph = phase(f, n, sr, phase0)
    y = np.zeros(n)
    for k in range(1, max_h + 1, 2):
        g = np.clip((0.45 * sr - k * f) / (0.05 * sr), 0.0, 1.0)
        if not g.any():
            break
        sgn = -1.0 if ((k - 1) // 2) % 2 else 1.0
        y += g * sgn * np.sin(TWO_PI * k * ph) / (k * k)
    return y * (8.0 / math.pi ** 2)


def harmonics(freq, n, sr, amps, phase0=0.0):
    """Additive tone with harmonic amplitudes amps[0]=fundamental (aliasing-safe)."""
    f = np.abs(_arr(freq, n))
    ph = phase(f, n, sr, phase0)
    y = np.zeros(n)
    for k, a in enumerate(amps, start=1):
        if a == 0:
            continue
        g = np.clip((0.45 * sr - k * f) / (0.05 * sr), 0.0, 1.0)
        y += a * g * np.sin(TWO_PI * k * ph)
    return y


def fm(fc, ratio, index, n, sr, phase0=0.0, mod_freq=None, auto_limit=True):
    """Two-operator FM (phase modulation): sin(2pi*phc + I(t) * sin(2pi*phm)).

    ratio: modulator/carrier frequency ratio (ignored if mod_freq given).
    index: scalar or per-sample modulation index. auto_limit trims the index so the highest
    significant sideband (Carson's rule) stays below 0.45*sr, preventing aliasing."""
    fc = _arr(fc, n)
    fmod = fc * ratio if mod_freq is None else _arr(mod_freq, n)
    idx = _arr(index, n)
    if auto_limit:
        lim = (0.45 * sr - np.abs(fc)) / np.maximum(np.abs(fmod), 1e-6) - 1.0
        idx = np.minimum(idx, np.clip(lim, 0.0, None))
    return np.sin(TWO_PI * phase(fc, n, sr, phase0) + idx * np.sin(TWO_PI * phase(fmod, n, sr)))


def modal(n, sr, freqs, amps, t60s, rng=None, attack=0.0005):
    """Bank of exponentially decaying sinusoids (modal synthesis of struck objects)."""
    t = tvec(n, sr)
    y = np.zeros(n)
    for f, a, d in zip(freqs, amps, t60s):
        if f >= 0.47 * sr or a == 0:
            continue
        ph0 = rng.random() if rng is not None else 0.0
        y += a * np.sin(TWO_PI * (f * t + ph0)) * 10.0 ** (-3.0 * t / d)
    if attack > 0:
        y *= np.clip(t / attack, 0, 1)
    return y


# --------------------------------------------------------------------------- noise
def white(n, rng):
    return rng.uniform(-1.0, 1.0, n)


def colored_noise(n, rng, slope_db_oct=-3.0):
    """FFT-shaped noise; slope -3 dB/oct = pink, -6 = brown. Normalised to unit peak."""
    X = np.fft.rfft(rng.standard_normal(n))
    k = np.arange(len(X), dtype=float)
    k[0] = 1.0
    X *= k ** (slope_db_oct / 6.02)
    X[0] = 0.0
    y = np.fft.irfft(X, n)
    return y / (np.max(np.abs(y)) + 1e-12)


def fft_filter(x, sr, lo=None, hi=None, order=2):
    """Zero-phase Butterworth-magnitude band shaping in the frequency domain (circular).

    Use on stationary material (noise beds, whole loops); for transients prefer svf()."""
    n = len(x)
    X = np.fft.rfft(x, axis=0)
    f = np.fft.rfftfreq(n, 1.0 / sr)
    H = np.ones_like(f)
    if hi is not None:
        H *= 1.0 / np.sqrt(1.0 + (f / hi) ** (2 * order))
    if lo is not None:
        H *= 1.0 / np.sqrt(1.0 + (lo / np.maximum(f, 1e-9)) ** (2 * order))
    if X.ndim == 2:
        H = H[:, None]
    return np.fft.irfft(X * H, n, axis=0)


# --------------------------------------------------------------------------- filters
def onepole_lp(x, fc, sr):
    a = math.exp(-TWO_PI * min(fc, 0.49 * sr) / sr)
    b = 1.0 - a
    out = []
    ap = out.append
    y = 0.0
    for v in np.asarray(x, dtype=float).tolist():
        y = b * v + a * y
        ap(y)
    return np.array(out)


def onepole_hp(x, fc, sr):
    return np.asarray(x, dtype=float) - onepole_lp(x, fc, sr)


def dc_block(x, sr=44100, fc=15.0):
    """y[n] = x[n] - x[n-1] + R*y[n-1]."""
    R = math.exp(-TWO_PI * fc / sr)
    out = []
    ap = out.append
    y = 0.0
    xp = 0.0
    for v in np.asarray(x, dtype=float).tolist():
        y = v - xp + R * y
        xp = v
        ap(y)
    return np.array(out)


def biquad_coeffs(kind, fc, sr, q=0.7071, gain_db=0.0):
    """RBJ audio-EQ-cookbook biquad coefficients (b0,b1,b2,a1,a2) normalised by a0."""
    w0 = TWO_PI * min(fc, 0.49 * sr) / sr
    cw, sw = math.cos(w0), math.sin(w0)
    alpha = sw / (2.0 * q)
    A = 10.0 ** (gain_db / 40.0)
    if kind == "lp":
        b = [(1 - cw) / 2, 1 - cw, (1 - cw) / 2]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "hp":
        b = [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "bp":  # constant 0 dB peak gain
        b = [alpha, 0.0, -alpha]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "notch":
        b = [1.0, -2 * cw, 1.0]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "peak":
        b = [1 + alpha * A, -2 * cw, 1 - alpha * A]
        a = [1 + alpha / A, -2 * cw, 1 - alpha / A]
    elif kind == "lowshelf":
        sa = 2 * math.sqrt(A) * alpha
        b = [A * ((A + 1) - (A - 1) * cw + sa), 2 * A * ((A - 1) - (A + 1) * cw), A * ((A + 1) - (A - 1) * cw - sa)]
        a = [(A + 1) + (A - 1) * cw + sa, -2 * ((A - 1) + (A + 1) * cw), (A + 1) + (A - 1) * cw - sa]
    elif kind == "highshelf":
        sa = 2 * math.sqrt(A) * alpha
        b = [A * ((A + 1) + (A - 1) * cw + sa), -2 * A * ((A - 1) + (A + 1) * cw), A * ((A + 1) + (A - 1) * cw - sa)]
        a = [(A + 1) - (A - 1) * cw + sa, 2 * ((A - 1) - (A + 1) * cw), (A + 1) - (A - 1) * cw - sa]
    else:
        raise ValueError(kind)
    return b[0] / a[0], b[1] / a[0], b[2] / a[0], a[1] / a[0], a[2] / a[0]


def biquad(x, kind, fc, sr, q=0.7071, gain_db=0.0):
    """Static RBJ biquad (transposed direct form II)."""
    b0, b1, b2, a1, a2 = biquad_coeffs(kind, fc, sr, q, gain_db)
    z1 = z2 = 0.0
    out = []
    ap = out.append
    for v in np.asarray(x, dtype=float).tolist():
        y = b0 * v + z1
        z1 = b1 * v - a1 * y + z2
        z2 = b2 * v - a2 * y
        ap(y)
    return np.array(out)


def svf(x, fc, sr, q=0.7071, mode="lp"):
    """Topology-preserving (Zavalishin) state-variable filter; fc may be per-sample (stable
    under fast modulation). Modes: lp, hp, bp (unity peak gain), band (raw), notch, peak."""
    xs = np.asarray(x, dtype=float).tolist()
    n = len(xs)
    k = 1.0 / q
    out = []
    ap = out.append
    ic1 = ic2 = 0.0
    if np.ndim(fc) == 0:
        g = math.tan(math.pi * min(max(float(fc), 5.0), 0.49 * sr) / sr)
        a1 = 1.0 / (1.0 + g * (g + k))
        a2 = g * a1
        a3 = g * a2
        A1 = [a1] * n
        A2 = [a2] * n
        A3 = [a3] * n
    else:
        g = np.tan(np.pi * np.clip(_arr(fc, n), 5.0, 0.49 * sr) / sr)
        a1v = 1.0 / (1.0 + g * (g + k))
        A1 = a1v.tolist()
        A2 = (g * a1v).tolist()
        A3 = (g * g * a1v).tolist()
    m = {"lp": 0, "bp": 1, "band": 2, "hp": 3, "notch": 4, "peak": 5}[mode]
    for i in range(n):
        v0 = xs[i]
        v3 = v0 - ic2
        v1 = A1[i] * ic1 + A2[i] * v3
        v2 = ic2 + A2[i] * ic1 + A3[i] * v3
        ic1 = 2.0 * v1 - ic1
        ic2 = 2.0 * v2 - ic2
        if m == 0:
            ap(v2)
        elif m == 1:
            ap(k * v1)
        elif m == 2:
            ap(v1)
        elif m == 3:
            ap(v0 - k * v1 - v2)
        elif m == 4:
            ap(v0 - k * v1)
        else:
            ap(2.0 * v2 - v0 + k * v1)
    return np.array(out)


def resonator_bank(x, sr, freqs, qs, gains):
    """Parallel band-pass resonators (formants, body modes)."""
    y = np.zeros(len(x))
    for f, q, g in zip(freqs, qs, gains):
        y += g * svf(x, f, sr, q, "bp")
    return y


# --------------------------------------------------------------------------- envelopes
def env_ad(n, sr, attack=0.002, t60=0.3, hold=0.0):
    """Smooth (sin^2) attack, optional hold, then exponential decay with the given T60."""
    t = tvec(n, sr)
    a = np.ones(n) if attack <= 0 else np.sin(0.5 * math.pi * np.clip(t / attack, 0.0, 1.0)) ** 2
    d = 10.0 ** (-3.0 * np.clip(t - attack - hold, 0.0, None) / max(t60, 1e-6))
    return a * d


def adsr(n, sr, a=0.01, d=0.1, s=0.7, r=0.2, gate=None):
    """ADSR with exponential decay/release; gate = note-on length in seconds."""
    t = tvec(n, sr)
    gate = n / sr if gate is None else gate
    env = np.where(t < a, np.sin(0.5 * math.pi * np.clip(t / max(a, 1e-6), 0, 1)) ** 2,
                   s + (1.0 - s) * np.exp(-np.clip(t - a, 0, None) / max(d / 3.0, 1e-6)))
    # value at gate time (for smooth release)
    if gate < a:
        g_val = math.sin(0.5 * math.pi * gate / max(a, 1e-6)) ** 2
    else:
        g_val = s + (1.0 - s) * math.exp(-(gate - a) / max(d / 3.0, 1e-6))
    rel = g_val * np.exp(-np.clip(t - gate, 0, None) / max(r / 4.6, 1e-6))
    return np.where(t < gate, env, rel)


def env_points(points, n, sr, shape="lin"):
    """Piecewise envelope from [(time_s, value), ...]; shape 'lin' or 'smooth' (cosine)."""
    t = tvec(n, sr)
    ts = np.array([p[0] for p in points], dtype=float)
    vs = np.array([p[1] for p in points], dtype=float)
    if shape == "lin":
        return np.interp(t, ts, vs)
    idx = np.clip(np.searchsorted(ts, t, side="right") - 1, 0, len(ts) - 2)
    t0, t1 = ts[idx], ts[idx + 1]
    u = np.clip((t - t0) / np.maximum(t1 - t0, 1e-9), 0, 1)
    u = 0.5 - 0.5 * np.cos(math.pi * u)
    return vs[idx] + (vs[idx + 1] - vs[idx]) * u


def exp_decay(n, sr, t60, delay=0.0):
    t = tvec(n, sr)
    return 10.0 ** (-3.0 * np.clip(t - delay, 0, None) / t60) * (t >= delay)


# --------------------------------------------------------------------------- Karplus-Strong
def ks_pluck(freq, dur, sr, rng, t60=1.5, damp=0.5, bright=0.7, pick=0.18, excitation=None):
    """Karplus-Strong plucked string, block-vectorised.

    The loop filter is a 2-tap averager (weight `damp` in (0, 0.5]; 0.5 = mellow, smaller =
    brighter) convolved with a linear-interpolation fractional delay, so tuning is accurate.
    Because every tap is >= N samples back, each block of N samples is computed at once."""
    n = nsamp(dur, sr)
    P = sr / freq
    s = min(max(damp, 0.02), 0.5)
    N = int(math.floor(P - s))
    if N < 2:
        N = 2
    frac = min(max(P - s - N, 0.0), 0.999)
    h0 = (1 - s) * (1 - frac)
    h1 = (1 - s) * frac + s * (1 - frac)
    h2 = s * frac
    g = 10.0 ** (-3.0 / (t60 * freq))
    if excitation is None:
        ex = rng.uniform(-1, 1, N)
        # brightness: one-pole smoothing of the excitation burst
        a = min(max(1.0 - bright, 0.0), 0.97)
        if a > 0:
            sm = []
            yv = 0.0
            for v in ex.tolist():
                yv = (1 - a) * v + a * yv
                sm.append(yv)
            ex = np.array(sm)
        # pick position comb
        d = max(1, int(pick * N))
        ex = ex - np.roll(ex, d)
    else:
        ex = fit(np.asarray(excitation, dtype=float), N)
    ex = ex - ex.mean()
    ex /= (np.max(np.abs(ex)) + 1e-12)
    y = np.zeros(n + 2)
    m = min(N, n)
    y[2:2 + m] = ex[:m]
    for st in range(N, n, N):
        en = min(st + N, n)
        k = en - st
        b = 2 + st - N
        y[2 + st:2 + en] = g * (h0 * y[b:b + k] + h1 * y[b - 1:b - 1 + k] + h2 * y[b - 2:b - 2 + k])
    return y[2:]


# --------------------------------------------------------------------------- reverb
def _comb_block(x, D, g):
    y = x.copy()
    n = len(x)
    for s in range(D, n, D):
        e = min(s + D, n)
        y[s:e] += g * y[s - D:e - D]
    return y


def _allpass_block(x, D, g):
    n = len(x)
    y = np.empty(n)
    y[:D] = -g * x[:D]
    for s in range(D, n, D):
        e = min(s + D, n)
        y[s:e] = -g * x[s:e] + x[s - D:e - D] + g * y[s - D:e - D]
    return y


def schroeder_reverb(x, sr, rt60=1.2, mix=0.2, damp_hz=5000.0, predelay=0.01, tail=0.0):
    """Classic Schroeder/Freeverb-style reverb: 8 parallel combs + 4 series allpasses
    (block-vectorised). Returns mono dry/wet mix with `tail` seconds appended."""
    x = np.concatenate([np.asarray(x, float), np.zeros(nsamp(tail, sr))])
    sc = sr / 44100.0
    combs = [1557, 1617, 1491, 1422, 1277, 1356, 1188, 1116]
    alls = [556, 441, 341, 225]
    pd = nsamp(predelay, sr)
    xin = np.concatenate([np.zeros(pd), x])[:len(x)]
    xin = onepole_lp(xin, damp_hz, sr)
    wet = np.zeros(len(x))
    for D in combs:
        D = max(2, int(D * sc))
        g = 10.0 ** (-3.0 * D / (rt60 * sr))
        wet += _comb_block(xin, D, g)
    wet /= len(combs)
    for D in alls:
        wet = _allpass_block(wet, max(2, int(D * sc)), 0.5)
    return (1 - mix) * x + mix * wet


def make_ir(sr, rt60=1.4, predelay=0.015, length=None, stereo=True, rng=None, hf_ratio=0.45,
            lf_ratio=1.1, early=True):
    """Synthetic room impulse response: band-split decaying noise (highs die faster), a soft
    diffuse build-up and a few early reflections. Returns (n, 2) (or (n,)) with unit energy."""
    rng = rng if rng is not None else np.random.default_rng(1)
    length = rt60 * 1.15 if length is None else length
    n = nsamp(length, sr)
    t = tvec(n, sr)
    chans = []
    for c in range(2 if stereo else 1):
        nz = rng.standard_normal(n)
        lo = fft_filter(nz, sr, hi=700, order=2)
        hi = fft_filter(nz, sr, lo=3500, order=2)
        mid = nz - lo - hi
        ir = (lo * 10.0 ** (-3.0 * t / (rt60 * lf_ratio)) + mid * 10.0 ** (-3.0 * t / rt60)
              + hi * 10.0 ** (-3.0 * t / (rt60 * hf_ratio)))
        ir *= 1.0 - np.exp(-t / 0.012)  # diffuse build-up
        if early:
            for _ in range(7):
                d = rng.uniform(0.004, 0.045)
                i = nsamp(d, sr)
                if i < n:
                    ir[i] += rng.choice([-1, 1]) * rng.uniform(0.6, 1.6) * (1 - d / 0.06) * 3.0
        pd = nsamp(predelay, sr)
        ir = np.concatenate([np.zeros(pd), ir])[:n]
        ir *= np.clip((n - np.arange(n)) / (0.05 * n), 0, 1)  # fade tail end
        chans.append(ir / math.sqrt(np.sum(ir ** 2) + 1e-12))
    return np.stack(chans, axis=1) if stereo else chans[0]


def convolve(x, ir):
    """Linear FFT convolution (mono or per-channel stereo). Output length len(x)+len(ir)-1."""
    nfft = 1 << int(math.ceil(math.log2(len(x) + len(ir) - 1)))
    if x.ndim == 1 and ir.ndim == 1:
        return np.fft.irfft(np.fft.rfft(x, nfft) * np.fft.rfft(ir, nfft), nfft)[:len(x) + len(ir) - 1]
    if x.ndim == 1:
        x = np.stack([x, x], axis=1)
    if ir.ndim == 1:
        ir = np.stack([ir, ir], axis=1)
    X = np.fft.rfft(x, nfft, axis=0)
    H = np.fft.rfft(ir, nfft, axis=0)
    return np.fft.irfft(X * H, nfft, axis=0)[:len(x) + len(ir) - 1]


def fold(buf, L):
    """Circular fold: add everything past sample L back onto the start (loop rendering)."""
    out = np.zeros((L,) + buf.shape[1:])
    for s in range(0, len(buf), L):
        seg = buf[s:s + L]
        out[:len(seg)] += seg
    return out


# --------------------------------------------------------------------------- dynamics / utility
def soft_clip(x, drive=1.0):
    """tanh saturation, unity gain at full scale."""
    return np.tanh(drive * x) / math.tanh(drive)


def normalize(x, peak_db=-1.0):
    p = np.max(np.abs(x))
    return x if p <= 0 else x * (db_to_lin(peak_db) / p)


def fade(x, sr, fin=0.005, fout=0.005):
    """Raised-cosine fade-in/out (default 5 ms each) to avoid clicks."""
    x = np.array(x, dtype=float, copy=True)
    n = len(x)
    a = min(nsamp(fin, sr), n)
    b = min(nsamp(fout, sr), n)
    if a > 0:
        w = 0.5 - 0.5 * np.cos(math.pi * np.arange(a) / a)
        x[:a] *= w if x.ndim == 1 else w[:, None]
    if b > 0:
        w = 0.5 + 0.5 * np.cos(math.pi * (np.arange(b) + 1) / b)
        x[n - b:] *= w if x.ndim == 1 else w[:, None]
    return x


def loop_crossfade(x, n_loop, sr=None, xf=None):
    """Make a seamless loop of n_loop samples from a render of n_loop+xf samples by
    equal-power crossfading the tail (x[n_loop:]) into the head."""
    xf = len(x) - n_loop if xf is None else xf
    out = x[:n_loop].copy()
    u = (np.arange(xf) + 0.5) / xf
    win_in = np.sqrt(u)
    win_out = np.sqrt(1.0 - u)
    if x.ndim == 2:
        win_in = win_in[:, None]
        win_out = win_out[:, None]
    out[:xf] = x[:xf] * win_in + x[n_loop:n_loop + xf] * win_out
    return out


def pan(mono, p):
    """Constant-power pan, p in [-1 (L), 1 (R)]; returns (n, 2)."""
    a = (np.clip(p, -1, 1) + 1.0) * math.pi / 4.0
    return np.stack([mono * math.cos(a), mono * math.sin(a)], axis=1)


def rms(x):
    return float(np.sqrt(np.mean(np.square(x)))) if len(x) else 0.0


# --------------------------------------------------------------------------- WAV I/O
def to_int16(x, rng=None):
    """Float -> int16 with TPDF dither (+-1 LSB)."""
    x = np.asarray(x, dtype=float)
    if not np.all(np.isfinite(x)):
        raise ValueError("non-finite samples")
    y = x * 32767.0
    if rng is not None:
        y = y + (rng.random(y.shape) - rng.random(y.shape))
    return np.clip(np.round(y), -32768, 32767).astype("<i2")


def write_wav(path, x, sr, dither_seed=None):
    x = np.asarray(x, dtype=float)
    ch = 1 if x.ndim == 1 else x.shape[1]
    rng = np.random.default_rng(dither_seed) if dither_seed is not None else None
    data = to_int16(x, rng)
    with wave.open(path, "wb") as w:
        w.setnchannels(ch)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(data.reshape(-1).tobytes())


def read_wav(path):
    """Returns (float array (n, ch) in [-1, 1), sr, raw int16 array (n, ch))."""
    with wave.open(path, "rb") as w:
        ch = w.getnchannels()
        sr = w.getframerate()
        sw = w.getsampwidth()
        raw = w.readframes(w.getnframes())
    if sw != 2:
        raise ValueError("expected 16-bit")
    a = np.frombuffer(raw, dtype="<i2").reshape(-1, ch)
    return a.astype(float) / 32768.0, sr, a
