package net.tfminecraft.simplefactions.war.civilwar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.database.MovementData;
import net.tfminecraft.simplefactions.diplomacy.Attitude;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.enums.Stance;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.loaders.BranchLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.UpgradeLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarCapitalAssignService;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarLandSplitService;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarLandSplitService.LandSplitPlan;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommitment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CivilWarLifecycleCoverageTest {
  private PersistenceFilesFixture files;
  private FactionDomainFixture fixture;
  private ProvinceManager manager;
  private Map<Integer, Province> provinces;
  private String oldVassalageGroup;
  private String oldVassalageLaw;
  private Map<Integer, List<WarCommitment>> oldCommitments;

  @BeforeEach
  void setup() throws Exception {
    oldVassalageGroup = Cache.civilWarVassalageGroup;
    oldVassalageLaw = Cache.civilWarVassalageLaw;
    files = new PersistenceFilesFixture();
    Files.createDirectories(files.root.resolve("Data"));
    fixture = new FactionDomainFixture();
    oldCommitments = new LinkedHashMap<>(commitments());
    commitments().clear();
    fixture.lawGroup("constitution", Map.of());
    manager = new ProvinceManager();
    provinces = new LinkedHashMap<>();
    manager.start(provinces);
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(manager);
    Cache.civilWarVassalageGroup = "vassalage";
    Cache.civilWarVassalageLaw = "missing";
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (oldCommitments != null) {
        commitments().clear();
        commitments().putAll(oldCommitments);
      }
      if (fixture != null) fixture.close();
    } finally {
      Cache.civilWarVassalageGroup = oldVassalageGroup;
      Cache.civilWarVassalageLaw = oldVassalageLaw;
      if (files != null) files.close();
    }
  }

  private Province province(int id, int x) {
    Province province = new Province(id, "PLAINS", 50, x, 0);
    provinces.put(id, province);
    return province;
  }

  private void link(Province first, Province second) {
    first.addNeighbour(second.getId());
    second.addNeighbour(first.getId());
  }

  private Movement movement(Faction host, String leader, Action action) {
    Proposal proposal = new Proposal(leader, host.getGovernment());
    if (action == Action.LAW_CHANGE) {
      proposal.setLawProposal(fixture.law("constitution", "reform", Map.of()));
    } else {
      proposal.setPoliticalActionProposal(new PoliticalAction(action));
    }
    Movement movement = new Movement(host, leader, proposal);
    host.getGovernment().getMovements().add(movement);
    return movement;
  }

  @Test
  void aFailedConfiguredLawPreservesTheRebelGuildItsMembersAndItsTreasury() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("guild_trade.allowed-types", List.of("guild"));
    config.set("guild_trade.group", 1);
    config.set("realm_trade.allowed-types", List.of("realm"));
    config.set("realm_trade.group", 1);
    for (String id : List.of("guild_trade", "realm_trade")) {
      BranchLoader.map.put(id, new Branch(id, config.getConfigurationSection(id)));
    }
    config.set("guild_road.allowed-types", List.of("guild"));
    UpgradeLoader.map.put(
        "guild_road", new Upgrade("guild_road", config.getConfigurationSection("guild_road")));
    Faction home = fixture.saved("home", "Alice");
    Province one = province(1, 0);
    Province two = province(2, 20);
    link(one, two);
    home.addProvince(1);
    home.addProvince(2);
    home.setCapital(1, true);
    home.getSettlementHandler().found("Capital", 1, 0, 0);
    home.getSettlementHandler().found("Guild Town", 2, 20, 0);
    Guild guild = fixture.guild(home, "traders", "Bob");
    guild.addMember("Cara");
    guild.setCapital(2);
    guild.getBank().deposit(37.0);
    var bank = guild.getBank();
    var type = guild.getType();
    guild.setName("The Merchant Company");
    guild.setRGB("4,8,12");
    guild.setBannerPatterns(List.of("BLUE.BASE", "WHITE.CROSS"));
    guild.rememberLeaderCharacter("Captain Rowan", "Bob");
    guild.getInvites().add("Elena");
    guild.setFavoured(true);
    guild.setStance(Stance.OPPOSE);
    Branch branch = guild.getBranch(1);
    branch.levelUp();
    branch.levelUp();
    Upgrade upgrade = guild.getUpgrade("guild_road");
    upgrade.setLevel(3);
    Movement movement = movement(home, "Bob", Action.LAW_CHANGE);
    assertTrue(movement.getAllSupportingGuilds().contains(guild));
    String error = CivilWarStartService.start(movement);
    assertEquals(CivilWarCopy.VASSALAGE_LAW_MISSING, error);
    assertFalse(movement.isFrozen());
    assertEquals(List.of(home), FactionManager.factions);
    assertSame(guild, home.getGuildHandler().getGuild("traders"));
    assertSame(home, guild.getFaction());
    assertSame(type, guild.getType());
    assertFalse(guild.isBase());
    assertEquals(List.of("Bob", "Cara"), guild.getMembers());
    assertEquals(37, guild.getBank().getWealth());
    assertSame(bank, guild.getBank());
    assertEquals("The Merchant Company", guild.getOwnName());
    assertEquals("4,8,12", guild.getRGB());
    assertEquals(List.of("BLUE.BASE", "WHITE.CROSS"), guild.getBannerPatterns());
    assertEquals("Captain Rowan", guild.getLeaderCharacter());
    assertEquals("Bob", guild.getLeaderCharacterOf());
    assertEquals(List.of("Elena"), guild.getInvites());
    assertTrue(guild.isFavoured());
    assertFalse(guild.isRepressed());
    assertEquals(Stance.OPPOSE, guild.getStance(home));
    assertSame(branch, guild.getBranch(1));
    assertEquals("guild_trade", branch.getId());
    assertEquals(2, branch.getLevel());
    assertSame(upgrade, guild.getUpgrade("guild_road"));
    assertEquals(3, upgrade.getLevel());
    assertEquals(2, guild.getCapital());
    assertEquals(List.of(1, 2), home.getProvinces());
    assertNotNull(home.getSettlementHandler().getByProvince(2));
    assertTrue(WarManager.get().isEmpty());
  }

  @Test
  void aFailedConfiguredLawReturnsCitizenSupportersToTheirOriginalGuild() {
    Faction home = fixture.saved("home", "Alice");
    Province one = province(1, 0);
    Province two = province(2, 20);
    link(one, two);
    home.addProvince(1);
    home.addProvince(2);
    home.setCapital(1, true);
    home.getOrCreateMainGuild().addMember("Bob");
    Movement movement = movement(home, "Bob", Action.LAW_CHANGE);
    assertTrue(movement.getCauses().get(0).getPool().getCitizens().contains("Bob"));
    assertEquals(CivilWarCopy.VASSALAGE_LAW_MISSING, CivilWarStartService.start(movement));
    assertTrue(home.getOrCreateMainGuild().isMember("Bob"));
    assertSame(home, FactionManager.getByMember("Bob"));
    assertEquals(List.of(home), FactionManager.factions);
    assertFalse(movement.isFrozen());
    assertEquals(List.of(1, 2), home.getProvinces());
  }

  @SuppressWarnings("unchecked")
  private static Map<Integer, List<WarCommitment>> commitments() throws Exception {
    Field field = WarCommitmentService.class.getDeclaredField("commitmentsByWar");
    field.setAccessible(true);
    return (Map<Integer, List<WarCommitment>>) field.get(null);
  }

  @Test
  void aSuccessfulCitizenRebellionMovesItsLeaderOutOfTheHostExactlyOnce() {
    fixture.lawGroup("vassalage", Map.of());
    Cache.civilWarVassalageLaw = "current";
    Faction home = fixture.saved("home", "Alice");
    Province one = province(1, 0);
    Province two = province(2, 20);
    link(one, two);
    home.addProvince(1);
    home.addProvince(2);
    home.setCapital(1, true);
    home.getSettlementHandler().found("Capital", 1, 0, 0);
    home.getOrCreateMainGuild().addMember("Bob");
    Movement movement = movement(home, "Bob", Action.LAW_CHANGE);
    fixture.provincesEnabled(true);
    assertNull(CivilWarStartService.start(movement));
    War war = WarManager.getActive().get(0);
    Faction rebels = war.getAttackers().getLeader();
    assertNotSame(home, rebels);
    assertEquals("Bob", rebels.getLeader());
    assertTrue(rebels.getOrCreateMainGuild().isMember("Bob"));
    assertFalse(home.getOrCreateMainGuild().isMember("Bob"));
    assertEquals(
        1, FactionManager.getAllGuilds().stream().filter(guild -> guild.isMember("Bob")).count());
    assertEquals(List.of(1), home.getProvinces());
    assertEquals(List.of(2), rebels.getProvinces());
    assertTrue(movement.isFrozen());
  }

  @Test
  void aValidChangeLeaderTargetRemainsSelectedWhenItsGuildMovesIntoTheRebellion() {
    fixture.lawGroup("vassalage", Map.of());
    Cache.civilWarVassalageLaw = "current";
    fixture.provincesEnabled(true);
    Faction home = fixture.saved("home", "Alice");
    Province one = province(1, 0);
    Province two = province(2, 20);
    link(one, two);
    home.addProvince(1);
    home.addProvince(2);
    home.setCapital(1, true);
    home.getSettlementHandler().found("Capital", 1, 0, 0);
    Guild guild = fixture.guild(home, "traders", "Bob");
    guild.addMember("Cara");
    guild.setCapital(2);
    Movement movement = movement(home, "Bob", Action.CHANGE_LEADER);
    movement.getCauses().get(0).getProposal().setTarget("Cara");
    assertTrue(home.canBecomeLeader("Cara"));

    assertNull(CivilWarStartService.start(movement));

    War war = WarManager.getActive().get(0);
    Faction rebels = war.getAttackers().getLeader();
    assertEquals("Cara", war.getCivilWarSnapshot().getWantedLeaderName());
    assertEquals("Cara", rebels.getLeader());
    assertTrue(rebels.isMember("Cara"));
    assertFalse(home.isMember("Cara"));
    assertSame(guild, rebels.getOrCreateMainGuild());
    assertEquals(List.of("Bob", "Cara"), guild.getMembers());
  }

  @Test
  void failureAfterSplittingReturnsEveryGuildAndCitizenWithoutChangingTreasuries() {
    fixture.lawGroup("vassalage", Map.of());
    Cache.civilWarVassalageLaw = "current";
    Faction home = fixture.saved("home", "Alice");
    Province one = province(1, 0);
    Province two = province(2, 20);
    link(one, two);
    home.addProvince(1);
    home.addProvince(2);
    home.setCapital(1, true);
    home.getSettlementHandler().found("Capital", 1, 0, 0);
    home.getSettlementHandler().found("Guild Town", 2, 20, 0);
    Guild main = fixture.guild(home, "traders", "Bob");
    Guild second = fixture.guild(home, "crafters", "Cara");
    main.setCapital(2);
    second.setCapital(2);
    main.getBank().deposit(37.0);
    second.getBank().deposit(21.0);
    home.getOrCreateMainGuild().addMember("Dave");
    Movement movement = movement(home, "Bob", Action.LAW_CHANGE);
    movement.getSupporters().addGuild(second);
    movement.getSupporters().addCitizen("Dave");
    assertEquals(Cache.PROVINCES_DISABLED_MESSAGE, CivilWarStartService.start(movement));
    assertSame(main, home.getGuildHandler().getGuild("traders"));
    assertSame(second, home.getGuildHandler().getGuild("crafters"));
    assertSame(home, main.getFaction());
    assertSame(home, second.getFaction());
    assertFalse(main.isBase());
    assertFalse(second.isBase());
    assertEquals(2, main.getCapital());
    assertEquals(2, second.getCapital());
    assertEquals(37, main.getBank().getWealth());
    assertEquals(21, second.getBank().getWealth());
    assertTrue(home.getOrCreateMainGuild().isMember("Dave"));
    assertEquals(List.of(1, 2), home.getProvinces());
    assertEquals(List.of(home), FactionManager.factions);
    assertFalse(movement.isFrozen());
    assertTrue(WarManager.get().isEmpty());
  }

  @Test
  void rebelFactoryAllocatesAnUnusedIdentityAndRegistersEachCreatedFaction() {
    Faction home = fixture.saved("home", "Alice");
    home.setName("Home Realm");
    Faction existing = fixture.saved("home_rebels", "Bob");
    existing.setRGB("138,48,48");
    Faction existingTwo = fixture.saved("home_rebels2", "Cara");
    existingTwo.setRGB("138,49,48");

    Faction rebels = CivilWarTempRebelFactory.create(home, "Dave");

    assertEquals("home_rebels3", rebels.getId());
    assertEquals("138,50,48", rebels.getRGB());
    assertEquals("Home Realm Rebels", org.bukkit.ChatColor.stripColor(rebels.getName()));
    assertEquals("Dave", rebels.getLeader());
    assertTrue(rebels.isMember("Dave"));
    assertSame(rebels, FactionManager.getByString(rebels.getId()));
    assertNull(CivilWarTempRebelFactory.create(null, "Dave"));
    assertNull(CivilWarTempRebelFactory.create(home, null));
    assertEquals(4, FactionManager.factions.size());
  }

  @Test
  void aCitizenOnlyFactoryRequestUsesTheFallbackLeaderAndDefaultDisplayName() {
    Faction home = fixture.saved("home", "Alice");
    home.setName(null);
    Guild.RebelNation nation = CivilWarTempRebelFactory.createFromMainGuild(home, null, "Bob");
    assertNotNull(nation);
    assertNull(nation.ownName());
    assertEquals("Rebel Rebels", org.bukkit.ChatColor.stripColor(nation.faction().getName()));
    assertEquals("Bob", nation.faction().getLeader());
    assertNull(CivilWarTempRebelFactory.createFromMainGuild(home, null, null));
    assertEquals(2, FactionManager.factions.size());
  }

  @Test
  void capitalAssignmentUsesEachGuildsOldSeatAndTheNearestSurvivingSettlement() {
    Faction home = fixture.saved("home", "Alice");
    Faction rebels = fixture.saved("rebels", "Bob");
    province(1, 0);
    province(2, 90);
    province(3, 100);
    province(4, 120);
    home.addProvince(1);
    home.getSettlementHandler().found("Loyal Town", 1, 0, 0);
    home.setCapital(1, true);
    for (int id : List.of(2, 3, 4)) rebels.addProvince(id);
    rebels.getSettlementHandler().found("West Town", 2, 90, 0);
    rebels.getSettlementHandler().found("East Town", 4, 120, 0);
    Guild second = fixture.guild(rebels, "traders", "Cara");
    Map<String, Integer> oldCapitals = new LinkedHashMap<>();
    oldCapitals.put(rebels.getOrCreateMainGuild().getId(), 4);
    oldCapitals.put(second.getId(), 3);
    LandSplitPlan plan = new LandSplitPlan(List.of(2, 3, 4), List.of(1));

    assertEquals(4, CivilWarCapitalAssignService.assign(home, rebels, plan, 1, oldCapitals));

    assertEquals(4, rebels.getCapital());
    assertEquals(2, second.getCapital());
    assertEquals(1, home.getCapital());
    assertEquals(2, rebels.getSettlementHandler().getAll().size());
  }

  @Test
  void capitalAssignmentReplacesACapturedHostSeatAndUsesAnotherGuildsPreferredSeat() {
    Faction home = fixture.saved("home", "Alice");
    Faction rebels = fixture.saved("rebels", "Bob");
    province(1, 0);
    province(2, 20);
    province(3, 30);
    home.addProvince(1);
    home.getSettlementHandler().found("Loyal Town", 1, 0, 0);
    home.setCapital(2, true);
    rebels.addProvince(2);
    rebels.addProvince(3);
    rebels.getSettlementHandler().found("Captured Seat", 2, 20, 0);
    rebels.getSettlementHandler().found("Guild Seat", 3, 30, 0);
    Guild guild = fixture.guild(rebels, "traders", "Cara");
    Map<String, Integer> oldCapitals = new LinkedHashMap<>();
    oldCapitals.put("gone", null);
    oldCapitals.put("landless", -1);
    oldCapitals.put(guild.getId(), 3);

    assertEquals(
        3,
        CivilWarCapitalAssignService.assign(
            home, rebels, new LandSplitPlan(List.of(2, 3), List.of(1)), 2, oldCapitals));

    assertEquals(1, home.getCapital());
    assertEquals(3, rebels.getCapital());
    assertEquals(3, guild.getCapital());
  }

  @Test
  void capitalAssignmentCreatesACampAtRealProvinceCoordinatesAndClearsLandlessGuildSeats() {
    Faction home = fixture.saved("home", "Alice");
    Faction rebels = fixture.saved("rebels", "Bob");
    province(1, 15);
    province(2, 75);
    home.addProvince(1);
    home.setCapital(1, true);
    rebels.addProvince(2);
    Guild guild = fixture.guild(rebels, "traders", "Cara");
    guild.setCapital(2);
    assertEquals(
        2,
        CivilWarCapitalAssignService.assign(
            home, rebels, new LandSplitPlan(List.of(2), List.of(1)), 1, null));
    var camp = rebels.getSettlementHandler().getByProvince(2);
    assertNotNull(camp);
    assertEquals(2, camp.getCenterProvince());
    assertEquals(75, camp.getCenterX());
    assertEquals(0, camp.getCenterZ());
    assertEquals(2, guild.getCapital());
    assertNotNull(home.getSettlementHandler().getByProvince(1));

    rebels.removeProvince(2, false);
    home.removeProvince(1, false);
    CivilWarCapitalAssignService.assign(
        home, rebels, new LandSplitPlan(List.of(), List.of()), 1, Map.of());
    assertEquals(-1, guild.getCapital());
    assertEquals(-1, home.getCapital());
  }

  private void twoProvinceHome(Faction home) {
    Province one = province(1, 0);
    Province two = province(2, 20);
    link(one, two);
    home.addProvince(1);
    home.addProvince(2);
    home.setCapital(1, true);
    home.getSettlementHandler().found("Capital", 1, 0, 0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void selectingAnotherGuildsMemberPreservesItsLeaderAndRollbackRestoresTheOriginalRoster(
      boolean success) {
    fixture.lawGroup("vassalage", Map.of());
    Cache.civilWarVassalageLaw = "current";
    fixture.provincesEnabled(success);
    Faction home = fixture.saved("home", "Alice");
    twoProvinceHome(home);
    home.getOrCreateMainGuild().addMember("Bob");
    Guild guild = fixture.guild(home, "merchants", "Cara");
    guild.addMember("Dave");
    guild.getBank().deposit(19.0);
    Movement movement = movement(home, "Bob", Action.CHANGE_LEADER);
    movement.getCauses().getFirst().getProposal().setTarget("Dave");
    assertTrue(home.canBecomeLeader("Dave"));
    assertFalse(home.canBecomeLeader("Cara"));

    String result = CivilWarStartService.start(movement);

    if (success) {
      assertNull(result);
      War war = WarManager.getActive().getFirst();
      assertEquals("Dave", war.getAttackers().getLeader().getLeader());
      assertEquals(List.of("Cara"), guild.getMembers());
      assertEquals("Cara", guild.getLeader());
      assertTrue(
          war.getCivilWarSnapshot().getMemberMoves().stream()
              .anyMatch(move -> move.player().equals("Dave") && !move.originWasGuildLeader()));
      assertSame(home, guild.getFaction());
      assertFalse(home.isMember("Dave"));
    } else {
      assertEquals(Cache.PROVINCES_DISABLED_MESSAGE, result);
      assertEquals(List.of("Cara", "Dave"), guild.getMembers());
      assertEquals("Cara", guild.getLeader());
      assertTrue(home.isMember("Bob"));
      assertEquals(List.of(home), FactionManager.factions);
      assertTrue(WarManager.get().isEmpty());
    }
    assertEquals(19, guild.getBank().getWealth());
  }

  @Test
  void aCitizenSelectedAsLeaderIsRecordedOnceAndStaleSupporterNamesDoNotMoveOthers() {
    fixture.lawGroup("vassalage", Map.of());
    Cache.civilWarVassalageLaw = "current";
    fixture.provincesEnabled(true);
    Faction home = fixture.saved("home", "Alice");
    twoProvinceHome(home);
    home.getOrCreateMainGuild().addMember("Bob");
    home.getOrCreateMainGuild().addMember("");
    Movement movement = movement(home, "Bob", Action.CHANGE_LEADER);
    movement.getCauses().getFirst().getProposal().setTarget("Bob");
    movement.getSupporters().getCitizens().addAll(Arrays.asList(null, "", "Alice", "departed"));

    assertNull(CivilWarStartService.start(movement));

    War war = WarManager.getActive().getFirst();
    assertEquals("Bob", war.getAttackers().getLeader().getLeader());
    assertEquals(1, war.getCivilWarSnapshot().getMemberMoves().size());
    assertEquals("Bob", war.getCivilWarSnapshot().getMemberMoves().getFirst().player());
    assertEquals(List.of("", "Alice"), home.getMembers());
    assertEquals(List.of("Bob"), war.getAttackers().getLeader().getMembers());
  }

  @Test
  void anImportedMovementWithoutALeaderCannotCreateATemporaryFaction() {
    fixture.lawGroup("vassalage", Map.of());
    Cache.civilWarVassalageLaw = "current";
    Faction home = fixture.saved("home", "Alice");
    twoProvinceHome(home);
    home.getOrCreateMainGuild().addMember("Bob");
    Movement movement = movement(home, "Bob", Action.LAW_CHANGE);
    movement.setLeader(null);

    assertEquals(CivilWarCopy.COULD_NOT_START, CivilWarStartService.start(movement));

    assertEquals(List.of(home), FactionManager.factions);
    assertEquals(List.of(1, 2), home.getProvinces());
    assertTrue(home.isMember("Bob"));
    assertFalse(movement.isFrozen());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void guildAndVassalSupportFormOneRebelRealmAndFailureRestoresOriginalSubjects(boolean success) {
    fixture.lawGroup("vassalage", Map.of());
    Cache.civilWarVassalageLaw = "current";
    fixture.provincesEnabled(success);
    Faction home = fixture.saved("home", "Alice");
    twoProvinceHome(home);
    Guild guild = fixture.guild(home, "traders", "Bob");
    guild.setCapital(2);
    Faction subject = fixture.saved("subject", "Cara");
    Province three = province(3, 40);
    link(provinces.get(2), three);
    subject.addProvince(3);
    subject.setCapital(3, true, false);
    fixture.subject(home, subject);
    YamlConfiguration attitudes = new YamlConfiguration();
    attitudes.set("friendly.target", 25);
    attitudes.set("cautious.target", -15);
    Attitude friendly = new Attitude("friendly", attitudes.getConfigurationSection("friendly"));
    Attitude cautious = new Attitude("cautious", attitudes.getConfigurationSection("cautious"));
    RelationLoader.attitudes.addAll(List.of(friendly, cautious));
    home.setRelation(subject, new Relation(RelationLoader.getType("vassal"), friendly, 41));
    subject.setRelation(home, new Relation(RelationLoader.getType("overlord"), cautious, -12));
    Movement movement = movement(home, "Bob", Action.LAW_CHANGE);
    movement.getSupporters().addFaction(subject);

    String result = CivilWarStartService.start(movement);

    if (success) {
      assertNull(result);
      Faction rebels = WarManager.getActive().getFirst().getAttackers().getLeader();
      assertEquals(rebels.getId(), RelationManager.getOverlord(subject));
      assertTrue(RelationManager.getSubjects(rebels).contains(subject));
      assertFalse(RelationManager.getSubjects(home).contains(subject));
      assertTrue(WarManager.getActive().getFirst().isMainParticipant(subject));
    } else {
      assertEquals(Cache.PROVINCES_DISABLED_MESSAGE, result);
      assertEquals(home.getId(), RelationManager.getOverlord(subject));
      assertEquals(List.of(subject), RelationManager.getSubjects(home));
      assertSame(friendly, home.getRelation(subject.getId()).getAttitude());
      assertSame(cautious, subject.getRelation(home.getId()).getAttitude());
      assertEquals(41, home.getRelation(subject.getId()).getOpinion());
      assertEquals(-12, subject.getRelation(home.getId()).getOpinion());
      assertEquals(List.of(home, subject), FactionManager.factions);
      assertSame(home, guild.getFaction());
      assertFalse(guild.isBase());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void capturingTheCapitalTransfersASecondaryTitleAndFailedStartRestoresBoth(boolean success) {
    fixture.lawGroup("vassalage", Map.of());
    Cache.civilWarVassalageLaw = "current";
    fixture.provincesEnabled(success);
    Faction home = fixture.saved("home", "Alice");
    Province one = province(1, 0);
    Province two = province(2, 20);
    Province three = province(3, 40);
    link(one, two);
    link(two, three);
    for (int id : List.of(1, 2, 3)) home.addProvince(id);
    home.setCapital(1, true, false);
    home.getSettlementHandler().found("Old Capital", 1, 0, 0);
    home.getSettlementHandler().found("Loyal Town", 3, 40, 0);
    Guild guild = fixture.guild(home, "traders", "Bob");
    guild.setCapital(2);
    var primary = fixture.title("loyal-duchy", "duchy", 3);
    var captured = fixture.title("rebel-county", "county", 1, 2);
    home.addTitle(primary);
    home.addTitle(captured);
    for (Province province : List.of(one, two)) {
      province.setData(guild.getId(), new ProvinceDataEntry(guild, 100, 20));
      province.calculateProsperity();
    }
    Guild loyal = home.getOrCreateMainGuild();
    three.setData(loyal.getId(), new ProvinceDataEntry(loyal, 100, 20));
    three.calculateProsperity();
    assertEquals(
        new LandSplitPlan(List.of(1, 2), List.of(3)),
        CivilWarLandSplitService.plan(home, List.of(guild)));
    Movement movement = movement(home, "Bob", Action.LAW_CHANGE);

    String result = CivilWarStartService.start(movement);

    if (success) {
      assertNull(result);
      War war = WarManager.getActive().getFirst();
      Faction rebels = war.getAttackers().getLeader();
      assertEquals(1, war.getCivilWarSnapshot().getHostOldCapitalId());
      assertEquals("rebel-county", war.getCivilWarSnapshot().getMovedTitleId());
      assertEquals(3, home.getCapital());
      assertTrue(rebels.hasTitle(captured));
      assertFalse(home.hasTitle(captured));
      assertTrue(home.hasTitle(primary));
      assertEquals(List.of(3), home.getProvinces());
      assertTrue(rebels.getProvinces().containsAll(List.of(1, 2)));
    } else {
      assertEquals(Cache.PROVINCES_DISABLED_MESSAGE, result);
      assertEquals(1, home.getCapital());
      assertTrue(home.hasTitle(captured));
      assertTrue(home.hasTitle(primary));
      assertEquals(3, home.getProvinces().size());
      assertTrue(home.getProvinces().containsAll(List.of(1, 2, 3)));
      assertSame(home, guild.getFaction());
      assertEquals(List.of(home), FactionManager.factions);
    }
  }

  @Test
  void publicSupporterSnapshotsSkipMissingObjectsButKeepValidSiblings() {
    Faction host = fixture.saved("host", "Alice");
    Guild guild = fixture.guild(host, "traders", "Bob");
    Faction vassal = fixture.saved("vassal", "Cara");
    Faction incomplete = fixture.saved("incomplete", "Dave");
    fixture.subject(host, vassal);
    Movement movement = movement(host, "Bob", Action.LAW_CHANGE);
    movement.getSupporters().getGuilds().add(null);
    movement.getSupporters().addGuild(host.getOrCreateMainGuild());
    movement.getSupporters().getFactions().add(null);
    movement.getSupporters().addFaction(vassal);
    incomplete.setId(null);
    movement.getSupporters().addFaction(incomplete);

    assertEquals(List.of(guild), CivilWarStartService.supportingHostGuilds(movement, host));
    assertEquals(List.of(vassal), CivilWarStartService.supportingVassals(movement, host));
    assertEquals(
        List.of(vassal),
        CivilWarStartService.directSupportingVassals(
            Arrays.asList(null, incomplete, vassal), host));
    GuildData imported = new GuildData();
    imported.name = "Incomplete guild";
    imported.leader = "Elena";
    imported.type = "guild";
    imported.rgb = "7,8,9";
    imported.capital = -1;
    imported.banner = List.of("white");
    Guild missingIdentity = new Guild(imported, host);
    assertEquals(
        Map.of("traders", -1),
        CivilWarStartService.snapshotGuildCapitals(Arrays.asList(null, missingIdentity, guild)));
  }

  @Test
  void failureToPublishTemporaryFactionDeletionStillRestoresActualGuildState() {
    Faction home = fixture.saved("home", "Alice");
    twoProvinceHome(home);
    Guild guild = fixture.guild(home, "traders", "Bob");
    guild.setCapital(2);
    guild.getBank().deposit(23.0);
    Movement movement = movement(home, "Bob", Action.LAW_CHANGE);
    AtomicBoolean unavailable = new AtomicBoolean();
    doAnswer(
            call -> {
              if (guild.getFaction() == home
                  && !guild.isBase()
                  && FactionManager.factions.size() > 1) {
                unavailable.set(true);
                throw new IllegalStateException("map publisher unavailable");
              }
              return null;
            })
        .when(fixture.map)
        .enqueue(eq("nation"), startsWith("138,"));

    assertEquals(CivilWarCopy.VASSALAGE_LAW_MISSING, CivilWarStartService.start(movement));

    assertTrue(unavailable.get(), "exercise the unavailable external publisher during cleanup");
    assertEquals(List.of(home), FactionManager.factions);
    assertSame(home, guild.getFaction());
    assertSame(guild, home.getGuildHandler().getGuild("traders"));
    assertEquals(List.of("Bob"), guild.getMembers());
    assertFalse(guild.isBase());
    assertEquals(23, guild.getBank().getWealth());
    assertEquals(List.of(1, 2), home.getProvinces());
    assertTrue(WarManager.get().isEmpty());
  }

  @Test
  void failedStartCannotDiscardAnImportedOverlordRelationWhoseCounterpartWasRemoved() {
    fixture.lawGroup("vassalage", Map.of());
    Cache.civilWarVassalageLaw = "current";
    Faction home = fixture.saved("home", "Alice");
    twoProvinceHome(home);
    Guild guild = fixture.guild(home, "traders", "Bob");
    guild.setCapital(2);
    Faction subject = fixture.saved("subject", "Cara");
    var orphanType =
        fixture.relationType(
            "orphan-overlord", Map.of("overlord", true, "link", "retired-vassalage"));
    Relation imported = new Relation(orphanType, RelationLoader.getDefaultAttitude());
    subject.setRelation(home, imported);
    assertEquals(home.getId(), RelationManager.getOverlord(subject));
    Movement movement = movement(home, "Bob", Action.LAW_CHANGE);
    movement.getSupporters().addFaction(subject);

    assertNotNull(CivilWarStartService.start(movement));

    assertEquals(home.getId(), RelationManager.getOverlord(subject));
    assertSame(orphanType, subject.getRelation(home.getId()).getType());
    assertEquals(List.of(home, subject), FactionManager.factions);
    assertSame(home, guild.getFaction());
    assertFalse(guild.isBase());
    assertTrue(WarManager.get().isEmpty());
  }

  @Test
  void failedStartPreservesAnOriginallyAbsentReciprocalRelation() {
    fixture.lawGroup("vassalage", Map.of());
    Cache.civilWarVassalageLaw = "current";
    Faction home = fixture.saved("home", "Alice");
    twoProvinceHome(home);
    Guild guild = fixture.guild(home, "traders", "Bob");
    guild.setCapital(2);
    Faction subject = fixture.saved("subject", "Cara");
    Relation original =
        new Relation(RelationLoader.getType("overlord"), RelationLoader.getDefaultAttitude(), 19);
    subject.setRelation(home, original);
    assertFalse(home.getRelations().containsKey(subject.getId()));
    Movement movement = movement(home, "Bob", Action.LAW_CHANGE);
    movement.getSupporters().addFaction(subject);

    assertEquals(Cache.PROVINCES_DISABLED_MESSAGE, CivilWarStartService.start(movement));

    assertFalse(home.getRelations().containsKey(subject.getId()));
    assertSame(original, subject.getRelation(home.getId()));
    assertEquals(19, subject.getRelation(home.getId()).getOpinion());
    assertSame(home, guild.getFaction());
    assertFalse(guild.isBase());
    assertEquals(List.of(home, subject), FactionManager.factions);
    assertTrue(WarManager.get().isEmpty());
  }

  @Test
  void absentFrozenAndHostlessMovementsCannotChangeFactionState() {
    assertEquals(CivilWarCopy.COULD_NOT_START, CivilWarStartService.start(null));
    assertEquals(
        CivilWarCopy.COULD_NOT_START,
        CivilWarStartService.start(new Movement(null, new MovementData())));
    Faction host = fixture.saved("host", "Alice");
    host.getOrCreateMainGuild().addMember("Bob");
    Movement movement = movement(host, "Bob", Action.LAW_CHANGE);
    movement.setFrozen(true);
    assertEquals(CivilWarCopy.COULD_NOT_START, CivilWarStartService.start(movement));
    assertEquals(List.of(host), FactionManager.factions);
    assertTrue(host.isMember("Bob"));
    assertTrue(WarManager.get().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"group_null", "group_blank", "law_null", "law_blank"})
  void missingVassalageConfigurationRejectsBeforeMovingMembersOrLand(String missing) {
    Faction host = fixture.saved("host", "Alice");
    twoProvinceHome(host);
    host.getOrCreateMainGuild().addMember("Bob");
    Movement movement = movement(host, "Bob", Action.LAW_CHANGE);
    if (missing.equals("group_null")) Cache.civilWarVassalageGroup = null;
    if (missing.equals("group_blank")) Cache.civilWarVassalageGroup = " ";
    if (missing.equals("law_null")) Cache.civilWarVassalageLaw = null;
    if (missing.equals("law_blank")) Cache.civilWarVassalageLaw = " ";

    assertEquals(CivilWarCopy.VASSALAGE_LAW_MISSING, CivilWarStartService.start(movement));

    assertEquals(List.of(host), FactionManager.factions);
    assertEquals(List.of(1, 2), host.getProvinces());
    assertSame(host, FactionManager.getByMember("Bob"));
    assertFalse(movement.isFrozen());
    assertEquals(
        CivilWarCopy.VASSALAGE_LAW_MISSING, CivilWarStartService.applyConfiguredVassalage(host));
  }

  @Test
  void vassalageHelperValidatesTheTargetAndAppliesActualScopedEffects() {
    assertEquals(
        CivilWarCopy.VASSALAGE_LAW_MISSING, CivilWarStartService.applyConfiguredVassalage(null));
    fixture.lawGroup(
        "vassalage", Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS false")));
    fixture.law(
        "vassalage",
        "autonomous",
        Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS true")));
    Faction rebels = fixture.saved("rebels", "Bob");
    assertFalse(rebels.canHaveVassals());
    Cache.civilWarVassalageLaw = "autonomous";

    assertNull(CivilWarStartService.applyConfiguredVassalage(rebels));

    assertTrue(rebels.canHaveVassals());
    assertEquals("autonomous", rebels.getLawHandler().getGroup("vassalage").getCurrent().getId());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void directVassalsLeadTheirOwnRebellionAndFailedStartRestoresBothDiplomacyDirections(
      boolean success) {
    Faction host = fixture.saved("host", "Alice");
    Faction first = fixture.saved("first", "Bob");
    Faction second = fixture.saved("second", "Cara");
    Province one = province(1, 0);
    Province two = province(2, 20);
    Province three = province(3, 40);
    link(one, two);
    link(two, three);
    host.addProvince(1);
    host.setCapital(1, true, false);
    first.addProvince(2);
    first.setCapital(2, true, false);
    second.addProvince(3);
    second.setCapital(3, true, false);
    fixture.subject(host, first);
    fixture.subject(host, second);
    Movement movement = movement(host, "Bob", Action.LAW_CHANGE);
    movement.getSupporters().addFaction(second);
    assertTrue(movement.getAllSupportingFactions().contains(first));
    fixture.provincesEnabled(success);

    String result = CivilWarStartService.start(movement);

    if (success) {
      assertNull(result);
      War war = WarManager.getActive().getFirst();
      assertSame(first, war.getAttackers().getLeader());
      assertEquals(
          List.of(first, second),
          war.getAttackers().getMainParticipants().stream().map(p -> p.getLeader()).toList());
      assertNull(war.getCivilWarSnapshot().getTempRebelFactionId());
      assertEquals(2, war.getCivilWarSnapshot().getWartimeVassalEnds().size());
      assertNull(RelationManager.getOverlord(first));
      assertNull(RelationManager.getOverlord(second));
      assertTrue(movement.isFrozen());
    } else {
      assertEquals(Cache.PROVINCES_DISABLED_MESSAGE, result);
      assertEquals(host.getId(), RelationManager.getOverlord(first));
      assertEquals(host.getId(), RelationManager.getOverlord(second));
      assertTrue(RelationManager.getSubjects(host).containsAll(List.of(first, second)));
      assertTrue(WarManager.get().isEmpty());
      assertFalse(movement.isFrozen());
    }
    assertEquals(List.of(host, first, second), FactionManager.factions);
  }

  @Test
  void aVassalLedRebellionAddsSupportingHostGuildsAsASeparateRebelFaction() {
    fixture.lawGroup("vassalage", Map.of());
    Cache.civilWarVassalageLaw = "current";
    Faction host = fixture.saved("host", "Alice");
    twoProvinceHome(host);
    Faction vassal = fixture.saved("vassal", "Bob");
    Province three = province(3, 40);
    link(provinces.get(2), three);
    vassal.addProvince(3);
    vassal.setCapital(3, true, false);
    fixture.subject(host, vassal);
    Guild guild = fixture.guild(host, "traders", "Cara");
    guild.setCapital(2);
    Movement movement = movement(host, "Bob", Action.LAW_CHANGE);
    movement.getSupporters().addGuild(guild);
    fixture.provincesEnabled(true);

    assertNull(CivilWarStartService.start(movement));

    War war = WarManager.getActive().getFirst();
    Faction rebels = FactionManager.getByString(war.getCivilWarSnapshot().getTempRebelFactionId());
    assertSame(vassal, war.getAttackers().getLeader());
    assertNotNull(rebels);
    assertTrue(war.isMainParticipant(rebels));
    assertSame(guild, rebels.getOrCreateMainGuild());
    assertSame(rebels, guild.getFaction());
    assertEquals("Cara", rebels.getLeader());
    assertNull(RelationManager.getOverlord(vassal));
  }

  @Test
  void supporterQueriesFilterMissingIdentitiesAndOnlyReturnDirectVassals() {
    Faction host = fixture.saved("host", "Alice");
    Faction direct = fixture.saved("direct", "Bob");
    Faction nested = fixture.saved("nested", "Cara");
    Faction independent = fixture.saved("independent", "Dave");
    fixture.subject(host, direct);
    fixture.subject(direct, nested);
    direct.getOrCreateMainGuild().addMember("Dora");
    Movement movement = movement(host, "Bob", Action.LAW_CHANGE);
    movement.getSupporters().addFaction(nested);
    movement.getSupporters().addFaction(host);
    assertEquals(List.of(), CivilWarStartService.supportingVassals(null, host));
    assertEquals(List.of(), CivilWarStartService.supportingVassals(movement, null));
    assertTrue(
        CivilWarStartService.supportingVassals(movement, host)
            .containsAll(List.of(direct, nested)));
    assertFalse(CivilWarStartService.supportingVassals(movement, host).contains(host));
    assertEquals(
        List.of(direct),
        CivilWarStartService.directSupportingVassals(
            Arrays.asList(null, direct, nested, independent), host));
    assertEquals(List.of(), CivilWarStartService.directSupportingVassals(null, host));
    assertEquals(List.of(), CivilWarStartService.directSupportingVassals(List.of(direct), null));
    assertFalse(CivilWarStartService.isVassalMember(null, "Bob"));
    assertFalse(CivilWarStartService.isVassalMember(host, null));
    assertTrue(CivilWarStartService.isVassalMember(host, "Bob"));
    assertTrue(CivilWarStartService.isVassalMember(host, "Dora"));
    assertFalse(CivilWarStartService.isVassalMember(host, "Dave"));
  }

  @Test
  void selectingARebelMainUsesItsMemberFirstThenHighestRealTradePower() {
    Faction host = fixture.saved("host", "Alice");
    Guild modest = fixture.guild(host, "modest", "Bob");
    Guild wealthy = fixture.guild(host, "wealthy", "Cara");
    modest.getTradeBreakdown().setTradePower(4);
    wealthy.getTradeBreakdown().setTradePower(12);
    assertTrue(
        wealthy.getTradeBreakdown().getTradePower() > modest.getTradeBreakdown().getTradePower());
    Movement member = movement(host, "Bob", Action.LAW_CHANGE);
    assertSame(modest, CivilWarStartService.pickRebelMainGuild(member, List.of(wealthy, modest)));
    assertSame(
        wealthy,
        CivilWarStartService.pickRebelMainGuild(null, Arrays.asList(null, modest, wealthy)));
    assertNull(CivilWarStartService.pickRebelMainGuild(member, List.of()));
    assertNull(CivilWarStartService.pickRebelMainGuild(member, null));
    assertEquals(Map.of(), CivilWarStartService.snapshotGuildCapitals(null));
    assertEquals(
        Map.of(modest.getId(), -1, wealthy.getId(), -1),
        CivilWarStartService.snapshotGuildCapitals(Arrays.asList(null, modest, wealthy)));
    assertEquals(List.of(), CivilWarStartService.supportingHostGuilds(null, host));
    assertEquals(List.of(), CivilWarStartService.supportingHostGuilds(member, null));
  }
}
