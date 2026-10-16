package com.pocketwave.client.audio;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.pocketwave.PocketWave;

/**
 * Downloads audio with yt-dlp and converts to WAV with ffmpeg.
 */
public final class YtDlpService {
	public record PreparedTrack(Path wavFile, String title, Path workDir) {
	}

	public PreparedTrack prepare(String queryOrUrl, Consumer<String> status) throws IOException, InterruptedException {
		Path ytDlp = ExternalTools.ytDlp(status);
		Path ffmpeg = ExternalTools.ffmpeg(status);

		String input = normalizeInput(queryOrUrl);
		Path workDir = CachePaths.newWorkDir("yt");
		Path outputTemplate = workDir.resolve("audio.%(ext)s");

		List<String> downloadCmd = new ArrayList<>();
		downloadCmd.add(ytDlp.toString());
		downloadCmd.add("--no-playlist");
		downloadCmd.add("--no-warnings");
		downloadCmd.add("--newline");
		downloadCmd.add("--ffmpeg-location");
		downloadCmd.add(ffmpeg.getParent() != null ? ffmpeg.getParent().toString() : ffmpeg.toString());
		downloadCmd.add("-f");
		downloadCmd.add("bestaudio/best");
		downloadCmd.add("--extractor-args");
		downloadCmd.add("youtube:player_client=android,web");
		downloadCmd.add("-o");
		downloadCmd.add(outputTemplate.toString());
		downloadCmd.add("--print");
		downloadCmd.add("after_move:title");
		downloadCmd.add("--print");
		downloadCmd.add("after_move:filepath");
		downloadCmd.add(input);

		PocketWave.LOGGER.info("Running yt-dlp for: {}", input);
		ProcessUtil.Result download = ProcessUtil.run(downloadCmd, 180);

		if (download.exitCode() != 0) {
			throw new IOException(download.output().isBlank()
					? "yt-dlp failed with exit code " + download.exitCode()
					: download.output());
		}

		String title = "Unknown track";
		Path downloaded = findDownloadedFile(workDir);

		String[] lines = download.output().lines().map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
		for (String line : lines) {
			Path maybe = Path.of(line);
			if (Files.isRegularFile(maybe)) {
				downloaded = maybe;
			} else if (!line.startsWith("[") && !line.contains("%") && !line.startsWith("WARNING")) {
				title = line;
			}
		}

		if (downloaded == null || !Files.isRegularFile(downloaded)) {
			throw new IOException("yt-dlp did not produce an audio file.\n" + download.output());
		}

		if ("Unknown track".equals(title)) {
			title = stripExtension(downloaded.getFileName().toString());
		}

		Path wav = workDir.resolve("track.wav");
		convertToWav(ffmpeg, downloaded, wav);

		try {
			if (!downloaded.equals(wav)) {
				Files.deleteIfExists(downloaded);
			}
		} catch (IOException ignored) {
		}

		return new PreparedTrack(wav, title, workDir);
	}

	private static void convertToWav(Path ffmpeg, Path input, Path wav) throws IOException, InterruptedException {
		List<String> cmd = List.of(
				ffmpeg.toString(),
				"-y",
				"-i", input.toString(),
				"-vn",
				"-acodec", "pcm_s16le",
				"-ar", "44100",
				"-ac", "2",
				wav.toString()
		);

		ProcessUtil.Result result = ProcessUtil.run(cmd, 120);
		if (result.exitCode() != 0 || !Files.isRegularFile(wav)) {
			throw new IOException(result.output().isBlank()
					? "ffmpeg failed to convert audio"
					: result.output());
		}
	}

	private static String normalizeInput(String queryOrUrl) {
		String trimmed = queryOrUrl.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);

		if (lower.startsWith("http://") || lower.startsWith("https://")
				|| lower.startsWith("ytsearch") || lower.contains("://")) {
			return trimmed;
		}

		return "ytsearch1:" + trimmed;
	}

	private static Path findDownloadedFile(Path workDir) throws IOException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(workDir)) {
			Path best = null;
			for (Path path : stream) {
				if (!Files.isRegularFile(path)) {
					continue;
				}
				String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
				if (name.endsWith(".wav") || name.endsWith(".part") || name.endsWith(".ytdl")) {
					continue;
				}
				if (best == null || Files.getLastModifiedTime(path).compareTo(Files.getLastModifiedTime(best)) > 0) {
					best = path;
				}
			}
			return best;
		}
	}

	private static String stripExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	public static void deleteQuietly(Path workDir) {
		if (workDir == null || !Files.exists(workDir)) {
			return;
		}

		try (Stream<Path> walk = Files.walk(workDir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
				}
			});
		} catch (IOException e) {
			PocketWave.LOGGER.debug("Failed to clean cache dir {}", workDir, e);
		}
	}
}
