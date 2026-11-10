package net.tfminecraft.simplefactions.war.stats;

/**
 * One character's numbers in one battle. Damage, kills, healing and crowd control
 * on enemies are kept apart from what landed on the fighter's own side.
 */
public final class FighterStats {
	/** Character key, see {@link net.tfminecraft.simplefactions.identity.Members#key(org.bukkit.entity.Player)}. */
	public String key;
	public String name;
	public String side;
	public String faction;
	/** The fighter's own faction when first seen, null without one; with the founding stamp it names one incarnation. */
	public String factionId;
	public Long factionFoundedAt;
	/** The faction that hired the fighter as a mercenary for this war, null when they fought unhired. */
	public String hirerFactionId;
	/** MMOCore class (or subclass) the character played when first seen in the battle. */
	public String className;
	public int kills;
	public int deaths;
	/** Enemy deaths this fighter hit in the last seconds before someone else got the kill. */
	public int assists;
	public int teamKills;
	public int logouts;
	/** Hits from enemies that some plugin cancelled outside the hit cooldown. */
	public int blockedHits;
	public double damage;
	public double friendlyDamage;
	public double damageTaken;
	/** Health restored to other fighters of the same side by this fighter's skills. */
	public double healing;
	/** Health this fighter restored to themself (heals on self, lifesteal, healing potions). */
	public double selfHealing;
	/** Seconds of crowd control (slow, root, weaken, silence, wound, stun) put on enemies. */
	public double cc;
	public double ccTaken;
	public double friendlyCc;
}
