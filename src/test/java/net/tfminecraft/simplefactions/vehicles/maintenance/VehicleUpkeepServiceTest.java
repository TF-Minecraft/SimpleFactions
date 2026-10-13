package net.tfminecraft.simplefactions.vehicles.maintenance;



import net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceStore;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleUpkeepService;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleHealthDecayApi;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyString;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;

class VehicleUpkeepServiceTest {
    private Path tempDir;
    private PlayerVehicleRegistry registry;
    private PlayerEconomyManager economyManager;
    private TestPlayerBank bank;
    private VehicleMaintenanceStore store;
    private RecordingDecayApi decayApi;
    private VehicleUpkeepService service;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-vehicle-upkeep-");
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());

        registry = new PlayerVehicleRegistry();
        economyManager = new PlayerEconomyManager();
        bank = new TestPlayerBank();
        store = new VehicleMaintenanceStore();
        decayApi = new RecordingDecayApi();
        service = new VehicleUpkeepService(registry, economyManager, bank, store, decayApi);
        FakeOwnerApi.install();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> path.toFile().delete());
        }
    }











    @Test
    void startupDefaultServiceDoesNotWarnOutsideThePrechargeWindow() {
        var defaultService = new VehicleUpkeepService(registry, economyManager);
        Player player = mock(Player.class);
        defaultService.warnBankShortfalls(List.of(player), 0);
        defaultService.warnBankShortfalls(List.of(player), -1);
        verify(player, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void warnsPlayerWhoseBankCannotCoverUpkeep() {
        UUID playerUuid = net.tfminecraft.simplefactions.testsupport.NameKeyedMembers.characterOf("Alice");
        bank.setBalance(playerUuid, 5.0);
        bank.remember("Alice", playerUuid);
        FakeOwnerApi.install().stored("vehicle-1", "ironclad", "player_Alice");
        Player alice = onlinePlayer("Alice", playerUuid);
        registry.register(new PlayerVehicleRecord(playerUuid, "vehicle-1", "ironclad", OwnershipMode.PERSONAL, null));

        service.warnBankShortfalls(List.of(alice), 3 * 3600 + 12 * 60);

        verify(alice).sendMessage("§cYou lack 15.00 denars in your personal bank to pay vehicle maintenance"
                + " in 3h 12m §7(/deco deposit for the bank, it does not count the pouch)");
    }

    @Test
    void doesNotWarnWhenBankCoversUpkeep() {
        UUID playerUuid = net.tfminecraft.simplefactions.testsupport.NameKeyedMembers.characterOf("Alice");
        bank.setBalance(playerUuid, 20.0);
        bank.remember("Alice", playerUuid);
        FakeOwnerApi.install().stored("vehicle-1", "ironclad", "player_Alice");
        Player alice = onlinePlayer("Alice", playerUuid);
        registry.register(new PlayerVehicleRecord(playerUuid, "vehicle-1", "ironclad", OwnershipMode.PERSONAL, null));

        service.warnBankShortfalls(List.of(alice), 600);

        verify(alice, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doesNotWarnPlayersWithoutVehicles() {
        UUID playerUuid = net.tfminecraft.simplefactions.testsupport.NameKeyedMembers.characterOf("Alice");
        bank.remember("Alice", playerUuid);
        Player alice = onlinePlayer("Alice", playerUuid);
        registry.register(new PlayerVehicleRecord(playerUuid, "vehicle-1", "ironclad", OwnershipMode.PERSONAL, null));

        service.warnBankShortfalls(List.of(alice), 600);

        verify(alice, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    private static Player onlinePlayer(String name, UUID uuid) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    @Test
    void hourlyDecaySparesADeadPayersVehicleWhileItWaitsForTheEstate() {
        UUID dead = UUID.randomUUID();
        UUID living = UUID.randomUUID();
        registry.register(new PlayerVehicleRecord(dead, "inherited", "ironclad", OwnershipMode.PERSONAL, null));
        registry.register(new PlayerVehicleRecord(living, "owned", "ironclad", OwnershipMode.PERSONAL, null));
        registry.register(new PlayerVehicleRecord(null, "orphan", "ironclad", OwnershipMode.PERSONAL, null));
        registry.register(new PlayerVehicleRecord(dead, "pooled", "ironclad", OwnershipMode.POOL, null));
        for (String vehicle : List.of("inherited", "owned", "orphan", "pooled", "unregistered")) store.markUnpaid(vehicle, 1L);
        CharacterRef deadRef = new CharacterRef(UUID.randomUUID(), dead);
        try (var members = mockStatic(net.tfminecraft.simplefactions.identity.Members.class, CALLS_REAL_METHODS);
             var estates = mockStatic(net.tfminecraft.tlibs.character.estate.Estates.class)) {
            members.when(() -> net.tfminecraft.simplefactions.identity.Members.info(anyString())).thenReturn(Optional.empty());
            members.when(() -> net.tfminecraft.simplefactions.identity.Members.info(dead.toString())).thenReturn(Optional.of(
                    new CharacterInfo(deadRef, "Dead", CharacterStatus.DEAD, null, Set.of(), 0, Instant.EPOCH)));
            estates.when(() -> net.tfminecraft.tlibs.character.estate.Estates.stateOf(dead))
                    .thenReturn(Optional.of(net.tfminecraft.tlibs.character.estate.EstateState.OPEN));
            service.tickHourlyDecay();
            assertEquals(Set.of("owned", "orphan", "pooled", "unregistered"),
                    decayApi.calls.stream().map(call -> call.uuid).collect(java.util.stream.Collectors.toSet()));
            assertTrue(store.isUnpaid("inherited"), "the marker stays until the recipient pays");
        }
    }

    @Test
    void hourlyDecayDamagesUnpaidVehicles() {
        store.markUnpaid("vehicle-1", 1L);
        service.tickHourlyDecay();
        assertEquals(1, decayApi.calls.size());
        assertEquals("vehicle-1", decayApi.calls.get(0).uuid);
        assertEquals(0.20, decayApi.calls.get(0).fractionOfMax);
        assertEquals(0.03, decayApi.calls.get(0).minHealthFraction);
    }















    private static Faction factionWithInstallation(String id, String installationId, Bank factionBank) {
        net.tfminecraft.simplefactions.installation.handler.InstallationHandler handler =
                mock(net.tfminecraft.simplefactions.installation.handler.InstallationHandler.class);
        when(handler.getById(installationId))
                .thenReturn(mock(net.tfminecraft.simplefactions.installation.Installation.class));
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn(id);
        when(faction.getBank()).thenReturn(factionBank);
        when(faction.getLeader()).thenReturn("Alice");
        when(faction.getInstallationHandler()).thenReturn(handler);
        return faction;
    }

    // Existing configuration identifies offline profiles by player name, not UUID.
    @SuppressWarnings("deprecation")
    private static MockedStatic<Bukkit> mockBukkit(String playerName, UUID playerUuid) {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        Server server = mock(Server.class);
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.getUniqueId()).thenReturn(playerUuid);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(() -> Bukkit.getPlayerExact(playerName)).thenReturn(null);
        bukkit.when(() -> Bukkit.getOfflinePlayer(playerName)).thenReturn(offline);
        bukkit.when(() -> Bukkit.getPlayer(playerUuid)).thenReturn(null);
        return bukkit;
    }

    private static final class RecordingDecayApi implements VehicleHealthDecayApi {
        private final List<DecayCall> calls = new ArrayList<>();

        @Override
        public boolean unloadedDamage(String vehicleUuid, double fractionOfMax, double minHealthFraction) {
            calls.add(new DecayCall(vehicleUuid, fractionOfMax, minHealthFraction));
            return true;
        }
    }

    private static final class DecayCall {
        private final String uuid;
        private final double fractionOfMax;
        private final double minHealthFraction;

        private DecayCall(String uuid, double fractionOfMax, double minHealthFraction) {
            this.uuid = uuid;
            this.fractionOfMax = fractionOfMax;
            this.minHealthFraction = minHealthFraction;
        }
    }

    private static final class TestPlayerBank implements PlayerBank {
        private final Map<UUID, Double> balances = new HashMap<>();

        private final Map<String, UUID> names = new HashMap<>();

        void setBalance(UUID playerUuid, double balance) {
            balances.put(playerUuid, balance);
        }

        void remember(String playerName, UUID playerUuid) {
            names.put(playerName.toLowerCase(java.util.Locale.ROOT), playerUuid);
        }

        @Override
        public UUID resolve(String playerName) {
            if (playerName == null) {
                return null;
            }
            return names.get(playerName.toLowerCase(java.util.Locale.ROOT));
        }

        @Override
        public double getBankBalance(UUID playerUuid) {
            return balances.getOrDefault(playerUuid, 0.0);
        }
        public boolean withdrawFromBank(UUID playerUuid, double amount) {
            double balance = getBankBalance(playerUuid);
            if (balance < amount) {
                return false;
            }
            balances.put(playerUuid, balance - amount);
            return true;
        }
        public boolean depositToBank(UUID playerUuid, double amount) {
            if (playerUuid == null || amount <= 0.0) {
                return false;
            }
            balances.put(playerUuid, getBankBalance(playerUuid) + amount);
            return true;
        }
    }
}
