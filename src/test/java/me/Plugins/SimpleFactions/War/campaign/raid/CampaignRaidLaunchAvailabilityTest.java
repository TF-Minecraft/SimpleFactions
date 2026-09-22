package me.Plugins.SimpleFactions.War.campaign.raid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class CampaignRaidLaunchAvailabilityTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;
	private War war;
	private Instant raidWindow;

	@BeforeEach
	void setUp() {
		Cache.warRaidWindowStartHour = 19;
		Cache.warRaidWindowEndHour = 20;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");

		war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		raidWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 19);
	}

	@Test
	void describe_hidesMusterFromDefenders() {
		CampaignRaid raid = new CampaignRaid();
		raid.setId("harbor_raid");
		raid.setState(CampaignRaidState.MUSTER);
		raid.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
		war.setActiveCampaignRaid(raid);

		var defenderView = CampaignRaidLaunchAvailability.describe(war, defender, raidWindow);
		assertFalse(defenderView.loreLines().stream().anyMatch(line -> line.contains("raid in progress")
				|| line.contains("muster in progress")));

		var attackerView = CampaignRaidLaunchAvailability.describe(war, attacker, raidWindow);
		assertTrue(attackerView.loreLines().stream().anyMatch(line -> line.contains("muster in progress")));
	}

	@Test
	void describe_showsFightingRaidToBothSides() {
		CampaignRaid raid = new CampaignRaid();
		raid.setId("harbor_raid");
		raid.setState(CampaignRaidState.FIGHTING);
		raid.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
		war.setActiveCampaignRaid(raid);

		var defenderView = CampaignRaidLaunchAvailability.describe(war, defender, raidWindow);
		assertTrue(defenderView.loreLines().stream().anyMatch(line -> line.contains("raid in progress")));
	}
}
