package net.tfminecraft.simplefactions.guild.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.diplomacy.DiplomacyHandler;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;

class InstallationAccessTest {
    private YamlConfiguration laws;

    @BeforeEach
    void setUp() {
        laws = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("laws.yml"), StandardCharsets.UTF_8));
    }

    @AfterEach
    void restore() {
        IncomePreviewContext.clear();
        InstallationAccess.endRecalculation();
    }

    @Test
    void sameFactionAndSameTopRealmHaveFullAccess() {
        Faction top = faction("top", "isolationism");
        Faction guild = faction("guild", "isolationism");
        Faction owner = faction("owner", "isolationism");
        when(guild.getOverlord()).thenReturn(top);
        when(owner.getOverlord()).thenReturn(top);

        assertEquals(1, InstallationAccess.of(top, top));
        assertEquals(1, InstallationAccess.of(guild, owner));
        assertSame(top, InstallationAccess.topRealm(guild));
    }

    @Test
    void embargoInEitherDirectionBlocksAccess() {
        Faction guild = faction("guild", "free_trade");
        Faction owner = faction("owner", "free_trade");
        RelationType embargo = relation("embargo");

        trade(guild, owner, embargo);
        assertEquals(0, InstallationAccess.of(guild, owner));
        trade(guild, owner, null);
        trade(owner, guild, embargo);
        assertEquals(0, InstallationAccess.of(guild, owner));
    }

    @Test
    void hostileWarBlocksAccess() {
        Faction guild = faction("guild", "free_trade");
        Faction owner = faction("owner", "free_trade");
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(() -> WarManager.existsHostile(guild, owner)).thenReturn(true);
            assertEquals(0, InstallationAccess.of(guild, owner));
        }
    }

    @Test
    void agreementsUseTheGuildRealmsDirection() {
        Faction guild = faction("guild", "isolationism");
        Faction owner = faction("owner", "isolationism");

        trade(guild, owner, relation("trade_agreement"));
        assertEquals(1, InstallationAccess.of(guild, owner));
        trade(guild, owner, relation("unequal_treaty_leader"));
        assertEquals(1, InstallationAccess.of(guild, owner));
        trade(guild, owner, relation("unequal_treaty_subject"));
        assertEquals(0.5, InstallationAccess.of(guild, owner));
    }

    @Test
    void lawPairsMatchTheMatrix() {
        assertLaw(0.60, "free_trade", "free_trade");
        assertLaw(0.70, "free_trade", "mercantilism");
        assertLaw(0.25, "mercantilism", "free_trade");
        assertLaw(0, "isolationism", "mercantilism");
    }

    @Test
    void agreementAndLawUseWhicheverIsStronger() {
        Faction guild = faction("guild", "free_trade");
        Faction owner = faction("owner", "free_trade");
        trade(guild, owner, relation("unequal_treaty_subject"));
        assertEquals(0.60, InstallationAccess.of(guild, owner), 1e-9);

        guild = faction("closed-guild", "isolationism");
        owner = faction("closed-owner", "isolationism");
        trade(guild, owner, relation("unequal_treaty_subject"));
        assertEquals(0.50, InstallationAccess.of(guild, owner), 1e-9);
    }

    @Test
    void previewRelationOpensAccessWithoutWritingIt() {
        Faction guild = faction("guild", "isolationism");
        Faction owner = faction("owner", "isolationism");
        RelationType agreement = relation("trade_agreement");

        assertEquals(0, InstallationAccess.of(guild, owner));
        IncomePreviewContext.open(IncomePreviewContext.trade(guild, owner, agreement));
        assertEquals(1, InstallationAccess.of(guild, owner));
        IncomePreviewContext.clear();
        assertEquals(0, InstallationAccess.of(guild, owner));
        assertTrue(guild.getDiplomacyHandler().getTradeRelations().isEmpty());
    }

    @Test
    void lawPreviewChangesGrantAndReach() {
        Faction owner = faction("owner", "isolationism");
        Faction guild = faction("guild", "protectionism");
        LawGroup ownerGroup = owner.getLawHandler().getGroup("economy");
        LawGroup guildGroup = guild.getLawHandler().getGroup("economy");

        IncomePreviewContext.open(IncomePreviewContext.law(
                owner, ownerGroup, ownerGroup.getLaw("free_trade")));
        assertEquals(0.50, InstallationAccess.of(guild, owner), 1e-9);
        IncomePreviewContext.clear();

        ownerGroup.setCurrent(ownerGroup.getLaw("protectionism"));
        IncomePreviewContext.open(IncomePreviewContext.law(
                guild, guildGroup, guildGroup.getLaw("mercantilism")));
        assertEquals(0.30, InstallationAccess.of(guild, owner), 1e-9);
    }

    @Test
    void bundledAndFallbackLawValuesMatch() {
        assertTable(group());
        for (Law law : group().getLaws().values()) {
            assertTrue(law.getScopedEffects().get(Scope.FOREIGN_GUILDS)
                    .getModifierAmount(FactionModifiers.INSTALLATION_ACCESS, Region.OUR_TERRITORY) != null);
            assertTrue(law.getScopedEffects().get(Scope.DOMESTIC_GUILDS)
                    .getModifierAmount(FactionModifiers.INSTALLATION_ACCESS, Region.FOREIGN_TERRITORY) != null);
        }
        for (String id : laws.getConfigurationSection("economy.laws").getKeys(false)) {
            laws.set("economy.laws." + id + ".effects.foreign_guilds.our_territory", List.of("trade_power(5)"));
            laws.set("economy.laws." + id + ".effects.domestic_guilds.foreign_territory", List.of("trade_power(5)"));
        }
        assertTable(group());
    }

    @Test
    void legacyModifierNameStillLoads() {
        laws.set("economy.laws.free_trade.effects.foreign_guilds.our_territory",
                List.of("infrastructure_access(0.42)"));
        assertEquals(0.42, grant(group().getLaw("free_trade")), 1e-9);
    }

    @Test
    void relationDefaultsApplyOnlyWhenKeysAreMissing() {
        assertEquals(1, relation("trade_agreement").getInstallationAccess());
        assertEquals(1, relation("unequal_treaty_leader").getInstallationAccess());
        assertEquals(0.5, relation("unequal_treaty_subject").getInstallationAccess());
        assertTrue(relation("embargo").blocksInstallations());

        YamlConfiguration config = new YamlConfiguration();
        config.set("installation-access", 0.25);
        config.set("blocks-installations", false);
        RelationType explicit = new RelationType("embargo", config);
        assertEquals(0.25, explicit.getInstallationAccess());
        assertEquals(false, explicit.blocksInstallations());
    }

    @Test
    void cyclicOverlordChainsDoNotGrantAccess() {
        Faction first = faction("first", "isolationism");
        Faction second = faction("second", "isolationism");
        when(first.getOverlord()).thenReturn(second);
        when(second.getOverlord()).thenReturn(first);

        assertSame(first, InstallationAccess.topRealm(first));
        assertEquals(0, InstallationAccess.of(first, second));
    }

    private void assertLaw(double expected, String hostLaw, String guestLaw) {
        assertEquals(expected, InstallationAccess.of(
                faction("guest-" + guestLaw, guestLaw), faction("host-" + hostLaw, hostLaw)), 1e-9);
    }

    private Faction faction(String id, String law) {
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn(id);
        LawHandler handler = mock(LawHandler.class);
        when(faction.getLawHandler()).thenReturn(handler);
        LawGroup group = group();
        group.setCurrent(group.getLaw(law));
        when(handler.getGroup("economy")).thenReturn(group);
        when(faction.getDiplomacyHandler()).thenReturn(new DiplomacyHandler(faction));
        return faction;
    }

    private RelationType relation(String id) {
        YamlConfiguration diplomacy = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("diplomacy.yml"), StandardCharsets.UTF_8));
        return new RelationType(id, diplomacy.getConfigurationSection("types." + id));
    }

    private static void trade(Faction from, Faction to, RelationType relation) {
        if (relation == null) {
            from.getDiplomacyHandler().removeTradeRelation(to.getId());
        } else {
            from.getDiplomacyHandler().setTradeRelation(to, relation);
        }
    }

    private LawGroup group() {
        return new LawGroup("economy", laws.getConfigurationSection("economy"));
    }

    private static double grant(Law law) {
        return InstallationAccess.amount(law, Scope.FOREIGN_GUILDS, Region.OUR_TERRITORY);
    }

    private static double reach(Law law) {
        return InstallationAccess.amount(law, Scope.DOMESTIC_GUILDS, Region.FOREIGN_TERRITORY);
    }

    private static void assertTable(LawGroup group) {
        for (Map.Entry<String, double[]> entry : Map.of(
                "free_trade", new double[] {0.50, 0.10},
                "decentralized", new double[] {0.35, 0.00},
                "mercantilism", new double[] {0.15, 0.20},
                "protectionism", new double[] {0.10, 0.00},
                "isolationism", new double[] {0.00, -0.25}).entrySet()) {
            assertEquals(entry.getValue()[0], grant(group.getLaw(entry.getKey())), 1e-9, entry.getKey());
            assertEquals(entry.getValue()[1], reach(group.getLaw(entry.getKey())), 1e-9, entry.getKey());
        }
    }
}
