package com.pocketwave.client.onboarding;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class IntroductionOnboardingScreen extends OnboardingScreenBase {
	private static final Component BODY = Component.literal(
			"PocketWave lets you play music in Minecraft from YouTube links, search terms, or files on your computer.\n\n"
					+ "This short guide shows the basics. You can skip it if you already know what you're doing — "
					+ "you can always reopen it from /music settings."
	);

	IntroductionOnboardingScreen(Screen previous) {
		super(Component.literal("Welcome to PocketWave"), previous);
	}

	@Override
	protected void init() {
		super.init();
		addBody(BODY);

		addRenderableWidget(Button.builder(Component.literal("I know what I'm doing — Skip"), button ->
						this.minecraft.gui.setScreen(new SkipOnboardingScreen(this)))
				.bounds(this.panelLeft + PAD, bottomY() - 28, this.contentWidth, BUTTON_H)
				.build());

		addBackOrCancelButton();
		addNextButton(new PlayOnboardingScreen(this));
	}
}
