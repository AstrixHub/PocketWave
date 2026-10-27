package com.pocketwave.client.playlist;

/**
 * One saved playlist entry: the original search/URL/path, plus an optional title
 * filled in after the first successful play.
 */
public final class PlaylistSong {
	public enum Kind {
		QUERY,
		LOCAL
	}

	private final Kind kind;
	private final String value;
	private final String displayTitle;

	public PlaylistSong(Kind kind, String value, String displayTitle) {
		this.kind = kind == null ? Kind.QUERY : kind;
		this.value = value == null ? "" : value;
		this.displayTitle = displayTitle;
	}

	public Kind kind() {
		return kind;
	}

	public String value() {
		return value;
	}

	public String displayTitle() {
		return displayTitle;
	}

	public String displayName() {
		if (displayTitle != null && !displayTitle.isBlank()) {
			return displayTitle;
		}
		return value;
	}

	public PlaylistSong withTitle(String title) {
		return new PlaylistSong(kind, value, title);
	}
}
