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
