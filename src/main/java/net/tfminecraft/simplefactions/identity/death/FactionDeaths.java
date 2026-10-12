package net.tfminecraft.simplefactions.identity.death;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.persistence.WarbandMapper;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandMembershipService;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;

/** Adapts the death journal to faction stores without recomputing successors or debts on recovery. */
final class FactionDeaths {
    private final Path dataFolder;

    FactionDeaths(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    Set<String> referencedCharacters() {
        Set<String> keys = new LinkedHashSet<>();
        for (Faction faction : FactionManager.getCopy()) collectKeys(snapshot(faction), keys);
        for (var warband : WarbandManager.get()) {
            warband.getMemberCharacters().values().forEach(ref -> keys.add(Members.key(ref)));
        }
        return keys;
    }

    DeathJournal capture(CharacterRef ref, UUID deathId, long at) throws IOException {
        DeathJournal journal = new DeathJournal();
        journal.deathId = deathId;
        journal.ownerId = ref.ownerId();
        journal.characterId = ref.characterId();
        journal.at = at;
        String key = Members.key(ref);
        List<DeathJournal.Step> successions = new ArrayList<>();
        for (Faction faction : FactionManager.getCopy()) {
            JsonObject before = snapshot(faction);
            Set<String> keys = new LinkedHashSet<>();
            collectKeys(before, keys);
            if (!keys.contains(key)) continue;
            String cleanup = "rights:" + before.get("realm").getAsString();
            journal.steps.add(new DeathJournal.Step(cleanup, "rights", before));
            for (Guild guild : faction.getGuildHandler().getGuilds()) {
                UUID incarnation = UUID.fromString(guild.getIncarnation());
                double wages = guild.getCompany() == null ? 0
                        : guild.getCompany().getPendingWages().getOrDefault(key, 0.0);
                addClaim(journal, cleanup, incarnation, cents(wages), OwedPayment.Kind.WAGES);
                if (guild.getDividendEligibleMembers().contains(key)) {
                    addClaim(journal, cleanup, incarnation,
                            cents(guild.getLedger().getDividendBreakdown().perMember()), OwedPayment.Kind.DIVIDENDS);
                }
                boolean emptyRealm = faction.getMembers().stream().allMatch(key::equals);
                if (!key.equals(guild.getLeader()) && !(guild.isBase() && emptyRealm)) continue;
                JsonObject succession = new JsonObject();
                succession.addProperty("realm", before.get("realm").getAsString());
                succession.addProperty("faction", faction.getId());
                succession.addProperty("guild", incarnation.toString());
                succession.addProperty("base", guild.isBase());
                succession.addProperty("elective", guild.isBase() && faction.getGovernment().hasLeaderElections());
                succession.addProperty("emptyRealm", emptyRealm);
                String successor = guild.getMembers().stream().filter(member -> !key.equals(member))
                        .filter(member -> Members.info(member).map(CharacterInfo::alive).orElse(false))
                        .findFirst().orElse("");
                succession.addProperty("successor", successor);
                successions.add(new DeathJournal.Step("succession:" + incarnation, "succession", succession));
            }
        }
        // Every original financial claim is durably transferred before disbanding or succession.
        journal.steps.addAll(successions);
        JsonObject online = new JsonObject();
        var warband = WarbandManager.getByMemberId(ref.ownerId());
        boolean participant = warband != null && warband.getMemberCharacter(ref.ownerId()).filter(ref::equals).isPresent();
        if (!participant) {
            var pending = WarbandMembershipService.getInstance().getPendingRejoin(ref.ownerId());
            if (pending != null && pending.isFor(ref)) {
                var original = WarbandManager.getByString(pending.getWarbandId());
                if (original != null && pending.belongsTo(original)) {
                    warband = original;
                    participant = true;
                }
            }
        }
        if (participant) {
            online.addProperty("warband", warband.getId());
            online.addProperty("leader", ref.ownerId().equals(warband.getLeaderId()));
            for (UUID member : warband.getMemberIds()) {
                if (member.equals(ref.ownerId())) continue;
                var candidate = warband.getMemberCharacter(member);
                if (candidate.isEmpty() || !Members.info(Members.key(candidate.get())).map(CharacterInfo::alive).orElse(false)) continue;
                online.addProperty("successorOwner", member.toString());
                online.addProperty("successorCharacter", candidate.get().characterId().toString());
                break;
            }
        }
        journal.steps.add(new DeathJournal.Step("online", "online", online));
        return journal;
    }

    boolean apply(DeathJournal journal, DeathJournal.Step step) throws IOException {
        return switch (step.kind) {
            case "rights" -> removeRights(journal, step.plan);
            case "succession" -> succeed(journal, step.plan);
            case "online" -> removeOnline(journal, step.plan);
            default -> throw new IOException("Unknown death step: " + step.kind);
        };
    }

    private boolean removeRights(DeathJournal journal, JsonObject plan) throws IOException {
        Faction faction = realm(plan.get("realm").getAsString());
        if (faction == null) return true;
        String key = journal.characterId.toString();
        for (Guild guild : faction.getGuildHandler().getGuilds()) guild.removeDeadCharacter(key);
        faction.succeedDeadLeader(key, "");
        faction.getGovernment().removeDeadCharacter(key);
        faction.getEspionage().removeDeadCharacter(new CharacterRef(journal.ownerId, journal.characterId),
                journal.deathId, journal.at);
        var plugin = SimpleFactions.getInstance();
        if (plugin != null && plugin.getSessionManager() != null) {
            var session = plugin.getSessionManager().getSession(faction.getGovernment().getCouncil());
            if (session != null) session.removeDeadCharacter(key);
        }
        save(faction);
        return true;
    }

    private boolean succeed(DeathJournal journal, JsonObject plan) throws IOException {
        String key = journal.characterId.toString();
        Faction faction = realm(plan.get("realm").getAsString());
        if (plan.get("emptyRealm").getAsBoolean() && plan.get("base").getAsBoolean()) {
            if (faction != null && (!faction.getMembers().isEmpty()
                    || faction.getGuildHandler().getGuilds().stream().anyMatch(Guild::hasUnresolvedLeader))) return false;
            disbandRealm(faction, plan.get("faction").getAsString());
            return true;
        }
        if (faction == null) return true;
        Guild guild = faction.getGuildHandler().getGuilds().stream()
                .filter(candidate -> candidate.getIncarnation().equals(plan.get("guild").getAsString()))
                .findFirst().orElse(null);
        if (guild == null) {
            save(faction);
            return true;
        }
        if (guild.hasUnresolvedLeader()) return false;
        String leader = guild.getLeader();
        // Once staff or a later event appoints someone else, this death cannot replace them.
        if (!key.equals(leader) && leader != null && !leader.isBlank()) {
            save(faction);
            return true;
        }
        if (!guild.isBase() && guild.getMembers().isEmpty()) {
            faction.getGuildHandler().removeGuild(guild.getId());
            save(faction);
            return true;
        }
        String successor = plan.get("successor").getAsString();
        if (guild.isBase() && plan.get("elective").getAsBoolean()) {
            faction.succeedDeadLeader(key, "");
            faction.getGovernment().beginDeathElection(journal.at);
            save(faction);
            return true;
        }
        if (successor.isBlank() || !guild.isMember(successor)
                || !Members.info(successor).map(CharacterInfo::alive).orElse(false)) {
            if (guild.isBase()) faction.succeedDeadLeader(key, "");
            else guild.setLeader("");
            save(faction);
            return false;
        }
        if (guild.isBase()) faction.succeedDeadLeader(key, successor);
        else guild.setLeader(successor);
        save(faction);
        return true;
    }

    private boolean removeOnline(DeathJournal journal, JsonObject plan) throws IOException {
        var membership = WarbandMembershipService.getInstance();
        var pending = membership.getPendingRejoin(journal.ownerId);
        CharacterRef ref = new CharacterRef(journal.ownerId, journal.characterId);
        if (pending != null && pending.isFor(ref)) membership.clearPendingRejoin(journal.ownerId);
        if (!plan.has("warband")) return true;
        var warband = WarbandManager.getByString(plan.get("warband").getAsString());
        if (warband == null) return true;
        var current = warband.getMemberCharacter(journal.ownerId);
        if ((current.isPresent() && !current.get().equals(ref))
                || (current.isEmpty() && warband.hasMember(journal.ownerId))) {
            saveWarband(warband);
            return true;
        }
        if (plan.get("leader").getAsBoolean() && journal.ownerId.equals(warband.getLeaderId())) {
            UUID successor = plan.has("successorOwner") ? UUID.fromString(plan.get("successorOwner").getAsString()) : null;
            var successorRef = successor == null ? java.util.Optional.<CharacterRef>empty() : warband.getMemberCharacter(successor);
            if (successorRef.isPresent() && successorRef.get().characterId().toString().equals(plan.get("successorCharacter").getAsString())
                    && Members.info(Members.key(successorRef.get())).map(CharacterInfo::alive).orElse(false)) {
                warband.setLeaderId(successor);
            } else warband.resetToPendingLeader();
        }
        var online = org.bukkit.Bukkit.getPlayer(journal.ownerId);
        if (online != null && WarbandManager.getByMemberId(journal.ownerId) == warband) {
            membership.handleQuit(online);
            membership.clearPendingRejoin(journal.ownerId);
        }
        warband.removeMember(journal.ownerId);
        saveWarband(warband);
        return true;
    }

    private void saveWarband(net.tfminecraft.simplefactions.war.battle.warband.Warband warband) throws IOException {
        DurableJson.write(dataFolder.resolve("Warbands/warband_" + warband.getId() + ".json"), WarbandMapper.toData(warband));
    }

    private void disbandRealm(Faction faction, String id) throws IOException {
        if (faction == null && FactionManager.getByString(id) != null) {
            throw new IOException("A new realm uses the old death journal's name: " + id);
        }
        if (faction != null) {
            FactionManager.deleteFaction(faction);
        }
        Files.deleteIfExists(dataFolder.resolve("Data/" + id + ".json"));
        try (FileChannel directory = FileChannel.open(dataFolder.resolve("Data"), StandardOpenOption.READ)) {
            directory.force(true);
        }
        for (Faction survivor : FactionManager.getCopy()) {
            survivor.getDiplomacyHandler().removeRelation(id);
            save(survivor);
        }
    }

    private static Faction realm(String incarnation) {
        return FactionManager.getCopy().stream()
                .filter(faction -> faction.getOrCreateMainGuild().getIncarnation().equals(incarnation))
                .findFirst().orElse(null);
    }

    private static void save(Faction faction) throws IOException {
        if (!new Database().saveFactionDurably(faction)) throw new IOException("Cannot save death cleanup for " + faction.getId());
    }

    private static void addClaim(DeathJournal journal, String cleanup, UUID guild, long cents, OwedPayment.Kind kind) {
        if (cents <= 0) return;
        String id = "death:" + journal.deathId + ":" + guild + ":" + kind.name().toLowerCase(java.util.Locale.ROOT);
        journal.claims.add(new DeathJournal.Claim(cleanup,
                new OwedPayment(id, guild, journal.characterId, cents, kind)));
    }

    private static long cents(double amount) throws IOException {
        try {
            return BigDecimal.valueOf(amount).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (NumberFormatException | ArithmeticException error) {
            throw new IOException("Invalid owed death amount: " + amount, error);
        }
    }

    private static JsonObject snapshot(Faction faction) {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("realm", faction.getOrCreateMainGuild().getIncarnation());
        snapshot.addProperty("faction", faction.getId());
        snapshot.addProperty("leader", faction.getLeader());
        snapshot.add("government", JsonUtil.GSON.toJsonTree(faction.getGovernment().serialize()));
        snapshot.add("offices", JsonUtil.GSON.toJsonTree(faction.getEspionage()));
        JsonArray guilds = new JsonArray();
        for (Guild guild : faction.getGuildHandler().getGuilds()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("incarnation", guild.getIncarnation());
            entry.addProperty("leader", guild.getLeader());
            entry.add("members", JsonUtil.GSON.toJsonTree(guild.getMembers()));
            entry.add("invites", JsonUtil.GSON.toJsonTree(guild.getInvites()));
            entry.add("dividends", JsonUtil.GSON.toJsonTree(guild.getDividendEligibleSnapshot()));
            if (guild.getCompany() != null) entry.add("company", JsonUtil.GSON.toJsonTree(guild.getCompany().serialize()));
            guilds.add(entry);
        }
        snapshot.add("guilds", guilds);
        var plugin = SimpleFactions.getInstance();
        if (plugin != null && plugin.getSessionManager() != null) {
            var session = plugin.getSessionManager().getSession(faction.getGovernment().getCouncil());
            if (session != null) {
                snapshot.addProperty("sessionLeader", session.getLeaderCharacter());
                snapshot.add("sessionVotes", JsonUtil.GSON.toJsonTree(session.votesSnapshot()));
                snapshot.add("sessionEligible", JsonUtil.GSON.toJsonTree(session.eligibleSnapshot()));
            }
        }
        return snapshot;
    }

    private static void collectKeys(JsonElement value, Set<String> keys) {
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (Members.characterId(entry.getKey()) != null) keys.add(entry.getKey());
                collectKeys(entry.getValue(), keys);
            }
        } else if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(item -> collectKeys(item, keys));
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String key = value.getAsString();
            if (Members.characterId(key) != null) keys.add(key);
        }
    }
}
