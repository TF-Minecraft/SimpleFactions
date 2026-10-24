package net.tfminecraft.simplefactions.economy;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.economy.settlement.SettlementGuilds;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.*;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import org.bukkit.entity.Player;

/** Pure topology plans; money commits once, then exact realm files and live topology follow. */
public final class TreasuryTopology {
    static Path directory = Path.of("plugins/SimpleFactions/Data");
    private TreasuryTopology() {}

    public static boolean capital(Player actor, Faction faction, int province, String name,
            boolean rename, double cost, String operationId) {
        return capital(actor, faction, province, name, rename, cost, operationId, true);
    }

    /** {@code countMove} false leaves the faction's free capital change unspent, for staff moves. */
    public static boolean capital(Player actor, Faction faction, int province, String name,
            boolean rename, double cost, String operationId, boolean countMove) {
        Boolean prior = TreasuryActions.resume(operationId, actor);
        if (prior != null) return prior;
        var result = rename ? faction.getSettlementHandler().rename(province, name, true)
                : faction.getSettlementHandler().validateFactionCapital(actor, province, name);
        if (!result.isSuccess()) { actor.sendMessage(result.getMessage()); return false; }
        JsonObject before = capture(), after = before.deepCopy();
        JsonObject state = realm(after, faction);
        if (rename) {
            settlementAt(state, province).addProperty("name", StringFormatter.formatHex(Formatter.formatName(name)));
        } else {
            found(state, province, name, actor);
            int old = state.get("capital").getAsInt();
            setCapital(state, faction.getId(), province);
            depart(after, state, old);
            revalidate(after, faction);
        }
        state.addProperty("capital moves", faction.getCapitalMoves() + (countMove ? 1 : 0));
        return commit(actor, SettlementGuilds.capital(faction), cost, operationId, "Capital change", before, after);
    }

    public static boolean relocate(Player actor, Guild guild, Faction target, int province,
            String name, double cost, String operationId) {
        return relocate(actor, guild, target, province, name, cost, operationId, actor);
    }

    public static boolean relocate(Player actor, Guild guild, Faction target, int province,
            String name, double cost, String operationId, Player founder) {
        Boolean prior = TreasuryActions.resume(operationId, actor);
        if (prior != null) return prior;
        JsonObject before = capture(), after = before.deepCopy();
        Faction origin = guild.getFaction();
        JsonObject from = realm(after, origin), to = realm(after, target);
        int old = guild.getCapital();
        if (origin != target) {
            JsonObject moving = guild(from, guild.getId());
            from.getAsJsonArray("guilds").remove(moving);
            depart(after, from, old);
            revalidate(after, origin);
            to.getAsJsonArray("guilds").add(moving);
        } else if (!target.hasProvince(province)) {
            // A province acquired by somebody else while the prompt was open is a stale destination.
            if (FactionManager.getByProvince(province) != null
                    || net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock.isLocked(target)
                    || !target.getProvinceHandler().canClaim(province, true, guild)
                    || target.getPrestige() < TitleManager.getClaimCost(target)
                    || TitleLoader.getByProvince(province) == null
                       && target.getUntitledProvinces().size() >= net.tfminecraft.simplefactions.Cache.maxUntitledProvinces) {
                actor.sendMessage("§cRelocation failed, cannot claim province!"); return false;
            }
            to.getAsJsonArray("provinces").add(province);
        }
        setCapital(to, guild.getId(), province);
        found(to, province, name, founder);
        if (origin == target) depart(after, from, old);
        try {
            if (origin != target) {
                // Preserve the complete moving guild even if a crash leaves neither realm file holding it.
                save(origin);
                JsonObject file = DurableJson.read(directory.resolve(origin.getId() + ".json"), JsonObject.class);
                for (JsonElement value : file.getAsJsonArray("guilds")) {
                    if (guild.getIncarnation().equals(value.getAsJsonObject().get("incarnation").getAsString())) {
                        after.getAsJsonObject("movingGuilds").add(guild.getIncarnation(), value.deepCopy());
                    }
                }
                if (!after.getAsJsonObject("movingGuilds").has(guild.getIncarnation())) throw new IOException("Moving guild was not persisted");
            }
        } catch (IOException error) { EconomyRuntime.failClosed(error); return false; }
        return commit(actor, guild, cost, operationId, "Guild relocation", before, after);
    }

    private static boolean commit(Player actor, Guild payer, double cost, String id, String reason,
            JsonObject before, JsonObject after) {
        JsonObject oldRealms = before.getAsJsonObject("realms"), newRealms = after.getAsJsonObject("realms");
        for (JsonElement state : newRealms.asMap().values()) state.getAsJsonObject().add("guilds", sorted(state.getAsJsonObject().get("guilds")));
        for (String key : new ArrayList<>(oldRealms.keySet())) {
            if (oldRealms.get(key).equals(newRealms.get(key))) { oldRealms.remove(key); newRealms.remove(key); }
        }
        TreasuryActions.Plan plan = new TreasuryActions.Plan(id, reason, actor, payer)
                .transfer(TreasuryActions.account(payer), Denars.MINT, Money.cents(cost));
        try {
            for (String key : newRealms.keySet()) {
                Faction faction = FactionManager.getByString(key);
                save(faction);
                plan.capture(SettlementGuilds.capital(faction));
            }
        } catch (IOException error) { EconomyRuntime.failClosed(error); return false; }
        return plan.effect("topology", payer, "", before, after).commit(actor);
    }

    static JsonObject capture() {
        JsonObject result = new JsonObject(), realms = new JsonObject();
        result.add("realms", realms); result.add("movingGuilds", new JsonObject());
        for (Faction faction : FactionManager.getCopy()) realms.add(faction.getId(), snapshot(faction));
        return result;
    }

    static JsonObject snapshot(Faction faction) {
        JsonObject data = new JsonObject();
        data.addProperty("incarnation", SettlementGuilds.capital(faction).getIncarnation());
        data.addProperty("capital", faction.getCapital()); data.addProperty("capital moves", faction.getCapitalMoves());
        data.add("provinces", JsonUtil.GSON.toJsonTree(faction.getProvinces()));
        data.add("titles", JsonUtil.GSON.toJsonTree(faction.getTitles().stream().map(Title::getId).toList()));
        data.add("settlements", sorted(faction.getSettlementHandler().serialize()));
        data.add("installations", sorted(faction.getInstallationHandler().serialize()));
        data.add("installation queue", JsonUtil.GSON.toJsonTree(faction.getInstallationHandler().serializeConstruction()));
        JsonArray guilds = new JsonArray();
        for (Guild guild : faction.getGuildHandler().getGuilds().stream().sorted(Comparator.comparing(Guild::getId)).toList()) {
            JsonObject ref = new JsonObject(); ref.addProperty("id", guild.getId());
            ref.addProperty("incarnation", guild.getIncarnation()); ref.addProperty("capital", guild.getCapital()); guilds.add(ref);
        }
        data.add("guilds", guilds); return data;
    }

    private static JsonArray sorted(Object values) {
        List<JsonElement> list = new ArrayList<>(); JsonUtil.GSON.toJsonTree(values).getAsJsonArray().forEach(list::add);
        list.sort(Comparator.comparing(v -> v.getAsJsonObject().get("id").getAsString()));
        JsonArray result = new JsonArray(); list.forEach(result::add); return result;
    }
    private static JsonObject realm(JsonObject plan, Faction faction) { return plan.getAsJsonObject("realms").getAsJsonObject(faction.getId()); }
    private static JsonObject guild(JsonObject realm, String id) {
        for (JsonElement g : realm.getAsJsonArray("guilds")) if (id.equals(g.getAsJsonObject().get("id").getAsString())) return g.getAsJsonObject();
        throw new IllegalStateException("Missing topology guild " + id);
    }
    private static JsonObject settlementAt(JsonObject realm, int province) {
        for (JsonElement s : realm.getAsJsonArray("settlements")) if (s.getAsJsonObject().get("centerProvince").getAsInt() == province) return s.getAsJsonObject();
        return null;
    }
    private static void found(JsonObject realm, int province, String name, Player actor) {
        if (settlementAt(realm, province) != null) return;
        Settlement city = new Settlement(Formatter.formatId(name), StringFormatter.formatHex(Formatter.formatName(name)),
                province, actor.getLocation().getBlockX(), actor.getLocation().getBlockZ());
        realm.getAsJsonArray("settlements").add(JsonUtil.GSON.toJsonTree(city.toData()));
        realm.add("settlements", sorted(realm.getAsJsonArray("settlements")));
    }
    private static void setCapital(JsonObject state, String id, int province) {
        JsonObject g = guild(state, id); g.addProperty("capital", province);
        if (g.get("incarnation").equals(state.get("incarnation"))) state.addProperty("capital", province);
    }
    private static void depart(JsonObject all, JsonObject state, int province) {
        if (province == -1 || settlementAt(state, province) == null) return;
        for (JsonElement g : state.getAsJsonArray("guilds")) if (g.getAsJsonObject().get("capital").getAsInt() == province) return;
        dissolve(all, state, province);
    }
    private static void dissolve(JsonObject all, JsonObject state, int province) {
        JsonObject city = settlementAt(state, province);
        if (city == null) return;
        state.getAsJsonArray("settlements").remove(city);
        for (JsonElement r : all.getAsJsonObject("realms").asMap().values()) {
            JsonObject other = r.getAsJsonObject();
            for (JsonElement g : other.getAsJsonArray("guilds")) {
                JsonObject ref = g.getAsJsonObject();
                if (ref.get("capital").getAsInt() == province) setCapital(other, ref.get("id").getAsString(), -1);
            }
        }
    }
    private static List<Integer> integers(JsonElement values) {
        List<Integer> result = new ArrayList<>(); values.getAsJsonArray().forEach(v -> result.add(v.getAsInt())); return result;
    }
    private static void revalidate(JsonObject all, Faction faction) {
        JsonObject state = realm(all, faction);
        Map<String,Integer> capitals = new LinkedHashMap<>();
        for (JsonElement value : state.getAsJsonArray("guilds")) {
            JsonObject g = value.getAsJsonObject(); capitals.put(g.get("id").getAsString(), g.get("capital").getAsInt());
        }
        List<Integer> owned = integers(state.get("provinces"));
        var claims = faction.getProvinceHandler().previewTopology(state.get("capital").getAsInt(), owned, capitals);
        for (var entry : claims.capitals().entrySet()) {
            int old = guild(state, entry.getKey()).get("capital").getAsInt();
            setCapital(state, entry.getKey(), entry.getValue());
            if (old != entry.getValue()) depart(all, state, old);
        }
        for (int province : owned) {
            if (claims.provinces().contains(province)) continue;
            state.getAsJsonArray("provinces").remove(new JsonPrimitive(province));
            destroyTitle(all, faction, TitleLoader.getByProvince(province));
            dissolve(all, state, province);
            JsonArray installations = state.getAsJsonArray("installations");
            for (JsonElement item : new ArrayList<>(installations.asList())) if (item.getAsJsonObject().get("province").getAsInt() == province) installations.remove(item);
            JsonElement queue = state.get("installation queue");
            if (!queue.isJsonNull() && queue.getAsJsonObject().get("province").getAsInt() == province) state.add("installation queue", JsonNull.INSTANCE);
        }
    }
    private static void destroyTitle(JsonObject all, Faction faction, Title title) {
        JsonArray own = realm(all, faction).getAsJsonArray("titles");
        if (title == null || !own.contains(new JsonPrimitive(title.getId()))) return;
        List<Integer> provinces = new ArrayList<>(); List<Title> titles = new ArrayList<>();
        collectRealm(all, faction, provinces, titles, new HashSet<>());
        long counties = own.asList().stream().map(v -> TitleLoader.getById(v.getAsString())).filter(t -> t.getTier().getTier() == 2).count();
        int members = 0;
        for (JsonElement g : realm(all, faction).getAsJsonArray("guilds")) members += FactionManager.getGuildByString(g.getAsJsonObject().get("id").getAsString()).getMembers().size();
        if (title.getCurrentAmount(faction, provinces, titles) > 0 && (title.getTier().getTier() != 2 || counties <= members)) return;
        own.remove(new JsonPrimitive(title.getId())); destroyTitle(all, faction, TitleManager.getParent(title));
    }
    private static void collectRealm(JsonObject all, Faction faction, List<Integer> provinces, List<Title> titles, Set<String> seen) {
        if (!seen.add(faction.getId())) return;
        JsonObject state = realm(all, faction); provinces.addAll(integers(state.get("provinces")));
        state.getAsJsonArray("titles").forEach(v -> titles.add(TitleLoader.getById(v.getAsString())));
        for (Faction subject : RelationManager.getSubjects(faction)) collectRealm(all, subject, provinces, titles, seen);
    }

    public static void apply(JsonObject effect) throws IOException {
        JsonObject before = effect.getAsJsonObject("before").getAsJsonObject("realms");
        JsonObject after = effect.getAsJsonObject("after").getAsJsonObject("realms");
        Map<String,Guild> guilds = new HashMap<>();
        for (Faction f : FactionManager.getCopy()) for (Guild g : f.getGuildHandler().getGuilds()) guilds.put(g.getIncarnation(), g);
        for (String id : after.keySet()) {
            Faction f = requireRealm(id, after.getAsJsonObject(id));
            JsonObject current = snapshot(f);
            for (String key : before.getAsJsonObject(id).keySet()) TreasuryActions.check(current.get(key), before.getAsJsonObject(id).get(key), after.getAsJsonObject(id).get(key));
            for (JsonElement g : after.getAsJsonObject(id).getAsJsonArray("guilds")) if (!guilds.containsKey(g.getAsJsonObject().get("incarnation").getAsString())) throw new IOException("Missing moving guild");
        }
        for (String id : after.keySet()) {
            JsonObject state = after.getAsJsonObject(id); Faction f = FactionManager.getByString(id);
            List<Guild> restored = new ArrayList<>();
            for (JsonElement value : state.getAsJsonArray("guilds")) {
                JsonObject ref = value.getAsJsonObject(); Guild g = guilds.get(ref.get("incarnation").getAsString());
                g.restoreTopology(f, ref.get("capital").getAsInt()); restored.add(g);
            }
            f.getGuildHandler().restoreTopology(restored);
            f.getProvinceHandler().restoreTopology(state.get("capital").getAsInt(), integers(state.get("provinces")));
            f.setCapitalMoves(state.get("capital moves").getAsInt()); f.getTitles().clear();
            state.getAsJsonArray("titles").forEach(v -> f.getTitles().add(TitleLoader.getById(v.getAsString())));
            f.getSettlementHandler().load(Arrays.asList(JsonUtil.GSON.fromJson(state.get("settlements"), SettlementData[].class)));
            f.getInstallationHandler().restoreSettlement(Arrays.asList(JsonUtil.GSON.fromJson(state.get("installations"), InstallationData[].class)),
                    JsonUtil.GSON.fromJson(state.get("installation queue"), InstallationConstructionData.class));
        }
        for (String id : after.keySet()) { Faction f = FactionManager.getByString(id); f.updateWealth(); f.updateTier(); save(f); FactionManager.getMap().enqueue("nation", f.getRGB()); }
        SimpleFactions.getInstance().getProvinceManager().recalculate();
    }

    private static Faction requireRealm(String id, JsonObject state) throws IOException {
        Faction f = FactionManager.getByString(id);
        if (f == null || !SettlementGuilds.capital(f).getIncarnation().equals(state.get("incarnation").getAsString())) throw new IOException("Realm incarnation changed: " + id);
        return f;
    }
    private static void save(Faction f) throws IOException { if (!new Database().saveFactionDurably(f)) throw new IOException("Cannot persist topology: " + f.getId()); }

    /** Repair cross-file publication before guilds and loan references are loaded. No posting or acknowledgment here. */
    public static void restoreFiles(JsonObject effect) throws IOException {
        JsonObject before = effect.getAsJsonObject("before").getAsJsonObject("realms");
        JsonObject after = effect.getAsJsonObject("after").getAsJsonObject("realms");
        Map<String,JsonObject> files = new LinkedHashMap<>(), guilds = new HashMap<>();
        effect.getAsJsonObject("after").getAsJsonObject("movingGuilds").entrySet().forEach(e -> guilds.put(e.getKey(), e.getValue().getAsJsonObject()));
        for (String id : after.keySet()) {
            if (!id.matches("[A-Za-z0-9_-]+")) throw new IOException("Invalid realm filename");
            JsonObject file = DurableJson.read(directory.resolve(id + ".json"), JsonObject.class); files.put(id, file);
            for (JsonElement value : file.getAsJsonArray("guilds")) {
                JsonObject g = value.getAsJsonObject(); guilds.put(g.get("incarnation").getAsString(), g);
            }
            JsonObject state = after.getAsJsonObject(id), original = before.getAsJsonObject(id);
            if (!file.get("id").getAsString().equals(id)) throw new IOException("Realm file identity changed");
            for (String key : state.keySet()) {
                if (key.equals("incarnation") || key.equals("guilds")) continue;
                JsonElement current = file.has(key) ? file.get(key) : JsonNull.INSTANCE;
                if (key.equals("settlements") || key.equals("installations")) current = sorted(current);
                TreasuryActions.check(current, original.get(key), state.get(key));
            }
            JsonObject base = null;
            JsonArray currentGuilds = new JsonArray();
            for (JsonElement value : file.getAsJsonArray("guilds")) {
                JsonObject g = value.getAsJsonObject(), ref = new JsonObject();
                for (String key : List.of("id", "incarnation", "capital")) ref.add(key, g.get(key));
                currentGuilds.add(ref);
            }
            TreasuryActions.check(sorted(currentGuilds), original.get("guilds"), state.get("guilds"));
            for (JsonElement value : file.getAsJsonArray("guilds")) if (value.getAsJsonObject().get("id").getAsString().equals(id)) base = value.getAsJsonObject();
            if (base == null || !base.get("incarnation").equals(state.get("incarnation"))) throw new IOException("Realm incarnation changed");
        }
        for (String id : after.keySet()) {
            JsonObject file = files.get(id), state = after.getAsJsonObject(id);
            for (String key : state.keySet()) if (!key.equals("incarnation") && !key.equals("guilds")) file.add(key, state.get(key).deepCopy());
            JsonArray restored = new JsonArray();
            for (JsonElement value : state.getAsJsonArray("guilds")) {
                JsonObject ref = value.getAsJsonObject(), data = guilds.get(ref.get("incarnation").getAsString());
                if (data == null || !data.get("id").equals(ref.get("id"))) throw new IOException("Missing original moving guild");
                data = data.deepCopy(); data.add("capital", ref.get("capital")); restored.add(data);
            }
            file.add("guilds", restored);
        }
        for (var entry : files.entrySet()) DurableJson.write(directory.resolve(entry.getKey() + ".json"), entry.getValue());
    }
}
