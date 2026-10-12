package net.tfminecraft.simplefactions.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.testsupport.SharedIdentity;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;

class SharedLeaderCharactersTest {
    @AfterEach
    void reset() {
        LeaderCharacters.reset();
    }

    @Test
    void leaderKeyNamesItsOwnCharacterEvenWhileOffline() {
        try (var identity = new SharedIdentity()) {
            LeaderCharacters.useSharedIdentity();
            UUID account = identity.account("rushork");
            CharacterRef grunk = identity.character(account, "§6Grunk the Bold");
            identity.character(account, "Another character of the same player");
            String key = Members.key(grunk);

            assertEquals(new LeaderCharacters.Remembered("Grunk the Bold", key),
                    LeaderCharacters.resolve(key, null, null));
            verify(identity.provider, never()).lastPlayed(any());
            verify(identity.provider, never()).displayName(any());
        }
    }

    @Test
    void deadLeaderStillShowsTheirName() {
        try (var identity = new SharedIdentity()) {
            LeaderCharacters.useSharedIdentity();
            CharacterRef fallen = identity.character(identity.account("rushork"), "Grunk", CharacterStatus.DEAD);
            assertEquals("Grunk", LeaderCharacters.resolve(Members.key(fallen), null, null).name());
        }
    }

    @Test
    void unknownCharacterKeepsOnlyItsOwnRememberedName() {
        try (var identity = new SharedIdentity()) {
            LeaderCharacters.useSharedIdentity();
            String key = Members.key(UUID.randomUUID());
            assertEquals(LeaderCharacters.Remembered.NONE, LeaderCharacters.resolve(key, null, null));
            assertEquals(new LeaderCharacters.Remembered("Remembered", key),
                    LeaderCharacters.resolve(key, "Remembered", key));
            assertEquals(LeaderCharacters.Remembered.NONE,
                    LeaderCharacters.resolve(key, "Another ruler", Members.key(UUID.randomUUID())));
        }
    }

    @Test
    void providerLossForgetsNamesUntilItReturns() {
        try (var identity = new SharedIdentity()) {
            LeaderCharacters.useSharedIdentity();
            String key = Members.key(identity.character(identity.account("rushork"), "Grunk"));
            identity.unregister();
            assertEquals(LeaderCharacters.Remembered.NONE, LeaderCharacters.resolve(key, "Grunk", key));
            identity.register();
            assertEquals("Grunk", LeaderCharacters.resolve(key, null, null).name());
        }
    }
}
