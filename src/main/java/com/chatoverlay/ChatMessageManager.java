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
	private final LinkedList<ChatLine> clanMessages = new LinkedList<>();

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

	private void pruneQueue(LinkedList<ChatLine> queue, int maxLimit)
	{
		long now = System.currentTimeMillis();
		// Remove messages that are pruned and fully faded out (1 second fade-out time)
		queue.removeIf(m -> m.isPruned() && (now - m.getPruneTimestamp()) > 1000L);

		int activeCount = 0;
		for (ChatLine m : queue)
		{
			if (!m.isPruned())
			{
				activeCount++;
			}
		}

		if (activeCount > maxLimit)
		{
			int toPrune = activeCount - maxLimit;
			for (ChatLine m : queue)
			{
				if (!m.isPruned())
				{
					m.prune();
					toPrune--;
					if (toPrune == 0)
					{
						break;
					}
				}
			}
		}
	}

	private List<ChatLine> getActiveAndFading(LinkedList<ChatLine> queue)
	{
		long now = System.currentTimeMillis();
		queue.removeIf(m -> m.isPruned() && (now - m.getPruneTimestamp()) > 1000L);
		return new ArrayList<>(queue);
	}

	public void addPublicClanMessage(ChatLine line, int maxMessages)
	{
		synchronized (publicClanMessages)
		{
			publicClanMessages.addLast(line);
			pruneQueue(publicClanMessages, maxMessages);
		}
	}

	public void addPrivateMessage(ChatLine line, int maxMessages)
	{
		synchronized (privateMessages)
		{
			privateMessages.addLast(line);
			pruneQueue(privateMessages, maxMessages);
		}
	}

	public void addClanMessage(ChatLine line, int maxMessages)
	{
		synchronized (clanMessages)
		{
			clanMessages.addLast(line);
			pruneQueue(clanMessages, maxMessages);
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
			pruneQueue(systemMessages, maxAlerts);
		}
		return true;
	}

	public List<ChatLine> getPublicClanMessages()
	{
		synchronized (publicClanMessages)
		{
			return getActiveAndFading(publicClanMessages);
		}
	}

	public List<ChatLine> getPrivateMessages()
	{
		synchronized (privateMessages)
		{
			return getActiveAndFading(privateMessages);
		}
	}

	public List<ChatLine> getSystemMessages()
	{
		synchronized (systemMessages)
		{
			return getActiveAndFading(systemMessages);
		}
	}

	public List<ChatLine> getClanMessages()
	{
		synchronized (clanMessages)
		{
			return getActiveAndFading(clanMessages);
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

	public void clearClanMessages()
	{
		synchronized (clanMessages)
		{
			clanMessages.clear();
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
		clearClanMessages();
	}
}
