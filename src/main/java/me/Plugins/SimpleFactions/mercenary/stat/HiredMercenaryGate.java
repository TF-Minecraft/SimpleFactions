package me.Plugins.SimpleFactions.mercenary.stat;

import java.util.UUID;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryEngagements;

/** True while the player is on a live battle roster under an active contract. */
public final class HiredMercenaryGate implements MercenaryBattleGate {
    @Override
    public boolean isHiredInBattle(String player) {
        if (player == null) return false;
        UUID id = MercenaryEngagements.uuidLookup().uuidOf(player);
        if (id == null) return false;
        for (Battle battle : BattleManager.get()) {
            if (battle == null || !battle.hasStarted() || battle.getWarId() == null) continue;
            War war = WarManager.getById(battle.getWarId());
            if (war == null) continue;
            if (MercenaryEngagements.forPlayer(war, player) == null) continue;
            if (battle.getSideByMemberId(id) != null) return true;
        }
        return false;
    }
}
