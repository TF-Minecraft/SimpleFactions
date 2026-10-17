package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.events.FactionLeaderChangedEvent;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.NameKeyedMembers;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeConfirmations;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeMessages;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryClaimListener;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryClaimService;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.vehicleframework.api.OwnershipReason;
import net.tfminecraft.vehicleframework.api.OwnershipValues;
import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.events.VehicleOwnershipChangeEvent;
import net.tfminecraft.vehicleframework.events.VehicleOwnershipChangedEvent;
import net.tfminecraft.vehicleframework.events.VehicleOwnershipEditCheckEvent;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

class VehicleOwnershipGateTest {
    @TempDir Path folder;
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    private final VehicleFeeStore store = new VehicleFeeStore();
    private final VehicleFeeConfirmations confirmations = new VehicleFeeConfirmations();
    private final AtomicInteger saves = new AtomicInteger();
    private final Logger logger = mock(Logger.class);
    private VehicleOwnershipGate gate;
    private MockedStatic<VehiclePayments> paymentsStatic;
    private MockedStatic<VehicleFeeService> fees;
    private VehiclePayments payments;
    private Player player;
    private CharacterRef actor;
    private ActiveVehicle vehicle;
    private OwnerData ownerData;
    private List<Faction> previousFactions;

    @BeforeEach
    void setUp() throws Exception {
        Path config = folder.resolve("vehicles.yml");
        Files.writeString(config, "personal-slot-limit: 3\ncategories:\n  ships:\n    ship:\n      upkeep: 20\n"
                + "      size: 1\n      per-person: 1\n");
        VehiclesConfigLoader.load(config.toFile());
        gate = new VehicleOwnershipGate(registry, new VehicleRegistryClaimService(registry), store, confirmations,
                saves::incrementAndGet, logger);
        payments = mock(VehiclePayments.class);
        when(payments.ready()).thenReturn(true);
        when(payments.personal(anyString(), anyString(), any(), any())).thenReturn(true);
        when(payments.replaceRow(anyString(), any())).thenReturn(true);
        paymentsStatic = mockStatic(VehiclePayments.class);
        paymentsStatic.when(VehiclePayments::current).thenReturn(payments);
        fees = mockStatic(VehicleFeeService.class);
        player = mock(Player.class);
        when(player.getName()).thenReturn("Claimer");
        UUID account = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(account);
        actor = new CharacterRef(account, NameKeyedMembers.characterOf("Claimer"));
        vehicle = mock(ActiveVehicle.class);
        when(vehicle.getUUID()).thenReturn("ship-1");
        when(vehicle.getId()).thenReturn("ship");
        ownerData = new OwnerData();
        when(vehicle.getOwnerData()).thenReturn(ownerData);
        previousFactions = FactionManager.factions;
        FactionManager.factions = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        fees.close();
        paymentsStatic.close();
        FactionManager.factions = previousFactions;
    }

    private VehicleOwnershipChangeEvent change(OwnershipReason reason, boolean staff) {
        return new VehicleOwnershipChangeEvent(player, vehicle, reason, UUID.randomUUID(), actor, staff,
                reason == OwnershipReason.CLAIM ? actor : null, null, "none", "char_" + actor.characterId());
    }

    private VehicleOwnershipChangedEvent changed(boolean success, OwnershipReason reason, UUID operation, boolean staff,
            CharacterRef target) {
        OwnershipValues values = new OwnershipValues("none", List.of(), false, false, null);
        return new VehicleOwnershipChangedEvent(success, reason, operation, UUID.randomUUID(), vehicle, player, actor,
                staff, target, null, values, values);
    }

    private void factionRow() {
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "ship-1", "ship", OwnershipMode.POOL, null, "red"));
    }

    @ParameterizedTest
    @EnumSource(value = OwnershipReason.class, names = {"CLAIM", "CLEAR", "WHITELIST_ADD", "WHITELIST_REMOVE",
        "WHITELIST_TOGGLE", "TICKETS_TOGGLE", "FACTION_ACCESS_TOGGLE", "GUILD_ACCESS_TOGGLE"})
    void everyNonStaffChangeToAFactionVehicleIsRefused(OwnershipReason reason) {
        factionRow();
        VehicleOwnershipChangeEvent event = change(reason, false);

        gate.onChange(event);

        assertTrue(event.isCancelled());
        verify(player).sendMessage(VehicleRegistryClaimListener.FACTION_VEHICLE);
    }

    @ParameterizedTest
    @EnumSource(value = OwnershipReason.class, names = {"ALLY_ACCESS_TOGGLE", "VASSAL_ACCESS_TOGGLE"})
    void theLeaderMayOpenAFactionVehicleToAlliesAndVassals(OwnershipReason reason) {
        factionRow();
        VehicleOwnershipChangeEvent event = change(reason, false);

        gate.onChange(event);
        gate.onClaimFee(event);

        assertFalse(event.isCancelled());
        verifyNoInteractions(player);
    }

    @Test
    void staffChangesPassEvenOnFactionVehiclesAndWithoutReadyPayments() {
        factionRow();
        when(payments.ready()).thenReturn(false);
        VehicleOwnershipChangeEvent takeover = change(OwnershipReason.TAKEOVER, true);
        VehicleOwnershipChangeEvent claim = change(OwnershipReason.CLAIM, true);

        gate.onChange(takeover);
        gate.onChange(claim);
        gate.onClaimFee(claim);

        assertFalse(takeover.isCancelled());
        assertFalse(claim.isCancelled());
        verifyNoInteractions(player);
    }

    @Test
    void personalWhitelistAndTicketChangesAreLeftToVehicleFramework() {
        registry.register(new PlayerVehicleRecord(actor.characterId(), "ship-1", "ship", OwnershipMode.PERSONAL, null));
        VehicleOwnershipChangeEvent event = change(OwnershipReason.WHITELIST_ADD, false);
        gate.onChange(event);
        gate.onClaimFee(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void aClaimNeedsACharacterAndReadyUnblockedPayments() {
        VehicleOwnershipChangeEvent noActor = new VehicleOwnershipChangeEvent(player, vehicle, OwnershipReason.CLAIM,
                UUID.randomUUID(), null, false, null, null, "none", "player_Claimer");
        gate.onChange(noActor);
        assertTrue(noActor.isCancelled());

        when(payments.blockedVehicle("ship-1")).thenReturn(true);
        VehicleOwnershipChangeEvent blocked = change(OwnershipReason.CLAIM, false);
        gate.onChange(blocked);
        assertTrue(blocked.isCancelled());

        when(payments.ready()).thenReturn(false);
        VehicleOwnershipChangeEvent unready = change(OwnershipReason.CLAIM, false);
        gate.onChange(unready);
        assertTrue(unready.isCancelled());

        paymentsStatic.when(VehiclePayments::current).thenReturn(null);
        VehicleOwnershipChangeEvent absent = change(OwnershipReason.CLAIM, false);
        gate.onChange(absent);
        assertTrue(absent.isCancelled());
        verify(player, times(4)).sendMessage(VehicleOwnershipGate.PAYMENT_UNAVAILABLE);
    }

    @Test
    void aClaimCountsTheClaimersAccountAgainstItsSlots() {
        VehicleOwnershipChangeEvent free = change(OwnershipReason.CLAIM, false);
        gate.onChange(free);
        assertFalse(free.isCancelled());

        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "ship-2", "ship", OwnershipMode.PERSONAL, null,
                null, player.getUniqueId()));
        VehicleOwnershipChangeEvent full = change(OwnershipReason.CLAIM, false);
        gate.onChange(full);
        assertTrue(full.isCancelled());
        verify(player).sendMessage(startsWith("§cYou already have the maximum number of ship"));
    }

    @Test
    void aVetoHandlerThatFailsRefusesTheChange() {
        when(vehicle.getUUID()).thenThrow(new IllegalStateException("broken vehicle"));
        VehicleOwnershipChangeEvent high = change(OwnershipReason.CLAIM, false);
        VehicleOwnershipChangeEvent highest = change(OwnershipReason.CLAIM, false);

        gate.onChange(high);
        gate.onClaimFee(highest);

        assertTrue(high.isCancelled());
        assertTrue(highest.isCancelled());
        verify(player, times(2)).sendMessage(VehicleOwnershipGate.FAILED);
        verify(logger, times(2)).log(eq(Level.SEVERE), contains("refused"), any(IllegalStateException.class));
    }

    private VehicleFeeService.Quote quote() {
        Faction faction = mock(Faction.class);
        Guild guild = mock(Guild.class);
        when(guild.getIncarnationId()).thenReturn(UUID.randomUUID());
        when(faction.getOrCreateMainGuild()).thenReturn(guild);
        when(faction.getName()).thenReturn("Red");
        VehicleFeeService.Quote quote = new VehicleFeeService.Quote(FeeKind.TRANSFER_FEE, "ship", faction, 2, 40);
        fees.when(() -> VehicleFeeService.quote(FeeKind.TRANSFER_FEE, "Previous", "ship")).thenReturn(quote);
        return quote;
    }

    @Test
    void claimingSomeoneElsesReleasedVehicleShowsTheFeeThenChargesItUnderTheOperation() {
        VehicleFeeService.Quote quote = quote();
        store.setLastOwner("ship-1", "Previous");
        VehicleOwnershipChangeEvent first = change(OwnershipReason.CLAIM, false);
        gate.onClaimFee(first);
        assertTrue(first.isCancelled());
        verify(player).sendMessage(VehicleFeeMessages.claimConfirm(quote, Members.display("Previous")));
        verify(payments, never()).charge(anyString(), anyString(), any(), any());

        VehicleOwnershipChangeEvent second = change(OwnershipReason.CLAIM, false);
        when(payments.charge("claim:ship-1", "sf:vehicle:claim:" + second.getOperationId(), actor, quote))
                .thenReturn(Result.APPLIED);
        gate.onClaimFee(second);
        assertFalse(second.isCancelled());
        verify(player).sendMessage(VehicleFeeMessages.claimPaid(quote));

        gate.onChanged(changed(true, OwnershipReason.CLAIM, second.getOperationId(), false, actor));
        verify(payments).personal("ship-1", "ship", actor.characterId(), actor.ownerId());
        verify(payments).complete("claim:ship-1", Members.key(actor));
        assertEquals(0, saves.get());
    }

    @Test
    void anUnaffordableClaimFeeRefusesTheClaim() {
        VehicleFeeService.Quote quote = quote();
        store.setLastOwner("ship-1", "Previous");
        gate.onClaimFee(change(OwnershipReason.CLAIM, false));
        VehicleOwnershipChangeEvent second = change(OwnershipReason.CLAIM, false);
        when(payments.charge(anyString(), anyString(), any(), any())).thenReturn(Result.REFUSED);

        gate.onClaimFee(second);

        assertTrue(second.isCancelled());
        verify(player).sendMessage(VehicleFeeMessages.claimUnaffordable(quote));
    }

    @Test
    void ownVehiclesUnknownHistoryAndFeelessFactionsClaimFree() {
        VehicleOwnershipChangeEvent unknown = change(OwnershipReason.CLAIM, false);
        gate.onClaimFee(unknown);
        store.setLastOwner("ship-1", Members.key(actor));
        VehicleOwnershipChangeEvent own = change(OwnershipReason.CLAIM, false);
        gate.onClaimFee(own);
        store.setLastOwner("ship-1", "Feeless");
        VehicleOwnershipChangeEvent feeless = change(OwnershipReason.CLAIM, false);
        gate.onClaimFee(feeless);

        assertFalse(unknown.isCancelled() || own.isCancelled() || feeless.isCancelled());
        verify(payments, never()).charge(anyString(), anyString(), any(), any());

        gate.onChanged(changed(true, OwnershipReason.CLAIM, feeless.getOperationId(), false, actor));
        verify(payments, never()).complete(anyString(), any());
        assertEquals(Members.key(actor), store.getLastOwner("ship-1"));
        assertEquals(1, saves.get());
    }

    @Test
    void aFailedClaimRefundsItsFeeOnceAndAFailedRefundIsReported() {
        quote();
        store.setLastOwner("ship-1", "Previous");
        gate.onClaimFee(change(OwnershipReason.CLAIM, false));
        VehicleOwnershipChangeEvent paid = change(OwnershipReason.CLAIM, false);
        when(payments.charge(anyString(), anyString(), any(), any())).thenReturn(Result.APPLIED);
        gate.onClaimFee(paid);
        when(payments.receipt("claim:ship-1")).thenReturn(transferReceipt(4000));
        when(payments.refund("claim:ship-1")).thenReturn(new VehiclePayments.Refund(Result.APPLIED, 40));

        gate.onChanged(changed(false, OwnershipReason.CLAIM, paid.getOperationId(), false, actor));
        gate.onChanged(changed(false, OwnershipReason.CLAIM, paid.getOperationId(), false, actor));

        verify(payments, times(1)).refund("claim:ship-1");
        verify(player).sendMessage("§aThe claim did not go through, so the transfer fee was refunded.");
        verify(logger, never()).warning(anyString());
        verify(payments, never()).personal(anyString(), anyString(), any(), any());

        confirmations.clear(player.getUniqueId());
        gate.onClaimFee(change(OwnershipReason.CLAIM, false));
        VehicleOwnershipChangeEvent again = change(OwnershipReason.CLAIM, false);
        gate.onClaimFee(again);
        when(payments.refund("claim:ship-1")).thenReturn(new VehiclePayments.Refund(Result.UNAVAILABLE, 0));
        gate.onChanged(changed(false, OwnershipReason.CLAIM, again.getOperationId(), false, actor));
        verify(logger).severe(contains("staff must recover it"));

        paymentsStatic.when(VehiclePayments::current).thenReturn(payments, (VehiclePayments) null);
        gate.onClaimFee(change(OwnershipReason.CLAIM, false));
        VehicleOwnershipChangeEvent lost = change(OwnershipReason.CLAIM, false);
        gate.onClaimFee(lost);
        gate.onChanged(changed(false, OwnershipReason.CLAIM, lost.getOperationId(), false, actor));
        verify(logger, times(2)).severe(contains("staff must recover it"));

        gate.onChanged(changed(false, OwnershipReason.CLEAR, UUID.randomUUID(), false, null));
        verify(payments, times(2)).refund("claim:ship-1");

        paymentsStatic.when(VehiclePayments::current).thenReturn(payments);
        when(payments.receipt("claim:ship-1")).thenReturn(null);
        confirmations.clear(player.getUniqueId());
        gate.onClaimFee(change(OwnershipReason.CLAIM, false));
        VehicleOwnershipChangeEvent unrecorded = change(OwnershipReason.CLAIM, false);
        gate.onClaimFee(unrecorded);
        gate.onChanged(changed(false, OwnershipReason.CLAIM, unrecorded.getOperationId(), false, actor));
        verify(logger, times(3)).severe(contains("staff must recover it"));
        verify(payments, times(2)).refund("claim:ship-1");
    }

    private VehiclePayments.Receipt transferReceipt(long cents) {
        return new VehiclePayments.Receipt("r", actor, "realm", UUID.randomUUID(), FeeKind.TRANSFER_FEE, cents);
    }

    @Test
    void aPartialRefundIsLoggedAndThePlayerToldTheShortfall() {
        quote();
        store.setLastOwner("ship-1", "Previous");
        when(payments.charge(anyString(), anyString(), any(), any())).thenReturn(Result.APPLIED);
        when(payments.receipt("claim:ship-1")).thenReturn(transferReceipt(4000));
        when(payments.refund("claim:ship-1")).thenReturn(new VehiclePayments.Refund(Result.APPLIED, 25),
                new VehiclePayments.Refund(Result.APPLIED, 0));

        for (int attempt = 0; attempt < 2; attempt++) {
            confirmations.clear(player.getUniqueId());
            gate.onClaimFee(change(OwnershipReason.CLAIM, false));
            VehicleOwnershipChangeEvent paid = change(OwnershipReason.CLAIM, false);
            gate.onClaimFee(paid);
            gate.onChanged(changed(false, OwnershipReason.CLAIM, paid.getOperationId(), false, actor));
        }

        verify(logger).warning("Transfer fee refund short by 15.0 for " + actor.characterId() + " from faction realm");
        verify(logger).warning("Transfer fee refund short by 40.0 for " + actor.characterId() + " from faction realm");
        verify(player).sendMessage(VehicleFeeMessages.claimRefundedPart(25));
        verify(player).sendMessage(VehicleFeeMessages.claimRefundShort(15));
        verify(player).sendMessage(VehicleFeeMessages.claimRefundShort(40));
        verify(player, never()).sendMessage(VehicleFeeMessages.claimRefundedPart(0));
        verify(player, never()).sendMessage(VehicleFeeMessages.claimRefunded());
        assertTrue(VehicleFeeMessages.claimRefundShort(15).contains("15")
                && VehicleFeeMessages.claimRefundedPart(25).contains("25"));
    }

    @Test
    void aStaffTakeoverRecordsTheTargetCharacterWithoutAFeeAndIsAudited() {
        CharacterRef target = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        factionRow();

        gate.onChanged(changed(true, OwnershipReason.TAKEOVER, UUID.randomUUID(), true, target));

        verify(payments).personal("ship-1", "ship", target.characterId(), target.ownerId());
        verify(payments, never()).charge(anyString(), anyString(), any(), any());
        verify(logger).info(startsWith("[vehicle audit] Claimer TAKEOVER ship-1 (ship)"));
    }

    @Test
    void aClaimWhosePayerRowCannotBeWrittenIsLeftToTheReconcile() {
        when(payments.personal(anyString(), anyString(), any(), any())).thenReturn(false);
        gate.onChanged(changed(true, OwnershipReason.CLAIM, UUID.randomUUID(), false, null));
        paymentsStatic.when(VehiclePayments::current).thenReturn(null);
        gate.onChanged(changed(true, OwnershipReason.CLAIM, UUID.randomUUID(), false, actor));
        VehicleOwnershipChangedEvent anonymous = new VehicleOwnershipChangedEvent(true, OwnershipReason.CLAIM,
                UUID.randomUUID(), null, vehicle, null, null, false, null, null,
                new OwnershipValues("none", List.of(), false, false, null),
                new OwnershipValues("none", List.of(), false, false, null));
        gate.onChanged(anonymous);
        verify(logger, times(3)).warning(contains("upkeep reconcile adds it"));
        assertNull(store.getLastOwner("ship-1"));
    }

    @Test
    void clearingDropsThePayerRowButKeepsTheLastOwnerForTheTransferFee() {
        store.setLastOwner("ship-1", "Previous");
        gate.onChanged(changed(true, OwnershipReason.CLEAR, UUID.randomUUID(), false, null));
        verify(payments, never()).replaceRow(anyString(), any());

        registry.register(new PlayerVehicleRecord(actor.characterId(), "ship-1", "ship", OwnershipMode.PERSONAL, null));
        gate.onChanged(changed(true, OwnershipReason.CLEAR, UUID.randomUUID(), false, null));
        verify(payments).replaceRow("ship-1", null);
        assertEquals("Previous", store.getLastOwner("ship-1"));

        when(payments.replaceRow(anyString(), any())).thenReturn(false);
        gate.onChanged(changed(true, OwnershipReason.CLEAR, UUID.randomUUID(), true, null));
        verify(logger).warning(contains("could not be removed"));
        verify(logger).info(startsWith("[vehicle audit] Claimer CLEAR ship-1"));
        paymentsStatic.when(VehiclePayments::current).thenReturn(null);
        gate.onChanged(changed(true, OwnershipReason.CLEAR, UUID.randomUUID(), false, null));
        verify(logger, times(2)).warning(contains("could not be removed"));
    }

    @Test
    void systemWritesAndWhitelistChangesNeverTouchTheRegistry() {
        gate.onChanged(changed(true, OwnershipReason.RESTAMP, UUID.randomUUID(), false, null));
        gate.onChanged(changed(true, OwnershipReason.WHITELIST_ADD, UUID.randomUUID(), false, actor));
        verifyNoInteractions(payments);
    }

    @Test
    void anOperationIsRecordedOnceEvenIfReportedAgainAndOldOnesAreForgotten() {
        UUID operation = UUID.randomUUID();
        gate.onChanged(changed(true, OwnershipReason.CLAIM, operation, false, actor));
        gate.onChanged(changed(true, OwnershipReason.CLAIM, operation, false, actor));
        verify(payments, times(1)).personal(anyString(), anyString(), any(), any());

        for (int i = 0; i < VehicleOwnershipGate.REMEMBERED_OPERATIONS; i++) {
            gate.onChanged(changed(true, OwnershipReason.WHITELIST_ADD, UUID.randomUUID(), false, actor));
        }
        gate.onChanged(changed(true, OwnershipReason.CLAIM, operation, false, actor));
        verify(payments, times(2)).personal(anyString(), anyString(), any(), any());
    }

    @Test
    void aRecordingFailureIsLoggedNotThrown() {
        when(payments.personal(anyString(), anyString(), any(), any())).thenThrow(new IllegalStateException("disk"));
        gate.onChanged(changed(true, OwnershipReason.CLAIM, UUID.randomUUID(), false, actor));
        verify(logger).log(eq(Level.SEVERE), contains("Could not record ownership change"), any(IllegalStateException.class));
    }

    private VehicleOwnershipEditCheckEvent check(OwnershipReason reason, boolean staff) {
        return new VehicleOwnershipEditCheckEvent(player, vehicle, reason, actor, staff);
    }

    @Test
    void factionLeadersMayOpenTheMenuAndOnlyTheAllyAndVassalSwitchesAndStaleStampsAreHiddenAndFixed() {
        UUID leader = UUID.randomUUID();
        Faction red = mock(Faction.class);
        when(red.getId()).thenReturn("red");
        when(red.getLeader()).thenReturn(leader.toString());
        FactionManager.factions.add(red);
        factionRow();
        ownerData.setOwner("char_" + leader);
        try (MockedStatic<LeaderStamps> stamps = mockStatic(LeaderStamps.class, CALLS_REAL_METHODS)) {
            stamps.when(() -> LeaderStamps.scheduleRestamp(any())).thenAnswer(call -> null);
            VehicleOwnershipEditCheckEvent menu = check(null, false);
            gate.onEditCheck(menu);
            assertTrue(menu.isAllowed());
            VehicleOwnershipEditCheckEvent toggle = check(OwnershipReason.WHITELIST_TOGGLE, false);
            gate.onEditCheck(toggle);
            assertFalse(toggle.isAllowed());
            VehicleOwnershipEditCheckEvent guild = check(OwnershipReason.GUILD_ACCESS_TOGGLE, false);
            gate.onEditCheck(guild);
            assertFalse(guild.isAllowed(), "a faction vehicle has no guild");
            VehicleOwnershipEditCheckEvent allies = check(OwnershipReason.ALLY_ACCESS_TOGGLE, false);
            gate.onEditCheck(allies);
            assertTrue(allies.isAllowed());
            VehicleOwnershipEditCheckEvent vassals = check(OwnershipReason.VASSAL_ACCESS_TOGGLE, false);
            gate.onEditCheck(vassals);
            assertTrue(vassals.isAllowed());
            VehicleOwnershipEditCheckEvent staff = check(OwnershipReason.CLEAR, true);
            gate.onEditCheck(staff);
            assertTrue(staff.isAllowed());
            stamps.verify(() -> LeaderStamps.scheduleRestamp(any()), never());

            ownerData.setOwner("char_" + UUID.randomUUID());
            VehicleOwnershipEditCheckEvent stale = check(null, false);
            gate.onEditCheck(stale);
            assertFalse(stale.isAllowed());
            stamps.verify(() -> LeaderStamps.scheduleRestamp(any()));
        }
        registry.unregister("ship-1");
        VehicleOwnershipEditCheckEvent personal = check(OwnershipReason.CLEAR, false);
        gate.onEditCheck(personal);
        assertTrue(personal.isAllowed());
        when(vehicle.getUUID()).thenThrow(new IllegalStateException("broken"));
        VehicleOwnershipEditCheckEvent broken = check(null, false);
        gate.onEditCheck(broken);
        assertFalse(broken.isAllowed());
    }

    @Test
    void theClickBackstopRefusesAStaleFactionStampAndSchedulesTheFix() {
        factionRow();
        ownerData.setOwner("player_FormerLeader");
        try (MockedStatic<LeaderStamps> stamps = mockStatic(LeaderStamps.class, CALLS_REAL_METHODS)) {
            stamps.when(() -> LeaderStamps.scheduleRestamp(any())).thenAnswer(call -> null);
            VehiclePreInteractEvent stale = new VehiclePreInteractEvent(player, vehicle);
            gate.onPreInteract(stale);
            assertTrue(stale.isCancelled());
            stamps.verify(() -> LeaderStamps.scheduleRestamp(registry.getByVehicleUuid("ship-1").orElseThrow()));

            ownerData.setOwner("char_none");
            VehiclePreInteractEvent fresh = new VehiclePreInteractEvent(player, vehicle);
            gate.onPreInteract(fresh);
            assertFalse(fresh.isCancelled());
        }
        VehiclePreInteractEvent nothing = new VehiclePreInteractEvent(player, null);
        gate.onPreInteract(nothing);
        assertFalse(nothing.isCancelled());
        when(vehicle.getOwnerData()).thenReturn(null);
        VehiclePreInteractEvent noOwner = new VehiclePreInteractEvent(player, vehicle);
        gate.onPreInteract(noOwner);
        assertFalse(noOwner.isCancelled());
        when(vehicle.getOwnerData()).thenThrow(new IllegalStateException("broken"));
        VehiclePreInteractEvent broken = new VehiclePreInteractEvent(player, vehicle);
        gate.onPreInteract(broken);
        assertTrue(broken.isCancelled());
    }

    @Test
    void aLeaderChangeRestampsThatFactionsVehicles() {
        try (MockedStatic<LeaderStamps> stamps = mockStatic(LeaderStamps.class)) {
            Faction faction = mock(Faction.class);
            gate.onLeaderChanged(new FactionLeaderChangedEvent(faction, "a", "b"));
            stamps.verify(() -> LeaderStamps.onLeaderChanged(faction, registry));
        }
        assertEquals(Optional.empty(), registry.getByVehicleUuid("ship-1"));
    }

    @Test
    void aVehicleHeldForStaffCannotBeChangedByPlayers() throws Exception {
        FakeOwnerApi.install().stored("ship-1", "ship", "none");
        VehicleQuarantine quarantine = new VehicleQuarantine(folder);
        quarantine.load(List.of(new VehicleQuarantine.Entry("ship-1", "none", "construction owner could not be written")));
        VehicleQuarantine.use(quarantine);
        VehicleOwnershipChangeEvent claim = change(OwnershipReason.CLAIM, false);
        VehicleOwnershipChangeEvent staff = change(OwnershipReason.TAKEOVER, true);

        gate.onChange(claim);
        gate.onChange(staff);

        assertTrue(claim.isCancelled());
        verify(player).sendMessage(VehicleOwnershipGate.HELD);
        assertFalse(staff.isCancelled());
    }
}
