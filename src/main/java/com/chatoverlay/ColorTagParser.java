package com.chatoverlay;

import java.util.regex.Pattern;

/**
 * A utility class for handling OSRS/RuneLite color tags.
 *
 * <p>The primary function of this class is to {@link #stripTags(String)},
 * which removes all {@code <col=...>} and other tags from a string,
 * returning only the plain text.</p>
 */
public class ColorTagParser
{
	private static final Pattern ANY_TAG = Pattern.compile("<[^>]+>");

	private ColorTagParser() {}

	/** Strip all tags and return plain text. */
	public static String stripTags(String rawText)
	{
		if (rawText == null)
		{
			return "";
		}
		return ANY_TAG.matcher(rawText).replaceAll("");
	}
}
