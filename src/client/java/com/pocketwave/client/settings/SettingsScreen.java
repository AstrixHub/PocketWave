package com.pocketwave.client.settings;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.pocketwave.client.MusicController;

final class SettingsScreen extends Screen {
	private int panelW;
	private int panelH;
	private int panelLeft;
	private int panelTop;

	SettingsScreen() {
		super(Component.literal("PocketWave Settings"));
	}

	@Override
	protected void init() {
		this.panelW = Math.min(320, Math.max(280, this.width - 48));
		this.panelH = 148;
		this.panelLeft = (this.width - this.panelW) / 2;
		this.panelTop = (this.height - this.panelH) / 2;

		int pad = 12;
		int inner = this.panelW - pad * 2;
		int x = this.panelLeft + pad;
		int y = this.panelTop + 10;

		addRenderableWidget(new StringWidget(x, y, inner, 12, this.title, this.font));
		y += 20;

		MusicSettings settings = MusicSettings.INSTANCE;
		addRenderableWidget(new VolumeSlider(x, y, inner, 20, settings.volumePercent() / 100.0));
		y += 24;
		addRenderableWidget(new StepSlider(x, y, inner, 20, (settings.volumeStepPercent() - 1) / 24.0));
		y += 24;
		addRenderableWidget(Checkbox.builder(Component.literal("Show now-playing HUD"), this.font)
				.pos(x, y)
				.selected(settings.showHud())
				.onValueChange((checkbox, selected) -> settings.setShowHud(selected))
				.build());

		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(this.panelLeft + (this.panelW - 120) / 2, this.panelTop + this.panelH - 30, 120, 20)
				.build());
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		this.renderBlurredBackground(graphics);
		this.renderMenuBackground(graphics, this.panelLeft, this.panelTop, this.panelW, this.panelH);
	}

	private static final class VolumeSlider extends AbstractSliderButton {
		VolumeSlider(int x, int y, int width, int height, double value) {
			super(x, y, width, height, Component.empty(), value);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			int percent = (int) Math.round(this.value * 100.0);
			setMessage(Component.literal("Default volume: " + percent + "%"));
		}

		@Override
		protected void applyValue() {
			int percent = (int) Math.round(this.value * 100.0);
			MusicSettings.INSTANCE.setVolumePercent(percent);
			MusicController.INSTANCE.applySavedVolume();
		}
	}

	private static final class StepSlider extends AbstractSliderButton {
		StepSlider(int x, int y, int width, int height, double value) {
			super(x, y, width, height, Component.empty(), value);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal("Volume up/down step: " + stepPercent() + "%"));
		}

		@Override
		protected void applyValue() {
			MusicSettings.INSTANCE.setVolumeStepPercent(stepPercent());
		}

		private int stepPercent() {
			return (int) Math.round(this.value * 24.0) + 1;
		}
	}
}
