package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildType;
import net.tfminecraft.simplefactions.guild.branch.Branch;

class SupplyHubBranchesTest {
    private final Map<String, Branch> savedBranches = new HashMap<>(BranchLoader.map);
    private final Map<String, GuildType> savedTypes = new HashMap<>(GuildLoader.map);

    @AfterEach
    void restore() {
        BranchLoader.map.clear();
        BranchLoader.map.putAll(savedBranches);
        GuildLoader.map.clear();
        GuildLoader.map.putAll(savedTypes);
    }

    @Test
    void bundledBranchesLoadForGuildsAndRealms() {
        GuildLoader.map.clear();
        YamlConfiguration types = new YamlConfiguration();
        types.set("guild.name", "Guild");
        types.set("realm.name", "Realm");
        GuildLoader.map.put("guild", new GuildType("guild", types.getConfigurationSection("guild")));
        GuildLoader.map.put("realm", new GuildType("realm", types.getConfigurationSection("realm")));

        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/Guilds/branches.yml"));
        BranchLoader.map.clear();
        for (String key : config.getKeys(false)) {
            BranchLoader.map.put(key, new Branch(key, config.getConfigurationSection(key)));
        }
        Guild guildType = mock(Guild.class);
        when(guildType.getType()).thenReturn(GuildLoader.getByString("guild"));
        Guild realmType = mock(Guild.class);
        when(realmType.getType()).thenReturn(GuildLoader.getByString("realm"));

        Branch supplyGuild = BranchLoader.getByGroup(guildType, 3);
        Branch supplyRealm = BranchLoader.getByGroup(realmType, 3);
        Branch freightGuild = BranchLoader.getByGroup(guildType, 4);
        Branch freightRealm = BranchLoader.getByGroup(realmType, 4);

        assertNotNull(supplyGuild);
        assertNotNull(supplyRealm);
        assertNotNull(freightGuild);
        assertNotNull(freightRealm);
        assertEquals("supply_lines", supplyGuild.getId());
        assertEquals("freight_yards", freightRealm.getId());
        assertEquals(0.5, supplyGuild.getModifier(GuildModifier.HUB_LIMIT).getPerLevel());
        assertEquals(0.08, freightRealm.getModifier(GuildModifier.HUB_PRODUCTION).getPerLevel());
        assertTrue(!supplyGuild.getDescription().isEmpty());
    }
}
