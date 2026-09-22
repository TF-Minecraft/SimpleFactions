package me.Plugins.SimpleFactions.War.battle.military;


import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.bukkit.Bukkit;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarCommitment;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.commitment.WarCommitmentService;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.enums.FactionModifiers;

import java.time.Instant;

class BattleCasualtyServiceTest {
	private static final int PROVINCE_ID = 42;
	private List<War> savedWars;
	private Map<String, Faction> factionsById;
	private MockedStatic<Bukkit> bukkitMock;
	private MockedStatic<WarManager> warManagerMock;

	@BeforeEach
	void setUp() {
		savedWars = new ArrayList<>(WarManager.get());
		WarManager.get().clear();
		factionsById = new HashMap<>();
		bukkitMock = mockStatic(Bukkit.class);
		bukkitMock.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);
		warManagerMock = mockStatic(WarManager.class, CALLS_REAL_METHODS);
		warManagerMock.when(() -> WarManager.persist(any())).then(inv -> null);
	}

	@AfterEach
	void tearDown() {
		warManagerMock.close();
		bukkitMock.close();
		for (War war : new ArrayList<>(WarManager.get())) {
			WarCommitmentService.clearCommitments(war.getId());
		}
		WarManager.get().clear();
		WarManager.get().addAll(savedWars);
	}

	@Test
	void militiaDebitedFirst_onOwnLand() {
		Faction attacker = fighter("atk", Map.of("professional", 10));
		Faction defender = fighter("def", Map.of("militia", 6, "garrison", 10));
		War war = baseWar(1, attacker, defender);
		war.setCampaignPhase(CampaignPhase.INVASION);
		seedOwnRow(war, "def", "militia", 6);
		seedOwnRow(war, "def", "garrison", 10);

		Battle battle = campaignBattle(war, PROVINCE_ID);
		Map<String, Integer> casualties = Map.of(BattleTemplate.DEFENDER_SIDE, 8);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(defender);
			stubFactions(factions);

			BattleCasualtyService.applyBattleCasualties(war, battle, casualties);

			assertEquals(0, commitmentCount(war.getId(), "def", null, "militia"));
			assertEquals(8, commitmentCount(war.getId(), "def", null, "garrison"));
			assertEquals(0, liveSlots("def", "militia"));
			assertEquals(8, liveSlots("def", "garrison"));
		}
	}

	@Test
	void proportionalAcrossContributors() {
		Faction attacker = fighter("atk", Map.of("professional", 10));
		Faction ally = fighter("ally", Map.of("professional", 5));
		Faction defender = fighter("def", Map.of("professional", 1));
		War war = baseWar(2, attacker, defender);
		war.getAttackers().getMainParticipants().get(0).getAllies().put(ally, true);
		war.setCampaignPhase(CampaignPhase.INVASION);
		seedOwnRow(war, "atk", "professional", 10);
		seedOwnRow(war, "ally", "professional", 5);

		Battle battle = campaignBattle(war, PROVINCE_ID);
		Map<String, Integer> casualties = Map.of(BattleTemplate.ATTACKER_SIDE, 3);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(defender);
			stubFactions(factions);

			BattleCasualtyService.applyBattleCasualties(war, battle, casualties);

			assertEquals(8, commitmentCount(war.getId(), "atk", null, "professional"));
			assertEquals(4, commitmentCount(war.getId(), "ally", null, "professional"));
			assertEquals(8, liveSlots("atk", "professional"));
			assertEquals(4, liveSlots("ally", "professional"));
		}
	}

	@Test
	void overlordMilitiaNotDebited_inVassalLand() {
		Faction overlord = fighter("o", Map.of("militia", 9, "professional", 2));
		Faction vassal = fighter("v", Map.of("militia", 4, "garrison", 1));
		Faction defender = fighter("def", Map.of("professional", 1));
		War war = baseWar(3, overlord, defender);
		war.getAttackers().getMainParticipants().get(0).getSubjects().add(vassal);
		war.setCampaignPhase(CampaignPhase.COUNTER_PUSH);
		war.setInitiativeHolder(me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole.DEFENDER);
		seedOwnRow(war, "o", "militia", 9);
		seedOwnRow(war, "o", "professional", 2);
		seedOwnRow(war, "v", "militia", 4);
		seedOwnRow(war, "v", "garrison", 1);

		Battle battle = campaignBattle(war, PROVINCE_ID);
		Map<String, Integer> casualties = Map.of(BattleTemplate.ATTACKER_SIDE, 4);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(vassal);
			stubFactions(factions);

			BattleCasualtyService.applyBattleCasualties(war, battle, casualties);

			assertEquals(9, commitmentCount(war.getId(), "o", null, "militia"));
			assertEquals(0, commitmentCount(war.getId(), "v", null, "militia"));
			assertEquals(0, liveSlots("v", "militia"));
			assertEquals(9, liveSlots("o", "militia"));
		}
	}

	@Test
	void noWinnerStillApplies() {
		Faction attacker = fighter("atk", Map.of("professional", 5));
		Faction defender = fighter("def", Map.of("garrison", 4));
		War war = baseWar(4, attacker, defender);
		war.setCampaignPhase(CampaignPhase.INVASION);
		seedOwnRow(war, "atk", "professional", 5);
		seedOwnRow(war, "def", "garrison", 4);

		Battle battle = campaignBattle(war, PROVINCE_ID);
		Map<String, Integer> casualties = Map.of(
				BattleTemplate.ATTACKER_SIDE, 2,
				BattleTemplate.DEFENDER_SIDE, 1);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(defender);
			stubFactions(factions);

			BattleCasualtyService.applyBattleCasualties(war, battle, casualties);

			assertEquals(3, commitmentCount(war.getId(), "atk", null, "professional"));
			assertEquals(3, commitmentCount(war.getId(), "def", null, "garrison"));
		}
	}

	@Test
	void skipsManualBattle() {
		Faction attacker = fighter("atk", Map.of("professional", 5));
		Faction defender = fighter("def", Map.of("professional", 4));
		War war = baseWar(5, attacker, defender);
		seedOwnRow(war, "atk", "professional", 5);

		Battle battle = mock(Battle.class);
		when(battle.getWarId()).thenReturn(null);
		when(battle.getBattleType()).thenReturn(BattleType.FIELD);
		assertFalse(BattleCasualtyService.shouldApply(war, battle, Map.of(BattleTemplate.ATTACKER_SIDE, 3)));
		assertEquals(5, commitmentCount(war.getId(), "atk", null, "professional"));
	}

	@Test
	void levyDebitsSourceSlots() {
		Faction attacker = fighter("atk", Map.of());
		Faction source = fighter("src", Map.of("professional", 8));
		Faction defender = fighter("def", Map.of("professional", 1));
		Regiment sourceRegiment = source.getMilitary().getRegiment("professional");
		AtomicInteger sent = new AtomicInteger(8);
		when(sourceRegiment.sentToOverlord()).thenAnswer(inv -> sent.get());
		doAnswer(inv -> {
			sent.set(inv.getArgument(0, Integer.class));
			return null;
		}).when(sourceRegiment).setSentToOverlord(org.mockito.ArgumentMatchers.anyInt());
		War war = baseWar(6, attacker, defender);
		war.setCampaignPhase(CampaignPhase.INVASION);
		seedOwnRow(war, "src", "professional", 8);
		seedLevyRow(war, "atk", "src", 6);

		Battle battle = campaignBattle(war, PROVINCE_ID);
		Map<String, Integer> casualties = Map.of(BattleTemplate.ATTACKER_SIDE, 3);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(defender);
			stubFactions(factions);

			BattleCasualtyService.applyBattleCasualties(war, battle, casualties);

			assertEquals(3, commitmentCount(war.getId(), "atk", "src", WarCommitment.LEVY_REGIMENT_ID));
			assertEquals(5, commitmentCount(war.getId(), "src", null, "professional"));
			assertEquals(5, liveSlots("src", "professional"));
		}
	}

	private static Battle campaignBattle(War war, int provinceId) {
		Battle battle = mock(Battle.class);
		when(battle.getWarId()).thenReturn(war.getId());
		when(battle.getProvinceId()).thenReturn(provinceId);
		when(battle.getBattleType()).thenReturn(BattleType.FIELD);
		return battle;
	}

	private void stubFactions(MockedStatic<FactionManager> factions) {
		factions.when(() -> FactionManager.getByString(anyString()))
				.thenAnswer(inv -> factionsById.get(inv.getArgument(0, String.class).toLowerCase()));
	}

	private static void seedOwnRow(War war, String factionId, String regimentId, int count) {
		appendRowForTest(new WarCommitment(
				war.getId(),
				factionId,
				null,
				regimentId,
				count,
				Instant.now()));
	}

	private static void seedLevyRow(War war, String holderId, String sourceId, int count) {
		appendRowForTest(new WarCommitment(
				war.getId(),
				holderId,
				sourceId,
				WarCommitment.LEVY_REGIMENT_ID,
				count,
				Instant.now()));
	}

	private static void appendRowForTest(WarCommitment row) {
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

	private static int commitmentCount(int warId, String factionId, String sourceId, String regimentId) {
		return BattleCasualtyService.getCommitmentCount(warId, factionId, sourceId, regimentId);
	}

	private int liveSlots(String factionId, String regimentId) {
		Faction faction = factionsById.get(factionId.toLowerCase());
		if (faction == null || faction.getMilitary() == null) {
			return 0;
		}
		Regiment regiment = faction.getMilitary().getRegiment(regimentId);
		return regiment != null ? regiment.getCurrentSlots() : 0;
	}

	private static War baseWar(int id, Faction attacker, Faction defender) {
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		War war = new War(id, attacker, defender);
		war.setCampaignProvinces(List.of(PROVINCE_ID, 43, 44));
		war.setCursorIndex(0);
		return war;
	}

	private Faction fighter(String id, Map<String, Integer> slots) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getMembers()).thenReturn(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"));
		FactionModifier levy = mock(FactionModifier.class);
		when(levy.getAmount()).thenReturn(100.0);
		when(faction.getModifier(FactionModifiers.LEVY)).thenReturn(levy);

		Military military = mock(Military.class);
		Map<String, Regiment> regimentById = new HashMap<>();
		List<Regiment> regiments = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : slots.entrySet()) {
			AtomicInteger currentSlots = new AtomicInteger(entry.getValue());
			Regiment regiment = mock(Regiment.class);
			when(regiment.getId()).thenReturn(entry.getKey());
			when(regiment.isLevy()).thenReturn(false);
			when(regiment.isOffensive()).thenReturn("professional".equals(entry.getKey()));
			when(regiment.getCurrentSlots()).thenAnswer(inv -> currentSlots.get());
			doAnswer(inv -> {
				currentSlots.updateAndGet(value -> Math.max(0, value - 1));
				return null;
			}).when(regiment).sizeDecrease();
			regimentById.put(entry.getKey().toLowerCase(), regiment);
			regiments.add(regiment);
		}
		when(military.getRegiments()).thenReturn(regiments);
		when(military.getRegiment(anyString())).thenAnswer(inv -> {
			String regimentId = inv.getArgument(0, String.class);
			return regimentById.get(regimentId.toLowerCase());
		});
		when(faction.getMilitary()).thenReturn(military);
		factionsById.put(id.toLowerCase(), faction);
		return faction;
	}
}
