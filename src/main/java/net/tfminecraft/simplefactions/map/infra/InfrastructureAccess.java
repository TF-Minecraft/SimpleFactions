package net.tfminecraft.simplefactions.map.infra;

import java.util.HashSet;
import java.util.Set;

import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawEffect;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.objects.Faction;

public final class InfrastructureAccess {
    private InfrastructureAccess() {}

    public static double forGuild(Faction guildFaction, Faction owner) {
        if (owner == null) return 1;
        if (guildFaction != null && sameFaction(topRealm(guildFaction), topRealm(owner))) return 1;
        double grant = amount(economyLaw(owner), Scope.FOREIGN_GUILDS, Region.OUR_TERRITORY);
        double reach = amount(economyLaw(guildFaction), Scope.DOMESTIC_GUILDS, Region.FOREIGN_TERRITORY);
        return Math.max(0, Math.min(1, grant + reach));
    }

    public static Faction topRealm(Faction faction) {
        Set<Faction> seen = new HashSet<>();
        Faction current = faction;
        while (current != null) {
            // Ignore a cyclic overlord chain rather than granting access through it.
            if (!seen.add(current)) return faction;
            Faction overlord = current.getOverlord();
            if (overlord == null) return current;
            current = overlord;
        }
        return null;
    }

    private static boolean sameFaction(Faction first, Faction second) {
        return first == second || (first != null && second != null && first.getId() != null
                && first.getId().equalsIgnoreCase(second.getId()));
    }

    private static Law economyLaw(Faction faction) {
        if (faction == null || faction.getLawHandler() == null) return null;
        LawGroup group = faction.getLawHandler().getGroup("economy");
        if (group == null) return null;
        IncomePreviewContext context = IncomePreviewContext.current();
        Law overlay = context != null && context.previewsLaw(faction)
                ? IncomePreviewContext.overlayLaw(group) : null;
        return overlay != null ? overlay : group.getCurrent();
    }

    public static double amount(Law law, Scope scope, Region region) {
        if (law == null) return 0;
        LawEffect effect = law.getScopedEffects().get(scope);
        Double explicit = effect == null ? null : effect.getModifierAmount(FactionModifiers.INFRASTRUCTURE_ACCESS, region);
        if (explicit != null) return explicit;
        boolean grant = scope == Scope.FOREIGN_GUILDS && region == Region.OUR_TERRITORY;
        boolean reach = scope == Scope.DOMESTIC_GUILDS && region == Region.FOREIGN_TERRITORY;
        if (!grant && !reach) return 0;
        return switch (law.getId()) {
            case "free_trade" -> grant ? 0.80 : 0.20;
            case "mercantilism" -> grant ? 0.50 : 0.20;
            case "decentralized" -> grant ? 0.50 : 0.00;
            case "protectionism" -> grant ? 0.20 : -0.10;
            case "isolationism" -> grant ? 0.00 : -0.30;
            default -> 0;
        };
    }
}
