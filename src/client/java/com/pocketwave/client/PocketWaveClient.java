package com.pocketwave.client;

import net.fabricmc.api.ClientModInitializer;

import com.pocketwave.PocketWave;
import com.pocketwave.client.onboarding.OnboardingManager;

public class PocketWaveClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MusicCommands.register();
		MusicKeybinds.register();
		MusicHud.register();
		OnboardingManager.register();
		MusicController.INSTANCE.applySavedVolume();

		Runtime.getRuntime().addShutdownHook(new Thread(MusicController.INSTANCE::shutdown, "pocketwave-shutdown"));

		PocketWave.LOGGER.info("PocketWave ready. Use /music play <url|search>.");
	}
}
