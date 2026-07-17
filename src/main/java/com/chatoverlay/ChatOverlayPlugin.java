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
import net.runelite.api.events.MenuOpened;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import java.awt.event.KeyEvent;
import net.runelite.client.util.Text;
import net.runelite.api.Player;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.Keybind;
import net.runelite.api.vars.AccountType;

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
	@Inject private ConfigManager       configManager;
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
	private KeyListener        peekListener;
	private final java.util.List<java.util.regex.Pattern> compiledHighlightPatterns = new java.util.ArrayList<>();

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

	public int getLocalPlayerIconIndex()
	{
		AccountType accountType = client.getAccountType();
		if (accountType == null)
		{
			return -1;
		}

		switch (accountType)
		{
			case IRONMAN:
				return 2;
			case ULTIMATE_IRONMAN:
				return 3;
			case HARDCORE_IRONMAN:
				return 4;
			case GROUP_IRONMAN:
				return 41;
			case HARDCORE_GROUP_IRONMAN:
				return 42;
			default:
				return -1;
		}
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
		peekListener = new KeyListener()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				Keybind keybind = config.peekKey();
				if (config.peekEnabled() && keybind != null && keybind.matches(e))
				{
					peekActive = true;
				}
			}

			@Override
			public void keyReleased(KeyEvent e)
			{
				Keybind keybind = config.peekKey();
				if (keybind != null && keybind.matches(e))
				{
					peekActive = false;
				}
			}

			@Override
			public void keyTyped(KeyEvent e)
			{
			}

			@Override
			public void focusLost()
			{
				peekActive = false;
			}

			@Override
			public boolean isEnabledOnLoginScreen()
			{
				return false;
			}
		};
		keyManager.registerKeyListener(peekListener);

		overlayManager.add(publicClanOverlay);
		overlayManager.add(privateChatOverlay);
		overlayManager.add(systemAlertOverlay);
		overlayManager.add(clanChatOverlay);

		rebuildHighlightPatterns();

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
		String option = event.getMenuOption();
		if ("Chat Overlay: Show".equals(option) || "Chat Overlay: Hide".equals(option))
		{
			String target = event.getMenuTarget();
			boolean enable = "Chat Overlay: Show".equals(option);
			String configKey = null;

			if (target.contains("Game"))
			{
				configKey = "showSystemAlerts";
			}
			else if (target.contains("Main Chat"))
			{
				configKey = "showMainChatOverlay";
			}
			else if (target.contains("Private"))
			{
				configKey = "showPrivateChat";
			}
			else if (target.contains("Clan"))
			{
				configKey = "showClanChatOverlay";
			}

			if (configKey != null)
			{
				configManager.setConfiguration("chatoverlay", configKey, enable);
			}
			return;
		}

		if (!option.endsWith("Clear history"))
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
		if (event.getGroup().equals("chatoverlay") && event.getKey().equals("highlightKeywordsList"))
		{
			rebuildHighlightPatterns();
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries == null || menuEntries.length == 0)
		{
			return;
		}

		MenuEntry tabEntry = null;
		String tabName = null;
		for (MenuEntry entry : menuEntries)
		{
			if (entry.getParam1() == InterfaceID.Chatbox.CHAT_ALL)
			{
				tabEntry = entry;
				tabName = "All";
				break;
			}

			String opt = entry.getOption();
			if (opt == null)
			{
				continue;
			}
			if (opt.startsWith("<col=ffff00>Game:</col>"))
			{
				tabEntry = entry;
				tabName = "Game";
				break;
			}
			if (opt.startsWith("<col=ffff00>Private:</col>"))
			{
				tabEntry = entry;
				tabName = "Private";
				break;
			}
			if (opt.startsWith("<col=ffff00>Clan:</col>"))
			{
				tabEntry = entry;
				tabName = "Clan";
				break;
			}
		}

		if (tabEntry == null || tabName == null)
		{
			return;
		}

		boolean currentValue = false;
		if ("Game".equals(tabName))
		{
			currentValue = config.showSystemAlerts();
		}
		else if ("All".equals(tabName))
		{
			currentValue = config.showMainChatOverlay();
		}
		else if ("Private".equals(tabName))
		{
			currentValue = config.showPrivateChat();
		}
		else if ("Clan".equals(tabName))
		{
			currentValue = config.showClanChatOverlay();
		}

		String option = currentValue ? "Chat Overlay: Hide" : "Chat Overlay: Show";

		client.createMenuEntry(-1)
			.setOption(option)
			.setTarget("<col=ffff00>" + ("All".equals(tabName) ? "Main Chat" : tabName) + "</col>")
			.setType(MenuAction.RUNELITE)
			.setParam0(tabEntry.getParam0())
			.setParam1(tabEntry.getParam1());
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

		if (config.showPlayerIcons())
		{
			String localName = getLocalPlayerName();
			if (localName != null && sender.equalsIgnoreCase(localName))
			{
				if (!rawSenderName.contains("<img="))
				{
					int iconIdx = getLocalPlayerIconIndex();
					if (iconIdx != -1)
					{
						rawSenderName = "<img=" + iconIdx + ">" + rawSenderName;
					}
				}
			}
		}

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
			case LOGINLOGOUTNOTIFICATION: case WELCOME: case CONSOLE: case UNKNOWN:
				handleSystemMessage(event.getMessageNode(), type, rawMsg, lower);
				break;

			default:
				break;
		}
	}

	// ── onChatMessage handlers ─────────────────────────────────────────────

	private void handlePublicChat(net.runelite.api.MessageNode messageNode, ChatMessageType type, String sender, String rawSenderName, String rawMsg)
	{
		ChatLine line = new ChatLine(messageNode, sender, rawSenderName, rawMsg, ChatCategory.PUBLIC, type);
		messageManager.addPublicClanMessage(line, 100);
	}

	private void handleClanChat(net.runelite.api.MessageNode messageNode, ChatMessageType type, String sender, String rawSenderName, String rawMsg)
	{
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
		messageManager.addPublicClanMessage(line, 100);
		messageManager.addClanMessage(line, 100);
	}

	private void handleFriendsChat(net.runelite.api.MessageNode messageNode, String sender, String rawSenderName, String rawMsg)
	{
		ChatLine line = new ChatLine(messageNode, sender, rawSenderName, rawMsg,
			ChatCategory.FRIENDS_CHAT, ChatMessageType.FRIENDSCHAT, channelNames.getFriendsChatName());
		messageManager.addPublicClanMessage(line, 100);
		messageManager.addClanMessage(line, 100);
	}

	private void handlePrivateChat(net.runelite.api.MessageNode messageNode, ChatMessageType type, String sender, String rawSenderName, String rawMsg)
	{
		boolean incoming = type != ChatMessageType.PRIVATECHATOUT;
		String prefix    = incoming ? "From " : "To ";
		ChatLine line    = new ChatLine(messageNode, prefix + sender, prefix + rawSenderName, rawMsg, ChatCategory.PRIVATE, type);
		messageManager.addPrivateMessage(line, 100);
		messageManager.addPublicClanMessage(line, 100);
	}

	public boolean isSystemMessageFiltered(String lower)
	{
		return (config.filterSpamAlerts() && filterMatcher.matches(config.spamPatterns(), lower))
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
			case CONSOLE:
			case UNKNOWN:
			{
				if (gameFilter == 2) return; // Off
				addSystemLine(messageNode, rawMsg, type, true);
				break;
			}
			case SPAM:
			{
				if (gameFilter != 0) return; // Filter or Off
				addSystemLine(messageNode, rawMsg, type, true);
				break;
			}
			case BROADCAST:
			{
				ChatLine line = new ChatLine(messageNode, null, null, rawMsg, ChatCategory.SYSTEM, type);
				messageManager.addSystemMessage(line, 100, false, 0L);
				messageManager.addPublicClanMessage(line, 100);
				break;
			}
			case FRIENDSCHATNOTIFICATION:
			case FRIENDNOTIFICATION:
			case LOGINLOGOUTNOTIFICATION:
			{
				ChatLine line = new ChatLine(messageNode, null, null, rawMsg, ChatCategory.SYSTEM, type);
				boolean added = messageManager.addSystemMessage(line, 100, config.filterSpamAlerts(), config.spamCooldownSeconds() * 1000L);
				if (added)
				{
					messageManager.addPublicClanMessage(line, 100);
				}
				break;
			}
			case WELCOME:
			{
				ChatLine line = new ChatLine(messageNode, null, null, rawMsg, ChatCategory.SYSTEM, type);
				messageManager.addSystemMessage(line, 100, false, 0L);
				messageManager.addPublicClanMessage(line, 100);
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
		boolean added = messageManager.addSystemMessage(line, 100,
			useFilter && config.filterSpamAlerts(), config.spamCooldownSeconds() * 1000L);
		if (added)
		{
			messageManager.addPublicClanMessage(line, 100);
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

	public boolean shouldHighlight(ChatLine line)
	{
		if (!config.highlightKeywords())
		{
			return false;
		}

		String text = line.getPlainMessage().toLowerCase();

		// Check custom keywords
		if (!compiledHighlightPatterns.isEmpty())
		{
			for (java.util.regex.Pattern pattern : compiledHighlightPatterns)
			{
				if (pattern.matcher(text).find())
				{
					return true;
				}
			}
		}

		return false;
	}

	private void rebuildHighlightPatterns()
	{
		compiledHighlightPatterns.clear();
		String customWords = config.highlightKeywordsList();
		if (customWords == null || customWords.trim().isEmpty())
		{
			return;
		}

		for (String word : customWords.split(","))
		{
			String trimmed = word.trim().toLowerCase();
			if (trimmed.isEmpty())
			{
				continue;
			}
			String regex = keywordToRegex(trimmed);
			try
			{
				compiledHighlightPatterns.add(java.util.regex.Pattern.compile(regex));
			}
			catch (java.util.regex.PatternSyntaxException e)
			{
				log.warn("Failed to compile custom highlight keyword regex: " + regex, e);
			}
		}
	}

	private static String keywordToRegex(String keyword)
	{
		if (keyword == null || keyword.isEmpty())
		{
			return "";
		}

		StringBuilder sb = new StringBuilder();

		// Check if it starts with word char and doesn't start with wildcard
		boolean startsWithWordChar = false;
		if (!keyword.startsWith("*"))
		{
			char first = keyword.charAt(0);
			if (Character.isLetterOrDigit(first) || first == '_')
			{
				startsWithWordChar = true;
			}
		}

		// Check if it ends with word char and doesn't end with wildcard
		boolean endsWithWordChar = false;
		if (!keyword.endsWith("*"))
		{
			char last = keyword.charAt(keyword.length() - 1);
			if (Character.isLetterOrDigit(last) || last == '_')
			{
				endsWithWordChar = true;
			}
		}

		if (startsWithWordChar)
		{
			sb.append("\\b");
		}

		String[] parts = keyword.split("\\*", -1);
		for (int i = 0; i < parts.length; i++)
		{
			if (i > 0)
			{
				sb.append(".*");
			}
			sb.append(java.util.regex.Pattern.quote(parts[i]));
		}

		if (endsWithWordChar)
		{
			sb.append("\\b");
		}

		return sb.toString();
	}

	private enum ChatFilterState
	{
		ON, FRIENDS, OFF, HIDE, FILTERED
	}

	private ChatFilterState getFilterState(int childId)
	{
		Widget w = client.getWidget(162, childId);
		if (w == null)
		{
			return ChatFilterState.ON;
		}
		String text = w.getText();
		if (text == null)
		{
			return ChatFilterState.ON;
		}
		String lowerText = text.toLowerCase();
		if (lowerText.contains("off"))
		{
			return ChatFilterState.OFF;
		}
		if (lowerText.contains("hide"))
		{
			return ChatFilterState.HIDE;
		}
		if (lowerText.contains("friends"))
		{
			return ChatFilterState.FRIENDS;
		}
		if (lowerText.contains("filter"))
		{
			return ChatFilterState.FILTERED;
		}
		return ChatFilterState.ON;
	}

	public boolean shouldShowMessage(ChatLine line)
	{
		if (line == null)
		{
			return false;
		}

		ChatCategory category = line.getCategory();
		if (category == null)
		{
			return true;
		}

		String localPlayerName = getLocalPlayerName();

		switch (category)
		{
			case PUBLIC:
			{
				ChatFilterState state = getFilterState(14);
				if (state == ChatFilterState.OFF || state == ChatFilterState.HIDE)
				{
					return false;
				}
				if (state == ChatFilterState.FRIENDS)
				{
					String sender = normalizeName(line.getSender());
					if (localPlayerName != null && normalizeName(localPlayerName).equalsIgnoreCase(sender))
					{
						return true;
					}
					return client.isFriended(sender, false);
				}
				break;
			}
			case PRIVATE:
			{
				ChatFilterState state = getFilterState(18);
				if (state == ChatFilterState.OFF)
				{
					return false;
				}
				if (state == ChatFilterState.FRIENDS)
				{
					String sender = line.getSender();
					if (sender.startsWith("From "))
					{
						sender = sender.substring(5);
					}
					else if (sender.startsWith("To "))
					{
						sender = sender.substring(3);
					}
					sender = normalizeName(sender);

					if (localPlayerName != null && normalizeName(localPlayerName).equalsIgnoreCase(sender))
					{
						return true;
					}
					if (line.getChatMessageType() == ChatMessageType.PRIVATECHATOUT)
					{
						return true;
					}
					return client.isFriended(sender, false);
				}
				break;
			}
			case CLAN:
			{
				ChatFilterState state = getFilterState(26);
				if (state == ChatFilterState.OFF)
				{
					return false;
				}
				if (state == ChatFilterState.FRIENDS)
				{
					String sender = normalizeName(line.getSender());
					if (localPlayerName != null && normalizeName(localPlayerName).equalsIgnoreCase(sender))
					{
						return true;
					}
					return client.isFriended(sender, false);
				}
				break;
			}
			case FRIENDS_CHAT:
			{
				ChatFilterState state = getFilterState(22);
				if (state == ChatFilterState.OFF)
				{
					return false;
				}
				if (state == ChatFilterState.FRIENDS)
				{
					String sender = normalizeName(line.getSender());
					if (localPlayerName != null && normalizeName(localPlayerName).equalsIgnoreCase(sender))
					{
						return true;
					}
					return client.isFriended(sender, false);
				}
				break;
			}
			case SYSTEM:
			{
				ChatFilterState state = getFilterState(10);
				if (state == ChatFilterState.OFF)
				{
					return false;
				}
				if (state == ChatFilterState.FILTERED)
				{
					if (line.getChatMessageType() == ChatMessageType.SPAM)
					{
						return false;
					}
				}
				break;
			}
		}
		return true;
	}

	private String normalizeName(String name)
	{
		if (name == null)
		{
			return "";
		}
		return Text.removeTags(name).replace('\u00a0', ' ').trim();
	}
}
