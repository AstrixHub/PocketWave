package com.pocketwave.client.onboarding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import com.pocketwave.client.ChatFeedback;
import com.pocketwave.client.settings.MusicSettings;

/**
 * Simple Voice Chat–style first-run guide. Opens once per install until finished/skipped,
 * and can be reopened from {@code /music settings}.
 */
public final class OnboardingManager {
	private OnboardingManager() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (!needsOnboarding()) {
				return;
			}
			client.schedule(() -> {
				if (!needsOnboarding() || client.gui.screen() != null) {
					return;
				}
				ChatFeedback.info("Opening the PocketWave setup guide…");
				startOnboarding(null);
			});
		});
	}

	public static boolean needsOnboarding() {
		return !MusicSettings.INSTANCE.onboardingFinished();
	}

	public static void startOnboarding(Screen previous) {
		Minecraft client = Minecraft.getInstance();
		client.schedule(() -> client.gui.setScreen(new IntroductionOnboardingScreen(previous)));
	}

	public static void finishOnboarding(Screen from) {
		MusicSettings.INSTANCE.setOnboardingFinished(true);
		Screen exit = from;
		while (exit instanceof OnboardingScreenBase page) {
			exit = page.previous;
		}
		Minecraft.getInstance().gui.setScreen(exit);
	}
}
