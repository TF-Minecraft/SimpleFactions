package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.espionage.OfficeOptionalIntegrationsTest.IdentityFixture;
import net.tfminecraft.simplefactions.identity.Members;

/** Names espionage shows and resolves now come from member keys through {@link Members}. */
class CharacterNameResolutionTest {
    @BeforeEach void guardEverything() { net.tfminecraft.simplefactions.testsupport.EspionageModes.guardEverything(); }
    @AfterEach void resetEspionage() { net.tfminecraft.simplefactions.testsupport.EspionageModes.reset(); }

    @Test void reportsShowCharacterNamesAndNeverFallBackToAccounts() {
        try (var f = new IdentityFixture("Account").shared()) {
            var viewer = player("ForeignViewer");
            UUID id = UUID.randomUUID();
            String key = Members.key(id);
            assertEquals("Unknown", Members.display(key), "An unreadable character never shows its account");
            f.infos.put(id, OfficeOptionalIntegrationsTest.info(new net.tfminecraft.tlibs.character.CharacterRef(f.owner, id),
                    net.tfminecraft.tlibs.character.CharacterStatus.ALIVE));
            String name = Members.display(key);
            assertNotEquals("Account", name);
            assertEquals(name, Members.display(viewer, key));
            OfflinePlayer account = mock(OfflinePlayer.class);
            when(account.getName()).thenReturn("Account");
            f.bukkit.when(() -> Bukkit.getOfflinePlayer(f.owner)).thenReturn(account);
            when(viewer.hasPermission(EspionageService.BYPASS_PERMISSION)).thenReturn(true);
            assertEquals(name + " §7(Account)", Members.display(viewer, key), "Staff see who plays it");
            f.registered = false;
            assertEquals("Unknown", Members.display(key));
        }
    }

    @Test void onlineCandidatesResolveByCharacterOrAccountNameAndRefuseAmbiguity() {
        try (var f = new IdentityFixture("Account").shared()) {
            var sender = player("Sender");
            var session = f.activate(UUID.randomUUID());
            String name = Members.name(f.player);
            assertSame(f.player, Members.resolveOnline(sender, " \"" + name.toLowerCase() + "\" "));
            assertSame(f.player, Members.resolveOnline(sender, "Account"));
            assertEquals(Members.key(session.ref()), Members.findOnline(name));
            assertNull(Members.resolveOnline(sender, "No Such Player"));
            verify(sender).sendMessage(contains("No online player found"));
            var twin = mock(Player.class);
            when(twin.getName()).thenReturn(name);
            f.bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.of(f.player, twin));
            assertNull(Members.resolveOnline(sender, name.toUpperCase()));
            verify(sender).sendMessage(contains("Several characters share that name"));
        }
    }

    @Test void completionReturnsOnlyTheCurrentWordAndGuardsPastTheEndOfAName() {
        var names = List.of("Lady Raven", "Lady Rose", "Lady", "Account", "Lady Raven");
        assertEquals(List.of("Lady Raven", "Lady Rose", "Lady"), Members.completions(names, new String[]{"spymaster", "La"}));
        assertEquals(List.of("Raven", "Rose"), Members.completions(names, new String[]{"spymaster", "Lady", "R"}));
        assertTrue(Members.completions(names, new String[]{"spymaster", "Lady", "Raven", ""}).isEmpty());
        assertEquals(List.of("Raven"), Members.completions(
                List.of("§6Lady §eRaven"), new String[]{"spymaster", "Lady", "R"}));
    }

    private static Player player(String name) {
        var player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
