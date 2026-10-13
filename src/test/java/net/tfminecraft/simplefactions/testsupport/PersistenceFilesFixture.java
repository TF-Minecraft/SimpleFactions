package net.tfminecraft.simplefactions.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

/** Isolates legacy relative persistence paths and restores any pre-existing checkout data. */
public final class PersistenceFilesFixture implements AutoCloseable {
  public final Path root = Path.of("plugins", "SimpleFactions").toAbsolutePath();
  private final Path backup =
      root.resolveSibling("SimpleFactions.coverage-backup-" + UUID.randomUUID());
  private final boolean hadOriginal;
  private boolean closed;
  private final Set<Path> previousHolds = new HashSet<>();

  public PersistenceFilesFixture() throws IOException {
    Files.createDirectories(root.getParent());
    hadOriginal = Files.exists(root);
    if (hadOriginal) Files.move(root, backup);
    try {
      Files.createDirectory(root);
      for (Path held : runtimeHolds()) if (held.startsWith(root)) previousHolds.add(held);
      runtimeHolds().removeIf(path -> path.startsWith(root));
    } catch (IOException failure) {
      if (hadOriginal) Files.move(backup, root);
      throw failure;
    }
  }

  public Path write(String relative, String contents) throws IOException {
    Path path = root.resolve(relative);
    Files.createDirectories(path.getParent());
    return Files.writeString(path, contents);
  }

  public void remove(String relative) throws IOException {
    Path path = root.resolve(relative);
    if (!Files.exists(path)) return;
    try (var entries = Files.walk(path)) {
      for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) Files.delete(entry);
    }
  }

  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    IOException failure = null;
    try {
      remove("");
    } catch (IOException cleanupFailure) {
      failure = cleanupFailure;
    }
    if (hadOriginal) {
      try {
        if (Files.exists(root)) {
          Path quarantine =
              root.resolveSibling("SimpleFactions.coverage-leftovers-" + UUID.randomUUID());
          Files.move(root, quarantine);
        }
        Files.move(backup, root);
      } catch (IOException restoreFailure) {
        if (failure == null) failure = restoreFailure;
        else failure.addSuppressed(restoreFailure);
      }
    }
    runtimeHolds().removeIf(path -> path.startsWith(root));
    runtimeHolds().addAll(previousHolds);
    if (failure != null) throw failure;
  }

  @SuppressWarnings("unchecked")
  private static Set<Path> runtimeHolds() {
    try {
      var field = net.tfminecraft.simplefactions.war.battle.persistence.WarbandCharacters.class.getDeclaredField("HELD");
      field.setAccessible(true);
      return (Set<Path>) field.get(null);
    } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
  }
}
