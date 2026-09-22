package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.List;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.checkerframework.checker.units.qual.g;

import me.Plugins.SimpleFactions.War.civilwar.CivilWarCopy;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarHostMovementRules;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.resolution.CouncilPeaceQueries;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.Rules;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.Loaders.PoliticalActionLoader;
import me.Plugins.SimpleFactions.government.Council;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.PoliticalAction;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.laws.CanHaveLaw;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

public class GovernmentView {
    public InventoryManager inv;
	
	public GovernmentCreator creator = new GovernmentCreator();
	public LawCreator lawCreator = new LawCreator();

	private static final List<Integer> SLOTS = List.of(
		10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25
	);
	
	public GovernmentView(InventoryManager inv) {
        this.inv = inv;
    }

    public void governmentView(Player player, Faction f, Inventory i) {
        boolean open = i == null;
		if(i == null) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.GOVERNMENT_VIEW), 54, "§7Government View");
		i.clear();
		i.setItem(10, creator.createGovernmentItem(f));
		i.setItem(11, creator.createStabilityItem(f));
		i.setItem(12, creator.createCouncilItem(f));
		i.setItem(15, creator.createProposalItem(player, f));
		if(f.hasFactionRule(Rules.CAN_FAVOUR) | f.hasFactionRule(Rules.CAN_REPRESS)) i.setItem(16, creator.createFavourRepressEntryButton());
		i.setItem(24, creator.createProposalListItem(player, f));
		i.setItem(33, creator.createMovementListItem(player, f));
		Government gov = f.getGovernment();
		if(gov.getCouncil().canBeMember(player.getName(), true, true)) i.setItem(21, creator.createToggleRefuseButton(player, f));
		if(gov.hasElections()) i.setItem(14, creator.createElectionItem(player, f));
		if(gov.getCouncil().canHostSession() && f.getLeader().equalsIgnoreCase(player.getName())) {
			i.setItem(23, creator.createStartCouncilButton(player, f));
		}
		
		Guild g = FactionManager.getGuildByMember(player.getName());
		if(g != null && gov.canAffectStability(g)) {
			i.setItem(28, creator.createStanceItem(f, g));
		}
		i.setItem(53, inv.createBackButton(SFGUI.GOVERNMENT_VIEW));
        if(open) player.openInventory(i);
	}

	public void councilView(Player player, Faction f, Inventory i) {
        boolean open = i == null;
		if(i == null) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.COUNCIL_VIEW), 54, "§7Council");
		i.clear();
		int x = 0;
		for(int slot : SLOTS) {
			if(x >= f.getGovernment().getCouncil().getMaxSize()) break;
			i.setItem(slot, creator.createCouncilMemberItem(player, f, x));
			x++;
		}
		i.setItem(53, inv.createBackButton(SFGUI.COUNCIL_VIEW));
        if(open) player.openInventory(i);
	}
	public void councilSelect(Player player, Faction f, Inventory i, int slot) {
        boolean open = i == null;
		if(i == null) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.COUNCIL_SELECT), 54, "§7Select Member");
		i.clear();
		int x = 0;
		List<String> members = f.getMembers();
		Council council = f.getGovernment().getCouncil();
		members.removeAll(f.getGovernment().getCouncil().getMembers());
		members.addAll(f.getVassalMembers());
		for(String member : members) {
			if(!council.canBeMember(member, true, false)) continue;
			i.setItem(x, creator.createPotentialMemberItem(player, f, member, slot));
			x++;
		}
		i.setItem(53, inv.createBackButton(SFGUI.COUNCIL_SELECT));
        if(open) player.openInventory(i);
	}

	public void proposalList(Player player, Faction f, Inventory i) {
		boolean open = i == null;
		if(i == null) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.PROPOSALS), 27, "§7Proposals");
		i.clear();
		for(Proposal proposal : f.getGovernment().getCouncil().getProposalHandler().getProposals()) {
			i.addItem(creator.createCurrentProposalItem(player, f, proposal));
		}
		i.setItem(26, inv.createBackButton(SFGUI.PROPOSALS));
		if(open) player.openInventory(i);
	}

	public void proposalView(Player player, Faction f, Inventory i) {
		boolean open = i == null;
		if(i == null) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.PROPOSAL_VIEW), 9, "§7Select Proposal Type");
		i.clear();
		i.setItem(0, creator.createProposalTypeItem("law"));
		i.setItem(1, creator.createProposalTypeItem("tax"));
		if((!f.getGovernment().canPropose(player) && f.getGovernment().canProposeOrStartMovement(player))
				|| (f.getGovernment().canPropose(player) && CouncilPeaceQueries.isParticipatingInAny(f)))
			i.setItem(2, creator.createProposalTypeItem("political"));
		i.setItem(8, inv.createBackButton(SFGUI.PROPOSAL_VIEW));
		if(open) player.openInventory(i);
	}

	public void politicalProposalView(Player player, Faction f, Inventory i) {
		boolean open = i == null;
		if(i == null) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.POLITICAL_PROPOSAL_VIEW), 27, "§7Select Political Change");
		i.clear();
		int x = 0;
		for(Action action : Action.values()) {
			if(action == Action.TAX_CHANGE || action == Action.LAW_CHANGE) continue; //handled by other views
			if(!f.getGovernment().canProposePolitical(player, action)) continue;
			i.setItem(x, creator.createPoliticalProposalTypeItem(player, f, action));
			x++;
		}
		i.setItem(26, inv.createBackButton(SFGUI.POLITICAL_PROPOSAL_VIEW));
		if(open) player.openInventory(i);
	}

	public void warPeaceSelectView(Player player, Faction f, Action action, boolean fromCause, int causeIndex, Inventory i) {
		boolean open = i == null;
		if (i == null) {
			i = SimpleFactions.plugin.getServer().createInventory(
					new SFInventoryHolder(f.getId(), SFGUI.WAR_PEACE_SELECT, causeIndex, fromCause, action.name()),
					54,
					"§7Select War");
		}
		i.clear();
		int x = 0;
		for (War war : CouncilPeaceQueries.warsFor(f)) {
			i.setItem(x, creator.createWarPeaceSelectItem(war, action));
			x++;
			if (x >= 53) {
				break;
			}
		}
		i.setItem(53, inv.createBackButton(SFGUI.WAR_PEACE_SELECT));
		if (open) {
			player.openInventory(i);
		}
	}

	public void taxProposalView(Player player, Faction f, Inventory i) {
		boolean open = i == null;
		if(i == null) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.TAX_PROPOSAL_VIEW), 9, "§7Select Tax Type");
		i.clear();
		int x = 0;
		for(TaxTarget target : TaxTarget.values()) {
			if(!f.getTaxHandler().canCollectTax(target)) continue;
			i.setItem(x, creator.createTaxTypeItem(player, f, target, true));
			x++;
		}
		i.setItem(8, inv.createBackButton(SFGUI.TAX_PROPOSAL_VIEW));
		if(open) player.openInventory(i);
	}

	public void specificTaxProposalView(Player player, Faction f, Inventory i, TaxTarget target) {
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.SPECIFIC_TAX_PROPOSAL_VIEW, target.name()), 54, "§7Select Target");
		i.clear();
		int x = 0;
		if(target == TaxTarget.GUILD_ID) {
			for(Guild g : f.getGuildHandler().getGuilds()) {
				if(g.isBase()) continue;
				i.setItem(x, creator.createSpecificTaxItem(player, f, g.getId(), target));
				if(x >= 53) break;
				x++;
			}
		} else if(target == TaxTarget.VASSAL_ID) {
			for(Faction s : RelationManager.getSubjects(f)) {
				i.setItem(x, creator.createSpecificTaxItem(player, f, s.getId(), target));
				if(x >= 53) break;
				x++;
			}
		} else if(target == TaxTarget.TARIFF_ID) {
			for(Faction fac : FactionManager.factions) {
				if(fac.getId().equalsIgnoreCase(f.getId())) continue;
				if(RelationManager.sameRealm(fac, f)) continue;
				if(x >= 53) break;
				i.setItem(x, creator.createSpecificTaxItem(player, f, fac.getId(), target));
				x++;
			}
		}
		i.setItem(53, inv.createBackButton(SFGUI.SPECIFIC_TAX_PROPOSAL_VIEW));
		if(open) player.openInventory(i);
	}

	public void lawProposalView(Player player, Faction f, Inventory i) {
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.LAW_PROPOSAL_VIEW), 54, "§7Select Law Group");
		i.clear();
		for(int x = 0; x<f.getLawHandler().getGroupList().size(); x++) {
			LawGroup group = f.getLawHandler().getGroupList().get(x);
			i.setItem(SLOTS.get(x), lawCreator.createLawGroupItem(player, f, group));
		}
		i.setItem(53, inv.createBackButton(SFGUI.LAW_PROPOSAL_VIEW));
		if(open) player.openInventory(i);
	}

	public void lawProposalSelect(Player player, Faction f, LawGroup group, Inventory i) {
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.LAW_PROPOSAL_SELECT, group.getId()), 27, "§7Select Law");
		i.clear();
		int slot = 0;
		for(Law law : group.getLaws().values()) {
			i.setItem(slot, lawCreator.createLawItem(player, f, group, law, true));
			slot++;
		}
		i.setItem(26, inv.createBackButton(SFGUI.LAW_PROPOSAL_SELECT));
		if(open) player.openInventory(i);
	}

	public void favourRepressMainView(Player player, Faction f, Inventory i) {
		boolean open = i == null;
		if(i == null) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.FAVOUR_REPRESS_MAIN), 9, "§7Favour & Repress");
		i.clear();
		if(f.hasFactionRule(Rules.CAN_FAVOUR)) i.setItem(3, creator.createFavourButton());
		if(f.hasFactionRule(Rules.CAN_REPRESS)) i.setItem(5, creator.createRepressButton());
		i.setItem(8, inv.createBackButton(SFGUI.FAVOUR_REPRESS_MAIN));
		if(open) player.openInventory(i);
	}

	public void favourRepressTypeView(Player player, Faction f, boolean isFavourMode, Inventory i) {
		boolean open = i == null;
		String title = isFavourMode ? "§7Favour - Select Type" : "§7Repress - Select Type";
		if(i == null) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.FAVOUR_REPRESS_TYPE, 0, isFavourMode), 9, title);
		i.clear();
		i.setItem(3, creator.createGuildsTypeButton(isFavourMode));
		i.setItem(5, creator.createVassalsTypeButton(isFavourMode));
		i.setItem(8, inv.createBackButton(SFGUI.FAVOUR_REPRESS_TYPE));
		if(open) player.openInventory(i);
	}

	public void favourRepressSelectView(Player player, Faction f, boolean isFavourMode, boolean isGuilds, Inventory i) {
		boolean open = i == null;
		String title = isFavourMode ? "§7Favour - " : "§7Repress - ";
		title += isGuilds ? "Guilds" : "Vassals";
		if(i == null) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.FAVOUR_REPRESS_SELECT, isGuilds ? 1 : 0, isFavourMode), 54, title);
		i.clear();
		
		int slot = 0;
		if(isGuilds) {
			for(Guild guild : f.getGuildHandler().getGuilds()) {
				if(guild.isBase()) continue;
				if(slot >= 53) break;
				i.setItem(slot, creator.createFavourRepressGuildItem(player, f, guild, isFavourMode));
				slot++;
			}
		} else {
			for(Faction vassal : f.getSubjects()) {
				if(slot >= 53) break;
				i.setItem(slot, creator.createFavourRepressVassalItem(player, f, vassal, isFavourMode));
				slot++;
			}
		}
		
		i.setItem(53, inv.createBackButton(SFGUI.FAVOUR_REPRESS_SELECT));
		if(open) player.openInventory(i);
	}

	public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		SFInventoryHolder h = (SFInventoryHolder) inventory.getHolder();
		if (h.getType() == SFGUI.GOVERNMENT_VIEW) {
			e.setCancelled(true);
			int slot = e.getSlot();
			ItemStack item = e.getCurrentItem();
			Faction f = FactionManager.getByString(((SFInventoryHolder)e.getInventory().getHolder()).getId());
			if(slot == 28) {
				String id = item.getItemMeta().getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
				if(id != null) {
					Guild g = FactionManager.getGuildByString(id);
					if(g != null) {
						g.switchStance();
						governmentView(p, f, inventory);
						p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					}
				}
			} else if(slot == 15) {
				Government gov = f.getGovernment();
				if(!gov.canProposeOrStartMovement(p) || gov.getMovementByMember(p.getName()) != null) {
					p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				proposalView(p, f, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(slot == 21) {
				Government gov = f.getGovernment();
				gov.getCouncil().toggleRefuse(p.getName());
				governmentView(p, f, inventory);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(slot == 24) {
				proposalList(p, f, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(slot == 33) {
				inv.movementListView(p, f, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(slot == 23) {
				if(!f.getGovernment().hasCouncil()) return;
				if(!f.getGovernment().getCouncil().hasEnoughValidVoters()) {
					p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					p.sendMessage("§cNot enough council members online! At least 75% must be present.");
					return;
				}
				SimpleFactions.plugin.getSessionManager().newSession(p, f);
				p.closeInventory();
				p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			} else if(slot == 12) {
				Government gov = f.getGovernment();
				if(!gov.hasCouncil()) return;
				councilView(p, f, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(slot == 16) {
				favourRepressMainView(p, f, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			}
		} else if (h.getType() == SFGUI.PROPOSAL_VIEW) {
			e.setCancelled(true);
			int slot = e.getSlot();
			Faction f = FactionManager.getByString(((SFInventoryHolder)e.getInventory().getHolder()).getId());
			if(slot == 0) {
				lawProposalView(p, f, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(slot == 1) {
				taxProposalView(p, f, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(slot == 2) {
				politicalProposalView(p, f, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			}
		} else if (h.getType() == SFGUI.TAX_PROPOSAL_VIEW) {
			e.setCancelled(true);
			ItemStack item = e.getCurrentItem();
			ItemMeta meta = item.getItemMeta();
			if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
			SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
			Faction f = FactionManager.getByString(holder.getId());
			if(f == null) return;
			String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
			if(id == null) return;
			try {
				TaxTarget target = TaxTarget.valueOf(id);
				if(target == TaxTarget.GUILD_ID) {
					specificTaxProposalView(p, f, null, TaxTarget.GUILD_ID);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				} else if(target == TaxTarget.VASSAL_ID) {
					specificTaxProposalView(p, f, null, TaxTarget.VASSAL_ID);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				} else if(target == TaxTarget.TARIFF_ID) {
					specificTaxProposalView(p, f, null, TaxTarget.TARIFF_ID);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				}
				inv.setChanging(f, p, target, "all");
				p.sendTitle("§aTax Change", "§eType a new tax for "+target.getDisplayName()+" §ein chat.", 20, 40, 20);
				p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				p.closeInventory();
			} catch (Exception ex) {
				// TODO: handle exception
			}
		} else if (h.getType() == SFGUI.SPECIFIC_TAX_PROPOSAL_VIEW) {
			e.setCancelled(true);
			ItemStack item = e.getCurrentItem();
			ItemMeta meta = item.getItemMeta();
			if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
			SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
			Faction f = FactionManager.getByString(holder.getId());
			if(f == null) return;
			String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
			if(id == null) return;

			String t = meta.getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
			if(t == null) return;
			TaxTarget target = TaxTarget.valueOf(t);
			String name = target == TaxTarget.GUILD_ID ? FactionManager.getGuildByString(id).getName() : FactionManager.getByString(id).getName();
			inv.setChanging(f, p, target, id);
			p.sendTitle("§aTax Change", "§eType a new tax for "+name+" §ein chat.", 20, 40, 20);
			p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			p.closeInventory();
		} else if (h.getType() == SFGUI.LAW_PROPOSAL_VIEW) {
			e.setCancelled(true);
			ItemStack item = e.getCurrentItem();
			ItemMeta meta = item.getItemMeta();
			if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
			SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
			Faction f = FactionManager.getByString(holder.getId());
			if(f == null) return;
			String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
			if(id == null) return;
			LawGroup group = f.getLawHandler().getGroup(id);
			if(group == null) return;
			lawProposalSelect(p, f, group, null);
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		} else if (h.getType() == SFGUI.LAW_PROPOSAL_SELECT) {
			e.setCancelled(true);
			ItemStack item = e.getCurrentItem();
			ItemMeta meta = item.getItemMeta();
			if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
			SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
			Faction f = FactionManager.getByString(holder.getId());
			if(f == null) return;
			String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
			if(id == null) return;
			String group = meta.getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
			if(group == null) return;
			Law law = f.getLawHandler().getLaw(group, id);
			if(law == null) return;
			String unavailable = CanHaveLaw.blockReason(f, law);
			if (unavailable != null) {
				p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				p.sendMessage(unavailable);
				return;
			}
			Government gov = f.getGovernment();
			double cost = law.getCost()*law.getCompatibility(f.getLawHandler().getGroup(group).getCurrent().getId());
			Proposal proposal = new Proposal(p.getName(), gov);
			proposal.setLawProposal(law);
			if(!gov.hasCouncil() && f.isLeader(p.getName())) {
				if(gov.getPower() < cost) {
					p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					p.sendMessage("§cThe government does not have enough power to propose this! §7(Need §e"+cost+"§7, has §e"+gov.getPower()+"§7)");
					return;
				}
				p.sendMessage("§aChange applied!");
				gov.spendPower(cost);
				proposal.apply(null);
				governmentView(p, f, null);
				p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				return;
			}
			if(gov.canPropose(p)) {
				if(!gov.canBeProposed(proposal)) {
					p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				if(gov.getPower() < cost) {
					p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					p.sendMessage("§cThe government does not have enough power to propose this! §7(Need §e"+cost+"§7, has §e"+gov.getPower()+"§7)");
					return;
				}
				gov.propose(proposal);
				gov.spendPower(cost);
				p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				governmentView(p, f, null);
			} else if(gov.canProposeOrStartMovement(p)) {
				Movement movement = gov.getMovementByMember(p.getName());
				if(movement != null) {
					if(!gov.canBeProposed(proposal)) {
						p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
						return;
					}
					movement.createCause(p.getName(), proposal);
					p.sendMessage("§aProposal added to your movement! Rally support for your proposal by sharing it with your faction and allies!");
					p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
					governmentView(p, f, null);
					return;
				}
				if (CivilWarHostMovementRules.blocksHostGuildStart(f, p.getName())) {
					p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					p.sendMessage(CivilWarCopy.ONE_PROVINCE_HOST_GUILD);
					return;
				}
				gov.startMovement(p.getName(), proposal);
				p.sendMessage("§aMovement started! Rally support for your proposal by sharing it with your faction and allies!");
				p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				governmentView(p, f, null);
			}
		} else if (h.getType() == SFGUI.POLITICAL_PROPOSAL_VIEW) {
			e.setCancelled(true);
			ItemStack item = e.getCurrentItem();
			ItemMeta meta = item.getItemMeta();
			if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
			SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
			Faction f = FactionManager.getByString(holder.getId());
			if(f == null) return;
			String actionName = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
			if(actionName == null) return;
			
			try {
				Action action = Action.valueOf(actionName);
				Government gov = f.getGovernment();
				
				if(!gov.canProposePolitical(p, action)) {
					p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				if (CouncilPeaceQueries.isWarEndAction(action)) {
					warPeaceSelectView(p, f, action, false, 0, null);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				}
				
				Proposal proposal = new Proposal(p.getName(), gov);
				PoliticalAction politicalAction = PoliticalActionLoader.getByAction(action);
				proposal.setPoliticalActionProposal(politicalAction);
				
				if(gov.canProposeOrStartMovement(p)) {
					Movement movement = gov.getMovementByMember(p.getName());
					if(movement != null) {
						if(!gov.canBeProposed(proposal)) {
							p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
							return;
						}
						movement.createCause(p.getName(), proposal);
						p.sendMessage("§aProposal added to your movement! Rally support for your proposal by sharing it with your faction and allies!");
						p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
						governmentView(p, f, null);
						return;
					}
					if (CivilWarHostMovementRules.blocksHostGuildStart(f, p.getName())) {
						p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
						p.sendMessage(CivilWarCopy.ONE_PROVINCE_HOST_GUILD);
						return;
					}
					gov.startMovement(p.getName(), proposal);
					p.sendMessage("§aMovement started! Rally support for your proposal by sharing it with your faction and allies!");
					p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
					governmentView(p, f, null);
				} else {
					p.sendMessage("§cYou cannot start a movement for this action.");
					p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				}
			} catch (IllegalArgumentException ex) {
				p.sendMessage("§cInvalid action.");
				p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			}
		} else if (h.getType() == SFGUI.WAR_PEACE_SELECT) {
			e.setCancelled(true);
			ItemStack item = e.getCurrentItem();
			if (item == null) {
				return;
			}
			ItemMeta meta = item.getItemMeta();
			if (meta == null) {
				return;
			}
			String warId = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
			if (warId == null) {
				return;
			}
			Faction faction = FactionManager.getByString(h.getId());
			if (faction == null) {
				return;
			}
			Action action;
			try {
				action = Action.valueOf(h.getSecondaryId());
			} catch (RuntimeException ex) {
				return;
			}
			submitWarEnd(p, faction, action, warId, h.getFlag(), h.getPage());
		} else if (h.getType() == SFGUI.PROPOSALS) {
			e.setCancelled(true);
		} else if (h.getType() == SFGUI.COUNCIL_VIEW) {
			e.setCancelled(true);
			int slot = e.getSlot();
			ItemStack item = e.getCurrentItem();
			if(item == null) return;
			Faction f = FactionManager.getByString(((SFInventoryHolder)e.getInventory().getHolder()).getId());
			if(f == null) return;
			boolean isLeader = p.getName().equalsIgnoreCase(f.getLeader());
			if(!isLeader) return;
			// Handle council member click for replace/appoint
			ItemMeta meta = item.getItemMeta();
			if(meta == null) return;
			Integer s = meta.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER);
			if(s == null) return;
			
			Council council = f.getGovernment().getCouncil();
			List<String> members = council.getMembers();
			boolean isOccupied = s < members.size() && members.get(s) != null && !members.get(s).isEmpty();
			boolean isNextEmpty = s == members.size();
			
			if(!isOccupied && !isNextEmpty) {
				p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				p.sendMessage("§cYou must fill council seats in order!");
				return;
			}
			
			councilSelect(p, f, null, s);
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		} else if (h.getType() == SFGUI.COUNCIL_SELECT) {
			e.setCancelled(true);
			ItemStack item = e.getCurrentItem();
			if(item == null) return;
			Faction f = FactionManager.getByString(((SFInventoryHolder)e.getInventory().getHolder()).getId());
			if(f == null) return;
			boolean isLeader = p.getName().equalsIgnoreCase(f.getLeader());
			if(!isLeader) return;
			// Handle council member click for replace/appoint
			ItemMeta meta = item.getItemMeta();
			if(meta == null) return;
			String member = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
			if(member == null) return;
			Integer s = meta.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER);
			if(s == null) return;
			Council council = f.getGovernment().getCouncil();
			
			// Verify slot can be appointed/modified
			List<String> members = council.getMembers();
			boolean isOccupied = s < members.size() && members.get(s) != null && !members.get(s).isEmpty();
			boolean isNextEmpty = s == members.size();
			
			if(!isOccupied && !isNextEmpty) {
				p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				p.sendMessage("§cYou must fill council seats in order!");
				return;
			}
			
			if(isOccupied) {
				council.replaceMember(s, member);
			} else {
				council.addMember(member);
			}
			councilView(p, f, null);
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		} else if (h.getType() == SFGUI.FAVOUR_REPRESS_MAIN) {
			e.setCancelled(true);
			int slot = e.getSlot();
			Faction f = FactionManager.getByString(h.getId());
			if(f == null) return;
			
			if(slot == 3) { // Favour button
				favourRepressTypeView(p, f, true, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(slot == 5) { // Repress button
				favourRepressTypeView(p, f, false, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			}
		} else if (h.getType() == SFGUI.FAVOUR_REPRESS_TYPE) {
			e.setCancelled(true);
			int slot = e.getSlot();
			Faction f = FactionManager.getByString(h.getId());
			if(f == null) return;
			boolean isFavourMode = h.getFlag();
			
			if(slot == 3) { // Guilds button
				favourRepressSelectView(p, f, isFavourMode, true, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(slot == 5) { // Vassals button
				favourRepressSelectView(p, f, isFavourMode, false, null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			}
		} else if (h.getType() == SFGUI.FAVOUR_REPRESS_SELECT) {
			e.setCancelled(true);
			ItemStack item = e.getCurrentItem();
			if(item == null) return;
			ItemMeta meta = item.getItemMeta();
			if(meta == null) return;
			
			Faction f = FactionManager.getByString(h.getId());
			if(f == null) return;

			if(!f.isLeader(p.getName())) return;
			
			String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
			if(id == null) return;
			
			boolean isFavourMode = h.getFlag();
			boolean isGuilds = h.getPage() == 1;
			Government gov = f.getGovernment();
			
			if(isGuilds) {
				Guild guild = FactionManager.getGuildByString(id);
				if(guild == null) return;
				
				if(isFavourMode) {
					if(gov.canFavour(guild)) {
						gov.toggleFavour(guild);
						favourRepressSelectView(p, f, isFavourMode, isGuilds, inventory);
						p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					} else {
						p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					}
				} else {
					if(gov.canRepress(guild)) {
						gov.toggleRepress(guild);
						favourRepressSelectView(p, f, isFavourMode, isGuilds, inventory);
						p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					} else {
						p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					}
				}
			} else {
				Faction vassal = FactionManager.getByString(id);
				if(vassal == null) return;
				Guild mainGuild = vassal.getOrCreateMainGuild();
				
				if(isFavourMode) {
					if(gov.canFavour(mainGuild)) {
						gov.toggleFavour(mainGuild);
						favourRepressSelectView(p, f, isFavourMode, isGuilds, inventory);
						p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					} else {
						p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					}
				} else {
					if(gov.canRepress(mainGuild)) {
						gov.toggleRepress(mainGuild);
						favourRepressSelectView(p, f, isFavourMode, isGuilds, inventory);
						p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					} else {
						p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					}
				}
			}
		}
	}

	private void submitWarEnd(Player p, Faction f, Action action, String warId, boolean fromCause, int causeIndex) {
		Government gov = f.getGovernment();
		if (!gov.canProposePolitical(p, action) && !fromCause) {
			p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}
		if (!CouncilPeaceQueries.isValidTarget(f, warId)) {
			p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			p.sendMessage("§cThat war is no longer valid.");
			return;
		}
		if (fromCause) {
			Movement movement = gov.getMovementByMember(p.getName());
			if (movement == null || causeIndex < 0 || causeIndex >= movement.getCauses().size()) {
				p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			me.Plugins.SimpleFactions.government.movement.cause.Cause cause = movement.getCauses().get(causeIndex);
			if (!cause.hasLeader() || !cause.getLeader().equals(p.getName())) {
				p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			cause.getProposal().setTarget(warId);
			inv.movementView.causeView(p, f, movement, cause, null);
			p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			return;
		}
		Proposal proposal = new Proposal(p.getName(), gov);
		PoliticalAction politicalAction = PoliticalActionLoader.getByAction(action);
		proposal.setPoliticalActionProposal(politicalAction);
		proposal.setTarget(warId);
		if (!gov.hasCouncil() && f.isLeader(p.getName())) {
			proposal.apply(null);
			p.sendMessage("§aChange applied!");
			governmentView(p, f, null);
			p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			return;
		}
		if (gov.canPropose(p)) {
			if (!gov.canBeProposed(proposal)) {
				p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			gov.propose(proposal);
			governmentView(p, f, null);
			p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			return;
		}
		if (gov.canProposeOrStartMovement(p)) {
			Movement movement = gov.getMovementByMember(p.getName());
			if (movement != null) {
				if (!gov.canBeProposed(proposal)) {
					p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				movement.createCause(p.getName(), proposal);
				p.sendMessage("§aProposal added to your movement! Rally support for your proposal by sharing it with your faction and allies!");
				p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				governmentView(p, f, null);
				return;
			}
			if (CivilWarHostMovementRules.blocksHostGuildStart(f, p.getName())) {
				p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				p.sendMessage(CivilWarCopy.ONE_PROVINCE_HOST_GUILD);
				return;
			}
			gov.startMovement(p.getName(), proposal);
			p.sendMessage("§aMovement started! Rally support for your proposal by sharing it with your faction and allies!");
			p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			governmentView(p, f, null);
			return;
		}
		p.sendMessage("§cYou cannot start a movement for this action.");
		p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
	}
}
