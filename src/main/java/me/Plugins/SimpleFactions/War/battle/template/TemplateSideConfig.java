package me.Plugins.SimpleFactions.War.battle.template;

import org.bukkit.configuration.ConfigurationSection;

public class TemplateSideConfig {
	private BattleLocation spawn;
	private BattleLocation jail;

	public TemplateSideConfig() {
	}

	public TemplateSideConfig(BattleLocation spawn, BattleLocation jail) {
		this.spawn = spawn;
		this.jail = jail;
	}

	public static TemplateSideConfig fromSection(ConfigurationSection section) {
		if (section == null) {
			return null;
		}
		TemplateSideConfig side = new TemplateSideConfig();
		side.spawn = BattleLocation.fromSection(section.getConfigurationSection("spawn"));
		if (side.spawn == null && section.contains("spawn")) {
			side.spawn = BattleLocation.fromSection(section);
		}
		side.jail = BattleLocation.fromSection(section.getConfigurationSection("jail"));
		return side;
	}

	public BattleLocation getSpawn() {
		return spawn;
	}

	public void setSpawn(BattleLocation spawn) {
		this.spawn = spawn;
	}

	public BattleLocation getJail() {
		return jail;
	}

	public void setJail(BattleLocation jail) {
		this.jail = jail;
	}
}
