package me.Plugins.SimpleFactions.War.core;


import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Tier;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class WarDeclareHelperTest {
	private final List<Faction> savedFactions = new ArrayList<>();

	@BeforeEach
	void setUp() {
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
	}

	@Test
	void warTypeForGoal_mapsEachGoal() {
		assertEquals(WarType.DE_JURE, WarDeclareHelper.warTypeForGoal(WarGoalType.DE_JURE_ANNEX));
		assertEquals(WarType.SUBJUGATE, WarDeclareHelper.warTypeForGoal(WarGoalType.SUBJUGATE));
		assertEquals(WarType.TRANSFER_SUBJECT, WarDeclareHelper.warTypeForGoal(WarGoalType.TRANSFER_SUBJECT));
		assertEquals(WarType.WAR, WarDeclareHelper.warTypeForGoal(WarGoalType.WAR));
		assertEquals(WarType.TRIBUTARY, WarDeclareHelper.warTypeForGoal(WarGoalType.TRIBUTARY));
		assertEquals(WarType.USURP, WarDeclareHelper.warTypeForGoal(WarGoalType.USURP));
		assertEquals(WarType.OPEN_MARKET, WarDeclareHelper.warTypeForGoal(WarGoalType.OPEN_MARKET));
		assertEquals(WarType.CHANGE_GOVERNMENT, WarDeclareHelper.warTypeForGoal(WarGoalType.CHANGE_GOVERNMENT));
		assertEquals(WarType.PILLAGE, WarDeclareHelper.warTypeForGoal(WarGoalType.PILLAGE));
		assertEquals(WarType.OVERTHROW, WarDeclareHelper.warTypeForGoal(WarGoalType.OVERTHROW));
		assertEquals(WarType.CHANGE_LAW, WarDeclareHelper.warTypeForGoal(WarGoalType.CHANGE_LAW));
		assertEquals(WarType.CHANGE_TAX, WarDeclareHelper.warTypeForGoal(WarGoalType.CHANGE_TAX));
		assertEquals(WarType.WAR, WarDeclareHelper.warTypeForGoal(WarGoalType.FORCE_PEACE));
	}

	@Test
	void canAnnexByRank_requiresEqualOrHigherTier() {
		assertEquals(true, WarDeclareHelper.canAnnexByRank(3, 2));
		assertEquals(true, WarDeclareHelper.canAnnexByRank(2, 2));
		assertEquals(false, WarDeclareHelper.canAnnexByRank(1, 2));
	}

	@Test
	void defenderSubjects_includesNestedRealmExcludesDefender() {
		Faction defender = mockFaction("defender");
		Faction mid = mockSubject("mid", "defender");
		Faction nested = mockSubject("nested", "mid");
		Faction outsider = mockFaction("outsider");
		FactionManager.factions.add(defender);
		FactionManager.factions.add(mid);
		FactionManager.factions.add(nested);
		FactionManager.factions.add(outsider);

		Set<String> ids = WarDeclareHelper.defenderSubjects(defender).stream()
				.map(Faction::getId)
				.collect(Collectors.toSet());

		assertEquals(Set.of("mid", "nested"), ids);
		assertFalse(ids.contains("defender"));
		assertFalse(ids.contains("outsider"));
		assertTrue(ids.contains("nested"));
	}

	@Test
	void deJureAndSubjects_emptyWhenDefenderLocked() {
		Faction attacker = mockFaction("attacker");
		Faction defender = mockFaction("defender");
		try (org.mockito.MockedStatic<me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock> lock =
				org.mockito.Mockito.mockStatic(me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock.class)) {
			lock.when(() -> me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock.isLocked(defender))
					.thenReturn(true);
			assertTrue(WarDeclareHelper.deJureTitleOptions(attacker, defender).isEmpty());
			assertTrue(WarDeclareHelper.defenderSubjects(defender).isEmpty());
		}
	}

	@Test
	void canDeclareUsurp_allowsIndependentWithTitleAndRank() {
		Faction attacker = mockRankedFaction("attacker", 3);
		Faction defender = mockRankedFaction("defender", 4);
		withTitle(defender);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		assertTrue(WarDeclareHelper.canDeclareUsurp(attacker, defender));
	}

	@Test
	void canDeclareUsurp_rejectsNullFactions() {
		assertFalse(WarDeclareHelper.canDeclareUsurp(null, mockRankedFaction("defender", 4)));
		assertFalse(WarDeclareHelper.canDeclareUsurp(mockRankedFaction("attacker", 3), null));
	}

	@Test
	void canDeclareUsurp_rejectsMissingTitle() {
		Faction attacker = mockRankedFaction("attacker", 3);
		Faction defender = mockRankedFaction("defender", 4);
		assertFalse(WarDeclareHelper.canDeclareUsurp(attacker, defender));
	}

	@Test
	void canDeclareUsurp_rejectsHigherRankAttacker() {
		Faction attacker = mockRankedFaction("attacker", 5);
		Faction defender = mockRankedFaction("defender", 3);
		withTitle(defender);
		assertFalse(WarDeclareHelper.canDeclareUsurp(attacker, defender));
	}

	@Test
	void canDeclareUsurp_allowsDirectOverlord() {
		Faction defender = mockRankedFaction("liege", 4);
		withTitle(defender);
		Faction attacker = mockSubject("vassal", "liege");
		Tier attackerTier = mockTier(3);
		when(attacker.getTier()).thenReturn(attackerTier);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		assertTrue(WarDeclareHelper.canDeclareUsurp(attacker, defender));
	}

	@Test
	void canDeclareUsurp_rejectsInternalPeer() {
		Faction king = mockRankedFaction("king", 5);
		Faction dukeA = mockSubject("dukeA", "king");
		Tier attackerTier = mockTier(3);
		when(dukeA.getTier()).thenReturn(attackerTier);
		Faction dukeB = mockSubject("dukeB", "king");
		Tier defenderTier = mockTier(3);
		when(dukeB.getTier()).thenReturn(defenderTier);
		withTitle(dukeB);
		FactionManager.factions.add(king);
		FactionManager.factions.add(dukeA);
		FactionManager.factions.add(dukeB);

		assertFalse(WarDeclareHelper.canDeclareUsurp(dukeA, dukeB));
	}

	@Test
	void canDeclareUsurp_rejectsSameRealmNonOverlord() {
		Faction attacker = mockRankedFaction("liege", 4);
		Faction defender = mockSubject("vassal", "liege");
		Tier defenderTier = mockTier(3);
		when(defender.getTier()).thenReturn(defenderTier);
		withTitle(defender);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		assertFalse(WarDeclareHelper.canDeclareUsurp(attacker, defender));
	}

	private static Faction mockFaction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getRelations()).thenReturn(new HashMap<>());
		return faction;
	}

	private static Faction mockRankedFaction(String id, int tierLevel) {
		Faction faction = mockFaction(id);
		Tier tier = mockTier(tierLevel);
		when(faction.getTier()).thenReturn(tier);
		return faction;
	}

	private static Tier mockTier(int tierLevel) {
		Tier tier = mock(Tier.class);
		when(tier.getTier()).thenReturn(tierLevel);
		return tier;
	}

	private static void withTitle(Faction faction) {
		Title title = mock(Title.class);
		when(title.getName()).thenReturn("Primary");
		when(faction.getHighestTitle()).thenReturn(title);
	}

	private static Faction mockSubject(String id, String overlordId) {
		Faction faction = mockFaction(id);
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
