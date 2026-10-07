package net.tfminecraft.simplefactions.espionage;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Roleplay text for the Spymaster's private sabotage. Missing keys fall back to the bundled defaults. */
public final class SabotageText {
    private static final String ROOT = "espionage.sabotage.";
    private static final YamlConfiguration BUNDLED = YamlConfiguration.loadConfiguration(new InputStreamReader(
            SabotageText.class.getResourceAsStream("/special-positions.yml"), StandardCharsets.UTF_8));
    private static ConfigurationSection source = BUNDLED;

    public record Level(String name, List<String> lore, String message) {}

    private SabotageText() {}

    static void load(ConfigurationSection config) { source = config; }

    public static String title(boolean offense) { return string(side(offense) + "title", 0); }
    public static List<String> lore(boolean offense) { return list(side(offense) + "lore", 0); }
    public static List<String> footer() { return list("footer", 0); }
    public static String note() { return string("note", 0); }

    /** Reductions are 0, 25, 50, 75 or 100; {reduction} becomes the level. */
    public static Level level(boolean offense, int reduction) {
        String path = side(offense) + "levels." + reduction + ".";
        return new Level(string(path + "name", reduction), list(path + "lore", reduction), string(path + "message", reduction));
    }

    private static String side(boolean offense) { return offense ? "offense." : "defense."; }

    private static String string(String key, int reduction) {
        String value = source.getString(ROOT + key);
        if (value == null) value = BUNDLED.getString(ROOT + key, "");
        return value.replace("{reduction}", String.valueOf(reduction));
    }

    private static List<String> list(String key, int reduction) {
        var lines = source.isList(ROOT + key) ? source.getStringList(ROOT + key) : BUNDLED.getStringList(ROOT + key);
        return lines.stream().map(line -> line.replace("{reduction}", String.valueOf(reduction))).toList();
    }
}
