package com.chatoverlay;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Manages chat message queues for each overlay type, handling max-size limits
 * and time-based expiration for system alerts.
 */
public class ChatMessageManager
{
	private final LinkedList<ChatLine> publicClanMessages = new LinkedList<>();
	private final LinkedList<ChatLine> privateMessages = new LinkedList<>();
	private final LinkedList<ChatLine> systemMessages = new LinkedList<>();

	/**
	 * Spam filter: tracks recently seen system messages to suppress duplicates.
	 * Key = lowercase message text, value = timestamp of last occurrence.
	 */
	private final Map<String, Long> recentSystemMessages = new LinkedHashMap<String, Long>()
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Long> eldest)
		{
			return size() > 50;
		}
	};


	public void addPublicClanMessage(ChatLine line, int maxMessages)
	{
		synchronized (publicClanMessages)
		{
			publicClanMessages.addLast(line);
			while (publicClanMessages.size() > maxMessages)
			{
				publicClanMessages.removeFirst();
			}
		}
	}

	public void addPrivateMessage(ChatLine line, int maxMessages)
	{
		synchronized (privateMessages)
		{
			privateMessages.addLast(line);
			while (privateMessages.size() > maxMessages)
			{
				privateMessages.removeFirst();
			}
		}
	}

	public boolean addSystemMessage(ChatLine line, int maxAlerts, boolean filterSpam, long spamCooldownMs)
	{
		if (filterSpam)
		{
			String lower = line.getPlainMessage().toLowerCase().trim();
			Long lastSeen = recentSystemMessages.get(lower);
			long now = System.currentTimeMillis();
			if (lastSeen != null && (now - lastSeen) < spamCooldownMs)
			{
				return false;
			}
			recentSystemMessages.put(lower, now);
		}

		synchronized (systemMessages)
		{
			systemMessages.addLast(line);
			while (systemMessages.size() > maxAlerts)
			{
				systemMessages.removeFirst();
			}
		}
		return true;
	}

	public List<ChatLine> getPublicClanMessages()
	{
		synchronized (publicClanMessages)
		{
			return new ArrayList<>(publicClanMessages);
		}
	}

	public List<ChatLine> getPrivateMessages()
	{
		synchronized (privateMessages)
		{
			return new ArrayList<>(privateMessages);
		}
	}

	public List<ChatLine> getSystemMessages()
	{
		synchronized (systemMessages)
		{
			return new ArrayList<>(systemMessages);
		}
	}

	public void clearPublicClanMessages()
	{
		synchronized (publicClanMessages)
		{
			publicClanMessages.clear();
		}
	}

	public void clearPrivateMessages()
	{
		synchronized (privateMessages)
		{
			privateMessages.clear();
		}
	}

	public void clearSystemMessages()
	{
		synchronized (systemMessages)
		{
			systemMessages.clear();
		}
		recentSystemMessages.clear();
	}

	public void clearAll()
	{
		clearPublicClanMessages();
		clearPrivateMessages();
		clearSystemMessages();
	}
}
