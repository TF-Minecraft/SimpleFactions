package me.Plugins.SimpleFactions.War.battle.template;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Loaders.BattleTemplateLoader;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.enums.LifeType;

public final class BattleTemplateService {
	private static BattleTemplateService instance;

	private BattleTemplateService() {
	}

	public static BattleTemplateService getInstance() {
		if (instance == null) {
			instance = new BattleTemplateService();
		}
		return instance;
	}

	public static void resetForTests() {
		instance = new BattleTemplateService();
	}

	public BattleTemplate getTemplate(String name) {
		return BattleTemplateLoader.getByName(name);
	}

	public boolean hasTemplate(String name) {
		return getTemplate(name) != null;
	}

	public BattleModeTemplate getModeConfig(String name) {
		BattleTemplate template = getTemplate(name);
		if (template == null) {
			return null;
		}
		return applyDefaults(copyMode(template.getConfig()), template.getType());
	}

	public BattleModeTemplate applyDefaults(BattleModeTemplate mode, BattleType type) {
		if (mode == null || type == null) {
			return mode;
		}
		if (mode.getFriendlyFire() == null) {
			mode.setFriendlyFire(true);
		}
		if (mode.getKeepInventory() == null) {
			mode.setKeepInventory(true);
		}
		if (mode.getLootEnabled() == null) {
			// Campaign raids are the one battle kind that pays nothing, so a raid
			// template that omits the key still ends up with loot off.
			mode.setLootEnabled(!Boolean.TRUE.equals(mode.getCampaignRaid()));
		}
		if (mode.getLifeType() == null) {
			mode.setLifeType(LifeType.COLLECTIVE);
		}
		if (mode.getLives() <= 0) {
			mode.setLives(25);
		}
		if (type == BattleType.SIEGE && mode.getContestDurationSeconds() <= 0) {
			mode.setContestDurationSeconds(Cache.battleSiegeContestDurationSeconds);
		}
		if (type == BattleType.RAID && mode.getDefenderRespawnMode() == null) {
			mode.setDefenderRespawnMode(Cache.battleRaidDefenderRespawnModeDefault);
		}
		if (mode.getCapturePointsEnabled() == null) {
			mode.setCapturePointsEnabled(type == BattleType.FIELD);
		}
		return mode;
	}

	private BattleModeTemplate copyMode(BattleModeTemplate source) {
		BattleModeTemplate copy = new BattleModeTemplate();
		copy.setAttacker(source.getAttacker());
		copy.setDefender(source.getDefender());
		copy.setFriendlyFire(source.getFriendlyFire());
		copy.setKeepInventory(source.getKeepInventory());
		copy.setLootEnabled(source.getLootEnabled());
		copy.setLifeType(source.getLifeType());
		copy.setLives(source.getLives());
		copy.setCapturePoints(source.getCapturePoints());
		copy.setContestArea(source.getContestArea());
		copy.setContestDurationSeconds(source.getContestDurationSeconds());
		copy.setNavalVariant(source.isNavalVariant());
		copy.setNavalSpawn(source.getNavalSpawn());
		copy.setDefenderRespawnMode(source.getDefenderRespawnMode());
		copy.setDefenderLives(source.getDefenderLives());
		copy.setRaidTarget(source.getRaidTarget());
		copy.setCapturePointsEnabled(source.getCapturePointsEnabled());
		copy.setCampaignRaid(source.getCampaignRaid());
		return copy;
	}
}
