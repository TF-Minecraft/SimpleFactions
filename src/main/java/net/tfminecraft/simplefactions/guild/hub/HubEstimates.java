package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * What-if income for a hub, a realm's infrastructure, or one new installation.
 * Menus read the cache. Tax and fee are arithmetic on a result that was already run.
 */
public final class HubEstimates {
    public enum Group {
        READY,
        WORTH_BUILDING
    }

    /**
     * One ranked destination. {@code installationId} is null when the installation
     * is only imagined. Gains are net income before this hub's tax, fee, and upkeep.
     */
    public record Destination(
            Group group,
            String hostFactionId,
            String installationId,
            String label,
            int provinceId,
            InstallationKind kind,
            boolean ownRealm,
            boolean assumedRailway,
            double assumedTrackBlocks,
            double operatorGain,
            double hostGain,
            Map<String, Double> hostGuildGains,
            double taxableIncome) {
    }

    /** Operator pays the tax and the fee. The host realm receives both. Own-realm terms are zero. */
    public record Terms(double operatorNet, double hostNet, double tax, double fee) {
    }

    /** What building this installation is worth to the realm that would own it. */
    public record InstallationPreview(double infrastructureHere, double realmPerDay, double upkeep) {
        public static final InstallationPreview NONE = new InstallationPreview(0, 0, 0);
    }

    record Candidate(
            Group group,
            Faction host,
            Installation installation,
            boolean ownRealm,
            double distance) {
    }

    record Planned(List<Link> added, boolean assumedRailway, double assumedTrackBlocks) {
    }

    record Result(
            double operatorGain,
            double hostGain,
            Map<String, Double> hostGuildGains,
            double taxableIncome) {
    }

    static void clearForTests() {
        proposals = Map.of();
        headlines = Map.of();
        RUNNING.set(false);
    }

    private static volatile Map<String, List<Destination>> proposals = Map.of();
    private static volatile Map<String, Double> headlines = Map.of();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private HubEstimates() {
    }

    public static List<Destination> destinations(Guild guild) {
        if (guild == null || guild.getId() == null) {
            return List.of();
        }
        return destinations(guild.getId());
    }

    public static List<Destination> destinations(String guildId) {
        if (guildId == null) {
            return List.of();
        }
        List<Destination> exact = proposals.get(guildId);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, List<Destination>> entry : proposals.entrySet()) {
            if (guildId.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    /** How much infrastructure is worth to this realm, from the last daily pass. Positive earns. */
    public static double infrastructureWorth(Faction realm) {
        if (realm == null) {
            return 0;
        }
        return infrastructureWorth(realm.getId());
    }

    public static double infrastructureWorth(String realmId) {
        if (realmId == null) {
            return 0;
        }
        Double exact = headlines.get(realmId);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Double> entry : headlines.entrySet()) {
            if (realmId.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 0;
    }

    public static Terms applyTerms(Destination destination, int ratePercent, long feeCents) {
        if (destination == null) {
            return new Terms(0, 0, 0, 0);
        }
        if (destination.ownRealm()) {
            return new Terms(destination.operatorGain(), destination.hostGain(), 0, 0);
        }
        int rate = Math.max(0, Math.min(100, ratePercent));
        double fee = Math.max(0, feeCents) / 100.0;
        double tax = destination.taxableIncome() * rate / 100.0;
        return new Terms(
                round(destination.operatorGain() - tax - fee),
                round(destination.hostGain() + tax + fee),
                round(tax),
                round(fee));
    }

    /**
     * On demand. Adds this kind's infrastructure in the province and reads the realm's net change.
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

    /** After income and the agreement tick. One pass, then the live trade numbers are put back. */
    public static void scheduleDaily() {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null || plugin.getProvinceManager() == null || !Cache.provincesEnabled) {
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            return;
        }
        ProvinceManager live = plugin.getProvinceManager();
        List<Guild> guilds = new ArrayList<>(FactionManager.getAllGuilds());
        List<Faction> realms = FactionManager.getCopy();
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> runDaily(plugin, live, guilds, realms));
        } catch (RuntimeException | LinkageError ex) {
            RUNNING.set(false);
        }
    }

    private static void runDaily(
            SimpleFactions plugin, ProvinceManager live, List<Guild> guilds, List<Faction> realms) {
        try {
            long started = System.nanoTime();
            rebuild(live, guilds, realms);
            long millis = (System.nanoTime() - started) / 1_000_000L;
            plugin.getLogger().info("Hub estimates took " + millis + " ms for " + guilds.size() + " guilds");
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Hub estimates failed", ex);
        } finally {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (plugin.isEnabled() && plugin.getProvinceManager() == live) {
                            live.recalculate();
                        }
                    } finally {
                        RUNNING.set(false);
                    }
                });
            } catch (RuntimeException | LinkageError ex) {
                RUNNING.set(false);
            }
        }
    }

    static void rebuild(ProvinceManager live, List<Guild> guilds, List<Faction> realms) {
        List<Guild> safeGuilds = guilds == null ? List.of() : guilds;
        List<Faction> safeRealms = realms == null ? List.of() : realms;
        Map<String, List<Destination>> nextProposals = new HashMap<>();
        int cap = Math.max(1, Cache.supplyHubEstimateCandidates);
        for (Guild guild : safeGuilds) {
            if (guild == null || guild.getId() == null || !guild.hasCapital() || guild.getLedger() == null) {
                continue;
            }
            if (!SupplyHubService.allowsSupplyHubs(guild)) {
                continue;
            }
            nextProposals.put(guild.getId(), List.copyOf(build(live, guild, safeGuilds, safeRealms, cap)));
        }
        Map<String, Double> nextHeadlines = headlinesFor(live, safeRealms);
        proposals = Map.copyOf(nextProposals);
        headlines = Map.copyOf(nextHeadlines);
    }

    static List<Candidate> shortlist(
            Guild guild, List<Guild> guilds, List<Faction> factions, ProvinceManager provinces, int cap) {
        int limit = Math.max(1, cap);
        List<Installation> partners = activeHubs(guild, guilds);
        Set<Integer> occupied = new HashSet<>();
        List<Candidate> ready = new ArrayList<>();
        if (factions != null) {
            for (Faction host : factions) {
                if (host == null || host.getId() == null || host.getInstallationHandler() == null
                        || host.getInstallationHandler().getAll() == null) {
                    continue;
                }
                boolean realm = sameRealm(guild.getFaction(), host);
                for (Installation installation : host.getInstallationHandler().getAll()) {
                    if (installation == null || installation.getId() == null) {
                        continue;
                    }
                    occupied.add(installation.getProvince());
                    if (!hubKind(installation.getKind())) {
                        continue;
                    }
                    int slots = hubSlots(installation);
                    if (slots <= 0) {
                        continue;
                    }
                    if (SupplyHubService.hasHub(guild.getSupplyHubs(), host.getId(), installation.getId())) {
                        continue;
                    }
                    if (SupplyHubService.countAt(host.getId(), installation.getId(), guilds) >= slots) {
                        continue;
                    }
                    if (!realm && !host.hasFactionRule(Rules.HUB_TAX)) {
                        continue;
                    }
                    ready.add(new Candidate(
                            Group.READY, host, installation, realm,
                            closeness(guild, installation.getCenterX(), installation.getCenterZ(), partners, provinces)));
                }
            }
        }
        List<Candidate> worth = new ArrayList<>();
        if (provinces != null) {
            for (Province province : provinces.getProvinces()) {
                if (province == null || !province.isValid() || province.isSea() || occupied.contains(province.getId())) {
                    continue;
                }
                Faction host = TitleManager.getByProvince(province.getId());
                if (host == null || host.getId() == null) {
                    continue;
                }
                boolean realm = sameRealm(guild.getFaction(), host);
                if (!realm && !host.hasFactionRule(Rules.HUB_TAX)) {
                    continue;
                }
                Installation imagined = new Installation(
                        "proposed-" + province.getId(),
                        terrainLabel(province),
                        InstallationKind.TRAIN_STATION,
                        province.getId(),
                        province.getCenterX(),
                        province.getCenterZ(),
                        0L);
                worth.add(new Candidate(
                        Group.WORTH_BUILDING, host, imagined, realm,
                        closeness(guild, province.getCenterX(), province.getCenterZ(), partners, provinces)));
            }
        }
        List<Candidate> chosen = new ArrayList<>();
        chosen.addAll(closest(ready, limit));
        chosen.addAll(closest(worth, limit));
        return chosen;
    }

    /** New links from the guild's active hubs to this installation. Assumed rail is a straight line. */
    static Planned plan(List<Installation> existing, Installation target, String targetFactionId, ProvinceManager provinces) {
        List<Link> added = new ArrayList<>();
        boolean assumed = false;
        double blocks = 0;
        if (existing == null || target == null) {
            return new Planned(added, false, 0);
        }
        String toFaction = targetFactionId == null ? "" : targetFactionId;
        for (Installation from : existing) {
            if (from == null || from.getProvince() == target.getProvince()) {
                continue;
            }
            String fromFaction = SupplyHubService.owningFactionId(from);
            Choice forward = choose(from, fromFaction, target, toFaction, provinces);
            Choice back = choose(target, toFaction, from, fromFaction, provinces);
            if (forward != null) {
                added.add(forward.link());
                if (forward.assumed()) {
                    assumed = true;
                    blocks = Math.max(blocks, forward.link().distance());
                }
            }
            if (back != null) {
                added.add(back.link());
                if (back.assumed()) {
                    assumed = true;
                    blocks = Math.max(blocks, back.link().distance());
                }
            }
        }
        return new Planned(List.copyOf(added), assumed, blocks);
    }

    static Result compare(
            ProvinceManager live, Guild operator, Faction host, List<Link> links, Map<Integer, Double> extra) {
        ProvinceManager baseline = copy(live);
        Map<Guild, Double> before = EconomicPreview.projectNets(baseline);
        double gross = baseline.getGrossTradeIncome(operator);
        return diff(live, operator, host, before, gross, links, extra);
    }

    private static List<Destination> build(
            ProvinceManager live, Guild guild, List<Guild> guilds, List<Faction> factions, int cap) {
        List<Candidate> chosen = shortlist(guild, guilds, factions, live, cap);
        if (chosen.isEmpty()) {
            return List.of();
        }
        ProvinceManager baseline = copy(live);
        Map<Guild, Double> before = EconomicPreview.projectNets(baseline);
        double gross = baseline.getGrossTradeIncome(guild);
        List<Installation> partners = activeHubs(guild, guilds);
        List<Link> current = new ArrayList<>(HubNetwork.linksFor(guild));
        List<Destination> built = new ArrayList<>();
        for (Candidate candidate : chosen) {
            Planned planned = plan(partners, candidate.installation(), candidate.host().getId(), live);
            List<Link> links = new ArrayList<>(current);
            links.addAll(planned.added());
            Map<Integer, Double> extra = null;
            if (candidate.group() == Group.WORTH_BUILDING) {
                double amount = sourceAmount(candidate.installation().getKind());
                if (amount > 0) {
                    extra = Map.of(candidate.installation().getProvince(), amount);
                }
            }
            Result result = diff(live, guild, candidate.host(), before, gross, links, extra);
            String installationId = candidate.group() == Group.READY ? candidate.installation().getId() : null;
            built.add(new Destination(
                    candidate.group(),
                    candidate.host().getId(),
                    installationId,
                    candidate.installation().getName(),
                    candidate.installation().getProvince(),
                    candidate.installation().getKind(),
                    candidate.ownRealm(),
                    planned.assumedRailway(),
                    planned.assumedTrackBlocks(),
                    result.operatorGain(),
                    result.hostGain(),
                    result.hostGuildGains(),
                    result.taxableIncome()));
        }
        built.sort(Comparator.comparingInt((Destination destination) -> destination.group() == Group.READY ? 0 : 1)
                .thenComparing(Comparator.comparingDouble(Destination::operatorGain).reversed()));
        return built;
    }

    private static Result diff(
            ProvinceManager live, Guild operator, Faction host,
            Map<Guild, Double> before, double baselineGross, List<Link> links, Map<Integer, Double> extra) {
        ProvinceManager after = copy(live);
        if (operator.getId() != null && links != null) {
            after.setHubLinksOverride(Map.of(operator.getId(), List.copyOf(links)));
        }
        if (extra != null && !extra.isEmpty()) {
            after.setExtraInfrastructure(extra);
        }
        Map<Guild, Double> next = EconomicPreview.projectNets(after);
        double operatorGain = round(next.getOrDefault(operator, 0.0) - before.getOrDefault(operator, 0.0));
        double hostGain = 0;
        Map<String, Double> hostGuilds = new HashMap<>();
        Set<Guild> seen = new HashSet<>();
        seen.addAll(before.keySet());
        seen.addAll(next.keySet());
        for (Guild other : seen) {
            if (other == null || sameGuild(other, operator) || !sameRealm(host, other.getFaction())) {
                continue;
            }
            double delta = round(next.getOrDefault(other, 0.0) - before.getOrDefault(other, 0.0));
            hostGain += delta;
            if (delta != 0 && other.getId() != null) {
                hostGuilds.put(other.getId(), delta);
            }
        }
        double taxable = round(Math.max(0, after.getGrossTradeIncome(operator) - baselineGross));
        return new Result(operatorGain, round(hostGain), Map.copyOf(hostGuilds), taxable);
    }

    private static Map<String, Double> headlinesFor(ProvinceManager live, List<Faction> realms) {
        if (realms.isEmpty()) {
            return Map.of();
        }
        Map<Guild, Double> before = EconomicPreview.projectNets(copy(live));
        ProvinceManager off = copy(live);
        off.setInfrastructureSuppressed(true);
        Map<Guild, Double> after = EconomicPreview.projectNets(off);
        Map<String, Double> worth = new HashMap<>();
        for (Faction realm : realms) {
            if (realm == null || realm.getId() == null) {
                continue;
            }
            worth.put(realm.getId(), realmDelta(realm, after, before));
        }
        return worth;
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

    private record Choice(Link link, boolean assumed) {
    }

    private static Choice choose(
            Installation from, String fromFactionId, Installation to, String toFactionId, ProvinceManager provinces) {
        if (from == null || to == null || from.getProvince() == to.getProvince()) {
            return null;
        }
        Link real = provinces == null ? null : HubNetwork.connect(from, to, provinces);
        Link assumed = assumedRail(from, fromFactionId, to, toFactionId);
        if (real != null && real.mode() == Mode.RAIL) {
            return new Choice(real, false);
        }
        if (assumed != null && (real == null || assumed.tradeFactor() > real.tradeFactor())) {
            return new Choice(assumed, true);
        }
        return real == null ? null : new Choice(real, false);
    }

    private static Link assumedRail(Installation from, String fromFactionId, Installation to, String toFactionId) {
        if (from.getProvince() == to.getProvince()) {
            return null;
        }
        double dx = from.getCenterX() - to.getCenterX();
        double dz = from.getCenterZ() - to.getCenterZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (!Double.isFinite(distance) || !HubTransport.inRange(HubTransport.rates(Mode.RAIL), distance)) {
            return null;
        }
        return HubTransport.link(
                from, fromFactionId == null ? "" : fromFactionId,
                to, toFactionId == null ? "" : toFactionId,
                Mode.RAIL, distance);
    }

    private static List<Installation> activeHubs(Guild guild, List<Guild> guilds) {
        List<Installation> sites = new ArrayList<>();
        if (guild == null || guild.getSupplyHubs() == null) {
            return sites;
        }
        for (SupplyHub hub : guild.getSupplyHubs()) {
            Installation installation = SupplyHubService.findInstallation(hub.ownerFactionId(), hub.installationId());
            if (installation == null) {
                continue;
            }
            int slots = hubSlots(installation);
            boolean allowed = SupplyHubService.hubPermitted(guild, hub.ownerFactionId(), hub.installationId());
            SupplyHubService.HubStanding standing = SupplyHubService.standing(
                    guild, hub, true, allowed, slots,
                    SupplyHubService.atInstallation(hub.ownerFactionId(), hub.installationId(), guilds));
            if (standing.active()) {
                sites.add(installation);
            }
        }
        return sites;
    }

    private static List<Candidate> closest(List<Candidate> sites, int cap) {
        sites.sort(Comparator.comparingDouble(Candidate::distance));
        if (sites.size() <= cap) {
            return sites;
        }
        return new ArrayList<>(sites.subList(0, cap));
    }

    private static double closeness(
            Guild guild, int x, int z, List<Installation> hubs, ProvinceManager provinces) {
        double best = Double.POSITIVE_INFINITY;
        if (hubs != null) {
            for (Installation hub : hubs) {
                if (hub == null) {
                    continue;
                }
                best = Math.min(best, distance(hub.getCenterX(), hub.getCenterZ(), x, z));
            }
        }
        if (best < Double.POSITIVE_INFINITY) {
            return best;
        }
        if (guild != null && guild.hasCapital() && provinces != null) {
            Province capital = provinces.get(guild.getCapital());
            if (capital != null && capital.isValid()) {
                return distance(capital.getCenterX(), capital.getCenterZ(), x, z);
            }
        }
        return 0;
    }

    private static double distance(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean hubKind(InstallationKind kind) {
        return kind == InstallationKind.PORT
                || kind == InstallationKind.AIRPORT
                || kind == InstallationKind.TRAIN_STATION;
    }

    private static int hubSlots(Installation installation) {
        try {
            return InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel());
        } catch (IllegalStateException ex) {
            return SupplyHubService.defaultHubSlots(installation.getKind(), installation.getLevel());
        }
    }

    static double sourceAmount(InstallationKind kind) {
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

    private static String terrainLabel(Province province) {
        String name = province.getTerrain().name().toLowerCase(Locale.ROOT);
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private static boolean sameRealm(Faction left, Faction right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && left.getId().equalsIgnoreCase(right.getId())) {
            return true;
        }
        return RelationManager.sameRealm(left, right);
    }

    private static boolean sameGuild(Guild left, Guild right) {
        if (left == right) {
            return true;
        }
        return left != null && right != null && left.getId() != null && left.getId().equalsIgnoreCase(right.getId());
    }

    private static ProvinceManager copy(ProvinceManager live) {
        ProvinceManager snapshot = live.createSnapshotShell();
        snapshot.copyAllDataFrom(live);
        return snapshot;
    }

    private static double round(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        return Math.round(value * 100.0) / 100.0;
    }
}
