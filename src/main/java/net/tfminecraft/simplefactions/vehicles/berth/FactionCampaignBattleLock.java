package net.tfminecraft.simplefactions.vehicles.berth;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.core.War;

public final class FactionCampaignBattleLock {
    public static final String BLOCKED =
            "§cYou cannot take or give faction vehicles while your faction is in a campaign battle.";

    private FactionCampaignBattleLock() {}

    /** True when the faction is in a war whose campaign battle, including a raid, has started. */
    public static boolean blocks(Faction faction) {
        if (faction == null || faction.getId() == null || faction.getId().isBlank()) {
            return false;
        }
        for (War war : WarManager.getActive()) {
            if (war == null || !participating(war, faction)) {
                continue;
            }
            for (Battle battle : BattleManager.getAllByWarId(war.getId())) {
                if (battle != null && battle.hasStarted() && battle.getWarId() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean participating(War war, Faction faction) {
        try {
            return war.isParticipating(faction);
        } catch (NullPointerException ignored) {
            return false;
        }
    }
}
