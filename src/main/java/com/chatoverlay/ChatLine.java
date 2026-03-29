package com.chatoverlay;

import net.runelite.api.ChatMessageType;

/**
 * Represents a single chat message captured from the game.
 *
 * <p>{@code rawMessage} and {@code rawSender} preserve OSRS {@code <col=RRGGBB>}
 * tags so overlays can render the exact colors from ChatColorPlugin or the server.
 * {@code channelName} carries the real clan/FC name (e.g. "Laced PVM") so the
 * overlay can display it instead of a generic "[Clan]" label.</p>
 *
 * <p>Colors are intentionally NOT stored here. Overlays resolve colors at render
 * time via {@link ChatOverlayPlugin#getChatColor} so they always reflect the
 * current transparency mode and the user's ChatColorPlugin settings.</p>
 */
public class ChatLine
{
	private final String sender;
	private final String rawSender;
	private final String rawMessage;
	private final String plainMessage;
	private final long timestamp;
	private final ChatCategory category;
	private final ChatMessageType chatMessageType;

	/** The clan or FC channel name (e.g. "Laced PVM"), or null for non-channel messages. */
	private final String channelName;

	public ChatLine(
		String sender,
		String rawSender,
		String rawMessage,
		ChatCategory category,
		ChatMessageType chatMessageType,
		String channelName)
	{
		this.sender = sender == null ? "" : sender;
		this.rawSender = rawSender == null ? "" : rawSender;
		this.rawMessage = rawMessage == null ? "" : rawMessage;
		this.plainMessage = ColorTagParser.stripTags(this.rawMessage);
		this.timestamp = System.currentTimeMillis();
		this.category = category;
		this.chatMessageType = chatMessageType;
		this.channelName = channelName;
	}

	/** Convenience constructor for messages that don't belong to a named channel. */
	public ChatLine(
		String sender,
		String rawSender,
		String rawMessage,
		ChatCategory category,
		ChatMessageType chatMessageType)
	{
		this(sender, rawSender, rawMessage, category, chatMessageType, null);
	}

	public String getSender()
	{
		return sender;
	}

	public String getRawSender()
	{
		return rawSender;
	}

	public String getRawMessage()
	{
		return rawMessage;
	}

	public String getPlainMessage()
	{
		return plainMessage;
	}

	public long getTimestamp()
	{
		return timestamp;
	}

	public ChatCategory getCategory()
	{
		return category;
	}

	/**
	 * The RuneLite {@link ChatMessageType} for this line. Used by overlays to
	 * look up the correct color from {@link ChatOverlayPlugin#getChatColor}.
	 */
	public ChatMessageType getChatMessageType()
	{
		return chatMessageType;
	}

	/**
	 * The real channel name for clan/FC messages (e.g. "Laced PVM"),
	 * or {@code null} for public/private/system messages.
	 */
	public String getChannelName()
	{
		return channelName;
	}

	public long getAge()
	{
		return System.currentTimeMillis() - timestamp;
	}

}
