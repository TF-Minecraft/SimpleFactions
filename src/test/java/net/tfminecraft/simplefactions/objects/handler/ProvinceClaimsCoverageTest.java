package net.tfminecraft.simplefactions.objects.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProvinceClaimsCoverageTest {
  private FactionDomainFixture fixture;
  private ProvinceManager manager;
  private Map<Integer, Province> provinces;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    manager = new ProvinceManager();
    provinces = new LinkedHashMap<>();
    manager.start(provinces);
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(manager);
  }

  @AfterEach
  void close() {
    if (fixture != null) fixture.close();
  }

  private Province province(int id, Terrain terrain) {
    Province province = new Province(id, terrain.name(), 50);
    provinces.put(id, province);
    return province;
  }

  private void link(Province first, Province second) {
    first.addNeighbour(second.getId());
    second.addNeighbour(first.getId());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void firstClaimStillRejectsLocationsWithoutARealProvince(boolean relocating) {
    Faction faction = fixture.saved("home", "Alice");
    ProvinceHandler claims = faction.getProvinceHandler();
    Guild guild = fixture.guild(faction, "merchants", "Bob");
    assertFalse(relocating ? claims.canClaim(999, false, guild) : claims.canClaim(999, false));
    assertEquals("§cThis location has no province.", claims.getClaimDeniedReason(999, false));
    assertTrue(faction.getProvinces().isEmpty());
  }

  @Test
  void claimExplanationAgreesThatTheFirstValidProvinceNeedsNoExistingBorder() {
    Faction faction = fixture.saved("home", "Alice");
    province(1, Terrain.PLAINS);
    assertTrue(faction.getProvinceHandler().canClaim(1, false));
    assertEquals("Success", faction.getProvinceHandler().getClaimDeniedReason(1, false));
    assertTrue(faction.getProvinces().isEmpty());
  }

  @ParameterizedTest
  @EnumSource(
      value = Terrain.class,
      names = {"WATER", "SEA"})
  void excludingAnUnrelatedGuildCapitalPreservesValidBridgeClaims(Terrain bridge) {
    Faction faction = fixture.saved("home", "Alice");
    Province capital = province(1, Terrain.PLAINS);
    Province water = province(2, bridge);
    Province destination = province(3, Terrain.PLAINS);
    province(10, Terrain.PLAINS);
    link(capital, water);
    link(water, destination);
    faction.addProvince(1);
    faction.addProvince(10);
    faction.setCapital(1, true);
    Guild guild = fixture.guild(faction, "merchants", "Bob");
    guild.setCapital(10);
    assertTrue(faction.getProvinceHandler().canClaim(3, false));
    assertTrue(faction.getProvinceHandler().canClaim(3, false, guild));
    assertEquals(10, guild.getCapital());
    assertFalse(faction.hasProvince(3));
  }

  @Test
  void persistedClaimsDiscardAlreadyOwnedLandAndKeepTheirOwnIndependentList() {
    Faction owner = fixture.saved("owner", "Alice");
    owner.addProvince(1);
    Faction newcomer = fixture.saved("newcomer", "Bob");
    List<Integer> loaded = new ArrayList<>(List.of(1, 2, 3));
    ProvinceHandler claims = new ProvinceHandler(newcomer, 2, loaded);
    loaded.clear();
    assertEquals(List.of(2, 3), claims.getProvinces());
    assertEquals(2, claims.getCapital());
    assertTrue(claims.hasCapital());
    assertFalse(claims.hasProvince(1));
    assertSame(owner, TitleManager.getByProvince(1));
  }

  @Test
  void capitalMovesRespectOwnershipAndReleaseAnUnpopulatedFormerSettlement() {
    Faction home = fixture.saved("home", "Alice");
    ProvinceHandler claims = home.getProvinceHandler();
    province(1, Terrain.PLAINS);
    province(2, Terrain.PLAINS);
    home.addProvince(1);
    home.addProvince(2);
    assertTrue(home.getSettlementHandler().found("Old", 1, 0, 0).isSuccess());
    claims.setCapital(1, false);
    claims.setCapital(999, false);
    assertEquals(1, claims.getCapital());
    claims.setCapital(1, false);
    assertNotNull(home.getSettlementHandler().getByProvince(1));
    claims.setCapital(2, false);
    assertEquals(2, claims.getCapital());
    assertNull(home.getSettlementHandler().getByProvince(1));
    assertTrue(home.getSettlementHandler().found("New", 2, 0, 0).isSuccess());
    claims.setCapital(-1, false, false);
    assertFalse(claims.hasCapital());
    assertNotNull(home.getSettlementHandler().getByProvince(2));
  }

  @Test
  void losingLandDropsItsTitleAndSettlementButRetainsOtherClaims() {
    Faction home = fixture.saved("home", "Alice");
    province(1, Terrain.PLAINS);
    province(2, Terrain.PLAINS);
    home.addProvince(1);
    home.addProvince(2);
    home.addProvince(2);
    var title = fixture.title("county", "county", 1);
    home.addTitle(title);
    home.getSettlementHandler().found("Town", 1, 0, 0);
    assertTrue(home.hasTitle(title));
    home.getProvinceHandler().removeProvince(1, true);
    assertEquals(List.of(2), home.getProvinces());
    assertFalse(home.hasTitle(title));
    assertNull(home.getSettlementHandler().getByProvince(1));
    home.getProvinceHandler().removeProvince(999, true);
    assertEquals(List.of(2), home.getProvinces());
  }

  @Test
  void untitledLandIncludesDirectSubjectsWithoutCountingTheirDeJureTitles() {
    Faction home = fixture.saved("home", "Alice");
    Faction subject = fixture.saved("subject", "Bob");
    fixture.subject(home, subject);
    home.addProvince(1);
    home.addProvince(2);
    subject.addProvince(3);
    subject.addProvince(4);
    fixture.title("home_title", "county", 1);
    fixture.title("subject_title", "county", 3);
    assertEquals(List.of(2, 4), home.getProvinceHandler().getUntitledProvinces());
  }

  @Test
  void provinceCapRemovesANoncapitalAndNeverTheSoleCapital() {
    int previousCost = Cache.provinceCost;
    try {
      Cache.provinceCost = 1_000_000;
      Faction home = fixture.saved("home", "Alice");
      home.addProvince(1);
      home.addProvince(2);
      home.setCapital(2, true);
      assertTrue(TitleManager.overProvinceCap(home));
      home.getProvinceHandler().provinceCap();
      assertEquals(List.of(2), home.getProvinces());
      home.getProvinceHandler().provinceCap();
      assertEquals(List.of(2), home.getProvinces());
      home.addProvince(3);
      home.getProvinceHandler().provinceCap();
      assertEquals(List.of(2), home.getProvinces());
    } finally {
      Cache.provinceCost = previousCost;
    }
  }

  @Test
  void relocationCannotUseTheLeavingGuildCapitalOrTheLandBehindIt() {
    Faction home = fixture.saved("home", "Alice");
    Province capital = province(1, Terrain.PLAINS);
    Province leaving = province(2, Terrain.PLAINS);
    Province frontier = province(3, Terrain.PLAINS);
    Province destination = province(4, Terrain.PLAINS);
    link(capital, leaving);
    link(leaving, frontier);
    link(frontier, destination);
    for (int id : List.of(1, 2, 3)) home.addProvince(id);
    home.setCapital(1, true);
    Guild departing = fixture.guild(home, "departing", "Bob");
    departing.setCapital(2);
    assertTrue(home.getProvinceHandler().canClaim(4, false));
    assertFalse(home.getProvinceHandler().canClaim(4, false, departing));
    Guild independent = fixture.guild(home, "independent", "Cara");
    independent.setCapital(3);
    assertTrue(home.getProvinceHandler().canClaim(4, false, departing));
    assertEquals(List.of(1, 2, 3), home.getProvinces());
    assertEquals(2, departing.getCapital());
  }

  @Test
  void relocationCanCrossAConnectedSeaButCannotUseLandAsASeaBridge() {
    Faction home = fixture.saved("home", "Alice");
    Province capital = province(1, Terrain.PLAINS);
    Province sea = province(2, Terrain.SEA);
    Province water = province(3, Terrain.WATER);
    Province coast = province(4, Terrain.PLAINS);
    Province inland = province(5, Terrain.PLAINS);
    Province unrelated = province(10, Terrain.PLAINS);
    link(capital, sea);
    link(sea, water);
    link(water, coast);
    link(coast, inland);
    home.addProvince(1);
    home.addProvince(10);
    home.setCapital(1, true);
    Guild departing = fixture.guild(home, "departing", "Bob");
    departing.setCapital(unrelated.getId());
    assertFalse(home.getProvinceHandler().canClaim(4, false, departing));
    assertTrue(home.getProvinceHandler().canClaim(4, true, departing));
    assertFalse(home.getProvinceHandler().canClaim(5, true, departing));
    assertTrue(home.getProvinceHandler().canClaim(4, true));
    assertFalse(home.getProvinceHandler().canClaim(5, true));
    assertEquals("Success", home.getProvinceHandler().getClaimDeniedReason(4, true));
    assertEquals(
        "§cThis province does not border your current realm.",
        home.getProvinceHandler().getClaimDeniedReason(4, false));
    assertEquals(
        "§cThis province must be connected to your realm by land or by sea.",
        home.getProvinceHandler().getClaimDeniedReason(5, true));
    assertEquals(
        "§cThis province is already part of your realm.",
        home.getProvinceHandler().getClaimDeniedReason(1, true));
    assertFalse(home.getProvinceHandler().canClaim(1, true, departing));
  }

  @Test
  void relocationOfTheMainGuildUsesTheOrdinaryFactionBorderAndFirstClaimRules() {
    Faction home = fixture.saved("home", "Alice");
    Province capital = province(1, Terrain.PLAINS);
    Province next = province(2, Terrain.PLAINS);
    link(capital, next);
    Guild main = home.getOrCreateMainGuild();
    assertTrue(home.getProvinceHandler().canClaim(1, false, main));
    home.addProvince(1);
    home.setCapital(1, true);
    assertTrue(home.getProvinceHandler().canClaim(2, false, main));
    assertEquals("Success", home.getProvinceHandler().getClaimDeniedReason(2, false));
    assertFalse(home.getProvinceHandler().canClaim(1, false));
  }

  @Test
  void revalidationDropsDisconnectedInlandClaimsAndInvalidGuildCapitals() {
    Faction home = fixture.saved("home", "Alice");
    Province capital = province(1, Terrain.PLAINS);
    Province connected = province(2, Terrain.PLAINS);
    province(10, Terrain.PLAINS);
    province(11, Terrain.WATER);
    link(capital, connected);
    for (int id : List.of(1, 2, 10, 11, 999)) home.addProvince(id);
    home.setCapital(1, true);
    Guild connectedGuild = fixture.guild(home, "connected", "Bob");
    connectedGuild.setCapital(2);
    Guild disconnected = fixture.guild(home, "disconnected", "Cara");
    disconnected.setCapital(10);
    Guild invalid = fixture.guild(home, "invalid", "Dave");
    invalid.setCapital(11);
    Guild absent = fixture.guild(home, "absent", "Eve");
    absent.setCapital(800);
    fixture.guild(home, "landless", "Frank");
    home.getProvinceHandler().revalidateClaims();
    assertEquals(List.of(1, 2), home.getProvinces());
    assertEquals(2, connectedGuild.getCapital());
    assertFalse(disconnected.hasCapital());
    assertFalse(invalid.hasCapital());
    assertFalse(absent.hasCapital());
  }

  @Test
  void capitalPreviewReturnsSortedLossesWithoutMutatingAnyCapitalOrClaim() {
    Faction home = fixture.saved("home", "Alice");
    Province one = province(1, Terrain.PLAINS);
    Province two = province(2, Terrain.PLAINS);
    Province five = province(5, Terrain.PLAINS);
    Province four = province(4, Terrain.PLAINS);
    link(one, two);
    link(five, four);
    for (int id : List.of(2, 1, 5, 4)) home.addProvince(id);
    home.setCapital(1, true);
    assertEquals(List.of(1, 2), home.getProvinceHandler().previewProvincesLostIfCapitalMoved(5));
    assertEquals(List.of(2, 1, 5, 4), home.getProvinces());
    assertEquals(1, home.getCapital());
    assertEquals(List.of(), home.getProvinceHandler().previewProvincesLostIfCapitalMoved(1));
    home.getProvinceHandler().setCapital(-1, false, false);
    assertEquals(List.of(), home.getProvinceHandler().previewProvincesLostIfCapitalMoved(5));
  }

  @Test
  void aNewHandlerStartsUnclaimedAndAnIndependentLandRouteRemainsAvailableDuringRelocation() {
    Faction home = fixture.saved("home", "Alice");
    ProvinceHandler empty = new ProvinceHandler(home);
    assertFalse(empty.hasCapital());
    assertEquals(-1, empty.getCapital());
    assertTrue(empty.getProvinces().isEmpty());
    assertFalse(empty.hasProvince(1));
    Province first = province(1, Terrain.PLAINS);
    Province middle = province(2, Terrain.PLAINS);
    Province edge = province(3, Terrain.PLAINS);
    Province destination = province(4, Terrain.PLAINS);
    province(10, Terrain.PLAINS);
    link(first, middle);
    link(middle, edge);
    link(edge, destination);
    for (int id : List.of(1, 2, 3, 10)) home.addProvince(id);
    home.setCapital(1, true);
    Guild leaving = fixture.guild(home, "leaving", "Bob");
    leaving.setCapital(10);
    assertTrue(home.getProvinceHandler().canClaim(4, false, leaving));
    assertEquals(10, leaving.getCapital());
    assertFalse(home.hasProvince(4));
  }
}
