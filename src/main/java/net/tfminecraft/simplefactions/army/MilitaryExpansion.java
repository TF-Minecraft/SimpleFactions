package net.tfminecraft.simplefactions.army;

public class MilitaryExpansion {
	private Regiment regiment;
	private int timeLeft;
	
	public MilitaryExpansion(Regiment r) {
		regiment = r;
		timeLeft = r.getExpansionTime();
	}

	public MilitaryExpansion(Regiment r, int time) {
		regiment = r;
		timeLeft = time;
	}
	
	public void tick() {
		if(timeLeft == 0) return;
		timeLeft--;
	}
	
	public Regiment getRegiment() {
		return regiment;
	}
	
	public int getTimeLeft() {
		return timeLeft;
	}
}
