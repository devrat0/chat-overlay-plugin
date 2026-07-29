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

	private boolean isSameMessage(ChatLine m1, ChatLine m2, boolean isGameChat)
	{
		if (m1 == null || m2 == null)
		{
			return false;
		}

		String msg1 = m1.getPlainMessage() != null ? m1.getPlainMessage().trim() : "";
		String msg2 = m2.getPlainMessage() != null ? m2.getPlainMessage().trim() : "";

		if (!msg1.equalsIgnoreCase(msg2))
		{
			return false;
		}

		if (isGameChat)
		{
			return true;
		}

		String sender1 = m1.getSender() != null ? m1.getSender().trim() : "";
		String sender2 = m2.getSender() != null ? m2.getSender().trim() : "";

		if (!sender1.equalsIgnoreCase(sender2))
		{
			return false;
		}

		if (m1.getCategory() != m2.getCategory())
		{
			return false;
		}

		return java.util.Objects.equals(m1.getChannelName(), m2.getChannelName());
	}

	public void collapseQueue(LinkedList<ChatLine> queue, boolean isGameChat)
	{
		synchronized (queue)
		{
			if (queue.isEmpty())
			{
				return;
			}

			List<ChatLine> copy = new ArrayList<>(queue);
			queue.clear();

			for (ChatLine line : copy)
			{
				List<ChatLine> matches = new ArrayList<>();
				int existingCount = 0;
				for (ChatLine existing : queue)
				{
					if (isSameMessage(existing, line, isGameChat))
					{
						matches.add(existing);
						existingCount += existing.getCount();
					}
				}

				if (!matches.isEmpty())
				{
					line.setCount(existingCount + 1);
					queue.removeAll(matches);
				}
				else
				{
					line.setCount(1);
				}

				line.resetPrune();
				queue.addLast(line);
			}
		}
	}

	public void collapseSystemMessages()
	{
		collapseQueue(systemMessages, true);
		collapseQueue(publicClanMessages, true);
	}

	public void collapsePlayerMessages()
	{
		collapseQueue(publicClanMessages, false);
		collapseQueue(privateMessages, false);
		collapseQueue(clanMessages, false);
	}

	private void addMessageWithCollapsing(LinkedList<ChatLine> queue, ChatLine line, int maxLimit, boolean collapse, boolean isGameChat)
	{
		synchronized (queue)
		{
			if (collapse)
			{
				List<ChatLine> matches = new ArrayList<>();
				int existingCount = 0;
				for (ChatLine m : queue)
				{
					if (isSameMessage(m, line, isGameChat))
					{
						matches.add(m);
						existingCount += m.getCount();
					}
				}

				if (!matches.isEmpty())
				{
					line.setCount(existingCount + 1);
					queue.removeAll(matches);
				}
				else
				{
					line.setCount(1);
				}
			}
			else
			{
				line.setCount(1);
			}

			line.resetPrune();
			queue.addLast(line);
			pruneQueue(queue, maxLimit);
		}
	}

	public void addPublicClanMessage(ChatLine line, int maxMessages)
	{
		addPublicClanMessage(line, maxMessages, false);
	}

	public void addPublicClanMessage(ChatLine line, int maxMessages, boolean collapse)
	{
		boolean isGameChat = line.getCategory() == ChatCategory.SYSTEM;
		addMessageWithCollapsing(publicClanMessages, line, maxMessages, collapse, isGameChat);
	}

	public void addPrivateMessage(ChatLine line, int maxMessages)
	{
		addPrivateMessage(line, maxMessages, false);
	}

	public void addPrivateMessage(ChatLine line, int maxMessages, boolean collapse)
	{
		addMessageWithCollapsing(privateMessages, line, maxMessages, collapse, false);
	}

	public void addClanMessage(ChatLine line, int maxMessages)
	{
		addClanMessage(line, maxMessages, false);
	}

	public void addClanMessage(ChatLine line, int maxMessages, boolean collapse)
	{
		addMessageWithCollapsing(clanMessages, line, maxMessages, collapse, false);
	}

	public boolean addSystemMessage(ChatLine line, int maxAlerts, boolean filterSpam, long spamCooldownMs)
	{
		return addSystemMessage(line, maxAlerts, filterSpam, spamCooldownMs, false);
	}

	public boolean addSystemMessage(ChatLine line, int maxAlerts, boolean filterSpam, long spamCooldownMs, boolean collapse)
	{
		if (filterSpam && !collapse)
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

		addMessageWithCollapsing(systemMessages, line, maxAlerts, collapse, true);
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
