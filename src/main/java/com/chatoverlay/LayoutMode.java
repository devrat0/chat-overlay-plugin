package com.chatoverlay;

/**
 * Directional stacking mode for chat overlays.
 */
public enum LayoutMode
{
	TOP_TO_BOTTOM("Top to Bottom"),
	BOTTOM_TO_TOP("Bottom to Top");

	private final String label;

	LayoutMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
