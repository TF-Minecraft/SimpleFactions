package net.tfminecraft.simplefactions.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.player.income.PlayerLedger;
import net.tfminecraft.simplefactions.identity.Members;

public final class PlayerEconomyManager {
    private final Map<UUID, PlayerLedger> ledgers = new HashMap<>();

    public static PlayerEconomyManager get() {
        return SimpleFactions.getPlayerEconomyManager();
    }

    public PlayerLedger getLedger(UUID playerUuid) {
        if (playerUuid == null) {
            return new PlayerLedger();
        }
        return ledgers.computeIfAbsent(playerUuid, ignored -> new PlayerLedger());
    }

    /** Ledger of the character a member key names. */
    public PlayerLedger getLedger(String member) {
        UUID uuid = Members.characterId(member);
        if (uuid == null) {
            return new PlayerLedger();
        }
        return getLedger(uuid);
    }

    public void clearAllDaily() {
        for (PlayerLedger ledger : ledgers.values()) {
            ledger.clearDaily();
        }
        ledgers.clear();
    }
}
