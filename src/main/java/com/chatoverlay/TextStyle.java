package com.chatoverlay;

public enum TextStyle
{
	SHADOW("Shadow"),
	OUTLINE("Outline"),
	OUTLINE_SHADOW("Outline Shadow"),
	SHADOW_BOLD("Shadow Bold");

	private final String displayName;

	TextStyle(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
