package me.Plugins.SimpleFactions.War.battle.template;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.enums.LifeType;

public class BattleModeTemplate {
	private TemplateSideConfig attacker;
	private TemplateSideConfig defender;
	private Boolean friendlyFire;
	private Boolean keepInventory;
	private Boolean lootEnabled;
	private LifeType lifeType;
	private int lives;
	private List<CapturePointDefinition> capturePoints = new ArrayList<>();
	private ContestArea contestArea;
	private int contestDurationSeconds;
	private boolean navalVariant;
	private TemplateSideConfig navalSpawn;
	private DefenderRespawnMode defenderRespawnMode;
	private Integer defenderLives;
	private Boolean capturePointsEnabled;
	private CapturePointDefinition raidTarget;
	private Boolean campaignRaid;

	public static BattleModeTemplate fromSection(ConfigurationSection section) {
		BattleModeTemplate mode = new BattleModeTemplate();
		mode.attacker = TemplateSideConfig.fromSection(section.getConfigurationSection("attacker"));
		mode.defender = TemplateSideConfig.fromSection(section.getConfigurationSection("defender"));
		if (section.contains("friendly_fire")) {
			mode.friendlyFire = section.getBoolean("friendly_fire");
		}
		if (section.contains("keep_inventory")) {
			mode.keepInventory = section.getBoolean("keep_inventory");
		}
		if (section.contains("loot_enabled")) {
			mode.lootEnabled = section.getBoolean("loot_enabled");
		}
		mode.lifeType = LifeType.fromJson(section.getString("life_type"));
		if (section.contains("lives")) {
			mode.lives = section.getInt("lives");
		}
		if (section.contains("capture_points")) {
			mode.capturePoints = parseCapturePoints(section.getList("capture_points"));
		}
		ConfigurationSection contest = section.getConfigurationSection("contest_area");
		if (contest != null) {
			ConfigurationSection min = contest.getConfigurationSection("min");
			ConfigurationSection max = contest.getConfigurationSection("max");
			mode.contestArea = new ContestArea(
					BattleLocation.fromSection(min),
					BattleLocation.fromSection(max));
		}
		if (section.contains("contest_duration_seconds")) {
			mode.contestDurationSeconds = section.getInt("contest_duration_seconds");
		}
		mode.navalVariant = section.getBoolean("naval_variant", false);
		mode.navalSpawn = TemplateSideConfig.fromSection(section.getConfigurationSection("naval_spawn"));
		mode.defenderRespawnMode = DefenderRespawnMode.fromJson(section.getString("defender_respawn_mode"));
		if (section.contains("defender_lives")) {
			mode.defenderLives = section.getInt("defender_lives");
		}
		if (section.contains("capture_points_enabled")) {
			mode.capturePointsEnabled = section.getBoolean("capture_points_enabled");
		}
		if (section.contains("campaign_raid")) {
			mode.campaignRaid = section.getBoolean("campaign_raid");
		}
		ConfigurationSection raidTargetSection = section.getConfigurationSection("raid_target");
		if (raidTargetSection != null) {
			mode.raidTarget = CapturePointDefinition.fromSection(raidTargetSection);
		}
		return mode;
	}

	@SuppressWarnings("unchecked")
	private static List<CapturePointDefinition> parseCapturePoints(List<?> raw) {
		List<CapturePointDefinition> points = new ArrayList<>();
		if (raw == null) {
			return points;
		}
		for (Object entry : raw) {
			if (entry instanceof ConfigurationSection pointSection) {
				CapturePointDefinition point = CapturePointDefinition.fromSection(pointSection);
				if (point != null) {
					points.add(point);
				}
			} else if (entry instanceof java.util.Map<?, ?> map) {
				CapturePointDefinition point = CapturePointDefinition.fromMap((java.util.Map<String, Object>) map);
				if (point != null) {
					points.add(point);
				}
			}
		}
		return points;
	}

	public TemplateSideConfig getAttacker() {
		return attacker;
	}

	public void setAttacker(TemplateSideConfig attacker) {
		this.attacker = attacker;
	}

	public TemplateSideConfig getDefender() {
		return defender;
	}

	public void setDefender(TemplateSideConfig defender) {
		this.defender = defender;
	}

	public Boolean getFriendlyFire() {
		return friendlyFire;
	}

	public void setFriendlyFire(Boolean friendlyFire) {
		this.friendlyFire = friendlyFire;
	}

	public Boolean getKeepInventory() {
		return keepInventory;
	}

	public void setKeepInventory(Boolean keepInventory) {
		this.keepInventory = keepInventory;
	}

	public Boolean getLootEnabled() {
		return lootEnabled;
	}

	public void setLootEnabled(Boolean lootEnabled) {
		this.lootEnabled = lootEnabled;
	}

	public LifeType getLifeType() {
		return lifeType;
	}

	public void setLifeType(LifeType lifeType) {
		this.lifeType = lifeType;
	}

	public int getLives() {
		return lives;
	}

	public void setLives(int lives) {
		this.lives = lives;
	}

	public List<CapturePointDefinition> getCapturePoints() {
		return capturePoints;
	}

	public void setCapturePoints(List<CapturePointDefinition> capturePoints) {
		this.capturePoints = capturePoints != null ? capturePoints : new ArrayList<>();
	}

	public ContestArea getContestArea() {
		return contestArea;
	}

	public void setContestArea(ContestArea contestArea) {
		this.contestArea = contestArea;
	}

	public int getContestDurationSeconds() {
		return contestDurationSeconds;
	}

	public void setContestDurationSeconds(int contestDurationSeconds) {
		this.contestDurationSeconds = contestDurationSeconds;
	}

	public boolean isNavalVariant() {
		return navalVariant;
	}

	public void setNavalVariant(boolean navalVariant) {
		this.navalVariant = navalVariant;
	}

	public TemplateSideConfig getNavalSpawn() {
		return navalSpawn;
	}

	public void setNavalSpawn(TemplateSideConfig navalSpawn) {
		this.navalSpawn = navalSpawn;
	}

	public DefenderRespawnMode getDefenderRespawnMode() {
		return defenderRespawnMode;
	}

	public void setDefenderRespawnMode(DefenderRespawnMode defenderRespawnMode) {
		this.defenderRespawnMode = defenderRespawnMode;
	}

	public Integer getDefenderLives() {
		return defenderLives;
	}

	public void setDefenderLives(Integer defenderLives) {
		this.defenderLives = defenderLives;
	}

	public CapturePointDefinition getRaidTarget() {
		return raidTarget;
	}

	public void setRaidTarget(CapturePointDefinition raidTarget) {
		this.raidTarget = raidTarget;
	}

	public Boolean getCapturePointsEnabled() {
		return capturePointsEnabled;
	}

	public void setCapturePointsEnabled(Boolean capturePointsEnabled) {
		this.capturePointsEnabled = capturePointsEnabled;
	}

	public Boolean getCampaignRaid() {
		return campaignRaid;
	}

	public void setCampaignRaid(Boolean campaignRaid) {
		this.campaignRaid = campaignRaid;
	}
}
