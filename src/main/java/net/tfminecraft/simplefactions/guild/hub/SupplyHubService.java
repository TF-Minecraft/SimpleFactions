package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;

import org.bukkit.configuration.file.FileConfiguration;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.SupplyHubData;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Supply-hub rules. No Bukkit player or world calls, so the checks can run in unit tests.
 * Whether a hub is active is computed here and never saved.
 */
public final class SupplyHubService {

    private static final Comparator<SupplyHub> OLDEST_FIRST = Comparator
            .comparingLong(SupplyHub::createdAt)
            .thenComparing(hub -> hub.installationId() == null ? "" : hub.installationId())
            .thenComparing(hub -> hub.ownerFactionId() == null ? "" : hub.ownerFactionId());

    private SupplyHubService() {
    }

    public enum BuildFailure {
        ECONOMY_DISALLOWS,
        KIND_DISALLOWS,
        ALREADY_HAS_HUB,
        HUB_LIMIT,
        NO_FREE_SLOT,
        NO_TRADE,
        NO_AGREEMENT
    }

    public enum DormantReason {
        ECONOMY_DISALLOWS,
        INSTALLATION_GONE,
        NO_AGREEMENT,
        BEYOND_HUB_SLOTS,
        BEYOND_GUILD_LIMIT
    }

    public record HubStanding(boolean active, DormantReason reason) {
    }

    public enum RemoveOutcome {
        FOUND,
        MISSING,
        AMBIGUOUS
    }

    public record RemoveMatch(RemoveOutcome outcome, SupplyHub hub) {
    }

    public record HubCandidate(
            String ownerFactionId,
            String installationId,
            double distance) {
    }

    public static void loadConfig(FileConfiguration config) {
        double maxTax = config == null ? 50.0 : config.getDouble("supply-hubs.max-tax", 50.0);
        Cache.supplyHubMaxTax = Double.isFinite(maxTax) ? Math.max(0, Math.min(100, maxTax)) : 50.0;
        double maxFee = config == null ? 500.0 : config.getDouble("supply-hubs.max-fee", 500.0);
        Cache.supplyHubMaxFee = Double.isFinite(maxFee) ? Math.max(0, maxFee) : 500.0;
        int offerDays = config == null ? 7 : config.getInt("supply-hubs.offer-days", 7);
        Cache.supplyHubOfferDays = Math.max(1, offerDays);
        int agreementDays = config == null ? 14 : config.getInt("supply-hubs.agreement-days", 14);
        Cache.supplyHubAgreementDays = Math.max(1, agreementDays);
        Cache.supplyHubAutoAccept = config != null && config.getBoolean("supply-hubs.auto-accept", false);
        double strength = config == null ? 0.5 : config.getDouble("supply-hubs.no-hub-strength", 0.5);
        Cache.supplyHubNoHubStrength = Double.isFinite(strength) ? Math.max(0, Math.min(1, strength)) : 0.5;
        double corridor = config == null ? 0.5 : config.getDouble("supply-hubs.corridor-share", 0.5);
        Cache.supplyHubCorridorShare = Double.isFinite(corridor) ? Math.max(0, Math.min(1, corridor)) : 0.5;
    }

    public static int limit(Guild guild) {
        if (guild == null) {
            return 0;
        }
        return Math.max(0, (int) Math.floor(GuildModifierOverride.resolve(guild, GuildModifier.HUB_LIMIT)));
    }

    public static double upkeepPerHub(Guild guild) {
        return guild == null ? 0 : Math.max(0, GuildModifierOverride.resolve(guild, GuildModifier.HUB_UPKEEP));
    }

    /** Hubs above the guild limit are dormant newest first, and remain in the saved list. */
    public static boolean beyondGuildLimit(Guild guild, SupplyHub hub) {
        if (guild == null || hub == null) {
            return false;
        }
        List<SupplyHub> ordered = oldestFirst(guild.getSupplyHubs());
        return indexOfHub(ordered, hub) >= limit(guild);
    }

    /**
     * Table used when {@code hub-slots} is absent for that kind and level.
     * Anything not listed is 0.
     */
    public static int defaultHubSlots(InstallationKind kind, int level) {
        if (kind == null || level < 1) {
            return 0;
        }
        switch (kind) {
            case FORT:
                return 0;
            case PORT:
                if (level == 1) {
                    return 2;
                }
                return 0;
            case AIRPORT:
                if (level == 1) {
                    return 1;
                }
                return 0;
            case TRAIN_STATION:
                if (level == 1) {
                    return 1;
                }
                if (level == 2) {
                    return 2;
                }
                if (level == 3) {
                    return 4;
                }
                return 0;
            default:
                return 0;
        }
    }

    public static BuildFailure checkBuild(
            int hubSlots,
            boolean guildAlreadyHasHub,
            int guildHubCount,
            int hubLimit,
            int hubsAtInstallation,
            double tradePower,
            boolean permitted) {
        if (hubSlots <= 0) {
            return BuildFailure.KIND_DISALLOWS;
        }
        if (guildAlreadyHasHub) {
            return BuildFailure.ALREADY_HAS_HUB;
        }
        if (guildHubCount >= hubLimit) {
            return BuildFailure.HUB_LIMIT;
        }
        if (hubsAtInstallation >= hubSlots) {
            return BuildFailure.NO_FREE_SLOT;
        }
        if (!(tradePower > 0)) {
            return BuildFailure.NO_TRADE;
        }
        if (!permitted) {
            return BuildFailure.NO_AGREEMENT;
        }
        return null;
    }

    public static String buildFailureMessage(BuildFailure failure, String kindDisplayName, int limit) {
        String kind = kindDisplayName == null || kindDisplayName.isBlank() ? "installation" : kindDisplayName;
        switch (failure) {
            case ECONOMY_DISALLOWS:
                return "§cYour faction's economy does not allow supply hubs";
            case KIND_DISALLOWS:
                return "§cA " + kind + " cannot host a supply hub";
            case ALREADY_HAS_HUB:
                return "§cYour guild already has a supply hub at this installation";
            case HUB_LIMIT:
                return "§cYour guild is at its supply hub limit (§e" + limit + "§c)";
            case NO_FREE_SLOT:
                return "§cThis installation has no free hub slot";
            case NO_TRADE:
                return "§cYour guild has no trade power in this province, and none of its hubs can reach it";
            case NO_AGREEMENT:
                return "§cA supply hub in another realm needs an agreement";
            default:
                return "§cYou cannot build a supply hub here";
        }
    }

/** Own realm, or a hub agreement with the installation's owner. */
    public static boolean hubPermitted(Guild guild, String ownerFactionId, String installationId) {
        if (guild == null || ownerFactionId == null || installationId == null) {
            return false;
        }
        Faction guildFaction = guild.getFaction();
        if (guildFaction != null && guildFaction.getId() != null
                && guildFaction.getId().equalsIgnoreCase(ownerFactionId)) {
            return true;
        }
        Faction owner = FactionManager.getByString(ownerFactionId);
        if (owner != null && RelationManager.sameRealm(owner, guildFaction)) {
            return true;
        }
        return HubAgreementService.hasAgreement(guild, ownerFactionId, installationId);
    }

    public static boolean allowsSupplyHubs(Guild guild) {
        Faction faction = guild == null ? null : guild.getFaction();
        if (faction == null) {
            return true;
        }
        IncomePreviewContext context = IncomePreviewContext.current();
        return context != null ? context.allowsHubRule(faction, Rules.SUPPLY_HUBS)
                : faction.hasFactionRule(Rules.SUPPLY_HUBS);
    }

    public static HubStanding standing(
            Guild guild,
            SupplyHub hub,
            boolean installationExists,
            boolean allowed,
            int hubSlots,
            List<SupplyHub> atInstallationOldestFirst) {
        if (!allowsSupplyHubs(guild)) {
            return new HubStanding(false, DormantReason.ECONOMY_DISALLOWS);
        }
        if (beyondGuildLimit(guild, hub)) {
            return new HubStanding(false, DormantReason.BEYOND_GUILD_LIMIT);
        }
        return standing(hub, installationExists, allowed, hubSlots, atInstallationOldestFirst);
    }

    public static HubStanding standing(
            SupplyHub hub,
            boolean installationExists,
            boolean allowed,
            int hubSlots,
            List<SupplyHub> atInstallationOldestFirst) {
        if (!installationExists) {
            return new HubStanding(false, DormantReason.INSTALLATION_GONE);
        }
        if (!allowed) {
            return new HubStanding(false, DormantReason.NO_AGREEMENT);
        }
        int index = indexOfHub(atInstallationOldestFirst, hub);
        if (index < 0 || index >= hubSlots) {
            return new HubStanding(false, DormantReason.BEYOND_HUB_SLOTS);
        }
        return new HubStanding(true, null);
    }

    public static String statusText(HubStanding standing) {
        if (standing != null && standing.active()) {
            return "§aActive";
        }
        DormantReason reason = standing == null ? null : standing.reason();
        return "§cDormant §7(" + dormantReasonText(reason) + ")";
    }

    public static String dormantReasonText(DormantReason reason) {
        if (reason == null) {
            return "dormant";
        }
        switch (reason) {
            case ECONOMY_DISALLOWS:
                return "your faction's economy does not allow supply hubs";
            case INSTALLATION_GONE:
                return "its installation no longer exists";
            case NO_AGREEMENT:
                return "there is no hub agreement";
            case BEYOND_HUB_SLOTS:
                return "it is beyond this installation's hub slots";
            case BEYOND_GUILD_LIMIT:
                return "it is beyond your guild's supply hub limit";
            default:
                return "dormant";
        }
    }

    public static double totalUpkeep(int count, double perHub) {
        return Math.max(0, count) * Math.max(0, perHub);
    }

    public static double dailyCost(Guild guild) {
        if (guild == null || guild.getSupplyHubs() == null || guild.getSupplyHubs().isEmpty()) {
            return 0;
        }
        return totalUpkeep(guild.getSupplyHubs().size(), upkeepPerHub(guild));
    }

    public static double upkeepOf(SupplyHub hub, List<SupplyHub> guildHubs, double perHub) {
        List<SupplyHub> ordered = oldestFirst(guildHubs);
        int index = indexOfHub(ordered, hub);
        if (index < 0) {
            return 0;
        }
        return Math.max(0, perHub);
    }

    /**
     * Removes hubs the guild cannot pay for, newest first. Hubs that remain are the ones
     * the daily charge should take. Does not move money.
     */
    public static List<SupplyHub> shedUnpaid(List<SupplyHub> hubs, double wealth, double perHub) {
        List<SupplyHub> removed = new ArrayList<>();
        if (hubs == null || hubs.isEmpty()) {
            return removed;
        }
        List<SupplyHub> ordered = oldestFirst(hubs);
        int guard = 0;
        while (!ordered.isEmpty() && shedCost(ordered, perHub, null) > wealth && guard++ < 10000) {
            SupplyHub newest = ordered.remove(ordered.size() - 1);
            if (!hubs.remove(newest)) {
                break;
            }
            removed.add(newest);
        }
        return removed;
    }

    /**
     * Same as {@link #shedUnpaid(List, double, double)}, counting each hub's agreement fee as well.
     */
    public static List<SupplyHub> shedUnpaid(
            List<SupplyHub> hubs, double wealth, double perHub, java.util.function.ToDoubleFunction<SupplyHub> feeOf) {
        List<SupplyHub> removed = new ArrayList<>();
        if (hubs == null || hubs.isEmpty()) {
            return removed;
        }
        List<SupplyHub> ordered = oldestFirst(hubs);
        int guard = 0;
        while (!ordered.isEmpty() && shedCost(ordered, perHub, feeOf) > wealth && guard++ < 10000) {
            SupplyHub newest = ordered.remove(ordered.size() - 1);
            if (!hubs.remove(newest)) {
                break;
            }
            removed.add(newest);
        }
        return removed;
    }

    private static double shedCost(
            List<SupplyHub> ordered, double perHub, java.util.function.ToDoubleFunction<SupplyHub> feeOf) {
        double total = totalUpkeep(ordered.size(), perHub);
        if (feeOf == null) {
            return total;
        }
        for (SupplyHub hub : ordered) {
            total += Math.max(0, feeOf.applyAsDouble(hub));
        }
        return total;
    }

    public static List<SupplyHub> shedUnpaid(Guild guild) {
        if (guild == null || guild.getSupplyHubs() == null) {
            return List.of();
        }
        double wealth = 0;
        Bank bank = guild.getBank();
        if (bank != null && bank.getWealth() != null) {
            wealth = bank.getWealth();
        }
        List<SupplyHub> removed = shedUnpaid(
                guild.getSupplyHubs(), wealth, upkeepPerHub(guild), hub -> HubAgreementService.feeDenars(guild, hub));
        HubAgreementService.endFor(guild, removed);
        return removed;
    }

    public static boolean hasHub(List<SupplyHub> guildHubs, String ownerFactionId, String installationId) {
        return findHub(guildHubs, ownerFactionId, installationId) != null;
    }

    public static SupplyHub findHub(List<SupplyHub> guildHubs, String ownerFactionId, String installationId) {
        if (guildHubs == null) {
            return null;
        }
        for (SupplyHub hub : guildHubs) {
            if (sameInstallation(hub, ownerFactionId, installationId)) {
                return hub;
            }
        }
        return null;
    }

    public static int countAt(String ownerFactionId, String installationId, Iterable<Guild> guilds) {
        return atInstallation(ownerFactionId, installationId, guilds).size();
    }

    public static List<SupplyHub> atInstallation(
            String ownerFactionId, String installationId, Iterable<Guild> guilds) {
        List<SupplyHub> found = new ArrayList<>();
        if (guilds == null) {
            return found;
        }
        for (Guild guild : guilds) {
            if (guild == null || guild.getSupplyHubs() == null) {
                continue;
            }
            for (SupplyHub hub : guild.getSupplyHubs()) {
                if (sameInstallation(hub, ownerFactionId, installationId)) {
                    found.add(hub);
                }
            }
        }
        found.sort(OLDEST_FIRST);
        return found;
    }

    public static int removeInstallation(Iterable<Guild> guilds, String ownerFactionId, String installationId) {
        int removed = 0;
        if (guilds == null) {
            return 0;
        }
        for (Guild guild : guilds) {
            if (guild == null || guild.getSupplyHubs() == null) {
                continue;
            }
            List<SupplyHub> hubs = guild.getSupplyHubs();
            for (int i = hubs.size() - 1; i >= 0; i--) {
                if (sameInstallation(hubs.get(i), ownerFactionId, installationId)) {
                    hubs.remove(i);
                    removed++;
                }
            }
        }
        return removed;
    }

    public static int retarget(
            Iterable<Guild> guilds, String fromFactionId, String toFactionId, String installationId) {
        int moved = 0;
        if (guilds == null || toFactionId == null) {
            return 0;
        }
        for (Guild guild : guilds) {
            if (guild == null || guild.getSupplyHubs() == null) {
                continue;
            }
            List<SupplyHub> hubs = guild.getSupplyHubs();
            for (int i = 0; i < hubs.size(); i++) {
                SupplyHub hub = hubs.get(i);
                if (!sameInstallation(hub, fromFactionId, installationId)) {
                    continue;
                }
                hubs.set(i, new SupplyHub(toFactionId, hub.installationId(), hub.createdAt()));
                moved++;
            }
        }
        return moved;
    }

    public static int dropMissing(Iterable<Guild> guilds, BiPredicate<String, String> installationExists) {
        int dropped = 0;
        if (guilds == null) {
            return 0;
        }
        for (Guild guild : guilds) {
            if (guild == null || guild.getSupplyHubs() == null) {
                continue;
            }
            List<SupplyHub> hubs = guild.getSupplyHubs();
            for (int i = hubs.size() - 1; i >= 0; i--) {
                SupplyHub hub = hubs.get(i);
                boolean exists = installationExists != null
                        && hub != null
                        && installationExists.test(hub.ownerFactionId(), hub.installationId());
                if (!exists) {
                    hubs.remove(i);
                    dropped++;
                }
            }
        }
        return dropped;
    }

    public static void dropMissingLoaded() {
        dropMissing(allGuilds(), SupplyHubService::installationExists);
    }

    /**
     * Called once relations are loaded. Before that, a vassal hub at its overlord's
     * installation looks foreign and would be deleted.
     */
    public static void dropUnagreedForeignLoaded() {
        HubAgreementService.logRemovedForeignHubs(
                HubAgreementService.removeUnagreedForeignHubs(allGuilds(), HubAgreementFacts.LIVE));
    }

    public static void onInstallationRemoved(String ownerFactionId, String installationId) {
        HubAgreementMessenger.deliver(HubAgreementService.onInstallationRemoved(
                allGuilds(), ownerFactionId, installationId, HubAgreementFacts.LIVE));
    }

    public static void onInstallationTransferred(String fromFactionId, String toFactionId, String installationId) {
        HubAgreementMessenger.deliver(HubAgreementService.onTransferred(
                allGuilds(), fromFactionId, toFactionId, installationId, HubAgreementFacts.LIVE));
    }

    public static boolean installationExists(String ownerFactionId, String installationId) {
        Installation installation = findInstallation(ownerFactionId, installationId);
        return installation != null;
    }

    public static Installation findInstallation(String ownerFactionId, String installationId) {
        if (ownerFactionId == null || installationId == null || FactionManager.factions == null) {
            return null;
        }
        Faction faction = FactionManager.getByString(ownerFactionId);
        if (faction == null || faction.getInstallationHandler() == null) {
            return null;
        }
        Installation exact = faction.getInstallationHandler().getById(installationId);
        if (exact != null) {
            return exact;
        }
        for (Installation installation : faction.getInstallationHandler().getAll()) {
            if (installation != null
                    && installation.getId() != null
                    && installation.getId().equalsIgnoreCase(installationId)) {
                return installation;
            }
        }
        return null;
    }

    public static List<Guild> allGuilds() {
        if (FactionManager.factions == null) {
            return List.of();
        }
        return FactionManager.getAllGuilds();
    }

    public static int countLoaded(String ownerFactionId, String installationId) {
        return countAt(ownerFactionId, installationId, allGuilds());
    }

    public static String owningFactionId(Installation installation) {
        if (installation == null || FactionManager.factions == null) {
            return null;
        }
        for (Faction faction : FactionManager.factions) {
            if (faction == null || faction.getInstallationHandler() == null) {
                continue;
            }
            for (Installation candidate : faction.getInstallationHandler().getAll()) {
                if (candidate == installation) {
                    return faction.getId();
                }
            }
        }
        return null;
    }

    /** Raw per-guild trade the map export writes, before faction modifiers. */
    public static double exportedTrade(Province province, String guildId) {
        if (province == null || guildId == null) {
            return 0;
        }
        ProvinceDataEntry entry = province.getAllData().get(guildId);
        if (entry == null) {
            return 0;
        }
        return entry.getTrade();
    }

    /** Nearest overlapping installation whose hub slots are above 0. */
    public static HubCandidate chooseAllowed(
            List<PlacedCandidate> covering) {
        PlacedCandidate nearest = null;
        if (covering == null) {
            return null;
        }
        for (PlacedCandidate candidate : covering) {
            if (candidate == null || candidate.hubSlots() <= 0) {
                continue;
            }
            if (nearest == null || closer(candidate, nearest)) {
                nearest = candidate;
            }
        }
        if (nearest == null) {
            return null;
        }
        return nearest.site();
    }

    public static PlacedCandidate nearest(List<PlacedCandidate> covering) {
        PlacedCandidate nearest = null;
        if (covering == null) {
            return null;
        }
        for (PlacedCandidate candidate : covering) {
            if (candidate == null) {
                continue;
            }
            if (nearest == null || closer(candidate, nearest)) {
                nearest = candidate;
            }
        }
        return nearest;
    }

    public static RemoveMatch matchRemove(List<SupplyHub> hubs, String argument) {
        if (hubs == null || argument == null || argument.isBlank()) {
            return new RemoveMatch(RemoveOutcome.MISSING, null);
        }
        String raw = argument.trim();
        int colon = raw.indexOf(':');
        if (colon >= 0) {
            String factionId = raw.substring(0, colon).trim();
            String installationId = raw.substring(colon + 1).trim();
            if (factionId.isEmpty() || installationId.isEmpty()) {
                return new RemoveMatch(RemoveOutcome.MISSING, null);
            }
            SupplyHub found = findHub(hubs, factionId, installationId);
            if (found == null) {
                return new RemoveMatch(RemoveOutcome.MISSING, null);
            }
            return new RemoveMatch(RemoveOutcome.FOUND, found);
        }
        List<SupplyHub> matches = new ArrayList<>();
        for (SupplyHub hub : hubs) {
            if (hub != null
                    && hub.installationId() != null
                    && hub.installationId().equalsIgnoreCase(raw)) {
                matches.add(hub);
            }
        }
        if (matches.isEmpty()) {
            return new RemoveMatch(RemoveOutcome.MISSING, null);
        }
        if (matches.size() > 1) {
            return new RemoveMatch(RemoveOutcome.AMBIGUOUS, null);
        }
        return new RemoveMatch(RemoveOutcome.FOUND, matches.get(0));
    }

    public static List<SupplyHub> fromData(List<SupplyHubData> data) {
        List<SupplyHub> hubs = new ArrayList<>();
        if (data == null) {
            return hubs;
        }
        for (SupplyHubData entry : data) {
            SupplyHub hub = fromData(entry);
            if (hub != null) {
                hubs.add(hub);
            }
        }
        return hubs;
    }

    public static SupplyHub fromData(SupplyHubData data) {
        if (data == null || data.faction == null || data.faction.isBlank()
                || data.installation == null || data.installation.isBlank()) {
            return null;
        }
        long createdAt = data.createdAt == null ? 0L : data.createdAt;
        return new SupplyHub(data.faction, data.installation, createdAt);
    }

    public static List<SupplyHubData> toData(List<SupplyHub> hubs) {
        List<SupplyHubData> data = new ArrayList<>();
        if (hubs == null) {
            return data;
        }
        for (SupplyHub hub : hubs) {
            if (hub == null || hub.ownerFactionId() == null || hub.installationId() == null) {
                continue;
            }
            SupplyHubData entry = new SupplyHubData();
            entry.faction = hub.ownerFactionId();
            entry.installation = hub.installationId();
            entry.createdAt = hub.createdAt();
            data.add(entry);
        }
        return data;
    }

    public static List<SupplyHub> oldestFirst(List<SupplyHub> hubs) {
        List<SupplyHub> ordered = new ArrayList<>();
        if (hubs != null) {
            ordered.addAll(hubs);
        }
        ordered.sort(OLDEST_FIRST);
        return ordered;
    }

    public static String removalMessage(String installationLabel) {
        String label = installationLabel == null || installationLabel.isBlank() ? "installation" : installationLabel;
        return "§cSupply hub at §f" + label + " §cwas removed §7(unable to pay upkeep)";
    }

    public record PlacedCandidate(HubCandidate site, int hubSlots, String installationId, String ownerFactionId) {
    }

    private static boolean closer(PlacedCandidate candidate, PlacedCandidate nearest) {
        if (candidate.site().distance() < nearest.site().distance()) {
            return true;
        }
        if (candidate.site().distance() > nearest.site().distance()) {
            return false;
        }
        String candidateId = candidate.installationId() == null ? "" : candidate.installationId();
        String nearestId = nearest.installationId() == null ? "" : nearest.installationId();
        int byId = candidateId.compareToIgnoreCase(nearestId);
        if (byId != 0) {
            return byId < 0;
        }
        String candidateFaction = candidate.ownerFactionId() == null ? "" : candidate.ownerFactionId();
        String nearestFaction = nearest.ownerFactionId() == null ? "" : nearest.ownerFactionId();
        return candidateFaction.compareToIgnoreCase(nearestFaction) < 0;
    }

    private static int indexOfHub(List<SupplyHub> hubs, SupplyHub hub) {
        if (hubs == null || hub == null) {
            return -1;
        }
        for (int i = 0; i < hubs.size(); i++) {
            SupplyHub other = hubs.get(i);
            if (other == hub || hub.equals(other)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameInstallation(SupplyHub hub, String ownerFactionId, String installationId) {
        if (hub == null || ownerFactionId == null || installationId == null) {
            return false;
        }
        return ownerFactionId.equalsIgnoreCase(hub.ownerFactionId())
                && installationId.equalsIgnoreCase(hub.installationId());
    }
}
