package net.tfminecraft.simplefactions.war.battle.engine.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;

class BattlePlacementValidatorTest {
	private SimpleFactions plugin;
	private ProvinceGrid grid;

	@BeforeEach
	void setUp() throws Exception {
		BattleManager.resetForTests();
		plugin = mock(SimpleFactions.class);
		grid = gridWithProvince(100, 100, 5, 5, 77);
		when(plugin.getProvinceGrid()).thenReturn(grid);
		when(plugin.getProvinceManager()).thenReturn(null);
	}

	@Test
	void isLocationAllowed_allowsAnyProvince() {
		try (MockedStatic<SimpleFactions> sf = Mockito.mockStatic(SimpleFactions.class);
				MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar()) {
			sf.when(SimpleFactions::getInstance).thenReturn(plugin);
			Battle battle = fieldBattle();
			battle.setProvinceId(77);

			assertTrue(BattlePlacementValidator.isLocationAllowed(battle, location(5, 64, 5)));
			assertTrue(BattlePlacementValidator.isLocationAllowed(battle, location(50, 64, 50)));
		}
	}

	@Test
	void validateForStart_allowsCapturePointOutsideProvince() {
		try (MockedStatic<SimpleFactions> sf = Mockito.mockStatic(SimpleFactions.class);
				MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar()) {
			sf.when(SimpleFactions::getInstance).thenReturn(plugin);
			Battle battle = fieldBattle();
			battle.setProvinceId(77);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			attacker.setSpawn(location(5, 64, 5));
			defender.setSpawn(location(5, 64, 5));

			CapturePoint point = new CapturePoint("A", location(50, 64, 50), attacker, 100);
			point.setAdvanceSideId(BattleTemplate.ATTACKER_SIDE);
			battle.addPoint(point);

			assertNull(BattlePlacementValidator.validateForStart(battle));
		}
	}

	@Test
	void start_succeedsWithSpawnOutsideBattleProvince() {
		try (MockedStatic<SimpleFactions> sf = Mockito.mockStatic(SimpleFactions.class);
				MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar()) {
			sf.when(SimpleFactions::getInstance).thenReturn(plugin);
			Battle battle = fieldBattle();
			battle.setProvinceId(77);
			battle.addPoint(new CapturePoint("A", location(50, 64, 50),
					battle.getSideById(BattleTemplate.ATTACKER_SIDE), 100));

			assertNull(battle.start());
			assertTrue(battle.hasStarted());
		}
	}

	@Test
	void validateForStart_blocksSiegeWithoutContestArea() {
		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar()) {
			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "siege_test");

			String error = BattlePlacementValidator.validateForStart(battle);

			assertNotNull(error);
			assertTrue(error.contains("contest area"));
		}
	}

	@Test
	void validate_campaignFieldReportsMissingSpawnAndJail() {
		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar()) {
			Battle battle = fieldBattle();
			battle.setWarId(1);
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setSpawn(location(1, 64, 1));

			java.util.List<String> errors = BattlePlacementValidator.validate(battle);

			assertTrue(errors.stream().anyMatch(error -> error.contains("Attacker jail")));
			assertTrue(errors.stream().anyMatch(error -> error.contains("Defender spawn")));
			assertTrue(errors.stream().anyMatch(error -> error.contains("Defender jail")));
			assertTrue(errors.stream().noneMatch(error -> error.contains("Attacker spawn")));
		}
	}

	@Test
	void start_campaignFieldDoesNotStartWhenJailMissing() {
		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar()) {
			Battle battle = fieldBattle();
			battle.setWarId(1);
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setSpawn(location(1, 64, 1));
			battle.getSideById(BattleTemplate.DEFENDER_SIDE).setSpawn(location(2, 64, 2));
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setJail(location(1, 64, 3));

			String error = battle.start();

			assertNotNull(error);
			assertTrue(error.contains("jail"));
			assertFalse(battle.hasStarted());
		}
	}

	@Test
	void start_skipsTeleportWhenSideHasNoSpawn() {
		SimpleFactions pluginBefore = SimpleFactions.plugin;
		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar()) {
			BukkitScheduler scheduler = mock(BukkitScheduler.class);
			bukkit.when(org.bukkit.Bukkit::getScheduler).thenReturn(scheduler);
			when(scheduler.runTaskLater(nullable(Plugin.class), any(Runnable.class), anyLong())).thenReturn(null);
			bukkit.when(org.bukkit.Bukkit::getPluginManager).thenReturn(null);

			UUID withSpawnId = UUID.randomUUID();
			UUID withoutSpawnId = UUID.randomUUID();
			Player withSpawn = mock(Player.class);
			Player withoutSpawn = mock(Player.class);
			when(withSpawn.isOnline()).thenReturn(true);
			when(withoutSpawn.isOnline()).thenReturn(true);
			when(withSpawn.getUniqueId()).thenReturn(withSpawnId);
			when(withoutSpawn.getUniqueId()).thenReturn(withoutSpawnId);
			bukkit.when(() -> org.bukkit.Bukkit.getPlayer(withSpawnId)).thenReturn(withSpawn);
			bukkit.when(() -> org.bukkit.Bukkit.getPlayer(withoutSpawnId)).thenReturn(withoutSpawn);

			Battle battle = fieldBattle();
			battle.setProvinceId(77);
			battle.setTeleport(true);
			Location spawn = location(5, 64, 5);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			attacker.setSpawn(spawn);
			attacker.addBand(Warband.createWithMemberIds("atk", withSpawnId, true));
			battle.getSideById(BattleTemplate.DEFENDER_SIDE)
					.addBand(Warband.createWithMemberIds("def", withoutSpawnId, true));
			SimpleFactions.plugin = plugin;

			assertNull(battle.start());
			assertTrue(battle.hasStarted());
			verify(withSpawn).teleport(spawn);
			verify(withoutSpawn, never()).teleport(any(Location.class));
		} finally {
			SimpleFactions.plugin = pluginBefore;
		}
	}

	@Test
	void start_returnsNullWhenValidationPasses() {
		try (MockedStatic<SimpleFactions> sf = Mockito.mockStatic(SimpleFactions.class);
				MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar()) {
			sf.when(SimpleFactions::getInstance).thenReturn(plugin);
			Battle battle = fieldBattle();
			battle.setProvinceId(77);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			attacker.setSpawn(location(5, 64, 5));
			defender.setSpawn(location(5, 64, 5));

			assertNull(battle.start());
			assertTrue(battle.hasStarted());
		}
	}

	private MockedStatic<org.bukkit.Bukkit> mockBossBar() {
		BossBar bossBar = mock(BossBar.class);
		MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class);
		bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
				Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
				.thenReturn(bossBar);
		bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
				Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
				.thenReturn(bossBar);
		return bukkit;
	}

	private Battle fieldBattle() {
		return BattleFactory.createBlank(BattleType.FIELD, "test");
	}

	private Location location(double x, double y, double z) {
		return new Location(mock(World.class), x, y, z);
	}

	private ProvinceGrid gridWithProvince(int width, int height, int x, int z, int provinceId) throws Exception {
		short[] ids = new short[width * height];
		ids[z * width + x] = (short) provinceId;
		Constructor<ProvinceGrid> constructor = ProvinceGrid.class.getDeclaredConstructor(int.class, int.class, short[].class);
		constructor.setAccessible(true);
		return constructor.newInstance(width, height, ids);
	}
}
