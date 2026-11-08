package net.tfminecraft.simplefactions.war.stats;

/**
 * One character's numbers in one battle. Damage to enemies and damage to the
 * fighter's own side are kept apart, as are enemy kills and team kills.
 */
public final class FighterStats {
	/** Character key, see {@link net.tfminecraft.simplefactions.identity.Members#key(org.bukkit.entity.Player)}. */
	public String key;
	public String name;
	public String side;
	public String faction;
	/** MMOCore class (or subclass) the character played when first seen in the battle. */
	public String className;
	public int kills;
	public int deaths;
	public int teamKills;
	public int logouts;
	/** Hits from enemies that some plugin cancelled outside the hit cooldown. */
	public int blockedHits;
	public double damage;
	public double friendlyDamage;
	public double damageTaken;
}
