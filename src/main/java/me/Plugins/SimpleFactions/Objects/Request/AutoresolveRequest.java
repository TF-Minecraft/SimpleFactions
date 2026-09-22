package me.Plugins.SimpleFactions.Objects.Request;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;

public class AutoresolveRequest extends Request {
	private static final long TIMEOUT_MS = 60_000L;

	private final War war;
	private final BelligerentRole proposerSide;

	public AutoresolveRequest(Guild sender, War war, BelligerentRole proposerSide) {
		super(sender);
		this.war = war;
		this.proposerSide = proposerSide;
		this.time = System.currentTimeMillis() + TIMEOUT_MS;
	}

	public War getWar() {
		return war;
	}

	public BelligerentRole getProposerSide() {
		return proposerSide;
	}
}
