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
		position = 0,
		closedByDefault = true
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Keyword Highlighting",
		description = "Configure custom keyword and boss alert highlighting across all overlays",
		position = 1,
		closedByDefault = true
	)
	String highlightSection = "highlighting";

	@ConfigSection(
		name = "Main Chat",
		description = "Settings for the main chat overlay (bottom-left)",
		position = 2,
		closedByDefault = true
	)
	String publicClanSection = "publicClan";

	@ConfigSection(
		name = "Private Chat",
		description = "Settings for the private chat overlay (above public/clan)",
		position = 3,
		closedByDefault = true
	)
	String privateSection = "private";

	@ConfigSection(
		name = "Clan Chat",
		description = "Settings for the dedicated clan chat overlay",
		position = 4,
		closedByDefault = true
	)
	String clanSection = "clan";

	@ConfigSection(
		name = "Game Chat",
		description = "Settings for game/system message alerts",
		position = 5,
		closedByDefault = true
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
		keyName = "bubbleRoundness",
		name = "Bubble Roundness",
		description = "Corner radius (roundness) of the chat bubbles in pixels",
		position = 3,
		section = "general"
	)
	@Range(min = 0, max = 30)
	default int bubbleRoundness()
	{
		return 12;
	}

	@ConfigItem(
		keyName = "fontType",
		name = "Font",
		description = "Font used for all chat overlays",
		position = 4,
		section = "general"
	)
	default FontType fontType()
	{
		return FontType.RUNESCAPE;
	}

	@ConfigItem(
		keyName = "customFontName",
		name = "Font Name (if custom font is chosen in the dropdown above)",
		description = "The name of the system font to use when 'Custom / System Font' is selected",
		position = 5,
		section = "general"
	)
	default String customFontName()
	{
		return "Arial";
	}

	@ConfigItem(
		keyName = "fontSize",
		name = "Font Size",
		description = "Font size for all chat overlays. Note: RuneScape pixel fonts will automatically snap to the nearest integer scale (16, 32, 48px) to maintain pixel-perfect crispness, while system fonts scale continuously.",
		position = 6,
		section = "general"
	)
	@Range(min = 8, max = 48)
	default int fontSize()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "showChatboxMessage",
		name = "Show Chatbox Message",
		description = "Display a bubble under the main chat overlay showing what you are currently typing",
		position = 7,
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
		position = 8,
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
		position = 9,
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
		position = 10,
		section = "general"
	)
	default boolean showPlayerIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "timestampFormat",
		name = "Timestamp Format",
		description = "Format style for chat message timestamps",
		position = 11,
		section = "general"
	)
	default TimestampFormat timestampFormat()
	{
		return TimestampFormat.HH_MM;
	}

	// ──────────────────────────────────────────────
	//  KEYWORD HIGHLIGHTING
	// ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "highlightKeywords",
		name = "Highlight Keywords",
		description = "Enable custom visual highlights for specific keywords in all overlays",
		position = 0,
		section = "highlighting"
	)
	default boolean highlightKeywords()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightKeywordsList",
		name = "Highlight Words",
		description = "Comma-separated list of keywords to highlight across all overlays. Case-insensitive.",
		position = 1,
		section = "highlighting"
	)
	default String highlightKeywordsList()
	{
		return "dd,pot up,spec";
	}

	@ConfigItem(
		keyName = "highlightCoX",
		name = "Highlight CoX Alerts",
		description = "Highlight Chambers of Xeric (CoX) boss and special attack messages",
		position = 2,
		section = "highlighting"
	)
	default boolean highlightCoX()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightToB",
		name = "Highlight ToB Alerts",
		description = "Highlight Theatre of Blood (ToB) boss and special attack messages",
		position = 3,
		section = "highlighting"
	)
	default boolean highlightToB()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightToA",
		name = "Highlight ToA Alerts",
		description = "Highlight Tombs of Amascut (ToA) boss and special attack messages",
		position = 4,
		section = "highlighting"
	)
	default boolean highlightToA()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightBgColor",
		name = "Highlight BG Color",
		description = "Background color for highlighted chat bubbles",
		position = 5,
		section = "highlighting"
	)
	default Color highlightBgColor()
	{
		return new Color(80, 15, 15, 220);
	}

	@ConfigItem(
		keyName = "highlightShowBorder",
		name = "Highlight Border",
		description = "Always show a border around highlighted chat bubbles",
		position = 6,
		section = "highlighting"
	)
	default boolean highlightShowBorder()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightBorderColor",
		name = "Highlight Border Color",
		description = "Border color for highlighted chat bubbles",
		position = 7,
		section = "highlighting"
	)
	default Color highlightBorderColor()
	{
		return new Color(220, 50, 50, 255);
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
		keyName = "showGameMessagesInMain",
		name = "Show Game Chat",
		description = "Also route game/system messages (GAMEMESSAGE, ENGINE, BROADCAST, WELCOME) into this main chat overlay. "
			+ "Off by default — the dedicated Game Chat overlay already handles them.",
		position = 4,
		section = "publicClan"
	)
	default boolean showGameMessagesInMain()
	{
		return false;
	}

	@ConfigItem(
		keyName = "publicHideWhenChatboxOpen",
		name = "Hide When Chatbox Visible",
		description = "Hide this overlay while the in-game chatbox is visible on screen",
		position = 5,
		section = "publicClan"
	)
	default boolean publicHideWhenChatboxOpen()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publicOverlayWidth",
		name = "Overlay Width",
		description = "Width of the public/clan chat overlay in pixels",
		position = 6,
		section = "publicClan"
	)
	@Range(min = 200, max = 800)
	default int publicOverlayWidth()
	{
		return 510;
	}

	@ConfigItem(
		keyName = "publicOverlayHeight",
		name = "Overlay Height",
		description = "Height of the public/clan chat overlay in pixels (used in Bottom to Top mode)",
		position = 7,
		section = "publicClan"
	)
	@Range(min = 100, max = 800)
	default int publicOverlayHeight()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "publicLayoutMode",
		name = "Layout Mode",
		description = "Stack messages from top to bottom or bottom to top",
		position = 8,
		section = "publicClan"
	)
	default LayoutMode publicLayoutMode()
	{
		return LayoutMode.BOTTOM_TO_TOP;
	}

	@ConfigItem(
		keyName = "publicMaxMessages",
		name = "Max Messages",
		description = "Maximum number of public/clan messages shown in the overlay",
		position = 9,
		section = "publicClan"
	)
	@Range(min = 1, max = 50)
	default int publicMaxMessages()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "publicWordWrap",
		name = "Word Wrap",
		description = "When on, long messages wrap across multiple lines inside the bubble. "
			+ "When off, messages are truncated to a single line with an ellipsis.",
		position = 10,
		section = "publicClan"
	)
	default boolean publicWordWrap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publicMessageDuration",
		name = "Message Duration (seconds)",
		description = "How long each message stays fully visible before disappearing. Set to 0 to keep messages indefinitely.",
		position = 11,
		section = "publicClan"
	)
	@Range(min = 0, max = 300)
	default int publicMessageDuration()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "publicFadeMessages",
		name = "Enable Fading Messages",
		description = "When on, messages gradually fade out before disappearing. "
			+ "When off, messages stay fully visible until they expire.",
		position = 12,
		section = "publicClan"
	)
	default boolean publicFadeMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publicBgEnabled",
		name = "Show Background",
		description = "Toggle background on/off for a cleaner look",
		position = 13,
		section = "publicClan"
	)
	default boolean publicBgEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publicBgColor",
		name = "Background Color",
		description = "Background color for the public/clan chat overlay",
		position = 14,
		section = "publicClan"
	)
	@Alpha
	default Color publicBgColor()
	{
		return new Color(30, 30, 30, 200);
	}

	@ConfigItem(
		keyName = "publicShowBubbleBorder",
		name = "Show Bubble Border",
		description = "Draw a 1px rounded border around each chat bubble",
		position = 15,
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
		position = 16,
		section = "publicClan"
	)
	default Color publicBubbleBorderColor()
	{
		return new Color(52, 52, 52, 180);
	}

	@ConfigItem(
		keyName = "publicShowTimestamp",
		name = "Show Timestamp",
		description = "Prefix each message with its timestamp",
		position = 17,
		section = "publicClan"
	)
	default boolean publicShowTimestamp()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publicDisableKeywordHighlight",
		name = "Disable Keyword Highlight",
		description = "Disable keyword and boss alert highlighting in the main chat overlay",
		position = 18,
		section = "publicClan"
	)
	default boolean publicDisableKeywordHighlight()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "publicTimestampColor",
		name = "Timestamp Color",
		description = "Custom color override for timestamps in the main chat overlay",
		position = 19,
		section = "publicClan"
	)
	default Color publicTimestampColor()
	{
		return null;
	}

	@Alpha
	@ConfigItem(
		keyName = "publicClanLabelColor",
		name = "Clan Label Color",
		description = "Custom color override for clan chat labels in the main chat overlay",
		position = 20,
		section = "publicClan"
	)
	default Color publicClanLabelColor()
	{
		return null;
	}

	@Alpha
	@ConfigItem(
		keyName = "publicUsernameColor",
		name = "Username Color",
		description = "Custom color override for usernames in the main chat overlay",
		position = 21,
		section = "publicClan"
	)
	default Color publicUsernameColor()
	{
		return null;
	}

	@Alpha
	@ConfigItem(
		keyName = "publicTextColor",
		name = "Text Color",
		description = "Custom color override for message text in the main chat overlay",
		position = 22,
		section = "publicClan"
	)
	default Color publicTextColor()
	{
		return null;
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
		keyName = "privateHideWhenChatboxOpen",
		name = "Hide When Chatbox Visible",
		description = "Hide this overlay while the in-game chatbox is visible on screen",
		position = 1,
		section = "private"
	)
	default boolean privateHideWhenChatboxOpen()
	{
		return true;
	}

	@ConfigItem(
		keyName = "privateOverlayWidth",
		name = "Overlay Width",
		description = "Width of the private chat overlay in pixels",
		position = 2,
		section = "private"
	)
	@Range(min = 200, max = 800)
	default int privateOverlayWidth()
	{
		return 510;
	}

	@ConfigItem(
		keyName = "privateOverlayHeight",
		name = "Overlay Height",
		description = "Height of the private chat overlay in pixels (used in Bottom to Top mode)",
		position = 3,
		section = "private"
	)
	@Range(min = 100, max = 800)
	default int privateOverlayHeight()
	{
		return 200;
	}

	@ConfigItem(
		keyName = "privateLayoutMode",
		name = "Layout Mode",
		description = "Stack messages from top to bottom or bottom to top",
		position = 4,
		section = "private"
	)
	default LayoutMode privateLayoutMode()
	{
		return LayoutMode.TOP_TO_BOTTOM;
	}

	@ConfigItem(
		keyName = "privateMaxMessages",
		name = "Max Messages",
		description = "Maximum number of private messages shown",
		position = 5,
		section = "private"
	)
	@Range(min = 1, max = 20)
	default int privateMaxMessages()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "privateWordWrap",
		name = "Word Wrap",
		description = "When on, long messages wrap across multiple lines inside the bubble. "
			+ "When off, messages are truncated to a single line with an ellipsis.",
		position = 6,
		section = "private"
	)
	default boolean privateWordWrap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "privateMessageDuration",
		name = "Message Duration (seconds)",
		description = "How long each private message stays fully visible before disappearing. Set to 0 to keep messages indefinitely.",
		position = 7,
		section = "private"
	)
	@Range(min = 0, max = 300)
	default int privateMessageDuration()
	{
		return 120;
	}

	@ConfigItem(
		keyName = "privateFadeMessages",
		name = "Enable Fading Messages",
		description = "When on, messages gradually fade out before disappearing. "
			+ "When off, messages stay fully visible until they expire.",
		position = 8,
		section = "private"
	)
	default boolean privateFadeMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "privateBgEnabled",
		name = "Show Background",
		description = "Toggle background on/off",
		position = 9,
		section = "private"
	)
	default boolean privateBgEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "privateBgColor",
		name = "Background Color",
		description = "Background color for the private chat overlay",
		position = 10,
		section = "private"
	)
	@Alpha
	default Color privateBgColor()
	{
		return new Color(0, 0, 0, 255);
	}

	@ConfigItem(
		keyName = "privateShowBubbleBorder",
		name = "Show Bubble Border",
		description = "Draw a 1px rounded border around each chat bubble",
		position = 11,
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
		position = 12,
		section = "private"
	)
	default Color privateBubbleBorderColor()
	{
		return new Color(52, 52, 52, 180);
	}

	@ConfigItem(
		keyName = "privateShowTimestamp",
		name = "Show Timestamp",
		description = "Prefix each message with its timestamp",
		position = 13,
		section = "private"
	)
	default boolean privateShowTimestamp()
	{
		return true;
	}

	@ConfigItem(
		keyName = "privateDisableKeywordHighlight",
		name = "Disable Keyword Highlight",
		description = "Disable keyword and boss alert highlighting in the private chat overlay",
		position = 14,
		section = "private"
	)
	default boolean privateDisableKeywordHighlight()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "privateTimestampColor",
		name = "Timestamp Color",
		description = "Custom color override for timestamps in the private chat overlay",
		position = 15,
		section = "private"
	)
	default Color privateTimestampColor()
	{
		return null;
	}

	@Alpha
	@ConfigItem(
		keyName = "privateUsernameColor",
		name = "Username Color",
		description = "Custom color override for usernames in the private chat overlay",
		position = 16,
		section = "private"
	)
	default Color privateUsernameColor()
	{
		return null;
	}

	@Alpha
	@ConfigItem(
		keyName = "privateTextColor",
		name = "Text Color",
		description = "Custom color override for message text in the private chat overlay",
		position = 17,
		section = "private"
	)
	default Color privateTextColor()
	{
		return null;
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
		keyName = "clanHideWhenChatboxOpen",
		name = "Hide When Chatbox Visible",
		description = "Hide this overlay while the in-game chatbox is visible on screen",
		position = 5,
		section = "clan"
	)
	default boolean clanHideWhenChatboxOpen()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clanOverlayWidth",
		name = "Overlay Width",
		description = "Width of the dedicated clan chat overlay in pixels",
		position = 6,
		section = "clan"
	)
	@Range(min = 200, max = 800)
	default int clanOverlayWidth()
	{
		return 510;
	}

	@ConfigItem(
		keyName = "clanOverlayHeight",
		name = "Overlay Height",
		description = "Height of the dedicated clan chat overlay in pixels (used in Bottom to Top mode)",
		position = 7,
		section = "clan"
	)
	@Range(min = 100, max = 800)
	default int clanOverlayHeight()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "clanLayoutMode",
		name = "Layout Mode",
		description = "Stack messages from top to bottom or bottom to top",
		position = 8,
		section = "clan"
	)
	default LayoutMode clanLayoutMode()
	{
		return LayoutMode.BOTTOM_TO_TOP;
	}

	@ConfigItem(
		keyName = "clanMaxMessages",
		name = "Max Messages",
		description = "Maximum number of messages shown in the clan chat overlay",
		position = 9,
		section = "clan"
	)
	@Range(min = 1, max = 50)
	default int clanMaxMessages()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "clanMessageDuration",
		name = "Message Duration (seconds)",
		description = "How long each message stays fully visible. Set to 0 to keep indefinitely.",
		position = 10,
		section = "clan"
	)
	@Range(min = 0, max = 300)
	default int clanMessageDuration()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "clanBgEnabled",
		name = "Show Background",
		description = "Toggle background on/off",
		position = 11,
		section = "clan"
	)
	default boolean clanBgEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clanBgColor",
		name = "Background Color",
		description = "Background color for the dedicated clan chat overlay",
		position = 12,
		section = "clan"
	)
	@Alpha
	default Color clanBgColor()
	{
		return new Color(30, 30, 30, 200);
	}

	@ConfigItem(
		keyName = "clanShowBubbleBorder",
		name = "Show Bubble Border",
		description = "Draw a 1px rounded border around each chat bubble",
		position = 13,
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
		position = 14,
		section = "clan"
	)
	default Color clanBubbleBorderColor()
	{
		return new Color(52, 52, 52, 180);
	}

	@ConfigItem(
		keyName = "clanShowTimestamp",
		name = "Show Timestamp",
		description = "Prefix each message with its timestamp",
		position = 15,
		section = "clan"
	)
	default boolean clanShowTimestamp()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clanDisableKeywordHighlight",
		name = "Disable Keyword Highlight",
		description = "Disable keyword and boss alert highlighting in the dedicated clan chat overlay",
		position = 16,
		section = "clan"
	)
	default boolean clanDisableKeywordHighlight()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "clanTimestampColor",
		name = "Timestamp Color",
		description = "Custom color override for timestamps in the dedicated clan chat overlay",
		position = 17,
		section = "clan"
	)
	default Color clanTimestampColor()
	{
		return null;
	}

	@Alpha
	@ConfigItem(
		keyName = "clanClanLabelColor",
		name = "Clan Label Color",
		description = "Custom color override for clan chat labels in the dedicated clan chat overlay",
		position = 18,
		section = "clan"
	)
	default Color clanClanLabelColor()
	{
		return null;
	}

	@Alpha
	@ConfigItem(
		keyName = "clanUsernameColor",
		name = "Username Color",
		description = "Custom color override for usernames in the dedicated clan chat overlay",
		position = 19,
		section = "clan"
	)
	default Color clanUsernameColor()
	{
		return null;
	}

	@Alpha
	@ConfigItem(
		keyName = "clanTextColor",
		name = "Text Color",
		description = "Custom color override for message text in the dedicated clan chat overlay",
		position = 20,
		section = "clan"
	)
	default Color clanTextColor()
	{
		return null;
	}

	// ──────────────────────────────────────────────
	//  SYSTEM ALERTS  (near player or free overlay)
	// ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "showSystemAlerts",
		name = "Show Game Chat",
		description = "Display game/system messages in the overlay",
		position = 0,
		section = "system"
	)
	default boolean showSystemAlerts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "systemAlertMode",
		name = "Overlay Mode",
		description = "Pinned to Player: bubbles float above your character. "
			+ "Free Overlay: a panel you can drag anywhere on screen.",
		position = 1,
		section = "system"
	)
	default GameOverlayMode systemAlertMode()
	{
		return GameOverlayMode.PINNED_TO_PLAYER;
	}

	@ConfigItem(
		keyName = "systemPlayerOffset",
		name = "Vertical Offset (Pinned)",
		description = "Vertical offset in 3D units above/below the player's head when Pinned to Player. "
			+ "Positive values move it higher, negative values move it lower.",
		position = 2,
		section = "system"
	)
	@Range(min = -500, max = 500)
	default int systemPlayerOffset()
	{
		return 120;
	}

	@ConfigItem(
		keyName = "systemHideWhenChatboxOpen",
		name = "Hide When Chatbox Visible",
		description = "Hide this overlay while the in-game chatbox is visible on screen",
		position = 3,
		section = "system"
	)
	default boolean systemHideWhenChatboxOpen()
	{
		return false;
	}

	@ConfigItem(
		keyName = "systemLayoutMode",
		name = "Layout Mode",
		description = "Stack messages from top to bottom or bottom to top",
		position = 4,
		section = "system"
	)
	default LayoutMode systemLayoutMode()
	{
		return LayoutMode.BOTTOM_TO_TOP;
	}

	@ConfigItem(
		keyName = "systemMaxAlerts",
		name = "Max Visible Messages",
		description = "Maximum number of game messages shown at once (oldest removed)",
		position = 5,
		section = "system"
	)
	@Range(min = 1, max = 8)
	default int systemMaxAlerts()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "systemWordWrap",
		name = "Word Wrap",
		description = "When on, long messages wrap across multiple lines inside the bubble. "
			+ "When off, messages are truncated to a single line with an ellipsis.",
		position = 6,
		section = "system"
	)
	default boolean systemWordWrap()
	{
		return false;
	}

	@ConfigItem(
		keyName = "systemAlertDuration",
		name = "Message Duration (seconds)",
		description = "How long each game message stays visible",
		position = 7,
		section = "system"
	)
	@Range(min = 1, max = 15)
	default int systemAlertDuration()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "systemFadeMessages",
		name = "Enable Fading Messages",
		description = "When on, messages gradually fade out before disappearing. "
			+ "When off, messages stay fully visible until they expire.",
		position = 8,
		section = "system"
	)
	default boolean systemFadeMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "systemBgColor",
		name = "Background Color",
		description = "Background color for game chat bubbles",
		position = 9,
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
		position = 10,
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
		position = 11,
		section = "system"
	)
	default Color systemBubbleBorderColor()
	{
		return new Color(52, 52, 52, 180);
	}

	@ConfigItem(
		keyName = "systemShowTimestamp",
		name = "Show Timestamp",
		description = "Prefix each alert with its timestamp",
		position = 12,
		section = "system"
	)
	default boolean systemShowTimestamp()
	{
		return false;
	}

	@ConfigItem(
		keyName = "systemDisableKeywordHighlight",
		name = "Disable Keyword Highlight",
		description = "Disable keyword and boss alert highlighting in the game chat overlay",
		position = 13,
		section = "system"
	)
	default boolean systemDisableKeywordHighlight()
	{
		return false;
	}

	@ConfigItem(
		keyName = "filterSpamAlerts",
		name = "Filter Spam",
		description = "Filter out repetitive/spammy system messages using the patterns below",
		position = 14,
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
		position = 15,
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
		position = 16,
		section = "system"
	)
	default boolean filterSkillingSpam()
	{
		return true;
	}

	@ConfigItem(
		keyName = "filterCombatLootSpam",
		name = "Filter Combat/Loot Spam",
		description = "Filter out minor combat or loot messages (e.g. Ring of Wealth coins, retrieving ammo)",
		position = 17,
		section = "system"
	)
	default boolean filterCombatLootSpam()
	{
		return true;
	}

	@ConfigItem(
		keyName = "filterConsumablesSpam",
		name = "Filter Consumables",
		description = "Filter out messages about eating food or drinking potions (e.g. 'You eat the shark', 'It heals some health')",
		position = 18,
		section = "system"
	)
	default boolean filterConsumablesSpam()
	{
		return true;
	}

	@ConfigItem(
		keyName = "spamPatterns",
		name = "Spam Patterns",
		description = "Comma-separated list of patterns to filter when 'Filter Spam' is on. "
			+ "Case-insensitive. If a pattern contains * it is treated as a wildcard (e.g. you*reach matches anything starting with 'you' and containing 'reach'). "
			+ "Otherwise the pattern is a plain substring match.",
		position = 19,
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
		position = 20,
		section = "system"
	)
	@Range(min = 0, max = 30)
	default int spamCooldownSeconds()
	{
		return 3;
	}

	@Alpha
	@ConfigItem(
		keyName = "systemTimestampColor",
		name = "Timestamp Color",
		description = "Custom color override for timestamps in the game chat overlay",
		position = 21,
		section = "system"
	)
	default Color systemTimestampColor()
	{
		return null;
	}

	@Alpha
	@ConfigItem(
		keyName = "systemUsernameColor",
		name = "Username Color",
		description = "Custom color override for usernames in the game chat overlay",
		position = 22,
		section = "system"
	)
	default Color systemUsernameColor()
	{
		return null;
	}

	@Alpha
	@ConfigItem(
		keyName = "systemTextColor",
		name = "Text Color",
		description = "Custom color override for message text in the game chat overlay",
		position = 23,
		section = "system"
	)
	default Color systemTextColor()
	{
		return null;
	}
}

