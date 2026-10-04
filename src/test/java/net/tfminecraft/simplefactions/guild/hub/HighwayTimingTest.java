package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;

/**
 * One full recalculation against the capital walk, on a map large enough to show a slow highway.
 * Guilds are plain objects: a mock on this path would time the mock, not the flood.
 */
class HighwayTimingTest {
    private static final int PROVINCES = 400;
    private static final int WIDTH = 20;
    private static final int NODES = 40;
    private static final int GUILDS = 30;

    private final List<Faction> savedFactions = new ArrayList<>();
    private boolean savedProvinces;
    private Double savedPlains;
    private double savedStrength;

    @AfterEach
    void tearDown() {
        HubNetwork.setHighwayForTests(null, null);
        Cache.provincesEnabled = savedProvinces;
        Cache.supplyHubNoHubStrength = savedStrength;
        if (savedPlains == null) {
            Cache.tradeCarry.remove(Terrain.PLAINS);
        } else {
            Cache.tradeCarry.put(Terrain.PLAINS, savedPlains);
        }
        FactionManager.factions.clear();
        FactionManager.factions.addAll(savedFactions);
    }

    @Test
    void fullRecalculationStaysNearTheCapitalWalk() {
        savedFactions.addAll(FactionManager.factions);
        FactionManager.factions.clear();
        savedProvinces = Cache.provincesEnabled;
        Cache.provincesEnabled = true;
        savedPlains = Cache.tradeCarry.get(Terrain.PLAINS);
        savedStrength = Cache.supplyHubNoHubStrength;
        Cache.supplyHubNoHubStrength = 0.5;
        Cache.tradeCarry.put(Terrain.PLAINS, 0.85);

        ProvinceManager provinces = grid();
        List<Installation> nodes = new ArrayList<>();
        for (int index = 1; index <= NODES; index++) {
            int id = index * 10;
            int col = (id - 1) % WIDTH;
            int row = (id - 1) / WIDTH;
            nodes.add(new Installation(
                    "n" + index, "n" + index, InstallationKind.AIRPORT, id, col * 200, row * 200, 1L));
        }
        TradeGraph graph = TestGraphs.rail("realm", nodes, (from, to) -> false, 0);
        assertEquals(1, graph.networks().size());
        assertEquals(NODES, graph.networks().get(0).size());

        ObjenesisStd objects = new ObjenesisStd();
        QuietFaction faction = objects.newInstance(QuietFaction.class);
        List<Guild> guilds = new ArrayList<>();
        Map<String, java.util.Set<Highway.HubSite>> hubbed = new HashMap<>();
        for (int index = 0; index < GUILDS; index++) {
            PlainGuild guild = objects.newInstance(PlainGuild.class);
            guild.gid = "g" + index;
            guild.capitalId = index + 1;
            guild.faction = faction;
            guilds.add(guild);
            hubbed.put(guild.gid, TestGraphs.hubs(
                    "realm", nodes.get(index % NODES), nodes.get((index + 7) % NODES)));
        }
        HubNetwork.setHighwayForTests(graph, hubbed);

        // One unmeasured pass so the timed runs are not the first time the flood is compiled.
        provinces.recalculateQuiet(guilds, List.of());
        walk(provinces, guilds);

        long[] capital = new long[3];
        long[] full = new long[3];
        for (int sample = 0; sample < 3; sample++) {
            capital[sample] = time(() -> walk(provinces, guilds));
            full[sample] = time(() -> provinces.recalculateQuiet(guilds, List.of()));
        }
        long capitalMedian = median(capital);
        long fullMedian = median(full);
        System.out.println("highway timing: capital "
                + ms(capital[0]) + " ms, " + ms(capital[1]) + " ms, " + ms(capital[2])
                + " ms (median " + ms(capitalMedian) + "); full "
                + ms(full[0]) + " ms, " + ms(full[1]) + " ms, " + ms(full[2])
                + " ms (median " + ms(fullMedian) + ")");
        long limit = Math.max(capitalMedian * 25, capitalMedian + 3_000_000_000L);
        assertTrue(fullMedian <= limit,
                "full recalculation " + ms(fullMedian) + " ms, capital walk " + ms(capitalMedian)
                        + " ms, limit " + ms(limit) + " ms");
    }

    private static void walk(ProvinceManager provinces, List<Guild> guilds) {
        for (Guild guild : guilds) {
            provinces.clearGuildData(guild.getId());
            provinces.get(guild.getCapital()).calculateTrade(provinces, guild, -1, 0);
        }
    }

    private static long time(Runnable work) {
        long start = System.nanoTime();
        work.run();
        return System.nanoTime() - start;
    }

    private static long median(long[] samples) {
        long[] copy = Arrays.copyOf(samples, samples.length);
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    private static String ms(long nanos) {
        return String.format("%.1f", nanos / 1_000_000.0);
    }

    private static ProvinceManager grid() {
        Map<Integer, Province> map = new HashMap<>();
        for (int id = 1; id <= PROVINCES; id++) {
            int col = (id - 1) % WIDTH;
            int row = (id - 1) / WIDTH;
            map.put(id, new Province(id, Terrain.PLAINS.name(), 50, col * 200, row * 200));
        }
        for (int id = 1; id <= PROVINCES; id++) {
            int col = (id - 1) % WIDTH;
            int row = (id - 1) / WIDTH;
            if (col + 1 < WIDTH) {
                join(map, id, id + 1);
            }
            if (row + 1 < PROVINCES / WIDTH) {
                join(map, id, id + WIDTH);
            }
        }
        ProvinceManager provinces = new ProvinceManager();
        provinces.start(map);
        return provinces;
    }

    private static void join(Map<Integer, Province> map, int left, int right) {
        map.get(left).addNeighbour(right);
        map.get(right).addNeighbour(left);
    }

    /** Skips Guild's constructor. The flood only needs the methods below. */
    public static final class PlainGuild extends Guild {
        String gid;
        int capitalId;
        Faction faction;

        private PlainGuild() {
            super((Faction) null);
        }

        @Override
        public String getId() {
            return gid;
        }

        @Override
        public boolean isBase() {
            return false;
        }

        @Override
        public boolean hasCapital() {
            return true;
        }

        @Override
        public int getCapital() {
            return capitalId;
        }

        @Override
        public Faction getFaction() {
            return faction;
        }

        @Override
        public double getModifier(GuildModifier modifier) {
            if (modifier == GuildModifier.TRADE_POWER) {
                return 20;
            }
            if (modifier == GuildModifier.TRADE_CARRY) {
                return 1;
            }
            if (modifier == GuildModifier.PRODUCTION) {
                return 10;
            }
            return 0;
        }
    }

    /** Skips Faction's constructor. Prosperity asks for modifiers and the hub law. */
    public static final class QuietFaction extends Faction {
        private QuietFaction() {
            super("realm", "Realm");
        }

        @Override
        public String getId() {
            return "realm";
        }

        @Override
        public boolean hasFactionRule(Rules rule) {
            return true;
        }

        @Override
        public Faction getOverlord() {
            return null;
        }

        @Override
        public List<FactionModifier> getModifiers(String id, Scope scope, Region region) {
            return List.of();
        }
    }
}
