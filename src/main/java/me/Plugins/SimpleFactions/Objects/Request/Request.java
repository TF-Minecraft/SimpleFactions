package me.Plugins.SimpleFactions.Objects.Request;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Objects.Faction;

public class Request {
	protected Guild sender;
	protected long time = System.currentTimeMillis()+6000;
	
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
