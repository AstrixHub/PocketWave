package com.pocketwave.client.playlist;

import net.minecraft.client.Minecraft;

/**
 * Opens the playlist GUI on the client thread (replaces chat if a command opened it).
 */
public final class PlaylistScreens {
	private PlaylistScreens() {
	}

	public static void open() {
		Minecraft client = Minecraft.getInstance();
		// Always queue: chat closes the screen with setScreen(null) after the command
		// returns, which would immediately dismiss a GUI opened synchronously.
		client.schedule(() -> client.gui.setScreen(new PlaylistListScreen()));
	}
}
