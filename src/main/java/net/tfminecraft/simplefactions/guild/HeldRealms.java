package net.tfminecraft.simplefactions.guild;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.tlibs.character.CharacterInfo;

/**
 * A realm whose last member died is held: its main guild keeps the dead leader, upkeep pauses and
 * nobody can declare war on it. A revive puts the leader back; staff can seat someone with
 * {@code /faction forcejoin} and {@code /faction forceleader}. A realm left with no members for
 * {@link #HOLD} closes.
 */
public final class HeldRealms {
    public static final Duration HOLD = Duration.ofDays(7);
    /** Realms whose closure failed part way, by id, with their main guild's incarnation; retried hourly. */
    private static final Map<String, UUID> unclosed = new ConcurrentHashMap<>();

    private HeldRealms() {}

    public static boolean isHeld(Faction faction) {
        if (faction == null || faction.getGuildHandler() == null) return false;
        Guild main = faction.getGuildHandler().getGuild(faction.getId());
        return main != null && main.isHeld();
    }

    /** Holds a realm whose last member died; a replay keeps the first time. */
    public static void hold(Faction faction, String deadKey, long at) {
        faction.getOrCreateMainGuild().markLeaderless(deadKey, at);
    }

    /** Whether estates must wait: the realm's hold has run out, or its closure is not finished. */
    public static boolean closing(UUID incarnation, long now) {
        if (incarnation == null) return false;
        if (unclosed.containsValue(incarnation)) return true;
        for (Faction faction : FactionManager.getCopy()) {
            Guild main = faction.getGuildHandler().getGuild(faction.getId());
            if (main != null && incarnation.equals(main.getIncarnationId())) return expired(faction, main, now);
        }
        return false;
    }

    /** The character came back to life: give back the realm it was holding, if nobody has taken it. */
    public static void revived(String key) {
        for (Faction faction : FactionManager.getCopy()) {
            Guild main = faction.getGuildHandler().getGuild(faction.getId());
            if (main != null && main.isHeld() && key.equals(main.getDeadLeader())) restore(faction, main);
        }
    }

    /** Hourly: gives realms back to revived leaders and closes those empty for {@link #HOLD}. */
    public static void closeExpired(long now, Path dataFolder) {
        for (var entry : new ArrayList<>(unclosed.entrySet())) {
            Faction faction = FactionManager.getByString(entry.getKey());
            Guild main = faction == null ? null : faction.getGuildHandler().getGuild(faction.getId());
            // A new realm may have taken the name and the file; it is left alone.
            if (main != null && !entry.getValue().equals(main.getIncarnationId())) unclosed.remove(entry.getKey());
            else close(faction, entry.getKey(), entry.getValue(), dataFolder);
        }
        for (Faction faction : new ArrayList<>(FactionManager.factions)) {
            Guild main = faction == null ? null : faction.getGuildHandler().getGuild(faction.getId());
            if (main == null || !main.isHeld() || restore(faction, main)) continue;
            if (main.trackEmpty(now, faction.getMembers().isEmpty())) save(faction);
            if (expired(faction, main, now)) close(faction, faction.getId(), main.getIncarnationId(), dataFolder);
        }
    }

    /** Removes a realm (null when already out of memory), its file and every other realm's relation with it. */
    private static void close(Faction faction, String id, UUID incarnation, Path dataFolder) {
        try {
            if (faction != null) FactionManager.deleteFaction(faction);
            Files.deleteIfExists(dataFolder.resolve("Data/" + id + ".json"));
            try (FileChannel directory = FileChannel.open(dataFolder.resolve("Data"), StandardOpenOption.READ)) {
                directory.force(true);
            }
            for (Faction survivor : FactionManager.getCopy()) {
                survivor.getDiplomacyHandler().removeRelation(id);
                if (!new Database().saveFactionDurably(survivor)) throw new IOException("cannot save " + survivor.getId());
            }
            unclosed.remove(id);
            Logger.getLogger("SimpleFactions").info("Closed " + id + ": its last member died and nobody took it on within "
                    + HOLD.toDays() + " days");
        } catch (IOException error) {
            unclosed.put(id, incarnation);
            Logger.getLogger("SimpleFactions").severe("Could not finish closing " + id + " (" + error.getMessage()
                    + "); it is tried again next hour");
        }
    }

    public static void resetForTests() {
        unclosed.clear();
    }

    private static boolean expired(Faction faction, Guild main, long now) {
        return main.isHeld() && faction.getMembers().isEmpty() && main.getEmptySince() > 0
                && now - main.getEmptySince() >= HOLD.toMillis();
    }

    /** True when the revived leader is back in charge; a failed save puts the hold back. */
    private static boolean restore(Faction faction, Guild main) {
        String dead = main.getDeadLeader();
        if (dead == null || !faction.getMembers().isEmpty() || FactionManager.getByMember(dead) != null
                || !Members.info(dead).map(CharacterInfo::alive).orElse(false)) return false;
        long since = main.getLeaderlessSince();
        long empty = main.getEmptySince();
        faction.addMember(dead);
        faction.setLeader(dead);
        if (save(faction)) {
            Logger.getLogger("SimpleFactions").info("Gave " + faction.getName() + " back to its revived leader " + dead);
            Player owner = Members.online(dead).orElse(null);
            if (owner != null) owner.sendMessage("§aYou lead " + faction.getName() + " §aagain.");
            return true;
        }
        faction.succeedDeadLeader(dead, "");
        main.getMembers().remove(dead);
        main.restoreLeaderless(dead, since, empty, null);
        if (!save(faction)) Logger.getLogger("SimpleFactions").severe("Could not save " + faction.getId()
                + " after undoing its return to a revived leader; its file may name them. Check it before restarting.");
        return false;
    }

    private static boolean save(Faction faction) {
        if (new Database().saveFactionDurably(faction)) return true;
        Logger.getLogger("SimpleFactions").warning("Could not save held realm " + faction.getId());
        return false;
    }
}
