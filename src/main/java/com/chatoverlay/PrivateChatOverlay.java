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
import net.runelite.api.ChatMessageType;
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
	private final ChatOverlayPlugin plugin;
	private final ChatOverlayConfig config;
	private final BubbleRenderer    renderer;
	private final ChatColorResolver colorResolver;

	@Inject
	public PrivateChatOverlay(ChatOverlayPlugin plugin, ChatOverlayConfig config,
		BubbleRenderer renderer, ChatColorResolver colorResolver)
	{
		this.plugin        = plugin;
		this.config        = config;
		this.renderer      = renderer;
		this.colorResolver = colorResolver;

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

		int maxMsg = config.privateMaxMessages();
		if (messages.size() > maxMsg)
		{
			messages = messages.subList(messages.size() - maxMsg, messages.size());
		}

		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		Font font = renderer.resolveFont();
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
			float alpha = renderer.computeAlpha(line.getAge(), durMs);
			if (plugin.isPeekActive() || (!config.privateFadeMessages() && alpha > 0f))
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

			// Timestamp rendered separately: [timestamp] [icon] username
			String timestampStr   = "";
			int    timestampWidth = 0;
			if (config.privateShowTimestamp())
			{
				LocalTime time = LocalTime.ofInstant(
					Instant.ofEpochMilli(line.getTimestamp()), ZoneId.systemDefault());
				timestampStr   = String.format("[%02d:%02d] ", time.getHour(), time.getMinute());
				timestampWidth = fm.stringWidth(timestampStr);
			}

			BufferedImage icon       = config.showPlayerIcons() ? line.getIcon() : null;
			int           iconOffsetX = 0;
			if (icon != null)
			{
				int iconH = fm.getHeight();
				int iconW = (int) ((double) icon.getWidth() * iconH / icon.getHeight());
				iconOffsetX = iconW + 4;
			}

			ChatLineBuilder builder = new ChatLineBuilder(msgColor, colorResolver.getChatColorConfig());
			builder.append(line.getRawSender(), senderColor);
			builder.append(": ");
			builder.append(line.getRawMessage());

			List<ColorSegment> allSegs    = builder.getSegments();
			String             plain      = builder.toPlainString();
			int                innerWidth = maxWidth - paddingX * 2 - timestampWidth - iconOffsetX;
			List<ColorSegment> faded      = renderer.applyAlphaToSegments(allSegs, alpha);
			int                textStartX = paddingX + timestampWidth + iconOffsetX;

			int bubbleWidth;
			int bubbleHeight;

			if (config.privateWordWrap())
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

				renderer.drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, config.privateBgColor(), alpha);
				renderer.drawBubbleBorder(graphics, 0, y, bubbleWidth, bubbleHeight,
					config.privateBubbleBorderColor(), config.privateShowBubbleBorder(), plugin.isPeekActive(), alpha);

				int textY = y + paddingY + fm.getAscent();
				drawTimestampAndIcon(graphics, fm, timestampStr, timestampWidth, icon, iconOffsetX,
					paddingX, y, paddingY, textY, senderColor, alpha);

				for (int[] range : lineRanges)
				{
					List<ColorSegment> lineSegs = renderer.sliceSegments(faded, range[0], range[1]);
					int lineW = fm.stringWidth(plain.substring(range[0], range[1]));
					renderer.renderSegments(graphics, lineSegs, textStartX, textY, fm, textStartX + lineW);
					textY += fm.getHeight();
				}
			}
			else
			{
				int textWidth = Math.min(fm.stringWidth(plain), innerWidth);
				bubbleWidth  = textWidth + timestampWidth + iconOffsetX + paddingX * 2;
				bubbleHeight = fm.getHeight() + paddingY * 2;

				renderer.drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, config.privateBgColor(), alpha);
				renderer.drawBubbleBorder(graphics, 0, y, bubbleWidth, bubbleHeight,
					config.privateBubbleBorderColor(), config.privateShowBubbleBorder(), plugin.isPeekActive(), alpha);

				int textY = y + paddingY + fm.getAscent();
				drawTimestampAndIcon(graphics, fm, timestampStr, timestampWidth, icon, iconOffsetX,
					paddingX, y, paddingY, textY, senderColor, alpha);
				renderer.renderSegments(graphics, faded, textStartX, textY, fm, textStartX + textWidth);
			}

			totalWidth = Math.max(totalWidth, bubbleWidth);
			y += bubbleHeight + bubbleSpacing;
		}

		if (y == 0)
		{
			return null;
		}
		return new Dimension(Math.max(totalWidth, maxWidth), y);
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private void drawTimestampAndIcon(Graphics2D graphics, FontMetrics fm,
		String timestampStr, int timestampWidth,
		BufferedImage icon, int iconOffsetX,
		int paddingX, int bubbleY, int paddingY, int textY,
		Color senderColor, float alpha)
	{
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
			graphics.drawImage(icon, paddingX + timestampWidth, bubbleY + paddingY, iconW, iconH, null);
			graphics.setComposite(orig);
		}
	}
}
