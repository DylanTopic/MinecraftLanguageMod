# LangMod

A Fabric mod for Minecraft 26.1.2 that teaches a language through exposure: **Mandarin Chinese**
(Simplified), **Spanish** (Mexico), **French** (Canada), **Korean**, **Russian**, **Dutch**,
**Latin**, **Tagalog**, **Japanese**, **Bosnian**, **Portuguese** (Brazil), **Vietnamese**,
**German** or **English**. The first time you pick up an
item type, you have to **say its name out loud** in the language you are learning before you get
it. Get it wrong and a voice says the name for you, so you can hear it and try again. Speech
recognition runs entirely on your computer with [whisper.cpp](https://github.com/ggml-org/whisper.cpp),
and the voice is one installed in Windows. Nothing is sent anywhere.

Item names are Minecraft's own translations (the ones you see when you switch the game to that
language), so nothing is translated by hand.

**Just want to play it?** Download the mod from the
[latest release](https://github.com/DylanTopic/MinecraftLanguageMod/releases/latest) and follow
[INSTALL.md](INSTALL.md), a step-by-step guide that needs no programming tools. The rest of this file
describes the mod in detail and how to build and change it.

## How it works

1. You pick up an item you have never picked up before (say, an apple). The mod holds it back and
   shows a popup with the item, its name in large letters (苹果, Manzana, Pomme or 사과), the
   pronunciation where it helps (pinyin with tone marks colored by tone, píng guǒ, or the Korean
   romanization, sagwa), the English name, a countdown and a red microphone dot.
2. Say the word. When you stop talking (about 0.8 s of silence) the clip goes to Whisper, which
   writes down what you said. The popup shows "Checking...". The countdown pauses while you speak
   and while Whisper works.
3. **Correct:** you get the item (dropped at your feet if your inventory is full), with a sound, a
   green flash and a few particles. That item type is now *learned* and is picked up normally from
   then on.
4. **Wrong:** the popup shows what Whisper heard next to the expected answer. The words or
   syllables that don't match are underlined in red (in Mandarin, wrong tones in orange). A voice
   then says the name out loud ([Hearing the name](#hearing-the-name)), and the countdown waits
   until it has finished. Try again.
5. After 3 wrong answers, or when the 20 s run out, the item **disappears** with a puff of smoke,
   and its name is said once more. It stays unlearned, so the next one you find is a new chance.

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
- Items without a name in the game files (e.g. most modded items) are picked up normally, and so is
  TNT in Mandarin and Korean, where its name is just the letters "TNT".
- Progress is stored per player, per world and per language (in the world's player data).

## Choosing a language

Open the language menu with **Practice Language** in the game menu (Esc), `/langmod language` (or
a key you can set under Options > Controls > Key Binds > LangMod), or change "Learning" at the top
of the settings screen. You learn one language at a time:

- Each language keeps its own progress: an apple learned in Spanish is still new in Korean.
- Switching while a challenge is running gives the held items back; nothing counts as a failure.
- Mandarin is the default. Progress from before languages were added counts as Mandarin progress.

| Language | Names from | Long names, by default | Shown under the name |
|---|---|---|---|
| Mandarin Chinese | `zh_cn` | more than 4 characters: one wrong syllable allowed | pinyin colored by tone |
| Spanish (Mexico) | `es_mx` | more than 3 words: not challenged | - |
| French (Canada) | `fr_ca` | more than 3 words: not challenged | - |
| Korean | `ko_kr` | more than 2 words: not challenged | Revised Romanization |
| Russian | `ru_ru` | more than 3 words: not challenged | transliteration (Яблоко: Yabloko) |
| Dutch | `nl_nl` | more than 2 words: not challenged | - |
| Latin | `la_la` | more than 3 words: not challenged | - |
| Tagalog | `fil_ph` | more than 3 words: not challenged | - |
| Japanese | `ja_jp` | more than 12 characters: not challenged | reading and romaji (松明: たいまつ taimatsu) |
| Bosnian | `bs_ba` | more than 3 words: not challenged | - |
| Portuguese (Brazil) | `pt_br` | more than 3 words: not challenged | - |
| Vietnamese | `vi_vn` | more than 4 syllables: not challenged | - (the accents are the tones) |
| German | `de_de` | more than 2 words: not challenged | - |
| English | `en_us` | more than 3 words: not challenged | - |

- The pronunciation under the name (pinyin, romanization, reading) can be hidden with the **Show
  pronunciation under names** checkbox in the Practice Language menu, to read the name on its own.
- Tagalog uses Minecraft's "Filipino" translation, the standard form of Tagalog with the words
  people use every day (Kalabasa, Karot). Minecraft's "Tagalog" translation uses rare, purist
  words instead (Kandos, Asanorya).
- Names a translation left in English are not challenged (151 in Bosnian, like "Cherry Door";
  a few loanwords such as "Lava" in the others), and neither is TNT.
- Whisper hardly understands Latin on its own, so in Latin it relies on prompt biasing (on by
  default). It was measured with Italian voices, i.e. Latin as said in church; a classical
  pronunciation may be recognised less well (see [Measured accuracy](#measured-accuracy-and-speed)).

Singleplayer only for now. All game logic runs on the (integrated) server and the microphone and
speech recognition on the client; they talk over Fabric networking, so multiplayer can be added
later.

## Items list

**Items** in the game menu (Esc), or `/langmod items`, lists every item that has a name in the
language you are learning, with its icon, in alphabetical order (Mandarin by pinyin). Items you have
learned are marked "learned".

- Type in the search box to find an item by its name (accents and tone marks optional: "pina",
  "pingguo"), its pronunciation ("sagwa"), its name in the game's language ("apple") or its id.
- Click an item to turn it off (red square): it is then picked up normally and never challenged.
  Click it again to turn it back on (green). Every item is on until you turn it off.
- Long names that are not challenged anyway (see "Long names" in the settings) have a grey square.

Turned-off items are kept per language in `config/langmod/langmod.json` (`disabledItems`), so they
apply in every world.

## Hearing the name

After a wrong answer, and when an item is lost, a voice says the name out loud, so you can hear how
it sounds and adjust before trying again. The microphone is paused while it speaks (it would
otherwise hear the voice) and the countdown waits. Press **H** to hear the name again; it works only
once you have got that item wrong, so it never gives an answer away. "Say the name when wrong" in
the settings turns it off; the volume is Minecraft's Music & Sounds > Voice/Speech.

The voices are the ones installed in Windows: the mod downloads nothing and sends nothing online.
An English Windows usually has only English voices, so add the voice of the language you learn:

1. Windows Settings > Time & language > Speech > Manage voices > **Add voices** (Windows 10:
   Settings > Time & Language > Speech > Add voices).
2. Install the voice from the table below.
3. In the game, run `/langmod voices` (or restart Minecraft). It lists the voices found and the one
   each language uses.

| Learning | Voice to add in Windows |
|---|---|
| Mandarin Chinese | Chinese (Simplified, China) |
| Spanish (Mexico) | Spanish (Mexico); Spanish (Spain) also works |
| French (Canada) | French (Canada); French (France) also works |
| Korean | Korean |
| Russian | Russian |
| Dutch | Dutch (Netherlands) |
| Latin | Italian: there is no Latin voice, and an Italian one reads Latin the way it is said in church |
| Tagalog | none: Windows has no Filipino voice, so Tagalog names are not said |
| Japanese | Japanese |
| Bosnian | Croatian: there is no Bosnian voice, and Croatian has the same sounds and spelling |
| Portuguese (Brazil) | Portuguese (Brazil) |
| Vietnamese | Vietnamese |
| German | German (Germany) |
| English | none: Windows comes with English voices |

When a language has several voices, pick one with "Voice" in the settings; changing it or "Voice
speed" plays a sample (the word for apple). Japanese names written with kanji are read from their
kana reading, the one shown in the popup. A voice may now and then misread a Chinese character that
has several readings; the pinyin in the popup is what counts.

The mod reaches the voices through Windows PowerShell, which is part of Windows: it writes
`windows-voices.ps1` into `config\langmod` and runs it while needed (it stops after a few idle
minutes). On Linux and macOS there are no voices yet: names are only shown.

## Requirements

- Minecraft Java Edition **26.1.2** with Fabric Loader 0.20.1+ and Fabric API
- **Java 25** (JDK) to build and run
- A microphone
- Windows x64, Linux x64 or macOS. On Windows: a CPU with AVX2 (Intel Core from 2013 on, any AMD
  Ryzen; some budget Pentium/Celeron chips lack it) and the Microsoft Visual C++ 2015-2022 runtime, which almost every gaming PC already has
  (otherwise: <https://aka.ms/vs/17/release/vc_redist.x64.exe>)
- About 0.5 GB of disk space for the Whisper model (downloaded separately, see below)
- For hearing names said out loud: Windows 10 or 11 with a voice for the language (see
  [Hearing the name](#hearing-the-name))
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

In a world, run `/langmod test`, then say something in the language you are learning (Mandarin
until you choose another one). Chat shows what Whisper heard, its pinyin or romanization and how
long it took. If nothing is detected, `/langmod mics` lists the microphones Java can
see; pick one in the settings ("Microphone").

### Using it in your normal Minecraft

Take the jar from the [latest release](https://github.com/DylanTopic/MinecraftLanguageMod/releases/latest),
or build it with `.\gradlew.bat build` (it ends up in `build\libs\langmod-0.1.0.jar`). Copy it and
[Fabric API](https://modrinth.com/mod/fabric-api) into the `mods` folder of a Fabric 26.1.2
installation, and put the model in that installation's `config\langmod\models` folder (for the
default launcher: `%APPDATA%\.minecraft\config\langmod\models`).

## Commands

| Command | What it does |
|---|---|
| `/langmod test` | Records one utterance and prints what Whisper heard (text, pinyin or romanization, timing). |
| `/langmod mics` | Lists the microphones. |
| `/langmod language` | Opens the menu for choosing the language to learn (also **Practice Language** in the game menu). |
| `/langmod items` | Opens the items list, where items can be turned off (also **Items** in the game menu). |
| `/langmod voices` | Looks for the voices installed in Windows again and lists the one that says each language's names. |
| `/langmod list [page]` | Learned items in the current language, with their name, pronunciation and English name. |
| `/langmod review` | The items you failed most often in the current language, with failure and wrong-answer counts. |
| `/langmod reset` | Forgets the learned items of the current language, so they are challenged again. |
| `/langmod reset everything` | Also forgets that language's mistake history used by `/langmod review`. |
| `/langmod config` | Opens the settings screen. |
| `/langmod debug` | Toggles the debug overlay (also **F9**). |
| `/langmod reload` | Re-reads `langmod.json` and the name override files, reloads the speech model if its settings changed. |

The debug overlay shows the raw Whisper output, the cleaned-up text with its pinyin or
romanization, the words or syllables that were compared, the expected name, the match result
(errors, allowed errors, mode) and the audio and transcription times.

## Settings

Open the settings with Mod Menu's "Configure" button, `/langmod config`, or a key (unbound by
default; set it under Options > Controls > Key Binds > LangMod, where "Hear the name again" (H) is
too). Every change is saved
immediately to `config/langmod/langmod.json`. Speech settings take effect when you close the
screen. The file can also be edited by hand (then run `/langmod reload`).

| Setting (`langmod.json` key) | Default | Meaning |
|---|---|---|
| Learning (`language`) | `zh_cn` | `zh_cn`, `es_mx`, `fr_ca`, `ko_kr`, `ru_ru`, `nl_nl`, `la_la`, `fil_ph`, `ja_jp`, `bs_ba`, `pt_br`, `vi_vn`, `de_de` or `en_us`, see [Choosing a language](#choosing-a-language). |
| Challenges (`enabled`) | on | Master switch. Turning it off gives held items back. |
| Pronunciation (`showPronunciation`) | on | The pinyin, romanization or reading under names and under what was heard; also the **Show pronunciation under names** checkbox in the Practice Language menu. |
| English name (`showEnglish`) | on | Show the item's name in your game language (usually English) in the popup; left out when it would only repeat the name. |
| Attempts (`maxAttempts`) | 3 | Wrong answers before the item vanishes. |
| Time limit (`timerSeconds`) | 20 | Seconds to answer (paused while speaking and checking). |
| Matching (`matchMode`) | strict | `strict`, `toneless` or `lenient`, see below. |
| Long names from (`nameLengthLimits`) | per language, see above | For the current language: names longer than this (characters in Mandarin, words otherwise) are "long". |
| Long names (`longNameModes`) | per language, see above | For the current language: `lenient` (one wrong syllable or word allowed) or `skip` (never challenged). |
| Prompt biasing (`promptBiasingByLanguage`) | on, except in Mandarin | For the current language: give Whisper the expected word as a hint (see below). |
| Say the name when wrong (`sayNameWhenWrong`) | on | Say the name out loud after a wrong answer and when an item is lost, see [Hearing the name](#hearing-the-name). |
| Voice (`voiceNames`) | automatic | For the current language: the Windows voice that says names (part of its name, e.g. `Kangkang`). |
| Voice speed (`voiceSpeed`) | 0.9 | 1.0 is the voice's normal speed. |
| Whisper model (`modelSize`) | small | `tiny`, `base`, `small`, `medium`, `large-v3-turbo`: loads `ggml-<size>.bin`. |
| Speech engine (`speechEngine`) | jni | `jni` (built in, CPU) or `server` (separate whisper.cpp program, GPU). |
| Use GPU (`useGpu`) | on | Only used by the `server` engine. |
| Whisper window (`audioContext`) | 512 = 10 s | Audio Whisper processes per answer, in 20 ms frames: `0` = its full 30 s window, `-1` = just the clip (fastest, much less accurate). See the measurements below. |
| CPU threads (`whisperThreads`) | 0 = auto | Auto uses half your cores (at most 8), so the game keeps running smoothly. |
| Microphone (`micDevice`) | system default | Part of a device name from `/langmod mics`. |
| Speech threshold (`vadThresholdDb`) | 12 | How much louder than background noise (dB) counts as speech. |
| Tone colors (`toneColors`) | red, green, blue, purple, grey | Mandarin tones 1-4 and neutral, as `#RRGGBB` (shown in the settings when learning Mandarin). |
| Name size (`hanziScale`) | 3 | Size of the name in the popup; long names are shrunk to fit. |
| Debug overlay (`debugOverlay`) | off | See above. |

Only in the file:

| Key | Default | Meaning |
|---|---|---|
| `disabledItems` | none | Per language, the ids of items turned off in the [items list](#items-list), e.g. `"es_mx": ["minecraft:dirt"]`. |
| `vadSilenceMs` | 800 | Silence that ends a recording. |
| `vadMaxClipMs` | 5000 | Longest recording. |
| `vadMinSpeechMs` | 200 | Shorter sounds (clicks, bumps) are ignored. |
| `whisperPrompts` | "Minecraft items:" in each language | Generic hint for Whisper per language (`我的世界物品：`, ...), used when prompt biasing is off. It never contains the answer. `""` = no prompt. |
| `transcriptionTimeoutSeconds` | 10 | Slower transcriptions are abandoned (not counted as an attempt). |
| `whisperServerPath` / `whisperServerPort` | "" / 8178 | For the `server` engine, see below. |
| `pinyinScale` | 1.5 | Size of the pinyin or romanization in the popup. |

## How answers are checked

Whisper is told which language you are learning, so it writes down what you said in that language
(it never translates). Phrases Whisper invents for silence or noise, such as subtitle credits
("Subtítulos por la comunidad de Amara.org", "시청해 주셔서 감사합니다"), count as nothing heard.
Then the text is compared with the item's name:

### Mandarin

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

### Spanish, French, Russian, Dutch, Latin, Tagalog, Bosnian, Portuguese, German and English

Names are compared word by word, and these differences are ignored:

- Capital letters, punctuation and accents: "azucar" counts for Azúcar, "baton" for Bâton.
- An article in front of the name: "la manzana" or "una manzana" for Manzana, "une pomme",
  "l'arc" or "de la laine verte" for Pomme, Arc and Laine verte, "de appel", "ang mansanas",
  "a maçã". Saying the name without one is fine too.
- Where the words are split. Whisper sometimes runs words together or splits one, which counts
  when the letters, or how they sound, are the same: "Zelenavuna" for Zelena Vuna, "Gold Block"
  for Goldblock, "pa na" for Pana.
- Words spelled differently but pronounced the same. Whisper writes words, and when several
  spellings sound alike it picks one, so the mod compares how words sound:
  - in Spanish (as spoken in Mexico): b/v, s/z/soft c, ll/y, a silent h ("mansana" counts), and the
    soft d between vowels, which Whisper sometimes writes as "th" ("bithrio" for Vidrio);
  - in French: silent endings, é/è/ê/ai and the like ("vert" or "vers" for Verre, "pomme doré"
    for Pomme dorée);
  - in Russian: ё written as е, and voiced consonants at the end of a word ("хлеп" for Хлеб);
  - in Dutch: ij/ei, ou/au, a final d or t, g/ch, and a doubled vowel ("pompoeen" for Pompoen);
  - in Latin, which is said the classical or the church way: ae/oe/e, v/b, j/i, y/i, ph/f, c/k/ch
    and a silent h ("bitrum" for Vitrum, "kukurbita" for Cucurbita);
  - in Tagalog: e/i and o/u, "ja" for "diya" ("jamanteng piko"), and loan letters said the
    Tagalog way (c = k, f = p, v = b);
  - in Bosnian: č/ć, dž/đ, and a t dropped between n and s ("dijamanski kramp");
  - in Brazilian Portuguese: ç/ss/s/z, a final e/o said i/u, but nasal vowels count (Massa is not
    Maçã);
  - in German: ß/ss, ä/e, eu/äu, v/f, a final b/d/g said p/t/k ("Brod" for Brot) and an h that only
    makes a vowel long; umlauts count, though (Äpfel, the plural, is not Apfel);
  - in English: the British spellings of names Minecraft spells the American way ("light grey wool"
    for Light Gray Wool, colour, armour).

A missing, extra or different word is wrong: "manzanas" for Manzana, "laine vert" for Laine verte
(the t of verte is pronounced), "pan" for Pain (a different nasal vowel), or "яблоки" for Яблоко.
The popup underlines the words of the name you did not say correctly, and shows the Russian name
and what Whisper heard in Latin letters too. Matching modes: `strict` and `toneless` are the same
here; with `lenient`, names of 4 or more words may have one wrong word. Long names are skipped by
default (see the table in [Choosing a language](#choosing-a-language)).

### Vietnamese

Names are compared syllable by syllable, and the tone of each syllable is checked, as in Mandarin:
"Tào" for Táo has the right letters but the wrong tone, so it is underlined in orange and does not
count (in `toneless` mode it does). Where the tone mark sits ("thuỷ" or "thủy") and i or y ("bánh
mỳ" or "bánh mì") do not matter, and a classifier in front is fine ("quả táo", "cái bánh mì").
Final consonants that many speakers, and Whisper, do not tell apart count as the same: -t, -c, -ch
and -p ("Đấc" for Đất), and -n, -ng and -nh.

### Japanese

Names are compared character by character, with katakana and hiragana counting as the same, so
"かぼちゃ" counts for カボチャ. For a name written with kanji, an answer in kana counts when it
matches the name's reading: "たいまつ" or "タイマツ" for 松明. A long vowel mark or a small っ that
Whisper leaves out or adds there is not counted ("てついんごと" for 鉄インゴット). A different word
is wrong ("リング" for リンゴ, 赤色の羊毛 for 緑色の羊毛), and so is a word written with other kanji,
because the mod only knows the readings of the item names. Under the name the popup shows its
reading (for names with kanji) and romaji: 松明 たいまつ taimatsu.

### Korean

Names are compared syllable by syllable (one Hangul block at a time), ignoring spaces and
punctuation, so "초록색양털" counts for 초록색 양털. Syllables that sound the same count as the same,
because Whisper has to pick one spelling: the vowels ㅐ/ㅔ, ㅒ/ㅖ and ㅙ/ㅚ/ㅞ, and final consonants
that are pronounced alike (낫, 낮, 낯 and 낟 all end in the same t sound). A missing, extra or
different syllable is wrong: "빵이요" ("bread, please") does not count for 빵, and "사가" does not
count for 사과. The popup underlines the syllables you did not say correctly, and what Whisper heard
is shown with its romanization.

The romanization under the name is the official Revised Romanization, written the way the name is
pronounced: 사과 sagwa, 독립 dongnip, 같이 gachi, 마법이 부여된 책 mabeobi buyeodoen chaek. Matching
modes work as in Spanish and French, counting syllables. Names of more than 2 words are skipped by
default.

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
background thread, so the game never waits for Whisper. Whisper is always told the language you are
learning, so it never has to guess it from a one-word clip.
The microphone is only open while a challenge (or `/langmod test`) is listening; the red dot in the
popup shows when it is. Audio is recorded as 16 kHz mono and split into utterances by a simple
voice activity detector (it adapts to background noise; 0.8 s of silence ends a recording, 5 s
maximum).

Whisper gets these hints by default, chosen from the measurements below:

- A **10 second window** instead of Whisper's usual 30 seconds (it always processes a fixed window,
  even for a one-second word). This is about 4 times faster and was as accurate in Mandarin, where
  both were measured.
- In Spanish, French and Korean, **prompt biasing**: the expected name is given to Whisper as its
  initial prompt, so it hears that name much more readily. Without it, Whisper turned many correct
  answers into similar-sounding words (방 for 빵, 할 for 활, "Pan" for Pain, "Art" for Arc) and the
  mod rejected them. The price is that Whisper may also "hear" the name when you said something
  close to it (see the measurements).
- In Mandarin, a generic **initial prompt** instead, `我的世界物品：` ("Minecraft items:"). It does
  not contain the answer, but nudges Whisper towards item names: it got one or two more names right
  with every window size (for example 火把 "torch" instead of 活吧 with the 30 s window, and 铁定
  tiě dìng, which sounds like 铁锭, instead of 贴定 tiē dìng with the 10 s one). Prompt biasing fixes
  most false failures in Mandarin too (暂时 for 钻石, 念包 for 面包), but with the 10 s window it also
  accepted the wrong-tone sample (你吐 for 泥土), and tones are much of what you learn in Mandarin.
  With the full 30 s window it did not, so if you turn biasing on in Mandarin, also set "Whisper
  window" to 30 s.

"Prompt biasing" in the settings applies to the language you are learning. The generic prompts of
the other languages (`Objetos de Minecraft:`, `Objets de Minecraft :`, `마인크래프트 아이템:`, used
when biasing is off) did not help in the measurements: no change in French and Korean, one name
fewer in Spanish. An entry of `whisperPrompts` set to `""` turns that language's generic prompt off.

### Measured accuracy and speed

`.github/workflows/speech.yml` synthesises item names with neural voices and runs them through the
mod's real pipeline with the `small` model on GitHub's 2-core runners. In every language some of the
samples are wrong answers, which must be rejected.

**Mandarin:** 16 samples (13 correct item names spoken by two voices, plus a wrong word, a similar
word and a wrong tone):

| Prompt | Window | Correct names accepted (strict) | Wrong answers rejected | Time per answer (2 threads) |
|---|---|---|---|---|
| none | 30 s | 7 of 13 | 3 of 3 | 12.3 s |
| none | 15 s | 7 of 13 | 3 of 3 | 4.8 s |
| none | 10 s | 7 of 13 | 3 of 3 | 3.5 s |
| none | clip only | 4 of 13 | 3 of 3 | 1.3 s |
| "Minecraft items:" | 30 s | 9 of 13 | 3 of 3 | 12.4 s |
| "Minecraft items:" | 15 s | 8 of 13 | 3 of 3 | 4.9 s |
| **"Minecraft items:" (default)** | **10 s** | **8 of 13** | **3 of 3** | **3.1 s** |
| "Mandarin sentences:" (以下是普通话的句子。) | 10 s | 8 of 13 | 3 of 3 | 3.1 s |
| expected word (biasing) | 30 s | 12 of 13 | 3 of 3 | 12.4 s |
| expected word (biasing) | 10 s | 10 of 13 | 2 of 3 | 3.0 s |

On Windows (also 2 threads) the default took 3.8 s per answer instead of 12.6 s with the full
window, with the same transcriptions as on Linux.

The misses are words Whisper turned into a more common word that sounds almost the same: 钻石
zuàn shí became 暂时 zàn shí, 面包 miàn bāo became 念包, and the one-syllable 弓 became the English
"GO!" (even with biasing). Homophones are accepted through the pinyin comparison: 目棍 for 木棍 and
铁钉 for 铁锭 passed.

**Spanish, French and Korean:** 15 Spanish samples spoken by two Mexican voices, 15 French ones by
two Canadian voices and 14 Korean ones, each language with 3 wrong answers: a wrong word, a
similar-sounding word (mañana for manzana, pompe for pomme, 사고 for 사과) and a wrong colour or
gender ("lana roja", "laine vert", 빨간색 양털). With the 10 s window, about 3 s per answer:

| Language | Prompt | Correct names accepted | Wrong answers rejected |
|---|---|---|---|
| Spanish | none | 9 of 12 | 3 of 3 |
| Spanish | "Minecraft items:" | 8 of 12 | 3 of 3 |
| **Spanish** | **expected word (biasing, default)** | **11 of 12** | **2 of 3** |
| French | none | 5 of 12 | 3 of 3 |
| French | "Minecraft items:" | 5 of 12 | 3 of 3 |
| **French** | **expected word (biasing, default)** | **8 of 12** | **3 of 3** |
| Korean | none | 3 of 11 | 3 of 3 |
| Korean | "Minecraft items:" | 3 of 11 | 3 of 3 |
| **Korean** | **expected word (biasing, default)** | **8 of 11** | **3 of 3** |

Without biasing, Whisper often wrote a similar-sounding word instead of the name: 방 for 빵, 할 for
활, 차과 for 사과, 햇불 for 횃불; "Ben" or "Pan" for Pain, "Art" for Arc, "Black Dark" for Bloc d'or;
"ban" for Pan, "Limbote" for Lingote. With biasing, the misses left were mostly short words (흙
heard as 흠, 호박 as 호바, 활 as 할, Pan as "Ban" or "Ben", Arc as "Art", Torche as "Tochche") and
"une pomme", where Whisper wrote "In" for the article. The one wrong answer accepted was "mañana",
which Whisper heard as the expected "manzana".

**The languages added later:** 15 or 16 samples per language, read by a male and a female voice
from the country (Latin by two Italian voices, as there is no Latin one: the church
pronunciation), with 3 wrong answers each: a wrong word, a similar word (Яблоки for Яблоко, Appels,
Malus, Luya for Lupa, リング for リンゴ, Jabuke, Massa for Maçã, Äpfel for Apfel, Apples for Apple; in
Vietnamese the wrong tone, Tào for Táo) and a wrong colour. Every wrong answer was rejected, in every language and
with every prompt. Correct names accepted, with the 10 s window (2 to 3 s per answer):

| Language | No prompt | "Minecraft items:" | **Expected word (biasing, default)** |
|---|---|---|---|
| Russian | 10 of 12 | 9 of 12 | **12 of 12** |
| Dutch | 7 of 13 | 7 of 13 | **10 of 13** |
| Latin | 6 of 12 | 9 of 12 | **10 of 12** |
| Tagalog | 9 of 13 | 10 of 13 | **10 of 13** |
| Japanese | 7 of 12 | 8 of 12 | **12 of 12** |
| Bosnian | 5 of 12 | 6 of 12 | **8 of 12** |
| Portuguese (Brazil) | 6 of 13 | 6 of 13 | **11 of 13** |
| Vietnamese | 4 of 13 | 5 of 13 | **8 of 13** |
| German | 8 of 13 | 8 of 13 | **12 of 13** |
| English | 12 of 13 | 12 of 13 | **13 of 13** |

Without biasing, Whisper often wrote something that sounds similar: "ḍmose" for Humus and
"Australis" for Cucurbita, 意味 (imi) for 弓 (yumi), "Massa" for Maçã and "Bom" for Pão, "FACO" for
Fakkel, "Kobis" for Kürbis, Đảo for Táo, "Class" for Glass. With biasing, the misses left were mostly a word from
another language ("Apple" for Appel, "Look" for Luk, "Block" for Bloke), a dropped or changed letter
("Zemija" for Zemlja, "Diamanski" for Dijamantski, "Humuse", "Linggot" for Linggote, "Boden" for
Bogen), and in Vietnamese short names written with another tone or vowel ("Tàu" for Táo, "Gây"
for Gậy, "Đu" for Đuốc). Vietnamese is the hardest: tones are checked, and Whisper is unsure of them
in one-syllable words. The `medium` model did no better there in a separate test (3 instead of 4
of 8 names with biasing) and took about four times as long.

The samples are synthesised again on every run, so the counts can differ by one between runs. The
test can be rerun with other settings from the Actions tab (workflow "speech", see the variants in
`src/test/java/.../speech/SpeechCheck.java`).

Before a language is added, the workflow "survey" checks whether Whisper hears it well enough: it
has the names in `.github/survey/survey.tsv` read by neural voices and prints what Whisper wrote
with and without the name as a hint, and whether wrong answers would slip through. Run it from the
Actions tab; `MODEL` in `.github/workflows/survey.yml` picks the Whisper model.

Synthesised speech is cleaner than a real microphone and a learner's accent, so expect somewhat
worse results in the game. A desktop CPU is much faster than these 2-core runners: Whisper's time
scales with the threads it gets (`whisperThreads`, auto = half your cores).

## Names and pinyin data

No name is translated by hand: they are Minecraft's own translations from its language files
(`zh_cn.json`, `es_mx.json`, `ja_jp.json`, ...), looked up by translation key
(`item.minecraft.apple` → 苹果, Manzana, Pomme, 사과, Яблоко, リンゴ). All 1715 item and block names
of 26.1.2 exist in every one of these languages.

### Every language but Mandarin

`tools/generate_names.py` copies the item and block names into
`src/main/resources/assets/langmod/names/<language>.json`, one per line:

```json
"item.minecraft.apple": "Manzana",
```

Your own fixes go in `config/langmod/names_<language>.json` (e.g. `names_es_mx.json`, created on
first start, same format); run `/langmod reload` after editing. In the languages added later
(Russian, Dutch, Latin, Tagalog, Japanese, Bosnian, Portuguese, Vietnamese, German), names the
translation left in English are left out. The Korean and Russian romanizations are worked out by the mod itself, so they need no
data.

Japanese also needs the reading of every name: `tools/generate_japanese.py` works it out with
[SudachiPy](https://github.com/WorksApplications/SudachiPy) and writes
`src/main/resources/assets/langmod/names/ja_jp_kana.json` (name → reading in hiragana). Every name
with kanji is listed with its reading in `tools/japanese_review.md`; corrections that should survive
regeneration go in `tools/japanese_fixes.json`.

To regenerate (Python 3.10+; the Japanese readings need `pip install -r tools/requirements.txt`):

```powershell
python tools/generate_names.py
python tools/generate_japanese.py
```

By default the script downloads the language files for 26.1.2 from Mojang's servers (cached in
`tools/.cache`); `--lang-dir` reads local copies instead. The bundled files were generated from the
copies mirrored in
[InventivetalentDev/minecraft-assets](https://github.com/InventivetalentDev/minecraft-assets/tree/26.1.2)
(the script prints each file's sha1; for example `ec1286e287ca210b507bf986875f49b4ac479d43` for
`es_mx.json` and `82ae51a68e114943fd95cc870643317dc02fe5e4` for `ja_jp.json`), for the same reason
as the pinyin below.

### Mandarin pinyin

`tools/generate_pinyin.py` turns the names from `zh_cn.json` into
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

1. **Setup:** the game reaches the title screen; Mods (Mod Menu) lists "LangMod".
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
6. **Languages** (added afterwards): `/langmod language` opens the language menu; choose Español
   (México) and pick up an apple. The popup shows "Manzana"; say "la manzana": correct, and
   `/langmod list` now lists only what you learned in Spanish. Choose 한국어 and pick up bread: the
   popup shows 빵 with "ppang" under it. Pick up something new and switch language before
   answering: the item is given back.
7. **Game menu and items list** (added afterwards): press Esc. Items and Practice Language are next
   to the vanilla buttons. Items lists everything in the current language; search for "carrot",
   click it so its square turns red, close the menus and pick up a carrot: it goes straight into
   your inventory.
8. **Hearing the name** (added afterwards, needs a Windows voice for the language): `/langmod voices`
   lists it. Pick up something new and say a wrong word: the popup shows "Listen...", the name is
   said and the countdown stands still meanwhile. Press H to hear it again.

Automated tests:

```powershell
.\gradlew.bat test               # unit tests: names, matching in every language, pinyin, romanization, text conversion, audio, whisper-server protocol
.\gradlew.bat runClientGameTest  # starts the real game with a scripted speech engine, plays through all of the above
```

The game test writes screenshots to `build\run\clientGameTest\screenshots`. Both run on every push
in GitHub Actions (`.github/workflows/build.yml`), which also uploads the built jar. The game test
uses silent scripted voices; `.github/workflows/voices.yml` checks the real Windows voices on a
Windows runner (VoiceCheck: lists them, has each language's voice say its word for apple and checks
the WAV files; the runner only has English voices, and others cannot be added to it).

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
- **"Transcription took longer than 10 s"** (not counted as an attempt): use a smaller model
  (`base`), give Whisper more threads, use the `server` engine with a GPU, or as a last resort set
  "Whisper window" to "Clip only".
- **Correct answers are rejected too often**: check that "Prompt biasing" is on. In Mandarin, turn it
  on together with the 30 s window, or use `toneless` matching. A bigger model (`medium`) is more
  accurate but slower. The debug overlay (F9) shows what Whisper wrote.
- **A wrong name or reading in the popup**: add the item to `config/langmod/pinyin_overrides.json`
  (Mandarin) or `config/langmod/names_<language>.json` (Spanish, French, Korean).
- **Names are not said out loud**: run `/langmod voices`. If your language shows "none", add its
  voice in Windows (see [Hearing the name](#hearing-the-name)) and run `/langmod voices` again. Check
  Minecraft's Voice/Speech volume, and that "Say the name when wrong" is on.
- **"You switched to ..., so your items were given back"**: changing the language during a
  challenge cancels it without counting a failure; pick the item up again.
- Logs: `run\logs\latest.log` (development client) or `.minecraft\logs\latest.log`.

## Project layout

```
src/main/java/.../langmod/
  LangMod.java, LangModConfig.java      server entrypoint, settings file
  challenge/                            pickup interception and the challenge rules (server)
  command/                              /langmod
  core/                                 plain Java, unit tested: pinyin, matching, audio, text, speech engines
  core/lang/                            the languages: names, answer checking, Korean romanization
  core/tts/                             the Windows voices (through PowerShell) and which voice says each language
  data/                                 per-player saved data (Fabric data attachment)
  mixin/                                ItemEntity pickup hook, player death hook
  net/                                  packets between server and client
src/client/java/.../langmod/client/     microphone, speech service, popup and overlays, settings screen, saying names
src/gametest/                           client game test (runClientGameTest)
src/test/                               unit tests and the SpeechCheck tool used by CI
tools/                                  name, pinyin and Japanese reading generators, fixes and review lists
```

## Versions

From the official Fabric 26.1.2 template (<https://fabricmc.net/develop>): Minecraft 26.1.2, Fabric
Loader 0.20.1, Loom 1.19-SNAPSHOT (`net.fabricmc.fabric-loom`, the non-remapping plugin for
unobfuscated Minecraft, so the code uses Mojang's names), Fabric API 0.155.3+26.1.2, Gradle 9.8.2,
Java 25. Mod Menu 18.0.2, whisper-jni 1.7.1.

## Releasing

Raise `version` in `gradle.properties` and push. Then, in the repository's **Actions** tab, choose
**release** and **Run workflow**: it builds the mod and publishes a GitHub Release `v<version>` with
the jar, which is where [INSTALL.md](INSTALL.md) sends players. It refuses a version that was
already released.

## License

LangMod's code is under the [MIT License](LICENSE). Third-party components and data keep their own
licences, listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); the item names are Minecraft's
own translations.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
