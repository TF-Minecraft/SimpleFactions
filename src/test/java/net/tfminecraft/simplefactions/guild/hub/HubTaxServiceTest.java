package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.guild.network.RailRoutes;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.Site;
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;

class HubTaxServiceTest {
    @Test
    void pairSharesTheGainBetweenBothEnds() {
        assertEquals(Map.of("a", 50.0, "b", 50.0),
                HubTaxService.taxableIncome(100, 200, Map.of("a", 100.0, "b", 100.0)));
    }

    @Test
    void chainScalesMarginalsProportionallyToTheGain() {
        Map<String, Double> taxable = HubTaxService.taxableIncome(
                100, 220, Map.of("a", 100.0, "b", 100.0, "c", 160.0));
        assertEquals(48, taxable.get("a"), 1e-9);
        assertEquals(48, taxable.get("b"), 1e-9);
        assertEquals(24, taxable.get("c"), 1e-9);
        assertEquals(120, taxable.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    void aHubThatAddsNothingGetsNoTaxableIncome() {
        assertEquals(Map.of("a", 30.0, "b", 0.0),
                HubTaxService.taxableIncome(100, 150, Map.of("a", 120.0, "b", 150.0)));
    }

    @Test
    void noGainOrNegativeMarginalsCannotBeTaxed() {
        assertEquals(Map.of("a", 0.0), HubTaxService.taxableIncome(200, 150, Map.of("a", 100.0)));
        assertEquals(Map.of("a", 0.0), HubTaxService.taxableIncome(100, 150, Map.of("a", 160.0)));
    }

    @Test
    void removingAHubOnlyRemovesItsOwnModeAtEitherEnd() {
        SupplyHub station = new SupplyHub("owner", "a", 0);
        SupplyHub port = new SupplyHub("owner", "port", 0);
        assertTrue(HubTaxService.touches(new Link(1, 2, "owner", "a", "owner", "b", Mode.RAIL, 0, 0.7, 0.25), station));
        assertTrue(HubTaxService.touches(new Link(2, 1, "owner", "b", "owner", "a", Mode.RAIL, 0, 0.7, 0.25), station));
        org.junit.jupiter.api.Assertions.assertFalse(
                HubTaxService.touches(new Link(1, 2, "owner", "c", "owner", "b", Mode.AIR, 0, 0.2, 0), station));
        org.junit.jupiter.api.Assertions.assertFalse(
                HubTaxService.touches(new Link(2, 3, "owner", "b", "owner", "c", Mode.RAIL, 0, 0.7, 0.25), station));
        Link stationRoute = new Link(1, 2, "owner", "a", "remote", "b", Mode.RAIL, 0, 0.7, 0.25);
        Link portRoute = new Link(1, 3, "owner", "port", "remote", "c", Mode.RAIL, 0, 0.7, 0.25);
        assertTrue(HubTaxService.touches(stationRoute, station));
        assertTrue(HubTaxService.touches(portRoute, port));
        org.junit.jupiter.api.Assertions.assertFalse(HubTaxService.touches(portRoute, station));
        org.junit.jupiter.api.Assertions.assertFalse(HubTaxService.touches(stationRoute, port));
    }

    @Test
    void connectionLinesAttributeSameProvinceHubsByInstallation() {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        SupplyHub portHub = new SupplyHub("home", "port", 1);
        SupplyHub stationHub = new SupplyHub("home", "station", 2);
        SupplyHub portDestinationHub = new SupplyHub("remote", "port-destination", 3);
        SupplyHub stationDestinationHub = new SupplyHub("remote", "station-destination", 4);
        when(guild.getSupplyHubs()).thenReturn(List.of(
                portHub, stationHub, portDestinationHub, stationDestinationHub));
        Installation port = new Installation("port", "Port", InstallationKind.PORT, 1, 0, 0, 0L);
        Installation station = new Installation("station", "Station", InstallationKind.TRAIN_STATION, 1, 0, 0, 0L);
        Installation portDestination = new Installation(
                "port-destination", "Port destination", InstallationKind.PORT, 2, 0, 0, 0L);
        Installation stationDestination = new Installation(
                "station-destination", "Station destination", InstallationKind.TRAIN_STATION, 3, 0, 0, 0L);
        InstallationHandler homeSites = mock(InstallationHandler.class);
        InstallationHandler remoteSites = mock(InstallationHandler.class);
        Faction home = mock(Faction.class);
        Faction remote = mock(Faction.class);
        when(home.getInstallationHandler()).thenReturn(homeSites);
        when(remote.getInstallationHandler()).thenReturn(remoteSites);
        when(homeSites.getById("port")).thenReturn(port);
        when(homeSites.getById("station")).thenReturn(station);
        when(remoteSites.getById("port-destination")).thenReturn(portDestination);
        when(remoteSites.getById("station-destination")).thenReturn(stationDestination);
        List<Link> links = List.of(
                new Link(1, 2, "home", "port", "remote", "port-destination", Mode.RAIL, 100, 0.7, 0.25),
                new Link(1, 3, "home", "station", "remote", "station-destination", Mode.RAIL, 200, 0.7, 0.25));
        HubNetwork.setLinksForTests(Map.of("guild", links));
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
            factions.when(() -> FactionManager.getByString("home")).thenReturn(home);
            factions.when(() -> FactionManager.getByString("remote")).thenReturn(remote);
            List<String> portLines = SupplyHubCommands.connectionLines(guild, portHub, port);
            List<String> stationLines = SupplyHubCommands.connectionLines(guild, stationHub, station);
            assertEquals(1, portLines.size());
            assertTrue(portLines.get(0).contains("Port destination"));
            assertEquals(1, stationLines.size());
            assertTrue(stationLines.get(0).contains("Station destination"));
        } finally {
            HubNetwork.setLinksForTests(null);
        }
    }

    @Test
    void snapshotsAssessForeignHubsWithoutChangingLiveDataOrTheNetwork() {
        boolean enabled = Cache.provincesEnabled;
        Map<Terrain, Double> carry = new HashMap<>(Cache.tradeCarry);
        Cache.provincesEnabled = true;
        Cache.tradeCarry.put(Terrain.PLAINS, 0.85);
        Guild guild = mock(Guild.class);
        Faction home = mock(Faction.class);
        Faction host = mock(Faction.class);
        Faction west = mock(Faction.class);
        when(home.getId()).thenReturn("home");
        when(home.hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(true);
        when(home.getRelations()).thenReturn(new HashMap<>());
        when(host.getId()).thenReturn("host");
        when(host.getRelations()).thenReturn(new HashMap<>());
        when(west.getId()).thenReturn("west");
        when(west.getRelations()).thenReturn(new HashMap<>());
        when(guild.getId()).thenReturn("guild");
        when(guild.getHubAgreements()).thenReturn(new ArrayList<>(List.of(
                new HubAgreement("host", "b", 50, 0, 14, true, true, null, false),
                new HubAgreement("west", "c", 10, 0, 14, true, true, null, false))));
        when(guild.getFaction()).thenReturn(home);
        when(guild.hasCapital()).thenReturn(true);
        when(guild.getCapital()).thenReturn(1);
        when(guild.getModifier(GuildModifier.TRADE_POWER)).thenReturn(20.0);
        when(guild.getModifier(GuildModifier.HUB_LIMIT)).thenReturn(4.0);
        when(guild.getModifier(GuildModifier.TRADE_CARRY)).thenReturn(1.0);
        when(guild.getModifier(GuildModifier.PRODUCTION)).thenReturn(10.0);
        TradeBreakdown trade = new TradeBreakdown();
        trade.setIncome(123);
        when(guild.getTradeBreakdown()).thenReturn(trade);
        SupplyHub first = new SupplyHub("home", "a", 1);
        SupplyHub last = new SupplyHub("host", "b", 2);
        SupplyHub other = new SupplyHub("west", "c", 3);
        SupplyHub dormant = new SupplyHub("host", "dormant", 4);
        when(guild.getSupplyHubs()).thenReturn(new ArrayList<>(List.of(first, last, other, dormant)));
        Installation a = new Installation("a", "A", InstallationKind.TRAIN_STATION, 1, 0, 0, 0L);
        Installation b = new Installation("b", "B", InstallationKind.TRAIN_STATION, 22, 100, 0, 0L);
        Installation c = new Installation("c", "C", InstallationKind.TRAIN_STATION, 12, 0, 0, 0L);
        InstallationHandler homeSites = mock(InstallationHandler.class);
        InstallationHandler hostSites = mock(InstallationHandler.class);
        InstallationHandler westSites = mock(InstallationHandler.class);
        when(home.getInstallationHandler()).thenReturn(homeSites);
        when(host.getInstallationHandler()).thenReturn(hostSites);
        when(west.getInstallationHandler()).thenReturn(westSites);
        when(homeSites.getById("a")).thenReturn(a);
        when(hostSites.getById("b")).thenReturn(b);
        when(westSites.getById("c")).thenReturn(c);
        ProvinceManager provinces = new ProvinceManager();
        Map<Integer, Province> map = new HashMap<>();
        for (int id = 1; id <= 24; id++) {
            map.put(id, new Province(id, Terrain.PLAINS.name(), 50));
        }
        for (int id = 1; id < 24; id++) {
            map.get(id).addNeighbour(id + 1);
            map.get(id + 1).addNeighbour(id);
        }
        provinces.start(map);
        YamlConfiguration rates = new YamlConfiguration();
        rates.set("supply-hubs.transport.rail.trade", 0.70);
        rates.set("supply-hubs.transport.rail.production", 0.25);
        HubTransport.loadConfig(rates);
        TradeGraph graph = TradeGraphBuilder.build(
                List.of(new Site("home", a, 1, true), new Site("host", b, 1, true), new Site("west", c, 1, true)),
                Map.of(),
                (from, to) -> {
                    int left = Math.min(from.getProvince(), to.getProvince());
                    int right = Math.max(from.getProvince(), to.getProvince());
                    if (left == 1 && (right == 12 || right == 22)) {
                        return Optional.of(new RailRoutes.Route(0, List.of()));
                    }
                    return Optional.empty();
                },
                point -> 0);
        Set<HubSite> hubs = Set.of(new HubSite("home", "a"), new HubSite("host", "b"), new HubSite("west", "c"));
        HubNetwork.setHighwayForTests(graph, Map.of("guild", hubs));
        List<Link> links = HubNetwork.linksFor(guild);
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
                MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<InstallationConfigLoader> config = mockStatic(InstallationConfigLoader.class)) {
            factions.when(() -> FactionManager.getByString("home")).thenReturn(home);
            factions.when(() -> FactionManager.getByString("host")).thenReturn(host);
            factions.when(() -> FactionManager.getByString("west")).thenReturn(west);
            factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(guild);
            factions.when(FactionManager::getAllGuilds).thenReturn(List.of(guild));
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(null);
            config.when(() -> InstallationConfigLoader.getHubSlots(InstallationKind.TRAIN_STATION, 1))
                    .thenReturn(1);
            provinces.recalculateForSingleGuild(guild, false);
            Map<Integer, ProvinceDataEntry> liveEntries = new HashMap<>();
            Map<Integer, Double> liveProsperity = new HashMap<>();
            Map<Integer, ProvinceDataEntry> liveValues = new HashMap<>();
            for (Province p : provinces.getProvinces()) {
                ProvinceDataEntry entry = p.getAllData().get("guild");
                liveEntries.put(p.getId(), entry);
                if (entry != null) {
                    liveValues.put(p.getId(), entry.copy());
                }
                liveProsperity.put(p.getId(), p.getProsperity());
            }
            ProvinceManager noLinks = provinces.createSnapshotShell();
            noLinks.copyAllDataFrom(provinces);
            noLinks.setHighwayOverride(graph, Map.of("guild", Set.of()));
            noLinks.recalculateForSingleGuild(guild, false);
            double gain = provinces.getGrossTradeIncome(guild) - noLinks.getGrossTradeIncome(guild);

            HubTaxBreakdown assessment = HubTaxService.assess(provinces, guild, List.of(guild));

            assertTrue(gain > 0);
            assertEquals(0, assessment.forHub(first).tax());
            assertTrue(assessment.forHub(last).taxableIncome() > 0);
            assertTrue(assessment.forHub(other).taxableIncome() > 0);
            assertEquals(assessment.forHub(last).taxableIncome() * 0.5, assessment.forHub(last).tax(), 1e-6);
            assertEquals(assessment.forHub(other).taxableIncome() * 0.1, assessment.forHub(other).tax(), 1e-6);
            assertEquals(assessment.forHub(last).taxableIncome(), assessment.getTaxableIncome(host), 1e-9);
            assertEquals(0, assessment.forHub(dormant).taxableIncome());
            for (Province p : provinces.getProvinces()) {
                assertSame(liveEntries.get(p.getId()), p.getAllData().get("guild"));
                assertEquals(liveProsperity.get(p.getId()), p.getProsperity());
                ProvinceDataEntry before = liveValues.get(p.getId());
                if (before != null) {
                    ProvinceDataEntry after = p.getAllData().get("guild");
                    assertEquals(before.getTrade(), after.getTrade());
                    assertEquals(before.getProduction(), after.getProduction());
                    assertEquals(before.getDistance(), after.getDistance());
                }
            }
            assertEquals(123, guild.getTradeBreakdown().getIncome());
            assertEquals(links, HubNetwork.linksFor(guild));
            try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
                relations.when(() -> RelationManager.sameRealm(host, home)).thenReturn(true);
                relations.when(() -> RelationManager.sameRealm(west, home)).thenReturn(true);
                HubTaxBreakdown exempt = HubTaxService.assess(provinces, guild, List.of(guild));
                assertEquals(0, exempt.getTotalTax());
                assertTrue(exempt.forHub(last).taxableIncome() > 0);
                assertTrue(exempt.forHub(other).taxableIncome() > 0);
            }
            HubNetwork.setHighwayForTests(null, null);
            assertEquals(0, HubTaxService.assess(provinces, guild, List.of(guild)).getTotalTax());
        } finally {
            HubNetwork.setHighwayForTests(null, null);
            HubTransport.resetConfig();
            Cache.provincesEnabled = enabled;
            Cache.tradeCarry.clear();
            Cache.tradeCarry.putAll(carry);
        }
    }

    @Test
    void oneHubTaxEqualsTheIncomeItAddsAndIsZeroWhenTradeArrivesAnyway() {
        boolean enabled = Cache.provincesEnabled;
        Map<Terrain, Double> carry = new HashMap<>(Cache.tradeCarry);
        double strength = Cache.supplyHubNoHubStrength;
        Cache.provincesEnabled = true;
        Cache.supplyHubNoHubStrength = 0.5;
        Cache.tradeCarry.put(Terrain.PLAINS, 0.85);
        try {
            YamlConfiguration rates = new YamlConfiguration();
            rates.set("supply-hubs.transport.rail.trade", 0.95);
            rates.set("supply-hubs.transport.rail.production", 0);
            HubTransport.loadConfig(rates);
            ProvinceManager road = road();
            Guild guild = capitalGuild();
            Faction home = guild.getFaction();
            Installation capital = new Installation("a", "A", InstallationKind.TRAIN_STATION, 1, 0, 0, 0L);
            Installation far = new Installation("far", "Far", InstallationKind.TRAIN_STATION, 7, 0, 0, 0L);
            Installation near = new Installation("near", "Near", InstallationKind.TRAIN_STATION, 2, 0, 0, 0L);
            SupplyHub hub = new SupplyHub("home", "a", 1);
            when(guild.getSupplyHubs()).thenReturn(List.of(hub));

            try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
                    MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
                factions.when(() -> FactionManager.getByString("home")).thenReturn(home);
                factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(guild);
                factions.when(FactionManager::getAllGuilds).thenReturn(List.of(guild));
                titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(null);

                TradeGraph reaches = rail(capital, far);
                Set<HubSite> active = Set.of(new HubSite("home", "a"));
                HubNetwork.setHighwayForTests(reaches, Map.of("guild", active));
                road.recalculateForSingleGuild(guild, false);
                double full = road.getGrossTradeIncome(guild);
                ProvinceManager bare = road.createSnapshotShell();
                bare.copyAllDataFrom(road);
                bare.setHighwayOverride(reaches, Map.of("guild", Set.of()));
                bare.recalculateForSingleGuild(guild, false);
                double without = bare.getGrossTradeIncome(guild);

                HubTaxBreakdown added = HubTaxService.assess(road, guild, List.of(guild));
                assertTrue(added.forHub(hub).taxableIncome() > 0);
                assertEquals(full - without, added.forHub(hub).taxableIncome(), 1e-6);
                assertEquals(0, added.forHub(hub).tax());

                TradeGraph local = rail(capital, near);
                HubNetwork.setHighwayForTests(local, Map.of("guild", active));
                HubTaxBreakdown idle = HubTaxService.assess(road, guild, List.of(guild));
                assertEquals(0, idle.forHub(hub).taxableIncome());
                assertEquals(0, idle.forHub(hub).tax());
            }
        } finally {
            HubNetwork.setHighwayForTests(null, null);
            HubTransport.resetConfig();
            Cache.provincesEnabled = enabled;
            Cache.supplyHubNoHubStrength = strength;
            Cache.tradeCarry.clear();
            Cache.tradeCarry.putAll(carry);
        }
    }

    private static TradeGraph rail(Installation from, Installation to) {
        return TradeGraphBuilder.build(
                List.of(new Site("home", from, 1, true), new Site("home", to, 1, true)),
                Map.of(),
                (left, right) -> Optional.of(new RailRoutes.Route(0, List.of())),
                point -> 0);
    }

    private static ProvinceManager road() {
        ProvinceManager provinces = new ProvinceManager();
        Map<Integer, Province> map = new HashMap<>();
        for (int id = 1; id <= 24; id++) {
            map.put(id, new Province(id, Terrain.PLAINS.name(), 50));
        }
        for (int id = 1; id < 24; id++) {
            map.get(id).addNeighbour(id + 1);
            map.get(id + 1).addNeighbour(id);
        }
        provinces.start(map);
        return provinces;
    }

    private static Guild capitalGuild() {
        Faction home = mock(Faction.class);
        when(home.getId()).thenReturn("home");
        when(home.hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(true);
        when(home.getRelations()).thenReturn(new HashMap<>());
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        when(guild.getFaction()).thenReturn(home);
        when(guild.hasCapital()).thenReturn(true);
        when(guild.getCapital()).thenReturn(1);
        when(guild.getModifier(GuildModifier.TRADE_POWER)).thenReturn(20.0);
        when(guild.getModifier(GuildModifier.TRADE_CARRY)).thenReturn(1.0);
        when(guild.getModifier(GuildModifier.PRODUCTION)).thenReturn(10.0);
        when(guild.getModifier(GuildModifier.HUB_LIMIT)).thenReturn(1.0);
        when(guild.getHubAgreements()).thenReturn(new ArrayList<>());
        return guild;
    }
}
