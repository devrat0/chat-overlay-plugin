package com.chatoverlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * System/game message alert overlay.
 */
public class GameOverlay extends Overlay
{
	private static final int BORDER_RADIUS    = 12;
	private static final int ABOVE_PLAYER_OFFSET = 90;
	private static final int MAX_BUBBLE_WIDTH = 350;

	private final Client client;
	private final ChatOverlayPlugin plugin;
	private final ChatOverlayConfig config;

	private GameOverlayMode lastMode = null;

	@Inject
	public GameOverlay(Client client, ChatOverlayPlugin plugin, ChatOverlayConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;

		// Default: pinned-to-player (DYNAMIC, drawn on the scene layer)
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(Overlay.PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showSystemAlerts())
		{
			return null;
		}

		if (config.systemHideWhenChatboxOpen() && plugin.isChatboxOpen())
		{
			return null;
		}

		List<ChatLine> alerts = plugin.getMessageManager().getSystemMessages();
		if (alerts.isEmpty())
		{
			return null;
		}

		GameOverlayMode mode = config.systemAlertMode();

		// Update overlay position/layer only when the mode has actually changed,
		// avoiding unnecessary overhead on every rendered frame.
		if (mode != lastMode)
		{
			lastMode = mode;
			if (mode == GameOverlayMode.OVERLAY)
			{
				setPosition(OverlayPosition.TOP_LEFT);
				setLayer(OverlayLayer.ABOVE_WIDGETS);
				setMovable(true);
				setSnappable(true);
			}
			else
			{
				setPosition(OverlayPosition.DYNAMIC);
				setLayer(OverlayLayer.ABOVE_SCENE);
				// Clear the panel position saved while in FREE OVERLAY mode.
				setPreferredLocation(null);
				setPreferredSize(null);
			}
		}

		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		Font font = resolveFont();
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics(font);
		long durationMs = config.systemAlertDuration() * 1000L;

		if (mode == GameOverlayMode.OVERLAY)
		{
			return renderAsOverlay(graphics, alerts, fm, durationMs);
		}
		else
		{
			return renderPinnedToPlayer(graphics, alerts, fm, durationMs);
		}
	}

	// ── Font helper ─────────────────────────────────────────────────────────

	private Font resolveFont()
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

	// ── Pinned-to-Player mode ───────────────────────────────────────────────

	/**
	 * Draws pill bubbles centered above the local player.
	 */
	private Dimension renderPinnedToPlayer(
		Graphics2D graphics, List<ChatLine> alerts, FontMetrics fm, long durationMs)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return null;
		}

		Point anchor = localPlayer.getCanvasTextLocation(
			graphics, "X", localPlayer.getLogicalHeight() + ABOVE_PLAYER_OFFSET);
		if (anchor == null)
		{
			return null;
		}

		int paddingX      = config.bubblePaddingX();
		int paddingY      = config.bubblePaddingY();
		int bubbleSpacing = config.bubbleSpacing();

		java.awt.geom.AffineTransform tx = graphics.getTransform();
		int centerX  = anchor.getX() - (int) tx.getTranslateX();
		int currentY = anchor.getY() - (int) tx.getTranslateY();

		for (int i = alerts.size() - 1; i >= 0; i--)
		{
			ChatLine alert = alerts.get(i);
			float alpha = computeAlpha(alert.getAge(), durationMs);
			if (alpha <= 0.01f)
			{
				continue;
			}

			Color msgColor = plugin.getChatColor(alert.getChatMessageType(), ChatColorType.HIGHLIGHT);
			ChatLineBuilder builder = new ChatLineBuilder(msgColor, plugin.getChatColorConfig());
			if (config.systemShowTimestamp())
			{
				LocalTime time = LocalTime.ofInstant(
					Instant.ofEpochMilli(alert.getTimestamp()), ZoneId.systemDefault());
				builder.append(String.format("[%02d:%02d] ", time.getHour(), time.getMinute()), msgColor);
			}
			builder.append(alert.getRawMessage());
			List<ColorSegment> segments = builder.getSegments();
			String plain      = builder.toPlainString();
			int    innerWidth = MAX_BUBBLE_WIDTH - paddingX * 2;
			List<ColorSegment> faded = applyAlphaToSegments(segments, alpha);

			int bubbleWidth;
			int bubbleHeight;

			if (config.systemWordWrap())
			{
				List<int[]> lineRanges = wrapText(plain, fm, innerWidth);
				if (lineRanges.isEmpty())
				{
					continue;
				}
				int maxLineW = 0;
				for (int[] range : lineRanges)
				{
					maxLineW = Math.max(maxLineW, fm.stringWidth(plain.substring(range[0], range[1])));
				}
				bubbleWidth  = maxLineW + paddingX * 2;
				bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;
				int bubbleX  = centerX - bubbleWidth / 2;
				int bubbleY  = currentY - bubbleHeight;

				drawBubble(graphics, bubbleX, bubbleY, bubbleWidth, bubbleHeight, alpha);

				int textY = bubbleY + paddingY + fm.getAscent();
				for (int[] range : lineRanges)
				{
					List<ColorSegment> lineSegs = sliceSegments(faded, range[0], range[1]);
					int lineW      = fm.stringWidth(plain.substring(range[0], range[1]));
					int textStartX = centerX - lineW / 2;
					renderSegments(graphics, lineSegs, textStartX, textY, fm, textStartX + lineW);
					textY += fm.getHeight();
				}
				currentY = bubbleY - bubbleSpacing;
			}
			else
			{
				int textWidth = Math.min(fm.stringWidth(plain), innerWidth);
				bubbleWidth  = textWidth + paddingX * 2;
				bubbleHeight = fm.getHeight() + paddingY * 2;
				int bubbleX  = centerX - bubbleWidth / 2;
				int bubbleY  = currentY - bubbleHeight;

				drawBubble(graphics, bubbleX, bubbleY, bubbleWidth, bubbleHeight, alpha);

				int textY      = bubbleY + paddingY + fm.getAscent();
				int textStartX = centerX - textWidth / 2;
				renderSegments(graphics, faded, textStartX, textY, fm, textStartX + textWidth);
				currentY = bubbleY - bubbleSpacing;
			}
		}

		return null; // DYNAMIC overlays don't return a bounding Dimension
	}

	// ── Free-Overlay mode ───────────────────────────────────────────────────

	/**
	 * Draws pill bubbles stacked downward from the panel's top edge.
	 */
	private Dimension renderAsOverlay(
		Graphics2D graphics, List<ChatLine> alerts, FontMetrics fm, long durationMs)
	{
		int paddingX      = config.bubblePaddingX();
		int paddingY      = config.bubblePaddingY();
		int bubbleSpacing = config.bubbleSpacing();

		int y          = 0;
		int totalWidth = 0;

		// Newest at top (reverse iteration mirrors pinned mode's "newest nearest player")
		for (int i = alerts.size() - 1; i >= 0; i--)
		{
			ChatLine alert = alerts.get(i);
			float alpha = computeAlpha(alert.getAge(), durationMs);
			if (alpha <= 0.01f)
			{
				continue;
			}

			Color msgColor = plugin.getChatColor(alert.getChatMessageType(), ChatColorType.HIGHLIGHT);
			ChatLineBuilder builder = new ChatLineBuilder(msgColor, plugin.getChatColorConfig());
			if (config.systemShowTimestamp())
			{
				LocalTime time = LocalTime.ofInstant(
					Instant.ofEpochMilli(alert.getTimestamp()), ZoneId.systemDefault());
				builder.append(String.format("[%02d:%02d] ", time.getHour(), time.getMinute()), msgColor);
			}
			builder.append(alert.getRawMessage());
			List<ColorSegment> segments = builder.getSegments();
			String plain      = builder.toPlainString();
			int    innerWidth = MAX_BUBBLE_WIDTH - paddingX * 2;
			List<ColorSegment> faded = applyAlphaToSegments(segments, alpha);

			int bubbleWidth;
			int bubbleHeight;

			if (config.systemWordWrap())
			{
				List<int[]> lineRanges = wrapText(plain, fm, innerWidth);
				if (lineRanges.isEmpty())
				{
					continue;
				}
				int maxLineW = 0;
				for (int[] range : lineRanges)
				{
					maxLineW = Math.max(maxLineW, fm.stringWidth(plain.substring(range[0], range[1])));
				}
				bubbleWidth  = maxLineW + paddingX * 2;
				bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;

				drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, alpha);

				int textY = y + paddingY + fm.getAscent();
				for (int[] range : lineRanges)
				{
					List<ColorSegment> lineSegs = sliceSegments(faded, range[0], range[1]);
					int lineW = fm.stringWidth(plain.substring(range[0], range[1]));
					renderSegments(graphics, lineSegs, paddingX, textY, fm, paddingX + lineW);
					textY += fm.getHeight();
				}
			}
			else
			{
				int textWidth = Math.min(fm.stringWidth(plain), innerWidth);
				bubbleWidth  = textWidth + paddingX * 2;
				bubbleHeight = fm.getHeight() + paddingY * 2;

				drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, alpha);

				int textY = y + paddingY + fm.getAscent();
				renderSegments(graphics, faded, paddingX, textY, fm, paddingX + textWidth);
			}

			if (bubbleWidth > totalWidth)
			{
				totalWidth = bubbleWidth;
			}
			y += bubbleHeight + bubbleSpacing;
		}

		if (y == 0)
		{
			return null; // nothing visible (all fully faded)
		}
		return new Dimension(totalWidth, y);
	}

	// ── Shared helpers ──────────────────────────────────────────────────────

	private void drawBubble(Graphics2D graphics,
		int x, int y, int width, int height, float alpha)
	{
		Color bg = config.systemBgColor();
		graphics.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(),
			(int) (bg.getAlpha() * alpha)));
		graphics.fillRoundRect(x, y, width, height, BORDER_RADIUS, BORDER_RADIUS);
	}

	/**
	 * Fade-in for first 10% of lifetime, fully opaque until 3 s before expiry,
	 */
	private float computeAlpha(long ageMs, long durationMs)
	{
		long fadeWindowMs = Math.min(3000L, durationMs);
		long fadeStartMs  = durationMs - fadeWindowMs;
		long fadeInEndMs  = Math.min((long)(durationMs * 0.1f), fadeStartMs);

		if (fadeInEndMs > 0 && ageMs < fadeInEndMs)
		{
			return (float) ageMs / fadeInEndMs;
		}
		if (ageMs < fadeStartMs)
		{
			return 1.0f;
		}
		return Math.max(0f, 1.0f - (float)(ageMs - fadeStartMs) / fadeWindowMs);
	}

	private List<ColorSegment> applyAlphaToSegments(List<ColorSegment> segments, float alpha)
	{
		List<ColorSegment> result = new java.util.ArrayList<>(segments.size());
		for (ColorSegment s : segments)
		{
			Color c = s.getColor();
			result.add(new ColorSegment(s.getText(),
				new Color(c.getRed(), c.getGreen(), c.getBlue(),
					(int) (c.getAlpha() * alpha))));
		}
		return result;
	}

	private int renderSegments(
		Graphics2D graphics,
		List<ColorSegment> segments,
		int x, int y,
		FontMetrics fm,
		int maxX)
	{
		// Shadow pass — black at (+1, +1), same alpha as the text
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
			int alpha = seg.getColor().getAlpha();
			graphics.setColor(new Color(0, 0, 0, alpha));
			graphics.drawString(text, shadowX + 1, y + 1);
			shadowX += fm.stringWidth(text);
		}

		// Main pass — actual colors at (x, y)
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

	private java.util.List<int[]> wrapText(String text, FontMetrics fm, int maxWidth)
	{
		java.util.List<int[]> lines = new java.util.ArrayList<>();
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

	private java.util.List<ColorSegment> sliceSegments(
		java.util.List<ColorSegment> segments, int start, int end)
	{
		java.util.List<ColorSegment> result = new java.util.ArrayList<>();
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

	private String clipIfNeeded(String text, FontMetrics fm, int availableWidth)
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
