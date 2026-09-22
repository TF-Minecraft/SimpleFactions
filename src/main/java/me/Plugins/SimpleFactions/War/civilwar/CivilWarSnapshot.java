package me.Plugins.SimpleFactions.War.civilwar;


import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarMemberMove;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarWartimeVassalEnd;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CivilWarSnapshot {
	private String hostFactionId;
	private String tempRebelFactionId;
	private Map<Integer, String> transferredProvinces = new LinkedHashMap<>();
	private List<CivilWarWartimeVassalEnd> wartimeVassalEnds = new ArrayList<>();
	private Integer hostOldCapitalId;
	private Integer rebelCapitalId;
	private String wantedLeaderName;
	private List<CivilWarMemberMove> memberMoves = new ArrayList<>();
	private String rebelMainGuildOwnName;
	private String movedTitleId;

	public String getHostFactionId() {
		return hostFactionId;
	}

	public void setHostFactionId(String hostFactionId) {
		this.hostFactionId = hostFactionId;
	}

	public String getTempRebelFactionId() {
		return tempRebelFactionId;
	}

	public void setTempRebelFactionId(String tempRebelFactionId) {
		this.tempRebelFactionId = tempRebelFactionId;
	}

	public Map<Integer, String> getTransferredProvinces() {
		return transferredProvinces;
	}

	public void setTransferredProvinces(Map<Integer, String> transferredProvinces) {
		this.transferredProvinces = transferredProvinces == null
				? new LinkedHashMap<>()
				: new LinkedHashMap<>(transferredProvinces);
	}

	public List<CivilWarWartimeVassalEnd> getWartimeVassalEnds() {
		return wartimeVassalEnds;
	}

	public void setWartimeVassalEnds(List<CivilWarWartimeVassalEnd> wartimeVassalEnds) {
		this.wartimeVassalEnds = wartimeVassalEnds == null
				? new ArrayList<>()
				: new ArrayList<>(wartimeVassalEnds);
	}

	public Integer getHostOldCapitalId() {
		return hostOldCapitalId;
	}

	public void setHostOldCapitalId(Integer hostOldCapitalId) {
		this.hostOldCapitalId = hostOldCapitalId;
	}

	public Integer getRebelCapitalId() {
		return rebelCapitalId;
	}

	public void setRebelCapitalId(Integer rebelCapitalId) {
		this.rebelCapitalId = rebelCapitalId;
	}

	public String getWantedLeaderName() {
		return wantedLeaderName;
	}

	public void setWantedLeaderName(String wantedLeaderName) {
		this.wantedLeaderName = wantedLeaderName;
	}

	public List<CivilWarMemberMove> getMemberMoves() {
		return memberMoves;
	}

	public void setMemberMoves(List<CivilWarMemberMove> memberMoves) {
		this.memberMoves = memberMoves == null
				? new ArrayList<>()
				: new ArrayList<>(memberMoves);
	}

	public String getRebelMainGuildOwnName() {
		return rebelMainGuildOwnName;
	}

	public void setRebelMainGuildOwnName(String rebelMainGuildOwnName) {
		this.rebelMainGuildOwnName = rebelMainGuildOwnName;
	}

	public String getMovedTitleId() {
		return movedTitleId;
	}

	public void setMovedTitleId(String movedTitleId) {
		this.movedTitleId = movedTitleId;
	}
}
