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
import net.runelite.client.plugins.chatfilter.ChatFilterConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.callback.ClientThread;
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
	@Inject private ClientThread          clientThread;
	@Inject private MessageFilterPolicy   messageFilters;

	private final ChatMessageManager messageManager = new ChatMessageManager();
	private volatile boolean running;

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

	@Inject private net.runelite.client.game.ChatIconManager chatIconManager;

	public int getFriendsChatRankIconIndex(String sender)
	{
		if (sender == null || sender.isEmpty())
		{
			return -1;
		}
		try
		{
			net.runelite.api.FriendsChatManager fcm = client.getFriendsChatManager();
			if (fcm == null)
			{
				return -1;
			}
			net.runelite.api.FriendsChatMember member = fcm.findByName(sender);
			if (member == null)
			{
				return -1;
			}
			net.runelite.api.FriendsChatRank rank = member.getRank();
			if (rank == null || rank == net.runelite.api.FriendsChatRank.UNRANKED)
			{
				return -1;
			}
			return chatIconManager.getIconNumber(rank);
		}
		catch (Exception e)
		{
			return -1;
		}
	}

	public int getClanRankIconIndex(String sender, boolean isGuest)
	{
		if (sender == null || sender.isEmpty())
		{
			return -1;
		}
		try
		{
			net.runelite.api.clan.ClanChannel channel = isGuest ? client.getGuestClanChannel() : client.getClanChannel();
			net.runelite.api.clan.ClanSettings settings = isGuest ? client.getGuestClanSettings() : client.getClanSettings();

			net.runelite.api.clan.ClanRank rank = null;
			if (channel != null)
			{
				net.runelite.api.clan.ClanChannelMember member = channel.findMember(sender);
				if (member != null)
				{
					rank = member.getRank();
				}
			}
			if (rank == null && settings != null)
			{
				net.runelite.api.clan.ClanMember member = settings.findMember(sender);
				if (member != null)
				{
					rank = member.getRank();
				}
			}

			if (rank == null || settings == null)
			{
				return -1;
			}

			net.runelite.api.clan.ClanTitle title = settings.titleForRank(rank);
			if (title == null)
			{
				return -1;
			}

			return chatIconManager.getIconNumber(title);
		}
		catch (Exception e)
		{
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
		migrateGlobalFilters();
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

		running = true;
		refreshOverlayLayers();

		rebuildHighlightPatterns();

		log.info("Chat Overlay plugin started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		running = false;
		keyManager.unregisterKeyListener(peekListener);
		peekActive = false;

		clientThread.invoke(this::removeOverlays);
		messageManager.clearAll();
		log.info("Chat Overlay plugin stopped");
	}

	private void removeOverlays()
	{
		overlayManager.remove(publicClanOverlay);
		overlayManager.remove(privateChatOverlay);
		overlayManager.remove(systemAlertOverlay);
		overlayManager.remove(clanChatOverlay);
	}

	private void refreshOverlayLayers()
	{
		clientThread.invoke(() ->
		{
			if (!running)
			{
				return;
			}
			removeOverlays();
			OverlayLayer layer = config.drawBelowInterfaces() ? OverlayLayer.ABOVE_SCENE : OverlayLayer.ABOVE_WIDGETS;
			publicClanOverlay.setLayer(layer);
			privateChatOverlay.setLayer(layer);
			clanChatOverlay.setLayer(layer);
			systemAlertOverlay.prepareRegistration();

			// Re-register to refresh RuneLite's layer registry and load saved floating placement.
			overlayManager.add(publicClanOverlay);
			overlayManager.add(privateChatOverlay);
			overlayManager.add(systemAlertOverlay);
			overlayManager.add(clanChatOverlay);
			systemAlertOverlay.finishRegistration();
		});
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
	public void onProfileChanged(ProfileChanged event)
	{
		migrateGlobalFilters();
	}

	/** Preserve saved preferences from the unreleased global-filter configuration. */
	private void migrateGlobalFilters()
	{
		String group = "chatoverlay";
		if (Boolean.parseBoolean(configManager.getConfiguration(group, "overlayFiltersMigrated")))
		{
			return;
		}

		String patterns = configManager.getConfiguration(group, "allChatPatterns");
		if (patterns != null && !patterns.trim().isEmpty())
		{
			boolean enabled = Boolean.parseBoolean(configManager.getConfiguration(group, "filterAllChatPatterns"));
			for (String overlay : new String[]{"main", "private", "clan"})
			{
				String current = configManager.getConfiguration(group, overlay + "FilterPatterns");
				if ((current == null || current.isEmpty())
					&& !Boolean.parseBoolean(configManager.getConfiguration(group, overlay + "FilterEnabled")))
				{
					configManager.setConfiguration(group, overlay + "FilterPatterns", patterns);
					configManager.setConfiguration(group, overlay + "FilterEnabled", enabled);
				}
			}

			if (enabled)
			{
				String[] keys = {"spamPatterns", "filterSpamAlerts", "spamCooldownSeconds"};
				Object[] values = {config.spamPatterns(), config.filterSpamAlerts(), config.spamCooldownSeconds()};
				for (int i = 0; i < keys.length; i++)
				{
					String backupKey = "globalFilterBackup_" + keys[i];
					if (configManager.getConfiguration(group, backupKey) == null)
					{
						configManager.setConfiguration(group, backupKey, values[i]);
					}
				}

				boolean gameEnabled = Boolean.parseBoolean(configManager.getConfiguration(group, "globalFilterBackup_filterSpamAlerts"));
				String gamePatterns = gameEnabled ? configManager.getConfiguration(group, "globalFilterBackup_spamPatterns") : "";
				String cooldown = gameEnabled ? configManager.getConfiguration(group, "globalFilterBackup_spamCooldownSeconds") : "0";
				configManager.setConfiguration(group, "spamPatterns", gamePatterns.isEmpty() ? patterns : gamePatterns + "\n" + patterns);
				configManager.setConfiguration(group, "spamCooldownSeconds", cooldown);
				configManager.setConfiguration(group, "filterSpamAlerts", true);
			}
		}
		configManager.setConfiguration(group, "overlayFiltersMigrated", true);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("chatoverlay") && event.getKey().equals("highlightKeywordsList"))
		{
			rebuildHighlightPatterns();
		}
		else if (event.getGroup().equals("chatoverlay")
			&& (event.getKey().equals("drawBelowInterfaces") || event.getKey().equals("systemAlertMode")))
		{
			refreshOverlayLayers();
		}
		else if (event.getGroup().equals("chatfilter"))
		{
			if ("collapseGameChat".equals(event.getKey()))
			{
				if (isCollapseGameChat())
				{
					messageManager.collapseSystemMessages();
				}
			}
			else if ("collapsePlayerChat".equals(event.getKey()))
			{
				if (isCollapsePlayerChat())
				{
					messageManager.collapsePlayerMessages();
				}
			}
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

			int rankIconIdx = -1;
			if (type == ChatMessageType.FRIENDSCHAT)
			{
				rankIconIdx = getFriendsChatRankIconIndex(sender);
			}
			else if (type == ChatMessageType.CLAN_CHAT || type == ChatMessageType.CLAN_GIM_CHAT || type == ChatMessageType.CLAN_MESSAGE)
			{
				rankIconIdx = getClanRankIconIndex(sender, false);
			}
			else if (type == ChatMessageType.CLAN_GUEST_CHAT || type == ChatMessageType.CLAN_GUEST_MESSAGE)
			{
				rankIconIdx = getClanRankIconIndex(sender, true);
			}

			if (rankIconIdx != -1 && !rawSenderName.contains("<img=" + rankIconIdx + ">"))
			{
				rawSenderName = "<img=" + rankIconIdx + ">" + rawSenderName;
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
			case ITEM_EXAMINE: case NPC_EXAMINE: case OBJECT_EXAMINE:
				handleSystemMessage(event.getMessageNode(), type, rawMsg, lower);
				break;

			default:
				break;
		}
	}

	public boolean isCollapseGameChat()
	{
		try
		{
			ChatFilterConfig chatFilterConfig = configManager.getConfig(ChatFilterConfig.class);
			if (chatFilterConfig != null)
			{
				return chatFilterConfig.collapseGameChat();
			}
		}
		catch (Exception ignored) {}

		Boolean val = configManager.getConfiguration("chatfilter", "collapseGameChat", Boolean.class);
		return val != null && val;
	}

	public boolean isCollapsePlayerChat()
	{
		try
		{
			ChatFilterConfig chatFilterConfig = configManager.getConfig(ChatFilterConfig.class);
			if (chatFilterConfig != null)
			{
				return chatFilterConfig.collapsePlayerChat();
			}
		}
		catch (Exception ignored) {}

		Boolean val = configManager.getConfiguration("chatfilter", "collapsePlayerChat", Boolean.class);
		return val != null && val;
	}

	// ── onChatMessage handlers ─────────────────────────────────────────────

	private void handlePublicChat(net.runelite.api.MessageNode messageNode, ChatMessageType type, String sender, String rawSenderName, String rawMsg)
	{
		ChatLine line = new ChatLine(messageNode, sender, rawSenderName, rawMsg, ChatCategory.PUBLIC, type);
		messageManager.addPublicClanMessage(line, 100, isCollapsePlayerChat());
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
		messageManager.addPublicClanMessage(line, 100, isCollapsePlayerChat());
		messageManager.addClanMessage(line, 100, isCollapsePlayerChat());
	}

	private void handleFriendsChat(net.runelite.api.MessageNode messageNode, String sender, String rawSenderName, String rawMsg)
	{
		ChatLine line = new ChatLine(messageNode, sender, rawSenderName, rawMsg,
			ChatCategory.FRIENDS_CHAT, ChatMessageType.FRIENDSCHAT, channelNames.getFriendsChatName());
		messageManager.addPublicClanMessage(line, 100, isCollapsePlayerChat());
		messageManager.addClanMessage(line, 100, isCollapsePlayerChat());
	}

	private void handlePrivateChat(net.runelite.api.MessageNode messageNode, ChatMessageType type, String sender, String rawSenderName, String rawMsg)
	{
		boolean incoming = type != ChatMessageType.PRIVATECHATOUT;
		String prefix    = incoming ? "From " : "To ";
		ChatLine line    = new ChatLine(messageNode, prefix + sender, prefix + rawSenderName, rawMsg, ChatCategory.PRIVATE, type);
		messageManager.addPrivateMessage(line, 100, isCollapsePlayerChat());
		messageManager.addPublicClanMessage(line, 100, isCollapsePlayerChat());
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
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
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
				messageManager.addSystemMessage(line, 100, false, 0L, isCollapseGameChat());
				messageManager.addPublicClanMessage(line, 100, isCollapseGameChat());
				break;
			}
			case FRIENDSCHATNOTIFICATION:
			case FRIENDNOTIFICATION:
			case LOGINLOGOUTNOTIFICATION:
			{
				ChatLine line = new ChatLine(messageNode, null, null, rawMsg, ChatCategory.SYSTEM, type);
				boolean added = messageManager.addSystemMessage(line, 100, config.filterSpamAlerts(), config.spamCooldownSeconds() * 1000L, isCollapseGameChat());
				if (added)
				{
					messageManager.addPublicClanMessage(line, 100, isCollapseGameChat());
				}
				break;
			}
			case WELCOME:
			{
				ChatLine line = new ChatLine(messageNode, null, null, rawMsg, ChatCategory.SYSTEM, type);
				messageManager.addSystemMessage(line, 100, false, 0L, isCollapseGameChat());
				messageManager.addPublicClanMessage(line, 100, isCollapseGameChat());
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
			useFilter && config.filterSpamAlerts(), config.spamCooldownSeconds() * 1000L, isCollapseGameChat());
		if (added)
		{
			messageManager.addPublicClanMessage(line, 100, isCollapseGameChat());
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
		return line != null && passesInGameFilters(line) && messageFilters.shouldShow(line);
	}

	public boolean shouldShowMessage(ChatLine line, boolean filterEnabled, String patterns)
	{
		return shouldShowMessage(line) && (!filterEnabled || !messageFilters.matches(patterns, line));
	}

	private boolean passesInGameFilters(ChatLine line)
	{
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
				if (line.getChatMessageType() == ChatMessageType.LOGINLOGOUTNOTIFICATION
					&& !areFriendNotificationsEnabled())
				{
					return false;
				}
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

	private boolean areFriendNotificationsEnabled()
	{
		// Game script 4487: a nonzero legacy setting overrides the newer setting. 2 means Off.
		int legacySetting = client.getVarbitValue(VarbitID.LOGINLOGOUT_PERMANENT);
		int setting = legacySetting != 0 ? legacySetting : client.getVarbitValue(VarbitID.LOGINLOGOUT_SETTING);
		return setting != 2;
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
