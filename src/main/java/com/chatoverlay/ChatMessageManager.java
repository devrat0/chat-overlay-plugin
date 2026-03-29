package com.chatoverlay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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
	private final java.util.Map<String, Long> recentSystemMessages = new java.util.LinkedHashMap<String, Long>()
	{
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<String, Long> eldest)
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

	/**
	 * Add a system message. Returns false if it was filtered as spam.
	 *
	 * @param spamPatterns  set of lowercase substrings to match against (ignored when filterSpam=false)
	 * @param spamCooldownMs minimum ms between identical messages (ignored when filterSpam=false)
	 */
	public boolean addSystemMessage(ChatLine line, int maxAlerts, boolean filterSpam,
		Set<String> spamPatterns, long spamCooldownMs)
	{
		if (filterSpam)
		{
			String lower = line.getPlainMessage().toLowerCase().trim();

			// Filter known spam patterns
			for (String pattern : spamPatterns)
			{
				if (lower.contains(pattern))
				{
					return false;
				}
			}

			// Filter duplicates within cooldown
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

	/**
	 * Remove expired system alerts.
	 */
	public void pruneSystemMessages(int durationSeconds)
	{
		pruneQueue(systemMessages, durationSeconds);
	}

	/**
	 * Remove fully-expired public/clan messages (age > duration).
	 * No-op when {@code durationSeconds} is 0 (fade disabled).
	 */
	public void prunePublicClanMessages(int durationSeconds)
	{
		pruneQueue(publicClanMessages, durationSeconds);
	}

	/**
	 * Remove fully-expired private messages (age > duration).
	 * No-op when {@code durationSeconds} is 0 (fade disabled).
	 */
	public void prunePrivateMessages(int durationSeconds)
	{
		pruneQueue(privateMessages, durationSeconds);
	}

	/**
	 * Shared prune logic: remove messages older than {@code durationSeconds}
	 * from the given queue. Skips pruning entirely when duration is 0.
	 */
	private void pruneQueue(LinkedList<ChatLine> queue, int durationSeconds)
	{
		if (durationSeconds <= 0)
		{
			return;
		}
		long cutoff = System.currentTimeMillis() - (durationSeconds * 1000L);
		synchronized (queue)
		{
			Iterator<ChatLine> it = queue.iterator();
			while (it.hasNext())
			{
				if (it.next().getTimestamp() < cutoff)
				{
					it.remove();
				}
			}
		}
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
