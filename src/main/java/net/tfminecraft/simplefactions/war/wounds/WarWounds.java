package net.tfminecraft.simplefactions.war.wounds;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyTally;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * The one place that turns battle wounds into army strength.
 * <ul>
 * <li>A faction regiment's wounded slots are its paid-slot wounds over every active war, one slot
 * per {@code deaths_per_regiment_loss} lives, never more than its paid slots. Available slots are
 * the rest; only they fight and pay upkeep.</li>
 * <li>When a war ends its wounds are settled once: each regiment loses that war's wounded paid
 * slots for good. Law slots are never lost.</li>
 * <li>Mercenary wounds go straight onto the company and heal over time.</li>
 * </ul>
 */
public final class WarWounds {
	private WarWounds() {}

	/** The answer to a staff tool: whether it worked and what to tell them. */
	public record Result(boolean ok, List<String> lines) {
		static Result error(String line) {
			return new Result(false, List.of(line));
		}
	}

	public static int deathsPerSlot() {
		return Cache.warBattleDeathsPerRegimentLoss > 0 ? Cache.warBattleDeathsPerRegimentLoss : 5;
	}

	/** Paid-slot wounds in lives on this regiment, over every active war not yet settled. */
	public static int woundLives(Faction faction, Regiment regiment) {
		if (faction == null || regiment == null || !carriesWounds(regiment)) {
			return 0;
		}
		long lives = 0;
		for (War war : WarManager.getActive()) {
			WarLedger ledger = war.getLedger();
			if (ledger != null && !ledger.isSettled()) {
				lives += ledger.factionWoundLives(faction.getId(), regiment.getId());
			}
		}
		return (int) Math.min(Integer.MAX_VALUE, lives);
	}

	public static int woundedSlots(Faction faction, Regiment regiment) {
		if (faction == null || regiment == null) {
			return 0;
		}
		return Math.min(regiment.getPaidSlots(), woundLives(faction, regiment) / deathsPerSlot());
	}

	public static int availablePaidSlots(Faction faction, Regiment regiment) {
		return regiment == null ? 0 : regiment.getPaidSlots() - woundedSlots(faction, regiment);
	}

	/** Slots that can fight now: every law slot and the unwounded paid ones. */
	public static int availableSlots(Faction faction, Regiment regiment) {
		return regiment == null ? 0 : regiment.getCurrentSlots() - woundedSlots(faction, regiment);
	}

	private static boolean carriesWounds(Regiment regiment) {
		return !regiment.isLevy() && !regiment.isEquipment() && !regiment.isMercenary();
	}

	/**
	 * Records a resolved battle in the war's ledger, puts the mercenary wounds on their companies
	 * and returns, per faction regiment, how many slots this battle wounded.
	 */
	public static BattleCasualtyTally recordBattle(War war, String battleId, String resolution, Integer provinceId,
			WarLedger.SideRecord attacker, WarLedger.SideRecord defender) {
		WarLedger ledger = war.getLedger();
		Map<String, Map<String, Integer>> before = ledger.factionWoundLives();
		WarLedger.BattleEntry entry = ledger.addBattle(battleId, resolution, CampaignClock.now().toString(),
				provinceId, attacker, defender);
		BattleCasualtyTally tally = BattleCasualtyTally.empty();
		addTally(tally, WarLedger.ATTACKER, entry.attacker(), before, ledger.factionWoundLives());
		addTally(tally, WarLedger.DEFENDER, entry.defender(), before, ledger.factionWoundLives());
		Map<String, Integer> companyLives = new LinkedHashMap<>();
		addCompanyLives(companyLives, entry.attacker().wounds(), 1);
		addCompanyLives(companyLives, entry.defender().wounds(), 1);
		applyCompanyLives(companyLives);
		WarManager.persist(war);
		LogManager.war("WAR_WOUNDS_BATTLE war=%s battle=%s number=%s attackerDeaths=%s defenderDeaths=%s",
				war.getId(), battleId, entry.number(), entry.attacker().deaths(), entry.defender().deaths());
		return tally;
	}

	/** Sets, adds or removes deaths for one side of a recorded battle; wounds follow. */
	public static Result editDeaths(War war, int number, String side, String operation, int amount, String by,
			String reason) {
		WarLedger ledger = war.getLedger();
		WarLedger.BattleEntry entry = ledger.battle(number);
		if (entry == null) {
			return Result.error("§cWar " + war.getId() + " has no battle " + number + ". See /war admin losses "
					+ war.getId() + ".");
		}
		if (ledger.isSettled()) {
			return Result.error("§cThat war's wounds are already settled.");
		}
		if (amount < 0) {
			return Result.error("§cThe amount must be 0 or more.");
		}
		int deathsBefore = entry.side(side).deaths();
		long deaths = switch (operation) {
			case "set" -> amount;
			case "add" -> (long) deathsBefore + amount;
			case "remove" -> (long) deathsBefore - amount;
			default -> Long.MIN_VALUE;
		};
		if (deaths == Long.MIN_VALUE) {
			return Result.error("§cUse set, add or remove.");
		}
		int newDeaths = (int) Math.max(0, Math.min(Integer.MAX_VALUE, deaths));
		Map<String, Map<String, Integer>> before = ledger.factionWoundLives();
		List<Wound> woundsBefore = entry.side(side).wounds();
		ledger.setDeaths(number, side, newDeaths);
		Map<String, Map<String, Integer>> after = ledger.factionWoundLives();
		Map<String, Integer> companyLives = new LinkedHashMap<>();
		addCompanyLives(companyLives, ledger.battle(number).side(side).wounds(), 1);
		addCompanyLives(companyLives, woundsBefore, -1);
		applyCompanyLives(companyLives);
		String text = "battle " + number + " " + side + " deaths " + deathsBefore + " -> " + newDeaths;
		return finishEdit(war, by, reason, text, before, after, companyLives);
	}

	/** Adds or removes wounded lives on one faction regiment directly. */
	public static Result adjust(War war, Faction faction, Regiment regiment, int lives, String by, String reason) {
		WarLedger ledger = war.getLedger();
		if (ledger.isSettled()) {
			return Result.error("§cThat war's wounds are already settled.");
		}
		Map<String, Map<String, Integer>> before = ledger.factionWoundLives();
		ledger.addAdjustment(new WarLedger.Adjustment(faction.getId(), regiment.getId(), lives, by, reason,
				CampaignClock.now().toString()));
		String text = (lives >= 0 ? "added " : "removed ") + Math.abs(lives) + " wounded lives on "
				+ faction.getId() + " " + regiment.getId();
		return finishEdit(war, by, reason, text, before, ledger.factionWoundLives(), Map.of());
	}

	private static Result finishEdit(War war, String by, String reason, String text,
			Map<String, Map<String, Integer>> before, Map<String, Map<String, Integer>> after,
			Map<String, Integer> companyLives) {
		String now = CampaignClock.now().toString();
		war.getLedger().addNote(new WarLedger.Note(now, by, text + (reason == null ? "" : " (" + reason + ")")));
		WarManager.persist(war);
		LogManager.war("WAR_WOUNDS_EDIT war=%s by=%s: %s reason=%s", war.getId(), by, text, reason);
		List<String> lines = new ArrayList<>();
		lines.add("§aWar " + war.getId() + ": " + text + ".");
		lines.addAll(slotChanges(before, after));
		for (Map.Entry<String, Integer> company : companyLives.entrySet()) {
			if (company.getValue() != 0) {
				lines.add("§7Company " + company.getKey() + ": " + signed(company.getValue()) + " wounded lives");
			}
		}
		if (lines.size() == 1) {
			lines.add("§7No wounded slots changed.");
		}
		return new Result(true, lines);
	}

	/** Lines naming each regiment whose wounded slots in this war went up or down. */
	static List<String> slotChanges(Map<String, Map<String, Integer>> before, Map<String, Map<String, Integer>> after) {
		Set<String> factions = new LinkedHashSet<>(before.keySet());
		factions.addAll(after.keySet());
		List<String> lines = new ArrayList<>();
		for (String faction : factions) {
			Set<String> regiments = new LinkedHashSet<>(before.getOrDefault(faction, Map.of()).keySet());
			regiments.addAll(after.getOrDefault(faction, Map.of()).keySet());
			for (String regiment : regiments) {
				int was = slots(faction, regiment, before.getOrDefault(faction, Map.of()).getOrDefault(regiment, 0));
				int now = slots(faction, regiment, after.getOrDefault(faction, Map.of()).getOrDefault(regiment, 0));
				if (was != now) {
					lines.add("§7" + faction + " " + regiment + ": " + was + " -> " + now + " wounded slots");
				}
			}
		}
		return lines;
	}

	/**
	 * Applies the war's wounds once, when it ends: each regiment loses its wounded paid slots. The
	 * ledger is marked settled and saved first, so a crash can lose a settlement but never repeat it.
	 */
	public static List<String> settle(War war) {
		WarLedger ledger = war.getLedger();
		List<String> lines = new ArrayList<>();
		if (ledger.isSettled()) {
			return lines;
		}
		Map<String, Map<String, Integer>> wounds = ledger.factionWoundLives();
		ledger.markSettled();
		WarManager.persist(war);
		for (Map.Entry<String, Map<String, Integer>> factionWounds : wounds.entrySet()) {
			Faction faction = FactionManager.getByString(factionWounds.getKey());
			if (faction == null || faction.getMilitary() == null) {
				continue;
			}
			boolean changed = false;
			for (Map.Entry<String, Integer> regimentWounds : factionWounds.getValue().entrySet()) {
				Regiment regiment = faction.getMilitary().getRegiment(regimentWounds.getKey());
				int lost = regiment == null ? 0 : Math.min(regiment.getPaidSlots(), regimentWounds.getValue() / deathsPerSlot());
				if (lost > 0) {
					regiment.setCurrentSlots(regiment.getCurrentSlots() - lost);
					changed = true;
					lines.add(faction.getId() + " " + regiment.getId() + " -" + lost);
				}
			}
			if (changed) {
				new Database().saveFaction(faction);
			}
		}
		LogManager.war("WAR_WOUNDS_SETTLED war=%s lost=%s", war.getId(), lines);
		return lines;
	}

	/** What {@link #settle} would take now, per faction and regiment, without changing anything. */
	public static List<String> settlementPreview(War war) {
		List<String> lines = new ArrayList<>();
		for (Map.Entry<String, Map<String, Integer>> factionWounds : war.getLedger().factionWoundLives().entrySet()) {
			Faction faction = FactionManager.getByString(factionWounds.getKey());
			for (Map.Entry<String, Integer> regimentWounds : factionWounds.getValue().entrySet()) {
				Regiment regiment = faction == null || faction.getMilitary() == null ? null
						: faction.getMilitary().getRegiment(regimentWounds.getKey());
				int slots = regimentWounds.getValue() / deathsPerSlot();
				int lost = regiment == null ? 0 : Math.min(regiment.getPaidSlots(), slots);
				lines.add(factionWounds.getKey() + " " + regimentWounds.getKey() + ": " + regimentWounds.getValue()
						+ " lives = " + slots + " slots, loses " + lost);
			}
		}
		return lines;
	}

	public static MercenaryCompany company(String guildId) {
		Guild guild = guildId == null ? null : FactionManager.getGuildByString(guildId);
		return guild == null ? null : guild.getCompany();
	}

	/** Sets a company's wounded lives as staff, kept between 0 and its size. */
	public static Result setCompanyWounds(MercenaryCompany company, int lives, String by) {
		Instant now = CampaignClock.now();
		int before = company.getWounds().lives(now, company.getSlots());
		company.getWounds().set(lives, now, company.getSlots());
		int after = company.getWounds().lives(now, company.getSlots());
		saveCompany(company);
		LogManager.war("WAR_WOUNDS_COMPANY company=%s by=%s %s -> %s", company.getGuild().getId(), by, before, after);
		return new Result(true, List.of("§aCompany " + company.getName() + ": wounded lives " + before + " -> " + after
				+ " (" + company.getWounds().woundedSlots(now, company.getSlots()) + " of " + company.getSlots()
				+ " slots wounded)."));
	}

	private static void addTally(BattleCasualtyTally tally, String side, WarLedger.SideRecord record,
			Map<String, Map<String, Integer>> before, Map<String, Map<String, Integer>> after) {
		Set<String> seen = new LinkedHashSet<>();
		for (Wound wound : record.wounds()) {
			String faction = WarLedger.key(wound.ownerId());
			String regiment = WarLedger.key(wound.regimentId());
			if (wound.kind() != ForceKind.MERCENARY && seen.add(faction + "|" + regiment)) {
				int was = slots(faction, regiment, before.getOrDefault(faction, Map.of()).getOrDefault(regiment, 0));
				int now = slots(faction, regiment, after.getOrDefault(faction, Map.of()).getOrDefault(regiment, 0));
				Faction owner = FactionManager.getByString(wound.ownerId());
				Regiment found = owner == null || owner.getMilitary() == null ? null
						: owner.getMilitary().getRegiment(wound.regimentId());
				if (now > was && found != null) {
					tally.add(owner, side, found, now - was);
				}
			}
		}
	}

	/** Wounded lives as slots, never more than the regiment's paid slots when it can be found. */
	static int slots(String factionId, String regimentId, int lives) {
		Faction faction = FactionManager.getByString(factionId);
		Regiment regiment = faction == null || faction.getMilitary() == null ? null
				: faction.getMilitary().getRegiment(regimentId);
		int slots = lives / deathsPerSlot();
		return regiment == null ? slots : Math.min(slots, regiment.getPaidSlots());
	}

	private static void addCompanyLives(Map<String, Integer> companyLives, List<Wound> wounds, int sign) {
		for (Wound wound : wounds) {
			if (wound.kind() == ForceKind.MERCENARY) {
				companyLives.merge(wound.ownerId(), sign * wound.lives(), Integer::sum);
			}
		}
	}

	private static void applyCompanyLives(Map<String, Integer> companyLives) {
		Instant now = CampaignClock.now();
		for (Map.Entry<String, Integer> entry : companyLives.entrySet()) {
			MercenaryCompany company = company(entry.getKey());
			if (company != null && entry.getValue() != 0) {
				company.getWounds().add(entry.getValue(), now, company.getSlots());
				saveCompany(company);
			}
		}
	}

	private static void saveCompany(MercenaryCompany company) {
		Faction host = company.getGuild().getFaction();
		if (host != null) {
			new Database().saveFaction(host);
		}
	}

	private static String signed(int value) {
		return value > 0 ? "+" + value : String.valueOf(value);
	}
}
