package net.tfminecraft.simplefactions.war.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.inventory.CharacterKeyFixture;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.campaign.buildup.BuildupService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.core.CallToArmsService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.history.WarHistory;
import net.tfminecraft.simplefactions.war.history.WarHistoryJson;
import net.tfminecraft.simplefactions.war.history.WarHistoryOutbox;
import net.tfminecraft.simplefactions.war.history.WarVoting;

/** Website actions applied to real factions, real wars and their saves; only Paper is simulated. */
class WarActionApplierTest {
	private FactionDomainFixture fixture;
	private PersistenceFilesFixture files;
	private CharacterKeyFixture characters;
	private final Map<String, Object> previousCache = new LinkedHashMap<>();
	private WarHistoryOutbox outbox;
	private Faction attacker;
	private Faction defender;
	private Faction target;
	private UUID cara;
	private UUID ann;
	private UUID dee;
	private final WarActionApplier applier = new WarActionApplier();

	@BeforeEach
	void setup() throws Exception {
		files = new PersistenceFilesFixture();
		Files.createDirectories(files.root.resolve("Data"));
		fixture = new FactionDomainFixture();
		CampaignClock.reset();
		previousCache.put("start", Cache.warBattleWindowStartHour);
		previousCache.put("end", Cache.warBattleWindowEndHour);
		previousCache.put("close", Cache.warVoteCloseHour);
		previousCache.put("buildup", Cache.warBuildupEnabled);
		previousCache.put("penalty", Cache.warDeclinedAllyStabilityPenalty);
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
		Cache.warVoteCloseHour = 16;
		Cache.warBuildupEnabled = true;
		Cache.warDeclinedAllyStabilityPenalty = -30;
		RelationType alliance = fixture.relationType("ally", Map.of("link", "ally"));
		characters = CharacterKeyFixture.install();
		attacker = fixture.saved("attacker", "Alice");
		defender = fixture.saved("defender", "Bob");
		target = fixture.saved("target", "Cara");
		cara = UUID.randomUUID();
		String leader = characters.offline(cara, "Cara");
		target.setLeader(leader);
		target.addMember(leader);
		ann = UUID.randomUUID();
		attacker.addMember(characters.offline(ann, "Ann"));
		dee = UUID.randomUUID();
		defender.addMember(characters.offline(dee, "Dee"));
		attacker.setRelation(target, new Relation(alliance, RelationLoader.getDefaultAttitude()));
		target.setRelation(attacker, new Relation(alliance, RelationLoader.getDefaultAttitude()));
		outbox = new WarHistoryOutbox(files.root.resolve("WarHistory/outbox.jsonl"), message -> {});
		WarHistory.startForTests(outbox);
	}

	@AfterEach
	void close() throws Exception {
		try {
			WarHistory.resetForTests();
			CampaignClock.reset();
			Cache.warBattleWindowStartHour = (Integer) previousCache.get("start");
			Cache.warBattleWindowEndHour = (Integer) previousCache.get("end");
			Cache.warVoteCloseHour = (Integer) previousCache.get("close");
			Cache.warBuildupEnabled = (Boolean) previousCache.get("buildup");
			Cache.warDeclinedAllyStabilityPenalty = (Integer) previousCache.get("penalty");
			fixture.close();
		} finally {
			files.close();
		}
	}


	private War war(int id) {
		return WarManager.addWar(new War(id, attacker, defender));
	}

	private War saved(int id) {
		return new Database().loadWars().stream().filter(w -> w.getId() == id).findFirst().orElseThrow();
	}

	private War calledWar(int id) {
		War war = war(id);
		CallToArmsService.send(fixture.player("Alice"), attacker, target, war);
		assertTrue(war.getCallsToArms().containsKey("target"));
		return war;
	}

	private War dayVote(int id) {
		War war = war(id);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		assertTrue(BuildupService.start(war, CampaignClock.now()));
		return war;
	}

	private War hourVote(int id) {
		War war = war(id);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setBattleDay(BattleWindowService.scheduleDate(CampaignClock.now()).plusDays(5));
		return war;
	}

	private static WarAction action(long id, String kind, War war, UUID account, String payload) {
		return new WarAction(id, kind, war == null ? "w0-0" : WarHistoryJson.warKey(war), account,
				payload == null ? null : JsonParser.parseString(payload).getAsJsonObject());
	}

	private static WarAction answer(War war, UUID account, boolean accept) {
		return action(1, WarAction.CALL_TO_ARMS_ANSWER, war, account,
				"{\"target_faction_id\":\"target\",\"accept\":" + accept + "}");
	}

	private static WarAction picks(String kind, War war, UUID account, String picks) {
		return action(2, kind, war, account, "{\"picks\":" + picks + "}");
	}

	private String reason(WarAction action) {
		WarActionResult result = applier.apply(action);
		assertEquals(action.id(), result.id());
		if (result.reason() == null) {
			assertEquals(WarActionResult.APPLIED, result.status());
		} else {
			assertEquals(WarActionResult.REJECTED, result.status());
		}
		return result.reason();
	}

	private int eventCount(String type) {
		int count = 0;
		for (var item : outbox.peek(WarHistoryOutbox.Kind.EVENT, 1000)) {
			if (JsonParser.parseString(item.json()).getAsJsonObject().get("type").getAsString().equals(type)) {
				count++;
			}
		}
		return count;
	}

	@Test
	void anActionThatCannotBeReadOrIsNotKnownIsRejected() {
		War war = war(80);
		assertEquals(WarActionApplier.UNREADABLE, reason(new WarAction(1, null, "w80-0", cara, new JsonObject())));
		assertEquals(WarActionApplier.UNREADABLE, reason(action(1, WarAction.DAY_VOTE, war, null, "{}")));
		assertEquals(WarActionApplier.UNREADABLE, reason(action(1, WarAction.DAY_VOTE, war, cara, null)));
		assertEquals(WarActionApplier.UNREADABLE,
				reason(new WarAction(1, WarAction.DAY_VOTE, null, cara, new JsonObject())));
		assertEquals(WarActionApplier.UNKNOWN_KIND, reason(action(1, "surrender", war, cara, "{}")));
		assertEquals(WarActionApplier.WAR_OVER, reason(answer(null, cara, true)), "No active war has that key");
	}

	@Test
	void theCalledLeadersAccountAcceptsAndTheFactionJoins() {
		War war = calledWar(81);

		assertNull(reason(answer(war, cara, true)));

		assertTrue(war.isParticipating(target));
		assertTrue(war.getCallsToArms().isEmpty());
		assertTrue(saved(81).isParticipating(target));
		assertEquals(1, eventCount("participant_joined"));
		assertEquals(1, eventCount("call_to_arms_closed"));
	}

	@Test
	void theCalledLeadersAccountDeclinesAndPaysTheStabilityCost() {
		War war = calledWar(82);

		assertNull(reason(answer(war, cara, false)));

		assertTrue(war.getCallsToArms().isEmpty());
		assertFalse(war.isParticipating(target));
		assertEquals(-30, target.getGovernment().getByName("Declined Call to Arms").getModifier());
		assertTrue(saved(82).getCallsToArms().isEmpty());
	}

	@Test
	void onlyTheAccountThatPlaysTheLeaderCanAnswer() {
		War war = calledWar(83);

		assertEquals(WarActionApplier.NOT_LEADER, reason(answer(war, ann, true)));
		target.setLeader("Cara");
		assertEquals(WarActionApplier.NOT_LEADER, reason(answer(war, cara, true)), "A leader with no known account");
		assertTrue(war.getCallsToArms().containsKey("target"), "The call stays open");
	}

	@Test
	void anAnswerNeedsItsFactionAndAnOpenCallAndWaitsForTheBattleToEnd() {
		War war = war(84);
		assertEquals(WarActionApplier.UNREADABLE, reason(action(1, WarAction.CALL_TO_ARMS_ANSWER, war, cara,
				"{\"target_faction_id\":\"target\",\"accept\":\"yes\"}")));
		assertEquals(WarActionApplier.UNREADABLE, reason(action(1, WarAction.CALL_TO_ARMS_ANSWER, war, cara,
				"{\"accept\":true}")));
		assertEquals(WarActionApplier.UNREADABLE, reason(action(1, WarAction.CALL_TO_ARMS_ANSWER, war, cara,
				"{\"target_faction_id\":\"target\"}")));
		assertEquals(WarActionApplier.NO_FACTION, reason(action(1, WarAction.CALL_TO_ARMS_ANSWER, war, cara,
				"{\"target_faction_id\":\"gone\",\"accept\":true}")));
		assertEquals(CallToArmsService.NO_CALL, reason(answer(war, cara, true)));
		assertEquals(CallToArmsService.NO_CALL, reason(answer(war, cara, false)));

		CallToArmsService.send(fixture.player("Alice"), attacker, target, war);
		var battle = mock(net.tfminecraft.simplefactions.war.battle.engine.core.Battle.class);
		when(battle.hasStarted()).thenReturn(true);
		try (var battles = mockStatic(net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.class)) {
			battles.when(() -> net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.getByWarId(84))
					.thenReturn(battle);
			assertEquals(CallToArmsService.BATTLE_UNDERWAY, reason(answer(war, cara, true)));
		}
		assertTrue(war.getCallsToArms().containsKey("target"), "Between battles it can still be answered");
	}

	@Test
	void aCallThatCanNoLongerBeAcceptedIsUsedUpAndClosed() {
		War war = calledWar(85);
		attacker.getDiplomacyHandler().removeRelation(target.getId());
		target.getDiplomacyHandler().removeRelation(attacker.getId());

		assertEquals(CallToArmsService.COULD_NOT_JOIN, reason(answer(war, cara, true)));

		assertTrue(war.getCallsToArms().isEmpty());
		assertEquals(1, eventCount("call_to_arms_closed"), "The website stops offering it");
	}

	@Test
	void aWebDayVoteReplacesTheAccountsPicksWithoutBeingOnline() {
		War war = dayVote(86);
		LocalDate first = war.getDayVoteFirstDay();

		assertNull(reason(picks(WarAction.DAY_VOTE, war, ann, "[\"" + first + "\",\"" + first.plusDays(2) + "\"]")));
		assertEquals(Set.of(0, 2), war.getDayVotes().get(ann));
		assertEquals(Set.of(0, 2), saved(86).getDayVotes().get(ann));
		assertEquals(1, eventCount("votes_changed"));

		assertNull(reason(picks(WarAction.DAY_VOTE, war, ann, "[\"" + first.plusDays(4) + "\"]")));
		assertEquals(Set.of(4), war.getDayVotes().get(ann), "The new picks replace the old ones");
		assertNull(reason(picks(WarAction.DAY_VOTE, war, dee, "[\"" + first.plusDays(4) + "\"]")));

		assertNull(reason(picks(WarAction.DAY_VOTE, war, ann, "[]")));
		assertFalse(war.getDayVotes().containsKey(ann), "No picks clears the vote");
		assertTrue(war.getDayVotes().containsKey(dee));
	}

	@Test
	void aDayVoteTakesOnlyTheDaysOnOfferFromMembersWhileItIsOpen() {
		War war = dayVote(87);
		LocalDate first = war.getDayVoteFirstDay();

		assertEquals(WarActionApplier.BAD_DAY, reason(picks(WarAction.DAY_VOTE, war, ann, "[\"" + first.minusDays(1) + "\"]")));
		assertEquals(WarActionApplier.BAD_DAY, reason(picks(WarAction.DAY_VOTE, war, ann,
				"[\"" + first.plusDays(Cache.warBuildupDayOptions) + "\"]")));
		assertEquals(WarActionApplier.BAD_DAY, reason(picks(WarAction.DAY_VOTE, war, ann, "[\"next tuesday\"]")));
		assertEquals(WarActionApplier.BAD_DAY, reason(picks(WarAction.DAY_VOTE, war, ann, "[3]")));
		assertEquals(WarActionApplier.UNREADABLE, reason(picks(WarAction.DAY_VOTE, war, ann, "\"" + first + "\"")));
		JsonArray tooMany = new JsonArray();
		for (int i = 0; i <= WarActionApplier.MAX_PICKS; i++) {
			tooMany.add(first.toString());
		}
		assertEquals(WarActionApplier.UNREADABLE, reason(picks(WarAction.DAY_VOTE, war, ann, tooMany.toString())));
		assertEquals(WarActionApplier.NOT_A_MEMBER, reason(picks(WarAction.DAY_VOTE, war, cara, "[]")),
				"An ally that has not joined does not vote");
		assertEquals(WarActionApplier.HOUR_VOTE_CLOSED, reason(picks(WarAction.HOUR_VOTE, war, ann, "[21]")),
				"The hour vote waits for the first battle day");
		assertTrue(war.getDayVotes().isEmpty());

		BuildupService.setFirstBattleDay(war, first, CampaignClock.now(), true);
		assertEquals(WarActionApplier.DAY_VOTE_CLOSED, reason(picks(WarAction.DAY_VOTE, war, ann, "[\"" + first + "\"]")));
		assertNull(WarVoting.dayOffset(war, first.toString()), "No day vote, no offsets");
	}

	@Test
	void aWebHourVoteSetsBattleHoursAsInGame() {
		War war = hourVote(88);

		assertNull(reason(picks(WarAction.HOUR_VOTE, war, dee, "[21,25,25]")));
		assertEquals(Set.of(21, 25), war.getBattleVotes().get(dee));
		assertEquals(Set.of(21, 25), saved(88).getBattleVotes().get(dee));
		assertEquals(WarActionApplier.BAD_HOUR, reason(picks(WarAction.HOUR_VOTE, war, dee, "[1]")),
				"Hours after midnight are 24 and 25, as in game");
		assertEquals(WarActionApplier.BAD_HOUR, reason(picks(WarAction.HOUR_VOTE, war, dee, "[21.5]")));
		assertEquals(WarActionApplier.BAD_HOUR, reason(picks(WarAction.HOUR_VOTE, war, dee, "[\"21\"]")));
		assertEquals(WarActionApplier.BAD_HOUR, reason(picks(WarAction.HOUR_VOTE, war, dee, "[99999999999]")));
		assertEquals(WarActionApplier.DAY_VOTE_CLOSED, reason(picks(WarAction.DAY_VOTE, war, dee, "[]")));
		assertEquals(Set.of(21, 25), war.getBattleVotes().get(dee));

		assertNull(reason(picks(WarAction.HOUR_VOTE, war, dee, "[]")));
		assertFalse(war.getBattleVotes().containsKey(dee));

		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		assertEquals(WarActionApplier.HOUR_VOTE_CLOSED, reason(picks(WarAction.HOUR_VOTE, war, dee, "[21]")));
	}
}
