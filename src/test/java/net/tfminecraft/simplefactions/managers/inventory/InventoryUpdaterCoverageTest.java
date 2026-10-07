package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.espionage.EspionageAccess;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.government.election.Candidate;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.CampaignInventoryHolder;
import net.tfminecraft.simplefactions.managers.holder.CampaignRaidLaunchHolder;
import net.tfminecraft.simplefactions.managers.holder.DeclareWarHolder;
import net.tfminecraft.simplefactions.managers.holder.SFCombinedInventoryHolder;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.managers.holder.WarInventoryHolder;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.war.battle.ui.BattleInventoryManager;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.War;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Periodic refresh contract: preserve snapshot screens, refresh live state, reject stale owners. */
class InventoryUpdaterCoverageTest {
  private GuiTestFixture ui;
  private Player player;
  private Faction faction;
  private Faction enemy;
  private Guild guild;
  private Movement movement;
  private War war;
  private Participant participant;
  private InventoryManager manager;
  private InventoryUpdater updater;
  private MockedConstruction<BattleInventoryManager> battleMenus;
  private final List<MockedStatic<?>> mocks = new ArrayList<>();
  private MockedStatic<FactionManager> factions;
  private MockedStatic<WarManager> wars;
  private MockedStatic<EspionageAccess> access;
  private MockedStatic<EspionageService> intelligence;
  private MockedStatic<TierLoader> tiers;

  private <T> MockedStatic<T> scoped(Class<T> type) {
    MockedStatic<T> result = mockStatic(type);
    mocks.add(result);
    return result;
  }

  @BeforeEach
  void setup() {
    ui = new GuiTestFixture();
    player = ui.player("Alice");
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(player));
    faction = mock(Faction.class);
    enemy = mock(Faction.class);
    guild = mock(Guild.class, RETURNS_DEEP_STUBS);
    movement = mock(Movement.class);
    war = mock(War.class);
    participant = mock(Participant.class);
    when(faction.getId()).thenReturn("home");
    when(enemy.getId()).thenReturn("enemy");
    when(guild.getId()).thenReturn("guild");
    when(guild.getName()).thenReturn("Artisans");
    when(guild.getFaction()).thenReturn(faction);
    when(movement.getFaction()).thenReturn(faction);
    when(war.isActive()).thenReturn(true);
    when(war.isParticipating(faction)).thenReturn(true);
    when(war.getParticipant(faction)).thenReturn(participant);
    factions = scoped(FactionManager.class);
    factions.when(() -> FactionManager.getByString("home")).thenReturn(faction);
    factions.when(() -> FactionManager.getByString("enemy")).thenReturn(enemy);
    factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(faction);
    factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(guild);
    factions.when(() -> FactionManager.getMovementById("movement")).thenReturn(movement);
    wars = scoped(WarManager.class);
    wars.when(() -> WarManager.getById(7)).thenReturn(war);
    access = scoped(EspionageAccess.class);
    access.when(() -> EspionageAccess.owner(any())).thenReturn(faction);
    intelligence = scoped(EspionageService.class);
    intelligence.when(() -> EspionageService.canViewExact(player, faction)).thenReturn(true);
    tiers = scoped(TierLoader.class);
    manager = mock(InventoryManager.class);
    manager.factionView = mock(FactionView.class);
    manager.guildView = mock(GuildView.class);
    manager.warView = mock(WarView.class);
    manager.mercenaryMarketView = mock(MercenaryMarketView.class);
    manager.governmentView = mock(GovernmentView.class);
    manager.taxView = mock(TaxView.class);
    manager.lawView = mock(LawView.class);
    manager.electionView = mock(ElectionView.class);
    manager.movementView = mock(MovementView.class);
    manager.declareWarView = mock(DeclareWarView.class);
    manager.campaignInstallationPickView = mock(CampaignInstallationPickView.class);
    manager.campaignRaidLaunchView = mock(CampaignRaidLaunchView.class);
    battleMenus = mockConstruction(BattleInventoryManager.class);
    updater = new InventoryUpdater(manager);
  }

  @AfterEach
  void cleanup() {
    battleMenus.close();
    for (int i = mocks.size() - 1; i >= 0; i--) mocks.get(i).close();
    ui.close();
  }

  private Inventory open(InventoryHolder holder, int size, String title) {
    Inventory inventory = ui.inventory(holder, size, title);
    player.openInventory(inventory);
    return inventory;
  }

  private Inventory sf(SFGUI type, String id, String secondary) {
    return open(new SFInventoryHolder(id, type, 2, true, secondary), 54, type.name());
  }

  @ParameterizedTest
  @EnumSource(value = SFGUI.class, names = {"FACTION_VIEW", "SPECIAL_POSITIONS", "SPYMASTER_VIEW", "FOREIGN_LEDGER_VIEW", "FOREIGN_POSITIONS_VIEW", "SPYMASTER_SETTINGS", "SPYMASTER_SELECT", "PLAYER_LEDGER_VIEW", "GUILD_VIEW", "GUILD_LIST", "FACTION_GUILDS", "LAW_PROPOSAL_SELECT", "LAW_SELECT", "FAVOUR_REPRESS_SELECT", "PROPOSALS", "CAUSES_VIEW", "CAUSE_VIEW", "LEDGER_VIEW", "COMPANY_VIEW"})
  void snapshotScreensAreNotRebuiltOnPeriodicRefresh(SFGUI type) {
    Inventory inventory = sf(type, "home", null);
    ItemStack snapshot = new ItemStack(Material.PAPER);
    inventory.setItem(0, snapshot);
    updater.updateInventory();
    assertSame(inventory, player.getOpenInventory().getTopInventory());
    assertSame(snapshot, inventory.getItem(0));
    verifyNoInteractions(manager, manager.factionView, manager.guildView, manager.governmentView, manager.lawView, manager.movementView);
  }

  @Test
  void lostViewingPermissionClosesMenusAndReportedSnapshotsRemainUnchanged() {
    Inventory inventory = sf(SFGUI.MILITARY_VIEW, "home", null);
    SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
    access.when(() -> EspionageAccess.denied(player, holder)).thenReturn(true);
    updater.updateInventory();
    verify(player).closeInventory();
    verifyNoInteractions(manager);
    access.when(() -> EspionageAccess.denied(player, holder)).thenReturn(false);
    player.openInventory(inventory);
    holder.markReported();
    intelligence.when(() -> EspionageService.canViewExact(player, faction)).thenReturn(false);
    updater.updateInventory();
    assertSame(inventory, player.getOpenInventory().getTopInventory());
    verifyNoInteractions(manager);
  }

  @ParameterizedTest
  @EnumSource(value = SFGUI.class, names = {"FACTION_LIST", "WAR_LIST", "MERCENARY_MARKET_LIST"})
  void globalListsRefreshWithoutResolvingAFaction(SFGUI type) {
    Inventory inventory = sf(type, "irrelevant", null);
    updater.updateInventory();
    switch (type) {
      case FACTION_LIST -> verify(manager.factionView).populateFactionList(inventory, player);
      case WAR_LIST -> verify(manager.warView).populateWarList(inventory);
      case MERCENARY_MARKET_LIST -> verify(manager.mercenaryMarketView).populateMarketList(inventory, player);
      default -> fail();
    }
    factions.verify(() -> FactionManager.getByString(anyString()), never());
  }

  @ParameterizedTest
  @EnumSource(value = SFGUI.class, names = {"UPGRADE_VIEW", "COMPANY_SLOTS_VIEW", "COMPANY_ROSTER_VIEW", "COMPANY_UPGRADE_VIEW", "CONTRACT_LIST_VIEW", "LOAN_MAIN_VIEW", "LOANS_GIVEN_VIEW", "LOANS_TAKEN_VIEW"})
  void guildScreensRefreshTheirLiveGuildAndPreserveInventory(SFGUI type) {
    Inventory inventory = sf(type, "guild", null);
    updater.updateInventory();
    switch (type) {
      case UPGRADE_VIEW -> verify(manager).upgradeView(player, guild, inventory);
      case COMPANY_SLOTS_VIEW -> verify(manager).companySlotsView(player, guild, inventory);
      case COMPANY_ROSTER_VIEW -> verify(manager).companyRosterView(player, guild, inventory);
      case COMPANY_UPGRADE_VIEW -> verify(manager).companyUpgradeView(player, guild, inventory);
      case CONTRACT_LIST_VIEW -> verify(manager).contractListView(player, guild, inventory);
      case LOAN_MAIN_VIEW -> verify(manager).loanMainView(player, guild, inventory);
      case LOANS_GIVEN_VIEW -> verify(manager).loansGivenView(player, guild, inventory);
      case LOANS_TAKEN_VIEW -> verify(manager).loansTakenView(player, guild, inventory);
      default -> fail();
    }
    factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(null);
    clearInvocations(manager);
    updater.updateInventory();
    verifyNoInteractions(manager);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void loanDetailsRefreshTheCorrectTakenOrIssuedDirection(boolean taken) {
    Loan loan = mock(Loan.class);
    when(guild.getLoanHandler().getLoanById("loan")).thenReturn(loan);
    Inventory inventory = sf(taken ? SFGUI.TAKEN_LOAN_DETAIL_VIEW : SFGUI.ISSUED_LOAN_DETAIL_VIEW, "guild", "loan");
    updater.updateInventory();
    verify(manager).loanDetailView(player, guild, loan, taken, inventory);
  }

  @Test
  void contractDetailsUseTheExactCurrentContractId() {
    MercenaryContract contract = mock(MercenaryContract.class);
    when(guild.getCompany().getContractHandler().getById("contract")).thenReturn(contract);
    Inventory inventory = sf(SFGUI.CONTRACT_DETAIL_VIEW, "guild", "contract");
    updater.updateInventory();
    verify(manager).contractDetailView(player, guild, inventory, "contract");
  }

  @ParameterizedTest
  @EnumSource(value = SFGUI.class, names = {"CONTRACT_DETAIL_VIEW", "TAKEN_LOAN_DETAIL_VIEW", "ISSUED_LOAN_DETAIL_VIEW"})
  void detailsWithoutAnIdentifierAreIgnored(SFGUI type) {
    sf(type, "guild", null);
    updater.updateInventory();
    verifyNoInteractions(manager);
  }

  @ParameterizedTest
  @EnumSource(value = SFGUI.class, names = {"CONTRACT_DETAIL_VIEW", "TAKEN_LOAN_DETAIL_VIEW", "ISSUED_LOAN_DETAIL_VIEW"})
  void deletedDetailsOpenAFreshFullSizeListInsteadOfReusingTheDetailInventory(SFGUI type) {
    when(guild.getCompany().getContractHandler().getById("gone")).thenReturn(null);
    when(guild.getLoanHandler().getLoanById("gone")).thenReturn(null);
    InventoryManager realManager = new InventoryManager();
    InventoryUpdater realUpdater = new InventoryUpdater(realManager);
    Inventory detail = open(new SFInventoryHolder("guild", type, "gone"), 27, "Detail");
    assertDoesNotThrow(realUpdater::updateInventory);
    Inventory list = player.getOpenInventory().getTopInventory();
    assertNotSame(detail, list);
    assertEquals(54, list.getSize());
    SFGUI expected = type == SFGUI.CONTRACT_DETAIL_VIEW ? SFGUI.CONTRACT_LIST_VIEW
        : type == SFGUI.TAKEN_LOAN_DETAIL_VIEW ? SFGUI.LOANS_TAKEN_VIEW : SFGUI.LOANS_GIVEN_VIEW;
    assertEquals(expected, ((SFInventoryHolder) list.getHolder()).getType());
    assertEquals(Material.BARRIER, list.getItem(53).getType());
  }

  @ParameterizedTest
  @EnumSource(value = SFGUI.class, names = {"GOVERNMENT_VIEW", "COUNCIL_VIEW", "PROPOSAL_VIEW", "POLITICAL_PROPOSAL_VIEW", "TAX_PROPOSAL_VIEW", "LAW_PROPOSAL_VIEW", "FAVOUR_REPRESS_MAIN", "FAVOUR_REPRESS_TYPE", "MILITARY_VIEW", "INSTALLATIONS_VIEW", "DIPLOMACY_VIEW", "DIPLOMACY_LIST", "ATTITUDE_VIEW", "RELATION_VIEW", "TAX_VIEW", "LAW_VIEW", "ELECTION_VIEW", "TIER_VIEW", "TITLE_VIEW"})
  void factionScreensRefreshTheOwnerAndTheirExactView(SFGUI type) {
    Inventory inventory = sf(type, "home", null);
    updater.updateInventory();
    switch (type) {
      case GOVERNMENT_VIEW -> verify(manager).governmentView(player, faction, inventory);
      case COUNCIL_VIEW -> verify(manager.governmentView).councilView(player, faction, inventory);
      case PROPOSAL_VIEW -> verify(manager).proposalView(player, faction, inventory);
      case POLITICAL_PROPOSAL_VIEW -> verify(manager.governmentView).politicalProposalView(player, faction, inventory);
      case TAX_PROPOSAL_VIEW -> verify(manager.governmentView).taxProposalView(player, faction, inventory);
      case LAW_PROPOSAL_VIEW -> verify(manager.governmentView).lawProposalView(player, faction, inventory);
      case FAVOUR_REPRESS_MAIN -> verify(manager.governmentView).favourRepressMainView(player, faction, inventory);
      case FAVOUR_REPRESS_TYPE -> verify(manager.governmentView).favourRepressTypeView(player, faction, true, inventory);
      case MILITARY_VIEW -> verify(manager).militaryView(inventory, player, faction, false);
      case INSTALLATIONS_VIEW -> verify(manager).installationsView(inventory, player, faction, false);
      case DIPLOMACY_VIEW -> verify(manager).diplomacyView(inventory, player, faction, false);
      case DIPLOMACY_LIST -> verify(manager).diplomacyListView(inventory, player, faction, false);
      case ATTITUDE_VIEW -> verify(manager).attitudeView(inventory, player, faction, false);
      case RELATION_VIEW -> verify(manager).relationView(inventory, player, faction, false);
      case TAX_VIEW -> verify(manager.taxView).taxView(player, faction, inventory);
      case LAW_VIEW -> verify(manager).lawView(player, faction, inventory);
      case ELECTION_VIEW -> verify(manager.electionView).electionView(player, faction, inventory);
      case TIER_VIEW -> verify(manager).tierView(inventory, player, faction, false);
      case TITLE_VIEW -> verify(manager).titleView(inventory, player, faction, false);
      default -> fail();
    }
  }

  @ParameterizedTest
  @CsvSource({"WAR_PEACE_SELECT,WHITE_PEACE", "SPECIFIC_TAX_PROPOSAL_VIEW,CITIZENS", "TAX_VIEW_SPECIFIC,VASSALS", "ELECTION_VOTING_VIEW,COUNCIL"})
  void enumSelectionsRefreshTheirSnapshotArgumentsAndIgnoreRemovedValues(SFGUI type, String selected) {
    Inventory inventory = type == SFGUI.WAR_PEACE_SELECT
        ? open(new SFInventoryHolder("home", type, 2, false, selected), 54, type.name())
        : sf(type, "home", selected);
    updater.updateInventory();
    switch (type) {
      case WAR_PEACE_SELECT -> verify(manager.governmentView).warPeaceSelectView(player, faction, Action.WHITE_PEACE, false, 2, inventory);
      case SPECIFIC_TAX_PROPOSAL_VIEW -> verify(manager.governmentView).specificTaxProposalView(player, faction, inventory, TaxTarget.CITIZENS);
      case TAX_VIEW_SPECIFIC -> verify(manager.taxView).specificTaxView(player, faction, TaxTarget.VASSALS, inventory);
      case ELECTION_VOTING_VIEW -> verify(manager.electionView).votingView(player, faction, Candidate.COUNCIL, inventory);
      default -> fail();
    }
    clearInvocations(manager.governmentView, manager.taxView, manager.electionView);
    for (String stale : new String[] {null, "REMOVED_OPTION"}) {
      sf(type, "home", stale);
      assertDoesNotThrow(updater::updateInventory);
      verifyNoInteractions(manager.governmentView, manager.taxView, manager.electionView);
    }
  }

  @Test
  void installationAndTitleSelectionsRequireIdsAndCurrentDefinitions() {
    Inventory installation = sf(SFGUI.INSTALLATION_DETAIL_VIEW, "home", "mill");
    updater.updateInventory();
    verify(manager).installationDetailView(player, faction, "mill", installation);
    Tier tier = mock(Tier.class);
    tiers.when(() -> TierLoader.getByString("county")).thenReturn(tier);
    Inventory titles = sf(SFGUI.TITLE_TYPE_VIEW, "home", "county");
    updater.updateInventory();
    verify(manager).titleTypeView(titles, player, faction, tier, false, 2);
    clearInvocations(manager);
    for (SFGUI type : List.of(SFGUI.INSTALLATION_DETAIL_VIEW, SFGUI.TITLE_TYPE_VIEW)) {
      sf(type, "home", null);
      updater.updateInventory();
      verifyNoInteractions(manager);
    }
    sf(SFGUI.TITLE_TYPE_VIEW, "home", "removed");
    updater.updateInventory();
    verifyNoInteractions(manager);
  }

  @ParameterizedTest
  @EnumSource(value = SFGUI.class, names = {"MOVEMENT_LIST", "MOVEMENT_VIEW", "MOVEMENT_DEMANDS", "MOVEMENT_CRACKDOWN"})
  void movementMenusRefreshTheCurrentMovementOrFactionList(SFGUI type) {
    Inventory inventory = sf(type, type == SFGUI.MOVEMENT_LIST ? "home" : "movement", null);
    updater.updateInventory();
    switch (type) {
      case MOVEMENT_LIST -> verify(manager).movementListView(player, faction, inventory);
      case MOVEMENT_VIEW -> verify(manager).movementView(player, faction, movement, inventory);
      case MOVEMENT_DEMANDS -> verify(manager.movementView).demandsView(player, faction, movement, inventory);
      case MOVEMENT_CRACKDOWN -> verify(manager.movementView).crackdownView(player, faction, movement, inventory);
      default -> fail();
    }
  }

  @Test
  void removedOwnersAreIgnoredAndTargetPickersRetainTheDisplayedCauseIdentity() {
    for (SFGUI type : List.of(SFGUI.MILITARY_VIEW, SFGUI.MOVEMENT_LIST, SFGUI.MOVEMENT_VIEW)) {
      sf(type, "deleted", null);
      updater.updateInventory();
    }
    verifyNoInteractions(manager, manager.movementView);
    when(movement.getFaction()).thenReturn(null);
    sf(SFGUI.MOVEMENT_VIEW, "movement", null);
    updater.updateInventory();
    verifyNoInteractions(manager);
    when(movement.getFaction()).thenReturn(faction);
    Cause displayed = mock(Cause.class);
    Inventory inventory = sf(SFGUI.TARGET_SELECT, "movement", null);
    when(manager.movementView.displayedCause(inventory, movement)).thenReturn(displayed);
    updater.updateInventory();
    verify(manager.movementView).targetSelectionView(player, faction, movement, displayed, inventory);
    when(manager.movementView.displayedCause(inventory, movement)).thenReturn(null);
    updater.updateInventory();
    verify(manager).causesView(player, faction, movement, null);
  }

  @Test
  void warAndParticipantScreensRefreshCurrentWarContextAndIgnoreDeletedWars() {
    Inventory main = open(new WarInventoryHolder(7, SFGUI.WAR_VIEW), 54, "War");
    updater.updateInventory();
    verify(manager).warView(main, player, war, false);
    Inventory engagements = open(new SFCombinedInventoryHolder(7, "home", SFGUI.MERCENARY_ENGAGEMENT_LIST), 54, "Engagements");
    updater.updateInventory();
    verify(manager.warView).populateEngagementList(engagements, player, war, "home");
    Inventory side = open(new SFCombinedInventoryHolder(7, "home", SFGUI.PARTICIPANT_VIEW), 54, "Participant");
    updater.updateInventory();
    verify(manager).participantView(side, player, war, participant, false);
    clearInvocations(manager, manager.warView);
    wars.when(() -> WarManager.getById(7)).thenReturn(null);
    for (Inventory inventory : List.of(main, engagements, side)) {
      player.openInventory(inventory);
      updater.updateInventory();
    }
    verifyNoInteractions(manager, manager.warView);
  }

  @ParameterizedTest
  @EnumSource(value = SFGUI.class, names = {"WAR_DECLARE_GOAL", "WAR_DECLARE_RELATION_TYPE", "WAR_DECLARE_TITLE", "WAR_DECLARE_SUBJECT", "WAR_DECLARE_SETTLEMENT", "WAR_DECLARE_GOVERNMENT"})
  void declarationPickersRetainBothFactionIdsAndGovernmentSelections(SFGUI step) {
    Inventory inventory = open(new DeclareWarHolder("home", "enemy", step, "government", "leadership"), 54, "Declare War");
    updater.updateInventory();
    switch (step) {
      case WAR_DECLARE_GOAL -> verify(manager.declareWarView).openGoalPicker(player, faction, enemy, inventory);
      case WAR_DECLARE_RELATION_TYPE -> verify(manager.declareWarView).openRelationTypePicker(player, faction, enemy, inventory);
      case WAR_DECLARE_TITLE -> verify(manager.declareWarView).openTitlePicker(player, faction, enemy, inventory);
      case WAR_DECLARE_SUBJECT -> verify(manager.declareWarView).openSubjectPicker(player, faction, enemy, inventory);
      case WAR_DECLARE_SETTLEMENT -> verify(manager.declareWarView).openSettlementPicker(player, faction, enemy, inventory);
      case WAR_DECLARE_GOVERNMENT -> verify(manager.declareWarView).openGovernmentPicker(player, faction, enemy, "government", "leadership", inventory);
      default -> fail();
    }
    clearInvocations(manager.declareWarView);
    factions.when(() -> FactionManager.getByString("enemy")).thenReturn(null);
    updater.updateInventory();
    verifyNoInteractions(manager.declareWarView);
  }

  @ParameterizedTest
  @ValueSource(strings = {"pick", "raidSource", "raidTarget"})
  void campaignPickersRefreshActiveParticipantAndSourceContext(String kind) {
    Inventory inventory = open(kind.equals("pick")
        ? new CampaignInventoryHolder(7, SFGUI.CAMPAIGN_INSTALLATION_PICK_VIEW)
        : new CampaignRaidLaunchHolder(7, kind.equals("raidSource") ? null : "fort"), 54, "Campaign");
    updater.updateInventory();
    if (kind.equals("pick")) verify(manager.campaignInstallationPickView).open(player, war, faction, false, inventory);
    else if (kind.equals("raidSource")) verify(manager.campaignRaidLaunchView).openSourcePage(player, war, faction, false, inventory);
    else verify(manager.campaignRaidLaunchView).openTargetPage(player, war, faction, "fort", false, inventory);
    clearInvocations(manager.campaignInstallationPickView, manager.campaignRaidLaunchView);
    factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(null);
    updater.updateInventory();
    factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(faction);
    when(war.isActive()).thenReturn(false);
    updater.updateInventory();
    wars.when(() -> WarManager.getById(7)).thenReturn(null);
    updater.updateInventory();
    verifyNoInteractions(manager.campaignInstallationPickView, manager.campaignRaidLaunchView);
  }

  @Test
  void campaignInstallationPickerDoesNotRefreshForNonParticipantsOrOtherCampaignPages() {
    open(new CampaignInventoryHolder(7, SFGUI.CAMPAIGN_INSTALLATION_PICK_VIEW), 54, "Campaign");
    when(war.isParticipating(faction)).thenReturn(false);
    updater.updateInventory();
    open(new CampaignInventoryHolder(7, SFGUI.CAMPAIGN_VIEW), 54, "Campaign");
    updater.updateInventory();
    verifyNoInteractions(manager.campaignInstallationPickView);
  }

  @Test
  void onlyTheBattleListTitleRefreshesAnUnownedInventory() {
    Inventory ordinary = open(null, 27, "Chest");
    updater.updateInventory();
    BattleInventoryManager battle = battleMenus.constructed().getFirst();
    verifyNoInteractions(battle);
    Inventory list = open(null, 27, "§7Battle List");
    updater.updateInventory();
    verify(battle).populateBattleList(list);
    assertNotSame(ordinary, list);
  }

  @Test
  void inventorySoundsOnlyReachPlayersWithTheRequestedMenuType() {
    Player other = ui.player("Bob");
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(player, other));
    sf(SFGUI.GOVERNMENT_VIEW, "home", null);
    updater.inventorySound("block.note_block.pling", SFGUI.GOVERNMENT_VIEW);
    verify(player).playSound(player, "block.note_block.pling", 1f, 1f);
    verify(other, never()).playSound(any(org.bukkit.entity.Entity.class), anyString(), anyFloat(), anyFloat());
    other.openInventory(ui.inventory(new SFInventoryHolder("home", SFGUI.MILITARY_VIEW), 54, "Military"));
    updater.inventorySound("block.note_block.bit", SFGUI.GOVERNMENT_VIEW);
    verify(player).playSound(player, "block.note_block.bit", 1f, 1f);
    verify(other, never()).playSound(any(org.bukkit.entity.Entity.class), anyString(), anyFloat(), anyFloat());
  }
}
