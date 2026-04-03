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
	@Inject private ChatColorResolver     colorResolver;
	@Inject private PlayerIconLoader      iconLoader;
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
		else if (widgetId == InterfaceID.Chatbox.CHAT_FRIENDSCHAT){ messageManager.clearPublicClanMessages(); }
		else if (widgetId == InterfaceID.Chatbox.CHAT_CLAN)       { messageManager.clearPublicClanMessages(); }
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
				handlePublicChat(type, sender, rawSenderName, rawMsg);
				break;

			case CLAN_CHAT: case CLAN_GIM_CHAT: case CLAN_GUEST_CHAT:
			case CLAN_MESSAGE: case CLAN_GUEST_MESSAGE:
				handleClanChat(type, sender, rawSenderName, rawMsg);
				break;

			case FRIENDSCHAT:
				handleFriendsChat(sender, rawSenderName, rawMsg);
				break;

			case PRIVATECHAT: case MODPRIVATECHAT: case PRIVATECHATOUT:
				handlePrivateChat(type, sender, rawSenderName, rawMsg);
				break;

			case GAMEMESSAGE: case ENGINE: case SPAM:
			case BROADCAST: case FRIENDSCHATNOTIFICATION: case FRIENDNOTIFICATION:
			case LOGINLOGOUTNOTIFICATION: case WELCOME:
				handleSystemMessage(type, rawMsg, lower);
				break;

			default:
				break;
		}
	}

	// ── onChatMessage handlers ─────────────────────────────────────────────

	private void handlePublicChat(ChatMessageType type, String sender, String rawSenderName, String rawMsg)
	{
		if (!config.showPublicChat())
		{
			return;
		}
		ChatLine line = new ChatLine(sender, rawSenderName, rawMsg, ChatCategory.PUBLIC, type);
		iconLoader.resolveAndSetIcon(line, iconLoader.extractIconId(rawSenderName));
		messageManager.addPublicClanMessage(line, config.publicMaxMessages());
	}

	private void handleClanChat(ChatMessageType type, String sender, String rawSenderName, String rawMsg)
	{
		if (!config.showClanChat())
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
		ChatLine line = new ChatLine(sender, rawSenderName, rawMsg, ChatCategory.CLAN, type, channelName);
		iconLoader.resolveAndSetIcon(line, iconLoader.extractIconId(rawSenderName));
		messageManager.addPublicClanMessage(line, config.publicMaxMessages());
	}

	private void handleFriendsChat(String sender, String rawSenderName, String rawMsg)
	{
		if (!config.showFriendsChat())
		{
			return;
		}
		ChatLine line = new ChatLine(sender, rawSenderName, rawMsg,
			ChatCategory.FRIENDS_CHAT, ChatMessageType.FRIENDSCHAT, channelNames.getFriendsChatName());
		iconLoader.resolveAndSetIcon(line, iconLoader.extractIconId(rawSenderName));
		messageManager.addPublicClanMessage(line, config.publicMaxMessages());
	}

	private void handlePrivateChat(ChatMessageType type, String sender, String rawSenderName, String rawMsg)
	{
		boolean incoming = type != ChatMessageType.PRIVATECHATOUT;
		String prefix    = incoming ? "From " : "To ";
		ChatLine line    = new ChatLine(prefix + sender, prefix + rawSenderName, rawMsg, ChatCategory.PRIVATE, type);
		if (incoming)
		{
			iconLoader.resolveAndSetIcon(line, iconLoader.extractIconId(rawSenderName));
		}
		if (config.showPrivateChat())
		{
			messageManager.addPrivateMessage(line, config.privateMaxMessages());
		}
		if (config.showPrivateChatInMain())
		{
			messageManager.addPublicClanMessage(line, config.publicMaxMessages());
		}
	}

	private void handleSystemMessage(ChatMessageType type, String rawMsg, String lower)
	{
		int gameFilter = client.getVarbitValue(VarbitID.GAME_FILTER);

		switch (type)
		{
			case GAMEMESSAGE:
			case ENGINE:
			{
				if (gameFilter == 2) return; // Off
				boolean blocked = config.filterSpamAlerts() && filterMatcher.matches(config.spamPatterns(), lower);
				if (blocked) return;
				addSystemLine(rawMsg, type, true);
				break;
			}
			case SPAM:
			{
				if (gameFilter != 0) return; // Filter or Off
				boolean blocked = config.filterSpamAlerts() && filterMatcher.matches(config.spamPatterns(), lower);
				if (blocked) return;
				addSystemLine(rawMsg, type, true);
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
					boolean blocked = config.filterSpamAlerts() && filterMatcher.matches(config.spamPatterns(), lower);
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
				boolean blocked = config.filterSpamAlerts() && filterMatcher.matches(config.spamPatterns(), lower);
				if (!blocked && config.showSystemAlerts())
				{
					messageManager.addSystemMessage(
						new ChatLine(null, null, rawMsg, ChatCategory.SYSTEM, type),
						config.systemMaxAlerts(), config.filterSpamAlerts(),
						config.spamCooldownSeconds() * 1000L);
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

	/** Creates and queues a standard system line (used for GAMEMESSAGE, ENGINE, SPAM). */
	private void addSystemLine(String rawMsg, ChatMessageType type, boolean useFilter)
	{
		ChatLine line = new ChatLine(null, null, rawMsg, ChatCategory.SYSTEM, type);
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
