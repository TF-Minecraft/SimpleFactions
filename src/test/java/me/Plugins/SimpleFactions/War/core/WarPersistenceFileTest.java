package me.Plugins.SimpleFactions.War.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Database.JsonUtil;
import me.Plugins.SimpleFactions.Database.WarData;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarMapper;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarStatus;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;

class WarPersistenceFileTest {
	private Path tempDir;
	private final List<Faction> savedFactions = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		tempDir = Files.createTempDirectory("sf-war-persist-");
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();
		me.Plugins.SimpleFactions.Cache.warInitiativeFactor = 1.5;
	}

	@AfterEach
	void tearDown() throws Exception {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
		if (tempDir != null && Files.exists(tempDir)) {
			Files.walk(tempDir)
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
		}
	}

	@Test
	void warData_fileRoundTrip_preservesV2Fields() throws Exception {
		Faction attacker = mockFaction("faction_a");
		Faction defender = mockFaction("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		War war = new War(3, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setRelationTypeId("march");
		war.setGovernmentLawId("democracy");
		war.setLeadershipLawId("elected");
		war.setTargetSettlementId("town_a");
		war.setMovementId("mov-1");
		war.setStatus(WarStatus.ACTIVE);
		war.setTargetTitleId("county_x");
		war.setObjectiveProvinceId(42);
		war.setCampaignStartProvinceId(17);
		war.setCampaignProvinces(java.util.List.of(5, 17, 23, 42));
		war.setCursorIndex(1);
		war.setInitiativeAttacker(6);
		war.setInitiativeDefender(3);
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(5, CampaignBattleKind.FIELD, true, null),
				new ScheduledCampaignBattle(8, CampaignBattleKind.FIELD, false, null)));
		war.setCampaignCounterScheduleIndex(1);
		war.setOccupiedByAttacker(new java.util.ArrayList<>(java.util.List.of(17)));
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
		war.setWhitePeaceProposedByDefender(true);
		war.setCampaignBattlesFought(3);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setBattleDay(LocalDate.parse("2026-08-21"));
		war.setScheduledBattleHour(21);
		war.setScheduledBattleProvinceId(23);
		Map<UUID, Set<Integer>> votes = new HashMap<>();
		votes.put(UUID.fromString("00000000-0000-0000-0000-000000000003"), new HashSet<>(List.of(20)));
		war.setBattleVotes(votes);
		war.setPostponementsThisCycle(1);
		war.setStartedAt(Instant.parse("2026-08-19T12:00:00Z"));
		war.getDefenders().getMainParticipants().get(0).setCivilWar(true);

		WarData data = WarMapper.toData(war);
		File file = tempDir.resolve("war_3.json").toFile();
		JsonUtil.writeJson(file, data);

		WarData restoredData = JsonUtil.readJson(file, WarData.class);
		War restored = WarMapper.fromData(restoredData);

		assertNotNull(restoredData);
		assertEquals(3, restoredData.schemaVersion);
		assertEquals(3, restoredData.id);
		assertEquals("active", restoredData.status);
		assertEquals("subjugate", restoredData.goal);
		assertEquals("march", restoredData.relationTypeId);
		assertEquals("democracy", restoredData.governmentLawId);
		assertEquals("elected", restoredData.leadershipLawId);
		assertEquals("town_a", restoredData.targetSettlementId);
		assertEquals("mov-1", restoredData.movementId);
		assertEquals("faction_a", restoredData.attackerLeaderId);
		assertEquals("faction_b", restoredData.defenderLeaderId);
		assertEquals("county_x", restoredData.targetTitleId);
		assertEquals(Integer.valueOf(42), restoredData.objectiveProvinceId);
		assertEquals(Integer.valueOf(17), restoredData.campaignStartProvinceId);
		assertEquals(java.util.List.of(5, 17, 23, 42), restoredData.campaignProvinces);
		assertEquals(1, restoredData.cursorIndex);
		assertEquals(Integer.valueOf(6), restoredData.initiativeAttacker);
		assertEquals(Integer.valueOf(3), restoredData.initiativeDefender);
		assertEquals(2, restoredData.campaignCounterSchedule.size());
		assertEquals(Integer.valueOf(1), restoredData.campaignCounterScheduleIndex);
		assertEquals("invasion", restoredData.campaignPhase);
		assertTrue(restoredData.whitePeaceProposedByDefender);
		assertEquals(Integer.valueOf(3), restoredData.campaignBattlesFought);
		assertEquals("voting", restoredData.battleSchedulePhase);
		assertEquals("2026-08-21", restoredData.battleDay);
		assertEquals(Integer.valueOf(21), restoredData.scheduledBattleHour);
		assertEquals(Integer.valueOf(23), restoredData.scheduledBattleProvinceId);
		assertEquals(List.of(20), restoredData.battleVotes.get("00000000-0000-0000-0000-000000000003"));
		assertEquals(Integer.valueOf(1), restoredData.postponementsThisCycle);
		assertTrue(restoredData.defenders.participants.get(0).civilWar);

		assertNotNull(restored);
		assertEquals(3, restored.getId());
		assertEquals(WarGoalType.SUBJUGATE, restored.getGoal());
		assertEquals("march", restored.getRelationTypeId());
		assertEquals("democracy", restored.getGovernmentLawId());
		assertEquals("elected", restored.getLeadershipLawId());
		assertEquals("town_a", restored.getTargetSettlementId());
		assertEquals("mov-1", restored.getMovementId());
		assertEquals(WarStatus.ACTIVE, restored.getStatus());
		assertEquals("faction_a", restored.getAttackerLeaderId());
		assertEquals("faction_b", restored.getDefenderLeaderId());
		assertEquals("county_x", restored.getTargetTitleId());
		assertEquals(Integer.valueOf(42), restored.getObjectiveProvinceId());
		assertEquals(Integer.valueOf(17), restored.getCampaignStartProvinceId());
		assertEquals(java.util.List.of(5, 17, 23, 42), restored.getCampaignProvinces());
		assertEquals(1, restored.getCursorIndex());
		assertEquals(6, restored.getInitiativeAttacker());
		assertEquals(3, restored.getInitiativeDefender());
		assertEquals(2, restored.getCampaignCounterSchedule().size());
		assertEquals(1, restored.getCampaignCounterScheduleIndex());
		assertEquals(CampaignPhase.INVASION, restored.getCampaignPhase());
		assertEquals(ObjectiveHolder.DEFENDER, restored.getObjectiveHeldBy());
		assertTrue(restored.isWhitePeaceProposedByDefender());
		assertEquals(3, restored.getCampaignBattlesFought());
		assertEquals(BattleSchedulePhase.VOTING, restored.getBattleSchedulePhase());
		assertEquals(LocalDate.parse("2026-08-21"), restored.getBattleDay());
		assertEquals(21, restored.getScheduledBattleHour());
		assertEquals(Integer.valueOf(23), restored.getScheduledBattleProvinceId());
		assertEquals(1, restored.getBattleVotes().size());
		assertEquals(1, restored.getPostponementsThisCycle());
		assertTrue(restored.getDefenders().getMainParticipants().get(0).isCivilWar());
	}

	@Test
	void warData_fileRoundTrip_preservesInternalWarSnapshot() throws Exception {
		Faction attacker = mockFaction("dukeA");
		Faction defender = mockFaction("dukeB");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		War war = new War(4, attacker, defender);
		war.setInternalWar(true);
		war.setInternalTopLiegeId("king");

		WarData data = WarMapper.toData(war);
		File file = tempDir.resolve("war_4.json").toFile();
		JsonUtil.writeJson(file, data);

		WarData restoredData = JsonUtil.readJson(file, WarData.class);
		War restored = WarMapper.fromData(restoredData);

		assertEquals(Boolean.TRUE, restoredData.internalWar);
		assertEquals("king", restoredData.internalTopLiegeId);
		assertTrue(restored.isInternalWar());
		assertEquals("king", restored.getInternalTopLiegeId());
	}

	private static Faction mockFaction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		return faction;
	}
}
