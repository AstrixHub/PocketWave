package com.pocketwave.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.pocketwave.PocketWave;

final class MusicKeybinds {
	private static KeyMapping pauseResume;
	private static KeyMapping volumeUp;
	private static KeyMapping volumeDown;
	private static KeyMapping stop;

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

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
		});
	}
}
