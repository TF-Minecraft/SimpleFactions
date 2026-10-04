package net.tfminecraft.simplefactions.guild.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Network;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;

/**
 * Names, hub counts, and member shares for the trade networks.
 * The numbers come from a graph and a trade lookup, with no server types.
 */
public final class NetworkSummary {
    /** A guild whose trade and name can be shown. {@code id} is the guild id. */
    public record GuildLabel(String id, String name) { }

    /** One guild's share of the trade at a network's stops, already a whole percent. */
    public record Member(String guildId, String name, int percent) { }

    /** Trade a guild has in one province, before any foreign-trade bonus. */
    @FunctionalInterface
    public interface RawTrade {
        double inProvince(String guildId, int provinceId);
    }

    /**
     * One network as the viewer and the map export show it.
     * {@code members} is at most five. {@code otherPercent} is the rest of those shares,
     * or zero when no further line is shown.
     */
    public record Summary(
            Network network,
            String name,
            int hubs,
            int slots,
            List<Member> members,
            int otherPercent,
            double tradeTotal,
            Map<Node, Integer> hubsByNode) {
        public Summary {
            members = List.copyOf(members);
            hubsByNode = Map.copyOf(hubsByNode);
        }

        public int nodes() {
            return network.size();
        }

        public boolean global() {
            return network.global();
        }

        /** Active hubs of every guild at this stop. */
        public int hubsAt(Node node) {
            return node == null ? 0 : hubsByNode.getOrDefault(node, 0);
        }

        /** Member lines, then {@code - Other (X%)} when more than five guilds remain. */
        public List<String> memberLines() {
            List<String> lines = new ArrayList<>();
            for (Member member : members) {
                lines.add("- " + member.name() + " (" + member.percent() + "%)");
            }
            if (otherPercent > 0) {
                lines.add("- Other (" + otherPercent + "%)");
            }
            return List.copyOf(lines);
        }
    }

    private static final Comparator<Summary> DISPLAY_ORDER = Comparator
            .comparingInt(Summary::nodes).reversed()
            .thenComparingInt((Summary summary) -> summary.global() ? 0 : 1)
            .thenComparingInt(summary -> lowestProvince(summary.network()))
            .thenComparing(Summary::name);

    private NetworkSummary() { }

    /**
     * Names every network. The global one uses {@code mapName}. Any other uses the guild
     * with the most trade at its stops, or the faction that owns the most stops when no
     * guild has trade there. A repeated name keeps the plain form on the largest network.
     * {@code hubbed} is each guild's active hubs. A guild missing from it has none.
     */
    public static List<Summary> summarize(
            TradeGraph graph,
            Map<String, Set<HubSite>> hubbed,
            List<GuildLabel> guilds,
            Map<String, String> factionNames,
            String mapName,
            RawTrade rawTrade) {
        if (graph == null || graph.networks().isEmpty()) return List.of();
        RawTrade trade = rawTrade == null ? (guildId, provinceId) -> 0 : rawTrade;
        Map<String, Set<HubSite>> hubs = indexHubs(hubbed);
        List<GuildLabel> labels = labels(guilds);
        Map<String, String> factions = indexFactions(factionNames);
        String chapter = mapLabel(mapName);
        List<Draft> drafts = new ArrayList<>();
        for (Network network : graph.networks()) {
            drafts.add(draft(network, hubs, labels, factions, chapter, trade));
        }
        assignNames(drafts);
        List<Summary> summaries = new ArrayList<>();
        for (Draft draft : drafts) summaries.add(draft.toSummary());
        summaries.sort(DISPLAY_ORDER);
        return List.copyOf(summaries);
    }

    /** The summary whose stops include this node, or null. */
    public static Summary containing(List<Summary> summaries, Node node) {
        if (summaries == null || node == null) return null;
        for (Summary summary : summaries) {
            if (summary.network() != null && summary.network().nodes().contains(node)) return summary;
        }
        return null;
    }

    /** Section-sign colours are removed so a name can take a prefix. */
    public static String plain(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '§' && i + 1 < value.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    private static Draft draft(
            Network network,
            Map<String, Set<HubSite>> hubs,
            List<GuildLabel> labels,
            Map<String, String> factions,
            String chapter,
            RawTrade rawTrade) {
        Set<Integer> provinces = new HashSet<>();
        for (Node node : network.nodes()) provinces.add(node.provinceId());
        List<GuildScore> scores = new ArrayList<>();
        double total = 0;
        for (GuildLabel label : labels) {
            double amount = 0;
            for (int provinceId : provinces) {
                amount += amount(rawTrade, label.id(), provinceId);
            }
            scores.add(new GuildScore(label.id(), label.name(), amount));
            total += amount;
        }
        scores.sort(Comparator.comparingDouble(GuildScore::trade).reversed()
                .thenComparing(GuildScore::label)
                .thenComparing(GuildScore::id));
        GuildScore best = scores.isEmpty() || scores.getFirst().trade() <= 0 ? null : scores.getFirst();
        String base = network.global()
                ? titled(chapter, false)
                : best != null ? titled(best.label(), true) : titled(topFaction(network, factions), false);
        List<Member> members = new ArrayList<>();
        if (total > 0) {
            for (GuildScore score : scores) {
                if (score.trade() <= 0) continue;
                int percent = (int) Math.round(score.trade() * 100.0 / total);
                if (percent <= 0) continue;
                members.add(new Member(score.id(), score.label(), percent));
            }
        }
        int other = 0;
        if (members.size() > 5) {
            for (int i = 5; i < members.size(); i++) other += members.get(i).percent();
            members = new ArrayList<>(members.subList(0, 5));
        }
        return new Draft(network, base, countHubs(network, hubs), total, members, other);
    }

    private static HubCount countHubs(Network network, Map<String, Set<HubSite>> hubs) {
        Map<HubSite, Node> bySite = new HashMap<>();
        int slots = 0;
        for (Node node : network.nodes()) {
            bySite.putIfAbsent(new HubSite(node.ownerFactionId(), node.installationId()), node);
            slots += Math.max(0, node.hubSlots());
        }
        Map<Node, Integer> byNode = new HashMap<>();
        int count = 0;
        for (Set<HubSite> sites : hubs.values()) {
            for (HubSite site : sites) {
                Node node = site == null ? null : bySite.get(site);
                if (node == null) continue;
                count++;
                byNode.merge(node, 1, Integer::sum);
            }
        }
        return new HubCount(count, slots, byNode);
    }

    private static void assignNames(List<Draft> drafts) {
        Map<String, List<Draft>> groups = new LinkedHashMap<>();
        for (Draft draft : drafts) {
            groups.computeIfAbsent(draft.baseName, key -> new ArrayList<>()).add(draft);
        }
        for (List<Draft> group : groups.values()) {
            group.sort(Comparator.comparingInt((Draft draft) -> draft.network.size()).reversed()
                    .thenComparingInt(draft -> lowestProvince(draft.network))
                    .thenComparing(draft -> lowestInstallation(draft.network)));
            for (int i = 0; i < group.size(); i++) {
                Draft draft = group.get(i);
                draft.name = i == 0 ? draft.baseName : draft.baseName + " " + roman(i + 1);
            }
        }
    }

    private static String topFaction(Network network, Map<String, String> factions) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Node node : network.nodes()) {
            if (node.ownerFactionId() == null || node.ownerFactionId().isBlank()) continue;
            String key = node.ownerFactionId().toLowerCase(Locale.ROOT);
            counts.merge(key, 1, Integer::sum);
            names.putIfAbsent(key, factionLabel(key, node.ownerFactionId(), factions));
        }
        String bestKey = null;
        String bestName = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String name = names.get(entry.getKey());
            int count = entry.getValue();
            boolean better = bestKey == null || count > bestCount
                    || (count == bestCount && (name.compareTo(bestName) < 0
                            || (name.equals(bestName) && entry.getKey().compareTo(bestKey) < 0)));
            if (better) {
                bestKey = entry.getKey();
                bestName = name;
                bestCount = count;
            }
        }
        return bestName == null ? "Unknown" : bestName;
    }

    private static String factionLabel(String key, String rawId, Map<String, String> factions) {
        String named = factions.get(key);
        if (named == null || named.isEmpty()) named = plain(rawId);
        return named.isEmpty() ? "Unknown" : named;
    }

    /** A guild name that already starts with "The " is not prefixed again. */
    private static String titled(String label, boolean guild) {
        if (guild && label.startsWith("The ")) return label + " Network";
        return "The " + label + " Network";
    }

    private static String mapLabel(String mapName) {
        String plain = plain(mapName);
        return plain.isEmpty() ? "Unknown" : plain;
    }

    private static int lowestProvince(Network network) {
        int lowest = Integer.MAX_VALUE;
        for (Node node : network.nodes()) lowest = Math.min(lowest, node.provinceId());
        return lowest;
    }

    private static String lowestInstallation(Network network) {
        String lowest = "";
        boolean found = false;
        for (Node node : network.nodes()) {
            String id = node.installationId() == null ? "" : node.installationId();
            if (!found || id.compareToIgnoreCase(lowest) < 0) {
                lowest = id;
                found = true;
            }
        }
        return lowest;
    }

    private static double amount(RawTrade rawTrade, String guildId, int provinceId) {
        double value = rawTrade.inProvince(guildId, provinceId);
        return Double.isFinite(value) && value > 0 ? value : 0;
    }

    private static Map<String, Set<HubSite>> indexHubs(Map<String, Set<HubSite>> hubbed) {
        Map<String, Set<HubSite>> indexed = new HashMap<>();
        if (hubbed == null) return indexed;
        for (Map.Entry<String, Set<HubSite>> entry : hubbed.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) continue;
            Set<HubSite> sites = indexed.computeIfAbsent(
                    entry.getKey().toLowerCase(Locale.ROOT), key -> new HashSet<>());
            for (HubSite site : entry.getValue()) {
                if (site != null) sites.add(site);
            }
        }
        return indexed;
    }

    private static List<GuildLabel> labels(List<GuildLabel> guilds) {
        Map<String, GuildLabel> byId = new LinkedHashMap<>();
        if (guilds == null) return List.of();
        for (GuildLabel guild : guilds) {
            if (guild == null || guild.id() == null || guild.id().isBlank()) continue;
            String name = plain(guild.name());
            if (name.isEmpty()) name = plain(guild.id());
            if (name.isEmpty()) name = "Unknown";
            byId.putIfAbsent(guild.id().toLowerCase(Locale.ROOT), new GuildLabel(guild.id(), name));
        }
        return List.copyOf(byId.values());
    }

    private static Map<String, String> indexFactions(Map<String, String> factionNames) {
        Map<String, String> names = new HashMap<>();
        if (factionNames == null) return names;
        for (Map.Entry<String, String> entry : factionNames.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) continue;
            names.putIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), plain(entry.getValue()));
        }
        return names;
    }

    private static String roman(int value) {
        int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder roman = new StringBuilder();
        int left = value;
        for (int i = 0; i < numbers.length && left > 0; i++) {
            while (left >= numbers[i]) {
                roman.append(letters[i]);
                left -= numbers[i];
            }
        }
        return roman.toString();
    }

    private record GuildScore(String id, String label, double trade) { }

    private record HubCount(int hubs, int slots, Map<Node, Integer> byNode) { }

    private static final class Draft {
        private final Network network;
        private final String baseName;
        private final int hubs;
        private final int slots;
        private final double tradeTotal;
        private final List<Member> members;
        private final int otherPercent;
        private final Map<Node, Integer> hubsByNode;
        private String name;

        private Draft(
                Network network, String baseName, HubCount hubs, double tradeTotal,
                List<Member> members, int otherPercent) {
            this.network = network;
            this.baseName = baseName;
            this.hubs = hubs.hubs();
            this.slots = hubs.slots();
            this.tradeTotal = tradeTotal;
            this.members = members;
            this.otherPercent = otherPercent;
            this.hubsByNode = hubs.byNode();
        }

        private Summary toSummary() {
            return new Summary(network, name, hubs, slots, members, otherPercent, tradeTotal, hubsByNode);
        }
    }
}
