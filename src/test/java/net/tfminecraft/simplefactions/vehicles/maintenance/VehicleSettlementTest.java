package net.tfminecraft.simplefactions.vehicles.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.economy.settlement.RealmSettlementChanges;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.registry.*;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class VehicleSettlementTest {
    @TempDir Path folder;
    final CharacterRef payer = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    final VehicleMaintenanceStore maintenance = new VehicleMaintenanceStore();
    final Map<String, Long> balances = new LinkedHashMap<>();
    final Map<String, Long> legs = new LinkedHashMap<>();
    final RealmSettlementChanges changes = new RealmSettlementChanges();
    MockedStatic<Denars> denars;
    MockedStatic<Members> members;
    MockedStatic<VehicleFeeService> fees;
    MockedStatic<FactionVehiclePoolService> pools;
    FakeOwnedInventory inventory;

    @BeforeEach void setup() throws Exception {
        Path config = folder.resolve("vehicles.yml");
        Files.writeString(config, "categories:\n  ships:\n    ship:\n      upkeep: 20\n      size: 1\n    free:\n      upkeep: 0\n      size: 1\n");
        VehiclesConfigLoader.load(config.toFile());
        inventory = new FakeOwnedInventory();
        VehicleOwnershipQueries.setSourceForTests(inventory);
        denars = mockStatic(Denars.class, CALLS_REAL_METHODS);
        denars.when(() -> Denars.balance(anyString())).thenReturn(10000L);
        members = mockStatic(Members.class, CALLS_REAL_METHODS);
        members.when(() -> Members.info(any())).thenReturn(Optional.empty());
        members.when(() -> Members.info(payer.characterId().toString())).thenReturn(Optional.of(new CharacterInfo(
                payer, "Payer", CharacterStatus.ALIVE, null, Set.of(), 0, Instant.EPOCH)));
        members.when(() -> Members.online(any())).thenReturn(Optional.empty());
        fees = mockStatic(VehicleFeeService.class);
        pools = mockStatic(FactionVehiclePoolService.class);
    }

    @AfterEach void close() {
        pools.close(); fees.close(); members.close(); denars.close();
        VehicleOwnershipQueries.setSourceForTests(null);
    }

    PlayerVehicleRecord vehicle(String id, String type, UUID character, OwnershipMode mode) {
        var record = new PlayerVehicleRecord(character, id, type, mode, "fort", "realm");
        registry.register(record);
        inventory.add(id, type, "player_UnrelatedAccountTag");
        return record;
    }

    Faction faction() {
        Faction faction = mock(Faction.class);
        Guild guild = mock(Guild.class);
        when(faction.getId()).thenReturn("realm");
        when(faction.getLeader()).thenReturn(payer.characterId().toString());
        when(faction.getBank()).thenReturn(mock(Bank.class));
        when(faction.getOrCreateMainGuild()).thenReturn(guild);
        var handler = mock(net.tfminecraft.simplefactions.objects.handler.GuildHandler.class);
        when(faction.getGuildHandler()).thenReturn(handler);
        when(handler.getGuild("realm")).thenReturn(guild);
        Bank bank = faction.getBank();
        when(guild.getBank()).thenReturn(bank);
        UUID incarnation = UUID.randomUUID();
        when(guild.getIncarnationId()).thenReturn(incarnation);
        RealmSettlementChanges.GuildChange saved = new RealmSettlementChanges.GuildChange();
        saved.id = "realm"; saved.incarnation = incarnation;
        changes.guilds.add(saved);
        return faction;
    }

    void calculate() { VehicleSettlement.calculate(balances, legs, changes, registry, maintenance, 1000); }
    JsonObject after() { return changes.vehicleChanges.getAsJsonObject("maintenance"); }

    @Test void registryCharacterPaysUpkeepAndTaxAtomicallyUsingEarlierSettlementIncome() {
        vehicle("v", "ship", payer.characterId(), OwnershipMode.PERSONAL);
        maintenance.markUnpaid("v", 1);
        Faction faction = faction();
        var quote = new VehicleFeeService.Quote(FeeKind.VEHICLE_TAX, "ship", faction, 25, 5);
        fees.when(() -> VehicleFeeService.quote(FeeKind.VEHICLE_TAX, payer.characterId().toString(), "ship")).thenReturn(quote);
        String account = Denars.bank(payer);
        denars.when(() -> Denars.balance(account)).thenReturn(0L);
        legs.put(account, 5000L);
        legs.put(Denars.MINT, -5000L);
        String treasury = Denars.guild(changes.guilds.getFirst().incarnation);
        balances.put(treasury, 1000L);
        calculate();
        assertEquals(2500L, balances.get(account));
        assertEquals(2500L, legs.get(account));
        assertEquals(1500L, balances.get(treasury));
        assertEquals(500L, legs.get(treasury));
        assertEquals(-3000L, legs.get(Denars.MINT));
        assertEquals(0, legs.values().stream().mapToLong(Long::longValue).sum());
        assertEquals(5, changes.guilds.getFirst().vehicleFeeIncome);
        assertEquals(Map.of("VEHICLE_UPKEEP", -20.0, "VEHICLE_TAX", -5.0), changes.playerIncome.get(payer.characterId()));
        assertTrue(maintenance.isUnpaid("v"));
        assertFalse(after().has("v"));
        denars.verify(() -> Denars.post(anyString(), anyString(), anyList()), never());
    }

    @Test void interruptedOwnershipPaymentQuarantinesUpkeepInsteadOfChargingTheOldPayer() {
        vehicle("v", "ship", payer.characterId(), OwnershipMode.PERSONAL);
        try (var runtime = mockStatic(net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.class)) {
            var payments = mock(net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.class);
            runtime.when(net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments::current).thenReturn(payments);
            when(payments.blockedVehicle("v")).thenReturn(true);
            calculate();
            assertTrue(legs.isEmpty());
            assertEquals(1000, after().get("v").getAsLong());
        }
    }

    @Test void personalShortfallMovesNeitherUpkeepNorTaxAndPreservesOriginalUnpaidDate() {
        vehicle("v", "ship", payer.characterId(), OwnershipMode.PERSONAL);
        maintenance.markUnpaid("v", 1);
        Faction faction = faction();
        fees.when(() -> VehicleFeeService.quote(any(), any(), any())).thenReturn(
                new VehicleFeeService.Quote(FeeKind.VEHICLE_TAX, "ship", faction, 25, 5));
        balances.put(Denars.bank(payer), 2499L);
        calculate();
        assertTrue(legs.isEmpty());
        assertEquals(1, after().get("v").getAsLong());
        assertTrue(changes.playerIncome.isEmpty());
        assertEquals(0, changes.guilds.getFirst().vehicleFeeIncome);
    }

    @Test void missingRegistryCharacterNeverFallsBackToVehicleFrameworkOwner() {
        inventory.add("unknown", "ship", "player_CurrentAccount");
        vehicle("missing", "ship", UUID.randomUUID(), OwnershipMode.PERSONAL);
        vehicle("free", "free", payer.characterId(), OwnershipMode.PERSONAL);
        maintenance.markUnpaid("free", 1);
        calculate();
        assertEquals(1000, after().get("unknown").getAsLong());
        assertEquals(1000, after().get("missing").getAsLong());
        assertFalse(after().has("free"));
        assertTrue(legs.isEmpty());
        denars.verify(() -> Denars.balance(anyString()), never());
    }

    @Test void pooledVehiclesCannotBorrowAndOrphanInstallationsStopDecaying() {
        var orphan = vehicle("orphan", "ship", null, OwnershipMode.INSTALLATION);
        var pool = vehicle("pool", "ship", null, OwnershipMode.POOL);
        var negative = vehicle("negative", "ship", null, OwnershipMode.POOL);
        var paid = vehicle("paid", "ship", null, OwnershipMode.INSTALLATION);
        vehicle("free", "free", null, OwnershipMode.POOL);
        maintenance.markUnpaid(orphan.getVehicleUuid(), 1);
        maintenance.markUnpaid(paid.getVehicleUuid(), 1);
        maintenance.markUnpaid("free", 1);
        Faction debtor = faction(), rich = faction();
        pools.when(() -> FactionVehiclePoolService.payingFaction(negative)).thenReturn(debtor);
        pools.when(() -> FactionVehiclePoolService.payingFaction(paid)).thenReturn(rich);
        String debtAccount = Denars.guild(debtor.getOrCreateMainGuild().getIncarnationId());
        String richAccount = Denars.guild(rich.getOrCreateMainGuild().getIncarnationId());
        balances.put(debtAccount, -100L);
        balances.put(richAccount, 2000L);
        calculate();
        assertEquals(-100L, balances.get(debtAccount));
        assertEquals(0L, balances.get(richAccount));
        assertEquals(-2000L, legs.get(richAccount));
        assertFalse(after().has("orphan"));
        assertFalse(after().has("paid"));
        assertFalse(after().has("free"));
        assertTrue(after().has("pool"));
        assertTrue(after().has("negative"));
        assertTrue(maintenance.isUnpaid("orphan"));
    }

    @Test void durableAfterValuesReplayWithoutChangingUnpaidAgesOrChargingAgain() throws Exception {
        vehicle("v", "ship", null, OwnershipMode.PERSONAL);
        calculate();
        var online = mock(Player.class);
        members.when(() -> Members.online(any())).thenReturn(Optional.of(online));
        VehicleSettlement.apply(changes.vehicleChanges, folder, maintenance);
        VehicleSettlement.apply(changes.vehicleChanges, folder, maintenance);
        assertEquals(Map.of("v", 1000L), maintenance.snapshot());
        assertEquals(1000, DurableJson.read(folder.resolve("Cache/vehicle_maintenance.json"), JsonObject.class).get("v").getAsLong());
        verify(online, times(2)).sendMessage(contains("insufficient bank"));
        denars.verify(() -> Denars.post(anyString(), anyString(), anyList()), never());
    }

    @Test void invalidOrUnwritableAfterValuesDoNotChangeTheLiveStore() throws Exception {
        maintenance.markUnpaid("old", 5);
        assertThrows(IOException.class, () -> VehicleSettlement.apply(new JsonObject(), folder, maintenance));
        JsonObject corrupt = new JsonObject();
        JsonObject map = new JsonObject(); map.addProperty("v", "bad"); corrupt.add("maintenance", map);
        assertThrows(IOException.class, () -> VehicleSettlement.apply(corrupt, folder, maintenance));
        calculate();
        Files.writeString(folder.resolve("blocked"), "file");
        assertThrows(IOException.class, () -> VehicleSettlement.apply(changes.vehicleChanges, folder.resolve("blocked"), maintenance));
        assertEquals(Map.of("old", 5L), maintenance.snapshot());
    }

    @Test void pluginAdaptersUseTheLoadedRegistryAndNotifyTheFactionLeader() throws Exception {
        var record = vehicle("pool", "ship", null, OwnershipMode.POOL);
        Faction faction = faction();
        pools.when(() -> FactionVehiclePoolService.payingFaction(record)).thenReturn(faction);
        balances.put(Denars.guild(changes.guilds.getFirst().incarnation), 0L);
        var plugin = mock(SimpleFactions.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getVehicleMaintenanceStore()).thenReturn(maintenance);
        var player = mock(Player.class);
        members.when(() -> Members.online(payer.characterId().toString())).thenReturn(Optional.of(player));
        try (var plugins = mockStatic(SimpleFactions.class); var factions = mockStatic(FactionManager.class)) {
            plugins.when(SimpleFactions::getInstance).thenReturn(plugin);
            plugins.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
            factions.when(() -> FactionManager.getByString("realm")).thenReturn(faction);
            VehicleSettlement.calculate(balances, legs, changes);
            VehicleSettlement.apply(changes.vehicleChanges);
            verify(player).sendMessage(contains("insufficient bank"));
        }
    }
}
