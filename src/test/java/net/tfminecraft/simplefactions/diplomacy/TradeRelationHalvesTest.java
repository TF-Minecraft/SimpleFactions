package net.tfminecraft.simplefactions.diplomacy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A guild trading in another faction's province gets the owner's half of their trade relation ("their guilds
 * in our territory") plus its own faction's half ("our guilds in their territory"), as the Diplomacy menu
 * shows them. The relation types mirror Main's diplomacy.yml.
 */
class TradeRelationHalvesTest {
  private FactionDomainFixture domain;
  private RelationType leaderSide;
  private RelationType subjectSide;
  private RelationType agreement;
  private RelationType embargo;

  @BeforeEach
  void setUp() {
    domain = new FactionDomainFixture();
    agreement =
        domain.relationType(
            "trade_agreement",
            Map.of(
                "trade-agreement", true,
                "mutual", true,
                "trade-effects-us", List.of("trade_power(15.0)", "production(5.0)")));
    leaderSide =
        domain.relationType(
            "unequal_treaty_leader",
            Map.of(
                "trade-agreement", true,
                "mutual", true,
                "link", "unequal_treaty_subject",
                "trade-effects-us", List.of("trade_power(20.0)", "production(7.5)")));
    subjectSide =
        domain.relationType(
            "unequal_treaty_subject",
            Map.of(
                "trade-agreement", true,
                "lock", true,
                "trade-effects-us", List.of("trade_power(5.0)", "production(2.5)")));
    embargo =
        domain.relationType(
            "embargo",
            Map.of(
                "trade-agreement", true,
                "trade-effects-them", List.of("trade_power(-95.0)", "production(-95.0)")));
  }

  @AfterEach
  void tearDown() {
    domain.close();
  }

  @Test
  void unequalTreatyGivesTheLeaderItsBonusInTheWeakerSidesLand() {
    Faction leader = domain.saved("leader", "LeaderPlayer");
    Faction weaker = domain.saved("weaker", "WeakerPlayer");
    leader.getDiplomacyHandler().setTradeRelation(weaker, leaderSide);
    weaker.getDiplomacyHandler().setTradeRelation(leader, subjectSide);

    // The leader's guilds trading in the weaker side's provinces.
    assertEquals(
        Map.of(FactionModifiers.TRADE_POWER, 20.0, FactionModifiers.PRODUCTION, 7.5),
        amounts(weaker.getDiplomacyHandler().getTradeModifiersFor(leader)));
    // The weaker side's guilds trading in the leader's provinces.
    assertEquals(
        Map.of(FactionModifiers.TRADE_POWER, 5.0, FactionModifiers.PRODUCTION, 2.5),
        amounts(leader.getDiplomacyHandler().getTradeModifiersFor(weaker)));
    assertTrue(
        weaker.getDiplomacyHandler().getTradeModifiersFor(leader).stream()
            .allMatch(modifier -> modifier.getFrom() == leader));
  }

  @Test
  void tradeAgreementStillGivesEachSideItsBonusOnce() {
    Faction first = domain.saved("first", "FirstPlayer");
    Faction second = domain.saved("second", "SecondPlayer");
    first.getDiplomacyHandler().setTradeRelation(second, agreement);
    second.getDiplomacyHandler().setTradeRelation(first, agreement);

    Map<FactionModifiers, Double> expected =
        Map.of(FactionModifiers.TRADE_POWER, 15.0, FactionModifiers.PRODUCTION, 5.0);
    assertEquals(expected, amounts(first.getDiplomacyHandler().getTradeModifiersFor(second)));
    assertEquals(expected, amounts(second.getDiplomacyHandler().getTradeModifiersFor(first)));
  }

  @Test
  void embargoStillHitsOnlyTheEmbargoedSide() {
    Faction embargoer = domain.saved("embargoer", "EmbargoPlayer");
    Faction target = domain.saved("target", "TargetPlayer");
    embargoer.getDiplomacyHandler().setTradeRelation(target, embargo);

    assertEquals(
        Map.of(FactionModifiers.TRADE_POWER, -95.0, FactionModifiers.PRODUCTION, -95.0),
        amounts(embargoer.getDiplomacyHandler().getTradeModifiersFor(target)));
    assertTrue(target.getDiplomacyHandler().getTradeModifiersFor(embargoer).isEmpty());
  }

  @Test
  void embargoOverTheOtherSidesAgreementKeepsItsFullForceAndOldResults() {
    Faction kept = domain.saved("kept", "KeptPlayer");
    Faction embargoer = domain.saved("embargoer", "EmbargoPlayer");
    // Only the embargoing side's record changes; the other side keeps its old agreement half.
    kept.getDiplomacyHandler().setTradeRelation(embargoer, agreement);
    embargoer.getDiplomacyHandler().setTradeRelation(kept, embargo);

    assertEquals(
        Map.of(FactionModifiers.TRADE_POWER, -95.0, FactionModifiers.PRODUCTION, -95.0),
        amounts(embargoer.getDiplomacyHandler().getTradeModifiersFor(kept)));
    assertEquals(
        Map.of(FactionModifiers.TRADE_POWER, 15.0, FactionModifiers.PRODUCTION, 5.0),
        amounts(kept.getDiplomacyHandler().getTradeModifiersFor(embargoer)));
  }

  @Test
  void leaderEmbargoingTheWeakerSideIsNotSoftenedByTheOldTreatyHalf() {
    Faction leader = domain.saved("leader", "LeaderPlayer");
    Faction weaker = domain.saved("weaker", "WeakerPlayer");
    leader.getDiplomacyHandler().setTradeRelation(weaker, embargo);
    weaker.getDiplomacyHandler().setTradeRelation(leader, subjectSide);

    assertEquals(
        Map.of(FactionModifiers.TRADE_POWER, -95.0, FactionModifiers.PRODUCTION, -95.0),
        amounts(leader.getDiplomacyHandler().getTradeModifiersFor(weaker)));
    assertTrue(weaker.getDiplomacyHandler().getTradeModifiersFor(leader).isEmpty());
  }

  @Test
  void noRelationOrUnknownFactionGivesNothing() {
    Faction first = domain.saved("first", "FirstPlayer");
    Faction second = domain.saved("second", "SecondPlayer");

    assertTrue(first.getDiplomacyHandler().getTradeModifiersFor(second).isEmpty());
    assertTrue(first.getDiplomacyHandler().getTradeModifiersFor((Faction) null).isEmpty());
    assertTrue(first.getDiplomacyHandler().getTradeModifiersFor("unknown").isEmpty());
  }

  private static Map<FactionModifiers, Double> amounts(List<FactionModifier> modifiers) {
    return modifiers.stream()
        .collect(Collectors.toMap(FactionModifier::getType, FactionModifier::getAmount, Double::sum));
  }
}
