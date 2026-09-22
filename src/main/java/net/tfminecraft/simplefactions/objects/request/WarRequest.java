package net.tfminecraft.simplefactions.objects.request;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.war.core.War;

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
