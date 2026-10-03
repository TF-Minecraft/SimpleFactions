package net.tfminecraft.simplefactions.managers.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubAgreement;
import net.tfminecraft.simplefactions.guild.hub.HubAgreementService;
import net.tfminecraft.simplefactions.guild.hub.HubAgreementService.AgreementResult;
import net.tfminecraft.simplefactions.guild.hub.HubAgreementService.PendingMatter;
import net.tfminecraft.simplefactions.guild.hub.HubAgreementService.RateRange;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Destination;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Group;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Terms;
import net.tfminecraft.simplefactions.guild.hub.HubOffer;
import net.tfminecraft.simplefactions.guild.hub.HubProposalCopy;
import net.tfminecraft.simplefactions.guild.hub.OfferSide;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;

/**
 * The proposal list, the shared negotiation chest, and the host's offer list.
 * Changing the rate or the fee only reruns {@link HubEstimates#applyTerms}.
 */
public final class HubProposalMenu {
    private static final String SEP = "\u001f";
    private static final int PAGE_SIZE = 45;

    private HubProposalMenu() {
    }

    public static void openProposals(Player player, Guild guild, int page) {
        if (player == null || guild == null) {
            return;
        }
        if (!isLeader(guild, player)) {
            player.sendMessage("§cOnly the guild leader can propose a hub");
            return;
        }
        List<Destination> destinations = HubEstimates.destinations(guild);
        int pages = Math.max(1, (destinations.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.max(0, Math.min(page, pages - 1));
        Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
                new SFInventoryHolder(guild.getId(), SFGUI.HUB_PROPOSAL_LIST, shown),
                54, "§7Propose a Hub");
        if (destinations.isEmpty()) {
            inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§7No estimates yet", List.of(
                    "§7Where a hub would pay is worked out once a day.",
                    "§7Check back after the next income pass.")));
        }
        int start = shown * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < destinations.size(); index++) {
            Destination destination = destinations.get(start + index);
            Faction host = FactionManager.getByString(destination.hostFactionId());
            List<String> lore = new ArrayList<>();
            lore.add("§7Host: §f" + (host == null ? destination.hostFactionId() : host.getName()));
            lore.addAll(HubProposalCopy.destinationLore(destination));
            ItemStack item = SupplyHubCreator.item(
                    destination.group() == Group.READY ? Material.CHEST : Material.MAP,
                    "§e" + destination.label(), lore);
            ItemMeta meta = item.getItemMeta();
            String installation = destination.installationId() == null ? "" : destination.installationId();
            meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING,
                    destination.group().name() + SEP + destination.hostFactionId() + SEP + installation
                            + SEP + destination.provinceId() + SEP + (destination.ownRealm() ? "own" : "foreign"));
            item.setItemMeta(meta);
            inventory.setItem(index, item);
        }
        if (shown > 0) {
            inventory.setItem(48, SupplyHubCreator.item(Material.ARROW, "§ePrevious page", List.of()));
        }
        if (shown + 1 < pages) {
            inventory.setItem(50, SupplyHubCreator.item(Material.ARROW, "§eNext page", List.of()));
        }
        inventory.setItem(53, backButton(SFGUI.HUB_PROPOSAL_LIST));
        player.openInventory(inventory);
    }

    public static void openOffers(Player player) {
        openOfferList(player, null, null);
    }

    public static void openOffersAt(Player player, String hostFactionId, String installationId) {
        openOfferList(player, hostFactionId, installationId);
    }

    public static void openNegotiation(
            Player player, Guild guild, String hostFactionId, String installationId, int rate, long feeCents) {
        if (player == null || guild == null || hostFactionId == null || installationId == null) {
            return;
        }
        Faction host = FactionManager.getByString(hostFactionId);
        if (!isLeader(guild, player) && !isCouncil(host, player)) {
            player.sendMessage("§cOnly the guild leader or the host's council can open this");
            return;
        }
        RateRange range = HubAgreementService.allowedRateRange(host);
        int min = range.minPercent();
        int max = range.maxPercent();
        int shownRate = HubProposalCopy.clampRate(rate, min, max);
        long shownFee = HubProposalCopy.clampFeeCents(feeCents, Cache.supplyHubMaxFee);
        Destination destination = findDestination(guild, hostFactionId, installationId);
        Terms terms = destination == null ? null : HubEstimates.applyTerms(destination, shownRate, shownFee);
        HubOffer offer = HubAgreementService.offer(guild, hostFactionId, installationId);
        HubAgreement agreement = HubAgreementService.findAgreement(guild, hostFactionId, installationId);
        boolean leader = isLeader(guild, player);
        boolean council = isCouncil(host, player);
        boolean yourTurn = offer != null && ((offer.awaiting() == OfferSide.GUILD && leader)
                || (offer.awaiting() == OfferSide.HOST && council));

        Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
                new SFInventoryHolder(guild.getId(), SFGUI.HUB_NEGOTIATION,
                        hostFactionId + SEP + installationId + SEP + shownRate + SEP + shownFee),
                54, "§7Hub Agreement");
        String site = destination == null ? installationId : destination.label();
        inventory.setItem(4, SupplyHubCreator.item(Material.PAPER, "§eYour side",
                HubProposalCopy.operatorLines(destination, terms)));
        List<String> termsLore = new ArrayList<>();
        termsLore.add("§7" + site);
        termsLore.add("§7Rate: §e" + shownRate + "% §7(§e" + min + "-" + max + "%§7)");
        termsLore.add("§7Daily fee: §e" + Formatter.formatMoney(shownFee / 100.0));
        termsLore.add("§7Term: §e" + Math.max(1, Cache.supplyHubAgreementDays) + " days");
        termsLore.add(HubProposalCopy.breakEvenLine(destination, shownFee, min, max));
        if (offer != null) {
            termsLore.add(yourTurn ? "§eWaiting on you" : "§7Waiting on the other side");
        } else if (agreement != null) {
            termsLore.add("§7This hub already has an agreement");
        }
        inventory.setItem(13, SupplyHubCreator.item(Material.GOLD_INGOT, "§eTerms", termsLore));
        inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§eHost side",
                HubProposalCopy.hostLines(destination, terms, guildNames(destination))));

        inventory.setItem(28, action(Material.RED_DYE, "§cRate -5", "rate:-5"));
        inventory.setItem(29, action(Material.RED_DYE, "§cRate -1", "rate:-1"));
        inventory.setItem(31, action(Material.LIME_DYE, "§aRate +1", "rate:1"));
        inventory.setItem(32, action(Material.LIME_DYE, "§aRate +5", "rate:5"));
        inventory.setItem(37, action(Material.RED_DYE, "§cFee -10", "fee:-1000"));
        inventory.setItem(38, action(Material.RED_DYE, "§cFee -1", "fee:-100"));
        inventory.setItem(40, action(Material.LIME_DYE, "§aFee +1", "fee:100"));
        inventory.setItem(41, action(Material.LIME_DYE, "§aFee +10", "fee:1000"));

        if (offer != null && yourTurn) {
            inventory.setItem(45, action(Material.RED_CONCRETE, "§cDecline", "decline"));
            inventory.setItem(51, action(Material.GREEN_CONCRETE, "§aAccept", "accept"));
        } else if (offer != null) {
            inventory.setItem(45, action(Material.RED_CONCRETE, "§cWithdraw", "withdraw"));
        }
        inventory.setItem(49, action(Material.EMERALD, offer == null ? "§aOffer" : "§aSend these terms", "offer"));
        if (agreement != null) {
            boolean enabled = leader ? agreement.guildRenews() : agreement.hostRenews();
            String whose = leader ? "Your guild" : "The host";
            inventory.setItem(47, action(Material.LEVER,
                    "§e" + whose + " renews: " + (enabled ? "§aon" : "§coff"),
                    "renew:" + !enabled));
        }
        inventory.setItem(53, backButton(SFGUI.HUB_NEGOTIATION));
        player.openInventory(inventory);
    }

    public static void click(InventoryClickEvent event, Inventory inventory, Player player, InventoryManager menus) {
        event.setCancelled(true);
        if (!(inventory.getHolder() instanceof SFInventoryHolder holder) || player == null) {
            return;
        }
        if (holder.getType() == SFGUI.HUB_PROPOSAL_LIST) {
            clickProposals(event, holder, player, menus);
        } else if (holder.getType() == SFGUI.HUB_NEGOTIATION) {
            clickNegotiation(event, holder, player, menus);
        } else if (holder.getType() == SFGUI.HUB_OFFER_LIST) {
            clickOffers(event, holder, player, menus);
        }
    }

    /** Reopens a hub menu that is already on screen so the other side sees the new terms. */
    public static void refresh(Player player) {
        try {
            if (player == null || player.getOpenInventory() == null) {
                return;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof SFInventoryHolder holder)) {
                return;
            }
            if (holder.getType() == SFGUI.HUB_PROPOSAL_LIST) {
                Guild guild = FactionManager.getGuildByString(holder.getId());
                if (guild != null) {
                    openProposals(player, guild, holder.getPage());
                }
            } else if (holder.getType() == SFGUI.HUB_OFFER_LIST) {
                String filter = holder.getSecondaryId();
                if (filter == null) {
                    openOffers(player);
                } else {
                    String[] parts = filter.split(SEP, -1);
                    if (parts.length >= 2) {
                        openOffersAt(player, parts[0], parts[1]);
                    } else {
                        openOffers(player);
                    }
                }
            } else if (holder.getType() == SFGUI.HUB_NEGOTIATION) {
                Guild guild = FactionManager.getGuildByString(holder.getId());
                Draft draft = Draft.parse(holder.getSecondaryId());
                if (guild == null || draft == null) {
                    return;
                }
                HubOffer offer = HubAgreementService.offer(guild, draft.hostId, draft.installationId);
                int rate = offer == null ? draft.rate : offer.taxRatePercent();
                long fee = offer == null ? draft.feeCents : offer.feeCents();
                openNegotiation(player, guild, draft.hostId, draft.installationId, rate, fee);
            }
        } catch (Throwable ignored) {
            // Menus are not open during a test, and a missing server must not drop the notice.
        }
    }

    private static void clickProposals(
            InventoryClickEvent event, SFInventoryHolder holder, Player player, InventoryManager menus) {
        Guild guild = FactionManager.getGuildByString(holder.getId());
        if (guild == null) {
            return;
        }
        if (event.getSlot() == 53) {
            menus.supplyHubView.guildView(player, guild);
            return;
        }
        if (event.getSlot() == 48) {
            openProposals(player, guild, holder.getPage() - 1);
            return;
        }
        if (event.getSlot() == 50) {
            openProposals(player, guild, holder.getPage() + 1);
            return;
        }
        String data = key(event.getCurrentItem());
        if (data == null) {
            return;
        }
        String[] parts = data.split(SEP, -1);
        if (parts.length < 4) {
            return;
        }
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
        if (Group.WORTH_BUILDING.name().equals(parts[0]) || parts[2].isEmpty()) {
            player.sendMessage("§7A station has to be built there before a hub can be proposed.");
            return;
        }
        Destination destination = findDestination(guild, parts[1], parts[2]);
        boolean ownRealm = (parts.length >= 5 && parts[4].equals("own"))
                || (destination != null && destination.ownRealm());
        if (ownRealm) {
            menus.confirmSupplyHub(player, guild.getFaction(),
                    "build|" + guild.getId() + "|" + parts[1] + "|" + parts[2]);
            return;
        }
        openFromExisting(player, guild, parts[1], parts[2]);
    }

    private static void clickNegotiation(
            InventoryClickEvent event, SFInventoryHolder holder, Player player, InventoryManager menus) {
        Guild guild = FactionManager.getGuildByString(holder.getId());
        Draft draft = Draft.parse(holder.getSecondaryId());
        if (guild == null || draft == null) {
            return;
        }
        if (event.getSlot() == 53) {
            if (isLeader(guild, player)) {
                openProposals(player, guild, 0);
            } else {
                openOffers(player);
            }
            return;
        }
        String action = key(event.getCurrentItem());
        if (action == null) {
            return;
        }
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
        Faction host = FactionManager.getByString(draft.hostId);
        RateRange range = HubAgreementService.allowedRateRange(host);
        int min = range.minPercent();
        int max = range.maxPercent();
        if (action.startsWith("rate:")) {
            int next = HubProposalCopy.clampRate(draft.rate + parseInt(action.substring(5)), min, max);
            openNegotiation(player, guild, draft.hostId, draft.installationId, next, draft.feeCents);
            return;
        }
        if (action.startsWith("fee:")) {
            long next = HubProposalCopy.clampFeeCents(
                    draft.feeCents + parseInt(action.substring(4)), Cache.supplyHubMaxFee);
            openNegotiation(player, guild, draft.hostId, draft.installationId, draft.rate, next);
            return;
        }
        if (action.equals("offer")) {
            sendTerms(player, guild, draft);
            return;
        }
        if (action.equals("accept")) {
            finish(player, guild, HubAgreementService.accept(
                    guild, player.getName(), draft.hostId, draft.installationId), menus);
            return;
        }
        if (action.equals("decline")) {
            finish(player, guild, HubAgreementService.decline(
                    guild, player.getName(), draft.hostId, draft.installationId), menus);
            return;
        }
        if (action.equals("withdraw")) {
            finish(player, guild, HubAgreementService.withdraw(
                    guild, player.getName(), draft.hostId, draft.installationId), menus);
            return;
        }
        if (action.startsWith("renew:")) {
            AgreementResult result = HubAgreementService.setAutoRenewal(
                    guild, player.getName(), draft.hostId, draft.installationId, Boolean.parseBoolean(action.substring(6)));
            if (result.message() != null) {
                player.sendMessage(result.message());
            }
            openNegotiation(player, guild, draft.hostId, draft.installationId, draft.rate, draft.feeCents);
        }
    }

    private static void clickOffers(
            InventoryClickEvent event, SFInventoryHolder holder, Player player, InventoryManager menus) {
        if (event.getSlot() == 53) {
            Faction faction = FactionManager.getByMember(player.getName());
            if (faction != null) {
                menus.factionView(player, faction);
            }
            return;
        }
        String data = key(event.getCurrentItem());
        if (data == null) {
            return;
        }
        String[] parts = data.split(SEP, -1);
        if (parts.length < 3) {
            return;
        }
        Guild guild = FactionManager.getGuildByString(parts[0]);
        if (guild == null) {
            return;
        }
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
        openFromExisting(player, guild, parts[1], parts[2]);
    }

    private static void openFromExisting(Player player, Guild guild, String hostId, String installationId) {
        HubOffer offer = HubAgreementService.offer(guild, hostId, installationId);
        HubAgreement agreement = HubAgreementService.findAgreement(guild, hostId, installationId);
        Faction host = FactionManager.getByString(hostId);
        RateRange range = HubAgreementService.allowedRateRange(host);
        int rate = offer != null ? offer.taxRatePercent()
                : agreement != null ? agreement.taxRatePercent()
                : HubProposalCopy.startingRate(range.minPercent(), range.maxPercent());
        long fee = offer != null ? offer.feeCents() : agreement != null ? agreement.feeCents() : 0;
        openNegotiation(player, guild, hostId, installationId, rate, fee);
    }

    private static void sendTerms(Player player, Guild guild, Draft draft) {
        HubOffer offer = HubAgreementService.offer(guild, draft.hostId, draft.installationId);
        AgreementResult result;
        if (offer != null && !waitingOn(player, guild, offer)) {
            player.sendMessage("§7Waiting on the other side.");
            return;
        }
        if (offer == null) {
            result = HubAgreementService.propose(
                    guild, player.getName(), draft.hostId, draft.installationId, draft.rate, draft.feeCents / 100.0);
        } else if (offer.taxRatePercent() == draft.rate && offer.feeCents() == draft.feeCents) {
            player.sendMessage("§7Those are already the offered terms. Accept them, or change the rate or the fee.");
            return;
        } else {
            result = HubAgreementService.counter(
                    guild, player.getName(), draft.hostId, draft.installationId, draft.rate, draft.feeCents / 100.0);
        }
        if (result.message() != null) {
            player.sendMessage(result.message());
        }
        if (result.succeeded()) {
            openFromExisting(player, guild, draft.hostId, draft.installationId);
        }
    }

    private static void finish(Player player, Guild guild, AgreementResult result, InventoryManager menus) {
        if (result != null && result.message() != null) {
            player.sendMessage(result.message());
        }
        if (result != null && result.succeeded() && result.builtHub()) {
            menus.supplyHubView.guildView(player, guild);
            return;
        }
        if (isLeader(guild, player)) {
            openProposals(player, guild, 0);
        } else {
            openOffers(player);
        }
    }

    private static void openOfferList(Player player, String hostFactionId, String installationId) {
        if (player == null) {
            return;
        }
        List<PendingMatter> matters = new ArrayList<>();
        for (PendingMatter matter : HubAgreementService.pendingFor(player.getName())) {
            if (matter == null) {
                continue;
            }
            if (hostFactionId != null && (matter.hostFactionId() == null
                    || !matter.hostFactionId().equalsIgnoreCase(hostFactionId))) {
                continue;
            }
            if (installationId != null && (matter.installationId() == null
                    || !matter.installationId().equalsIgnoreCase(installationId))) {
                continue;
            }
            matters.add(matter);
        }
        String filter = hostFactionId == null ? null : hostFactionId + SEP + installationId;
        Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
                new SFInventoryHolder(player.getName(), SFGUI.HUB_OFFER_LIST, filter),
                54, "§7Hub Offers");
        if (matters.isEmpty()) {
            inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§7Nothing is waiting", List.of(
                    "§7Offers and agreements that are about to end show up here.")));
        }
        for (int index = 0; index < matters.size() && index < PAGE_SIZE; index++) {
            PendingMatter matter = matters.get(index);
            ItemStack item = SupplyHubCreator.item(Material.WRITABLE_BOOK, "§eHub offer", List.of(
                    matter.message() == null ? "§7An offer is waiting" : matter.message(),
                    "§eClick to open"));
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING,
                    matter.guildId() + SEP + matter.hostFactionId() + SEP + matter.installationId());
            item.setItemMeta(meta);
            inventory.setItem(index, item);
        }
        inventory.setItem(53, backButton(SFGUI.HUB_OFFER_LIST));
        player.openInventory(inventory);
    }

    private static Destination findDestination(Guild guild, String hostId, String installationId) {
        for (Destination destination : HubEstimates.destinations(guild)) {
            if (destination.installationId() == null || destination.hostFactionId() == null) {
                continue;
            }
            if (destination.hostFactionId().equalsIgnoreCase(hostId)
                    && destination.installationId().equalsIgnoreCase(installationId)) {
                return destination;
            }
        }
        return null;
    }

    private static Map<String, String> guildNames(Destination destination) {
        Map<String, String> names = new LinkedHashMap<>();
        if (destination == null || destination.hostGuildGains() == null) {
            return names;
        }
        for (String guildId : destination.hostGuildGains().keySet()) {
            Guild guild = FactionManager.getGuildByString(guildId);
            names.put(guildId, guild == null || guild.getName() == null ? guildId : guild.getName());
        }
        return names;
    }

    private static ItemStack action(Material material, String name, String action) {
        ItemStack item = SupplyHubCreator.item(material, name, List.of());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack backButton(SFGUI gui) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§cBack");
        if (SimpleFactions.plugin != null) {
            NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "gui");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, gui.name());
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String key(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static boolean waitingOn(Player player, Guild guild, HubOffer offer) {
        if (offer == null || player == null) {
            return false;
        }
        Faction host = FactionManager.getByString(offer.hostFactionId());
        return (offer.awaiting() == OfferSide.GUILD && isLeader(guild, player))
                || (offer.awaiting() == OfferSide.HOST && isCouncil(host, player));
    }

    private static boolean isLeader(Guild guild, Player player) {
        return guild != null && player != null && player.getName().equalsIgnoreCase(guild.getLeader());
    }

    private static boolean isCouncil(Faction host, Player player) {
        if (host == null || player == null) {
            return false;
        }
        Government government = host.getGovernment();
        return government != null && government.isCouncilMember(player);
    }

    private record Draft(String hostId, String installationId, int rate, long feeCents) {
        static Draft parse(String raw) {
            if (raw == null) {
                return null;
            }
            String[] parts = raw.split(SEP, -1);
            if (parts.length < 4 || parts[0].isEmpty() || parts[1].isEmpty()) {
                return null;
            }
            try {
                return new Draft(parts[0], parts[1], Integer.parseInt(parts[2]), Long.parseLong(parts[3]));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
