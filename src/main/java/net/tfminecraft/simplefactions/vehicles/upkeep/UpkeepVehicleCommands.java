package net.tfminecraft.simplefactions.vehicles.upkeep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands;
import net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands.VehicleTabCompletions;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleFindMessages;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferMessages;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepClickSessions.Mode;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepClickSessions.Session;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepHolder.Kind;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepRequests.Awaiting;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepRequests.Pending;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepVehicleService.AddResult;
import net.tfminecraft.vehicleframework.VehicleFramework;

/**
 * {@code /faction vehicle transfer upkeep}, {@code /faction vehicle upkeep ...} and the same
 * under {@code /guild vehicle}. Faction commands act for the player's faction, guild commands
 * for the player guild they are in or lead.
 */
public final class UpkeepVehicleCommands {
    private UpkeepVehicleCommands() {}

    /** Every {@code /guild vehicle} command, and the upkeep ones under {@code /faction vehicle}. */
    public static boolean handles(String command, String[] args) {
        if (command == null || args == null || args.length < 1 || !args[0].equalsIgnoreCase("vehicle")) return false;
        if (command.equalsIgnoreCase(Kind.GUILD.command())) return true;
        if (!command.equalsIgnoreCase(Kind.FACTION.command()) || args.length < 2) return false;
        return args[1].equalsIgnoreCase("upkeep") || isTransferUpkeep(args);
    }

    private static boolean isTransferUpkeep(String[] args) {
        return args.length >= 3 && args[1].equalsIgnoreCase("transfer") && args[2].equalsIgnoreCase("upkeep");
    }

    public static boolean handle(Player player, String command, String[] args) {
        Kind kind = command.equalsIgnoreCase(Kind.GUILD.command()) ? Kind.GUILD : Kind.FACTION;
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (isTransferUpkeep(args)) {
            armAdd(player, kind);
        } else if (sub.equals("disable") || sub.equals("cancel")) {
            VehicleFactionCommands.disarm(player);
        } else if (!sub.equals("upkeep")) {
            player.sendMessage(UpkeepVehicleMessages.usage(kind));
        } else {
            String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "list";
            String rest = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
            switch (action) {
                case "list", "info" -> info(player, kind);
                case "request" -> armRequest(player, kind);
                case "approve" -> decide(player, rest, true);
                case "deny" -> decide(player, rest, false);
                case "give" -> armGive(player, kind, rest);
                default -> player.sendMessage(UpkeepVehicleMessages.usage(kind));
            }
        }
        return true;
    }

    static UpkeepVehicleService service() {
        return SimpleFactions.getInstance().getUpkeepVehicleService();
    }

    private static long expiry() {
        return UpkeepClickSessions.now() + InstallationConfigLoader.getTransferRequestTimeoutSeconds() * 1000L;
    }

    /** Ends the player's other vehicle click modes before arming an upkeep one. */
    private static void arm(Player player, Session session) {
        VehicleFactionCommands.clearFactionClickModes(player);
        net.tfminecraft.simplefactions.vehicles.battle.BattleForcedVehicles.disarm(player.getUniqueId());
        UpkeepClickSessions.arm(player.getUniqueId(), session);
    }

    static void armAdd(Player player, Kind kind) {
        UpkeepHolder holder = UpkeepHolder.ledBy(Members.key(player), kind);
        if (holder == null) {
            player.sendMessage(UpkeepVehicleMessages.notLeader(kind));
            return;
        }
        arm(player, new Session(Mode.ADD, holder, null, expiry()));
        player.sendMessage(UpkeepVehicleMessages.addArmed(holder));
    }

    static void armRequest(Player player, Kind kind) {
        String key = Members.key(player);
        UpkeepHolder holder = UpkeepHolder.memberOf(key, kind);
        if (holder == null) {
            player.sendMessage(UpkeepVehicleMessages.notMember(kind));
            return;
        }
        if (holder.isLeader(key)) {
            // A leader needs nobody's approval for their own vehicle.
            armAdd(player, kind);
            return;
        }
        arm(player, new Session(Mode.REQUEST, holder, null, expiry()));
        player.sendMessage(UpkeepVehicleMessages.requestArmed(holder));
    }

    static void armGive(Player player, Kind kind, String member) {
        UpkeepHolder holder = UpkeepHolder.ledBy(Members.key(player), kind);
        if (holder == null) {
            player.sendMessage(UpkeepVehicleMessages.notLeader(kind));
            return;
        }
        if (member == null || member.isBlank()) {
            player.sendMessage(UpkeepVehicleMessages.usage(kind));
            return;
        }
        String recipient = Members.find(holder.members(), member);
        if (recipient == null) {
            player.sendMessage(UpkeepVehicleMessages.notAMemberNamed(holder, member));
            return;
        }
        arm(player, new Session(Mode.GIVE, holder, recipient, expiry()));
        player.sendMessage(UpkeepVehicleMessages.giveArmed(holder, Members.display(recipient)));
    }

    static void info(Player player, Kind kind) {
        String key = Members.key(player);
        UpkeepHolder holder = UpkeepHolder.memberOf(key, kind);
        if (holder == null) {
            player.sendMessage(UpkeepVehicleMessages.notMember(kind));
            return;
        }
        SimpleFactions plugin = SimpleFactions.getInstance();
        var registry = plugin.getUpkeepVehicleService().registry();
        List<PlayerVehicleRecord> vehicles = holder.vehicles(registry);
        player.sendMessage(UpkeepVehicleMessages.header(holder, vehicles.size()));
        if (vehicles.size() > holder.cap()) player.sendMessage(UpkeepVehicleMessages.overCap());
        player.sendMessage(UpkeepVehicleMessages.daily(holder, UpkeepVehicleService.dailyUpkeep(registry, holder)));
        if (vehicles.isEmpty()) player.sendMessage(UpkeepVehicleMessages.none());
        for (PlayerVehicleRecord record : vehicles) {
            String location = VehicleFindMessages.formatLocation(
                    VehicleFramework.getVehicleManager().getOfflineLocation(record.getVehicleUuid()));
            player.sendMessage(UpkeepVehicleMessages.line(record.getVehicleTypeId(),
                    Pending.shortId(record.getVehicleUuid()), location,
                    plugin.getVehicleMaintenanceStore().isUnpaid(record.getVehicleUuid())));
        }
        boolean leader = holder.isLeader(key);
        List<Pending> pending = new ArrayList<>();
        UUID character = VehicleOwnershipQueries.characterId(player);
        for (Pending each : UpkeepRequests.forHolder(holder)) {
            if (leader || each.owner().characterId().equals(character)) pending.add(each);
        }
        if (!pending.isEmpty()) player.sendMessage(UpkeepVehicleMessages.pendingHeader());
        for (Pending each : pending) {
            player.sendMessage(UpkeepVehicleMessages.pendingLine(each, Members.display(Members.key(each.owner()))));
        }
        if (leader) player.sendMessage(UpkeepVehicleMessages.leaderHelp(holder.kind()));
    }

    /** Approve or deny a pending request: by the side it waits for, or (deny only) by the other side. */
    static void decide(Player player, String id, boolean approve) {
        Pending pending = UpkeepRequests.find(id);
        if (pending == null) {
            player.sendMessage(UpkeepVehicleMessages.noPending());
            return;
        }
        UpkeepHolder holder = pending.holder();
        boolean leader = holder.isLeader(Members.key(player));
        boolean owner = pending.owner().characterId().equals(VehicleOwnershipQueries.characterId(player));
        boolean awaited = pending.awaiting() == Awaiting.LEADER ? leader : owner;
        if (!approve) {
            if (!leader && !owner) {
                player.sendMessage(UpkeepVehicleMessages.notWaitingForYou());
                return;
            }
            UpkeepRequests.remove(pending.vehicle());
            player.sendMessage(UpkeepVehicleMessages.youDeclined());
            notifyOther(pending, leader, UpkeepVehicleMessages.declined(Members.name(player), pending.typeId()));
            return;
        }
        if (!awaited) {
            player.sendMessage(UpkeepVehicleMessages.notWaitingForYou());
            return;
        }
        UpkeepRequests.remove(pending.vehicle());
        if (pending.awaiting() == Awaiting.OWNER && !holder.isLeader(pending.leaderKey())) {
            player.sendMessage(VehicleTransferMessages.consentExpired());
            return;
        }
        UpkeepTarget target = UpkeepTarget.read(pending.vehicle());
        if (target != null && !OwnerTags.names(target.owner(), pending.owner().characterId())) {
            both(pending, UpkeepVehicleMessages.ownerChanged());
            return;
        }
        UpkeepVehicleService service = service();
        AddResult result = service.canAdd(holder, target);
        if (result != AddResult.OK) {
            both(pending, UpkeepVehicleMessages.forAdd(result, holder, holder.vehicles(service.registry()).size()));
            return;
        }
        if (!service.register(holder, target, pending.owner().characterId())) {
            both(pending, UpkeepVehicleMessages.saveFailed());
            return;
        }
        Members.online(Members.key(pending.owner())).ifPresent(online ->
                online.sendMessage(UpkeepVehicleMessages.addedForOwner(holder, target.typeId())));
        Members.online(holder.leader()).ifPresent(online ->
                online.sendMessage(UpkeepVehicleMessages.addedForLeader(holder, target.typeId())));
    }

    /** Tells the leader and the owner, whoever of them is online. */
    private static void both(Pending pending, String message) {
        Members.online(Members.key(pending.owner())).ifPresent(online -> online.sendMessage(message));
        Members.online(pending.holder().leader()).ifPresent(online -> online.sendMessage(message));
    }

    private static void notifyOther(Pending pending, boolean byLeader, String message) {
        String other = byLeader ? Members.key(pending.owner()) : pending.holder().leader();
        Members.online(other).ifPresent(online -> online.sendMessage(message));
    }

    /**
     * Completions for {@code /<faction|guild> vehicle ...} from the second word on: the guild's
     * subcommands, {@code transfer upkeep}, and the upkeep actions with their IDs and members.
     */
    public static List<String> complete(Player player, Kind kind, String[] args) {
        String sub = args[1];
        if (args.length == 2) return VehicleTabCompletions.filter(List.of("transfer", "upkeep", "disable"), sub);
        if (sub.equalsIgnoreCase("transfer") && args.length == 3) {
            return VehicleTabCompletions.filter(List.of("upkeep"), args[2]);
        }
        if (!sub.equalsIgnoreCase("upkeep")) return new ArrayList<>();
        if (args.length == 3) return VehicleTabCompletions.filter(List.of("list", "request", "approve", "deny", "give"), args[2]);
        String action = args[2];
        if (args.length == 4 && (action.equalsIgnoreCase("approve") || action.equalsIgnoreCase("deny"))) {
            return VehicleTabCompletions.filter(pendingIds(player, kind), args[3]);
        }
        if (action.equalsIgnoreCase("give")) {
            return Members.completions(memberNames(player, kind), Arrays.copyOfRange(args, 2, args.length));
        }
        return new ArrayList<>();
    }

    /** Vehicle IDs of the requests the player could approve or deny. */
    static List<String> pendingIds(Player player, Kind kind) {
        List<String> ids = new ArrayList<>();
        UpkeepHolder holder = UpkeepHolder.memberOf(Members.key(player), kind);
        if (holder == null) return ids;
        UUID character = VehicleOwnershipQueries.characterId(player);
        for (Pending pending : UpkeepRequests.forHolder(holder)) {
            if (holder.isLeader(Members.key(player)) || pending.owner().characterId().equals(character)) {
                ids.add(pending.shortId());
            }
        }
        return ids;
    }

    /** Names of the members a leader could hand a vehicle to. */
    static List<String> memberNames(Player player, Kind kind) {
        UpkeepHolder holder = UpkeepHolder.ledBy(Members.key(player), kind);
        return holder == null ? new ArrayList<>() : new ArrayList<>(Members.display(holder.members()));
    }
}
