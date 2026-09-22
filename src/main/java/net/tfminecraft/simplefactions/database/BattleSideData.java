package net.tfminecraft.simplefactions.database;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;

public class BattleSideData {
	public String id;
	public BattleLocation spawn;
	public BattleLocation jail;
	public List<BattleLocation> respawnPoints = new ArrayList<>();
	public int lives;
	public int maxLives;
	public String lifeType;
	public List<String> warbandIds = new ArrayList<>();
}
