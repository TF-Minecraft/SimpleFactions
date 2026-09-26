package net.tfminecraft.simplefactions.installation;

import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

public final class InstallationOwners {
    private InstallationOwners() {}

    /**
     * The faction holding this installation object. Ids repeat across factions, so this
     * matches the instance itself rather than its id.
     */
    public static Faction ownerOf(Installation installation) {
        if (installation == null || installation.getId() == null || FactionManager.factions == null) {
            return null;
        }
        for (Faction faction : FactionManager.factions) {
            InstallationHandler handler = faction == null ? null : faction.getInstallationHandler();
            if (handler != null && handler.getById(installation.getId()) == installation) {
                return faction;
            }
        }
        return null;
    }

    public static String ownerIdOf(Installation installation) {
        Faction owner = ownerOf(installation);
        return owner == null ? null : owner.getId();
    }
}
