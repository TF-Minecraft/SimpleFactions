package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.espionage.CharacterNames;
import net.tfminecraft.simplefactions.espionage.IntelligenceLedger;
import net.tfminecraft.simplefactions.espionage.SpecialPosition;
import net.tfminecraft.simplefactions.espionage.EspionageConfig;
import net.tfminecraft.simplefactions.espionage.IntelligenceTier;
import net.tfminecraft.simplefactions.espionage.SharingPartner;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.Cashflow;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.enums.MenuItemType;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.simplefactions.espionage.IntelligenceReport;
import net.tfminecraft.simplefactions.espionage.SpecialPositionAssignment;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;

/** Foreign reports retain normal navigation through read-only, masked menus. */
@SuppressWarnings("deprecation")
public final class EspionageView {
    private EspionageView() {}

    public static ItemStack item(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.setDisplayName(net.tfminecraft.tlibs.objects.api.subapi.StringFormatter.formatHex("§e" + title));
        meta.setLore(java.util.Arrays.stream(lore)
                .map(net.tfminecraft.tlibs.objects.api.subapi.StringFormatter::formatHex).toList());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack directoryItem(Player viewer, Faction target) {
        return new FactionCreator().createListItem(viewer, target);
    }

    public static void foreign(Inventory inventory, Player viewer, Faction target, InventoryManager manager) {
        IntelligenceReport report = EspionageService.report(viewer, target);
        inventory.clear();
        FactionCreator creator = new FactionCreator();
        inventory.setItem(4, reportHeader(viewer, report));
        int[] slots = {10, 11, 12, 13, 14, 16, 23, 25, 28, 29, 31, 32, 33, 34};
        MenuItemType[] types = {MenuItemType.BANNER, MenuItemType.GOVERNMENT, MenuItemType.WEALTH,
                MenuItemType.PRESTIGE, MenuItemType.MEMBERS, MenuItemType.MODIFIERS, MenuItemType.GUILDS,
                MenuItemType.TAX, MenuItemType.LAWS, MenuItemType.MILITARY, MenuItemType.DIPLOMACY,
                MenuItemType.INSTALLATIONS, MenuItemType.TIER, MenuItemType.TITLES};
        for (int index = 0; index < slots.length; index++) inventory.setItem(slots[index], creator.createMenuItem(viewer, target, types[index]));
        inventory.setItem(15, ledgerItem(report, target.getOrCreateMainGuild()));
        inventory.setItem(20, foreignPositionsItem(viewer, target));
        inventory.setItem(53, manager.createBackButton(SFGUI.FACTION_VIEW));
    }

    private static ItemStack reportHeader(Player viewer, IntelligenceReport report) {
        var observer = FactionManager.getByMember(viewer.getName());
        var spy = observer == null ? null : observer.getEspionage().getSpymaster();
        boolean missingSpymaster = !EspionageService.hasSpymaster(observer);
        String last = missingSpymaster ? "\u00a77The Spymaster's office stands vacant; no findings reach your court."
                : "\u00a77These are today\u2019s findings, delivered by your spymaster, " + CharacterNames.of(spy.playerName) + ".";
        List<String> lore = new ArrayList<>(List.of("\u00a77Report quality: \u00a7e" + (missingSpymaster ? "Absent"
                        : report == null ? IntelligenceTier.UNKNOWN.label() : report.qualityLabel()),
                report == null ? "\u00a77No dated account has yet reached your court." : "\u00a77Dated " + report.loreDate(), last));
        if (!missingSpymaster && report != null && EspionageConfig.sharingAllowed() && report.sharedTier() != IntelligenceTier.UNKNOWN)
            lore.add("\u00a7aTheir Spymaster shares everything up to " + report.sharedTier().label() + " exactly.");
        return item(Material.SPYGLASS, "Foreign intelligence", lore.toArray(String[]::new));
    }

    public static ItemStack ledgerItem(IntelligenceReport report, Guild guild) {
        return styled(Material.WRITABLE_BOOK, "#d6cf69Ledger", IntelligenceLedger.summary(report, guild).toArray(String[]::new));
    }

    public static void guild(Inventory inventory, Player viewer, Guild guild, InventoryManager manager) {
        var report = EspionageService.report(viewer, guild.getFaction());
        var creator = new GuildCreator();
        inventory.clear();
        inventory.setItem(4, reportHeader(viewer, report));
        inventory.setItem(10, creator.createMenuItem(viewer, guild, MenuItemType.BANNER));
        inventory.setItem(11, creator.createMenuItem(viewer, guild, MenuItemType.LEADER));
        inventory.setItem(12, styled(Material.GOLD_NUGGET, "#d1b43fWealth: " + IntelligenceLedger.value(report, guild, "Wealth", "d")));
        inventory.setItem(13, styled(Material.EMERALD, "#338651Trade Breakdown",
                "#d4c9aeIncome from trade: " + IntelligenceLedger.value(report, guild, "Cashflow:TRADE", "d/day"),
                "#d4c9aeUpkeep from trade: " + IntelligenceLedger.value(report, guild, "Cashflow:TRADE_UPKEEP", "d/day"),
                "#d4c9aeTariffs Paid: " + IntelligenceLedger.value(report, guild, "Cashflow:TARIFF_PAYMENTS", "d/day"),
                "#d4c9aeTotal Trade Power: " + IntelligenceLedger.value(report, guild, "Trade power", "")));
        inventory.setItem(14, ledgerItem(report, guild));
        List<String> roster = net.tfminecraft.simplefactions.espionage.RosterLore.guild(viewer, guild);
        inventory.setItem(15, styled(Material.PLAYER_HEAD, "#b8ae61Members: "
                + IntelligenceLedger.value(report, guild, "Members", "") + "/" + net.tfminecraft.simplefactions.Cache.maxMembers, roster.toArray(String[]::new)));
        if (guild.hasUpgrades()) inventory.setItem(16, styled(Material.GOLD_INGOT, "#d979c2Upgrades", "\u00a77Click to inspect upgrades."));
        for (int group = 0; group < 10; group++) {
            var branch = guild.getBranch(group);
            if (branch != null) inventory.setItem(group + 29, ReportedMenus.branch(viewer, guild, branch));
        }
        inventory.setItem(GuildView.COMPANY_SLOT, styled(Material.IRON_SWORD, "Mercenary Company", "\u00a77Company details: Unknown", "\u00a77Click to inspect."));
        if (!guild.isBase()) inventory.setItem(17, styled(Material.GOLD_INGOT, "#c49e5cDividends",
                "#d4c9aeRate: " + IntelligenceLedger.value(report, guild, "Dividend rate", "%"),
                "#d4c9aePool: " + IntelligenceLedger.value(report, guild, "Dividend pool", "d"),
                "#d4c9aeTax withheld: " + IntelligenceLedger.value(report, guild, "Dividend tax", "d"),
                "#d4c9aePer member: " + IntelligenceLedger.value(report, guild, "Dividend per member", "d")));
        inventory.setItem(25, ReportedMenus.mask(IconGetter.getIconOrDefault("guild_loans", Material.BLACK_DYE),
                "#e8c65fLoans", List.of("\u00a77Loans: Unknown")));
        inventory.setItem(GuildView.HOST_FACTION_SLOT, creator.createHostFactionItem(guild));
        inventory.setItem(53, manager.createBackButton(SFGUI.GUILD_VIEW));
        for (var entry : java.util.Map.of(12, MenuItemType.WEALTH, 13, MenuItemType.TRADE_BREAKDOWN, 15, MenuItemType.MEMBERS).entrySet())
            inventory.setItem(entry.getKey(), FactionCreator.applyIcon(inventory.getItem(entry.getKey()), entry.getValue()));
    }

    public static void foreignLedger(Player viewer, Guild guild, InventoryManager manager) {
        Inventory inventory = Bukkit.createInventory(new SFInventoryHolder(guild.getId(), SFGUI.FOREIGN_LEDGER_VIEW),
                27, MenuTitles.legacy("Ledger for " + guild.getName()));
        var report = EspionageService.report(viewer, guild.getFaction());
        if (guild.isBase()) {
            Material[] icons = {Material.PLAYER_HEAD, Material.BARREL, Material.IRON_INGOT, Material.GOLD_INGOT,
                    Material.EMERALD, Material.CHEST};
            String[] titles = {"#94b572Citizens", "#b89448Guild Taxes", "#7299b5Vassals", "#ab8568Tributes",
                    "#5cc46aTariffs", "#c9a25eDeposits"};
            Cashflow[] flows = {Cashflow.CITIZENS, Cashflow.GUILDS, Cashflow.VASSALS, Cashflow.TRIBUTES,
                    Cashflow.TARIFFS, null};
            for (int index = 0; index < icons.length; index++) inventory.setItem(10 + index, styled(icons[index], titles[index],
                    "\u00a77Today's amount: " + (flows[index] == null ? "Unknown" : IntelligenceLedger.value(report, guild, "Cashflow:" + flows[index].name(), "d/day")),
                    "\u00a77Contributors and history: Unknown"));
        }
        inventory.setItem(26, manager.createBackButton(SFGUI.FOREIGN_LEDGER_VIEW));
        viewer.openInventory(inventory);
    }

    private static String value(IntelligenceReport report, String metric, String units) {
        String value = report == null ? "Unknown" : report.display(metric);
        return value.equals("Unknown") ? "\u00a77" + value : value + units;
    }

    private static ItemStack styled(Material material, String title, String... lore) {
        ItemStack result = item(material, title, lore);
        var meta = result.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex(title));
        if (material == Material.PLAYER_HEAD) meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        result.setItemMeta(meta);
        return result;
    }

    public static ItemStack factionItem(Player viewer, Faction target, MenuItemType type) {
        IntelligenceReport report = EspionageService.report(viewer, target);
        return switch (type) {
            case GOVERNMENT -> {
                ItemStack head = styled(Material.PLAYER_HEAD, "#93c9a7Government:",
                        "#9c9775§l" + target.getRulerTitle() + ": #c2bea7" + CharacterNames.display(viewer, target.getLeader(), target),
                        "#85c265Administrative Power§7: §e" + value(report, "Administrative power", ""),
                        "#85c265Stability: " + IntelligenceReport.stabilityRange(report), " ",
                        "#b8ae61Ruling System: #d4c9ae" + target.getGovernmentString());
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(target.getLeader()));
                head.setItemMeta(meta);
                yield head;
            }
            case WEALTH -> {
                Integer rank = new net.tfminecraft.simplefactions.utils.FactionRanker()
                        .getVisibleRank(viewer, target, net.tfminecraft.simplefactions.enums.RankType.WEALTH);
                yield styled(Material.GOLD_NUGGET, "#d1b43fWealth: #ccbb76" + value(report, "Wealth", "d"),
                        rank == null ? "§7Ranking: Unknown" : "#7fbd73Estimated ranking: #" + rank);
            }
            case PRESTIGE -> styled(Material.DIAMOND, "#4793bfPrestige: #6eafba" + target.getPrestige(),
                    "#7fbd73Current Rank: " + target.getRank().getName(),
                    "#7fbd73Prestige ranking: #" + new net.tfminecraft.simplefactions.utils.FactionRanker().getPrestigeRank(target));
            case MEMBERS -> {
                List<String> lore = net.tfminecraft.simplefactions.espionage.RosterLore.faction(viewer, target);
                yield styled(Material.PLAYER_HEAD, "#b8ae61Members: #7fbd73" + value(report, "Members", ""), lore.toArray(String[]::new));
            }
            case MODIFIERS -> styled(Material.GOLDEN_APPLE, "#c49760Modifiers",
                    "#4bb244Prosperity: #4fd945" + value(report, "Prosperity", ""), "§7Faction modifiers: Unknown");
            case TAX -> styled(Material.GOLD_INGOT, "#c77c32Tax Rates", "§7Tax rates: Unknown");
            case LAWS -> styled(Material.WRITABLE_BOOK, "#9c64b0Laws", "§7Laws: Unknown");
            case MILITARY -> styled(Material.IRON_SWORD, "#a6659fMilitary",
                    "#d4c9aeProfessional army: §e" + value(report, "Professional army", ""),
                    "#d4c9aeLevies: §e" + value(report, "Levies", ""),
                    "#d4c9aeMercenaries: §e" + value(report, "Mercenaries", ""));
            case INSTALLATIONS -> styled(Material.GREEN_CONCRETE, "#706964Installations",
                    "#d4c9aeInstallations: §e" + value(report, "Installations", ""));
            case DIPLOMACY -> {
                ItemStack item = styled(Material.WRITABLE_BOOK, "#35f2bdDiplomacy",
                        "#7fbd73Diplomatic Capacity: §7Unknown", "§7Click to view Diplomacy");
                var meta = item.getItemMeta();
                meta.getPersistentDataContainer().set(new NamespacedKey(SimpleFactions.plugin, "id"), PersistentDataType.STRING, target.getId());
                item.setItemMeta(meta);
                yield item;
            }
            default -> throw new IllegalArgumentException("No private faction item for " + type);
        };
    }

    public static ItemStack positionButton() {
        return item(Material.ENDER_EYE, "Special Positions", "§7Inspect your faction's special offices.",
                "§aClick to view offices.");
    }

    public static ItemStack foreignPositionsItem(Player viewer, Faction faction) {
        List<String> lore = new ArrayList<>();
        var report = EspionageService.report(viewer, faction);
        for (var office : SpecialPosition.values()) {
            var holder = EspionageService.canViewExact(viewer, faction) ? faction.getEspionage().holder(office) : null;
            String name = EspionageService.canViewExact(viewer, faction)
                    ? holder == null ? "Vacant" : CharacterNames.display(viewer, holder.playerName)
                    : report == null ? "Unknown" : report.officeHolder(office);
            String aptitude = EspionageService.canViewExact(viewer, faction)
                    ? Integer.toString(switch (office) {
                        case SPYMASTER -> EspionageService.effectiveAptitude(faction, holder);
                    })
                    : report == null ? "Unknown" : report.display(IntelligenceReport.officeAptitudeKey(office));
            lore.add("\u00a77" + office.label() + ": " + name);
            lore.add("\u00a77Aptitude: " + aptitude + (aptitude.equals("Unknown") ? "" : "/100"));
        }
        lore.add("\u00a7aClick to inspect offices.");
        return item(Material.ENDER_EYE, "Special Positions", lore.toArray(String[]::new));
    }

    public static void foreignPositions(Player viewer, Faction faction, InventoryManager manager) {
        Inventory inventory = Bukkit.createInventory(new SFInventoryHolder(faction.getId(), SFGUI.FOREIGN_POSITIONS_VIEW),
                27, "\u00a77Foreign Special Positions");
        inventory.setItem(4, EspionageService.canViewExact(viewer, faction)
                ? item(Material.PAPER, "Court offices", "\u00a77Exact information") : reportHeader(viewer, EspionageService.report(viewer, faction)));
        inventory.setItem(13, foreignPositionsItem(viewer, faction));
        inventory.setItem(26, manager.createBackButton(SFGUI.FOREIGN_POSITIONS_VIEW));
        viewer.openInventory(inventory);
    }

    public static void positions(Player viewer, Faction faction, InventoryManager manager) {
        if (!EspionageService.isOwn(viewer, faction)) { foreignPositions(viewer, faction, manager); return; }
        Inventory inventory = Bukkit.createInventory(new SFInventoryHolder(faction.getId(), SFGUI.SPECIAL_POSITIONS),
                27, "\u00a77Special Positions");
        int slot = 10;
        for (SpecialPosition office : SpecialPosition.values()) {
            var holder = office == SpecialPosition.SPYMASTER ? EspionageService.spymaster(faction) : faction.getEspionage().holder(office);
            ItemStack option = item(Material.ENDER_EYE, office.label(),
                    "\u00a77Office holder: " + (holder == null ? "Vacant" : CharacterNames.display(viewer, holder.playerName)),
                    "\u00a7aClick to inspect this office.");
            var meta = option.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(SimpleFactions.plugin, "special-office"),
                    PersistentDataType.STRING, office.name());
            option.setItemMeta(meta);
            inventory.setItem(slot++, option);
        }
        inventory.setItem(26, manager.createBackButton(SFGUI.SPECIAL_POSITIONS));
        viewer.openInventory(inventory);
    }

    public static void spymasterOffice(Player viewer, Faction faction, InventoryManager manager) {
        if (!EspionageService.isOwn(viewer, faction)) return;
        Inventory inventory = Bukkit.createInventory(new SFInventoryHolder(faction.getId(), SFGUI.SPYMASTER_VIEW),
                27, "\u00a77Spymaster's Office");
        SpecialPositionAssignment holder = EspionageService.spymaster(faction);
        long now = System.currentTimeMillis();
        ItemStack head = item(Material.PLAYER_HEAD, "Spymaster: " + (holder == null ? "Vacant" : CharacterNames.display(viewer, holder.playerName)),
                "§7Aptitude: §e" + EspionageService.effectiveAptitude(faction, holder) + "/100",
                buildUpLine(holder, now),
                holder == null ? "§7Falls to the faction leader once they have an active character."
                        : holder.automatic ? "§7Held by the faction leader until a member is appointed."
                        : "§7Appointed by the faction leader.",
                "§7Gathers foreign intelligence and guards your secrets.",
                holder == null ? "\u00a7cWithout a Spymaster, all faction and guild information is public."
                        : "\u00a77An eligible Spymaster protects your faction and guild information.",
                holder != null && holder.isHolder(viewer.getUniqueId())
                        ? "§aClick your own head to inspect your private conduct." : "§8Private conduct is known only to the office holder.");
        if (holder != null) {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(holder.playerId));
            head.setItemMeta(meta);
        }
        inventory.setItem(13, head);
        if (faction.isLeader(viewer.getName())) {
            long next = EspionageService.nextAppointmentAt(faction);
            inventory.setItem(11, item(Material.NAME_TAG, "Appoint Spymaster", "§7Choose a member of your faction.",
                    buildUpTerms(),
                    faction.getEspionage().appointmentCount(SpecialPosition.SPYMASTER) == 0
                            ? "\u00a77The first appointment brings no unrest."
                            : EspionageConfig.stabilityPenalty() <= 0 || EspionageConfig.penaltyDays() <= 0
                            ? "\u00a77Changing Spymaster brings no unrest."
                            : "\u00a77Unrest: -" + EspionageConfig.stabilityPenalty() + " points, fading over " + EspionageConfig.penaltyDays() + " days.",
                    now < next ? "\u00a7cNext appointment in " + EspionageService.duration(next - now) + "."
                            : "\u00a7aAn appointment can be made now."));
            inventory.setItem(15, item(Material.REDSTONE, "Remove Spymaster",
                    "\u00a77Dismisses the appointee. The office returns to you."));
        }
        inventory.setItem(26, manager.createBackButton(SFGUI.SPYMASTER_VIEW));
        viewer.openInventory(inventory);
    }

    public static void settings(Player viewer, Faction faction, InventoryManager manager) {
        SpecialPositionAssignment holder = EspionageService.spymaster(faction);
        if (!EspionageService.isOwn(viewer, faction) || holder == null || !holder.isHolder(viewer.getUniqueId())) return;
        Inventory inventory = Bukkit.createInventory(new SFInventoryHolder(faction.getId(), SFGUI.SPYMASTER_SETTINGS),
                27, "§7Spymaster's Private Conduct");
        inventory.setItem(4, item(Material.PAPER, "Your sealed instructions",
                "§7Aptitude: §e" + EspionageService.effectiveAptitude(faction, holder) + "/100",
                buildUpLine(holder, System.currentTimeMillis()),
                "§7Sabotage is voluntary and disabled on appointment.",
                "§7Only you can see or change these choices.",
                "§7Sabotage changes affect your next daily rolls.", "§7Existing daily rolls and reports never reroll."));
        inventory.setItem(11, conduct("Offensive sabotage", holder.offenseReduction));
        inventory.setItem(15, conduct("Defensive sabotage", holder.defenseReduction));
        if (EspionageConfig.sharingAllowed()) {
            var overlord = faction.getOverlord();
            inventory.setItem(SHARE_OVERLORD_SLOT, sharing("Share with your overlord", faction.getEspionage().sharing(SharingPartner.OVERLORD),
                    overlord == null ? "\u00a78Your faction has no overlord." : "\u00a77Overlord: \u00a7f" + overlord.getName()));
            int vassals = faction.getVassals().size();
            inventory.setItem(SHARE_VASSALS_SLOT, sharing("Share with your vassals", faction.getEspionage().sharing(SharingPartner.VASSALS),
                    vassals == 0 ? "\u00a78Your faction has no vassals." : "\u00a77Vassals: \u00a7f" + vassals));
        }
        inventory.setItem(26, manager.createBackButton(SFGUI.SPYMASTER_SETTINGS));
        viewer.openInventory(inventory);
    }

    private static String buildUpLine(SpecialPositionAssignment holder, long now) {
        long remaining = EspionageService.buildUpRemaining(holder, now);
        return remaining <= 0 ? "\u00a77Network: \u00a7aestablished"
                : "\u00a77Network: \u00a7ebuilding\u00a77, full aptitude in " + EspionageService.duration(remaining);
    }

    private static String buildUpTerms() {
        if (!EspionageConfig.buildsUp()) return "\u00a77A new Spymaster serves at full aptitude at once.";
        return "\u00a77A new Spymaster starts at " + Math.round(EspionageConfig.startingAptitude() * 100)
                + "% aptitude, reaching full over " + EspionageService.duration(Math.round(EspionageConfig.buildUpDays() * 86_400_000)) + ".";
    }

    private static final int SHARE_OVERLORD_SLOT = 21, SHARE_VASSALS_SLOT = 23;

    private static ItemStack sharing(String title, IntelligenceTier tier, String partner) {
        return item(tier == IntelligenceTier.UNKNOWN ? Material.BOOK : Material.WRITABLE_BOOK, title, partner,
                tier == IntelligenceTier.UNKNOWN ? "\u00a7cSharing nothing" : "\u00a7aSharing up to " + tier.label(),
                "\u00a77Information shown at this tier or lower", "\u00a77reaches them exactly, not as ranges.",
                "\u00a77Click to cycle: nothing, rumours, broad, reliable, detailed.");
    }

    static IntelligenceTier nextSharing(IntelligenceTier tier) {
        var tiers = IntelligenceTier.values();
        return tiers[(tier.ordinal() + 1) % tiers.length];
    }

    private static ItemStack conduct(String title, int reduction) {
        return item(reduction == 0 ? Material.LIME_DYE : Material.RED_DYE, title,
                reduction == 0 ? "§aDisabled" : "§cRoll reduced by " + reduction,
                "§7Click to cycle: disabled, -25, -50, -75, -100.", "§8A private choice for roleplay betrayal.");
    }

    private static void candidates(Player viewer, Faction faction, InventoryManager manager, int page) {
        if (!EspionageService.isOwn(viewer, faction) || !faction.isLeader(viewer.getName())) return;
        List<String> members = faction.getMembers().stream().distinct()
                .filter(name -> EspionageService.eligible(faction, name)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        int safePage = Math.max(0, Math.min(page, Math.max(0, (members.size() - 1) / 45)));
        Inventory inventory = Bukkit.createInventory(new SFInventoryHolder(faction.getId(), SFGUI.SPYMASTER_SELECT, safePage),
                54, "§7Appoint Spymaster");
        int start = safePage * 45;
        for (int index = start; index < Math.min(start + 45, members.size()); index++) {
            String name = members.get(index);
            ItemStack head = item(Material.PLAYER_HEAD, CharacterNames.display(viewer, name),
                    "§7Guild: " + net.tfminecraft.simplefactions.utils.Represents.represents(faction, name),
                    buildUpTerms(),
                    Bukkit.getPlayerExact(name) == null ? "§8Must be online with an active character." : "§aClick to appoint as Spymaster.");
            var meta = head.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(SimpleFactions.plugin, "spy-candidate"),
                    PersistentDataType.STRING, name);
            head.setItemMeta(meta);
            inventory.setItem(index - start, head);
        }
        if (safePage > 0) inventory.setItem(45, item(Material.ARROW, "Previous page"));
        if (start + 45 < members.size()) inventory.setItem(52, item(Material.ARROW, "Next page"));
        inventory.setItem(53, manager.createBackButton(SFGUI.SPYMASTER_SELECT));
        viewer.openInventory(inventory);
    }

    public static boolean handles(SFGUI type) {
        return type == SFGUI.SPECIAL_POSITIONS || type == SFGUI.SPYMASTER_VIEW
                || type == SFGUI.SPYMASTER_SETTINGS || type == SFGUI.SPYMASTER_SELECT || type == SFGUI.FOREIGN_LEDGER_VIEW || type == SFGUI.FOREIGN_POSITIONS_VIEW;
    }

    public static void click(InventoryClickEvent event, Player viewer, SFInventoryHolder menu, InventoryManager manager) {
        event.setCancelled(true);
        if (menu.getType() == SFGUI.FOREIGN_POSITIONS_VIEW) {
            var faction = FactionManager.getByString(menu.getId());
            if (faction == null) viewer.closeInventory();
            else if (event.getRawSlot() == 26) manager.factionView(viewer, faction);
            else foreignPositions(viewer, faction, manager); // Recheck current tier and viewing permission.
            return;
        }
        if (menu.getType() == SFGUI.FOREIGN_LEDGER_VIEW) {
            if (event.getRawSlot() == 26) {
                var guild = FactionManager.getGuildByString(menu.getId());
                if (guild != null) {
                    if (guild.isBase()) manager.factionView(viewer, guild.getFaction());
                    else manager.guildView(viewer, guild);
                }
            }
            return;
        }
        Faction faction = FactionManager.getByString(menu.getId());
        if (faction == null || !EspionageService.isOwn(viewer, faction)) {
            viewer.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (menu.getType() == SFGUI.SPECIAL_POSITIONS) {
            if (slot == 26) manager.factionView(viewer, faction);
            else if (event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta()) {
                String office = event.getCurrentItem().getItemMeta().getPersistentDataContainer()
                        .get(new NamespacedKey(SimpleFactions.plugin, "special-office"), PersistentDataType.STRING);
                if (SpecialPosition.SPYMASTER.name().equals(office)) spymasterOffice(viewer, faction, manager);
            }
        } else if (menu.getType() == SFGUI.SPYMASTER_VIEW) {
            if (slot == 26) positions(viewer, faction, manager);
            else if (slot == 13) settings(viewer, faction, manager);
            else if (slot == 11) candidates(viewer, faction, manager, 0);
            else if (slot == 15 && EspionageService.remove(viewer, faction)) spymasterOffice(viewer, faction, manager);
        } else if (menu.getType() == SFGUI.SPYMASTER_SETTINGS) {
            if (slot == 26) spymasterOffice(viewer, faction, manager);
            else if (slot == 11 || slot == 15) {
                SpecialPositionAssignment holder = EspionageService.spymaster(faction);
                if (holder == null || !holder.isHolder(viewer.getUniqueId())) {
                    viewer.closeInventory();
                    return;
                }
                int current = slot == 11 ? holder.offenseReduction : holder.defenseReduction;
                if (EspionageService.setSabotage(viewer, faction, slot == 11, (current + 25) % 125)) {
                    settings(viewer, faction, manager);
                }
            } else if (slot == SHARE_OVERLORD_SLOT || slot == SHARE_VASSALS_SLOT) {
                var partner = slot == SHARE_OVERLORD_SLOT ? SharingPartner.OVERLORD : SharingPartner.VASSALS;
                if (EspionageService.setSharing(viewer, faction, partner, nextSharing(faction.getEspionage().sharing(partner))))
                    settings(viewer, faction, manager);
            }
        } else {
            if (!faction.isLeader(viewer.getName())) { viewer.closeInventory(); return; }
            if (slot == 53) spymasterOffice(viewer, faction, manager);
            else if (slot == 45 || slot == 52) candidates(viewer, faction, manager, menu.getPage() + (slot == 45 ? -1 : 1));
            else if (event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta()) {
                String candidate = event.getCurrentItem().getItemMeta().getPersistentDataContainer()
                        .get(new NamespacedKey(SimpleFactions.plugin, "spy-candidate"), PersistentDataType.STRING);
                if (candidate != null && EspionageService.appoint(viewer, faction, Bukkit.getPlayerExact(candidate))) {
                    spymasterOffice(viewer, faction, manager);
                }
            }
        }
    }
}
