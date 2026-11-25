package com.pocketwave.client.audio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

import net.minecraft.client.Minecraft;

import com.pocketwave.PocketWave;

/**
 * Resolves yt-dlp / ffmpeg: system PATH first, then bundled (yt-dlp) or one-time download (ffmpeg).
 * Bundling ffmpeg for every OS would push the mod over 100MB, so only yt-dlp is jar-embedded.
 * Bundled/downloaded binaries are checked with SHA-256 before use.
 */
public final class ExternalTools {
	private static final Object LOCK = new Object();
	private static final long MAX_FFMPEG_BYTES = 100L * 1024L * 1024L;

	private static Path cachedYtDlp;
	private static Path cachedFfmpeg;
	private static boolean ytDlpReady;
	private static boolean ffmpegReady;

	private ExternalTools() {
	}

	public static Path ytDlp(Consumer<String> status) throws IOException {
		synchronized (LOCK) {
			if (ytDlpReady && cachedYtDlp != null) {
				return cachedYtDlp;
			}

			Path system = findOnPath("yt-dlp", "yt-dlp.exe");
			if (system != null) {
				cachedYtDlp = system;
				ytDlpReady = true;
				return cachedYtDlp;
			}

			Platform platform = Platform.detect();
			Path target = binDir().resolve(platform.ytDlpFileName);
			if (Files.isRegularFile(target) && verifyExisting(target, platform.ytDlpSha256)) {
				cachedYtDlp = target;
				ytDlpReady = true;
				return cachedYtDlp;
			}

			if (status != null) {
				status.accept("Extracting bundled yt-dlp...");
			}

			extractGzipResource(platform.ytDlpResource, target, platform.ytDlpSha256);
			makeExecutable(target);
			if (!smokeTest(target)) {
				Files.deleteIfExists(target);
				throw new IOException("Bundled yt-dlp could not be started from " + target);
			}

			cachedYtDlp = target;
			ytDlpReady = true;
			return cachedYtDlp;
		}
	}

	public static Path ffmpeg(Consumer<String> status) throws IOException {
		synchronized (LOCK) {
			if (ffmpegReady && cachedFfmpeg != null) {
				return cachedFfmpeg;
			}

			Path system = findOnPath("ffmpeg", "ffmpeg.exe");
			if (system != null) {
				cachedFfmpeg = system;
				ffmpegReady = true;
				return cachedFfmpeg;
			}

			Platform platform = Platform.detect();
			Path target = binDir().resolve(platform.ffmpegFileName);
			if (Files.isRegularFile(target) && verifyExisting(target, platform.ffmpegSha256)) {
				cachedFfmpeg = target;
				ffmpegReady = true;
				return cachedFfmpeg;
			}

			if (!FfmpegConsent.ensureAllowed()) {
				throw new IOException(
						"ffmpeg download declined. Allow it in /music settings, or install ffmpeg on your PATH."
				);
			}

			if (status != null) {
				status.accept("Downloading ffmpeg (one-time, ~20–30MB)...");
			}

			downloadGzip(platform.ffmpegUrl, target, platform.ffmpegSha256);
			makeExecutable(target);
			if (!smokeTest(target)) {
				Files.deleteIfExists(target);
				throw new IOException("Failed to download ffmpeg. Check your internet connection, or install ffmpeg on your PATH.");
			}

			cachedFfmpeg = target;
			ffmpegReady = true;
			return cachedFfmpeg;
		}
	}

	public static Path binDir() throws IOException {
		Path dir = Minecraft.getInstance().gameDirectory.toPath()
				.resolve(PocketWave.MOD_ID)
				.resolve("bin");
		Files.createDirectories(dir);
		return dir;
	}

	private static boolean verifyExisting(Path path, String expectedSha256) {
		try {
			if (Files.size(path) < 100_000) {
				return false;
			}
			verifySha256(path, expectedSha256);
			makeExecutable(path);
			return smokeTest(path);
		} catch (IOException e) {
			PocketWave.LOGGER.warn("Ignoring untrusted tool at {}: {}", path, e.toString());
			try {
				Files.deleteIfExists(path);
			} catch (IOException ignored) {
			}
			return false;
		}
	}

	private static boolean smokeTest(Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			return false;
		}
		makeExecutable(path);
		return ProcessUtil.commandAvailable(path.toString());
	}

	private static void extractGzipResource(String resourcePath, Path target, String expectedSha256) throws IOException {
		Files.createDirectories(target.getParent());
		URL resource = ExternalTools.class.getClassLoader().getResource(resourcePath);
		if (resource == null) {
			throw new IOException("Bundled tool missing from mod jar: " + resourcePath
					+ ". Rebuild the mod with network access so natives can be downloaded.");
		}

		Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
		try (InputStream in = new GZIPInputStream(resource.openStream());
				OutputStream out = Files.newOutputStream(tmp)) {
			in.transferTo(out);
		} catch (IOException e) {
			Files.deleteIfExists(tmp);
			throw e;
		}

		try {
			verifySha256(tmp, expectedSha256);
		} catch (IOException e) {
			Files.deleteIfExists(tmp);
			throw e;
		}
		moveReplace(tmp, target);
	}

	private static void downloadGzip(String url, Path target, String expectedSha256) throws IOException {
		Files.createDirectories(target.getParent());
		Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");

		try (InputStream raw = URI.create(url).toURL().openStream();
				InputStream in = new GZIPInputStream(raw);
				OutputStream out = Files.newOutputStream(tmp)) {
			copyBounded(in, out, MAX_FFMPEG_BYTES);
		} catch (IOException e) {
			Files.deleteIfExists(tmp);
			throw new IOException("Failed to download ffmpeg. Check your internet connection, or install ffmpeg on your PATH.", e);
		}

		try {
			verifySha256(tmp, expectedSha256);
		} catch (IOException e) {
			Files.deleteIfExists(tmp);
			throw e;
		}
		moveReplace(tmp, target);
	}

	private static void copyBounded(InputStream in, OutputStream out, long maxBytes) throws IOException {
		byte[] buf = new byte[8192];
		long total = 0;
		int read;
		while ((read = in.read(buf)) >= 0) {
			total += read;
			if (total > maxBytes) {
				throw new IOException("Download exceeded " + maxBytes + " bytes; aborting.");
			}
			out.write(buf, 0, read);
		}
	}

	private static void verifySha256(Path file, String expectedHex) throws IOException {
		String actual;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream in = Files.newInputStream(file);
					DigestInputStream dig = new DigestInputStream(in, digest)) {
				dig.transferTo(OutputStream.nullOutputStream());
			}
			actual = HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 not available", e);
		}

		if (!actual.equalsIgnoreCase(expectedHex)) {
			throw new IOException(
					"Tool integrity check failed for " + file.getFileName()
							+ " (expected " + expectedHex + ", got " + actual + "). "
							+ "Delete pocketwave/bin and try again, or install the tool on your PATH."
			);
		}
	}

	private static void moveReplace(Path from, Path to) throws IOException {
		try {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static Path findOnPath(String unixName, String windowsName) {
		boolean windows = Platform.isWindows();
		String wanted = windows ? windowsName : unixName;

		for (Path dir : toolSearchDirs(windows)) {
			Path candidate = dir.resolve(wanted);
			if (smokeTest(candidate)) {
				try {
					if (Files.size(candidate) >= 100_000) {
						return candidate;
					}
				} catch (IOException ignored) {
				}
			}
		}

		if (ProcessUtil.commandAvailable(unixName)) {
			return Path.of(unixName);
		}
		if (windows && ProcessUtil.commandAvailable(windowsName)) {
			return Path.of(windowsName);
		}

		return null;
	}

	private static List<Path> toolSearchDirs(boolean windows) {
		List<Path> dirs = new ArrayList<>();
		String pathEnv = System.getenv("PATH");
		if (pathEnv != null && !pathEnv.isBlank()) {
			for (String part : pathEnv.split(windows ? ";" : ":")) {
				if (!part.isBlank()) {
					dirs.add(Path.of(part.trim()));
				}
			}
		}

		// Minecraft launchers often strip Homebrew / local PATH entries.
		if (!windows) {
			String home = System.getProperty("user.home", "");
			dirs.add(Path.of("/opt/homebrew/bin"));
			dirs.add(Path.of("/usr/local/bin"));
			dirs.add(Path.of("/opt/local/bin"));
			if (!home.isBlank()) {
				dirs.add(Path.of(home, ".local", "bin"));
			}
		}

		return dirs;
	}

	private static void makeExecutable(Path path) {
		try {
			Set<PosixFilePermission> perms = EnumSet.of(
					PosixFilePermission.OWNER_READ,
					PosixFilePermission.OWNER_WRITE,
					PosixFilePermission.OWNER_EXECUTE
			);
			Files.setPosixFilePermissions(path, perms);
		} catch (UnsupportedOperationException | IOException ignored) {
			// Windows / non-POSIX
		}
	}

	private enum Platform {
		WINDOWS_X64(
				"natives/yt-dlp/windows-x64.exe.gz",
				"yt-dlp.exe",
				"66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a",
				"ffmpeg.exe",
				"https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-win32-x64.gz",
				"04e1307997530f9cf2fe35cba2ca7e8875ca91da02f89d6c7243df819c94ad00"
		),
		LINUX_X64(
				"natives/yt-dlp/linux-x64.gz",
				"yt-dlp",
				"58162f9bfdc27458ea47bfcb311cf47028f17d8154a8bf7d689861d46399230a",
				"ffmpeg",
				"https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-linux-x64.gz",
				"e7e7fb30477f717e6f55f9180a70386c62677ef8a4d4d1a5d948f4098aa3eb99"
		),
		MACOS_ARM64(
				"natives/yt-dlp/macos.gz",
				"yt-dlp",
				"0f192b7ec147ab6288885d6351d9ab67367640029b4377576ef46dd79cf7b202",
				"ffmpeg",
				"https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-darwin-arm64.gz",
				"a90e3db6a3fd35f6074b013f948b1aa45b31c6375489d39e572bea3f18336584"
		),
		MACOS_X64(
				"natives/yt-dlp/macos.gz",
				"yt-dlp",
				"0f192b7ec147ab6288885d6351d9ab67367640029b4377576ef46dd79cf7b202",
				"ffmpeg",
				"https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-darwin-x64.gz",
				"ebdddc936f61e14049a2d4b549a412b8a40deeff6540e58a9f2a2da9e6b18894"
		);

		final String ytDlpResource;
		final String ytDlpFileName;
		final String ytDlpSha256;
		final String ffmpegFileName;
		final String ffmpegUrl;
		final String ffmpegSha256;

		Platform(
				String ytDlpResource,
				String ytDlpFileName,
				String ytDlpSha256,
				String ffmpegFileName,
				String ffmpegUrl,
				String ffmpegSha256
		) {
			this.ytDlpResource = ytDlpResource;
			this.ytDlpFileName = ytDlpFileName;
			this.ytDlpSha256 = ytDlpSha256;
			this.ffmpegFileName = ffmpegFileName;
			this.ffmpegUrl = ffmpegUrl;
			this.ffmpegSha256 = ffmpegSha256;
		}

		static boolean isWindows() {
			return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		}

		static Platform detect() throws IOException {
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
			boolean arm = arch.contains("aarch64") || arch.equals("arm64");

			if (os.contains("win")) {
				return WINDOWS_X64;
			}
			if (os.contains("mac") || os.contains("darwin")) {
				return arm ? MACOS_ARM64 : MACOS_X64;
			}
			if (os.contains("nux") || os.contains("nix")) {
				if (arm) {
					throw new IOException("Bundled tools do not include Linux ARM yet. Install yt-dlp and ffmpeg on your PATH.");
				}
				return LINUX_X64;
			}

			throw new IOException("Unsupported OS for bundled tools: " + os + ". Install yt-dlp and ffmpeg on your PATH.");
		}
	}
}
