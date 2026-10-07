package net.tfminecraft.simplefactions.government.stability;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.government.VotingBlock;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.MovementCrackdownQueries;
import net.tfminecraft.simplefactions.government.movement.MovementIds;
import net.tfminecraft.simplefactions.government.movement.MovementJoinCopy;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.movement.Pool;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.loaders.BranchLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PoliticalStateBoundaryCoverageTest {
  @Test
  void bankruptcyOverridesOtherwiseStableFactsAndMissingFactsReportFailure() {
    StabilityReport missing = StabilityMath.assess(null);
    assertEquals(StabilityStatus.FAILED, missing.status);
    assertEquals(List.of("Failed State"), missing.lines);
    StabilityFacts facts = new StabilityFacts();
    assertEquals(100, StabilityMath.assess(facts).stability);
    facts.bankrupt = true;
    StabilityReport bankrupt = StabilityMath.assess(facts);
    assertEquals(0, bankrupt.stability);
    assertEquals(StabilityStatus.FAILED, bankrupt.status);
    assertFalse(bankrupt.status.canFormTitles());
    assertFalse(bankrupt.status.canWageWar());
  }

  @Test
  void electedGovernmentChangesWeakStateRequirementsRegardlessOfServerLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      StabilityFacts facts = new StabilityFacts();
      facts.government = "OLIGARCHY";
      facts.electedLeadership = true;
      assertEquals(.25, StabilityMath.sizeRequirement(facts, StabilityTuning.DEFAULTS));
      assertEquals(.5, StabilityMath.governmentExpectation("OLIGARCHY", StabilityTuning.DEFAULTS));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void guildIncompatibilityUsesConfiguredGovernmentAndElectedLeadershipInAnyLocale() {
    Locale previous = Locale.getDefault();
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      fixture.lawGroup("government", Map.of());
      fixture.lawGroup("leadership", Map.of());
      Faction faction = fixture.saved("realm", "Alice");
      Guild state = faction.getOrCreateMainGuild();
      Guild merchants = fixture.guild(faction, "merchants", "Bob");
      state.getBranches().put(0, new Branch(BranchLoader.getByString("commerce"), 1));
      merchants.getBranches().put(0, new Branch(BranchLoader.getByString("commerce"), 8));
      var government = fixture.law("government", "OLIGARCHY", Map.of());
      var elected = fixture.law("leadership", "ELECTED", Map.of());
      faction.getLawHandler().getGroup("government").setCurrent(government);
      faction.getLawHandler().getGroup("leadership").setCurrent(elected);
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertEquals(.5, GovernmentIncompatibility.factor(merchants));
      assertTrue(StateStability.facts(faction).electedLeadership);
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void liveStateIncludesSubjectPopulationAndTheirStanceWithoutChangingTheRoster() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Faction realm = fixture.saved("realm", "Alice");
      Faction subject = fixture.saved("subject", "Bob");
      subject.addMember("Cara");
      subject.getOrCreateMainGuild().setStance(net.tfminecraft.simplefactions.enums.Stance.OPPOSE);
      fixture.subject(realm, subject);
      StabilityFacts facts = StateStability.facts(realm);
      assertEquals(1, facts.vassals.size());
      assertEquals("subject", facts.vassals.getFirst().name);
      assertEquals(2, facts.vassals.getFirst().members);
      assertEquals("OPPOSE", facts.vassals.getFirst().stance);
      assertEquals(List.of("Bob", "Cara"), subject.getMembers());
      assertTrue(StateStability.facts(null).guilds.isEmpty());
    }
  }

  @Test
  void stabilityBandsExposeTheDocumentedAdministrativeAndDisplayEffects() {
    assertEquals(1, StabilityStatus.STABLE.getAdminFactor());
    assertEquals(.75, StabilityStatus.STRUGGLING.getAdminFactor());
    assertEquals(1.5, StabilityStatus.STRUGGLING.getUpkeepFactor());
    assertEquals(30, StabilityStatus.FAILING.getDeJureBonus());
    assertEquals("#d13530", StabilityStatus.FAILING.getListColor());
    assertEquals("#a32020", StabilityStatus.COLLAPSING.getListColor());
    assertEquals("#8f1d1d", StabilityStatus.FAILED.getListColor());
    assertTrue(new StabilityModifier("Expired", 0, 1).tick());
  }

  @Test
  void absentTuningRestoresDefaultsAfterAnExplicitConfiguration() throws Exception {
    // Preserve process-global configuration, including a non-default value installed by another
    // test.
    var active = StabilityTuning.class.getDeclaredField("active");
    active.setAccessible(true);
    Object previous = active.get(null);
    try {
      YamlConfiguration config = new YamlConfiguration();
      config.set("stability.weak-state-oligarchy", .75);
      StabilityTuning.load(config);
      assertEquals(.75, StabilityTuning.get().weakStateOligarchy);
      assertNotSame(StabilityTuning.DEFAULTS, StabilityTuning.get());
      StabilityTuning.load(null);
      assertSame(StabilityTuning.DEFAULTS, StabilityTuning.get());
      assertEquals(.5, StabilityTuning.get().weakStateOligarchy);
    } finally {
      active.set(null, previous);
    }
  }

  @Test
  void removingPoolEntriesByPersistentIdKeepsUnrelatedSupporters() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Faction realm = fixture.saved("realm", "Alice");
      Faction first = fixture.saved("first", "Bob");
      Faction second = fixture.saved("second", "Cara");
      Guild one = fixture.guild(realm, "one", "Drew");
      Guild two = fixture.guild(realm, "two", "Eve");
      Pool pool = new Pool();
      pool.addGuild(one);
      pool.addGuild(two);
      pool.addFaction(first);
      pool.addFaction(second);
      pool.remove("guilds", "one");
      pool.remove("factions", "first");
      pool.remove("guilds", "unknown");
      assertEquals(List.of(two), pool.getGuilds());
      assertEquals(List.of(second), pool.getFactions());
    }
  }

  @Test
  void movementIdentifiersAvoidMultipleExistingCollisions() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Faction faction = fixture.saved("realm", "Alice");
      faction.addMember("Bob");
      Proposal proposal = new Proposal("Bob", faction.getGovernment());
      proposal.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));
      Movement first = new Movement(faction, "Bob", proposal);
      faction.getGovernment().getMovements().add(first);
      Movement second = new Movement(faction, "Bob", proposal);
      faction.getGovernment().getMovements().add(second);
      assertEquals("bob_movement", first.getId());
      assertEquals("bob_movement_2", second.getId());
      assertEquals("bob_movement_3", MovementIds.allocate("Bob"));
      assertEquals("unknown_movement", MovementIds.slug(null));
      assertEquals("unknown_movement", MovementIds.slug(" "));
    }
  }

  @Test
  void missingAssemblyLawIdentityUsesTheDefaultCrackdownRules() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      fixture.lawGroup("assembly", Map.of());
      Faction faction = fixture.saved("realm", "Alice");
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("law.name", "Unnamed imported law");
      var law =
          new net.tfminecraft.simplefactions.laws.Law(
              "assembly", null, yaml.getConfigurationSection("law"));
      faction.getLawHandler().getGroup("assembly").getLaws().put("missing-id", law);
      faction.getLawHandler().getGroup("assembly").setCurrent(law);
      assertEquals(
          MovementCrackdownQueries.LAW_NONE, MovementCrackdownQueries.assemblyLawId(faction));
    }
  }

  @Test
  void votingAndMembershipMessagesHandleIncompleteExternalIdentifiers() {
    assertFalse(VotingBlock.matches("iaf(tfmc:voting)", null));
    assertFalse(VotingBlock.matches("iaf(tfmc:voting)", " "));
    assertNull(VotingBlock.furnitureId("tfmc:voting"));
    assertNull(VotingBlock.furnitureId("iaf("));
    assertEquals("?", MovementJoinCopy.factionId(null));
    assertEquals("?", MovementJoinCopy.guildId(null));
    assertTrue(MovementJoinCopy.backerSameRealm(false, null, null).contains("same realm"));
    assertTrue(MovementJoinCopy.backerOwnFaction(true, null).contains("own movement"));
  }
}
