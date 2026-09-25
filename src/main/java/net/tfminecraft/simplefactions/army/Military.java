package net.tfminecraft.simplefactions.army;

import java.util.ArrayList;
import java.util.List;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.SFGUI;

public class Military {
	Faction f;
	Formatter format = new Formatter();
	private List<Regiment> regiments = new ArrayList<>();
	private List<MilitaryExpansion> queue = new ArrayList<>();
	
	public Military(Faction f) {
		this.f = f;
		for(Regiment r : RegimentLoader.getRegiments()) {
			if(r.isMercenary()) continue;
			regiments.add(new Regiment(r));
		}
	}
	
	public int getTotalSlots() {
		int i = 0;
		for(Regiment r : regiments) {
			i += r.getCurrentSlots();
		}
		return i;
	}
	
	public double getTotalUpkeep() {
		double d = getRawTotalUpkeep();
		if(f.getModifier(FactionModifiers.MILITARY_UPKEEP) != null){
			double mod = 1.0 + f.getModifier(FactionModifiers.MILITARY_UPKEEP).getAmount()/100.0;
			d*=mod;
		}
		return format.formatDouble(d);
	}

	public double getRawTotalUpkeep() {
		double d = 0;
		for(Regiment r : regiments) {
			d += r.getTotalUpkeep();
		}
		return format.formatDouble(d);
	}
	
	public void updateLevies() {
		getRegiment("levy");
	}
	
	public Regiment getRegiment(String s) {
		for(Regiment r : regiments) {
			if(r.getId().equalsIgnoreCase(s)) {
				if(s.equalsIgnoreCase("levy")) {
					r.setLevyEntries(getLevies());
				}
				return r;
			}
		}
		return null;
	}
	
	public List<Regiment> getRegiments(){
		updateLevies();
		return regiments;
	}
	
	public List<MilitaryExpansion> getQueue(){
		return queue;
	}

	public ExpandResult canExpand(Regiment r) {
		if (r == null) {
			return ExpandResult.deny("Unknown regiment.");
		}
		if (!r.isProfessional()) {
			return ExpandResult.ok();
		}
		if (f != null && f.hasFactionRule(Rules.CAN_RECRUIT_PROFESSIONAL_ARMY)) {
			return ExpandResult.ok();
		}
		return ExpandResult.deny("Your laws do not allow recruiting a professional army.");
	}
	
	public boolean enqueue(Regiment r) {
		if(queue.size() == 3) return false;
		if (!canExpand(r).allowed()) {
			return false;
		}
		queue.add(new MilitaryExpansion(r));
		return true;
	}

	public void addQueueItem(Regiment r, int time){
		if(queue.size() == 3) return;
		queue.add(new MilitaryExpansion(r, time));
	}

	public boolean cancelQueue(int index) {
		if (index < 0 || index >= queue.size()) {
			return false;
		}
		queue.remove(index);
		return true;
	}

	public ExpandResult adminAdjustSlots(String regimentId, int delta) {
		if (delta == 0) {
			return ExpandResult.deny("Amount must be non-zero.");
		}
		Regiment regiment = getRegiment(regimentId);
		if (regiment == null) {
			return ExpandResult.deny("Unknown regiment.");
		}
		if (regiment.isLevy()) {
			return ExpandResult.deny("Levies cannot be adjusted by admins.");
		}
		if (regiment.isMercenary()) {
			return ExpandResult.deny("Mercenary regiments belong to companies, not factions.");
		}
		if (delta > 0) {
			for (int i = 0; i < delta; i++) {
				regiment.sizeIncrease();
			}
			return ExpandResult.ok();
		}
		int remove = -delta;
		if (regiment.getCurrentSlots() < remove) {
			return ExpandResult.deny("Not enough slots to remove.");
		}
		for (int i = 0; i < remove; i++) {
			regiment.sizeDecrease();
		}
		return ExpandResult.ok();
	}
	
	/**
	 * The main guild's company, when it has finished founding. Only this company
	 * fights for the faction; companies of other guilds are hired by contract.
	 */
	public MercenaryCompany getHomeCompany() {
		if(f == null) return null;
		GuildHandler handler = f.getGuildHandler();
		if(handler == null) return null;
		Guild main = handler.getGuild(f.getId());
		if(main == null) return null;
		MercenaryCompany company = main.getCompany();
		return company != null && company.isFormed() ? company : null;
	}

	/** Filled slots of the home company. Empty slots add nothing. */
	public int getMercenaryManpower() {
		MercenaryCompany company = getHomeCompany();
		return company == null ? 0 : company.getFilledSlots();
	}

	public int getManpower(boolean offense) {
		int manpower = getMercenaryManpower();
		for(Regiment r : getRegiments()) {
			if(offense && !r.isOffensive()) continue;
			if(r.isLevy()) {
				for(LevyEntry e : r.getEntries()) {
					manpower += e.getAmount();
				}
			} else {
				manpower += r.getCurrentSlots();
			}
		}
		return manpower;
	}
	
	public int getManpowerNoLevy(boolean offense) {
		int manpower = getMercenaryManpower();
		for(Regiment r : regiments) {
			if(offense && !r.isOffensive()) continue;
			if(r.isLevy()) continue;
			manpower += r.getCurrentSlots();
		}
		return manpower;
	}
	
	public List<LevyEntry> getLevies() {
		List<LevyEntry> levies = new ArrayList<>();
		for(Faction subject : RelationManager.getSubjects(f)) {
			List<LevyEntry> subjectLevies = new ArrayList<>();
			if(RelationManager.getSubjects(subject).size() > 0) {
				subjectLevies = subject.getMilitary().getLevies();
			}
			int total = 0;
			for(Regiment r : subject.getMilitary().getRegiments()) {
				int count = r.getCurrentSlots();
				count = (int) Math.round(count * (subject.getModifier(FactionModifiers.LEVY).getAmount()/100));
				if(total+count >= subject.getMembers().size()) {
					count = subject.getMembers().size() - total;
				}
				total+=count;
				if(count > 0) r.setSentToOverlord(count);
				else continue;
			}
			levies.add(new LevyEntry(subject, total));
			for(LevyEntry e : subjectLevies) {
				int count = e.getAmount();
				count = (int) Math.round(count * (subject.getModifier(FactionModifiers.LEVY).getAmount()/100));
				if(count > 0) levies.add(new LevyEntry(e.getFrom(), count));
			}
		}
		return levies;
	}
	
	public void tick() {
		if(queue.size() == 0) return;
		MilitaryExpansion e = queue.get(0);
		e.tick();
		if(e.getTimeLeft() != 0) return;
		queue.remove(0);
		e.getRegiment().sizeIncrease();
		FactionManager.getInv().getUpdater().inventorySound("minecraft:block.note_block.chime", SFGUI.MILITARY_VIEW);
	}
}
