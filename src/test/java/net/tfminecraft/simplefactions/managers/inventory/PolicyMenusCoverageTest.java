package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.simplefactions.enums.*;
import net.tfminecraft.simplefactions.government.proposal.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.laws.*;
import net.tfminecraft.simplefactions.loaders.LawLoader;
import net.tfminecraft.simplefactions.managers.*;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PolicyMenusCoverageTest {
  private org.mockito.MockedStatic<net.tfminecraft.tlibs.TLibs> items;
  private FactionDomainFixture fixture;
  private InventoryManager inventory;
  private Player alice;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    inventory = new InventoryManager();
    items = mockStatic(net.tfminecraft.tlibs.TLibs.class);
    var api = mock(net.tfminecraft.tlibs.objects.api.ItemAPI.class);
    var creator = mock(net.tfminecraft.tlibs.objects.api.subapi.ItemCreator.class);
    when(api.getCreator()).thenReturn(creator);
    items.when(net.tfminecraft.tlibs.TLibs::getItemAPI).thenReturn(api);
    when(creator.getItemFromPath(anyString()))
        .thenAnswer(call -> new ItemStack(org.bukkit.Material.PAPER));
    when(creator.getItemsAdderItem(anyString()))
        .thenAnswer(call -> new ItemStack(org.bukkit.Material.PAPER));
    when(fixture.ui.plugin.getProvinceManager())
        .thenReturn(new net.tfminecraft.simplefactions.managers.ProvinceManager());
    alice = fixture.player("Alice");
  }

  @AfterEach
  void close() {
    if (items != null) items.close();
    fixture.close();
  }

  private Inventory top() {
    return alice.getOpenInventory().getTopInventory();
  }

  private String key(ItemStack item) {
    return item == null || item.getItemMeta() == null
        ? null
        : item.getItemMeta()
            .getPersistentDataContainer()
            .get(Keys.STRING_KEY, PersistentDataType.STRING);
  }

  private int named(Inventory menu, String text) {
    for (int slot = 0; slot < menu.getSize(); slot++) {
      ItemStack item = menu.getItem(slot);
      if (item != null
          && item.getItemMeta() != null
          && item.getItemMeta().getDisplayName().contains(text)) return slot;
    }
    return -1;
  }

  private net.tfminecraft.simplefactions.diplomacy.Relation relation(String type) {
    var r = new net.tfminecraft.simplefactions.diplomacy.Relation();
    r.setType(net.tfminecraft.simplefactions.loaders.RelationLoader.getType(type));
    return r;
  }

  private Set<String> collect(boolean laws) {
    Set<String> ids = new LinkedHashSet<>();
    for (int page = 0; page < 8; page++) {
      Inventory menu = top();
      for (ItemStack item : menu.getContents()) {
        String id = key(item);
        if (id != null && !id.contains("VIEW") && !id.contains("SELECT")) ids.add(id);
      }
      int next = named(menu, "Next");
      if (next < 0) break;
      if (laws) inventory.lawView.click(fixture.ui.click(alice, next), menu, alice);
      else inventory.taxView.click(fixture.ui.click(alice, next), menu, alice);
    }
    return ids;
  }

  @Test
  void allConfiguredLawGroupsRemainAccessibleBeyondFourteen() {
    for (int n = 0; n < 17; n++) fixture.lawGroup("group" + n, Map.of());
    Faction home = fixture.saved("home", "Alice");
    assertDoesNotThrow(() -> inventory.lawView.lawView(alice, home, null));
    assertEquals(LawLoader.map.keySet(), collect(true));
  }

  @Test
  void allPoliciesRemainAccessibleBeyondTheSelectionInventory() {
    fixture.lawGroup("government", Map.of());
    for (int n = 0; n < 30; n++) fixture.law("government", "policy" + n, Map.of());
    Faction home = fixture.saved("home", "Alice");
    LawGroup group = home.getLawHandler().getGroup("government");
    assertDoesNotThrow(() -> inventory.lawView.lawSelect(alice, home, group, null));
    assertEquals(group.getLaws().keySet(), collect(true));
    assertEquals("current", group.getCurrent().getId(), "Browsing must never enact a policy");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaxTarget.class,
      names = {"GUILD_ID", "VASSAL_ID", "TARIFF_ID"})
  void everySpecificTaxTargetIsAccessibleIncludingTheOldBackButtonSlot(TaxTarget target) {
    fixture.lawGroup(
        "tax",
        Map.of(
            "effects.faction.rules", List.of("GUILD_TAX true", "VASSAL_TAX true", "TARIFFS true")));
    Faction home = fixture.saved("home", "Alice");
    Set<String> expected = new LinkedHashSet<>();
    for (int n = 0; n < 55; n++) {
      if (target == TaxTarget.GUILD_ID) {
        Guild guild = fixture.guild(home, "guild" + n, "Leader" + n);
        expected.add(guild.getId());
      } else {
        Faction next = fixture.saved("foreign" + n, "Leader" + n);
        expected.add(next.getId());
        if (target == TaxTarget.VASSAL_ID) {
          home.getDiplomacyHandler().setRelation(next, relation("vassal"));
          next.getDiplomacyHandler().setRelation(home, relation("overlord"));
        }
      }
    }
    inventory.taxView.specificTaxView(alice, home, target, null);
    assertEquals(expected, collect(false));
  }

  @Test
  void lawGroupAndPolicyLoreDescribeCurrentEffectsAndProposalCostWithoutMutation() {
    fixture.regiment("guards", false, 1, 2);
    fixture.lawGroup(
        "government",
        Map.of(
            "description",
            List.of("Current charter"),
            "effects.faction.rules",
            List.of("CITIZEN_TAX true", "GUILD_TAX false"),
            "effects.faction.council-size",
            2));
    Law alternative =
        fixture.law(
            "government",
            "democracy",
            Map.ofEntries(
                Map.entry("description", List.of("Proposed charter")),
                Map.entry("cost", 30),
                Map.entry("upkeep", 2),
                Map.entry("effects.faction.rules", List.of("CITIZEN_TAX false", "GUILD_TAX true")),
                Map.entry("effects.faction.council-size", 3),
                Map.entry("effects.faction.brackets.citizen_tax", "2-8"),
                Map.entry("effects.faction.regiments", List.of("guards 2")),
                Map.entry("effects.faction.modifiers", List.of("PRESTIGE(4)")),
                Map.entry("effects.faction.our_territory", List.of("PRESTIGE(3)")),
                Map.entry("effects.domestic_guilds.rules", List.of("CITIZEN_TAX true"))));
    Faction home = fixture.saved("home", "Alice");
    LawGroup group = home.getLawHandler().getGroup("government");
    LawCreator creator = new LawCreator();
    ItemStack current = creator.createLawGroupItem(alice, home, group);
    assertEquals(group.getId(), key(current));
    assertTrue(String.join(" ", current.getItemMeta().getLore()).contains("Current charter"));
    ItemStack proposal = creator.createLawItem(alice, home, group, alternative, true);
    String lore = String.join(" ", proposal.getItemMeta().getLore());
    assertTrue(lore.contains("30"));
    assertTrue(lore.contains("Upkeep"));
    assertTrue(lore.contains("Council Size"));
    assertTrue(lore.contains("Proposed charter"));
    assertTrue(lore.contains("State Power Expectation"));
    assertEquals("democracy", key(proposal));
    assertEquals(
        "government",
        proposal
            .getItemMeta()
            .getPersistentDataContainer()
            .get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING));
    assertEquals("current", group.getCurrent().getId());
    assertTrue(!home.getGovernment().getCouncil().hasProposals());
  }

  @Test
  void groupsOpenPolicyChoicesAndPreviousNavigationClampsAfterPoliciesAreRemoved() {
    fixture.lawGroup("charter", Map.of());
    for (int n = 0; n < 30; n++) fixture.law("charter", "policy" + n, Map.of());
    Faction home = fixture.saved("home", "Alice");
    inventory.lawView.lawView(alice, home, null);
    Inventory groups = top();
    inventory.lawView.click(fixture.ui.click(alice, 10), groups, alice);
    Inventory choices = top();
    SFInventoryHolder holder = (SFInventoryHolder) choices.getHolder();
    assertEquals(SFGUI.LAW_SELECT, holder.getType());
    assertEquals("charter", holder.getSecondaryId());
    inventory.lawView.click(fixture.ui.click(alice, 25), choices, alice);
    assertEquals(1, holder.getPage());
    inventory.lawView.click(fixture.ui.click(alice, 24), choices, alice);
    assertEquals(0, holder.getPage());
    inventory.lawView.click(fixture.ui.click(alice, 25), choices, alice);
    home.getLawHandler()
        .getGroup("charter")
        .getLaws()
        .keySet()
        .removeIf(id -> !id.equals("current"));
    inventory.lawView.lawSelect(alice, home, home.getLawHandler().getGroup("charter"), choices);
    assertEquals(0, holder.getPage());
    assertEquals("current", key(choices.getItem(0)));
    assertNull(choices.getItem(25));
    inventory.lawView.lawView(alice, home, groups);
    alice.openInventory(groups);
    groups.setItem(10, new ItemStack(Material.PAPER));
    inventory.lawView.click(fixture.ui.click(alice, 10), groups, alice);
    assertSame(groups, top());
  }

  @Test
  void lawLoreExplainsRequirementsNoUpkeepAndPendingProposalWithoutEnactingIt() {
    fixture.lawGroup("charter", Map.of());
    Law alternative =
        fixture.law("charter", "reform", Map.of("requirements", List.of("has_law prerequisite")));
    Faction home = fixture.saved("home", "Alice");
    LawGroup group = home.getLawHandler().getGroup("charter");
    group.getDescription().add("Charter group description");
    LawCreator creator = new LawCreator();
    assertTrue(
        String.join(" ", creator.createLawGroupItem(alice, home, group).getItemMeta().getLore())
            .contains("Charter group description"));
    Proposal proposal = new Proposal("Alice", home.getGovernment());
    proposal.setLawProposal(alternative);
    home.getGovernment().propose(proposal);
    String lore =
        String.join(
            " ",
            creator.createLawItem(alice, home, group, alternative, true).getItemMeta().getLore());
    assertTrue(lore.contains("Requires law: prerequisite"));
    assertTrue(lore.contains("None"));
    assertTrue(lore.contains("already a proposal"));
    assertEquals("current", group.getCurrent().getId());
    assertEquals(
        List.of(proposal), home.getGovernment().getCouncil().getProposalHandler().getProposals());
  }

  @Test
  void topTaxMenuRoutesEachSpecificTypeAndIgnoresStaleItemKeys() {
    fixture.lawGroup(
        "tax",
        Map.of(
            "effects.faction.rules",
            List.of(
                "CITIZEN_TAX true",
                "GUILD_TAX true",
                "VASSAL_TAX true",
                "DIVIDEND_TAX true",
                "TARIFFS true")));
    Faction home = fixture.saved("home", "Alice");
    fixture.guild(home, "traders", "Trader");
    Faction foreign = fixture.saved("foreign", "Foreign");
    home.getDiplomacyHandler().setRelation(foreign, relation("vassal"));
    foreign.getDiplomacyHandler().setRelation(home, relation("overlord"));
    for (TaxTarget target : TaxTarget.values()) {
      inventory.taxView.taxView(alice, home);
      Inventory menu = top();
      int slot = -1;
      for (int n = 0; n < 17; n++) if (target.name().equals(key(menu.getItem(n)))) slot = n;
      assertTrue(slot >= 0, target.name());
      inventory.taxView.click(fixture.ui.click(alice, slot), menu, alice);
      if (target.name().endsWith("_ID")) {
        assertEquals(SFGUI.TAX_VIEW_SPECIFIC, ((SFInventoryHolder) top().getHolder()).getType());
        assertEquals(target.name(), ((SFInventoryHolder) top().getHolder()).getSecondaryId());
      } else assertSame(menu, top());
    }
    inventory.taxView.taxView(alice, home);
    Inventory menu = top();
    menu.setItem(0, new ItemStack(Material.PAPER));
    inventory.taxView.click(fixture.ui.click(alice, 0), menu, alice);
    var item = menu.getItem(0);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(Keys.STRING_KEY, PersistentDataType.STRING, "removed-tax-type");
    item.setItemMeta(meta);
    inventory.taxView.click(fixture.ui.click(alice, 0), menu, alice);
    assertSame(menu, top());
    assertEquals(5.0, home.getTaxHandler().getCitizenTax());
    FactionManager.factions.remove(home);
    inventory.taxView.click(fixture.ui.click(alice, 0), menu, alice);
    verify(alice).closeInventory();
  }

  @Test
  void foreignLawAndTaxViewsRemainReportedDuringRefresh() {
    fixture.lawGroup("charter", Map.of());
    Faction target = fixture.saved("foreign", "Foreign");
    Player foreign = fixture.player("Foreign");
    var spymaster = new net.tfminecraft.simplefactions.espionage.SpecialPositionAssignment();
    spymaster.playerName = foreign.getName();
    spymaster.playerId = foreign.getUniqueId();
    spymaster.characterId = "character-Foreign";
    target.getEspionage().appoint(spymaster, 80);
    fixture.saved("home", "Alice");
    inventory.lawView.lawView(alice, target, null);
    Inventory laws = top();
    assertTrue(((SFInventoryHolder) laws.getHolder()).isReported());
    inventory.lawView.lawView(alice, target, laws);
    assertSame(laws, top());
    inventory.taxView.taxView(alice, target);
    Inventory taxes = top();
    assertTrue(((SFInventoryHolder) taxes.getHolder()).isReported());
    inventory.taxView.taxView(alice, target, taxes);
    assertSame(taxes, top());
    inventory.taxView.taxView(alice, null);
    assertSame(taxes, top());
    assertFalse(target.getGovernment().getCouncil().hasProposals());
  }
}
