package com.chatoverlay;

import org.junit.Test;
import static org.junit.Assert.*;

import net.runelite.client.game.ChatIconManager;
import net.runelite.api.FriendsChatRank;

public class ClanIconTest
{
	@Test
	public void testChatIconManagerAvailability()
	{
		assertNotNull(ChatIconManager.class);
	}
}
