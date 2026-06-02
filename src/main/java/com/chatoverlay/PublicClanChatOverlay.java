package com.chatoverlay;

import java.awt.AlphaComposite;
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
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Bubble-style overlay for Public, Clan, and Friends Chat messages.
 */
public class PublicClanChatOverlay extends Overlay
{
	private final ChatOverlayPlugin plugin;
	private final ChatOverlayConfig config;
	private final BubbleRenderer    renderer;
	private final ChatColorResolver colorResolver;

	@Inject
	public PublicClanChatOverlay(ChatOverlayPlugin plugin, ChatOverlayConfig config,
		BubbleRenderer renderer, ChatColorResolver colorResolver)
	{
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

		List<ChatLine> messages = plugin.getMessageManager().getPublicClanMessages();
		if (messages.isEmpty())
		{
			return null;
		}

		int maxMsg = config.publicMaxMessages();
		if (messages.size() > maxMsg)
		{
			messages = messages.subList(messages.size() - maxMsg, messages.size());
		}

		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

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

				ChatLineBuilder typingBuilder = new ChatLineBuilder(typingMsg, colorResolver.getChatColorConfig());
				typingBuilder.append(displayName + ": ", typingSender);
				typingBuilder.append(typedText);

				List<ColorSegment> typingSegs  = typingBuilder.getSegments();
				String             typingPlain  = typingBuilder.toPlainString();
				int                innerWidth   = maxWidth - paddingX * 2;

				int bubbleWidth;
				int bubbleHeight;

				if (config.publicWordWrap())
				{
					List<int[]> lineRanges = renderer.wrapText(typingPlain, fm, innerWidth);
					if (!lineRanges.isEmpty())
					{
						int maxLineW = 0;
						for (int[] range : lineRanges)
						{
							maxLineW = Math.max(maxLineW, fm.stringWidth(typingPlain.substring(range[0], range[1])));
						}
						bubbleWidth  = maxLineW + paddingX * 2;
						bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;

						y -= bubbleHeight;
						renderer.drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, config.publicBgColor(), 1.0f);

						int textY = y + paddingY + fm.getAscent();
						for (int[] range : lineRanges)
						{
							List<ColorSegment> lineSegs = renderer.sliceSegments(typingSegs, range[0], range[1]);
							int lineW = fm.stringWidth(typingPlain.substring(range[0], range[1]));
							renderer.renderSegments(graphics, lineSegs, paddingX, textY, fm, paddingX + lineW);
							textY += fm.getHeight();
						}
						totalWidth = Math.max(totalWidth, bubbleWidth);
						y -= bubbleSpacing;
					}
				}
				else
				{
					int textWidth = Math.min(fm.stringWidth(typingPlain), innerWidth);
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
			float alpha = renderer.computeAlpha(line, durMs);
			if (plugin.isPeekActive() || (!config.publicFadeMessages() && alpha > 0f))
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

			// Timestamp rendered separately: [timestamp] [icon] username
			String timestampStr   = "";
			int    timestampWidth = 0;
			if (config.publicShowTimestamp())
			{
				LocalTime time = LocalTime.ofInstant(
					Instant.ofEpochMilli(line.getTimestamp()), ZoneId.systemDefault());
				timestampStr   = String.format("[%02d:%02d] ", time.getHour(), time.getMinute());
				timestampWidth = fm.stringWidth(timestampStr);
			}

			BufferedImage icon    = config.showPlayerIcons() ? line.getIcon() : null;
			int           iconOffsetX = 0;
			if (icon != null)
			{
				int iconH = fm.getHeight();
				int iconW = (int) ((double) icon.getWidth() * iconH / icon.getHeight());
				iconOffsetX = iconW + 4;
			}

			ChatLineBuilder builder     = new ChatLineBuilder(msgColor, colorResolver.getChatColorConfig());
			String          channelName = line.getChannelName();
			if (channelName != null && !channelName.isEmpty())
			{
				builder.append("[" + channelName + "] ", colorResolver.getChannelNameColor(line.getChatMessageType()));
			}
			if (!line.getSender().isEmpty())
			{
				builder.append(line.getRawSender(), senderColor);
				builder.append(": ");
			}
			builder.append(line.getRawMessage());

			List<ColorSegment> allSegs    = builder.getSegments();
			String             plain      = builder.toPlainString();
			int                innerWidth = maxWidth - paddingX * 2 - timestampWidth - iconOffsetX;
			List<ColorSegment> faded      = renderer.applyAlphaToSegments(allSegs, alpha);
			int                textStartX = paddingX + timestampWidth + iconOffsetX;

			int bubbleWidth;
			int bubbleHeight;

			if (config.publicWordWrap())
			{
				List<int[]> lineRanges = renderer.wrapText(plain, fm, innerWidth);
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

				int bubbleY;
				if (layoutMode == LayoutMode.BOTTOM_TO_TOP)
				{
					y -= bubbleHeight;
					bubbleY = y;
				}
				else
				{
					bubbleY = y;
				}

				renderer.drawBubble(graphics, 0, bubbleY, bubbleWidth, bubbleHeight, config.publicBgColor(), alpha);
				renderer.drawBubbleBorder(graphics, 0, bubbleY, bubbleWidth, bubbleHeight,
					config.publicBubbleBorderColor(), config.publicShowBubbleBorder(), plugin.isPeekActive(), alpha);

				int textY = bubbleY + paddingY + fm.getAscent();
				drawTimestamp(graphics, timestampStr, paddingX, textY, senderColor, alpha);

				int startX = paddingX + timestampWidth;
				boolean isFirst = true;
				for (int[] range : lineRanges)
				{
					List<ColorSegment> lineSegs = renderer.sliceSegments(faded, range[0], range[1]);
					int lineW = fm.stringWidth(plain.substring(range[0], range[1]));

					if (isFirst && icon != null)
					{
						String chanText = (channelName != null && !channelName.isEmpty()) ? "[" + channelName + "] " : "";
						int chanLen = chanText.length();
						int relativeSplit = chanLen - range[0];

						if (relativeSplit <= 0)
						{
							drawIcon(graphics, fm, icon, startX, bubbleY, paddingY, alpha);
							renderer.renderSegments(graphics, lineSegs, startX + iconOffsetX, textY, fm, startX + iconOffsetX + lineW);
						}
						else if (relativeSplit >= range[1] - range[0])
						{
							renderer.renderSegments(graphics, lineSegs, startX, textY, fm, startX + lineW);
						}
						else
						{
							List<ColorSegment> chanSegs = renderer.sliceSegments(faded, range[0], chanLen);
							List<ColorSegment> senderSegs = renderer.sliceSegments(faded, chanLen, range[1]);

							int chanW = fm.stringWidth(chanText);
							renderer.renderSegments(graphics, chanSegs, startX, textY, fm, startX + chanW);
							drawIcon(graphics, fm, icon, startX + chanW, bubbleY, paddingY, alpha);

							int contentStartX = startX + chanW + iconOffsetX;
							int contentW = fm.stringWidth(plain.substring(chanLen, range[1]));
							renderer.renderSegments(graphics, senderSegs, contentStartX, textY, fm, contentStartX + contentW);
						}
						isFirst = false;
					}
					else
					{
						renderer.renderSegments(graphics, lineSegs, startX, textY, fm, startX + lineW);
					}
					textY += fm.getHeight();
				}
			}
			else
			{
				int textWidth = Math.min(fm.stringWidth(plain), innerWidth);
				bubbleWidth  = textWidth + timestampWidth + iconOffsetX + paddingX * 2;
				bubbleHeight = fm.getHeight() + paddingY * 2;

				int bubbleY;
				if (layoutMode == LayoutMode.BOTTOM_TO_TOP)
				{
					y -= bubbleHeight;
					bubbleY = y;
				}
				else
				{
					bubbleY = y;
				}

				renderer.drawBubble(graphics, 0, bubbleY, bubbleWidth, bubbleHeight, config.publicBgColor(), alpha);
				renderer.drawBubbleBorder(graphics, 0, bubbleY, bubbleWidth, bubbleHeight,
					config.publicBubbleBorderColor(), config.publicShowBubbleBorder(), plugin.isPeekActive(), alpha);

				int textY = bubbleY + paddingY + fm.getAscent();
				drawTimestamp(graphics, timestampStr, paddingX, textY, senderColor, alpha);

				int startX = paddingX + timestampWidth;
				if (icon != null)
				{
					String chanText = (channelName != null && !channelName.isEmpty()) ? "[" + channelName + "] " : "";
					if (!chanText.isEmpty())
					{
						int chanLen = chanText.length();
						int plainLen = plain.length();

						List<ColorSegment> chanSegs = renderer.sliceSegments(faded, 0, chanLen);
						List<ColorSegment> senderSegs = renderer.sliceSegments(faded, chanLen, plainLen);

						int chanW = fm.stringWidth(chanText);
						renderer.renderSegments(graphics, chanSegs, startX, textY, fm, startX + chanW);
						drawIcon(graphics, fm, icon, startX + chanW, bubbleY, paddingY, alpha);

						int contentStartX = startX + chanW + iconOffsetX;
						renderer.renderSegments(graphics, senderSegs, contentStartX, textY, fm, contentStartX + textWidth - chanW);
					}
					else
					{
						drawIcon(graphics, fm, icon, startX, bubbleY, paddingY, alpha);
						renderer.renderSegments(graphics, faded, startX + iconOffsetX, textY, fm, startX + iconOffsetX + textWidth);
					}
				}
				else
				{
					renderer.renderSegments(graphics, faded, startX, textY, fm, startX + textWidth);
				}
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

				ChatLineBuilder typingBuilder = new ChatLineBuilder(typingMsg, colorResolver.getChatColorConfig());
				typingBuilder.append(displayName + ": ", typingSender);
				typingBuilder.append(typedText);

				List<ColorSegment> typingSegs  = typingBuilder.getSegments();
				String             typingPlain  = typingBuilder.toPlainString();
				int                innerWidth   = maxWidth - paddingX * 2;

				int bubbleWidth;
				int bubbleHeight;

				if (config.publicWordWrap())
				{
					List<int[]> lineRanges = renderer.wrapText(typingPlain, fm, innerWidth);
					if (!lineRanges.isEmpty())
					{
						int maxLineW = 0;
						for (int[] range : lineRanges)
						{
							maxLineW = Math.max(maxLineW, fm.stringWidth(typingPlain.substring(range[0], range[1])));
						}
						bubbleWidth  = maxLineW + paddingX * 2;
						bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;

						renderer.drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, config.publicBgColor(), 1.0f);

						int textY = y + paddingY + fm.getAscent();
						for (int[] range : lineRanges)
						{
							List<ColorSegment> lineSegs = renderer.sliceSegments(typingSegs, range[0], range[1]);
							int lineW = fm.stringWidth(typingPlain.substring(range[0], range[1]));
							renderer.renderSegments(graphics, lineSegs, paddingX, textY, fm, paddingX + lineW);
							textY += fm.getHeight();
						}
						totalWidth = Math.max(totalWidth, bubbleWidth);
						y += bubbleHeight + config.bubbleSpacing();
					}
				}
				else
				{
					int textWidth = Math.min(fm.stringWidth(typingPlain), innerWidth);
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

	private void drawTimestamp(Graphics2D graphics, String timestampStr, int x, int textY, Color senderColor, float alpha)
	{
		if (!timestampStr.isEmpty())
		{
			Color tc = new Color(senderColor.getRed(), senderColor.getGreen(),
				senderColor.getBlue(), (int) (senderColor.getAlpha() * alpha));
			graphics.setColor(new Color(0, 0, 0, tc.getAlpha()));
			graphics.drawString(timestampStr, x + 1, textY + 1);
			graphics.setColor(tc);
			graphics.drawString(timestampStr, x, textY);
		}
	}

	private void drawIcon(Graphics2D graphics, FontMetrics fm, BufferedImage icon, int x, int bubbleY, int paddingY, float alpha)
	{
		if (icon != null)
		{
			int iconH = fm.getHeight();
			int iconW = (int) ((double) icon.getWidth() * iconH / icon.getHeight());
			Composite orig = graphics.getComposite();
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
			graphics.drawImage(icon, x, bubbleY + paddingY, iconW, iconH, null);
			graphics.setComposite(orig);
		}
	}
}
