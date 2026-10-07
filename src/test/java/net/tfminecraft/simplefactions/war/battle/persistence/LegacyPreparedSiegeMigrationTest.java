package net.tfminecraft.simplefactions.war.battle.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleLaunchService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleRosterService;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.engine.win.FieldWinService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;
import net.tfminecraft.simplefactions.war.battle.template.ContestArea;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.zoc.FortZocIndex;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/** A migrated war must not silently reuse staff placement from its former battle province. */
class LegacyPreparedSiegeMigrationTest {
  private static final Instant DUE = Instant.parse("2026-10-10T19:00:00Z");
  private static final int AXIS = 31;
  private static final int FORT_HOME = 20;

  @ParameterizedTest
  @ValueSource(strings = {"province", "type"})
  void preparationRejectsTheOldPlacementWithoutReplacingItsSavedBattleOrRosters(String mismatch)
      throws Exception {
    try (Context context = new Context(mismatch, false)) {
      assertNull(CampaignBattleLaunchService.prepareScheduledBattle(context.war));
      assertTrue(
          context.messages.stream().anyMatch(LegacyPreparedSiegeMigrationTest::mentionsReset));
      context.assertPreserved(false);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"province", "type"})
  void directStartRejectsTheOldPlacementWithAnExplicitResetInstruction(String mismatch)
      throws Exception {
    try (Context context = new Context(mismatch, false)) {
      String error = CampaignBattleLaunchService.startPreparedBattle(context.war, context.battle);
      assertNotNull(error);
      assertTrue(mentionsReset(error), error);
      context.assertPreserved(false);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"province", "type"})
  void scheduledStartCannotBypassTheMismatchGuardWhenTheSavedBattleAlreadyExists(String mismatch)
      throws Exception {
    try (Context context = new Context(mismatch, false)) {
      assertFalse(CampaignBattleLaunchService.tryStartScheduledBattle(context.war, DUE));
      assertTrue(
          context.messages.stream().anyMatch(LegacyPreparedSiegeMigrationTest::mentionsReset));
      context.assertPreserved(false);
    }
  }

  @Test
  void anAlreadyStartedSiegeRetainsItsOriginalGeometryAndRosterAfterWarMigration()
      throws Exception {
    try (Context context = new Context("province", true)) {
      assertSame(context.battle, CampaignBattleLaunchService.prepareScheduledBattle(context.war));
      assertEquals(
          "Battle already started.",
          CampaignBattleLaunchService.startPreparedBattle(context.war, context.battle));
      context.assertPreserved(true);
      assertEquals(DUE.minusSeconds(90), context.battle.getStartedAt());
    }
  }

  @Test
  void aCorrectlyPlacedSavedSiegeStillReusesItsRosterAndStartsNormally() throws Exception {
    try (Context context = new Context("matching", false)) {
      assertSame(context.battle, CampaignBattleLaunchService.prepareScheduledBattle(context.war));
      assertTrue(CampaignBattleLaunchService.tryStartScheduledBattle(context.war, DUE));
      assertTrue(context.battle.hasStarted());
      assertTrue(context.war.hasFirstBattleStarted());
      assertEquals(FORT_HOME, context.battle.getProvinceId());
      assertEquals(BattleType.SIEGE, context.battle.getBattleType());
      context.assertRosters();
      assertEquals(context.savedGeometry, context.geometry());
      BattlePersistenceService.saveAll();
      assertTrue(Files.exists(context.battleFile));
      BattlePersistenceService.loadAll();
      Battle reloaded = BattleManager.getByWarId(context.war.getId());
      assertNotNull(reloaded);
      assertTrue(reloaded.hasStarted());
      assertEquals(FORT_HOME, reloaded.getProvinceId());
      assertEquals(context.savedGeometry, geometry(reloaded));
      assertEquals(
          List.of(context.alice.getUniqueId()),
          new ArrayList<>(
              CampaignBattleRosterService.getCampaignWarband(reloaded, "attacker").getMemberIds()));
    }
  }

  private static boolean mentionsReset(String message) {
    return message != null && message.toLowerCase(Locale.ROOT).contains("reset");
  }

  private static JsonObject geometry(Battle battle) {
    JsonObject state = JsonUtil.GSON.toJsonTree(BattleMapper.toData(battle)).getAsJsonObject();
    JsonObject geometry = new JsonObject();
    for (String key : List.of("provinceId", "battleType", "contestMin", "contestMax", "points"))
      geometry.add(key, state.get(key));
    for (int index = 0; index < state.getAsJsonArray("sides").size(); index++) {
      JsonObject side = state.getAsJsonArray("sides").get(index).getAsJsonObject();
      for (String key : List.of("spawn", "jail", "respawnPoints"))
        geometry.add("side" + index + key, side.get(key));
    }
    return geometry;
  }

  private static final class Context implements AutoCloseable {
    final PersistenceFilesFixture files = new PersistenceFilesFixture();
    final FactionDomainFixture domain = new FactionDomainFixture();
    final List<Battle> previousBattles = new ArrayList<>(BattleManager.get());
    final List<Warband> previousWarbands = new ArrayList<>(WarbandManager.get());
    final Map<Player, Battle> previousEditors = new HashMap<>(BattleManager.currentBattle);
    final Map<Player, String> previousSideEditors = new HashMap<>(BattleManager.currentSideEdit);
    final MockedStatic<CampaignClock> clock = mockStatic(CampaignClock.class, CALLS_REAL_METHODS);
    final List<String> messages = new ArrayList<>();
    final Logger logger = Logger.getAnonymousLogger();
    final Player alice;
    final Player bob;
    final War war;
    final Battle battle;
    final Path battleFile;
    final JsonObject savedGeometry;
    final JsonObject loadedState;
    final byte[] savedBattle;
    final Map<String, byte[]> savedBands = new HashMap<>();
    final int previousLives = Cache.warBattleLivesPerRegiment;
    final int previousMinimumLives = Cache.warBattleMinSideLives;

    Context(String mismatch, boolean started) throws Exception {
      BattleManager.resetForTests();
      WarbandManager.resetForTests();
      Cache.warBattleLivesPerRegiment = 4;
      Cache.warBattleMinSideLives = 1;
      clock.when(CampaignClock::now).thenReturn(DUE);
      logger.setUseParentHandlers(false);
      logger.addHandler(
          new Handler() {
            @Override
            public void publish(LogRecord record) {
              messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          });
      when(domain.ui.plugin.getLogger()).thenReturn(logger);
      when(Bukkit.getLogger()).thenReturn(logger);
      when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
          .thenAnswer(call -> mock(BossBar.class));
      alice = domain.player("Alice");
      bob = domain.player("Bob");
      when(Bukkit.getPlayer(any(UUID.class)))
          .thenAnswer(call -> call.getArgument(0).equals(alice.getUniqueId()) ? alice : bob);
      Faction attacker = domain.saved("prepared_attacker", "Alice");
      Faction defender = domain.saved("prepared_defender", "Bob");
      army(attacker);
      army(defender);
      defender
          .getInstallationHandler()
          .acceptTransferred(
              new Installation("keep", "Keep", InstallationKind.FORT, FORT_HOME, 200, 200, 100L));
      province(defender, FORT_HOME, AXIS);
      province(defender, AXIS, FORT_HOME);
      assertEquals(
          FORT_HOME, FortZocIndex.fromGameState().fortForProvince(AXIS).orElseThrow().province());
      War original = new War(990340, attacker, defender);
      original.setGoal(WarGoalType.WAR);
      original.setCampaignProvinces(List.of(10, AXIS, 40));
      original.setObjectiveProvinceId(40);
      original.setCursorIndex(0);
      original.setInitiativeAttacker(5);
      original.setInitiativeDefender(5);
      original.setInitiativeHolder(BelligerentRole.ATTACKER);
      original.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
      original.setCampaignBattleSchedule(
          List.of(new ScheduledCampaignBattle(AXIS, CampaignBattleKind.SIEGE, true, "keep")));
      original.setCampaignScheduleIndex(0);
      original.setScheduledBattleProvinceId(AXIS);
      original.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
      original.setScheduledBattleAt(DUE);
      original.setFirstBattleStarted(started);
      JsonObject oldWar = JsonUtil.GSON.toJsonTree(WarMapper.toData(original)).getAsJsonObject();
      oldWar.addProperty("installationReferenceVersion", 0);
      files.write("Wars/war_" + original.getId() + ".json", JsonUtil.GSON.toJson(oldWar));

      int savedProvince = mismatch.equals("province") ? AXIS : FORT_HOME;
      Battle saved =
          BattleFactory.createBlank(
              mismatch.equals("type") ? BattleType.FIELD : BattleType.SIEGE,
              "legacy_prepared_siege");
      saved.setWarId(original.getId());
      saved.setProvinceId(savedProvince);
      saved.setLocked(false);
      saved.setTeleport(false);
      saved.setContestDurationSeconds(600);
      saved.setContestHoldRemainingSeconds(600);
      saved.setContestArea(
          new ContestArea(
              new BattleLocation("world", savedProvince * 10, 50, 100, 0, 0),
              new BattleLocation("world", savedProvince * 10 + 20, 80, 120, 0, 0)));
      for (BattleSide side : saved.getSides()) {
        side.setSpawn(new Location(domain.ui.world, savedProvince * 10, 64, 100));
        side.setJail(new Location(domain.ui.world, savedProvince * 10 + 50, 64, 100));
        side.getRespawnPoints().add(new Location(domain.ui.world, savedProvince * 10 + 2, 64, 100));
        Warband band =
            Warband.createCampaignSideShell(
                "legacy_siege_" + side.getId(),
                original,
                side.getId().equals("attacker") ? original.getAttackers() : original.getDefenders(),
                side.getId());
        band.addPlayer(side.getId().equals("attacker") ? alice : bob);
        side.addBand(band);
        new Database().saveWarband(band);
      }
      saved.addPoint(
          new CapturePoint(
              "courtyard",
              new Location(domain.ui.world, savedProvince * 10 + 5, 64, 105),
              saved.getSideById("defender"),
              100));
      saved.setStarted(started);
      if (started) saved.setStartedAt(DUE.minusSeconds(90));
      new Database().saveBattle(saved);
      WarManager.start();
      war = WarManager.getById(original.getId());
      assertNotNull(war);
      assertEquals(FORT_HOME, war.getScheduledBattleProvinceId());
      assertEquals(FORT_HOME, war.getCampaignBattleSchedule().getFirst().provinceId());
      assertEquals(AXIS, war.getCampaignBattleSchedule().getFirst().chronologyProvinceId());
      BattlePersistenceService.loadAll();
      battle = BattleManager.getByWarId(war.getId());
      assertNotNull(battle);
      assertEquals(savedProvince, battle.getProvinceId());
      battleFile = files.root.resolve("Battles/battle_" + battle.getId() + ".json");
      savedBattle = Files.readAllBytes(battleFile);
      savedGeometry = geometry();
      loadedState = JsonUtil.GSON.toJsonTree(BattleMapper.toData(battle)).getAsJsonObject();
      JsonObject normalizedSave = JsonUtil.readJson(battleFile.toFile(), JsonObject.class);
      // Old prepared sides saved a zero maximum before the first life allocation. Loading
      // restores their actual 25-life maximum; no placement or roster field may change.
      for (var element : normalizedSave.getAsJsonArray("sides")) {
        JsonObject side = element.getAsJsonObject();
        if (side.get("maxLives").getAsInt() == 0) {
          assertEquals(25, side.get("lives").getAsInt());
          side.addProperty("maxLives", 25);
        }
      }
      assertEquals(loadedState, normalizedSave, "Only the legacy maximum-life default is restored");
      for (Warband band : WarbandManager.get()) {
        savedBands.put(band.getId(), Files.readAllBytes(bandFile(band.getId())));
      }
      assertRosters();
      messages.clear();
    }

    private void army(Faction faction) {
      YamlConfiguration config = new YamlConfiguration();
      config.set("item.material", "PAPER");
      config.set("offense", true);
      Regiment regiment = new Regiment("professional", config);
      faction.getMilitary().getRegiments().add(regiment);
      assertTrue(faction.getMilitary().adminAdjustSlots(regiment.getId(), 2).allowed());
    }

    private void province(Faction owner, int id, Integer... neighbors) {
      Province province = mock(Province.class);
      when(province.getId()).thenReturn(id);
      when(province.isValid()).thenReturn(true);
      when(province.isSea()).thenReturn(false);
      when(province.getOwner()).thenReturn(owner);
      when(province.getNeighbours()).thenReturn(Set.of(neighbors));
      domain.provinceData.put(id, province);
    }

    JsonObject geometry() {
      return LegacyPreparedSiegeMigrationTest.geometry(battle);
    }

    Path bandFile(String id) {
      return files.root.resolve("Warbands/warband_" + id + ".json");
    }

    void assertRosters() {
      assertEquals(2, WarbandManager.get().size());
      for (String side : List.of("attacker", "defender")) {
        Warband band = CampaignBattleRosterService.getCampaignWarband(battle, side);
        assertNotNull(band);
        assertEquals("legacy_siege_" + side, band.getId());
        assertEquals(
            List.of(side.equals("attacker") ? alice.getUniqueId() : bob.getUniqueId()),
            new ArrayList<>(band.getMemberIds()));
        assertSame(band, WarbandManager.getByString(band.getId()));
      }
    }

    void assertPreserved(boolean started) throws Exception {
      assertSame(battle, BattleManager.getByWarId(war.getId()));
      assertEquals(1, BattleManager.get().size());
      assertEquals(started, battle.hasStarted());
      assertEquals(started, war.hasFirstBattleStarted());
      assertTrue(war.isActive());
      assertEquals(savedGeometry, geometry());
      assertRosters();
      assertArrayEquals(savedBattle, Files.readAllBytes(battleFile));
      BattlePersistenceService.saveAll();
      assertEquals(
          loadedState,
          JsonUtil.readJson(battleFile.toFile(), JsonObject.class),
          "Autosave preserves the complete loaded state, including geometry and rosters");
      for (var entry : savedBands.entrySet())
        assertArrayEquals(entry.getValue(), Files.readAllBytes(bandFile(entry.getKey())));
    }

    @Override
    public void close() throws Exception {
      try {
        BattlePersistenceService.stopAutosave();
        for (Battle current : BattleManager.get()) FieldWinService.clearEmptySideTracking(current);
        BattleManager.resetForTests();
        WarbandManager.resetForTests();
        BattleManager.get().addAll(previousBattles);
        WarbandManager.get().addAll(previousWarbands);
        BattleManager.currentBattle.putAll(previousEditors);
        BattleManager.currentSideEdit.putAll(previousSideEditors);
        Cache.warBattleLivesPerRegiment = previousLives;
        Cache.warBattleMinSideLives = previousMinimumLives;
        clock.close();
        domain.close();
      } finally {
        files.close();
      }
    }
  }
}
