"""qc.py - numeric quality checks for the generated audio (we can't listen, so we measure)."""
import math
import os

import numpy as np

import dsp as D


def file_stats(path):
    x, sr, raw = D.read_wav(path)
    n, ch = x.shape
    peak = float(np.max(np.abs(x))) if n else 0.0
    return dict(
        path=path, sr=sr, ch=ch, n=n, dur=n / sr, size=os.path.getsize(path), x=x,
        peak_db=D.lin_to_db(peak), rms_db=D.lin_to_db(D.rms(x)),
        dc=float(np.max(np.abs(x.mean(axis=0)))) if n else 0.0,
        nan=int(np.count_nonzero(~np.isfinite(x))),
        clip=int(np.count_nonzero((raw >= 32767) | (raw <= -32768))),
        corr=float(np.corrcoef(x[:, 0], x[:, 1])[0, 1]) if ch == 2 and n > 1 else None,
    )


def _win_rms(x, a, b):
    return math.sqrt(float(np.mean(x[a:b] ** 2)) + 1e-20)


def seam(x, sr, boundaries=None, win=0.02, rng=None):
    """Loop seam metrics.
    jump_ratio: |x[0]-x[-1]| relative to the 99.9th percentile of all sample-to-sample steps
                (<= 1 means the wrap is no bigger than an ordinary step inside the file).
    wrap_step_db: RMS of the 20 ms after the wrap minus the 20 ms before it.
    ref_step_db: the same measure at internal reference points (bar lines for music,
                random points for SFX loops) -> median and 90th percentile of |step|."""
    n = len(x)
    d = np.max(np.abs(np.diff(x, axis=0)), axis=1)
    jump = float(np.max(np.abs(x[0] - x[-1])))
    p999 = float(np.percentile(d, 99.9))
    w = D.nsamp(win, sr)
    xx = np.concatenate([x[-w:], x[:w]])
    wrap = 20 * math.log10(_win_rms(xx, w, 2 * w) / _win_rms(xx, 0, w))
    if boundaries is None:
        rng = rng if rng is not None else np.random.default_rng(0)
        boundaries = rng.integers(w, n - w, 64)
    steps = [abs(20 * math.log10(_win_rms(x, p, p + w) / _win_rms(x, p - w, p))) for p in boundaries if w <= p <= n - w]
    return dict(jump=jump, jump_lsb=jump * 32768, jump_ratio=jump / max(p999, 1e-9), wrap_step_db=wrap,
                ref_med_db=float(np.median(steps)), ref_p90_db=float(np.percentile(steps, 90)))


def seam_ok(s):
    return s["jump_ratio"] <= 1.0 and abs(s["wrap_step_db"]) <= max(3.0, s["ref_p90_db"] + 1.0)


def max_short_rms_db(mono, sr, win=0.05):
    """Loudest 50 ms RMS window (a simple loudness proxy for short SFX)."""
    w = max(1, D.nsamp(win, sr))
    if len(mono) <= w:
        return D.lin_to_db(D.rms(mono))
    c = np.cumsum(np.concatenate([[0.0], mono ** 2]))
    r = np.sqrt((c[w:] - c[:-w]) / w)
    return D.lin_to_db(float(r.max()))


def band_fraction(mono, sr, lo=0.0, hi=None):
    X = np.abs(np.fft.rfft(mono)) ** 2
    f = np.fft.rfftfreq(len(mono), 1.0 / sr)
    hi = sr / 2 + 1 if hi is None else hi
    tot = X.sum() + 1e-20
    return float(X[(f >= lo) & (f < hi)].sum() / tot)


def centroid(mono, sr):
    X = np.abs(np.fft.rfft(mono)) ** 2
    f = np.fft.rfftfreq(len(mono), 1.0 / sr)
    return float((X * f).sum() / (X.sum() + 1e-20))


def table(rows, headers):
    w = [max([len(str(h))] + [len(str(r[i])) for r in rows]) for i, h in enumerate(headers)]
    line = "  ".join(str(h).ljust(w[i]) for i, h in enumerate(headers))
    out = [line, "-" * len(line)]
    for r in rows:
        out.append("  ".join(str(c).rjust(w[i]) if isinstance(c, (int, float)) or str(c)[:1] in "-0123456789" else str(c).ljust(w[i])
                             for i, c in enumerate(r)))
    return "\n".join(out)
