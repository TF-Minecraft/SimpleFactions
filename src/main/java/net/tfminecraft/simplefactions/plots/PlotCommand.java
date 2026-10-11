package net.tfminecraft.simplefactions.plots;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.tfminecraft.simplefactions.espionage.CharacterNames;

/** /plot: the Plots menu, the same actions by command, and staff approval. */
public final class PlotCommand implements CommandExecutor, TabCompleter {
    private static final List<String> PLAYER = List.of("request", "accept", "decline", "invite", "inform", "role", "dismiss",
            "start", "launch", "leave", "abandon", "approach", "defender", "investigator", "status");
    private static final List<String> STAFF = List.of("approve", "deny", "cancel", "day", "list", "info");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (STAFF.contains(sub)) return staff(sender, sub, args);
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use plots. Staff: /plot " + String.join("|", STAFF));
            return true;
        }
        String rest = String.join(" ", Arrays.copyOfRange(args, Math.min(1, args.length), args.length));
        switch (sub) {
            case "" -> PlotMenu.open(player);
            case "request" -> PlotService.request(player);
            case "accept", "decline" -> {
                if (args.length != 2) return usage(player, sub + " <code>");
                if (sub.equals("accept")) PlotService.accept(player, args[1]);
                else PlotService.decline(player, args[1]);
            }
            case "invite", "inform", "investigator" -> {
                if (rest.isBlank()) return usage(player, sub + " <player or character>");
                Player other = CharacterNames.resolveOnline(player, rest);
                if (other == null) return true;
                if (sub.equals("invite")) PlotService.invite(player, other);
                else if (sub.equals("inform")) PlotService.inform(player, other);
                else PlotService.toggleInvestigator(player, other);
            }
            case "role" -> role(player, args);
            case "dismiss" -> {
                PlotMember member = member(player, rest);
                if (member != null) PlotService.dismiss(player, member.character.characterId);
            }
            case "start" -> PlotService.begin(player);
            case "launch" -> PlotService.launch(player);
            case "leave" -> PlotService.leave(player);
            case "abandon" -> {
                if (!rest.equalsIgnoreCase("confirm")) return usage(player, "abandon confirm");
                PlotService.abandon(player);
            }
            case "approach" -> {
                PlotApproach approach = PlotApproach.parse(rest);
                if (approach == null) return usage(player, "approach <offensive|neutral|defensive>");
                PlotService.setApproach(player, approach);
            }
            case "defender" -> defender(player, rest);
            case "status" -> status(player);
            default -> usage(player, String.join("|", PLAYER));
        }
        return true;
    }

    private static boolean usage(CommandSender sender, String text) {
        sender.sendMessage("§7/plot " + text);
        return true;
    }

    private static void defender(Player player, String rest) {
        if (rest.isBlank()) {
            usage(player, "defender <self|spymaster|player or character>");
            return;
        }
        if (rest.equalsIgnoreCase("self")) PlotService.setDefender(player, DefenderChoice.SELF, null);
        else if (rest.equalsIgnoreCase("spymaster")) PlotService.setDefender(player, DefenderChoice.SPYMASTER, null);
        else {
            Player other = CharacterNames.resolveOnline(player, rest);
            if (other != null) PlotService.setDefender(player, DefenderChoice.CHARACTER, other);
        }
    }

    /** /plot role <name...> <assassin|hide|progress|strength|none>; the name may have spaces. */
    private static void role(Player player, String[] args) {
        if (args.length < 3) {
            usage(player, "role <member> <assassin|hide|progress|strength|none>");
            return;
        }
        String choice = args[args.length - 1].toLowerCase(Locale.ROOT);
        PlotRole role = choice.equals("assassin") ? PlotRole.ASSASSIN : choice.equals("none") ? PlotRole.UNASSIGNED
                : CollaboratorTask.parse(choice) != null ? PlotRole.COLLABORATOR : null;
        if (role == null) {
            usage(player, "role <member> <assassin|hide|progress|strength|none>");
            return;
        }
        PlotMember member = member(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1)));
        if (member != null) PlotService.assign(player, member.character.characterId, role, CollaboratorTask.parse(choice));
    }

    /** A member of the player's own plot, by character or Minecraft name. */
    private static PlotMember member(Player player, String name) {
        Plot plot = PlotService.mastered(player);
        if (plot == null) return null;
        for (PlotMember member : plot.members)
            if (member.character.name.equalsIgnoreCase(name) || member.character.playerName.equalsIgnoreCase(name)) return member;
        player.sendMessage("§cNobody by that name is in your plot.");
        return null;
    }

    private static void status(Player player) {
        PlotCharacter me = PlotService.character(player);
        if (me == null) return;
        player.sendMessage(PlotService.PREFIX + "Intrigue §e" + PlotService.intrigue(me) + "§7, plotting §e"
                + PlotService.offense(me) + "§7, defending §e" + PlotService.defense(me) + "§7.");
        Plot plot = PlotService.store().plotOf(me.characterId);
        if (plot != null) player.sendMessage(PlotService.PREFIX + (plot.target == null ? "Request " + plot.code + " awaits approval."
                : "Plot against §e" + plot.target.name + "§7: " + PlotService.summary(plot)));
        for (Plot against : PlotService.store().against(me.characterId))
            for (String line : against.knowledge.values()) player.sendMessage(PlotService.PREFIX + "§c" + line);
    }

    private static boolean staff(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission(PlotConfig.staffPermission())) {
            sender.sendMessage("§cYou do not have permission to manage plots.");
            return true;
        }
        if (sub.equals("list")) {
            var plots = PlotService.store().plots.stream().filter(plot -> plot.phase.active()).toList();
            if (plots.isEmpty()) sender.sendMessage("§7No plots are running.");
            for (Plot plot : plots) sender.sendMessage("§e" + plot.code + "§7 " + plot.master.name + " (" + plot.master.playerName
                    + ") -> " + (plot.target == null ? "?" : plot.target.name + " (" + plot.target.playerName + ")") + ": " + plot.phase.label());
            return true;
        }
        if (args.length != (sub.equals("approve") ? 3 : 2))
            return usage(sender, sub + " <code>" + (sub.equals("approve") ? " <target player>" : ""));
        switch (sub) {
            case "approve" -> PlotService.approve(sender, args[1], args[2]);
            case "deny" -> PlotService.deny(sender, args[1]);
            case "cancel" -> PlotService.cancel(sender, args[1]);
            case "day" -> PlotService.passDay(sender, args[1]);
            default -> info(sender, args[1]);
        }
        return true;
    }

    private static void info(CommandSender sender, String code) {
        Plot plot = PlotService.store().plot(code);
        if (plot == null) {
            sender.sendMessage("§cNo plot has that code.");
            return;
        }
        sender.sendMessage("§e" + plot.code + "§7: " + PlotService.summary(plot) + (plot.outcome == null ? "" : " (" + plot.outcome + ")"));
        sender.sendMessage("§7Master: " + plot.master.name + " (" + plot.master.playerName + "), target: "
                + (plot.target == null ? "none yet" : plot.target.name + " (" + plot.target.playerName + ")"));
        for (PlotMember member : plot.members)
            sender.sendMessage("§7- " + member.character.name + " (" + member.character.playerName + "): " + member.roleLabel());
        for (PlotCharacter informed : plot.informed) sender.sendMessage("§8- " + informed.name + " (" + informed.playerName + "): informed");
        if (plot.target != null) {
            var protection = PlotService.protection(plot);
            sender.sendMessage("§7Protection " + Math.round(protection.value()) + ", betrayal " + protection.betrayal()
                    + ", known to target: " + plot.discovered + (plot.exposed ? " (exposed)" : "")
                    + ", conspirators investigated: " + plot.investigated.size());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = new ArrayList<>(PLAYER);
        if (sender.hasPermission(PlotConfig.staffPermission())) options.addAll(STAFF);
        return options.stream().filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
