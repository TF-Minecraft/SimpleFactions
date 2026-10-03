package net.tfminecraft.simplefactions.espionage;

import java.util.List;
import net.tfminecraft.simplefactions.objects.Faction;

/** Non-numeric intelligence is frozen with the same faction-wide daily report. */
final class ReportDetails {
    private ReportDetails() {}

    static void capture(IntelligenceReport report, Faction target) {
        for (var installation : target.getInstallationHandler().getAll()) report.maximums.put("Installation:" + installation.getId() + ":Level",
                (long) net.tfminecraft.simplefactions.loaders.InstallationConfigLoader.getMaximumLevel(installation.getKind()));
        for (var guild : target.getGuildHandler().getGuilds()) for (var upgrade : guild.getUpgrades())
            if (upgrade.hasMaxLevel()) report.maximums.put(IntelligenceLedger.key(guild, "Upgrade:" + upgrade.getId()), (long) upgrade.getMaxLevel());
        if (report.allows("office-holder")) report.details.put("leader-offices", java.util.Arrays.stream(SpecialPosition.values()).filter(office -> {
            var holder = target.getEspionage().holder(office);
            return holder != null && target.isLeader(holder.playerName);
        }).map(SpecialPosition::label).toList());
        if (report.allows("training")) {
            report.details.put("training", target.getMilitary().getQueue().stream()
                    .map(entry -> entry.getRegiment().getName()).limit(3).toList());
        }
        if (report.allows("laws")) for (var group : target.getLawHandler().getGroupList()) {
            if (group.getCurrent() != null) report.details.put("law:" + group.getId(), List.of(group.getCurrent().getName()));
        }
        if (report.allows("installation-details")) {
            report.details.put("installations", target.getInstallationHandler().getAll().stream()
                    .sorted(java.util.Comparator.comparing(installation -> installation.getKind().name() + installation.getId()))
                    .map(installation -> installation.getId() + "\n" + installation.getKind().name() + "\n" + installation.getName()).toList());
            var pending = target.getInstallationHandler().getPendingConstruction();
            report.details.put("construction", pending == null ? List.of() : List.of((pending.isUpgrade() ? "Upgrading " : "Building ") + pending.getName()));
        }
        if (report.allows("guild-leader")) for (var guild : target.getGuildHandler().getGuilds())
            report.details.put("guild-leader:" + guild.getId(), List.of(CharacterNames.of(guild.getLeader())));
        if (report.allows("upgrades")) for (var guild : target.getGuildHandler().getGuilds())
            report.details.put("upgrading:" + guild.getId(), guild.getUpgradeQueue().stream()
                    .map(entry -> entry.getUpgrade().getName()).limit(3).toList());
    }
}
