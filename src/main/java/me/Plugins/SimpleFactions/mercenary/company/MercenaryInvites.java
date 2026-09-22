package me.Plugins.SimpleFactions.mercenary.company;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.RequestManager;
import me.Plugins.SimpleFactions.Objects.Request.MercenaryInviteRequest;
import me.Plugins.SimpleFactions.Objects.Request.Request;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;

/** Chat side of enlistment: invites, acceptance, and refusal. */
public final class MercenaryInvites {
    private MercenaryInvites() {
    }

    public static void invite(Player leader, String targetName) {
        Guild guild = me.Plugins.SimpleFactions.Managers.FactionManager
                .getGuildByLeader(leader.getName());
        MercenaryResult check = MercenaryCompanyService
                .canInvite(guild, leader.getName(), targetName);
        if (!check.ok()) {
            leader.sendMessage("§c" + check.message());
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            leader.sendMessage("§cCould not find the player " + targetName);
            return;
        }
        MercenaryCompany company = guild.getCompany();
        RequestManager.addRequest(leader, target, new MercenaryInviteRequest(company));
        if (!(RequestManager.getRequest(target) instanceof MercenaryInviteRequest)) {
            return;
        }
        leader.sendMessage("§aInvited " + target.getName() + " to " + company.getName() + ".");
        target.sendMessage("§a" + leader.getName() + " offers you a slot in "
                + company.getName() + ".");
        target.sendMessage("§aType /company accept to sign on, or /company decline to refuse.");
    }

    public static void accept(Player player) {
        MercenaryInviteRequest request = pending(player);
        if (request == null) {
            player.sendMessage("§cYou have no company invite to accept.");
            return;
        }
        MercenaryResult result = MercenaryCompanyService.join(request.getCompany(), player.getName());
        player.sendMessage((result.ok() ? "§a" : "§c") + result.message());
        RequestManager.remove(player);
        if (!result.ok()) {
            return;
        }
        String leader = request.getCompany().getLeader();
        Player leaderPlayer = leader == null ? null : Bukkit.getPlayerExact(leader);
        if (leaderPlayer != null) {
            leaderPlayer.sendMessage("§a" + player.getName() + " signed on with the company.");
        }
    }

    public static void decline(Player player) {
        MercenaryInviteRequest request = pending(player);
        if (request == null) {
            player.sendMessage("§cYou have no company invite to decline.");
            return;
        }
        RequestManager.remove(player);
        player.sendMessage("§aYou turned down " + request.getCompany().getName() + ".");
        String leader = request.getCompany().getLeader();
        Player leaderPlayer = leader == null ? null : Bukkit.getPlayerExact(leader);
        if (leaderPlayer != null) {
            leaderPlayer.sendMessage("§c" + player.getName() + " turned down the company.");
        }
    }

    private static MercenaryInviteRequest pending(Player player) {
        Request request = RequestManager.getRequest(player);
        return request instanceof MercenaryInviteRequest invite ? invite : null;
    }
}
