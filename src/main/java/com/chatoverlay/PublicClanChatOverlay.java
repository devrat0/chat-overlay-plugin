package com.chatoverlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
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
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Bubble-style overlay for Public, Clan, and Friends Chat messages.
 */
public class PublicClanChatOverlay extends Overlay
{
	private final Client client;
	private final ChatOverlayPlugin plugin;
	private final ChatOverlayConfig config;
	private final BubbleRenderer    renderer;
	private final ChatColorResolver colorResolver;

	@Inject
	public PublicClanChatOverlay(Client client, ChatOverlayPlugin plugin, ChatOverlayConfig config,
		BubbleRenderer renderer, ChatColorResolver colorResolver)
	{
		this.client        = client;
		this.plugin        = plugin;
		this.config        = config;
		this.renderer      = renderer;
		this.colorResolver = colorResolver;

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

		List<ChatLine> allMessages = plugin.getMessageManager().getPublicClanMessages();
		List<ChatLine> messages = new java.util.ArrayList<>();
		for (ChatLine line : allMessages)
		{
			boolean keep = false;
			switch (line.getCategory())
			{
				case PUBLIC:
					keep = config.showPublicChat();
					break;
				case CLAN:
					keep = config.showClanChat();
					break;
				case FRIENDS_CHAT:
					keep = config.showFriendsChat();
					break;
				case PRIVATE:
					keep = config.showPrivateChatInMain();
					break;
				case SYSTEM:
					keep = config.showGameMessagesInMain() && !plugin.isSystemMessageFiltered(line.getPlainMessage().toLowerCase());
					break;
			}
			if (keep)
			{
				messages.add(line);
			}
		}

		boolean showTyping = config.showChatboxMessage() && !plugin.getChatboxTypedText().isEmpty();
		if (messages.isEmpty() && !showTyping)
		{
			return null;
		}

		int maxMsg = config.publicMaxMessages();
		if (messages.size() > maxMsg)
		{
			messages = messages.subList(messages.size() - maxMsg, messages.size());
		}

		renderer.configureRenderingHints(graphics);

		Font font = renderer.resolveFont();
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics(font);

		int maxWidth      = config.publicOverlayWidth();
		int maxHeight     = config.publicOverlayHeight();
		LayoutMode layoutMode = config.publicLayoutMode();
		long durMs        = config.publicMessageDuration() * 1000L;
		int paddingX      = config.bubblePaddingX();
		int paddingY      = config.bubblePaddingY();
		int bubbleSpacing = config.bubbleSpacing();

		int y          = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? maxHeight : 0;
		int totalWidth = 0;

		// ── Chatbox typing bubble (BOTTOM_TO_TOP) ─────────────────────────────
		if (layoutMode == LayoutMode.BOTTOM_TO_TOP && config.showChatboxMessage())
		{
			String typedText = plugin.getChatboxTypedText();
			if (!typedText.isEmpty())
			{
				String localName    = plugin.getLocalPlayerName();
				String displayName  = (localName != null && !localName.isEmpty()) ? localName : "You";
				Color  typingSender = new Color(255, 255, 255);
				Color  typingMsg    = new Color(255, 255, 0);

				ChatLineBuilder typingBuilder = new ChatLineBuilder(client, typingMsg, colorResolver.getChatColorConfig());
				typingBuilder.append(displayName + ": ", typingSender);
				typingBuilder.append(typedText);

				List<ColorSegment> typingSegs  = typingBuilder.getSegments();
				String             typingPlain  = typingBuilder.toPlainString();
				int                innerWidth   = maxWidth - paddingX * 2;

				int bubbleWidth;
				int bubbleHeight;

				if (config.publicWordWrap())
				{
					List<int[]> lineRanges = renderer.wrapText(typingSegs, fm, innerWidth);
					if (!lineRanges.isEmpty())
					{
						int maxLineW = 0;
						for (int[] range : lineRanges)
						{
							maxLineW = Math.max(maxLineW, renderer.getSlicedSegmentsWidth(typingSegs, range[0], range[1], fm));
						}
						bubbleWidth  = maxLineW + paddingX * 2;
						bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;

						y -= bubbleHeight;
						renderer.drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, config.publicBgColor(), 1.0f);

						int textY = y + paddingY + fm.getAscent();
						for (int[] range : lineRanges)
						{
							List<ColorSegment> lineSegs = renderer.sliceSegments(typingSegs, range[0], range[1]);
							int lineW = renderer.getSlicedSegmentsWidth(typingSegs, range[0], range[1], fm);
							renderer.renderSegments(graphics, lineSegs, paddingX, textY, fm, paddingX + lineW);
							textY += fm.getHeight();
						}
						totalWidth = Math.max(totalWidth, bubbleWidth);
						y -= bubbleSpacing;
					}
				}
				else
				{
					int textWidth = Math.min(renderer.getSegmentsWidth(typingSegs, fm), innerWidth);
					bubbleWidth  = textWidth + paddingX * 2;
					bubbleHeight = fm.getHeight() + paddingY * 2;

					y -= bubbleHeight;
					renderer.drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, config.publicBgColor(), 1.0f);

					int textY = y + paddingY + fm.getAscent();
					renderer.renderSegments(graphics, typingSegs, paddingX, textY, fm, paddingX + textWidth);
					totalWidth = Math.max(totalWidth, bubbleWidth);
					y -= bubbleSpacing;
				}
			}
		}

		// ── Messages Loop ─────────────────────────────────────────────────────
		int startIdx = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? messages.size() - 1 : 0;
		int endIdx   = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? -1 : messages.size();
		int step     = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? -1 : 1;

		for (int i = startIdx; i != endIdx; i += step)
		{
			ChatLine line = messages.get(i);
			float alpha = renderer.computeAlpha(line, durMs, config.publicFadeIn(), config.publicFadeOut());
			if (plugin.isPeekActive())
			{
				alpha = 1.0f;
			}
			if (alpha <= 0.01f)
			{
				continue;
			}

			String localName  = plugin.getLocalPlayerName();
			boolean isSender  = localName != null && localName.equalsIgnoreCase(line.getRawSender());
			Color senderColor = colorResolver.getSenderColor(line.getChatMessageType(), ChatColorType.NORMAL, isSender);
			Color msgColor    = colorResolver.getChatColor(line.getChatMessageType(), ChatColorType.HIGHLIGHT);

			Color customTimestampColor = config.publicTimestampColor() != null ? config.publicTimestampColor() : senderColor;
			Color customClanLabelColor = config.publicClanLabelColor() != null ? config.publicClanLabelColor() : colorResolver.getChannelNameColor(line.getChatMessageType());
			Color customUsernameColor  = config.publicUsernameColor() != null ? config.publicUsernameColor() : senderColor;
			Color customTextColor      = config.publicTextColor() != null ? config.publicTextColor() : msgColor;

			// Timestamp rendered separately: [timestamp] [icon] username
			String timestampStr   = "";
			int    timestampWidth = 0;
			if (config.publicShowTimestamp())
			{
				LocalTime time = LocalTime.ofInstant(
					Instant.ofEpochMilli(line.getTimestamp()), ZoneId.systemDefault());
				boolean showSeconds = config.timestampFormat() == TimestampFormat.HH_MM_SS;
				String formatStr = showSeconds ? "[%02d:%02d:%02d] " : "[%02d:%02d] ";
				if (showSeconds)
				{
					timestampStr = String.format(formatStr, time.getHour(), time.getMinute(), time.getSecond());
				}
				else
				{
					timestampStr = String.format(formatStr, time.getHour(), time.getMinute());
				}
				timestampWidth = fm.stringWidth(timestampStr);
			}

			ChatLineBuilder builder     = new ChatLineBuilder(client, customTextColor, colorResolver.getChatColorConfig());
			String          channelName = line.getChannelName();
			if (channelName != null && !channelName.isEmpty())
			{
				builder.append("[" + channelName + "] ", customClanLabelColor);
			}
			if (!line.getSender().isEmpty())
			{
				String senderName = config.showPlayerIcons() ? line.getRawSender() : line.getSender();
				builder.append(senderName, customUsernameColor);
				builder.append(": ");
			}
			builder.append(line.getRawMessage());

			List<ColorSegment> allSegs    = builder.getSegments();
			int                innerWidth = maxWidth - paddingX * 2 - timestampWidth;
			List<ColorSegment> faded      = renderer.applyAlphaToSegments(allSegs, alpha);

			int bubbleWidth;
			int bubbleHeight;

			if (config.publicWordWrap())
			{
				List<int[]> lineRanges = renderer.wrapText(faded, fm, innerWidth);
				if (lineRanges.isEmpty())
				{
					continue;
				}
				int maxLineW = 0;
				for (int[] range : lineRanges)
				{
					maxLineW = Math.max(maxLineW, renderer.getSlicedSegmentsWidth(faded, range[0], range[1], fm));
				}
				bubbleWidth  = maxLineW + timestampWidth + paddingX * 2;
				bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;

				int bubbleY;
				if (layoutMode == LayoutMode.BOTTOM_TO_TOP)
				{
					y -= bubbleHeight;
					if (y < 0)
					{
						break;
					}
					bubbleY = y;
				}
				else
				{
					if (y + bubbleHeight > maxHeight)
					{
						break;
					}
					bubbleY = y;
				}

				boolean isHighlighted = !config.publicDisableKeywordHighlight() && plugin.shouldHighlight(line);
				Color bgColor = isHighlighted ? config.highlightBgColor() : config.publicBgColor();
				Color borderColor = isHighlighted ? config.highlightBorderColor() : config.publicBubbleBorderColor();
				boolean showBorder = isHighlighted ? config.highlightShowBorder() : config.publicShowBubbleBorder();

				if (isHighlighted || config.publicBgEnabled())
				{
					renderer.drawBubble(graphics, 0, bubbleY, bubbleWidth, bubbleHeight, bgColor, alpha);
				}
				renderer.drawBubbleBorder(graphics, 0, bubbleY, bubbleWidth, bubbleHeight,
					borderColor, showBorder, plugin.isPeekActive(), alpha);

				int textY = bubbleY + paddingY + fm.getAscent();
				drawTimestamp(graphics, timestampStr, paddingX, textY, customTimestampColor, alpha);

				int startX = paddingX + timestampWidth;
				for (int[] range : lineRanges)
				{
					List<ColorSegment> lineSegs = renderer.sliceSegments(faded, range[0], range[1]);
					int lineW = renderer.getSlicedSegmentsWidth(faded, range[0], range[1], fm);
					renderer.renderSegments(graphics, lineSegs, startX, textY, fm, startX + lineW);
					textY += fm.getHeight();
				}
			}
			else
			{
				int textWidth = Math.min(renderer.getSegmentsWidth(faded, fm), innerWidth);
				bubbleWidth  = textWidth + timestampWidth + paddingX * 2;
				bubbleHeight = fm.getHeight() + paddingY * 2;

				int bubbleY;
				if (layoutMode == LayoutMode.BOTTOM_TO_TOP)
				{
					y -= bubbleHeight;
					if (y < 0)
					{
						break;
					}
					bubbleY = y;
				}
				else
				{
					if (y + bubbleHeight > maxHeight)
					{
						break;
					}
					bubbleY = y;
				}

				boolean isHighlighted = !config.publicDisableKeywordHighlight() && plugin.shouldHighlight(line);
				Color bgColor = isHighlighted ? config.highlightBgColor() : config.publicBgColor();
				Color borderColor = isHighlighted ? config.highlightBorderColor() : config.publicBubbleBorderColor();
				boolean showBorder = isHighlighted ? config.highlightShowBorder() : config.publicShowBubbleBorder();

				if (isHighlighted || config.publicBgEnabled())
				{
					renderer.drawBubble(graphics, 0, bubbleY, bubbleWidth, bubbleHeight, bgColor, alpha);
				}
				renderer.drawBubbleBorder(graphics, 0, bubbleY, bubbleWidth, bubbleHeight,
					borderColor, showBorder, plugin.isPeekActive(), alpha);

				int textY = bubbleY + paddingY + fm.getAscent();
				drawTimestamp(graphics, timestampStr, paddingX, textY, customTimestampColor, alpha);

				int startX = paddingX + timestampWidth;
				renderer.renderSegments(graphics, faded, startX, textY, fm, startX + textWidth);
			}

			totalWidth = Math.max(totalWidth, bubbleWidth);

			if (layoutMode == LayoutMode.BOTTOM_TO_TOP)
			{
				y -= bubbleSpacing;
				if (y < 0)
				{
					break;
				}
			}
			else
			{
				y += bubbleHeight + bubbleSpacing;
			}
		}

		// ── Chatbox typing bubble (TOP_TO_BOTTOM) ─────────────────────────────
		if (layoutMode == LayoutMode.TOP_TO_BOTTOM && config.showChatboxMessage())
		{
			String typedText = plugin.getChatboxTypedText();
			if (!typedText.isEmpty())
			{
				String localName    = plugin.getLocalPlayerName();
				String displayName  = (localName != null && !localName.isEmpty()) ? localName : "You";
				Color  typingSender = new Color(255, 255, 255);
				Color  typingMsg    = new Color(255, 255, 0);

				ChatLineBuilder typingBuilder = new ChatLineBuilder(client, typingMsg, colorResolver.getChatColorConfig());
				typingBuilder.append(displayName + ": ", typingSender);
				typingBuilder.append(typedText);

				List<ColorSegment> typingSegs  = typingBuilder.getSegments();
				int                innerWidth   = maxWidth - paddingX * 2;

				int bubbleWidth;
				int bubbleHeight;

				if (config.publicWordWrap())
				{
					List<int[]> lineRanges = renderer.wrapText(typingSegs, fm, innerWidth);
					if (!lineRanges.isEmpty())
					{
						int maxLineW = 0;
						for (int[] range : lineRanges)
						{
							maxLineW = Math.max(maxLineW, renderer.getSlicedSegmentsWidth(typingSegs, range[0], range[1], fm));
						}
						bubbleWidth  = maxLineW + paddingX * 2;
						bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;

						renderer.drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, config.publicBgColor(), 1.0f);

						int textY = y + paddingY + fm.getAscent();
						for (int[] range : lineRanges)
						{
							List<ColorSegment> lineSegs = renderer.sliceSegments(typingSegs, range[0], range[1]);
							int lineW = renderer.getSlicedSegmentsWidth(typingSegs, range[0], range[1], fm);
							renderer.renderSegments(graphics, lineSegs, paddingX, textY, fm, paddingX + lineW);
							textY += fm.getHeight();
						}
						totalWidth = Math.max(totalWidth, bubbleWidth);
						y += bubbleHeight + config.bubbleSpacing();
					}
				}
				else
				{
					int textWidth = Math.min(renderer.getSegmentsWidth(typingSegs, fm), innerWidth);
					bubbleWidth  = textWidth + paddingX * 2;
					bubbleHeight = fm.getHeight() + paddingY * 2;

					renderer.drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, config.publicBgColor(), 1.0f);

					int textY = y + paddingY + fm.getAscent();
					renderer.renderSegments(graphics, typingSegs, paddingX, textY, fm, paddingX + textWidth);
					totalWidth = Math.max(totalWidth, bubbleWidth);
					y += bubbleHeight + config.bubbleSpacing();
				}
			}
		}

		if (layoutMode == LayoutMode.BOTTOM_TO_TOP)
		{
			if (y == maxHeight)
			{
				return null;
			}
			return new Dimension(Math.max(totalWidth, maxWidth), maxHeight);
		}
		else
		{
			if (y == 0)
			{
				return null;
			}
			return new Dimension(Math.max(totalWidth, maxWidth), y);
		}
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private void drawTimestamp(Graphics2D graphics, String timestampStr, int x, int textY, Color tsColor, float alpha)
	{
		if (!timestampStr.isEmpty())
		{
			Color tc = new Color(tsColor.getRed(), tsColor.getGreen(),
				tsColor.getBlue(), (int) (tsColor.getAlpha() * alpha));
			Color black = new Color(0, 0, 0, tc.getAlpha());

			TextStyle style = config.textStyle();

			if (style == TextStyle.OUTLINE || style == TextStyle.OUTLINE_SHADOW)
			{
				graphics.setColor(black);
				graphics.drawString(timestampStr, x - 1, textY);
				graphics.drawString(timestampStr, x + 1, textY);
				graphics.drawString(timestampStr, x, textY - 1);
				graphics.drawString(timestampStr, x, textY + 1);
				graphics.drawString(timestampStr, x - 1, textY - 1);
				graphics.drawString(timestampStr, x + 1, textY - 1);
				graphics.drawString(timestampStr, x - 1, textY + 1);
				graphics.drawString(timestampStr, x + 1, textY + 1);
			}

			if (style == TextStyle.SHADOW || style == TextStyle.SHADOW_BOLD || style == TextStyle.OUTLINE_SHADOW)
			{
				graphics.setColor(black);
				graphics.drawString(timestampStr, x + 1, textY + 1);
			}

			graphics.setColor(tc);
			graphics.drawString(timestampStr, x, textY);
		}
	}
}
