package net.tfminecraft.simplefactions.economy;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;

/**
 * Guild treasury balances read once on the main thread. DenarEconomy refuses ledger reads
 * from other threads, so income previews that run async read these instead.
 */
public final class FrozenBalances {
    private static final ThreadLocal<FrozenBalances> CURRENT = new ThreadLocal<>();

    private final Map<String, Long> cents;

    private FrozenBalances(Map<String, Long> cents) {
        this.cents = cents;
    }

    /** Reads every guild treasury now. Call on the main thread. */
    public static FrozenBalances capture() {
        Map<String, Long> cents = new HashMap<>();
        for (Guild guild : FactionManager.getAllGuilds()) {
            Bank bank = guild == null ? null : guild.getBank();
            if (bank != null) {
                String account = bank.accountId();
                cents.put(account, Denars.balance(account));
            }
        }
        return new FrozenBalances(cents);
    }

    /** Runs the work with these balances standing in for the ledger on this thread. */
    public <T> T read(Supplier<T> work) {
        FrozenBalances previous = CURRENT.get();
        CURRENT.set(this);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** The frozen balance inside {@link #read}, otherwise the live ledger balance. */
    public static long balance(String account) {
        FrozenBalances current = CURRENT.get();
        if (current != null) {
            return current.cents.getOrDefault(account, 0L);
        }
        return Denars.balance(account);
    }
}
