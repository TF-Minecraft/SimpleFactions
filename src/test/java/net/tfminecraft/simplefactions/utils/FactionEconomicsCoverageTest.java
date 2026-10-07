package net.tfminecraft.simplefactions.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import net.tfminecraft.denareconomy.accounts.OfflineModifier;
import net.tfminecraft.denareconomy.enums.Accounts;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.RankType;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.espionage.EspionageConfig;
import net.tfminecraft.simplefactions.espionage.EspionageMath;
import net.tfminecraft.simplefactions.espionage.IntelligenceReport;
import net.tfminecraft.simplefactions.espionage.SpecialPosition;
import net.tfminecraft.simplefactions.espionage.SpecialPositionAssignment;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.BankManager;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.BankPlacementValidator;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.objects.ModifierScale;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class FactionEconomicsCoverageTest {
  @TempDir Path temporary;

  @Test
  void editingAModifierRetainsTwoDecimalPlaces() {
    FactionModifier modifier = new FactionModifier(FactionModifiers.TAX_MULTIPLIER, 12.5);
    modifier.edit(0.25);
    assertEquals(12.75, modifier.getAmount());
    modifier.edit(-0.125);
    assertEquals(12.63, modifier.getAmount());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void configuredModifierIdentifiersDoNotDependOnTheServerLocale(boolean mapped) {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      YamlConfiguration config = new YamlConfiguration();
      Object entry =
          mapped ? Map.of("type", "military_upkeep", "amount", 12.5) : "military_upkeep(12.5)";
      config.set("modifiers", List.of(entry));
      List<FactionModifier> loaded = new ArrayList<>();
      FactionModifier.addFromConfig(config, "modifiers", loaded);
      assertEquals(1, loaded.size(), "A valid configured modifier must survive a locale change");
      assertEquals(FactionModifiers.MILITARY_UPKEEP, loaded.getFirst().getType());
      assertEquals(12.5, loaded.getFirst().getAmount());
    } finally {
      Locale.setDefault(original);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "LEVY,Levy Contribution",
    "MILITARY_UPKEEP,Military Upkeep",
    "NODE_SPEED,Node Speed",
    "PRESTIGE,Prestige to Overlord",
    "PRESTIGE_BONUS,Prestige Bonus",
    "PRESTIGE_MALUS,Prestige Malus",
    "TRIBUTE,Tribute",
    "TAX_MULTIPLIER,Tax Multiplier",
    "DE_JURE,De Jure Requirement",
    "STABILITY_INFLUENCE,Stability Influence",
    "TRADE_POWER,Trade Power",
    "PRODUCTION,Production",
    "INSTALLATION_ACCESS,Installation Access",
    "DIPLOMATIC_CAPACITY_MULTIPLIER,Diplomatic Capacity Multiplier",
    "ADMIN_POWER_MULTIPLIER,Admin Power Multiplier",
    "ADMIN_POWER_GAIN_MULTIPLIER,Admin Power Gain Multiplier"
  })
  void eachConfiguredModifierHasADescriptivePlayerFacingLabel(FactionModifiers type, String label) {
    double oldRequirement = Cache.deJureRequirement;
    Cache.deJureRequirement = 50;
    try {
      String text = ChatColor.stripColor(new FactionModifier(type, 12.345).getString());
      assertTrue(text.startsWith(label + ": "), text);
      String expected =
          type == FactionModifiers.INSTALLATION_ACCESS
              ? "1234.5"
              : type == FactionModifiers.TAX_MULTIPLIER
                  ? "+12.35"
                  : type == FactionModifiers.PRESTIGE_MALUS ? "-12.35" : "12.35";
      assertTrue(text.endsWith("(" + expected + "%)"), text);
    } finally {
      Cache.deJureRequirement = oldRequirement;
    }
  }

  @Test
  void modifierLoreDistinguishesBeneficialSignsAndFractionalAccess() {
    FactionModifier upkeep = new FactionModifier(FactionModifiers.MILITARY_UPKEEP, -2.5);
    assertTrue(upkeep.getString().contains(StringFormatter.formatHex("§7(#87d65c-2.5%§7)")));
    FactionModifier access = new FactionModifier(FactionModifiers.INSTALLATION_ACCESS, 0.125);
    assertTrue(
        ChatColor.stripColor(access.getString(null, Region.FOREIGN_TERRITORY))
            .endsWith("(+12.5%)"));
    assertTrue(ChatColor.stripColor(access.getString()).endsWith("(12.5%)"));
    assertFalse(access.isMultiplier());
    assertTrue(new FactionModifier(FactionModifiers.TAX_MULTIPLIER, 1).isMultiplier());
    assertTrue(
        new FactionModifier(FactionModifiers.PRODUCTION, -1)
            .getString()
            .contains(StringFormatter.formatHex("§7(#d65c5c-1%§7)")));
  }

  @Test
  void yamlModifiersSkipMalformedEntriesAndKeepValidLegacyAliases() {
    YamlConfiguration yaml = new YamlConfiguration();
    List<Object> entries = new ArrayList<>();
    entries.add("infrastructure_access(0.25)");
    entries.add(Map.of("type", "production", "amount", "7.5"));
    entries.add(Map.of("type", "trade_power", "amount", "not-a-number"));
    entries.add(Map.of("amount", 10));
    entries.add(Map.of("type", "not_a_modifier", "amount", 10));
    entries.add("invalid");
    entries.add(42);
    yaml.set("modifiers", entries);
    List<FactionModifier> loaded = new ArrayList<>();
    FactionModifier.addFromConfig(yaml, "modifiers", loaded);
    assertEquals(3, loaded.size());
    assertEquals(FactionModifiers.INSTALLATION_ACCESS, loaded.get(0).getType());
    assertEquals(0.25, loaded.get(0).getAmount());
    assertEquals(7.5, loaded.get(1).getAmount());
    assertEquals(0.0, loaded.get(2).getAmount());
    assertNull(FactionModifier.fromYamlEntry(null));
    FactionModifier.addFromConfig(null, "modifiers", loaded);
    FactionModifier.addFromConfig(yaml, "missing", loaded);
    yaml.set("empty", List.of());
    FactionModifier.addFromConfig(yaml, "empty", loaded);
    yaml.set("scalar", "production(50)");
    FactionModifier.addFromConfig(yaml, "scalar", loaded);
    assertEquals(3, loaded.size(), "Missing or non-list settings must not invent modifiers");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "invalid",
        "not_a_modifier(5)",
        "production(NaN)",
        "production(Infinity)",
        "production(-Infinity)"
      })
  void malformedLawModifiersCannotPoisonTheRemainingEconomicEffects(String invalid) {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      fixture.lawGroup(
          "economy", Map.of("effects.faction.modifiers", List.of(invalid, "production(7.5)")));
      Faction faction = fixture.saved("home", "Leader");
      assertEquals(7.5, faction.getModifier(FactionModifiers.PRODUCTION).getAmount());
      assertTrue(
          faction.getModifiers().stream()
              .allMatch(
                  modifier -> modifier.getType() != null && Double.isFinite(modifier.getAmount())));
      assertTrue(
          faction.getCombinedModifiers().stream()
              .anyMatch(
                  modifier ->
                      ChatColor.stripColor(modifier.getString()).startsWith("Production:")));
    }
  }

  @Test
  void malformedRankModifiersDoNotDiscardValidSiblingBonuses() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("name", "Artisan");
    yaml.set("level", 1);
    yaml.set("modifiers", List.of("invalid", "production(NaN)", "trade_power(7.5)"));
    var rank = new net.tfminecraft.simplefactions.objects.PrestigeRank("artisan", yaml);
    assertEquals(1, rank.getModifiers().size());
    FactionModifier valid = rank.getModifiers().getFirst();
    assertEquals(FactionModifiers.TRADE_POWER, valid.getType());
    assertEquals(7.5, valid.getAmount());
    assertTrue(rank.hasModifiers());
  }

  @ParameterizedTest
  @ValueSource(strings = {"amount", "at_equal", "at_weaker", "at_stronger"})
  void mappedNonFiniteModifierNumbersCannotReachTheEconomicCalculation(String key) {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Faction owner = fixture.saved("owner", "Owner");
      Faction partner = fixture.saved("partner", "Partner");
      owner.setPrestige(100.0);
      Map<String, Object> entry =
          new HashMap<>(Map.of("type", "production", "scale", "relative_prestige", "amount", 3.0));
      entry.put(key, Double.NaN);
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("modifiers", List.of(entry, "trade_power(4)"));
      List<FactionModifier> loaded = new ArrayList<>();
      FactionModifier.addFromConfig(yaml, "modifiers", loaded);
      assertTrue(
          loaded.stream()
              .anyMatch(
                  modifier ->
                      modifier.getType() == FactionModifiers.TRADE_POWER
                          && modifier.getAmount() == 4));
      for (FactionModifier modifier : loaded) {
        FactionModifier bound = new FactionModifier(partner, modifier);
        for (double prestige : List.of(0.0, 100.0, 1000.0)) {
          partner.setPrestige(prestige);
          assertTrue(
              Double.isFinite(bound.resolve(owner)),
              key + " must not introduce NaN into live faction modifiers");
        }
      }
    }
  }

  @Test
  void relativePrestigeCopiesUseTheirActualPartnerAndKeepTheTemplateUnbound() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Faction owner = fixture.saved("owner", "Owner");
      Faction partner = fixture.saved("partner", "Partner");
      owner.setPrestige(100.0);
      partner.setPrestige(250.0);
      FactionModifier template =
          FactionModifier.fromYamlEntry(
              Map.of(
                  "type",
                  "trade_power",
                  "scale",
                  "relative_prestige",
                  "at_weaker",
                  "-4",
                  "at_equal",
                  "10",
                  "at_stronger",
                  "20"));
      FactionModifier bound = new FactionModifier(partner, template);
      assertSame(partner, bound.getFrom());
      assertNull(template.getFrom());
      assertEquals(ModifierScale.Kind.RELATIVE_PRESTIGE, bound.getScale());
      assertEquals(20.0, bound.resolve(owner));
      assertEquals(10.0, template.resolve(owner));
      assertEquals(10.0, bound.resolve(null));
      assertTrue(
          ChatColor.stripColor(bound.getString(owner)).endsWith("(20%) (vs their prestige)"));
      partner.setPrestige(40.0);
      assertEquals(-4.0, bound.resolve(owner), 1e-9);
      partner.setPrestige(0.0);
      assertEquals(-4.0, bound.resolve(owner));
      assertEquals("TRADE_POWER{from=partner, amount=10.0}", bound.toString());
      assertEquals("TRADE_POWER{from=null, amount=10.0}", template.toString());
      FactionModifier flat = new FactionModifier(partner, FactionModifiers.PRODUCTION, 3.5);
      assertSame(partner, flat.getFrom());
      assertEquals(3.5, flat.resolve(owner));
    }
  }

  @Test
  void malformedScaleNumbersUseTheConfiguredEqualFallbackAndDeJureKeepsItsFloor() {
    FactionModifier modifier =
        FactionModifier.fromYamlEntry(
            Map.of(
                "type",
                "production",
                "scale",
                "unknown",
                "amount",
                6,
                "at_equal",
                "invalid",
                "at_weaker",
                List.of(1),
                "at_stronger",
                false));
    assertEquals(ModifierScale.Kind.NONE, modifier.getScale());
    assertEquals(6.0, modifier.getAmount());
    assertEquals(ModifierScale.Kind.NONE, ModifierScale.kindFrom(" "));
    double oldRequirement = Cache.deJureRequirement;
    try {
      Cache.deJureRequirement = 60;
      FactionModifier deJure = new FactionModifier(FactionModifiers.DE_JURE, -90);
      assertEquals(-40.0, deJure.getAmount());
      assertEquals(20.0, Cache.deJureRequirement + deJure.getAmount());
    } finally {
      Cache.deJureRequirement = oldRequirement;
    }
    for (FactionModifiers type : FactionModifiers.values())
      assertEquals(
          List.of(
                  FactionModifiers.TRADE_POWER,
                  FactionModifiers.PRODUCTION,
                  FactionModifiers.INSTALLATION_ACCESS)
              .contains(type),
          type.affectsEconomy(),
          type.name());
  }

  @ParameterizedTest
  @EnumSource(
      value = RankType.class,
      names = {"WEALTH", "PRESTIGE", "MEMBERS"})
  void legacyFactionRankingsUseIndependentSortedSnapshots(RankType type) {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Faction large = fundedFaction(fixture, "large", "Large", 300);
      Faction small = fundedFaction(fixture, "small", "Small", 100);
      Faction middle = fundedFaction(fixture, "middle", "Middle", 200);
      large.getOrCreateMainGuild().addMember("LargeTwo");
      large.getOrCreateMainGuild().addMember("LargeThree");
      middle.getOrCreateMainGuild().addMember("MiddleTwo");
      small.setPrestige(10.0);
      middle.setPrestige(20.0);
      large.setPrestige(30.0);
      FactionRanker ranker = new FactionRanker();
      assertEquals(List.of(small, middle, large), ranker.getRankedList(type));
      assertEquals(List.of(large, small, middle), FactionManager.factions);
      assertEquals(1, ranker.getWealthRank(large));
      assertEquals(3, ranker.getWealthRank(small));
      assertEquals(2, ranker.getPrestigeRank(middle));
      assertEquals(List.of(large, small, middle), ranker.getRankedList(RankType.TRADE_POWER));
      FactionManager.factions.remove(middle);
      assertEquals(0, ranker.getPrestigeRank(middle));
      assertEquals(0, ranker.getWealthRank(middle));
      assertNull(ranker.getVisibleRank(fixture.online.get("Middle"), middle, type));
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = RankType.class,
      names = {"WEALTH", "MEMBERS", "INCOME", "TRADE_POWER"})
  void guildRankingsAndVisibleValuesUseRealGuildEconomics(RankType type) {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Faction faction = fundedFaction(fixture, "home", "Leader", 0);
      Guild small = fixture.guild(faction, "small", "Small");
      Guild large = fixture.guild(faction, "large", "Large");
      small.getBank().deposit(100.0);
      large.getBank().deposit(300.0);
      large.addMember("Additional");
      small.getTradeBreakdown().setTradePower(5);
      large.getTradeBreakdown().setTradePower(25);
      small.getTradeBreakdown().setIncome(50);
      large.getTradeBreakdown().setIncome(250);
      faction.getTaxHandler().setGuildTax(0);
      Player viewer = fixture.online.get("Leader");
      FactionRanker ranker = new FactionRanker();
      List<Guild> raw = ranker.getRankedGuildList(type);
      assertTrue(raw.indexOf(small) < raw.indexOf(large));
      double smallMetric =
          switch (type) {
            case WEALTH -> 100.0;
            case MEMBERS -> 1.0;
            case INCOME -> small.getLedger().getNetIncome();
            case TRADE_POWER -> 5.0;
            default -> throw new AssertionError(type);
          };
      assertEquals(smallMetric, ranker.visibleGuildValue(viewer, small, type));
      List<Guild> visible = ranker.getVisibleRankedGuildList(viewer, type);
      assertTrue(visible.indexOf(large) < visible.indexOf(small));
      assertEquals(visible.indexOf(small) + 1, ranker.getVisibleGuildRank(viewer, small, type));
      assertEquals(1, ranker.getWealthRank(large));
      faction.getGuildHandler().removeGuild("small", false, false);
      assertEquals(0, ranker.getWealthRank(small));
      assertNull(ranker.getVisibleGuildRank(viewer, small, type));
      assertEquals(FactionManager.getAllGuilds(), ranker.getRankedGuildList(RankType.PRESTIGE));
    }
  }

  @Test
  void unknownForeignValuesSortLastAndNeverReceiveAnExactRank() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Faction hiddenZulu = fundedFaction(fixture, "Zulu", "ZuluLeader", 9000);
      Faction home = fundedFaction(fixture, "home", "Leader", 100);
      Faction hiddenAlpha = fundedFaction(fixture, "Alpha", "AlphaLeader", 8000);
      Faction open = fundedFaction(fixture, "open", "OpenLeader", 200);
      guard(hiddenZulu, fixture.online.get("ZuluLeader"));
      guard(hiddenAlpha, fixture.online.get("AlphaLeader"));
      Player viewer = fixture.online.get("Leader");
      FactionRanker ranker = new FactionRanker();
      assertNull(ranker.visibleValue(viewer, hiddenAlpha, RankType.WEALTH));
      assertNull(ranker.getVisibleRank(viewer, hiddenAlpha, RankType.WEALTH));
      assertNull(
          ranker.getVisibleGuildRank(viewer, hiddenAlpha.getOrCreateMainGuild(), RankType.MEMBERS));
      assertEquals(
          List.of(open, home, hiddenAlpha, hiddenZulu),
          ranker.getVisibleRankedList(viewer, RankType.WEALTH));
      assertEquals(
          List.of(
              open.getOrCreateMainGuild(),
              home.getOrCreateMainGuild(),
              hiddenAlpha.getOrCreateMainGuild(),
              hiddenZulu.getOrCreateMainGuild()),
          ranker.getVisibleRankedGuildList(viewer, RankType.WEALTH));
      hiddenAlpha.setPrestige(4321.0);
      assertEquals(4321.0, ranker.visibleValue(viewer, hiddenAlpha, RankType.PRESTIGE));
      assertEquals(1.0, ranker.visibleValue(viewer, home, RankType.MEMBERS));
      assertEquals(2, ranker.getVisibleRank(viewer, home, RankType.WEALTH));
      when(viewer.hasPermission(EspionageConfig.bypassPermission())).thenReturn(true);
      assertEquals(1, ranker.getVisibleRank(viewer, hiddenZulu, RankType.WEALTH));
    }
  }

  @Test
  void dailyIntelligenceRanksTheStoredEstimateRatherThanTheLiveSecretBalance() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Faction home = fundedFaction(fixture, "home", "Leader", 100);
      Faction foreign = fundedFaction(fixture, "foreign", "ForeignLeader", 9000);
      guard(home, fixture.online.get("Leader"));
      guard(foreign, fixture.online.get("ForeignLeader"));
      IntelligenceReport report = new IntelligenceReport();
      report.quality = "detailed";
      report.estimates.put("Wealth", new EspionageMath.Estimate(180, 220));
      report.estimates.put(
          "Guild:" + foreign.getOrCreateMainGuild().getId() + ":Wealth",
          new EspionageMath.Estimate(40, 60));
      home.getEspionage()
          .report(
              foreign.getId(),
              foreign.getFoundedAt(),
              LocalDate.now(ZoneOffset.UTC).toEpochDay(),
              () -> report);
      FactionRanker ranker = new FactionRanker();
      Player viewer = fixture.online.get("Leader");
      assertEquals(200.0, ranker.visibleValue(viewer, foreign, RankType.WEALTH));
      assertEquals(
          50.0, ranker.visibleGuildValue(viewer, foreign.getOrCreateMainGuild(), RankType.WEALTH));
      assertEquals(1, ranker.getVisibleRank(viewer, foreign, RankType.WEALTH));
      assertEquals(
          2, ranker.getVisibleGuildRank(viewer, foreign.getOrCreateMainGuild(), RankType.WEALTH));
      foreign.getBank().deposit(5000.0);
      assertEquals(200.0, ranker.visibleValue(viewer, foreign, RankType.WEALTH));
      assertEquals(
          50.0, ranker.visibleGuildValue(viewer, foreign.getOrCreateMainGuild(), RankType.WEALTH));
    }
  }

  @Test
  void tiedVisibleRankingsUseCaseInsensitiveIdsAndDoNotReorderTheRegistry() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Faction zulu = fundedFaction(fixture, "zulu", "Zulu", 100);
      Faction bravo = fundedFaction(fixture, "bravo", "Bravo", 100);
      Faction alpha = fundedFaction(fixture, "Alpha", "Alpha", 100);
      Player viewer = fixture.online.get("Zulu");
      FactionRanker ranker = new FactionRanker();
      assertEquals(
          List.of(alpha, bravo, zulu), ranker.getVisibleRankedList(viewer, RankType.WEALTH));
      assertEquals(
          List.of(
              alpha.getOrCreateMainGuild(),
              bravo.getOrCreateMainGuild(),
              zulu.getOrCreateMainGuild()),
          ranker.getVisibleRankedGuildList(viewer, RankType.WEALTH));
      assertEquals(List.of(zulu, bravo, alpha), FactionManager.factions);
    }
  }

  @Test
  void bankRegistryFollowsExplicitChunkMovesWithoutChangingItsBalance() {
    List<Bank> oldBanks = BankManager.banks;
    BankManager.banks = new ArrayList<>();
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Faction faction = fundedFaction(fixture, "home", "Leader", 80);
      Bank bank = faction.getBank();
      BankManager.banks.add(bank);
      Chunk original = bank.getChunk();
      Chunk moved = mock(Chunk.class);
      BankManager manager = new BankManager();
      assertTrue(manager.hasBank(original));
      assertFalse(manager.hasBank(moved));
      bank.setChunk(moved);
      assertFalse(manager.hasBank(original));
      assertTrue(manager.hasBank(moved));
      assertEquals(80.0, bank.getWealth());
      Bank openingBalance = new Bank(faction.getOrCreateMainGuild(), 25.0, original);
      Bank empty = new Bank(faction.getOrCreateMainGuild(), original);
      assertEquals(25.0, openingBalance.getWealth());
      assertEquals(0.0, empty.getWealth());
      assertSame(original, openingBalance.getChunk());
      bank.withdraw(12.5);
      assertEquals(67.5, bank.getWealth());
      assertEquals(67.5, faction.getWealth());
      bank.setWealth(50.0);
      assertEquals(50.0, bank.getWealth());
    } finally {
      BankManager.banks = oldBanks;
    }
  }

  @Test
  void publicPlayerLedgerAccessUsesThePluginsOneSharedManager() throws Exception {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      PlayerEconomyManager manager = new PlayerEconomyManager();
      Field installedManager = SimpleFactions.class.getDeclaredField("playerEconomyManager");
      installedManager.setAccessible(true);
      Object original = installedManager.get(fixture.ui.plugin);
      try {
        installedManager.set(fixture.ui.plugin, manager);
        UUID player = UUID.randomUUID();
        PlayerEconomyManager.get().getLedger(player).add(PlayerCashflow.EARNINGS, 35.0);
        assertSame(manager, PlayerEconomyManager.get());
        assertEquals(
            35.0, SimpleFactions.getPlayerEconomyManager().getLedger(player).getNetDaily());
      } finally {
        installedManager.set(fixture.ui.plugin, original);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void bankPlacementCannotTreatAnotherWorldAsTheCapital(boolean guildBank) throws Exception {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      fixture.provinceData.put(10, new Province(10, "PLAINS", 50));
      var data = fixture.data("home", "Leader");
      data.provinces.add(10);
      data.capital = 10;
      Faction faction = fixture.saved(data);
      Guild guild = fixture.guild(faction, "merchants", "Merchant");
      guild.setCapital(10, false);
      Path file = temporary.resolve("grid.gz");
      try (var zipped = new GZIPOutputStream(Files.newOutputStream(file))) {
        zipped.write(
            ByteBuffer.allocate(10)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1)
                .putInt(1)
                .putShort((short) 10)
                .array());
      }
      when(fixture.ui.plugin.getProvinceGrid()).thenReturn(ProvinceGrid.load(file.toFile()));
      Location capital = new Location(fixture.ui.world, 0, 64, 0);
      World otherWorld = mock(World.class);
      when(otherWorld.getName()).thenReturn("the_nether");
      Location unrelated = new Location(otherWorld, 0, 64, 0);
      assertNull(
          guildBank
              ? BankPlacementValidator.failureReasonForGuild(guild, capital)
              : BankPlacementValidator.failureReasonForFaction(faction, capital));
      assertNotNull(
          guildBank
              ? BankPlacementValidator.failureReasonForGuild(guild, unrelated)
              : BankPlacementValidator.failureReasonForFaction(faction, unrelated));
    }
  }

  @Test
  void legacyZeroCapitalDoesNotImposeAnUnresolvableBankRestriction() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var data = fixture.data("legacy", "Leader");
      data.capital = 0;
      Faction faction = fixture.saved(data);
      Guild guild = fixture.guild(faction, "merchant", "Merchant");
      guild.setCapital(0, false);
      assertNull(BankPlacementValidator.failureReasonForFaction(faction, 99));
      assertNull(BankPlacementValidator.failureReasonForGuild(guild, 99));
      assertNull(BankPlacementValidator.failureReasonForFaction(null, 99));
      assertNull(BankPlacementValidator.failureReasonForGuild(null, 99));
    }
  }

  @Test
  void playerLedgersResolveAccountsWithoutSharingAnonymousEntries() {
    UUID alice = UUID.randomUUID();
    PlayerEconomyManager manager = new PlayerEconomyManager();
    try (var accounts = mockStatic(OfflineModifier.class)) {
      accounts.when(() -> OfflineModifier.playerId("Alice")).thenReturn(alice);
      manager.getLedger("Alice").add(PlayerCashflow.EARNINGS, 50);
      manager.getLedger(alice).add(PlayerCashflow.CITIZEN_TAX, -10);
      assertSame(manager.getLedger(alice), manager.getLedger("Alice"));
      assertEquals(40.0, manager.getLedger(alice).getNetDaily());
      manager.getLedger((UUID) null).add(PlayerCashflow.EARNINGS, 999);
      manager.getLedger("Unknown").add(PlayerCashflow.EARNINGS, 888);
      assertEquals(0.0, manager.getLedger((UUID) null).getNetDaily());
      assertEquals(0.0, manager.getLedger("Unknown").getNetDaily());
      assertEquals(40.0, manager.getLedger("Alice").getNetDaily());
      var previous = manager.getLedger(alice);
      manager.clearAllDaily();
      assertEquals(0.0, previous.getNetDaily());
      assertEquals(0.0, manager.getLedger(alice).getNetDaily());
      assertNotSame(previous, manager.getLedger(alice));
    }
  }

  @Test
  void denarAdapterConservesSeparateBankAndPouchBalances() {
    UUID account = UUID.randomUUID();
    Map<Accounts, Double> balances =
        new HashMap<>(Map.of(Accounts.BANK, 100.0, Accounts.POUCH, 40.0));
    try (var external = mockStatic(OfflineModifier.class)) {
      external.when(() -> OfflineModifier.playerId("Alice")).thenReturn(account);
      external
          .when(() -> OfflineModifier.balance(eq(account), any(Accounts.class)))
          .thenAnswer(call -> balances.get(call.getArgument(1)));
      external
          .when(() -> OfflineModifier.apply(eq(account), any(Accounts.class), anyDouble()))
          .thenAnswer(
              call -> {
                Accounts type = call.getArgument(1);
                double next = balances.get(type) + (double) call.getArgument(2);
                if (next < 0) return false;
                balances.put(type, next);
                return true;
              });
      var bank = DenarEconomyPlayerBank.INSTANCE;
      assertEquals(account, bank.resolve("Alice"));
      assertEquals(100.0, bank.getBankBalance(account));
      assertEquals(40.0, bank.getPouchBalance(account));
      assertTrue(bank.withdrawFromBank(account, 30));
      assertTrue(bank.depositToBank(account, 5));
      assertTrue(bank.withdrawFromPouch(account, 8));
      assertEquals(75.0, bank.getBankBalance(account));
      assertEquals(32.0, bank.getPouchBalance(account));
      assertFalse(bank.withdrawFromBank(account, 100));
      assertFalse(bank.withdrawFromBank(account, 0));
      assertFalse(bank.withdrawFromPouch(account, -1));
      assertFalse(bank.depositToBank(account, -10));
      assertEquals(Map.of(Accounts.BANK, 75.0, Accounts.POUCH, 32.0), balances);
    }
  }

  private Faction fundedFaction(
      FactionDomainFixture fixture, String id, String leader, double balance) {
    fixture.player(leader);
    Faction faction = fixture.saved(id, leader);
    faction.getBank().deposit(balance);
    return faction;
  }

  private void guard(Faction faction, Player leader) {
    SpecialPositionAssignment assignment = new SpecialPositionAssignment();
    assignment.playerId = leader.getUniqueId();
    assignment.playerName = leader.getName();
    assignment.characterId = faction.getId() + "-character";
    assignment.automatic = true;
    faction.getEspionage().assignFounder(SpecialPosition.SPYMASTER, assignment, 75);
  }
}
