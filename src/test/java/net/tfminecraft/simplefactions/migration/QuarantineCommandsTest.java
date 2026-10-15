package net.tfminecraft.simplefactions.migration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.testsupport.SharedIdentity;
import net.tfminecraft.tlibs.character.CharacterRef;

class QuarantineCommandsTest {
    @TempDir
    Path root;

    private SimpleFactions previous;
    private final CommandSender staff = mock(CommandSender.class);

    @BeforeEach
    void plugin() {
        previous = SimpleFactions.plugin;
        SimpleFactions plugin = mock(SimpleFactions.class);
        when(plugin.getDataFolder()).thenReturn(root.toFile());
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        SimpleFactions.plugin = plugin;
        when(staff.hasPermission("simplefactions.admin")).thenReturn(true);
    }

    @AfterEach
    void restore() {
        SimpleFactions.plugin = previous;
    }

    private Quarantine.Record record(String id, boolean leader) throws IOException {
        Quarantine quarantine = Quarantine.load(root.resolve(CharacterMigration.QUARANTINE));
        Quarantine.Record record = new Quarantine.Record();
        record.id = id;
        record.store = "Data/rome.json";
        record.kind = Quarantine.NAME;
        record.original = "Ghost";
        record.token = Members.QUARANTINED + id;
        record.occurrences.add(new Quarantine.Occurrence(leader ? "$.guilds[rome].leader" : "$.guilds[rome].members[1]",
                new com.google.gson.JsonPrimitive("Ghost")));
        quarantine.merge(List.of(record));
        quarantine.save();
        return record;
    }

    @Test
    void completionOffersOnlyTheAuthorizedGrammarAndUnresolvedSnapshot() throws IOException {
        assertEquals(List.of("quarantine"), QuarantineCommands.complete(staff, new String[] {"admin", "Qu"}));
        assertEquals(List.of("list", "assign"), QuarantineCommands.complete(staff, new String[] {"admin", "quarantine", ""}));
        assertEquals(List.of("assign"), QuarantineCommands.complete(staff, new String[] {"admin", "quarantine", "AS"}));
        record("q1", true);
        record("q2", false);
        record("q3", false);
        Quarantine quarantine = Quarantine.load(root.resolve(CharacterMigration.QUARANTINE));
        quarantine.get("q2").assigned = "pending";
        quarantine.get("q3").applied = true;
        quarantine.save();
        assertEquals(List.of("q1"), QuarantineCommands.complete(staff, new String[] {"admin", "quarantine", "assign", "Q"}));
        Files.delete(root.resolve(CharacterMigration.QUARANTINE));
        assertEquals(List.of("q1"), QuarantineCommands.complete(staff, new String[] {"admin", "quarantine", "assign", ""}), "completion must not reread disk");
        assertTrue(QuarantineCommands.complete(mock(CommandSender.class), new String[] {"admin", "quarantine", "assign", ""}).isEmpty());
        assertTrue(QuarantineCommands.complete(staff, new String[] {"admin", "quarantine", "assign", "q1", ""}).isEmpty());
        assertTrue(QuarantineCommands.complete(staff, new String[] {"admin", "quarantine", "list", ""}).isEmpty());
        assertTrue(QuarantineCommands.complete(staff, new String[] {"admin", "other", ""}).isEmpty());
    }

    @Test
    void completionDropsAssignedRecordsAndDoesNotReuseAnotherDataFoldersSnapshot() throws IOException {
        record("q1", true);
        try (var identity = new SharedIdentity()) {
            CharacterRef ada = identity.character(identity.account("Steve"), "Ada");
            QuarantineCommands.handle(staff, new String[] {"admin", "quarantine", "assign", "q1", ada.characterId().toString()});
            assertTrue(QuarantineCommands.complete(staff, new String[] {"admin", "quarantine", "assign", ""}).isEmpty());
        }
        record("q4", false);
        when(SimpleFactions.plugin.getDataFolder()).thenReturn(root.resolve("other").toFile());
        assertTrue(QuarantineCommands.complete(staff, new String[] {"admin", "quarantine", "assign", ""}).isEmpty());
    }

    @Test
    void onlyStaffMayUseIt() {
        assertTrue(QuarantineCommands.matches(new String[] {"admin", "QUARANTINE"}));
        assertFalse(QuarantineCommands.matches(new String[] {"admin"}));
        assertFalse(QuarantineCommands.matches(new String[] {"list", "quarantine"}));
        CommandSender player = mock(CommandSender.class);
        assertTrue(QuarantineCommands.handle(player, new String[] {"admin", "quarantine", "list"}));
        verify(player).sendMessage("§a[SimpleFactions]§c You do not have access to this command");
    }

    @Test
    void listsRecordsWithTheirState() throws IOException {
        QuarantineCommands.handle(staff, new String[] {"admin", "quarantine", "list"});
        verify(staff).sendMessage("§aNothing is quarantined.");

        record("q1", true);
        Quarantine.Record second = record("q2", false);
        second.occurrences.add(second.occurrences.getFirst());
        Quarantine quarantine = Quarantine.load(root.resolve(CharacterMigration.QUARANTINE));
        quarantine.get("q2").assigned = "abc";
        quarantine.merge(List.of(second));
        quarantine.get("q2").assigned = "abc";
        Quarantine.Record third = new Quarantine.Record();
        third.id = "q3";
        third.store = "Cache/logins.json";
        third.kind = Quarantine.NAME;
        third.original = "Old";
        third.assigned = "def";
        third.applied = true;
        quarantine.merge(List.of(third));
        quarantine.get("q3").assigned = "def";
        quarantine.get("q3").applied = true;
        quarantine.save();

        QuarantineCommands.handle(staff, new String[] {"admin", "quarantine", "list"});
        verify(staff).sendMessage("§eQuarantined faction records:");
        verify(staff).sendMessage(contains("q1 §7name §fGhost §7in Data/rome.json (1 place, holds a leader's seat) §cunresolved"));
        verify(staff).sendMessage(contains("q2 §7name §fGhost §7in Data/rome.json (2 places) §6assigned to abc, applies at the next restart"));
        verify(staff).sendMessage(contains("q3 §7name §fOld §7in Cache/logins.json (0 places) §agiven to def"));
    }

    @Test
    void assignsACharacterForTheNextStart() throws IOException {
        record("q1", true);
        try (var identity = new SharedIdentity()) {
            CharacterRef ada = identity.character(identity.account("Steve"), "Ada");
            QuarantineCommands.handle(staff, new String[] {"admin", "quarantine", "assign", "q1",
                    ada.characterId().toString().toUpperCase()});
            verify(staff).sendMessage("§aRecord q1 (Ghost) goes to Ada at the next restart, before factions load.");
            assertEquals(Members.key(ada),
                    Quarantine.load(root.resolve(CharacterMigration.QUARANTINE)).get("q1").assigned);
        }
    }

    @Test
    void refusesUnknownRecordsCharactersAndFinishedRecords() throws IOException {
        record("q1", false);
        try (var identity = new SharedIdentity()) {
            QuarantineCommands.handle(staff, new String[] {"admin", "quarantine", "assign", "nope", "x"});
            verify(staff).sendMessage("§cNo quarantine record nope");
            QuarantineCommands.handle(staff, new String[] {"admin", "quarantine", "assign", "q1", "Steve"});
            verify(staff).sendMessage("§cThat is not a character ID");
            String unknown = UUID.randomUUID().toString();
            QuarantineCommands.handle(staff, new String[] {"admin", "quarantine", "assign", "q1", unknown});
            verify(staff).sendMessage("§cNo character with ID " + unknown);
            Quarantine quarantine = Quarantine.load(root.resolve(CharacterMigration.QUARANTINE));
            quarantine.get("q1").assigned = "done";
            quarantine.get("q1").applied = true;
            quarantine.save();
            QuarantineCommands.handle(staff, new String[] {"admin", "quarantine", "assign", "q1", unknown});
            verify(staff).sendMessage("§cRecord q1 was already given to done");
            QuarantineCommands.handle(staff, new String[] {"admin", "quarantine", "what"});
            verify(staff).sendMessage("§cUsage: /faction admin quarantine list | assign <recordId> <characterId>");
        }
    }

    @Test
    void reportsAnUnreadableOrUnwritableQuarantine() throws IOException {
        Files.createDirectories(root.resolve("migrations"));
        Files.writeString(root.resolve(CharacterMigration.QUARANTINE), "{oops");
        QuarantineCommands.handle(staff, new String[] {"admin", "quarantine", "list"});
        verify(staff).sendMessage(startsWith("§cCannot read the quarantine: "));

        Files.delete(root.resolve(CharacterMigration.QUARANTINE));
        record("q1", false);
        try (var identity = new SharedIdentity()) {
            CharacterRef ada = identity.character(identity.account("Steve"), "Ada");
            // A directory where the temporary file would go makes the save fail.
            Path migrations = root.resolve("migrations");
            migrations.toFile().setWritable(false);
            try {
                QuarantineCommands.handle(staff, new String[] {"admin", "quarantine", "assign", "q1", ada.characterId().toString()});
            } finally {
                migrations.toFile().setWritable(true);
            }
            verify(staff).sendMessage("§cCannot save the quarantine; nothing changed");
            assertNull(Quarantine.load(root.resolve(CharacterMigration.QUARANTINE)).get("q1").assigned);
        }
    }
}
