package me.Plugins.SimpleFactions.War.campaign.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Database.Database;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.Permissions;
import me.Plugins.SimpleFactions.War.core.WarCommandManager;
import me.Plugins.SimpleFactions.War.resolution.WarReparationsObligation;

class WarReparationsAdminServiceTest {
	private List<Faction> previousFactions;
	private Faction payer;
	private Faction payee;
	private List<WarReparationsObligation> obligations;

	@BeforeEach
	void setUp() {
		Cache.warReparationsIncomePercent = 25;
		Cache.warReparationsDays = 10;
		previousFactions = FactionManager.factions;
		FactionManager.factions = new ArrayList<>();
		payer = mockFaction("atk");
		payee = mockFaction("def");
		obligations = new ArrayList<>();
		when(payer.getWarReparationsObligations()).thenReturn(obligations);
		doAnswer(invocation -> {
			obligations.add(invocation.getArgument(0));
			return null;
		}).when(payer).addWarReparationsObligation(any());
		FactionManager.factions.add(payer);
		FactionManager.factions.add(payee);
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions = previousFactions;
	}

	@Test
	void apply_unknownFactionRejected() {
		WarReparationsAdminService.ApplyResult result =
				WarReparationsAdminService.apply("missing", "def", null, null);
		assertFalse(result.ok());
		assertEquals("§cUnknown faction: missing", result.message());
		assertTrue(obligations.isEmpty());
	}

	@Test
	void apply_defaultsAndPersistsPayer() {
		try (MockedConstruction<Database> databases = mockConstruction(Database.class)) {
			WarReparationsAdminService.ApplyResult result =
					WarReparationsAdminService.apply("atk", "def", null, null);

			assertTrue(result.ok());
			assertEquals(
					"§aAdded reparations: atk pays def 25% of main-guild income for 10 days.",
					result.message());
			assertEquals(1, obligations.size());
			assertEquals(25, obligations.get(0).getIncomePercent());
			assertEquals(10, obligations.get(0).getDaysRemaining());
			verify(databases.constructed().get(0)).saveFaction(payer);
		}
	}

	@Test
	void apply_overridesPercentAndDays() {
		try (MockedConstruction<Database> databases = mockConstruction(Database.class)) {
			WarReparationsAdminService.ApplyResult result =
					WarReparationsAdminService.apply("atk", "def", "40", "3");

			assertTrue(result.ok());
			assertEquals(
					"§aAdded reparations: atk pays def 40% of main-guild income for 3 days.",
					result.message());
			assertEquals(40, obligations.get(0).getIncomePercent());
			assertEquals(3, obligations.get(0).getDaysRemaining());
			verify(databases.constructed().get(0)).saveFaction(payer);
		}
	}

	@Test
	void command_nonAdminRejected() {
		Player player = mock(Player.class);
		when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(false);
		Command command = mock(Command.class);

		new WarCommandManager().onCommand(
				player, command, "war", new String[] {"admin", "reparations", "atk", "def"});

		verify(player).sendMessage("§a[SimpleFactions]§c You do not have access to this command");
		assertTrue(obligations.isEmpty());
	}

	@Test
	void command_unknownFactionRejectedWithoutSave() {
		Player player = mock(Player.class);
		when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
		Command command = mock(Command.class);

		try (MockedConstruction<Database> databases = mockConstruction(Database.class)) {
			new WarCommandManager().onCommand(
					player, command, "war", new String[] {"admin", "reparations", "nope", "def"});

			verify(player).sendMessage("§cUnknown faction: nope");
			assertTrue(databases.constructed().isEmpty());
		}
	}

	private static Faction mockFaction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		return faction;
	}
}
