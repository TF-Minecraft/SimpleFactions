package net.tfminecraft.simplefactions.vehicles.berth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.VehicleGiveConsentRequest;
import net.tfminecraft.simplefactions.vehicles.registry.FakeOwnedInventory;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;

class FactionVehicleGiveServiceTest {
    private Path tempDir;
    private List<Faction> previousFactions;
    private PlayerVehicleRegistry registry;
    private List<String> assigned;
    private FactionVehicleGiveService giveService;
    private Faction faction;
    private Player leader;
    private Player recipient;
    private UUID leaderUuid;
    private UUID recipientUuid;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-vehicle-give-");
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 2
            default-upkeep: 1
            default-per-person: 1

            categories:
              land_vehicles:
                coal_car:
                  size: 1
                  upkeep: 1
                  per-person: 1
              ships: {}
              static_emplacements: {}
              aircraft: {}
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory());

        previousFactions = FactionManager.factions;
        FactionManager.factions = new ArrayList<>();

        registry = new PlayerVehicleRegistry();
        assigned = new ArrayList<>();
        FactionVehicleReleaseService releaseService = new FactionVehicleReleaseService(registry, (uuid, name) -> {
            assigned.add(uuid + ":" + name);
            return true;
        });
        giveService = new FactionVehicleGiveService(releaseService);

        leaderUuid = UUID.randomUUID();
        recipientUuid = UUID.randomUUID();
        leader = player(leaderUuid, "Leader");
        recipient = player(recipientUuid, "Bob");

        faction = mock(Faction.class);
        when(faction.getId()).thenReturn("red");
        when(faction.getLeader()).thenReturn("Leader");
        when(faction.getOrCreateMainGuild()).thenReturn(mock(net.tfminecraft.simplefactions.guild.Guild.class));
        FactionManager.factions.add(faction);
    }

    @AfterEach
    void tearDown() throws IOException {
        RequestManager.remove(recipient);
        FactionManager.factions = previousFactions;
        VehicleOwnershipQueries.setSourceForTests(null);
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void accept_makesTheVehicleTheRecipientsPersonalVehicle() {
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(), "car-1", "coal_car", OwnershipMode.POOL, null, "red"));
        List<String> leaderMessages = messagesOf(leader);
        List<String> recipientMessages = messagesOf(recipient);

        SimpleFactions plugin = savingPlugin();
        when(plugin.getFactionVehicleGiveService()).thenReturn(giveService);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<SimpleFactions> factions = mockStatic(SimpleFactions.class)) {
            factions.when(SimpleFactions::getInstance).thenReturn(plugin);
            bukkit.when(() -> Bukkit.getPlayer(leaderUuid)).thenReturn(leader);

            giveService.offer(leader, recipient, faction, "car-1", "coal_car");
            assertTrue(RequestManager.getRequest(recipient) instanceof VehicleGiveConsentRequest);

            RequestManager.accept(recipient);

            assertEquals(List.of("car-1:Bob"), assigned);
            assertFalse(registry.getByVehicleUuid("car-1").isPresent());
            assertTrue(recipientMessages.stream().anyMatch(line -> line.contains("personal vehicle")));
            assertTrue(leaderMessages.stream().anyMatch(line -> line.contains("Bob")));
            assertFalse(RequestManager.hasRequest(recipient));
        }
    }

    @Test
    void accept_refusesAnExpiredRequest() {
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(), "car-1", "coal_car", OwnershipMode.POOL, null, "red"));
        List<String> recipientMessages = messagesOf(recipient);
        VehicleGiveConsentRequest expired = org.mockito.Mockito.spy(new VehicleGiveConsentRequest(
                null, "red", "car-1", "coal_car", recipient.getUniqueId(), leaderUuid, "Leader"));
        org.mockito.Mockito.doReturn(true).when(expired).timedOut();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<SimpleFactions> factions = mockStatic(SimpleFactions.class)) {
            factions.when(SimpleFactions::getInstance).thenReturn(savingPlugin());
            bukkit.when(() -> Bukkit.getPlayer(leaderUuid)).thenReturn(leader);
            messagesOf(leader);

            RequestManager.addRequest(leader, recipient, expired);
            giveService.acceptRequest(recipient);

            assertTrue(registry.getByVehicleUuid("car-1").isPresent());
            assertTrue(assigned.isEmpty());
            assertEquals(List.of(FactionVehicleReleaseMessages.giveExpired()), recipientMessages);
        }
    }

    @Test
    void accept_refusesWhenTheRecipientHasNoRoom() {
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(), "car-1", "coal_car", OwnershipMode.POOL, null, "red"));
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("owned", "coal_car", "player_Bob"));
        List<String> recipientMessages = messagesOf(recipient);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<SimpleFactions> factions = mockStatic(SimpleFactions.class)) {
            factions.when(SimpleFactions::getInstance).thenReturn(savingPlugin());
            bukkit.when(() -> Bukkit.getPlayer(leaderUuid)).thenReturn(leader);
            messagesOf(leader);

            giveService.offer(leader, recipient, faction, "car-1", "coal_car");
            giveService.acceptRequest(recipient);

            assertTrue(registry.getByVehicleUuid("car-1").isPresent());
            assertTrue(assigned.isEmpty());
            assertTrue(recipientMessages.stream().anyMatch(line -> line.contains("Bob") || line.contains("maximum")));
        }
    }

    private static Player player(UUID uuid, String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private static List<String> messagesOf(Player player) {
        List<String> messages = new ArrayList<>();
        doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(player).sendMessage(anyString());
        return messages;
    }

    /** A plugin mock whose registry saves succeed. */
    private static SimpleFactions savingPlugin() {
        // A default answer rather than when(), so it is safe inside another stubbing call.
        return org.mockito.Mockito.mock(SimpleFactions.class, invocation ->
                invocation.getMethod().getReturnType() == boolean.class
                        ? Boolean.TRUE
                        : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation));
    }
}
