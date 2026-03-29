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
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Bubble-style overlay for Private chat messages.
 */
public class PrivateChatOverlay extends Overlay
{
	private static final int BORDER_RADIUS = 12;

	private final ChatOverlayPlugin plugin;
	private final ChatOverlayConfig config;

	@Inject
	public PrivateChatOverlay(ChatOverlayPlugin plugin, ChatOverlayConfig config)
	{
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(Overlay.PRIORITY_MED);
		setMovable(true);
		setSnappable(true);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showPrivateChat())
		{
			return null;
		}

		if (config.privateHideWhenChatboxOpen() && plugin.isChatboxOpen())
		{
			return null;
		}

		List<ChatLine> messages = plugin.getMessageManager().getPrivateMessages();
		if (messages.isEmpty())
		{
			return null;
		}

		// Enforce max display limit — in case the setting was lowered after messages arrived
		int maxMsg = config.privateMaxMessages();
		if (messages.size() > maxMsg)
		{
			messages = messages.subList(messages.size() - maxMsg, messages.size());
		}

		// Bold RuneScape font, crisp (no AA) — matching system overlay.
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		Font font = resolveFont();
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics(font);

		int maxWidth      = config.privateOverlayWidth();
		long durMs        = config.privateMessageDuration() * 1000L;
		int paddingX      = config.bubblePaddingX();
		int paddingY      = config.bubblePaddingY();
		int bubbleSpacing = config.bubbleSpacing();

		int y          = 0;
		int totalWidth = 0;

		for (ChatLine line : messages)
		{
			float alpha = computeAlpha(line.getAge(), durMs);
			if (!config.privateFadeMessages() && alpha > 0f)
			{
				alpha = 1.0f;
			}
			if (alpha <= 0.01f)
			{
				continue;
			}

			// Resolve colors at render time from RuneLite's ChatMessageManager.
			// NORMAL = sender name color, HIGHLIGHT = message body color.
			boolean isSender = line.getChatMessageType() == ChatMessageType.PRIVATECHATOUT;
			Color senderColor = plugin.getSenderColor(line.getChatMessageType(), ChatColorType.NORMAL, isSender);
			Color msgColor    = plugin.getChatColor(line.getChatMessageType(), ChatColorType.HIGHLIGHT);

			ChatLineBuilder builder = new ChatLineBuilder(msgColor, plugin.getChatColorConfig());

			// Timestamp: [HH:MM] prefix in sender color (optional)
			if (config.privateShowTimestamp())
			{
				LocalTime time = LocalTime.ofInstant(
					Instant.ofEpochMilli(line.getTimestamp()), ZoneId.systemDefault());
				builder.append(String.format("[%02d:%02d] ", time.getHour(), time.getMinute()),
					senderColor);
			}

			// rawSender already contains the "From "/"To " prefix as plain text.
			builder.append(line.getRawSender(), senderColor);
			builder.append(": ");
			builder.append(line.getRawMessage());

			List<ColorSegment> allSegs = builder.getSegments();

			String plain      = builder.toPlainString();
			int    innerWidth = maxWidth - paddingX * 2;
			List<ColorSegment> faded = applyAlphaToSegments(allSegs, alpha);

			int bubbleWidth;
			int bubbleHeight;

			if (config.privateWordWrap())
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
			return null;
		}
		return new Dimension(Math.max(totalWidth, maxWidth), y);
	}

	// ── Helpers ─────────────────────────────────────────────────────────────

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

	private void drawBubble(Graphics2D graphics,
		int x, int y, int width, int height, float alpha)
	{
		Color bg = config.privateBgColor();
		graphics.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(),
			(int) (bg.getAlpha() * alpha)));
		graphics.fillRoundRect(x, y, width, height, BORDER_RADIUS, BORDER_RADIUS);
	}

	private float computeAlpha(long ageMs, long durMs)
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

	/**
	 * Render segments with the standard OSRS 1-pixel black shadow.
	 * Shadow is drawn at (+1, +1) using the same alpha as the segment text.
	 *
	 * @return X position after the last drawn character
	 */
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
