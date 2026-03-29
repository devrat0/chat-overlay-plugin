package com.chatoverlay;

public enum FontType
{
	RUNESCAPE_BOLD("RuneScape Bold"),
	RUNESCAPE("RuneScape"),
	RUNESCAPE_SMALL("RuneScape Small");

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
