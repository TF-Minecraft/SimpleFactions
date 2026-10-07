package net.tfminecraft.simplefactions.installation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.zoc.PortSeaZocIndex;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.junit.jupiter.api.Test;

class PortTransferReferencesCoverageTest {
  @Test
  void collisionRenamingRebindsThePhysicalPortOnBothLegsAndKeepsOtherSeaReferences()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      configureCoast(rig);
      String originalId = rig.target.getId();
      Faction receiver = rig.domain.saved("port_receiver", "Cara");
      Faction otherOwner = rig.domain.saved("other_port_owner", "Dana");
      Installation collision = installation(receiver, originalId, InstallationKind.FORT, 30);
      Installation otherPort = installation(otherOwner, originalId, InstallationKind.PORT, 40);
      rig.war.getDefenders().getMainParticipants().getFirst().getAllies().put(receiver, true);
      rig.war.getDefenders().getMainParticipants().getFirst().getAllies().put(otherOwner, true);
      War otherWar = new War(990320, rig.attacker, rig.defender);
      otherWar.setGoal(WarGoalType.WAR);
      WarManager.addWar(otherWar);
      War ended = new War(990321, rig.attacker, rig.defender);
      ended.setGoal(WarGoalType.WAR);
      ended.end(WarEndReason.ADMIN_END);
      WarManager.addWar(ended);
      var field = new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, true, null);
      var naval =
          new ScheduledCampaignBattle(21, CampaignBattleKind.NAVAL, false, null, originalId, 22);
      var otherSea =
          new ScheduledCampaignBattle(41, CampaignBattleKind.NAVAL, true, null, originalId);
      var disappeared =
          new ScheduledCampaignBattle(61, CampaignBattleKind.NAVAL, false, null, "missing_port");
      List<ScheduledCampaignBattle> invasion = List.of(field, naval, otherSea, disappeared);
      List<ScheduledCampaignBattle> counter = List.of(disappeared, otherSea, naval, field);
      for (War war : List.of(rig.war, otherWar, ended)) {
        war.setCampaignBattleSchedule(invasion);
        war.setCampaignCounterSchedule(counter);
        war.setCampaignScheduleIndex(1);
        war.setCampaignCounterScheduleIndex(2);
        war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
      }
      assertSame(
          rig.defender,
          PortSeaZocIndex.fromGameState().portForSeaProvince(21).orElseThrow().owner());
      assertSame(
          otherOwner, PortSeaZocIndex.fromGameState().portForSeaProvince(41).orElseThrow().owner());
      assertTrue(
          BattleInstallationPickService.isDefenderZocPort(rig.war, rig.defender, originalId));
      rig.writes.clear();

      InstallationTransferService.transfer(rig.defender, receiver, 20);

      Installation moved =
          receiver.getInstallationHandler().getByProvince(InstallationKind.PORT, 20);
      assertNotNull(moved);
      assertNotEquals(originalId, moved.getId());
      assertEquals(rig.target.getStableKey(), moved.getStableKey());
      assertSame(collision, receiver.getInstallationHandler().getById(originalId));
      assertSame(otherPort, otherOwner.getInstallationHandler().getById(originalId));
      var rebound =
          new ScheduledCampaignBattle(21, CampaignBattleKind.NAVAL, false, null, moved.getId(), 22);
      for (War war : List.of(rig.war, otherWar)) {
        assertAll(
            () ->
                assertEquals(
                    List.of(field, rebound, otherSea, disappeared),
                    war.getCampaignBattleSchedule()),
            () ->
                assertEquals(
                    List.of(disappeared, otherSea, rebound, field),
                    war.getCampaignCounterSchedule()),
            () -> assertEquals(1, war.getCampaignScheduleIndex()),
            () -> assertEquals(2, war.getCampaignCounterScheduleIndex()));
      }
      assertEquals(List.of(rig.war.getId(), otherWar.getId()), savedWarIds(rig));
      for (War war : List.of(rig.war, otherWar)) {
        War saved = savedWar(rig, war);
        assertEquals(war.getCampaignBattleSchedule(), saved.getCampaignBattleSchedule());
        assertEquals(war.getCampaignCounterSchedule(), saved.getCampaignCounterSchedule());
      }
      assertEquals(invasion, ended.getCampaignBattleSchedule());
      assertEquals(counter, ended.getCampaignCounterSchedule());
      BattleInstallationPickService.ensureDefenderZocPort(rig.war);
      assertAll(
          () ->
              assertEquals(moved.getId(), BattleInstallationPickService.defenderZocPortId(rig.war)),
          () ->
              assertTrue(
                  BattleInstallationPickService.isDefenderZocPort(
                      rig.war, receiver, moved.getId())),
          () ->
              assertFalse(
                  BattleInstallationPickService.isDefenderZocPort(rig.war, otherOwner, originalId)),
          () ->
              assertTrue(
                  BattleInstallationPickService.getPicks(rig.war, receiver.getId())
                      .contains(moved.getId())),
          () ->
              assertTrue(
                  BattleInstallationPickService.getPicks(rig.war, otherOwner.getId()).isEmpty()));
    }
  }

  @Test
  void transferringWithoutALocalIdCollisionPreservesTheScheduleAndStillSelectsTheNewHolder()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      configureCoast(rig);
      Faction receiver = rig.domain.saved("port_ally", "Cara");
      rig.war.getDefenders().getMainParticipants().getFirst().getAllies().put(receiver, true);
      var naval =
          new ScheduledCampaignBattle(21, CampaignBattleKind.NAVAL, true, null, rig.target.getId());
      rig.war.setCampaignBattleSchedule(List.of(naval));
      rig.war.setCampaignCounterSchedule(List.of(naval));
      rig.war.setCampaignScheduleIndex(0);
      rig.war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
      rig.writes.clear();

      InstallationTransferService.transfer(rig.defender, receiver, 20);

      assertSame(rig.target, receiver.getInstallationHandler().getById(rig.target.getId()));
      assertEquals(List.of(naval), rig.war.getCampaignBattleSchedule());
      assertEquals(List.of(naval), rig.war.getCampaignCounterSchedule());
      assertTrue(savedWarIds(rig).isEmpty());
      BattleInstallationPickService.ensureDefenderZocPort(rig.war);
      assertTrue(
          BattleInstallationPickService.getPicks(rig.war, receiver.getId())
              .contains(rig.target.getId()));
      assertTrue(BattleInstallationPickService.getPicks(rig.war, rig.defender.getId()).isEmpty());
    }
  }

  private static void configureCoast(Fixture rig) throws Exception {
    rig.remember(Cache.class, "warPortSeaZocRadius");
    Cache.warPortSeaZocRadius = 1;
    coast(rig, 20, 21);
    coast(rig, 40, 41);
  }

  private static void coast(Fixture rig, int landId, int seaId) {
    Province land = new Province(landId, "PLAINS", 0);
    Province sea = new Province(seaId, "SEA", 0);
    land.addNeighbour(seaId);
    sea.addNeighbour(landId);
    rig.domain.provinceData.put(landId, land);
    rig.domain.provinceData.put(seaId, sea);
  }

  private static Installation installation(
      Faction owner, String id, InstallationKind kind, int province) {
    Installation installation =
        new Installation(id, id, kind, province, province * 10, province * 10, 100L);
    owner.getInstallationHandler().acceptTransferred(installation);
    return installation;
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
    var writes =
        rig.writes.stream()
            .filter(write -> write.value() instanceof WarData data && data.id == war.getId())
            .toList();
    assertEquals(1, writes.size(), "Each affected active war must be saved once");
    War saved = WarMapper.fromData((WarData) writes.getFirst().value());
    assertNotNull(saved);
    return saved;
  }
}
