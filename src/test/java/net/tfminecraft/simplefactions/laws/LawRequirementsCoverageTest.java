package net.tfminecraft.simplefactions.laws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Brackets;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.government.handler.ProposalHandler;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.proposal.FeeChange;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxLawChange;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.loaders.PoliticalActionLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class LawRequirementsCoverageTest {
  private FactionDomainFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new FactionDomainFixture();
  }

  @AfterEach
  void tearDown() {
    fixture.close();
  }

  @Test
  void configuredLawScopesDoNotDependOnTheServerLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("effects.faction.modifiers", List.of("production(7.5)"));
      Law law = new Law("economy", "production", yaml);
      assertNotNull(law.getScopedEffects().get(Scope.FACTION));
      assertEquals(
          7.5,
          law.getScopedEffects()
              .get(Scope.FACTION)
              .getModifierAmount(FactionModifiers.PRODUCTION, null));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"rule", "bracket", "region"})
  void configuredEffectIdentifiersRemainValidUnderTurkishLocale(String kind) {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("rules", List.of("citizen_tax false"));
      yaml.set("brackets.citizen_tax", "5-15");
      yaml.set("foreign_territory", List.of("production(7.5)"));
      LawEffect effect = new LawEffect(Scope.FACTION, yaml);
      switch (kind) {
        case "rule" -> assertEquals(Boolean.FALSE, effect.getRules().get(Rules.CITIZEN_TAX));
        case "bracket" -> {
          assertNotNull(effect.getBrackets().get(Brackets.CITIZEN_TAX));
          assertEquals(15.0, effect.getBrackets().get(Brackets.CITIZEN_TAX).getMax());
        }
        case "region" ->
            assertEquals(
                7.5,
                effect.getModifierAmount(FactionModifiers.PRODUCTION, Region.FOREIGN_TERRITORY));
        default -> throw new AssertionError(kind);
      }
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void unknownRegimentEntriesCannotInsertANullMilitaryTemplate() {
    var guard = fixture.regiment("guard", false, 2, 0);
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("regiments", List.of("missing 4", "guard 2"));
    LawEffect effect = new LawEffect(Scope.FACTION, yaml);
    assertEquals(Map.of(guard, 2), effect.getRegiments());
  }

  @ParameterizedTest
  @ValueSource(strings = {"NaN-50", "0-Infinity", "30-10"})
  void invalidBracketsDoNotReplaceValidEconomicLimits(String malformed) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("brackets.vassal_tax", malformed);
    yaml.set("brackets.guild_tax", "5-20");
    LawEffect effect = new LawEffect(Scope.FACTION, yaml);
    assertFalse(effect.getBrackets().containsKey(Brackets.VASSAL_TAX));
    assertEquals(5.0, effect.getBrackets().get(Brackets.GUILD_TAX).getMin());
    assertEquals(20.0, effect.getBrackets().get(Brackets.GUILD_TAX).getMax());
  }

  @Test
  void anInvalidBooleanCannotSilentlyBecomeAnExplicitLawVeto() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("rules", List.of("guild_tax typo", "citizen_tax false"));
    LawEffect effect = new LawEffect(Scope.FACTION, yaml);
    assertFalse(effect.getRules().containsKey(Rules.GUILD_TAX));
    assertEquals(Boolean.FALSE, effect.getRules().get(Rules.CITIZEN_TAX));
  }

  @ParameterizedTest
  @CsvSource({
    "tax,NaN",
    "tax,Infinity",
    "tax,-Infinity",
    "fee,NaN",
    "fee,Infinity",
    "fee,-Infinity"
  })
  void malformedSavedRatesCannotReturnAsLiveFinancialProposals(String kind, String rate) {
    var faction = fixture.saved("home", "Leader");
    ProposalHandler handler = new ProposalHandler(faction.getGovernment());
    String target = kind.equals("tax") ? "CITIZENS:null" : "REGISTRATION_FEE:*";
    handler.restoreProposals(
        faction,
        List.of("Leader:" + kind + ":" + target + ":" + rate, "Leader:tax:CITIZENS:null:12.5"));
    assertEquals(1, handler.getProposals().size());
    assertEquals(12.5, handler.getProposals().getFirst().getTaxChange().getNewTax());
  }

  @Test
  void availabilityFollowsRequiredAndForbiddenCurrentLawsWithoutChangingEitherGroup() {
    fixture.lawGroup("government", Map.of());
    fixture.lawGroup("economy", Map.of());
    var monarchy = fixture.law("government", "monarchy", Map.of());
    var community = fixture.law("government", "community", Map.of());
    var candidate =
        fixture.law(
            "economy",
            "regulated",
            Map.of("requirements", List.of("  ", "  HAS_LAW MONARCHY  ", "not_law community")));
    var home = fixture.saved("home", "Leader");
    var government = home.getLawHandler().getGroup("government");
    var economy = home.getLawHandler().getGroup("economy");
    Law original = economy.getCurrent();
    assertEquals("§cRequires law: MONARCHY.", CanHaveLaw.blockReason(home, candidate));
    government.setCurrent(monarchy);
    assertTrue(candidate.isAvailable(home));
    assertSame(original, economy.getCurrent());
    var forbidden =
        fixture.law("economy", "forbidden", Map.of("requirements", List.of("not_law MONARCHY")));
    assertEquals("§cCannot have law: MONARCHY.", CanHaveLaw.blockReason(home, forbidden));
    government.setCurrent(community);
    assertEquals("§cRequires law: MONARCHY.", CanHaveLaw.blockReason(home, candidate));
    assertEquals("§cRequires law: MONARCHY.", CanHaveLaw.blockReason(null, candidate));
    assertFalse(CanHaveLaw.canHave(home, null));
    assertEquals("§cThat law does not exist.", CanHaveLaw.blockReason(home, null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"has_law", "not_law", "unrecognized monarchy"})
  void malformedRequirementsDenyTheCandidateInsteadOfIgnoringTheRule(String requirement) {
    fixture.lawGroup("economy", Map.of());
    var candidate =
        fixture.law("economy", "regulated", Map.of("requirements", List.of(requirement)));
    var home = fixture.saved("home", "Leader");
    assertEquals("§cInvalid law requirement.", CanHaveLaw.blockReason(home, candidate));
    assertEquals("current", home.getLawHandler().getGroup("economy").getCurrent().getId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"economy", "military"})
  void communityGovernmentMayRestoreDecentralizationButCannotReplaceIt(String category) {
    fixture.lawGroup("government", Map.of());
    fixture.lawGroup(category, Map.of());
    var community = fixture.law("government", "community", Map.of());
    var decentralized = fixture.law(category, "decentralized", Map.of());
    var centralized = fixture.law(category, "centralized", Map.of());
    var home = fixture.saved("home", "Leader");
    var government = home.getLawHandler().getGroup("government");
    var subject = home.getLawHandler().getGroup(category);
    government.setCurrent(community);
    assertTrue(CanHaveLaw.blockReason(home, centralized).contains("Already off Decentralized"));
    assertNull(CanHaveLaw.blockReason(home, decentralized));
    subject.setCurrent(decentralized);
    assertTrue(CanHaveLaw.blockReason(home, centralized).contains("stay Decentralized"));
    assertNull(CanHaveLaw.blockReason(home, decentralized));
    government.setCurrent(government.getLaw("current"));
    assertNull(CanHaveLaw.blockReason(home, centralized));
    assertSame(decentralized, subject.getCurrent());
  }

  @Test
  void factionsWithoutAGovernmentGroupCanStillAdoptCompatibleEconomicLaws() {
    fixture.lawGroup("economy", Map.of());
    var home = fixture.saved("home", "Leader");
    var denied = fixture.law("economy", "denied", Map.of("compatibility", List.of("current 0")));
    var permitted =
        fixture.law("economy", "permitted", Map.of("compatibility", List.of("current 2")));
    assertEquals(
        "§cIncompatible with the current law in this group.", CanHaveLaw.blockReason(home, denied));
    assertNull(CanHaveLaw.blockReason(home, permitted));
    assertNull(CanHaveLaw.blockReason(home, fixture.law("unknown", "unregistered", Map.of())));
  }

  @Test
  void aCurrentLawRemainsValidWhileTheActualSwitchLockProtectsOtherCandidates() {
    fixture.lawGroup("economy", Map.of());
    var home = fixture.saved("home", "Leader");
    var group = home.getLawHandler().getGroup("economy");
    var current = group.getCurrent();
    var replacement = fixture.law("economy", "replacement", Map.of());
    double previous = Cache.lawSwitchLockDays;
    try {
      Cache.lawSwitchLockDays = 2;
      long now = System.currentTimeMillis();
      group.setChangedAt(now - 60_000);
      current.getRequirements().add("has_law nonexistent");
      assertNull(CanHaveLaw.blockReason(home, current));
      assertNull(CanHaveLaw.lockReason(home, current, now));
      assertTrue(CanHaveLaw.blockReason(home, replacement).contains("changed recently"));
      assertTrue(CanHaveLaw.lockReason(home, replacement, now).contains("1d 23h"));
      assertNull(CanHaveLaw.lockReason(home, replacement, now + 2 * 86_400_000L));
      assertNull(CanHaveLaw.lockReason(null, replacement, now));
      assertNull(CanHaveLaw.lockReason(home, null, now));
      assertEquals("1m", CanHaveLaw.formatRemaining(1));
      assertEquals("1h 1m", CanHaveLaw.formatRemaining(3_600_001));
      assertEquals("2d 3h", CanHaveLaw.formatRemaining(2 * 86_400_000L + 3 * 3_600_000L));
    } finally {
      Cache.lawSwitchLockDays = previous;
    }
  }

  @Test
  void unavailableInitialLawsHaveADeterministicFallbackAndCopiesPreserveGroupMetadata() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("name", "Economic Rules");
    yaml.set("description", List.of("Choose how the realm trades."));
    yaml.set("laws.first.requirements", List.of("has_law absent"));
    yaml.set("laws.second.requirements", List.of("has_law also_absent"));
    var template = new LawGroup("economy", yaml);
    var home = fixture.saved("home", "Leader");
    var group = new LawGroup(home, template);
    assertEquals("economy", group.getId());
    assertEquals("Economic Rules", group.getName());
    assertTrue(group.hasDescription());
    assertEquals(List.of("Choose how the realm trades."), group.getDescription());
    assertSame(template.getLaw("first"), group.getCurrent());
    group.setCurrent(fixture.law("elsewhere", "unregistered", Map.of()));
    assertSame(template.getLaw("first"), group.getCurrent());
    group.switchTo(template.getLaw("second"), 100);
    assertSame(template.getLaw("second"), group.getCurrent());
    assertEquals(100, group.getChangedAt());
    group.switchTo(template.getLaw("second"), 200);
    assertEquals(100, group.getChangedAt());
    group.switchTo(fixture.law("elsewhere", "another", Map.of()), 300);
    assertSame(template.getLaw("second"), group.getCurrent());
  }

  @Test
  void lawMetadataAndValidEffectsSurviveMalformedSiblingConfiguration() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("name", "Trading Charter");
    yaml.set("icon", "v.emerald");
    yaml.set("upkeep", 2.5);
    yaml.set("cost", 25);
    yaml.set("description", List.of("Trade freely."));
    yaml.set("compatibility", List.of("incomplete", "too many parts", "old bad", "current -1"));
    yaml.set("effects.unknown.rules", List.of("guild_tax true"));
    yaml.set("effects.faction.modifiers", List.of("production(15)"));
    Law law = new Law("economy", "charter", yaml);
    assertEquals("Trading Charter", law.getName());
    assertEquals("v.emerald", law.getIconString());
    assertEquals(2.5, law.getUpkeep());
    assertEquals(25.0, law.getCost());
    assertTrue(law.hasDescription());
    assertEquals(List.of("Trade freely."), law.getDescription());
    assertEquals(Map.of("current", -1), law.getCompatibility());
    assertEquals(1, law.getCompatibility("unmentioned"));
    assertTrue(law.hasEffects());
    assertEquals(1, law.getScopedEffects().size());
    assertTrue(law.affectsEconomy());
    try (var boundary = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class);
      ItemCreator creator = mock(ItemCreator.class);
      ItemStack icon = new ItemStack(Material.EMERALD);
      boundary.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator()).thenReturn(creator);
      when(creator.getItemFromPath("v.emerald")).thenReturn(icon);
      assertSame(icon, law.getIcon());
    }
    assertTrue(fixture.law("government", "community", Map.of()).affectsEconomy());
    assertFalse(
        fixture
            .law("military", "levies", Map.of("effects.faction.modifiers", List.of("prestige(3)")))
            .affectsEconomy());
  }

  @Test
  void effectConfigurationKeepsValidSiblingsAndCombinesRegionalAndGlobalAmounts() {
    var guard = fixture.regiment("guard", false, 1, 0);
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set(
        "rules", List.of("malformed", "unknown true", "guild_tax TRUE", "can_have_vassals false"));
    yaml.set("regiments", List.of("malformed", "guard bad", "guard 3"));
    yaml.set("brackets.guild_tax", "5-25");
    yaml.set("brackets.unknown", "0-5");
    yaml.set("brackets.vassal_tax", "garbage");
    yaml.set("brackets.tariffs", "bad-10");
    yaml.set("modifiers", List.of("bad-format", "production(10)", "production(2.5)"));
    yaml.set("foreign_territory", List.of("missing(7)", "production(4)", "trade_power(9)"));
    LawEffect effect = new LawEffect(Scope.FACTION, yaml);
    assertEquals(Map.of(guard, 3), effect.getRegiments());
    assertEquals(Map.of(Rules.GUILD_TAX, true, Rules.CAN_HAVE_VASSALS, false), effect.getRules());
    assertEquals(1, effect.getBrackets().size());
    assertTrue(effect.hasGlobalModifiers());
    assertTrue(effect.hasRegionModifiers());
    assertTrue(effect.hasRegiments());
    assertEquals(2, effect.getGlobalModifiers().size());
    assertEquals(2, effect.getRegionModifiers().get(Region.FOREIGN_TERRITORY).size());
    assertEquals(12.5, effect.getModifierAmount(FactionModifiers.PRODUCTION, null));
    assertEquals(
        16.5, effect.getModifierAmount(FactionModifiers.PRODUCTION, Region.FOREIGN_TERRITORY));
    assertNull(effect.getModifierAmount(FactionModifiers.PRESTIGE, null));
    assertTrue(effect.prohibitsVassals());
    assertTrue(effect.affectsEconomy());
    assertFalse(effect.affectsCouncilType());
  }

  @Test
  void regionalPrestigeAloneDoesNotRequestAnEconomicPreview() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("foreign_territory", List.of("prestige(5)"));
    LawEffect effect = new LawEffect(Scope.FACTION, yaml);
    assertEquals(
        5.0, effect.getModifierAmount(FactionModifiers.PRESTIGE, Region.FOREIGN_TERRITORY));
    assertFalse(effect.affectsEconomy());
  }

  @ParameterizedTest
  @CsvSource({
    "appointed_council,APPOINTED_COUNCIL",
    "wealth_based_council,WEALTH_BASED_COUNCIL",
    "elected_council,ELECTED_COUNCIL",
    "has_council,NO_COUNCIL"
  })
  void councilRulesDeclareTheirTypeAndConfiguredSize(String configured, Rules expected) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("rules", List.of(configured + " true"));
    yaml.set("council-size", 5);
    LawEffect effect = new LawEffect(Scope.FACTION, yaml);
    assertTrue(effect.affectsCouncilType());
    assertEquals(expected, effect.getCouncilType());
    assertTrue(effect.affectsCouncilSize());
    assertEquals(5, effect.getCouncilSize());
    assertFalse(effect.prohibitsVassals());
    assertFalse(effect.affectsEconomy());
    LawEffect empty = new LawEffect(Scope.FACTION, new YamlConfiguration());
    assertFalse(empty.affectsCouncilType());
    assertFalse(empty.affectsCouncilSize());
    assertEquals(Rules.NO_COUNCIL, empty.getCouncilType());
    assertFalse(empty.prohibitsVassals());
  }

  @ParameterizedTest
  @CsvSource({
    "brackets.citizen_tax,false",
    "brackets.dividend_tax,false",
    "brackets.vehicle_tax,false",
    "brackets.registration_fee,false",
    "brackets.transfer_fee,false",
    "brackets.guild_tax,true",
    "brackets.vassal_tax,true",
    "brackets.tariffs,true",
    "rules,true",
    "modifiers,true",
    "foreign_territory,true"
  })
  void onlyEffectsThatChangeGuildIncomeRequestEconomicPreview(String key, boolean expected) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set(
        key,
        key.startsWith("brackets")
            ? "0-20"
            : key.equals("rules") ? List.of("tariffs true") : List.of("production(5)"));
    assertEquals(expected, new LawEffect(Scope.FACTION, yaml).affectsEconomy());
  }

  @Test
  void proposalQueuesEnforcePerMemberAndPerTargetLimitsWhileMovementQueuesStayIndependent() {
    fixture.lawGroup("economy", Map.of());
    fixture.lawGroup("military", Map.of());
    var home = fixture.saved("home", "Leader");
    ProposalHandler handler = new ProposalHandler(home.getGovernment());
    Proposal first = tax(home, "Alice", TaxTarget.GUILD_ID, "merchants", 10);
    Proposal second = tax(home, "Alice", TaxTarget.CITIZENS, null, 5);
    handler.propose(first);
    assertTrue(handler.canPropose("Alice"));
    handler.propose(second);
    assertFalse(handler.canPropose("Alice"));
    assertTrue(handler.canPropose("Bob"));
    assertEquals(List.of(first, second), handler.getProposalsByProposer("Alice"));
    assertTrue(handler.getProposalsByProposer("Bob").isEmpty());
    assertFalse(handler.canBeProposed(tax(home, "Bob", TaxTarget.GUILD_ID, "MERCHANTS", 20)));
    assertFalse(handler.canBeProposed(tax(home, "Bob", TaxTarget.CITIZENS, null, 20)));
    assertTrue(handler.canBeProposed(tax(home, "Bob", TaxTarget.GUILD_ID, "others", 20)));
    Proposal law = new Proposal("Bob", home.getGovernment());
    law.setLawProposal(fixture.law("economy", "regulated", Map.of()));
    assertTrue(handler.canBeProposed(law));
    handler.propose(law);
    Proposal sameGroup = new Proposal("Charlie", home.getGovernment());
    sameGroup.setLawProposal(fixture.law("economy", "free", Map.of()));
    assertFalse(handler.canBeProposed(sameGroup));
    Proposal otherGroup = new Proposal("Charlie", home.getGovernment());
    otherGroup.setLawProposal(fixture.law("military", "levies", Map.of()));
    assertTrue(handler.canBeProposed(otherGroup));
    Proposal fee = new Proposal("Bob", home.getGovernment());
    fee.setFeeProposal(new FeeChange(FeeKind.REGISTRATION_FEE, "tf:boat", 2));
    handler.propose(fee);
    Proposal duplicate = new Proposal("Charlie", home.getGovernment());
    duplicate.setFeeProposal(new FeeChange(FeeKind.REGISTRATION_FEE, "TF:BOAT", 3));
    assertFalse(handler.canBeProposed(duplicate));
    duplicate.setFeeProposal(new FeeChange(FeeKind.TRANSFER_FEE, "tf:boat", 3));
    assertTrue(handler.canBeProposed(duplicate));
    assertSame(first, handler.pop());
    assertSame(second, handler.pop());
    assertTrue(handler.hasProposals());
    ProposalHandler movement = new ProposalHandler(home.getGovernment(), true);
    movement.propose(law);
    movement.propose(law);
    assertTrue(movement.canPropose("Bob"));
    assertTrue(movement.canBeProposed(sameGroup));
    handler.clearProposals();
    assertFalse(handler.hasProposals());
    assertNull(handler.pop());
  }

  @Test
  void savedProposalsRoundTripAllKindsAndSkipMalformedOrRemovedTargetsIndividually() {
    fixture.lawGroup("economy", Map.of());
    var law = fixture.law("economy", "regulated", Map.of());
    var home = fixture.saved("home", "Leader");
    ProposalHandler handler = new ProposalHandler(home.getGovernment());
    Proposal proposal = new Proposal("Leader", home.getGovernment());
    proposal.setLawProposal(law);
    handler.propose(proposal);
    handler.propose(tax(home, "Alice", TaxTarget.GUILD_ID, "merchants", 7.5));
    handler.propose(tax(home, "Alice", TaxTarget.CITIZENS, null, 4));
    Proposal fee = new Proposal("Bob", home.getGovernment());
    fee.setFeeProposal(new FeeChange(FeeKind.REGISTRATION_FEE, "tf:boat", 1.25));
    handler.propose(fee);
    Proposal general = new Proposal("Bob", home.getGovernment());
    general.setFeeProposal(new FeeChange(FeeKind.TRANSFER_FEE, null, 2));
    handler.propose(general);
    Proposal action = new Proposal("Leader", home.getGovernment());
    action.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));
    action.setTarget("Alice");
    handler.propose(action);
    Proposal untargeted = new Proposal("Leader", home.getGovernment());
    untargeted.setPoliticalActionProposal(new PoliticalAction(Action.SNAP_ELECTIONS));
    handler.propose(untargeted);
    List<String> encoded = handler.serializeProposals();
    assertEquals(
        List.of(
            "Leader:law:economy:regulated",
            "Alice:tax:GUILD_ID:merchants:7.5",
            "Alice:tax:CITIZENS:null:4.0",
            "Bob:fee:REGISTRATION_FEE:tf:boat:1.25",
            "Bob:fee:TRANSFER_FEE:*:2.0",
            "Leader:action:CHANGE_LEADER:Alice",
            "Leader:action:SNAP_ELECTIONS:"),
        encoded);
    List<String> stored =
        new ArrayList<>(
            Arrays.asList(
                null,
                "bad",
                ":tax:CITIZENS:null:99",
                "Leader:law:missing:removed",
                "Leader:law:economy:removed",
                "Leader:law:economy",
                "Leader:fee:missing",
                "Leader:fee:UNKNOWN:*:1",
                "Leader:fee:TRANSFER_FEE:*:bad",
                "Leader:action:REMOVED",
                "Leader:tax:UNKNOWN:null:1",
                "Leader:tax:CITIZENS:null:bad",
                "Leader:tax:CITIZENS",
                "Leader:unknown:data"));
    stored.addAll(encoded);
    Map<Action, PoliticalAction> previous = new LinkedHashMap<>(PoliticalActionLoader.map);
    try {
      PoliticalActionLoader.map.clear();
      PoliticalAction configured = new PoliticalAction(Action.CHANGE_LEADER);
      PoliticalActionLoader.map.put(Action.CHANGE_LEADER, configured);
      handler.restoreProposals(home, stored);
      assertEquals(encoded, handler.serializeProposals());
      assertSame(law, handler.getProposals().get(0).getLaw());
      assertNull(handler.getProposals().get(2).getTaxChange().getId());
      assertEquals("tf:boat", handler.getProposals().get(3).getFeeChange().getVehicleTypeId());
      assertSame(configured, handler.getProposals().get(5).getPoliticalAction());
      assertEquals("Alice", handler.getProposals().get(5).getTarget());
      assertFalse(handler.getProposals().get(6).hasTarget());
    } finally {
      PoliticalActionLoader.map.clear();
      PoliticalActionLoader.map.putAll(previous);
    }
  }

  @ParameterizedTest
  @EnumSource(FeeKind.class)
  void feeKindsMapToTheirRulesAndCalculateOnlyPositiveCharges(FeeKind kind) {
    assertSame(kind, FeeKind.fromRule(kind.getRule()));
    assertSame(kind, FeeKind.fromBracket(kind.getBracket()));
    assertEquals(kind.isPercent() ? 1.25 : 125.0, kind.amount(2.5, 50));
    assertEquals(0.0, kind.amount(0, 50));
    assertEquals(0.0, kind.amount(2.5, 0));
    assertEquals(kind.isPercent() ? "2.5%" : "2.5x upkeep", kind.formatRate(2.5));
    assertFalse(kind.getDisplayName().isBlank());
    assertNull(FeeKind.fromRule(Rules.CAN_HAVE_VASSALS));
    assertNull(FeeKind.fromBracket(Brackets.GUILD_TAX));
  }

  private Proposal tax(Faction faction, String proposer, TaxTarget target, String id, double rate) {
    Proposal proposal = new Proposal(proposer, faction.getGovernment());
    proposal.setTaxProposal(new TaxLawChange(target, id, rate));
    return proposal;
  }
}
