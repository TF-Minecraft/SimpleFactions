package net.tfminecraft.simplefactions.economy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.denareconomy.api.Result;

/** Imports old treasury and withheld-tax balances once, including debt and quarantined tax keys. */
public final class GuildTreasury {
    private GuildTreasury() {}

    public static void importLegacy(UUID incarnation, double treasury, Map<String, Double> taxes) {
        importBalance("import-guild:" + incarnation, Denars.guild(incarnation), Money.cents(treasury));
        long held = 0;
        if (taxes != null) {
            for (Double tax : taxes.values()) {
                if (tax != null) held = Math.addExact(held, Money.cents(tax));
            }
        }
        importBalance("import-tax:" + incarnation, Denars.guildTax(incarnation), held);
    }

    private static void importBalance(String operation, String account, long cents) {
        Result previous = Denars.status(operation).orElse(null);
        if (previous == Result.APPLIED) return;
        Result result = previous == null ? Denars.post(operation, "sf:legacy-import",
                List.of(new Leg(Denars.MINT, Math.negateExact(cents)), new Leg(account, cents))) : previous;
        if (result != Result.APPLIED) throw new IllegalStateException("Cannot import " + operation + ": " + result);
    }
}
