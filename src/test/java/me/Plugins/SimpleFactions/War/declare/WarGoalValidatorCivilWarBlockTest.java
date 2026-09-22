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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Tier;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarCopy;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;

class WarGoalValidatorCivilWarBlockTest {
	private final List<Faction> savedFactions = new ArrayList<>();
	private WarGoalValidator validator;

	@BeforeEach
	void setUp() {
		validator = new WarGoalValidator();
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
	}

	@Test
	void deJureAnnex_rejectsLockedDefender() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		when(attacker.getPrestige()).thenReturn(100.0);
		Title title = mock(Title.class);
		when(title.getId()).thenReturn("county_x");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
				MockedStatic<CivilWarBorderLock> lock = mockStatic(CivilWarBorderLock.class)) {
			titleLoader.when(() -> TitleLoader.getById("county_x")).thenReturn(title);
			lock.when(() -> CivilWarBorderLock.isLocked(defender)).thenReturn(true);

			WarDeclareRequest request = new WarDeclareRequest(
					attacker, defender, WarGoalType.DE_JURE_ANNEX, "county_x", null);
			WarValidationResult result = validator.validate(request);
			assertFalse(result.isValid());
			assertEquals(CivilWarCopy.DECLARE_VS_CIVIL_WAR, result.getMessage());
		}
	}

	@Test
	void transferSubject_rejectsLockedDefender() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 4);
		Faction subject = mockSubject("subject", "defender");
		FactionManager.factions.add(defender);
		FactionManager.factions.add(subject);

		try (MockedStatic<CivilWarBorderLock> lock = mockStatic(CivilWarBorderLock.class)) {
			lock.when(() -> CivilWarBorderLock.isLocked(defender)).thenReturn(true);
			lock.when(() -> CivilWarBorderLock.isLocked(subject)).thenReturn(false);
			WarDeclareRequest request = new WarDeclareRequest(
					attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, "subject");
			WarValidationResult result = validator.validate(request);
			assertFalse(result.isValid());
			assertEquals(CivilWarCopy.DECLARE_VS_CIVIL_WAR, result.getMessage());
		}
	}

	@Test
	void war_stillValidAgainstLockedDefender() {
		Faction attacker = mockFaction("attacker", 3);
		Faction defender = mockFaction("defender", 3);
		when(defender.getCapital()).thenReturn(42);
		FactionManager.factions.add(defender);

		try (MockedStatic<CivilWarBorderLock> lock = mockStatic(CivilWarBorderLock.class)) {
			lock.when(() -> CivilWarBorderLock.isLocked(defender)).thenReturn(true);
			assertTrue(validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.WAR)).isValid());
		}
	}

	private static Faction mockFaction(String id, int tierLevel) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(id);
		when(faction.getRelations()).thenReturn(new HashMap<>());
		when(faction.getTitles()).thenReturn(List.of());
		when(faction.getProvinces()).thenReturn(List.of());
		Tier tier = mock(Tier.class);
		when(tier.getTier()).thenReturn(tierLevel);
		when(faction.getTier()).thenReturn(tier);
		when(faction.canHaveVassals()).thenReturn(true);
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
}
