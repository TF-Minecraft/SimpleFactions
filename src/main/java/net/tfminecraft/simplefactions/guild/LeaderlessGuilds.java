package net.tfminecraft.simplefactions.guild;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.tlibs.character.CharacterInfo;

/**
 * Guilds whose leader died with nobody to succeed them. The realm leader offers the seat to a
 * living member of the realm with {@code /guild appoint <guild> <name>}; it is theirs once they
 * accept with {@code /guild lead <guild>}. A guild left with no members and no leader closes
 * after {@link #CLOSE_AFTER}.
 */
public final class LeaderlessGuilds {
    public static final Duration CLOSE_AFTER = Duration.ofDays(30);
    private static final String GUILD = "guild";
    private static final String NOT_SAVED = "§cThe guild could not be saved. Nothing changed; try again shortly.";

    private LeaderlessGuilds() {}

    public static boolean handles(String command, String[] args) {
        if (!command.equalsIgnoreCase(GUILD) || args.length == 0) return false;
        if (args[0].equalsIgnoreCase("appoint")) return true;
        return args[0].equalsIgnoreCase("lead") && args.length == 2;
    }

    public static boolean handle(Player p, String[] args) {
        if (args[0].equalsIgnoreCase("lead")) {
            accept(p, args[1]);
        } else if (args.length < 3) {
            p.sendMessage("§cUse /guild appoint <guild> <character name>");
        } else {
            appoint(p, args[1], String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
        }
        return true;
    }

    /** "Name (dead)" in grey for an empty seat whose leader died, else null. */
    public static String deadLeaderLabel(Guild guild) {
        if (!guild.isLeaderless() || guild.getDeadLeader() == null) return null;
        return "§7" + Members.display(guild.getDeadLeader()) + " (dead)";
    }

    /** Leaderless guilds of the realm this character leads. */
    public static List<Guild> appointable(String key) {
        List<Guild> guilds = new ArrayList<>();
        if (key == null) return guilds;
        for (Faction faction : FactionManager.factions) {
            if (faction == null || !faction.isLeader(key)) continue;
            for (Guild guild : faction.getGuildHandler().getGuilds()) {
                if (guild != null && guild.isLeaderless()) guilds.add(guild);
            }
        }
        return guilds;
    }

    /** The leaderless guild that has offered its seat to this character, or null. */
    public static Guild offeredTo(String key) {
        if (key == null) return null;
        for (Faction faction : FactionManager.factions) {
            if (faction == null) continue;
            for (Guild guild : faction.getGuildHandler().getGuilds()) {
                if (guild != null && guild.isLeaderless() && key.equals(guild.getLeaderOffer())) return guild;
            }
        }
        return null;
    }

    static void appoint(Player p, String guildId, String name) {
        String own = Members.key(p);
        Guild guild = FactionManager.getGuildByString(guildId);
        if (guild == null) {
            p.sendMessage("§cNo guild by the id " + guildId);
            return;
        }
        Faction faction = guild.getFaction();
        if (!faction.isLeader(own)) {
            p.sendMessage("§cOnly the leader of " + faction.getName() + " §ccan appoint its guild leaders");
            return;
        }
        if (!guild.isLeaderless()) {
            p.sendMessage("§c" + guild.getName() + " §calready has a leader");
            return;
        }
        String target = Members.find(faction.getMembers(), name);
        if (target == null) {
            p.sendMessage("§c" + name + " is not a member of " + faction.getName());
            return;
        }
        if (!Members.info(target).map(CharacterInfo::alive).orElse(false)) {
            p.sendMessage("§c" + Members.display(target) + " is not alive");
            return;
        }
        if (FactionManager.getGuildByLeader(target) != null) {
            p.sendMessage("§c" + Members.display(target) + " already leads a guild");
            return;
        }
        String previousOffer = guild.getLeaderOffer();
        guild.offerLeadership(target);
        if (!save(faction)) {
            guild.offerLeadership(previousOffer);
            p.sendMessage(NOT_SAVED);
            return;
        }
        p.sendMessage("§aOffered the leadership of " + guild.getName() + " §ato " + Members.display(target)
                + ". They become leader when they accept.");
        Members.online(target).ifPresent(online -> sendOffer(online, guild));
    }

    static void accept(Player p, String guildId) {
        String own = Members.key(p);
        Guild guild = FactionManager.getGuildByString(guildId);
        if (guild == null || !guild.isLeaderless() || own == null || !own.equals(guild.getLeaderOffer())) {
            p.sendMessage("§cYou have no leadership offer from that guild");
            return;
        }
        Faction faction = guild.getFaction();
        if (Members.find(faction.getMembers(), own) == null) {
            p.sendMessage("§cYou are no longer a member of " + faction.getName());
            return;
        }
        if (FactionManager.getGuildByLeader(own) != null) {
            p.sendMessage("§cYou already lead a guild");
            return;
        }
        if (!fill(guild, own)) {
            p.sendMessage(NOT_SAVED);
            return;
        }
        p.sendMessage("§aYou now lead " + guild.getName());
        String announcement = "§a" + Members.name(p) + " now leads " + guild.getName() + "§a!";
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != p && faction.isMember(online)) online.sendMessage(announcement);
        }
    }

    /** Clears the offer when its character turns it down; false when there was none. */
    public static boolean decline(Player p, Guild guild) {
        String own = Members.key(p);
        if (!guild.isLeaderless() || own == null || !own.equals(guild.getLeaderOffer())) return false;
        guild.withdrawLeaderOffer();
        if (!save(guild.getFaction())) {
            guild.offerLeadership(own);
            p.sendMessage(NOT_SAVED);
            return true;
        }
        p.sendMessage("§aDeclined the leadership of " + guild.getName());
        Members.online(guild.getFaction().getLeader()).ifPresent(leader -> leader.sendMessage(
                "§c" + Members.name(p) + " declined the leadership of " + guild.getName()));
        return true;
    }

    /** Reminds a character of an offer waiting for them, and a realm leader of seats to fill. */
    public static void remind(Player p) {
        String own = Members.key(p);
        Guild offered = offeredTo(own);
        if (offered != null) sendOffer(p, offered);
        for (Guild guild : appointable(own)) {
            p.sendMessage("§e" + guild.getName() + " §ehas no leader since " + deadLeaderLabel(guild)
                    + "§e died. Appoint one with §f/guild appoint " + guild.getId() + " <name>");
        }
    }

    /**
     * Makes {@code member}, a living member of the guild's realm, its leader and saves the realm.
     * When the save fails nothing is kept and the result is false.
     */
    public static boolean fill(Guild guild, String member) {
        Faction faction = guild.getFaction();
        String deadLeader = guild.getDeadLeader();
        long since = guild.getLeaderlessSince();
        long empty = guild.getEmptySince();
        String offer = guild.getLeaderOffer();
        Guild previous = FactionManager.getGuildByMember(member);
        int index = previous == null ? -1 : previous.getMembers().indexOf(member);
        if (previous != null && previous != guild) previous.kick(member);
        guild.addMember(member);
        guild.setLeader(member);
        if (!save(faction)) {
            guild.kick(member);
            guild.restoreLeaderless(deadLeader, since, empty, offer);
            if (previous != null && index >= 0) previous.getMembers().add(Math.min(index, previous.getMembers().size()), member);
            return false;
        }
        net.tfminecraft.simplefactions.identity.LeaderCharacterListener.refresh(member);
        faction.updatePrestige();
        return true;
    }

    /** Hourly: removes leaderless guilds that have had no members for {@link #CLOSE_AFTER}. */
    public static void closeExpired(long now) {
        for (Faction faction : new ArrayList<>(FactionManager.factions)) {
            if (faction == null) continue;
            boolean changed = false;
            for (Guild guild : new ArrayList<>(faction.getGuildHandler().getGuilds())) {
                if (guild == null || !guild.isLeaderless()) continue;
                changed |= guild.trackEmpty(now);
                if (!guild.getMembers().isEmpty() || now - guild.getEmptySince() < CLOSE_AFTER.toMillis()) continue;
                faction.getGuildHandler().removeGuild(guild.getId());
                Logger.getLogger("SimpleFactions").info("Closed " + guild.getName() + " (" + guild.getIncarnation()
                        + "): no leader was appointed within " + CLOSE_AFTER.toDays() + " days");
                changed = true;
            }
            if (changed) save(faction);
        }
    }

    private static void sendOffer(Player p, Guild guild) {
        p.sendMessage("§a" + guild.getFaction().getName() + " §aoffers you the leadership of " + guild.getName()
                + "§a. You would leave your current guild.");
        p.sendMessage(Component.text()
                .append(button("[Accept]", NamedTextColor.GREEN, "/guild lead " + guild.getId(), "Lead this guild"))
                .append(Component.text("  "))
                .append(button("[Decline]", NamedTextColor.RED, "/guild decline " + guild.getId(), "Turn down this offer"))
                .build());
    }

    private static Component button(String label, NamedTextColor colour, String command, String hover) {
        return Component.text(label, colour, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private static boolean save(Faction faction) {
        if (new Database().saveFactionDurably(faction)) return true;
        Logger.getLogger("SimpleFactions").warning("Could not save " + faction.getId() + " after a guild leadership change");
        return false;
    }
}
