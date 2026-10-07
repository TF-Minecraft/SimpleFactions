package net.tfminecraft.simplefactions.government;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Stance;
import net.tfminecraft.simplefactions.government.handler.ProposalHandler;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.movement.Pool;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.FeeChange;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxLawChange;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class PoliticalObjectsCoverageTest {
  private FactionDomainFixture fixture;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
  }

  @AfterEach
  void cleanup() {
    if (fixture != null) fixture.close();
  }

  private Faction host() {
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    faction.addMember("Cara");
    return faction;
  }

  private Proposal political(Faction faction, Action action) {
    Proposal proposal = new Proposal("Bob", faction.getGovernment());
    proposal.setPoliticalActionProposal(new PoliticalAction(action));
    return proposal;
  }

  private PoliticalAction restricted(String... pools) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("action.pools", List.of(pools));
    return new PoliticalAction("CHANGE_LEADER", yaml.getConfigurationSection("action"));
  }

  private Movement movement(Faction faction) {
    faction.getGovernment().startMovement("Bob", political(faction, Action.CHANGE_LEADER));
    return faction.getGovernment().getMovementByLeader("Bob");
  }

  private String pages(ItemStack item) {
    assertEquals(Material.WRITTEN_BOOK, item.getType());
    return org.bukkit.ChatColor.stripColor(
        String.join("\n", ((BookMeta) item.getItemMeta()).getPages()));
  }

  @Test
  void causeRejectsCitizensOutsideItsPermittedPoolAndDropsDepartedMembers() {
    Faction faction = host();
    Movement movement = movement(faction);
    Cause cause = movement.getCauses().getFirst();
    assertSame(movement, cause.getMovement());
    assertEquals(Action.CHANGE_LEADER, cause.getAction());
    assertEquals(0, cause.getIndex());
    assertEquals(List.of("Bob"), cause.getCitizenList());
    assertEquals(List.of("Bob"), cause.getFullMemberList());
    assertNotNull(cause.joinBlockReason(42, true));
    assertNotNull(cause.memberBlockReason("Alice", true, true));
    assertFalse(cause.canBeLeader(null));
    assertFalse(cause.canBeLeader("Cara"));
    cause.getProposal().setPoliticalActionProposal(restricted("guilds"));
    assertNotNull(cause.memberBlockReason("Cara", true, true));
    assertFalse(cause.canBeLeader("Bob"));
    cause.join("Cara");
    assertEquals(List.of("Bob"), cause.getCitizenList());
    cause.getProposal().setPoliticalActionProposal(restricted("citizens"));
    cause.join("Cara");
    cause.join("Cara");
    assertEquals(List.of("Bob", "Cara"), cause.getCitizenList());
    faction.forceRemoveMember("Bob");
    cause.tick();
    assertNull(cause.getLeader());
    assertEquals(List.of("Cara"), cause.getFullMemberList());
    assertTrue(cause.canBeLeader("Cara"));
    cause.leave("Cara");
    cause.tick();
    assertFalse(movement.getCauses().contains(cause));
  }

  @Test
  void guildCauseEligibilityUsesPoolRoleRealmStanceAndExistingMembership() {
    Faction faction = host();
    Movement movement = movement(faction);
    Cause cause = movement.getCauses().getFirst();
    Guild guild = fixture.guild(faction, "merchants", "Drew");
    guild.addMember("Eve");
    guild.setStance(Stance.OPPOSE);
    cause.getProposal().setPoliticalActionProposal(restricted("citizens"));
    assertNotNull(cause.guildBlockReason(guild, true, true));
    cause.getProposal().setPoliticalActionProposal(restricted("guilds", "citizens"));
    assertNotNull(cause.guildBlockReason(faction.getOrCreateMainGuild(), true, false));
    Guild foreign = fixture.guild(fixture.saved("foreign", "Finn"), "foreign_guild", "Grace");
    assertNotNull(cause.guildBlockReason(foreign, true, true));
    guild.setStance(Stance.SUPPORT);
    assertNotNull(cause.guildBlockReason(guild, true, false));
    guild.setStance(Stance.OPPOSE);
    assertNull(cause.guildBlockReason(guild, true, true));
    cause.join(guild);
    assertEquals(List.of(guild), cause.getPool().getGuilds());
    assertTrue(cause.canBeLeader("Drew"));
    assertTrue(cause.canBeLeader("Eve"));
    assertNotNull(cause.joinBlockReason(guild, true));
    cause.leave(guild);
    assertTrue(cause.getPool().getGuilds().isEmpty());
    fixture.provinceData.put(1, new Province(1, "PLAINS", 1));
    faction.addProvince(1);
    assertNotNull(cause.guildBlockReason(guild, true, false));
  }

  @Test
  void factionCauseEligibilityUsesPoolVassalageStanceAndExistingMembership() {
    Faction faction = host();
    Movement movement = movement(faction);
    Cause cause = movement.getCauses().getFirst();
    Faction vassal = fixture.saved("vassal", "Drew");
    vassal.addMember("Eve");
    cause.getProposal().setPoliticalActionProposal(restricted("citizens"));
    assertNotNull(cause.factionBlockReason(vassal, true, true));
    cause.getProposal().setPoliticalActionProposal(restricted("factions", "citizens"));
    assertNotNull(cause.factionBlockReason(faction, true, true));
    assertNotNull(cause.factionBlockReason(vassal, true, false));
    fixture.subject(faction, vassal);
    vassal.getOrCreateMainGuild().setStance(Stance.SUPPORT);
    assertNotNull(cause.factionBlockReason(vassal, true, true));
    vassal.getOrCreateMainGuild().setStance(Stance.OPPOSE);
    cause.join(vassal);
    assertEquals(List.of(vassal), cause.getPool().getFactions());
    assertNotNull(cause.factionBlockReason(vassal, true, false));
    assertTrue(cause.canBeLeader("Drew"));
    assertFalse(cause.canBeLeader("Eve"));
    cause.leave(vassal);
    assertTrue(cause.getPool().getFactions().isEmpty());
  }

  @Test
  void persistedCauseWithoutAnEligibleFounderRetainsExplicitPoolAndRequiresANewLeader() {
    Faction faction = host();
    Movement movement = movement(faction);
    Cause restored = new Cause(movement, political(faction, Action.NONE), "Departed");
    assertFalse(restored.hasLeader());
    assertTrue(restored.isEmpty());
    restored.setPool(
        new Pool(
            new java.util.ArrayList<>(List.of("Cara")),
            new java.util.ArrayList<>(),
            new java.util.ArrayList<>()));
    movement.addCause(restored);
    assertEquals(1, restored.getIndex());
    assertTrue(restored.canBeLeader("Cara"));
    restored.setLeader("Cara");
    restored.tick();
    assertEquals("Cara", restored.getLeader());
    assertTrue(movement.getCauses().contains(restored));
  }

  @Test
  void lawApplicationHonorsTheCurrentSwitchLockWhileSuccessfulMovementsCanOverrideIt() {
    fixture.lawGroup("finance", Map.of());
    Faction faction = host();
    Law next = fixture.law("finance", "reform", Map.of());
    LawGroup group = faction.getLawHandler().getGroup("finance");
    Proposal proposal = new Proposal("Bob", faction.getGovernment());
    proposal.setLawProposal(next);
    assertEquals("Bob", proposal.getProposer());
    assertSame(next, proposal.getLaw());
    assertEquals(Action.LAW_CHANGE, proposal.getPoliticalAction().getAction());
    double previousLock = Cache.lawSwitchLockDays;
    try {
      Cache.lawSwitchLockDays = 1;
      group.setChangedAt(System.currentTimeMillis());
      Law original = group.getCurrent();
      proposal.apply(null);
      assertSame(original, group.getCurrent());
      Cause cause = new Cause(new Movement(faction, "Bob", proposal), proposal, "Bob");
      proposal.apply(cause);
      assertSame(next, group.getCurrent());
    } finally {
      Cache.lawSwitchLockDays = previousLock;
    }
  }

  @ParameterizedTest
  @EnumSource(TaxTarget.class)
  void taxProposalsApplyOnlyTheirIntendedRateAndRenderCurrentAndProposedValues(TaxTarget target) {
    Faction faction = host();
    String id =
        switch (target) {
          case GUILD_ID, VASSAL_ID, TARIFF_ID -> "missing_target";
          default -> null;
        };
    TaxLawChange tax = new TaxLawChange(target, id, 17);
    Proposal proposal = new Proposal("Bob", faction.getGovernment());
    proposal.setTaxProposal(tax);
    assertSame(tax, proposal.getTaxChange());
    assertTrue(proposal.affectsEconomy());
    assertFalse(proposal.needsTarget());
    assertTrue(proposal.checkTarget());
    assertEquals(Action.TAX_CHANGE, proposal.getPoliticalAction().getAction());
    String book = pages(proposal.getAsBook());
    assertTrue(book.contains("17.0%"));
    assertTrue(book.contains("Economic preview unavailable"));
    if (id != null) {
      assertTrue(book.contains(id));
      assertTrue(book.contains("Base Rate"));
    }
    proposal.apply(null);
    assertEquals(17, faction.getTaxRate(target, id, false));
  }

  @ParameterizedTest
  @EnumSource(FeeKind.class)
  void vehicleFeeProposalsApplyTheScopedRateAndDescribeGeneralAndSpecificTargets(FeeKind kind) {
    Faction faction = host();
    Proposal general = new Proposal("Bob", faction.getGovernment());
    general.setFeeProposal(new FeeChange(kind, null, 1.5));
    assertTrue(general.isFeeProposal());
    assertEquals(Action.TAX_CHANGE, general.getPoliticalAction().getAction());
    general.apply(null);
    assertEquals(1.5, faction.getVehicleFeeHandler().getRate(kind));
    assertTrue(pages(general.getAsBook()).contains("All vehicles"));
    Proposal specific = new Proposal("Bob", faction.getGovernment());
    FeeChange fee = new FeeChange(kind, "tf:cart", 2.5);
    specific.setFeeProposal(fee);
    assertSame(fee, specific.getFeeChange());
    specific.apply(null);
    assertEquals(2.5, faction.getVehicleFeeHandler().getRate(kind, "tf:cart"));
    assertEquals(1.5, faction.getVehicleFeeHandler().getRate(kind));
    assertTrue(pages(specific.getAsBook()).contains("General Rate"));
    assertTrue(pages(specific.getAsBook()).contains("tf:cart"));
  }

  @ParameterizedTest
  @EnumSource(Action.class)
  void politicalBooksDescribeEachActionAndItsTargetRequirement(Action action) {
    Faction faction = host();
    Proposal proposal = political(faction, action);
    assertFalse(proposal.affectsEconomy());
    String book = pages(proposal.getAsBook());
    assertTrue(
        book.contains(org.bukkit.ChatColor.stripColor(action.getDisplay()).replace("#c5e0e3", "")));
    boolean needs =
        action == Action.CHANGE_LEADER
            || action == Action.WHITE_PEACE
            || action == Action.SURRENDER;
    assertEquals(needs, proposal.needsTarget());
    assertEquals(!needs, proposal.checkTarget());
    if (needs) assertTrue(book.contains("No target"));
  }

  @Test
  void leaderTargetsAreRevalidatedBeforeDisplayAndPoliticalApplication() {
    Faction faction = host();
    Proposal proposal = political(faction, Action.CHANGE_LEADER);
    proposal.setTarget("Cara");
    proposal.tick();
    assertTrue(proposal.hasTarget());
    assertTrue(pages(proposal.getAsBook()).contains("Cara"));
    faction.forceRemoveMember("Cara");
    proposal.tick();
    assertFalse(proposal.hasTarget());
    proposal.setTarget("Bob");
    assertTrue(proposal.checkTarget());
    proposal.apply(null);
    assertEquals("Bob", faction.getLeader());
  }

  @Test
  void warTargetsShowLiveWarsAndDiscardInvalidOrFinishedWarIds() {
    Faction faction = host();
    Faction enemy = fixture.saved("enemy", "Drew");
    var war = new net.tfminecraft.simplefactions.war.core.War(19, faction, enemy);
    WarManager.get().add(war);
    Proposal proposal = political(faction, Action.WHITE_PEACE);
    proposal.setTarget("19");
    assertTrue(proposal.checkTarget());
    assertTrue(
        pages(proposal.getAsBook()).contains(org.bukkit.ChatColor.stripColor(war.getName())));
    proposal.setTarget("not_a_war");
    assertTrue(pages(proposal.getAsBook()).contains("Invalid war"));
    proposal.tick();
    assertFalse(proposal.hasTarget());
    proposal.setTarget("19");
    WarManager.get().clear();
    proposal.tick();
    assertFalse(proposal.hasTarget());
  }

  @Test
  void booksForUnavailableGovernmentsAndEmptyProposalsRemainReadable() {
    Proposal empty = new Proposal(null, null);
    assertFalse(empty.affectsEconomy());
    assertNull(empty.getPoliticalAction());
    BookMeta meta = (BookMeta) empty.getAsBook().getItemMeta();
    assertEquals("Unknown", meta.getAuthor());
    assertEquals(1, meta.getPageCount());
    Proposal tax = new Proposal("Bob", null);
    tax.setTaxProposal(new TaxLawChange(TaxTarget.CITIZENS, null, 12));
    assertTrue(pages(tax.getAsBook()).contains("12.0%"));
    Proposal fee = new Proposal("Bob", null);
    fee.setFeeProposal(new FeeChange(FeeKind.TRANSFER_FEE, null, 3));
    assertTrue(pages(fee.getAsBook()).contains("3.0x upkeep"));
    Law detached = fixture.law("removed_group", "law", Map.of("name", "Detached law"));
    Proposal law = new Proposal("Bob", null);
    law.setLawProposal(detached);
    assertTrue(pages(law.getAsBook()).contains("removed_group"));
    assertTrue(pages(law.getAsBook()).contains("Detached law"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"citizens", "guilds", "factions", "vassals"})
  void configuredActionsRetainNamesDescriptionsIconsAndAllowedPools(String pool) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("action.name", "Public reform");
    yaml.set("action.icon", "v.book");
    yaml.set("action.description", List.of("First line", "Second line"));
    yaml.set("action.pools", List.of(pool));
    PoliticalAction action =
        new PoliticalAction("change_leader", yaml.getConfigurationSection("action"));
    assertEquals(Action.CHANGE_LEADER, action.getAction());
    assertEquals("Public reform", action.getName());
    assertEquals(List.of("First line", "Second line"), action.getDescription());
    assertEquals(List.of(pool), action.getPools());
    assertEquals(pool.equals("citizens"), action.allowCitizens());
    assertEquals(pool.equals("guilds"), action.allowGuilds());
    assertEquals(pool.equals("factions") || pool.equals("vassals"), action.allowFactions());
    assertEquals("v.book", action.getIconString());
    ItemAPI api = mock(ItemAPI.class);
    ItemCreator creator = mock(ItemCreator.class);
    when(api.getCreator()).thenReturn(creator);
    ItemStack book = new ItemStack(Material.BOOK);
    when(creator.getItemFromPath("v.book")).thenReturn(book);
    try (MockedStatic<TLibs> tlibs = mockStatic(TLibs.class)) {
      tlibs.when(TLibs::getItemAPI).thenReturn(api);
      assertSame(book, action.getIcon());
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> new PoliticalAction("removed_action", yaml.getConfigurationSection("action")));
  }

  @Test
  void actionKeysRemainStableUnderATurkishServerLocale() {
    java.util.Locale previous = java.util.Locale.getDefault();
    try {
      java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("action.pools", List.of("citizens"));
      PoliticalAction action =
          assertDoesNotThrow(
              () -> new PoliticalAction("independence", yaml.getConfigurationSection("action")));
      assertEquals(Action.INDEPENDENCE, action.getAction());
    } finally {
      java.util.Locale.setDefault(previous);
    }
  }

  @Test
  void onlineEconomicBooksPreviewLawTaxAndTariffsWithoutApplyingTheirChanges() {
    fixture.lawGroup("government", Map.of());
    Faction faction = host();
    Guild viewer = faction.getOrCreateMainGuild();
    var player = fixture.player("Bob");
    Law next = fixture.law("government", "reform", Map.of("name", "Reformed government"));
    LawGroup group = faction.getLawHandler().getGroup("government");
    Law original = group.getCurrent();
    when(fixture.provinces.previewLawIncomeExact(faction, group, next))
        .thenReturn(Map.of(viewer, 4.5));
    Proposal law = new Proposal("Bob", faction.getGovernment());
    law.setLawProposal(next);
    assertTrue(law.affectsEconomy());
    String lawBook = pages(law.getAsBook(player));
    assertTrue(lawBook.contains("Reformed government"));
    assertTrue(lawBook.contains("+4.50d"));
    assertSame(original, group.getCurrent());
    assertTrue(pages(law.getAsBook()).contains("Economic preview unavailable"));
    Proposal tax = new Proposal("Bob", faction.getGovernment());
    tax.setTaxProposal(new TaxLawChange(TaxTarget.CITIZENS, null, 17));
    assertTrue(pages(tax.getAsBook(player)).contains("Impacts:"));
    assertEquals(5, faction.getTaxRate());
    when(fixture.provinces.previewTariffRateChange(faction, null, 18))
        .thenReturn(Map.of(viewer, -2.5));
    Proposal tariffs = new Proposal("Bob", faction.getGovernment());
    tariffs.setTaxProposal(new TaxLawChange(TaxTarget.TARIFFS, null, 18));
    assertTrue(pages(tariffs.getAsBook(player)).contains("-2.50d"));
    assertEquals(30, faction.getTaxRate(TaxTarget.TARIFFS, null, false));
  }

  @Test
  void duplicateGeneralTaxProposalIsRejectedWithoutDereferencingItsAbsentSpecificId() {
    Faction faction = host();
    ProposalHandler handler = new ProposalHandler(faction.getGovernment());
    Proposal original = new Proposal("Bob", faction.getGovernment());
    original.setTaxProposal(new TaxLawChange(TaxTarget.CITIZENS, null, 12));
    Proposal replacement = new Proposal("Cara", faction.getGovernment());
    replacement.setTaxProposal(new TaxLawChange(TaxTarget.CITIZENS, null, 18));
    handler.propose(original);
    assertFalse(assertDoesNotThrow(() -> handler.canBeProposed(replacement)));
    assertEquals(List.of(original), handler.getProposals());
  }

  @Test
  void pendingPoliticalActionsAndGeneralTaxIdentitySurviveProposalSerialization() {
    Faction faction = host();
    ProposalHandler handler = new ProposalHandler(faction.getGovernment());
    Proposal leader = political(faction, Action.CHANGE_LEADER);
    leader.setTarget("Cara");
    handler.propose(leader);
    Proposal peace = political(faction, Action.WHITE_PEACE);
    peace.setTarget("19");
    handler.propose(peace);
    Proposal elections = political(faction, Action.SNAP_ELECTIONS);
    handler.propose(elections);
    Proposal tax = new Proposal("Bob", faction.getGovernment());
    tax.setTaxProposal(new TaxLawChange(TaxTarget.CITIZENS, null, 17));
    handler.propose(tax);
    ProposalHandler restored = new ProposalHandler(faction.getGovernment());
    restored.restoreProposals(faction, handler.serializeProposals());
    assertEquals(4, restored.getProposals().size());
    assertEquals(
        Action.CHANGE_LEADER, restored.getProposals().get(0).getPoliticalAction().getAction());
    assertEquals("Cara", restored.getProposals().get(0).getTarget());
    assertEquals(
        Action.WHITE_PEACE, restored.getProposals().get(1).getPoliticalAction().getAction());
    assertEquals("19", restored.getProposals().get(1).getTarget());
    assertEquals(
        Action.SNAP_ELECTIONS, restored.getProposals().get(2).getPoliticalAction().getAction());
    assertNull(restored.getProposals().get(2).getTarget());
    assertNull(restored.getProposals().get(3).getTaxChange().getId());
    assertEquals(17, restored.getProposals().get(3).getTaxChange().getNewTax());
  }

  @Test
  void malformedSavedProposalEntriesDoNotPreventValidSiblingsFromRestoring() {
    Faction faction = host();
    ProposalHandler handler = new ProposalHandler(faction.getGovernment());
    List<String> saved =
        java.util.Arrays.asList(
            null,
            "missing-separator",
            "Bob:fee:bad",
            "Bob:tax:REMOVED:null:3",
            "Bob:law:missing:removed",
            "Bob:action:REMOVED:Target",
            "Bob:tax:CITIZENS:null:17");
    assertDoesNotThrow(() -> handler.restoreProposals(faction, saved));
    assertEquals(1, handler.getProposals().size());
    assertEquals(TaxTarget.CITIZENS, handler.getProposals().getFirst().getTaxChange().getTarget());
    assertNull(handler.getProposals().getFirst().getTaxChange().getId());
    assertEquals(17, handler.getProposals().getFirst().getTaxChange().getNewTax());
  }
}
