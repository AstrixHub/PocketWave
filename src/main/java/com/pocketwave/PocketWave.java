package com.pocketwave;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PocketWave {
	public static final String MOD_ID = "pocketwave";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private PocketWave() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
