package net.tfminecraft.simplefactions.installation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.zoc.FortZocIndex;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;

/** Resolves legacy holder-local references once, before a loaded war becomes live. */
public final class WarInstallationMigration {
    private static final Logger LOGGER = Logger.getLogger(WarInstallationMigration.class.getName());

    private WarInstallationMigration() {}

    public static void migrate(War war) {
        List<Installation> installations = List.copyOf(InstallationLookup.all());
        war.setFortControllers(migrateKeys(war, "fort controller", war.getFortControllers(),
                installations.stream().filter(i -> i.getKind() == InstallationKind.FORT).toList(), false));
        war.setWartimeInstallationOwners(migrateKeys(war, "original owner", war.getWartimeInstallationOwners(),
                installations, true));
        war.setRaidRepairLockUntil(migrateKeys(war, "repair lock", war.getRaidRepairLockUntil(), installations, false));
        ScheduleLeg active = CampaignScheduleService.activeLeg(war);
        for (ScheduleLeg leg : ScheduleLeg.values()) {
            List<ScheduledCampaignBattle> schedule = CampaignScheduleService.scheduleListForLeg(war, leg);
            for (int index = 0; index < schedule.size(); index++) {
                ScheduledCampaignBattle slot = schedule.get(index);
                if (slot.kind() != CampaignBattleKind.SIEGE || slot.fortInstallationId() == null) continue;
                List<Installation> matches = installations.stream()
                        .filter(i -> i.getKind() == InstallationKind.FORT && slot.fortInstallationId().equals(i.getId()))
                        .toList();
                // A home-province match already identifies the intended physical fort.
                if (matches.stream().anyMatch(i -> i.getProvince() == slot.provinceId())) continue;
                if (matches.size() != 1 || FortZocIndex.fromGameState().fortForProvince(slot.provinceId())
                        .filter(covering -> covering.stableKey().equals(matches.getFirst().getStableKey()))
                        .isEmpty()) {
                    LOGGER.warning("War " + war.getId() + ": preserving unresolved legacy siege at province "
                            + slot.provinceId() + " for fort '" + slot.fortInstallationId() + "'");
                    continue;
                }
                Installation fort = matches.getFirst();
                schedule.set(index, new ScheduledCampaignBattle(fort.getProvince(), slot.kind(), slot.required(),
                        fort.getId(), slot.portInstallationId(),
                        slot.chronologyProvinceId() == null ? slot.provinceId() : slot.chronologyProvinceId()));
                if (leg == active && index == CampaignScheduleService.scheduleIndexForLeg(war, leg)
                        && Objects.equals(war.getScheduledBattleProvinceId(), slot.provinceId())) {
                    war.setScheduledBattleProvinceId(fort.getProvince());
                }
            }
        }
    }

    private static <T> Map<String, T> migrateKeys(War war, String purpose, Map<String, T> previous,
            List<Installation> installations, boolean requireBelligerent) {
        Map<String, T> migrated = new LinkedHashMap<>();
        // Prefer explicit physical entries regardless of the old map's iteration order.
        for (Installation installation : installations) {
            T value = previous.get(installation.getStableKey());
            if (value != null) migrated.put(installation.getStableKey(), value);
        }
        for (var entry : previous.entrySet()) {
            String key = entry.getKey();
            if (migrated.containsKey(key)) continue;
            List<Installation> matches = installations.stream().filter(i -> i.getId().equals(key)).toList();
            if (matches.size() != 1 || entry.getValue() == null) {
                warn(war, purpose, key);
                continue;
            }
            Installation installation = matches.getFirst();
            if (requireBelligerent && war.getSide(InstallationOwners.ownerOf(installation)) == null) {
                warn(war, purpose, key);
                continue;
            }
            migrated.putIfAbsent(installation.getStableKey(), entry.getValue());
        }
        return migrated;
    }

    private static void warn(War war, String purpose, String key) {
        LOGGER.warning("War " + war.getId() + ": discarded ambiguous, orphaned or invalid legacy "
                + purpose + " reference '" + key + "'");
    }
}
