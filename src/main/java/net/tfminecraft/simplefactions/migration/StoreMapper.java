package net.tfminecraft.simplefactions.migration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterRef;

/**
 * Rewrites the name and account fields of one store into member keys. Names
 * resolve to an account, then to that account's reviewed heir; a name or
 * account that does not is replaced by a quarantine token and recorded.
 */
final class StoreMapper {
    enum Merge { SUM, FIRST, MIN }

    private final String store;
    private final CharacterMigration.Heirs heirs;
    private final Map<String, Optional<String>> names;
    private final Map<UUID, Optional<String>> accounts;
    private final Map<String, Quarantine.Record> records = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private boolean changed;

    StoreMapper(String store, CharacterMigration.Heirs heirs,
                Map<String, Optional<String>> names, Map<UUID, Optional<String>> accounts) {
        this.store = store;
        this.heirs = heirs;
        this.names = names;
        this.accounts = accounts;
    }

    Collection<Quarantine.Record> records() {
        return records.values();
    }

    List<String> warnings() {
        return warnings;
    }

    boolean changed() {
        return changed;
    }

    /* ---------- stores ---------- */

    /** {@code Data/<faction>.json}. */
    void faction(JsonObject root, String originalHash) {
        scalar(root, "leader", "$");
        forgetLeaderCharacter(root);
        String factionId = string(root, "id");
        for (JsonObject guild : objects(root, "guilds")) {
            String path = "$.guilds[" + string(guild, "id") + "]";
            if (!guild.has("incarnation")) {
                guild.addProperty("incarnation", UUID.nameUUIDFromBytes(("incarnation:" + store + ":"
                        + string(guild, "id") + ":" + originalHash).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
                changed = true;
            }
            scalar(guild, "leader", path);
            forgetLeaderCharacter(guild);
            list(guild, "members", path);
            list(guild, "dividendEligible", path);
            keys(guild, "citizen taxes", Merge.SUM, path);
            keys(guild, "deposits today", Merge.SUM, path);
            for (String ledger : List.of("ledger last day", "ledger lifetime")) {
                JsonObject sources = object(guild, ledger);
                if (sources == null) continue;
                for (String source : List.of("CITIZENS", "DEPOSITS")) {
                    keys(sources, source, Merge.SUM, path + "." + ledger);
                }
            }
            JsonObject company = object(guild, "company");
            if (company != null) {
                String companyPath = path + ".company";
                list(company, "enlisted", companyPath);
                keys(company, "activeWageOverrides", Merge.FIRST, companyPath);
                keys(company, "peacetimeWageOverrides", Merge.FIRST, companyPath);
                keys(company, "pendingWages", Merge.SUM, companyPath);
            }
        }
        oneGuildPerMember(root, factionId);
        JsonObject government = object(root, "governmentData");
        if (government != null) government(government, "$.governmentData");
    }

    /** {@code Wars/war_<id>.json}. */
    void war(JsonObject root) {
        scalar(root, "civilWarWantedLeaderName", "$");
        int index = 0;
        for (JsonObject move : objects(root, "civilWarMemberMoves")) {
            scalar(move, "player", "$.civilWarMemberMoves[" + index++ + "]");
        }
    }

    /** {@code Cache/logins.json}: days offline by name; merged names keep the most recent login. */
    void logins(JsonObject root) {
        rekey(root, Merge.MIN, "$");
    }

    /** {@code Cache/vehicle_fees.json}: last owners by name, paid builds by payer account. */
    void vehicleFees(JsonObject root) {
        JsonObject owners = object(root, "lastOwners");
        if (owners != null) {
            for (String vehicle : new ArrayList<>(owners.keySet())) {
                scalar(owners, vehicle, "$.lastOwners");
            }
        }
        int index = 0;
        for (JsonObject build : objects(root, "paidBuilds")) {
            accountField(build, "payerUuid", "$.paidBuilds[" + index++ + "]");
        }
    }

    /** {@code Cache/vehicles_registry.json}: a list of rows with the owning account. */
    void vehicleRegistry(JsonArray rows) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index) instanceof JsonObject row) {
                accountField(row, "playerUuid", "$[" + index + "]");
            }
        }
    }

    /* ---------- government ---------- */

    private void government(JsonObject government, String path) {
        list(government, "council members", path);
        list(government, "eligible voters", path);
        JsonObject candidates = object(government, "election candidates");
        if (candidates != null) {
            for (String type : candidates.keySet()) list(candidates, type, path + ".election candidates");
        }
        JsonObject votes = object(government, "election votes");
        if (votes != null) {
            for (String type : votes.keySet()) votes(votes, type, path + ".election votes");
        }
        JsonObject previous = object(government, "previous votes");
        if (previous != null) {
            for (String type : previous.keySet()) keys(previous, type, Merge.SUM, path + ".previous votes");
        }
        proposals(government, path);
        for (JsonObject movement : objects(government, "movements")) {
            String movementPath = path + ".movements[" + string(movement, "id") + "]";
            scalar(movement, "leader", movementPath);
            pool(object(movement, "supporters"), movementPath + ".supporters");
            int index = 0;
            for (JsonObject cause : objects(movement, "causes")) {
                String causePath = movementPath + ".causes[" + index++ + "]";
                scalar(cause, "leader", causePath);
                pool(object(cause, "members"), causePath + ".members");
                JsonObject proposal = object(cause, "proposal");
                if (proposal == null) continue;
                scalar(proposal, "proposer", causePath + ".proposal");
                if ("CHANGE_LEADER".equals(string(proposal, "action key"))) {
                    scalar(proposal, "target", causePath + ".proposal");
                }
            }
        }
    }

    private void pool(JsonObject pool, String path) {
        if (pool != null) list(pool, "citizens", path);
    }

    /** Council proposals are {@code proposer:kind:...}; a leader change ends with the wanted leader. */
    private void proposals(JsonObject government, String path) {
        if (!(government.get("proposals") instanceof JsonArray proposals)) return;
        JsonArray rewritten = new JsonArray();
        for (int index = 0; index < proposals.size(); index++) {
            JsonElement element = proposals.get(index);
            String text = string(element);
            int separator = text == null ? -1 : text.indexOf(':');
            if (separator <= 0) {
                rewritten.add(element);
                continue;
            }
            String entryPath = path + ".proposals[" + index + "]";
            String rest = text.substring(separator + 1);
            String proposer = name(text.substring(0, separator), entryPath, element);
            String changeLeader = "action:CHANGE_LEADER:";
            if (rest.startsWith(changeLeader) && rest.length() > changeLeader.length()) {
                rest = changeLeader + name(rest.substring(changeLeader.length()), entryPath, element);
            }
            rewritten.add(proposer + ":" + rest);
        }
        government.add("proposals", rewritten);
    }

    /** Ballots by voter: a merged voter keeps the first ballot; candidates are names too. */
    private void votes(JsonObject parent, String field, String path) {
        JsonObject ballots = object(parent, field);
        if (ballots == null) return;
        String ballotsPath = path + "." + field;
        JsonObject rewritten = new JsonObject();
        for (Map.Entry<String, JsonElement> ballot : ballots.entrySet()) {
            JsonObject fragment = new JsonObject();
            fragment.add(ballot.getKey(), ballot.getValue());
            String voter = name(ballot.getKey(), ballotsPath, fragment);
            if (rewritten.has(voter)) continue;
            String candidate = string(ballot.getValue());
            rewritten.add(voter, candidate == null ? ballot.getValue()
                    : new JsonPrimitive(name(candidate, ballotsPath, fragment)));
        }
        parent.add(field, rewritten);
    }

    /* ---------- field helpers ---------- */

    private void scalar(JsonObject object, String field, String path) {
        String value = string(object.get(field));
        if (value == null) return;
        object.addProperty(field, name(value, path + "." + field, object.get(field)));
    }

    private void list(JsonObject object, String field, String path) {
        if (!(object.get(field) instanceof JsonArray values)) return;
        String listPath = path + "." + field;
        Set<String> seen = new LinkedHashSet<>();
        JsonArray rewritten = new JsonArray();
        for (int index = 0; index < values.size(); index++) {
            JsonElement element = values.get(index);
            String value = string(element);
            if (value == null) {
                rewritten.add(element);
                continue;
            }
            String key = name(value, listPath + "[" + index + "]", element);
            if (seen.add(key)) rewritten.add(key);
            else changed = true;
        }
        object.add(field, rewritten);
    }

    private void keys(JsonObject parent, String field, Merge merge, String path) {
        if (parent.get(field) instanceof JsonObject map) rekey(map, merge, path + "." + field);
    }

    private void rekey(JsonObject map, Merge merge, String path) {
        Map<String, JsonElement> rewritten = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : map.entrySet()) {
            JsonObject fragment = new JsonObject();
            fragment.add(entry.getKey(), entry.getValue());
            String key = name(entry.getKey(), path + "[" + entry.getKey() + "]", fragment);
            JsonElement existing = rewritten.get(key);
            rewritten.put(key, existing == null ? entry.getValue() : merge(existing, entry.getValue(), merge));
        }
        for (String old : new ArrayList<>(map.keySet())) map.remove(old);
        rewritten.forEach(map::add);
    }

    static JsonElement merge(JsonElement kept, JsonElement added, Merge merge) {
        if (merge == Merge.FIRST || !isNumber(kept) || !isNumber(added)) return kept;
        double a = kept.getAsDouble();
        double b = added.getAsDouble();
        if (merge == Merge.MIN) return new JsonPrimitive(Math.min(a, b));
        boolean whole = kept.getAsString().matches("-?\\d+") && added.getAsString().matches("-?\\d+");
        return whole ? new JsonPrimitive(kept.getAsLong() + added.getAsLong()) : new JsonPrimitive(a + b);
    }

    private static boolean isNumber(JsonElement element) {
        return element instanceof JsonPrimitive primitive && primitive.isNumber();
    }

    private void accountField(JsonObject row, String field, String path) {
        String value = string(row.get(field));
        if (value == null) return;
        row.addProperty(field, account(value, path + "." + field, row));
    }

    /**
     * A realm's members belong to one guild. Merged names can leave a key in
     * several guilds; it stays where it leads, else in the first ordinary
     * guild, else in the realm's own guild.
     */
    private void oneGuildPerMember(JsonObject root, String factionId) {
        List<JsonObject> guilds = objects(root, "guilds");
        JsonObject base = base(guilds, factionId);
        List<JsonObject> leadersFirst = new ArrayList<>();
        if (base != null) leadersFirst.add(base);
        for (JsonObject guild : guilds) if (guild != base) leadersFirst.add(guild);
        Map<String, JsonObject> home = new LinkedHashMap<>();
        for (JsonObject guild : leadersFirst) {
            String leader = string(guild, "leader");
            if (leader != null) home.putIfAbsent(leader, guild);
        }
        for (JsonObject guild : guilds) {
            if (guild == base) continue;
            for (String member : strings(guild, "members")) home.putIfAbsent(member, guild);
        }
        for (JsonObject guild : guilds) {
            if (!(guild.get("members") instanceof JsonArray members)) continue;
            JsonArray kept = new JsonArray();
            for (JsonElement element : members) {
                String member = string(element);
                JsonObject owner = member == null ? null : home.get(member);
                if (owner == null || owner == guild) {
                    kept.add(element);
                } else {
                    changed = true;
                    warnings.add(store + ": " + member + " was in several guilds; kept in "
                            + string(owner, "id") + ", removed from " + string(guild, "id"));
                }
            }
            guild.add("members", kept);
        }
    }

    private static JsonObject base(List<JsonObject> guilds, String factionId) {
        for (JsonObject guild : guilds) {
            if (factionId != null && factionId.equals(string(guild, "id"))) return guild;
        }
        return null;
    }

    private void forgetLeaderCharacter(JsonObject object) {
        // Leader names are re-read from the leader's character when the plugin starts.
        if (object.remove("leader character") != null) changed = true;
        if (object.remove("leader character of") != null) changed = true;
    }

    /* ---------- resolution ---------- */

    /** The member key for a stored name, or the quarantine token holding its place. */
    String name(String raw, String path, JsonElement fragment) {
        if (Members.isQuarantined(raw)) return raw;
        UUID asKey = Members.characterId(raw);
        if (asKey != null) return Members.key(asKey);
        Optional<String> key = names.computeIfAbsent(raw.toLowerCase(Locale.ROOT), lower -> heirs.account(raw)
                .flatMap(this::heirKey));
        changed = true;
        if (key.isPresent()) return key.get();
        Quarantine.Record record = record(Quarantine.NAME, raw, null);
        record.occurrences.add(new Quarantine.Occurrence(path, fragment.deepCopy()));
        return record.token;
    }

    /** The heir character's ID for an account field; unmapped accounts stay and are recorded. */
    String account(String raw, String path, JsonElement fragment) {
        UUID account;
        try {
            account = UUID.fromString(raw);
        } catch (IllegalArgumentException notAnAccount) {
            return raw;
        }
        Optional<String> key = heirKey(account);
        if (key.isPresent()) {
            changed = true;
            return key.get();
        }
        Quarantine.Record record = record(Quarantine.ACCOUNT, raw.toLowerCase(Locale.ROOT), raw);
        record.occurrences.add(new Quarantine.Occurrence(path, fragment.deepCopy()));
        return raw;
    }

    private Optional<String> heirKey(UUID account) {
        return accounts.computeIfAbsent(account, id -> heirs.heir(id).map(CharacterRef::characterId).map(Members::key));
    }

    private Quarantine.Record record(String kind, String original, String token) {
        String id = "q" + MigrationFiles.sha256(store + "\n" + kind + "\n" + original.toLowerCase(Locale.ROOT)).substring(0, 12);
        return records.computeIfAbsent(id, ignored -> {
            Quarantine.Record record = new Quarantine.Record();
            record.id = id;
            record.store = store;
            record.kind = kind;
            record.original = original;
            record.token = token != null ? token : Members.QUARANTINED + id;
            return record;
        });
    }

    /* ---------- JSON helpers ---------- */

    private static String string(JsonObject object, String field) {
        return object == null ? null : string(object.get(field));
    }

    static String string(JsonElement element) {
        return element instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : null;
    }

    private static JsonObject object(JsonObject parent, String field) {
        return parent.get(field) instanceof JsonObject object ? object : null;
    }

    private static List<JsonObject> objects(JsonObject parent, String field) {
        List<JsonObject> objects = new ArrayList<>();
        if (parent.get(field) instanceof JsonArray array) {
            array.forEach(element -> {
                if (element instanceof JsonObject object) objects.add(object);
            });
        }
        return objects;
    }

    private static List<String> strings(JsonObject parent, String field) {
        List<String> values = new ArrayList<>();
        if (parent.get(field) instanceof JsonArray array) {
            Consumer<JsonElement> add = element -> {
                String value = string(element);
                if (value != null) values.add(value);
            };
            array.forEach(add);
        }
        return values;
    }
}
