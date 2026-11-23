package net.tfminecraft.simplefactions.map;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

/**
 * A map payload already built on the server thread, to be written to disk later, possibly off it. The tree
 * holds only copied values, so writing it never reads live faction state. It is written to a temporary file
 * in the same folder and then moved over the target, so an upload never reads a half-written file and two
 * overlapping writes of the same payload cannot interleave.
 */
public record MapFile(File file, JsonElement json, boolean pretty) {

	public void write() throws IOException {
		Path target = file.toPath().toAbsolutePath();
		Path folder = target.getParent();
		Files.createDirectories(folder);
		// Created like a plain new file (umask and the folder's default ACL apply, as with the old in-place
		// writer), not with Files.createTempFile, which would make it owner-only on Linux.
		Path temp = folder.resolve(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
		try {
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				Gson gson = pretty ? new GsonBuilder().setPrettyPrinting().create() : new Gson();
				gson.toJson(json, writer);
			}
			try {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}
}
