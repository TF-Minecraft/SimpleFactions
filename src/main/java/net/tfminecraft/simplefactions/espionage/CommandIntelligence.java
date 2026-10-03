package net.tfminecraft.simplefactions.espionage;

import java.util.function.Supplier;
import org.bukkit.entity.Player;

/** Only a command creating a faction/guild menu can request new daily intelligence. */
public final class CommandIntelligence {
    private static final ThreadLocal<Context> current = new ThreadLocal<>();
    private static final class Context {
        final Player player;
        boolean gathered;
        Context(Player player) { this.player = player; }
    }
    private CommandIntelligence() {}

    public static boolean execute(Player player, Supplier<Boolean> command) {
        Context previous = current.get();
        current.set(new Context(player));
        try { return command.get(); }
        finally {
            if (previous == null) current.remove();
            else current.set(previous);
        }
    }

    public static void beforeMenu() {
        Context context = current.get();
        if (context != null && context.player != null && !context.gathered) {
            context.gathered = true;
            EspionageService.refreshReports(context.player);
        }
    }
}
