package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class GuildUnresolvedLeaderTest {
    @Test
    void aQuarantinedLeaderSeatIsUnresolved() {
        Guild guild = mock(Guild.class);
        when(guild.hasUnresolvedLeader()).thenCallRealMethod();
        when(guild.getLeader()).thenReturn("quarantine:q1a2b3c4d5e6f");
        assertTrue(guild.hasUnresolvedLeader());
        when(guild.getLeader()).thenReturn("6e846e46-699e-4131-9e9a-3e68a6dc4b08");
        assertFalse(guild.hasUnresolvedLeader());
        when(guild.getLeader()).thenReturn(null);
        assertFalse(guild.hasUnresolvedLeader());
    }
}
