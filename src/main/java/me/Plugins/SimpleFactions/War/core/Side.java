package me.Plugins.SimpleFactions.War.core;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Objects.Faction;

public class Side {
	private Faction leader;
	private List<Participant> participants = new ArrayList<>();
	
	public Side(Faction main) {
		leader = main;
		participants.add(new Participant(main));
	}
	
	public Participant addNewParticipant(Faction f, Participant old) {
		old.clean(f);
		Participant par = new Participant(f);
		participants.add(par);
		return par;
	}
	
	public Faction getLeader() {
		return leader;
	}
	
	public List<Participant> getMainParticipants(){
		return participants;
	}
	
	public boolean isParticipating(Faction f) {
		if(leader.getId().equalsIgnoreCase(f.getId())) return true;
		for(Participant p : participants) {
			if(p.getLeader().getId().equalsIgnoreCase(f.getId())) return true;
			if(p.getSubjects().contains(f)) return true;
			if(p.isJoinedSecondary(f)) return true;
		}
		return false;
	}
	
	public int getTotalManpower(boolean offense) {
		int count = 0;
		for(Participant p : participants) {
			count += p.getLeader().getMilitary().getManpower(offense);
		}
		return count;
	}
}
