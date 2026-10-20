package net.tfminecraft.simplefactions.diplomacy.coalition;

import java.io.File;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** The {@code coalition:} section of diplomacy.yml. Defaults match the shipped file. */
public final class CoalitionSettings {
	public static boolean enabled = true;
	public static double costBase = 5.0;
	public static double costExponent = 1.5;
	public static double leaderMultiplier = 1.0;
	public static double gateRatio = 1.0;
	public static int targetLossDays = 7;
	public static int singleMemberDays = 7;
	public static int leaveCooldownDays = 7;
	public static int inviteExpiryDays = 7;
	public static int nameMinLength = 3;
	public static int nameMaxLength = 32;

	private CoalitionSettings() {}

	public static void load(File diplomacyFile) {
		load(YamlConfiguration.loadConfiguration(diplomacyFile).getConfigurationSection("coalition"));
	}

	/** A missing section keeps every default. */
	public static void load(ConfigurationSection section) {
		reset();
		if (section == null) return;
		enabled = section.getBoolean("enabled", enabled);
		costBase = Math.max(0.0, section.getDouble("cost.base", costBase));
		costExponent = Math.max(0.0, section.getDouble("cost.exponent", costExponent));
		leaderMultiplier = Math.max(0.0, section.getDouble("cost.leader-multiplier", leaderMultiplier));
		gateRatio = Math.max(0.0, section.getDouble("gate-ratio", gateRatio));
		targetLossDays = Math.max(0, section.getInt("target-loss-days", targetLossDays));
		singleMemberDays = Math.max(0, section.getInt("single-member-days", singleMemberDays));
		leaveCooldownDays = Math.max(0, section.getInt("leave-cooldown-days", leaveCooldownDays));
		inviteExpiryDays = Math.max(1, section.getInt("invite-expiry-days", inviteExpiryDays));
		nameMinLength = Math.max(1, section.getInt("name-min-length", nameMinLength));
		nameMaxLength = Math.max(nameMinLength, section.getInt("name-max-length", nameMaxLength));
	}

	public static void reset() {
		enabled = true;
		costBase = 5.0;
		costExponent = 1.5;
		leaderMultiplier = 1.0;
		gateRatio = 1.0;
		targetLossDays = 7;
		singleMemberDays = 7;
		leaveCooldownDays = 7;
		inviteExpiryDays = 7;
		nameMinLength = 3;
		nameMaxLength = 32;
	}

	static long days(int days) {
		return days * 24L * 60L * 60L * 1000L;
	}
}
