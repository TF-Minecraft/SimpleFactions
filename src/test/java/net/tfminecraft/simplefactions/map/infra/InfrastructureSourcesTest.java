package net.tfminecraft.simplefactions.map.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

class InfrastructureSourcesTest {
    private final double station = Cache.infrastructureStation;
    private final double port = Cache.infrastructurePort;
    private final double airport = Cache.infrastructureAirport;

    @AfterEach
    void restore() {
        GuildModifierOverride.clear();
        Cache.infrastructureStation = station;
        Cache.infrastructurePort = port;
        Cache.infrastructureAirport = airport;
    }

    @Test
    void realmCapitalUsesTheResolvedModifierOnce() {
        Guild guild = guild(true, 1, 12.5);
        Map<Integer, Double> sources = InfrastructureSources.collect(
                Map.of(1, province(1, "forest")), List.of(guild), List.of());

        assertEquals(12.5, sources.get(1));
        verify(guild, times(1)).getModifier(GuildModifier.INFRASTRUCTURE);
        assertTrue(GuildModifier.INFRASTRUCTURE.isPositive());
        assertEquals(GuildModifier.PRODUCTION.scalesWithInactivity(), GuildModifier.INFRASTRUCTURE.scalesWithInactivity());
    }

    @Test
    void capitalUsesPreviewOverrideInsteadOfTheLiveModifier() {
        Guild guild = guild(true, 1, 12.5);
        GuildModifierOverride.use(guild, Map.of(GuildModifier.INFRASTRUCTURE, 17.0));
        Map<Integer, Double> sources = InfrastructureSources.collect(
                Map.of(1, province(1, "forest")), List.of(guild), List.of());

        assertEquals(17, sources.get(1));
        verify(guild, never()).getModifier(GuildModifier.INFRASTRUCTURE);
    }

    @Test
    void nonBaseGuildsAndGuildsWithoutCapitalsAddNothing() {
        Guild regular = guild(false, 1, 12.5);
        Guild noCapital = guild(true, 1, 12.5);
        when(noCapital.hasCapital()).thenReturn(false);

        assertTrue(InfrastructureSources.collect(Map.of(1, province(1, "forest")),
                List.of(regular, noCapital), List.of()).isEmpty());
        verify(regular, never()).getModifier(GuildModifier.INFRASTRUCTURE);
        verify(noCapital, never()).getModifier(GuildModifier.INFRASTRUCTURE);
    }

    @Test
    void installationsUseConfigAmountsWithoutScalingByLevelAndFortsAddNothing() {
        Cache.infrastructureStation = 7;
        Cache.infrastructurePort = 11;
        Cache.infrastructureAirport = 3;
        Installation station = installation(InstallationKind.TRAIN_STATION, 1);
        Installation port = installation(InstallationKind.PORT, 2);
        Installation airport = installation(InstallationKind.AIRPORT, 3);
        Installation fort = installation(InstallationKind.FORT, 4);
        Map<Integer, Double> sources = InfrastructureSources.collect(Map.of(
                1, province(1, "forest"), 2, province(2, "forest"),
                3, province(3, "forest"), 4, province(4, "forest")), List.of(),
                List.of(faction(station, port, airport, fort)));

        assertEquals(Map.of(1, 7.0, 2, 11.0, 3, 3.0), sources);
        verify(station, never()).getLevel();
        verify(port, never()).getLevel();
        verify(airport, never()).getLevel();
    }

    @Test
    void sourcesInOneProvinceAddBeforeTheySpread() {
        Cache.infrastructureStation = 10;
        Guild guild = guild(true, 1, 10);
        Map<Integer, Double> sources = InfrastructureSources.collect(Map.of(
                1, province(1, "forest"), 2, province(2, "plains")), List.of(guild),
                List.of(faction(installation(InstallationKind.TRAIN_STATION, 1))));
        Map<Integer, InfrastructureSpread.Arrival> result = InfrastructureSpread.spread(Map.of(
                1, new InfrastructureSpread.Node(0.6, true, "realm", List.of(2)),
                2, new InfrastructureSpread.Node(0.75, true, "realm", List.of(1))), sources, 0.25, 0.5);

        assertEquals(20, sources.get(1));
        assertEquals(15, result.get(2).amount());
    }

    @Test
    void sourcesOutsideTheManagerAndOnSeaOrWaterAreIgnored() {
        Map<Integer, Double> sources = InfrastructureSources.collect(Map.of(
                1, province(1, "sea"), 2, province(2, "water")),
                List.of(guild(true, 1, 10), guild(true, 2, 10), guild(true, 3, 10)),
                List.of(faction(installation(InstallationKind.PORT, 1),
                        installation(InstallationKind.TRAIN_STATION, 2),
                        installation(InstallationKind.AIRPORT, 3))));

        assertTrue(sources.isEmpty());
    }

    private static Province province(int id, String terrain) {
        return new Province(id, terrain, 0);
    }

    private static Guild guild(boolean base, int capital, double amount) {
        Guild guild = mock(Guild.class);
        when(guild.isBase()).thenReturn(base);
        when(guild.hasCapital()).thenReturn(true);
        when(guild.getCapital()).thenReturn(capital);
        when(guild.getModifier(GuildModifier.INFRASTRUCTURE)).thenReturn(amount);
        return guild;
    }

    private static Installation installation(InstallationKind kind, int province) {
        Installation installation = mock(Installation.class);
        when(installation.getKind()).thenReturn(kind);
        when(installation.getProvince()).thenReturn(province);
        when(installation.getLevel()).thenReturn(5);
        return installation;
    }

    private static Faction faction(Installation... installations) {
        Faction faction = mock(Faction.class);
        InstallationHandler handler = mock(InstallationHandler.class);
        when(faction.getInstallationHandler()).thenReturn(handler);
        when(handler.getAll()).thenReturn(List.of(installations));
        return faction;
    }
}
