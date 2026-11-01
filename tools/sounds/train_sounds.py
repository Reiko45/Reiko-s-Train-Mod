#!/usr/bin/env python3
"""
Synthesises the mod's sounds (a three-chime steam whistle, exhaust chuffs and a coupling clank) and encodes them
as mono Ogg Vorbis (mono, so Minecraft plays them from where they happen). Needs numpy and ffmpeg. Run from the
repository root:

    python3 tools/sounds/train_sounds.py
"""
import os
import subprocess
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


def main():
    write("steam_whistle", whistle())
    write("chuff1", chuff(11), 0.8)
    write("chuff2", chuff(23), 0.8)
    write("chuff3", chuff(41), 0.8)
    write("couple", clank(), 0.8)


if __name__ == "__main__":
    main()
