package me.Plugins.SimpleFactions.War.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;

public class Participant {
	private Faction leader;
	private HashMap<Faction, Boolean> allies = new HashMap<>();
	private List<Faction> subjects = new ArrayList<>();
	private List<Faction> backers = new ArrayList<>();
	
	private HashMap<Faction, WarGoal> warGoals = new HashMap<>();

	private boolean civilWar;
	
	public Participant(Faction leader) {
		this.leader = leader;
		for(Faction s : RelationManager.getSubjects(leader)) {
			subjects.add(s);
		}
		for(Faction a : RelationManager.getAllies(leader)) {
			allies.put(a, false);
		}
		civilWar = false;
	}

	public Participant(Faction leader, boolean civilWar) {
		this.leader = leader;
		for(Faction s : RelationManager.getSubjects(leader)) {
			subjects.add(s);
		}
		for(Faction a : RelationManager.getAllies(leader)) {
			allies.put(a, false);
		}
		this.civilWar = civilWar;
	}

	public Participant(Faction leader, List<Faction> subjects, Map<Faction, Boolean> allies, Map<Faction, WarGoal> warGoals, boolean civilWar) {
		this(leader, subjects, allies, List.of(), warGoals, civilWar);
	}

	public Participant(
			Faction leader,
			List<Faction> subjects,
			Map<Faction, Boolean> allies,
			List<Faction> backers,
			Map<Faction, WarGoal> warGoals,
			boolean civilWar) {
		this.leader = leader;
		this.subjects = subjects == null ? new ArrayList<>() : new ArrayList<>(subjects);
		this.allies = allies == null ? new HashMap<>() : new HashMap<>(allies);
		this.backers = backers == null ? new ArrayList<>() : new ArrayList<>(backers);
		this.warGoals = warGoals == null ? new HashMap<>() : new HashMap<>(warGoals);
		this.civilWar = civilWar;
	}

	public void update(War w) {
		Iterator<Faction> iterator = subjects.iterator();
		while(iterator.hasNext()) {
			Faction subject = iterator.next();
			String overlord = RelationManager.getOverlord(subject);
			if(w.isMainParticipant(subject) || overlord == null || !overlord.equalsIgnoreCase(leader.getId())) {
				iterator.remove();
			}
		}
		for (Faction subject : RelationManager.getSubjects(leader)) {
			if (subjects.contains(subject) || w.isMainParticipant(subject)) continue;
			subjects.add(subject);
		}
		Iterator<Map.Entry<Faction, Boolean>> allyIterator = allies.entrySet().iterator();
		while (allyIterator.hasNext()) {
			Map.Entry<Faction, Boolean> entry = allyIterator.next();
			if(!entry.getKey().getRelation(leader.getId()).getType().getId().equalsIgnoreCase("ally")) {
				allyIterator.remove(); // Safe: modifies original map
			}
		}
		for(Map.Entry<String, Relation> entry : leader.getRelations().entrySet()){
			Faction ally = FactionManager.getByString(entry.getKey());
			if(!entry.getValue().getType().getId().equalsIgnoreCase("ally")) continue;
			if(ally == null) continue;
			if(allies.containsKey(ally)) continue;
			allies.put(ally, false);
		}
	}


	public boolean isCivilWar(){
		return civilWar;
	}

	public void setCivilWar(boolean b){
		civilWar = b;
	}

	public Faction getLeader() {
		return leader;
	}

	public HashMap<Faction, Boolean> getAllies() {
		return allies;
	}

	public List<Faction> getSubjects() {
		return subjects;
	}
	
	public List<Faction> getBackers() {
		return backers;
	}

	public boolean isJoinedSecondary(Faction faction) {
		if (faction == null || faction.getId() == null) {
			return false;
		}
		String id = faction.getId();
		Boolean joinedAlly = allies.get(faction);
		if (Boolean.TRUE.equals(joinedAlly)) {
			return true;
		}
		for (Map.Entry<Faction, Boolean> entry : allies.entrySet()) {
			if (entry.getKey() != null
					&& entry.getKey().getId() != null
					&& entry.getKey().getId().equalsIgnoreCase(id)
					&& Boolean.TRUE.equals(entry.getValue())) {
				return true;
			}
		}
		return containsBacker(id);
	}

	public List<Faction> getJoinedSecondaries() {
		List<Faction> joined = new ArrayList<>();
		for (Map.Entry<Faction, Boolean> entry : allies.entrySet()) {
			if (Boolean.TRUE.equals(entry.getValue()) && entry.getKey() != null) {
				joined.add(entry.getKey());
			}
		}
		for (Faction backer : backers) {
			if (backer == null || backer.getId() == null) {
				continue;
			}
			boolean already = false;
			for (Faction existing : joined) {
				if (existing.getId() != null && existing.getId().equalsIgnoreCase(backer.getId())) {
					already = true;
					break;
				}
			}
			if (!already) {
				joined.add(backer);
			}
		}
		return joined;
	}

	public boolean addBacker(Faction backer) {
		if (backer == null || backer.getId() == null) {
			return false;
		}
		if (leader != null && leader.getId() != null && leader.getId().equalsIgnoreCase(backer.getId())) {
			return false;
		}
		if (containsBacker(backer.getId())) {
			return false;
		}
		backers.add(backer);
		return true;
	}

	private boolean containsBacker(String factionId) {
		if (factionId == null) {
			return false;
		}
		for (Faction backer : backers) {
			if (backer != null && backer.getId() != null && backer.getId().equalsIgnoreCase(factionId)) {
				return true;
			}
		}
		return false;
	}

	public void clean(Faction f) {
		if(subjects.contains(f)) subjects.remove(f);
		if(allies.containsKey(f)) allies.remove(f);
		backers.remove(f);
		if (f != null && f.getId() != null) {
			backers.removeIf(backer -> backer != null && backer.getId() != null && backer.getId().equalsIgnoreCase(f.getId()));
		}
	}
	
	public boolean hasWarGoal(Faction f) {
		return warGoals.containsKey(f);
	}
	
	public WarGoal getWarGoal(Faction f) {
		if(hasWarGoal(f)) return warGoals.get(f);
		return null;
	}

	/** @deprecated Per-participant goals replaced by single war-level goal in v2. Read-only for v1 migration. */
	@Deprecated
	public HashMap<Faction, WarGoal> getWarGoals() {
		return warGoals;
	}

	/** @deprecated Use war-level {@link me.Plugins.SimpleFactions.War.enums.WarGoalType} instead. */
	@Deprecated
	public void addWarGoal(Faction f, WarGoal goal) {
		warGoals.put(f, goal);
	}

	public List<Faction> getAllParticipatingFactions(){
		List<Faction> list = new ArrayList<>();
		list.add(leader);
		list.addAll(subjects);
		list.addAll(getJoinedSecondaries());
		return list;
	}
}
