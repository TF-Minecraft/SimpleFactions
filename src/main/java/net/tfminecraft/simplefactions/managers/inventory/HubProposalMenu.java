package net.tfminecraft.simplefactions.managers.inventory;

import java.util.ArrayList;
import java.util.List;

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
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Site;
import net.tfminecraft.simplefactions.guild.hub.HubOffer;
import net.tfminecraft.simplefactions.guild.hub.HubProposalCopy;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService;
import net.tfminecraft.simplefactions.guild.hub.OfferSide;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;

/**
 * The proposal list, the shared negotiation chest, and the host's offer list.
 */
public final class HubProposalMenu {
    private static final String SEP = "\u001f";
    private static final int PAGE_SIZE = 45;

    private HubProposalMenu() {
    }

    public static void openProposals(Player player, Guild guild, int page) {
        openProposals(player, guild, page, false);
    }

    static void openProposals(Player player, Guild guild, int page, boolean showAll) {
        if (player == null || guild == null) {
            return;
        }
        if (!isLeader(guild, player)) {
            player.sendMessage("§cOnly the guild leader can propose a hub");
            return;
        }
        List<Site> sites = HubEstimates.sites(guild);
        boolean noHubs = guild.getSupplyHubs() == null || guild.getSupplyHubs().isEmpty();
        boolean focused = noHubs && !showAll;
        List<Site> territory = noHubs ? HubEstimates.territory(sites) : sites;
        List<Site> tradable = noHubs ? HubEstimates.withTrade(territory) : territory;
        List<Site> listed = noHubs
                ? (focused ? HubEstimates.bestTerritory(tradable) : tradable)
                : HubEstimates.connected(sites);
        listed = noHubs ? HubEstimates.byTradePower(listed) : HubEstimates.byArrival(listed);
        int pages = Math.max(1, (listed.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.max(0, Math.min(page, pages - 1));
        Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
                new SFInventoryHolder(guild.getId(), SFGUI.HUB_PROPOSAL_LIST, shown, showAll && noHubs),
                54, noHubs ? "§7Main Hub" : "§7Propose a Hub");
        if (listed.isEmpty()) {
            if (noHubs && territory.isEmpty()) {
                inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§7You have no installation", List.of(
                        "§7Build a port, airport, or train station in your territory.")));
            } else if (noHubs) {
                inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§7No trade power in your territory", List.of(
                        "§7A main hub is placed where your guild already has trade power.")));
            } else if (!sites.isEmpty()) {
                inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§7No connections can be made", List.of(
                        "§7You might need a rail")));
            } else {
                inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§7No installation can take a hub", List.of(
                        "§7Every open port, airport, and train station already has your hub, or has no free slot.")));
            }
        }
        int start = shown * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < listed.size(); index++) {
            Site destination = listed.get(start + index);
            Faction host = FactionManager.getByString(destination.hostFactionId());
            List<String> lore = new ArrayList<>();
            lore.add("§7Host: §f" + (host == null ? destination.hostFactionId() : host.getName()));
            lore.addAll(HubProposalCopy.destinationLore(destination, !noHubs));
            if (focused) {
                lore.add(listed.size() > 1
                        ? "§7Tied for the most trade power in your territory"
                        : "§7The most trade power in your territory");
            }
            ItemStack item = SupplyHubCreator.item(Material.CHEST, "§e" + destination.label(), lore);
            ItemMeta meta = item.getItemMeta();
            String installation = destination.installationId() == null ? "" : destination.installationId();
            meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING,
                    destination.hostFactionId() + SEP + installation
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
        if (noHubs && !tradable.isEmpty() && (focused ? tradable.size() > listed.size() : true)) {
            if (focused) {
                inventory.setItem(49, action(Material.BOOK, "§eView all", "view:all", List.of(
                        "§7Every place in your territory with trade power")));
            } else {
                inventory.setItem(49, action(Material.COMPASS, "§eBest in your territory", "view:best", List.of(
                        "§7Only the installations with the most trade power")));
            }
        }
        inventory.setItem(53, backButton(SFGUI.HUB_PROPOSAL_LIST));
        player.openInventory(inventory);
    }

    public static void openOffers(Player player) {
        openOfferList(player, null, null, 0);
    }

    public static void openOffersAt(Player player, String hostFactionId, String installationId) {
        openOfferList(player, hostFactionId, installationId, 0);
    }

    public static void openNegotiation(
            Player player, Guild guild, String hostFactionId, String installationId, int rate, long feeCents) {
        openNegotiation(player, guild, hostFactionId, installationId, rate, feeCents, rate, feeCents);
    }

    static void openNegotiation(
            Player player, Guild guild, String hostFactionId, String installationId,
            int rate, long feeCents, int baseRate, long baseFeeCents) {
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
        int shownBaseRate = HubProposalCopy.clampRate(baseRate, min, max);
        long shownBaseFee = HubProposalCopy.clampFeeCents(baseFeeCents, Cache.supplyHubMaxFee);
        Site destination = findDestination(guild, hostFactionId, installationId);
        HubOffer offer = HubAgreementService.offer(guild, hostFactionId, installationId);
        HubAgreement agreement = HubAgreementService.findAgreement(guild, hostFactionId, installationId);
        boolean leader = isLeader(guild, player);
        boolean council = isCouncil(host, player);
        boolean sentThese = offer != null && offer.lastActor() != null
                && offer.lastActor().equalsIgnoreCase(player.getName());
        boolean yourTurn = offer != null && !sentThese && ((offer.awaiting() == OfferSide.GUILD && leader)
                || (offer.awaiting() == OfferSide.HOST && council));

        Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
                new SFInventoryHolder(guild.getId(), SFGUI.HUB_NEGOTIATION,
                        hostFactionId + SEP + installationId + SEP + shownRate + SEP + shownFee
                                + SEP + shownBaseRate + SEP + shownBaseFee),
                54, "§7Hub Agreement");
        String site = destination == null ? installationId : destination.label();
        inventory.setItem(4, SupplyHubCreator.item(Material.PAPER, "§eYour side",
                HubProposalCopy.powerLines(destination)));
        List<String> termsLore = new ArrayList<>();
        termsLore.add("§7" + site);
        termsLore.add("§7Rate: §e" + shownRate + "% §7(§e" + min + "-" + max + "%§7)");
        termsLore.add("§7Daily fee: §e" + Formatter.formatMoney(shownFee / 100.0));
        termsLore.add("§7Term: §e" + Math.max(1, Cache.supplyHubAgreementDays) + " days");
        if (Cache.supplyHubAutoAccept) {
            termsLore.add("§7Dev server: sending these terms accepts them.");
        }
        if (offer != null && (offer.taxRatePercent() != shownRate || offer.feeCents() != shownFee)) {
            termsLore.add("§7Offered now: §e" + offer.taxRatePercent() + "%§7 and §e"
                    + Formatter.formatMoney(offer.feeCents() / 100.0));
            termsLore.add("§7Your unsent terms stay on this screen");
        }
        if (offer != null) {
            termsLore.add(yourTurn ? "§eWaiting on you" : "§7Waiting on the other side");
        } else if (agreement != null) {
            termsLore.add("§7This hub already has an agreement");
        }
        inventory.setItem(13, SupplyHubCreator.item(Material.GOLD_INGOT, "§eTerms", termsLore));
        inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§eHost side",
                HubProposalCopy.hostLines(destination)));

        inventory.setItem(29, action(Material.NAME_TAG, "§eSet rate", "type:rate", List.of(
                "§7Now §e" + shownRate + "%",
                "§7Click, then type the percent in chat")));
        if (offer != null && (offer.taxRatePercent() != shownRate || offer.feeCents() != shownFee)) {
            inventory.setItem(30, action(Material.GOLD_NUGGET, "§eUse offered terms", "adopt"));
        }
        inventory.setItem(31, action(Material.NAME_TAG, "§eSet fee", "type:fee", List.of(
                "§7Now §e" + Formatter.formatMoney(shownFee / 100.0),
                "§7Click, then type the fee in chat")));

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
                    openProposals(player, guild, holder.getPage(), holder.getFlag());
                }
            } else if (holder.getType() == SFGUI.HUB_OFFER_LIST) {
                String[] filter = offerFilter(holder.getSecondaryId());
                openOfferList(player, filter[0], filter[1], holder.getPage());
            } else if (holder.getType() == SFGUI.HUB_NEGOTIATION) {
                Guild guild = FactionManager.getGuildByString(holder.getId());
                Draft draft = Draft.parse(holder.getSecondaryId());
                if (guild == null || draft == null) {
                    return;
                }
                HubOffer offer = HubAgreementService.offer(guild, draft.hostId, draft.installationId);
                Draft next = refreshed(draft, offer);
                openNegotiation(player, guild, next.hostId, next.installationId,
                        next.rate, next.feeCents, next.baseRate, next.baseFeeCents);
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
            openProposals(player, guild, holder.getPage() - 1, holder.getFlag());
            return;
        }
        if (event.getSlot() == 50) {
            openProposals(player, guild, holder.getPage() + 1, holder.getFlag());
            return;
        }
        String data = key(event.getCurrentItem());
        if (data == null) {
            return;
        }
        if (data.equals("view:all")) {
            openProposals(player, guild, 0, true);
            return;
        }
        if (data.equals("view:best")) {
            openProposals(player, guild, 0, false);
            return;
        }
        String[] parts = data.split(SEP, -1);
        if (parts.length < 4 || parts[1].isEmpty()) {
            return;
        }
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
        boolean noHubs = guild.getSupplyHubs() == null || guild.getSupplyHubs().isEmpty();
        boolean ownRealm = parts[3].equals("own");
        if (noHubs && !ownRealm) {
            player.sendMessage("§cYour first hub has to be in your own territory");
            return;
        }
        if (SupplyHubService.hasHub(guild.getSupplyHubs(), parts[0], parts[1])) {
            player.sendMessage("§cYour guild already has a supply hub there");
            openProposals(player, guild, holder.getPage(), holder.getFlag());
            return;
        }
        Site destination = findDestination(guild, parts[0], parts[1]);
        if (ownRealm || (destination != null && destination.ownRealm())) {
            menus.confirmSupplyHub(player, guild.getFaction(),
                    "build|" + guild.getId() + "|" + parts[0] + "|" + parts[1]);
            return;
        }
        openFromExisting(player, guild, parts[0], parts[1]);
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
        if (action.equals("type:rate") || action.equals("type:fee")) {
            HubTermsPrompt.ask(player, guild, draft.hostId, draft.installationId,
                    draft.rate, draft.feeCents, draft.baseRate, draft.baseFeeCents,
                    action.equals("type:rate"));
            return;
        }
        if (action.equals("adopt")) {
            HubOffer offer = HubAgreementService.offer(guild, draft.hostId, draft.installationId);
            if (offer != null) {
                openNegotiation(player, guild, draft.hostId, draft.installationId,
                        offer.taxRatePercent(), offer.feeCents());
            }
            return;
        }
        if (action.equals("offer")) {
            sendTerms(player, guild, draft, menus);
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
            openNegotiation(player, guild, draft.hostId, draft.installationId,
                    draft.rate, draft.feeCents, draft.baseRate, draft.baseFeeCents);
        }
    }

    private static void clickOffers(
            InventoryClickEvent event, SFInventoryHolder holder, Player player, InventoryManager menus) {
        String[] filter = offerFilter(holder.getSecondaryId());
        if (event.getSlot() == 48) {
            openOfferList(player, filter[0], filter[1], holder.getPage() - 1);
            return;
        }
        if (event.getSlot() == 50) {
            openOfferList(player, filter[0], filter[1], holder.getPage() + 1);
            return;
        }
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

    private static void sendTerms(Player player, Guild guild, Draft draft, InventoryManager menus) {
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
        if (result.succeeded() && Cache.supplyHubAutoAccept) {
            AgreementResult accepted = HubAgreementService.acceptAutomatically(
                    guild, draft.hostId, draft.installationId);
            if (accepted.message() != null) {
                player.sendMessage(accepted.message());
            }
            if (accepted.succeeded() && accepted.builtHub()) {
                menus.supplyHubView.guildView(player, guild);
                return;
            }
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

    private static void openOfferList(Player player, String hostFactionId, String installationId, int page) {
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
        int pages = Math.max(1, (matters.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.max(0, Math.min(page, pages - 1));
        String filter = hostFactionId == null ? null : hostFactionId + SEP + installationId;
        Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
                new SFInventoryHolder(player.getName(), SFGUI.HUB_OFFER_LIST, shown, false, filter),
                54, "§7Hub Offers");
        if (matters.isEmpty()) {
            inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§7Nothing is waiting", List.of(
                    "§7Offers and agreements that are about to end show up here.")));
        }
        List<PendingMatter> visible = page(matters, shown, PAGE_SIZE);
        for (int index = 0; index < visible.size(); index++) {
            PendingMatter matter = visible.get(index);
            ItemStack item = SupplyHubCreator.item(Material.WRITABLE_BOOK, "§eHub offer", List.of(
                    matter.message() == null ? "§7An offer is waiting" : matter.message(),
                    "§eClick to open"));
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING,
                    matter.guildId() + SEP + matter.hostFactionId() + SEP + matter.installationId());
            item.setItemMeta(meta);
            inventory.setItem(index, item);
        }
        if (shown > 0) {
            inventory.setItem(48, SupplyHubCreator.item(Material.ARROW, "§ePrevious page", List.of()));
        }
        if (shown + 1 < pages) {
            inventory.setItem(50, SupplyHubCreator.item(Material.ARROW, "§eNext page", List.of()));
        }
        inventory.setItem(53, backButton(SFGUI.HUB_OFFER_LIST));
        player.openInventory(inventory);
    }

    private static String[] offerFilter(String secondaryId) {
        if (secondaryId == null) {
            return new String[] {null, null};
        }
        String[] parts = secondaryId.split(SEP, -1);
        if (parts.length < 2) {
            return new String[] {null, null};
        }
        return new String[] {parts[0], parts[1]};
    }

    private static Site findDestination(Guild guild, String hostId, String installationId) {
        for (Site destination : HubEstimates.sites(guild)) {
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

    private static ItemStack action(Material material, String name, String action) {
        return action(material, name, action, List.of());
    }

    private static ItemStack action(Material material, String name, String action, List<String> lore) {
        ItemStack item = SupplyHubCreator.item(material, name, lore);
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

    /** The buttons show {@code rate} and {@code feeCents}. {@code baseRate} is the offer they were editing from. */
    record Draft(String hostId, String installationId, int rate, long feeCents, int baseRate, long baseFeeCents) {
        static Draft parse(String raw) {
            if (raw == null) {
                return null;
            }
            String[] parts = raw.split(SEP, -1);
            if (parts.length < 4 || parts[0].isEmpty() || parts[1].isEmpty()) {
                return null;
            }
            try {
                int rate = Integer.parseInt(parts[2]);
                long fee = Long.parseLong(parts[3]);
                int baseRate = parts.length >= 6 ? Integer.parseInt(parts[4]) : rate;
                long baseFee = parts.length >= 6 ? Long.parseLong(parts[5]) : fee;
                return new Draft(parts[0], parts[1], rate, fee, baseRate, baseFee);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    /** Keeps an unsent rate or fee. A screen that still matches the last offer follows the new terms. */
    static Draft refreshed(Draft draft, HubOffer offer) {
        if (draft == null || offer == null) {
            return draft;
        }
        boolean unsent = draft.rate != draft.baseRate || draft.feeCents != draft.baseFeeCents;
        if (!unsent) {
            return new Draft(draft.hostId, draft.installationId,
                    offer.taxRatePercent(), offer.feeCents(), offer.taxRatePercent(), offer.feeCents());
        }
        return new Draft(draft.hostId, draft.installationId,
                draft.rate, draft.feeCents, offer.taxRatePercent(), offer.feeCents());
    }

    static <T> List<T> page(List<T> items, int page, int pageSize) {
        if (items == null || items.isEmpty() || pageSize < 1) {
            return List.of();
        }
        int pages = Math.max(1, (items.size() + pageSize - 1) / pageSize);
        int shown = Math.max(0, Math.min(page, pages - 1));
        int start = shown * pageSize;
        return items.subList(start, Math.min(items.size(), start + pageSize));
    }
}
