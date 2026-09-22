package me.Plugins.SimpleFactions.War.battle.campaign.warband;


import me.Plugins.SimpleFactions.War.battle.campaign.warband.BattleWarbandRetreatService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleEndSupport;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleEndReason;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.battle.campaign.warband.BattleWarbandRetreatService.RetreatResult;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;

class BattleWarbandRetreatServiceTest {
	private static final int PROVINCE_ID = 20;
	private static final Instant NOW = Instant.parse("2026-08-21T20:00:00Z");

	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		Cache.battleRetreatMinElapsedSeconds = 1200;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
	}

	private record RetreatFixture(
			War war,
			Battle battle,
			Warband retreatingWarband,
			Player leader,
			Instant now) {
	}

	private RetreatFixture eligibleContext(BattleType battleType, String retreatingSide) {
		UUID leaderId = UUID.randomUUID();
		UUID opponentLeaderId = UUID.randomUUID();
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(PROVINCE_ID);

		Warband retreatingWarband = Warband.createWithMemberIds("retreat_band", leaderId, true);
		Warband opponentWarband = Warband.createWithMemberIds("opponent_band", opponentLeaderId, true);

		BossBar bossBar = mock(BossBar.class);
		Battle battle;
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(any(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(any(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);

			battle = BattleFactory.createBlank(battleType, "campaign_w1");
			battle.setWarId(1);
			battle.setProvinceId(PROVINCE_ID);
			battle.setStarted(true);
			battle.setStartedAt(NOW.minusSeconds(1200));
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(
					BattleTemplate.ATTACKER_SIDE.equalsIgnoreCase(retreatingSide) ? retreatingWarband : opponentWarband);
			battle.getSideById(BattleTemplate.DEFENDER_SIDE).addBand(
					BattleTemplate.DEFENDER_SIDE.equalsIgnoreCase(retreatingSide) ? retreatingWarband : opponentWarband);
			BattleManager.addBattle(battle);
		}

		WarbandManager.addWarband(retreatingWarband);
		WarbandManager.addWarband(opponentWarband);

		Player leader = mock(Player.class);
		when(leader.getUniqueId()).thenReturn(leaderId);

		return new RetreatFixture(war, battle, retreatingWarband, leader, NOW);
	}

	private void withWarManager(War war, Runnable action) {
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			action.run();
		}
	}

	@Test
	void opponentSideId_attackerReturnsDefender() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		assertEquals(
				BattleTemplate.DEFENDER_SIDE,
				BattleWarbandRetreatService.opponentSideId(fixture.battle(), BattleTemplate.ATTACKER_SIDE));
	}

	@Test
	void opponentSideId_defenderReturnsAttacker() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.DEFENDER_SIDE);
		assertEquals(
				BattleTemplate.ATTACKER_SIDE,
				BattleWarbandRetreatService.opponentSideId(fixture.battle(), BattleTemplate.DEFENDER_SIDE));
	}

	@Test
	void opponentSideId_unknownSideReturnsNull() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		assertNull(BattleWarbandRetreatService.opponentSideId(fixture.battle(), "unknown"));
	}

	@Test
	void opponentSideId_missingOpponentSideReturnsNull() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.battle().removeSide(BattleTemplate.DEFENDER_SIDE);
		assertNull(BattleWarbandRetreatService.opponentSideId(
				fixture.battle(), BattleTemplate.ATTACKER_SIDE));
	}

	@Test
	void remainingSeconds_beforeCooldown() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.battle().setStartedAt(NOW.minusSeconds(600));
		assertEquals(600L, BattleWarbandRetreatService.remainingSecondsUntilRetreat(fixture.battle(), NOW));
	}

	@Test
	void remainingSeconds_afterCooldown() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.battle().setStartedAt(NOW.minusSeconds(1300));
		assertEquals(0L, BattleWarbandRetreatService.remainingSecondsUntilRetreat(fixture.battle(), NOW));
	}

	@Test
	void remainingSeconds_nullStartedAt() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.battle().setStartedAt(null);
		assertEquals(1200L, BattleWarbandRetreatService.remainingSecondsUntilRetreat(fixture.battle(), NOW));
	}

	@Test
	void rejection_nullPlayer() {
		assertEquals(RetreatResult.REJECTED_NOT_IN_WARBAND, BattleWarbandRetreatService.retreatRejection(null, NOW));
	}

	@Test
	void rejection_notInWarband() {
		Player stranger = mock(Player.class);
		when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());
		assertEquals(RetreatResult.REJECTED_NOT_IN_WARBAND,
				BattleWarbandRetreatService.retreatRejection(stranger, NOW));
	}

	@Test
	void rejection_memberNotLeader() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		UUID memberId = UUID.randomUUID();
		fixture.retreatingWarband().addMember(memberId);
		Player member = mock(Player.class);
		when(member.getUniqueId()).thenReturn(memberId);

		withWarManager(fixture.war(), () -> assertEquals(
				RetreatResult.REJECTED_NOT_LEADER,
				BattleWarbandRetreatService.retreatRejection(member, fixture.now())));
	}

	@Test
	void rejection_pendingLeader() {
		War war = new War(1, attacker, defender);
		Warband shell = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
		Player pendingLeader = mock(Player.class);
		when(pendingLeader.getUniqueId()).thenReturn(shell.getLeaderId());
		WarbandManager.addWarband(shell);

		withWarManager(war, () -> assertEquals(
				RetreatResult.REJECTED_PENDING_LEADER,
				BattleWarbandRetreatService.retreatRejection(pendingLeader, NOW)));
	}

	@Test
	void rejection_notInBattle() {
		UUID leaderId = UUID.randomUUID();
		Warband orphan = Warband.createWithMemberIds("orphan_band", leaderId, true);
		WarbandManager.addWarband(orphan);
		Player leader = mock(Player.class);
		when(leader.getUniqueId()).thenReturn(leaderId);

		assertEquals(RetreatResult.REJECTED_NOT_IN_BATTLE,
				BattleWarbandRetreatService.retreatRejection(leader, NOW));
	}

	@Test
	void rejection_warInactive() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.war().end(WarEndReason.WHITE_PEACE);

		withWarManager(fixture.war(), () -> assertEquals(
				RetreatResult.REJECTED_NOT_IN_BATTLE,
				BattleWarbandRetreatService.retreatRejection(fixture.leader(), fixture.now())));
	}

	@Test
	void rejection_battleNotStarted() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.battle().setStarted(false);

		withWarManager(fixture.war(), () -> assertEquals(
				RetreatResult.REJECTED_BATTLE_NOT_STARTED,
				BattleWarbandRetreatService.retreatRejection(fixture.leader(), fixture.now())));
	}

	@Test
	void rejection_manualBattle() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.battle().setWarId(null);

		withWarManager(fixture.war(), () -> assertEquals(
				RetreatResult.REJECTED_NOT_IN_BATTLE,
				BattleWarbandRetreatService.retreatRejection(fixture.leader(), fixture.now())));
	}

	@Test
	void rejection_campaignRaid() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.battle().setCampaignRaid(true);

		withWarManager(fixture.war(), () -> assertEquals(
				RetreatResult.REJECTED_RAID,
				BattleWarbandRetreatService.retreatRejection(fixture.leader(), fixture.now())));
	}

	@Test
	void rejection_wrongBattleType() {
		RetreatFixture fixture = eligibleContext(BattleType.RAID, BattleTemplate.ATTACKER_SIDE);

		withWarManager(fixture.war(), () -> assertEquals(
				RetreatResult.REJECTED_RAID,
				BattleWarbandRetreatService.retreatRejection(fixture.leader(), fixture.now())));
	}

	@Test
	void rejection_tooEarly() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.battle().setStartedAt(fixture.now().minusSeconds(60));

		withWarManager(fixture.war(), () -> assertEquals(
				RetreatResult.REJECTED_TOO_EARLY,
				BattleWarbandRetreatService.retreatRejection(fixture.leader(), fixture.now())));
	}

	@Test
	void rejection_nullStartedAt() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.battle().setStartedAt(null);

		withWarManager(fixture.war(), () -> assertEquals(
				RetreatResult.REJECTED_TOO_EARLY,
				BattleWarbandRetreatService.retreatRejection(fixture.leader(), fixture.now())));
	}

	@Test
	void rejection_noOpponent() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.battle().removeSide(BattleTemplate.DEFENDER_SIDE);

		withWarManager(fixture.war(), () -> assertEquals(
				RetreatResult.REJECTED_NO_OPPONENT,
				BattleWarbandRetreatService.retreatRejection(fixture.leader(), fixture.now())));
	}

	@Test
	void canRetreat_eligibleFieldBattle() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);

		withWarManager(fixture.war(), () -> {
			assertNull(BattleWarbandRetreatService.retreatRejection(fixture.leader(), fixture.now()));
			assertTrue(BattleWarbandRetreatService.canRetreat(fixture.leader(), fixture.now()));
		});
	}

	@Test
	void canRetreat_eligibleSiegeBattle() {
		RetreatFixture fixture = eligibleContext(BattleType.SIEGE, BattleTemplate.ATTACKER_SIDE);

		withWarManager(fixture.war(), () -> {
			assertNull(BattleWarbandRetreatService.retreatRejection(fixture.leader(), fixture.now()));
			assertTrue(BattleWarbandRetreatService.canRetreat(fixture.leader(), fixture.now()));
		});
	}

	@Test
	void retreat_successEndsBattleAsRetreat() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);

		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
			wars.when(() -> WarManager.getById(1)).thenReturn(fixture.war());

			RetreatResult result = BattleWarbandRetreatService.retreat(fixture.leader(), fixture.now());

			assertEquals(RetreatResult.SUCCESS, result);
			end.verify(() -> BattleEndSupport.endBattle(
					eq(fixture.battle()),
					eq(BattleTemplate.DEFENDER_SIDE),
					eq(BattleEndReason.RETREAT)));
		}
	}

	@Test
	void retreat_defenderRetreatsOpponentWins() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.DEFENDER_SIDE);

		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
			wars.when(() -> WarManager.getById(1)).thenReturn(fixture.war());

			RetreatResult result = BattleWarbandRetreatService.retreat(fixture.leader(), fixture.now());

			assertEquals(RetreatResult.SUCCESS, result);
			end.verify(() -> BattleEndSupport.endBattle(
					eq(fixture.battle()),
					eq(BattleTemplate.ATTACKER_SIDE),
					eq(BattleEndReason.RETREAT)));
		}
	}

	@Test
	void retreat_rejectedDoesNotEndBattle() {
		RetreatFixture fixture = eligibleContext(BattleType.FIELD, BattleTemplate.ATTACKER_SIDE);
		fixture.battle().setStartedAt(fixture.now().minusSeconds(60));

		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
			wars.when(() -> WarManager.getById(1)).thenReturn(fixture.war());

			RetreatResult result = BattleWarbandRetreatService.retreat(fixture.leader(), fixture.now());

			assertEquals(RetreatResult.REJECTED_TOO_EARLY, result);
			end.verify(() -> BattleEndSupport.endBattle(any(), any(), any()), never());
		}
	}
}
