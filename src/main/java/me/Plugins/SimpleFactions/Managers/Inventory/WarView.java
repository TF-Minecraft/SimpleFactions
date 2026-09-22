package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Managers.Holder.SFCombinedInventoryHolder;
import me.Plugins.SimpleFactions.Managers.Holder.WarInventoryHolder;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryEngagements;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryEngagements.Engagement;

public class WarView {
	public static final int SIDE_COLUMN_SIZE = 16;
	public static final HashMap<Player, Integer> engagementPage = new HashMap<>();

	private static final int PREV_PAGE_SLOT = 45;
	private static final int NEXT_PAGE_SLOT = 53;
	private static final List<Integer> ENGAGEMENT_RESERVED = List.of(
			8, 17, 26, 35, 44, PREV_PAGE_SLOT, NEXT_PAGE_SLOT);

	public record SideColumnLayout(int factionsShown, int engagementsShown, boolean overflowOpener) {
		public static SideColumnLayout of(int factions, int engagements, int columnSize) {
			int size = Math.max(1, columnSize);
			int total = Math.max(0, factions) + Math.max(0, engagements);
			if (total <= size) {
				return new SideColumnLayout(Math.max(0, factions), Math.max(0, engagements), false);
			}
			int room = size - 1;
			int shownFactions = Math.min(Math.max(0, factions), room);
			int shownEngagements = Math.min(Math.max(0, engagements), room - shownFactions);
			return new SideColumnLayout(shownFactions, shownEngagements, true);
		}

		public int hiddenEngagements(int engagements) {
			return Math.max(0, engagements - engagementsShown);
		}
	}
	public InventoryManager inv;
	public WarCreator creator = new WarCreator();
	
	public WarView(InventoryManager inv) {
		this.inv = inv;
	}
	
	public void warList(Player player) {
		warList(player, null);
	}

	public void warList(Player player, Inventory i) {
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(
				new SFInventoryHolder("", SFGUI.WAR_LIST), 54, "§7War List");
		populateWarList(i);
		if(open) player.openInventory(i);
	}

	public void populateWarList(Inventory i) {
		i.clear();
		List<War> activeWars = WarManager.getActive();
		for(int x = 0; x < activeWars.size(); x++) {
			i.setItem(x, creator.createWarItem(activeWars.get(x), true));
		}
	}
	
	public void warView(Inventory i, Player player, War w, boolean open) {
		w.update();
		if(open) {
			i = SimpleFactions.plugin.getServer().createInventory(new WarInventoryHolder(w.getId(), SFGUI.WAR_VIEW), 54, w.getName());
		}
		List<Integer> gray = Arrays.asList(0, 1, 2, 6, 7, 8, 45, 46, 47, 48, 50, 51, 52);
		List<Integer> red = Arrays.asList(13, 22, 31, 40, 49);
		
		List<Integer> attackerSide = Arrays.asList(9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39);
		List<Integer> defenderSide = Arrays.asList(14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35, 41, 42, 43, 44);
		
		i.setItem(3, creator.createParticipantItem(player, w.getAttackers().getMainParticipants().get(0), "main_attacker", w, false, false));
		i.setItem(4, creator.createWarItem(w, false));
		i.setItem(5, creator.createParticipantItem(player, w.getDefenders().getMainParticipants().get(0), "main_defender", w, false, false));

		fillSideColumn(i, player, w, w.getAttackers(), attackerSide, "mercenary_attacker", BattleTemplate.ATTACKER_SIDE);
		fillSideColumn(i, player, w, w.getDefenders(), defenderSide, "mercenary_defender", BattleTemplate.DEFENDER_SIDE);
		
		for(int x : gray) {
			i.setItem(x, inv.getFiller(Material.GRAY_STAINED_GLASS_PANE));
		}
		boolean showCampaign = canShowCampaignView(w, player);
		for(int x : red) {
			if (x == 49 && showCampaign) {
				continue;
			}
			i.setItem(x, inv.getFiller(Material.RED_STAINED_GLASS_PANE));
		}
		if (showCampaign) {
			i.setItem(49, creator.createCampaignButton(w));
		}
		i.setItem(53, inv.createBackButton(SFGUI.WAR_VIEW));
		if(open) player.openInventory(i);
	}

	private void fillSideColumn(
			Inventory i,
			Player player,
			War w,
			Side side,
			List<Integer> slots,
			String mercMarker,
			String sideId) {
		List<Participant> mains = side.getMainParticipants();
		List<Engagement> engagements = MercenaryEngagements.on(w, side);
		SideColumnLayout layout = SideColumnLayout.of(mains.size(), engagements.size(), slots.size());
		int index = 0;
		String factionMarker = mercMarker.endsWith("defender") ? "main_defender" : "main_attacker";
		for (int x = 0; x < layout.factionsShown(); x++) {
			i.setItem(slots.get(index++), creator.createParticipantItem(
					player, mains.get(x), factionMarker, w, true, false));
		}
		for (int x = 0; x < layout.engagementsShown(); x++) {
			i.setItem(slots.get(index++), creator.createMercenaryItem(engagements.get(x), mercMarker));
		}
		if (layout.overflowOpener()) {
			i.setItem(slots.get(slots.size() - 1), creator.createOverflowOpener(
					sideId, layout.hiddenEngagements(engagements.size())));
		}
	}

	public void engagementList(Player player, War w, String sideId) {
		engagementList(player, w, sideId, null);
	}

	public void engagementList(Player player, War w, String sideId, Inventory inventory) {
		engagementPage.putIfAbsent(player, 0);
		boolean open = inventory == null;
		if (open) {
			inventory = SimpleFactions.plugin.getServer().createInventory(
					new SFCombinedInventoryHolder(w.getId(), sideId, SFGUI.MERCENARY_ENGAGEMENT_LIST),
					54,
					"§7Hired companies");
		}
		populateEngagementList(inventory, player, w, sideId);
		if (open) player.openInventory(inventory);
	}

	public void populateEngagementList(Inventory inventory, Player player, War w, String sideId) {
		engagementPage.putIfAbsent(player, 0);
		int page = engagementPage.get(player);
		Side side = BattleTemplate.DEFENDER_SIDE.equalsIgnoreCase(sideId)
				? w.getDefenders() : w.getAttackers();
		List<Engagement> engagements = MercenaryEngagements.on(w, side);
		String marker = BattleTemplate.DEFENDER_SIDE.equalsIgnoreCase(sideId)
				? "mercenary_defender" : "mercenary_attacker";

		List<Integer> usable = new ArrayList<>();
		for (int s = 0; s < 54; s++) {
			if (!ENGAGEMENT_RESERVED.contains(s)) usable.add(s);
		}
		inventory.clear();
		int perPage = usable.size();
		int start = page * perPage;
		int end = Math.min(start + perPage, engagements.size());
		for (int x = start; x < end; x++) {
			inventory.setItem(usable.get(x - start), creator.createMercenaryItem(engagements.get(x), marker));
		}
		inventory.setItem(8, inv.createBackButton(SFGUI.MERCENARY_ENGAGEMENT_LIST));
		if (page > 0) inventory.setItem(PREV_PAGE_SLOT, DefaultCreator.createPreviousPageButton());
		if (end < engagements.size()) {
			inventory.setItem(NEXT_PAGE_SLOT, DefaultCreator.createNextPageButton());
		}
	}
	
	public void participantView(Inventory i, Player player, War w, Participant p, boolean open) {
		w.update();
		if(open) {
			i = SimpleFactions.plugin.getServer().createInventory(new SFCombinedInventoryHolder(w.getId(), p.getLeader().getId(), SFGUI.PARTICIPANT_VIEW), 54, w.getName());
		}
		List<Integer> gray = Arrays.asList(0, 1, 2, 3, 5, 6, 7, 8, 45, 46, 47, 48, 49, 50, 51, 52);
		
		i.setItem(4, creator.createParticipantItem(player, p, w.getType(p.getLeader()), w, false, true));
		int slots = 0;
		int offset = 9;
		for(int x = 0; x < p.getSubjects().size(); x++) {
			int slot = x+offset;
			i.setItem(slot, creator.createSecondaryItem(player, p, w, p.getSubjects().get(x), true, true));
			slots++;
		}
		offset += slots;
		List<Faction> allies = new ArrayList<>(p.getAllies().keySet());
		for(int x = 0; x < allies.size(); x++) {
			int slot = x+offset;
			i.setItem(slot, creator.createSecondaryItem(player, p, w, allies.get(x), false, p.getAllies().get(allies.get(x))));
			slots++;
		}
		offset += allies.size();
		List<Faction> backers = p.getBackers();
		for(int x = 0; x < backers.size(); x++) {
			int slot = x+offset;
			i.setItem(slot, creator.createSecondaryItem(player, p, w, backers.get(x), false, true, true));
			slots++;
		}
		for(int x : gray) {
			i.setItem(x, inv.getFiller(Material.GRAY_STAINED_GLASS_PANE));
		}
		i.setItem(53, inv.createBackButton(SFGUI.PARTICIPANT_VIEW));
		if(open) player.openInventory(i);
	}
	
	public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		if(e.getView().getTitle().equalsIgnoreCase("§7War List")) {
			e.setCancelled(true);
			if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			Integer id = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
			if(id == null) return;
			War w = WarManager.getById(id);
			if (w == null) return;
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			warView(null, p, w, true);
		} else if(inventory.getHolder() instanceof WarInventoryHolder && ((WarInventoryHolder) inventory.getHolder()).getType().equals(SFGUI.WAR_VIEW)) {
			e.setCancelled(true);
			WarInventoryHolder h = (WarInventoryHolder) inventory.getHolder();
			War w = WarManager.getById(h.getId());
			if(e.getSlot() == 53) {
				warList(p);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			} else if(e.getSlot() == 49) {
				if (!canShowCampaignView(w, p)) {
					return;
				}
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				inv.openCampaignView(p, w);
				return;
			}
			if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
			NamespacedKey overflow = new NamespacedKey(SimpleFactions.plugin, "mercenary_overflow");
			String overflowSide = e.getCurrentItem().getItemMeta().getPersistentDataContainer()
					.get(overflow, PersistentDataType.STRING);
			if (overflowSide != null) {
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				engagementList(p, w, overflowSide);
				return;
			}
			if (openContractFromItem(e.getCurrentItem(), p, w)) return;
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			String id = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if(id == null) return;
			Faction f = FactionManager.getByString(id);
			if(f == null) return;
			Participant par = w.getParticipant(f);
			if(par == null) return;
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			participantView(null, p, w, par, true);
		} else if(inventory.getHolder() instanceof SFCombinedInventoryHolder
				&& ((SFCombinedInventoryHolder) inventory.getHolder()).getType().equals(SFGUI.MERCENARY_ENGAGEMENT_LIST)) {
			e.setCancelled(true);
			SFCombinedInventoryHolder h = (SFCombinedInventoryHolder) inventory.getHolder();
			War w = WarManager.getById(h.getWarId());
			if (w == null) return;
			if (e.getSlot() == 8) {
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				warView(null, p, w, true);
				return;
			}
			if (e.getSlot() == PREV_PAGE_SLOT) {
				engagementPage.put(p, Math.max(0, engagementPage.getOrDefault(p, 0) - 1));
				engagementList(p, w, h.getFactionId(), inventory);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			}
			if (e.getSlot() == NEXT_PAGE_SLOT) {
				engagementPage.put(p, engagementPage.getOrDefault(p, 0) + 1);
				engagementList(p, w, h.getFactionId(), inventory);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			}
			openContractFromItem(e.getCurrentItem(), p, w);
		} else if(inventory.getHolder() instanceof SFCombinedInventoryHolder && ((SFCombinedInventoryHolder) inventory.getHolder()).getType().equals(SFGUI.PARTICIPANT_VIEW)) {
			e.setCancelled(true);
			SFCombinedInventoryHolder h = (SFCombinedInventoryHolder) inventory.getHolder();
			War w = WarManager.getById(h.getWarId());
			if(e.getSlot() == 53) {
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				warView(null, p, w, true);
				return;
			}
			Faction pf = FactionManager.getByLeader(p.getName());
			if(pf == null) return;
			Participant par = w.getParticipant(pf);
			if(par == null) return;
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			String id = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if(id == null) return;
			Faction f = FactionManager.getByString(id);
			if(f == null) return;
			if(par.getAllies().containsKey(f)) {
				if(par.getAllies().get(f)) return;
				if(!w.canBeCalled(pf, f)) return;
				WarManager.sendRequest(p, pf, f, w);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			}
		}
	}

	private boolean canShowCampaignView(War w, Player player) {
		if (w == null || !w.isActive() || w.getWarType() == WarType.RAID) {
			return false;
		}
		List<Integer> axis = w.getCampaignProvinces();
		if (axis == null || axis.isEmpty()) {
			return false;
		}
		Faction faction = FactionManager.getByLeader(player.getName());
		if (faction == null) {
			faction = FactionManager.getByMember(player.getName());
		}
		return faction != null && w.getParticipant(faction) != null;
	}

	private boolean openContractFromItem(ItemStack item, Player p, War w) {
		if (item == null || !item.hasItemMeta() || w == null) return false;
		String contractId = item.getItemMeta().getPersistentDataContainer()
				.get(Keys.CONTRACT_ID, PersistentDataType.STRING);
		if (contractId == null) return false;
		for (Engagement engagement : MercenaryEngagements.on(w, w.getAttackers())) {
			if (openContract(p, engagement, contractId)) return true;
		}
		for (Engagement engagement : MercenaryEngagements.on(w, w.getDefenders())) {
			if (openContract(p, engagement, contractId)) return true;
		}
		return false;
	}

	private boolean openContract(Player p, Engagement engagement, String contractId) {
		if (engagement == null || engagement.contract() == null) return false;
		if (!engagement.contract().getId().equalsIgnoreCase(contractId)) return false;
		if (engagement.company() == null || engagement.company().getGuild() == null) return false;
		p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		inv.contractDetailView(p, engagement.company().getGuild(), contractId);
		return true;
	}
}
