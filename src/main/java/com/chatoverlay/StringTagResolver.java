package com.chatoverlay;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expands message-local string definitions such as quest names before stripping markup. */
public final class StringTagResolver
{
	private static final Pattern STRING_TAG = Pattern.compile(
		"<(str_[a-z0-9_]+)(?:=([^<>]*))?>", Pattern.CASE_INSENSITIVE);

	private StringTagResolver() {}

	public static String resolve(String text)
	{
		if (text == null || text.isEmpty())
		{
			return text;
		}

		Matcher matcher = STRING_TAG.matcher(text);
		if (!matcher.find())
		{
			return text;
		}

		Map<String, String> values = new HashMap<>();
		StringBuffer resolved = new StringBuffer(text.length());
		do
		{
			String key = matcher.group(1).toLowerCase(Locale.ROOT);
			String value = matcher.group(2);
			String replacement = "";
			if (value != null)
			{
				values.put(key, value);
			}
			else
			{
				replacement = values.getOrDefault(key, "");
			}
			matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
		}
		while (matcher.find());
		matcher.appendTail(resolved);
		return resolved.toString();
	}
}
