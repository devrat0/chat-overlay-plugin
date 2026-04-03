package com.chatoverlay;

import java.awt.Color;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Varbits;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.JagexColors;

/**
 * Resolves the correct {@link Color} for a given chat message type and color role,
 * honoring user-configured Chat Color plugin settings and falling back to OSRS defaults.
 */
@Singleton
public class ChatColorResolver
{
	private final Client        client;
	private final ConfigManager configManager;

	@Inject
	public ChatColorResolver(Client client, ConfigManager configManager)
	{
		this.client        = client;
		this.configManager = configManager;
	}

	public boolean isTransparentChatbox()
	{
		return client.isResized()
			&& client.getVarbitValue(Varbits.TRANSPARENT_CHATBOX) == 1;
	}

	public ChatColorConfig getChatColorConfig()
	{
		return configManager.getConfig(ChatColorConfig.class);
	}

	/** Resolves the message-body or sender color for a given type. */
	public Color getChatColor(ChatMessageType type, ChatColorType colorType)
	{
		boolean t = isTransparentChatbox();
		ChatColorConfig cc = getChatColorConfig();

		Color color = resolveFromConfig(cc, type, colorType, t);
		if (color == null || color.getAlpha() == 0)
		{
			color = resolveJagexDefault(type, colorType, t);
		}
		return color;
	}

	/** Resolves the sender-name color, distinguishing the local player from others. */
	public Color getSenderColor(ChatMessageType type, ChatColorType colorType, boolean isSender)
	{
		boolean t = isTransparentChatbox();
		ChatColorConfig cc = getChatColorConfig();

		Color color = resolveSenderFromConfig(cc, type, colorType, t, isSender);
		if (color == null || color.getAlpha() == 0)
		{
			color = resolveJagexDefault(type, colorType, t);
		}
		return color;
	}

	/** Resolves the color for a channel-name prefix (e.g. "[Laced PVM] "). */
	public Color getChannelNameColor(ChatMessageType type)
	{
		boolean t = isTransparentChatbox();
		ChatColorConfig cc = getChatColorConfig();

		Color color;
		switch (type)
		{
			case CLAN_CHAT:
			case CLAN_GIM_CHAT:
			case CLAN_MESSAGE:
				color = t ? cc.transparentClanChannelName() : cc.opaqueClanChannelName();
				break;
			case CLAN_GUEST_CHAT:
			case CLAN_GUEST_MESSAGE:
				color = t ? cc.transparentClanChannelGuestName() : cc.opaqueClanGuestChatChannelName();
				break;
			case FRIENDSCHAT:
			case FRIENDSCHATNOTIFICATION:
				color = t ? cc.transparentFriendsChatChannelName() : cc.opaqueFriendsChatChannelName();
				break;
			default:
				color = null;
				break;
		}

		if (color == null || color.getAlpha() == 0)
		{
			color = t
				? JagexColors.CHAT_FC_NAME_TRANSPARENT_BACKGROUND
				: JagexColors.CHAT_FC_NAME_OPAQUE_BACKGROUND;
		}
		return color;
	}

	// ── Private helpers ───────────────────────────────────────────────────────

	private Color resolveFromConfig(
		ChatColorConfig cc, ChatMessageType type, ChatColorType colorType, boolean t)
	{
		switch (type)
		{
			case PUBLICCHAT:
			case MODCHAT:
				return colorType == ChatColorType.HIGHLIGHT
					? (t ? cc.transparentPublicChat()      : cc.opaquePublicChat())
					: (t ? cc.transparentUsername()        : cc.opaqueUsername());

			case AUTOTYPER:
			case MODAUTOTYPER:
				return colorType == ChatColorType.HIGHLIGHT
					? (t ? cc.transparentAutochatMessage() : cc.opaqueAutochatMessage())
					: (t ? cc.transparentUsername()        : cc.opaqueUsername());

			case CLAN_CHAT:
			case CLAN_GIM_CHAT:
				return colorType == ChatColorType.HIGHLIGHT
					? (t ? cc.transparentClanChatMessage()   : cc.opaqueClanChatMessage())
					: (t ? cc.transparentClanChatUsernames() : cc.opaqueClanChatUsernames());

			case CLAN_GUEST_CHAT:
				return colorType == ChatColorType.HIGHLIGHT
					? (t ? cc.transparentClanChatGuestMessage()   : cc.opaqueClanChatGuestMessage())
					: (t ? cc.transparentClanChatGuestUsernames() : cc.opaqueClanChatGuestUsernames());

			case CLAN_MESSAGE:
				return t ? cc.transparentClanChatInfo() : cc.opaqueClanChatInfo();

			case CLAN_GUEST_MESSAGE:
				return t ? cc.transparentClanChatGuestInfo() : cc.opaqueClanChatGuestInfo();

			case FRIENDSCHAT:
				return colorType == ChatColorType.HIGHLIGHT
					? (t ? cc.transparentFriendsChatMessage()   : cc.opaqueFriendsChatMessage())
					: (t ? cc.transparentFriendsChatUsernames() : cc.opaqueFriendsChatUsernames());

			case MODPRIVATECHAT:
				return colorType == ChatColorType.HIGHLIGHT
					? (t ? cc.transparentPrivateMessageReceived() : cc.opaquePrivateMessageReceived())
					: (t ? cc.transparentPrivateUsernames()       : cc.opaquePrivateUsernames());

			case PRIVATECHAT:
				return colorType == ChatColorType.HIGHLIGHT
					? (t ? cc.transparentPrivateMessageReceivedHighlight() : cc.opaquePrivateMessageReceivedHighlight())
					: (t ? cc.transparentPrivateMessageReceived()          : cc.opaquePrivateMessageReceived());

			case PRIVATECHATOUT:
				return colorType == ChatColorType.HIGHLIGHT
					? (t ? cc.transparentPrivateMessageSentHighlight() : cc.opaquePrivateMessageSentHighlight())
					: (t ? cc.transparentPrivateMessageSent()          : cc.opaquePrivateMessageSent());

			case GAMEMESSAGE:
			case ENGINE:
				return t ? cc.transparentGameMessage() : cc.opaqueGameMessage();

			case SPAM:
				return t ? cc.transparentFiltered() : cc.opaqueFiltered();

			case BROADCAST:
				return t ? cc.transparentServerMessage() : cc.opaqueServerMessage();

			case FRIENDSCHATNOTIFICATION:
			case FRIENDNOTIFICATION:
			case LOGINLOGOUTNOTIFICATION:
				return t ? cc.transparentFriendsChatInfo() : cc.opaqueFriendsChatInfo();

			case WELCOME:
				return t ? cc.transparentServerMessage() : cc.opaqueServerMessage();

			default:
				return null;
		}
	}

	private Color resolveSenderFromConfig(
		ChatColorConfig cc, ChatMessageType type, ChatColorType colorType, boolean t, boolean isSender)
	{
		if (isSender)
		{
			return t ? cc.transparentUsername() : cc.opaqueUsername();
		}
		switch (type)
		{
			case PUBLICCHAT:
				return t ? cc.transparentPlayerUsername() : cc.opaquePlayerUsername();

			case CLAN_CHAT:
			case CLAN_MESSAGE:
			case CLAN_GIM_CHAT:
				return t ? cc.transparentClanChatUsernames() : cc.opaqueClanChatUsernames();

			case CLAN_GUEST_CHAT:
			case CLAN_GUEST_MESSAGE:
				return t ? cc.transparentClanChatGuestUsernames() : cc.opaqueClanChatGuestUsernames();

			case FRIENDSCHATNOTIFICATION:
			case FRIENDSCHAT:
				return t ? cc.transparentFriendsChatUsernames() : cc.opaqueFriendsChatUsernames();

			case PRIVATECHAT:
			case PRIVATECHATOUT:
				return t ? cc.transparentPrivateUsernames() : cc.opaquePrivateUsernames();

			default:
				return t ? cc.transparentUsername() : cc.opaqueUsername();
		}
	}

	private Color resolveJagexDefault(ChatMessageType type, ChatColorType colorType, boolean t)
	{
		if (colorType == ChatColorType.NORMAL)
		{
			return Color.WHITE;
		}
		switch (type)
		{
			case PUBLICCHAT:
			case MODCHAT:
			case AUTOTYPER:
			case MODAUTOTYPER:
				return t ? JagexColors.CHAT_PUBLIC_TEXT_TRANSPARENT_BACKGROUND
				         : JagexColors.CHAT_PUBLIC_TEXT_OPAQUE_BACKGROUND;

			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
				return t ? JagexColors.CHAT_PRIVATE_MESSAGE_TEXT_TRANSPARENT_BACKGROUND
				         : JagexColors.CHAT_PRIVATE_MESSAGE_TEXT_OPAQUE_BACKGROUND;

			case CLAN_CHAT:
			case CLAN_GIM_CHAT:
			case CLAN_GUEST_CHAT:
			case CLAN_MESSAGE:
			case CLAN_GUEST_MESSAGE:
			case FRIENDSCHAT:
			case FRIENDSCHATNOTIFICATION:
			case FRIENDNOTIFICATION:
				return t ? JagexColors.CHAT_FC_TEXT_TRANSPARENT_BACKGROUND
				         : JagexColors.CHAT_FC_TEXT_OPAQUE_BACKGROUND;

			default:
				return t ? JagexColors.CHAT_GAME_EXAMINE_TEXT_TRANSPARENT_BACKGROUND
				         : JagexColors.CHAT_GAME_EXAMINE_TEXT_OPAQUE_BACKGROUND;
		}
	}
}
