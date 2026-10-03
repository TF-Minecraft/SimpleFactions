package net.tfminecraft.simplefactions.espionage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.entity.Player;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

/** One roster format for owned, staff and estimated views. Never sort foreign guilds by exact wealth. */
public final class RosterLore {
    private RosterLore() {}

    public static List<String> faction(Player viewer, Faction faction) { return build(viewer, faction, null); }
    public static List<String> guild(Player viewer, Guild guild) { return build(viewer, guild.getFaction(), guild); }

    private static List<String> build(Player viewer, Faction faction, Guild only) {
        boolean exact = EspionageService.canViewExact(viewer, faction);
        var report = exact ? null : EspionageService.report(viewer, faction);
        List<String> lore = new ArrayList<>();
        lore.add("#e8c55a\u00a7lFaction Leader: #f4e4aa" + CharacterNames.display(viewer, faction.getLeader()));
        List<String> leaderOffices = exact ? java.util.Arrays.stream(SpecialPosition.values()).filter(office -> {
            var holder = faction.getEspionage().holder(office);
            return holder != null && holder.playerName.equalsIgnoreCase(faction.getLeader());
        }).map(SpecialPosition::label).toList() : report == null ? List.of() : report.details("office-holder", "leader-offices");
        for (String office : leaderOffices) lore.add("#93c9a7  " + office + ": #c2daca" + CharacterNames.display(viewer, faction.getLeader()));
        List<Guild> guilds = new ArrayList<>(only == null ? faction.getGuildHandler().getGuilds() : List.of(only));
        if (exact && only == null) for (String name : faction.getCompleteMemberList()) {
            Guild represented = net.tfminecraft.simplefactions.managers.FactionManager.getGuildByMember(name);
            if (represented != null && guilds.stream().noneMatch(guild -> guild.getId().equals(represented.getId()))) guilds.add(represented);
        }
        guilds.sort(Comparator.comparing((Guild guild) -> {
            var wealth = exact ? guild.getWealth() : EspionageService.visibleValue(viewer, faction,
                    IntelligenceLedger.key(guild, "Wealth"), guild::getWealth);
            return wealth == null ? Double.NEGATIVE_INFINITY : wealth;
        }).reversed().thenComparing(Guild::getName, String.CASE_INSENSITIVE_ORDER));
        for (Guild guild : guilds) {
            List<IntelligenceReport.RosterMember> entries;
            if (exact) entries = guild.getMembers().stream().filter(name -> !faction.isLeader(name)).map(name ->
                    new IntelligenceReport.RosterMember(CharacterNames.display(viewer, name), guild.getId(), guild.getName(), true, guild.isLeader(name),
                            java.util.Arrays.stream(SpecialPosition.values()).filter(office -> {
                                var holder = faction.getEspionage().holder(office);
                                return holder != null && holder.playerName.equalsIgnoreCase(name);
                            }).toList())).toList();
            else entries = report == null || report.roster == null ? List.of() : report.roster.stream()
                    .filter(entry -> entry.guildId().equals(guild.getId()))
                    .filter(entry -> entry.sampled() && report.allows("guild-members") && report.allows("roster")
                            || entry.guildLeader() && report.allows("guild-leader")
                            || !entry.offices().isEmpty() && report.allows("office-holder")).toList();
            lore.add("");
            lore.add("#cba351\u00a7l" + guild.getName() + (guild.isBase() ? " #7fbd73(Capital)" : " #b8ae61(Guild)"));
            var sorted = new ArrayList<>(entries);
            sorted.sort(Comparator.comparingInt((IntelligenceReport.RosterMember entry) ->
                    (entry.guildLeader() && (exact || report.allows("guild-leader")) ? 0
                            : !entry.offices().isEmpty() && (exact || report.allows("office-holder")) ? 1 : 2))
                    .thenComparing(IntelligenceReport.RosterMember::character, String.CASE_INSENSITIVE_ORDER));
            for (var entry : sorted) {
                List<String> roles = new ArrayList<>();
                if (entry.guildLeader() && (exact || report.allows("guild-leader"))) roles.add("Guild Leader");
                if (exact || report.allows("office-holder")) entry.offices().forEach(office -> roles.add(office.label()));
                String prefix = roles.isEmpty() ? "#d4c9ae  - " : "#93c9a7  " + String.join(" / ", roles) + ": #c2daca";
                lore.add(prefix + entry.character());
            }
            if (!exact && entries.isEmpty() && !guild.isMember(faction.getLeader())) lore.add("\u00a77  Members: Unknown");
        }
        return lore;
    }
}
