package com.pocketwave.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.pocketwave.PocketWave;
import com.pocketwave.client.settings.MusicSettings;

/**
 * Tiny top-left now-playing widget — short title, XP-styled volume bar, status.
 * Kept out of the center/bottom so it stays out of a PVP sightline.
 */
final class MusicHud {
	private static final int MARGIN_X = 6;
	private static final int MARGIN_Y = 14;
	/** Compact fixed content width — long titles are ellipsized to fit. */
	private static final int CONTENT_WIDTH = 96;
	private static final int BAR_HEIGHT = 5;
	private static final int BAR_GAP = 3;
	private static final int STATUS_GAP = 2;
	private static final int PAD = 4;

	private static final Identifier XP_BAR_BACKGROUND = Identifier.withDefaultNamespace("hud/experience_bar_background");
	private static final Identifier XP_BAR_PROGRESS = Identifier.withDefaultNamespace("hud/experience_bar_progress");

	private static final int SCRIM = 0x66000000;
	private static final int TITLE_COLOR = CommonColors.WHITE;
	private static final int PAUSED_COLOR = 0xFFB0B0B0;
	private static final int STATUS_COLOR = 0xFFA8E6A0;
	private static final int STATUS_DIM = 0xFF9A9A9A;

	private static String cachedTitleSource;
	private static String cachedLabel = "";
	private static String cachedStatus = "";
	private static boolean cachedPaused;
	private static boolean cachedLooping;
	private static int cachedQueued = -1;

	private MusicHud() {
	}

	static void register() {
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.MISC_OVERLAYS,
				PocketWave.id("now_playing"),
				MusicHud::extract
		);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		MusicController music = MusicController.INSTANCE;
		if (!MusicSettings.INSTANCE.showHud() || !music.shouldShowHud()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		boolean paused = music.isPaused();
		boolean looping = music.isLooping();
		int queued = music.queueSize();
		String title = music.hudTitle();
		if (cachedTitleSource == null || !cachedTitleSource.equals(title)
				|| cachedPaused != paused || cachedLooping != looping || cachedQueued != queued) {
			cachedTitleSource = title;
			cachedPaused = paused;
			cachedLooping = looping;
			cachedQueued = queued;
			cachedLabel = ellipsize(font, title, CONTENT_WIDTH);
			cachedStatus = statusLine(paused, looping, queued);
		}
		String status = cachedStatus;
		boolean showStatus = !status.isEmpty();
		String label = cachedLabel;

		int x = MARGIN_X;
		int y = MARGIN_Y;
		int boxBottom = y + PAD + font.lineHeight + BAR_GAP + BAR_HEIGHT
				+ (showStatus ? STATUS_GAP + font.lineHeight : 0) + PAD;
		int boxRight = x + CONTENT_WIDTH + PAD * 2;

		graphics.fill(x, y, boxRight, boxBottom, SCRIM);

		int textX = x + PAD;
		int textY = y + PAD;
		graphics.text(font, label, textX, textY, paused ? PAUSED_COLOR : TITLE_COLOR, true);

		int barX = textX;
		int barY = textY + font.lineHeight + BAR_GAP;
		drawXpVolumeBar(graphics, barX, barY, CONTENT_WIDTH, music.getVolumePercent());

		if (showStatus) {
			int statusY = barY + BAR_HEIGHT + STATUS_GAP;
			String statusLabel = ellipsize(font, status, CONTENT_WIDTH);
			graphics.text(font, statusLabel, textX, statusY, looping || paused ? STATUS_COLOR : STATUS_DIM, true);
		}
	}

	/**
	 * Stretch the full XP sprites so both end caps stay rounded.
	 * Partial volume scissors the stretched progress (flat mid cut, like vanilla XP).
	 */
	private static void drawXpVolumeBar(GuiGraphicsExtractor graphics, int x, int y, int barWidth, int volumePercent) {
		int fillWidth = Math.round(barWidth * (Math.max(0, Math.min(100, volumePercent)) / 100f));

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_BACKGROUND, x, y, barWidth, BAR_HEIGHT);

		if (fillWidth <= 0) {
			return;
		}

		if (fillWidth >= barWidth) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_PROGRESS, x, y, barWidth, BAR_HEIGHT);
			return;
		}

		graphics.enableScissor(x, y, x + fillWidth, y + BAR_HEIGHT);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_PROGRESS, x, y, barWidth, BAR_HEIGHT);
		graphics.disableScissor();
	}

	private static String statusLine(boolean paused, boolean looping, int queued) {
		StringBuilder status = new StringBuilder();
		if (paused) {
			status.append("Paused");
		}
		if (looping) {
			if (!status.isEmpty()) {
				status.append("  ·  ");
			}
			status.append("Loop");
		}
		if (queued > 0) {
			if (!status.isEmpty()) {
				status.append("  ·  ");
			}
			status.append("+").append(queued);
		}
		return status.toString();
	}

	private static String ellipsize(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}

		String ellipsis = "...";
		int budget = maxWidth - font.width(ellipsis);
		if (budget <= 0) {
			return ellipsis;
		}

		int low = 0;
		int high = text.length();
		while (low < high) {
			int mid = (low + high + 1) / 2;
			if (font.width(text.substring(0, mid)) <= budget) {
				low = mid;
			} else {
				high = mid - 1;
			}
		}
		return text.substring(0, low) + ellipsis;
	}
}
