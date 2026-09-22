package me.Plugins.SimpleFactions.War.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.bukkit.Bukkit;

import com.google.gson.Gson;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.War.commitment.WarCommitmentService;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarCommitment;
import me.Plugins.SimpleFactions.enums.FactionModifiers;

class WarCommitmentTest {
	private static final Gson GSON = new Gson();
	private List<War> savedWars;

	@BeforeEach
	void setUp() {
		savedWars = new ArrayList<>(WarManager.get());
		WarManager.get().clear();
	}

	@AfterEach
	void tearDown() {
		for (War war : new ArrayList<>(WarManager.get())) {
			WarCommitmentService.clearCommitments(war.getId());
		}
		WarManager.get().clear();
		WarManager.get().addAll(savedWars);
	}

	@Test
	void commitment_preservesWarIdAndFields() {
		Instant committedAt = Instant.parse("2026-08-19T12:00:00Z");
		WarCommitment commitment = new WarCommitment(3, "faction_a", "subject_a", "levy", 12, committedAt);

		assertEquals(3, commitment.warId());
		assertEquals("faction_a", commitment.factionId());
		assertEquals("subject_a", commitment.sourceFactionId());
		assertEquals("levy", commitment.regimentId());
		assertEquals(12, commitment.count());
		assertEquals(committedAt, commitment.committedAt());
		assertTrue(commitment.isLevyRow());
	}

	@Test
	void commitment_jsonShape_preservesWarId() {
		String json =
				"{\"warId\":3,\"factionId\":\"faction_a\",\"sourceFactionId\":\"subject_a\",\"regimentId\":\"levy\",\"count\":12,\"committedAt\":\"2026-08-19T12:00:00Z\"}";
		CommitmentJson restored = GSON.fromJson(json, CommitmentJson.class);

		assertNotNull(restored);
		assertEquals(3, restored.warId);
		assertEquals("faction_a", restored.factionId);
		assertEquals("subject_a", restored.sourceFactionId);
		assertEquals("levy", restored.regimentId);
		assertEquals(12, restored.count);
		assertEquals("2026-08-19T12:00:00Z", restored.committedAt);
	}

	private static final class CommitmentJson {
		int warId;
		String factionId;
		String sourceFactionId;
		String regimentId;
		int count;
		String committedAt;
	}

	@Test
	void getWarId_matchesGetId() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("attacker");
		when(defender.getId()).thenReturn("defender");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");

		War war = new War(7, attacker, defender);
		assertEquals(war.getId(), war.getWarId());
	}

	@Test
	void commitFaction_capturesRegimentSlots() {
		War war = baseWar(5);
		Faction faction = fighter("attacker", Map.of("militia", 3, "professional", 7));

		List<WarCommitment> commitments = WarCommitmentService.commitFaction(war, faction);

		assertEquals(2, commitments.size());
		assertTrue(commitments.stream().noneMatch(c -> "levy".equals(c.regimentId())));
		assertTrue(commitments.stream().anyMatch(c -> "militia".equals(c.regimentId()) && c.count() == 3));
		assertTrue(commitments.stream().anyMatch(c -> "professional".equals(c.regimentId()) && c.count() == 7));
		for (WarCommitment commitment : commitments) {
			assertNull(commitment.sourceFactionId());
		}
	}

	@Test
	void commitFaction_idempotent() {
		War war = baseWar(9);
		Faction faction = fighter("attacker", Map.of("infantry", 5));

		List<WarCommitment> first = WarCommitmentService.commitFaction(war, faction);
		List<WarCommitment> second = WarCommitmentService.commitFaction(war, faction);

		assertEquals(first, second);
		assertEquals(1, WarManager.getCommitmentsForWar(9).stream().filter(c -> !c.isLevyRow()).count());
	}

	@Test
	void nestedVassal_holderIsNearestFighter() {
		Faction m = fighter("m", Map.of("professional", 1));
		Faction v = fighter("v", Map.of("professional", 1));
		Faction v2 = levySource("v2", 8);
		Faction v3 = levySource("v3", 4);
		Faction d = fighter("d", Map.of("professional", 1));

		War war = baseWar(20);
		war.getAttackers().getMainParticipants().get(0).getSubjects().add(v);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			stubLevyGraph(relations, factions, m, v, v2, v3);

			WarCommitmentService.snapshotLevyForSide(war, war.getAttackers());

			assertLevyRow(war.getId(), "v", "v2", 4);
			assertLevyRow(war.getId(), "v", "v3", 1);
			assertNoLevyRow(war.getId(), "m", "v2");
			assertNoLevyRow(war.getId(), "m", "v3");
		}
	}

	@Test
	void commitAllParticipants_includesSubjects() {
		Faction m = fighter("m", Map.of("professional", 2));
		Faction v = fighter("v", Map.of("militia", 1));
		Faction v2 = levySource("v2", 6);
		Faction d = fighter("d", Map.of("professional", 3));

		War war = baseWar(21);
		war.getAttackers().getMainParticipants().get(0).getSubjects().add(v);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			stubLevyGraph(relations, factions, m, v, v2, null);

			WarCommitmentService.commitAllParticipants(war);

			assertTrue(WarManager.getCommitmentsForWar(21).stream()
					.anyMatch(c -> "v".equals(c.factionId()) && "militia".equals(c.regimentId())));
			assertLevyRow(21, "v", "v2", 3);
			assertTrue(WarManager.getCommitmentsForWar(21).stream()
					.noneMatch(c -> c.isLevyRow() && "v2".equals(c.factionId())));
		}
	}

	@Test
	void allyJoin_addsLevySnapshot() {
		Faction m = fighter("m", Map.of("professional", 1));
		Faction ally = fighter("ally", Map.of("professional", 1));
		Faction s1 = levySource("s1", 6);
		Faction d = fighter("d", Map.of("professional", 1));

		War war = baseWar(22);
		war.getAttackers().getMainParticipants().get(0).getAllies().put(ally, false);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			stubLevyGraph(relations, factions, m, ally, s1, null);
			relations.when(() -> RelationManager.getSubjects(ally)).thenReturn(List.of(s1));
			relations.when(() -> RelationManager.getSubjects(s1)).thenReturn(List.of());
			relations.when(() -> RelationManager.getOverlord(s1)).thenReturn("ally");
			relations.when(() -> RelationManager.sameRealm(s1, ally)).thenReturn(true);
			factions.when(() -> FactionManager.getByString("ally")).thenReturn(ally);

			WarCommitmentService.commitAllParticipants(war);
			int levyRowsBefore = countLevyRows(22);

			war.getAttackers().getMainParticipants().get(0).getAllies().put(ally, true);
			WarCommitmentService.commitFaction(war, ally);
			WarCommitmentService.snapshotLevyForFighter(war, ally);

			assertLevyRow(22, "ally", "s1", 3);
			assertEquals(levyRowsBefore + 1, countLevyRows(22));
		}
	}

	@Test
	void bottomVassalBreak_removesSubtree() {
		Faction m = fighter("m", Map.of("professional", 1));
		Faction v = fighter("v", Map.of("professional", 1));
		Faction v2 = levySource("v2", 8);
		Faction v3 = levySource("v3", 4);
		Faction d = fighter("d", Map.of("professional", 1));

		War war = baseWar(23);
		WarManager.get().add(war);
		war.getAttackers().getMainParticipants().get(0).getSubjects().add(v);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			stubLevyGraph(relations, factions, m, v, v2, v3);
			WarCommitmentService.snapshotLevyForSide(war, war.getAttackers());

			relations.when(() -> RelationManager.getSubjects(v2)).thenReturn(List.of(v3));
			relations.when(() -> RelationManager.getSubjects(v3)).thenReturn(List.of());

			WarCommitmentService.removeLevySubtree(v2);

			assertNoLevyRow(23, "v", "v2");
			assertNoLevyRow(23, "v", "v3");
		}
	}

	@Test
	void newVassal_noAdd() {
		Faction m = fighter("m", Map.of("professional", 1));
		Faction d = fighter("d", Map.of("professional", 1));
		War war = baseWar(24);

		WarCommitmentService.commitAllParticipants(war);
		int before = WarManager.getCommitmentsForWar(24).size();

		Faction s3 = levySource("s3", 10);
		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			relations.when(() -> RelationManager.getSubjects(m)).thenReturn(List.of(s3));
			relations.when(() -> RelationManager.getSubjects(s3)).thenReturn(List.of());
			relations.when(() -> RelationManager.getOverlord(s3)).thenReturn("m");
			relations.when(() -> RelationManager.sameRealm(s3, m)).thenReturn(true);
			factions.when(() -> FactionManager.getByString("m")).thenReturn(m);

			WarCommitmentService.snapshotLevyForSide(war, war.getAttackers());
			assertEquals(before, WarManager.getCommitmentsForWar(24).size());
		}
	}

	@Test
	void transferSubject_removeOnly() {
		Faction m = fighter("m", Map.of("professional", 1));
		Faction v = fighter("v", Map.of("professional", 1));
		Faction v2 = levySource("v2", 8);
		Faction receiver = fighter("receiver", Map.of("professional", 1));
		Faction d = fighter("d", Map.of("professional", 1));

		War war = baseWar(25);
		WarManager.get().add(war);
		war.getAttackers().getMainParticipants().get(0).getSubjects().add(v);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			stubLevyGraph(relations, factions, m, v, v2, null);
			WarCommitmentService.snapshotLevyForSide(war, war.getAttackers());
			assertLevyRow(25, "v", "v2", 4);

			relations.when(() -> RelationManager.getSubjects(v2)).thenReturn(List.of());
			WarCommitmentService.removeLevySubtree(v2);

			assertNoLevyRow(25, "v", "v2");
			WarCommitmentService.snapshotLevyForSide(war, war.getAttackers());
			assertNoLevyRow(25, "receiver", "v2");
		}
	}

	@Test
	void endWar_clearsCommitments() {
		War war = baseWar(11);
		WarManager.get().add(war);
		Faction faction = fighter("attacker", Map.of("infantry", 5));

		WarCommitmentService.commitFaction(war, faction);
		assertEquals(1, WarManager.getCommitmentsForWar(11).size());

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
			WarManager.endWar(war);
		}
		assertTrue(WarManager.getCommitmentsForWar(11).isEmpty());
	}

	private static War baseWar(int id) {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("attacker");
		when(defender.getId()).thenReturn("defender");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		when(attacker.getMembers()).thenReturn(List.of("A"));
		when(defender.getMembers()).thenReturn(List.of("B"));
		stubOwnMilitary(attacker, Map.of("professional", 0));
		stubOwnMilitary(defender, Map.of("professional", 0));
		return new War(id, attacker, defender);
	}

	private static Faction fighter(String id, Map<String, Integer> slots) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getMembers()).thenReturn(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"));
		stubLevyModifier(faction);
		stubOwnMilitary(faction, slots);
		return faction;
	}

	private static Faction levySource(String id, int slots) {
		return fighter(id, Map.of("infantry", slots));
	}

	private static void stubOwnMilitary(Faction faction, Map<String, Integer> slots) {
		Military military = mock(Military.class);
		List<Regiment> regiments = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : slots.entrySet()) {
			Regiment regiment = mock(Regiment.class);
			when(regiment.isLevy()).thenReturn(false);
			when(regiment.getId()).thenReturn(entry.getKey());
			when(regiment.getCurrentSlots()).thenReturn(entry.getValue());
			regiments.add(regiment);
		}
		when(military.getRegiments()).thenReturn(regiments);
		when(faction.getMilitary()).thenReturn(military);
	}

	private static void stubLevyModifier(Faction faction) {
		FactionModifier levy = mock(FactionModifier.class);
		when(levy.getAmount()).thenReturn(50.0);
		when(faction.getModifier(FactionModifiers.LEVY)).thenReturn(levy);
	}

	private static void stubLevyGraph(
			MockedStatic<RelationManager> relations,
			MockedStatic<FactionManager> factions,
			Faction m,
			Faction v,
			Faction v2,
			Faction v3) {
		relations.when(() -> RelationManager.getSubjects(m)).thenReturn(v2 == null && v3 == null ? List.of(v) : List.of(v));
		relations.when(() -> RelationManager.getSubjects(v)).thenReturn(v2 == null ? List.of() : List.of(v2));
		if (v2 != null) {
			relations.when(() -> RelationManager.getSubjects(v2)).thenReturn(v3 == null ? List.of() : List.of(v3));
		}
		if (v3 != null) {
			relations.when(() -> RelationManager.getSubjects(v3)).thenReturn(List.of());
		}
		relations.when(() -> RelationManager.getOverlord(v)).thenReturn("m");
		relations.when(() -> RelationManager.getOverlord(m)).thenReturn(null);
		if (v2 != null) {
			relations.when(() -> RelationManager.getOverlord(v2)).thenReturn("v");
			relations.when(() -> RelationManager.sameRealm(v2, v)).thenReturn(true);
			factions.when(() -> FactionManager.getByString("v")).thenReturn(v);
		}
		if (v3 != null) {
			relations.when(() -> RelationManager.getOverlord(v3)).thenReturn("v2");
			relations.when(() -> RelationManager.sameRealm(v3, v)).thenReturn(true);
			factions.when(() -> FactionManager.getByString("v2")).thenReturn(v2);
		}
		factions.when(() -> FactionManager.getByString("m")).thenReturn(m);
	}

	private static void assertLevyRow(int warId, String holderId, String sourceId, int count) {
		assertTrue(WarManager.getCommitmentsForWar(warId).stream()
				.anyMatch(c -> c.isLevyRow()
						&& holderId.equals(c.factionId())
						&& sourceId.equals(c.sourceFactionId())
						&& count == c.count()),
				"Expected levy row " + holderId + " <- " + sourceId + " = " + count);
	}

	private static void assertNoLevyRow(int warId, String holderId, String sourceId) {
		assertTrue(WarManager.getCommitmentsForWar(warId).stream()
				.noneMatch(c -> c.isLevyRow()
						&& holderId.equals(c.factionId())
						&& sourceId.equals(c.sourceFactionId())));
	}

	private static int countLevyRows(int warId) {
		return (int) WarManager.getCommitmentsForWar(warId).stream().filter(WarCommitment::isLevyRow).count();
	}
}
