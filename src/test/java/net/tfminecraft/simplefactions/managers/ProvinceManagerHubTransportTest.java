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
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.diplomacy.DiplomacyHandler;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import org.bukkit.configuration.file.YamlConfiguration;
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
        when(host.hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(true);
        guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        when(guild.getFaction()).thenReturn(host);
        when(guild.hasCapital()).thenReturn(true);
        when(guild.getCapital()).thenReturn(1);
        when(guild.getModifier(GuildModifier.TRADE_POWER)).thenReturn(20.0);
        when(guild.getModifier(GuildModifier.TRADE_CARRY)).thenReturn(1.0);
        when(guild.getModifier(GuildModifier.PRODUCTION)).thenReturn(10.0);
        when(guild.getModifier(GuildModifier.HUB_TRADE)).thenReturn(0.0);

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
    void forbiddenSupplyHubsCarryNothingAndLawPreviewSuppressesCachedLinks() {
        link(new Link(1, 22, Mode.RAIL, 0, 0.4, 0.8));
        List<Link> cached = HubNetwork.linksFor(guild);
        recalculate();
        assertEquals(8, trade(22), 1e-9);
        YamlConfiguration config = new YamlConfiguration();
        config.set("effects.faction.rules", List.of("supply_hubs false"));
        Law proposed = new Law("economy", "decentralized", config);
        IncomePreviewContext.open(IncomePreviewContext.law(guild.getFaction(), mock(LawGroup.class), proposed));
        try {
            ProvinceManager snapshot = provinces.createSnapshotShell();
            snapshot.copyAllDataFrom(provinces);
            snapshot.recalculateForSingleGuild(guild, false);
            assertTrue(HubNetwork.linksFor(guild).isEmpty());
            assertEquals(0, snapshot.get(22).getStoredGuildTrade(guild));
            assertEquals(0, snapshot.get(22).getGuildProduction(guild));
            assertEquals(8, trade(22), 1e-9);
        } finally {
            IncomePreviewContext.clear();
        }
        assertEquals(cached, HubNetwork.linksFor(guild));
        when(guild.getFaction().hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(false);
        recalculate();
        assertEquals(0, trade(22));
        assertEquals(0, production(22));
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
        link(new Link(1, 22, Mode.RAIL, 0, 0.4, 0.8));

        recalculate();

        assertEquals(8, trade(22), 1e-9);
        assertEquals(8 * 0.85, trade(21), 1e-9);
        assertEquals(8 * 0.85, trade(23), 1e-9);
        assertEquals(0, distance(22));
        assertEquals(1, distance(21));
        assertEquals(8, production(22), 1e-9);
        assertTrue(production(21) > 0 && production(21) < 8);
        // The road in between gets nothing from the journey.
        assertEquals(0, trade(12));
    }

    @Test
    void hubTradeModifierBoostsTheShareDuringRecalculation() {
        when(guild.getModifier(GuildModifier.HUB_TRADE)).thenReturn(0.30);
        link(new Link(1, 22, Mode.RAIL, 0, 0.4, 0.8));

        recalculate();

        assertEquals(10.4, trade(22), 1e-9);
    }

    @Test
    void tradeOnlyDeliveryKeepsTheWalkedDistanceForProductionThatWalked() {
        // Province 4 is three steps from the capital. An air link brings more trade there but no
        // production, so the production that walked in is still weighed as three steps away.
        link(new Link(1, 4, Mode.AIR, 0, 0.9, 0));

        recalculate();

        assertEquals(18, trade(4), 1e-9);
        assertTrue(production(4) > 0);
        assertEquals(3, distance(4));
        assertEquals(2, distance(3));
        assertEquals(4, distance(5));
    }

    @Test
    void productionDeliveredByAHubIsMeasuredFromThatHub() {
        recalculate();
        double walkedProduction = production(4);
        link(new Link(1, 4, Mode.RAIL, 0, 0.9, 0.9));

        recalculate();

        assertTrue(production(4) > walkedProduction);
        assertEquals(9, production(4), 1e-9);
        assertEquals(0, distance(4));
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
        link(new Link(1, 8, Mode.RAIL, 0, 0.4, 0.8), new Link(8, 14, Mode.RAIL, 0, 0.4, 0.8));

        recalculate();

        assertEquals(8, trade(8), 1e-9);
        assertEquals(3.2, trade(14), 1e-9);
        assertEquals(8, production(8), 1e-9);
        assertEquals(6.4, production(14), 1e-9);
    }

    @Test
    void chainSettlesWhateverOrderTheLinksAreIn() {
        link(new Link(8, 14, Mode.RAIL, 0, 0.4, 0.8), new Link(1, 8, Mode.RAIL, 0, 0.4, 0.8));

        recalculate();

        assertEquals(3.2, trade(14), 1e-9);
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
        link(new Link(1, 12, Mode.RAIL, 0, 0.4, 0.8));

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

    private int distance(int province) {
        return provinces.get(province).getData("guild").getDistance();
    }

    private double production(int province) {
        return provinces.get(province).getGuildProduction(guild);
    }
}
