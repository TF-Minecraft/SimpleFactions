package me.Plugins.SimpleFactions.installation.handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Database.InstallationConstructionData;
import me.Plugins.SimpleFactions.Database.InstallationData;
import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Map.ProvinceSpatial;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Bank;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationConstruction;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.Plugins.TLibs.Utils.TimeFormatter;

public class InstallationHandler {
    private final Faction faction;
    private final Map<String, Installation> byId = new HashMap<>();
    private final Map<String, Installation> byProvinceKind = new HashMap<>();
    private InstallationConstruction pendingConstruction;
    private final Map<String, InstallationConstruction> pendingByProvinceKind = new HashMap<>();

    public InstallationHandler(Faction faction) {
        this.faction = faction;
    }

    public void load(List<InstallationData> data) {
        byId.clear();
        byProvinceKind.clear();
        if (data == null) {
            return;
        }
        for (InstallationData entry : data) {
            try {
                register(new Installation(entry));
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    public void loadConstruction(InstallationConstructionData data) {
        pendingConstruction = null;
        pendingByProvinceKind.clear();
        if (data == null) {
            return;
        }
        try {
            InstallationConstruction construction = new InstallationConstruction(data);
            if (construction.getTimeLeft() <= 0) {
                completeConstruction(construction);
                return;
            }
            setPendingConstruction(construction);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public InstallationConstructionData serializeConstruction() {
        if (pendingConstruction == null) {
            return null;
        }
        return pendingConstruction.toData();
    }

    public InstallationConstruction getPendingConstruction() {
        return pendingConstruction;
    }

    public Installation getByProvince(InstallationKind kind, int provinceId) {
        return byProvinceKind.get(indexKey(kind, provinceId));
    }

    public Installation getById(String id) {
        if (id == null) {
            return null;
        }
        return byId.get(id);
    }

    public Collection<Installation> getAll() {
        return byId.values();
    }

    public List<InstallationData> serialize() {
        List<InstallationData> out = new ArrayList<>();
        for (Installation installation : byId.values()) {
            out.add(installation.toData());
        }
        return out;
    }

    public ConstructResult construct(
            InstallationKind kind,
            String displayName,
            int province,
            int x,
            int z) {
        ValidatedConstruct validated = validateConstruct(kind, displayName, province, x, z);
        if (validated.failed()) {
            return validated.failure;
        }

        InstallationConstruction construction = new InstallationConstruction(
                validated.id,
                validated.name,
                kind,
                province,
                x,
                z);
        setPendingConstruction(construction);

        return ConstructResult.ok(
                "§eBuilding "
                        + kind.getCommandName()
                        + " §f"
                        + validated.name
                        + "§e - §7"
                        + TimeFormatter.formatTime(construction.getTimeLeft())
                        + " remaining");
    }

    public ConstructResult constructInstant(
            InstallationKind kind,
            String displayName,
            int province,
            int x,
            int z) {
        ValidatedConstruct validated = validateConstruct(kind, displayName, province, x, z);
        if (validated.failed()) {
            return validated.failure;
        }

        Installation installation = placeCompleted(
                validated.id,
                validated.name,
                kind,
                province,
                x,
                z,
                System.currentTimeMillis());

        return ConstructResult.ok(
                "§aConstructed "
                        + kind.getCommandName()
                        + " §f"
                        + validated.name
                        + " §7("
                        + validated.id
                        + ")",
                installation);
    }

    public ConstructResult deconstruct(String id) {
        if (id == null || id.isBlank()) {
            return ConstructResult.fail("§cUsage: §e/faction deconstruct <id>");
        }

        if (pendingConstruction != null && pendingConstruction.getId().equalsIgnoreCase(id)) {
            String kindName = pendingConstruction.getKind().getCommandName();
            String constructionName = pendingConstruction.getName();
            clearPendingConstruction();
            return ConstructResult.ok(
                    "§aCancelled construction of "
                            + kindName
                            + " §f"
                            + constructionName
                            + " §7("
                            + id
                            + ")");
        }

        Installation installation = getById(id);
        if (installation == null) {
            return ConstructResult.fail("§cNo installation with id §f" + id);
        }

        String installationName = installation.getName();
        String kindName = installation.getKind().getCommandName();

        byId.remove(installation.getId());
        byProvinceKind.remove(indexKey(installation.getKind(), installation.getProvince()));
        enqueueMapUpdate();

        return ConstructResult.ok(
                "§aDeconstructed "
                        + kindName
                        + " §f"
                        + installationName
                        + " §7("
                        + installation.getId()
                        + ")",
                installation);
    }

    public void tick() {
        if (pendingConstruction == null) {
            return;
        }
        pendingConstruction.tick();
        if (pendingConstruction.getTimeLeft() != 0) {
            return;
        }
        completeConstruction(pendingConstruction);
    }

    public void payDailyUpkeep() {
        List<Installation> installations = new ArrayList<>(getAll());
        installations.sort(
                Comparator.comparingDouble(
                                (Installation installation) ->
                                        InstallationConfigLoader.getDailyUpkeep(installation.getKind()))
                        .thenComparingLong(Installation::getCompletedAt));

        Bank bank = faction.getBank();
        for (Installation installation : installations) {
            double upkeep = InstallationConfigLoader.getDailyUpkeep(installation.getKind());
            if (upkeep <= 0) {
                continue;
            }
            if (bank == null || bank.getWealth() < upkeep) {
                dissolveForNonPayment(installation);
                continue;
            }
            bank.withdraw(upkeep);
        }
    }

    public void cancelPendingConstructionOnProvince(int province) {
        if (pendingConstruction != null && pendingConstruction.getProvince() == province) {
            clearPendingConstruction();
        }
    }

    public List<Installation> detachOnProvince(int province) {
        List<Installation> moved = new ArrayList<>();
        for (Installation installation : new ArrayList<>(byId.values())) {
            if (installation.getProvince() == province) {
                removeInstallation(installation);
                moved.add(installation);
            }
        }
        return moved;
    }

    public void acceptTransferred(Installation installation) {
        if (installation == null) {
            return;
        }
        register(installation);
        enqueueMapUpdate();
    }

    public void onProvinceLost(int province) {
        cancelPendingConstructionOnProvince(province);

        List<Installation> snapshot = new ArrayList<>(byId.values());
        for (Installation installation : snapshot) {
            if (installation.getProvince() == province) {
                dissolve(installation);
            }
        }
    }

    public void validate() {
        if (pendingConstruction != null
                && !faction.getProvinceHandler().hasProvince(pendingConstruction.getProvince())) {
            clearPendingConstruction();
        }

        List<Installation> snapshot = new ArrayList<>(byId.values());
        for (Installation installation : snapshot) {
            if (!faction.getProvinceHandler().hasProvince(installation.getProvince())) {
                dissolve(installation);
            }
        }
    }

    void register(Installation installation) {
        byId.put(installation.getId(), installation);
        byProvinceKind.put(
                indexKey(installation.getKind(), installation.getProvince()),
                installation);
    }

    void dissolve(Installation installation) {
        removeInstallation(installation);

        Player leader = Bukkit.getPlayerExact(faction.getLeader());
        if (leader != null) {
            leader.sendMessage(
                    "§c"
                            + installation.getKind().getCommandName()
                            + " §f"
                            + installation.getName()
                            + " §chas been destroyed");
        }
    }

    private void dissolveForNonPayment(Installation installation) {
        removeInstallation(installation);

        Player leader = Bukkit.getPlayerExact(faction.getLeader());
        if (leader != null) {
            leader.sendMessage(
                    "§c"
                            + installation.getKind().getCommandName()
                            + " §f"
                            + installation.getName()
                            + " §chas been destroyed §7(unable to pay upkeep)");
        }
    }

    private void removeInstallation(Installation installation) {
        byId.remove(installation.getId());
        byProvinceKind.remove(indexKey(installation.getKind(), installation.getProvince()));
        enqueueMapUpdate();
    }

    private ValidatedConstruct validateConstruct(
            InstallationKind kind,
            String displayName,
            int province,
            int x,
            int z) {
        if (kind == null) {
            return ValidatedConstruct.fail(ConstructResult.fail("§cUnknown installation type"));
        }
        if (displayName == null || displayName.isBlank()) {
            return ValidatedConstruct.fail(
                    ConstructResult.fail(
                            "§cName required: §e/faction construct "
                                    + kind.getCommandName()
                                    + " <name>"));
        }
        if (pendingConstruction != null) {
            return ValidatedConstruct.fail(
                    ConstructResult.fail("§cYour faction is already building an installation"));
        }
        if (!faction.getProvinceHandler().hasProvince(province)) {
            return ValidatedConstruct.fail(
                    ConstructResult.fail("§cYour faction doesn't own this province!"));
        }
        if (byProvinceKind.containsKey(indexKey(kind, province))
                || pendingByProvinceKind.containsKey(indexKey(kind, province))) {
            return ValidatedConstruct.fail(
                    ConstructResult.fail(
                            "§cThis province already has a " + kind.getCommandName()));
        }

        Province prov = SimpleFactions.getInstance().getProvinceManager().get(province);
        if (!prov.isValid()) {
            return ValidatedConstruct.fail(
                    ConstructResult.fail("§cThis location has no province!"));
        }
        if (prov.isSea()) {
            return ValidatedConstruct.fail(
                    ConstructResult.fail("§cYou cannot construct on water!"));
        }
        if (kind == InstallationKind.PORT
                && !ProvinceSpatial.withinConfiguredPortSeaProximity(x, z)) {
            return ValidatedConstruct.fail(
                    ConstructResult.fail(
                            "§cPorts must be within "
                                    + me.Plugins.SimpleFactions.Cache.portSeaProximityBlocks
                                    + " blocks of sea or river!"));
        }

        String id = Formatter.formatId(displayName);
        if (id.isBlank()) {
            return ValidatedConstruct.fail(ConstructResult.fail("§cInvalid installation name"));
        }
        if (byId.containsKey(id)) {
            return ValidatedConstruct.fail(
                    ConstructResult.fail("§cAn installation with that id already exists"));
        }

        String name = StringFormatter.formatHex(Formatter.formatName(displayName));
        return ValidatedConstruct.ok(id, name);
    }

    private Installation placeCompleted(
            String id,
            String name,
            InstallationKind kind,
            int province,
            int x,
            int z,
            long completedAt) {
        Installation installation = new Installation(id, name, kind, province, x, z, completedAt);
        register(installation);
        enqueueMapUpdate();
        return installation;
    }

    private void completeConstruction(InstallationConstruction construction) {
        Installation installation = placeCompleted(
                construction.getId(),
                construction.getName(),
                construction.getKind(),
                construction.getProvince(),
                construction.getCenterX(),
                construction.getCenterZ(),
                System.currentTimeMillis());
        clearPendingConstruction();

        Player leader = Bukkit.getPlayerExact(faction.getLeader());
        if (leader != null) {
            leader.sendMessage(
                    "§a"
                            + installation.getKind().getCommandName()
                            + " §f"
                            + installation.getName()
                            + " §ahas finished construction");
        }
    }

    private static final class ValidatedConstruct {
        private final ConstructResult failure;
        private final String id;
        private final String name;

        private ValidatedConstruct(ConstructResult failure, String id, String name) {
            this.failure = failure;
            this.id = id;
            this.name = name;
        }

        static ValidatedConstruct fail(ConstructResult failure) {
            return new ValidatedConstruct(failure, null, null);
        }

        static ValidatedConstruct ok(String id, String name) {
            return new ValidatedConstruct(null, id, name);
        }

        boolean failed() {
            return failure != null;
        }
    }

    private void setPendingConstruction(InstallationConstruction construction) {
        pendingConstruction = construction;
        pendingByProvinceKind.put(
                indexKey(construction.getKind(), construction.getProvince()),
                construction);
    }

    private void clearPendingConstruction() {
        if (pendingConstruction != null) {
            pendingByProvinceKind.remove(
                    indexKey(pendingConstruction.getKind(), pendingConstruction.getProvince()));
        }
        pendingConstruction = null;
    }

    private void enqueueMapUpdate() {
        FactionManager.getMap().enqueue("nation", faction.getRGB());
    }

    private static String indexKey(InstallationKind kind, int province) {
        return kind.name() + ":" + province;
    }
}
