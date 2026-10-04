package net.tfminecraft.simplefactions.map.provinces;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;

class ProvinceTradeCarryTest {
    private final Map<Terrain, Double> previousCarry = new HashMap<>(Cache.tradeCarry);
    private final boolean previousEnabled = Cache.provincesEnabled;
    private final double previousMaxUpkeep = Cache.maxTradeUpkeep;
    private MockedStatic<TitleManager> titles;
    private Guild guild;

    @BeforeEach
    void setUp() {
        Cache.provincesEnabled = true;
        Cache.maxTradeUpkeep = 0.75;
        Cache.tradeCarry.put(Terrain.PLAINS, 0.75);
        Cache.tradeCarry.put(Terrain.BOG, 0.4);
        Cache.tradeCarry.put(Terrain.SEA, 0.6);
        Cache.tradeCarry.put(Terrain.WATER, 0.75);
        titles = mockStatic(TitleManager.class);
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn("realm");
        guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        when(guild.getFaction()).thenReturn(faction);
        when(guild.getTradeBreakdown()).thenReturn(new TradeBreakdown());
        when(guild.getModifier(GuildModifier.TRADE_CARRY)).thenReturn(1.0);
    }

    @AfterEach
    void restore() {
        titles.close();
        Cache.tradeCarry.clear();
        Cache.tradeCarry.putAll(previousCarry);
        Cache.provincesEnabled = previousEnabled;
        Cache.maxTradeUpkeep = previousMaxUpkeep;
    }

    @Test
    void tradeProductionAndUpkeepUseRawTerrain() {
        Province province = new Province(1, "bog", 0);
        ProvinceManager manager = manager(Map.of(1, province));
        province.calculateTrade(manager, guild, 10, 1);

        assertEquals(4.0, province.getRawGuildTrade(guild), 1e-9);
        double factor = 4.0 / (4.0 + 2.5 / Math.sqrt(0.4));
        assertEquals(factor, province.getTradeFactor(guild), 1e-9);
        province.calculateProduction(manager, guild, new ProvinceDataEntry(guild, 0, 10), 1);
        assertEquals(10 * Math.sqrt(0.4) * factor, province.getGuildProduction(guild), 1e-9);
        when(guild.getModifier(GuildModifier.TRADE_UPKEEP)).thenReturn(1.0);
        province.setProsperity(100);
        double expectedNet = Math.round(100 * (1 - factor) * 100) / 100.0;
        assertEquals(expectedNet, manager.getIncome(guild, false), 1e-9);
        assertEquals(0.4, province.getTradeCarry());
        assertEquals(0.4, Cache.getTradeCarry(Terrain.BOG));
    }

    @Test
    void hubProductionSpreadUsesRawTerrain() {
        Province source = new Province(1, "plains", 0);
        Province destination = new Province(2, "bog", 0);
        source.addNeighbour(2);
        destination.setData(guild.getId(), new ProvinceDataEntry(guild, 10, 0));
        ProvinceManager manager = manager(Map.of(1, source, 2, destination));
        source.seedProduction(manager, guild, 100);

        double factor = 10 / (10 + 2.5 / Math.sqrt(0.4));
        assertEquals(100 * Math.sqrt(0.4) * factor, destination.getGuildProduction(guild), 1e-9);
    }

    @Test
    void seaAndWaterUseTheirConfiguredCarry() {
        for (Terrain terrain : List.of(Terrain.SEA, Terrain.WATER)) {
            Province province = new Province(1, terrain.name(), 0);
            assertEquals(Cache.getTradeCarry(terrain), province.getTradeCarry());
        }
    }

    private static ProvinceManager manager(Map<Integer, Province> provinces) {
        ProvinceManager manager = new ProvinceManager();
        manager.start(provinces);
        return manager;
    }
}
