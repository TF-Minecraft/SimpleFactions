package net.tfminecraft.simplefactions.war.civilwar.wartime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.MovementData;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.GuildLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.SeaConnectivity;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarCopy;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarSnapshot;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarLandSplitService.LandSplitPlan;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarMemberMove;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CivilWarWartimeBoundaryCoverageTest {
  private PersistenceFilesFixture files;
  private FactionDomainFixture fixture;
  private final ProvinceManager manager = new ProvinceManager();
  private final Map<Integer, Province> provinces = new LinkedHashMap<>();

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    Files.createDirectories(files.root.resolve("Data"));
    fixture = new FactionDomainFixture();
    manager.start(provinces);
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(manager);
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (fixture != null) fixture.close();
    } finally {
      if (files != null) files.close();
    }
  }

  @Test
  void borderQueriesIgnoreOrdinaryAndEndedWarsAndRespectLegacyMainParticipants() {
    var host = fixture.saved("host", "Alice");
    var rebels = fixture.saved("rebels", "Bob");
    var second = fixture.saved("second", "Cara");
    var backer = fixture.saved("backer", "Dan");
    var ordinary = war(1, rebels, host);
    assertFalse(CivilWarBorderLock.isCivilWar(ordinary));
    assertFalse(CivilWarBorderLock.isCivilWar(null));
    assertFalse(CivilWarBorderLock.isLocked((Faction) null));
    assertNull(CivilWarBorderLock.findActiveCivilWarInvolving((Faction) null));
    assertNull(CivilWarBorderLock.findActiveCivilWarInvolving((String) null));
    assertNull(CivilWarBorderLock.findActiveCivilWarInvolving(" "));
    assertTrue(CivilWarBorderLock.involvedIds(null).isEmpty());
    assertFalse(CivilWarBorderLock.isLocked(host));
    var civil = war(2, rebels, host);
    civil.setMovementId("legacy-movement");
    civil.getAttackers().getMainParticipants().add(new Participant(second, true));
    civil.getAttackers().getMainParticipants().getFirst().getBackers().add(backer);
    assertEquals(Set.of("host", "rebels", "second"), CivilWarBorderLock.involvedIds(civil));
    assertSame(civil, CivilWarBorderLock.findActiveCivilWarInvolving("HOST"));
    assertTrue(CivilWarBorderLock.isLocked(second));
    assertEquals(CivilWarCopy.ALREADY_IN_CIVIL_WAR, CivilWarBorderLock.refuseStart(null, host));
    assertFalse(CivilWarBorderLock.isLocked(backer));
    civil.end(WarEndReason.ADMIN_END);
    assertFalse(CivilWarBorderLock.isCivilWar(civil));
    assertFalse(CivilWarBorderLock.isLocked(host));
  }

  @Test
  void snapshotLocksIncludeDetachedVassalsAndTheirCurrentDescendantsButNotForeignBackers() {
    var host = fixture.saved("host", "Alice");
    var rebels = fixture.saved("rebels", "Bob");
    var vassal = fixture.saved("vassal", "Cara");
    var child = fixture.saved("child", "Dan");
    var outside = fixture.saved("outside", "Elena");
    fixture.subject(vassal, child);
    var war = war(3, rebels, host);
    var snapshot = snapshot(host, rebels);
    snapshot.setWartimeVassalEnds(
        Arrays.asList(
            null,
            new CivilWarWartimeVassalEnd("vassal", "host", "vassal"),
            new CivilWarWartimeVassalEnd("missing", "host", "vassal")));
    war.setCivilWarSnapshot(snapshot);
    assertEquals(
        Set.of("host", "rebels", "vassal", "child", "missing"),
        CivilWarBorderLock.involvedIds(war));
    snapshot
        .getWartimeVassalEnds()
        .removeIf(end -> end != null && "missing".equals(end.factionId()));
    FactionManager.factions.add(null);
    outside.setId(null);
    try {
      assertEquals(
          Set.of("host", "rebels", "vassal", "child"), CivilWarBorderLock.involvedIds(war));
    } finally {
      FactionManager.factions.remove(null);
      outside.setId("outside");
    }
    assertTrue(CivilWarBorderLock.isLocked(child));
    assertFalse(CivilWarBorderLock.isLocked(outside));
  }

  @Test
  void pendingPayloadWarsAndLockedSupportersPreventStartingAnotherCivilWar() {
    var host = fixture.saved("host", "Alice");
    var supporter = fixture.saved("supporter", "Bob");
    var other = fixture.saved("other", "Cara");
    fixture.subject(host, supporter);
    var data = new MovementData();
    data.leader = "Bob";
    var movement = new Movement(host, data);
    movement.getSupporters().addFaction(supporter);
    assertEquals(CivilWarCopy.COULD_NOT_START, CivilWarBorderLock.refuseStart(movement, null));
    assertFalse(CivilWarBorderLock.hostBlockedByDeJureOrTransfer(null));
    var active = war(4, supporter, other);
    active.setMovementId("supporter-civil-war");
    assertEquals(CivilWarCopy.ALREADY_IN_CIVIL_WAR, CivilWarBorderLock.refuseStart(movement, host));
    movement.getSupporters().getFactions().clear();
    assertEquals(CivilWarCopy.ALREADY_IN_CIVIL_WAR, CivilWarBorderLock.refuseStart(movement, host));
    active.end(WarEndReason.ADMIN_END);
    assertNull(CivilWarBorderLock.refuseStart(movement, host));
    var payload = war(5, other, host);
    payload.setGoal(null);
    assertFalse(CivilWarBorderLock.hostBlockedByDeJureOrTransfer(host));
    payload.setGoal(WarGoalType.DE_JURE_ANNEX);
    assertEquals(CivilWarCopy.HOST_IS_WAR_PAYLOAD, CivilWarBorderLock.refuseStart(null, host));
    payload.end(WarEndReason.ADMIN_END);
    var transfer = war(6, supporter, other);
    transfer.setGoal(WarGoalType.TRANSFER_SUBJECT);
    assertFalse(CivilWarBorderLock.hostBlockedByDeJureOrTransfer(host));
    transfer.setSubjectFactionId(host.getId());
    assertTrue(CivilWarBorderLock.hostBlockedByDeJureOrTransfer(host));
    transfer.end(WarEndReason.ADMIN_END);
    var hostMovementData = new MovementData();
    hostMovementData.leader = "Alice";
    var hostMovement = new Movement(host, hostMovementData);
    hostMovement.getSupporters().addFaction(supporter);
    assertNull(CivilWarBorderLock.refuseStart(hostMovement, host));
  }

  @Test
  void landConnectivityHandlesCyclesDanglingNeighboursAndUnavailableMapBoundaries() {
    var host = fixture.saved("host", "Alice");
    province(1, "PLAINS");
    province(2, "PLAINS");
    province(3, "PLAINS");
    link(1, 2);
    link(2, 3);
    provinces.get(2).addNeighbour(999);
    host.addProvince(1);
    host.setCapital(1, true, false);
    var plan = new LandSplitPlan(List.of(3), List.of(1, 2));
    assertTrue(CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(host, plan));
    assertTrue(CivilWarSeaPortGate.landReachable(manager, 1, 1));
    assertTrue(CivilWarSeaPortGate.landReachable(manager, 1, 3));
    assertFalse(CivilWarSeaPortGate.landReachable(manager, 999, 3));
    assertFalse(CivilWarSeaPortGate.landReachable(manager, 1, 998));
    assertFalse(CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(manager, null, plan));
    assertFalse(CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(manager, host, null));
    assertTrue(CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(null, host, plan));
    var plugin = SimpleFactions.plugin;
    try {
      SimpleFactions.plugin = null;
      assertTrue(CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(host, plan));
    } finally {
      SimpleFactions.plugin = plugin;
    }
  }

  @Test
  void onlyARebelPortOnTheSharedSeaCanSupportAnIslandRebellion() {
    var host = fixture.saved("host", "Alice");
    province(1, "PLAINS");
    province(2, "SEA");
    province(3, "PLAINS");
    province(4, "PLAINS");
    province(5, "SEA");
    link(1, 2);
    link(2, 3);
    link(4, 5);
    host.addProvince(1);
    host.addProvince(3);
    host.addProvince(4);
    host.setCapital(1, true, false);
    var plan = new LandSplitPlan(List.of(3, 4), List.of(1));
    host.getInstallationHandler().acceptTransferred(installation("fort", InstallationKind.FORT, 3));
    host.getInstallationHandler()
        .acceptTransferred(installation("loyal-port", InstallationKind.PORT, 1));
    host.getInstallationHandler()
        .acceptTransferred(installation("wrong-sea", InstallationKind.PORT, 4));
    host.getInstallationHandler()
        .acceptTransferred(installation("missing-map", InstallationKind.PORT, 999));
    assertFalse(CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(manager, host, plan));
    assertFalse(CivilWarSeaPortGate.hasPortOnSea(host, List.of(999), manager, Set.of(2)));
    assertFalse(CivilWarSeaPortGate.hasPortOnSea(host, List.of(4), manager, Set.of(2)));
    host.getInstallationHandler()
        .acceptTransferred(installation("rebel-port", InstallationKind.PORT, 3));
    assertTrue(CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(manager, host, plan));
    assertEquals(5, host.getInstallationHandler().getAll().size());
    assertTrue(
        CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(
            manager, host, new LandSplitPlan(List.of(4), List.of(1))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void untanglingRestoresActualLandArmiesTreasuriesGuildsAndMembers(boolean attackerWins) {
    fixture.regiment("guard", false, 0, 2);
    var host = fixture.saved("host", "Alice");
    var rebels = fixture.saved("rebels", "Bob");
    province(1, "PLAINS");
    province(2, "PLAINS");
    host.addProvince(1);
    rebels.addProvince(2);
    host.setCapital(1, true, false);
    rebels.setCapital(2, true, false);
    var origin = fixture.guild(host, "origin", "ActingLeader");
    var extra = fixture.guild(rebels, "extra", "Elena");
    extra.setCapital(2, false);
    extra.getBank().deposit(20.0);
    host.getOrCreateMainGuild().getBank().deposit(100.0);
    rebels.getOrCreateMainGuild().getBank().deposit(30.0);
    rebels.getOrCreateMainGuild().addMember("Cara");
    host.getMilitary().getRegiment("guard").setCurrentSlots(7);
    rebels.getMilitary().getRegiment("guard").setCurrentSlots(3);
    var port = installation("harbour", InstallationKind.PORT, 2);
    rebels.getInstallationHandler().acceptTransferred(port);
    rebels.getSettlementHandler().found("Rebel Town", 2, 40, 0);
    var movedTitle = fixture.title("moved", "county", 2);
    rebels.addTitle(movedTitle);
    var snapshot = snapshot(host, rebels);
    snapshot.setTransferredProvinces(Map.of(2, host.getId()));
    snapshot.setHostOldCapitalId(1);
    snapshot.setMovedTitleId(movedTitle.getId());
    snapshot.setRebelMainGuildOwnName("Returned Company");
    snapshot.setWantedLeaderName("Bob");
    snapshot.setMemberMoves(
        Arrays.asList(
            null,
            new CivilWarMemberMove(null, "origin", false),
            new CivilWarMemberMove(" ", "origin", false),
            new CivilWarMemberMove("Bob", "origin", true),
            new CivilWarMemberMove("Cara", "removed-guild", false)));
    var war = war(7, rebels, host);
    war.setCivilWarSnapshot(snapshot);

    CivilWarUntangleService.restore(
        war, attackerWins ? WarEndReason.ATTACKER_VICTORY : WarEndReason.WHITE_PEACE);

    assertNull(FactionManager.getByString(rebels.getId()));
    assertEquals(Set.of(1, 2), Set.copyOf(host.getProvinces()));
    assertEquals(1, host.getCapital());
    assertSame(host, extra.getFaction());
    assertSame(port, host.getInstallationHandler().getById("harbour"));
    assertNotNull(host.getSettlementHandler().getByProvince(2));
    assertTrue(host.hasTitle(movedTitle));
    assertEquals(10, host.getMilitary().getRegiment("guard").getCurrentSlots());
    assertEquals(0, rebels.getMilitary().getRegiment("guard").getCurrentSlots());
    assertEquals(
        150.0,
        host.getGuildHandler().getGuilds().stream()
            .mapToDouble(g -> g.getBank().getWealth())
            .sum());
    assertTrue(host.getOrCreateMainGuild().isMember("Cara"));
    assertEquals(attackerWins, host.getOrCreateMainGuild().isMember("Bob"));
    assertEquals(attackerWins ? "ActingLeader" : "Bob", origin.getLeader());
    assertEquals(
        1, host.getGuildHandler().getGuilds().stream().filter(g -> g.isMember("Bob")).count());
  }

  @Test
  void absentSnapshotAndMissingRestorationTargetsLeaveUnrelatedFactionStateIntact() {
    var host = fixture.saved("host", "Alice");
    var other = fixture.saved("other", "Bob");
    var war = war(8, other, host);
    assertDoesNotThrow(() -> CivilWarUntangleService.restore(null));
    CivilWarUntangleService.restore(war);
    assertEquals(List.of(host, other), FactionManager.factions);
    var snapshot = new CivilWarSnapshot();
    snapshot.setHostFactionId("missing");
    snapshot.setWartimeVassalEnds(
        Arrays.asList(null, new CivilWarWartimeVassalEnd("missing", "host", "vassal")));
    war.setCivilWarSnapshot(snapshot);
    CivilWarUntangleService.restore(war);
    assertEquals(List.of(host, other), FactionManager.factions);
    assertNull(RelationManager.getOverlord(other));
  }

  @Test
  void restoringDetachedVassalsUnderstandsBothRelationDirectionsAndIgnoresUnrelatedTypes() {
    var host = fixture.saved("host", "Alice");
    var vassal = fixture.saved("vassal", "Bob");
    assertNull(CivilWarUntangleService.snapshotVassalageTypeId(null, host));
    assertNull(CivilWarUntangleService.snapshotVassalageTypeId(vassal, host));
    CivilWarUntangleService.restoreVassalRelation("vassal", "host", "neutral");
    CivilWarUntangleService.restoreVassalRelation("vassal", "host", null);
    assertNull(RelationManager.getOverlord(vassal));
    CivilWarUntangleService.restoreVassalRelation("vassal", "host", "overlord");
    assertEquals("host", RelationManager.getOverlord(vassal));
    assertEquals("vassal", CivilWarUntangleService.snapshotVassalageTypeId(vassal, host));
    host.getDiplomacyHandler().removeRelation(vassal.getId());
    assertEquals("vassal", CivilWarUntangleService.snapshotVassalageTypeId(vassal, host));
    vassal.getDiplomacyHandler().removeRelation(host.getId());
    assertNull(CivilWarUntangleService.snapshotVassalageTypeId(vassal, host));
  }

  @Test
  void nullableSnapshotCollectionsNormalizeToIndependentEmptyMutableSnapshots() {
    var snapshot = new CivilWarSnapshot();
    var transfers = new LinkedHashMap<Integer, String>();
    transfers.put(2, "host");
    var ends = new ArrayList<CivilWarWartimeVassalEnd>();
    ends.add(new CivilWarWartimeVassalEnd("subject", "host", "vassal"));
    var moves = new ArrayList<CivilWarMemberMove>();
    moves.add(new CivilWarMemberMove("Bob", "guild", false));
    snapshot.setTransferredProvinces(transfers);
    snapshot.setWartimeVassalEnds(ends);
    snapshot.setMemberMoves(moves);
    transfers.clear();
    ends.clear();
    moves.clear();
    assertEquals(Map.of(2, "host"), snapshot.getTransferredProvinces());
    assertEquals(1, snapshot.getWartimeVassalEnds().size());
    assertEquals(1, snapshot.getMemberMoves().size());
    snapshot.setTransferredProvinces(null);
    snapshot.setWartimeVassalEnds(null);
    snapshot.setMemberMoves(null);
    assertTrue(snapshot.getTransferredProvinces().isEmpty());
    assertTrue(snapshot.getWartimeVassalEnds().isEmpty());
    assertTrue(snapshot.getMemberMoves().isEmpty());
  }

  @Test
  void restoringWithOnlyABaseGuildTypeCannotLeaveMembersOrFundsBoundToTheDeletedRebels()
      throws Exception {
    new GuildLoader()
        .load(files.write("guilds.yml", "realm:\n  base: true\n  default: true\n").toFile());
    var host = fixture.saved("host", "Alice");
    var rebels = fixture.saved("rebels", "Bob");
    province(1, "PLAINS");
    province(2, "PLAINS");
    host.addProvince(1);
    rebels.addProvince(2);
    host.setCapital(1, true, false);
    rebels.setCapital(2, true, false);
    host.getOrCreateMainGuild().getBank().deposit(100.0);
    var base = rebels.getOrCreateMainGuild();
    base.getBank().deposit(30.0);
    base.addMember("Cara");
    base.setName("Rebel Company");
    base.setFavoured(true);
    base.setDividendPercent(25.0);
    rebels.getSettlementHandler().found("Rebel Town", 2, 40, 0);
    var settlement = rebels.getSettlementHandler().getByProvince(2);
    var port = installation("rebel-port", InstallationKind.PORT, 2);
    rebels.getInstallationHandler().acceptTransferred(port);
    var war = war(9, rebels, host);
    war.setCivilWarSnapshot(snapshot(host, rebels));

    CivilWarUntangleService.restore(war);

    assertNull(FactionManager.getByString(rebels.getId()));
    assertTrue(host.isMember("Bob"));
    assertTrue(host.isMember("Cara"));
    assertSame(base, host.getGuildHandler().getGuild(base.getId()));
    assertSame(host, base.getFaction());
    assertTrue(base.isBase());
    assertEquals("Rebel Company", base.getOwnName());
    assertEquals(host.getBannerPatterns(), base.getBannerPatterns());
    assertTrue(base.isFavoured());
    assertEquals(25.0, base.getDividendPercent());
    assertSame(settlement, host.getSettlementHandler().getByProvince(2));
    assertSame(port, host.getInstallationHandler().getById("rebel-port"));
    assertEquals(Set.of(1, 2), Set.copyOf(host.getProvinces()));
    assertEquals(1, host.getCapital());
    assertEquals(
        130.0,
        host.getGuildHandler().getGuilds().stream()
            .mapToDouble(g -> g.getBank().getWealth())
            .sum());
    assertTrue(
        host.getGuildHandler().getGuilds().stream().allMatch(g -> g.getFaction() == host),
        "Restored guilds must never reference the deleted temporary realm");
  }

  @Test
  void publicationFailureStillRetiresAnEmptyTemporaryRealmAndKeepsRestoredFundsAndMembers() {
    var host = fixture.saved("host", "Alice");
    var rebels = fixture.saved("rebels", "Bob");
    rebels.setRGB("71,72,73");
    rebels.getOrCreateMainGuild().getBank().deposit(19.0);
    var restored = rebels.getOrCreateMainGuild();
    var subject = fixture.saved("subject", "Cara");
    fixture.subject(rebels, subject);
    var snapshot = snapshot(host, rebels);
    snapshot.setWartimeVassalEnds(
        List.of(new CivilWarWartimeVassalEnd(subject.getId(), host.getId(), "vassal")));
    snapshot.setMovedTitleId("removed-title");
    var war = war(10, rebels, host);
    war.setCivilWarSnapshot(snapshot);
    var savedRebels = files.root.resolve("Data/rebels.json");
    assertTrue(new net.tfminecraft.simplefactions.database.Database().saveFactionChecked(rebels));
    assertTrue(Files.exists(savedRebels));
    var failures = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(
            call -> {
              if (restored.getFaction() == host) {
                failures.incrementAndGet();
                throw new IllegalStateException("Map publisher unavailable");
              }
              return null;
            })
        .when(fixture.map)
        .enqueue("nation", "71,72,73");

    assertDoesNotThrow(() -> CivilWarUntangleService.restore(war));

    assertEquals(1, failures.get());
    assertEquals(List.of(host, subject), FactionManager.factions);
    assertEquals(host.getId(), RelationManager.getOverlord(subject));
    assertFalse(subject.getDiplomacyHandler().getRelations().containsKey(rebels.getId()));
    assertFalse(
        Files.exists(savedRebels), "The temporary realm must not reappear on the next load");
    assertSame(host, restored.getFaction());
    assertSame(restored, host.getGuildHandler().getGuild(restored.getId()));
    assertTrue(host.isMember("Bob"));
    assertEquals(19.0, restored.getBank().getWealth());
    assertTrue(host.getProvinces().isEmpty());
  }

  @Test
  void sharedSeaSearchReturnsOnlyConnectedOceanTilesAndRejectsMissingLandOrServer() {
    var first = fixture.saved("first", "Alice");
    var second = fixture.saved("second", "Bob");
    province(1, "PLAINS");
    province(2, "SEA");
    province(3, "SEA");
    province(4, "PLAINS");
    province(5, "SEA");
    province(6, "PLAINS");
    province(7, "WATER");
    link(1, 2);
    link(2, 3);
    link(3, 4);
    link(5, 6);
    link(3, 7);
    link(7, 5);
    provinces.get(2).addNeighbour(999);
    first.addProvince(1);
    first.addProvince(6);
    second.addProvince(4);
    assertEquals(
        Set.of(2, 3),
        SeaConnectivity.sharedSeaProvinces(manager, first.getProvinces(), second.getProvinces()));
    assertTrue(SeaConnectivity.hasSeaConnection(first, second));
    assertFalse(SeaConnectivity.hasSeaConnection(null, first, second));
    assertFalse(SeaConnectivity.hasSeaConnection(manager, null, second));
    assertFalse(SeaConnectivity.hasSeaConnection(manager, first, null));
    assertTrue(SeaConnectivity.sharedSeaProvinces(null, List.of(1), List.of(4)).isEmpty());
    assertTrue(SeaConnectivity.sharedSeaProvinces(manager, null, List.of(4)).isEmpty());
    assertTrue(SeaConnectivity.sharedSeaProvinces(manager, List.of(2, 999), List.of(4)).isEmpty());
    assertTrue(SeaConnectivity.sharedSeaProvinces(manager, List.of(1), List.of(999)).isEmpty());
    var plugin = SimpleFactions.plugin;
    try {
      SimpleFactions.plugin = null;
      assertFalse(SeaConnectivity.hasSeaConnection(first, second));
    } finally {
      SimpleFactions.plugin = plugin;
    }
  }

  private War war(int id, Faction attackers, Faction defenders) {
    War war = new War(id, attackers, defenders);
    WarManager.get().add(war);
    return war;
  }

  private CivilWarSnapshot snapshot(Faction host, Faction rebels) {
    var snapshot = new CivilWarSnapshot();
    snapshot.setHostFactionId(host.getId());
    snapshot.setTempRebelFactionId(rebels.getId());
    return snapshot;
  }

  private Installation installation(String id, InstallationKind kind, int province) {
    return new Installation(id, id, kind, province, province * 20, 0, 1L);
  }

  private Province province(int id, String terrain) {
    var province = new Province(id, terrain, 50, id * 20, 0);
    provinces.put(id, province);
    return province;
  }

  private void link(int first, int second) {
    provinces.get(first).addNeighbour(second);
    provinces.get(second).addNeighbour(first);
  }
}
