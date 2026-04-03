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
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * System/game message alert overlay.
 */
public class GameOverlay extends Overlay
{
	private static final int ABOVE_PLAYER_OFFSET = 90;
	private static final int MAX_BUBBLE_WIDTH    = 350;

	private final Client            client;
	private final ChatOverlayPlugin plugin;
	private final ChatOverlayConfig config;
	private final BubbleRenderer    renderer;
	private final ChatColorResolver colorResolver;

	private GameOverlayMode lastMode = null;

	@Inject
	public GameOverlay(Client client, ChatOverlayPlugin plugin, ChatOverlayConfig config,
		BubbleRenderer renderer, ChatColorResolver colorResolver)
	{
		this.client        = client;
		this.plugin        = plugin;
		this.config        = config;
		this.renderer      = renderer;
		this.colorResolver = colorResolver;

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
				setPreferredLocation(null);
				setPreferredSize(null);
			}
		}

		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		Font font = renderer.resolveFont();
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics(font);
		long durationMs = config.systemAlertDuration() * 1000L;

		return mode == GameOverlayMode.OVERLAY
			? renderAsOverlay(graphics, alerts, fm, durationMs)
			: renderPinnedToPlayer(graphics, alerts, fm, durationMs);
	}

	// ── Pinned-to-Player mode ───────────────────────────────────────────────

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
			float alpha = renderer.computeAlphaWithFadeIn(alert.getAge(), durationMs);
			if (plugin.isPeekActive() || (!config.systemFadeMessages() && alpha > 0f))
			{
				alpha = 1.0f;
			}
			if (alpha <= 0.01f)
			{
				continue;
			}

			List<ColorSegment> faded = buildAlertSegments(alert, fm, alpha);
			String plain      = buildAlertPlain(alert);
			int    innerWidth = MAX_BUBBLE_WIDTH - paddingX * 2;

			int bubbleWidth;
			int bubbleHeight;

			if (config.systemWordWrap())
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
				bubbleWidth  = maxLineW + paddingX * 2;
				bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;
				int bubbleX  = centerX - bubbleWidth / 2;
				int bubbleY  = currentY - bubbleHeight;

				renderer.drawBubble(graphics, bubbleX, bubbleY, bubbleWidth, bubbleHeight, config.systemBgColor(), alpha);
				renderer.drawBubbleBorder(graphics, bubbleX, bubbleY, bubbleWidth, bubbleHeight,
					config.systemBubbleBorderColor(), config.systemShowBubbleBorder(), plugin.isPeekActive(), alpha);

				int textY = bubbleY + paddingY + fm.getAscent();
				for (int[] range : lineRanges)
				{
					List<ColorSegment> lineSegs = renderer.sliceSegments(faded, range[0], range[1]);
					int lineW      = fm.stringWidth(plain.substring(range[0], range[1]));
					int textStartX = centerX - lineW / 2;
					renderer.renderSegments(graphics, lineSegs, textStartX, textY, fm, textStartX + lineW);
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

				renderer.drawBubble(graphics, bubbleX, bubbleY, bubbleWidth, bubbleHeight, config.systemBgColor(), alpha);
				renderer.drawBubbleBorder(graphics, bubbleX, bubbleY, bubbleWidth, bubbleHeight,
					config.systemBubbleBorderColor(), config.systemShowBubbleBorder(), plugin.isPeekActive(), alpha);

				int textY      = bubbleY + paddingY + fm.getAscent();
				int textStartX = centerX - textWidth / 2;
				renderer.renderSegments(graphics, faded, textStartX, textY, fm, textStartX + textWidth);
				currentY = bubbleY - bubbleSpacing;
			}
		}

		return null; // DYNAMIC overlays don't return a bounding Dimension
	}

	// ── Free-Overlay mode ───────────────────────────────────────────────────

	private Dimension renderAsOverlay(
		Graphics2D graphics, List<ChatLine> alerts, FontMetrics fm, long durationMs)
	{
		int paddingX      = config.bubblePaddingX();
		int paddingY      = config.bubblePaddingY();
		int bubbleSpacing = config.bubbleSpacing();

		int y          = 0;
		int totalWidth = 0;

		for (int i = alerts.size() - 1; i >= 0; i--)
		{
			ChatLine alert = alerts.get(i);
			float alpha = renderer.computeAlphaWithFadeIn(alert.getAge(), durationMs);
			if (plugin.isPeekActive() || (!config.systemFadeMessages() && alpha > 0f))
			{
				alpha = 1.0f;
			}
			if (alpha <= 0.01f)
			{
				continue;
			}

			List<ColorSegment> faded = buildAlertSegments(alert, fm, alpha);
			String plain      = buildAlertPlain(alert);
			int    innerWidth = MAX_BUBBLE_WIDTH - paddingX * 2;

			int bubbleWidth;
			int bubbleHeight;

			if (config.systemWordWrap())
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
				bubbleWidth  = maxLineW + paddingX * 2;
				bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;

				renderer.drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, config.systemBgColor(), alpha);
				renderer.drawBubbleBorder(graphics, 0, y, bubbleWidth, bubbleHeight,
					config.systemBubbleBorderColor(), config.systemShowBubbleBorder(), plugin.isPeekActive(), alpha);

				int textY = y + paddingY + fm.getAscent();
				for (int[] range : lineRanges)
				{
					List<ColorSegment> lineSegs = renderer.sliceSegments(faded, range[0], range[1]);
					int lineW = fm.stringWidth(plain.substring(range[0], range[1]));
					renderer.renderSegments(graphics, lineSegs, paddingX, textY, fm, paddingX + lineW);
					textY += fm.getHeight();
				}
			}
			else
			{
				int textWidth = Math.min(fm.stringWidth(plain), innerWidth);
				bubbleWidth  = textWidth + paddingX * 2;
				bubbleHeight = fm.getHeight() + paddingY * 2;

				renderer.drawBubble(graphics, 0, y, bubbleWidth, bubbleHeight, config.systemBgColor(), alpha);
				renderer.drawBubbleBorder(graphics, 0, y, bubbleWidth, bubbleHeight,
					config.systemBubbleBorderColor(), config.systemShowBubbleBorder(), plugin.isPeekActive(), alpha);

				int textY = y + paddingY + fm.getAscent();
				renderer.renderSegments(graphics, faded, paddingX, textY, fm, paddingX + textWidth);
			}

			totalWidth = Math.max(totalWidth, bubbleWidth);
			y += bubbleHeight + bubbleSpacing;
		}

		if (y == 0)
		{
			return null;
		}
		return new Dimension(totalWidth, y);
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	/** Builds the plain text for an alert (timestamp + message). */
	private String buildAlertPlain(ChatLine alert)
	{
		if (!config.systemShowTimestamp())
		{
			return alert.getRawMessage();
		}
		LocalTime time = LocalTime.ofInstant(
			Instant.ofEpochMilli(alert.getTimestamp()), ZoneId.systemDefault());
		return String.format("[%02d:%02d] ", time.getHour(), time.getMinute()) + alert.getRawMessage();
	}

	/** Builds alpha-faded segments for an alert. */
	private List<ColorSegment> buildAlertSegments(ChatLine alert, FontMetrics fm, float alpha)
	{
		Color msgColor = colorResolver.getChatColor(alert.getChatMessageType(), ChatColorType.HIGHLIGHT);
		ChatLineBuilder builder = new ChatLineBuilder(msgColor, colorResolver.getChatColorConfig());
		if (config.systemShowTimestamp())
		{
			LocalTime time = LocalTime.ofInstant(
				Instant.ofEpochMilli(alert.getTimestamp()), ZoneId.systemDefault());
			builder.append(String.format("[%02d:%02d] ", time.getHour(), time.getMinute()), msgColor);
		}
		builder.append(alert.getRawMessage());
		return renderer.applyAlphaToSegments(builder.getSegments(), alpha);
	}
}
