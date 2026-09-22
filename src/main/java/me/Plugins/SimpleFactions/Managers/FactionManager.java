package me.Plugins.SimpleFactions.Managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Database.Database;
import me.Plugins.SimpleFactions.Database.LoanData;
import me.Plugins.SimpleFactions.Diplomacy.Attitude;
import me.Plugins.SimpleFactions.Diplomacy.DiplomacyHandler;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.Loan;
import me.Plugins.SimpleFactions.Loaders.RankLoader;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Map.MapSystem;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Modifier;
import me.Plugins.SimpleFactions.Objects.PrestigeRank;
import me.Plugins.SimpleFactions.Objects.Request.ElevateRequest;
import me.Plugins.SimpleFactions.Objects.Request.MovementJoinRequest;
import me.Plugins.SimpleFactions.Objects.Request.MovementLeaderTargetRequest;
import me.Plugins.SimpleFactions.Objects.Request.RelationRequest;
import me.Plugins.SimpleFactions.Objects.Request.RelocateRequest;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.resolution.WarReparationsService;
import me.Plugins.SimpleFactions.Utils.DailyGuildTransfers;
import me.Plugins.SimpleFactions.Utils.FactionCleanup;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.Utils.PostSettlementPayouts;
import me.Plugins.SimpleFactions.player.PlayerEconomyManager;
import me.Plugins.SimpleFactions.vehicles.maintenance.DenarEconomyPlayerBank;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.TLibs.TLibs;
import net.tfminecraft.DenarEconomy.DenarEconomy;
import net.tfminecraft.DenarEconomy.Enum.Accounts;

public class FactionManager implements Listener{
	public static int timer = 0;
	/** Completed day rollovers. Counts server uptime, not calendar days. */
	public static int day = 0;
	private static boolean loaded = false;
	/** True while Database.loadFactions is populating the list. See updateAllPrestige. */
	public static boolean loading = false;

	public static boolean isLoaded() {
		return loaded;
	}

	public static List<Faction> factions = new ArrayList<Faction>();
	
	private static HashMap<Faction, List<String>> dbRelations = new HashMap<>();
	private static HashMap<Faction, List<String>> dbTradeRelations = new HashMap<>();
	private static HashMap<Faction, List<String>> dbTreatyRelations = new HashMap<>();
	private static List<LoanData> loans = new ArrayList<>();

	public static int getTimer(){
		return timer;
	}

	public static int getDay(){
		return day;
	}
	
	public static void addDBRelation(Faction f, String s) {
		List<String> list = new ArrayList<>();
		if(dbRelations.containsKey(f)) {
			list = dbRelations.get(f);
		}
		list.add(s);
		dbRelations.put(f, list);
		LogManager.relations("QUEUE %s %s", factionId(f), s);
	}

	public static void addDBTradeRelation(Faction f, String s) {
		List<String> list = new ArrayList<>();
		if(dbTradeRelations.containsKey(f)) {
			list = dbTradeRelations.get(f);
		}
		list.add(s);
		dbTradeRelations.put(f, list);
		LogManager.relations("QUEUE trade %s %s", factionId(f), s);
	}

	public static void addDBTreatyRelation(Faction f, String s) {
		List<String> list = new ArrayList<>();
		if(dbTreatyRelations.containsKey(f)) {
			list = dbTreatyRelations.get(f);
		}
		list.add(s);
		dbTreatyRelations.put(f, list);
		LogManager.relations("QUEUE treaty %s %s", factionId(f), s);
	}

	public static void addDBLoan(LoanData data) {
		loans.add(data);
	}

	public static void loadDBLoans() {
		for(LoanData data : loans) {
			Loan loan = new Loan(data);
			loan.getIssuer().getLoanHandler().issueLoan(loan);
		}
		loans.clear();
	}
	
	public static void loadRelations() {
		LogManager.relations("loadRelations start factions=%d queued=%d tradeQueued=%d treatyQueued=%d",
				factions.size(), dbRelations.size(), dbTradeRelations.size(), dbTreatyRelations.size());
		for(Map.Entry<Faction, List<String>> entry : dbRelations.entrySet()) {
			Faction f = entry.getKey();
			List<String> relations = entry.getValue();
			LogManager.relations("load %s rawCount=%d", factionId(f), relations == null ? 0 : relations.size());
			if (relations == null) {
				continue;
			}
			for(String s : relations) {
				applyStoredRelation(f, s);
			}
		}
		for(Map.Entry<Faction, List<String>> entry : dbTradeRelations.entrySet()) {
			Faction f = entry.getKey();
			List<String> relations = entry.getValue();
			LogManager.relations("loadTrade %s rawCount=%d", factionId(f), relations == null ? 0 : relations.size());
			if (relations == null) {
				continue;
			}
			for(String s : relations) {
				applyStoredTradeRelation(f, s);
			}
		}
		for(Map.Entry<Faction, List<String>> entry : dbTreatyRelations.entrySet()) {
			Faction f = entry.getKey();
			List<String> relations = entry.getValue();
			LogManager.relations("loadTreaty %s rawCount=%d", factionId(f), relations == null ? 0 : relations.size());
			if (relations == null) {
				continue;
			}
			for(String s : relations) {
				applyStoredTreatyRelation(f, s);
			}
		}
		for (Faction f : factions) {
			logRelationSnapshot("afterLoad", f);
		}
		dbRelations.clear();
		dbTradeRelations.clear();
		dbTreatyRelations.clear();
		LogManager.relations("loadRelations done");
	}

	private static void applyStoredRelation(Faction f, String s) {
		try {
			if (s == null || !s.contains("(")) {
				LogManager.relations("SKIP %s malformed '%s'", factionId(f), s);
				return;
			}
			String targetId = s.split("\\(")[0];
			Faction target = getByString(targetId);
			if(target == null) {
				LogManager.relations("SKIP %s -> %s targetMissing raw='%s'", factionId(f), targetId, s);
				return;
			}
			if(target.getId().equalsIgnoreCase(f.getId())) {
				LogManager.relations("SKIP %s self raw='%s'", factionId(f), s);
				return;
			}
			String info = s.split("\\(")[1].replace(")", "");
			String[] parts = info.split("\\.");
			if (parts.length < 3) {
				LogManager.relations("SKIP %s -> %s badParts '%s'", factionId(f), targetId, s);
				return;
			}
			RelationType r = RelationLoader.getType(parts[0]);
			Attitude a = RelationLoader.getAttitude(parts[1]);
			if(r == null || a == null) {
				LogManager.relations("SKIP %s -> %s type=%s attitude=%s raw='%s'",
						factionId(f), targetId, parts[0], parts[1], s);
				return;
			}
			int opinion = Integer.parseInt(parts[2]);
			boolean reverseWasDefault = target.getRelation(f.getId()).isDefault();
			f.setRelation(target, new Relation(r, a, opinion));
			LogManager.relations("APPLY %s -> %s %s.%s.%d", factionId(f), target.getId(), r.getId(), a.getId(), opinion);
			if(reverseWasDefault && r.isVassalage()) {
				target.setRelation(f, new Relation(r.getLink(), RelationLoader.getDefaultAttitude(), 0));
				LogManager.relations("REVERSE-FILL %s -> %s %s (was default)",
						target.getId(), factionId(f), r.getLink() == null ? "null" : r.getLink().getId());
			} else if (r.isVassalage()) {
				Relation reverse = target.getRelation(f.getId());
				LogManager.relations("NO-REVERSE-FILL %s -> %s existing=%s default=%s",
						target.getId(),
						factionId(f),
						describeRelation(reverse),
						reverse.isDefault());
			}
		} catch (Exception exception) {
			LogManager.relations("ERROR %s raw='%s' %s", factionId(f), s, exception.getMessage());
		}
	}

	private static void applyStoredTradeRelation(Faction f, String s) {
		try {
			if (s == null || !s.contains("(")) {
				LogManager.relations("SKIP trade %s malformed '%s'", factionId(f), s);
				return;
			}
			String targetId = s.split("\\(")[0];
			Faction target = getByString(targetId);
			if(target == null) {
				LogManager.relations("SKIP trade %s -> %s targetMissing", factionId(f), targetId);
				return;
			}
			if(target.getId().equalsIgnoreCase(f.getId())) {
				return;
			}
			String info = s.split("\\(")[1].replace(")", "");
			RelationType r = RelationLoader.getType(info);
			if(r == null) {
				LogManager.relations("SKIP trade %s -> %s type=%s", factionId(f), targetId, info);
				return;
			}
			f.getDiplomacyHandler().setTradeRelation(target, r);
			LogManager.relations("APPLY trade %s -> %s %s", factionId(f), target.getId(), r.getId());
		} catch (Exception exception) {
			LogManager.relations("ERROR trade %s raw='%s' %s", factionId(f), s, exception.getMessage());
		}
	}

	private static void applyStoredTreatyRelation(Faction f, String s) {
		try {
			if (s == null || !s.contains("(")) {
				LogManager.relations("SKIP treaty %s malformed '%s'", factionId(f), s);
				return;
			}
			String targetId = s.split("\\(")[0];
			Faction target = getByString(targetId);
			if(target == null) {
				LogManager.relations("SKIP treaty %s -> %s targetMissing", factionId(f), targetId);
				return;
			}
			if(target.getId().equalsIgnoreCase(f.getId())) {
				return;
			}
			String info = s.split("\\(")[1].replace(")", "");
			RelationType r = RelationLoader.getType(info);
			if(r == null || !r.isTreaty() || r.isClearTreaty()) {
				LogManager.relations("SKIP treaty %s -> %s type=%s", factionId(f), targetId, info);
				return;
			}
			f.getDiplomacyHandler().setTreatyRelation(target, r);
			LogManager.relations("APPLY treaty %s -> %s %s", factionId(f), target.getId(), r.getId());
		} catch (Exception exception) {
			LogManager.relations("ERROR treaty %s raw='%s' %s", factionId(f), s, exception.getMessage());
		}
	}

	static void logRelationSnapshot(String reason, Faction f) {
		if (f == null) {
			return;
		}
		String overlord = RelationManager.getOverlord(f);
		List<String> subjects = new ArrayList<>();
		for (Map.Entry<String, Relation> entry : f.getRelations().entrySet()) {
			Relation rel = entry.getValue();
			if (rel != null && rel.getType() != null && rel.getType().isVassalage()) {
				subjects.add(entry.getKey() + "=" + describeRelation(rel));
			}
		}
		LogManager.relations(
				"SNAPSHOT %s %s overlord=%s subjects=%s map=%s",
				reason,
				factionId(f),
				overlord,
				subjects,
				describeRelationMap(f));
	}

	static String factionId(Faction f) {
		return f == null ? "null" : f.getId();
	}

	public static String describeRelation(Relation relation) {
		if (relation == null || relation.getType() == null) {
			return "null";
		}
		String attitude = relation.getAttitude() == null ? "?" : relation.getAttitude().getId();
		return relation.getType().getId()
				+ "."
				+ attitude
				+ "."
				+ relation.getOpinion()
				+ (relation.isDefault() ? "(default)" : "");
	}

	private static String describeRelationMap(Faction f) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, Relation> entry : f.getRelations().entrySet()) {
			parts.add(entry.getKey() + "=" + describeRelation(entry.getValue()));
		}
		return parts.toString();
	}
	
	public static void reloadTitles() {
		for(Faction f : factions) {
			List<Title> newTitles = new ArrayList<>();
			for(Title t : f.getTitles()) {
				Title title = TitleLoader.getById(t.getId());
				if(title == null) continue;
				newTitles.add(title);
			}
			f.resetTitles(newTitles);
		}
	}

	public static void rebindRanks() {
		PrestigeRank fallback = RankLoader.getRanks().isEmpty() ? null : RankLoader.getLowest();
		for (Faction f : factions) {
			if (f == null) continue;
			PrestigeRank current = f.getRank();
			String id = current != null ? current.getId() : null;
			PrestigeRank rebound = id != null ? RankLoader.getByString(id) : null;
			if (rebound == null) {
				rebound = fallback;
			}
			f.setRank(rebound);
		}
	}

	public static void rebindDiplomacy() {
		RelationType defaultType = RelationLoader.getTypes().isEmpty() ? null : RelationLoader.getDefaultType();
		Attitude defaultAttitude = RelationLoader.getAttitudes().isEmpty() ? null : RelationLoader.getDefaultAttitude();
		for (Faction f : factions) {
			if (f == null || f.getDiplomacyHandler() == null) continue;
			DiplomacyHandler handler = f.getDiplomacyHandler();
			for (Relation relation : handler.getRelations().values()) {
				if (relation == null) continue;
				RelationType type = relation.getType();
				String typeId = type != null ? type.getId() : null;
				RelationType reboundType = typeId != null ? RelationLoader.getType(typeId) : null;
				relation.setType(reboundType != null ? reboundType : defaultType);
				Attitude attitude = relation.getAttitude();
				String attitudeId = attitude != null ? attitude.getId() : null;
				Attitude reboundAttitude = attitudeId != null ? RelationLoader.getAttitude(attitudeId) : null;
				relation.setAttitude(reboundAttitude != null ? reboundAttitude : defaultAttitude);
			}
			rebindRelationTypeMap(handler.getTradeRelations());
			rebindRelationTypeMap(handler.getTreatyRelations());
		}
		RequestManager.rebindRelationRequests();
	}

	private static void rebindRelationTypeMap(Map<String, RelationType> map) {
		if (map == null || map.isEmpty()) return;
		List<String> drop = new ArrayList<>();
		for (Map.Entry<String, RelationType> entry : map.entrySet()) {
			RelationType current = entry.getValue();
			if (current == null || current.getId() == null) {
				drop.add(entry.getKey());
				continue;
			}
			RelationType rebound = RelationLoader.getType(current.getId());
			if (rebound == null) {
				drop.add(entry.getKey());
			} else {
				entry.setValue(rebound);
			}
		}
		for (String key : drop) {
			map.remove(key);
		}
	}

	public void fixRelations() {
		//Allies
		for(Faction f : factions) {
			List<Faction> allies = RelationManager.getAllies(f);
			for(Faction ally : allies) {
				if(!ally.getRelation(f.getId()).getType().getId().equalsIgnoreCase("ally")) {
					Relation r = ally.getRelation(f.getId());
					LogManager.relations("PATCH-ALLY %s -> %s was=%s",
							ally.getId(), factionId(f), describeRelation(r));
					r.setType(RelationLoader.getType("ally"));
					ally.setRelation(f, r);
				}
			}
		}
	}
	
	public static Double globalWealth = 0.0;
	
	public static MapSystem map = new MapSystem();
	
	public static InventoryManager inv = new InventoryManager();
	
	public static InventoryManager getInv() {
		return inv;
	}
	
	public static MapSystem getMap() {
		return map;
	}
	
	public static Faction getTitleOwner(Title t) {
		for(Faction f : factions) {
			if(f.hasTitle(t)) return f;
		}
		return null;
	}
	
	public static Faction getByProvince(int i) {
		for(Faction f : factions) {
			if(f.getProvinces().contains(i)) return f;
		}
		return null;
	}
	
	private void tickCycle() {
		new BukkitRunnable() {
			@Override
	        public void run() {
				for(Faction f : factions) {
					f.tick();
				}
				map.tick();
				RelationManager.tick();
				inv.getUpdater().updateInventory();
				time();
	        }
	    }.runTaskTimer(SimpleFactions.plugin, 0L, 20L);
	}

	public void time() {
		timer++;
		if(timer % 3600 == 0) {
			for(Faction f : factions) {
				if (f.getGovernment() != null) {
					f.getGovernment().powerTick();
				}
				if (f.getGuildHandler() == null) {
					continue;
				}
				for(Guild g : f.getGuildHandler().getGuilds()) {
					g.tickPillageHits();
				}
			}
		}
		if(timer%300 == 0) {
			for(Faction f : factions) {
				if(f.getProvinces().size() == 0) continue;
				Player leader = Bukkit.getPlayerExact(f.getLeader());
				if(leader == null) continue;
				if(TitleManager.overProvinceCap(f)) {
					leader.sendMessage("§cYou are over your province cap! Other nations can steal your provinces from you!");
					leader.sendMessage("§cGet more prestige or unclaim provinces to counteract this!");
				}
			}
		}
		if (timer >= 86400) {
			PlayerEconomyManager.get().clearAllDaily();
			for(Faction f : factions){
				f.newDay();
			}
			FactionCleanup.kickInactiveMembers(factions);
			settleIncome();
			timer = 0;
			day++;
		}
	}

	public void settleIncome() {
		DailyGuildTransfers buffer = new DailyGuildTransfers();

		// Phase 0: contracts accrue the day and hand the hiring capital its bill, which
		// it cannot compute itself because it owns no contract object.
		me.Plugins.SimpleFactions.mercenary.contract.ContractAccrualService.accrueDailyAndPush();

		// Phase 1: collect transfers & external deltas
		for (Guild g : getAllGuilds()) {
			g.getLedger().populateDailyTransfers(buffer);
		}

		// Phase 2: compute net deltas
		Map<Guild, Double> deltas = new HashMap<>();

		// Guild -> Guild transfers
		for (var fromEntry : buffer.getTransfers().entrySet()) {
			Guild from = fromEntry.getKey();
			for (var toEntry : fromEntry.getValue().entrySet()) {
				Guild to = toEntry.getKey();
				double amount = toEntry.getValue();

				deltas.merge(from, -amount, Double::sum);
				deltas.merge(to, amount, Double::sum);
			}
		}

		// External deposits / withdrawals
		for (var entry : buffer.getExternalDeltas().entrySet()) {
			Guild guild = entry.getKey();
			double delta = entry.getValue();

			deltas.merge(guild, delta, Double::sum);
		}

		// Phase 3: apply atomically
		for (var entry : deltas.entrySet()) {
			Guild guild = entry.getKey();
			double amount = Formatter.formatDouble(entry.getValue());
			if (amount == 0.0) continue;
			if(guild.getBank() == null) continue;
			guild.getBank().deposit(amount);
			while(guild.isBankrupt() && guild.canLiquidate()) {
				guild.liquidateRandom();
			} 
		}

		PostSettlementPayouts.apply(
				buffer,
				DenarEconomyPlayerBank.INSTANCE,
				PlayerEconomyManager.get(),
				name -> Bukkit.getOfflinePlayer(name).getUniqueId());
		for (Guild g : getAllGuilds()) {
			g.refreshDividendEligibility();
		}

		for(Guild g : getAllGuilds()) {
			for(Loan loan : g.getLoanHandler().getLoansGiven()) {
				loan.tickDay();
			}
		}

		//Contracts age a day, then elapsed and bankrupt ones are closed out
		me.Plugins.SimpleFactions.mercenary.contract.ContractTerminationService.tickDay();

		for (Faction faction : factions) {
			WarReparationsService.tickAfterDailySettlement(faction);
		}

		buffer.clear();
		SimpleFactions.getInstance().getVehicleUpkeepService().processDailyUpkeep();
	}

	
	public void run() {
		Database database = new Database();
		timer = database.getTimer();
		day = database.getDay();
		LogManager.relations("FactionManager.run loadRelations");
		loadRelations();
		tickCycle();	
		for(Faction f : factions) {
			f.getLawHandler().apply();
			f.countyCheck();
			f.getGovernment().loadMovements();
			f.ping();
		}
		updateAllPrestigeConverged();
		fixRelations();
		loadDBLoans();
		loaded = true;
	}

	public static boolean guildExists(String id) {
		for(Faction f : factions) {
			for(Guild guild : f.getGuildHandler().getGuilds()) {
				if(guild.getId().equalsIgnoreCase(id)) return true;
			}
		}
		return false;
	}

	public static boolean canJoinGuild(Player p) {
		Guild guild = getGuildByMember(p.getName());
		if(guild == null) return true;
		if(guild.getLeader().equalsIgnoreCase(p.getName())) return false;
		return guild.getType().isBase();
	}

	public static Guild getGuildByMember(String player) {
		if(player == null) return null;
		for(Faction f : factions) {
			for(Guild guild : f.getGuildHandler().getGuilds()) {
				if(Guild.findIgnoreCase(guild.getMembers(), player) != null) return guild;
			}
		}
		return null;
	}

	public static Guild getGuildByLeader(String leader) {
		for(Faction f : factions) {
			for(Guild guild : f.getGuildHandler().getGuilds()) {
				if(guild.isLeader(leader)) return guild;
			}
		}
		return null;
	}

	public static Guild getGuildByString(String id) {
		for(Faction f : factions) {
			for(Guild guild : f.getGuildHandler().getGuilds()) {
				if(guild.getId().equalsIgnoreCase(id)) return guild;
			}
		}
		return null;
	}

	public static List<Guild> getAllGuilds() {
		List<Guild> guilds = new ArrayList<>();
		for(Faction f : factions) {
			guilds.addAll(f.getGuildHandler().getGuilds());
		}
		return guilds;
	}
	
	public void start(List<Faction> l) {
		factions = l;
	}
	public static void addFaction(Faction f) {
		factions.add(f);
		if (f.getProvinces() == null || f.getProvinces().isEmpty()) {
			return;
		}
		if (f.getCapital() != -1) {
			return;
		}
		Guild main = f.getOrCreateMainGuild();
		if (main == null || !main.hasCapital()) {
			return;
		}
		f.setCapital(main.getCapital(), true);
	}
	public static void deleteFaction(Faction f){
		map.enqueue("nation", f.getRGB());
		for(Faction fac : factions) {
			fac.getDiplomacyHandler().removeRelation(f.getId());
		}
		factions.remove(f);
		Database db = new Database();
		db.deleteFaction(f);
	}
	public static void updateAllPrestige() {
		// Every Faction.updateWealth ends here, and loadFactions calls updateWealth per faction
		// and per guild. Without this guard a load is O(n^2) prestige passes over a partial
		// faction set, which also lets the rank-down branch demote against a half-built ladder.
		if(loading) return;
		for(Faction f : factions) {
			f.updatePrestige();
		}
	}
	/**
	 * Rank moves at most one level per updatePrestige, and getRankUpAmount is competitive:
	 * it reads the top holder of the target rank. After a cold start nobody holds the upper
	 * ranks, so the ladder needs repeated passes to settle.
	 */
	public static void updateAllPrestigeConverged() {
		int maxPasses = Math.max(1, RankLoader.getRanks().size());
		for(int i = 0; i < maxPasses; i++) {
			Map<String, PrestigeRank> before = new HashMap<>();
			for(Faction f : factions) {
				before.put(f.getId(), f.getRank());
			}
			updateAllPrestige();
			boolean changed = false;
			for(Faction f : factions) {
				if(before.get(f.getId()) != f.getRank()) {
					changed = true;
					break;
				}
			}
			if(!changed) return;
		}
	}
	public static Double getRankUpAmount(PrestigeRank rank) {
		List<Faction> ranked = new ArrayList<Faction>();
		for(Faction f : factions) {
			if(f.getRank().getId().equalsIgnoreCase(rank.getId())) {
				ranked.add(f);
			}
		}
		if(ranked.size() < 1) {
			return rank.getMin();
		}
		Collections.sort(ranked, new Comparator<Faction>() {
		    @Override
		    public int compare(Faction c1, Faction c2) {
		        return Double.compare(c1.getPrestige(), c2.getPrestige());
		    }
		});
		Collections.reverse(ranked);
		Double amount = ranked.get(0).getPrestige()*(rank.getPercentage()/100);
		if(amount > rank.getMin()) {
			return amount;
		}
		return rank.getMin();
	}
	public static double getPouchWealth() {
		Formatter format = new Formatter();
		return format.formatDouble(DenarEconomy.getMoneyManager().getServerBal(Accounts.POUCH));
	}
	public static double getBankWealth() {
		Formatter format = new Formatter();
		return format.formatDouble(DenarEconomy.getMoneyManager().getServerBal(Accounts.BANK));
	}
	public static Double getGlobalWealth() {
		Formatter format = new Formatter();
		Double amount = 0.0;
		for(Faction f : factions) {
			amount = amount + f.getWealth();
		}
		return format.formatDouble(amount);
	}
	public static Double getGlobalLiquidWealth() {
		Formatter format = new Formatter();
		Double amount = 0.0;
		for(Faction f : factions) {
			for(Modifier m : f.getWealthModifiers()) {
				if(m.getType().equalsIgnoreCase("bank")) {
					amount = amount + m.getAmount();
				}
			}
		}
		return format.formatDouble(amount);
	}
	public static Double getTotalGuildIncome() {
		Formatter format = new Formatter();
		Double amount = 0.0;
		for(Guild g : getAllGuilds()) {
			amount+=g.getLedger().getInflationDelta();
		}
		return format.formatDouble(amount);
	}
	public static Double getGuildLiquidWealth() {
		Formatter format = new Formatter();
		Double amount = 0.0;
		for(Guild g : getAllGuilds()) {
			if(g.isBase()) continue;
			for(Modifier m : g.getWealthModifiers()) {
				if(m.getType().equalsIgnoreCase("bank")) {
					amount = amount + m.getAmount();
				}
			}
		}
		return format.formatDouble(amount);
	}
	public static Double getGlobalGuildExpansions() {
		Formatter format = new Formatter();
		Double amount = 0.0;
		for(Faction f : factions) {
			for(Guild guild : f.getGuildHandler().getGuilds()) {
				amount+=guild.getTotalExpansionSpent();
			}
		}
		return format.formatDouble(amount);
	}
	public static Double getGlobalNodeWealth() {
		Formatter format = new Formatter();
		Double amount = 0.0;
		for(Faction f : factions) {
			for(Modifier m : f.getWealthModifiers()) {
				if(m.getType().equalsIgnoreCase("nodes")) {
					amount = amount + m.getAmount();
				}
			}
		}
		return format.formatDouble(amount);
	}
	public static Faction getByString(String s) {
		for(Faction f : factions) {
			if(f.getId().equalsIgnoreCase(s)) return f;
		}
		return null;
	}
	public static List<Faction> getCopy(){
		List<Faction> c = new ArrayList<Faction>();
		for(Faction f : factions) {
			c.add(f);
		}
		return c;
	}
	public static Faction getByLeader(String name) {
		for(Faction f : factions) {
			if(f.getLeader().equalsIgnoreCase(name)) return f;
		}
		return null;
	}
	public static Faction getByMember(String name) {
		if(name == null) return null;
		for(Faction f : factions) {
			if(f.isMemberIgnoreCase(name)) return f;
			if(f.getLeader().equalsIgnoreCase(name)) {
				f.addMember(name);
				return f;
			}
		}
		return null;
	}
	
	public static Faction getByRGB(String rgb) {
		for(Faction f : factions) {
			if(f.getRGB().equalsIgnoreCase(rgb)) return f;
		}
		return null;
	}
	
	/**
	 * Validates an RGB string in the format "R,G,B"
	 * 
	 * @param rgb The input RGB string
	 * @return 0 if valid, or error code:
	 *         1 - Incorrect number of components
	 *         2 - Component is not a number
	 *         3 - Component is out of range (not between 0-255)
	 */
	public static int validateRGB(String rgb) {
	    if (rgb == null) return 1;

	    String[] parts = rgb.trim().split(",");
	    if (parts.length != 3) return 1;

	    try {
	        for (String part : parts) {
	            int value = Integer.parseInt(part.trim());
	            if (value < 0 || value > 255) return 3;
	        }
	        return 0; // All good
	    } catch (NumberFormatException e) {
	        return 2;
	    }
	}

	public static me.Plugins.SimpleFactions.government.movement.Movement getMovementById(String movementId) {
		if (movementId == null) return null;
		for (Faction f : factions) {
			me.Plugins.SimpleFactions.government.movement.Movement movement = f.getGovernment().getMovementById(movementId);
			if (movement != null) return movement;
		}
		return null;
	}

    public static Title usurp(Player p, Faction usurping, Faction losing) {
        Title t = losing.getHighestTitle();
		if(t == null){
			if(p != null) p.sendMessage("§ctarget has no titles");
			return null;
		}
		usurping.addTitle(t);
		for(Faction subject : RelationManager.getSubjects(losing)) {
			if(subject.getId().equalsIgnoreCase(usurping.getId())) continue;
			RelationManager.transferSubject(subject, usurping);
		}
		String o = RelationManager.getOverlord(losing);
		if(o != null){
			Faction overlord = FactionManager.getByString(o);
			RelationManager.endVassalage(overlord, losing, false);
			RelationManager.transferSubject(usurping, overlord);
		}
		RelationManager.endVassalage(usurping, losing, true);
		RelationManager.setRelationForced(RelationLoader.getType("subject"), losing, usurping);
		losing.removeTitle(t);
		return t;
    }

	//Guild relocation

	public static void requestRelocation(Player sender, Guild g, Faction target, int capital, String settlementName) {
		Player p = Bukkit.getPlayerExact(target.getLeader());
		if(p == null) {
			sender.sendMessage("§cTarget faction leader is not online");
			return;
		}
		p.sendMessage(g.getName()+" §7is requesting to relocate to your faction");
		p.sendMessage("§7Type §a/faction accept §7to accept");
		p.sendMessage("§7Request will time out in 60 seconds");
		sender.sendMessage("§aRelocation request sent to "+target.getName());
		RequestManager.addRequest(sender, p, new RelocateRequest(g, capital, settlementName));
	}

	public static void acceptRelocateRequest(Player p) {
		RelocateRequest req = (RelocateRequest) RequestManager.getRequest(p);
		Faction reciever = FactionManager.getByLeader(p.getName());
		if(reciever == null) {
			p.sendMessage("§cYou do not have a faction");
			return;
		}
		Guild sender = req.getSender();
		int capital = req.getNewCapital();
		if(reciever.getSettlementHandler().requiresFoundingName(capital)
				&& (req.getSettlementName() == null || req.getSettlementName().isBlank())) {
			p.sendMessage("§cA city name is required to relocate here");
			Player sp = Bukkit.getPlayerExact(sender.getLeader());
			if(sp != null && sp.isOnline()) {
				sp.sendMessage("§cRelocation failed - a city name is required at the destination");
			}
			return;
		}
		double cost = sender.getRelocationCost(capital);
		Player sp = Bukkit.getPlayerExact(sender.getLeader());
		if(sender.getBank().getWealth() < cost) {
			p.sendMessage("§c"+sender.getName()+" does not have enough funds to relocate (Cost: "+Formatter.formatDouble(cost)+"d)");
			if(sp != null && sp.isOnline()) sp.sendMessage("§cYour guild does not have enough funds to relocate (Cost: "+Formatter.formatDouble(cost)+"d)");
			return;
		}
		sender.getBank().withdraw(cost);
		if(sp != null && sp.isOnline()) sp.sendMessage(reciever.getName()+" §aaccepted your request to relocate to their faction");
		Player actor = (sp != null && sp.isOnline()) ? sp : p;
		sender.relocate(reciever, capital, actor, req.getSettlementName());
		p.sendMessage(sender.getName()+"§a has been relocated to your faction");
	}

	public static void requestElevation(Player sender, Guild target) {
		Player p = Bukkit.getPlayerExact(target.getLeader());
		if(p == null) {
			sender.sendMessage("§cTarget guild leader is not online");
			return;
		}
		p.sendMessage(target.getFaction().getName()+" §7wants to elevate your guild to a faction");
		p.sendMessage("§7Type §a/faction accept §7to accept");
		p.sendMessage("§7Request will time out in 60 seconds");
		sender.sendMessage("§aRelocation request sent to "+target.getName());
		RequestManager.addRequest(sender, p, new ElevateRequest(target));
	}

	public static void acceptElevationRequest(Player p) {
		ElevateRequest req = (ElevateRequest) RequestManager.getRequest(p);
		Guild guild = FactionManager.getGuildByLeader(p.getName());
		if(guild == null) {
			p.sendMessage("§cYou are not the leader of a guild");
			return;
		}
		Guild sender = req.getSender();
		double cost = guild.getElevationCost();
		if(guild.getFaction().getGovernment().getPower() < cost) {
			p.sendMessage("§cCannot afford to elevate");
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
			return;
		}
		Faction newFaction = guild.elevate(true);
		if(newFaction == null) {
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
			return;
		}
		sender.getFaction().getGovernment().spendPower(cost);
		p.sendMessage("§aGuild elevated to Faction!");
		p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
	}

	public static void requestMovementJoin(Player sender, Movement movement, String type, Cause cause) {
		Player p = Bukkit.getPlayerExact(movement.getLeader());
		if(p == null) {
			sender.sendMessage("§cTarget movement leader is not online");
			return;
		}
		p.sendMessage(sender.getName()+" §7wants to join your movement as a "+type);
		p.sendMessage("§7Type §a/faction accept §7to accept");
		p.sendMessage("§7Request will time out in 60 seconds");
		sender.sendMessage("§aJoin request sent to "+movement.getLeader());
		RequestManager.addRequest(sender, p, new MovementJoinRequest(null, sender.getName(), type, movement.getFaction().getId(), cause == null ? -1 : cause.getIndex()));
	}

	public static void acceptMovementJoinRequest(Player p) {
		MovementJoinRequest req = (MovementJoinRequest) RequestManager.getRequest(p);
		String sender = req.getPlayer();
		Player sp = Bukkit.getPlayerExact(sender);
		if(sp == null || !sp.isOnline()) {
			p.sendMessage("§cRequest sender is not online");
			return;
		}
		Faction f = getByString(req.getTargetFactionId());
		if(f == null) {
			p.sendMessage("§cTarget faction no longer exists");
			return;
		}
		Movement target = f.getGovernment().getMovementByLeader(p.getName());
		if(target == null) {
			p.sendMessage("§cYou are not the leader of a movement");
			return;
		}
		Object o = target.getJoiningAs(sp);
		Cause cause = null;
		if(req.getCauseIndex() != -1) {
			cause = target.getCauses().get(req.getCauseIndex());
			if(cause == null) {
				p.sendMessage("§cSpecified cause no longer exists");
				return;
			}
		}
		if ("foreign_backer".equalsIgnoreCase(req.getType())) {
			Faction fac = getByMember(sender);
			if (fac == null) {
				p.sendMessage("§cSender has no faction.");
				return;
			}
			String staffReason = target.foreignBackerBlockReason(fac, true);
			if (staffReason != null) {
				p.sendMessage(staffReason);
				sp.sendMessage(target.foreignBackerBlockReason(fac, false));
				return;
			}
			target.joinAsForeignBacker(fac);
			p.sendMessage("§a"+sender+" has joined your movement as a "+req.getType()+"!");
			return;
		}
		if(!target.canJoin(o, cause)) {
			String staffReason = o == null
					? "§cSender cannot join movement as a "+req.getType()
					: target.joinBlockReason(o, cause, true);
			p.sendMessage(staffReason != null ? staffReason : "§cSender cannot join movement as a "+req.getType());
			if (o != null) {
				String playerReason = target.joinBlockReason(o, cause, false);
				if (playerReason != null) {
					sp.sendMessage(playerReason);
				}
			}
			return;
		}
		target.join(o, cause);
		p.sendMessage("§a"+sender+" has joined your movement as a "+req.getType()+"!");
	}

	public static void requestMovementLeaderTarget(Player sender, Movement movement, Cause cause, String targetName) {
		if (sender == null || movement == null || cause == null || targetName == null) {
			return;
		}
		Player target = Bukkit.getPlayerExact(targetName);
		if (target == null || !target.isOnline()) {
			sender.sendMessage("§cThat player must be online to become the wanted leader.");
			return;
		}
		Faction host = movement.getFaction();
		if (host == null || !host.canBecomeLeader(targetName)) {
			sender.sendMessage("§c" + targetName + " cannot be the wanted leader.");
			return;
		}
		target.sendMessage(sender.getName() + " §7wants you to be the wanted leader of their movement.");
		target.sendMessage("§7Type §a/faction accept §7to accept");
		target.sendMessage("§7Request will time out in 60 seconds");
		sender.sendMessage("§aLeader request sent to " + targetName);
		RequestManager.addRequest(
				sender,
				target,
				new MovementLeaderTargetRequest(
						null,
						sender.getName(),
						movement.getId(),
						cause.getIndex(),
						targetName));
	}

	public static void acceptMovementLeaderTargetRequest(Player p) {
		MovementLeaderTargetRequest req = (MovementLeaderTargetRequest) RequestManager.getRequest(p);
		if (req == null) {
			return;
		}
		if (!p.getName().equalsIgnoreCase(req.getProposedName())) {
			p.sendMessage("§cThat request is not for you.");
			return;
		}
		Movement movement = getMovementById(req.getMovementId());
		if (movement == null || movement.isFrozen()) {
			p.sendMessage("§cThat movement is no longer available.");
			return;
		}
		if (req.getCauseIndex() < 0 || req.getCauseIndex() >= movement.getCauses().size()) {
			p.sendMessage("§cSpecified cause no longer exists");
			return;
		}
		Cause cause = movement.getCauses().get(req.getCauseIndex());
		if (cause == null || cause.getProposal() == null || !cause.getProposal().needsTarget()) {
			p.sendMessage("§cSpecified cause no longer exists");
			return;
		}
		Faction host = movement.getFaction();
		if (host == null || !host.canBecomeLeader(req.getProposedName())) {
			p.sendMessage("§cYou can no longer become the wanted leader.");
			return;
		}
		cause.getProposal().setTarget(req.getProposedName());
		new Database().saveFaction(host);
		p.sendMessage("§aYou are now the wanted leader of this movement.");
		Player requester = Bukkit.getPlayerExact(req.getRequester());
		if (requester != null && requester.isOnline()) {
			requester.sendMessage("§a" + p.getName() + " accepted becoming the wanted leader.");
		}
	}

	//Elections and stuff

	public Faction getByVotingBooth(Block b) {
		for(Faction f : factions) {
			Government gov = f.getGovernment();
			if(gov.isVotingBooth(b.getLocation())) return f;
		}
		return null;
	}

	@EventHandler
	public void openBooth(PlayerInteractEvent e) {
		if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
		Block b = e.getClickedBlock();
		Player p = e.getPlayer();
		Faction f = getByVotingBooth(b);
		if(f == null) return;
		e.setCancelled(true);
		if(!f.getGovernment().hasElections()) {
			p.sendMessage("§cThis faction has no elections");
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}
		if(!f.canVote(p.getName())) {
			p.sendMessage("§cYou have no voting rights in this faction");
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}
		InventoryManager inv = new InventoryManager();
		inv.electionView(p, f);
	}

	@EventHandler
	public void placeVotingBooth(BlockPlaceEvent e) {
		Block b = e.getBlock();
		Player p = e.getPlayer();
		if(!TLibs.getBlockAPI().getChecker().checkBlock(b, Cache.votingBlock)) return;
		Faction f = FactionManager.getByMember(p.getName());
		if(f == null) return;
		Government gov = f.getGovernment();
		if(gov.isVotingBooth(b.getLocation())) return;
		gov.addVotingBooth(b.getLocation());
		p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
		p.sendMessage("§aVoting booth added!");
	}

	@EventHandler
	public void breakVotingBooth(BlockBreakEvent e) {
		Block b = e.getBlock();
		Player p = e.getPlayer();
		if(!TLibs.getBlockAPI().getChecker().checkBlock(b, Cache.votingBlock)) return;
		Faction f = getByVotingBooth(b);
		if(f == null) return;
		Government gov = f.getGovernment();
		if(!gov.isVotingBooth(b.getLocation())) return;
		gov.removeVotingBooth(b.getLocation());
		p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
		p.sendMessage("§cVoting booth removed!");
	}
}
