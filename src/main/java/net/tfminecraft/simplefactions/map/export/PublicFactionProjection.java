package net.tfminecraft.simplefactions.map.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.tfminecraft.simplefactions.identity.Members;

/** Explicit map schema: private membership keys, offices, votes and accounting never leave storage. */
public final class PublicFactionProjection {
    private PublicFactionProjection() {}

    public static JsonObject faction(JsonObject source) {
        JsonObject result = fields(source, "id", "name", "rgb", "ruler title", "government", "culture",
                "religion", "capital", "rank", "founded at", "overlord", "tier index");
        for (String field : new String[] {"banner", "provinces", "titles", "relations", "trade relation", "treaty relation", "laws"}) {
            result.add(field, values(source, field));
        }
        leader(source, result, mainGuildDeadLeader(source));
        JsonArray guilds = new JsonArray();
        JsonArray members = new JsonArray();
        if (source.has("guilds") && source.get("guilds").isJsonArray()) {
            for (JsonElement entry : source.getAsJsonArray("guilds")) {
                if (!entry.isJsonObject()) continue;
                JsonObject guild = guild(entry.getAsJsonObject());
                guilds.add(guild);
                guild.getAsJsonArray("members").forEach(members::add);
            }
        }
        result.add("guilds", guilds);
        result.add("members", members);
        return result;
    }

    private static JsonObject guild(JsonObject source) {
        JsonObject result = fields(source, "id", "name", "rgb", "type", "capital", "stance");
        result.add("banner", values(source, "banner"));
        leader(source, result, source.get("dead leader"));
        JsonArray members = new JsonArray();
        for (JsonElement member : values(source, "members")) members.add(Members.display(member.getAsString()));
        result.add("members", members);
        JsonArray branches = new JsonArray();
        if (source.has("branches") && source.get("branches").isJsonArray()) {
            for (JsonElement branch : source.getAsJsonArray("branches")) {
                if (branch.isJsonObject()) branches.add(fields(branch.getAsJsonObject(), "id", "level"));
            }
        }
        result.add("branches", branches);
        return result;
    }

    /** A held realm keeps its dead leader on its main guild, the one sharing the realm's id. */
    private static JsonElement mainGuildDeadLeader(JsonObject source) {
        JsonElement id = source.get("id");
        if (id == null || !source.has("guilds") || !source.get("guilds").isJsonArray()) return null;
        for (JsonElement entry : source.getAsJsonArray("guilds")) {
            if (entry.isJsonObject() && id.equals(entry.getAsJsonObject().get("id"))) return entry.getAsJsonObject().get("dead leader");
        }
        return null;
    }

    private static void leader(JsonObject source, JsonObject target, JsonElement dead) {
        JsonElement stored = source.get("leader");
        String key = stored != null && stored.isJsonPrimitive() ? stored.getAsString() : null;
        boolean leaderless = (key == null || key.isBlank()) && dead != null && dead.isJsonPrimitive();
        if (leaderless) key = dead.getAsString();
        String name = Members.display(key) + (leaderless ? " (dead)" : "");
        String account = Members.accountName(key);
        // The website verifies these two legacy fields match before showing the character name.
        String publicLeader = account == null ? name : account;
        target.addProperty("leader", publicLeader);
        target.addProperty("leader character", name);
        target.addProperty("leader character of", publicLeader);
    }

    private static JsonObject fields(JsonObject source, String... fields) {
        JsonObject result = new JsonObject();
        for (String field : fields) {
            JsonElement value = source.get(field);
            if (value != null && value.isJsonPrimitive()) result.add(field, value.deepCopy());
        }
        return result;
    }

    private static JsonArray values(JsonObject source, String field) {
        JsonArray result = new JsonArray();
        if (source.has(field) && source.get(field).isJsonArray()) {
            for (JsonElement value : source.getAsJsonArray(field)) {
                if (value.isJsonPrimitive()) result.add(value.deepCopy());
            }
        }
        return result;
    }
}
