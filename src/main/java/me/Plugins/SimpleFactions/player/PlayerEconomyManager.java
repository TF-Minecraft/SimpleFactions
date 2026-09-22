package me.Plugins.SimpleFactions.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.player.income.PlayerLedger;

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

    public PlayerLedger getLedger(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return new PlayerLedger();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = offline.getUniqueId();
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
