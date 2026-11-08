package net.tfminecraft.simplefactions.vehicles.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.LifeType;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

class BattleForcedVehiclesTest {
	private MockedStatic<Bukkit> bukkit;
	private MockedStatic<BattlePersistenceService> persistence;
	private Battle battle;
	private Player staff;
	private ActiveVehicle vehicle;
	private final BattleForcedVehicles.Listener listener = new BattleForcedVehicles.Listener();

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		BattleForcedVehicles.resetForTests();
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any(BarFlag[].class)))
				.thenAnswer(call -> mock(BossBar.class));
		persistence = mockStatic(BattlePersistenceService.class);
		battle = new Battle("b1");
		battle.setDisplayName("Battle of Ford");
		battle.setWarId(3);
		battle.addSide(new BattleSide("Attacker", LifeType.COLLECTIVE, 10));
		battle.addSide(new BattleSide("defender", LifeType.COLLECTIVE, 10));
		BattleManager.addBattle(battle);
		staff = mock(Player.class);
		when(staff.getUniqueId()).thenReturn(UUID.randomUUID());
		when(staff.getName()).thenReturn("Staff");
		vehicle = mock(ActiveVehicle.class);
		when(vehicle.getUUID()).thenReturn("veh-1");
		when(vehicle.getId()).thenReturn("biplane");
	}

	@AfterEach
	void tearDown() {
		BattleForcedVehicles.resetForTests();
		BattleManager.resetForTests();
		persistence.close();
		bukkit.close();
	}

	private VehiclePreInteractEvent click(Player player, ActiveVehicle clicked) {
		VehiclePreInteractEvent event = new VehiclePreInteractEvent(player, clicked);
		listener.onVehiclePreInteract(event);
		return event;
	}

	@Test
	void runsBeforeTheBattleAndTransferListeners() throws Exception {
		EventHandler handler = BattleForcedVehicles.Listener.class
				.getMethod("onVehiclePreInteract", VehiclePreInteractEvent.class).getAnnotation(EventHandler.class);
		assertEquals(EventPriority.LOW, handler.priority());
	}

	@Test
	void clicksWithoutAnArmedModeAreLeftAlone() {
		assertFalse(click(staff, vehicle).isCancelled());
		assertFalse(click(null, vehicle).isCancelled());
		BattleForcedVehicles.arm(staff.getUniqueId(), "b1", "attacker");
		assertFalse(click(staff, null).isCancelled());
		when(vehicle.getUUID()).thenReturn(null);
		assertFalse(click(staff, vehicle).isCancelled());
		assertTrue(battle.getForcedVehicles().isEmpty());
	}

	@Test
	void eachClickAssignsMovesOrReleasesTheVehicle() {
		BattleForcedVehicles.arm(staff.getUniqueId(), "B1", "attacker");

		assertTrue(click(staff, vehicle).isCancelled());
		assertEquals("Attacker", battle.getForcedVehicleSide("veh-1"));
		verify(staff).sendMessage("§aVehicle biplane can now be used by Attacker in Battle of Ford.");

		assertTrue(click(staff, vehicle).isCancelled());
		assertNull(battle.getForcedVehicleSide("veh-1"));
		verify(staff).sendMessage("§eVehicle biplane is no longer forced to Attacker in Battle of Ford;"
				+ " the normal vehicle rules apply.");

		battle.setForcedVehicleSide("veh-1", "defender");
		click(staff, vehicle);
		assertEquals("Attacker", battle.getForcedVehicleSide("veh-1"));
		persistence.verify(() -> BattlePersistenceService.persistBattle(battle), org.mockito.Mockito.times(3));
	}

	@Test
	void aGoneBattleOrSideEndsTheModeWithoutTouchingTheVehicle() {
		BattleForcedVehicles.arm(staff.getUniqueId(), "b1", "nobody");
		assertTrue(click(staff, vehicle).isCancelled());
		verify(staff).sendMessage("§cThat battle or side is gone. Force-vehicle mode off.");
		assertNull(BattleForcedVehicles.get(staff.getUniqueId()));

		BattleForcedVehicles.arm(staff.getUniqueId(), "gone", "attacker");
		assertTrue(click(staff, vehicle).isCancelled());
		assertNull(BattleForcedVehicles.get(staff.getUniqueId()));
		assertTrue(battle.getForcedVehicles().isEmpty());
		persistence.verify(() -> BattlePersistenceService.persistBattle(any()), never());
	}

	@Test
	void disarmReportsWhetherAModeWasOn() {
		assertFalse(BattleForcedVehicles.disarm(staff.getUniqueId()));
		BattleForcedVehicles.arm(staff.getUniqueId(), "b1", "attacker");
		assertTrue(BattleForcedVehicles.disarm(staff.getUniqueId()));
		assertNull(BattleForcedVehicles.get(staff.getUniqueId()));
	}
}
