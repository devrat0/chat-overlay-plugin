package com.chatoverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;

/** Game Chat visibility rules, optionally applied to game messages in Main Chat. */
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
		if (config.filterExamineMessages() && (type == ChatMessageType.ITEM_EXAMINE
			|| type == ChatMessageType.NPC_EXAMINE || type == ChatMessageType.OBJECT_EXAMINE))
		{
			return false;
		}

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

	/** Filters a view of chronological history without removing messages from other overlays. */
	public List<ChatLine> filterGameMessages(List<ChatLine> lines, boolean collapseGameChat)
	{
		List<ChatLine> visible = new ArrayList<>();
		Map<String, Long> lastShown = new HashMap<>();
		long cooldownMs = config.filterSpamAlerts() && !collapseGameChat
			? config.spamCooldownSeconds() * 1000L : 0L;
		for (ChatLine line : lines)
		{
			if (!shouldShow(line))
			{
				continue;
			}

			// Broadcasts and welcome messages have never used the duplicate cooldown.
			if (cooldownMs > 0 && line.getCategory() == ChatCategory.SYSTEM
				&& line.getChatMessageType() != ChatMessageType.BROADCAST
				&& line.getChatMessageType() != ChatMessageType.WELCOME)
			{
				String text = line.getPlainMessage().toLowerCase(Locale.ROOT).trim();
				Long previous = lastShown.get(text);
				if (previous != null && line.getTimestamp() - previous < cooldownMs)
				{
					continue;
				}
				lastShown.put(text, line.getTimestamp());
			}
			visible.add(line);
		}
		return visible;
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
