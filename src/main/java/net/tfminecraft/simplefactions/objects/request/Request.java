package net.tfminecraft.simplefactions.objects.request;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

public class Request {
	protected Guild sender;
	// Matches the 60 seconds the request messages promise.
	protected long time = System.currentTimeMillis() + 60_000L;
	
	public Request(Guild sender) {
		this.sender = sender;
	}

	public Guild getSender() {
		return sender;
	}

	public Faction getFaction() {
		return sender.getFaction();
	}
	
	public boolean timedOut() {
		return System.currentTimeMillis() >= time;
	}
}
