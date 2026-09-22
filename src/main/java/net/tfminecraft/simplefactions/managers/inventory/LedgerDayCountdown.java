package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

final class LedgerDayCountdown {
    private LedgerDayCountdown() {}

    // Snapshot when the item is built; no scheduled refresh or income projection.
    static String loreLine() {
        // Round up so the final partial minute does not read as an already-due reset.
        int minutes = (FactionManager.getSecondsUntilNewDay() + 59) / 60;
        return StringFormatter.formatHex("#d6cf69New day in: #cfc7a2"
                + minutes / 60 + "h " + minutes % 60 + "m");
    }
}
