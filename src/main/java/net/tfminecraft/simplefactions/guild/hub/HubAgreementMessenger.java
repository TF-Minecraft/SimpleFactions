package net.tfminecraft.simplefactions.guild.hub;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Sends agreement notices to players who are online. Offline players are skipped. */
final class HubAgreementMessenger {
    private HubAgreementMessenger() {
    }

    static void deliver(List<HubAgreementService.HubNotice> notices) {
        if (notices == null || notices.isEmpty()) {
            return;
        }
        for (HubAgreementService.HubNotice notice : notices) {
            Player player = online(notice.playerName());
            if (player != null && notice.message() != null) {
                player.sendMessage(notice.message());
            }
        }
    }

    private static Player online(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            if (Bukkit.getServer() == null) {
                return null;
            }
            return Bukkit.getPlayerExact(name);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
