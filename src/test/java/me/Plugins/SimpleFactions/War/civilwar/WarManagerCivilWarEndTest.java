package me.Plugins.SimpleFactions.War.civilwar;


import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarUntangleService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickService;
import me.Plugins.SimpleFactions.War.combat.WarCombatTeardownService;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.resolution.WarOutcomeService;
import me.Plugins.SimpleFactions.installation.WartimeInstallationService;

class WarManagerCivilWarEndTest {
	private List<War> savedWars;

	@BeforeEach
	void setUp() {
		savedWars = new ArrayList<>(WarManager.get());
		WarManager.get().clear();
	}

	@AfterEach
	void tearDown() {
		WarManager.get().clear();
		WarManager.get().addAll(savedWars);
	}

	@Test
	void endWar_restoresBeforeOutcome() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getMembers()).thenReturn(List.of());
		when(defender.getMembers()).thenReturn(List.of());
		War war = new War(99, attacker, defender);
		WarManager.get().add(war);

		AtomicInteger step = new AtomicInteger();
		int[] marks = new int[3];

		try (MockedStatic<WartimeInstallationService> wartime = mockStatic(WartimeInstallationService.class);
				MockedStatic<CivilWarUntangleService> untangle = mockStatic(CivilWarUntangleService.class);
				MockedStatic<WarOutcomeService> outcome = mockStatic(WarOutcomeService.class);
				MockedStatic<WarCombatTeardownService> combat = mockStatic(WarCombatTeardownService.class);
				MockedStatic<BattleInstallationPickService> picks = mockStatic(BattleInstallationPickService.class);
				MockedStatic<CampaignRaidService> raids = mockStatic(CampaignRaidService.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			wartime.when(() -> WartimeInstallationService.revert(war)).thenAnswer(invocation -> {
				marks[0] = step.incrementAndGet();
				return null;
			});
			untangle.when(() -> CivilWarUntangleService.restore(war, WarEndReason.ADMIN_END)).thenAnswer(invocation -> {
				marks[1] = step.incrementAndGet();
				return null;
			});
			outcome.when(() -> WarOutcomeService.apply(war, WarEndReason.ADMIN_END)).thenAnswer(invocation -> {
				marks[2] = step.incrementAndGet();
				return null;
			});

			WarManager.endWar(war, WarEndReason.ADMIN_END);

			assertEquals(1, marks[0]);
			assertEquals(2, marks[1]);
			assertEquals(3, marks[2]);
		}
	}
}
