package me.Plugins.SimpleFactions.War.battle.engine.core;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleModeTemplate;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplateService;

public final class BattleFactory {
	private BattleFactory() {
	}

	public static Battle createBlank(BattleType type, String battleId) {
		if (type == null) {
			throw new IllegalArgumentException("Battle type is required (field, siege, raid)");
		}
		if (battleId == null || battleId.isBlank()) {
			throw new IllegalArgumentException("Battle id is required");
		}

		Battle battle = new Battle(battleId);
		battle.setBattleType(type);
		battle.setTemplateName(null);
		battle.setCapturePointsEnabled(type == BattleType.FIELD);
		seedBaseSides(battle);
		return battle;
	}

	public static void applyTemplate(Battle battle, String templateName) {
		validateEditable(battle);
		if (templateName == null || templateName.isBlank()) {
			throw new IllegalArgumentException("Template name is required");
		}
		if (battle.getBattleType() == null) {
			throw new IllegalStateException("Battle type must be set before applying a template");
		}

		BattleTemplate template = BattleTemplateService.getInstance().getTemplate(templateName);
		if (template == null) {
			throw new IllegalArgumentException("Unknown battle template: " + templateName);
		}
		if (template.getType() != battle.getBattleType()) {
			throw new IllegalArgumentException(
					"Template '" + templateName + "' is type " + template.getType().toJson()
							+ ", not " + battle.getBattleType().toJson());
		}

		resetLayout(battle);
		BattleModeTemplate config = BattleTemplateService.getInstance().getModeConfig(templateName);
		battle.setTemplateName(templateName);
		applyModeSettings(battle, config);
		seedBaseSides(battle);
	}

	public static void resetToBase(Battle battle) {
		validateEditable(battle);
		resetLayout(battle);
		battle.setTemplateName(null);
		if (battle.getBattleType() != null) {
			battle.setCapturePointsEnabled(battle.getBattleType() == BattleType.FIELD);
		}
		seedBaseSides(battle);
	}

	public static void applyCampaignDefault(Battle battle) {
		if (battle == null || battle.getBattleType() == null) {
			return;
		}
		String name = switch (battle.getBattleType()) {
			case FIELD -> Cache.battleCampaignTemplateField;
			case SIEGE -> Cache.battleCampaignTemplateSiege;
			case RAID -> Cache.battleCampaignTemplateRaid;
		};
		if (name != null && !name.isBlank()) {
			applyTemplate(battle, name);
		}
	}

	public static void resetLayout(Battle battle) {
		battle.clearSides();
		battle.clearPoints();
		battle.clearTemplateMetadata();
		battle.resetBaseSettings();
	}

	private static void validateEditable(Battle battle) {
		if (battle == null) {
			throw new IllegalArgumentException("Battle is required");
		}
		if (battle.hasStarted()) {
			throw new IllegalStateException("Cannot change template while battle has started");
		}
	}

	private static void seedBaseSides(Battle battle) {
		BattleSide attacker = new BattleSide(BattleTemplate.ATTACKER_SIDE, battle.getLifeType(), battle.getLives());
		BattleSide defender = new BattleSide(BattleTemplate.DEFENDER_SIDE, battle.getLifeType(), battle.getLives());
		battle.addSide(attacker);
		battle.addSide(defender);
	}

	private static void applyModeSettings(Battle battle, BattleModeTemplate config) {
		if (config == null) {
			return;
		}
		battle.setFriendlyFire(config.getFriendlyFire());
		battle.setKeepInventory(config.getKeepInventory());
		if (config.getLootEnabled() != null) {
			battle.setLootEnabled(config.getLootEnabled());
		}
		battle.setLifeType(config.getLifeType());
		battle.setLives(config.getLives());
		if (config.getCapturePointsEnabled() != null) {
			battle.setCapturePointsEnabled(config.getCapturePointsEnabled());
		}

		BattleType type = battle.getBattleType();
		if (type == BattleType.SIEGE) {
			battle.setContestDurationSeconds(config.getContestDurationSeconds());
			battle.setNavalVariant(config.isNavalVariant());
		} else if (type == BattleType.RAID) {
			battle.setDefenderRespawnMode(config.getDefenderRespawnMode());
			if (config.getDefenderLives() != null) {
				battle.setDefenderLives(config.getDefenderLives());
			}
			if (Boolean.TRUE.equals(config.getCampaignRaid())) {
				battle.setCampaignRaid(true);
			}
		} else if (type == BattleType.FIELD) {
			battle.setNavalVariant(config.isNavalVariant());
		}
	}
}

