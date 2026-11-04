package net.tfminecraft.simplefactions.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterRef;

class TaxExemptionsTest {
    private final Set<UUID> permitted = new HashSet<>();
    private final UUID owner = UUID.randomUUID();
    private final CharacterRef npc = new CharacterRef(owner, UUID.randomUUID());
    private final CharacterRef own = new CharacterRef(owner, UUID.randomUUID());
    private final CharacterRef citizen = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    private MockedStatic<SimpleFactions> plugin;
    private MockedStatic<Members> members;
    private Logger logger;

    @BeforeEach
    void setUp() {
        TaxExemptions.reset();
        TaxExemptions.setPermissionCheck(permitted::contains);
        plugin = mockStatic(SimpleFactions.class);
        SimpleFactions instance = mock(SimpleFactions.class);
        logger = mock(Logger.class);
        when(instance.getLogger()).thenReturn(logger);
        plugin.when(SimpleFactions::getInstance).thenReturn(instance);
        members = mockStatic(Members.class, CALLS_REAL_METHODS);
        members.when(() -> Members.display(anyString())).thenReturn("Captain Avalon");
        members.when(() -> Members.accountName(anyString())).thenReturn("Lucy");
    }

    @AfterEach
    void tearDown() {
        members.close();
        plugin.close();
        TaxExemptions.reset();
    }

    private static String key(CharacterRef ref) {
        return ref.characterId().toString().toLowerCase(Locale.ROOT);
    }

    @Test
    void everyCharacterOfAPermittedAccountOwesNoTax() {
        assertFalse(TaxExemptions.exempt(npc), "without the permission it is taxed");
        assertFalse(TaxExemptions.exempt(null));
        permitted.add(owner);
        assertTrue(TaxExemptions.exempt(npc));
        assertTrue(TaxExemptions.exempt(own), "the rank covers whichever character is played");
        assertFalse(TaxExemptions.exempt(citizen), "other accounts stay taxed");
        permitted.clear();
        assertFalse(TaxExemptions.exempt(npc), "going off duty taxes it again");
    }

    @Test
    void theFirstExemptCheckOfEachCharacterIsLoggedOnce() {
        permitted.add(owner);
        TaxExemptions.exempt(npc);
        TaxExemptions.exempt(npc);
        TaxExemptions.exempt(own);
        verify(logger).info("[TaxExempt] Lucy owes no citizen tax as Captain Avalon (" + key(npc) + "): holds "
                + TaxExemptions.PERMISSION + "; logged once per character until restart");
        verify(logger, times(2)).info(startsWith("[TaxExempt] "));
    }

    @Test
    void anUnknownAccountNameFallsBackToItsIdAndNoPluginLogsNothing() {
        permitted.add(owner);
        members.when(() -> Members.accountName(anyString())).thenReturn(null);
        TaxExemptions.exempt(npc);
        verify(logger).info(startsWith("[TaxExempt] " + owner + " owes no citizen tax as Captain Avalon"));

        plugin.when(SimpleFactions::getInstance).thenReturn(null);
        assertTrue(TaxExemptions.exempt(own), "still exempt while the plugin is unavailable");
        verify(logger, never()).info(startsWith("[TaxExempt] " + owner + " owes no citizen tax as Captain Avalon (" + key(own)));
    }

    @Test
    void theOwnerMustBeOnlineWithThePermission() {
        TaxExemptions.setPermissionCheck(null);
        try (var bukkit = mockStatic(Bukkit.class)) {
            assertFalse(TaxExemptions.exempt(npc), "offline owner");
            Player player = mock(Player.class);
            bukkit.when(() -> Bukkit.getPlayer(owner)).thenReturn(player);
            assertFalse(TaxExemptions.exempt(npc), "online without the permission");
            when(player.hasPermission(TaxExemptions.PERMISSION)).thenReturn(true);
            assertTrue(TaxExemptions.exempt(npc));
        }
    }
}
