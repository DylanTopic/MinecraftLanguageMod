#!/usr/bin/env python3
"""Synthesises the Mandarin speech samples listed in samples.tsv as 16 kHz mono WAV files.

Uses Microsoft Edge's neural TTS voices (edge-tts) and the static ffmpeg binary from the
imageio-ffmpeg wheel to convert its MP3 output (no system packages needed).

Usage: python3 make_samples.py samples.tsv out_dir
"""

import shutil
import subprocess
import sys
from pathlib import Path

import imageio_ffmpeg

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()


def synthesise(text: str, voice: str, wav: Path) -> None:
    mp3 = wav.with_suffix(".mp3")
    for attempt in range(3):
        try:
            subprocess.run([sys.executable, "-m", "edge_tts", "--voice", voice, "--text", text, "--write-media", str(mp3)],
                           check=True, capture_output=True, timeout=60)
            break
        except subprocess.SubprocessError as e:
            print(f"  edge-tts attempt {attempt + 1} failed for {text}: {e}")
    else:
        sys.exit(f"Could not synthesise {text}")
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", str(mp3), "-ar", "16000", "-ac", "1",
                    "-c:a", "pcm_s16le", str(wav)], check=True, timeout=60)
    mp3.unlink()


def main() -> None:
    manifest, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    shutil.copy(manifest, out / "samples.tsv")
    engines = {}
    for line in manifest.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        name, _key, text, voice, _expect, _language = line.split("\t")
        synthesise(text, voice, out / f"{name}.wav")
        engines[name] = "edge-tts " + voice
        print(f"{name}: {text} ({engines[name]})")
    (out / "engines.txt").write_text("\n".join(f"{k}\t{v}" for k, v in engines.items()) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
