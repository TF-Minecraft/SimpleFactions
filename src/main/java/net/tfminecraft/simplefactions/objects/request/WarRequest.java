package net.tfminecraft.simplefactions.objects.request;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.war.core.War;

public class WarRequest extends Request{
	private War war;
	// The called faction, kept so a decline still lands on it if its leader changes meanwhile.
	private String targetFactionId;
	// Set when the call goes to a coalition member rather than an ally; declining it costs more.
	private String coalitionId;

	public WarRequest(Guild sender, War w) {
		this(sender, w, null);
	}

	public WarRequest(Guild sender, War w, String targetFactionId) {
		this(sender, w, targetFactionId, null);
	}

	public WarRequest(Guild sender, War w, String targetFactionId, String coalitionId) {
		super(sender);
		this.war = w;
		this.targetFactionId = targetFactionId;
		this.coalitionId = coalitionId;
		this.time = System.currentTimeMillis() + expirySeconds() * 1000L;
	}

	/** The window the call-to-arms message promises; running out of it is never a decline. */
	public static int expirySeconds() {
		return Math.max(1, Cache.warCallToArmsExpirySeconds);
	}

	/** {@code 5 minutes} for whole minutes, otherwise {@code 90 seconds}. */
	public static String expiryText() {
		int seconds = expirySeconds();
		if (seconds % 60 != 0) return seconds + (seconds == 1 ? " second" : " seconds");
		int minutes = seconds / 60;
		return minutes + (minutes == 1 ? " minute" : " minutes");
	}

	public War getWar() {
		return war;
	}

	public String getTargetFactionId() {
		return targetFactionId;
	}

	public String getCoalitionId() {
		return coalitionId;
	}

	public boolean isCoalitionCall() {
		return coalitionId != null;
	}
}
