package com.pocketwave.client.playlist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

import com.pocketwave.client.ChatFeedback;

final class PlaylistListScreen extends Screen {
	private int panelW;
	private int panelH;
	private int panelLeft;
	private int panelTop;

	private PlaylistNameList list;
	private EditBox nameBox;

	PlaylistListScreen() {
		super(Component.literal("Playlists"));
	}

	@Override
	protected void init() {
		this.panelW = Math.min(380, Math.max(300, this.width - 48));
		this.panelH = Math.min(260, Math.max(210, this.height - 36));
		this.panelLeft = (this.width - this.panelW) / 2;
		this.panelTop = (this.height - this.panelH) / 2;

		int pad = 10;
		int inner = this.panelW - pad * 2;
		int listH = this.panelH - 86;

		addRenderableWidget(new StringWidget(this.panelLeft + pad, this.panelTop + 8, inner, 12, this.title, this.font));

		this.nameBox = new EditBox(this.font, this.panelLeft + pad, this.panelTop + 26, inner - 64, 20, Component.literal("Playlist name"));
		this.nameBox.setHint(Component.literal("New playlist"));
		this.nameBox.setMaxLength(40);
		addRenderableWidget(this.nameBox);
		addRenderableWidget(Button.builder(Component.literal("Create"), button -> createPlaylist())
				.bounds(this.panelLeft + pad + inner - 60, this.panelTop + 26, 60, 20)
				.build());

		this.list = new PlaylistNameList(this.minecraft, inner, listH);
		this.list.updateSizeAndPosition(inner, listH, this.panelLeft + pad, this.panelTop + 52);
		addRenderableWidget(this.list);
		this.list.refresh();

		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(this.panelLeft + (this.panelW - 120) / 2, this.panelTop + this.panelH - 30, 120, 20)
				.build());
		setInitialFocus(this.nameBox);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.extractBlurredBackground(graphics);
		this.extractMenuBackground(graphics, this.panelLeft, this.panelTop, this.panelW, this.panelH);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.nameBox != null && this.nameBox.isFocused() && event.isConfirmation()) {
			createPlaylist();
			return true;
		}
		return super.keyPressed(event);
	}

	private void createPlaylist() {
		String raw = this.nameBox == null ? "" : this.nameBox.getValue();
		Playlist created = PlaylistStore.INSTANCE.create(raw);
		if (created == null) {
			createFailed(raw);
			return;
		}
		this.nameBox.setValue("");
		this.minecraft.gui.setScreen(new PlaylistDetailScreen(created.name()));
	}

	private void createFailed(String raw) {
		String name = PlaylistStore.sanitizeName(raw);
		if (name.isEmpty()) {
			ChatFeedback.error("Enter a playlist name.");
			return;
		}
		ChatFeedback.error("A playlist named \"" + name + "\" already exists.");
	}

	private void openPlaylist(String name) {
		this.minecraft.gui.setScreen(new PlaylistDetailScreen(name));
	}

	private final class PlaylistNameList extends ObjectSelectionList<PlaylistNameList.Entry> {
		private final int rowWidth;

		PlaylistNameList(Minecraft minecraft, int rowWidth, int height) {
			super(minecraft, rowWidth, height, 0, 20);
			this.rowWidth = rowWidth;
		}

		void refresh() {
			clearEntries();
			for (Playlist playlist : PlaylistStore.INSTANCE.list()) {
				addEntry(new Entry(playlist));
			}
		}

		@Override
		public int getRowWidth() {
			return this.rowWidth - 6;
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			private final Playlist playlist;

			Entry(Playlist playlist) {
				this.playlist = playlist;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovering, float tickDelta) {
				Font font = PlaylistListScreen.this.font;
				String label = playlist.name() + " (" + playlist.songs().size() + ")";
				int maxWidth = Math.max(8, getContentWidth() - 8);
				if (font.width(label) > maxWidth) {
					while (label.length() > 1 && font.width(label + "...") > maxWidth) {
						label = label.substring(0, label.length() - 1);
					}
					label = label + "...";
				}
				graphics.text(font, label, getContentX() + 4, getContentYMiddle() - font.lineHeight / 2, CommonColors.WHITE, true);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				PlaylistNameList.this.setSelected(this);
				PlaylistListScreen.this.openPlaylist(this.playlist.name());
				return true;
			}

			@Override
			public Component getNarration() {
				return Component.literal(this.playlist.name());
			}
		}
	}
}
