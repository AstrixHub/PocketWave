package com.pocketwave.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class ChatFeedback {
	private ChatFeedback() {
	}

	static void info(String message) {
		send(Component.literal("§a[PocketWave] §f" + message));
	}

	static void error(String message) {
		send(Component.literal("§c[PocketWave] §f" + message));
	}

	static void send(Component component) {
		Minecraft client = Minecraft.getInstance();
		Runnable task = () -> {
			if (client.player != null) {
				client.player.sendSystemMessage(component);
			}
		};

		if (client.isSameThread()) {
			task.run();
		} else {
			client.execute(task);
		}
	}

	/**
	 * Turns long yt-dlp / bot-check stderr into a short player-facing line.
	 */
	static String friendlyError(String raw) {
		if (raw == null || raw.isBlank()) {
			return "Unknown error.";
		}

		String lower = raw.toLowerCase();

		if (lower.contains("sign in") || lower.contains("not a bot") || lower.contains("confirm you're not")
				|| lower.contains("confirm you are not")) {
			return "YouTube blocked this request. Update yt-dlp, or try another link.";
		}

		if (lower.contains("bundled tool missing") || lower.contains("rebuild the mod")) {
			return "Bundled yt-dlp is missing from the mod jar. Rebuild with network access.";
		}

		if (lower.contains("failed to download ffmpeg")) {
			return "Could not download ffmpeg. Check your internet, or install ffmpeg on your PATH.";
		}

		if (lower.contains("yt-dlp") && (lower.contains("not found") || lower.contains("no such file")
				|| lower.contains("cannot find"))) {
			return "yt-dlp was not found (bundled extract failed). Install yt-dlp on your PATH.";
		}

		if (lower.contains("ffmpeg") && (lower.contains("not found") || lower.contains("no such file")
				|| lower.contains("cannot find"))) {
			return "ffmpeg was not found. Allow the one-time download, or install ffmpeg on your PATH.";
		}

		if (lower.contains("spotify")) {
			return "Spotify is not supported (requires login/DRM). Use YouTube, local files, or another supported link.";
		}

		String firstLine = raw.lines()
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.findFirst()
				.orElse(raw.trim());

		if (firstLine.length() > 160) {
			return firstLine.substring(0, 157) + "...";
		}

		return firstLine;
	}
}
