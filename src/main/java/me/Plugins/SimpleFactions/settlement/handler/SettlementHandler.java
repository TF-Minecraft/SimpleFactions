package me.Plugins.SimpleFactions.settlement.handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Database.SettlementData;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.settlement.Settlement;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class SettlementHandler {
    private final Faction faction;
    private final Map<String, Settlement> byId = new HashMap<>();
    private final Map<Integer, Settlement> provinceIndex = new HashMap<>();

    public SettlementHandler(Faction faction) {
        this.faction = faction;
    }

    public void load(List<SettlementData> data) {
        byId.clear();
        provinceIndex.clear();
        if (data == null) {
            return;
        }
        for (SettlementData entry : data) {
            try {
                register(new Settlement(entry));
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        rebuildIndex();
    }

    public Settlement getByProvince(int provinceId) {
        return provinceIndex.get(provinceId);
    }

    public Settlement getById(String id) {
        if (id == null) {
            return null;
        }
        return byId.get(id);
    }

    public Collection<Settlement> getAll() {
        return byId.values();
    }

    public List<SettlementData> serialize() {
        List<SettlementData> out = new ArrayList<>();
        for (Settlement s : byId.values()) {
            out.add(s.toData());
        }
        return out;
    }

    public CapitalResult resolveGuildCapital(Player player, Guild guild, int province, String nameOpt) {
        if (guild.isBase()) {
            return CapitalResult.fail("§cUse §e/faction setcapital §cto set the faction capital");
        }
        if (!faction.ownsProvince(province)) {
            return CapitalResult.fail("§cYour faction doesn't own this province!");
        }
        return resolveCapital(player, province, nameOpt, guild.getCapital(), false, false);
    }

    public CapitalResult resolveFactionCapital(Player player, int province, String nameOpt) {
        return applyFactionCapital(player, province, nameOpt);
    }

    public CapitalResult validateFactionCapital(Player player, int province, String nameOpt) {
        if (!faction.ownsProvince(province)) {
            return CapitalResult.fail("§cYour faction doesn't own this province!");
        }
        if (byId.isEmpty() && (nameOpt == null || nameOpt.isBlank())) {
            return CapitalResult.fail(
                    "§cName required to found your capital city: §e/faction setcapital <name>");
        }
        return resolveCapital(player, province, nameOpt, faction.getCapital(), true, true);
    }

    public CapitalResult applyFactionCapital(Player player, int province, String nameOpt) {
        if (!faction.ownsProvince(province)) {
            return CapitalResult.fail("§cYour faction doesn't own this province!");
        }
        if (byId.isEmpty() && (nameOpt == null || nameOpt.isBlank())) {
            return CapitalResult.fail(
                    "§cName required to found your capital city: §e/faction setcapital <name>");
        }
        return resolveCapital(player, province, nameOpt, faction.getCapital(), true, false);
    }

    public boolean requiresFoundingName(int province) {
        return getByProvince(province) == null;
    }

    public void onGuildDepartedCapital(int province) {
        if (province == -1) {
            return;
        }
        Settlement settlement = getByProvince(province);
        if (settlement == null) {
            return;
        }
        if (getPopulation(settlement).isEmpty()) {
            dissolve(settlement);
        }
    }

    public CapitalResult onGuildRelocateTo(Player player, Guild guild, int newCapital, String nameOpt) {
        if (!faction.hasProvince(newCapital)) {
            return CapitalResult.fail("§cYour faction doesn't own this province!");
        }
        return resolveCapital(player, newCapital, nameOpt, -1, true, false);
    }

    private CapitalResult resolveCapital(
            Player player,
            int province,
            String nameOpt,
            int currentCapital,
            boolean fromRelocate,
            boolean dryRun) {
        Province prov = SimpleFactions.getInstance().getProvinceManager().get(province);
        if (province == 0 || prov == null || !prov.isValid()) {
            return CapitalResult.fail("§cThis location has no province!");
        }
        if (prov.isSea()) {
            return CapitalResult.fail("§cYou cannot set a capital on water!");
        }

        if (!fromRelocate && currentCapital != -1 && currentCapital != province) {
            Settlement currentSettlement = getByProvince(currentCapital);
            Settlement targetSettlement = getByProvince(province);
            if (currentSettlement == null
                    || targetSettlement == null
                    || currentSettlement != targetSettlement) {
                return CapitalResult.fail(
                        "§cCapital already set - use relocate to move between cities");
            }
        }

        Settlement existing = getByProvince(province);
        if (existing != null) {
            return CapitalResult.ok(
                    "§aCapital set in §f" + existing.getName(), existing);
        }

        if (nameOpt == null || nameOpt.isBlank()) {
            return CapitalResult.fail("§cName required to found a settlement here");
        }

        if (dryRun) {
            String id = Formatter.formatId(nameOpt);
            if (id.isBlank()) {
                return CapitalResult.fail("§cInvalid settlement name");
            }
            if (byId.containsKey(id)) {
                return CapitalResult.fail("§cA settlement with that id already exists");
            }
            return CapitalResult.ok("§aReady to found settlement");
        }

        return found(
                nameOpt,
                province,
                player.getLocation().getBlockX(),
                player.getLocation().getBlockZ());
    }

    public CapitalResult found(String displayName, int province, int x, int z) {
        String id = Formatter.formatId(displayName);
        if (id.isBlank()) {
            return CapitalResult.fail("§cInvalid settlement name");
        }
        if (byId.containsKey(id)) {
            return CapitalResult.fail("§cA settlement with that id already exists");
        }
        if (getByProvince(province) != null) {
            return CapitalResult.fail("§cThis province already has a settlement");
        }

        String name = StringFormatter.formatHex(Formatter.formatName(displayName));
        Settlement settlement = new Settlement(id, name, province, x, z);

        register(settlement);
        enqueueMapUpdate();

        return CapitalResult.ok(
                "§aFounded settlement §f" + name + " §7(" + id + ")", settlement);
    }

    public Settlement detachOnProvince(int province) {
        Settlement settlement = getByProvince(province);
        if (settlement == null) {
            return null;
        }
        byId.remove(settlement.getId());
        for (int p : settlement.getProvinces()) {
            provinceIndex.remove(p, settlement);
        }
        rebuildIndex();
        return settlement;
    }

    public void acceptTransferred(Settlement settlement) {
        if (settlement == null) {
            return;
        }
        register(settlement);
    }

    public void onProvinceLost(int province) {
        Settlement settlement = getByProvince(province);
        if (settlement == null) {
            return;
        }
        dissolve(settlement);
    }

    public List<Guild> getPopulation(Settlement settlement) {
        List<Guild> population = new ArrayList<>();
        for (Guild g : faction.getGuildHandler().getGuilds()) {
            if (settlement.contains(g.getCapital())) {
                population.add(g);
            }
        }
        return population;
    }

    public void validate() {
        List<Settlement> snapshot = new ArrayList<>(byId.values());
        for (Settlement s : snapshot) {
            s.normalizeToCenterOnly();

            if (!faction.hasProvince(s.getCenterProvince())) {
                dissolve(s);
            }
        }
        rebuildIndex();
    }

    void register(Settlement s) {
        s.normalizeToCenterOnly();
        byId.put(s.getId(), s);
        rebuildIndex();
    }

    void dissolve(Settlement s) {
        String settlementName = s.getName();
        clearCapitalsPointingAt(faction, s);

        byId.remove(s.getId());
        for (int p : s.getProvinces()) {
            provinceIndex.remove(p, s);
        }

        enqueueMapUpdate();

        Player leader = Bukkit.getPlayerExact(faction.getLeader());
        if (leader != null) {
            leader.sendMessage("§cSettlement §f" + settlementName + " §chas been destroyed");
        }
    }

    static void clearCapitalsPointingAt(Faction owner, Settlement destroyed) {
        if (destroyed == null) {
            return;
        }
        int center = destroyed.getCenterProvince();
        clearFactionCapitals(owner, destroyed, center);
        if (FactionManager.factions == null) {
            return;
        }
        for (Faction other : FactionManager.getCopy()) {
            if (other == owner) {
                continue;
            }
            clearFactionCapitals(other, destroyed, center);
        }
    }

    private static void clearFactionCapitals(Faction other, Settlement destroyed, int center) {
        if (other == null) {
            return;
        }
        if (other.getGuildHandler() != null) {
            for (Guild g : other.getGuildHandler().getGuilds()) {
                if (g == null || g.isBase()) {
                    continue;
                }
                if (pointsAtDestroyed(g.getCapital(), destroyed, center)) {
                    g.setCapital(-1, false);
                }
            }
        }
        if (pointsAtDestroyed(other.getCapital(), destroyed, center)) {
            other.setCapital(-1, true, false);
        }
    }

    private static boolean pointsAtDestroyed(int capital, Settlement destroyed, int center) {
        if (capital <= 0) {
            return false;
        }
        return capital == center || destroyed.contains(capital);
    }

    private void enqueueMapUpdate() {
        if (FactionManager.getMap() == null || faction.getRGB() == null) {
            return;
        }
        FactionManager.getMap().enqueue("nation", faction.getRGB());
    }

    private void rebuildIndex() {
        provinceIndex.clear();
        for (Settlement s : byId.values()) {
            s.normalizeToCenterOnly();
            provinceIndex.put(s.getCenterProvince(), s);
        }
    }
}
