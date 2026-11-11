package net.tfminecraft.simplefactions.database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;
import net.tfminecraft.simplefactions.war.battle.template.CapturePointDefinition;

public class BattleData {
	public String id;
	public java.util.Set<net.tfminecraft.tlibs.character.CharacterRef> participants = new java.util.LinkedHashSet<>();
	public String displayName;
	public String battleType;
	public Integer warId;
	// Absent in older saves: war attackers stay on the battle attacker side.
	public String offensiveCoalition;
	public Integer provinceId;
	public String templateName;
	public boolean friendlyFire;
	public boolean keepInventory;
	// Wrapper on purpose: absent in older files means "decide from campaignRaid",
	// which a primitive would silently read as false.
	public Boolean lootEnabled;
	// Absent in older files reads as false, which is the default: no mana on respawn.
	public boolean manaRefillOnRespawn;
	public boolean teleport;
	public boolean locked;
	public int lives;
	public String lifeType;
	public boolean sequentialCapture;
	public boolean capturePointsEnabled;
	public boolean campaignRaid;
	public boolean started;
	public String startedAt;
	public int contestDurationSeconds;
	public int contestHoldRemainingSeconds;
	public String defenderRespawnMode;
	public Integer defenderLives;
	public boolean navalVariant;
	public Map<String, Integer> sideCasualties = new LinkedHashMap<>();
	// Staff vehicle overrides: vehicle UUID -> side id. Absent in older saves means none.
	public Map<String, String> forcedVehicles = new LinkedHashMap<>();
	// Absent in older files reads as false: installation commitments apply.
	public boolean allVehiclesUsable;
	// Absent in older saves and for battles that have not started.
	public net.tfminecraft.simplefactions.war.stats.BattleStats stats;
	public BattleLocation navalSpawn;
	public BattleLocation contestMin;
	public BattleLocation contestMax;
	public CapturePointDefinition raidTarget;
	public List<Integer> allowedProvinceIds = new ArrayList<>();
	public List<BattleSideData> sides = new ArrayList<>();
	public List<CapturePointData> points = new ArrayList<>();
	// Capture points as they stood at the start, for a staff restart. Only id and state are set.
	public List<CapturePointData> startPoints = new ArrayList<>();
	// Capture points as they stand in a running battle. Only id and state are set.
	public List<CapturePointData> livePoints = new ArrayList<>();
	// Field battle control per side id. Absent in older saves means none.
	public Map<String, Double> controlScores = new LinkedHashMap<>();
	public boolean staffHold;
	public boolean firstBattleOfWar;
}
