package me.Plugins.SimpleFactions.War.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Database.WarData;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarMapper;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarStatus;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService.RaidKind;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;

class WarMapperTest {

	@BeforeEach
	void setUp() {
		Cache.warInitiativeFactor = 1.5;
	}

	@Test
	void toData_v2FieldsWhenGoalSetOnWar() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");

		Participant attackerParticipant = mock(Participant.class);
		when(attackerParticipant.getLeader()).thenReturn(attacker);
		when(attackerParticipant.getSubjects()).thenReturn(List.of());
		when(attackerParticipant.getAllies()).thenReturn(new HashMap<>());
		when(attackerParticipant.getBackers()).thenReturn(List.of());
		when(attackerParticipant.isCivilWar()).thenReturn(false);

		Participant defenderParticipant = mock(Participant.class);
		when(defenderParticipant.getLeader()).thenReturn(defender);
		when(defenderParticipant.getSubjects()).thenReturn(List.of());
		when(defenderParticipant.getAllies()).thenReturn(new HashMap<>());
		when(defenderParticipant.getBackers()).thenReturn(List.of());
		when(defenderParticipant.isCivilWar()).thenReturn(false);

		Side attackers = mock(Side.class);
		when(attackers.getLeader()).thenReturn(attacker);
		when(attackers.getMainParticipants()).thenReturn(List.of(attackerParticipant));

		Side defenders = mock(Side.class);
		when(defenders.getLeader()).thenReturn(defender);
		when(defenders.getMainParticipants()).thenReturn(List.of(defenderParticipant));

		War war = mock(War.class);
		when(war.getSchemaVersion()).thenReturn(2);
		when(war.getId()).thenReturn(7);
		when(war.getStatus()).thenReturn(WarStatus.ACTIVE);
		when(war.getGoal()).thenReturn(WarGoalType.SUBJUGATE);
		when(war.getWarType()).thenReturn(WarType.SUBJUGATE);
		when(war.getAttackerLeaderId()).thenReturn("faction_a");
		when(war.getDefenderLeaderId()).thenReturn("faction_b");
		when(war.getTargetTitleId()).thenReturn("county_x");
		when(war.getObjectiveProvinceId()).thenReturn(42);
		when(war.getCampaignStartProvinceId()).thenReturn(17);
		when(war.getCampaignProvinces()).thenReturn(List.of(17, 23, 42));
		when(war.getCursorIndex()).thenReturn(0);
		when(war.getInitiativeAttacker()).thenReturn(4);
		when(war.getInitiativeDefender()).thenReturn(3);
		when(war.getOccupiedByAttacker()).thenReturn(List.of(17));
		when(war.getOccupiedByDefender()).thenReturn(List.of());
		when(war.getLastBattleOccupied()).thenReturn(List.of(17));
		when(war.getCampaignPhase()).thenReturn(CampaignPhase.INVASION);
		when(war.getObjectiveHeldBy()).thenReturn(ObjectiveHolder.DEFENDER);
		when(war.isWhitePeaceProposedByAttacker()).thenReturn(false);
		when(war.isWhitePeaceProposedByDefender()).thenReturn(true);
		when(war.getCampaignBattlesFought()).thenReturn(2);
		when(war.getCampaignBattleSchedule()).thenReturn(List.of());
		when(war.getCampaignScheduleIndex()).thenReturn(0);
		when(war.getFortControllers()).thenReturn(Map.of("fort_a", CampaignCoalition.DEFENDER));
		when(war.getBattleSchedulePhase()).thenReturn(BattleSchedulePhase.SCHEDULED);
		when(war.getBattleDay()).thenReturn(LocalDate.parse("2026-08-21"));
		when(war.getScheduledBattleAt()).thenReturn(Instant.parse("2026-08-21T21:00:00Z"));
		when(war.getScheduledBattleHour()).thenReturn(21);
		when(war.getScheduledBattleProvinceId()).thenReturn(23);
		Map<UUID, Set<Integer>> votes = new HashMap<>();
		votes.put(UUID.fromString("00000000-0000-0000-0000-000000000001"), new HashSet<>(List.of(20, 21)));
		when(war.getBattleVotes()).thenReturn(votes);
		when(war.isAutoresolveProposedByAttacker()).thenReturn(true);
		when(war.isAutoresolveProposedByDefender()).thenReturn(false);
		when(war.getPostponementsThisCycle()).thenReturn(1);
		when(war.isDefenderChoiceResolved()).thenReturn(true);
		when(war.isForceQuorumNextClose()).thenReturn(true);
		when(war.getSubjectFactionId()).thenReturn(null);
		when(war.getRelationTypeId()).thenReturn("march");
		when(war.getStartedAt()).thenReturn(Instant.parse("2026-08-19T12:00:00Z"));
		when(war.getEndedAt()).thenReturn(null);
		when(war.getEndReason()).thenReturn(null);
		when(war.getAttackers()).thenReturn(attackers);
		when(war.getDefenders()).thenReturn(defenders);

		WarData data = WarMapper.toData(war);

		assertEquals(3, data.schemaVersion);
		assertEquals(7, data.id);
		assertEquals("subjugate", data.goal);
		assertEquals("subjugate", data.warType);
		assertEquals(WarStatus.ACTIVE.toJson(), data.status);
		assertEquals("faction_a", data.attackerLeaderId);
		assertEquals("faction_b", data.defenderLeaderId);
		assertEquals("county_x", data.targetTitleId);
		assertEquals("march", data.relationTypeId);
		assertEquals(Integer.valueOf(42), data.objectiveProvinceId);
		assertEquals(Integer.valueOf(17), data.campaignStartProvinceId);
		assertEquals(List.of(17, 23, 42), data.campaignProvinces);
		assertEquals(0, data.cursorIndex);
		assertEquals(Integer.valueOf(4), data.initiativeAttacker);
		assertEquals(Integer.valueOf(3), data.initiativeDefender);
		assertEquals(List.of(17), data.occupiedByAttacker);
		assertEquals(CampaignPhase.INVASION.toJson(), data.campaignPhase);
		assertEquals(ObjectiveHolder.DEFENDER.toJson(), data.objectiveHeldBy);
		assertTrue(data.whitePeaceProposedByDefender);
		assertEquals(Integer.valueOf(2), data.campaignBattlesFought);
		assertEquals("scheduled", data.battleSchedulePhase);
		assertEquals("2026-08-21", data.battleDay);
		assertEquals("2026-08-21T21:00:00Z", data.scheduledBattleAt);
		assertEquals(Integer.valueOf(21), data.scheduledBattleHour);
		assertEquals(Integer.valueOf(23), data.scheduledBattleProvinceId);
		assertEquals(List.of(20, 21), data.battleVotes.get("00000000-0000-0000-0000-000000000001"));
		assertTrue(data.autoresolveProposedByAttacker);
		assertEquals(Integer.valueOf(1), data.postponementsThisCycle);
		assertTrue(data.defenderChoiceResolved);
		assertTrue(data.forceQuorumNextClose);
		assertEquals("2026-08-19T12:00:00Z", data.startedAt);
		assertNull(data.endReason);
		assertTrue(data.attackers.participants.get(0).warGoals.isEmpty());
		assertTrue(data.attackers.participants.get(0).backers.isEmpty());
	}

	@Test
	void roundTrip_participantBackers() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		Faction backer = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		when(backer.getId()).thenReturn("brume");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		FactionManager.factions.add(backer);
		try {
			War war = new War(42, attacker, defender);
			war.getAttackers().getMainParticipants().get(0).addBacker(backer);

			WarData data = WarMapper.toData(war);
			assertEquals(List.of("brume"), data.attackers.participants.get(0).backers);

			War restored = WarMapper.fromData(data);
			boolean foundBacker = false;
			for (Participant participant : restored.getAttackers().getMainParticipants()) {
				for (Faction loaded : participant.getBackers()) {
					if (loaded != null && "brume".equalsIgnoreCase(loaded.getId())) {
						foundBacker = true;
					}
				}
			}
			assertTrue(foundBacker);
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
			FactionManager.factions.remove(backer);
		}
	}

	@Test
	void fromData_missingBackersDefaultsEmpty() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = minimalWarData();
			data.attackers.participants.get(0).backers = null;

			War war = WarMapper.fromData(data);
			for (Participant participant : war.getAttackers().getMainParticipants()) {
				assertNotNull(participant.getBackers());
				assertTrue(participant.getBackers().isEmpty());
			}
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void fromData_defaultsMissingCampaignBattlesFoughtAndInitiativeHolder() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = new WarData();
			data.schemaVersion = 2;
			data.id = 9;
			data.status = "active";
			data.attackers = new me.Plugins.SimpleFactions.Database.SideData();
			data.attackers.leader = "faction_a";
			data.defenders = new me.Plugins.SimpleFactions.Database.SideData();
			data.defenders.leader = "faction_b";

			War war = WarMapper.fromData(data);

			assertEquals(0, war.getCampaignBattlesFought());
			assertEquals(BelligerentRole.ATTACKER, war.getInitiativeHolder());
			assertEquals(CampaignCoalition.AGGRESSOR, war.getInitiativeHolderCoalition());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void fromData_legacyMissingScheduleFieldsUsesDefaults() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = new WarData();
			data.id = 9;
			data.status = "active";
			data.attackers = new me.Plugins.SimpleFactions.Database.SideData();
			data.attackers.leader = "faction_a";
			me.Plugins.SimpleFactions.Database.ParticipantData atk = new me.Plugins.SimpleFactions.Database.ParticipantData();
			atk.leader = "faction_a";
			data.attackers.participants.add(atk);
			data.defenders = new me.Plugins.SimpleFactions.Database.SideData();
			data.defenders.leader = "faction_b";
			me.Plugins.SimpleFactions.Database.ParticipantData def = new me.Plugins.SimpleFactions.Database.ParticipantData();
			def.leader = "faction_b";
			data.defenders.participants.add(def);

			War war = WarMapper.fromData(data);

			assertEquals(BattleSchedulePhase.IDLE, war.getBattleSchedulePhase());
			assertNull(war.getBattleDay());
			assertNull(war.getScheduledBattleAt());
			assertEquals(0, war.getScheduledBattleHour());
			assertTrue(war.getBattleVotes().isEmpty());
			assertEquals(0, war.getPostponementsThisCycle());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void fromData_v2DerivesCoalitionAndPushTarget() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = new WarData();
			data.schemaVersion = 2;
			data.id = 11;
			data.status = "active";
			data.campaignPhase = CampaignPhase.COUNTER_PUSH.toJson();
			data.objectiveHeldBy = ObjectiveHolder.DEFENDER.toJson();
			data.initiativeHolder = BelligerentRole.DEFENDER.name();
			data.attackers = new me.Plugins.SimpleFactions.Database.SideData();
			data.attackers.leader = "faction_a";
			me.Plugins.SimpleFactions.Database.ParticipantData atk = new me.Plugins.SimpleFactions.Database.ParticipantData();
			atk.leader = "faction_a";
			data.attackers.participants.add(atk);
			data.defenders = new me.Plugins.SimpleFactions.Database.SideData();
			data.defenders.leader = "faction_b";
			me.Plugins.SimpleFactions.Database.ParticipantData def = new me.Plugins.SimpleFactions.Database.ParticipantData();
			def.leader = "faction_b";
			data.defenders.participants.add(def);

			War war = WarMapper.fromData(data);

			assertEquals(CampaignCoalition.DEFENDER, war.getInitiativeHolderCoalition());
			assertEquals(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL, war.getPushTarget());
			assertEquals(PostBattleChoicePhase.NONE, war.getPostBattleChoicePhase());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void fromData_v3ReadsRuntimeFields() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = new WarData();
			data.schemaVersion = 3;
			data.id = 12;
			data.status = "active";
			data.initiativeHolderCoalition = CampaignCoalition.DEFENDER.toJson();
			data.pushTarget = CampaignPushTarget.RETAKE_OBJECTIVE.toJson();
			data.postBattleChoicePhase = PostBattleChoicePhase.LOSER_ATTACK_PEACE.toJson();
			data.postBattleWinnerCoalition = CampaignCoalition.AGGRESSOR.toJson();
			data.postBattleChoiceResolved = false;
			data.lastBattleOffensiveCoalition = CampaignCoalition.AGGRESSOR.toJson();
			data.holdPeaceProposalActive = true;
			data.campaignPhase = CampaignPhase.INVASION.toJson();
			data.objectiveHeldBy = ObjectiveHolder.ATTACKER.toJson();
			data.attackers = new me.Plugins.SimpleFactions.Database.SideData();
			data.attackers.leader = "faction_a";
			me.Plugins.SimpleFactions.Database.ParticipantData atk = new me.Plugins.SimpleFactions.Database.ParticipantData();
			atk.leader = "faction_a";
			data.attackers.participants.add(atk);
			data.defenders = new me.Plugins.SimpleFactions.Database.SideData();
			data.defenders.leader = "faction_b";
			me.Plugins.SimpleFactions.Database.ParticipantData def = new me.Plugins.SimpleFactions.Database.ParticipantData();
			def.leader = "faction_b";
			data.defenders.participants.add(def);

			War war = WarMapper.fromData(data);

			assertEquals(CampaignCoalition.DEFENDER, war.getInitiativeHolderCoalition());
			assertEquals(CampaignPushTarget.RETAKE_OBJECTIVE, war.getPushTarget());
			assertEquals(PostBattleChoicePhase.LOSER_ATTACK_PEACE, war.getPostBattleChoicePhase());
			assertEquals(CampaignCoalition.AGGRESSOR, war.getPostBattleWinnerCoalition());
			assertFalse(war.isPostBattleChoiceResolved());
			assertEquals(CampaignCoalition.AGGRESSOR, war.getLastBattleOffensiveCoalition());
			assertTrue(war.isHoldPeaceProposalActive());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void roundTrip_campaignBattleSchedule() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = minimalWarData();
			data.campaignScheduleIndex = 1;
			me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData field = new me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData();
			field.provinceId = 10;
			field.kind = CampaignBattleKind.FIELD.toJson();
			me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData siege = new me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData();
			siege.provinceId = 20;
			siege.kind = CampaignBattleKind.SIEGE.toJson();
			siege.fortInstallationId = "fort_a";
			me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData objective = new me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData();
			objective.provinceId = 42;
			objective.kind = CampaignBattleKind.FIELD.toJson();
			objective.required = true;
			data.campaignBattleSchedule = List.of(field, siege, objective);

			War war = WarMapper.fromData(data);
			assertEquals(3, war.getCampaignBattleSchedule().size());
			assertEquals(1, war.getCampaignScheduleIndex());
			assertEquals(CampaignBattleKind.FIELD, war.getCampaignBattleSchedule().get(0).kind());
			assertEquals(CampaignBattleKind.SIEGE, war.getCampaignBattleSchedule().get(1).kind());
			assertEquals("fort_a", war.getCampaignBattleSchedule().get(1).fortInstallationId());
			assertTrue(war.getCampaignBattleSchedule().get(2).required());

			WarData roundTripped = WarMapper.toData(war);
			assertEquals(1, roundTripped.campaignScheduleIndex.intValue());
			assertEquals(3, roundTripped.campaignBattleSchedule.size());
			assertEquals("siege", roundTripped.campaignBattleSchedule.get(1).kind);
			assertEquals("fort_a", roundTripped.campaignBattleSchedule.get(1).fortInstallationId);
			assertTrue(roundTripped.campaignBattleSchedule.get(2).required);

			War reloaded = WarMapper.fromData(roundTripped);
			assertEquals(war.getCampaignBattleSchedule(), reloaded.getCampaignBattleSchedule());
			assertEquals(war.getCampaignScheduleIndex(), reloaded.getCampaignScheduleIndex());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void roundTrip_chronologyProvinceId() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			War war = WarMapper.fromData(minimalWarData());
			war.setCampaignBattleSchedule(List.of(new ScheduledCampaignBattle(
					704,
					CampaignBattleKind.SIEGE,
					false,
					"Lan_Airfield",
					null,
					713)));

			WarData data = WarMapper.toData(war);
			assertEquals(Integer.valueOf(713), data.campaignBattleSchedule.get(0).chronologyProvinceId);

			War reloaded = WarMapper.fromData(data);
			ScheduledCampaignBattle slot = reloaded.getCampaignBattleSchedule().get(0);
			assertEquals(704, slot.provinceId());
			assertEquals(713, slot.chronologyProvinceId());
			assertEquals(713, slot.sortProvinceId());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void roundTrip_counterScheduleAndAsymmetricFuel() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = minimalWarData();
			data.campaignScheduleIndex = 1;
			data.initiativeAttacker = 6;
			data.initiativeDefender = 3;
			data.campaignCounterScheduleIndex = 1;
			me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData invasion = new me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData();
			invasion.provinceId = 10;
			invasion.kind = CampaignBattleKind.FIELD.toJson();
			me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData counter = new me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData();
			counter.provinceId = 5;
			counter.kind = CampaignBattleKind.FIELD.toJson();
			counter.required = true;
			data.campaignBattleSchedule = List.of(invasion, invasion, invasion, invasion);
			data.campaignCounterSchedule = List.of(counter, counter);

			War war = WarMapper.fromData(data);
			assertEquals(4, war.getCampaignBattleSchedule().size());
			assertEquals(2, war.getCampaignCounterSchedule().size());
			assertEquals(1, war.getCampaignScheduleIndex());
			assertEquals(1, war.getCampaignCounterScheduleIndex());
			assertEquals(6, war.getInitiativeAttacker());
			assertEquals(3, war.getInitiativeDefender());

			WarData roundTripped = WarMapper.toData(war);
			assertEquals(2, roundTripped.campaignCounterSchedule.size());
			assertEquals(Integer.valueOf(1), roundTripped.campaignCounterScheduleIndex);
			assertEquals(Integer.valueOf(6), roundTripped.initiativeAttacker);
			assertEquals(Integer.valueOf(3), roundTripped.initiativeDefender);

			War reloaded = WarMapper.fromData(roundTripped);
			assertEquals(war.getCampaignCounterSchedule(), reloaded.getCampaignCounterSchedule());
			assertEquals(war.getCampaignCounterScheduleIndex(), reloaded.getCampaignCounterScheduleIndex());
			assertEquals(war.getInitiativeAttacker(), reloaded.getInitiativeAttacker());
			assertEquals(war.getInitiativeDefender(), reloaded.getInitiativeDefender());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void fromData_pre70Json_symmetricFuelDefaults() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = minimalWarData();
			me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData slot = new me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData();
			slot.provinceId = 10;
			slot.kind = CampaignBattleKind.FIELD.toJson();
			data.campaignBattleSchedule = List.of(slot, slot, slot, slot);
			data.campaignCounterSchedule = null;
			data.initiativeAttacker = null;
			data.initiativeDefender = null;

			War war = WarMapper.fromData(data);

			assertEquals(6, war.getInitiativeAttacker());
			assertEquals(6, war.getInitiativeDefender());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void fromData_emptyCounterSchedule_defenderFuelZero() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = minimalWarData();
			me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData slot = new me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData();
			slot.provinceId = 10;
			slot.kind = CampaignBattleKind.FIELD.toJson();
			data.campaignBattleSchedule = List.of(slot, slot, slot, slot);
			data.campaignCounterSchedule = new java.util.ArrayList<>();
			data.initiativeAttacker = null;
			data.initiativeDefender = null;

			War war = WarMapper.fromData(data);

			assertEquals(6, war.getInitiativeAttacker());
			assertEquals(0, war.getInitiativeDefender());
			assertTrue(war.getCampaignCounterSchedule().isEmpty());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void fromData_missingScheduleDefaultsEmpty() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = minimalWarData();
			data.campaignBattleSchedule = null;
			data.campaignScheduleIndex = null;

			War war = WarMapper.fromData(data);

			assertTrue(war.getCampaignBattleSchedule().isEmpty());
			assertEquals(0, war.getCampaignScheduleIndex());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void roundTrip_concededScheduleSlots() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = minimalWarData();
			data.concededScheduleSlots = List.of("invasion:0", "counter:1");

			War war = WarMapper.fromData(data);
			assertTrue(war.getConcededScheduleSlots().contains("invasion:0"));
			assertTrue(war.getConcededScheduleSlots().contains("counter:1"));
			assertEquals(2, war.getConcededScheduleSlots().size());

			WarData roundTripped = WarMapper.toData(war);
			assertEquals(List.of("invasion:0", "counter:1"), roundTripped.concededScheduleSlots);

			War reloaded = WarMapper.fromData(roundTripped);
			assertEquals(war.getConcededScheduleSlots(), reloaded.getConcededScheduleSlots());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void roundTrip_fortControllers() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = minimalWarData();
			data.fortControllers = new HashMap<>();
			data.fortControllers.put("fort_a", CampaignCoalition.DEFENDER.toJson());
			data.fortControllers.put("fort_b", CampaignCoalition.AGGRESSOR.toJson());
			data.fortControllers.put("fort_bad", "unknown");

			War war = WarMapper.fromData(data);
			assertEquals(CampaignCoalition.DEFENDER, war.getFortControllers().get("fort_a"));
			assertEquals(CampaignCoalition.AGGRESSOR, war.getFortControllers().get("fort_b"));
			assertFalse(war.getFortControllers().containsKey("fort_bad"));

			WarData roundTripped = WarMapper.toData(war);
			assertEquals("defender", roundTripped.fortControllers.get("fort_a"));
			assertEquals("aggressor", roundTripped.fortControllers.get("fort_b"));
			assertFalse(roundTripped.fortControllers.containsKey("fort_bad"));

			War reloaded = WarMapper.fromData(roundTripped);
			assertEquals(war.getFortControllers(), reloaded.getFortControllers());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void fromData_missingFortControllersDefaultsEmpty() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			WarData data = minimalWarData();
			data.fortControllers = null;

			War war = WarMapper.fromData(data);

			assertTrue(war.getFortControllers().isEmpty());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void roundTrip_battleInstallationPicks() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			War war = new War(42, attacker, defender);
			war.setBattleDay(LocalDate.parse("2026-08-21"));
			LinkedHashSet<String> atkPicks = new LinkedHashSet<>();
			atkPicks.add("fort-1");
			atkPicks.add("airport-2");
			Map<String, LinkedHashSet<String>> picks = new LinkedHashMap<>();
			picks.put("faction_a", atkPicks);
			war.setBattleInstallationPicks(picks);
			war.setBattleInstallationPicksBattleDay(LocalDate.parse("2026-08-21"));

			WarData data = WarMapper.toData(war);
			assertEquals(List.of("fort-1", "airport-2"), data.battleInstallationPicks.get("faction_a"));
			assertEquals("2026-08-21", data.battleInstallationPicksBattleDay);

			War restored = WarMapper.fromData(data);
			assertEquals(Set.of("fort-1", "airport-2"), restored.getBattleInstallationPicks().get("faction_a"));
			assertEquals(LocalDate.parse("2026-08-21"), restored.getBattleInstallationPicksBattleDay());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void roundTrip_wartimeInstallationOwners() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			War war = new War(42, attacker, defender);
			war.putWartimeInstallationOwner("port-1", "faction_b");

			WarData data = WarMapper.toData(war);
			assertEquals("faction_b", data.wartimeInstallationOwners.get("port-1"));

			War restored = WarMapper.fromData(data);
			assertEquals("faction_b", restored.getWartimeInstallationOwners().get("port-1"));
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void roundTrip_campaignRaidFields() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			War war = new War(42, attacker, defender);
			war.setBattleDay(LocalDate.parse("2026-08-21"));
			war.getCampaignRaidsUsed().put(CampaignCoalition.AGGRESSOR.toJson(), "2026-08-21");
			war.getRaidRepairLockUntil().put(
					"port-def",
					Instant.parse("2026-08-27T20:00:00+02:00"));

			CampaignRaid raid = new CampaignRaid();
			raid.setId("harbor_raid");
			raid.setDisplayName("Harbor Raid");
			raid.setWarId(42);
			raid.setBattleDay(LocalDate.parse("2026-08-21"));
			raid.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
			raid.setLauncherFactionId("faction_a");
			raid.setSourceInstallationId("port-atk");
			raid.setTargetInstallationId("port-def");
			raid.setRaidKind(RaidKind.NAVAL);
			raid.setState(CampaignRaidState.MUSTER);
			raid.setMusterEndsAt(Instant.parse("2026-08-21T19:01:00+02:00"));
			raid.getMusterParticipantIds().add("00000000-0000-0000-0000-0000000000aa");
			war.setActiveCampaignRaid(raid);

			WarData data = WarMapper.toData(war);
			assertEquals("2026-08-21", data.campaignRaidsUsed.get("aggressor"));
			assertNotNull(data.activeCampaignRaid);
			assertEquals("harbor_raid", data.activeCampaignRaid.id);
			assertEquals("Harbor Raid", data.activeCampaignRaid.displayName);
			assertEquals("port-def", data.raidRepairLockUntil.keySet().iterator().next());

			War restored = WarMapper.fromData(data);
			assertEquals("2026-08-21", restored.getCampaignRaidsUsed().get("aggressor"));
			assertNotNull(restored.getActiveCampaignRaid());
			assertEquals(CampaignRaidState.MUSTER, restored.getActiveCampaignRaid().getState());
			assertEquals(RaidKind.NAVAL, restored.getActiveCampaignRaid().getRaidKind());
			assertTrue(restored.getActiveCampaignRaid().getMusterParticipantIds()
					.contains("00000000-0000-0000-0000-0000000000aa"));
			assertEquals(
					Instant.parse("2026-08-27T20:00:00+02:00"),
					restored.getRaidRepairLockUntil().get("port-def"));
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	@Test
	void fromData_omittedInstallationPicksDefaultsEmpty() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("faction_a");
		when(defender.getId()).thenReturn("faction_b");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		try {
			War war = WarMapper.fromData(minimalWarData());
			assertTrue(war.getBattleInstallationPicks().isEmpty());
			assertNull(war.getBattleInstallationPicksBattleDay());
		} finally {
			FactionManager.factions.remove(attacker);
			FactionManager.factions.remove(defender);
		}
	}

	private static WarData minimalWarData() {
		WarData data = new WarData();
		data.id = 9;
		data.status = "active";
		data.attackers = new me.Plugins.SimpleFactions.Database.SideData();
		data.attackers.leader = "faction_a";
		me.Plugins.SimpleFactions.Database.ParticipantData atk = new me.Plugins.SimpleFactions.Database.ParticipantData();
		atk.leader = "faction_a";
		data.attackers.participants.add(atk);
		data.defenders = new me.Plugins.SimpleFactions.Database.SideData();
		data.defenders.leader = "faction_b";
		me.Plugins.SimpleFactions.Database.ParticipantData def = new me.Plugins.SimpleFactions.Database.ParticipantData();
		def.leader = "faction_b";
		data.defenders.participants.add(def);
		return data;
	}
}
