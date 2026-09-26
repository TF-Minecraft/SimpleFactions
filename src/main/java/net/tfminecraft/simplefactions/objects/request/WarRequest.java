package net.tfminecraft.simplefactions.objects.request;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.war.core.War;

public class WarRequest extends Request{
	// Matches the 60 seconds the call-to-arms message promises; expiry costs stability.
	private static final long EXPIRY_MILLIS = 60000;

	private War war;

	public WarRequest(Guild sender, War w) {
		super(sender);
		this.war = w;
		this.time = System.currentTimeMillis() + EXPIRY_MILLIS;
	}

	public War getWar() {
		return war;
	}
}
