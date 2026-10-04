package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Site;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;

class HubEstimatesTest {

    @AfterEach
    void clearHighway() {
        HubNetwork.setHighwayForTests(null, null);
        HubTransport.resetConfig();
    }

    @Test
    void arrivesUsesTheSnapshotAndDropsAMissingEdge() {
        HubTransport.resetConfig();
        Guild guild = guild();
        Installation near = station("near", 1, 0, 0);
        Installation richer = station("richer", 3, 0, 0);
        Installation target = station("target", 2, 1000, 0);
        ProvinceManager provinces = provinces(guild, Map.of(1, 10.0, 2, 4.0, 3, 40.0));
        var linked = TestGraphs.linked("home", 1000, near, richer, target);
        // Rail over 1000 blocks keeps 0.36. Hubs no longer change the richer end's 14.4 arrival.
        HubNetwork.setHighwayForTests(linked, Map.of("bog", TestGraphs.hubs("home", near)));
        assertEquals(14.4, HubEstimates.arrives(guild, target, List.of(near), provinces), 0.01);
        HubNetwork.setHighwayForTests(linked, Map.of("bog", TestGraphs.hubs("home", near, richer)));
        assertEquals(14.4, HubEstimates.arrives(guild, target, List.of(near, richer), provinces), 0.01);
        HubNetwork.setHighwayForTests(linked, Map.of());
        assertEquals(14.4, HubEstimates.arrives(guild, target, List.of(), provinces), 0.01);

        HubNetwork.setHighwayForTests(
                TestGraphs.rail("home", List.of(near, target), (from, to) -> false, 0), Map.of());
        assertEquals(0, HubEstimates.arrives(guild, target, List.of(near), provinces), 0.01);
    }

    @Test
    void mainHubFiltersKeepOwnTradeTiesAndLaterHubsSortByArrival() {
        Site high = site("a", true, 20, 1, true);
        Site tied = site("b", true, 20, 0, false);
        Site low = site("c", true, 5, 9, true);
        Site empty = site("d", true, 0, 4, true);
        Site foreign = site("e", false, 50, 8, true);

        assertEquals(List.of("a", "b", "c"), ids(HubEstimates.withTrade(HubEstimates.territory(
                List.of(high, tied, low, empty, foreign)))));
        assertEquals(List.of("a", "b"), ids(HubEstimates.bestTerritory(List.of(high, tied, low, foreign))));
        assertEquals(List.of("a", "b", "c"), ids(HubEstimates.byTradePower(List.of(low, high, tied))));
        assertEquals(List.of("c", "e", "d", "a", "b"), ids(HubEstimates.byArrival(
                List.of(high, tied, low, empty, foreign))));
        assertEquals(List.of("c", "e", "d", "a"), ids(HubEstimates.byArrival(HubEstimates.connected(
                List.of(high, tied, low, empty, foreign)))));
    }

    @Test
    void sitesUseLiveInstallationsAndDeliverFromAnExistingHub() {
        List<Faction> previousFactions = new ArrayList<>(FactionManager.factions);
        FactionManager.factions.clear();
        HubTransport.resetConfig();
        try {

        Faction home = faction("home");
        Faction foreign = faction("foreign");
        when(foreign.hasFactionRule(Rules.HUB_TAX)).thenReturn(false);
        InstallationHandler homeSites = mock(InstallationHandler.class);
        InstallationHandler foreignSites = mock(InstallationHandler.class);
        Installation capital = station("capital", 1, 0, 0);
        Installation next = station("next", 2, 1000, 0);
        Installation quiet = station("quiet", 4, 250, 0);
        Installation abroad = station("abroad", 3, 500, 0);
        when(home.getInstallationHandler()).thenReturn(homeSites);
        when(foreign.getInstallationHandler()).thenReturn(foreignSites);
        when(homeSites.getAll()).thenReturn(List.of(
                capital, next, quiet, station("fort", InstallationKind.FORT, 5, 0, 0)));
        when(foreignSites.getAll()).thenReturn(List.of(abroad));
        FactionManager.factions.add(home);
        FactionManager.factions.add(foreign);

        Guild guild = guild();
        when(guild.getFaction()).thenReturn(home);
        when(guild.getSupplyHubs()).thenReturn(List.of(new SupplyHub("home", "capital", 1)));
        when(guild.getModifier(any())).thenReturn(0.0);
        when(guild.getModifier(GuildModifier.HUB_LIMIT)).thenReturn(1.0);
        when(home.hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(true);

        ProvinceManager provinces = provinces(guild, Map.of(1, 10.0, 2, 4.0, 4, 0.0));
        List<Site> listed = HubEstimates.sites(guild, provinces, List.of(home, foreign), List.of(guild));

        assertEquals(List.of("next", "quiet"), ids(listed));
        assertTrue(HubEstimates.connected(listed).isEmpty());
        Site delivered = listed.stream().filter(site -> site.installationId().equals("next")).findFirst().orElseThrow();
        assertEquals(0, delivered.arrivesHere(), 0.01);
        assertFalse(delivered.connected());
        } finally {
            FactionManager.factions.clear();
            FactionManager.factions.addAll(previousFactions);
            HubTransport.resetConfig();
        }
    }

    private static List<String> ids(List<Site> sites) {
        return sites.stream().map(Site::installationId).toList();
    }

    private static Site site(String id, boolean ownRealm, double tradeHere, double arrivesHere, boolean connected) {
        return new Site("home", id, id, 1, InstallationKind.TRAIN_STATION, ownRealm,
                tradeHere, arrivesHere, connected);
    }

    private static Guild guild() {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn("bog");
        return guild;
    }

    private static Faction faction(String id) {
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn(id);
        return faction;
    }

    private static Installation station(String id, int province, int x, int z) {
        return station(id, InstallationKind.TRAIN_STATION, province, x, z);
    }

    private static Installation station(String id, InstallationKind kind, int province, int x, int z) {
        return new Installation(id, id, kind, province, x, z, 0L);
    }

    private static ProvinceManager provinces(Guild guild, Map<Integer, Double> trade) {
        Map<Integer, Province> map = new java.util.HashMap<>();
        for (Map.Entry<Integer, Double> entry : trade.entrySet()) {
            Province province = new Province(entry.getKey(), Terrain.PLAINS.name(), 50, 0, 0);
            province.setData(guild.getId(), new ProvinceDataEntry(guild, entry.getValue(), 0));
            map.put(entry.getKey(), province);
        }
        ProvinceManager provinces = new ProvinceManager();
        provinces.start(map);
        return provinces;
    }
}
