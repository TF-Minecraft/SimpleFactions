package net.tfminecraft.simplefactions.managers.inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubAgreementService;
import net.tfminecraft.simplefactions.guild.hub.HubAgreementService.RateRange;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;

/** Chat entry for a hub agreement's rate and fee. The chest stays closed until the number is typed. */
public final class HubTermsPrompt implements Listener {
    private static final long PROMPT_TIMEOUT_MS = 60_000L;

    private record Pending(
            String guildId,
            String hostId,
            String installationId,
            int rate,
            long feeCents,
            int baseRate,
            long baseFeeCents,
            boolean rateField,
            long askedAtMillis) {
    }

    private static final Map<UUID, Pending> WAITING = new ConcurrentHashMap<>();

    public HubTermsPrompt() {
    }

    public static void ask(
            Player player, Guild guild, String hostId, String installationId,
            int rate, long feeCents, int baseRate, long baseFeeCents, boolean rateField) {
        if (player == null || guild == null || guild.getId() == null || hostId == null || installationId == null) {
            return;
        }
        WAITING.put(player.getUniqueId(), new Pending(
                guild.getId(), hostId, installationId, rate, feeCents, baseRate, baseFeeCents, rateField,
                System.currentTimeMillis()));
        player.closeInventory();
        if (rateField) {
            Faction host = FactionManager.getByString(hostId);
            RateRange range = HubAgreementService.allowedRateRange(host);
            player.sendMessage("§eType the hub tax percent, from §f" + range.minPercent()
                    + "§e to §f" + range.maxPercent() + "§e. Type §ccancel§e to go back.");
            return;
        }
        player.sendMessage("§eType the daily fee in denars. Type §ccancel§e to go back.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer() != null) {
            WAITING.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() != null) {
            WAITING.remove(event.getPlayer().getUniqueId());
        }
    }

    // Retain Bukkit chat-event ordering and String message semantics for existing integrations.
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Pending pending = WAITING.get(playerId);
        if (pending == null) {
            return;
        }
        if (isExpired(pending.askedAtMillis, System.currentTimeMillis())) {
            WAITING.remove(playerId, pending);
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        if (SimpleFactions.plugin == null) {
            WAITING.remove(playerId, pending);
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!WAITING.remove(playerId, pending)) {
                    return;
                }
                apply(player, pending, message);
            }
        }.runTask(SimpleFactions.plugin);
    }

    private static void apply(Player player, Pending pending, String message) {
        if (player == null || !player.isOnline() || pending == null) {
            return;
        }
        String text = message == null ? "" : message.trim();
        if (text.equalsIgnoreCase("cancel")) {
            player.sendMessage("§7Left the terms as they were.");
            reopen(player, pending, pending.rate, pending.feeCents);
            return;
        }
        if (pending.rateField) {
            Integer typed = wholePercent(text);
            Faction host = FactionManager.getByString(pending.hostId);
            RateRange range = HubAgreementService.allowedRateRange(host);
            if (typed == null || typed < range.minPercent() || typed > range.maxPercent()) {
                WAITING.put(player.getUniqueId(), askedAgain(pending));
                player.sendMessage("§cType a whole percent from §e" + range.minPercent()
                        + "§c to §e" + range.maxPercent() + "§c, or §ecancel§c.");
                return;
            }
            reopen(player, pending, typed, pending.feeCents);
            return;
        }
        Long cents = wholeCents(text);
        long maxCents = Math.round(Math.max(0, Cache.supplyHubMaxFee) * 100.0);
        if (cents == null || cents > maxCents) {
            WAITING.put(player.getUniqueId(), askedAgain(pending));
            player.sendMessage("§cThe daily fee must be at least zero, in whole cents, and at most §e"
                    + Formatter.formatMoney(Cache.supplyHubMaxFee) + "§c. Type §ecancel§c to go back.");
            return;
        }
        reopen(player, pending, pending.rate, cents);
    }

    private static void reopen(Player player, Pending pending, int rate, long feeCents) {
        Guild guild = FactionManager.getGuildByString(pending.guildId);
        if (guild == null) {
            player.sendMessage("§cThat guild does not exist");
            return;
        }
        HubProposalMenu.openNegotiation(
                player, guild, pending.hostId, pending.installationId,
                rate, feeCents, pending.baseRate, pending.baseFeeCents);
    }

    private static Integer wholePercent(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long wholeCents(String text) {
        try {
            double fee = Double.parseDouble(text);
            if (Double.isNaN(fee) || Double.isInfinite(fee) || fee < 0) {
                return null;
            }
            double scaled = fee * 100.0;
            long cents = Math.round(scaled);
            if (Math.abs(scaled - cents) > 0.0001d) {
                return null;
            }
            return cents;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static boolean isExpired(long askedAtMillis, long nowMillis) {
        return nowMillis - askedAtMillis > PROMPT_TIMEOUT_MS;
    }

    private static Pending askedAgain(Pending pending) {
        return new Pending(pending.guildId, pending.hostId, pending.installationId,
                pending.rate, pending.feeCents, pending.baseRate, pending.baseFeeCents,
                pending.rateField, System.currentTimeMillis());
    }
}
