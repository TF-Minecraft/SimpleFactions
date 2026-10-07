package net.tfminecraft.simplefactions.war.campaign;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.BattleData;
import net.tfminecraft.simplefactions.database.MercenaryCompanyData;
import net.tfminecraft.simplefactions.database.MercenaryContractData;
import net.tfminecraft.simplefactions.database.WarbandData;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.CampaignInventoryHolder;
import net.tfminecraft.simplefactions.managers.inventory.CampaignCreator;
import net.tfminecraft.simplefactions.managers.inventory.CampaignInstallationPickView;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService.CampaignBattleContext;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandBattleService;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandLeaveBlock;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.core.*;
import net.tfminecraft.simplefactions.war.battle.enums.*;
import net.tfminecraft.simplefactions.war.battle.template.*;
import net.tfminecraft.simplefactions.war.battle.warband.*;
import net.tfminecraft.simplefactions.war.campaign.admin.WarScheduleAdminResult;
import net.tfminecraft.simplefactions.war.campaign.admin.WarScheduleAdminService;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignBattleEndService;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidLaunchService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.enums.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class CampaignBattleLifecycleCoverageTest {
  private Fixture rig;
  private CampaignInstallationPickView picks;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    picks = new CampaignInstallationPickView(rig.navigation);
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 10));
  }

  @AfterEach
  void cleanup() throws Exception {
    if (rig != null) rig.close();
  }

  @Test
  void everyEligibleInstallationRemainsAccessibleWhenThePickListExceedsOnePage() {
    for (int i = 0; i < 34; i++)
      rig.install(rig.attacker, "pick_extra_" + i, InstallationKind.PORT, 100 + i);
    picks.open(rig.alice, rig.war, rig.attacker);
    Set<String> reached = new LinkedHashSet<>();
    for (int page = 0; page < 10; page++) {
      Inventory menu = rig.top(rig.alice);
      for (ItemStack item : menu.getContents()) {
        if (item == null || item.isEmpty()) continue;
        String id =
            item.getItemMeta()
                .getPersistentDataContainer()
                .get(CampaignCreator.installationPickIdKey(), PersistentDataType.STRING);
        if (id != null) reached.add(id);
      }
      int next = rig.slotNamed(menu, "Next");
      if (next < 0) break;
      picks.click(rig.domain.ui.click(rig.alice, next), menu, rig.alice);
    }
    assertEquals(35, reached.size(), "All controllable installations must remain selectable");
  }

  @Test
  void committingAndRemovingAnInstallationUpdatesItsGlowAndPersistsExactlyOnce() {
    picks.open(rig.alice, rig.war, rig.attacker);
    Inventory first = rig.top(rig.alice);
    assertEquals(Set.of(), BattleInstallationPickService.getPicks(rig.war, rig.attacker.getId()));
    long before = rig.warWrites();
    click(12);
    assertEquals(
        Set.of(rig.source.getId()),
        BattleInstallationPickService.getPicks(rig.war, rig.attacker.getId()));
    assertTrue(rig.top(rig.alice).getItem(12).getItemMeta().hasEnchants());
    assertEquals(before + 1, rig.warWrites());
    click(12);
    assertTrue(BattleInstallationPickService.getPicks(rig.war, rig.attacker.getId()).isEmpty());
    assertFalse(rig.top(rig.alice).getItem(12).getItemMeta().hasEnchants());
    assertEquals(before + 2, rig.warWrites());
    verify(rig.alice).sendMessage("§aCommitted " + rig.source.getName() + " for this battle.");
    verify(rig.alice).sendMessage("§7Uncommitted " + rig.source.getName() + ".");
  }

  @Test
  void refreshingPicksReusesTheMenuAndClearsLostInstallationsWithoutReopening() {
    picks.open(rig.alice, rig.war, rig.attacker);
    Inventory menu = rig.top(rig.alice);
    rig.attacker.getInstallationHandler().detachOnProvince(10);
    clearInvocations(rig.alice);
    picks.open(rig.alice, rig.war, rig.attacker, false, menu);
    picks.open(rig.alice, rig.war, rig.attacker, false);
    assertTrue(menu.getItem(12).isEmpty());
    assertSame(menu, rig.top(rig.alice));
    verify(rig.alice, never()).openInventory(any(Inventory.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "ended", "faction", "outsider"})
  void pickMenuOpeningRequiresAnActiveParticipatingFaction(String reason) {
    Faction faction =
        reason.equals("faction")
            ? null
            : reason.equals("outsider") ? rig.domain.saved("pick_outsider", "Guest") : rig.attacker;
    if (reason.equals("ended")) rig.war.end(WarEndReason.ADMIN_END);
    picks.open(rig.alice, reason.equals("missing") ? null : rig.war, faction);
    verify(rig.alice)
        .sendMessage(
            reason.equals("outsider")
                ? "§cYou are not a belligerent in this war."
                : "§cWar not found.");
    verify(rig.alice, never()).openInventory(any(Inventory.class));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"missing_war", "ended", "outsider", "member", "locked", "removed", "battle_day"})
  void deniedPickClicksRetainOwnershipAndExistingSelections(String reason) {
    picks.open(rig.alice, rig.war, rig.attacker);
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
    if (reason.equals("locked")) rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 17));
    if (reason.equals("removed")) rig.attacker.getInstallationHandler().detachOnProvince(10);
    if (reason.equals("battle_day")) rig.war.setBattleDay(null);
    long before = rig.warWrites();
    click(12);
    assertTrue(rig.war.getBattleInstallationPicks().values().stream().allMatch(Set::isEmpty));
    assertEquals(before, rig.warWrites());
    assertSame(rig.target, rig.defender.getInstallationHandler().getById(rig.target.getId()));
    verify(rig.alice, never())
        .sendMessage("§aCommitted " + rig.source.getName() + " for this battle.");
  }

  @Test
  void theDefendersMandatoryNavalPortCannotBeUncommitted() {
    rig.war.setCampaignBattleSchedule(
        List.of(
            new ScheduledCampaignBattle(
                20, CampaignBattleKind.NAVAL, true, null, rig.target.getId())));
    picks.open(rig.bob, rig.war, rig.defender);
    assertEquals(
        Set.of(rig.target.getId()),
        BattleInstallationPickService.getPicks(rig.war, rig.defender.getId()));
    picks.click(rig.domain.ui.click(rig.bob, 12), rig.top(rig.bob), rig.bob);
    assertEquals(
        Set.of(rig.target.getId()),
        BattleInstallationPickService.getPicks(rig.war, rig.defender.getId()));
    verify(rig.bob).sendMessage("§cThe ZOC port is required for this naval battle.");
  }

  @Test
  void unrelatedOrMalformedPickItemsDoNotMutateWarStateAndBackReturnsToCampaign() {
    picks.open(rig.alice, rig.war, rig.attacker);
    Inventory real = rig.top(rig.alice);
    Inventory unrelated = rig.domain.ui.inventory(null, 9, "Other");
    InventoryClickEvent event = rig.domain.ui.click(rig.alice, 12);
    picks.click(event, unrelated, rig.alice);
    assertFalse(event.isCancelled());
    Inventory anotherType =
        rig.domain.ui.inventory(
            new CampaignInventoryHolder(rig.war.getId(), SFGUI.CAMPAIGN_VIEW), 54, "Campaign");
    picks.click(event, anotherType, rig.alice);
    assertFalse(event.isCancelled());
    real.setItem(12, null);
    click(12);
    real.setItem(12, new ItemStack(Material.PAPER));
    click(12);
    ItemStack item =
        new CampaignCreator()
            .createInstallationPickToggleItem(rig.war, rig.source, false, false, false);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(CampaignCreator.installationPickWarKey(), PersistentDataType.INTEGER, -1);
    item.setItemMeta(meta);
    real.setItem(12, item);
    click(12);
    assertTrue(rig.war.getBattleInstallationPicks().isEmpty());
    click(53);
    verify(rig.navigation.campaignView).campaignView(rig.alice, rig.war, true);
  }

  @Test
  void leavingOneWarbandDoesNotEliminateAStillPopulatedCampaignSide() {
    Battle battle = battle(BattleType.FIELD, true);
    Warband leaving = battle.getSideById("attacker").getBands().getFirst();
    Player ally = rig.player("Ally");
    rig.attacker.addMember("Ally");
    Warband remaining =
        Warband.createCampaignSideShell(
            "lifecycle_second_band", rig.war, rig.war.getAttackers(), "attacker");
    remaining.addPlayer(ally);
    remaining.setLeaderId(ally.getUniqueId());
    WarbandManager.addWarband(remaining);
    BattleSide side = battle.getSideById("attacker");
    side.addBand(remaining);
    int lives = side.getLives();

    CampaignWarbandBattleService.processLeave(rig.alice, leaving, true);

    assertTrue(battle.hasStarted(), "Other warbands remain on this side");
    assertEquals(lives, side.getLives());
    assertTrue(remaining.hasMember(ally));
    assertFalse(leaving.hasMember(rig.alice));
  }

  @ParameterizedTest
  @ValueSource(strings = {"left", "offline", "ended", "different_battle"})
  void delayedCampaignSpawnMenuRechecksWhetherTheNewMemberCanStillRespawn(String change) {
    Battle battle = battle(BattleType.FIELD, true);
    Warband band = battle.getSideById("attacker").getBands().getFirst();
    Player recruit = rig.player("Recruit");
    rig.attacker.addMember("Recruit");
    band.addPlayer(recruit);
    rig.domain.ui.tasks.clear();
    CampaignWarbandBattleService.onMemberJoined(
        recruit, band, new CampaignBattleContext(battle, "attacker", rig.war));
    assertEquals(1, rig.domain.ui.tasks.size());
    if (change.equals("left")) CampaignWarbandBattleService.processLeave(recruit, band, true);
    if (change.equals("offline")) when(recruit.isOnline()).thenReturn(false);
    if (change.equals("ended")) battle.end();
    if (change.equals("different_battle"))
      BattleManager.currentBattle.put(recruit, new Battle("other_campaign"));
    rig.domain.ui.runTasks();
    verify(recruit, never()).openInventory(any(Inventory.class));
  }

  @ParameterizedTest
  @EnumSource(
      value = BattleType.class,
      names = {"FIELD", "SIEGE"})
  void joiningAndLeavingAStartedBattleConservesRosterAndPromotesTheRemainingLeader(
      BattleType type) {
    Battle battle = battle(type, true);
    Warband band = battle.getSideById("attacker").getBands().getFirst();
    Player recruit = rig.player("Recruit");
    rig.attacker.addMember("Recruit");
    assertNull(
        CampaignWarbandBattleService.validateMidBattleJoin(
            rig.war, battle, "attacker", band, recruit.getName(), recruit.getUniqueId()));
    int lives = battle.getSideById("attacker").getLives();
    band.addPlayer(recruit);
    CampaignWarbandBattleService.onMemberJoined(
        recruit, band, new CampaignBattleContext(battle, "attacker", rig.war));
    assertEquals(lives - 1, battle.getSideById("attacker").getLives());
    assertSame(battle, BattleManager.currentBattle.get(recruit));
    verify(recruit).teleport(battle.getSideById("attacker").getSpawn());
    rig.domain.ui.runTasks();
    if (type == BattleType.FIELD) verify(recruit).openInventory(any(Inventory.class));
    CampaignWarbandBattleService.processLeave(rig.alice, band, true);
    assertFalse(band.hasMember(rig.alice));
    assertTrue(band.hasMember(recruit));
    assertEquals(recruit.getUniqueId(), band.getLeaderId());
    assertTrue(
        CampaignWarbandLeaveBlock.isBlocked(battle.getId(), band.getId(), rig.alice.getUniqueId()));
    assertTrue(rig.writes.stream().anyMatch(write -> write.value() instanceof BattleData));
    assertTrue(rig.writes.stream().anyMatch(write -> write.value() instanceof WarbandData));
  }

  @Test
  void pickPagingRetainsThePageOnToggleAndClampsAfterOwnershipChanges() {
    for (int i = 0; i < 34; i++)
      rig.install(rig.attacker, "page_pick_" + i, InstallationKind.PORT, 100 + i);
    picks.open(rig.alice, rig.war, rig.attacker);
    click(52);
    Inventory page = rig.top(rig.alice);
    assertEquals(1, ((CampaignInventoryHolder) page.getHolder()).getPage());
    String selected =
        page.getItem(12)
            .getItemMeta()
            .getPersistentDataContainer()
            .get(CampaignCreator.installationPickIdKey(), PersistentDataType.STRING);
    click(12);
    assertEquals(1, ((CampaignInventoryHolder) rig.top(rig.alice).getHolder()).getPage());
    assertTrue(
        BattleInstallationPickService.getPicks(rig.war, rig.attacker.getId()).contains(selected));
    click(45);
    assertEquals(0, ((CampaignInventoryHolder) rig.top(rig.alice).getHolder()).getPage());
    click(52);
    for (int i = 0; i < 34; i++) rig.attacker.getInstallationHandler().detachOnProvince(100 + i);
    page = rig.top(rig.alice);
    picks.open(rig.alice, rig.war, rig.attacker, false, page);
    assertEquals(0, ((CampaignInventoryHolder) page.getHolder()).getPage());
    assertEquals(
        rig.source.getId(),
        page.getItem(12)
            .getItemMeta()
            .getPersistentDataContainer()
            .get(CampaignCreator.installationPickIdKey(), PersistentDataType.STRING));
    assertTrue(page.getItem(13).isEmpty());
    assertEquals(-1, rig.slotNamed(page, "Next"));
    assertEquals(-1, rig.slotNamed(page, "Previous"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"visitor", "opposite", "side", "blocked", "no_lives"})
  void midBattleJoinRejectsIneligiblePlayersWithoutChangingTheRoster(String reason) {
    Battle battle = battle(BattleType.FIELD, true);
    Warband band = battle.getSideById("attacker").getBands().getFirst();
    Player recruit = rig.player("Recruit");
    if (!reason.equals("visitor"))
      (reason.equals("opposite") ? rig.defender : rig.attacker).addMember("Recruit");
    if (reason.equals("blocked"))
      CampaignWarbandLeaveBlock.block(battle.getId(), band.getId(), recruit.getUniqueId());
    if (reason.equals("no_lives")) battle.getSideById("attacker").setLives(0);
    String rejection =
        CampaignWarbandBattleService.validateMidBattleJoin(
            rig.war,
            battle,
            reason.equals("side") ? "missing" : "attacker",
            band,
            recruit.getName(),
            recruit.getUniqueId());
    assertNotNull(rejection);
    assertFalse(band.hasMember(recruit));
    assertEquals(1, band.getRealMemberCount());
    assertNull(BattleManager.currentBattle.get(recruit));
    assertEquals(
        switch (reason) {
          case "visitor" -> "You must be in a faction to join this campaign battle";
          case "blocked" -> "You cannot rejoin this warband for this battle";
          case "no_lives" -> "Cannot join: this side has no lives remaining in the battle";
          default -> "Your faction is not on this battle side";
        },
        rejection);
  }

  @Test
  void preBattleMembershipAndOptionalCallbacksLeaveCombatStateUntouched() {
    Battle battle = battle(BattleType.FIELD, false);
    Warband band = battle.getSideById("attacker").getBands().getFirst();
    assertNull(
        CampaignWarbandBattleService.validateMidBattleJoin(
            rig.war, battle, "attacker", band, "Alice", rig.alice.getUniqueId()));
    assertEquals(
        "Invalid campaign warband join",
        CampaignWarbandBattleService.validateMidBattleJoin(
            null, battle, "attacker", band, "Alice", rig.alice.getUniqueId()));
    CampaignWarbandBattleService.onMemberJoined(
        rig.alice, band, new CampaignBattleContext(battle, "attacker", rig.war));
    CampaignWarbandBattleService.onMemberJoined(null, band, null);
    CampaignWarbandBattleService.processLeave(null, band, true);
    assertTrue(band.hasMember(rig.alice));
    assertTrue(BattleManager.currentBattle.isEmpty());
    assertTrue(CampaignWarbandBattleService.isWarSideMainLeader(rig.war, band, "Alice"));
    assertFalse(CampaignWarbandBattleService.isWarSideMainLeader(rig.war, band, "Other"));
    assertFalse(CampaignWarbandBattleService.isWarSideMainLeader(null, band, "Alice"));
    assertFalse(
        CampaignWarbandBattleService.isWarSideMainLeader(
            rig.war, new Warband("manual", rig.bob), "Bob"));
    CampaignWarbandBattleService.processLeave(rig.alice, band, false);
    assertTrue(band.isPendingLeader());
    assertFalse(band.hasMember(rig.alice));
    assertFalse(
        CampaignWarbandLeaveBlock.isBlocked(battle.getId(), band.getId(), rig.alice.getUniqueId()));
  }

  @ParameterizedTest
  @EnumSource(
      value = BattleType.class,
      names = {"FIELD", "SIEGE"})
  void losingTheLastBandMemberEmptiesOnlyThatSideAndAppliesTheConfiguredGrace(BattleType type) {
    int previous = Cache.battleEmptySideGraceSeconds;
    try {
      Cache.battleEmptySideGraceSeconds = 0;
      Battle battle = battle(type, true);
      Warband band = battle.getSideById("attacker").getBands().getFirst();
      CampaignWarbandBattleService.processLeave(rig.alice, band, true);
      assertFalse(battle.hasStarted());
      assertTrue(band.isPendingLeader());
      assertEquals(0, battle.getSideById("attacker").getLives());
      assertTrue(battle.getSideById("defender").getBands().getFirst().hasMember(rig.bob));
    } finally {
      Cache.battleEmptySideGraceSeconds = previous;
    }
  }

  @Test
  void leavingAnUnattachedBandStillRemovesMembershipAndResetsItsLeader() {
    Warband unattached = new Warband("unattached", rig.alice);
    WarbandManager.addWarband(unattached);
    CampaignWarbandBattleService.processLeave(rig.alice, unattached, false);
    assertEquals(0, unattached.getRealMemberCount());
    assertTrue(unattached.isPendingLeader());
    assertTrue(BattleManager.currentBattle.isEmpty());
    assertTrue(BattleManager.get().isEmpty());
  }

  @Test
  void hiredMercenaryCannotJoinTheHostOpposingTheirActiveSavedContract() {
    Battle battle = battle(BattleType.FIELD, true);
    Warband band = battle.getSideById("attacker").getBands().getFirst();
    var guild = rig.attacker.getOrCreateMainGuild();
    MercenaryCompanyData data = new MercenaryCompanyData();
    data.name = "Hired Spears";
    data.formationRemaining = 0;
    data.enlisted = List.of("Alice");
    data.slots = "mercenary.1";
    MercenaryContractData contract = new MercenaryContractData();
    contract.id = "lifecycle_contract";
    contract.hirer = rig.defender.getId();
    contract.status = "ACTIVE";
    contract.kind = "MERCENARY";
    contract.slots = 1;
    contract.durationDays = 7;
    contract.issueDate = System.currentTimeMillis();
    contract.dueDate = contract.issueDate + java.time.Duration.ofDays(7).toMillis();
    data.contracts.add(contract);
    YamlConfiguration unit = new YamlConfiguration();
    unit.set("mercenary.item.material", "PAPER");
    unit.set("mercenary.default-slots", 1);
    MercenaryCompany company =
        new MercenaryCompany(
            guild, data, new Regiment("mercenary", unit.getConfigurationSection("mercenary")));
    guild.setCompany(company);
    assertTrue(company.isFormed());
    assertEquals(1, company.getContractHandler().getActive().size());
    int lives = battle.getSideById("attacker").getLives();

    assertEquals(
        "You are under contract to the other host",
        CampaignWarbandBattleService.validateMidBattleJoin(
            rig.war, battle, "attacker", band, "Alice", rig.alice.getUniqueId()));

    assertEquals(lives, battle.getSideById("attacker").getLives());
    assertTrue(band.hasMember(rig.alice));
    assertEquals(1, company.getContractHandler().getActive().size());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void joiningAnActiveRaidRespectsTeleportWithoutSpendingRegularBattleLives(boolean teleport) {
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 19).plusSeconds(120));
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    CampaignRaid raid = CampaignRaidService.getActive(rig.war);
    CampaignRaidWarbandService.signupAttacker(rig.war, raid, rig.alice.getUniqueId(), "Alice");
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    battle.setTeleport(teleport);
    Player late = rig.player("LateDefender");
    rig.defender.addMember(late.getName());
    CampaignRaidWarbandService.signupDefender(rig.war, raid, late.getUniqueId(), late.getName());
    Warband band = CampaignRaidWarbandService.getDefenderWarband(raid);
    int lives = battle.getSideById("defender").getLives();
    rig.domain.ui.tasks.clear();

    CampaignWarbandBattleService.onMemberJoined(
        late, band, new CampaignBattleContext(battle, "defender", rig.war));

    assertSame(battle, BattleManager.currentBattle.get(late));
    assertTrue(band.hasMember(late));
    if (teleport) verify(late).teleport(battle.getSideById("defender").getSpawn());
    else verify(late, never()).teleport(any(Location.class));
    assertEquals(lives, battle.getSideById("defender").getLives());
    assertTrue(rig.domain.ui.tasks.isEmpty());
  }

  @Test
  void aRemovedSideInvalidatesAPreviouslyCapturedJoinContext() {
    Battle battle = battle(BattleType.FIELD, true);
    Warband band = battle.getSideById("attacker").getBands().getFirst();
    Player recruit = rig.player("Recruit");
    rig.attacker.addMember("Recruit");
    band.addMember(recruit.getUniqueId());
    CampaignBattleContext context = new CampaignBattleContext(battle, "attacker", rig.war);
    battle.removeSide("attacker");
    rig.domain.ui.tasks.clear();

    CampaignWarbandBattleService.onMemberJoined(recruit, band, context);

    assertNull(BattleManager.currentBattle.get(recruit));
    verify(recruit, never()).teleport(any(Location.class));
    assertTrue(rig.domain.ui.tasks.isEmpty());
    assertTrue(band.hasMember(recruit));
    assertEquals(List.of("defender"), battle.getSides().stream().map(BattleSide::getId).toList());
  }

  @Test
  void leavingAnUnattachedBandDoesNotTouchOtherBattlesOrTheirRoster() {
    Battle other = battle(BattleType.FIELD, true);
    Player recruit = rig.player("Recruit");
    Warband unattached = new Warband("unattached", recruit);
    WarbandManager.addWarband(unattached);
    int lives = other.getSideById("attacker").getLives();
    var before = new java.util.HashMap<>(BattleManager.currentBattle);

    CampaignWarbandBattleService.processLeave(recruit, unattached, false);

    assertEquals(0, unattached.getRealMemberCount());
    assertTrue(unattached.isPendingLeader());
    assertNull(BattleManager.currentBattle.get(recruit));
    assertEquals(before, BattleManager.currentBattle);
    assertTrue(other.hasStarted());
    assertEquals(lives, other.getSideById("attacker").getLives());
    assertTrue(other.getSideById("attacker").getBands().getFirst().hasMember(rig.alice));
  }

  private void click(int slot) {
    picks.click(rig.domain.ui.click(rig.alice, slot), rig.top(rig.alice), rig.alice);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void administrativeScheduleOperationsRejectMissingOrEndedWarsWithoutMutation(boolean ended) {
    if (ended) rig.war.end(WarEndReason.ADMIN_END);
    var war = ended ? rig.war : null;
    long writes = rig.warWrites();
    String expected = ended ? "War is not active." : "War not found.";
    assertEquals(WarScheduleAdminResult.error(expected), WarScheduleAdminService.openVote(war));
    assertEquals(
        WarScheduleAdminResult.error(expected), WarScheduleAdminService.closeVote(war, rig.now()));
    assertEquals(WarScheduleAdminResult.error(expected), WarScheduleAdminService.skipDay(war));
    assertEquals(
        WarScheduleAdminResult.error(expected), WarScheduleAdminService.castVote(war, 21, "both"));
    assertEquals(WarScheduleAdminResult.error(expected), WarScheduleAdminService.forceQuorum(war));
    assertEquals(
        WarScheduleAdminResult.error(expected),
        WarScheduleAdminService.setScheduled(war, rig.now().toString()));
    assertEquals(WarScheduleAdminResult.error(expected), WarScheduleAdminService.battleCreate(war));
    assertEquals(WarScheduleAdminResult.error(expected), WarScheduleAdminService.battleDelete(war));
    assertEquals(WarScheduleAdminResult.error(expected), WarScheduleAdminService.battleStart(war));
    assertEquals(
        WarScheduleAdminResult.error(expected),
        WarScheduleAdminService.winBattle(war, BelligerentRole.ATTACKER));
    assertEquals(
        WarScheduleAdminResult.error(expected), WarScheduleAdminService.battleChoice(war, "hold"));
    assertEquals(writes, rig.warWrites());
    assertTrue(BattleManager.get().isEmpty());
    assertTrue(WarbandManager.get().isEmpty());
    assertSame(rig.target, rig.defender.getInstallationHandler().getById(rig.target.getId()));
  }

  @Test
  void incompleteAdministrativeArgumentsPreserveTheCurrentCampaign() throws Exception {
    configureCampaign();
    assertFalse(WarScheduleAdminService.setScheduled(rig.war, " ").success());
    assertFalse(WarScheduleAdminService.setScheduled(rig.war, "bad-instant").success());
    assertFalse(
        WarScheduleAdminService.setScheduled(
                rig.war, BattleWindowService.atScheduleHour(Fixture.DAY, 10).toString())
            .success());
    assertFalse(WarScheduleAdminService.winBattle(rig.war, null).success());
    assertFalse(WarScheduleAdminService.battleChoice(rig.war, "").success());
    assertFalse(WarScheduleAdminService.battleChoice(rig.war, "invalid").success());
    assertFalse(WarScheduleAdminService.battleDelete(rig.war).success());
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    assertFalse(WarScheduleAdminService.closeVote(rig.war, rig.now()).success());
    rig.war.setBattleDay(null);
    assertFalse(WarScheduleAdminService.skipDay(rig.war).success());
    assertTrue(BattleManager.get().isEmpty());
    assertEquals(0, rig.war.getCampaignBattlesFought());
    assertEquals(4, rig.war.getInitiativeAttacker());
    assertEquals(4, rig.war.getInitiativeDefender());
  }

  @Test
  void closingAForcedRealVoteSchedulesOneBattleAndRepeatedCreateRetainsIt() throws Exception {
    configureCampaign();
    assertTrue(WarScheduleAdminService.castVote(rig.war, 21, "both").success());
    assertTrue(WarScheduleAdminService.forceQuorum(rig.war).success());
    WarScheduleAdminResult result = WarScheduleAdminService.closeVote(rig.war, rig.now());
    assertTrue(result.success(), result.message());
    assertTrue(result.message().contains("scheduled battle"), result.message());
    assertEquals(BattleSchedulePhase.SCHEDULED, rig.war.getBattleSchedulePhase());
    assertEquals(
        BattleWindowService.computeScheduledBattleAt(Fixture.DAY, 21),
        rig.war.getScheduledBattleAt());
    assertEquals(20, rig.war.getScheduledBattleProvinceId());
    assertFalse(rig.war.isForceQuorumNextClose());
    Battle prepared = BattleManager.getByWarId(rig.war.getId());
    assertNotNull(prepared);
    result = WarScheduleAdminService.battleCreate(rig.war);
    assertTrue(result.success(), result.message());
    assertTrue(result.message().contains("already exists"));
    var originalBands = List.copyOf(WarbandManager.get());
    assertEquals(2, originalBands.size());
    assertTrue(WarScheduleAdminService.battleCreate(rig.war).success());
    assertSame(prepared, BattleManager.getByWarId(rig.war.getId()));
    assertEquals(originalBands, WarbandManager.get());
    assertEquals(1, BattleManager.get().size());
  }

  @Test
  void insufficientVotesPostponeTheCampaignAndUnresolvedChoiceBlocksClosing() throws Exception {
    configureCampaign();
    WarScheduleAdminResult postponed = WarScheduleAdminService.closeVote(rig.war, rig.now());
    assertTrue(postponed.success(), postponed.message());
    assertTrue(postponed.message().contains("postponed"));
    assertEquals(Fixture.DAY.plusDays(1), rig.war.getBattleDay());
    assertEquals(1, rig.war.getPostponementsThisCycle());
    assertTrue(BattleManager.get().isEmpty());
    rig.war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
    rig.war.setPostBattleWinnerCoalition(CampaignCoalition.AGGRESSOR);
    rig.war.setPostBattleChoiceResolved(false);
    WarScheduleAdminResult blocked = WarScheduleAdminService.closeVote(rig.war, rig.now());
    assertFalse(blocked.success());
    assertTrue(blocked.message().contains("choice unresolved"));
    assertEquals(1, rig.war.getPostponementsThisCycle());
  }

  @Test
  void startingWithoutANextProvinceReturnsAnErrorWithoutCreatingPartialState() throws Exception {
    configureCampaign();
    rig.war.setInitiativeAttacker(0);
    assertFalse(WarScheduleAdminService.winBattle(rig.war, BelligerentRole.ATTACKER).success());
    WarScheduleAdminResult result = WarScheduleAdminService.battleStart(rig.war);
    assertFalse(result.success());
    assertTrue(result.message().contains("No campaign battle"));
    assertTrue(BattleManager.get().isEmpty());
    assertTrue(WarbandManager.get().isEmpty());
    assertEquals(0, rig.war.getCampaignBattlesFought());
  }

  @Test
  void loadedWarbandsWithAnUnknownCampaignSideNeverGrantMainLeaderAuthority() {
    Warband loaded =
        Warband.fromPersistence(
            "legacy_side",
            "Legacy side",
            rig.alice.getUniqueId(),
            List.of(rig.alice.getUniqueId()),
            List.of(),
            false,
            true,
            "removed_side");
    WarbandManager.addWarband(loaded);
    assertFalse(CampaignWarbandBattleService.isWarSideMainLeader(rig.war, loaded, "Alice"));
    assertEquals(rig.alice.getUniqueId(), loaded.getLeaderId());
    assertEquals(Set.of(rig.alice.getUniqueId()), loaded.getMemberIds());
  }

  @ParameterizedTest
  @ValueSource(strings = {"province", "missing_template", "wrong_template"})
  void resettingWithoutAReplacementMustPreserveTheExistingBattleAndBands(String missing)
      throws Exception {
    configureCampaign();
    assertTrue(WarScheduleAdminService.battleCreate(rig.war).success());
    Battle existing = BattleManager.getByWarId(rig.war.getId());
    var bands = List.copyOf(WarbandManager.get());
    if (missing.equals("province")) rig.war.setInitiativeAttacker(0);
    else if (missing.equals("missing_template"))
      Cache.battleCampaignTemplateField = "unknown_template";
    else {
      YamlConfiguration template = new YamlConfiguration();
      template.set("type", "siege");
      net.tfminecraft.simplefactions.loaders.BattleTemplateLoader.putForTests(
          new BattleTemplate("wrong_field_template", template));
      Cache.battleCampaignTemplateField = "wrong_field_template";
    }
    WarScheduleAdminResult result = WarScheduleAdminService.battleDelete(rig.war);
    assertFalse(result.success());
    assertAll(
        () -> assertSame(existing, BattleManager.getByWarId(rig.war.getId())),
        () -> assertEquals(bands, WarbandManager.get()),
        () -> assertFalse(existing.hasStarted()));
  }

  @Test
  void aSecondFailedVoteAutoresolvesARealCampaignBattleWithoutStartingALiveBattle()
      throws Exception {
    configureCampaign();
    rig.war.setPostponementsThisCycle(Cache.warBattleVotingMaxPostponements);
    WarScheduleAdminResult result = WarScheduleAdminService.closeVote(rig.war, rig.now());
    assertTrue(result.success(), result.message());
    assertTrue(result.message().contains("autoresolved"), result.message());
    assertEquals(1, rig.war.getCampaignBattlesFought());
    assertTrue(BattleManager.get().isEmpty());
    assertTrue(rig.war.hasFirstBattleStarted());
  }

  @Test
  void administrativeWinAppliesAnActualOutcomeAndRefusesAnotherUntilChoiceIsResolved()
      throws Exception {
    configureCampaign();
    prepareArmies();
    rig.attacker.addTitle(rig.domain.title("attacker_lands", "county", 5, 10));
    rig.defender.addTitle(rig.domain.title("defender_lands", "county", 20, 30));
    rig.attacker.addProvince(5);
    rig.attacker.addProvince(10);
    rig.defender.addProvince(20);
    rig.defender.addProvince(30);
    WarScheduleAdminResult result =
        WarScheduleAdminService.winBattle(rig.war, BelligerentRole.ATTACKER);
    assertTrue(result.success(), result.message());
    assertTrue(result.message().contains("Applied attacker win at province 20"), result.message());
    assertEquals(1, rig.war.getCampaignBattlesFought());
    assertTrue(rig.war.getOccupiedByAttacker().contains(20));
    assertEquals(CampaignCoalition.AGGRESSOR, rig.war.getPostBattleWinnerCoalition());
    assertFalse(rig.war.isPostBattleChoiceResolved());
    WarScheduleAdminResult repeated =
        WarScheduleAdminService.winBattle(rig.war, BelligerentRole.DEFENDER);
    assertFalse(repeated.success());
    assertTrue(repeated.message().contains("choice pending"));
    assertEquals(1, rig.war.getCampaignBattlesFought());
  }

  @ParameterizedTest
  @ValueSource(strings = {"push", "hold", "attack", "accept"})
  void administrativePostBattleChoicesApplyTheActualCampaignTransition(String choice)
      throws Exception {
    configureCampaign();
    prepareArmies();
    CampaignBattleEndService.beginPostBattleChoice(rig.war, CampaignCoalition.AGGRESSOR);
    if (choice.equals("attack") || choice.equals("accept")) {
      assertTrue(WarScheduleAdminService.battleChoice(rig.war, "hold").success());
      assertTrue(rig.war.isHoldPeaceProposalActive());
    }
    WarScheduleAdminResult result = WarScheduleAdminService.battleChoice(rig.war, choice);
    assertTrue(result.success(), result.message());
    switch (choice) {
      case "push" -> {
        assertEquals(3, rig.war.getCursorIndex());
        assertTrue(rig.war.isPostBattleChoiceResolved());
        assertEquals(BattleSchedulePhase.VOTING, rig.war.getBattleSchedulePhase());
      }
      case "hold" -> {
        assertEquals(PostBattleChoicePhase.LOSER_ATTACK_PEACE, rig.war.getPostBattleChoicePhase());
        assertTrue(rig.war.isHoldPeaceProposalActive());
        assertFalse(rig.war.isPostBattleChoiceResolved());
      }
      case "attack" -> {
        assertEquals(BelligerentRole.DEFENDER, rig.war.getInitiativeHolder());
        assertFalse(rig.war.isHoldPeaceProposalActive());
        assertTrue(rig.war.isPostBattleChoiceResolved());
      }
      case "accept" -> {
        assertFalse(rig.war.isActive());
        assertEquals(WarEndReason.WHITE_PEACE, rig.war.getEndReason());
        assertNull(WarManager.getById(rig.war.getId()));
        assertTrue(result.message().contains("white peace"));
      }
      default -> fail("Unexpected case");
    }
  }

  private void prepareArmies() {
    YamlConfiguration unit = new YamlConfiguration();
    unit.set("infantry.item.material", "PAPER");
    unit.set("infantry.default-slots", 5);
    unit.set("infantry.offense", true);
    rig.attacker
        .getMilitary()
        .getRegiments()
        .add(new Regiment("infantry", unit.getConfigurationSection("infantry")));
    rig.defender
        .getMilitary()
        .getRegiments()
        .add(new Regiment("infantry", unit.getConfigurationSection("infantry")));
  }

  private void configureCampaign() throws Exception {
    for (String name :
        List.of(
            "warFirstBattleAtBorder",
            "warBattleWindowStartHour",
            "warBattleWindowEndHour",
            "warBattleVotingMaxPostponements",
            "warBattleVotingMinPlayers",
            "warBattleVotingRequireSmallestSideFull",
            "warBattleVotingPassIfEither",
            "warBattleVotingDevMinPlayersEnabled",
            "battleCampaignTemplateField")) rig.remember(Cache.class, name);
    Cache.warFirstBattleAtBorder = true;
    Cache.warBattleWindowStartHour = 20;
    Cache.warBattleWindowEndHour = 24;
    Cache.warBattleVotingMaxPostponements = 1;
    Cache.warBattleVotingMinPlayers = 4;
    Cache.warBattleVotingRequireSmallestSideFull = true;
    Cache.warBattleVotingPassIfEither = true;
    Cache.warBattleVotingDevMinPlayersEnabled = false;
    Cache.battleCampaignTemplateField = "";
    rig.war.setObjectiveProvinceId(30);
    rig.war.setCampaignStartProvinceId(20);
    rig.war.setCampaignProvinces(List.of(5, 10, 20, 30));
    rig.war.setCursorIndex(2);
    rig.war.setInitiativeAttacker(4);
    rig.war.setInitiativeDefender(4);
    rig.war.setInitiativeHolder(BelligerentRole.ATTACKER);
    rig.war.setCampaignPhase(CampaignPhase.INVASION);
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
  }

  private Battle battle(BattleType type, boolean start) {
    YamlConfiguration unit = new YamlConfiguration();
    unit.set("infantry.item.material", "PAPER");
    unit.set("infantry.default-slots", 5);
    unit.set("infantry.offense", true);
    rig.attacker
        .getMilitary()
        .getRegiments()
        .add(new Regiment("infantry", unit.getConfigurationSection("infantry")));
    rig.defender
        .getMilitary()
        .getRegiments()
        .add(new Regiment("infantry", unit.getConfigurationSection("infantry")));
    Battle battle =
        BattleFactory.createBlank(
            type, "lifecycle_" + type.name().toLowerCase(java.util.Locale.ROOT));
    battle.setWarId(rig.war.getId());
    battle.setProvinceId(20);
    battle.setTeleport(true);
    battle.setLootEnabled(false);
    Warband attackers =
        Warband.createCampaignSideShell(
            "lifecycle_field_attackers", rig.war, rig.war.getAttackers(), "attacker");
    Warband defenders =
        Warband.createCampaignSideShell(
            "lifecycle_field_defenders", rig.war, rig.war.getDefenders(), "defender");
    attackers.addPlayer(rig.alice);
    attackers.setLeaderId(rig.alice.getUniqueId());
    defenders.addPlayer(rig.bob);
    defenders.setLeaderId(rig.bob.getUniqueId());
    WarbandManager.addWarband(attackers);
    WarbandManager.addWarband(defenders);
    battle.getSideById("attacker").addBand(attackers);
    battle.getSideById("defender").addBand(defenders);
    for (BattleSide side : battle.getSides()) {
      side.setSpawn(new Location(rig.domain.ui.world, 100, 65, 100));
      side.setJail(new Location(rig.domain.ui.world, 200, 65, 200));
    }
    if (type == BattleType.FIELD)
      battle
          .getPoints()
          .add(
              new CapturePoint(
                  "lifecycle_point",
                  new Location(rig.domain.ui.world, 120, 65, 120),
                  battle.getSideById("attacker"),
                  100));
    if (type == BattleType.SIEGE)
      battle.setContestArea(
          new ContestArea(
              BattleLocation.fromBukkitLocation(new Location(rig.domain.ui.world, 100, 60, 100)),
              BattleLocation.fromBukkitLocation(new Location(rig.domain.ui.world, 110, 70, 110))));
    BattleManager.addBattle(battle);
    if (start) {
      assertNull(battle.start());
      assertTrue(battle.hasStarted());
    }
    return battle;
  }
}
