#!/usr/bin/env python3
"""Work out the kana reading of every Japanese item name.

Reads src/main/resources/assets/langmod/names/ja_jp.json (written by generate_names.py) and writes

  src/main/resources/assets/langmod/names/ja_jp_kana.json
      name -> its reading in hiragana, words separated by spaces ("緑色の羊毛": "みどりいろ の ようもう").
      The mod shows it (with its romaji) under names written with kanji, and accepts an answer
      written in kana for a name written in kanji.
  tools/japanese_review.md
      every name with kanji and its reading, for checking by someone who reads Japanese.

Readings come from SudachiPy and its core dictionary (Apache License 2.0). Corrections that
should survive regeneration go in tools/japanese_fixes.json (name -> reading).

Usage (Python 3.10+):
  pip install -r tools/requirements.txt
  python tools/generate_japanese.py
"""

from __future__ import annotations

import json
import unicodedata
from pathlib import Path

from sudachipy import dictionary, tokenizer

ROOT = Path(__file__).resolve().parent.parent
NAMES = ROOT / "src" / "main" / "resources" / "assets" / "langmod" / "names" / "ja_jp.json"
OUT = NAMES.with_name("ja_jp_kana.json")
REVIEW = ROOT / "tools" / "japanese_review.md"
FIXES = ROOT / "tools" / "japanese_fixes.json"


def to_hiragana(text: str) -> str:
    return "".join(chr(ord(ch) - 0x60) if "ァ" <= ch <= "ヶ" else ch for ch in text)


def has_kanji(text: str) -> bool:
    return any("CJK UNIFIED IDEOGRAPH" in unicodedata.name(ch, "") or ch == "々" for ch in text)


def is_kana(ch: str) -> bool:
    return "ぁ" <= ch <= "ゖ" or ch in "ー "


def main() -> None:
    names = json.loads(NAMES.read_text(encoding="utf-8"))
    fixes = json.loads(FIXES.read_text(encoding="utf-8")) if FIXES.exists() else {}
    fixes = {k: v for k, v in fixes.items() if not k.startswith("_")}
    sudachi = dictionary.Dictionary(dict="core").tokenizer()
    mode = tokenizer.Tokenizer.SplitMode.C
    readings: dict[str, str] = {}
    odd: list[str] = []

    for text in sorted(set(names.values())):
        if text in fixes:
            readings[text] = fixes[text]
            continue
        words: list[str] = []
        previous_kanji_noun = False
        for morpheme in sudachi.tokenize(unicodedata.normalize("NFKC", text), mode):
            pos = morpheme.part_of_speech()[0]
            if not morpheme.surface().strip() or pos in ("補助記号", "記号", "空白"):
                continue
            reading = to_hiragana(morpheme.reading_form() or morpheme.surface())
            kanji_noun = pos == "名詞" and has_kanji(morpheme.surface())
            # Verb endings and suffixes belong to the word before them, and so do the parts of a
            # compound written in kanji (深層+岩): the spaces only make the romaji easier to read.
            if words and (pos in ("助動詞", "接尾辞") or (kanji_noun and previous_kanji_noun)):
                words[-1] += reading
            else:
                words.append(reading)
            previous_kanji_noun = kanji_noun
        reading = " ".join(words)
        readings[text] = reading
        if not all(is_kana(ch) for ch in reading):
            odd.append(text)

    lines = [f"\t{json.dumps(name, ensure_ascii=False)}: {json.dumps(readings[name], ensure_ascii=False)}" for name in sorted(readings)]
    OUT.write_text("{\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8")

    with_kanji = sorted((k for k, v in names.items() if has_kanji(v)), key=lambda k: names[k])
    review = ["# Japanese readings to check", "",
              "Written by `tools/generate_japanese.py` from SudachiPy. Fix a reading in `tools/japanese_fixes.json`",
              "(name -> reading in hiragana, words separated by spaces) and run the script again.", ""]
    if odd:
        review += ["## Readings that are not all kana", ""] + [f"- {name}: {readings[name]}" for name in odd] + [""]
    review += ["## Names with kanji", "", "| Key | Name | Reading |", "|---|---|---|"]
    review += [f"| `{key}` | {names[key]} | {readings[names[key]]} |" for key in with_kanji]
    REVIEW.write_text("\n".join(review) + "\n", encoding="utf-8")
    print(f"{len(readings)} readings, {len(with_kanji)} names with kanji, {len(odd)} not all kana")


if __name__ == "__main__":
    main()
