package net.tfminecraft.simplefactions.installation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleSiegeFortService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.zoc.FortControlService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class LegacyInstallationIdentityMigrationTest {
  private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
  private static final Instant UNTIL = NOW.plusSeconds(3600);

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void legacyReferencesAreSavedOnceBeforeTheyCanBeReinterpretedByLaterConstruction(
      boolean omitVersion) throws Exception {
    try (Context context = new Context()) {
      Installation fort = install(context.defender, "keep", InstallationKind.FORT, 20);
      Installation port = install(context.defender, "harbor", InstallationKind.PORT, 21);
      War war = context.war(990301);
      war.setFortControllers(Map.of("keep", CampaignCoalition.AGGRESSOR));
      war.setWartimeInstallationOwners(Map.of("keep", context.defender.getId()));
      war.setRaidRepairLockUntil(Map.of("harbor", UNTIL));
      Path file = context.saveLegacy(war, omitVersion);
      AtomicInteger saves = new AtomicInteger();
      try (MockedStatic<JsonUtil> json = mockStatic(JsonUtil.class, CALLS_REAL_METHODS)) {
        json.when(() -> JsonUtil.writeJsonAtomic(any(File.class), any()))
            .thenAnswer(
                call -> {
                  saves.incrementAndGet();
                  return call.callRealMethod();
                });
        WarManager.start();
        War loaded = WarManager.getById(war.getId());
        assertNotNull(loaded);
        assertAll(
            () ->
                assertEquals(
                    Map.of(fort.getStableKey(), CampaignCoalition.AGGRESSOR),
                    loaded.getFortControllers()),
            () ->
                assertEquals(
                    Map.of(fort.getStableKey(), context.defender.getId()),
                    loaded.getWartimeInstallationOwners()),
            () -> assertEquals(Map.of(port.getStableKey(), UNTIL), loaded.getRaidRepairLockUntil()),
            () -> assertEquals(1, saves.get()));
        JsonObject saved = JsonUtil.readJson(file.toFile(), JsonObject.class);
        assertEquals(1, saved.get("installationReferenceVersion").getAsInt());
        byte[] migratedBytes = Files.readAllBytes(file);

        Faction third = context.domain.saved("later_neighbor", "Cara");
        install(third, "keep", InstallationKind.FORT, 40);
        Installation otherPort = install(third, "harbor", InstallationKind.PORT, 41);
        assertEquals(
            CampaignCoalition.AGGRESSOR,
            FortControlService.controllerForInstallation(loaded, fort).orElseThrow());
        assertTrue(CampaignRaidService.isInstallationRepairLocked(loaded, port, NOW));
        assertFalse(CampaignRaidService.isInstallationRepairLocked(loaded, otherPort, NOW));
        WarManager.start();
        assertEquals(1, saves.get(), "Versioned saves are not migrated or rewritten again");
        assertArrayEquals(migratedBytes, Files.readAllBytes(file));
        assertEquals(
            loaded.getFortControllers(), WarManager.getById(war.getId()).getFortControllers());
      }
    }
  }

  @Test
  void ambiguousAndMissingLegacyReferencesCannotAcquireAnotherInstallationAfterARename()
      throws Exception {
    try (Context context = new Context()) {
      Faction third = context.domain.saved("third_holder", "Cara");
      Installation movedFort = install(context.defender, "shared", InstallationKind.FORT, 20);
      Installation otherFort = install(context.attacker, "shared", InstallationKind.FORT, 11);
      install(third, "shared", InstallationKind.PORT, 30);
      War war = context.war(990302);
      war.setFortControllers(
          Map.of("shared", CampaignCoalition.AGGRESSOR, "missing", CampaignCoalition.DEFENDER));
      war.setWartimeInstallationOwners(
          Map.of("shared", context.defender.getId(), "missing", context.attacker.getId()));
      war.setRaidRepairLockUntil(Map.of("shared", UNTIL, "missing", UNTIL));
      Path file = context.saveLegacy(war, false);
      WarManager.start();
      War loaded = WarManager.getById(war.getId());
      assertNotNull(loaded);
      assertAll(
          () -> assertTrue(loaded.getFortControllers().isEmpty()),
          () -> assertTrue(loaded.getWartimeInstallationOwners().isEmpty()),
          () -> assertTrue(loaded.getRaidRepairLockUntil().isEmpty()),
          () ->
              assertTrue(context.warnings.stream().anyMatch(message -> message.contains("shared"))),
          () ->
              assertTrue(
                  context.warnings.stream().anyMatch(message -> message.contains("missing"))));

      InstallationTransferService.transfer(context.defender, third, 20);
      Installation renamed =
          third.getInstallationHandler().getByProvince(InstallationKind.FORT, 20);
      assertNotNull(renamed);
      assertNotEquals("shared", renamed.getId());
      assertEquals(movedFort.getStableKey(), renamed.getStableKey());
      assertTrue(FortControlService.controllerForInstallation(loaded, otherFort).isEmpty());
      assertTrue(FortControlService.controllerForInstallation(loaded, renamed).isEmpty());
      assertFalse(CampaignRaidService.isInstallationRepairLocked(loaded, otherFort, NOW));
      WartimeInstallationService.revert(loaded);
      assertSame(context.attacker, InstallationOwners.ownerOf(otherFort));
      assertSame(third, InstallationOwners.ownerOf(renamed));
      WarData saved = JsonUtil.readJson(file.toFile(), WarData.class);
      assertTrue(saved.fortControllers.isEmpty());
      assertTrue(saved.wartimeInstallationOwners.isEmpty());
      assertTrue(saved.raidRepairLockUntil.isEmpty());
    }
  }

  @Test
  void anAmbiguousOldOwnerSnapshotNeverReturnsAnUnrelatedThirdPartyFort() throws Exception {
    try (Context context = new Context()) {
      Faction third = context.domain.saved("third_fort_owner", "Cara");
      install(context.attacker, "shared", InstallationKind.FORT, 20);
      Installation untouched = install(third, "shared", InstallationKind.FORT, 11);
      War war = context.war(990303);
      war.setWartimeInstallationOwners(Map.of("shared", context.defender.getId()));
      context.saveLegacy(war, false);
      WarManager.start();
      War loaded = WarManager.getById(war.getId());
      InstallationTransferService.transfer(context.attacker, third, 20);
      WartimeInstallationService.revert(loaded);
      assertSame(third, InstallationOwners.ownerOf(untouched));
      assertNull(
          context.defender.getInstallationHandler().getByProvince(InstallationKind.FORT, 11));
      assertTrue(loaded.getWartimeInstallationOwners().isEmpty());
    }
  }

  @Test
  void aUniqueNeutralOwnerSnapshotIsDroppedWhileValidBelligerentRestorationStillWorks()
      throws Exception {
    try (Context context = new Context()) {
      Faction neutral = context.domain.saved("neutral_snapshot_holder", "Cara");
      Installation untouched = install(neutral, "neutral_only_keep", InstallationKind.FORT, 40);
      Installation occupied = install(context.attacker, "occupied_keep", InstallationKind.FORT, 20);
      War war = context.war(990313);
      assertNull(war.getSide(neutral));
      war.setWartimeInstallationOwners(
          Map.of(
              untouched.getId(),
              context.defender.getId(),
              occupied.getId(),
              context.defender.getId()));
      Path file = context.saveLegacy(war, false);

      WarManager.start();

      War loaded = WarManager.getById(war.getId());
      assertNotNull(loaded);
      assertEquals(
          Map.of(occupied.getStableKey(), context.defender.getId()),
          loaded.getWartimeInstallationOwners());
      assertTrue(
          context.warnings.stream()
              .anyMatch(
                  message ->
                      message.contains("War " + war.getId())
                          && message.contains("original owner")
                          && message.contains("neutral_only_keep")));
      WarData saved = JsonUtil.readJson(file.toFile(), WarData.class);
      assertEquals(1, saved.installationReferenceVersion);
      assertEquals(
          Map.of(occupied.getStableKey(), context.defender.getId()),
          saved.wartimeInstallationOwners);

      WartimeInstallationService.revert(loaded);

      assertSame(neutral, InstallationOwners.ownerOf(untouched));
      assertSame(untouched, neutral.getInstallationHandler().getById("neutral_only_keep"));
      assertNull(
          context.defender.getInstallationHandler().getByProvince(InstallationKind.FORT, 40));
      assertSame(context.defender, InstallationOwners.ownerOf(occupied));
      assertNull(
          context.attacker.getInstallationHandler().getByProvince(InstallationKind.FORT, 20));
      assertTrue(loaded.getWartimeInstallationOwners().isEmpty());
    }
  }

  @Test
  void existingPhysicalValuesWinOverConflictingLegacyValuesInAllThreeMaps() throws Exception {
    try (Context context = new Context()) {
      Installation fort = install(context.defender, "keep", InstallationKind.FORT, 20);
      War war = context.war(990304);
      war.setFortControllers(
          Map.of(
              "keep",
              CampaignCoalition.AGGRESSOR,
              fort.getStableKey(),
              CampaignCoalition.DEFENDER));
      war.setWartimeInstallationOwners(
          Map.of("keep", context.attacker.getId(), fort.getStableKey(), context.defender.getId()));
      Instant physicalUntil = UNTIL.plusSeconds(7200);
      war.setRaidRepairLockUntil(Map.of("keep", UNTIL, fort.getStableKey(), physicalUntil));
      context.saveLegacy(war, false);
      War loaded = new Database().loadWars().getFirst();
      assertAll(
          () ->
              assertEquals(
                  Map.of(fort.getStableKey(), CampaignCoalition.DEFENDER),
                  loaded.getFortControllers()),
          () ->
              assertEquals(
                  Map.of(fort.getStableKey(), context.defender.getId()),
                  loaded.getWartimeInstallationOwners()),
          () ->
              assertEquals(
                  Map.of(fort.getStableKey(), physicalUntil), loaded.getRaidRepairLockUntil()));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void oldAxisProvinceSiegesKeepChronologyButResolveThePhysicalFortAndScheduledProvince(
      boolean explicitChronology) throws Exception {
    try (Context context = new Context()) {
      install(context.defender, "keep", InstallationKind.FORT, 20);
      War war = context.war(990305);
      var field = new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, true, null);
      var old =
          new ScheduledCampaignBattle(
              31, CampaignBattleKind.SIEGE, true, "keep", null, explicitChronology ? 7 : null);
      war.setCampaignProvinces(List.of(10, 31, 40));
      war.setObjectiveProvinceId(40);
      war.setCursorIndex(0);
      war.setInitiativeAttacker(5);
      war.setCampaignBattleSchedule(List.of(field, old));
      war.setCampaignCounterSchedule(List.of(old, field));
      war.setCampaignScheduleIndex(1);
      war.setCampaignCounterScheduleIndex(0);
      war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
      war.setScheduledBattleProvinceId(31);
      Path file = context.saveLegacy(war, false);
      WarManager.start();
      War loaded = WarManager.getById(war.getId());
      var expected =
          new ScheduledCampaignBattle(
              20, CampaignBattleKind.SIEGE, true, "keep", null, explicitChronology ? 7 : 31);
      assertAll(
          () -> assertEquals(List.of(field, expected), loaded.getCampaignBattleSchedule()),
          () -> assertEquals(List.of(expected, field), loaded.getCampaignCounterSchedule()),
          () -> assertEquals(1, loaded.getCampaignScheduleIndex()),
          () -> assertEquals(0, loaded.getCampaignCounterScheduleIndex()),
          () -> assertEquals(20, loaded.getScheduledBattleProvinceId()),
          () -> assertEquals(20, BattleScheduleService.resolveScheduledProvinceId(loaded)),
          () ->
              assertSame(
                  context.defender,
                  BattleSiegeFortService.currentSiegeFortOwner(loaded).orElse(null)));
      War restored = WarMapper.fromData(JsonUtil.readJson(file.toFile(), WarData.class));
      assertNotNull(restored);
      assertEquals(expected, restored.getCampaignBattleSchedule().get(1));
      assertEquals(20, restored.getScheduledBattleProvinceId());
    }
  }

  @Test
  void failedMigrationSaveKeepsOriginalBytesAndDoesNotPublishAnUnpersistedWar() throws Exception {
    try (Context context = new Context()) {
      install(context.defender, "keep", InstallationKind.FORT, 20);
      War war = context.war(990306);
      war.setFortControllers(Map.of("keep", CampaignCoalition.AGGRESSOR));
      Path failing = context.saveLegacy(war, false);
      byte[] original = Files.readAllBytes(failing);
      War good = context.war(990307);
      Path unaffected = context.save(good, 1);
      byte[] goodBytes = Files.readAllBytes(unaffected);
      try (MockedStatic<JsonUtil> json = mockStatic(JsonUtil.class, CALLS_REAL_METHODS)) {
        json.when(
                () ->
                    JsonUtil.writeJsonAtomic(
                        argThat((File file) -> file.toPath().toAbsolutePath().equals(failing)),
                        any()))
            .thenThrow(new IOException("simulated migration disk failure"));
        assertThrows(IllegalStateException.class, WarManager::start);
        assertNull(WarManager.getById(war.getId()));
        assertNull(
            WarManager.getById(good.getId()),
            "A failed initial load cannot publish a partial war list");
        assertArrayEquals(original, Files.readAllBytes(failing));
        assertArrayEquals(goodBytes, Files.readAllBytes(unaffected));
      }
    }
  }

  @Test
  void aVersionedWarNeverDynamicallyResolvesRawKeysInsertedByAnOldIntegration() throws Exception {
    try (Context context = new Context()) {
      Installation fort = install(context.defender, "keep", InstallationKind.FORT, 20);
      War war = context.war(990308);
      war.setFortControllers(Map.of("keep", CampaignCoalition.AGGRESSOR));
      war.setWartimeInstallationOwners(Map.of("keep", context.attacker.getId()));
      war.setRaidRepairLockUntil(Map.of("keep", UNTIL));
      Path file = context.save(war, 1);
      byte[] original = Files.readAllBytes(file);
      WarManager.start();
      War loaded = WarManager.getById(war.getId());
      assertAll(
          () -> assertTrue(FortControlService.controllerForInstallation(loaded, fort).isEmpty()),
          () -> assertTrue(FortControlService.controller(loaded, "keep").isEmpty()),
          () -> assertFalse(CampaignRaidService.isInstallationRepairLocked(loaded, fort, NOW)),
          () -> assertFalse(CampaignRaidService.isRepairLocked(loaded, "keep", NOW)));
      WartimeInstallationService.revert(loaded);
      assertSame(context.defender, InstallationOwners.ownerOf(fort));
      assertArrayEquals(original, Files.readAllBytes(file));
    }
  }

  @Test
  void fortMigrationIgnoresAnUnrelatedPortWithTheSameLocalId() throws Exception {
    try (Context context = new Context()) {
      Installation fort = install(context.defender, "shared", InstallationKind.FORT, 20);
      install(context.attacker, "shared", InstallationKind.PORT, 11);
      War war = context.war(990309);
      war.setFortControllers(Map.of("shared", CampaignCoalition.AGGRESSOR));
      context.saveLegacy(war, false);
      War loaded = new Database().loadWars().getFirst();
      assertEquals(
          Map.of(fort.getStableKey(), CampaignCoalition.AGGRESSOR), loaded.getFortControllers());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void anUnresolvableOldAxisSiegeAbortsWithoutPublishingOrRewritingTheWar(boolean ambiguous)
      throws Exception {
    try (Context context = new Context()) {
      if (ambiguous) {
        install(context.defender, "keep", InstallationKind.FORT, 20);
        install(context.attacker, "keep", InstallationKind.FORT, 10);
      }
      War war = context.war(990310);
      war.setCampaignBattleSchedule(
          List.of(new ScheduledCampaignBattle(31, CampaignBattleKind.SIEGE, false, "keep")));
      Path file = context.saveLegacy(war, false);
      byte[] original = Files.readAllBytes(file);
      assertThrows(IllegalStateException.class, WarManager::start);
      assertNull(WarManager.getById(war.getId()));
      assertArrayEquals(original, Files.readAllBytes(file));
    }
  }

  @Test
  void aHomeProvinceQualifiedSiegeRemainsBoundDespiteDuplicateFortIds() throws Exception {
    try (Context context = new Context()) {
      install(context.defender, "keep", InstallationKind.FORT, 20);
      install(context.attacker, "keep", InstallationKind.FORT, 10);
      War war = context.war(990311);
      var slot = new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, "keep", null, 31);
      war.setCampaignBattleSchedule(List.of(slot));
      war.setScheduledBattleProvinceId(20);
      context.saveLegacy(war, false);
      War loaded = new Database().loadWars().getFirst();
      assertEquals(List.of(slot), loaded.getCampaignBattleSchedule());
      assertEquals(20, loaded.getScheduledBattleProvinceId());
      assertSame(
          context.defender, BattleSiegeFortService.currentSiegeFortOwner(loaded).orElseThrow());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"malformed_json", "unknown_status"})
  void aSkippedWarFileStillReservesItsIdAndCannotBeOverwrittenByNewWarAllocation(String invalid)
      throws Exception {
    try (Context context = new Context()) {
      String contents = "{not valid JSON";
      if (invalid.equals("unknown_status")) {
        WarData data = WarMapper.toData(context.war(0));
        data.status = "future_unknown_status";
        contents = JsonUtil.GSON.toJson(data);
      }
      Path reserved = context.files.write("Wars/war_0.json", contents);
      byte[] original = Files.readAllBytes(reserved);
      WarManager.start();
      assertTrue(WarManager.get().isEmpty());
      if (invalid.equals("unknown_status")) {
        assertTrue(
            context.warnings.stream()
                .anyMatch(
                    message ->
                        message.contains("Cannot restore war from")
                            && message.contains("war_0.json")
                            && message.contains("keeping its file for recovery")));
      }
      assertEquals(1, WarManager.newId());
      War created = context.war(WarManager.newId());
      WarManager.addWar(created);
      assertArrayEquals(original, Files.readAllBytes(reserved));
      assertTrue(Files.exists(context.files.root.resolve("Wars/war_1.json")));
      assertEquals(2, WarManager.newId());
    }
  }

  @Test
  void stringRepairLockCompatibilityBindsOnePhysicalInstallationBeforeIdsCanChange()
      throws Exception {
    try (Context context = new Context()) {
      Installation original = install(context.defender, "harbor", InstallationKind.PORT, 20);
      War war = context.war(990312);
      CampaignRaidService.setRepairLockUntil(war, "harbor", UNTIL);
      assertEquals(Map.of(original.getStableKey(), UNTIL), war.getRaidRepairLockUntil());
      assertTrue(CampaignRaidService.isRepairLocked(war, "harbor", NOW));
      assertTrue(CampaignRaidService.isRepairLocked(war, original.getStableKey(), NOW));

      Installation duplicate = install(context.attacker, "harbor", InstallationKind.PORT, 10);
      Instant later = UNTIL.plusSeconds(3600);
      CampaignRaidService.setRepairLockUntil(war, "harbor", later);
      CampaignRaidService.setRepairLockUntil(war, "missing", later);
      CampaignRaidService.setRepairLockUntil(war, "province:99:port:100", later);
      assertEquals(Map.of(original.getStableKey(), UNTIL), war.getRaidRepairLockUntil());
      assertFalse(CampaignRaidService.isRepairLocked(war, "harbor", NOW));
      assertTrue(CampaignRaidService.isInstallationRepairLocked(war, original, NOW));
      assertFalse(CampaignRaidService.isInstallationRepairLocked(war, duplicate, NOW));

      CampaignRaidService.setRepairLockUntil(war, original.getStableKey(), later);
      assertEquals(Map.of(original.getStableKey(), later), war.getRaidRepairLockUntil());
      CampaignRaidService.setInstallationRepairLockUntil(war, original, null);
      CampaignRaidService.setInstallationRepairLockUntil(null, original, later);
      CampaignRaidService.setInstallationRepairLockUntil(war, null, later);
      assertEquals(Map.of(original.getStableKey(), later), war.getRaidRepairLockUntil());
      assertFalse(CampaignRaidService.isInstallationRepairLocked(war, duplicate, NOW));
    }
  }

  private static Installation install(
      Faction faction, String id, InstallationKind kind, int province) {
    Installation installation =
        new Installation(id, id, kind, province, province * 10, province * 10, 100L);
    faction.getInstallationHandler().acceptTransferred(installation);
    return installation;
  }

  private static final class Context implements AutoCloseable {
    final PersistenceFilesFixture files = new PersistenceFilesFixture();
    final FactionDomainFixture domain = new FactionDomainFixture();
    final Faction attacker = domain.saved("migration_attacker", "Alice");
    final Faction defender = domain.saved("migration_defender", "Bob");
    final List<String> warnings = new ArrayList<>();
    final Logger logger = Logger.getAnonymousLogger();
    final Logger migrationLogger =
        Logger.getLogger("net.tfminecraft.simplefactions.installation.WarInstallationMigration");
    final boolean oldMigrationParentHandlers = migrationLogger.getUseParentHandlers();
    final Logger databaseLogger = Logger.getLogger(Database.class.getName());
    final boolean oldDatabaseParentHandlers = databaseLogger.getUseParentHandlers();
    final Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            warnings.add(record.getMessage());
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };

    Context() throws Exception {
      logger.setUseParentHandlers(false);
      logger.addHandler(handler);
      migrationLogger.setUseParentHandlers(false);
      migrationLogger.addHandler(handler);
      databaseLogger.setUseParentHandlers(false);
      databaseLogger.addHandler(handler);
      when(Bukkit.getLogger()).thenReturn(logger);
      when(domain.ui.plugin.getLogger()).thenReturn(logger);
    }

    War war(int id) {
      War war = new War(id, attacker, defender);
      war.setGoal(WarGoalType.WAR);
      return war;
    }

    Path saveLegacy(War war, boolean omitVersion) throws IOException {
      JsonObject data = JsonUtil.GSON.toJsonTree(WarMapper.toData(war)).getAsJsonObject();
      if (omitVersion) data.remove("installationReferenceVersion");
      else data.addProperty("installationReferenceVersion", 0);
      return files.write("Wars/war_" + war.getId() + ".json", JsonUtil.GSON.toJson(data));
    }

    Path save(War war, int version) throws IOException {
      JsonObject data = JsonUtil.GSON.toJsonTree(WarMapper.toData(war)).getAsJsonObject();
      data.addProperty("installationReferenceVersion", version);
      return files.write("Wars/war_" + war.getId() + ".json", JsonUtil.GSON.toJson(data));
    }

    @Override
    public void close() throws Exception {
      try {
        logger.removeHandler(handler);
        migrationLogger.removeHandler(handler);
        migrationLogger.setUseParentHandlers(oldMigrationParentHandlers);
        databaseLogger.removeHandler(handler);
        databaseLogger.setUseParentHandlers(oldDatabaseParentHandlers);
        domain.close();
      } finally {
        files.close();
      }
    }
  }
}
