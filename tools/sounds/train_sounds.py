#!/usr/bin/env python3
"""
Synthesises the mod's sounds (a three-chime steam whistle, exhaust chuffs, a coupling clank, a diesel's two-tone horn,
the loop of its engine running, a level crossing's bell, an electric's horn and the loop of its motors, and the
Bullet Train's horn) and
encodes them as mono Ogg Vorbis (mono, so Minecraft plays them from where they happen). Needs numpy and ffmpeg. Run
from the repository root, naming the sounds to make (all of them, if none are named; Vorbis encoding isn't
byte-for-byte repeatable, so remaking one that hasn't changed just churns the file):

    python3 tools/sounds/train_sounds.py [crossing_bell ...]
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src/main/resources/assets/reikotrains/sounds")
RATE = 44100


def band(signal, low, high):
    """Keeps only the frequencies between low and high (Hz), with soft edges."""
    spectrum = np.fft.rfft(signal)
    freqs = np.fft.rfftfreq(len(signal), 1 / RATE)
    mask = np.clip((freqs - low * 0.8) / (low * 0.2 + 1e-9), 0, 1) * np.clip((high * 1.2 - freqs) / (high * 0.2), 0, 1)
    return np.fft.irfft(spectrum * mask, len(signal))


def envelope(n, attack, release):
    env = np.ones(n)
    a, r = int(attack * RATE), int(release * RATE)
    env[:a] = np.linspace(0, 1, a) ** 1.5
    env[n - r:] *= np.linspace(1, 0, r) ** 2
    return env


def whistle():
    duration = 2.0
    n = int(duration * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(7)
    out = np.zeros(n)
    # The steam takes a moment to come up to pressure, so the pitch rises a little at first.
    rise = 0.94 + 0.06 * (1 - np.exp(-t / 0.06))
    wobble = 1 + 0.003 * np.sin(2 * np.pi * 5.5 * t)
    for freq, level in ((392.0, 1.0), (493.9, 0.85), (587.3, 0.7)):
        phase = 2 * np.pi * np.cumsum(freq * rise * wobble) / RATE
        out += level * (np.sin(phase) + 0.35 * np.sin(2 * phase) + 0.12 * np.sin(3 * phase))
    out /= np.max(np.abs(out))
    breath = band(rng.standard_normal(n), 1800, 5000)
    breath /= np.max(np.abs(breath))
    chiff = np.exp(-t / 0.08)
    out = out * 0.85 + breath * (0.08 + 0.35 * chiff)
    out *= envelope(n, 0.07, 0.45)
    return out


def chuff(seed):
    duration = 0.4
    n = int(duration * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(seed)
    noise = band(rng.standard_normal(n), 120, 1400)
    noise /= np.max(np.abs(noise))
    hiss = band(rng.standard_normal(n), 2000, 6000)
    hiss /= np.max(np.abs(hiss))
    thump = np.sin(2 * np.pi * (70 + 20 * np.exp(-t / 0.03)) * t)
    decay = np.exp(-t / 0.075)
    out = (noise * 0.8 + hiss * 0.18) * decay + thump * np.exp(-t / 0.04) * 0.5
    out *= envelope(n, 0.006, 0.08)
    return out


def clank():
    duration = 0.6
    n = int(duration * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(3)
    out = np.zeros(n)
    for freq, level, tau in ((523, 1.0, 0.16), (1307, 0.7, 0.10), (2153, 0.5, 0.07), (3391, 0.35, 0.05), (812, 0.6, 0.12)):
        out += level * np.sin(2 * np.pi * freq * t + rng.uniform(0, 6.28)) * np.exp(-t / tau)
    click = band(rng.standard_normal(n), 1000, 8000) * np.exp(-t / 0.008)
    out = out / np.max(np.abs(out)) + click / np.max(np.abs(click)) * 0.5
    # a second, softer clank as the coupling settles
    delay = int(0.11 * RATE)
    out[delay:] += 0.45 * out[:-delay]
    out *= envelope(n, 0.001, 0.15)
    return out


def horn(notes=(311.1, 370.0)):
    """A two-tone air horn: two reedy notes (a minor third apart, for the diesel), with a puff of air at the start."""
    duration = 1.8
    n = int(duration * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(5)
    out = np.zeros(n)
    # The air comes up to pressure: the pitch sags a little at first.
    rise = 0.97 + 0.03 * (1 - np.exp(-t / 0.05))
    for freq, level in zip(notes, (1.0, 0.9)):
        phase = 2 * np.pi * np.cumsum(freq * rise) / RATE
        # a reed: rich in odd and even harmonics, a bit nasal
        tone = sum(np.sin(k * phase) / k ** 0.8 for k in range(1, 9))
        out += level * tone
    out /= np.max(np.abs(out))
    air = band(rng.standard_normal(n), 600, 4000)
    air /= np.max(np.abs(air))
    out = out * 0.9 + air * (0.05 + 0.25 * np.exp(-t / 0.06))
    out = np.tanh(out * 1.6)  # the horn's bell overdrives a little
    out *= envelope(n, 0.04, 0.3)
    return out


def engine():
    """
    A diesel ticking over, made to loop: firing pulses at 12 a second (an eight-cylinder engine at a fast idle) with
    the low growl of the exhaust and some mechanical clatter. Every part repeats exactly over the two seconds, so the
    end runs straight into the start.
    """
    duration = 2.0
    n = int(duration * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(9)
    fire = 12.0  # pulses a second: 24 in the two seconds, so it loops
    pulse = (0.5 + 0.5 * np.cos(2 * np.pi * fire * t)) ** 6
    growl = np.zeros(n)
    for k, level in ((1, 1.0), (2, 0.6), (3, 0.45), (4, 0.3), (6, 0.2), (8, 0.12)):
        growl += level * np.sin(2 * np.pi * 36.0 * k * t + rng.uniform(0, 6.28))  # 36 Hz: 72 cycles, loops
    # Noise that loops: random phases at every frequency of the two-second period.
    spectrum = np.exp(1j * rng.uniform(0, 2 * np.pi, n // 2 + 1))
    noise = np.fft.irfft(spectrum, n)
    rumble = band(noise, 40, 400)
    rumble /= np.max(np.abs(rumble))
    clatter = band(noise, 1500, 5000)
    clatter /= np.max(np.abs(clatter))
    out = growl / np.max(np.abs(growl)) * (0.55 + 0.45 * pulse) + rumble * 0.35 * (0.4 + 0.6 * pulse) + clatter * 0.08 * pulse
    return out


def motor():
    """
    An electric's traction motors, made to loop: the low hum of the power electronics and the whine of the motors
    (played higher the faster it goes), with a little air rushing. Every part repeats exactly over the two seconds.
    """
    duration = 2.0
    n = int(duration * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(17)
    hum = sum(level * np.sin(2 * np.pi * 100.0 * k * t + rng.uniform(0, 6.28)) for k, level in ((1, 1.0), (2, 0.5), (3, 0.3), (5, 0.15)))
    whine = np.zeros(n)
    for freq, level in ((660.0, 1.0), (990.0, 0.5), (1320.0, 0.35), (1984.5, 0.15)):
        wobble = 1 + 0.002 * np.sin(2 * np.pi * 1.5 * t)  # a slight waver, three times over the loop
        whine += level * np.sin(2 * np.pi * freq * t * wobble + rng.uniform(0, 6.28))
    spectrum = np.exp(1j * rng.uniform(0, 2 * np.pi, n // 2 + 1))
    noise = band(np.fft.irfft(spectrum, n), 300, 3000)
    out = hum / np.max(np.abs(hum)) * 0.5 + whine / np.max(np.abs(whine)) * 0.45 + noise / np.max(np.abs(noise)) * 0.12
    return out


def bell():
    """
    One stroke of a level crossing bell: a hammer striking a small steel gong. A bell's overtones aren't in tune with
    each other; the high ones die away first, leaving the hum. It's rung twice a second.
    """
    duration = 0.5
    n = int(duration * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(13)
    out = np.zeros(n)
    base = 880.0
    for ratio, level, tau in ((0.5, 0.35, 0.30), (1.0, 1.0, 0.22), (1.19, 0.55, 0.16), (1.51, 0.5, 0.12), (2.0, 0.45, 0.10),
                              (2.51, 0.3, 0.07), (3.34, 0.2, 0.05)):
        out += level * np.sin(2 * np.pi * base * ratio * t + rng.uniform(0, 6.28)) * np.exp(-t / tau)
    strike = band(rng.standard_normal(n), 2000, 9000) * np.exp(-t / 0.004)
    out = out / np.max(np.abs(out)) + strike / np.max(np.abs(strike)) * 0.35
    out *= envelope(n, 0.001, 0.08)
    return out


def bullet_horn():
    """
    The Bullet Train's horn: an electronic call rather than an air horn, a short note and then a longer one a fourth
    higher, clear and smooth, with a touch of vibrato as the long note holds.
    """
    duration = 1.5
    n = int(duration * RATE)
    t = np.arange(n) / RATE
    out = np.zeros(n)
    for start, length, freq in ((0.0, 0.22, 587.3), (0.30, 1.15, 784.0)):
        a, b = int(start * RATE), int((start + length) * RATE)
        tt = t[a:b] - start
        vibrato = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * tt) * np.clip((tt - 0.25) / 0.3, 0, 1)
        phase = 2 * np.pi * np.cumsum(freq * vibrato) / RATE
        tone = sum(np.sin(k * phase) / k ** 1.6 for k in range(1, 6))
        out[a:b] += tone * envelope(b - a, 0.025, min(0.12, length / 3))
    out = np.tanh(out * 1.2)
    return out


def write(name, samples, peak=0.85):
    samples = samples / np.max(np.abs(samples)) * peak
    pcm = (samples * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    target = os.path.join(OUT, f"{name}.ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "5", "-map_metadata", "-1", target],
                   check=True)
    os.remove(path)
    print(f"{name}.ogg: {len(samples) / RATE:.2f}s")


SOUNDS = {
    "steam_whistle": (whistle, 0.85),
    "chuff1": (lambda: chuff(11), 0.8),
    "chuff2": (lambda: chuff(23), 0.8),
    "chuff3": (lambda: chuff(41), 0.8),
    "couple": (clank, 0.8),
    "diesel_horn": (horn, 0.85),
    "diesel_engine": (engine, 0.7),
    "crossing_bell": (bell, 0.8),
    "electric_horn": (lambda: horn((440.0, 523.3)), 0.85),
    "electric_motor": (motor, 0.6),
    "bullet_horn": (bullet_horn, 0.85),
}


def main(names):
    for name in names or SOUNDS:
        make, peak = SOUNDS[name]
        write(name, make(), peak)


if __name__ == "__main__":
    main(sys.argv[1:])
