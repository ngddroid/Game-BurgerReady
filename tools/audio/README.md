# Bun Voyage! audio: procedural SFX and music

Every sound and music cue in `app/src/main/assets/audio/` is synthesised from scratch by the
Python + numpy code in this folder. No samples, loops or recordings are used, so there is nothing
to license. Renders are deterministic: every random source is seeded from the sound's name, so a
re-run reproduces the files exactly.

## Regenerating

The system `python3` has no numpy, so the scripts run inside Blender's bundled Python 3.13
(numpy 2.x). They use only numpy and the standard library (`wave`), never `bpy`.

```sh
cd /Volumes/Work/Raj/Android/BurgerReady
~/Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup --python-exit-code 1 \
    --python tools/audio/synth.py -- all
```

| argument | effect |
|---|---|
| `all` (default) | render all SFX and music, then run QC |
| `sfx` / `music` | render only that half, then run QC on everything on disk |
| `check` | QC only: no rendering. The bass-under-250 Hz column needs stems, so it shows `-` |
| `--only a,b` | render only the named files. `level_calm` and `level_rush` always render together because they share a master gain |

A full run takes about 10 s. The process exits with code 1 if any QC check fails, because of
`--python-exit-code 1`. Any Python with numpy can run it too: `python synth.py all`.

## Files in this folder

| file | contents |
|---|---|
| `dsp.py` | Toolkit: PolyBLEP saw/square, band-limited additive triangle/harmonics, 2-op FM with anti-alias index limiting, modal (struck-object) synthesis, block-vectorised Karplus-Strong with fractional-delay tuning, white/coloured noise, one-pole LP/HP, RBJ biquads, TPT state-variable filter (supports per-sample cutoff), FFT band shaping, ADSR/AD/breakpoint envelopes, pitch sweeps, vibrato, smooth random LFOs, Schroeder comb/allpass reverb, synthetic-IR convolution reverb, circular fold for loops, tanh soft clip, normalise, 5 ms raised-cosine fades, loop crossfade, constant-power pan, 16-bit WAV I/O with TPDF dither |
| `instruments.py` | Shared instruments. Pitched: FM marimba, FM kalimba, FM e-piano, KS guitar, detuned-saw pad, soft FM bass, driven saw bass, KS upright bass, ocarina lead, bright lead, FM brass, glockenspiel, FM bell, chime. Drums: kick, snare, rim, clap, hats, shaker, tambourine, congas, toms, crash, brush, woodblock. Also a variant-caching `DrumKit` |
| `sfx.py` | One recipe per line of `sfx_list.txt`. Target durations are parsed from that list, and the renderer fails if any name has no recipe |
| `music.py` | Beat-grid sequencer (swing, humanisation, per-stem RMS levelling, reverb send, circular loop rendering) and the six cues |
| `qc.py` | Measurements: stats, loop-seam metrics, band-energy fractions, spectral centroid, short-term loudness |
| `synth.py` | Command-line entry point: renders, runs QC and prints the summary tables |

## Output formats

- **SFX**: `assets/audio/sfx/<name>.wav`, 44.1 kHz, mono, 16-bit.
  - Gameplay sounds peak at -1.0 dBFS and `ui_*` sounds at -4.0 dBFS.
  - Every one-shot has a 5 ms raised-cosine fade-in and fade-out. The exceptions are the five
    transient ticks (`ui_spin_tick`, `ui_coin_tick`, `ui_toggle`, `pad_fill` and `timer_tick`),
    which use a 1.5 ms fade-in so their attack stays crisp; their fade-out is still 5 ms.
  - DC is removed with a 12 Hz DC blocker, then a scaled Hann bump that is zero at both ends.
  - Every file is exactly the target length (±0 samples).
- **Music**: `assets/audio/music/<name>.wav`, 22.05 kHz, stereo, 16-bit, peak -1.0 dBFS (calm
  peaks at -2.0, see below). Mastering is a circular 32 Hz high-pass, a gentle tanh soft clip
  (about 3 dB of peak reduction) and normalisation.

## Loop points

Every loop is meant to be played from sample 0 to the last sample and repeated. There is no
intro section and no tail section.

- **Music loops** (`menu_theme`, `level_calm`, `level_rush`, `boss_theme`) are rendered
  *circularly*. Every note and reverb tail that rings past the loop end is folded back onto the
  start, exactly as it sounds when the file repeats. The master high-pass is applied circularly
  (in the FFT domain) and the soft clip is memoryless, so the wrap is sample-continuous by
  construction. The loop end lands on a bar line, and the tempo is set so one beat is a whole
  number of samples.
- **`level_calm` / `level_rush`** are sample-synchronous twins. Both are exactly **717,568
  samples** (32.5428 s): 16 bars × 4 beats × 11,212 samples per beat, which is 117.999 BPM.
  - To crossfade, play both from the same sample position and ramp their gains. Any crossfade
    point works.
  - The pad, guitar and shaker layers are bit-identical in both files: same seeds, same calls.
  - Both files are mastered with one shared gain, so rush is naturally about 2.7 dB louder (RMS
    -17.4 vs -14.7 dBFS). That is why calm peaks at -2.0 dBFS.
- **`sizzle_loop` / `fryer_loop`** (2.000 s = 88,200 samples) were rendered 0.3 s long. That
  extra tail was equal-power crossfaded into the head, so the last sample flows straight into the
  first. They have no fades.
- **Jingles** (`victory_jingle`, `fail_jingle`) do not loop. They have a 5 ms fade-in and a
  250 ms fade-out.

QC measures each seam. The jump across the wrap is 0.05 to 0.13× the 99.9th-percentile step
inside the file. The 20 ms RMS step at the wrap is no bigger than at the file's own bar lines
(music) or at random points (SFX loops).

## Music

| file | dur | key / tempo | bars | progression |
|---|---|---|---|---|
| `menu_theme.wav` | 32.000 s (705,600 smp) | F major, 90 BPM, swung 8ths (63 %) | 12 | \| Fmaj7 \| Dm7 \| Gm7 \| C7 \| ×2 (I–vi–ii–V), bridge \| Bbmaj7 \| Am7 \| Gm7 \| C7sus4 C7 \| |
| `level_calm.wav` | 32.543 s (717,568 smp) | G major, 118 BPM, 56 % swing | 16 | \| Gmaj7 \| Em7 \| Cmaj7 \| D7 \| Gmaj7 \| Em7 \| Cmaj7 \| D7sus4 D7 \| Cmaj7 \| Bm7 \| Am7 \| D7 \| Gmaj7 \| Em7 \| Am7 \| D7 \| |
| `level_rush.wav` | 32.543 s (717,568 smp) | same | 16 | same |
| `boss_theme.wav` | 32.000 s (705,600 smp) | D minor, 150 BPM | 20 | A: \| Dm \| Bb \| C \| A7 \| ×2 · B: \| Gm \| Dm \| Bb \| A7 \| Gm \| Dm \| Eb \| A7 \| · C (breakdown): \| Dm \| Dm \| Bb \| A7 \| |
| `victory_jingle.wav` | 3.000 s | C major, 150 BPM | — | C – Am7 – G – C |
| `fail_jingle.wav` | 2.500 s | C major, 100 BPM | — | C arpeggio, then Fm → C (minor plagal "aww"), then a hopeful plink |

**menu_theme**: relaxed and bouncy.
- An FM e-piano plays Charleston comping and a Karplus-Strong upright bass walks underneath.
- A kalimba plays the lead hook (A–C–E–D–C over Fmaj7). In the bridge an ocarina doubles it.
- Marimba answers at the end of every second bar, and glock accents mark the phrase starts.
- A warm detuned-saw pad sits underneath.
- The kit is soft: kick, brush and rim backbeat, swung ride-style hats and a shaker.

**level_calm**: a light groove.
- The kalimba lead motif is B–D–G–F♯–G–D over Gmaj7, answered a third higher over Em7. It repeats
  with variations, and an ocarina doubles it in the bridge.
- Sparse marimba arpeggios run under the melody.
- A KS guitar strums down on beat 1 and up on the "and" of beats 3 and 4.
- The soft FM bass plays root, root, 5th, octave, then a chromatic or step approach into the next
  chord.
- The pad, the light kit (soft kick, rim backbeat, shaker, hats in bars 9–16) and the gentle
  reverb complete the groove.

**level_rush**: the same harmony, melody and bar structure with more energy.
- The calm layers stay: the identical pad, guitar and shaker, plus a quieter kalimba melody.
- A bright saw/pulse lead plays the melody. A glock doubles it an octave up in bars 9–16, and a
  diatonic third harmony joins in bars 13–16.
- Marimba arpeggios move to 16ths, and the bass pumps in octaves.
- The kit drives harder: four-on-the-floor kick, snare and clap backbeat, 16th hats (open hats in
  the second half), congas and tambourine.
- A crash hits bars 1 and 9, and toms fill the end of bars 8 and 16.

**boss_theme**: an intense, cute boss loop.
- Section A has a driving minor riff lead over syncopated FM brass stabs. A 16th marimba ostinato,
  muted KS power-chord chugs and an octave-pumping driven bass run underneath.
- Section B opens into long heroic notes. Brass doubles the lead an octave down, with brass swells
  and a dark pad, and a Neapolitan E♭ chord adds drama.
- Section C is a taiko-style tom breakdown. A snare roll and a noise riser build back into the
  loop, and a G♯→A pickup leads into bar 1.

**victory_jingle**: a triplet pickup G-G-G, then C–E–G | D–G | a held C6.
- Brass lead with marimba doubling, then a marimba roll on the last chord.
- Brass chords, a vi–V–I cadence, a snare roll into a crash, and a glock sparkle arpeggio.

**fail_jingle**: kind, not mocking.
- A kalimba descends G–E–C.
- A soft muted horn sighs A♭→G over Fm → C with e-piano and bass.
- It ends on a hopeful kalimba G→C "try again" plink.

The bass sits under 250 Hz: QC measures the bass stem of each cue at render time. The measured
share of bass energy below 250 Hz is:

| cue | bass energy < 250 Hz |
|---|---:|
| menu_theme | 96 % |
| level_calm | 99 % |
| level_rush | 92 % |
| boss_theme | 91 % |
| jingles | 98–99 % |

| file | size |
|---|---:|
| `menu_theme.wav` | 2.82 MB |
| `level_calm.wav` | 2.87 MB |
| `level_rush.wav` | 2.87 MB |
| `boss_theme.wav` | 2.82 MB |
| `victory_jingle.wav` | 0.26 MB |
| `fail_jingle.wav` | 0.22 MB |

## SFX

Durations match `sfx_list.txt` exactly; see that file for each sound's brief.

The SFX are peak-normalised as specified, so sustained tonal sounds (horn, siren, whistle, beeps)
are much louder per unit time than clicks. **Suggested trim** is a starting point for the engine's
per-sound volume. It brings the loudest 50 ms RMS window to about -12 dBFS (-14 dBFS for UI).
This is a plain RMS proxy and not a true loudness measure, so tune by ear.

| file | dur (s) | peak dBFS | loudest 50 ms RMS | suggested trim | size (KB) |
|---|---:|---:|---:|---:|---:|
| `ui_tap.wav` | 0.060 | -4.0 | -17.6 | 0 | 5 |
| `ui_back.wav` | 0.060 | -4.0 | -16.8 | 0 | 5 |
| `ui_tab.wav` | 0.120 | -4.0 | -20.7 | 0 | 10 |
| `ui_toggle.wav` | 0.050 | -4.0 | -20.1 | 0 | 4 |
| `ui_popup.wav` | 0.200 | -4.0 | -8.1 | -6 dB | 17 |
| `ui_purchase.wav` | 0.400 | -4.0 | -12.3 | -2 dB | 34 |
| `ui_error.wav` | 0.250 | -4.0 | -8.7 | -5 dB | 22 |
| `ui_reward.wav` | 0.600 | -4.0 | -12.6 | -1 dB | 52 |
| `ui_star1.wav` | 0.350 | -4.0 | -12.3 | -2 dB | 30 |
| `ui_star2.wav` | 0.350 | -4.0 | -12.3 | -2 dB | 30 |
| `ui_star3.wav` | 0.500 | -4.0 | -11.3 | -3 dB | 43 |
| `ui_chest_open.wav` | 0.900 | -4.0 | -13.8 | 0 | 78 |
| `ui_spin_tick.wav` | 0.025 | -4.0 | -20.8 | 0 | 2 |
| `ui_spin_win.wav` | 1.200 | -4.0 | -14.6 | 0 | 103 |
| `ui_level_unlock.wav` | 0.600 | -4.0 | -17.6 | 0 | 52 |
| `ui_coin_tick.wav` | 0.030 | -4.0 | -21.3 | 0 | 3 |
| `pickup.wav` | 0.090 | -1.0 | -12.6 | 0 | 8 |
| `drop.wav` | 0.100 | -1.0 | -12.2 | 0 | 9 |
| `coin_collect.wav` | 0.120 | -1.0 | -12.1 | 0 | 10 |
| `coin_cascade.wav` | 0.700 | -1.0 | -13.8 | 0 | 60 |
| `cash_register.wav` | 0.500 | -1.0 | -11.8 | 0 | 43 |
| `pad_fill.wav` | 0.040 | -1.0 | -18.7 | 0 | 3 |
| `build_pop.wav` | 0.700 | -1.0 | -12.5 | 0 | 60 |
| `upgrade.wav` | 0.700 | -1.0 | -10.9 | -1 dB | 60 |
| `hire.wav` | 0.500 | -1.0 | -4.8 | -7 dB | 43 |
| `sizzle_loop.wav` | 2.000 | -1.0 | -15.5 | 0 | 172 |
| `fryer_loop.wav` | 2.000 | -1.0 | -14.0 | 0 | 172 |
| `soda_pour.wav` | 0.600 | -1.0 | -12.6 | 0 | 52 |
| `blender_whirr.wav` | 0.800 | -1.0 | -5.8 | -6 dB | 69 |
| `customer_order.wav` | 0.150 | -1.0 | -5.4 | -7 dB | 13 |
| `customer_happy.wav` | 0.300 | -1.0 | -6.0 | -6 dB | 26 |
| `customer_angry.wav` | 0.350 | -1.0 | -8.6 | -3 dB | 30 |
| `eat_chomp.wav` | 0.150 | -1.0 | -13.3 | 0 | 13 |
| `trash_dump.wav` | 0.400 | -1.0 | -11.9 | 0 | 34 |
| `footstep1.wav` | 0.060 | -1.0 | -15.7 | 0 | 5 |
| `footstep2.wav` | 0.060 | -1.0 | -15.7 | 0 | 5 |
| `combo_up1.wav` | 0.300 | -1.0 | -15.4 | 0 | 26 |
| `combo_up2.wav` | 0.300 | -1.0 | -15.8 | 0 | 26 |
| `combo_up3.wav` | 0.400 | -1.0 | -13.7 | 0 | 34 |
| `combo_break.wav` | 0.400 | -1.0 | -5.8 | -6 dB | 34 |
| `rush_siren.wav` | 1.500 | -1.0 | -5.0 | -7 dB | 129 |
| `fire_whoosh.wav` | 0.600 | -1.0 | -9.8 | -2 dB | 52 |
| `extinguish.wav` | 0.700 | -1.0 | -13.3 | 0 | 60 |
| `spill_splat.wav` | 0.300 | -1.0 | -16.1 | 0 | 26 |
| `mop_squeak.wav` | 0.300 | -1.0 | -5.9 | -6 dB | 26 |
| `raccoon_chitter.wav` | 0.500 | -1.0 | -9.2 | -3 dB | 43 |
| `machine_jam.wav` | 0.500 | -1.0 | -9.4 | -3 dB | 43 |
| `machine_fixed.wav` | 0.500 | -1.0 | -13.2 | 0 | 43 |
| `car_horn.wav` | 0.400 | -1.0 | -4.6 | -7 dB | 34 |
| `car_engine.wav` | 1.000 | -1.0 | -10.6 | -1 dB | 86 |
| `powerup.wav` | 0.600 | -1.0 | -9.8 | -2 dB | 52 |
| `timer_tick.wav` | 0.050 | -1.0 | -20.8 | 0 | 4 |
| `timer_warning.wav` | 0.200 | -1.0 | -5.0 | -7 dB | 17 |
| `level_win.wav` | 2.500 | -1.0 | -10.5 | -1 dB | 215 |
| `level_fail.wav` | 1.800 | -1.0 | -8.0 | -4 dB | 155 |
| `vip_arrive.wav` | 0.800 | -1.0 | -8.3 | -4 dB | 69 |
| `critic_hmm.wav` | 0.500 | -1.0 | -6.7 | -5 dB | 43 |
| `splash_pop.wav` | 0.120 | -1.0 | -14.3 | 0 | 10 |
| `whoosh.wav` | 0.400 | -1.0 | -11.4 | 0 | 34 |

Total audio: **14.58 MB** (65 files: SFX 2.7 MB, music 11.9 MB).

## QC performed on every run

- **Per file:** sample rate, channels, exact length, peak dBFS, RMS, DC offset (< 1e-3), NaN/Inf
  and clipped-sample counts.
- **One-shots:** the edge samples must be ≤ 4 LSB, which proves the fades.
- **Loops:** wrap discontinuity, and the 20 ms RMS step at the wrap compared with internal
  reference points.
- **`level_calm` / `level_rush`:** identical sample counts. QC also reports their RMS and the
  waveform correlation between them.
- **Spectrum:** ticks (`ui_spin_tick`, `ui_coin_tick`, `timer_tick`, `pad_fill`) must have ≥ 85 %
  of their energy above 1.5 kHz (they measure 98–100 %). Footsteps and `drop` must have ≥ 80 %
  below 1 kHz. Music bass stems must have ≥ 85 % below 250 Hz.
- **Size:** the total must stay ≤ 16 MB.
