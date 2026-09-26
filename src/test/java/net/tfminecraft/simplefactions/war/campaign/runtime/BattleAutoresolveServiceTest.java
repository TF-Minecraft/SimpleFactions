package net.tfminecraft.simplefactions.war.campaign.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleSides;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyService;
import net.tfminecraft.simplefactions.war.battle.military.BattlePoolService;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.campaign.progression.AttackerNavalContestService;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCapabilityService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleAutoresolveService.Prediction;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;

class BattleAutoresolveServiceTest {
	private Faction attacker;
	private Faction defender;
	private Player player;
	private SimpleFactions pluginBackup;
	private MockedStatic<Bukkit> bukkit;
	private MockedStatic<WarManager> warManager;
	private MockedStatic<TitleManager> titles;
	private MockedStatic<CampaignCapabilityService> capability;
	private MockedStatic<BattlePoolService> pools;
	private MockedStatic<BattleCasualtyService> casualties;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		BattleAutoresolveService.setRandomForTests(null);
		Cache.warBattleLivesPerRegiment = 5;
		Cache.warBattleDeathsPerRegimentLoss = 5;
		Cache.warAutoresolveLuck = 0;
		Cache.warAutoresolveLoserLossFraction = 0.5;
		Cache.warFirstBattleAtBorder = true;
		Cache.warBattleVotingMaxPostponements = 1;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("Attackers");
		when(defender.getName()).thenReturn("Defenders");
		when(attacker.getMembers()).thenReturn(List.of("Alice"));
		when(defender.getMembers()).thenReturn(List.of("Alice"));

		player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		close(casualties);
		close(pools);
		close(capability);
		close(titles);
		close(warManager);
		close(bukkit);
		SimpleFactions.plugin = pluginBackup;
		BattleAutoresolveService.setRandomForTests(null);
	}

	@Test
	void predict_fiftyVsThirty_noLuck_matchesLanchesterExample() {
		Prediction prediction = BattleAutoresolveService.predict(50, 30, 0, 0.5, explodingRandom());

		assertTrue(prediction.offensiveWins());
		assertEquals(10, prediction.offensiveDeaths());
		assertEquals(15, prediction.defensiveDeaths());
		assertEquals(2, prediction.offensiveRegimentLosses());
		assertEquals(3, prediction.defensiveRegimentLosses());
	}

	@Test
	void predict_zeroStrength_otherSideWinsWithNoLosses() {
		Prediction defendersWin = BattleAutoresolveService.predict(0, 40, 0.15, 0.5, explodingRandom());
		assertFalse(defendersWin.offensiveWins());
		assertEquals(0, defendersWin.offensiveDeaths());
		assertEquals(0, defendersWin.defensiveDeaths());

		Prediction attackersWin = BattleAutoresolveService.predict(40, 0, 0.15, 0.5, explodingRandom());
		assertTrue(attackersWin.offensiveWins());
		assertEquals(0, attackersWin.offensiveRegimentLosses());
		assertEquals(0, attackersWin.defensiveRegimentLosses());
	}

	@Test
	void predict_bothZero_tieGoesToDefender() {
		Prediction prediction = BattleAutoresolveService.predict(0, 0, 0, 0.5, explodingRandom());
		assertFalse(prediction.offensiveWins());
		assertEquals(0, prediction.offensiveDeaths());
		assertEquals(0, prediction.defensiveDeaths());
	}

	@Test
	void predict_tieGoesToDefenderAndCapsWinnerDeaths() {
		Prediction tied = BattleAutoresolveService.predict(50, 50, 0, 0.5, explodingRandom());
		assertFalse(tied.offensiveWins());
		assertEquals(25, tied.offensiveDeaths());
		assertEquals(50, tied.defensiveDeaths());

		Prediction capped = BattleAutoresolveService.predict(50, 50, 0.15, 0.5, rolls(1.0, 1.0));
		assertFalse(capped.offensiveWins());
		assertEquals(25, capped.offensiveDeaths());
		assertEquals(50, capped.defensiveDeaths());
		assertEquals(5, capped.offensiveRegimentLosses());
		assertEquals(10, capped.defensiveRegimentLosses());
	}

	@Test
	void predict_luckCanFlipTheWinner() {
		Prediction prediction = BattleAutoresolveService.predict(30, 30, 0.15, 0.5, rolls(0.0, 1.0));

		assertFalse(prediction.offensiveWins());
		assertEquals(15, prediction.offensiveDeaths());
		assertEquals(11, prediction.defensiveDeaths());
	}

	@Test
	void resolve_appliesLossesThroughCampaignOutcome() {
		War war = votingWar();
		openHarness(war, war.getAttackers());

		assertTrue(BattleAutoresolveService.resolve(war, explodingRandom()));

		Map<String, Integer> deaths = capturedDeaths();
		assertEquals(10, deaths.get(BattleTemplate.ATTACKER_SIDE));
		assertEquals(15, deaths.get(BattleTemplate.DEFENDER_SIDE));
		Battle carrier = capturedCarrier();
		assertEquals(CampaignCoalition.AGGRESSOR, carrier.getOffensiveCoalition());
		assertFalse(carrier.hasStarted());
		assertNull(BattleManager.getByString(carrier.getId()));
		assertEquals(1, war.getCampaignBattlesFought());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertEquals(0, war.getPostponementsThisCycle());
		assertFalse(war.isAutoresolveProposedByAttacker());
		assertMessageContains("Autoresolved", "Attackers", "attacker 2", "defender 3");
	}

	@Test
	void resolve_counterPush_debitsTheOffensiveCoalition() {
		War war = votingWar();
		war.setInitiativeHolder(BelligerentRole.DEFENDER);
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		openHarness(war, war.getDefenders());

		assertTrue(BattleAutoresolveService.resolve(war, explodingRandom()));

		Battle carrier = capturedCarrier();
		assertEquals(CampaignCoalition.DEFENDER, carrier.getOffensiveCoalition());
		assertEquals(war.getDefenders(), CampaignBattleSides.warSideFor(war, carrier, BattleTemplate.ATTACKER_SIDE));
		assertEquals(10, capturedDeaths().get(BattleTemplate.ATTACKER_SIDE));
		assertEquals(15, capturedDeaths().get(BattleTemplate.DEFENDER_SIDE));
		assertEquals(CampaignCoalition.DEFENDER, war.getLastBattleOffensiveCoalition());
		assertEquals(CampaignCoalition.DEFENDER, war.getPostBattleWinnerCoalition());
		assertMessageContains("Defenders", "attacker 2", "defender 3");
	}

	@Test
	void resolve_navalAutoLossRunsBeforeSimulation() {
		War war = votingWar();
		war.setPostponementsThisCycle(2);
		war.setAutoresolveProposedByAttacker(true);

		try (MockedStatic<AttackerNavalContestService> naval = mockStatic(AttackerNavalContestService.class);
				MockedStatic<BattleCasualtyService> casualty = mockStatic(BattleCasualtyService.class);
				MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			naval.when(() -> AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(any(), anyInt()))
					.thenReturn(true);

			assertTrue(BattleAutoresolveService.resolve(war, explodingRandom()));

			assertEquals(0, war.getPostponementsThisCycle());
			assertFalse(war.isAutoresolveProposedByAttacker());
			assertEquals(0, war.getCampaignBattlesFought());
			casualty.verify(
					() -> BattleCasualtyService.applyBattleCasualties(any(), any(), any()),
					never());
			pool.verify(
					() -> BattlePoolService.totalCommittedRegiments(any(), anyInt(), any()),
					never());
		}
	}

	@Test
	void resolve_returnsFalseWhenNoProvince() {
		War war = votingWar();
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleChoiceResolved(false);

		assertFalse(BattleAutoresolveService.resolve(war, explodingRandom()));
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
	}

	private void openHarness(War war, Side heavySide) {
		ProvinceManager provinces = new ProvinceManager();
		provinces.start(Map.of(20, new Province(20, Terrain.PLAINS.name(), 50, 200, 200)));
		pluginBackup = SimpleFactions.plugin;
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(provinces);
		SimpleFactions.plugin = plugin;

		titles = mockStatic(TitleManager.class);
		titles.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);

		BossBar bossBar = mock(BossBar.class);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
				.thenReturn(bossBar);
		bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
				.thenReturn(bossBar);
		bukkit.when(() -> Bukkit.getPlayerExact("Alice")).thenReturn(player);

		warManager = mockStatic(WarManager.class);
		warManager.when(() -> WarManager.persist(any())).then(invocation -> null);
		warManager.when(() -> WarManager.getById(war.getId())).thenReturn(war);

		capability = mockStatic(CampaignCapabilityService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
		capability.when(() -> CampaignCapabilityService.canMountOffensiveAfterPush(any(), any())).thenReturn(true);

		AtomicReference<Battle> carrier = new AtomicReference<>();
		AtomicReference<Map<String, Integer>> deaths = new AtomicReference<>();
		pools = mockStatic(BattlePoolService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
		pools.when(() -> BattlePoolService.totalCommittedRegiments(any(), anyInt(), any()))
				.thenAnswer(invocation -> invocation.getArgument(2) == heavySide ? 10 : 6);
		casualties = mockStatic(BattleCasualtyService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
		casualties.when(() -> BattleCasualtyService.applyBattleCasualties(any(), any(), any()))
				.thenAnswer(invocation -> {
					carrier.set(invocation.getArgument(1));
					@SuppressWarnings("unchecked")
					Map<String, Integer> sideDeaths = invocation.getArgument(2);
					deaths.set(sideDeaths);
					return null;
				});
		this.carrier = carrier;
		this.deaths = deaths;
	}

	private AtomicReference<Battle> carrier;
	private AtomicReference<Map<String, Integer>> deaths;

	private Battle capturedCarrier() {
		return carrier.get();
	}

	private Map<String, Integer> capturedDeaths() {
		return deaths.get();
	}

	private void assertMessageContains(String... parts) {
		ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
		verify(player, atLeastOnce()).sendMessage(messages.capture());
		String combined = String.join("\n", messages.getAllValues());
		for (String part : parts) {
			assertTrue(combined.contains(part), combined);
		}
	}

	private War votingWar() {
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
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setPostponementsThisCycle(2);
		war.setAutoresolveProposedByAttacker(true);
		war.setAutoresolveProposedByDefender(true);
		return war;
	}

	private static Random explodingRandom() {
		return new Random() {
			@Override
			public double nextDouble() {
				throw new AssertionError("luck 0 must not roll");
			}
		};
	}

	private static Random rolls(double first, double second) {
		return new Random() {
			private int index;

			@Override
			public double nextDouble() {
				double value = index == 0 ? first : second;
				index++;
				return value;
			}
		};
	}

	private static void close(MockedStatic<?> mocked) {
		if (mocked != null) {
			mocked.close();
		}
	}
}
