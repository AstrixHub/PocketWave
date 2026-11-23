package com.pocketwave.client.onboarding;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class PlayOnboardingScreen extends OnboardingScreenBase {
	private static final Component BODY = Component.literal(
			"Here's how to use it:\n\n"
					+ "• /music play <song or YouTube link> — search or play a link\n"
					+ "• /music pause, resume, stop, skip — control playback\n"
					+ "• /music volume <0-100> — set volume\n"
					+ "• /music playlist — make and play playlists\n"
					+ "• /music settings — volume, HUD, and this guide again\n\n"
					+ "You can also bind keys under Controls → Key Binds → PocketWave."
	);

	PlayOnboardingScreen(Screen previous) {
		super(Component.literal("How to play music"), previous);
	}

	@Override
	protected void init() {
		super.init();
		addBody(BODY);
		addBackOrCancelButton();
		addNextButton(new FfmpegOnboardingScreen(this));
	}
}
