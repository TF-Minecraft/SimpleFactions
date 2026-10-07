package net.tfminecraft.simplefactions.diplomacy;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.army.LevyEntry;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarSnapshot;
import net.tfminecraft.simplefactions.war.commitment.LevySnapshotCalculator;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommitment;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.pathfinder.BelligerentTerritory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RealmRelationsBoundaryCoverageTest {
  private static final int WAR_ID = 551091;
  private FactionDomainFixture domain;
  private List<WarCommitment> previousCommitments;
  private Locale previousLocale;

  @BeforeEach
  void setUp() {
    previousLocale = Locale.getDefault();
    previousCommitments = new ArrayList<>(WarCommitmentService.getCommitmentsForWar(WAR_ID));
    WarCommitmentService.clearCommitments(WAR_ID);
    domain = new FactionDomainFixture();
  }

  @AfterEach
  void tearDown() {
    try {
      domain.close();
    } finally {
      WarCommitmentService.restoreCommitments(WAR_ID, previousCommitments);
      Locale.setDefault(previousLocale);
    }
  }

  @Test
  void lateSubjectLeviesUseTheSameCaseIndependentIdentityAsWarCommitments() {
    domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction holder = domain.saved("IRON", "IronLeader");
    Faction enemy = domain.saved("enemy", "EnemyLeader");
    War war = new War(WAR_ID, holder, enemy);
    WarManager.get().add(war);
    Faction source = domain.saved("IRIS", "IrisLeader");
    domain.subject(holder, source);
    Regiment guard = regiment(source, "guard", 3);
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));

    List<WarCommitment> rows = WarCommitmentService.snapshotLevyForFighter(war, holder);

    assertEquals(1, rows.size(), "A late subject must still contribute to its uppercase-ID holder");
    assertEquals(holder.getId(), rows.getFirst().factionId());
    assertEquals(source.getId(), rows.getFirst().sourceFactionId());
    assertEquals(1, rows.getFirst().count(), "The one actual member caps the contribution");
    assertEquals(3, guard.getCurrentSlots(), "Snapshotting must not consume the live army");
    assertTrue(WarCommitmentService.snapshotLevyForFighter(war, holder).isEmpty());
  }

  @Test
  void releasingAnUppercaseSubjectRemovesItsCommittedLevyUnderTurkishLocale() {
    domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction holder = domain.saved("holder", "HolderLeader");
    War war = new War(WAR_ID, holder, domain.saved("enemy", "EnemyLeader"));
    WarManager.get().add(war);
    Faction source = domain.saved("IRIS", "IrisLeader");
    domain.subject(holder, source);
    Regiment guard = regiment(source, "guard", 2);
    assertEquals(1, WarCommitmentService.snapshotLevyForFighter(war, holder).size());
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));

    WarCommitmentService.removeLevySubtree(source);

    assertTrue(WarCommitmentService.getCommitmentsForWar(WAR_ID).isEmpty());
    assertEquals(2, guard.getCurrentSlots(), "Releasing a levy cannot erase its source soldiers");
  }

  @Test
  void incompleteSavedParticipantsCannotPromoteAnAllyToMainDefender() {
    Faction attacker = domain.saved("attacker", "AttackerLeader");
    Faction defender = domain.saved("defender", "DefenderLeader");
    Faction ally = domain.saved("ally", "AllyLeader");
    War war = new War(WAR_ID, attacker, defender);
    war.getDefenders().addNewParticipant(ally, war.getDefenders().getMainParticipants().getFirst());
    WarData saved = WarMapper.toData(war);
    saved.defenders.participants.removeIf(row -> row.leader.equals(defender.getId()));
    Gson gson = new Gson();
    War restored = WarMapper.fromData(gson.fromJson(gson.toJson(saved), WarData.class));
    assertNotNull(restored);
    assertSame(defender, restored.getDefenders().getLeader());
    Map<Integer, String> owners =
        Map.of(10, attacker.getId(), 20, defender.getId(), 30, ally.getId());

    BelligerentTerritory territory = BelligerentTerritory.fromWar(restored, owners::get);

    assertTrue(territory.isDefenderSide(20), "The saved side leader remains a belligerent");
    assertTrue(territory.isMainDefenderRealm(20), "The war objective stays in the defender realm");
    assertTrue(territory.isDefenderSide(30), "A valid ally is still on the defending side");
    assertFalse(territory.isMainDefenderRealm(30), "An ally cannot inherit the missing main row");
  }

  @Test
  void reloadedCivilWarSuppressesRebelLawGrantsWithoutTakingTheLoyalArmyGrants() {
    domain.regiment("guard", false, 0, 0);
    domain.lawGroup("government", Map.of("effects.faction.regiments", List.of("guard 4")));
    Faction rebels = domain.saved("rebels", "RebelLeader");
    Faction host = domain.saved("host", "HostLeader");
    rebels.refreshLawSlots();
    host.refreshLawSlots();
    Regiment rebelGuard = rebels.getMilitary().getRegiment("guard");
    Regiment loyalGuard = host.getMilitary().getRegiment("guard");
    assertEquals(4, rebelGuard.getFreeSlots());
    assertEquals(4, loyalGuard.getFreeSlots());
    rebelGuard.setCurrentSlots(6);
    loyalGuard.setCurrentSlots(7);
    CivilWarSnapshot snapshot = new CivilWarSnapshot();
    snapshot.setTempRebelFactionId(rebels.getId());
    snapshot.setHostFactionId(host.getId());
    War war = new War(WAR_ID, rebels, host);
    war.setGoal(WarGoalType.CHANGE_LAW);
    war.setMovementId("rebel-law-movement");
    war.setCivilWarSnapshot(snapshot);
    Gson gson = new Gson();
    War restored =
        WarMapper.fromData(gson.fromJson(gson.toJson(WarMapper.toData(war)), WarData.class));
    assertNotNull(restored);
    WarManager.get().add(restored);

    rebels.refreshLawSlots();
    host.refreshLawSlots();

    assertEquals(0, rebelGuard.getFreeSlots(), "Rebels retain only their paid split-off troops");
    assertEquals(2, rebelGuard.getCurrentSlots());
    assertEquals(
        4, loyalGuard.getFreeSlots(), "The loyal faction still receives its lawful grants");
    assertEquals(7, loyalGuard.getCurrentSlots(), "The loyal faction keeps its paid troops too");
    assertEquals(3, loyalGuard.getPaidSlots());
  }

  @Test
  void tradeAndTreatyPartnersConsumeCapacityAndConvergeOnTheirCombinedOpinion() {
    Faction origin = domain.saved("origin", "OriginLeader");
    Faction trader = domain.saved("trader", "TraderLeader");
    Faction treatyPartner = domain.saved("treaty_partner", "TreatyLeader");
    Faction removed = domain.saved("removed", "RemovedLeader");
    RelationType trade =
        domain.relationType("trade", Map.of("trade-agreement", true, "cost", 6.0, "target", 4));
    RelationType treaty =
        domain.relationType("peace", Map.of("treaty", true, "cost", 3.0, "target", -2));
    DiplomacyHandler handler = origin.getDiplomacyHandler();
    handler.setTradeRelation(trader, trade);
    handler.setTreatyRelation(trader, treaty);
    handler.setTreatyRelation(treatyPartner, treaty);
    handler.setTradeRelation(removed, trade);
    handler.setTreatyRelation(removed, treaty);
    FactionManager.factions.remove(removed);
    double expectedCost =
        RelationManager.getDiplomaticCost(origin, trader, trade)
            + RelationManager.getDiplomaticCost(origin, trader, treaty)
            + RelationManager.getDiplomaticCost(origin, treatyPartner, treaty);

    handler.updateRelations();

    Relation tradingRelation = handler.getRelation(trader.getId());
    assertTrue(tradingRelation.isDefault());
    assertEquals(1, tradingRelation.getOpinion());
    assertEquals(-1, handler.getRelation(treatyPartner.getId()).getOpinion());
    assertEquals(expectedCost, handler.getUsedDiplomaticCapacity(), 0.00001);
    for (int tick = 0; tick < 5; tick++) handler.updateRelations();
    assertSame(tradingRelation, handler.getRelation(trader.getId()));
    assertEquals(2, tradingRelation.getOpinion());
    assertEquals(-2, handler.getRelation(treatyPartner.getId()).getOpinion());
    handler.removeTradeRelation(trader.getId());
    handler.updateRelations();
    assertEquals(
        1,
        tradingRelation.getOpinion(),
        "Ending trade lowers the target without resetting history");
  }

  @Test
  void relationModifiersAndThresholdsDriveTheConfiguredDecision() {
    RelationType pact =
        domain.relationType(
            "pact",
            Map.of(
                "name",
                "Pact",
                "prefix",
                "Our ",
                "give-modifiers",
                List.of("LEVY(25)"),
                "recieve-modifiers",
                List.of("TRADE_POWER(7)"),
                "threshold.mode",
                "higher_than_or_equal_to",
                "threshold.amount",
                10));
    Relation relation = new Relation(pact, RelationLoader.getDefaultAttitude(), 9);
    Faction origin = domain.saved("origin", "OriginLeader");
    Faction target = domain.saved("target", "TargetLeader");
    origin.setRelation(target, relation);
    assertFalse(pact.fulfilled(relation.getOpinion()));
    assertEquals("higher than or equal to", pact.getFormattedThresholdType());
    assertEquals(">=", pact.getThreshold().getFormattedShort());
    assertEquals("Our Pact", pact.getFull());
    assertEquals(
        "Our ", pact.getPrefix(), "Configured diplomatic labels retain their display prefix");
    assertEquals(25.0, relation.getGiveModifier(FactionModifiers.LEVY));
    assertEquals(0.0, relation.getGiveModifier(FactionModifiers.TRADE_POWER));
    assertEquals(7.0, relation.getRecieveModifier(FactionModifiers.TRADE_POWER));
    assertEquals(0.0, relation.getRecieveModifier(FactionModifiers.LEVY));
    RelationType cooling =
        domain.relationType(
            "cooling", Map.of("threshold.mode", "lower_than_or_equal_to", "threshold.amount", -10));
    assertFalse(cooling.fulfilled(-9));
    assertTrue(cooling.fulfilled(-10));
    assertTrue(cooling.fulfilled(-11));
    assertEquals("<=", cooling.getThreshold().getFormattedShort());
  }

  @Test
  void anUnknownThresholdModeCannotSilentlyAuthorizeEveryOpinion() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("mode", "greater_then");
    yaml.set("amount", 20);
    IllegalArgumentException failure =
        assertThrows(IllegalArgumentException.class, () -> new Threshold(yaml));
    assertTrue(failure.getMessage().contains("greater_then"));
  }

  @Test
  void diplomacyDirectoryKeepsPartnersSeparatedAndSkipsMissingRegistryEntries() {
    Faction origin = domain.saved("origin", "OriginLeader");
    Faction partner = domain.saved("partner", "PartnerLeader");
    Faction stranger = domain.saved("stranger", "StrangerLeader");
    RelationType trade = domain.relationType("trade", Map.of("trade-agreement", true));
    origin.getDiplomacyHandler().setTradeRelation(partner, trade);
    Faction broken = domain.saved("broken", "BrokenLeader");
    broken.setId(null);
    FactionManager.factions.add(1, null);
    List<DiplomacyQueries.DiplomacyListEntry> directory = DiplomacyQueries.ownDirectory(origin);
    assertEquals(20, directory.size());
    assertSame(partner, directory.getFirst().getFaction());
    assertEquals(DiplomacyQueries.DiplomacyListEntry.Kind.FACTION, directory.getFirst().getKind());
    assertTrue(
        directory.subList(1, 19).stream()
            .allMatch(
                entry ->
                    entry.getKind() == DiplomacyQueries.DiplomacyListEntry.Kind.SEPARATOR
                        && entry.getFaction() == null));
    assertSame(stranger, directory.getLast().getFaction());
    assertTrue(DiplomacyQueries.officialPartners(null).isEmpty());
    assertTrue(DiplomacyQueries.otherFactions(null).isEmpty());
    broken.setId("broken");
  }

  @Test
  void nestedLevyRatesCapContributionsAndTheDisplayedLedgerTracksAdjustments() {
    domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction holder = domain.saved("holder", "HolderLeader");
    Faction middle = domain.saved("middle", "MiddleLeader");
    Faction source = domain.saved("source", "SourceLeader");
    source.getOrCreateMainGuild().getMembers().add("SecondSoldier");
    domain.subject(holder, middle);
    domain.subject(middle, source);
    var half =
        domain.law(
            "conscription", "half", Map.of("effects.faction.modifiers", List.of("LEVY(50)")));
    middle.getLawHandler().getGroup("conscription").setCurrent(half);
    Regiment guard = regiment(source, "guard", 5);
    assertEquals(2, LevySnapshotCalculator.directLevyContribution(source));
    assertEquals(1, LevySnapshotCalculator.levyContribution(source, holder));
    assertSame(
        middle,
        LevySnapshotCalculator.findNearestFighterHolder(source, Set.of("middle", "holder")));
    List<LevyEntry> entries = holder.getMilitary().getLevies();
    assertEquals(
        1,
        entries.stream()
            .filter(entry -> entry.getFrom() == source)
            .mapToInt(LevyEntry::getAmount)
            .sum());
    YamlConfiguration levyConfig = new YamlConfiguration();
    levyConfig.set("item.material", "PAPER");
    levyConfig.set("item.model-data", 19);
    levyConfig.set("description", List.of("Realm contributions"));
    levyConfig.set("levy", true);
    Regiment levy = new Regiment("levy", levyConfig);
    holder.getMilitary().getRegiments().add(levy);
    assertEquals(1, holder.getMilitary().getManpower(false));
    levy.setLevyEntries(new ArrayList<>(entries));
    assertEquals(1, levy.getLevyTotal());
    LevyEntry contribution = levy.getEntry(source);
    contribution.decrease();
    contribution.decrease();
    assertEquals(0, levy.getLevyTotal(), "Losses cannot take a displayed contribution below zero");
    contribution.increase();
    contribution.setAmount(3);
    assertEquals(3, levy.getLevyTotal());
    Faction reinforcement = domain.saved("reinforcement", "ReinforcementLeader");
    levy.addLevyEntry(new LevyEntry(reinforcement, 2));
    assertEquals(5, levy.getLevyTotal());
    assertNull(levy.getEntry(holder));
    assertEquals(List.of("Realm contributions"), new Regiment(levy).getDescription());
    assertEquals(19, levy.getIcon().getItemMeta().getCustomModelData());
    assertEquals(
        5,
        guard.getCurrentSlots(),
        "Calculating and adjusting a ledger must preserve source strength");
  }

  @Test
  void levyQueriesRefuseMissingPathsAndZeroIntermediateContributions() {
    domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction holder = domain.saved("holder", "HolderLeader");
    Faction middle = domain.saved("middle", "MiddleLeader");
    Faction source = domain.saved("source", "SourceLeader");
    regiment(source, "guard", 2);
    assertEquals(0, LevySnapshotCalculator.levyContribution(source, holder));
    assertNull(LevySnapshotCalculator.findNearestFighterHolder(source, Set.of("holder")));
    domain.subject(holder, middle);
    domain.subject(middle, source);
    var none =
        domain.law("conscription", "none", Map.of("effects.faction.modifiers", List.of("LEVY(0)")));
    middle.getLawHandler().getGroup("conscription").setCurrent(none);
    assertEquals(0, LevySnapshotCalculator.levyContribution(source, holder));
    assertTrue(
        LevySnapshotCalculator.collectLevyRowsForFighter(holder, Set.of("holder")).isEmpty());
    assertTrue(
        LevySnapshotCalculator.collectLevyRowsForFighter(holder, Set.of("unrelated_fighter"))
            .isEmpty());
    FactionManager.factions.remove(middle);
    assertNull(LevySnapshotCalculator.findNearestFighterHolder(source, Set.of("holder")));
    assertEquals(0, LevySnapshotCalculator.levyContribution(source, holder));
    assertEquals(0, LevySnapshotCalculator.directLevyContribution(null));
    assertEquals(0, LevySnapshotCalculator.levyContribution(source, null));
    assertNull(LevySnapshotCalculator.findNearestFighterHolder(source, Set.of()));
    assertTrue(LevySnapshotCalculator.collectLevyRows(null).isEmpty());
    assertTrue(LevySnapshotCalculator.collectLevyRowsForFighter(holder, Set.of()).isEmpty());
    assertTrue(LevySnapshotCalculator.collectSubjectSubtreeIds(null).isEmpty());
  }

  @Test
  void internalWarTransitRecognizesTheLiegeAndNeutralPeersWithoutExpandingObjectives() {
    Faction liege = domain.saved("liege", "LiegeLeader");
    Faction attacker = domain.saved("attacker", "AttackerLeader");
    Faction defender = domain.saved("defender", "DefenderLeader");
    Faction peer = domain.saved("peer", "PeerLeader");
    domain.subject(liege, attacker);
    domain.subject(liege, defender);
    domain.subject(liege, peer);
    War war = new War(WAR_ID, attacker, defender);
    Map<Integer, String> owners =
        Map.of(1, "attacker", 2, "defender", 3, "liege", 4, "peer", 5, "removed");
    BelligerentTerritory territory = BelligerentTerritory.fromWar(war, owners::get);
    assertTrue(territory.isLiegeTransit(3));
    assertTrue(territory.isLiegeTransit(4));
    assertFalse(territory.isForeignNation(4));
    assertFalse(territory.isLiegeTransit(1));
    assertFalse(territory.isLiegeTransit(2));
    assertFalse(territory.isLiegeTransit(5));
    assertTrue(territory.isForeignNation(5));
    assertFalse(territory.isLiegeTransit(6));
    BelligerentTerritory noLookup =
        new BelligerentTerritory(
            Set.of("attacker"), Set.of("defender"), Set.of("defender"), owners::get, "liege", null);
    assertTrue(noLookup.isLiegeTransit(3));
    assertFalse(noLookup.isLiegeTransit(4));
    BelligerentTerritory empty = new BelligerentTerritory(null, null, owners::get);
    assertFalse(empty.isAttackerSide(1));
    assertFalse(empty.isDefenderSide(2));
    assertTrue(empty.isForeignNation(1));
    assertFalse(territory.isAdjacentToSea(domain.provinces, 999));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "malformed", "scalar", "missing_sections"})
  void failedDiplomacyReloadPreservesBothLiveRegistries(String failure, @TempDir Path temp)
      throws Exception {
    List<RelationType> oldTypes = new ArrayList<>(RelationLoader.types);
    List<Attitude> oldAttitudes = new ArrayList<>(RelationLoader.attitudes);
    Path config = temp.resolve("diplomacy.yml");
    if (failure.equals("missing_sections")) Files.writeString(config, "unrelated: true\n");
    if (failure.equals("malformed")) Files.writeString(config, "types: [unterminated\n");
    if (failure.equals("scalar"))
      Files.writeString(
          config,
          """
          types:
            first:
              name: New
            broken: true
          attitudes:
            first:
              name: New
            broken: true
          """);
    RelationLoader loader = new RelationLoader();
    assertAll(
        () -> assertThrows(IllegalStateException.class, () -> loader.loadRelationTypes(config.toFile())),
        () -> assertThrows(IllegalStateException.class, () -> loader.loadAttitudes(config.toFile())),
        () -> assertEquals(oldTypes, RelationLoader.types),
        () -> assertEquals(oldAttitudes, RelationLoader.attitudes));
  }

  @Test
  void invalidThresholdCannotPublishEarlierValidRelationTypes(@TempDir Path temp) throws Exception {
    List<RelationType> oldTypes = new ArrayList<>(RelationLoader.types);
    Path config = temp.resolve("diplomacy.yml");
    Files.writeString(
        config,
        """
        types:
          first:
            name: New
          broken:
            threshold:
              mode: greater_then
              amount: 20
        """);
    assertThrows(IllegalStateException.class, () -> new RelationLoader().loadRelationTypes(config.toFile()));
    assertEquals(oldTypes, RelationLoader.types);
  }

  @Test
  void aValidDiplomacyReloadRecoversAfterFailureAndReplacesTheOldDefinitions(@TempDir Path temp)
      throws Exception {
    Path config = temp.resolve("diplomacy.yml");
    RelationLoader loader = new RelationLoader();
    RelationType previous = RelationLoader.getDefaultType();
    assertThrows(IllegalStateException.class, () -> loader.loadRelationTypes(config.toFile()));
    assertSame(previous, RelationLoader.getDefaultType());
    Files.writeString(
        config,
        """
        types:
          recovered:
            name: Recovered
            default: true
            threshold:
              mode: higher_than_or_equal_to
              amount: 7
        attitudes:
          friendly:
            name: Friendly
            default: true
            target: 4
        """);
    loader.loadRelationTypes(config.toFile());
    loader.loadAttitudes(config.toFile());
    assertEquals(1, RelationLoader.getTypes().size());
    assertEquals(1, RelationLoader.getAttitudes().size());
    assertEquals("recovered", RelationLoader.getDefaultType().getId());
    assertNotSame(previous, RelationLoader.getDefaultType());
    assertFalse(RelationLoader.getDefaultType().fulfilled(6));
    assertTrue(RelationLoader.getDefaultType().fulfilled(7));
    assertEquals("friendly", RelationLoader.getDefaultAttitude().getId());
    assertEquals(4, RelationLoader.getDefaultAttitude().getTarget());
    Files.writeString(config, Files.readString(config).replace("default: true", "default: false"));
    loader.loadRelationTypes(config.toFile());
    loader.loadAttitudes(config.toFile());
    assertEquals(
        "recovered",
        RelationLoader.getDefaultType().getId(),
        "Legacy lists without a default flag use their first definition");
    assertEquals("friendly", RelationLoader.getDefaultAttitude().getId());
  }

  @Test
  void missingAndCompanyOwnedRegimentsCannotBeExpandedThroughFactionAdministration() {
    Faction faction = domain.saved("faction", "Leader");
    var military = faction.getMilitary();
    assertFalse(military.enqueue(null));
    assertTrue(military.getQueue().isEmpty());
    YamlConfiguration companyConfig = new YamlConfiguration();
    companyConfig.set("item.material", "IRON_SWORD");
    companyConfig.set("mercenary", true);
    companyConfig.set("default-slots", 3);
    Regiment companyRegiment = new Regiment("company", companyConfig);
    military.getRegiments().add(companyRegiment);
    var result = military.adminAdjustSlots("company", 1);
    assertFalse(result.allowed());
    assertEquals(3, companyRegiment.getCurrentSlots());
    assertTrue(military.getQueue().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -25})
  void endingALevyObligationClearsTheOldSentLedgerWithoutDeletingSoldiers(int nextRate) {
    domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction holder = domain.saved("holder", "HolderLeader");
    Faction source = domain.saved("source", "SourceLeader");
    domain.subject(holder, source);
    Regiment guard = regiment(source, "guard", 3);
    assertEquals(1, holder.getMilitary().getLevies().stream().mapToInt(LevyEntry::getAmount).sum());
    assertEquals(1, guard.sentToOverlord());
    var none =
        domain.law(
            "conscription",
            "none",
            Map.of("effects.faction.modifiers", List.of("LEVY(" + nextRate + ")")));
    source.getLawHandler().getGroup("conscription").setCurrent(none);

    assertEquals(0, holder.getMilitary().getLevies().stream().mapToInt(LevyEntry::getAmount).sum());

    assertEquals(
        0,
        guard.sentToOverlord(),
        "A released contribution cannot remain available to levy losses or army lore");
    assertEquals(3, guard.getCurrentSlots());
    assertEquals(List.of("SourceLeader"), source.getMembers());
  }

  @Test
  void reciprocalModifiersRemainAttributedToTheirActualPartner() {
    Faction origin = domain.saved("origin", "OriginLeader");
    Faction partner = domain.saved("partner", "PartnerLeader");
    RelationType outgoing =
        domain.relationType("outgoing", Map.of("recieve-modifiers", List.of("LEVY(5)")));
    RelationType incoming =
        domain.relationType("incoming", Map.of("give-modifiers", List.of("TRADE_POWER(7)")));
    YamlConfiguration attitudeConfig = new YamlConfiguration();
    attitudeConfig.set("recieve-modifiers", List.of("PRESTIGE(2)"));
    Attitude attitude = new Attitude("supportive", attitudeConfig);
    origin.setRelation(partner, new Relation(outgoing, attitude));
    partner.setRelation(origin, new Relation(incoming, RelationLoader.getDefaultAttitude()));
    var modifiers = origin.getDiplomacyHandler().getModifiers();
    assertEquals(3, modifiers.size());
    assertTrue(modifiers.stream().allMatch(modifier -> modifier.getFrom() == partner));
    Map<FactionModifiers, Double> amounts =
        modifiers.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    modifier -> modifier.getType(), modifier -> modifier.getAmount()));
    assertEquals(
        Map.of(
            FactionModifiers.LEVY,
            5.0,
            FactionModifiers.TRADE_POWER,
            7.0,
            FactionModifiers.PRESTIGE,
            2.0),
        amounts);
    RelationType trade =
        domain.relationType(
            "trade", Map.of("trade-agreement", true, "trade-effects-them", List.of("TRIBUTE(3)")));
    origin.getDiplomacyHandler().setTradeRelation(partner, trade);
    var tradeEffects = origin.getDiplomacyHandler().getTradeModifiersFor(partner.getId());
    assertEquals(1, tradeEffects.size());
    assertEquals(FactionModifiers.TRIBUTE, tradeEffects.getFirst().getType());
    assertEquals(3.0, tradeEffects.getFirst().getAmount());
    assertSame(partner, tradeEffects.getFirst().getFrom());
    assertTrue(origin.getDiplomacyHandler().getTradeModifiersFor("unknown").isEmpty());
  }

  @Test
  void loadedVassalOptionsKeepElevationAndWarChoiceSeparate() {
    assertNull(RelationLoader.getElevationTarget());
    RelationType elevated =
        domain.relationType(
            "elevated",
            Map.of(
                "vassal",
                true,
                "elevation-target",
                true,
                "can-pick-for-war",
                false,
                "link",
                "overlord"));
    assertSame(elevated, RelationLoader.getElevationTarget());
    assertEquals("overlord", elevated.getLinkString());
    assertFalse(RelationLoader.isWarPickableVassal(elevated));
    assertTrue(RelationLoader.isWarPickableVassal(RelationLoader.getType("vassal")));
    assertFalse(RelationLoader.isWarPickableVassal(null));
    RelationLoader.types.remove(elevated);
    assertNull(RelationLoader.getElevationTarget());
  }

  @Test
  void restoredExpansionQueueAdvancesOnlyItsHeadAndHonoursTheThreeEntryLimit() {
    Faction faction = domain.saved("faction", "Leader");
    Regiment guard = regiment(faction, "guard", 0);
    var military = faction.getMilitary();
    military.addQueueItem(guard, 2);
    military.addQueueItem(guard, 1);
    military.addQueueItem(guard, 1);
    military.addQueueItem(guard, 1);
    assertEquals(3, military.getQueue().size());
    military.tick();
    assertEquals(0, guard.getCurrentSlots());
    assertEquals(1, military.getQueue().getFirst().getTimeLeft());
    assertEquals(1, military.getQueue().get(1).getTimeLeft());
    military.tick();
    assertEquals(1, guard.getCurrentSlots());
    assertEquals(2, military.getQueue().size());
    military.tick();
    military.tick();
    assertEquals(3, guard.getCurrentSlots());
    assertTrue(military.getQueue().isEmpty());
    military.tick();
    assertEquals(3, guard.getCurrentSlots());
  }

  @Test
  void defendingSubjectsAndJoinedBackersRemainBelligerentsInTerritoryQueries() {
    Faction attacker = domain.saved("attacker", "AttackerLeader");
    Faction defender = domain.saved("defender", "DefenderLeader");
    Faction subject = domain.saved("subject", "SubjectLeader");
    Faction backer = domain.saved("backer", "BackerLeader");
    domain.subject(defender, subject);
    War war = new War(WAR_ID, attacker, defender);
    assertTrue(war.getDefenders().getMainParticipants().getFirst().addBacker(backer));
    Map<Integer, String> owners = Map.of(1, "attacker", 2, "defender", 3, "subject", 4, "backer");
    BelligerentTerritory territory = BelligerentTerritory.fromWar(war, owners::get);
    assertTrue(territory.isDefenderSide(3));
    assertTrue(territory.isMainDefenderRealm(3));
    assertTrue(territory.isDefenderSide(4));
    assertFalse(territory.isNeutral(4));
    assertFalse(territory.isAttackerSide(4));
    assertTrue(territory.isAttackerSide(1));
  }

  private Regiment regiment(Faction faction, String id, int slots) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("item.material", "PAPER");
    yaml.set("default-slots", slots);
    Regiment regiment = new Regiment(id, yaml);
    faction.getMilitary().getRegiments().add(regiment);
    return regiment;
  }
}
