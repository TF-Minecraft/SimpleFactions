package me.Plugins.SimpleFactions.War.battle.ui;

import org.bukkit.command.CommandSender;

public class BattlePermissions {
public static String Permission_Admin;
    
    static {
        BattlePermissions.Permission_Admin = "warbands.admin";
    }
    
    public static boolean isAdmin(final CommandSender commandSender) {
        return commandSender.hasPermission(BattlePermissions.Permission_Admin);
    }
}