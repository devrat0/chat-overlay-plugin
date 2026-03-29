package com.chatoverlay;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import net.runelite.client.config.ChatColorConfig;

/**
 * Stateful builder that converts raw OSRS chat markup into a flat list of
 * {@link ColorSegment}s ready for rendering.
 */
public class ChatLineBuilder
{
	private static final Pattern TAG_PATTERN = Pattern.compile(
		"<col=([0-9a-fA-F]{3,6})>"   // group 1: hex color
		+ "|</col>"                    // color end
		+ "|<col(NORMAL|HIGHLIGHT)>"   // group 2: named color
		+ "|<img=\\d+>"               // icon (stripped)
		+ "|<br>"                      // line break → space
		+ "|<lt>"                      // entity: <
		+ "|<gt>"                      // entity: >
		+ "|<[^>]+>",                  // any other tag (stripped)
		Pattern.CASE_INSENSITIVE);

	@Getter
	private final List<ColorSegment> segments = new ArrayList<>();
	private final Color baseColor;
	private final ChatColorConfig chatColorConfig;

	public ChatLineBuilder(Color baseColor)
	{
		this(baseColor, null);
	}

	public ChatLineBuilder(Color baseColor, ChatColorConfig chatColorConfig)
	{
		this.baseColor = baseColor;
		this.chatColorConfig = chatColorConfig;
	}

	/**
	 * Appends a string to the line
	 */
	public void append(String rawText)
	{
		append(rawText, this.baseColor);
	}

	/**
	 * Appends a string to the line
	 */
	public void append(String rawText, Color defaultColor)
	{
		if (rawText == null || rawText.isEmpty())
		{
			return;
		}

		Matcher m = TAG_PATTERN.matcher(rawText);
		Color currentColor = defaultColor;
		int lastEnd = 0;

		while (m.find())
		{
			// Flush any plain text before this tag
			if (m.start() > lastEnd)
			{
				segments.add(new ColorSegment(
					rawText.substring(lastEnd, m.start()), currentColor));
			}

			String lowerMatch = m.group(0).toLowerCase();

			if (m.group(1) != null)
			{
				currentColor = hexToColor(m.group(1), defaultColor);
			}
			else if (lowerMatch.equals("</col>"))
			{
				currentColor = defaultColor;
			}
			else if (m.group(2) != null)
			{
				String name = m.group(2).toUpperCase();
				if ("NORMAL".equals(name))
				{
					currentColor = defaultColor;
				}
				else if ("HIGHLIGHT".equals(name))
				{
					Color highlight = null;
					if (chatColorConfig != null)
					{
						try
						{
							highlight = chatColorConfig.transparentExamineHighlight();
						}
						catch (Exception ignored) {}
					}
					currentColor = highlight != null ? highlight : defaultColor;
				}
			}
			else if (lowerMatch.equals("<lt>"))
			{
				segments.add(new ColorSegment("<", currentColor));
			}
			else if (lowerMatch.equals("<gt>"))
			{
				segments.add(new ColorSegment(">", currentColor));
			}
			else if (lowerMatch.equals("<br>"))
			{
				segments.add(new ColorSegment(" ", currentColor));
			}
			lastEnd = m.end();
		}

		// Flush any trailing plain text after the last tag
		if (lastEnd < rawText.length())
		{
			segments.add(new ColorSegment(
				rawText.substring(lastEnd), currentColor));
		}
	}

	/** Returns the total character count of the plain text in the line. */
	public int length()
	{
		return segments.stream().mapToInt(s -> s.getText().length()).sum();
	}

	/** Returns the plain text of the line by concatenating all segments. */
	public String toPlainString()
	{
		StringBuilder sb = new StringBuilder();
		for (ColorSegment segment : segments)
		{
			sb.append(segment.getText());
		}
		return sb.toString();
	}

	/** Resets the builder to an empty line. */
	public void clear()
	{
		segments.clear();
	}

	private static Color hexToColor(String hex, Color fallback)
	{
		if (hex == null)
		{
			return fallback;
		}
		try
		{
			if (hex.length() == 3)
			{
				char r = hex.charAt(0);
				char g = hex.charAt(1);
				char b = hex.charAt(2);
				hex = "" + r + r + g + g + b + b;
			}
			return Color.decode("#" + hex);
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}
}
