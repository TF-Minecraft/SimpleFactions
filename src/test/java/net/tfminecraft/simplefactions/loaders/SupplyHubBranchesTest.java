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

        Branch supplyGuild = BranchLoader.getByGroup(guildType, 3);
        Branch supplyRealm = BranchLoader.getByGroup(realmType, 3);
        Branch freightGuild = BranchLoader.getByGroup(guildType, 4);
        Branch freightRealm = BranchLoader.getByGroup(realmType, 4);

        assertNotNull(supplyGuild);
        assertNull(supplyRealm);
        assertNotNull(freightGuild);
        assertNotNull(freightRealm);
        assertEquals("supply_lines", supplyGuild.getId());
        assertNull(BranchLoader.getByString("infrastructure"));
        assertEquals("counting_houses", freightRealm.getId());
        assertEquals(2.0, supplyGuild.getModifier(GuildModifier.HUB_LIMIT).getBase());
        assertEquals(0.5, supplyGuild.getModifier(GuildModifier.HUB_LIMIT).getPerLevel());
        assertEquals(0.05, supplyGuild.getModifier(GuildModifier.HUB_TRADE).getPerLevel());
        assertEquals(0.08, supplyGuild.getModifier(GuildModifier.HUB_PRODUCTION).getPerLevel());
        assertEquals(-0.01, freightRealm.getModifier(GuildModifier.TRADE_UPKEEP).getPerLevel());
        assertEquals(1.0, supplyGuild.getModifier(GuildModifier.HUB_UPKEEP).getBase());
        assertEquals(0.5, supplyGuild.getModifier(GuildModifier.HUB_UPKEEP).getPerLevel());
        assertNotNull(BranchLoader.getByString("bureaucracy"));
        Branch storehouses = new Branch(BranchLoader.getByString("storehouses"), 3);
        Branch supplyLines = new Branch(BranchLoader.getByString("supply_lines"), 4);
        assertEquals(0.70, storehouses.getAmount(GuildModifier.TRADE_CARRY));
        assertEquals(0.09, storehouses.getAmount(GuildModifier.HUB_PRODUCTION));
        assertNull(storehouses.getModifier(GuildModifier.HUB_UPKEEP));
        assertEquals(1.0, supplyGuild.getAmount(GuildModifier.HUB_UPKEEP));
        assertEquals(3.0, supplyLines.getAmount(GuildModifier.HUB_UPKEEP));
        Branch twoStorehouses = new Branch(BranchLoader.getByString("storehouses"), 2);
        Branch fiveSupplyLines = new Branch(BranchLoader.getByString("supply_lines"), 5);
        assertEquals(0.46, twoStorehouses.getAmount(GuildModifier.HUB_PRODUCTION)
                + fiveSupplyLines.getAmount(GuildModifier.HUB_PRODUCTION));
        assertTrue(!supplyGuild.getDescription().isEmpty());
    }

    @Test
    void savedSupplyLinesStayWhenTheRealmHasNoBranchInThatGroup() {
        loadBundledBranches();
        Branch supplyLines = new Branch(BranchLoader.getByString("supply_lines"), 3);
        Map<Integer, Branch> realmBranches = new HashMap<>(Map.of(3, supplyLines));
        Map<Integer, Branch> normalBranches = new HashMap<>(Map.of(3, supplyLines));
        BranchLoader.replaceDisallowedBranches(realmBranches, GuildLoader.getByString("realm"));
        BranchLoader.replaceDisallowedBranches(normalBranches, GuildLoader.getByString("guild"));

        assertEquals("supply_lines", realmBranches.get(3).getId());
        assertEquals(3, realmBranches.get(3).getLevel());
        assertEquals("supply_lines", normalBranches.get(3).getId());
        assertEquals(3, normalBranches.get(3).getLevel());

        YamlConfiguration legacyConfig = new YamlConfiguration();
        legacyConfig.set("name", "Supply Lines");
        legacyConfig.set("group", 3);
        legacyConfig.set("allowed-types", java.util.List.of("guild", "realm"));
        BranchLoader.map.put("supply_lines", new Branch("supply_lines", legacyConfig));
        Map<Integer, Branch> legacyRealmBranches = new HashMap<>(Map.of(3,
                new Branch(BranchLoader.getByString("supply_lines"), 0)));
        BranchLoader.replaceDisallowedBranches(legacyRealmBranches, GuildLoader.getByString("realm"));
        assertEquals("supply_lines", legacyRealmBranches.get(3).getId());
        assertEquals(0, legacyRealmBranches.get(3).getLevel());
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
