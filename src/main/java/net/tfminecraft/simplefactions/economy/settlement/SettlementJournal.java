package net.tfminecraft.simplefactions.economy.settlement;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.database.JsonUtil;

/** One durable, immutable realm-wide plan and one atomic posting per day. */
public final class SettlementJournal {
    /** Changes contain exact after-values, so applying them again after an interrupted save is safe. */
    public record Plan(long day, List<Leg> legs, JsonObject changes) {
        public Plan {
            if (day < 0) throw new IllegalArgumentException("Invalid settlement day");
            legs = legs.isEmpty() ? List.of(new Leg(Denars.MINT, 0)) : List.copyOf(legs);
            changes = changes.deepCopy();
        }

        @Override
        public JsonObject changes() { return changes.deepCopy(); }
    }

    @FunctionalInterface
    public interface Effects {
        /** Applies the recorded after-values and durably saves every affected store. */
        void apply(Plan plan) throws IOException;
    }

    private static final class Saved {
        Plan plan;
        String hash;
        boolean done;
    }

    private final Path directory;

    public SettlementJournal(Path directory) {
        this.directory = directory;
    }

    public Result settle(long day, Supplier<Plan> calculate, Effects effects) throws IOException {
        Path path = directory.resolve(day + ".json");
        Saved saved;
        if (Files.exists(path)) {
            saved = read(path);
        } else {
            Plan plan = calculate.get();
            if (plan.day() != day) throw new IOException("Settlement day changed during planning");
            saved = new Saved();
            saved.plan = plan;
            saved.hash = hash(plan);
            DurableJson.write(path, saved);
        }
        return finish(path, saved, effects);
    }

    /** Replays stored plans before any membership or economic state can change. Never recalculates. */
    public void recover(Effects effects) throws IOException {
        if (!Files.exists(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("[0-9]+\\.json"))
                    .sorted(Comparator.comparingLong(SettlementJournal::dayOf)).toList()) {
                Result result = finish(file, read(file), effects);
                if (result != Result.APPLIED) throw new IOException("Settlement remains " + result + ": " + file);
            }
        }
    }

    private Result finish(Path path, Saved saved, Effects effects) throws IOException {
        if (saved.done) {
            if (Denars.status("sf:settle:" + saved.plan.day()).orElse(null) != Result.APPLIED) {
                throw new IOException("Completed settlement is absent from the money ledger: " + path);
            }
            return Result.APPLIED;
        }
        Result result = Denars.post("sf:settle:" + saved.plan.day(), "sf:settlement", saved.plan.legs());
        if (result != Result.APPLIED) return result;
        effects.apply(saved.plan);
        saved.done = true;
        DurableJson.write(path, saved);
        return result;
    }

    private static Saved read(Path path) throws IOException {
        Saved saved = DurableJson.read(path, Saved.class);
        if (saved.plan == null || !hash(saved.plan).equals(saved.hash) || saved.plan.day() != dayOf(path)) {
            throw new IOException("Settlement plan hash or day mismatch: " + path);
        }
        return saved;
    }

    private static long dayOf(Path path) {
        String name = path.getFileName().toString();
        return Long.parseLong(name.substring(0, name.length() - ".json".length()));
    }

    @SuppressWarnings("deprecation")
    private static String hash(Plan plan) {
        return com.google.common.hash.Hashing.sha256().hashString(JsonUtil.GSON.toJson(plan), StandardCharsets.UTF_8).toString();
    }
}
