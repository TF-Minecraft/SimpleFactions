package net.tfminecraft.simplefactions.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

/** Isolates legacy relative persistence paths and restores any pre-existing checkout data. */
public final class PersistenceFilesFixture implements AutoCloseable {
  public final Path root = Path.of("plugins", "SimpleFactions").toAbsolutePath();
  private final Path backup = root.resolveSibling("SimpleFactions.coverage-backup-" + UUID.randomUUID());
  private final boolean hadOriginal;

  public PersistenceFilesFixture() throws IOException {
    Files.createDirectories(root.getParent());
    hadOriginal = Files.exists(root);
    if (hadOriginal) Files.move(root, backup);
    try { Files.createDirectory(root); }
    catch (IOException failure) {
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

  @Override public void close() throws IOException {
    remove("");
    if (hadOriginal) Files.move(backup, root);
  }
}
