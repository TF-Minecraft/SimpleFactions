package net.tfminecraft.simplefactions.estate;

import java.nio.file.Files;
import java.nio.file.Path;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.tlibs.character.estate.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Durable unfinished entries still count missing vehicles after their registry row is removed. */
class VehicleEstateDestroyedRetryTest {
    @TempDir Path folder;
    VehicleEstateParticipantTest fixture;
    VehicleEstateParticipantTest.FakeFramework framework;

    @BeforeEach void setup() throws Exception {
        fixture = new VehicleEstateParticipantTest();
        fixture.folder = folder;
        fixture.setup();
        fixture.participant.clock = fixture.now::get;
        framework = spy(fixture.framework);
        VehicleOwnerWrites.use(framework);
    }

    @AfterEach void close() { fixture.close(); }

    private void cart(boolean row) {
        if (row) fixture.row("cart", "cart", fixture.testator.characterId());
        fixture.framework.put("cart", "cart", fixture.tag(fixture.testator));
    }

    private void destroyedEntryBecomesBlocked(String originalStep) throws Exception {
        String previousRow = net.tfminecraft.simplefactions.database.JsonUtil.GSON.toJson(fixture.plan().entry("cart").previousRow);
        fixture.framework.vehicles.remove("cart");
        fixture.registry.unregister("cart");
        // Another personal vehicle can finish while this entry waits, without weakening the missing entry.
        fixture.row("boat", "boat", fixture.testator.characterId());
        fixture.framework.put("boat", "boat", fixture.tag(fixture.testator));
        clearInvocations(fixture.payments, framework);
        for (int attempt = 1; attempt <= 3; attempt++) {
            fixture.now.addAndGet(VehicleEstateParticipant.ATTEMPT_SPACING_MS);
            Outcome result = fixture.participant.settle(fixture.toHeir());
            assertEquals(attempt == 3 ? Outcome.Kind.BLOCKED : Outcome.Kind.RETRY, result.kind());
            var entry = fixture.plan().entry("cart");
            assertEquals(attempt, entry.attempts);
            assertEquals(originalStep, entry.step);
            assertEquals(fixture.testator.characterId(), entry.from);
            assertEquals(fixture.heir.characterId(), entry.to);
            assertEquals(fixture.heir.ownerId(), entry.toAccount);
            assertEquals(previousRow, net.tfminecraft.simplefactions.database.JsonUtil.GSON.toJson(entry.previousRow));
            assertEquals(attempt == 3 ? "the vehicle is gone or cannot be read" : null, entry.blocked);
            assertTrue(fixture.registry.getByVehicleUuid("cart").isEmpty(), "the old row must not be recreated");
            assertFalse(fixture.framework.vehicles.containsKey("cart"));
            assertEquals(fixture.heir.characterId(), fixture.payer("boat"));
            assertEquals(fixture.tag(fixture.heir), fixture.owner("boat"));
            assertEquals(VehicleEstatePlan.LAST_OWNER, fixture.plan().entry("boat").step);
            if (attempt < 3) {
                assertEquals(Outcome.Kind.RETRY, fixture.participant.settle(fixture.toHeir()).kind());
                assertEquals(attempt, fixture.plan().entry("cart").attempts, "same-hour calls do not consume another attempt");
            }
        }
        verify(fixture.payments, never()).personal(eq("cart"), anyString(), any(), any());
        verify(fixture.payments, never()).replaceRow(eq("cart"), any());
        verify(framework, never()).write(eq("cart"), any(), any(), any());
        // The existing public settle entry point is the provider's staff retry: reread once and remain blocked.
        clearInvocations(framework, fixture.payments);
        assertEquals(Outcome.Kind.BLOCKED, fixture.participant.settle(fixture.toHeir()).kind());
        verify(framework, times(1)).read("cart");
        verify(framework, never()).write(eq("cart"), any(), any(), any());
        verifyNoInteractions(fixture.payments);
        assertEquals(3, fixture.plan().entry("cart").attempts);
        assertTrue(fixture.registry.getByVehicleUuid("cart").isEmpty());
        assertFalse(fixture.framework.vehicles.containsKey("cart"));
    }

    @Test void failedHandoverThenPermanentDestructionCountsThreeSpacedChecks() throws Exception {
        cart(true);
        fixture.framework.scripted.add(VehicleOwnerApi.Result.SAVE_FAILED);
        assertEquals(Outcome.retry("cart: the owner change failed (SAVE_FAILED)"), fixture.participant.settle(fixture.toHeir()));
        assertEquals(VehicleEstatePlan.PLANNED, fixture.plan().entry("cart").step);
        assertEquals(fixture.testator.characterId(), fixture.payer("cart"));
        destroyedEntryBecomesBlocked(VehicleEstatePlan.PLANNED);
    }

    @Test void failedLastOwnerSaveThenPermanentDestructionCountsThreeSpacedChecks() throws Exception {
        cart(true);
        Path obstruction = folder.resolve("not-a-directory"); Files.writeString(obstruction, "file");
        fixture.fees.bind(obstruction.resolve("blocked").toFile());
        assertEquals(Outcome.retry("Could not save a vehicle estate step"), fixture.participant.settle(fixture.toHeir()));
        assertEquals(VehicleEstatePlan.DONE, fixture.plan().entry("cart").step);
        assertEquals(fixture.heir.characterId(), fixture.payer("cart"));
        assertEquals(fixture.tag(fixture.heir), fixture.owner("cart"));
        fixture.fees.bind(folder.resolve("Cache").toFile());
        destroyedEntryBecomesBlocked(VehicleEstatePlan.DONE);
    }

    @Test void failedTagOnlyHandoverThenPermanentDestructionCountsThreeSpacedChecks() throws Exception {
        cart(false);
        fixture.framework.scripted.add(VehicleOwnerApi.Result.UNAVAILABLE);
        assertEquals(Outcome.retry("cart: the owner change failed (UNAVAILABLE)"), fixture.participant.settle(fixture.toHeir()));
        assertEquals(VehicleEstatePlan.PLANNED, fixture.plan().entry("cart").step);
        assertNull(fixture.plan().entry("cart").previousRow);
        assertTrue(fixture.registry.getByVehicleUuid("cart").isEmpty());
        destroyedEntryBecomesBlocked(VehicleEstatePlan.PLANNED);
    }
}
