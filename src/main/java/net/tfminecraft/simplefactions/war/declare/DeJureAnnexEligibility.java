package net.tfminecraft.simplefactions.war.declare;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.objects.handler.ProvinceHandler;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.settlement.handler.SettlementHandler;
import net.tfminecraft.simplefactions.tiers.Title;

public final class DeJureAnnexEligibility {
	private DeJureAnnexEligibility() {}

	public record DeJureTitleOption(Title title, boolean eligible, String blockReason, DeJureAnnexPreview preview) {
		public DeJureTitleOption(Title title, boolean eligible, String blockReason) {
			this(title, eligible, blockReason, null);
		}
	}

	/** What a victory would move: {@code taken} of {@code incoming} provinces, the rest kept by their holders. */
	/** {@code otherKept}: provinces held back only so the holder's land stays legally connected. */
	public record DeJureAnnexPreview(
			int taken, int incoming, List<KeptSettlement> keptSettlements, int roadSize, int otherKept) {}

	public record KeptSettlement(String holderName, String settlementName) {}

	public static List<DeJureTitleOption> options(Faction attacker, Faction defender) {
		List<DeJureTitleOption> options = new ArrayList<>();
		if (attacker == null || defender == null) {
			return options;
		}
		for (Title title : TitleLoader.getTitles()) {
			if (title == null) {
				continue;
			}
			if (incomingLandCount(attacker, defender, title) < 1) {
				continue;
			}
			options.add(evaluate(attacker, defender, title));
		}
		return options;
	}

	public static DeJureTitleOption evaluate(Faction attacker, Faction defender, Title title) {
		if (attacker == null || defender == null || title == null || title.getId() == null || title.getId().isBlank()) {
			return new DeJureTitleOption(title, false, "§cThat title does not exist.");
		}

		int incoming = incomingLandCount(attacker, defender, title);
		if (incoming < 1) {
			return blocked(title, "§cThat faction holds no land in this title.");
		}

		int attackerTier = attacker.getTier() != null ? attacker.getTier().getTier() : 0;
		int titleTier = title.getTier() != null ? title.getTier().getTier() : 0;
		if (!WarGoalValidator.canAnnexByRank(attackerTier, titleTier)) {
			return blocked(title, "§cYou cannot de jure annex a title above your rank.");
		}

		if (!attackerOwnsTitle(attacker, title)) {
			Faction owner = TitleManager.getOwner(title);
			if (owner != null) {
				return blocked(title, "§cYou do not own this title.");
			}
			if (!attackerOwnsProvinceInTitle(attacker, title)) {
				return blocked(title, "§cYou do not own a province in this unowned title.");
			}
		}

		AnnexPartition.Result partition = partition(attacker, defender, title);
		DeJureAnnexPreview preview = preview(attacker, defender, title, partition);
		if (partition.taken().isEmpty()) {
			return new DeJureTitleOption(title, false,
					"§cNothing in this title can be taken: it is all settlements or the road to them.", preview);
		}

		if (overProjectedProvinceCap(attacker, partition.taken().size())) {
			return new DeJureTitleOption(title, false,
					"§cYou do not have enough prestige for the incoming provinces.", preview);
		}

		return new DeJureTitleOption(title, true, null, preview);
	}

	/**
	 * Splits the defender realm's land in {@code title}: holders keep settlements, capitals
	 * and the road that keeps their other land claimed; the attacker takes the rest.
	 */
	public static AnnexPartition.Result partition(Faction attacker, Faction defender, Title title) {
		Map<Faction, Set<Integer>> byHolder = incomingByHolder(attacker, defender, title);
		List<AnnexPartition.Holder> holders = new ArrayList<>();
		for (Map.Entry<Faction, Set<Integer>> entry : byHolder.entrySet()) {
			Faction holder = entry.getKey();
			Set<Integer> incoming = entry.getValue();
			Set<Integer> holdings = new HashSet<>(incoming);
			if (holder.getProvinces() != null) {
				holdings.addAll(holder.getProvinces());
			}
			holders.add(new AnnexPartition.Holder(
					String.valueOf(holder.getId()), holdings, incoming, protectedProvinces(holder, incoming),
					holder.getCapital(), guildCapitals(holder), owned -> previewLegal(holder, owned)));
		}
		Set<Integer> titleProvinces = new HashSet<>();
		List<Integer> listed = TitleManager.getProvinces(title);
		if (listed != null) {
			for (Integer provinceId : listed) {
				if (provinceId != null) {
					titleProvinces.add(provinceId);
				}
			}
		}
		Set<Integer> attackerLand = attacker.getProvinces() == null
				? Set.of() : new HashSet<>(attacker.getProvinces());
		return AnnexPartition.compute(new ProvinceAnnexGraph(provinceManager()), titleProvinces, attackerLand, holders);
	}

	private static DeJureAnnexPreview preview(
			Faction attacker, Faction defender, Title title, AnnexPartition.Result partition) {
		List<KeptSettlement> kept = new ArrayList<>();
		for (Faction holder : incomingByHolder(attacker, defender, title).keySet()) {
			List<Settlement> settlements = settlementsOf(holder);
			settlements.sort((a, b) -> Integer.compare(a.getCenterProvince(), b.getCenterProvince()));
			for (Settlement settlement : settlements) {
				if (settlementProvinces(settlement).stream().anyMatch(partition.protectedProvinces()::contains)) {
					kept.add(new KeptSettlement(holder.getName(), settlement.getName()));
				}
			}
		}
		return new DeJureAnnexPreview(
				partition.taken().size(), partition.incoming(), List.copyOf(kept), partition.road().size(),
				partition.kept().size() - partition.protectedProvinces().size() - partition.road().size());
	}

	private static Map<Faction, Set<Integer>> incomingByHolder(Faction attacker, Faction defender, Title title) {
		Map<Faction, Set<Integer>> byHolder = new LinkedHashMap<>();
		for (Integer provinceId : incomingProvinces(attacker, defender, title)) {
			Faction holder = ownerOfProvince(provinceId);
			if (holder != null) {
				byHolder.computeIfAbsent(holder, h -> new TreeSet<>()).add(provinceId);
			}
		}
		return byHolder;
	}

	/** The holder's settlements and capital inside the title; the annex never takes these. */
	private static Set<Integer> protectedProvinces(Faction holder, Set<Integer> incoming) {
		Set<Integer> protectedProvinces = new TreeSet<>();
		if (incoming.contains(holder.getCapital())) {
			protectedProvinces.add(holder.getCapital());
		}
		for (Settlement settlement : settlementsOf(holder)) {
			for (int provinceId : settlementProvinces(settlement)) {
				if (incoming.contains(provinceId)) {
					protectedProvinces.add(provinceId);
				}
			}
		}
		return protectedProvinces;
	}

	private static Set<Integer> guildCapitals(Faction holder) {
		Set<Integer> capitals = new TreeSet<>();
		GuildHandler guilds = holder.getGuildHandler();
		for (Guild guild : guilds == null ? List.<Guild>of() : guilds.getGuilds()) {
			if (guild.hasCapital()) {
				capitals.add(guild.getCapital());
			}
		}
		return capitals;
	}

	private static List<Settlement> settlementsOf(Faction holder) {
		SettlementHandler handler = holder.getSettlementHandler();
		return handler == null ? new ArrayList<>() : new ArrayList<>(handler.getAll());
	}

	private static Set<Integer> settlementProvinces(Settlement settlement) {
		Set<Integer> provinces = new TreeSet<>();
		if (settlement.getProvinces() != null) {
			provinces.addAll(settlement.getProvinces());
		}
		provinces.add(settlement.getCenterProvince());
		return provinces;
	}

	/** With no province map or handler there is nothing to keep connected, so nothing must stay legal. */
	private static Set<Integer> previewLegal(Faction holder, Set<Integer> owned) {
		ProvinceHandler handler = holder.getProvinceHandler();
		if (handler == null || provinceManager() == null) {
			return Set.of();
		}
		Set<Integer> legal = handler.previewLegalProvinces(owned);
		return legal == null ? Set.of() : legal;
	}

	private static ProvinceManager provinceManager() {
		SimpleFactions plugin = SimpleFactions.getInstance();
		return plugin == null ? null : plugin.getProvinceManager();
	}

	static int incomingLandCount(Faction attacker, Faction defender, Title title) {
		return incomingProvinces(attacker, defender, title).size();
	}

	public static List<Integer> incomingProvinces(Faction attacker, Faction defender, Title title) {
		List<Integer> incoming = new ArrayList<>();
		if (attacker == null || defender == null || title == null) {
			return incoming;
		}
		List<Integer> titleProvinces = TitleManager.getProvinces(title);
		if (titleProvinces == null || titleProvinces.isEmpty()) {
			return incoming;
		}
		for (Integer provinceId : titleProvinces) {
			if (provinceId == null) {
				continue;
			}
			Faction holder = ownerOfProvince(provinceId);
			if (holder != null && inDefenderRealm(holder, defender) && !holder.getId().equalsIgnoreCase(attacker.getId())) {
				incoming.add(provinceId);
			}
		}
		return incoming;
	}

	private static DeJureTitleOption blocked(Title title, String reason) {
		return new DeJureTitleOption(title, false, reason);
	}

	private static boolean attackerOwnsTitle(Faction attacker, Title title) {
		List<Title> titles = attacker.getTitles();
		for (Title held : titles) {
			if (held != null && held.getId() != null && held.getId().equalsIgnoreCase(title.getId())) {
				return true;
			}
		}
		return false;
	}

	private static boolean attackerOwnsProvinceInTitle(Faction attacker, Title title) {
		List<Integer> attackerProvinces = attacker.getProvinces();
		List<Integer> titleProvinces = TitleManager.getProvinces(title);
		for (Integer provinceId : titleProvinces) {
			if (provinceId != null && attackerProvinces.contains(provinceId)) {
				return true;
			}
		}
		return false;
	}

	static boolean inDefenderRealm(Faction holder, Faction defender) {
		if (holder.getId().equalsIgnoreCase(defender.getId())) {
			return true;
		}
		return RelationManager.isOnOverlordPath(holder, defender);
	}

	public static Faction ownerOfProvince(int provinceId) {
		for (Faction faction : FactionManager.factions) {
			if (faction == null) {
				continue;
			}
			List<Integer> provinces = faction.getProvinces();
			if (provinces != null && provinces.contains(provinceId)) {
				return faction;
			}
		}
		return null;
	}

	private static boolean overProjectedProvinceCap(Faction attacker, int incoming) {
		List<Integer> provinces = attacker.getProvinces();
		int current = provinces == null ? 0 : provinces.size();
		Double prestige = attacker.getPrestige();
		double prestigeValue = prestige == null ? 0 : prestige;
		return prestigeValue < Math.max(0, current + incoming - 1) * Cache.provinceCost;
	}
}
