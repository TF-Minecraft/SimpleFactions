package me.Plugins.SimpleFactions.Map.export;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.RankLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Modifier;
import me.Plugins.SimpleFactions.Objects.PrestigeRank;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.Utils.FactionRanker;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.settlement.Settlement;

/**
 * Builds the chronicle snapshot: a point-in-time record of every stock and flow the
 * website needs to graph a season.
 *
 * Stocks are absolute; ProvinceSystem differences consecutive snapshots for deltas.
 * Flows are shipped explicitly because they cannot be recovered from stock differences,
 * and they are always the ledger projections rather than the daily accumulators, which
 * are cleared at settlement and would sawtooth across a 5 minute cadence.
 */
public final class ChronicleSnapshot {
	public static final int SCHEMA_VERSION = 1;

	private ChronicleSnapshot() {
	}

	public static JsonObject build(
			List<Faction> factions, int serverDay, int dayProgressSeconds, Instant capturedAt) {
		Map<String, List<String>> warsByFaction = warsByFaction();

		JsonObject root = new JsonObject();
		root.addProperty("schema_version", SCHEMA_VERSION);
		root.addProperty("map_id", Cache.mapRef);
		ChapterIdentity.putOnEnvelope(root);
		root.addProperty("captured_at", capturedAt.toString());
		root.addProperty("server_day", serverDay);
		root.addProperty("day_progress_seconds", dayProgressSeconds);
		// Absence of a faction only means deletion when this is true. A partial export
		// must never let the backend mark half the server dead.
		root.addProperty("complete", true);
		root.add("global", buildGlobal(factions));
		root.add("factions", buildFactions(factions, warsByFaction));
		root.add("guilds", buildGuilds(factions));
		// Reserved. The event stream is still owned outside SF.
		root.add("events", new JsonArray());
		return root;
	}

	private static JsonObject buildGlobal(List<Faction> factions) {
		int guildCount = 0;
		int claimedProvinces = 0;
		Set<String> population = new HashSet<>();
		for (Faction faction : factions) {
			guildCount += faction.getGuildHandler().getGuilds().size();
			claimedProvinces += faction.getProvinces().size();
			population.addAll(faction.getMembers());
		}

		JsonObject global = new JsonObject();
		global.addProperty("faction_count", factions.size());
		global.addProperty("guild_count", guildCount);
		global.addProperty("claimed_provinces", claimedProvinces);
		global.addProperty("population", population.size());
		global.addProperty("active_wars", activeWars().size());
		global.addProperty("max_wealth_prestige", Cache.maxWealthPrestige);

		// Kept as separate series. getGlobalWealth excludes all personal money, so total
		// money supply is a website decision rather than ours to pre-sum.
		global.addProperty("faction_wealth", safe(FactionManager::getGlobalWealth));
		global.addProperty("pouch_wealth", safe(FactionManager::getPouchWealth));
		global.addProperty("player_bank_wealth", safe(FactionManager::getBankWealth));
		global.addProperty("liquid_wealth", safe(FactionManager::getGlobalLiquidWealth));
		global.addProperty("guild_liquid_wealth", safe(FactionManager::getGuildLiquidWealth));
		global.addProperty("node_wealth", safe(FactionManager::getGlobalNodeWealth));
		global.addProperty("expansion_wealth", safe(FactionManager::getGlobalGuildExpansions));
		global.addProperty("guild_income", safe(FactionManager::getTotalGuildIncome));
		return global;
	}

	private static JsonArray buildFactions(List<Faction> factions, Map<String, List<String>> warsByFaction) {
		FactionRanker ranker = new FactionRanker();
		JsonArray rows = new JsonArray();
		for (Faction faction : factions) {
			JsonObject row = new JsonObject();
			row.addProperty("id", faction.getId());
			// Ids come from the faction name, so a recycled name reuses the id. Pairing it
			// with the founding stamp is what lets the backend tell reincarnations apart.
			row.addProperty("founded_at", faction.getFoundedAt());
			row.addProperty("name", faction.getName());
			row.addProperty("rgb", faction.getRGB());
			row.addProperty("overlord", RelationManager.getOverlord(faction));

			JsonArray subjects = new JsonArray();
			for (Faction subject : RelationManager.getSubjects(faction)) {
				if (subject != null) subjects.add(subject.getId());
			}
			row.add("subjects", subjects);

			row.addProperty("wealth", faction.getWealth());
			row.add("wealth_breakdown", wealthBreakdown(faction));
			row.addProperty("bank", faction.getBank() != null ? faction.getBank().getWealth() : 0.0);
			row.addProperty("vassal_wealth", faction.getVassalWealth());

			double netIncome = 0.0;
			double inflationDelta = 0.0;
			double tradePower = 0.0;
			for (Guild guild : faction.getGuildHandler().getGuilds()) {
				netIncome += guild.getLedger().getNetIncome();
				inflationDelta += guild.getLedger().getInflationDelta();
				tradePower += guild.getTradeBreakdown().getTradePower();
			}
			row.addProperty("net_income", netIncome);
			row.addProperty("inflation_delta", inflationDelta);
			row.addProperty("trade_power", tradePower);

			row.addProperty("prestige", faction.getPrestige());
			// The Wealth component is a share of global wealth, so prestige moves when
			// rivals get richer. Without the breakdown those dips are unexplainable.
			row.add("prestige_breakdown", breakdown(faction.getPrestigeModifiers()));

			PrestigeRank rank = faction.getRank();
			if (rank != null) {
				row.addProperty("rank", rank.getId());
				row.addProperty("rank_level", rank.getLevel());
				// Competitive thresholds, not the ranks.yml minimums. The website must draw
				// its threshold lines from these.
				PrestigeRank next = RankLoader.getByLevel(rank.getLevel() + 1);
				if (next != null) row.addProperty("rank_up_at", FactionManager.getRankUpAmount(next));
				if (rank.getLevel() != 1) {
					Double floor = FactionManager.getRankUpAmount(rank);
					if (floor != null) row.addProperty("rank_down_at", floor * 0.95);
				}
			}

			row.addProperty("prestige_position", ranker.getPrestigeRank(faction));
			row.addProperty("wealth_position", ranker.getWealthRank(faction));

			row.addProperty("provinces", faction.getProvinces().size());
			row.addProperty("realm_size", TitleManager.getRealmSize(faction));
			row.addProperty("tier", faction.getTier() != null ? faction.getTier().getId() : null);
			row.addProperty("tier_index", faction.getTier() != null ? faction.getTier().getIndex() : 0);
			Title highest = faction.getTitles().isEmpty() ? null : faction.getHighestTitle();
			row.addProperty("highest_title", highest != null ? highest.getId() : null);

			row.addProperty("members", faction.getMembers().size());
			row.addProperty("members_with_vassals", faction.getCompleteMemberList().size());

			int settlements = 0;
			int settlementPopulation = 0;
			for (Settlement settlement : faction.getSettlementHandler().getAll()) {
				settlements++;
				settlementPopulation += faction.getSettlementHandler().getPopulation(settlement).size();
			}
			row.addProperty("settlements", settlements);
			row.addProperty("population", settlementPopulation);

			int installations = 0;
			int forts = 0;
			for (Installation installation : faction.getInstallationHandler().getAll()) {
				installations++;
				if (installation.getKind() == InstallationKind.FORT) forts++;
			}
			row.addProperty("installations", installations);
			row.addProperty("forts", forts);

			JsonArray wars = new JsonArray();
			for (String warId : warsByFaction.getOrDefault(faction.getId(), List.of())) {
				wars.add(warId);
			}
			row.add("wars", wars);

			rows.add(row);
		}
		return rows;
	}

	private static JsonArray buildGuilds(List<Faction> factions) {
		JsonArray rows = new JsonArray();
		for (Faction faction : factions) {
			for (Guild guild : faction.getGuildHandler().getGuilds()) {
				JsonObject row = new JsonObject();
				row.addProperty("id", guild.getId());
				row.addProperty("faction_id", faction.getId());
				row.addProperty("name", guild.getOwnName());
				row.addProperty("type", guild.getType().getName());
				row.addProperty("wealth", guild.getWealth());
				row.addProperty("bank", guild.getBank() != null ? guild.getBank().getWealth() : 0.0);
				row.addProperty("expansions", guild.getTotalExpansionSpent());
				row.addProperty("trade_power", guild.getTradeBreakdown().getTradePower());
				row.addProperty("credit_score", guild.getLoanHandler().getCreditScore());
				row.addProperty("size", guild.getSize());
				rows.add(row);
			}
		}
		return rows;
	}

	private static JsonObject wealthBreakdown(Faction faction) {
		Guild main = faction.getOrCreateMainGuild();
		JsonObject object = breakdown(main != null ? main.getWealthModifiers() : null);
		if (faction.getGuildHandler() == null) {
			return object;
		}
		for (Guild guild : faction.getGuildHandler().getGuilds()) {
			if (guild == null || guild.isBase() || guild.getWealth() == 0) continue;
			if (guild.getId() == null) continue;
			object.addProperty(guild.getId(), guild.getWealth());
		}
		return object;
	}

	private static JsonObject breakdown(List<Modifier> modifiers) {
		JsonObject object = new JsonObject();
		if (modifiers != null) {
			for (Modifier modifier : modifiers) {
				if (modifier != null && modifier.getType() != null) {
					object.addProperty(modifier.getType(), modifier.getAmount());
				}
			}
		}
		return object;
	}

	private static Map<String, List<String>> warsByFaction() {
		Map<String, List<String>> byFaction = new HashMap<>();
		for (War war : activeWars()) {
			String warId = String.valueOf(war.getId());
			for (String factionId : belligerentIds(war)) {
				byFaction.computeIfAbsent(factionId, k -> new ArrayList<>()).add(warId);
			}
		}
		return byFaction;
	}

	private static Set<String> belligerentIds(War war) {
		Set<String> ids = new LinkedHashSet<>();
		collect(war.getAttackers(), ids);
		collect(war.getDefenders(), ids);
		return ids;
	}

	private static void collect(Side side, Set<String> ids) {
		if (side == null) {
			return;
		}
		for (Participant participant : side.getMainParticipants()) {
			for (Faction faction : participant.getAllParticipatingFactions()) {
				if (faction != null && faction.getId() != null) ids.add(faction.getId());
			}
		}
	}

	private static List<War> activeWars() {
		try {
			List<War> active = WarManager.getActive();
			return active != null ? active : List.of();
		} catch (Throwable t) {
			return List.of();
		}
	}

	/**
	 * The economy aggregates reach into DenarEconomy. A broken or absent economy plugin
	 * should degrade one number, not take the whole snapshot down.
	 */
	private static double safe(Supplier<Double> supplier) {
		try {
			Double value = supplier.get();
			return value != null ? value : 0.0;
		} catch (Throwable t) {
			return 0.0;
		}
	}
}
