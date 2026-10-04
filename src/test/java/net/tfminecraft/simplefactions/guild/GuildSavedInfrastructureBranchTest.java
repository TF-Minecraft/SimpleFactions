package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.loaders.BranchLoader;
import net.tfminecraft.simplefactions.loaders.GuildLoader;
import net.tfminecraft.simplefactions.loaders.UpgradeLoader;
import net.tfminecraft.simplefactions.objects.Faction;

class GuildSavedInfrastructureBranchTest {
    private final Map<String, Branch> savedBranches = new HashMap<>(BranchLoader.map);
    private final Map<String, GuildType> savedTypes = new HashMap<>(GuildLoader.map);
    private final Map<String, Upgrade> savedUpgrades = new LinkedHashMap<>(UpgradeLoader.map);

    @AfterEach
    void restore() {
        BranchLoader.map.clear();
        BranchLoader.map.putAll(savedBranches);
        GuildLoader.map.clear();
        GuildLoader.map.putAll(savedTypes);
        UpgradeLoader.map.clear();
        UpgradeLoader.map.putAll(savedUpgrades);
    }

    @Test
    void aSavedInfrastructureBranchIsSkipped() {
        loadBundledBranches();
        GuildData data = JsonUtil.GSON.fromJson("""
                {
                  "id": "realm-guild",
                  "name": "The Realm",
                  "leader": "Ada",
                  "type": "realm",
                  "capital": 4,
                  "banner": ["white"],
                  "wealth modifiers": [],
                  "branches": [
                    {"id": "infrastructure", "level": 4},
                    {"id": "bureaucracy", "level": 2}
                  ]
                }
                """, GuildData.class);
        World world = mock(World.class);
        when(world.getChunkAt(0, 0)).thenReturn(mock(Chunk.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedConstruction<ItemStack> items = mockConstruction(ItemStack.class, (mock, context) ->
                        when(mock.getItemMeta()).thenReturn(mock(BannerMeta.class)))) {
            bukkit.when(() -> Bukkit.getWorld(nullable(String.class))).thenReturn(world);
            bukkit.when(Bukkit::getLogger).thenReturn(mock(Logger.class));

            Guild guild = new Guild(data, mock(Faction.class));

            assertEquals("realm-guild", guild.getId());
            assertNull(guild.getBranch("infrastructure"));
            Branch bureaucracy = guild.getBranch("bureaucracy");
            assertNotNull(bureaucracy);
            assertEquals(2, bureaucracy.getLevel());
            assertEquals(1, items.constructed().size());
        }
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
        UpgradeLoader.map.clear();
        for (String key : config.getKeys(false)) {
            BranchLoader.map.put(key, new Branch(key, config.getConfigurationSection(key)));
        }
    }
}
