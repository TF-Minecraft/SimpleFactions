package net.tfminecraft.simplefactions.testsupport;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import net.tfminecraft.simplefactions.vehicles.owners.LeaderStamps;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleQuarantine;

/** Vehicle ownership keeps its VehicleFramework link in statics; no test may leave one behind. */
public final class VehicleOwnersReset implements AfterEachCallback {
    @Override
    public void afterEach(ExtensionContext context) {
        VehicleOwnerWrites.use(null);
        LeaderStamps.enable(false);
        VehicleQuarantine.use(null);
    }
}
