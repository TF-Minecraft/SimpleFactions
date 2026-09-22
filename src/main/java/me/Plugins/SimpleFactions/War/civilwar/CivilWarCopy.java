package me.Plugins.SimpleFactions.War.civilwar;

public final class CivilWarCopy {
	private CivilWarCopy() {}

	public static final String COULD_NOT_START = "§cCould not start a civil war.";
	public static final String UNMAPPABLE_CAUSE = "§cThis movement's first cause cannot start a civil war.";
	public static final String LAND_SPLIT_FAILED = "§cCould not start a civil war: not enough land to split.";
	public static final String NO_PORT_ON_SEA = "§cThe rebels would have no operational port on the required sea.";
	public static final String ONE_PROVINCE_HOST_GUILD =
			"§cHost guilds cannot start or join a movement in a one-province faction.";
	public static final String ALREADY_IN_CIVIL_WAR =
			"§cA civil war is already underway involving this faction.";
	public static final String HOST_IS_WAR_PAYLOAD =
			"§cCannot start a civil war while this faction is the target of a de jure annex or transfer subject war.";
	public static final String CANNOT_CLAIM = "§cCannot claim land during a civil war.";
	public static final String CANNOT_UNCLAIM = "§cCannot unclaim land during a civil war.";
	public static final String CANNOT_STEAL = "§cCannot take land from a faction during a civil war.";
	public static final String DECLARE_VS_CIVIL_WAR =
			"§cCannot pick this war goal against a faction in a civil war.";
	public static final String VASSALAGE_LAW_MISSING =
			"§cCivil war vassalage law is missing or invalid in war.yml.";
}