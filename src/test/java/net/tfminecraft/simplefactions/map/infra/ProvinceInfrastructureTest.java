package net.tfminecraft.simplefactions.map.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.guild.hub.HubNetwork;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;

class ProvinceInfrastructureTest {
    private final Map<Terrain, Double> previousCarry = new HashMap<>(Cache.tradeCarry);
    private final boolean previousEnabled = Cache.provincesEnabled;
    private final double previousFull = Cache.infrastructureFull;
    private final double previousTarget = Cache.infrastructureTarget;
    private final double previousWilderness = Cache.infrastructureWildernessSpread;
    private final double previousFloor = Cache.infrastructureSpreadFloor;
    private final double previousStation = Cache.infrastructureStation;
    private final double previousMaxUpkeep = Cache.maxTradeUpkeep;
    private MockedStatic<FactionManager> factions;
    private MockedStatic<TitleManager> titles;
    private MockedStatic<HubNetwork> hubs;
    private Guild guild;
    private Faction faction;

    @BeforeEach
    void setUp() {
        Cache.provincesEnabled = true;
        Cache.infrastructureFull = 20;
        Cache.infrastructureTarget = 0.75;
        Cache.infrastructureWildernessSpread = 0.25;
        Cache.infrastructureSpreadFloor = 0.5;
        Cache.infrastructureStation = 10;
        Cache.maxTradeUpkeep = 0.75;
        for (Terrain terrain : Terrain.values()) Cache.tradeCarry.put(terrain, 0.5);
        Cache.tradeCarry.put(Terrain.FARMLAND, 0.8);
        Cache.tradeCarry.put(Terrain.PLAINS, 0.75);
        Cache.tradeCarry.put(Terrain.BOG, 0.4);
        Cache.tradeCarry.put(Terrain.MOUNTAIN, 0.3);
        factions = mockStatic(FactionManager.class);
        titles = mockStatic(TitleManager.class);
        hubs = mockStatic(HubNetwork.class);
        faction = mock(Faction.class);
        when(faction.getId()).thenReturn("realm");
        guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        when(guild.getFaction()).thenReturn(faction);
        when(guild.getTradeBreakdown()).thenReturn(new TradeBreakdown());
        when(guild.getModifier(GuildModifier.TRADE_CARRY)).thenReturn(1.0);
    }

    @AfterEach
    void restore() {
        GuildModifierOverride.clear();
        hubs.close();
        titles.close();
        factions.close();
        Cache.tradeCarry.clear();
        Cache.tradeCarry.putAll(previousCarry);
        Cache.provincesEnabled = previousEnabled;
        Cache.infrastructureFull = previousFull;
        Cache.infrastructureTarget = previousTarget;
        Cache.infrastructureWildernessSpread = previousWilderness;
        Cache.infrastructureSpreadFloor = previousFloor;
        Cache.infrastructureStation = previousStation;
        Cache.maxTradeUpkeep = previousMaxUpkeep;
    }

    @Test
    void anEmptySourceMapLeavesGuildCarryEqualToRawOnEveryTerrain() {
        Map<Integer, Province> map = new HashMap<>();
        for (Terrain terrain : Terrain.values()) {
            int id = terrain.ordinal() + 1;
            map.put(id, new Province(id, terrain.name(), 0));
        }
        ProvinceManager manager = manager(map);
        manager.recalculate();

        for (Province province : manager.getProvinces()) {
            assertEquals(0, province.getInfrastructure());
            assertEquals(province.getTradeCarry(), province.getTradeCarry(guild));
        }
    }

    @Test
    void bothRecalculationPathsRebuildSourcesFromCurrentModifiers() {
        Province first = new Province(1, "bog", 0);
        Province second = new Province(2, "plains", 0);
        first.addNeighbour(2);
        second.addNeighbour(1);
        titles.when(() -> TitleManager.getByProvince(1)).thenReturn(faction);
        titles.when(() -> TitleManager.getByProvince(2)).thenReturn(faction);
        capitalSource(10);
        ProvinceManager manager = manager(Map.of(1, first, 2, second));

        manager.recalculate();
        assertEquals(10, first.getInfrastructure());
        assertEquals(7.5, second.getInfrastructure(), 1e-9);
        assertEquals(0.575, first.getTradeCarry(guild), 1e-9);
        when(guild.getModifier(GuildModifier.INFRASTRUCTURE)).thenReturn(20.0);
        manager.recalculateForSingleGuild(guild, false);
        assertEquals(20, first.getInfrastructure());
        assertEquals(15, second.getInfrastructure(), 1e-9);
        when(guild.getModifier(GuildModifier.INFRASTRUCTURE)).thenReturn(0.0);
        manager.recalculate();
        assertEquals(0, first.getInfrastructure());
        assertEquals(first.getTradeCarry(), first.getTradeCarry(guild));
    }

    @Test
    void snapshotsRebuildTheirOwnInfrastructureAndDoNotChangeTheLiveMap() {
        Province capital = new Province(1, "bog", 0);
        titles.when(() -> TitleManager.getByProvince(1)).thenReturn(faction);
        capitalSource(10);
        ProvinceManager live = manager(Map.of(1, capital));
        live.recalculate();
        EconomicPreview.Prepared prepared = EconomicPreview.prepare(live);
        ProvinceManager snapshot = EconomicPreview.copyOf(prepared);

        assertNotSame(capital, snapshot.get(1));
        assertEquals(10, snapshot.get(1).getInfrastructure());
        GuildModifierOverride.use(guild, Map.of(GuildModifier.INFRASTRUCTURE, 20.0));
        snapshot.recalculate();
        GuildModifierOverride.clear();

        assertEquals(20, snapshot.get(1).getInfrastructure());
        assertEquals(10, capital.getInfrastructure());
        assertEquals(10, EconomicPreview.copyOf(prepared).get(1).getInfrastructure());
    }

    @Test
    void snapshotsReadInstallationsAtRecalculationTime() {
        InstallationHandler handler = mock(InstallationHandler.class);
        when(faction.getInstallationHandler()).thenReturn(handler);
        factions.when(FactionManager::getCopy).thenReturn(List.of(faction));
        ProvinceManager live = manager(Map.of(1, new Province(1, "bog", 0)));
        EconomicPreview.Prepared prepared = EconomicPreview.prepare(live);
        Installation station = mock(Installation.class);
        when(station.getKind()).thenReturn(InstallationKind.TRAIN_STATION);
        when(station.getProvince()).thenReturn(1);
        when(handler.getAll()).thenReturn(List.of(station));
        ProvinceManager snapshot = EconomicPreview.copyOf(prepared);
        snapshot.recalculate();

        assertEquals(10, snapshot.get(1).getInfrastructure());
        assertEquals(0, live.get(1).getInfrastructure());
        when(handler.getAll()).thenReturn(List.of());
        snapshot.recalculate();
        assertEquals(0, snapshot.get(1).getInfrastructure());
    }

    @Test
    void singleGuildRecalculationRebuildsInstallationsEvenWithoutACapital() {
        InstallationHandler handler = mock(InstallationHandler.class);
        Installation station = mock(Installation.class);
        when(station.getKind()).thenReturn(InstallationKind.TRAIN_STATION);
        when(station.getProvince()).thenReturn(1);
        when(handler.getAll()).thenReturn(List.of(station));
        when(faction.getInstallationHandler()).thenReturn(handler);
        factions.when(FactionManager::getCopy).thenReturn(List.of(faction));
        ProvinceManager manager = manager(Map.of(1, new Province(1, "bog", 0)));

        manager.recalculateForSingleGuild(guild, false);

        assertEquals(10, manager.get(1).getInfrastructure());
    }

    @Test
    void effectiveTerrainIsUsedByTradeProductionAndUpkeep() {
        Province province = new Province(1, "bog", 0);
        province.setInfrastructure(20);
        ProvinceManager manager = manager(Map.of(1, province));
        province.calculateTrade(manager, guild, 10, 1);

        assertEquals(7.5, province.getRawGuildTrade(guild), 1e-9);
        double factor = 7.5 / (7.5 + 2.5 / Math.sqrt(0.75));
        assertEquals(factor, province.getTradeFactor(guild), 1e-9);
        province.calculateProduction(manager, guild, new ProvinceDataEntry(guild, 0, 10), 1);
        assertEquals(10 * Math.sqrt(0.75) * factor, province.getGuildProduction(guild), 1e-9);
        when(guild.getModifier(GuildModifier.TRADE_UPKEEP)).thenReturn(1.0);
        province.setProsperity(100);
        double expectedNet = Math.round(100 * (1 - factor) * 100) / 100.0;
        assertEquals(expectedNet, manager.getIncome(guild, false), 1e-9);
        assertEquals(0.4, province.getTradeCarry());
        assertEquals(0.4, Cache.getTradeCarry(Terrain.BOG));
    }

    @Test
    void hubProductionSpreadUsesEffectiveTerrain() {
        Province source = new Province(1, "plains", 0);
        Province destination = new Province(2, "bog", 0);
        source.addNeighbour(2);
        destination.setInfrastructure(20);
        destination.setData(guild.getId(), new ProvinceDataEntry(guild, 10, 0));
        ProvinceManager manager = manager(Map.of(1, source, 2, destination));
        source.seedProduction(manager, guild, 100);

        double factor = 10 / (10 + 2.5 / Math.sqrt(0.75));
        assertEquals(100 * Math.sqrt(0.75) * factor, destination.getGuildProduction(guild), 1e-9);
    }

    @Test
    void seaAndWaterCarryRemainRawEvenIfInfrastructureIsSet() {
        for (Terrain terrain : List.of(Terrain.SEA, Terrain.WATER)) {
            Province province = new Province(1, terrain.name(), 0);
            province.setInfrastructure(20);
            assertEquals(province.getTradeCarry(), province.getTradeCarry(guild));
        }
    }

    private void capitalSource(double amount) {
        when(guild.isBase()).thenReturn(true);
        when(guild.hasCapital()).thenReturn(true);
        when(guild.getCapital()).thenReturn(1);
        when(guild.getModifier(GuildModifier.INFRASTRUCTURE)).thenReturn(amount);
        factions.when(FactionManager::getAllGuilds).thenReturn(List.of(guild));
    }

    private static ProvinceManager manager(Map<Integer, Province> provinces) {
        ProvinceManager manager = new ProvinceManager();
        manager.start(provinces);
        return manager;
    }
}
