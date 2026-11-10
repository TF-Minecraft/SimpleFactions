package net.tfminecraft.simplefactions.war.battle.military;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Regiments one campaign battle cost each faction, summed per (faction, regiment). Only real
 * debits count: a regiment that actually shrank, whether it fought itself or was lent as a levy.
 */
public final class BattleCasualtyTally {
	/** {@code side} is the war side, "attacker" or "defender". */
	public record Loss(Faction faction, String side, String regimentId, String regimentName, int count) {
	}

	private final Map<String, Loss> losses = new LinkedHashMap<>();

	public static BattleCasualtyTally empty() {
		return new BattleCasualtyTally();
	}

	public void add(Faction faction, String side, Regiment regiment, int count) {
		String key = faction.getId().toLowerCase(Locale.ROOT) + "|" + side + "|" + regiment.getId();
		Loss before = losses.get(key);
		int total = (before == null ? 0 : before.count()) + count;
		losses.put(key, new Loss(faction, side, regiment.getId(), regiment.getName(), total));
	}

	public List<Loss> losses() {
		return new ArrayList<>(losses.values());
	}
}
