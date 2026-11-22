package net.tfminecraft.simplefactions.tiers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** County land on a real province map: land borders and rivers join it, the sea does not. */
class CountyLandTest {
  private FactionDomainFixture fixture;
  private Map<Integer, Province> provinces;
  private Faction realm;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    ProvinceManager manager = new ProvinceManager();
    provinces = new LinkedHashMap<>();
    manager.start(provinces);
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(manager);
    // 1 - 2 border by land, 2 and 3 face each other across a river, 3 and 4 only across the sea.
    Province one = province(1, Terrain.PLAINS);
    Province two = province(2, Terrain.FOREST);
    Province river = province(50, Terrain.WATER);
    Province three = province(3, Terrain.HILLS);
    Province sea = province(60, Terrain.SEA);
    Province four = province(4, Terrain.PLAINS);
    link(one, two);
    link(two, river);
    link(river, three);
    link(three, sea);
    link(sea, four);
    realm = fixture.saved("realm", "Leader");
    for (int id : List.of(1, 2, 3, 4)) realm.addProvince(id);
  }

  @AfterEach
  void close() {
    fixture.close();
  }

  @Test
  void aCountyTakesLandJoinedByBordersAndRiversButNotBySea() {
    assertEquals(List.of(1, 2, 3), CountyLand.countyAt(realm, 3));
    assertEquals(List.of(4), CountyLand.countyAt(realm, 4));
    assertEquals(List.of(), CountyLand.countyAt(realm, 99), "Standing outside untitled land takes nothing");
    assertEquals(3, CountyLand.largestCounty(realm));
  }

  @Test
  void aBorderStoredOnOneSideOnlyStillJoinsTheLandFromEitherEnd() {
    Map<Integer, List<Integer>> oneWay = Map.of(2, List.of(1));
    java.util.function.IntFunction<java.util.Collection<Integer>> links =
        id -> oneWay.getOrDefault(id, List.of());
    assertEquals(List.of(1, 2), CountyLand.groupOf(1, List.of(1, 2), links));
    assertEquals(List.of(1, 2), CountyLand.groupOf(2, List.of(1, 2), links));
    assertEquals(2, CountyLand.largestGroup(List.of(1, 2), links));
  }

  @Test
  void titledLandSplitsTheUntitledLandAroundIt() {
    fixture.title("middle", "county", 2);
    assertEquals(List.of(1), CountyLand.countyAt(realm, 1));
    assertEquals(List.of(3), CountyLand.countyAt(realm, 3));
    assertEquals(1, CountyLand.largestCounty(realm));
  }

  @Test
  void claimingStopsOnlyOnceOneConnectedAreaReachesTheCap() {
    Cache.maxUntitledProvinces = 3;
    assertTrue(CountyLand.mustFormCounty(realm));
    Cache.maxUntitledProvinces = 4;
    assertFalse(CountyLand.mustFormCounty(realm), "Four untitled provinces in two areas never block claiming");
  }

  @Test
  void aVassalsLandCountsTowardItsOwnCapButCanJoinTheOverlordsCounty() {
    Cache.maxUntitledProvinces = 3;
    realm.removeProvince(3, false);
    Faction vassal = fixture.saved("vassal", "Vassal");
    vassal.addProvince(3);
    fixture.subject(realm, vassal);
    assertFalse(CountyLand.mustFormCounty(realm));
    assertEquals(List.of(1, 2, 3), CountyLand.countyAt(realm, 1));
    assertEquals(List.of(3), CountyLand.countyAt(vassal, 3));
  }

  @Test
  void theCapIsNeverBelowWhatACountyCostsToForm() {
    Cache.maxUntitledProvinces = 2;
    county(5);
    assertEquals(5, CountyLand.claimCap());
    TierLoader.oList.removeIf(t -> t.getId().equals("county"));
    assertEquals(2, CountyLand.claimCap());
    Cache.maxUntitledProvinces = 0;
    assertEquals(1, CountyLand.claimCap());
  }

  @Test
  void withoutAProvinceMapEveryProvinceStandsAlone() {
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(null);
    assertEquals(List.of(1), CountyLand.countyAt(realm, 1));
    assertEquals(1, CountyLand.largestCounty(realm));
  }

  private void county(int formCost) {
    TierLoader.oList.removeIf(t -> t.getId().equals("county"));
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("tier", 2);
    yaml.set("form-cost", formCost);
    TierLoader.oList.add(new Tier("county", yaml));
  }

  private Province province(int id, Terrain terrain) {
    Province province = new Province(id, terrain.name(), 50);
    provinces.put(id, province);
    return province;
  }

  private static void link(Province first, Province second) {
    first.addNeighbour(second.getId());
    second.addNeighbour(first.getId());
  }
}
