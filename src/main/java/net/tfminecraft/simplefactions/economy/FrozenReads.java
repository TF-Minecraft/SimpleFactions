package net.tfminecraft.simplefactions.economy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Guild treasuries, held tax, node upkeep, espionage stability and inactive members read once
 * on the main thread. DenarEconomy, Dowsing and TLibs character identity refuse reads from
 * other threads, so income previews that run async read these instead.
 *
 * <p>Whose earnings the held tax came from is not captured: no income reads it, and the ledger
 * answers it from every tax credit since the last settlement (about 0.65 s a minute on Main).
 */
public final class FrozenReads {
    private static final ThreadLocal<FrozenReads> CURRENT = new ThreadLocal<>();

    private final Map<String, Long> cents;
    private final Map<UUID, Double> nodeUpkeep;
    private final Map<String, List<StabilityModifier>> espionage;
    private final Set<String> inactive;
    private final long capturedAt;

    private FrozenReads(
            Map<String, Long> cents, Map<UUID, Double> nodeUpkeep,
            Map<String, List<StabilityModifier>> espionage, Set<String> inactive, long capturedAt) {
        this.cents = Map.copyOf(cents);
        this.nodeUpkeep = Map.copyOf(nodeUpkeep);
        this.espionage = Map.copyOf(espionage);
        this.inactive = Set.copyOf(inactive);
        this.capturedAt = capturedAt;
    }

    /**
     * Reads guild treasuries, held tax, node upkeep, realm espionage modifiers and inactive
     * members now. Call on the main thread.
     */
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
        Map<UUID, Double> nodeUpkeep = new HashMap<>();
        for (Guild guild : FactionManager.getAllGuilds()) {
            Bank bank = guild == null ? null : guild.getBank();
            if (bank != null) {
                String account = bank.accountId();
                cents.put(account, Denars.balance(account));
            }
            UUID incarnation = guild == null ? null : guild.getIncarnationId();
            if (incarnation != null) {
                String taxAccount = Denars.guildTax(incarnation);
                cents.put(taxAccount, Denars.balance(taxAccount));
                nodeUpkeep.put(incarnation, Ledger.liveNodeUpkeep(guild));
            }
        }
        // Member shares (stability, upkeep) skip inactive members; whether one is online asks TLibs.
        Set<String> inactive = InactivityService.captureInactiveMembers();
        return new FrozenReads(cents, nodeUpkeep, espionage, inactive, now);
    }

    /** When these reads were taken, in epoch milliseconds. */
    public long capturedAt() {
        return capturedAt;
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

    /**
     * Held tax by earning character, for the ledger menu and settlement. Always live.
     *
     * @throws IllegalStateException inside {@link #read}, which does not capture it
     */
    public static Map<UUID, Long> taxContributors(UUID incarnation) {
        if (incarnation == null) return Map.of();
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Tax contributors are not captured for previews");
        }
        return Denars.taxContributors(incarnation);
    }

    /** The frozen node upkeep inside {@link #read}, otherwise the live upkeep. */
    public static double nodeUpkeep(Guild guild) {
        FrozenReads current = CURRENT.get();
        if (current != null) {
            UUID incarnation = guild.getIncarnationId();
            return incarnation == null ? 0.0 : current.nodeUpkeep.getOrDefault(incarnation, 0.0);
        }
        return Ledger.liveNodeUpkeep(guild);
    }

    /** Inside {@link #read}, the members who were inactive at capture; otherwise null, so callers check live. */
    public static Set<String> inactiveMembers() {
        FrozenReads current = CURRENT.get();
        return current == null ? null : current.inactive;
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
