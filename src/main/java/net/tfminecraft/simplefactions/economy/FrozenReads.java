package net.tfminecraft.simplefactions.economy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Guild treasuries and espionage stability read once on the main thread. DenarEconomy and
 * TLibs character identity refuse reads from other threads, so income previews that run
 * async read these instead.
 */
public final class FrozenReads {
    private static final ThreadLocal<FrozenReads> CURRENT = new ThreadLocal<>();

    private final Map<String, Long> cents;
    private final Map<String, List<StabilityModifier>> espionage;

    private FrozenReads(Map<String, Long> cents, Map<String, List<StabilityModifier>> espionage) {
        this.cents = Map.copyOf(cents);
        this.espionage = Map.copyOf(espionage);
    }

    /** Reads every guild treasury and realm espionage modifier now. Call on the main thread. */
    public static FrozenReads capture() {
        long now = System.currentTimeMillis();
        Map<String, List<StabilityModifier>> espionage = new HashMap<>();
        for (Faction faction : FactionManager.factions) {
            // Stability asks for each realm guild, so create any missing one here rather than on a worker.
            if (faction != null && faction.getGuildHandler() != null) {
                faction.getOrCreateMainGuild();
            }
            // Spymaster vacancy asks TLibs whether the holder's character is dead.
            if (faction != null && faction.getId() != null && faction.getEspionage() != null) {
                espionage.put(faction.getId(), List.copyOf(EspionageService.stabilityModifiers(faction, now)));
            }
        }
        Map<String, Long> cents = new HashMap<>();
        for (Guild guild : FactionManager.getAllGuilds()) {
            Bank bank = guild == null ? null : guild.getBank();
            if (bank != null) {
                String account = bank.accountId();
                cents.put(account, Denars.balance(account));
            }
        }
        return new FrozenReads(cents, espionage);
    }

    /** Runs the work with these reads standing in for the live state on this thread. */
    public <T> T read(Supplier<T> work) {
        FrozenReads previous = CURRENT.get();
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
        FrozenReads current = CURRENT.get();
        if (current != null) {
            return current.cents.getOrDefault(account, 0L);
        }
        return Denars.balance(account);
    }

    /** The frozen espionage modifiers inside {@link #read}, otherwise the live ones at {@code now}. */
    public static List<StabilityModifier> espionage(Faction faction, long now) {
        FrozenReads current = CURRENT.get();
        if (current != null) {
            String id = faction.getId();
            return id == null ? List.of() : current.espionage.getOrDefault(id, List.of());
        }
        return EspionageService.stabilityModifiers(faction, now);
    }
}
