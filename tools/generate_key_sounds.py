#!/usr/bin/env python3
# SPDX-License-Identifier: CC0-1.0
"""Generate original CC0 key clicks, without recordings or external samples.

Run from any directory: python3 tools/generate_key_sounds.py
Uses only Python's standard library. Output: mono 16-bit PCM WAV, 22050 Hz.
The descriptors use Trime's existing soundeffect package format; unmatched keys
fall back to sound index 0, with a lower-pitched space/enter/backspace variant.
"""

import math
from pathlib import Path
import random
import struct
import wave

RATE = 22050
DEST = Path(__file__).resolve().parents[1] / "app/src/main/assets/rime/soundeffect"
PACKS = (
    ("chen_crisp", "清脆", 0.028, 2400, 0.38),
    ("chen_mechanical", "机械", 0.048, 820, 0.45),
    ("chen_soft", "轻柔", 0.040, 460, 0.20),
)


def synthesize(pack, duration, frequency, gain, special):
    rng = random.Random(20261029 + special)
    frames = []
    count = round(RATE * duration)
    for i in range(count):
        t = i / RATE
        f = frequency * (0.75 if special else 1.0)
        noise = rng.uniform(-1, 1)
        if pack == "chen_crisp":
            signal = 0.65 * math.sin(2 * math.pi * f * t) + 0.35 * noise
            decay = math.exp(-t / 0.004)
        elif pack == "chen_mechanical":
            signal = (0.55 * math.sin(2 * math.pi * f * t)
                      + 0.25 * math.sin(2 * math.pi * f * 2.71 * t)
                      + 0.20 * noise)
            # A delayed second impact suggests a mechanical switch settling.
            decay = math.exp(-t / 0.006)
            if t >= 0.012:
                decay += 0.45 * math.exp(-(t - 0.012) / 0.005)
        else:
            signal = (0.85 * math.sin(2 * math.pi * f * t)
                      + 0.15 * math.sin(2 * math.pi * f * 1.5 * t))
            decay = math.exp(-t / 0.009)
        # Smooth onset and tail; keep the endpoints at zero.
        envelope = min(1.0, i / 12) * min(1.0, (count - 1 - i) / 80)
        sample = round(32767 * gain * signal * decay * envelope)
        frames.append(struct.pack("<h", sample))
    return b"".join(frames)


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    for pack, name, duration, frequency, gain in PACKS:
        folder = DEST / pack
        folder.mkdir(exist_ok=True)
        for special, filename in enumerate(("click.wav", "special.wav")):
            with wave.open(str(folder / filename), "wb") as audio:
                audio.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
                audio.writeframes(synthesize(pack, duration, frequency, gain, special))
        (DEST / f"{pack}.sound.yaml").write_text(
            "# Original synthesized sounds and descriptor: CC0-1.0\n"
            f"name: {name}\nfolder: {pack}\n"
            "sound: [click.wav, special.wav]\n"
            "keyset:\n"
            "  - keys: [KEYCODE_SPACE, KEYCODE_ENTER, KEYCODE_DEL]\n"
            "    inOrder: true\n    sounds: [1]\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
