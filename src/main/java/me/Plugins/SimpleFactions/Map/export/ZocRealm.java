package me.Plugins.SimpleFactions.Map.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.installation.Installation;

/**
 * Fort zone-of-control province selection for map export and campaign schedule.
 *
 * <p>ZOC shape: fort province plus one-ring land neighbors owned by factions in the
 * same top realm as the controller faction ({@link RelationManager#getTopLiege}).
 *
 * <p>Map export ({@link #computeZocProvincesForExport}) uses the installation owner
 * unless the fort is referenced on an active war with a {@code fortControllers} entry;
 * then the controller coalition's war leader drives neighbor inclusion. Ports and
 * airports are unchanged (no ZOC export).
 */
public final class ZocRealm {
    private ZocRealm() {
    }

    public static String topRealmId(Faction faction) {
        String topLiege = RelationManager.getTopLiege(faction);
        return topLiege != null ? topLiege : faction.getId();
    }

    public static boolean sameTopRealm(Faction a, Faction b) {
        return topRealmId(a).equalsIgnoreCase(topRealmId(b));
    }

    public static List<Integer> computeZocProvinces(Faction fortOwner, int fortProvince) {
        ProvinceManager provinceManager = SimpleFactions.getInstance().getProvinceManager();
        Province fortProv = provinceManager.get(fortProvince);
        if (fortProv == null || !fortProv.isValid()) {
            return List.of();
        }

        TreeSet<Integer> provinces = new TreeSet<>();
        provinces.add(fortProvince);

        for (int neighborId : fortProv.getNeighbours()) {
            Province neighbor = provinceManager.get(neighborId);
            if (neighbor == null || !neighbor.isValid() || neighbor.isSea()) {
                continue;
            }

            Faction owner = neighbor.getOwner();
            if (owner == null) {
                continue;
            }

            if (sameTopRealm(fortOwner, owner)) {
                provinces.add(neighborId);
            }
        }

        return new ArrayList<>(provinces);
    }

    public static Faction resolveExportControllerFaction(
            Installation fort,
            Faction installationOwner,
            List<War> activeWars) {
        if (fort == null || installationOwner == null) {
            return installationOwner;
        }
        War war = selectPrimaryWarForFort(fort, activeWars);
        if (war == null) {
            return installationOwner;
        }
        String fortId = fort.getId();
        if (fortId == null || fortId.isBlank()) {
            return installationOwner;
        }
        CampaignCoalition coalition = war.getFortControllers().get(fortId);
        if (coalition == null) {
            return installationOwner;
        }
        Side side = CampaignCoalitionService.toSide(war, coalition);
        if (side == null || side.getLeader() == null) {
            return installationOwner;
        }
        return side.getLeader();
    }

    public static War selectPrimaryWarForFort(Installation fort, List<War> activeWars) {
        if (fort == null || activeWars == null || activeWars.isEmpty()) {
            return null;
        }
        List<War> candidates = activeWars.stream()
                .filter(war -> referencesFort(war, fort))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        int fortProvince = fort.getProvince();
        List<War> onAxis = candidates.stream()
                .filter(war -> axisContains(war, fortProvince))
                .sorted(Comparator.comparingInt(War::getId))
                .toList();
        if (onAxis.isEmpty()) {
            return null;
        }
        return onAxis.get(0);
    }

    public static List<Integer> computeZocProvincesForExport(
            Installation fort,
            Faction installationOwner,
            List<War> activeWars) {
        Faction controller = resolveExportControllerFaction(fort, installationOwner, activeWars);
        if (fort == null) {
            return List.of();
        }
        return computeZocProvinces(controller, fort.getProvince());
    }

    private static boolean referencesFort(War war, Installation fort) {
        if (war == null || fort == null) {
            return false;
        }
        String fortId = fort.getId();
        if (fortId != null && !fortId.isBlank() && war.getFortControllers().containsKey(fortId)) {
            return true;
        }
        if (axisContains(war, fort.getProvince())) {
            return true;
        }
        if (fortId == null || fortId.isBlank()) {
            return false;
        }
        List<ScheduledCampaignBattle> schedule = war.getCampaignBattleSchedule();
        if (schedule == null) {
            return false;
        }
        for (ScheduledCampaignBattle slot : schedule) {
            if (fortId.equals(slot.fortInstallationId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean axisContains(War war, int fortProvince) {
        List<Integer> axis = war.getCampaignProvinces();
        return axis != null && axis.contains(fortProvince);
    }
}
