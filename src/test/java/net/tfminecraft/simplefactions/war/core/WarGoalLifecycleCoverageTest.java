package net.tfminecraft.simplefactions.war.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.enums.Goal;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.war.campaign.ObjectiveProvincePicker;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.declare.DeJureAnnexEligibility;
import net.tfminecraft.simplefactions.war.declare.PillageEligibility;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import net.tfminecraft.simplefactions.war.declare.WarGoalValidator;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WarGoalLifecycleCoverageTest {
  @Test
  void annexEligibilityRejectsAnUnidentifiedTitleConstructedThroughThePublicApi() throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.remember(Cache.class, "provinceCost");
      Cache.provinceCost = 0;
      rig.attacker.addProvince(11);
      rig.defender.addProvince(22);
      rig.attacker.addTitle(rig.domain.title("identified_county", "county", 90));
      JsonObject definition = new JsonObject();
      definition.addProperty("name", "Unidentified County");
      JsonArray provinces = new JsonArray();
      provinces.add(11);
      provinces.add(22);
      definition.add("provinces", provinces);
      Title unidentified = new Title(TierLoader.getByString("county"), null, definition);
      var result = DeJureAnnexEligibility.evaluate(rig.attacker, rig.defender, unidentified);
      assertFalse(
          result.eligible(), "A title without an ID cannot become a selectable war objective");
      assertEquals("§cThat title does not exist.", result.blockReason());
      assertTrue(rig.attacker.ownsProvince(11));
      assertTrue(rig.defender.ownsProvince(22));
    }
  }

  @Test
  void settlementIdsRetainTheirExistingExactCaseContractAcrossRealmSelection() throws Exception {
    try (Fixture rig = new Fixture()) {
      Faction subject = rig.domain.saved("case_subject", "CaseLeader");
      rig.domain.subject(rig.defender, subject);
      rig.defender.addProvince(22);
      subject.addProvince(33);
      var capitalized = rig.defender.getSettlementHandler().found("Town", 22, 0, 0).getSettlement();
      var lowercase = subject.getSettlementHandler().found("town", 33, 0, 0).getSettlement();
      assertSame(capitalized, PillageEligibility.findSettlement("Town", rig.defender));
      assertSame(lowercase, PillageEligibility.findSettlement("town", rig.defender));
      assertEquals(2, PillageEligibility.options(rig.attacker, rig.defender).size());
      assertTrue(
          PillageEligibility.options(rig.attacker, rig.defender).stream()
              .noneMatch(
                  option ->
                      option.blockReason() != null
                          && option.blockReason().contains("More than one")));
    }
  }

  @Test
  void unknownLegacyGoalCannotSilentlyBecomeAnAnnexation() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new WarGoal("retired_objective", new YamlConfiguration()));
    assertTrue(failure.getMessage().contains("retired_objective"));
  }

  @Test
  void pillageOptionsUseRealmOwnershipAndExplainMissingCoastAndLandConnections() throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.remember(Cache.class, "pillageRangeProvinces");
      Cache.pillageRangeProvinces = 3;
      rig.attacker.addProvince(11);
      rig.defender.addProvince(22);
      var own = rig.attacker.getSettlementHandler().found("Home", 11, 0, 0).getSettlement();
      var enemy = rig.defender.getSettlementHandler().found("Enemy", 22, 0, 0).getSettlement();
      var first = new Province(11, "plains", 0);
      var second = new Province(22, "plains", 0);
      rig.domain.provinceData.put(11, first);
      rig.domain.provinceData.put(22, second);
      FactionManager.factions.add(null);
      assertTrue(PillageEligibility.options(null, rig.defender).isEmpty());
      assertTrue(PillageEligibility.options(rig.attacker, null).isEmpty());
      assertNull(PillageEligibility.findSettlement(null));
      assertNull(PillageEligibility.findSettlement(" "));
      assertNull(PillageEligibility.findSettlement("missing"));
      assertNull(PillageEligibility.findSettlement(enemy.getId(), null));
      assertSame(enemy, PillageEligibility.findSettlement(enemy.getId()));
      assertSame(enemy, PillageEligibility.findSettlement(enemy.getId(), rig.defender));
      assertNull(PillageEligibility.landOwner(999));
      assertFalse(PillageEligibility.evaluate(rig.attacker, rig.defender, null).eligible());
      assertTrue(
          PillageEligibility.evaluate(rig.attacker, rig.defender, own)
              .blockReason()
              .contains("your own"));
      assertTrue(
          PillageEligibility.evaluate(rig.attacker, null, enemy)
              .blockReason()
              .contains("not in their realm"));
      var options = PillageEligibility.options(rig.attacker, rig.defender);
      assertEquals(1, options.size());
      assertSame(enemy, options.getFirst().settlement());
      assertTrue(options.getFirst().blockReason().contains("out of pillage range"));
      second.addNeighbour(30);
      rig.domain.provinceData.put(30, new Province(30, "sea", 0));
      assertTrue(
          PillageEligibility.evaluate(rig.attacker, rig.defender, enemy)
              .blockReason()
              .contains("No sea connection"));
      FactionManager.factions.remove(rig.defender);
      assertTrue(
          PillageEligibility.evaluate(rig.attacker, rig.defender, enemy)
              .blockReason()
              .contains("No sea connection"));
      assertSame(enemy, rig.defender.getSettlementHandler().getById(enemy.getId()));
      FactionManager.factions.add(1, rig.defender);
      first.addNeighbour(30);
      assertTrue(PillageEligibility.evaluate(rig.attacker, rig.defender, enemy).eligible());
      when(rig.domain.ui.plugin.getProvinceManager()).thenReturn(null);
      assertTrue(
          PillageEligibility.evaluate(rig.attacker, rig.defender, enemy)
              .blockReason()
              .contains("out of pillage range"));
      FactionManager.factions.remove(null);
    }
  }

  @Test
  void deJureEligibilityUsesCurrentLandAcrossTheDefendersRealmAndCurrentSettlementProtection()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.remember(Cache.class, "provinceCost");
      Cache.provinceCost = 0;
      WarManager.get().clear();
      Faction subject = rig.domain.saved("annex_subject", "Subject");
      rig.domain.subject(rig.defender, subject);
      rig.attacker.addProvince(11);
      rig.attacker.addProvince(99);
      rig.defender.addProvince(22);
      subject.addProvince(33);
      rig.attacker.getSettlementHandler().found("Outside", 99, 0, 0);
      rig.attacker.addTitle(rig.domain.title("separate_county", "county", 90));
      var target = rig.domain.title("unowned_county", "county", 11, 22, 33, 44);
      var empty = rig.domain.title("empty_county", "county");
      target.getProvinces().add(null);
      assertEquals(
          List.of(22, 33),
          DeJureAnnexEligibility.incomingProvinces(rig.attacker, rig.defender, target));
      assertTrue(
          DeJureAnnexEligibility.incomingProvinces(rig.attacker, rig.defender, empty).isEmpty());
      assertTrue(DeJureAnnexEligibility.incomingProvinces(null, rig.defender, target).isEmpty());
      assertTrue(DeJureAnnexEligibility.options(null, rig.defender).isEmpty());
      assertFalse(DeJureAnnexEligibility.evaluate(rig.attacker, rig.defender, null).eligible());
      assertTrue(
          DeJureAnnexEligibility.evaluate(rig.attacker, rig.defender, empty)
              .blockReason()
              .contains("no land"));
      var option = DeJureAnnexEligibility.evaluate(rig.attacker, rig.defender, target);
      assertTrue(option.eligible(), option.blockReason());
      TitleLoader.getTitles()
          .add(null); // The exposed configuration registry is mutable across plugin integrations.
      assertEquals(
          List.of(target),
          DeJureAnnexEligibility.options(rig.attacker, rig.defender).stream()
              .map(DeJureAnnexEligibility.DeJureTitleOption::title)
              .toList());
      TitleLoader.getTitles().remove(null);
      rig.attacker.addTitle(target);
      rig.defender.getSettlementHandler().found("Protected", 22, 0, 0);
      RelationLoader.types.removeIf(type -> type.isVassalage());
      rig.domain.relationType("exhausted_vassal", Map.of("vassal", true, "limit", 0));
      FactionManager.factions.add(null);
      assertEquals(
          "§cThis title has settlements.",
          DeJureAnnexEligibility.evaluate(rig.attacker, rig.defender, target).blockReason());
      FactionManager.factions.remove(null);
      target.getProvinces().remove(null);
      assertTrue(rig.attacker.ownsProvince(11));
      assertTrue(rig.defender.ownsProvince(22));
      assertTrue(subject.ownsProvince(33));
    }
  }

  @Test
  void legacyGoalConfigurationRetainsDescriptionCostAndStackingRules() throws Exception {
    try (Fixture rig = new Fixture()) {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("name", "Independence");
      yaml.set("cost", 7);
      yaml.set("desc", List.of("Leave your overlord", "Win the war"));
      yaml.set("target", List.of("OVERLORD"));
      WarGoal single = new WarGoal("independence", yaml);
      rig.domain.subject(rig.defender, rig.attacker);
      Participant declaring =
          new Participant(rig.attacker, List.of(), Map.of(), Map.of(rig.defender, single), false);
      rig.war.getAttackers().getMainParticipants().clear();
      rig.war.getAttackers().getMainParticipants().add(declaring);
      assertEquals("Independence", single.getName());
      assertEquals(7, single.getCost());
      assertEquals(List.of("Leave your overlord", "Win the war"), single.getDescription());
      assertEquals(List.of("OVERLORD"), single.getTargeters());
      assertFalse(single.canTarget(rig.war, rig.attacker, rig.defender));
      yaml.set("stackable", true);
      WarGoal stackable = new WarGoal("independence", yaml);
      rig.war
          .getAttackers()
          .getMainParticipants()
          .set(
              0,
              new Participant(
                  rig.attacker, List.of(), Map.of(), Map.of(rig.defender, stackable), false));
      assertTrue(stackable.canTarget(rig.war, rig.attacker, rig.defender));
      assertSame(stackable, rig.war.getWarGoalsOn(rig.defender).get(rig.attacker));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"overlord", "overlords", "independent", "lower_tier", "higher_tier"})
  void legacyTargetRulesFollowActualRelationsAndRanks(String rule) throws Exception {
    try (Fixture rig = new Fixture()) {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("target", List.of(rule));
      WarGoal goal = new WarGoal("annex", yaml);
      Faction outsider = rig.domain.saved("legacy_outsider", "Outsider");
      switch (rule) {
        case "overlord" -> {
          assertFalse(goal.canTarget(rig.war, rig.attacker, rig.defender));
          rig.domain.subject(outsider, rig.attacker);
          assertFalse(goal.canTarget(rig.war, rig.attacker, rig.defender));
          rig.attacker.getRelations().clear();
          rig.domain.subject(rig.defender, rig.attacker);
          assertTrue(goal.canTarget(rig.war, rig.attacker, rig.defender));
        }
        case "overlords" -> {
          assertFalse(goal.canTarget(rig.war, rig.attacker, rig.defender));
          rig.domain.subject(rig.defender, outsider);
          assertTrue(goal.canTarget(rig.war, rig.attacker, rig.defender));
        }
        case "independent" -> {
          assertTrue(goal.canTarget(rig.war, rig.attacker, rig.defender));
          rig.domain.subject(outsider, rig.defender);
          assertFalse(goal.canTarget(rig.war, rig.attacker, rig.defender));
        }
        case "lower_tier" -> {
          rig.defender.addTitle(rig.domain.title("legacy_county", "county", 90));
          assertFalse(goal.canTarget(rig.war, rig.attacker, rig.defender));
          rig.attacker.addTitle(rig.domain.title("legacy_duchy", "duchy", 91));
          assertTrue(goal.canTarget(rig.war, rig.attacker, rig.defender));
        }
        case "higher_tier" -> {
          rig.attacker.addTitle(rig.domain.title("legacy_county", "county", 90));
          assertFalse(goal.canTarget(rig.war, rig.attacker, rig.defender));
          rig.defender.addTitle(rig.domain.title("legacy_duchy", "duchy", 91));
          assertTrue(goal.canTarget(rig.war, rig.attacker, rig.defender));
        }
        default -> fail("Unexpected rule");
      }
    }
  }

  @Test
  void diplomacyAndGovernmentStateRejectOtherwiseValidDeclarations() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("declaration_attacker", "LeaderA");
      Faction defender = domain.saved("declaration_defender", "LeaderB");
      var validator = new WarGoalValidator();
      attacker.getGovernment().addStabilityModifier(new StabilityModifier("Unrest", -1000, 1));
      assertTrue(
          validator
              .validate(WarDeclareRequest.of(attacker, defender, WarGoalType.WAR))
              .getMessage()
              .contains("cannot declare war"));
      attacker.getGovernment().getByName("Unrest").increaseModifier(1000);
      var ally = domain.relationType("ally", Map.of("name", "Ally"));
      Faction third = domain.saved("other_ally", "Other");
      attacker.setRelation(third, new Relation(ally, RelationLoader.getDefaultAttitude()));
      assertTrue(
          new WarGoalValidator()
              .validate(WarDeclareRequest.of(attacker, defender, WarGoalType.WAR))
              .isValid());
      attacker.setRelation(defender, new Relation(ally, RelationLoader.getDefaultAttitude()));
      assertEquals(
          "§cYou cannot declare war on an ally.",
          validator
              .validate(WarDeclareRequest.of(attacker, defender, WarGoalType.WAR))
              .getMessage());
      var tributary = domain.relationType("tributary", Map.of("name", "Tributary"));
      attacker.setRelation(defender, new Relation(tributary, RelationLoader.getDefaultAttitude()));
      assertEquals(
          "§cYou cannot declare war on your tributary.",
          validator
              .validate(WarDeclareRequest.of(attacker, defender, WarGoalType.OPEN_MARKET))
              .getMessage());
      assertTrue(
          validator.validate(WarDeclareRequest.of(attacker, defender, WarGoalType.WAR)).isValid());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"USURP", "OVERTHROW", "CHANGE_LAW", "CHANGE_TAX", "FORCE_PEACE"})
  void siblingSubjectsCannotUseGoalsReservedForTheirLiegeOrMovement(String name) {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction root = domain.saved("shared_liege", "Liege");
      Faction first = domain.saved("first_subject", "One");
      Faction second = domain.saved("second_subject", "Two");
      domain.subject(root, first);
      domain.subject(root, second);
      var result =
          new WarGoalValidator()
              .validate(WarDeclareRequest.of(first, second, WarGoalType.valueOf(name)));
      assertFalse(result.isValid());
      assertEquals(
          name.equals("USURP")
              ? "§cUsurp can only target your direct overlord."
              : "§cThis war goal cannot be declared yet.",
          result.getMessage());
    }
  }

  @Test
  void incompletePillageAndTributaryRequestsAreRejectedWithoutMutation() throws Exception {
    try (Fixture rig = new Fixture()) {
      WarManager.get().clear();
      var validator = new WarGoalValidator();
      assertEquals(
          "§cSpecify a settlement to pillage.",
          validator
              .validate(WarDeclareRequest.of(rig.attacker, rig.defender, WarGoalType.PILLAGE))
              .getMessage());
      assertEquals(
          "§cThat settlement does not exist or its id is ambiguous in their realm.",
          validator
              .validate(
                  new WarDeclareRequest(
                      rig.attacker,
                      rig.defender,
                      WarGoalType.PILLAGE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "missing"))
              .getMessage());
      assertEquals(
          "§cTributary diplomacy is not configured.",
          validator
              .validate(WarDeclareRequest.of(rig.attacker, rig.defender, WarGoalType.TRIBUTARY))
              .getMessage());
      assertEquals(
          "§cSpecify a de jure title to annex.",
          validator
              .validate(WarDeclareRequest.of(rig.attacker, rig.defender, WarGoalType.DE_JURE_ANNEX))
              .getMessage());
      assertEquals(
          "§cThat title does not exist.",
          validator
              .validate(
                  new WarDeclareRequest(
                      rig.attacker, rig.defender, WarGoalType.DE_JURE_ANNEX, "removed_title", null))
              .getMessage());
      assertTrue(rig.attacker.getRelations().isEmpty());
      assertTrue(rig.defender.getRelations().isEmpty());
      assertTrue(WarManager.get().isEmpty());
    }
  }

  @Test
  void subjugationRejectsMissingUnpickableAndExhaustedSubjectTypes() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      domain.lawGroup(
          "authority", Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS true")));
      Faction attacker = domain.saved("prospective_liege", "One");
      Faction defender = domain.saved("prospective_subject", "Two");
      var validator = new WarGoalValidator();
      assertEquals(
          "§cSpecify a subject type.",
          validator
              .validate(WarDeclareRequest.of(attacker, defender, WarGoalType.SUBJUGATE))
              .getMessage());
      assertEquals(
          "§cThat subject type cannot be chosen for war.",
          validator
              .validate(
                  new WarDeclareRequest(
                      attacker, defender, WarGoalType.SUBJUGATE, null, null, "neutral"))
              .getMessage());
      var limited =
          domain.relationType(
              "limited_vassal", Map.of("vassal", true, "link", "overlord", "limit", 0));
      assertEquals(
          "§cYou have reached the limit for this relation type.",
          validator
              .validate(
                  new WarDeclareRequest(
                      attacker, defender, WarGoalType.SUBJUGATE, null, null, limited.getId()))
              .getMessage());
      domain.subject(defender, domain.saved("transferred_subject", "Three"));
      Faction subject = FactionManager.getByString("transferred_subject");
      defender.setRelation(subject, new Relation(limited, RelationLoader.getDefaultAttitude()));
      assertEquals(
          "§cYou have reached the limit for this relation type.",
          validator
              .validate(
                  new WarDeclareRequest(
                      attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, subject.getId()))
              .getMessage());
      assertTrue(attacker.getRelations().isEmpty());
      assertEquals(
          "§cSpecify a valid subject faction.",
          validator
              .validate(WarDeclareRequest.of(attacker, defender, WarGoalType.TRANSFER_SUBJECT))
              .getMessage());
      assertEquals(
          "§cSpecify a valid subject faction.",
          validator
              .validate(
                  new WarDeclareRequest(
                      attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, "removed_subject"))
              .getMessage());
      assertEquals(
          "§cYou cannot transfer your own faction as a subject.",
          validator
              .validate(
                  new WarDeclareRequest(
                      attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, attacker.getId()))
              .getMessage());
      var unaffiliated = domain.saved("unaffiliated_subject", "Four");
      assertEquals(
          "§cThat faction is not a subject of the defender.",
          validator
              .validate(
                  new WarDeclareRequest(
                      attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, unaffiliated.getId()))
              .getMessage());
      domain.subject(attacker, unaffiliated);
      assertEquals(
          "§cThat faction is already your subject.",
          validator
              .validate(
                  new WarDeclareRequest(
                      attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, unaffiliated.getId()))
              .getMessage());
      FactionManager.factions.remove(defender);
      assertEquals(
          "§cInvalid war target.",
          validator
              .validate(
                  new WarDeclareRequest(
                      attacker, defender, WarGoalType.SUBJUGATE, null, null, "vassal"))
              .getMessage());
    }
  }

  @Test
  void pillageDeclarationFindsTheDefendersTownWhenAnotherRealmUsesTheSameName() throws Exception {
    try (Fixture rig = new Fixture()) {
      WarManager.get().clear();
      rig.attacker.addProvince(11);
      rig.defender.addProvince(22);
      Province first = new Province(11, "plains", 0);
      Province second = new Province(22, "plains", 0);
      first.addNeighbour(22);
      second.addNeighbour(11);
      rig.domain.provinceData.put(11, first);
      rig.domain.provinceData.put(22, second);
      assertTrue(rig.attacker.getSettlementHandler().found("Town", 11, 0, 0).isSuccess());
      assertTrue(rig.defender.getSettlementHandler().found("Town", 22, 0, 0).isSuccess());
      var result =
          new WarGoalValidator()
              .validate(
                  new WarDeclareRequest(
                      rig.attacker,
                      rig.defender,
                      WarGoalType.PILLAGE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "Town"));
      assertTrue(result.isValid(), result.getMessage());
      rig.war.setGoal(WarGoalType.PILLAGE);
      rig.war.setTargetSettlementId("Town");
      assertEquals(
          22,
          new ObjectiveProvincePicker(rig.domain.provinces)
              .pickObjective(rig.war, rig.defender)
              .orElseThrow());
    }
  }

  @Test
  void legacyGoalNamesRemainCanonicalUnderTurkishLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      WarGoal goal = new WarGoal("independence", new YamlConfiguration());
      assertEquals(Goal.INDEPENDENCE, goal.getType());
      assertEquals("independence", goal.getId());
      assertEquals("independence", goal.getName());
      assertEquals(1, goal.getCost());
      assertTrue(goal.getDescription().isEmpty());
      assertTrue(goal.getTargeters().isEmpty());
    } finally {
      Locale.setDefault(previous);
    }
  }
}
