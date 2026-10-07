package net.tfminecraft.simplefactions.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

@SuppressWarnings("deprecation")
class EconomicImpactCoverageTest {
  enum Kind {
    LAW,
    TAX,
    TARIFF,
    FAVOUR,
    TRADE
  }

  GuiTestFixture gui;
  MockedStatic<FactionManager> factions;
  MockedStatic<EconomicPreview> previews;
  Player player;
  Faction faction, target;
  Guild ours;
  ProvinceManager provinces, snapshot;
  TaxHandler taxes;
  LawGroup group;
  Law law;
  RelationType agreement;
  EconomicPreview.Prepared prepared;

  @BeforeEach
  void setUp() {
    // The legacy stateless facade is also publicly constructible.
    new EconomicImpact();
    gui = new GuiTestFixture();
    player = gui.player("Reader");
    faction = mock(Faction.class);
    target = mock(Faction.class);
    ours = guild("Our Guild", "Our Realm");
    provinces = mock(ProvinceManager.class);
    snapshot = mock(ProvinceManager.class);
    taxes = mock(TaxHandler.class);
    group = mock(LawGroup.class);
    law = mock(Law.class);
    agreement = mock(RelationType.class);
    prepared = mock(EconomicPreview.Prepared.class);
    when(gui.plugin.getProvinceManager()).thenReturn(provinces);
    when(faction.getTaxHandler()).thenReturn(taxes);
    factions = mockStatic(FactionManager.class);
    factions.when(() -> FactionManager.getGuildByMember("Reader")).thenReturn(ours);
    previews = mockStatic(EconomicPreview.class);
    previews.when(EconomicPreview::current).thenReturn(prepared);
    previews.when(() -> EconomicPreview.copyOf(prepared)).thenReturn(snapshot);
    Map<Guild, Double> result = Map.of(ours, 12.5);
    when(provinces.previewLawIncomeExact(faction, group, law)).thenReturn(result);
    when(taxes.getTaxChangeEffects(TaxTarget.GUILDS, "subject", 17.0)).thenReturn(result);
    when(provinces.previewTariffRateChange(faction, "subject", 17.0)).thenReturn(result);
    when(snapshot.previewTariffRateChange(faction, "subject", 17.0)).thenReturn(result);
    when(provinces.previewFavourRepressIncomeExact(faction, ours, true)).thenReturn(result);
    when(provinces.previewTradeAgreementIncomeExact(faction, target, agreement)).thenReturn(result);
    previews.when(() -> EconomicPreview.law(prepared, faction, group, law)).thenReturn(result);
    previews
        .when(() -> EconomicPreview.tax(faction, TaxTarget.GUILDS, "subject", 17.0))
        .thenReturn(result);
    previews.when(() -> EconomicPreview.favour(prepared, ours, true)).thenReturn(result);
    previews
        .when(() -> EconomicPreview.trade(prepared, faction, target, agreement))
        .thenReturn(result);
  }

  @AfterEach
  void close() {
    gui.runTasks();
    previews.close();
    factions.close();
    gui.close();
  }

  static Stream<Arguments> variants() {
    return Stream.of(Kind.values())
        .flatMap(kind -> Stream.of(0, 1, 2).map(form -> Arguments.of(kind, form)));
  }

  @ParameterizedTest
  @MethodSource("variants")
  void eachPublicOverloadShowsTheRequestedEstimate(Kind kind, int form) {
    List<String> lore = new ArrayList<>(List.of("Existing description"));
    apply(kind, lore, player, form, form == 2 ? new ItemStack(Material.PAPER).getItemMeta() : null);
    String rendered = plain(lore);
    assertTrue(rendered.startsWith("Existing description\n"));
    assertTrue(rendered.contains("Our Guild: +12.50" + (form == 1 ? "d" : "d/day")), rendered);
    assertTrue(rendered.contains(form == 1 ? "Impacts:" : "Estimated Economic Impacts:"));
    verifyCalculation(kind, false);
    assertTrue(
        gui.tasks.isEmpty(), "Disabled plugin and metadata-free calls compute synchronously");
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void estimatesAreDeferredAndReplaceThePlaceholderOnTheSameItem(Kind kind) {
    when(gui.plugin.isEnabled()).thenReturn(true);
    ItemStack item = new ItemStack(Material.PAPER);
    ItemMeta meta = item.getItemMeta();
    List<String> lore = new ArrayList<>(List.of("Proposal"));
    apply(kind, lore, player, 2, meta);
    lore.add("Click to propose");
    meta.setLore(lore);
    item.setItemMeta(meta);
    player.getInventory().setItem(3, item);
    assertTrue(plain(lore).contains("Calculating economic impact..."));
    verifyNoInteractions(provinces, taxes);
    gui.runTasks();
    assertEquals(1, gui.asyncTasks.size());
    gui.asyncTasks.removeFirst().run();
    assertTrue(plain(item.getItemMeta().getLore()).contains("Calculating"));
    gui.runTasks();
    String rendered = plain(player.getInventory().getItem(3).getItemMeta().getLore());
    assertTrue(rendered.startsWith("Proposal\n"));
    assertTrue(rendered.endsWith("Click to propose"));
    assertTrue(rendered.contains("Our Guild: +12.50d/day"));
    assertFalse(rendered.contains("Calculating"));
    verifyCalculation(kind, true);
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void absentPlayerOrGuildLeavesExistingLoreAlone(Kind kind) {
    List<String> lore = new ArrayList<>(List.of("Unchanged"));
    apply(kind, lore, null, 0, null);
    factions.when(() -> FactionManager.getGuildByMember("Reader")).thenReturn(null);
    apply(kind, lore, player, 2, new ItemStack(Material.PAPER).getItemMeta());
    assertEquals(List.of("Unchanged"), lore);
    verifyNoInteractions(provinces, taxes);
  }

  @Test
  void rendererPrioritizesOurGuildThenTheFiveLargestOtherChanges() {
    LinkedHashMap<Guild, Double> changes = new LinkedHashMap<>();
    changes.put(guild("Tiny", "Realm"), 0.25);
    changes.put(guild("Zero", "Realm"), 0.0);
    changes.put(ours, -2.0);
    changes.put(guild("Third", "C"), 30.0);
    changes.put(guild("First", "A"), -50.0);
    changes.put(guild("Fifth", "E"), 10.0);
    changes.put(guild("Second", "B"), 40.0);
    changes.put(guild("Fourth", "D"), -20.0);
    List<String> lore = new ArrayList<>();
    EconomicImpact.write(lore, changes, ours, false);
    List<String> rows =
        lore.stream().map(ChatColor::stripColor).filter(line -> line.contains("d/day")).toList();
    assertEquals(
        List.of(
            "  Our Guild: -2.00d/day",
            "  First (A): -50.00d/day",
            "  Second (B): +40.00d/day",
            "  Third (C): +30.00d/day",
            "  Fourth (D): -20.00d/day",
            "  Fifth (E): +10.00d/day"),
        rows);
    assertFalse(plain(lore).contains("Tiny"));
    assertFalse(plain(lore).contains("Zero"));
  }

  @Test
  void compactAndUnchangedEstimatesHaveReadableDistinctFormats() {
    List<String> compact = new ArrayList<>();
    EconomicImpact.write(compact, Map.of(guild("Other", "Realm"), -3.0), ours, true);
    assertTrue(plain(compact).contains("Other: -3.00d"));
    assertFalse(plain(compact).contains("Realm"));
    assertFalse(plain(compact).contains("No Change"));
    List<String> unchanged = new ArrayList<>();
    EconomicImpact.write(unchanged, Map.of(ours, 0.0), ours, false);
    assertTrue(plain(unchanged).endsWith("  No Economic Change"));
    List<String> empty = new ArrayList<>();
    EconomicImpact.write(empty, Map.of(), ours, true);
    assertTrue(plain(empty).endsWith("No Change"));
  }

  private void verifyCalculation(Kind kind, boolean async) {
    if (async) {
      switch (kind) {
        case LAW -> previews.verify(() -> EconomicPreview.law(prepared, faction, group, law));
        case TAX ->
            previews.verify(() -> EconomicPreview.tax(faction, TaxTarget.GUILDS, "subject", 17.0));
        case TARIFF -> verify(snapshot).previewTariffRateChange(faction, "subject", 17.0);
        case FAVOUR -> previews.verify(() -> EconomicPreview.favour(prepared, ours, true));
        case TRADE ->
            previews.verify(() -> EconomicPreview.trade(prepared, faction, target, agreement));
      }
    } else {
      switch (kind) {
        case LAW -> verify(provinces).previewLawIncomeExact(faction, group, law);
        case TAX -> verify(taxes).getTaxChangeEffects(TaxTarget.GUILDS, "subject", 17.0);
        case TARIFF -> verify(provinces).previewTariffRateChange(faction, "subject", 17.0);
        case FAVOUR -> verify(provinces).previewFavourRepressIncomeExact(faction, ours, true);
        case TRADE ->
            verify(provinces).previewTradeAgreementIncomeExact(faction, target, agreement);
      }
    }
  }

  private void apply(Kind kind, List<String> lore, Player player, int form, ItemMeta meta) {
    switch (kind) {
      case LAW -> {
        if (form == 0) EconomicImpact.applyEconomicChange(lore, player, faction, group, law);
        else if (form == 1)
          EconomicImpact.applyEconomicChange(lore, player, faction, group, law, true);
        else
          EconomicImpact.applyEconomicChange(lore, player, faction, group, law, false, meta, false);
      }
      case TAX -> {
        if (form == 0)
          EconomicImpact.applyTaxImpact(lore, player, faction, TaxTarget.GUILDS, "subject", 17.0);
        else if (form == 1)
          EconomicImpact.applyTaxImpact(
              lore, player, faction, TaxTarget.GUILDS, "subject", 17.0, true);
        else
          EconomicImpact.applyTaxImpact(
              lore, player, faction, TaxTarget.GUILDS, "subject", 17.0, false, meta, false);
      }
      case TARIFF -> {
        if (form == 0) EconomicImpact.applyTariffImpact(lore, player, faction, "subject", 17.0);
        else if (form == 1)
          EconomicImpact.applyTariffImpact(lore, player, faction, "subject", 17.0, true);
        else
          EconomicImpact.applyTariffImpact(
              lore, player, faction, "subject", 17.0, false, meta, false);
      }
      case FAVOUR -> {
        if (form == 0) EconomicImpact.applyFavourRepressChange(lore, player, faction, ours, true);
        else if (form == 1)
          EconomicImpact.applyFavourRepressChange(lore, player, faction, ours, true, true);
        else
          EconomicImpact.applyFavourRepressChange(
              lore, player, faction, ours, true, false, meta, false);
      }
      case TRADE -> {
        if (form == 0)
          EconomicImpact.applyTradeAgreementChange(lore, player, faction, target, agreement);
        else if (form == 1)
          EconomicImpact.applyTradeAgreementChange(lore, player, faction, target, agreement, true);
        else
          EconomicImpact.applyTradeAgreementChange(
              lore, player, faction, target, agreement, false, meta, false);
      }
    }
  }

  private Guild guild(String name, String realm) {
    Guild guild = mock(Guild.class);
    Faction host = mock(Faction.class);
    when(guild.getName()).thenReturn(name);
    when(guild.getFaction()).thenReturn(host);
    when(host.getName()).thenReturn(realm);
    return guild;
  }

  private static String plain(List<String> lore) {
    return ChatColor.stripColor(String.join("\n", lore));
  }
}
