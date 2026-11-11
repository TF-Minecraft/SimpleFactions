package net.tfminecraft.simplefactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.mercenary.company.CompanyFixture;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.military.BattleLivesService;
import net.tfminecraft.simplefactions.war.battle.military.BattleLivesService.SideLivesPreview;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.core.War;

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
        giveRegiments(fixture.hirer, 2);
        when(MercenaryLoyalty.hostFaction(fixture.company).getRelations())
                .thenReturn(new HashMap<>());
        enemy = ContractFixture.faction("lives_enemy");
        when(enemy.getRelations()).thenReturn(new HashMap<>());
        net.tfminecraft.simplefactions.objects.handler.GuildHandler empty =
                mock(net.tfminecraft.simplefactions.objects.handler.GuildHandler.class);
        when(empty.getGuilds()).thenReturn(new java.util.ArrayList<>());
        when(enemy.getGuildHandler()).thenReturn(empty);
        net.tfminecraft.simplefactions.managers.FactionManager.factions.add(enemy);
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

    /** Two paid offensive slots, so the hirer's side brings regiments of its own. */
    private static void giveRegiments(Faction faction, int slots) {
        Regiment regiment = CompanyFixture.regularPrototype("professional", slots, 1.0);
        when(regiment.getPaidSlots()).thenReturn(slots);
        Military military = mock(Military.class);
        when(military.getRegiments()).thenReturn(new ArrayList<>(List.of(regiment)));
        when(faction.getMilitary()).thenReturn(military);
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

        SideLivesPreview attending = BattleLivesService.previewCampaignSideLives(
                war, withMerc, BattleTemplate.ATTACKER_SIDE);
        assertEquals(1, attending.mercenarySlots());
        assertEquals(3, attending.slots());
        assertEquals(15, attending.poolLives());
        assertEquals(1, attending.rosterFighters());
        assertEquals(14, attending.sideLives());

        SideLivesPreview vacant = BattleLivesService.previewCampaignSideLives(
                war, empty, BattleTemplate.ATTACKER_SIDE);
        assertEquals(0, vacant.mercenarySlots());
        assertEquals(2, vacant.slots());
    }

    @Test
    void aDualRolePlayerIsSubtractedOnceAndPreviewMatchesApply() {
        War war = war();
        Battle live = battle(1);
        Warband band = Warband.createWithMemberIds("atk", sigrun, true, sigrun);
        live.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(band);

        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
                MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            BossBar bar = mock(BossBar.class);
            bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
                    .thenReturn(bar);
            bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
                    .thenReturn(bar);
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
