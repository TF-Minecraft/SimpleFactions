package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationConstruction;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.vehicles.berth.InstallationVehicleUnberthService;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.VehicleFramework.VehicleFramework;

public class InstallationView {
    public InventoryManager inv;
    public InstallationCreator creator = new InstallationCreator();

    public InstallationView(InventoryManager inv) {
        this.inv = inv;
    }

    public void installationsView(Inventory inventory, Player player, Faction f, boolean open) {
        if (open) {
            inventory =
                    SimpleFactions.plugin
                            .getServer()
                            .createInventory(
                                    new SFInventoryHolder(f.getId(), SFGUI.INSTALLATIONS_VIEW),
                                    54,
                                    "§7Installations View");
        }

        InstallationHandler handler = f.getInstallationHandler();
        inventory.setItem(10, creator.createSummary(f));

        List<Installation> installations = new ArrayList<>(handler.getAll());
        installations.sort(
                Comparator.comparing((Installation installation) -> installation.getKind().name())
                        .thenComparing(Installation::getId));

        for (int index = 0; index < installations.size(); index++) {
            int slot = index + 12;
            if (slot > 44) {
                break;
            }
            inventory.setItem(slot, creator.createInstallationIcon(installations.get(index)));
        }
        for (int slot = 12 + installations.size(); slot <= 44; slot++) {
            inventory.setItem(slot, new ItemStack(Material.AIR, 1));
        }

        InstallationConstruction pending = handler.getPendingConstruction();
        if (pending != null) {
            inventory.setItem(39, creator.createConstructionIcon(pending, f));
        } else {
            inventory.setItem(39, new ItemStack(Material.AIR, 1));
        }

        inventory.setItem(53, inv.createBackButton(SFGUI.INSTALLATIONS_VIEW));
        if (open) {
            player.openInventory(inventory);
        }
    }

    public void installationDetailView(Player player, Faction f, String installationId) {
        installationDetailView(player, f, installationId, null);
    }

    public void installationDetailView(Player player, Faction f, String installationId, Inventory inventory) {
        InstallationHandler handler = f.getInstallationHandler();
        InstallationConstruction pending = handler.getPendingConstruction();
        boolean isPending =
                pending != null && pending.getId().equalsIgnoreCase(installationId);
        Installation installation = handler.getById(installationId);

        if (!isPending && installation == null) {
            if (inventory == null) {
                player.sendMessage("§cNo installation with id §f" + installationId);
            }
            installationsView(null, player, f, true);
            return;
        }

        boolean open = inventory == null;
        if (open) {
            inventory =
                    SimpleFactions.plugin
                            .getServer()
                            .createInventory(
                                    new SFInventoryHolder(f.getId(), SFGUI.INSTALLATION_DETAIL_VIEW, installationId),
                                    54,
                                    "§7Installation Details");
        }
        inventory.clear();

        boolean leader = f.getLeader().equalsIgnoreCase(player.getName());
        if (isPending) {
            inventory.setItem(49, creator.createConstructionDetailItem(pending));
        } else {
            inventory.setItem(49, creator.createDetailItem(installation));
            List<PlayerVehicleRecord> berthed =
                    SimpleFactions.getVehicleRegistry().getByInstallationId(installation.getId());
            berthed.sort(Comparator.comparing(PlayerVehicleRecord::getVehicleTypeId));
            for (int index = 0; index < berthed.size() && index < 45; index++) {
                PlayerVehicleRecord record = berthed.get(index);
                Optional<Location> location =
                        VehicleFramework.getVehicleManager()
                                .getOfflineLocation(record.getVehicleUuid());
                inventory.setItem(
                        index,
                        creator.createBerthedVehicleIcon(record, location, leader));
            }
        }

        if (leader) {
            inventory.setItem(
                    11, creator.createDeconstructButton(installationId, isPending));
        } else {
            inventory.setItem(11, new ItemStack(Material.AIR, 1));
        }

        inventory.setItem(53, inv.createBackButton(SFGUI.INSTALLATION_DETAIL_VIEW));
        if (open) player.openInventory(inventory);
    }

    public void click(InventoryClickEvent event, Inventory inventory, Player player) {
        event.setCancelled(true);
        if (!(inventory.getHolder() instanceof SFInventoryHolder holder)) {
            return;
        }

        Faction f = FactionManager.getByString(holder.getId());
        if (f == null) {
            return;
        }

        if (holder.getType() == SFGUI.INSTALLATIONS_VIEW) {
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta()) {
                return;
            }
            ItemMeta meta = item.getItemMeta();
            String queuePayload = meta.getPersistentDataContainer().get(Keys.QUEUE_CANCEL, PersistentDataType.STRING);
            if (queuePayload != null) {
                if (!f.getLeader().equalsIgnoreCase(player.getName())) {
                    return;
                }
                inv.openQueueCancelConfirm(player, f, queuePayload, "§eCancel construction?");
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
            if (!meta.getPersistentDataContainer().has(Keys.STRING_KEY, PersistentDataType.STRING)) {
                return;
            }
            String id =
                    meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
            installationDetailView(player, f, id);
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
            return;
        }

        if (holder.getType() == SFGUI.INSTALLATION_DETAIL_VIEW) {
            int slot = event.getSlot();
            if (slot >= 0 && slot <= 44) {
                handleBerthedVehicleClick(event, inventory, player, f);
                return;
            }
            if (slot != 11) {
                return;
            }
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta()) {
                return;
            }
            ItemMeta meta = item.getItemMeta();
            if (!meta.getPersistentDataContainer().has(Keys.STRING_KEY, PersistentDataType.STRING)) {
                return;
            }
            String id =
                    meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
            inv.confirming.put(player, f);
            inv.installationConfirmFromCommand.put(player, false);
            inv.confirmView(player, f, "installation", id);
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
        }
    }

    private void handleBerthedVehicleClick(
            InventoryClickEvent event, Inventory inventory, Player player, Faction faction) {
        if (!faction.getLeader().equalsIgnoreCase(player.getName())) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(Keys.STRING_KEY, PersistentDataType.STRING)) {
            return;
        }
        String vehicleUuid =
                meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
        ItemStack detailItem = inventory.getItem(49);
        if (detailItem == null || !detailItem.hasItemMeta()) {
            return;
        }
        ItemMeta detailMeta = detailItem.getItemMeta();
        if (!detailMeta.getPersistentDataContainer().has(Keys.STRING_KEY, PersistentDataType.STRING)) {
            return;
        }
        String installationId =
                detailMeta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
        Installation installation = faction.getInstallationHandler().getById(installationId);
        if (installation == null) {
            return;
        }

        InstallationVehicleUnberthService service =
                SimpleFactions.getInstance().getInstallationVehicleUnberthService();
        InstallationVehicleUnberthService.UnberthResult result =
                service.unberth(faction, player.getName(), installation, vehicleUuid);
        player.sendMessage(InstallationVehicleUnberthService.messageFor(result));
        if (result == InstallationVehicleUnberthService.UnberthResult.OK) {
            installationDetailView(player, faction, installationId);
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
        }
    }
}
