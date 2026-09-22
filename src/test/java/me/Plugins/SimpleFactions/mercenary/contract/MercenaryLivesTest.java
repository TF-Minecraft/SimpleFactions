package me.Plugins.SimpleFactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.military.BattleLivesService;
import me.Plugins.SimpleFactions.War.battle.military.BattleLivesService.SideLivesPreview;
import me.Plugins.SimpleFactions.War.battle.military.BattlePoolService;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.core.War;

class MercenaryLivesTest {
    private static final int PROVINCE_ID = 42;
    private ContractFixture fixture;
    private Faction enemy;
    private UUID sigrun;

    @BeforeEach
    void setUp() {
        BattleManager.resetForTests();
        Cache.warBattleLivesPerRegiment = 5;
        Cache.warBattleMinSideLives = 1;
        fixture = ContractFixture.formed(2);
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        when(MercenaryLoyalty.hostFaction(fixture.company).getRelations())
                .thenReturn(new HashMap<>());
        enemy = ContractFixture.faction("lives_enemy");
        when(enemy.getRelations()).thenReturn(new HashMap<>());
        me.Plugins.SimpleFactions.Objects.Handler.GuildHandler empty =
                mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class);
        when(empty.getGuilds()).thenReturn(new java.util.ArrayList<>());
        when(enemy.getGuildHandler()).thenReturn(empty);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(enemy);
        fixture.company.kick("Soldier0");
        fixture.company.enlist("Sigrun");
        sigrun = UUID.randomUUID();
        MercenaryEngagements.setUuidLookup(name -> "Sigrun".equalsIgnoreCase(name) ? sigrun : null);
        MercenaryContract contract =
                fixture.offer(ContractFixture.validTerms(1), System.currentTimeMillis());
        assertTrue(contract.activate());
    }

    @AfterEach
    void tearDown() {
        MercenaryEngagements.setUuidLookup(null);
        BattleManager.resetForTests();
        ContractFixture.tearDown();
    }

    private War war() {
        return new War(1, fixture.hirer, enemy);
    }

    private Battle battle(int warId) {
        BossBar bar = mock(BossBar.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
                    .thenReturn(bar);
            bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
                    .thenReturn(bar);
            Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w" + warId);
            battle.setWarId(warId);
            battle.setProvinceId(PROVINCE_ID);
            return battle;
        }
    }

    @Test
    void filledAttendingSlotsAddLivesAndEmptyAddsNone() {
        War war = war();
        Battle withMerc = battle(1);
        Warband band = Warband.createWithMemberIds("atk", sigrun, true);
        withMerc.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(band);

        Battle empty = battle(1);

        try (MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
            pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
                    .thenReturn(2);
            pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getDefenders())))
                    .thenReturn(2);

            SideLivesPreview attending = BattleLivesService.previewCampaignSideLives(
                    war, withMerc, BattleTemplate.ATTACKER_SIDE);
            assertEquals(1, attending.mercenarySlots());
            assertEquals(3, attending.committedRegiments());
            assertEquals(15, attending.poolLives());
            assertEquals(1, attending.rosterFighters());
            assertEquals(14, attending.sideLives());

            SideLivesPreview vacant = BattleLivesService.previewCampaignSideLives(
                    war, empty, BattleTemplate.ATTACKER_SIDE);
            assertEquals(0, vacant.mercenarySlots());
            assertEquals(2, vacant.committedRegiments());
        }
    }

    @Test
    void aDualRolePlayerIsSubtractedOnceAndPreviewMatchesApply() {
        War war = war();
        Battle live = battle(1);
        Warband band = Warband.createWithMemberIds("atk", sigrun, true, sigrun);
        live.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(band);

        try (MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class);
                MockedStatic<WarManager> wars = mockStatic(WarManager.class);
                MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            BossBar bar = mock(BossBar.class);
            bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
                    .thenReturn(bar);
            bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
                    .thenReturn(bar);
            pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
                    .thenReturn(2);
            pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getDefenders())))
                    .thenReturn(2);
            wars.when(() -> WarManager.getById(1)).thenReturn(war);

            SideLivesPreview preview = BattleLivesService.previewCampaignSideLives(
                    war, live, BattleTemplate.ATTACKER_SIDE);
            assertEquals(1, preview.rosterFighters());
            BattleLivesService.applyCampaignLives(live);
            assertEquals(preview.sideLives(),
                    live.getSideById(BattleTemplate.ATTACKER_SIDE).getLives());
        }
    }
}
