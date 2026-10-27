# LangMod: Mandarin

A Fabric mod for Minecraft 26.1.2 that teaches Mandarin Chinese (Simplified) through exposure:
the first time you pick up an item type, you have to say its Chinese name out loud before you
get it. Speech recognition runs locally with [whisper.cpp](https://github.com/ggml-org/whisper.cpp).

> Work in progress. This README grows with each development stage.

## Requirements

- Minecraft Java Edition 26.1.2 with Fabric Loader 0.20.1+ and Fabric API
- **Java 25** (JDK) to build and run the development client

### Check / install Java 25 on Windows

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
`java -version` again. If you have several JDKs installed, point Gradle at JDK 25 for the current
window with:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.<version>-hotspot"
```

## Running the development client

```powershell
.\gradlew.bat runClient
```

The first run downloads Minecraft, Fabric and the other dependencies, which takes a few minutes.

Automated end-to-end checks (starts a real client, creates a world and takes screenshots into
`build/run/clientGameTest/screenshots`):

```powershell
.\gradlew.bat runClientGameTest
```

## Versions

Taken from the official Fabric 26.1.2 template (<https://fabricmc.net/develop>):
Minecraft 26.1.2, Fabric Loader 0.20.1, Loom 1.19-SNAPSHOT (`net.fabricmc.fabric-loom`, the
non-remapping plugin for unobfuscated Minecraft), Fabric API 0.155.3+26.1.2, Gradle 9.8.2.
