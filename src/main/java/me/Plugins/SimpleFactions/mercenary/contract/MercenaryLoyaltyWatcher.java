package me.Plugins.SimpleFactions.mercenary.contract;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/**
 * Re-validates signed contracts when the world moves under them.
 *
 * <p>This is the gap that would otherwise bite: {@code Side.isParticipating} only
 * counts secondaries that have already joined, so a contract that was legal at
 * signing turns illegal the moment an ally answers a call to arms, a vassalage
 * lands, or an election changes who sits in a government. There are no Bukkit
 * events for any of the three, so the choke points call in here directly, the way
 * {@code RelationManager.endVassalage} already calls {@code WarCommitmentService}.
 */
public final class MercenaryLoyaltyWatcher {
    private MercenaryLoyaltyWatcher() {
    }

    /** A faction joined a war, which may have put a company on the wrong side. */
    public static List<MercenaryContract> onWarJoined(Faction joiner) {
        return revalidateAll();
    }

    /** An alliance, vassalage or subject transfer changed who counts as an enemy. */
    public static List<MercenaryContract> onRelationChanged(Faction origin, Faction target) {
        return revalidateAll();
    }

    /** A leader or council changed, which can flip the player-level gate. */
    public static List<MercenaryContract> onGovernmentChanged(Faction faction) {
        return revalidateAll();
    }

    /**
     * Sweeps every company. Any of the three triggers can reach a contract the
     * changed faction is not itself a party to, through a nested relation, so
     * narrowing the sweep by faction would miss exactly the cases this exists for.
     * Performance is explicitly not a concern for this program.
     */
    public static List<MercenaryContract> revalidateAll() {
        List<MercenaryContract> terminated = new ArrayList<>();
        for (Guild g : FactionManager.getAllGuilds()) {
            if (g == null) continue;
            MercenaryCompany company = g.getCompany();
            if (company == null || !company.isFormed()) continue;
            terminated.addAll(ContractTerminationService.loyaltyConflicts(company));
            terminated.addAll(ContractTerminationService.resolveDoubleHire(company));
        }
        return terminated;
    }
}
