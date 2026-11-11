package net.tfminecraft.simplefactions.war.battle.military;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCapabilityService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.wounds.BattleForce;
import net.tfminecraft.simplefactions.war.wounds.ForceKind;
import net.tfminecraft.simplefactions.war.wounds.ForcePart;
import net.tfminecraft.simplefactions.war.wounds.WarWounds;
import net.tfminecraft.simplefactions.war.wounds.WoundAllocator;

/**
 * What every force on one war side brings to one battle, read live from the factions and
 * companies when asked:
 * <ul>
 * <li>each fighting faction: its regiments allowed in this battle (offensive regiments to attack,
 * all of them to defend, militia only to defend its own province);</li>
 * <li>each non-fighting subject: a levy, the levy share of all its regiments including militia,
 * lent to its nearest fighting overlord for any battle; when the battle is in the subject's own
 * province, its whole militia defends instead;</li>
 * <li>each hired company: the members on the roster, up to the contract and its healthy slots.</li>
 * </ul>
 * Wounded paid slots bring nothing. Law slots always come in full.
 */
public final class BattleForces {
	public static final String MILITIA_REGIMENT_ID = "militia";

	private BattleForces() {}

	public static PoolMode resolvePoolMode(War war, Side side) {
		if (war == null || side == null) {
			return PoolMode.DEFENSIVE;
		}
		CampaignSideRole offensive = CampaignCapabilityService.battleOffensiveCoalition(war);
		CampaignSideRole sideCoalition = CampaignSideService.coalitionOf(war, side);
		if (offensive == null || sideCoalition == null) {
			return PoolMode.DEFENSIVE;
		}
		return sideCoalition == offensive ? PoolMode.OFFENSIVE : PoolMode.DEFENSIVE;
	}

	/** Forces for a battle side in the war's current offensive; mercenaries only with a roster. */
	public static List<BattleForce> build(War war, Integer provinceId, Side side, Battle battle, BattleSide roster) {
		return build(war, provinceId, side, resolvePoolMode(war, side), battle, roster);
	}

	public static List<BattleForce> build(
			War war,
			Integer provinceId,
			Side side,
			PoolMode mode,
			Battle battle,
			BattleSide roster) {
		List<BattleForce> forces = new ArrayList<>();
		if (war == null || side == null || mode == null) {
			return forces;
		}
		int livesPerSlot = Math.max(0, Cache.warBattleLivesPerRegiment);
		List<Faction> fighters = BattleSideMembers.collectParticipatingFactions(side);
		Set<String> fighterIds = new LinkedHashSet<>();
		for (Faction fighter : fighters) {
			fighterIds.add(key(fighter.getId()));
		}
		boolean civilWar = CivilWarBorderLock.isCivilWar(war);
		for (Faction fighter : fighters) {
			List<ForcePart> parts = new ArrayList<>();
			for (Regiment regiment : soldierRegiments(fighter)) {
				if (isEligible(civilWar, regiment, fighter, provinceId, mode)) {
					addPart(parts, part(regiment, WarWounds.availablePaidSlots(fighter, regiment)));
				}
			}
			addForce(forces, ForceKind.REGIMENTS, fighter.getId(), livesPerSlot, parts);
		}
		Set<Faction> sources = new LinkedHashSet<>();
		for (Faction fighter : fighters) {
			collectNonFighterDescendants(fighter, fighterIds, sources);
		}
		for (Faction source : sources) {
			Faction holder = findNearestFighterHolder(source, fighterIds);
			if (holder == null) {
				continue;
			}
			boolean home = defendsHome(civilWar, source, provinceId, mode);
			if (home) {
				Regiment militia = source.getMilitary().getRegiment(MILITIA_REGIMENT_ID);
				List<ForcePart> militiaParts = new ArrayList<>();
				if (militia != null) {
					addPart(militiaParts, part(militia, WarWounds.availablePaidSlots(source, militia)));
				}
				addForce(forces, ForceKind.MILITIA, source.getId(), livesPerSlot, militiaParts);
			}
			addForce(forces, ForceKind.LEVY, source.getId(), livesPerSlot, levyParts(source, holder, home));
		}
		if (roster != null) {
			addMercenaries(forces, war, side, battle, roster, livesPerSlot);
		}
		return forces;
	}

	/** Slots the side's own forces bring (no mercenaries), for whether it can attack or defend. */
	public static int regimentSlots(War war, Integer provinceId, Side side, PoolMode mode) {
		return BattleForce.regimentSlots(build(war, provinceId, side, mode, null, null));
	}

	public static boolean isMilitiaEligible(Faction faction, Integer provinceId) {
		if (faction == null || faction.getId() == null || provinceId == null) {
			return false;
		}
		Faction owner = TitleManager.getByProvince(provinceId);
		return owner != null && owner.getId().equalsIgnoreCase(faction.getId());
	}

	private static boolean isEligible(boolean civilWar, Regiment regiment, Faction faction, Integer provinceId, PoolMode mode) {
		if (civilWar) {
			return true;
		}
		if (MILITIA_REGIMENT_ID.equalsIgnoreCase(regiment.getId())) {
			return mode == PoolMode.DEFENSIVE && isMilitiaEligible(faction, provinceId);
		}
		// Defence brings every regiment type; an attack only the offensive ones.
		return mode == PoolMode.DEFENSIVE || regiment.isOffensive();
	}

	private static boolean defendsHome(boolean civilWar, Faction source, Integer provinceId, PoolMode mode) {
		return (civilWar || mode == PoolMode.DEFENSIVE) && isMilitiaEligible(source, provinceId);
	}

	/**
	 * The levy: its levy % of every available regiment (capped at its active members), then each
	 * overlord between it and the holder takes its own % of that again. Militia is included unless
	 * it is already defending its home.
	 */
	private static List<ForcePart> levyParts(Faction source, Faction holder, boolean withoutMilitia) {
		int memberCap = source.getMembers() == null ? 0 : InactivityService.activeOnly(source.getMembers()).size();
		double levyPercent = source.getModifier(FactionModifiers.LEVY).getAmount();
		Map<Regiment, Integer> counts = new LinkedHashMap<>();
		Map<Regiment, int[]> available = new HashMap<>();
		int total = 0;
		for (Regiment regiment : soldierRegiments(source)) {
			if (withoutMilitia && MILITIA_REGIMENT_ID.equalsIgnoreCase(regiment.getId())) {
				continue;
			}
			int law = lawSlots(regiment);
			int paid = WarWounds.availablePaidSlots(source, regiment);
			int count = (int) Math.round((law + paid) * (levyPercent / 100.0));
			if (total + count >= memberCap) {
				count = memberCap - total;
			}
			if (count <= 0) {
				continue;
			}
			total += count;
			counts.put(regiment, count);
			available.put(regiment, new int[] { law, paid });
		}
		Faction current = source;
		while (!counts.isEmpty() && !sameId(current, holder)) {
			Faction overlord = FactionManager.getByString(RelationManager.getOverlord(current));
			if (sameId(overlord, holder)) {
				break;
			}
			double percent = overlord.getModifier(FactionModifiers.LEVY).getAmount();
			counts.replaceAll((regiment, count) -> (int) Math.round(count * (percent / 100.0)));
			counts.values().removeIf(count -> count <= 0);
			current = overlord;
		}
		List<ForcePart> parts = new ArrayList<>();
		for (Map.Entry<Regiment, Integer> entry : counts.entrySet()) {
			int[] slots = available.get(entry.getKey());
			long[] split = WoundAllocator.share(entry.getValue(), new long[] { slots[0], slots[1] });
			addPart(parts, new ForcePart(entry.getKey().getId(),
					MILITIA_REGIMENT_ID.equalsIgnoreCase(entry.getKey().getId()), (int) split[0], (int) split[1]));
		}
		return parts;
	}

	private static void addMercenaries(List<BattleForce> forces, War war, Side side, Battle battle, BattleSide roster,
			int livesPerSlot) {
		Instant now = CampaignClock.now();
		// What each company can still bring here: its members on the roster, so one company hired by
		// two allies is not counted twice, and its healthy slots not fighting another battle.
		Map<MercenaryCompany, Integer> left = new LinkedHashMap<>();
		for (MercenaryEngagements.Engagement engagement : MercenaryEngagements.on(war, side)) {
			MercenaryCompany company = engagement.company();
			int free = left.containsKey(company) ? left.get(company)
					: Math.min(MercenaryEngagements.membersOnRoster(company, roster),
							company.getHealthySlots(now) - slotsInOtherBattles(company, battle));
			int slots = Math.max(0, Math.min(MercenaryEngagements.coveringMembers(engagement, roster), free));
			left.put(company, free - slots);
			if (slots > 0) {
				forces.add(new BattleForce(ForceKind.MERCENARY, company.getGuild().getId(), livesPerSlot,
						List.of(new ForcePart(company.getRegiment().getId(), false, 0, slots))));
			}
		}
	}

	/** A company's healthy slots already fighting in another running battle cannot fight here too. */
	private static int slotsInOtherBattles(MercenaryCompany company, Battle battle) {
		String guildId = company.getGuild().getId();
		int slots = 0;
		for (Battle other : BattleManager.get()) {
			if (other == battle || !other.hasStarted()) {
				continue;
			}
			for (BattleSide side : other.getSides()) {
				for (BattleForce force : side.getForces()) {
					if (force.kind() == ForceKind.MERCENARY && force.ownerId().equalsIgnoreCase(guildId)) {
						slots += force.slots();
					}
				}
			}
		}
		return slots;
	}

	private static ForcePart part(Regiment regiment, int availablePaid) {
		return new ForcePart(regiment.getId(), MILITIA_REGIMENT_ID.equalsIgnoreCase(regiment.getId()),
				lawSlots(regiment), availablePaid);
	}

	private static int lawSlots(Regiment regiment) {
		return Math.min(Math.max(0, regiment.getFreeSlots()), regiment.getCurrentSlots());
	}

	private static void addPart(List<ForcePart> parts, ForcePart part) {
		if (part.slots() > 0) {
			parts.add(part);
		}
	}

	private static void addForce(List<BattleForce> forces, ForceKind kind, String ownerId, int livesPerSlot,
			List<ForcePart> parts) {
		if (!parts.isEmpty()) {
			forces.add(new BattleForce(kind, ownerId, livesPerSlot, parts));
		}
	}

	/** Regiments that are soldiers: not the levy row, equipment or a mercenary company. */
	private static List<Regiment> soldierRegiments(Faction faction) {
		List<Regiment> regiments = new ArrayList<>();
		if (faction.getMilitary() == null) {
			return regiments;
		}
		for (Regiment regiment : faction.getMilitary().getRegiments()) {
			if (!regiment.isLevy() && !regiment.isEquipment() && !regiment.isMercenary()) {
				regiments.add(regiment);
			}
		}
		return regiments;
	}

	static Faction findNearestFighterHolder(Faction source, Set<String> fighterIds) {
		Faction current = source;
		while (current != null) {
			if (fighterIds.contains(key(current.getId()))) {
				return current;
			}
			String overlordId = RelationManager.getOverlord(current);
			if (overlordId == null || overlordId.isBlank()) {
				return null;
			}
			current = FactionManager.getByString(overlordId);
		}
		return null;
	}

	private static void collectNonFighterDescendants(Faction root, Set<String> fighterIds, Set<Faction> out) {
		for (Faction subject : RelationManager.getSubjects(root)) {
			if (!fighterIds.contains(key(subject.getId()))) {
				out.add(subject);
			}
			collectNonFighterDescendants(subject, fighterIds, out);
		}
	}

	private static boolean sameId(Faction a, Faction b) {
		return a != null && b != null && a.getId().equalsIgnoreCase(b.getId());
	}

	private static String key(String id) {
		return id == null ? "" : id.toLowerCase(Locale.ROOT);
	}
}
