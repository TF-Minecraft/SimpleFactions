package me.Plugins.SimpleFactions.Utils;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;

public class Represents {
    public static String represents(Faction f, String player) {
        Guild origin = FactionManager.getGuildByMember(player);
        if(origin != null) {
            String type = origin.isBase() ? " §7(#4ecc5eCapital§7)" : " §7("+origin.getType().getName()+"§7)";
            if(origin.getFaction().getOverlord() != null && RelationManager.isOnOverlordPath(origin.getFaction(), f)) {
                type = " §7(#4269a8Vassal§7)";
            }
            return origin.getName()+type;
        }
        return "";
    }
}
