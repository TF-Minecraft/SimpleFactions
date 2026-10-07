package net.tfminecraft.simplefactions.installation;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleSiegeFortService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.zoc.FortControlService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FortTransferReferencesCoverageTest {
  @Test
  void collisionRenameRebindsBothLegsOfEveryActiveWarWithoutChangingTheCampaignPosition()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      Faction occupier = rig.domain.saved("fort_occupier", "Cedar");
      Installation port = installation(occupier, "shared", InstallationKind.PORT, 30);
      Installation fort = installation(rig.defender, "shared", InstallationKind.FORT, 20);
      Installation otherFort = installation(rig.attacker, "shared", InstallationKind.FORT, 11);
      War otherWar = activeWar(998801, occupier, rig.defender);
      War endedWar = activeWar(998802, occupier, rig.attacker);
      endedWar.end(WarEndReason.ADMIN_END);
      rig.war.setFortControllers(Map.of(fort.getStableKey(), CampaignCoalition.DEFENDER));
      otherWar.setFortControllers(Map.of(fort.getStableKey(), CampaignCoalition.DEFENDER));
      var field = new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, true, null);
      var siege =
          new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, true, "shared", null, 12);
      var otherProvince =
          new ScheduledCampaignBattle(11, CampaignBattleKind.SIEGE, false, "shared");
      var otherId =
          new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, "retired_fort");
      var naval =
          new ScheduledCampaignBattle(31, CampaignBattleKind.NAVAL, false, null, "shared", 12);
      List<ScheduledCampaignBattle> invasion = List.of(field, siege, otherProvince, otherId, naval);
      List<ScheduledCampaignBattle> counter = List.of(naval, otherProvince, siege, field);
      for (War war : List.of(rig.war, otherWar, endedWar)) {
        war.setCampaignBattleSchedule(invasion);
        war.setCampaignCounterSchedule(counter);
        war.setCampaignScheduleIndex(1);
        war.setCampaignCounterScheduleIndex(2);
        war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
      }
      assertSame(rig.defender, BattleSiegeFortService.currentSiegeFortOwner(rig.war).orElseThrow());

      rig.writes.clear();
      InstallationTransferService.transfer(rig.defender, occupier, 20);

      Installation moved =
          occupier.getInstallationHandler().getByProvince(InstallationKind.FORT, 20);
      assertNotNull(moved);
      assertNotEquals(fort.getId(), moved.getId());
      assertEquals(fort.getStableKey(), moved.getStableKey());
      assertSame(port, occupier.getInstallationHandler().getById("shared"));
      assertSame(otherFort, rig.attacker.getInstallationHandler().getById("shared"));
      var rebound =
          new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, true, moved.getId(), null, 12);
      assertEquals(
          List.of(rig.war.getId(), otherWar.getId()),
          savedWarIds(rig),
          "Only the two changed active wars are saved, once each; the ended war is untouched");
      for (War war : List.of(rig.war, otherWar)) {
        War restored = savedWar(rig, war);
        assertAll(
            () ->
                assertEquals(
                    List.of(field, rebound, otherProvince, otherId, naval),
                    war.getCampaignBattleSchedule()),
            () ->
                assertEquals(
                    List.of(naval, otherProvince, rebound, field),
                    war.getCampaignCounterSchedule()),
            () -> assertEquals(1, war.getCampaignScheduleIndex()),
            () -> assertEquals(2, war.getCampaignCounterScheduleIndex()),
            () ->
                assertSame(
                    occupier, BattleSiegeFortService.currentSiegeFortOwner(war).orElse(null)),
            () ->
                assertEquals(
                    CampaignCoalition.DEFENDER,
                    FortControlService.controllerForInstallation(war, moved).orElseThrow()),
            () ->
                assertEquals(war.getCampaignBattleSchedule(), restored.getCampaignBattleSchedule()),
            () ->
                assertEquals(
                    war.getCampaignCounterSchedule(), restored.getCampaignCounterSchedule()),
            () -> assertEquals(1, restored.getCampaignScheduleIndex()),
            () -> assertEquals(2, restored.getCampaignCounterScheduleIndex()),
            () -> assertEquals(war.getFortControllers(), restored.getFortControllers()));
      }
      assertEquals(invasion, endedWar.getCampaignBattleSchedule());
      assertEquals(counter, endedWar.getCampaignCounterSchedule());
    }
  }

  @Test
  void occupationPreservesTheMigratedControllerOfAnotherActiveWarWhenRenamingItsFort()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      Installation port = installation(rig.attacker, "shared", InstallationKind.PORT, 10);
      Installation fort = installation(rig.defender, "shared", InstallationKind.FORT, 20);
      Faction otherAttacker = rig.domain.saved("other_siege_realm", "Cedar");
      War otherWar = activeWar(998803, otherAttacker, rig.defender);
      rig.war.setFortControllers(Map.of(fort.getStableKey(), CampaignCoalition.AGGRESSOR));
      otherWar.setFortControllers(Map.of("shared", CampaignCoalition.AGGRESSOR));
      WarInstallationMigration.migrate(otherWar);
      assertEquals(
          CampaignCoalition.AGGRESSOR,
          FortControlService.controllerForInstallation(otherWar, fort).orElseThrow());

      rig.writes.clear();
      WartimeInstallationService.occupy(rig.war, rig.attacker, 20);

      Installation moved =
          rig.attacker.getInstallationHandler().getByProvince(InstallationKind.FORT, 20);
      assertNotNull(moved);
      assertNotEquals(fort.getId(), moved.getId());
      assertEquals(fort.getStableKey(), moved.getStableKey());
      assertSame(port, rig.attacker.getInstallationHandler().getById("shared"));
      assertTrue(savedWarIds(rig).isEmpty(), "An already migrated physical controller needs no rewrite on transfer");
      War restored = roundTrip(otherWar);
      assertAll(
          () ->
              assertEquals(
                  CampaignCoalition.AGGRESSOR,
                  FortControlService.controllerForInstallation(rig.war, moved).orElse(null)),
          () ->
              assertEquals(
                  CampaignCoalition.AGGRESSOR,
                  FortControlService.controllerForInstallation(otherWar, moved).orElse(null),
                  "The transferring war must not orphan another active war's captured controller"),
          () ->
              assertEquals(
                  CampaignCoalition.AGGRESSOR,
                  FortControlService.controllerForInstallation(restored, moved).orElse(null)),
          () ->
              assertEquals(
                  CampaignCoalition.AGGRESSOR,
                  otherWar.getFortControllers().get(moved.getStableKey())));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aRenameCannotAssignAnAmbiguousLegacyCaptureOrReplaceAnExistingPhysicalController(
      boolean existingPhysicalController) throws Exception {
    try (Fixture rig = new Fixture()) {
      Faction occupier = rig.domain.saved("ambiguous_fort_occupier", "Cedar");
      installation(occupier, "shared", InstallationKind.PORT, 30);
      Installation fort = installation(rig.defender, "shared", InstallationKind.FORT, 20);
      Installation otherFort = installation(rig.attacker, "shared", InstallationKind.FORT, 11);
      Map<String, CampaignCoalition> controllers = new HashMap<>();
      controllers.put("shared", CampaignCoalition.AGGRESSOR);
      controllers.put(otherFort.getStableKey(), CampaignCoalition.DEFENDER);
      if (existingPhysicalController)
        controllers.put(fort.getStableKey(), CampaignCoalition.DEFENDER);
      rig.war.setFortControllers(controllers);
      CampaignCoalition expected = existingPhysicalController ? CampaignCoalition.DEFENDER : null;
      assertEquals(
          expected, FortControlService.controllerForInstallation(rig.war, fort).orElse(null));

      rig.writes.clear();
      InstallationTransferService.transfer(rig.defender, occupier, 20);

      Installation moved =
          occupier.getInstallationHandler().getByProvince(InstallationKind.FORT, 20);
      assertNotNull(moved);
      assertNotEquals("shared", moved.getId());
      assertEquals(fort.getStableKey(), moved.getStableKey());
      assertAll(
          () ->
              assertEquals(
                  expected,
                  FortControlService.controllerForInstallation(rig.war, moved).orElse(null)),
          () -> assertEquals(expected, rig.war.getFortControllers().get(moved.getStableKey())),
          () ->
              assertEquals(
                  CampaignCoalition.DEFENDER,
                  FortControlService.controllerForInstallation(rig.war, otherFort).orElseThrow()),
          () -> assertEquals(controllers, rig.war.getFortControllers()),
          () -> assertTrue(savedWarIds(rig).isEmpty(), "Unchanged war state is not saved"),
          () ->
              assertEquals(rig.war.getFortControllers(), roundTrip(rig.war).getFortControllers()));
    }
  }

  private static Installation installation(
      Faction owner, String id, InstallationKind kind, int province) {
    Installation installation =
        new Installation(id, id, kind, province, province * 10, province * 10, 100L);
    owner.getInstallationHandler().acceptTransferred(installation);
    return installation;
  }

  private static War activeWar(int id, Faction attacker, Faction defender) {
    War war = new War(id, attacker, defender);
    war.setGoal(WarGoalType.WAR);
    WarManager.addWar(war);
    return war;
  }

  private static List<Integer> savedWarIds(Fixture rig) {
    return rig.writes.stream()
        .map(write -> write.value())
        .filter(WarData.class::isInstance)
        .map(WarData.class::cast)
        .map(data -> data.id)
        .sorted()
        .toList();
  }

  private static War savedWar(Fixture rig, War war) {
    var saved =
        rig.writes.stream()
            .filter(write -> write.value() instanceof WarData data && data.id == war.getId())
            .toList();
    assertEquals(1, saved.size(), "Each changed war is saved once after transfer completes");
    assertEquals("war_" + war.getId() + ".json", saved.getFirst().file().getName());
    return readData((WarData) saved.getFirst().value());
  }

  private static War roundTrip(War war) {
    return readData(WarMapper.toData(war));
  }

  private static War readData(WarData data) {
    Gson gson = new Gson();
    War restored = WarMapper.fromData(gson.fromJson(gson.toJson(data), WarData.class));
    assertNotNull(restored);
    return restored;
  }
}
