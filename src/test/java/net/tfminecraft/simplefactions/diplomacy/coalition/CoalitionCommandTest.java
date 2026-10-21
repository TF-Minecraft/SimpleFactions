package net.tfminecraft.simplefactions.diplomacy.coalition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.managers.CoalitionCommandManager;
import net.tfminecraft.simplefactions.managers.CoalitionTabCompletion;
import net.tfminecraft.simplefactions.managers.inventory.CoalitionView;
import net.tfminecraft.simplefactions.war.declare.WarDeclareCodeService;

class CoalitionCommandTest extends CoalitionTestBase {
	private final CoalitionCommandManager commands = new CoalitionCommandManager();
	private final Command command = mock(Command.class);
	private CoalitionView view;
	private final List<String> gatewayCalls = new ArrayList<>();
	private GatewayClient.Result reply = GatewayClient.Result.success("{\"kind\":\"coalition_offensive\"}");

	@BeforeEach
	void wire() {
		when(command.getName()).thenReturn("coalition");
		view = mock(CoalitionView.class);
		fixture.inventory.coalitionView = view;
		WarDeclareCodeService.setGateway((path, body) -> {
			gatewayCalls.add(body);
			return reply;
		});
	}

	@AfterEach
	void unwire() {
		WarDeclareCodeService.resetGateway();
	}

	private void run(CommandSender sender, String... args) {
		assertTrue(commands.onCommand(sender, command, "coalition", args));
	}

	private Player admin() {
		Player staff = fixture.player("Staff");
		when(staff.hasPermission("simplefactions.admin")).thenReturn(true);
		return staff;
	}

	@Test
	void leadersFormInviteAndRunACoalitionFromChat() {
		run(tom, "create");
		verify(tom).sendMessage("§cUsage: /coalition create <target faction> <name>");
		Player nobody = fixture.player("Nobody");
		run(nobody, "create", "holy", "League");
		verify(nobody).sendMessage("§cOnly a faction leader can do that.");
		run(tom, "create", "holy", "Grand", "League");
		Coalition league = CoalitionService.of(thalendor);
		assertEquals("Grand League", league.getName());

		run(tom, "invite");
		verify(tom).sendMessage("§cUsage: /coalition invite <faction>");
		run(tom, "invite", "bog");
		run(bea, "join");
		verify(bea).sendMessage("§cUsage: /coalition join <coalition>");
		run(nobody, "join", "Grand League");
		run(bea, "join", "Grand", "League");
		assertTrue(league.isMember("bog"));
		run(tom, "invite", "fig");
		run(finn, "join", "grand league");

		run(tom, "leader", "fig");
		assertTrue(league.isLeader("fig"));
		run(finn, "kick", "thalendor");
		assertFalse(league.isMember("thalendor"));
		run(finn, "target", "holy");
		verify(finn).sendMessage("§cYou can only choose a new target after losing the old one.");
		run(nobody, "leave");

		ItemStack banner = new ItemStack(Material.BLUE_BANNER);
		var hands = finn.getInventory();
		org.mockito.Mockito.doReturn(banner).when(hands).getItemInMainHand();
		run(finn, "setbanner");
		assertEquals(List.of("BLUE.BASE"), league.getBannerPatterns());
		run(finn, "resetbanner");
		assertNull(league.getBannerPatterns());

		run(bea, "leave");
		run(finn, "disband");
		assertNull(CoalitionRegistry.get(league.getId()));
	}

	@Test
	void menusListsAndHelpOpenForAnyone() {
		run(tom);
		verify(tom).sendMessage("§7Your faction is not in a coalition. See §e/coalition list§7.");
		league();
		run(tom, "menu");
		verify(view).menu(tom, CoalitionService.of(thalendor));
		run(hana, "list");
		verify(view).list(hana, 0);
		run(hana, "help");
		verify(hana).sendMessage("§e/coalition list §7- every coalition");
		CommandSender console = mock(CommandSender.class);
		run(console, "list");
		verify(console).sendMessage("§cThis command can only be used by players.");
	}

	@Test
	void theOffensiveCodeIsTakenFromChatAndSpentOffThread() {
		Player nobody = fixture.player("Nobody");
		run(nobody, "offensive");
		verify(nobody).sendMessage("§cOnly a faction leader can do that.");
		run(bea, "offensive");
		verify(bea).sendMessage(CoalitionService.NOT_LEADER);
		Coalition league = league();
		CoalitionCodePrompt prompt = new CoalitionCodePrompt();

		run(tom, "offensive");
		verify(tom).sendMessage("§eEnter the offensive coalition code staff gave you in chat:");
		AsyncPlayerChatEvent unrelated = new AsyncPlayerChatEvent(true, hana, "hello", Set.of());
		prompt.onPlayerChat(unrelated);
		assertFalse(unrelated.isCancelled());
		AsyncPlayerChatEvent typed = new AsyncPlayerChatEvent(true, tom, "  CODE-1 ", Set.of());
		prompt.onPlayerChat(typed);
		assertTrue(typed.isCancelled());
		fixture.ui.asyncTasks.removeFirst().run();
		assertTrue(gatewayCalls.getFirst().contains("\"kind\":\"coalition_offensive\""));
		assertTrue(gatewayCalls.getFirst().contains("\"attacker_faction_id\":\"thalendor\""));
		fixture.ui.runTasks();
		assertTrue(league.isOffensive());

		run(tom, "offensive");
		verify(tom).sendMessage("§cThe coalition is already offensive.");
		league.setOffensive(false);
		league.setTargetLostAt(CoalitionRegistry.now());
		run(tom, "offensive");
		verify(tom).sendMessage("§cChoose a new target before asking for an offensive code.");
		league.setTargetLostAt(null);

		run(tom, "offensive");
		prompt.onPlayerChat(new AsyncPlayerChatEvent(true, tom, "cancel", Set.of()));
		verify(tom).sendMessage("§7Cancelled. The coalition stays defensive.");

		run(tom, "offensive");
		prompt.onPlayerQuit(new PlayerQuitEvent(tom, "bye"));
		AsyncPlayerChatEvent afterQuit = new AsyncPlayerChatEvent(true, tom, "CODE", Set.of());
		prompt.onPlayerChat(afterQuit);
		assertFalse(afterQuit.isCancelled());

		run(tom, "offensive");
		var deadline = fixture.ui.tasks.getLast();
		deadline.run();
		verify(tom).sendMessage("§cCode entry timed out.");
	}

	@Test
	void aCodeOfTheWrongKindIsCheckedBeforeItIsSpent() {
		Coalition league = league();
		reply = GatewayClient.Result.success("{\"kind\":\"war_declare\"}");
		CoalitionCodePrompt prompt = new CoalitionCodePrompt();
		run(tom, "offensive");
		prompt.onPlayerChat(new AsyncPlayerChatEvent(true, tom, "WAR-CODE", Set.of()));
		fixture.ui.asyncTasks.removeFirst().run();
		fixture.ui.runTasks();
		assertEquals(1, gatewayCalls.size());
		verify(tom).sendMessage("§cThat code is a war declaration code, not a coalition code.");
		assertFalse(league.isOffensive());
	}

	@Test
	void aRejectedOrStaleCodeChangesNothing() {
		Coalition league = league();
		CoalitionCodePrompt.Pending state = new CoalitionCodePrompt.Pending(league.getId(), "thalendor", "holy");
		CoalitionCodePrompt.apply(tom, state, WarDeclareCodeService.Result.fail("§cThat code has already been used"));
		verify(tom).sendMessage("§cThat code has already been used");
		assertFalse(league.isOffensive());

		CoalitionCodePrompt.Pending stale = new CoalitionCodePrompt.Pending(league.getId(), "bog", "holy");
		CoalitionCodePrompt.apply(tom, stale, WarDeclareCodeService.Result.success());
		verify(tom).sendMessage("§cThe code was used, but the coalition changed meanwhile. Ask staff for help.");
		CoalitionCodePrompt.apply(tom, new CoalitionCodePrompt.Pending("gone", "thalendor", "holy"),
				WarDeclareCodeService.Result.success());
		CoalitionCodePrompt.apply(tom, new CoalitionCodePrompt.Pending(league.getId(), "thalendor", "other"),
				WarDeclareCodeService.Result.success());
		assertFalse(league.isOffensive());

		when(tom.isOnline()).thenReturn(false);
		CoalitionCodePrompt.apply(tom, state, WarDeclareCodeService.Result.fail("§cNope"));
		CoalitionCodePrompt.apply(tom, stale, WarDeclareCodeService.Result.success());
		verify(tom, never()).sendMessage("§cNope");
	}

	@Test
	void staffToolsNeedThePermission() {
		run(tom, "admin", "cost");
		verify(tom).sendMessage("§cYou do not have access to this command.");
		Player staff = admin();
		Coalition league = league();

		run(staff, "admin");
		verify(staff).sendMessage("§cUsage: /coalition admin <disband|offensive|add|kick|setleader|cost>");
		run(staff, "admin", "offensive", "maybe", "Grand League");
		verify(staff).sendMessage("§cUsage: /coalition admin offensive <on|off> <coalition>");
		run(staff, "admin", "offensive", "on");
		run(staff, "admin", "offensive", "on", "Unknown");
		verify(staff).sendMessage(CoalitionService.NO_COALITION);
		run(staff, "admin", "offensive", "on", "Grand", "League");
		assertTrue(league.isOffensive());
		run(staff, "admin", "offensive", "OFF", "Grand League");
		assertFalse(league.isOffensive());

		run(staff, "admin", "kick", "bog");
		verify(staff).sendMessage("§cUsage: /coalition admin kick <faction> <coalition>");
		run(staff, "admin", "kick", "bog", "Grand League");
		assertFalse(league.isMember("bog"));
		run(staff, "admin", "setleader", "fig", "Grand League");
		assertTrue(league.isLeader("fig"));
		run(staff, "admin", "add", "bog", "Grand League");
		assertTrue(league.isMember("bog"));
		verify(staff).sendMessage("§aAdded bog §ato the Grand League §acoalition.");

		run(staff, "admin", "disband");
		verify(staff).sendMessage("§cUsage: /coalition admin disband <coalition>");
		run(staff, "admin", "disband", "Grand League");
		assertNull(CoalitionRegistry.get(league.getId()));
	}

	@Test
	void theCostReportShowsLivePrestigeNextToAnAlliance() {
		CoalitionSettings.costBase = 5;
		Player staff = admin();
		run(staff, "admin", "cost");
		verify(staff).sendMessage(startsWith("§eMedian prestige of active factions: §f428"));
		verify(staff).sendMessage(contains("§fthalendor§7: prestige 1044 realm, r=2.44, seat §f"));
		verify(staff).sendMessage(contains("§ffig§7: prestige 428 realm, r=1.00, seat §f5§7, ally §f-"));
		verify(staff, never()).sendMessage(startsWith("§fsanctuary"));

		run(staff, "admin", "cost", "bog", "nobody");
		verify(staff).sendMessage("§cNo faction nobody.");
		verify(staff, org.mockito.Mockito.times(2)).sendMessage(contains("§fbog§7: prestige 124 realm, r=0.29, seat §f5§7, ally §f"));

		CommandSender console = mock(CommandSender.class);
		when(console.hasPermission("simplefactions.admin")).thenReturn(true);
		for (var faction : List.copyOf(net.tfminecraft.simplefactions.managers.FactionManager.factions)) faction.setPrestige(0.0);
		run(console, "admin", "cost");
		verify(console).sendMessage("§eMedian prestige of active factions: §f0");
	}

	@Test
	void tabCompletionOffersWhatEachStepTakes() {
		CoalitionTabCompletion tabs = new CoalitionTabCompletion();
		league();
		Player staff = admin();
		assertEquals(List.of("create"), tabs.onTabComplete(tom, command, "coalition", new String[] {"cr"}));
		assertFalse(tabs.onTabComplete(tom, command, "coalition", new String[] {""}).contains("admin"));
		assertTrue(tabs.onTabComplete(staff, command, "coalition", new String[] {"ad"}).contains("admin"));
		assertEquals(List.of("holy"), tabs.onTabComplete(tom, command, "coalition", new String[] {"invite", "ho"}));
		assertEquals(List.of("Grand League"), tabs.onTabComplete(tom, command, "coalition", new String[] {"join", "g"}));
		assertTrue(tabs.onTabComplete(tom, command, "coalition", new String[] {"leave", ""}).isEmpty());
		assertTrue(tabs.onTabComplete(tom, command, "coalition", new String[] {"invite", "bog", ""}).isEmpty());
		assertTrue(tabs.onTabComplete(tom, command, "coalition", new String[] {"admin", ""}).isEmpty());
		assertEquals(List.of("cost"), tabs.onTabComplete(staff, command, "coalition", new String[] {"admin", "co"}));
		assertEquals(List.of("fig"), tabs.onTabComplete(staff, command, "coalition", new String[] {"admin", "add", "f"}));
		assertEquals(List.of("fig"), tabs.onTabComplete(staff, command, "coalition", new String[] {"admin", "cost", "bog", "f"}));
		assertEquals(List.of("on", "off"), tabs.onTabComplete(staff, command, "coalition", new String[] {"admin", "offensive", ""}));
		assertEquals(List.of("Grand League"), tabs.onTabComplete(staff, command, "coalition", new String[] {"admin", "offensive", "on", "G"}));
		assertEquals(List.of("thalendor"), tabs.onTabComplete(staff, command, "coalition", new String[] {"admin", "kick", "th"}));
		assertEquals(List.of("bog"), tabs.onTabComplete(staff, command, "coalition", new String[] {"admin", "setleader", "b"}));
		assertEquals(List.of("Grand League"), tabs.onTabComplete(staff, command, "coalition", new String[] {"admin", "disband", ""}));
		assertTrue(tabs.onTabComplete(staff, command, "coalition", new String[] {"admin", "disband", "a", "b", "c"}).isEmpty());
	}
}
