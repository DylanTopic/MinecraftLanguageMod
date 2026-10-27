#!/usr/bin/env python3
"""Generate the pinyin data bundled with LangMod.

Reads Minecraft's own Simplified Chinese language file (zh_cn.json) and writes:

  src/main/resources/assets/langmod/pinyin/items.json
      translation key -> {"hanzi", "pinyin_marks", "pinyin_numbers"} for every item/block name.
      One entry per line so it is easy to edit by hand.
  src/main/resources/assets/langmod/pinyin/chars.tsv
      every CJK character pypinyin knows -> all of its readings (tone numbers). The mod uses it
      at runtime to turn whatever Whisper transcribed into pinyin.
  tools/pinyin_review.md
      every item whose name contains a character with more than one reading (长, 行, 重, ...),
      grouped by character, so the chosen readings can be checked by hand.

Hand corrections live in tools/pinyin_fixes.json and are re-applied on every run.

Where zh_cn.json comes from (first match wins):
  --lang-file PATH        an explicit file
  --from-loom-cache       Fabric Loom's asset cache (~/.gradle/caches/fabric-loom/assets)
  (default)               Mojang's official asset server, cached in tools/.cache/

Usage:
  pip install -r tools/requirements.txt
  python tools/generate_pinyin.py [--mc-version 26.1.2]
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import sys
import urllib.request
import zipfile
from collections import defaultdict
from datetime import date
from pathlib import Path

try:
    import pypinyin
    from pypinyin import Style, load_phrases_dict, pinyin
    from pypinyin.constants import PHRASES_DICT, PINYIN_DICT
    from pypinyin.contrib.tone_convert import to_tone, to_tone3
except ImportError:  # pragma: no cover - friendly message for a missing build dependency
    sys.exit("pypinyin is not installed. Run: pip install -r tools/requirements.txt")

ROOT = Path(__file__).resolve().parent.parent
PINYIN_DIR = ROOT / "src" / "main" / "resources" / "assets" / "langmod" / "pinyin"
CACHE_DIR = ROOT / "tools" / ".cache"
VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
ASSET_SERVER = "https://resources.download.minecraft.net"

ITEM_KEY = re.compile(r"^(item|block)\.minecraft\.[a-z0-9_]+$")
# Characters pypinyin may know but that are irrelevant for transcriptions (keep the table small).
CJK_RANGES = [(0x3400, 0x4DBF), (0x4E00, 0x9FFF), (0xF900, 0xFAFF)]


def is_cjk(ch: str) -> bool:
    cp = ord(ch)
    return any(lo <= cp <= hi for lo, hi in CJK_RANGES) or 0x20000 <= cp <= 0x3134F


# --------------------------------------------------------------------------------------------
# Fetching zh_cn.json / en_us.json
# --------------------------------------------------------------------------------------------

def http_get(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()


def cached_download(url: str, name: str, sha1: str | None = None) -> bytes:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    path = CACHE_DIR / name
    if path.exists():
        data = path.read_bytes()
        if sha1 is None or hashlib.sha1(data).hexdigest() == sha1:
            return data
    print(f"  downloading {url}")
    data = http_get(url)
    if sha1 is not None and hashlib.sha1(data).hexdigest() != sha1:
        raise SystemExit(f"Checksum mismatch for {url}")
    path.write_bytes(data)
    return data


def mojang_version_json(mc_version: str) -> dict:
    manifest = json.loads(http_get(VERSION_MANIFEST))
    for version in manifest["versions"]:
        if version["id"] == mc_version:
            return json.loads(cached_download(version["url"], f"{mc_version}.json", version.get("sha1")))
    raise SystemExit(f"Minecraft version {mc_version} not found in Mojang's version manifest")


def zh_cn_from_mojang(mc_version: str) -> bytes:
    version = mojang_version_json(mc_version)
    index_info = version["assetIndex"]
    index = json.loads(cached_download(index_info["url"], f"assets-{index_info['id']}.json", index_info.get("sha1")))
    obj = index["objects"]["minecraft/lang/zh_cn.json"]
    sha1 = obj["hash"]
    return cached_download(f"{ASSET_SERVER}/{sha1[:2]}/{sha1}", f"zh_cn-{mc_version}.json", sha1)


def en_us_from_mojang(mc_version: str) -> bytes:
    # en_us.json ships inside the client jar rather than the asset index.
    version = mojang_version_json(mc_version)
    client = version["downloads"]["client"]
    jar = cached_download(client["url"], f"client-{mc_version}.jar", client.get("sha1"))
    with zipfile.ZipFile(io.BytesIO(jar)) as zf:
        return zf.read("assets/minecraft/lang/en_us.json")


def zh_cn_from_loom_cache() -> bytes:
    assets = Path.home() / ".gradle" / "caches" / "fabric-loom" / "assets"
    indexes = sorted((assets / "indexes").glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    for index_path in indexes:
        objects = json.loads(index_path.read_text(encoding="utf-8")).get("objects", {})
        entry = objects.get("minecraft/lang/zh_cn.json")
        if entry:
            obj = assets / "objects" / entry["hash"][:2] / entry["hash"]
            if obj.exists():
                print(f"  using {obj} (asset index {index_path.name})")
                return obj.read_bytes()
    raise SystemExit(f"No zh_cn.json found in Loom's asset cache at {assets}. Run ./gradlew runClient once first.")


# --------------------------------------------------------------------------------------------
# Pinyin helpers
# --------------------------------------------------------------------------------------------

def numbers_to_marks(numbers: str) -> str:
    return " ".join(to_tone(syllable.replace("u:", "v")) for syllable in numbers.split())


def readings(ch: str) -> list[str]:
    """All readings of one character, tone numbers, most common first."""
    result = pinyin(ch, style=Style.TONE3, heteronym=True, neutral_tone_with_five=True)
    seen: list[str] = []
    for r in result[0] if result else []:
        if r not in seen and r != ch:
            seen.append(r)
    return seen


def phrase_backed(hanzi: str, index: int, extra_phrases: dict) -> bool:
    """True if some dictionary word of 2+ characters covers position index."""
    for start in range(0, index + 1):
        for end in range(index + 1, len(hanzi) + 1):
            if end - start >= 2:
                word = hanzi[start:end]
                if word in PHRASES_DICT or word in extra_phrases:
                    return True
    return False


def load_fixes(path: Path) -> tuple[dict, dict]:
    if not path.exists():
        return {}, {}
    data = json.loads(path.read_text(encoding="utf-8"))
    phrases = {k: v for k, v in data.get("phrases", {}).items() if not k.startswith("_")}
    keys = {k: v for k, v in data.get("keys", {}).items() if not k.startswith("_")}
    for word, numbers in phrases.items():
        syllables = numbers.split()
        if len(syllables) != len(word):
            raise SystemExit(f"pinyin_fixes.json: phrase {word!r} has {len(word)} characters but {len(syllables)} syllables")
    # pypinyin expects tone marks: {"长剑": [["cháng"], ["jiàn"]]}
    load_phrases_dict({w: [[to_tone(s)] for s in n.split()] for w, n in phrases.items()})
    return phrases, keys


# --------------------------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------------------------

def write_items_json(path: Path, entries: dict[str, dict]) -> None:
    lines = []
    for key in sorted(entries):
        e = entries[key]
        lines.append(
            "\t" + json.dumps(key, ensure_ascii=False) + ": {"
            + f"\"hanzi\": {json.dumps(e['hanzi'], ensure_ascii=False)}, "
            + f"\"pinyin_marks\": {json.dumps(e['pinyin_marks'], ensure_ascii=False)}, "
            + f"\"pinyin_numbers\": {json.dumps(e['pinyin_numbers'], ensure_ascii=False)}"
            + "}"
        )
    path.write_text("{\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8", newline="\n")


def write_chars_tsv(path: Path) -> int:
    rows = []
    for cp in sorted(PINYIN_DICT):
        ch = chr(cp)
        if not any(lo <= cp <= hi for lo, hi in CJK_RANGES):
            continue
        rs = readings(ch)
        if rs:
            rows.append(f"{ch}\t{','.join(rs)}")
    header = (
        "# Every CJK character known to pypinyin and all of its readings (tone numbers, 5 = neutral,\n"
        "# v = u-umlaut, most common first). Generated by tools/generate_pinyin.py; do not edit.\n"
    )
    path.write_text(header + "\n".join(rows) + "\n", encoding="utf-8", newline="\n")
    return len(rows)


def write_review(path: Path, review: dict, skipped: list, english: dict, source: str) -> None:
    out = [
        "# Pinyin review list",
        "",
        f"Generated by `tools/generate_pinyin.py` on {date.today().isoformat()} from {source}, "
        f"pypinyin {pypinyin.__version__}.",
        "",
        "Every item below contains a character with more than one reading. Check the reading in **bold**:",
        "",
        "- **default** rows: no dictionary word covers that character, so pypinyin used its most common",
        "  reading. Check these first.",
        "- **word** rows: the reading came from pypinyin's word dictionary and is usually right.",
        "",
        "To fix an entry permanently, add it to `tools/pinyin_fixes.json` (`keys` for one item, `phrases`",
        "for a word that appears in several items) and rerun the script. To fix it only in your game, put",
        "it in `config/langmod/pinyin_overrides.json` (same format as `items.json`).",
        "",
    ]
    order = sorted(review, key=lambda ch: (-sum(1 for r in review[ch] if not r["phrase"]), -len(review[ch]), ch))
    out.append("| Character | Readings | Items | Default reading used |")
    out.append("|---|---|---|---|")
    for ch in order:
        rows = review[ch]
        alts = " / ".join(numbers_to_marks(r) for r in readings(ch))
        out.append(f"| {ch} | {alts} | {len(rows)} | {sum(1 for r in rows if not r['phrase'])} |")
    out.append("")
    for ch in order:
        rows = review[ch]
        alts = " / ".join(numbers_to_marks(r) for r in readings(ch))
        out.append(f"## {ch} ({alts})")
        out.append("")
        out.append("| Key | Hanzi | Pinyin | English | Source |")
        out.append("|---|---|---|---|---|")
        for r in sorted(rows, key=lambda r: (r["phrase"], r["key"])):
            marks = r["marks"].split()
            marks[r["index"]] = f"**{marks[r['index']]}**"
            out.append(
                f"| `{r['key']}` | {r['hanzi']} | {' '.join(marks)} | {english.get(r['key'], '')} | "
                f"{'word' if r['phrase'] else '**default**'} |"
            )
        out.append("")
    if skipped:
        out.append("## Skipped (not challenged)")
        out.append("")
        out.append("These names contain letters or digits that have no pinyin, so the mod never challenges them.")
        out.append("Add an entry to `tools/pinyin_fixes.json` → `keys` if you want one anyway.")
        out.append("")
        out.append("| Key | Name | English |")
        out.append("|---|---|---|")
        for key, hanzi in skipped:
            out.append(f"| `{key}` | {hanzi} | {english.get(key, '')} |")
        out.append("")
    path.write_text("\n".join(out), encoding="utf-8", newline="\n")


# --------------------------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mc-version", default="26.1.2")
    parser.add_argument("--lang-file", type=Path, help="use this zh_cn.json instead of downloading it")
    parser.add_argument("--en-file", type=Path, help="en_us.json, only used for the English column of the review list")
    parser.add_argument("--from-loom-cache", action="store_true", help="read zh_cn.json from Fabric Loom's asset cache")
    parser.add_argument("--no-english", action="store_true", help="do not download the client jar for English names")
    parser.add_argument("--out-dir", type=Path, default=PINYIN_DIR)
    parser.add_argument("--review", type=Path, default=ROOT / "tools" / "pinyin_review.md")
    parser.add_argument("--fixes", type=Path, default=ROOT / "tools" / "pinyin_fixes.json")
    args = parser.parse_args()

    print("Reading zh_cn.json ...")
    if args.lang_file:
        raw = args.lang_file.read_bytes()
        source = f"`{args.lang_file.name}`"
    elif args.from_loom_cache:
        raw = zh_cn_from_loom_cache()
        source = "Fabric Loom's asset cache"
    else:
        raw = zh_cn_from_mojang(args.mc_version)
        source = f"Mojang's asset server (Minecraft {args.mc_version})"
    source += f" (zh_cn.json sha1 `{hashlib.sha1(raw).hexdigest()}`)"
    lang = json.loads(raw.decode("utf-8"))

    english: dict = {}
    if args.en_file:
        english = json.loads(args.en_file.read_text(encoding="utf-8"))
    elif not args.no_english and not args.lang_file:
        try:
            english = json.loads(en_us_from_mojang(args.mc_version).decode("utf-8"))
        except Exception as e:  # English names are optional
            print(f"  (no English names: {e})")

    extra_phrases, key_fixes = load_fixes(args.fixes)

    entries: dict[str, dict] = {}
    review: dict[str, list] = defaultdict(list)
    skipped: list[tuple[str, str]] = []
    for key, value in lang.items():
        if not ITEM_KEY.match(key):
            continue
        hanzi = "".join(value.split())
        if key in key_fixes:
            numbers = " ".join(key_fixes[key].split())
        elif not hanzi or not all(is_cjk(c) for c in hanzi):
            skipped.append((key, value))
            continue
        else:
            numbers = " ".join(pinyin_word(hanzi))
        if len(numbers.split()) != len(hanzi):
            raise SystemExit(f"{key}: {hanzi!r} has {len(hanzi)} characters but pinyin {numbers!r}")
        marks = numbers_to_marks(numbers)
        entries[key] = {"hanzi": hanzi, "pinyin_marks": marks, "pinyin_numbers": numbers}
        for i, ch in enumerate(hanzi):
            if len(readings(ch)) > 1:
                review[ch].append({
                    "key": key, "hanzi": hanzi, "marks": marks, "index": i,
                    "phrase": key in key_fixes or phrase_backed(hanzi, i, extra_phrases),
                })

    args.out_dir.mkdir(parents=True, exist_ok=True)
    write_items_json(args.out_dir / "items.json", entries)
    n_chars = write_chars_tsv(args.out_dir / "chars.tsv")
    write_review(args.review, review, skipped, english, source)

    flagged = {r["key"] for rows in review.values() for r in rows}
    defaults = {r["key"] for rows in review.values() for r in rows if not r["phrase"]}
    print(f"items.json: {len(entries)} names ({len(skipped)} skipped)")
    print(f"chars.tsv:  {n_chars} characters")
    print(f"review:     {len(flagged)} names contain a multi-reading character "
          f"({len(defaults)} use a default reading) -> {args.review.relative_to(ROOT) if args.review.is_relative_to(ROOT) else args.review}")


def pinyin_word(hanzi: str) -> list[str]:
    """Phrase-aware reading of a whole name in tone numbers (pypinyin segments it into words)."""
    syllables = pinyin(hanzi, style=Style.TONE3, neutral_tone_with_five=True, errors="default")
    return [s[0] for s in syllables]


if __name__ == "__main__":
    main()
