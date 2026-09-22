package me.Plugins.SimpleFactions.War.core;

import java.time.Instant;

public record WarCommitment(
		int warId,
		String factionId,
		String sourceFactionId,
		String regimentId,
		int count,
		Instant committedAt) {

	public static final String LEVY_REGIMENT_ID = "levy";

	public boolean isLevyRow() {
		return LEVY_REGIMENT_ID.equalsIgnoreCase(regimentId) && sourceFactionId != null;
	}
}
