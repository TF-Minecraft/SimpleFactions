package net.tfminecraft.simplefactions.testsupport;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import net.tfminecraft.simplefactions.war.history.WarHistory;

/** A plugin enable starts war history; no test may leave it recording into another test's folder. */
public final class WarHistoryReset implements AfterEachCallback {
    @Override
    public void afterEach(ExtensionContext context) {
        WarHistory.resetForTests();
    }
}
