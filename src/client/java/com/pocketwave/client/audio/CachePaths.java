package com.pocketwave.client.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;

import com.pocketwave.PocketWave;

final class CachePaths {
	private CachePaths() {
	}

	static Path cacheDir() throws IOException {
		Path dir = Minecraft.getInstance().gameDirectory.toPath()
				.resolve(PocketWave.MOD_ID)
				.resolve("cache");
		Files.createDirectories(dir);
		return dir;
	}

	static Path newWorkDir(String prefix) throws IOException {
		Path dir = cacheDir().resolve(prefix + "-" + System.currentTimeMillis());
		Files.createDirectories(dir);
		return dir;
	}
}
