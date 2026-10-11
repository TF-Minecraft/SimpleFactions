package net.tfminecraft.rpcharacters.plots;

import org.bukkit.entity.Player;

/** Stands in for RPCharacters' plot API, which the pinned RPCharacters release does not have. */
public final class PlotsApi {
    public static boolean huntingAnswer;
    public static Integer intrigueAnswer;
    public static RuntimeException failure;

    private PlotsApi() {}

    public static boolean hunting(Player player) {
        if (failure != null) throw failure;
        return huntingAnswer;
    }

    public static Integer intrigue(Player player) {
        if (failure != null) throw failure;
        return intrigueAnswer;
    }
}
