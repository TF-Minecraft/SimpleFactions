package net.tfminecraft.simplefactions.managers.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.espionage.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/** Read-only versions of the normal menus: original slots and template icons, cached intelligence only. */
@SuppressWarnings("deprecation")
public final class ReportedMenus {
    public static final Set<SFGUI> READABLE = java.util.EnumSet.of(SFGUI.MILITARY_VIEW, SFGUI.GOVERNMENT_VIEW,
            SFGUI.INSTALLATIONS_VIEW, SFGUI.INSTALLATION_DETAIL_VIEW, SFGUI.LAW_VIEW, SFGUI.LAW_SELECT,
            SFGUI.TAX_VIEW, SFGUI.TAX_VIEW_SPECIFIC, SFGUI.UPGRADE_VIEW, SFGUI.COMPANY_VIEW,
            SFGUI.COUNCIL_VIEW, SFGUI.PROPOSALS, SFGUI.MOVEMENT_LIST, SFGUI.STABILITY_VIEW,
            SFGUI.LOAN_MAIN_VIEW, SFGUI.LOANS_GIVEN_VIEW, SFGUI.LOANS_TAKEN_VIEW,
            SFGUI.COMPANY_SLOTS_VIEW, SFGUI.COMPANY_ROSTER_VIEW, SFGUI.COMPANY_UPGRADE_VIEW, SFGUI.CONTRACT_LIST_VIEW);
    private ReportedMenus() {}

    private static void prepare(Inventory inventory) {
        inventory.clear();
        if (inventory.getHolder() instanceof SFInventoryHolder holder) holder.markReported();
    }

    public static ItemStack mask(ItemStack template, String title, List<String> lore) {
        ItemStack item = template.clone();
        var meta = item.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex(title));
        meta.setLore(lore.stream().map(StringFormatter::formatHex).toList());
        // Template icons carry appearance only. Report items never carry mutation payloads.
        for (var key : new java.util.HashSet<>(meta.getPersistentDataContainer().getKeys())) meta.getPersistentDataContainer().remove(key);
        item.setItemMeta(meta);
        return item;
    }

    public static String guildLeader(Player viewer, Guild guild) {
        if (EspionageService.canViewExact(viewer, guild.getFaction()) || guild.getFaction().isLeader(guild.getLeader()))
            return CharacterNames.display(viewer, guild.getLeader(), guild.getFaction());
        var report = EspionageService.report(viewer, guild.getFaction());
        var names = report == null ? List.<String>of() : report.details("guild-leader", "guild-leader:" + guild.getId());
        return names.isEmpty() ? "\u00a77Unknown" : names.getFirst();
    }

    /** Vehicles berthed at a foreign installation, from the daily report. */
    public static ItemStack berthedVehicles(IntelligenceReport report, String installationId) {
        return EspionageView.item(Material.MINECART, "Berthed vehicles",
                VehicleIntelligence.lore(report, installationId).toArray(String[]::new));
    }

    public static ItemStack branch(Player viewer, Guild guild, net.tfminecraft.simplefactions.guild.branch.Branch branch) {
        var report = EspionageService.report(viewer, guild.getFaction());
        List<String> lore = new ArrayList<>();
        lore.add("#575150[#d6cf69LVL " + IntelligenceLedger.value(report, guild, "Branch:" + branch.getId(), "") + "#575150]");
        lore.add(""); lore.addAll(branch.getDescription()); lore.add("");
        lore.add("#a6c793Effects:");
        var levels = report == null ? null : report.estimate(IntelligenceLedger.key(guild, "Branch:" + branch.getId()));
        for (var key : branch.getModifierKeys()) {
            var modifier = branch.getModifier(key);
            if (modifier == null) continue;
            String estimate = "\u00a77Unknown";
            if (levels != null) {
                double first = modifier.getCurrent((int) Math.min(Integer.MAX_VALUE, levels.lower()));
                double second = modifier.getCurrent((int) Math.min(Integer.MAX_VALUE, levels.upper()));
                estimate = (key.isPositive() ? "#4fd945" : "#cf493a") + (levels.isExact() ? first
                        : Math.floor(Math.min(first, second)) + " to " + Math.ceil(Math.max(first, second)));
            }
            lore.add("\u00a7f - " + key.getName() + ": " + estimate);
        }
        return mask(branch.getIconItem(), branch.getName(), lore);
    }

    public static void military(Inventory inventory, Player viewer, Faction faction, InventoryManager manager) {
        var report = EspionageService.report(viewer, faction);
        prepare(inventory);
        inventory.setItem(4, EspionageView.reportHeader(viewer, report));
        inventory.setItem(10, EspionageView.factionItem(viewer, faction, net.tfminecraft.simplefactions.enums.MenuItemType.MILITARY));
        int slot = 12;
        for (var regiment : faction.getMilitary().getRegiments()) {
            if (slot > 38) break;
            List<String> lore = new ArrayList<>();
            lore.add("\u00a77" + (regiment.isLevy() ? "Total Levies: " : "Current Slots: ")
                    + (report == null ? "Unknown" : report.display("Regiment:" + regiment.getId() + (regiment.isLevy() ? ":Levies" : ":Soldiers"))));
            lore.add("\u00a77Current Upkeep: Unknown"); lore.add(""); lore.addAll(regiment.getDescription());
            inventory.setItem(slot++, mask(regiment.getIcon(), regiment.getName(), lore));
        }
        queue(inventory, report, "training", "training", "Army training");
        inventory.setItem(49, EspionageView.item(Material.MINECART, "Faction Vehicles", VehicleIntelligence.lore(report, null).toArray(String[]::new)));
        inventory.setItem(53, manager.createBackButton(SFGUI.MILITARY_VIEW));
    }

    private static void queue(Inventory inventory, IntelligenceReport report, String field, String key, String label) {
        boolean known = report != null && report.allows(field);
        List<String> names = known ? report.details(field, key) : List.of();
        for (int index = 0; index < 3; index++) {
            if (known && index >= names.size()) continue;
            inventory.setItem(39 + index, EspionageView.item(Material.CLOCK,
                    known ? (index == 0 ? (field.equals("training") ? "Training " : "Upgrading ") : "Queued: ") + names.get(index) : label,
                    known && field.equals("training") ? "\u00a77Time remaining: " + report.display("Training:" + index) + " seconds"
                            : known ? "\u00a77Time remaining: Unknown" : "\u00a77Queue status: Unknown"));
        }
    }

    public static void upgrades(Inventory inventory, Player viewer, Guild guild, InventoryManager manager) {
        var report = EspionageService.report(viewer, guild.getFaction());
        prepare(inventory);
        int slot = 9;
        for (var upgrade : guild.getUpgrades()) {
            if (slot > 17) break;
            List<String> lore = new ArrayList<>();
            lore.add("#575150Level: " + IntelligenceLedger.value(report, guild, "Upgrade:" + upgrade.getId(), ""));
            lore.addAll(upgrade.getDescription()); lore.add("\u00a77Effects: Unknown");
            inventory.setItem(slot++, mask(upgrade.getIconItem(), upgrade.getName(), lore));
        }
        queue(inventory, report, "upgrades", "upgrading:" + guild.getId(), "Upgrade queue");
        inventory.setItem(53, manager.createBackButton(SFGUI.UPGRADE_VIEW));
    }

    public static void government(Inventory inventory, Player viewer, Faction faction, InventoryManager manager) {
        prepare(inventory);
        var report = EspionageService.report(viewer, faction);
        inventory.setItem(10, mask(new ItemStack(Material.WRITABLE_BOOK), "#93c9a7Government:", List.of(
                "#9c9775" + faction.getRulerTitle() + ": #c2bea7" + CharacterNames.display(viewer, faction.getLeader(), faction),
                "#85c265Administrative Power: " + (report == null ? "Unknown" : report.display("Administrative power")),
                "", "#b8ae61Ruling System: #d4c9ae" + faction.getGovernmentString())));
        inventory.setItem(11, mask(IconGetter.getIconOrDefault("stability", Material.GREEN_DYE),
                IntelligenceReport.stabilityState(report) + ": " + IntelligenceReport.stabilityRange(report), List.of("\u00a77Modifiers: Unknown")));
        inventory.setItem(12, mask(IconGetter.getIconOrDefault("overlord", Material.BLACK_DYE), "#d4c9aeLegitimacy: "
                + (report == null ? "Unknown" : report.display("Legitimacy")), List.of("\u00a77Effects: Unknown")));
        inventory.setItem(13, EspionageView.item(Material.PAPER, "Council:", "\u00a77Council size: "
                + (report == null ? "Unknown" : report.display("Council size")), "\u00a77Council members: Unknown"));
        if (faction.getGovernment().hasElections()) inventory.setItem(14, EspionageView.item(Material.PAPER, "Elections", "\u00a77Election details: Unknown"));
        inventory.setItem(15, EspionageView.item(Material.WRITABLE_BOOK, "Proposals", "\u00a77Foreign proposals cannot be submitted."));
        inventory.setItem(24, EspionageView.item(Material.PAPER, "Current Proposals", "\u00a77Proposals: Unknown"));
        inventory.setItem(33, EspionageView.item(Material.PAPER, "Movements", "\u00a77Movements: Unknown"));
        inventory.setItem(53, manager.createBackButton(SFGUI.GOVERNMENT_VIEW));
    }

    public static void installations(Inventory inventory, Player viewer, Faction faction, InventoryManager manager) {
        prepare(inventory);
        var report = EspionageService.report(viewer, faction);
        inventory.setItem(10, EspionageView.factionItem(viewer, faction, net.tfminecraft.simplefactions.enums.MenuItemType.INSTALLATIONS));
        var entries = report == null ? List.<String>of() : report.details("installation-details", "installations");
        int slot = 12;
        for (String entry : entries) {
            if (slot > 38) break;
            String[] fields = entry.split("\n", 3);
            if (fields.length < 3) continue;
            Material material = fields[1].equals("TRAIN_STATION") ? Material.MINECART : Material.GREEN_CONCRETE;
            inventory.setItem(slot++, EspionageView.item(material, fields[2], "\u00a77Level: " + report.display("Installation:" + fields[0] + ":Level"),
                    "\u00a77Coordinates: Unknown", VehicleIntelligence.lore(report, fields[0]).getFirst()));
        }
        if (report == null || !report.allows("installation-details")) inventory.setItem(12, EspionageView.item(Material.GRAY_CONCRETE, "Installations", "\u00a77Locations and levels: Unknown"));
        var construction = report == null ? List.<String>of() : report.details("installation-details", "construction");
        if (!construction.isEmpty() || report == null || !report.allows("installation-details")) inventory.setItem(39,
                EspionageView.item(Material.YELLOW_CONCRETE, construction.isEmpty() ? "Construction" : construction.getFirst(), "\u00a77Time remaining: Unknown"));
        inventory.setItem(53, manager.createBackButton(SFGUI.INSTALLATIONS_VIEW));
    }

    public static void laws(Inventory inventory, Player viewer, Faction faction, InventoryManager manager) {
        prepare(inventory); var report = EspionageService.report(viewer, faction);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25}; int index = 0;
        for (var group : faction.getLawHandler().getGroupList()) {
            if (index >= slots.length) break;
            var current = report == null ? List.<String>of() : report.details("laws", "law:" + group.getId());
            inventory.setItem(slots[index++], EspionageView.item(Material.WRITABLE_BOOK, group.getName(),
                    "\u00a77Current law: " + (current.isEmpty() ? "Unknown" : current.getFirst())));
        }
        inventory.setItem(53, manager.createBackButton(SFGUI.LAW_VIEW));
    }

    public static Inventory open(Player viewer, String id, SFGUI type, int size, String title) {
        return SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(id, type), size, MenuTitles.legacy(title));
    }

    public static void taxes(Inventory inventory, Player viewer, Faction faction, InventoryManager manager) {
        prepare(inventory);
        var report = EspionageService.report(viewer, faction); int slot = 0;
        for (var target : net.tfminecraft.simplefactions.government.proposal.TaxTarget.values()) {
            if (!faction.getTaxHandler().canCollectTax(target)) continue;
            inventory.setItem(slot++, EspionageView.item(Material.GOLD_INGOT, target.getDisplayName(),
                    "\u00a77Tax rate: " + (report == null ? "Unknown" : report.display("Tax:" + target.name()))));
        }
        inventory.setItem(17, manager.createBackButton(SFGUI.TAX_VIEW));
    }

    public static void company(Inventory inventory, InventoryManager manager) {
        prepare(inventory);
        inventory.setItem(4, EspionageView.item(Material.IRON_SWORD, "Mercenary Company", "\u00a77Company details: Unknown"));
        inventory.setItem(11, EspionageView.item(Material.IRON_SWORD, "Company Slots", "\u00a77Slots: Unknown"));
        inventory.setItem(13, EspionageView.item(Material.PLAYER_HEAD, "Company Roster", "\u00a77Roster: Unknown"));
        inventory.setItem(15, EspionageView.item(Material.GOLD_INGOT, "Company Upgrades", "\u00a77Upgrades: Unknown"));
        inventory.setItem(22, EspionageView.item(Material.WRITABLE_BOOK, "Contracts", "\u00a77Contracts: Unknown"));
        inventory.setItem(26, manager.createBackButton(SFGUI.COMPANY_VIEW));
    }

    public static void loans(Inventory inventory, InventoryManager manager) {
        prepare(inventory);
        inventory.setItem(2, EspionageView.item(Material.GOLD_INGOT, "Loans Given", "\u00a77Loans given: Unknown"));
        inventory.setItem(4, EspionageView.item(Material.GOLD_INGOT, "Loans Taken", "\u00a77Loans taken: Unknown"));
        inventory.setItem(8, manager.createBackButton(SFGUI.LOAN_MAIN_VIEW));
    }

    /** Only navigation survives masking. No foreign click reaches the original mutation handlers. */
    public static void navigate(org.bukkit.event.inventory.InventoryClickEvent event, Player viewer, SFInventoryHolder holder, InventoryManager manager) {
        Faction faction = EspionageAccess.owner(holder);
        if (faction == null || event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.BARRIER) return;
        int slot = event.getSlot();
        var guild = net.tfminecraft.simplefactions.managers.FactionManager.getGuildByString(holder.getId());
        switch (holder.getType()) {
            case INSTALLATIONS_VIEW -> {
                var report = EspionageService.report(viewer, faction);
                var entries = report == null ? List.<String>of() : report.details("installation-details", "installations");
                if (slot >= 12 && slot <= 38) {
                    int position = 12;
                    for (String entry : entries) {
                        String[] fields = entry.split("\n", 3);
                        if (fields.length != 3) continue;
                        if (position++ == slot) {
                            manager.installationDetailView(viewer, faction, fields[0]);
                            break;
                        }
                    }
                }
            }
            case TAX_VIEW -> {
                var types = java.util.Arrays.stream(net.tfminecraft.simplefactions.government.proposal.TaxTarget.values())
                        .filter(faction.getTaxHandler()::canCollectTax).toList();
                if (slot >= 0 && slot < types.size() && types.get(slot).name().endsWith("_ID")) {
                    var target = types.get(slot);
                    var inventory = open(viewer, faction.getId(), SFGUI.TAX_VIEW_SPECIFIC, 54, target.getDisplayName()); prepare(inventory);
                    int position = 0;
                    if (target == net.tfminecraft.simplefactions.government.proposal.TaxTarget.GUILD_ID)
                        for (var entry : faction.getGuildHandler().getGuilds()) {
                            if (entry.isBase() || position >= 53) continue;
                            inventory.setItem(position++, mask(entry.getBanner(), entry.getName(), List.of("\u00a77Tax rate: Unknown")));
                        }
                    else inventory.setItem(0, EspionageView.item(Material.GOLD_INGOT, target.getDisplayName(), "\u00a77Specific rates: Unknown"));
                    inventory.setItem(53, manager.createBackButton(SFGUI.TAX_VIEW_SPECIFIC)); viewer.openInventory(inventory);
                }
            }
            case GOVERNMENT_VIEW -> {
                if (slot == 13) {
                    var inventory = open(viewer, faction.getId(), SFGUI.COUNCIL_VIEW, 54, "Council"); prepare(inventory);
                    for (int index : new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25})
                        inventory.setItem(index, EspionageView.item(Material.PLAYER_HEAD, "Council Seat", "\u00a77Office holder: Unknown"));
                    inventory.setItem(53, manager.createBackButton(SFGUI.COUNCIL_VIEW)); viewer.openInventory(inventory);
                }
                if (slot == 24) placeholder(viewer, faction.getId(), SFGUI.PROPOSALS, 27, "Proposals", 13, "Proposals", 26, manager);
                if (slot == 33) placeholder(viewer, faction.getId(), SFGUI.MOVEMENT_LIST, 54, "Movements", 10, "Movements", 53, manager);
            }
            case LAW_VIEW -> {
                int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25};
                for (int index = 0; index < slots.length && index < faction.getLawHandler().getGroupList().size(); index++) {
                    if (slot != slots[index]) continue;
                    var group = faction.getLawHandler().getGroupList().get(index);
                    var inventory = open(viewer, faction.getId(), SFGUI.LAW_SELECT, 27, "Law View"); prepare(inventory);
                    int position = 0;
                    var report = EspionageService.report(viewer, faction);
                    var current = report == null ? List.<String>of() : report.details("laws", "law:" + group.getId());
                    for (var law : group.getLaws().values()) {
                        if (position >= 26) break;
                        inventory.setItem(position++, mask(law.getIcon(), law.getName(), List.of("\u00a77Current policy: " +
                                (current.isEmpty() ? "Unknown" : current.getFirst().equals(law.getName()) ? "Yes" : "No"))));
                    }
                    inventory.setItem(26, manager.createBackButton(SFGUI.LAW_SELECT)); viewer.openInventory(inventory);
                }
            }
            case LOAN_MAIN_VIEW -> {
                if (slot == 2 || slot == 4) placeholder(viewer, holder.getId(), slot == 2 ? SFGUI.LOANS_GIVEN_VIEW : SFGUI.LOANS_TAKEN_VIEW,
                        54, slot == 2 ? "Loans Given" : "Loans Taken", 0, "Loan records", 53, manager);
            }
            case COMPANY_VIEW -> {
                if (slot == 11) placeholder(viewer, holder.getId(), SFGUI.COMPANY_SLOTS_VIEW, 54, "Company Slots", 9, "Company Slots", 53, manager);
                if (slot == 13) placeholder(viewer, holder.getId(), SFGUI.COMPANY_ROSTER_VIEW, 54, "Company Roster", 9, "Company Roster", 53, manager);
                if (slot == 15) placeholder(viewer, holder.getId(), SFGUI.COMPANY_UPGRADE_VIEW, 54, "Company Upgrades", 9, "Company Upgrades", 53, manager);
                if (slot == 22) placeholder(viewer, holder.getId(), SFGUI.CONTRACT_LIST_VIEW, 54, "Contracts", 0, "Contracts", 53, manager);
            }
            default -> { }
        }
    }

    private static void placeholder(Player viewer, String id, SFGUI type, int size, String title, int slot, String field, int back, InventoryManager manager) {
        var inventory = open(viewer, id, type, size, title); prepare(inventory);
        inventory.setItem(slot, EspionageView.item(Material.PAPER, field, "\u00a77Details: Unknown"));
        inventory.setItem(back, manager.createBackButton(type)); viewer.openInventory(inventory);
    }

    public static void installationDetail(Inventory inventory, Player viewer, Faction faction, String id, InventoryManager manager) {
        prepare(inventory);
        var report = EspionageService.report(viewer, faction);
        var entries = report == null ? List.<String>of() : report.details("installation-details", "installations");
        String title = "Installation Details"; Material icon = Material.GREEN_CONCRETE;
        for (String entry : entries) {
            String[] fields = entry.split("\n", 3);
            if (fields.length == 3 && fields[0].equals(id)) {
                title = fields[2]; icon = fields[1].equals("TRAIN_STATION") ? Material.MINECART : Material.GREEN_CONCRETE;
            }
        }
        inventory.setItem(49, EspionageView.item(icon, title, "\u00a77Level: " + (report == null ? "Unknown" : report.display("Installation:" + id + ":Level")),
                "\u00a77Coordinates: Unknown"));
        inventory.setItem(0, berthedVehicles(report, id));
        inventory.setItem(53, manager.createBackButton(SFGUI.INSTALLATION_DETAIL_VIEW));
    }
}
