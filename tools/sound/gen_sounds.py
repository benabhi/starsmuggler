"""Generates the app's sound effects (SMUG-31) as small WAV files: the dice roll in the :dice
module, the rest in app/src/main/res/raw.

Every sound is synthesized here, in the style of early-80s sound chips, so the app carries no
third-party audio; the dice roll replaced irondroid's clip for the same reason (benabhi,
04/11/2026). Run from the repo root:

    python tools/sound/gen_sounds.py
"""

import math
import random
import struct
import wave
from pathlib import Path

RATE = 22050
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"
DICE_OUT = ROOT / "dice" / "src" / "main" / "res" / "raw"


def square(phase: float) -> float:
    return 1.0 if (phase % 1.0) < 0.5 else -1.0


def saw(phase: float) -> float:
    return 2.0 * (phase % 1.0) - 1.0


def write(name: str, samples: list[float], volume: float, out: Path = OUT) -> None:
    """Writes 16-bit mono PCM, normalized to [volume] of full scale."""
    peak = max(abs(s) for s in samples) or 1.0
    frames = b"".join(struct.pack("<h", int(s / peak * volume * 32767)) for s in samples)
    out.mkdir(parents=True, exist_ok=True)
    path = out / f"{name}.wav"
    with wave.open(str(path), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(frames)
    print(f"{path.relative_to(ROOT)}: {len(samples) / RATE * 1000:.0f} ms")


def fade(samples: list[float], attack_ms: float = 2, release_ms: float = 10) -> list[float]:
    """Short ramps at both ends, so no sound starts or stops with a pop."""
    a = int(RATE * attack_ms / 1000)
    r = int(RATE * release_ms / 1000)
    n = len(samples)
    return [s * min(1.0, i / a if a else 1.0, (n - i) / r if r else 1.0) for i, s in enumerate(samples)]


def ui_click() -> list[float]:
    """A teletype key: a noise tick over a short high square blip."""
    rng = random.Random(1982)
    n = int(RATE * 0.035)
    out = []
    for i in range(n):
        t = i / RATE
        env = math.exp(-t * 180)
        out.append(0.6 * rng.uniform(-1, 1) * env + 0.5 * square(1900 * t) * math.exp(-t * 120))
    return fade(out, attack_ms=0.5, release_ms=4)


def loan_warning() -> list[float]:
    """Two flat square beeps, like an old terminal's alert."""
    out: list[float] = []
    for beep in range(2):
        n = int(RATE * 0.13)
        out += fade([square(880 * i / RATE) * (1 - 0.3 * i / n) for i in range(n)], release_ms=8)
        if beep == 0:
            out += [0.0] * int(RATE * 0.07)
    return out


def hyperspace_jump() -> list[float]:
    """A rising saw sweep with vibrato that bursts into a fading noise whoosh."""
    rng = random.Random(1201)
    sweep = int(RATE * 0.9)
    whoosh = int(RATE * 0.6)
    out = []
    phase = 0.0
    for i in range(sweep):
        t = i / sweep
        freq = 110 * (16 ** t) * (1 + 0.03 * math.sin(2 * math.pi * 9 * i / RATE))
        phase += freq / RATE
        out.append(saw(phase) * (0.3 + 0.7 * t))
    low = 0.0
    for i in range(whoosh):
        t = i / whoosh
        # A one-pole low-pass that closes as the whoosh fades makes it sound like it moves away.
        low += (rng.uniform(-1, 1) - low) * (0.6 - 0.5 * t)
        out.append(low * 2.2 * (1 - t) ** 2)
    return fade(out, release_ms=30)


def laser_shot() -> list[float]:
    """A quick falling square sweep."""
    n = int(RATE * 0.26)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / n
        phase += (2400 * (0.12 ** t)) / RATE
        out.append(square(phase) * (1 - t) ** 1.5)
    return fade(out, release_ms=15)


def money_gained() -> list[float]:
    """A coin chime: two quick rising square notes, the second one ringing out."""
    out: list[float] = []
    for freq, length in ((988, 0.07), (1319, 0.32)):
        n = int(RATE * length)
        out += fade([square(freq * i / RATE) * math.exp(-i / RATE * (6 if length > 0.1 else 2)) for i in range(n)], release_ms=6)
    return out


def dice_roll() -> list[float]:
    """Two plastic dice thrown on a table: a thud, then clicks that come closer and fainter as they settle."""
    rng = random.Random(6)
    n = int(RATE * 0.95)
    out = [0.0] * n
    for die in range(2):
        t = 0.01 + die * 0.035
        gap = 0.11 + die * 0.02
        strength = 1.0
        while t < 0.85 and strength > 0.05:
            start = int(t * RATE)
            ping = rng.uniform(2300, 4200)
            for i in range(min(int(RATE * 0.045), n - start)):
                s = i / RATE
                click = rng.uniform(-1, 1) * math.exp(-s * 900) + 0.7 * square(ping * s) * math.exp(-s * 160)
                # The first hits land with the body of the die, a low thud.
                thud = 0.8 * math.sin(2 * math.pi * 170 * s) * math.exp(-s * 70) if strength > 0.6 else 0.0
                out[start + i] += strength * (click + thud)
            t += gap * rng.uniform(0.8, 1.15)
            gap *= 0.72
            strength *= rng.uniform(0.62, 0.78)
    return fade(out, attack_ms=0.5, release_ms=40)


def main() -> None:
    write("dice_roll", dice_roll(), 0.8, DICE_OUT)
    write("ui_click", ui_click(), 0.45)
    write("loan_warning", loan_warning(), 0.55)
    write("hyperspace_jump", hyperspace_jump(), 0.7)
    write("laser_shot", laser_shot(), 0.6)
    write("money_gained", money_gained(), 0.5)


if __name__ == "__main__":
    main()
