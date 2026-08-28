package com.chatoverlay;

public class MacroColor
{
	private final String opaque;
	private final String transparent;

	public MacroColor(String opaque, String transparent)
	{
		this.opaque = opaque;
		this.transparent = transparent;
	}

	public String getOpaque()
	{
		return opaque;
	}

	public String getTransparent()
	{
		return transparent;
	}
}
