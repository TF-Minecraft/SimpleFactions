package net.tfminecraft.simplefactions.war.declare;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarGoalPreviewTest {
  private FactionDomainFixture fixture;
  private double previousPercent;
  private int previousDays;
  private int previousLootDays;
  private Faction attacker;
  private Faction defender;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    previousPercent = Cache.warReparationsIncomePercent;
    previousDays = Cache.warReparationsDays;
    previousLootDays = Cache.pillageLootDays;
    Cache.warReparationsIncomePercent = 25;
    Cache.warReparationsDays = 10;
    Cache.pillageLootDays = 10;
    attacker = fixture.saved("attacker", "Alice");
    defender = fixture.saved("defender", "Bob");
  }

  @AfterEach
  void cleanup() {
    Cache.warReparationsIncomePercent = previousPercent;
    Cache.warReparationsDays = previousDays;
    Cache.pillageLootDays = previousLootDays;
    if (fixture != null) fixture.close();
  }

  private static void income(Guild guild, double amount) {
    TradeBreakdown trade = new TradeBreakdown();
    trade.setIncome(amount);
    guild.setTradeBreakdown(trade);
  }

  @Test
  void reparationsCountTheLoserAndItsSubjectsAtTodaysTradeIncome() {
    Faction vassal = fixture.saved("vassal", "Cara");
    fixture.subject(defender, vassal);
    income(defender.getOrCreateMainGuild(), 300);
    income(vassal.getOrCreateMainGuild(), 100);
    income(fixture.guild(defender, "debtors", "Dee"), -50);
    income(attacker.getOrCreateMainGuild(), 40);

    assertEquals(100.0, WarGoalPreview.dailyReparations(defender), 1e-9);
    assertEquals(10.0, WarGoalPreview.dailyReparations(attacker), 1e-9);
    String text = String.join("\n", WarGoalPreview.reparations(attacker, defender));
    assertTrue(text.contains("25% of their trade income for 10 days"), text);
    assertTrue(text.contains("about §e100.00d §7a day, §e1000.00d §7in total"), text);
    assertTrue(text.contains("the same share (about §e10.00d §7a day)"), text);
    Cache.warReparationsIncomePercent = 12.5;
    assertTrue(String.join("\n", WarGoalPreview.reparations(attacker, defender)).contains("12.5%"));
  }

  @Test
  void subjectEffectsShowWhatEachSideGetsAndNothingForAPlainType() {
    var tributary =
        fixture.relationType(
            "tributary",
            Map.of("give-modifiers", List.of("levy(25.0)"), "recieve-modifiers", List.of("prestige(5.0)")));
    String text = String.join("\n", WarGoalPreview.subjectEffects(tributary, attacker, defender));
    assertTrue(text.startsWith("§7You get:"), text);
    assertTrue(text.contains("§7They get:"), text);
    assertEquals(4, WarGoalPreview.subjectEffects(tributary, attacker, defender).size());
    assertTrue(WarGoalPreview.subjectEffects(fixture.relationType("plain", Map.of("name", "Plain")), attacker, defender).isEmpty());
    assertTrue(WarGoalPreview.subjectEffects(null, attacker, defender).isEmpty());
  }

  @Test
  void usurpNamesTheTitleAndTheSubjectsThatChangeHands() {
    fixture.title("crown", "duchy");
    FactionData data = fixture.data("king", "Kai");
    data.titles.add("crown");
    Faction king = fixture.saved(data);
    assertEquals(List.of("§7Title: §fcrown", "§7They become your subject"), WarGoalPreview.usurp(king));
    fixture.subject(king, fixture.saved("one", "One"));
    assertTrue(WarGoalPreview.usurp(king).contains("§7Their §f1 §7subject becomes yours"));
    fixture.subject(king, fixture.saved("two", "Two"));
    assertTrue(WarGoalPreview.usurp(king).contains("§7Their §f2 §7subjects become yours"));
    assertEquals(List.of("§7They become your subject"), WarGoalPreview.usurp(attacker));
  }

  @Test
  void pillageShowsLootFromTheGuildsInTheSettlementAndPicksTheRichestEligibleOne() {
    Guild rich = fixture.guild(defender, "rich", "Rae");
    rich.setCapital(5, false);
    when(fixture.provinces.getIncome(rich, false)).thenReturn(30.0);
    Guild poor = fixture.guild(defender, "poor", "Pat");
    poor.setCapital(6, false);
    when(fixture.provinces.getIncome(poor, false)).thenReturn(7.0);
    Settlement richTown = settlement("Richtown", 5);
    Settlement poorTown = settlement("Poortown", 6);

    String text = String.join("\n", WarGoalPreview.pillage(richTown));
    assertTrue(text.contains("Loot: §e300.00d"), text);
    assertTrue(text.contains("from 1 guild)"), text);
    assertTrue(text.contains("lose 100%"), text);
    assertTrue(String.join("\n", WarGoalPreview.pillage(settlement("Empty", 9))).contains("from 0 guilds)"));

    var options =
        List.of(
            new PillageEligibility.PillageSettlementOption(poorTown, true, null),
            new PillageEligibility.PillageSettlementOption(richTown, false, "§cToo far"));
    assertEquals("§7Best loot: §e70.00d §7from Poortown", WarGoalPreview.bestPillage(options));
    assertNull(WarGoalPreview.bestPillage(List.of(options.get(1))));
  }

  private static Settlement settlement(String name, int province) {
    Settlement settlement = mock(Settlement.class);
    when(settlement.getName()).thenReturn(name);
    when(settlement.contains(province)).thenReturn(true);
    return settlement;
  }
}
