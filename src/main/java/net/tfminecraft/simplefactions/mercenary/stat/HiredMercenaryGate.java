package net.tfminecraft.simplefactions.mercenary.stat;

import java.util.UUID;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;

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
