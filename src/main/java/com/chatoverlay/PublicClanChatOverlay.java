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
	private static final int PADDING_X      = 10;
	private static final int PADDING_Y      = 5;
	private static final int BUBBLE_SPACING = 4;
	private static final int BORDER_RADIUS  = 12;

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

		int maxWidth = config.publicOverlayWidth();
		long durMs   = config.publicMessageDuration() * 1000L;

		int y          = 0;
		int totalWidth = 0;

		for (ChatLine line : messages)
		{
			float alpha = computeAlpha(line.getAge(), durMs);
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

			ChatLineBuilder builder = new ChatLineBuilder(msgColor, plugin.getChatColorConfig());

			// Timestamp: [HH:MM] prefix in sender color (optional)
			if (config.publicShowTimestamp())
			{
				LocalTime time = LocalTime.ofInstant(
					Instant.ofEpochMilli(line.getTimestamp()), ZoneId.systemDefault());
				builder.append(String.format("[%02d:%02d] ", time.getHour(), time.getMinute()),
					senderColor);
			}

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

			// Measure plain-text width, cap at overlay width
			String plain = builder.toPlainString();
			int textWidth    = Math.min(fm.stringWidth(plain), maxWidth - PADDING_X * 2);
			int bubbleWidth  = textWidth + PADDING_X * 2;
			int bubbleHeight = fm.getHeight() + PADDING_Y * 2;

			// Bubble background
			drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, alpha);

			// Text inside bubble
			int textY = y + PADDING_Y + fm.getAscent();
			List<ColorSegment> faded = applyAlphaToSegments(allSegs, alpha);
			renderSegments(graphics, faded, PADDING_X, textY, fm,
				PADDING_X + textWidth);

			if (bubbleWidth > totalWidth)
			{
				totalWidth = bubbleWidth;
			}
			y += bubbleHeight + BUBBLE_SPACING;
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

		graphics.setColor(new Color(255, 255, 255, (int) (50 * alpha)));
		graphics.drawRoundRect(x, y, width, height, BORDER_RADIUS, BORDER_RADIUS);
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
