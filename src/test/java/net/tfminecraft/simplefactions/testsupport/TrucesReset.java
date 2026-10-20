package net.tfminecraft.simplefactions.testsupport;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import net.tfminecraft.simplefactions.war.truce.TruceService;

/** Ending a war records truces in statics; no test may leave one behind for the next declare. */
public final class TrucesReset implements AfterEachCallback {
    @Override
    public void afterEach(ExtensionContext context) {
        TruceService.reset();
    }
}
