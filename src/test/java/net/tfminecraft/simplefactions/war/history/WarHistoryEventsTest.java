package net.tfminecraft.simplefactions.war.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyTally;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.campaign.buildup.BuildupService;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.CallToArmsService;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommitment;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.history.WarHistoryOutbox.Item;
import net.tfminecraft.simplefactions.war.history.WarHistoryOutbox.Kind;
import net.tfminecraft.simplefactions.war.stats.BattleRecord;
import net.tfminecraft.simplefactions.war.stats.BattleStatsArchive;

/** War events from the real war code on real factions and war saves; only Paper and the website are simulated. */
class WarHistoryEventsTest {
	private FactionDomainFixture fixture;
	private PersistenceFilesFixture files;
	private Map<Integer, List<WarCommitment>> previousCommitments;
	private RelationType alliance;
	private WarHistoryOutbox outbox;
	private Logger logger;
	private int previousTruceDays;
	private Faction attacker;
	private Faction defender;
	private Faction target;

	@BeforeEach
	void setup() throws Exception {
		files = new PersistenceFilesFixture();
		Files.createDirectories(files.root.resolve("Data"));
		fixture = new FactionDomainFixture();
		CampaignClock.reset();
		BattleStatsArchive.resetForTests();
		when(org.bukkit.Bukkit.createBossBar(anyString(), any(org.bukkit.boss.BarColor.class),
				any(org.bukkit.boss.BarStyle.class))).thenAnswer(call -> mock(org.bukkit.boss.BossBar.class));
		previousCommitments = new LinkedHashMap<>(commitments());
		commitments().clear();
		previousTruceDays = Cache.warTruceDays;
		alliance = fixture.relationType("ally", Map.of("link", "ally"));
		attacker = fixture.saved("attacker", "Alice");
		attacker.setName("#c9a227The Aureate Coterie");
		attacker.setFoundedAt(1730000000L);
		defender = fixture.saved("defender", "Bob");
		target = fixture.saved("target", "Cara");
		ally(attacker, target);
		outbox = new WarHistoryOutbox(files.root.resolve("WarHistory/outbox.jsonl"), message -> {});
		logger = mock(Logger.class);
		WarHistory.startForTests(outbox, new WarHistorySender(outbox, (path, json) -> GatewayClient.Result.fail("off"),
				() -> "main", message -> {}, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)), logger);
	}

	@AfterEach
	void close() throws Exception {
		try {
			WarHistory.resetForTests();
			BattleStatsArchive.resetForTests();
			CampaignClock.reset();
			commitments().clear();
			commitments().putAll(previousCommitments);
			Cache.warTruceDays = previousTruceDays;
			fixture.close();
		} finally {
			files.close();
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<Integer, List<WarCommitment>> commitments() throws Exception {
		Field field = WarCommitmentService.class.getDeclaredField("commitmentsByWar");
		field.setAccessible(true);
		return (Map<Integer, List<WarCommitment>>) field.get(null);
	}

	private void ally(Faction first, Faction second) {
		first.setRelation(second, new Relation(alliance, RelationLoader.getDefaultAttitude()));
		second.setRelation(first, new Relation(alliance, RelationLoader.getDefaultAttitude()));
	}

	private War war(int id) {
		return WarManager.addWar(new War(id, attacker, defender));
	}

	private List<JsonObject> events() {
		List<JsonObject> events = new ArrayList<>();
		for (Item item : outbox.peek(Kind.EVENT, 1000)) {
			events.add(JsonParser.parseString(item.json()).getAsJsonObject());
		}
		return events;
	}

	private JsonObject last(String type) {
		JsonObject found = null;
		for (JsonObject event : events()) {
			if (event.get("type").getAsString().equals(type)) {
				found = event;
			}
		}
		assertNotNull(found, "No " + type + " event in " + events());
		return found;
	}

	private static JsonObject payload(JsonObject event) {
		return event.getAsJsonObject("payload");
	}

	@Test
	void aDeclaredWarIsRecordedWithItsSnapshot() {
		War war = war(40);

		JsonObject event = last("war_declared");
		assertEquals("w40-" + war.getStartedAt().getEpochSecond(), event.get("war_key").getAsString());
		assertEquals(36, event.get("id").getAsString().length());
		Instant at = Instant.parse(event.get("at").getAsString());
		assertEquals(0, at.getNano() % 1_000_000, "Whole milliseconds, which every ISO parser reads");
		assertEquals(new JsonObject(), payload(event));
		JsonObject state = event.getAsJsonObject("state");
		assertEquals("The Aureate Coterie vs. defender", state.get("name").getAsString());
		assertEquals("#c9a227The Aureate Coterie #a83116vs. defender", state.get("name_raw").getAsString());
		assertEquals("active", state.get("status").getAsString());
		assertFalse(state.get("civil_war").getAsBoolean());
		JsonObject leader = state.getAsJsonObject("sides").getAsJsonArray("attacker").get(0).getAsJsonObject();
		assertEquals("attacker", leader.get("id").getAsString());
		assertEquals(1730000000L, leader.get("founded_at").getAsLong());
		assertEquals("The Aureate Coterie", leader.get("name").getAsString());
		assertEquals("#c9a227The Aureate Coterie", leader.get("name_raw").getAsString());
		assertEquals("1,2,3", leader.get("rgb").getAsString());
		assertEquals("leader", leader.get("role").getAsString());
		assertTrue(leader.get("joined_at").isJsonNull(), "Present at declaration");
	}

	@Test
	void anAcceptedCallIsRecordedAndItsJoinTimeIsSaved() {
		Player sender = fixture.player("Alice");
		Player called = fixture.player("Cara");
		War war = war(41);
		CallToArmsService.send(sender, attacker, target, war);
		CampaignClock.add(java.time.Duration.ofHours(2));

		CallToArmsService.accept(called, war);

		JsonObject event = last("participant_joined");
		JsonObject payload = payload(event);
		assertEquals("target", payload.getAsJsonObject("faction").get("id").getAsString());
		assertEquals("attacker", payload.get("side").getAsString());
		assertEquals("ally", payload.get("role").getAsString());
		Instant joined = war.getJoinedAt().get("target");
		assertNotNull(joined);
		JsonArray attackers = event.getAsJsonObject("state").getAsJsonObject("sides").getAsJsonArray("attacker");
		JsonObject row = attackers.get(1).getAsJsonObject();
		assertEquals("ally", row.get("role").getAsString());
		assertEquals(joined.toString(), row.get("joined_at").getAsString());
		War saved = new Database().loadWars().stream().filter(w -> w.getId() == 41).findFirst().orElseThrow();
		assertEquals(joined, saved.getJoinedAt().get("target"));
	}

	@Test
	void aCoalitionMemberJoinsInTheCoalitionRole() {
		War war = war(42);
		Faction member = fixture.saved("member", "Dan");
		war.getAttackers().getMainParticipants().get(0).addCoalitionAlly(member);

		WarHistoryEvents.joined(war, member, Instant.parse("2026-11-11T10:00:00Z"));

		JsonObject payload = payload(last("participant_joined"));
		assertEquals("coalition", payload.get("role").getAsString());
		assertEquals("2026-11-11T10:00:00Z", war.getJoinedAt().get("member").toString());
	}

	@Test
	void theFirstBattleDayAndPostponementsAreRecorded() {
		War war = war(43);
		Instant now = Instant.parse("2026-11-10T09:00:00Z");

		BuildupService.setFirstBattleDay(war, LocalDate.parse("2026-11-19"), now, true);
		JsonObject closed = payload(last("day_vote_closed"));
		assertEquals("2026-11-19", closed.get("battle_day").getAsString());
		assertTrue(closed.get("quorum").getAsBoolean());

		BattleScheduleService.postpone(war, now);
		assertEquals("2026-11-20", payload(last("battle_postponed")).get("battle_day").getAsString());
		BattleScheduleService.skipBattleDay(war, now);
		assertEquals("2026-11-21", payload(last("battle_postponed")).get("battle_day").getAsString());
		assertEquals("2026-11-21", last("battle_postponed").getAsJsonObject("state").get("battle_day").getAsString());

		war.setBattleDay(null);
		WarHistoryEvents.postponed(war);
		WarHistoryEvents.dayVoteClosed(war, null, false);
		assertTrue(payload(last("battle_postponed")).get("battle_day").isJsonNull());
		assertTrue(payload(last("day_vote_closed")).get("battle_day").isJsonNull());
	}

	@Test
	void theNextBattleDayIsRecordedWhenItsHourVoteOpens() {
		War war = war(48);
		war.setBattleDay(LocalDate.parse("2026-11-19"));
		BattleScheduleService.openVote(war);
		assertEquals("2026-11-19", payload(last("next_battle_day")).get("battle_day").getAsString());
		assertEquals("voting", last("next_battle_day").getAsJsonObject("state").get("schedule_phase").getAsString());

		war.setBattleDay(LocalDate.parse("2026-11-01"));
		BattleScheduleService.catchUpStaleBattleDay(war, Instant.parse("2026-11-10T09:00:00Z"));
		assertEquals(war.getBattleDay().toString(), payload(last("next_battle_day")).get("battle_day").getAsString());

		int before = events().size();
		war.setBattleDay(null);
		WarHistoryEvents.nextBattleDay(war);
		assertEquals(before, events().size(), "No battle day, nothing to record");
	}

	@Test
	void aScheduledBattleNamesItsKindFromTheActiveSlot() {
		War war = war(44);
		war.setScheduledBattleAt(Instant.parse("2026-11-19T20:00:00Z"));
		try (MockedStatic<CampaignScheduleService> schedule = mockStatic(CampaignScheduleService.class)) {
			schedule.when(() -> CampaignScheduleService.slotAtActiveIndex(war))
					.thenReturn(Optional.of(new ScheduledCampaignBattle(77, CampaignBattleKind.SIEGE, true, "fort")));

			WarHistoryEvents.scheduled(war, 77);
			JsonObject siege = payload(last("battle_scheduled"));
			assertEquals("siege", siege.get("kind").getAsString());
			assertEquals(77, siege.get("province_id").getAsInt());
			assertEquals("2026-11-19T20:00:00Z", siege.get("scheduled_at").getAsString());

			WarHistoryEvents.scheduled(war, 78);
			assertEquals("field", payload(last("battle_scheduled")).get("kind").getAsString());

			schedule.when(() -> CampaignScheduleService.slotAtActiveIndex(war)).thenReturn(Optional.empty());
			war.setScheduledBattleAt(null);
			WarHistoryEvents.scheduled(war, 77);
			assertEquals("field", payload(last("battle_scheduled")).get("kind").getAsString());
			assertTrue(payload(last("battle_scheduled")).get("scheduled_at").isJsonNull());
		}
	}

	@Test
	void anEndedWarNamesItsWinnerAndTruce() {
		Cache.warTruceDays = 10;
		WarManager.endWar(war(45), WarEndReason.ATTACKER_VICTORY);
		JsonObject won = last("war_ended");
		assertEquals("attacker_victory", payload(won).get("reason").getAsString());
		assertEquals("attacker", payload(won).get("winner").getAsString());
		assertFalse(payload(won).get("truce_until").isJsonNull());
		assertEquals("ended", won.getAsJsonObject("state").get("status").getAsString());

		WarManager.endWar(war(46), WarEndReason.DEFENDER_VICTORY);
		assertEquals("defender", payload(last("war_ended")).get("winner").getAsString());

		WarManager.endWar(war(47), WarEndReason.WHITE_PEACE);
		assertTrue(payload(last("war_ended")).get("winner").isJsonNull());

		WarManager.endWar(war(48), WarEndReason.ADMIN_END);
		assertTrue(payload(last("war_ended")).get("truce_until").isJsonNull(), "Staff ending a war leaves no truce");
	}

	@Test
	void anEndedCivilWarCarriesTheHostsCooldown() {
		Cache.warTruceDays = 10;
		War war = war(49);
		war.setMovementId("movement-1");

		WarManager.endWar(war, WarEndReason.DEFENDER_VICTORY);

		JsonObject event = last("war_ended");
		assertTrue(event.getAsJsonObject("state").get("civil_war").getAsBoolean());
		assertFalse(payload(event).get("truce_until").isJsonNull());
	}

	@Test
	void aFoughtBattleCarriesDeathsLivesItsRecordAndRegimentLosses() {
		War war = war(50);
		Battle battle = BattleFactory.createBlank(BattleType.FIELD, "w50-field-77");
		battle.setWarId(50);
		battle.setOffensiveCoalition(CampaignSideRole.DEFENDER);
		BattleRecord record = new BattleRecord();
		record.id = "w50-field-77-1";
		record.battleId = battle.getId();
		record.warKey = WarHistoryJson.warKey(war);
		BattleStatsArchive.save(record);
		Map<String, Integer> casualties = new HashMap<>();
		casualties.put(BattleTemplate.ATTACKER_SIDE, 23);
		casualties.put(BattleTemplate.DEFENDER_SIDE, 31);
		casualties.put("neutral", 4);
		casualties.put("broken", null);
		BattleCasualtyTally tally = BattleCasualtyTally.empty();
		Regiment pikemen = mock(Regiment.class);
		when(pikemen.getId()).thenReturn("pikemen");
		when(pikemen.getName()).thenReturn("§6Pikemen");
		tally.add(defender, "defender", pikemen, 2);

		WarHistoryEvents.battleResolved(new WarHistoryEvents.ResolvedBattle(war, BattleResolution.FOUGHT, 3,
				CampaignPhase.INVASION, 77, new ScheduledCampaignBattle(77, CampaignBattleKind.SIEGE, true, "fort"),
				BelligerentRole.ATTACKER, battle, casualties, tally));

		JsonObject payload = payload(last("battle_resolved"));
		assertEquals(3, payload.get("battle_number").getAsInt());
		assertEquals(77, payload.get("province_id").getAsInt());
		assertEquals("siege", payload.get("kind").getAsString());
		assertEquals("invasion", payload.get("leg").getAsString());
		assertEquals("attacker", payload.get("winner").getAsString());
		assertEquals("fought", payload.get("resolution").getAsString());
		assertEquals("w50-field-77", payload.get("battle_id").getAsString());
		assertEquals("w50-field-77-1", payload.get("record_id").getAsString());
		// The war's defenders were on the battle's attacking side.
		assertEquals(31, payload.getAsJsonObject("deaths").get("attacker").getAsInt());
		assertEquals(23, payload.getAsJsonObject("deaths").get("defender").getAsInt());
		BattleSide battleAttackers = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
		JsonObject defenderLives = payload.getAsJsonObject("lives").getAsJsonObject("defender");
		assertEquals(battleAttackers.getLives(), defenderLives.get("left").getAsInt());
		assertEquals(battleAttackers.getMaxLives(), defenderLives.get("max").getAsInt());
		JsonObject lost = payload.getAsJsonArray("regiments_lost").get(0).getAsJsonObject();
		assertEquals("defender", lost.getAsJsonObject("faction").get("id").getAsString());
		assertEquals("defender", lost.get("side").getAsString());
		assertEquals("pikemen", lost.get("regiment").getAsString());
		assertEquals("Pikemen", lost.get("regiment_name").getAsString());
		assertEquals(2, lost.get("count").getAsInt());
		assertEquals(1, outbox.size(Kind.BATTLE), "Saving the record queued it too");
	}

	@Test
	void aBattleSettledWithoutAFightHasNoLiveBattleFields() {
		War war = war(51);
		Battle carrier = new Battle("autoresolve-51");
		carrier.setWarId(51);

		WarHistoryEvents.battleResolved(new WarHistoryEvents.ResolvedBattle(war, BattleResolution.AUTORESOLVE, 1,
				CampaignPhase.RETAKE, 77, null, BelligerentRole.DEFENDER, carrier,
				Map.of(BattleTemplate.ATTACKER_SIDE, 5), BattleCasualtyTally.empty()));
		JsonObject autoresolved = payload(last("battle_resolved"));
		assertEquals("autoresolve", autoresolved.get("resolution").getAsString());
		assertEquals("defender", autoresolved.get("winner").getAsString());
		assertEquals("field", autoresolved.get("kind").getAsString());
		assertTrue(autoresolved.get("battle_id").isJsonNull());
		assertTrue(autoresolved.get("record_id").isJsonNull());
		assertTrue(autoresolved.get("lives").isJsonNull());
		assertEquals(5, autoresolved.getAsJsonObject("deaths").get("attacker").getAsInt());

		WarHistoryEvents.battleResolved(new WarHistoryEvents.ResolvedBattle(war, BattleResolution.STAFF, 2,
				null, null, null, null, null, null, BattleCasualtyTally.empty()));
		JsonObject staff = payload(last("battle_resolved"));
		assertEquals("staff", staff.get("resolution").getAsString());
		assertTrue(staff.get("winner").isJsonNull());
		assertTrue(staff.get("leg").isJsonNull());
		assertTrue(staff.get("province_id").isJsonNull());
		assertEquals("field", staff.get("kind").getAsString());
		assertEquals(0, staff.getAsJsonObject("deaths").get("defender").getAsInt());

		WarHistoryEvents.battleResolved(new WarHistoryEvents.ResolvedBattle(war, BattleResolution.NAVAL_DEFAULT, 3,
				null, 77, null, null, carrier, null, BattleCasualtyTally.empty()));
		assertEquals("naval_default", payload(last("battle_resolved")).get("resolution").getAsString());
		assertEquals(0, payload(last("battle_resolved")).getAsJsonObject("deaths").get("attacker").getAsInt());
	}

	@Test
	void aFailureToRecordIsLoggedAndNeverThrown() {
		War broken = mock(War.class);
		when(broken.getId()).thenReturn(9);

		assertDoesNotThrow(() -> WarHistoryEvents.declared(broken));
		assertDoesNotThrow(() -> WarHistoryEvents.declared(null));

		verify(logger).warning(startsWith("[WarHistory] Could not record war_declared for war 9: "));
		assertEquals(0, outbox.size());
	}

	@Test
	void nothingIsRecordedWhileHistoryIsOff() {
		boolean previous = Cache.warHistoryEnabled;
		Cache.warHistoryEnabled = false;
		try {
			War war = war(52);
			WarHistoryEvents.joined(war, target, Instant.EPOCH);
			assertEquals(0, outbox.size());
			assertEquals(Instant.EPOCH, war.getJoinedAt().get("target"), "The join date is kept for later");
		} finally {
			Cache.warHistoryEnabled = previous;
		}
	}

	@Test
	void participantsCarryTheirRolesLeadersFirstAndEachFactionOnce() {
		Faction subject = fixture.saved("subject", "Sam");
		fixture.subject(attacker, subject);
		Faction second = fixture.saved("second", "Sue");
		Faction coalition = fixture.saved("coalition", "Cole");
		Faction backer = fixture.saved("backer", "Ben");
		Faction waiting = fixture.saved("waiting", "Wes");
		War war = new War(53, attacker, defender);
		Participant lead = war.getAttackers().getMainParticipants().get(0);
		war.getAttackers().addNewParticipant(second, lead);
		lead.getAllies().put(target, true);
		lead.getAllies().put(waiting, false);
		lead.addCoalitionAlly(coalition);
		lead.addBacker(backer);
		lead.addBacker(subject);
		lead.getSubjects().add(null);
		lead.getSubjects().add(mock(Faction.class));
		war.getJoinedAt().put("target", Instant.parse("2026-11-12T08:00:00Z"));

		JsonArray rows = WarHistoryJson.participants(war, war.getAttackers());

		List<String> roles = new ArrayList<>();
		for (var row : rows) {
			roles.add(row.getAsJsonObject().get("id").getAsString() + ":" + row.getAsJsonObject().get("role").getAsString());
		}
		assertEquals(List.of("attacker:leader", "second:main", "subject:subject", "target:ally",
				"coalition:coalition", "backer:backer"), roles);
		assertEquals("2026-11-12T08:00:00Z", rows.get(3).getAsJsonObject().get("joined_at").getAsString());
		assertEquals(0, WarHistoryJson.participants(war, null).size());
		assertEquals("attacker", WarHistoryJson.sideOf(war, target));
		assertEquals("defender", WarHistoryJson.sideOf(war, defender));
		assertNull(WarHistoryJson.sideOf(war, waiting));
		assertNull(WarHistoryJson.sideOf(war, null));
	}

	@Test
	void theStateSnapshotReadsTheCampaign() {
		War war = new War(54, attacker, defender);
		war.setCampaignProvinces(List.of(1, 2, 3, 4));
		war.setObjectiveProvinceId(4);
		war.setOccupiedByAttacker(List.of(1, 2));
		war.setBattleDay(LocalDate.parse("2026-11-19"));
		war.setScheduledBattleAt(Instant.parse("2026-11-19T20:00:00Z"));
		war.setDayVoteClosesAt(Instant.parse("2026-11-13T09:50:00Z"));
		war.setBuildup(true);
		war.setCampaignBattlesFought(2);
		war.setInitiativeAttacker(6);
		war.setInitiativeDefender(4);
		war.setInitiativeHolder(BelligerentRole.DEFENDER);

		JsonObject state = WarHistoryJson.state(war);

		assertEquals(4, state.get("route_length").getAsInt());
		assertEquals(4, state.get("objective_province_id").getAsInt());
		assertEquals("defender", state.get("objective_held_by").getAsString());
		assertEquals(2, state.get("occupied_by_attacker").getAsInt());
		assertEquals(0, state.get("occupied_by_defender").getAsInt());
		assertEquals("2026-11-19", state.get("battle_day").getAsString());
		assertEquals("2026-11-19T20:00:00Z", state.get("scheduled_at").getAsString());
		assertEquals("2026-11-13T09:50:00Z", state.get("day_vote_closes_at").getAsString());
		assertTrue(state.get("buildup").getAsBoolean());
		assertEquals("invasion", state.get("campaign_phase").getAsString());
		assertEquals(2, state.get("battles_fought").getAsInt());
		JsonObject initiative = state.getAsJsonObject("initiative");
		assertEquals(6, initiative.get("attacker").getAsInt());
		assertEquals("defender", initiative.get("holder").getAsString());

		war.setCampaignProvinces(null);
		war.setGoal(null);
		war.setWarType(null);
		JsonObject sparse = WarHistoryJson.state(war);
		assertEquals(0, sparse.get("route_length").getAsInt());
		assertTrue(sparse.get("goal").isJsonNull());
		assertTrue(sparse.get("war_type").isJsonNull());
	}

	@Test
	void namesLoseTheirColoursAndAMissingLeaderStillNamesTheWar() {
		assertEquals("Iron Pact", WarHistoryJson.clean(" §x§a§8§3§1§1§6#a83116Iron §lPact "));
		// Gradient names double the section sign before each colour.
		assertEquals("The Jolly", WarHistoryJson.clean("§§x§B§C§1§2§3§4T§§x§B§C§1§2§3§5he §§x§B§C§1§2§3§6Jolly"));
		assertNull(WarHistoryJson.clean(null));
		War leaderless = mock(War.class);
		when(leaderless.getId()).thenReturn(7);
		assertEquals("War 7", WarHistoryJson.rawName(leaderless));
		Faction unfounded = fixture.saved("unfounded", "Una");
		unfounded.setFoundedAt(0);
		unfounded.setRGB(null);
		JsonObject ref = WarHistoryJson.factionRef(unfounded);
		assertTrue(ref.get("founded_at").isJsonNull());
		assertTrue(ref.get("rgb").isJsonNull());
	}
}
