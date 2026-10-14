package net.tfminecraft.simplefactions.war.battle.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.WarbandData;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;

/** Expands account routing into reviewed character authority without replacing existing refs. */
public final class WarbandCharacters {
    private static final String VERSION = "character-identity-version";
    private static final com.google.gson.Gson FIELDS = JsonUtil.GSON.newBuilder().serializeNulls().create();
    private static final Set<Path> HELD = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private record Backup(String source, String hash) {}
    private record Plan(String hash, String afterHash, String after) {}

    private WarbandCharacters() {}

    public static Warband read(File file) throws IOException {
        return read(file.toPath(), WarbandCharacters::heirsReady, account -> Characters.legacyHeir(account));
    }

    static Warband read(Path file, BooleanSupplier ready,
            Function<UUID, Optional<CharacterRef>> heirs) throws IOException {
        HELD.add(file.toAbsolutePath());
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject raw = parse(source);
            validate(raw);
            if (!file.getFileName().toString().equals("warband_" + raw.get("id").getAsString() + ".json")) {
                throw new IOException("Warband filename conflicts with its stored id: " + file);
            }
            if (!raw.has(VERSION) && ready.getAsBoolean()) raw = expand(file, source, raw, heirs);
            JsonObject typed = raw.deepCopy();
            typed.remove("memberCharacters");
            typed.remove("invitedCharacters");
            WarbandData data = JsonUtil.GSON.fromJson(typed, WarbandData.class);
            data.memberCharacters = refs(raw, "memberCharacters");
            data.invitedCharacters = refs(raw, "invitedCharacters");
            Warband band = WarbandMapper.fromData(data);
            if (band == null) throw new IOException("Invalid warband identity: " + file);
            band.restoreStoredFields(raw);
            HELD.remove(file.toAbsolutePath());
            return band;
        } catch (RuntimeException error) {
            throw new IOException("Warband held for identity review: " + file, error);
        }
    }

    private static void validate(JsonObject raw) {
        if (raw.has(VERSION) && (!raw.get(VERSION).isJsonPrimitive()
                || !raw.get(VERSION).getAsJsonPrimitive().isNumber()
                || raw.get(VERSION).getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) != 0)) {
            throw new IllegalArgumentException("Unsupported warband identity version");
        }
        for (String field : java.util.List.of("memberIds", "invitedIds", "dummyMemberIds")) {
            if (raw.has(field) && !raw.get(field).isJsonNull()) {
                for (JsonElement id : raw.getAsJsonArray(field)) UUID.fromString(id.getAsString());
            }
        }
        if (raw.has("leaderId") && !raw.get("leaderId").isJsonNull()) {
            UUID.fromString(raw.get("leaderId").getAsString());
        }
    }

    private static boolean heirsReady() {
        try {
            Characters.class.getMethod("legacyHeirsReady");
            Characters.class.getMethod("legacyHeir", UUID.class);
            return Characters.legacyHeirsReady();
        } catch (ReflectiveOperationException | LinkageError absent) {
            return false;
        }
    }

    private static JsonObject expand(Path file, String source, JsonObject raw,
            Function<UUID, Optional<CharacterRef>> heirs) throws IOException {
        Path folder = file.getParent().resolve("character-identity-v1").resolve(file.getFileName().toString());
        Path planFile = folder.resolve("plan.json");
        Path backupFile = folder.resolve("source.json");
        String hash = hash(source);
        Plan plan;
        if (Files.exists(planFile)) {
            plan = DurableJson.read(planFile, Plan.class);
            if (!hash.equals(plan.hash()) || plan.after() == null
                    || !hash(plan.after()).equals(plan.afterHash())) {
                throw new IOException("Warband changed after its fixed identity plan: " + file);
            }
            validate(parse(plan.after()));
        } else {
            JsonObject after = raw.deepCopy();
            fill(after, "memberIds", "memberCharacters", heirs);
            fill(after, "invitedIds", "invitedCharacters", heirs);
            after.addProperty(VERSION, 1);
            String planned = FIELDS.toJson(after);
            plan = new Plan(hash, hash(planned), planned);
            // The complete source text preserves original bytes, not just recognised fields.
            if (!Files.exists(backupFile)) DurableJson.write(backupFile, new Backup(source, hash), FIELDS);
            DurableJson.write(planFile, plan, FIELDS);
        }
        Backup backup = DurableJson.read(backupFile, Backup.class);
        if (backup.source() == null || !hash.equals(backup.hash()) || !hash.equals(hash(backup.source()))) {
            throw new IOException("Warband identity backup does not match its plan: " + file);
        }
        if (!hash(Files.readString(file, StandardCharsets.UTF_8)).equals(hash)) {
            throw new IOException("Warband changed before identity publication: " + file);
        }
        JsonObject after = parse(plan.after());
        validateRewrite(raw, after);
        if (!hash(FIELDS.toJson(after)).equals(plan.afterHash())) {
            throw new IOException("Planned output bytes differ from their publication encoding");
        }
        DurableJson.write(file, after, FIELDS);
        return after;
    }

    private static void validateRewrite(JsonObject before, JsonObject after) throws IOException {
        validate(after);
        if (!after.has(VERSION)) throw new IOException("Planned warband lacks its identity version");
        JsonObject original = before.deepCopy(), output = after.deepCopy();
        for (String field : java.util.List.of("memberCharacters", "invitedCharacters")) {
            JsonObject refs = object(after.get(field));
            if (before.has(field)) for (var entry : object(before.get(field)).entrySet()) {
                if (!java.util.Objects.equals(entry.getValue(), refs.get(entry.getKey()))) {
                    throw new IOException("Planned warband changed an existing character field");
                }
            }
            original.remove(field);
            output.remove(field);
        }
        original.remove(VERSION);
        output.remove(VERSION);
        if (!original.equals(output)) throw new IOException("Planned warband changed its source identity or state");
    }

    private static void fill(JsonObject raw, String ids, String field,
            Function<UUID, Optional<CharacterRef>> heirs) {
        JsonObject characters = raw.has(field) ? object(raw.get(field)) : new JsonObject();
        if (raw.has(ids) && !raw.get(ids).isJsonNull()) for (JsonElement element : raw.getAsJsonArray(ids)) {
            UUID account = UUID.fromString(element.getAsString());
            if (Warband.isPendingLeader(raw.get("id").getAsString(), account)
                    || (raw.has("dummyMemberIds") && !raw.get("dummyMemberIds").isJsonNull()
                            && raw.getAsJsonArray("dummyMemberIds").contains(element))) continue;
            String key = account.toString();
            // Present-but-invalid/null refs stay held; they are never treated as legacy absence.
            boolean present = characters.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(key));
            if (!present) {
                CharacterRef heir = heirs.apply(account).filter(ref -> account.equals(ref.ownerId())).orElse(null);
                characters.add(key, JsonUtil.GSON.toJsonTree(heir));
            }
        }
        raw.add(field, characters);
    }

    private static Map<UUID, CharacterRef> refs(JsonObject raw, String field) {
        Map<UUID, CharacterRef> refs = new LinkedHashMap<>();
        Set<UUID> invalid = new HashSet<>();
        if (!raw.has(field)) return refs;
        for (Map.Entry<String, JsonElement> entry : object(raw.get(field)).entrySet()) {
            UUID account;
            try {
                account = UUID.fromString(entry.getKey());
            } catch (RuntimeException metadata) {
                continue;
            }
            try {
                CharacterRef ref = JsonUtil.GSON.fromJson(entry.getValue(), CharacterRef.class);
                if (ref == null || !ref.ownerId().equals(account)
                        || (refs.containsKey(account) && !refs.get(account).equals(ref))) {
                    invalid.add(account);
                    refs.remove(account);
                } else if (!invalid.contains(account)) refs.put(account, ref);
            } catch (RuntimeException malformed) {
                // Retain the raw entry, but grant it no character authority.
                invalid.add(account);
                refs.remove(account);
            }
        }
        return refs;
    }

    public static boolean isHeld(File file) {
        return HELD.contains(file.toPath().toAbsolutePath());
    }

    public static void save(File file, Warband band) throws IOException {
        if (isHeld(file)) throw new IOException("Cannot overwrite held warband: " + file);
        HELD.add(file.toPath().toAbsolutePath());
        try {
            saveChecked(file, band);
            HELD.remove(file.toPath().toAbsolutePath());
        } catch (RuntimeException corrupt) {
            throw new IOException("Warband save held for identity review: " + file, corrupt);
        }
    }

    private static void saveChecked(File file, Warband band) throws IOException {
        if (!file.exists() && !band.getStoredFields().keySet().isEmpty()) {
            throw new IOException("Loaded warband source disappeared: " + file);
        }
        JsonObject raw = file.isFile()
                ? parse(Files.readString(file.toPath(), StandardCharsets.UTF_8))
                : band.getStoredFields();
        validate(raw);
        JsonObject known = FIELDS.toJsonTree(WarbandMapper.toData(band)).getAsJsonObject();
        JsonObject previous = raw.deepCopy();
        if (file.isFile()) {
            JsonObject expected = band.getStoredFields();
            for (String field : known.keySet()) {
                if (!java.util.Objects.equals(expected.get(field), raw.get(field))) {
                    throw new IOException("Warband changed outside its loaded writer: " + file);
                }
            }
        }
        for (Map.Entry<String, JsonElement> entry : known.entrySet()) {
            String field = entry.getKey();
            if (field.equals("memberCharacters") || field.equals("invitedCharacters")) {
                patchRefs(raw, field, entry.getValue().getAsJsonObject(),
                        known.getAsJsonArray(field.equals("memberCharacters") ? "memberIds" : "invitedIds"),
                        previous.getAsJsonArray(field.equals("memberCharacters") ? "memberIds" : "invitedIds"));
            } else raw.add(field, entry.getValue());
        }
        if (!file.isFile()) raw.addProperty(VERSION, 1);
        DurableJson.write(file.toPath(), raw, FIELDS);
        band.restoreStoredFields(raw);
    }

    private static void patchRefs(JsonObject raw, String field, JsonObject known,
            com.google.gson.JsonArray ids, com.google.gson.JsonArray originalIds) {
        JsonObject merged = raw.has(field) ? object(raw.get(field)).deepCopy() : new JsonObject();
        Set<String> present = new HashSet<>();
        ids.forEach(id -> present.add(id.getAsString().toLowerCase(java.util.Locale.ROOT)));
        Set<String> previous = new HashSet<>();
        if (originalIds != null) originalIds.forEach(id -> previous.add(id.getAsString().toLowerCase(java.util.Locale.ROOT)));
        for (String key : Set.copyOf(merged.keySet())) {
            // Unknown map metadata is not an ownership row to prune.
            try { UUID.fromString(key); }
            catch (IllegalArgumentException metadata) { continue; }
            if (previous.contains(key.toLowerCase(java.util.Locale.ROOT))
                    && !present.contains(key.toLowerCase(java.util.Locale.ROOT))) merged.remove(key);
        }
        for (Map.Entry<String, JsonElement> entry : known.entrySet()) {
            String key = merged.keySet().stream().filter(old -> old.equalsIgnoreCase(entry.getKey()))
                    .findFirst().orElse(entry.getKey());
            JsonElement existing = merged.get(key);
            JsonObject value = existing != null && existing.isJsonObject()
                    ? existing.getAsJsonObject().deepCopy() : new JsonObject();
            entry.getValue().getAsJsonObject().entrySet().forEach(part -> value.add(part.getKey(), part.getValue()));
            merged.add(key, value);
        }
        raw.add(field, merged);
    }

    private static JsonObject object(JsonElement element) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException("Expected identity object");
        return element.getAsJsonObject();
    }

    private static JsonObject parse(String source) throws IOException {
        // Duplicate keys would silently choose one owner or discard unknown state.
        try (var reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(source))) {
            reader.setStrictness(com.google.gson.Strictness.STRICT);
            checkNames(reader);
            reader.peek(); // Strict mode refuses another top-level value.
        }
        return object(JsonParser.parseString(source));
    }

    private static void checkNames(com.google.gson.stream.JsonReader reader) throws IOException {
        if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
            reader.beginObject();
            Set<String> names = new HashSet<>();
            while (reader.hasNext()) {
                if (!names.add(reader.nextName())) throw new IOException("Duplicate warband field");
                checkNames(reader);
            }
            reader.endObject();
        } else if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
            reader.beginArray();
            while (reader.hasNext()) checkNames(reader);
            reader.endArray();
        } else reader.skipValue();
    }

    private static String hash(String source) {
        return com.google.common.hash.Hashing.sha256().hashString(source, StandardCharsets.UTF_8).toString();
    }
}
