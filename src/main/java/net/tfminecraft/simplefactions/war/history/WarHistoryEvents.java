package net.tfminecraft.simplefactions.war.history;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleSides;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyTally;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.stats.BattleStatsArchive;
import net.tfminecraft.simplefactions.war.truce.TruceService;

/**
 * The war events of the history contract, one method per moment in the war. Each is called
 * from the war code where that moment happens and never throws into it.
 */
public final class WarHistoryEvents {
	private WarHistoryEvents() {
	}

	/** What {@link #battleResolved} needs, gathered in CampaignBattleOutcomeService. */
	public record ResolvedBattle(
			War war,
			BattleResolution resolution,
			int battleNumber,
			CampaignPhase leg,
			Integer provinceId,
			ScheduledCampaignBattle slot,
			BelligerentRole winner,
			Battle battle,
			Map<String, Integer> sideCasualties,
			BattleCasualtyTally tally) {
	}

	/** A war was declared or a civil war started. */
	public static void declared(War war) {
		record(war, "war_declared", JsonObject::new);
	}

	/** A faction answered a call to arms; remembers when, for its participant row. */
	public static void joined(War war, Faction faction, Instant at) {
		// Kept even while history is off, so the participant row has its date once it is back on.
		war.getJoinedAt().put(faction.getId().toLowerCase(java.util.Locale.ROOT), at);
		record(war, "participant_joined", () -> {
			JsonObject payload = new JsonObject();
			payload.add("faction", WarHistoryJson.factionRef(faction));
			payload.addProperty("side", WarHistoryJson.sideOf(war, faction));
			payload.addProperty("role", hasCoalitionCall(war, faction) ? "coalition" : "ally");
			return payload;
		});
	}

	/** The buildup's day vote closed, or staff set the first battle day. */
	public static void dayVoteClosed(War war, LocalDate day, boolean quorum) {
		record(war, "day_vote_closed", () -> {
			JsonObject payload = new JsonObject();
			payload.addProperty("battle_day", day == null ? null : day.toString());
			payload.addProperty("quorum", quorum);
			return payload;
		});
	}

	public static void scheduled(War war, int provinceId) {
		record(war, "battle_scheduled", () -> {
			JsonObject payload = new JsonObject();
			payload.addProperty("scheduled_at", WarHistoryJson.instant(war.getScheduledBattleAt()));
			payload.addProperty("province_id", provinceId);
			payload.addProperty("kind", kind(war, provinceId));
			return payload;
		});
	}

	/** The battle moved to a later day: a postponed vote or a skipped battle day. */
	public static void postponed(War war) {
		record(war, "battle_postponed", () -> {
			JsonObject payload = new JsonObject();
			payload.addProperty("battle_day", war.getBattleDay() == null ? null : war.getBattleDay().toString());
			return payload;
		});
	}

	/** The war's hour vote opened on a new battle day, so the website shows the day that is next, not the last one. */
	public static void nextBattleDay(War war) {
		if (war.getBattleDay() == null) {
			return;
		}
		record(war, "next_battle_day", () -> {
			JsonObject payload = new JsonObject();
			payload.addProperty("battle_day", war.getBattleDay().toString());
			return payload;
		});
	}

	public static void battleResolved(ResolvedBattle facts) {
		record(facts.war(), "battle_resolved", () -> resolvedPayload(facts));
	}

	/** Called while the war still has its sides, before its file is deleted. */
	public static void ended(War war, WarEndReason reason) {
		record(war, "war_ended", () -> {
			JsonObject payload = new JsonObject();
			payload.addProperty("reason", reason.toJson());
			payload.addProperty("winner", switch (reason) {
				case ATTACKER_VICTORY -> WarHistoryJson.ATTACKER;
				case DEFENDER_VICTORY -> WarHistoryJson.DEFENDER;
				default -> null;
			});
			payload.addProperty("truce_until", WarHistoryJson.instant(TruceService.recordedFor(war)));
			return payload;
		});
	}

	private static void record(War war, String type, Supplier<JsonObject> payload) {
		if (war == null || !WarHistory.isRecording()) {
			return;
		}
		try {
			WarHistory.event(war, type, payload.get());
		} catch (RuntimeException failed) {
			WarHistory.warn("Could not record " + type + " for war " + war.getId() + ": " + failed);
		}
	}

	private static JsonObject resolvedPayload(ResolvedBattle facts) {
		War war = facts.war();
		Battle battle = facts.battle();
		boolean live = facts.resolution() == BattleResolution.FOUGHT && battle != null;
		JsonObject payload = new JsonObject();
		payload.addProperty("battle_number", facts.battleNumber());
		payload.addProperty("province_id", facts.provinceId());
		payload.addProperty("kind", facts.slot() != null ? facts.slot().kind().toJson()
				: facts.provinceId() == null ? CampaignBattleKind.FIELD.toJson() : kind(war, facts.provinceId()));
		payload.addProperty("leg", facts.leg() == null ? null : facts.leg().toJson());
		payload.addProperty("winner", role(facts.winner()));
		payload.addProperty("resolution", facts.resolution().toJson());
		payload.addProperty("battle_id", live ? battle.getId() : null);
		payload.addProperty("record_id", live
				? BattleStatsArchive.latestRecordId(battle.getId(), WarHistoryJson.warKey(war)) : null);
		payload.add("deaths", deaths(war, battle, facts.sideCasualties()));
		payload.add("lives", live ? lives(war, battle) : null);
		JsonArray lost = new JsonArray();
		for (BattleCasualtyTally.Loss loss : facts.tally().losses()) {
			JsonObject row = new JsonObject();
			row.add("faction", WarHistoryJson.factionRef(loss.faction()));
			row.addProperty("side", loss.side());
			row.addProperty("regiment", loss.regimentId());
			row.addProperty("regiment_name", WarHistoryJson.clean(loss.regimentName()));
			row.addProperty("count", loss.count());
			lost.add(row);
		}
		payload.add("regiments_lost", lost);
		return payload;
	}

	/** Deaths per war side, from the battle-side ledger (or the autoresolve result) the outcome was given. */
	private static JsonObject deaths(War war, Battle battle, Map<String, Integer> sideCasualties) {
		JsonObject deaths = new JsonObject();
		deaths.addProperty(WarHistoryJson.ATTACKER, 0);
		deaths.addProperty(WarHistoryJson.DEFENDER, 0);
		if (sideCasualties == null || battle == null) {
			return deaths;
		}
		for (Map.Entry<String, Integer> entry : sideCasualties.entrySet()) {
			String side = role(CampaignBattleSides.roleFor(war, battle, entry.getKey()));
			if (side != null && entry.getValue() != null) {
				deaths.addProperty(side, deaths.get(side).getAsInt() + entry.getValue());
			}
		}
		return deaths;
	}

	private static JsonObject lives(War war, Battle battle) {
		JsonObject lives = new JsonObject();
		for (BattleSide side : battle.getSides()) {
			String role = role(CampaignBattleSides.roleFor(war, battle, side.getId()));
			if (role != null) {
				JsonObject row = new JsonObject();
				row.addProperty("left", side.getLives());
				row.addProperty("max", side.getMaxLives());
				lives.add(role, row);
			}
		}
		return lives;
	}

	/** The kind of the active schedule slot when it is this province's, otherwise a field battle. */
	private static String kind(War war, int provinceId) {
		ScheduledCampaignBattle slot = CampaignScheduleService.slotAtActiveIndex(war).orElse(null);
		return slot != null && slot.provinceId() == provinceId ? slot.kind().toJson() : CampaignBattleKind.FIELD.toJson();
	}

	private static String role(BelligerentRole role) {
		if (role == null) {
			return null;
		}
		return role == BelligerentRole.ATTACKER ? WarHistoryJson.ATTACKER : WarHistoryJson.DEFENDER;
	}

	private static boolean hasCoalitionCall(War war, Faction faction) {
		for (var participant : war.getParticipants()) {
			if (participant.hasCoalitionAlly(faction)) {
				return true;
			}
		}
		return false;
	}
}
