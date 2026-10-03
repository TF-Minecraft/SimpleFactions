package net.tfminecraft.simplefactions.database;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.LeaderCharacters;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.map.MapSystem;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;

class DatabaseLeaderCharacterTest {

    private MapSystem previousMap;
    private MapSystem map;
    private Faction faction;
    private Guild guild;

    @BeforeEach
    void setUp() {
        previousMap = FactionManager.map;
        map = mock(MapSystem.class);
        FactionManager.map = map;
        faction = mock(Faction.class);
        guild = mock(Guild.class);
        GuildHandler guilds = mock(GuildHandler.class);
        when(faction.getGuildHandler()).thenReturn(guilds);
        when(guilds.getGuilds()).thenReturn(List.of(guild));
        when(faction.getLeader()).thenReturn("rushork");
        when(faction.getLeaderCharacter()).thenReturn("Grunk the Bold");
        when(faction.getLeaderCharacterOf()).thenReturn("rushork");
        when(guild.getLeader()).thenReturn("hazel");
        when(guild.getLeaderCharacter()).thenReturn("Hazel Stonebrook");
        when(guild.getLeaderCharacterOf()).thenReturn("hazel");
    }

    @AfterEach
    void tearDown() {
        FactionManager.map = previousMap;
        LeaderCharacters.reset();
    }

    private static void online(Map<String, String> active) {
        LeaderCharacters.setProbe(player -> active.get(player));
    }

    @Test
    void aSaveThatLearnsANewRealmLeaderNameMarksTheMap() {
        online(Map.of("rushork", "Grunk the Second", "hazel", "Hazel Stonebrook"));
        Database.rememberLeaderCharacters(faction);
        verify(faction).rememberLeaderCharacter("Grunk the Second", "rushork");
        verify(map).markLeaderNamesChanged();
    }

    @Test
    void aSaveThatLearnsANewGuildLeaderNameMarksTheMap() {
        online(Map.of("rushork", "Grunk the Bold", "hazel", "Hazel Ironbrook"));
        Database.rememberLeaderCharacters(faction);
        verify(guild).rememberLeaderCharacter("Hazel Ironbrook", "hazel");
        verify(map).markLeaderNamesChanged();
    }

    @Test
    void aSaveThatLearnsNothingNewLeavesTheMapAlone() {
        online(Map.of());
        Database.rememberLeaderCharacters(faction);
        verify(map, never()).markLeaderNamesChanged();
    }
}
