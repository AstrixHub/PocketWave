package com.pocketwave.client.onboarding;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared layout for PocketWave's setup pages (inspired by Simple Voice Chat).
 */
abstract class OnboardingScreenBase extends Screen {
	protected static final int PAD = 12;
	protected static final int BUTTON_H = 20;

	protected final Screen previous;
	protected int panelW;
	protected int panelH;
	protected int panelLeft;
	protected int panelTop;
	protected int contentWidth;

	OnboardingScreenBase(Component title, Screen previous) {
		super(title);
		this.previous = previous;
	}

	@Override
	protected void init() {
		this.panelW = Math.min(420, Math.max(320, this.width - 40));
		this.panelH = Math.min(260, Math.max(220, this.height - 36));
		this.panelLeft = (this.width - this.panelW) / 2;
		this.panelTop = (this.height - this.panelH) / 2;
		this.contentWidth = this.panelW - PAD * 2;

		addRenderableWidget(new StringWidget(
				this.panelLeft + PAD,
				this.panelTop + 10,
				this.contentWidth,
				12,
				this.title,
				this.font
		));
	}

	protected MultiLineTextWidget addBody(Component text) {
		MultiLineTextWidget body = new MultiLineTextWidget(
				this.panelLeft + PAD,
				this.panelTop + 32,
				text,
				this.font
		);
		body.setMaxWidth(this.contentWidth);
		addRenderableWidget(body);
		return body;
	}

	protected void addBackOrCancelButton() {
		Component label = this.previous instanceof OnboardingScreenBase
				? Component.literal("Back")
				: Component.literal("Cancel");
		addRenderableWidget(Button.builder(label, button -> {
			if (this.previous instanceof OnboardingScreenBase) {
				this.minecraft.gui.setScreen(this.previous);
			} else {
				OnboardingManager.finishOnboarding(this.previous);
			}
		}).bounds(this.panelLeft + PAD, bottomY(), 100, BUTTON_H).build());
	}

	protected void addNextButton(Screen next) {
		addRenderableWidget(Button.builder(Component.literal("Next"), button -> this.minecraft.gui.setScreen(next))
				.bounds(this.panelLeft + this.panelW - PAD - 100, bottomY(), 100, BUTTON_H)
				.build());
	}

	protected void addFinishButton(Component label) {
		addRenderableWidget(Button.builder(label, button -> OnboardingManager.finishOnboarding(this))
				.bounds(this.panelLeft + this.panelW - PAD - 120, bottomY(), 120, BUTTON_H)
				.build());
	}

	protected int bottomY() {
		return this.panelTop + this.panelH - 30;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.extractBlurredBackground(graphics);
		this.extractMenuBackground(graphics, this.panelLeft, this.panelTop, this.panelW, this.panelH);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
