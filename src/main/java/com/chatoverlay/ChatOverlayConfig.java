package com.chatoverlay;

import java.awt.Color;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("chatoverlay")
public interface ChatOverlayConfig extends Config
{
	// ──────────────────────────────────────────────
	//  SECTIONS
	// ──────────────────────────────────────────────

	@ConfigSection(
		name = "General",
		description = "General plugin settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Main Chat",
		description = "Settings for the main chat overlay (bottom-left)",
		position = 1
	)
	String publicClanSection = "publicClan";

	@ConfigSection(
		name = "Private Chat",
		description = "Settings for the private chat overlay (above public/clan)",
		position = 2
	)
	String privateSection = "private";

	@ConfigSection(
		name = "Clan Chat",
		description = "Settings for the dedicated clan chat overlay",
		position = 3
	)
	String clanSection = "clan";

	@ConfigSection(
		name = "Game Chat",
		description = "Settings for game/system message alerts",
		position = 4
	)
	String systemSection = "system";

	// ──────────────────────────────────────────────
	//  GENERAL
	// ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "bubblePaddingX",
		name = "Bubble Padding (Horizontal)",
		description = "Left and right padding in pixels between the text and the bubble edge",
		position = 0,
		section = "general"
	)
	@Range(min = 0, max = 30)
	default int bubblePaddingX()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "bubblePaddingY",
		name = "Bubble Padding (Vertical)",
		description = "Top and bottom padding in pixels between the text and the bubble edge",
		position = 1,
		section = "general"
	)
	@Range(min = 0, max = 20)
	default int bubblePaddingY()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "bubbleSpacing",
		name = "Bubble Spacing",
		description = "Gap in pixels between consecutive chat bubbles",
		position = 2,
		section = "general"
	)
	@Range(min = 0, max = 20)
	default int bubbleSpacing()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "fontType",
		name = "Font",
		description = "Font used for all chat overlays",
		position = 3,
		section = "general"
	)
	default FontType fontType()
	{
		return FontType.RUNESCAPE;
	}

	@ConfigItem(
		keyName = "fontSize",
		name = "Font Size",
		description = "Font size for all chat overlays",
		position = 4,
		section = "general"
	)
	@Range(min = 8, max = 48)
	default int fontSize()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "showChatboxMessage",
		name = "Show Chatbox Message",
		description = "Display a bubble under the main chat overlay showing what you are currently typing",
		position = 5,
		section = "general"
	)
	default boolean showChatboxMessage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "peekEnabled",
		name = "Peek Mode",
		description = "Hold the peek key to temporarily reveal all faded messages at full opacity",
		position = 6,
		section = "general"
	)
	default boolean peekEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "peekKey",
		name = "Peek Key",
		description = "Hold this key to peek at all chat messages at full opacity",
		position = 7,
		section = "general"
	)
	default Keybind peekKey()
	{
		return new Keybind(KeyEvent.VK_ALT, 0);
	}

	@ConfigItem(
		keyName = "showPlayerIcons",
		name = "Show Player Icons",
		description = "Display Ironman and J-Mod crown icons next to sender names in chat bubbles",
		position = 8,
		section = "general"
	)
	default boolean showPlayerIcons()
	{
		return true;
	}

	// ──────────────────────────────────────────────
	//  MAIN CHAT  (bottom-left)
	// ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "publicHideWhenChatboxOpen",
		name = "Hide When Chatbox Visible",
		description = "Hide this overlay while the in-game chatbox is visible on screen",
		position = 0,
		section = "publicClan"
	)
	default boolean publicHideWhenChatboxOpen()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publicWordWrap",
		name = "Word Wrap",
		description = "When on, long messages wrap across multiple lines inside the bubble. "
			+ "When off, messages are truncated to a single line with an ellipsis.",
		position = 1,
		section = "publicClan"
	)
	default boolean publicWordWrap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publicFadeMessages",
		name = "Enable Fading Messages",
		description = "When on, messages gradually fade out before disappearing. "
			+ "When off, messages stay fully visible until they expire.",
		position = 2,
		section = "publicClan"
	)
	default boolean publicFadeMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPublicChat",
		name = "Show Public Chat",
		description = "Display public chat messages",
		position = 3,
		section = "publicClan"
	)
	default boolean showPublicChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showClanChat",
		name = "Show Clan Chat",
		description = "Display clan chat messages",
		position = 4,
		section = "publicClan"
	)
	default boolean showClanChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFriendsChat",
		name = "Show Friends Chat",
		description = "Display friends chat (FC) messages",
		position = 5,
		section = "publicClan"
	)
	default boolean showFriendsChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPrivateChatInMain",
		name = "Show Private Chat",
		description = "Include private messages in this overlay",
		position = 6,
		section = "publicClan"
	)
	default boolean showPrivateChatInMain()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showGameMessagesInMain",
		name = "Show Game Chat",
		description = "Also route game/system messages (GAMEMESSAGE, ENGINE, BROADCAST, WELCOME) into this main chat overlay. "
			+ "Off by default — the dedicated Game Chat overlay already handles them.",
		position = 7,
		section = "publicClan"
	)
	default boolean showGameMessagesInMain()
	{
		return false;
	}

	@ConfigItem(
		keyName = "publicOverlayWidth",
		name = "Overlay Width",
		description = "Width of the public/clan chat overlay in pixels",
		position = 8,
		section = "publicClan"
	)
	@Range(min = 200, max = 800)
	default int publicOverlayWidth()
	{
		return 400;
	}

	@ConfigItem(
		keyName = "publicBgColor",
		name = "Background Color",
		description = "Background color for the public/clan chat overlay",
		position = 9,
		section = "publicClan"
	)
	@Alpha
	default Color publicBgColor()
	{
		return new Color(30, 30, 30, 200);
	}

	@ConfigItem(
		keyName = "publicBgEnabled",
		name = "Show Background",
		description = "Toggle background on/off for a cleaner look",
		position = 10,
		section = "publicClan"
	)
	default boolean publicBgEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publicShowBubbleBorder",
		name = "Show Bubble Border",
		description = "Draw a 1px rounded border around each chat bubble",
		position = 11,
		section = "publicClan"
	)
	default boolean publicShowBubbleBorder()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "publicBubbleBorderColor",
		name = "Bubble Border Color",
		description = "Color of the bubble border in the main chat overlay",
		position = 12,
		section = "publicClan"
	)
	default Color publicBubbleBorderColor()
	{
		return new Color(52, 52, 52, 180);
	}

	@ConfigItem(
		keyName = "publicMessageDuration",
		name = "Message Duration (seconds)",
		description = "How long each message stays fully visible before disappearing. Set to 0 to keep messages indefinitely.",
		position = 13,
		section = "publicClan"
	)
	@Range(min = 0, max = 300)
	default int publicMessageDuration()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "publicMaxMessages",
		name = "Max Messages",
		description = "Maximum number of public/clan messages shown in the overlay",
		position = 14,
		section = "publicClan"
	)
	@Range(min = 1, max = 50)
	default int publicMaxMessages()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "publicShowTimestamp",
		name = "Show Timestamp [HH:MM]",
		description = "Prefix each message with its timestamp",
		position = 15,
		section = "publicClan"
	)
	default boolean publicShowTimestamp()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publicLayoutMode",
		name = "Layout Mode",
		description = "Stack messages from top to bottom or bottom to top",
		position = 16,
		section = "publicClan"
	)
	default LayoutMode publicLayoutMode()
	{
		return LayoutMode.BOTTOM_TO_TOP;
	}

	@ConfigItem(
		keyName = "publicOverlayHeight",
		name = "Overlay Height",
		description = "Height of the public/clan chat overlay in pixels (used in Bottom to Top mode)",
		position = 17,
		section = "publicClan"
	)
	@Range(min = 100, max = 800)
	default int publicOverlayHeight()
	{
		return 300;
	}

	// ──────────────────────────────────────────────
	//  PRIVATE CHAT  (above public/clan)
	// ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "privateHideWhenChatboxOpen",
		name = "Hide When Chatbox Visible",
		description = "Hide this overlay while the in-game chatbox is visible on screen",
		position = 0,
		section = "private"
	)
	default boolean privateHideWhenChatboxOpen()
	{
		return true;
	}

	@ConfigItem(
		keyName = "privateWordWrap",
		name = "Word Wrap",
		description = "When on, long messages wrap across multiple lines inside the bubble. "
			+ "When off, messages are truncated to a single line with an ellipsis.",
		position = 1,
		section = "private"
	)
	default boolean privateWordWrap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "privateFadeMessages",
		name = "Enable Fading Messages",
		description = "When on, messages gradually fade out before disappearing. "
			+ "When off, messages stay fully visible until they expire.",
		position = 2,
		section = "private"
	)
	default boolean privateFadeMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPrivateChat",
		name = "Show Private Chat",
		description = "Display private chat messages",
		position = 3,
		section = "private"
	)
	default boolean showPrivateChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "privateMaxMessages",
		name = "Max Messages",
		description = "Maximum number of private messages shown",
		position = 4,
		section = "private"
	)
	@Range(min = 1, max = 20)
	default int privateMaxMessages()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "privateBgColor",
		name = "Background Color",
		description = "Background color for the private chat overlay",
		position = 5,
		section = "private"
	)
	@Alpha
	default Color privateBgColor()
	{
		return new Color(30, 30, 30, 200);
	}

	@ConfigItem(
		keyName = "privateBgEnabled",
		name = "Show Background",
		description = "Toggle background on/off",
		position = 6,
		section = "private"
	)
	default boolean privateBgEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "privateShowBubbleBorder",
		name = "Show Bubble Border",
		description = "Draw a 1px rounded border around each chat bubble",
		position = 7,
		section = "private"
	)
	default boolean privateShowBubbleBorder()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "privateBubbleBorderColor",
		name = "Bubble Border Color",
		description = "Color of the bubble border in the private chat overlay",
		position = 8,
		section = "private"
	)
	default Color privateBubbleBorderColor()
	{
		return new Color(52, 52, 52, 180);
	}

	@ConfigItem(
		keyName = "privateOverlayWidth",
		name = "Overlay Width",
		description = "Width of the private chat overlay in pixels",
		position = 9,
		section = "private"
	)
	@Range(min = 200, max = 800)
	default int privateOverlayWidth()
	{
		return 400;
	}

	@ConfigItem(
		keyName = "privateMessageDuration",
		name = "Message Duration (seconds)",
		description = "How long each private message stays fully visible before disappearing. Set to 0 to keep messages indefinitely.",
		position = 10,
		section = "private"
	)
	@Range(min = 0, max = 300)
	default int privateMessageDuration()
	{
		return 120;
	}

	@ConfigItem(
		keyName = "privateShowTimestamp",
		name = "Show Timestamp [HH:MM]",
		description = "Prefix each message with its timestamp",
		position = 11,
		section = "private"
	)
	default boolean privateShowTimestamp()
	{
		return true;
	}

	@ConfigItem(
		keyName = "privateLayoutMode",
		name = "Layout Mode",
		description = "Stack messages from top to bottom or bottom to top",
		position = 12,
		section = "private"
	)
	default LayoutMode privateLayoutMode()
	{
		return LayoutMode.BOTTOM_TO_TOP;
	}

	@ConfigItem(
		keyName = "privateOverlayHeight",
		name = "Overlay Height",
		description = "Height of the private chat overlay in pixels (used in Bottom to Top mode)",
		position = 13,
		section = "private"
	)
	@Range(min = 100, max = 800)
	default int privateOverlayHeight()
	{
		return 200;
	}

	// ──────────────────────────────────────────────
	//  CLAN CHAT OVERLAY
	// ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "showClanChatOverlay",
		name = "Show Clan Chat Overlay",
		description = "Display a dedicated overlay for clan and friends chat messages",
		position = 0,
		section = "clan"
	)
	default boolean showClanChatOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "clanShowClan",
		name = "Show Clan Chat Messages",
		description = "Display regular clan chat messages in this overlay",
		position = 1,
		section = "clan"
	)
	default boolean clanShowClan()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clanShowGuest",
		name = "Show Guest Clan Messages",
		description = "Display guest clan chat messages in this overlay",
		position = 2,
		section = "clan"
	)
	default boolean clanShowGuest()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clanShowGim",
		name = "Show GIM Clan Messages",
		description = "Display Group Ironman clan chat messages in this overlay",
		position = 3,
		section = "clan"
	)
	default boolean clanShowGim()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clanShowFriendsChat",
		name = "Show Friends Chat Messages",
		description = "Display friends chat messages in this overlay",
		position = 4,
		section = "clan"
	)
	default boolean clanShowFriendsChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clanOverlayWidth",
		name = "Overlay Width",
		description = "Width of the dedicated clan chat overlay in pixels",
		position = 5,
		section = "clan"
	)
	@Range(min = 200, max = 800)
	default int clanOverlayWidth()
	{
		return 400;
	}

	@ConfigItem(
		keyName = "clanOverlayHeight",
		name = "Overlay Height",
		description = "Height of the dedicated clan chat overlay in pixels (used in Bottom to Top mode)",
		position = 6,
		section = "clan"
	)
	@Range(min = 100, max = 800)
	default int clanOverlayHeight()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "clanBgColor",
		name = "Background Color",
		description = "Background color for the dedicated clan chat overlay",
		position = 7,
		section = "clan"
	)
	@Alpha
	default Color clanBgColor()
	{
		return new Color(30, 30, 30, 200);
	}

	@ConfigItem(
		keyName = "clanBgEnabled",
		name = "Show Background",
		description = "Toggle background on/off",
		position = 8,
		section = "clan"
	)
	default boolean clanBgEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clanShowBubbleBorder",
		name = "Show Bubble Border",
		description = "Draw a 1px rounded border around each chat bubble",
		position = 9,
		section = "clan"
	)
	default boolean clanShowBubbleBorder()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "clanBubbleBorderColor",
		name = "Bubble Border Color",
		description = "Color of the bubble border in the clan chat overlay",
		position = 10,
		section = "clan"
	)
	default Color clanBubbleBorderColor()
	{
		return new Color(52, 52, 52, 180);
	}

	@ConfigItem(
		keyName = "clanMessageDuration",
		name = "Message Duration (seconds)",
		description = "How long each message stays fully visible. Set to 0 to keep indefinitely.",
		position = 11,
		section = "clan"
	)
	@Range(min = 0, max = 300)
	default int clanMessageDuration()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "clanMaxMessages",
		name = "Max Messages",
		description = "Maximum number of messages shown in the clan chat overlay",
		position = 12,
		section = "clan"
	)
	@Range(min = 1, max = 50)
	default int clanMaxMessages()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "clanLayoutMode",
		name = "Layout Mode",
		description = "Stack messages from top to bottom or bottom to top",
		position = 13,
		section = "clan"
	)
	default LayoutMode clanLayoutMode()
	{
		return LayoutMode.BOTTOM_TO_TOP;
	}

	@ConfigItem(
		keyName = "clanShowTimestamp",
		name = "Show Timestamp [HH:MM]",
		description = "Prefix each message with its timestamp",
		position = 14,
		section = "clan"
	)
	default boolean clanShowTimestamp()
	{
		return true;
	}

	// ──────────────────────────────────────────────
	//  SYSTEM ALERTS  (near player or free overlay)
	// ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "systemHideWhenChatboxOpen",
		name = "Hide When Chatbox Visible",
		description = "Hide this overlay while the in-game chatbox is visible on screen",
		position = 0,
		section = "system"
	)
	default boolean systemHideWhenChatboxOpen()
	{
		return false;
	}

	@ConfigItem(
		keyName = "systemWordWrap",
		name = "Word Wrap",
		description = "When on, long messages wrap across multiple lines inside the bubble. "
			+ "When off, messages are truncated to a single line with an ellipsis.",
		position = 1,
		section = "system"
	)
	default boolean systemWordWrap()
	{
		return false;
	}

	@ConfigItem(
		keyName = "systemFadeMessages",
		name = "Enable Fading Messages",
		description = "When on, messages gradually fade out before disappearing. "
			+ "When off, messages stay fully visible until they expire.",
		position = 2,
		section = "system"
	)
	default boolean systemFadeMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "systemAlertMode",
		name = "Overlay Mode",
		description = "Pinned to Player: bubbles float above your character. "
			+ "Free Overlay: a panel you can drag anywhere on screen.",
		position = 3,
		section = "system"
	)
	default GameOverlayMode systemAlertMode()
	{
		return GameOverlayMode.PINNED_TO_PLAYER;
	}

	@ConfigItem(
		keyName = "showSystemAlerts",
		name = "Show Game Chat",
		description = "Display game/system messages in the overlay",
		position = 4,
		section = "system"
	)
	default boolean showSystemAlerts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "systemAlertDuration",
		name = "Message Duration (seconds)",
		description = "How long each game message stays visible",
		position = 5,
		section = "system"
	)
	@Range(min = 1, max = 15)
	default int systemAlertDuration()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "systemMaxAlerts",
		name = "Max Visible Messages",
		description = "Maximum number of game messages shown at once (oldest removed)",
		position = 6,
		section = "system"
	)
	@Range(min = 1, max = 8)
	default int systemMaxAlerts()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "systemBgColor",
		name = "Background Color",
		description = "Background color for game chat bubbles",
		position = 7,
		section = "system"
	)
	@Alpha
	default Color systemBgColor()
	{
		return new Color(30, 30, 30, 200);
	}

	@ConfigItem(
		keyName = "systemShowBubbleBorder",
		name = "Show Bubble Border",
		description = "Draw a 1px rounded border around each game chat bubble",
		position = 8,
		section = "system"
	)
	default boolean systemShowBubbleBorder()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "systemBubbleBorderColor",
		name = "Bubble Border Color",
		description = "Color of the bubble border in the game chat overlay",
		position = 9,
		section = "system"
	)
	default Color systemBubbleBorderColor()
	{
		return new Color(52, 52, 52, 180);
	}

	@ConfigItem(
		keyName = "filterSpamAlerts",
		name = "Filter Spam",
		description = "Filter out repetitive/spammy system messages using the patterns below",
		position = 10,
		section = "system"
	)
	default boolean filterSpamAlerts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "filterInteractionSpam",
		name = "Filter Interaction Failures",
		description = "Filter out repetitive interaction block messages (e.g. 'I can't reach that', 'Nothing interesting happens')",
		position = 11,
		section = "system"
	)
	default boolean filterInteractionSpam()
	{
		return true;
	}

	@ConfigItem(
		keyName = "filterSkillingSpam",
		name = "Filter Skilling Spam",
		description = "Filter out repetitive skilling status messages (e.g. 'You swing your axe', 'You get some logs', 'You manage to mine')",
		position = 12,
		section = "system"
	)
	default boolean filterSkillingSpam()
	{
		return false;
	}

	@ConfigItem(
		keyName = "filterCombatLootSpam",
		name = "Filter Combat/Loot Spam",
		description = "Filter out minor combat or loot messages (e.g. Ring of Wealth coins, retrieving ammo)",
		position = 13,
		section = "system"
	)
	default boolean filterCombatLootSpam()
	{
		return false;
	}

	@ConfigItem(
		keyName = "filterConsumablesSpam",
		name = "Filter Consumables",
		description = "Filter out messages about eating food or drinking potions (e.g. 'You eat the shark', 'It heals some health')",
		position = 14,
		section = "system"
	)
	default boolean filterConsumablesSpam()
	{
		return false;
	}

	@ConfigItem(
		keyName = "spamPatterns",
		name = "Spam Patterns",
		description = "Comma-separated list of patterns to filter when 'Filter Spam' is on. "
			+ "Case-insensitive. If a pattern contains * it is treated as a wildcard (e.g. you*reach matches anything starting with 'you' and containing 'reach'). "
			+ "Otherwise the pattern is a plain substring match.",
		position = 15,
		section = "system"
	)
	default String spamPatterns()
	{
		return "you can't reach that,"
			+ "i can't reach that,"
			+ "nothing interesting happens,"
			+ "you can't do that right now,"
			+ "please finish what you're doing,"
			+ "you need to be closer,"
			+ "you can't use that here";
	}

	@ConfigItem(
		keyName = "spamCooldownSeconds",
		name = "Spam Cooldown (seconds)",
		description = "Minimum seconds between identical system messages. Set to 0 to allow all duplicates.",
		position = 16,
		section = "system"
	)
	@Range(min = 0, max = 30)
	default int spamCooldownSeconds()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "showLevelUpAlerts",
		name = "Show Level-Up Alerts",
		description = "Show level-up messages in game chat overlay",
		position = 17,
		section = "system"
	)
	default boolean showLevelUpAlerts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDropAlerts",
		name = "Show Loot/Drop Alerts",
		description = "Show valuable drop messages in game chat overlay",
		position = 18,
		section = "system"
	)
	default boolean showDropAlerts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "systemShowTimestamp",
		name = "Show Timestamp [HH:MM]",
		description = "Prefix each alert with its timestamp",
		position = 19,
		section = "system"
	)
	default boolean systemShowTimestamp()
	{
		return false;
	}

	@ConfigItem(
		keyName = "systemPlayerOffset",
		name = "Vertical Offset (Pinned)",
		description = "Vertical offset in 3D units above/below the player's head when Pinned to Player. "
			+ "Positive values move it higher, negative values move it lower.",
		position = 20,
		section = "system"
	)
	@Range(min = -300, max = 300)
	default int systemPlayerOffset()
	{
		return 90;
	}

}

