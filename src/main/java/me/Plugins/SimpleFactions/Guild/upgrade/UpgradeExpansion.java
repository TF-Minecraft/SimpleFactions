package me.Plugins.SimpleFactions.Guild.upgrade;

public class UpgradeExpansion {
	private Upgrade upgrade;
	private int timeLeft;
	
	public UpgradeExpansion(Upgrade u) {
		upgrade = u;
		timeLeft = u.getExpansionTime();
	}

	public UpgradeExpansion(Upgrade u, int time) {
		upgrade = u;
		timeLeft = time;
	}
	
	public void tick() {
		if(timeLeft == 0) return;
		timeLeft--;
	}
	
	public Upgrade getUpgrade() {
		return upgrade;
	}
	
	public int getTimeLeft() {
		return timeLeft;
	}
}
