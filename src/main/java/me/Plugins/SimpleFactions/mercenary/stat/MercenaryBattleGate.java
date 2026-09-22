package me.Plugins.SimpleFactions.mercenary.stat;

/**
 * Answers whether a player is currently fighting in a battle as a hired
 * mercenary. Contracts arrive in Phase 4; until then the production gate is
 * always closed, so no company buff can reach a player.
 */
public interface MercenaryBattleGate {
    MercenaryBattleGate CLOSED = player -> false;

    boolean isHiredInBattle(String player);
}
