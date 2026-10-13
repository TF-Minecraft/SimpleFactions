package net.tfminecraft.simplefactions.war.battle.warband;

import net.tfminecraft.simplefactions.identity.Members;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;

public class Warband {
	private String id;
	private String name;
	private UUID leaderId;
	private Set<UUID> memberIds = new LinkedHashSet<>();
	private final Map<UUID, net.tfminecraft.tlibs.character.CharacterRef> memberCharacters = new HashMap<>();
	private final Map<UUID, net.tfminecraft.tlibs.character.CharacterRef> invitedCharacters = new HashMap<>();
	private Set<UUID> dummyMemberIds = new LinkedHashSet<>();
	private Map<UUID, String> dummyDisplayNames = new HashMap<>();
	private Set<UUID> invitedIds = new HashSet<>();
	private boolean locked;
	private boolean faction;
	private String campaignSideId;
	private com.google.gson.JsonObject storedFields = new com.google.gson.JsonObject();

	public void restoreStoredFields(com.google.gson.JsonObject fields) {
		storedFields = fields.deepCopy();
	}

	public com.google.gson.JsonObject getStoredFields() {
		return storedFields.deepCopy();
	}

	public static Warband createWithMemberIds(String id, UUID leaderId, boolean locked, UUID... members) {
		Warband warband = new Warband();
		warband.id = id;
		warband.name = id;
		warband.leaderId = leaderId;
		warband.locked = locked;
		warband.faction = false;
		warband.memberIds.add(leaderId);
		for (UUID memberId : members) {
			if (!memberId.equals(leaderId)) {
				warband.memberIds.add(memberId);
			}
		}
		return warband;
	}

	private Warband() {
	}

	public Warband(String id, Player l) {
		this.id = id;
		this.name = id;
		this.leaderId = l.getUniqueId();
		this.memberIds.add(l.getUniqueId());
		Members.session(l).filter(Members::isCurrent).ifPresent(session -> memberCharacters.put(l.getUniqueId(), session.ref()));
		this.locked = true;
		this.faction = false;
	}

	public static String campaignSideWarbandId(int warId, String battleSideId) {
		return "campaign_w" + warId + "_" + battleSideId;
	}

	public static Warband createCampaignSideShell(String warbandId, War war, Side side, String battleSideId) {
		Warband warband = new Warband();
		warband.id = warbandId;
		warband.name = "The " + side.getLeader().getName() + " Host";
		warband.leaderId = pendingLeaderUuid(warband.id);
		warband.locked = true;
		warband.faction = true;
		warband.campaignSideId = battleSideId;
		return warband;
	}

	/** @deprecated use {@link #createCampaignSideShell(String, War, Side, String)} */
	@Deprecated
	public static Warband createCampaignSideShell(War war, Side side, String battleSideId) {
		return createCampaignSideShell(campaignSideWarbandId(war.getId(), battleSideId), war, side, battleSideId);
	}

	public static Warband createRaidShell(String id, Side side, String campaignSideId) {
		Warband warband = new Warband();
		warband.id = id;
		warband.name = "The " + side.getLeader().getName() + " Host";
		warband.leaderId = pendingLeaderUuid(id);
		warband.locked = true;
		warband.faction = true;
		warband.campaignSideId = campaignSideId;
		return warband;
	}

	public static UUID pendingLeaderUuid(String warbandId) {
		return UUID.nameUUIDFromBytes(("warband_pending:" + warbandId).getBytes(StandardCharsets.UTF_8));
	}

	public static boolean isPendingLeader(String warbandId, UUID leaderId) {
		return warbandId != null && leaderId != null && leaderId.equals(pendingLeaderUuid(warbandId));
	}

	public boolean isPendingLeader() {
		return isPendingLeader(id, leaderId);
	}

	public void resetToPendingLeader() {
		this.leaderId = pendingLeaderUuid(id);
	}

	public int getRealMemberCount() {
		return Math.max(0, memberIds.size() - dummyMemberIds.size());
	}

	public UUID getOldestRealMemberId(UUID excludeId) {
		for (UUID memberId : memberIds) {
			if (memberId.equals(excludeId) || isDummyMember(memberId)) {
				continue;
			}
			return memberId;
		}
		return null;
	}

	public java.util.Optional<net.tfminecraft.tlibs.character.CharacterRef> getMemberCharacter(UUID ownerId) {
		return java.util.Optional.ofNullable(memberCharacters.get(ownerId));
	}

	public Map<UUID, net.tfminecraft.tlibs.character.CharacterRef> getMemberCharacters() {
		return Map.copyOf(memberCharacters);
	}

	/** New enrollment captures its named character; this is never a legacy-owner resolver. */
	public static java.util.Optional<net.tfminecraft.tlibs.character.CharacterRef> participant(
			UUID account, String memberKey, Player online) {
		Player player = online != null ? online : Bukkit.getServer() == null ? null : Bukkit.getPlayer(account);
		if (player == null) player = Members.player(memberKey);
		if (player != null) {
			return Members.session(player).filter(Members::isCurrent)
					.filter(session -> account.equals(session.ref().ownerId())
							&& java.util.Objects.equals(memberKey, Members.key(session.ref())))
					.map(net.tfminecraft.tlibs.character.CharacterSession::ref);
		}
		return Members.ref(memberKey).filter(ref -> account.equals(ref.ownerId()));
	}

	public void restoreMemberCharacters(Map<UUID, net.tfminecraft.tlibs.character.CharacterRef> saved) {
		memberCharacters.clear();
		if (saved != null) saved.forEach((owner, character) -> {
			if (character != null && owner.equals(character.ownerId()) && memberIds.contains(owner)) memberCharacters.put(owner, character);
		});
	}

	public Map<UUID, net.tfminecraft.tlibs.character.CharacterRef> getInvitedCharacters() {
		return Map.copyOf(invitedCharacters);
	}

	public void restoreInvitedCharacters(Map<UUID, net.tfminecraft.tlibs.character.CharacterRef> saved) {
		invitedCharacters.clear();
		if (saved != null) saved.forEach((owner, character) -> {
			if (character != null && owner.equals(character.ownerId()) && invitedIds.contains(owner)) invitedCharacters.put(owner, character);
		});
	}

	public void addPlayer(Player p) {
		if (isIdentityHeld()) return;
		var session = Members.session(p).filter(Members::isCurrent).orElse(null);
		if (session == null || (memberIds.contains(p.getUniqueId())
				&& !session.ref().equals(memberCharacters.get(p.getUniqueId())))) return;
		memberIds.add(p.getUniqueId());
		memberCharacters.put(p.getUniqueId(), session.ref());
		invitedIds.remove(p.getUniqueId());
		invitedCharacters.remove(p.getUniqueId());
		var battle = net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.getBattleByMemberId(p.getUniqueId());
		if (battle != null && battle.hasStarted()) battle.captureParticipant(p);
	}

	public void addMember(UUID memberId) {
		memberIds.add(memberId);
	}

	/** Enrollment records the supplied character; UUID-only adds are structural bookkeeping. */
	public void addMember(UUID memberId, net.tfminecraft.tlibs.character.CharacterRef character) {
		if (!isIdentityHeld() && character != null && memberId.equals(character.ownerId())) {
			memberIds.add(memberId);
			memberCharacters.put(memberId, character);
		}
	}

	public void removeMember(UUID memberId) {
		memberIds.remove(memberId);
		memberCharacters.remove(memberId);
		dummyMemberIds.remove(memberId);
		dummyDisplayNames.remove(memberId);
	}

	public void removePlayer(Player p) {
		if (p != null) {
			removeMember(p.getUniqueId());
		}
	}

	public void uninvite(Player p) {
		if (p != null) {
			invitedIds.remove(p.getUniqueId());
			invitedCharacters.remove(p.getUniqueId());
		}
	}

	public void invite(Player p) {
		if (isIdentityHeld()) return;
		Members.session(p).filter(Members::isCurrent).ifPresent(session -> {
			invitedIds.add(p.getUniqueId());
			invitedCharacters.put(p.getUniqueId(), session.ref());
		});
	}

	public boolean isInvited(Player p) {
		return p != null && invitedIds.contains(p.getUniqueId()) && matches(p, invitedCharacters);
	}

	public boolean hasMember(Player p) {
		return p != null && memberIds.contains(p.getUniqueId()) && matches(p, memberCharacters);
	}

	/** Account UUIDs route players; only the captured character has the role. */
	private boolean matches(Player player, Map<UUID, net.tfminecraft.tlibs.character.CharacterRef> characters) {
		if (isIdentityHeld()) return false;
		return Members.session(player).filter(Members::isCurrent)
				.map(session -> session.ref().equals(characters.get(player.getUniqueId())))
				.orElse(false);
	}

	public boolean isLeader(Player player) {
		return player != null && !isPendingLeader() && !isDummyMember(player.getUniqueId())
				&& player.getUniqueId().equals(leaderId) && hasMember(player);
	}

	public boolean isIdentityHeld() {
		return net.tfminecraft.simplefactions.war.battle.persistence.WarbandCharacters.isHeld(
				new java.io.File("plugins/SimpleFactions/Warbands", "warband_" + id + ".json"));
	}

	public boolean hasMember(UUID memberId) {
		return memberIds.contains(memberId);
	}

	public int getMemberCount() {
		return memberIds.size();
	}

	public void addDummyMembers(Collection<UUID> ids, Map<UUID, String> displayNames) {
		if (ids == null || ids.isEmpty()) {
			return;
		}
		for (UUID id : ids) {
			if (id == null || id.equals(leaderId)) {
				continue;
			}
			if (memberIds.add(id)) {
				dummyMemberIds.add(id);
				if (displayNames != null && displayNames.containsKey(id)) {
					dummyDisplayNames.put(id, displayNames.get(id));
				}
			}
		}
	}

	public boolean isDummyMember(UUID id) {
		return id != null && dummyMemberIds.contains(id);
	}

	public int getDummyMemberCount() {
		return dummyMemberIds.size();
	}

	public void clearDummyMembers() {
		if (dummyMemberIds.isEmpty()) {
			return;
		}
		boolean leaderWasDummy = isDummyMember(leaderId);
		for (UUID dummyId : new ArrayList<>(dummyMemberIds)) {
			memberIds.remove(dummyId);
		}
		dummyMemberIds.clear();
		dummyDisplayNames.clear();
		if (leaderWasDummy || (leaderId != null && !memberIds.contains(leaderId))) {
			UUID realLeader = getOldestRealMemberId(null);
			if (realLeader != null) {
				setLeaderId(realLeader);
			} else if (isFaction()) {
				resetToPendingLeader();
			}
		}
	}

	public String getMemberDisplayName(UUID memberId) {
		if (memberId == null) {
			return "Unknown";
		}
		if (isDummyMember(memberId)) {
			String name = dummyDisplayNames.get(memberId);
			return name != null && !name.isBlank() ? name : "Unknown";
		}
		var captured = memberCharacters.get(memberId);
		if (captured != null) return Members.display(Members.key(captured));
		Player online = Bukkit.getPlayer(memberId);
		if (online != null && online.getName() != null) {
			return online.getName();
		}
		OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
		if (offline.getName() != null && !offline.getName().isBlank()) {
			return offline.getName();
		}
		return "Unknown";
	}

	public List<String> getMemberDisplayNamesForLore(int maxNames) {
		List<String> names = new ArrayList<>();
		if (maxNames <= 0 || memberIds.isEmpty()) {
			return names;
		}
		UUID leader = leaderId;
		if (leader != null && !isPendingLeader() && hasMember(leader)) {
			names.add(getMemberDisplayName(leader) + " (leader)");
		}
		for (UUID memberId : memberIds) {
			if (names.size() >= maxNames) {
				break;
			}
			if (leader != null && !isPendingLeader() && memberId.equals(leader)) {
				continue;
			}
			names.add(getMemberDisplayName(memberId));
		}
		return names;
	}

	public java.util.Collection<UUID> getMemberIds() {
		return java.util.Collections.unmodifiableSet(memberIds);
	}

	/** Online Bukkit players only; excludes devmode dummy members. */
	public List<Player> getOnlineMembers() {
		List<Player> online = new ArrayList<>();
		for (UUID memberId : memberIds) {
			if (isDummyMember(memberId)) {
				continue;
			}
			Player p = Bukkit.getPlayer(memberId);
			if (p != null && p.isOnline() && hasMember(p)) {
				online.add(p);
			}
		}
		return online;
	}

	public int getOnlineMemberCount() {
		return getOnlineMembers().size();
	}

	public String getId() {
		return this.id;
	}

	public String getName() {
		return name;
	}

	public String getCampaignSideId() {
		return campaignSideId;
	}

	public String getLeaderDisplayName() {
		if (isPendingLeader()) {
			return "Pending signup";
		}
		return getMemberDisplayName(leaderId);
	}

	public Player getLeader() {
		Player player = Bukkit.getPlayer(leaderId);
		return isLeader(player) ? player : null;
	}

	public UUID getLeaderId() {
		return leaderId;
	}

	public List<Player> getPlayers() {
		return getOnlineMembers();
	}

	public List<Player> getInvited() {
		List<Player> invited = new ArrayList<>();
		for (UUID invitedId : invitedIds) {
			Player p = Bukkit.getPlayer(invitedId);
			if (p != null && p.isOnline() && isInvited(p)) {
				invited.add(p);
			}
		}
		return invited;
	}

	public boolean isLocked() {
		return locked;
	}

	public boolean isFaction() {
		return faction;
	}

	public void setLocked(boolean b) {
		this.locked = b;
	}

	public void setLeader(Player p) {
		this.leaderId = p.getUniqueId();
	}

	public void setLeaderId(UUID leaderId) {
		this.leaderId = leaderId;
	}

	public Set<UUID> getInvitedIds() {
		return java.util.Collections.unmodifiableSet(invitedIds);
	}

	public static Warband fromPersistence(
			String id,
			String name,
			UUID leaderId,
			List<UUID> memberIds,
			List<UUID> invitedIds,
			boolean locked,
			boolean faction,
			String campaignSideId) {
		Warband warband = new Warband();
		warband.id = id;
		warband.name = name;
		warband.leaderId = leaderId != null ? leaderId : pendingLeaderUuid(id);
		warband.locked = locked;
		warband.faction = faction;
		warband.campaignSideId = campaignSideId;
		if (memberIds != null) {
			warband.memberIds.addAll(memberIds);
		}
		if (invitedIds != null) {
			warband.invitedIds.addAll(invitedIds);
		}
		return warband;
	}
}
