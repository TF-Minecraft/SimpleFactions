package net.tfminecraft.simplefactions.diplomacy.coalition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A group of independent factions aimed at one target faction. Faction ids are kept
 * as given and compared without case, the same way {@code FactionManager.getByString} does.
 */
public final class Coalition {
	private final String id;
	private String name;
	private String leaderId;
	private String targetId;
	private boolean offensive;
	/** Member id to when it joined, in joining order. The leader is a member too. */
	private final LinkedHashMap<String, Long> members = new LinkedHashMap<>();
	/** Invited faction id to when the invite runs out. */
	private final LinkedHashMap<String, Long> invites = new LinkedHashMap<>();
	/** Null while the coalition shows its leader's banner. */
	private List<String> bannerPatterns;
	/** {@code R,G,B} on the website map; null while it uses its leader's colour. */
	private String rgb;
	private final long createdAt;
	/** When the target stopped being a valid target, or null while it still is one. */
	private Long targetLostAt;
	/** Time the target-loss countdown spent paused for a running coalition war. */
	private long targetLossPausedMillis;
	/** When the coalition was left with a single member, or null while it has more. */
	private Long singleMemberSince;

	public Coalition(String id, String name, String leaderId, String targetId, long createdAt) {
		this.id = id;
		this.name = name;
		this.leaderId = leaderId;
		this.targetId = targetId;
		this.createdAt = createdAt;
		members.put(leaderId, createdAt);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getLeaderId() {
		return leaderId;
	}

	public void setLeaderId(String leaderId) {
		this.leaderId = leaderId;
	}

	public boolean isLeader(String factionId) {
		return factionId != null && factionId.equalsIgnoreCase(leaderId);
	}

	public String getTargetId() {
		return targetId;
	}

	public void setTargetId(String targetId) {
		this.targetId = targetId;
	}

	public boolean isTarget(String factionId) {
		return factionId != null && factionId.equalsIgnoreCase(targetId);
	}

	public boolean isOffensive() {
		return offensive;
	}

	public void setOffensive(boolean offensive) {
		this.offensive = offensive;
	}

	public List<String> getMemberIds() {
		return new ArrayList<>(members.keySet());
	}

	public Map<String, Long> getMembers() {
		return members;
	}

	public boolean isMember(String factionId) {
		return key(members, factionId) != null;
	}

	/** When the member joined, or null when it is not a member. */
	public Long joinedAt(String factionId) {
		String key = key(members, factionId);
		return key == null ? null : members.get(key);
	}

	public void addMember(String factionId, long joinedAt) {
		members.put(factionId, joinedAt);
		removeInvite(factionId);
	}

	public boolean removeMember(String factionId) {
		String key = key(members, factionId);
		return key != null && members.remove(key) != null;
	}

	public Map<String, Long> getInvites() {
		return invites;
	}

	public boolean isInvited(String factionId, long now) {
		String key = key(invites, factionId);
		return key != null && invites.get(key) > now;
	}

	public void invite(String factionId, long until) {
		removeInvite(factionId);
		invites.put(factionId, until);
	}

	public boolean removeInvite(String factionId) {
		String key = key(invites, factionId);
		return key != null && invites.remove(key) != null;
	}

	public List<String> getBannerPatterns() {
		return bannerPatterns;
	}

	public void setBannerPatterns(List<String> bannerPatterns) {
		this.bannerPatterns = bannerPatterns == null ? null : new ArrayList<>(bannerPatterns);
	}

	public String getRgb() {
		return rgb;
	}

	public void setRgb(String rgb) {
		this.rgb = rgb;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public Long getTargetLostAt() {
		return targetLostAt;
	}

	public void setTargetLostAt(Long targetLostAt) {
		this.targetLostAt = targetLostAt;
	}

	public long getTargetLossPausedMillis() {
		return targetLossPausedMillis;
	}

	public void setTargetLossPausedMillis(long targetLossPausedMillis) {
		this.targetLossPausedMillis = targetLossPausedMillis;
	}

	public Long getSingleMemberSince() {
		return singleMemberSince;
	}

	public void setSingleMemberSince(Long singleMemberSince) {
		this.singleMemberSince = singleMemberSince;
	}

	private static String key(Map<String, Long> map, String factionId) {
		if (factionId == null) return null;
		for (String key : map.keySet()) {
			if (key.equalsIgnoreCase(factionId)) return key;
		}
		return null;
	}
}
