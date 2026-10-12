package net.tfminecraft.simplefactions.economy;

import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.api.SimpleFactionsApi;

/** Stops new actions after an uncertain posting or incomplete durable effect. */
public final class EconomyRuntime {
    private static boolean failed;
    private EconomyRuntime() {}

    public static boolean ready() { return !failed && SimpleFactionsApi.ready() && Denars.ready(); }

    /** Called only after every imported balance and stored intent has recovered successfully. */
    public static void recovered() { failed = false; }

    public static void failClosed(Throwable error) {
        failed = true;
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null) plugin.failFinancialState(error);
    }
}
