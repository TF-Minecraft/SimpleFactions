package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.DeclareWarHolder;
import net.tfminecraft.simplefactions.managers.holder.SFCombinedInventoryHolder;
import net.tfminecraft.simplefactions.managers.holder.WarInventoryHolder;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements.Engagement;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarDeclareHelper;
import net.tfminecraft.simplefactions.war.core.WarGoal;
import net.tfminecraft.simplefactions.war.declare.DeJureAnnexEligibility.DeJureTitleOption;
import net.tfminecraft.simplefactions.war.declare.OpenMarketEligibility;
import net.tfminecraft.simplefactions.war.declare.PillageEligibility;
import net.tfminecraft.simplefactions.war.declare.WarDeclareCodeService;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import net.tfminecraft.simplefactions.war.declare.WarGoalValidator;
import net.tfminecraft.simplefactions.war.declare.WarValidationResult;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class WarMenusCoverageTest {
  private FactionDomainFixture fixture;
  private Faction attacker, defender;
  private Player player, defending;
  private War war;
  private InventoryManager navigation;
  private WarView view;
  private DeclareWarView declaration;
  private MockedStatic<WarManager> wars;
  private final List<MockedStatic<?>> scopes = new ArrayList<>();
  private Map<Player, Integer> oldPages;

  private <T> MockedStatic<T> scoped(Class<T> type) {
    MockedStatic<T> s = mockStatic(type);
    scopes.add(s);
    return s;
  }

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    fixture.lawGroup("government", Map.of());
    fixture.lawGroup("leadership", Map.of());
    attacker = fixture.saved("attacker", "Leader");
    attacker.getOrCreateMainGuild();
    defender = fixture.saved("defender", "Defender");
    defender.getOrCreateMainGuild();
    player = fixture.player("Leader");
    defending = fixture.player("Defender");
    for (String groupId : List.of("government", "leadership")) {
      var group = defender.getLawHandler().getGroup(groupId);
      group.setCurrent(group.getLaw("current"));
    }
    ItemAPI itemApi = mock(ItemAPI.class);
    ItemCreator itemCreator = mock(ItemCreator.class);
    when(itemApi.getCreator()).thenReturn(itemCreator);
    when(itemCreator.getItemsAdderItem(anyString()))
        .thenAnswer(call -> new ItemStack(Material.PAPER));
    scoped(TLibs.class).when(TLibs::getItemAPI).thenReturn(itemApi);
    war = new War(7, attacker, defender);
    war.setGoal(WarGoalType.WAR);
    war.setWarType(WarType.WAR);
    war.setCampaignProvinces(List.of(10, 11));
    wars = scoped(WarManager.class);
    wars.when(() -> WarManager.getById(7)).thenReturn(war);
    wars.when(WarManager::getActive).thenReturn(List.of(war));
    scoped(BattleManager.class);
    MockedStatic<IconGetter> icons = scoped(IconGetter.class);
    icons
        .when(() -> IconGetter.getIcon(anyString()))
        .thenAnswer(c -> new ItemStack(Material.BLAZE_POWDER));
    icons
        .when(() -> IconGetter.getIconOrDefault(anyString(), any()))
        .thenAnswer(c -> new ItemStack(c.<Material>getArgument(1)));
    navigation = mock(InventoryManager.class);
    navigation.confirming = new HashMap<>();
    when(navigation.getFiller(any())).thenAnswer(c -> new ItemStack(c.<Material>getArgument(0)));
    when(navigation.createBackButton(any())).thenAnswer(c -> new ItemStack(Material.BARRIER));
    view = new WarView(navigation);
    declaration = new DeclareWarView(navigation);
    fixture.provincesEnabled(true);
    oldPages = new HashMap<>(WarView.engagementPage);
    WarView.engagementPage.clear();
  }

  @AfterEach
  void close() {
    try {
      for (int n = scopes.size() - 1; n >= 0; n--) scopes.get(n).close();
    } finally {
      WarView.engagementPage.clear();
      WarView.engagementPage.putAll(oldPages);
      fixture.close();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "leadership",
        "removed_attacker",
        "removed_defender",
        "replaced_attacker",
        "replaced_defender"
      })
  void confirmingAnOldDeclarationCannotActForAFormerLeader(String changed) {
    WarDeclareRequest request = WarDeclareRequest.of(attacker, defender, WarGoalType.WAR);
    if (changed.equals("leadership")) {
      attacker.addMember("Successor");
      attacker.setLeader("Successor");
    }
    if (changed.endsWith("attacker")) {
      FactionManager.factions.remove(attacker);
      if (changed.startsWith("replaced")) fixture.saved("attacker", "Leader");
    }
    if (changed.endsWith("defender")) {
      FactionManager.factions.remove(defender);
      if (changed.startsWith("replaced")) fixture.saved("defender", "Defender");
    }
    try (MockedConstruction<WarGoalValidator> validation =
            mockConstruction(
                WarGoalValidator.class,
                (mock, context) ->
                    when(mock.validate(any())).thenReturn(WarValidationResult.ok()));
        MockedStatic<WarDeclareCodeService> codes = mockStatic(WarDeclareCodeService.class)) {
      declaration.handleConfirm(player, request, true);
    }
    wars.verify(
        () ->
            WarManager.declareWar(
                any(),
                any(),
                any(),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class)),
        never());
    assertEquals(changed.equals("leadership") ? "Successor" : "Leader", attacker.getLeader());
    verify(navigation, never()).warList(player);
  }

  @Test
  void outsideFactionLeadersCanReadSecondaryParticipantsInLegacyWars() {
    Faction outside = fixture.saved("outside", "Visitor");
    outside.getOrCreateMainGuild();
    Player visitor = fixture.player("Visitor");
    war.setGoal(null);
    ItemStack item =
        view.creator.createSecondaryItem(
            visitor, war.getParticipant(attacker), war, attacker, false, true);
    assertEquals("attacker", name(item));
    assertFalse(lore(item).contains("set a war goal"));
    assertTrue(lore(item).contains("Called!"));
  }

  @ParameterizedTest
  @EnumSource(
      value = SFGUI.class,
      names = {"WAR_VIEW", "PARTICIPANT_VIEW"})
  void aRemovedWarCannotBeUsedFromItsStaleMenu(SFGUI type) {
    InventoryHolder holder =
        type == SFGUI.WAR_VIEW
            ? new WarInventoryHolder(7, type)
            : new SFCombinedInventoryHolder(7, "attacker", type);
    Inventory inventory = fixture.ui.inventory(holder, 54, "War");
    inventory.setItem(4, tag("attacker"));
    player.openInventory(inventory);
    wars.when(() -> WarManager.getById(7)).thenReturn(null);
    InventoryClickEvent event = fixture.ui.click(player, 4);
    assertDoesNotThrow(() -> view.click(event, inventory, player));
    assertTrue(event.isCancelled());
    assertEquals(2, FactionManager.factions.size());
    verify(navigation, never()).contractDetailView(any(), any(), anyString());
    wars.verify(() -> WarManager.sendRequest(any(), any(), any(), any()), never());
  }

  @Test
  void warRefreshRemovesAFormerParticipantFromItsSideColumn() {
    Faction ally = fixture.saved("ally", "Ally");
    ally.getOrCreateMainGuild();
    war.getAttackers().getMainParticipants().add(new Participant(ally));
    view.warView(null, player, war, true);
    Inventory inventory = top();
    assertEquals("ally", id(inventory.getItem(10)));
    war.getAttackers()
        .getMainParticipants()
        .removeIf(participant -> participant.getLeader() == ally);
    view.warView(inventory, player, war, false);
    assertTrue(
        inventory.getItem(10) == null || inventory.getItem(10).getType() == Material.AIR,
        "Removed participants must disappear on refresh");
    assertEquals("attacker", id(inventory.getItem(9)));
  }

  @Test
  void participantRefreshRemovesAnAllyWhoseRelationEnded() {
    Faction ally = fixture.saved("ally", "Ally");
    ally.getOrCreateMainGuild();
    var type = fixture.relationType("ally", Map.of("name", "Ally"));
    attacker.setRelation(ally, new Relation(type, RelationLoader.getDefaultAttitude()));
    ally.setRelation(attacker, new Relation(type, RelationLoader.getDefaultAttitude()));
    Participant participant = war.getParticipant(attacker);
    view.participantView(null, player, war, participant, true);
    Inventory inventory = top();
    assertEquals("ally", id(inventory.getItem(9)));
    attacker.setRelation(
        ally, new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));
    ally.setRelation(
        attacker,
        new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));
    view.participantView(inventory, player, war, participant, false);
    assertTrue(participant.getAllies().isEmpty());
    assertTrue(
        inventory.getItem(9) == null || inventory.getItem(9).getType() == Material.AIR,
        "Removed allies must disappear on refresh");
  }

  @Test
  void warListOpensTheSelectedWarAndCampaignAndBackNavigationStayConnected() {
    view.warList(player);
    Inventory inventory = top();
    assertEquals(7, integer(inventory.getItem(0), "id"));
    assertTrue(lore(inventory.getItem(0)).contains("Attacker: attacker"));
    assertTrue(lore(inventory.getItem(0)).contains("Defender: defender"));
    assertTrue(lore(inventory.getItem(0)).contains("Status: Active"));
    view.click(fixture.ui.click(player, 0), inventory, player);
    inventory = top();
    assertEquals(SFGUI.WAR_VIEW, ((WarInventoryHolder) inventory.getHolder()).getType());
    assertEquals("attacker", id(inventory.getItem(3)));
    assertEquals("defender", id(inventory.getItem(5)));
    assertEquals(Material.COMPASS, inventory.getItem(49).getType());
    view.click(fixture.ui.click(player, 49), inventory, player);
    verify(navigation).openCampaignView(player, war);
    view.click(fixture.ui.click(player, 9), inventory, player);
    inventory = top();
    assertEquals(
        SFGUI.PARTICIPANT_VIEW, ((SFCombinedInventoryHolder) inventory.getHolder()).getType());
    view.click(fixture.ui.click(player, 53), inventory, player);
    inventory = top();
    assertEquals(SFGUI.WAR_VIEW, ((WarInventoryHolder) inventory.getHolder()).getType());
    view.click(fixture.ui.click(player, 53), inventory, player);
    assertEquals("§7War List", player.getOpenInventory().getTitle());
    wars.when(WarManager::getActive).thenReturn(List.of());
    view.populateWarList(top());
    assertNull(top().getItem(0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"empty", "no_id", "missing"})
  void staleWarListRowsNeverOpenAnotherMenu(String condition) {
    view.warList(player);
    Inventory inventory = top();
    if (condition.equals("empty")) inventory.setItem(0, null);
    if (condition.equals("no_id")) inventory.setItem(0, new ItemStack(Material.PAPER));
    if (condition.equals("missing")) wars.when(() -> WarManager.getById(7)).thenReturn(null);
    InventoryClickEvent event = fixture.ui.click(player, 0);
    view.click(event, inventory, player);
    assertSame(inventory, top());
    assertTrue(event.isCancelled());
  }

  @ParameterizedTest
  @ValueSource(strings = {"raiding", "ended", "no_route", "outsider", "member"})
  void campaignButtonReflectsLiveWarAndViewerEligibility(String reason) {
    Player viewer = player;
    if (reason.equals("raiding")) war.setWarType(WarType.RAID);
    if (reason.equals("ended")) war.end(WarEndReason.WHITE_PEACE);
    if (reason.equals("no_route")) war.setCampaignProvinces(List.of());
    if (reason.equals("outsider")) viewer = fixture.player("Visitor");
    if (reason.equals("member")) {
      attacker.addMember("Citizen");
      viewer = fixture.player("Citizen");
    }
    view.warView(null, viewer, war, true);
    Inventory inventory = viewer.getOpenInventory().getTopInventory();
    assertEquals(
        reason.equals("member") ? Material.COMPASS : Material.RED_STAINED_GLASS_PANE,
        inventory.getItem(49).getType());
    view.click(fixture.ui.click(viewer, 49), inventory, viewer);
    verify(navigation, reason.equals("member") ? times(1) : never()).openCampaignView(viewer, war);
  }

  @ParameterizedTest
  @CsvSource({"10,TRIBUTARY", "11,OPEN_MARKET", "22,default"})
  void defenderSelectsAndPersistsCounterGoalsBeforeBattle(int slot, String goalName) {
    view.warView(null, defending, war, true);
    Inventory inventory = defending.getOpenInventory().getTopInventory();
    assertEquals(Material.GOLDEN_SWORD, inventory.getItem(48).getType());
    view.click(fixture.ui.click(defending, 48), inventory, defending);
    inventory = defending.getOpenInventory().getTopInventory();
    assertEquals(SFGUI.WAR_COUNTER_GOAL, ((WarInventoryHolder) inventory.getHolder()).getType());
    assertEquals(27, inventory.getSize());
    view.click(fixture.ui.click(defending, slot), inventory, defending);
    assertEquals(
        goalName.equals("default") ? null : WarGoalType.valueOf(goalName),
        war.getDefenderCounterGoal());
    wars.verify(() -> WarManager.persist(war));
    verify(defending)
        .sendMessage(
            goalName.equals("default")
                ? "§aWar reparations set as the default counter."
                : "§aDefender counter goal set to "
                    + WarGoalType.valueOf(goalName).getDisplayName()
                    + ".");
  }

  @Test
  void counterGoalPickerOffersEligibleUsurpAndBoundedConfiguredVassalTypes() {
    Title county = fixture.title("Attacker County", "county", 10);
    attacker.addTitle(county);
    Title duchy = fixture.title("Defender County", "county", 20);
    defender.addTitle(duchy);
    for (int n = 0; n < 12; n++)
      fixture.relationType("type" + n, Map.of("vassal", true, "war", true, "name", "Subject " + n));
    view.warView(null, defending, war, true);
    view.click(
        fixture.ui.click(defending, 48), defending.getOpenInventory().getTopInventory(), defending);
    Inventory inventory = defending.getOpenInventory().getTopInventory();
    assertEquals("usurp", string(inventory.getItem(12), "counter_goal"));
    assertNotNull(inventory.getItem(16));
    assertNull(inventory.getItem(17));
    String relation = string(inventory.getItem(13), "counter_relation_type");
    assertNotNull(relation);
    view.click(fixture.ui.click(defending, 13), inventory, defending);
    assertEquals(WarGoalType.SUBJUGATE, war.getDefenderCounterGoal());
    assertEquals(relation, war.getDefenderCounterRelationTypeId());
  }

  @Test
  void startingTheBattleLocksAnAlreadyOpenCounterGoalPicker() {
    view.warView(null, defending, war, true);
    view.click(
        fixture.ui.click(defending, 48), defending.getOpenInventory().getTopInventory(), defending);
    Inventory picker = defending.getOpenInventory().getTopInventory();
    Battle battle = mock(Battle.class);
    when(battle.hasStarted()).thenReturn(true);
    when(BattleManager.getByWarId(7)).thenReturn(battle);
    view.click(fixture.ui.click(defending, 10), picker, defending);
    assertTrue(war.hasFirstBattleStarted());
    assertNull(war.getDefenderCounterGoal());
    wars.verify(() -> WarManager.persist(war));
    assertTrue(
        lore(defending.getOpenInventory().getTopInventory().getItem(48))
            .contains("Locked after the first battle began"));
    verify(defending).sendMessage(contains("Counter goals can only be changed"));
  }

  @Test
  void callersCanInviteAnUncalledAllyButCannotInviteItTwice() {
    Faction ally = ally("ally", "Ally");
    Participant participant = war.getParticipant(attacker);
    view.participantView(null, player, war, participant, true);
    Inventory inventory = top();
    assertTrue(lore(inventory.getItem(9)).contains("Click to call!"));
    view.click(fixture.ui.click(player, 9), inventory, player);
    wars.verify(() -> WarManager.sendRequest(player, attacker, ally, war));
    participant.getAllies().put(ally, true);
    view.participantView(inventory, player, war, participant, false);
    assertTrue(lore(inventory.getItem(9)).contains("Called!"));
    view.click(fixture.ui.click(player, 9), inventory, player);
    wars.verify(() -> WarManager.sendRequest(player, attacker, ally, war), times(1));
  }

  @Test
  void legacyParticipantLoreShowsBothOurAndOtherFactionsWarGoals() {
    war.setGoal(null);
    Faction ally = ally("ally", "Ally");
    YamlConfiguration config = new YamlConfiguration();
    config.set("name", "Annexation");
    WarGoal goal = new WarGoal("annex", config);
    Participant mine =
        new Participant(attacker, List.of(), Map.of(ally, true), Map.of(defender, goal), false);
    Participant theirs = new Participant(ally, List.of(), Map.of(), Map.of(defender, goal), false);
    war.getAttackers().getMainParticipants().clear();
    war.getAttackers().getMainParticipants().add(mine);
    war.getAttackers().getMainParticipants().add(theirs);
    ItemStack item =
        view.creator.createParticipantItem(
            player, war.getParticipant(defender), "main_defender", war, true, true);
    assertTrue(lore(item).contains("Your War Goal"));
    assertTrue(lore(item).contains("Annexation"));
    assertTrue(lore(item).contains("Other War Goals"));
    assertTrue(lore(item).contains("ally - Annexation"));
    Player visitor = fixture.player("Visitor");
    ItemStack read =
        view.creator.createParticipantItem(visitor, mine, "main_attacker", war, true, false);
    assertTrue(lore(read).contains("Allies: 1"));
    assertFalse(lore(read).contains("Your War Goal"));
    mine.setCivilWar(true);
    assertTrue(
        lore(view.creator.createParticipantItem(
                player, mine, "secondary_participant", war, false, false))
            .contains("Civil War"));
  }

  @Test
  void legacyEnemyGoalAndSecondaryBackerAndSubjectLoreAreExplicit() {
    war.setGoal(null);
    Participant main = war.getParticipant(attacker), enemy = war.getParticipant(defender);
    assertTrue(
        lore(view.creator.createParticipantItem(player, enemy, "main_defender", war, true, true))
            .contains("Not Set!"));
    assertTrue(
        lore(view.creator.createSecondaryItem(player, enemy, war, defender, false, false, true))
            .contains("Click to set a war goal!"));
    Faction subject = fixture.saved("subject", "Subject");
    fixture.subject(attacker, subject);
    Regiment levy = fixture.regiment("levy", true, 0, 0);
    attacker.getMilitary().getRegiments().add(levy);
    war.update();
    ItemStack item = view.creator.createSecondaryItem(player, main, war, subject, true, true);
    assertTrue(lore(item).contains("Contributes: 0 Soldiers"));
    assertTrue(lore(item).contains("automatically called"));
    Faction backer = fixture.saved("backer", "Backer");
    backer.getOrCreateMainGuild();
    main.addBacker(backer);
    view.participantView(null, player, war, main, true);
    assertEquals("subject", id(top().getItem(9)));
    assertEquals("backer", id(top().getItem(10)));
    assertTrue(lore(top().getItem(10)).contains("Backer"));
  }

  @Test
  void warSummaryDescribesTargetTitleGovernmentAndPrimaryTitle() {
    Title title = fixture.title("Crown", "county", 10);
    defender.addTitle(title);
    war.setGoal(WarGoalType.DE_JURE_ANNEX);
    war.setTargetTitleId(title.getId());
    assertTrue(lore(view.creator.createWarItem(war, false)).contains("Title: Crown"));
    war.setGoal(WarGoalType.USURP);
    assertTrue(lore(view.creator.createWarItem(war, true)).contains("Primary title: Crown"));
    LawGroup group = fixture.lawGroup("government", Map.of());
    Law law = group.getLaw("current");
    try (MockedStatic<OpenMarketEligibility> market = mockStatic(OpenMarketEligibility.class)) {
      market
          .when(() -> OpenMarketEligibility.resolve(eq(defender), anyString()))
          .thenReturn(new OpenMarketEligibility.ResolvedLaw(law, group));
      war.setGoal(WarGoalType.CHANGE_GOVERNMENT);
      war.setGovernmentLawId("current");
      war.setLeadershipLawId("current");
      String lore = lore(view.creator.createWarItem(war, false));
      assertTrue(lore.contains("Government: Current government"));
      assertTrue(lore.contains("Leadership: Current government"));
    }
    war.setDefenderCounterGoal(WarGoalType.TRIBUTARY, null);
    assertTrue(lore(view.creator.createWarItem(war, true)).contains("Defender counter: Tributary"));
    war.setInitiativeAttacker(8);
    war.setInitiativeDefender(2);
    ItemStack campaign = view.creator.createCampaignButton(war);
    assertEquals(7, integer(campaign, "campaign_war"));
    assertTrue(lore(campaign).contains("Attacker initiative: 8"));
    assertTrue(lore(campaign).contains("Defender initiative: 2"));
  }

  @Test
  void hiredCompaniesOverflowIntoPagesAndContractClicksUseTheOriginalGuild() {
    List<Engagement> engaged =
        IntStream.range(0, 49).mapToObj(n -> engagement("contract-" + n)).toList();
    try (MockedStatic<MercenaryEngagements> mercenaries = mockStatic(MercenaryEngagements.class)) {
      mercenaries.when(() -> MercenaryEngagements.on(war, war.getAttackers())).thenReturn(engaged);
      view.warView(null, player, war, true);
      Inventory inventory = top();
      assertEquals(Material.WRITABLE_BOOK, inventory.getItem(39).getType());
      assertTrue(lore(inventory.getItem(39)).contains("+35 not shown"));
      view.click(fixture.ui.click(player, 10), inventory, player);
      verify(navigation).contractDetailView(player, attacker.getOrCreateMainGuild(), "contract-0");
      view.click(fixture.ui.click(player, 39), inventory, player);
      inventory = top();
      assertEquals(
          SFGUI.MERCENARY_ENGAGEMENT_LIST,
          ((SFCombinedInventoryHolder) inventory.getHolder()).getType());
      assertEquals("contract-0", contractId(inventory.getItem(0)));
      assertNotNull(inventory.getItem(53));
      view.click(fixture.ui.click(player, 53), inventory, player);
      assertEquals(1, WarView.engagementPage.get(player));
      assertEquals("contract-47", contractId(inventory.getItem(0)));
      assertNull(inventory.getItem(2));
      view.click(fixture.ui.click(player, 0), inventory, player);
      verify(navigation).contractDetailView(player, attacker.getOrCreateMainGuild(), "contract-47");
      view.click(fixture.ui.click(player, 45), inventory, player);
      assertEquals(0, WarView.engagementPage.get(player));
      assertNull(inventory.getItem(45));
      view.click(fixture.ui.click(player, 8), inventory, player);
      assertEquals(SFGUI.WAR_VIEW, ((WarInventoryHolder) top().getHolder()).getType());
      mercenaries
          .when(() -> MercenaryEngagements.on(war, war.getDefenders()))
          .thenReturn(List.of(engaged.get(0)));
      view.engagementList(player, war, BattleTemplate.DEFENDER_SIDE);
      assertTrue(lore(top().getItem(0)).contains("Mercenary (Defender)"));
    }
  }

  @Test
  void mercenaryItemsShowContractFactsAndGracefullyRepresentMissingAssociations() {
    Engagement engagement = engagement("signed");
    ItemStack item = view.creator.createMercenaryItem(engagement, "mercenary_defender");
    assertEquals("Company signed", name(item));
    assertEquals("signed", contractId(item));
    assertTrue(lore(item).contains("Promised slots: 3"));
    assertTrue(lore(item).contains("Days remaining: 4"));
    assertTrue(lore(item).contains("Host guild: attacker"));
    assertEquals(
        Material.IRON_SWORD,
        view.creator.createMercenaryItem(null, "mercenary_attacker").getType());
    assertEquals("Mercenary", name(view.creator.createMercenaryItem(null, "mercenary_attacker")));
    assertEquals(
        1,
        view.creator.buildMercenaryLore(new Engagement(null, engagement.contract()), null).size());
    assertEquals(
        1,
        view.creator.buildMercenaryLore(new Engagement(engagement.company(), null), null).size());
    assertTrue(String.join("", view.creator.buildOverflowOpenerLore(-3)).contains("0"));
    assertEquals(new WarView.SideColumnLayout(1, 1, false), WarView.SideColumnLayout.of(1, 1, 16));
    assertEquals(new WarView.SideColumnLayout(0, 0, true), WarView.SideColumnLayout.of(5, 5, 0));
  }

  @Test
  void eachWarGoalButtonKeepsItsGoalIdAndDescription() {
    Map<WarGoalType, Supplier<ItemStack>> options =
        Map.of(
            WarGoalType.WAR,
            declaration.creator::createWarGoalItem,
            WarGoalType.TRIBUTARY,
            declaration.creator::createTributaryGoalItem,
            WarGoalType.SUBJUGATE,
            declaration.creator::createSubjugateGoalItem,
            WarGoalType.USURP,
            declaration.creator::createUsurpGoalItem,
            WarGoalType.OPEN_MARKET,
            declaration.creator::createOpenMarketGoalItem,
            WarGoalType.CHANGE_GOVERNMENT,
            declaration.creator::createChangeGovernmentGoalItem,
            WarGoalType.PILLAGE,
            declaration.creator::createPillageGoalItem,
            WarGoalType.DE_JURE_ANNEX,
            declaration.creator::createDeJureGoalItem,
            WarGoalType.TRANSFER_SUBJECT,
            declaration.creator::createTransferSubjectGoalItem);
    options.forEach(
        (goal, build) -> {
          ItemStack item = build.get();
          assertEquals(goal.toJson(), string(item, "goal"));
          assertTrue(lore(item).contains("Click to select"));
          assertFalse(name(item).isBlank());
        });
    Title title = fixture.title("Frontier", "county", 10);
    Settlement settlement = mock(Settlement.class);
    when(settlement.getId()).thenReturn("town");
    when(settlement.getName()).thenReturn("Town");
    for (boolean eligible : List.of(true, false)) {
      ItemStack t =
          declaration.creator.createTitleItem(
              new DeJureTitleOption(title, eligible, "Blocked title"));
      ItemStack city =
          declaration.creator.createSettlementItem(
              new PillageEligibility.PillageSettlementOption(
                  settlement, eligible, "Blocked settlement"));
      assertEquals(title.getId(), id(t));
      assertEquals("town", id(city));
      assertEquals(eligible ? "true" : null, string(t, "eligible"));
      assertEquals(eligible ? "true" : null, string(city, "eligible"));
      assertTrue(lore(t).contains(eligible ? "Click to declare" : "Blocked title"));
      assertTrue(lore(city).contains(eligible ? "Click to declare" : "Blocked settlement"));
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = WarGoalType.class,
      names = {"WAR", "TRIBUTARY", "USURP", "OPEN_MARKET"})
  void goalsWithoutExtraSelectionsOpenAValidatedConfirmation(WarGoalType goal) {
    try (MockedConstruction<WarGoalValidator> validators = validating(WarValidationResult.ok())) {
      declaration.routeGoal(player, attacker, defender, goal);
      ArgumentCaptor<WarDeclareRequest> request = ArgumentCaptor.forClass(WarDeclareRequest.class);
      verify(navigation).confirmWarDeclareView(eq(player), request.capture());
      assertSame(attacker, request.getValue().getAttacker());
      assertSame(defender, request.getValue().getDefender());
      assertEquals(goal, request.getValue().getGoal());
    }
  }

  @Test
  void invalidDeclarationShowsItsReasonAndDoesNotOpenOrExecuteConfirmation() {
    try (MockedConstruction<WarGoalValidator> validators =
        validating(WarValidationResult.fail("§cCannot attack this realm"))) {
      declaration.routeGoal(player, attacker, defender, WarGoalType.WAR);
      verify(navigation, never()).confirmWarDeclareView(any(), any());
      declaration.handleConfirm(
          player, WarDeclareRequest.of(attacker, defender, WarGoalType.WAR), true);
      verify(player, times(2)).sendMessage("§cCannot attack this realm");
      assertEquals(SFGUI.WAR_DECLARE_GOAL, ((DeclareWarHolder) top().getHolder()).getStep());
      wars.verify(
          () ->
              WarManager.declareWar(
                  any(),
                  any(),
                  any(),
                  nullable(String.class),
                  nullable(String.class),
                  nullable(String.class),
                  nullable(String.class),
                  nullable(String.class),
                  nullable(String.class)),
          never());
    }
  }

  @Test
  void authorizedConfirmationCreatesTheWarAndRetainsAnUnspentCodeOnFailure() {
    try (MockedConstruction<WarGoalValidator> validators = validating(WarValidationResult.ok());
        MockedStatic<WarDeclareCodeService> codes = mockStatic(WarDeclareCodeService.class)) {
      WarDeclareRequest request = WarDeclareRequest.of(attacker, defender, WarGoalType.WAR);
      declaration.handleConfirm(player, request, true);
      verify(player).sendMessage("§cCould not declare war.");
      verify(navigation, never()).warList(player);
      wars.when(WarManager::getLastDeclareError).thenReturn("§cNavy unavailable");
      declaration.handleConfirm(player, request, true);
      verify(player).sendMessage("§cNavy unavailable");
      wars.when(
              () ->
                  WarManager.declareWar(
                      attacker, defender, WarGoalType.WAR, null, null, null, null, null, null))
          .thenReturn(war);
      declaration.handleConfirm(player, request, true);
      verify(navigation).warList(player);
      codes.verify(() -> WarDeclareCodeService.clearSession(any()), never());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void acceptedDeclarationCodeIsRedeemedOnlyAfterWarCreation(boolean redeemed) {
    WarDeclareRequest request = WarDeclareRequest.of(attacker, defender, WarGoalType.WAR);
    WarDeclareCodeService.Session session =
        new WarDeclareCodeService.Session("approved", "attacker", "defender", WarGoalType.WAR);
    try (MockedConstruction<WarGoalValidator> validators = validating(WarValidationResult.ok());
        MockedStatic<WarDeclareCodeService> codes = mockStatic(WarDeclareCodeService.class)) {
      codes.when(() -> WarDeclareCodeService.isRequired(player)).thenReturn(true);
      codes.when(() -> WarDeclareCodeService.session(player)).thenReturn(session);
      codes.when(() -> WarDeclareCodeService.covers(session, request)).thenReturn(true);
      codes
          .when(() -> WarDeclareCodeService.redeem("approved", "attacker", "defender", 7))
          .thenReturn(
              redeemed
                  ? WarDeclareCodeService.Result.success(WarGoalType.WAR)
                  : WarDeclareCodeService.Result.fail("unavailable"));
      wars.when(
              () ->
                  WarManager.declareWar(
                      attacker, defender, WarGoalType.WAR, null, null, null, null, null, null))
          .thenReturn(war);
      int before = fixture.ui.tasks.size();
      declaration.handleConfirm(player, request, true);
      codes.verify(() -> WarDeclareCodeService.clearSession(player));
      assertEquals(1, fixture.ui.asyncTasks.size());
      fixture.ui.asyncTasks.removeFirst().run();
      codes.verify(() -> WarDeclareCodeService.redeem("approved", "attacker", "defender", 7));
      assertEquals(before + (redeemed ? 0 : 1), fixture.ui.tasks.size());
      if (!redeemed) fixture.ui.tasks.removeLast().run();
      verify(navigation).warList(player);
    }
  }

  @Test
  void mismatchingCodesAndCancellationNeverDeclareOrSpendTheCode() {
    try (MockedConstruction<WarGoalValidator> validators = validating(WarValidationResult.ok());
        MockedStatic<WarDeclareCodeService> codes = mockStatic(WarDeclareCodeService.class)) {
      codes.when(() -> WarDeclareCodeService.isRequired(player)).thenReturn(true);
      declaration.handleConfirm(
          player, WarDeclareRequest.of(attacker, defender, WarGoalType.WAR), true);
      verify(player).sendMessage("§cYour war code does not cover this declaration.");
      codes.verify(() -> WarDeclareCodeService.clearSession(player));
      codes
          .when(() -> WarDeclareCodeService.session(player))
          .thenReturn(
              new WarDeclareCodeService.Session("code", "attacker", "defender", WarGoalType.WAR));
      declaration.handleConfirm(
          player, WarDeclareRequest.of(attacker, defender, WarGoalType.WAR), false);
      verify(navigation).diplomacyView(null, player, defender, true);
      verify(player).sendMessage("§7War declaration cancelled. Your code is still unused.");
      assertTrue(fixture.ui.asyncTasks.isEmpty());
    }
  }

  @Test
  void goalPickerShowsOnlyAvailableGoalsAndRefreshesTheExistingInventory() {
    Title title = fixture.title("Frontier", "county", 10);
    Faction subject = fixture.saved("subject", "Subject");
    try (MockedStatic<WarDeclareHelper> options = mockStatic(WarDeclareHelper.class);
        MockedConstruction<WarGoalValidator> validators = validating(WarValidationResult.ok())) {
      options.when(() -> WarDeclareHelper.canDeclareUsurp(attacker, defender)).thenReturn(true);
      options
          .when(() -> WarDeclareHelper.deJureTitleOptions(attacker, defender))
          .thenReturn(List.of(new DeJureTitleOption(title, true, null)));
      options.when(() -> WarDeclareHelper.defenderSubjects(defender)).thenReturn(List.of(subject));
      declaration.openGoalPicker(player, attacker, defender);
      Inventory inventory = top();
      assertEquals("war", string(inventory.getItem(10), "goal"));
      assertEquals("transfer_subject", string(inventory.getItem(18), "goal"));
      assertEquals("usurp", string(inventory.getItem(16), "goal"));
      assertEquals("de_jure_annex", string(inventory.getItem(17), "goal"));
      declaration.click(fixture.ui.click(player, 10), inventory, player);
      verify(navigation)
          .confirmWarDeclareView(eq(player), argThat(r -> r.getGoal() == WarGoalType.WAR));
      options.when(() -> WarDeclareHelper.canDeclareUsurp(attacker, defender)).thenReturn(false);
      options
          .when(() -> WarDeclareHelper.deJureTitleOptions(attacker, defender))
          .thenReturn(List.of());
      options.when(() -> WarDeclareHelper.defenderSubjects(defender)).thenReturn(List.of());
      declaration.openGoalPicker(player, attacker, defender, inventory);
      assertNull(inventory.getItem(16));
      assertSame(inventory, top());
      declaration.click(fixture.ui.click(player, 26), inventory, player);
      verify(navigation).diplomacyView(null, player, defender, true);
      fixture.provincesEnabled(false);
      clearInvocations(player);
      declaration.openGoalPicker(player, attacker, defender);
      verify(player, never()).openInventory(any(Inventory.class));
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = WarGoalType.class,
      names = {"SUBJUGATE", "DE_JURE_ANNEX", "TRANSFER_SUBJECT", "PILLAGE", "CHANGE_GOVERNMENT"})
  void routedGoalPickersPreserveTheSelectedTargetInTheConfirmation(WarGoalType goal) {
    Title title = fixture.title("Frontier", "county", 10);
    Faction subject = fixture.saved("subject", "Subject");
    Settlement town = mock(Settlement.class);
    when(town.getId()).thenReturn("town");
    when(town.getName()).thenReturn("Town");
    try (MockedStatic<WarDeclareHelper> options = mockStatic(WarDeclareHelper.class);
        MockedStatic<PillageEligibility> pillage = mockStatic(PillageEligibility.class);
        MockedConstruction<WarGoalValidator> validators = validating(WarValidationResult.ok())) {
      options
          .when(() -> WarDeclareHelper.deJureTitleOptions(attacker, defender))
          .thenReturn(List.of(new DeJureTitleOption(title, true, null)));
      options.when(() -> WarDeclareHelper.defenderSubjects(defender)).thenReturn(List.of(subject));
      pillage
          .when(() -> PillageEligibility.options(attacker, defender))
          .thenReturn(List.of(new PillageEligibility.PillageSettlementOption(town, true, null)));
      declaration.routeGoal(player, attacker, defender, goal);
      Inventory inventory = top();
      DeclareWarHolder holder = (DeclareWarHolder) inventory.getHolder();
      assertEquals("attacker", holder.getAttackerId());
      assertEquals("defender", holder.getDefenderId());
      if (goal == WarGoalType.CHANGE_GOVERNMENT) {
        fixture.law("government", "republic", Map.of());
        declaration.click(fixture.ui.click(player, 11), inventory, player);
        inventory = top();
        assertEquals(
            SFGUI.WAR_DECLARE_GOVERNMENT_LAW, ((DeclareWarHolder) inventory.getHolder()).getStep());
        declaration.click(
            fixture.ui.click(player, findId(inventory, "republic")), inventory, player);
        inventory = top();
        assertEquals("republic", ((DeclareWarHolder) inventory.getHolder()).getGovernmentLawId());
        assertEquals("confirm", id(inventory.getItem(22)));
        declaration.click(fixture.ui.click(player, 22), inventory, player);
      } else declaration.click(fixture.ui.click(player, 0), inventory, player);
      ArgumentCaptor<WarDeclareRequest> request = ArgumentCaptor.forClass(WarDeclareRequest.class);
      verify(navigation).confirmWarDeclareView(eq(player), request.capture());
      WarDeclareRequest actual = request.getValue();
      assertEquals(goal, actual.getGoal());
      switch (goal) {
        case SUBJUGATE -> assertEquals("vassal", actual.getRelationTypeId());
        case DE_JURE_ANNEX -> assertEquals(title.getId(), actual.getTargetTitleId());
        case TRANSFER_SUBJECT -> assertEquals("subject", actual.getSubjectFactionId());
        case PILLAGE -> assertEquals("town", actual.getTargetSettlementId());
        case CHANGE_GOVERNMENT -> {
          assertEquals("republic", actual.getGovernmentLawId());
          assertEquals("current", actual.getLeadershipLawId());
        }
        default -> fail("Unexpected goal");
      }
    }
  }

  @Test
  void largePickersRespectTheBackSlotAndRefreshWithoutLeavingOldChoices() {
    List<DeJureTitleOption> titleOptions = new ArrayList<>();
    List<Faction> subjects = new ArrayList<>();
    List<PillageEligibility.PillageSettlementOption> settlements = new ArrayList<>();
    for (int n = 0; n < 28; n++) {
      titleOptions.add(
          new DeJureTitleOption(fixture.title("Title " + n, "county", 100 + n), true, null));
      subjects.add(fixture.saved("subject" + n, "Subject" + n));
      Settlement town = mock(Settlement.class);
      when(town.getId()).thenReturn("town" + n);
      when(town.getName()).thenReturn("Town " + n);
      settlements.add(new PillageEligibility.PillageSettlementOption(town, true, null));
      fixture.relationType("vassal" + n, Map.of("vassal", true));
    }
    try (MockedStatic<WarDeclareHelper> options = mockStatic(WarDeclareHelper.class);
        MockedStatic<PillageEligibility> pillage = mockStatic(PillageEligibility.class)) {
      options
          .when(() -> WarDeclareHelper.deJureTitleOptions(attacker, defender))
          .thenReturn(titleOptions);
      options.when(() -> WarDeclareHelper.defenderSubjects(defender)).thenReturn(subjects);
      pillage.when(() -> PillageEligibility.options(attacker, defender)).thenReturn(settlements);
      declaration.openTitlePicker(player, attacker, defender);
      Inventory titles = top();
      assertEquals(titleOptions.get(25).title().getId(), id(titles.getItem(25)));
      assertEquals(Material.BARRIER, titles.getItem(26).getType());
      options
          .when(() -> WarDeclareHelper.deJureTitleOptions(attacker, defender))
          .thenReturn(List.of());
      declaration.openTitlePicker(player, attacker, defender, titles);
      assertNull(titles.getItem(25));
      declaration.openSubjectPicker(player, attacker, defender);
      Inventory people = top();
      assertEquals("subject25", id(people.getItem(25)));
      options.when(() -> WarDeclareHelper.defenderSubjects(defender)).thenReturn(List.of());
      declaration.openSubjectPicker(player, attacker, defender, people);
      assertNull(people.getItem(0));
      declaration.openSettlementPicker(player, attacker, defender);
      Inventory towns = top();
      assertEquals("town25", id(towns.getItem(25)));
      pillage.when(() -> PillageEligibility.options(attacker, defender)).thenReturn(List.of());
      declaration.openSettlementPicker(player, attacker, defender, towns);
      assertNull(towns.getItem(25));
      declaration.openRelationTypePicker(player, attacker, defender);
      Inventory types = top();
      assertNotNull(types.getItem(25));
      assertEquals(Material.BARRIER, types.getItem(26).getType());
      RelationLoader.types.removeIf(
          t -> t.getId().startsWith("vassal") && !t.getId().equals("vassal"));
      declaration.openRelationTypePicker(player, attacker, defender, types);
      assertNull(types.getItem(1));
    }
  }

  @Test
  void governmentAndLeadershipSelectionsCanBeChangedIndependentlyAndBackKeepsThem() {
    fixture.law("government", "republic", Map.of());
    fixture.law("leadership", "elected", Map.of());
    declaration.openGovernmentPicker(player, attacker, defender, null, null);
    Inventory inventory = top();
    assertEquals("government", id(inventory.getItem(11)));
    assertEquals("leadership", id(inventory.getItem(15)));
    assertNull(inventory.getItem(22));
    assertTrue(lore(inventory.getItem(11)).contains("Selected: Current government"));
    declaration.click(fixture.ui.click(player, 15), inventory, player);
    Inventory lawMenu = top();
    assertTrue(lore(lawMenu.getItem(findId(lawMenu, "current"))).contains("Currently selected"));
    declaration.click(fixture.ui.click(player, findId(lawMenu, "elected")), lawMenu, player);
    inventory = top();
    assertEquals("elected", ((DeclareWarHolder) inventory.getHolder()).getLeadershipLawId());
    assertNotNull(inventory.getItem(22));
    declaration.click(fixture.ui.click(player, 11), inventory, player);
    lawMenu = top();
    declaration.click(fixture.ui.click(player, 26), lawMenu, player);
    inventory = top();
    assertEquals("elected", ((DeclareWarHolder) inventory.getHolder()).getLeadershipLawId());
    declaration.openGovernmentPicker(player, attacker, defender, "MISSING", "elected", inventory);
    assertFalse(lore(inventory.getItem(11)).contains("Selected:"));
    for (int n = 0; n < 27; n++) fixture.law("leadership", "law" + n, Map.of());
    declaration.click(fixture.ui.click(player, 15), inventory, player);
    assertNotNull(top().getItem(25));
    assertEquals(Material.BARRIER, top().getItem(26).getType());
  }

  @ParameterizedTest
  @EnumSource(WarGoalType.class)
  void decliningConfirmationReturnsToTheRelevantPickerWithoutDeclaring(WarGoalType goal) {
    declaration.handleConfirm(player, WarDeclareRequest.of(attacker, defender, goal), false);
    SFGUI expected =
        switch (goal) {
          case CHANGE_GOVERNMENT -> SFGUI.WAR_DECLARE_GOVERNMENT;
          case SUBJUGATE -> SFGUI.WAR_DECLARE_RELATION_TYPE;
          case DE_JURE_ANNEX -> SFGUI.WAR_DECLARE_TITLE;
          case TRANSFER_SUBJECT -> SFGUI.WAR_DECLARE_SUBJECT;
          case PILLAGE -> SFGUI.WAR_DECLARE_SETTLEMENT;
          default -> SFGUI.WAR_DECLARE_GOAL;
        };
    assertEquals(expected, ((DeclareWarHolder) top().getHolder()).getStep());
    verify(navigation, never()).warList(player);
  }

  @Test
  void backingOutOfAnUnpinnedSubPickerReturnsToGoalsAndPinnedOneReturnsToDiplomacy() {
    declaration.openRelationTypePicker(player, attacker, defender);
    declaration.click(fixture.ui.click(player, 26), top(), player);
    assertEquals(SFGUI.WAR_DECLARE_GOAL, ((DeclareWarHolder) top().getHolder()).getStep());
    try (MockedStatic<WarDeclareCodeService> codes = mockStatic(WarDeclareCodeService.class)) {
      codes
          .when(() -> WarDeclareCodeService.session(player))
          .thenReturn(
              new WarDeclareCodeService.Session(
                  "approved", "attacker", "defender", WarGoalType.SUBJUGATE));
      declaration.openRelationTypePicker(player, attacker, defender);
      declaration.click(fixture.ui.click(player, 26), top(), player);
      codes.verify(() -> WarDeclareCodeService.clearSession(player));
      verify(navigation).diplomacyView(null, player, defender, true);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "empty",
        "air",
        "missing_id",
        "missing_goal",
        "unknown_goal",
        "missing_attacker",
        "missing_defender",
        "blocked_title",
        "blocked_settlement",
        "other_holder"
      })
  void invalidOrStaleDeclarationControlsHaveNoConfirmationSideEffects(String condition) {
    SFGUI step =
        condition.contains("title")
            ? SFGUI.WAR_DECLARE_TITLE
            : condition.contains("settlement")
                ? SFGUI.WAR_DECLARE_SETTLEMENT
                : condition.contains("id") ? SFGUI.WAR_DECLARE_SUBJECT : SFGUI.WAR_DECLARE_GOAL;
    Inventory inventory =
        fixture.ui.inventory(
            condition.equals("other_holder")
                ? null
                : new DeclareWarHolder("attacker", "defender", step),
            27,
            "Declare");
    ItemStack item = tag("selection");
    if (condition.equals("empty")) item = null;
    if (condition.equals("air")) item = new ItemStack(Material.AIR);
    if (condition.equals("missing_id")) item = new ItemStack(Material.PAPER);
    if (condition.equals("unknown_goal")) {
      ItemMeta meta = item.getItemMeta();
      meta.getPersistentDataContainer()
          .set(new NamespacedKey(fixture.ui.plugin, "goal"), PersistentDataType.STRING, "unknown");
      item.setItemMeta(meta);
    }
    if (condition.equals("missing_attacker")) FactionManager.factions.remove(attacker);
    if (condition.equals("missing_defender")) FactionManager.factions.remove(defender);
    inventory.setItem(0, item);
    player.openInventory(inventory);
    declaration.click(fixture.ui.click(player, 0), inventory, player);
    verify(navigation, never()).confirmWarDeclareView(any(), any());
    assertSame(inventory, top());
  }

  @Test
  void movementOnlyAndNullGoalsDoNotOpenOrdinaryDeclarationMenus() {
    for (WarGoalType goal :
        List.of(
            WarGoalType.OVERTHROW,
            WarGoalType.CHANGE_LAW,
            WarGoalType.CHANGE_TAX,
            WarGoalType.FORCE_PEACE)) declaration.routeGoal(player, attacker, defender, goal);
    declaration.routeGoal(player, attacker, defender, null);
    verify(player, never()).openInventory(any(Inventory.class));
    verify(navigation, never()).confirmWarDeclareView(any(), any());
  }

  @ParameterizedTest
  @EnumSource(
      value = WarGoalType.class,
      names = {
        "WAR",
        "DE_JURE_ANNEX",
        "SUBJUGATE",
        "TRANSFER_SUBJECT",
        "USURP",
        "OPEN_MARKET",
        "CHANGE_GOVERNMENT",
        "PILLAGE"
      })
  void confirmationSummaryShowsTheResolvedTermsAndTarget(WarGoalType goal) {
    Title title = fixture.title("Defender Crown", "county", 20);
    defender.addTitle(title);
    Law government = fixture.law("government", "republic", Map.of());
    Law leadership = fixture.law("leadership", "elected", Map.of());
    Settlement settlement = mock(Settlement.class);
    when(settlement.getName()).thenReturn("Trade Town");
    String previous = Cache.openMarketApplyDefenderLaw;
    Cache.openMarketApplyDefenderLaw = "republic";
    try (MockedStatic<PillageEligibility> pillage = mockStatic(PillageEligibility.class)) {
      pillage.when(() -> PillageEligibility.findSettlement("town", defender)).thenReturn(settlement);
      WarDeclareRequest request =
          new WarDeclareRequest(
              attacker,
              defender,
              goal,
              title.getId(),
              attacker.getId(),
              "vassal",
              "republic",
              "elected",
              "town");
      ItemStack item = declaration.creator.createConfirmSummaryItem(request);
      String text = lore(item);
      assertEquals("Declare War?", name(item));
      assertTrue(text.contains("Target: defender"));
      assertTrue(text.contains("Goal: " + goal.getDisplayName()));
      assertTrue(text.contains("cannot be undone easily"));
      switch (goal) {
        case DE_JURE_ANNEX -> assertTrue(text.contains("Title: Defender Crown"));
        case SUBJUGATE ->
            assertTrue(
                text.contains("Subject type: " + RelationLoader.getType("vassal").getName()));
        case TRANSFER_SUBJECT -> assertTrue(text.contains("Subject: attacker"));
        case USURP -> assertTrue(text.contains("Primary title: Defender Crown"));
        case OPEN_MARKET -> assertTrue(text.contains("Law: republic"));
        case CHANGE_GOVERNMENT -> {
          assertTrue(text.contains("Government: republic"));
          assertTrue(text.contains("Leadership: elected"));
        }
        case PILLAGE -> assertTrue(text.contains("Settlement: Trade Town"));
        default -> assertTrue(text.contains("Goal: War"));
      }
    } finally {
      Cache.openMarketApplyDefenderLaw = previous;
    }
  }

  @Test
  void relationTypeItemExplainsAnExistingLimitButPreservesTheSelectedType() {
    var type = RelationLoader.getType("vassal");
    try (MockedStatic<RelationManager> relations =
        mockStatic(RelationManager.class, CALLS_REAL_METHODS)) {
      relations.when(() -> RelationManager.atLimit(attacker, type)).thenReturn(true);
      ItemStack item = declaration.creator.createRelationTypeItem(type, attacker);
      assertEquals("vassal", id(item));
      assertTrue(lore(item).contains("At limit"));
    }
  }

  @Test
  void counterGoalBackReturnsToWarAndTheAttackerCannotEditTheDefenderChoice() {
    view.warView(null, defending, war, true);
    view.click(
        fixture.ui.click(defending, 48), defending.getOpenInventory().getTopInventory(), defending);
    view.click(
        fixture.ui.click(defending, 26), defending.getOpenInventory().getTopInventory(), defending);
    assertEquals(
        SFGUI.WAR_VIEW,
        ((WarInventoryHolder) defending.getOpenInventory().getTopInventory().getHolder())
            .getType());
    view.warView(null, player, war, true);
    Inventory inventory = top();
    view.click(fixture.ui.click(player, 48), inventory, player);
    assertSame(inventory, top());
    assertNull(war.getDefenderCounterGoal());
    verify(player)
        .sendMessage(
            "§cOnly the defending nation's leader can set a counter goal before the first battle"
                + " begins.");
  }

  @Test
  void defendingCompanyContractsOpenAndExpiredContractButtonsHaveNoEffect() {
    Engagement company = engagement("defending-contract");
    try (MockedStatic<MercenaryEngagements> companies = mockStatic(MercenaryEngagements.class)) {
      companies
          .when(() -> MercenaryEngagements.on(war, war.getDefenders()))
          .thenReturn(List.of(company));
      view.warView(null, player, war, true);
      Inventory inventory = top();
      assertEquals("defending-contract", contractId(inventory.getItem(15)));
      view.click(fixture.ui.click(player, 15), inventory, player);
      verify(navigation)
          .contractDetailView(player, attacker.getOrCreateMainGuild(), "defending-contract");
      Engagement replacement = engagement("replacement-contract");
      companies
          .when(() -> MercenaryEngagements.on(war, war.getDefenders()))
          .thenReturn(List.of(replacement));
      view.click(fixture.ui.click(player, 15), inventory, player);
      verify(navigation, times(1)).contractDetailView(any(), any(), anyString());
      assertSame(inventory, top());
    }
  }

  @Test
  void reloadedAwayGovernmentGroupsDoNotOpenInvalidLawPickers() {
    declaration.openGovernmentPicker(player, attacker, defender, null, null);
    Inventory inventory = top();
    assertTrue(lore(inventory.getItem(11)).contains("Selected: Current government"));
    net.tfminecraft.simplefactions.loaders.LawLoader.map.remove("government");
    FactionManager.factions.remove(defender);
    fixture.saved("defender", "Defender");
    declaration.click(fixture.ui.click(player, 11), inventory, player);
    assertSame(inventory, top());
    verify(navigation, never()).confirmWarDeclareView(any(), any());
  }

  private int findId(Inventory inventory, String value) {
    for (int n = 0; n < inventory.getSize(); n++) {
      ItemStack item = inventory.getItem(n);
      if (item != null && value.equals(id(item))) return n;
    }
    throw new AssertionError("Missing menu item " + value);
  }

  private MockedConstruction<WarGoalValidator> validating(WarValidationResult result) {
    return mockConstruction(
        WarGoalValidator.class,
        (validator, context) -> when(validator.validate(any())).thenReturn(result));
  }

  private Faction ally(String id, String leader) {
    Faction ally = fixture.saved(id, leader);
    ally.getOrCreateMainGuild();
    var relation = fixture.relationType("ally", Map.of("name", "Ally"));
    attacker.setRelation(ally, new Relation(relation, RelationLoader.getDefaultAttitude()));
    ally.setRelation(attacker, new Relation(relation, RelationLoader.getDefaultAttitude()));
    war.update();
    return ally;
  }

  private Engagement engagement(String id) {
    MercenaryCompany company = mock(MercenaryCompany.class);
    MercenaryContract contract = mock(MercenaryContract.class);
    when(company.getGuild()).thenReturn(attacker.getOrCreateMainGuild());
    when(company.getName()).thenReturn("Company " + id);
    when(company.getReputationString()).thenReturn("Trusted");
    when(contract.getId()).thenReturn(id);
    when(contract.getSlots()).thenReturn(3);
    when(contract.getDaysRemaining()).thenReturn(4);
    return new Engagement(company, contract);
  }

  private String contractId(ItemStack item) {
    return item.getItemMeta()
        .getPersistentDataContainer()
        .get(Keys.CONTRACT_ID, PersistentDataType.STRING);
  }

  private Integer integer(ItemStack item, String key) {
    return item.getItemMeta()
        .getPersistentDataContainer()
        .get(new NamespacedKey(fixture.ui.plugin, key), PersistentDataType.INTEGER);
  }

  private String string(ItemStack item, String key) {
    return item.getItemMeta()
        .getPersistentDataContainer()
        .get(new NamespacedKey(fixture.ui.plugin, key), PersistentDataType.STRING);
  }

  private Inventory top() {
    return player.getOpenInventory().getTopInventory();
  }

  private String id(ItemStack item) {
    return item.getItemMeta()
        .getPersistentDataContainer()
        .get(new NamespacedKey(fixture.ui.plugin, "id"), PersistentDataType.STRING);
  }

  private ItemStack tag(String id) {
    ItemStack item = new ItemStack(Material.PAPER);
    ItemMeta meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(new NamespacedKey(fixture.ui.plugin, "id"), PersistentDataType.STRING, id);
    item.setItemMeta(meta);
    return item;
  }

  private static String name(ItemStack item) {
    return ChatColor.stripColor(item.getItemMeta().getDisplayName());
  }

  private static String lore(ItemStack item) {
    return item.getItemMeta().getLore() == null
        ? ""
        : String.join(
            "\n", item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList());
  }
}
