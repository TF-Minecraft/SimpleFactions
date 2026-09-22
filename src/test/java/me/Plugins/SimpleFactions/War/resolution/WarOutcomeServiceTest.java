package me.Plugins.SimpleFactions.War.resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.LawHandler;
import me.Plugins.SimpleFactions.Objects.Handler.ProvinceHandler;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.StabilityModifier;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.MovementOutcomeService;
import me.Plugins.SimpleFactions.government.movement.MovementOutcomeSource;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

class WarOutcomeServiceTest {
	private final List<Faction> savedFactions = new ArrayList<>();

	@BeforeEach
	void setUp() {
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();
		Cache.warReparationsIncomePercent = 25;
		Cache.warReparationsDays = 10;
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
	}

	@Test
	void attackerVictory_doesNotSetRelationOrUsurp() {
		Fixture fx = fixture();
		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			relations.verify(
					() -> RelationManager.setRelation(
							any(), any(RelationType.class), any(), any(), anyBoolean()),
					never());
			relations.verify(
					() -> RelationManager.setRelation(
							any(), any(RelationType.class), any(), any(), anyBoolean(), anyBoolean()),
					never());
			relations.verify(() -> RelationManager.setRelationForced(any(), any(), any()), never());
			factions.verify(() -> FactionManager.usurp(any(Player.class), any(), any()), never());
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_warGoal_doesNotSetRelationOrUsurp() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.WAR);
		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			relations.verify(
					() -> RelationManager.setRelation(
							any(), any(RelationType.class), any(), any(), anyBoolean()),
					never());
			relations.verify(
					() -> RelationManager.setRelation(
							any(), any(RelationType.class), any(), any(), anyBoolean(), anyBoolean()),
					never());
			relations.verify(() -> RelationManager.setRelationForced(any(), any(), any()), never());
			factions.verify(() -> FactionManager.usurp(any(Player.class), any(), any()), never());
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_tributary_setsForcedTributary() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.TRIBUTARY);
		RelationType tributary = mock(RelationType.class);
		when(tributary.getId()).thenReturn("tributary");
		try (MockedStatic<RelationLoader> loader = mockStatic(RelationLoader.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			loader.when(() -> RelationLoader.getType("tributary")).thenReturn(tributary);
			relations.when(() -> RelationManager.setRelationForced(tributary, fx.defender, fx.attacker))
					.thenReturn(true);
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			relations.verify(() -> RelationManager.setRelationForced(tributary, fx.defender, fx.attacker));
			factions.verify(() -> FactionManager.usurp(any(Player.class), any(), any()), never());
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_tributary_missingType_doesNotSetRelation() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.TRIBUTARY);
		try (MockedStatic<RelationLoader> loader = mockStatic(RelationLoader.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			loader.when(() -> RelationLoader.getType("tributary")).thenReturn(null);
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			relations.verify(() -> RelationManager.setRelationForced(any(), any(), any()), never());
		}
	}

	@Test
	void attackerVictory_subjugate_setsForcedChosenType() {
		Fixture fx = fixture();
		fx.war.setRelationTypeId("march");
		RelationType march = mock(RelationType.class);
		when(march.getId()).thenReturn("march");
		try (MockedStatic<RelationLoader> loader = mockStatic(RelationLoader.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			loader.when(() -> RelationLoader.getType("march")).thenReturn(march);
			loader.when(() -> RelationLoader.isWarPickableVassal(march)).thenReturn(true);
			relations.when(() -> RelationManager.setRelationForced(march, fx.defender, fx.attacker))
					.thenReturn(true);
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			relations.verify(() -> RelationManager.setRelationForced(march, fx.defender, fx.attacker));
			relations.verify(() -> RelationManager.transferSubject(any(), any()), never());
			factions.verify(() -> FactionManager.usurp(any(Player.class), any(), any()), never());
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_internalSubjugate_transfersThenSetsChosenType() {
		Fixture fx = fixture();
		fx.war.setInternalWar(true);
		fx.war.setRelationTypeId("march");
		RelationType march = mock(RelationType.class);
		when(march.getId()).thenReturn("march");
		try (MockedStatic<RelationLoader> loader = mockStatic(RelationLoader.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			loader.when(() -> RelationLoader.getType("march")).thenReturn(march);
			loader.when(() -> RelationLoader.isWarPickableVassal(march)).thenReturn(true);
			relations.when(() -> RelationManager.setRelationForced(march, fx.defender, fx.attacker))
					.thenReturn(true);
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			relations.verify(() -> RelationManager.transferSubject(fx.defender, fx.attacker));
			relations.verify(() -> RelationManager.setRelationForced(march, fx.defender, fx.attacker));
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_internalSubjugate_skipsForcedWhenTypeAlreadySet() {
		Fixture fx = fixture();
		fx.war.setInternalWar(true);
		fx.war.setRelationTypeId("march");
		RelationType march = mock(RelationType.class);
		when(march.getId()).thenReturn("march");
		Relation existing = mock(Relation.class);
		when(existing.getType()).thenReturn(march);
		when(fx.attacker.getRelation("def")).thenReturn(existing);
		try (MockedStatic<RelationLoader> loader = mockStatic(RelationLoader.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			loader.when(() -> RelationLoader.getType("march")).thenReturn(march);
			loader.when(() -> RelationLoader.isWarPickableVassal(march)).thenReturn(true);
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			relations.verify(() -> RelationManager.transferSubject(fx.defender, fx.attacker));
			relations.verify(() -> RelationManager.setRelationForced(any(), any(), any()), never());
		}
	}

	@Test
	void attackerVictory_subjugate_missingType_doesNotSetRelation() {
		Fixture fx = fixture();
		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			relations.verify(() -> RelationManager.setRelationForced(any(), any(), any()), never());
		}
	}

	@Test
	void attackerVictory_transferSubject_callsTransfer() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.TRANSFER_SUBJECT);
		fx.war.setSubjectFactionId("subject");
		Faction subject = mock(Faction.class);
		when(subject.getId()).thenReturn("subject");
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			factions.when(() -> FactionManager.getByString("subject")).thenReturn(subject);
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			relations.verify(() -> RelationManager.transferSubject(subject, fx.attacker));
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_transferSubject_missingPayload_doesNotTransfer() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.TRANSFER_SUBJECT);
		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			relations.verify(() -> RelationManager.transferSubject(any(), any()), never());
		}
	}

	@Test
	void attackerVictory_usurp_callsUsurpWithNoPlayer() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.USURP);
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			factions.verify(() -> FactionManager.usurp(isNull(), eq(fx.attacker), eq(fx.defender)));
			relations.verify(() -> RelationManager.setRelationForced(any(), any(), any()), never());
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void whitePeace_usurp_doesNotCallUsurp() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.USURP);
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			WarOutcomeService.apply(fx.war, WarEndReason.WHITE_PEACE);
			factions.verify(() -> FactionManager.usurp(any(Player.class), any(), any()), never());
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_openMarket_appliesLawAndStability() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.OPEN_MARKET);
		Cache.openMarketApplyDefenderLaw = "free_trade";
		Law law = mock(Law.class);
		when(law.getId()).thenReturn("free_trade");
		LawGroup group = mock(LawGroup.class);
		when(group.getLaws()).thenReturn(java.util.Map.of("free_trade", law));
		LawHandler handler = mock(LawHandler.class);
		when(handler.getGroupList()).thenReturn(List.of(group));
		when(fx.defender.getLawHandler()).thenReturn(handler);
		Government government = mock(Government.class);
		when(fx.defender.getGovernment()).thenReturn(government);
		try {
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
		} finally {
			Cache.openMarketApplyDefenderLaw = "";
		}
		verify(fx.defender).applyLaw(law, group);
		ArgumentCaptor<StabilityModifier> captor = ArgumentCaptor.forClass(StabilityModifier.class);
		verify(government).addStabilityModifier(captor.capture());
		assertEquals("Forced Market Open", captor.getValue().getName());
		assertEquals(-25, captor.getValue().getModifier());
		assertEquals(1, captor.getValue().getDecay());
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void whitePeace_openMarket_doesNotApplyLaw() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.OPEN_MARKET);
		Cache.openMarketApplyDefenderLaw = "free_trade";
		Law law = mock(Law.class);
		when(law.getId()).thenReturn("free_trade");
		LawGroup group = mock(LawGroup.class);
		when(group.getLaws()).thenReturn(java.util.Map.of("free_trade", law));
		LawHandler handler = mock(LawHandler.class);
		when(handler.getGroupList()).thenReturn(List.of(group));
		when(fx.defender.getLawHandler()).thenReturn(handler);
		Government government = mock(Government.class);
		when(fx.defender.getGovernment()).thenReturn(government);
		try {
			WarOutcomeService.apply(fx.war, WarEndReason.WHITE_PEACE);
		} finally {
			Cache.openMarketApplyDefenderLaw = "";
		}
		verify(fx.defender, never()).applyLaw(any(), any());
		verify(government, never()).addStabilityModifier(any());
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_changeGovernment_appliesChangedLawsAndStability() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.CHANGE_GOVERNMENT);
		fx.war.setGovernmentLawId("democracy");
		fx.war.setLeadershipLawId("fixed");
		Law autocracy = mock(Law.class);
		when(autocracy.getId()).thenReturn("autocracy");
		Law democracy = mock(Law.class);
		when(democracy.getId()).thenReturn("democracy");
		LawGroup government = mock(LawGroup.class);
		when(government.getCurrent()).thenReturn(autocracy);
		when(government.getLaws()).thenReturn(java.util.Map.of("autocracy", autocracy, "democracy", democracy));
		Law fixed = mock(Law.class);
		when(fixed.getId()).thenReturn("fixed");
		LawGroup leadership = mock(LawGroup.class);
		when(leadership.getCurrent()).thenReturn(fixed);
		when(leadership.getLaws()).thenReturn(java.util.Map.of("fixed", fixed));
		LawHandler handler = mock(LawHandler.class);
		when(handler.getGroupList()).thenReturn(List.of(government, leadership));
		when(fx.defender.getLawHandler()).thenReturn(handler);
		Government gov = mock(Government.class);
		when(fx.defender.getGovernment()).thenReturn(gov);

		WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);

		verify(fx.defender).applyLaw(democracy, government);
		verify(fx.defender, never()).applyLaw(eq(fixed), any());
		ArgumentCaptor<StabilityModifier> captor = ArgumentCaptor.forClass(StabilityModifier.class);
		verify(gov).addStabilityModifier(captor.capture());
		assertEquals("Forced Government Change", captor.getValue().getName());
		assertEquals(-50, captor.getValue().getModifier());
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void whitePeace_changeGovernment_doesNotApplyLaw() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.CHANGE_GOVERNMENT);
		fx.war.setGovernmentLawId("democracy");
		Law democracy = mock(Law.class);
		when(democracy.getId()).thenReturn("democracy");
		LawGroup government = mock(LawGroup.class);
		when(government.getLaws()).thenReturn(java.util.Map.of("democracy", democracy));
		LawHandler handler = mock(LawHandler.class);
		when(handler.getGroupList()).thenReturn(List.of(government));
		when(fx.defender.getLawHandler()).thenReturn(handler);
		Government gov = mock(Government.class);
		when(fx.defender.getGovernment()).thenReturn(gov);

		WarOutcomeService.apply(fx.war, WarEndReason.WHITE_PEACE);

		verify(fx.defender, never()).applyLaw(any(), any());
		verify(gov, never()).addStabilityModifier(any());
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_deJure_transfersDefenderRealmLand() {
		DeJureLandFixture land = deJureLandFixture();
		Title title = mock(Title.class);
		when(title.getId()).thenReturn("county_x");
		try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(20));
			WarOutcomeService.apply(land.fx.war, WarEndReason.ATTACKER_VICTORY);
		}
		assertEquals(List.of(10, 20), land.attackerProvinces);
		assertTrue(land.defenderProvinces.isEmpty());
		verify(land.fx.defender).removeProvince(20, false);
		verify(land.fx.attacker).addProvince(20);
		verify(land.fx.attacker, never()).addTitle(any());
		verify(land.defenderHandler).revalidateClaims();
	}

	@Test
	void attackerVictory_deJure_transfersVassalLandInTitle() {
		DeJureLandFixture land = deJureLandFixture();
		land.defenderProvinces.clear();
		land.defenderProvinces.add(30);
		List<Integer> vassalProvinces = new ArrayList<>(List.of(20));
		Faction vassal = mock(Faction.class);
		when(vassal.getId()).thenReturn("vassal");
		when(vassal.getProvinces()).thenReturn(vassalProvinces);
		when(vassal.getCapital()).thenReturn(99);
		doAnswer(invocation -> {
			vassalProvinces.remove(Integer.valueOf((int) invocation.getArgument(0)));
			return null;
		}).when(vassal).removeProvince(anyInt(), anyBoolean());
		ProvinceHandler vassalHandler = mock(ProvinceHandler.class);
		when(vassal.getProvinceHandler()).thenReturn(vassalHandler);
		FactionManager.factions.add(vassal);
		Title title = mock(Title.class);
		when(title.getId()).thenReturn("county_x");
		try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			titles.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(20));
			relations.when(() -> RelationManager.isOnOverlordPath(vassal, land.fx.defender)).thenReturn(true);
			WarOutcomeService.apply(land.fx.war, WarEndReason.ATTACKER_VICTORY);
		}
		assertEquals(List.of(10, 20), land.attackerProvinces);
		assertEquals(List.of(30), land.defenderProvinces);
		assertTrue(vassalProvinces.isEmpty());
		verify(vassal).removeProvince(20, false);
		verify(land.fx.attacker, never()).addTitle(any());
		verify(vassalHandler).revalidateClaims();
	}

	@Test
	void attackerVictory_deJure_doesNotGrantUnownedTitle() {
		DeJureLandFixture land = deJureLandFixture();
		Title title = mock(Title.class);
		when(title.getId()).thenReturn("county_x");
		try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			titleManager.when(() -> TitleManager.getOwner(title)).thenReturn(null);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(20));
			WarOutcomeService.apply(land.fx.war, WarEndReason.ATTACKER_VICTORY);
		}
		assertEquals(List.of(10, 20), land.attackerProvinces);
		assertTrue(land.defenderProvinces.isEmpty());
		verify(land.fx.attacker, never()).addTitle(any());
	}

	@Test
	void whitePeace_deJure_doesNotMoveLand() {
		DeJureLandFixture land = deJureLandFixture();
		Title title = mock(Title.class);
		when(title.getId()).thenReturn("county_x");
		try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(20));
			WarOutcomeService.apply(land.fx.war, WarEndReason.WHITE_PEACE);
		}
		assertEquals(List.of(10), land.attackerProvinces);
		assertEquals(List.of(20), land.defenderProvinces);
		verify(land.fx.attacker, never()).addProvince(anyInt());
		verify(land.fx.defender, never()).removeProvince(anyInt(), anyBoolean());
		verify(land.fx.attacker, never()).addTitle(any());
	}

	@Test
	void whitePeaceAndAdminEnd_doNotAddObligation() {
		Fixture fx = fixture();
		WarOutcomeService.apply(fx.war, WarEndReason.WHITE_PEACE);
		assertTrue(fx.payerObligations.isEmpty());
		WarOutcomeService.apply(fx.war, WarEndReason.ADMIN_END);
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_pillage_appliesLootAndHit() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.PILLAGE);
		try (MockedStatic<PillageApplyService> pillage = mockStatic(PillageApplyService.class);
				MockedStatic<WarReparationsService> reparations = mockStatic(WarReparationsService.class)) {
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			pillage.verify(() -> PillageApplyService.apply(fx.war));
			reparations.verify(() -> WarReparationsService.applyFromWar(fx.war), never());
		}
	}

	@Test
	void whitePeaceAndAdminEnd_pillage_doNotApply() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.PILLAGE);
		try (MockedStatic<PillageApplyService> pillage = mockStatic(PillageApplyService.class)) {
			WarOutcomeService.apply(fx.war, WarEndReason.WHITE_PEACE);
			WarOutcomeService.apply(fx.war, WarEndReason.ADMIN_END);
			pillage.verify(() -> PillageApplyService.apply(fx.war), never());
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void defenderVictory_pillage_reparationsOnly() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.PILLAGE);
		try (MockedStatic<PillageApplyService> pillage = mockStatic(PillageApplyService.class)) {
			WarOutcomeService.apply(fx.war, WarEndReason.DEFENDER_VICTORY);
			pillage.verify(() -> PillageApplyService.apply(fx.war), never());
		}
		assertEquals(1, fx.payerObligations.size());
	}

	@Test
	void attackerVictory_movementOrigin_appliesGateWithWarSource() {
		Fixture fx = fixture();
		Movement movement = mock(Movement.class);
		fx.war.setMovementId("mov-1");
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<MovementOutcomeService> gate = mockStatic(MovementOutcomeService.class)) {
			factions.when(() -> FactionManager.getMovementById("mov-1")).thenReturn(movement);
			for (WarGoalType goal : WarGoalType.values()) {
				if (!goal.isMovementOrigin()) {
					continue;
				}
				fx.war.setGoal(goal);
				WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			}
			int movementOriginCount = 0;
			for (WarGoalType goal : WarGoalType.values()) {
				if (goal.isMovementOrigin()) {
					movementOriginCount++;
				}
			}
			gate.verify(
					() -> MovementOutcomeService.apply(movement, MovementOutcomeSource.WAR),
					times(movementOriginCount));
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_movementOrigin_missingMovement_doesNotApply() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.OVERTHROW);
		fx.war.setMovementId("missing");
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<MovementOutcomeService> gate = mockStatic(MovementOutcomeService.class)) {
			factions.when(() -> FactionManager.getMovementById("missing")).thenReturn(null);
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			gate.verify(() -> MovementOutcomeService.apply(any(), any()), never());
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void attackerVictory_movementOrigin_blankId_doesNotLookup() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.CHANGE_LAW);
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<MovementOutcomeService> gate = mockStatic(MovementOutcomeService.class)) {
			WarOutcomeService.apply(fx.war, WarEndReason.ATTACKER_VICTORY);
			factions.verify(() -> FactionManager.getMovementById(any()), never());
			gate.verify(() -> MovementOutcomeService.apply(any(), any()), never());
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void whitePeaceAndAdminEnd_movementOrigin_doNotApply() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.CHANGE_TAX);
		fx.war.setMovementId("mov-1");
		Movement movement = mock(Movement.class);
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<MovementOutcomeService> gate = mockStatic(MovementOutcomeService.class)) {
			factions.when(() -> FactionManager.getMovementById("mov-1")).thenReturn(movement);
			WarOutcomeService.apply(fx.war, WarEndReason.WHITE_PEACE);
			WarOutcomeService.apply(fx.war, WarEndReason.ADMIN_END);
			gate.verify(() -> MovementOutcomeService.apply(any(), any()), never());
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void civilWar_defenderVictory_skipsReparationsAndEndsMovementEmpty() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.OVERTHROW);
		fx.war.setMovementId("mov-1");
		Movement movement = mock(Movement.class);
		Government government = mock(Government.class);
		when(movement.getFaction()).thenReturn(fx.defender);
		when(fx.defender.getGovernment()).thenReturn(government);
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<MovementOutcomeService> gate = mockStatic(MovementOutcomeService.class)) {
			factions.when(() -> FactionManager.getMovementById("mov-1")).thenReturn(movement);
			WarOutcomeService.apply(fx.war, WarEndReason.DEFENDER_VICTORY);
			gate.verify(() -> MovementOutcomeService.apply(any(), any()), never());
			verify(government).endMovement(movement);
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void civilWar_whitePeace_endsMovementEmpty() {
		Fixture fx = fixture();
		fx.war.setGoal(WarGoalType.CHANGE_LAW);
		fx.war.setMovementId("mov-1");
		Movement movement = mock(Movement.class);
		Government government = mock(Government.class);
		when(movement.getFaction()).thenReturn(fx.defender);
		when(fx.defender.getGovernment()).thenReturn(government);
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<MovementOutcomeService> gate = mockStatic(MovementOutcomeService.class)) {
			factions.when(() -> FactionManager.getMovementById("mov-1")).thenReturn(movement);
			WarOutcomeService.apply(fx.war, WarEndReason.WHITE_PEACE);
			gate.verify(() -> MovementOutcomeService.apply(any(), any()), never());
			verify(government).endMovement(movement);
		}
		assertTrue(fx.payerObligations.isEmpty());
	}

	@Test
	void defenderVictory_addsObligationOnAttacker() {
		Fixture fx = fixture();
		WarOutcomeService.apply(fx.war, WarEndReason.DEFENDER_VICTORY);
		assertEquals(1, fx.payerObligations.size());
		WarReparationsObligation obligation = fx.payerObligations.get(0);
		assertEquals("def", obligation.getPayeeFactionId());
		assertEquals(25, obligation.getIncomePercent());
		assertEquals(10, obligation.getDaysRemaining());
	}

	@Test
	void tickAfterDailySettlement_decrementsThenRemoves() {
		Faction payer = mock(Faction.class);
		List<WarReparationsObligation> obligations = new ArrayList<>();
		obligations.add(new WarReparationsObligation("def", 25, 2));
		when(payer.getWarReparationsObligations()).thenReturn(obligations);

		WarReparationsService.tickAfterDailySettlement(payer);
		assertEquals(1, obligations.size());
		assertEquals(1, obligations.get(0).getDaysRemaining());

		WarReparationsService.tickAfterDailySettlement(payer);
		assertTrue(obligations.isEmpty());
	}

	private static Fixture fixture() {
		Fixture fx = new Fixture();
		fx.attacker = mock(Faction.class);
		fx.defender = mock(Faction.class);
		when(fx.attacker.getId()).thenReturn("atk");
		when(fx.defender.getId()).thenReturn("def");
		fx.payerObligations = new ArrayList<>();
		when(fx.attacker.getWarReparationsObligations()).thenReturn(fx.payerObligations);
		doAnswer(invocation -> {
			fx.payerObligations.add(invocation.getArgument(0));
			return null;
		}).when(fx.attacker).addWarReparationsObligation(any());
		fx.war = new War(1, fx.attacker, fx.defender);
		fx.war.setGoal(WarGoalType.SUBJUGATE);
		return fx;
	}

	private static DeJureLandFixture deJureLandFixture() {
		DeJureLandFixture land = new DeJureLandFixture();
		land.fx = fixture();
		land.fx.war.setGoal(WarGoalType.DE_JURE_ANNEX);
		land.fx.war.setTargetTitleId("county_x");
		land.attackerProvinces = new ArrayList<>(List.of(10));
		land.defenderProvinces = new ArrayList<>(List.of(20));
		when(land.fx.attacker.getProvinces()).thenReturn(land.attackerProvinces);
		when(land.fx.defender.getProvinces()).thenReturn(land.defenderProvinces);
		when(land.fx.attacker.getCapital()).thenReturn(10);
		when(land.fx.defender.getCapital()).thenReturn(99);
		doAnswer(invocation -> {
			land.attackerProvinces.add((int) invocation.getArgument(0));
			return null;
		}).when(land.fx.attacker).addProvince(anyInt());
		doAnswer(invocation -> {
			land.defenderProvinces.remove(Integer.valueOf((int) invocation.getArgument(0)));
			return null;
		}).when(land.fx.defender).removeProvince(anyInt(), anyBoolean());
		land.defenderHandler = mock(ProvinceHandler.class);
		when(land.fx.defender.getProvinceHandler()).thenReturn(land.defenderHandler);
		when(land.fx.attacker.getProvinceHandler()).thenReturn(mock(ProvinceHandler.class));
		FactionManager.factions.add(land.fx.attacker);
		FactionManager.factions.add(land.fx.defender);
		return land;
	}

	private static final class Fixture {
		Faction attacker;
		Faction defender;
		War war;
		List<WarReparationsObligation> payerObligations;
	}

	private static final class DeJureLandFixture {
		Fixture fx;
		List<Integer> attackerProvinces;
		List<Integer> defenderProvinces;
		ProvinceHandler defenderHandler;
	}
}
