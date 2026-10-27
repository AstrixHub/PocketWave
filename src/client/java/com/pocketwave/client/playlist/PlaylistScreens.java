package com.pocketwave.client.playlist;

import com.pocketwave.client.ClientScreens;

/**
 * Opens the playlist GUI on the next client tick (chat closes the screen after a command).
 */
public final class PlaylistScreens {
	private PlaylistScreens() {
	}

	public static void open() {
		ClientScreens.open(new PlaylistListScreen());
	}
}
