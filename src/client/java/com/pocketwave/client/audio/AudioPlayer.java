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
 */
public final class AudioPlayer {
	/**
	 * User-facing volume 0–1 (shown as 0–100%). Full scale maps to this fraction of
	 * true output so "100%" matches the old ~10% loudness.
	 */
	private static final float OUTPUT_SCALE = 0.10f;

	private Clip clip;
	private float volume = 1.0f;
	private boolean paused;

	public synchronized void play(Path wavFile) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		stopInternal(false);

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
			this.clip = newClip;
			applyVolume();
			newClip.start();
			paused = false;
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
			clip.start();
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
		stopInternal(true);
	}

	private void stopInternal(boolean clearPaused) {
		if (clip != null) {
			try {
				clip.stop();
				clip.flush();
				clip.close();
			} catch (Exception e) {
				PocketWave.LOGGER.debug("Error closing audio clip", e);
			}
			clip = null;
		}

		if (clearPaused) {
			paused = false;
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

	public synchronized float getVolume() {
		return volume;
	}

	public synchronized void setVolume(float volume) {
		this.volume = Math.max(0f, Math.min(1f, volume));
		applyVolume();
	}

	public synchronized void adjustVolume(float delta) {
		setVolume(volume + delta);
	}

	private void applyVolume() {
		if (clip == null) {
			return;
		}

		try {
			if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
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
