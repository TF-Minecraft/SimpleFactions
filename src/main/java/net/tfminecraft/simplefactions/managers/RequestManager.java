package net.tfminecraft.simplefactions.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.objects.request.AutoresolveRequest;
import net.tfminecraft.simplefactions.objects.request.ElevateRequest;
import net.tfminecraft.simplefactions.objects.request.MercenaryInviteRequest;
import net.tfminecraft.simplefactions.objects.request.MovementJoinRequest;
import net.tfminecraft.simplefactions.objects.request.MovementLeaderTargetRequest;
import net.tfminecraft.simplefactions.objects.request.RelationRequest;
import net.tfminecraft.simplefactions.objects.request.RelocateRequest;
import net.tfminecraft.simplefactions.objects.request.Request;
import net.tfminecraft.simplefactions.objects.request.VehicleTransferConsentRequest;
import net.tfminecraft.simplefactions.objects.request.WarRequest;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleAutoresolveService;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryInvites;

public class RequestManager {
	private static HashMap<Player, Request> requests = new HashMap<>();
	
	public static void start() {
		new BukkitRunnable() {
			@Override
	        public void run() {
				expireTimedOutRequests();
	        }
	    }.runTaskTimer(SimpleFactions.plugin, 0L, 20L);
	}
	
	public static boolean hasRequest(Player p) {
		return requests.containsKey(p);
	}
	
	public static Request getRequest(Player p) {
		return requests.get(p);
	}
	
	public static void remove(Player p) {
		requests.remove(p);
	}
	
	public static void addRequest(Player sender, Player p, Request r) {
		if(hasRequest(p)) {
			sender.sendMessage("§cThe target is already considering another request.");
			return;
		}
		requests.put(p, r);
	}

	public static void rebindRelationRequests() {
		List<Player> cancel = new ArrayList<>();
		for (Map.Entry<Player, Request> entry : requests.entrySet()) {
			if (!(entry.getValue() instanceof RelationRequest relationRequest)) continue;
			RelationType current = relationRequest.getType();
			if (current == null || current.getId() == null) {
				cancel.add(entry.getKey());
				continue;
			}
			RelationType rebound = RelationLoader.getType(current.getId());
			if (rebound == null) {
				cancel.add(entry.getKey());
			} else {
				relationRequest.setType(rebound);
			}
		}
		for (Player player : cancel) {
			requests.remove(player);
		}
	}
	
	// A call to arms that expires is a decline. Package-visible for tests.
	static void expireTimedOutRequests() {
		Iterator<Map.Entry<Player, Request>> iterator = requests.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Player, Request> entry = iterator.next();
			Request request = entry.getValue();
			if (!request.timedOut()) {
				continue;
			}
			if (request instanceof VehicleTransferConsentRequest consentRequest) {
				SimpleFactions plugin = SimpleFactions.getInstance();
				if (plugin != null) {
					plugin.getVehicleTransferConsentService()
							.notifyExpired(consentRequest, entry.getKey());
				}
			} else if (request instanceof WarRequest warRequest) {
				WarManager.declineCallToArms(entry.getKey(), warRequest, false);
			}
			iterator.remove();
		}
	}

	public static void decline(Player p) {
		if (!hasRequest(p)) return;
		Request req = requests.remove(p);
		if (req instanceof WarRequest warRequest) {
			WarManager.declineCallToArms(p, warRequest, true);
			return;
		}
		p.sendMessage("§7You declined the request.");
	}

	public static void accept(Player p) {
		if(!hasRequest(p)) return;
		Request req = requests.get(p);
		if(req instanceof RelationRequest rreq) {
			if(rreq.isTrade()) {
				RelationManager.acceptTradeRequest(p);
			} else if(rreq.isTreaty()) {
				RelationManager.acceptTreatyRequest(p);
			} else {
				RelationManager.acceptRequest(p);
			}
		} else if(req instanceof WarRequest){
			WarManager.acceptRequest(p);
		} else if(req instanceof AutoresolveRequest) {
			BattleAutoresolveService.acceptRequest(p);
		} else if(req instanceof RelocateRequest){
			FactionManager.acceptRelocateRequest(p);
		} else if(req instanceof ElevateRequest) {
			FactionManager.acceptElevationRequest(p);
		} else if(req instanceof MovementJoinRequest) {
			FactionManager.acceptMovementJoinRequest(p);
		} else if(req instanceof MovementLeaderTargetRequest) {
			FactionManager.acceptMovementLeaderTargetRequest(p);
		} else if(req instanceof MercenaryInviteRequest) {
			MercenaryInvites.accept(p);
		} else if(req instanceof VehicleTransferConsentRequest) {
			SimpleFactions plugin = SimpleFactions.getInstance();
			if (plugin != null) {
				plugin.getVehicleTransferConsentService().acceptRequest(p);
			}
		}
		requests.remove(p);
	}
}
