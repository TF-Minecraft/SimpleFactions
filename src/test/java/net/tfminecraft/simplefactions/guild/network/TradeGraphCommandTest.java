package net.tfminecraft.simplefactions.guild.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.ProvinceData;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.Site;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.utils.Permissions;

class TradeGraphCommandTest {
    private boolean provincesWereEnabled;

    @BeforeEach
    void enableProvinces() {
        provincesWereEnabled = Cache.provincesEnabled;
        Cache.provincesEnabled = true;
        TradeGraph.setLiveForTests(null);
    }

    @AfterEach
    void restore() {
        TradeGraph.setLiveForTests(null);
        Cache.provincesEnabled = provincesWereEnabled;
    }

    @Test
    void aSenderWithoutPermissionIsRefused() {
        CommandSender sender = mock(CommandSender.class);

        assertTrue(TradeGraphCommand.handle(sender, new String[] {"tradegraph"}));

        verify(sender).sendMessage("§a[SimpleFactions]§c You do not have access to this command");
        verify(sender, times(1)).sendMessage(anyString());
    }

    @Test
    void staffOnTheConsoleSeeEachNetworkNodeAndEdge() {
        CommandSender sender = admin();
        TradeGraph.setLiveForTests(sample());

        assertTrue(TradeGraphCommand.handle(sender, new String[] {"tradegraph"}));

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(sender, times(5)).sendMessage(messages.capture());
        assertEquals(List.of(
                "§6Trade graph. §e2§7 installations, §e1§7 connection.",
                "§ea §7alpha, port",
                "§7  b, sea, 500 blocks, 2 provinces",
                "§eb §7beta, port",
                "§7  a, sea, 500 blocks, 2 provinces"), messages.getAllValues());
    }

    @Test
    void extraWordsShowTheUsageAndAnEmptyGraphSaysSo() {
        CommandSender sender = admin();

        assertTrue(TradeGraphCommand.handle(sender, new String[] {"tradegraph", "more"}));
        verify(sender).sendMessage("§cUsage: §e/faction tradegraph");

        assertTrue(TradeGraphCommand.handle(sender, new String[] {"tradegraph"}));
        verify(sender).sendMessage("§6Trade graph. §e0§7 installations, §e0§7 connections.");
    }

    @Test
    void theCommandWaitsUntilProvincesAreEnabled() {
        CommandSender sender = admin();
        Cache.provincesEnabled = false;
        TradeGraph.setLiveForTests(sample());

        assertTrue(TradeGraphCommand.handle(sender, new String[] {"tradegraph"}));

        verify(sender).sendMessage(Cache.PROVINCES_DISABLED_MESSAGE);
        verify(sender, times(1)).sendMessage(anyString());
    }

    private static CommandSender admin() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
        return sender;
    }

    private static TradeGraph sample() {
        Map<Integer, ProvinceData> provinces = new HashMap<>();
        provinces.put(1, new ProvinceData(Terrain.PLAINS, java.util.Set.of(2)));
        provinces.put(2, new ProvinceData(Terrain.SEA, java.util.Set.of(1, 3)));
        provinces.put(3, new ProvinceData(Terrain.SEA, java.util.Set.of(2, 4)));
        provinces.put(4, new ProvinceData(Terrain.PLAINS, java.util.Set.of(3)));
        return TradeGraphBuilder.build(List.of(
                new Site("alpha", new Installation("a", "a", InstallationKind.PORT, 1, 0, 0, 1L), true),
                new Site("beta", new Installation("b", "b", InstallationKind.PORT, 4, 300, 400, 1L), true)),
                provinces, (from, to) -> Optional.empty(), point -> 0);
    }
}
