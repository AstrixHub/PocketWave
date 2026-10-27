package com.pocketwave.client.playlist;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of a named playlist.
 */
public final class Playlist {
	private final String name;
	private final boolean loop;
	private final List<PlaylistSong> songs;

	public Playlist(String name, boolean loop, List<PlaylistSong> songs) {
		this.name = name;
		this.loop = loop;
		this.songs = List.copyOf(songs == null ? List.of() : songs);
	}

	public String name() {
		return name;
	}

	public boolean loop() {
		return loop;
	}

	public List<PlaylistSong> songs() {
		return songs;
	}

	public boolean isEmpty() {
		return songs.isEmpty();
	}

	Playlist withLoop(boolean loop) {
		return new Playlist(name, loop, songs);
	}

	Playlist withSongs(List<PlaylistSong> songs) {
		return new Playlist(name, loop, new ArrayList<>(songs));
	}
}
