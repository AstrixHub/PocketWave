package com.pocketwave.client.audio;

import java.io.IOException;
import java.nio.file.Path;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.pocketwave.PocketWave;

/**
 * Plays a local WAV via Java Sound, independent of Minecraft's sound engine.
 * The upcoming track can be decoded into a second clip while the current one plays.
 */
public final class AudioPlayer {
	/**
	 * User-facing volume 0–1 (shown as 0–100%). Full scale maps to this fraction of
	 * true output so "100%" matches the old ~10% loudness.
	 */
	private static final float OUTPUT_SCALE = 0.10f;
	/** Decode the next WAV only when the current track is this close to ending. */
	private static final double NEXT_DECODE_SECONDS = 12.0;

	private Clip clip;
	private Clip nextClip;
	private Path nextWav;
	private boolean nextDecodeStarted;
	private float volume = 1.0f;
	private boolean paused;
	private boolean looping;
	private volatile boolean finished;

	public synchronized void play(Path wavFile) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		Path normalized = wavFile.toAbsolutePath().normalize();
		if (promoteNext(normalized)) {
			return;
		}

		discardNext();
		stopInternal(false);

		this.clip = openClip(normalized);
		applyVolumeTo(this.clip);
		finished = false;
		startPlayback();
		paused = false;
	}

	/**
	 * Remember the next WAV so it can be decoded shortly before the current track
	 * ends. Safe to call from a background thread; does not decode immediately.
	 */
	public synchronized void prepareNext(Path wavFile) {
		if (wavFile == null) {
			discardNext();
			return;
		}

		Path normalized = wavFile.toAbsolutePath().normalize();
		if (normalized.equals(nextWav) && (nextClip != null || nextDecodeStarted)) {
			return;
		}

		discardNext();
		nextWav = normalized;
	}

	public synchronized boolean needsNextDecode() {
		if (nextWav == null || nextClip != null || nextDecodeStarted || !shouldDecodeNextNow()) {
			return false;
		}
		nextDecodeStarted = true;
		return true;
	}

	/**
	 * Decode a previously {@link #prepareNext(Path) prepared} WAV. Call from a
	 * background thread so the client thread is not stalled.
	 */
	public void decodePreparedNext() {
		Path path;
		synchronized (this) {
			path = nextWav;
			if (path == null || nextClip != null) {
				nextDecodeStarted = false;
				return;
			}
		}

		Clip decoded = null;
		try {
			decoded = openClip(path);
		} catch (Exception e) {
			PocketWave.LOGGER.debug("Could not pre-open next clip for {}", path, e);
		}

		synchronized (this) {
			if (decoded != null && path.equals(nextWav) && nextClip == null) {
				nextClip = decoded;
				applyVolumeTo(nextClip);
			} else {
				closeClip(decoded);
				if (path.equals(nextWav) && nextClip == null) {
					nextWav = null;
				}
			}
			nextDecodeStarted = false;
		}
	}

	public synchronized void discardNext() {
		closeClip(nextClip);
		nextClip = null;
		nextWav = null;
		nextDecodeStarted = false;
	}

	public synchronized void discardNextIf(Path wavFile) {
		if (wavFile == null || nextWav == null) {
			return;
		}
		if (wavFile.toAbsolutePath().normalize().equals(nextWav)) {
			discardNext();
		}
	}

	public synchronized void pause() {
		if (clip != null && clip.isRunning()) {
			paused = true;
			clip.stop();
		}
	}

	public synchronized void resume() {
		if (clip != null && paused) {
			startPlayback();
			paused = false;
		}
	}

	public synchronized void togglePause() {
		if (clip == null) {
			return;
		}

		if (paused || !clip.isRunning()) {
			resume();
		} else {
			pause();
		}
	}

	public synchronized void stop() {
		discardNext();
		stopInternal(true);
	}

	private boolean promoteNext(Path wavFile) {
		if (nextClip == null || wavFile == null || nextWav == null) {
			return false;
		}
		if (!wavFile.toAbsolutePath().normalize().equals(nextWav)) {
			return false;
		}

		stopInternal(false);
		clip = nextClip;
		nextClip = null;
		nextWav = null;
		nextDecodeStarted = false;
		applyVolumeTo(clip);
		finished = false;
		startPlayback();
		paused = false;
		return true;
	}

	private void stopInternal(boolean clearPaused) {
		finished = false;
		Clip toClose = clip;
		clip = null;
		closeClip(toClose);

		if (clearPaused) {
			paused = false;
		}
	}

	private static void closeClip(Clip toClose) {
		if (toClose == null) {
			return;
		}
		try {
			toClose.stop();
			toClose.flush();
			toClose.close();
		} catch (Exception e) {
			PocketWave.LOGGER.debug("Error closing audio clip", e);
		}
	}

	private Clip openClip(Path wavFile) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		try (AudioInputStream stream = AudioSystem.getAudioInputStream(wavFile.toFile())) {
			AudioFormat format = stream.getFormat();
			AudioInputStream playStream = stream;

			if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
				AudioFormat pcm = new AudioFormat(
						AudioFormat.Encoding.PCM_SIGNED,
						format.getSampleRate(),
						16,
						format.getChannels(),
						format.getChannels() * 2,
						format.getSampleRate(),
						false
				);
				playStream = AudioSystem.getAudioInputStream(pcm, stream);
			}

			Clip newClip = AudioSystem.getClip();
			newClip.open(playStream);
			newClip.addLineListener(this::onLineEvent);
			return newClip;
		}
	}

	private void onLineEvent(LineEvent event) {
		if (event.getType() != LineEvent.Type.STOP) {
			return;
		}
		synchronized (this) {
			if (paused || looping || clip == null || event.getSource() != clip) {
				return;
			}
			long length = clip.getFrameLength();
			if (length > 0 && clip.getFramePosition() >= length - 1) {
				finished = true;
			}
		}
	}

	private boolean shouldDecodeNextNow() {
		if (looping) {
			return false;
		}
		if (clip == null) {
			return true;
		}
		float rate = clip.getFormat().getFrameRate();
		long remaining = clip.getFrameLength() - clip.getFramePosition();
		return rate <= 0 || remaining <= 0 || (remaining / rate) <= NEXT_DECODE_SECONDS;
	}

	public synchronized boolean isPlaying() {
		return clip != null && clip.isRunning();
	}

	public synchronized boolean isPaused() {
		return clip != null && paused;
	}

	public synchronized boolean hasTrack() {
		return clip != null;
	}

	/**
	 * True when the clip reached its natural end (not paused, not looping, not manually stopped).
	 */
	public synchronized boolean hasFinished() {
		if (clip == null || paused || looping) {
			return false;
		}
		if (finished) {
			return true;
		}
		if (clip.isRunning()) {
			return false;
		}
		long length = clip.getFrameLength();
		return length > 0 && clip.getFramePosition() >= length - 1;
	}

	public synchronized boolean isLooping() {
		return looping;
	}

	/**
	 * Enables or disables continuous loop of the current clip.
	 *
	 * @return the new looping state
	 */
	public synchronized boolean setLooping(boolean looping) {
		this.looping = looping;
		applyLooping();
		if (looping && nextClip != null) {
			closeClip(nextClip);
			nextClip = null;
			nextDecodeStarted = false;
		}
		return this.looping;
	}

	public synchronized boolean toggleLooping() {
		return setLooping(!looping);
	}

	public synchronized float getVolume() {
		return volume;
	}

	public synchronized void setVolume(float volume) {
		this.volume = Math.max(0f, Math.min(1f, volume));
		applyVolumeTo(clip);
		applyVolumeTo(nextClip);
	}

	public synchronized void adjustVolume(float delta) {
		setVolume(volume + delta);
	}

	private void startPlayback() {
		if (clip == null) {
			return;
		}
		if (looping) {
			clip.loop(Clip.LOOP_CONTINUOUSLY);
		} else {
			clip.loop(0);
			clip.start();
		}
	}

	private void applyLooping() {
		if (clip == null || paused) {
			return;
		}
		if (looping) {
			clip.loop(Clip.LOOP_CONTINUOUSLY);
		} else {
			clip.loop(0);
		}
	}

	private void applyVolumeTo(Clip target) {
		if (target == null) {
			return;
		}

		try {
			if (target.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				FloatControl gain = (FloatControl) target.getControl(FloatControl.Type.MASTER_GAIN);
				float min = gain.getMinimum();
				float max = gain.getMaximum();

				float actual = volume * OUTPUT_SCALE;
				if (actual <= 0.0001f) {
					gain.setValue(min);
				} else {
					float db = (float) (20.0 * Math.log10(actual));
					gain.setValue(Math.max(min, Math.min(max, db)));
				}
			}
		} catch (IllegalArgumentException e) {
			PocketWave.LOGGER.debug("Could not set volume control", e);
		}
	}
}
