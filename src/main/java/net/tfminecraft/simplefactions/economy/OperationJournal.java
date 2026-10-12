package net.tfminecraft.simplefactions.economy;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.database.JsonUtil;

/** Caller-owned operation IDs and replayable domain after-values around one atomic money posting. */
public final class OperationJournal {
    public record Intent(String id, String reason, List<Leg> legs, JsonObject changes) {
        public Intent {
            if (id == null || id.isBlank() || reason == null || reason.isBlank() || legs.isEmpty())
                throw new IllegalArgumentException("An intent needs an ID, reason and balanced legs");
            legs = List.copyOf(legs);
            changes = changes.deepCopy();
        }
        @Override public JsonObject changes() { return changes.deepCopy(); }
    }
    @FunctionalInterface public interface Effects { void apply(Intent intent) throws IOException; }
    private static final class Saved {
        Intent intent;
        String hash;
        Result completed;
    }
    private final Path folder;
    public OperationJournal(Path folder) { this.folder = folder; }

    /** Replays the original values for an intrinsic retry, without rebuilding them from changed state. */
    public java.util.Optional<Result> resume(String id, Effects effects) throws IOException {
        Path path = folder.resolve(hash(id) + ".json");
        return Files.exists(path) ? java.util.Optional.of(finish(path, read(path), effects)) : java.util.Optional.empty();
    }

    public Result execute(Intent intent, Effects effects) throws IOException {
        Path path = folder.resolve(hash(intent.id()) + ".json");
        Saved saved;
        if (Files.exists(path)) {
            saved = read(path);
            if (!JsonUtil.GSON.toJsonTree(saved.intent).equals(JsonUtil.GSON.toJsonTree(intent))) return Result.CONFLICT;
        } else {
            saved = new Saved();
            saved.intent = intent;
            saved.hash = hash(JsonUtil.GSON.toJson(intent));
            DurableJson.write(path, saved);
        }
        return finish(path, saved, effects);
    }

    public void recover(Effects effects) throws IOException {
        if (!Files.exists(folder)) return;
        try (var files = Files.list(folder)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                Result result = finish(path, read(path), effects);
                if (result != Result.APPLIED && result != Result.REFUSED)
                    throw new IOException("Money intent remains " + result + ": " + path);
            }
        }
    }

    /** Restores split-file topology from already posted intents before domain objects are loaded. */
    public void recoverAppliedEffects(Effects effects) throws IOException {
        if (!Files.exists(folder)) return;
        try (var files = Files.list(folder)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                Saved saved = read(path);
                if (saved.completed == null && Denars.status(saved.intent.id()).orElse(null) == Result.APPLIED)
                    effects.apply(saved.intent);
            }
        }
    }

    private Result finish(Path path, Saved saved, Effects effects) throws IOException {
        Intent intent = saved.intent;
        if (saved.completed != null) {
            if (Denars.status(intent.id()).orElse(null) != saved.completed)
                throw new IOException("Completed money intent disagrees with ledger: " + intent.id());
            return saved.completed;
        }
        Result result = Denars.post(intent.id(), intent.reason(), intent.legs());
        if (result == Result.APPLIED) effects.apply(intent);
        if (result == Result.APPLIED || result == Result.REFUSED) {
            saved.completed = result;
            DurableJson.write(path, saved);
        }
        return result;
    }

    private static Saved read(Path path) throws IOException {
        Saved saved = DurableJson.read(path, Saved.class);
        if (saved.intent == null || !hash(JsonUtil.GSON.toJson(saved.intent)).equals(saved.hash)
                || !path.getFileName().toString().equals(hash(saved.intent.id()) + ".json")
                || saved.completed == Result.CONFLICT || saved.completed == Result.UNAVAILABLE)
            throw new IOException("Invalid money intent: " + path);
        return saved;
    }

    @SuppressWarnings("deprecation")
    private static String hash(String value) {
        return com.google.common.hash.Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString();
    }
}
