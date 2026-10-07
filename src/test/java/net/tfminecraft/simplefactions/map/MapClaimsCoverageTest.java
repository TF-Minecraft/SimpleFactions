package net.tfminecraft.simplefactions.map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.installation.*;
import net.tfminecraft.simplefactions.managers.*;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.rest.RestServer;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarSnapshot;
import net.tfminecraft.simplefactions.war.core.War;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class MapClaimsCoverageTest {
  private FactionDomainFixture fixture;
  private MapSystem map;
  private Faction home;
  private Player leader;
  private int cost;
  private MockedStatic<RestServer> rest;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    cost = Cache.provinceCost;
    Cache.provinceCost = 0;
    for (int id = 1; id <= 8; id++) fixture.provinceData.put(id, new Province(id, "PLAINS", 50));
    link(1, 2);
    link(2, 3);
    link(3, 4);
    home = realm("home", "Leader", 1, 1);
    leader = fixture.player("Leader");
    map = new MapSystem();
    rest = mockStatic(RestServer.class);
  }

  @AfterEach
  void close() {
    try {
      rest.close();
      Cache.provinceCost = cost;
    } finally {
      fixture.close();
    }
  }

  private void link(int a, int b) {
    fixture.provinceData.get(a).addNeighbour(b);
    fixture.provinceData.get(b).addNeighbour(a);
  }

  private Faction realm(String id, String name, int capital, int... owned) {
    var data = fixture.data(id, name);
    data.capital = capital;
    for (int p : owned) data.provinces.add(p);
    Faction f = fixture.saved(data);
    f.getOrCreateMainGuild();
    return f;
  }

  @SuppressWarnings("unchecked")
  private void civilWar(Faction host) throws Exception {
    Faction rebels = fixture.saved("rebels", "Rebel");
    War war = new War(71, rebels, host);
    CivilWarSnapshot snapshot = new CivilWarSnapshot();
    snapshot.setHostFactionId(host.getId());
    snapshot.setTempRebelFactionId(rebels.getId());
    war.setCivilWarSnapshot(snapshot);
    var field = WarManager.class.getDeclaredField("wars");
    field.setAccessible(true);
    ((List<War>) field.get(null)).add(war);
  }

  @Test
  void unknownProvinceCannotBecomeALandlessFactionsFirstClaim() {
    Faction landless = fixture.saved("landless", "Nomad");
    map.claim(fixture.player("Nomad"), landless, 999, false);
    assertTrue(landless.getProvinces().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"WATER", "SEA"})
  void waterTilesAreRejectedBeforeOwnershipChanges(String terrain) {
    fixture.provinceData.put(2, new Province(2, terrain, 0));
    map.claim(leader, home, 2, false);
    assertEquals(List.of(1), home.getProvinces());
    verify(leader).sendMessage(contains("no province"));
  }

  @Test
  void invalidOwnedAndDistantClaimsReturnSpecificDenials() {
    map.claim(leader, home, 0, false);
    verify(leader).sendMessage(contains("no province"));
    map.claim(leader, home, 1, false);
    verify(leader).sendMessage(contains("already owns"));
    map.claim(leader, home, 8, false);
    verify(leader).sendMessage(contains("does not border"));
    assertEquals(List.of(1), home.getProvinces());
  }

  @Test
  void validNeighborClaimChangesOwnershipExactlyOnce() {
    map.claim(leader, home, 2, false);
    map.claim(leader, home, 2, false);
    assertEquals(List.of(1, 2), home.getProvinces());
    assertSame(home, FactionManager.getByProvince(2));
    verify(leader).sendMessage(contains("Successfully"));
  }

  @Test
  void insufficientPrestigeDoesNotClaimOrRemoveAnOwnersProvince() {
    Cache.provinceCost = 1_000_000;
    assertTrue(home.getPrestige() < TitleManager.getClaimCost(home));
    map.claim(leader, home, 2, false);
    assertEquals(List.of(1), home.getProvinces());
    verify(leader).sendMessage(contains("prestige to claim"));
  }

  @Test
  void firstCapitalClaimMayPassTheUntitledLimitButOrdinaryClaimCannot() {
    Cache.maxUntitledProvinces = 0;
    map.claim(leader, home, 2, false);
    assertFalse(home.hasProvince(2));
    verify(leader).sendMessage(contains("too many untitled"));
    map.claimForCapital(leader, home, 2, false);
    assertTrue(home.hasProvince(2));
  }

  @Test
  void anotherRealmBelowItsCapKeepsItsProvince() {
    Faction owner = realm("other", "Other", 2, 2);
    map.claim(leader, home, 2, false);
    assertSame(owner, FactionManager.getByProvince(2));
    assertFalse(home.hasProvince(2));
    verify(leader).sendMessage(contains("already claimed"));
  }

  @Test
  void civilWarLocksClaimAndUnclaimForItsParticipants() throws Exception {
    home.addProvince(2);
    civilWar(home);
    map.claim(leader, home, 3, false);
    map.unclaim(leader, home, 2);
    assertEquals(List.of(1, 2), home.getProvinces());
    verify(leader)
        .sendMessage(net.tfminecraft.simplefactions.war.civilwar.CivilWarCopy.CANNOT_CLAIM);
    verify(leader)
        .sendMessage(net.tfminecraft.simplefactions.war.civilwar.CivilWarCopy.CANNOT_UNCLAIM);
  }

  @Test
  void lockedOverCapOwnerCannotLoseProvinceToAnotherClaim() throws Exception {
    Faction owner = realm("other", "Other", 3, 2, 3);
    civilWar(owner);
    Cache.provinceCost = 1000;
    home.getBank().deposit(1_000_000_000.0);
    home.updatePrestige();
    map.claim(leader, home, 2, false);
    assertSame(owner, FactionManager.getByProvince(2));
    verify(leader)
        .sendMessage(net.tfminecraft.simplefactions.war.civilwar.CivilWarCopy.CANNOT_STEAL);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stealingAnOverCapHoldingTransfersItsInstallationWithTheLand(boolean online) {
    Faction owner = realm("other", "Other", 3, 2, 3);
    Cache.provinceCost = 1000;
    home.getBank().deposit(1_000_000_000.0);
    home.updatePrestige();
    assertTrue(TitleManager.overProvinceCap(owner));
    assertTrue(home.getPrestige() >= TitleManager.getClaimCost(home));
    Player other = fixture.player("Other");
    if (!online) fixture.online.remove("Other");
    Installation fort = new Installation("fort", "Riverside", InstallationKind.FORT, 2, 0, 0, 0);
    owner.getInstallationHandler().acceptTransferred(fort);
    map.claim(leader, home, 2, false);
    assertTrue(home.hasProvince(2));
    assertFalse(owner.hasProvince(2));
    assertTrue(owner.hasProvince(3));
    assertSame(fort, home.getInstallationHandler().getById("fort"));
    assertNull(owner.getInstallationHandler().getById("fort"));
    if (online) verify(other).sendMessage(contains("claimed one of your provinces"));
    else verify(other, never()).sendMessage(anyString());
    verify(fixture.map, atLeastOnce()).enqueue("nation", home.getRGB());
  }

  @Test
  void unclaimRejectsMissingAndCapitalButAllowsOrdinaryHoldingAndInternalCleanup() {
    map.unclaim(leader, home, 0);
    map.unclaim(leader, home, 7);
    map.unclaim(leader, home, 1);
    assertEquals(List.of(1), home.getProvinces());
    home.addProvince(2);
    map.unclaim(leader, home, 2);
    assertEquals(List.of(1), home.getProvinces());
    home.addProvince(2);
    map.unclaim(null, home, 2);
    map.unclaim(null, home, 0);
    map.unclaim(null, home, 8);
    map.unclaim(null, home, 1);
    assertEquals(List.of(1), home.getProvinces());
    verify(leader).sendMessage(contains("Cannot unclaim the capital"));
  }

  @Test
  void relocationRequiresANonBaseGuildAndADifferentValidLandCapital() {
    Player outsider = fixture.player("Outsider");
    assertNull(map.getRelocationTarget(outsider));
    assertNull(map.getRelocationTarget(leader));
    Guild guild = fixture.guild(home, "merchant", "Merchant");
    Player merchant = fixture.player("Merchant");
    assertNull(map.getRelocationTarget(merchant));
    guild.setCapital(2, false);
    rest.when(() -> RestServer.getProvince(merchant)).thenReturn(2);
    assertNull(map.getRelocationTarget(merchant));
    rest.when(() -> RestServer.getProvince(merchant)).thenReturn(999);
    assertNull(map.getRelocationTarget(merchant));
    fixture.provinceData.put(6, new Province(6, "SEA", 0));
    rest.when(() -> RestServer.getProvince(merchant)).thenReturn(6);
    assertNull(map.getRelocationTarget(merchant));
    rest.when(() -> RestServer.getProvince(merchant)).thenReturn(1);
    assertSame(home, map.getRelocationTarget(merchant));
    rest.when(() -> RestServer.getProvince(merchant)).thenReturn(2);
    guild.setCapital(3, false);
    assertSame(home, map.getRelocationTarget(merchant));
    rest.when(() -> RestServer.getProvince(merchant)).thenReturn(8);
    assertNull(map.getRelocationTarget(merchant));
  }
}
