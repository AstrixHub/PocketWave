package com.pocketwave.client.onboarding;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.pocketwave.client.settings.MusicSettings;

final class FfmpegOnboardingScreen extends OnboardingScreenBase {
	private static final Component BODY = Component.literal(
			"One extra helper is needed to turn songs into audio Minecraft can play.\n\n"
					+ "It's called ffmpeg. PocketWave can download it for you from GitHub "
					+ "(about 20–30 MB — not a big file) and keep it in your instance's pocketwave/bin folder.\n\n"
					+ "You can say no and install ffmpeg yourself later, or turn this on/off anytime in settings."
	);

	FfmpegOnboardingScreen(Screen previous) {
		super(Component.literal("Download ffmpeg?"), previous);
	}

	@Override
	protected void init() {
		super.init();
		addBody(BODY);

		int half = (this.contentWidth - 8) / 2;
		int y = bottomY() - 28;
		addRenderableWidget(Button.builder(Component.literal("Yes, download it"), button -> {
			MusicSettings.INSTANCE.setAllowFfmpegDownload(true);
			this.minecraft.gui.setScreen(new FinalOnboardingScreen(this, true));
		}).bounds(this.panelLeft + PAD, y, half, BUTTON_H).build());

		addRenderableWidget(Button.builder(Component.literal("No thanks"), button -> {
			MusicSettings.INSTANCE.setAllowFfmpegDownload(false);
			this.minecraft.gui.setScreen(new FinalOnboardingScreen(this, false));
		}).bounds(this.panelLeft + PAD + half + 8, y, half, BUTTON_H).build());

		addBackOrCancelButton();
	}
}
