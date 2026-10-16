package com.pocketwave.client.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

/**
 * Prepares a local audio file as a WAV suitable for {@link AudioPlayer}.
 */
public final class LocalFileService {
	public YtDlpService.PreparedTrack prepare(String pathText, Consumer<String> status) throws IOException, InterruptedException {
		Path path = resolvePath(pathText);
		if (!Files.isRegularFile(path)) {
			throw new IOException("File not found: " + path);
		}

		String name = path.getFileName().toString();
		String lower = name.toLowerCase(Locale.ROOT);

		if (lower.endsWith(".wav")) {
			return new YtDlpService.PreparedTrack(path, stripExtension(name), null);
		}

		Path ffmpeg = ExternalTools.ffmpeg(status);
		Path workDir = CachePaths.newWorkDir("local");
		Path wav = workDir.resolve("track.wav");

		List<String> cmd = List.of(
				ffmpeg.toString(),
				"-y",
				"-i", path.toAbsolutePath().toString(),
				"-vn",
				"-acodec", "pcm_s16le",
				"-ar", "44100",
				"-ac", "2",
				wav.toString()
		);

		ProcessUtil.Result result = ProcessUtil.run(cmd, 120);
		if (result.exitCode() != 0 || !Files.isRegularFile(wav)) {
			throw new IOException(result.output().isBlank()
					? "ffmpeg failed to convert local file"
					: result.output());
		}

		return new YtDlpService.PreparedTrack(wav, stripExtension(name), workDir);
	}

	private static Path resolvePath(String pathText) {
		String trimmed = pathText.trim();
		if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
				|| (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
			trimmed = trimmed.substring(1, trimmed.length() - 1);
		}

		Path path = Path.of(trimmed);
		if (path.isAbsolute()) {
			return path.normalize();
		}

		return Minecraft.getInstance().gameDirectory.toPath().resolve(path).normalize();
	}

	private static String stripExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}
}
