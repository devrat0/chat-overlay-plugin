package com.chatoverlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

	public int getSnappedFontSize()
	{
		FontType type = config.fontType();
		boolean isRuneScape = type == FontType.RUNESCAPE
			|| type == FontType.RUNESCAPE_SMALL
			|| type == FontType.RUNESCAPE_BOLD;

		int currentSize = config.fontSize();
		if (isRuneScape)
		{
			Font nativeFont = resolveNativeFont();
			int nativeSize = nativeFont.getSize();
			int scale = Math.max(1, Math.round((float) currentSize / nativeSize));
			return nativeSize * scale;
		}
		return currentSize;
	}

	public Font resolveFont()
	{
		Font base;
		switch (config.fontType())
		{
			case RUNESCAPE:       base = FontManager.getRunescapeFont();      break;
			case RUNESCAPE_SMALL: base = FontManager.getRunescapeSmallFont(); break;
			case RUNESCAPE_BOLD:  base = FontManager.getRunescapeBoldFont();  break;
			case ARIAL:           base = new Font("Arial", Font.PLAIN, config.fontSize()); break;
			case DIALOG:          base = new Font(Font.DIALOG, Font.PLAIN, config.fontSize()); break;
			case SANS_SERIF:      base = new Font(Font.SANS_SERIF, Font.PLAIN, config.fontSize()); break;
			case SERIF:           base = new Font(Font.SERIF, Font.PLAIN, config.fontSize()); break;
			case MONOSPACED:      base = new Font(Font.MONOSPACED, Font.PLAIN, config.fontSize()); break;
			case CUSTOM:          base = new Font(config.customFontName(), Font.PLAIN, config.fontSize()); break;
			default:              base = FontManager.getRunescapeBoldFont();  break;
		}
		return base.deriveFont((float) getSnappedFontSize());
	}

	public void configureRenderingHints(Graphics2D graphics)
	{
		FontType type = config.fontType();
		boolean isRuneScape = type == FontType.RUNESCAPE
			|| type == FontType.RUNESCAPE_SMALL
			|| type == FontType.RUNESCAPE_BOLD;

		if (isRuneScape)
		{
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		}
		else
		{
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		}
	}

	// ── Alpha ────────────────────────────────────────────────────────────────

	/**
	 * Standard fade-out only: opaque until 3 s before {@code durMs}, then fades to 0.
	 * Returns 1.0 when {@code durMs} is 0 (infinite).
	 */
	public float computeAlpha(ChatLine line, long durMs)
	{
		float baseAlpha;
		long ageMs = line.getAge();
		if (durMs <= 0)
		{
			baseAlpha = 1.0f;
		}
		else
		{
			long fadeWindowMs = Math.min(3000L, durMs);
			long fadeStartMs  = durMs - fadeWindowMs;
			if (ageMs < fadeStartMs)
			{
				baseAlpha = 1.0f;
			}
			else
			{
				baseAlpha = Math.max(0f, 1.0f - (float) (ageMs - fadeStartMs) / fadeWindowMs);
			}
		}

		if (line.isPruned())
		{
			float pruneAlpha = Math.max(0f, 1.0f - (float) line.getPruneAge() / 1000f);
			return Math.min(baseAlpha, pruneAlpha);
		}
		return baseAlpha;
	}

	/**
	 * Fade-in over first 10% of lifetime, fully opaque until 3 s before expiry, then fades out.
	 * Used by GameOverlay.
	 */
	public float computeAlphaWithFadeIn(ChatLine line, long durationMs)
	{
		long ageMs = line.getAge();
		long fadeWindowMs = Math.min(3000L, durationMs);
		long fadeStartMs  = durationMs - fadeWindowMs;
		long fadeInEndMs  = Math.min((long) (durationMs * 0.1f), fadeStartMs);

		float baseAlpha;
		if (fadeInEndMs > 0 && ageMs < fadeInEndMs)
		{
			baseAlpha = (float) ageMs / fadeInEndMs;
		}
		else if (ageMs < fadeStartMs)
		{
			baseAlpha = 1.0f;
		}
		else
		{
			baseAlpha = Math.max(0f, 1.0f - (float) (ageMs - fadeStartMs) / fadeWindowMs);
		}

		if (line.isPruned())
		{
			float pruneAlpha = Math.max(0f, 1.0f - (float) line.getPruneAge() / 1000f);
			return Math.min(baseAlpha, pruneAlpha);
		}
		return baseAlpha;
	}

	public List<ColorSegment> applyAlphaToSegments(List<ColorSegment> segments, float alpha)
	{
		List<ColorSegment> result = new ArrayList<>(segments.size());
		for (ColorSegment s : segments)
		{
			Color c = s.getColor();
			Color newColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (c.getAlpha() * alpha));
			if (s.getImage() != null)
			{
				result.add(new ColorSegment(s.getImage(), s.getImageId(), newColor));
			}
			else
			{
				result.add(new ColorSegment(s.getText(), newColor));
			}
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
		int roundness = config.bubbleRoundness();
		graphics.fillRoundRect(x, y, width, height, roundness, roundness);
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
		int roundness = config.bubbleRoundness();
		graphics.drawRoundRect(x, y, width - 1, height - 1, roundness, roundness);
	}

	// ── Text rendering ───────────────────────────────────────────────────────

	/**
	 * Renders color segments with the OSRS 1-pixel black drop shadow.
	 *
	 * @return x position after the last drawn character
	 */
	public Font resolveNativeFont()
	{
		switch (config.fontType())
		{
			case RUNESCAPE:       return FontManager.getRunescapeFont();
			case RUNESCAPE_SMALL: return FontManager.getRunescapeSmallFont();
			default:              return FontManager.getRunescapeBoldFont();
		}
	}

	public int renderSegments(Graphics2D graphics,
		List<ColorSegment> segments,
		int x, int y,
		FontMetrics fm,
		int maxX)
	{
		FontType type = config.fontType();
		boolean isRuneScape = type == FontType.RUNESCAPE
			|| type == FontType.RUNESCAPE_SMALL
			|| type == FontType.RUNESCAPE_BOLD;

		if (isRuneScape)
		{
			Font nativeFont = resolveNativeFont();
			int nativeSize = nativeFont.getSize();
			int currentSize = getSnappedFontSize();

			if (currentSize != nativeSize)
			{
				int scale = currentSize / nativeSize;
				FontMetrics nativeFm = graphics.getFontMetrics(nativeFont);
				int nativeW = getSegmentsWidth(segments, nativeFm);
				int nativeH = nativeFm.getHeight();

				if (nativeW <= 0 || nativeH <= 0)
				{
					return x;
				}

				java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
					nativeW + 2, nativeH + 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
				Graphics2D g2d = img.createGraphics();
				g2d.setFont(nativeFont);
				g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
				g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

				// Render onto the image
				renderSegmentsDirectly(g2d, segments, 0, nativeFm.getAscent(), nativeFm, nativeW + 2);
				g2d.dispose();

				// Scale and draw to main graphics context using integer factors
				int scaledW = (nativeW + 2) * scale;
				int scaledH = (nativeH + 2) * scale;
				int topY = y - nativeFm.getAscent() * scale;

				Object oldInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
				graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				graphics.drawImage(img, x, topY, scaledW, scaledH, null);
				if (oldInterpolation != null)
				{
					graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
				}

				return x + scaledW;
			}
		}

		return renderSegmentsDirectly(graphics, segments, x, y, fm, maxX);
	}

	private int renderSegmentsDirectly(Graphics2D graphics,
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
			if (seg.getImage() != null)
			{
				shadowX += seg.getImage().getWidth() + 2;
				continue;
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
			if (seg.getImage() != null)
			{
				java.awt.image.BufferedImage img = seg.getImage();
				int imgW = img.getWidth();
				int imgH = img.getHeight();
				if (x + imgW > maxX)
				{
					break;
				}
				int imgY = y - fm.getAscent() + (fm.getAscent() - imgH) / 2;
				float imgAlpha = seg.getColor().getAlpha() / 255.0f;
				Composite orig = graphics.getComposite();
				graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, imgAlpha));
				graphics.drawImage(img, x + 1, imgY, null);
				graphics.setComposite(orig);
				x += imgW + 2;
				continue;
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

	public int getSegmentsWidth(List<ColorSegment> segments, FontMetrics fm)
	{
		int width = 0;
		for (ColorSegment seg : segments)
		{
			if (seg.getImage() != null)
			{
				width += seg.getImage().getWidth() + 2;
			}
			else
			{
				width += fm.stringWidth(seg.getText());
			}
		}
		return width;
	}

	public int getSlicedSegmentsWidth(List<ColorSegment> segments, int start, int end, FontMetrics fm)
	{
		List<ColorSegment> sliced = sliceSegments(segments, start, end);
		return getSegmentsWidth(sliced, fm);
	}

	/**
	 * Word wraps a list of color segments (which may contain inline images) by
	 * constructing a coordinate mapping of character offsets to custom segment/character widths.
	 */
	public List<int[]> wrapText(List<ColorSegment> segments, FontMetrics fm, int maxWidth)
	{
		List<Integer> charWidths = new ArrayList<>();
		StringBuilder sb = new StringBuilder();

		for (ColorSegment seg : segments)
		{
			if (seg.getImage() != null)
			{
				sb.append(' '); // Use space as 1-char placeholder
				charWidths.add(seg.getImage().getWidth() + 2);
			}
			else
			{
				String txt = seg.getText();
				for (int i = 0; i < txt.length(); i++)
				{
					sb.append(txt.charAt(i));
					charWidths.add(fm.charWidth(txt.charAt(i)));
				}
			}
		}

		String text = sb.toString();
		List<int[]> lines = new ArrayList<>();
		if (text.isEmpty())
		{
			return lines;
		}

		int totalW = 0;
		for (int w : charWidths)
		{
			totalW += w;
		}
		if (totalW <= maxWidth)
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

			int curWidth = 0;
			for (int j = lineStart; j <= i; j++)
			{
				curWidth += charWidths.get(j);
			}

			if (curWidth > maxWidth)
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
			String text = seg.getImage() != null ? " " : seg.getText();
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
				if (seg.getImage() != null)
				{
					result.add(seg);
				}
				else
				{
					result.add(new ColorSegment(text.substring(from, to), seg.getColor()));
				}
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
