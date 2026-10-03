package net.tfminecraft.simplefactions.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RpCharactersLeaderCharacterProbeTest {

    @TempDir
    Path folder;

    private void character(String file, String json) throws IOException {
        Files.writeString(folder.resolve(file), json, StandardCharsets.UTF_8);
    }

    @Test
    void readsTheActiveCharacterFromDisk() throws IOException {
        character("a.json", "{\"name\":\"Hazel Stonebrook\",\"active\":\"false\"}");
        character("b.json", "{\"name\":\"Grunk the Bold\",\"active\":\"true\"}");
        assertEquals("Grunk the Bold",
                RpCharactersLeaderCharacterProbe.activeNameOnDisk(folder.toFile()));
    }

    @Test
    void acceptsABooleanActiveFlag() throws IOException {
        character("a.json", "{\"name\":\"Aelin\",\"active\":true}");
        assertEquals("Aelin", RpCharactersLeaderCharacterProbe.activeNameOnDisk(folder.toFile()));
    }

    @Test
    void skipsStagingAndBrokenFiles() throws IOException {
        character(".rpcharacters-123.tmp", "{\"name\":\"Half written\",\"active\":\"true\"}");
        character("broken.json", "{not json");
        character("c.json", "{\"name\":\"Brann\",\"active\":\"true\"}");
        assertEquals("Brann", RpCharactersLeaderCharacterProbe.activeNameOnDisk(folder.toFile()));
    }

    @Test
    void noActiveCharacterOrNoFolderIsUnknown() throws IOException {
        character("a.json", "{\"name\":\"Hazel\",\"active\":\"false\"}");
        assertNull(RpCharactersLeaderCharacterProbe.activeNameOnDisk(folder.toFile()));
        assertNull(RpCharactersLeaderCharacterProbe.activeNameOnDisk(new File(folder.toFile(), "missing")));
    }
}
