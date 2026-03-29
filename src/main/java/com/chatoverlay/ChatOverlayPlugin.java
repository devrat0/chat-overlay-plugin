package com.chatoverlay;

import com.google.inject.Provides;
import java.awt.Color;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.GameState;
import net.runelite.api.Varbits;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.api.widgets.Widget;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@PluginDescriptor(
	name = "Chat Overlay",
	description = "Split chat into customizable overlays: system alerts near player, private chat above chatbox, public/clan in bottom panel",
	tags = {"chat", "overlay", "split", "system", "private", "clan", "public", "alert"},
	enabledByDefault = true
)
public class ChatOverlayPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ChatOverlayConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PublicClanChatOverlay publicClanOverlay;

	@Inject
	private PrivateChatOverlay privateChatOverlay;

	@Inject
	private GameOverlay systemAlertOverlay;

	private final ChatMessageManager messageManager = new ChatMessageManager();

	/**
	 * Returns {@code true} when the in-game chatbox chat area is visible.
	 *
	 * <p>Widget 162:34 is {@code Chatbox.CHATAREA} — it disappears (returns null)
	 * when the chatbox is collapsed. All other children of group 162 remain loaded
	 * regardless of collapse state, so only this specific child reliably reflects
	 * the visible/collapsed distinction.</p>
	 */
	public boolean isChatboxOpen()
	{
		Widget chatArea = client.getWidget(162, 34);
		return chatArea != null && !chatArea.isHidden();
	}

	public String getLocalPlayerName()
	{
		net.runelite.api.Player p = client.getLocalPlayer();
		return p != null ? p.getName() : null;
	}

	/**
	 * Returns {@code true} when the chatbox is in transparent mode.
	 */
	public boolean isTransparentChatbox()
	{
		return client.isResized()
			&& client.getVarbitValue(Varbits.TRANSPARENT_CHATBOX) == 1;
	}

	/**
	 * Resolves the color for the given message type and color role.
	 *
	 * <p>Priority order:
	 * <ol>
	 *   <li>User-configured color from {@link ChatColorConfig} (Chat Color plugin)</li>
	 *   <li>{@link JagexColors} — RuneLite's canonical OSRS default chat colors</li>
	 * </ol>
	 *
	 * <p>{@link ChatColorType#NORMAL} = sender username color.<br>
	 * {@link ChatColorType#HIGHLIGHT} = message body color.</p>
	 */
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

	/**
	 * Resolves the color for the channel-name prefix (e.g. "[Laced PVM] "),
	 * using the specific {@link ChatColorConfig} channel-name color, then
	 * {@link JagexColors} as fallback.
	 */
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

	// ── Private color resolution helpers ──────────────────────────────────

	/**
	 * Reads the color from {@link ChatColorConfig}.  Returns {@code null} when
	 * no custom color has been configured for this type (ConfigManager returns
	 * null for unset abstract config methods).
	 */
	private Color resolveFromConfig(
		ChatColorConfig cc, ChatMessageType type, ChatColorType colorType, boolean t)
	{
		switch (type)
		{
			case PUBLICCHAT:
			case MODCHAT:
				return colorType == ChatColorType.HIGHLIGHT
					? (t ? cc.transparentPublicChat()        : cc.opaquePublicChat())
					: (t ? cc.transparentUsername()          : cc.opaqueUsername());

			case AUTOTYPER:
			case MODAUTOTYPER:
				return colorType == ChatColorType.HIGHLIGHT
					? (t ? cc.transparentAutochatMessage()   : cc.opaqueAutochatMessage())
					: (t ? cc.transparentUsername()          : cc.opaqueUsername());

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
                        : (t ? cc.transparentPrivateMessageReceived()   : cc.opaquePrivateMessageReceived());

			case PRIVATECHATOUT:
				return colorType == ChatColorType.HIGHLIGHT
					? (t ? cc.transparentPrivateMessageSentHighlight() : cc.opaquePrivateMessageSentHighlight())
					: (t ? cc.transparentPrivateMessageSent() : cc.opaquePrivateMessageSent());

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
            ChatColorConfig cc, ChatMessageType type, ChatColorType colorType, boolean transparent, boolean isSender)
    {
        if(isSender){
            return transparent ? cc.transparentUsername() :  cc.opaqueUsername();
        }

        switch (type)
        {
            case PUBLICCHAT:
                    return transparent ? cc.transparentPlayerUsername() : cc.opaquePlayerUsername();

            case CLAN_CHAT:
            case CLAN_MESSAGE:
            case CLAN_GIM_CHAT:
                return transparent ? cc.transparentClanChatUsernames() : cc.opaqueClanChatUsernames();

            case CLAN_GUEST_CHAT:
            case CLAN_GUEST_MESSAGE:
                return transparent ? cc.transparentClanChatGuestUsernames()  : cc.opaqueClanChatGuestUsernames();

            case FRIENDSCHATNOTIFICATION:
            case FRIENDSCHAT:
                return transparent ? cc.transparentFriendsChatUsernames()  : cc.opaqueFriendsChatUsernames();

            case PRIVATECHAT:
            case PRIVATECHATOUT:
                return transparent ? cc.transparentPrivateUsernames()  : cc.opaquePrivateUsernames();

            default:
                return transparent ? cc.transparentUsername() :  cc.opaqueUsername();
        }
    }

	/**
	 * Returns the {@link JagexColors} constant that RuneLite uses as the OSRS
	 * default for this chat type.
	 */
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

	/**
	 * Returns RuneLite's core {@link ChatColorConfig} for resolving named color
	 */
	public ChatColorConfig getChatColorConfig()
	{
		return configManager.getConfig(ChatColorConfig.class);
	}

	public ChatMessageManager getMessageManager()
	{
		return messageManager;
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(publicClanOverlay);
		overlayManager.add(privateChatOverlay);
		overlayManager.add(systemAlertOverlay);
		log.info("Chat Overlay plugin started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(publicClanOverlay);
		overlayManager.remove(privateChatOverlay);
		overlayManager.remove(systemAlertOverlay);
		messageManager.clearAll();
		log.info("Chat Overlay plugin stopped");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		messageManager.pruneSystemMessages(config.systemAlertDuration());
		messageManager.prunePublicClanMessages(config.publicMessageDuration());
		messageManager.prunePrivateMessages(config.privateMessageDuration());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			messageManager.clearAll();
		}
	}

	/**
	 * Clears the relevant overlay queue when the player right-clicks a chat
	 * tab and selects "Clear history".
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!event.getMenuOption().endsWith("Clear history"))
		{
			return;
		}

		int widgetId = event.getParam1();

		if (widgetId == InterfaceID.Chatbox.CHAT_ALL)
		{
			messageManager.clearAll();
		}
		else if (widgetId == InterfaceID.Chatbox.CHAT_GAME)
		{
			messageManager.clearSystemMessages();
		}
		else if (widgetId == InterfaceID.Chatbox.CHAT_PUBLIC)
		{
			messageManager.clearPublicClanMessages();
		}
		else if (widgetId == InterfaceID.Chatbox.CHAT_PRIVATE)
		{
			messageManager.clearPrivateMessages();
		}
		else if (widgetId == InterfaceID.Chatbox.CHAT_FRIENDSCHAT)
		{
			messageManager.clearPublicClanMessages();
		}
		else if (widgetId == InterfaceID.Chatbox.CHAT_CLAN)
		{
			messageManager.clearPublicClanMessages();
		}
	}

	/**
	 * Priority -1 runs AFTER RuneLite's ChatColorPlugin (priority 0), which
	 * injects {@code <col=RRGGBB>} tags into {@code messageNode.getName()} and
	 * {@code messageNode.getValue()}.
	 */
	@Subscribe(priority = -1)
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();

		// Live values from messageNode — already modified by ChatColorPlugin
		String rawSenderName = event.getMessageNode().getName();
		String sender = sanitizeName(rawSenderName);
		String rawMsg = event.getMessageNode().getValue();

		switch (type)
		{
			// ── PUBLIC CHAT ──
			case PUBLICCHAT:
			case MODCHAT:
			case AUTOTYPER:
			case MODAUTOTYPER:
				if (config.showPublicChat())
				{
					messageManager.addPublicClanMessage(
						new ChatLine(sender, rawSenderName, rawMsg,
							ChatCategory.PUBLIC, type),
						config.publicMaxMessages()
					);
				}
				break;

			// ── CLAN CHAT ──
			case CLAN_CHAT:
			case CLAN_GIM_CHAT:
				if (config.showClanChat())
				{
					messageManager.addPublicClanMessage(
						new ChatLine(sender, rawSenderName, rawMsg,
							ChatCategory.CLAN, type,
							getClanChannelName()),
						config.publicMaxMessages()
					);
				}
				break;
			case CLAN_GUEST_CHAT:
				if (config.showClanChat())
				{
					messageManager.addPublicClanMessage(
						new ChatLine(sender, rawSenderName, rawMsg,
							ChatCategory.CLAN, type,
							getGuestClanChannelName()),
						config.publicMaxMessages()
					);
				}
				break;
			case CLAN_MESSAGE:
			case CLAN_GUEST_MESSAGE:
				if (config.showClanChat())
				{
					String clanName = (type == ChatMessageType.CLAN_GUEST_MESSAGE)
						? getGuestClanChannelName()
						: getClanChannelName();
					messageManager.addPublicClanMessage(
						new ChatLine(sender, rawSenderName, rawMsg,
							ChatCategory.CLAN, type,
							clanName),
						config.publicMaxMessages()
					);
				}
				break;

			// ── FRIENDS CHAT ──
			case FRIENDSCHAT:
				if (config.showFriendsChat())
				{
					messageManager.addPublicClanMessage(
						new ChatLine(sender, rawSenderName, rawMsg,
							ChatCategory.FRIENDS_CHAT, type,
							getFriendsChatName()),
						config.publicMaxMessages()
					);
				}
				break;

			// ── PRIVATE CHAT ──
			case PRIVATECHAT:
			case MODPRIVATECHAT:
			{
				ChatLine line = new ChatLine("From " + sender, "From " + rawSenderName, rawMsg,
					ChatCategory.PRIVATE, type);
				if (config.showPrivateChat())
				{
					messageManager.addPrivateMessage(line, config.privateMaxMessages());
				}
				if (config.showPrivateChatInMain())
				{
					messageManager.addPublicClanMessage(line, config.publicMaxMessages());
				}
				break;
			}
			case PRIVATECHATOUT:
			{
				ChatLine line = new ChatLine("To " + sender, "To " + rawSenderName, rawMsg,
					ChatCategory.PRIVATE, type);
				if (config.showPrivateChat())
				{
					messageManager.addPrivateMessage(line, config.privateMaxMessages());
				}
				if (config.showPrivateChatInMain())
				{
					messageManager.addPublicClanMessage(line, config.publicMaxMessages());
				}
				break;
			}

			// ── SYSTEM / GAME MESSAGES ──
			// Respect the in-game "Game" chat filter:
			//   0 = On     → show GAMEMESSAGE, ENGINE, and SPAM
			//   1 = Filter → show GAMEMESSAGE and ENGINE, hide SPAM
			//   2 = Off    → hide all game messages
			case GAMEMESSAGE:
			case ENGINE:
			{
				int gameFilter = client.getVarbitValue(VarbitID.GAME_FILTER);
				if (gameFilter == 2) break; // Off — hide all
				if (config.showSystemAlerts())
				{
					messageManager.addSystemMessage(
						new ChatLine(null, null, rawMsg,
							ChatCategory.SYSTEM, type),
						config.systemMaxAlerts(),
						config.filterSpamAlerts(),
						parseSpamPatterns(),
						config.spamCooldownSeconds() * 1000L
					);
				}
				break;
			}
			case SPAM:
			{
				int gameFilter = client.getVarbitValue(VarbitID.GAME_FILTER);
				if (gameFilter != 0) break; // Filter or Off — hide SPAM
				if (config.showSystemAlerts())
				{
					messageManager.addSystemMessage(
						new ChatLine(null, null, rawMsg,
							ChatCategory.SYSTEM, type),
						config.systemMaxAlerts(),
						config.filterSpamAlerts(),
						parseSpamPatterns(),
						config.spamCooldownSeconds() * 1000L
					);
				}
				break;
			}

			case BROADCAST:
				if (config.showSystemAlerts())
				{
					messageManager.addSystemMessage(
						new ChatLine(null, null, rawMsg,
							ChatCategory.SYSTEM, type),
						config.systemMaxAlerts(),
						false,
						Collections.emptySet(),
						0L
					);
				}
				break;

			case FRIENDSCHATNOTIFICATION:
			case FRIENDNOTIFICATION:
			case LOGINLOGOUTNOTIFICATION:
				if (config.showSystemAlerts())
				{
					messageManager.addSystemMessage(
						new ChatLine(null, null, rawMsg,
							ChatCategory.SYSTEM, type),
						config.systemMaxAlerts(),
						config.filterSpamAlerts(),
						parseSpamPatterns(),
						config.spamCooldownSeconds() * 1000L
					);
				}
				break;

			case WELCOME:
				if (config.showSystemAlerts())
				{
					messageManager.addSystemMessage(
						new ChatLine(null, null, rawMsg,
							ChatCategory.SYSTEM, type),
						config.systemMaxAlerts(),
						false,
						Collections.emptySet(),
						0L
					);
				}
				break;

			default:
				break;
		}
	}

	// ── Channel name helpers ───────────────────────────────────────────────

	private String getClanChannelName()
	{
		try
		{
			ClanChannel cc = client.getClanChannel();
			return cc != null ? cc.getName() : "Clan";
		}
		catch (Exception e)
		{
			return "Clan";
		}
	}

	private String getGuestClanChannelName()
	{
		try
		{
			ClanChannel cc = client.getGuestClanChannel();
			return cc != null ? cc.getName() : "Guest Clan";
		}
		catch (Exception e)
		{
			return "Guest Clan";
		}
	}

	private String getFriendsChatName()
	{
		try
		{
			FriendsChatManager fcm = client.getFriendsChatManager();
			return fcm != null ? fcm.getOwner() : "FC";
		}
		catch (Exception e)
		{
			return "FC";
		}
	}

	// ── Utilities ──────────────────────────────────────────────────────────

	/**
	 * Parses the comma-separated spam patterns config string into a lowercase set.
	 */
	private Set<String> parseSpamPatterns()
	{
		String csv = config.spamPatterns();
		if (csv == null || csv.trim().isEmpty())
		{
			return Collections.emptySet();
		}
		Set<String> patterns = new HashSet<>();
		for (String p : csv.split(","))
		{
			String trimmed = p.trim().toLowerCase();
			if (!trimmed.isEmpty())
			{
				patterns.add(trimmed);
			}
		}
		return patterns;
	}

	private String sanitizeName(String name)
	{
		if (name == null)
		{
			return "";
		}
		return Text.removeTags(name);
	}

	@Provides
	ChatOverlayConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatOverlayConfig.class);
	}
}
