package com.chatoverlay;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Bootstrap class used to launch a full RuneLite client with the
 * Chat Overlay plugin pre-loaded for local development & testing.
 *
 * Run this class directly from IntelliJ, or use:
 *   ./gradlew run
 */
public class ChatOverlayPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ChatOverlayPlugin.class);
		RuneLite.main(args);
	}
}
