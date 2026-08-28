package com.chatoverlay;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts color macros emitted by the game into the {@code <col=RRGGBB>}
 * markup understood by {@link ChatLineBuilder}.
 */
public class ColorMacroResolver
{
	private static final Pattern MACRO_PATTERN = Pattern.compile(
		"@(mes_[a-z0-9_]+)@",
		Pattern.CASE_INSENSITIVE);
	private static final Map<String, MacroColor> MACRO_COLORS = Map.ofEntries(
		Map.entry("mes_hl_red", new MacroColor("e00a19", "ff3045")),
		Map.entry("mes_hl_gre", new MacroColor("06600c", "229628")),
		Map.entry("mes_hl_pur", new MacroColor("6800bf", "a53fff")),
		Map.entry("mes_hl_mag", new MacroColor("ef0083", "ff289d")),
		Map.entry("mes_hl_blu", new MacroColor("0000b2", "3366ff")),
		Map.entry("mes_hl_cya", new MacroColor("00ffff", "00e6e6")),
		Map.entry("mes_hl_ora", new MacroColor("b25000", "ff8e32"))
	);

	private ColorMacroResolver() {}

	/**
	 * Resolves known color macros. Unknown macros are preserved as text.
	 */
	public static String resolve(String rawText, boolean isTransparent)
	{
		if (rawText == null || rawText.isEmpty())
		{
			return rawText;
		}

		Matcher matcher = MACRO_PATTERN.matcher(rawText);
		StringBuffer resolved = new StringBuffer(rawText.length());
		while (matcher.find())
		{
			String name = matcher.group(1).toLowerCase();
			MacroColor color = MACRO_COLORS.get(name);
			String replacement = matcher.group(0);
			if (color != null)
			{
				String colorCode = isTransparent ? color.getTransparent() : color.getOpaque();
				replacement = "<col=" + colorCode + ">";
			}
			matcher.appendReplacement(resolved, replacement);
		}
		matcher.appendTail(resolved);
		return resolved.toString();
	}
}
