package me.Plugins.SimpleFactions.mercenary.stat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.mercenary.contract.ContractFixture;
import me.Plugins.SimpleFactions.mercenary.contract.ContractKind;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryEngagements;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryLoyalty;

class HiredMercenaryGateTest {
    private ContractFixture fixture;
    private UUID sigrun;

    @BeforeEach
    void setUp() {
        BattleManager.resetForTests();
        fixture = ContractFixture.formed(2);
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        when(MercenaryLoyalty.hostFaction(fixture.company).getRelations())
                .thenReturn(new HashMap<>());
        fixture.company.kick("Soldier0");
        fixture.company.enlist("Sigrun");
        sigrun = UUID.randomUUID();
        MercenaryEngagements.setUuidLookup(name -> "Sigrun".equalsIgnoreCase(name) ? sigrun : null);
        MercenaryContract contract = new MercenaryContract(
                fixture.company, fixture.hirer, ContractKind.MERCENARY,
                ContractFixture.validTerms(1), System.currentTimeMillis());
        fixture.company.getContractHandler().add(contract);
        assertTrue(contract.activate());
    }

    @AfterEach
    void tearDown() {
        MercenaryEngagements.setUuidLookup(null);
        BattleManager.resetForTests();
        ContractFixture.tearDown();
    }

    @Test
    void theGateOpensOnlyForARosteredContractedPlayer() {
        Faction enemy = ContractFixture.faction("foe");
        when(enemy.getRelations()).thenReturn(new HashMap<>());
        me.Plugins.SimpleFactions.Objects.Handler.GuildHandler empty =
                mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class);
        when(empty.getGuilds()).thenReturn(new java.util.ArrayList<>());
        when(enemy.getGuildHandler()).thenReturn(empty);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(enemy);
        War war = new War(8, fixture.hirer, enemy);

        Battle battle = mock(Battle.class);
        when(battle.hasStarted()).thenReturn(true);
        when(battle.getWarId()).thenReturn(8);
        BattleSide side = mock(BattleSide.class);
        when(battle.getSideByMemberId(sigrun)).thenReturn(side);
        BattleManager.addBattle(battle);

        HiredMercenaryGate gate = new HiredMercenaryGate();
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(() -> WarManager.getById(8)).thenReturn(war);
            assertTrue(gate.isHiredInBattle("Sigrun"));
            assertFalse(gate.isHiredInBattle("Stranger"));
        }

        when(battle.getSideByMemberId(sigrun)).thenReturn(null);
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(() -> WarManager.getById(8)).thenReturn(war);
            assertFalse(gate.isHiredInBattle("Sigrun"));
        }
    }
}
