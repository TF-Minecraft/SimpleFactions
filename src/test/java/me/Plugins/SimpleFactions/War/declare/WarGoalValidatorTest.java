package me.Plugins.SimpleFactions.War.declare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Diplomacy.DiplomacyHandler;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.LawHandler;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Tiers.Tier;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.declare.WarGoalValidator.SettlementProbe;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;
import me.Plugins.SimpleFactions.settlement.Settlement;

class WarGoalValidatorTest {
	private final List<Faction> savedFactions = new ArrayList<>();
	private final List<RelationType> savedRelationTypes = new ArrayList<>();
	private WarGoalValidator validator;

	@BeforeEach
	void setUp() {
		validator = new WarGoalValidator();
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();
		savedRelationTypes.addAll(RelationLoader.types);
		RelationLoader.types.clear();
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
		RelationLoader.types.clear();
		RelationLoader.types.addAll(savedRelationTypes);
	}

	@Test
	void canAnnexByRank_countyCannotTargetKingdomTitle() {
		assertFalse(WarGoalValidator.canAnnexByRank(2, 4));
		assertTrue(WarGoalValidator.canAnnexByRank(4, 2));
		assertTrue(WarGoalValidator.canAnnexByRank(4, 4));
	}

	@Test
	void canUsurpByRank_attackerMustNotOutrankDefender() {
		assertTrue(WarGoalValidator.canUsurpByRank(3, 4));
		assertTrue(WarGoalValidator.canUsurpByRank(4, 4));
		assertFalse(WarGoalValidator.canUsurpByRank(5, 3));
	}

	@Test
	void titleProvincesContainSettlement_blockedWhenSettlementInRegion() {
		Set<Integer> titleProvinces = Set.of(10, 11, 12);
		List<SettlementProbe> settlements = List.of(new SettlementProbe(11));
		List<Integer> capitals = List.of();

		assertTrue(WarGoalValidator.titleProvincesContainSettlement(titleProvinces, settlements, capitals));
	}

	@Test
	void titleProvincesContainSettlement_blockedWhenCapitalInRegion() {
		Set<Integer> titleProvinces = Set.of(10, 11, 12);
		List<SettlementProbe> settlements = List.of();
		List<Integer> capitals = List.of(12);

		assertTrue(WarGoalValidator.titleProvincesContainSettlement(titleProvinces, settlements, capitals));
	}

	@Test
	void deJureAnnex_allowedWhenAttackerOwnsTitleAndDefenderHoldsLand() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		when(attacker.getPrestige()).thenReturn(100.0);
		Title title = mockTitle("county_x", 2);
		when(attacker.getTitles()).thenReturn(List.of(title));
		when(defender.getProvinces()).thenReturn(List.of(11));
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleLoader.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			titleManager.when(() -> TitleManager.getOwner(title)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(10, 11));

			WarDeclareRequest request = new WarDeclareRequest(
					attacker, defender, WarGoalType.DE_JURE_ANNEX, "county_x", null);

			assertTrue(validator.validate(request).isValid());
		}
	}

	@Test
	void deJureAnnex_allowedWhenUnownedAndAttackerOwnsProvince() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		when(attacker.getPrestige()).thenReturn(100.0);
		when(attacker.getProvinces()).thenReturn(List.of(10));
		when(defender.getProvinces()).thenReturn(List.of(11));
		Title title = mockTitle("county_x", 2);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleLoader.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			titleManager.when(() -> TitleManager.getOwner(title)).thenReturn(null);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(10, 11));

			WarDeclareRequest request = new WarDeclareRequest(
					attacker, defender, WarGoalType.DE_JURE_ANNEX, "county_x", null);

			assertTrue(validator.validate(request).isValid());
		}
	}

	@Test
	void deJureAnnex_rejectsWhenDefenderOwnsTitle() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		when(defender.getProvinces()).thenReturn(List.of(11));
		Title title = mockTitle("county_x", 2);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleLoader.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			titleManager.when(() -> TitleManager.getOwner(title)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(11));

			WarDeclareRequest request = new WarDeclareRequest(
					attacker, defender, WarGoalType.DE_JURE_ANNEX, "county_x", null);

			WarValidationResult result = validator.validate(request);
			assertFalse(result.isValid());
			assertEquals("§cYou do not own this title.", result.getMessage());
		}
	}

	@Test
	void deJureAnnex_rejectsUnownedWithoutAttackerProvince() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		when(defender.getProvinces()).thenReturn(List.of(11));
		Title title = mockTitle("county_x", 2);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleLoader.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			titleManager.when(() -> TitleManager.getOwner(title)).thenReturn(null);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(11));

			WarDeclareRequest request = new WarDeclareRequest(
					attacker, defender, WarGoalType.DE_JURE_ANNEX, "county_x", null);

			WarValidationResult result = validator.validate(request);
			assertFalse(result.isValid());
			assertEquals("§cYou do not own a province in this unowned title.", result.getMessage());
		}
	}

	@Test
	void deJureAnnex_rejectsWhenSettlementPresent_suggestsSubjugate() {
		addPickableVassalType("subject");
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		when(attacker.getPrestige()).thenReturn(100.0);
		Title title = mockTitle("county_x", 2);
		when(attacker.getTitles()).thenReturn(List.of(title));
		when(defender.getProvinces()).thenReturn(List.of(11));
		Faction settler = mockFactionWithSettlement("settler", 11);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		FactionManager.factions.add(settler);

		try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleLoader.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			titleManager.when(() -> TitleManager.getOwner(title)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(11));

			WarDeclareRequest request = new WarDeclareRequest(
					attacker, defender, WarGoalType.DE_JURE_ANNEX, "county_x", null);

			WarValidationResult result = validator.validate(request);
			assertFalse(result.isValid());
			assertEquals("§cThis title has settlements - use subjugate instead.", result.getMessage());
		}
	}

	@Test
	void deJureAnnex_rejectsWhenSettlementPresent_withoutSubjugate() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockSubject("vassal", "liege");
		when(attacker.getPrestige()).thenReturn(100.0);
		Title title = mockTitle("county_x", 2);
		when(attacker.getTitles()).thenReturn(List.of(title));
		when(defender.getProvinces()).thenReturn(List.of(11));
		Faction settler = mockFactionWithSettlement("settler", 11);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		FactionManager.factions.add(settler);

		try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleLoader.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			titleManager.when(() -> TitleManager.getOwner(title)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(11));

			WarDeclareRequest request = new WarDeclareRequest(
					attacker, defender, WarGoalType.DE_JURE_ANNEX, "county_x", null);

			WarValidationResult result = validator.validate(request);
			assertFalse(result.isValid());
			assertEquals("§cThis title has settlements.", result.getMessage());
		}
	}

	@Test
	void deJureAnnex_rejectsWhenPrestigeTooLow() {
		int savedCost = Cache.provinceCost;
		Cache.provinceCost = 50;
		try {
			Faction attacker = mockFaction("attacker", 4);
			Faction defender = mockFaction("defender", 2);
			when(attacker.getPrestige()).thenReturn(0.0);
			Title title = mockTitle("county_x", 2);
			when(attacker.getTitles()).thenReturn(List.of(title));
			when(defender.getProvinces()).thenReturn(List.of(10, 11));
			FactionManager.factions.add(attacker);
			FactionManager.factions.add(defender);

			try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
					MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
				titleLoader.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
				titleManager.when(() -> TitleManager.getOwner(title)).thenReturn(attacker);
				titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(10, 11));

				WarDeclareRequest request = new WarDeclareRequest(
						attacker, defender, WarGoalType.DE_JURE_ANNEX, "county_x", null);

				WarValidationResult result = validator.validate(request);
				assertFalse(result.isValid());
				assertEquals("§cYou do not have enough prestige for the incoming provinces.", result.getMessage());
			}
		} finally {
			Cache.provinceCost = savedCost;
		}
	}

	@Test
	void deJureAnnex_rejectsWhenTitleAboveRank() {
		Faction attacker = mockFaction("attacker", 2);
		Faction defender = mockFaction("defender", 4);
		when(defender.getProvinces()).thenReturn(List.of(11));
		Title title = mockTitle("county_x", 4);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleLoader.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(11));

			WarDeclareRequest request = new WarDeclareRequest(
					attacker, defender, WarGoalType.DE_JURE_ANNEX, "county_x", null);

			WarValidationResult result = validator.validate(request);
			assertFalse(result.isValid());
			assertEquals("§cYou cannot de jure annex a title above your rank.", result.getMessage());
		}
	}

	@Test
	void validateShared_rejectsSameRealm() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockSubject("defender", "attacker");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.SUBJUGATE));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot declare war on a faction in the same realm.", result.getMessage());
	}

	@Test
	void validateShared_rejectsAlly() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFaction("defender", 3);
		linkOutgoingRelation(attacker, defender, "ally");
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.SUBJUGATE));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot declare war on an ally.", result.getMessage());
	}

	@Test
	void validateShared_rejectsTributaryUnlessSubjugate() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		linkOutgoingRelation(attacker, defender, "tributary");
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(new WarDeclareRequest(
				attacker, defender, WarGoalType.DE_JURE_ANNEX, "county_x", null));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot declare war on your tributary.", result.getMessage());
	}

	@Test
	void validateShared_allowsTributaryForSubjugate() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		linkOutgoingRelation(attacker, defender, "tributary");
		FactionManager.factions.add(defender);

		addPickableVassalType("subject");
		assertTrue(validator.validate(subjugateRequest(attacker, defender, "subject")).isValid());
	}

	@Test
	void validateShared_allowsTributaryForWar() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		linkOutgoingRelation(attacker, defender, "tributary");
		FactionManager.factions.add(defender);

		assertTrue(validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.WAR)).isValid());
	}

	@Test
	void validateShared_rejectsWarWhenNapOverlay() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		linkNap(attacker, defender);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.WAR));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot declare war while a non-aggression pact is in effect.", result.getMessage());
	}

	@Test
	void validateShared_rejectsSubjugateWhenNapOverlay() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		linkOutgoingRelation(attacker, defender, "tributary");
		linkNap(attacker, defender);
		FactionManager.factions.add(defender);

		addPickableVassalType("subject");
		WarValidationResult result = validator.validate(subjugateRequest(attacker, defender, "subject"));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot declare war while a non-aggression pact is in effect.", result.getMessage());
	}

	@Test
	void validateShared_allowsTributarySubjugateWithoutNap() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		linkOutgoingRelation(attacker, defender, "tributary");
		FactionManager.factions.add(defender);

		addPickableVassalType("subject");
		assertTrue(validator.validate(subjugateRequest(attacker, defender, "subject")).isValid());
	}

	@Test
	void movementOriginGoals_cannotBeDeclared() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		FactionManager.factions.add(defender);

		for (WarGoalType goal : WarGoalType.values()) {
			if (!goal.isMovementOrigin()) {
				continue;
			}
			WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, goal));
			assertFalse(result.isValid(), goal.name());
			assertEquals("§cThis war goal cannot be declared yet.", result.getMessage());
		}
	}

	@Test
	void war_validWhenIndependent() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		FactionManager.factions.add(defender);

		assertTrue(validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.WAR)).isValid());
	}

	@Test
	void war_rejectsSameRealm() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockSubject("defender", "attacker");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.WAR));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot declare war on a faction in the same realm.", result.getMessage());
	}

	@Test
	void war_allowsInternalPeerDukes() {
		Faction king = mockFaction("king", 5);
		Faction dukeA = mockSubject("dukeA", "king");
		when(dukeA.getTier().getTier()).thenReturn(3);
		Faction dukeB = mockSubject("dukeB", "king");
		when(dukeB.getTier().getTier()).thenReturn(3);
		FactionManager.factions.add(king);
		FactionManager.factions.add(dukeA);
		FactionManager.factions.add(dukeB);

		assertTrue(validator.validate(WarDeclareRequest.of(dukeA, dukeB, WarGoalType.WAR)).isValid());
	}

	@Test
	void war_rejectsDukeVersusKing() {
		Faction king = mockFaction("king", 5);
		Faction duke = mockSubject("duke", "king");
		when(duke.getTier().getTier()).thenReturn(3);
		FactionManager.factions.add(king);
		FactionManager.factions.add(duke);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(duke, king, WarGoalType.WAR));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot declare war on a faction in the same realm.", result.getMessage());
	}

	@Test
	void usurp_rejectsInternalPeer() {
		Faction king = mockFaction("king", 5);
		Faction dukeA = mockSubject("dukeA", "king");
		when(dukeA.getTier().getTier()).thenReturn(3);
		Faction dukeB = mockSubject("dukeB", "king");
		when(dukeB.getTier().getTier()).thenReturn(3);
		withTitle(dukeB);
		FactionManager.factions.add(king);
		FactionManager.factions.add(dukeA);
		FactionManager.factions.add(dukeB);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(dukeA, dukeB, WarGoalType.USURP));
		assertFalse(result.isValid());
		assertEquals("§cUsurp can only target your direct overlord.", result.getMessage());
	}

	@Test
	void subjugate_allowsInternalPeer() {
		addPickableVassalType("subject");
		Faction king = mockFaction("king", 5);
		Faction dukeA = mockSubject("dukeA", "king");
		when(dukeA.getTier().getTier()).thenReturn(3);
		Faction dukeB = mockSubject("dukeB", "king");
		when(dukeB.getTier().getTier()).thenReturn(3);
		FactionManager.factions.add(king);
		FactionManager.factions.add(dukeA);
		FactionManager.factions.add(dukeB);

		assertTrue(validator.validate(subjugateRequest(dukeA, dukeB, "subject")).isValid());
	}

	@Test
	void tributary_validWhenIndependentAndTypeLoaded() {
		addTributaryType();
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		FactionManager.factions.add(defender);

		assertTrue(validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.TRIBUTARY)).isValid());
	}

	@Test
	void tributary_rejectsOwnTributary() {
		addTributaryType();
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		linkOutgoingRelation(attacker, defender, "tributary");
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.TRIBUTARY));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot declare war on your tributary.", result.getMessage());
	}

	@Test
	void tributary_rejectsSameRealm() {
		addTributaryType();
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockSubject("defender", "attacker");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.TRIBUTARY));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot declare war on a faction in the same realm.", result.getMessage());
	}

	@Test
	void tributary_rejectsDefenderWithOverlord() {
		addTributaryType();
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockSubject("defender", "other_liege");
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(
				WarDeclareRequest.of(attacker, defender, WarGoalType.TRIBUTARY));
		assertFalse(result.isValid());
		assertEquals("§cYou can only make independent factions tributary.", result.getMessage());
	}

	@Test
	void tributary_failsWhenTypeMissing() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.TRIBUTARY));
		assertFalse(result.isValid());
		assertEquals("§cTributary diplomacy is not configured.", result.getMessage());
	}

	@Test
	void subjugate_allowedWhenSettlementPresent() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		FactionManager.factions.add(defender);

		addPickableVassalType("subject");
		assertTrue(validator.validate(subjugateRequest(attacker, defender, "subject")).isValid());
	}

	@Test
	void subjugate_failsWhenTypeMissing() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.SUBJUGATE));
		assertFalse(result.isValid());
		assertEquals("§cSpecify a subject type.", result.getMessage());
	}

	@Test
	void subjugate_rejectsIntegratedType() {
		addVassalType("integrated_subject", false);
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(subjugateRequest(attacker, defender, "integrated_subject"));
		assertFalse(result.isValid());
		assertEquals("§cThat subject type cannot be chosen for war.", result.getMessage());
	}

	@Test
	void subjugate_rejectsNonVassalType() {
		RelationType ally = mock(RelationType.class);
		when(ally.getId()).thenReturn("ally");
		when(ally.isVassalage()).thenReturn(false);
		when(ally.canPickForWar()).thenReturn(true);
		RelationLoader.types.add(ally);
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(subjugateRequest(attacker, defender, "ally"));
		assertFalse(result.isValid());
		assertEquals("§cThat subject type cannot be chosen for war.", result.getMessage());
	}

	@Test
	void subjugate_rejectsOtherOverlord() {
		addPickableVassalType("subject");
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockSubject("defender", "other_liege");
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(subjugateRequest(attacker, defender, "subject"));
		assertFalse(result.isValid());
		assertEquals("§cThat faction is already a subject of someone else.", result.getMessage());
	}

	@Test
	void subjugate_rejectsAtLimit() {
		RelationType march = addPickableVassalType("march");
		when(march.hasLimit()).thenReturn(true);
		when(march.getLimit()).thenReturn(1);
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		Relation existing = mock(Relation.class);
		when(existing.getType()).thenReturn(march);
		HashMap<String, Relation> attackerRelations = new HashMap<>();
		attackerRelations.put("other", existing);
		when(attacker.getRelations()).thenReturn(attackerRelations);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(subjugateRequest(attacker, defender, "march"));
		assertFalse(result.isValid());
		assertEquals("§cYou have reached the limit for this relation type.", result.getMessage());
	}

	@Test
	void subjugate_rejectsAlreadySubject_sameRealmBlocksFirst() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockSubject("defender", "attacker");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.SUBJUGATE));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot declare war on a faction in the same realm.", result.getMessage());
	}

	@Test
	void subjugate_rejectsWhenAttackerCannotHaveVassals() {
		Faction attacker = mockFaction("attacker", 3);
		when(attacker.canHaveVassals()).thenReturn(false);
		Faction defender = mockFactionWithCapital("defender", 42);
		FactionManager.factions.add(defender);

		addPickableVassalType("subject");
		WarValidationResult result = validator.validate(subjugateRequest(attacker, defender, "subject"));
		assertFalse(result.isValid());
		assertEquals("§cYour faction cannot have vassals!", result.getMessage());
	}

	@Test
	void transferSubject_rejectsWhenAttackerCannotHaveVassals() {
		Faction attacker = mockFaction("attacker", 4);
		when(attacker.canHaveVassals()).thenReturn(false);
		Faction defender = mockFaction("defender", 4);
		Faction subject = mockSubject("subject", "defender");
		FactionManager.factions.add(defender);
		FactionManager.factions.add(subject);

		WarDeclareRequest request = new WarDeclareRequest(
				attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, "subject");

		WarValidationResult result = validator.validate(request);
		assertFalse(result.isValid());
		assertEquals("§cYour faction cannot have vassals!", result.getMessage());
	}

	@Test
	void transferSubject_requiresDefenderAsOverlord() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 4);
		Faction subject = mockSubject("subject", "other_overlord");
		FactionManager.factions.add(subject);

		WarDeclareRequest request = new WarDeclareRequest(
				attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, "subject");

		WarValidationResult result = validator.validate(request);
		assertFalse(result.isValid());
		assertTrue(result.getMessage().contains("not a subject of the defender"));
	}

	@Test
	void transferSubject_allowedWhenDefenderIsOverlord() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 4);
		Faction subject = mockSubject("subject", "defender");
		FactionManager.factions.add(defender);
		FactionManager.factions.add(subject);

		WarDeclareRequest request = new WarDeclareRequest(
				attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, "subject");

		assertTrue(validator.validate(request).isValid());
	}

	@Test
	void transferSubject_allowedWhenNestedUnderDefender() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 4);
		Faction mid = mockSubject("mid", "defender");
		Faction nested = mockSubject("nested", "mid");
		FactionManager.factions.add(defender);
		FactionManager.factions.add(mid);
		FactionManager.factions.add(nested);

		WarDeclareRequest request = new WarDeclareRequest(
				attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, "nested");

		assertTrue(validator.validate(request).isValid());
	}

	@Test
	void transferSubject_rejectsAtLimitForCurrentType() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 4);
		Faction subject = mockSubject("subject", "defender");
		RelationType march = mock(RelationType.class);
		when(march.getId()).thenReturn("march");
		when(march.hasLimit()).thenReturn(true);
		when(march.getLimit()).thenReturn(1);
		Relation defenderToSubject = mock(Relation.class);
		when(defenderToSubject.getType()).thenReturn(march);
		when(defender.getRelation("subject")).thenReturn(defenderToSubject);

		Relation existing = mock(Relation.class);
		when(existing.getType()).thenReturn(march);
		HashMap<String, Relation> attackerRelations = new HashMap<>();
		attackerRelations.put("other", existing);
		when(attacker.getRelations()).thenReturn(attackerRelations);

		FactionManager.factions.add(defender);
		FactionManager.factions.add(subject);

		WarDeclareRequest request = new WarDeclareRequest(
				attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, "subject");

		WarValidationResult result = validator.validate(request);
		assertFalse(result.isValid());
		assertEquals("§cYou have reached the limit for this relation type.", result.getMessage());
	}

	@Test
	void usurp_allowsIndependent() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithTitle("defender", 4);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		assertTrue(validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.USURP)).isValid());
	}

	@Test
	void usurp_allowsDirectOverlord() {
		Faction defender = mockFactionWithTitle("liege", 4);
		Faction attacker = mockSubject("vassal", "liege");
		when(attacker.getTier().getTier()).thenReturn(3);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		assertTrue(validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.USURP)).isValid());
	}

	@Test
	void usurp_rejectsSameRealmNonOverlord() {
		Faction attacker = mockFaction("liege", 4);
		Faction defender = mockSubject("vassal", "liege");
		withTitle(defender);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.USURP));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot declare war on a faction in the same realm.", result.getMessage());
	}

	@Test
	void usurp_rejectsHigherRankAttacker() {
		Faction attacker = mockFaction("attacker", 5);
		Faction defender = mockFactionWithTitle("defender", 3);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.USURP));
		assertFalse(result.isValid());
		assertEquals("§cYou cannot usurp a faction of lower rank.", result.getMessage());
	}

	@Test
	void usurp_rejectsDefenderWithoutTitle() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFaction("defender", 4);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.USURP));
		assertFalse(result.isValid());
		assertEquals("§cThat faction has no title to usurp.", result.getMessage());
	}

	@Test
	void changeGovernment_validWhenOneAxisChanges() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		stubGovernmentLaws(defender, "autocracy", "fixed");
		FactionManager.factions.add(defender);

		assertTrue(validator.validate(changeGovRequest(attacker, defender, "democracy", "fixed")).isValid());
	}

	@Test
	void changeGovernment_validWhenBothAxesChange() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		stubGovernmentLaws(defender, "autocracy", "fixed");
		FactionManager.factions.add(defender);

		assertTrue(validator.validate(changeGovRequest(attacker, defender, "democracy", "elected")).isValid());
	}

	@Test
	void changeGovernment_rejectsSameCombination() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		stubGovernmentLaws(defender, "autocracy", "fixed");
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(changeGovRequest(attacker, defender, "autocracy", "fixed"));
		assertFalse(result.isValid());
		assertEquals("§cThey already have that government.", result.getMessage());
	}

	@Test
	void changeGovernment_rejectsMissingGovernmentGroup() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		LawHandler handler = mock(LawHandler.class);
		when(handler.getGroup("government")).thenReturn(null);
		when(defender.getLawHandler()).thenReturn(handler);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(changeGovRequest(attacker, defender, "democracy", null));
		assertFalse(result.isValid());
		assertEquals("§cChange Government is not configured.", result.getMessage());
	}

	@Test
	void changeGovernment_rejectsInvalidLeadershipLaw() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		stubGovernmentLaws(defender, "autocracy", "fixed");
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(changeGovRequest(attacker, defender, "democracy", "not_a_law"));
		assertFalse(result.isValid());
		assertEquals("§cThat leadership law is not available.", result.getMessage());
	}

	@Test
	void openMarket_validWhenNeitherHasBlockedLaw() {
		withOpenMarketCache();
		try {
			Faction attacker = mockFaction("attacker", 3);
			Faction defender = mockFactionWithCapital("defender", 42);
			stubTradeLaws(attacker, "mercantilism", "free_trade");
			stubTradeLaws(defender, "mercantilism", "free_trade");
			FactionManager.factions.add(defender);

			assertTrue(validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.OPEN_MARKET)).isValid());
		} finally {
			clearOpenMarketCache();
		}
	}

	@Test
	void openMarket_rejectsWhenDefenderAlreadyHasOpenMarketLaw() {
		withOpenMarketCache();
		try {
			Faction attacker = mockFaction("attacker", 3);
			Faction defender = mockFactionWithCapital("defender", 42);
			stubTradeLaws(attacker, "mercantilism", "free_trade");
			stubTradeLaws(defender, "free_trade", "free_trade");
			FactionManager.factions.add(defender);

			WarValidationResult result =
					validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.OPEN_MARKET));
			assertFalse(result.isValid());
			assertEquals("§cThey already have an open market.", result.getMessage());
		} finally {
			clearOpenMarketCache();
		}
	}

	@Test
	void openMarket_rejectsWhenAttackerHasIsolationLaw() {
		withOpenMarketCache();
		try {
			Faction attacker = mockFaction("attacker", 3);
			Faction defender = mockFactionWithCapital("defender", 42);
			stubTradeLaws(attacker, "isolationism", "free_trade");
			stubTradeLaws(defender, "mercantilism", "free_trade");
			FactionManager.factions.add(defender);

			WarValidationResult result =
					validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.OPEN_MARKET));
			assertFalse(result.isValid());
			assertEquals("§cYou cannot force open markets with your current trade law.", result.getMessage());
		} finally {
			clearOpenMarketCache();
		}
	}

	@Test
	void openMarket_rejectsWhenApplyIdBlank() {
		Cache.openMarketApplyDefenderLaw = "";
		Cache.openMarketDefenderMustNotHave = List.of();
		Cache.openMarketAttackerMustNotHave = List.of();
		try {
			Faction attacker = mockFaction("attacker", 3);
			Faction defender = mockFactionWithCapital("defender", 42);
			FactionManager.factions.add(defender);

			WarValidationResult result =
					validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.OPEN_MARKET));
			assertFalse(result.isValid());
			assertEquals("§cOpen Market is not configured.", result.getMessage());
		} finally {
			clearOpenMarketCache();
		}
	}

	@Test
	void pillage_rejectsMissingSettlement() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(pillageRequest(attacker, defender, null));
		assertFalse(result.isValid());
		assertEquals("§cSpecify a settlement to pillage.", result.getMessage());
	}

	@Test
	void pillage_rejectsUnknownSettlement() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 42);
		FactionManager.factions.add(defender);

		WarValidationResult result = validator.validate(pillageRequest(attacker, defender, "missing"));
		assertFalse(result.isValid());
		assertEquals("§cThat settlement does not exist.", result.getMessage());
	}

	@Test
	void pillage_rejectsOutOfRange() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 4);
		when(attacker.getProvinces()).thenReturn(List.of(1));
		when(defender.getProvinces()).thenReturn(List.of(4));
		when(defender.ownsProvince(4)).thenReturn(true);
		Settlement settlement = new Settlement("town", "Town", 4, 0, 0);
		when(defender.getSettlementHandler().getById("town")).thenReturn(settlement);
		when(defender.getSettlementHandler().getAll()).thenReturn(List.of(settlement));
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		ProvinceManager pm = new ProvinceManager();
		Province a = new Province(1, Terrain.PLAINS.name(), 50);
		Province h1 = new Province(2, Terrain.PLAINS.name(), 50);
		Province h2 = new Province(3, Terrain.PLAINS.name(), 50);
		Province town = new Province(4, Terrain.PLAINS.name(), 50);
		a.addNeighbour(2);
		h1.addNeighbour(1);
		h1.addNeighbour(3);
		h2.addNeighbour(2);
		h2.addNeighbour(4);
		town.addNeighbour(3);
		pm.start(Map.of(1, a, 2, h1, 3, h2, 4, town));

		Cache.pillageRangeProvinces = 2;
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class);
				MockedStatic<TitleManager> tm = mockStatic(TitleManager.class)) {
			sf.when(SimpleFactions::getInstance).thenReturn(plugin);
			tm.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(4));

			WarValidationResult result = validator.validate(pillageRequest(attacker, defender, "town"));
			assertFalse(result.isValid());
			assertEquals("§cThat settlement is out of pillage range.", result.getMessage());
		} finally {
			Cache.pillageRangeProvinces = 3;
		}
	}

	@Test
	void pillage_validLandRange() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 2);
		when(attacker.getProvinces()).thenReturn(List.of(1));
		when(defender.getProvinces()).thenReturn(List.of(2));
		when(defender.ownsProvince(2)).thenReturn(true);
		Settlement settlement = new Settlement("town", "Town", 2, 0, 0);
		when(defender.getSettlementHandler().getById("town")).thenReturn(settlement);
		when(defender.getSettlementHandler().getAll()).thenReturn(List.of(settlement));
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		ProvinceManager pm = new ProvinceManager();
		Province a = new Province(1, Terrain.PLAINS.name(), 50);
		Province town = new Province(2, Terrain.PLAINS.name(), 50);
		a.addNeighbour(2);
		town.addNeighbour(1);
		pm.start(Map.of(1, a, 2, town));

		Cache.pillageRangeProvinces = 1;
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class);
				MockedStatic<TitleManager> tm = mockStatic(TitleManager.class)) {
			sf.when(SimpleFactions::getInstance).thenReturn(plugin);
			tm.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(2));

			assertTrue(validator.validate(pillageRequest(attacker, defender, "town")).isValid());
		} finally {
			Cache.pillageRangeProvinces = 3;
		}
	}

	@Test
	void pillage_validSeaRangeWhenLandTooFar() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFactionWithCapital("defender", 4);
		when(attacker.getProvinces()).thenReturn(List.of(1));
		when(defender.getProvinces()).thenReturn(List.of(4));
		when(defender.ownsProvince(4)).thenReturn(true);
		Settlement settlement = new Settlement("port", "Port", 4, 0, 0);
		when(defender.getSettlementHandler().getById("port")).thenReturn(settlement);
		when(defender.getSettlementHandler().getAll()).thenReturn(List.of(settlement));
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		ProvinceManager pm = new ProvinceManager();
		Province a = new Province(1, Terrain.PLAINS.name(), 50);
		Province h1 = new Province(2, Terrain.PLAINS.name(), 50);
		Province h2 = new Province(3, Terrain.PLAINS.name(), 50);
		Province town = new Province(4, Terrain.PLAINS.name(), 50);
		Province sea = new Province(5, Terrain.SEA.name(), 50);
		a.addNeighbour(2);
		a.addNeighbour(5);
		h1.addNeighbour(1);
		h1.addNeighbour(3);
		h2.addNeighbour(2);
		h2.addNeighbour(4);
		town.addNeighbour(3);
		town.addNeighbour(5);
		sea.addNeighbour(1);
		sea.addNeighbour(4);
		pm.start(Map.of(1, a, 2, h1, 3, h2, 4, town, 5, sea));

		Cache.pillageRangeProvinces = 2;
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class);
				MockedStatic<TitleManager> tm = mockStatic(TitleManager.class)) {
			sf.when(SimpleFactions::getInstance).thenReturn(plugin);
			tm.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(4));

			assertTrue(validator.validate(pillageRequest(attacker, defender, "port")).isValid());
		} finally {
			Cache.pillageRangeProvinces = 3;
		}
	}

	private static WarDeclareRequest pillageRequest(Faction attacker, Faction defender, String settlementId) {
		return new WarDeclareRequest(
				attacker, defender, WarGoalType.PILLAGE, null, null, null, null, null, settlementId);
	}

	private static Title mockTitle(String id, int tierLevel) {
		Title title = mock(Title.class);
		Tier titleTier = mock(Tier.class);
		when(title.getId()).thenReturn(id);
		when(title.getName()).thenReturn(id);
		when(title.getTier()).thenReturn(titleTier);
		when(titleTier.getTier()).thenReturn(tierLevel);
		when(titleTier.getName()).thenReturn("County");
		return title;
	}

	private static Faction mockFaction(String id, int tierLevel) {
		Faction faction = mock(Faction.class);
		Tier tier = mock(Tier.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getTier()).thenReturn(tier);
		when(tier.getTier()).thenReturn(tierLevel);
		when(faction.getRelations()).thenReturn(new HashMap<>());
		when(faction.getCapital()).thenReturn(0);
		when(faction.getSettlementHandler()).thenReturn(mock(me.Plugins.SimpleFactions.settlement.handler.SettlementHandler.class));
		when(faction.getSettlementHandler().getAll()).thenReturn(List.of());
		when(faction.getTitles()).thenReturn(List.of());
		when(faction.getProvinces()).thenReturn(List.of());
		when(faction.getPrestige()).thenReturn(0.0);
		when(faction.canHaveVassals()).thenReturn(true);
		return faction;
	}

	private static Faction mockFactionWithTitle(String id, int tierLevel) {
		Faction faction = mockFaction(id, tierLevel);
		withTitle(faction);
		return faction;
	}

	private static void withTitle(Faction faction) {
		Title title = mock(Title.class);
		when(title.getName()).thenReturn("Primary");
		when(faction.getHighestTitle()).thenReturn(title);
	}

	private static Faction mockFactionWithCapital(String id, int capitalProvince) {
		Faction faction = mockFaction(id, 2);
		when(faction.getCapital()).thenReturn(capitalProvince);
		return faction;
	}

	private static Faction mockFactionWithSettlement(String id, int settlementProvince) {
		Faction faction = mockFaction(id, 2);
		me.Plugins.SimpleFactions.settlement.Settlement settlement =
				mock(me.Plugins.SimpleFactions.settlement.Settlement.class);
		when(settlement.getCenterProvince()).thenReturn(settlementProvince);
		when(faction.getSettlementHandler().getAll()).thenReturn(List.of(settlement));
		return faction;
	}

	private static Faction mockSubject(String id, String overlordId) {
		Faction faction = mockFaction(id, 2);
		Relation relation = mock(Relation.class);
		RelationType type = mock(RelationType.class);
		when(type.isOverlord()).thenReturn(true);
		when(type.getId()).thenReturn("subject");
		when(relation.getType()).thenReturn(type);
		HashMap<String, Relation> relations = new HashMap<>();
		relations.put(overlordId, relation);
		when(faction.getRelations()).thenReturn(relations);
		when(faction.getRelation(overlordId)).thenReturn(relation);
		return faction;
	}

	private static void linkOutgoingRelation(Faction origin, Faction target, String typeId) {
		Relation relation = mock(Relation.class);
		RelationType type = mock(RelationType.class);
		when(type.getId()).thenReturn(typeId);
		when(relation.getType()).thenReturn(type);
		HashMap<String, Relation> relations = new HashMap<>();
		relations.put(target.getId(), relation);
		when(origin.getRelations()).thenReturn(relations);
		when(origin.getRelation(target.getId())).thenReturn(relation);
	}

	private static void linkNap(Faction a, Faction b) {
		RelationType nap = mock(RelationType.class);
		when(nap.blocksWar()).thenReturn(true);
		DiplomacyHandler handlerA = mock(DiplomacyHandler.class);
		DiplomacyHandler handlerB = mock(DiplomacyHandler.class);
		when(handlerA.getTreatyRelation(b.getId())).thenReturn(nap);
		when(handlerB.getTreatyRelation(a.getId())).thenReturn(nap);
		when(a.getDiplomacyHandler()).thenReturn(handlerA);
		when(b.getDiplomacyHandler()).thenReturn(handlerB);
	}

	private static void addTributaryType() {
		RelationType type = mock(RelationType.class);
		when(type.getId()).thenReturn("tributary");
		RelationLoader.types.add(type);
	}

	private static RelationType addPickableVassalType(String id) {
		return addVassalType(id, true);
	}

	private static RelationType addVassalType(String id, boolean canPickForWar) {
		RelationType type = mock(RelationType.class);
		when(type.getId()).thenReturn(id);
		when(type.isVassalage()).thenReturn(true);
		when(type.canPickForWar()).thenReturn(canPickForWar);
		RelationLoader.types.add(type);
		return type;
	}

	private static WarDeclareRequest subjugateRequest(Faction attacker, Faction defender, String typeId) {
		return new WarDeclareRequest(attacker, defender, WarGoalType.SUBJUGATE, null, null, typeId);
	}

	private static void withOpenMarketCache() {
		Cache.openMarketApplyDefenderLaw = "free_trade";
		Cache.openMarketDefenderMustNotHave = List.of("free_trade");
		Cache.openMarketAttackerMustNotHave = List.of("isolationism");
	}

	private static void clearOpenMarketCache() {
		Cache.openMarketApplyDefenderLaw = "";
		Cache.openMarketDefenderMustNotHave = List.of();
		Cache.openMarketAttackerMustNotHave = List.of();
	}

	private static void stubTradeLaws(Faction faction, String currentLawId, String applyLawId) {
		Law current = mock(Law.class);
		when(current.getId()).thenReturn(currentLawId);
		Law apply = mock(Law.class);
		when(apply.getId()).thenReturn(applyLawId);
		HashMap<String, Law> laws = new HashMap<>();
		laws.put(currentLawId, current);
		laws.put(applyLawId, apply);
		LawGroup group = mock(LawGroup.class);
		when(group.getLaws()).thenReturn(laws);
		LawHandler handler = mock(LawHandler.class);
		when(handler.getCurrentLaws()).thenReturn(List.of(current));
		when(handler.getGroupList()).thenReturn(List.of(group));
		when(faction.getLawHandler()).thenReturn(handler);
	}

	private static WarDeclareRequest changeGovRequest(
			Faction attacker, Faction defender, String governmentLawId, String leadershipLawId) {
		return new WarDeclareRequest(
				attacker, defender, WarGoalType.CHANGE_GOVERNMENT, null, null, null, governmentLawId, leadershipLawId);
	}

	private static void stubGovernmentLaws(Faction faction, String currentGovId, String currentLeadId) {
		LawGroup government = stubLawGroup("government", currentGovId, "autocracy", "oligarchy", "plutocracy", "democracy");
		LawGroup leadership = stubLawGroup("leadership", currentLeadId, "fixed", "elected");
		LawHandler handler = mock(LawHandler.class);
		when(handler.getGroup("government")).thenReturn(government);
		when(handler.getGroup("leadership")).thenReturn(leadership);
		when(handler.getGroupList()).thenReturn(List.of(government, leadership));
		when(faction.getLawHandler()).thenReturn(handler);
	}

	private static LawGroup stubLawGroup(String groupId, String currentId, String... lawIds) {
		HashMap<String, Law> laws = new HashMap<>();
		for (String lawId : lawIds) {
			Law law = mock(Law.class);
			when(law.getId()).thenReturn(lawId);
			laws.put(lawId, law);
		}
		LawGroup group = mock(LawGroup.class);
		when(group.getId()).thenReturn(groupId);
		when(group.getLaws()).thenReturn(laws);
		when(group.getCurrent()).thenReturn(laws.get(currentId));
		return group;
	}
}
