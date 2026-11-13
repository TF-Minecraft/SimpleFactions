package net.tfminecraft.simplefactions.vehicles.upkeep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseMessages;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferMessages;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepHolder.Kind;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepVehicleService.AddResult;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepVehicleService.GiveOutcome;

public final class UpkeepVehicleMessages {
    private UpkeepVehicleMessages() {}

    public static String usage(Kind kind) {
        String root = "/" + kind.command() + " vehicle ";
        return "§cUsage: §e" + root + "upkeep §7(list), §e" + root + "transfer upkeep§7, §e" + root
                + "upkeep request§7, §e" + root + "upkeep approve|deny <vehicle id>§7, §e" + root
                + "upkeep give <member>";
    }

    public static String notLeader(Kind kind) {
        return kind == Kind.FACTION
                ? "§cYou need to be a faction leader to manage upkeep-only vehicles."
                : "§cYou need to lead a guild to manage its upkeep-only vehicles.";
    }

    public static String notMember(Kind kind) {
        return kind == Kind.FACTION
                ? "§cYou need to be in a faction to ask for an upkeep-only vehicle."
                : "§cYou need to be in a guild (not a realm's main guild) to ask for an upkeep-only vehicle.";
    }

    private static String stop(Kind kind) {
        return " §7(/" + kind.command() + " vehicle disable to stop)";
    }

    public static String addArmed(UpkeepHolder holder) {
        return "§aRight-click the vehicle to make it an upkeep-only vehicle of " + holder.name() + "."
                + stop(holder.kind());
    }

    public static String requestArmed(UpkeepHolder holder) {
        return "§aRight-click your vehicle to ask the leader of " + holder.name()
                + " to keep it as an upkeep-only vehicle." + stop(holder.kind());
    }

    public static String giveArmed(UpkeepHolder holder, String member) {
        return "§aRight-click an upkeep-only vehicle of " + holder.name() + " to give it to " + member + "."
                + stop(holder.kind());
    }

    public static String notAMemberNamed(UpkeepHolder holder, String input) {
        return "§cNo member of " + holder.name() + " §cmatches " + input + ".";
    }

    public static String notYourVehicle() {
        return "§cYou can only ask for your own vehicle. Its owner has to ask, or the leader can move it in.";
    }

    public static String forAdd(AddResult result, UpkeepHolder holder, int count) {
        return switch (result) {
            case OK -> null;
            case NO_HOLDER -> "§cThat faction or guild no longer exists.";
            case NOT_FOUND -> "§cThat vehicle could not be found.";
            case NOT_OWNED -> "§cThis vehicle must be owned by a player before it can be made upkeep-only.";
            case NOT_MEMBER -> "§cOnly vehicles owned by members of " + holder.name() + " §ccan be made upkeep-only.";
            case ALREADY_UPKEEP -> "§cThis vehicle is already an upkeep-only vehicle.";
            case ALREADY_IN_POOL -> "§cThis vehicle is in a faction vehicle pool. Take it out first (§e/faction vehicle take§c).";
            case ALREADY_BERTHED -> "§cThis vehicle is berthed at an installation. Take it out first (§e/faction vehicle take§c).";
            case UNKNOWN_TYPE -> "§cThis vehicle is not registered for upkeep.";
            case AT_CAP -> "§c" + holder.name() + " §calready keeps " + count + "/" + holder.cap()
                    + " upkeep-only vehicles (" + UpkeepHolder.VEHICLES_PER_MEMBER + " per member).";
        };
    }

    private static String payer(UpkeepHolder holder) {
        return holder.kind() == Kind.FACTION ? "the faction's main guild bank" : "the guild bank";
    }

    private static String upkeep(String typeId) {
        return Formatter.formatMoney(VehiclesConfigLoader.getUpkeep(typeId)) + "d";
    }

    public static String addedForLeader(UpkeepHolder holder, String typeId) {
        return "§a" + typeId + " is now an upkeep-only vehicle of " + holder.name() + ". " + capitalised(payer(holder))
                + " pays " + upkeep(typeId) + " a day; it cannot be used in battles.";
    }

    public static String addedForOwner(UpkeepHolder holder, String typeId) {
        return "§aYour " + typeId + " is now kept by " + holder.name()
                + " as an upkeep-only vehicle. From now on only its leader decides what happens to it.";
    }

    private static String capitalised(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public static String alreadyPending() {
        return "§cThis vehicle already has a pending upkeep-only request.";
    }

    public static String requestSent(UpkeepHolder holder, String typeId, boolean leaderOnline) {
        return "§aAsked the leader of " + holder.name() + " to keep your " + typeId
                + " as an upkeep-only vehicle. The request lapses in 10 minutes"
                + (leaderOnline ? "." : "; the leader is offline and sees it in §e/" + holder.kind().command()
                        + " vehicle upkeep§a.");
    }

    public static String ownerAsked(String ownerName) {
        return "§aAsked " + ownerName + " to agree. The request lapses in 10 minutes.";
    }

    public static Component leaderPrompt(UpkeepRequests.Pending pending, String ownerName) {
        return prompt("§e" + ownerName + " asks " + pending.holder().name() + " to keep their " + pending.typeId()
                + " §8#" + pending.shortId() + " §eas an upkeep-only vehicle (" + upkeep(pending.typeId())
                + " a day from " + payer(pending.holder()) + ").", pending);
    }

    public static Component ownerPrompt(UpkeepRequests.Pending pending, String leaderName) {
        return prompt("§e" + leaderName + " wants " + pending.holder().name() + " to keep your " + pending.typeId()
                + " §8#" + pending.shortId() + " §eas an upkeep-only vehicle. You would no longer control it;"
                + " only the leader would.", pending);
    }

    private static Component prompt(String text, UpkeepRequests.Pending pending) {
        String root = "/" + pending.holder().kind().command() + " vehicle upkeep ";
        return Component.text()
                .append(legacy(text))
                .append(Component.text(" "))
                .append(button("[Approve]", NamedTextColor.GREEN, root + "approve " + pending.shortId()))
                .append(Component.text(" "))
                .append(button("[Deny]", NamedTextColor.RED, root + "deny " + pending.shortId()))
                .build();
    }

    private static Component button(String label, NamedTextColor colour, String command) {
        return Component.text(label, colour, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command)));
    }

    private static Component legacy(String text) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text);
    }

    public static String noPending() {
        return "§cNo pending upkeep-only request matches that vehicle. §7See §e/faction vehicle upkeep§7.";
    }

    public static String notWaitingForYou() {
        return "§cThis upkeep-only request is not waiting for you.";
    }

    public static String declined(String who, String typeId) {
        return "§c" + who + " declined the upkeep-only request for the " + typeId + ".";
    }

    public static String youDeclined() {
        return "§7You declined the upkeep-only request.";
    }

    public static String ownerChanged() {
        return VehicleTransferMessages.ownerChanged();
    }

    public static String saveFailed() {
        return VehicleTransferMessages.saveFailed();
    }

    public static String forGive(GiveOutcome outcome, UpkeepHolder holder, String recipientName) {
        return switch (outcome.status()) {
            case OK -> "§aGave the " + outcome.vehicleTypeId() + " to " + recipientName + " as their personal vehicle.";
            case NOT_LEADER -> notLeader(holder.kind());
            case NOT_UPKEEP_VEHICLE -> "§cThis is not an upkeep-only vehicle of " + holder.name() + ".";
            case NOT_MEMBER -> "§c" + recipientName + " §cis not a member of " + holder.name() + ".";
            case NO_PERSONAL_ROOM -> FactionVehicleReleaseMessages.personalRoom(
                    outcome.slotFailure(), outcome.vehicleTypeId(), recipientName);
            case UNKNOWN_TYPE -> "§cThis vehicle type is not registered for upkeep.";
            case OWNERSHIP_UNAVAILABLE -> FactionVehicleReleaseMessages.ownershipUnavailable();
            case SAVE_FAILED -> saveFailed();
        };
    }

    public static String received(UpkeepHolder holder, String leaderName, String typeId) {
        return "§a" + leaderName + " gave you the " + typeId + " from " + holder.name()
                + ". It is now your personal vehicle and you pay its upkeep.";
    }

    public static String header(UpkeepHolder holder, int count) {
        return "§bUpkeep-only vehicles of " + holder.name() + "§b: §e" + count + "/" + holder.cap()
                + " §7(" + UpkeepHolder.VEHICLES_PER_MEMBER + " per member)";
    }

    public static String overCap() {
        return "§cOver the cap: nothing new can be added until members join or vehicles leave.";
    }

    public static String daily(UpkeepHolder holder, double total) {
        return "§7Daily upkeep: §e" + Formatter.formatMoney(total) + "d §7from " + payer(holder)
                + ". §7Never used in battles.";
    }

    public static String none() {
        return "§7None yet.";
    }

    public static String line(String typeId, String shortId, String location, boolean unpaid) {
        return "§7- §f" + typeId + " §8#" + shortId + " §8" + upkeep(typeId) + "/day"
                + (unpaid ? " §cunpaid" : "") + " §7- " + location;
    }

    public static String pendingHeader() {
        return "§bPending requests:";
    }

    public static String pendingLine(UpkeepRequests.Pending pending, String ownerName) {
        return "§7- §f" + pending.typeId() + " §8#" + pending.shortId() + " §7from " + ownerName
                + (pending.awaiting() == UpkeepRequests.Awaiting.LEADER
                        ? " §7- waiting for the leader" : " §7- waiting for the owner");
    }

    public static String leaderHelp(Kind kind) {
        String root = "/" + kind.command() + " vehicle ";
        return "§7Leader: §e" + root + "transfer upkeep§7, §e" + root + "upkeep give <member>§7, §e"
                + root + "upkeep approve|deny <id>";
    }
}
