package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.tfminecraft.tlibs.character.CharacterRef;

class OwnerTagsTest {
    private final UUID kira = UUID.randomUUID();

    @Test
    void onlyAnExactCharacterUuidNamesACharacter() {
        assertEquals("char_" + kira, OwnerTags.character(kira));
        assertEquals("char_none", OwnerTags.character(null));
        assertEquals(Optional.of(kira), OwnerTags.characterId("char_" + kira));
        assertEquals(Optional.of(kira), OwnerTags.characterId("CHAR_" + kira.toString().toUpperCase()));
        for (String other : Arrays.asList(null, "char_none", "char_", "player_" + kira, "char_" + kira + "x",
                "char_" + kira.toString().replace("-", "x"), "char_1-1-1-1-11111111111111111111111111")) {
            assertEquals(Optional.empty(), OwnerTags.characterId(other), String.valueOf(other));
            assertFalse(OwnerTags.isCharacter(other));
        }
        assertTrue(OwnerTags.names("char_" + kira, kira));
        assertFalse(OwnerTags.names("char_" + kira, UUID.randomUUID()));
        assertFalse(OwnerTags.names("char_" + kira, null));
    }

    @Test
    void unownedAndLegacyValuesAreRecognised() {
        for (String unowned : Arrays.asList(null, "", " ", "none", "NONE")) assertTrue(OwnerTags.isUnowned(unowned));
        assertFalse(OwnerTags.isUnowned("char_none"));
        assertEquals("Steve", OwnerTags.legacyName("player_Steve"));
        assertEquals("Steve", OwnerTags.legacyName("PLAYER_Steve"));
        for (String other : Arrays.asList(null, "player_", "player_none", "PLAYER_NONE", "char_" + kira, "steve")) {
            assertNull(OwnerTags.legacyName(other), String.valueOf(other));
        }
    }

    @Test
    void whitelistEntriesUseVehicleFrameworksStoredForm() {
        CharacterRef bran = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        String entry = OwnerTags.whitelistEntry(bran, "Bran");
        assertEquals("char_" + bran.characterId() + ";" + bran.ownerId() + ";Bran", entry);
        assertEquals("char_" + bran.characterId() + ";" + bran.ownerId() + ";", OwnerTags.whitelistEntry(bran, null));
        assertTrue(OwnerTags.isCharacterEntry(entry));
        for (String legacy : Arrays.asList(null, "player_Bran", "Bran", ";x", "char_none;x;y")) {
            assertFalse(OwnerTags.isCharacterEntry(legacy), String.valueOf(legacy));
        }
    }

    @Test
    void ownerFieldsCompareTheOwnershipAMigrationWrites() {
        OwnerFields fields = new OwnerFields(null, Arrays.asList("a", null, "b"), true, true, "ticket");
        assertEquals("none", fields.owner());
        assertEquals(List.of("a", "b"), fields.whitelist());
        assertEquals("none", new OwnerFields(" ", null, false, false, null).owner());
        OwnerFields moved = fields.withOwnership("char_" + kira, List.of("c"));
        assertEquals(new OwnerFields("char_" + kira, List.of("c"), true, true, "ticket"), moved);
        assertTrue(moved.sameOwnership(new OwnerFields("char_" + kira, List.of("c"), true, false, null)));
        assertFalse(moved.sameOwnership(new OwnerFields("char_" + kira, List.of("c"), false, true, "ticket")));
        assertFalse(moved.sameOwnership(new OwnerFields("char_" + kira, List.of(), true, true, "ticket")));
        assertFalse(moved.sameOwnership(fields));
        assertFalse(moved.sameOwnership(null));
        OwnerMeta meta = new OwnerMeta("v", "cart", null, null, null);
        assertFalse(meta.hasStoredRow());
        assertNull(meta.currentOwner());
        assertEquals(Optional.empty(), meta.current());
    }
}
