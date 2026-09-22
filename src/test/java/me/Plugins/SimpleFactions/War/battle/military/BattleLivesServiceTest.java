package me.Plugins.SimpleFactions.War.battle.military;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Map.ProvinceGrid;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSideSetupService;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.LifeType;
import me.Plugins.SimpleFactions.War.battle.military.BattleLivesService.SideLivesPreview;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

import java.lang.reflect.Constructor;
import java.util.function.Consumer;

class BattleLivesServiceTest {
	private static final int PROVINCE_ID = 42;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		Cache.warBattleLivesPerRegiment = 5;
		Cache.warBattleMinSideLives = 1;
	}

	@Test
	void computeSideLives_formula() {
		assertEquals(48, BattleLivesService.computeSideLives(10, 2));
		assertEquals(11, BattleLivesService.computeSideLives(3, 4));
		assertEquals(0, BattleLivesService.computeSideLives(0, 10));
		assertEquals(1, BattleLivesService.computeSideLives(1, 10));
	}

	@Test
	void previewCampaignSideLives_asymmetric() {
		War war = campaignWar(1);
		withMockBossBar(bukkit -> {
			Battle battle = campaignFieldBattle(1, PROVINCE_ID);

			try (MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class);
					MockedStatic<BattleLivesService> lives = mockStatic(BattleLivesService.class, CALLS_REAL_METHODS)) {
				pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
						.thenReturn(10);
				pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getDefenders())))
						.thenReturn(3);
				lives.when(() -> BattleLivesService.countRosterFighters(battle.getSideById(BattleTemplate.ATTACKER_SIDE)))
						.thenReturn(12);
				lives.when(() -> BattleLivesService.countRosterFighters(battle.getSideById(BattleTemplate.DEFENDER_SIDE)))
						.thenReturn(4);

				SideLivesPreview attacker = BattleLivesService.previewCampaignSideLives(
						war, battle, BattleTemplate.ATTACKER_SIDE);
				SideLivesPreview defender = BattleLivesService.previewCampaignSideLives(
						war, battle, BattleTemplate.DEFENDER_SIDE);

				assertEquals(10, attacker.committedRegiments());
				assertEquals(50, attacker.poolLives());
				assertEquals(12, attacker.rosterFighters());
				assertEquals(38, attacker.sideLives());
				assertEquals(11, defender.sideLives());
			}
		});
	}

	@Test
	void applyCampaignLives_asymmetricSides() {
		War war = campaignWar(1);
		withMockBossBar(bukkit -> {
			Battle battle = campaignFieldBattle(1, PROVINCE_ID);

			try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
					MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class);
					MockedStatic<BattleLivesService> lives = mockStatic(BattleLivesService.class, CALLS_REAL_METHODS)) {
				wars.when(() -> WarManager.getById(1)).thenReturn(war);
				pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
						.thenReturn(10);
				pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getDefenders())))
						.thenReturn(3);
				lives.when(() -> BattleLivesService.countRosterFighters(battle.getSideById(BattleTemplate.ATTACKER_SIDE)))
						.thenReturn(2);
				lives.when(() -> BattleLivesService.countRosterFighters(battle.getSideById(BattleTemplate.DEFENDER_SIDE)))
						.thenReturn(4);

				BattleLivesService.applyCampaignLives(battle);

				assertEquals(LifeType.COLLECTIVE, battle.getLifeType());
				assertEquals(48, battle.getSideById(BattleTemplate.ATTACKER_SIDE).getLives());
				assertEquals(11, battle.getSideById(BattleTemplate.DEFENDER_SIDE).getLives());
			}
		});
	}

	@Test
	void applyCampaignLives_respectsMinFloor() {
		War war = campaignWar(2);
		withMockBossBar(bukkit -> {
			Battle battle = campaignFieldBattle(2, PROVINCE_ID);

			try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
					MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class);
					MockedStatic<BattleLivesService> lives = mockStatic(BattleLivesService.class, CALLS_REAL_METHODS)) {
				wars.when(() -> WarManager.getById(2)).thenReturn(war);
				pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), any(Side.class)))
						.thenReturn(1);
				lives.when(() -> BattleLivesService.countRosterFighters(any())).thenReturn(10);

				BattleLivesService.applyCampaignLives(battle);

				assertEquals(1, battle.getSideById(BattleTemplate.ATTACKER_SIDE).getLives());
				assertEquals(1, battle.getSideById(BattleTemplate.DEFENDER_SIDE).getLives());
			}
		});
	}

	@Test
	void countRosterFighters_countsUniqueWarbandMembers() {
		withMockBossBar(bukkit -> {
			BattleSide side = new BattleSide("attacker", LifeType.COLLECTIVE, 25);
			me.Plugins.SimpleFactions.War.battle.warband.Warband first =
					mock(me.Plugins.SimpleFactions.War.battle.warband.Warband.class);
			me.Plugins.SimpleFactions.War.battle.warband.Warband second =
					mock(me.Plugins.SimpleFactions.War.battle.warband.Warband.class);
			UUID shared = UUID.randomUUID();
			when(first.getMemberIds()).thenReturn(Set.of(shared, UUID.randomUUID()));
			when(second.getMemberIds()).thenReturn(Set.of(shared));
			side.addBand(first);
			side.addBand(second);

			assertEquals(2, BattleLivesService.countRosterFighters(side));
		});
	}

	@Test
	void setSideLives_rejectsCampaignBattle() {
		withMockBossBar(bukkit -> {
			Battle battle = campaignFieldBattle(1, PROVINCE_ID);
			BattleSide side = battle.getSideById(BattleTemplate.ATTACKER_SIDE);

			IllegalStateException error = assertThrows(
					IllegalStateException.class,
					() -> BattleSideSetupService.setSideLives(battle, side, 25));

			assertEquals("Campaign battle lives are computed from war commitment", error.getMessage());
		});
	}

	@Test
	void manualStart_preservesPerSideLives() throws Exception {
		SimpleFactions plugin = mock(SimpleFactions.class);
		ProvinceGrid grid = gridWithProvince(100, 100, 5, 5, 77);
		when(plugin.getProvinceGrid()).thenReturn(grid);
		when(plugin.getProvinceManager()).thenReturn(null);

		try (MockedStatic<SimpleFactions> sf = Mockito.mockStatic(SimpleFactions.class);
				MockedStatic<Bukkit> bukkit = mockBossBarStatic()) {
			sf.when(SimpleFactions::getInstance).thenReturn(plugin);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "manual");
			battle.setProvinceId(77);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			attacker.setLives(50);
			defender.setLives(10);
			attacker.setSpawn(location(5, 64, 5));
			defender.setSpawn(location(5, 64, 5));

			assertEquals(null, battle.start());

			assertEquals(50, attacker.getLives());
			assertEquals(10, defender.getLives());
		}
	}

	@Test
	void applyCampaignLives_skipsManualBattle() {
		withMockBossBar(bukkit -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "manual");
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setLives(25);
			battle.getSideById(BattleTemplate.DEFENDER_SIDE).setLives(25);

			BattleLivesService.applyCampaignLives(battle);

			assertEquals(25, battle.getSideById(BattleTemplate.ATTACKER_SIDE).getLives());
			assertEquals(25, battle.getSideById(BattleTemplate.DEFENDER_SIDE).getLives());
		});
	}

	@Test
	void applyCampaignLives_skipsRaid() {
		withMockBossBar(bukkit -> {
			Battle battle = BattleFactory.createBlank(BattleType.RAID, "raid");
			battle.setWarId(3);
			battle.setProvinceId(PROVINCE_ID);
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setLives(12);
			battle.getSideById(BattleTemplate.DEFENDER_SIDE).setLives(12);

			BattleLivesService.applyCampaignLives(battle);

			assertEquals(12, battle.getSideById(BattleTemplate.ATTACKER_SIDE).getLives());
			assertEquals(12, battle.getSideById(BattleTemplate.DEFENDER_SIDE).getLives());
		});
	}

	private static War campaignWar(int id) {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		War war = new War(id, attacker, defender);
		war.setCampaignProvinces(List.of(PROVINCE_ID, 43, 44));
		war.setCursorIndex(0);
		return war;
	}

	private static Battle campaignFieldBattle(int warId, int provinceId) {
		Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w" + warId);
		battle.setWarId(warId);
		battle.setProvinceId(provinceId);
		return battle;
	}

	private static Location location(double x, double y, double z) {
		World world = mock(World.class);
		return new Location(world, x, y, z);
	}

	private static ProvinceGrid gridWithProvince(int width, int height, int x, int z, int provinceId)
			throws Exception {
		short[] ids = new short[width * height];
		ids[z * width + x] = (short) provinceId;
		Constructor<ProvinceGrid> constructor = ProvinceGrid.class.getDeclaredConstructor(
				int.class, int.class, short[].class);
		constructor.setAccessible(true);
		return constructor.newInstance(width, height, ids);
	}

	private static MockedStatic<Bukkit> mockBossBarStatic() {
		BossBar bossBar = mock(BossBar.class);
		MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
				.thenReturn(bossBar);
		bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
				.thenReturn(bossBar);
		return bukkit;
	}

	private static void withMockBossBar(Consumer<MockedStatic<Bukkit>> action) {
		try (MockedStatic<Bukkit> bukkit = mockBossBarStatic()) {
			action.accept(bukkit);
		}
	}
}
