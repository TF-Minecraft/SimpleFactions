package net.tfminecraft.simplefactions.managers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionStartEvent;

class PlayerManagerCharacterStartTest {
    @Test
    void playingACharacterCountsAsItsLogin() {
        Player player = mock(Player.class);
        CharacterSession session = new CharacterSession(new CharacterRef(UUID.randomUUID(), UUID.randomUUID()), 1);
        try (var inactivity = mockStatic(InactivityService.class)) {
            new PlayerManager().characterStart(new CharacterSessionStartEvent(player, session));
            inactivity.verify(() -> InactivityService.onLogin(player));
        }
    }
}
