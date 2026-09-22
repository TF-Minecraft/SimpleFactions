package me.Plugins.SimpleFactions.War.battle.template;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.SimpleFactions.War.battle.enums.BattleType;

public class BattleTemplate {
	public static final String ATTACKER_SIDE = "attacker";
	public static final String DEFENDER_SIDE = "defender";

	private String id;
	private BattleType type;
	private BattleModeTemplate config;

	public BattleTemplate(String id, ConfigurationSection section) {
		this.id = id;
		this.type = BattleType.fromJson(section.getString("type"));
		if (this.type == null) {
			throw new IllegalStateException("battle-templates.yml: template '" + id + "' requires valid type (field, siege, raid)");
		}
		this.config = BattleModeTemplate.fromSection(section);
	}

	public String getId() {
		return id;
	}

	public BattleType getType() {
		return type;
	}

	public BattleModeTemplate getConfig() {
		return config;
	}
}
