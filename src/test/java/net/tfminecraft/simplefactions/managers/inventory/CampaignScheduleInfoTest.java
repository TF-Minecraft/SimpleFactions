package net.tfminecraft.simplefactions.managers.inventory;


import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.enums.SFGUI;

class CampaignScheduleInfoTest {
	private Faction attacker;
	private Faction defender;
	private UUID attackerVoter;

	@BeforeEach
	void setUp() {
		Cache.warBattleWindowStartHour = 20;
		Cache.warBattleWindowEndHour = 24;
		Cache.warVoteCloseHour = 16;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("AttackerRealm");
		when(defender.getName()).thenReturn("DefenderRealm");
		attackerVoter = UUID.randomUUID();
	}

	@Test
	void buildScheduleInfoLines_includesBattleDayVoteCloseAndSelections() {
		War war = votingWar();
		war.getBattleVotes().put(attackerVoter, Set.of(21, 22));

		List<String> lines = CampaignCreator.buildScheduleInfoLines(
				war,
				attackerVoter,
				uuid -> attacker);

		assertTrue(lines.stream().anyMatch(line -> line.contains("21/08/2026")));
		assertTrue(lines.stream().anyMatch(line -> line.contains("16:00 CET / 10:00 EST")));
		assertTrue(lines.stream().anyMatch(line -> line.contains("21:00 CET / 15:00 EST")));
		assertTrue(lines.stream().anyMatch(line -> line.contains("Total voters")));
		assertTrue(lines.stream().anyMatch(line -> line.contains("Hour Votes")));
		assertTrue(lines.stream().anyMatch(line -> line.contains("AttackerRealm/DefenderRealm")));
		assertTrue(lines.stream().anyMatch(line -> line.contains("Status:") && line.contains("Currently Voting")));
		assertFalse(lines.stream().anyMatch(line -> line.contains("Schedule:")));
		assertFalse(lines.stream().anyMatch(line -> line.contains("Push target")));
	}

	@Test
	void buildScheduleInfoLines_includesScheduledFightWhenScheduled() {
		War war = votingWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setScheduledBattleAt(Instant.parse("2026-08-21T19:00:00Z"));
		war.setScheduledBattleProvinceId(20);

		List<String> lines = CampaignCreator.buildScheduleInfoLines(war, attackerVoter, uuid -> attacker);

		assertTrue(lines.stream().anyMatch(line -> line.contains("21:00 CET / 15:00 EST")));
		assertTrue(lines.stream().anyMatch(line -> line.contains("Fight At:")));
		assertTrue(lines.stream().anyMatch(line -> line.contains("Battle province")));
		assertTrue(lines.stream().anyMatch(line -> line.contains("Awaiting Battle")));
	}

	@Test
	void usesGuildBranchOnlyForGuildViews() {
		assertTrue(InventoryUpdater.usesGuildBranch(SFGUI.GUILD_VIEW));
		assertTrue(InventoryUpdater.usesGuildBranch(SFGUI.UPGRADE_VIEW));
		assertFalse(InventoryUpdater.usesGuildBranch(SFGUI.MILITARY_VIEW));
		assertFalse(InventoryUpdater.usesGuildBranch(SFGUI.INSTALLATIONS_VIEW));
		assertFalse(InventoryUpdater.usesGuildBranch(SFGUI.DIPLOMACY_VIEW));
	}

	private War votingWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(LocalDate.of(2026, 8, 21));
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		return war;
	}
}
