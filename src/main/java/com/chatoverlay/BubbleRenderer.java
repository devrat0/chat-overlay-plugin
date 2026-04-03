package com.chatoverlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.FontManager;

/**
 * Shared rendering utilities for all chat bubble overlays.
 *
 * <p>All methods are stateless with respect to per-frame render state; callers
 * supply colors, alpha, and positioning so this class has no per-message side effects.</p>
 */
@Singleton
public class BubbleRenderer
{
	public static final int BORDER_RADIUS = 12;
	private static final Color PEEK_AMBER = new Color(255, 200, 0, 220);

	private final ChatOverlayConfig config;

	@Inject
	public BubbleRenderer(ChatOverlayConfig config)
	{
		this.config = config;
	}

	// ── Font ────────────────────────────────────────────────────────────────

	public Font resolveFont()
	{
		Font base;
		switch (config.fontType())
		{
			case RUNESCAPE:       base = FontManager.getRunescapeFont();      break;
			case RUNESCAPE_SMALL: base = FontManager.getRunescapeSmallFont(); break;
			default:              base = FontManager.getRunescapeBoldFont();  break;
		}
		return base.deriveFont((float) config.fontSize());
	}

	// ── Alpha ────────────────────────────────────────────────────────────────

	/**
	 * Standard fade-out only: opaque until 3 s before {@code durMs}, then fades to 0.
	 * Returns 1.0 when {@code durMs} is 0 (infinite).
	 */
	public float computeAlpha(long ageMs, long durMs)
	{
		if (durMs <= 0)
		{
			return 1.0f;
		}
		long fadeWindowMs = Math.min(3000L, durMs);
		long fadeStartMs  = durMs - fadeWindowMs;
		if (ageMs < fadeStartMs)
		{
			return 1.0f;
		}
		return Math.max(0f, 1.0f - (float) (ageMs - fadeStartMs) / fadeWindowMs);
	}

	/**
	 * Fade-in over first 10% of lifetime, fully opaque until 3 s before expiry, then fades out.
	 * Used by GameOverlay.
	 */
	public float computeAlphaWithFadeIn(long ageMs, long durationMs)
	{
		long fadeWindowMs = Math.min(3000L, durationMs);
		long fadeStartMs  = durationMs - fadeWindowMs;
		long fadeInEndMs  = Math.min((long) (durationMs * 0.1f), fadeStartMs);

		if (fadeInEndMs > 0 && ageMs < fadeInEndMs)
		{
			return (float) ageMs / fadeInEndMs;
		}
		if (ageMs < fadeStartMs)
		{
			return 1.0f;
		}
		return Math.max(0f, 1.0f - (float) (ageMs - fadeStartMs) / fadeWindowMs);
	}

	public List<ColorSegment> applyAlphaToSegments(List<ColorSegment> segments, float alpha)
	{
		List<ColorSegment> result = new ArrayList<>(segments.size());
		for (ColorSegment s : segments)
		{
			Color c = s.getColor();
			result.add(new ColorSegment(s.getText(),
				new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (c.getAlpha() * alpha))));
		}
		return result;
	}

	// ── Drawing ──────────────────────────────────────────────────────────────

	/**
	 * Fills a rounded-rect bubble. {@code bg} alpha is scaled by {@code alpha}.
	 */
	public void drawBubble(Graphics2D graphics,
		int x, int y, int width, int height, Color bg, float alpha)
	{
		graphics.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(),
			(int) (bg.getAlpha() * alpha)));
		graphics.fillRoundRect(x, y, width, height, BORDER_RADIUS, BORDER_RADIUS);
	}

	/**
	 * Draws a 1 px rounded border. When {@code isPeek} is true the border uses amber
	 * regardless of {@code borderColor}. Skips drawing when neither {@code showBorder}
	 * nor {@code isPeek} is true.
	 */
	public void drawBubbleBorder(Graphics2D graphics,
		int x, int y, int width, int height,
		Color borderColor, boolean showBorder, boolean isPeek, float alpha)
	{
		if (!showBorder && !isPeek)
		{
			return;
		}
		Color bc = isPeek ? PEEK_AMBER : borderColor;
		graphics.setColor(new Color(bc.getRed(), bc.getGreen(), bc.getBlue(),
			Math.min(255, (int) (bc.getAlpha() * alpha))));
		graphics.setStroke(new BasicStroke(1f));
		graphics.drawRoundRect(x, y, width - 1, height - 1, BORDER_RADIUS, BORDER_RADIUS);
	}

	// ── Text rendering ───────────────────────────────────────────────────────

	/**
	 * Renders color segments with the OSRS 1-pixel black drop shadow.
	 *
	 * @return x position after the last drawn character
	 */
	public int renderSegments(Graphics2D graphics,
		List<ColorSegment> segments,
		int x, int y,
		FontMetrics fm,
		int maxX)
	{
		// Shadow pass — black at (+1, +1)
		int shadowX = x;
		for (ColorSegment seg : segments)
		{
			if (shadowX >= maxX)
			{
				break;
			}
			String text = clipIfNeeded(seg.getText(), fm, maxX - shadowX);
			if (text.isEmpty())
			{
				break;
			}
			graphics.setColor(new Color(0, 0, 0, seg.getColor().getAlpha()));
			graphics.drawString(text, shadowX + 1, y + 1);
			shadowX += fm.stringWidth(text);
		}

		// Main pass
		for (ColorSegment seg : segments)
		{
			if (x >= maxX)
			{
				break;
			}
			String text = clipIfNeeded(seg.getText(), fm, maxX - x);
			if (text.isEmpty())
			{
				break;
			}
			graphics.setColor(seg.getColor());
			graphics.drawString(text, x, y);
			x += fm.stringWidth(text);
		}

		return x;
	}

	// ── Text layout ──────────────────────────────────────────────────────────

	/**
	 * Returns a list of [start, end) char ranges that fit within {@code maxWidth},
	 * breaking on word boundaries where possible.
	 */
	public List<int[]> wrapText(String text, FontMetrics fm, int maxWidth)
	{
		List<int[]> lines = new ArrayList<>();
		if (text.isEmpty())
		{
			return lines;
		}
		if (fm.stringWidth(text) <= maxWidth)
		{
			lines.add(new int[]{0, text.length()});
			return lines;
		}
		int lineStart = 0;
		int lastSpace = -1;
		int i         = lineStart;
		while (i < text.length())
		{
			if (text.charAt(i) == ' ')
			{
				lastSpace = i;
			}
			if (fm.stringWidth(text.substring(lineStart, i + 1)) > maxWidth)
			{
				if (lastSpace > lineStart)
				{
					lines.add(new int[]{lineStart, lastSpace});
					lineStart = lastSpace + 1;
					lastSpace = -1;
					i = lineStart;
				}
				else
				{
					int breakAt = i > lineStart ? i : i + 1;
					lines.add(new int[]{lineStart, breakAt});
					lineStart = breakAt;
					lastSpace = -1;
					i = lineStart;
				}
			}
			else
			{
				i++;
			}
		}
		if (lineStart < text.length())
		{
			lines.add(new int[]{lineStart, text.length()});
		}
		return lines;
	}

	/**
	 * Extracts the sub-segments covering chars [{@code start}, {@code end}) of the
	 * original plain string.
	 */
	public List<ColorSegment> sliceSegments(List<ColorSegment> segments, int start, int end)
	{
		List<ColorSegment> result = new ArrayList<>();
		int pos = 0;
		for (ColorSegment seg : segments)
		{
			String text   = seg.getText();
			int    segEnd = pos + text.length();
			if (segEnd <= start)
			{
				pos = segEnd;
				continue;
			}
			if (pos >= end)
			{
				break;
			}
			int from = Math.max(0, start - pos);
			int to   = Math.min(text.length(), end - pos);
			if (from < to)
			{
				result.add(new ColorSegment(text.substring(from, to), seg.getColor()));
			}
			pos = segEnd;
		}
		return result;
	}

	public String clipIfNeeded(String text, FontMetrics fm, int availableWidth)
	{
		if (availableWidth <= 0)
		{
			return "";
		}
		if (fm.stringWidth(text) <= availableWidth)
		{
			return text;
		}
		String ellipsis = "...";
		int ew = fm.stringWidth(ellipsis);
		if (availableWidth <= ew)
		{
			return "";
		}
		for (int i = text.length() - 1; i > 0; i--)
		{
			if (fm.stringWidth(text.substring(0, i)) + ew <= availableWidth)
			{
				return text.substring(0, i) + ellipsis;
			}
		}
		return ellipsis;
	}
}
