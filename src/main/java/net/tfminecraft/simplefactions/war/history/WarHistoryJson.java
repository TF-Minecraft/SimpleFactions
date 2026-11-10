package net.tfminecraft.simplefactions.war.history;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.stats.BattleRecord;

/**
 * The shapes of the war history contract (FactionRef, Participant, WarState), read from the
 * live war. Only what a public war page shows: no regiment counts, pools, treasuries or votes.
 */
public final class WarHistoryJson {
	/** Nulls are written, so the website can tell "none" from a field it does not know. */
	static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

	private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

	public static final String ATTACKER = "attacker";
	public static final String DEFENDER = "defender";

	private WarHistoryJson() {
	}

	/** A name without § codes or #rrggbb colours. */
	public static String clean(String raw) {
		if (raw == null) {
			return null;
		}
		// Gradient names repeat the section sign (§§x§B§C...); a single pass of stripColor would leave one per letter.
		return ChatColor.stripColor(HEX.matcher(raw.replaceAll("§{2,}", "§")).replaceAll("")).trim();
	}

	public static String instant(Instant at) {
		return at == null ? null : at.toString();
	}

	public static JsonObject factionRef(Faction faction) {
		JsonObject ref = new JsonObject();
		ref.addProperty("id", faction.getId());
		// 0 means the faction never had a founding stamp; the website pairs it with the id.
		ref.addProperty("founded_at", faction.getFoundedAt() > 0 ? faction.getFoundedAt() : null);
		ref.addProperty("name", clean(faction.getName()));
		ref.addProperty("name_raw", faction.getName());
		ref.addProperty("rgb", faction.getRGB());
		return ref;
	}

	/** Every faction on one side with its role, leaders first, each faction once. */
	public static JsonArray participants(War war, Side side) {
		JsonArray out = new JsonArray();
		for (Map.Entry<String, Row> entry : rows(side).entrySet()) {
			JsonObject row = factionRef(entry.getValue().faction());
			row.addProperty("role", entry.getValue().role());
			row.addProperty("joined_at", instant(war.getJoinedAt().get(entry.getKey())));
			out.add(row);
		}
		return out;
	}

	/** The factions of {@link #participants}, in the same order. */
	static List<Faction> factions(Side side) {
		List<Faction> factions = new ArrayList<>();
		rows(side).values().forEach(row -> factions.add(row.faction()));
		return factions;
	}

	private record Row(Faction faction, String role) {
	}

	private static Map<String, Row> rows(Side side) {
		Map<String, Row> rows = new LinkedHashMap<>();
		if (side == null) {
			return rows;
		}
		add(rows, side.getLeader(), "leader");
		for (Participant participant : side.getMainParticipants()) {
			add(rows, participant.getLeader(), "main");
		}
		for (Participant participant : side.getMainParticipants()) {
			addAll(rows, participant.getSubjects(), "subject");
			for (Map.Entry<Faction, Boolean> ally : participant.getAllies().entrySet()) {
				if (Boolean.TRUE.equals(ally.getValue())) {
					add(rows, ally.getKey(), "ally");
				}
			}
			addAll(rows, participant.getCoalitionAllies(), "coalition");
			addAll(rows, participant.getBackers(), "backer");
		}
		return rows;
	}

	private static void addAll(Map<String, Row> rows, List<Faction> factions, String role) {
		for (Faction faction : factions) {
			add(rows, faction, role);
		}
	}

	private static void add(Map<String, Row> rows, Faction faction, String role) {
		if (faction == null || faction.getId() == null) {
			return;
		}
		rows.putIfAbsent(faction.getId().toLowerCase(Locale.ROOT), new Row(faction, role));
	}

	/** "attacker" or "defender" for a faction in the war, or null. */
	public static String sideOf(War war, Faction faction) {
		Side side = faction == null ? null : war.getSide(faction);
		if (side == null) {
			return null;
		}
		return side == war.getAttackers() ? ATTACKER : DEFENDER;
	}

	public static String warKey(War war) {
		return BattleRecord.warKey(war);
	}

	/** The war as it stands now; the website keeps the latest snapshot. */
	public static JsonObject state(War war) {
		JsonObject state = new JsonObject();
		String raw = rawName(war);
		state.addProperty("name", clean(raw));
		state.addProperty("name_raw", raw);
		state.addProperty("goal", war.getGoal() == null ? null : war.getGoal().toJson());
		state.addProperty("war_type", war.getWarType() == null ? null : war.getWarType().toJson());
		state.addProperty("civil_war", CivilWarBorderLock.wasCivilWar(war));
		state.addProperty("status", war.getStatus().toJson());
		state.addProperty("schedule_phase", war.getBattleSchedulePhase().toJson());
		state.addProperty("campaign_phase", war.getCampaignPhase().toJson());
		state.addProperty("buildup", war.isBuildup());
		state.addProperty("day_vote_closes_at", instant(war.getDayVoteClosesAt()));
		state.addProperty("battle_day", war.getBattleDay() == null ? null : war.getBattleDay().toString());
		state.addProperty("scheduled_at", instant(war.getScheduledBattleAt()));
		state.addProperty("route_length", size(war.getCampaignProvinces()));
		state.addProperty("objective_province_id", war.getObjectiveProvinceId());
		state.addProperty("objective_held_by", war.getObjectiveHeldBy().toJson());
		state.addProperty("occupied_by_attacker", size(war.getOccupiedByAttacker()));
		state.addProperty("occupied_by_defender", size(war.getOccupiedByDefender()));
		state.addProperty("battles_fought", war.getCampaignBattlesFought());
		JsonObject initiative = new JsonObject();
		initiative.addProperty(ATTACKER, war.getInitiativeAttacker());
		initiative.addProperty(DEFENDER, war.getInitiativeDefender());
		initiative.addProperty("holder", war.getInitiativeHolder().name().toLowerCase(Locale.ROOT));
		state.add("initiative", initiative);
		JsonObject sides = new JsonObject();
		sides.add(ATTACKER, participants(war, war.getAttackers()));
		sides.add(DEFENDER, participants(war, war.getDefenders()));
		state.add("sides", sides);
		return state;
	}

	/** The war's name before {@link War#getName()} turns its #rrggbb colours into § codes. */
	static String rawName(War war) {
		Faction attacker = war.getAttackers() == null ? null : war.getAttackers().getLeader();
		Faction defender = war.getDefenders() == null ? null : war.getDefenders().getLeader();
		if (attacker == null || defender == null) {
			return "War " + war.getId();
		}
		return attacker.getName() + " #a83116vs. " + defender.getName();
	}

	private static int size(List<Integer> list) {
		return list == null ? 0 : list.size();
	}
}
