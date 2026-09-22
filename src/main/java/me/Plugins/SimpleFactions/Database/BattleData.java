package me.Plugins.SimpleFactions.Database;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.War.battle.template.BattleLocation;
import me.Plugins.SimpleFactions.War.battle.template.CapturePointDefinition;

public class BattleData {
	public String id;
	public String displayName;
	public String battleType;
	public Integer warId;
	public Integer provinceId;
	public String templateName;
	public boolean friendlyFire;
	public boolean keepInventory;
	// Wrapper on purpose: absent in older files means "decide from campaignRaid",
	// which a primitive would silently read as false.
	public Boolean lootEnabled;
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
	public BattleLocation navalSpawn;
	public BattleLocation contestMin;
	public BattleLocation contestMax;
	public CapturePointDefinition raidTarget;
	public List<Integer> allowedProvinceIds = new ArrayList<>();
	public List<BattleSideData> sides = new ArrayList<>();
	public List<CapturePointData> points = new ArrayList<>();
}
