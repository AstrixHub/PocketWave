package com.pocketwave.client;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.pocketwave.PocketWave;
import com.pocketwave.client.audio.AudioPlayer;
import com.pocketwave.client.audio.LocalFileService;
import com.pocketwave.client.audio.YtDlpService;

/**
 * Coordinates async fetch/convert and playback.
 */
public final class MusicController {
	public static final MusicController INSTANCE = new MusicController();

	private static final float VOLUME_STEP = 0.10f;

	private final AudioPlayer player = new AudioPlayer();
	private final YtDlpService ytDlp = new YtDlpService();
	private final LocalFileService localFiles = new LocalFileService();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "pocketwave");
		t.setDaemon(true);
		return t;
	});

	private final AtomicInteger jobEpoch = new AtomicInteger();
	private Future<?> currentJob;
	private Path currentWorkDir;
	private String currentTitle;
	private volatile boolean loading;

	private MusicController() {
	}

	public void playQuery(String queryOrUrl) {
		startJob("Fetching audio...", status -> ytDlp.prepare(queryOrUrl, status));
	}

	public void playLocal(String path) {
		startJob("Loading local file...", status -> localFiles.prepare(path, status));
	}

	private void startJob(String startMessage, TrackSupplier supplier) {
		int epoch = jobEpoch.incrementAndGet();
		cancelJob();
		player.stop();
		cleanupWorkDir();

		loading = true;
		ChatFeedback.info(startMessage);

		currentJob = executor.submit(() -> {
			try {
				YtDlpService.PreparedTrack track = supplier.get(ChatFeedback::info);
				if (epoch != jobEpoch.get()) {
					YtDlpService.deleteQuietly(track.workDir());
					return;
				}

				currentWorkDir = track.workDir();
				currentTitle = track.title();
				player.play(track.wavFile());
				loading = false;
				ChatFeedback.info("Now playing: " + track.title());
			} catch (Exception e) {
				if (epoch != jobEpoch.get()) {
					return;
				}
				loading = false;
				PocketWave.LOGGER.error("Failed to play music", e);
				String message = e.getMessage();
				if ((message == null || message.isBlank()) && e.getCause() != null) {
					message = e.getCause().getMessage();
				}
				ChatFeedback.error(ChatFeedback.friendlyError(message));
			}
		});
	}

	public void pause() {
		if (!player.hasTrack()) {
			ChatFeedback.error("Nothing is playing.");
			return;
		}
		player.pause();
		ChatFeedback.info("Paused.");
	}

	public void resume() {
		if (!player.hasTrack()) {
			ChatFeedback.error("Nothing is playing.");
			return;
		}
		player.resume();
		ChatFeedback.info("Resumed.");
	}

	public void togglePause() {
		if (!player.hasTrack()) {
			ChatFeedback.error("Nothing is playing.");
			return;
		}

		if (player.isPaused() || !player.isPlaying()) {
			player.resume();
			ChatFeedback.info("Resumed.");
		} else {
			player.pause();
			ChatFeedback.info("Paused.");
		}
	}

	public void stop() {
		jobEpoch.incrementAndGet();
		cancelJob();
		player.stop();
		cleanupWorkDir();
		currentTitle = null;
		loading = false;
		ChatFeedback.info("Stopped.");
	}

	public void volumeUp() {
		player.adjustVolume(VOLUME_STEP);
		ChatFeedback.info("Volume: " + percent() + "%");
	}

	public void volumeDown() {
		player.adjustVolume(-VOLUME_STEP);
		ChatFeedback.info("Volume: " + percent() + "%");
	}

	public void setVolumePercent(int percent) {
		int clamped = Math.max(0, Math.min(100, percent));
		player.setVolume(clamped / 100f);
		ChatFeedback.info("Volume: " + clamped + "%");
	}

	public void nowPlaying() {
		if (loading) {
			ChatFeedback.info("Loading...");
			return;
		}

		if (!player.hasTrack()) {
			ChatFeedback.info("Nothing playing.");
			return;
		}

		String state = player.isPaused() ? "Paused" : (player.isPlaying() ? "Playing" : "Stopped");
		String title = currentTitle == null ? "Unknown" : currentTitle;
		ChatFeedback.info(state + ": " + title + " (" + percent() + "%)");
	}

	/** Visible while a track is loaded (playing or paused) or still fetching. */
	public boolean shouldShowHud() {
		return loading || player.hasTrack();
	}

	public boolean isPaused() {
		return player.isPaused();
	}

	public int getVolumePercent() {
		return percent();
	}

	public String hudTitle() {
		if (loading) {
			return "Loading...";
		}
		String title = currentTitle == null ? "Unknown" : currentTitle;
		return player.isPaused() ? title + "  ·  paused" : title;
	}

	public void shutdown() {
		jobEpoch.incrementAndGet();
		cancelJob();
		player.stop();
		cleanupWorkDir();
		executor.shutdownNow();
	}

	private int percent() {
		return Math.round(player.getVolume() * 100f);
	}

	private void cancelJob() {
		if (currentJob != null) {
			currentJob.cancel(true);
			currentJob = null;
		}
	}

	private void cleanupWorkDir() {
		YtDlpService.deleteQuietly(currentWorkDir);
		currentWorkDir = null;
	}

	@FunctionalInterface
	private interface TrackSupplier {
		YtDlpService.PreparedTrack get(java.util.function.Consumer<String> status) throws Exception;
	}
}
