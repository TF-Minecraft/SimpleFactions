package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.RelocateRequest;
import net.tfminecraft.simplefactions.objects.request.Request;
import net.tfminecraft.simplefactions.objects.request.VehicleTransferConsentRequest;
import net.tfminecraft.simplefactions.objects.request.WarRequest;
import net.tfminecraft.simplefactions.utils.TabCompletion;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferConsentService;
import net.tfminecraft.simplefactions.war.core.War;

class RequestManagerDeclineTest {
	private List<Faction> savedFactions;
	private int savedPenalty;
	private final List<Player> players = new ArrayList<>();

	@BeforeEach
	void setUp() {
		savedFactions = new ArrayList<>(FactionManager.factions);
		FactionManager.factions.clear();
		savedPenalty = Cache.warDeclinedAllyStabilityPenalty;
		Cache.warDeclinedAllyStabilityPenalty = -30;
	}

	@AfterEach
	void tearDown() {
		for (Player player : players) {
			RequestManager.remove(player);
		}
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
		Cache.warDeclinedAllyStabilityPenalty = savedPenalty;
	}

	@Test
	void factionDecline_withoutRequest_tellsThePlayer() {
		Player player = player("Bob");
		Command command = factionCommand();

		assertTrue(new CommandManager().onCommand(player, command, "faction", new String[] {"decline"}));

		verify(player).sendMessage("§cYou have no requests to decline");
	}

	@Test
	void factionDecline_offersDeclineInTabCompletion() {
		Player player = player("Bob");
		Command command = factionCommand();

		List<String> options = new TabCompletion().onTabComplete(player, command, "faction", new String[] {""});

		assertTrue(options.contains("decline"));
		assertTrue(options.contains("accept"));
	}

	@Test
	void factionDecline_otherRequest_removesItWithoutStability() {
		Player player = player("Bob");
		Faction called = faction("called", "Bob", "Called");
		Government government = mock(Government.class);
		when(called.getGovernment()).thenReturn(government);
		FactionManager.factions.add(called);
		RequestManager.addRequest(player, player, new RelocateRequest(mock(Guild.class), 4, "Town"));

		assertTrue(new CommandManager().onCommand(player, commandNamed("faction"), "faction", new String[] {"decline"}));

		assertFalse(RequestManager.hasRequest(player));
		verify(player).sendMessage("§7You declined the request.");
		verify(government, never()).addStabilityModifier(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void factionDecline_warRequest_penalisesCalledFactionAndTellsCaller() {
		Player calledPlayer = player("AllyLead");
		when(calledPlayer.isOnline()).thenReturn(true);
		Player caller = player("AtkLead");
		when(caller.isOnline()).thenReturn(true);
		Faction called = faction("ally", "AllyLead", "Allies");
		Government government = mock(Government.class);
		when(called.getGovernment()).thenReturn(government);
		Faction origin = faction("atk", "AtkLead", "Attackers");
		FactionManager.factions.add(called);
		Guild guild = mock(Guild.class);
		when(guild.getFaction()).thenReturn(origin);
		RequestManager.addRequest(caller, calledPlayer, new WarRequest(guild, activeWar()));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("AtkLead")).thenReturn(caller);

			assertTrue(new CommandManager().onCommand(
					calledPlayer, factionCommand(), "faction", new String[] {"decline"}));
		}

		assertFalse(RequestManager.hasRequest(calledPlayer));
		ArgumentCaptor<StabilityModifier> captor = ArgumentCaptor.forClass(StabilityModifier.class);
		verify(government).addStabilityModifier(captor.capture());
		assertEquals("Declined Call to Arms", captor.getValue().getName());
		assertEquals(-30, captor.getValue().getModifier());
		assertEquals(1, captor.getValue().getDecay());
		verify(caller).sendMessage("Allies §cdeclined your call to arms");
		verify(calledPlayer).sendMessage("§7You declined the call to arms.");
	}

	@Test
	void factionDecline_warRequestWithoutGovernment_stillTellsCaller() {
		Player calledPlayer = player("AllyLead");
		when(calledPlayer.isOnline()).thenReturn(true);
		Player caller = player("AtkLead");
		when(caller.isOnline()).thenReturn(true);
		Faction called = faction("ally", "AllyLead", "Allies");
		when(called.getGovernment()).thenReturn(null);
		Faction origin = faction("atk", "AtkLead", "Attackers");
		FactionManager.factions.add(called);
		Guild guild = mock(Guild.class);
		when(guild.getFaction()).thenReturn(origin);
		RequestManager.addRequest(caller, calledPlayer, new WarRequest(guild, activeWar()));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("AtkLead")).thenReturn(caller);
			RequestManager.decline(calledPlayer);
		}

		assertFalse(RequestManager.hasRequest(calledPlayer));
		verify(caller).sendMessage("Allies §cdeclined your call to arms");
	}

	@Test
	void timeout_warRequests_penaliseEachCalledFaction() throws Exception {
		Player first = player("One");
		Player second = player("Two");
		Player caller = player("AtkLead");
		when(caller.isOnline()).thenReturn(true);
		Faction firstFaction = faction("one", "One", "First");
		Faction secondFaction = faction("two", "Two", "Second");
		Government firstGovernment = mock(Government.class);
		Government secondGovernment = mock(Government.class);
		when(firstFaction.getGovernment()).thenReturn(firstGovernment);
		when(secondFaction.getGovernment()).thenReturn(secondGovernment);
		Faction origin = faction("atk", "AtkLead", "Attackers");
		FactionManager.factions.add(firstFaction);
		FactionManager.factions.add(secondFaction);
		Guild guild = mock(Guild.class);
		when(guild.getFaction()).thenReturn(origin);
		WarRequest firstRequest = new WarRequest(guild, activeWar());
		WarRequest secondRequest = new WarRequest(guild, activeWar());
		forceTimeout(firstRequest);
		forceTimeout(secondRequest);
		RequestManager.addRequest(caller, first, firstRequest);
		RequestManager.addRequest(caller, second, secondRequest);
		Player waiting = player("Waiting");
		Request stillOpen = new RelocateRequest(mock(Guild.class), 1, "Town");
		RequestManager.addRequest(waiting, waiting, stillOpen);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("AtkLead")).thenReturn(caller);
			RequestManager.expireTimedOutRequests();
		}

		assertFalse(RequestManager.hasRequest(first));
		assertFalse(RequestManager.hasRequest(second));
		assertTrue(RequestManager.hasRequest(waiting));
		verify(firstGovernment).addStabilityModifier(org.mockito.ArgumentMatchers.any(StabilityModifier.class));
		verify(secondGovernment).addStabilityModifier(org.mockito.ArgumentMatchers.any(StabilityModifier.class));
		verify(caller, org.mockito.Mockito.times(2)).sendMessage(org.mockito.ArgumentMatchers.contains("declined your call to arms"));
		verify(first, never()).sendMessage("§7You declined the call to arms.");
	}

	@Test
	void timeout_vehicleConsent_stillNotifies() throws Exception {
		Player owner = player("Owner");
		Guild guild = mock(Guild.class);
		VehicleTransferConsentRequest request = new VehicleTransferConsentRequest(
				guild, "fort", "Fort", UUID.randomUUID().toString(), "wagon", UUID.randomUUID(), UUID.randomUUID());
		forceTimeout(request);
		RequestManager.addRequest(owner, owner, request);
		SimpleFactions plugin = mock(SimpleFactions.class);
		VehicleTransferConsentService service = mock(VehicleTransferConsentService.class);
		when(plugin.getVehicleTransferConsentService()).thenReturn(service);

		try (MockedStatic<SimpleFactions> plugins = mockStatic(SimpleFactions.class)) {
			plugins.when(SimpleFactions::getInstance).thenReturn(plugin);
			RequestManager.expireTimedOutRequests();
		}

		assertFalse(RequestManager.hasRequest(owner));
		verify(service).notifyExpired(request, owner);
	}

	@Test
	void warRequest_lastsSixtySeconds() {
		WarRequest request = new WarRequest(mock(Guild.class), activeWar());
		assertFalse(request.timedOut());
	}

	@Test
	void factionDecline_endedWar_noPenalty() {
		Player calledPlayer = player("AllyLead");
		Player caller = player("AtkLead");
		Faction called = faction("ally", "AllyLead", "Allies");
		Government government = mock(Government.class);
		when(called.getGovernment()).thenReturn(government);
		FactionManager.factions.add(called);
		Faction origin = faction("atk", "AtkLead", "Attackers");
		Guild guild = mock(Guild.class);
		when(guild.getFaction()).thenReturn(origin);
		RequestManager.addRequest(caller, calledPlayer, new WarRequest(guild, mock(War.class)));

		RequestManager.decline(calledPlayer);

		assertFalse(RequestManager.hasRequest(calledPlayer));
		verify(government, never()).addStabilityModifier(org.mockito.ArgumentMatchers.any());
	}

	private static War activeWar() {
		War war = mock(War.class);
		when(war.isActive()).thenReturn(true);
		return war;
	}

	private Player player(String name) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		players.add(player);
		return player;
	}

	private static Faction faction(String id, String leader, String name) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getLeader()).thenReturn(leader);
		when(faction.getName()).thenReturn(name);
		return faction;
	}

	private static Command factionCommand() {
		return commandNamed("faction");
	}

	private static Command commandNamed(String name) {
		Command command = mock(Command.class);
		when(command.getName()).thenReturn(name);
		return command;
	}

	private static void forceTimeout(Request request) throws Exception {
		Field time = Request.class.getDeclaredField("time");
		time.setAccessible(true);
		time.setLong(request, 0L);
	}
}
