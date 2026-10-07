package net.tfminecraft.simplefactions.testsupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PersistenceFilesFixtureTest {
  private static final byte[] ORIGINAL = {0, 7, -1, 20, 0};

  @Test
  void normalCloseRestoresOriginalBytesAndARepeatedCloseCannotDeleteThem() throws Exception {
    try (var outer = new PersistenceFilesFixture()) {
      Path original = originalFile(outer);
      Set<Path> before = artifacts(outer.root.getParent());
      var inner = new PersistenceFilesFixture();
      Path backup = newArtifact(outer.root.getParent(), before, "backup");
      try {
        inner.write("fixture-created.txt", "temporary");
        inner.close();
        assertArrayEquals(ORIGINAL, Files.readAllBytes(original));
        assertFalse(Files.exists(outer.root.resolve("fixture-created.txt")));
        assertEquals(before, artifacts(outer.root.getParent()));

        assertDoesNotThrow(inner::close);
        assertArrayEquals(ORIGINAL, Files.readAllBytes(original));
      } finally {
        restoreAndCleanTestArtifacts(outer.root, backup, before);
      }
    }
  }

  @Test
  void normalCloseWithoutAnOriginalRemovesOnlyItsTestTree() throws Exception {
    try (var outer = new PersistenceFilesFixture()) {
      outer.remove("");
      Set<Path> before = artifacts(outer.root.getParent());
      var inner = new PersistenceFilesFixture();
      inner.write("nested/fixture-created.txt", "temporary");

      inner.close();

      assertFalse(Files.exists(outer.root));
      assertEquals(before, artifacts(outer.root.getParent()));
    }
  }

  @Test
  void failedCleanupRestoresOriginalBytesAndRetainsOnlyTheTestLeftoversInQuarantine()
      throws Exception {
    try (var outer = new PersistenceFilesFixture()) {
      Path original = originalFile(outer);
      Set<Path> before = artifacts(outer.root.getParent());
      var inner = new PersistenceFilesFixture();
      Path backup = newArtifact(outer.root.getParent(), before, "backup");
      Path blocked = inner.write("fixture-created.txt", "retain this test file");
      IOException cleanupFailure = new IOException("Injected fixture file deletion failure");
      try {
        IOException reported;
        try (var files =
            mockStatic(
                Files.class,
                invocation -> {
                  if (invocation.getMethod().getName().equals("delete")
                      && blocked.equals(invocation.getArgument(0))) throw cleanupFailure;
                  return invocation.callRealMethod();
                })) {
          reported = assertThrows(IOException.class, inner::close);
        }

        assertSame(cleanupFailure, reported);
        assertEquals(0, reported.getSuppressed().length);
        assertTrue(
            Files.isRegularFile(original), "The original must return despite cleanup failure");
        assertArrayEquals(ORIGINAL, Files.readAllBytes(original));
        assertFalse(Files.exists(backup));
        Path quarantine = newArtifact(outer.root.getParent(), before, "leftovers");
        assertEquals(
            "retain this test file", Files.readString(quarantine.resolve("fixture-created.txt")));
        assertFalse(Files.exists(quarantine.resolve("original/data.bin")));
        assertFalse(Files.exists(outer.root.resolve("fixture-created.txt")));
        assertDoesNotThrow(inner::close);
        assertArrayEquals(ORIGINAL, Files.readAllBytes(original));
      } finally {
        restoreAndCleanTestArtifacts(outer.root, backup, before);
      }
      assertEquals(before, artifacts(outer.root.getParent()));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"quarantine", "restore"})
  void aFailedRecoveryPreservesTheOriginalBackupAndReportsBothFailures(String failureStage)
      throws Exception {
    try (var outer = new PersistenceFilesFixture()) {
      originalFile(outer);
      Set<Path> before = artifacts(outer.root.getParent());
      var inner = new PersistenceFilesFixture();
      Path backup = newArtifact(outer.root.getParent(), before, "backup");
      Path blocked = inner.write("fixture-created.txt", "retain this test file");
      IOException cleanupFailure = new IOException("Injected fixture file deletion failure");
      IOException recoveryFailure = new IOException("Injected " + failureStage + " move failure");
      try {
        IOException reported;
        try (var files =
            mockStatic(
                Files.class,
                invocation -> {
                  String method = invocation.getMethod().getName();
                  if (method.equals("delete") && blocked.equals(invocation.getArgument(0))) {
                    throw cleanupFailure;
                  }
                  if (method.equals("move")) {
                    Path source = invocation.getArgument(0);
                    if ((failureStage.equals("quarantine") && source.equals(outer.root))
                        || (failureStage.equals("restore") && source.equals(backup))) {
                      throw recoveryFailure;
                    }
                  }
                  return invocation.callRealMethod();
                })) {
          reported = assertThrows(IOException.class, inner::close);
        }

        assertSame(cleanupFailure, reported);
        assertArrayEquals(new Throwable[] {recoveryFailure}, reported.getSuppressed());
        assertArrayEquals(ORIGINAL, Files.readAllBytes(backup.resolve("original/data.bin")));
        if (failureStage.equals("quarantine")) {
          assertEquals("retain this test file", Files.readString(blocked));
          assertEquals(Set.of(backup), newArtifacts(outer.root.getParent(), before));
        } else {
          assertFalse(Files.exists(outer.root));
          Path quarantine = newArtifact(outer.root.getParent(), before, "leftovers");
          assertEquals(
              "retain this test file", Files.readString(quarantine.resolve("fixture-created.txt")));
        }
      } finally {
        restoreAndCleanTestArtifacts(outer.root, backup, before);
      }
      assertArrayEquals(ORIGINAL, Files.readAllBytes(outer.root.resolve("original/data.bin")));
      assertEquals(before, artifacts(outer.root.getParent()));
    }
  }

  private Path originalFile(PersistenceFilesFixture outer) throws IOException {
    Path file = outer.root.resolve("original/data.bin");
    Files.createDirectories(file.getParent());
    return Files.write(file, ORIGINAL);
  }

  private Set<Path> artifacts(Path parent) throws IOException {
    try (var files = Files.list(parent)) {
      return new HashSet<>(
          files
              .filter(path -> path.getFileName().toString().startsWith("SimpleFactions.coverage-"))
              .toList());
    }
  }

  private Set<Path> newArtifacts(Path parent, Set<Path> before) throws IOException {
    Set<Path> result = artifacts(parent);
    result.removeAll(before);
    return result;
  }

  private Path newArtifact(Path parent, Set<Path> before, String kind) throws IOException {
    var matching =
        newArtifacts(parent, before).stream()
            .filter(
                path ->
                    path.getFileName()
                        .toString()
                        .startsWith("SimpleFactions.coverage-" + kind + "-"))
            .toList();
    assertEquals(1, matching.size(), "Expected exactly one new " + kind + " directory");
    return matching.getFirst();
  }

  private void restoreAndCleanTestArtifacts(Path root, Path backup, Set<Path> before)
      throws IOException {
    // Only the outer fixture owns real checkout data; this backup contains our synthetic bytes.
    if (Files.exists(backup)) {
      deleteTestTree(root);
      Files.move(backup, root);
    }
    for (Path testArtifact : newArtifacts(root.getParent(), before)) deleteTestTree(testArtifact);
  }

  private void deleteTestTree(Path root) throws IOException {
    if (!Files.exists(root)) return;
    try (var entries = Files.walk(root)) {
      for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) Files.delete(entry);
    }
  }
}
