package me.Plugins.SimpleFactions.Objects.Request;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.War.core.War;

public class WarRequest extends Request{
	private War war;
	
	public WarRequest(Guild sender, War w) {
		super(sender);
		this.war = w;
	}

	public War getWar() {
		return war;
	}
}
