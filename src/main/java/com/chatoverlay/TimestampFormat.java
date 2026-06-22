package com.chatoverlay;

/**
 * Format styles for chat timestamps.
 */
public enum TimestampFormat
{
	HH_MM("HH:MM"),
	HH_MM_SS("HH:MM:SS");

	private final String label;

	TimestampFormat(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
