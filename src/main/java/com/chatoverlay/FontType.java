package com.chatoverlay;

public enum FontType
{
	RUNESCAPE_BOLD("RuneScape Bold"),
	RUNESCAPE("RuneScape"),
	RUNESCAPE_SMALL("RuneScape Small"),
	ARIAL("Arial"),
	DIALOG("Dialog"),
	SANS_SERIF("Sans Serif"),
	SERIF("Serif"),
	MONOSPACED("Monospaced"),
	CUSTOM("Custom / System Font");

	private final String displayName;

	FontType(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
