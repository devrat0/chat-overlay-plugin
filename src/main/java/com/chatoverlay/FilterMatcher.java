package com.chatoverlay;

/**
 * Matches comma-separated filter patterns against messages.
 *
 * <p>Per-pattern rules (case-insensitive):
 * <ul>
 *   <li>Pattern contains {@code *} anywhere — wildcard match: {@code *} matches any sequence of
 *       characters (including none). e.g. {@code spam*}, {@code *spam}, {@code *spam*}.</li>
 *   <li>Otherwise — substring match: the message must <em>contain</em> the pattern.</li>
 * </ul>
 */
public class FilterMatcher
{
	private static final String[] INTERACTION_SPAM_PATTERNS = {
		"you can't reach that",
		"i can't reach that",
		"nothing interesting happens",
		"you can't do that right now",
		"please finish what you're doing",
		"you need to be closer",
		"you can't use that here"
	};

	private static final String[] SKILLING_SPAM_PATTERNS = {
		"you swing your axe",
		"you get some * logs",
		"you get some * ore",
		"you swing your pick",
		"you manage to mine",
		"you catch a",
		"you net a",
		"you harpoon",
		"you successfully cook",
		"you accidentally burn",
		"the fire catches",
		"you plant the seed",
		"you treat the patch",
		"you bind the",
		"you craft the"
	};

	private static final String[] COMBAT_LOOT_SPAM_PATTERNS = {
		"your ring of wealth",
		"you find some coins",
		"you retrieve your ammo",
		"you drop the",
		"you construct a"
	};

	private static final String[] CONSUMABLES_SPAM_PATTERNS = {
		"you eat the",
		"you drink the",
		"it heals some health",
		"you restore some",
		"you drink some of your"
	};

	public boolean matchesInteraction(String lowerMessage)
	{
		return matchesAny(INTERACTION_SPAM_PATTERNS, lowerMessage);
	}

	public boolean matchesSkilling(String lowerMessage)
	{
		return matchesAny(SKILLING_SPAM_PATTERNS, lowerMessage);
	}

	public boolean matchesCombatLoot(String lowerMessage)
	{
		return matchesAny(COMBAT_LOOT_SPAM_PATTERNS, lowerMessage);
	}

	public boolean matchesConsumables(String lowerMessage)
	{
		return matchesAny(CONSUMABLES_SPAM_PATTERNS, lowerMessage);
	}

	private boolean matchesAny(String[] patterns, String lowerMessage)
	{
		for (String pattern : patterns)
		{
			if (pattern.contains("*"))
			{
				if (wildcardMatches(pattern, lowerMessage))
				{
					return true;
				}
			}
			else
			{
				if (lowerMessage.contains(pattern))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns true if {@code lowerMessage} matches any pattern in {@code rawPatterns}.
	 *
	 * @param rawPatterns  comma-separated pattern string from config
	 * @param lowerMessage the plain message text, already lowercased by the caller
	 */
	public boolean matches(String rawPatterns, String lowerMessage)
	{
		if (rawPatterns == null || rawPatterns.trim().isEmpty())
		{
			return false;
		}
		for (String part : rawPatterns.split(","))
		{
			String pattern = part.trim().toLowerCase();
			if (pattern.isEmpty())
			{
				continue;
			}
			if (pattern.contains("*"))
			{
				if (wildcardMatches(pattern, lowerMessage))
				{
					return true;
				}
			}
			else
			{
				if (lowerMessage.contains(pattern))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Matches {@code text} against a wildcard {@code pattern} where {@code *} matches any sequence.
	 * Both arguments must already be lowercased.
	 */
	private static boolean wildcardMatches(String pattern, String text)
	{
		String[] parts = pattern.split("\\*", -1);
		int pos = 0;
		for (int i = 0; i < parts.length; i++)
		{
			String part = parts[i];
			if (i == 0)
			{
				// First segment must match at the start (no leading wildcard)
				if (!text.startsWith(part))
				{
					return false;
				}
				pos = part.length();
			}
			else if (i == parts.length - 1)
			{
				// Last segment must match at the end
				if (part.isEmpty())
				{
					// trailing * — anything goes
					break;
				}
				if (!text.endsWith(part) || text.length() - part.length() < pos)
				{
					return false;
				}
			}
			else
			{
				// Middle segment: find next occurrence at or after pos
				int idx = text.indexOf(part, pos);
				if (idx < 0)
				{
					return false;
				}
				pos = idx + part.length();
			}
		}
		return true;
	}
}
