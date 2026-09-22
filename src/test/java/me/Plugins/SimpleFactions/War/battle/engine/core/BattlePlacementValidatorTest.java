package me.Plugins.SimpleFactions.War.battle.engine.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.Map.ProvinceGrid;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

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
