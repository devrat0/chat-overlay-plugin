package com.chatoverlay;

import java.awt.image.BufferedImage;
import net.runelite.api.IndexedSprite;

/**
 * Utility class to convert client IndexedSprites to BufferedImages.
 */
public class PlayerIconLoader
{
	private PlayerIconLoader() {}

	// ── Private helpers ───────────────────────────────────────────────────────

	public static BufferedImage toBufferedImage(IndexedSprite sprite)
	{
		int w     = sprite.getWidth();
		int h     = sprite.getHeight();
		int origW = sprite.getOriginalWidth();
		int origH = sprite.getOriginalHeight();
		int ox    = sprite.getOffsetX();
		int oy    = sprite.getOffsetY();
		int[] palette = sprite.getPalette();
		byte[] pixels = sprite.getPixels();

		BufferedImage img = new BufferedImage(origW, origH, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				int idx = pixels[y * w + x] & 0xFF;
				if (idx == 0)
				{
					continue; // transparent
				}
				img.setRGB(x + ox, y + oy, 0xFF000000 | palette[idx]);
			}
		}
		return img;
	}
}
