#!/usr/bin/env python3
"""Extract the item and block names LangMod teaches in every language except Mandarin.

Reads Minecraft's own language files and writes, for each language,

  src/main/resources/assets/langmod/names/<locale>.json
      translation key -> name for every item/block, one entry per line so it is easy to edit.

Mandarin has its own generator (generate_pinyin.py) because it also needs pinyin, and the
Japanese readings come from generate_japanese.py. Korean and Russian romanisation is worked out
by the mod itself, so these files only hold the names.

In the languages added later (see SKIP_ENGLISH), names the translation left in English (Bosnian
has 151, like "Cherry Door") are dropped: those items are picked up normally.

Where the language files come from:
  --lang-dir DIR      a folder holding <locale>.json files (and en_us.json)
  (default)           Mojang's official servers, cached in tools/.cache/

Usage:
  python tools/generate_names.py [--mc-version 26.1.2] [--locales es_mx,fr_ca,...]
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NAMES_DIR = ROOT / "src" / "main" / "resources" / "assets" / "langmod" / "names"
CACHE_DIR = ROOT / "tools" / ".cache"
VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
ASSET_SERVER = "https://resources.download.minecraft.net"
ITEM_KEY = re.compile(r"^(item|block)\.minecraft\.[a-z0-9_]+$")
LOCALES = "es_mx,fr_ca,ko_kr,ru_ru,nl_nl,la_la,pt_br,bs_ba,fil_ph,ja_jp"
SKIP_ENGLISH = {"ru_ru", "nl_nl", "pt_br", "bs_ba", "fil_ph", "ja_jp", "la_la"}


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


def lang_file_from_mojang(mc_version: str, locale: str) -> bytes:
    manifest = json.loads(http_get(VERSION_MANIFEST))
    for version in manifest["versions"]:
        if version["id"] == mc_version:
            info = json.loads(cached_download(version["url"], f"{mc_version}.json", version.get("sha1")))
            break
    else:
        raise SystemExit(f"Minecraft version {mc_version} not found in Mojang's version manifest")
    index_info = info["assetIndex"]
    index = json.loads(cached_download(index_info["url"], f"assets-{index_info['id']}.json", index_info.get("sha1")))
    sha1 = index["objects"][f"minecraft/lang/{locale}.json"]["hash"]
    return cached_download(f"{ASSET_SERVER}/{sha1[:2]}/{sha1}", f"{locale}-{mc_version}.json", sha1)


def en_us_from_mojang(mc_version: str) -> bytes:
    # en_us.json ships inside the client jar rather than the asset index.
    manifest = json.loads(http_get(VERSION_MANIFEST))
    for version in manifest["versions"]:
        if version["id"] == mc_version:
            info = json.loads(cached_download(version["url"], f"{mc_version}.json", version.get("sha1")))
            break
    else:
        raise SystemExit(f"Minecraft version {mc_version} not found in Mojang's version manifest")
    client = info["downloads"]["client"]
    jar = cached_download(client["url"], f"client-{mc_version}.jar", client.get("sha1"))
    with zipfile.ZipFile(io.BytesIO(jar)) as zf:
        return zf.read("assets/minecraft/lang/en_us.json")


def english_name(english: dict[str, str], key: str) -> str:
    """The English name, or one made from the key for blocks newer than the en_us.json at hand."""
    return english.get(key) or " ".join(word.capitalize() for word in key.rsplit(".", 1)[-1].split("_"))


def write_names(path: Path, names: dict[str, str]) -> None:
    lines = [f"\t{json.dumps(key, ensure_ascii=False)}: {json.dumps(names[key], ensure_ascii=False)}" for key in sorted(names)]
    path.write_text("{\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mc-version", default="26.1.2")
    parser.add_argument("--locales", default=LOCALES)
    parser.add_argument("--lang-dir", type=Path, help="read <locale>.json and en_us.json from this folder instead of downloading")
    parser.add_argument("--out-dir", type=Path, default=NAMES_DIR)
    args = parser.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)
    english: dict[str, str] | None = None
    for locale in args.locales.split(","):
        raw = (args.lang_dir / f"{locale}.json").read_bytes() if args.lang_dir else lang_file_from_mojang(args.mc_version, locale)
        lang = json.loads(raw.decode("utf-8"))
        names = {key: name.strip() for key, name in lang.items() if ITEM_KEY.match(key) and name.strip()}
        skipped = 0
        if locale in SKIP_ENGLISH:
            if english is None:
                en_raw = (args.lang_dir / "en_us.json").read_bytes() if args.lang_dir else en_us_from_mojang(args.mc_version)
                english = json.loads(en_raw.decode("utf-8"))
            untranslated = [key for key, name in names.items() if name.lower() == english_name(english, key).lower()]
            skipped = len(untranslated)
            for key in untranslated:
                del names[key]
        write_names(args.out_dir / f"{locale}.json", names)
        print(f"{locale}: {len(names)} names, {skipped} left in English skipped (source sha1 {hashlib.sha1(raw).hexdigest()})")


if __name__ == "__main__":
    main()
