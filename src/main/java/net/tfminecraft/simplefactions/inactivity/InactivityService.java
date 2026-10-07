package net.tfminecraft.simplefactions.inactivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.inactivity.DecayClock.Event;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.FactionCleanup;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;

/**
 * Tracks guild output penalties and faction prestige penalties, and sheds land
 * from an inactive faction that holds more provinces than its prestige covers.
 */
public final class InactivityService {
	private static final File FILE = new File("plugins/SimpleFactions/Cache", "inactivity.json");

	private static final Map<String, DecayClock> guildClocks = new HashMap<>();
	private static final Map<String, DecayClock> factionClocks = new HashMap<>();
	private static long earliestDue = Long.MAX_VALUE;
	private static boolean loaded;
	private static boolean saveBlocked;

	private InactivityService() {}

	public static final class SavedClock {
		public int percent;
		public long nextAt;
	}

	public static final class SavedState {
		public Map<String, SavedClock> guilds = new HashMap<>();
		public Map<String, SavedClock> factions = new HashMap<>();
	}

	public static void load() {
		Map<String, DecayClock> nextGuilds = new HashMap<>();
		Map<String, DecayClock> nextFactions = new HashMap<>();
		try {
			if (FILE.exists()) {
				SavedState state = JsonUtil.readJson(FILE, SavedState.class);
				if (state == null) throw new IOException("Inactivity timer file contains no state");
				readInto(state.guilds, nextGuilds);
				readInto(state.factions, nextFactions);
			}
		} catch (IOException | RuntimeException ex) {
			// Preserve both the last good snapshot and the unreadable source until an explicit reload succeeds.
			loaded = true;
			saveBlocked = true;
			log(Level.WARNING, "Could not read inactivity timers", ex);
			return;
		}
		guildClocks.clear();
		guildClocks.putAll(nextGuilds);
		factionClocks.clear();
		factionClocks.putAll(nextFactions);
		loaded = true;
		saveBlocked = false;
		recomputeEarliest();
	}

	public static void save() {
		if (!loaded || saveBlocked) return;
		try {
			File parent = FILE.getParentFile();
			if (parent != null && !parent.exists()) parent.mkdirs();
			SavedState state = new SavedState();
			writeFrom(guildClocks, state.guilds);
			writeFrom(factionClocks, state.factions);
			JsonUtil.writeJsonAtomic(FILE, state);
		} catch (IOException ex) {
			log(Level.WARNING, "Could not save inactivity timers", ex);
		}
	}

	public static double outputFactor(Guild guild) {
		if (guild == null) return 1.0;
		return InactivityRules.outputFactor(guildPercent(guild.getId()));
	}

	public static int guildPercent(Guild guild) {
		if (guild == null) return 0;
		return guildPercent(guild.getId());
	}

	public static int prestigePercent(Faction faction) {
		if (faction == null) return 0;
		ensureLoaded();
		DecayClock clock = factionClocks.get(faction.getId());
		return clock == null ? 0 : clock.getPercent();
	}

	public static boolean isMemberInactive(String name) {
		if (!InactivityRules.isTrackedMember(name)) return false;
		if (isOnline(name)) return false;
		return InactivityRules.isInactive(FactionCleanup.daysOffline(name));
	}

	public static boolean isFullyInactive(List<String> members) {
		int tracked = 0;
		int inactive = 0;
		if (members == null) return false;
		for (String member : members) {
			if (!InactivityRules.isTrackedMember(member)) continue;
			tracked++;
			if (isMemberInactive(member)) inactive++;
		}
		return InactivityRules.isFullyInactive(tracked, inactive);
	}

	public static void onLogin(Player player) {
		if (player == null) return;
		ensureLoaded();
		String name = player.getName();
		boolean wasInactive = InactivityRules.isInactive(FactionCleanup.daysOffline(name));
		FactionCleanup.ping(name);
		if (!wasInactive) return;

		Faction faction = FactionManager.getByMember(name);
		if (faction == null) {
			player.sendMessage("§aYou are active again.");
			return;
		}
		Guild guild = faction.getGuild(name);
		boolean outputCleared = guild != null && clearClock(guildClocks, guild.getId());
		boolean prestigeCleared = clearClock(factionClocks, faction.getId());
		if (prestigeCleared) faction.updatePrestige();
		if (outputCleared || prestigeCleared) {
			recomputeEarliest();
			save();
			if (outputCleared) refreshEconomy();
		}
		StringBuilder message = new StringBuilder("§aYou are active again.");
		if (outputCleared && guild != null) {
			message.append(" §7").append(guild.getName()).append(" output penalty cleared.");
		}
		if (prestigeCleared) {
			message.append(" §7").append(faction.getName()).append(" prestige penalty cleared.");
		}
		player.sendMessage(message.toString());
	}

	/** Start the four-hour wait for groups that just became fully inactive. */
	public static void armAll(List<Faction> factions, long now) {
		ensureLoaded();
		if (factions == null) return;
		boolean changed = false;
		for (Faction faction : factions) {
			if (faction == null) continue;
			if (syncClock(factionClocks, faction.getId(), isFullyInactive(faction.getMembers()), now)) {
				changed = true;
			}
			if (faction.getGuildHandler() == null) continue;
			for (Guild guild : faction.getGuildHandler().getGuilds()) {
				if (guild == null) continue;
				if (syncClock(guildClocks, guild.getId(), isFullyInactive(guild.getMembers()), now)) {
					changed = true;
				}
			}
		}
		if (changed) {
			recomputeEarliest();
			save();
		}
	}

	public static void tickIfDue(List<Faction> factions) {
		ensureLoaded();
		long now = System.currentTimeMillis();
		if (now < earliestDue) return;
		tick(factions, now);
	}

	static void tick(List<Faction> factions, long now) {
		if (factions == null) return;
		boolean outputChanged = false;
		boolean dirty = false;
		for (Faction faction : new ArrayList<>(factions)) {
			if (faction == null) continue;
			try {
				if (faction.getGuildHandler() != null) {
					for (Guild guild : faction.getGuildHandler().getGuilds()) {
						if (guild == null) continue;
						Event event = advance(guildClocks, guild.getId(), isFullyInactive(guild.getMembers()), now);
						if (event == Event.NONE) continue;
						dirty = true;
						if (event == Event.STEPPED) {
							outputChanged = true;
							log(Level.INFO, guild.getName() + " inactivity output is now -" + guildPercent(guild.getId()) + "%", null);
						} else if (event == Event.CLEARED) {
							outputChanged = true;
						}
					}
				}
				Event factionEvent = advance(factionClocks, faction.getId(), isFullyInactive(faction.getMembers()), now);
				if (factionEvent == Event.CLEARED) {
					dirty = true;
					faction.updatePrestige();
				} else if (factionEvent == Event.STEPPED) {
					dirty = true;
					faction.updatePrestige();
					log(Level.INFO, faction.getName() + " inactivity prestige is now -" + prestigePercent(faction) + "%", null);
					if (loseProvinceIfNeeded(faction)) outputChanged = true;
				} else if (factionEvent == Event.ARMED) {
					dirty = true;
				}
			} catch (RuntimeException ex) {
				log(Level.SEVERE, "Inactivity tick failed for " + faction.getId(), ex);
			}
		}
		recomputeEarliest();
		if (dirty) save();
		if (outputChanged) refreshEconomy();
	}

	private static boolean loseProvinceIfNeeded(Faction faction) {
		if (faction.getProvinces() == null || faction.getProvinces().isEmpty()) return false;
		if (CivilWarBorderLock.isLocked(faction)) return false;
		double prestige = faction.getPrestige() == null ? 0 : faction.getPrestige();
		boolean overCap = TitleManager.overProvinceCap(faction);
		if (!InactivityRules.hasTooManyProvinces(prestige, faction.getProvinces().size(), Cache.provinceCost, overCap)) {
			return false;
		}
		Integer provinceId = InactivityRules.nextProvinceToLose(new ArrayList<>(faction.getProvinces()), faction.getCapital());
		if (provinceId == null) return false;
		dropProvince(faction, provinceId);
		log(Level.INFO, faction.getName() + " lost province " + provinceId + " to inactivity", null);
		return true;
	}

	private static void dropProvince(Faction faction, int provinceId) {
		if (faction.getGuildHandler() != null) {
			for (Guild guild : faction.getGuildHandler().getGuilds()) {
				if (guild != null && !guild.isBase() && guild.getCapital() == provinceId) {
					guild.setCapital(-1, false);
				}
			}
		}
		if (faction.getCapital() == provinceId) {
			faction.setCapital(-1, true, false);
		}
		faction.removeProvince(provinceId, true);
		if (FactionManager.getMap() != null && faction.getRGB() != null) {
			FactionManager.getMap().enqueue("nation", faction.getRGB());
		}
		faction.updateTier();
		faction.updatePrestige();
		new Database().saveFaction(faction);
	}

	private static Event advance(Map<String, DecayClock> clocks, String id, boolean fullyInactive, long now) {
		if (id == null) return Event.NONE;
		DecayClock clock = clocks.get(id);
		if (clock == null) {
			if (!fullyInactive) return Event.NONE;
			clock = new DecayClock();
			clocks.put(id, clock);
		}
		Event event = clock.tick(now, fullyInactive);
		if (!clock.isSet()) clocks.remove(id);
		return event;
	}

	/** @return true when the stored clock changed */
	private static boolean syncClock(Map<String, DecayClock> clocks, String id, boolean fullyInactive, long now) {
		if (id == null) return false;
		DecayClock clock = clocks.get(id);
		if (!fullyInactive) {
			if (clock == null || !clock.isSet()) return false;
			clocks.remove(id);
			return true;
		}
		if (clock == null) {
			clock = new DecayClock();
			clocks.put(id, clock);
		}
		return clock.arm(now);
	}

	private static boolean clearClock(Map<String, DecayClock> clocks, String id) {
		if (id == null) return false;
		DecayClock clock = clocks.remove(id);
		return clock != null && clock.isSet();
	}

	private static int guildPercent(String id) {
		ensureLoaded();
		DecayClock clock = guildClocks.get(id);
		return clock == null ? 0 : clock.getPercent();
	}

	private static void ensureLoaded() {
		if (!loaded) load();
	}

	private static void recomputeEarliest() {
		long earliest = Long.MAX_VALUE;
		earliest = earliestOf(guildClocks, earliest);
		earliest = earliestOf(factionClocks, earliest);
		earliestDue = earliest;
	}

	private static long earliestOf(Map<String, DecayClock> clocks, long earliest) {
		for (DecayClock clock : clocks.values()) {
			if (clock.getNextAt() > 0 && clock.getNextAt() < earliest) earliest = clock.getNextAt();
		}
		return earliest;
	}

	private static void readInto(Map<String, SavedClock> saved, Map<String, DecayClock> clocks) {
		if (saved == null) return;
		for (Map.Entry<String, SavedClock> entry : saved.entrySet()) {
			SavedClock data = entry.getValue();
			if (entry.getKey() == null || data == null) continue;
			DecayClock clock = new DecayClock(data.percent, data.nextAt);
			if (clock.isSet()) clocks.put(entry.getKey(), clock);
		}
	}

	private static void writeFrom(Map<String, DecayClock> clocks, Map<String, SavedClock> saved) {
		for (Map.Entry<String, DecayClock> entry : clocks.entrySet()) {
			DecayClock clock = entry.getValue();
			if (clock == null || !clock.isSet()) continue;
			SavedClock data = new SavedClock();
			data.percent = clock.getPercent();
			data.nextAt = clock.getNextAt();
			saved.put(entry.getKey(), data);
		}
	}

	private static boolean isOnline(String name) {
		try {
			if (Bukkit.getServer() == null) return false;
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player != null && player.getName().equalsIgnoreCase(name)) return true;
			}
		} catch (Throwable ignored) {
			return false;
		}
		return false;
	}

	private static void refreshEconomy() {
		try {
			SimpleFactions plugin = SimpleFactions.getInstance();
			if (plugin == null || plugin.getProvinceManager() == null) return;
			plugin.getProvinceManager().recalculate();
		} catch (RuntimeException ex) {
			log(Level.WARNING, "Could not refresh guild output after an inactivity change", ex);
		}
	}

	private static void log(Level level, String message, Throwable error) {
		SimpleFactions plugin = SimpleFactions.getInstance();
		if (plugin != null && plugin.getLogger() != null) {
			if (error == null) plugin.getLogger().log(level, message);
			else plugin.getLogger().log(level, message, error);
			return;
		}
		if (error == null) java.util.logging.Logger.getLogger(InactivityService.class.getName()).log(level, message);
		else java.util.logging.Logger.getLogger(InactivityService.class.getName()).log(level, message, error);
	}
}
