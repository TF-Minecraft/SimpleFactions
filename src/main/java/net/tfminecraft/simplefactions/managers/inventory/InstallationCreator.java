package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.war.freeze.PreparationFreeze;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationConstruction;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleFindMessages;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.tlibs.utils.TimeFormatter;

public class InstallationCreator {
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createSummary(Faction f) {
        InstallationHandler handler = f.getInstallationHandler();
        int forts = 0;
        int ports = 0;
        int airports = 0;
        int trainStations = 0;
        double totalUpkeep = 0;
        for (Installation installation : handler.getAll()) {
            totalUpkeep += InstallationConfigLoader.getDailyUpkeep(
                    installation.getKind(), installation.getLevel());
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
                case TRAIN_STATION:
                    trainStations++;
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
        lore.add("§7Forts: §e" + forts + " §7Ports: §e" + ports + " §7Airports: §e" + airports
                + " §7Train Stations: §e" + trainStations);
        lore.add("§7Total Upkeep: §e" + Formatter.formatDouble(totalUpkeep) + "d/day");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createInstallationIcon(Installation installation) {
        ItemStack item = new ItemStack(iconFor(installation.getKind()), 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(installation.getName());
        meta.setLore(createOperationalLore(installation));
        meta.getPersistentDataContainer()
                .set(Keys.STRING_KEY, PersistentDataType.STRING, installation.getId());
        item.setItemMeta(meta);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createConstructionIcon(InstallationConstruction construction, Faction faction) {
        ItemStack item = new ItemStack(Material.YELLOW_CONCRETE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(
                construction.isUpgrade()
                        ? "§eUpgrading " + construction.getName()
                        : "§eBuilding " + construction.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Kind: §e" + construction.getKind().getDisplayName());
        if (construction.isUpgrade()) {
            Installation current = faction.getInstallationHandler().getById(construction.getId());
            if (current != null) {
                lore.add("§7Upgrade to level: §e" + (current.getLevel() + 1));
            }
        }
        lore.add("§7Province: §e" + construction.getProvince());
        lore.add("§7Coords: §e" + construction.getCenterX() + ", " + construction.getCenterZ());
        lore.add("§7Time left: §e" + TimeFormatter.formatTime(construction.getTimeLeft()));
        String frozen = PreparationFreeze.frozenLore(PreparationFreeze.frozenUntil(faction, java.time.Instant.now()));
        if (frozen != null) lore.add(frozen);
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

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createDetailItem(Installation installation) {
        return createDetailItem(installation, null);
    }

    @SuppressWarnings("deprecation")
    public ItemStack createDetailItem(
            Installation installation, InstallationConstruction pendingUpgrade) {
        ItemStack item = new ItemStack(iconFor(installation.getKind()), 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(installation.getName());
        meta.setLore(createOperationalLore(installation, pendingUpgrade));
        meta.getPersistentDataContainer()
                .set(Keys.STRING_KEY, PersistentDataType.STRING, installation.getId());
        item.setItemMeta(meta);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createConstructionDetailItem(InstallationConstruction construction) {
        return createConstructionDetailItem(construction, null);
    }

    public ItemStack createConstructionDetailItem(
            InstallationConstruction construction, Installation current) {
        ItemStack item = new ItemStack(Material.YELLOW_CONCRETE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(
                construction.isUpgrade()
                        ? "§eUpgrading " + construction.getName()
                        : "§eBuilding " + construction.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Kind: §e" + construction.getKind().getDisplayName());
        if (construction.isUpgrade() && current != null) {
            lore.add("§7Upgrade to level: §e" + (current.getLevel() + 1));
        }
        lore.add("§7Province: §e" + construction.getProvince());
        lore.add("§7Coords: §e" + construction.getCenterX() + ", " + construction.getCenterZ());
        lore.add("§7Time left: §e" + TimeFormatter.formatTime(construction.getTimeLeft()));
        lore.add("§7Status: §e" + (construction.isUpgrade() ? "Upgrading" : "Under construction"));
        meta.setLore(lore);
        meta.getPersistentDataContainer()
                .set(Keys.STRING_KEY, PersistentDataType.STRING, construction.getId());
        item.setItemMeta(meta);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createDeconstructButton(String id, boolean pending) {
        return createDeconstructButton(id, pending, false);
    }

    @SuppressWarnings("deprecation")
    public ItemStack createDeconstructButton(String id, boolean pending, boolean upgrade) {
        ItemStack item = new ItemStack(Material.RED_CONCRETE, 1);
        ItemMeta meta = item.getItemMeta();
        if (pending) {
            meta.setDisplayName("§cCancel " + (upgrade ? "Upgrade" : "Construction"));
            meta.setLore(List.of("§7Click to cancel this " + (upgrade ? "upgrade" : "build")));
        } else {
            meta.setDisplayName("§cDeconstruct");
            meta.setLore(List.of("§7Click to deconstruct this installation"));
        }
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    @SuppressWarnings("deprecation")
    public ItemStack createCancelUpgradeButton(
            Installation installation, InstallationConstruction upgrade) {
        ItemStack item = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§cCancel Upgrade");
        meta.setLore(List.of(
                "§7Target level: §e" + (installation.getLevel() + 1),
                "§7Time left: §e" + TimeFormatter.formatTime(upgrade.getTimeLeft()),
                "§eClick to cancel"));
        meta.getPersistentDataContainer()
                .set(Keys.STRING_KEY, PersistentDataType.STRING, upgrade.getId());
        item.setItemMeta(meta);
        return item;
    }

    private List<String> createOperationalLore(Installation installation) {
        return createOperationalLore(installation, null);
    }

    private List<String> createOperationalLore(
            Installation installation, InstallationConstruction pendingUpgrade) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Kind: §e" + installation.getKind().getDisplayName());
        int maximum = InstallationConfigLoader.getMaximumLevel(installation.getKind());
        if (maximum > 1) {
            lore.add("§7Level: §e" + installation.getLevel() + "/" + maximum);
        }
        if (pendingUpgrade != null && pendingUpgrade.isUpgrade()) {
            lore.add("§7Upgrading to level " + (installation.getLevel() + 1) + ": §e"
                    + TimeFormatter.formatTime(pendingUpgrade.getTimeLeft()));
        }
        lore.add("§7Province: §e" + installation.getProvince());
        lore.add(
                "§7Coords: §e" + installation.getCenterX() + ", " + installation.getCenterZ());
        lore.add(
                "§7Upkeep: §e"
                        + Formatter.formatDouble(
                                InstallationConfigLoader.getDailyUpkeep(
                                        installation.getKind(), installation.getLevel()))
                        + "d/day");
        for (var slot : InstallationConfigLoader
                .getCategorySlots(installation.getKind(), installation.getLevel())
                .entrySet()) {
            lore.add("§7" + categoryDisplayName(slot.getKey()) + " slots: §e" + slot.getValue());
        }
        return lore;
    }

    @SuppressWarnings("deprecation")
    public ItemStack createUpgradeButton(Installation installation) {
        int nextLevel = installation.getLevel() + 1;
        ItemStack item = new ItemStack(Material.ANVIL, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eUpgrade");
        List<String> lore = new ArrayList<>();
        lore.add("§7Next level: §e" + nextLevel);
        lore.add("§7Upkeep: §e"
                + Formatter.formatDouble(InstallationConfigLoader.getDailyUpkeep(
                        installation.getKind(), nextLevel))
                + "d/day");
        lore.add("§7Build time: §e"
                + TimeFormatter.formatTime(InstallationConfigLoader.getConstructionTimeSeconds(
                        installation.getKind(), nextLevel)));
        for (var slot : InstallationConfigLoader
                .getCategorySlots(installation.getKind(), nextLevel)
                .entrySet()) {
            lore.add("§7" + categoryDisplayName(slot.getKey()) + " slots: §e" + slot.getValue());
        }
        lore.add("§eClick to upgrade");
        meta.setLore(lore);
        meta.getPersistentDataContainer()
                .set(Keys.STRING_KEY, PersistentDataType.STRING, installation.getId());
        item.setItemMeta(meta);
        return item;
    }

    static String categoryDisplayName(String categoryId) {
        String configuredName = VehiclesConfigLoader.getCategoryDisplayName(categoryId);
        if (configuredName != null) {
            return configuredName;
        }
        return categoryId.replace('_', ' ');
    }

    private Material iconFor(InstallationKind kind) {
        return kind == InstallationKind.TRAIN_STATION ? Material.MINECART : Material.GREEN_CONCRETE;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
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
            lore.add("§cClick to take as your vehicle");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer()
                .set(Keys.STRING_KEY, PersistentDataType.STRING, record.getVehicleUuid());
        item.setItemMeta(meta);
        return item;
    }
}
