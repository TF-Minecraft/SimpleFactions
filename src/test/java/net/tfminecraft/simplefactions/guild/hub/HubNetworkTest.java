package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;

class HubNetworkTest {
    private ProvinceManager provinces;

    @BeforeEach
    void setUp() {
        HubTransport.resetConfig();
        provinces = new ProvinceManager();
        // 1 and 5 are coastal land joined by sea 2-3-4; 6 is inland beside 5; 7 sits on its own lake 8.
        Map<Integer, Province> map = new HashMap<>();
        map.put(1, province(1, Terrain.PLAINS));
        map.put(2, province(2, Terrain.SEA));
        map.put(3, province(3, Terrain.SEA));
        map.put(4, province(4, Terrain.SEA));
        map.put(5, province(5, Terrain.PLAINS));
        map.put(6, province(6, Terrain.PLAINS));
        map.put(7, province(7, Terrain.PLAINS));
        map.put(8, province(8, Terrain.SEA));
        join(map, 1, 2);
        join(map, 2, 3);
        join(map, 3, 4);
        join(map, 4, 5);
        join(map, 5, 6);
        join(map, 7, 8);
        provinces.start(map);
    }

    @AfterEach
    void restore() {
        HubNetwork.setRailRoutesForTests(null);
        HubNetwork.setLinksForTests(null);
        HubTransport.resetConfig();
    }

    @Test
    void stationsConnectOnlyWhenTrackJoinsThem_andDistanceIsTheTrackLength() {
        Installation a = installation("a", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation b = installation("b", InstallationKind.TRAIN_STATION, 5, 300, 400);

        HubNetwork.setRailRoutesForTests((from, to) -> OptionalDouble.empty());
        assertNull(HubNetwork.connect(a, b, provinces));

        HubNetwork.setRailRoutesForTests((from, to) -> OptionalDouble.of(2000));
        Link link = HubNetwork.connect(a, b, provinces);

        assertNotNull(link);
        assertEquals(Mode.RAIL, link.mode());
        assertEquals(2000, link.distance());
        assertEquals(0.70 * 0.81, link.tradeFactor(), 1e-9);
        assertEquals(0.25 * 0.81, link.productionFactor(), 1e-9);
    }

    @Test
    void aTrackLengthThatIsNotARealDistanceIsNoConnection() {
        Installation a = installation("a", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation b = installation("b", InstallationKind.TRAIN_STATION, 5, 300, 400);

        HubNetwork.setRailRoutesForTests((from, to) -> OptionalDouble.of(Double.NaN));
        assertNull(HubNetwork.connect(a, b, provinces));

        HubNetwork.setRailRoutesForTests((from, to) -> OptionalDouble.of(-50));
        assertNull(HubNetwork.connect(a, b, provinces));

        HubNetwork.setRailRoutesForTests((from, to) -> OptionalDouble.of(Double.POSITIVE_INFINITY));
        assertNull(HubNetwork.connect(a, b, provinces));
    }

    @Test
    void trackIsMeasuredOncePerPairUntilForgotten() {
        int[] calls = {0};
        HubNetwork.setRailRoutesForTests((from, to) -> {
            calls[0]++;
            return OptionalDouble.of(100);
        });
        Installation a = installation("a", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation b = installation("b", InstallationKind.TRAIN_STATION, 5, 300, 400);

        HubNetwork.connect(a, b, provinces);
        HubNetwork.connect(b, a, provinces);
        assertEquals(1, calls[0]);

        HubNetwork.forgetRoutes();
        HubNetwork.connect(a, b, provinces);
        assertEquals(2, calls[0]);
    }

    @Test
    void missingTrackIsAskedAboutAgain_soANewLineConnectsAtOnce() {
        boolean[] laid = {false};
        HubNetwork.setRailRoutesForTests((from, to) -> laid[0] ? OptionalDouble.of(300) : OptionalDouble.empty());
        Installation a = installation("a", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation b = installation("b", InstallationKind.TRAIN_STATION, 5, 300, 400);

        assertNull(HubNetwork.connect(a, b, provinces));
        laid[0] = true;

        assertNotNull(HubNetwork.connect(a, b, provinces));
    }

    @Test
    void portsConnectAcrossSharedSea_inAStraightLine() {
        Installation a = installation("a", InstallationKind.PORT, 1, 0, 0);
        Installation b = installation("b", InstallationKind.PORT, 5, 600, 800);
        Installation inland = installation("c", InstallationKind.PORT, 6, 700, 800);
        Installation lake = installation("d", InstallationKind.PORT, 7, 100, 100);

        Link link = HubNetwork.connect(a, b, provinces);

        assertNotNull(link);
        assertEquals(Mode.SEA, link.mode());
        assertEquals(1000, link.distance(), 1e-9);
        assertEquals(0.45 * 0.85, link.tradeFactor(), 1e-9);
        assertNull(HubNetwork.connect(a, inland, provinces));
        assertNull(HubNetwork.connect(a, lake, provinces));
    }

    @Test
    void airportsConnectWithinRangeOnly() {
        Installation a = installation("a", InstallationKind.AIRPORT, 1, 0, 0);
        Installation near = installation("b", InstallationKind.AIRPORT, 6, 1500, 2000);
        Installation far = installation("c", InstallationKind.AIRPORT, 7, 2000, 2000);

        Link link = HubNetwork.connect(a, near, provinces);

        assertNotNull(link);
        assertEquals(Mode.AIR, link.mode());
        assertEquals(2500, link.distance(), 1e-9);
        assertEquals(0, link.productionFactor());
        assertNull(HubNetwork.connect(a, far, provinces));
    }

    @Test
    void differentKindsAndSameProvinceDoNotConnect() {
        HubNetwork.setRailRoutesForTests((from, to) -> OptionalDouble.of(10));
        Installation station = installation("a", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation port = installation("b", InstallationKind.PORT, 5, 10, 10);
        Installation sameProvince = installation("c", InstallationKind.TRAIN_STATION, 1, 50, 50);
        Installation fort = installation("d", InstallationKind.FORT, 5, 10, 10);
        Installation otherFort = installation("e", InstallationKind.FORT, 6, 10, 10);

        assertNull(HubNetwork.connect(station, port, provinces));
        assertNull(HubNetwork.connect(station, sameProvince, provinces));
        assertNull(HubNetwork.connect(fort, otherFort, provinces));
        assertNull(HubNetwork.connect(station, station, provinces));
    }

    @Test
    void linksRunBothWaysBetweenEveryConnectedPair() {
        HubNetwork.setRailRoutesForTests((from, to) -> OptionalDouble.of(500));
        Installation a = installation("a", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation b = installation("b", InstallationKind.TRAIN_STATION, 5, 0, 0);
        Installation airport = installation("c", InstallationKind.AIRPORT, 6, 0, 0);

        List<Link> links = HubNetwork.linksBetween(List.of(a, b, airport), provinces);

        assertEquals(2, links.size());
        assertEquals(1, links.get(0).fromProvince());
        assertEquals(5, links.get(0).toProvince());
        assertEquals(5, links.get(1).fromProvince());
        assertEquals(1, links.get(1).toProvince());
    }

    private static Installation installation(String id, InstallationKind kind, int province, int x, int z) {
        return new Installation(id, id, kind, province, x, z, 1L);
    }

    private static Province province(int id, Terrain terrain) {
        return new Province(id, terrain.name(), 50);
    }

    private static void join(Map<Integer, Province> map, int a, int b) {
        map.get(a).addNeighbour(b);
        map.get(b).addNeighbour(a);
    }
}
