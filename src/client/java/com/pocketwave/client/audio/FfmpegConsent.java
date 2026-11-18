package com.pocketwave.client.audio;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

import com.pocketwave.client.ClientScreens;
import com.pocketwave.client.settings.MusicSettings;

/**
 * Asks once (with a clear yes/no screen) before PocketWave downloads ffmpeg.
 */
final class FfmpegConsent {
	private static final AtomicReference<CompletableFuture<Boolean>> PENDING = new AtomicReference<>();

	private FfmpegConsent() {
	}

	static boolean ensureAllowed() {
		if (MusicSettings.INSTANCE.allowFfmpegDownload()) {
			return true;
		}

		boolean allowed = askUser();
		MusicSettings.INSTANCE.setAllowFfmpegDownload(allowed);
		return allowed;
	}

	private static boolean askUser() {
		CompletableFuture<Boolean> created = new CompletableFuture<>();
		CompletableFuture<Boolean> existing = PENDING.compareAndExchange(null, created);
		CompletableFuture<Boolean> future = existing == null ? created : existing;

		if (existing == null) {
			Minecraft client = Minecraft.getInstance();
			Runnable open = () -> ClientScreens.open(new ConfirmScreen(
					accepted -> {
						client.setScreen(null);
						PENDING.compareAndSet(future, null);
						future.complete(accepted);
					},
					Component.literal("Download ffmpeg?"),
					Component.literal(
							"PocketWave needs ffmpeg once to convert audio (~20–30MB). "
									+ "It will download from GitHub into your instance's pocketwave/bin folder. "
									+ "You can also install ffmpeg yourself and leave this as No."
					)
			));

			// Chat closes screens after a command; schedule so the prompt sticks.
			client.execute(open);
		}

		try {
			return Boolean.TRUE.equals(future.get(5, TimeUnit.MINUTES));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			PENDING.compareAndSet(future, null);
			return false;
		} catch (ExecutionException | TimeoutException e) {
			PENDING.compareAndSet(future, null);
			future.complete(false);
			return false;
		}
	}
}
