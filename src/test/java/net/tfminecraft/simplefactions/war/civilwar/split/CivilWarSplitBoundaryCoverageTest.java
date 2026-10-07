package net.tfminecraft.simplefactions.war.civilwar.split;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.MovementData;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarGoalMapper;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarTempRebelFactory;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CivilWarSplitBoundaryCoverageTest {
  private PersistenceFilesFixture files;
  private FactionDomainFixture fixture;
  private ProvinceManager manager;
  private final Map<Integer, Province> provinces = new LinkedHashMap<>();

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    Files.createDirectories(files.root.resolve("Data"));
    fixture = new FactionDomainFixture();
    manager = new ProvinceManager();
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
  void aTwoProvinceSplitCannotInventLoyalLandFromALostCapital() {
    Faction host = fixture.saved("host", "Alice");
    for (int id : List.of(1, 2, 3)) {
      province(id);
      host.addProvince(id);
    }
    host.setCapital(1, true, false);
    host.removeProvince(1, false);
    assertEquals(List.of(2, 3), host.getProvinces());
    assertEquals(1, host.getCapital());
    var supporters = fixture.guild(host, "supporters", "Bob");

    assertNull(
        CivilWarLandSplitService.plan(host, List.of(supporters)),
        "A lost capital must not become loyal land while an owned province disappears from the"
            + " split");
    assertEquals(List.of(2, 3), host.getProvinces());
  }

  @Test
  void anImportedDuplicateCapitalCannotProduceASecondSideOfTheLandPartition() {
    province(1);
    var data = fixture.data("host", "Alice");
    data.capital = 1;
    data.provinces.add(1);
    data.provinces.add(1);
    var host = fixture.saved(data);

    assertNull(CivilWarLandSplitService.plan(host, List.of()));

    assertEquals(List.of(1, 1), host.getProvinces());
    assertEquals(1, host.getCapital());
  }

  @Test
  void aFullMutedRedPaletteStillAllocatesAnUnusedRebelMapColour() {
    Faction host = fixture.saved("host", "Alice");
    Set<String> taken = new LinkedHashSet<>();
    for (int offset = 0; offset <= 80; offset++) {
      Faction existing = fixture.saved("existing_" + offset, "Leader" + offset);
      String rgb = "138," + (48 + offset) + ",48";
      existing.setRGB(rgb);
      taken.add(rgb);
    }

    Faction rebels = CivilWarTempRebelFactory.create(host, "Bob");

    assertNotNull(rebels);
    assertFalse(
        taken.contains(rebels.getRGB()),
        "The palette limit must not reuse another faction's map identity");
    assertSame(rebels, FactionManager.getByRGB(rebels.getRGB()));
    assertEquals(83, FactionManager.factions.size());
    for (Faction existing : FactionManager.factions) {
      if (existing != rebels) assertNotEquals(existing.getRGB(), rebels.getRGB());
    }
  }

  @Test
  void paidRegimentsSplitAndRollbackExactlyWhileLeviesAndUnknownTypesRemainUntouched() {
    fixture.regiment("guard", false, 2, 3);
    fixture.regiment("levy", true, 3, 0);
    var host = fixture.saved("host", "Alice");
    fixture.regiment("new-type", false, 6, 2);
    var rebels = fixture.saved("rebels", "Bob");
    var retired = new Regiment(fixture.regiment("retired", false, 0, 2));
    retired.setCurrentSlots(5);
    host.getMilitary().getRegiments().add(retired);
    var hostGuard = host.getMilitary().getRegiment("guard");
    var rebelGuard = rebels.getMilitary().getRegiment("guard");
    hostGuard.setCurrentSlots(10);
    hostGuard.setFreeSlots(2);
    rebelGuard.setCurrentSlots(2);
    rebelGuard.setFreeSlots(2);
    var moved = CivilWarRegimentSplitService.split(host, rebels, 50);
    assertEquals(Map.of("guard", 4), moved);
    assertEquals(6, hostGuard.getCurrentSlots());
    assertEquals(4, rebelGuard.getCurrentSlots());
    assertEquals(2, hostGuard.getFreeSlots());
    assertEquals(0, rebelGuard.getFreeSlots());
    assertEquals(8, hostGuard.getPaidSlots() + rebelGuard.getPaidSlots());
    assertEquals(3, host.getMilitary().getRegiment("levy").getCurrentSlots());
    assertEquals(3, rebels.getMilitary().getRegiment("levy").getCurrentSlots());
    assertEquals(5, retired.getCurrentSlots());
    assertEquals(0, rebels.getMilitary().getRegiment("new-type").getCurrentSlots());
    var imported = new LinkedHashMap<String, Integer>(moved);
    imported.put(null, 2);
    imported.put("missing-amount", null);
    imported.put("negative", -1);
    imported.put("zero", 0);
    imported.put("unknown", 5);
    CivilWarRegimentSplitService.rollback(host, rebels, imported);
    assertEquals(10, hostGuard.getCurrentSlots());
    assertEquals(0, rebelGuard.getCurrentSlots());
    rebelGuard.setCurrentSlots(3);
    rebels.getMilitary().getRegiment("new-type").setCurrentSlots(2);
    CivilWarRegimentSplitService.mergeRemaining(rebels, host);
    assertEquals(13, hostGuard.getCurrentSlots());
    assertEquals(0, rebelGuard.getCurrentSlots());
    assertEquals(
        2,
        rebels.getMilitary().getRegiment("new-type").getCurrentSlots(),
        "A missing destination type must not silently consume surviving troops");
    CivilWarRegimentSplitService.mergeRemaining(rebels, host);
    assertEquals(13, hostGuard.getCurrentSlots());
    assertEquals(3, rebels.getMilitary().getRegiment("levy").getCurrentSlots());
  }

  @Test
  void publicNullIdRegimentsAreSkippedWithoutConsumingKnownOrUnnamedPaidTroops() {
    fixture.regiment("levy", true, 3, 0);
    fixture.regiment("guard", false, 0, 2);
    var host = fixture.saved("host", "Alice");
    var rebels = fixture.saved("rebels", "Bob");
    var hostGuard = host.getMilitary().getRegiment("guard");
    var rebelGuard = rebels.getMilitary().getRegiment("guard");
    hostGuard.setCurrentSlots(10);
    var config = new org.bukkit.configuration.file.YamlConfiguration();
    config.set("item.material", "PAPER");
    config.set("default-slots", 5);
    config.set("upkeep", 2);
    var unnamed = new Regiment(null, config);
    unnamed.setFreeSlots(0);
    host.getMilitary().getRegiments().add(unnamed);

    assertEquals(Map.of("guard", 5), CivilWarRegimentSplitService.split(host, rebels, 50));

    assertEquals(5, hostGuard.getPaidSlots());
    assertEquals(5, rebelGuard.getPaidSlots());
    assertEquals(5, unnamed.getPaidSlots());
    assertTrue(host.getMilitary().getRegiments().remove(unnamed));
    rebels.getMilitary().getRegiments().add(unnamed);

    CivilWarRegimentSplitService.mergeRemaining(rebels, host);

    assertEquals(10, hostGuard.getPaidSlots());
    assertEquals(0, rebelGuard.getPaidSlots());
    assertEquals(5, unnamed.getPaidSlots());
    assertSame(unnamed, rebels.getMilitary().getRegiments().getLast());
    assertEquals(3, rebels.getMilitary().getRegiment("levy").getCurrentSlots());
  }

  @Test
  void missingSplitParticipantsAndEmptyRollbacksCannotChangeExistingTroops() {
    fixture.regiment("guard", false, 5, 2);
    var host = fixture.saved("host", "Alice");
    var rebels = fixture.saved("rebels", "Bob");
    assertTrue(CivilWarRegimentSplitService.split(null, rebels, 50).isEmpty());
    assertTrue(CivilWarRegimentSplitService.split(host, null, 50).isEmpty());
    assertTrue(CivilWarRegimentSplitService.split(host, rebels, 0).isEmpty());
    assertTrue(CivilWarRegimentSplitService.split(host, rebels, -10).isEmpty());
    CivilWarRegimentSplitService.rollback(null, rebels, Map.of("guard", 1));
    CivilWarRegimentSplitService.rollback(host, null, Map.of("guard", 1));
    CivilWarRegimentSplitService.rollback(host, rebels, null);
    CivilWarRegimentSplitService.rollback(host, rebels, Map.of());
    CivilWarRegimentSplitService.mergeRemaining(null, host);
    CivilWarRegimentSplitService.mergeRemaining(rebels, null);
    assertEquals(5, host.getMilitary().getRegiment("guard").getCurrentSlots());
    assertEquals(5, rebels.getMilitary().getRegiment("guard").getCurrentSlots());
  }

  @Test
  void landPlanAndRollbackPreserveThePartitionAndIgnoreAnIncompleteImportedGuild() {
    var host = fixture.saved("host", "Alice");
    var rebels = fixture.saved("rebels", "Bob");
    for (int id : List.of(1, 2, 3)) {
      province(id);
      host.addProvince(id);
    }
    host.setCapital(1, true, false);
    var supporting = fixture.guild(host, "supporting", "Cara");
    fixture.guild(host, null, "Incomplete");
    var plan =
        CivilWarLandSplitService.plan(
            host,
            Arrays.asList(null, supporting),
            (province, guild) -> guild == supporting ? (province == 2 ? 10 : 0) : 1);
    assertEquals(List.of(2), plan.rebelProvinceIds());
    assertEquals(List.of(1, 3), plan.loyalProvinceIds());
    assertThrows(UnsupportedOperationException.class, () -> plan.rebelProvinceIds().add(99));
    CivilWarLandSplitService.apply(null, rebels, plan);
    CivilWarLandSplitService.apply(host, null, plan);
    CivilWarLandSplitService.apply(host, rebels, null);
    assertEquals(List.of(1, 2, 3), host.getProvinces());
    CivilWarLandSplitService.apply(host, rebels, plan);
    assertEquals(List.of(1, 3), host.getProvinces());
    assertEquals(List.of(2), rebels.getProvinces());
    CivilWarLandSplitService.rollback(null, rebels, plan);
    CivilWarLandSplitService.rollback(host, null, plan);
    CivilWarLandSplitService.rollback(host, rebels, null);
    assertEquals(List.of(2), rebels.getProvinces());
    CivilWarLandSplitService.rollback(host, rebels, plan);
    assertEquals(Set.of(1, 2, 3), Set.copyOf(host.getProvinces()));
    assertTrue(rebels.getProvinces().isEmpty());
  }

  @Test
  void defaultPresenceFallsBackSafelyWhenTheServerMapIsUnavailable() {
    var host = fixture.saved("host", "Alice");
    for (int id : List.of(1, 2, 3)) {
      province(id);
      host.addProvince(id);
    }
    var supporting = fixture.guild(host, "supporting", "Bob");
    assertEquals(0, CivilWarLandSplitService.defaultPresence().score(1, null));
    var plugin = SimpleFactions.plugin;
    try {
      SimpleFactions.plugin = null;
      assertNull(CivilWarLandSplitService.plan(host, Arrays.asList(null, supporting), null));
    } finally {
      SimpleFactions.plugin = plugin;
    }
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(null);
    assertNull(CivilWarLandSplitService.plan(host, List.of(supporting), null));
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(manager);
    assertEquals(0, CivilWarLandSplitService.defaultPresence().score(999, supporting));
  }

  @Test
  void aDetachedMainGuildUsesTheFallbackRealmWithoutMovingItsOrphanedState() {
    var host = fixture.saved("host", "Alice");
    var detached = fixture.guild(host, "detached", "Bob");
    host.getGuildHandler().removeGuild(detached.getId(), false, false);
    detached.setHost(null);
    var nation = CivilWarTempRebelFactory.createFromMainGuild(host, detached, "Cara");
    assertNotNull(nation);
    assertEquals("Cara", nation.faction().getLeader());
    assertTrue(nation.faction().isMember("Cara"));
    assertNull(nation.ownName());
    assertNull(detached.getFaction());
    assertNull(CivilWarTempRebelFactory.createFromMainGuild(host, detached, null));
    assertEquals(2, FactionManager.factions.size());
  }

  @Test
  void titleMovesRequireAnOwnedNonPrimaryTitleTheRebelsCanActuallyHold() {
    var host = fixture.saved("host", "Alice");
    var rebels = fixture.saved("rebels", "Bob");
    province(1);
    province(2);
    host.addProvince(1);
    host.addProvince(2);
    var primary = fixture.title("primary", "duchy", 1);
    var candidate = fixture.title("candidate", "county", 2);
    host.addTitle(primary);
    assertNull(CivilWarTitleMove.pick(host, rebels, 2));
    assertNull(CivilWarTitleMove.pick(null, rebels, 2));
    assertNull(CivilWarTitleMove.pick(host, null, 2));
    assertNull(CivilWarTitleMove.pick(host, rebels, 0));
    host.addTitle(candidate);
    assertNull(CivilWarTitleMove.pick(host, rebels, 2));
    host.removeProvince(2, false);
    rebels.addProvince(2);
    assertSame(candidate, CivilWarTitleMove.pick(host, rebels, 2));
    CivilWarTitleMove.transfer(null, rebels, candidate);
    CivilWarTitleMove.transfer(host, null, candidate);
    CivilWarTitleMove.transfer(host, rebels, null);
    assertTrue(host.hasTitle(candidate));
    CivilWarTitleMove.transfer(host, rebels, candidate);
    assertFalse(host.hasTitle(candidate));
    assertTrue(rebels.hasTitle(candidate));
    assertTrue(host.hasTitle(primary));
  }

  @Test
  void capitalHelpersReuseAnExistingCampAndPreserveItsIdentity() {
    var rebels = fixture.saved("rebels", "Bob");
    province(2);
    rebels.addProvince(2);
    rebels.getSettlementHandler().found("Rebel Camp", 2, 40, 0);
    var original = rebels.getSettlementHandler().getByProvince(2);
    assertEquals(2, CivilWarCapitalAssignService.foundRebelCamp(rebels, List.of(2)));
    assertSame(original, rebels.getSettlementHandler().getByProvince(2));
    assertEquals(1, rebels.getSettlementHandler().getAll().size());
    assertEquals(4, CivilWarCapitalAssignService.distance(-1, 3));
    assertEquals(-1, CivilWarCapitalAssignService.foundRebelCamp(rebels, List.of()));
    assertEquals(-1, CivilWarCapitalAssignService.foundRebelCamp(null, List.of(2)));
    assertEquals(-1, CivilWarCapitalAssignService.foundRebelCamp(rebels, null));
    assertTrue(CivilWarCapitalAssignService.directSettlements(null, List.of(2)).isEmpty());
    assertTrue(CivilWarCapitalAssignService.directSettlements(rebels, null).isEmpty());
    assertTrue(CivilWarCapitalAssignService.hostSettlementMissing(rebels, -1));
    assertFalse(CivilWarCapitalAssignService.hostSettlementMissing(null, 2));
    assertSame(original, rebels.getSettlementHandler().getByProvince(2));
  }

  @Test
  void unavailablePositiveProvinceIdsUseTheDocumentedNumericDistanceFallback() {
    province(12);
    assertEquals(3.0, CivilWarCapitalAssignService.distance(12, 15));
    assertEquals(3.0, CivilWarCapitalAssignService.distance(15, 12));
    assertEquals(1.0, CivilWarCapitalAssignService.distance(999, 1000));
  }

  @Test
  void foundingACampNeverOverwritesAnExistingSettlementWhenEveryDirectTileIsOccupied() {
    var rebels = fixture.saved("rebels", "Bob");
    province(2);
    rebels.addProvince(2);
    rebels.setCapital(2, true, false);
    rebels.getSettlementHandler().found("Existing Town", 2, 40, 0);
    var existing = rebels.getSettlementHandler().getByProvince(2);

    assertEquals(2, CivilWarCapitalAssignService.foundRebelCamp(rebels, List.of(2)));

    assertSame(existing, rebels.getSettlementHandler().getByProvince(2));
    assertTrue(existing.getName().endsWith("Existing Town"));
    assertEquals(1, rebels.getSettlementHandler().getAll().size());
  }

  @Test
  void goalMappingRejectsAbsentOrCorruptCausesAndUsesTheFirstActualProposal() {
    var host = fixture.saved("host", "Alice");
    var imported = new Movement(host, new MovementData());
    assertNull(CivilWarGoalMapper.fromFirstCause(null));
    assertNull(CivilWarGoalMapper.fromFirstCause(imported));
    imported.getCauses().add(null);
    assertNull(CivilWarGoalMapper.fromFirstCause(imported));
    assertNull(CivilWarGoalMapper.fromAction(null));
    var proposal = new Proposal("Alice", host.getGovernment());
    proposal.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));
    proposal.setTarget("Alice");
    var supported = new Movement(host, "Alice", proposal);
    assertEquals(WarGoalType.OVERTHROW, CivilWarGoalMapper.fromFirstCause(supported));
    proposal = new Proposal("Alice", host.getGovernment());
    proposal.setPoliticalActionProposal(new PoliticalAction(Action.NATIONHOOD));
    assertNull(CivilWarGoalMapper.fromFirstCause(new Movement(host, "Alice", proposal)));
  }

  private Province province(int id) {
    Province province = new Province(id, "PLAINS", 50, id * 20, 0);
    provinces.put(id, province);
    return province;
  }
}
