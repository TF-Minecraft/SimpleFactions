package net.tfminecraft.simplefactions.mercenary.company;

import net.tfminecraft.simplefactions.identity.Members;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.objects.request.MercenaryInviteRequest;
import net.tfminecraft.simplefactions.objects.request.Request;
import net.tfminecraft.simplefactions.mercenary.MercenaryResult;

/** Chat side of enlistment: invites, acceptance, and refusal. */
public final class MercenaryInvites {
    private MercenaryInvites() {
    }

    public static void invite(Player leader, String targetName) {
        Guild guild = net.tfminecraft.simplefactions.managers.FactionManager
                .getGuildByLeader(Members.key(leader));
        // Tells the leader when nobody online matches the name.
        Player target = Members.resolveOnline(leader, targetName);
        if (target == null) {
            return;
        }
        String targetKey = Members.key(target);
        MercenaryResult check = MercenaryCompanyService
                .canInvite(guild, Members.key(leader), targetKey);
        if (!check.ok()) {
            leader.sendMessage("§c" + check.message());
            return;
        }
        if (targetKey == null) {
            leader.sendMessage("§c" + target.getName() + " has no character to enlist.");
            return;
        }
        MercenaryCompany company = guild.getCompany();
        MercenaryInviteRequest invitation = new MercenaryInviteRequest(company);
        RequestManager.addRequest(leader, target, invitation);
        if (RequestManager.getRequest(target) != invitation) {
            return;
        }
        leader.sendMessage("§aInvited " + Members.name(target) + " to " + company.getName() + ".");
        target.sendMessage("§a" + Members.name(leader) + " offers you a slot in "
                + company.getName() + ".");
        target.sendMessage("§aType /company accept to sign on, or /company decline to refuse.");
    }

    public static void accept(Player player) {
        MercenaryInviteRequest request = pending(player);
        if (request == null) {
            player.sendMessage("§cYou have no company invite to accept.");
            return;
        }
        MercenaryCompany company = request.getCompany();
        if (company.getGuild().getCompany() != company) {
            RequestManager.remove(player);
            player.sendMessage("§cThat company invite has expired.");
            return;
        }
        MercenaryResult result = MercenaryCompanyService.join(company, Members.key(player));
        player.sendMessage((result.ok() ? "§a" : "§c") + result.message());
        RequestManager.remove(player);
        if (!result.ok()) {
            return;
        }
        Members.online(request.getCompany().getLeader()).ifPresent(leaderPlayer ->
                leaderPlayer.sendMessage("§a" + Members.name(player) + " signed on with the company."));
    }

    public static void decline(Player player) {
        MercenaryInviteRequest request = pending(player);
        if (request == null) {
            player.sendMessage("§cYou have no company invite to decline.");
            return;
        }
        RequestManager.remove(player);
        player.sendMessage("§aYou turned down " + request.getCompany().getName() + ".");
        Members.online(request.getCompany().getLeader()).ifPresent(leaderPlayer ->
                leaderPlayer.sendMessage("§c" + Members.name(player) + " turned down the company."));
    }

    private static MercenaryInviteRequest pending(Player player) {
        Request request = RequestManager.getRequest(player);
        return request instanceof MercenaryInviteRequest invite ? invite : null;
    }
}
