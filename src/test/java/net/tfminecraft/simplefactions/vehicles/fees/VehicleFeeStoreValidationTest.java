package net.tfminecraft.simplefactions.vehicles.fees;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VehicleFeeStoreValidationTest {
    @TempDir Path folder;

    @Test void invalidReceiptAndLegacyUuidPreservePreviouslyLoadedStateAndFile() throws Exception {
        var store = new VehicleFeeStore(); store.bind(folder.toFile());
        var previous = new VehicleFeeStore.PaidBuild(UUID.randomUUID(), "realm", 1);
        store.putPaidBuild("good", previous);
        for (String bad : new String[] {
                "{\"receipts\":{\"build:s\":{\"id\":\"incomplete\"}}}",
                "{\"paidBuilds\":{\"s\":{\"payerUuid\":\"invalid\",\"factionId\":\"realm\",\"amount\":1}}}"}) {
            Files.writeString(folder.resolve("vehicle_fees.json"), bad);
            assertThrows(IllegalStateException.class, store::load);
            assertEquals(previous, store.getPaidBuild("good"));
            assertThrows(java.io.IOException.class, store::saveDurably);
            assertEquals(bad, Files.readString(folder.resolve("vehicle_fees.json")));
        }
    }
}
