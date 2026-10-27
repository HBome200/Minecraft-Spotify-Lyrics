# Spotify Lyrics

A client-side Fabric mod that displays synchronized Spotify lyrics directly on the Minecraft HUD.

Spotify Lyrics automatically detects the currently playing track, retrieves synchronized lyrics, and displays the previous, current, and next lines while you play.

## ✨ Features

- 🎵 Automatically detects the currently playing track
- 🎤 Fetches synchronized lyrics from LRCLIB
- ⏱️ Synchronizes lyrics with playback position
- ⬆️ Displays the previous lyric
- ⭐ Highlights the current lyric
- ⬇️ Displays the next lyric
- 🖥️ Renders directly on the Minecraft HUD
- 🐧 Supports Linux through MPRIS and D-Bus
- 🪟 Supports Windows through native media-session APIs using Java FFM

## 📦 Requirements

- Minecraft 26.2
- Fabric Loader
- Fabric API
- Spotify desktop application
- A supported operating system:
  - **Linux:** MPRIS-compatible Spotify playback through D-Bus
  - **Windows:** Windows media-session support

The mod is client-side, so it does not need to be installed on a multiplayer server.

## 🚀 Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Install Fabric API.
3. Download the latest release from the [GitHub Releases page](https://github.com/HBome200/Minecraft-Spotify-Lyrics/releases).
4. Place the downloaded JAR in your Minecraft `mods` folder.
5. Launch Minecraft using your Fabric installation.
6. Start Spotify and play a song.

Lyrics will be retrieved automatically when available.

## 🎧 How It Works

Spotify Lyrics retrieves playback information from the operating system and uses it to synchronize lyrics with the currently playing track.

### Linux

The mod communicates with Spotify through MPRIS over D-Bus to obtain playback information, including the current track and playback position.

### Windows: Native Media Integration

On Windows, Spotify Lyrics uses Java's **Foreign Function & Memory (FFM) API** to communicate with native Windows media-session APIs directly from Java.

Rather than relying on a separate helper application, the mod uses FFM to invoke native functions and work with native data structures from within the JVM.

The Windows backend retrieves playback information from the system's media session, including track metadata and playback position. This information is used to identify the currently playing song and synchronize its lyrics with playback.

The backend also estimates playback progress between reported position updates using a time-based reference, helping the lyrics remain synchronized even when updates aren't continuously provided.

**Why FFM?**
- No separate C# bridge process to install or maintain.
- Native Windows integration implemented directly in Java.
- Access to native functions and memory through the JDK's FFM API.

This approach keeps the Windows integration within the mod's Java codebase while avoiding an additional helper executable.

### Lyrics

When a track changes, the mod attempts to retrieve synchronized lyrics from LRCLIB, parses the timestamps, and determines which line corresponds to the current playback position.

The HUD displays the previous, current, and next lyric lines.

## 🛠️ Building From Source

### Requirements

- JDK compatible with the project's Gradle configuration
- Git

Clone the repository:

```bash
git clone https://github.com/HBome200/Minecraft-Spotify-Lyrics.git
cd Minecraft-Spotify-Lyrics
```

Build the mod:

**Linux**
```bash
./gradlew build
```

**Windows**
```powershell
.\gradlew.bat build
```

The compiled JAR will be located in:

```text
build/libs/
```

## ⚠️ Known Limitations

- Lyrics availability depends on LRCLIB and whether synchronized lyrics exist for a track.
- An internet connection is needed to retrieve lyrics that are not already available locally.
- Playback detection depends on the supported operating-system integration.
- Playback synchronization may vary depending on the media session and available timing information.

## ❤️ Support Development

Enjoying Spotify Lyrics? If you'd like to support continued development, you can leave an optional tip on Ko-fi.

☕ **[Support Spotify Lyrics on Ko-fi](https://ko-fi.com/alongoldberger)**

The mod will remain free and open source. Thank you for your support!

## 🐛 Bug Reports & Contributions

Found a bug or have an idea for an improvement?

- [Report an issue](https://github.com/HBome200/Minecraft-Spotify-Lyrics/issues)
- [View the source code](https://github.com/HBome200/Minecraft-Spotify-Lyrics)

Contributions, feedback, and suggestions are welcome.

## 📄 License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

Built by **HBome200 (alongold11)**.
