package net.tfminecraft.simplefactions.map.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;

class InfrastructureAccessTest {
    private YamlConfiguration config;

    @BeforeEach
    void setUp() {
        config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("laws.yml"), StandardCharsets.UTF_8));
    }

    @AfterEach
    void restore() {
        IncomePreviewContext.clear();
    }

    @Test
    void foreignAccessMatchesTheDesignExamples() {
        Faction free = faction("free", "free_trade");
        Faction mercantilist = faction("merchant", "mercantilism");
        Faction protectionist = faction("protected", "protectionism");

        assertEquals(1, InfrastructureAccess.forGuild(mercantilist, free), 1e-9);
        assertEquals(0.70, InfrastructureAccess.forGuild(protectionist, free), 1e-9);
        assertEquals(0.40, InfrastructureAccess.forGuild(free, protectionist), 1e-9);
    }

    @Test
    void siblingsUnderTheSameTopOverlordHaveFullAccess() {
        Faction top = faction("top", "isolationism");
        Faction middle = faction("middle", "isolationism");
        Faction guild = faction("guild", "isolationism");
        Faction owner = faction("owner", "isolationism");
        when(middle.getOverlord()).thenReturn(top);
        when(guild.getOverlord()).thenReturn(middle);
        when(owner.getOverlord()).thenReturn(top);

        assertEquals(1, InfrastructureAccess.forGuild(guild, owner));
        assertEquals(1, InfrastructureAccess.forGuild(owner, guild));
        assertEquals(1, InfrastructureAccess.forGuild(top, guild));
    }

    @Test
    void unownedLandHasFullAccess() {
        assertEquals(1, InfrastructureAccess.forGuild(faction("guild", "isolationism"), null));
    }

    @Test
    void explicitZeroDoesNotFallThroughToTheTable() {
        config.set("economy.laws.free_trade.effects.foreign_guilds.our_territory",
                List.of("infrastructure_access(0.00)"));
        config.set("economy.laws.free_trade.effects.domestic_guilds.foreign_territory",
                List.of("infrastructure_access(0.00)"));
        Law law = group().getLaw("free_trade");

        assertEquals(0, grant(law));
        assertEquals(0, reach(law));
        assertTrue(law.affectsEconomy());
    }

    @Test
    void allMissingLinesFallBackToTheTable() {
        for (String id : config.getConfigurationSection("economy.laws").getKeys(false)) {
            config.set("economy.laws." + id + ".effects.foreign_guilds.our_territory", List.of("trade_power(5)"));
            config.set("economy.laws." + id + ".effects.domestic_guilds.foreign_territory", List.of("trade_power(5)"));
        }
        assertTable(group());
    }

    @Test
    void bundledLawLinesMatchTheTable() {
        assertTable(group());
        for (Law law : group().getLaws().values()) {
            assertTrue(law.getScopedEffects().get(Scope.FOREIGN_GUILDS)
                    .getModifierAmount(FactionModifiers.INFRASTRUCTURE_ACCESS, Region.OUR_TERRITORY) != null);
            assertTrue(law.getScopedEffects().get(Scope.DOMESTIC_GUILDS)
                    .getModifierAmount(FactionModifiers.INFRASTRUCTURE_ACCESS, Region.FOREIGN_TERRITORY) != null);
        }
    }

    @Test
    void everyMatchingModifierIsSummedForItsScopeAndRegion() {
        config.set("economy.laws.free_trade.effects.foreign_guilds.modifiers",
                List.of("infrastructure_access(0.10)"));
        config.set("economy.laws.free_trade.effects.foreign_guilds.our_territory",
                List.of("infrastructure_access(0.20)", "infrastructure_access(0.30)", "trade_power(50)"));
        config.set("economy.laws.free_trade.effects.foreign_guilds.foreign_territory",
                List.of("infrastructure_access(0.90)"));

        assertEquals(0.60, grant(group().getLaw("free_trade")), 1e-9);
    }

    @Test
    void unknownEconomyLawHasNoFallbackAccess() {
        Law law = new Law("economy", "unknown", new YamlConfiguration());
        assertEquals(0, grant(law));
        assertEquals(0, reach(law));
    }

    @Test
    void foreignAccessIsClampedAtZero() {
        assertEquals(0, InfrastructureAccess.forGuild(
                faction("guild", "isolationism"), faction("owner", "protectionism")));
    }

    @Test
    void previewsChangeBothGrantAndReachWithoutChangingLiveLaws() {
        Faction owner = faction("owner", "isolationism");
        Faction guild = faction("guild", "protectionism");
        LawGroup ownerGroup = owner.getLawHandler().getGroup("economy");
        LawGroup guildGroup = guild.getLawHandler().getGroup("economy");
        Law old = ownerGroup.getCurrent();

        IncomePreviewContext.open(IncomePreviewContext.law(owner, ownerGroup, ownerGroup.getLaw("free_trade")));
        assertEquals(0.70, InfrastructureAccess.forGuild(guild, owner), 1e-9);
        IncomePreviewContext.clear();
        assertSame(old, ownerGroup.getCurrent());
        assertEquals(0, InfrastructureAccess.forGuild(guild, owner));

        ownerGroup.setCurrent(ownerGroup.getLaw("protectionism"));
        IncomePreviewContext.open(IncomePreviewContext.law(guild, guildGroup, guildGroup.getLaw("free_trade")));
        assertEquals(0.40, InfrastructureAccess.forGuild(guild, owner), 1e-9);
        IncomePreviewContext.clear();
        assertEquals("protectionism", guildGroup.getCurrent().getId());
        assertEquals(0.10, InfrastructureAccess.forGuild(guild, owner), 1e-9);
    }

    @Test
    void cyclicOverlordChainsAreIgnored() {
        Faction first = faction("first", "isolationism");
        Faction second = faction("second", "isolationism");
        when(first.getOverlord()).thenReturn(second);
        when(second.getOverlord()).thenReturn(first);

        assertSame(first, InfrastructureAccess.topRealm(first));
        assertEquals(0, InfrastructureAccess.forGuild(first, second));
    }

    private Faction faction(String id, String law) {
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn(id);
        LawHandler handler = mock(LawHandler.class);
        when(faction.getLawHandler()).thenReturn(handler);
        LawGroup group = group();
        group.setCurrent(group.getLaw(law));
        when(handler.getGroup("economy")).thenReturn(group);
        return faction;
    }

    private LawGroup group() {
        return new LawGroup("economy", config.getConfigurationSection("economy"));
    }

    private static double grant(Law law) {
        return InfrastructureAccess.amount(law, Scope.FOREIGN_GUILDS, Region.OUR_TERRITORY);
    }

    private static double reach(Law law) {
        return InfrastructureAccess.amount(law, Scope.DOMESTIC_GUILDS, Region.FOREIGN_TERRITORY);
    }

    private static void assertTable(LawGroup group) {
        for (Map.Entry<String, double[]> entry : Map.of(
                "free_trade", new double[] {0.80, 0.20},
                "mercantilism", new double[] {0.50, 0.20},
                "decentralized", new double[] {0.50, 0.00},
                "protectionism", new double[] {0.20, -0.10},
                "isolationism", new double[] {0.00, -0.30}).entrySet()) {
            assertEquals(entry.getValue()[0], grant(group.getLaw(entry.getKey())), 1e-9, entry.getKey());
            assertEquals(entry.getValue()[1], reach(group.getLaw(entry.getKey())), 1e-9, entry.getKey());
        }
    }
}
