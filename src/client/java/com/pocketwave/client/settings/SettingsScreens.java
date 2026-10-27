package com.pocketwave.client.settings;

import net.minecraft.client.Minecraft;

public final class SettingsScreens {
	private SettingsScreens() {
	}

	public static void open() {
		Minecraft client = Minecraft.getInstance();
		client.schedule(() -> client.gui.setScreen(new SettingsScreen()));
	}
}
