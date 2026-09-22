package me.Plugins.SimpleFactions.War.battle.template;

import org.bukkit.configuration.ConfigurationSection;

public class CapturePointDefinition {
	private String id;
	private BattleLocation location;

	public CapturePointDefinition() {
	}

	public CapturePointDefinition(String id, BattleLocation location) {
		this.id = id;
		this.location = location;
	}

	public static CapturePointDefinition fromMap(java.util.Map<String, Object> map) {
		if (map == null) {
			return null;
		}
		Object idObj = map.get("id");
		if (!(idObj instanceof String pointId)) {
			return null;
		}
		BattleLocation location = null;
		Object locationObj = map.get("location");
		if (locationObj instanceof java.util.Map<?, ?> locationMap) {
			location = BattleLocation.fromMap(locationMap);
		} else if (map.containsKey("x")) {
			location = BattleLocation.fromMap(map);
		}
		if (location == null) {
			return null;
		}
		return new CapturePointDefinition(pointId, location);
	}

	public static CapturePointDefinition fromSection(ConfigurationSection section) {
		if (section == null) {
			return null;
		}
		String pointId = section.getString("id");
		BattleLocation location = BattleLocation.fromSection(section.getConfigurationSection("location"));
		if (location == null && section.contains("x")) {
			location = BattleLocation.fromSection(section);
		}
		if (pointId == null || location == null) {
			return null;
		}
		return new CapturePointDefinition(pointId, location);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public BattleLocation getLocation() {
		return location;
	}

	public void setLocation(BattleLocation location) {
		this.location = location;
	}
}
