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
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.tlibs.character.CharacterInfo;

/**
 * A realm whose last member died is held: its main guild keeps the dead leader, upkeep and taxes
 * pause, and nobody can join it or declare war on it. A revive puts the leader back; staff can seat someone with
 * {@code /faction forcejoin} and {@code /faction forceleader}. A realm left with no members for
 * {@link #HOLD} closes once it is at peace.
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

    /** The realm leader as the viewer sees it: "Name (dead)" while held. */
    public static String leaderName(Player viewer, Faction faction) {
        String dead = isHeld(faction) ? LeaderlessGuilds.deadLeaderLabel(faction.getOrCreateMainGuild()) : null;
        return dead != null ? dead : Members.display(viewer, faction.getLeader());
    }

    /** Holds a realm whose last member died; a replay keeps the first time. */
    public static void hold(Faction faction, String deadKey, long at) {
        faction.getOrCreateMainGuild().markLeaderless(deadKey, at);
        faction.getInvited().clear();
    }

    /**
     * Whether estates must wait: the realm's week has run out (it may still be at war), or its closure is
     * not finished.
     */
    public static boolean closing(UUID incarnation, long now) {
        if (incarnation == null) return false;
        if (unclosed.containsValue(incarnation)) return true;
        for (Faction faction : FactionManager.getCopy()) {
            Guild main = faction.getGuildHandler().getGuild(faction.getId());
            if (main != null && incarnation.equals(main.getIncarnationId())) return weekOver(faction, main, now);
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

    /**
     * Hourly: gives realms back to revived leaders and closes those empty for {@link #HOLD} and at
     * peace. A realm whose revived leader could not be saved back in is kept for the next hour.
     */
    public static void closeExpired(long now, Path dataFolder) {
        for (var entry : new ArrayList<>(unclosed.entrySet())) {
            // A realm still loaded is decided below; a new realm that took the name is left alone.
            if (FactionManager.getByString(entry.getKey()) == null) close(null, entry.getKey(), entry.getValue(), now, dataFolder);
            else unclosed.remove(entry.getKey());
        }
        for (Faction faction : new ArrayList<>(FactionManager.factions)) {
            Guild main = faction == null ? null : faction.getGuildHandler().getGuild(faction.getId());
            if (main == null || !main.isHeld() || restore(faction, main) != Restore.NOT_ELIGIBLE) continue;
            if (main.trackEmpty(now, faction.getMembers().isEmpty())) save(faction);
            if (weekOver(faction, main, now) && !WarManager.isAtWar(faction)) {
                close(faction, faction.getId(), main.getIncarnationId(), now, dataFolder);
            }
        }
    }

    /**
     * Saves the realm as closing, so a revive can no longer take back a realm whose relations are
     * partly gone; removes every other realm's relation, trade agreement and treaty with it, so a
     * partial closure never leaves them for a new realm of the same name; then the realm and its file.
     * {@code faction} is null when it already left memory.
     */
    private static void close(Faction faction, String id, UUID incarnation, long now, Path dataFolder) {
        try {
            Guild main = faction == null ? null : faction.getOrCreateMainGuild();
            if (main != null && main.getClosingSince() == 0) {
                main.markClosing(now);
                if (!save(faction)) {
                    main.markClosing(0);
                    throw new IOException("cannot save " + id + " as closing");
                }
            }
            for (Faction survivor : FactionManager.getCopy()) {
                if (survivor == faction) continue;
                survivor.getDiplomacyHandler().removeRelation(id);
                survivor.getDiplomacyHandler().removeTradeRelation(id);
                survivor.getDiplomacyHandler().removeTreatyRelation(id);
                if (!new Database().saveFactionDurably(survivor)) throw new IOException("cannot save " + survivor.getId());
            }
            if (faction != null) FactionManager.deleteFaction(faction);
            Files.deleteIfExists(dataFolder.resolve("Data/" + id + ".json"));
            try (FileChannel directory = FileChannel.open(dataFolder.resolve("Data"), StandardOpenOption.READ)) {
                directory.force(true);
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

    private static boolean weekOver(Faction faction, Guild main, long now) {
        return main.isHeld() && faction.getMembers().isEmpty() && main.getEmptySince() > 0
                && now - main.getEmptySince() >= HOLD.toMillis();
    }

    private enum Restore { NOT_ELIGIBLE, RESTORED, NOT_SAVED }

    /** Puts the revived leader back in charge; a failed save puts the hold back for the next try. */
    private static Restore restore(Faction faction, Guild main) {
        String dead = main.getDeadLeader();
        if (dead == null || main.getClosingSince() > 0 || !faction.getMembers().isEmpty() || FactionManager.getByMember(dead) != null
                || !Members.info(dead).map(CharacterInfo::alive).orElse(false)) return Restore.NOT_ELIGIBLE;
        long since = main.getLeaderlessSince();
        long empty = main.getEmptySince();
        faction.addMember(dead);
        faction.setLeader(dead);
        if (save(faction)) {
            Logger.getLogger("SimpleFactions").info("Gave " + faction.getName() + " back to its revived leader " + dead);
            Player owner = Members.online(dead).orElse(null);
            if (owner != null) owner.sendMessage("§aYou lead " + faction.getName() + " §aagain.");
            return Restore.RESTORED;
        }
        faction.succeedDeadLeader(dead, "");
        main.getMembers().remove(dead);
        main.restoreLeaderless(dead, since, empty, null);
        if (!save(faction)) Logger.getLogger("SimpleFactions").severe("Could not save " + faction.getId()
                + " after undoing its return to a revived leader; its file may name them. Check it before restarting.");
        return Restore.NOT_SAVED;
    }

    private static boolean save(Faction faction) {
        if (new Database().saveFactionDurably(faction)) return true;
        Logger.getLogger("SimpleFactions").warning("Could not save held realm " + faction.getId());
        return false;
    }
}
