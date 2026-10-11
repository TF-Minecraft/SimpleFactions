package net.tfminecraft.simplefactions.espionage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import com.google.gson.reflect.TypeToken;
import net.tfminecraft.simplefactions.database.JsonUtil;

/** Character identity, rather than faction membership, owns the permanent roll. */
public final class CharacterAptitudes {
    private final Path file;
    private final Map<String, Integer> aptitudes = new LinkedHashMap<>();

    public CharacterAptitudes(Path file) { this.file = file; }

    public void load() throws IOException {
        aptitudes.clear();
        if (!Files.exists(file)) return;
        Map<String, Integer> loaded = JsonUtil.GSON.fromJson(Files.readString(file),
                new TypeToken<Map<String, Integer>>() {}.getType());
        if (loaded == null) throw new IOException("Empty character aptitude registry: " + file);
        loaded.forEach((key, value) -> {
            if (key != null && value != null) aptitudes.put(key, EspionageMath.clamp(value, 0, 100));
        });
    }

    public void remember(String characterId, int aptitude) {
        if (characterId != null && !characterId.isBlank())
            aptitudes.putIfAbsent(characterId, EspionageMath.clamp(aptitude, 0, 100));
    }

    public int aptitude(String characterId, Supplier<Integer> roll) throws IOException {
        if (characterId == null || characterId.isBlank()) throw new IllegalArgumentException("Missing character identity");
        Integer existing = aptitudes.get(characterId);
        if (existing != null) return existing;
        int result = EspionageMath.clamp(roll.get(), 0, 100);
        aptitudes.put(characterId, result);
        try { save(); }
        catch (IOException error) { aptitudes.remove(characterId); throw error; }
        return result;
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path pending = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(pending, JsonUtil.GSON.toJson(aptitudes));
        try { Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
