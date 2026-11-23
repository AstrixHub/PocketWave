package com.pocketwave.client.onboarding;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class FinalOnboardingScreen extends OnboardingScreenBase {
	private final boolean ffmpegAllowed;

	FinalOnboardingScreen(Screen previous, boolean ffmpegAllowed) {
		super(Component.literal("You're all set"), previous);
		this.ffmpegAllowed = ffmpegAllowed;
	}

	@Override
	protected void init() {
		super.init();
		String ffmpegLine = this.ffmpegAllowed
				? "ffmpeg will download the first time you play a song that needs it."
				: "You declined the ffmpeg download. Install it yourself, or enable it later in /music settings.";
		addBody(Component.literal(
				"That's everything!\n\n"
						+ ffmpegLine
						+ "\n\nTry: /music play never gonna give you up\n\n"
						+ "Reopen this guide anytime with the Setup guide button in /music settings."
		));
		addFinishButton(Component.literal("Finish setup"));
	}
}
