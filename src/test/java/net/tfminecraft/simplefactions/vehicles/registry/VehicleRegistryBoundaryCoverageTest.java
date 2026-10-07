package net.tfminecraft.simplefactions.vehicles.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

class VehicleRegistryBoundaryCoverageTest {
  private static final UUID OWNER = UUID.fromString("30000000-0000-0000-0000-000000000001");
  @TempDir Path directory;

  static Stream<String> damagedRegistries() {
    String valid = row("vehicle-new", OWNER.toString(), "ironclad", "INSTALLATION");
    return Stream.of(
        "null",
        "{",
        "{}",
        "[null]",
        "[" + valid + "," + row("bad", "invalid-uuid", "ironclad", "POOL") + "]",
        "[" + valid + "," + row("bad", OWNER.toString(), "ironclad", "UNKNOWN") + "]",
        "["
            + valid
            + ",{\"vehicleUuid\":\"missing-type\",\"playerUuid\":\""
            + OWNER
            + "\",\"mode\":\"POOL\"}]");
  }

  @ParameterizedTest
  @MethodSource("damagedRegistries")
  void damagedInputPreservesLiveOwnershipAndCannotBeOverwrittenBySave(String contents)
      throws Exception {
    PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    PlayerVehicleRecord live = record("live-owned-vehicle");
    registry.register(live);
    Path file = directory.resolve("vehicles_registry.json");
    Files.writeString(file, contents);
    VehicleRegistryPersistence persistence =
        new VehicleRegistryPersistence(directory.toFile(), registry);

    assertDoesNotThrow(persistence::load, "A damaged registry must not abort plugin startup");

    assertEquals(List.of(live), registry.getAll(), "The complete prior registry must survive");
    assertSame(live, registry.getByVehicleUuid(live.getVehicleUuid()).orElseThrow());
    assertFalse(persistence.save(), "An unread registry must not be replaced on shutdown");
    assertEquals(contents, Files.readString(file));
  }

  @Test
  void repairedEmptyRegistryClearsOwnershipAndUnblocksFutureSaves() throws Exception {
    PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    registry.register(record("live"));
    Path file = directory.resolve("vehicles_registry.json");
    Files.writeString(file, "null");
    VehicleRegistryPersistence persistence =
        new VehicleRegistryPersistence(directory.toFile(), registry);
    persistence.load();
    assertFalse(persistence.save());

    Files.writeString(file, "[]");
    persistence.load();
    assertTrue(registry.getAll().isEmpty());
    registry.register(record("repaired"));
    assertTrue(persistence.save());
    PlayerVehicleRegistry restored = new PlayerVehicleRegistry();
    new VehicleRegistryPersistence(directory.toFile(), restored).load();
    assertEquals("repaired", restored.getAll().getFirst().getVehicleUuid());
  }

  @Test
  void factionOwnershipWithoutAnOriginalPlayerSurvivesARealSaveAndLoad() {
    PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    registry.register(
        new PlayerVehicleRecord(
            null, "unattributed-pool", "field_artillery", OwnershipMode.POOL, null, "red"));
    registry.register(
        new PlayerVehicleRecord(
            null, "unattributed-berth", "ironclad", OwnershipMode.INSTALLATION, "port", "red"));
    assertTrue(new VehicleRegistryPersistence(directory.toFile(), registry).save());

    PlayerVehicleRegistry restored = new PlayerVehicleRegistry();
    new VehicleRegistryPersistence(directory.toFile(), restored).load();

    assertEquals(
        2,
        restored.getAll().size(),
        "Faction ownership does not require knowing the former player");
    PlayerVehicleRecord pooled = restored.getByVehicleUuid("unattributed-pool").orElseThrow();
    assertNull(pooled.getPlayerUuid());
    assertEquals("red", pooled.getFactionId());
    assertTrue(restored.isFactionOwned("unattributed-pool"));
    assertTrue(restored.isBerthed("unattributed-berth"));
  }

  @Test
  void missingFilesAndExplicitDeletionAfterAReadFailurePreserveMemoryAndAllowRecovery()
      throws Exception {
    PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    PlayerVehicleRecord original = record("original");
    registry.register(original);
    Path file = directory.resolve("vehicles_registry.json");
    VehicleRegistryPersistence persistence =
        new VehicleRegistryPersistence(directory.toFile(), registry);
    persistence.load();
    assertSame(original, registry.getByVehicleUuid("original").orElseThrow());
    Files.createDirectory(file);
    assertDoesNotThrow(persistence::load);
    assertSame(original, registry.getByVehicleUuid("original").orElseThrow());
    Files.delete(file);
    assertFalse(
        persistence.save(), "Removing an unread file does not silently authorize replacement");
    persistence.load();
    assertTrue(
        persistence.save(), "An explicit successful reload recovers storage after operator repair");
    PlayerVehicleRegistry restored = new PlayerVehicleRegistry();
    new VehicleRegistryPersistence(directory.toFile(), restored).load();
    assertTrue(restored.isBerthed("original"));
  }

  @Test
  void failedWritesAndFailedRenamesPreserveTheExistingStorageAndLiveOwnership() throws Exception {
    PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    PlayerVehicleRecord original = record("live");
    registry.register(original);
    Path file = directory.resolve("vehicles_registry.json");
    String persisted = "[" + row("persisted", OWNER.toString(), "ironclad", "INSTALLATION") + "]";
    Files.writeString(file, persisted);
    Path temp = directory.resolve("vehicles_registry.json.tmp");
    Files.createDirectory(temp);
    Path marker = temp.resolve("unrelated.txt");
    Files.writeString(marker, "leave me intact");
    VehicleRegistryPersistence persistence =
        new VehicleRegistryPersistence(directory.toFile(), registry);
    assertFalse(persistence.save());
    assertEquals(persisted, Files.readString(file));
    assertEquals("leave me intact", Files.readString(marker));
    assertSame(original, registry.getByVehicleUuid("live").orElseThrow());
    Files.delete(marker);
    Files.delete(temp);
    Files.delete(file);
    Files.createDirectory(file);
    Path sentinel = file.resolve("existing.txt");
    Files.writeString(sentinel, "existing destination");
    assertFalse(persistence.save());
    assertEquals("existing destination", Files.readString(sentinel));
    assertSame(original, registry.getByVehicleUuid("live").orElseThrow());
  }

  @Test
  void aFilesystemWithoutAtomicRenameStillPublishesACompleteReadableRegistry() throws Exception {
    PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    registry.register(record("atomically-written"));
    AtomicInteger atomicAttempts = new AtomicInteger();
    AtomicInteger ordinaryMoves = new AtomicInteger();
    // Only simulate the filesystem capability; the fallback performs a real move and readback.
    try (MockedStatic<Files> filesystem =
        mockStatic(
            Files.class,
            call -> {
              if (call.getMethod().getName().equals("move")) {
                CopyOption[] options = (CopyOption[]) call.getRawArguments()[2];
                if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                  atomicAttempts.incrementAndGet();
                  throw new AtomicMoveNotSupportedException(
                      call.getArgument(0).toString(),
                      call.getArgument(1).toString(),
                      "filesystem capability");
                }
                ordinaryMoves.incrementAndGet();
              }
              return call.callRealMethod();
            })) {
      assertTrue(new VehicleRegistryPersistence(directory.toFile(), registry).save());
    }
    assertEquals(1, atomicAttempts.get());
    assertEquals(1, ordinaryMoves.get());
    assertFalse(Files.exists(directory.resolve("vehicles_registry.json.tmp")));
    PlayerVehicleRegistry restored = new PlayerVehicleRegistry();
    new VehicleRegistryPersistence(directory.toFile(), restored).load();
    assertEquals(
        "red", restored.getByVehicleUuid("atomically-written").orElseThrow().getFactionId());
  }

  @Test
  void invalidQueryInputsAndDetachedSnapshotsCannotRemoveRegisteredOwnership() {
    PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    PlayerVehicleRecord original = record("registered");
    registry.register(original);
    registry.register(null);
    registry.register(
        new PlayerVehicleRecord(OWNER, null, "ironclad", OwnershipMode.PERSONAL, null));
    assertFalse(registry.unregister(null));
    assertTrue(registry.getByVehicleUuid(null).isEmpty());
    assertTrue(registry.getPoolVehicles(null).isEmpty());
    assertTrue(registry.getPoolVehicles(" ").isEmpty());
    assertEquals(0, registry.countPoolCategory(null, "ships"));
    assertEquals(0, registry.countPoolCategory("red", null));
    assertEquals(0, registry.countPoolCategory("red", ""));
    assertEquals(0, registry.usedCategorySize("red", null, "ships"));
    assertEquals(0, registry.usedCategorySize("red", "port", null));
    assertEquals(0, registry.usedCategorySize("red", "port", ""));
    registry.getAll().clear();
    registry.getByInstallationId("port").clear();
    assertSame(original, registry.getByVehicleUuid("registered").orElseThrow());
    assertTrue(registry.isFactionOwned("registered"));
    assertTrue(registry.unregister("registered"));
    assertFalse(registry.unregister("registered"));
    assertTrue(registry.getAll().isEmpty());
  }

  private static PlayerVehicleRecord record(String id) {
    return new PlayerVehicleRecord(
        OWNER, id, "ironclad", OwnershipMode.INSTALLATION, "port", "red");
  }

  private static String row(String id, String owner, String type, String mode) {
    return "{\"playerUuid\":\""
        + owner
        + "\",\"vehicleUuid\":\""
        + id
        + "\",\"vehicleTypeId\":\""
        + type
        + "\",\"mode\":\""
        + mode
        + "\",\"installationId\":\"port\",\"factionId\":\"red\"}";
  }
}
