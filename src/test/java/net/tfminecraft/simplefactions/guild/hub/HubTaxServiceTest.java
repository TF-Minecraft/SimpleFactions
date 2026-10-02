package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
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
        when(home.getId()).thenReturn("home");
        when(home.hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(true);
        when(host.getId()).thenReturn("host");
        when(host.hasHubPermit("guild")).thenReturn(true);
        when(host.getTaxRate(TaxTarget.HUB_TAX, null, true)).thenReturn(50.0);
        when(guild.getId()).thenReturn("guild");
        when(guild.getFaction()).thenReturn(home);
        when(guild.hasCapital()).thenReturn(true);
        when(guild.getCapital()).thenReturn(1);
        when(guild.getModifier(GuildModifier.TRADE_POWER)).thenReturn(20.0);
        when(guild.getModifier(GuildModifier.HUB_LIMIT)).thenReturn(3.0);
        when(guild.getModifier(GuildModifier.TRADE_CARRY)).thenReturn(1.0);
        when(guild.getModifier(GuildModifier.PRODUCTION)).thenReturn(10.0);
        TradeBreakdown trade = new TradeBreakdown();
        trade.setIncome(123);
        when(guild.getTradeBreakdown()).thenReturn(trade);
        SupplyHub first = new SupplyHub("home", "a", 1);
        SupplyHub last = new SupplyHub("host", "b", 2);
        SupplyHub dormant = new SupplyHub("host", "dormant", 3);
        when(guild.getSupplyHubs()).thenReturn(new ArrayList<>(List.of(first, last, dormant)));
        Installation a = new Installation("a", "A", InstallationKind.TRAIN_STATION, 1, 0, 0, 0L);
        Installation b = new Installation("b", "B", InstallationKind.TRAIN_STATION, 22, 100, 0, 0L);
        InstallationHandler homeSites = mock(InstallationHandler.class);
        InstallationHandler hostSites = mock(InstallationHandler.class);
        when(home.getInstallationHandler()).thenReturn(homeSites);
        when(host.getInstallationHandler()).thenReturn(hostSites);
        when(homeSites.getById("a")).thenReturn(a);
        when(hostSites.getById("b")).thenReturn(b);
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
        List<Link> links = List.of(new Link(1, 22, "home", "a", "host", "b", Mode.RAIL, 0, 0.7, 0.25),
                new Link(22, 1, "host", "b", "home", "a", Mode.RAIL, 0, 0.7, 0.25));
        HubNetwork.setLinksForTests(Map.of("guild", links));
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
                MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<InstallationConfigLoader> config = mockStatic(InstallationConfigLoader.class)) {
            factions.when(() -> FactionManager.getByString("home")).thenReturn(home);
            factions.when(() -> FactionManager.getByString("host")).thenReturn(host);
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
            noLinks.setHubLinksOverride(Map.of("guild", List.of()));
            noLinks.recalculateForSingleGuild(guild, false);
            double gain = provinces.getGrossTradeIncome(guild) - noLinks.getGrossTradeIncome(guild);

            HubTaxBreakdown assessment = HubTaxService.assess(provinces, guild, List.of(guild));

            assertTrue(gain > 0);
            assertEquals(gain / 2, assessment.forHub(first).taxableIncome(), 1e-9);
            assertEquals(gain / 2, assessment.forHub(last).taxableIncome(), 1e-9);
            assertEquals(0, assessment.forHub(first).tax());
            assertEquals(gain / 4, assessment.forHub(last).tax(), 1e-9);
            assertEquals(gain / 2, assessment.getTaxableIncome(host), 1e-9);
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
                HubTaxBreakdown exempt = HubTaxService.assess(provinces, guild, List.of(guild));
                assertEquals(0, exempt.getTotalTax());
                assertEquals(gain / 2, exempt.getTaxableIncome(host), 1e-9);
                assertTrue(exempt.forHub(last).taxableIncome() > 0);
            }
            HubNetwork.setLinksForTests(null);
            assertEquals(0, HubTaxService.assess(provinces, guild, List.of(guild)).getTotalTax());
        } finally {
            HubNetwork.setLinksForTests(null);
            Cache.provincesEnabled = enabled;
            Cache.tradeCarry.clear();
            Cache.tradeCarry.putAll(carry);
        }
    }
}
