package com.pocketwave.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.CommonColors;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.pocketwave.PocketWave;

/**
 * Tiny top-left now-playing widget — title + volume bar only.
 * Kept out of the center/bottom so it stays out of a PVP sightline.
 */
final class MusicHud {
	private static final int MARGIN_X = 4;
	private static final int MARGIN_Y = 4;
	private static final int MAX_TITLE_WIDTH = 120;
	private static final int BAR_WIDTH = 72;
	private static final int BAR_HEIGHT = 2;
	private static final int BAR_GAP = 2;

	/** Soft dark scrim so text stays readable without a bulky card. */
	private static final int SCRIM = 0x66000000;
	private static final int BAR_TRACK = 0x55FFFFFF;
	private static final int BAR_FILL = 0xCCE8E8E8;
	private static final int TITLE_COLOR = CommonColors.WHITE;
	private static final int PAUSED_COLOR = 0xFFB0B0B0;

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
		if (!music.shouldShowHud()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		String title = music.hudTitle();
		boolean paused = music.isPaused();

		String label = ellipsize(font, title, MAX_TITLE_WIDTH);
		int textWidth = font.width(label);
		int contentWidth = Math.max(textWidth, BAR_WIDTH);
		int x = MARGIN_X;
		int y = MARGIN_Y;
		int pad = 3;
		int boxRight = x + contentWidth + pad * 2;
		int boxBottom = y + font.lineHeight + BAR_GAP + BAR_HEIGHT + pad;

		graphics.fill(x, y, boxRight, boxBottom, SCRIM);

		int textX = x + pad;
		int textY = y + pad - 1;
		graphics.text(font, label, textX, textY, paused ? PAUSED_COLOR : TITLE_COLOR, true);

		int barX = textX;
		int barY = textY + font.lineHeight + BAR_GAP;
		graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, BAR_TRACK);

		int fillWidth = Math.round(BAR_WIDTH * (music.getVolumePercent() / 100f));
		if (fillWidth > 0) {
			graphics.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, BAR_FILL);
		}
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

		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			builder.append(text.charAt(i));
			if (font.width(builder.toString()) > budget) {
				builder.setLength(Math.max(0, builder.length() - 1));
				break;
			}
		}
		return builder + ellipsis;
	}
}
