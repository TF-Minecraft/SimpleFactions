package net.tfminecraft.simplefactions.war.declare;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;

class WarDeclareConflictTest {
	private final List<War> savedWars = new ArrayList<>();
	private final List<RelationType> savedRelationTypes = new ArrayList<>();
	private WarGoalValidator validator;

	@BeforeEach
	void setUp() throws Exception {
		validator = new WarGoalValidator();
		savedWars.clear();
		savedWars.addAll(activeWars());
		activeWars().clear();
		savedRelationTypes.addAll(RelationLoader.types);
		RelationLoader.types.clear();
	}

	@AfterEach
	void tearDown() throws Exception {
		activeWars().clear();
		activeWars().addAll(savedWars);
		RelationLoader.types.clear();
		RelationLoader.types.addAll(savedRelationTypes);
	}

	@Test
	void findSharedActiveWar_detectsOppositeSides() throws Exception {
		Faction attacker = faction("atk");
		Faction defender = faction("def");
		activeWars().add(new War(1, attacker, defender));

		assertNotNull(WarManager.findSharedActiveWar(attacker, defender));
		assertTrue(WarManager.existsHostile(attacker, defender));
	}

	@Test
	void findSharedActiveWar_detectsCalledAllyOnSameSide() throws Exception {
		Faction leader = faction("leader");
		Faction ally = faction("ally");
		Faction enemy = faction("enemy");
		War war = new War(2, leader, enemy);
		Participant participant = war.getParticipant(leader);
		participant.getAllies().put(ally, true);
		activeWars().add(war);

		assertNotNull(WarManager.findSharedActiveWar(leader, ally));
		assertFalse(WarManager.existsHostile(leader, ally));
	}

	@Test
	void findSharedActiveWar_ignoresEndedWars() throws Exception {
		Faction attacker = faction("atk");
		Faction defender = faction("def");
		War war = new War(3, attacker, defender);
		war.end(net.tfminecraft.simplefactions.war.enums.WarEndReason.WHITE_PEACE);
		activeWars().add(war);

		assertNull(WarManager.findSharedActiveWar(attacker, defender));
	}

	@Test
	void validate_rejectsWhenAlreadyAtWar() throws Exception {
		Faction attacker = faction("atk");
		Faction defender = faction("def");
		activeWars().add(new War(4, attacker, defender));

		WarValidationResult result = validator.validate(
				WarDeclareRequest.of(attacker, defender, WarGoalType.SUBJUGATE));

		assertFalse(result.isValid());
		assertTrue(result.getMessage().contains("already at war"));
	}

	@Test
	void validate_rejectsWhenAlreadyAlliedInWar() throws Exception {
		Faction leader = faction("leader");
		Faction ally = faction("ally");
		Faction enemy = faction("enemy");
		War war = new War(5, leader, enemy);
		war.getParticipant(leader).getAllies().put(ally, true);
		activeWars().add(war);

		WarValidationResult result = validator.validate(
				WarDeclareRequest.of(leader, ally, WarGoalType.SUBJUGATE));

		assertFalse(result.isValid());
		assertTrue(result.getMessage().contains("already allied"));
	}

	@Test
	void validate_allowsWhenFactionsNotInSameActiveWar() throws Exception {
		Faction attacker = faction("atk");
		Faction defender = faction("def");
		Faction other = faction("other");
		net.tfminecraft.simplefactions.managers.FactionManager.factions.add(defender);
		try {
			activeWars().add(new War(6, attacker, other));

			WarValidationResult result = validator.validate(
					subjugateRequest(attacker, defender, "subject"));

			assertTrue(result.isValid());
		} finally {
			net.tfminecraft.simplefactions.managers.FactionManager.factions.remove(defender);
		}
	}

	@SuppressWarnings("unchecked")
	private static List<War> activeWars() throws Exception {
		Field field = WarManager.class.getDeclaredField("wars");
		field.setAccessible(true);
		return (List<War>) field.get(null);
	}

	private static Faction faction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getRelations()).thenReturn(new HashMap<>());
		when(faction.canHaveVassals()).thenReturn(true);
		return faction;
	}

	private static WarDeclareRequest subjugateRequest(Faction attacker, Faction defender, String typeId) {
		RelationType type = mock(RelationType.class);
		when(type.getId()).thenReturn(typeId);
		when(type.isVassalage()).thenReturn(true);
		when(type.canPickForWar()).thenReturn(true);
		RelationLoader.types.add(type);
		return new WarDeclareRequest(attacker, defender, WarGoalType.SUBJUGATE, null, null, typeId);
	}
}
