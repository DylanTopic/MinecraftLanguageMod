# LangMod: Mandarin

A Fabric mod for Minecraft 26.1.2 that teaches Mandarin Chinese (Simplified) through exposure.
The first time you pick up an item type, you have to **say its Chinese name out loud** before you
get it. Speech recognition runs entirely on your computer with
[whisper.cpp](https://github.com/ggml-org/whisper.cpp). Nothing is sent anywhere.

## How it works

1. You pick up an item you have never picked up before (say, an apple). The mod holds it back and
   shows a popup with the item, its Chinese name in large characters (苹果), the pinyin with tone
   marks colored by tone (píng guǒ), the English name, a countdown and a red microphone dot.
2. Say the word. When you stop talking (about 0.8 s of silence) the clip goes to Whisper, which
   turns it into Chinese characters. The popup shows "Checking...". The countdown pauses while
   you speak and while Whisper works.
3. **Correct:** you get the item (dropped at your feet if your inventory is full), with a sound, a
   green flash and a few particles. That item type is now *learned* and is picked up normally from
   then on.
4. **Wrong:** the popup shows what Whisper heard, in characters and pinyin, next to the expected
   answer. Syllables that don't match are underlined in red, wrong tones in orange. Try again.
5. After 3 wrong answers, or when the 20 s run out, the item **disappears** with a puff of smoke.
   It stays unlearned, so the next one you find is a new chance.

Other rules:

- One challenge at a time. Anything else you pick up meanwhile waits in a queue (the popup shows
  how many are waiting).
- Silence, noise and recognition errors never count as a wrong answer. A transcription that crashes
  or takes longer than 10 s doesn't count either, and never makes an item vanish.
- If you die or quit mid-challenge, the held items are given back (they drop with the rest of your
  inventory when you die) and nothing counts as a failure. Held items are also saved with your
  player data, so even a game crash cannot lose them.
- Only picking items up from the ground counts. Crafting, chests and the creative inventory are
  not affected.
- Items without a Chinese name in the game files (e.g. most modded items) are picked up normally.
- Progress is stored per player and per world (in the world's player data).

Singleplayer only for now. All game logic runs on the (integrated) server and the microphone and
speech recognition on the client; they talk over Fabric networking, so multiplayer can be added
later.

## Requirements

- Minecraft Java Edition **26.1.2** with Fabric Loader 0.20.1+ and Fabric API
- **Java 25** (JDK) to build and run
- A microphone
- Windows x64, Linux x64 or macOS. On Windows: a CPU with AVX2 (Intel Core from 2013 on, any AMD
  Ryzen; some budget Pentium/Celeron chips lack it) and the Microsoft Visual C++ 2015-2022 runtime, which almost every gaming PC already has
  (otherwise: <https://aka.ms/vs/17/release/vc_redist.x64.exe>)
- About 0.5 GB of disk space for the Whisper model (downloaded separately, see below)
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu) for a "Configure" button

## Setup (Windows)

### 1. Java 25

Open PowerShell and run:

```powershell
java -version
```

If it does not print a version starting with `25`, install a JDK 25, for example Eclipse Temurin:

```powershell
winget install EclipseAdoptium.Temurin.25.JDK
```

(or download the `.msi` from <https://adoptium.net/temurin/releases/?version=25> and tick
"Set JAVA_HOME variable" in the installer). Open a **new** PowerShell window afterwards and check
`java -version` again. If you have several JDKs, point Gradle at JDK 25 for the current window:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.<version>-hotspot"
```

### 2. Start the development client

In the project folder:

```powershell
.\gradlew.bat runClient
```

The first run downloads Minecraft, Fabric and the other dependencies, which takes a few minutes.
Mod Menu is included in the development client.

### 3. Download the Whisper model

The model is not bundled (it is about 0.5 GB). On the first start the mod shows a message saying it is
missing and where it should go. Download **`ggml-small.bin`** from
<https://huggingface.co/ggerganov/whisper.cpp/tree/main> and put it in the `config\langmod\models`
folder of the game directory. For the development client that is `run\config\langmod\models`
inside the project folder:

```powershell
New-Item -ItemType Directory -Force run\config\langmod\models
curl.exe -L -o run\config\langmod\models\ggml-small.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin
```

Then restart the game (or run `/langmod reload`). Chat says "Speech recognition ready" when the
model has loaded (a few seconds, in the background).

Other sizes: `ggml-base.bin` (about 150 MB, faster, less accurate) or `ggml-medium.bin` (about
1.5 GB, slower, more accurate). Select them in the settings ("Whisper model"). Only the multilingual models work;
the `.en` ones are English only.

### 4. Check the microphone

In a world, run `/langmod test`, then say something in Chinese. Chat shows what Whisper heard, its
pinyin and how long it took. If nothing is detected, `/langmod mics` lists the microphones Java can
see; pick one in the settings ("Microphone").

### Using it in your normal Minecraft

Build the jar with `.\gradlew.bat build`, then copy `build\libs\langmod-0.1.0.jar` and
[Fabric API](https://modrinth.com/mod/fabric-api) into the `mods` folder of a Fabric 26.1.2
installation, and put the model in that installation's `config\langmod\models` folder (for the
default launcher: `%APPDATA%\.minecraft\config\langmod\models`).

## Commands

| Command | What it does |
|---|---|
| `/langmod test` | Records one utterance and prints what Whisper heard (characters, pinyin, timing). |
| `/langmod mics` | Lists the microphones. |
| `/langmod list [page]` | Learned items with their Chinese name, pinyin and English name. |
| `/langmod review` | The items you failed most often, with failure and wrong-answer counts. |
| `/langmod reset` | Forgets all learned items, so every item is challenged again. |
| `/langmod reset everything` | Also forgets the mistake history used by `/langmod review`. |
| `/langmod config` | Opens the settings screen. |
| `/langmod debug` | Toggles the debug overlay (also **F9**). |
| `/langmod reload` | Re-reads `langmod.json` and `pinyin_overrides.json`, reloads the speech model if its settings changed. |

The debug overlay shows the raw Whisper output, the simplified text, its pinyin, the expected
pinyin, the match result (errors, allowed errors, mode) and the audio and transcription times.

## Settings

Open the settings with Mod Menu's "Configure" button, `/langmod config`, or a key (unbound by
default; set it under Options > Controls > Key Binds > LangMod: Mandarin). Every change is saved
immediately to `config/langmod/langmod.json`. Speech settings take effect when you close the
screen. The file can also be edited by hand (then run `/langmod reload`).

| Setting (`langmod.json` key) | Default | Meaning |
|---|---|---|
| Challenges (`enabled`) | on | Master switch. Turning it off gives held items back. |
| English name (`showEnglish`) | on | Show the English name in the popup. |
| Attempts (`maxAttempts`) | 3 | Wrong answers before the item vanishes. |
| Time limit (`timerSeconds`) | 20 | Seconds to answer (paused while speaking and checking). |
| Matching (`matchMode`) | strict | `strict`, `toneless` or `lenient`, see below. |
| Long names from (`maxNameLength`) | 4 | Names with more characters than this are "long". |
| Long names (`longNameMode`) | lenient | `lenient` (one wrong syllable allowed) or `skip` (never challenged). |
| Prompt biasing (`promptBiasing`) | off | Give Whisper the expected word as a hint (see below). |
| Whisper model (`modelSize`) | small | `tiny`, `base`, `small`, `medium`, `large-v3-turbo`: loads `ggml-<size>.bin`. |
| Speech engine (`speechEngine`) | jni | `jni` (built in, CPU) or `server` (separate whisper.cpp program, GPU). |
| Use GPU (`useGpu`) | on | Only used by the `server` engine. |
| Whisper window (`audioContext`) | 0 = 30 s | Audio Whisper processes per answer: `0` = its full 30 s window, `-1` = just the clip (fastest, less accurate), or a number of 20 ms frames (`512` = 10 s). |
| CPU threads (`whisperThreads`) | 0 = auto | Auto uses half your cores (at most 8), so the game keeps running smoothly. |
| Microphone (`micDevice`) | system default | Part of a device name from `/langmod mics`. |
| Speech threshold (`vadThresholdDb`) | 12 | How much louder than background noise (dB) counts as speech. |
| Tone colors (`toneColors`) | red, green, blue, purple, grey | Tones 1-4 and neutral, as `#RRGGBB`. |
| Character size (`hanziScale`) | 3 | Size of the characters in the popup. |
| Debug overlay (`debugOverlay`) | off | See above. |

Only in the file:

| Key | Default | Meaning |
|---|---|---|
| `vadSilenceMs` | 800 | Silence that ends a recording. |
| `vadMaxClipMs` | 5000 | Longest recording. |
| `vadMinSpeechMs` | 200 | Shorter sounds (clicks, bumps) are ignored. |
| `transcriptionTimeoutSeconds` | 10 | Slower transcriptions are abandoned (not counted as an attempt). |
| `whisperServerPath` / `whisperServerPort` | "" / 8178 | For the `server` engine, see below. |
| `pinyinScale` | 1.5 | Size of the pinyin in the popup. |

## How answers are checked

1. Whisper's output is cleaned up: punctuation and spaces are removed, Traditional characters are
   converted to Simplified (with OpenCC's dictionaries), digits become Chinese numerals.
2. If the characters are exactly the expected ones, it is correct.
3. Otherwise the pinyin of what was heard is compared with the expected pinyin, syllable by
   syllable (a character with several readings matches if any of them fits). So a homophone such
   as 目棍 for 木棍 (both mù gùn) still counts.
   - **strict** (default): every syllable and its tone must match. Neutral tones in the expected
     answer accept any tone.
   - **toneless**: syllables must match, tones are ignored.
   - **lenient**: like strict, but names of 4 or more syllables may have one wrong syllable.
   - Names longer than "Long names from" are always checked leniently, or skipped, depending on
     "Long names".

**A note on tones:** Whisper transcribes *words*; it does not grade pronunciation. Tones are only
checked indirectly: if you say ní tǔ with the wrong tone, Whisper usually writes a different word
(你吐 nǐ tǔ instead of 泥土 ní tǔ) and the tone mismatch shows up. But Whisper also "corrects" many
slightly-off pronunciations to the word it expects from context, and sometimes writes a
same-sounding word with other tones even when you said it right. Tone checking is therefore
helpful but imperfect. If it gets in the way, use `toneless` matching.

## Speech recognition

The mod talks to Whisper through a small `SpeechEngine` interface with two implementations:

- **`jni` (default)**: whisper.cpp runs inside the game through the
  [whisper-jni](https://github.com/GiviMAD/whisper-jni) bindings (io.github.givimad:whisper-jni
  1.7.1 on Maven Central), which bundle native libraries for Windows x64, Linux and macOS. It is the
  maintained Java binding for whisper.cpp, it works on Java 25 (checked on Windows and Linux in CI),
  and it needs nothing installed besides the model, so it is the default. Its Windows build is CPU
  only and uses whisper.cpp 1.7.1.
- **`server`**: the mod starts a separate `whisper-server` program from a
  [whisper.cpp release](https://github.com/ggml-org/whisper.cpp/releases) and sends it the audio over
  `127.0.0.1` (nothing leaves your computer). Use this for an NVIDIA GPU (download the release zip
  with `cuda` in its name) or to use a newer whisper.cpp. Unzip it, then set `speechEngine` to
  `server` and `whisperServerPath` to the full path of `whisper-server.exe`, e.g.
  `"C:\\whisper\\whisper-server.exe"` (double backslashes in JSON). If `whisperServerPath` is empty,
  the mod expects a `whisper-server` you started yourself on `whisperServerPort`. Its output goes to
  `config/langmod/whisper-server.log`.

The model loads once, in the background, when the game starts. All transcriptions run on a
background thread, so the game never waits for Whisper. The language is always forced to Chinese.
The microphone is only open while a challenge (or `/langmod test`) is listening; the red dot in the
popup shows when it is. Audio is recorded as 16 kHz mono and split into utterances by a simple
voice activity detector (it adapts to background noise; 0.8 s of silence ends a recording, 5 s
maximum).

**Prompt biasing** (off by default) passes the expected word to Whisper as its "initial prompt".
Whisper then hears that word much more readily. In the tests below this fixed most false failures
(暂时 for 钻石, 念包 for 面包) without making the wrong-word and wrong-tone samples pass, but with
real learners it will also accept some answers that were not quite right. Turn it on if correct
answers are too often rejected.

### Measured accuracy and speed

`.github/workflows/speech.yml` synthesises 16 Mandarin samples (13 correct item names spoken by two
neural voices, plus a wrong word, a similar word and a wrong tone) and runs them through the mod's
real pipeline with the `small` model on GitHub's 2-core runners:

| How Whisper is called | Correct names accepted (strict) | Wrong answers rejected | Time per answer |
|---|---|---|---|
| Default: no prompt, 30 s window | 7 of 13 (9 of 13 toneless) | 3 of 3 | 13 s (1 thread), 12.6 s (2 threads) |
| Prompt biasing (expected word as prompt) | 12 of 13 | 3 of 3 | 13 s (1 thread) |
| No prompt, window cut to the clip | 2 of 4 (first 4 samples) | not measured | 1.0 s (2 threads) |

The misses without biasing are words Whisper turned into a more common word that sounds almost the
same: 钻石 zuàn shí became 暂时 zàn shí, 面包 miàn bāo became 念包, 火把 huǒ bǎ became 活吧 huó ba,
闪长岩 became 闪长眼 (yǎn instead of yán), and the one-syllable 弓 became the English "GO!" (also with
biasing). Homophones are still accepted through the pinyin comparison: 目棍 for 木棍 and 铁钉 for 铁锭
passed.

Synthesised speech is cleaner than a real microphone and a learner's accent, so expect somewhat
worse results in the game. A desktop CPU is much faster than these 2-core runners: Whisper's time
scales with the threads it gets (`whisperThreads`, auto = half your cores).

## Pinyin data

Chinese names are not translated by hand: they come from Minecraft's own `zh_cn.json`, looked up by
translation key (`item.minecraft.apple` → 苹果). `tools/generate_pinyin.py` turns them into
`src/main/resources/assets/langmod/pinyin/items.json` at build time using
[pypinyin](https://github.com/mozillazg/python-pinyin), which knows multi-character words
(长 in 闪长岩 is cháng, not zhǎng):

```json
"item.minecraft.apple": {"hanzi": "苹果", "pinyin_marks": "píng guǒ", "pinyin_numbers": "ping2 guo3"}
```

- Every name with a character that has several readings (长, 行, 重, ...) is listed in
  `tools/pinyin_review.md`, sorted so that the guesses most worth checking come first.
- Fixes that should survive regeneration go in `tools/pinyin_fixes.json` (11 so far, e.g. 闪长岩,
  木栅栏, 空地图).
- Your own fixes go in `config/langmod/pinyin_overrides.json` (created on first start, same format;
  `pinyin_numbers` is enough). They take priority over the bundled file; run `/langmod reload`
  after editing.

To regenerate (Python 3.10+):

```powershell
pip install -r tools/requirements.txt
python tools/generate_pinyin.py
```

By default the script downloads `zh_cn.json` for 26.1.2 from Mojang's servers (cached in
`tools/.cache`); `--lang-file` uses a local copy and `--from-loom-cache` reads the one Loom
downloaded. The bundled data was generated from the 26.1.2 `zh_cn.json` mirrored in
[InventivetalentDev/minecraft-assets](https://github.com/InventivetalentDev/minecraft-assets/tree/26.1.2)
(sha1 `6055d366b3523e9eb01c72b7bfbdb79c65353ae1`), because the machine it was built on could not
reach Mojang's servers; running the script once on your machine regenerates it from the official
file.

## Testing each stage

The mod was built in five stages. To check them by hand, start `.\gradlew.bat runClient`, create a
**Creative** world (so you can drop items with Q from the creative inventory and pick them up again):

1. **Setup:** the game reaches the title screen; Mods (Mod Menu) lists "LangMod: Mandarin".
2. **Pickup + popup:** drop an item you have not learned (e.g. an apple) and walk over it. It is not
   added to your inventory; the popup appears with the icon, 苹果, tone-colored pinyin, "Apple" and
   a countdown. Pick up something else meanwhile: it queues.
3. **Microphone + Whisper:** `/langmod mics` lists your microphone; `/langmod test` prints what you
   said with pinyin and timing.
4. **Answers:** pick up an apple and say "píngguǒ": green flash, sound, apples in your inventory. Pick
   up dirt and say something else: what Whisper heard is shown with the wrong syllables marked;
   after 3 wrong answers (or 20 s of silence) the dirt vanishes in a puff of smoke. Quit to the menu
   during a challenge and rejoin: the item is back in your inventory.
5. **Persistence, settings, commands:** quit and rejoin: learned items are still learned
   (`/langmod list`). Try `/langmod review`, `/langmod reset`, F9 (debug overlay) and the settings
   screen (Mods > LangMod > Configure, or `/langmod config`).

Automated tests:

```powershell
.\gradlew.bat test               # unit tests: pinyin, matching, text conversion, audio, whisper-server protocol
.\gradlew.bat runClientGameTest  # starts the real game with a scripted speech engine, plays through all of the above
```

The game test writes screenshots to `build\run\clientGameTest\screenshots`. Both run on every push
in GitHub Actions (`.github/workflows/build.yml`), which also uploads the built jar.

## Troubleshooting

- **"Whisper model not found"**: check the file name (`ggml-small.bin`) and folder
  (`config\langmod\models` in the game directory, `run\config\langmod\models` for `runClient`).
- **"Could not load the whisper.cpp native library"**: install the Visual C++ runtime
  (<https://aka.ms/vs/17/release/vc_redist.x64.exe>). Very old CPUs without AVX2 can use the
  `server` engine with a matching whisper.cpp build.
- **"No speech detected" / recordings never start**: pick the right microphone in the settings; lower
  "Speech threshold" if you speak quietly, raise it if background noise starts recordings. Windows
  must allow desktop apps to use the microphone (Settings > Privacy (& security) > Microphone >
  "Let desktop apps access your microphone").
- **Recordings start but are cut off**: raise `vadSilenceMs`.
- **"Transcription took longer than 10 s"** (not counted as an attempt): set "Whisper window" to
  10 s or "Clip only", use a smaller model (`base`), give Whisper more threads, or use the `server`
  engine with a GPU.
- **Correct answers are rejected too often**: turn on prompt biasing, or use `toneless` matching.
- **A wrong reading in the popup**: add the item to `config/langmod/pinyin_overrides.json`.
- Logs: `run\logs\latest.log` (development client) or `.minecraft\logs\latest.log`.

## Project layout

```
src/main/java/.../langmod/
  LangMod.java, LangModConfig.java      server entrypoint, settings file
  challenge/                            pickup interception and the challenge rules (server)
  command/                              /langmod
  core/                                 plain Java, unit tested: pinyin, matching, audio, text, speech engines
  data/                                 per-player saved data (Fabric data attachment)
  mixin/                                ItemEntity pickup hook, player death hook
  net/                                  packets between server and client
src/client/java/.../langmod/client/     microphone, speech service, popup and overlays, settings screen
src/gametest/                           client game test (runClientGameTest)
src/test/                               unit tests and the SpeechCheck tool used by CI
tools/                                  pinyin generator, fixes and review list
```

## Versions

From the official Fabric 26.1.2 template (<https://fabricmc.net/develop>): Minecraft 26.1.2, Fabric
Loader 0.20.1, Loom 1.19-SNAPSHOT (`net.fabricmc.fabric-loom`, the non-remapping plugin for
unobfuscated Minecraft, so the code uses Mojang's names), Fabric API 0.155.3+26.1.2, Gradle 9.8.2,
Java 25. Mod Menu 18.0.2, whisper-jni 1.7.1.

Third-party components and their licences are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
