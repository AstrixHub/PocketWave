# **PocketWave 1.2.1! (Untested)**

## Updates

- Queue only preloads the next couple of songs (same idea as playlists), so filling the queue doesn’t hammer your disk/CPU
- Checks SHA-256 on bundled yt-dlp and downloaded ffmpeg before running them
- Only allows http/https links for playback (no odd schemes), and passes them to yt-dlp more safely

# **PocketWave 1.2.0!**

## Updates

- PocketWave now works on Minecraft 26.3 (Fabric Loader 0.20.1+)
- Everything from the 1.0.x versions is here — playlists, settings, queue, HUD, the works
- Asks before downloading ffmpeg (and you can turn that permission on/off in `/music settings`)
