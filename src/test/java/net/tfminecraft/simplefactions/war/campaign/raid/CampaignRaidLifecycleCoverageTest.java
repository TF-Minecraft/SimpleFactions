package net.tfminecraft.simplefactions.war.campaign.raid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.CampaignRaidData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.events.PlayerProvinceEnterEvent;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.BattleTemplateLoader;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.CampaignRaidLaunchHolder;
import net.tfminecraft.simplefactions.managers.inventory.CampaignCreator;
import net.tfminecraft.simplefactions.managers.inventory.CampaignRaidLaunchView;
import net.tfminecraft.simplefactions.managers.inventory.CampaignView;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.presence.ProvincePresenceService;
import net.tfminecraft.simplefactions.map.presence.RegionPresenceService;
import net.tfminecraft.simplefactions.map.presence.TitlePresenceService;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleLaunchService;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandBattleService;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandLeaveBlock;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService;
import net.tfminecraft.simplefactions.war.battle.engine.core.*;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidAttackerEliminationService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.*;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.*;
import net.tfminecraft.simplefactions.war.campaign.raid.intruder.CampaignRaidIntruderService;
import net.tfminecraft.simplefactions.war.campaign.runtime.*;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarDevMode;
import net.tfminecraft.simplefactions.war.enums.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

public class CampaignRaidLifecycleCoverageTest {
  private Fixture rig;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
  }

  @AfterEach
  void cleanup() throws Exception {
    if (rig != null) rig.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void everyEligibleInstallationRemainsAccessibleWhenRaidListsExceedOnePage(boolean sources) {
    Faction owner = sources ? rig.attacker : rig.defender;
    for (int i = 0; i < 34; i++) rig.install(owner, "extra_" + i, InstallationKind.PORT, 100 + i);
    if (sources) rig.raidView.openSourcePage(rig.alice, rig.war, rig.attacker);
    else rig.raidView.openTargetPage(rig.alice, rig.war, rig.attacker, rig.source.getId());
    Set<String> reached = new LinkedHashSet<>();
    for (int page = 0; page < 10; page++) {
      Inventory menu = rig.top(rig.alice);
      for (ItemStack item : menu.getContents()) {
        if (item == null || item.isEmpty()) continue;
        String id =
            item.getItemMeta()
                .getPersistentDataContainer()
                .get(CampaignCreator.raidLaunchInstallationIdKey(), PersistentDataType.STRING);
        if (id != null) reached.add(id);
      }
      int next = rig.slotNamed(menu, "Next");
      if (next < 0) break;
      rig.raidView.click(rig.domain.ui.click(rig.alice, next), menu, rig.alice);
    }
    assertEquals(
        35, reached.size(), "Every owned or enemy installation must have an accessible selection");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void disappearingSourceBeforeFightDoesNotConsumeQuotaOrCreateARepairLock(boolean transferred) {
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    CampaignRaid raid = CampaignRaidService.getActive(rig.war);
    assertEquals(
        List.of(rig.source),
        rig.attacker.getInstallationHandler().detachOnProvince(rig.source.getProvince()));
    Faction newOwner = transferred ? rig.domain.saved("new_source_owner", "NewOwner") : null;
    if (newOwner != null) newOwner.getInstallationHandler().acceptTransferred(rig.source);

    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());

    assertFalse(
        CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR),
        "A fight which cannot be created must not consume the coalition's daily raid");
    assertFalse(rig.war.getRaidRepairLockUntil().containsKey(rig.target.getId()));
    assertTrue(BattleManager.get().isEmpty());
    assertTrue(WarbandManager.get().isEmpty());
    assertNull(CampaignRaidService.getActive(rig.war));
    assertNotEquals(CampaignRaidState.FIGHTING, raid.getState());
    assertSame(rig.target, rig.defender.getInstallationHandler().getById(rig.target.getId()));
    if (newOwner != null)
      assertSame(rig.source, newOwner.getInstallationHandler().getById(rig.source.getId()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing_template", "wrong_template", "missing_world", "missing_target"})
  void unavailableFightDependenciesAbortTheMusterWithoutQuotaOrPartialBattle(String reason) {
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    CampaignRaid raid = CampaignRaidService.getActive(rig.war);
    if (reason.equals("missing_template")) Cache.battleCampaignTemplateRaid = "unknown_template";
    if (reason.equals("wrong_template")) {
      YamlConfiguration wrong = new YamlConfiguration();
      wrong.set("type", "field");
      BattleTemplateLoader.putForTests(new BattleTemplate("wrong_template", wrong));
      Cache.battleCampaignTemplateRaid = "wrong_template";
    }
    if (reason.equals("missing_world")) when(Bukkit.getWorld("world")).thenReturn(null);
    if (reason.equals("missing_target")) rig.defender.getInstallationHandler().detachOnProvince(20);
    assertDoesNotThrow(() -> CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt()));
    assertNull(CampaignRaidService.getActive(rig.war));
    assertTrue(BattleManager.get().isEmpty());
    assertTrue(WarbandManager.get().isEmpty());
    assertFalse(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR));
    assertTrue(rig.war.getRaidRepairLockUntil().isEmpty());
  }

  @Test
  void sourceAndTargetClicksLaunchOneRealMusterAndPersistItsRoster() {
    rig.raidView.openSourcePage(rig.alice, rig.war, rig.attacker);
    Inventory sourceMenu = rig.top(rig.alice);
    assertTrue(((CampaignRaidLaunchHolder) sourceMenu.getHolder()).isSourcePage());
    assertEquals(rig.source.getId(), raidId(sourceMenu.getItem(12)));
    assertEquals(Material.WRITABLE_BOOK, sourceMenu.getItem(10).getType());
    click(12);
    Inventory targetMenu = rig.top(rig.alice);
    assertEquals(
        rig.source.getId(),
        ((CampaignRaidLaunchHolder) targetMenu.getHolder()).getSourceInstallationId());
    assertEquals(rig.target.getId(), raidId(targetMenu.getItem(12)));
    long before = rig.warWrites();
    click(12);
    CampaignRaid raid = CampaignRaidService.getActive(rig.war);
    assertNotNull(raid);
    assertEquals(CampaignRaidState.MUSTER, raid.getState());
    assertEquals(rig.source.getId(), raid.getSourceInstallationId());
    assertEquals(rig.target.getId(), raid.getTargetInstallationId());
    assertEquals(rig.attacker.getId(), raid.getLauncherFactionId());
    assertEquals(1, WarbandManager.get().size());
    assertTrue(CampaignRaidWarbandService.getAttackerWarband(raid).isPendingLeader());
    assertFalse(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR));
    assertTrue(rig.warWrites() > before);
    verify(rig.alice).sendMessage("§aCampaign raid muster started.");
    verify(rig.navigation.campaignView).campaignView(rig.alice, rig.war, true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void refreshReusesTheExistingInventoryAndClearsRemovedSelections(boolean sourcePage) {
    if (sourcePage) rig.raidView.openSourcePage(rig.alice, rig.war, rig.attacker);
    else rig.raidView.openTargetPage(rig.alice, rig.war, rig.attacker, rig.source.getId());
    Inventory menu = rig.top(rig.alice);
    assertNotNull(raidId(menu.getItem(12)));
    (sourcePage ? rig.attacker : rig.defender)
        .getInstallationHandler()
        .detachOnProvince(sourcePage ? 10 : 20);
    clearInvocations(rig.alice);
    if (sourcePage) rig.raidView.openSourcePage(rig.alice, rig.war, rig.attacker, false, menu);
    else
      rig.raidView.openTargetPage(
          rig.alice, rig.war, rig.attacker, rig.source.getId(), false, menu);
    assertTrue(menu.getItem(12).isEmpty());
    assertSame(menu, rig.top(rig.alice));
    verify(rig.alice, never()).openInventory(any(Inventory.class));
    if (sourcePage) rig.raidView.openSourcePage(rig.alice, rig.war, rig.attacker, false);
    else rig.raidView.openTargetPage(rig.alice, rig.war, rig.attacker, rig.source.getId(), false);
    verify(rig.alice, never()).openInventory(any(Inventory.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "ended", "no_faction", "outsider"})
  void openingRaidPagesRejectsMissingWarsAndNonParticipants(String reason) {
    War viewed = rig.war;
    Faction faction = rig.attacker;
    if (reason.equals("missing")) viewed = null;
    if (reason.equals("ended")) rig.war.end(WarEndReason.ADMIN_END);
    if (reason.equals("no_faction")) faction = null;
    if (reason.equals("outsider")) faction = rig.domain.saved("lifecycle_outside", "Visitor");
    rig.raidView.openSourcePage(rig.alice, viewed, faction);
    rig.raidView.openTargetPage(rig.alice, viewed, faction, rig.source.getId());
    verify(rig.alice, times(2))
        .sendMessage(
            reason.equals("missing") || reason.equals("ended")
                ? CampaignRaidMessages.WAR_INACTIVE
                : CampaignRaidMessages.NOT_PARTICIPANT);
    verify(rig.alice, never()).openInventory(any(Inventory.class));
    assertNull(CampaignRaidService.getActive(rig.war));
  }

  @Test
  void backNavigationReturnsToSourceThenCampaignWithoutRequiringLeadership() {
    rig.attacker.addMember("Observer");
    Player observer = rig.player("Observer");
    rig.raidView.openTargetPage(observer, rig.war, rig.attacker, rig.source.getId());
    Inventory target = rig.top(observer);
    rig.raidView.click(rig.domain.ui.click(observer, 53), target, observer);
    assertTrue(((CampaignRaidLaunchHolder) rig.top(observer).getHolder()).isSourcePage());
    rig.raidView.click(rig.domain.ui.click(observer, 53), rig.top(observer), observer);
    verify(rig.navigation.campaignView).campaignView(observer, rig.war, true);
    assertNull(CampaignRaidService.getActive(rig.war));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missing_war",
        "ended",
        "outsider",
        "member",
        "no_item",
        "missing_id",
        "foreign_war"
      })
  void deniedSourceClicksPreserveRaidQuotaAndRoster(String reason) {
    rig.raidView.openSourcePage(rig.alice, rig.war, rig.attacker);
    Inventory menu = rig.top(rig.alice);
    if (reason.equals("missing_war")) WarManager.get().clear();
    if (reason.equals("ended")) rig.war.end(WarEndReason.ADMIN_END);
    if (reason.equals("outsider")) {
      rig.attacker.setLeader("Other");
      rig.attacker.getOrCreateMainGuild().getMembers().remove("Alice");
    }
    if (reason.equals("member")) {
      rig.attacker.addMember("Alice");
      rig.attacker.setLeader("Other");
    }
    if (reason.equals("no_item")) menu.setItem(12, null);
    if (reason.equals("missing_id") || reason.equals("foreign_war")) {
      ItemStack item = menu.getItem(12);
      var meta = item.getItemMeta();
      if (reason.equals("missing_id"))
        meta.getPersistentDataContainer().remove(CampaignCreator.raidLaunchInstallationIdKey());
      else
        meta.getPersistentDataContainer()
            .set(CampaignCreator.raidLaunchWarKey(), PersistentDataType.INTEGER, -4);
      item.setItemMeta(meta);
    }
    long before = rig.warWrites();
    InventoryClickEvent event = rig.domain.ui.click(rig.alice, 12);
    rig.raidView.click(event, menu, rig.alice);
    assertTrue(event.isCancelled());
    assertNull(rig.war.getActiveCampaignRaid());
    assertTrue(rig.war.getCampaignRaidsUsed().isEmpty());
    assertTrue(WarbandManager.get().isEmpty());
    assertEquals(before, rig.warWrites());
    assertSame(menu, rig.top(rig.alice));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "source_removed",
        "target_removed",
        "same_coalition",
        "outside_window",
        "quota",
        "in_progress"
      })
  void targetClicksRevalidateLiveInstallationsAndLaunchGates(String reason) {
    rig.raidView.openTargetPage(rig.alice, rig.war, rig.attacker, rig.source.getId());
    if (reason.equals("source_removed")) rig.attacker.getInstallationHandler().detachOnProvince(10);
    if (reason.equals("target_removed")) rig.defender.getInstallationHandler().detachOnProvince(20);
    if (reason.equals("same_coalition")) {
      rig.defender.getInstallationHandler().detachOnProvince(20);
      rig.attacker.getInstallationHandler().acceptTransferred(rig.target);
    }
    if (reason.equals("outside_window"))
      rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 18));
    if (reason.equals("quota"))
      rig.war
          .getCampaignRaidsUsed()
          .put(CampaignCoalition.AGGRESSOR.toJson(), Fixture.DAY.toString());
    if (reason.equals("in_progress"))
      assertEquals(
          CampaignRaidResults.LaunchResult.STARTED,
          CampaignRaidService.beginMuster(
              rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    CampaignRaid previous = rig.war.getActiveCampaignRaid();
    List<Warband> bands = new ArrayList<>(WarbandManager.get());
    long before = rig.warWrites();
    click(12);
    assertSame(previous, rig.war.getActiveCampaignRaid());
    assertEquals(bands, WarbandManager.get());
    assertEquals(before, rig.warWrites());
    verify(rig.alice, never()).sendMessage("§aCampaign raid muster started.");
  }

  @Test
  void sourceClickOutsideTheWindowExplainsTheRejectionAndIgnoresUnrelatedInventories() {
    rig.raidView.openSourcePage(rig.alice, rig.war, rig.attacker);
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 18));
    click(12);
    verify(rig.alice)
        .sendMessage(
            CampaignRaidMessages.messageForLaunchResult(
                CampaignRaidResults.LaunchResult.REJECTED_OUTSIDE_WINDOW));
    Inventory unrelated = rig.domain.ui.inventory(null, 9, "Unrelated");
    InventoryClickEvent event = rig.domain.ui.click(rig.alice, 12);
    rig.raidView.click(event, unrelated, rig.alice);
    assertFalse(event.isCancelled());
  }

  private String raidId(ItemStack item) {
    return item == null || item.isEmpty()
        ? null
        : item.getItemMeta()
            .getPersistentDataContainer()
            .get(CampaignCreator.raidLaunchInstallationIdKey(), PersistentDataType.STRING);
  }

  private void click(int slot) {
    rig.raidView.click(rig.domain.ui.click(rig.alice, slot), rig.top(rig.alice), rig.alice);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void raidPagingMovesBackAndClampsAfterInstallationsDisappear(boolean sourcePage) {
    Faction owner = sourcePage ? rig.attacker : rig.defender;
    for (int i = 0; i < 34; i++) rig.install(owner, "page_" + i, InstallationKind.PORT, 100 + i);
    if (sourcePage) rig.raidView.openSourcePage(rig.alice, rig.war, rig.attacker);
    else rig.raidView.openTargetPage(rig.alice, rig.war, rig.attacker, rig.source.getId());
    click(52);
    Inventory menu = rig.top(rig.alice);
    assertEquals(1, ((CampaignRaidLaunchHolder) menu.getHolder()).getPage());
    assertTrue(rig.slotNamed(menu, "Previous") >= 0);
    click(45);
    assertEquals(0, ((CampaignRaidLaunchHolder) rig.top(rig.alice).getHolder()).getPage());
    click(52);
    for (int i = 0; i < 34; i++) owner.getInstallationHandler().detachOnProvince(100 + i);
    menu = rig.top(rig.alice);
    if (sourcePage) rig.raidView.openSourcePage(rig.alice, rig.war, rig.attacker, false, menu);
    else
      rig.raidView.openTargetPage(
          rig.alice, rig.war, rig.attacker, rig.source.getId(), false, menu);
    assertEquals(0, ((CampaignRaidLaunchHolder) menu.getHolder()).getPage());
    assertEquals(sourcePage ? rig.source.getId() : rig.target.getId(), raidId(menu.getItem(12)));
    assertTrue(menu.getItem(13).isEmpty());
    assertEquals(-1, rig.slotNamed(menu, "Next"));
    assertEquals(-1, rig.slotNamed(menu, "Previous"));
  }

  @Test
  void aMusterBecomesOneFightAndTheTimerReleasesItsBandsWithoutChangingOwnership() {
    CampaignRaid raid = muster();
    assertEquals(
        CampaignRaidResults.JoinResult.OK,
        CampaignRaidJoinService.join(
            rig.war,
            rig.alice.getUniqueId(),
            rig.alice.getName(),
            rig.attacker,
            raid.getId(),
            rig.now()));
    Warband attackers = CampaignRaidWarbandService.getAttackerWarband(raid);
    assertTrue(CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(attackers, rig.bob));
    assertFalse(CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(attackers, rig.alice));
    assertFalse(CampaignRaidMusterScheduler.processOverdue(rig.war, rig.now()));
    assertTrue(CampaignRaidMusterScheduler.processOverdue(rig.war, raid.getMusterEndsAt()));
    Battle battle = BattleManager.getByString(raid.getBattleId());
    assertNotNull(battle);
    assertTrue(battle.hasStarted());
    assertTrue(battle.isCampaignRaid());
    assertFalse(battle.hasLootEnabled());
    assertEquals(CampaignRaidState.FIGHTING, raid.getState());
    assertTrue(attackers.hasMember(rig.alice));
    assertTrue(CampaignRaidWarbandService.getDefenderWarband(raid).hasMember(rig.bob));
    assertTrue(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR));
    assertTrue(
        CampaignRaidService.isRepairLocked(rig.war, rig.target.getId(), raid.getMusterEndsAt()));
    List<Warband> bands = new ArrayList<>(WarbandManager.get());
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    assertEquals(bands, WarbandManager.get());
    assertEquals(1, BattleManager.get().size());
    assertFalse(
        CampaignRaidFightScheduler.processOverdue(rig.war, raid.getFightEndsAt().minusSeconds(1)));
    assertTrue(CampaignRaidFightScheduler.processOverdue(rig.war, raid.getFightEndsAt()));
    assertFalse(battle.hasStarted());
    var events = org.mockito.ArgumentCaptor.forClass(org.bukkit.event.Event.class);
    verify(Bukkit.getPluginManager(), atLeastOnce()).callEvent(events.capture());
    var ended =
        events.getAllValues().stream()
            .filter(
                net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent.class::isInstance)
            .map(net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent.class::cast)
            .findFirst()
            .orElseThrow();
    new CampaignRaidBattleEndService().onBattleEnded(ended);
    assertNull(CampaignRaidService.getActive(rig.war));
    assertTrue(BattleManager.get().isEmpty());
    assertTrue(WarbandManager.get().isEmpty());
    assertSame(rig.source, rig.attacker.getInstallationHandler().getById(rig.source.getId()));
    assertSame(rig.target, rig.defender.getInstallationHandler().getById(rig.target.getId()));
  }

  @Test
  void raidBandLeadershipFollowsCurrentMembershipAndMainFactionLeaders() {
    CampaignRaid raid = muster();
    Player recruit = rig.player("Recruit");
    rig.attacker.addMember("Recruit");
    CampaignRaidWarbandService.signupAttacker(
        rig.war, raid, recruit.getUniqueId(), recruit.getName());
    Warband band = CampaignRaidWarbandService.getAttackerWarband(raid);
    assertEquals(recruit.getUniqueId(), band.getLeaderId());
    CampaignRaidWarbandService.signupAttacker(
        rig.war, raid, rig.alice.getUniqueId(), rig.alice.getName());
    CampaignRaidWarbandService.signupAttacker(
        rig.war, raid, rig.alice.getUniqueId(), rig.alice.getName());
    assertEquals(rig.alice.getUniqueId(), band.getLeaderId());
    assertEquals(2, band.getRealMemberCount());
    CampaignRaidWarbandService.promoteLeaderIfNeeded(band);
    assertEquals(rig.alice.getUniqueId(), band.getLeaderId());
    var listener = new CampaignRaidWarbandService.Listener();
    var quit =
        new org.bukkit.event.player.PlayerQuitEvent(
            rig.alice, net.kyori.adventure.text.Component.empty());
    listener.onQuitLow(quit);
    band.removeMember(rig.alice.getUniqueId());
    listener.onQuitMonitor(quit);
    assertEquals(recruit.getUniqueId(), band.getLeaderId());
    listener.onQuitMonitor(quit);
    band.removeMember(recruit.getUniqueId());
    CampaignRaidWarbandService.promoteLeaderIfNeeded(band);
    assertTrue(band.isPendingLeader());
    assertEquals(0, band.getRealMemberCount());
  }

  @Test
  void defenderLoginEnrollsOnlyAnEligibleFreeDefenderDuringTheFight() {
    CampaignRaid raid = muster();
    Player late = rig.player("Late");
    rig.defender.addMember("Late");
    when(late.isOnline()).thenReturn(false);
    Player reserved = rig.player("Reserved");
    rig.defender.addMember("Reserved");
    Warband existing = new Warband("lifecycle_existing", reserved);
    WarbandManager.addWarband(existing);
    var listener = new CampaignRaidWarbandService.Listener();
    listener.onJoin(
        new org.bukkit.event.player.PlayerJoinEvent(
            late, net.kyori.adventure.text.Component.empty()));
    assertNull(CampaignRaidWarbandService.getDefenderWarband(raid));
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    Warband defenders = CampaignRaidWarbandService.getDefenderWarband(raid);
    assertFalse(defenders.hasMember(late));
    assertFalse(defenders.hasMember(reserved));
    when(late.isOnline()).thenReturn(true);
    listener.onJoin(
        new org.bukkit.event.player.PlayerJoinEvent(
            late, net.kyori.adventure.text.Component.empty()));
    assertTrue(defenders.hasMember(late));
    assertSame(existing, WarbandManager.getByPlayer(reserved));
    CampaignRaidWarbandService.tryEnrollDefenderOnLogin(rig.alice);
    CampaignRaidWarbandService.tryEnrollDefenderOnLogin(rig.player("Unrelated"));
    CampaignRaidWarbandService.tryEnrollDefenderOnLogin(late);
    assertEquals(2, defenders.getRealMemberCount());
    assertTrue(defenders.hasMember(rig.bob));
  }

  @Test
  void absentRaidAndWarbandInputsDoNotMutateTheRegistries() {
    CampaignRaidLaunchService.startFight(null, rig.now());
    CampaignRaidLaunchService.startFight(rig.war, null);
    CampaignRaidLaunchService.startFight(rig.war, rig.now());
    assertNull(CampaignRaidWarbandService.attackerWarbandId(null));
    assertNull(CampaignRaidWarbandService.defenderWarbandId(new CampaignRaid()));
    assertNull(CampaignRaidWarbandService.getAttackerWarband(null));
    assertNull(CampaignRaidWarbandService.getDefenderWarband(null));
    assertFalse(CampaignRaidWarbandService.isRaidWarband(null));
    assertFalse(CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(null, rig.alice));
    CampaignRaidWarbandService.createAttackerWarband(null, null);
    CampaignRaidWarbandService.createRaidWarbands(rig.war, null);
    CampaignRaidWarbandService.signupAttacker(rig.war, null, rig.alice.getUniqueId(), "Alice");
    CampaignRaidWarbandService.signupDefender(null, null, null, null);
    CampaignRaidWarbandService.enrollOnlineDefenders(null, null);
    CampaignRaidWarbandService.tryEnrollDefenderOnLogin(null);
    CampaignRaidWarbandService.promoteLeaderIfNeeded(null);
    CampaignRaidWarbandService.destroyRaidWarbands(rig.war, null);
    assertTrue(WarbandManager.get().isEmpty());
    assertTrue(BattleManager.get().isEmpty());
  }

  @Test
  void intruderWarningsArePerRaidAndOnlyApplyToUnregisteredAttackersInTheTargetProvince() {
    Player rogue = rig.player("Rogue");
    rig.attacker.addMember(rogue.getName());
    CampaignRaid raid = muster();
    var listener = new CampaignRaidIntruderService.Listener();
    PlayerProvinceEnterEvent entry =
        new PlayerProvinceEnterEvent(rogue, rig.target.getProvince(), null);
    listener.onProvinceEnter(entry);
    verify(rogue, never()).sendMessage(CampaignRaidMessages.INTRUDER);
    CampaignRaidWarbandService.signupAttacker(rig.war, raid, rig.alice.getUniqueId(), "Alice");
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());

    listener.onProvinceEnter(entry);
    listener.onProvinceEnter(entry);
    verify(rogue, times(1)).sendMessage(CampaignRaidMessages.INTRUDER);
    CampaignRaidIntruderService.clearForRaid(raid);
    listener.onProvinceEnter(entry);
    verify(rogue, times(2)).sendMessage(CampaignRaidMessages.INTRUDER);
    CampaignRaidIntruderService.onProvinceEnter(rogue, rig.source.getProvince());
    CampaignRaidIntruderService.onProvinceEnter(rig.alice, rig.target.getProvince());
    CampaignRaidIntruderService.onProvinceEnter(rig.bob, rig.target.getProvince());
    Player visitor = rig.player("Visitor");
    CampaignRaidIntruderService.onProvinceEnter(visitor, rig.target.getProvince());
    listener.onProvinceEnter(null);
    CampaignRaidIntruderService.onProvinceEnter(null, rig.target.getProvince());
    CampaignRaidIntruderService.clearForRaid(null);
    CampaignRaidIntruderService.clearForRaid(new CampaignRaid());
    verify(rogue, times(2)).sendMessage(CampaignRaidMessages.INTRUDER);
    verify(rig.alice, never()).sendMessage(CampaignRaidMessages.INTRUDER);
    verify(rig.bob, never()).sendMessage(CampaignRaidMessages.INTRUDER);
    verify(visitor, never()).sendMessage(CampaignRaidMessages.INTRUDER);
  }

  @Test
  void intruderTicksUseActualProvincePresenceAndRecordOnlyLethalDamage() throws Exception {
    Player rogue = rig.player("Rogue");
    rig.attacker.addMember(rogue.getName());
    Player visitor = rig.player("Visitor");
    installTargetProvinceGrid();
    double[] rogueHealth = healthBoundary(rogue, 14.0);
    double[] aliceHealth = healthBoundary(rig.alice, 20.0);
    CampaignRaid raid = muster();
    CampaignRaidWarbandService.signupAttacker(rig.war, raid, rig.alice.getUniqueId(), "Alice");
    ProvincePresenceService.getInstance().tick(rig.domain.online.values());
    assertEquals(
        rig.target.getProvince(), ProvincePresenceService.getInstance().getCurrentProvince(rogue));
    CampaignRaidIntruderService.processTick();
    verify(rogue, never()).damage(anyDouble());
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    Battle battle = BattleManager.getByString(raid.getBattleId());

    rig.domain.ui.repeatingTasks.clear();
    CampaignRaidIntruderService.Tick.start();
    verify(rig.domain.ui.scheduler)
        .runTaskTimer(eq(rig.domain.ui.plugin), any(Runnable.class), eq(1L), eq(1L));
    assertEquals(1, rig.domain.ui.repeatingTasks.size());
    rig.domain.ui.repeatingTasks.getFirst().run();
    assertEquals(8.0, rogueHealth[0]);
    assertEquals(20.0, aliceHealth[0]);
    assertFalse(CampaignRaidIntruderService.consumeIntruderDeath(rogue.getUniqueId()));

    RaidAttackerEliminationService.markOut(battle, rig.alice.getUniqueId());
    CampaignRaidIntruderService.processTick();
    assertEquals(2.0, rogueHealth[0]);
    assertEquals(14.0, aliceHealth[0]);
    assertFalse(CampaignRaidIntruderService.consumeIntruderDeath(rogue.getUniqueId()));
    CampaignRaidIntruderService.processTick();
    assertEquals(0.0, rogueHealth[0]);
    assertTrue(CampaignRaidIntruderService.consumeIntruderDeath(rogue.getUniqueId()));
    assertFalse(CampaignRaidIntruderService.consumeIntruderDeath(rogue.getUniqueId()));
    assertFalse(CampaignRaidIntruderService.consumeIntruderDeath(null));
    assertFalse(CampaignRaidIntruderService.consumeIntruderDeath(rig.bob.getUniqueId()));
    verify(rig.bob, never()).damage(anyDouble());
    verify(visitor, never()).damage(anyDouble());

    rig.defender.getInstallationHandler().detachOnProvince(rig.target.getProvince());
    CampaignRaidIntruderService.processTick();
    assertEquals(0.0, rogueHealth[0]);
    assertEquals(8.0, aliceHealth[0]);
    CampaignRaidIntruderService.resetForTests();
    assertFalse(CampaignRaidIntruderService.consumeIntruderDeath(rogue.getUniqueId()));
  }

  @Test
  void realSchedulerCallbacksSendOneReminderThenStartAndEndTheRaid() throws Exception {
    rig.remember(Cache.class, "campaignRaidMusterReminderSecondsBefore");
    Cache.campaignRaidMusterReminderSecondsBefore = List.of(30);
    CampaignRaid raid = muster();
    Instant start = rig.now();
    CampaignRaidWarbandService.signupAttacker(rig.war, raid, rig.alice.getUniqueId(), "Alice");
    CampaignClock.reset();
    rig.domain.ui.tasks.clear();
    CampaignRaidMusterScheduler.onMusterStarted(rig.war, start);
    assertEquals(2, rig.domain.ui.tasks.size());
    List<Runnable> callbacks = new ArrayList<>(rig.domain.ui.tasks);
    long writes = rig.warWrites();
    rig.time(raid.getMusterEndsAt().minusSeconds(29));
    callbacks.getFirst().run();
    assertEquals(Set.of(30), raid.getMusterRemindersSent());
    assertEquals(writes + 1, rig.warWrites());
    verify(rig.alice).sendMessage(contains("starts in"));
    verify(rig.bob, never()).sendMessage(contains("starts in"));
    CampaignRaidMusterReminderService.processReminders(rig.war, rig.now());
    assertEquals(writes + 1, rig.warWrites());

    rig.time(raid.getMusterEndsAt());
    callbacks.getLast().run();
    Battle battle = BattleManager.getByString(raid.getBattleId());
    assertNotNull(battle);
    assertTrue(battle.hasStarted());
    assertEquals(CampaignRaidState.FIGHTING, raid.getState());
    CampaignClock.reset();
    rig.domain.ui.tasks.clear();
    CampaignRaidFightScheduler.onFightStarted(rig.war, raid.getMusterEndsAt());
    assertEquals(1, rig.domain.ui.tasks.size());
    Runnable end = rig.domain.ui.tasks.getFirst();
    CampaignRaidFightScheduler.cancelForWar(rig.war.getId());
    verify(rig.domain.ui.scheduler, atLeastOnce()).cancelTask(anyInt());
    CampaignRaidFightScheduler.onFightStarted(rig.war, raid.getMusterEndsAt());
    end = rig.domain.ui.tasks.getLast();
    rig.time(raid.getFightEndsAt());
    end.run();
    assertFalse(battle.hasStarted());
    assertTrue(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR));
    assertSame(rig.target, rig.defender.getInstallationHandler().getById(rig.target.getId()));
  }

  @Test
  void replacingMusterSchedulesCancelsOldTasksAndImmediateDeadlinesStartOnlyOneFight()
      throws Exception {
    rig.remember(Cache.class, "campaignRaidMusterReminderSecondsBefore");
    Cache.campaignRaidMusterReminderSecondsBefore = List.of(30);
    CampaignRaid raid = muster();
    Instant start = rig.now();
    CampaignClock.reset();
    rig.domain.ui.tasks.clear();
    CampaignRaidMusterScheduler.onMusterStarted(rig.war, start);
    CampaignRaidMusterScheduler.onMusterStarted(rig.war, start);
    assertEquals(4, rig.domain.ui.tasks.size());
    verify(rig.domain.ui.scheduler).cancelTask(1);
    verify(rig.domain.ui.scheduler).cancelTask(2);
    CampaignRaidMusterScheduler.cancelForWar(rig.war.getId());
    verify(rig.domain.ui.scheduler).cancelTask(3);
    verify(rig.domain.ui.scheduler).cancelTask(4);
    CampaignRaidMusterScheduler.onMusterStarted(rig.war, raid.getMusterEndsAt());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    assertNotNull(battle);
    assertTrue(battle.hasStarted());
    CampaignRaidMusterScheduler.processOverdue(rig.war, raid.getMusterEndsAt());
    assertEquals(1, BattleManager.get().size());
    CampaignRaidFightScheduler.onFightStarted(rig.war, raid.getFightEndsAt());
    assertFalse(battle.hasStarted());
    assertEquals(1, BattleManager.get().size());
  }

  private double[] healthBoundary(Player player, double initial) {
    double[] health = {initial};
    when(player.getHealth()).thenAnswer(call -> health[0]);
    doAnswer(
            call -> {
              health[0] = Math.max(0.0, health[0] - (double) call.getArgument(0));
              return null;
            })
        .when(player)
        .damage(anyDouble());
    return health;
  }

  private void installTargetProvinceGrid() throws Exception {
    for (String name :
        List.of(
            "mapEnabled",
            "provincesEnabled",
            "campaignRaidIntruderDamageAmount",
            "campaignRaidIntruderDamageIntervalTicks")) rig.remember(Cache.class, name);
    rig.remember(ProvincePresenceService.class, "instance");
    rig.remember(TitlePresenceService.class, "instance");
    rig.remember(RegionPresenceService.class, "instance");
    ProvincePresenceService.resetForTests();
    TitlePresenceService.resetForTests();
    RegionPresenceService.resetForTests();
    Cache.mapEnabled = true;
    Cache.provincesEnabled = true;
    Cache.campaignRaidIntruderDamageAmount = 6;
    Cache.campaignRaidIntruderDamageIntervalTicks = 0;
    var file = Files.createTempFile("raid-province-", ".bin.gz");
    try {
      try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
        out.write(
            ByteBuffer.allocate(10)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1)
                .putInt(1)
                .putShort((short) rig.target.getProvince())
                .array());
      }
      ProvinceGrid grid = ProvinceGrid.load(file.toFile());
      when(rig.domain.ui.plugin.getProvinceGrid()).thenReturn(grid);
    } finally {
      Files.deleteIfExists(file);
    }
    for (Player player : rig.domain.online.values())
      when(player.getLocation()).thenReturn(new Location(rig.domain.ui.world, 0, 64, 0));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aManualBattleCreatedDuringMusterMustNotBeReusedOrChanged(boolean started) {
    CampaignRaid raid = muster();
    Battle manual = BattleFactory.createBlank(BattleType.RAID, raid.getId());
    manual.setDisplayName("Staff practice");
    manual.setLootEnabled(true);
    BattleManager.addBattle(manual);
    if (started) assertNull(manual.start());

    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());

    assertAll(
        () -> assertFalse(manual.isCampaignRaid(), "Manual identity must be preserved"),
        () -> assertNull(manual.getWarId()),
        () -> assertEquals("Staff practice", manual.getDisplayName()),
        () -> assertTrue(manual.hasLootEnabled()),
        () -> assertEquals(started, manual.hasStarted()),
        () -> assertEquals(List.of(manual), BattleManager.get()),
        () ->
            assertFalse(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR)),
        () -> assertFalse(rig.war.getRaidRepairLockUntil().containsKey(rig.target.getId())),
        () -> assertNull(CampaignRaidService.getActive(rig.war)),
        () -> assertTrue(WarbandManager.get().isEmpty()));
  }

  @Test
  void startingMusterAvoidsIdsAlreadyHeldByManualBattles() {
    String proposed =
        BattleNamingService.slugifyDisplayName(
            BattleNamingService.buildRaidDisplayName(rig.war, rig.target));
    Battle manual = BattleFactory.createBlank(BattleType.RAID, proposed);
    BattleManager.addBattle(manual);

    CampaignRaid raid = muster();

    assertNotEquals(manual.getId(), raid.getId());
    assertEquals(List.of(manual), BattleManager.get());
    assertNull(manual.getWarId());
    assertFalse(manual.isCampaignRaid());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void identicallyNamedRaidsInDifferentWarsKeepTheirWarbandsAndCleanupSeparate(boolean longName) {
    String targetName = longName ? "Harbor district ".repeat(20) : rig.target.getName();
    if (longName) {
      rig.defender.getInstallationHandler().detachOnProvince(rig.target.getProvince());
      rig.defender
          .getInstallationHandler()
          .acceptTransferred(
              new Installation(
                  rig.target.getId(),
                  targetName,
                  rig.target.getKind(),
                  rig.target.getProvince(),
                  rig.target.getCenterX(),
                  rig.target.getCenterZ(),
                  0L));
    }
    CampaignRaid first = muster();
    CampaignRaidWarbandService.signupAttacker(rig.war, first, rig.alice.getUniqueId(), "Alice");
    Warband firstAttackers = CampaignRaidWarbandService.getAttackerWarband(first);
    Warband firstDefenders = CampaignRaidWarbandService.getDefenderWarband(first);
    Player charlie = rig.player("Charlie");
    Faction third = rig.domain.saved("lifecycle_third", "Charlie");
    rig.player("Dora");
    Faction fourth = rig.domain.saved("lifecycle_fourth", "Dora");
    War secondWar = new War(rig.war.getId() + 1, third, fourth);
    secondWar.setGoal(WarGoalType.SUBJUGATE);
    secondWar.setWarType(WarType.SUBJUGATE);
    secondWar.setBattleDay(Fixture.DAY);
    secondWar.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    secondWar.setOccupiedByAttacker(new ArrayList<>(List.of(30)));
    secondWar.setOccupiedByDefender(new ArrayList<>(List.of(40)));
    Installation source =
        new Installation("second_source", "Second source", InstallationKind.PORT, 30, 300, 300, 0L);
    Installation target =
        new Installation("second_target", targetName, InstallationKind.PORT, 40, 400, 400, 0L);
    third.getInstallationHandler().acceptTransferred(source);
    fourth.getInstallationHandler().acceptTransferred(target);
    WarManager.addWar(secondWar);
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            secondWar, third, source.getId(), target.getId(), rig.now()));
    CampaignRaid second = CampaignRaidService.getActive(secondWar);
    assertEquals(first.getDisplayName(), second.getDisplayName());
    assertNotEquals(first.getId(), second.getId());
    CampaignRaidWarbandService.signupAttacker(secondWar, second, charlie.getUniqueId(), "Charlie");
    Warband secondAttackers = CampaignRaidWarbandService.getAttackerWarband(second);
    Warband secondDefenders = CampaignRaidWarbandService.getDefenderWarband(second);

    assertAll(
        () -> assertNotSame(firstAttackers, secondAttackers),
        () -> assertNotSame(firstDefenders, secondDefenders),
        () -> assertEquals(Set.of(rig.alice.getUniqueId()), firstAttackers.getMemberIds()),
        () -> assertEquals(Set.of(charlie.getUniqueId()), secondAttackers.getMemberIds()),
        () -> {
          CampaignRaidService.endRaid(rig.war, rig.now());
          assertNull(CampaignRaidService.getActive(rig.war));
          assertSame(second, CampaignRaidService.getActive(secondWar));
          assertSame(secondAttackers, CampaignRaidWarbandService.getAttackerWarband(second));
          assertSame(secondDefenders, CampaignRaidWarbandService.getDefenderWarband(second));
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"war", "type"})
  void conflictingCampaignBattleIdentityIsPreservedAndTheMusterAborts(String mismatch) {
    CampaignRaid raid = muster();
    Battle existing =
        BattleFactory.createBlank(
            mismatch.equals("type") ? BattleType.FIELD : BattleType.RAID, raid.getId());
    existing.setCampaignRaid(true);
    int owner = rig.war.getId() + (mismatch.equals("war") ? 1 : 0);
    existing.setWarId(owner);
    existing.setDisplayName("Different campaign");
    existing.setLootEnabled(true);
    BattleManager.addBattle(existing);
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    assertEquals(owner, existing.getWarId());
    assertEquals("Different campaign", existing.getDisplayName());
    assertTrue(existing.hasLootEnabled());
    assertFalse(existing.hasStarted());
    assertEquals(List.of(existing), BattleManager.get());
    assertNull(CampaignRaidService.getActive(rig.war));
    assertFalse(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR));
    assertTrue(WarbandManager.get().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aMatchingRestoredCampaignBattleIsReusedWithoutDuplicateRegistration(boolean started) {
    CampaignRaid raid = muster();
    Battle existing;
    if (started) {
      CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
      existing = BattleManager.getByString(raid.getBattleId());
    } else {
      existing = BattleFactory.createBlank(BattleType.RAID, raid.getId());
      existing.setCampaignRaid(true);
      existing.setWarId(rig.war.getId());
      existing.setLives(123);
      BattleManager.addBattle(existing);
    }
    Instant originalStart = existing.getStartedAt();
    Battle reused = CampaignRaidBattleService.createAndStart(rig.war, raid, raid.getMusterEndsAt());
    assertSame(existing, reused);
    assertEquals(List.of(existing), BattleManager.get());
    assertTrue(existing.hasStarted());
    assertFalse(existing.hasLootEnabled());
    assertEquals(rig.war.getId(), existing.getWarId());
    assertEquals(started ? originalStart : raid.getMusterEndsAt(), existing.getStartedAt());
    if (!started) assertEquals(123, existing.getLives());
    assertEquals(2, WarbandManager.get().size());
  }

  @Test
  void musterDoesNotReuseOrDeleteAPreexistingPlayerWarband() {
    Player owner = rig.player("PrivateOwner");
    String proposed =
        BattleNamingService.slugifyDisplayName(
            BattleNamingService.buildRaidDisplayName(rig.war, rig.target));
    Warband manual = new Warband(proposed + "_attacker", owner);
    WarbandManager.addWarband(manual);
    CampaignRaid raid = muster();
    CampaignRaidWarbandService.signupAttacker(rig.war, raid, rig.alice.getUniqueId(), "Alice");
    assertAll(
        () -> assertNotSame(manual, CampaignRaidWarbandService.getAttackerWarband(raid)),
        () -> assertEquals(Set.of(owner.getUniqueId()), manual.getMemberIds()),
        () -> assertEquals(owner.getUniqueId(), manual.getLeaderId()),
        () -> {
          CampaignRaidService.endRaid(rig.war, rig.now());
          assertSame(manual, WarbandManager.getByString(manual.getId()));
          assertEquals(Set.of(owner.getUniqueId()), manual.getMemberIds());
        });
  }

  @Test
  void aPlayerWarbandCreatedDuringMusterCannotBeEnrolledOrDeletedAsRaidDefenders() {
    CampaignRaid raid = muster();
    Player owner = rig.player("PrivateOwner");
    Warband manual = new Warband(CampaignRaidWarbandService.defenderWarbandId(raid), owner);
    WarbandManager.addWarband(manual);
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    assertAll(
        () -> assertEquals(Set.of(owner.getUniqueId()), manual.getMemberIds()),
        () -> assertSame(manual, WarbandManager.getByString(manual.getId())),
        () -> assertNull(CampaignRaidService.getActive(rig.war)),
        () -> assertTrue(BattleManager.get().isEmpty()),
        () ->
            assertFalse(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR)),
        () -> assertTrue(rig.war.getRaidRepairLockUntil().isEmpty()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void restoredFightDoesNotRepurposeOrEndAReplacementManualBattle(boolean overdueTimer) {
    CampaignRaid raid = muster();
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    CampaignRaidData saved = raid.toData();
    Battle original = BattleManager.getByString(raid.getBattleId());
    original.end();
    BattleManager.get().clear();
    Battle manual = BattleFactory.createBlank(BattleType.RAID, saved.battleId);
    manual.setLootEnabled(true);
    manual.setDisplayName("Unrelated staff battle");
    BattleManager.addBattle(manual);
    assertNull(manual.start());
    rig.war.setActiveCampaignRaid(CampaignRaid.fromData(saved));
    rig.time(raid.getFightEndsAt().plusSeconds(overdueTimer ? 1 : -20));
    if (overdueTimer) CampaignRaidFightScheduler.processOverdue(rig.war, rig.now());
    else CampaignRaidResumeService.resumeAll();
    assertAll(
        () -> assertFalse(manual.isCampaignRaid()),
        () -> assertTrue(manual.hasLootEnabled()),
        () -> assertNull(manual.getWarId()),
        () -> assertEquals("Unrelated staff battle", manual.getDisplayName()),
        () ->
            assertTrue(
                manual.hasStarted(), "The raid timer must not end a replacement manual battle"),
        () -> assertTrue(manual.getSideById("attacker").getBands().isEmpty()),
        () -> assertTrue(manual.getSideById("defender").getBands().isEmpty()),
        () -> assertEquals(List.of(manual), BattleManager.get()));
  }

  private CampaignRaid muster() {
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    return CampaignRaidService.getActive(rig.war);
  }

  @Test
  void bossBarsTrackLiveParticipantsAndEliminationThenRemoveAllViewers() {
    CampaignRaid raid = muster();
    CampaignRaidWarbandService.signupAttacker(rig.war, raid, rig.alice.getUniqueId(), "Alice");
    rig.time(raid.getMusterEndsAt());
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    CampaignRaidBossBarService.clear(battle);
    rig.bars.clear();
    rig.time(raid.getFightEndsAt().minusSeconds(300));
    CampaignRaidBossBarService.update(battle, raid);
    assertEquals(2, rig.bars.size());
    BossBar time = rig.bars.get(0), raiders = rig.bars.get(1);
    assertEquals(Set.of(rig.alice, rig.bob), new HashSet<>(time.getPlayers()));
    assertEquals(time.getPlayers(), raiders.getPlayers());
    assertTrue(time.isVisible());
    assertTrue(time.getProgress() > 0.49 && time.getProgress() <= 0.51);
    assertEquals(1.0, raiders.getProgress());
    assertEquals("Raiders remaining: 1", raiders.getTitle());

    RaidAttackerEliminationService.markOut(battle, rig.alice.getUniqueId());
    CampaignRaidBossBarService.tickCampaignRaid(battle);
    assertEquals(0.0, raiders.getProgress());
    assertEquals("Raiders remaining: 0", raiders.getTitle());
    CampaignRaidWarbandService.getAttackerWarband(raid).removeMember(rig.alice.getUniqueId());
    CampaignRaidBossBarService.tickCampaignRaid(battle);
    assertEquals(List.of(rig.bob), time.getPlayers());
    assertEquals(List.of(rig.bob), raiders.getPlayers());
    battle.setDisplayName("");
    rig.time(raid.getFightEndsAt().plusSeconds(2));
    CampaignRaidBossBarService.update(battle, raid);
    assertEquals(0.0, time.getProgress());
    assertEquals(battle.getId() + " - 0s", time.getTitle());

    CampaignRaidBossBarService.clear(battle);
    assertTrue(time.getPlayers().isEmpty());
    assertTrue(raiders.getPlayers().isEmpty());
    assertFalse(time.isVisible());
    assertFalse(raiders.isVisible());
  }

  @Test
  void bossBarsIgnoreNonRaidAndUnstartedBattlesAndRemoveUnboundCampaignBars() {
    CampaignRaid raid = muster();
    Battle manual = BattleFactory.createBlank(BattleType.RAID, "manual_without_war");
    int before = rig.bars.size();
    CampaignRaidBossBarService.onFightStarted(null, raid);
    CampaignRaidBossBarService.onFightStarted(manual, raid);
    CampaignRaidBossBarService.update(manual, raid);
    CampaignRaidBossBarService.tickCampaignRaid(manual);
    CampaignRaidBossBarService.clear(null);
    assertEquals(before, rig.bars.size());
    manual.setCampaignRaid(true);
    BattleManager.addBattle(manual);
    assertNull(manual.start());
    CampaignRaidBossBarService.onFightStarted(manual, raid);
    List<BossBar> created = List.copyOf(rig.bars.subList(before, rig.bars.size()));
    assertTrue(created.stream().anyMatch(BossBar::isVisible));
    CampaignRaidBossBarService.tickCampaignRaid(manual);
    assertTrue(created.stream().noneMatch(BossBar::isVisible));
    assertTrue(created.stream().allMatch(bar -> bar.getPlayers().isEmpty()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void restoredMusterResumesBeforeOrAtItsOriginalDeadline(boolean overdue) {
    CampaignRaid original = muster();
    CampaignRaidData data = original.toData();
    WarbandManager.get().clear();
    CampaignRaid restored = CampaignRaid.fromData(data);
    rig.war.setActiveCampaignRaid(restored);
    rig.time(original.getMusterEndsAt().plusSeconds(overdue ? 1 : -10));
    CampaignRaidResumeService.resumeAll();
    assertNotNull(CampaignRaidWarbandService.getAttackerWarband(restored));
    if (overdue) {
      assertEquals(CampaignRaidState.FIGHTING, restored.getState());
      assertTrue(BattleManager.getByString(restored.getBattleId()).hasStarted());
    } else {
      assertEquals(CampaignRaidState.MUSTER, restored.getState());
      assertTrue(BattleManager.get().isEmpty());
      assertEquals(original.getMusterEndsAt(), restored.getMusterEndsAt());
      assertFalse(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR));
    }
  }

  @Test
  void resumeRecreatesAMissingFightFromSavedStateWithoutResettingItsDeadline() {
    CampaignRaid raid = muster();
    CampaignRaidWarbandService.signupAttacker(rig.war, raid, rig.alice.getUniqueId(), "Alice");
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    Battle previous = BattleManager.getByString(raid.getBattleId());
    Instant ends = raid.getFightEndsAt();
    CampaignRaidData data = raid.toData();
    data.battleId = null;
    previous.end();
    BattleManager.get().clear();
    CampaignRaid restored = CampaignRaid.fromData(data);
    rig.war.setActiveCampaignRaid(restored);
    rig.time(ends.minusSeconds(100));
    CampaignRaidResumeService.resumeAll();
    Battle recovered = BattleManager.getByString(restored.getBattleId());
    assertNotNull(recovered);
    assertNotSame(previous, recovered);
    assertTrue(recovered.hasStarted());
    assertFalse(recovered.hasLootEnabled());
    assertEquals(ends, restored.getFightEndsAt());
    assertTrue(CampaignRaidWarbandService.getAttackerWarband(restored).hasMember(rig.alice));
    assertEquals(1, BattleManager.get().size());
  }

  @Test
  void resumingAnExpiredFightEndsTheActualBattleAndKeepsItsQuota() {
    CampaignRaid raid = muster();
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    rig.time(raid.getFightEndsAt().plusSeconds(1));
    CampaignRaidResumeService.resumeAll();
    assertFalse(battle.hasStarted());
    assertTrue(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR));
    assertEquals(rig.war.getId(), battle.getWarId());
  }

  @Test
  void loadedBattleAssociationIgnoresAbsentWarsAndRestoresLegacyStartedTime() {
    CampaignRaidResumeService.applyLoadedBattle(null);
    Battle manual = BattleFactory.createBlank(BattleType.RAID, "unrelated_loaded");
    CampaignRaidResumeService.applyLoadedBattle(manual);
    manual.setWarId(Integer.MAX_VALUE);
    CampaignRaidResumeService.applyLoadedBattle(manual);
    manual.setWarId(rig.war.getId());
    CampaignRaidResumeService.applyLoadedBattle(manual);
    assertFalse(manual.isCampaignRaid());
    assertNull(manual.getStartedAt());
    CampaignRaidResumeService.resumeAll();
    assertTrue(BattleManager.get().isEmpty());

    CampaignRaid raid = muster();
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    battle.setStartedAt(null); // Old persisted battles predate the optional startedAt field.
    CampaignRaidResumeService.applyLoadedBattle(battle);
    assertEquals(
        raid.getFightEndsAt().minusSeconds(Cache.campaignRaidDurationSeconds),
        battle.getStartedAt());
    Instant restored = battle.getStartedAt();
    CampaignRaidResumeService.applyLoadedBattle(battle);
    assertEquals(restored, battle.getStartedAt());
    CampaignRaidService.endRaid(rig.war, rig.now());
    CampaignRaidResumeService.applyLoadedBattle(battle);
    assertEquals(restored, battle.getStartedAt());
  }

  @Test
  void incompleteSavedMustersDoNotCreateBandsScheduleTasksOrConsumeQuota() {
    CampaignRaidData saved = muster().toData();
    saved.musterEndsAt = null;
    WarbandManager.get().clear();
    CampaignRaid restored = CampaignRaid.fromData(saved);
    rig.war.setActiveCampaignRaid(restored);
    rig.domain.ui.tasks.clear();
    CampaignRaidResumeService.resumeAll();
    CampaignRaidMusterReminderService.processReminders(rig.war, rig.now());
    assertTrue(WarbandManager.get().isEmpty());
    assertTrue(rig.domain.ui.tasks.isEmpty());
    assertFalse(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR));
    saved.attackerCoalition = null;
    CampaignRaid withoutCoalition = CampaignRaid.fromData(saved);
    CampaignRaidWarbandService.enrollOnlineDefenders(rig.war, withoutCoalition);
    CampaignRaidWarbandService.signupAttacker(
        rig.war, withoutCoalition, rig.alice.getUniqueId(), "Alice");
    assertTrue(WarbandManager.get().isEmpty());
  }

  @Test
  void savedRaidCollectionsCopyInputAndRoundTripReminderStateWithoutNullEntries() {
    CampaignRaidData data = muster().toData();
    data.raidKind = "retired_kind";
    data.musterRemindersSent = Arrays.asList(30, null, 60);
    CampaignRaid restored = CampaignRaid.fromData(data);
    assertNull(restored.getRaidKind());
    assertEquals(Set.of(30, 60), restored.getMusterRemindersSent());
    Set<String> ids = new LinkedHashSet<>(List.of(rig.alice.getUniqueId().toString()));
    restored.setMusterParticipantIds(ids);
    ids.clear();
    assertEquals(Set.of(rig.alice.getUniqueId().toString()), restored.getMusterParticipantIds());
    Set<Integer> reminders = new LinkedHashSet<>(List.of(60));
    restored.setMusterRemindersSent(reminders);
    reminders.clear();
    CampaignRaid roundTrip = CampaignRaid.fromData(restored.toData());
    assertEquals(Set.of(60), roundTrip.getMusterRemindersSent());
    assertEquals(restored.getMusterParticipantIds(), roundTrip.getMusterParticipantIds());
    assertEquals(rig.war.getId(), roundTrip.getWarId());
    restored.clearMusterRemindersSent();
    assertTrue(restored.getMusterRemindersSent().isEmpty());
    restored.setMusterRemindersSent(null);
    restored.setMusterParticipantIds(null);
    assertNull(restored.toData().musterRemindersSent);
    assertNull(restored.toData().musterParticipantIds);
    assertNull(CampaignRaid.fromData(null));
    assertNull(CampaignRaid.fromData(new CampaignRaidData()));
  }

  @Test
  void stoppingRaidSchedulersCancelsAllPendingTasksWithoutChangingTheRaid() throws Exception {
    rig.remember(Cache.class, "campaignRaidMusterReminderSecondsBefore");
    Cache.campaignRaidMusterReminderSecondsBefore = List.of(30);
    CampaignRaid raid = muster();
    Instant now = rig.now();
    CampaignClock.reset();
    rig.domain.ui.tasks.clear();
    CampaignRaidMusterScheduler.onMusterStarted(rig.war, now);
    assertEquals(2, rig.domain.ui.tasks.size());
    CampaignRaidMusterScheduler.cancelAllScheduled();
    verify(rig.domain.ui.scheduler).cancelTask(1);
    verify(rig.domain.ui.scheduler).cancelTask(2);
    assertEquals(CampaignRaidState.MUSTER, raid.getState());
    assertTrue(raid.getMusterRemindersSent().isEmpty());
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    assertTrue(battle.hasStarted());
    int fightTaskId = rig.domain.ui.tasks.size();
    assertTrue(fightTaskId > 2);
    CampaignRaidFightScheduler.cancelAllScheduled();
    verify(rig.domain.ui.scheduler).cancelTask(fightTaskId);
    assertTrue(battle.hasStarted());
    assertEquals(CampaignRaidState.FIGHTING, raid.getState());
  }

  @ParameterizedTest
  @ValueSource(strings = {"early", "expired", "sent", "ended"})
  void staleReminderCallbacksNeverSendOrPersistADuplicate(String change) throws Exception {
    rig.remember(Cache.class, "campaignRaidMusterReminderSecondsBefore");
    Cache.campaignRaidMusterReminderSecondsBefore = List.of(30);
    CampaignRaid raid = muster();
    Instant start = rig.now();
    CampaignClock.reset();
    rig.domain.ui.tasks.clear();
    CampaignRaidMusterScheduler.onMusterStarted(rig.war, start);
    Runnable reminder = rig.domain.ui.tasks.getFirst();
    rig.time(raid.getMusterEndsAt().minusSeconds(20));
    switch (change) {
      case "early" -> rig.time(raid.getMusterEndsAt().minusSeconds(40));
      case "expired" -> rig.time(raid.getMusterEndsAt().plusSeconds(1));
      case "sent" -> CampaignRaidMusterReminderService.processReminders(rig.war, rig.now());
      case "ended" -> CampaignRaidService.endRaid(rig.war, rig.now());
      default -> fail("Unexpected case");
    }
    long writes = rig.warWrites();
    Set<Integer> reminders = Set.copyOf(raid.getMusterRemindersSent());
    clearInvocations(rig.alice, rig.bob);
    reminder.run();
    assertEquals(writes, rig.warWrites());
    assertEquals(reminders, raid.getMusterRemindersSent());
    verify(rig.alice, never()).sendMessage(contains("starts in"));
    verify(rig.bob, never()).sendMessage(contains("starts in"));
  }

  @Test
  void overdueReminderProcessingSkipsExpiredWindowsAndAlreadySentOffsets() throws Exception {
    rig.remember(Cache.class, "campaignRaidMusterReminderSecondsBefore");
    Cache.campaignRaidMusterReminderSecondsBefore = List.of(180, 30);
    CampaignRaid raid = muster();
    rig.time(raid.getMusterEndsAt().minusSeconds(20));
    long writes = rig.warWrites();
    CampaignRaidMusterReminderService.processReminders(rig.war, rig.now());
    assertEquals(Set.of(30), raid.getMusterRemindersSent());
    assertEquals(writes + 1, rig.warWrites());
    CampaignClock.reset();
    rig.domain.ui.tasks.clear();
    CampaignRaidMusterScheduler.onMusterStarted(rig.war, raid.getMusterEndsAt().minusSeconds(20));
    assertEquals(1, rig.domain.ui.tasks.size(), "Only the muster timer remains due");
    verify(rig.alice, times(1)).sendMessage(contains("starts in"));
  }

  @Test
  void staleMusterAndFightTimersLeaveLaterLifecycleStateUntouched() throws Exception {
    rig.remember(Cache.class, "campaignRaidMusterReminderSecondsBefore");
    Cache.campaignRaidMusterReminderSecondsBefore = List.of();
    CampaignRaid raid = muster();
    Instant now = rig.now();
    CampaignClock.reset();
    rig.domain.ui.tasks.clear();
    CampaignRaidMusterScheduler.onMusterStarted(rig.war, now);
    Runnable musterTimer = rig.domain.ui.tasks.getFirst();
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    Runnable fightTimer = rig.domain.ui.tasks.getLast();
    long writes = rig.warWrites();
    musterTimer.run();
    assertEquals(writes, rig.warWrites());
    assertEquals(1, BattleManager.get().size());
    assertTrue(battle.hasStarted());
    battle.end();
    CampaignRaidService.endRaid(rig.war, rig.now());
    writes = rig.warWrites();
    fightTimer.run();
    assertEquals(writes, rig.warWrites());
    assertNull(CampaignRaidService.getActive(rig.war));
    assertTrue(WarbandManager.get().isEmpty());
  }

  @Test
  void schedulerEntrypointsRejectMissingContextWithoutSchedulingOrPersisting() {
    long writes = rig.warWrites();
    CampaignRaidMusterScheduler.onMusterStarted(null, rig.now());
    CampaignRaidMusterScheduler.onMusterStarted(rig.war, null);
    CampaignRaidMusterScheduler.onMusterStarted(rig.war, rig.now());
    CampaignRaidFightScheduler.onFightStarted(null, rig.now());
    CampaignRaidFightScheduler.onFightStarted(rig.war, null);
    CampaignRaidFightScheduler.onFightStarted(rig.war, rig.now());
    assertFalse(CampaignRaidMusterScheduler.processOverdue(null, rig.now()));
    assertFalse(CampaignRaidFightScheduler.processOverdue(null, rig.now()));
    CampaignRaidMusterReminderService.processReminders(null, rig.now());
    CampaignRaidMusterReminderService.processReminders(rig.war, null);
    CampaignRaidMusterReminderService.processReminders(rig.war, rig.now());
    assertTrue(rig.domain.ui.tasks.isEmpty());
    assertEquals(writes, rig.warWrites());
    assertTrue(BattleManager.get().isEmpty());
  }

  @Test
  void persistedRaidKindsAndStatesRemainCanonicalUnderTurkishLocale() {
    CampaignRaidData data = muster().toData();
    data.raidKind = "air";
    data.state = "fighting";
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      CampaignRaid restored = CampaignRaid.fromData(data);
      assertAll(
          () -> assertEquals(RaidTargetService.RaidKind.AIR, restored.getRaidKind()),
          () -> assertEquals(CampaignRaidState.FIGHTING, restored.getState()),
          () -> assertEquals("fighting", restored.toData().state));
    } finally {
      Locale.setDefault(original);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "removed_state"})
  void unknownSavedRaidStateCannotResumeARealBattle(String state) {
    CampaignRaidData data = muster().toData();
    data.state = state;
    data.raidKind = "";
    CampaignRaid restored = CampaignRaid.fromData(data);
    assertNull(restored.getState());
    assertNull(restored.getRaidKind());
    rig.war.setActiveCampaignRaid(restored);
    CampaignRaidResumeService.resumeAll();
    assertTrue(BattleManager.get().isEmpty());
    assertFalse(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR));
  }

  @Test
  void launchAvailabilityExplainsWindowParticipationQuotaAndDevelopmentOverride() {
    var missing = CampaignRaidLaunchAvailability.describe(null, null, null);
    assertFalse(missing.enabled());
    assertTrue(
        missing.loreLines().stream().anyMatch(line -> line.contains("Outside raid call window")));
    Faction outsider = rig.domain.saved("unaffiliated", "Visitor");
    assertFalse(CampaignRaidLaunchAvailability.describe(rig.war, outsider, rig.now()).enabled());
    var enabled = CampaignRaidLaunchAvailability.describe(rig.war, rig.attacker, rig.now());
    assertTrue(enabled.enabled());
    assertTrue(enabled.loreLines().stream().anyMatch(line -> line.contains("click to launch")));
    CampaignRaid raid = muster();
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    CampaignRaidService.endRaid(rig.war, rig.now());
    assertFalse(
        CampaignRaidLaunchAvailability.describe(rig.war, rig.attacker, rig.now()).enabled());
    WarDevMode.setEnabled(true);
    var bypassed = CampaignRaidLaunchAvailability.describe(rig.war, rig.attacker, rig.now());
    assertTrue(bypassed.enabled());
    assertTrue(bypassed.loreLines().stream().anyMatch(line -> line.contains("bypassed (devmode)")));
  }

  @Test
  void unavailableRaidListsAndSourcesNeverExposeOtherFactionsInstallations() {
    Faction outsider = rig.domain.saved("unaffiliated", "Visitor");
    assertTrue(
        CampaignRaidEligibilityService.listValidSources(null, rig.attacker.getId(), rig.now())
            .isEmpty());
    assertTrue(
        CampaignRaidEligibilityService.listValidSources(rig.war, outsider.getId(), rig.now())
            .isEmpty());
    assertTrue(
        CampaignRaidEligibilityService.listValidTargets(
                null, rig.attacker.getId(), rig.source.getId(), rig.now())
            .isEmpty());
    assertTrue(
        CampaignRaidEligibilityService.listValidTargets(
                rig.war, outsider.getId(), rig.source.getId(), rig.now())
            .isEmpty());
    assertTrue(
        CampaignRaidEligibilityService.listValidTargets(
                rig.war, rig.attacker.getId(), "missing", rig.now())
            .isEmpty());
    assertFalse(
        CampaignRaidEligibilityService.isValidSource(
            null, rig.attacker.getId(), rig.source.getId(), rig.now()));
    assertFalse(
        CampaignRaidEligibilityService.isValidSource(
            rig.war, outsider.getId(), rig.source.getId(), rig.now()));
    assertFalse(
        CampaignRaidEligibilityService.isValidTarget(
            null, rig.attacker.getId(), rig.source.getId(), rig.target.getId(), rig.now()));
    assertFalse(
        CampaignRaidEligibilityService.isValidTarget(
            rig.war, outsider.getId(), rig.source.getId(), rig.target.getId(), rig.now()));
    assertFalse(
        CampaignRaidEligibilityService.isValidTarget(
            rig.war, rig.attacker.getId(), "missing", rig.target.getId(), rig.now()));
    assertFalse(
        CampaignRaidEligibilityService.isValidTarget(
            rig.war, rig.attacker.getId(), rig.source.getId(), "missing", rig.now()));
    assertTrue(BattleManager.get().isEmpty());
    assertSame(rig.source, rig.attacker.getInstallationHandler().getById(rig.source.getId()));
    assertSame(rig.target, rig.defender.getInstallationHandler().getById(rig.target.getId()));
  }

  @Test
  void raidKindTargetLookupRejectsWrongOwnerKindAndWindow() {
    var kind = RaidTargetService.RaidKind.NAVAL;
    Faction outsider = rig.domain.saved("unaffiliated", "Visitor");
    assertFalse(
        CampaignRaidEligibilityService.isValidTargetForRaidKind(
            rig.war, rig.attacker.getId(), "missing", kind, rig.now()));
    assertFalse(
        CampaignRaidEligibilityService.isValidTargetForRaidKind(
            rig.war, outsider.getId(), rig.target.getId(), kind, rig.now()));
    assertFalse(
        CampaignRaidEligibilityService.isValidTargetForRaidKind(
            rig.war, rig.attacker.getId(), rig.source.getId(), kind, rig.now()));
    assertTrue(
        CampaignRaidEligibilityService.isValidTargetForRaidKind(
            rig.war, rig.attacker.getId(), rig.target.getId(), kind, rig.now()));
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 10));
    assertFalse(
        CampaignRaidEligibilityService.isValidTargetForRaidKind(
            rig.war, rig.attacker.getId(), rig.target.getId(), kind, rig.now()));
  }

  @Test
  void anOldBattleEndEventCannotCancelANewerRaidInTheSameWar() {
    CampaignRaid previous = muster();
    CampaignRaidLaunchService.startFight(rig.war, previous.getMusterEndsAt());
    Battle previousBattle = BattleManager.getByString(previous.getBattleId());
    BattleEndSupport.endBattle(
        previousBattle,
        null,
        net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason.TIMER);
    var events = org.mockito.ArgumentCaptor.forClass(org.bukkit.event.Event.class);
    verify(Bukkit.getPluginManager(), atLeastOnce()).callEvent(events.capture());
    var ended =
        events.getAllValues().stream()
            .filter(
                net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent.class::isInstance)
            .map(net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent.class::cast)
            .findFirst()
            .orElseThrow();
    var listener = new CampaignRaidBattleEndService();
    listener.onBattleEnded(ended);
    assertEquals(1, CampaignRaidService.resetRaidQuota(rig.war, CampaignCoalition.AGGRESSOR));
    Installation replacementTarget =
        rig.install(rig.defender, "later_target", InstallationKind.PORT, 21);
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), replacementTarget.getId(), rig.now()));
    CampaignRaid current = CampaignRaidService.getActive(rig.war);
    assertNotEquals(previous.getId(), current.getId());
    Warband currentAttackers = CampaignRaidWarbandService.getAttackerWarband(current);
    listener.onBattleEnded(ended);
    assertSame(current, CampaignRaidService.getActive(rig.war));
    assertSame(currentAttackers, CampaignRaidWarbandService.getAttackerWarband(current));
    assertEquals(CampaignRaidState.MUSTER, current.getState());
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "owned_running", "manual_collision"})
  void aSavedFightingRaidWithoutADeadlineIsAbortedInsteadOfStartingAnUnlimitedFight(
      String savedBattle) {
    CampaignRaid raid = muster();
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    CampaignRaidData data = raid.toData();
    data.fightEndsAt = null;
    Battle original = BattleManager.getByString(raid.getBattleId());
    Battle manual = BattleFactory.createBlank(BattleType.RAID, raid.getBattleId());
    if (!savedBattle.equals("owned_running")) {
      original.end();
      BattleManager.get().clear();
      WarbandManager.get().clear();
    }
    if (savedBattle.equals("manual_collision")) {
      manual.setLootEnabled(true);
      BattleManager.addBattle(manual);
      assertNull(manual.start());
    }
    rig.war.setActiveCampaignRaid(CampaignRaid.fromData(data));
    CampaignRaidResumeService.resumeAll();
    assertAll(
        () -> assertNull(CampaignRaidService.getActive(rig.war)),
        () -> {
          if (savedBattle.equals("manual_collision")) {
            assertEquals(List.of(manual), BattleManager.get());
            assertTrue(manual.hasStarted());
            assertTrue(manual.hasLootEnabled());
            assertFalse(manual.isCampaignRaid());
          } else {
            assertTrue(BattleManager.get().isEmpty());
            assertFalse(original.hasStarted());
          }
        },
        () -> assertTrue(WarbandManager.get().isEmpty()),
        () ->
            assertTrue(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR)));
  }

  @Test
  void defendersLaunchingARaidReceiveRaiderRulesAndKeepTheirCoalitionIdentity() {
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.defender, rig.target.getId(), rig.source.getId(), rig.now()));
    CampaignRaid raid = CampaignRaidService.getActive(rig.war);
    Warband actualRaiders =
        WarbandManager.getByString(CampaignRaidWarbandService.attackerWarbandId(raid));
    Warband retrievedRaiders = CampaignRaidWarbandService.getAttackerWarband(raid);
    assertEquals(
        CampaignRaidResults.JoinResult.OK,
        CampaignRaidJoinService.join(
            rig.war, rig.bob.getUniqueId(), "Bob", rig.defender, raid.getId(), rig.now()));
    rig.time(raid.getMusterEndsAt());
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    assertAll(
        () ->
            assertSame(
                actualRaiders,
                retrievedRaiders,
                "Raid lookup must retain defender-coalition raiders"),
        () ->
            assertTrue(
                actualRaiders.hasMember(rig.bob), "Successful signup must reach the actual roster"),
        () -> assertSame(raid, CampaignRaidService.getActive(rig.war)),
        () -> assertTrue(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.DEFENDER)),
        () ->
            assertFalse(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR)),
        () -> {
          assertNotNull(battle, "A valid defender-launched raid must start");
          assertTrue(battle.hasStarted());
          assertEquals(CampaignCoalition.DEFENDER, battle.getOffensiveCoalition());
          assertEquals("defender", actualRaiders.getCampaignSideId());
          assertSame(battle.getSideById("attacker"), battle.getSideByPlayer(rig.bob));
          assertSame(battle.getSideById("defender"), battle.getSideByPlayer(rig.alice));
          assertEquals(0, battle.getSideByPlayer(rig.bob).getLives());
          assertEquals(9999, battle.getSideByPlayer(rig.alice).getLives());
          assertEquals(1, RaidAttackerEliminationService.countAttackerRoster(battle));
          RaidAttackerEliminationService.markOut(battle, rig.bob.getUniqueId());
          assertEquals(0, RaidAttackerEliminationService.countActiveAttackers(battle));
          assertTrue(RaidAttackerEliminationService.isAttackerSideEliminated(battle));
          assertEquals(
              "attacker", CampaignRaidWarbandService.getDefenderWarband(raid).getCampaignSideId());
          verify(rig.alice)
              .sendTitle(
                  eq("§cRAID INCOMING"), contains(rig.source.getName()), eq(10), eq(120), eq(10));
        });
  }

  @Test
  void reverseRaidAcceptsLateDefendersThroughNormalWarbandSignup() {
    Player late = rig.player("LateDefender");
    rig.attacker.addMember(late.getName());
    when(late.isOnline()).thenReturn(false);
    CampaignRaid raid = reverseMuster();
    rig.time(raid.getMusterEndsAt());
    CampaignRaidLaunchService.startFight(rig.war, rig.now());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    Warband defenders = CampaignRaidWarbandService.getDefenderWarband(raid);
    assertFalse(defenders.hasMember(late));
    int lives = battle.getSideById("defender").getLives();
    when(late.isOnline()).thenReturn(true);

    String result = CampaignWarbandSignupService.signup(late, defenders, rig.attacker);

    assertAll(
        () -> assertNull(result, "A defender must be allowed onto its role-relative battle side"),
        () -> assertTrue(defenders.hasMember(late)),
        () -> assertSame(battle, BattleManager.currentBattle.get(late)),
        () -> assertSame(battle.getSideById("defender"), battle.getSideByPlayer(late)),
        () -> assertEquals(lives, battle.getSideById("defender").getLives()));
    assertNotNull(CampaignWarbandSignupService.signup(rig.bob, defenders, rig.defender));
    assertFalse(defenders.hasMember(rig.bob));
    assertSame(battle.getSideById("attacker"), battle.getSideByPlayer(rig.bob));
    assertNull(CampaignWarbandSignupService.signup(late, defenders, rig.attacker));
    assertEquals(2, defenders.getRealMemberCount());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reverseRaidLeaderAuthorityAndLateLoginFollowTheWarbandCoalition(boolean scheduledBattle)
      throws Exception {
    if (scheduledBattle) {
      rig.remember(Cache.class, "battleCampaignTemplateField");
      Cache.battleCampaignTemplateField = "";
      rig.war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
      rig.war.setScheduledBattleAt(rig.now().plusSeconds(7200));
      assertTrue(BattleScheduleService.markScheduledAtProvince(rig.war, rig.source.getProvince()));
      Battle scheduled = BattleManager.getByWarId(rig.war.getId());
      assertNotNull(scheduled);
      assertFalse(scheduled.hasStarted());
      assertEquals(CampaignCoalition.DEFENDER, scheduled.getOffensiveCoalition());
    }
    Player recruit = rig.player("DefendingRecruit");
    rig.attacker.addMember(recruit.getName());
    when(rig.alice.isOnline()).thenReturn(false);
    CampaignRaid raid = reverseMuster();
    rig.time(raid.getMusterEndsAt());
    CampaignRaidLaunchService.startFight(rig.war, rig.now());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    Warband raiders = CampaignRaidWarbandService.getAttackerWarband(raid);
    Warband defenders = CampaignRaidWarbandService.getDefenderWarband(raid);
    assertEquals(recruit.getUniqueId(), defenders.getLeaderId());
    when(rig.alice.isOnline()).thenReturn(true);

    new CampaignRaidWarbandService.Listener()
        .onJoin(
            new org.bukkit.event.player.PlayerJoinEvent(
                rig.alice, net.kyori.adventure.text.Component.empty()));

    assertAll(
        () -> assertTrue(CampaignWarbandBattleService.isWarSideMainLeader(rig.war, raiders, "Bob")),
        () ->
            assertFalse(
                CampaignWarbandBattleService.isWarSideMainLeader(rig.war, raiders, "Alice")),
        () ->
            assertTrue(
                CampaignWarbandBattleService.isWarSideMainLeader(rig.war, defenders, "Alice")),
        () ->
            assertFalse(
                CampaignWarbandBattleService.isWarSideMainLeader(rig.war, defenders, "Bob")),
        () -> assertEquals(rig.alice.getUniqueId(), defenders.getLeaderId()),
        () -> assertTrue(defenders.hasMember(rig.alice)),
        () -> assertTrue(defenders.hasMember(recruit)),
        () -> assertEquals(2, defenders.getRealMemberCount()),
        () -> assertSame(battle.getSideById("defender"), battle.getSideByPlayer(rig.alice)));
  }

  private CampaignRaid reverseMuster() {
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.defender, rig.target.getId(), rig.source.getId(), rig.now()));
    CampaignRaid raid = CampaignRaidService.getActive(rig.war);
    assertEquals(
        CampaignRaidResults.JoinResult.OK,
        CampaignRaidJoinService.join(
            rig.war, rig.bob.getUniqueId(), "Bob", rig.defender, raid.getId(), rig.now()));
    return raid;
  }

  @Test
  void raidStartUsesTheLaunchersSourceWhenANeutralPortSharesItsId() {
    Player charlie = rig.player("Charlie");
    Faction launcher = rig.domain.saved("second_raid_launcher", charlie.getName());
    Installation actualSource =
        rig.install(launcher, rig.source.getId(), InstallationKind.PORT, 30);
    War second = new War(rig.war.getId() + 1, launcher, rig.defender);
    second.setGoal(WarGoalType.SUBJUGATE);
    second.setWarType(WarType.SUBJUGATE);
    second.setBattleDay(Fixture.DAY);
    second.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    second.setOccupiedByAttacker(new ArrayList<>(List.of(actualSource.getProvince())));
    second.setOccupiedByDefender(new ArrayList<>(List.of(rig.target.getProvince())));
    WarManager.addWar(second);
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            second, launcher, actualSource.getId(), rig.target.getId(), rig.now()));
    CampaignRaid raid = CampaignRaidService.getActive(second);
    assertEquals(
        CampaignRaidResults.JoinResult.OK,
        CampaignRaidJoinService.join(
            second, charlie.getUniqueId(), charlie.getName(), launcher, raid.getId(), rig.now()));
    rig.time(raid.getMusterEndsAt());

    CampaignRaidLaunchService.startFight(second, rig.now());

    Battle battle = BattleManager.getByString(raid.getBattleId());
    assertNotNull(battle);
    Location expected = new Location(rig.domain.ui.world, 300.5, 65, 300.5);
    assertAll(
        () -> assertTrue(battle.hasStarted()),
        () -> assertEquals(expected, battle.getSideById("attacker").getSpawn()),
        () -> assertEquals(expected, battle.getSideById("attacker").getJail()),
        () -> verify(charlie).teleport(expected),
        () ->
            assertSame(
                actualSource, launcher.getInstallationHandler().getById(actualSource.getId())),
        () ->
            assertSame(
                rig.source, rig.attacker.getInstallationHandler().getById(rig.source.getId())),
        () ->
            assertSame(
                rig.target, rig.defender.getInstallationHandler().getById(rig.target.getId())),
        () -> assertSame(raid, CampaignRaidService.getActive(second)));
  }

  /**
   * Shared real campaign domain for these two lifecycle suites; only server and file I/O are faked.
   */
  public static final class Fixture implements AutoCloseable {
    public static final LocalDate DAY = LocalDate.of(2026, 10, 10);
    public final FactionDomainFixture domain = new FactionDomainFixture();
    public final Player alice = player("Alice"), bob = player("Bob");
    public final Faction attacker = domain.saved("lifecycle_iron", "Alice");
    public final Faction defender = domain.saved("lifecycle_river", "Bob");
    public final War war = new War(918731, attacker, defender);
    public final InventoryManager navigation = mock(InventoryManager.class);
    public final CampaignRaidLaunchView raidView = new CampaignRaidLaunchView(navigation);
    public final List<Write> writes = new ArrayList<>();
    public final List<BossBar> bars = new ArrayList<>();
    public final Installation source, target;
    private MockedStatic<JsonUtil> json;
    private MockedStatic<TLibs> tlibs;
    private final Duration oldOffset = CampaignClock.getOffset();
    private final List<Battle> oldBattles = new ArrayList<>(BattleManager.get());
    private final List<Warband> oldBands = new ArrayList<>(WarbandManager.get());
    private final Map<Player, Battle> oldCurrent = new HashMap<>(BattleManager.currentBattle);
    private final Map<String, BattleTemplate> oldTemplates = BattleTemplateLoader.getAll();
    private final Map<Field, Object> oldFields = new LinkedHashMap<>();
    private final List<Runnable> restoreContents = new ArrayList<>();

    public Fixture() throws Exception {
      try {
        BattleManager.get().clear();
        WarbandManager.get().clear();
        BattleManager.currentBattle.clear();
        for (String name :
            List.of(
                "warVoteCloseHour",
                "warRaidWindowStartHour",
                "warRaidWindowEndHour",
                "campaignRaidMusterSeconds",
                "campaignRaidDurationSeconds",
                "campaignRaidRepairLockHours",
                "battleCampaignTemplateRaid",
                "warBattleLivesPerRegiment",
                "warBattleMinSideLives")) remember(Cache.class, name);
        remember(WarDevMode.class, "enabled");
        set(WarDevMode.class, "enabled", false);
        snapshotMap(InstallationConfigLoader.class, "byKind");
        remember(InstallationConfigLoader.class, "consentProximityBlocks");
        remember(InstallationConfigLoader.class, "transferRequestTimeoutSeconds");
        snapshotMap(CampaignRaidMusterScheduler.class, "scheduledWarTasks");
        snapshotMap(CampaignRaidMusterReminderService.class, "scheduledReminderTasks");
        snapshotMap(CampaignRaidFightScheduler.class, "scheduledWarTasks");
        snapshotMap(CampaignRaidBossBarService.class, "BARS");
        snapshotMap(CampaignBattleLaunchService.class, "belligerentStartFailure");
        snapshotMap(CampaignBattleLaunchService.class, "adminStartFailure");
        snapshotSet(CampaignRaidIntruderService.class, "intruderDeathPending");
        snapshotSet(CampaignRaidIntruderService.class, "enterWarningsSent");
        snapshotSet(CampaignWarbandLeaveBlock.class, "blockedKeys");
        Cache.warVoteCloseHour = 16;
        Cache.warRaidWindowStartHour = 19;
        Cache.warRaidWindowEndHour = 20;
        Cache.campaignRaidMusterSeconds = 60;
        Cache.campaignRaidDurationSeconds = 600;
        Cache.campaignRaidRepairLockHours = 6;
        Cache.warBattleLivesPerRegiment = 4;
        Cache.warBattleMinSideLives = 1;
        Cache.battleCampaignTemplateRaid = "lifecycle_raid_template";
        time(BattleWindowService.atScheduleHour(DAY, 19).plusSeconds(120));
        war.setGoal(WarGoalType.SUBJUGATE);
        war.setWarType(WarType.SUBJUGATE);
        war.setBattleDay(DAY);
        war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
        war.setOccupiedByAttacker(new ArrayList<>());
        war.setOccupiedByDefender(new ArrayList<>());
        WarManager.addWar(war);
        when(domain.ui.world.getName()).thenReturn("world");
        when(domain.ui.world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(64);
        when(Bukkit.getPlayer(any(UUID.class)))
            .thenAnswer(
                call ->
                    domain.online.values().stream()
                        .filter(p -> p.getUniqueId().equals(call.getArgument(0)))
                        .findFirst()
                        .orElse(null));
        when(Bukkit.getOnlinePlayers()).thenAnswer(call -> domain.online.values());
        when(Bukkit.getOfflinePlayer(anyString()))
            .thenAnswer(
                call -> {
                  String name = call.getArgument(0);
                  Player online = domain.online.get(name);
                  if (online != null) return online;
                  OfflinePlayer profile = mock(OfflinePlayer.class);
                  when(profile.getName()).thenReturn(name);
                  when(profile.getUniqueId())
                      .thenReturn(
                          UUID.nameUUIDFromBytes(
                              name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                  return profile;
                });
        when(Bukkit.getConsoleSender()).thenReturn(mock(ConsoleCommandSender.class));
        when(Bukkit.createBossBar(
                anyString(), any(BarColor.class), any(BarStyle.class), any(BarFlag[].class)))
            .thenAnswer(
                call -> {
                  BossBar bar = mock(BossBar.class);
                  List<Player> viewers = new ArrayList<>();
                  String[] title = {call.getArgument(0)};
                  double[] progress = {1.0};
                  boolean[] visible = {true};
                  when(bar.getPlayers()).thenAnswer(ignored -> List.copyOf(viewers));
                  when(bar.getTitle()).thenAnswer(ignored -> title[0]);
                  when(bar.getProgress()).thenAnswer(ignored -> progress[0]);
                  when(bar.isVisible()).thenAnswer(ignored -> visible[0]);
                  doAnswer(
                          added -> {
                            if (!viewers.contains(added.getArgument(0)))
                              viewers.add(added.getArgument(0));
                            return null;
                          })
                      .when(bar)
                      .addPlayer(any(Player.class));
                  doAnswer(
                          removed -> {
                            viewers.remove(removed.getArgument(0));
                            return null;
                          })
                      .when(bar)
                      .removePlayer(any(Player.class));
                  doAnswer(
                          ignored -> {
                            viewers.clear();
                            return null;
                          })
                      .when(bar)
                      .removeAll();
                  doAnswer(
                          changed -> {
                            title[0] = changed.getArgument(0);
                            return null;
                          })
                      .when(bar)
                      .setTitle(anyString());
                  doAnswer(
                          changed -> {
                            progress[0] = changed.getArgument(0);
                            return null;
                          })
                      .when(bar)
                      .setProgress(anyDouble());
                  doAnswer(
                          changed -> {
                            visible[0] = changed.getArgument(0);
                            return null;
                          })
                      .when(bar)
                      .setVisible(anyBoolean());
                  bars.add(bar);
                  return bar;
                });
        ScoreboardManager scoreboards = mock(ScoreboardManager.class);
        when(scoreboards.getMainScoreboard()).thenReturn(mock(Scoreboard.class));
        when(Bukkit.getScoreboardManager()).thenReturn(scoreboards);
        when(navigation.createBackButton(any())).thenAnswer(call -> new ItemStack(Material.ARROW));
        navigation.campaignView = mock(CampaignView.class);
        navigation.campaignRaidLaunchView = raidView;
        json = mockStatic(JsonUtil.class, CALLS_REAL_METHODS);
        json.when(() -> JsonUtil.writeJson(any(File.class), any()))
            .thenAnswer(
                call -> {
                  writes.add(new Write(call.getArgument(0), call.getArgument(1)));
                  return null;
                });
        json.when(() -> JsonUtil.writeJsonAtomic(any(File.class), any()))
            .thenAnswer(
                call -> {
                  writes.add(new Write(call.getArgument(0), call.getArgument(1)));
                  return null;
                });
        tlibs = mockStatic(TLibs.class);
        ItemAPI itemApi = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        tlibs.when(TLibs::getItemAPI).thenReturn(itemApi);
        when(itemApi.getCreator().getItemsAdderItem(anyString()))
            .thenAnswer(call -> new ItemStack(Material.PAPER));
        YamlConfiguration installationConfig = new YamlConfiguration();
        installationConfig.set("consent-proximity-blocks", 20);
        installationConfig.set("transfer-request-timeout-seconds", 60);
        for (InstallationKind kind : InstallationKind.values()) {
          String key = kind.getCommandName();
          installationConfig.set(key + ".daily-upkeep", 2);
          installationConfig.set(key + ".construction-time", 60);
          installationConfig.set(key + ".radius", 30);
          installationConfig.createSection(key + ".slots");
        }
        var installationFile = Files.createTempFile("campaign-lifecycle-installations-", ".yml");
        try {
          installationConfig.save(installationFile.toFile());
          InstallationConfigLoader.load(installationFile.toFile());
        } finally {
          Files.deleteIfExists(installationFile);
        }
        source = install(attacker, "lifecycle_source", InstallationKind.PORT, 10);
        target = install(defender, "lifecycle_target", InstallationKind.PORT, 20);
        YamlConfiguration config = new YamlConfiguration();
        config.set("type", "raid");
        config.set("campaign_raid", true);
        config.set("keep_inventory", true);
        config.set("defender_respawn_mode", "infinite");
        BattleTemplateLoader.putForTests(
            new BattleTemplate(Cache.battleCampaignTemplateRaid, config));
      } catch (Exception | Error failure) {
        try {
          close();
        } catch (Exception | Error cleanup) {
          failure.addSuppressed(cleanup);
        }
        throw failure;
      }
    }

    public Player player(String name) {
      Player player = domain.player(name);
      when(player.getScoreboard()).thenReturn(mock(Scoreboard.class));
      return player;
    }

    public Instant now() {
      return CampaignClock.now();
    }

    public void time(Instant target) {
      CampaignClock.reset();
      CampaignClock.add(Duration.between(Instant.now(), target));
    }

    public Installation install(Faction faction, String id, InstallationKind kind, int province) {
      Installation installation =
          new Installation(id, id, kind, province, province * 10, province * 10, 0L);
      faction.getInstallationHandler().acceptTransferred(installation);
      (faction == attacker ? war.getOccupiedByAttacker() : war.getOccupiedByDefender())
          .add(province);
      return installation;
    }

    public Inventory top(Player player) {
      return player.getOpenInventory().getTopInventory();
    }

    public int slotNamed(Inventory menu, String name) {
      for (int slot = 0; slot < menu.getSize(); slot++) {
        ItemStack item = menu.getItem(slot);
        if (item != null
            && !item.isEmpty()
            && item.getItemMeta().hasDisplayName()
            && ChatColor.stripColor(item.getItemMeta().getDisplayName()).contains(name))
          return slot;
      }
      return -1;
    }

    public long warWrites() {
      return writes.stream().filter(write -> write.value() instanceof WarData).count();
    }

    public void remember(Class<?> type, String name) throws Exception {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      oldFields.put(field, field.get(null));
    }

    private void set(Class<?> type, String name, Object value) throws Exception {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      field.set(null, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void snapshotMap(Class<?> type, String name) throws Exception {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      Map map = (Map) field.get(null), old = new LinkedHashMap(map);
      map.clear();
      restoreContents.add(
          () -> {
            map.clear();
            map.putAll(old);
          });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void snapshotSet(Class<?> type, String name) throws Exception {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      Set set = (Set) field.get(null), old = new LinkedHashSet(set);
      set.clear();
      restoreContents.add(
          () -> {
            set.clear();
            set.addAll(old);
          });
    }

    @Override
    public void close() throws Exception {
      try {
        for (Battle battle : new ArrayList<>(BattleManager.get())) battle.end();
        CampaignRaidMusterScheduler.cancelAllScheduled();
        CampaignRaidFightScheduler.cancelAllScheduled();
        CampaignRaidBossBarService.resetForTests();
      } finally {
        try {
          restoreContents.forEach(Runnable::run);
          for (var entry : oldFields.entrySet()) entry.getKey().set(null, entry.getValue());
          CampaignClock.reset();
          CampaignClock.add(oldOffset);
          BattleManager.get().clear();
          BattleManager.get().addAll(oldBattles);
          WarbandManager.get().clear();
          WarbandManager.get().addAll(oldBands);
          BattleManager.currentBattle.clear();
          BattleManager.currentBattle.putAll(oldCurrent);
          BattleTemplateLoader.resetForTests();
          oldTemplates.values().forEach(BattleTemplateLoader::putForTests);
        } finally {
          try {
            if (tlibs != null) tlibs.close();
          } finally {
            try {
              if (json != null) json.close();
            } finally {
              domain.close();
            }
          }
        }
      }
    }

    public record Write(File file, Object value) {}
  }
}
