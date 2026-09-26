package net.tfminecraft.simplefactions.war.battle.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignOffensiveForfeitService;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;

class CampaignBattleLaunchServiceTest {
	private Faction attacker;
	private Faction defender;

	private SimpleFactions pluginBackup;

	@BeforeEach
	void setUp() {
		pluginBackup = SimpleFactions.plugin;
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		Cache.warFirstBattleAtBorder = true;
		Cache.battleCampaignTemplateField = "";
		Cache.battleCampaignTemplateSiege = "";
		Cache.warBattleWindowStartHour = 20;
		Cache.warBattleWindowEndHour = 24;
		CampaignBattleLaunchService.resetStartFailureAlertsForTests();

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Bob");
		mockMilitary(attacker);
		mockMilitary(defender);
	}

	private void mockMilitary(Faction faction) {
		Military military = mock(Military.class);
		Regiment levy = mock(Regiment.class);
		Regiment professional = mock(Regiment.class);
		when(professional.getId()).thenReturn("professional");
		when(professional.isLevy()).thenReturn(false);
		when(professional.isOffensive()).thenReturn(true);
		when(professional.getCurrentSlots()).thenReturn(10);
		when(military.getManpowerNoLevy(anyBoolean())).thenReturn(10);
		when(military.getRegiment("levy")).thenReturn(levy);
		when(military.getRegiments()).thenReturn(java.util.List.of(professional));
		when(levy.getEntries()).thenReturn(java.util.List.of());
		when(faction.getMilitary()).thenReturn(military);
		when(faction.getMembers()).thenReturn(java.util.List.of());
		when(faction.getName()).thenReturn("faction");
	}

	@AfterEach
	void restorePlugin() {
		SimpleFactions.plugin = pluginBackup;
	}

	@Test
	void resolve_returnsSiegeForSiegeSlot() {
		War war = baseWar();
		ScheduledCampaignBattle siege = new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, "fort_a");
		war.setCampaignBattleSchedule(List.of(siege));
		war.setCampaignScheduleIndex(0);

		assertEquals(BattleType.SIEGE, CampaignBattleTypeResolver.resolve(war, siege));
		assertEquals(BattleType.SIEGE, CampaignBattleTypeResolver.resolve(war, 20));
	}

	@Test
	void resolve_returnsFieldForCampaignProvince() {
		War war = baseWar();
		assertEquals(BattleType.FIELD, CampaignBattleTypeResolver.resolve(war, 20));
	}

	@Test
	void resolve_returnsFieldForNavalSlot() {
		War war = baseWar();
		ScheduledCampaignBattle naval = new ScheduledCampaignBattle(
				20, CampaignBattleKind.NAVAL, false, null, "port_a");
		war.setCampaignBattleSchedule(List.of(naval));
		war.setCampaignScheduleIndex(0);

		assertEquals(BattleType.FIELD, CampaignBattleTypeResolver.resolve(war, naval));
	}

	@Test
	void prepareScheduledBattle_navalSlot_setsNavalVariant() {
		War war = scheduledWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL, false, null, "port_a")));
		war.setCampaignScheduleIndex(0);

		withMockBossBar(() -> {
			Battle battle = CampaignBattleLaunchService.prepareScheduledBattle(war);

			assertNotNull(battle);
			assertEquals(BattleType.FIELD, battle.getBattleType());
			assertTrue(battle.isNavalVariant());
		});
	}

	@Test
	void prepareScheduledBattle_navalInvasionSlot_setsNavalVariant() {
		War war = scheduledWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL_INVASION, false, null)));
		war.setCampaignScheduleIndex(0);

		withMockBossBar(() -> {
			Battle battle = CampaignBattleLaunchService.prepareScheduledBattle(war);

			assertNotNull(battle);
			assertEquals(BattleType.FIELD, battle.getBattleType());
			assertTrue(battle.isNavalVariant());
		});
	}

	@Test
	void prepareScheduledBattle_siegeSlot_doesNotSetNavalVariant() {
		War war = scheduledWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, "fort_a")));
		war.setCampaignScheduleIndex(0);

		withMockBossBar(() -> {
			Battle battle = CampaignBattleLaunchService.prepareScheduledBattle(war);

			assertNotNull(battle);
			assertEquals(BattleType.SIEGE, battle.getBattleType());
			assertFalse(battle.isNavalVariant());
		});
	}

	@Test
	void prepareScheduledBattle_createsSiegeBattle() {
		War war = scheduledWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, "fort_a")));
		war.setCampaignScheduleIndex(0);

		withMockBossBar(() -> {
			Battle battle = CampaignBattleLaunchService.prepareScheduledBattle(war);

			assertNotNull(battle);
			assertEquals(BattleType.SIEGE, battle.getBattleType());
		});
	}

	@Test
	void prepareScheduledBattle_setsWarIdProvinceAndType() {
		War war = scheduledWar();

		withMockBossBar(() -> {
			Battle battle = CampaignBattleLaunchService.prepareScheduledBattle(war);

			assertNotNull(battle);
			assertEquals("campaign_w1_p20", battle.getId());
			assertEquals(Integer.valueOf(1), battle.getWarId());
			assertEquals(Integer.valueOf(20), battle.getProvinceId());
			assertEquals(BattleType.FIELD, battle.getBattleType());
			assertFalse(battle.isLocked());
			assertTrue(battle.hasTeleport());
		});
	}

	@Test
	void prepareScheduledBattle_isIdempotent() {
		War war = scheduledWar();

		withMockBossBar(() -> {
			Battle first = CampaignBattleLaunchService.prepareScheduledBattle(war);
			Battle second = CampaignBattleLaunchService.prepareScheduledBattle(war);
			assertEquals(first.getId(), second.getId());
			assertEquals(1, BattleManager.get().size());
		});
	}

	@Test
	void launchAutoresolveBattle_startsImmediately() {
		War war = baseWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.AUTORESOLVE_PENDING);

		withMockBossBar(() -> {
			Battle battle = CampaignBattleLaunchService.launchAutoresolveBattle(war);

			assertNotNull(battle);
			assertFalse(battle.hasStarted());
			assertEquals("campaign_w1_p20", battle.getId());
		});
	}

	@Test
	void tryStartScheduledBattle_failsWhenSiegeContestMissing() {
		War war = scheduledWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, "fort_a")));
		war.setCampaignScheduleIndex(0);
		Instant startAt = war.getScheduledBattleAt();
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));

		withMockBossBar(() -> {
			try (MockedStatic<CampaignOffensiveForfeitService> forfeit =
					mockStatic(CampaignOffensiveForfeitService.class)) {
				forfeit.when(() -> CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(
						any(), anyInt())).thenReturn(false);

				SimpleFactions.plugin = plugin;
				CampaignBattleLaunchService.prepareScheduledBattle(war);
				Battle battle = BattleManager.getByWarId(war.getId());
				assertNotNull(battle);
				assertEquals(BattleType.SIEGE, battle.getBattleType());

				assertFalse(CampaignBattleLaunchService.tryStartScheduledBattle(war, startAt));
				assertFalse(battle.hasStarted());
			}
		});
	}

	@Test
	void tryStartScheduledBattle_startsWhenDue() {
		War war = scheduledWar();
		Instant startAt = war.getScheduledBattleAt();
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));

		withMockBossBar(() -> {
			try (MockedStatic<CampaignOffensiveForfeitService> forfeit =
					mockStatic(CampaignOffensiveForfeitService.class)) {
				forfeit.when(() -> CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(
						any(), anyInt())).thenReturn(false);

				SimpleFactions.plugin = plugin;
				CampaignBattleLaunchService.prepareScheduledBattle(war);
				placeSpawnsAndJails(BattleManager.getByWarId(war.getId()));
				assertFalse(BattleManager.getByWarId(war.getId()).hasStarted());

				assertTrue(CampaignBattleLaunchService.tryStartScheduledBattle(war, startAt));
				assertTrue(BattleManager.getByWarId(war.getId()).hasStarted());
			}
		});
	}

	@Test
	void tryStartScheduledBattle_navalWithoutShips_appliesAutoLossAndDoesNotStart() {
		War war = scheduledNavalWar();
		Instant startAt = war.getScheduledBattleAt();
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
		ProvinceManager pm = new ProvinceManager();
		pm.start(java.util.Map.of(20, new Province(20, Terrain.PLAINS.name(), 50, 200, 200)));
		when(plugin.getProvinceManager()).thenReturn(pm);

		withMockBossBar(() -> {
			try (MockedStatic<CampaignOffensiveForfeitService> forfeit =
							mockStatic(CampaignOffensiveForfeitService.class);
					MockedStatic<WarManager> warManager = mockStatic(WarManager.class);
					MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class);
					MockedStatic<SimpleFactions> simpleFactions = mockStatic(SimpleFactions.class)) {
				forfeit.when(() -> CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(
						any(), anyInt())).thenReturn(false);
				warManager.when(() -> WarManager.persist(any())).then(inv -> null);
				warManager.when(() -> WarManager.getById(1)).thenReturn(war);
				titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
				simpleFactions.when(SimpleFactions::getVehicleRegistry).thenReturn(null);
				SimpleFactions.plugin = plugin;

				CampaignBattleLaunchService.prepareScheduledBattle(war);
				assertFalse(BattleManager.getByWarId(war.getId()).hasStarted());

				assertTrue(CampaignBattleLaunchService.tryStartScheduledBattle(war, startAt));
				assertEquals(3, war.getInitiativeAttacker());
				assertTrue(BattleManager.getByWarId(war.getId()) == null
						|| !BattleManager.getByWarId(war.getId()).hasStarted());
			}
		});
	}

	@Test
	void tryStartScheduledBattle_navalWithBerthedShip_starts() {
		War war = scheduledNavalWar();
		Instant startAt = war.getScheduledBattleAt();
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
		PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
		registry.register(new PlayerVehicleRecord(
				java.util.UUID.randomUUID(),
				"v-ship",
				"ironclad",
				OwnershipMode.INSTALLATION,
				"port-atk"));

		withMockBossBar(() -> {
			try (MockedStatic<CampaignOffensiveForfeitService> forfeit =
							mockStatic(CampaignOffensiveForfeitService.class);
					MockedStatic<SimpleFactions> simpleFactions = mockStatic(SimpleFactions.class);
					MockedStatic<VehiclesConfigLoader> vehicles = mockStatic(VehiclesConfigLoader.class)) {
				forfeit.when(() -> CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(
						any(), anyInt())).thenReturn(false);
				simpleFactions.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
				vehicles.when(() -> VehiclesConfigLoader.getCategoryId("ironclad"))
						.thenReturn(java.util.Optional.of("ships"));
				SimpleFactions.plugin = plugin;

				CampaignBattleLaunchService.prepareScheduledBattle(war);
				placeSpawnsAndJails(BattleManager.getByWarId(war.getId()));
				assertFalse(BattleManager.getByWarId(war.getId()).hasStarted());

				assertTrue(CampaignBattleLaunchService.tryStartScheduledBattle(war, startAt));
				assertTrue(BattleManager.getByWarId(war.getId()).hasStarted());
				assertEquals(4, war.getInitiativeAttacker());
			}
		});
	}

	@Test
	void prepareScheduledBattle_alertsAdminsAndLogsMissingSetupOnce() {
		War war = scheduledWar();
		Player admin = mock(Player.class);
		when(admin.isOnline()).thenReturn(true);
		when(admin.hasPermission(anyString())).thenReturn(true);
		Logger logger = mock(Logger.class);
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getLogger()).thenReturn(logger);
		SimpleFactions.plugin = plugin;

		withBukkit(bukkit -> {
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(admin));
			assertNotNull(CampaignBattleLaunchService.prepareScheduledBattle(war));
			assertNotNull(CampaignBattleLaunchService.prepareScheduledBattle(war));
		});

		verify(admin, times(1)).sendMessage(contains("Attacker spawn is not set."));
		verify(admin).sendMessage(contains("(field)"));
		verify(admin).sendMessage(contains("province §e20"));
		verify(admin).sendMessage(contains("CET"));
		verify(logger, times(1)).warning(contains("province 20"));
		verify(logger).warning(contains("Attacker jail is not set."));
	}

	@Test
	void prepareScheduledBattle_labelsNavalVariant() {
		War war = scheduledNavalWar();
		Player admin = mock(Player.class);
		when(admin.isOnline()).thenReturn(true);
		when(admin.hasPermission(anyString())).thenReturn(true);

		withBukkit(bukkit -> {
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(admin));
			CampaignBattleLaunchService.prepareScheduledBattle(war);
		});

		verify(admin).sendMessage(contains("(naval)"));
	}

	@Test
	void tryStartScheduledBattle_notifiesBelligerentsOnceAndAdminsOnEachNewError() {
		War war = scheduledWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, "fort_a")));
		war.setCampaignScheduleIndex(0);
		when(attacker.getMembers()).thenReturn(List.of("Alice"));
		Player alice = mock(Player.class);
		when(alice.isOnline()).thenReturn(true);
		when(alice.hasPermission(anyString())).thenReturn(false);
		Player admin = mock(Player.class);
		when(admin.isOnline()).thenReturn(true);
		when(admin.hasPermission(anyString())).thenReturn(true);
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getLogger()).thenReturn(Logger.getLogger("test-start-failure"));
		SimpleFactions.plugin = plugin;
		Instant startAt = war.getScheduledBattleAt();

		withBukkit(bukkit -> {
			bukkit.when(() -> Bukkit.getPlayerExact("Alice")).thenReturn(alice);
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
			try (MockedStatic<CampaignOffensiveForfeitService> forfeit =
					mockStatic(CampaignOffensiveForfeitService.class)) {
				forfeit.when(() -> CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(
						any(), anyInt())).thenReturn(false);

				CampaignBattleLaunchService.prepareScheduledBattle(war);
				bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(admin));
				assertFalse(CampaignBattleLaunchService.tryStartScheduledBattle(war, startAt));
				assertFalse(CampaignBattleLaunchService.tryStartScheduledBattle(war, startAt));
				verify(alice, times(1)).sendMessage(contains("could not start"));
				verify(admin, times(1)).sendMessage(contains("could not start"));

				Battle battle = BattleManager.getByWarId(war.getId());
				CampaignBattleLaunchService.broadcastStartFailure(war, battle, "Defender jail is not set.");
				verify(alice, times(1)).sendMessage(contains("could not start"));
				verify(admin, times(2)).sendMessage(contains("could not start"));
				assertFalse(battle.hasStarted());

				// A replacement battle keeps the id but is a new occurrence, so it alerts again.
				BattleManager.deleteBattle(battle);
				CampaignBattleLaunchService.prepareScheduledBattle(war);
				assertFalse(CampaignBattleLaunchService.tryStartScheduledBattle(war, startAt));
				verify(alice, times(2)).sendMessage(contains("could not start"));
			}
		});
	}

	private War baseWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(20);
		war.setCampaignProvinces(java.util.List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		war.setCampaignPhase(CampaignPhase.INVASION);
		return war;
	}

	private War scheduledWar() {
		War war = baseWar();
		LocalDate battleDay = LocalDate.of(2026, 8, 21);
		war.setBattleDay(battleDay);
		war.setScheduledBattleHour(21);
		war.setScheduledBattleAt(BattleWindowService.computeScheduledBattleAt(battleDay, 21));
		war.setScheduledBattleProvinceId(20);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		return war;
	}

	private War scheduledNavalWar() {
		War war = scheduledWar();
		Installation port = new Installation("port-atk", "Harbour", InstallationKind.PORT, 5, 0, 0, 1L);
		InstallationHandler attackerHandler = mock(InstallationHandler.class);
		when(attacker.getInstallationHandler()).thenReturn(attackerHandler);
		when(attackerHandler.getAll()).thenReturn(java.util.List.of(port));
		when(attackerHandler.getById("port-atk")).thenReturn(port);
		InstallationHandler defenderHandler = mock(InstallationHandler.class);
		when(defender.getInstallationHandler()).thenReturn(defenderHandler);
		when(defenderHandler.getAll()).thenReturn(java.util.List.of());
		war.setCampaignBattleSchedule(java.util.List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL, false, null, "port_zoc")));
		war.setCampaignScheduleIndex(0);
		war.getBattleInstallationPicks().computeIfAbsent("atk", ignored -> new java.util.LinkedHashSet<>())
				.add("port-atk");
		war.setBattleInstallationPicksBattleDay(war.getBattleDay());
		return war;
	}

	private static void placeSpawnsAndJails(Battle battle) {
		org.bukkit.World world = mock(org.bukkit.World.class);
		for (net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide side : battle.getSides()) {
			side.setSpawn(new org.bukkit.Location(world, 0, 64, 0));
			side.setJail(new org.bukkit.Location(world, 4, 64, 4));
		}
	}

	private void withMockBossBar(Runnable action) {
		withBukkit(bukkit -> action.run());
	}

	private void withBukkit(Consumer<MockedStatic<Bukkit>> action) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);
			action.accept(bukkit);
		}
	}
}
