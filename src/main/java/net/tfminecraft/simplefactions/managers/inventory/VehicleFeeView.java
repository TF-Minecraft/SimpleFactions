package net.tfminecraft.simplefactions.managers.inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.proposal.FeeChange;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.VehicleFeeHandler;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.vehicles.fees.VfBuildersCatalog;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/**
 * Fee proposal menus: pick the tax or fee, then all vehicles or a VFBuilders category,
 * then a vehicle in it, then type the new rate in chat.
 */
public class VehicleFeeView {
    private static final String GENERAL = "*";

    private final InventoryManager inv;

    public VehicleFeeView(InventoryManager inv) {
        this.inv = inv;
    }

    /** Whether the faction's vehicle tax law allows any charge to be proposed. */
    public static boolean anyChargeable(Faction f) {
        for (FeeKind kind : FeeKind.values()) {
            if (f.getVehicleFeeHandler().canCharge(kind)) {
                return true;
            }
        }
        return false;
    }

    public void feeProposalView(Player player, Faction f, Inventory i) {
        boolean open = i == null;
        if (open) {
            i = SimpleFactions.plugin.getServer().createInventory(
                    new SFInventoryHolder(f.getId(), SFGUI.FEE_PROPOSAL_VIEW), 9, "§7Select Vehicle Fee");
        }
        i.clear();
        int x = 0;
        for (FeeKind kind : FeeKind.values()) {
            if (!f.getVehicleFeeHandler().canCharge(kind)) {
                continue;
            }
            i.setItem(x, kindItem(f, kind));
            x++;
        }
        i.setItem(8, inv.createBackButton(SFGUI.FEE_PROPOSAL_VIEW));
        if (open) {
            player.openInventory(i);
        }
    }

    public void feeCategoryView(Player player, Faction f, FeeKind kind, Inventory i) {
        boolean open = i == null;
        if (open) {
            i = SimpleFactions.plugin.getServer().createInventory(
                    new SFInventoryHolder(f.getId(), SFGUI.FEE_CATEGORY_VIEW, kind.name()), 54, "§7Select Vehicles");
        }
        i.clear();
        i.setItem(0, generalItem(player, f, kind));
        int x = 1;
        for (VfBuildersCatalog.Category category : VfBuildersCatalog.categories()) {
            if (x >= 53) {
                break;
            }
            i.setItem(x, categoryItem(f, kind, category));
            x++;
        }
        i.setItem(53, inv.createBackButton(SFGUI.FEE_CATEGORY_VIEW));
        if (open) {
            player.openInventory(i);
        }
    }

    public void feeVehicleView(Player player, Faction f, FeeKind kind, String categoryId, Inventory i) {
        VfBuildersCatalog.Category category = VfBuildersCatalog.category(categoryId);
        if (category == null) {
            feeCategoryView(player, f, kind, null);
            return;
        }
        boolean open = i == null;
        if (open) {
            i = SimpleFactions.plugin.getServer().createInventory(
                    new SFInventoryHolder(f.getId(), SFGUI.FEE_VEHICLE_VIEW, kind.name() + ":" + category.id()),
                    54, "§7Select Vehicle");
        }
        i.clear();
        int x = 0;
        for (VfBuildersCatalog.Entry entry : category.vehicles()) {
            if (x >= 53) {
                break;
            }
            i.setItem(x, vehicleItem(player, f, kind, entry));
            x++;
        }
        i.setItem(53, inv.createBackButton(SFGUI.FEE_VEHICLE_VIEW));
        if (open) {
            player.openInventory(i);
        }
    }

    /** Where the back button of each fee menu leads. */
    public void back(Player player, Faction f, SFInventoryHolder h) {
        switch (h.getType()) {
            case FEE_PROPOSAL_VIEW -> inv.governmentView.proposalView(player, f, null);
            case FEE_CATEGORY_VIEW -> feeProposalView(player, f, null);
            case FEE_VEHICLE_VIEW -> {
                FeeKind kind = parseKind(h.getSecondaryId());
                if (kind == null) {
                    feeProposalView(player, f, null);
                } else {
                    feeCategoryView(player, f, kind, null);
                }
            }
            default -> {
            }
        }
    }

    public void click(InventoryClickEvent e, Inventory inventory, Player p) {
        e.setCancelled(true);
        if (!(inventory.getHolder() instanceof SFInventoryHolder h)) {
            return;
        }
        ItemStack item = e.getCurrentItem();
        if (item == null || item.getItemMeta() == null) {
            return;
        }
        Faction f = FactionManager.getByString(h.getId());
        if (f == null) {
            return;
        }
        String key = item.getItemMeta().getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
        if (key == null) {
            return;
        }
        switch (h.getType()) {
            case FEE_PROPOSAL_VIEW -> {
                FeeKind kind = parseKind(key);
                if (kind != null) {
                    feeCategoryView(p, f, kind, null);
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                }
            }
            case FEE_CATEGORY_VIEW -> {
                FeeKind kind = parseKind(h.getSecondaryId());
                if (kind == null) {
                    return;
                }
                if (GENERAL.equals(key)) {
                    startInput(p, f, kind, null);
                } else {
                    feeVehicleView(p, f, kind, key, null);
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                }
            }
            case FEE_VEHICLE_VIEW -> {
                String secondary = h.getSecondaryId();
                FeeKind kind = secondary == null ? null : parseKind(secondary.split(":", 2)[0]);
                if (kind != null) {
                    startInput(p, f, kind, key);
                }
            }
            default -> {
            }
        }
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private void startInput(Player p, Faction f, FeeKind kind, String vehicleTypeId) {
        if (!canPropose(p, f, kind, vehicleTypeId)) {
            p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendMessage("§cAnother proposal is active for this fee, or you cannot propose right now.");
            return;
        }
        inv.setChangingFee(f, p, kind, vehicleTypeId);
        String target = vehicleTypeId == null ? "all vehicles" : vehicleTypeId;
        String unit = kind.isPercent() ? "a percentage of upkeep" : "a multiple of upkeep";
        p.sendTitle("§a" + kind.getDisplayName(), "§eType a new rate for " + target + " §ein chat.", 20, 40, 20);
        p.sendMessage(StringFormatter.formatHex("§eType the new " + kind.getDisplayName() + " for " + target
                + " as " + unit + " §7(" + rangeText(f.getVehicleFeeHandler(), kind) + "§7)§e, or §ccancel§e."));
        p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        p.closeInventory();
    }

    private static boolean canPropose(Player p, Faction f, FeeKind kind, String vehicleTypeId) {
        Government gov = f.getGovernment();
        Proposal proposal = new Proposal(p.getName(), gov);
        proposal.setFeeProposal(new FeeChange(kind, vehicleTypeId, 0.0));
        return gov.canProposeOrStartMovement(p) && gov.canBeProposed(proposal);
    }

    // Items

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private ItemStack kindItem(Faction f, FeeKind kind) {
        VehicleFeeHandler handler = f.getVehicleFeeHandler();
        Material material = switch (kind) {
            case VEHICLE_TAX -> Material.GOLD_INGOT;
            case REGISTRATION_FEE -> Material.WRITABLE_BOOK;
            case TRANSFER_FEE -> Material.PAPER;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#93c9a7" + kind.getDisplayName()));
        List<String> lore = new ArrayList<>();
        lore.addAll(describe(kind));
        lore.add("");
        lore.add(StringFormatter.formatHex("#525d5dGeneral Rate: #e3d5a1" + kind.formatRate(handler.getRate(kind))));
        lore.add(StringFormatter.formatHex("#525d5dAllowed: " + rangeText(handler, kind)));
        int own = handler.getTypeRates(kind).size();
        if (own > 0) {
            lore.add(StringFormatter.formatHex("#525d5dVehicles with their own rate: #e3d5a1" + own));
        }
        lore.add(StringFormatter.formatHex("#28ed70Click to choose vehicles"));
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, kind.name());
        item.setItemMeta(m);
        return item;
    }

    private static List<String> describe(FeeKind kind) {
        List<String> lore = new ArrayList<>();
        switch (kind) {
            case VEHICLE_TAX -> {
                lore.add(StringFormatter.formatHex("#b8ae61Charged daily with vehicle upkeep,"));
                lore.add(StringFormatter.formatHex("#b8ae61as a percentage of it."));
            }
            case REGISTRATION_FEE -> {
                lore.add(StringFormatter.formatHex("#b8ae61Charged once when a member starts"));
                lore.add(StringFormatter.formatHex("#b8ae61building a vehicle, as a multiple"));
                lore.add(StringFormatter.formatHex("#b8ae61of its daily upkeep."));
            }
            case TRANSFER_FEE -> {
                lore.add(StringFormatter.formatHex("#b8ae61Charged once to a member who hands"));
                lore.add(StringFormatter.formatHex("#b8ae61a vehicle to another player, as a"));
                lore.add(StringFormatter.formatHex("#b8ae61multiple of its daily upkeep."));
            }
        }
        lore.add(StringFormatter.formatHex("#767a77Paid from members' banks. The leader is exempt."));
        return lore;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private ItemStack generalItem(Player p, Faction f, FeeKind kind) {
        VehicleFeeHandler handler = f.getVehicleFeeHandler();
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#93c9a7All Vehicles"));
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#525d5dGeneral Rate: #e3d5a1" + kind.formatRate(handler.getRate(kind))));
        lore.add(StringFormatter.formatHex("#525d5dAllowed: " + rangeText(handler, kind)));
        lore.add(StringFormatter.formatHex("#767a77Applies to every vehicle without its own rate."));
        lore.add(proposeLine(p, f, kind, null));
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, GENERAL);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private ItemStack categoryItem(Faction f, FeeKind kind, VfBuildersCatalog.Category category) {
        ItemStack item = category.icon() != null ? category.icon() : new ItemStack(Material.CHEST);
        ItemMeta m = item.getItemMeta();
        if (!m.hasDisplayName()) {
            m.setDisplayName(StringFormatter.formatHex("#93c9a7" + Formatter.formatName(category.id())));
        }
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#525d5dVehicles: #e3d5a1" + category.vehicles().size()));
        int own = 0;
        for (VfBuildersCatalog.Entry entry : category.vehicles()) {
            if (f.getVehicleFeeHandler().hasTypeRate(kind, entry.vehicleTypeId())) {
                own++;
            }
        }
        if (own > 0) {
            lore.add(StringFormatter.formatHex("#525d5dWith their own rate: #e3d5a1" + own));
        }
        lore.add(StringFormatter.formatHex("#28ed70Click to view vehicles"));
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, category.id());
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private ItemStack vehicleItem(Player p, Faction f, FeeKind kind, VfBuildersCatalog.Entry entry) {
        VehicleFeeHandler handler = f.getVehicleFeeHandler();
        String typeId = entry.vehicleTypeId();
        ItemStack item = entry.icon() != null ? entry.icon() : new ItemStack(Material.MINECART);
        ItemMeta m = item.getItemMeta();
        if (!m.hasDisplayName()) {
            m.setDisplayName(StringFormatter.formatHex("#93c9a7" + Formatter.formatName(typeId)));
        }
        double upkeep = VehiclesConfigLoader.getUpkeep(typeId);
        double rate = handler.getRate(kind, typeId);
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#525d5dUpkeep: #e3d5a1" + Formatter.formatMoney(upkeep) + "d"));
        if (handler.hasTypeRate(kind, typeId)) {
            lore.add(StringFormatter.formatHex("#525d5dOwn Rate: #e3d5a1" + kind.formatRate(rate)));
            lore.add(StringFormatter.formatHex("#3f4040(#767a77General Rate: #928d7a"
                    + kind.formatRate(handler.getRate(kind)) + "#3f4040)"));
        } else {
            lore.add(StringFormatter.formatHex("#525d5dRate: #e3d5a1" + kind.formatRate(rate) + " §8(general)"));
        }
        lore.add(StringFormatter.formatHex("#525d5dCharge: #e3d5a1"
                + Formatter.formatMoney(kind.amount(handler.getChargedRate(kind, typeId), upkeep)) + "d"
                + (kind.isPercent() ? " per day" : "")));
        lore.add(proposeLine(p, f, kind, typeId));
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, typeId);
        item.setItemMeta(m);
        return item;
    }

    private static String proposeLine(Player p, Faction f, FeeKind kind, String vehicleTypeId) {
        return canPropose(p, f, kind, vehicleTypeId)
                ? StringFormatter.formatHex("#28ed70Click to propose a change")
                : StringFormatter.formatHex("#89504eAnother proposal is active for this target.");
    }

    private static String rangeText(VehicleFeeHandler handler, FeeKind kind) {
        Bracket bracket = handler.getBracket(kind);
        if (bracket == null) {
            return "#e3d5a1none";
        }
        return "#e3d5a1" + kind.formatRate(handler.getMin(kind)) + " §7- #e3d5a1" + kind.formatRate(handler.getMax(kind));
    }

    private static FeeKind parseKind(String name) {
        if (name == null) {
            return null;
        }
        try {
            return FeeKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
