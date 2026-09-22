package me.Plugins.SimpleFactions.War.battle.engine.core;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.War.battle.enums.LifeType;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;

public class BattleSide {
	private String id;
	private Location spawn;
	private BossBar lifeBar;
	private List<Warband> bands = new ArrayList<Warband>();
	private LifeType lt;
	private int lives;
	private int maxLives;
	private List<Location> respawnPoints = new ArrayList<Location>();
	private Location jail;
	
	public LifeType getLt() {
		return lt;
	}

	public void setLt(LifeType lt) {
		this.lt = lt;
	}

	public int getLives() {
		return lives;
	}
	public boolean hasPlayer(Player p) {
		for(Warband w : bands) {
			if(w.getPlayers().contains(p)) return true;
		}
		return false;
	}

	public void setLives(int lives) {
		lifeBar.setColor(BarColor.GREEN);
		lifeBar.setStyle(BarStyle.SOLID);
		lifeBar.setTitle(id);
		this.lives = lives;
		this.maxLives = lives;
	}

	public int getMaxLives() {
		return maxLives;
	}

	public void restoreLives(int lives, int maxLives) {
		this.lives = Math.max(0, lives);
		this.maxLives = Math.max(1, maxLives);
		if (this.maxLives < this.lives) {
			this.maxLives = this.lives;
		}
		lifeBar.setColor(BarColor.GREEN);
		lifeBar.setStyle(BarStyle.SOLID);
		lifeBar.setTitle("§f" + id + ": §e" + this.lives);
		lifeBar.setProgress(Math.min(1.0, (double) this.lives / (double) this.maxLives));
	}
	public void updateBossBar(List<Player> list) {
		lifeBar.setVisible(true);
		for(Player p : list) {
			if(!lifeBar.getPlayers().contains(p)) {
				lifeBar.addPlayer(p);
			}
		}
		int divisor = Math.max(1, maxLives);
		double progress = Math.min(1.0, Math.max(0.0, (double) getLives() / (double) divisor));
		lifeBar.setProgress(progress);
		lifeBar.setTitle("§f"+id+": §e"+getLives());
	}
	public void removeBossBar() {
		if (lifeBar != null) {
			lifeBar.removeAll();
			lifeBar.setVisible(false);
		}
	}

	public void addBossBarPlayer(Player p) {
		lifeBar.setVisible(true);
		lifeBar.addPlayer(p);
	}

	public void removeBossBarPlayer(Player p) {
		lifeBar.removePlayer(p);
	}
	public Location getJail() {
		return jail;
	}

	public void setJail(Location jail) {
		this.jail = jail;
	}
	public Location getSpawn() {
		return spawn;
	}

	public void setSpawn(Location spawn) {
		this.spawn = spawn;
	}

	public List<Warband> getBands() {
		return bands;
	}
	public void addBand(Warband b) {
		bands.add(b);
	}
	public void removeBand(Warband b) {
		bands.remove(b);
	}
	public List<Location> getRespawnPoints() {
		return respawnPoints;
	}
	public void addRespawnPoint(Location l) {
		this.respawnPoints.add(l);
	}
	public String getId() {
		return id;
	}

	public BattleSide(String id, LifeType lifeType, int lives) {
		this.id = id;
		this.lt = lifeType;
		this.lives = lives;
		this.lifeBar = Bukkit.createBossBar(id, BarColor.GREEN, BarStyle.SOLID);
	}

	public void tickLife() {
		if(this.lives > 0) {
			this.lives--;
		}
	}

	/**
	 * Applies a battle death to collective lives. Lives are total respawns remaining for the side.
	 * @return true when there was no respawn left before this death (respawn at jail)
	 */
	public boolean applyDeathAndNeedsJailRespawn() {
		if (lives <= 0) {
			return true;
		}
		tickLife();
		return false;
	}

	public Integer getAllParticipants() {
		int count = 0;
		for (Warband w : bands) {
			count = count + w.getMemberCount();
		}
		return count;
	}
	
	
}
