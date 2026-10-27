package com.pocketwave.client.playlist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.minecraft.client.Minecraft;

import com.pocketwave.PocketWave;

/**
 * Loads and saves playlists to {@code <game dir>/pocketwave/playlists.json}.
 */
public final class PlaylistStore {
	public static final PlaylistStore INSTANCE = new PlaylistStore();

	private static final int MAX_NAME_LENGTH = 40;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final List<Playlist> playlists = new ArrayList<>();
	private boolean loaded;

	private PlaylistStore() {
	}

	public synchronized List<Playlist> list() {
		ensureLoaded();
		return List.copyOf(playlists);
	}

	public synchronized Playlist find(String name) {
		ensureLoaded();
		if (name == null) {
			return null;
		}
		String needle = name.trim();
		if (needle.isEmpty()) {
			return null;
		}
		for (Playlist playlist : playlists) {
			if (playlist.name().equalsIgnoreCase(needle)) {
				return playlist;
			}
		}
		return null;
	}

	public synchronized Playlist create(String rawName) {
		ensureLoaded();
		String name = sanitizeName(rawName);
		if (name.isEmpty()) {
			return null;
		}
		if (find(name) != null) {
			return null;
		}
		Playlist created = new Playlist(name, false, List.of());
		playlists.add(created);
		save();
		return created;
	}

	public synchronized boolean delete(String name) {
		ensureLoaded();
		Playlist existing = find(name);
		if (existing == null) {
			return false;
		}
		playlists.removeIf(playlist -> playlist.name().equalsIgnoreCase(existing.name()));
		save();
		return true;
	}

	public synchronized boolean setLoop(String name, boolean loop) {
		ensureLoaded();
		int index = indexOf(name);
		if (index < 0) {
			return false;
		}
		playlists.set(index, playlists.get(index).withLoop(loop));
		save();
		return true;
	}

	public synchronized boolean addSong(String name, PlaylistSong song) {
		ensureLoaded();
		if (song == null || song.value().isBlank()) {
			return false;
		}
		int index = indexOf(name);
		if (index < 0) {
			return false;
		}
		Playlist playlist = playlists.get(index);
		List<PlaylistSong> songs = new ArrayList<>(playlist.songs());
		songs.add(song);
		playlists.set(index, playlist.withSongs(songs));
		save();
		return true;
	}

	public synchronized int moveSong(String name, int songIndex, int delta) {
		ensureLoaded();
		int index = indexOf(name);
		if (index < 0 || delta == 0) {
			return -1;
		}
		Playlist playlist = playlists.get(index);
		int target = songIndex + delta;
		if (songIndex < 0 || songIndex >= playlist.songs().size() || target < 0 || target >= playlist.songs().size()) {
			return -1;
		}
		List<PlaylistSong> songs = new ArrayList<>(playlist.songs());
		PlaylistSong moved = songs.remove(songIndex);
		songs.add(target, moved);
		playlists.set(index, playlist.withSongs(songs));
		save();
		return target;
	}

	public synchronized boolean removeSong(String name, int songIndex) {
		ensureLoaded();
		int index = indexOf(name);
		if (index < 0) {
			return false;
		}
		Playlist playlist = playlists.get(index);
		if (songIndex < 0 || songIndex >= playlist.songs().size()) {
			return false;
		}
		List<PlaylistSong> songs = new ArrayList<>(playlist.songs());
		songs.remove(songIndex);
		playlists.set(index, playlist.withSongs(songs));
		save();
		return true;
	}

	public synchronized void updateSongTitle(String name, int songIndex, String title) {
		ensureLoaded();
		int index = indexOf(name);
		if (index < 0 || title == null || title.isBlank()) {
			return;
		}
		Playlist playlist = playlists.get(index);
		if (songIndex < 0 || songIndex >= playlist.songs().size()) {
			return;
		}
		List<PlaylistSong> songs = new ArrayList<>(playlist.songs());
		PlaylistSong current = songs.get(songIndex);
		if (title.equals(current.displayTitle())) {
			return;
		}
		songs.set(songIndex, current.withTitle(title));
		playlists.set(index, playlist.withSongs(songs));
		save();
	}

	private int indexOf(String name) {
		if (name == null) {
			return -1;
		}
		String needle = name.trim();
		for (int i = 0; i < playlists.size(); i++) {
			if (playlists.get(i).name().equalsIgnoreCase(needle)) {
				return i;
			}
		}
		return -1;
	}

	static String sanitizeName(String rawName) {
		if (rawName == null) {
			return "";
		}
		String name = rawName.trim().replaceAll("[\\p{Cntrl}\\\\/]", "");
		if (name.length() > MAX_NAME_LENGTH) {
			name = name.substring(0, MAX_NAME_LENGTH).trim();
		}
		return name;
	}

	private void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		playlists.clear();
		Path file = filePath();
		if (file == null || !Files.isRegularFile(file)) {
			return;
		}
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			FileFormat format = GSON.fromJson(json, FileFormat.class);
			if (format == null || format.playlists == null) {
				return;
			}
			for (FilePlaylist raw : format.playlists) {
				Playlist parsed = fromFile(raw);
				if (parsed != null && find(parsed.name()) == null) {
					playlists.add(parsed);
				}
			}
		} catch (IOException | JsonSyntaxException e) {
			PocketWave.LOGGER.warn("Could not read playlists.json", e);
		}
	}

	private void save() {
		Path file = filePath();
		if (file == null) {
			return;
		}
		FileFormat format = new FileFormat();
		format.playlists = new ArrayList<>();
		for (Playlist playlist : playlists) {
			format.playlists.add(toFile(playlist));
		}
		Path tmp = file.resolveSibling("playlists.json.tmp");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(tmp, GSON.toJson(format), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			PocketWave.LOGGER.warn("Could not save playlists.json", e);
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
			}
		}
	}

	private static Path filePath() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.gameDirectory == null) {
			return null;
		}
		return client.gameDirectory.toPath().resolve(PocketWave.MOD_ID).resolve("playlists.json");
	}

	private static Playlist fromFile(FilePlaylist raw) {
		if (raw == null) {
			return null;
		}
		String name = sanitizeName(raw.name);
		if (name.isEmpty()) {
			return null;
		}
		List<PlaylistSong> songs = new ArrayList<>();
		if (raw.songs != null) {
			for (FileSong song : raw.songs) {
				PlaylistSong parsed = fromFile(song);
				if (parsed != null) {
					songs.add(parsed);
				}
			}
		}
		return new Playlist(name, raw.loop, songs);
	}

	private static PlaylistSong fromFile(FileSong raw) {
		if (raw == null || raw.value == null || raw.value.isBlank()) {
			return null;
		}
		PlaylistSong.Kind kind = PlaylistSong.Kind.QUERY;
		if (raw.kind != null && raw.kind.equalsIgnoreCase("LOCAL")) {
			kind = PlaylistSong.Kind.LOCAL;
		}
		return new PlaylistSong(kind, raw.value.trim(), raw.displayTitle);
	}

	private static FilePlaylist toFile(Playlist playlist) {
		FilePlaylist raw = new FilePlaylist();
		raw.name = playlist.name();
		raw.loop = playlist.loop();
		raw.songs = new ArrayList<>();
		for (PlaylistSong song : playlist.songs()) {
			FileSong fileSong = new FileSong();
			fileSong.kind = song.kind().name();
			fileSong.value = song.value();
			fileSong.displayTitle = song.displayTitle();
			raw.songs.add(fileSong);
		}
		return raw;
	}

	@SuppressWarnings("unused")
	private static final class FileFormat {
		List<FilePlaylist> playlists;
	}

	@SuppressWarnings("unused")
	private static final class FilePlaylist {
		String name;
		boolean loop;
		List<FileSong> songs;
	}

	@SuppressWarnings("unused")
	private static final class FileSong {
		String kind;
		String value;
		String displayTitle;
	}
}
