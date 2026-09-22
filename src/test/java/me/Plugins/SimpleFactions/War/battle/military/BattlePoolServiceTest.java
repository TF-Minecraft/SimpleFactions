package me.Plugins.SimpleFactions.War.battle.military;


import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarCommitment;
import me.Plugins.SimpleFactions.War.commitment.WarCommitmentService;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.enums.FactionModifiers;

class BattlePoolServiceTest {
	private static final int PROVINCE_ID = 42;
	private List<War> savedWars;

	@BeforeEach
	void setUp() {
		savedWars = new ArrayList<>(WarManager.get());
		WarManager.get().clear();
	}

	@AfterEach
	void tearDown() {
		for (War war : new ArrayList<>(WarManager.get())) {
			WarCommitmentService.clearCommitments(war.getId());
		}
		WarManager.get().clear();
		WarManager.get().addAll(savedWars);
	}

	@Test
	void invasion_attackerOffensiveDefenderDefensive() {
		Faction attacker = fighter("atk", Map.of("professional", 10, "militia", 4));
		Faction defender = fighter("def", Map.of("professional", 8, "militia", 6));
		War war = baseWar(1, attacker, defender);
		war.setCampaignPhase(CampaignPhase.INVASION);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(defender);

			assertEquals(PoolMode.OFFENSIVE, BattlePoolService.resolvePoolMode(war, PROVINCE_ID, war.getAttackers()));
			assertEquals(PoolMode.DEFENSIVE, BattlePoolService.resolvePoolMode(war, PROVINCE_ID, war.getDefenders()));

			assertEquals(10, BattlePoolService.totalCommittedRegiments(war, PROVINCE_ID, war.getAttackers()));
			assertEquals(6, BattlePoolService.totalCommittedRegiments(war, PROVINCE_ID, war.getDefenders()));
		}
	}

	@Test
	void counterPush_poolsSwap() {
		Faction attacker = fighter("atk", Map.of("professional", 10, "militia", 4));
		Faction defender = fighter("def", Map.of("professional", 8, "militia", 6));
		War war = baseWar(2, attacker, defender);
		war.setCampaignPhase(CampaignPhase.COUNTER_PUSH);
		war.setInitiativeHolder(me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole.DEFENDER);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(attacker);

			assertEquals(PoolMode.DEFENSIVE, BattlePoolService.resolvePoolMode(war, PROVINCE_ID, war.getAttackers()));
			assertEquals(PoolMode.OFFENSIVE, BattlePoolService.resolvePoolMode(war, PROVINCE_ID, war.getDefenders()));

			assertEquals(4, BattlePoolService.totalCommittedRegiments(war, PROVINCE_ID, war.getAttackers()));
			assertEquals(8, BattlePoolService.totalCommittedRegiments(war, PROVINCE_ID, war.getDefenders()));
		}
	}

	@Test
	void militia_ownLandOnly() {
		Faction owner = fighter("owner", Map.of("militia", 5));
		Faction ally = fighter("ally", Map.of("militia", 7));
		War war = baseWar(3, owner, fighter("def", Map.of("professional", 1)));
		war.getAttackers().getMainParticipants().get(0).getAllies().put(ally, true);
		war.setCampaignPhase(CampaignPhase.INVASION);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(owner);

			Map<String, Map<String, Integer>> eligible = BattlePoolService.eligibleRegiments(
					war,
					PROVINCE_ID,
					war.getAttackers(),
					PoolMode.DEFENSIVE);

			assertEquals(5, eligible.get("owner").get(BattlePoolService.MILITIA_REGIMENT_ID));
			assertFalse(eligible.containsKey("ally"));
		}
	}

	@Test
	void militia_vassalLand_overlordExcluded() {
		Faction overlord = fighter("o", Map.of("militia", 9, "professional", 2));
		Faction vassal = fighter("v", Map.of("militia", 4, "professional", 1));
		Faction defender = fighter("def", Map.of("professional", 1));
		War war = baseWar(4, overlord, defender);
		war.getAttackers().getMainParticipants().get(0).getSubjects().add(vassal);
		war.setCampaignPhase(CampaignPhase.INVASION);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(vassal);

			Map<String, Map<String, Integer>> eligible = BattlePoolService.eligibleRegiments(
					war,
					PROVINCE_ID,
					war.getAttackers(),
					PoolMode.DEFENSIVE);

			assertEquals(4, eligible.get("v").get(BattlePoolService.MILITIA_REGIMENT_ID));
			assertFalse(eligible.getOrDefault("o", Map.of()).containsKey(BattlePoolService.MILITIA_REGIMENT_ID));
		}
	}

	@Test
	void levy_offensivePoolOnly() {
		Faction attacker = fighter("atk", Map.of("professional", 5));
		Faction defender = fighter("def", Map.of("professional", 1));
		War war = baseWar(5, attacker, defender);
		seedLevyRow(war, "atk", "subject", 12);

		assertEquals(17, BattlePoolService.totalCommittedRegiments(war, PROVINCE_ID, war.getAttackers(), PoolMode.OFFENSIVE));
		assertEquals(0, BattlePoolService.totalCommittedRegiments(war, PROVINCE_ID, war.getAttackers(), PoolMode.DEFENSIVE));
	}

	@Test
	void civilWar_allTypesBothPools_militiaNotOwnLandGated() {
		Faction attacker = fighter("atk", Map.of("professional", 10, "militia", 4));
		Faction defender = fighter("def", Map.of("professional", 8, "militia", 6));
		War war = baseWar(8, attacker, defender);
		war.setMovementId("mov-cw");
		war.setCampaignPhase(CampaignPhase.INVASION);
		seedLevyRow(war, "atk", "levySource", 3);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(defender);

			Map<String, Map<String, Integer>> offensive = BattlePoolService.eligibleRegiments(
					war,
					PROVINCE_ID,
					war.getAttackers(),
					PoolMode.OFFENSIVE);
			assertEquals(10, offensive.get("atk").get("professional"));
			assertEquals(4, offensive.get("atk").get(BattlePoolService.MILITIA_REGIMENT_ID));
			assertEquals(3, offensive.get("atk").get(WarCommitment.LEVY_REGIMENT_ID));

			Map<String, Map<String, Integer>> defensive = BattlePoolService.eligibleRegiments(
					war,
					PROVINCE_ID,
					war.getAttackers(),
					PoolMode.DEFENSIVE);
			assertEquals(10, defensive.get("atk").get("professional"));
			assertEquals(4, defensive.get("atk").get(BattlePoolService.MILITIA_REGIMENT_ID));
			assertEquals(3, defensive.get("atk").get(WarCommitment.LEVY_REGIMENT_ID));
		}
	}

	@Test
	void nestedLevy_holderOnSide() {
		Faction main = fighter("m", Map.of("professional", 1));
		Faction subject = fighter("v", Map.of("professional", 1));
		Faction defender = fighter("def", Map.of("professional", 1));
		War war = baseWar(6, main, defender);
		war.getAttackers().getMainParticipants().get(0).getSubjects().add(subject);
		seedLevyRow(war, "v", "v2", 4);
		seedLevyRow(war, "v", "v3", 2);

		Map<String, Map<String, Integer>> eligible = BattlePoolService.eligibleRegiments(
				war,
				PROVINCE_ID,
				war.getAttackers(),
				PoolMode.OFFENSIVE);

		assertEquals(6, eligible.get("v").get(WarCommitment.LEVY_REGIMENT_ID));
		assertEquals(1, eligible.get("m").get("professional"));
	}

	@Test
	void totalCommittedRegiments_sumsEligible() {
		Faction attacker = fighter("atk", Map.of("professional", 3, "militia", 2));
		Faction defender = fighter("def", Map.of("professional", 4, "militia", 5));
		War war = baseWar(7, attacker, defender);
		war.setCampaignPhase(CampaignPhase.INVASION);
		seedLevyRow(war, "atk", "levySource", 6);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(defender);

			PoolMode attackerMode = BattlePoolService.resolvePoolMode(war, PROVINCE_ID, war.getAttackers());
			Map<String, Map<String, Integer>> eligible =
					BattlePoolService.eligibleRegiments(war, PROVINCE_ID, war.getAttackers(), attackerMode);
			int summed = eligible.values().stream().flatMap(map -> map.values().stream()).mapToInt(Integer::intValue).sum();

			assertEquals(summed, BattlePoolService.totalCommittedRegiments(war, PROVINCE_ID, war.getAttackers()));
			assertEquals(9, summed);
		}
	}

	@Test
	void isMilitiaEligible_matchesDirectOwner() {
		Faction owner = fighter("owner", Map.of("militia", 1));
		Faction other = fighter("other", Map.of("militia", 1));

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(owner);
			assertTrue(BattlePoolService.isMilitiaEligible(owner, PROVINCE_ID));
			assertFalse(BattlePoolService.isMilitiaEligible(other, PROVINCE_ID));
		}
	}

	private static void seedLevyRow(War war, String holderId, String sourceId, int count) {
		appendLevyRowForTest(new WarCommitment(
				war.getId(),
				holderId,
				sourceId,
				WarCommitment.LEVY_REGIMENT_ID,
				count,
				Instant.now()));
	}

	private static void appendLevyRowForTest(WarCommitment row) {
		try {
			java.lang.reflect.Field field = WarCommitmentService.class.getDeclaredField("commitmentsByWar");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<Integer, List<WarCommitment>> store =
					(Map<Integer, List<WarCommitment>>) field.get(null);
			store.computeIfAbsent(row.warId(), ignored -> new ArrayList<>()).add(row);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static War baseWar(int id, Faction attacker, Faction defender) {
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		War war = new War(id, attacker, defender);
		war.setCampaignProvinces(List.of(PROVINCE_ID, 43, 44));
		war.setCursorIndex(0);
		return war;
	}

	private static Faction fighter(String id, Map<String, Integer> slots) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getMembers()).thenReturn(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"));
		FactionModifier levy = mock(FactionModifier.class);
		when(levy.getAmount()).thenReturn(100.0);
		when(faction.getModifier(FactionModifiers.LEVY)).thenReturn(levy);

		Military military = mock(Military.class);
		List<Regiment> regiments = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : slots.entrySet()) {
			Regiment regiment = mock(Regiment.class);
			when(regiment.getId()).thenReturn(entry.getKey());
			when(regiment.isLevy()).thenReturn(false);
			when(regiment.isOffensive()).thenReturn("professional".equals(entry.getKey()));
			when(regiment.getCurrentSlots()).thenReturn(entry.getValue());
			regiments.add(regiment);
		}
		when(military.getRegiments()).thenReturn(regiments);
		when(faction.getMilitary()).thenReturn(military);
		return faction;
	}
}
