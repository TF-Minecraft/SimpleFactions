package net.tfminecraft.simplefactions.plots;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.espionage.EspionageConfig;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/** Plots from request to outcome. Every rule works on characters; accounts only carry messages. */
public final class PlotService {
    static final String PREFIX = "§8[§5Plot§8] §7";
    static final int MAX_MEMBERS = 4, MAX_ASSASSINS = 2, MAX_COLLABORATORS = 2;
    private static final int MAX_NOTICES = 30;
    private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static LongSupplier clock = System::currentTimeMillis;
    static Supplier<RandomGenerator> random = ThreadLocalRandom::current;
    private static PlotStore store = new PlotStore();
    private static final Map<UUID, Hit> hits = new HashMap<>();

    private record Hit(UUID attacker, long at) {}
    record Protection(double value, Betrayal betrayal) {}
    record Guard(String characterId, int defense) {}

    private PlotService() {}

    public static void load(Path file) throws IOException {
        store = PlotStore.load(file);
        hits.clear();
    }

    public static PlotStore store() { return store; }

    static long now() { return clock.getAsLong(); }

    static void save() {
        try {
            store.save();
        } catch (IOException error) {
            SimpleFactions.plugin.getLogger().log(Level.SEVERE, "Could not save plots", error);
        }
    }

    // ---- Intrigue -------------------------------------------------------------------------------

    /** Intrigue rolls on first use while the character is played; until then it counts as the formula's base. */
    public static int intrigue(PlotCharacter character) {
        Integer known = EspionageService.knownIntrigue(character.characterId);
        if (known != null) return known;
        Player player = PlotCharacters.online(character);
        if (player != null) {
            try {
                return EspionageService.intrigue(player, character.characterId);
            } catch (IOException | RuntimeException error) {
                SimpleFactions.plugin.getLogger().log(Level.WARNING, "Could not roll Intrigue for " + character.name, error);
            }
        }
        return (int) Math.round(EspionageConfig.base());
    }

    public static int offense(PlotCharacter character) {
        return PlotMath.offense(intrigue(character), store.peek(character.characterId).approach());
    }

    public static int defense(PlotCharacter character) {
        return PlotMath.defense(intrigue(character), store.peek(character.characterId).approach());
    }

    /** The target's faction Spymaster and their defence, or null when the office is vacant. */
    static Guard spymaster(PlotCharacter character) {
        var faction = FactionManager.getByMember(character.playerName);
        if (faction == null || !EspionageService.hasSpymaster(faction)) return null;
        var holder = faction.getEspionage().getSpymaster();
        if (holder.characterId == null) return null;
        return new Guard(holder.characterId, PlotMath.defense(EspionageService.effectiveAptitude(faction, holder),
                store.peek(holder.characterId).approach()));
    }

    static Protection protection(Plot plot) { return protection(plot.target, plot); }

    /** The target's protection; without a plot nobody can betray them. */
    static Protection protection(PlotCharacter target, Plot plot) {
        IntrigueSettings settings = store.peek(target.characterId);
        int own = defense(target);
        switch (settings.defender()) {
            case CHARACTER -> {
                PlotCharacter helper = settings.helper;
                if (!PlotCharacters.dead(helper)) return plot != null && plot.involves(helper.characterId)
                        ? new Protection(PlotMath.betrayedByHelper(own, offense(helper)), Betrayal.HELPER)
                        : new Protection(PlotMath.withHelper(own, defense(helper)), Betrayal.NONE);
            }
            case SPYMASTER -> {
                Guard guard = spymaster(target);
                if (guard != null) return plot != null && plot.involves(guard.characterId())
                        ? new Protection(0, Betrayal.SPYMASTER) : new Protection(guard.defense(), Betrayal.NONE);
            }
            default -> { }
        }
        return new Protection(PlotMath.alone(own), Betrayal.NONE);
    }

    static PlotMath.Snapshot snapshot(Plot plot) {
        List<PlotMath.Contributor> contributors = new ArrayList<>();
        contributors.add(new PlotMath.Contributor(PlotRole.MASTER, null, offense(plot.master)));
        for (PlotMember member : plot.members)
            contributors.add(new PlotMath.Contributor(member.role, member.task, offense(member.character)));
        Protection protection = protection(plot);
        return new PlotMath.Snapshot(contributors, protection.value(), protection.betrayal());
    }

    // ---- Messages -------------------------------------------------------------------------------

    /** Reaches the character now if it is played, otherwise the next time it is. */
    static void tell(PlotCharacter character, String message) {
        Player player = PlotCharacters.online(character);
        if (player != null) {
            player.sendMessage(PREFIX + message);
            return;
        }
        var queue = store.notices.computeIfAbsent(character.characterId, id -> new ArrayList<>());
        queue.add(PREFIX + message);
        while (queue.size() > MAX_NOTICES) queue.removeFirst();
    }

    static void tellConspirators(Plot plot, String message) {
        tell(plot.master, message);
        for (PlotMember member : plot.members) tell(member.character, message);
    }

    public static void deliver(Player player) {
        PlotCharacter character = PlotCharacters.active(player);
        if (character == null) return;
        intrigue(character);
        var queue = store.notices.remove(character.characterId);
        if (queue == null) return;
        queue.forEach(player::sendMessage);
        save();
    }

    // ---- Settings -------------------------------------------------------------------------------

    static PlotCharacter character(Player player) {
        PlotCharacter character = PlotCharacters.active(player);
        if (character == null) player.sendMessage("§cYou need a living active character.");
        return character;
    }

    public static boolean setApproach(Player player, PlotApproach approach) {
        PlotCharacter me = character(player);
        if (me == null) return false;
        IntrigueSettings settings = store.settings(me.characterId);
        if (settings.approach() == approach) {
            player.sendMessage("§7Your approach is already " + approach.label() + ".");
            return false;
        }
        long wait = settings.approachChangedAt + PlotConfig.approachCooldown() - now();
        if (settings.approachChangedAt > 0 && wait > 0) {
            player.sendMessage("§cYou can change your approach again in " + EspionageService.duration(wait) + ".");
            return false;
        }
        settings.approach = approach;
        settings.approachChangedAt = now();
        save();
        player.sendMessage(PREFIX + "Your approach is now §e" + approach.label() + "§7: +"
                + PlotConfig.offenseBonus(approach) + " Intrigue when plotting, +" + PlotConfig.defenseBonus(approach) + " when defending.");
        return true;
    }

    public static boolean setDefender(Player player, DefenderChoice choice, Player other) {
        PlotCharacter me = character(player);
        if (me == null) return false;
        IntrigueSettings settings = store.settings(me.characterId);
        long wait = settings.defenderChangedAt + PlotConfig.defenderCooldown() - now();
        if (settings.defenderChangedAt > 0 && wait > 0) {
            player.sendMessage("§cYou can choose your defender again in " + EspionageService.duration(wait) + ".");
            return false;
        }
        PlotCharacter helper = null;
        if (choice == DefenderChoice.CHARACTER) {
            helper = other(player, other, "trust them with your safety");
            if (helper == null) return false;
            intrigue(helper);
        } else if (choice == DefenderChoice.SPYMASTER && spymaster(me) == null) {
            player.sendMessage("§cYour faction has no Spymaster to guard you.");
            return false;
        }
        settings.defender = choice;
        settings.helper = helper;
        settings.defenderChangedAt = now();
        save();
        player.sendMessage(PREFIX + "You are now guarded against plots by: §e"
                + (helper == null ? choice.label() : helper.name) + "§7.");
        if (helper != null) other.sendMessage(PREFIX + "§e" + me.name + "§7 trusts you to watch their back against plots.");
        return true;
    }

    /** Another player's living character, met in person. */
    private static PlotCharacter other(Player player, Player other, String purpose) {
        PlotCharacter character = PlotCharacters.active(other);
        if (other == null || character == null || other.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cChoose another player's living active character.");
            return null;
        }
        if (!near(player, other, PlotConfig.inviteRadius())) {
            player.sendMessage("§cDo this in person: stand within " + (int) PlotConfig.inviteRadius() + " blocks of them to " + purpose + ".");
            return null;
        }
        return character;
    }

    static boolean near(Player first, Player second, double radius) {
        return first.getWorld().equals(second.getWorld()) && first.getLocation().distanceSquared(second.getLocation()) <= radius * radius;
    }

    /** True when the character knows of at least one plot against them. */
    public static boolean threatened(String characterId) {
        return store.against(characterId).stream().anyMatch(plot -> plot.discovered);
    }

    public static boolean toggleInvestigator(Player player, Player other) {
        PlotCharacter me = character(player);
        if (me == null) return false;
        if (!threatened(me.characterId)) {
            player.sendMessage("§cYou know of no plot against your life.");
            return false;
        }
        IntrigueSettings settings = store.settings(me.characterId);
        PlotCharacter known = other == null ? null : PlotCharacters.active(other);
        if (known != null && settings.investigators.removeIf(character -> character.is(known.characterId))) {
            save();
            player.sendMessage(PREFIX + "§e" + known.name + "§7 no longer investigates for you.");
            return true;
        }
        if (settings.investigators.size() >= PlotConfig.maxInvestigators()) {
            player.sendMessage("§cYou can have at most " + PlotConfig.maxInvestigators() + " investigators.");
            return false;
        }
        PlotCharacter investigator = other(player, other, "ask for their help");
        if (investigator == null) return false;
        settings.investigators.add(investigator);
        save();
        player.sendMessage(PREFIX + "§e" + investigator.name + "§7 now investigates the plot against you.");
        other.sendMessage(PREFIX + "§e" + me.name + "§7 has asked you to look into a plot against their life. "
                + "Stay close to the people around them.");
        return true;
    }

    // ---- Plot lifecycle -------------------------------------------------------------------------

    public static Plot request(Player player) {
        PlotCharacter me = character(player);
        if (me == null) return null;
        if (store.plotOf(me.characterId) != null) {
            player.sendMessage("§cYour character is already part of a plot.");
            return null;
        }
        intrigue(me);
        Plot plot = new Plot();
        plot.code = newCode();
        plot.master = me;
        plot.requestedAt = plot.phaseStartedAt = now();
        store.plots.add(plot);
        save();
        player.sendMessage(PREFIX + "You begin to plan an assassination. Open a ticket naming your target and give staff this code: §e"
                + plot.code + "§7. It expires in " + EspionageService.duration(PlotConfig.requestExpiry()) + ".");
        return plot;
    }

    private static String newCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder("P-");
            for (int index = 0; index < 5; index++)
                builder.append(CODE_LETTERS.charAt(random.get().nextInt(CODE_LETTERS.length())));
            code = builder.toString();
        } while (store.plot(code) != null);
        return code;
    }

    /** Staff link the requested target. The target may be offline. */
    public static boolean approve(CommandSender staff, String code, String targetName) {
        Plot plot = store.plot(code);
        if (plot == null || plot.phase != PlotPhase.REQUESTED) {
            staff.sendMessage("§cNo plot request with that code is awaiting approval.");
            return false;
        }
        PlotCharacter target = resolve(targetName);
        if (target == null) {
            staff.sendMessage("§cThat player has no living active character.");
            return false;
        }
        if (target.playerId.equals(plot.master.playerId)) {
            staff.sendMessage("§cA player cannot plot against their own characters.");
            return false;
        }
        Long until = store.cooldowns.get(cooldownKey(plot.master, target));
        if (until != null && until > now()) {
            staff.sendMessage("§c" + plot.master.name + " cannot target " + target.name + " again for "
                    + EspionageService.duration(until - now()) + ".");
            return false;
        }
        plot.target = target;
        plot.phase = PlotPhase.RECRUITING;
        plot.phaseStartedAt = now();
        save();
        staff.sendMessage("§aPlot " + plot.code + " approved: " + plot.master.name + " (" + plot.master.playerName
                + ") against " + target.name + " (" + target.playerName + ").");
        tell(plot.master, "Your plot against §e" + target.name + "§7 has been approved. Meet other characters and propose "
                + "that they join: stand near them and use the Plots menu or §e/plot invite <name>§7. Start the plot within "
                + EspionageService.duration(PlotConfig.recruiting()) + " or it dissolves.");
        return true;
    }

    static PlotCharacter resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return PlotCharacters.active(online);
        return PlotCharacters.offline(Bukkit.getOfflinePlayerIfCached(name));
    }

    private static String cooldownKey(PlotCharacter master, PlotCharacter target) {
        return master.characterId + "|" + target.characterId;
    }

    public static boolean deny(CommandSender staff, String code) {
        Plot plot = store.plot(code);
        if (plot == null || plot.phase != PlotPhase.REQUESTED) {
            staff.sendMessage("§cNo plot request with that code is awaiting approval.");
            return false;
        }
        end(plot, PlotPhase.DISSOLVED, "Denied by staff", false);
        staff.sendMessage("§aPlot request " + plot.code + " denied.");
        tell(plot.master, "Your plot request §e" + plot.code + "§7 was not approved.");
        save();
        return true;
    }

    public static boolean cancel(CommandSender staff, String code) {
        Plot plot = store.plot(code);
        if (plot == null || !plot.phase.active()) {
            staff.sendMessage("§cNo running plot has that code.");
            return false;
        }
        end(plot, PlotPhase.DISSOLVED, "Ended by staff", false);
        tellConspirators(plot, "The plot" + against(plot) + " has come to nothing.");
        save();
        staff.sendMessage("§aPlot " + plot.code + " ended.");
        return true;
    }

    private static String against(Plot plot) {
        return plot.target == null ? "" : " against §e" + plot.target.name + "§7";
    }

    static void end(Plot plot, PlotPhase phase, String outcome, boolean cooldown) {
        plot.phase = phase;
        plot.outcome = outcome;
        plot.invitations.clear();
        if (cooldown && plot.target != null)
            store.cooldowns.put(cooldownKey(plot.master, plot.target), now() + PlotConfig.retargetCooldown());
        save();
    }

    /** The plot the player's character leads, or null with a message. */
    static Plot mastered(Player player) {
        PlotCharacter me = character(player);
        Plot plot = me == null ? null : store.plotOf(me.characterId);
        if (me != null && (plot == null || !plot.master.is(me.characterId))) {
            player.sendMessage("§cYou are not leading a plot.");
            return null;
        }
        return plot;
    }

    private static Plot organising(Player player) {
        Plot plot = mastered(player);
        if (plot != null && !plot.phase.organising()) {
            player.sendMessage("§cYou can only change the conspirators while the plot is being organised.");
            return null;
        }
        return plot;
    }

    /** The target's own player must never learn of the plot through another of their characters. */
    private static boolean targetsAccount(Plot plot, PlotCharacter character) {
        return plot.isTarget(character.characterId) || character.playerId.equals(plot.target.playerId);
    }

    public static boolean invite(Player player, Player other) {
        Plot plot = organising(player);
        if (plot == null) return false;
        PlotCharacter guest = other(player, other, "propose it");
        if (guest == null) return false;
        if (targetsAccount(plot, guest) || plot.involves(guest.characterId)) {
            player.sendMessage("§cYou cannot invite them.");
            return false;
        }
        plot.invitations.values().removeIf(expiry -> expiry < now());
        if (plot.members.size() + plot.invitations.size() >= MAX_MEMBERS && !plot.invitations.containsKey(guest.characterId)) {
            player.sendMessage("§cYour plot has no room for more conspirators.");
            return false;
        }
        plot.invitations.put(guest.characterId, now() + PlotConfig.inviteDuration());
        save();
        player.sendMessage(PREFIX + "You propose to §e" + guest.name + "§7 that they join your plot.");
        other.sendMessage(LegacyComponentSerializer.legacySection().deserialize(PREFIX + "§e" + PlotCharacters.seenAs(player, plot.master)
                        + "§7 proposes that you join a plot against the life of §e" + plot.target.name + "§7. ")
                .append(Component.text("[Accept]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/plot accept " + plot.code)))
                .append(Component.text(" "))
                .append(Component.text("[Decline]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/plot decline " + plot.code))));
        return true;
    }

    public static boolean accept(Player player, String code) {
        PlotCharacter me = character(player);
        Plot plot = me == null ? null : store.plot(code);
        Long expiry = plot == null ? null : plot.invitations.get(me.characterId);
        if (me == null) return false;
        if (expiry == null || expiry < now() || !plot.phase.organising()) {
            player.sendMessage("§cThat proposal is no longer open.");
            return false;
        }
        plot.invitations.remove(me.characterId);
        if (store.plotOf(me.characterId) != null || plot.members.size() >= MAX_MEMBERS) {
            save();
            player.sendMessage("§cYou cannot join this plot: you are already part of one, or it has no room left.");
            return false;
        }
        intrigue(me);
        plot.members.add(new PlotMember(me));
        save();
        player.sendMessage(PREFIX + "You have joined the plot against §e" + plot.target.name + "§7. §e"
                + plot.master.name + "§7 will give you your role.");
        tell(plot.master, "§e" + me.name + "§7 has joined your plot. Give them a role in the Plots menu.");
        save();
        return true;
    }

    public static boolean decline(Player player, String code) {
        PlotCharacter me = character(player);
        Plot plot = me == null ? null : store.plot(code);
        if (me == null) return false;
        if (plot == null || plot.invitations.remove(me.characterId) == null) {
            player.sendMessage("§cThat proposal is no longer open.");
            return false;
        }
        save();
        player.sendMessage(PREFIX + "You turn the proposal down.");
        tell(plot.master, "§e" + me.name + "§7 has turned down your proposal.");
        save();
        return true;
    }

    public static boolean assign(Player player, String characterId, PlotRole role, CollaboratorTask task) {
        Plot plot = organising(player);
        if (plot == null) return false;
        PlotMember member = plot.member(characterId);
        if (member == null) {
            player.sendMessage("§cThat character is not in your plot.");
            return false;
        }
        if (role == PlotRole.ASSASSIN && member.role != role && plot.withRole(role).size() >= MAX_ASSASSINS
                || role == PlotRole.COLLABORATOR && member.role != role && plot.withRole(role).size() >= MAX_COLLABORATORS) {
            player.sendMessage("§cA plot has room for " + MAX_ASSASSINS + " assassins and " + MAX_COLLABORATORS + " collaborators.");
            return false;
        }
        member.role = role;
        member.task = role == PlotRole.COLLABORATOR ? task == null ? CollaboratorTask.HIDE : task : null;
        save();
        player.sendMessage(PREFIX + "§e" + member.character.name + "§7 is now: " + member.roleLabel() + ".");
        tell(member.character, "§e" + plot.master.name + "§7 has made you: §e" + member.roleLabel() + "§7.");
        save();
        return true;
    }

    public static boolean dismiss(Player player, String characterId) {
        Plot plot = organising(player);
        if (plot == null) return false;
        PlotMember member = plot.member(characterId);
        if (member == null) {
            player.sendMessage("§cThat character is not in your plot.");
            return false;
        }
        plot.members.remove(member);
        save();
        player.sendMessage(PREFIX + "§e" + member.character.name + "§7 is no longer part of your plot.");
        tell(member.character, "You are no longer part of the plot" + against(plot) + ".");
        save();
        return true;
    }

    /** A conspirator marks a character who learned of the plot in roleplay. */
    public static boolean inform(Player player, Player other) {
        PlotCharacter me = character(player);
        Plot plot = me == null ? null : store.plotOf(me.characterId);
        if (me == null) return false;
        if (plot == null || plot.target == null) {
            player.sendMessage("§cYou are not part of a running plot.");
            return false;
        }
        PlotCharacter guest = other(player, other, "tell them");
        if (guest == null) return false;
        if (targetsAccount(plot, guest) || plot.role(guest.characterId) != null) {
            player.sendMessage("§cThey cannot be marked as knowing of the plot.");
            return false;
        }
        plot.informed.add(guest);
        save();
        player.sendMessage(PREFIX + "§e" + guest.name + "§7 now knows of the plot. Near the target, they may give it away.");
        other.sendMessage(PREFIX + "You now know of a plot against §e" + plot.target.name
                + "§7. Near them, your knowledge may show.");
        return true;
    }

    public static boolean begin(Player player) {
        Plot plot = mastered(player);
        if (plot == null) return false;
        if (plot.phase != PlotPhase.RECRUITING) {
            player.sendMessage("§cThe plot is not recruiting.");
            return false;
        }
        if (plot.withRole(PlotRole.ASSASSIN).isEmpty()) {
            player.sendMessage("§cRecruit at least one conspirator and make them an assassin first.");
            return false;
        }
        plot.phase = PlotPhase.BUILDING;
        plot.phaseStartedAt = now();
        plot.nextDayAt = now() + PlotConfig.DAY;
        var snapshot = snapshot(plot);
        plot.required = PlotMath.required(snapshot.protection(), snapshot.betrayal());
        save();
        tellConspirators(plot, "The plot against §e" + plot.target.name + "§7 is under way. It needs about "
                + EspionageService.duration(Math.round(PlotConfig.buildDays() / PlotMath.speed(snapshot) * PlotConfig.DAY))
                + " and enough strength.");
        save();
        return true;
    }

    public static boolean launch(Player player) {
        Plot plot = mastered(player);
        if (plot == null) return false;
        if (plot.phase != PlotPhase.READY) {
            player.sendMessage("§cThe plot is not ready to strike.");
            return false;
        }
        if (plot.withRole(PlotRole.ASSASSIN).isEmpty()) {
            player.sendMessage("§cThe plot has no assassin left.");
            return false;
        }
        plot.phase = PlotPhase.INVESTIGATION;
        plot.phaseStartedAt = now();
        save();
        tellConspirators(plot, "Final preparations begin. In " + EspionageService.duration(PlotConfig.investigation())
                + " the assassins may strike. Until then, §e" + plot.target.name + "§7 may notice something about anyone near them.");
        if (plot.discovered) tell(plot.target, "§cYou feel the danger closing in. Watch the people around you.");
        save();
        return true;
    }

    public static boolean leave(Player player) {
        PlotCharacter me = character(player);
        Plot plot = me == null ? null : store.plotOf(me.characterId);
        if (me == null) return false;
        if (plot == null || plot.master.is(me.characterId)) {
            player.sendMessage("§cYou are not a member of a plot. A plot master abandons it instead.");
            return false;
        }
        if (!plot.phase.organising() && plot.phase != PlotPhase.READY) {
            player.sendMessage("§cIt is too late to back out now.");
            return false;
        }
        plot.members.remove(plot.member(me.characterId));
        save();
        player.sendMessage(PREFIX + "You withdraw from the plot.");
        tell(plot.master, "§e" + me.name + "§7 has withdrawn from your plot.");
        save();
        return true;
    }

    public static boolean abandon(Player player) {
        Plot plot = mastered(player);
        if (plot == null) return false;
        end(plot, PlotPhase.DISSOLVED, "Abandoned by the plot master", true);
        player.sendMessage(PREFIX + "You abandon the plot.");
        for (PlotMember member : plot.members)
            tell(member.character, "§e" + plot.master.name + "§7 has abandoned the plot" + against(plot) + ".");
        save();
        return true;
    }

    // ---- Time -----------------------------------------------------------------------------------

    /** Advances every plot's timers; days missed while the server was down are caught up. */
    public static void tick() {
        long now = now();
        boolean changed = false;
        hits.values().removeIf(hit -> now - hit.at() > PlotConfig.hitWindow());
        for (Plot plot : List.copyOf(store.plots)) {
            if (!plot.phase.active()) continue;
            changed |= plot.invitations.values().removeIf(expiry -> expiry < now);
            switch (plot.phase) {
                case REQUESTED -> {
                    if (now >= plot.requestedAt + PlotConfig.requestExpiry()) {
                        end(plot, PlotPhase.DISSOLVED, "The request expired", false);
                        tell(plot.master, "Your plot request §e" + plot.code + "§7 expired without approval.");
                        changed = true;
                    }
                }
                case RECRUITING -> {
                    if (now >= plot.phaseStartedAt + PlotConfig.recruiting()) {
                        end(plot, PlotPhase.DISSOLVED, "Never got under way", true);
                        tellConspirators(plot, "The plot" + against(plot) + " never got under way and has dissolved.");
                        changed = true;
                    }
                }
                case BUILDING -> {
                    while (plot.phase == PlotPhase.BUILDING && now >= plot.nextDayAt) {
                        plot.nextDayAt += PlotConfig.DAY;
                        buildDay(plot);
                        changed = true;
                    }
                }
                case INVESTIGATION -> {
                    if (now >= plot.phaseStartedAt + PlotConfig.investigation()) {
                        plot.phase = PlotPhase.EXECUTION;
                        plot.phaseStartedAt += PlotConfig.investigation();
                        String message = "§cThe time has come. The assassins may now strike §e" + plot.target.name
                                + "§c. They have " + EspionageService.duration(PlotConfig.execution()) + ".";
                        tell(plot.master, message);
                        for (PlotMember assassin : plot.withRole(PlotRole.ASSASSIN)) tell(assassin.character, message);
                        changed = true;
                    }
                }
                case EXECUTION -> {
                    if (now >= plot.phaseStartedAt + PlotConfig.execution()) {
                        end(plot, PlotPhase.FAILED, "The assassins never struck", true);
                        tellConspirators(plot, "The moment has passed. The plot" + against(plot) + " has failed.");
                        if (plot.discovered) tell(plot.target, "The danger to your life seems to have passed.");
                        changed = true;
                    }
                }
                default -> { }
            }
        }
        if (changed) save();
    }

    /** Staff testing: one day passes for this plot only. */
    public static boolean passDay(CommandSender staff, String code) {
        Plot plot = store.plot(code);
        if (plot == null || !plot.phase.active()) {
            staff.sendMessage("§cNo running plot has that code.");
            return false;
        }
        plot.requestedAt -= PlotConfig.DAY;
        plot.phaseStartedAt -= PlotConfig.DAY;
        if (plot.phase == PlotPhase.BUILDING) plot.nextDayAt = Math.min(plot.nextDayAt, now());
        plot.invitations.replaceAll((id, expiry) -> expiry - PlotConfig.DAY);
        tick();
        staff.sendMessage("§aA day passes for plot " + plot.code + ". " + summary(plot));
        return true;
    }

    static void buildDay(Plot plot) {
        var snapshot = snapshot(plot);
        boolean leak = PlotMath.day(plot, snapshot, random.get());
        boolean exposing = plot.secrecy <= 0 && !plot.exposed && snapshot.betrayal() != Betrayal.SPYMASTER;
        if (leak || exposing) {
            plot.discovered = true;
            plot.exposed |= exposing;
            List<String> lines = PlotMath.reveal(plot, conspiratorNames(plot));
            if (lines.isEmpty()) lines = List.of(plot.knowledge.get("threat"));
            for (String line : lines) tell(plot.target, "§c" + line);
            if (leak) tell(plot.master, "Whispers reach you: §e" + plot.target.name + "§7 suspects that someone wants them dead.");
            if (exposing) tellConspirators(plot, "§cThe plot is exposed. §e" + plot.target.name + "§c knows who is behind it.");
        }
        if (plot.progress < 100) return;
        if (plot.strength >= plot.required) {
            plot.phase = PlotPhase.READY;
            plot.phaseStartedAt = plot.nextDayAt - PlotConfig.DAY;
            tell(plot.master, "The plot" + against(plot) + " is ready. Begin the final phase from the Plots menu or with §e/plot launch§7.");
            for (PlotMember member : plot.members) tell(member.character, "The plot" + against(plot) + " is ready to strike.");
        } else {
            end(plot, PlotPhase.FAILED, "Not enough strength", true);
            tellConspirators(plot, "The plot" + against(plot) + " ran out of time without enough strength and has failed.");
        }
    }

    static List<String> conspiratorNames(Plot plot) {
        List<String> names = new ArrayList<>();
        names.add(plot.master.name + " (plot master)");
        for (PlotMember member : plot.members) names.add(member.character.name);
        return names;
    }

    public static String summary(Plot plot) {
        return plot.phase.label() + ", day " + plot.day + ", progress " + Math.round(plot.progress) + "%, strength "
                + Math.round(plot.strength) + "/" + Math.round(plot.required) + ", secrecy " + Math.round(plot.secrecy) + "%";
    }

    // ---- Final phase ----------------------------------------------------------------------------

    /** Each investigator rolls once against each conspirator they come near during the final preparations. */
    public static void scan() {
        boolean changed = false;
        for (Plot plot : store.plots) {
            if (plot.phase != PlotPhase.INVESTIGATION) continue;
            Map<Player, PlotCharacter> investigators = new java.util.LinkedHashMap<>();
            Player target = PlotCharacters.online(plot.target);
            if (target != null) investigators.put(target, plot.target);
            if (plot.discovered) for (PlotCharacter character : store.peek(plot.target.characterId).investigators) {
                Player player = PlotCharacters.online(character);
                if (player != null) investigators.put(player, character);
            }
            if (investigators.isEmpty()) continue;
            int modifier = PlotMath.investigationModifier(plot.discovered, protection(plot).betrayal());
            for (PlotCharacter suspect : plot.suspects()) {
                Player suspected = plot.investigated.contains(suspect.characterId) ? null : PlotCharacters.online(suspect);
                if (suspected == null) continue;
                for (var investigator : investigators.entrySet()) {
                    if (investigator.getKey().equals(suspected) || !near(investigator.getKey(), suspected, PlotConfig.radius())) continue;
                    plot.investigated.add(suspect.characterId);
                    changed = true;
                    PlotRole role = plot.role(suspect.characterId);
                    int margin = PlotMath.investigate(defense(investigator.getValue()), modifier, offense(suspect), role, random.get());
                    if (margin >= 0) investigator.getKey().sendMessage(StringFormatter.formatHex(org.bukkit.ChatColor
                            .translateAlternateColorCodes('&', PlotConfig.hint(role, margin >= PlotConfig.strongMargin(),
                                    PlotCharacters.seenAs(suspected, suspect)))));
                    break;
                }
            }
        }
        if (changed) save();
    }

    private static PlotMember assassin(Plot plot, Player player) {
        PlotCharacter character = PlotCharacters.active(player);
        PlotMember member = character == null ? null : plot.member(character.characterId);
        return member != null && member.role == PlotRole.ASSASSIN ? member : null;
    }

    /** Remembers the last player blow on each player: a knocked-out player may die from it later. */
    public static void damaged(Player victim, Player attacker) {
        hits.put(victim.getUniqueId(), new Hit(attacker.getUniqueId(), now()));
    }

    /** Whoever killed the player, or failing that whoever struck them within the hit window. */
    private static List<Player> culprits(Player victim, Player killer) {
        Hit hit = hits.remove(victim.getUniqueId());
        List<Player> culprits = new ArrayList<>();
        if (killer != null) culprits.add(killer);
        Player striker = hit != null && now() - hit.at() <= PlotConfig.hitWindow() ? Bukkit.getPlayer(hit.attacker()) : null;
        if (striker != null && !striker.equals(killer)) culprits.add(striker);
        return culprits;
    }

    /** The target dying to an assassin ends the plot; an assassin dying to anyone's hand is revealed. Both die for good. */
    public static void died(Player victim, Player killer) {
        List<Player> culprits = culprits(victim, killer);
        PlotCharacter character = PlotCharacters.active(victim);
        if (character == null || culprits.isEmpty()) return;
        for (Plot plot : List.copyOf(store.plots)) {
            if (plot.phase != PlotPhase.EXECUTION) continue;
            if (plot.isTarget(character.characterId)) {
                Player assassin = culprits.stream().filter(culprit -> assassin(plot, culprit) != null).findFirst().orElse(null);
                if (assassin == null) continue;
                end(plot, PlotPhase.SUCCEEDED, "The target was assassinated", false);
                PlotCharacters.permakill(victim, character.characterId, assassin);
                tellConspirators(plot, "§e" + character.name + "§7 is dead. The plot has succeeded.");
                save();
                return;
            }
            PlotMember member = assassin(plot, victim);
            if (member == null) continue;
            Player killedBy = culprits.getFirst();
            plot.members.remove(member);
            save();
            PlotCharacters.permakill(victim, character.characterId, killedBy);
            String reveal = "§cThe fallen assassin is revealed as §e" + character.name + "§c.";
            killedBy.sendMessage(PREFIX + reveal);
            Player target = PlotCharacters.online(plot.target);
            if (target != null && !target.equals(killedBy)) target.sendMessage(PREFIX + reveal);
            tell(plot.master, "Your assassin §e" + character.name + "§7 has fallen.");
            if (plot.withRole(PlotRole.ASSASSIN).isEmpty()) {
                end(plot, PlotPhase.FAILED, "Every assassin fell", true);
                tellConspirators(plot, "The plot" + against(plot) + " has failed: no assassin is left.");
            }
            save();
            return;
        }
    }

    /** A character's permanent death, from any cause, ends or thins the plots around it. */
    public static void characterDied(String characterId) {
        boolean changed = false;
        for (Plot plot : List.copyOf(store.plots)) {
            if (!plot.phase.active()) continue;
            changed |= plot.informed.removeIf(character -> character.is(characterId));
            if (plot.master.is(characterId)) {
                end(plot, PlotPhase.DISSOLVED, "The plot master died", false);
                for (PlotMember member : plot.members)
                    tell(member.character, "With the death of §e" + plot.master.name + "§7, the plot" + against(plot) + " dissolves.");
            } else if (plot.isTarget(characterId)) {
                end(plot, PlotPhase.DISSOLVED, "The target died", false);
                tellConspirators(plot, "Fate has claimed §e" + plot.target.name + "§7 before the plot could. It dissolves.");
            } else if (plot.member(characterId) != null) {
                PlotMember member = plot.member(characterId);
                plot.members.remove(member);
                tell(plot.master, "Your conspirator §e" + member.character.name + "§7 is dead.");
                if (plot.phase.ordinal() >= PlotPhase.INVESTIGATION.ordinal() && plot.withRole(PlotRole.ASSASSIN).isEmpty()) {
                    end(plot, PlotPhase.FAILED, "Every assassin died", true);
                    tellConspirators(plot, "The plot" + against(plot) + " has failed: no assassin is left.");
                }
            } else continue;
            changed = true;
        }
        for (IntrigueSettings settings : store.characters.values())
            changed |= settings.investigators.removeIf(character -> character.is(characterId));
        if (changed) save();
    }
}
