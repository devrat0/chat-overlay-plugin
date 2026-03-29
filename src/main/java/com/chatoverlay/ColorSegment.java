package com.chatoverlay;

import java.awt.Color;

/**
 * A run of plain text paired with the color it should be drawn in.
 * Produced by {@link ColorTagParser}.
 */
public class ColorSegment
{
	private final String text;
	private final Color color;

	public ColorSegment(String text, Color color)
	{
		this.text = text;
		this.color = color;
	}

	public String getText()
	{
		return text;
	}

	public Color getColor()
	{
		return color;
	}
}
