package me.Plugins.SimpleFactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleJoinService;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.military.BattlePoolService;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.government.Government;

class MercenaryRosterTest {
    private static final int PROVINCE_ID = 20;
    private ContractFixture fixture;
    private Faction enemy;
    private UUID sigrunId;

    @BeforeEach
    void setUp() {
        BattleManager.resetForTests();
        Cache.warBattleLivesPerRegiment = 5;
        Cache.warBattleMinSideLives = 1;
        fixture = ContractFixture.formed(2);
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        when(MercenaryLoyalty.hostFaction(fixture.company).getRelations())
                .thenReturn(new HashMap<>());
        enemy = ContractFixture.faction("enemy_realm");
        when(enemy.getRelations()).thenReturn(new HashMap<>());
        emptyHandler(enemy);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(enemy);
        fixture.company.kick("Soldier0");
        fixture.company.enlist("Sigrun");
        sigrunId = UUID.randomUUID();
        MercenaryEngagements.setUuidLookup(name -> "Sigrun".equalsIgnoreCase(name) ? sigrunId : null);
    }

    @AfterEach
    void tearDown() {
        MercenaryEngagements.setUuidLookup(null);
        ContractFixture.tearDown();
        BattleManager.resetForTests();
    }

    private static void emptyHandler(Faction f) {
        me.Plugins.SimpleFactions.Objects.Handler.GuildHandler empty =
                mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class);
        when(empty.getGuilds()).thenReturn(new java.util.ArrayList<>());
        when(f.getGuildHandler()).thenReturn(empty);
    }

    private MercenaryContract hire() {
        MercenaryContract contract =
                fixture.offer(ContractFixture.validTerms(1), System.currentTimeMillis());
        assertTrue(contract.activate());
        return contract;
    }

    private Battle campaignBattle(int warId) {
        BossBar bossBar = mock(BossBar.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
                    .thenReturn(bossBar);
            bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
                    .thenReturn(bossBar);
            Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w" + warId);
            battle.setWarId(warId);
            battle.setProvinceId(PROVINCE_ID);
            battle.setLocked(false);
            return battle;
        }
    }

    @Test
    void aMercenaryJoinsTheContractedSideAgainstTheirOwnFaction() {
        hire();
        War war = new War(1, fixture.hirer, enemy);
        Battle battle = campaignBattle(1);
        Warband attackers = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
        battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(attackers);
        war.setScheduledBattleProvinceId(PROVINCE_ID);

        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class, org.mockito.Mockito.CALLS_REAL_METHODS);
                MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
            factions.when(() -> FactionManager.getByMember("Sigrun")).thenReturn(enemy);
            pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
                    .thenReturn(5);

            assertNull(CampaignBattleJoinService.validateWarbandMemberJoin(
                    war, battle, BattleTemplate.ATTACKER_SIDE, attackers, "Sigrun", sigrunId));
            assertEquals("You are under contract to the other host",
                    CampaignBattleJoinService.validateWarbandMemberJoin(
                            war, battle, BattleTemplate.DEFENDER_SIDE,
                            Warband.createCampaignSideShell(war, war.getDefenders(), BattleTemplate.DEFENDER_SIDE),
                            "Sigrun", sigrunId));
        }
    }

    @Test
    void aCouncilMemberIsRefusedAndACitizenIsAllowed() {
        MercenaryContract contract =
                fixture.offer(ContractFixture.validTerms(2), System.currentTimeMillis());
        assertTrue(contract.activate());
        fixture.company.enlist("Chancellor");
        Government gov = mock(Government.class);
        when(gov.isCouncilMember("Chancellor")).thenReturn(true);
        when(gov.isCouncilMember("Sigrun")).thenReturn(false);
        when(enemy.getGovernment()).thenReturn(gov);
        when(fixture.host.guild.isMember("Chancellor")).thenReturn(false);

        War war = new War(1, fixture.hirer, enemy);
        Battle battle = campaignBattle(1);
        Warband attackers = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
        battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(attackers);
        war.setScheduledBattleProvinceId(PROVINCE_ID);

        me.Plugins.SimpleFactions.Guild.Guild enemyGuild = mock(me.Plugins.SimpleFactions.Guild.Guild.class);
        when(enemyGuild.getFaction()).thenReturn(enemy);
        when(enemyGuild.isMember("Chancellor")).thenReturn(true);
        when(enemyGuild.isMember("Sigrun")).thenReturn(true);

        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class, org.mockito.Mockito.CALLS_REAL_METHODS);
                MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
            factions.when(() -> FactionManager.getByMember("Sigrun")).thenReturn(enemy);
            factions.when(() -> FactionManager.getByMember("Chancellor")).thenReturn(enemy);
            factions.when(() -> FactionManager.getGuildByMember("Chancellor")).thenReturn(enemyGuild);
            factions.when(() -> FactionManager.getGuildByMember("Sigrun")).thenReturn(enemyGuild);
            pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
                    .thenReturn(5);

        fixture.company.enlist("Chancellor");
        MercenaryEngagements.setUuidLookup(name -> "Sigrun".equalsIgnoreCase(name) ? sigrunId
                : "Chancellor".equalsIgnoreCase(name) ? UUID.randomUUID() : null);
            assertEquals("You cannot march on your own realm",
                    CampaignBattleJoinService.validateWarbandMemberJoin(
                            war, battle, BattleTemplate.ATTACKER_SIDE, attackers, "Chancellor", UUID.randomUUID()));
            assertNull(CampaignBattleJoinService.validateWarbandMemberJoin(
                    war, battle, BattleTemplate.ATTACKER_SIDE, attackers, "Sigrun", sigrunId));
        }
    }

    @Test
    void theSlotCapRefusesTheSurplusPlayer() {
        hire();
        War war = new War(1, fixture.hirer, enemy);
        Battle battle = campaignBattle(1);
        Warband attackers = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
        attackers.addMember(sigrunId);
        battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(attackers);
        war.setScheduledBattleProvinceId(PROVINCE_ID);

        UUID extra = UUID.randomUUID();
        fixture.company.enlist("Bjorn");
        MercenaryEngagements.setUuidLookup(name -> "Sigrun".equalsIgnoreCase(name) ? sigrunId
                : "Bjorn".equalsIgnoreCase(name) ? extra : null);

        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class, org.mockito.Mockito.CALLS_REAL_METHODS);
                MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
            factions.when(() -> FactionManager.getByMember("Bjorn")).thenReturn(fixture.hirer);
            pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
                    .thenReturn(5);

            assertEquals("Every hired slot is already covered",
                    CampaignBattleJoinService.validateWarbandMemberJoin(
                            war, battle, BattleTemplate.ATTACKER_SIDE, attackers, "Bjorn", extra));
        }
    }

    @Test
    void aNonMercenaryIsStillGovernedByFactionSide() {
        War war = new War(1, fixture.hirer, enemy);
        Battle battle = campaignBattle(1);
        Warband attackers = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);

        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            factions.when(() -> FactionManager.getByMember("Carol")).thenReturn(enemy);
            assertEquals("Your faction is not on this battle side",
                    CampaignBattleJoinService.validateWarbandMemberJoin(
                            war, battle, BattleTemplate.ATTACKER_SIDE, attackers, "Carol", UUID.randomUUID()));
        }
    }
}
