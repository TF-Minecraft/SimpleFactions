package net.tfminecraft.simplefactions.vehicles.maintenance;



import net.tfminecraft.simplefactions.vehicles.registry.FakeOwnedInventory;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory());
    }

    @AfterEach
    void tearDown() throws IOException {
        VehicleOwnershipQueries.setSourceForTests(null);
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void successfulUpkeepWithdrawsBankAndRecordsLedger() {
        UUID playerUuid = UUID.randomUUID();
        bank.setBalance(playerUuid, 100.0);
        bank.remember("Alice", playerUuid);
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("vehicle-1", "ironclad", "player_Alice"));

        try (MockedStatic<Bukkit> bukkit = mockBukkit("Alice", playerUuid)) {
            service.processDailyUpkeep();
        }

        assertEquals(80.0, bank.getBankBalance(playerUuid));
        assertEquals(-20.0, economyManager.getLedger(playerUuid).getAmount(PlayerCashflow.VEHICLE_UPKEEP));
        assertFalse(store.isUnpaid("vehicle-1"));
    }

    @Test
    void offlineOwnerIsChargedOnTheResolvedAccount() {
        UUID playerUuid = UUID.randomUUID();
        UUID standIn = UUID.randomUUID();
        bank.setBalance(playerUuid, 100.0);
        bank.remember("Alice", playerUuid);
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("vehicle-1", "ironclad", "player_Alice"));

        try (MockedStatic<Bukkit> bukkit = mockBukkit("Alice", standIn)) {
            service.processDailyUpkeep();
        }

        assertEquals(80.0, bank.getBankBalance(playerUuid));
        assertEquals(0.0, bank.getBankBalance(standIn));
        assertEquals(-20.0, economyManager.getLedger(playerUuid).getAmount(PlayerCashflow.VEHICLE_UPKEEP));
        assertFalse(store.isUnpaid("vehicle-1"));
    }

    @Test
    void unknownOwnerIsUnpaidInsteadOfChargedToAStandIn() {
        UUID standIn = UUID.randomUUID();
        bank.setBalance(standIn, 100.0);
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("vehicle-1", "ironclad", "player_Alice"));

        try (MockedStatic<Bukkit> bukkit = mockBukkit("Alice", standIn)) {
            service.processDailyUpkeep();
        }

        assertEquals(100.0, bank.getBankBalance(standIn));
        assertTrue(store.isUnpaid("vehicle-1"));
    }

    @Test
    void insufficientBalanceSkipsCharge() {
        UUID playerUuid = UUID.randomUUID();
        bank.setBalance(playerUuid, 10.0);
        bank.remember("Alice", playerUuid);
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("vehicle-1", "ironclad", "player_Alice"));

        try (MockedStatic<Bukkit> bukkit = mockBukkit("Alice", playerUuid)) {
            service.processDailyUpkeep();
        }

        assertEquals(10.0, bank.getBankBalance(playerUuid));
        assertEquals(0.0, economyManager.getLedger(playerUuid).getAmount(PlayerCashflow.VEHICLE_UPKEEP));
        assertTrue(store.isUnpaid("vehicle-1"));
    }

    @Test
    void successfulUpkeepClearsExistingUnpaid() {
        UUID playerUuid = UUID.randomUUID();
        bank.setBalance(playerUuid, 100.0);
        bank.remember("Alice", playerUuid);
        store.markUnpaid("vehicle-1", 1L);
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("vehicle-1", "ironclad", "player_Alice"));

        try (MockedStatic<Bukkit> bukkit = mockBukkit("Alice", playerUuid)) {
            service.processDailyUpkeep();
        }

        assertFalse(store.isUnpaid("vehicle-1"));
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

    @Test
    void skipsBerthedVehicles() {
        UUID playerUuid = UUID.randomUUID();
        bank.setBalance(playerUuid, 100.0);
        bank.remember("Alice", playerUuid);
        registry.register(new PlayerVehicleRecord(
            playerUuid,
            "vehicle-1",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "installation-1"
        ));
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("vehicle-1", "ironclad", "player_Alice"));

        try (MockedStatic<Bukkit> bukkit = mockBukkit("Alice", playerUuid)) {
            service.processDailyUpkeep();
        }

        assertEquals(100.0, bank.getBankBalance(playerUuid));
        assertEquals(0.0, economyManager.getLedger(playerUuid).getNetDaily());
        assertFalse(store.isUnpaid("vehicle-1"));
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

        @Override
        public boolean withdrawFromBank(UUID playerUuid, double amount) {
            double balance = getBankBalance(playerUuid);
            if (balance < amount) {
                return false;
            }
            balances.put(playerUuid, balance - amount);
            return true;
        }

        @Override
        public boolean depositToBank(UUID playerUuid, double amount) {
            if (playerUuid == null || amount <= 0.0) {
                return false;
            }
            balances.put(playerUuid, getBankBalance(playerUuid) + amount);
            return true;
        }
    }
}
