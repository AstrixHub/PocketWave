package com.pocketwave.client.playlist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

import com.pocketwave.client.ChatFeedback;
import com.pocketwave.client.MusicController;

final class PlaylistDetailScreen extends Screen {
	private int panelW;
	private int panelH;
	private int panelLeft;
	private int panelTop;

	private final String playlistName;
	private SongList list;
	private EditBox queryBox;
	private Button addCurrentButton;
	private Button moveUpButton;
	private Button moveDownButton;

	PlaylistDetailScreen(String playlistName) {
		super(Component.literal(playlistName));
		this.playlistName = playlistName;
	}

	@Override
	protected void init() {
		Playlist playlist = PlaylistStore.INSTANCE.find(this.playlistName);
		if (playlist == null) {
			this.minecraft.setScreen(new PlaylistListScreen());
			return;
		}

		this.panelW = Math.min(400, Math.max(340, this.width - 48));
		this.panelH = Math.min(280, Math.max(230, this.height - 36));
		this.panelLeft = (this.width - this.panelW) / 2;
		this.panelTop = (this.height - this.panelH) / 2;

		int pad = 10;
		int inner = this.panelW - pad * 2;
		int listH = this.panelH - 128;
		int moveW = 72;
		int listW = inner - moveW - 4;

		addRenderableWidget(new StringWidget(this.panelLeft + pad, this.panelTop + 8, inner, 12, Component.literal(playlist.name()), this.font));
		addRenderableWidget(Checkbox.builder(Component.literal("Loop playlist"), this.font)
				.pos(this.panelLeft + pad, this.panelTop + 24)
				.selected(playlist.loop())
				.onValueChange((checkbox, selected) -> PlaylistStore.INSTANCE.setLoop(this.playlistName, selected))
				.build());

		this.list = new SongList(this.minecraft, listW, listH);
		this.list.updateSizeAndPosition(listW, listH, this.panelLeft + pad, this.panelTop + 46);
		addRenderableWidget(this.list);
		this.list.refresh();

		int moveX = this.panelLeft + pad + listW + 4;
		this.moveUpButton = Button.builder(Component.literal("Move Up"), button -> moveSelected(-1))
				.bounds(moveX, this.panelTop + 46, moveW, 20)
				.build();
		this.moveDownButton = Button.builder(Component.literal("Move Down"), button -> moveSelected(1))
				.bounds(moveX, this.panelTop + 70, moveW, 20)
				.build();
		addRenderableWidget(this.moveUpButton);
		addRenderableWidget(this.moveDownButton);

		int addY = this.panelTop + 46 + listH + 6;
		this.queryBox = new EditBox(this.font, this.panelLeft + pad, addY, inner - 52, 20, Component.literal("Song"));
		this.queryBox.setHint(Component.literal("Song name or URL"));
		this.queryBox.setMaxLength(256);
		addRenderableWidget(this.queryBox);
		addRenderableWidget(Button.builder(Component.literal("Add"), button -> addSong())
				.bounds(this.panelLeft + pad + inner - 48, addY, 48, 20)
				.build());

		int row = this.panelTop + this.panelH - 52;
		this.addCurrentButton = Button.builder(Component.literal("Add current"), button -> addCurrent())
				.bounds(this.panelLeft + pad, row, 100, 20)
				.build();
		addRenderableWidget(this.addCurrentButton);
		addRenderableWidget(Button.builder(Component.literal("Play"), button -> playSelected())
				.bounds(this.panelLeft + pad + 106, row, 60, 20)
				.build());
		addRenderableWidget(Button.builder(Component.literal("Remove"), button -> removeSelected())
				.bounds(this.panelLeft + pad + 172, row, 70, 20)
				.build());

		addRenderableWidget(Button.builder(Component.literal("Delete"), button -> confirmDelete())
				.bounds(this.panelLeft + pad, this.panelTop + this.panelH - 28, 80, 20)
				.build());
		addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
				.bounds(this.panelLeft + this.panelW - pad - 80, this.panelTop + this.panelH - 28, 80, 20)
				.build());

		setInitialFocus(this.queryBox);
		updateButtons();
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		this.renderBlurredBackground(graphics);
		this.renderMenuBackground(graphics, this.panelLeft, this.panelTop, this.panelW, this.panelH);
	}

	@Override
	public void tick() {
		super.tick();
		updateButtons();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.queryBox != null && this.queryBox.isFocused() && event.isConfirmation()) {
			addSong();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(new PlaylistListScreen());
	}

	private void updateButtons() {
		if (this.addCurrentButton != null) {
			this.addCurrentButton.active = MusicController.INSTANCE.currentSongSnapshot() != null;
		}
		int selected = selectedIndex();
		int size = 0;
		Playlist playlist = PlaylistStore.INSTANCE.find(this.playlistName);
		if (playlist != null) {
			size = playlist.songs().size();
		}
		if (this.moveUpButton != null) {
			this.moveUpButton.active = selected > 0;
		}
		if (this.moveDownButton != null) {
			this.moveDownButton.active = selected >= 0 && selected < size - 1;
		}
	}

	private int selectedIndex() {
		if (this.list == null || this.list.getSelected() == null) {
			return -1;
		}
		return this.list.getSelected().index;
	}

	private void moveSelected(int delta) {
		int selected = selectedIndex();
		if (selected < 0) {
			ChatFeedback.error("Select a song to move.");
			return;
		}
		int moved = PlaylistStore.INSTANCE.moveSong(this.playlistName, selected, delta);
		if (moved < 0) {
			return;
		}
		if (this.list != null) {
			this.list.selectIndex(moved);
		}
		updateButtons();
	}

	private void addSong() {
		String query = this.queryBox == null ? "" : this.queryBox.getValue().trim();
		if (query.isEmpty()) {
			ChatFeedback.error("Type a song name or URL to add.");
			return;
		}
		PlaylistSong song = new PlaylistSong(PlaylistSong.Kind.QUERY, query, null);
		if (!PlaylistStore.INSTANCE.addSong(this.playlistName, song)) {
			ChatFeedback.error("Could not add that song.");
			return;
		}
		this.queryBox.setValue("");
		if (this.list != null) {
			this.list.refresh();
		}
	}

	private void addCurrent() {
		PlaylistSong song = MusicController.INSTANCE.currentSongSnapshot();
		if (song == null) {
			ChatFeedback.error("Nothing is playing.");
			return;
		}
		if (!PlaylistStore.INSTANCE.addSong(this.playlistName, song)) {
			ChatFeedback.error("Could not add the current song.");
			return;
		}
		if (this.list != null) {
			this.list.refresh();
		}
	}

	private void playSelected() {
		int index = 0;
		if (this.list != null && this.list.getSelected() != null) {
			index = this.list.getSelected().index;
		}
		MusicController.INSTANCE.playPlaylist(this.playlistName, index);
		this.minecraft.setScreen(null);
	}

	private void removeSelected() {
		if (this.list == null || this.list.getSelected() == null) {
			ChatFeedback.error("Select a song to remove.");
			return;
		}
		int index = this.list.getSelected().index;
		if (!PlaylistStore.INSTANCE.removeSong(this.playlistName, index)) {
			ChatFeedback.error("Could not remove that song.");
			return;
		}
		this.list.refresh();
	}

	private void confirmDelete() {
		this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				PlaylistStore.INSTANCE.delete(this.playlistName);
				this.minecraft.setScreen(new PlaylistListScreen());
			} else {
				this.minecraft.setScreen(new PlaylistDetailScreen(this.playlistName));
			}
		}, Component.literal("Delete playlist?"), Component.literal("\"" + this.playlistName + "\" will be removed.")));
	}

	private final class SongList extends ObjectSelectionList<SongList.Entry> {
		private final int rowWidth;

		SongList(Minecraft minecraft, int rowWidth, int height) {
			super(minecraft, rowWidth, height, 0, 20);
			this.rowWidth = rowWidth;
		}

		void refresh() {
			int selectedIndex = getSelected() == null ? -1 : getSelected().index;
			selectIndex(selectedIndex);
		}

		void selectIndex(int selectedIndex) {
			clearEntries();
			Playlist playlist = PlaylistStore.INSTANCE.find(playlistName);
			if (playlist == null) {
				return;
			}
			int i = 0;
			for (PlaylistSong song : playlist.songs()) {
				Entry entry = new Entry(i, song);
				addEntry(entry);
				if (i == selectedIndex) {
					setSelected(entry);
				}
				i++;
			}
		}

		@Override
		public int getRowWidth() {
			return this.rowWidth - 6;
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			private final int index;
			private final PlaylistSong song;

			Entry(int index, PlaylistSong song) {
				this.index = index;
				this.song = song;
			}

			@Override
			public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float tickDelta) {
				Font font = PlaylistDetailScreen.this.font;
				String label = (this.index + 1) + ". " + this.song.displayName();
				int maxWidth = Math.max(8, getContentWidth() - 8);
				if (font.width(label) > maxWidth) {
					while (label.length() > 1 && font.width(label + "...") > maxWidth) {
						label = label.substring(0, label.length() - 1);
					}
					label = label + "...";
				}
				graphics.drawString(font, label, getContentX() + 4, getContentYMiddle() - font.lineHeight / 2, CommonColors.WHITE, true);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				SongList.this.setSelected(this);
				if (doubleClick) {
					PlaylistDetailScreen.this.playSelected();
				}
				return true;
			}

			@Override
			public Component getNarration() {
				return Component.literal(this.song.displayName());
			}
		}
	}
}
