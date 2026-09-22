package net.tfminecraft.simplefactions.mercenary.contract;

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

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.military.BattlePoolService;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.government.Government;

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
        net.tfminecraft.simplefactions.managers.FactionManager.factions.add(enemy);
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
        net.tfminecraft.simplefactions.objects.handler.GuildHandler empty =
                mock(net.tfminecraft.simplefactions.objects.handler.GuildHandler.class);
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

    // Exercise the retained compatibility entry point so legacy behavior stays covered.
    @SuppressWarnings("deprecation")
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

    // Exercise the retained compatibility entry point so legacy behavior stays covered.
    @SuppressWarnings("deprecation")
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

        net.tfminecraft.simplefactions.guild.Guild enemyGuild = mock(net.tfminecraft.simplefactions.guild.Guild.class);
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

    // Exercise the retained compatibility entry point so legacy behavior stays covered.
    @SuppressWarnings("deprecation")
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

    // Exercise the retained compatibility entry point so legacy behavior stays covered.
    @SuppressWarnings("deprecation")
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
