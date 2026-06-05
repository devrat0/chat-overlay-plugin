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
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Dedicated overlay for Clan, Guest Clan, GIM, and Friends Chat messages.
 */
public class ClanChatOverlay extends Overlay {
    private final Client client;
    private final ChatOverlayPlugin plugin;
    private final ChatOverlayConfig config;
    private final BubbleRenderer renderer;
    private final ChatColorResolver colorResolver;

    @Inject
    public ClanChatOverlay(Client client, ChatOverlayPlugin plugin, ChatOverlayConfig config,
                           BubbleRenderer renderer, ChatColorResolver colorResolver) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.renderer = renderer;
        this.colorResolver = colorResolver;

        setPosition(OverlayPosition.BOTTOM_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_LOW);
        setMovable(true);
        setSnappable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showClanChatOverlay()) {
            return null;
        }

        List<ChatLine> messages = plugin.getMessageManager().getClanMessages();
        if (messages.isEmpty()) {
            return null;
        }

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        Font font = renderer.resolveFont();
        graphics.setFont(font);
        FontMetrics fm = graphics.getFontMetrics(font);

        int maxWidth = config.clanOverlayWidth();
        int maxHeight = config.clanOverlayHeight();
        LayoutMode layoutMode = config.clanLayoutMode();
        long durMs = config.clanMessageDuration() * 1000L;
        int paddingX = config.bubblePaddingX();
        int paddingY = config.bubblePaddingY();
        int bubbleSpacing = config.bubbleSpacing();

        int y = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? maxHeight : 0;
        int totalWidth = 0;

        int startIdx = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? messages.size() - 1 : 0;
        int endIdx = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? -1 : messages.size();
        int step = (layoutMode == LayoutMode.BOTTOM_TO_TOP) ? -1 : 1;

        for (int i = startIdx; i != endIdx; i += step) {
            ChatLine line = messages.get(i);
            float alpha = renderer.computeAlpha(line, durMs);
            if (plugin.isPeekActive()) {
                alpha = 1.0f;
            }
            if (alpha <= 0.01f) {
                continue;
            }

            String localName = plugin.getLocalPlayerName();
            boolean isSender = localName != null && localName.equalsIgnoreCase(line.getRawSender());
            Color senderColor = colorResolver.getSenderColor(line.getChatMessageType(), ChatColorType.NORMAL, isSender);
            Color msgColor = colorResolver.getChatColor(line.getChatMessageType(), ChatColorType.HIGHLIGHT);

            // Timestamp rendered separately: [timestamp] [icon] username
            String timestampStr = "";
            int timestampWidth = 0;
            if (config.clanShowTimestamp()) {
                LocalTime time = LocalTime.ofInstant(
                        Instant.ofEpochMilli(line.getTimestamp()), ZoneId.systemDefault());
                timestampStr = String.format("[%02d:%02d] ", time.getHour(), time.getMinute());
                timestampWidth = fm.stringWidth(timestampStr);
            }

            ChatLineBuilder builder = new ChatLineBuilder(client, msgColor, colorResolver.getChatColorConfig());
            String channelName = line.getChannelName();
            if (channelName != null && !channelName.isEmpty()) {
                builder.append("[" + channelName + "] ", colorResolver.getChannelNameColor(line.getChatMessageType()));
            }
            if (!line.getSender().isEmpty()) {
                String senderName = config.showPlayerIcons() ? line.getRawSender() : line.getSender();
                builder.append(senderName, senderColor);
                builder.append(": ");
            }
            builder.append(line.getRawMessage());

            List<ColorSegment> allSegs = builder.getSegments();
            int innerWidth = maxWidth - paddingX * 2 - timestampWidth;
            List<ColorSegment> faded = renderer.applyAlphaToSegments(allSegs, alpha);

            int bubbleWidth;
            int bubbleHeight;

            if (config.publicWordWrap()) {
                List<int[]> lineRanges = renderer.wrapText(faded, fm, innerWidth);
                if (lineRanges.isEmpty()) {
                    continue;
                }
                int maxLineW = 0;
                for (int[] range : lineRanges) {
                    maxLineW = Math.max(maxLineW, renderer.getSlicedSegmentsWidth(faded, range[0], range[1], fm));
                }
                bubbleWidth = maxLineW + timestampWidth + paddingX * 2;
                bubbleHeight = fm.getHeight() * lineRanges.size() + paddingY * 2;

                int bubbleY;
                if (layoutMode == LayoutMode.BOTTOM_TO_TOP) {
                    y -= bubbleHeight;
                    bubbleY = y;
                } else {
                    bubbleY = y;
                }

                if (config.clanBgEnabled()) {
                    renderer.drawBubble(graphics, 0, bubbleY, bubbleWidth, bubbleHeight, config.clanBgColor(), alpha);
                }
                renderer.drawBubbleBorder(graphics, 0, bubbleY, bubbleWidth, bubbleHeight,
                        config.clanBubbleBorderColor(), config.clanShowBubbleBorder(), plugin.isPeekActive(), alpha);

                int textY = bubbleY + paddingY + fm.getAscent();
                drawTimestamp(graphics, timestampStr, paddingX, textY, senderColor, alpha);

                int startX = paddingX + timestampWidth;
                for (int[] range : lineRanges) {
                    List<ColorSegment> lineSegs = renderer.sliceSegments(faded, range[0], range[1]);
                    int lineW = renderer.getSlicedSegmentsWidth(faded, range[0], range[1], fm);
                    renderer.renderSegments(graphics, lineSegs, startX, textY, fm, startX + lineW);
                    textY += fm.getHeight();
                }
            } else {
                int textWidth = Math.min(renderer.getSegmentsWidth(faded, fm), innerWidth);
                bubbleWidth = textWidth + timestampWidth + paddingX * 2;
                bubbleHeight = fm.getHeight() + paddingY * 2;

                int bubbleY;
                if (layoutMode == LayoutMode.BOTTOM_TO_TOP) {
                    y -= bubbleHeight;
                    bubbleY = y;
                } else {
                    bubbleY = y;
                }

                if (config.clanBgEnabled()) {
                    renderer.drawBubble(graphics, 0, bubbleY, bubbleWidth, bubbleHeight, config.clanBgColor(), alpha);
                }
                renderer.drawBubbleBorder(graphics, 0, bubbleY, bubbleWidth, bubbleHeight,
                        config.clanBubbleBorderColor(), config.clanShowBubbleBorder(), plugin.isPeekActive(), alpha);

                int textY = bubbleY + paddingY + fm.getAscent();
                drawTimestamp(graphics, timestampStr, paddingX, textY, senderColor, alpha);

                int startX = paddingX + timestampWidth;
                renderer.renderSegments(graphics, faded, startX, textY, fm, startX + textWidth);
            }

            totalWidth = Math.max(totalWidth, bubbleWidth);

            if (layoutMode == LayoutMode.BOTTOM_TO_TOP) {
                y -= bubbleSpacing;
                if (y < 0) {
                    break;
                }
            } else {
                y += bubbleHeight + bubbleSpacing;
            }
        }

        if (layoutMode == LayoutMode.BOTTOM_TO_TOP) {
            if (y == maxHeight) {
                return null;
            }
            return new Dimension(Math.max(totalWidth, maxWidth), maxHeight);
        } else {
            if (y == 0) {
                return null;
            }
            return new Dimension(Math.max(totalWidth, maxWidth), y);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void drawTimestamp(Graphics2D graphics, String timestampStr, int x, int textY, Color senderColor, float alpha) {
        if (!timestampStr.isEmpty()) {
            Color tc = new Color(senderColor.getRed(), senderColor.getGreen(),
                    senderColor.getBlue(), (int) (senderColor.getAlpha() * alpha));
            graphics.setColor(new Color(0, 0, 0, tc.getAlpha()));
            graphics.drawString(timestampStr, x + 1, textY + 1);
            graphics.setColor(tc);
            graphics.drawString(timestampStr, x, textY);
        }
    }
}
