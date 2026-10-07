package net.tfminecraft.simplefactions.war.battle.military;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.WartimeInstallationService;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.map.export.ZocRealm;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.zoc.FortControlService;
import net.tfminecraft.simplefactions.war.campaign.zoc.FortZocIndex;
import net.tfminecraft.simplefactions.war.campaign.zoc.FortZocIndex.OperationalFort;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommitment;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattleMilitaryBoundaryCoverageTest {
  @Test
  void aMissingPublicFactionRegistryEntryDoesNotHideSurvivingPhysicalForts() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction owner = domain.saved("registry_fort_owner", "FortLeader");
      domain.provinceData.put(10, new Province(10, "PLAINS", 50, 100, 100));
      Installation fort = fort(owner, "surviving", 10);
      FactionManager.factions.add(null);

      List<OperationalFort> snapshot = FortZocIndex.listOperationalForts();
      FortZocIndex index = FortZocIndex.fromGameState();

      assertEquals(1, snapshot.size());
      assertSame(owner, snapshot.getFirst().owner());
      assertEquals(fort.getStableKey(), snapshot.getFirst().stableKey());
      assertEquals(snapshot, index.fortsCovering(10));
      assertEquals(fort.getStableKey(), index.fortForProvince(10).orElseThrow().stableKey());
      assertTrue(FortZocIndex.fromForts(null).fortForProvince(10).isEmpty());
      assertTrue(FortZocIndex.fromForts(List.of()).fortsCovering(10).isEmpty());
    }
  }

  @Test
  void aLegacyControllerFollowsItsPhysicalFortWhenOccupationRenamesItAroundAPortCollision()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      Installation port = rig.install(rig.attacker, "shared", InstallationKind.PORT, 10);
      Installation fort = rig.install(rig.defender, "shared", InstallationKind.FORT, 20);
      rig.war.setFortControllers(Map.of("shared", CampaignCoalition.AGGRESSOR));
      assertEquals(
          CampaignCoalition.AGGRESSOR,
          FortControlService.controllerForInstallation(rig.war, fort).orElseThrow());

      WartimeInstallationService.occupy(rig.war, rig.attacker, 20);
      Installation moved =
          rig.attacker.getInstallationHandler().getByProvince(InstallationKind.FORT, 20);
      assertNotNull(moved);
      assertNotEquals("shared", moved.getId());
      assertEquals(fort.getStableKey(), moved.getStableKey());
      assertSame(port, rig.attacker.getInstallationHandler().getById("shared"));
      assertNull(rig.defender.getInstallationHandler().getByProvince(InstallationKind.FORT, 20));
      War restored = roundTrip(rig.war);

      assertEquals(
          CampaignCoalition.AGGRESSOR,
          FortControlService.controllerForInstallation(restored, moved).orElse(null),
          "A holder-local rename must not orphan an existing legacy controller");
      assertEquals(
          rig.defender.getId(), restored.getWartimeInstallationOwners().get(moved.getStableKey()));
      assertSame(
          rig.attacker,
          ZocRealm.resolveExportControllerFaction(moved, rig.attacker, List.of(restored)));
    }
  }

  @Test
  void aStaleCaptureProvinceCannotOverwriteTheUniqueFortInAnotherProvince() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("fort_iron", "Alice");
      Faction defender = domain.saved("fort_river", "Bob");
      Installation fort = fort(defender, "shared", 20);
      War war = war(attacker, defender);
      war.setFortControllers(Map.of("shared", CampaignCoalition.DEFENDER));

      FortControlService.setControllerAtProvince(war, "shared", 30, CampaignCoalition.AGGRESSOR);

      War restored = roundTrip(war);
      assertEquals(
          CampaignCoalition.DEFENDER,
          FortControlService.controllerForInstallation(restored, fort).orElseThrow());
      assertEquals(Map.of("shared", CampaignCoalition.DEFENDER), restored.getFortControllers());
      assertSame(
          defender, ZocRealm.resolveExportControllerFaction(fort, defender, List.of(restored)));
    }
  }

  @Test
  void incompletePublicRosterEntriesDoNotExcludeValidFactionMembers() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("roster_iron", "Alice");
      Faction defender = domain.saved("roster_river", "Bob");
      Faction subject = domain.saved("roster_subject", "Charlie");
      Faction ally = domain.saved("roster_ally", "Dana");
      Faction malformed = domain.saved("roster_removed_identity", "RemovedLeader");
      domain.subject(attacker, subject);
      War war = war(attacker, defender);
      var main = war.getAttackers().getMainParticipants().getFirst();
      main.getSubjects().add(null);
      main.getSubjects().add(malformed);
      main.getAllies().put(subject, true);
      main.getAllies().put(ally, true);
      malformed.setId(null);

      assertEquals(
          List.of(attacker, subject, ally),
          BattleSideMembers.collectParticipatingFactions(war.getAttackers()));
      assertEquals(
          java.util.Set.of("Alice", "Charlie", "Dana"),
          BattleSideMembers.collectEligibleMemberNames(war.getAttackers()));
      assertEquals(3, BattleSideMembers.countEligibleMembers(war.getAttackers()));
      assertTrue(BattleSideMembers.collectParticipatingFactions(null).isEmpty());
      assertNull(BattleSideMembers.resolveSide(null, attacker));
      assertNull(BattleSideMembers.resolveSide(war, null));
      // Resolve only the intact public state; malformed optional rows are tolerated by collection.
      main.getSubjects().remove(null);
      main.getSubjects().remove(malformed);
      assertEquals(BelligerentRole.ATTACKER, BattleSideMembers.resolveSide(war, subject));
      assertEquals(BelligerentRole.DEFENDER, BattleSideMembers.resolveSide(war, defender));
      Faction outsider = domain.saved("roster_outsider", "OutsiderLeader");
      assertNull(BattleSideMembers.resolveSide(war, outsider));
    }
  }

  @Test
  void missingOrForeignPoolRequestsCannotSpendAnActualArmiesRegiments() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("pool_iron", "Alice");
      Faction defender = domain.saved("pool_river", "Bob");
      Faction outsider = domain.saved("pool_outsider", "Charlie");
      Regiment guard = regiment(attacker, "guard", 7, true);
      War war = war(attacker, defender);
      War other = new War(998012, outsider, defender);
      assertEquals(
          PoolMode.DEFENSIVE, BattlePoolService.resolvePoolMode(null, 10, war.getAttackers()));
      assertEquals(PoolMode.DEFENSIVE, BattlePoolService.resolvePoolMode(war, 10, null));
      assertEquals(
          PoolMode.DEFENSIVE, BattlePoolService.resolvePoolMode(war, 10, other.getAttackers()));
      assertFalse(BattlePoolService.isMilitiaEligible(null, 10));
      outsider.setId(null);
      assertFalse(BattlePoolService.isMilitiaEligible(outsider, 10));
      assertTrue(
          BattlePoolService.eligibleRegiments(null, 10, war.getAttackers(), PoolMode.OFFENSIVE)
              .isEmpty());
      assertTrue(BattlePoolService.eligibleRegiments(war, 10, null, PoolMode.OFFENSIVE).isEmpty());
      assertTrue(BattlePoolService.eligibleRegiments(war, 10, war.getAttackers(), null).isEmpty());
      assertEquals(
          Map.of(attacker.getId(), Map.of("guard", 7)),
          BattlePoolService.eligibleRegiments(war, 10, war.getAttackers(), PoolMode.OFFENSIVE));
      assertEquals(7, guard.getCurrentSlots());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void largeConfiguredArmiesCannotWrapTheirAvailablePoolBelowOneContributingArmy(boolean levies) {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("pool_iron", "Alice");
      Faction defender = domain.saved("pool_river", "Bob");
      Faction firstSource = levies ? domain.saved("pool_first_subject", "Charlie") : attacker;
      Faction secondSource = levies ? domain.saved("pool_second_subject", "Dana") : attacker;
      if (levies) {
        domain.subject(attacker, firstSource);
        domain.subject(attacker, secondSource);
      }
      Regiment first = regiment(firstSource, "first", 1_500_000_000, !levies);
      Regiment second = regiment(secondSource, "second", 1_500_000_000, !levies);
      War war = war(attacker, defender);
      List<WarCommitment> previous = WarCommitmentService.getCommitmentsForWar(war.getId());
      try {
        WarCommitmentService.restoreCommitments(
            war.getId(),
            levies
                ? List.of(
                    new WarCommitment(
                        war.getId(),
                        attacker.getId(),
                        firstSource.getId(),
                        WarCommitment.LEVY_REGIMENT_ID,
                        1_500_000_000,
                        Instant.EPOCH),
                    new WarCommitment(
                        war.getId(),
                        attacker.getId(),
                        secondSource.getId(),
                        WarCommitment.LEVY_REGIMENT_ID,
                        1_500_000_000,
                        Instant.EPOCH))
                : List.of());

        int available =
            BattlePoolService.totalCommittedRegiments(
                war, 10, war.getAttackers(), PoolMode.OFFENSIVE);

        assertTrue(
            available >= 1_500_000_000,
            "Adding a second positive army must not overflow to a smaller or empty pool: "
                + available);
        assertEquals(1_500_000_000, first.getCurrentSlots());
        assertEquals(1_500_000_000, second.getCurrentSlots());
      } finally {
        WarCommitmentService.restoreCommitments(war.getId(), previous);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void invalidPublicFortRecordsCannotDiscardValidZoneOfControl(boolean missingId) {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction owner = domain.saved("fort_owner", "FortLeader");
      domain.provinceData.put(10, new Province(10, "PLAINS", 50, 100, 100));
      OperationalFort valid = new OperationalFort("valid", owner, 10, 100L);
      List<OperationalFort> imported = new ArrayList<>();
      imported.add(valid);
      imported.add(missingId ? new OperationalFort(null, owner, 10, 100L) : null);
      imported.add(new OperationalFort("missing_owner", null, 10, 100L));

      FortZocIndex index = assertDoesNotThrow(() -> FortZocIndex.fromForts(imported));

      assertSame(valid, index.fortForProvince(10).orElseThrow());
      assertEquals(List.of(valid), index.fortsCovering(10));
      assertTrue(index.fortsCovering(99).isEmpty());
      assertEquals(3, imported.size(), "Validation must not mutate the caller's imported records");
    }
  }

  @Test
  void sameLocalFortNamesRetainBothControllersAcrossAnActualWarSaveAndReload() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("fort_iron", "Alice");
      Faction defender = domain.saved("fort_river", "Bob");
      Installation attackerFort = fort(attacker, "shared", 10);
      Installation defenderFort = fort(defender, "shared", 20);
      War war = war(attacker, defender);

      FortControlService.initializeAtDeclare(war);
      War restored = roundTrip(war);

      assertAll(
          () -> assertEquals(2, war.getFortControllers().size()),
          () -> assertEquals(war.getFortControllers(), restored.getFortControllers()),
          () ->
              assertSame(
                  attacker,
                  ZocRealm.resolveExportControllerFaction(
                      attackerFort, attacker, List.of(restored))),
          () ->
              assertSame(
                  defender,
                  ZocRealm.resolveExportControllerFaction(
                      defenderFort, defender, List.of(restored))));
      assertSame(attackerFort, attacker.getInstallationHandler().getById("shared"));
      assertSame(defenderFort, defender.getInstallationHandler().getById("shared"));
    }
  }

  @Test
  void ambiguousLegacyControllerKeysCannotApplyOneFortsCaptureToAnother() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("fort_iron", "Alice");
      Faction defender = domain.saved("fort_river", "Bob");
      Installation attackerFort = fort(attacker, "shared", 10);
      Installation defenderFort = fort(defender, "shared", 20);
      War war = war(attacker, defender);
      war.setFortControllers(Map.of("shared", CampaignCoalition.DEFENDER));

      War restored = roundTrip(war);

      assertAll(
          () ->
              assertSame(
                  attacker,
                  ZocRealm.resolveExportControllerFaction(
                      attackerFort, attacker, List.of(restored))),
          () ->
              assertSame(
                  defender,
                  ZocRealm.resolveExportControllerFaction(
                      defenderFort, defender, List.of(restored))));
      assertEquals(Map.of("shared", CampaignCoalition.DEFENDER), restored.getFortControllers());
    }
  }

  @Test
  void anUnambiguousLegacyControllerSurvivesReadCaptureAndJsonRoundTrip() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("fort_iron", "Alice");
      Faction defender = domain.saved("fort_river", "Bob");
      Installation defenderFort = fort(defender, "unique", 20);
      War war = war(attacker, defender);
      war.setFortControllers(Map.of("unique", CampaignCoalition.AGGRESSOR));
      assertSame(
          attacker, ZocRealm.resolveExportControllerFaction(defenderFort, defender, List.of(war)));
      assertTrue(FortControlService.isEnemyControlled(war, "unique", CampaignCoalition.DEFENDER));

      FortControlService.setController(war, "unique", CampaignCoalition.DEFENDER);
      War restored = roundTrip(war);

      assertSame(
          defender,
          ZocRealm.resolveExportControllerFaction(defenderFort, defender, List.of(restored)));
      assertEquals(
          CampaignCoalition.DEFENDER,
          FortControlService.controller(restored, "unique").orElseThrow());
      assertFalse(
          FortControlService.isEnemyControlled(restored, "unique", CampaignCoalition.DEFENDER));
    }
  }

  private static Installation fort(Faction owner, String id, int province) {
    Installation fort =
        new Installation(
            id, id, InstallationKind.FORT, province, province * 10, province * 10, 100L);
    owner.getInstallationHandler().acceptTransferred(fort);
    return fort;
  }

  private static Regiment regiment(Faction faction, String id, int slots, boolean offensive) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("item.material", "PAPER");
    yaml.set("default-slots", slots);
    yaml.set("offense", offensive);
    Regiment regiment = new Regiment(id, yaml);
    faction.getMilitary().getRegiments().add(regiment);
    return regiment;
  }

  private static War war(Faction attacker, Faction defender) {
    War war = new War(998011, attacker, defender);
    war.setGoal(WarGoalType.WAR);
    return war;
  }

  private static War roundTrip(War war) {
    Gson gson = new Gson();
    War restored =
        WarMapper.fromData(gson.fromJson(gson.toJson(WarMapper.toData(war)), WarData.class));
    assertNotNull(restored);
    return restored;
  }
}
