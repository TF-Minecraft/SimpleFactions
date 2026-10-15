package net.tfminecraft.simplefactions.economy.settlement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.FactionCleanup;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;

/** Replays the nonfinancial preparation of a day before its monetary plan is published. */
public final class DailyPreparationJournal {
    private record Removal(String faction, UUID incarnation, Integer province) {}
    public record Prepared(long day, long timestamp, List<FactionUpkeepPlan> upkeep) {}
    private static final class Saved {
        long day;
        long timestamp;
        List<Removal> removals;
        List<FactionUpkeepPlan> upkeep;
        Map<String, Map<String, Double>> organization;
        Map<String, Integer> offlineDays;
        boolean prepared;
        boolean settled;
    }
    private final Path folder;
    private final Database database;
    public DailyPreparationJournal(Path folder, Database database) {
        this.folder = folder;
        this.database = database;
    }

    public List<Long> unfinishedDays() throws IOException {
        if (!Files.exists(folder)) return List.of();
        List<Long> days = new ArrayList<>();
        try (var files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches("[0-9]+\\.json")).toList()) {
                Saved saved = read(file);
                if (!saved.settled) days.add(saved.day);
            }
        }
        return days.stream().sorted().toList();
    }

    public Prepared prepare(long day, long now) throws IOException {
        Path path = file(day);
        Saved saved;
        if (Files.exists(path)) saved = read(path);
        else {
            saved = new Saved();
            saved.day = day;
            saved.timestamp = now;
            saved.removals = new ArrayList<>();
            saved.upkeep = new ArrayList<>();
            for (Faction faction : FactionManager.factions.stream().sorted(Comparator.comparing(Faction::getId)).toList()) {
                saved.removals.add(new Removal(faction.getId(), SettlementGuilds.capital(faction).getIncarnationId(), removal(faction)));
                saved.upkeep.add(FactionUpkeepPlan.calculate(faction));
            }
            DurableJson.write(path, saved);
        }
        if (!saved.prepared) {
            if (saved.organization == null) {
                for (Removal removal : saved.removals) {
                    Faction faction = require(removal);
                    // A faction save includes ownership, dependent titles, settlements and installations.
                    if (removal.province() != null && faction.hasProvince(removal.province()))
                        faction.removeProvince(removal.province(), true);
                    save(faction);
                }
                saved.organization = new LinkedHashMap<>();
                for (Removal removal : saved.removals) {
                    Faction faction = require(removal);
                    Map<String, Double> values = new LinkedHashMap<>();
                    for (Movement movement : faction.getGovernment().getMovements()) {
                        if (!movement.isFrozen()) values.put(movement.getId(), Math.clamp(
                                movement.getOrganization() + movement.getOrganizationGain(), 0, movement.getMaxOrganization()));
                    }
                    saved.organization.put(faction.getId(), values);
                }
                saved.offlineDays = FactionCleanup.planOfflineDays(FactionManager.factions);
                List<Removal> removals = saved.removals;
                saved.upkeep = saved.upkeep.stream().map(plan -> afterProvinceLoss(plan, removals)).toList();
                DurableJson.write(path, saved);
            }
            for (Removal removal : saved.removals) {
                Faction faction = require(removal);
                for (var value : saved.organization.get(faction.getId()).entrySet()) {
                    Movement movement = faction.getGovernment().getMovementById(value.getKey());
                    if (movement == null) throw new IOException("Prepared movement disappeared: " + value.getKey());
                    movement.restoreOrganization(value.getValue());
                }
                save(faction);
            }
            FactionCleanup.restoreOfflineDays(saved.offlineDays);
            saved.prepared = true;
            DurableJson.write(path, saved);
        }
        return new Prepared(saved.day, saved.timestamp, saved.upkeep);
    }

    public void complete(long day) throws IOException {
        Path path = file(day);
        Saved saved = read(path);
        saved.settled = true;
        DurableJson.write(path, saved);
    }

    private static FactionUpkeepPlan afterProvinceLoss(FactionUpkeepPlan plan, List<Removal> removals) {
        Integer lost = removals.stream().filter(r -> r.faction().equals(plan.faction())).findFirst().orElseThrow().province();
        if (lost == null) return plan;
        var installations = plan.installations().stream().filter(i -> !lost.equals(i.province)).toList();
        var construction = plan.construction();
        if (construction != null && lost.equals(construction.province)) construction = null;
        double cost = installations.stream().mapToDouble(i -> InstallationConfigLoader.getDailyUpkeep(InstallationKind.fromCommand(i.kind), i.level)).sum();
        return new FactionUpkeepPlan(plan.faction(), plan.regimentSlots(), installations, construction,
                plan.charge(), plan.armyLedgerCost(), cost);
    }

    private static Integer removal(Faction faction) {
        List<Integer> provinces = faction.getProvinces();
        if (CivilWarBorderLock.isLocked(faction) || net.tfminecraft.simplefactions.guild.HeldRealms.isHeld(faction)
                || !TitleManager.overProvinceCap(faction) || provinces.isEmpty()) return null;
        int remove = provinces.getLast();
        if (remove == faction.getCapital() && provinces.size() > 1) remove = provinces.get(provinces.size() - 2);
        return remove == faction.getCapital() ? null : remove;
    }

    private static Faction require(Removal removal) throws IOException {
        Faction faction = FactionManager.getByString(removal.faction());
        if (faction == null || !SettlementGuilds.capital(faction).getIncarnationId().equals(removal.incarnation()))
            throw new IOException("Prepared faction missing or replaced: " + removal.faction());
        return faction;
    }

    private void save(Faction faction) throws IOException {
        if (!database.saveFactionDurably(faction)) throw new IOException("Cannot persist day preparation for " + faction.getId());
    }

    private Path file(long day) { return folder.resolve(day + ".json"); }
    private static Saved read(Path path) throws IOException {
        Saved saved = DurableJson.read(path, Saved.class);
        if (!path.getFileName().toString().equals(saved.day + ".json") || saved.timestamp <= 0
                || saved.removals == null || saved.upkeep == null || (saved.prepared && saved.organization == null))
            throw new IOException("Invalid daily preparation: " + path);
        return saved;
    }
}
