# PocketWave

Client-only Fabric mod for **Minecraft 26.2** that plays audio from YouTube, YouTube Music, and other [yt-dlp](https://github.com/yt-dlp/yt-dlp)-supported sites, plus local files. No account login is required. Spotify is not supported (DRM / login).

Only you hear the music — it works in singleplayer and on multiplayer servers without a server-side mod.

## Requirements

- Minecraft **26.2**
- Fabric Loader **0.19.5+**
- [Fabric API](https://modrinth.com/mod/fabric-api) for 26.2
- Java **25+** (for building)
- Internet on first play if `ffmpeg` is not already installed (one-time ~20–30MB download)

**You do not need to install yt-dlp yourself.** A copy for Windows, macOS, and Linux is bundled in the mod (kept under 100MB total). If `yt-dlp` / `ffmpeg` are already on your `PATH`, those are used instead.

## Build

```bash
./gradlew build
```

The first build downloads yt-dlp binaries (network required). The mod JAR is written to `build/libs/pocketwave-1.0.0.jar` and must stay under **100MB**.

## Commands

All commands are client-side:

| Command | Description |
| --- | --- |
| `/music play <url\|search terms>` | Fetch and play (URL or YouTube search) |
| `/music local <path>` | Play a local file (absolute or relative to the game directory) |
| `/music pause` / `/music resume` | Pause or resume |
| `/music stop` | Stop playback |
| `/music volup` / `/music voldown` | Volume ±10% |
| `/music volume <0-100>` | Set volume |
| `/music now` | Show current track / status |

Examples:

```
/music play https://www.youtube.com/watch?v=dQw4w9WgXcQ
/music play never gonna give you up
/music local ~/Music/song.mp3
```

## Keybinds

Open **Options → Controls → PocketWave**. All are **unbound by default**:

- Pause/Resume
- Volume Up
- Volume Down
- Stop

## HUD

While a track is playing or paused (or loading), a small widget appears in the **top-left**: song title and a thin volume bar. It stays out of the crosshair/hotbar area and hides with the rest of the HUD (F1).

## How it works

1. Bundled (or system) `yt-dlp` downloads the best audio stream (no cookies / login by default).
2. `ffmpeg` converts it to a temporary WAV under `.minecraft/pocketwave/cache/`.
3. If `ffmpeg` is not on your PATH, the mod downloads it once into `.minecraft/pocketwave/bin/` (bundling ffmpeg for every OS would exceed the 100MB jar limit).
4. Java Sound plays the WAV (separate from Minecraft’s volume sliders).

## Troubleshooting

**“YouTube blocked this request…”**  
YouTube’s bot check rejected the download. Try again later, or use another link / a local file. This mod does not require Google login.

**ffmpeg download failed**  
Allow Minecraft network access, or install ffmpeg yourself (`brew install ffmpeg` on macOS) and restart the game.

**Spotify links**  
Not supported. Use YouTube, YouTube Music, SoundCloud, Bandcamp, direct audio URLs, or `/music local`.
