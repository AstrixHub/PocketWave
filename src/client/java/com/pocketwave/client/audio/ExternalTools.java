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
import java.util.ArrayList;
import java.util.EnumSet;
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
 */
public final class ExternalTools {
	private static final Object LOCK = new Object();

	private static Path cachedYtDlp;
	private static Path cachedFfmpeg;

	private ExternalTools() {
	}

	public static Path ytDlp(Consumer<String> status) throws IOException {
		synchronized (LOCK) {
			if (isWorkingTool(cachedYtDlp)) {
				return cachedYtDlp;
			}

			Path system = findOnPath("yt-dlp", "yt-dlp.exe");
			if (system != null) {
				cachedYtDlp = system;
				return cachedYtDlp;
			}

			Platform platform = Platform.detect();
			Path target = binDir().resolve(platform.ytDlpFileName);
			if (isWorkingTool(target)) {
				cachedYtDlp = target;
				return cachedYtDlp;
			}

			if (status != null) {
				status.accept("Extracting bundled yt-dlp...");
			}

			extractGzipResource(platform.ytDlpResource, target);
			makeExecutable(target);
			if (!isWorkingTool(target)) {
				throw new IOException("Bundled yt-dlp could not be started from " + target);
			}

			cachedYtDlp = target;
			return cachedYtDlp;
		}
	}

	public static Path ffmpeg(Consumer<String> status) throws IOException {
		synchronized (LOCK) {
			if (isWorkingTool(cachedFfmpeg)) {
				return cachedFfmpeg;
			}

			Path system = findOnPath("ffmpeg", "ffmpeg.exe");
			if (system != null) {
				cachedFfmpeg = system;
				return cachedFfmpeg;
			}

			Platform platform = Platform.detect();
			Path target = binDir().resolve(platform.ffmpegFileName);
			if (isWorkingTool(target)) {
				cachedFfmpeg = target;
				return cachedFfmpeg;
			}

			if (status != null) {
				status.accept("Downloading ffmpeg (one-time, ~20–30MB)...");
			}

			downloadGzip(platform.ffmpegUrl, target);
			makeExecutable(target);
			if (!isWorkingTool(target)) {
				Files.deleteIfExists(target);
				throw new IOException("Failed to download ffmpeg. Check your internet connection, or install ffmpeg on your PATH.");
			}

			cachedFfmpeg = target;
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

	private static boolean isWorkingTool(Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			return false;
		}
		try {
			if (Files.size(path) < 100_000) {
				return false;
			}
		} catch (IOException e) {
			return false;
		}
		makeExecutable(path);
		return ProcessUtil.commandAvailable(path.toString());
	}

	private static void extractGzipResource(String resourcePath, Path target) throws IOException {
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
		}

		moveReplace(tmp, target);
	}

	private static void downloadGzip(String url, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");

		try (InputStream in = new GZIPInputStream(URI.create(url).toURL().openStream());
				OutputStream out = Files.newOutputStream(tmp)) {
			in.transferTo(out);
		} catch (IOException e) {
			Files.deleteIfExists(tmp);
			throw new IOException("Failed to download ffmpeg. Check your internet connection, or install ffmpeg on your PATH.", e);
		}

		moveReplace(tmp, target);
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
			if (isWorkingTool(candidate)) {
				return candidate;
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
					PosixFilePermission.OWNER_EXECUTE,
					PosixFilePermission.GROUP_READ,
					PosixFilePermission.GROUP_EXECUTE,
					PosixFilePermission.OTHERS_READ,
					PosixFilePermission.OTHERS_EXECUTE
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
				"ffmpeg.exe",
				"https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-win32-x64.gz"
		),
		LINUX_X64(
				"natives/yt-dlp/linux-x64.gz",
				"yt-dlp",
				"ffmpeg",
				"https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-linux-x64.gz"
		),
		MACOS_ARM64(
				"natives/yt-dlp/macos.gz",
				"yt-dlp",
				"ffmpeg",
				"https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-darwin-arm64.gz"
		),
		MACOS_X64(
				"natives/yt-dlp/macos.gz",
				"yt-dlp",
				"ffmpeg",
				"https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-darwin-x64.gz"
		);

		final String ytDlpResource;
		final String ytDlpFileName;
		final String ffmpegFileName;
		final String ffmpegUrl;

		Platform(String ytDlpResource, String ytDlpFileName, String ffmpegFileName, String ffmpegUrl) {
			this.ytDlpResource = ytDlpResource;
			this.ytDlpFileName = ytDlpFileName;
			this.ffmpegFileName = ffmpegFileName;
			this.ffmpegUrl = ffmpegUrl;
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
