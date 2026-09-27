package net.tfminecraft.simplefactions.vehicles.fees;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class VehicleFeeConfirmationsTest {
    private final VehicleFeeConfirmations confirmations = new VehicleFeeConfirmations();
    private final UUID player = UUID.randomUUID();

    @Test
    void secondActionWithinTheWindowConfirmsOnce() {
        assertFalse(confirmations.confirm(player, "build:a", 10.0, 0));
        confirmations.ask(player, "build:a", 10.0, 0);

        assertTrue(confirmations.confirm(player, "build:a", 10.0, 1_000));
        assertFalse(confirmations.confirm(player, "build:a", 10.0, 2_000));
    }

    @Test
    void differentTargetPriceOrLapsedWindowAsksAgain() {
        confirmations.ask(player, "build:a", 10.0, 0);
        assertFalse(confirmations.confirm(player, "build:b", 10.0, 1_000));
        assertFalse(confirmations.confirm(player, "build:a", 12.0, 1_000));
        assertFalse(confirmations.confirm(player, "build:a", 10.0, VehicleFeeConfirmations.WINDOW_MILLIS));
    }
}
