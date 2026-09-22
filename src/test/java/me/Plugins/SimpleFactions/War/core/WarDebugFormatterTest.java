package me.Plugins.SimpleFactions.War.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.commitment.WarCommitmentService;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarCommitment;
import me.Plugins.SimpleFactions.War.core.WarDebugFormatter;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;

class WarDebugFormatterTest {

	@AfterEach
	void tearDown() {
		WarCommitmentService.clearCommitments(3);
	}

	@Test
	void formatStatusLines_includesCommitmentRows() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");

		War war = new War(3, attacker, defender);
		seedCommitment(new WarCommitment(
				3, "faction_a", null, "militia", 4, Instant.parse("2026-08-21T10:00:00Z")));
		seedCommitment(new WarCommitment(
				3, "faction_a", "subject_a", WarCommitment.LEVY_REGIMENT_ID, 6, Instant.parse("2026-08-21T10:00:00Z")));

		String json = WarDebugFormatter.formatStatusLines(war).get(0);

		assertTrue(json.contains("\"commitments\":2"));
		assertTrue(json.contains("\"commitmentRows\""));
		assertTrue(json.contains("\"factionId\":\"faction_a\""));
		assertTrue(json.contains("\"regimentId\":\"militia\""));
		assertTrue(json.contains("\"count\":4"));
		assertTrue(json.contains("\"sourceFactionId\":\"subject_a\""));
		assertTrue(json.contains("\"regimentId\":\"levy\""));
		assertTrue(json.contains("\"count\":6"));
	}

	private static void seedCommitment(WarCommitment row) {
		try {
			java.lang.reflect.Field field = WarCommitmentService.class.getDeclaredField("commitmentsByWar");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<Integer, List<WarCommitment>> store =
					(Map<Integer, List<WarCommitment>>) field.get(null);
			store.computeIfAbsent(row.warId(), ignored -> new ArrayList<>()).add(row);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void formatStatusLines_includesCampaignFields() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");

		War war = new War(3, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(42);
		war.setCampaignStartProvinceId(17);
		war.setCampaignProvinces(java.util.List.of(17, 23, 42));
		war.setCursorIndex(1);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(3);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
		war.setCampaignBattlesFought(1);
		war.setLastBattleOccupied(java.util.List.of(23));
		war.setOccupiedByAttacker(java.util.List.of(23));
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setBattleDay(LocalDate.parse("2026-08-21"));
		war.setScheduledBattleHour(21);
		war.setScheduledBattleProvinceId(23);
		Map<UUID, Set<Integer>> votes = new HashMap<>();
		votes.put(UUID.randomUUID(), new HashSet<>(List.of(20, 21)));
		war.setBattleVotes(votes);
		war.setPostponementsThisCycle(2);

		List<String> lines = WarDebugFormatter.formatStatusLines(war);

		assertEquals(1, lines.size());
		String json = lines.get(0);
		assertTrue(json.contains("\"id\":3"));
		assertTrue(json.contains("\"goal\":\"subjugate\""));
		assertTrue(json.contains("\"status\":\"active\""));
		assertTrue(json.contains("\"objectiveProvinceId\":42"));
		assertTrue(json.contains("\"campaignStartProvinceId\":17"));
		assertTrue(json.contains("\"campaignProvinces\":[17,23,42]"));
		assertTrue(json.contains("\"cursorIndex\":1"));
		assertTrue(json.contains("\"cursorProvinceId\":23"));
		assertTrue(json.contains("\"initiativeAttacker\":4"));
		assertTrue(json.contains("\"initiativeDefender\":3"));
		assertTrue(json.contains("\"campaignPhase\":\"invasion\""));
		assertTrue(json.contains("\"campaignBattlesFought\":1"));
		assertTrue(json.contains("\"lastBattleOccupied\":[23]"));
		assertTrue(json.contains("\"nextBattleNodes\""));
		assertTrue(json.contains("\"battleSchedulePhase\":\"voting\""));
		assertTrue(json.contains("\"battleDay\":\"2026-08-21\""));
		assertTrue(json.contains("\"scheduledBattleHour\":21"));
		assertTrue(json.contains("\"scheduledBattleProvinceId\":23"));
		assertTrue(json.contains("\"battleVoteCount\":1"));
		assertTrue(json.contains("\"postponementsThisCycle\":2"));
	}

	@Test
	void formatStatusLines_includesNavalCampaignSchedule() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");

		War war = new War(3, attacker, defender);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(12, CampaignBattleKind.NAVAL, false, null, "port_a"),
				new ScheduledCampaignBattle(14, CampaignBattleKind.NAVAL_INVASION, false, null)));
		war.setCampaignScheduleIndex(0);

		String json = WarDebugFormatter.formatStatusLines(war).get(0);

		assertTrue(json.contains("\"kind\":\"naval\""));
		assertTrue(json.contains("\"portInstallationId\":\"port_a\""));
		assertTrue(json.contains("\"kind\":\"naval_invasion\""));
	}

	@Test
	void formatStatusLines_includesCounterSchedule() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");

		War war = new War(3, attacker, defender);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null)));
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(5, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignCounterScheduleIndex(1);

		String json = WarDebugFormatter.formatStatusLines(war).get(0);

		assertTrue(json.contains("\"campaignCounterScheduleIndex\":1"));
		assertTrue(json.contains("\"campaignCounterSchedule\""));
		assertTrue(json.contains("\"provinceId\":10"));
		assertTrue(json.contains("\"provinceId\":5"));
	}

	@Test
	void formatStatusLines_includesCampaignSchedule() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");

		War war = new War(3, attacker, defender);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "fort_a")));
		war.setCampaignScheduleIndex(1);

		String json = WarDebugFormatter.formatStatusLines(war).get(0);

		assertTrue(json.contains("\"campaignScheduleIndex\":1"));
		assertTrue(json.contains("\"campaignBattleSchedule\""));
		assertTrue(json.contains("\"provinceId\":18"));
		assertTrue(json.contains("\"kind\":\"siege\""));
		assertTrue(json.contains("\"fortInstallationId\":\"fort_a\""));
	}
}
