package net.tfminecraft.simplefactions.vehicles.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.VehicleFeeHandler;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import net.tfminecraft.simplefactions.vehicles.registry.FakeOwnedInventory;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;

/** Vehicle tax is charged in the same withdrawal as upkeep. */
class VehicleUpkeepTaxTest {
    private Path tempDir;
    private PlayerEconomyManager economyManager;
    private MapBank bank;
    private VehicleMaintenanceStore store;
    private VehicleUpkeepService service;
    private Bank factionBank;
    private Ledger factionLedger;
    private final UUID alice = UUID.randomUUID();

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-vehicle-tax-");
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());

        Faction faction = mock(Faction.class);
        when(faction.hasFactionRule(any(Rules.class))).thenReturn(true);
        when(faction.getLeader()).thenReturn("Leader");
        VehicleFeeHandler handler = new VehicleFeeHandler(faction);
        handler.applyBracket(FeeKind.VEHICLE_TAX, new Bracket(0, 100));
        handler.setRate(FeeKind.VEHICLE_TAX, null, 50);
        when(faction.getVehicleFeeHandler()).thenReturn(handler);
        factionBank = mock(Bank.class);
        when(faction.getBank()).thenReturn(factionBank);
        Guild guild = mock(Guild.class);
        when(guild.isBase()).thenReturn(true);
        factionLedger = new Ledger(guild);
        when(guild.getLedger()).thenReturn(factionLedger);
        when(faction.getOrCreateMainGuild()).thenReturn(guild);

        bank = new MapBank();
        bank.names.put("alice", alice);
        VehicleFeeService.setForTests(name -> name.equalsIgnoreCase("Alice") ? faction : null, bank);
        economyManager = new PlayerEconomyManager();
        store = new VehicleMaintenanceStore();
        service = new VehicleUpkeepService(
                new PlayerVehicleRegistry(), economyManager, bank, store, (uuid, fraction, min) -> true);
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("vehicle-1", "ironclad", "player_Alice"));
    }

    @AfterEach
    void tearDown() throws IOException {
        VehicleFeeService.setForTests(null, null);
        VehicleOwnershipQueries.setSourceForTests(null);
        Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }

    @Test
    void taxIsChargedWithUpkeepAndPaidToTheFaction() {
        bank.balances.put(alice, 100.0);

        runDay();

        assertEquals(70.0, bank.balances.get(alice));
        assertEquals(-20.0, economyManager.getLedger(alice).getAmount(PlayerCashflow.VEHICLE_UPKEEP));
        verify(factionBank).deposit(10.0);
        assertEquals(10.0, factionLedger.getVehicleFeeIncome());
        assertFalse(store.isUnpaid("vehicle-1"));
    }

    @Test
    void missingTheTaxMissesUpkeepToo() {
        bank.balances.put(alice, 25.0);

        runDay();

        assertEquals(25.0, bank.balances.get(alice));
        verify(factionBank, never()).deposit(any());
        assertTrue(store.isUnpaid("vehicle-1"));
    }

    private void runDay() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            service.processDailyUpkeep();
        }
    }

    private static final class MapBank implements PlayerBank {
        final Map<UUID, Double> balances = new HashMap<>();
        final Map<String, UUID> names = new HashMap<>();

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
            balances.put(playerUuid, getBankBalance(playerUuid) + amount);
            return true;
        }

        @Override
        public UUID resolve(String playerName) {
            return playerName == null ? null : names.get(playerName.toLowerCase());
        }
    }
}
