package net.tfminecraft.simplefactions.war.campaign.progression;



import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
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

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.ObjectiveHolder;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

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
