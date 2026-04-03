package com.chatoverlay;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.clan.ClanChannel;

/**
 * Resolves display names for the active clan channel, guest clan channel,
 * and friends chat channel, with safe fallback strings.
 */
@Singleton
public class ChannelNameResolver
{
	private final Client client;

	@Inject
	public ChannelNameResolver(Client client)
	{
		this.client = client;
	}

	public String getClanChannelName()
	{
		try
		{
			ClanChannel cc = client.getClanChannel();
			return cc != null ? cc.getName() : "Clan";
		}
		catch (Exception e)
		{
			return "Clan";
		}
	}

	public String getGuestClanChannelName()
	{
		try
		{
			ClanChannel cc = client.getGuestClanChannel();
			return cc != null ? cc.getName() : "Guest Clan";
		}
		catch (Exception e)
		{
			return "Guest Clan";
		}
	}

	public String getFriendsChatName()
	{
		try
		{
			FriendsChatManager fcm = client.getFriendsChatManager();
			return fcm != null ? fcm.getOwner() : "FC";
		}
		catch (Exception e)
		{
			return "FC";
		}
	}
}
