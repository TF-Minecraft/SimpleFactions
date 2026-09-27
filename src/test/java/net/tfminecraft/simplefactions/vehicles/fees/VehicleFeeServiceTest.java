package net.tfminecraft.simplefactions.vehicles.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.VehicleFeeHandler;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService.Quote;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;

class VehicleFeeServiceTest {
    private Path tempDir;
    private Faction faction;
    private VehicleFeeHandler handler;
    private Bank factionBank;
    private Ledger ledger;
    private Guild guild;
    private MapBank bank;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-vehicle-fees-");
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            categories:
              ships:
                cruiser:
                  upkeep: 40
                  size: 1
                sloop:
                  upkeep: 8
                  size: 1
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());

        faction = mock(Faction.class);
        when(faction.hasFactionRule(any(Rules.class))).thenReturn(true);
        when(faction.getLeader()).thenReturn("Leader");
        when(faction.getId()).thenReturn("rome");
        handler = new VehicleFeeHandler(faction);
        when(faction.getVehicleFeeHandler()).thenReturn(handler);
        factionBank = mock(Bank.class);
        when(factionBank.getWealth()).thenReturn(1000.0);
        when(faction.getBank()).thenReturn(factionBank);
        guild = mock(Guild.class);
        when(guild.isBase()).thenReturn(true);
        ledger = new Ledger(guild);
        when(guild.getLedger()).thenReturn(ledger);
        when(faction.getOrCreateMainGuild()).thenReturn(guild);

        bank = new MapBank();
        Map<String, Faction> members = new HashMap<>();
        members.put("alice", faction);
        members.put("leader", faction);
        VehicleFeeService.setForTests(name -> members.get(name.toLowerCase()), bank);
    }

    @AfterEach
    void tearDown() throws IOException {
        VehicleFeeService.setForTests(null, null);
        Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }

    @Test
    void taxIsAPercentageAndFeesAMultipleOfUpkeep() {
        handler.applyBracket(FeeKind.VEHICLE_TAX, new Bracket(0, 50));
        handler.setRate(FeeKind.VEHICLE_TAX, null, 25);
        handler.applyBracket(FeeKind.REGISTRATION_FEE, new Bracket(0, 5));
        handler.setRate(FeeKind.REGISTRATION_FEE, null, 1.5);

        assertEquals(10.0, VehicleFeeService.quote(FeeKind.VEHICLE_TAX, "Alice", "cruiser").amount());
        assertEquals(60.0, VehicleFeeService.quote(FeeKind.REGISTRATION_FEE, "Alice", "cruiser").amount());
    }

    @Test
    void leadersAndFactionlessPlayersOweNothing() {
        handler.applyBracket(FeeKind.TRANSFER_FEE, new Bracket(1, 5));

        assertNull(VehicleFeeService.quote(FeeKind.TRANSFER_FEE, "Leader", "cruiser"));
        assertNull(VehicleFeeService.quote(FeeKind.TRANSFER_FEE, "Nomad", "cruiser"));
        assertNotNull(VehicleFeeService.quote(FeeKind.TRANSFER_FEE, "Alice", "cruiser"));
    }

    @Test
    void zeroRateOwesNothing() {
        handler.applyBracket(FeeKind.REGISTRATION_FEE, new Bracket(0, 5));

        assertNull(VehicleFeeService.quote(FeeKind.REGISTRATION_FEE, "Alice", "cruiser"));
    }

    @Test
    void collectMovesMoneyToTheFactionAndItsLedger() {
        handler.applyBracket(FeeKind.REGISTRATION_FEE, new Bracket(2, 5));
        UUID alice = UUID.randomUUID();
        bank.balances.put(alice, 100.0);
        Quote quote = VehicleFeeService.quote(FeeKind.REGISTRATION_FEE, "Alice", "cruiser");

        assertTrue(VehicleFeeService.collect(alice, quote));

        assertEquals(20.0, bank.balances.get(alice));
        verify(factionBank).deposit(80.0);
        assertEquals(80.0, ledger.getVehicleFeeIncome());
    }

    @Test
    void collectMovesNothingWhenThePayerCannotCoverIt() {
        handler.applyBracket(FeeKind.REGISTRATION_FEE, new Bracket(2, 5));
        UUID alice = UUID.randomUUID();
        bank.balances.put(alice, 50.0);
        Quote quote = VehicleFeeService.quote(FeeKind.REGISTRATION_FEE, "Alice", "cruiser");

        assertFalse(VehicleFeeService.collect(alice, quote));

        assertEquals(50.0, bank.balances.get(alice));
        verify(factionBank, never()).deposit(any());
        assertEquals(0.0, ledger.getVehicleFeeIncome());
    }

    private static final class MapBank implements PlayerBank {
        final Map<UUID, Double> balances = new HashMap<>();

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
            return null;
        }
    }
}
