package com.pocketwave.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

final class MusicCommands {
	private MusicCommands() {
	}

	static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("music")
					.requires(FabricClientCommandSource::attended);

			root.then(ClientCommands.literal("play")
					.then(ClientCommands.argument("query", StringArgumentType.greedyString())
							.executes(ctx -> {
								String query = StringArgumentType.getString(ctx, "query");
								MusicController.INSTANCE.playQuery(query);
								return 1;
							})));

			root.then(ClientCommands.literal("local")
					.then(ClientCommands.argument("path", StringArgumentType.greedyString())
							.executes(ctx -> {
								String path = StringArgumentType.getString(ctx, "path");
								MusicController.INSTANCE.playLocal(path);
								return 1;
							})));

			root.then(ClientCommands.literal("pause")
					.executes(ctx -> {
						MusicController.INSTANCE.pause();
						return 1;
					}));

			root.then(ClientCommands.literal("resume")
					.executes(ctx -> {
						MusicController.INSTANCE.resume();
						return 1;
					}));

			root.then(ClientCommands.literal("stop")
					.executes(ctx -> {
						MusicController.INSTANCE.stop();
						return 1;
					}));

			root.then(ClientCommands.literal("loop")
					.executes(ctx -> {
						MusicController.INSTANCE.toggleLoop();
						return 1;
					}));

			root.then(ClientCommands.literal("volup")
					.executes(ctx -> {
						MusicController.INSTANCE.volumeUp();
						return 1;
					}));

			root.then(ClientCommands.literal("voldown")
					.executes(ctx -> {
						MusicController.INSTANCE.volumeDown();
						return 1;
					}));

			root.then(ClientCommands.literal("volume")
					.then(ClientCommands.argument("percent", IntegerArgumentType.integer(0, 100))
							.executes(ctx -> {
								int percent = IntegerArgumentType.getInteger(ctx, "percent");
								MusicController.INSTANCE.setVolumePercent(percent);
								return 1;
							})));

			root.then(ClientCommands.literal("now")
					.executes(ctx -> {
						MusicController.INSTANCE.nowPlaying();
						return 1;
					}));

			dispatcher.register(root);
		});
	}
}
