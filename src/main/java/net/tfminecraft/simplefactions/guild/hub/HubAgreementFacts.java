package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Network;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * World lookups the agreement rules ask for. The live instance reads factions.
 * Tests pass their own, so the daily tick does not need a server.
 */
public interface HubAgreementFacts {
    boolean sameRealm(Guild guild, String factionId);

    boolean hostExists(String factionId);

    boolean hostHasHubTax(String factionId);

    int minRate(String factionId);

    int maxRate(String factionId);

    double maxFee();

    int offerDays();

    int agreementDays();

    boolean installationExists(String factionId, String installationId);

    boolean allowsSupplyHubs(Guild guild);

    int hubSlots(String factionId, String installationId);

    int hubLimit(Guild guild);

    int hubsAtInstallation(String factionId, String installationId);

    double tradePower(Guild guild, String factionId, String installationId);

    boolean isGuildLeader(Guild guild, String actorName);

    boolean isHostCouncil(String factionId, String actorName);

    List<String> council(String factionId);

    String guildLeader(Guild guild);

    boolean hubDormant(Guild guild, SupplyHub hub);

    String installationLabel(String factionId, String installationId);

    /**
     * What {@link HubPlacement#mayPlace} needs for this installation.
     * The default is own land, so a test that is not about networks still builds.
     */
    default HubPlacement.Inputs placementInputs(Guild guild, String hostFactionId, String installationId) {
        return HubPlacement.Inputs.ownLand();
    }

    HubAgreementFacts LIVE = new LiveHubAgreementFacts();
}

final class LiveHubAgreementFacts implements HubAgreementFacts {
    @Override
    public boolean sameRealm(Guild guild, String factionId) {
        if (guild == null || factionId == null) {
            return false;
        }
        Faction guildFaction = guild.getFaction();
        if (guildFaction != null && guildFaction.getId() != null
                && guildFaction.getId().equalsIgnoreCase(factionId)) {
            return true;
        }
        Faction other = FactionManager.getByString(factionId);
        return other != null && RelationManager.sameRealm(other, guildFaction);
    }

    @Override
    public boolean hostExists(String factionId) {
        return FactionManager.getByString(factionId) != null;
    }

    @Override
    public boolean hostHasHubTax(String factionId) {
        Faction host = FactionManager.getByString(factionId);
        return host != null && host.hasFactionRule(Rules.HUB_TAX);
    }

    @Override
    public int minRate(String factionId) {
        HubAgreementService.RateRange range = range(factionId);
        return range.canHost() ? range.minPercent() : 0;
    }

    @Override
    public int maxRate(String factionId) {
        HubAgreementService.RateRange range = range(factionId);
        return range.canHost() ? range.maxPercent() : 0;
    }

    private static HubAgreementService.RateRange range(String factionId) {
        return HubAgreementService.allowedRateRange(FactionManager.getByString(factionId));
    }

    @Override
    public double maxFee() {
        return Cache.supplyHubMaxFee;
    }

    @Override
    public int offerDays() {
        return Cache.supplyHubOfferDays;
    }

    @Override
    public int agreementDays() {
        return Cache.supplyHubAgreementDays;
    }

    @Override
    public boolean installationExists(String factionId, String installationId) {
        return SupplyHubService.installationExists(factionId, installationId);
    }

    @Override
    public boolean allowsSupplyHubs(Guild guild) {
        return SupplyHubService.allowsSupplyHubs(guild);
    }

    @Override
    public int hubSlots(String factionId, String installationId) {
        Installation installation = SupplyHubService.findInstallation(factionId, installationId);
        if (installation == null) {
            return 0;
        }
        return InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel());
    }

    @Override
    public int hubLimit(Guild guild) {
        return SupplyHubService.limit(guild);
    }

    @Override
    public int hubsAtInstallation(String factionId, String installationId) {
        return SupplyHubService.countLoaded(factionId, installationId);
    }

    @Override
    public HubPlacement.Inputs placementInputs(Guild guild, String hostFactionId, String installationId) {
        TradeGraph graph = HighwaySnapshot.current().graph();
        Node node = graph == null ? null : graph.node(hostFactionId, installationId);
        Network network = graph == null || node == null ? null : graph.networkOf(node);
        List<SupplyHub> hubs = guild == null || guild.getSupplyHubs() == null ? List.of() : guild.getSupplyHubs();
        return new HubPlacement.Inputs(
                network, HubPlacement.joinedNetworks(graph, hubs), HubPlacement.ownLand(guild, hostFactionId));
    }

    @Override
    public double tradePower(Guild guild, String factionId, String installationId) {
        Installation installation = SupplyHubService.findInstallation(factionId, installationId);
        if (guild == null || installation == null) {
            return 0;
        }
        return SupplyHubCommands.tradeAvailable(guild, installation);
    }

    @Override
    public boolean isGuildLeader(Guild guild, String actorName) {
        if (guild == null || actorName == null || guild.getLeader() == null) {
            return false;
        }
        return guild.getLeader().equalsIgnoreCase(actorName);
    }

    @Override
    public boolean isHostCouncil(String factionId, String actorName) {
        Faction host = FactionManager.getByString(factionId);
        return host != null && host.getGovernment() != null && host.getGovernment().isCouncilMember(actorName);
    }

    @Override
    public List<String> council(String factionId) {
        List<String> names = new ArrayList<>();
        Faction faction = FactionManager.getByString(factionId);
        if (faction == null) {
            return names;
        }
        if (faction.getLeader() != null && !faction.getLeader().isBlank()) {
            names.add(faction.getLeader());
        }
        if (faction.getGovernment() != null) {
            for (String member : faction.getGovernment().getCouncilMembers()) {
                if (member == null || member.isBlank()) {
                    continue;
                }
                boolean seen = false;
                for (String name : names) {
                    if (name.equalsIgnoreCase(member)) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    names.add(member);
                }
            }
        }
        return names;
    }

    @Override
    public String guildLeader(Guild guild) {
        return guild == null ? null : guild.getLeader();
    }

    @Override
    public boolean hubDormant(Guild guild, SupplyHub hub) {
        if (guild == null || hub == null) {
            return false;
        }
        Installation installation = SupplyHubService.findInstallation(hub.ownerFactionId(), hub.installationId());
        int slots = installation == null
                ? 0
                : InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel());
        boolean allowed = SupplyHubService.hubPermitted(guild, hub.ownerFactionId(), hub.installationId());
        SupplyHubService.HubStanding standing = SupplyHubService.standing(
                guild, hub, installation != null, allowed, slots,
                SupplyHubService.atInstallation(
                        hub.ownerFactionId(), hub.installationId(), SupplyHubService.allGuilds()));
        return !standing.active();
    }

    @Override
    public String installationLabel(String factionId, String installationId) {
        Installation installation = SupplyHubService.findInstallation(factionId, installationId);
        if (installation == null || installation.getName() == null || installation.getName().isBlank()) {
            return installationId == null ? "installation" : installationId;
        }
        return installation.getName();
    }
}
