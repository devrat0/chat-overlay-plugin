package com.chatoverlay;

import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;

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
	private final long timestamp;
	private final ChatCategory category;
	private final ChatMessageType chatMessageType;
	private final MessageNode messageNode;

	private volatile String cachedRawMessage;
	private volatile String cachedPlainMessage;

	/** The clan or FC channel name (e.g. "Laced PVM"), or null for non-channel messages. */
	private final String channelName;

	private static final java.util.regex.Pattern CA_PREFIX_PATTERN =
		java.util.regex.Pattern.compile("^((?:<[^>]+>)*)CA_ID:(?:<[^>]+>)*\\d+(?:<[^>]+>)*\\|\\s*");

	private static String cleanPrefixes(String msg)
	{
		if (msg == null)
		{
			return null;
		}
		return CA_PREFIX_PATTERN.matcher(msg).replaceFirst("$1");
	}


	public ChatLine(
		MessageNode messageNode,
		String sender,
		String rawSender,
		String rawMessage,
		ChatCategory category,
		ChatMessageType chatMessageType,
		String channelName)
	{
		this.messageNode = messageNode;
		this.sender = sender == null ? "" : sender;
		this.rawSender = rawSender == null ? "" : rawSender;
		this.rawMessage = rawMessage == null ? "" : cleanPrefixes(rawMessage);
		this.timestamp = System.currentTimeMillis();
		this.category = category;
		this.chatMessageType = chatMessageType;
		this.channelName = channelName;

		String initialRaw = getRawMessage();
		this.cachedRawMessage = initialRaw;
		this.cachedPlainMessage = ColorTagParser.stripTags(initialRaw);
	}

	public ChatLine(
		MessageNode messageNode,
		String sender,
		String rawSender,
		String rawMessage,
		ChatCategory category,
		ChatMessageType chatMessageType)
	{
		this(messageNode, sender, rawSender, rawMessage, category, chatMessageType, null);
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
		if (messageNode != null)
		{
			String rfmt = messageNode.getRuneLiteFormatMessage();
			String currentRaw = (rfmt != null && !rfmt.isEmpty()) ? rfmt : messageNode.getValue();
			if (currentRaw != null)
			{
				return cleanPrefixes(currentRaw);
			}
		}
		return rawMessage;
	}

	public String getPlainMessage()
	{
		String currentRaw = getRawMessage();
		if (currentRaw.equals(cachedRawMessage))
		{
			return cachedPlainMessage;
		}
		cachedRawMessage = currentRaw;
		cachedPlainMessage = ColorTagParser.stripTags(currentRaw);
		return cachedPlainMessage;
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


	private volatile long pruneTimestamp = 0;

	public void prune()
	{
		if (pruneTimestamp == 0)
		{
			pruneTimestamp = System.currentTimeMillis();
		}
	}

	public boolean isPruned()
	{
		return pruneTimestamp > 0;
	}

	public long getPruneTimestamp()
	{
		return pruneTimestamp;
	}

	public long getPruneAge()
	{
		return pruneTimestamp == 0 ? 0 : System.currentTimeMillis() - pruneTimestamp;
	}
}
