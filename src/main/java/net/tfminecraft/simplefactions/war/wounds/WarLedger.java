package net.tfminecraft.simplefactions.war.wounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything a war's battles cost, kept with the war. Each battle stores what every force
 * brought and each side's deaths; staff corrections are added next to them. Faction wounds are
 * worked out from these records every time, so a corrected battle changes them exactly and
 * nothing can drift. Mercenary wounds live on their company, since they heal over time.
 */
public final class WarLedger {
	public static final String ATTACKER = "attacker";
	public static final String DEFENDER = "defender";

	/** One war side in one battle. */
	public record SideRecord(int deaths, List<BattleForce> forces) {
		public SideRecord {
			deaths = Math.max(0, deaths);
			forces = forces == null ? List.of() : List.copyOf(forces);
		}

		public List<Wound> wounds() {
			return WoundAllocator.allocate(deaths, forces);
		}

		SideRecord withDeaths(int newDeaths) {
			return new SideRecord(newDeaths, forces);
		}
	}

	/** One resolved battle. {@code number} counts from 1 in the order the battles were resolved. */
	public record BattleEntry(
			int number,
			String battleId,
			String resolution,
			String at,
			Integer provinceId,
			SideRecord attacker,
			SideRecord defender) {
		public SideRecord side(String side) {
			return ATTACKER.equals(side) ? attacker : defender;
		}

		BattleEntry withSide(String side, SideRecord record) {
			return ATTACKER.equals(side)
					? new BattleEntry(number, battleId, resolution, at, provinceId, record, defender)
					: new BattleEntry(number, battleId, resolution, at, provinceId, attacker, record);
		}
	}

	/** Lives staff added to (positive) or took from (negative) one faction regiment's wounds. */
	public record Adjustment(String factionId, String regimentId, int lives, String by, String reason, String at) {
	}

	/** What a staff correction changed, for the record. */
	public record Note(String at, String by, String text) {
	}

	/** The saved form, read by Gson. Any list may be missing. */
	public record Data(List<BattleEntry> battles, List<Adjustment> adjustments, List<Note> notes, boolean settled) {
	}

	private final List<BattleEntry> battles = new ArrayList<>();
	private final List<Adjustment> adjustments = new ArrayList<>();
	private final List<Note> notes = new ArrayList<>();
	private boolean settled;
	private Map<String, Map<String, Integer>> woundCache;

	public static WarLedger fromData(Data data) {
		WarLedger ledger = new WarLedger();
		if (data == null) {
			return ledger;
		}
		if (data.battles() != null) {
			ledger.battles.addAll(data.battles());
		}
		if (data.adjustments() != null) {
			ledger.adjustments.addAll(data.adjustments());
		}
		if (data.notes() != null) {
			ledger.notes.addAll(data.notes());
		}
		ledger.settled = data.settled();
		return ledger;
	}

	public Data toData() {
		return new Data(List.copyOf(battles), List.copyOf(adjustments), List.copyOf(notes), settled);
	}

	public List<BattleEntry> battles() {
		return List.copyOf(battles);
	}

	public List<Adjustment> adjustments() {
		return List.copyOf(adjustments);
	}

	public List<Note> notes() {
		return List.copyOf(notes);
	}

	public BattleEntry battle(int number) {
		for (BattleEntry entry : battles) {
			if (entry.number() == number) {
				return entry;
			}
		}
		return null;
	}

	public BattleEntry addBattle(String battleId, String resolution, String at, Integer provinceId,
			SideRecord attacker, SideRecord defender) {
		BattleEntry entry = new BattleEntry(battles.size() + 1, battleId, resolution, at, provinceId, attacker, defender);
		battles.add(entry);
		woundCache = null;
		return entry;
	}

	/** Replaces one side's deaths in a recorded battle and returns the battle as it was before. */
	public BattleEntry setDeaths(int number, String side, int deaths) {
		for (int i = 0; i < battles.size(); i++) {
			BattleEntry entry = battles.get(i);
			if (entry.number() == number) {
				battles.set(i, entry.withSide(side, entry.side(side).withDeaths(deaths)));
				woundCache = null;
				return entry;
			}
		}
		return null;
	}

	public void addAdjustment(Adjustment adjustment) {
		adjustments.add(adjustment);
		woundCache = null;
	}

	public void addNote(Note note) {
		notes.add(note);
	}

	public boolean isSettled() {
		return settled;
	}

	public void markSettled() {
		settled = true;
	}

	/**
	 * Paid-slot wounds in lives per faction and regiment, both keyed in lower case. Never below 0
	 * for a regiment, whatever staff took off.
	 */
	public Map<String, Map<String, Integer>> factionWoundLives() {
		if (woundCache == null) {
			woundCache = computeFactionWounds();
		}
		return woundCache;
	}

	public int factionWoundLives(String factionId, String regimentId) {
		if (factionId == null || regimentId == null) {
			return 0;
		}
		return factionWoundLives()
				.getOrDefault(key(factionId), Map.of())
				.getOrDefault(key(regimentId), 0);
	}

	private Map<String, Map<String, Integer>> computeFactionWounds() {
		Map<String, Map<String, Long>> totals = new LinkedHashMap<>();
		for (BattleEntry entry : battles) {
			addWounds(totals, entry.attacker());
			addWounds(totals, entry.defender());
		}
		for (Adjustment adjustment : adjustments) {
			totals.computeIfAbsent(key(adjustment.factionId()), ignored -> new LinkedHashMap<>())
					.merge(key(adjustment.regimentId()), (long) adjustment.lives(), Long::sum);
		}
		Map<String, Map<String, Integer>> wounds = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Long>> faction : totals.entrySet()) {
			Map<String, Integer> regiments = new LinkedHashMap<>();
			for (Map.Entry<String, Long> regiment : faction.getValue().entrySet()) {
				long lives = Math.max(0, Math.min(Integer.MAX_VALUE, regiment.getValue()));
				if (lives > 0) {
					regiments.put(regiment.getKey(), (int) lives);
				}
			}
			if (!regiments.isEmpty()) {
				wounds.put(faction.getKey(), Collections.unmodifiableMap(regiments));
			}
		}
		// Kept in the order the wounds were first taken, so staff see them in battle order.
		return Collections.unmodifiableMap(wounds);
	}

	private static void addWounds(Map<String, Map<String, Long>> totals, SideRecord side) {
		if (side == null) {
			return;
		}
		for (Wound wound : side.wounds()) {
			if (wound.kind() != ForceKind.MERCENARY && wound.paidLives() > 0) {
				totals.computeIfAbsent(key(wound.ownerId()), ignored -> new LinkedHashMap<>())
						.merge(key(wound.regimentId()), (long) wound.paidLives(), Long::sum);
			}
		}
	}

	static String key(String id) {
		return id.toLowerCase(Locale.ROOT);
	}
}
