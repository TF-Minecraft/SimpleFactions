package me.Plugins.SimpleFactions.War.campaign.runtime.pick;


import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickEligibility;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class BattleInstallationPickEligibilityTest {
	private static final int ATTACKER_PROVINCE = 10;
	private static final int DEFENDER_PROVINCE = 20;

	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
	}

	@Test
	void isPickableKind_acceptsPortAndAirport() {
		assertTrue(BattleInstallationPickEligibility.isPickableKind(InstallationKind.PORT));
		assertTrue(BattleInstallationPickEligibility.isPickableKind(InstallationKind.AIRPORT));
	}

	@Test
	void isPickableKind_rejectsFort() {
		assertFalse(BattleInstallationPickEligibility.isPickableKind(InstallationKind.FORT));
		assertFalse(BattleInstallationPickEligibility.isPickableKind(null));
	}

	@Test
	void isPickable_attackerPortInDeJureHomeProvince() {
		War war = baseWar();
		Installation port = installation("port-1", InstallationKind.PORT, ATTACKER_PROVINCE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			assertTrue(BattleInstallationPickEligibility.isPickable(war, attacker, port));
		}
	}

	@Test
	void isPickable_rejectsFortRegardlessOfProvince() {
		War war = baseWar();
		Installation fort = installation("fort-1", InstallationKind.FORT, ATTACKER_PROVINCE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			assertFalse(BattleInstallationPickEligibility.isPickable(war, attacker, fort));
		}
	}

	@Test
	void isPickable_rejectsAttackerPortInEnemyOccupiedProvince() {
		War war = baseWar();
		war.setOccupiedByDefender(new ArrayList<>(List.of(ATTACKER_PROVINCE)));
		Installation port = installation("port-1", InstallationKind.PORT, ATTACKER_PROVINCE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			assertFalse(BattleInstallationPickEligibility.isPickable(war, attacker, port));
		}
	}

	@Test
	void isPickable_acceptsAttackerPortInOccupationBulge() {
		War war = baseWar();
		war.setOccupiedByAttacker(new ArrayList<>(List.of(DEFENDER_PROVINCE)));
		Installation port = installation("port-1", InstallationKind.PORT, DEFENDER_PROVINCE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			assertTrue(BattleInstallationPickEligibility.isPickable(war, attacker, port));
		}
	}

	@Test
	void isPickable_rejectsDefenderPortInEnemyOccupiedProvince() {
		War war = baseWar();
		war.setOccupiedByAttacker(new ArrayList<>(List.of(DEFENDER_PROVINCE)));
		Installation port = installation("port-1", InstallationKind.PORT, DEFENDER_PROVINCE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			assertFalse(BattleInstallationPickEligibility.isPickable(war, defender, port));
		}
	}

	@Test
	void isPickable_acceptsDefenderPortInOccupationBulge() {
		War war = baseWar();
		war.setOccupiedByDefender(new ArrayList<>(List.of(ATTACKER_PROVINCE)));
		Installation port = installation("port-1", InstallationKind.PORT, ATTACKER_PROVINCE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			assertTrue(BattleInstallationPickEligibility.isPickable(war, defender, port));
		}
	}

	@Test
	void listPickableInstallations_filtersKindAndControl() {
		War war = baseWar();
		Installation port = installation("port-1", InstallationKind.PORT, ATTACKER_PROVINCE);
		Installation fort = installation("fort-1", InstallationKind.FORT, ATTACKER_PROVINCE);
		InstallationHandler handler = mock(InstallationHandler.class);
		when(attacker.getInstallationHandler()).thenReturn(handler);
		when(handler.getAll()).thenReturn(List.of(port, fort));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			assertEquals(List.of(port), BattleInstallationPickEligibility.listPickableInstallations(war, attacker));
		}
	}

	private War baseWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setOccupiedByAttacker(new ArrayList<>());
		war.setOccupiedByDefender(new ArrayList<>());
		return war;
	}

	private static Installation installation(String id, InstallationKind kind, int province) {
		return new Installation(id, "Test", kind, province, 0, 0, 0L);
	}

	private void stubProvinceOwnership(MockedStatic<TitleManager> titleManager) {
		titleManager.when(() -> TitleManager.getByProvince(ATTACKER_PROVINCE)).thenReturn(attacker);
		titleManager.when(() -> TitleManager.getByProvince(DEFENDER_PROVINCE)).thenReturn(defender);
	}
}
