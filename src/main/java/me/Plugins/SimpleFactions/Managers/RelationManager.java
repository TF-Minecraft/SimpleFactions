package me.Plugins.SimpleFactions.Managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import me.Plugins.SimpleFactions.Diplomacy.Attitude;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Diplomacy.Threshold;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Request.RelationRequest;
import me.Plugins.SimpleFactions.Utils.OpinionColourMapper;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryLoyaltyWatcher;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class RelationManager {
	
	private static int tick = 0;
	
	public static void tick() {
		tick++;
		if(tick >= 3600) {
			tick = 0;
			for(Faction f : FactionManager.factions) {
				f.updateRelations();
			}
		}
	}
	
	public static void reset(Faction origin, Faction target, boolean hostile) {
		Relation relation = new Relation(origin.getRelation(target.getId()));
		Relation reverse = new Relation(target.getRelation(origin.getId()));
		relation.setType(RelationLoader.getDefaultType());
		reverse.setType(RelationLoader.getDefaultType());
		if(hostile) {
			relation.setAttitude(RelationLoader.getAttitude("hostile"));
			reverse.setAttitude(RelationLoader.getAttitude("hostile"));
		} else {
			relation.setAttitude(RelationLoader.getDefaultAttitude());
			reverse.setAttitude(RelationLoader.getDefaultAttitude());
		}
		origin.setRelation(target, relation);
		target.setRelation(origin, reverse);
	}

	public static boolean endVassalage(Faction origin, Faction target, boolean hostile) {
		if(isOverlord(origin, target) || isOverlord(target, origin)) {
			reset(origin, target, hostile);
			me.Plugins.SimpleFactions.War.commitment.WarCommitmentService.onVassalageEnded(origin, target);
			MercenaryLoyaltyWatcher.onRelationChanged(origin, target);
			return true;
		}
		return false;
	}

	public static boolean isOverlord(Faction origin, Faction target) {
		String overlord = getOverlord(origin);
		if(overlord == null) return false;
		return overlord.equalsIgnoreCase(target.getId());
	}

	public static double getDiplomaticCost(Faction from, Faction to, RelationType r) {
		if (r == null || from == null || to == null) {
			return 0;
		}
		double cost = r.getBaseCost();
		if(!r.isSettable()) cost = 0;
		cost*=prestigeOf(to)/10.0;
		if(r.isVassalage()) {
			cost/=3.0;
		}
		return cost;
	}

	public static double getDiplomaticCost(Faction from, Faction to, Attitude a) {
		if (a == null || from == null || to == null) {
			return 0;
		}
		double cost = a.getBaseCost();
		if (cost == 0) {
			return 0;
		}
		return cost * prestigeOf(to) / 10.0;
	}

	private static double prestigeOf(Faction faction) {
		Double prestige = faction.getPrestige();
		return prestige == null ? 0 : prestige;
	}

	public static boolean sameRealm(Faction a, Faction b) {
		if (a == null || b == null) return false;
		if (a.getId().equalsIgnoreCase(b.getId())) return true;

		// Is A under B?
		if (isOnOverlordPath(a, b)) return true;

		// Is B under A?
		if (isOnOverlordPath(b, a)) return true;

		return false;
	}

	public static boolean atLimit(Faction f, RelationType r) {
		if(!r.hasLimit()) return false;
		int count = 0;
		for(Map.Entry<String, Relation> entry : f.getRelations().entrySet()) {
			if(entry.getValue().getType().getId().equalsIgnoreCase(r.getId())) count++;
		}
		return count >= r.getLimit();
	}

	public static int getRelationCount(Faction f, RelationType r) {
		int count = 0;
		for(Map.Entry<String, Relation> entry : f.getRelations().entrySet()) {
			if(entry.getValue().getType().getId().equalsIgnoreCase(r.getId())) count++;
		}
		return count;
	}
	
	public static void setRelation(Player p, RelationType r, Faction target, Faction origin, boolean check) {
		setRelation(p, r, target, origin, check, false);
	}

	public static boolean setRelationForced(RelationType r, Faction target, Faction origin) {
		return setRelation(null, r, target, origin, false, true);
	}

	public static boolean setRelation(
			Player p,
			RelationType r,
			Faction target,
			Faction origin,
			boolean check,
			boolean forced) {
		if (r == null || target == null || origin == null) {
			return false;
		}
		Relation relation = new Relation(origin.getRelation(target.getId()));
		Relation reverse = new Relation(target.getRelation(origin.getId()));
		boolean reverseChange = reverseChange(target, origin, r);
		if(r.isVassalage()) {
			// War levy rows are snapshotted at declare / ally join only (61.01b); no mid-war add here.
			if(!origin.canHaveVassals()) {
				if(p != null) p.sendMessage("§cYour faction cannot have vassals!");
				return false;
			}
			if(!vassalCheck(target, origin)) {
				if(p != null) p.sendMessage("§cThis faction is alredy a subject of someone else");
				return false;
			}
			String topLiege = getTopLiege(origin);
			if(topLiege != null && topLiege.equalsIgnoreCase(target.getId())) {
				if(p != null) p.sendMessage("§cThis faction is your top overlord");
				return false;
			}
			if(isOnOverlordPath(origin, target)){
				if(p != null) p.sendMessage("§cThis relation would cause a loop");
				return false;
			}
		}
		if(atLimit(origin, r)) {
			if(p != null) p.sendMessage("§cYou have reached the limit for this relation type");
			return false;
		}
		if(!forced && r.hasThreshold()) {
			Threshold h = r.getThreshold();
			int opinion = origin.getRelation(target.getId()).getOpinion();
			boolean fulfilled = true;
			String plus = "";
			if(h.getOpinion() > 0) plus = "+";
			if(!h.fulfilled(opinion)) {
				if(p != null) p.sendMessage(StringFormatter.formatHex("§cYou need an opinion "+h.getFormattedType()+" "+OpinionColourMapper.getOpinionColor(h.getOpinion())+plus+h.getOpinion()+ "§c of them §7(currently "+opinion+")"));
				fulfilled = false;
			}
			if(h.isMutual()) {
				int reverseOpinion = target.getRelation(origin.getId()).getOpinion();
				if(!h.fulfilled(reverseOpinion)) {
					if(p != null) p.sendMessage(StringFormatter.formatHex("§cThey need an opinion "+h.getFormattedType()+" "+OpinionColourMapper.getOpinionColor(h.getOpinion())+plus+h.getOpinion()+ "§c of us §7(currently "+reverseOpinion+")"));
					fulfilled = false;
				}
			}
			if(!fulfilled) {
				return false;
			}
		}
		if(!forced && r.isMutual() && check) {
			sendRequest(p, target, r);
			return false;
		}
		if(r.shouldUpdateMap() || relation.getType().shouldUpdateMap()) {
			if (FactionManager.getMap() != null) {
				FactionManager.getMap().enqueue("nation", origin.getRGB());
				FactionManager.getMap().enqueue("nation", target.getRGB());
			}
		}
		relation.setType(r);
		origin.setRelation(target, relation);
		if(reverseChange) {
			Player l = Bukkit.getPlayerExact(target.getLeader());
			if(l != null && l.isOnline()) {
				l.sendMessage(StringFormatter.formatHex("#a89977Your relation with "+origin.getName()+" #a89977has been changed to "+r.getLink().getName()));
			}
			reverse.setType(r.getLink());
			target.setRelation(origin, reverse);
		} else if(r.willReset()) {
			reverse.setType(r.getLink());
			target.setRelation(origin, reverse);
		}
		if(p != null) p.sendMessage(StringFormatter.formatHex("#a89977Set relation to "+r.getName()));
		//An alliance or vassalage can make a signed mercenary contract treachery
		MercenaryLoyaltyWatcher.onRelationChanged(origin, target);
		return true;
	}

	public static void setTradeRelation(Player p, RelationType r, Faction target, Faction origin, boolean check) {
		if(r.hasThreshold()) {
			Threshold h = r.getThreshold();
			int opinion = origin.getRelation(target.getId()).getOpinion();
			boolean fulfilled = true;
			String plus = "";
			if(h.getOpinion() > 0) plus = "+";
			if(!h.fulfilled(opinion)) {
				if(p != null) p.sendMessage(StringFormatter.formatHex("§cYou need an opinion "+h.getFormattedType()+" "+OpinionColourMapper.getOpinionColor(h.getOpinion())+plus+h.getOpinion()+ "§c of them §7(currently "+opinion+")"));
				fulfilled = false;
			}
			if(h.isMutual()) {
				int reverseOpinion = target.getRelation(origin.getId()).getOpinion();
				if(!h.fulfilled(reverseOpinion)) {
					if(p != null) p.sendMessage(StringFormatter.formatHex("§cThey need an opinion "+h.getFormattedType()+" "+OpinionColourMapper.getOpinionColor(h.getOpinion())+plus+h.getOpinion()+ "§c of us §7(currently "+reverseOpinion+")"));
					fulfilled = false;
				}
			}
			if(!fulfilled) {
				return;
			}
		}
		if(r.isMutual() && check) {
			sendTradeRequest(p, target, r);
			return;
		}
		origin.getDiplomacyHandler().setTradeRelation(target, r);
		if(r.isMutual()) {
			target.getDiplomacyHandler().setTradeRelation(origin, r.getLink());
		}
		if(p != null) p.sendMessage(StringFormatter.formatHex("#a89977Set trade to "+r.getName()));
	}

	public static void setTreatyRelation(Player p, RelationType r, Faction target, Faction origin, boolean check) {
		if(r.hasThreshold()) {
			Threshold h = r.getThreshold();
			int opinion = origin.getRelation(target.getId()).getOpinion();
			boolean fulfilled = true;
			String plus = "";
			if(h.getOpinion() > 0) plus = "+";
			if(!h.fulfilled(opinion)) {
				if(p != null) p.sendMessage(StringFormatter.formatHex("§cYou need an opinion "+h.getFormattedType()+" "+OpinionColourMapper.getOpinionColor(h.getOpinion())+plus+h.getOpinion()+ "§c of them §7(currently "+opinion+")"));
				fulfilled = false;
			}
			if(h.isMutual()) {
				int reverseOpinion = target.getRelation(origin.getId()).getOpinion();
				if(!h.fulfilled(reverseOpinion)) {
					if(p != null) p.sendMessage(StringFormatter.formatHex("§cThey need an opinion "+h.getFormattedType()+" "+OpinionColourMapper.getOpinionColor(h.getOpinion())+plus+h.getOpinion()+ "§c of us §7(currently "+reverseOpinion+")"));
					fulfilled = false;
				}
			}
			if(!fulfilled) {
				return;
			}
		}
		if(r.isClearTreaty()) {
			origin.getDiplomacyHandler().removeTreatyRelation(target.getId());
			target.getDiplomacyHandler().removeTreatyRelation(origin.getId());
			if(p != null) p.sendMessage(StringFormatter.formatHex("#a89977Cleared treaty"));
			return;
		}
		if(r.isMutual() && check) {
			sendTreatyRequest(p, target, r);
			return;
		}
		origin.getDiplomacyHandler().setTreatyRelation(target, r);
		if(r.isMutual()) {
			target.getDiplomacyHandler().setTreatyRelation(origin, r.getLink());
		}
		if(p != null) p.sendMessage(StringFormatter.formatHex("#a89977Set treaty to "+r.getName()));
	}

	public static void setTreatyRelationForced(RelationType r, Faction target, Faction origin) {
		if (r == null || target == null || origin == null) {
			return;
		}
		if (r.isClearTreaty()) {
			origin.getDiplomacyHandler().removeTreatyRelation(target.getId());
			target.getDiplomacyHandler().removeTreatyRelation(origin.getId());
			return;
		}
		origin.getDiplomacyHandler().setTreatyRelation(target, r);
		if (r.isMutual() && r.getLink() != null) {
			target.getDiplomacyHandler().setTreatyRelation(origin, r.getLink());
		}
	}

	public static void setTradeRelationForced(RelationType r, Faction target, Faction origin) {
		if (r == null || target == null || origin == null) {
			return;
		}
		origin.getDiplomacyHandler().setTradeRelation(target, r);
		if (r.isMutual() && r.getLink() != null) {
			target.getDiplomacyHandler().setTradeRelation(origin, r.getLink());
		}
	}
	
	public static boolean reverseChange(Faction target, Faction origin, RelationType t) {
		RelationType linked = t.getLink() != null ? t.getLink() : RelationLoader.getDefaultType();
		RelationType outgoing = origin.getRelation(target.getId()).getType();
		RelationType incoming = target.getRelation(origin.getId()).getType();
		return (outgoing.willReset() || incoming.willReset()) && !incoming.getId().equalsIgnoreCase(linked.getId());
	}
	
	public static String getOverlord(Faction f) {
		for(Map.Entry<String, Relation> entry : f.getRelations().entrySet()) {
			if(entry.getValue().getType().isOverlord()) return entry.getKey();
		}
		return null;
	}
	
	public static List<Faction> getAllies(Faction f){
		List<Faction> allies = new ArrayList<>();
		for(Map.Entry<String, Relation> entry : f.getRelations().entrySet()) {
			if(entry.getValue().getType().getId().equalsIgnoreCase("ally")) allies.add(FactionManager.getByString(entry.getKey()));
		}
		return allies;
	}

	public static boolean hasTradeEmbargo(Faction origin, Faction target) {
		if (origin == null || target == null || origin.getDiplomacyHandler() == null) {
			return false;
		}
		RelationType trade = origin.getDiplomacyHandler().getTradeRelation(target.getId());
		return trade != null && trade.blocksShops();
	}

	public static boolean hasNonAggressionPact(Faction a, Faction b) {
		if (a == null || b == null) {
			return false;
		}
		return treatyBlocksWar(a, b) || treatyBlocksWar(b, a);
	}

	private static boolean treatyBlocksWar(Faction from, Faction to) {
		if (from.getDiplomacyHandler() == null) {
			return false;
		}
		RelationType treaty = from.getDiplomacyHandler().getTreatyRelation(to.getId());
		return treaty != null && treaty.blocksWar();
	}

	public static boolean isTributaryOf(Faction suzerain, Faction tributary) {
		if (suzerain == null || tributary == null) {
			return false;
		}
		Relation relation = suzerain.getRelation(tributary.getId());
		if (relation == null || relation.getType() == null) {
			return false;
		}
		return relation.getType().getId().equalsIgnoreCase("tributary");
	}
	
	@SuppressWarnings("unchecked")
	public static List<Faction> getSubjects(Faction f){
		List<Faction> subjects = new ArrayList<>();
		if(f == null) return subjects;
		for(Map.Entry<String, Relation> entry : ((Map<String, Relation>) f.getRelations().clone()).entrySet()) {
			Faction potential = FactionManager.getByString(entry.getKey());
			if(potential == null) {
				LogManager.relations(
						"PRUNE %s dropped %s rel=%s (faction missing)",
						f.getId(),
						entry.getKey(),
						FactionManager.describeRelation(entry.getValue()));
				f.getRelations().remove(entry.getKey());
				continue;
			}
			if(entry.getValue() != null
					&& entry.getValue().getType() != null
					&& entry.getValue().getType().isVassalage()) {
				subjects.add(potential);
			}
		}
		return subjects;
	}
	
	public static String getTopLiege(Faction f) {
	    String liege = getOverlord(f);

	    while (liege != null) {
	        Faction overlord = FactionManager.getByString(liege);
	        if (overlord == null) {
	            break;
	        }

	        String nextLiege = getOverlord(overlord);
	        if (nextLiege == null) {
	            break;
	        }

	        liege = nextLiege;
	    }

	    return liege;
	}

	public static void transferSubject(Faction subject, Faction reciever) {
		if (subject == null || reciever == null) {
			return;
		}
		if (!reciever.canHaveVassals()) {
			return;
		}
		String overlord = getOverlord(subject);
		if (overlord == null) {
			return;
		}
		Faction o = FactionManager.getByString(overlord);
		if (o == null) {
			return;
		}
		Relation relation = o.getRelation(subject.getId());
		if (relation == null || relation.getType() == null) {
			return;
		}
		RelationType type = relation.getType();
		endVassalage(o, subject, false);
		setRelationForced(type, subject, reciever);
	}

	public static boolean isOnOverlordPath(Faction origin, Faction target) {
		String liege = getOverlord(origin);

	    while (liege != null) {
	        Faction overlord = FactionManager.getByString(liege);
	        if (overlord == null) {
	            break;
	        }
			if(overlord.getId().equalsIgnoreCase(target.getId())) return true;

	        String nextLiege = getOverlord(overlord);
	        if (nextLiege == null) {
	            break;
	        }

	        liege = nextLiege;
	    }

	    return false;
	}

	
	public static boolean vassalCheck(Faction target, Faction origin) {
		if(getOverlord(target) == null) return true;
		if(getOverlord(target).equalsIgnoreCase(origin.getId())) return true;
		return false;
	}
	
	public static boolean setAttitude(Player p, Attitude a, Faction target, Faction origin) {
		if (p == null || a == null || target == null || origin == null) {
			return false;
		}
		Relation r = origin.getRelation(target.getId());
		Attitude current = r.getAttitude();
		if (current != null && current.getId().equalsIgnoreCase(a.getId())) {
			p.sendMessage(StringFormatter.formatHex("#a89977Set attitude to "+a.getName()));
			return true;
		}
		double oldCost = getDiplomaticCost(origin, target, current);
		double newCost = getDiplomaticCost(origin, target, a);
		if (origin.getDiplomacyHandler().getAvailableCapacity() < newCost - oldCost) {
			p.sendMessage("§cYou lack diplomatic capacity for this attitude!");
			return false;
		}
		r.setAttitude(a);
		origin.setRelation(target, r);
		p.sendMessage(StringFormatter.formatHex("#a89977Set attitude to "+a.getName()));
		return true;
	}
	
	private static void sendRequest(Player sender, Faction f, RelationType type) {
		Player p = Bukkit.getPlayerExact(f.getLeader());
		if(p == null || !p.isOnline()) {
			sender.sendMessage("§cCannot send request, target faction leader is not online!");
			return;
		}
		sender.sendMessage("§aSent a request to "+f.getName()+" §afor them to become your "+type.getName());
		p.sendMessage(FactionManager.getByLeader(sender.getName()).getName()+" §7is requesting that you become their "+type.getName());
		p.sendMessage("§7Type §a/faction accept §7to accept");
		p.sendMessage("§7Request will time out in 60 seconds");
		RequestManager.addRequest(sender, p, new RelationRequest(FactionManager.getByLeader(sender.getName()).getOrCreateMainGuild(), type, false));
	}
	
	public static void acceptRequest(Player p) {
		RelationRequest req = (RelationRequest) RequestManager.getRequest(p);
		Faction reciever = FactionManager.getByLeader(p.getName());
		if(reciever == null) {
			p.sendMessage("§cYou do not have a faction");
			return;
		}
		Faction sender = req.getFaction();
		Player sp = Bukkit.getPlayerExact(sender.getLeader());
		if(sp != null && sp.isOnline()) sp.sendMessage(reciever.getName()+" §aaccepted your request and became your "+req.getType().getName());
		setRelation(p, req.getType(), reciever, sender, false);
	}

	private static void sendTradeRequest(Player sender, Faction f, RelationType type) {
		Player p = Bukkit.getPlayerExact(f.getLeader());
		if(p == null || !p.isOnline()) {
			sender.sendMessage("§cCannot send request, target faction leader is not online!");
			return;
		}
		sender.sendMessage("§aSent a request to "+f.getName()+" §ato set trade to "+type.getName());
		p.sendMessage(FactionManager.getByLeader(sender.getName()).getName()+" §7is requesting that you set trade to "+type.getName());
		p.sendMessage("§7Type §a/faction accept §7to accept");
		p.sendMessage("§7Request will time out in 60 seconds");
		RequestManager.addRequest(sender, p, new RelationRequest(FactionManager.getByLeader(sender.getName()).getOrCreateMainGuild(), type, true));
	}
	
	public static void acceptTradeRequest(Player p) {
		RelationRequest req = (RelationRequest) RequestManager.getRequest(p);
		Faction reciever = FactionManager.getByLeader(p.getName());
		if(reciever == null) {
			p.sendMessage("§cYou do not have a faction");
			return;
		}
		Faction sender = req.getFaction();
		Player sp = Bukkit.getPlayerExact(sender.getLeader());
		if(sp != null && sp.isOnline()) sp.sendMessage(reciever.getName()+" §aaccepted your request and set trade to "+req.getType().getName());
		setRelation(p, req.getType(), reciever, sender, false);
	}

	private static void sendTreatyRequest(Player sender, Faction f, RelationType type) {
		Player p = Bukkit.getPlayerExact(f.getLeader());
		if(p == null || !p.isOnline()) {
			sender.sendMessage("§cCannot send request, target faction leader is not online!");
			return;
		}
		sender.sendMessage("§aSent a request to "+f.getName()+" §ato set treaty to "+type.getName());
		p.sendMessage(FactionManager.getByLeader(sender.getName()).getName()+" §7is requesting that you set treaty to "+type.getName());
		p.sendMessage("§7Type §a/faction accept §7to accept");
		p.sendMessage("§7Request will time out in 60 seconds");
		RequestManager.addRequest(sender, p, new RelationRequest(FactionManager.getByLeader(sender.getName()).getOrCreateMainGuild(), type, false, true));
	}

	public static void acceptTreatyRequest(Player p) {
		RelationRequest req = (RelationRequest) RequestManager.getRequest(p);
		Faction reciever = FactionManager.getByLeader(p.getName());
		if(reciever == null) {
			p.sendMessage("§cYou do not have a faction");
			return;
		}
		Faction sender = req.getFaction();
		Player sp = Bukkit.getPlayerExact(sender.getLeader());
		if(sp != null && sp.isOnline()) sp.sendMessage(reciever.getName()+" §aaccepted your request and set treaty to "+req.getType().getName());
		setTreatyRelation(p, req.getType(), reciever, sender, false);
	}
}
