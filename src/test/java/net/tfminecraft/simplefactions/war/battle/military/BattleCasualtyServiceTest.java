package net.tfminecraft.simplefactions.war.battle.military;

import static net.tfminecraft.simplefactions.war.wounds.WoundFixture.regiment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.history.BattleResolution;
import net.tfminecraft.simplefactions.war.wounds.BattleForce;
import net.tfminecraft.simplefactions.war.wounds.TestForces;
import net.tfminecraft.simplefactions.war.wounds.WarLedger;
import net.tfminecraft.simplefactions.war.wounds.WoundFixture;

class BattleCasualtyServiceTest {
	private static final int PROVINCE_ID = 42;
	private WoundFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = new WoundFixture();
	}

	@AfterEach
	void tearDown() {
		fixture.close();
	}

	@Test
	void aFoughtBattleIsRecordedWithTheForcesItStartedWith() {
		Faction atk = fixture.faction("atk", regiment("professional", 10, 0));
		Faction def = fixture.faction("def", regiment("professional", 6, 0));
		War war = fixture.war(1, atk, def);
		Battle battle = battle(war, BattleType.FIELD, TestForces.regiments("atk", 10), TestForces.regiments("def", 6));

		BattleCasualtyTally tally = BattleCasualtyService.applyBattleCasualties(war, battle,
				Map.of(BattleTemplate.ATTACKER_SIDE, 12, BattleTemplate.DEFENDER_SIDE, 30), BattleResolution.FOUGHT);

		WarLedger.BattleEntry entry = war.getLedger().battle(1);
		assertEquals("b1", entry.battleId());
		assertEquals("fought", entry.resolution());
		assertEquals(PROVINCE_ID, entry.provinceId());
		assertEquals(12, entry.attacker().deaths());
		assertEquals(TestForces.regiments("atk", 10), entry.attacker().forces());
		assertEquals(List.of("atk attacker professional 2", "def defender professional 6"), lines(tally));
		assertEquals(10, atk.getMilitary().getRegiment("professional").getCurrentSlots(),
				"nothing is taken until the war ends");
	}

	@Test
	void inACounterPushTheBattleAttackersAreTheWarDefenders() {
		Faction atk = fixture.faction("atk", regiment("professional", 10, 0));
		Faction def = fixture.faction("def", regiment("professional", 6, 0));
		War war = fixture.war(2, atk, def);
		Battle battle = battle(war, BattleType.SIEGE, TestForces.regiments("def", 6), TestForces.regiments("atk", 10));
		when(battle.getOffensiveCoalition()).thenReturn(CampaignSideRole.DEFENDER);

		BattleCasualtyTally tally = BattleCasualtyService.applyBattleCasualties(war, battle,
				Map.of(BattleTemplate.ATTACKER_SIDE, 15), BattleResolution.FOUGHT);

		assertEquals(15, war.getLedger().battle(1).defender().deaths());
		assertEquals(0, war.getLedger().battle(1).attacker().deaths());
		assertEquals(List.of("def defender professional 3"), lines(tally));
	}

	@Test
	void anAutoresolveReadsTheForcesNowAndNoCasualtiesMeansNoDeaths() {
		War war = fixture.war(3, fixture.faction("atk"), fixture.faction("def"));
		Battle carrier = mock(Battle.class);
		when(carrier.getId()).thenReturn("autoresolve-3");
		when(carrier.getWarId()).thenReturn(3);
		when(carrier.getBattleType()).thenReturn(BattleType.FIELD);
		when(carrier.getProvinceId()).thenReturn(null);
		war.setScheduledBattleProvinceId(PROVINCE_ID);
		try (MockedStatic<BattleForces> forces = mockStatic(BattleForces.class)) {
			forces.when(() -> BattleForces.build(eq(war), eq(PROVINCE_ID), same(war.getAttackers()), eq(carrier), any()))
					.thenReturn(TestForces.regiments("atk", 3));
			forces.when(() -> BattleForces.build(eq(war), eq(PROVINCE_ID), same(war.getDefenders()), eq(carrier), any()))
					.thenReturn(List.<BattleForce>of());

			BattleCasualtyService.applyBattleCasualties(war, carrier, null, BattleResolution.AUTORESOLVE);
		}

		WarLedger.BattleEntry entry = war.getLedger().battle(1);
		assertEquals("autoresolve", entry.resolution());
		assertEquals(TestForces.regiments("atk", 3), entry.attacker().forces());
		assertEquals(0, entry.attacker().deaths());
	}

	@Test
	void onlyActiveWarFieldBattlesAndSiegesAreRecorded() {
		War war = fixture.war(4, fixture.faction("atk"), fixture.faction("def"));
		Battle raid = battle(war, BattleType.RAID, List.of(), List.of());
		Battle manual = battle(war, BattleType.FIELD, List.of(), List.of());
		when(manual.getWarId()).thenReturn(null);

		assertTrue(BattleCasualtyService.applyBattleCasualties(war, raid, Map.of(), BattleResolution.FOUGHT).losses().isEmpty());
		assertFalse(BattleCasualtyService.shouldApply(war, manual));
		assertFalse(BattleCasualtyService.shouldApply(war, null));
		assertFalse(BattleCasualtyService.shouldApply(null, raid));
		war.end(WarEndReason.ADMIN_END);
		assertFalse(BattleCasualtyService.shouldApply(war, battle(war, BattleType.SIEGE, List.of(), List.of())));
		assertTrue(war.getLedger().battles().isEmpty());
	}

	@Test
	void woundsAreAnnouncedToOnlineMembersOfBothSides() {
		Faction atk = fixture.faction("atk", regiment("professional", 10, 0));
		Faction def = fixture.faction("def", regiment("professional", 6, 0));
		when(atk.getMembers()).thenReturn(List.of("Online", "Offline"));
		when(def.getMembers()).thenReturn(List.of("Away"));
		War war = fixture.war(5, atk, def);
		Player online = mock(Player.class);
		when(online.isOnline()).thenReturn(true);
		Player away = mock(Player.class);
		Battle battle = battle(war, BattleType.FIELD, TestForces.regiments("atk", 10), TestForces.regiments("def", 6));

		try (MockedStatic<Members> members = mockStatic(Members.class)) {
			members.when(() -> Members.player("Online")).thenReturn(online);
			members.when(() -> Members.player("Away")).thenReturn(away);

			BattleCasualtyService.applyBattleCasualties(war, battle,
					Map.of(BattleTemplate.ATTACKER_SIDE, 5, BattleTemplate.DEFENDER_SIDE, 10), BattleResolution.FOUGHT);
			verify(online).sendMessage("§7Campaign battle wounds: attacker 1, defender 2 regiments."
					+ " Wounded regiments cannot fight and are lost when the war ends.");
			verify(away, never()).sendMessage(any(String.class));

			BattleCasualtyService.applyBattleCasualties(war, battle, Map.of(BattleTemplate.ATTACKER_SIDE, 1),
					BattleResolution.FOUGHT);
			verify(online).sendMessage(any(String.class));
		}
	}

	@Test
	void deathsTurnIntoSlotsAtTheConfiguredRate() {
		assertEquals(5, BattleCasualtyService.deathsPerRegimentLoss());
		assertEquals(0, BattleCasualtyService.regimentLossesForDeaths(0));
		assertEquals(0, BattleCasualtyService.regimentLossesForDeaths(4));
		assertEquals(2, BattleCasualtyService.regimentLossesForDeaths(11));
	}

	@Test
	void theTallySumsRepeatedWoundsOfOneRegimentAndKeepsItsName() {
		Faction faction = fixture.faction("atk", regiment("professional", 4, 0));
		var regiment = faction.getMilitary().getRegiment("professional");
		BattleCasualtyTally tally = BattleCasualtyTally.empty();

		tally.add(faction, "attacker", regiment, 1);
		tally.add(faction, "attacker", regiment, 2);
		tally.add(faction, "defender", regiment, 1);

		assertEquals(List.of("atk attacker professional 3", "atk defender professional 1"), lines(tally));
		assertEquals("Name professional", tally.losses().get(0).regimentName());
	}

	private static Battle battle(War war, BattleType type, List<BattleForce> attackerForces,
			List<BattleForce> defenderForces) {
		Battle battle = mock(Battle.class);
		when(battle.getId()).thenReturn("b1");
		when(battle.getWarId()).thenReturn(war.getId());
		when(battle.getProvinceId()).thenReturn(PROVINCE_ID);
		when(battle.getBattleType()).thenReturn(type);
		BattleSide attacker = mock(BattleSide.class);
		when(attacker.hasForces()).thenReturn(true);
		when(attacker.getForces()).thenReturn(attackerForces);
		BattleSide defender = mock(BattleSide.class);
		when(defender.hasForces()).thenReturn(true);
		when(defender.getForces()).thenReturn(defenderForces);
		when(battle.getSideById(BattleTemplate.ATTACKER_SIDE)).thenReturn(attacker);
		when(battle.getSideById(BattleTemplate.DEFENDER_SIDE)).thenReturn(defender);
		return battle;
	}

	private static List<String> lines(BattleCasualtyTally tally) {
		return tally.losses().stream()
				.map(loss -> loss.faction().getId() + " " + loss.side() + " " + loss.regimentId() + " " + loss.count())
				.toList();
	}
}
