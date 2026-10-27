#!/usr/bin/env python3
"""Synthesises the Mandarin speech samples listed in samples.tsv as 16 kHz mono WAV files.

Uses Microsoft Edge's neural TTS voices (edge-tts) and falls back to espeak-ng if that service
is unavailable. Requires ffmpeg (preinstalled on GitHub's Ubuntu runners) for edge-tts output.

Usage: python3 make_samples.py samples.tsv out_dir
"""

import shutil
import subprocess
import sys
from pathlib import Path


def edge_tts(text: str, voice: str, wav: Path) -> bool:
    mp3 = wav.with_suffix(".mp3")
    try:
        subprocess.run(["edge-tts", "--voice", voice, "--text", text, "--write-media", str(mp3)],
                       check=True, capture_output=True, timeout=60)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(mp3), "-ar", "16000", "-ac", "1",
                        "-c:a", "pcm_s16le", str(wav)], check=True, timeout=60)
        mp3.unlink()
        return True
    except (subprocess.SubprocessError, FileNotFoundError) as e:
        print(f"  edge-tts failed for {text}: {e}")
        return False


def espeak(text: str, wav: Path) -> bool:
    raw = wav.with_suffix(".raw.wav")
    try:
        subprocess.run(["espeak-ng", "-v", "cmn", "-w", str(raw), text], check=True, timeout=60)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(raw), "-ar", "16000", "-ac", "1",
                        "-c:a", "pcm_s16le", str(wav)], check=True, timeout=60)
        raw.unlink()
        return True
    except (subprocess.SubprocessError, FileNotFoundError) as e:
        print(f"  espeak-ng failed for {text}: {e}")
        return False


def main() -> None:
    manifest, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    shutil.copy(manifest, out / "samples.tsv")
    engines = {}
    for line in manifest.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        name, _key, text, voice, _expect = line.split("\t")
        wav = out / f"{name}.wav"
        if edge_tts(text, voice, wav):
            engines[name] = "edge-tts " + voice
        elif espeak(text, wav):
            engines[name] = "espeak-ng"
        else:
            sys.exit(f"Could not synthesise {name}")
        print(f"{name}: {text} ({engines[name]})")
    (out / "engines.txt").write_text("\n".join(f"{k}\t{v}" for k, v in engines.items()) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
