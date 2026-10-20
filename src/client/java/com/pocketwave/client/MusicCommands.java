package com.pocketwave.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

final class MusicCommands {
	private MusicCommands() {
	}

	static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("music");

			root.then(ClientCommandManager.literal("play")
					.then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
							.executes(ctx -> {
								String query = StringArgumentType.getString(ctx, "query");
								MusicController.INSTANCE.playQuery(query);
								return 1;
							})));

			root.then(ClientCommandManager.literal("local")
					.then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
							.executes(ctx -> {
								String path = StringArgumentType.getString(ctx, "path");
								MusicController.INSTANCE.playLocal(path);
								return 1;
							})));

			root.then(ClientCommandManager.literal("pause")
					.executes(ctx -> {
						MusicController.INSTANCE.pause();
						return 1;
					}));

			root.then(ClientCommandManager.literal("resume")
					.executes(ctx -> {
						MusicController.INSTANCE.resume();
						return 1;
					}));

			root.then(ClientCommandManager.literal("stop")
					.executes(ctx -> {
						MusicController.INSTANCE.stop();
						return 1;
					}));

			root.then(ClientCommandManager.literal("loop")
					.executes(ctx -> {
						MusicController.INSTANCE.toggleLoop();
						return 1;
					}));

			root.then(ClientCommandManager.literal("queue")
					.executes(ctx -> {
						MusicController.INSTANCE.showQueue();
						return 1;
					})
					.then(ClientCommandManager.literal("clear")
							.executes(ctx -> {
								MusicController.INSTANCE.clearQueue();
								return 1;
							}))
					.then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
							.executes(ctx -> {
								String query = StringArgumentType.getString(ctx, "query");
								MusicController.INSTANCE.queueQuery(query);
								return 1;
							})));

			root.then(ClientCommandManager.literal("volup")
					.executes(ctx -> {
						MusicController.INSTANCE.volumeUp();
						return 1;
					}));

			root.then(ClientCommandManager.literal("voldown")
					.executes(ctx -> {
						MusicController.INSTANCE.volumeDown();
						return 1;
					}));

			root.then(ClientCommandManager.literal("volume")
					.then(ClientCommandManager.argument("percent", IntegerArgumentType.integer(0, 100))
							.executes(ctx -> {
								int percent = IntegerArgumentType.getInteger(ctx, "percent");
								MusicController.INSTANCE.setVolumePercent(percent);
								return 1;
							})));

			root.then(ClientCommandManager.literal("now")
					.executes(ctx -> {
						MusicController.INSTANCE.nowPlaying();
						return 1;
					}));

			dispatcher.register(root);
		});
	}
}
