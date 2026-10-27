# PocketWave

Client-only Fabric mod for **Minecraft 1.21.11** that plays audio from YouTube, YouTube Music, and other [yt-dlp](https://github.com/yt-dlp/yt-dlp)-supported sites, plus local files. No account login is required. Spotify is not supported (DRM / login).

Only you hear the music — it works in singleplayer and on multiplayer servers without a server-side mod.

## Requirements

- Minecraft **1.21.11**
- Fabric Loader **0.19.5+**
- [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11
- Java **21+** (for building)
- Internet on first play if `ffmpeg` is not already installed (one-time ~20–30MB download)

**You do not need to install yt-dlp yourself.** A copy for Windows, macOS, and Linux is bundled in the mod (kept under 100MB total). If `yt-dlp` / `ffmpeg` are already on your `PATH`, those are used instead.

## Build

```bash
./gradlew build
```

The first build downloads yt-dlp binaries (network required). The mod JAR is written to `build/libs/pocketwave-1.1.0.jar` and must stay under **100MB**.

## Commands

All commands are client-side:

| Command | Description |
| --- | --- |
| `/music play <url\|search terms>` | Fetch and play (URL or YouTube search) |
| `/music local <path>` | Play a local file (absolute or relative to the game directory) |
| `/music pause` / `/music resume` | Pause or resume |
| `/music stop` | Stop playback |
| `/music loop` | Toggle looping the current song |
| `/music queue <url\|search>` | Queue a song to play next (plays now if idle) |
| `/music queue` | List the queue |
| `/music queue clear` | Clear the queue |
| `/music volup` / `/music voldown` | Volume ±10% |
| `/music volume <0-100>` | Set volume |
| `/music now` | Show current track / status |
| `/music playlist` | Open the playlist GUI |
| `/music playlist <name>` | Play a saved playlist (example: `/music playlist Gaming Playlist`) |
| `/music settings` | Open settings (default volume, volume step, HUD) |

Examples:

```
/music play https://www.youtube.com/watch?v=dQw4w9WgXcQ
/music play never gonna give you up
/music local ~/Music/song.mp3
/music playlist Gaming Playlist
```

## Keybinds

Open **Options → Controls → PocketWave**. All are **unbound by default**:

- Pause/Resume
- Volume Up
- Volume Down
- Stop
- Toggle Loop
- Show Queue (prints the queue in chat)
- Open Playlists

## Playlists

`/music playlist` or the Open Playlists keybind opens a GUI where you can create playlists, click into them, add songs (search/URL or the song currently playing), reorder them with **Move Up** / **Move Down**, remove songs, and toggle **Loop playlist**. Playlists are saved to `<game dir>/pocketwave/playlists.json` and stay across launches.

`/music playlist Gaming Playlist` starts that playlist immediately. Playlist loop is separate from `/music loop` (which repeats only the current song). Starting a playlist turns song-loop off so tracks can advance.

## Settings

`/music settings` opens a small menu for **default volume** (remembered the next time you play a song, and also saved when you use volume up/down), **how much** `/music volup` and `/music voldown` change, and whether the now-playing HUD is shown. Saved to `<game dir>/pocketwave/settings.json`.

## HUD

While a track is playing or paused (or loading), a small widget appears in the **top-left**: a short ellipsized title, a vanilla XP-styled volume bar (rounded ends), and a status line for paused / loop / queue count. It hides when playback ends (or with F1), and auto-advances through the queue.

## How it works

1. Bundled (or system) `yt-dlp` downloads the best audio stream (no cookies / login by default).
2. `ffmpeg` converts it to a temporary WAV under `.minecraft/pocketwave/cache/` (deleted when the track finishes or is discarded — not kept permanently).
3. Queued songs **preload** in the background so the next track can start with little/no gap.
4. If `ffmpeg` is not on your PATH, the mod downloads it once into `<game dir>/pocketwave/bin/` (for vanilla that’s `.minecraft/pocketwave/bin/ffmpeg`; launcher instances use their own instance folder). That copy is reused after the first successful download.
5. Java Sound plays the WAV (separate from Minecraft’s volume sliders).

## Troubleshooting

**“YouTube blocked this request…”**  
YouTube’s bot check rejected the download. Try again later, or use another link / a local file. This mod does not require Google login.

**ffmpeg download failed**  
Allow Minecraft network access, or install ffmpeg yourself (`brew install ffmpeg` on macOS) and restart the game.

**Spotify links**  
Not supported. Use YouTube, YouTube Music, SoundCloud, Bandcamp, direct audio URLs, or `/music local`.
