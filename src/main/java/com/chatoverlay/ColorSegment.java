package com.chatoverlay;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * A run of plain text paired with the color it should be drawn in,
 * or an inline image segment.
 * Produced by {@link ColorTagParser}.
 */
public class ColorSegment
{
	private final String text;
	private final Color color;
	private final BufferedImage image;
	private final int imageId;

	public ColorSegment(String text, Color color)
	{
		this.text = text;
		this.color = color;
		this.image = null;
		this.imageId = -1;
	}

	public ColorSegment(BufferedImage image, int imageId)
	{
		this(image, imageId, Color.WHITE);
	}

	public ColorSegment(BufferedImage image, int imageId, Color color)
	{
		this.text = "";
		this.color = color;
		this.image = image;
		this.imageId = imageId;
	}

	public String getText()
	{
		return text;
	}

	public Color getColor()
	{
		return color;
	}

	public BufferedImage getImage()
	{
		return image;
	}

	public int getImageId()
	{
		return imageId;
	}
}
