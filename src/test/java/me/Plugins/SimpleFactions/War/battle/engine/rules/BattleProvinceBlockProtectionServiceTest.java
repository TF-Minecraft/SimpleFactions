package me.Plugins.SimpleFactions.War.battle.engine.rules;





import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.rules.BattleProvinceBlockProtectionService;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Map.ProvinceGrid;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;

class BattleProvinceBlockProtectionServiceTest {
	private static final int BATTLE_PROVINCE_ID = 20;

	private SimpleFactions plugin;
	private Location battleLocation;

	@BeforeEach
	void setUp() throws Exception {
		BattleManager.resetForTests();
		Cache.mapEnabled = true;
		Cache.battleProvinceBlockProtectionEnabled = true;

		plugin = mock(SimpleFactions.class);
		ProvinceGrid grid = gridWithProvince(100, 100, 50, 50, BATTLE_PROVINCE_ID);
		when(plugin.getProvinceGrid()).thenReturn(grid);

		World world = mock(World.class);
		battleLocation = new Location(world, 50, 64, 50);
	}

	@AfterEach
	void tearDown() {
		BattleManager.resetForTests();
		Cache.battleProvinceBlockProtectionEnabled = false;
		Cache.mapEnabled = true;
	}

	@Test
	void toggleOff_notBlocked() {
		Cache.battleProvinceBlockProtectionEnabled = false;
		addStartedBattle(BattleType.FIELD, BATTLE_PROVINCE_ID);

		try (MockedStatic<SimpleFactions> pluginStatic = Mockito.mockStatic(SimpleFactions.class)) {
			pluginStatic.when(SimpleFactions::getInstance).thenReturn(plugin);
			assertFalse(BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(battleLocation));
		}
	}

	@Test
	void toggleOn_noActiveBattle_notBlocked() {
		try (MockedStatic<SimpleFactions> pluginStatic = Mockito.mockStatic(SimpleFactions.class)) {
			pluginStatic.when(SimpleFactions::getInstance).thenReturn(plugin);
			assertFalse(BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(battleLocation));
		}
	}

	@Test
	void toggleOn_startedFieldBattle_wrongProvince_notBlocked() throws Exception {
		addStartedBattle(BattleType.FIELD, 99);

		try (MockedStatic<SimpleFactions> pluginStatic = Mockito.mockStatic(SimpleFactions.class)) {
			pluginStatic.when(SimpleFactions::getInstance).thenReturn(plugin);
			assertFalse(BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(battleLocation));
		}
	}

	@Test
	void toggleOn_startedFieldBattle_matchingProvince_blocked() {
		addStartedBattle(BattleType.FIELD, BATTLE_PROVINCE_ID);

		try (MockedStatic<SimpleFactions> pluginStatic = Mockito.mockStatic(SimpleFactions.class)) {
			pluginStatic.when(SimpleFactions::getInstance).thenReturn(plugin);
			assertTrue(BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(battleLocation));
		}
	}

	@Test
	void toggleOn_startedSiegeBattle_matchingProvince_blocked() {
		addStartedBattle(BattleType.SIEGE, BATTLE_PROVINCE_ID);

		try (MockedStatic<SimpleFactions> pluginStatic = Mockito.mockStatic(SimpleFactions.class)) {
			pluginStatic.when(SimpleFactions::getInstance).thenReturn(plugin);
			assertTrue(BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(battleLocation));
		}
	}

	@Test
	void toggleOn_startedRaidBattle_notBlocked() {
		addStartedBattle(BattleType.RAID, BATTLE_PROVINCE_ID);

		try (MockedStatic<SimpleFactions> pluginStatic = Mockito.mockStatic(SimpleFactions.class)) {
			pluginStatic.when(SimpleFactions::getInstance).thenReturn(plugin);
			assertFalse(BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(battleLocation));
		}
	}

	@Test
	void toggleOn_battleNotStarted_notBlocked() {
		Battle battle = createBattle(BattleType.FIELD, "not-started");
		battle.setProvinceId(BATTLE_PROVINCE_ID);
		battle.setStarted(false);
		BattleManager.addBattle(battle);

		try (MockedStatic<SimpleFactions> pluginStatic = Mockito.mockStatic(SimpleFactions.class)) {
			pluginStatic.when(SimpleFactions::getInstance).thenReturn(plugin);
			assertFalse(BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(battleLocation));
		}
	}

	private void addStartedBattle(BattleType type, int provinceId) {
		Battle battle = createBattle(type, type.name().toLowerCase() + "-" + provinceId);
		battle.setProvinceId(provinceId);
		battle.setStarted(true);
		BattleManager.addBattle(battle);
	}

	private Battle createBattle(BattleType type, String id) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);
			return BattleFactory.createBlank(type, id);
		}
	}

	private ProvinceGrid gridWithProvince(int width, int height, int x, int z, int provinceId) throws Exception {
		short[] ids = new short[width * height];
		ids[z * width + x] = (short) provinceId;
		Constructor<ProvinceGrid> constructor = ProvinceGrid.class.getDeclaredConstructor(
				int.class, int.class, short[].class);
		constructor.setAccessible(true);
		return constructor.newInstance(width, height, ids);
	}
}
