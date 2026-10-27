package com.pocketwave.client;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.pocketwave.PocketWave;
import com.pocketwave.client.audio.AudioPlayer;
import com.pocketwave.client.audio.LocalFileService;
import com.pocketwave.client.audio.YtDlpService;
import com.pocketwave.client.playlist.Playlist;
import com.pocketwave.client.playlist.PlaylistSong;
import com.pocketwave.client.playlist.PlaylistStore;
import com.pocketwave.client.settings.MusicSettings;

/**
 * Coordinates async fetch/convert, playback, a preloaded upcoming queue, and playlists.
 * Temp WAVs live under the game dir cache and are deleted when finished/discarded.
 */
public final class MusicController {
	public static final MusicController INSTANCE = new MusicController();

	private static final int MAX_QUEUE = 25;
	private static final int PRELOAD_AHEAD = 2;

	private final AudioPlayer player = new AudioPlayer();
	private final YtDlpService ytDlp = new YtDlpService();
	private final LocalFileService localFiles = new LocalFileService();
	private final ExecutorService playExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "pocketwave-play");
		t.setDaemon(true);
		return t;
	});
	private final ExecutorService preloadExecutor = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "pocketwave-preload");
		t.setDaemon(true);
		return t;
	});

	private final AtomicInteger jobEpoch = new AtomicInteger();
	private final Deque<QueuedItem> queue = new ArrayDeque<>();
	private Future<?> currentJob;
	private Path currentWorkDir;
	private String currentTitle;
	private QueuedItem.Kind currentKind;
	private String currentValue;
	private volatile boolean loading;

	private String activePlaylistName;
	private int playlistIndex = -1;
	private final List<QueuedItem> playlistPreloads = new ArrayList<>();
	private int consecutivePlaylistFailures;

	private MusicController() {
	}

	public void applySavedVolume() {
		player.setVolume(MusicSettings.INSTANCE.volumePercent() / 100f);
	}

	public void playQuery(String queryOrUrl) {
		String trimmed = queryOrUrl.trim();
		if (trimmed.isEmpty()) {
			ChatFeedback.error("Usage: /music play <url|search terms>");
			return;
		}
		clearPlaylistSession();
		setCurrentSource(QueuedItem.Kind.QUERY, trimmed);
		startJob("Fetching audio...", status -> ytDlp.prepare(trimmed, status));
	}

	public void playLocal(String path) {
		String trimmed = path.trim();
		if (trimmed.isEmpty()) {
			ChatFeedback.error("Usage: /music local <path>");
			return;
		}
		clearPlaylistSession();
		setCurrentSource(QueuedItem.Kind.LOCAL, trimmed);
		startJob("Loading local file...", status -> localFiles.prepare(trimmed, status));
	}

	public void playPlaylist(String name) {
		playPlaylist(name, 0);
	}

	public void playPlaylist(String name, int startIndex) {
		Playlist playlist = PlaylistStore.INSTANCE.find(name);
		if (playlist == null) {
			ChatFeedback.error("No playlist named \"" + name.trim() + "\".");
			return;
		}
		if (playlist.isEmpty()) {
			ChatFeedback.error("That playlist is empty.");
			return;
		}

		int index = Math.max(0, Math.min(startIndex, playlist.songs().size() - 1));
		discardQueue();
		clearPlaylistSession();
		player.setLooping(false);
		activePlaylistName = playlist.name();
		consecutivePlaylistFailures = 0;
		ChatFeedback.info("Playing playlist: " + playlist.name());
		playPlaylistIndex(index);
	}

	public PlaylistSong currentSongSnapshot() {
		if (currentValue == null || currentValue.isBlank()) {
			return null;
		}
		PlaylistSong.Kind kind = currentKind == QueuedItem.Kind.LOCAL
				? PlaylistSong.Kind.LOCAL
				: PlaylistSong.Kind.QUERY;
		return new PlaylistSong(kind, currentValue, currentTitle);
	}

	public void queueQuery(String queryOrUrl) {
		String trimmed = queryOrUrl.trim();
		if (trimmed.isEmpty()) {
			ChatFeedback.error("Usage: /music queue <url|search terms>");
			return;
		}

		if (!loading && !player.hasTrack()) {
			playQuery(trimmed);
			return;
		}

		QueuedItem item;
		synchronized (queue) {
			if (queue.size() >= MAX_QUEUE) {
				ChatFeedback.error("Queue is full (" + MAX_QUEUE + ").");
				return;
			}
			item = new QueuedItem(QueuedItem.Kind.QUERY, trimmed);
			queue.addLast(item);
			ChatFeedback.info("Queued (#" + queue.size() + "): " + trimmed);
		}
		startPreload(item);
	}

	public void showQueue() {
		List<QueuedItem> snapshot;
		synchronized (queue) {
			snapshot = new ArrayList<>(queue);
		}

		if (snapshot.isEmpty()) {
			ChatFeedback.info("Queue is empty.");
			return;
		}

		ChatFeedback.info("Queue (" + snapshot.size() + "):");
		for (int i = 0; i < snapshot.size(); i++) {
			ChatFeedback.info("  " + (i + 1) + ". " + snapshot.get(i).display());
		}
	}

	public void clearQueue() {
		discardQueue();
		armNextClip();
		ChatFeedback.info("Queue cleared.");
	}

	public int queueSize() {
		synchronized (queue) {
			return queue.size();
		}
	}

	/** Called each client tick to advance when a track ends. */
	public void tick() {
		if (loading) {
			return;
		}
		if (player.hasFinished()) {
			onTrackEnded();
		}
	}

	private void onTrackEnded() {
		Path oldDir = currentWorkDir;
		currentWorkDir = null;
		currentTitle = null;
		if (!playNext()) {
			player.stop();
			clearCurrentSource();
		}
		YtDlpService.deleteQuietly(oldDir);
	}

	private boolean playNext() {
		QueuedItem next;
		synchronized (queue) {
			next = queue.pollFirst();
		}
		if (next != null) {
			setCurrentSource(next.kind(), next.value());
			YtDlpService.PreparedTrack ready = next.takePrepared();
			if (ready != null) {
				ChatFeedback.info("Playing next: " + ready.title());
				playPrepared(ready);
				return true;
			}
			startJob("Loading next...", status -> next.awaitOrFetch(ytDlp, localFiles));
			return true;
		}

		return playNextFromPlaylist();
	}

	private boolean playNextFromPlaylist() {
		if (activePlaylistName == null) {
			return false;
		}

		Playlist playlist = PlaylistStore.INSTANCE.find(activePlaylistName);
		if (playlist == null || playlist.isEmpty()) {
			clearPlaylistSession();
			return false;
		}

		int next = playlistIndex + 1;
		if (next >= playlist.songs().size()) {
			if (playlist.loop()) {
				next = 0;
			} else {
				clearPlaylistSession();
				return false;
			}
		}

		playPlaylistIndex(next);
		return true;
	}

	private void playPlaylistIndex(int index) {
		Playlist playlist = PlaylistStore.INSTANCE.find(activePlaylistName);
		if (playlist == null || playlist.isEmpty()) {
			clearPlaylistSession();
			clearCurrentSource();
			return;
		}

		int clamped = Math.max(0, Math.min(index, playlist.songs().size() - 1));
		playlistIndex = clamped;
		PlaylistSong song = playlist.songs().get(clamped);
		setCurrentSource(kindOf(song), song.value());

		QueuedItem readyItem = takeMatchingPreload(song);
		if (readyItem != null) {
			YtDlpService.PreparedTrack ready = readyItem.takePrepared();
			if (ready != null) {
				ChatFeedback.info("Playing next: " + ready.title());
				playPrepared(ready);
				preloadPlaylistAhead();
				return;
			}
			startJob("Loading next...", status -> readyItem.awaitOrFetch(ytDlp, localFiles));
			preloadPlaylistAhead();
			return;
		}

		startJob("Fetching audio...", status -> prepareSong(song, status));
		preloadPlaylistAhead();
	}

	private QueuedItem takeMatchingPreload(PlaylistSong song) {
		for (int i = 0; i < playlistPreloads.size(); i++) {
			QueuedItem item = playlistPreloads.get(i);
			if (item.matches(song)) {
				playlistPreloads.remove(i);
				return item;
			}
		}
		return null;
	}

	private void preloadPlaylistAhead() {
		if (activePlaylistName == null) {
			discardPlaylistPreloads();
			return;
		}
		Playlist playlist = PlaylistStore.INSTANCE.find(activePlaylistName);
		if (playlist == null || playlist.isEmpty()) {
			discardPlaylistPreloads();
			return;
		}

		List<PlaylistSong> wanted = upcomingPlaylistSongs(playlist);
		List<QueuedItem> kept = new ArrayList<>();
		for (PlaylistSong song : wanted) {
			QueuedItem existing = takeMatchingPreload(song);
			if (existing != null) {
				kept.add(existing);
			} else {
				QueuedItem item = new QueuedItem(kindOf(song), song.value());
				kept.add(item);
				startPreload(item);
			}
		}
		for (QueuedItem leftover : playlistPreloads) {
			YtDlpService.PreparedTrack track = leftover.peekPrepared();
			if (track != null) {
				player.discardNextIf(track.wavFile());
			}
			leftover.discard();
		}
		playlistPreloads.clear();
		playlistPreloads.addAll(kept);
	}

	private List<PlaylistSong> upcomingPlaylistSongs(Playlist playlist) {
		List<PlaylistSong> songs = playlist.songs();
		int size = songs.size();
		List<PlaylistSong> wanted = new ArrayList<>();
		if (size == 0) {
			return wanted;
		}

		int step = 1;
		while (wanted.size() < PRELOAD_AHEAD && step <= size) {
			int index = playlistIndex + step;
			if (index >= size) {
				if (!playlist.loop()) {
					break;
				}
				index = index % size;
			}
			if (index == playlistIndex && size > 1) {
				break;
			}
			wanted.add(songs.get(index));
			if (index == playlistIndex) {
				break;
			}
			step++;
		}
		return wanted;
	}

	private void discardPlaylistPreloads() {
		for (QueuedItem item : playlistPreloads) {
			YtDlpService.PreparedTrack track = item.peekPrepared();
			if (track != null) {
				player.discardNextIf(track.wavFile());
			}
			item.discard();
		}
		playlistPreloads.clear();
	}

	private void playPrepared(YtDlpService.PreparedTrack track) {
		jobEpoch.incrementAndGet();
		cancelJob();

		Path oldDir = currentWorkDir;
		currentWorkDir = null;

		try {
			player.play(track.wavFile());
			currentWorkDir = track.workDir();
			currentTitle = track.title();
			loading = false;
			consecutivePlaylistFailures = 0;
			rememberPlaylistTitle(track.title());
		} catch (Exception e) {
			loading = false;
			currentTitle = null;
			YtDlpService.deleteQuietly(track.workDir());
			YtDlpService.deleteQuietly(oldDir);
			PocketWave.LOGGER.error("Failed to play preloaded track", e);
			ChatFeedback.error(ChatFeedback.friendlyError(e.getMessage()));
			onPlaylistPlayFailed();
			playNext();
			return;
		}

		YtDlpService.deleteQuietly(oldDir);
		armNextClip();
	}

	private void startPreload(QueuedItem item) {
		item.preloadFuture = preloadExecutor.submit(() -> {
			try {
				YtDlpService.PreparedTrack track = switch (item.kind()) {
					case QUERY -> ytDlp.prepare(item.value(), msg -> {
					});
					case LOCAL -> localFiles.prepare(item.value(), msg -> {
					});
				};
				item.complete(track);
				armNextClip();
			} catch (Exception e) {
				item.fail(e.getMessage());
				PocketWave.LOGGER.warn("Preload failed for {}: {}", item.value(), e.toString());
			}
		});
	}

	private void startJob(String startMessage, TrackSupplier supplier) {
		int epoch = jobEpoch.incrementAndGet();
		cancelJob();
		player.stop();
		cleanupWorkDir();

		loading = true;
		ChatFeedback.info(startMessage);

		currentJob = playExecutor.submit(() -> {
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
				consecutivePlaylistFailures = 0;
				rememberPlaylistTitle(track.title());
				armNextClip();
				ChatFeedback.info("Now playing: " + track.title());
			} catch (Exception e) {
				if (epoch != jobEpoch.get()) {
					return;
				}
				loading = false;
				currentTitle = null;
				PocketWave.LOGGER.error("Failed to play music", e);
				String message = e.getMessage();
				if ((message == null || message.isBlank()) && e.getCause() != null) {
					message = e.getCause().getMessage();
				}
				ChatFeedback.error(ChatFeedback.friendlyError(message));
				onPlaylistPlayFailed();
				playNext();
			}
		});
	}

	private void rememberPlaylistTitle(String title) {
		if (activePlaylistName == null || title == null || title.isBlank()) {
			return;
		}
		PlaylistStore.INSTANCE.updateSongTitle(activePlaylistName, playlistIndex, title);
	}

	private void onPlaylistPlayFailed() {
		if (activePlaylistName == null) {
			return;
		}
		consecutivePlaylistFailures++;
		Playlist playlist = PlaylistStore.INSTANCE.find(activePlaylistName);
		int size = playlist == null ? 0 : playlist.songs().size();
		if (size > 0 && consecutivePlaylistFailures >= size) {
			ChatFeedback.error("Stopped playlist — every song failed to load.");
			clearPlaylistSession();
		}
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
		clearPlaylistSession();
		clearCurrentSource();
		currentTitle = null;
		loading = false;
		ChatFeedback.info("Stopped.");
	}

	public void toggleLoop() {
		boolean looping = player.toggleLooping();
		ChatFeedback.info(looping ? "Loop enabled." : "Loop disabled.");
	}

	public void volumeUp() {
		player.adjustVolume(MusicSettings.INSTANCE.volumeStep());
		persistVolume();
		ChatFeedback.info("Volume: " + percent() + "%");
	}

	public void volumeDown() {
		player.adjustVolume(-MusicSettings.INSTANCE.volumeStep());
		persistVolume();
		ChatFeedback.info("Volume: " + percent() + "%");
	}

	public void setVolumePercent(int percent) {
		int clamped = Math.max(0, Math.min(100, percent));
		player.setVolume(clamped / 100f);
		persistVolume();
		ChatFeedback.info("Volume: " + clamped + "%");
	}

	private void persistVolume() {
		MusicSettings.INSTANCE.setVolumePercent(percent());
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
		String loop = player.isLooping() ? ", loop on" : "";
		int queued = queueSize();
		String queuedLabel = queued > 0 ? ", +" + queued + " queued" : "";
		String playlist = activePlaylistName == null ? "" : ", playlist " + activePlaylistName;
		ChatFeedback.info(state + ": " + title + " (" + percent() + "%" + loop + queuedLabel + playlist + ")");
	}

	/** Visible while a track is loaded (playing or paused) or still fetching. */
	public boolean shouldShowHud() {
		return loading || player.hasTrack();
	}

	public boolean isPaused() {
		return player.isPaused();
	}

	public boolean isLooping() {
		return player.isLooping();
	}

	public int getVolumePercent() {
		return percent();
	}

	public String hudTitle() {
		if (loading) {
			return "Loading...";
		}
		return currentTitle == null ? "Unknown" : currentTitle;
	}

	public void shutdown() {
		jobEpoch.incrementAndGet();
		cancelJob();
		player.stop();
		cleanupWorkDir();
		clearPlaylistSession();
		clearCurrentSource();
		discardQueue();
		playExecutor.shutdownNow();
		preloadExecutor.shutdownNow();
	}

	private void discardQueue() {
		player.discardNext();
		List<QueuedItem> discarded;
		synchronized (queue) {
			discarded = new ArrayList<>(queue);
			queue.clear();
		}
		for (QueuedItem item : discarded) {
			item.discard();
		}
	}

	/**
	 * Decode the immediate next WAV into the player (queue head, else playlist next).
	 * Only one extra clip is kept in memory.
	 */
	private void armNextClip() {
		QueuedItem next = peekNextItem();
		if (next == null) {
			player.discardNext();
			return;
		}
		YtDlpService.PreparedTrack track = next.peekPrepared();
		if (track == null || track.wavFile() == null) {
			player.discardNext();
			return;
		}
		player.prepareNext(track.wavFile());
	}

	private QueuedItem peekNextItem() {
		synchronized (queue) {
			QueuedItem head = queue.peekFirst();
			if (head != null) {
				return head;
			}
		}
		return playlistPreloads.isEmpty() ? null : playlistPreloads.get(0);
	}

	private void clearPlaylistSession() {
		activePlaylistName = null;
		playlistIndex = -1;
		consecutivePlaylistFailures = 0;
		discardPlaylistPreloads();
	}

	private void setCurrentSource(QueuedItem.Kind kind, String value) {
		currentKind = kind;
		currentValue = value;
	}

	private void clearCurrentSource() {
		currentKind = null;
		currentValue = null;
	}

	private static QueuedItem.Kind kindOf(PlaylistSong song) {
		return song.kind() == PlaylistSong.Kind.LOCAL ? QueuedItem.Kind.LOCAL : QueuedItem.Kind.QUERY;
	}

	private YtDlpService.PreparedTrack prepareSong(PlaylistSong song, java.util.function.Consumer<String> status) throws Exception {
		return switch (song.kind()) {
			case QUERY -> ytDlp.prepare(song.value(), status);
			case LOCAL -> localFiles.prepare(song.value(), status);
		};
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

	private static final class QueuedItem {
		enum Kind {
			QUERY,
			LOCAL
		}

		private final Kind kind;
		private final String value;
		private final Object lock = new Object();
		private Future<?> preloadFuture;
		private YtDlpService.PreparedTrack prepared;
		private String error;
		private boolean done;

		QueuedItem(Kind kind, String value) {
			this.kind = kind;
			this.value = value;
		}

		Kind kind() {
			return kind;
		}

		String value() {
			return value;
		}

		boolean matches(PlaylistSong song) {
			return kindOf(song) == kind && song.value().equals(value);
		}

		String display() {
			synchronized (lock) {
				if (prepared != null) {
					return prepared.title() + " (ready)";
				}
				if (error != null) {
					return value + " (failed)";
				}
				if (preloadFuture != null && !done) {
					return value + " (loading)";
				}
				return value;
			}
		}

		void complete(YtDlpService.PreparedTrack track) {
			synchronized (lock) {
				prepared = track;
				done = true;
				lock.notifyAll();
			}
		}

		YtDlpService.PreparedTrack peekPrepared() {
			synchronized (lock) {
				return prepared;
			}
		}

		void fail(String message) {
			synchronized (lock) {
				error = message == null ? "error" : message;
				done = true;
				lock.notifyAll();
			}
		}

		YtDlpService.PreparedTrack takePrepared() {
			synchronized (lock) {
				YtDlpService.PreparedTrack track = prepared;
				prepared = null;
				return track;
			}
		}

		/**
		 * Waits for an in-flight preload, or fetches now if preload never started / failed.
		 */
		YtDlpService.PreparedTrack awaitOrFetch(YtDlpService ytDlp, LocalFileService localFiles) throws Exception {
			synchronized (lock) {
				while (!done && preloadFuture != null) {
					lock.wait(200);
				}
				if (prepared != null) {
					YtDlpService.PreparedTrack track = prepared;
					prepared = null;
					return track;
				}
			}

			return switch (kind) {
				case QUERY -> ytDlp.prepare(value, msg -> {
				});
				case LOCAL -> localFiles.prepare(value, msg -> {
				});
			};
		}

		void discard() {
			Future<?> future;
			YtDlpService.PreparedTrack track;
			synchronized (lock) {
				future = preloadFuture;
				track = prepared;
				prepared = null;
				done = true;
				lock.notifyAll();
			}
			if (future != null) {
				future.cancel(true);
			}
			if (track != null) {
				YtDlpService.deleteQuietly(track.workDir());
			}
		}
	}

	@FunctionalInterface
	private interface TrackSupplier {
		YtDlpService.PreparedTrack get(java.util.function.Consumer<String> status) throws Exception;
	}
}
