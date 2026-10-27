package com.pocketwave.client.audio;

import java.io.IOException;
import java.nio.file.Path;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
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

	private Clip clip;
	private Clip nextClip;
	private Path nextWav;
	private float volume = 1.0f;
	private boolean paused;
	private boolean looping;

	public synchronized void play(Path wavFile) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		if (promoteNext(wavFile)) {
			return;
		}

		discardNext();
		stopInternal(false);

		this.clip = openClip(wavFile);
		applyVolumeTo(this.clip);
		startPlayback();
		paused = false;
	}

	/**
	 * Decode {@code wavFile} into a silent clip so {@link #play(Path)} can start it
	 * immediately. Safe to call from a background thread.
	 */
	public synchronized void prepareNext(Path wavFile) {
		if (wavFile == null) {
			discardNext();
			return;
		}

		Path normalized = wavFile.toAbsolutePath().normalize();
		if (nextClip != null && normalized.equals(nextWav)) {
			return;
		}

		discardNext();
		try {
			nextClip = openClip(normalized);
			nextWav = normalized;
			applyVolumeTo(nextClip);
		} catch (Exception e) {
			discardNext();
			PocketWave.LOGGER.debug("Could not pre-open next clip for {}", wavFile, e);
		}
	}

	public synchronized void discardNext() {
		closeClip(nextClip);
		nextClip = null;
		nextWav = null;
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
			clip.stop();
			paused = true;
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
		applyVolumeTo(clip);
		startPlayback();
		paused = false;
		return true;
	}

	private void stopInternal(boolean clearPaused) {
		closeClip(clip);
		clip = null;

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

	private static Clip openClip(Path wavFile) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
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
			return newClip;
		}
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
		if (clip.isRunning()) {
			return false;
		}
		long length = clip.getFrameLength();
		if (length <= 0) {
			return false;
		}
		return clip.getFramePosition() >= length - 1;
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
