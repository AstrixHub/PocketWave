package com.pocketwave.client.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.minecraft.client.Minecraft;

import com.pocketwave.PocketWave;

/**
 * Saved player preferences at {@code <game dir>/pocketwave/settings.json}.
 */
public final class MusicSettings {
	public static final MusicSettings INSTANCE = new MusicSettings();

	private static final int DEFAULT_VOLUME = 100;
	private static final int DEFAULT_STEP = 10;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private int volumePercent = DEFAULT_VOLUME;
	private int volumeStepPercent = DEFAULT_STEP;
	private boolean showHud = true;
	private boolean loaded;

	private MusicSettings() {
	}

	public synchronized int volumePercent() {
		ensureLoaded();
		return volumePercent;
	}

	public synchronized int volumeStepPercent() {
		ensureLoaded();
		return volumeStepPercent;
	}

	public synchronized float volumeStep() {
		return volumeStepPercent() / 100f;
	}

	public synchronized boolean showHud() {
		ensureLoaded();
		return showHud;
	}

	public synchronized void setVolumePercent(int percent) {
		ensureLoaded();
		volumePercent = clamp(percent, 0, 100);
		save();
	}

	public synchronized void setVolumeStepPercent(int percent) {
		ensureLoaded();
		volumeStepPercent = clamp(percent, 1, 25);
		save();
	}

	public synchronized void setShowHud(boolean showHud) {
		ensureLoaded();
		this.showHud = showHud;
		save();
	}

	private void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = filePath();
		if (file == null || !Files.isRegularFile(file)) {
			return;
		}
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			FileFormat format = GSON.fromJson(json, FileFormat.class);
			if (format == null) {
				return;
			}
			if (format.volumePercent != null) {
				volumePercent = clamp(format.volumePercent, 0, 100);
			}
			if (format.volumeStepPercent != null && format.volumeStepPercent > 0) {
				volumeStepPercent = clamp(format.volumeStepPercent, 1, 25);
			}
			showHud = format.showHud == null || format.showHud;
		} catch (IOException | JsonSyntaxException e) {
			PocketWave.LOGGER.warn("Could not read settings.json", e);
		}
	}

	private void save() {
		Path file = filePath();
		if (file == null) {
			return;
		}
		FileFormat format = new FileFormat();
		format.volumePercent = volumePercent;
		format.volumeStepPercent = volumeStepPercent;
		format.showHud = showHud;
		Path tmp = file.resolveSibling("settings.json.tmp");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(tmp, GSON.toJson(format), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			PocketWave.LOGGER.warn("Could not save settings.json", e);
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
			}
		}
	}

	private static Path filePath() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.gameDirectory == null) {
			return null;
		}
		return client.gameDirectory.toPath().resolve(PocketWave.MOD_ID).resolve("settings.json");
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	@SuppressWarnings("unused")
	private static final class FileFormat {
		Integer volumePercent;
		Integer volumeStepPercent;
		Boolean showHud;
	}
}
