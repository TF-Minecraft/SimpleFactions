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
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityMarks;
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

	private War buildup(int id) {
		War war = war(id);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		assertTrue(BuildupService.start(war, CampaignClock.now()));
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

	private static WarAction mark(War war, UUID account, String payload) {
		return action(2, WarAction.AVAILABILITY, war, account, payload);
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
	void aWebMarkReplacesTheAccountsHoursOnThatDateWithoutBeingOnline() {
		War war = buildup(86);
		LocalDate day = war.getAvailability().getEarliestBattleDay();

		assertNull(reason(mark(war, ann, "{\"date\":\"" + day + "\",\"hours\":[21,25,25]}")));
		assertEquals(Set.of(21, 25), war.getAvailability().marksOn(ann, day));
		assertEquals(Set.of(21, 25), saved(86).getAvailability().marksOn(ann, day));
		assertEquals(1, eventCount("votes_changed"));

		assertNull(reason(mark(war, ann, "{\"date\":\"" + day + "\",\"hours\":[22]}")));
		assertEquals(Set.of(22), war.getAvailability().marksOn(ann, day), "The new hours replace the old ones");
		assertNull(reason(mark(war, dee, "{\"date\":\"" + day.plusDays(1) + "\",\"hours\":[24]}")));

		assertNull(reason(mark(war, ann, "{\"date\":\"" + day + "\",\"hours\":[]}")));
		assertTrue(war.getAvailability().marksOn(ann, day).isEmpty(), "No hours clears the date");
		assertEquals(Set.of(24), war.getAvailability().marksOn(dee, day.plusDays(1)));
	}

	@Test
	void noneOfTheseDaysClearsWhatCanChangeAndMarkingAgainWithdrawsIt() {
		War war = buildup(87);
		LocalDate day = war.getAvailability().getEarliestBattleDay();
		assertNull(reason(mark(war, ann, "{\"date\":\"" + day + "\",\"hours\":[21]}")));

		assertNull(reason(mark(war, ann, "{\"none\":true}")));
		assertTrue(war.getAvailability().marksOn(ann, day).isEmpty());
		assertEquals(day.plusDays(Cache.warAvailabilityDaysShown - 1), war.getAvailability().noneUntil(ann));
		assertEquals(WarActionApplier.UNREADABLE, reason(mark(war, ann, "{\"none\":false}")));
		assertEquals(WarActionApplier.UNREADABLE, reason(mark(war, ann, "{\"none\":\"yes\"}")));
		assertEquals(WarActionApplier.NOT_A_MEMBER, reason(mark(war, cara, "{\"none\":true}")));

		assertNull(reason(mark(war, ann, "{\"date\":\"" + day + "\",\"hours\":[23]}")));
		assertNull(war.getAvailability().noneUntil(ann), "Marking anything takes the answer back");
	}

	@Test
	void aMarkTakesOnlyOpenDatesAndBattleHoursFromAccountsThatFight() {
		War war = buildup(88);
		LocalDate day = war.getAvailability().getEarliestBattleDay();
		String date = "{\"date\":\"" + day + "\",\"hours\":";

		assertEquals(WarActionApplier.BAD_DATE, reason(mark(war, ann, "{\"date\":\"next tuesday\",\"hours\":[]}")));
		assertEquals(AvailabilityMarks.NOT_SHOWN, reason(mark(war, ann, "{\"date\":\"" + day.minusDays(1) + "\",\"hours\":[21]}")));
		assertEquals(AvailabilityMarks.BAD_HOUR, reason(mark(war, ann, date + "[1]}")),
				"Hours after midnight are 24 and 25, as in game");
		assertEquals(AvailabilityMarks.BAD_HOUR, reason(mark(war, ann, date + "[21.5]}")));
		assertEquals(AvailabilityMarks.BAD_HOUR, reason(mark(war, ann, date + "[\"21\"]}")));
		assertEquals(AvailabilityMarks.BAD_HOUR, reason(mark(war, ann, date + "[99999999999]}")));
		assertEquals(WarActionApplier.UNREADABLE, reason(mark(war, ann, date + "21}")));
		assertEquals(WarActionApplier.UNREADABLE, reason(mark(war, ann, "{\"hours\":[21]}")));
		JsonArray tooMany = new JsonArray();
		for (int i = 0; i <= WarActionApplier.MAX_PICKS; i++) {
			tooMany.add(21);
		}
		assertEquals(WarActionApplier.UNREADABLE, reason(mark(war, ann, date + tooMany + "}")));
		assertEquals(WarActionApplier.NOT_A_MEMBER, reason(mark(war, cara, date + "[21]}")),
				"An ally that has not joined does not mark");
		assertTrue(war.getAvailability().marks().isEmpty());

		war.getAvailability().setLastDayLockProcessed(day);
		assertEquals(AvailabilityMarks.LOCKED, reason(mark(war, ann, date + "[21]}")), "A decided day cannot change");
		war.setBattleSchedulePhase(BattleSchedulePhase.IDLE);
		assertEquals(AvailabilityMarks.NO_SCHEDULE, reason(mark(war, ann, date + "[21]}")));
		assertTrue(war.getAvailability().marks().isEmpty());
	}

	@Test
	void theRetiredDayAndHourVotesArePolitelyRefused() {
		War war = buildup(89);
		assertEquals(WarActionApplier.VOTE_CHANGED, reason(picks(WarAction.DAY_VOTE, war, ann, "[]")));
		assertEquals(WarActionApplier.VOTE_CHANGED, reason(picks(WarAction.HOUR_VOTE, war, dee, "[21]")));
		assertEquals("This vote has changed: mark your availability instead.", WarActionApplier.VOTE_CHANGED);
		assertTrue(war.getAvailability().marks().isEmpty());
	}
}
