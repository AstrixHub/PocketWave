package com.pocketwave.client.settings;

import com.pocketwave.client.ClientScreens;

public final class SettingsScreens {
	private SettingsScreens() {
	}

	public static void open() {
		ClientScreens.open(new SettingsScreen());
	}
}
