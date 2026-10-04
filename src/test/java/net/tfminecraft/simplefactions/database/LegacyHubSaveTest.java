package net.tfminecraft.simplefactions.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;

import org.junit.jupiter.api.Test;

class LegacyHubSaveTest {
    @Test
    void oldHubFieldsLoadAndTheNextSaveDropsThem() throws Exception {
        File file = new File("src/test/resources/legacy-hub-faction.json");
        FactionData loaded = JsonUtil.readJson(file, FactionData.class);

        assertEquals("vardera", loaded.id);
        assertEquals("Vardera", loaded.name);
        GuildData guild = loaded.guilds.get(0);
        assertEquals("The Chisels", guild.name);
        assertEquals("supply_lines", guild.branches.get(0).id);
        assertEquals(4, guild.branches.get(0).level.intValue());
        assertEquals("infrastructure", guild.branches.get(1).id);
        assertEquals("storehouses", guild.branches.get(2).id);

        String rewritten = JsonUtil.GSON.toJson(loaded);
        assertFalse(rewritten.contains("hub tax"));
        assertFalse(rewritten.contains("hub permits"));
        assertFalse(rewritten.contains("supply hubs"));
        assertFalse(rewritten.contains("hub agreements"));
        assertFalse(rewritten.contains("hub offers"));
        assertFalse(rewritten.contains("supply hub tutorial dismissals"));
        assertEquals("vardera", JsonUtil.GSON.fromJson(rewritten, FactionData.class).id);
    }
}
