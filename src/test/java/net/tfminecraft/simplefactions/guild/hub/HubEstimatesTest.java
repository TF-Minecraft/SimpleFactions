package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Candidate;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Destination;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Group;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.InstallationPreview;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Planned;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Result;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Terms;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.guild.income.Ledger;
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
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;

class HubEstimatesTest {
    @AfterEach
    void reset() {
        IncomePreviewContext.clear();
        GuildModifierOverride.clear();
        HubNetwork.setLinksForTests(null);
        HubNetwork.setRailRoutesForTests(null);
        HubEstimates.clearForTests();
    }

    @Test
    void suppressedInfrastructureClearsAndExtraSourceSkipsSea() {
        boolean enabled = Cache.provincesEnabled;
        Cache.provincesEnabled = true;
        Province land = new Province(1, "bog", 0);
        Province sea = new Province(2, "sea", 0);
        ProvinceManager manager = new ProvinceManager();
        manager.start(Map.of(1, land, 2, sea));
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
            factions.when(FactionManager::getAllGuilds).thenReturn(List.of());
            factions.when(FactionManager::getCopy).thenReturn(List.of());
            manager.setExtraInfrastructure(Map.of(1, 20.0, 2, 20.0));
            manager.recalculate();
            assertEquals(20, manager.get(1).getInfrastructure(), 1e-9);
            assertEquals(0, manager.get(2).getInfrastructure(), 1e-9);

            manager.setInfrastructureSuppressed(true);
            manager.recalculate();
            assertEquals(0, manager.get(1).getInfrastructure(), 1e-9);
            assertEquals(0, manager.get(2).getInfrastructure(), 1e-9);
        } finally {
            Cache.provincesEnabled = enabled;
        }
    }

    @Test
    void missingRailwayUsesAStraightLineAndTermsAreArithmetic() {
        Installation from = new Installation("a", "Home", InstallationKind.TRAIN_STATION, 1, 0, 0, 0L);
        Installation to = new Installation("b", "Away", InstallationKind.TRAIN_STATION, 2, 3000, 0, 0L);
        HubNetwork.setRailRoutesForTests((left, right) -> OptionalDouble.empty());
        Planned planned = HubEstimates.plan(List.of(from), to, "host", new ProvinceManager());

        assertTrue(planned.assumedRailway());
        assertEquals(3000, planned.assumedTrackBlocks(), 1e-6);
        assertEquals(2, planned.added().size());
        assertEquals(Mode.RAIL, planned.added().get(0).mode());
        assertEquals(3000, planned.added().get(0).distance(), 1e-6);

        Destination foreign = destination(false, 80, -20, 100);
        Terms terms = HubEstimates.applyTerms(foreign, 10, 500);
        assertEquals(65, terms.operatorNet(), 1e-9);
        assertEquals(-5, terms.hostNet(), 1e-9);
        assertEquals(10, terms.tax(), 1e-9);
        assertEquals(5, terms.fee(), 1e-9);

        Terms clamped = HubEstimates.applyTerms(foreign, 150, -20);
        assertEquals(0, clamped.fee(), 1e-9);
        assertEquals(100, clamped.tax(), 1e-9);

        Terms own = HubEstimates.applyTerms(destination(true, 80, 4, 100), 10, 500);
        assertEquals(80, own.operatorNet(), 1e-9);
        assertEquals(4, own.hostNet(), 1e-9);
        assertEquals(0, own.tax(), 1e-9);
        assertEquals(0, own.fee(), 1e-9);
    }

    @Test
    void aHubLinkRaisesTheOperatorsNetAndThatRiseIsTaxable() {
        Road road = road(24);
        Link link = new Link(1, 22, Mode.RAIL, 0, 0.4, 0.8);
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
                MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
            factions.when(FactionManager::getAllGuilds).thenReturn(List.of(road.guild));
            factions.when(FactionManager::getCopy).thenReturn(List.of());
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(null);
            Result result = HubEstimates.compare(road.provinces, road.guild, road.faction, List.of(link), null);
            assertTrue(result.operatorGain() > 0, "operator gain " + result.operatorGain());
            assertTrue(result.taxableIncome() > 0, "taxable " + result.taxableIncome());
            assertEquals(0, result.hostGain(), 1e-9);
        } finally {
            road.restore();
        }
    }

    @Test
    void shortlistKeepsFreeHubsAndTheClosestProvinces() {
        Faction home = faction("home", true);
        Faction foreign = faction("foreign", false);
        Faction taxed = faction("taxed", true);
        Guild guild = guild(home, 1, List.of(new SupplyHub("home", "mine", 1L)));
        Installation near = station("near", 1, 0, 0);
        Installation mine = station("mine", 12, 50, 0);
        Installation taken = station("taken", 3, 80, 0);
        Installation far = station("far", 4, 10_000, 0);
        Installation abroad = station("abroad", 5, 90, 0);
        Installation fort = new Installation("fort", "Fort", InstallationKind.FORT, 2, 10, 0, 0L);
        InstallationHandler homeSites = handler(near, mine, taken, fort, far);
        InstallationHandler foreignSites = handler(abroad);
        InstallationHandler taxedSites = handler(station("taxed", 11, 8_000, 0));
        when(home.getInstallationHandler()).thenReturn(homeSites);
        when(foreign.getInstallationHandler()).thenReturn(foreignSites);
        when(taxed.getInstallationHandler()).thenReturn(taxedSites);

        int slots = stationSlots();
        List<Guild> guilds = new ArrayList<>();
        guilds.add(guild);
        for (int i = 0; i < slots; i++) {
            Guild occupant = mock(Guild.class);
            when(occupant.getSupplyHubs()).thenReturn(List.of(new SupplyHub("home", "taken", i)));
            guilds.add(occupant);
        }

        ProvinceManager provinces = new ProvinceManager();
        Map<Integer, Province> map = new HashMap<>();
        map.put(1, new Province(1, Terrain.PLAINS.name(), 50, 0, 0));
        map.put(6, new Province(6, Terrain.PLAINS.name(), 50, 100, 0));
        map.put(7, new Province(7, Terrain.PLAINS.name(), 50, 5_000, 0));
        map.put(8, new Province(8, Terrain.SEA.name(), 50, 30, 0));
        map.put(9, new Province(9, Terrain.PLAINS.name(), 50, 40, 0));
        map.put(10, new Province(10, Terrain.PLAINS.name(), 50, 60, 0));
        map.put(11, new Province(11, Terrain.PLAINS.name(), 50, 8_000, 0));
        map.put(13, new Province(13, Terrain.PLAINS.name(), 50, 9_000, 0));
        provinces.start(map);

        try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(null);
            titles.when(() -> TitleManager.getByProvince(6)).thenReturn(home);
            titles.when(() -> TitleManager.getByProvince(7)).thenReturn(home);
            titles.when(() -> TitleManager.getByProvince(8)).thenReturn(home);
            titles.when(() -> TitleManager.getByProvince(10)).thenReturn(foreign);
            titles.when(() -> TitleManager.getByProvince(11)).thenReturn(taxed);
            titles.when(() -> TitleManager.getByProvince(13)).thenReturn(taxed);
            relations.when(() -> RelationManager.sameRealm(any(), any())).thenAnswer(invocation -> {
                Faction left = invocation.getArgument(0);
                Faction right = invocation.getArgument(1);
                return left != null && right != null && left.getId().equalsIgnoreCase(right.getId());
            });

            List<Candidate> listed = HubEstimates.shortlist(
                    guild, guilds, List.of(home, foreign, taxed), provinces, 10);
            assertTrue(listed.stream().anyMatch(candidate -> "near".equals(candidate.installation().getId())));
            assertTrue(listed.stream().anyMatch(candidate -> "far".equals(candidate.installation().getId())));
            assertTrue(listed.stream().anyMatch(candidate -> candidate.installation().getProvince() == 6
                    && candidate.group() == Group.WORTH_BUILDING));
            assertTrue(listed.stream().anyMatch(candidate -> "taxed".equals(candidate.installation().getId())
                    && candidate.group() == Group.READY));
            assertTrue(listed.stream().anyMatch(candidate -> candidate.installation().getProvince() == 13
                    && candidate.group() == Group.WORTH_BUILDING));
            assertFalse(listed.stream().anyMatch(candidate -> "mine".equals(candidate.installation().getId())
                    && candidate.group() == Group.READY));
            assertFalse(listed.stream().anyMatch(candidate -> "taken".equals(candidate.installation().getId())));
            assertFalse(listed.stream().anyMatch(candidate -> "abroad".equals(candidate.installation().getId())));
            assertFalse(listed.stream().anyMatch(candidate -> "fort".equals(candidate.installation().getId())));
            assertFalse(listed.stream().anyMatch(candidate -> {
                int province = candidate.installation().getProvince();
                return province == 8 || province == 9 || province == 10;
            }));

            List<Candidate> capped = HubEstimates.shortlist(
                    guild, guilds, List.of(home, foreign, taxed), provinces, 1);
            assertEquals(1, capped.stream().filter(candidate -> candidate.group() == Group.READY).count());
            assertEquals("near", capped.stream().filter(candidate -> candidate.group() == Group.READY).findFirst().orElseThrow().installation().getId());
            assertEquals(6, capped.stream().filter(candidate -> candidate.group() == Group.WORTH_BUILDING).findFirst().orElseThrow().installation().getProvince());
        }
    }

    @Test
    void aFewHundredProvincesFinishWithinTenSeconds() {
        Road road = road(300);
        Link link = new Link(1, 200, Mode.RAIL, 0, 0.4, 0.8);
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
                MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
            factions.when(FactionManager::getAllGuilds).thenReturn(List.of(road.guild));
            factions.when(FactionManager::getCopy).thenReturn(List.of());
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(null);
            long started = System.nanoTime();
            HubEstimates.compare(road.provinces, road.guild, road.faction, List.of(link), null);
            long millis = (System.nanoTime() - started) / 1_000_000L;
            assertTrue(millis < 10_000, "synthetic estimate took " + millis + " ms");
        } finally {
            road.restore();
        }
    }

    @Test
    void infrastructureWorthAndANewStationArePositive() {
        // A capital alone ignores terrain. The neighbour is what infrastructure changes.
        boolean enabled = Cache.provincesEnabled;
        Map<Terrain, Double> carry = new HashMap<>(Cache.tradeCarry);
        Cache.provincesEnabled = true;
        Cache.tradeCarry.clear();
        Cache.tradeCarry.put(Terrain.BOG, 0.4);
        Province capital = new Province(1, Terrain.BOG.name(), 50, 0, 0);
        Province neighbour = new Province(2, Terrain.BOG.name(), 50, 100, 0);
        capital.addNeighbour(2);
        neighbour.addNeighbour(1);
        ProvinceManager provinces = new ProvinceManager();
        provinces.start(Map.of(1, capital, 2, neighbour));
        Faction home = faction("home", true);
        Guild operator = guild(home, 1, List.of());
        InstallationHandler sites = handler(station("station", 1, 0, 0));
        when(home.getInstallationHandler()).thenReturn(sites);
        double previousStation = Cache.infrastructureStation;
        double previousFull = Cache.infrastructureFull;
        double previousTarget = Cache.infrastructureTarget;
        Cache.infrastructureStation = 10;
        Cache.infrastructureFull = 20;
        Cache.infrastructureTarget = 0.75;
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
                MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
            factions.when(FactionManager::getAllGuilds).thenReturn(List.of(operator));
            factions.when(FactionManager::getCopy).thenReturn(List.of(home));
            factions.when(() -> FactionManager.getByString("home")).thenReturn(home);
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(home);
            when(home.hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(true);
            HubEstimates.rebuild(provinces, List.of(operator), List.of(home));
            assertTrue(HubEstimates.infrastructureWorth(home) > 0,
                    "worth " + HubEstimates.infrastructureWorth(home));

            InstallationPreview preview = HubEstimates.previewInstallation(
                    provinces, home, 2, InstallationKind.TRAIN_STATION);
            assertEquals(10, preview.infrastructureHere(), 1e-9);
            assertTrue(preview.realmPerDay() > 0, "realm " + preview.realmPerDay());
            assertTrue(preview.upkeep() > 0);
            assertEquals(InstallationPreview.NONE, HubEstimates.previewInstallation(
                    provinces, home, 2, null));
        } finally {
            Cache.infrastructureStation = previousStation;
            Cache.infrastructureFull = previousFull;
            Cache.infrastructureTarget = previousTarget;
            Cache.provincesEnabled = enabled;
            Cache.tradeCarry.clear();
            Cache.tradeCarry.putAll(carry);
        }
    }

    @Test
    void scheduleDoesNothingWithoutAPluginAndCandidatesDefaultToTwentyFour() {
        assertNull(SimpleFactions.getInstance());
        HubEstimates.scheduleDaily();
        int previous = Cache.supplyHubEstimateCandidates;
        double previousTax = Cache.supplyHubMaxTax;
        double previousFee = Cache.supplyHubMaxFee;
        int previousOffer = Cache.supplyHubOfferDays;
        int previousAgreement = Cache.supplyHubAgreementDays;
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("supply-hubs.estimate-candidates", 0);
            SupplyHubService.loadConfig(config);
            assertEquals(1, Cache.supplyHubEstimateCandidates);
            SupplyHubService.loadConfig(null);
            assertEquals(24, Cache.supplyHubEstimateCandidates);
        } finally {
            Cache.supplyHubEstimateCandidates = previous;
            Cache.supplyHubMaxTax = previousTax;
            Cache.supplyHubMaxFee = previousFee;
            Cache.supplyHubOfferDays = previousOffer;
            Cache.supplyHubAgreementDays = previousAgreement;
        }
    }

    private static Destination destination(boolean ownRealm, double operator, double host, double taxable) {
        return new Destination(
                Group.READY, "host", "station", "Station", 2, InstallationKind.TRAIN_STATION,
                ownRealm, true, 3000, operator, host, Map.of(), taxable);
    }

    private static int stationSlots() {
        try {
            return InstallationConfigLoader.getHubSlots(InstallationKind.TRAIN_STATION, 1);
        } catch (IllegalStateException ex) {
            return SupplyHubService.defaultHubSlots(InstallationKind.TRAIN_STATION, 1);
        }
    }

    private static Installation station(String id, int province, int x, int z) {
        return new Installation(id, id, InstallationKind.TRAIN_STATION, province, x, z, 0L);
    }

    private static InstallationHandler handler(Installation... installations) {
        InstallationHandler handler = mock(InstallationHandler.class);
        when(handler.getAll()).thenReturn(List.of(installations));
        return handler;
    }

    private static Faction faction(String id, boolean hubTax) {
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn(id);
        lenient().when(faction.hasFactionRule(Rules.HUB_TAX)).thenReturn(hubTax);
        lenient().when(faction.hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(true);
        lenient().when(faction.getModifiers(any(), any(), any())).thenReturn(List.of());
        return faction;
    }

    private static Guild guild(Faction faction, int capital, List<SupplyHub> hubs) {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        when(guild.getFaction()).thenReturn(faction);
        when(guild.hasCapital()).thenReturn(true);
        when(guild.getCapital()).thenReturn(capital);
        when(guild.getSupplyHubs()).thenReturn(hubs);
        when(guild.isBase()).thenReturn(false);
        when(guild.isBankrupt()).thenReturn(false);
        when(guild.getBank()).thenReturn(mock(Bank.class));
        when(guild.getTradeBreakdown()).thenReturn(new TradeBreakdown());
        when(guild.getLedger()).thenReturn(new Ledger(guild));
        lenient().when(guild.getModifier(any())).thenReturn(0.0);
        when(guild.getModifier(GuildModifier.TRADE_POWER)).thenReturn(20.0);
        when(guild.getModifier(GuildModifier.TRADE_CARRY)).thenReturn(1.0);
        when(guild.getModifier(GuildModifier.PRODUCTION)).thenReturn(10.0);
        return guild;
    }

    private static Road road(int count) {
        boolean enabled = Cache.provincesEnabled;
        Map<Terrain, Double> carry = new HashMap<>(Cache.tradeCarry);
        Cache.provincesEnabled = true;
        Cache.tradeCarry.clear();
        Cache.tradeCarry.put(Terrain.PLAINS, 0.85);
        Cache.tradeCarry.put(Terrain.BOG, 0.4);
        ProvinceManager provinces = new ProvinceManager();
        Map<Integer, Province> map = new HashMap<>();
        for (int id = 1; id <= count; id++) {
            String terrain = count == 1 ? Terrain.BOG.name() : Terrain.PLAINS.name();
            map.put(id, new Province(id, terrain, 50, id * 100, 0));
            if (id > 1) {
                map.get(id).addNeighbour(id - 1);
                map.get(id - 1).addNeighbour(id);
            }
        }
        provinces.start(map);
        Faction faction = faction("home", true);
        Guild guild = guild(faction, 1, List.of());
        return new Road(provinces, faction, guild, enabled, carry);
    }

    private record Road(
            ProvinceManager provinces, Faction faction, Guild guild, boolean enabled, Map<Terrain, Double> carry) {
        void restore() {
            Cache.provincesEnabled = enabled;
            Cache.tradeCarry.clear();
            Cache.tradeCarry.putAll(carry);
        }
    }
}
