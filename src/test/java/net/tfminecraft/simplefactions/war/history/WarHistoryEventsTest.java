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
import java.util.UUID;
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
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.inventory.CharacterKeyFixture;
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
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityScheduler;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;
import net.tfminecraft.simplefactions.war.core.War;
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
			Cache.warTruceDays = previousTruceDays;
			fixture.close();
		} finally {
			files.close();
		}
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
		assertEquals(0, event.getAsJsonObject("notify").getAsJsonArray("defender_accounts").size(),
				"Name-keyed test members have no account");
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
	void aDeclaredWarListsTheAccountsOfEveryDefendingMemberOnce() {
		CharacterKeyFixture characters = CharacterKeyFixture.install();
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID third = UUID.randomUUID();
		defender.addMember(characters.offline(first, "Bob"));
		defender.addMember(characters.offline(first, "Bob's heir"));
		defender.addMember(characters.offline(second, "Bea"));
		Faction friend = fixture.saved("friend", "Fay");
		friend.addMember(characters.offline(third, "Fay"));
		friend.addMember(characters.offline(second, "Bea's alt"));
		attacker.addMember(characters.offline(UUID.randomUUID(), "Alice"));
		War war = new War(39, attacker, defender);
		war.getDefenders().getMainParticipants().get(0).getAllies().put(friend, true);

		WarManager.addWar(war);

		JsonObject event = last("war_declared");
		List<String> accounts = new ArrayList<>();
		event.getAsJsonObject("notify").getAsJsonArray("defender_accounts").forEach(id -> accounts.add(id.getAsString()));
		assertEquals(3, accounts.size(), "Each account once: " + accounts);
		assertEquals(java.util.Set.of(first.toString(), second.toString(), third.toString()),
				java.util.Set.copyOf(accounts));
		assertEquals(new JsonObject(), payload(event), "The accounts stay out of the public payload");
		assertFalse(event.getAsJsonObject("state").toString().contains(first.toString()));
	}

	@Test
	void unreadableAccountsLeaveTheDeclarationWithoutThem() {
		Members.Resolver offline = mock(Members.Resolver.class);
		when(offline.info(anyString())).thenThrow(new IllegalStateException("characters offline"));
		Members.setResolver(offline);
		defender.addMember(UUID.randomUUID().toString());

		War war = war(38);

		JsonObject event = last("war_declared");
		assertEquals(0, event.getAsJsonObject("notify").getAsJsonArray("defender_accounts").size());
		verify(logger).warning("[WarHistory] Could not list the defending accounts of war " + war.getId()
				+ ": java.lang.IllegalStateException: characters offline");
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
	void battleDayLocksAndStaffSkipsAreRecorded() {
		War war = war(43);
		war.setBattleSchedulePhase(net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase.VOTING);
		Instant now = CampaignClock.now();
		LocalDate today = net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService.scheduleDate(now);

		assertNull(AvailabilityScheduler.lockDay(war, today.plusDays(4), now));
		JsonObject locked = payload(last("battle_day_locked"));
		assertEquals(today.plusDays(4).toString(), locked.get("battle_day").getAsString());
		assertEquals(AvailabilityCalendar.timeLockAt(today.plusDays(4)).toString(), locked.get("time_locks_at").getAsString());
		assertFalse(locked.get("forced").getAsBoolean());
		JsonArray battleDays = last("battle_day_locked").getAsJsonObject("state").getAsJsonArray("battle_days");
		assertEquals(today.plusDays(4).toString(), battleDays.get(0).getAsJsonObject().get("date").getAsString());
		assertTrue(battleDays.get(0).getAsJsonObject().get("hour").isJsonNull());
		assertNull(AvailabilityScheduler.lockDay(war, today.plusDays(5), now));

		assertTrue(AvailabilityScheduler.skip(war));
		assertEquals(today.plusDays(5).toString(), payload(last("battle_postponed")).get("battle_day").getAsString());
		assertEquals(today.plusDays(5).toString(),
				last("battle_postponed").getAsJsonObject("state").get("battle_day").getAsString());

		assertTrue(AvailabilityScheduler.skip(war));
		assertTrue(payload(last("battle_postponed")).get("battle_day").isJsonNull());
	}

	@Test
	void theNextBattleDayIsRecordedWhenTheWarGoesBackToWaitingForIt() {
		War war = war(48);
		war.setBattleDay(LocalDate.parse("2026-11-19"));
		BattleScheduleService.openVote(war);
		assertEquals("2026-11-19", payload(last("next_battle_day")).get("battle_day").getAsString());
		assertEquals("voting", last("next_battle_day").getAsJsonObject("state").get("schedule_phase").getAsString());

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
		assertTrue(state.get("day_vote_closes_at").isJsonNull(), "Availability wars have no day vote");
		assertTrue(state.get("next_lock_at").isJsonNull(), "A war without a schedule has no locks");
		assertEquals(0, state.getAsJsonArray("battle_days").size());
		assertTrue(state.get("buildup").getAsBoolean());
		assertEquals("invasion", state.get("campaign_phase").getAsString());
		assertEquals(2, state.get("battles_fought").getAsInt());
		JsonObject initiative = state.getAsJsonObject("initiative");
		assertEquals(6, initiative.get("attacker").getAsInt());
		assertEquals("defender", initiative.get("holder").getAsString());

		war.setBattleSchedulePhase(net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase.SCHEDULED);
		war.getAvailability().putBattleDay(LockedBattleDay.open(LocalDate.parse("2026-11-19"), false).withHour(22, false));
		war.getAvailability().putBattleDay(LockedBattleDay.open(LocalDate.parse("2026-11-20"), false));
		JsonObject scheduled = WarHistoryJson.state(war);
		assertEquals("[{\"date\":\"2026-11-19\",\"hour\":22},{\"date\":\"2026-11-20\",\"hour\":null}]",
				WarHistoryJson.GSON.toJson(scheduled.getAsJsonArray("battle_days")));
		assertEquals(AvailabilityCalendar.nextLockAt(war, CampaignClock.now()).toString(),
				scheduled.get("next_lock_at").getAsString());
		war.end(WarEndReason.WHITE_PEACE);
		assertEquals(0, WarHistoryJson.state(war).getAsJsonArray("battle_days").size(), "An ended war fights no more");

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

	private int[] windowBefore;

	/** Battle hours 21 to 25 (01:00), the vote closing at 16:00 on the battle day, as on Main. */
	private void mainWindow() {
		windowBefore = new int[] {Cache.warBattleWindowStartHour, Cache.warBattleWindowEndHour, Cache.warVoteCloseHour};
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
		Cache.warVoteCloseHour = 16;
	}

	private Boolean buildupBefore;

	private void buildup(War war) {
		buildupBefore = Cache.warBuildupEnabled;
		Cache.warBuildupEnabled = true;
		war.setBattleSchedulePhase(net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase.VOTING);
		assertTrue(BuildupService.start(war, CampaignClock.now()));
	}

	@AfterEach
	void restoreWindow() {
		if (buildupBefore != null) {
			Cache.warBuildupEnabled = buildupBefore;
		}
		if (windowBefore != null) {
			Cache.warBattleWindowStartHour = windowBefore[0];
			Cache.warBattleWindowEndHour = windowBefore[1];
			Cache.warVoteCloseHour = windowBefore[2];
		}
	}

	private static JsonObject voting(JsonObject event) {
		return event.getAsJsonObject("private").getAsJsonObject("voting");
	}

	private static List<String> strings(JsonArray array) {
		List<String> out = new ArrayList<>();
		array.forEach(item -> out.add(item.getAsString()));
		return out;
	}

	@Test
	void everyEventCarriesTheAvailabilityOnlyInItsPrivateBlock() {
		mainWindow();
		CharacterKeyFixture characters = CharacterKeyFixture.install();
		UUID ann = UUID.randomUUID();
		UUID dee = UUID.randomUUID();
		attacker.addMember(characters.offline(ann, "Ann"));
		attacker.addMember(characters.offline(ann, "Ann's heir"));
		defender.addMember(characters.offline(dee, "Dee"));

		War war = war(60);
		// Declaring starts the buildup before the event; addWar alone does not.
		buildup(war);
		WarHistoryEvents.declared(war);

		JsonObject event = last("war_declared");
		JsonObject voting = voting(event);
		assertEquals("availability", voting.get("kind").getAsString());
		assertEquals("[21,22,23,24,25]", voting.getAsJsonArray("hours").toString());
		assertEquals(List.of("21:00 CET", "22:00 CET", "23:00 CET", "00:00 CET", "01:00 CET"),
				strings(voting.getAsJsonArray("hour_labels")));
		LocalDate first = war.getAvailability().getEarliestBattleDay();
		JsonArray dates = voting.getAsJsonArray("dates");
		assertEquals(7, dates.size());
		JsonObject firstDate = dates.get(0).getAsJsonObject();
		assertEquals(first.toString(), firstDate.get("date").getAsString());
		assertEquals(java.time.format.DateTimeFormatter.ofPattern("EEE dd/MM", java.util.Locale.ENGLISH).format(first),
				firstDate.get("label").getAsString());
		assertEquals("open", firstDate.get("state").getAsString());
		assertEquals(AvailabilityCalendar.dayLockAt(first).toString(), firstDate.get("day_locks_at").getAsString());
		assertEquals(AvailabilityCalendar.timeLockAt(first).toString(), firstDate.get("time_locks_at").getAsString());
		assertTrue(firstDate.get("hour").isJsonNull());
		assertTrue(firstDate.get("editable").getAsBoolean());
		assertFalse(firstDate.get("forced").getAsBoolean());
		assertEquals(AvailabilityCalendar.dayLockAt(first).toString(), voting.get("closes_at").getAsString());
		assertEquals(1, voting.getAsJsonObject("need").get("attacker").getAsInt(), "A side smaller than 3 needs all of it");
		assertEquals(1, voting.getAsJsonObject("need").get("defender").getAsInt());
		JsonObject eligible = voting.getAsJsonObject("eligible");
		assertEquals(2, eligible.size(), "One entry per account: " + eligible);
		assertEquals("attacker", eligible.get(ann.toString()).getAsString());
		assertEquals("defender", eligible.get(dee.toString()).getAsString());
		assertEquals(0, voting.getAsJsonObject("marks").size());
		assertEquals(0, voting.getAsJsonArray("none").size());
		assertEquals(35, voting.getAsJsonArray("tally").size(), "Seven dates of five hours");
		assertFalse(event.getAsJsonObject("state").toString().contains(ann.toString()), "Accounts stay private");
		assertFalse(event.getAsJsonObject("payload").toString().contains(ann.toString()));

		war.getAvailability().setMarks(ann, first, java.util.Set.of(22, 21));
		war.getAvailability().setMarks(ann, first.minusDays(1), java.util.Set.of(21));
		war.getAvailability().setMarks(dee, first.plusDays(2), java.util.Set.of(21));
		war.getAvailability().setNone(dee, first.plusDays(6));
		WarHistoryEvents.votesChanged(war);

		JsonObject changed = last("votes_changed");
		assertEquals(new JsonObject(), payload(changed), "Silent: nothing public");
		JsonObject marks = voting(changed).getAsJsonObject("marks");
		assertEquals("{\"" + first + "\":[21,22]}", marks.getAsJsonObject(ann.toString()).toString(),
				"Only the dates shown");
		assertEquals("{\"" + first.plusDays(2) + "\":[21]}", marks.getAsJsonObject(dee.toString()).toString());
		assertEquals(List.of(dee.toString()), strings(voting(changed).getAsJsonArray("none")));
		JsonObject row = voting(changed).getAsJsonArray("tally").get(0).getAsJsonObject();
		assertEquals(first.toString(), row.get("date").getAsString());
		assertEquals(21, row.get("hour").getAsInt());
		assertEquals(1, row.get("attacker").getAsInt());
		assertEquals(0, row.get("defender").getAsInt());
		JsonObject later = voting(changed).getAsJsonArray("tally").get(10).getAsJsonObject();
		assertEquals(first.plusDays(2).toString(), later.get("date").getAsString());
		assertEquals(1, later.get("defender").getAsInt());
	}

	@Test
	void aBattleDayShowsItsTimeLockAndADecidedDayCannotChange() {
		mainWindow();
		War war = war(61);
		war.setBattleSchedulePhase(net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase.VOTING);
		LocalDate first = AvailabilityCalendar.firstVisible(war, CampaignClock.now());
		war.getAvailability().putBattleDay(LockedBattleDay.open(first.plusDays(1), true));
		war.getAvailability().putBattleDay(LockedBattleDay.open(first.plusDays(3), false).withHour(22, false));
		war.getAvailability().setLastDayLockProcessed(first.plusDays(3));

		WarHistoryEvents.votesChanged(war);

		JsonArray dates = voting(last("votes_changed")).getAsJsonArray("dates");
		JsonObject noBattle = dates.get(0).getAsJsonObject();
		assertEquals("no_battle", noBattle.get("state").getAsString());
		assertFalse(noBattle.get("editable").getAsBoolean());
		JsonObject forced = dates.get(1).getAsJsonObject();
		assertEquals("battle", forced.get("state").getAsString());
		assertTrue(forced.get("editable").getAsBoolean());
		assertTrue(forced.get("forced").getAsBoolean());
		assertTrue(forced.get("hour").isJsonNull());
		JsonObject timed = dates.get(3).getAsJsonObject();
		assertEquals(22, timed.get("hour").getAsInt());
		assertFalse(timed.get("editable").getAsBoolean(), "Its time is already set");
		assertEquals(AvailabilityCalendar.timeLockAt(first.plusDays(1)).toString(),
				voting(last("votes_changed")).get("closes_at").getAsString(), "The next lock among the dates");

		war.setBattleSchedulePhase(net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase.IDLE);
		WarHistoryEvents.postponed(war);
		JsonObject none = voting(last("battle_postponed"));
		assertTrue(none.get("kind").isJsonNull(), "No battles to plan, no vote");
		assertEquals(1, none.size());
	}

	@Test
	void aReminderNamesItsAccountsOnlyInItsPrivateNotify() {
		War war = war(59);
		UUID ann = UUID.randomUUID();
		UUID dee = UUID.randomUUID();
		Instant locks = Instant.parse("2026-11-19T20:00:00Z");

		WarHistoryEvents.voteReminder(war, "time_lock_soon", LocalDate.parse("2026-11-20"), locks, List.of(ann, dee));
		WarHistoryEvents.voteReminder(war, "mark_availability", null, locks, List.of(ann));

		List<JsonObject> reminders = new ArrayList<>();
		events().stream().filter(e -> e.get("type").getAsString().equals("vote_reminder")).forEach(reminders::add);
		JsonObject soon = reminders.get(0);
		assertEquals("time_lock_soon", payload(soon).get("reason").getAsString());
		assertEquals("2026-11-20", payload(soon).get("battle_day").getAsString());
		assertEquals("2026-11-19T20:00:00Z", payload(soon).get("locks_at").getAsString());
		assertEquals(List.of(ann.toString(), dee.toString()),
				strings(soon.getAsJsonObject("private").getAsJsonObject("notify").getAsJsonArray("accounts")));
		assertFalse(payload(soon).toString().contains(ann.toString()));
		assertFalse(soon.has("notify"));
		assertTrue(payload(reminders.get(1)).get("battle_day").isJsonNull());
	}

	@Test
	void unreadableVotesLeaveThePrivateBlockWithoutThem() {
		War war = war(62);
		war.setBattleSchedulePhase(net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase.VOTING);
		Members.Resolver offline = mock(Members.Resolver.class);
		when(offline.info(anyString())).thenThrow(new IllegalStateException("characters offline"));
		Members.setResolver(offline);
		defender.addMember(UUID.randomUUID().toString());

		WarHistoryEvents.postponed(war);

		JsonObject event = last("battle_postponed");
		assertFalse(event.getAsJsonObject("private").has("voting"));
		verify(logger).warning("[WarHistory] Could not read the votes of war " + war.getId()
				+ ": java.lang.IllegalStateException: characters offline");
	}

	@Test
	void voteChangesAreSentAtMostOncePerWarEveryFiveSeconds() {
		java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000_000L);
		WarVoteChanges.clock = now::get;
		War war = war(63);
		buildup(war);
		War other = war(64);

		WarHistoryEvents.votesChanged(war);
		now.addAndGet(1000);
		WarHistoryEvents.votesChanged(war);
		war.getAvailability().setMarks(UUID.randomUUID(), war.getAvailability().getEarliestBattleDay(), java.util.Set.of(22));
		now.addAndGet(1000);
		WarHistoryEvents.votesChanged(war);
		WarHistoryEvents.votesChanged(other);
		assertEquals(2, count("votes_changed"), "The first change of each war goes at once");
		assertEquals(1, WarVoteChanges.waitingForTests());

		now.addAndGet(2000);
		WarVoteChanges.flushDue();
		assertEquals(2, count("votes_changed"), "Still inside the five seconds");

		now.addAndGet(1000);
		WarVoteChanges.flushDue();
		assertEquals(3, count("votes_changed"));
		assertEquals(1, voting(last("votes_changed")).getAsJsonObject("marks").size(), "The latest state wins");
		WarVoteChanges.flushDue();
		assertEquals(3, count("votes_changed"));

		now.addAndGet(1000);
		WarHistoryEvents.votesChanged(war);
		war.end(WarEndReason.WHITE_PEACE);
		now.addAndGet(10_000);
		WarVoteChanges.flushDue();
		assertEquals(3, count("votes_changed"), "An ended war's last state went with war_ended");

		now.addAndGet(10_000);
		WarVoteChanges.flushDue();
		WarHistoryEvents.votesChanged(other);
		assertEquals(4, count("votes_changed"), "A window that is over is forgotten");
	}

	@Test
	void voteChangesAreNotRecordedWhileHistoryIsOffAndNeverThrow() {
		War broken = mock(War.class);
		when(broken.getId()).thenReturn(8);
		when(broken.getStartedAt()).thenThrow(new IllegalStateException("no start"));

		WarHistoryEvents.votesChanged(null);
		WarHistoryEvents.votesChanged(broken);
		verify(logger).warning("[WarHistory] Could not record votes_changed for war 8: java.lang.IllegalStateException: no start");

		WarHistory.resetForTests();
		WarHistoryEvents.votesChanged(war(65));
		assertEquals(0, count("votes_changed"));
	}

	@Test
	void lockingCastingAndTimingBattleDaysAllTellTheWebsite() {
		mainWindow();
		java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(100_000L);
		WarVoteChanges.clock = now::get;
		War war = war(66);
		war.setBattleSchedulePhase(net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase.VOTING);
		LocalDate day = net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService
				.scheduleDate(CampaignClock.now()).plusDays(3);
		assertNull(AvailabilityScheduler.lockDay(war, day, CampaignClock.now()));
		assertEquals(1, count("votes_changed"), "Locking a day");
		now.addAndGet(10_000);
		BattleScheduleService.openVote(war);
		assertEquals(2, count("votes_changed"));
		now.addAndGet(10_000);
		net.tfminecraft.simplefactions.war.campaign.admin.WarScheduleAdminService.castVote(war, day.toString(), 21, "both");
		assertEquals(3, count("votes_changed"));
		now.addAndGet(10_000);
		assertNull(AvailabilityScheduler.unlockDay(war, day));
		assertEquals(4, count("votes_changed"), "Unlocking it");
		now.addAndGet(10_000);
		assertEquals("That day is not a battle day.", AvailabilityScheduler.unlockDay(war, day));
		assertEquals(4, count("votes_changed"), "Nothing changed, nothing sent");
	}

	@Test
	void aCallToArmsIsRecordedPrivatelyWithTheCalledLeadersAccount() {
		CharacterKeyFixture characters = CharacterKeyFixture.install();
		UUID cara = UUID.randomUUID();
		String leader = characters.offline(cara, "Cara");
		target.setLeader(leader);
		target.addMember(leader);
		Player sender = fixture.player("Alice");
		War war = war(67);

		CallToArmsService.send(sender, attacker, target, war);

		JsonObject event = last("call_to_arms_sent");
		JsonObject payload = payload(event);
		assertEquals("attacker", payload.getAsJsonObject("caller").get("id").getAsString());
		assertEquals("target", payload.getAsJsonObject("target").get("id").getAsString());
		assertEquals("attacker", payload.get("side").getAsString());
		assertFalse(payload.get("coalition").getAsBoolean());
		JsonObject notify = event.getAsJsonObject("private").getAsJsonObject("notify");
		assertEquals(cara.toString(), notify.get("leader_account").getAsString());
		assertEquals(leader, notify.get("leader_character_id").getAsString());
		assertTrue(event.getAsJsonObject("private").has("voting"));
		assertFalse(event.has("notify"), "Only war_declared has the Phase 3 notify field");
		assertFalse(payload.toString().contains(cara.toString()));
	}

	@Test
	void aCalledLeaderWithoutAKnownAccountIsSentWithoutOne() {
		War war = war(68);
		WarHistoryEvents.callToArmsSent(war, attacker, target, true);
		JsonObject event = last("call_to_arms_sent");
		JsonObject notify = event.getAsJsonObject("private").getAsJsonObject("notify");
		assertTrue(notify.get("leader_account").isJsonNull(), "Name-keyed test leaders have no account");
		assertTrue(notify.get("leader_character_id").isJsonNull());
		assertTrue(payload(event).get("coalition").getAsBoolean());

		Members.Resolver offline = mock(Members.Resolver.class);
		when(offline.info(anyString())).thenThrow(new IllegalStateException("characters offline"));
		Members.setResolver(offline);
		String key = UUID.randomUUID().toString();
		target.setLeader(key);
		WarHistoryEvents.callToArmsSent(war, attacker, target, false);
		notify = last("call_to_arms_sent").getAsJsonObject("private").getAsJsonObject("notify");
		assertTrue(notify.get("leader_account").isJsonNull());
		assertEquals(key, notify.get("leader_character_id").getAsString());
		verify(logger).warning("[WarHistory] Could not read the account of the called leader in war " + war.getId()
				+ ": java.lang.IllegalStateException: characters offline");
	}

	@Test
	void answeringACallClosesItOnTheWebsite() {
		Player sender = fixture.player("Alice");
		Player called = fixture.player("Cara");
		War war = war(69);
		CallToArmsService.send(sender, attacker, target, war);
		CallToArmsService.accept(called, war);
		JsonObject accepted = payload(last("call_to_arms_closed"));
		assertEquals("target", accepted.getAsJsonObject("target").get("id").getAsString());
		assertEquals("accepted", accepted.get("result").getAsString());

		Faction other = fixture.saved("other", "Olga");
		ally(attacker, other);
		War second = war(70);
		CallToArmsService.send(sender, attacker, other, second);
		CallToArmsService.decline(fixture.player("Olga"), second);
		assertEquals("declined", payload(last("call_to_arms_closed")).get("result").getAsString());
		assertEquals("other", payload(last("call_to_arms_closed")).getAsJsonObject("target").get("id").getAsString());
	}

	private int count(String type) {
		int found = 0;
		for (JsonObject event : events()) {
			if (event.get("type").getAsString().equals(type)) {
				found++;
			}
		}
		return found;
	}

	@Test
	void theVoteListsStayBounded() {
		CharacterKeyFixture characters = CharacterKeyFixture.install();
		for (int i = 0; i < 3; i++) {
			attacker.addMember(characters.offline(UUID.randomUUID(), "Ann " + i));
			defender.addMember(characters.offline(UUID.randomUUID(), "Dee " + i));
		}
		War war = war(71);
		buildup(war);
		for (int i = 0; i < 3; i++) {
			war.getAvailability().setMarks(UUID.randomUUID(), war.getAvailability().getEarliestBattleDay(), java.util.Set.of(21));
			war.getAvailability().setNone(UUID.randomUUID(), war.getAvailability().getEarliestBattleDay().plusDays(9));
		}
		int before = WarVoting.maxAccounts;
		WarVoting.maxAccounts = 2;
		try {
			WarHistoryEvents.postponed(war);
		} finally {
			WarVoting.maxAccounts = before;
		}
		JsonObject voting = voting(last("battle_postponed"));
		assertEquals(2, voting.getAsJsonObject("eligible").size());
		assertEquals(2, voting.getAsJsonObject("marks").size());
		assertEquals(2, voting.getAsJsonArray("none").size());
	}
}
