package net.tfminecraft.simplefactions.migration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.Characters;

/** {@code /f admin quarantine list} and {@code /f admin quarantine assign <recordId> <characterId>}. */
public final class QuarantineCommands {
    private QuarantineCommands() {}

    public static boolean matches(String[] args) {
        return args.length >= 2 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("quarantine");
    }

    public static boolean handle(CommandSender sender, String[] args) {
        if (!Permissions.isAdmin(sender)) {
            sender.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
            return true;
        }
        Path root = SimpleFactions.getInstance().getDataFolder().toPath();
        Quarantine quarantine;
        try {
            quarantine = Quarantine.load(root.resolve(CharacterMigration.QUARANTINE));
        } catch (IOException unreadable) {
            sender.sendMessage("§cCannot read the quarantine: " + unreadable.getMessage());
            return true;
        }
        if (args.length == 3 && args[2].equalsIgnoreCase("list")) {
            list(sender, quarantine);
        } else if (args.length == 5 && args[2].equalsIgnoreCase("assign")) {
            assign(sender, quarantine, args[3], args[4]);
        } else {
            sender.sendMessage("§cUsage: /faction admin quarantine list | assign <recordId> <characterId>");
        }
        return true;
    }

    private static void list(CommandSender sender, Quarantine quarantine) {
        if (quarantine.records().isEmpty()) {
            sender.sendMessage("§aNothing is quarantined.");
            return;
        }
        sender.sendMessage("§eQuarantined faction records:");
        for (Quarantine.Record record : quarantine.records()) {
            String status = record.applied ? "§agiven to " + record.assigned
                    : record.assigned != null ? "§6assigned to " + record.assigned + ", applies at the next restart"
                    : "§cunresolved";
            sender.sendMessage("§f" + record.id + " §7" + record.kind + " §f" + record.original + " §7in " + record.store
                    + " (" + record.occurrences.size() + " place" + (record.occurrences.size() == 1 ? "" : "s")
                    + (record.holdsLeader() ? ", holds a leader's seat" : "") + ") " + status);
        }
    }

    private static void assign(CommandSender sender, Quarantine quarantine, String id, String character) {
        Quarantine.Record record = quarantine.get(id);
        if (record == null) {
            sender.sendMessage("§cNo quarantine record " + id);
            return;
        }
        if (record.applied) {
            sender.sendMessage("§cRecord " + id + " was already given to " + record.assigned);
            return;
        }
        String key = CharacterMigration.characterKey(character);
        if (key == null) {
            sender.sendMessage("§cThat is not a character ID");
            return;
        }
        CharacterInfo info = Characters.info(Members.characterId(key)).orElse(null);
        if (info == null) {
            sender.sendMessage("§cNo character with ID " + key);
            return;
        }
        record.assigned = key;
        try {
            quarantine.save();
        } catch (IOException failure) {
            SimpleFactions.getInstance().getLogger().log(Level.SEVERE, "Cannot save the quarantine", failure);
            sender.sendMessage("§cCannot save the quarantine; nothing changed");
            record.assigned = null;
            return;
        }
        sender.sendMessage("§aRecord " + id + " (" + record.original + ") goes to " + Members.display(key)
                + " at the next restart, before factions load.");
    }
}
