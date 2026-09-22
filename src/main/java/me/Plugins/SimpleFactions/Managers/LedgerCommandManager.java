package me.Plugins.SimpleFactions.Managers;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LedgerCommandManager implements CommandExecutor {
    public final String cmd = "ledger";
    private final InventoryManager inventoryManager;

    public LedgerCommandManager(InventoryManager inventoryManager) {
        this.inventoryManager = inventoryManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase(cmd)) {
            return false;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }
        inventoryManager.playerLedgerView.open((Player) sender);
        return true;
    }
}
