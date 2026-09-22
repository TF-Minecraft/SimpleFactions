package me.Plugins.SimpleFactions.War.campaign.progression;



import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;

class AttackerNavalContestServiceTest {
	private Faction attacker;
	private Faction defender;
	private InstallationHandler attackerHandler;
	private SimpleFactions pluginBackup;
	private MockedStatic<Bukkit> bukkitMock;
	private MockedStatic<WarManager> warManagerMock;
	private MockedStatic<TitleManager> titleManagerMock;
	private MockedStatic<SimpleFactions> simpleFactionsMock;
	private MockedStatic<VehiclesConfigLoader> vehiclesMock;
	private PlayerVehicleRegistry registry;

	@BeforeEach
	void setUp() {
		Cache.warFirstBattleAtBorder = true;
		BattleManager.resetForTests();

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getMembers()).thenReturn(List.of());
		when(defender.getMembers()).thenReturn(List.of());
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Bob");

		Installation port = new Installation("port-atk", "Harbour", InstallationKind.PORT, 5, 0, 0, 1L);
		attackerHandler = mock(InstallationHandler.class);
		when(attacker.getInstallationHandler()).thenReturn(attackerHandler);
		when(attackerHandler.getAll()).thenReturn(List.of(port));
		when(attackerHandler.getById("port-atk")).thenReturn(port);

		InstallationHandler defenderHandler = mock(InstallationHandler.class);
		when(defender.getInstallationHandler()).thenReturn(defenderHandler);
		when(defenderHandler.getAll()).thenReturn(List.of());

		registry = new PlayerVehicleRegistry();
		pluginBackup = SimpleFactions.plugin;
		ProvinceManager pm = new ProvinceManager();
		pm.start(Map.of(20, new Province(20, Terrain.PLAINS.name(), 50, 200, 200)));
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);

		simpleFactionsMock = mockStatic(SimpleFactions.class);
		simpleFactionsMock.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
		SimpleFactions.plugin = plugin;

		vehiclesMock = mockStatic(VehiclesConfigLoader.class);
		vehiclesMock.when(() -> VehiclesConfigLoader.getCategoryId("ironclad"))
				.thenReturn(Optional.of("ships"));
		vehiclesMock.when(() -> VehiclesConfigLoader.getCategoryId("cannon"))
				.thenReturn(Optional.of("static_emplacements"));

		titleManagerMock = mockStatic(TitleManager.class);
		titleManagerMock.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);

		BossBar bossBar = mock(BossBar.class);
		bukkitMock = mockStatic(Bukkit.class);
		bukkitMock.when(() -> Bukkit.createBossBar(any(), any(BarColor.class), any(BarStyle.class))).thenReturn(bossBar);
		bukkitMock.when(() -> Bukkit.createBossBar(any(), any(BarColor.class), any(BarStyle.class), any()))
				.thenReturn(bossBar);

		warManagerMock = mockStatic(WarManager.class);
		warManagerMock.when(() -> WarManager.persist(any())).then(inv -> null);
	}

	@AfterEach
	void tearDown() {
		bukkitMock.close();
		warManagerMock.close();
		titleManagerMock.close();
		vehiclesMock.close();
		simpleFactionsMock.close();
		SimpleFactions.plugin = pluginBackup;
		BattleManager.resetForTests();
	}

	@Test
	void hasBerthedNavalAtInPlayPort_trueWhenShipBerthedAtCommittedPort() {
		War war = navalWar();
		commitAttackerPort(war);
		registry.register(shipAt("port-atk"));

		assertTrue(AttackerNavalContestService.hasBerthedNavalAtInPlayPort(war));
		assertFalse(AttackerNavalContestService.wouldAttackerAutoLoseNaval(war));
	}

	@Test
	void hasBerthedNavalAtInPlayPort_falseWhenPortNotInPlay() {
		War war = navalWar();
		registry.register(shipAt("port-atk"));

		assertFalse(AttackerNavalContestService.hasBerthedNavalAtInPlayPort(war));
	}

	@Test
	void hasBerthedNavalAtInPlayPort_falseForPersonalUnberthedShip() {
		War war = navalWar();
		commitAttackerPort(war);
		registry.register(new PlayerVehicleRecord(
				UUID.randomUUID(), "v1", "ironclad", OwnershipMode.PERSONAL, null));

		assertFalse(AttackerNavalContestService.hasBerthedNavalAtInPlayPort(war));
	}

	@Test
	void hasBerthedNavalAtInPlayPort_falseWhenRegistryNull() {
		simpleFactionsMock.when(SimpleFactions::getVehicleRegistry).thenReturn(null);
		War war = navalWar();
		commitAttackerPort(war);

		assertFalse(AttackerNavalContestService.hasBerthedNavalAtInPlayPort(war));
	}

	@Test
	void applyIfAttackerHasNoBerthedNavy_appliesDefenderWinAndSpendsAttackerFuel() {
		War war = navalWar();
		commitAttackerPort(war);
		warManagerMock.when(() -> WarManager.getById(1)).thenReturn(war);

		assertTrue(AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(war, 20));
		assertEquals(3, war.getInitiativeAttacker());
		assertEquals(CampaignCoalition.AGGRESSOR, war.getLastBattleOffensiveCoalition());
		assertTrue(war.getCampaignBattlesFought() >= 1);
	}

	@Test
	void applyIfAttackerHasNoBerthedNavy_skipsWhenShipBerthed() {
		War war = navalWar();
		commitAttackerPort(war);
		registry.register(shipAt("port-atk"));

		assertFalse(AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(war, 20));
		assertEquals(4, war.getInitiativeAttacker());
	}

	private War navalWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(20);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolder(BelligerentRole.ATTACKER);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		war.setBattleDay(LocalDate.of(2026, 8, 21));
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL, false, null, "port_zoc")));
		war.setCampaignScheduleIndex(0);
		return war;
	}

	private void commitAttackerPort(War war) {
		war.getBattleInstallationPicks().computeIfAbsent("atk", ignored -> new LinkedHashSet<>()).add("port-atk");
		war.setBattleInstallationPicksBattleDay(war.getBattleDay());
	}

	private static PlayerVehicleRecord shipAt(String installationId) {
		return new PlayerVehicleRecord(
				UUID.randomUUID(), "v-ship", "ironclad", OwnershipMode.INSTALLATION, installationId);
	}
}
