package me.Plugins.SimpleFactions.War.campaign.progression.postbattle;



import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCapabilityService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignOffensiveForfeitService;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.enums.Terrain;

class CampaignOffensiveForfeitServiceTest {
	private Faction attacker;
	private Faction defender;
	private MockedStatic<Bukkit> bukkitMock;
	private MockedStatic<WarManager> warManagerMock;
	private MockedStatic<TitleManager> titleManagerMock;
	private SimpleFactions pluginBackup;

	@BeforeEach
	void setUp() {
		Cache.warFirstBattleAtBorder = true;
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getMembers()).thenReturn(List.of());
		when(defender.getMembers()).thenReturn(List.of());

		ProvinceManager pm = new ProvinceManager();
		pm.start(Map.of(20, new Province(20, Terrain.PLAINS.name(), 50, 200, 200)));
		pluginBackup = SimpleFactions.plugin;
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		SimpleFactions.plugin = plugin;

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
		SimpleFactions.plugin = pluginBackup;
	}

	@Test
	void applyIfBattleOffensiveCannotAttack_appliesOpponentWin() {
		War war = baseWar();
		warManagerMock.when(() -> WarManager.getById(1)).thenReturn(war);

		assertTrue(CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(war, 20));
		assertEquals(1, war.getCampaignBattlesFought());
		assertTrue(CampaignPostBattleChoiceService.needsAnyChoice(war)
				|| war.getPostBattleChoicePhase() != PostBattleChoicePhase.NONE);
	}

	@Test
	void applyIfBattleOffensiveCannotAttack_skipsWhenOffensiveCanAttack() {
		War war = baseWar();
		try (MockedStatic<CampaignCapabilityService> capability = mockStatic(CampaignCapabilityService.class)) {
			capability.when(() -> CampaignCapabilityService.battleOffensiveCoalition(war))
					.thenReturn(CampaignCoalition.AGGRESSOR);
			capability.when(() -> CampaignCapabilityService.canAttack(war, CampaignCoalition.AGGRESSOR))
					.thenReturn(true);
			assertFalse(CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(war, 20));
		}
	}

	private War baseWar() {
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
		return war;
	}
}
