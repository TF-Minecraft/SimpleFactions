package net.tfminecraft.simplefactions.objects;

public class FactionPlayer {
	String player;
	Integer lastLogin;
	public String getPlayer() {
		return player;
	}
	public void setPlayer(String player) {
		this.player = player;
	}
	public Integer getLastLogin() {
		return lastLogin;
	}
	public void setLastLogin(Integer lastLogin) {
		this.lastLogin = lastLogin;
	}
	public FactionPlayer(String p) {
		this.player = p;
		this.lastLogin = 0;
	}
}
