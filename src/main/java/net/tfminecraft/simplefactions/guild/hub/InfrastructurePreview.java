package net.tfminecraft.simplefactions.guild.hub;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

/** What one new installation would earn the realm. Used by the build confirmation. */
public final class InfrastructurePreview {
    public record InstallationPreview(double infrastructureHere, double realmPerDay, double upkeep) {
        public static final InstallationPreview NONE = new InstallationPreview(0, 0, 0);
    }

    private InfrastructurePreview() {
    }

    /**
     * Adds this kind's infrastructure in the province and reads the realm's net change.
     * Writes live trade breakdowns, the same way a law preview does.
     */
    public static InstallationPreview previewInstallation(
            ProvinceManager live, Faction realm, int provinceId, InstallationKind kind) {
        if (live == null || realm == null || kind == null || !live.contains(provinceId)) {
            return InstallationPreview.NONE;
        }
        Province province = live.get(provinceId);
        if (province.isSea()) {
            return InstallationPreview.NONE;
        }
        double added = sourceAmount(kind);
        Map<Guild, Double> before = EconomicPreview.projectNets(copy(live));
        ProvinceManager after = copy(live);
        if (added > 0) {
            after.setExtraInfrastructure(Map.of(provinceId, added));
        }
        Map<Guild, Double> next = EconomicPreview.projectNets(after);
        return new InstallationPreview(added, realmDelta(realm, before, next), levelOneUpkeep(kind));
    }

    private static double sourceAmount(InstallationKind kind) {
        if (kind == null) {
            return 0;
        }
        return switch (kind) {
            case TRAIN_STATION -> Cache.infrastructureStation;
            case PORT -> Cache.infrastructurePort;
            case AIRPORT -> Cache.infrastructureAirport;
            default -> 0;
        };
    }

    private static double levelOneUpkeep(InstallationKind kind) {
        if (kind == null) {
            return 0;
        }
        try {
            return InstallationConfigLoader.getDailyUpkeep(kind, 1);
        } catch (IllegalStateException ex) {
            return switch (kind) {
                case TRAIN_STATION -> 5;
                case PORT -> 15;
                case AIRPORT -> 20;
                case FORT -> 30;
            };
        }
    }

    private static double realmDelta(Faction realm, Map<Guild, Double> before, Map<Guild, Double> after) {
        double sum = 0;
        Set<Guild> guilds = new HashSet<>();
        guilds.addAll(before.keySet());
        guilds.addAll(after.keySet());
        for (Guild guild : guilds) {
            if (guild == null || !sameRealm(realm, guild.getFaction())) {
                continue;
            }
            sum += after.getOrDefault(guild, 0.0) - before.getOrDefault(guild, 0.0);
        }
        return round(sum);
    }

    private static double round(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private static boolean sameRealm(Faction left, Faction right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && left.getId().equalsIgnoreCase(right.getId())) {
            return true;
        }
        return net.tfminecraft.simplefactions.managers.RelationManager.sameRealm(left, right);
    }

    private static ProvinceManager copy(ProvinceManager live) {
        ProvinceManager snapshot = live.createSnapshotShell();
        snapshot.copyAllDataFrom(live);
        return snapshot;
    }
}
