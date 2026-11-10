package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.RelationRequest;
import net.tfminecraft.simplefactions.objects.request.RelocateRequest;
import net.tfminecraft.simplefactions.objects.request.Request;
import net.tfminecraft.simplefactions.objects.request.VehicleTransferConsentRequest;
import net.tfminecraft.simplefactions.utils.TabCompletion;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferConsentService;
import net.tfminecraft.simplefactions.war.core.CallToArms;
import net.tfminecraft.simplefactions.war.core.War;

class RequestManagerDeclineTest {
	private List<Faction> savedFactions;
	private int savedPenalty;
	private final List<Player> players = new ArrayList<>();
	private final List<War> wars = new ArrayList<>();

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
		WarManager.get().removeAll(wars);
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
	void timeout_vehicleConsent_stillNotifies() throws Exception {
		Player owner = player("Owner");
		Guild guild = mock(Guild.class);
		VehicleTransferConsentRequest request = new VehicleTransferConsentRequest(
				guild, "fort", "Fort", UUID.randomUUID().toString(), "wagon",
				new net.tfminecraft.tlibs.character.CharacterRef(owner.getUniqueId() == null ? UUID.randomUUID() : owner.getUniqueId(), UUID.randomUUID()),
				new net.tfminecraft.tlibs.character.CharacterRef(UUID.randomUUID(), UUID.randomUUID()), false);
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
	void relationRequest_lastsSixtySeconds() throws Exception {
		RelationRequest request = new RelationRequest(mock(Guild.class), null, true);
		assertFalse(request.timedOut());
		assertWindow(request, 60_000L);
	}

	@Test
	void factionDecline_withoutOtherRequest_declinesTheOnlyOpenCall() {
		Player calledPlayer = player("AllyLead");
		when(calledPlayer.isOnline()).thenReturn(true);
		Player caller = player("AtkLead");
		when(caller.isOnline()).thenReturn(true);
		Faction called = faction("ally", "AllyLead", "Allies");
		Government government = mock(Government.class);
		when(called.getGovernment()).thenReturn(government);
		Faction origin = faction("atk", "AtkLead", "Attackers");
		FactionManager.factions.add(called);
		FactionManager.factions.add(origin);
		War war = openCall(4401, origin, "ally");

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedConstruction<Database> ignored = mockConstruction(Database.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("AtkLead")).thenReturn(caller);
			// The called leader is told through war notices, which reach the online character.
			bukkit.when(() -> Bukkit.getPlayerExact("AllyLead")).thenReturn(calledPlayer);

			assertTrue(new CommandManager().onCommand(
					calledPlayer, factionCommand(), "faction", new String[] {"decline"}));
		}

		assertTrue(war.getCallsToArms().isEmpty());
		ArgumentCaptor<StabilityModifier> captor = ArgumentCaptor.forClass(StabilityModifier.class);
		verify(government).addStabilityModifier(captor.capture());
		assertEquals("Declined Call to Arms", captor.getValue().getName());
		assertEquals(-30, captor.getValue().getModifier());
		assertEquals(1, captor.getValue().getDecay());
		verify(caller).sendMessage("Allies §cdeclined your call to arms: " + war.getName() + "§c.");
		verify(calledPlayer).sendMessage("§7You declined the call to arms.");
		verify(calledPlayer, never()).sendMessage("§cYou have no requests to decline");
	}

	@Test
	void factionAccept_withoutRequestOrCall_tellsThePlayer() {
		Player player = player("Bob");

		assertTrue(new CommandManager().onCommand(player, factionCommand(), "faction", new String[] {"accept"}));

		verify(player).sendMessage("§cYou have no requests to accept");
	}

	@Test
	void factionAccept_withSeveralOpenCalls_listsThemInstead() {
		Player calledPlayer = player("AllyLead");
		Faction called = faction("ally", "AllyLead", "Allies");
		Faction origin = faction("atk", "AtkLead", "Attackers");
		FactionManager.factions.add(called);
		FactionManager.factions.add(origin);
		War first = openCall(4402, origin, "ally");
		War second = openCall(4403, origin, "ally");

		assertTrue(new CommandManager().onCommand(
				calledPlayer, factionCommand(), "faction", new String[] {"accept"}));

		verify(calledPlayer).sendMessage("§6Open calls to arms:");
		verify(calledPlayer, never()).sendMessage("§cYou have no requests to accept");
		assertEquals(1, first.getCallsToArms().size());
		assertEquals(1, second.getCallsToArms().size());
	}

	private War openCall(int id, Faction origin, String targetId) {
		War war = new War(id, origin, faction("def" + id, "DefLead" + id, "Defenders"));
		war.getCallsToArms().put(targetId, new CallToArms(targetId, origin.getId(), null, Instant.now()));
		WarManager.get().add(war);
		wars.add(war);
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

	private static void assertWindow(Request request, long window) throws Exception {
		Field time = Request.class.getDeclaredField("time");
		time.setAccessible(true);
		long remaining = time.getLong(request) - System.currentTimeMillis();
		assertTrue(remaining > window - 5_000L, "remaining was " + remaining);
		assertTrue(remaining <= window, "remaining was " + remaining);
	}
}
