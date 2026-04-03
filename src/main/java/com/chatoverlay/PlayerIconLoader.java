package com.chatoverlay;

import java.awt.image.BufferedImage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;

/**
 * Loads icons from {@code <img=N>} tags embedded in sender names
 * and attaches the resulting {@link BufferedImage} to the {@link ChatLine}.
 *
 * <p>Must be called on the client thread (inside an event handler).</p>
 */
@Singleton
public class PlayerIconLoader
{
	private static final Pattern IMG_TAG = Pattern.compile("<img=(\\d+)>");

	private final Client            client;
	private final ChatOverlayConfig config;

	@Inject
	public PlayerIconLoader(Client client, ChatOverlayConfig config)
	{
		this.client = client;
		this.config = config;
	}

	/**
	 * Extracts the first {@code <img=N>} image ID from a raw sender name,
	 * or returns -1 if absent.
	 */
	public int extractIconId(String rawSender)
	{
		if (rawSender == null)
		{
			return -1;
		}
		Matcher m = IMG_TAG.matcher(rawSender);
		if (m.find())
		{
			try
			{
				return Integer.parseInt(m.group(1));
			}
			catch (NumberFormatException e)
			{
				return -1;
			}
		}
		return -1;
	}

	/**
	 * Resolves the icon for the given {@code iconId} and attaches it to {@code line}.
	 * No-op when icons are disabled in config or the id is invalid.
	 */
	public void resolveAndSetIcon(ChatLine line, int iconId)
	{
		if (iconId < 0 || !config.showPlayerIcons())
		{
			return;
		}
		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null || iconId >= modIcons.length)
		{
			return;
		}
		IndexedSprite sprite = modIcons[iconId];
		if (sprite == null)
		{
			return;
		}
		line.setIcon(toBufferedImage(sprite));
	}

	// ── Private helpers ───────────────────────────────────────────────────────

	private static BufferedImage toBufferedImage(IndexedSprite sprite)
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
