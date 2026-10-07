package net.tfminecraft.simplefactions.war.battle.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.simplefactions.database.*;
import net.tfminecraft.simplefactions.testsupport.*;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.core.*;
import net.tfminecraft.simplefactions.war.battle.enums.*;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.*;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattlePersistenceLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private PersistenceFilesFixture files;
  private List<Battle> priorBattles;
  private List<Warband> priorWarbands;

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    fixture = new FactionDomainFixture();
    priorBattles = new ArrayList<>(BattleManager.get());
    priorWarbands = new ArrayList<>(WarbandManager.get());
    BattleManager.resetForTests();
    WarbandManager.resetForTests();
    when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
        .thenAnswer(c -> mock(BossBar.class));
  }

  @AfterEach
  void close() throws Exception {
    BattlePersistenceService.stopAutosave();
    BattleManager.resetForTests();
    WarbandManager.resetForTests();
    BattleManager.get().addAll(priorBattles);
    WarbandManager.get().addAll(priorWarbands);
    fixture.close();
    files.close();
  }

  private Battle battle(String id) {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, id);
    battle.setLocked(false);
    BattleManager.addBattle(battle);
    return battle;
  }

  private Warband band(Battle battle, String id) {
    Warband band = Warband.createWithMemberIds(id, UUID.randomUUID(), false);
    WarbandManager.addWarband(band);
    assertNull(BattleJoinService.join(band, battle, BattleTemplate.ATTACKER_SIDE));
    return band;
  }

  @ParameterizedTest
  @ValueSource(strings = {"Battles", "Warbands"})
  void autosaveIgnoresJsonFilesItDoesNotOwn(String directory) throws Exception {
    Battle battle = battle("active");
    Warband band = band(battle, "fighters");
    Path notes = files.write(directory + "/notes.json", "{\"note\":\"preserve me\"}");
    assertDoesNotThrow(BattlePersistenceService::saveAll);
    assertEquals("{\"note\":\"preserve me\"}", Files.readString(notes));
    assertTrue(Files.exists(files.root.resolve("Battles/battle_active.json")));
    assertTrue(Files.exists(files.root.resolve("Warbands/warband_fighters.json")));
    assertSame(band, WarbandManager.getByString("fighters"));
  }

  @Test
  void loadIgnoresForeignJsonEvenWhenItLooksLikeABattle() throws Exception {
    Battle active = battle("active");
    active.setStarted(true);
    new Database().saveBattle(active);
    files.write("Battles/notes.json", "{\"id\":\"foreign\",\"started\":true}");
    BattlePersistenceService.loadAll();
    assertEquals(1, BattleManager.get().size());
    assertNotNull(BattleManager.getByString("active"));
    assertNull(BattleManager.getByString("foreign"));
    assertTrue(Files.exists(files.root.resolve("Battles/notes.json")));
  }

  @Test
  void malformedBattleFilesDoNotHideAValidSiblingAndUnknownReferencesAreSkipped() throws Exception {
    Battle active = battle("active");
    active.setSequentialCapture(true);
    active.addPoint(
        new CapturePoint(
            "one", new Location(fixture.ui.world, 0, 64, 0), active.getSideById("defender"), 100));
    active.addPoint(
        new CapturePoint(
            "two", new Location(fixture.ui.world, 10, 64, 0), active.getSideById("defender"), 100));
    Warband band = band(active, "fighters");
    BattleData data = BattleMapper.toData(active);
    data.sides.add(null);
    data.sides.add(new BattleSideData());
    data.sides.get(0).warbandIds.add("deleted");
    JsonUtil.writeJson(files.root.resolve("Battles/battle_active.json").toFile(), data);
    files.write("Battles/battle_empty.json", "null");
    files.write("Battles/battle_missing_id.json", "{}");
    files.write("Battles/battle_blank_id.json", "{\"id\":\" \"}");
    files.write("Battles/battle_malformed.json", "{bad");
    new Database().saveWarband(band);
    BattlePersistenceService.loadAll();
    Battle loaded = BattleManager.getByString("active");
    assertNotNull(loaded);
    assertEquals(2, loaded.getPoints().size());
    assertEquals(1, loaded.getSideById("attacker").getBands().size());
    assertEquals("fighters", loaded.getSideById("attacker").getBands().getFirst().getId());
    assertTrue(Files.exists(files.root.resolve("Battles/battle_malformed.json")));
  }

  @Test
  void autosaveSchedulesOneTaskCancelsTheOldOneAndPersistsCurrentState() {
    var tasks = new ArrayList<BukkitTask>();
    var callbacks = new ArrayList<Runnable>();
    when(fixture.ui.scheduler.runTaskTimer(
            eq(fixture.ui.plugin), any(Runnable.class), eq(1200L), eq(1200L)))
        .thenAnswer(
            c -> {
              callbacks.add(c.getArgument(1));
              BukkitTask task = mock(BukkitTask.class);
              tasks.add(task);
              return task;
            });
    Battle active = battle("timer");
    band(active, "fighters");
    BattlePersistenceService.startAutosave();
    BattlePersistenceService.startAutosave();
    verify(tasks.get(0)).cancel();
    verify(tasks.get(1), never()).cancel();
    callbacks.get(1).run();
    assertTrue(Files.exists(files.root.resolve("Battles/battle_timer.json")));
    BattlePersistenceService.stopAutosave();
    BattlePersistenceService.stopAutosave();
    verify(tasks.get(1), times(1)).cancel();
  }

  @Test
  void deleteManualBattleDeletesItsRosterButKeepsAnUnrelatedCampaign() throws Exception {
    Battle manual = battle("manual");
    Warband band = band(manual, "fighters");
    var attacker = fixture.saved("attacker", "Alice");
    var defender = fixture.saved("defender", "Bob");
    net.tfminecraft.simplefactions.managers.WarManager.addWar(
        new net.tfminecraft.simplefactions.war.core.War(23, attacker, defender));
    Battle campaign = battle("campaign");
    campaign.setWarId(23);
    BattlePersistenceService.saveAll();
    BattlePersistenceService.deleteManualBattle(manual);
    assertNull(BattleManager.getByString("manual"));
    assertNull(WarbandManager.getByString("fighters"));
    assertSame(campaign, BattleManager.getByString("campaign"));
    assertFalse(Files.exists(files.root.resolve("Battles/battle_manual.json")));
    assertFalse(Files.exists(files.root.resolve("Warbands/warband_fighters.json")));
  }

  @Test
  void deletingAManualBattlePreservesAStoredBandAlsoReferencedByCampaign() throws Exception {
    var attacker = fixture.saved("attacker", "Alice");
    var defender = fixture.saved("defender", "Bob");
    net.tfminecraft.simplefactions.managers.WarManager.addWar(
        new net.tfminecraft.simplefactions.war.core.War(23, attacker, defender));
    Battle manual = battle("manual");
    Warband shared = band(manual, "shared");
    Battle campaign = battle("campaign");
    campaign.setWarId(23);
    // Older persisted records may reference one band from both battles. Load the
    // supported on-disk shape and preserve every reference that survives deletion.
    BattleData campaignData = BattleMapper.toData(campaign);
    campaignData.sides.getFirst().warbandIds.add(shared.getId());
    new Database().saveBattle(manual);
    new Database().saveWarband(shared);
    JsonUtil.writeJson(files.root.resolve("Battles/battle_campaign.json").toFile(), campaignData);
    BattlePersistenceService.loadAll();
    Battle loadedManual = BattleManager.getByString("manual");
    Battle loadedCampaign = BattleManager.getByString("campaign");
    assertNotNull(loadedManual);
    assertNotNull(loadedCampaign);
    Warband restored = WarbandManager.getByString("shared");
    assertNotNull(restored);
    assertTrue(loadedCampaign.getSideById("attacker").getBands().contains(restored));
    BattlePersistenceService.deleteManualBattle(loadedManual);
    assertSame(restored, WarbandManager.getByString("shared"));
    assertTrue(Files.exists(files.root.resolve("Warbands/warband_shared.json")));
    BattlePersistenceService.saveAll();
    BattlePersistenceService.loadAll();
    assertNotNull(WarbandManager.getByString("shared"));
  }

  @Test
  void staleKnownFilesArePrunedAndUnreferencedManualBandsAreRemoved() throws Exception {
    Battle active = battle("active");
    Warband referenced = band(active, "fighters");
    Warband orphan = Warband.createWithMemberIds("orphan", UUID.randomUUID(), false);
    WarbandManager.addWarband(orphan);
    new Database().saveWarband(orphan);
    files.write("Battles/battle_stale.json", "{}");
    files.write("Warbands/warband_stale.json", "{}");
    files.write("Battles/readme.txt", "keep");
    files.write("Warbands/readme.txt", "keep");
    files.write("Battles/battle_active.json.bak", "battle backup");
    files.write("Warbands/warband_fighters.json.bak", "roster backup");
    BattlePersistenceService.saveAll();
    assertNull(WarbandManager.getByString("orphan"));
    assertSame(referenced, WarbandManager.getByString("fighters"));
    for (String path :
        List.of(
            "Battles/battle_stale.json",
            "Warbands/warband_stale.json",
            "Warbands/warband_orphan.json")) assertFalse(Files.exists(files.root.resolve(path)));
    assertEquals("keep", Files.readString(files.root.resolve("Battles/readme.txt")));
    assertEquals("keep", Files.readString(files.root.resolve("Warbands/readme.txt")));
    assertEquals(
        "battle backup", Files.readString(files.root.resolve("Battles/battle_active.json.bak")));
    assertEquals(
        "roster backup",
        Files.readString(files.root.resolve("Warbands/warband_fighters.json.bak")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"sides", "points", "respawnPoints", "warbandIds"})
  void omittedOptionalCollectionsDoNotDiscardAPersistedBattle(String field) throws Exception {
    String side = "{\"id\":\"attacker\",\"" + field + "\":null}";
    String json = "{\"id\":\"legacy\",\"sides\":[" + side + "],\"" + field + "\":null}";
    files.write("Battles/battle_legacy.json", json);
    BattlePersistenceService.loadAll();
    assertNotNull(BattleManager.getByString("legacy"));
    assertTrue(Files.exists(files.root.resolve("Battles/battle_legacy.json")));
  }

  @Test
  void savedEnumModesDoNotDependOnTheServersLocale() throws Exception {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      files.write(
          "Battles/battle_individual.json",
          "{\"id\":\"individual\",\"lifeType\":\"individual\",\"defenderRespawnMode\":\"infinite\"}");
      BattlePersistenceService.loadAll();
      assertEquals(
          DefenderRespawnMode.INFINITE,
          BattleManager.getByString("individual").getDefenderRespawnMode());
    } finally {
      Locale.setDefault(original);
    }
  }

  @Test
  void loadingRemovesAnOrphanCampaignAndKeepsAManualBattle() {
    Battle orphan = battle("orphan");
    orphan.setWarId(999);
    new Database().saveBattle(orphan);
    Battle manual = battle("manual");
    new Database().saveBattle(manual);
    BattlePersistenceService.loadAll();
    assertNull(BattleManager.getByString("orphan"));
    assertNotNull(BattleManager.getByString("manual"));
    assertFalse(Files.exists(files.root.resolve("Battles/battle_orphan.json")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void duplicateCampaignsKeepOneMatchingSavedBattle(boolean sameId) {
    var attacker = fixture.saved("attacker", "Alice");
    var defender = fixture.saved("defender", "Bob");
    net.tfminecraft.simplefactions.managers.WarManager.addWar(
        new net.tfminecraft.simplefactions.war.core.War(23, attacker, defender));
    Battle first = battle("campaign");
    first.setWarId(23);
    new Database().saveBattle(first);
    Battle second = battle(sameId ? "CAMPAIGN" : "duplicate");
    second.setWarId(23);
    new Database().saveBattle(second);
    BattlePersistenceService.loadAll();
    assertEquals(1, BattleManager.get().size());
    assertNotNull(BattleManager.getByWarId(23));
    String kept = BattleManager.get().getFirst().getId();
    assertTrue(Files.exists(files.root.resolve("Battles/battle_" + kept + ".json")));
  }

  @Test
  void startedManualBattleWinsOverAnIdleDuplicate() {
    when(fixture.ui.plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
    Battle idle = battle("idle");
    new Database().saveBattle(idle);
    Battle active = battle("started");
    active.setStarted(true);
    new Database().saveBattle(active);
    BattlePersistenceService.loadAll();
    assertEquals(1, BattleManager.get().size());
    assertEquals("started", BattleManager.get().getFirst().getId());
    assertFalse(Files.exists(files.root.resolve("Battles/battle_idle.json")));
    verify(fixture.ui.plugin.getLogger()).warning(contains("Multiple manual battle files"));
  }

  @Test
  void incompleteCleanupInputsLeaveUnrelatedLiveStateAndFilesAlone() {
    Battle manual = battle("manual");
    Warband members = band(manual, "members");
    new Database().saveBattle(manual);
    new Database().saveWarband(members);
    Battle campaign = battle("campaign");
    campaign.setWarId(23);
    BattlePersistenceService.persistBattle(null);
    BattlePersistenceService.persistWarband(null);
    BattlePersistenceService.deleteManualBattle(null);
    BattlePersistenceService.deleteManualBattle(campaign);
    BattlePersistenceService.deleteCampaignBattle(null);
    BattlePersistenceService.deleteCampaignBattle(manual);
    BattlePersistenceService.deleteRaidBattle(null);
    BattlePersistenceService.purgeCampaignWarbandsForBattle(null);
    BattlePersistenceService.deleteWarband(null);
    BattlePersistenceService.purgeCampaignWarbandsForWar(23);
    assertSame(manual, BattleManager.getByString("manual"));
    assertSame(campaign, BattleManager.getByString("campaign"));
    assertSame(members, WarbandManager.getByString("members"));
    assertTrue(Files.exists(files.root.resolve("Battles/battle_manual.json")));
    assertTrue(Files.exists(files.root.resolve("Warbands/warband_members.json")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void absentBattleDirectoriesDoNotPreventLoadingOrSaving(boolean regularFile) throws Exception {
    if (regularFile) files.write("Battles", "not a directory");
    BattlePersistenceService.loadAll();
    assertTrue(BattleManager.get().isEmpty());
    BattlePersistenceService.saveAll();
    assertTrue(WarbandManager.get().isEmpty());
    if (regularFile)
      assertEquals("not a directory", Files.readString(files.root.resolve("Battles")));
  }

  @Test
  void unreadableBattleDirectoryLeavesItUntouched() throws Exception {
    Path path = files.root.resolve("Battles");
    Files.createDirectories(path);
    org.junit.jupiter.api.Assumptions.assumeTrue(
        Files.getFileStore(path).supportsFileAttributeView("posix"));
    var permissions = Files.getPosixFilePermissions(path);
    try {
      Files.setPosixFilePermissions(path, Set.of());
      org.junit.jupiter.api.Assumptions.assumeFalse(Files.isReadable(path));
      BattlePersistenceService.loadAll();
      assertTrue(BattleManager.get().isEmpty());
    } finally {
      Files.setPosixFilePermissions(path, permissions);
    }
  }

  @Test
  void mapperRoundTripRetainsSpawnsContestAndUnknownOptionalValues() {
    Battle active = battle("roundtrip");
    var origin =
        new net.tfminecraft.simplefactions.war.battle.template.BattleLocation(
            "world", 1, 64, 2, 20, 15);
    var corner =
        new net.tfminecraft.simplefactions.war.battle.template.BattleLocation(
            "world", 10, 90, 20, 0, 0);
    active.setContestArea(
        new net.tfminecraft.simplefactions.war.battle.template.ContestArea(origin, corner));
    active.setNavalSpawn(origin.toBukkitLocation());
    active.setDefenderRespawnMode(DefenderRespawnMode.INFINITE);
    active.getSideById("attacker").addRespawnPoint(origin.toBukkitLocation());
    active.getSideById("attacker").setSpawn(corner.toBukkitLocation());
    BattleData data = BattleMapper.toData(active);
    data.defenderRespawnMode = "unknown-legacy-mode";
    Battle restored = BattleMapper.fromData(data);
    assertNotNull(restored.getContestArea());
    assertEquals(10, restored.getContestArea().getMax().getX());
    assertEquals(origin.toBukkitLocation(), restored.getNavalSpawn());
    assertEquals(
        List.of(origin.toBukkitLocation()), restored.getSideById("attacker").getRespawnPoints());
    assertNull(restored.getDefenderRespawnMode());
    assertNull(BattleMapper.toData(null));
  }

  @Test
  void mapperSkipsInvalidCaptureLocationsAndFallsBackForUnknownController() {
    when(Bukkit.getWorld("missing-world")).thenReturn(null);
    BattleData data = BattleMapper.toData(battle("points"));
    data.points.add(null);
    data.points.add(new CapturePointData());
    var missing = new CapturePointData();
    missing.id = "unloaded";
    missing.location =
        new net.tfminecraft.simplefactions.war.battle.template.BattleLocation(
            "missing-world", 1, 64, 1, 0, 0);
    data.points.add(missing);
    var valid = new CapturePointData();
    valid.id = "fallback";
    valid.controllerSideId = "unknown";
    valid.location =
        new net.tfminecraft.simplefactions.war.battle.template.BattleLocation(
            "world", 1, 64, 1, 0, 0);
    valid.captureProgress = 70;
    data.points.add(valid);
    data.sides.getFirst().respawnPoints.add(missing.location);
    Battle restored = BattleMapper.fromData(data);
    assertEquals(1, restored.getPoints().size());
    assertSame(restored.getSides().getFirst(), restored.getPoints().getFirst().getController());
    assertEquals(70, restored.getPoints().getFirst().getCaptureProgress());
    assertTrue(restored.getSides().getFirst().getRespawnPoints().isEmpty());
  }

  @Test
  void deletingARegisteredWarbandRemovesOnlyItsStateAndOwnedFile() throws Exception {
    Battle active = battle("active");
    Warband fighters = band(active, "fighters");
    BattlePersistenceService.saveAll();
    BattlePersistenceService.deleteWarband(fighters);
    assertNull(WarbandManager.getByString("fighters"));
    assertFalse(Files.exists(files.root.resolve("Warbands/warband_fighters.json")));
    assertTrue(Files.exists(files.root.resolve("Battles/battle_active.json")));
  }
}
