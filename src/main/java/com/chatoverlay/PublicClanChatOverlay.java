package com.chatoverlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Bubble-style overlay for Public, Clan, and Friends Chat messages.
 *
 * <p>Each message is rendered in its own pill bubble — same visual style as
 * {@link GameOverlay}.  Uses bold RuneScape font, no anti-aliasing,
 * and a single {@link ChatLineBuilder} pass per line so the game's embedded
 * {@code <col>} tags are honored exactly.</p>
 */
public class PublicClanChatOverlay extends Overlay
{
	private static final int BORDER_RADIUS = 12;

	private final ChatOverlayPlugin plugin;
	private final ChatOverlayConfig config;

	@Inject
	public PublicClanChatOverlay(ChatOverlayPlugin plugin, ChatOverlayConfig config)
	{
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.BOTTOM_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(Overlay.PRIORITY_LOW);
		setMovable(true);
		setSnappable(true);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.publicHideWhenChatboxOpen() && plugin.isChatboxOpen())
		{
			return null;
		}

		List<ChatLine> messages = plugin.getMessageManager().getPublicClanMessages();
		if (messages.isEmpty())
		{
			return null;
		}

		// Enforce max display limit — in case the setting was lowered after messages arrived
		int maxMsg = config.publicMaxMessages();
		if (messages.size() > maxMsg)
		{
			messages = messages.subList(messages.size() - maxMsg, messages.size());
		}

		// Bold RuneScape font, crisp (no AA) — same as the system alert overlay.
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		Font font = resolveFont();
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics(font);

		int maxWidth     = config.publicOverlayWidth();
		long durMs       = config.publicMessageDuration() * 1000L;
		int paddingX     = config.bubblePaddingX();
		int paddingY     = config.bubblePaddingY();
		int bubbleSpacing = config.bubbleSpacing();

		int y          = 0;
		int totalWidth = 0;

		for (ChatLine line : messages)
		{
			float alpha = computeAlpha(line.getAge(), durMs);
			if (plugin.isPeekActive() || (!config.publicFadeMessages() && alpha > 0f))
			{
				alpha = 1.0f;
			}
			if (alpha <= 0.01f)
			{
				continue;
			}

			// Resolve colors at render time from RuneLite's ChatMessageManager.
			// NORMAL = sender name color, HIGHLIGHT = message body color.
			// Both account for the current transparent/opaque chatbox state.
			String localName = plugin.getLocalPlayerName();
			boolean isSender = localName != null && localName.equalsIgnoreCase(line.getRawSender());
			Color senderColor = plugin.getSenderColor(line.getChatMessageType(), ChatColorType.NORMAL, isSender);
			Color msgColor    = plugin.getChatColor(line.getChatMessageType(), ChatColorType.HIGHLIGHT);

			// Timestamp: [HH:MM] rendered separately so order is [timestamp] [icon] username
			String timestampStr   = "";
			int    timestampWidth = 0;
			if (config.publicShowTimestamp())
			{
				LocalTime time = LocalTime.ofInstant(
					Instant.ofEpochMilli(line.getTimestamp()), ZoneId.systemDefault());
				timestampStr  = String.format("[%02d:%02d] ", time.getHour(), time.getMinute());
				timestampWidth = fm.stringWidth(timestampStr);
			}

			// Icon sits between timestamp and sender name
			BufferedImage icon = config.showPlayerIcons() ? line.getIcon() : null;
			int iconOffsetX = 0;
			if (icon != null)
			{
				int iconH = fm.getHeight();
				int iconW = (int) ((double) icon.getWidth() * iconH / icon.getHeight());
				iconOffsetX = iconW + 4;
			}

			ChatLineBuilder builder = new ChatLineBuilder(msgColor, plugin.getChatColorConfig());

			// Channel prefix: real clan/FC name, e.g. "[Laced PVM] "
			// Use the dedicated channel-name color from ChatColorConfig.
			String channelName = line.getChannelName();
			if (channelName != null && !channelName.isEmpty())
			{
				Color channelColor = plugin.getChannelNameColor(line.getChatMessageType());
				builder.append("[" + channelName + "] ", channelColor);
			}
			if (!line.getSender().isEmpty())
			{
				builder.append(line.getRawSender(), senderColor);
				builder.append(": ");
			}
			builder.append(line.getRawMessage());

			List<ColorSegment> allSegs = builder.getSegments();

			String plain      = builder.toPlainString();
			int    innerWidth = maxWidth - paddingX * 2 - timestampWidth - iconOffsetX;
			List<ColorSegment> faded = applyAlphaToSegments(allSegs, alpha);

			int bubbleWidth;
			int bubbleHeight;
			int textStartX = paddingX + timestampWidth + iconOffsetX;

			if (config.publicWordWrap())
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
				bubbleWidth  = maxLineW + timestampWidth + iconOffsetX + paddingX * 2;
				bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;

				drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, alpha);
				drawBubbleBorderIfNeeded(graphics, 0, y, bubbleWidth, bubbleHeight, alpha);

				int textY = y + paddingY + fm.getAscent();

				if (!timestampStr.isEmpty())
				{
					Color tc = new Color(senderColor.getRed(), senderColor.getGreen(),
						senderColor.getBlue(), (int) (senderColor.getAlpha() * alpha));
					graphics.setColor(new Color(0, 0, 0, tc.getAlpha()));
					graphics.drawString(timestampStr, paddingX + 1, textY + 1);
					graphics.setColor(tc);
					graphics.drawString(timestampStr, paddingX, textY);
				}

				if (icon != null)
				{
					int iconH = fm.getHeight();
					int iconW = iconOffsetX - 4;
					Composite orig = graphics.getComposite();
					graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
					graphics.drawImage(icon, paddingX + timestampWidth, y + paddingY, iconW, iconH, null);
					graphics.setComposite(orig);
				}

				for (int[] range : lineRanges)
				{
					List<ColorSegment> lineSegs = sliceSegments(faded, range[0], range[1]);
					int lineW = fm.stringWidth(plain.substring(range[0], range[1]));
					renderSegments(graphics, lineSegs, textStartX, textY, fm, textStartX + lineW);
					textY += fm.getHeight();
				}
			}
			else
			{
				int textWidth = Math.min(fm.stringWidth(plain), innerWidth);
				bubbleWidth  = textWidth + timestampWidth + iconOffsetX + paddingX * 2;
				bubbleHeight = fm.getHeight() + paddingY * 2;

				drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, alpha);
				drawBubbleBorderIfNeeded(graphics, 0, y, bubbleWidth, bubbleHeight, alpha);

				int textY = y + paddingY + fm.getAscent();

				if (!timestampStr.isEmpty())
				{
					Color tc = new Color(senderColor.getRed(), senderColor.getGreen(),
						senderColor.getBlue(), (int) (senderColor.getAlpha() * alpha));
					graphics.setColor(new Color(0, 0, 0, tc.getAlpha()));
					graphics.drawString(timestampStr, paddingX + 1, textY + 1);
					graphics.setColor(tc);
					graphics.drawString(timestampStr, paddingX, textY);
				}

				if (icon != null)
				{
					int iconH = fm.getHeight();
					int iconW = iconOffsetX - 4;
					Composite orig = graphics.getComposite();
					graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
					graphics.drawImage(icon, paddingX + timestampWidth, y + paddingY, iconW, iconH, null);
					graphics.setComposite(orig);
				}

				renderSegments(graphics, faded, textStartX, textY, fm, textStartX + textWidth);
			}

			if (bubbleWidth > totalWidth)
			{
				totalWidth = bubbleWidth;
			}
			y += bubbleHeight + bubbleSpacing;
		}

		// ── Chatbox typing bubble ──────────────────────────────────────────
		if (config.showChatboxMessage())
		{
			String typedText = plugin.getChatboxTypedText();
			if (!typedText.isEmpty())
			{
				String localName = plugin.getLocalPlayerName();
				String displayName = (localName != null && !localName.isEmpty()) ? localName : "You";

				Color typingSenderColor = new Color(255, 255, 255);
				Color typingMsgColor    = new Color(255, 255, 0);

				ChatLineBuilder typingBuilder = new ChatLineBuilder(typingMsgColor, plugin.getChatColorConfig());
				typingBuilder.append(displayName + ": ", typingSenderColor);
				typingBuilder.append(typedText);

				List<ColorSegment> typingSegs = typingBuilder.getSegments();
				String typingPlain = typingBuilder.toPlainString();
				int innerWidth = maxWidth - paddingX * 2;

				int bubbleWidth;
				int bubbleHeight;

				if (config.publicWordWrap())
				{
					List<int[]> lineRanges = wrapText(typingPlain, fm, innerWidth);
					if (!lineRanges.isEmpty())
					{
						int maxLineW = 0;
						for (int[] range : lineRanges)
						{
							maxLineW = Math.max(maxLineW, fm.stringWidth(typingPlain.substring(range[0], range[1])));
						}
						bubbleWidth  = maxLineW + paddingX * 2;
						bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;

						drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, 1.0f);

						int textY = y + paddingY + fm.getAscent();
						for (int[] range : lineRanges)
						{
							List<ColorSegment> lineSegs = sliceSegments(typingSegs, range[0], range[1]);
							int lineW = fm.stringWidth(typingPlain.substring(range[0], range[1]));
							renderSegments(graphics, lineSegs, paddingX, textY, fm, paddingX + lineW);
							textY += fm.getHeight();
						}

						if (bubbleWidth > totalWidth)
						{
							totalWidth = bubbleWidth;
						}
						y += bubbleHeight + bubbleSpacing;
					}
				}
				else
				{
					int textWidth = Math.min(fm.stringWidth(typingPlain), innerWidth);
					bubbleWidth  = textWidth + paddingX * 2;
					bubbleHeight = fm.getHeight() + paddingY * 2;

					drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, 1.0f);

					int textY = y + paddingY + fm.getAscent();
					renderSegments(graphics, typingSegs, paddingX, textY, fm, paddingX + textWidth);

					if (bubbleWidth > totalWidth)
					{
						totalWidth = bubbleWidth;
					}
					y += bubbleHeight + bubbleSpacing;
				}
			}
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
		Color bg = config.publicBgColor();
		graphics.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(),
			(int) (bg.getAlpha() * alpha)));
		graphics.fillRoundRect(x, y, width, height, BORDER_RADIUS, BORDER_RADIUS);
	}

	private void drawBubbleBorderIfNeeded(Graphics2D graphics,
		int x, int y, int width, int height, float alpha)
	{
		boolean showBorder = config.publicShowBubbleBorder() || plugin.isPeekActive();
		if (!showBorder)
		{
			return;
		}
		Color bc = plugin.isPeekActive()
			? new Color(255, 200, 0, 220)
			: config.publicBubbleBorderColor();
		graphics.setColor(new Color(bc.getRed(), bc.getGreen(), bc.getBlue(),
			Math.min(255, (int) (bc.getAlpha() * alpha))));
		graphics.setStroke(new BasicStroke(1f));
		graphics.drawRoundRect(x, y, width - 1, height - 1, BORDER_RADIUS, BORDER_RADIUS);
	}

	// Channel prefix is now read from line.getChannelName() — no more hardcoded labels.

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

	/**
	 * Returns a list of [start, end) char ranges in {@code text} that fit within
	 * {@code maxWidth} pixels, breaking on word boundaries where possible.
	 */
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
		int lineStart  = 0;
		int lastSpace  = -1;
		int i          = lineStart;
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
					// No break point — force-break before this char (or include it if first)
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
	 * Extracts the sub-segments of {@code segments} that correspond to
	 * chars [{@code start}, {@code end}) of the original plain text.
	 */
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
