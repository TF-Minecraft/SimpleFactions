package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
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
import net.tfminecraft.simplefactions.diplomacy.DiplomacyHandler;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubNetwork;
import net.tfminecraft.simplefactions.guild.hub.HubTransport;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

/** Trade power and production carried between supply hubs during the province recalculation. */
class ProvinceManagerHubTransportTest {
    private static final int LAST = 24;

    private ProvinceManager provinces;
    private Guild guild;
    private MockedStatic<TitleManager> titles;
    private boolean provincesWereEnabled;

    @BeforeEach
    void setUp() {
        provincesWereEnabled = Cache.provincesEnabled;
        Cache.provincesEnabled = true;
        Cache.tradeCarry.clear();
        Cache.tradeCarry.put(Terrain.PLAINS, 0.85);

        // A straight road of plains, 1 to LAST, with the guild's capital at 1.
        provinces = new ProvinceManager();
        Map<Integer, Province> map = new HashMap<>();
        for (int id = 1; id <= LAST; id++) {
            map.put(id, new Province(id, Terrain.PLAINS.name(), 50));
        }
        for (int id = 1; id < LAST; id++) {
            map.get(id).addNeighbour(id + 1);
            map.get(id + 1).addNeighbour(id);
        }
        provinces.start(map);

        Faction host = mock(Faction.class);
        when(host.getId()).thenReturn("host");
        guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        when(guild.getFaction()).thenReturn(host);
        when(guild.hasCapital()).thenReturn(true);
        when(guild.getCapital()).thenReturn(1);
        when(guild.getModifier(GuildModifier.TRADE_POWER)).thenReturn(20.0);
        when(guild.getModifier(GuildModifier.TRADE_CARRY)).thenReturn(1.0);
        when(guild.getModifier(GuildModifier.PRODUCTION)).thenReturn(10.0);

        titles = mockStatic(TitleManager.class);
        titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        titles.close();
        HubNetwork.setLinksForTests(null);
        Cache.provincesEnabled = provincesWereEnabled;
    }

    @Test
    void withoutHubs_powerFadesAlongTheRoad() {
        recalculate();

        assertEquals(20, trade(1), 1e-9);
        assertEquals(17, trade(2), 1e-9);
        assertEquals(0, trade(12));
        assertEquals(10, production(1), 1e-9);
    }

    @Test
    void hubDeliversAShareAndItSpreadsFromThere() {
        link(new Link(1, 22, Mode.RAIL, 0, 0.7, 0.25));

        recalculate();

        assertEquals(14, trade(22), 1e-9);
        assertEquals(14 * 0.85, trade(21), 1e-9);
        assertEquals(14 * 0.85, trade(23), 1e-9);
        assertEquals(0, provinces.get(22).getData("guild").getDistance());
        assertEquals(2.5, production(22), 1e-9);
        assertTrue(production(21) > 0 && production(21) < 2.5);
        // The road in between gets nothing from the journey.
        assertEquals(0, trade(12));
    }

    @Test
    void hubNeverLowersWhatAlreadyArrives() {
        link(new Link(1, 2, Mode.RAIL, 0, 0.5, 0.25));

        recalculate();

        assertEquals(17, trade(2), 1e-9);
    }

    @Test
    void deliveryIsComparedBeforeTheForeignTradeBonus() {
        // Province 2 belongs to an unstable foreign nation, so the guild's 17 there is stored as 25.5.
        Faction owner = mock(Faction.class);
        Government government = mock(Government.class);
        when(owner.getId()).thenReturn("owner");
        when(owner.getGovernment()).thenReturn(government);
        when(owner.getDiplomacyHandler()).thenReturn(mock(DiplomacyHandler.class));
        when(government.getStability()).thenReturn(0.0);
        titles.when(() -> TitleManager.getByProvince(2)).thenReturn(owner);
        link(new Link(1, 2, Mode.RAIL, 0, 0.9, 0));

        try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
            recalculate();
        }

        // 18 delivered beats the 17 that arrived by road, though it is below the stored 25.5.
        assertEquals(18, provinces.get(2).getRawGuildTrade(guild), 1e-9);
        assertEquals(27, trade(2), 1e-9);
    }

    @Test
    void powerTravelsAlongAChainAndLosesAShareAtEachHub() {
        link(new Link(1, 8, Mode.RAIL, 0, 0.7, 0.25), new Link(8, 14, Mode.RAIL, 0, 0.7, 0.25));

        recalculate();

        assertEquals(14, trade(8), 1e-9);
        assertEquals(9.8, trade(14), 1e-9);
        assertEquals(2.5, production(8), 1e-9);
        assertEquals(0.625, production(14), 1e-9);
    }

    @Test
    void chainSettlesWhateverOrderTheLinksAreIn() {
        link(new Link(8, 14, Mode.RAIL, 0, 0.7, 0.25), new Link(1, 8, Mode.RAIL, 0, 0.7, 0.25));

        recalculate();

        assertEquals(9.8, trade(14), 1e-9);
    }

    @Test
    void aRingOfHubsCannotGrowPower() {
        link(
                new Link(1, 12, Mode.RAIL, 0, 0.95, 0.95),
                new Link(12, 1, Mode.RAIL, 0, 0.95, 0.95));

        recalculate();

        assertEquals(20, trade(1), 1e-9);
        assertEquals(19, trade(12), 1e-9);
        assertEquals(10, production(1), 1e-9);
        assertEquals(9.5, production(12), 1e-9);
    }

    @Test
    void deliveriesTooSmallToTradeAreDropped() {
        link(new Link(1, 12, Mode.AIR, 0, 0.02, 0.005));

        recalculate();

        assertEquals(0, trade(12));
        assertEquals(0, production(12));
    }

    @Test
    void recalculatingAgainGivesTheSameResult() {
        link(new Link(1, 12, Mode.RAIL, 0, 0.7, 0.25));

        recalculate();
        double first = trade(12);
        recalculate();

        assertEquals(first, trade(12), 1e-9);
    }

    private void link(Link... links) {
        HubNetwork.setLinksForTests(Map.of("guild", List.of(links)));
    }

    private void recalculate() {
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
            factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(guild);
            factions.when(FactionManager::getAllGuilds).thenReturn(List.of(guild));
            provinces.recalculateForSingleGuild(guild, false);
        }
    }

    private double trade(int province) {
        return provinces.get(province).getStoredGuildTrade(guild);
    }

    private double production(int province) {
        return provinces.get(province).getGuildProduction(guild);
    }
}
