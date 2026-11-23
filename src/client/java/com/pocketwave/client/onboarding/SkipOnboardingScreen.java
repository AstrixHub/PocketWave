package com.pocketwave.client.onboarding;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SkipOnboardingScreen extends OnboardingScreenBase {
	private static final Component BODY = Component.literal(
			"Skip the setup guide?\n\n"
					+ "You can still play music right away. If PocketWave asks to download ffmpeg later, "
					+ "that's the small helper file used to convert audio (about 20–30 MB).\n\n"
					+ "You can reopen this guide from /music settings whenever you want."
	);

	SkipOnboardingScreen(Screen previous) {
		super(Component.literal("Skip setup?"), previous);
	}

	@Override
	protected void init() {
		super.init();
		addBody(BODY);

		int half = (this.contentWidth - 8) / 2;
		addRenderableWidget(Button.builder(Component.literal("Go back"), button ->
						this.minecraft.gui.setScreen(this.previous))
				.bounds(this.panelLeft + PAD, bottomY(), half, BUTTON_H)
				.build());
		addRenderableWidget(Button.builder(Component.literal("Skip setup"), button ->
						OnboardingManager.finishOnboarding(this))
				.bounds(this.panelLeft + PAD + half + 8, bottomY(), half, BUTTON_H)
				.build());
	}
}
