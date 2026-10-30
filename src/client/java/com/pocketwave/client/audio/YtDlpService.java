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

		String input = resolveInput(queryOrUrl, ytDlp);
		Path workDir = CachePaths.newWorkDir("yt");
		Path outputTemplate = workDir.resolve("audio.%(ext)s");

		List<String> downloadCmd = new ArrayList<>();
		downloadCmd.add(ytDlp.toString());
		downloadCmd.add("--no-playlist");
		downloadCmd.add("--no-warnings");
		downloadCmd.add("--no-progress");
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
				"-nostdin",
				"-hide_banner",
				"-loglevel", "error",
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

	private static String resolveInput(String queryOrUrl, Path ytDlp) throws IOException, InterruptedException {
		String trimmed = queryOrUrl.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);

		if (lower.startsWith("http://") || lower.startsWith("https://")
				|| lower.startsWith("ytsearch") || lower.contains("://")) {
			return trimmed;
		}

		String matched = pickSearchResult(ytDlp, trimmed);
		if (matched != null) {
			return matched;
		}

		return "ytsearch1:" + sanitizeQuery(trimmed);
	}

	/**
	 * Looks at the top YouTube hits and picks a title that actually matches the query.
	 * Phrase search is a fallback only — quoting "Dolphin" will miss titles like "Dol9hin".
	 */
	private static String pickSearchResult(Path ytDlp, String query) throws IOException, InterruptedException {
		String sanitized = sanitizeQuery(query);
		List<SearchHit> hits = searchHits(ytDlp, sanitized, false);
		SearchHit best = bestHit(hits, sanitized);
		if (best == null || !best.acceptable(sanitized)) {
			List<SearchHit> quoted = searchHits(ytDlp, sanitized, true);
			for (SearchHit hit : quoted) {
				if (hits.stream().noneMatch(existing -> existing.id.equals(hit.id))) {
					hits.add(hit);
				}
			}
			best = bestHit(hits, sanitized);
		}

		if (best == null || !best.acceptable(sanitized)) {
			PocketWave.LOGGER.info("Search '{}' had no close title match in {} hits", query, hits.size());
			return null;
		}

		PocketWave.LOGGER.info("Search '{}' picked '{}' ({})", query, best.title, best.id);
		return "https://www.youtube.com/watch?v=" + best.id;
	}

	private static List<SearchHit> searchHits(Path ytDlp, String query, boolean quoted) throws IOException, InterruptedException {
		String search = quoted ? "ytsearch10:\"" + query + "\"" : "ytsearch10:" + query;
		List<String> cmd = new ArrayList<>();
		cmd.add(ytDlp.toString());
		cmd.add("--flat-playlist");
		cmd.add("--skip-download");
		cmd.add("--no-warnings");
		cmd.add("--no-playlist");
		cmd.add("--print");
		cmd.add("%(id)s\t%(title)s\t%(duration)s");
		cmd.add(search);

		ProcessUtil.Result result = ProcessUtil.run(cmd, 60);
		if (result.exitCode() != 0) {
			PocketWave.LOGGER.warn("Search failed for '{}': {}", query, result.output());
			return new ArrayList<>();
		}

		List<SearchHit> hits = new ArrayList<>();
		for (String line : result.output().lines().toList()) {
			SearchHit hit = SearchHit.parse(line);
			if (hit != null) {
				hits.add(hit);
			}
		}
		return hits;
	}

	private static SearchHit bestHit(List<SearchHit> hits, String query) {
		SearchHit best = null;
		int bestScore = Integer.MIN_VALUE;
		for (SearchHit hit : hits) {
			int score = hit.score(query);
			if (score > bestScore) {
				bestScore = score;
				best = hit;
			}
		}
		return best;
	}

	private static String sanitizeQuery(String query) {
		return query.replace('"', ' ').replace('\n', ' ').replace('\r', ' ').trim();
	}

	private record SearchHit(String id, String title, int durationSeconds) {
		private static final String[] STOPWORDS = {
				"a", "an", "the", "for", "of", "to", "and", "or", "in", "on", "at", "by", "with", "from"
		};
		private static final String[] JUNK = {
				"nightcore", "sped up", "slowed", "reverb", "8d", "cover", "karaoke",
				"remix", "mashup", "hour", "hours", "live", "concert", "reaction",
				"chipmunk", "bass boosted", "tiktok", "gameplay", "pvp", "montage",
				"highlights", "winstreak", "vs"
		};

		static SearchHit parse(String line) {
			if (line == null || line.isBlank() || line.startsWith("[")) {
				return null;
			}
			String[] parts = line.split("\t", 3);
			if (parts.length < 2) {
				return null;
			}
			String id = parts[0].trim();
			if (id.length() < 8 || id.contains(" ")) {
				return null;
			}
			String title = parts[1].trim();
			int duration = -1;
			if (parts.length >= 3) {
				try {
					duration = (int) Double.parseDouble(parts[2].trim());
				} catch (NumberFormatException ignored) {
				}
			}
			return new SearchHit(id, title, duration);
		}

		boolean acceptable(String query) {
			int distinctive = distinctiveCount(query);
			if (distinctive <= 0) {
				return true;
			}
			int matched = matchedDistinctive(query);
			int needed = distinctive <= 2 ? distinctive : (distinctive * 2 + 2) / 3;
			return matched >= needed;
		}

		int score(String query) {
			String q = normalize(query);
			String t = normalize(title);
			if (q.isEmpty() || t.isEmpty()) {
				return Integer.MIN_VALUE;
			}

			int score = 0;
			if (t.equals(q)) {
				score += 80;
			} else if (t.contains(q)) {
				score += 36;
			}

			int distinctive = 0;
			int matched = 0;
			for (String token : q.split(" ")) {
				if (!isDistinctive(token)) {
					continue;
				}
				distinctive++;
				if (tokenMatches(token, t)) {
					matched++;
					score += 18;
				} else {
					score -= 30;
				}
			}

			if (distinctive > 0 && matched == 0) {
				return Integer.MIN_VALUE / 4;
			}

			String[] junk = JUNK;
			for (String word : junk) {
				if (t.contains(word) && !q.contains(word)) {
					score -= 25;
				}
			}

			if (t.contains("official") || t.contains("topic") || t.contains("audio") || t.contains("lyrics")) {
				score += 8;
			}

			if (durationSeconds > 0) {
				if (durationSeconds < 40) {
					score -= 24;
				} else if (durationSeconds <= 480) {
					score += 6;
				} else if (durationSeconds > 900 && !q.contains("mix") && !q.contains("hour")) {
					score -= 16;
				}
			}

			return score;
		}

		private int distinctiveCount(String query) {
			int count = 0;
			for (String token : normalize(query).split(" ")) {
				if (isDistinctive(token)) {
					count++;
				}
			}
			return count;
		}

		private int matchedDistinctive(String query) {
			String t = normalize(title);
			int matched = 0;
			for (String token : normalize(query).split(" ")) {
				if (isDistinctive(token) && tokenMatches(token, t)) {
					matched++;
				}
			}
			return matched;
		}

		private static boolean isDistinctive(String token) {
			if (token == null || token.length() <= 1) {
				return false;
			}
			for (String stop : STOPWORDS) {
				if (token.equals(stop)) {
					return false;
				}
			}
			return true;
		}

		private static boolean tokenMatches(String token, String titleNorm) {
			if (titleNorm.contains(token)) {
				return true;
			}
			if (token.length() < 4) {
				return false;
			}
			for (String word : titleNorm.split(" ")) {
				if (word.length() < 4) {
					continue;
				}
				if (Math.abs(word.length() - token.length()) > 1) {
					continue;
				}
				if (levenshtein(token, word) <= 1) {
					return true;
				}
			}
			return false;
		}

		private static int levenshtein(String a, String b) {
			int[] prev = new int[b.length() + 1];
			int[] cur = new int[b.length() + 1];
			for (int j = 0; j <= b.length(); j++) {
				prev[j] = j;
			}
			for (int i = 1; i <= a.length(); i++) {
				cur[0] = i;
				for (int j = 1; j <= b.length(); j++) {
					int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
					cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
				}
				int[] swap = prev;
				prev = cur;
				cur = swap;
			}
			return prev[b.length()];
		}

		private static String normalize(String text) {
			return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
		}
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
