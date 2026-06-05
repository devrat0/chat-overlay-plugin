package com.chatoverlay;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;
import net.runelite.api.Player;
import net.runelite.api.widgets.Widget;

@Slf4j
@PluginDescriptor(
	name = "Chat Overlay",
	description = "Split chat into customizable overlays: system alerts near player, private chat above chatbox, public/clan in bottom panel",
	tags = {"chat", "overlay", "split", "system", "private", "clan", "public", "alert"},
	enabledByDefault = true
)
public class ChatOverlayPlugin extends Plugin
{
	@Inject private Client              client;
	@Inject private ChatOverlayConfig   config;
	@Inject private OverlayManager      overlayManager;
	@Inject private KeyManager          keyManager;
	@Inject private PublicClanChatOverlay publicClanOverlay;
	@Inject private PrivateChatOverlay    privateChatOverlay;
	@Inject private GameOverlay           systemAlertOverlay;
	@Inject private ClanChatOverlay       clanChatOverlay;
	@Inject private ChatColorResolver     colorResolver;
	@Inject private ChannelNameResolver   channelNames;

	private final ChatMessageManager messageManager = new ChatMessageManager();
	private final FilterMatcher      filterMatcher  = new FilterMatcher();

	private volatile boolean   peekActive   = false;
	private HotkeyListener     peekListener;

	// ── Queries used by overlays ──────────────────────────────────────────────

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

	public boolean isPeekActive()
	{
		return peekActive && config.peekEnabled();
	}

	public ChatMessageManager getMessageManager()
	{
		return messageManager;
	}

	// ── Color / icon delegation (convenience for overlays) ────────────────────

	public java.awt.Color getChatColor(ChatMessageType type, net.runelite.client.chat.ChatColorType colorType)
	{
		return colorResolver.getChatColor(type, colorType);
	}

	public java.awt.Color getSenderColor(ChatMessageType type, net.runelite.client.chat.ChatColorType colorType, boolean isSender)
	{
		return colorResolver.getSenderColor(type, colorType, isSender);
	}

	public java.awt.Color getChannelNameColor(ChatMessageType type)
	{
		return colorResolver.getChannelNameColor(type);
	}

	public net.runelite.client.config.ChatColorConfig getChatColorConfig()
	{
		return colorResolver.getChatColorConfig();
	}

	// ── Lifecycle ─────────────────────────────────────────────────────────────

	@Override
	protected void startUp() throws Exception
	{
		peekListener = new HotkeyListener(() -> config.peekKey())
		{
			@Override
			public void hotkeyPressed()  { peekActive = true;  }

			@Override
			public void hotkeyReleased() { peekActive = false; }
		};
		keyManager.registerKeyListener(peekListener);

		overlayManager.add(publicClanOverlay);
		overlayManager.add(privateChatOverlay);
		overlayManager.add(systemAlertOverlay);
		overlayManager.add(clanChatOverlay);
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
		overlayManager.remove(clanChatOverlay);
		messageManager.clearAll();
		log.info("Chat Overlay plugin stopped");
	}

	// ── Event handlers ────────────────────────────────────────────────────────

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			messageManager.clearAll();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!event.getMenuOption().endsWith("Clear history"))
		{
			return;
		}
		int widgetId = event.getParam1();
		if      (widgetId == InterfaceID.Chatbox.CHAT_ALL)        { messageManager.clearAll(); }
		else if (widgetId == InterfaceID.Chatbox.CHAT_GAME)       { messageManager.clearSystemMessages(); }
		else if (widgetId == InterfaceID.Chatbox.CHAT_PUBLIC)     { messageManager.clearPublicClanMessages(); }
		else if (widgetId == InterfaceID.Chatbox.CHAT_PRIVATE)    { messageManager.clearPrivateMessages(); }
		else if (widgetId == InterfaceID.Chatbox.CHAT_FRIENDSCHAT){ messageManager.clearPublicClanMessages(); messageManager.clearClanMessages(); }
		else if (widgetId == InterfaceID.Chatbox.CHAT_CLAN)       { messageManager.clearPublicClanMessages(); messageManager.clearClanMessages(); }
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// reserved for future config-change reactions
	}

	/**
	 * Priority -1 runs AFTER RuneLite's ChatColorPlugin (priority 0), which
	 * injects {@code <col=RRGGBB>} tags into the message node.
	 */
	@Subscribe(priority = -1)
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type         = event.getType();
		String          rawSenderName = event.getMessageNode().getName();
		String          sender        = sanitizeName(rawSenderName);
		String          rawMsg        = event.getMessageNode().getValue();
		String          lower         = ColorTagParser.stripTags(rawMsg).toLowerCase().trim();

		switch (type)
		{
			case PUBLICCHAT: case MODCHAT: case AUTOTYPER: case MODAUTOTYPER:
				handlePublicChat(event.getMessageNode(), type, sender, rawSenderName, rawMsg);
				break;

			case CLAN_CHAT: case CLAN_GIM_CHAT: case CLAN_GUEST_CHAT:
			case CLAN_MESSAGE: case CLAN_GUEST_MESSAGE:
				handleClanChat(event.getMessageNode(), type, sender, rawSenderName, rawMsg);
				break;

			case FRIENDSCHAT:
				handleFriendsChat(event.getMessageNode(), sender, rawSenderName, rawMsg);
				break;

			case PRIVATECHAT: case MODPRIVATECHAT: case PRIVATECHATOUT:
				handlePrivateChat(event.getMessageNode(), type, sender, rawSenderName, rawMsg);
				break;

			case GAMEMESSAGE: case ENGINE: case SPAM:
			case BROADCAST: case FRIENDSCHATNOTIFICATION: case FRIENDNOTIFICATION:
			case LOGINLOGOUTNOTIFICATION: case WELCOME:
				handleSystemMessage(event.getMessageNode(), type, rawMsg, lower);
				break;

			default:
				break;
		}
	}

	// ── onChatMessage handlers ─────────────────────────────────────────────

	private void handlePublicChat(net.runelite.api.MessageNode messageNode, ChatMessageType type, String sender, String rawSenderName, String rawMsg)
	{
		if (!config.showPublicChat())
		{
			return;
		}
		ChatLine line = new ChatLine(messageNode, sender, rawSenderName, rawMsg, ChatCategory.PUBLIC, type);
		messageManager.addPublicClanMessage(line, config.publicMaxMessages());
	}

	private void handleClanChat(net.runelite.api.MessageNode messageNode, ChatMessageType type, String sender, String rawSenderName, String rawMsg)
	{
		if (!config.showClanChat() && !config.showClanChatOverlay())
		{
			return;
		}
		String channelName;
		switch (type)
		{
			case CLAN_GUEST_CHAT:
			case CLAN_GUEST_MESSAGE:
				channelName = channelNames.getGuestClanChannelName();
				break;
			default:
				channelName = channelNames.getClanChannelName();
				break;
		}
		ChatLine line = new ChatLine(messageNode, sender, rawSenderName, rawMsg, ChatCategory.CLAN, type, channelName);
		if (config.showClanChat())
		{
			messageManager.addPublicClanMessage(line, config.publicMaxMessages());
		}
		if (config.showClanChatOverlay())
		{
			boolean shouldAdd = false;
			if (type == ChatMessageType.CLAN_GIM_CHAT)
			{
				shouldAdd = config.clanShowGim();
			}
			else if (type == ChatMessageType.CLAN_GUEST_CHAT || type == ChatMessageType.CLAN_GUEST_MESSAGE)
			{
				shouldAdd = config.clanShowGuest();
			}
			else
			{
				shouldAdd = config.clanShowClan();
			}
			if (shouldAdd)
			{
				messageManager.addClanMessage(line, config.clanMaxMessages());
			}
		}
	}

	private void handleFriendsChat(net.runelite.api.MessageNode messageNode, String sender, String rawSenderName, String rawMsg)
	{
		if (!config.showFriendsChat() && !config.showClanChatOverlay())
		{
			return;
		}
		ChatLine line = new ChatLine(messageNode, sender, rawSenderName, rawMsg,
			ChatCategory.FRIENDS_CHAT, ChatMessageType.FRIENDSCHAT, channelNames.getFriendsChatName());
		if (config.showFriendsChat())
		{
			messageManager.addPublicClanMessage(line, config.publicMaxMessages());
		}
		if (config.showClanChatOverlay() && config.clanShowFriendsChat())
		{
			messageManager.addClanMessage(line, config.clanMaxMessages());
		}
	}

	private void handlePrivateChat(net.runelite.api.MessageNode messageNode, ChatMessageType type, String sender, String rawSenderName, String rawMsg)
	{
		boolean incoming = type != ChatMessageType.PRIVATECHATOUT;
		String prefix    = incoming ? "From " : "To ";
		ChatLine line    = new ChatLine(messageNode, prefix + sender, prefix + rawSenderName, rawMsg, ChatCategory.PRIVATE, type);
		if (config.showPrivateChat())
		{
			messageManager.addPrivateMessage(line, config.privateMaxMessages());
		}
		if (config.showPrivateChatInMain())
		{
			messageManager.addPublicClanMessage(line, config.publicMaxMessages());
		}
	}

	private boolean isSystemMessageFiltered(String lower)
	{
		if (!config.filterSpamAlerts())
		{
			return false;
		}
		return filterMatcher.matches(config.spamPatterns(), lower)
			|| (config.filterInteractionSpam() && filterMatcher.matchesInteraction(lower))
			|| (config.filterSkillingSpam() && filterMatcher.matchesSkilling(lower))
			|| (config.filterCombatLootSpam() && filterMatcher.matchesCombatLoot(lower))
			|| (config.filterConsumablesSpam() && filterMatcher.matchesConsumables(lower));
	}

	private void handleSystemMessage(net.runelite.api.MessageNode messageNode, ChatMessageType type, String rawMsg, String lower)
	{
		int gameFilter = client.getVarbitValue(VarbitID.GAME_FILTER);

		switch (type)
		{
			case GAMEMESSAGE:
			case ENGINE:
			{
				if (gameFilter == 2) return; // Off
				boolean blocked = isSystemMessageFiltered(lower);
				if (blocked) return;
				addSystemLine(messageNode, rawMsg, type, true);
				break;
			}
			case SPAM:
			{
				if (gameFilter != 0) return; // Filter or Off
				boolean blocked = isSystemMessageFiltered(lower);
				if (blocked) return;
				addSystemLine(messageNode, rawMsg, type, true);
				break;
			}
			case BROADCAST:
			{
				ChatLine line = new ChatLine(messageNode, null, null, rawMsg, ChatCategory.SYSTEM, type);
				if (config.showSystemAlerts())
				{
					messageManager.addSystemMessage(line, config.systemMaxAlerts(), false, 0L);
				}
				if (config.showGameMessagesInMain())
				{
					boolean blocked = isSystemMessageFiltered(lower);
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
				boolean blocked = isSystemMessageFiltered(lower);
				if (!blocked && config.showSystemAlerts())
				{
					messageManager.addSystemMessage(
						new ChatLine(messageNode, null, null, rawMsg, ChatCategory.SYSTEM, type),
						config.systemMaxAlerts(), config.filterSpamAlerts(),
						config.spamCooldownSeconds() * 1000L);
				}
				break;
			}
			case WELCOME:
			{
				ChatLine line = new ChatLine(messageNode, null, null, rawMsg, ChatCategory.SYSTEM, type);
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

	/** Creates and queues a standard system line (used for GAMEMESSAGE, ENGINE, SPAM). */
	private void addSystemLine(net.runelite.api.MessageNode messageNode, String rawMsg, ChatMessageType type, boolean useFilter)
	{
		ChatLine line = new ChatLine(messageNode, null, null, rawMsg, ChatCategory.SYSTEM, type);
		if (config.showSystemAlerts())
		{
			messageManager.addSystemMessage(line, config.systemMaxAlerts(),
				useFilter && config.filterSpamAlerts(), config.spamCooldownSeconds() * 1000L);
		}
		if (config.showGameMessagesInMain())
		{
			messageManager.addPublicClanMessage(line, config.publicMaxMessages());
		}
	}

	// ── Utilities ─────────────────────────────────────────────────────────────

	private String sanitizeName(String name)
	{
		return name != null ? Text.removeTags(name) : "";
	}

	@Provides
	ChatOverlayConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatOverlayConfig.class);
	}
}
