"""music.py - a small beat-grid sequencer and the Bun Voyage! music cues (22.05 kHz stereo).

Loops are rendered *circularly*: every note is rendered in full and anything that rings past
the loop end (note tails, reverb tails) is folded back onto the loop start, exactly as it
would sound when the file repeats. Master processing is either memoryless (tanh) or circular
(FFT high-pass), so the wrap point is sample-continuous by construction.
"""
import math

import numpy as np

import dsp as D
import instruments as I

SR = 22050


# =========================================================================== sequencer
class Song:
    def __init__(self, name, bpm, bars, sr=SR, bpb=4, s8=0.5, s16=0.5, loop=True, total=None, tail=6.0):
        self.name = name
        self.sr = sr
        self.spb = int(round(sr * 60.0 / bpm))  # integer samples per beat -> exact grid
        self.bpm = sr * 60.0 / self.spb
        self.bars = bars
        self.bpb = bpb
        self.loop = loop
        self.L = self.spb * bpb * bars if total is None else D.nsamp(total, sr)
        self.N = self.L + (D.nsamp(tail, sr) if loop else 0)
        self.s8, self.s16 = s8, s16
        self.dry = np.zeros((self.L, 2))
        self.send = np.zeros((self.L, 2))
        self.stems = []
        self.keep = {}

    def beat_pos(self, beat, swing=True):
        if not swing:
            return beat
        w = math.floor(beat + 1e-9)
        f = beat - w
        s8, s16 = self.s8, self.s16
        return w + float(np.interp(f, [0, 0.25, 0.5, 0.75, 1.0], [0, s16 * s8, s8, s8 + s16 * (1 - s8), 1.0]))

    def sec(self, beats):
        return beats * self.spb / self.sr

    def stem(self, name, level_db, send=0.15, human_ms=4.0, vel_var=0.08, swing=True, seed=None, keep=False):
        st = Stem(self, name, level_db, send, human_ms, vel_var, swing, seed or (self.name + ":" + name), keep)
        return st

    def mix(self, st):
        y = D.fold(st.buf, self.L) if self.loop else st.buf[:self.L]
        mono = y.mean(axis=1)
        win = max(1, int(0.2 * self.sr))
        k = max(1, len(mono) // win)
        r = np.sqrt(np.mean(mono[:k * win].reshape(k, win) ** 2, axis=1))
        if r.max() <= 0:
            return
        act = r[r > r.max() * 10 ** (-40 / 20.0)]
        g = D.db_to_lin(st.level_db) / math.sqrt(np.mean(act ** 2))
        y = y * g
        self.dry += y
        self.send += y * st.send
        self.stems.append((st.name, st.level_db, len(act) / k))
        if st.keep:
            self.keep[st.name] = mono * g

    def render(self, rt60=1.3, verb=1.0, predelay=0.02, hp=32.0):
        ir = D.make_ir(self.sr, rt60=rt60, predelay=predelay, stereo=True, rng=D.rng_for(self.name + ":ir"))
        wet = D.convolve(self.send, ir)
        wet = D.fold(wet, self.L) if self.loop else wet[:self.L]
        mix = self.dry + verb * wet
        if self.loop:
            mix = D.fft_filter(mix, self.sr, lo=hp, order=2)  # circular -> seam-safe
        else:
            mix = np.stack([D.biquad(mix[:, c], "hp", hp, self.sr, 0.707) for c in range(2)], axis=1)
        return mix


class Stem:
    def __init__(self, song, name, level_db, send, human_ms, vel_var, swing, seed, keep):
        self.song = song
        self.name = name
        self.level_db = level_db
        self.send = send
        self.human = human_ms / 1000.0 * song.sr
        self.vel_var = vel_var
        self.swing = swing
        self.keep = keep
        self.rng = D.rng_for(seed)
        self.buf = np.zeros((song.N, 2))

    def v(self, vel):
        return float(np.clip(vel * (1.0 + self.vel_var * self.rng.uniform(-1, 1)), 0.05, 1.0))

    def add(self, sig, beat, pan=0.0, gain=1.0, human=True, offset_s=0.0):
        s = self.song
        pos = int(round(s.beat_pos(beat, self.swing) * s.spb + offset_s * s.sr))
        if human and self.human > 0:
            pos += int(round(np.clip(self.rng.normal(0, self.human), -2.5 * self.human, 2.5 * self.human)))
        sig = D.fade(sig, s.sr, 0.0, 0.003)  # every note tail ends exactly at zero
        st = D.pan(sig, pan) if sig.ndim == 1 else sig
        if s.loop:
            pos %= s.L
        elif pos < 0:
            st = st[-pos:]
            pos = 0
        m = min(len(st), s.N - pos)
        if m > 0:
            self.buf[pos:pos + m] += gain * st[:m]


def master(mixes, peak_db=-1.0, drive=1.3, headroom=1.2):
    """Common-gain mastering for one or more mixes that must stay level-matched:
    gain so the loudest pre-clip peak hits `headroom`, tanh soft clip, then one shared
    normalisation to `peak_db`."""
    g = headroom / max(np.max(np.abs(m)) for m in mixes)
    clipped = [D.soft_clip(m * g, drive) for m in mixes]
    p = max(np.max(np.abs(c)) for c in clipped)
    return [c * (D.db_to_lin(peak_db) / p) for c in clipped]


# =========================================================================== harmony helpers
def prog(spec, bpb=4):
    segs = []
    for bar, item in enumerate(spec):
        items = [(0, bpb) + tuple(item)] if isinstance(item, tuple) else item
        for off, dur, name, root, tones in items:
            segs.append(dict(beat=bar * bpb + off, dur=dur, name=name, root=root, tones=list(tones), bar=bar))
    return segs


def chord_at(segs, beat):
    total = segs[-1]["beat"] + segs[-1]["dur"]
    b = beat % total
    for s in segs:
        if s["beat"] <= b < s["beat"] + s["dur"]:
            return s
    return segs[-1]


def bar_root(segs, bar, bpb=4):
    return chord_at(segs, bar * bpb)["root"]


def melody(bars_spec, bpb=4):
    """bars_spec: dict bar(1-based) -> [(beat, dur, midi), ...]. Returns flat (beat, dur, midi)."""
    out = []
    for bar, notes in bars_spec.items():
        for b, d, m in notes:
            out.append(((bar - 1) * bpb + b, d, m))
    return sorted(out)


def scale_shift(m, steps, scale_pcs):
    """Move midi note m by `steps` diatonic steps within the scale (pitch classes)."""
    pcs = sorted(scale_pcs)
    notes = [o * 12 + p for o in range(0, 11) for p in pcs]
    if m not in notes:
        m = min(notes, key=lambda x: abs(x - m))
    i = notes.index(m)
    return notes[i + steps]


def hz(m):
    return D.midi_hz(m)


# =========================================================================== level theme (calm / rush)
LEVEL_BPM = 118
LEVEL_PROG = prog([
    ("Gmaj7", 43, (55, 59, 62, 66)),
    ("Em7", 40, (55, 59, 62, 64)),
    ("Cmaj7", 36, (55, 59, 60, 64)),
    ("D7", 38, (54, 57, 60, 62)),
    ("Gmaj7", 43, (55, 59, 62, 66)),
    ("Em7", 40, (55, 59, 62, 64)),
    ("Cmaj7", 36, (55, 59, 60, 64)),
    [(0, 2, "D7sus4", 38, (55, 57, 60, 62)), (2, 2, "D7", 38, (54, 57, 60, 62))],
    ("Cmaj7", 36, (55, 59, 60, 64)),
    ("Bm7", 35, (54, 57, 59, 62)),
    ("Am7", 33, (55, 57, 60, 64)),
    ("D7", 38, (54, 57, 60, 62)),
    ("Gmaj7", 43, (55, 59, 62, 66)),
    ("Em7", 40, (55, 59, 62, 64)),
    ("Am7", 33, (55, 57, 60, 64)),
    ("D7", 38, (54, 57, 60, 62)),
])
_B1 = [(0, .5, 71), (.5, .5, 74), (1, 1, 79), (2, .5, 78), (2.5, .5, 79), (3, 1, 74)]
_B2 = [(0, .5, 76), (.5, .5, 79), (1, 1, 83), (2, .5, 81), (2.5, .5, 79), (3, 1, 76)]
LEVEL_MEL = melody({
    1: _B1, 2: _B2,
    3: [(0, 1, 79), (1, .5, 76), (1.5, .5, 74), (2, .5, 72), (2.5, .5, 71), (3, .5, 72), (3.5, .5, 76)],
    4: [(0, 1.5, 74), (1.5, .5, 69), (2, .5, 72), (2.5, .5, 71), (3, 1, 69)],
    5: _B1, 6: _B2,
    7: [(0, .5, 79), (.5, .5, 81), (1, 1, 83), (2, .5, 81), (2.5, .5, 79), (3, 1, 76)],
    8: [(0, .5, 74), (.5, .5, 76), (1, 1, 79), (2, 1, 78), (3, 1, 81)],
    9: [(0, 1.5, 76), (1.5, .5, 74), (2, .5, 76), (2.5, 1.5, 79)],
    10: [(0, 1.5, 78), (1.5, .5, 76), (2, .5, 74), (2.5, 1.5, 78)],
    11: [(0, 1, 76), (1, .5, 72), (1.5, .5, 76), (2, 1, 81), (3, .5, 79), (3.5, .5, 76)],
    12: [(0, 1, 78), (1, 1, 81), (2, .5, 84), (2.5, .5, 83), (3, 1, 81)],
    13: _B1, 14: _B2,
    15: [(0, .5, 84), (.5, .5, 83), (1, .5, 81), (1.5, .5, 79), (2, 1, 76), (3, 1, 79)],
    16: [(0, 1, 78), (1, .5, 76), (1.5, .5, 74), (2, .5, 72), (2.5, .5, 69), (3, .5, 66), (3.5, .5, 69)],
})
G_MAJOR = (7, 9, 11, 0, 2, 4, 6)


def _level_shared(song):
    """Layers that are bit-identical in calm and rush (same seeds, same calls)."""
    sr = song.sr
    # warm pad, whole-bar chords
    st = song.stem("pad", -31.0, send=0.35, human_ms=0, seed="level:pad")
    for seg in LEVEL_PROG:
        st.add(I.pad([hz(m) for m in seg["tones"]], song.sec(seg["dur"]) * 0.98, sr, st.rng, cutoff=1200.0,
                     attack=0.35, release=0.9), seg["beat"], human=False)
    song.mix(st)
    # Karplus-Strong guitar strums: down on 1, up on the "and" of 3, ghost on the "and" of 4
    st = song.stem("guitar", -25.0, send=0.22, human_ms=5, seed="level:guitar")
    for bar in range(16):
        for off, vel, up in ((0.0, 0.85, False), (2.5, 0.6, True), (3.5, 0.35, True)):
            seg = chord_at(LEVEL_PROG, bar * 4 + off)
            notes = seg["tones"] + [seg["tones"][0] + 12]
            notes = notes[::-1] if up else notes
            for i, m in enumerate(notes):
                y = I.pluck(hz(m), st.v(vel), sr, rng=st.rng, t60=1.2 if not up else 0.7, damp=0.42, bright=0.55)
                st.add(y, bar * 4 + off, pan=-0.5 + 0.06 * i, offset_s=0.012 * i)
    song.mix(st)
    # shaker on swung 8ths
    kit = I.DrumKit(sr, seed=21)
    st = song.stem("shaker", -31.0, send=0.08, human_ms=4, seed="level:shaker")
    for b in np.arange(0, 64, 0.5):
        st.add(kit.get("shaker"), b, pan=0.55, gain=st.v(0.85 if b % 1 else 0.5))
    song.mix(st)


def _kalimba_lead(song, level, seed):
    st = song.stem("kalimba", level, send=0.25, human_ms=3, seed=seed)
    for b, d, m in LEVEL_MEL:
        st.add(I.kalimba(hz(m), st.v(0.85), song.sr), b, pan=0.08)
    song.mix(st)


def level_calm():
    song = Song("level_calm", LEVEL_BPM, 16, s8=0.56, s16=0.5)
    sr = song.sr
    _level_shared(song)
    _kalimba_lead(song, -19.0, "level:kalimba")
    kit = I.DrumKit(sr, seed=5)
    # light kit
    st = song.stem("kick", -21.0, send=0.04, human_ms=2, keep=True)
    for bar in range(16):
        st.add(kit.get("kick", soft=True), bar * 4, gain=st.v(0.95))
        st.add(kit.get("kick", soft=True), bar * 4 + 2, gain=st.v(0.75))
        if bar % 4 == 3:
            st.add(kit.get("kick", soft=True), bar * 4 + 2.5, gain=st.v(0.45))
    song.mix(st)
    st = song.stem("rim", -27.0, send=0.18, human_ms=3)
    for bar in range(16):
        for b in (1, 3):
            st.add(kit.get("rim"), bar * 4 + b, pan=-0.2, gain=st.v(0.8))
        if bar % 8 == 7:
            for b in (3.5, 3.75):
                st.add(kit.get("rim"), bar * 4 + b, pan=-0.12, gain=st.v(0.45))
    song.mix(st)
    st = song.stem("hat", -33.0, send=0.06, human_ms=3)
    for bar in range(8, 16):
        for b in (0.5, 1.5, 2.5, 3.5):
            st.add(kit.get("hat"), bar * 4 + b, pan=0.3, gain=st.v(0.7))
    song.mix(st)
    # soft bass: root, root, fifth, octave, approach
    st = song.stem("bass", -17.0, send=0.03, human_ms=3, keep=True)
    for bar in range(16):
        seg = chord_at(LEVEL_PROG, bar * 4)
        r = seg["root"]
        nxt = bar_root(LEVEL_PROG, (bar + 1) % 16)
        appr = nxt - 1 if bar % 2 == 0 else nxt + 2
        for b, d, m, v in ((0, 1.4, r, 0.95), (1.5, 0.45, r, 0.65), (2.5, 0.45, r + 7, 0.8), (3, 0.4, r + 12, 0.6),
                           (3.5, 0.45, appr, 0.75)):
            st.add(I.bass_soft(hz(m), st.v(v), sr, dur=song.sec(d)), bar * 4 + b)
    song.mix(st)
    # sparse marimba arpeggios (A sections), quarter notes in B
    st = song.stem("marimba", -26.0, send=0.22, human_ms=4)
    for bar in range(16):
        if 8 <= bar < 12:
            rhythm = [(0, 0), (1, 1), (2, 2), (3, 3)]
        else:
            rhythm = [(0, 0), (0.5, 2), (1.5, 1), (2, 3), (3, 2), (3.5, 1)]
        for b, i in rhythm:
            seg = chord_at(LEVEL_PROG, bar * 4 + b)
            tones = sorted(seg["tones"])
            st.add(I.marimba(hz(tones[i % 4] + 12), st.v(0.7), sr), bar * 4 + b, pan=0.5)
    song.mix(st)
    # ocarina doubles the melody in the B section
    st = song.stem("ocarina", -23.0, send=0.3, human_ms=3)
    for b, d, m in LEVEL_MEL:
        if 32 <= b < 48:
            st.add(I.lead_soft(hz(m), st.v(0.8), sr, dur=song.sec(d) * 0.92, rng=st.rng), b, pan=-0.25)
    song.mix(st)
    return song


def level_rush():
    song = Song("level_rush", LEVEL_BPM, 16, s8=0.56, s16=0.53)
    sr = song.sr
    _level_shared(song)
    _kalimba_lead(song, -25.0, "level:kalimba")
    kit = I.DrumKit(sr, seed=6)
    # driving kit: four on the floor, backbeat snare+clap, 16th hats, congas, tambourine
    st = song.stem("kick", -18.0, send=0.03, human_ms=1.5, keep=True)
    for bar in range(16):
        for b in range(4):
            st.add(kit.get("kick"), bar * 4 + b, gain=st.v(1.0 if b % 2 == 0 else 0.85))
        if bar % 4 == 3:
            st.add(kit.get("kick"), bar * 4 + 3.75, gain=st.v(0.5))
    song.mix(st)
    st = song.stem("snare", -22.0, send=0.15, human_ms=2)
    for bar in range(16):
        for b in (1, 3):
            st.add(kit.get("snare"), bar * 4 + b, pan=-0.08, gain=st.v(0.9))
        if bar % 8 == 7:
            for i, b in enumerate((3.25, 3.5, 3.75)):
                st.add(kit.get("snare"), bar * 4 + b, pan=-0.08, gain=st.v(0.4 + 0.15 * i))
    song.mix(st)
    st = song.stem("clap", -26.0, send=0.25, human_ms=2)
    for bar in range(16):
        for b in (1, 3):
            st.add(kit.get("clap"), bar * 4 + b, pan=0.12, gain=st.v(0.85), offset_s=0.004)
    song.mix(st)
    st = song.stem("hats", -27.0, send=0.05, human_ms=2)
    acc = (0.55, 0.3, 0.85, 0.35)
    for bar in range(16):
        for i in range(16):
            b = bar * 4 + i * 0.25
            if bar >= 8 and i % 4 == 2:
                st.add(kit.get("hat", open_=True), b, pan=0.3, gain=st.v(0.6))
            else:
                st.add(kit.get("hat"), b, pan=0.3, gain=st.v(acc[i % 4]))
    song.mix(st)
    st = song.stem("congas", -26.0, send=0.12, human_ms=4)
    for bar in range(16):
        for b, f, p, v in ((0.75, 330, 0.45, 0.7), (1.5, 230, -0.4, 0.9), (2.25, 330, 0.45, 0.6), (2.75, 330, 0.45, 0.75),
                           (3.5, 230, -0.4, 0.85)):
            st.add(kit.get("conga", f=f), bar * 4 + b, pan=p, gain=st.v(v))
    song.mix(st)
    st = song.stem("tambourine", -30.0, send=0.1, human_ms=3)
    for bar in range(16):
        for b in (1, 3):
            st.add(kit.get("tambourine"), bar * 4 + b, pan=0.55, gain=st.v(0.9))
        if bar >= 8:
            for b in (0.5, 1.5, 2.5, 3.5):
                st.add(kit.get("tambourine"), bar * 4 + b, pan=0.55, gain=st.v(0.5))
    song.mix(st)
    st = song.stem("crash", -27.0, send=0.2, human_ms=0)
    for bar in (0, 8):
        st.add(kit.get("crash"), bar * 4, pan=-0.3, gain=0.9)
    song.mix(st)
    st = song.stem("toms", -24.0, send=0.18, human_ms=2)
    for bar in (7, 15):
        for i, (b, f) in enumerate(((3.0, 180), (3.25, 150), (3.5, 120), (3.75, 95))):
            st.add(kit.get("tom", f=f), bar * 4 + b, pan=0.4 - 0.25 * i, gain=st.v(0.8))
    song.mix(st)
    # octave-pumping bass, same roots
    st = song.stem("bass", -17.0, send=0.02, human_ms=1.5, keep=True)
    for bar in range(16):
        r = bar_root(LEVEL_PROG, bar)
        nxt = bar_root(LEVEL_PROG, (bar + 1) % 16)
        appr = nxt - 1 if bar % 2 == 0 else nxt + 2
        pat = [(0, r, 1.0), (0.5, r + 12, 0.7), (1, r, 0.9), (1.5, r + 12, 0.7), (2, r, 0.95), (2.5, r + 12, 0.7),
               (3, r + 7, 0.85), (3.5, appr, 0.8)]
        for b, m, v in pat:
            st.add(I.bass_drive(hz(m), st.v(v), sr, dur=song.sec(0.42), cutoff=800.0), bar * 4 + b)
    song.mix(st)
    # 16th marimba arpeggios
    st = song.stem("marimba", -25.0, send=0.18, human_ms=3)
    pat = (0, 1, 2, 3, 4, 3, 2, 1)
    for bar in range(16):
        for i in range(16):
            b = bar * 4 + i * 0.25
            tones = sorted(chord_at(LEVEL_PROG, b)["tones"])
            tones = tones + [tones[0] + 12]
            st.add(I.marimba(hz(tones[pat[i % 8]] + 12), st.v(0.75 if i % 4 == 0 else 0.55), sr), b,
                   pan=0.38 if i % 2 == 0 else 0.2)
    song.mix(st)
    # bright lead + glock octave doubling (B and last A) + diatonic third harmony (last A)
    st = song.stem("lead", -18.0, send=0.22, human_ms=2)
    for b, d, m in LEVEL_MEL:
        st.add(I.lead_bright(hz(m), st.v(0.85), sr, dur=song.sec(d) * 0.9, rng=st.rng), b)
    song.mix(st)
    st = song.stem("glock", -27.0, send=0.3, human_ms=2)
    for b, d, m in LEVEL_MEL:
        if b >= 32:
            st.add(I.glock(hz(m + 12), st.v(0.8), sr, t60=0.9), b, pan=0.3)
    song.mix(st)
    st = song.stem("harmony", -25.0, send=0.25, human_ms=3)
    for b, d, m in LEVEL_MEL:
        if b >= 48:
            st.add(I.lead_soft(hz(scale_shift(m, -2, G_MAJOR)), st.v(0.7), sr, dur=song.sec(d) * 0.9, rng=st.rng), b,
                   pan=-0.3)
    song.mix(st)
    return song


# =========================================================================== menu theme
MENU_BPM = 90
MENU_PROG = prog([
    ("Fmaj7", 41, (53, 57, 60, 64)),
    ("Dm7", 38, (53, 57, 60, 62)),
    ("Gm7", 43, (53, 55, 58, 62)),
    ("C7", 36, (52, 55, 58, 60)),
    ("Fmaj7", 41, (53, 57, 60, 64)),
    ("Dm7", 38, (53, 57, 60, 62)),
    ("Gm7", 43, (53, 55, 58, 62)),
    ("C7", 36, (52, 55, 58, 60)),
    ("Bbmaj7", 34, (53, 57, 58, 62)),
    ("Am7", 33, (52, 55, 57, 60)),
    ("Gm7", 31, (53, 55, 58, 62)),
    [(0, 2, "C7sus4", 36, (53, 55, 58, 60)), (2, 2, "C7", 36, (52, 55, 58, 60))],
])
_M1 = [(0, .5, 69), (.5, .5, 72), (1, 1, 76), (2, .5, 74), (2.5, 1.5, 72)]
MENU_MEL = melody({
    1: _M1,
    2: [(0, .5, 74), (.5, .5, 77), (1, 1, 81), (2, .5, 79), (2.5, .5, 77), (3, 1, 74)],
    3: [(0, .5, 70), (.5, .5, 74), (1, 1, 77), (2, .5, 76), (2.5, .5, 74), (3, 1, 70)],
    4: [(0, 1.5, 72), (1.5, .5, 76), (2, 1, 79), (3, 1, 76)],
    5: _M1,
    6: [(0, .5, 74), (.5, .5, 77), (1, .5, 81), (1.5, 1.5, 84), (3, 1, 81)],
    7: [(0, .5, 79), (.5, .5, 77), (1, .5, 74), (1.5, .5, 70), (2, .5, 74), (2.5, .5, 77), (3, 1, 76)],
    8: [(0, 2, 72), (3, .5, 67), (3.5, .5, 69)],
    9: [(0, .5, 70), (.5, .5, 74), (1, .5, 77), (1.5, 1.5, 81), (3, .5, 79), (3.5, .5, 77)],
    10: [(0, 1.5, 76), (1.5, .5, 72), (2, .5, 76), (2.5, 1.5, 79)],
    11: [(0, .5, 77), (.5, .5, 76), (1, 1, 74), (2, .5, 70), (2.5, .5, 74), (3, 1, 77)],
    12: [(0, 1, 77), (1, 1, 79), (2, .5, 76), (2.5, .5, 74), (3, .5, 72), (3.5, .5, 67)],
})


def menu_theme():
    song = Song("menu_theme", MENU_BPM, 12, s8=0.63, s16=0.5)
    sr = song.sr
    kit = I.DrumKit(sr, seed=9)
    st = song.stem("pad", -31.0, send=0.4, human_ms=0)
    for seg in MENU_PROG:
        st.add(I.pad([hz(m) for m in seg["tones"]], song.sec(seg["dur"]) * 0.98, sr, st.rng, cutoff=1000.0,
                     attack=0.5, release=1.2), seg["beat"], human=False)
    song.mix(st)
    # FM electric piano, Charleston comping
    st = song.stem("epiano", -22.0, send=0.3, human_ms=6)
    for bar in range(12):
        hits = [(0.0, 1.2, 0.7), (1.5, 0.45, 0.55)]
        if bar % 4 == 3:
            hits.append((3.5, 0.4, 0.45))
        for b, d, v in hits:
            seg = chord_at(MENU_PROG, bar * 4 + b)
            vv = st.v(v)
            for i, m in enumerate(seg["tones"]):
                st.add(I.epiano(hz(m), vv, sr, dur=song.sec(d)), bar * 4 + b, pan=-0.45 + 0.3 * i, offset_s=0.006 * i)
    song.mix(st)
    # walking upright bass
    st = song.stem("bass", -17.0, send=0.05, human_ms=5, keep=True)
    for bar in range(12):
        seg = chord_at(MENU_PROG, bar * 4)
        r = seg["root"]
        th = 3 if seg["name"].startswith(("Dm", "Gm", "Am")) else 4
        nxt = bar_root(MENU_PROG, (bar + 1) % 12)
        appr = nxt + 1 if bar % 2 else nxt - 1
        for b, m, v in ((0, r, 0.95), (1, r + th, 0.7), (2, r + 7, 0.85), (3, appr, 0.7)):
            st.add(I.bass_upright(hz(m), st.v(v), sr, dur=song.sec(0.85), rng=st.rng), bar * 4 + b)
    song.mix(st)
    # soft kit: kick, brush backbeat, swung ride-ish hats, shaker
    st = song.stem("kick", -22.0, send=0.04, human_ms=3, keep=True)
    for bar in range(12):
        st.add(kit.get("kick", soft=True), bar * 4, gain=st.v(0.95))
        st.add(kit.get("kick", soft=True), bar * 4 + 2, gain=st.v(0.7))
        if bar % 2 == 1:
            st.add(kit.get("kick", soft=True), bar * 4 + 3.5, gain=st.v(0.4))
    song.mix(st)
    st = song.stem("brush", -27.0, send=0.2, human_ms=5)
    for bar in range(12):
        for b in (1, 3):
            st.add(kit.get("brush"), bar * 4 + b, pan=-0.15, gain=st.v(0.85))
            st.add(kit.get("rim"), bar * 4 + b, pan=-0.15, gain=st.v(0.25))
    song.mix(st)
    st = song.stem("ride", -31.0, send=0.12, human_ms=4)
    for bar in range(12):
        for b, v in ((0, 0.8), (1, 0.7), (1.5, 0.45), (2, 0.8), (3, 0.7), (3.5, 0.45)):
            st.add(kit.get("hat"), bar * 4 + b, pan=0.45, gain=st.v(v))
    song.mix(st)
    st = song.stem("shaker", -33.0, send=0.08, human_ms=4)
    for b in np.arange(0, 48, 0.5):
        st.add(kit.get("shaker"), b, pan=-0.55, gain=st.v(0.8 if b % 1 else 0.45))
    song.mix(st)
    # kalimba melody (phrases 1-2), ocarina + kalimba in the bridge
    st = song.stem("kalimba", -19.0, send=0.28, human_ms=4)
    for b, d, m in MENU_MEL:
        st.add(I.kalimba(hz(m), st.v(0.85 if b < 32 else 0.6), sr), b, pan=0.1)
    song.mix(st)
    st = song.stem("ocarina", -22.0, send=0.32, human_ms=4)
    for b, d, m in MENU_MEL:
        if b >= 32:
            st.add(I.lead_soft(hz(m), st.v(0.8), sr, dur=song.sec(d) * 0.9, rng=st.rng), b, pan=-0.12)
    song.mix(st)
    # marimba answer phrases at the end of every 2nd bar, and a soft glock on phrase starts
    st = song.stem("marimba", -26.0, send=0.25, human_ms=5)
    for bar in range(1, 12, 2):
        tones = sorted(chord_at(MENU_PROG, bar * 4 + 3)["tones"])
        for b, i in ((2.5, 0), (3, 1), (3.5, 3)):
            st.add(I.marimba(hz(tones[i] + 12), st.v(0.65), sr), bar * 4 + b, pan=0.55)
    song.mix(st)
    st = song.stem("glock", -30.0, send=0.35, human_ms=0)
    for bar in (0, 4, 8):
        m = MENU_MEL[[i for i, n in enumerate(MENU_MEL) if n[0] >= bar * 4][0]][2]
        st.add(I.glock(hz(m + 12), 0.7, sr, t60=1.4), bar * 4, pan=0.25)
    song.mix(st)
    return song


# =========================================================================== boss theme
BOSS_BPM = 150
_DM = ("Dm", 38, (57, 62, 65, 69))
_BB = ("Bb", 34, (58, 62, 65, 70))
_C = ("C", 36, (55, 60, 64, 67))
_A7 = ("A7", 33, (55, 61, 64, 69))
_GM = ("Gm", 43, (55, 58, 62, 67))
_EB = ("Eb", 39, (55, 58, 63, 67))
BOSS_PROG = prog([_DM, _BB, _C, _A7, _DM, _BB, _C, _A7,
                  _GM, _DM, _BB, _A7, _GM, _DM, _EB, _A7,
                  _DM, _DM, _BB, _A7])
_X1 = [(0, .75, 81), (.75, .75, 77), (1.5, .5, 74), (2, .5, 76), (2.5, .5, 77), (3, .5, 79), (3.5, .5, 77)]
_X2 = [(0, .75, 77), (.75, .75, 74), (1.5, .5, 70), (2, .5, 72), (2.5, .5, 74), (3, .5, 77), (3.5, .5, 74)]
_X3 = [(0, .75, 76), (.75, .75, 72), (1.5, .5, 67), (2, .5, 69), (2.5, .5, 72), (3, .5, 76), (3.5, .5, 79)]
BOSS_MEL = melody({
    1: _X1, 2: _X2, 3: _X3,
    4: [(0, 1.5, 81), (1.5, .5, 79), (2, .5, 76), (2.5, .5, 73), (3, 1, 69)],
    5: _X1, 6: _X2, 7: _X3,
    8: [(0, .5, 69), (.5, .5, 73), (1, .5, 76), (1.5, .5, 79), (2, .5, 81), (2.5, .5, 85), (3, 1, 81)],
    9: [(0, 1.5, 74), (1.5, .5, 70), (2, .5, 74), (2.5, 1.5, 79)],
    10: [(0, 1.5, 77), (1.5, .5, 76), (2, .5, 74), (2.5, 1.5, 81)],
    11: [(0, 1, 82), (1, .5, 81), (1.5, .5, 79), (2, 1, 77), (3, 1, 74)],
    12: [(0, 1.5, 76), (1.5, .5, 73), (2, .5, 76), (2.5, 1.5, 81)],
    13: [(0, 1, 79), (1, 1, 82), (2, .5, 81), (2.5, .5, 79), (3, 1, 77)],
    14: [(0, 1, 77), (1, 1, 81), (2, .5, 79), (2.5, .5, 77), (3, .5, 76), (3.5, .5, 74)],
    15: [(0, 1, 75), (1, 1, 79), (2, 1.5, 82), (3.5, .5, 79)],
    16: [(0, 2, 81), (2, .5, 79), (2.5, .5, 76), (3, .5, 73), (3.5, .5, 76)],
    20: [(3, .25, 76), (3.25, .25, 77), (3.5, .25, 79), (3.75, .25, 80)],
})


def boss_theme():
    song = Song("boss_theme", BOSS_BPM, 20, s8=0.5, s16=0.53)
    sr = song.sr
    kit = I.DrumKit(sr, seed=13)
    A = set(range(0, 8))
    B = set(range(8, 16))
    C = set(range(16, 20))
    st = song.stem("kick", -17.0, send=0.03, human_ms=1.5, keep=True)
    for bar in range(20):
        beats = (0, 1.5, 2, 3) if bar in C and bar < 18 else (0, 1, 2, 3)
        for b in beats:
            st.add(kit.get("kick"), bar * 4 + b, gain=st.v(1.0 if b in (0, 2) else 0.85))
        if bar in A or bar in B:
            st.add(kit.get("kick"), bar * 4 + 2.75, gain=st.v(0.55))
    song.mix(st)
    st = song.stem("snare", -21.0, send=0.15, human_ms=2)
    for bar in range(20):
        if bar in A or bar in B:
            for b in (1, 3):
                st.add(kit.get("snare"), bar * 4 + b, pan=-0.08, gain=st.v(0.95))
        if bar in (18, 19):  # 16th roll building into the loop start
            for i in range(16):
                k = (bar - 18) * 16 + i
                st.add(kit.get("snare"), bar * 4 + i * 0.25, pan=-0.08, gain=0.15 + 0.85 * (k / 31.0) ** 1.5)
    song.mix(st)
    st = song.stem("clap", -25.0, send=0.25, human_ms=2)
    for bar in sorted(B):
        for b in (1, 3):
            st.add(kit.get("clap"), bar * 4 + b, pan=0.12, gain=st.v(0.85), offset_s=0.004)
    song.mix(st)
    st = song.stem("hats", -27.0, send=0.05, human_ms=1.5)
    acc = (0.6, 0.3, 0.8, 0.35)
    for bar in range(20):
        if bar in (16, 17):
            steps = range(0, 16, 2)
        else:
            steps = range(16)
        for i in steps:
            b = bar * 4 + i * 0.25
            if bar in A and i % 4 == 2:
                st.add(kit.get("hat", open_=True), b, pan=0.3, gain=st.v(0.55))
            else:
                st.add(kit.get("hat"), b, pan=0.3, gain=st.v(acc[i % 4]))
    song.mix(st)
    st = song.stem("crash", -26.0, send=0.2, human_ms=0)
    for bar in (0, 8, 16):
        st.add(kit.get("crash"), bar * 4, pan=-0.3, gain=0.9)
    song.mix(st)
    st = song.stem("toms", -21.0, send=0.2, human_ms=2)
    for bar in (16, 17, 18):
        for b, f, p in ((0, 85, -0.2), (0.75, 120, 0.2), (1.5, 85, -0.2), (2, 120, 0.2), (3, 160, 0.35), (3.5, 120, 0.2)):
            st.add(kit.get("tom", f=f), bar * 4 + b, pan=p, gain=st.v(0.9))
    for bar in (7, 15):
        for i, (b, f) in enumerate(((3.0, 180), (3.25, 150), (3.5, 120), (3.75, 95))):
            st.add(kit.get("tom", f=f), bar * 4 + b, pan=0.4 - 0.25 * i, gain=st.v(0.85))
    song.mix(st)
    # pumping octave bass with 16th pickups
    st = song.stem("bass", -16.0, send=0.02, human_ms=1, keep=True)
    for bar in range(20):
        r = bar_root(BOSS_PROG, bar)
        pat = [(b * 0.5, r + (12 if b % 2 else 0), 1.0 if b % 2 == 0 else 0.75) for b in range(8)]
        if bar % 2 == 1:
            pat = pat[:-1] + [(3.5, r + 12, 0.8), (3.75, r + 7, 0.7)]
        for b, m, v in pat:
            st.add(I.bass_drive(hz(m), st.v(v), sr, dur=song.sec(0.4 if b % 0.5 == 0 else 0.2), cutoff=1000.0), bar * 4 + b)
    song.mix(st)
    # brass: syncopated stabs in A and C, sustained swells in B
    st = song.stem("brass", -21.0, send=0.22, human_ms=2)
    for bar in range(20):
        seg = chord_at(BOSS_PROG, bar * 4)
        if bar in B:
            for i, m in enumerate(seg["tones"]):
                st.add(I.brass(hz(m), st.v(0.6), sr, dur=song.sec(3.8), bright=0.6), bar * 4, pan=-0.3 + 0.2 * i)
        else:
            for b, d, v in ((0, 0.4, 1.0), (0.75, 0.25, 0.7), (1.5, 0.4, 0.85), (3, 0.25, 0.7), (3.5, 0.4, 0.9)):
                vv = st.v(v)
                for i, m in enumerate(seg["tones"]):
                    st.add(I.brass(hz(m), vv, sr, dur=song.sec(d), bright=1.0), bar * 4 + b, pan=-0.3 + 0.2 * i)
    song.mix(st)
    # marimba 16th ostinato in A and C
    st = song.stem("marimba", -25.0, send=0.15, human_ms=2)
    pat = (0, 2, 1, 2, 3, 2, 1, 2)
    for bar in sorted(A | C):
        for i in range(16):
            b = bar * 4 + i * 0.25
            tones = sorted(chord_at(BOSS_PROG, b)["tones"])
            st.add(I.marimba(hz(tones[pat[i % 8]] + 12), st.v(0.75 if i % 4 == 0 else 0.55), sr), b, pan=0.4)
    song.mix(st)
    # muted power-chord guitar chugs in A and B
    st = song.stem("guitar", -27.0, send=0.08, human_ms=3)
    for bar in sorted(A | B):
        r = bar_root(BOSS_PROG, bar) + 12
        for i in range(8):
            v = st.v(0.9 if i % 2 == 0 else 0.6)
            for j, m in enumerate((r, r + 7, r + 12)):
                st.add(I.pluck(hz(m), v, sr, rng=st.rng, mute=True), bar * 4 + i * 0.5, pan=-0.45, offset_s=0.004 * j)
    song.mix(st)
    # dark pad under B
    st = song.stem("pad", -29.0, send=0.35, human_ms=0)
    for seg in BOSS_PROG:
        if seg["bar"] in B:
            st.add(I.pad([hz(m - 12) for m in seg["tones"]], song.sec(seg["dur"]) * 0.98, sr, st.rng, cutoff=900.0,
                         attack=0.3, release=0.6), seg["beat"], human=False)
    song.mix(st)
    # lead + brass doubling an octave below in B
    st = song.stem("lead", -17.0, send=0.2, human_ms=1.5)
    for b, d, m in BOSS_MEL:
        st.add(I.lead_bright(hz(m), st.v(0.9), sr, dur=song.sec(d) * 0.88, rng=st.rng, cutoff=3400.0), b)
    song.mix(st)
    st = song.stem("lead_brass", -24.0, send=0.2, human_ms=2)
    for b, d, m in BOSS_MEL:
        if 32 <= b < 64:
            st.add(I.brass(hz(m - 12), st.v(0.8), sr, dur=song.sec(d) * 0.9), b, pan=-0.2)
    song.mix(st)
    # riser into the loop point
    st = song.stem("riser", -29.0, send=0.3, human_ms=0)
    n = D.nsamp(song.sec(8), sr)
    rng = st.rng
    fc = D.sweep(300.0, 7000.0, n, sr)
    rz = D.svf(D.white(n, rng), fc, sr, 1.5, "bp") * np.linspace(0, 1, n) ** 2
    rz = D.fade(rz, sr, 0.005, 0.01)
    st.add(rz, 18 * 4, human=False)
    song.mix(st)
    return song


# =========================================================================== jingles
def victory_jingle():
    song = Song("victory_jingle", 150, 2, loop=False, total=3.0)
    sr = song.sr
    kit = I.DrumKit(sr, seed=17)
    trip = 1.0 / 3.0
    mel = [(0, trip, 67), (trip, trip, 67), (2 * trip, trip, 67), (1, 0.5, 72), (1.5, 0.5, 76), (2, 1, 79), (3, 0.5, 74),
           (3.5, 0.5, 79), (4, 3.0, 84)]
    st = song.stem("brass_lead", -16.0, send=0.25, human_ms=0, swing=False)
    for b, d, m in mel:
        st.add(I.brass(hz(m), 0.95, sr, dur=song.sec(d) * 0.9, bright=1.2), b)
    song.mix(st)
    st = song.stem("marimba", -22.0, send=0.25, human_ms=0, swing=False)
    for b, d, m in mel:
        st.add(I.marimba(hz(m + 12), 0.8, sr), b, pan=0.35)
    for i in range(12):  # tremolo roll on the final chord
        st.add(I.marimba(hz((84, 88, 91)[i % 3]), 0.7 * (1 - i / 14), sr), 4 + i * 0.25, pan=0.35)
    song.mix(st)
    chords = [((60, 64, 67), 1, 1, "C"), ((57, 60, 64, 67), 2, 1, "Am7"), ((55, 59, 62, 67), 3, 1, "G"),
              ((60, 64, 67, 72), 4, 3, "C")]
    st = song.stem("brass_chords", -22.0, send=0.3, human_ms=0, swing=False)
    for tones, b, d, _ in chords:
        for i, m in enumerate(tones):
            st.add(I.brass(hz(m), 0.6, sr, dur=song.sec(d) * 0.9, bright=0.6), b, pan=-0.35 + 0.23 * i)
    song.mix(st)
    st = song.stem("pad", -28.0, send=0.4, human_ms=0, swing=False)
    st.add(I.pad([hz(m) for m in (60, 64, 67, 71)], song.sec(2.6), sr, st.rng, cutoff=2000.0, attack=0.2, release=0.8), 4)
    song.mix(st)
    st = song.stem("bass", -18.0, send=0.05, human_ms=0, swing=False, keep=True)
    for b, m, d in ((1, 48, 1), (2, 45, 1), (3, 43, 1), (4, 36, 2.5)):
        st.add(I.bass_soft(hz(m), 0.9, sr, dur=song.sec(d) * 0.9), b)
    song.mix(st)
    st = song.stem("drums", -19.0, send=0.15, human_ms=0, swing=False, keep=True)
    for b in (0, trip, 2 * trip):
        st.add(kit.get("snare"), b, gain=0.55)
    for b in (1, 2, 3, 4):
        st.add(kit.get("kick"), b, gain=1.0)
    for i in range(8):
        st.add(kit.get("snare"), 3 + i / 8.0, gain=0.25 + 0.07 * i)
    st.add(kit.get("crash"), 4, pan=0.2, gain=0.8)
    song.mix(st)
    st = song.stem("sparkle", -27.0, send=0.4, human_ms=0, swing=False)
    for i, m in enumerate((84, 88, 91, 96, 100)):
        st.add(I.glock(hz(m + 12), 0.8, sr, t60=1.2), 4, pan=-0.5 + 0.25 * i, offset_s=0.045 * i)
    song.mix(st)
    return song


def fail_jingle():
    song = Song("fail_jingle", 100, 2, loop=False, total=2.5)
    sr = song.sr
    kit = I.DrumKit(sr, seed=19)
    st = song.stem("kalimba", -17.0, send=0.3, human_ms=0, swing=False)
    for b, m in ((0, 79), (0.33, 76), (0.66, 72)):
        st.add(I.kalimba(hz(m), 0.8, sr), b, pan=0.15)
    st.add(I.kalimba(hz(79), 0.45, sr), 3.0, pan=0.3)
    st.add(I.kalimba(hz(84), 0.55, sr), 3.25, pan=0.3)
    song.mix(st)
    # soft muted horn: Ab (over Fm, the bittersweet iv minor) sighing down to G, gentle wobble
    st = song.stem("horn", -19.0, send=0.3, human_ms=0, swing=False)
    for b, d, m in ((1.1, 0.75, 68), (1.85, 1.3, 67)):
        y = I.brass(hz(m), 0.55, sr, dur=song.sec(d), bright=0.4, cutoff=1300.0)
        st.add(y, b, pan=-0.1)
    song.mix(st)
    st = song.stem("epiano", -22.0, send=0.35, human_ms=0, swing=False)
    for b, d, tones in ((1.1, 0.75, (53, 56, 60)), (1.85, 1.6, (52, 55, 60))):
        for i, m in enumerate(tones):
            st.add(I.epiano(hz(m), 0.5, sr, dur=song.sec(d)), b, pan=-0.25 + 0.25 * i, offset_s=0.01 * i)
    song.mix(st)
    st = song.stem("bass", -20.0, send=0.05, human_ms=0, swing=False, keep=True)
    st.add(I.bass_soft(hz(41), 0.7, sr, dur=song.sec(0.7)), 1.1)
    st.add(I.bass_soft(hz(36), 0.7, sr, dur=song.sec(1.4)), 1.85)
    song.mix(st)
    st = song.stem("perc", -27.0, send=0.2, human_ms=0, swing=False)
    st.add(kit.get("woodblock", f=900.0), 0.0, pan=0.3, gain=0.6)
    st.add(kit.get("woodblock", f=700.0), 0.66, pan=-0.3, gain=0.5)
    st.add(kit.get("kick", soft=True), 1.85, gain=0.5)
    song.mix(st)
    return song


# =========================================================================== render entry
TRACKS = {
    "menu_theme": dict(fn=menu_theme, rt60=1.7, verb=1.0),
    "level_calm": dict(fn=level_calm, rt60=1.35, verb=1.0),
    "level_rush": dict(fn=level_rush, rt60=1.35, verb=1.0),
    "boss_theme": dict(fn=boss_theme, rt60=1.15, verb=0.9),
    "victory_jingle": dict(fn=victory_jingle, rt60=1.5, verb=1.0),
    "fail_jingle": dict(fn=fail_jingle, rt60=1.5, verb=1.0),
}
GROUPS = [["level_calm", "level_rush"], ["menu_theme"], ["boss_theme"], ["victory_jingle"], ["fail_jingle"]]


def render_all(out_dir, only=None, log=print):
    """Renders the music; returns {name: dict(path, song, data, analysis stems)}."""
    import os
    os.makedirs(out_dir, exist_ok=True)
    results = {}
    for group in GROUPS:
        if only and not any(nm in only for nm in group):
            continue
        songs, mixes = [], []
        for nm in group:
            cfg = TRACKS[nm]
            song = cfg["fn"]()
            log("  music %-15s %d stems, %.3f BPM, %d samples" % (nm, len(song.stems), song.bpm, song.L))
            songs.append(song)
            mixes.append(song.render(rt60=cfg["rt60"], verb=cfg["verb"]))
        outs = master(mixes)
        for nm, song, y in zip(group, songs, outs):
            if not song.loop:
                y = D.fade(y, song.sr, 0.005, 0.25)
            if not np.all(np.isfinite(y)):
                raise RuntimeError("NaN in " + nm)
            path = os.path.join(out_dir, nm + ".wav")
            D.write_wav(path, y, song.sr, dither_seed=len(nm))
            results[nm] = dict(path=path, song=song, data=y)
    return results
