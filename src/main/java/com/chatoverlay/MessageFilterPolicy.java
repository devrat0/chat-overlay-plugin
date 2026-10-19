package com.chatoverlay;

import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;

/** Text visibility rules shared by every overlay; capture and duplicate handling stay separate. */
@Singleton
public class MessageFilterPolicy
{
	private final ChatOverlayConfig config;
	private final FilterMatcher matcher = new FilterMatcher();

	@Inject
	public MessageFilterPolicy(ChatOverlayConfig config)
	{
		this.config = config;
	}

	public boolean shouldShow(ChatLine line)
	{
		if (line.getCategory() != ChatCategory.SYSTEM)
		{
			return true;
		}

		String text = line.getPlainMessage().toLowerCase(Locale.ROOT).trim();
		ChatMessageType type = line.getChatMessageType();
		if (config.filterSystemConsole() && (type == ChatMessageType.CONSOLE || type == ChatMessageType.UNKNOWN))
		{
			return false;
		}

		// Saved custom patterns retain their original Game/system scope.
		if (config.filterSpamAlerts() && matcher.matches(config.spamPatterns(), text))
		{
			return false;
		}

		// Status rules must not match player chat, examines, friend activity, or broadcasts.
		if (!isGameStatus(type))
		{
			return true;
		}

		return !((config.filterInteractionSpam() && matcher.matchesInteraction(text))
			|| (config.filterSkillingSpam() && matcher.matchesSkilling(text))
			|| (config.filterCombatLootSpam() && matcher.matchesCombatLoot(text))
			|| (config.filterConsumablesSpam() && matcher.matchesConsumables(text)));
	}

	public boolean matches(String patterns, ChatLine line)
	{
		return matcher.matches(patterns, line.getPlainMessage().toLowerCase(Locale.ROOT).trim());
	}

	private boolean isGameStatus(ChatMessageType type)
	{
		return type == ChatMessageType.GAMEMESSAGE
			|| type == ChatMessageType.ENGINE
			|| type == ChatMessageType.SPAM
			|| type == ChatMessageType.CONSOLE
			|| type == ChatMessageType.UNKNOWN;
	}
}
