package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.diplomacy.Attitude;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;

class RelationManagerWartimeBlockTest {

	private final List<War> savedWars = new ArrayList<>();
	private Faction origin;
	private Faction target;

	@BeforeEach
	void setUp() {
		savedWars.addAll(WarManager.get());
		WarManager.get().clear();
		origin = faction("origin");
		target = faction("target");
		relate(origin, target, type("none"));
		relate(target, origin, type("none"));
	}

	@AfterEach
	void tearDown() {
		WarManager.get().clear();
		WarManager.get().addAll(savedWars);
	}

	@Test
	void isAtWar_onlyCountsActiveWars() {
		War ended = war(false, origin);
		WarManager.get().add(ended);
		assertFalse(WarManager.isAtWar(origin));

		WarManager.get().add(war(true, origin));
		assertTrue(WarManager.isAtWar(origin));
		assertFalse(WarManager.isAtWar(target));
		assertFalse(WarManager.isAtWar(null));
	}

	@Test
	void ally_allowedWhenNeitherAtWar() {
		assertNull(RelationManager.wartimeBlock(type("ally"), target, origin));
	}

	@Test
	void ally_blockedWhenProposerAtWar() {
		WarManager.get().add(war(true, origin));
		assertNotNull(RelationManager.wartimeBlock(type("ally"), target, origin));
	}

	@Test
	void ally_blockedWhenTargetAtWar() {
		WarManager.get().add(war(true, target));
		assertNotNull(RelationManager.wartimeBlock(type("ally"), target, origin));
	}

	@Test
	void ally_existingAllianceNotBlocked() {
		relate(origin, target, type("ally"));
		WarManager.get().add(war(true, origin, target));
		assertNull(RelationManager.wartimeBlock(type("ally"), target, origin));
	}

	@Test
	void vassal_blockedWhenNewSubjectAtWar() {
		WarManager.get().add(war(true, target));
		assertNotNull(RelationManager.wartimeBlock(vassalType("subject"), target, origin));
	}

	@Test
	void vassal_allowedWhenOverlordAtWar() {
		WarManager.get().add(war(true, origin));
		assertNull(RelationManager.wartimeBlock(vassalType("subject"), target, origin));
	}

	@Test
	void vassal_changingExistingSubjectTypeNotBlocked() {
		RelationType overlord = type("overlord");
		when(overlord.isOverlord()).thenReturn(true);
		HashMap<String, Relation> targetRelations = new HashMap<>();
		targetRelations.put("origin", new Relation(overlord, mock(Attitude.class), 0));
		when(target.getRelations()).thenReturn(targetRelations);
		WarManager.get().add(war(true, target));

		assertNull(RelationManager.wartimeBlock(vassalType("march"), target, origin));
	}

	@Test
	void tributary_blockedWhenTargetAtWar() {
		WarManager.get().add(war(true, target));
		assertNotNull(RelationManager.wartimeBlock(type("tributary"), target, origin));
	}

	@Test
	void tributary_existingTributaryNotBlocked() {
		relate(origin, target, type("tributary"));
		WarManager.get().add(war(true, target));
		assertNull(RelationManager.wartimeBlock(type("tributary"), target, origin));
	}

	@Test
	void otherRelations_notBlocked() {
		WarManager.get().add(war(true, origin, target));
		assertNull(RelationManager.wartimeBlock(type("rival"), target, origin));
		assertNull(RelationManager.wartimeBlock(type("none"), target, origin));
	}

	private static War war(boolean active, Faction... participants) {
		War war = mock(War.class);
		when(war.isActive()).thenReturn(active);
		for (Faction f : participants) {
			when(war.isParticipating(f)).thenReturn(true);
		}
		return war;
	}

	private static Faction faction(String id) {
		Faction f = mock(Faction.class);
		when(f.getId()).thenReturn(id);
		when(f.getName()).thenReturn(id);
		when(f.getRelations()).thenReturn(new HashMap<>());
		return f;
	}

	private static void relate(Faction from, Faction to, RelationType type) {
		when(from.getRelation(to.getId())).thenReturn(new Relation(type, mock(Attitude.class), 0));
	}

	private static RelationType type(String id) {
		RelationType type = mock(RelationType.class);
		when(type.getId()).thenReturn(id);
		return type;
	}

	private static RelationType vassalType(String id) {
		RelationType type = type(id);
		when(type.isVassalage()).thenReturn(true);
		return type;
	}
}
