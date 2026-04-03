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
import net.runelite.api.VarClientStr;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.IndexedSprite;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.api.Player;
import net.runelite.api.widgets.Widget;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	private KeyManager keyManager;

	@Inject
	private PublicClanChatOverlay publicClanOverlay;

	@Inject
	private PrivateChatOverlay privateChatOverlay;

	@Inject
	private GameOverlay systemAlertOverlay;

	private final ChatMessageManager messageManager = new ChatMessageManager();
	private final FilterMatcher filterMatcher = new FilterMatcher();

	private volatile boolean peekActive = false;
	private HotkeyListener peekListener;

	private static final Pattern IMG_TAG = Pattern.compile("<img=(\\d+)>");

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
		Player p = client.getLocalPlayer();
		return p != null ? p.getName() : null;
	}

	/**
	 * Returns whatever the player is currently typing in the chatbox,
	 * or an empty string if the chatbox input is idle.
	 */
	public String getChatboxTypedText()
	{
		try
		{
			String text = client.getVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT);
			return text != null ? text : "";
		}
		catch (Exception e)
		{
			return "";
		}
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
		peekListener = new HotkeyListener(() -> config.peekKey())
		{
			@Override
			public void hotkeyPressed()
			{
				peekActive = true;
			}

			@Override
			public void hotkeyReleased()
			{
				peekActive = false;
			}
		};
		keyManager.registerKeyListener(peekListener);

		overlayManager.add(publicClanOverlay);
		overlayManager.add(privateChatOverlay);
		overlayManager.add(systemAlertOverlay);
		log.info("Chat Overlay plugin started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		keyManager.unregisterKeyListener(peekListener);
		peekActive = false;

		overlayManager.remove(publicClanOverlay);
		overlayManager.remove(privateChatOverlay);
		overlayManager.remove(systemAlertOverlay);
		messageManager.clearAll();
		log.info("Chat Overlay plugin stopped");
	}

	public boolean isPeekActive()
	{
		return peekActive && config.peekEnabled();
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

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"chatoverlay".equals(event.getGroup()))
		{
			return;
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
		String lower = ColorTagParser.stripTags(rawMsg).toLowerCase().trim();

		switch (type)
		{
			// ── PUBLIC CHAT ──
			case PUBLICCHAT:
			case MODCHAT:
			case AUTOTYPER:
			case MODAUTOTYPER:
				if (config.showPublicChat())
				{
					ChatLine line = new ChatLine(sender, rawSenderName, rawMsg,
						ChatCategory.PUBLIC, type);
					resolveAndSetIcon(line, extractIconId(rawSenderName));
					messageManager.addPublicClanMessage(line, config.publicMaxMessages());
				}
				break;

			// ── CLAN CHAT ──
			case CLAN_CHAT:
			case CLAN_GIM_CHAT:
				if (config.showClanChat())
				{
					ChatLine line = new ChatLine(sender, rawSenderName, rawMsg,
						ChatCategory.CLAN, type, getClanChannelName());
					resolveAndSetIcon(line, extractIconId(rawSenderName));
					messageManager.addPublicClanMessage(line, config.publicMaxMessages());
				}
				break;
			case CLAN_GUEST_CHAT:
				if (config.showClanChat())
				{
					ChatLine line = new ChatLine(sender, rawSenderName, rawMsg,
						ChatCategory.CLAN, type, getGuestClanChannelName());
					resolveAndSetIcon(line, extractIconId(rawSenderName));
					messageManager.addPublicClanMessage(line, config.publicMaxMessages());
				}
				break;
			case CLAN_MESSAGE:
			case CLAN_GUEST_MESSAGE:
				if (config.showClanChat())
				{
					String clanName = (type == ChatMessageType.CLAN_GUEST_MESSAGE)
						? getGuestClanChannelName()
						: getClanChannelName();
					ChatLine line = new ChatLine(sender, rawSenderName, rawMsg,
						ChatCategory.CLAN, type, clanName);
					resolveAndSetIcon(line, extractIconId(rawSenderName));
					messageManager.addPublicClanMessage(line, config.publicMaxMessages());
				}
				break;

			// ── FRIENDS CHAT ──
			case FRIENDSCHAT:
				if (config.showFriendsChat())
				{
					ChatLine line = new ChatLine(sender, rawSenderName, rawMsg,
						ChatCategory.FRIENDS_CHAT, type, getFriendsChatName());
					resolveAndSetIcon(line, extractIconId(rawSenderName));
					messageManager.addPublicClanMessage(line, config.publicMaxMessages());
				}
				break;

			// ── PRIVATE CHAT ──
			case PRIVATECHAT:
			case MODPRIVATECHAT:
			{
				ChatLine line = new ChatLine("From " + sender, "From " + rawSenderName, rawMsg,
					ChatCategory.PRIVATE, type);
				resolveAndSetIcon(line, extractIconId(rawSenderName));
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
				// No icon for outgoing — the local player's own name has no img= tag in rawSenderName
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
				boolean blocked = config.filterSpamAlerts()
					&& filterMatcher.matches(config.spamPatterns(), lower);
				if (!blocked)
				{
					ChatLine line = new ChatLine(null, null, rawMsg, ChatCategory.SYSTEM, type);
					if (config.showSystemAlerts())
					{
						messageManager.addSystemMessage(line,
							config.systemMaxAlerts(), config.filterSpamAlerts(),
							config.spamCooldownSeconds() * 1000L);
					}
					if (config.showGameMessagesInMain())
					{
						messageManager.addPublicClanMessage(line, config.publicMaxMessages());
					}
				}
				break;
			}
			case SPAM:
			{
				int gameFilter = client.getVarbitValue(VarbitID.GAME_FILTER);
				if (gameFilter != 0) break; // Filter or Off — hide SPAM
				boolean blocked = config.filterSpamAlerts()
					&& filterMatcher.matches(config.spamPatterns(), lower);
				if (!blocked)
				{
					ChatLine line = new ChatLine(null, null, rawMsg, ChatCategory.SYSTEM, type);
					if (config.showSystemAlerts())
					{
						messageManager.addSystemMessage(line,
							config.systemMaxAlerts(), config.filterSpamAlerts(),
							config.spamCooldownSeconds() * 1000L);
					}
					if (config.showGameMessagesInMain())
					{
						messageManager.addPublicClanMessage(line, config.publicMaxMessages());
					}
				}
				break;
			}

			case BROADCAST:
			{
				ChatLine line = new ChatLine(null, null, rawMsg, ChatCategory.SYSTEM, type);
				if (config.showSystemAlerts())
				{
					messageManager.addSystemMessage(line, config.systemMaxAlerts(), false, 0L);
				}
				if (config.showGameMessagesInMain())
				{
					boolean blocked = config.filterSpamAlerts()
						&& filterMatcher.matches(config.spamPatterns(), lower);
					if (!blocked)
					{
						messageManager.addPublicClanMessage(line, config.publicMaxMessages());
					}
				}
				break;
			}

			case FRIENDSCHATNOTIFICATION:
			case FRIENDNOTIFICATION:
			case LOGINLOGOUTNOTIFICATION:
			{
				boolean blocked = config.filterSpamAlerts()
					&& filterMatcher.matches(config.spamPatterns(), lower);
				if (!blocked && config.showSystemAlerts())
				{
					messageManager.addSystemMessage(
						new ChatLine(null, null, rawMsg, ChatCategory.SYSTEM, type),
						config.systemMaxAlerts(),
						config.filterSpamAlerts(),
						config.spamCooldownSeconds() * 1000L
					);
				}
				break;
			}

			case WELCOME:
			{
				ChatLine line = new ChatLine(null, null, rawMsg, ChatCategory.SYSTEM, type);
				if (config.showSystemAlerts())
				{
					messageManager.addSystemMessage(line, config.systemMaxAlerts(), false, 0L);
				}
				if (config.showGameMessagesInMain())
				{
					messageManager.addPublicClanMessage(line, config.publicMaxMessages());
				}
				break;
			}

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
	 * Extracts the first {@code <img=N>} image ID from the raw sender name, or -1 if absent.
	 * OSRS embeds these tags to indicate ironman/JMOD status.
	 */
	private int extractIconId(String rawSender)
	{
		if (rawSender == null)
		{
			return -1;
		}
		Matcher m = IMG_TAG.matcher(rawSender);
		if (m.find())
		{
			try
			{
				return Integer.parseInt(m.group(1));
			}
			catch (NumberFormatException e)
			{
				return -1;
			}
		}
		return -1;
	}

	/**
	 * Resolves the {@code <img=N>} icon from the game's mod-icon array and sets it on {@code line}.
	 * Must be called on the client thread (i.e. inside an event handler).
	 */
	private void resolveAndSetIcon(ChatLine line, int iconId)
	{
		if (iconId < 0 || !config.showPlayerIcons())
		{
			return;
		}
		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null || iconId >= modIcons.length)
		{
			return;
		}
		IndexedSprite sprite = modIcons[iconId];
		if (sprite == null)
		{
			return;
		}
		line.setIcon(indexedSpriteToImage(sprite));
	}

	/**
	 * Converts a RuneLite {@link IndexedSprite} to a {@link java.awt.image.BufferedImage}.
	 * Palette index 0 is treated as transparent.
	 */
	private static java.awt.image.BufferedImage indexedSpriteToImage(IndexedSprite sprite)
	{
		int w = sprite.getWidth();
		int h = sprite.getHeight();
		int origW = sprite.getOriginalWidth();
		int origH = sprite.getOriginalHeight();
		int ox = sprite.getOffsetX();
		int oy = sprite.getOffsetY();
		int[] palette = sprite.getPalette();
		byte[] pixels = sprite.getPixels();

		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
			origW, origH, java.awt.image.BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				int idx = pixels[y * w + x] & 0xFF;
				if (idx == 0)
				{
					continue; // transparent
				}
				img.setRGB(x + ox, y + oy, 0xFF000000 | palette[idx]);
			}
		}
		return img;
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
