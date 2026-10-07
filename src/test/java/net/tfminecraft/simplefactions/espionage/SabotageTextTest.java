package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SabotageTextTest {
    @Test void everyBundledLevelHasRoleplayTextWithTheReductionFilledIn() {
        for (boolean offense : new boolean[] {true, false}) {
            assertFalse(SabotageText.title(offense).isBlank());
            assertFalse(SabotageText.lore(offense).isEmpty());
            for (int reduction : new int[] {0, 25, 50, 75, 100}) {
                var level = SabotageText.level(offense, reduction);
                assertFalse(level.name().isBlank(), offense + " " + reduction);
                assertFalse(level.lore().isEmpty(), offense + " " + reduction);
                assertFalse(level.message().isBlank(), offense + " " + reduction);
                String all = level.name() + level.lore() + level.message();
                assertFalse(all.contains("{reduction}"), all);
                if (reduction > 0) assertTrue(level.message().contains("-" + reduction), level.message());
            }
        }
        assertFalse(SabotageText.footer().isEmpty());
        assertFalse(SabotageText.note().isBlank());
    }

    @Test void configuredTextOverridesTheDefaultsAndMissingKeysFallBack() {
        var config = new YamlConfiguration();
        config.set("espionage.sabotage.offense.levels.25.name", "&eLetters in the river");
        config.set("espionage.sabotage.offense.levels.25.lore", List.of("&7Lost {reduction} letters"));
        config.set("espionage.sabotage.defense.title", "Counter-espionage");
        config.set("espionage.sabotage.footer", List.of());
        config.set("espionage.sabotage.note", "");
        try {
            EspionageConfig.load(config);
            var level = SabotageText.level(true, 25);
            assertEquals("&eLetters in the river", level.name());
            assertEquals(List.of("&7Lost 25 letters"), level.lore());
            assertTrue(level.message().contains("dispatch"), "Missing message falls back to the bundled text");
            assertEquals("Counter-espionage", SabotageText.title(false));
            assertEquals("Offensive sabotage", SabotageText.title(true));
            assertEquals(List.of(), SabotageText.footer());
            assertEquals("", SabotageText.note());
        } finally { EspionageConfig.load(new YamlConfiguration()); }
        assertEquals("&eStray dispatches", SabotageText.level(true, 25).name());
    }
}
