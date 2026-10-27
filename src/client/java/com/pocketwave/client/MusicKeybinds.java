package com.pocketwave.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.pocketwave.PocketWave;
import com.pocketwave.client.playlist.PlaylistScreens;

final class MusicKeybinds {
	private static KeyMapping pauseResume;
	private static KeyMapping volumeUp;
	private static KeyMapping volumeDown;
	private static KeyMapping stop;
	private static KeyMapping loop;
	private static KeyMapping showQueue;
	private static KeyMapping openPlaylists;

	private MusicKeybinds() {
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(PocketWave.id("music"));

		// Unbound by default (UNKNOWN / -1).
		pauseResume = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pocketwave.pause_resume",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				category
		));
		volumeUp = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pocketwave.volume_up",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				category
		));
		volumeDown = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pocketwave.volume_down",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				category
		));
		stop = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pocketwave.stop",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				category
		));
		loop = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pocketwave.loop",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				category
		));
		showQueue = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pocketwave.show_queue",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				category
		));
		openPlaylists = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pocketwave.open_playlists",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			MusicController.INSTANCE.tick();

			while (pauseResume.consumeClick()) {
				MusicController.INSTANCE.togglePause();
			}
			while (volumeUp.consumeClick()) {
				MusicController.INSTANCE.volumeUp();
			}
			while (volumeDown.consumeClick()) {
				MusicController.INSTANCE.volumeDown();
			}
			while (stop.consumeClick()) {
				MusicController.INSTANCE.stop();
			}
			while (loop.consumeClick()) {
				MusicController.INSTANCE.toggleLoop();
			}
			while (showQueue.consumeClick()) {
				MusicController.INSTANCE.showQueue();
			}
			while (openPlaylists.consumeClick()) {
				if (client.gui.screen() == null) {
					PlaylistScreens.open();
				}
			}
		});
	}
}
