package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationConstruction;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleFindMessages;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.Plugins.TLibs.Utils.TimeFormatter;

public class InstallationCreator {
    public ItemStack createSummary(Faction f) {
        InstallationHandler handler = f.getInstallationHandler();
        int forts = 0;
        int ports = 0;
        int airports = 0;
        double totalUpkeep = 0;
        for (Installation installation : handler.getAll()) {
            totalUpkeep += InstallationConfigLoader.getDailyUpkeep(installation.getKind());
            switch (installation.getKind()) {
                case FORT:
                    forts++;
                    break;
                case PORT:
                    ports++;
                    break;
                case AIRPORT:
                    airports++;
                    break;
                default:
                    break;
            }
        }

        ItemStack item = new ItemStack(Material.GREEN_CONCRETE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#706964Installations"));
        List<String> lore = new ArrayList<>();
        lore.add("§7Total: §e" + handler.getAll().size());
        lore.add("§7Forts: §e" + forts + " §7Ports: §e" + ports + " §7Airports: §e" + airports);
        lore.add("§7Total Upkeep: §e" + Formatter.formatDouble(totalUpkeep) + "d/day");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createInstallationIcon(Installation installation) {
        ItemStack item = new ItemStack(Material.GREEN_CONCRETE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(installation.getName());
        meta.setLore(createOperationalLore(installation));
        meta.getPersistentDataContainer()
                .set(Keys.STRING_KEY, PersistentDataType.STRING, installation.getId());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createConstructionIcon(InstallationConstruction construction, Faction faction) {
        ItemStack item = new ItemStack(Material.YELLOW_CONCRETE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eBuilding " + construction.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Kind: §e" + construction.getKind().getCommandName());
        lore.add("§7Province: §e" + construction.getProvince());
        lore.add("§7Coords: §e" + construction.getCenterX() + ", " + construction.getCenterZ());
        lore.add("§7Time left: §e" + TimeFormatter.formatTime(construction.getTimeLeft()));
        lore.add("§cClick to cancel");
        meta.setLore(lore);
        meta.getPersistentDataContainer()
                .set(Keys.STRING_KEY, PersistentDataType.STRING, construction.getId());
        meta.getPersistentDataContainer()
                .set(Keys.QUEUE_CANCEL, PersistentDataType.STRING,
                        QueueCancelPayload.installation(faction.getId(), construction.getId()));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDetailItem(Installation installation) {
        ItemStack item = new ItemStack(Material.GREEN_CONCRETE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(installation.getName());
        meta.setLore(createOperationalLore(installation));
        meta.getPersistentDataContainer()
                .set(Keys.STRING_KEY, PersistentDataType.STRING, installation.getId());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createConstructionDetailItem(InstallationConstruction construction) {
        ItemStack item = new ItemStack(Material.YELLOW_CONCRETE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eBuilding " + construction.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Kind: §e" + construction.getKind().getCommandName());
        lore.add("§7Province: §e" + construction.getProvince());
        lore.add("§7Coords: §e" + construction.getCenterX() + ", " + construction.getCenterZ());
        lore.add("§7Time left: §e" + TimeFormatter.formatTime(construction.getTimeLeft()));
        lore.add("§7Status: §eUnder construction");
        meta.setLore(lore);
        meta.getPersistentDataContainer()
                .set(Keys.STRING_KEY, PersistentDataType.STRING, construction.getId());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDeconstructButton(String id, boolean pending) {
        ItemStack item = new ItemStack(Material.RED_CONCRETE, 1);
        ItemMeta meta = item.getItemMeta();
        if (pending) {
            meta.setDisplayName("§cCancel Construction");
            meta.setLore(List.of("§7Click to cancel this build"));
        } else {
            meta.setDisplayName("§cDeconstruct");
            meta.setLore(List.of("§7Click to deconstruct this installation"));
        }
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private List<String> createOperationalLore(Installation installation) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Kind: §e" + installation.getKind().getCommandName());
        lore.add("§7Province: §e" + installation.getProvince());
        lore.add(
                "§7Coords: §e" + installation.getCenterX() + ", " + installation.getCenterZ());
        lore.add(
                "§7Upkeep: §e"
                        + Formatter.formatDouble(
                                InstallationConfigLoader.getDailyUpkeep(installation.getKind()))
                        + "d/day");
        return lore;
    }

    public ItemStack createBerthedVehicleIcon(
            PlayerVehicleRecord record, Optional<Location> location, boolean leader) {
        ItemStack item = new ItemStack(Material.MINECART, 1);
        ItemMeta meta = item.getItemMeta();
        String displayName = VehicleFindMessages.resolveVehicleName(record.getVehicleUuid());
        meta.setDisplayName("§e" + displayName);
        List<String> lore = new ArrayList<>();
        lore.add("§7Type: §f" + record.getVehicleTypeId());
        lore.add("§7Location: " + VehicleFindMessages.formatLocation(location));
        if (leader) {
            lore.add("§cClick to unberth");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer()
                .set(Keys.STRING_KEY, PersistentDataType.STRING, record.getVehicleUuid());
        item.setItemMeta(meta);
        return item;
    }
}
