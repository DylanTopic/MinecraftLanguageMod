# Installing LangMod

LangMod is a Minecraft mod for learning a language: the first time you pick up an item, you have to
say its name out loud in that language before you get it. Get it wrong and a voice can say it for
you. It works offline; nothing you say is sent anywhere.

Setting it up takes about 15 minutes, most of it waiting for downloads.

**You need:** Windows 10 or 11, Minecraft Java Edition, a microphone, and about 1 GB of free disk
space. These steps use the free CurseForge app. If you use the normal Minecraft Launcher instead,
see [Without CurseForge](#without-curseforge) at the end.

## 1. Make a Fabric 26.1.2 profile in CurseForge

1. Install the CurseForge app from <https://www.curseforge.com/download/app>, open it and pick
   **Minecraft**.
2. Click **Create Custom Profile**. Name it `LangMod`, choose game version **26.1.2** and mod loader
   **Fabric** (the version it suggests is fine), then click **Create**.
3. Open the new profile, click **Add Content** (or **Browse mods**), search for **Fabric API** and
   install it.
4. Optional: also install **Mod Menu**. It adds a "Mods" button on the title screen, where LangMod's
   settings can be opened.

## 2. Download the LangMod file

The mod is one file, `langmod-0.1.0.jar` (a later version has a higher number).

1. Open the latest release: <https://github.com/DylanTopic/MinecraftLanguageMod/releases/latest>
2. Under **Assets**, click **`langmod-0.1.0.jar`** to download it. (The other entries there are the
   source code; you don't need them.)

## 3. Put it in your profile

1. In CurseForge, on the LangMod profile, click the **⋯** button next to **Play** and choose
   **Open Folder**. A folder opens, something like
   `C:\Users\<you>\curseforge\minecraft\Instances\LangMod`.
2. Open the **mods** folder (Fabric API is already in it) and put `langmod-0.1.0.jar` next to it.

Keep that folder open; you need it again in the next step.

## 4. Download the speech model

The mod understands you with Whisper, a speech recognition program that runs on your computer. It
needs a 466 MB model file that isn't included in the mod.

1. Download it: <https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin>
2. In the profile folder from step 3, go into **config**, then **langmod**, then **models**. Create
   any of these folders that isn't there yet (right-click → New → Folder).
3. Move `ggml-small.bin` into `models`. It must end up here, with exactly this file name:

   ```
   C:\Users\<you>\curseforge\minecraft\Instances\LangMod\config\langmod\models\ggml-small.bin
   ```

## 5. First start

1. Click **Play**. The first start takes a minute or two.
2. Create a world. Creative mode is easiest for trying it out: drop items with Q and walk over them.
3. Wait for the chat message **"Speech recognition ready"** (a few seconds after joining).
4. Check your microphone: type `/langmod test`, press Enter and say something. The chat shows what
   it heard.
5. Choose the language you want to learn: press **Esc**, then **Practice Language**. Mandarin
   Chinese is selected at first. You can also pick Spanish, French, Korean, Russian, Dutch, Latin,
   Tagalog, Japanese, Bosnian, Portuguese, Vietnamese, German or English.

### How to play

- Pick up an item you've never picked up. A popup shows its name (with pronunciation where it
  helps) and a timer. Say the name out loud.
- **Right:** you get the item, and that item is learned for good.
- **Wrong:** the popup shows what it heard, and the name is said out loud if you have a voice for
  your language (step 6). Press **H** to hear it again. After 3 wrong answers, or when time runs
  out, the item disappears. You can still learn it next time you find one.
- **Villagers** (when learning Korean or English): right-click one and choose **Conversate** to talk
  with it. Press **Speak** and say something, like "What is your job?" (직업이 뭐예요?), or type it;
  **Phrases** has more to try. **Trade** opens the normal trading screen (so does sneaking and
  right-clicking).
- **Esc → Items** lists every item in your language. Click an item to turn it off if you never want
  to be asked about it.
- Settings: `/langmod config`, or Mod Menu's Configure button. Every command is listed in the
  [README](README.md#commands).

## 6. Optional: hear names said out loud

When you get a name wrong, a Windows voice can say it correctly. English Windows usually only has
English voices, so add the one for your language:

1. Windows **Settings → Time & language → Speech**, then under **Manage voices** click **Add voices**.
2. Install the voice from this table:

   | Learning | Voice to add |
   |---|---|
   | Mandarin Chinese | Chinese (Simplified, China) |
   | Spanish | Spanish (Mexico) |
   | French | French (Canada) |
   | Korean | Korean |
   | Russian | Russian |
   | Dutch | Dutch (Netherlands) |
   | Latin | Italian (there is no Latin voice) |
   | Tagalog | none: Windows has no voice for it |
   | Japanese | Japanese |
   | Bosnian | Croatian (same sounds and spelling) |
   | Portuguese | Portuguese (Brazil) |
   | Vietnamese | Vietnamese |
   | German | German (Germany) |
   | English | none needed: Windows comes with English voices |

3. In the game, type `/langmod voices`. It lists the voices it found and which one each language
   uses. The volume is Minecraft's **Music & Sounds → Voice/Speech** slider.

## If something doesn't work

- **"Whisper model not found"**: check that the file is called exactly `ggml-small.bin` and is in
  `config\langmod\models` inside the profile folder, then restart the game.
- **"Could not load the whisper.cpp native library"**: install Microsoft's Visual C++ Redistributable
  (<https://aka.ms/vs/17/release/vc_redist.x64.exe>) and restart. Very old or budget processors (no
  AVX2) can't run it.
- **It never hears you**: in Windows **Settings → Privacy & security → Microphone**, turn on "Let
  desktop apps access your microphone". In the game, `/langmod mics` lists microphones; choose yours
  under "Microphone" in `/langmod config`.
- **Background noise starts recordings** (or quiet speech doesn't): change "Speech threshold" in
  `/langmod config`, higher for a noisy room, lower if you speak quietly.
- **"Transcription took longer than 10 s"**: your computer is on the slow side. Download
  `ggml-base.bin` from the same place as the model
  (<https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin>), put it next to
  `ggml-small.bin`, and choose "base" under "Whisper model" in `/langmod config`.
- **Names aren't said out loud**: see step 6 and run `/langmod voices`.
- **The game won't start**: check that the profile is Minecraft 26.1.2 with Fabric and that Fabric API
  is in the mods folder.

## Updating

Download the newest jar from the [latest release](https://github.com/DylanTopic/MinecraftLanguageMod/releases/latest),
delete the old `langmod-...jar` from the mods folder (two versions at once won't start) and put the
new one there. Your progress is kept.

## Without CurseForge

With the normal Minecraft Launcher:

1. Download the Fabric installer from <https://fabricmc.net/use/installer/>, run it, keep **Client**
   selected, pick Minecraft version **26.1.2** and click **Install**.
2. Download Fabric API for 26.1.2 from <https://modrinth.com/mod/fabric-api>.
3. Press Windows+R, type `%APPDATA%\.minecraft` and press Enter. Put Fabric API and
   `langmod-0.1.0.jar` in the `mods` folder there (create it if it's missing).
4. The model goes in `%APPDATA%\.minecraft\config\langmod\models\ggml-small.bin`.
5. In the launcher, choose the **fabric-loader-26.1.2** profile and click **Play**. Continue with step 5
   above.

## Building it yourself

Only for trying changes that aren't in a release yet.

1. Install Java 25: open PowerShell and run `winget install EclipseAdoptium.Temurin.25.JDK`, then
   open a new PowerShell window.
2. On the project's GitHub page, click **Code → Download ZIP** and unzip it.
3. In File Explorer, open the unzipped folders until you see `gradlew.bat`. Click the address bar,
   type `powershell` and press Enter: a PowerShell window opens in that folder. Run:

   ```powershell
   .\gradlew.bat build
   ```

4. The first build takes a few minutes. The mod ends up in that folder as
   `build\libs\langmod-0.1.0.jar`.
