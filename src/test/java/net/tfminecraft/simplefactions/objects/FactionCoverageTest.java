package net.tfminecraft.simplefactions.objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.GovernmentData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.MovementData;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.enums.Brackets;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Member;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.espionage.EspionageState;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.BranchModifier;
import net.tfminecraft.simplefactions.loaders.RankLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.TestRegistryAccess;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.utils.RandomRGB;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsObligation;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class FactionCoverageTest {
  private FactionDomainFixture fixture;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
  }

  @AfterEach
  void cleanup() {
    if (fixture != null) fixture.close();
  }

  @Test
  void savedFactionRetainsIdentityTaxRatesAndRealDomainHandlersAfterJsonRoundTrip() {
    FactionData data = fixture.data("home", "Alice");
    Faction faction =
        fixture.saved(JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(data), FactionData.class));
    assertEquals("home", faction.getId());
    assertEquals("home", faction.getName());
    assertEquals("Alice", faction.getLeader());
    assertEquals("Governor", faction.getRulerTitle());
    assertEquals("Community", faction.getGovernmentString());
    assertEquals("Riverfolk", faction.getCulture());
    assertEquals("Old Faith", faction.getReligion());
    assertEquals("1,2,3", faction.getRGB());
    assertEquals(List.of("Alice"), faction.getMembers());
    assertTrue(faction.getOrCreateMainGuild().isBase());
    assertSame(faction, faction.getOrCreateMainGuild().getFaction());
    assertEquals(Material.WHITE_BANNER, faction.getBanner().getType());
    assertEquals(List.of("white"), faction.getBannerPatterns());
    assertEquals(5, faction.getTaxRate());
    assertEquals(20, faction.getVassalTaxRate());
    assertEquals(10, faction.getTaxRate(TaxTarget.GUILDS, null, false));
    assertNotNull(faction.getGovernment());
    assertNotNull(faction.getMilitary());
    assertNotNull(faction.getInstallationHandler());
    assertNotNull(faction.getSettlementHandler());
    assertEquals("landless", faction.getTier().getId());
    assertEquals("common", faction.getRank().getId());
    assertTrue(faction.getPrestige() > 0);
    assertEquals(0, faction.getWealth());
  }

  @Test
  void factionInvitationsAreCaseInsensitiveAndCanBeConsumedExactlyOnce() {
    Faction faction = fixture.saved("home", "Alice");
    faction.invite(null);
    faction.invite("Bob");
    faction.invite("BOB");
    assertEquals(List.of("Bob"), faction.getInvited());
    assertTrue(faction.isInvited("bob"));
    assertFalse(faction.consumeInvite("Cara"));
    assertTrue(faction.consumeInvite("bOB"));
    assertFalse(faction.consumeInvite("Bob"));
    assertTrue(faction.getInvited().isEmpty());
    faction.setInvited(new ArrayList<>(List.of("Cara")));
    assertTrue(faction.isInvited("cara"));
    faction.addMember("Bob");
    assertTrue(faction.isMemberIgnoreCase("bob"));
    assertFalse(faction.isMemberIgnoreCase("missing"));
  }

  @Test
  void newFactionSelectsAnUnusedColorAndOnlyAcceptsTheBannerWhileItsPlaceholderIsCurrent() {
    try (MockedStatic<RandomRGB> rgb = mockStatic(RandomRGB.class)) {
      rgb.when(RandomRGB::random).thenReturn("1,1,1", "2,3,4");
      rgb.when(() -> RandomRGB.isFree("2,3,4")).thenReturn(true);
      rgb.when(() -> RandomRGB.similarButDistinct(anyString())).thenReturn("5,6,7");
      rgb.when(() -> RandomRGB.isFree("5,6,7")).thenReturn(true);
      Faction faction = new Faction("River_Town", "Alice");
      assertEquals("River_Town", faction.getId());
      assertTrue(faction.getName().endsWith("River Town"));
      assertEquals("2,3,4", faction.getRGB());
      assertEquals("Leader", faction.getRulerTitle());
      assertEquals("Multicultural", faction.getCulture());
      assertEquals("Religious Diversity", faction.getReligion());
      assertTrue(faction.getFoundedAt() <= System.currentTimeMillis() / 1000);
      assertTrue(faction.getFoundedAt() > System.currentTimeMillis() / 1000 - 10);
      assertEquals(1, fixture.bannerCallbacks.size());
      fixture.bannerCallbacks.getFirst().accept(null);
      assertEquals(Material.WHITE_BANNER, faction.getBanner().getType());
      fixture.bannerCallbacks.getFirst().accept(new ArrayList<>(List.of("blue")));
      assertEquals(Material.BLUE_BANNER, faction.getBanner().getType());
      faction.setBannerPatterns(new ArrayList<>(List.of("red")));
      fixture.bannerCallbacks.getFirst().accept(List.of("green"));
      assertEquals(Material.RED_BANNER, faction.getBanner().getType());
      assertEquals(List.of("Alice"), faction.getMembers());
    }
  }

  @Test
  void promotingAGuildToAFactionAppliesItsInitialTaxLawLikeEveryOtherFactionConstructor() {
    fixture.lawGroup("finance", Map.of("effects.faction.brackets.CITIZEN_TAX", "10-20"));
    Faction host = fixture.saved("host", "Alice");
    assertEquals(10, host.getTaxRate());
    Guild guild = fixture.guild(host, "merchants", "Bob");
    guild.rememberLeaderCharacter("Borin", "Bob");
    host.getGuildHandler().removeGuild("merchants", false, false);
    try (MockedStatic<RandomRGB> rgb = mockStatic(RandomRGB.class)) {
      rgb.when(() -> RandomRGB.isFree(anyString())).thenReturn(true);
      Faction promoted = new Faction(guild);
      assertEquals(10, promoted.getTaxRate());
      assertEquals("merchants", promoted.getId());
      assertEquals("merchants", promoted.getName());
      assertEquals("Bob", promoted.getLeader());
      assertEquals("Borin", promoted.getLeaderCharacter());
      assertEquals("Bob", promoted.getLeaderCharacterOf());
      assertEquals(host.getCulture(), promoted.getCulture());
      assertEquals(host.getReligion(), promoted.getReligion());
      assertSame(promoted, guild.getFaction());
      assertTrue(guild.isBase());
      assertSame(guild, promoted.getOrCreateMainGuild());
    }
  }

  @Test
  void persistedSpecificTaxesAndGovernmentPowerSurviveInvalidSiblingTaxCategories() {
    FactionData data = fixture.data("home", "Alice");
    data.specificTaxes.put("GUILDS", new HashMap<>(Map.of("merchants", 22.5)));
    data.specificTaxes.put("obsolete-category", new HashMap<>(Map.of("ignored", 99.0)));
    data.specificTaxes.put("TARIFFS", null);
    data.governmentData = new GovernmentData();
    data.governmentData.power = 42.0;
    data.prestigeModifiers.add("Foundation(10.0)");
    Faction faction = fixture.saved(data);
    assertEquals(22.5, faction.getTaxRate(TaxTarget.GUILD_ID, "merchants", false));
    assertEquals(10, faction.getTaxRate(TaxTarget.GUILD_ID, "other", false));
    assertEquals(30, faction.getTaxRate(TaxTarget.TARIFF_ID, "other", false));
    assertEquals(42, faction.getGovernment().getPower());
    assertTrue(
        faction.getPrestigeModifiers().stream()
            .anyMatch(m -> m.getType().equals("Foundation") && m.getAmount() == 10));
  }

  @Test
  void baseGuildAndOrdinaryGuildMembershipsDetermineLeadershipVotingAndRealmRoles() {
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Cara");
    Guild artisans = fixture.guild(faction, "artisans", "Bob");
    artisans.addMember("Dana");
    assertEquals(List.of("Alice", "Bob", "Cara", "Dana"), faction.getMembers());
    assertEquals(Member.LEADER, faction.getRelationToFaction("Alice"));
    assertEquals(Member.MEMBER, faction.getRelationToFaction("Cara"));
    assertEquals(Member.GUILD_LEADER, faction.getRelationToFaction("Bob"));
    assertEquals(Member.GUILD_MEMBER, faction.getRelationToFaction("Dana"));
    assertEquals(Member.FOREIGNER, faction.getRelationToFaction("Visitor"));
    assertFalse(faction.isInGuild("Visitor"));
    assertFalse(faction.isInGuild("Cara"));
    assertTrue(faction.isInGuild("Dana"));
    assertFalse(faction.canBecomeLeader("Alice"));
    assertFalse(faction.canBecomeLeader("Visitor"));
    assertFalse(faction.canBecomeLeader("Bob"));
    assertTrue(faction.canBecomeLeader("Dana"));
    assertFalse(faction.canBeCleanKicked("Alice"));
    assertFalse(faction.canBeCleanKicked("Bob"));
    assertTrue(faction.canBeCleanKicked("Dana"));
    assertTrue(faction.canVote("Cara"));
    assertFalse(faction.canVote("Visitor"));
    assertTrue(faction.canRemainLeader("Cara"));
    assertFalse(faction.canRemainLeader("Visitor"));
    assertSame(artisans, faction.getGuild("Dana"));
    faction.promoteToLeader("Visitor");
    assertEquals("Alice", faction.getLeader());
    faction.promoteToLeader("Dana");
    assertEquals("Dana", faction.getLeader());
    assertTrue(faction.getOrCreateMainGuild().isMember("Dana"));
    assertFalse(artisans.isMember("Dana"));
    assertEquals(4, faction.getMembers().size());
    faction.forceRemoveMember("Cara");
    assertFalse(faction.isMember("Cara"));
  }

  @Test
  void realmMemberQueriesAndVassalVotingUseTheLiveRelationGraph() {
    fixture.lawGroup(
        "franchise", Map.of("effects.faction.rules", List.of("VASSAL_VOTING_RIGHTS true")));
    Faction root = fixture.saved("root", "Alice");
    Faction child = fixture.saved("child", "Bob");
    child.addMember("Cara");
    Faction grandchild = fixture.saved("grandchild", "Dana");
    fixture.subject(root, child);
    fixture.subject(child, grandchild);
    assertSame(root, child.getOverlord());
    assertNull(root.getOverlord());
    assertEquals(List.of(child), root.getSubjects());
    assertEquals(List.of(child), root.getVassals());
    assertTrue(root.hasVassals());
    assertEquals(List.of("Bob", "Cara"), root.getVassalMembers());
    assertEquals(Set.of("Alice", "Bob", "Cara", "Dana"), Set.copyOf(root.getCompleteMemberList()));
    assertEquals(Member.VASSAL_LEADER, root.getRelationToFaction("Bob"));
    assertEquals(Member.VASSAL_MEMBER, root.getRelationToFaction("Cara"));
    assertTrue(root.canVote("Cara"));
    assertFalse(root.canVote("Dana"));
    assertTrue(child.canDissolve());
    assertTrue(root.canDissolve());
    assertFalse(grandchild.hasVassals());
    child.setWealth(125.25);
    assertEquals(125.25, root.getVassalWealth());
    assertEquals(child.getGuildHandler().getTotalTradePower(), root.getVassalTradePower());
    assertEquals(
        root.getGuildHandler().getTotalTradePower() + root.getVassalTradePower(),
        root.getTotalTradePower());
    root.refreshLawSlots();
    assertSame(root.getRelation("child"), root.getRelations().get("child"));
    root.updateRelations();
  }

  @Test
  void applyingLocalRulesOverridesInheritedVassalRulesAndExplicitFalseWins() {
    fixture.lawGroup(
        "constitution",
        Map.of(
            "effects.vassals.rules",
            List.of("CAN_HAVE_VASSALS false"),
            "effects.faction.rules",
            List.of("CAN_FAVOUR true")));
    Faction root = fixture.saved("root", "Alice");
    Faction child = fixture.saved("child", "Bob");
    fixture.subject(root, child);
    assertFalse(child.canHaveVassals());
    assertTrue(child.hasFactionRule(Rules.CAN_FAVOUR));
    assertNull(child.getExplicitRule(Scope.DOMESTIC_GUILDS, Rules.CAN_FAVOUR));
    assertEquals(Boolean.FALSE, root.getExplicitRule(Scope.VASSALS, Rules.CAN_HAVE_VASSALS));
    var group = child.getLawHandler().getGroup("constitution");
    var effect = group.getCurrent().getScopedEffects().get(Scope.FACTION);
    effect.getRules().put(Rules.CAN_HAVE_VASSALS, true);
    assertTrue(child.canHaveVassals());
    fixture.lawGroup("restriction", Map.of("effects.faction.rules", List.of("CAN_FAVOUR false")));
    Faction restricted = fixture.saved("restricted", "Cara");
    assertFalse(restricted.hasFactionRule(Rules.CAN_FAVOUR));
    assertEquals(Boolean.FALSE, restricted.getExplicitRule(Scope.FACTION, Rules.CAN_FAVOUR));
  }

  @Test
  void bannerPatternsRoundTripAcrossVanillaAndCustomRegistriesAndSkipInvalidDecorations() {
    var bootstrapPattern = org.bukkit.block.banner.PatternType.STRIPE_TOP;
    assertNotNull(bootstrapPattern);
    var vanilla = TestRegistryAccess.registerPattern(NamespacedKey.minecraft("stripe_top"));
    var custom = TestRegistryAccess.registerPattern(new NamespacedKey("tfmc", "test_pattern"));
    Faction faction = fixture.saved("home", "Alice");
    faction.setBannerPatterns(
        new ArrayList<>(
            List.of(
                "blue",
                "badformat",
                "notacolor.stripe_top",
                "red.nonexistent",
                "white.stripe_top",
                "black.test_pattern")));
    BannerMeta meta = (BannerMeta) faction.getBanner().getItemMeta();
    assertEquals(Material.BLUE_BANNER, faction.getBanner().getType());
    assertEquals(
        List.of(new Pattern(DyeColor.WHITE, vanilla), new Pattern(DyeColor.BLACK, custom)),
        meta.getPatterns());
    ItemStack replacement = new ItemStack(Material.RED_BANNER);
    BannerMeta next = (BannerMeta) replacement.getItemMeta();
    next.addPattern(new Pattern(DyeColor.BLUE, vanilla));
    replacement.setItemMeta(next);
    faction.setBanner(replacement);
    assertEquals(List.of("RED.BASE", "BLUE.STRIPE_TOP"), faction.getBannerPatterns());
    assertEquals(Material.RED_BANNER, faction.getBanner().getType());
  }

  @Test
  void provincesCapitalAndTitlesDriveTheFactionTierWithoutDuplicatingTitles() {
    Faction faction = fixture.saved("home", "Alice");
    assertFalse(faction.hasCapital());
    assertEquals(-1, faction.getCapital());
    faction.addProvince(7);
    faction.addProvince(7);
    faction.addProvince(8);
    assertEquals(List.of(7, 8), faction.getProvinces());
    assertTrue(faction.hasProvince(7));
    assertTrue(faction.ownsProvince(8));
    assertEquals("province", faction.getTier().getId());
    faction.setCapital(99);
    assertFalse(faction.hasCapital());
    faction.setCapital(7);
    assertEquals(7, faction.getCapital());
    faction.setCapital(8, true, false);
    assertEquals(8, faction.getCapital());
    Title county = fixture.title("county-a", "county", 7, 8);
    Title duchy = fixture.title("duchy-a", "duchy", 7, 8);
    faction.addTitle(county);
    faction.addTitle(county);
    faction.addTitle(duchy);
    assertEquals(List.of(county, duchy), faction.getTitles());
    assertEquals(List.of(duchy, county), faction.getRankedTitles());
    assertSame(duchy, faction.getHighestTitle());
    assertEquals("duchy", faction.getTier().getId());
    assertEquals(List.of(county), faction.getTitles(TierLoader.getByString("county")));
    faction.resetTitles(java.util.Arrays.asList(county, county, null, duchy));
    assertEquals(2, faction.getTitles().size());
    faction.stripTitle(duchy);
    faction.stripTitle(duchy);
    assertEquals("county", faction.getTier().getId());
    faction.removeTitle(county);
    faction.removeTitle(county);
    assertNull(faction.getHighestTitle());
    assertEquals("province", faction.getTier().getId());
    faction.removeProvince(8, false);
    faction.removeProvince(7, false);
    assertEquals("landless", faction.getTier().getId());
    verify(fixture.map, atLeastOnce()).enqueue("nation", "1,2,3");
  }

  @Test
  void persistentPrestigeModifiersAccumulateReplaceAndRemoveAsSourcesChange() {
    Faction faction = fixture.saved("home", "Alice");
    faction.setPrestigeModifiers(new ArrayList<>());
    faction.addPersistentPrestigeModifier(new Modifier("Gift", 3.0, true));
    faction.addPersistentPrestigeModifier(new Modifier("GIFT", 4.0, true));
    assertEquals(7, faction.getPrestigeModifiers().getFirst().getAmount());
    faction.addPersistentPrestigeModifier(new Modifier("gift", -7.0, true));
    faction.addPersistentPrestigeModifier(new Modifier("zero", 0.0, true));
    assertTrue(faction.getPrestigeModifiers().isEmpty());
    faction.setPersistentPrestigeModifier("Foundation", 10);
    faction.setPersistentPrestigeModifier("FOUNDATION", 20);
    assertEquals(1, faction.getPrestigeModifiers().size());
    assertEquals(20, faction.getPrestigeModifiers().getFirst().getAmount());
    faction.setPersistentPrestigeModifier("foundation", 0);
    faction.setPersistentPrestigeModifier("absent", 0);
    assertTrue(faction.getPrestigeModifiers().isEmpty());
    faction.updatePrestige();
    assertTrue(faction.getPrestige() > 0);
  }

  @Test
  void guildBankMutationsRecalculateFactionWealthAndReportItsActualSources() {
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = fixture.guild(faction, "artisans", "Bob");
    assertFalse(
        faction.getWealthModifiers().stream().anyMatch(m -> m.getType().contains("artisans")));
    guild.getBank().deposit(30.5);
    faction.getBank().deposit(20.0);
    assertEquals(50.5, faction.getWealth());
    assertTrue(
        faction.getWealthModifiers().stream()
            .anyMatch(m -> m.getType().contains("artisans") && m.getAmount() == 30.5));
    faction.giveTax("Cara", 4.25);
    assertEquals(
        Map.of("Cara", 4.25), faction.getOrCreateMainGuild().getLedger().getCitizenTaxesCopy());
    Bank replacement =
        new Bank(faction.getOrCreateMainGuild(), 5.0, fixture.ui.world.getChunkAt(0, 0));
    faction.setBank(replacement);
    assertSame(replacement, faction.getBank());
    faction.getOrCreateMainGuild().updateWealth();
    assertEquals(35.5, faction.getWealth());
  }

  @Test
  void editedFactionIdentityAndGovernmentAreWrittenAsASingleCompletePersistenceSnapshot()
      throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    faction.setName("River Union");
    faction.setRGB("11,22,33");
    faction.setRulerTitle("Chancellor");
    faction.setGovernment("Republic");
    faction.setCulture("Riverfolk and Coastfolk");
    faction.setReligion("Pluralist");
    faction.setFoundedAt(123456789L);
    faction.setCapitalMoves(3);
    faction.setExtraNodeCapacity(4);
    faction.rememberLeaderCharacter("Alys Rivers", "Alice");
    faction.getGovernment().setPower(12.5);
    faction.setPersistentPrestigeModifier("Charter", 17);
    faction.addWarReparationsObligation(null);
    faction.addWarReparationsObligation(new WarReparationsObligation("neighbor", 12.5, 3));
    List<FactionData> saved = new ArrayList<>();
    try (MockedStatic<JsonUtil> json = mockStatic(JsonUtil.class)) {
      json.when(() -> JsonUtil.writeJsonAtomic(any(java.io.File.class), any()))
          .thenAnswer(
              call -> {
                saved.add(call.getArgument(1));
                return null;
              });
      assertTrue(new Database().saveFactionChecked(faction));
    }
    assertEquals(1, saved.size());
    FactionData persisted =
        JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(saved.getFirst()), FactionData.class);
    assertEquals("home", persisted.id);
    assertEquals("River Union", persisted.name);
    assertEquals("11,22,33", persisted.rgb);
    assertEquals("Chancellor", persisted.rulerTitle);
    assertEquals("Republic", persisted.government);
    assertEquals("Riverfolk and Coastfolk", persisted.culture);
    assertEquals("Pluralist", persisted.religion);
    assertEquals(123456789L, persisted.foundedAt);
    assertEquals(3, persisted.capitalMoves);
    assertEquals(4, persisted.extraNodeCapacity);
    assertEquals("Alys Rivers", persisted.leaderCharacter);
    assertEquals("Alice", persisted.leaderCharacterOf);
    assertEquals(12.5, persisted.governmentData.power);
    assertTrue(persisted.prestigeModifiers.contains("Charter(17.0)"));
    assertEquals(1, persisted.warReparationsObligations.size());
    assertEquals("neighbor", persisted.warReparationsObligations.getFirst().payeeFactionId);
    assertSame(faction.getEspionage(), saved.getFirst().espionage);
    assertTrue(faction.canPurchaseCapacity());
    faction.setExtraNodeCapacity(10);
    assertFalse(faction.canPurchaseCapacity());
  }

  @Test
  void changingARegisteredFactionIdUpdatesItsIdentityWithoutReplacingItsDomainHandlers() {
    Faction faction = fixture.saved("home", "Alice");
    var laws = faction.getLawHandler();
    faction.setId("river_union");
    assertEquals("river_union", faction.getId());
    assertSame(faction, FactionManager.getByString("river_union"));
    assertNull(FactionManager.getByString("home"));
    assertSame(laws, faction.getLawHandler());
  }

  @Test
  void lawChangesClampEveryTaxAndVehicleFeeThenDisabledChargesBecomeZero() {
    Map<String, Object> options = new HashMap<>();
    options.put(
        "effects.faction.rules",
        List.of(
            "CITIZEN_TAX true",
            "GUILD_TAX true",
            "VASSAL_TAX true",
            "DIVIDEND_TAX true",
            "TARIFFS true",
            "VEHICLE_TAX true",
            "REGISTRATION_FEE true",
            "TRANSFER_FEE true"));
    for (var bracket : Brackets.values()) {
      options.put("effects.faction.brackets." + bracket.name(), "10-40");
    }
    fixture.lawGroup("finance", options);
    Faction faction = fixture.saved("home", "Alice");
    assertEquals(10, faction.getTaxRate());
    assertEquals(15, faction.getTaxRate(TaxTarget.DIVIDENDS, null, false));
    assertEquals(20, faction.getVassalTaxRate());
    assertEquals(30, faction.getTaxRate(TaxTarget.TARIFFS, null, false));
    for (var fee : FeeKind.values()) {
      assertEquals(10, faction.getVehicleFeeHandler().getRate(fee));
      assertTrue(faction.getVehicleFeeHandler().canCharge(fee));
      faction.getVehicleFeeHandler().setRate(fee, "wagon", 35);
    }
    faction.getTaxHandler().setTaxRate(TaxTarget.GUILD_ID, "artisans", 30);
    var group = faction.getLawHandler().getGroup("finance");
    var disabled =
        fixture.law(
            "finance",
            "disabled",
            Map.of(
                "effects.faction.rules",
                List.of(
                    "CITIZEN_TAX false",
                    "GUILD_TAX false",
                    "VASSAL_TAX false",
                    "DIVIDEND_TAX false",
                    "TARIFFS false",
                    "VEHICLE_TAX false",
                    "REGISTRATION_FEE false",
                    "TRANSFER_FEE false")));
    faction.applyLaw(disabled, group);
    assertSame(disabled, group.getCurrent());
    for (var target :
        List.of(
            TaxTarget.CITIZENS,
            TaxTarget.GUILDS,
            TaxTarget.VASSALS,
            TaxTarget.DIVIDENDS,
            TaxTarget.TARIFFS)) {
      assertEquals(0, faction.getTaxRate(target, null, false));
    }
    assertEquals(0, faction.getTaxRate(TaxTarget.GUILDS, "artisans", false));
    for (var fee : FeeKind.values()) {
      assertEquals(0, faction.getVehicleFeeHandler().getRate(fee, "wagon"));
      assertFalse(faction.getVehicleFeeHandler().canCharge(fee));
    }
  }

  @Test
  void councilLawChoosesSizeTypeAndCancelsElectionsWhenRightsAreRemoved() {
    fixture.lawGroup(
        "government",
        Map.of(
            "effects.faction.rules",
            List.of("HAS_COUNCIL true", "ELECTED_COUNCIL true", "LEADER_ELECTIONS true"),
            "effects.faction.council-size",
            2));
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    assertEquals(2, faction.getCouncilSize());
    assertEquals(Rules.ELECTED_COUNCIL, faction.getCouncilType());
    var group = faction.getLawHandler().getGroup("government");
    assertTrue(Faction.enablesElections(group.getCurrent().getScopedEffects().get(Scope.FACTION)));
    assertFalse(Faction.enablesElections(null));
    faction.getGovernment().getElection().start();
    faction.ping();
    assertTrue(faction.getGovernment().getElection().isActive());
    var autocracy =
        fixture.law(
            "government",
            "autocracy",
            Map.of(
                "effects.faction.rules",
                List.of("HAS_COUNCIL false", "LEADER_ELECTIONS false", "ELECTED_COUNCIL false")));
    faction.applyLaw(autocracy, group);
    assertEquals(0, faction.getCouncilSize());
    assertEquals(Rules.NO_COUNCIL, faction.getCouncilType());
    assertFalse(faction.getGovernment().getElection().isActive());
    assertFalse(Faction.enablesElections(autocracy.getScopedEffects().get(Scope.FACTION)));
  }

  @Test
  void councilWithoutConfiguredSizeUsesFourSeatsAndUnspecifiedTypeUsesNoCouncil() {
    fixture.lawGroup("foreign", Map.of("effects.vassals.rules", List.of("CAN_HAVE_VASSALS true")));
    fixture.lawGroup("government", Map.of("effects.faction.rules", List.of("HAS_COUNCIL true")));
    Faction faction = fixture.saved("home", "Alice");
    assertEquals(4, faction.getCouncilSize());
    assertEquals(Rules.NO_COUNCIL, faction.getCouncilType());
    var noFactionEffect =
        fixture.law(
            "government",
            "foreignonly",
            Map.of("effects.vassals.rules", List.of("CAN_HAVE_VASSALS false")));
    faction.applyLaw(noFactionEffect, faction.getLawHandler().getGroup("government"));
    assertSame(noFactionEffect, faction.getLawHandler().getGroup("government").getCurrent());
    assertFalse(faction.getGovernment().getElection().isActive());
  }

  @Test
  void outlawingVassalageFreesEveryExistingSubject() {
    fixture.lawGroup(
        "authority", Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS true")));
    Faction ruler = fixture.saved("ruler", "Alice");
    Faction child = fixture.saved("child", "Bob");
    fixture.subject(ruler, child);
    assertSame(ruler, child.getOverlord());
    var banned =
        fixture.law(
            "authority",
            "banned",
            Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS false")));
    ruler.applyLaw(banned, ruler.getLawHandler().getGroup("authority"));
    assertNull(child.getOverlord());
    assertTrue(ruler.getVassals().isEmpty());
    assertFalse(ruler.canHaveVassals());
  }

  @Test
  void dailyArmyUpkeepShrinksPaidSlotsOnlyUntilAffordableAndPreservesFreeSlots() {
    fixture.regiment("reserve", false, 0, 0);
    fixture.regiment("guard", false, 1, 10);
    fixture.regiment("levy", true, 0, 0);
    Faction faction = fixture.saved("home", "Alice");
    var guard = faction.getMilitary().getRegiment("guard");
    guard.setFreeSlots(1);
    guard.setCurrentSlots(4);
    faction.getBank().deposit(25.0);
    assertEquals(30, faction.getMilitary().getTotalUpkeep());
    faction.newDay();
    assertEquals(3, guard.getCurrentSlots());
    assertEquals(0, faction.getMilitary().getRegiment("reserve").getCurrentSlots());
    assertEquals(1, guard.getFreeSlots());
    assertEquals(5, faction.getBank().getWealth());
    faction.newDay();
    assertEquals(1, guard.getCurrentSlots());
    assertEquals(5, faction.getBank().getWealth());
    faction.newDay();
    assertEquals(1, guard.getCurrentSlots());
    assertEquals(5, faction.getBank().getWealth());
  }

  @Test
  void missingBankRemovesOnlyPaidRegimentSlotsAndStillAllowsDailyHousekeeping() {
    fixture.regiment("guard", false, 0, 10);
    fixture.regiment("levy", true, 0, 0);
    Faction faction = fixture.saved("home", "Alice");
    var guard = faction.getMilitary().getRegiment("guard");
    var levy = faction.getMilitary().getRegiment("levy");
    guard.setFreeSlots(2);
    guard.setCurrentSlots(4);
    levy.setCurrentSlots(5);
    faction.setBank(null);
    faction.newDay();
    assertEquals(2, guard.getCurrentSlots());
    assertEquals(5, levy.getCurrentSlots());
    assertNull(faction.getBank());
  }

  @Test
  void tickCompletesRealRegimentConstructionAndLeavesAnEmptyQueue() {
    fixture.regiment("guard", false, 0, 10);
    Faction faction = fixture.saved("home", "Alice");
    var guard = faction.getMilitary().getRegiment("guard");
    faction.getMilitary().addQueueItem(guard, 1);
    fixture.provincesEnabled(true);
    faction.tick();
    assertEquals(1, guard.getCurrentSlots());
    assertTrue(faction.getMilitary().getQueue().isEmpty());
    assertEquals(5, faction.getTaxRate());
  }

  @Test
  void onlineCountProsperityAndCapacityUseTheCurrentRosterAndTerritory() {
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    faction.addMember("Cara");
    fixture.player("Alice");
    var offline = fixture.player("Bob");
    when(offline.isOnline()).thenReturn(false);
    fixture.player("Visitor");
    assertEquals(1, faction.numOnline());
    var north = new Province(7, "PLAINS", 1);
    var south = new Province(8, "PLAINS", 1);
    north.setProsperity(12.345);
    south.setProsperity(20.0);
    fixture.provinceData.put(7, north);
    fixture.provinceData.put(8, south);
    faction.addProvince(7);
    faction.addProvince(8);
    assertEquals(32.35, faction.getProsperity());
    assertEquals(List.of(7, 8), faction.getUntitledProvinces());
    faction.provinceCap();
    assertEquals(List.of(7, 8), faction.getProvinces());
  }

  @Test
  void taxMultipliersUseCurrentOverlordAndTradeRegionsAndCombinedModifiersSumTheirSources() {
    fixture.lawGroup(
        "finance",
        Map.of(
            "effects.faction.modifiers",
            List.of("TAX_MULTIPLIER(50)", "TRADE_POWER(10)"),
            "effects.faction.OUR_TERRITORY",
            List.of("TRADE_POWER(15)"),
            "effects.vassals.modifiers",
            List.of("TRADE_POWER(20)")));
    Faction ruler = fixture.saved("ruler", "Alice");
    Faction child = fixture.saved("child", "Bob");
    assertEquals(0, child.getOverlordTaxRate(child));
    fixture.subject(ruler, child);
    ruler.getTaxHandler().setTaxRate(TaxTarget.VASSAL_ID, "child", 40);
    assertEquals(0.6, child.getOverlordTaxRate(child), 0.0001);
    assertEquals(
        1.25,
        child.getModifier(FactionModifiers.TRADE_POWER, null, Scope.FACTION, Region.OUR_TERRITORY));
    assertEquals(1.0, child.getModifier(FactionModifiers.LEVY, null, Scope.FACTION, null));
    var combined = child.getCombinedModifiers();
    assertEquals(
        30,
        combined.stream()
            .filter(m -> m.getType() == FactionModifiers.TRADE_POWER)
            .findFirst()
            .orElseThrow()
            .getAmount());
    assertEquals(
        1, combined.stream().filter(m -> m.getType() == FactionModifiers.TRADE_POWER).count());
    assertEquals(50, child.getModifier(FactionModifiers.TAX_MULTIPLIER).getAmount());
  }

  @Test
  void foreignTributeIncludesOnlyExistingRecipientsWithBanksAndCapsCitizenTaxesAtTick() {
    fixture.lawGroup("local", Map.of("effects.faction.modifiers", List.of("TRIBUTE(80)")));
    var tributary =
        fixture.relationType(
            "tributary", Map.of("recieve-modifiers", List.of("TRIBUTE(30)", "TRADE_POWER(10)")));
    Faction payer = fixture.saved("payer", "Alice");
    Faction recipient = fixture.saved("recipient", "Bob");
    Faction bankless = fixture.saved("bankless", "Cara");
    payer.setRelation(recipient, new Relation(tributary, RelationLoader.getDefaultAttitude()));
    payer.setRelation(bankless, new Relation(tributary, RelationLoader.getDefaultAttitude()));
    bankless.setBank(null);
    assertEquals(30, payer.getTotalForeignTaxRate());
    assertEquals(90, payer.setTaxRate(90));
    payer.tick();
    assertEquals(70, payer.getTaxRate());
    assertEquals(30, payer.getTotalForeignTaxRate());
  }

  @Test
  void prestigeRanksRiseAndFallWithRealWealthAndPersistentAchievements() {
    Faction faction = fixture.saved("home", "Alice");
    var common = RankLoader.getByLevel(1);
    var renowned = RankLoader.getByLevel(2);
    faction.setRank(common);
    faction.setPrestige(123.0);
    assertEquals(123, faction.getPrestige());
    faction.setWealth(5000.0);
    faction.setPersistentPrestigeModifier("Achievement", 20000);
    faction.updatePrestige();
    assertSame(renowned, faction.getRank());
    assertEquals(
        1000,
        faction.getPrestigeModifiers().stream()
            .filter(m -> m.getType().equals("Wealth"))
            .findFirst()
            .orElseThrow()
            .getAmount());
    faction.setPersistentPrestigeModifier("Achievement", 0);
    faction.setWealth(0.0);
    faction.updatePrestige();
    assertSame(common, faction.getRank());
    assertTrue(faction.getPrestige() < 10000);
  }

  @Test
  void subjectPrestigeAndRankAndBranchBonusesAreReflectedExactlyOnce() {
    var vassalType =
        fixture.relationType(
            "prestige-vassal", Map.of("vassal", true, "give-modifiers", List.of("PRESTIGE(10)")));
    Faction ruler = fixture.saved("ruler", "Alice");
    Faction child = fixture.saved("child", "Bob");
    fixture.subject(ruler, child);
    ruler.setRelation(child, new Relation(vassalType, RelationLoader.getDefaultAttitude()));
    ruler.getRank().getModifiers().add(new FactionModifier(FactionModifiers.TRADE_POWER, 5));
    ruler
        .getOrCreateMainGuild()
        .getBranches()
        .get(0)
        .getModifiers()
        .put(GuildModifier.PRESTIGE_BONUS, new BranchModifier(10, 0));
    child.updatePrestige();
    double expected = Formatter.formatDouble(child.getPrestige() * 0.1);
    assertEquals(
        expected,
        ruler.getPrestigeModifiers().stream()
            .filter(m -> m.getType().equals("Subjects"))
            .findFirst()
            .orElseThrow()
            .getAmount());
    assertEquals(10, ruler.getModifier(FactionModifiers.PRESTIGE_BONUS).getAmount());
    double first = ruler.getPrestige();
    ruler.updatePrestige();
    assertEquals(first, ruler.getPrestige());
    assertEquals(5, ruler.getModifier(FactionModifiers.TRADE_POWER).getAmount());
  }

  @Test
  void administrativeAndDiplomaticDeficitsBothContributeToTheFactionPenalty() {
    fixture.lawGroup("expensive", Map.of("upkeep", 50));
    Faction faction = fixture.saved("home", "Alice");
    Faction neighbor = fixture.saved("neighbor", "Bob");
    var treaty = fixture.relationType("expensive-treaty", Map.of("cost", 25));
    faction.setRelation(neighbor, new Relation(treaty, RelationLoader.getDefaultAttitude()));
    double administration = faction.getGovernment().getMaxPower();
    double diplomacy = faction.getDiplomacyHandler().getAvailableCapacity();
    assertTrue(administration < 0);
    assertTrue(diplomacy < 0);
    assertEquals(-administration - diplomacy, faction.getPenalty());
    assertTrue(
        faction.getModifiers().stream()
            .anyMatch(m -> m.getType() == FactionModifiers.PRESTIGE_MALUS && m.getAmount() > 0));
  }

  @Test
  void countyLimitKeepsOnlyAsManyTitlesAsMembersAndDropsClaimsWithoutTerritory() {
    Faction faction = fixture.saved("home", "Alice");
    faction.addProvince(7);
    faction.addProvince(8);
    Title first = fixture.title("first", "county", 7);
    Title second = fixture.title("second", "county", 8);
    faction.addTitle(first);
    faction.addTitle(second);
    faction.countyCheck();
    assertEquals(1, faction.getTitles().size());
    assertTrue(List.of(first, second).contains(faction.getTitles().getFirst()));
    faction.removeProvince(7, false);
    faction.removeProvince(8, false);
    faction.countyCheck();
    assertTrue(faction.getTitles().isEmpty());
  }

  @Test
  void freeTitlesIncludeSubjectTitlesAndARealmLosingTierReleasesHigherTierSubjects() {
    Faction ruler = fixture.saved("ruler", "Alice");
    Faction child = fixture.saved("child", "Bob");
    Title duchy = fixture.title("duchy", "duchy", 7);
    Title county = fixture.title("county", "county", 8);
    ruler.addProvince(7);
    ruler.addTitle(duchy);
    child.addProvince(8);
    child.addTitle(county);
    fixture.subject(ruler, child);
    assertEquals(List.of(county), ruler.getFreeTitles(TierLoader.getByString("county")));
    assertEquals(List.of(duchy), ruler.getFreeTitles(TierLoader.getByString("duchy")));
    var leader = fixture.player("Alice");
    ruler.stripTitle(duchy);
    assertNull(child.getOverlord());
    assertTrue(ruler.getVassals().isEmpty());
    verify(leader).sendMessage(contains("rank difference"));
    assertEquals(List.of(7), ruler.getProvinceHandler().getProvinces());
  }

  @Test
  void restoringAndResettingEspionageStateKeepsOrClearsThePersistedReportSnapshot() {
    Faction faction = fixture.saved("home", "Alice");
    var original = faction.getEspionage();
    var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(original), EspionageState.class);
    faction.setEspionage(restored);
    assertSame(restored, faction.getEspionage());
    faction.setEspionage(null);
    assertNotNull(faction.getEspionage());
    assertNotSame(restored, faction.getEspionage());
    assertEquals(JsonUtil.GSON.toJson(original), JsonUtil.GSON.toJson(faction.getEspionage()));
  }

  @Test
  void politicalLeadershipChangesPromoteAnEligibleMemberAndRejectAStaleTarget() {
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    var proposal = new Proposal("Alice", faction.getGovernment());
    faction.applyPoliticalAction(null, proposal);
    assertEquals("Alice", faction.getLeader());
    proposal.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));
    proposal.setTarget("Visitor");
    faction.applyPoliticalAction(null, proposal);
    assertEquals("Alice", faction.getLeader());
    proposal.setTarget("Bob");
    faction.applyPoliticalAction(null, proposal);
    assertEquals("Bob", faction.getLeader());
    assertTrue(faction.isMember("Alice"));
    assertTrue(faction.isMember("Bob"));
  }

  @Test
  void snapshotElectionActionStartsVotingWhileIncompletePoliticalActionsPreserveTheRealm() {
    fixture.lawGroup(
        "democracy", Map.of("effects.faction.rules", List.of("LEADER_ELECTIONS true")));
    Faction faction = fixture.saved("home", "Alice");
    var proposal = new Proposal("Alice", faction.getGovernment());
    proposal.setPoliticalActionProposal(new PoliticalAction(Action.SNAP_ELECTIONS));
    faction.applyPoliticalAction(null, proposal);
    assertTrue(faction.getGovernment().getElection().isActive());
    assertEquals(List.of("Alice"), faction.getGovernment().getElection().getStoredEligibleVoters());
    for (var action :
        List.of(
            Action.NONE,
            Action.LAW_CHANGE,
            Action.TAX_CHANGE,
            Action.INDEPENDENCE,
            Action.NATIONHOOD,
            Action.WHITE_PEACE,
            Action.SURRENDER)) {
      proposal.setPoliticalActionProposal(new PoliticalAction(action));
      proposal.setTarget("removed-war");
      faction.applyPoliticalAction(null, proposal);
      assertEquals("Alice", faction.getLeader(), action.toString());
      assertEquals(List.of("Alice"), faction.getMembers(), action.toString());
      assertTrue(FactionManager.factions.contains(faction), action.toString());
    }
    assertFalse(faction.canDissolve());
  }

  @Test
  void factionBracketFacadeExposesTheEffectiveAppliedTaxAndFeeLawBounds() {
    Map<String, Object> options = new HashMap<>();
    for (var kind : Brackets.values()) {
      options.put("effects.faction.brackets." + kind.name(), "10-40");
    }
    fixture.lawGroup("finance", options);
    Faction faction = fixture.saved("home", "Alice");
    assertNull(faction.getBracket(null));
    for (var kind : Brackets.values()) {
      Bracket bracket = faction.getBracket(kind);
      assertNotNull(bracket, kind.toString());
      assertEquals(10, bracket.getMin(), kind.toString());
      assertEquals(40, bracket.getMax(), kind.toString());
    }
  }

  @Test
  void promotedFactionRetriesAConflictingMapColorAndKeepsItsLeaderAndRates() {
    Faction host = fixture.saved("home", "Alice");
    Guild guild = fixture.guild(host, "merchants", "Bob");
    host.getGuildHandler().removeGuild("merchants", false, false);
    try (MockedStatic<RandomRGB> rgb = mockStatic(RandomRGB.class)) {
      rgb.when(() -> RandomRGB.isFree("4,5,6")).thenReturn(false);
      rgb.when(RandomRGB::random).thenReturn("11,12,13");
      rgb.when(() -> RandomRGB.isFree("11,12,13")).thenReturn(true);
      Faction promoted = new Faction(guild);
      assertEquals("11,12,13", promoted.getRGB());
      assertEquals("Bob", promoted.getLeader());
      assertEquals(List.of("Bob"), promoted.getMembers());
      promoted.setVassalTaxRate(35);
      assertEquals(35, promoted.getVassalTaxRate());
    }
  }

  @Test
  void inheritedCouncilPermissionWithoutATypeKeepsItsDefaultSizeAndNoElectionType() {
    fixture.lawGroup("foreign", Map.of("effects.vassals.rules", List.of("HAS_COUNCIL true")));
    fixture.lawGroup("domestic", Map.of("effects.faction.rules", List.of("CAN_FAVOUR true")));
    Faction ruler = fixture.saved("ruler", "Alice");
    Faction child = fixture.saved("child", "Bob");
    fixture.subject(ruler, child);
    assertTrue(child.hasFactionRule(Rules.HAS_COUNCIL));
    assertEquals(4, child.getCouncilSize());
    assertEquals(Rules.NO_COUNCIL, child.getCouncilType());
    assertEquals(Member.FOREIGNER, ruler.getRelationToFaction("Visitor"));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "false,false",
    "false,true",
    "true,false",
    "true,true"
  })
  void independenceAndNationhoodMoveTheGuildAndRespectSubjectAndProvinceOwnership(
      boolean nationhood, boolean withSeparateCapital) {
    fixture.relationType(
        "elevation", Map.of("vassal", true, "link", "overlord", "elevation-target", true));
    Faction ruler = fixture.saved("ruler", "Alice");
    Faction child = fixture.saved("child", "Cara");
    connectProvinces(7, 8);
    ruler.addProvince(7);
    ruler.addProvince(8);
    ruler.setCapital(7);
    fixture.subject(ruler, child);
    Guild guild = fixture.guild(ruler, "merchants", "Bob");
    guild.setCapital(withSeparateCapital ? 8 : 7);
    guild.getBank().deposit(37.0);
    Faction observer = fixture.saved("observer", "Dana");
    observer.getBank().deposit(2000.0);
    double existingShare =
        observer.getPrestigeModifiers().stream()
            .filter(m -> m.getType().equals("Wealth"))
            .findFirst()
            .orElseThrow()
            .getAmount();
    assertEquals(981.84, existingShare);
    assertEquals(withSeparateCapital, guild.canBeElevated(null));
    var action = nationhood ? Action.NATIONHOOD : Action.INDEPENDENCE;
    var proposal = new Proposal("Bob", ruler.getGovernment());
    proposal.setPoliticalActionProposal(new PoliticalAction(action));
    var movementData = new MovementData();
    movementData.id = "split";
    movementData.leader = "Bob";
    var movement = new Movement(ruler, movementData);
    var cause = new Cause(movement, proposal, "Bob");
    assertEquals(List.of(guild), cause.getPool().getGuilds());
    ruler.applyPoliticalAction(cause, proposal);
    Faction result = FactionManager.getByString("merchants");
    assertNotNull(result);
    assertNotSame(ruler, result);
    assertSame(result, guild.getFaction());
    assertSame(guild, result.getOrCreateMainGuild());
    assertEquals(List.of("Bob"), result.getMembers());
    assertFalse(ruler.isMember("Bob"));
    assertEquals(37, result.getBank().getWealth());
    assertEquals(0, ruler.getWealth());
    assertEquals(37, result.getWealth());
    assertEquals(
        18.16,
        result.getPrestigeModifiers().stream()
            .filter(m -> m.getType().equals("Wealth"))
            .findFirst()
            .orElseThrow()
            .getAmount());
    assertEquals(
        existingShare,
        observer.getPrestigeModifiers().stream()
            .filter(m -> m.getType().equals("Wealth"))
            .findFirst()
            .orElseThrow()
            .getAmount());
    assertEquals(withSeparateCapital ? List.of(8) : List.of(), result.getProvinces());
    assertEquals(!withSeparateCapital, ruler.hasProvince(8));
    assertTrue(ruler.hasProvince(7));
    assertSame(nationhood ? ruler : null, result.getOverlord());
    assertSame(nationhood ? ruler : null, child.getOverlord());
  }

  @Test
  void
      dissolutionTransfersTerritoryMembersAndPeacefulSubjectsToTheOverlordAndFreesWarParticipants() {
    Faction ruler = fixture.saved("ruler", "Alice");
    Faction source = fixture.saved("source", "Bob");
    Faction peaceful = fixture.saved("peaceful", "Cara");
    Faction fighting = fixture.saved("fighting", "Dana");
    Faction enemy = fixture.saved("enemy", "Evan");
    for (int id : List.of(7, 8, 9)) {
      var province = new Province(id, "PLAINS", 1);
      fixture.provinceData.put(id, province);
    }
    ruler.addProvince(7);
    ruler.setCapital(7);
    source.addProvince(8);
    source.addProvince(9);
    source.setCapital(8);
    Guild artisans = fixture.guild(source, "artisans", "Fay");
    artisans.setCapital(9);
    source.getBank().deposit(20.0);
    artisans.getBank().deposit(30.0);
    Guild oldBase = source.getOrCreateMainGuild();
    fixture.subject(ruler, source);
    fixture.subject(source, peaceful);
    fixture.subject(source, fighting);
    WarManager.get().add(new War(1, fighting, enemy));
    assertTrue(WarManager.isAtWar(fighting));
    try (var database = mockConstruction(Database.class)) {
      Faction result = source.dissolve(source.getVassals(), source.getGuildHandler().getGuilds());
      assertSame(ruler, result);
      assertEquals(1, database.constructed().size());
      verify(database.constructed().getFirst()).deleteFaction(source);
    }
    assertFalse(FactionManager.factions.contains(source));
    assertEquals(Set.of(7, 8, 9), Set.copyOf(ruler.getProvinces()));
    assertTrue(source.getProvinces().isEmpty());
    assertSame(ruler, oldBase.getFaction());
    assertFalse(oldBase.isBase());
    assertEquals(20, oldBase.getBank().getWealth());
    assertSame(ruler, artisans.getFaction());
    assertEquals(30, artisans.getBank().getWealth());
    assertEquals(List.of("Alice", "Bob", "Fay"), ruler.getMembers());
    assertEquals(50, ruler.getWealth());
    assertSame(ruler, peaceful.getOverlord());
    assertNull(fighting.getOverlord());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void anIndependentRealmDissolvesItsGuildsAndSubjectsWhilePreservingItsBaseRealm(
      boolean withSeparateCapital) {
    Faction ruler = fixture.saved("ruler", "Alice");
    Faction subject = fixture.saved("subject", "Cara");
    connectProvinces(7, 8);
    ruler.addProvince(7);
    ruler.addProvince(8);
    ruler.setCapital(7);
    fixture.subject(ruler, subject);
    Guild guild = fixture.guild(ruler, "merchants", "Bob");
    guild.setCapital(withSeparateCapital ? 8 : 7);
    guild.getBank().deposit(12.0);
    var proposal = new Proposal("Alice", ruler.getGovernment());
    proposal.setPoliticalActionProposal(new PoliticalAction(Action.DISSOLVE));
    ruler.applyPoliticalAction(null, proposal);
    assertTrue(FactionManager.factions.contains(ruler));
    assertEquals(List.of("Alice"), ruler.getMembers());
    Faction released = FactionManager.getByString("merchants");
    assertNotNull(released);
    assertSame(released, guild.getFaction());
    assertEquals(12, guild.getBank().getWealth());
    assertEquals(withSeparateCapital ? List.of(8) : List.of(), released.getProvinces());
    assertNull(subject.getOverlord());
    assertNull(released.getOverlord());
    assertSame(ruler, ruler.dissolve(List.of(), ruler.getGuildHandler().getGuilds()));
  }

  private void connectProvinces(int... ids) {
    for (int id : ids) {
      var province = new Province(id, "PLAINS", 1);
      for (int neighbor : ids) if (neighbor != id) province.addNeighbour(neighbor);
      fixture.provinceData.put(id, province);
    }
  }

  @Test
  void promotedGuildWealthIsImmediatelyVisibleInTheNewFactionAndItsPrestigeBreakdown() {
    Faction old = fixture.saved("old", "Alice");
    Guild guild = fixture.guild(old, "merchants", "Bob");
    guild.getBank().deposit(37.0);
    assertEquals(37, guild.getWealth());
    old.getGuildHandler().removeGuild("merchants", false, false);
    Faction promoted = new Faction(guild);
    FactionManager.addFaction(promoted);
    assertEquals(37, promoted.getBank().getWealth());
    assertEquals(37, promoted.getWealth());
    assertEquals(
        37,
        promoted.getPrestigeModifiers().stream()
            .filter(m -> m.getType().equals("Wealth"))
            .findFirst()
            .orElseThrow()
            .getAmount());
  }
}
