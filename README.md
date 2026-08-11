# Minecraft Spotify Lyrics

A client-side Fabric Minecraft mod that displays synchronized Spotify lyrics directly in-game.

Lyrics are fetched automatically when the currently playing Spotify song changes and synchronized with the song's playback position.

## ✨ Features

- 🎵 Detects the currently playing Spotify song
- 🔄 Automatically detects song changes
- 🎤 Fetches synchronized lyrics
- ⏱️ Synchronizes lyrics with Spotify playback
- ⬆️ Displays the previous lyric
- ⭐ Highlights the current lyric
- ⬇️ Displays the next lyric
- 🖥️ Renders directly on the Minecraft HUD

## 📦 Requirements

- Minecraft **26.2**
- [Fabric Loader](https://fabricmc.net/)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Spotify
- Linux with D-Bus support

> Currently, the mod is developed and tested on Fedora Linux.

## 🚀 Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Install Fabric API.
3. Download the latest `.jar` from the [Releases](https://github.com/HBome200/Minecraft-Spotify-Lyrics/releases) page.
4. Place the `.jar` file into your Minecraft `mods` folder.
5. Start Minecraft with the Fabric profile.
6. Start playing a song on Spotify.
7. The lyrics will automatically appear in-game.

## 🎧 How It Works

The mod communicates with Spotify through D-Bus to obtain information about the currently playing track and its playback position.

When the song changes, the mod:

1. Detects the new artist and song.
2. Requests synchronized lyrics.
3. Parses the lyric timestamps.
4. Matches the current Spotify playback position to the appropriate lyric.
5. Displays the previous, current, and next lyrics on the Minecraft HUD.

## 🛠️ Building From Source

Clone the repository:

```bash
git clone https://github.com/HBome200/Minecraft-Spotify-Lyrics.git
cd Minecraft-Spotify-Lyrics
```

Build the mod:

```bash
./gradlew build
```

The compiled JAR will be located in:

```text
build/libs/
```

## ⚠️ Known Limitations

- Currently intended for Linux systems with D-Bus.
- Lyrics availability depends on the lyrics service.
- Some songs may not have synchronized lyrics available.
- The mod currently targets Minecraft 26.2.

## 📄 License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## ❤️ Credits

Built by [HBome200](https://github.com/HBome200).

If you find a bug or have an idea for a feature, feel free to open an issue!
