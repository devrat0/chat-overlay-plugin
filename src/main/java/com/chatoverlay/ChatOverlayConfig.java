package com.chatoverlay;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
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
		name = "Game Chat",
		description = "Settings for game/system message alerts",
		position = 3
	)
	String systemSection = "system";

	// ──────────────────────────────────────────────
	//  GENERAL
	// ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "hideWhenChatboxOpen",
		name = "Hide When Chatbox Visible",
		description = "Hide all chat overlays while the in-game chatbox is visible on screen",
		position = 0,
		section = "general"
	)
	default boolean hideWhenChatboxOpen()
	{
		return false;
	}

	@ConfigItem(
		keyName = "fontType",
		name = "Font",
		description = "Font used for all chat overlays",
		position = 1,
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
		position = 2,
		section = "general"
	)
	@Range(min = 8, max = 48)
	default int fontSize()
	{
		return 15;
	}

	// ──────────────────────────────────────────────
	//  MAIN CHAT  (bottom-left)
	// ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "showPublicChat",
		name = "Show Public Chat",
		description = "Display public chat messages",
		position = 0,
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
		position = 1,
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
		position = 2,
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
		position = 3,
		section = "publicClan"
	)
	default boolean showPrivateChatInMain()
	{
		return false;
	}

	@ConfigItem(
		keyName = "publicOverlayWidth",
		name = "Overlay Width",
		description = "Width of the public/clan chat overlay in pixels",
		position = 4,
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
		position = 5,
		section = "publicClan"
	)
	@Alpha
	default Color publicBgColor()
	{
		return new Color(0, 0, 0, 140);
	}

	@ConfigItem(
		keyName = "publicBgEnabled",
		name = "Show Background",
		description = "Toggle background on/off for a cleaner look",
		position = 6,
		section = "publicClan"
	)
	default boolean publicBgEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publicMessageDuration",
		name = "Message Duration (seconds)",
		description = "How long each message stays fully visible before fading out. Set to 0 to never fade.",
		position = 7,
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
		position = 8,
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
		position = 9,
		section = "publicClan"
	)
	default boolean publicShowTimestamp()
	{
		return true;
	}

	// ──────────────────────────────────────────────
	//  PRIVATE CHAT  (above public/clan)
	// ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "showPrivateChat",
		name = "Show Private Chat",
		description = "Display private chat messages",
		position = 0,
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
		position = 1,
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
		position = 2,
		section = "private"
	)
	@Alpha
	default Color privateBgColor()
	{
		return new Color(20, 0, 40, 160);
	}

	@ConfigItem(
		keyName = "privateBgEnabled",
		name = "Show Background",
		description = "Toggle background on/off",
		position = 3,
		section = "private"
	)
	default boolean privateBgEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "privateOverlayWidth",
		name = "Overlay Width",
		description = "Width of the private chat overlay in pixels",
		position = 4,
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
		description = "How long each private message stays fully visible before fading out. Set to 0 to never fade.",
		position = 5,
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
		position = 6,
		section = "private"
	)
	default boolean privateShowTimestamp()
	{
		return true;
	}

	// ──────────────────────────────────────────────
	//  SYSTEM ALERTS  (near player or free overlay)
	// ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "systemAlertMode",
		name = "Overlay Mode",
		description = "Pinned to Player: bubbles float above your character. "
			+ "Free Overlay: a panel you can drag anywhere on screen.",
		position = 0,
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
		position = 1,
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
		position = 2,
		section = "system"
	)
	@Range(min = 1, max = 15)
	default int systemAlertDuration()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "systemMaxAlerts",
		name = "Max Visible Messages",
		description = "Maximum number of game messages shown at once (oldest fade out)",
		position = 3,
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
		position = 4,
		section = "system"
	)
	@Alpha
	default Color systemBgColor()
	{
		return new Color(30, 30, 30, 200);
	}

	@ConfigItem(
		keyName = "filterSpamAlerts",
		name = "Filter Spam",
		description = "Filter out repetitive/spammy system messages using the patterns below",
		position = 5,
		section = "system"
	)
	default boolean filterSpamAlerts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "spamPatterns",
		name = "Spam Patterns",
		description = "Comma-separated list of message substrings to filter when 'Filter Spam' is on. "
			+ "Case-insensitive. Example: you can't reach that,nothing interesting happens",
		position = 6,
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
		position = 7,
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
		position = 8,
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
		position = 9,
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
		position = 10,
		section = "system"
	)
	default boolean systemShowTimestamp()
	{
		return false;
	}
}
