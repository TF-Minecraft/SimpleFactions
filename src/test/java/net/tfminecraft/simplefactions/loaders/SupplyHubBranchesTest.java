package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import net.tfminecraft.simplefactions.database.GuildBranchData;

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
        types.set("realm.base", true);
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

        Branch countingGuild = BranchLoader.getByGroup(guildType, 3);
        Branch countingRealm = BranchLoader.getByGroup(realmType, 3);
        Branch guildAfterCounting = BranchLoader.getByGroup(guildType, 4);
        Branch realmAfterCounting = BranchLoader.getByGroup(realmType, 4);

        assertNotNull(countingGuild);
        assertNotNull(countingRealm);
        assertNull(guildAfterCounting);
        assertNull(realmAfterCounting);
        assertNull(BranchLoader.getByString("supply_lines"));
        assertNull(BranchLoader.getByString("infrastructure"));
        assertEquals("counting_houses", countingGuild.getId());
        assertEquals("counting_houses", countingRealm.getId());
        assertEquals(-0.01, countingRealm.getModifier(GuildModifier.TRADE_UPKEEP).getPerLevel());
        assertNotNull(BranchLoader.getByString("bureaucracy"));
        Branch storehouses = new Branch(BranchLoader.getByString("storehouses"), 3);
        assertEquals(1.00, storehouses.getAmount(GuildModifier.TRADE_CARRY));
        assertTrue(!countingGuild.getDescription().isEmpty());
    }

    @Test
    void aDisallowedBranchIsReplacedByTheAllowedOneAtThatGroup() {
        loadBundledBranches();
        YamlConfiguration realmOnly = new YamlConfiguration();
        realmOnly.set("name", "Realm Only");
        realmOnly.set("group", 3);
        realmOnly.set("allowed-types", java.util.List.of("realm"));
        BranchLoader.map.put("realm_only", new Branch("realm_only", realmOnly));
        Map<Integer, Branch> normalBranches = new HashMap<>(Map.of(3,
                new Branch(BranchLoader.getByString("realm_only"), 2)));
        BranchLoader.replaceDisallowedBranches(normalBranches, GuildLoader.getByString("guild"));

        assertEquals("counting_houses", normalBranches.get(3).getId());
        assertEquals(2, normalBranches.get(3).getLevel());
    }

    private static void loadBundledBranches() {
        YamlConfiguration types = new YamlConfiguration();
        types.set("guild.name", "Guild");
        types.set("realm.name", "Realm");
        types.set("realm.base", true);
        GuildLoader.map.clear();
        GuildLoader.map.put("guild", new GuildType("guild", types.getConfigurationSection("guild")));
        GuildLoader.map.put("realm", new GuildType("realm", types.getConfigurationSection("realm")));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/Guilds/branches.yml"));
        BranchLoader.map.clear();
        for (String key : config.getKeys(false)) {
            BranchLoader.map.put(key, new Branch(key, config.getConfigurationSection(key)));
        }
    }

    @Test
    void freightYardsResolvesToCountingHousesOnlyWhenNoExactBranchExists() {
        Branch counting = new Branch("counting_houses", new YamlConfiguration().createSection("counting_houses"));
        BranchLoader.map.clear();
        BranchLoader.map.put(counting.getId(), counting);
        assertEquals(counting, BranchLoader.getByString("freight_yards"));
        Branch migrated = new Branch(BranchLoader.getByString("freight_yards"), 3);
        assertEquals("counting_houses", migrated.getId());
        assertEquals(3, migrated.getLevel());
        GuildBranchData saved = new GuildBranchData();
        saved.id = migrated.getId();
        saved.level = migrated.getLevel();
        assertEquals("counting_houses", saved.id);
        assertEquals(3, saved.level);

        Branch legacy = new Branch("freight_yards", new YamlConfiguration().createSection("freight_yards"));
        BranchLoader.map.put(legacy.getId(), legacy);
        assertEquals(legacy, BranchLoader.getByString("freight_yards"));
    }
}
