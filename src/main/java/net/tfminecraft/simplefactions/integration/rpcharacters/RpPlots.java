package net.tfminecraft.simplefactions.integration.rpcharacters;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Character plots live in RPCharacters. Looked up at runtime because the pinned RPCharacters release
 * predates them; without them nobody is hunting and nobody has an Intrigue roll there.
 */
public final class RpPlots {
    static final String API = "net.tfminecraft.rpcharacters.plots.PlotsApi";
    public static final String HUNTING = "You are hunting someone. Warbands and battles must wait until the hunt is over.";
    private static MethodHandle hunting, intrigue;
    private static boolean resolved;

    private RpPlots() {}

    static synchronized void reset() {
        resolved = false;
        hunting = intrigue = null;
    }

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null || !Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) {
            resolved = false;
            return;
        }
        try {
            Class<?> api = Class.forName(API, true, Bukkit.getPluginManager().getPlugin("RPCharacters").getClass().getClassLoader());
            hunting = MethodHandles.publicLookup().findStatic(api, "hunting", MethodType.methodType(boolean.class, Player.class));
            intrigue = MethodHandles.publicLookup().findStatic(api, "intrigue", MethodType.methodType(Integer.class, Player.class));
        } catch (ReflectiveOperationException | LinkageError older) {
            hunting = intrigue = null;
        }
    }

    /** True while the player's character is an assassin out on a hunt. */
    public static boolean hunting(Player player) {
        resolve();
        if (hunting == null || player == null) return false;
        try {
            return (boolean) hunting.invokeExact(player);
        } catch (Throwable error) {
            return false;
        }
    }

    /** The character's Intrigue from RPCharacters, rolled there on first use; null when unavailable. */
    public static Integer intrigue(Player player) {
        resolve();
        if (intrigue == null || player == null) return null;
        try {
            return (Integer) intrigue.invokeExact(player);
        } catch (Throwable error) {
            return null;
        }
    }
}
