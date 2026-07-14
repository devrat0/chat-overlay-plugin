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
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Bubble-style overlay for Private chat messages.
 */
public class PrivateChatOverlay extends Overlay
{
	private final Client client;
	private final ChatOverlayPlugin plugin;
	private final ChatOverlayConfig config;
	private final BubbleRenderer    renderer;
	private final ChatColorResolver colorResolver;

	@Inject
	public PrivateChatOverlay(Client client, ChatOverlayPlugin plugin, ChatOverlayConfig config,
		BubbleRenderer renderer, ChatColorResolver colorResolver)
	{
		this.client        = client;
		this.plugin        = plugin;
		this.config        = config;
		this.renderer      = renderer;
		this.colorResolver = colorResolver;

		setPosition(OverlayPosition.TOP_LEFT);
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

		List<ChatLine> allMessages = plugin.getMessageManager().getPrivateMessages();
		List<ChatLine> messages = new java.util.ArrayList<>();
		for (ChatLine line : allMessages)
		{
			if (plugin.shouldShowMessage(line))
			{
				messages.add(line);
			}
		}
		if (messages.isEmpty())
		{
			return null;
		}

		int maxMsg = config.privateMaxMessages();
		if (messages.size() > maxMsg)
		{
			messages = messages.subList(messages.size() - maxMsg, messages.size());
		}

		renderer.configureRenderingHints(graphics);

		Font font = renderer.resolveFont();
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics(font);

		int maxWidth      = config.privateOverlayWidth();
		int maxHeight     = config.privateOverlayHeight();
		LayoutMode layoutMode = config.privateLayoutMode();
		long durMs        = config.privateMessageDuration() * 1000L;
		int paddingX      = config.bubblePaddingX();
		int paddingY      = config.bubblePaddingY();
		int bubbleSpacing = config.bubbleSpacing();

		int y          = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? maxHeight : 0;
		int totalWidth = 0;

		int startIdx = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? messages.size() - 1 : 0;
		int endIdx   = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? -1 : messages.size();
		int step     = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? -1 : 1;

		for (int i = startIdx; i != endIdx; i += step)
		{
			ChatLine line = messages.get(i);
			float alpha = renderer.computeAlpha(line, durMs, config.privateFadeIn(), config.privateFadeOut());
			if (plugin.isPeekActive())
			{
				alpha = 1.0f;
			}
			if (alpha <= 0.01f)
			{
				continue;
			}

			boolean isSender  = line.getChatMessageType() == ChatMessageType.PRIVATECHATOUT;
			Color senderColor = colorResolver.getSenderColor(line.getChatMessageType(), ChatColorType.NORMAL, isSender);
			Color msgColor    = plugin.getChatColor(line.getChatMessageType(), ChatColorType.HIGHLIGHT);

			Color customTimestampColor = config.privateTimestampColor() != null ? config.privateTimestampColor() : senderColor;
			Color customUsernameColor  = config.privateUsernameColor() != null ? config.privateUsernameColor() : senderColor;
			Color customTextColor      = config.privateTextColor() != null ? config.privateTextColor() : msgColor;

			// Timestamp rendered separately: [timestamp] [icon] username
			String timestampStr   = "";
			int    timestampWidth = 0;
			if (config.privateShowTimestamp())
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

			ChatLineBuilder builder = new ChatLineBuilder(client, customTextColor, colorResolver.getChatColorConfig());
			String senderName = config.showPlayerIcons() ? line.getRawSender() : line.getSender();
			builder.append(senderName, customUsernameColor);
			builder.append(": ");
			builder.append(line.getRawMessage());

			List<ColorSegment> allSegs    = builder.getSegments();
			int                innerWidth = maxWidth - paddingX * 2 - timestampWidth;
			List<ColorSegment> faded      = renderer.applyAlphaToSegments(allSegs, alpha);

			int bubbleWidth;
			int bubbleHeight;

			if (config.privateWordWrap())
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

				boolean isHighlighted = !config.privateDisableKeywordHighlight() && plugin.shouldHighlight(line);
				Color bgColor = isHighlighted ? config.highlightBgColor() : config.privateBgColor();
				Color borderColor = isHighlighted ? config.highlightBorderColor() : config.privateBubbleBorderColor();
				boolean showBorder = isHighlighted ? config.highlightShowBorder() : config.privateShowBubbleBorder();

				if (isHighlighted || config.privateBgEnabled())
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

				boolean isHighlighted = !config.privateDisableKeywordHighlight() && plugin.shouldHighlight(line);
				Color bgColor = isHighlighted ? config.highlightBgColor() : config.privateBgColor();
				Color borderColor = isHighlighted ? config.highlightBorderColor() : config.privateBubbleBorderColor();
				boolean showBorder = isHighlighted ? config.highlightShowBorder() : config.privateShowBubbleBorder();

				if (isHighlighted || config.privateBgEnabled())
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
