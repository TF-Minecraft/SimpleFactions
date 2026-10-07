package net.tfminecraft.simplefactions.war.campaign.progression;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleOutcomeService;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignMilitaryWalkoverService;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushProjection;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.enums.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;

class CampaignSafetyRegressionTest {
  private Fixture rig;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    var registryField =
        net.tfminecraft.simplefactions.SimpleFactions.class.getDeclaredField("vehicleRegistry");
    registryField.setAccessible(true);
    registryField.set(rig.domain.ui.plugin, new PlayerVehicleRegistry());
    rig.remember(Cache.class, "warFirstBattleAtBorder");
    rig.remember(Cache.class, "warProvincesBetweenBattles");
    Cache.warFirstBattleAtBorder = true;
    Cache.warProvincesBetweenBattles = 1;
    rig.war.setGoal(WarGoalType.WAR);
    rig.war.setWarType(WarType.WAR);
    rig.war.setCampaignProvinces(List.of(10, 11, 12, 20));
    rig.war.setObjectiveProvinceId(20);
    rig.war.setCursorIndex(0);
    rig.war.setInitiativeAttacker(4);
    rig.war.setInitiativeDefender(4);
    rig.war.setInitiativeHolder(BelligerentRole.ATTACKER);
    rig.war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
  }

  @AfterEach
  void close() throws Exception {
    if (rig != null) rig.close();
  }

  private void army(Faction faction, int slots) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("item.material", "PAPER");
    yaml.set("offense", true);
    Regiment regiment = new Regiment("campaign_guard", yaml);
    faction.getMilitary().getRegiments().add(regiment);
    assertTrue(faction.getMilitary().adminAdjustSlots(regiment.getId(), slots).allowed());
  }

  @Test
  void aMissingObjectiveCannotBeTreatedAsAnAlreadyReachableTarget() {
    rig.war.setObjectiveProvinceId(99);
    assertFalse(CampaignCapabilityService.canReachTarget(rig.war, CampaignCoalition.AGGRESSOR));
    WhitePeaceService.recalculateProposals(rig.war);
    assertTrue(rig.war.isWhitePeaceProposedByAttacker());
  }

  @Test
  void aBattleWithoutAWinnerReopensVotingWithoutAwardingEitherSideAChoice() {
    army(rig.attacker, 2);
    army(rig.defender, 2);
    rig.war.setCursorIndex(1);
    rig.war.setScheduledBattleProvinceId(11);
    var battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_draw");
    battle.setWarId(rig.war.getId());
    battle.setProvinceId(11);
    BattleManager.addBattle(battle);

    new CampaignBattleOutcomeService()
        .onBattleEnded(
            new BattleEndedEvent(
                battle.getId(), BattleType.FIELD, rig.war.getId(), null, Map.of(), Set.of()));

    assertTrue(rig.war.isActive());
    assertFalse(CampaignPostBattleChoiceService.needsAnyChoice(rig.war));
    assertNull(rig.war.getPostBattleWinnerCoalition());
    assertEquals(0, rig.war.getCampaignBattlesFought());
    assertEquals(1, rig.war.getCursorIndex());
    assertEquals(4, rig.war.getInitiativeAttacker());
    assertEquals(BattleSchedulePhase.VOTING, rig.war.getBattleSchedulePhase());
  }

  @Test
  void uncontestedScheduledBattlesAdvanceEachSlotOnce() {
    army(rig.attacker, 2);
    rig.war.setCampaignBattleSchedule(
        List.of(
            new ScheduledCampaignBattle(11, CampaignBattleKind.FIELD, false, null),
            new ScheduledCampaignBattle(12, CampaignBattleKind.FIELD, false, null),
            new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, true, null)));

    CampaignMilitaryWalkoverService.resolvePendingWalkovers(rig.war);

    assertEquals(3, rig.war.getCampaignScheduleIndex());
    assertEquals(3, rig.war.getCampaignBattlesFought());
    assertEquals(1, rig.war.getInitiativeAttacker());
    assertEquals(CampaignCoalition.AGGRESSOR, rig.war.getInitiativeHolderCoalition());
  }

  @Test
  void aCounterPushPreviewUsesTheWinningSidesNextLegWithoutMutatingTheLiveSchedule() {
    army(rig.defender, 2);
    rig.defender.getInstallationHandler().load(List.of());
    rig.war.setCursorIndex(2);
    rig.war.setLastBattleOffensiveCoalition(CampaignCoalition.AGGRESSOR);
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL, false, null)));
    rig.war.setCampaignCounterSchedule(
        List.of(new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, true, null)));
    List<ScheduledCampaignBattle> invasion = List.copyOf(rig.war.getCampaignBattleSchedule());
    List<ScheduledCampaignBattle> counter = List.copyOf(rig.war.getCampaignCounterSchedule());

    assertTrue(
        CampaignPushProjection.canMountOffensiveAfterPush(rig.war, CampaignCoalition.DEFENDER));

    assertEquals(invasion, rig.war.getCampaignBattleSchedule());
    assertEquals(counter, rig.war.getCampaignCounterSchedule());
    assertEquals(CampaignPushTarget.TOWARD_OBJECTIVE, rig.war.getPushTarget());
    assertEquals(CampaignCoalition.AGGRESSOR, rig.war.getInitiativeHolderCoalition());
    assertEquals(2, rig.war.getCursorIndex());
  }
}
