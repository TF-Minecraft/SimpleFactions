package net.tfminecraft.simplefactions.war.core;

import java.time.Instant;

/**
 * An unanswered call to arms. It stays open until the called faction's leader answers or the war
 * ends, so an ally can join late. {@code coalitionId} is set when the call goes to a coalition
 * member rather than an ally; declining it costs more.
 */
public record CallToArms(String targetFactionId, String callerFactionId, String coalitionId, Instant sentAt) {

	public boolean isCoalitionCall() {
		return coalitionId != null;
	}
}
