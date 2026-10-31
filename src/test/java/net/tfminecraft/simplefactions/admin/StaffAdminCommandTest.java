package net.tfminecraft.simplefactions.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.migration.QuarantineCommands;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.rest.RestServer;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.settlement.handler.SettlementHandler;
import net.tfminecraft.simplefactions.utils.Permissions;

class StaffAdminCommandTest {
	private MockedStatic<StaffAdminService> service;
	private MockedStatic<FactionManager> factions;
	private MockedStatic<SimpleFactions> plugin;
	private MockedStatic<RestServer> rest;
	private MockedStatic<QuarantineCommands> quarantine;
	private MockedStatic<Members> members;
	private boolean provinces;
	private CommandSender console;
	private Player staff;
	private ItemStack held;
	private Faction faction;
	private Guild guild;
	private Logger logger;
	private long now;

	@BeforeEach
	void setUp() {
		provinces = Cache.provincesEnabled;
		Cache.provincesEnabled = true;
		service = mockStatic(StaffAdminService.class,
				call -> call.getMethod().getReturnType() == List.class ? List.of("ok") : null);
		factions = mockStatic(FactionManager.class);
		plugin = mockStatic(SimpleFactions.class);
		rest = mockStatic(RestServer.class);
		quarantine = mockStatic(QuarantineCommands.class);
		members = mockStatic(Members.class, CALLS_REAL_METHODS);
		members.when(() -> Members.display(anyString())).thenAnswer(call -> call.getArgument(0));
		SimpleFactions instance = mock(SimpleFactions.class);
		logger = mock(Logger.class);
		when(instance.getLogger()).thenReturn(logger);
		plugin.when(SimpleFactions::getInstance).thenReturn(instance);
		console = mock(CommandSender.class);
		when(console.getName()).thenReturn("CONSOLE");
		when(console.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
		staff = mock(Player.class);
		when(staff.getName()).thenReturn("Fran");
		when(staff.getUniqueId()).thenReturn(UUID.randomUUID());
		when(staff.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
		PlayerInventory inventory = mock(PlayerInventory.class);
		held = mock(ItemStack.class);
		when(inventory.getItemInMainHand()).thenReturn(held);
		when(staff.getInventory()).thenReturn(inventory);
		faction = mock(Faction.class);
		when(faction.getId()).thenReturn("sunsora");
		when(faction.getRGB()).thenReturn("1,2,3");
		when(faction.getMembers()).thenReturn(List.of("Bob Smith", "Carl"));
		guild = mock(Guild.class);
		when(guild.getId()).thenReturn("smiths");
		when(guild.getRGB()).thenReturn("4,5,6");
		when(guild.getMembers()).thenReturn(List.of("Bob Smith"));
		factions.when(() -> FactionManager.getByString("sunsora")).thenReturn(faction);
		factions.when(() -> FactionManager.getGuildByString("smiths")).thenReturn(guild);
		factions.when(FactionManager::getCopy).thenReturn(List.of(faction));
		factions.when(FactionManager::getAllGuilds).thenReturn(List.of(guild));
		now = 1_000;
		StaffAdminCommand.clock = () -> now;
		StaffAdminCommand.clearPending();
	}

	@AfterEach
	void tearDown() {
		StaffAdminCommand.clock = System::currentTimeMillis;
		StaffAdminCommand.clearPending();
		members.close();
		quarantine.close();
		rest.close();
		plugin.close();
		factions.close();
		service.close();
		Cache.provincesEnabled = provinces;
	}

	private boolean run(CommandSender sender, String command, String... args) {
		return StaffAdminCommand.handle(sender, command, args);
	}

	@Test
	void matchesOnlyTheAdminSubcommandOfFactionAndGuild() {
		assertTrue(StaffAdminCommand.matches("faction", new String[] {"ADMIN"}));
		assertTrue(StaffAdminCommand.matches("Guild", new String[] {"admin", "info"}));
		assertFalse(StaffAdminCommand.matches("company", new String[] {"admin"}));
		assertFalse(StaffAdminCommand.matches("faction", new String[] {}));
		assertFalse(StaffAdminCommand.matches("faction", new String[] {"title"}));
	}

	@Test
	void argumentCountsFitEachSubcommand() {
		assertTrue(StaffAdminCommand.fits("confirm", 2));
		assertFalse(StaffAdminCommand.fits("confirm", 3));
		assertTrue(StaffAdminCommand.fits("info", 3));
		assertFalse(StaffAdminCommand.fits("info", 4));
		assertTrue(StaffAdminCommand.fits("movecapital", 5));
		assertFalse(StaffAdminCommand.fits("kick", 3));
		assertTrue(StaffAdminCommand.fits("set", 6));
		assertFalse(StaffAdminCommand.fits("renamesettlement", 4));
		assertTrue(StaffAdminCommand.fits("removeinstallation", 4));
		assertFalse(StaffAdminCommand.fits("removeinstallation", 5));
	}

	@Test
	void nonStaffAreRefusedAndBareCommandsShowUsage() {
		CommandSender player = mock(CommandSender.class);
		assertTrue(run(player, "faction", "admin", "info", "sunsora"));
		verify(player).sendMessage(StaffAdminCommand.DENIED);
		service.verifyNoInteractions();
		run(console, "faction", "admin");
		verify(console).sendMessage(StaffAdminCommand.FACTION_USAGE);
		run(console, "guild", "admin");
		verify(console).sendMessage(StaffAdminCommand.GUILD_USAGE);
		run(console, "faction", "admin", "nonsense");
		verify(console, org.mockito.Mockito.times(2)).sendMessage(StaffAdminCommand.FACTION_USAGE);
		run(console, "guild", "admin", "nonsense");
		verify(console, org.mockito.Mockito.times(2)).sendMessage(StaffAdminCommand.GUILD_USAGE);
	}

	@Test
	void wrongArgumentsShowThatSubcommandsUsage() {
		run(console, "faction", "admin", "rename", "sunsora");
		verify(console).sendMessage(StaffAdminCommand.FACTION_USAGE[2]);
		run(console, "faction", "admin", "info", "sunsora", "extra");
		verify(console).sendMessage(StaffAdminCommand.FACTION_USAGE[0]);
		run(console, "guild", "admin", "kick", "smiths");
		verify(console).sendMessage(StaffAdminCommand.GUILD_USAGE[4]);
		run(console, "faction", "admin", "info", "missing");
		verify(console).sendMessage("§cNo faction by the id missing");
		run(console, "guild", "admin", "info", "missing");
		verify(console).sendMessage("§cNo guild by the id missing");
		service.verifyNoInteractions();
	}

	@Test
	void quarantineKeepsItsOwnHandler() {
		String[] args = {"admin", "quarantine", "list"};
		assertTrue(StaffAdminCommand.handle(console, "faction", args));
		quarantine.verify(() -> QuarantineCommands.handle(console, args));
	}

	@Test
	void factionCommandsRouteToTheServiceAndLogChanges() {
		run(console, "faction", "admin", "info", "sunsora");
		service.verify(() -> StaffAdminService.info(faction));
		run(console, "faction", "admin", "who", "Bob", "Smith");
		service.verify(() -> StaffAdminService.who("Bob Smith"));
		run(console, "faction", "admin", "rename", "sunsora", "Sunsora", "Empire");
		service.verify(() -> StaffAdminService.rename(faction, "Sunsora Empire"));
		run(console, "faction", "admin", "renamesettlement", "sunsora", "capital", "Sunsora", "Castle");
		service.verify(() -> StaffAdminService.renameSettlement(faction, "capital", "Sunsora Castle"));
		run(console, "faction", "admin", "setcolour", "sunsora", "1,", "2,", "3");
		service.verify(() -> StaffAdminService.setColour(faction, "1,2,3"));
		run(console, "faction", "admin", "set", "sunsora", "culture", "High", "Elven");
		service.verify(() -> StaffAdminService.set(faction, "culture", "High Elven"));
		run(console, "faction", "admin", "kick", "sunsora", "Bob", "Smith");
		service.verify(() -> StaffAdminService.kick(faction, "Bob Smith"));
		run(console, "faction", "admin", "removeinstallation", "sunsora", "Sunray_Railway");
		service.verify(() -> StaffAdminService.removeInstallation(faction, "Sunray_Railway"));
		verify(logger).info("[StaffAdmin] CONSOLE ran /faction admin removeinstallation sunsora Sunray_Railway: ok");
		verify(console, org.mockito.Mockito.times(8)).sendMessage("ok");
		verify(logger).info("[StaffAdmin] CONSOLE ran /faction admin kick sunsora Bob Smith: ok");
		verify(logger, never()).info("[StaffAdmin] CONSOLE ran /faction admin info sunsora: ok");
	}

	@Test
	void bannersAndCapitalMovesNeedAPlayerInGame() {
		run(console, "faction", "admin", "setbanner", "sunsora");
		run(console, "guild", "admin", "setbanner", "smiths");
		run(console, "faction", "admin", "movecapital", "sunsora");
		run(console, "faction", "admin", "confirm");
		verify(console, org.mockito.Mockito.times(4)).sendMessage(StaffAdminCommand.IN_GAME);
		run(staff, "faction", "admin", "setbanner", "sunsora");
		service.verify(() -> StaffAdminService.setBanner(faction, held));
		run(staff, "guild", "admin", "setbanner", "smiths");
		service.verify(() -> StaffAdminService.guildSetBanner(guild, held));
	}

	@Test
	void settlementCommandsNeedProvinces() {
		Cache.provincesEnabled = false;
		run(console, "faction", "admin", "renamesettlement", "sunsora", "capital", "X");
		run(staff, "faction", "admin", "movecapital", "sunsora");
		verify(console).sendMessage(Cache.PROVINCES_DISABLED_MESSAGE);
		verify(staff).sendMessage(Cache.PROVINCES_DISABLED_MESSAGE);
		service.verifyNoInteractions();
	}

	@Test
	void capitalMovesArePreviewedThenConfirmedOnce() {
		StaffAdminService.CapitalMove move = new StaffAdminService.CapitalMove(faction, 1, 5, "Sunsora_Castle");
		rest.when(() -> RestServer.getProvince(staff)).thenReturn(5);
		service.when(() -> StaffAdminService.previewCapital(eq(staff), eq(faction), eq(5), eq("Sunsora Castle"), any()))
				.thenAnswer(call -> {
					call.<List<String>>getArgument(4).add("preview");
					return move;
				});
		run(staff, "faction", "admin", "movecapital", "sunsora", "Sunsora", "Castle");
		verify(staff).sendMessage("preview");
		service.when(() -> StaffAdminService.moveCapital(staff, move, 5)).thenReturn(List.of("§aMoved"));
		run(staff, "faction", "admin", "confirm");
		verify(staff).sendMessage("§aMoved");
		verify(logger).info("[StaffAdmin] Fran ran /faction admin confirm: Moved");
		run(staff, "faction", "admin", "confirm");
		verify(staff).sendMessage("§cNothing to confirm. Run §e/faction admin movecapital §cagain.");
		service.verify(() -> StaffAdminService.moveCapital(staff, move, 5));
	}

	@Test
	void aRefusedOrStalePreviewCannotBeConfirmed() {
		StaffAdminService.CapitalMove move = new StaffAdminService.CapitalMove(faction, 1, 5, null);
		rest.when(() -> RestServer.getProvince(staff)).thenReturn(5);
		service.when(() -> StaffAdminService.previewCapital(eq(staff), eq(faction), eq(5), any(), any())).thenReturn(move);
		run(staff, "faction", "admin", "movecapital", "sunsora");
		service.verify(() -> StaffAdminService.previewCapital(eq(staff), eq(faction), eq(5), eq(null), any()));
		// A later refused preview drops the earlier one.
		service.when(() -> StaffAdminService.previewCapital(eq(staff), eq(faction), eq(5), any(), any())).thenReturn(null);
		run(staff, "faction", "admin", "movecapital", "sunsora");
		run(staff, "faction", "admin", "confirm");
		service.when(() -> StaffAdminService.previewCapital(eq(staff), eq(faction), eq(5), any(), any())).thenReturn(move);
		run(staff, "faction", "admin", "movecapital", "sunsora");
		now += StaffAdminCommand.CONFIRM_MILLIS + 1;
		run(staff, "faction", "admin", "confirm");
		verify(staff, org.mockito.Mockito.times(2)).sendMessage("§cNothing to confirm. Run §e/faction admin movecapital §cagain.");
		// Provinces turned off between preview and confirm.
		now = 1_000;
		run(staff, "faction", "admin", "movecapital", "sunsora");
		Cache.provincesEnabled = false;
		run(staff, "faction", "admin", "confirm");
		service.verify(() -> StaffAdminService.moveCapital(any(), any(), anyInt()), never());
	}

	@Test
	void guildCommandsRouteToTheService() {
		run(console, "guild", "admin", "info", "smiths");
		service.verify(() -> StaffAdminService.guildInfo(guild));
		run(console, "guild", "admin", "rename", "smiths", "Iron", "Smiths");
		service.verify(() -> StaffAdminService.guildRename(guild, "Iron Smiths"));
		run(console, "guild", "admin", "setcolour", "smiths", "7,8,9");
		service.verify(() -> StaffAdminService.guildSetColour(guild, "7,8,9"));
		run(console, "guild", "admin", "kick", "smiths", "Bob", "Smith");
		service.verify(() -> StaffAdminService.guildKick(guild, "Bob Smith"));
	}

	@Test
	void completionIsForStaffOnly() {
		CommandSender player = mock(CommandSender.class);
		assertEquals(List.of(), StaffAdminCommand.complete(player, "faction", new String[] {"admin", ""}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "faction", new String[] {"title", ""}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "faction", new String[] {"admin"}));
	}

	@Test
	void completionOffersSubcommandsTargetsAndArguments() {
		assertEquals(List.of("rename", "renamesettlement"), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "ren"}));
		assertEquals(List.of("kick"), StaffAdminCommand.complete(console, "guild", new String[] {"admin", "K"}));
		quarantine.when(() -> QuarantineCommands.complete(eq(console), any())).thenReturn(List.of("list"));
		assertEquals(List.of("list"), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "quarantine", ""}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "bogus", ""}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "confirm", ""}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "who", ""}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "guild", new String[] {"admin", "quarantine", ""}));
		assertEquals(List.of("sunsora"), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "info", "S"}));
		assertEquals(List.of("smiths"), StaffAdminCommand.complete(console, "guild", new String[] {"admin", "info", ""}));
	}

	@Test
	void guildCompletionUsesTheChosenGuild() {
		assertEquals(List.of(), StaffAdminCommand.complete(console, "guild", new String[] {"admin", "kick", "missing", ""}));
		assertEquals(List.of("4,5,6"), StaffAdminCommand.complete(console, "guild", new String[] {"admin", "setcolour", "smiths", ""}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "guild", new String[] {"admin", "setcolour", "smiths", "4,5,6", ""}));
		assertEquals(List.of("Smith"), StaffAdminCommand.complete(console, "guild", new String[] {"admin", "kick", "smiths", "Bob", "S"}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "guild", new String[] {"admin", "rename", "smiths", ""}));
	}

	@Test
	void factionCompletionUsesTheChosenFaction() {
		assertEquals(List.of(), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "kick", "missing", ""}));
		SettlementHandler settlements = mock(SettlementHandler.class);
		Settlement tower = mock(Settlement.class);
		when(tower.getId()).thenReturn("Research_Tower");
		when(settlements.getAll()).thenReturn(List.of(tower));
		when(faction.getSettlementHandler()).thenReturn(settlements);
		assertEquals(List.of("capital", "Research_Tower"),
				StaffAdminCommand.complete(console, "faction", new String[] {"admin", "renamesettlement", "sunsora", ""}));
		assertEquals(List.of("Research_Tower"),
				StaffAdminCommand.complete(console, "faction", new String[] {"admin", "renamesettlement", "sunsora", "re"}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "renamesettlement", "sunsora", "x", ""}));
		assertEquals(List.of("1,2,3"), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "setcolour", "sunsora", ""}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "setcolour", "sunsora", "1", ""}));
		assertEquals(List.of("rulingsystem", "rulertitle"),
				StaffAdminCommand.complete(console, "faction", new String[] {"admin", "set", "sunsora", "rul"}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "set", "sunsora", "culture", ""}));
		assertEquals(List.of("Carl"), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "kick", "sunsora", "c"}));
		service.when(() -> StaffAdminService.installationIds(faction)).thenReturn(new ArrayList<>(List.of("Sunray_Railway", "Sunray_Docks")));
		assertEquals(List.of("Sunray_Railway"),
				StaffAdminCommand.complete(console, "faction", new String[] {"admin", "removeinstallation", "sunsora", "sunray_r"}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "removeinstallation", "sunsora", "x", ""}));
		assertEquals(List.of(), StaffAdminCommand.complete(console, "faction", new String[] {"admin", "info", "sunsora", ""}));
	}
}
