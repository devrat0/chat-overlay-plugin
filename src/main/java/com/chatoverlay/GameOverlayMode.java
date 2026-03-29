package com.chatoverlay;

/**
 * Display mode for the game overlay.
 *
 * <ul>
 *   <li>{@link #PINNED_TO_PLAYER} — bubbles float above the local player character
 *       (current classic behaviour).</li>
 *   <li>{@link #OVERLAY} — alerts render inside a free-floating panel that the
 *       player can drag anywhere on screen.</li>
 * </ul>
 */
public enum GameOverlayMode
{
	PINNED_TO_PLAYER("Pinned to Player"),
	OVERLAY("Free Overlay");

	private final String label;

	GameOverlayMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
