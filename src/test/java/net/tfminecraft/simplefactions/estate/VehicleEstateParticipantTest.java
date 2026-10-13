package net.tfminecraft.simplefactions.estate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.api.SimpleFactionsApi;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerFields;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerMeta;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleQuarantine;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.EstateDecision;
import net.tfminecraft.tlibs.character.estate.Estates;
import net.tfminecraft.tlibs.character.estate.Holdings;
import net.tfminecraft.tlibs.character.estate.Outcome;
import net.tfminecraft.tlibs.character.estate.Recipient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

/** A dead character's personal vehicles move to the property recipient, replayed by observed state (R8, M5, N3). */
class VehicleEstateParticipantTest {
    @TempDir Path folder;
    final CharacterRef testator = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final CharacterRef heir = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final UUID death = UUID.randomUUID();
    final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    final VehicleFeeStore fees = new VehicleFeeStore();
    final FakeFramework framework = new FakeFramework();
    final VehiclePayments payments = mock(VehiclePayments.class);
    final List<String> planOnFirstRowWrite = new ArrayList<>();
    MockedStatic<SimpleFactionsApi> api;
    MockedStatic<Estates> estates;
    MockedStatic<VehiclesConfigLoader> types;
    MockedStatic<VehiclePayments> paymentsCurrent;
    VehicleEstateParticipant participant;
    final java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000_000);

    /** VehicleFramework's owner values in memory; a scripted result replaces the next writes. */
    static final class FakeFramework implements VehicleOwnerApi {
        final Map<String, OwnerMeta> vehicles = new LinkedHashMap<>();
        final Set<String> unreadable = new HashSet<>();
        final List<String> writes = new ArrayList<>();
        Deque<Result> scripted = new ArrayDeque<>();
        boolean listFails;

        void put(String vehicle, String type, String owner) {
            OwnerFields fields = new OwnerFields(owner, List.of("char_x;y;Crew"), true, false, null);
            vehicles.put(vehicle, new OwnerMeta(vehicle, type, owner, Optional.of(fields), Optional.empty()));
        }

        @Override public boolean characters() { return true; }

        @Override public List<OwnerMeta> list() {
            if (listFails) throw new IllegalStateException("database locked");
            return List.copyOf(vehicles.values());
        }

        @Override public Optional<OwnerMeta> read(String vehicle) {
            return unreadable.contains(vehicle) ? Optional.empty() : Optional.ofNullable(vehicles.get(vehicle));
        }

        @Override public Result write(String vehicle, String owner, List<String> whitelist, Reason reason) {
            writes.add(vehicle + "=" + owner + ":" + reason);
            Result result = scripted.isEmpty() ? Result.OK : scripted.poll();
            if (result == Result.OK) put(vehicle, vehicles.get(vehicle).typeId(), owner);
            return result;
        }
    }

    @BeforeEach void setup() throws IOException {
        api = mockStatic(SimpleFactionsApi.class);
        api.when(SimpleFactionsApi::ready).thenReturn(true);
        estates = mockStatic(Estates.class);
        estates.when(() -> Estates.guardHolds(any())).thenReturn(true);
        types = mockStatic(VehiclesConfigLoader.class);
        types.when(() -> VehiclesConfigLoader.isKnownType(anyString())).thenAnswer(call -> !"unknown".equals(call.getArgument(0)));
        paymentsCurrent = mockStatic(VehiclePayments.class);
        paymentsCurrent.when(VehiclePayments::current).thenReturn(payments);
        when(payments.ready()).thenReturn(true);
        when(payments.personal(anyString(), anyString(), any(), any())).thenAnswer(call -> {
            String vehicle = call.getArgument(0);
            if (planOnFirstRowWrite.isEmpty()) planOnFirstRowWrite.add(Files.readString(planFile()));
            registry.unregister(vehicle);
            registry.register(new PlayerVehicleRecord(call.getArgument(2), vehicle, call.getArgument(1),
                    OwnershipMode.PERSONAL, null, null, call.getArgument(3)));
            return true;
        });
        when(payments.replaceRow(anyString(), any())).thenAnswer(call -> {
            registry.unregister(call.getArgument(0));
            registry.register(call.getArgument(1));
            return true;
        });
        VehicleOwnerWrites.use(framework);
        VehicleQuarantine.use(null);
        Files.createDirectories(folder.resolve("Cache"));
        fees.bind(folder.resolve("Cache").toFile());
        participant = new VehicleEstateParticipant(folder, registry, fees, Logger.getLogger("test"));
        // Each call happens an hour after the last, so every gone or unreadable read counts.
        participant.clock = () -> now.addAndGet(VehicleEstateParticipant.ATTEMPT_SPACING_MS);
    }

    @AfterEach void close() {
        VehicleOwnerWrites.use(null);
        VehicleQuarantine.use(null);
        paymentsCurrent.close();
        types.close();
        estates.close();
        api.close();
    }

    Path planFile() {
        return folder.resolve("vehicles/estates/" + death + ".json");
    }

    EstateDecision decision(Recipient property) {
        return new EstateDecision(death, testator, Optional.of(Instant.EPOCH), Instant.EPOCH, Optional.of(heir),
                Optional.empty(), Optional.empty(), Recipient.account("char:" + heir.characterId() + ":bank"),
                property, Recipient.character(heir), Recipient.character(heir), 3);
    }

    EstateDecision toHeir() {
        return decision(Recipient.character(heir));
    }

    void row(String vehicle, String type, UUID payer) {
        registry.register(new PlayerVehicleRecord(payer, vehicle, type, OwnershipMode.PERSONAL, null, null,
                testator.ownerId()));
    }

    String owner(String vehicle) {
        return framework.vehicles.get(vehicle).currentOwner();
    }

    UUID payer(String vehicle) {
        return registry.getByVehicleUuid(vehicle).map(PlayerVehicleRecord::getPlayerUuid).orElse(null);
    }

    VehicleEstatePlan plan() throws IOException {
        return DurableJson.read(planFile(), VehicleEstatePlan.class);
    }

    String tag(CharacterRef ref) {
        return OwnerTags.character(ref.characterId());
    }

    @Test void rowAndTagVehiclesMoveToTheRecipientWithoutAFee() throws Exception {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        row("boat", "boat", testator.characterId());
        framework.put("boat", "boat", OwnerTags.NO_CHARACTER);
        framework.put("wagon", "wagon", tag(testator));
        framework.put("other", "cart", tag(heir));
        Outcome outcome = participant.settle(toHeir());
        assertEquals(Outcome.done(Map.of("vehicles", 2), List.of("cart", "wagon")), outcome);
        for (String vehicle : List.of("cart", "wagon")) {
            assertEquals(heir.characterId(), payer(vehicle));
            assertEquals(heir.ownerId(), registry.getByVehicleUuid(vehicle).orElseThrow().getAccountUuid());
            assertEquals(tag(heir), owner(vehicle));
            assertEquals(Members.key(heir.characterId()), fees.getLastOwner(vehicle));
        }
        assertEquals(List.of("cart=" + tag(heir) + ":HANDOVER", "wagon=" + tag(heir) + ":HANDOVER"), framework.writes);
        assertEquals(testator.characterId(), payer("boat"), "a tag naming someone else is not the testator's vehicle");
        verify(payments, never()).charge(any(), any(), any(), any());
        verify(payments, never()).charge(any(), any(), any(), any(), anyLong());
        // The plan was durable before the first store changed.
        var early = com.google.gson.JsonParser.parseString(planOnFirstRowWrite.getFirst()).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals(VehicleEstatePlan.PLANNED, early.get("step").getAsString());
        assertEquals(testator.characterId().toString(), early.getAsJsonObject("previousRow").get("payer").getAsString());
        VehicleEstatePlan plan = plan();
        assertEquals(List.of(VehicleEstatePlan.LAST_OWNER, VehicleEstatePlan.LAST_OWNER),
                plan.entries.stream().map(entry -> entry.step).toList());
        assertEquals(testator.characterId(), plan.entry("cart").previousRow.payer());
        assertNull(plan.entry("wagon").previousRow, "a tag-only vehicle had no row to restore");
        assertEquals(outcome, participant.settle(toHeir()), "settling again moves nothing twice");
        assertEquals(2, framework.writes.size());
    }

    @Test void factionVehiclesStampedToTheTestatorStayWithTheFaction() {
        registry.register(new PlayerVehicleRecord(null, "ram", "ram", OwnershipMode.POOL, null, "realm"));
        framework.put("ram", "ram", tag(testator));
        assertEquals(Outcome.done(Map.of("vehicles", 0), List.of()), participant.settle(toHeir()));
        assertEquals(tag(testator), owner("ram"));
    }

    @Test void waitingOrFrozenPropertyMovesNothingAtThisRevision() {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        assertEquals(Outcome.done(), participant.settle(decision(Recipient.WAITING)));
        assertEquals(Outcome.done(), participant.settle(decision(Recipient.FROZEN)));
        assertEquals(testator.characterId(), payer("cart"));
        assertFalse(Files.exists(planFile()));
    }

    @Test void nothingMovesUnlessTheGuardHoldsAndVehiclesAreReady() {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        estates.when(() -> Estates.guardHolds(any())).thenReturn(false);
        assertEquals(Outcome.Kind.RETRY, participant.settle(toHeir()).kind());
        assertEquals(Outcome.Kind.RETRY, participant.settle(decision(Recipient.FROZEN)).kind(), "not this revision");
        estates.when(() -> Estates.guardHolds(any())).thenReturn(true);
        api.when(SimpleFactionsApi::ready).thenReturn(false);
        assertEquals(Outcome.Kind.RETRY, participant.settle(toHeir()).kind());
        api.when(SimpleFactionsApi::ready).thenReturn(true);
        VehicleOwnerWrites.use(null);
        assertEquals(Outcome.Kind.RETRY, participant.settle(toHeir()).kind());
        assertEquals(testator.characterId(), payer("cart"));
        assertTrue(framework.writes.isEmpty());
    }

    @Test void nothingToMoveIsDoneEvenWhileTheVehicleStageIsDegraded() {
        api.when(SimpleFactionsApi::ready).thenReturn(false);
        VehicleOwnerWrites.use(null);
        // Otherwise a degraded stage would hold every revive behind this participant.
        assertEquals(Outcome.done(), participant.settle(decision(Recipient.WAITING)));
        assertEquals(Outcome.done(), participant.settle(decision(Recipient.FROZEN)));
    }

    @Test void theEstateGateHoldsVehiclesBackWithoutPlanningThem() throws Exception {
        row("odd", "unknown", testator.characterId());
        framework.put("odd", "unknown", tag(testator));
        row("blocked", "cart", testator.characterId());
        framework.put("blocked", "cart", tag(testator));
        when(payments.blockedVehicle("blocked")).thenReturn(true);
        row("held", "cart", testator.characterId());
        framework.put("held", "cart", tag(testator));
        VehicleQuarantine quarantine = new VehicleQuarantine(folder);
        quarantine.load(List.of(new VehicleQuarantine.Entry("held", tag(testator), "staff")));
        VehicleQuarantine.use(quarantine);
        row("untyped", null, testator.characterId());
        Outcome outcome = participant.settle(toHeir());
        assertInstanceOf(Outcome.Retry.class, outcome);
        String reason = ((Outcome.Retry) outcome).reason();
        assertTrue(reason.contains("odd: unknown vehicle type"), reason);
        assertTrue(reason.contains("blocked: a vehicle payment awaits recovery or staff"), reason);
        assertTrue(reason.contains("held: held back for staff"), reason);
        assertTrue(reason.contains("untyped: unknown vehicle type"), reason);
        assertFalse(Files.exists(planFile()));
        when(payments.blockedVehicle("blocked")).thenReturn(false);
        when(payments.ready()).thenReturn(false);
        assertTrue(((Outcome.Retry) participant.settle(toHeir())).reason().contains("blocked: a vehicle payment"));
        paymentsCurrent.when(VehiclePayments::current).thenReturn(null);
        assertTrue(((Outcome.Retry) participant.settle(toHeir())).reason().contains("blocked: a vehicle payment"));
        assertTrue(framework.writes.isEmpty());
    }

    @Test void slotLimitsAndTheBattleLockDoNotApply() {
        for (int i = 0; i < 5; i++) {
            row("cart" + i, "cart", testator.characterId());
            framework.put("cart" + i, "cart", tag(testator));
        }
        assertEquals(5, ((Outcome.Done) participant.settle(toHeir())).counts().get("vehicles"));
    }

    @Test void lateVehicleIsCaughtByTheNextSettle() {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        assertEquals(List.of("cart"), ((Outcome.Done) participant.settle(toHeir())).movedIds());
        // A build finishing for the dead payer is stamped to them as today.
        row("built", "cart", testator.characterId());
        framework.put("built", "cart", tag(testator));
        assertEquals(List.of("cart", "built"), ((Outcome.Done) participant.settle(toHeir())).movedIds());
    }

    /** Seeds a plan entry as an interrupted run left it. */
    void planned(String vehicle, String step) throws IOException {
        VehicleEstatePlan plan = VehicleEstatePlan.load(planFile(), death, testator.characterId());
        VehicleEstatePlan.Entry entry = new VehicleEstatePlan.Entry();
        entry.vehicle = vehicle;
        entry.from = testator.characterId();
        entry.to = heir.characterId();
        entry.toAccount = heir.ownerId();
        entry.previousRow = new VehicleEstatePlan.Row(testator.characterId(), "cart", "PERSONAL", null, null,
                testator.ownerId());
        entry.step = step;
        plan.entries.add(entry);
        plan.save(planFile());
    }

    @Test void replayClosesAVehicleHandedOnSinceWithoutRewritingIt() throws Exception {
        planned("cart", VehicleEstatePlan.ROW);
        row("cart", "cart", heir.characterId());
        CharacterRef buyer = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        framework.put("cart", "cart", tag(buyer));
        assertEquals(Outcome.done(Map.of("vehicles", 0), List.of()), participant.settle(toHeir()));
        assertEquals(tag(buyer), owner("cart"));
        assertEquals(VehicleEstatePlan.SUPERSEDED, plan().entry("cart").step);
        assertTrue(framework.writes.isEmpty());
        assertEquals(Outcome.done(Map.of("vehicles", 0), List.of()), participant.settle(toHeir()));
    }

    @Test void replayRedoesTheRowWhateverTheTagSays() throws Exception {
        // The owner change reported a failure after VehicleFramework had written it, so the row was put back.
        planned("cart", VehicleEstatePlan.PLANNED);
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(heir));
        assertEquals(List.of("cart"), ((Outcome.Done) participant.settle(toHeir())).movedIds());
        assertEquals(heir.characterId(), payer("cart"));
        assertTrue(framework.writes.isEmpty(), "the tag already names the recipient");
        assertEquals(Members.key(heir.characterId()), fees.getLastOwner("cart"));
    }

    @Test void replayRedoesTheOwnerChangeAfterACrashBetweenRowAndTag() throws Exception {
        planned("cart", VehicleEstatePlan.ROW);
        row("cart", "cart", heir.characterId());
        framework.put("cart", "cart", tag(testator));
        assertEquals(List.of("cart"), ((Outcome.Done) participant.settle(toHeir())).movedIds());
        assertEquals(List.of("cart=" + tag(heir) + ":HANDOVER"), framework.writes);
        verify(payments, never()).personal(any(), any(), any(), any());
    }

    @Test void replayRecordsTheLastOwnerAfterACrashBeforeIt() throws Exception {
        planned("cart", VehicleEstatePlan.DONE);
        row("cart", "cart", heir.characterId());
        framework.put("cart", "cart", tag(heir));
        assertEquals(List.of("cart"), ((Outcome.Done) participant.settle(toHeir())).movedIds());
        assertEquals(Members.key(heir.characterId()), fees.getLastOwner("cart"));
        assertEquals(VehicleEstatePlan.LAST_OWNER, plan().entry("cart").step);
        assertTrue(framework.writes.isEmpty());
    }

    @Test void finishedEntryIsDoneWithoutTouchingAnything() throws Exception {
        planned("cart", VehicleEstatePlan.LAST_OWNER);
        row("cart", "cart", heir.characterId());
        framework.put("cart", "cart", tag(heir));
        assertEquals(List.of("cart"), ((Outcome.Done) participant.settle(toHeir())).movedIds());
        assertNull(fees.getLastOwner("cart"));
        verifyNoInteractions(payments);
    }

    @Test void failedOwnerChangePutsThePreviousRowBackAndRetries() throws Exception {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        framework.scripted.add(VehicleOwnerApi.Result.SAVE_FAILED);
        Outcome first = participant.settle(toHeir());
        assertEquals(Outcome.retry("cart: the owner change failed (SAVE_FAILED)"), first);
        assertEquals(testator.characterId(), payer("cart"));
        assertEquals(VehicleEstatePlan.PLANNED, plan().entry("cart").step);
        assertEquals(List.of("cart"), ((Outcome.Done) participant.settle(toHeir())).movedIds());
        assertEquals(heir.characterId(), payer("cart"));
    }

    @Test void failedOwnerChangeOfATagOnlyVehicleRemovesTheNewRow() throws Exception {
        framework.put("cart", "cart", tag(testator));
        framework.scripted.add(VehicleOwnerApi.Result.UNAVAILABLE);
        assertEquals(Outcome.Kind.RETRY, participant.settle(toHeir()).kind());
        assertTrue(registry.getByVehicleUuid("cart").isEmpty());
    }

    @Test void vehicleGoneAfterThreeAttemptsIsBlockedAndItsRowIsNotPutBack() throws Exception {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        framework.scripted.addAll(List.of(VehicleOwnerApi.Result.NOT_FOUND, VehicleOwnerApi.Result.NOT_FOUND,
                VehicleOwnerApi.Result.NOT_FOUND));
        assertEquals(Outcome.retry("cart: the vehicle is gone"), participant.settle(toHeir()));
        assertEquals(Outcome.Kind.RETRY, participant.settle(toHeir()).kind());
        assertEquals(Outcome.blocked("cart: the vehicle is gone"), participant.settle(toHeir()));
        assertEquals(heir.characterId(), payer("cart"), "upkeep never falls back on the dead owner");
        verify(payments, never()).replaceRow(any(), any());
        assertEquals(3, plan().entry("cart").attempts);
        // Staff fixed the vehicle, then asked for a retry.
        assertEquals(List.of("cart"), ((Outcome.Done) participant.settle(toHeir())).movedIds());
    }

    @Test void attemptsCountAtMostOnceAnHourSoCallsCloseTogetherDoNotListAVehicle() throws Exception {
        participant.clock = now::get;
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        framework.unreadable.add("cart");
        for (int i = 0; i < 5; i++) assertEquals(Outcome.Kind.RETRY, participant.settle(toHeir()).kind());
        assertEquals(1, plan().entry("cart").attempts);
        now.addAndGet(VehicleEstateParticipant.ATTEMPT_SPACING_MS - 1);
        participant.settle(toHeir());
        assertEquals(1, plan().entry("cart").attempts);
        now.addAndGet(1);
        participant.settle(toHeir());
        assertEquals(2, plan().entry("cart").attempts);
    }

    @Test void blockedVehicleWaitsForStaffAndTheReconcileNeverAdvancesIt() throws Exception {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        framework.unreadable.add("cart");
        for (int i = 0; i < 3; i++) participant.settle(toHeir());
        assertEquals("the vehicle is gone or cannot be read", plan().entry("cart").blocked);
        framework.unreadable.clear();
        estates.when(Estates::decided).thenReturn(List.of(toHeir()));
        estates.when(() -> Estates.report(any(), any(), anyLong(), any())).thenReturn(true);
        participant.reconcile();
        estates.verify(() -> Estates.report(death, VehicleEstateParticipant.ID, 3,
                Outcome.blocked("cart: the vehicle is gone or cannot be read")));
        assertTrue(framework.writes.isEmpty(), "only a staff retry moves it on");
        // Staff retry: the estate provider calls settle, which reads the vehicle once more.
        assertEquals(List.of("cart"), ((Outcome.Done) participant.settle(toHeir())).movedIds());
    }

    @Test void staffRetryOfAStillMissingVehicleIsBlockedAgainAtOnce() throws Exception {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        framework.unreadable.add("cart");
        for (int i = 0; i < 3; i++) participant.settle(toHeir());
        participant.clock = now::get;
        assertEquals(Outcome.blocked("cart: the vehicle is gone or cannot be read"), participant.settle(toHeir()));
        assertEquals(VehicleEstateParticipant.ATTEMPTS, plan().entry("cart").attempts);
    }

    @Test void entryPlannedForAnotherRecipientIsListedRatherThanReportedMoved() throws Exception {
        planned("cart", VehicleEstatePlan.LAST_OWNER);
        CharacterRef other = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(Outcome.blocked("cart: planned for another recipient"),
                participant.settle(decision(Recipient.character(other))));
        assertTrue(framework.writes.isEmpty());
    }

    @Test void unreadableVehicleIsRetriedThenBlocked() throws Exception {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        framework.unreadable.add("cart");
        participant.settle(toHeir());
        participant.settle(toHeir());
        assertEquals(Outcome.blocked("cart: the vehicle is gone or cannot be read"), participant.settle(toHeir()));
        assertEquals(heir.characterId(), payer("cart"));
    }

    @Test void retryingVehiclesAreReportedBeforeBlockedOnes() throws Exception {
        row("gone", "cart", testator.characterId());
        framework.put("gone", "cart", tag(testator));
        framework.unreadable.add("gone");
        for (int i = 0; i < 3; i++) participant.settle(toHeir());
        row("blocked", "cart", testator.characterId());
        framework.put("blocked", "cart", tag(testator));
        when(payments.blockedVehicle("blocked")).thenReturn(true);
        assertEquals(Outcome.Kind.RETRY, participant.settle(toHeir()).kind());
    }

    @Test void storeFailuresRetryWithoutLosingTheStep() throws Exception {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        doReturn(false).when(payments).personal(anyString(), anyString(), any(), any());
        assertEquals(Outcome.retry("cart: the payer row could not be saved"), participant.settle(toHeir()));
        reset(payments);
        when(payments.ready()).thenReturn(true);
        when(payments.personal(anyString(), anyString(), any(), any())).thenAnswer(call -> {
            row(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            return true;
        });
        when(payments.replaceRow(anyString(), any())).thenReturn(false);
        framework.scripted.add(VehicleOwnerApi.Result.SAVE_FAILED);
        assertEquals(Outcome.retry("cart: the previous payer row could not be restored"), participant.settle(toHeir()));
        fees.bind(folder.resolve("Cache/vehicle_fees.json/blocked").toFile());
        Files.writeString(folder.resolve("Cache/vehicle_fees.json"), "a file, not a folder");
        assertEquals(Outcome.retry("Could not save a vehicle estate step"), participant.settle(toHeir()));
        assertEquals(VehicleEstatePlan.DONE, plan().entry("cart").step);
    }

    @Test void unlistableOwnersOrAForeignPlanRetry() throws Exception {
        framework.listFails = true;
        assertEquals(Outcome.retry("Could not list vehicle owners"), participant.settle(toHeir()));
        framework.listFails = false;
        VehicleEstatePlan foreign = VehicleEstatePlan.load(planFile(), death, UUID.randomUUID());
        foreign.save(planFile());
        assertEquals(Outcome.retry("Could not save a vehicle estate step"), participant.settle(toHeir()));
        Files.delete(planFile());
        planned("cart", VehicleEstatePlan.ROW);
        var json = com.google.gson.JsonParser.parseString(Files.readString(planFile())).getAsJsonObject();
        json.getAsJsonArray("entries").add(com.google.gson.JsonNull.INSTANCE);
        Files.writeString(planFile(), json.toString());
        assertThrows(IOException.class, () -> VehicleEstatePlan.load(planFile(), death, testator.characterId()));
        json.getAsJsonArray("entries").remove(1);
        json.getAsJsonArray("entries").get(0).getAsJsonObject().remove("step");
        Files.writeString(planFile(), json.toString());
        assertThrows(IOException.class, () -> VehicleEstatePlan.load(planFile(), death, testator.characterId()));
        json.add("entries", com.google.gson.JsonNull.INSTANCE);
        Files.writeString(planFile(), json.toString());
        assertThrows(IOException.class, () -> VehicleEstatePlan.load(planFile(), death, testator.characterId()));
        assertThrows(IOException.class, () -> VehicleEstatePlan.load(planFile(), UUID.randomUUID(), testator.characterId()));
    }

    @Test void describeListsWhatTheCharacterHoldsToday() {
        row("cart", "cart", testator.characterId());
        framework.put("wagon", "wagon", tag(testator));
        Holdings holdings = participant.describe(testator.characterId());
        assertEquals(Map.of("vehicles", 2), holdings.counts());
        assertEquals(List.of("cart cart", "wagon wagon"), holdings.labels());
        framework.listFails = true;
        assertEquals(List.of("cart cart"), participant.describe(testator.characterId()).labels());
        VehicleOwnerWrites.use(null);
        assertEquals(Map.of("vehicles", 1), participant.describe(testator.characterId()).counts());
        registry.register(new PlayerVehicleRecord(testator.characterId(), "0123456789abcdef", "cart",
                OwnershipMode.PERSONAL, null));
        assertTrue(participant.describe(testator.characterId()).labels().contains("cart 01234567"));
        assertEquals(VehicleEstateParticipant.ID, participant.id());
    }

    @Test void describeNamesATagOnlyVehicleWithoutAType() {
        framework.vehicles.put("wagon", new OwnerMeta("wagon", null, tag(testator),
                Optional.of(new OwnerFields(tag(testator), List.of(), false, false, null)), Optional.empty()));
        assertEquals(List.of("vehicle wagon"), participant.describe(testator.characterId()).labels());
    }

    @Test void reconcileSettlesDecidedEstatesAndReportsOnlyChanges() {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        EstateDecision decided = toHeir();
        EstateDecision stale = new EstateDecision(UUID.randomUUID(), heir, Optional.empty(), Instant.EPOCH,
                Optional.empty(), Optional.empty(), Optional.empty(), Recipient.FROZEN, Recipient.FROZEN,
                Recipient.FROZEN, Recipient.SENDER, 0);
        estates.when(Estates::decided).thenReturn(List.of(decided, stale));
        estates.when(() -> Estates.guardHolds(stale)).thenReturn(false);
        estates.when(() -> Estates.report(any(), any(), anyLong(), any())).thenReturn(false, true, true);
        participant.reconcile();
        participant.reconcile();
        participant.reconcile();
        Outcome done = Outcome.done(Map.of("vehicles", 1), List.of("cart"));
        estates.verify(() -> Estates.report(death, VehicleEstateParticipant.ID, 3, done), times(2));
        estates.verify(() -> Estates.report(eq(stale.deathId()), any(), anyLong(), any()), never());
        // A late vehicle changes the outcome, so it is reported again.
        row("built", "cart", testator.characterId());
        framework.put("built", "cart", tag(testator));
        participant.reconcile();
        estates.verify(() -> Estates.report(death, VehicleEstateParticipant.ID, 3,
                Outcome.done(Map.of("vehicles", 2), List.of("cart", "built"))));
        estates.when(Estates::decided).thenReturn(List.of());
        participant.reconcile();
        estates.when(Estates::decided).thenReturn(List.of(decided));
        participant.reconcile();
        estates.verify(() -> Estates.report(death, VehicleEstateParticipant.ID, 3,
                Outcome.done(Map.of("vehicles", 2), List.of("cart", "built"))), times(2));
    }

    @Test void oneFailingEstateDoesNotHoldBackTheOthers() {
        row("cart", "cart", testator.characterId());
        framework.put("cart", "cart", tag(testator));
        EstateDecision poisoned = new EstateDecision(UUID.randomUUID(), heir, Optional.empty(), Instant.EPOCH,
                Optional.empty(), Optional.empty(), Optional.empty(), Recipient.FROZEN, Recipient.FROZEN,
                Recipient.FROZEN, Recipient.SENDER, 0);
        estates.when(Estates::decided).thenReturn(List.of(poisoned, toHeir()));
        estates.when(() -> Estates.guardHolds(poisoned)).thenThrow(new IllegalStateException("corrupt record"));
        estates.when(() -> Estates.report(any(), any(), anyLong(), any())).thenReturn(true);
        participant.reconcile();
        estates.verify(() -> Estates.report(death, VehicleEstateParticipant.ID, 3,
                Outcome.done(Map.of("vehicles", 1), List.of("cart"))));
    }

    @Test void reconcileWaitsForSimpleFactionsAndContainsFailures() {
        api.when(SimpleFactionsApi::ready).thenReturn(false);
        participant.reconcile();
        estates.verify(Estates::decided, never());
        api.when(SimpleFactionsApi::ready).thenReturn(true);
        estates.when(Estates::decided).thenThrow(new IllegalStateException("not on the main thread"));
        assertDoesNotThrow(participant::reconcile);
    }
}
