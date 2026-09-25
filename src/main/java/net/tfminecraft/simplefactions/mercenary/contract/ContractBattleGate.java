package net.tfminecraft.simplefactions.mercenary.contract;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * Whether a slot change may be accepted. A fight that has already started uses
 * the promised slot count for its join cap and lives, so the count stays put
 * until that battle is gone.
 */
public final class ContractBattleGate {
    @FunctionalInterface
    public interface Fighting {
        boolean hirerIsFighting(Faction hirer);
    }

    private static Fighting fighting = ContractBattleGate::fromLiveBattles;

    private ContractBattleGate() {
    }

    /** Tests install a stub. Null restores the live battle list. */
    public static void setFighting(Fighting next) {
        fighting = next == null ? ContractBattleGate::fromLiveBattles : next;
    }

    public static boolean hirerIsFighting(Faction hirer) {
        return hirer != null && fighting.hirerIsFighting(hirer);
    }

    private static boolean fromLiveBattles(Faction hirer) {
        for (Battle battle : BattleManager.get()) {
            if (battle == null || !battle.hasStarted()) continue;
            Integer warId = battle.getWarId();
            if (warId == null) continue;
            War war = WarManager.getById(warId);
            if (war != null && war.isParticipating(hirer)) return true;
        }
        return false;
    }
}
