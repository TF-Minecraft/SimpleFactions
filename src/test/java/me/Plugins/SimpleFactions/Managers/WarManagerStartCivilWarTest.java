package me.Plugins.SimpleFactions.Managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignNavyGate;
import me.Plugins.SimpleFactions.War.commitment.WarCommitmentService;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.declare.WarGoalValidator;
import me.Plugins.SimpleFactions.War.declare.WarValidationResult;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;

class WarManagerStartCivilWarTest {
	private final List<War> savedWars = new ArrayList<>();

	@BeforeEach
	void setUp() {
		savedWars.addAll(WarManager.get());
		WarManager.get().clear();
	}

	@AfterEach
	void tearDown() {
		WarManager.get().clear();
		WarManager.get().addAll(savedWars);
	}

	@Test
	void startCivilWar_skipsGoalValidator_setsMovementIdAndCivilWarFlag() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("rebels");
		when(defender.getId()).thenReturn("host");
		when(attacker.getMembers()).thenReturn(List.of());
		when(defender.getMembers()).thenReturn(List.of());
		when(attacker.getRelations()).thenReturn(new HashMap<>());
		when(defender.getRelations()).thenReturn(new HashMap<>());

		try (MockedStatic<WarGoalValidator> validator = mockStatic(WarGoalValidator.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class, CALLS_REAL_METHODS);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<WarCommitmentService> commit = mockStatic(WarCommitmentService.class);
				MockedStatic<CampaignNavyGate> navy = mockStatic(CampaignNavyGate.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			wars.when(() -> WarManager.populateCampaignIfNeeded(any())).thenReturn(true);
			navy.when(() -> CampaignNavyGate.validateDeclareAfterPopulate(any()))
					.thenReturn(WarValidationResult.ok());
			relations.when(() -> RelationManager.getSubjects(any())).thenReturn(List.of());
			relations.when(() -> RelationManager.getAllies(any())).thenReturn(List.of());
			bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);

			War war = WarManager.startCivilWar(
					attacker,
					defender,
					WarGoalType.OVERTHROW,
					"movement-1",
					List.of(),
					List.of(),
					null);

			assertNotNull(war);
			assertEquals("movement-1", war.getMovementId());
			assertTrue(war.getAttackers().getMainParticipants().get(0).isCivilWar());
			assertTrue(war.getDefenders().getMainParticipants().get(0).isCivilWar());
			validator.verifyNoInteractions();
		}
	}

	@Test
	void startCivilWar_putsForeignBackerOnBackersNotAllies() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		Faction backer = mock(Faction.class);
		when(attacker.getId()).thenReturn("rebels");
		when(defender.getId()).thenReturn("host");
		when(backer.getId()).thenReturn("brume");
		when(attacker.getMembers()).thenReturn(List.of());
		when(defender.getMembers()).thenReturn(List.of());
		when(attacker.getRelations()).thenReturn(new HashMap<>());
		when(defender.getRelations()).thenReturn(new HashMap<>());

		try (MockedStatic<WarGoalValidator> validator = mockStatic(WarGoalValidator.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class, CALLS_REAL_METHODS);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<WarCommitmentService> commit = mockStatic(WarCommitmentService.class);
				MockedStatic<CampaignNavyGate> navy = mockStatic(CampaignNavyGate.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			wars.when(() -> WarManager.populateCampaignIfNeeded(any())).thenReturn(true);
			navy.when(() -> CampaignNavyGate.validateDeclareAfterPopulate(any()))
					.thenReturn(WarValidationResult.ok());
			relations.when(() -> RelationManager.getSubjects(any())).thenReturn(List.of());
			relations.when(() -> RelationManager.getAllies(any())).thenReturn(List.of());
			bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);

			War war = WarManager.startCivilWar(
					attacker,
					defender,
					WarGoalType.OVERTHROW,
					"movement-1",
					List.of(),
					List.of(backer),
					null);

			assertNotNull(war);
			Participant leader = war.getAttackers().getMainParticipants().get(0);
			assertTrue(leader.getBackers().contains(backer));
			assertFalse(leader.getAllies().containsKey(backer));
			assertTrue(leader.isJoinedSecondary(backer));
			validator.verifyNoInteractions();
		}
	}
}
