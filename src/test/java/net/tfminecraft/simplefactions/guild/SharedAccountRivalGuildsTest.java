package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;

/** One account, two characters, two rival guilds: each character acts only for its own guild. */
class SharedAccountRivalGuildsTest {
    private FactionDomainFixture fixture;
    private TestCharacters characters;

    @BeforeEach
    void setUp() {
        fixture = new FactionDomainFixture();
        characters = TestCharacters.install();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void eachCharacterOfOneAccountLeadsOnlyItsOwnRivalGuild() {
        UUID account = UUID.randomUUID();
        String aldric = characters.character(account, "Aldric Vane");
        String mira = characters.character(account, "Mira Thorn");
        assertNotEquals(aldric, mira);
        assertEquals(Members.accountId(aldric), Members.accountId(mira));

        Faction north = fixture.saved("north", characters.character(UUID.randomUUID(), "North King"));
        Faction south = fixture.saved("south", characters.character(UUID.randomUUID(), "South Queen"));
        Guild smiths = fixture.guild(north, "smiths", aldric);
        Guild masons = fixture.guild(south, "masons", mira);
        Player player = fixture.player("SharedAccount");

        characters.play(player, aldric);
        assertSame(smiths, FactionManager.getGuildByMember(Members.key(player)));
        assertSame(north, FactionManager.getByMember(Members.key(player)));
        assertTrue(smiths.isLeader(player));
        assertTrue(smiths.isMember(player));
        assertFalse(masons.isLeader(player));
        assertFalse(masons.isMember(player));
        assertFalse(south.isMember(player));
        assertEquals("Aldric Vane", Members.name(player));

        characters.play(player, mira);
        assertSame(masons, FactionManager.getGuildByMember(Members.key(player)));
        assertSame(south, FactionManager.getByMember(Members.key(player)));
        assertTrue(masons.isLeader(player));
        assertFalse(smiths.isLeader(player));
        assertFalse(smiths.isMember(player));
        assertFalse(north.isMember(player));
        assertEquals("Mira Thorn", Members.display(masons.getLeader()));

        characters.play(player, null);
        assertNull(FactionManager.getGuildByMember(Members.key(player)));
        assertFalse(smiths.isLeader(player));
        assertFalse(masons.isLeader(player));
    }
}
