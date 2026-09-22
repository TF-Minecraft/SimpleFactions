package me.Plugins.SimpleFactions.Managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Objects.Request.AutoresolveRequest;
import me.Plugins.SimpleFactions.Objects.Request.ElevateRequest;
import me.Plugins.SimpleFactions.Objects.Request.MercenaryInviteRequest;
import me.Plugins.SimpleFactions.Objects.Request.MovementJoinRequest;
import me.Plugins.SimpleFactions.Objects.Request.MovementLeaderTargetRequest;
import me.Plugins.SimpleFactions.Objects.Request.RelationRequest;
import me.Plugins.SimpleFactions.Objects.Request.RelocateRequest;
import me.Plugins.SimpleFactions.Objects.Request.Request;
import me.Plugins.SimpleFactions.Objects.Request.VehicleTransferConsentRequest;
import me.Plugins.SimpleFactions.Objects.Request.WarRequest;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleAutoresolveService;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryInvites;

public class RequestManager {
	private static HashMap<Player, Request> requests = new HashMap<>();
	
	public static void start() {
		new BukkitRunnable() {
			@Override
	        public void run() {
				for(Map.Entry<Player, Request> entry : requests.entrySet()) {
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
					}
					requests.remove(entry.getKey());
				}
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
