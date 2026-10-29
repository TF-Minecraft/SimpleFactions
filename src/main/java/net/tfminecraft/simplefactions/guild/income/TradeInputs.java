package net.tfminecraft.simplefactions.guild.income;

import java.util.List;
import java.util.Objects;

import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Faction state that guild trade income reads besides province data: tariffs, guild tax and
 * realms. Work prepared on a province copy is stale once this changes. Call on the main thread.
 */
public final class TradeInputs {
    private TradeInputs() {}

    public static long fingerprint() {
        List<Faction> factions = FactionManager.factions;
        long hash = 1;
        for (Faction owner : factions) {
            hash = 31 * hash + Objects.hashCode(owner.getId());
            hash = 31 * hash + Objects.hashCode(RelationManager.getOverlord(owner));
            for (Faction other : factions) {
                hash = 31 * hash + Double.hashCode(owner.getTaxRate(TaxTarget.TARIFFS, other.getId(), true));
            }
            for (Guild guild : owner.getGuildHandler().getGuilds()) {
                hash = 31 * hash + Double.hashCode(owner.getTaxRate(TaxTarget.GUILDS, guild.getId(), true));
            }
        }
        return hash;
    }
}
