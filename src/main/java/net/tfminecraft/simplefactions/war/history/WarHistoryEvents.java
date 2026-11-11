package net.tfminecraft.simplefactions.war.history;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.identity.Members;
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

	/**
	 * A war was declared or a civil war started. Carries, outside the public payload, the accounts
	 * that play a member of a defending faction, so the website can tell them on Discord.
	 */
	public static void declared(War war) {
		record(war, "war_declared", JsonObject::new, () -> defenderNotice(war));
	}

	/** {@code {"defender_accounts": [...]}}; empty when the accounts cannot be read, never failing the event. */
	static JsonObject defenderNotice(War war) {
		JsonArray accounts = new JsonArray();
		try {
			Set<String> seen = new LinkedHashSet<>();
			for (Faction faction : WarHistoryJson.factions(war.getDefenders())) {
				for (String member : faction.getMembers()) {
					Members.accountId(member).ifPresent(owner -> seen.add(owner.toString()));
				}
			}
			seen.forEach(accounts::add);
		} catch (RuntimeException failed) {
			WarHistory.warn("Could not list the defending accounts of war " + war.getId() + ": " + failed);
		}
		JsonObject notify = new JsonObject();
		notify.add("defender_accounts", accounts);
		return notify;
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

	/** A date's day lock (or staff) made it a battle day; its start time is fixed at {@code timeLocksAt}. */
	public static void battleDayLocked(War war, LocalDate day, Instant timeLocksAt, boolean forced) {
		record(war, "battle_day_locked", () -> {
			JsonObject payload = new JsonObject();
			payload.addProperty("battle_day", day.toString());
			payload.addProperty("time_locks_at", WarHistoryJson.instant(timeLocksAt));
			payload.addProperty("forced", forced);
			return payload;
		});
	}

	/**
	 * A reminder fell due. Silent (no timeline entry, no post): the website DMs the accounts under
	 * {@code private.notify.accounts}, those that have not marked what it asks about.
	 */
	public static void voteReminder(War war, String reason, LocalDate day, Instant locksAt, List<UUID> accounts) {
		record(war, "vote_reminder", () -> {
			JsonObject payload = new JsonObject();
			payload.addProperty("reason", reason);
			payload.addProperty("battle_day", day == null ? null : day.toString());
			payload.addProperty("locks_at", WarHistoryJson.instant(locksAt));
			return payload;
		}, () -> null, () -> {
			JsonArray list = new JsonArray();
			accounts.forEach(account -> list.add(account.toString()));
			JsonObject notify = new JsonObject();
			notify.add("accounts", list);
			JsonObject extra = new JsonObject();
			extra.add("notify", notify);
			return extra;
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

	/** Staff skipped the next battle day. */
	public static void postponed(War war) {
		record(war, "battle_postponed", () -> {
			JsonObject payload = new JsonObject();
			payload.addProperty("battle_day", war.getBattleDay() == null ? null : war.getBattleDay().toString());
			return payload;
		});
	}

	/** The war waits for its next battle day again (after a battle or a choice), so the website shows the day that is next. */
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

	/**
	 * Votes changed: in game, by staff, by an applied web action, or because a vote opened or
	 * closed. Silent (no timeline entry, no post); it only carries the new {@code private.voting}.
	 * At most one per war every five seconds, with the latest state.
	 */
	public static void votesChanged(War war) {
		if (war == null || !WarHistory.isRecording()) {
			return;
		}
		try {
			WarVoteChanges.changed(war);
		} catch (RuntimeException failed) {
			WarHistory.warn("Could not record votes_changed for war " + war.getId() + ": " + failed);
		}
	}

	/** What {@link WarVoteChanges} sends when a war's window allows it. */
	static void votesChangedNow(War war) {
		record(war, "votes_changed", JsonObject::new);
	}

	/**
	 * A call to arms was sent. Silent; the website DMs the called leader, so the leader's account and
	 * character go under {@code private.notify}.
	 */
	public static void callToArmsSent(War war, Faction caller, Faction target, boolean coalition) {
		record(war, "call_to_arms_sent", () -> {
			JsonObject payload = new JsonObject();
			payload.add("caller", WarHistoryJson.factionRef(caller));
			payload.add("target", WarHistoryJson.factionRef(target));
			payload.addProperty("side", WarHistoryJson.sideOf(war, caller));
			payload.addProperty("coalition", coalition);
			return payload;
		}, () -> null, () -> {
			JsonObject notify = new JsonObject();
			String leader = target.getLeader();
			notify.addProperty("leader_account", leaderAccount(war, leader));
			notify.addProperty("leader_character_id", Members.characterId(leader) == null ? null : leader);
			JsonObject extra = new JsonObject();
			extra.add("notify", notify);
			return extra;
		});
	}

	/** A call to arms was answered and is no longer open. Silent; an accepted call also sends participant_joined. */
	public static void callToArmsClosed(War war, Faction target, boolean accepted) {
		record(war, "call_to_arms_closed", () -> {
			JsonObject payload = new JsonObject();
			payload.add("target", WarHistoryJson.factionRef(target));
			payload.addProperty("result", accepted ? "accepted" : "declined");
			return payload;
		});
	}

	private static String leaderAccount(War war, String leader) {
		try {
			return Members.accountId(leader).map(UUID::toString).orElse(null);
		} catch (RuntimeException failed) {
			WarHistory.warn("Could not read the account of the called leader in war " + war.getId() + ": " + failed);
			return null;
		}
	}

	private static void record(War war, String type, Supplier<JsonObject> payload) {
		record(war, type, payload, () -> null);
	}

	private static void record(War war, String type, Supplier<JsonObject> payload, Supplier<JsonObject> notify) {
		record(war, type, payload, notify, () -> null);
	}

	private static void record(War war, String type, Supplier<JsonObject> payload, Supplier<JsonObject> notify,
			Supplier<JsonObject> extra) {
		if (war == null || !WarHistory.isRecording()) {
			return;
		}
		try {
			WarHistory.event(war, type, payload.get(), notify.get(), extra.get());
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
