package net.tfminecraft.simplefactions.war.campaign.raid.intruder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.CampaignRaidData;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidAttackerEliminationService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyLedger;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidState;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidBossBarService;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidFightScheduler;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidMusterReminderService;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidMusterScheduler;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/** Real saved raid/warband/battle upgrades; only Paper and the campaign clock are boundaries. */
class LegacyRaidWarbandIdentityTest {
  private static final int WAR_ID = 942711;
  private static final String PREFIX = "def_port_raid";
  private static final String RAID_ID = PREFIX + "_w" + WAR_ID;
  private static final Instant NOW = Instant.parse("2026-10-10T18:01:00Z");
  private static final LocalDate DAY = LocalDate.of(2026, 10, 10);

  @Test
  void loadedFightingRaidKeepsSavedParticipantsExemptAndOutsidersPenalized() throws Exception {
    try (Context context = new Context()) {
      context.saveLegacy(CampaignRaidState.FIGHTING);
      context.load();
      CampaignRaid raid = context.raid();
      Battle battle = BattleManager.getByString(RAID_ID);
      assertNotNull(battle);
      assertTrue(battle.hasStarted());
      assertTrue(battle.isCampaignRaid());
      assertFalse(
          CampaignRaidIntruderService.shouldPenalize(
              context.war, raid, context.alice.getUniqueId(), "Alice", 20),
          "The persisted attacker leader remains a legitimate participant after resume");
      assertFalse(
          CampaignRaidIntruderService.shouldPenalize(
              context.war, raid, context.ann.getUniqueId(), "Ann", 20));
      assertTrue(
          CampaignRaidIntruderService.shouldPenalize(
              context.war, raid, context.outsider.getUniqueId(), "Unenrolled", 20));
      context.assertRoster(battle);
      RaidAttackerEliminationService.markOut(battle, context.ann.getUniqueId());
      assertTrue(
          CampaignRaidIntruderService.shouldPenalize(
              context.war, raid, context.ann.getUniqueId(), "Ann", 20),
          "Migration must not exempt an eliminated attacker");
    }
  }

  @Test
  void migratedIdentitySurvivesDisplayRenameAndRepeatedRealSaveReload() throws Exception {
    try (Context context = new Context()) {
      context.saveLegacy(CampaignRaidState.FIGHTING);
      context.load();
      for (int round = 0; round < 3; round++) {
        context.raid().setDisplayName("Renamed port " + round + " Raid");
        WarManager.persist(context.war);
        BattlePersistenceService.saveAll();
        JsonObject saved =
            JsonUtil.readJson(context.warFile().toFile(), JsonObject.class)
                .getAsJsonObject("activeCampaignRaid");
        assertEquals(PREFIX, savedPrefix(saved));
        context.load();
        assertEquals("Renamed port " + round + " Raid", context.raid().getDisplayName());
        context.assertRoster(BattleManager.getByString(RAID_ID));
        context.assertUnrelatedManualBattle();
      }
    }
  }

  @Test
  void loadedMusterRecoversSavedLeadersAndMembersWithoutEmptyReplacementBands() throws Exception {
    try (Context context = new Context()) {
      context.saveLegacy(CampaignRaidState.MUSTER);
      context.load();
      assertEquals(CampaignRaidState.MUSTER, context.raid().getState());
      context.assertBands();
      assertEquals(1, BattleManager.get().size(), "Only the unrelated manual battle exists");
      assertNull(BattleManager.getByString(RAID_ID));
      context.assertUnrelatedManualBattle();
      assertTrue(Files.exists(context.bandFile(PREFIX + "_attacker")));
      assertFalse(Files.exists(context.bandFile(RAID_ID + "_attacker")));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"MUSTER", "FIGHTING"})
  void terminalCleanupRemovesOnlyTheHistoricalRaidBands(String state) throws Exception {
    try (Context context = new Context()) {
      context.saveLegacy(CampaignRaidState.valueOf(state));
      context.load();
      CampaignRaid raid = context.raid();
      CampaignRaidService.endRaid(context.war, NOW);
      assertNull(context.war.getActiveCampaignRaid());
      assertEquals(CampaignRaidState.ENDED, raid.getState());
      assertNull(BattleManager.getByString(RAID_ID));
      assertNull(WarbandManager.getByString(PREFIX + "_attacker"));
      assertNull(WarbandManager.getByString(PREFIX + "_defender"));
      assertFalse(Files.exists(context.bandFile(PREFIX + "_attacker")));
      assertFalse(Files.exists(context.bandFile(PREFIX + "_defender")));
      assertFalse(Files.exists(context.files.root.resolve("Battles/battle_" + RAID_ID + ".json")));
      assertEquals(1, WarbandManager.get().size());
      context.assertUnrelatedManualBattle();
      assertArrayEquals(
          context.manualBytes, Files.readAllBytes(context.bandFile("unrelated_manual")));
    }
  }

  @ParameterizedTest
  @CsvSource({
    "def_port_raid, def_port_raid, Def Port Raid",
    "def_port_raid_w942711, def_port_raid, Def Port Raid",
    "def_port_raid_w942711_1791655260000, def_port_raid, Def Port Raid",
    "def_port_raid_w99, def_port_raid_w99, Def Port Raid",
    "def_port_raid_w942711_archive, def_port_raid_w942711_archive, Def Port Raid",
    "d_yar_raid_w942711, d_yar_raid, DIYAR Raid"
  })
  void legacyIdsRetainTheirHistoricalSlugAndStripOnlyTheirOwnCollisionSuffix(
      String historicalId, String expectedPrefix, String historicalDisplay) {
    CampaignRaid raid = fromJson(legacyJson(historicalId, historicalDisplay));
    assertEquals(expectedPrefix + "_attacker", CampaignRaidWarbandService.attackerWarbandId(raid));
    assertEquals(expectedPrefix + "_defender", CampaignRaidWarbandService.defenderWarbandId(raid));
    assertEquals(historicalId, raid.getId(), "Battle identity and file names do not change");
    assertEquals(
        expectedPrefix, savedPrefix(JsonUtil.GSON.toJsonTree(raid.toData()).getAsJsonObject()));
  }

  @Test
  void naturalTruncatedNameEndingInWarSuffixMustNotBeMistakenForACollision() {
    String naturalId = "a".repeat(45) + "_w1";
    JsonObject old = legacyJson(naturalId, "A".repeat(45) + " W1 Raid");
    old.addProperty("warId", 1);
    CampaignRaid raid = fromJson(old);
    assertEquals(naturalId + "_attacker", CampaignRaidWarbandService.attackerWarbandId(raid));
    assertEquals(naturalId, savedPrefix(JsonUtil.GSON.toJsonTree(raid.toData()).getAsJsonObject()));
  }

  @Test
  void unrecognizedLegacyDisplayNeverRedirectsToAnotherPrefix() {
    CampaignRaid raid = fromJson(legacyJson(RAID_ID, "Different Port Raid"));
    assertEquals(RAID_ID + "_attacker", CampaignRaidWarbandService.attackerWarbandId(raid));
    assertEquals(RAID_ID + "_defender", CampaignRaidWarbandService.defenderWarbandId(raid));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void missingLegacyDisplayConservativelyPreservesTheSavedId(String displayName) {
    CampaignRaid raid = fromJson(legacyJson(RAID_ID, displayName));
    assertEquals(RAID_ID + "_attacker", CampaignRaidWarbandService.attackerWarbandId(raid));
    assertEquals(RAID_ID + "_defender", CampaignRaidWarbandService.defenderWarbandId(raid));
  }

  @ParameterizedTest
  @ValueSource(strings = {"explicit_raid", "def_port_raid_w942711"})
  void explicitPrefixRemainsAuthoritativeAcrossDisplayAndIdChanges(String prefix) {
    JsonObject data = legacyJson(RAID_ID, "Def Port Raid");
    data.addProperty("warbandIdPrefix", prefix);
    CampaignRaid raid = fromJson(data);
    raid.setDisplayName("Another Port Raid");
    raid.setId("revised_battle_id");
    assertEquals(prefix + "_attacker", CampaignRaidWarbandService.attackerWarbandId(raid));
    CampaignRaid reloaded = fromJson(JsonUtil.GSON.toJsonTree(raid.toData()).getAsJsonObject());
    assertEquals(prefix + "_defender", CampaignRaidWarbandService.defenderWarbandId(reloaded));
    assertEquals("revised_battle_id", reloaded.getId());
  }

  @Test
  void blankStoredPrefixUsesLegacyMigrationOnce() {
    JsonObject old = legacyJson(RAID_ID, "Def Port Raid");
    old.addProperty("warbandIdPrefix", "  ");
    CampaignRaid raid = fromJson(old);
    assertEquals(PREFIX + "_attacker", CampaignRaidWarbandService.attackerWarbandId(raid));
    JsonObject persisted = JsonUtil.GSON.toJsonTree(raid.toData()).getAsJsonObject();
    assertEquals(PREFIX, savedPrefix(persisted));
    assertEquals(
        PREFIX + "_defender", CampaignRaidWarbandService.defenderWarbandId(fromJson(persisted)));
  }

  @Test
  void newSuffixedRaidKeepsItsNewIdentityAndNeverBorrowsDisplayNamedBands() throws Exception {
    try (Context context = new Context()) {
      CampaignRaid fresh = new CampaignRaid();
      fresh.setId(RAID_ID);
      fresh.setDisplayName("Def Port Raid");
      fresh.setWarId(WAR_ID);
      fresh.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
      fresh.setState(CampaignRaidState.MUSTER);
      fresh.setBattleDay(DAY);
      context.war.setActiveCampaignRaid(fresh);
      Warband unrelated =
          Warband.createWithMemberIds(PREFIX + "_attacker", context.outsider.getUniqueId(), true);
      WarbandManager.addWarband(unrelated);
      CampaignRaidWarbandService.createRaidWarbands(context.war, fresh);
      Warband own = CampaignRaidWarbandService.getAttackerWarband(fresh);
      assertNotNull(own);
      assertEquals(RAID_ID + "_attacker", own.getId());
      assertNotSame(unrelated, own);
      assertFalse(own.hasMember(context.outsider.getUniqueId()));
      JsonObject persisted = JsonUtil.GSON.toJsonTree(fresh.toData()).getAsJsonObject();
      assertEquals(RAID_ID, savedPrefix(persisted));
      CampaignRaid restored = fromJson(persisted);
      assertSame(own, CampaignRaidWarbandService.getAttackerWarband(restored));
      CampaignRaidWarbandService.destroyRaidWarbands(context.war, restored);
      assertSame(unrelated, WarbandManager.getByString(PREFIX + "_attacker"));
      assertEquals(Set.of(context.outsider.getUniqueId()), unrelated.getMemberIds());
      assertNull(WarbandManager.getByString(RAID_ID + "_attacker"));
    }
  }

  private static CampaignRaid fromJson(JsonObject json) {
    return CampaignRaid.fromData(JsonUtil.GSON.fromJson(json, CampaignRaidData.class));
  }

  private static JsonObject legacyJson(String id, String displayName) {
    JsonObject data = new JsonObject();
    data.addProperty("id", id);
    data.addProperty("displayName", displayName);
    data.addProperty("warId", WAR_ID);
    data.addProperty("attackerCoalition", "AGGRESSOR");
    assertFalse(data.has("warbandIdPrefix"));
    return data;
  }

  private static String savedPrefix(JsonObject data) {
    assertTrue(
        data.has("warbandIdPrefix"), "The migrated identity must be explicit in the saved raid");
    return data.get("warbandIdPrefix").getAsString();
  }

  private static final class Context implements AutoCloseable {
    final PersistenceFilesFixture files = new PersistenceFilesFixture();
    final FactionDomainFixture domain = new FactionDomainFixture();
    final MockedStatic<CampaignClock> clock = mockStatic(CampaignClock.class);
    final List<Battle> oldBattles = new ArrayList<>(BattleManager.get());
    final List<Warband> oldBands = new ArrayList<>(WarbandManager.get());
    final Map<Player, Battle> oldEditors = new HashMap<>(BattleManager.currentBattle);
    final Map<Player, String> oldSideEditors = new HashMap<>(BattleManager.currentSideEdit);
    final List<Runnable> restorations = new ArrayList<>();
    final int oldDuration = Cache.campaignRaidDurationSeconds;
    final Player alice = domain.player("Alice");
    final Player ann = domain.player("Ann");
    final Player bob = domain.player("Bob");
    final Player outsider = domain.player("Unenrolled");
    final Player manualLeader = domain.player("Manual");
    final Faction attacker = domain.saved("legacy_raid_attackers", "Alice");
    final Faction defender = domain.saved("legacy_raid_defenders", "Bob");
    War war;
    byte[] manualBytes;

    Context() throws Exception {
      snapshotMap(CampaignRaidMusterScheduler.class, "scheduledWarTasks");
      snapshotMap(CampaignRaidMusterReminderService.class, "scheduledReminderTasks");
      snapshotMap(CampaignRaidFightScheduler.class, "scheduledWarTasks");
      snapshotMap(CampaignRaidBossBarService.class, "BARS");
      snapshotMap(RaidAttackerEliminationService.class, "OUT_ATTACKERS");
      snapshotMap(BattleCasualtyLedger.class, "CASUALTIES_BY_BATTLE");
      snapshotCollection(
          Class.forName(
              "net.tfminecraft.simplefactions.war.battle.engine.core.BattleRespawnRouting"),
          "jailRespawns");
      BattleManager.resetForTests();
      WarbandManager.resetForTests();
      clock.when(CampaignClock::now).thenReturn(NOW);
      clock.when(CampaignClock::isSpoofed).thenReturn(true);
      Cache.campaignRaidDurationSeconds = 600;
      when(Bukkit.getPlayer(any(UUID.class)))
          .thenAnswer(
              call ->
                  domain.online.values().stream()
                      .filter(player -> player.getUniqueId().equals(call.getArgument(0)))
                      .findFirst()
                      .orElse(null));
      when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
          .thenAnswer(call -> mock(BossBar.class));
      for (Player player : domain.online.values())
        when(player.getScoreboard()).thenReturn(mock(Scoreboard.class));
      attacker.addMember("Ann");
      attacker.addMember("Unenrolled");
      attacker
          .getInstallationHandler()
          .acceptTransferred(
              new Installation("source", "Source Port", InstallationKind.PORT, 10, 100, 100, 1L));
      defender
          .getInstallationHandler()
          .acceptTransferred(
              new Installation("target", "Def Port", InstallationKind.PORT, 20, 200, 200, 2L));
      war = new War(WAR_ID, attacker, defender);
      war.setGoal(WarGoalType.SUBJUGATE);
      war.setWarType(WarType.SUBJUGATE);
      war.setBattleDay(DAY);
      war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
      war.setOccupiedByAttacker(new ArrayList<>(List.of(10)));
      war.setOccupiedByDefender(new ArrayList<>(List.of(20)));
      WarManager.addWar(war);
    }

    void saveLegacy(CampaignRaidState state) throws Exception {
      CampaignRaid old = new CampaignRaid();
      old.setId(RAID_ID);
      old.setDisplayName("Def Port Raid");
      old.setWarId(WAR_ID);
      old.setBattleDay(DAY);
      old.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
      old.setLauncherFactionId(attacker.getId());
      old.setSourceInstallationId("source");
      old.setTargetInstallationId("target");
      old.setState(state);
      old.setMusterEndsAt(NOW.plusSeconds(120));
      if (state == CampaignRaidState.FIGHTING) {
        old.setFightEndsAt(NOW.plusSeconds(600));
        old.setBattleId(RAID_ID);
      }
      war.setActiveCampaignRaid(old);
      JsonObject legacy = JsonUtil.GSON.toJsonTree(WarMapper.toData(war)).getAsJsonObject();
      legacy.getAsJsonObject("activeCampaignRaid").remove("warbandIdPrefix");
      files.write("Wars/war_" + WAR_ID + ".json", JsonUtil.GSON.toJson(legacy));
      Warband attackers =
          Warband.createRaidShell(PREFIX + "_attacker", war.getAttackers(), "attacker");
      attackers.setLeaderId(alice.getUniqueId());
      attackers.addMember(alice.getUniqueId());
      attackers.addMember(ann.getUniqueId());
      Warband defenders =
          Warband.createRaidShell(PREFIX + "_defender", war.getDefenders(), "defender");
      defenders.setLeaderId(bob.getUniqueId());
      defenders.addMember(bob.getUniqueId());
      Database database = new Database();
      database.saveWarband(attackers);
      database.saveWarband(defenders);
      if (state == CampaignRaidState.FIGHTING) {
        Battle saved = BattleFactory.createBlank(BattleType.RAID, RAID_ID);
        saved.setWarId(WAR_ID);
        saved.setCampaignRaid(true);
        saved.setProvinceId(20);
        saved.setDisplayName("Def Port Raid");
        saved.setStarted(true);
        saved.setStartedAt(NOW.minusSeconds(60));
        saved.getSideById("attacker").addBand(attackers);
        saved.getSideById("defender").addBand(defenders);
        for (var side : saved.getSides()) {
          side.setSpawn(new Location(domain.ui.world, 200, 64, 200));
          side.setJail(new Location(domain.ui.world, 400, 64, 400));
        }
        database.saveBattle(saved);
      }
      Warband manual =
          Warband.createWithMemberIds("unrelated_manual", manualLeader.getUniqueId(), true);
      Battle manualBattle = BattleFactory.createBlank(BattleType.FIELD, "unrelated_manual_battle");
      manualBattle.getSideById("attacker").addBand(manual);
      database.saveWarband(manual);
      database.saveBattle(manualBattle);
      manualBytes = Files.readAllBytes(bandFile(manual.getId()));
    }

    void load() {
      WarManager.start();
      war = WarManager.getById(WAR_ID);
      assertNotNull(war);
      BattlePersistenceService.loadAll();
      assertNotNull(raid());
    }

    CampaignRaid raid() {
      return war.getActiveCampaignRaid();
    }

    void assertBands() {
      Warband attackers = CampaignRaidWarbandService.getAttackerWarband(raid());
      Warband defenders = CampaignRaidWarbandService.getDefenderWarband(raid());
      assertNotNull(attackers);
      assertNotNull(defenders);
      assertEquals(PREFIX + "_attacker", attackers.getId());
      assertEquals(PREFIX + "_defender", defenders.getId());
      assertEquals(alice.getUniqueId(), attackers.getLeaderId());
      assertEquals(bob.getUniqueId(), defenders.getLeaderId());
      assertEquals(Set.of(alice.getUniqueId(), ann.getUniqueId()), attackers.getMemberIds());
      assertEquals(Set.of(bob.getUniqueId()), defenders.getMemberIds());
      assertSame(attackers, WarbandManager.getByString(PREFIX + "_attacker"));
      assertSame(defenders, WarbandManager.getByString(PREFIX + "_defender"));
      assertNull(WarbandManager.getByString(RAID_ID + "_attacker"));
      assertNull(WarbandManager.getByString(RAID_ID + "_defender"));
      assertEquals(3, WarbandManager.get().size());
    }

    void assertRoster(Battle battle) {
      assertNotNull(battle);
      assertBands();
      assertEquals(
          List.of(CampaignRaidWarbandService.getAttackerWarband(raid())),
          battle.getSideById("attacker").getBands());
      assertEquals(
          List.of(CampaignRaidWarbandService.getDefenderWarband(raid())),
          battle.getSideById("defender").getBands());
    }

    void assertUnrelatedManualBattle() {
      Battle manual = BattleManager.getByString("unrelated_manual_battle");
      assertNotNull(manual);
      assertNull(manual.getWarId());
      Warband band = WarbandManager.getByString("unrelated_manual");
      assertNotNull(band);
      assertFalse(band.isFaction());
      assertEquals(Set.of(manualLeader.getUniqueId()), band.getMemberIds());
      assertEquals(List.of(band), manual.getSideById("attacker").getBands());
    }

    Path warFile() {
      return files.root.resolve("Wars/war_" + WAR_ID + ".json");
    }

    Path bandFile(String id) {
      return files.root.resolve("Warbands/warband_" + id + ".json");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void snapshotMap(Class<?> owner, String fieldName) throws Exception {
      Field field = owner.getDeclaredField(fieldName);
      field.setAccessible(true);
      Map target = (Map) field.get(null), old = new LinkedHashMap(target);
      target.clear();
      restorations.add(
          () -> {
            target.clear();
            target.putAll(old);
          });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void snapshotCollection(Class<?> owner, String fieldName) throws Exception {
      Field field = owner.getDeclaredField(fieldName);
      field.setAccessible(true);
      Collection target = (Collection) field.get(null), old = new ArrayList(target);
      target.clear();
      restorations.add(
          () -> {
            target.clear();
            target.addAll(old);
          });
    }

    @Override
    public void close() throws Exception {
      try {
        CampaignRaidBossBarService.resetForTests();
        BattleManager.resetForTests();
        WarbandManager.resetForTests();
        BattleManager.get().addAll(oldBattles);
        WarbandManager.get().addAll(oldBands);
        BattleManager.currentBattle.putAll(oldEditors);
        BattleManager.currentSideEdit.putAll(oldSideEditors);
        for (Runnable restore : restorations) restore.run();
        Cache.campaignRaidDurationSeconds = oldDuration;
        clock.close();
        domain.close();
      } finally {
        files.close();
      }
    }
  }
}
