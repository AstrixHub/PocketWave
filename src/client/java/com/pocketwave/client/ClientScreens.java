package com.pocketwave.client;

import net.minecraft.client.gui.screens.Screen;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Opens a screen on the next client tick so chat's {@code setScreen(null)} cannot
 * immediately dismiss a GUI opened from a {@code /music} command.
 */
public final class ClientScreens {
	private static Screen pending;

	private ClientScreens() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Screen next = pending;
			if (next == null) {
				return;
			}
			pending = null;
			client.setScreen(next);
		});
	}

	public static void open(Screen screen) {
		pending = screen;
	}
}
