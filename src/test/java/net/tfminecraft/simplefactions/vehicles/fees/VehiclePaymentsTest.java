package net.tfminecraft.simplefactions.vehicles.fees;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.denareconomy.api.*;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.economy.EconomyRuntime;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.testsupport.TestTreasuries;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceStore;
import net.tfminecraft.tlibs.character.CharacterRef;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;

class VehiclePaymentsTest {
    @TempDir Path folder;
    final CharacterRef payer = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final UUID incarnation = UUID.randomUUID();
    VehicleFeeStore store;
    VehicleMaintenanceStore maintenance;
    VehiclePayments payments;
    TestTreasuries treasury;
    MockedStatic<org.bukkit.Bukkit> bukkit;
    MockedStatic<FactionManager> factions;
    MockedConstruction<Database> database;
    Faction faction;
    Guild guild;
    Ledger ledger;
    boolean saves = true;

    @BeforeEach void setup() throws Exception {
        SimpleFactions.plugin = null;
        EconomyRuntime.recovered();
        bukkit = mockStatic(org.bukkit.Bukkit.class);
        bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
        treasury = new TestTreasuries();
        factions = mockStatic(FactionManager.class);
        faction = mock(Faction.class);
        guild = mock(Guild.class);
        var handler = mock(GuildHandler.class);
        when(faction.getId()).thenReturn("realm");
        when(faction.getBank()).thenReturn(mock(Bank.class));
        when(faction.getGuildHandler()).thenReturn(handler);
        when(handler.getGuild("realm")).thenReturn(guild);
        when(guild.getFaction()).thenReturn(faction);
        when(guild.getIncarnationId()).thenReturn(incarnation);
        ledger = new Ledger(guild);
        when(guild.getLedger()).thenReturn(ledger);
        factions.when(() -> FactionManager.getByString("realm")).thenReturn(faction);
        database = mockConstruction(Database.class, (value, context) -> when(value.saveFactionDurably(any())).thenAnswer(call -> saves));
        store = new VehicleFeeStore(); store.bind(folder.resolve("Cache").toFile());
        maintenance = new VehicleMaintenanceStore();
        VehiclePayments.startup(folder, store, maintenance);
        payments = VehiclePayments.current();
        TestTreasuries.set(Denars.bank(payer), 10000);
    }
    @AfterEach void close() {
        VehiclePayments.stop(); database.close(); factions.close(); treasury.close(); bukkit.close(); EconomyRuntime.recovered();
    }
    VehicleFeeService.Quote quote() { return new VehicleFeeService.Quote(FeeKind.REGISTRATION_FEE, "ship", faction, 2, 80); }

    @Test void chargeIsAtomicDurableAndPendingPhysicalActionCannotChargeAgain() throws Exception {
        assertEquals(Result.APPLIED, payments.charge("build:station", "fee", payer, quote()));
        assertEquals(2000, Denars.balance(Denars.bank(payer)));
        assertEquals(8000, Denars.balance(Denars.guild(incarnation)));
        assertEquals(80, ledger.getVehicleFeeIncome());
        assertEquals(Result.CONFLICT, payments.charge("build:station", "another-request", payer, quote()));
        assertTrue(payments.ready());
        VehicleFeeStore reopened = new VehicleFeeStore(); reopened.bind(folder.resolve("Cache").toFile()); reopened.load();
        assertEquals(payer, reopened.receipt("build:station").payer());
        VehiclePayments.startup(folder, reopened, maintenance);
        assertEquals("fee", VehiclePayments.current().receipt("build:station").id());
        assertEquals(2000, Denars.balance(Denars.bank(payer)));
        assertTrue(VehiclePayments.current().complete("build:station", null));
        assertNull(reopened.receipt("build:station"));
    }

    @Test void refusalCreatesNoReceiptAndChangesNeitherTreasuryNorBookkeeping() {
        TestTreasuries.set(Denars.bank(payer), 7999);
        assertEquals(Result.REFUSED, payments.charge("claim:v", "fee", payer, quote()));
        assertEquals(7999, Denars.balance(Denars.bank(payer)));
        assertEquals(0, Denars.balance(Denars.guild(incarnation)));
        assertEquals(0, ledger.getVehicleFeeIncome());
        assertFalse(payments.blockedVehicle("v"));
        assertEquals(Result.REFUSED, payments.charge("claim:v", "null", null, quote()));
        assertEquals(Result.REFUSED, payments.charge("claim:v", "null", payer, null));
    }

    @Test void appliedPostingWithFailedDomainSaveRecoversExactValuesWithoutSecondDebit() throws Exception {
        saves = false;
        assertEquals(Result.UNAVAILABLE, payments.charge("handover:v", "fee", payer, quote()));
        assertEquals(2000, Denars.balance(Denars.bank(payer)));
        assertFalse(payments.ready());
        assertEquals(Result.UNAVAILABLE, payments.charge("other", "new", payer, quote()));
        saves = true;
        VehiclePayments.startup(folder, store, maintenance);
        assertTrue(VehiclePayments.current().blockedVehicle("v"));
        assertEquals(80, ledger.getVehicleFeeIncome());
        assertEquals(2000, Denars.balance(Denars.bank(payer)));
        assertEquals(8000, Denars.balance(Denars.guild(incarnation)));
    }

    @Test void refundUsesCapturedCharacterAndOriginalIncarnationAfterNameRecreation() {
        assertEquals(Result.APPLIED, payments.charge("build:s", "fee", payer, quote()));
        TestTreasuries.set(Denars.guild(incarnation), 3000);
        UUID replacement = UUID.randomUUID();
        when(guild.getIncarnationId()).thenReturn(replacement);
        TestTreasuries.set(Denars.guild(replacement), 9000);
        var refund = payments.refund("build:s");
        assertEquals(Result.APPLIED, refund.result());
        assertEquals(30, refund.amount());
        assertEquals(5000, Denars.balance(Denars.bank(payer)));
        assertEquals(0, Denars.balance(Denars.guild(incarnation)));
        assertEquals(9000, Denars.balance(Denars.guild(replacement)));
        assertEquals(80, ledger.getVehicleFeeIncome());
        assertEquals(Result.REFUSED, payments.refund("build:s").result());
    }

    @Test void fullAndZeroRefundsFinishTheOriginalBuildWithoutDebt() {
        assertEquals(Result.APPLIED, payments.charge("build:s", "fee", payer, quote()));
        assertEquals(80, payments.refund("build:s").amount());
        assertEquals(0, ledger.getVehicleFeeIncome());
        assertEquals(10000, Denars.balance(Denars.bank(payer)));
        assertEquals(Result.APPLIED, payments.charge("build:z", "fee2", payer, quote()));
        TestTreasuries.set(Denars.guild(incarnation), 0);
        assertEquals(Result.APPLIED, payments.refund("build:z").result());
        assertNull(payments.receipt("build:z"));
        assertEquals(0, Denars.balance(Denars.guild(incarnation)));
    }

    @Test void maintenanceDebitsOnlySelectedCharacterAccountAndClearsAfterApplied() throws Exception {
        TestTreasuries.set(Denars.pouch(payer), 2500);
        maintenance.markUnpaid("v", 1);
        assertEquals(Result.APPLIED, payments.maintain("maintenance", payer, "v", false, 2000));
        assertEquals(500, Denars.balance(Denars.pouch(payer)));
        assertEquals(10000, Denars.balance(Denars.bank(payer)));
        assertFalse(maintenance.isUnpaid("v"));
        assertEquals(Result.REFUSED, payments.maintain("new", payer, "v", false, 2000));
        maintenance.markUnpaid("b", 2);
        assertEquals(Result.REFUSED, payments.maintain("short", payer, "b", false, 2000));
        assertTrue(maintenance.isUnpaid("b"));
        assertEquals(Result.APPLIED, payments.maintain("bank", payer, "b", true, 2000));
        assertEquals(8000, Denars.balance(Denars.bank(payer)));
        maintenance.markUnpaid("free", 3);
        assertEquals(Result.APPLIED, payments.maintain("free", payer, "free", true, 0));
        assertFalse(maintenance.isUnpaid("free"));
    }

    @Test void originalLegacyBuildCapturesHeirAndIncarnationOnce() throws Exception {
        store.putPaidBuild("legacy", new VehicleFeeStore.PaidBuild(payer.characterId(), "realm", 25));
        try (var members = mockStatic(Members.class)) {
            members.when(() -> Members.ref(payer.characterId().toString())).thenReturn(Optional.of(payer));
            payments.recover();
            assertEquals(incarnation, payments.receipt("build:legacy").guildIncarnation());
            assertEquals(payer, payments.receipt("build:legacy").payer());
            members.when(() -> Members.ref(payer.characterId().toString())).thenReturn(Optional.empty());
            payments.recover();
            assertEquals(2500, payments.receipt("build:legacy").cents());
        }
        assertTrue(payments.complete("build:legacy", null));
        assertTrue(store.pendingBuilds().isEmpty());
    }

    @Test void failedReceiptCompletionKeepsTheDurableScopeForRestartReview() throws Exception {
        assertEquals(Result.APPLIED, payments.charge("build:s", "fee", payer, quote()));
        Path blocked = folder.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        store.bind(blocked.toFile());
        assertFalse(payments.complete("build:s", null));
        assertFalse(payments.ready());
        var reopened = new VehicleFeeStore(); reopened.bind(folder.resolve("Cache").toFile()); reopened.load();
        assertNotNull(reopened.receipt("build:s"));
        assertEquals(2000, Denars.balance(Denars.bank(payer)));
    }

    @Test void failedPersonalRegistrationPreservesTheOriginalReceipt() {
        assertEquals(Result.APPLIED, payments.charge("claim:v", "fee", payer, quote()));
        assertFalse(payments.personal("v", "ship", null));
        assertFalse(payments.ready());
        assertNotNull(payments.receipt("claim:v"));
    }

    @Test void failedFreeMaintenanceSaveLeavesVehicleUnpaid() throws Exception {
        maintenance.markUnpaid("v", 1);
        Files.createDirectory(folder.resolve("Cache/vehicle_maintenance.json"));
        assertEquals(Result.UNAVAILABLE, payments.maintain("free", payer, "v", true, 0));
        assertTrue(maintenance.isUnpaid("v"));
        assertFalse(payments.ready());
        assertEquals(10000, Denars.balance(Denars.bank(payer)));
    }

    @Test void unsupportedPersistedActionStopsRecoveryWithoutGuessingAnEffect() throws Exception {
        var changes = new com.google.gson.JsonObject();
        changes.addProperty("action", "unsupported");
        changes.addProperty("scope", "claim:v");
        changes.add("receipt", net.tfminecraft.simplefactions.database.JsonUtil.GSON.toJsonTree(
                new VehiclePayments.Receipt("unsupported", payer, "realm", incarnation, FeeKind.REGISTRATION_FEE, 1)));
        var intent = new net.tfminecraft.simplefactions.economy.OperationJournal.Intent("unsupported", "test",
                List.of(new Leg(Denars.bank(payer), -1), new Leg(Denars.guild(incarnation), 1)), changes);
        var journal = new net.tfminecraft.simplefactions.economy.OperationJournal(folder.resolve("vehicle-payments"));
        assertThrows(java.io.IOException.class, () -> journal.execute(intent, ignored -> { throw new java.io.IOException("interrupted"); }));
        assertThrows(java.io.IOException.class, payments::recover);
        assertFalse(payments.ready());
        assertNull(payments.receipt("claim:v"));
        assertEquals(9999, Denars.balance(Denars.bank(payer)));
    }
}
